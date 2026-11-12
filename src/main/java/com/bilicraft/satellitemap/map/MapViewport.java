package com.bilicraft.satellitemap.map;

/** Retain fractional coordinates until the GUI pose transforms the complete tile. */
public record MapViewport(double x,double y,double size) {
    public static MapViewport tile(int col,int row,double size,int x,int y,double left,double top) {
        return new MapViewport(x+col*size-left,y+row*size-top,size);
    }
}
