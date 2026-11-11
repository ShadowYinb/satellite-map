package com.bilicraft.satellitemap.map;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Persistent coarse coverage built from successfully loaded tiles. No Minecraft/GPU calls. */
public final class OverviewStore implements AutoCloseable {
    public record Key(URI root,int level,int col,int row) {
        public double size(int tileSize){return Math.scalb((double)tileSize,level);}
    }
    private static final class Node {
        final BufferedImage image;final byte[] quality;boolean dirty;
        Node(){image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);quality=new byte[128*128];Arrays.fill(quality,(byte)127);}
        Node(BufferedImage image,byte[] quality){this.image=image;this.quality=quality;}
    }
    private final Path directory;
    private final Map<Key,Node> nodes=new LinkedHashMap<>(256,.75f,true);
    private final Map<URI,Integer> maxLevels=new ConcurrentHashMap<>();
    private final Map<URI,Integer> seen=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key,byte[]> updates=new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<Runnable> jobs=new ArrayBlockingQueue<>(32);
    private final Thread worker;
    private volatile boolean closed;
    public OverviewStore(Path directory){this.directory=directory;worker=new Thread(this::run,"satellite-map-overview");worker.setDaemon(true);worker.start();}
    public void configure(URI root,int maxLevel){maxLevels.put(root,Math.clamp(maxLevel,1,18));}
    // Called only on background download/disk workers, never on the render thread.
    public void accept(URI uri,byte[] png){
        var tile=TilePyramid.parse(uri);if(tile==null || closed)return;
        int hash=Arrays.hashCode(png);if(Objects.equals(seen.get(uri),hash))return;
        try {
            BufferedImage input=ImageIO.read(new ByteArrayInputStream(png));if(input==null)return;
            BufferedImage small=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);var g=small.createGraphics();
            try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(input,0,0,128,128,null);}finally{g.dispose();}
            jobs.put(()->compose(tile,small));if(seen.size()>8192)seen.clear();seen.put(uri,hash);
        }catch(IOException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    public boolean request(Key key){return !closed && jobs.offer(()->{Node node=load(key);if(node!=null)publish(key,node);});}
    public Map<Key,byte[]> drain(int max){
        Map<Key,byte[]> result=new LinkedHashMap<>();
        for(var entry:updates.entrySet()){if(result.size()>=max)break;if(updates.remove(entry.getKey(),entry.getValue()))result.put(entry.getKey(),entry.getValue());}
        return result;
    }
    public void flush() throws Exception {
        var done=new CompletableFuture<Void>();jobs.put(()->{flushDirty();done.complete(null);});done.get(10,TimeUnit.SECONDS);
    }
    private void run(){
        long nextFlush=0;
        try{
            while(!closed || !jobs.isEmpty()){
                Runnable job=jobs.poll(50,TimeUnit.MILLISECONDS);if(job!=null)try{job.run();}catch(RuntimeException ignored){}
                if(System.currentTimeMillis()>=nextFlush){flushDirty();nextFlush=System.currentTimeMillis()+200;}
            }
        }catch(InterruptedException ignored){}finally{flushDirty();}
    }
    private void compose(TilePyramid.Tile tile,BufferedImage source){
        int max=maxLevels.getOrDefault(tile.root(),11);
        List<BufferedImage> mipmaps=new ArrayList<>();mipmaps.add(source);
        // Area averages avoid aliasing and preserve small structures when zooming out.
        for(int size=64;size>=1;size/=2) {
            var parent=mipmaps.getLast();var mip=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
                int a=0,r=0,g=0,b=0;
                for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++) {
                    int pixel=parent.getRGB(x*2+dx,y*2+dy);a+=pixel>>>24;r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;
                }
                mip.setRGB(x,y,(a/4)<<24|(r/4)<<16|(g/4)<<8|b/4);
            }
            mipmaps.add(mip);
        }
        for(int level=tile.level()+1;level<=max;level++){
            int factor=1<<(level-tile.level()),col=Math.floorDiv(tile.col(),factor),row=Math.floorDiv(tile.row(),factor);
            var key=new Key(tile.root(),level,col,row);Node node=load(key);if(node==null){node=new Node();nodes.put(key,node);}
            int subX=Math.floorMod(tile.col(),factor),subY=Math.floorMod(tile.row(),factor);
            int left=(int)Math.floor(subX*128.0/factor),right=(int)Math.ceil((subX+1.0)*128/factor);
            int top=(int)Math.floor(subY*128.0/factor),bottom=(int)Math.ceil((subY+1.0)*128/factor);
            for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
                int index=y*128+x;if((node.quality[index]&255)<tile.level())continue;
                var mip=mipmaps.get(Math.min(level-tile.level(),7));
                int sx=Math.clamp(x-left,0,mip.getWidth()-1),sy=Math.clamp(y-top,0,mip.getHeight()-1),pixel=mip.getRGB(sx,sy);
                if((pixel>>>24)==0)continue;
                if(node.image.getRGB(x,y)!=pixel || (node.quality[index]&255)!=tile.level()){
                    node.image.setRGB(x,y,pixel);node.quality[index]=(byte)tile.level();node.dirty=true;
                }
            }
            trim();
        }
    }
    private Path path(Key key){return directory.resolve(TileDiskStore.key(key.root())).resolve(key.level()+"_"+key.col()+"_"+key.row()+".overview");}
    private Node load(Key key){
        Node node=nodes.get(key);if(node!=null)return node;
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path(key))))){
            if(in.readInt()!=0x534F5631 || !in.readUTF().equals(key.root().toASCIIString()))return null;
            int size=in.readInt();if(size<=0 || size>256*1024)return null;
            byte[] png=in.readNBytes(size);BufferedImage image=ImageIO.read(new ByteArrayInputStream(png));
            byte[] quality=in.readNBytes(128*128);if(image==null || image.getWidth()!=128 || image.getHeight()!=128 || quality.length!=128*128)return null;
            node=new Node(image,quality);nodes.put(key,node);trim();return node;
        }catch(IOException | RuntimeException ignored){return null;}
    }
    private byte[] encode(Node node) throws IOException {var bytes=new ByteArrayOutputStream();ImageIO.write(node.image,"png",bytes);return bytes.toByteArray();}
    private void publish(Key key,Node node){try{updates.put(key,encode(node));}catch(IOException ignored){}}
    private void persist(Key key,Node node){
        byte[] png;
        try{png=encode(node);updates.put(key,png);}catch(IOException e){return;}
        Path file=path(key),temp=null;
        try{
            Files.createDirectories(file.getParent());temp=Files.createTempFile(file.getParent(),"overview-",".tmp");
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))){out.writeInt(0x534F5631);out.writeUTF(key.root().toASCIIString());out.writeInt(png.length);out.write(png);out.write(node.quality);}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            node.dirty=false;
        }catch(IOException ignored){}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
    private void flushDirty(){for(var entry:nodes.entrySet())if(entry.getValue().dirty)persist(entry.getKey(),entry.getValue());}
    private void trim(){var iterator=nodes.entrySet().iterator();while(nodes.size()>256 && iterator.hasNext()){var entry=iterator.next();if(entry.getValue().dirty)persist(entry.getKey(),entry.getValue());iterator.remove();}}
    @Override public void close(){closed=true;worker.interrupt();}
}
