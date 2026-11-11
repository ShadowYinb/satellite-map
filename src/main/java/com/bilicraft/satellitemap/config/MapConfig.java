package com.bilicraft.satellitemap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean minimapEnabled = true;
    public boolean autoDetect = true;
    public int manualProfile = 0;
    public int minimapSize = 160;
    // Zero preserves the square size from 1.0.0 configurations.
    public int minimapWidth = 0, minimapHeight = 0;
    public int offsetX = 0, offsetY = 0;
    public int corner = 0;
    public int zoom = 2;
    public int refreshSeconds = 60;
    public OverlaySettings minimapOverlays=OverlaySettings.minimapDefaults();
    public OverlaySettings fullmapOverlays=new OverlaySettings();
    public int displayRevision=0;
    public List<MapProfile> profiles = new ArrayList<>();

    public static MapConfig defaults() {
        MapConfig c = new MapConfig();
        c.displayRevision=140;
        MapProfile p = new MapProfile();
        p.name = "Bilicraft · Hyperion";
        c.profiles.add(p);
        return c;
    }

    public static MapConfig load(Path path) throws IOException {
        if (!Files.exists(path)) { MapConfig c = defaults(); c.save(path); return c; }
        try {
            MapConfig c = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MapConfig.class);
            if (c == null || c.profiles == null) throw new IllegalArgumentException("配置为空");
            c.profiles.removeIf(p -> p == null);
            for (MapProfile p : c.profiles) {
                if (p.name == null) p.name = "地图";
                if (p.server == null) p.server = "";
                if (p.url == null) p.url = "";
                if (p.world == null) p.world = "";
                if (p.map == null) p.map = "";
                if (p.dimension == null) p.dimension = "";
            }
            c.minimapSize = Math.clamp(c.minimapSize, 96, 256);
            c.minimapWidth=c.width();c.minimapHeight=c.height();
            c.offsetX=Math.clamp(c.offsetX,-20000,20000);c.offsetY=Math.clamp(c.offsetY,-20000,20000);
            c.corner = Math.floorMod(c.corner, 4);
            c.zoom = Math.clamp(c.zoom, -6, 16);
            c.refreshSeconds = Math.clamp(c.refreshSeconds, 15, 3600);
            if(c.minimapOverlays==null)c.minimapOverlays=OverlaySettings.minimapDefaults();
            if(c.fullmapOverlays==null)c.fullmapOverlays=new OverlaySettings();
            c.minimapOverlays.normalize();c.fullmapOverlays.normalize();
            if(c.displayRevision<130){c.minimapOverlays.tabDetails=true;c.minimapOverlays.labels=true;c.minimapOverlays.playerNames=true;c.displayRevision=130;}
            if(c.displayRevision<140){c.minimapOverlays.migrateSizes();c.fullmapOverlays.migrateSizes();c.displayRevision=140;}
            return c;
        } catch (RuntimeException ex) {
            // Preserve damaged user data for recovery, never silently overwrite it.
            Files.copy(path, path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis()));
            MapConfig c = defaults(); c.save(path); return c;
        }
    }

    public int width() {return Math.clamp(minimapWidth>0?minimapWidth:minimapSize,96,320);}
    public int height() {return Math.clamp(minimapHeight>0?minimapHeight:minimapSize,96,320);}

    public void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
