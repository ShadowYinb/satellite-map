package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MapConfigTest {
    @TempDir Path folder;
    @Test void defaultMappingAndEditsSurviveUtf8Save() throws Exception {
        Path path=folder.resolve("satellite-map.json");MapConfig config=MapConfig.load(path);
        assertEquals("mc.bilicraft.com:25577",config.profiles.getFirst().server);
        config.profiles.getFirst().name="中文地图测试";config.save(path);
        assertEquals("中文地图测试",MapConfig.load(path).profiles.getFirst().name);
        assertFalse(Files.exists(folder.resolve("satellite-map.json.tmp")));
    }
    @Test void invalidJsonIsBackedUpWithoutLosingOriginalContents() throws Exception {
        Path path=folder.resolve("satellite-map.json");Files.writeString(path,"{broken: [");
        MapConfig config=MapConfig.load(path);assertEquals(1,config.profiles.size());
        try(var files=Files.list(folder)) {
            Path backup=files.filter(p->p.getFileName().toString().contains(".invalid-")).findFirst().orElseThrow();
            assertEquals("{broken: [",Files.readString(backup));
        }
    }
    @Test void emptyProfileListIsAnIntentionalUserChoice() throws Exception {
        Path path=folder.resolve("satellite-map.json");MapConfig config=MapConfig.defaults();
        config.profiles.clear();config.save(path);assertTrue(MapConfig.load(path).profiles.isEmpty());
    }
}
