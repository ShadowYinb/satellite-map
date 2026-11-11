package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.MapConfig;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.ui.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Local website fixture: actual dimension packets, world selection and offline GPU uploads. */
final class WorldSwitchSmokeTest {
    static void run(ClientGameTestContext context,TestServerContext gameServer) throws Exception {
        HttpServer web=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> webWorld=new AtomicReference<>("fixture_overworld");
        String account=context.computeOnClient(mc->mc.player.getGameProfile().name());
        String maps="[{\"name\":\"flat\",\"prefix\":\"flat\",\"image-format\":\"png\",\"worldtomap\":[4,0,0,0,0,-4,0,1,0],\"mapzoomout\":5}]";
        String worlds=java.util.stream.Stream.of("fixture_overworld","other_overworld","fixture_nether","fixture_the_end")
                .map(name->"{\"name\":\""+name+"\",\"maps\":"+maps+"}").collect(java.util.stream.Collectors.joining(","));
        BufferedImage image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<128;x++)for(int y=0;y<128;y++)image.setRGB(x,y,((x/16+y/16)%2==0)?0xFF24594D:0xFF397D67);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);byte[] png=bytes.toByteArray();
        BufferedImage icon=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<8;i++){icon.setRGB(i,3,0xFFFFD15B);icon.setRGB(3,i,0xFFFFD15B);}
        ByteArrayOutputStream iconBytes=new ByteArrayOutputStream();ImageIO.write(icon,"png",iconBytes);byte[] iconPng=iconBytes.toByteArray();
        String markerJson="""
                {"sets":{
                  "markers":{"label":"Markers","markers":{"spawn":{"label":"<div>出生点</div><div>创建时间：2026-11-11</div>","icon":"gate","x":-10,"y":100,"z":-5}},"areas":{"_worldborder_fixture":{"label":"世界边界","x":[-70,90,90,-70],"z":[-60,-60,100,100],"ytop":100,"color":"#FF0000","weight":3,"opacity":0.8,"fillopacity":0}}},
                  "stargate":{"label":"Stargate","markers":{"gate":{"label":"星门","icon":"gate","x":10,"y":100,"z":40}}},
                  "residence.markerset":{"label":"Residence","areas":{"home":{"label":"测试领地","x":[20,60],"z":[30,60],"ytop":100,"color":"#FFBB44","fillcolor":"#FFBB44","fillopacity":0.2}}},
                  "factions_markerset":{"label":"公会区域","areas":{"guild":{"label":"测试公会","x":[-30,-10],"z":[-20,30],"ytop":100,"color":"#55AAFF","fillcolor":"#55AAFF","fillopacity":0.2}}},
                  "warzone":{"label":"warzone","circles":{"zone":{"label":"战区","x":-30,"y":100,"z":40,"xr":15,"zr":12,"color":"#FF4444","fillcolor":"#FF4444","fillopacity":0.15}}}
                }}
                """;
        web.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();byte[] data;int status=200;
            if(path.equals("/standalone/dynmap_config.json"))data=("{\"worlds\":["+worlds+"]}").getBytes(StandardCharsets.UTF_8);
            else if(path.startsWith("/standalone/dynmap_"))data=("{\"players\":[{\"account\":\""+account+"\",\"world\":\""+webWorld.get()+"\",\"x\":10,\"y\":100,\"z\":20},"
                    +"{\"account\":\"FixtureFriend\",\"name\":\"FixtureFriend\",\"world\":\""+webWorld.get()+"\",\"x\":35,\"y\":100,\"z\":10}]}").getBytes(StandardCharsets.UTF_8);
            else if(path.startsWith("/tiles/_markers_/marker_"))data=markerJson.getBytes(StandardCharsets.UTF_8);
            else if(path.startsWith("/tiles/_markers_/"))data=iconPng;
            else if(path.startsWith("/tiles/faces/"))data=iconPng;
            else if(path.startsWith("/tiles/")) {
                boolean coarse=path.substring(path.lastIndexOf('/')+1).startsWith("z");
                // Nether supplies only large tiles; End supplies only base tiles.
                if((path.contains("fixture_nether/") && !coarse)||(path.contains("fixture_the_end/") && coarse)) {status=404;data=new byte[0];}
                else data=png;
            }
            else {status=404;data=new byte[0];}
            exchange.sendResponseHeaders(status,data.length);try(var out=exchange.getResponseBody()){out.write(data);}exchange.close();
        });
        web.start();
        try {
            gameServer.runCommand("tp @a 10 100 20");
            context.runOnClient(mc->{
                var c=MapConfig.defaults();c.profiles.getFirst().server="";
                c.profiles.getFirst().url="http://127.0.0.1:"+web.getAddress().getPort()+"/";
                SatelliteMapClient.INSTANCE.config=c;SatelliteMapClient.INSTANCE.reload();
            });
            waitWorld(context,"minecraft:overworld","fixture_overworld");
            webWorld.set("other_overworld");
            waitWorld(context,"minecraft:overworld","other_overworld");
            LocalTerrainSmokeTest.run(context,gameServer);
            BorderSmokeTest.run(context);
            webWorld.set("fixture_nether");gameServer.runCommand("execute in minecraft:the_nether run tp @a 10 100 20");
            waitWorld(context,"minecraft:the_nether","fixture_nether");
            waitCenterTile(context);
            context.takeScreenshot("satellite-coarse-fallback");
            webWorld.set("fixture_the_end");gameServer.runCommand("execute in minecraft:the_end run tp @a 10 100 20");
            waitWorld(context,"minecraft:the_end","fixture_the_end");
            waitCenterTile(context);
            context.runOnClient(mc->SatelliteMapClient.INSTANCE.config.minimapOverlays.labels=true);
            context.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;return mod.markers.request(mod.model,mod.selection.world()).data().sets().size()==5
                    && mod.mapPlayers.stream().anyMatch(p->p.account().equals("FixtureFriend") && p.world().equals("fixture_the_end"));},1200);
            context.waitTicks(5);
            context.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;var texture=mod.icons.get(OverlayModel.iconUri(mod.model.markerRoot(),"gate"),300);
                if(texture==null)return false;
                var point=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
                if(mod.tiles.resident(TilePyramid.root(mod.model,mod.selection.world(),mod.layer),mod.layer.tileSize(),point.x()-1000,point.y()-1000,point.x()+1000,point.y()+1000)
                        .stream().anyMatch(tile->tile.texture().equals(texture)))throw new AssertionError("Icon texture overwrote map texture");
                return true;},1200);
            context.takeScreenshot("satellite-players-and-regions");
            gameServer.runCommand("difficulty normal");
            gameServer.runCommand("execute in minecraft:the_end run summon zombie 0 100 15 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
            gameServer.runCommand("execute in minecraft:the_end run summon cow 24 100 35 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
            gameServer.runCommand("execute in minecraft:the_end run summon wolf -15 100 45 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
            gameServer.runCommand("execute in minecraft:the_end run summon rabbit 30 100 5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
            context.waitFor(mc->SatelliteMapClient.INSTANCE.radar.visible().size()>=4,1200);
            context.takeScreenshot("satellite-radar-dots");
            context.getInput().holdKey(options->options.keyPlayerList);context.waitTicks(5);
            context.takeScreenshot("satellite-tab-heads");
            context.getInput().releaseKey(options->options.keyPlayerList);context.waitTicks(2);
            context.takeScreenshot("satellite-tab-released");
            context.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);context.waitForScreen(FullMapScreen.class);
            context.clickScreenButton("图层");context.waitForScreen(OverlaySettingsScreen.class);context.waitTicks(3);
            context.clickScreenButton("图标文字");context.waitForScreen(OverlaySettingsScreen.class);
            context.takeScreenshot("satellite-icon-settings");context.clickScreenButton("生物雷达");context.takeScreenshot("satellite-radar-settings");context.clickScreenButton("显示图层");
            context.clickScreenButton("在线玩家: 显示");
            context.clickScreenButton("Stargate (1): 显示");
            context.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;var set=mod.markers.request(mod.model,mod.selection.world()).data().sets().stream().filter(s->s.id().equals("stargate")).findFirst().orElseThrow();
                if(mod.config.fullmapOverlays.players || mod.config.fullmapOverlays.enabled(mod.model.base(),set) || !mod.config.minimapOverlays.players || !mod.config.minimapOverlays.enabled(mod.model.base(),set))throw new AssertionError("Map layer switches are not independent");});
            context.takeScreenshot("satellite-overlay-switches");
            context.clickScreenButton("在线玩家: 隐藏");context.clickScreenButton("完成");context.waitForScreen(FullMapScreen.class);
            context.clickScreenButton("+");context.waitTicks(5);
            assertTerrain(context,"satellite-fine-zoom-in");
            context.clickScreenButton("−");context.clickScreenButton("−");context.waitTicks(5);
            assertTerrain(context,"satellite-fine-zoom-out");
            CacheContinuitySmokeTest.run(context);
            for(int i=0;i<12;i++)context.clickScreenButton("−");
            context.runOnClient(mc->{var field=FullMapScreen.class.getDeclaredField("zoom");field.setAccessible(true);int actual=field.getInt(mc.screen);
                if(actual!=SatelliteMapClient.INSTANCE.layer.displayMaxZoomOut() || actual<=SatelliteMapClient.INSTANCE.layer.maxZoomOut())throw new AssertionError("Extra zoom-out levels unavailable");});
            context.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);context.waitForScreen(null);
            // Forget all in-memory metadata and textures, then disconnect the website completely.
            web.stop(0);context.runOnClient(mc->SatelliteMapClient.INSTANCE.reload());
            waitWorld(context,"minecraft:the_end","fixture_the_end");
            waitCenterTile(context);
            context.takeScreenshot("satellite-offline-cache");
            SatelliteMapClient.LOGGER.info("World switch + offline metadata/tile cache smoke test passed");
            SatelliteMapClient.LOGGER.info("Player overlays + independent layers + fine/coarse zoom fallback smoke test passed");
            SatelliteMapClient.LOGGER.info("Fullscreen + player heads + hold Tab + mob radar + zoom keys smoke test passed");
        } finally {web.stop(0);}
    }
    private static void waitWorld(ClientGameTestContext c,String dimension,String world) {
        c.waitFor(mc->{var mod=SatelliteMapClient.INSTANCE;return mc.level!=null && mc.level.dimension().identifier().toString().equals(dimension)
                && mod.selection!=null && mod.layer!=null && mod.selection.world().equals(world);},1200);
    }
    private static void waitCenterTile(ClientGameTestContext c) {
        c.waitFor(mc->{
            var mod=SatelliteMapClient.INSTANCE;if(mod.layer==null || mc.player==null)return false;
            var point=mod.layer.project(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            return !mod.tiles.resident(TilePyramid.root(mod.model,mod.selection.world(),mod.layer),mod.layer.tileSize(),point.x(),point.y(),point.x()+1,point.y()+1).isEmpty();
        },1200);
    }
    private static void assertTerrain(ClientGameTestContext context,String name) throws Exception {
        var file=context.takeScreenshot(name);var image=ImageIO.read(file.toFile());
        int[] sample=context.computeOnClient(mc->{double scale=mc.getWindow().getGuiScale();return new int[]{(int)((mc.screen.width/2.0-20)*scale),(int)((mc.screen.height/2.0-25)*scale)};});
        int green=(image.getRGB(sample[0],sample[1])>>8)&255;
        if(green<48)throw new AssertionError("Fine tiles vanished after zoom: "+Integer.toHexString(image.getRGB(sample[0],sample[1])));
    }
}
