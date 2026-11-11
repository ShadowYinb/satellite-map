package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.config.MapConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MinimapPositionScreen extends Screen {
    private final Screen parent;
    private final SatelliteMapClient mod;
    private final MapConfig draft;
    private final Runnable accept;
    private boolean dragging;
    private double grabX,grabY;
    public MinimapPositionScreen(Screen parent,SatelliteMapClient mod,MapConfig draft,Runnable accept) {
        super(Component.literal("拖动小地图调整位置"));this.parent=parent;this.mod=mod;this.draft=draft;this.accept=accept;
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("重置到右上"),b->{draft.corner=0;draft.offsetX=0;draft.offsetY=0;}).bounds(width/2-105,height-27,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"),b->onClose()).bounds(width/2+5,height-27,100,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x9014202B);
        var b=MinimapLayout.bounds(draft,width,height);
        g.renderOutline(b.x()-2,b.y()-2,b.width()+4,b.height()+4,0xDDD6E3EC);
        var player=minecraft.player;
        MapRenderer.draw(g,mod,b.x(),b.y(),b.width(),b.height(),player==null?0:player.getX(),player==null?64:player.getY(),player==null?0:player.getZ(),draft.zoom);
        g.fill(b.x()-2,b.y()-2,b.x()+b.width()+2,b.y(),MapRenderer.ACCENT);
        MapRenderer.text(g,MapRenderer.clip("按住左键拖动小地图",b.width()-8),b.x()+4,b.y()+b.height()+4,MapRenderer.ACCENT);
        MapRenderer.text(g,b.width()+" × "+b.height()+"  X "+b.x()+" Y "+b.y(),b.x()+4,b.y()+b.height()+16,MapRenderer.TEXT);
        super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled) {
        if(event.y()>=height-30)return super.mouseClicked(event,doubled);
        var b=MinimapLayout.bounds(draft,width,height);
        if(event.button()==0 && event.x()>=b.x()-2 && event.x()<b.x()+b.width()+2 && event.y()>=b.y()-2 && event.y()<b.y()+b.height()+30) {
            dragging=true;grabX=event.x()-b.x();grabY=event.y()-b.y();return true;
        }
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(dragging) {MinimapLayout.place(draft,width,height,(int)Math.round(event.x()-grabX),(int)Math.round(event.y()-grabY));return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {dragging=false;return super.mouseReleased(event);}
    @Override public void onClose() {accept.run();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
