package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.map.OverlayGeometry.Point;
import com.bilicraft.satellitemap.map.OverlayGeometry.Quad;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionOutlineTest {
    private List<Point> rectangle(double x,double y,double w,double h){return List.of(new Point(x,y),new Point(x+w,y),new Point(x+w,y+h),new Point(x,y+h));}
    private double area(Quad q){var p=q.vertices();double area=0;for(int i=1;i<3;i++)area+=(p.get(i).x()-p.getFirst().x())*(p.get(i+1).y()-p.getFirst().y())-(p.get(i).y()-p.getFirst().y())*(p.get(i+1).x()-p.getFirst().x());return area/2;}
    private void inside(List<Quad> quads,List<Point> polygon){for(var q:quads){assertTrue(area(q)>0);for(var p:q.vertices())assertTrue(OverlayGeometry.hit(polygon,true,true,p.x(),p.y(),1e-7),p.toString());}}
    @Test void tinySquareNeverBecomesAnOversizedCross(){var p=rectangle(10.125,20.25,1,1);var q=RegionOutline.build(p,6,1);assertEquals(1,q.size());inside(q,p);assertEquals(1,q.stream().mapToDouble(this::area).sum(),1e-9);}
    @Test void joinedCornersArePresentAndLargeRectangleKeepsItsInterior(){var p=rectangle(0,0,20,10);var q=RegionOutline.build(p,2,1);assertEquals(4,q.size());assertTrue(q.stream().anyMatch(v->OverlayGeometry.contains(v.vertices(),.25,.125)));assertFalse(q.stream().anyMatch(v->OverlayGeometry.contains(v.vertices(),10,5)));assertEquals(104,q.stream().mapToDouble(this::area).sum(),1e-9);inside(q,p);}
    @Test void allZoomLevelsAndGuiScalesStayWithinTheRealRectangle(){for(int gui=1;gui<=4;gui++)for(int zoom=-4;zoom<=9;zoom++)for(double[] size:new double[][]{{20,50},{40,30}}){double scale=Math.scalb(4.0,-zoom);var p=rectangle(90.125,75.375,size[0]*scale,size[1]*scale);var q=RegionOutline.build(p,2.0/gui,1.0/gui);assertFalse(q.isEmpty());inside(q,p);assertTrue(q.stream().mapToDouble(this::area).sum()<=size[0]*size[1]*scale*scale+1e-5);}}
    @Test void reversedWindingStillProducesVisibleClockwiseGuiQuads(){var p=new ArrayList<>(rectangle(0,0,20,10));Collections.reverse(p);var q=RegionOutline.build(p,2,1);assertEquals(4,q.size());inside(q,p);assertEquals(104,q.stream().mapToDouble(this::area).sum(),1e-9);}
    @Test void repeatedClosingPointsDoNotCreateZeroLengthEdges(){var p=new ArrayList<>(rectangle(0,0,20,10));p.add(p.getFirst());p.add(p.getFirst());var q=RegionOutline.build(p,2,1);assertEquals(4,q.size());inside(q,rectangle(0,0,20,10));}
    @Test void rotatedRectanglesKeepTheirActualFootprint(){var p=List.of(new Point(10,0),new Point(30,10),new Point(25,20),new Point(5,10));var q=RegionOutline.build(p,2,1);assertEquals(4,q.size());inside(q,p);assertFalse(q.stream().anyMatch(v->OverlayGeometry.contains(v.vertices(),17.5,10)));}
    @Test void concaveAreasDoNotGrowSpikesIntoTheirNotches(){var p=List.of(new Point(0,0),new Point(10,0),new Point(10,3),new Point(3,3),new Point(3,10),new Point(0,10));var q=RegionOutline.build(p,2,1);assertFalse(q.isEmpty());inside(q,p);assertFalse(q.stream().anyMatch(v->OverlayGeometry.contains(v.vertices(),7,7)));}
    @Test void fractionalPanningPreservesTheJoinedBorder(){var p=rectangle(10.125,20.25,20,50);var before=RegionOutline.build(p,1,.5);var after=RegionOutline.build(p.stream().map(v->new Point(v.x()-.125,v.y()+.375)).toList(),1,.5);assertEquals(before.size(),after.size());for(int i=0;i<before.size();i++)for(int j=0;j<4;j++){assertEquals(before.get(i).vertices().get(j).x()-.125,after.get(i).vertices().get(j).x(),1e-9);assertEquals(before.get(i).vertices().get(j).y()+.375,after.get(i).vertices().get(j).y(),1e-9);}}
    @Test void degenerateAreasDoNotTurnIntoPointIcons(){assertTrue(RegionOutline.build(rectangle(0,0,0,20),2,1).isEmpty());assertTrue(RegionOutline.build(List.of(new Point(0,0),new Point(0,0)),2,1).isEmpty());}
    @Test void unevenlySpacedVerticesDoNotChangeRectangleThickness(){List<Point> p=new ArrayList<>();for(int i=0;i<=100;i++)p.add(new Point(i,0));p.add(new Point(100,20));p.add(new Point(0,20));var q=RegionOutline.build(p,2,1);assertEquals(464,q.stream().mapToDouble(this::area).sum(),1e-8);inside(q,p);}
}
