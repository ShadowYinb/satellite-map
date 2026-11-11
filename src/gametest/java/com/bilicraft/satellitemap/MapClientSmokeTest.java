package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.config.MapConfig;
import com.bilicraft.satellitemap.net.DynmapService;
import com.bilicraft.satellitemap.ui.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.options.controls.*;
import org.lwjgl.glfw.GLFW;
import java.net.URI;

/** Optional real-client smoke test. No account or multiplayer connection is used. */
@SuppressWarnings("UnstableApiUsage")
public final class MapClientSmokeTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try {
            context.setScreen(() -> new SettingsScreen(null,SatelliteMapClient.INSTANCE));
            context.waitTicks(3);
            context.takeScreenshot("satellite-settings");
            context.clickScreenButton("世界 / 图层");
            context.waitTicks(3);
            context.takeScreenshot("satellite-world-settings");
            context.clickScreenButton("小地图显示");
            context.waitTicks(3);
            context.takeScreenshot("satellite-display-settings");
            context.clickScreenButton("图层与玩家");context.waitForScreen(OverlaySettingsScreen.class);
            context.takeScreenshot("satellite-main-overlay-entry");context.clickScreenButton("完成");context.waitForScreen(SettingsScreen.class);
            context.clickScreenButton("拖动调整位置…");
            context.waitForScreen(MinimapPositionScreen.class);
            double scale=context.computeOnClient(mc->mc.getWindow().getGuiScale());
            var bounds=context.computeOnClient(mc->MinimapLayout.bounds(SatelliteMapClient.INSTANCE.config,mc.screen.width,mc.screen.height));
            context.getInput().setCursorPos((bounds.x()+30)*scale,(bounds.y()+30)*scale);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            context.getInput().moveCursor(-70*scale,12*scale);
            context.waitTicks(2);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.takeScreenshot("satellite-position-editor");
            context.clickScreenButton("完成");
            context.waitForScreen(SettingsScreen.class);
            context.clickScreenButton("原版按键绑定…");
            context.waitForScreen(KeyBindsScreen.class);
            context.runOnClient(mc->{
                if(java.util.Arrays.stream(mc.options.keyMappings).filter(k->k.getName().startsWith("key.satellite_map.")).count()!=6)
                    throw new AssertionError("Missing vanilla key mappings");
                var screen=(KeyBindsScreen)mc.screen;
                for(var field:KeyBindsScreen.class.getDeclaredFields())if(field.getType()==KeyBindsList.class) {
                    field.setAccessible(true);((KeyBindsList)field.get(screen)).setScrollAmount(Double.MAX_VALUE);
                }
                screen.selectedKey=SatelliteMapClient.INSTANCE.openMap;
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_KP_9);
            context.takeScreenshot("satellite-vanilla-keybindings");
            context.clickScreenButton("gui.done");
            context.waitForScreen(SettingsScreen.class);

            DynmapModel model=DynmapService.discover(URI.create("https://map.bilicraft.com/s2/hyperion/"));
            context.runOnClient(mc -> {
                SatelliteMapClient mod=SatelliteMapClient.INSTANCE;
                mod.model=model;mod.layer=DynmapModel.layer(model.world("Paralon"),"flat");
                mod.selection=new ProfileSelector.Selection(mod.config.profiles.getFirst(),"Paralon",true,"客户端渲染测试 · 真实 Dynmap 瓦片");
                mod.status="客户端渲染测试 · 真实 Dynmap 瓦片";
                FullMapScreen screen=new FullMapScreen(mod);
                // Place the test camera at the website's spawn without a player/world.
                for (var value:new Object[][]{{"cx",4117.0},{"cy",69.0},{"cz",-591.0}}) {
                    var field=FullMapScreen.class.getDeclaredField((String)value[0]);field.setAccessible(true);field.setDouble(screen,(Double)value[1]);
                }
                mc.setScreen(screen);
            });
            // Wall clock requests run in background workers; waitFor also advances render ticks.
            context.waitFor(mc -> {
                var mod=SatelliteMapClient.INSTANCE;
                var path=mod.layer.tilePath("Paralon",2,32,-5);
                return mod.tiles.get(mod.model.tiles().resolve(path),60)!=null;
            },1200);
            context.waitTicks(40);
            context.takeScreenshot("satellite-full-map");
            context.clickScreenButton("设置");
            context.waitForScreen(SettingsScreen.class);
            context.clickScreenButton("取消");
            context.waitForScreen(FullMapScreen.class);
            context.clickScreenButton("关闭");
            context.runOnClient(mc -> SatelliteMapClient.INSTANCE.resetSession());

            // Exercise the actual HUD and registered keyboard events in an isolated flat world.
            try (var singleplayer=context.worldBuilder().create()) {
                singleplayer.getServer().runCommand("gamemode spectator @a");
                singleplayer.getServer().runCommand("tp @a 4117 100 -591");
                context.runOnClient(mc -> {
                    MapConfig testConfig=MapConfig.defaults();
                    testConfig.profiles.getFirst().server=""; // Integrated test world only; never saved as a user mapping.
                    testConfig.profiles.getFirst().world="Paralon";
                    testConfig.autoDetect=false;
                    SatelliteMapClient.INSTANCE.config=testConfig;
                    SatelliteMapClient.INSTANCE.reload();
                });
                context.waitFor(mc -> SatelliteMapClient.INSTANCE.layer!=null && mc.player!=null && Math.abs(mc.player.getX()-4117)<1,1200);
                context.setScreen(() -> null);
                context.waitFor(mc -> {
                    var mod=SatelliteMapClient.INSTANCE;
                    for(int row=-5;row<=-4;row++)for(int col=31;col<=32;col++)
                        if(mod.tiles.get(mod.model.tiles().resolve(mod.layer.tilePath("Paralon",2,col,row)),60)==null)return false;
                    return true;
                },1200);
                context.waitTicks(5);
                context.takeScreenshot("satellite-hud-minimap");
                int originalZoom=context.computeOnClient(mc->SatelliteMapClient.INSTANCE.config.zoom);
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.zoomOut);
                context.waitFor(mc->SatelliteMapClient.INSTANCE.config.zoom==originalZoom+1);
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.zoomIn);
                context.waitFor(mc->SatelliteMapClient.INSTANCE.config.zoom==originalZoom);
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.toggleMinimap);
                context.waitFor(mc -> !SatelliteMapClient.INSTANCE.config.minimapEnabled);
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.toggleMinimap);
                context.waitFor(mc -> SatelliteMapClient.INSTANCE.config.minimapEnabled);
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);
                context.waitForScreen(FullMapScreen.class);
                context.takeScreenshot("satellite-full-map-in-world");
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);
                context.waitForScreen(null);
                context.getInput().pressKey(GLFW.GLFW_KEY_M);
                context.runOnClient(mc->{if(mc.screen!=null)throw new AssertionError("Old key still opens map after native rebinding");});
                context.getInput().pressKey(SatelliteMapClient.INSTANCE.openSettings);
                context.waitForScreen(SettingsScreen.class);
                context.clickScreenButton("取消");
                context.waitForScreen(null);
                WorldSwitchSmokeTest.run(context,singleplayer.getServer());
            }
        } catch (Exception e) {throw new AssertionError("客户端地图渲染测试失败",e);}
    }
}
