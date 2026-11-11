package com.bilicraft.satellitemap.map;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** Public Dynmap marker sets, normalized to immutable world-space geometry. */
public record OverlayModel(List<MarkerSet> sets) {
    public static final OverlayModel EMPTY=new OverlayModel(List.of());
    public record Point(double x,double y,double z) {}
    public record Style(int stroke,int fill,int weight) {}
    public record Marker(String id,String label,Point point,String icon,int size,int minZoom,int maxZoom,String detail) {}
    public record Shape(String id,String label,List<Point> points,boolean closed,Style style,int minZoom,int maxZoom,String detail) {}
    public record MarkerSet(String id,String label,boolean hidden,boolean labels,int priority,int minZoom,int maxZoom,List<Marker> markers,List<Shape> shapes) {
        public int count() {return markers.size()+shapes.size();}
    }
    public static String plain(String value) {
        if(value==null)return "";
        String result=value.replaceAll("(?is)<script\\b[^>]*>.*?</script>","").replaceAll("(?is)<style\\b[^>]*>.*?</style>","")
                .replaceAll("<[^>]*>","").replaceAll("(?i)§[0-9a-fk-or]","")
                .replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'").replace("&nbsp;"," ").replace("&amp;","&")
                .replaceAll("[\\p{Cntrl}]"," ").trim();
        return result.substring(0,Math.min(160,result.length()));
    }
    public static boolean zoomVisible(int actual,int min,int max) {return (min<0 || actual>=min) && (max<0 || actual<=max);}
    public static URI iconUri(URI root,String icon) {return root.resolve("_markers_/"+DynmapModel.encode(icon)+".png");}
    public static OverlayModel parse(JsonObject json) {
        if(!json.has("sets") || !json.get("sets").isJsonObject())throw new IllegalArgumentException("缺少 Dynmap marker sets");
        List<MarkerSet> result=new ArrayList<>();int total=0;
        for(var entry:json.getAsJsonObject("sets").entrySet()) {
            if(!entry.getValue().isJsonObject() || result.size()>=256)continue;
            JsonObject set=entry.getValue().getAsJsonObject();List<Marker> markers=new ArrayList<>();List<Shape> shapes=new ArrayList<>();
            for(var m:object(set,"markers").entrySet()) {
                if(total++>=20000)break;
                try {
                    JsonObject v=m.getValue().getAsJsonObject();Point point=point(number(v,"x",0),number(v,"y",64),number(v,"z",0));
                    String dim=string(v,"dim","16x16");int size=dim.startsWith("32")?16:dim.startsWith("8")?8:12;
                    String raw=string(v,"label",m.getKey());
                    markers.add(new Marker(m.getKey(),MarkerText.name(raw),point,string(v,"icon","default"),size,integer(v,"minzoom",-1),integer(v,"maxzoom",-1),MarkerText.detail(raw,string(v,"desc",""))));
                } catch(RuntimeException ignored){}
            }
            for(String type:List.of("areas","lines","circles"))for(var entryShape:object(set,type).entrySet()) {
                if(total++>=20000)break;
                try {
                    JsonObject v=entryShape.getValue().getAsJsonObject();List<Point> points=new ArrayList<>();boolean closed=!type.equals("lines");
                    double height=number(v,type.equals("areas")?"ytop":"y",64);
                    if(type.equals("circles")) {
                        double x=number(v,"x",0),z=number(v,"z",0),xr=number(v,"xr",0),zr=number(v,"zr",0);
                        if(xr<=0 || zr<=0)continue;
                        for(int i=0;i<48;i++) {double a=i*Math.PI*2/48;points.add(point(x+Math.cos(a)*xr,height,z+Math.sin(a)*zr));}
                    } else {
                        JsonArray xs=v.getAsJsonArray("x"),zs=v.getAsJsonArray("z");
                        if(xs==null || zs==null || xs.size()!=zs.size() || xs.size()<2 || xs.size()>2048)continue;
                        if(closed && xs.size()==2) {
                            double x0=xs.get(0).getAsDouble(),x1=xs.get(1).getAsDouble(),z0=zs.get(0).getAsDouble(),z1=zs.get(1).getAsDouble();
                            points.addAll(List.of(point(x0,height,z0),point(x1,height,z0),point(x1,height,z1),point(x0,height,z1)));
                        } else {
                            JsonArray ys=v.has("y") && v.get("y").isJsonArray()?v.getAsJsonArray("y"):null;
                            for(int i=0;i<xs.size();i++)points.add(point(xs.get(i).getAsDouble(),ys!=null && i<ys.size()?ys.get(i).getAsDouble():height,zs.get(i).getAsDouble()));
                        }
                    }
                    int stroke=color(string(v,"color","#FF6600"),number(v,"opacity",.8));
                    int fill=closed?color(string(v,"fillcolor","#FF6600"),number(v,"fillopacity",.2)):0;
                    var style=new Style(stroke,fill,Math.clamp(integer(v,"weight",2),1,6));
                    String raw=string(v,"label",entryShape.getKey());
                    shapes.add(new Shape(entryShape.getKey(),MarkerText.name(raw),List.copyOf(points),closed,style,integer(v,"minzoom",-1),integer(v,"maxzoom",-1),MarkerText.detail(raw,string(v,"desc",""))));
                } catch(RuntimeException ignored){}
            }
            result.add(new MarkerSet(entry.getKey(),plain(string(set,"label",entry.getKey())),bool(set,"hide",false),bool(set,"showlabels",true),
                    integer(set,"layerprio",0),integer(set,"minzoom",-1),integer(set,"maxzoom",-1),List.copyOf(markers),List.copyOf(shapes)));
        }
        result.sort(Comparator.comparingInt(MarkerSet::priority));return new OverlayModel(List.copyOf(result));
    }
    public static int color(String hex,double opacity) {
        try {return ((int)Math.round(Math.clamp(opacity,0,1)*255)<<24)|(Integer.parseUnsignedInt(hex.replace("#",""),16)&0xFFFFFF);}
        catch(RuntimeException e) {return 0xCCFF6600;}
    }
    private static Point point(double x,double y,double z) {
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x)>6e7 || Math.abs(z)>6e7)throw new IllegalArgumentException("Invalid marker coordinates");
        return new Point(x,y,z);
    }
    private static JsonObject object(JsonObject o,String key) {return o.has(key) && o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
    private static String string(JsonObject o,String key,String fallback) {return DynmapModel.string(o,key,fallback);}
    private static int integer(JsonObject o,String key,int fallback) {return o.has(key)?o.get(key).getAsInt():fallback;}
    private static double number(JsonObject o,String key,double fallback) {return o.has(key) && !o.get(key).isJsonArray()?o.get(key).getAsDouble():fallback;}
    private static boolean bool(JsonObject o,String key,boolean fallback) {return o.has(key)?o.get(key).getAsBoolean():fallback;}
}
