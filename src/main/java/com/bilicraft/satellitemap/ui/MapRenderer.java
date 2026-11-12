package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.map.DynmapModel;
import com.bilicraft.satellitemap.map.TilePyramid;
import com.bilicraft.satellitemap.map.MapViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class MapRenderer {
    public static final int PANEL = 0xF014202B, TEXT = 0xFFE3EEF5, ACCENT = 0xFF64E3C3;
    public static void hud(GuiGraphics g,SatelliteMapClient mod) {
        Minecraft mc = Minecraft.getInstance();
        if (!mod.config.minimapEnabled || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        var bounds=MinimapLayout.bounds(mod.config,g.guiWidth(),g.guiHeight());
        int x=bounds.x(),y=bounds.y(),width=bounds.width(),height=bounds.height();
        if(width<64 || height<64)return;
        // A narrow floating frame; the coordinate readout has no opaque backing panel.
        g.renderOutline(x-3,y-3,width+6,height+6,0x70000000);
        g.renderOutline(x-2,y-2,width+4,height+4,0xD0D6E3EC);
        g.renderOutline(x-1,y-1,width+2,height+2,0xA0151C28);
        g.fill(x-2,y-2,x+12,y,0xFFF1F6FA);g.fill(x+width-12,y-2,x+width+2,y,0xFFF1F6FA);
        var position=mc.player.getPosition(partialTick());
        draw(g,mod,x,y,width,height,position.x,position.y,position.z,mod.config.zoom);
        int fontSize=mod.config.minimapOverlays.textSize;
        textScaled(g,String.format(Locale.ROOT,"X %.0f  Y %.0f  Z %.0f",mc.player.getX(),mc.player.getY(),mc.player.getZ()),x,y+height+5,0xFFF0F4FA,fontSize);
        if(mc.options.keyPlayerList.isDown())textScaled(g,clip(mod.title(),(int)(width*9.0/fontSize)),x,y+height+fontSize+9,0xFFB8CDE0,fontSize);
        if (mod.layer == null) {
            g.fill(x,y+height/2-12,x+width,y+height/2+13,0xD014202B);
            text(g,clip(mod.status,width-8),x+4,y+height/2-7,TEXT);
            text(g,"设置: "+mod.openSettings.getTranslatedKeyMessage().getString(),x+4,y+height/2+4,ACCENT);
        }
    }

    public static void draw(GuiGraphics g,SatelliteMapClient mod,int x,int y,int width,int height,
                            double centerX,double centerY,double centerZ,int zoom) {
        draw(g,mod,x,y,width,height,centerX,centerY,centerZ,zoom,false,Integer.MIN_VALUE,Integer.MIN_VALUE);
    }
    public static void draw(GuiGraphics g,SatelliteMapClient mod,int x,int y,int width,int height,
                            double centerX,double centerY,double centerZ,int zoom,boolean full,int mouseX,int mouseY) {
        g.fill(x,y,x+width,y+height,0xFF0C141C);
        g.enableScissor(x,y,x+width,y+height);
        try {
            DynmapModel.Layer layer = mod.layer;
            if (layer == null || mod.model == null || mod.selection == null) return;
            int actualZoom = Math.clamp(zoom,-layer.maxZoomIn(),layer.displayMaxZoomOut());
            double scale = layer.scale(actualZoom);
            DynmapModel.Point center = layer.project(centerX,centerY,centerZ);
            double left = center.x()*scale-width/2.0, top = center.y()*scale-height/2.0;
            double nativeLeft=left/scale,nativeTop=top/scale,nativeRight=(left+width)/scale,nativeBottom=(top+height)/scale;
            var root=TilePyramid.root(mod.model,mod.selection.world(),layer);
            int overviewMax=Math.min(18,layer.displayMaxZoomOut()+2);
            if(mod.tiles.overview!=null){mod.tiles.overview.configure(root,overviewMax);mod.tiles.overview.beginFrame();}
            double cell=Math.max(32,layer.tileSize()*Math.scalb(1.0,Math.min(layer.maxZoomOut(),Math.max(0,actualZoom)))/8);
            mod.tiles.focusView(root+"|"+actualZoom+"|"+(long)Math.floor(center.x()/cell)+"|"+(long)Math.floor(center.y()/cell));
            // Disk-indexed fine tiles remain usable at every screen zoom.
            for(var tile:mod.tiles.known(root,layer.tileSize(),nativeLeft,nativeTop,nativeRight,nativeBottom))mod.tiles.local(tile.uri());
            int priority=0;
            for(var tile:TilePyramid.requests(mod.model,mod.selection.world(),layer,actualZoom,nativeLeft,nativeTop,nativeRight,nativeBottom,full?384:128))mod.tiles.get(tile.uri(),mod.config.refreshSeconds,priority++);
            int loaded = 0;
            var residents=mod.tiles.resident(root,layer.tileSize(),nativeLeft,nativeTop,nativeRight,nativeBottom);
            // Interleave by resolution: coarse originals must not cover finer persistent mosaics.
            for(int level=overviewMax;level>=0;level--) {
              for(var resident:residents) {
                if(resident.tile().level()!=level)continue;
                var tile=resident.tile();double size=tile.size(layer.tileSize())*scale;
                blitTile(g,resident.texture(),tile.col(),tile.row(),size,x,y,left,top);loaded++;
                mod.tiles.get(tile.uri(),mod.config.refreshSeconds,10000);
              }
              if(mod.tiles.overview!=null)mod.tiles.overview.drawLevel(g,root,layer.tileSize(),level,actualZoom,x,y,width,height,left,top,scale);
            }
            if (loaded == 0) {
                String message = mod.tiles.lastError.isEmpty()?"正在加载卫星瓦片…":"瓦片暂不可用（未渲染或网络错误）";
                text(g,clip(message,width-16),x+8,y+height-16,TEXT);
            }
            if(!full)mod.localTerrain.draw(g,layer,x,y,width,height,left,top,scale,centerY);
            var options=full?mod.config.fullmapOverlays:mod.config.minimapOverlays;
            OverlayRenderer.draw(g,mod,options,x,y,width,height,left,top,scale,actualZoom,mouseX,mouseY,full);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && options.self) {
                var position=mc.player.getPosition(partialTick());
                DynmapModel.Point player = layer.project(position.x,position.y,position.z);
                int px=full?x+(int)Math.round(player.x()*scale-left):x+width/2;
                int py=full?y+(int)Math.round(player.y()*scale-top):y+height/2;
                int half=options.selfSize/2;
                iconOutline(g,px,py,options.selfSize,0xB0D7E5EF);
                Portraits.player(g,mod,mc.player.getGameProfile().name(),px,py,options.selfSize);
                double yaw=Math.toRadians(mc.player.getYRot(partialTick()));
                DynmapModel.Point facing=layer.project(position.x-Math.sin(yaw)*8,position.y,position.z+Math.cos(yaw)*8);
                double fx=facing.x()-player.x(), fy=facing.y()-player.y(), len=Math.hypot(fx,fy);
                if (len>0) {
                    g.pose().pushMatrix();g.pose().scale(.5f,.5f);
                    for(int i=(half+1)*2;i<=(half+5)*2;i++) {
                        int ax=px*2+(int)Math.round(fx/len*i),ay=py*2+(int)Math.round(fy/len*i);
                        g.fill(ax,ay,ax+1,ay+1,0xEFFFFFFF);
                    }
                    g.pose().popMatrix();
                }
            }
        } finally { g.disableScissor(); }
    }
    public static String clip(String s,int width) { return Minecraft.getInstance().font.plainSubstrByWidth(s,Math.max(width,0)); }
    public static void text(GuiGraphics g,String s,int x,int y,int color) { g.drawString(Minecraft.getInstance().font,s,x,y,color); }
    public static void textScaled(GuiGraphics g,String s,int x,int y,int color,int size) {
        float scale=size/9f;g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);
        g.drawString(Minecraft.getInstance().font,s,0,0,color);g.pose().popMatrix();
    }
    public static void iconOutline(GuiGraphics g,int x,int y,int size,int color) {
        g.pose().pushMatrix();g.pose().scale(.5f,.5f);int left=(x-size/2)*2,top=(y-size/2)*2;
        g.renderOutline(left-1,top-1,size*2+2,size*2+2,color);g.pose().popMatrix();
    }
    public static float partialTick(){return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);}
    public static void blitTile(GuiGraphics g,Identifier texture,int col,int row,double size,int x,int y,double left,double top) {
        var rect=MapViewport.tile(col,row,size,x,y,left,top);
        g.pose().pushMatrix();g.pose().translate((float)rect.x(),(float)rect.y());g.pose().scale((float)(size/128),(float)(size/128));
        g.blit(texture,0,0,128,128,0,1,0,1);g.pose().popMatrix();
    }
    private MapRenderer() {}
}
