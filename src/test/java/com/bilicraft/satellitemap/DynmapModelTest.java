package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.DynmapModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynmapModelTest {
    private DynmapModel.Layer flat() {
        return new DynmapModel.Layer("flat","flat","jpg",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
    }
    @Test void flatProjectionAndInverseMatchDynmapJavascript() {
        var layer=flat();var p=layer.project(4117,69,-591);
        assertEquals(16468,p.x());assertEquals(-2236,p.y());
        var inverse=layer.unproject(p.x(),p.y(),69);
        assertEquals(4117,inverse.x(),1e-6);assertEquals(-591,inverse.y(),1e-6);
    }
    @Test void negativeTileGroupsUseArithmeticShiftAndDynmapYDirection() {
        assertEquals("Paralon/flat/-1_-1/-1_-1.jpg",flat().tilePath("Paralon",0,-1,1));
        assertEquals("Paralon/flat/4_0/zz_128_20.jpg",flat().tilePath("Paralon",2,32,-5));
        assertEquals("Paralon/flat/-1_0/zz_-4_4.jpg",flat().tilePath("Paralon",2,-1,-1));
    }
    @Test void isometricProjectionRoundTripsAtPlayerHeight() {
        var layer=new DynmapModel.Layer("surface","t","jpg",128,5,4,
                new double[]{2.82842712474619,0,-2.82842712474619,-1.414213562373095,3.4641016151377544,-1.414213562373095,0,1,0},false);
        var point=layer.project(-1234.5,117,987.25);var inverse=layer.unproject(point.x(),point.y(),117);
        assertEquals(-1234.5,inverse.x(),1e-6);assertEquals(987.25,inverse.y(),1e-6);
    }
    @Test void zoomScaleMatchesNativeAndOverzoomLevels() {
        assertEquals(.25,flat().scale(2));assertEquals(4,flat().scale(-2));
    }
}
