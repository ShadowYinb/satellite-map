package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadarWaterTest {
    @Test void levelMobsStayOpaqueAndHeightFadesContinuously(){
        assertEquals(1,RadarAppearance.opacity(0,2,32));assertEquals(.5,RadarAppearance.opacity(32,2,32));
        double previous=1;for(int y=1;y<=128;y++){double value=RadarAppearance.opacity(y,2,32);assertTrue(value<=previous);assertTrue(value>=.06);previous=value;}
    }
    @Test void zoomingOutMakesHeightFadeLessAggressive(){for(int z=-5;z<9;z++)assertTrue(RadarAppearance.opacity(32,z,32)<RadarAppearance.opacity(32,z+1,32) || RadarAppearance.opacity(32,z,32)==.06);}
    @Test void fadeIsSymmetricAndDoesNotChangeRgb(){assertEquals(RadarAppearance.opacity(40,2,32),RadarAppearance.opacity(-40,2,32));assertEquals(0x80AABBCC,RadarAppearance.fade(0xFFAABBCC,.5));assertEquals(0x40AABBCC,RadarAppearance.fade(0x80AABBCC,.5));}
    @Test void largerHeightDistanceRetainsMoreVisibleMobs(){assertTrue(RadarAppearance.opacity(64,2,64)>RadarAppearance.opacity(64,2,16));}
    @Test void waterCanMatchHueChangesRejectedByLandCalibration(){
        int source=0xFF173A91,target=0xFF207C91;assertFalse(TerrainPalette.compatible(source,target));
        var palette=new WaterPalette();var key=WaterPalette.key(0x3F76E4,3);for(int i=0;i<32;i++)palette.observe(key,source,target);
        assertEquals(target,palette.apply(key,source));
    }
    @Test void waterDepthAndBiomeAreKeptSeparate(){
        var palette=new WaterPalette();WaterPalette.Key shallow=WaterPalette.key(1,1),deep=WaterPalette.key(1,12),other=WaterPalette.key(2,1);
        for(int i=0;i<32;i++){palette.observe(shallow,0xFF204080,0xFF368CAC);palette.observe(deep,0xFF204080,0xFF123755);}
        assertEquals(0xFF368CAC,palette.apply(shallow,0xFF204080));assertEquals(0xFF123755,palette.apply(deep,0xFF204080));assertEquals(0xFF204080,palette.apply(other,0xFF204080));
    }
    @Test void landAndMissingSamplesNeverRecolorWater(){var palette=new WaterPalette();var k=WaterPalette.key(1,3);for(int i=0;i<64;i++){palette.observe(k,0xFF204080,0);palette.observe(k,0xFF204080,0xFF508030);}assertEquals(0xFF204080,palette.apply(k,0xFF204080));}
    @Test void deepWaterShowsLessBottomColor(){int shallow=WaterPalette.base(0xFF204080,0xFFCCAA66,1),deep=WaterPalette.base(0xFF204080,0xFFCCAA66,16);assertTrue(((shallow>>16)&255)>((deep>>16)&255));}
    @Test void waterCalibrationIsStableAndResets(){var p=new WaterPalette();var k=WaterPalette.key(1,3);for(int i=0;i<32;i++)p.observe(k,0xFF204080,0xFF368CAC);for(int i=0;i<64;i++)p.observe(k,0xFF204080,0xFF123755);assertEquals(0xFF368CAC,p.apply(k,0xFF204080));p.clear();assertEquals(0xFF204080,p.apply(k,0xFF204080));}
}
