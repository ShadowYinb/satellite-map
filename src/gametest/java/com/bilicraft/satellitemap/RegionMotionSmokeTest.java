package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.OverlaySettings;
import com.bilicraft.satellitemap.ui.OverlayRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Compare entire fill, border, text and label-background patches during diagonal sub-GUI panning. */
final class RegionMotionSmokeTest {
    private static final int BACKGROUND=0xFF223344;
    private static final class Preview extends Screen {
        final boolean full;double centerX=10,centerZ=20;Preview(boolean full){super(Component.literal("Region motion probe"));this.full=full;}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            var mod=SatelliteMapClient.INSTANCE;var center=mod.layer.project(centerX,100,centerZ);double scale=mod.layer.scale(2);
            g.fill(20,20,180,180,BACKGROUND);g.enableScissor(20,20,180,180);
            try{OverlayRenderer.draw(g,mod,full?mod.config.fullmapOverlays:mod.config.minimapOverlays,20,20,160,160,center.x()*scale-80,center.y()*scale-80,scale,2,Integer.MIN_VALUE,Integer.MIN_VALUE,full);}finally{g.disableScissor();}
        }
        @Override public boolean isPauseScreen(){return false;}
    }
    static void run(ClientGameTestContext c) throws Exception {
        var saved=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var old=new OverlaySettings[]{mod.config.minimapOverlays,mod.config.fullmapOverlays};
            mod.config.minimapOverlays=new OverlaySettings();mod.config.fullmapOverlays=new OverlaySettings();
            for(var settings:new OverlaySettings[]{mod.config.minimapOverlays,mod.config.fullmapOverlays}){settings.players=false;settings.mobs=false;settings.tabDetails=false;
                for(var set:mod.markers.request(mod.model,mod.selection.world()).data().sets())settings.set(mod.model.base(),set.id(),set.id().equals("residence.markerset") || set.id().equals("factions_markerset"));}
            return old;});
        double scale=c.computeOnClient(mc->mc.getWindow().getGuiScale());
        try {
            for(boolean full:new boolean[]{false,true}){
                c.setScreen(()->new Preview(full));c.waitTicks(3);BufferedImage first=null;
                for(int frame=0;frame<4;frame++){
                    double offset=frame/scale;c.runOnClient(mc->{var screen=(Preview)mc.screen;screen.centerX=10+offset;screen.centerZ=20+offset;});c.waitTicks(2);
                    var current=ImageIO.read(c.takeScreenshot("satellite-region-motion-"+(full?"full-":"mini-")+frame).toFile());
                    if(first==null){first=current;assertFeatures(first,scale);}else{
                        for(int[] patch:new int[][]{{55,55,103,113},{106,106,160,146}})for(int y=(int)(patch[1]*scale);y<(int)(patch[3]*scale);y++)for(int x=(int)(patch[0]*scale);x<(int)(patch[2]*scale);x++)
                            if(first.getRGB(x,y)!=current.getRGB(x-frame,y-frame))throw new AssertionError("Region/text jitter in "+(full?"full":"mini")+" frame "+frame+" at "+x+","+y);
                    }
                }
            }
        }finally{c.setScreen(()->null);c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.minimapOverlays=saved[0];mod.config.fullmapOverlays=saved[1];});}
        SatelliteMapClient.LOGGER.info("Region motion: guild and Residence fill, border, text and label background translate exactly one physical pixel per axis in both map views");
    }
    private static void assertFeatures(BufferedImage image,double scale){
        int glyphs=0,colored=0;
        for(int y=(int)(55*scale);y<(int)(146*scale);y++)for(int x=(int)(55*scale);x<(int)(160*scale);x++){
            int c=image.getRGB(x,y);if(c!=BACKGROUND)colored++;if(((c>>16)&255)>180 && ((c>>8)&255)>140 && (c&255)<180)glyphs++;
        }
        if(colored<500 || glyphs<10)throw new AssertionError("Region/text movement probe has no visible features");
        int blue=image.getRGB((int)(60*scale),(int)(80*scale)),gold=image.getRGB((int)(110*scale),(int)(120*scale));
        if((blue&255)<180 || ((blue>>8)&255)<100 || ((blue>>16)&255)>150)throw new AssertionError("Guild outline missing: "+Integer.toHexString(blue));
        if(((gold>>16)&255)<180 || ((gold>>8)&255)<110 || (gold&255)>150)throw new AssertionError("Residence outline missing: "+Integer.toHexString(gold));
        int text=0;for(int y=(int)(85*scale);y<(int)(91*scale);y++)for(int x=(int)(70*scale);x<(int)(78*scale);x++){int c=image.getRGB(x,y);if(((c>>16)&255)>180 && ((c>>8)&255)>140 && (c&255)<180)text++;}
        if(text<3)throw new AssertionError("Guild text missing from its label box");
    }
}
