package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.MinimapLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.util.Arrays;

/** Move with actual game input while checking that the opaque self portrait never shifts. */
final class MovementSmokeTest {
    static void run(ClientGameTestContext c,TestServerContext server) throws Exception {
        c.waitTicks(60);double start=c.computeOnClient(mc->mc.player.getZ());
        int[] portrait=portrait(c,"satellite-walking-0");
        c.getInput().holdKey(options->options.keyUp);
        try {
            for(int frame=1;frame<=3;frame++){
                c.waitTicks(6);
                if(!Arrays.equals(portrait,portrait(c,"satellite-walking-"+frame)))throw new AssertionError("Self portrait shifted while walking");
            }
        } finally {c.getInput().releaseKey(options->options.keyUp);}
        double traveled=Math.abs(c.computeOnClient(mc->mc.player.getZ())-start);
        if(traveled<1)throw new AssertionError("Movement test did not move the player");
        server.runCommand("tp @a 10 100 20");c.waitTicks(20);
        SatelliteMapClient.LOGGER.info("Minimap movement: self portrait fixed through four captures, {} blocks traveled",traveled);
    }
    private static int[] portrait(ClientGameTestContext c,String name) throws Exception {
        int[] rect=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;double s=mc.getWindow().getGuiScale();var b=MinimapLayout.bounds(mod.config,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            int size=mod.config.minimapOverlays.selfSize;
            return new int[]{(int)((b.x()+b.width()/2-size/2)*s),(int)((b.y()+b.height()/2-size/2)*s),(int)(size*s)};});
        var image=ImageIO.read(c.takeScreenshot(name).toFile());return image.getRGB(rect[0],rect[1],rect[2],rect[2],null,0,rect[2]);
    }
}
