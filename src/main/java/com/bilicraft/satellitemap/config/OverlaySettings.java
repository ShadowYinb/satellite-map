package com.bilicraft.satellitemap.config;

import com.bilicraft.satellitemap.map.OverlayModel;
import java.net.URI;
import java.util.*;

public final class OverlaySettings {
    public boolean self=true,players=true,playerNames=true,labels=true;
    public boolean tabDetails=true,mobs=true,hostile=true,neutral=true,friendly=true;
    public int playerSize=6,markerSize=6,mobSize=6,selfSize=6,textSize=6,radarRadius=96,radarHeight=32;
    public Map<String,Boolean> layers=new HashMap<>();
    public static OverlaySettings minimapDefaults() {return new OverlaySettings();}
    public boolean enabled(URI site,OverlayModel.MarkerSet set) {return layers.getOrDefault(site+"|"+set.id(),!set.hidden());}
    public void set(URI site,String id,boolean enabled) {layers.put(site+"|"+id,enabled);}
    public void normalize() {
        if(layers==null)layers=new HashMap<>();layers.entrySet().removeIf(e->e.getKey()==null || e.getValue()==null);
        playerSize=Math.clamp(playerSize,4,32);markerSize=Math.clamp(markerSize,4,32);mobSize=Math.clamp(mobSize,4,24);selfSize=Math.clamp(selfSize,4,24);
        textSize=Math.clamp(textSize,4,18);
        radarRadius=Math.clamp(radarRadius,16,256);radarHeight=Math.clamp(radarHeight,8,128);
    }
    public void migrateSizes() {
        if(playerSize==10)playerSize=6;if(markerSize==12)markerSize=6;if(mobSize==9)mobSize=6;if(selfSize==8)selfSize=6;
    }
}
