package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.MapConfig;
import com.bilicraft.satellitemap.ui.MinimapLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MinimapLayoutTest {
    @TempDir Path folder;
    @Test void oldSquareSizeMigratesWithoutChangingSavedLayout() throws Exception {
        Path path=folder.resolve("config.json");Files.writeString(path,"{\"minimapSize\":224,\"corner\":1,\"profiles\":[]}");
        var c=MapConfig.load(path);assertEquals(224,c.width());assertEquals(224,c.height());
        var b=MinimapLayout.bounds(c,900,600);assertEquals(10,b.x());assertEquals(10,b.y());
    }
    @Test void arbitraryDraggedPositionAndRectangleSurviveSave() throws Exception {
        var c=MapConfig.defaults();c.minimapWidth=240;c.minimapHeight=128;
        MinimapLayout.place(c,900,600,333,222);Path path=folder.resolve("config.json");c.save(path);
        var loaded=MapConfig.load(path);var b=MinimapLayout.bounds(loaded,900,600);
        assertEquals(333,b.x());assertEquals(222,b.y());assertEquals(240,b.width());assertEquals(128,b.height());
    }
    @Test void dragCoordinatesWorkForEveryAnchor() {
        for(int corner=0;corner<4;corner++) {
            var c=MapConfig.defaults();c.corner=corner;MinimapLayout.place(c,900,600,200,150);
            var b=MinimapLayout.bounds(c,900,600);assertEquals(200,b.x());assertEquals(150,b.y());
        }
    }
    @Test void shrinkingWindowKeepsMapAndFooterOnScreen() {
        var c=MapConfig.defaults();c.minimapWidth=320;c.minimapHeight=320;
        MinimapLayout.place(c,1500,900,1000,400);var b=MinimapLayout.bounds(c,427,240);
        assertTrue(b.x()>=4 && b.y()>=4);assertTrue(b.x()+b.width()<=427);assertTrue(b.y()+b.height()+30<=240);
    }
}
