package com.bilicraft.satellitemap.map;

import java.util.*;
import com.bilicraft.satellitemap.map.OverlayGeometry.Point;
import com.bilicraft.satellitemap.map.OverlayGeometry.Quad;

/** Joined, inward region borders never extend outside the actual land footprint. */
public final class RegionOutline {
    public static List<Quad> build(List<Point> input,double requestedWidth,double pixelSize) {
        List<Point> points=new ArrayList<>();
        for(var p:input)if(points.isEmpty() || distance(p,points.getLast())>1e-9)points.add(p);
        if(points.size()>1 && distance(points.getFirst(),points.getLast())<1e-9)points.removeLast();
        List<Point> corners=new ArrayList<>();
        for(int i=0;i<points.size();i++){
            var a=points.get(Math.floorMod(i-1,points.size()));var b=points.get(i);var c=points.get((i+1)%points.size());
            double dot=(b.x()-a.x())*(c.x()-b.x())+(b.y()-a.y())*(c.y()-b.y());
            if(Math.abs(cross(a,b,c))>1e-9*(distance(a,b)+distance(b,c)) || dot<0)corners.add(b);
        }
        points=corners;
        if(points.size()<3 || requestedWidth<=0)return List.of();
        double area=area(points);if(Math.abs(area)<1e-12)return List.of();
        double sign=Math.signum(area),minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        var origin=points.getFirst();double cx=0,cy=0;
        for(var p:points){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
        // Area weighting keeps dense runs of collinear vertices from pulling the center to an edge.
        for(int i=1;i+1<points.size();i++){var a=points.get(i);var b=points.get(i+1);double weight=cross(origin,a,b);cx+=((a.x()-origin.x())+(b.x()-origin.x()))*weight;cy+=((a.y()-origin.y())+(b.y()-origin.y()))*weight;}
        var center=new Point(origin.x()+cx/(6*area),origin.y()+cy/(6*area));
        boolean convex=true;double radius=Double.POSITIVE_INFINITY;
        List<Point> normals=new ArrayList<>();
        for(int i=0;i<points.size();i++) {
            var a=points.get(i);var b=points.get((i+1)%points.size());var c=points.get((i+2)%points.size());
            double dx=b.x()-a.x(),dy=b.y()-a.y(),length=Math.hypot(dx,dy);
            normals.add(new Point(-dy/length*sign,dx/length*sign));
            radius=Math.min(radius,Math.abs(cross(a,b,center))/length);
            if(cross(a,b,c)*sign< -1e-9)convex=false;
        }
        double span=convex?radius*2:Math.min(maxX-minX,maxY-minY);
        // Below four physical pixels, an accurately bounded solid footprint is clearer than
        // a stroke wider than the area. This does not enlarge or replace it with a point icon.
        if(span<pixelSize*4)return OverlayGeometry.fillQuads(points,minX,minY,maxX,maxY);
        double width=Math.min(requestedWidth,span*.25);
        for(int attempt=0;attempt<8;attempt++,width*=.5) {
            List<Point> inset=new ArrayList<>();
            for(int i=0;i<points.size();i++) {
                var a=normals.get(Math.floorMod(i-1,points.size()));var b=normals.get(i);double denominator=1+a.x()*b.x()+a.y()*b.y();
                if(denominator<1e-8)break;
                var p=points.get(i);inset.add(new Point(p.x()+(a.x()+b.x())*width/denominator,p.y()+(a.y()+b.y())*width/denominator));
            }
            if(inset.size()!=points.size() || area(inset)*sign<=0 || (!convex && !validInset(inset,points)))continue;
            List<Quad> result=new ArrayList<>();boolean valid=true;
            for(int i=0;i<points.size();i++){
                int next=(i+1)%points.size();var q=new Quad(points.get(i),points.get(next),inset.get(next),inset.get(i));
                if((cross(q.a(),q.b(),q.c())+cross(q.a(),q.c(),q.d()))*sign<=0){valid=false;break;}
                result.add(sign>0?q:new Quad(q.a(),q.d(),q.c(),q.b()));
            }
            if(valid)return result;
        }
        return OverlayGeometry.fillQuads(points,minX,minY,maxX,maxY);
    }
    private static boolean validInset(List<Point> inset,List<Point> outer) {
        for(int i=0;i<inset.size();i++) {
            var a=inset.get(i);var b=inset.get((i+1)%inset.size());
            if(!OverlayGeometry.contains(outer,a.x(),a.y()) || !OverlayGeometry.contains(outer,(a.x()+b.x())/2,(a.y()+b.y())/2))return false;
            for(int j=i+2;j<inset.size();j++) {
                if(i==0 && j==inset.size()-1)continue;
                var c=inset.get(j);var d=inset.get((j+1)%inset.size());
                if(cross(a,b,c)*cross(a,b,d)<0 && cross(c,d,a)*cross(c,d,b)<0)return false;
            }
        }
        return true;
    }
    private static double area(List<Point> points){double area=0;for(int i=1;i+1<points.size();i++)area+=cross(points.getFirst(),points.get(i),points.get(i+1));return area/2;}
    private static double cross(Point a,Point b,Point c){return (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());}
    private static double distance(Point a,Point b){return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    private RegionOutline(){}
}
