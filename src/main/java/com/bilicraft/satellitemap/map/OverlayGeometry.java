package com.bilicraft.satellitemap.map;

import java.util.*;

public final class OverlayGeometry {
    public record Point(double x,double y) {}
    public record Span(int x0,int x1,int y) {}
    public static double[] clipLine(Point a,Point b,double left,double top,double right,double bottom) {
        double dx=b.x-a.x,dy=b.y-a.y,t0=0,t1=1;
        double[] p={-dx,dx,-dy,dy},q={a.x-left,right-a.x,a.y-top,bottom-a.y};
        for(int i=0;i<4;i++) {
            if(p[i]==0){if(q[i]<0)return null;continue;}
            double t=q[i]/p[i];if(p[i]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);if(t0>t1)return null;
        }
        return new double[]{a.x+t0*dx,a.y+t0*dy,a.x+t1*dx,a.y+t1*dy};
    }
    public static List<Span> spans(List<Point> points,int left,int top,int right,int bottom) {
        if(points.size()<3)return List.of();
        int first=Math.max(top,(int)Math.floor(points.stream().mapToDouble(Point::y).min().orElse(top)));
        int last=Math.min(bottom,(int)Math.ceil(points.stream().mapToDouble(Point::y).max().orElse(bottom)));
        List<Span> spans=new ArrayList<>();
        for(int y=first;y<last;y++) {
            double scan=y+.5;List<Double> cuts=new ArrayList<>();
            for(int i=0,j=points.size()-1;i<points.size();j=i++) {
                Point a=points.get(j),b=points.get(i);
                if((a.y<=scan && b.y>scan)||(b.y<=scan && a.y>scan))cuts.add(a.x+(scan-a.y)*(b.x-a.x)/(b.y-a.y));
            }
            cuts.sort(Double::compareTo);
            for(int i=0;i+1<cuts.size();i+=2) {
                int x0=Math.max(left,(int)Math.ceil(cuts.get(i)-.5)),x1=Math.min(right,(int)Math.ceil(cuts.get(i+1)-.5));
                if(x1>x0)spans.add(new Span(x0,x1,y));
            }
        }
        return spans;
    }
    public static boolean contains(List<Point> polygon,double x,double y) {
        boolean inside=false;
        for(int i=0,j=polygon.size()-1;i<polygon.size();j=i++) {
            Point a=polygon.get(i),b=polygon.get(j);
            if((a.y>y)!=(b.y>y) && x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)inside=!inside;
        }
        return inside;
    }
    /** Outlines can be selected from either side, without selecting their entire interior. */
    public static boolean hit(List<Point> points,boolean closed,boolean filled,double x,double y,double tolerance) {
        if(closed && filled && contains(points,x,y))return true;
        int segments=closed?points.size():points.size()-1;
        for(int i=0;i<segments;i++) {
            Point a=points.get(i),b=points.get((i+1)%points.size());
            double dx=b.x-a.x,dy=b.y-a.y,length=dx*dx+dy*dy;
            double t=length==0?0:Math.clamp(((x-a.x)*dx+(y-a.y)*dy)/length,0,1);
            if(Math.hypot(x-a.x-t*dx,y-a.y-t*dy)<=tolerance)return true;
        }
        return false;
    }
    public static Point visibleEdge(List<Point> points,boolean closed,double left,double top,double right,double bottom) {
        Point anchor=null;double longest=0;
        for(int i=0;i<(closed?points.size():points.size()-1);i++) {
            double[] edge=clipLine(points.get(i),points.get((i+1)%points.size()),left,top,right,bottom);
            if(edge==null)continue;
            double length=Math.hypot(edge[2]-edge[0],edge[3]-edge[1]);
            if(length>longest){longest=length;anchor=new Point((edge[0]+edge[2])/2,(edge[1]+edge[3])/2);}
        }
        return anchor;
    }
    private OverlayGeometry() {}
}
