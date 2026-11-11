package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DisplayPreferencesTest {
    @TempDir Path folder;
    @Test void everyDefaultIconAndFontUsesSizeSix() {
        var config=MapConfig.defaults();for(var options:new OverlaySettings[]{config.minimapOverlays,config.fullmapOverlays}){
            assertEquals(6,options.playerSize);assertEquals(6,options.markerSize);assertEquals(6,options.selfSize);assertEquals(6,options.mobSize);assertEquals(6,options.textSize);
        }
    }
    @Test void mapFontSizesPersistIndependentlyFromIconsAndEachOther() throws Exception {
        var config=MapConfig.defaults();config.minimapOverlays.textSize=5;config.fullmapOverlays.textSize=12;
        Path path=folder.resolve("config.json");config.save(path);var loaded=MapConfig.load(path);
        assertEquals(5,loaded.minimapOverlays.textSize);assertEquals(12,loaded.fullmapOverlays.textSize);assertEquals(6,loaded.minimapOverlays.playerSize);
    }
    @Test void oldDefaultsMigrateButCustomSizesArePreserved() throws Exception {
        Path path=folder.resolve("config.json");Files.writeString(path,"{\"profiles\":[],\"displayRevision\":130,\"minimapOverlays\":{\"playerSize\":10,\"selfSize\":8,\"markerSize\":12,\"mobSize\":9},\"fullmapOverlays\":{\"playerSize\":16}}");
        var c=MapConfig.load(path);assertEquals(6,c.minimapOverlays.playerSize);assertEquals(6,c.minimapOverlays.selfSize);assertEquals(6,c.minimapOverlays.markerSize);assertEquals(6,c.minimapOverlays.mobSize);assertEquals(16,c.fullmapOverlays.playerSize);
    }
}
