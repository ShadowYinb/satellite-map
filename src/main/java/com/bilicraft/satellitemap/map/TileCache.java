package com.bilicraft.satellitemap.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.bilicraft.satellitemap.ui.OverviewCache;

public final class TileCache implements AutoCloseable {
    private static final int MAX_PENDING = 32;
    private static final AtomicInteger CACHE_IDS=new AtomicInteger();
    private final String texturePrefix="cache_"+CACHE_IDS.getAndIncrement()+"_tile_";
    private final int maxEntries;
    private static final class Entry {
        Identifier texture;
        long updated, retryAt;
        long bytes;
        int failures;
        long lastVisible=-1;
        boolean loading,diskChecked;
        NetworkJob job;
    }
    private record Download(URI uri, Entry owner, byte[] png, long checkedAt, boolean local, String error) {}
    private final TileRepository repository;
    private final LinkedHashMap<URI,Entry> entries = new LinkedHashMap<>(256,.75f,true);
    private final ConcurrentLinkedQueue<Download> completed = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<URI> discovered=new ConcurrentLinkedQueue<>();
    private final Map<URI,Map<URI,TilePyramid.Tile>> index=new HashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(8,8,0L,TimeUnit.MILLISECONDS,new PriorityBlockingQueue<>(),r->{
        Thread t = new Thread(r,"satellite-map-tiles"); t.setDaemon(true); return t;
    });
    private long jobSerial;
    private String focus="";
    private long viewFrame;
    public final OverviewCache overview;
    private final class NetworkJob implements Runnable,Comparable<NetworkJob> {
        final URI uri;final Entry entry;final boolean needTexture;final long order=jobSerial++;
        int priority;
        NetworkJob(URI uri,Entry entry,int priority){this.uri=uri;this.entry=entry;this.priority=priority;needTexture=entry.texture==null;}
        @Override public int compareTo(NetworkJob other){int c=Integer.compare(priority,other.priority);return c!=0?c:Long.compare(order,other.order);}
        @Override public void run(){
            try {
                var local=repository.local(uri);var refreshed=repository.refresh(uri,local);
                if(overview!=null && (refreshed.changed() || needTexture))overview.accept(uri,refreshed.tile().png());
                if(!closed)completed.add(new Download(uri,entry,refreshed.changed() || needTexture?refreshed.tile().png():null,
                        refreshed.tile().checkedAt(),false,refreshed.storageError()));
            }catch(Exception e){if(!closed)completed.add(new Download(uri,entry,null,0,false,Objects.toString(e.getMessage(),e.getClass().getSimpleName())));}
        }
    }
    public void focusView(String key) {
        viewFrame++;
        if(focus.equals(key))return;focus=key;
        for(Runnable task;(task=workers.getQueue().poll())!=null;) {
            NetworkJob job=(NetworkJob)task;job.entry.loading=false;job.entry.job=null;pending--;
        }
    }
    // Slow website requests must never hold up local cache reads.
    private final ThreadPoolExecutor localWorkers=(ThreadPoolExecutor)Executors.newFixedThreadPool(2,r->{
        Thread t=new Thread(r,"satellite-map-disk");t.setDaemon(true);return t;
    });
    private int pending, pendingLocal, serial;
    private volatile boolean closed;
    public String lastError = "";

    public TileCache(Path directory) {this(directory,384,TileDiskStore.DEFAULT_LIMIT,true);}
    public TileCache(Path directory,int maxEntries,long diskLimit) {
        this(directory,maxEntries,diskLimit,false);
    }
    public TileCache(Path directory,int maxEntries,long diskLimit,boolean withOverview) {
        this.maxEntries=maxEntries;
        overview=withOverview?new OverviewCache(directory.resolveSibling("overview")):null;
        var disk=new TileDiskStore(directory,diskLimit);repository=new TileRepository(disk);
        localWorkers.execute(()->disk.scan(discovered::add));
    }

    public Identifier get(URI uri,int refreshSeconds) {
        return get(uri,refreshSeconds,true,1000);
    }
    public Identifier get(URI uri,int refreshSeconds,int priority) {return get(uri,refreshSeconds,true,priority);}
    public Identifier local(URI uri) {return get(uri,3600,false,10000);}
    private Identifier get(URI uri,int refreshSeconds,boolean network,int priority) {
        Entry entry = entries.computeIfAbsent(uri,key -> new Entry());
        long now = System.currentTimeMillis();
        if(network && entry.job!=null && priority<entry.job.priority && workers.getQueue().remove(entry.job)) {
            entry.job.priority=priority;workers.getQueue().add(entry.job);
        }
        if(!closed && !entry.diskChecked && !entry.loading && pendingLocal<64) {
            entry.loading=true;pendingLocal++;
            localWorkers.execute(()->{
                var local=repository.local(uri);
                if(overview!=null && local!=null)overview.accept(uri,local.png());
                if(!closed)completed.add(new Download(uri,entry,local==null?null:local.png(),local==null?0:local.checkedAt(),true,""));
            });
        }
        if (network && !closed && entry.diskChecked && !entry.loading && now >= entry.retryAt && pending < MAX_PENDING
                && (entry.texture == null || now-entry.updated >= refreshSeconds*1000L)) {
            entry.loading = true; pending++;
            entry.job=new NetworkJob(uri,entry,priority);workers.execute(entry.job);
        }
        return entry.texture;
    }
    public record Resident(TilePyramid.Tile tile,Identifier texture) {}
    /** Only base imagery is reliable enough to calibrate a single surface block. No network I/O. */
    public int sampleBase(DynmapModel model,String world,DynmapModel.Layer layer,double u,double v) {
        int col=(int)Math.floor(u/layer.tileSize()),row=(int)Math.floor(v/layer.tileSize());
        var entry=entries.get(model.tiles().resolve(layer.tilePath(world,0,col,row)));
        if(entry==null || entry.texture==null)return 0;
        var texture=Minecraft.getInstance().getTextureManager().getTexture(entry.texture);
        if(!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels()==null)return 0;
        var image=dynamic.getPixels();
        double fx=(u-col*(double)layer.tileSize())/layer.tileSize(),fy=(v-row*(double)layer.tileSize())/layer.tileSize();
        double[] m=layer.matrix();
        double rx=Math.min(8,(Math.abs(m[0])+Math.abs(m[2]))*.5*image.getWidth()/layer.tileSize());
        double ry=Math.min(8,(Math.abs(m[3])+Math.abs(m[5]))*.5*image.getHeight()/layer.tileSize());
        int x0=Math.clamp((int)Math.floor(fx*image.getWidth()-rx),0,image.getWidth()-1),y0=Math.clamp((int)Math.floor(fy*image.getHeight()-ry),0,image.getHeight()-1);
        int x1=Math.clamp((int)Math.ceil(fx*image.getWidth()+rx),x0+1,image.getWidth()),y1=Math.clamp((int)Math.ceil(fy*image.getHeight()+ry),y0+1,image.getHeight());
        int r=0,g=0,b=0,count=0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
            int color=image.getPixel(x,y);if((color>>>24)!=255)continue;
            r+=(color>>16)&255;g+=(color>>8)&255;b+=color&255;count++;
        }
        return count==0?0:0xFF000000|(r/count)<<16|(g/count)<<8|b/count;
    }
    public List<Resident> resident(URI root,int tileSize,double left,double top,double right,double bottom) {
        List<Resident> result=new ArrayList<>();
        for(var entry:entries.entrySet())if(entry.getValue().texture!=null) {
            TilePyramid.Tile tile=TilePyramid.parse(entry.getKey());
            if(tile!=null && tile.root().equals(root) && tile.intersects(tileSize,left,top,right,bottom)){entry.getValue().lastVisible=viewFrame;result.add(new Resident(tile,entry.getValue().texture));}
        }
        result.sort(Comparator.comparingInt((Resident r)->r.tile.level()).reversed());
        return result;
    }
    public List<TilePyramid.Tile> known(URI root,int tileSize,double left,double top,double right,double bottom) {
        var tiles=index.get(root);if(tiles==null)return List.of();
        double cx=(left+right)/2,cy=(top+bottom)/2;
        return tiles.values().stream().filter(t->t.intersects(tileSize,left,top,right,bottom))
                .sorted(Comparator.comparingInt(TilePyramid.Tile::level).thenComparingDouble(t->Math.hypot((t.col()+.5)*t.size(tileSize)-cx,(t.row()+.5)*t.size(tileSize)-cy)))
                .limit(128).toList();
    }
    private void remember(URI uri) {
        var tile=TilePyramid.parse(uri);if(tile!=null)index.computeIfAbsent(tile.root(),k->new HashMap<>()).put(uri,tile);
    }

    // All GPU work happens during ticks, outside GUI render-state extraction.
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if(overview!=null)overview.tick();
        for(int count=0;count<2048;count++) {URI uri=discovered.poll();if(uri==null)break;remember(uri);}
        for (int uploads=0;;) {
            Download d=completed.peek();if(d==null || (d.png!=null && uploads>=8))break;completed.poll();
            Entry e = d.owner;
            if (entries.get(d.uri) != e) continue;
            if(d.local)pendingLocal--;else pending--;
            e.loading=false;if(d.local)e.diskChecked=true;else e.job=null;
            if(d.checkedAt>0)e.updated=d.checkedAt;
            if(!d.error.isEmpty()) {e.failures++;long delay=d.error.startsWith("HTTP 404")?60000:Math.min(30000,1000L<<Math.min(5,e.failures));e.retryAt=System.currentTimeMillis()+delay;lastError=d.error;}
            else if(!d.local) {e.retryAt=0;e.failures=0;lastError="";}
            if(d.png==null)continue;
            try {
                NativeImage image = NativeImage.read(d.png);
                uploads++;
                Identifier id = Identifier.fromNamespaceAndPath("satellite_map",texturePrefix+serial++);
                DynamicTexture texture = new DynamicTexture(() -> "Satellite map tile",image);
                e.bytes=(long)image.getWidth()*image.getHeight()*4;
                mc.getTextureManager().register(id,texture);
                if (e.texture != null) mc.getTextureManager().release(e.texture);
                e.texture = id;
                remember(d.uri);
            } catch (Exception ex) { e.retryAt = System.currentTimeMillis()+30000; lastError = ex.getMessage(); }
        }
        long bytes=entries.values().stream().mapToLong(e->e.bytes).sum();
        int textureCount=(int)entries.values().stream().filter(e->e.texture!=null).count();
        // Requests and cache misses are metadata, not GPU textures. Never evict an image
        // merely because many requests exist; keep currently displayed images pinned.
        for(Entry e:entries.values()) {
            if(textureCount<=maxEntries && bytes<=64L*1024*1024)break;
            if(e.texture==null || e.loading || (e.lastVisible>=0 && e.lastVisible>=viewFrame-1 && textureCount<=maxEntries*2 && bytes<=128L*1024*1024))continue;
            mc.getTextureManager().release(e.texture);e.texture=null;bytes-=e.bytes;e.bytes=0;e.diskChecked=false;textureCount--;
        }
        var it=entries.entrySet().iterator();while(entries.size()>8192 && it.hasNext()){Entry e=it.next().getValue();if(e.texture==null && !e.loading)it.remove();}
    }
    public void clear() {
        for (Entry e : entries.values()) if (e.texture != null) Minecraft.getInstance().getTextureManager().release(e.texture);
        entries.clear(); lastError = "";
        // Discard work queued for a previous world; active requests may still populate disk.
        localWorkers.getQueue().clear();workers.getQueue().clear();completed.clear();pending=0;pendingLocal=0;
    }
    @Override public void close() { closed = true; localWorkers.shutdownNow();workers.shutdownNow(); completed.clear(); clear();if(overview!=null)overview.close(); }
}
