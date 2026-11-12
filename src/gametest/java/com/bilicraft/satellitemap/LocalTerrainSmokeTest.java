package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Real chunk packets, block changes and screenshot pixels, against a different website image. */
final class LocalTerrainSmokeTest {
    static void run(ClientGameTestContext c,TestServerContext server,AtomicReference<byte[]> paletteTiles) throws Exception {
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
        checkPalette(c,paletteTiles);
        MovementSmokeTest.run(c,server);
        c.runOnClient(mc->{var config=SatelliteMapClient.INSTANCE.config;config.minimapOverlays.layers.clear();config.minimapOverlays.layers.putAll(saved);config.fullmapOverlays.layers.clear();config.fullmapOverlays.layers.putAll(savedFull);});
        SatelliteMapClient.LOGGER.info("Local terrain: loaded chunks override website, red-to-blue block update, fullscreen website imagery passed");
    }
    private static int pixel(ClientGameTestContext c,String name,boolean full) throws Exception {
        return pixel(c,name,full,25,30);
    }
    private static int pixel(ClientGameTestContext c,String name,boolean full,int wx,int wz) throws Exception {
        int[] sample=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var a=mod.layer.project(wx,mc.player.getY(),wz);var p=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            double x,y;if(full){x=mc.screen.width/2.0;y=mc.screen.height/2.0;}else{var b=MinimapLayout.bounds(mod.config,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());x=b.x()+b.width()/2.0;y=b.y()+b.height()/2.0;}
            double scale=mc.getWindow().getGuiScale(),mapScale=mod.layer.scale(mod.config.zoom);
            return new int[]{(int)((x+(a.x()-p.x())*mapScale)*scale),(int)((y+(a.y()-p.y())*mapScale)*scale)};});
        return ImageIO.read(c.takeScreenshot(name).toFile()).getRGB(sample[0],sample[1]);
    }
    private static void checkPalette(ClientGameTestContext c,AtomicReference<byte[]> paletteTiles) throws Exception {
        int source=pixel(c,"satellite-material-colors",false,70,50),target=0xFF000000;
        for(int shift:new int[]{16,8,0})target|=(int)(((source>>shift)&255)*.78)<<shift;
        var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,target);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);paletteTiles.set(bytes.toByteArray());
        String original=c.computeOnClient(mc->SatelliteMapClient.INSTANCE.config.profiles.getFirst().url);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.profiles.getFirst().url=original+"matched/";mod.reload();});
        c.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;return mod.model!=null && mod.layer!=null && mod.model.base().getPath().contains("matched");},1200);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;for(String id:new String[]{"markers","stargate","residence.markerset","factions_markerset","warzone"})mod.config.minimapOverlays.set(mod.model.base(),id,false);});
        c.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;var p=mod.layer.project(70,100,50);return mod.tiles.sampleBase(mod.model,mod.selection.world(),mod.layer,p.x(),p.y())!=0;},1200);
        c.waitTicks(120);
        int matched=pixel(c,"satellite-palette-matched",false,70,50),error=0;
        for(int shift:new int[]{16,8,0})error=Math.max(error,Math.abs(((matched>>shift)&255)-((target>>shift)&255)));
        if(error>3)throw new AssertionError("Website color calibration did not converge: "+Integer.toHexString(source)+" -> "+Integer.toHexString(matched)+", expected "+Integer.toHexString(target));
        int blue=pixel(c,"satellite-palette-preserves-changes",false);
        if((blue&255)<((blue>>16)&255)*1.5)throw new AssertionError("Calibration erased a real block change");
        SatelliteMapClient.LOGGER.info("Website palette: max RGB error {}, changed blue block retained",error);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.profiles.getFirst().url=original;mod.reload();});
        c.waitFor(mc->SatelliteMapClient.INSTANCE.layer!=null,1200);
    }
}
