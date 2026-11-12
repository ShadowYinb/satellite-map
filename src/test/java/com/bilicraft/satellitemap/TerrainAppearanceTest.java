package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainAppearanceTest {
    @Test void biomeTintMultipliesTextureInsteadOfReplacingIt() {
        assertEquals(0xFF406020,TerrainPalette.multiply(0xFF808080,0xFF80C040));
        assertEquals(0xFF884422,TerrainPalette.multiply(0xFF884422,-1));
    }
    @Test void matchesMaterialToWebsitePaletteAfterEnoughEvidence() {
        var palette=new TerrainPalette();
        for(int i=0;i<32;i++)palette.observe("grass",0xFF648C3C,0xFF507030);
        assertEquals(0xFF507030,palette.apply("grass",0xFF648C3C));
        assertEquals(0xFF648C3C,palette.apply("other-world-grass",0xFF648C3C));
    }
    @Test void oldWebsiteTerrainCannotRecolorNewRedOrBlueBlocks() {
        var palette=new TerrainPalette();
        for(int i=0;i<100;i++){palette.observe("red",0xFFAA3322,0xFF507030);palette.observe("blue",0xFF2233AA,0xFF507030);}
        assertEquals(0xFFAA3322,palette.apply("red",0xFFAA3322));
        assertEquals(0xFF2233AA,palette.apply("blue",0xFF2233AA));
    }
    @Test void missingTilesAndShadowsDoNotBecomeCalibrationData() {
        var palette=new TerrainPalette();
        for(int i=0;i<64;i++){palette.observe("grass",0xFF648C3C,0);palette.observe("grass",0xFF648C3C,0xFF020301);}
        assertEquals(0xFF648C3C,palette.apply("grass",0xFF648C3C));
    }
    @Test void calibrationStaysFixedWhileMovingAndResetsForNewWorld() {
        var palette=new TerrainPalette();for(int i=0;i<32;i++)palette.observe("grass",0xFF648C3C,0xFF507030);
        for(int i=0;i<100;i++)palette.observe("grass",0xFF648C3C,0xFF82A04A);
        assertEquals(0xFF507030,palette.apply("grass",0xFF648C3C));
        palette.clear();assertEquals(0xFF648C3C,palette.apply("grass",0xFF648C3C));
    }
    @Test void slowMovementRetainsEveryFractionalFrameStep() {
        double previous=MapViewport.tile(-1,2,32,10,20,7.1,9.3).x();
        for(int frame=1;frame<=20;frame++){
            var current=MapViewport.tile(-1,2,32,10,20,7.1+frame*.05,9.3);
            assertEquals(-.05,current.x()-previous,1e-10);assertEquals(32,current.size());previous=current.x();
        }
    }
    @Test void adjacentTilesAndDifferentLayersMoveTogetherAtNegativeCoordinates() {
        for(int zoom=-4;zoom<=9;zoom++) {
            double size=Math.scalb(128.0,-zoom),left=-12345.125;
            var a=MapViewport.tile(-3,-4,size,7,9,left,987.375);
            var b=MapViewport.tile(-2,-4,size,7,9,left,987.375);
            var parent=MapViewport.tile(-2,-2,size*2,7,9,left,987.375);
            assertEquals(a.x()+size,b.x(),1e-9);
            assertEquals(a.x()-size,parent.x(),1e-9);
            assertEquals(a.y(),parent.y(),1e-9);
        }
    }
}
