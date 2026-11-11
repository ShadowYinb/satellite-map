package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BorderAndDetailTest {
    private final List<OverlayGeometry.Point> border=List.of(new OverlayGeometry.Point(10,10),new OverlayGeometry.Point(110,10),new OverlayGeometry.Point(110,110),new OverlayGeometry.Point(10,110));
    @Test void outlineDoesNotSelectWholeInterior() {
        assertFalse(OverlayGeometry.hit(border,true,false,60,60,5));
        assertTrue(OverlayGeometry.hit(border,true,false,12,60,5));
        assertTrue(OverlayGeometry.hit(border,true,false,8,60,5));
        assertFalse(OverlayGeometry.hit(border,true,false,0,60,5));
        assertTrue(OverlayGeometry.hit(border,true,true,60,60,5));
    }
    @Test void websiteBorderRemainsAnUnfilledOutline() {
        var json=com.google.gson.JsonParser.parseString("""
                {"sets":{"markers":{"areas":{"_worldborder_Paralon":{"label":"世界边界","x":[1330.5,23330.5,23330.5,1330.5],"z":[-16632.5,-16632.5,5367.5,5367.5],"ytop":-62,"fillcolor":"#000000","fillopacity":0,"color":"#FF0000","weight":3,"opacity":0.8}}}}}
                """).getAsJsonObject();
        var shape=OverlayModel.parse(json).sets().getFirst().shapes().getFirst();
        assertEquals(0,shape.style().fill()>>>24);
        var points=shape.points().stream().map(p->new OverlayGeometry.Point(p.x(),p.z())).toList();
        assertFalse(OverlayGeometry.hit(points,true,false,4117,-591,5));
        assertTrue(OverlayGeometry.hit(points,true,false,1328.5,-591,5));
    }
    @Test void offscreenBorderHasNoFloatingCenterLabel() {
        assertNull(OverlayGeometry.visibleEdge(border,true,20,20,100,100));
        var point=OverlayGeometry.visibleEdge(border,true,5,20,100,100);
        assertNotNull(point);assertEquals(10,point.x());
    }
    @Test void openLinesAndDegenerateEdgesAreSelectable() {
        var line=List.of(new OverlayGeometry.Point(10,10),new OverlayGeometry.Point(10,10),new OverlayGeometry.Point(110,10));
        assertTrue(OverlayGeometry.hit(line,false,false,60,8,3));
        assertTrue(OverlayGeometry.hit(line,false,false,9,9,3));
        assertFalse(OverlayGeometry.hit(line,false,false,60,60,3));
    }
    @Test void persistentPreviewHasAtLeastTwoTexelsPerGuiPixel() {
        for(int zoom=2;zoom<=16;zoom++) {
            int level=TilePyramid.overviewDetailLevel(zoom,18);
            assertTrue(Math.scalb(1.0,level-zoom)<=.5);
        }
    }
    @Test void fullscreenRequestsFinerTilesAcrossEntireViewport() {
        var layer=new DynmapModel.Layer("flat","flat","png",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
        var model=new DynmapModel(URI.create("https://example.test/"),URI.create("https://example.test/tiles/"),"",List.of());
        int small=TilePyramid.detailLevel(layer,0,0,8192,4096,96),full=TilePyramid.detailLevel(layer,0,0,8192,4096,384);
        assertTrue(full<small);
        var requests=TilePyramid.requests(model,"world",layer,4,0,0,8192,4096,384);
        var expected=TilePyramid.visible(model,"world",layer,full,0,0,8192,4096);
        assertTrue(requests.containsAll(expected));
        assertEquals(layer.maxZoomOut(),requests.getFirst().level());
    }
}
