package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.OverlayGeometry;
import com.bilicraft.satellitemap.map.OverlayGeometry.Point;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousOverlayTest {
    private List<Point> rectangle(double x,double y,double w,double h){return List.of(new Point(x,y),new Point(x+w,y),new Point(x+w,y+h),new Point(x,y+h));}
    private double area(List<OverlayGeometry.Quad> quads){double sum=0;for(var q:quads){var p=q.vertices();double a=0;for(int i=0;i<4;i++){var v=p.get(i);var next=p.get((i+1)%4);a+=v.x()*next.y()-next.x()*v.y();}sum+=Math.abs(a)/2;}return sum;}
    @Test void rectangleKeepsFractionalEdgesAndUsesOneQuad(){var q=OverlayGeometry.fillQuads(rectangle(.125,.375,10.5,8.25),-100,-100,100,100);assertEquals(1,q.size());assertEquals(.125,q.getFirst().a().x());assertEquals(.375,q.getFirst().a().y());assertEquals(86.625,area(q),1e-9);}
    @Test void diagonalAndConcaveFillsHaveExactArea(){
        var p=List.of(new Point(0,0),new Point(10,0),new Point(10,3),new Point(3,3),new Point(3,10),new Point(0,10));
        assertEquals(51,area(OverlayGeometry.fillQuads(p,-20,-20,20,20)),1e-9);
        assertEquals(50,area(OverlayGeometry.fillQuads(List.of(new Point(0,0),new Point(10,0),new Point(0,10)),-20,-20,20,20)),1e-9);
    }
    @Test void clippedDisconnectedConcavePartsDoNotFillTheGap(){
        var p=List.of(new Point(-2,-2),new Point(2,-2),new Point(2,6),new Point(6,6),new Point(6,-2),new Point(10,-2),new Point(10,10),new Point(-2,10));
        var quads=OverlayGeometry.fillQuads(p,0,0,8,4);assertEquals(16,area(quads),1e-9);assertFalse(quads.stream().anyMatch(q->OverlayGeometry.contains(q.vertices(),4,2)));
    }
    @Test void hugeBorderClipsBeforeConvertingCoordinatesToGpuFloats(){var q=OverlayGeometry.fillQuads(rectangle(-1e12,-1e12,2e12,2e12),.25,.375,100.25,64.375);assertEquals(6400,area(q),1e-5);for(var quad:q)for(var p:quad.vertices()){assertTrue(p.x()>=.25 && p.x()<=100.25);assertTrue(p.y()>=.375 && p.y()<=64.375);}}
    @Test void fractionalPanningTranslatesWholeMeshWithoutChangingItsTopology(){
        var p=List.of(new Point(1.25,2.5),new Point(18.25,5.5),new Point(3.25,17.5));var before=OverlayGeometry.fillQuads(p,-100,-100,100,100);
        for(double step:new double[]{.05,.125,.5}){var after=OverlayGeometry.fillQuads(p.stream().map(v->new Point(v.x()-step,v.y()+step)).toList(),-100,-100,100,100);assertEquals(before.size(),after.size());for(int i=0;i<before.size();i++)for(int j=0;j<4;j++){assertEquals(before.get(i).vertices().get(j).x()-step,after.get(i).vertices().get(j).x(),1e-9);assertEquals(before.get(i).vertices().get(j).y()+step,after.get(i).vertices().get(j).y(),1e-9);}}
    }
    @Test void lineThicknessDoesNotChangeAsItMovesAcrossPixelBoundaries(){var a=new Point(-20.2,10.6);var b=new Point(80.7,50.2);var q=OverlayGeometry.stroke(a,b,3);assertNotNull(q);assertEquals(3,Math.hypot(q.a().x()-q.d().x(),q.a().y()-q.d().y()),1e-10);assertTrue((q.b().x()-q.a().x())*(q.d().y()-q.a().y())-(q.b().y()-q.a().y())*(q.d().x()-q.a().x())>0,"Stroke winding must match the fill winding");var moved=OverlayGeometry.stroke(new Point(a.x()+.125,a.y()-.375),new Point(b.x()+.125,b.y()-.375),3);for(int i=0;i<4;i++){assertEquals(q.vertices().get(i).x()+.125,moved.vertices().get(i).x(),1e-9);assertEquals(q.vertices().get(i).y()-.375,moved.vertices().get(i).y(),1e-9);}assertNull(OverlayGeometry.stroke(a,a,3));}
    @Test void outlineLabelStaysOnTheSameEdgeWhenVisibleSideLengthsSwap(){var p=rectangle(0,0,100,100);for(double offset:new double[]{-.1,0,.1}){var anchor=OverlayGeometry.visibleEdge(p,true,-20,-20,80+offset,80-offset);assertNotNull(anchor);assertEquals(0,anchor.y());assertEquals((80+offset)/2,anchor.x(),1e-9);}}
}
