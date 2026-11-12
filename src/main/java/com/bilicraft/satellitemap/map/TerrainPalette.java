package com.bilicraft.satellitemap.map;

import java.util.*;

/** Per-material daylight calibration from matching base tiles; never recolors changed materials. */
public final class TerrainPalette {
    private static final int REQUIRED=32;
    private static final class Fit {
        int samples;final double[] source=new double[3],target=new double[3];
    }
    private final Map<Object,Fit> fits=new HashMap<>();
    public static int multiply(int texture,int tint) {
        if(tint==-1)return texture;
        return 0xFF000000|(((texture>>16)&255)*((tint>>16)&255)/255)<<16|
                (((texture>>8)&255)*((tint>>8)&255)/255)<<8|(texture&255)*(tint&255)/255;
    }
    public static boolean compatible(int source,int target) {
        if((target>>>24)!=255)return false;
        double s=0,t=0;for(int shift:new int[]{16,8,0}){s+=(source>>shift)&255;t+=(target>>shift)&255;}
        if(s<40 || t<40 || t/s<.45 || t/s>1.65)return false;
        for(int shift:new int[]{16,8,0})if(Math.abs(((source>>shift)&255)/s-((target>>shift)&255)/t)>.12)return false;
        return true;
    }
    public void observe(Object material,int source,int target) {
        if(!compatible(source,target))return;
        var fit=fits.computeIfAbsent(material,k->new Fit());if(fit.samples>=REQUIRED)return;
        for(int i=0;i<3;i++){int shift=16-i*8;fit.source[i]+=(source>>shift)&255;fit.target[i]+=(target>>shift)&255;}
        fit.samples++;
    }
    public int apply(Object material,int color) {
        var fit=fits.get(material);if(fit==null || fit.samples<REQUIRED)return color;
        int result=0xFF000000;
        for(int i=0;i<3;i++){
            double gain=Math.clamp(fit.target[i]/Math.max(1,fit.source[i]),.45,1.65);
            result|=Math.clamp((int)Math.round(((color>>(16-i*8))&255)*gain),0,255)<<(16-i*8);
        }
        return result;
    }
    public void clear(){fits.clear();}
}
