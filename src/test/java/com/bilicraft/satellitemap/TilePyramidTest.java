package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TilePyramidTest {
    @TempDir Path folder;
    private final DynmapModel.Layer layer=new DynmapModel.Layer("flat","flat","jpg",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
    private final DynmapModel model=new DynmapModel(URI.create("https://example.test/sub/"),URI.create("https://example.test/sub/tiles/"),"",List.of());
    @Test void everyLevelAndNegativeCoordinateRoundTripsThroughDynmapPaths() {
        for(int level=0;level<=5;level++)for(int col:new int[]{-33,-1,0,31,32})for(int row:new int[]{-33,-1,0,31,32}) {
            URI uri=model.tiles().resolve(layer.tilePath("中文世界",level,col,row));var tile=TilePyramid.parse(uri);
            assertNotNull(tile);assertEquals(col,tile.col());assertEquals(row,tile.row());assertEquals(level,tile.level());
            assertEquals(TilePyramid.root(model,"中文世界",layer),tile.root());
        }
    }
    @Test void parentAndChildrenCoverExactlyTheSameProjectedRegion() {
        for(int col:new int[]{-3,-1,0,2})for(int row:new int[]{-3,-1,0,2}) {
            double left=col*512.0,top=row*512.0;
            var fine=TilePyramid.visible(model,"world",layer,0,left,top,left+512,top+512);
            var coarse=TilePyramid.visible(model,"world",layer,2,left,top,left+512,top+512);
            assertEquals(16,fine.size());assertEquals(1,coarse.size());assertEquals(col,coarse.getFirst().col());assertEquals(row,coarse.getFirst().row());
            assertEquals(4*4*128*128,coarse.getFirst().size(128)*coarse.getFirst().size(128));
        }
    }
    @Test void cachedSmallTileIntersectsAtAnyDisplayZoom() {
        var tile=TilePyramid.parse(model.tiles().resolve(layer.tilePath("world",0,5,-3)));
        for(int zoom=-4;zoom<=5;zoom++) {
            double half=160/layer.scale(zoom)/2;
            assertTrue(tile.intersects(128,704-half,-320-half,704+half,-320+half));
        }
    }
    @Test void automaticDetailDownloadsRemainBoundedWhileZoomingOut() {
        for(int zoom=-4;zoom<=5;zoom++) {
            double width=800/layer.scale(zoom),height=400/layer.scale(zoom);
            int detail=TilePyramid.detailLevel(layer,-width/2,-height/2,width/2,height/2);
            var planned=TilePyramid.visible(model,"world",layer,detail,-width/2,-height/2,width/2,height/2);
            assertTrue(planned.size()<=96);assertFalse(planned.isEmpty());
        }
    }
    @Test void diskIndexDiscoversExistingLevelsAcrossNewInstances() throws Exception {
        var disk=new TileDiskStore(folder);URI fine=model.tiles().resolve(layer.tilePath("world",0,-2,3)),large=model.tiles().resolve(layer.tilePath("world",3,-1,0));
        disk.write(fine,new TileDiskStore.Tile(new byte[]{1,2,3},"",0,1));disk.write(large,new TileDiskStore.Tile(new byte[]{4,5,6},"",0,1));
        List<URI> found=new ArrayList<>();new TileDiskStore(folder).scan(found::add);
        assertEquals(Set.of(fine,large),Set.copyOf(found));
    }
    @Test void invalidTilePathsAndMarkerIconsAreExcluded() {
        assertNull(TilePyramid.parse(URI.create("https://example.test/tiles/_markers_/world.png")));
        assertNull(TilePyramid.parse(URI.create("https://example.test/tiles/world/flat/0_0/zz_1_0.jpg")));
        assertNull(TilePyramid.parse(URI.create("https://example.test/tiles/world/flat/1_0/0_0.jpg")));
    }
    @Test void baseTilesAreRequestedEvenAtExtremeZoomOut() {
        var core=TilePyramid.nearbyBase(model,"world",layer,-100000,-100000,100000,100000);
        assertEquals(24,core.size());assertTrue(core.stream().allMatch(t->t.level()==0));
    }
}
