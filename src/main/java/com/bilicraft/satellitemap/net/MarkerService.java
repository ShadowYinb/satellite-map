package com.bilicraft.satellitemap.net;

import com.bilicraft.satellitemap.map.*;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Marker polling is independent of player discovery and tile downloads. */
public final class MarkerService implements AutoCloseable {
    public record Snapshot(OverlayModel data,long observedAt,String error) {}
    private static final class Entry {
        volatile Snapshot snapshot=new Snapshot(OverlayModel.EMPTY,0,"");
        volatile long nextPoll;volatile boolean loading;
    }
    private final Map<URI,Entry> entries=new LinkedHashMap<>(32,.75f,true);
    private final Path root;
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"satellite-map-markers");t.setDaemon(true);return t;});
    public MarkerService(Path root) {this.root=root;}
    public static URI endpoint(DynmapModel model,String world) {return model.markerRoot().resolve("_markers_/marker_"+DynmapModel.encode(world)+".json");}
    public Snapshot request(DynmapModel model,String world) {
        URI uri=endpoint(model,world);Entry entry=entries.computeIfAbsent(uri,k->new Entry());
        long now=System.currentTimeMillis();
        if(!entry.loading && now>=entry.nextPoll) {
            entry.loading=true;entry.nextPoll=now+15000;
            workers.execute(()->{
                Path file=root.resolve(TileDiskStore.key(uri)+".json");
                try {
                    if(entry.snapshot.observedAt==0 && Files.exists(file) && Files.size(file)<=8*1024*1024) {
                        try {var cached=OverlayModel.parse(JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject());
                            entry.snapshot=new Snapshot(cached,Files.getLastModifiedTime(file).toMillis(),"使用缓存图层");}catch(Exception ignored){}
                    }
                    String raw=new String(WebAccess.get(uri,8*1024*1024),StandardCharsets.UTF_8);
                    OverlayModel data=OverlayModel.parse(JsonParser.parseString(raw).getAsJsonObject());
                    entry.snapshot=new Snapshot(data,System.currentTimeMillis(),"");
                    try {
                        Files.createDirectories(root);Path temp=Files.createTempFile(root,"markers-",".tmp");
                        try {Files.writeString(temp,raw,StandardCharsets.UTF_8);
                            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
                        }finally{Files.deleteIfExists(temp);}
                    }catch(Exception ignored){}
                }catch(Exception e){entry.snapshot=new Snapshot(entry.snapshot.data,entry.snapshot.observedAt,"图层暂不可用: "+Objects.toString(e.getMessage(),"网络错误"));}
                finally{entry.nextPoll=System.currentTimeMillis()+15000;entry.loading=false;}
            });
        }
        if(entries.size()>16) {var iterator=entries.entrySet().iterator();while(entries.size()>16 && iterator.hasNext())if(!iterator.next().getValue().loading)iterator.remove();}
        return entry.snapshot;
    }
    @Override public void close(){workers.shutdownNow();entries.clear();}
}
