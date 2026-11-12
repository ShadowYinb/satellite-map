package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.OverlaySettings;
import com.bilicraft.satellitemap.ui.OverlayRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Every rendered pixel must remain inside the projected land rectangle, even below one pixel. */
final class RegionZoomSmokeTest {
    private static final int BACKGROUND=0xFF223344;
    private static final class Preview extends Screen {
        final boolean full,guild;int zoom;
        Preview(boolean full,boolean guild){super(Component.literal("Region zoom probe"));this.full=full;this.guild=guild;}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            var mod=SatelliteMapClient.INSTANCE;var center=mod.layer.project(guild?-20:40,100,guild?5:45);double scale=mod.layer.scale(zoom),halfPixel=.5/minecraft.getWindow().getGuiScale();
            g.fill(20,20,180,180,BACKGROUND);g.enableScissor(20,20,180,180);
            try{OverlayRenderer.draw(g,mod,full?mod.config.fullmapOverlays:mod.config.minimapOverlays,20,20,160,160,center.x()*scale-80-halfPixel,center.y()*scale-80-halfPixel,scale,zoom,Integer.MIN_VALUE,Integer.MIN_VALUE,full);}finally{g.disableScissor();}
        }
        @Override public boolean isPauseScreen(){return false;}
    }
    static void run(ClientGameTestContext c) throws Exception {
        var old=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var previous=new OverlaySettings[]{mod.config.minimapOverlays,mod.config.fullmapOverlays};mod.config.minimapOverlays=new OverlaySettings();mod.config.fullmapOverlays=new OverlaySettings();return previous;});
        double gui=c.computeOnClient(mc->mc.getWindow().getGuiScale());
        try{
            for(boolean full:new boolean[]{false,true})for(boolean guild:new boolean[]{true,false}) {
                c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var settings=full?mod.config.fullmapOverlays:mod.config.minimapOverlays;settings.players=false;settings.mobs=false;settings.labels=false;
                    for(var set:mod.markers.request(mod.model,mod.selection.world()).data().sets())settings.set(mod.model.base(),set.id(),set.id().equals(guild?"factions_markerset":"residence.markerset"));});
                c.setScreen(()->new Preview(full,guild));
                for(int zoom=0;zoom<=9;zoom++) {
                    int level=zoom;c.runOnClient(mc->((Preview)mc.screen).zoom=level);c.waitTicks(2);
                    var image=ImageIO.read(c.takeScreenshot("satellite-region-zoom-"+(full?"full-":"mini-")+(guild?"guild-":"residence-")+zoom).toFile());
                    check(image,guild,zoom,gui);
                }
            }
        }finally{c.setScreen(()->null);c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.minimapOverlays=old[0];mod.config.fullmapOverlays=old[1];});}
        SatelliteMapClient.LOGGER.info("Region zoom: guild and Residence retain exact rectangular footprints at levels 0-9 in both views; no cross-shaped or enlarged pixels");
    }
    private static void check(BufferedImage image,boolean guild,int zoom,double gui){
        double scale=Math.scalb(4.0,-zoom),cx=100*gui+.5,cy=100*gui+.5;
        double left=cx-(guild?20:40)*scale*gui/2,right=cx+(guild?20:40)*scale*gui/2;
        double top=cy-(guild?50:30)*scale*gui/2,bottom=cy+(guild?50:30)*scale*gui/2;
        int count=0,minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;
        for(int y=(int)(20*gui);y<(int)(180*gui);y++)for(int x=(int)(20*gui);x<(int)(180*gui);x++)if(image.getRGB(x,y)!=BACKGROUND){
            count++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
            if(x+.5<left-1e-4 || x+.5>right+1e-4 || y+.5<top-1e-4 || y+.5>bottom+1e-4)throw new AssertionError("Region grew a cross/spike outside its real rectangle at zoom "+zoom+": "+x+","+y);
        }
        if(count==0)throw new AssertionError("Region vanished at zoom "+zoom);
        if(left>=20*gui && right<180*gui && top>=20*gui && bottom<180*gui) {
            if(Math.abs(minX-(int)Math.ceil(left-.5))>1 || Math.abs(maxX-((int)Math.ceil(right-.5)-1))>1 || Math.abs(minY-(int)Math.ceil(top-.5))>1 || Math.abs(maxY-((int)Math.ceil(bottom-.5)-1))>1)throw new AssertionError("Region proportions changed at zoom "+zoom);
        }
    }
}
