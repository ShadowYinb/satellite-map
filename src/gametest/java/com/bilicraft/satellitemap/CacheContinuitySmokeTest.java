package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.ui.FullMapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.net.URI;
import java.util.*;

/** Reproduces miss-count thrashing and checks actual GPU image continuity under pressure. */
final class CacheContinuitySmokeTest {
    static void run(ClientGameTestContext context) throws Exception {
        Identifier initial=context.computeOnClient(mc->{
            var mod=SatelliteMapClient.INSTANCE;var p=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            return mod.tiles.resident(TilePyramid.root(mod.model,mod.selection.world(),mod.layer),mod.layer.tileSize(),p.x(),p.y(),p.x()+1,p.y()+1).stream().filter(r->r.tile().level()==0).findFirst().orElseThrow().texture();
        });
        context.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;for(int i=0;i<1600;i++)mod.tiles.local(mod.model.tiles().resolve("missing-metadata/"+i+".png"));});
        context.waitTicks(20);
        context.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var p=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            if(mod.tiles.resident(TilePyramid.root(mod.model,mod.selection.world(),mod.layer),mod.layer.tileSize(),p.x(),p.y(),p.x()+1,p.y()+1).stream().noneMatch(r->r.texture().equals(initial)))throw new AssertionError("Visible tile evicted by metadata pressure");});
        Set<URI> loaded=new HashSet<>();
        context.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;for(int i=0;i<400;i++){
            URI uri=mod.model.tiles().resolve(mod.layer.tilePath("_pressure_",0,i,300));if(mod.tiles.get(uri,3600,20000)!=null)loaded.add(uri);
        }return loaded.size()==400;},2400);
        // Keep the viewport stable while unrelated textures exceed the normal GPU entry limit.
        context.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var p=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            if(mod.tiles.resident(TilePyramid.root(mod.model,mod.selection.world(),mod.layer),mod.layer.tileSize(),p.x(),p.y(),p.x()+1,p.y()+1).stream().noneMatch(r->r.texture().equals(initial)))throw new AssertionError("Visible tile evicted by background downloads");});
        // Clearing detail textures reproduces a cold GPU cache. Persistent coverage must still draw.
        context.runOnClient(mc->SatelliteMapClient.INSTANCE.tiles.clear());
        context.waitTicks(3);checkPreview(context,"satellite-continuity-overview");
        checkSharpness(context);
        for(int i=0;i<3;i++){context.clickScreenButton("+");context.waitTicks(2);context.clickScreenButton("−");context.waitTicks(2);}
        checkPreview(context,"satellite-continuity-zoom-return");
        // Pan away and back through actual full-map input; previously this caused whole-map blanks.
        double scale=context.computeOnClient(mc->mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(200*scale,110*scale);context.getInput().holdMouse(0);context.waitTick();context.getInput().moveCursor(120*scale,0);context.waitTicks(10);
        context.getInput().moveCursor(-120*scale,0);context.waitTick();context.getInput().releaseMouse(0);context.waitTicks(2);
        checkPreview(context,"satellite-continuity-pan-return");
        SatelliteMapClient.LOGGER.info("Cache continuity: metadata pressure, 400 background textures, GPU clear, repeated zoom and pan passed");
    }
    private static void checkPreview(ClientGameTestContext context,String name) throws Exception {
        var image=ImageIO.read(context.takeScreenshot(name).toFile());int[] p=context.computeOnClient(mc->{double s=mc.getWindow().getGuiScale();return new int[]{(int)((mc.screen.width/2.0-20)*s),(int)((mc.screen.height/2.0-25)*s)};});
        int green=(image.getRGB(p[0],p[1])>>8)&255;if(green<48)throw new AssertionError("Previously loaded map turned black: "+name);
    }
    private static void checkSharpness(ClientGameTestContext context) throws Exception {
        var image=ImageIO.read(context.takeScreenshot("satellite-sharp-persistent-cache").toFile());
        int[] bounds=context.computeOnClient(mc->{double s=mc.getWindow().getGuiScale();return new int[]{(int)((mc.screen.width/2.0-50)*s),(int)((mc.screen.width/2.0-25)*s),(int)((mc.screen.height/2.0-25)*s)};});
        int min=255,max=0,changes=0,previous=-1;
        for(int x=bounds[0];x<bounds[1];x++){int green=(image.getRGB(x,bounds[2])>>8)&255;min=Math.min(min,green);max=Math.max(max,green);if(previous>=0 && Math.abs(previous-green)>20)changes++;previous=green;}
        if(max-min<25 || changes<4)throw new AssertionError("Persistent map lost fine checker detail: contrast="+(max-min)+", edges="+changes);
        SatelliteMapClient.LOGGER.info("Persistent map sharpness: checker contrast {}, {} visible detail edges",max-min,changes);
    }
}
