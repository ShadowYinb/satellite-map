package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.config.MapConfig;

/** Positions in Minecraft's GUI pixels, with automatic clamping after resizing. */
public final class MinimapLayout {
    public record Bounds(int x,int y,int width,int height) {}
    public static Bounds bounds(MapConfig c,int screenWidth,int screenHeight) {
        int width=Math.min(c.width(),screenWidth-24),height=Math.min(c.height(),screenHeight-70);
        int x=c.corner%2==0?screenWidth-width-10-c.offsetX:10+c.offsetX;
        int y=c.corner<2?10+c.offsetY:screenHeight-height-44-c.offsetY;
        x=Math.clamp(x,4,Math.max(4,screenWidth-width-4));
        y=Math.clamp(y,4,Math.max(4,screenHeight-height-34));
        return new Bounds(x,y,width,height);
    }
    public static void place(MapConfig c,int screenWidth,int screenHeight,int x,int y) {
        Bounds b=bounds(c,screenWidth,screenHeight);
        x=Math.clamp(x,4,Math.max(4,screenWidth-b.width-4));
        y=Math.clamp(y,4,Math.max(4,screenHeight-b.height-34));
        c.offsetX=c.corner%2==0?screenWidth-b.width-10-x:x-10;
        c.offsetY=c.corner<2?y-10:screenHeight-b.height-44-y;
    }
    private MinimapLayout() {}
}
