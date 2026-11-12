package com.bilicraft.satellitemap.map;

import java.util.*;

public final class OverlayGeometry {
    public record Point(double x,double y) {}
    public record Span(int x0,int x1,int y) {}
    public record Quad(Point a,Point b,Point c,Point d) {
        public List<Point> vertices(){return List.of(a,b,c,d);}
    }
    private record Edge(Point a,Point b) {
        double xAt(double y){return a.x+(y-a.y)*(b.x-a.x)/(b.y-a.y);}
    }
    /** Exact trapezoids between vertex heights, with no raster-row or integer coordinate snapping. */
    public static List<Quad> fillQuads(List<Point> polygon,double left,double top,double right,double bottom) {
        var points=clipPolygon(polygon,left,top,right,bottom);if(points.size()<3)return List.of();
        var heights=new ArrayList<>(new TreeSet<>(points.stream().map(Point::y).toList()));
        List<Quad> result=new ArrayList<>();
        for(int band=1;band<heights.size();band++) {
            double y0=heights.get(band-1),y1=heights.get(band);if(y1-y0<1e-8)continue;
            double middle=(y0+y1)/2;List<Edge> edges=new ArrayList<>();
            for(int i=0;i<points.size();i++) {
                var a=points.get(i);var b=points.get((i+1)%points.size());
                if((a.y<=middle && b.y>middle)||(b.y<=middle && a.y>middle))edges.add(new Edge(a,b));
            }
            edges.sort(Comparator.comparingDouble(e->e.xAt(middle)));
            for(int i=0;i+1<edges.size();i+=2) {
                var a=edges.get(i);var b=edges.get(i+1);if(b.xAt(middle)-a.xAt(middle)<1e-8)continue;
                result.add(new Quad(new Point(a.xAt(y0),y0),new Point(b.xAt(y0),y0),new Point(b.xAt(y1),y1),new Point(a.xAt(y1),y1)));
            }
        }
        return result;
    }
    public static List<Point> clipPolygon(List<Point> polygon,double left,double top,double right,double bottom) {
        List<Point> points=polygon;
        for(int side=0;side<4 && !points.isEmpty();side++) {
            List<Point> output=new ArrayList<>();double boundary=switch(side){case 0->left;case 1->right;case 2->top;default->bottom;};
            boolean horizontal=side<2,greater=(side&1)==0;Point previous=points.getLast();
            double before=horizontal?previous.x:previous.y;boolean previousInside=greater?before>=boundary:before<=boundary;
            for(Point point:points) {
                double value=horizontal?point.x:point.y;boolean inside=greater?value>=boundary:value<=boundary;
                if(inside!=previousInside) {
                    double t=(boundary-before)/(value-before);
                    output.add(horizontal?new Point(boundary,previous.y+(point.y-previous.y)*t):new Point(previous.x+(point.x-previous.x)*t,boundary));
                }
                if(inside)output.add(point);previous=point;before=value;previousInside=inside;
            }
            points=output;
        }
        return points;
    }
    public static Quad stroke(Point a,Point b,double weight) {
        double dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);if(length<1e-8)return null;
        double nx=dy/length*weight/2,ny=-dx/length*weight/2;
        return new Quad(new Point(a.x+nx,a.y+ny),new Point(b.x+nx,b.y+ny),new Point(b.x-nx,b.y-ny),new Point(a.x-nx,a.y-ny));
    }
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
        // Preserve edge order. Choosing the longest clipped edge can switch labels back and forth
        // between equally long sides while the camera moves by a fraction of a pixel.
        for(int i=0;i<(closed?points.size():points.size()-1);i++) {
            double[] edge=clipLine(points.get(i),points.get((i+1)%points.size()),left,top,right,bottom);
            if(edge==null)continue;
            double length=Math.hypot(edge[2]-edge[0],edge[3]-edge[1]);
            if(length>1e-6)return new Point((edge[0]+edge[2])/2,(edge[1]+edge[3])/2);
        }
        return null;
    }
    private OverlayGeometry() {}
}
