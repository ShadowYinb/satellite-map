package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.map.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Persistent mosaics keep fine coverage available independently of detail texture eviction. */
public final class OverviewCache implements AutoCloseable {
    private static final AtomicInteger IDS=new AtomicInteger();
    private final OverviewStore store;
    private final Map<OverviewStore.Key,Identifier> textures=new LinkedHashMap<>(128,.75f,true);
    private final Map<OverviewStore.Key,Long> requested=new HashMap<>();
    private final Set<OverviewStore.Key> visible=new HashSet<>();
    public OverviewCache(Path directory){store=new OverviewStore(directory);}
    public void accept(URI uri,byte[] png){store.accept(uri,png);}
    public void configure(URI root,int max){store.configure(root,max);}
    public void tick(){
        var manager=Minecraft.getInstance().getTextureManager();
        for(var entry:store.drain(8).entrySet())try{
            var image=NativeImage.read(entry.getValue());var id=Identifier.fromNamespaceAndPath("satellite_map","overview_"+IDS.getAndIncrement());
            manager.register(id,new DynamicTexture(()->"Satellite persistent overview",image));var old=textures.put(entry.getKey(),id);if(old!=null)manager.release(old);
        }catch(Exception ignored){}
        var iterator=textures.entrySet().iterator();while(textures.size()>384 && iterator.hasNext()){
            var entry=iterator.next();if(visible.contains(entry.getKey()) && textures.size()<=768)continue;
            manager.release(entry.getValue());iterator.remove();
        }
    }
    public void beginFrame(){visible.clear();}
    public void drawLevel(GuiGraphics g,URI root,int tileSize,int level,int zoom,int x,int y,int width,int height,double left,double top,double scale){
        if(level<TilePyramid.overviewDetailLevel(zoom,18))return;
        long now=System.currentTimeMillis();
            double size=Math.scalb((double)tileSize,level)*scale;
            int minCol=(int)Math.floor(left/size),maxCol=(int)Math.ceil((left+width)/size)-1,minRow=(int)Math.floor(top/size),maxRow=(int)Math.ceil((top+height)/size)-1;
            if((long)(maxCol-minCol+1)*(maxRow-minRow+1)>384)return;
            for(int row=minRow;row<=maxRow;row++)for(int col=minCol;col<=maxCol;col++){
                var key=new OverviewStore.Key(root,level,col,row);var texture=textures.get(key);
                if(texture==null && now-requested.getOrDefault(key,0L)>2000 && store.request(key))requested.put(key,now);
                if(texture!=null){visible.add(key);int dx=x+(int)Math.round(col*size-left),dy=y+(int)Math.round(row*size-top),right=x+(int)Math.round((col+1.0)*size-left),bottom=y+(int)Math.round((row+1.0)*size-top);
                    g.blit(texture,dx,dy,right,bottom,0,1,0,1);}
            }
        if(requested.size()>8192)requested.clear();
    }
    @Override public void close(){store.close();var manager=Minecraft.getInstance().getTextureManager();textures.values().forEach(manager::release);textures.clear();}
}
