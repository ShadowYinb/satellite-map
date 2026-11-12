package com.bilicraft.satellitemap.map;

import java.util.*;

/** Water needs a separate model: depth, river biome and renderer water tint can change its hue. */
public final class WaterPalette {
    public record Key(int biome,int depth){}
    private static final class Fit {int count;final double[] source=new double[3],target=new double[3];}
    private final Map<Key,Fit> fits=new HashMap<>();
    public static Key key(int biome,int depth){return new Key(biome,depth<=1?1:depth<=4?4:depth<=8?8:depth<=16?16:32);}
    public static boolean isWaterColor(int color) {
        int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        return (color>>>24)==255 && r+g+b>=30 && (b>r*1.12 || (g>r*1.25 && b>r*.85));
    }
    public static int base(int water,int bottom,int depth) {
        double floor=Math.pow(.68,Math.max(1,depth));int result=0xFF000000;
        for(int shift:new int[]{16,8,0})result|=Math.clamp((int)Math.round(((water>>shift)&255)*(1-floor)+((bottom>>shift)&255)*floor),0,255)<<shift;
        return result;
    }
    public void observe(Key key,int source,int target){
        if(!isWaterColor(target))return;
        var fit=fits.computeIfAbsent(key,k->new Fit());if(fit.count>=32)return;
        for(int i=0;i<3;i++){fit.source[i]+=(source>>(16-i*8))&255;fit.target[i]+=(target>>(16-i*8))&255;}fit.count++;
    }
    public int apply(Key key,int color){
        var fit=fits.get(key);if(fit==null || fit.count<32)return color;
        int result=0xFF000000;
        for(int i=0;i<3;i++)result|=Math.clamp((int)Math.round(fit.target[i]/32+(((color>>(16-i*8))&255)-fit.source[i]/32)*.5),0,255)<<(16-i*8);
        return result;
    }
    public void clear(){fits.clear();}
}
