package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;

final class MobHeadSmokeTest {
    static void check(ClientGameTestContext c,Path screenshot) throws Exception {
        int[][] regions=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;double scale=mc.getWindow().getGuiScale(),mapScale=mod.layer.scale(mod.config.zoom);var p=mc.player.getPosition(MapRenderer.partialTick());var center=mod.layer.project(p.x,p.y,p.z);var b=MinimapLayout.bounds(mod.config,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            return mod.radar.visible().stream().filter(m->Set.of("bat","horse","parrot","rabbit").contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath())).map(m->{var at=m.getPosition(MapRenderer.partialTick());var point=mod.layer.project(at.x,at.y,at.z);int x=(int)((b.x()+b.width()/2.0+(point.x()-center.x())*mapScale)*scale),y=(int)((b.y()+b.height()/2.0+(point.y()-center.y())*mapScale)*scale);return new int[]{x,y,(int)(2*scale),(int)(5*scale)};}).toArray(int[][]::new);});
        if(regions.length<4)throw new AssertionError("Missing head test entities");
        var image=ImageIO.read(screenshot.toFile());
        for(int[] r:regions){int background=image.getRGB(r[0]-r[3],r[1]-r[3]),filled=0,count=0;
            for(int y=r[1]-r[2];y<r[1]+r[2];y++)for(int x=r[0]-r[2];x<r[0]+r[2];x++){
                int color=image.getRGB(x,y),difference=0;for(int shift:new int[]{16,8,0})difference=Math.max(difference,Math.abs(((color>>shift)&255)-((background>>shift)&255)));if(difference>8)filled++;count++;
            }
            if(filled<count/2)throw new AssertionError("Empty mob portrait: "+filled+"/"+count);
        }
        SatelliteMapClient.LOGGER.info("Mob heads: bat, horse, parrot and rabbit fallback have filled portrait interiors");
    }
}
