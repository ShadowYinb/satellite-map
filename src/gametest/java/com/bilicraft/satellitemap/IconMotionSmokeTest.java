package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.MapRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.imageio.ImageIO;

/** Half a GUI pixel must move the marker by one physical pixel at GUI scale 2. */
final class IconMotionSmokeTest {
    private static final class Preview extends Screen {
        double centerX=10;Preview(){super(Component.literal("Icon movement probe"));}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){MapRenderer.draw(g,SatelliteMapClient.INSTANCE,20,20,160,160,centerX,100,20,2);}
        @Override public boolean isPauseScreen(){return false;}
    }
    static void run(ClientGameTestContext c) throws Exception {
        var settings=c.computeOnClient(mc->{var s=SatelliteMapClient.INSTANCE.config.minimapOverlays;boolean[] saved={s.labels,s.players,s.mobs};s.labels=false;s.players=false;s.mobs=false;return saved;});
        c.setScreen(Preview::new);c.waitTicks(5);int previous=-1;
        double scale=c.computeOnClient(mc->mc.getWindow().getGuiScale());
        for(int frame=0;frame<4;frame++) {
            double center=10+frame/scale;c.runOnClient(mc->((Preview)mc.screen).centerX=center);c.waitTicks(2);
            var image=ImageIO.read(c.takeScreenshot("satellite-icon-motion-"+frame).toFile());int first=Integer.MAX_VALUE;
            for(int y=(int)(70*scale);y<(int)(80*scale);y++)for(int x=(int)(70*scale);x<(int)(85*scale);x++){
                int rgb=image.getRGB(x,y);if(((rgb>>16)&255)>220 && ((rgb>>8)&255)>160 && (rgb&255)<150)first=Math.min(first,x);
            }
            if(first==Integer.MAX_VALUE || (frame>0 && previous-first!=1))throw new AssertionError("Marker jitter: expected one physical pixel, got "+(previous-first));previous=first;
        }
        c.setScreen(()->null);c.runOnClient(mc->{var s=SatelliteMapClient.INSTANCE.config.minimapOverlays;s.labels=settings[0];s.players=settings[1];s.mobs=settings[2];});
        SatelliteMapClient.LOGGER.info("Icon movement: four frames move exactly one physical pixel each");
    }
}
