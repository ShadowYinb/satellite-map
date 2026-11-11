package com.bilicraft.satellitemap.net;

import com.bilicraft.satellitemap.map.DynmapModel;
import com.bilicraft.satellitemap.map.ModelDiskStore;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class DynmapService implements AutoCloseable {
    public record Player(String account, String world, double x, double y, double z,String label) {
        public Player(String account,String world,double x,double y,double z) {this(account,world,x,y,z,account);}
    }
    public record Snapshot(DynmapModel model, List<Player> players, long observedAt, String error) {}
    public static final class Site {
        public volatile Snapshot snapshot = new Snapshot(null,List.of(),0,"");
        private volatile long nextPoll;
        private volatile boolean loading;
        private volatile int generation;
        private long nextConfigRefresh;
    }
    private final ModelDiskStore disk;
    public DynmapService() {disk=null;}
    public DynmapService(Path directory) {disk=new ModelDiskStore(directory);}
    private final Map<URI,Site> sites = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r,"satellite-map-metadata"); t.setDaemon(true); return t;
    });

    // Called on the client thread. Publish each immutable snapshot in one volatile write.
    public Site request(URI base) {
        Site site = sites.computeIfAbsent(base, key -> new Site());
        long now = System.currentTimeMillis();
        if (!site.loading && now >= site.nextPoll) {
            site.loading = true; site.nextPoll = now + 6000;
            int generation=site.generation;
            executor.execute(() -> {
                Snapshot previous = site.snapshot;
                DynmapModel model=previous.model;String metadataError="";
                try {
                    if(model==null && disk!=null) {
                        model=disk.read(base);
                        if(model!=null)publish(site,generation,new Snapshot(model,List.of(),0,"正在验证本地地图配置…"));
                    }
                    if(model==null || System.currentTimeMillis()>=site.nextConfigRefresh) {
                        try {
                            model=discover(base);site.nextConfigRefresh=System.currentTimeMillis()+300000;
                            if(disk!=null)try {disk.write(model);}catch(IOException ignored){}
                        } catch(IOException e) {
                            site.nextConfigRefresh=System.currentTimeMillis()+30000;
                            if(model==null)throw e;
                            metadataError="网站暂不可用，使用本地地图配置";
                        }
                    }
                    String path = model.updateTemplate().replace("{world}",DynmapModel.encode(model.worlds().getFirst().name()))
                            .replace("{timestamp}",Long.toString(System.currentTimeMillis()));
                    List<Player> players = new ArrayList<>(); String error = "";
                    try {
                        JsonObject update = WebAccess.json(base.resolve(path));
                        if (update.has("players")) for (JsonElement e : update.getAsJsonArray("players")) {
                            JsonObject p = e.getAsJsonObject();
                            String world = DynmapModel.string(p,"world", "-some-other-bogus-world-");
                            if (model.world(world) == null || !p.has("x") || !p.has("z")) continue;
                            double x=p.get("x").getAsDouble(),y=p.has("y")?p.get("y").getAsDouble():64,z=p.get("z").getAsDouble();
                            if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))continue;
                            String account=DynmapModel.string(p,"account",DynmapModel.string(p,"name",""));
                            players.add(new Player(account,world,x,y,z,com.bilicraft.satellitemap.map.OverlayModel.plain(DynmapModel.string(p,"name",account))));
                        }
                    } catch (Exception e) { error = "玩家识别暂不可用: " + e.getMessage(); }
                    if(!metadataError.isEmpty())error=metadataError+(error.isEmpty()?"":" · "+error);
                    publish(site,generation,new Snapshot(model,List.copyOf(players),System.currentTimeMillis(),error));
                } catch (Exception e) {
                    publish(site,generation,new Snapshot(model,List.of(),0,"地图连接失败: " + e.getMessage()));
                } finally {
                    site.nextPoll = generation==site.generation?System.currentTimeMillis() + (site.snapshot.model == null ? 30000 : 6000):0;
                    site.loading = false;
                }
            });
        }
        return site;
    }
    private static void publish(Site site,int generation,Snapshot snapshot) {
        synchronized(site) {if(site.generation==generation)site.snapshot=snapshot;}
    }
    public void worldChanged() {
        for(Site site:sites.values()) {
            synchronized(site) {
                site.generation++;Snapshot old=site.snapshot;
                site.snapshot=new Snapshot(old.model,List.of(),0,"");site.nextPoll=0;
            }
        }
    }

    public static DynmapModel discover(URI base) throws IOException {
        String config = null, update = null, tiles = "tiles/", markers=null;
        try {
            String script = WebAccess.text(base.resolve("standalone/config.js"));
            config = option(script,"configuration"); update = option(script,"update");
            String tileOption = option(script,"tiles"); if (tileOption != null) tiles = tileOption;
            markers=option(script,"markers");
        } catch (IOException ignored) { /* Dynamic Dynmap installations need no config.js. */ }
        IOException last = null;
        List<String> endpoints = new ArrayList<>();
        if (config != null) endpoints.add(config);
        endpoints.add("standalone/dynmap_config.json"); endpoints.add("up/configuration");
        for (String endpoint : endpoints) {
            try {
                JsonObject json = WebAccess.json(base.resolve(endpoint.replace("{timestamp}",Long.toString(System.currentTimeMillis()))));
                if (!json.has("worlds")) throw new IOException("缺少 worlds；该地图可能需要登录");
                String updates = update != null ? update : endpoint.startsWith("up/") ? "up/world/{world}/0" : "standalone/dynmap_{world}.json";
                return DynmapModel.parse(base,base.resolve(tiles),updates,json).withMarkers(base.resolve(markers==null?tiles:markers));
            } catch (Exception e) { last = new IOException(e.getMessage(),e); }
        }
        throw new IOException("无法读取 Dynmap 配置，请检查网址和访问权限",last);
    }
    private static String option(String script,String key) {
        Matcher m = Pattern.compile("(?:['\"]?" + key + "['\"]?)\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(script);
        return m.find() ? m.group(1) : null;
    }
    public void reset() { sites.clear(); }
    @Override public void close() { executor.shutdownNow(); sites.clear(); }
}
