package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.util.HashMap;

/** Real chunk packets, block changes and screenshot pixels, against a different website image. */
final class LocalTerrainSmokeTest {
    static void run(ClientGameTestContext c,TestServerContext server) throws Exception {
        var saved=c.computeOnClient(mc->new HashMap<>(SatelliteMapClient.INSTANCE.config.minimapOverlays.layers));
        var savedFull=c.computeOnClient(mc->new HashMap<>(SatelliteMapClient.INSTANCE.config.fullmapOverlays.layers));
        c.waitFor(mc->SatelliteMapClient.INSTANCE.markers.request(SatelliteMapClient.INSTANCE.model,SatelliteMapClient.INSTANCE.selection.world()).data().sets().size()==5,1200);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;for(var set:mod.markers.request(mod.model,mod.selection.world()).data().sets()){mod.config.minimapOverlays.set(mod.model.base(),set.id(),false);mod.config.fullmapOverlays.set(mod.model.base(),set.id(),false);}});
        server.runCommand("fill 4 99 16 31 99 47 red_concrete");
        c.waitFor(mc->mc.level.getBlockState(new net.minecraft.core.BlockPos(25,99,30)).is(net.minecraft.world.level.block.Blocks.RED_CONCRETE),1200);
        c.waitTicks(100);
        int red=pixel(c,"satellite-local-terrain-red",false);
        if(((red>>16)&255)<(red&255)*1.5)throw new AssertionError("Minimap did not use the loaded red-concrete chunk: "+Integer.toHexString(red));
        server.runCommand("fill 4 99 16 31 99 47 blue_concrete");
        c.waitFor(mc->mc.level.getBlockState(new net.minecraft.core.BlockPos(25,99,30)).is(net.minecraft.world.level.block.Blocks.BLUE_CONCRETE),1200);
        c.waitTicks(100);
        int blue=pixel(c,"satellite-local-terrain-update",false);
        if((blue&255)<((blue>>16)&255)*1.5)throw new AssertionError("Minimap did not update after a block change: "+Integer.toHexString(blue));
        c.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);c.waitForScreen(FullMapScreen.class);c.waitTicks(30);
        int website=pixel(c,"satellite-local-terrain-fullmap",true);
        if(((website>>8)&255)<((website>>16)&255)*1.25 || ((website>>8)&255)<(website&255))throw new AssertionError("Fullscreen no longer uses website imagery: "+Integer.toHexString(website));
        c.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);c.waitForScreen(null);
        c.runOnClient(mc->{var config=SatelliteMapClient.INSTANCE.config;config.minimapOverlays.layers.clear();config.minimapOverlays.layers.putAll(saved);config.fullmapOverlays.layers.clear();config.fullmapOverlays.layers.putAll(savedFull);});
        SatelliteMapClient.LOGGER.info("Local terrain: loaded chunks override website, red-to-blue block update, fullscreen website imagery passed");
    }
    private static int pixel(ClientGameTestContext c,String name,boolean full) throws Exception {
        int[] sample=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var a=mod.layer.project(25,mc.player.getY(),30);var p=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            double x,y;if(full){x=mc.screen.width/2.0;y=mc.screen.height/2.0;}else{var b=MinimapLayout.bounds(mod.config,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());x=b.x()+b.width()/2.0;y=b.y()+b.height()/2.0;}
            double scale=mc.getWindow().getGuiScale(),mapScale=mod.layer.scale(mod.config.zoom);
            return new int[]{(int)((x+(a.x()-p.x())*mapScale)*scale),(int)((y+(a.y()-p.y())*mapScale)*scale)};});
        return ImageIO.read(c.takeScreenshot(name).toFile()).getRGB(sample[0],sample[1]);
    }
}
