package com.bilicraft.satellitemap.map;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Tile bounds use the same unscaled projection at every zoom level. */
public final class TilePyramid {
    private static final Pattern PATH=Pattern.compile("(.*/)(-?\\d+)_(-?\\d+)/(?:(z+)_)?(-?\\d+)_(-?\\d+)\\.(?:png|jpg|jpeg)");
    public record Tile(URI uri,URI root,int level,int col,int row) {
        public double size(int tileSize) {return Math.scalb((double)tileSize,level);}
        public boolean intersects(int tileSize,double left,double top,double right,double bottom) {
            double size=size(tileSize);return col*size<right && (col+1.0)*size>left && row*size<bottom && (row+1.0)*size>top;
        }
    }
    public static Tile parse(URI uri) {
        Matcher m=PATH.matcher(uri.toASCIIString());if(!m.matches())return null;
        try {
            int level=m.group(4)==null?0:m.group(4).length();if(level>12)return null;
            int tx=Integer.parseInt(m.group(5)),ty=Integer.parseInt(m.group(6)),step=1<<level;
            if(Math.floorMod(tx,step)!=0 || Math.floorMod(ty,step)!=0)return null;
            if((tx>>5)!=Integer.parseInt(m.group(2)) || (ty>>5)!=Integer.parseInt(m.group(3)))return null;
            return new Tile(uri,URI.create(m.group(1)),level,tx/step,-ty/step);
        } catch(RuntimeException e) {return null;}
    }
    public static URI root(DynmapModel model,String world,DynmapModel.Layer layer) {
        return model.tiles().resolve(DynmapModel.encode(world)+"/"+DynmapModel.encode(layer.prefix())+"/");
    }
    public static List<Tile> visible(DynmapModel model,String world,DynmapModel.Layer layer,int level,
                                     double left,double top,double right,double bottom) {
        double size=Math.scalb((double)layer.tileSize(),level);
        int minCol=(int)Math.floor(left/size),maxCol=(int)Math.ceil(right/size)-1;
        int minRow=(int)Math.floor(top/size),maxRow=(int)Math.ceil(bottom/size)-1;
        List<Tile> result=new ArrayList<>();
        if((long)(maxCol-minCol+1)*(maxRow-minRow+1)>4096) {
            int cx=(int)Math.floor((left+right)/2/size),cy=(int)Math.floor((top+bottom)/2/size);
            minCol=Math.max(minCol,cx-16);maxCol=Math.min(maxCol,cx+16);minRow=Math.max(minRow,cy-16);maxRow=Math.min(maxRow,cy+16);
        }
        URI root=root(model,world,layer);
        for(int row=minRow;row<=maxRow;row++)for(int col=minCol;col<=maxCol;col++)
            result.add(new Tile(model.tiles().resolve(layer.tilePath(world,level,col,row)),root,level,col,row));
        double cx=(left+right)/2,cy=(top+bottom)/2;
        result.sort(Comparator.comparingDouble(t->Math.hypot((t.col+.5)*size-cx,(t.row+.5)*size-cy)));
        return result;
    }
    public static List<Tile> requests(DynmapModel model,String world,DynmapModel.Layer layer,int zoom,double left,double top,double right,double bottom) {
        return requests(model,world,layer,zoom,left,top,right,bottom,96);
    }
    public static List<Tile> requests(DynmapModel model,String world,DynmapModel.Layer layer,int zoom,double left,double top,double right,double bottom,int budget) {
        var result=new LinkedHashMap<URI,Tile>();
        int detail=detailLevel(layer,left,top,right,bottom,budget);
        // Big blocks cover the focus first. Every level is ordered by distance to its center.
        for(int level=layer.maxZoomOut();level>=detail;level--)
            for(var tile:visible(model,world,layer,level,left,top,right,bottom).stream().limit(budget).toList())result.putIfAbsent(tile.uri(),tile);
        if(detail>0)for(var tile:nearbyBase(model,world,layer,left,top,right,bottom))result.putIfAbsent(tile.uri(),tile);
        double cx=(left+right)/2,cy=(top+bottom)/2;
        return result.values().stream().sorted(Comparator.comparingInt(Tile::level).reversed()
                .thenComparingDouble(t->Math.hypot((t.col()+.5)*t.size(layer.tileSize())-cx,(t.row()+.5)*t.size(layer.tileSize())-cy))).toList();
    }
    public static int detailLevel(DynmapModel.Layer layer,double left,double top,double right,double bottom) {
        return detailLevel(layer,left,top,right,bottom,96);
    }
    public static int detailLevel(DynmapModel.Layer layer,double left,double top,double right,double bottom,int budget) {
        int level=0;
        // Keep automatic detail downloads bounded, even when viewing millions of base tiles.
        while(level<layer.maxZoomOut()) {
            double size=Math.scalb((double)layer.tileSize(),level);
            double count=(Math.ceil(right/size)-Math.floor(left/size))*(Math.ceil(bottom/size)-Math.floor(top/size));
            if(count<=budget)break;level++;
        }
        return level;
    }
    public static int overviewDetailLevel(int zoom,int maxLevel){return Math.clamp(zoom-1,1,maxLevel);}
    public static List<Tile> nearbyBase(DynmapModel model,String world,DynmapModel.Layer layer,double left,double top,double right,double bottom) {
        double size=layer.tileSize(),cx=Math.floor((left+right)/2/size),cy=Math.floor((top+bottom)/2/size);
        return visible(model,world,layer,0,Math.max(left,(cx-3)*size),Math.max(top,(cy-3)*size),
                Math.min(right,(cx+4)*size),Math.min(bottom,(cy+4)*size)).stream().limit(24).toList();
    }
    private TilePyramid() {}
}
