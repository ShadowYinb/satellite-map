package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.config.OverlaySettings;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.DynmapService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class OverlayRenderer {
    private record LabelBox(int x,int y,int width,int height) {}
    private final GuiGraphics g;
    private final SatelliteMapClient mod;
    private final OverlaySettings settings;
    private final int x,y,width,height,mx,my,webZoom;
    private final double left,top,scale;
    private final List<LabelBox> labels=new ArrayList<>();
    private String tooltip="";
    private int fillBudget=8192;
    private final boolean detailed;
    private OverlayRenderer(GuiGraphics g,SatelliteMapClient mod,OverlaySettings settings,int x,int y,int width,int height,
                            double left,double top,double scale,int zoom,int mx,int my,boolean full) {
        this.g=g;this.mod=mod;this.settings=settings;this.x=x;this.y=y;this.width=width;this.height=height;
        this.left=left;this.top=top;this.scale=scale;this.mx=mx;this.my=my;webZoom=mod.layer.maxZoomOut()-zoom;
        detailed=full || !settings.tabDetails || Minecraft.getInstance().options.keyPlayerList.isDown();
    }
    public static void draw(GuiGraphics g,SatelliteMapClient mod,OverlaySettings settings,int x,int y,int width,int height,
                            double left,double top,double scale,int zoom,int mx,int my,boolean full) {
        new OverlayRenderer(g,mod,settings,x,y,width,height,left,top,scale,zoom,mx,my,full).draw();
    }
    private OverlayGeometry.Point project(double px,double py,double pz) {
        var p=mod.layer.project(px,py,pz);return new OverlayGeometry.Point(x+p.x()*scale-left,y+p.y()*scale-top);
    }
    private boolean inside(double px,double py,int margin) {return px>=x-margin && px<x+width+margin && py>=y-margin && py<y+height+margin;}
    private void draw() {
        var snapshot=mod.markers.request(mod.model,mod.selection.world());
        List<OverlayModel.MarkerSet> enabled=snapshot.data().sets().stream()
                .filter(s->settings.enabled(mod.model.base(),s) && OverlayModel.zoomVisible(webZoom,s.minZoom(),s.maxZoom())).toList();
        int shapes=0;
        for(var set:enabled)for(var shape:set.shapes()) {
            if(shapes>=512 || !OverlayModel.zoomVisible(webZoom,shape.minZoom(),shape.maxZoom()))continue;
            List<OverlayGeometry.Point> points=shape.points().stream().map(p->project(p.x(),p.y(),p.z())).toList();
            double minX=points.stream().mapToDouble(OverlayGeometry.Point::x).min().orElse(0),maxX=points.stream().mapToDouble(OverlayGeometry.Point::x).max().orElse(0);
            double minY=points.stream().mapToDouble(OverlayGeometry.Point::y).min().orElse(0),maxY=points.stream().mapToDouble(OverlayGeometry.Point::y).max().orElse(0);
            double tolerance=4+shape.style().weight()/2.0;
            if(maxX<x-tolerance || minX>x+width+tolerance || maxY<y-tolerance || minY>y+height+tolerance)continue;shapes++;
            boolean filled=shape.closed() && (shape.style().fill()>>>24)>0;
            if(filled && fillBudget>0) {
                for(var span:OverlayGeometry.spans(points,x,y,x+width,y+height)) {
                    if(fillBudget--<=0)break;g.fill(span.x0(),span.y(),span.x1(),span.y()+1,shape.style().fill());
                }
            }
            for(int i=1;i<points.size();i++)line(points.get(i-1),points.get(i),shape.style().stroke(),shape.style().weight());
            if(shape.closed())line(points.getLast(),points.getFirst(),shape.style().stroke(),shape.style().weight());
            if(detailed && settings.labels && set.labels()) {
                var anchor=filled?new OverlayGeometry.Point((minX+maxX)/2,(minY+maxY)/2):
                        OverlayGeometry.visibleEdge(points,shape.closed(),x+2,y+2,x+width-2,y+height-2);
                if(anchor!=null)label(shape.label(),anchor.x(),anchor.y(),0xFFEAD8A0);
            }
            if(inside(mx,my,0) && OverlayGeometry.hit(points,shape.closed(),filled,mx,my,tolerance))tooltip=set.label()+" · "+shape.detail();
        }
        int markerCount=0;
        for(var set:enabled)for(var marker:set.markers()) {
            if(markerCount>=256 || !OverlayModel.zoomVisible(webZoom,marker.minZoom(),marker.maxZoom()))continue;
            var p=project(marker.point().x(),marker.point().y(),marker.point().z());if(!inside(p.x(),p.y(),16))continue;markerCount++;
            int px=(int)Math.round(p.x()),py=(int)Math.round(p.y()),half=settings.markerSize/2;
            var icon=mod.icons.get(OverlayModel.iconUri(mod.model.markerRoot(),marker.icon()),300);
            if(icon==null) {int dot=Math.max(1,half-1);g.fill(px-dot,py-dot,px+dot,py+dot,0xFFFFCF69);}
            else g.blit(icon,px-half,py-half,px+half,py+half,0,1,0,1);
            if(detailed && settings.labels && set.labels())label(marker.label(),px+half+3,py-4,0xFFFFE5B2);
            if(Math.abs(mx-px)<=half+3 && Math.abs(my-py)<=half+3)tooltip=set.label()+" · "+marker.detail()+String.format(Locale.ROOT,"\nX %.0f  Y %.0f  Z %.0f",marker.point().x(),marker.point().y(),marker.point().z());
        }
        if(settings.players)drawPlayers();
        if(settings.mobs)drawMobs();
        if(!tooltip.isEmpty() && inside(mx,my,0)) {
            var font=Minecraft.getInstance().font;g.setTooltipForNextFrame(font,font.split(Component.literal(tooltip),240),mx,my);
        }
    }
    public static List<DynmapService.Player> visiblePlayers(List<DynmapService.Player> remote,List<DynmapService.Player> local,String world,String self) {
        Map<String,DynmapService.Player> players=new LinkedHashMap<>();
        for(var source:List.of(remote,local))for(var player:source) {
            if(!player.world().equals(world) || player.account().equalsIgnoreCase(self) || player.account().isBlank())continue;
            if(!Double.isFinite(player.x()) || !Double.isFinite(player.y()) || !Double.isFinite(player.z()))continue;
            players.put(player.account().toLowerCase(Locale.ROOT),player);
        }
        return List.copyOf(players.values());
    }
    private void drawPlayers() {
        Minecraft mc=Minecraft.getInstance();List<DynmapService.Player> nearby=new ArrayList<>();
        String world=mod.selection.world(),self=mc.player==null?"":mc.player.getGameProfile().name();
        if(mc.level!=null)for(var p:mc.level.players()){var at=p.getPosition(MapRenderer.partialTick());nearby.add(new DynmapService.Player(p.getGameProfile().name(),world,at.x,at.y,at.z,OverlayModel.plain(p.getDisplayName().getString())));}
        var remote=System.currentTimeMillis()-mod.playersObservedAt<=20000?mod.mapPlayers:List.<DynmapService.Player>of();
        int shown=0;
        for(var player:visiblePlayers(remote,nearby,world,self)) {
            var p=project(player.x(),player.y(),player.z());if(!inside(p.x(),p.y(),8) || shown++>=256)continue;
            int px=(int)Math.round(p.x()),py=(int)Math.round(p.y());
            int half=settings.playerSize/2;
            MapRenderer.iconOutline(g,px,py,settings.playerSize,0xB063BCFF);
            Portraits.player(g,mod,player.account(),px,py,settings.playerSize);
            if(detailed && settings.playerNames)label(player.label().isBlank()?player.account():player.label(),px+half+3,py-4,0xFFBBDDFF);
            if(Math.abs(mx-px)<half+2 && Math.abs(my-py)<half+2)tooltip=player.account()+String.format(Locale.ROOT,"\nX %.0f  Y %.0f  Z %.0f",player.x(),player.y(),player.z());
        }
    }
    private void drawMobs() {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        boolean heads=mc.options.keyPlayerList.isDown();int count=0;
        for(var mob:mod.radar.visible()) {
            if(!mob.isAlive() || Math.abs(mob.getY()-mc.player.getY())>settings.radarHeight || mob.distanceToSqr(mc.player)>settings.radarRadius*settings.radarRadius)continue;
            var kind=MobRadar.classify(mob);
            if((kind==MobRadar.Kind.HOSTILE && !settings.hostile)||(kind==MobRadar.Kind.NEUTRAL && !settings.neutral)||(kind==MobRadar.Kind.FRIENDLY && !settings.friendly))continue;
            var at=mob.getPosition(MapRenderer.partialTick());var point=project(at.x,at.y,at.z);if(!inside(point.x(),point.y(),5) || count++>=128)continue;
            int px=(int)Math.round(point.x()),py=(int)Math.round(point.y());
            if(heads) {
                int half=settings.mobSize/2;MapRenderer.iconOutline(g,px,py,settings.mobSize,kind.color);
                if(!Portraits.mob(g,mob,px,py,settings.mobSize)) {g.fill(px-half,py-half,px+half,py+half,0xDD17202B);g.fill(px-1,py-1,px+2,py+2,kind.color);}
            } else {
                int dot=Math.max(1,settings.mobSize/5);g.fill(px-dot-1,py-dot-1,px+dot+2,py+dot+2,0xB0000000);g.fill(px-dot,py-dot,px+dot+1,py+dot+1,kind.color);
            }
            if(Math.abs(mx-px)<6 && Math.abs(my-py)<6)tooltip=mob.getName().getString()+String.format(Locale.ROOT,"\nX %.0f Y %.0f Z %.0f",mob.getX(),mob.getY(),mob.getZ());
        }
    }
    private void label(String value,double px,double py,int color) {
        if(value.isBlank() || labels.size()>=64 || !inside(px,py,0))return;
        float scale=settings.textSize/9f;
        String text=MapRenderer.clip(value,(int)(Math.min(120,width-8)/scale));int w=(int)Math.ceil(Minecraft.getInstance().font.width(text)*scale),h=settings.textSize+1;
        int lx=Math.clamp((int)Math.round(px),x+2,Math.max(x+2,x+width-w-2)),ly=Math.clamp((int)Math.round(py),y+2,Math.max(y+2,y+height-h-1));
        LabelBox box=new LabelBox(lx,ly,w,h);
        for(var old:labels)if(box.x<old.x+old.width+2 && box.x+box.width+2>old.x && box.y<old.y+old.height+1 && box.y+box.height+1>old.y)return;
        labels.add(box);g.fill(lx-1,ly-1,lx+w+1,ly+h,0x80101822);MapRenderer.textScaled(g,text,lx,ly,color,settings.textSize);
    }
    private void line(OverlayGeometry.Point a,OverlayGeometry.Point b,int color,int weight) {
        if((color>>>24)==0)return;
        double[] clipped=OverlayGeometry.clipLine(a,b,x,y,x+width-1,y+height-1);if(clipped==null)return;
        double dx=clipped[2]-clipped[0],dy=clipped[3]-clipped[1];int count=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy)))),half=weight/2;
        for(int i=0;i<=count;i++) {int px=(int)Math.round(clipped[0]+dx*i/count),py=(int)Math.round(clipped[1]+dy*i/count);g.fill(px-half,py-half,px-half+weight,py-half+weight,color);}
    }
    private OverlayRenderer(){throw new AssertionError();}
}
