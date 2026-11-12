package com.bilicraft.satellitemap.map;

public final class RadarAppearance {
    public static double opacity(double heightDifference,int zoom,int heightScale) {
        double distance=Math.max(1,heightScale)*Math.pow(2,(Math.clamp(zoom,-6,16)-2)/2.0);
        double ratio=Math.abs(heightDifference)/distance;
        return Math.max(.06,1/(1+ratio*ratio));
    }
    public static int fade(int color,double opacity){return (color&0xFFFFFF)|((int)Math.round((color>>>24)*Math.clamp(opacity,0,1)))<<24;}
    private RadarAppearance(){}
}
