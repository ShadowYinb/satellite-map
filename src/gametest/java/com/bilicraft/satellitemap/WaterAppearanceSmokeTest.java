package com.bilicraft.satellitemap;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

final class WaterAppearanceSmokeTest {
    static void run(ClientGameTestContext c,TestServerContext server,AtomicReference<byte[]> paletteTiles) throws Exception {
        server.runCommand("fill 34 95 15 63 99 48 sand");server.runCommand("fill 35 96 16 62 99 47 water");
        c.waitFor(mc->mc.level.getFluidState(new net.minecraft.core.BlockPos(50,99,30)).is(net.minecraft.tags.FluidTags.WATER),1200);c.waitTicks(80);
        LocalTerrainSmokeTest.pixel(c,"satellite-water-material",false,50,30);
        int target=0xFF2184A1;var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,target);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);paletteTiles.set(bytes.toByteArray());
        String original=c.computeOnClient(mc->SatelliteMapClient.INSTANCE.config.profiles.getFirst().url);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.profiles.getFirst().url=original+"water/";mod.reload();});
        c.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;return mod.layer!=null && mod.model.base().getPath().contains("water");},1200);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;for(String id:new String[]{"markers","stargate","residence.markerset","factions_markerset","warzone"})mod.config.minimapOverlays.set(mod.model.base(),id,false);});
        c.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;var p=mod.layer.project(50,99,30);return mod.tiles.sampleBase(mod.model,mod.selection.world(),mod.layer,p.x(),p.y())!=0;},1200);
        c.waitTicks(120);int actual=LocalTerrainSmokeTest.pixel(c,"satellite-water-matched",false,50,30),error=0;
        for(int shift:new int[]{16,8,0})error=Math.max(error,Math.abs(((actual>>shift)&255)-((target>>shift)&255)));
        if(error>3)throw new AssertionError("Water color mismatch: "+Integer.toHexString(actual)+", expected "+Integer.toHexString(target));
        SatelliteMapClient.LOGGER.info("Water palette: four-block deep pool, max RGB error {}",error);
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.profiles.getFirst().url=original;mod.reload();});c.waitFor(mc->SatelliteMapClient.INSTANCE.layer!=null,1200);
    }
}
