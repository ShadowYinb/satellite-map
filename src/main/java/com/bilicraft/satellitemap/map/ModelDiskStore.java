package com.bilicraft.satellitemap.map;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Keeps the projection and world list available when a known website is offline. */
public final class ModelDiskStore {
    private static final Gson GSON=new Gson();
    private final Path root;
    public ModelDiskStore(Path root) {this.root=root;}
    public DynmapModel read(URI base) {
        try {
            Path file=root.resolve(TileDiskStore.key(base)+".json");
            if(Files.size(file)>4*1024*1024)return null;
            DynmapModel model=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),DynmapModel.class);
            if(model==null || !base.equals(model.base()) || model.tiles()==null
                    || !java.util.Set.of("http","https").contains(model.tiles().getScheme())
                    || model.updateTemplate()==null || model.worlds()==null || model.worlds().isEmpty())return null;
            for(var world:model.worlds()) {
                if(world.name()==null || world.layers()==null || world.layers().isEmpty())return null;
                for(var layer:world.layers())if(layer.matrix()==null || layer.matrix().length!=9 || layer.tileSize()<1
                        || layer.tileSize()>2048 || layer.maxZoomOut()<0 || layer.maxZoomOut()>12 || layer.maxZoomIn()<0 || layer.maxZoomIn()>6)return null;
            }
            return model;
        } catch(IOException | RuntimeException e) {return null;}
    }
    public void write(DynmapModel model) throws IOException {
        Files.createDirectories(root);Path file=root.resolve(TileDiskStore.key(model.base())+".json");
        Path temp=Files.createTempFile(root,"model-",".tmp");
        try {
            Files.writeString(temp,GSON.toJson(model),StandardCharsets.UTF_8);
            try {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
}
