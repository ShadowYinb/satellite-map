package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.MinimapLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.util.HashMap;

final class RadarHeightSmokeTest {
    static void run(ClientGameTestContext c,TestServerContext server) throws Exception {
        var layers=c.computeOnClient(mc->new HashMap<>(SatelliteMapClient.INSTANCE.config.minimapOverlays.layers));
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;mod.config.minimapOverlays.self=false;mod.config.minimapOverlays.players=false;
            for(var set:mod.markers.request(mod.model,mod.selection.world()).data().sets())mod.config.minimapOverlays.set(mod.model.base(),set.id(),false);});
        for(String coordinates:new String[]{"-40 100 0","-20 132 0","0 164 0"})server.runCommand("execute in minecraft:the_end run summon cow "+coordinates+" {Tags:[\"satellite_radar_probe\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
        c.waitFor(mc->SatelliteMapClient.INSTANCE.radar.visible().stream().filter(m->m.getType()==net.minecraft.world.entity.EntityType.COW).count()==3,1200);
        c.waitTicks(40);int[] close=sample(c,"satellite-height-fade-close");
        if(!(close[0]>close[1]+20 && close[1]>close[2]+15))throw new AssertionError("Radar height fade is not progressive: "+java.util.Arrays.toString(close));
        c.runOnClient(mc->SatelliteMapClient.INSTANCE.config.zoom=4);c.waitTicks(5);int[] far=sample(c,"satellite-height-fade-far");
        if(far[1]<=close[1]+20 || far[2]<=close[2]+20)throw new AssertionError("Zooming out did not reduce height fading");
        c.runOnClient(mc->{var s=SatelliteMapClient.INSTANCE.config.minimapOverlays;SatelliteMapClient.INSTANCE.config.zoom=2;s.self=true;s.players=true;s.layers.clear();s.layers.putAll(layers);});
        server.runCommand("execute in minecraft:the_end run kill @e[tag=satellite_radar_probe]");c.waitTicks(10);
        SatelliteMapClient.LOGGER.info("Radar height fade: close {}, zoomed out {}",java.util.Arrays.toString(close),java.util.Arrays.toString(far));
    }
    private static int[] sample(ClientGameTestContext c,String name) throws Exception {
        int[][] coordinates=c.computeOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var b=MinimapLayout.bounds(mod.config,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());var p=mc.player.getPosition(com.bilicraft.satellitemap.ui.MapRenderer.partialTick());var center=mod.layer.project(p.x,p.y,p.z);double s=mc.getWindow().getGuiScale(),mapScale=mod.layer.scale(mod.config.zoom);int[][] points=new int[3][2];
            for(int i=0;i<3;i++){var at=mod.layer.project(-40+i*20,100+i*32,0);points[i]=new int[]{(int)((b.x()+b.width()/2.0+(at.x()-center.x())*mapScale)*s),(int)((b.y()+b.height()/2.0+(at.y()-center.y())*mapScale)*s)};}return points;});
        var image=ImageIO.read(c.takeScreenshot(name).toFile());int[] levels=new int[3];for(int i=0;i<3;i++)levels[i]=(image.getRGB(coordinates[i][0],coordinates[i][1])>>8)&255;return levels;
    }
}
