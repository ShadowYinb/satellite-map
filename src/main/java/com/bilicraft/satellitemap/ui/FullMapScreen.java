package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.map.DynmapModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class FullMapScreen extends Screen {
    private final SatelliteMapClient mod;
    private double cx,cy=64,cz;
    private int zoom;
    private boolean follow = true, dragging;
    private String mapIdentity = "";
    public FullMapScreen(SatelliteMapClient mod) { super(Component.literal("卫星地图")); this.mod=mod; zoom=mod.config.zoom; }
    @Override protected void init() {
        int buttonY=height-27;
        addRenderableWidget(Button.builder(Component.literal("定位"),b -> follow=true).bounds(8,buttonY,48,20).build());
        addRenderableWidget(Button.builder(Component.literal("−"),b -> changeZoom(1)).bounds(60,buttonY,24,20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),b -> changeZoom(-1)).bounds(88,buttonY,24,20).build());
        addRenderableWidget(Button.builder(Component.literal("切换组"),b -> { mod.cycle(); follow=true; }).bounds(116,buttonY,48,20).build());
        addRenderableWidget(Button.builder(Component.literal("自动"),b -> {mod.automatic();follow=true;}).bounds(168,buttonY,40,20).build());
        addRenderableWidget(Button.builder(Component.literal("图层"),b->minecraft.setScreen(new OverlaySettingsScreen(this,mod,true)))
                .bounds(width>=380?214:width-60,width>=380?buttonY:5,48,20).build());
        addRenderableWidget(Button.builder(Component.literal("设置"),b -> minecraft.setScreen(new SettingsScreen(this,mod))).bounds(width-108,buttonY,48,20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"),b -> onClose()).bounds(width-56,buttonY,48,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        String identity=mod.title();
        if (!identity.equals(mapIdentity)) { mapIdentity=identity; follow=true; }
        if (follow && minecraft.player != null) {var p=minecraft.player.getPosition(MapRenderer.partialTick());cx=p.x;cy=p.y;cz=p.z;}
        MapRenderer.draw(g,mod,0,0,width,height,cx,cy,cz,zoom,true,mx,my);
        int size=mod.config.fullmapOverlays.textSize;float fontScale=size/9f;
        g.fill(5,5,Math.min(width-5,Math.max(180,(int)(font.width(mod.title())*fontScale)+16)),14+size*2,0x90101824);
        MapRenderer.textScaled(g,MapRenderer.clip(mod.title(),(int)((width-80)/fontScale)),10,9,0xFFF0F6FF,size);
        MapRenderer.textScaled(g,MapRenderer.clip(mod.status,(int)((width-16)/fontScale)),8,height-30-size-4,0xFFE5EDF6,size);
        if (mod.layer!=null) {
            double scale=mod.layer.scale(Math.clamp(zoom,-mod.layer.maxZoomIn(),mod.layer.displayMaxZoomOut()));
            DynmapModel.Point center=mod.layer.project(cx,cy,cz);
            DynmapModel.Point world=mod.layer.unproject(center.x()+(mx-width/2.0)/scale,
                    center.y()+(my-height/2.0)/scale,cy);
            String coord=String.format(java.util.Locale.ROOT,"X %.0f  Y %.0f  Z %.0f  ·  级别 %d",world.x(),cy,world.y(),zoom);
            MapRenderer.textScaled(g,MapRenderer.clip(coord,(int)((width-20)/fontScale)),10,12+size,0xFFCDDAEB,size);
        }
        super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled) {
        if(super.mouseClicked(event,doubled))return true;
        if (event.button()==0) {dragging=true;return true;}
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging=false;return super.mouseReleased(event); }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if (dragging && mod.layer!=null) {
            follow=false;
            int z=Math.clamp(zoom,-mod.layer.maxZoomIn(),mod.layer.displayMaxZoomOut());
            DynmapModel.Point p=mod.layer.project(cx,cy,cz);
            DynmapModel.Point point=mod.layer.unproject(p.x()-dx/mod.layer.scale(z),p.y()-dy/mod.layer.scale(z),cy);
            cx=point.x();cz=point.y();return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if (vertical!=0) changeZoom(vertical>0?-1:1);return true;
    }
    private void changeZoom(int amount) {
        zoom=Math.clamp(zoom+amount,mod.layer==null?-6:-mod.layer.maxZoomIn(),mod.layer==null?16:mod.layer.displayMaxZoomOut());
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (mod.openMap.matches(event)) {onClose();return true;}
        if (event.key()==GLFW.GLFW_KEY_SPACE) {follow=true;return true;}
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
}
