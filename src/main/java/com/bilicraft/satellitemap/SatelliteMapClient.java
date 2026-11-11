package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.*;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.DynmapService;
import com.bilicraft.satellitemap.net.MarkerService;
import com.bilicraft.satellitemap.ui.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

public final class SatelliteMapClient implements ClientModInitializer {
    public static SatelliteMapClient INSTANCE;
    public static final Logger LOGGER = LoggerFactory.getLogger("satellite_map");
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("satellite-map.json");
    public MapConfig config;
    public final Path cacheDirectory=FabricLoader.getInstance().getGameDir().resolve("cache/satellite-map");
    public final DynmapService service = new DynmapService(cacheDirectory.resolve("sites"));
    public final TileCache tiles = new TileCache(cacheDirectory.resolve("tiles"));
    public final TileCache icons = new TileCache(cacheDirectory.resolve("icons"),64,32L*1024*1024);
    public final MarkerService markers=new MarkerService(cacheDirectory.resolve("markers"));
    public final MobRadar radar=new MobRadar();
    public final LocalTerrain localTerrain=new LocalTerrain();
    public List<DynmapService.Player> mapPlayers=List.of();
    public long playersObservedAt;
    private final WorldResolver.History worldHistory=new WorldResolver.History();
    public ProfileSelector.Selection selection;
    public DynmapModel model;
    public DynmapModel.Layer layer;
    public String status = "请连接已配置的服务器", address = "";
    private MapProfile manual;
    private String session = "";
    private Object connection;
    private int tick;
    public KeyMapping openMap, openSettings, toggleMinimap, cycleProfile,zoomIn,zoomOut;

    @Override public void onInitializeClient() {
        INSTANCE = this;
        try { config = MapConfig.load(configPath); }
        catch (Exception e) { config = MapConfig.defaults(); LOGGER.error("Cannot read map config",e); }
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("satellite_map","maps"));
        openMap = key("map",GLFW.GLFW_KEY_M,category);
        openSettings = key("settings",GLFW.GLFW_KEY_O,category);
        toggleMinimap = key("toggle",GLFW.GLFW_KEY_N,category);
        cycleProfile = key("cycle",GLFW.GLFW_KEY_B,category);
        zoomIn=key("zoom_in",GLFW.GLFW_KEY_EQUAL,category);
        zoomOut=key("zoom_out",GLFW.GLFW_KEY_MINUS,category);
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        // Fabric may dispatch disconnects on Netty's IO thread. Texture disposal must
        // be queued onto the client/render thread along with the other session state.
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> client.execute(this::resetSession));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { localTerrain.close();tiles.close();icons.close();markers.close();service.close(); });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("satellite_map","minimap"),(g,delta) -> MapRenderer.hud(g,this));
    }
    private KeyMapping key(String name,int key,KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.satellite_map."+name,InputConstants.Type.KEYSYM,key,category));
    }
    private void clientTick(Minecraft mc) {
        tiles.tick();
        icons.tick();
        radar.tick(mc);
        localTerrain.tick(mc,config.minimapEnabled && layer!=null);
        while (openSettings.consumeClick()) if (mc.screen == null) mc.setScreen(new SettingsScreen(null,this));
        while (openMap.consumeClick()) if (mc.screen == null) mc.setScreen(new FullMapScreen(this));
        while (toggleMinimap.consumeClick()) { config.minimapEnabled = !config.minimapEnabled; save(); }
        while (cycleProfile.consumeClick()) if (mc.screen == null) cycle();
        while(zoomIn.consumeClick())if(mc.screen==null)changeMinimapZoom(-1);
        while(zoomOut.consumeClick())if(mc.screen==null)changeMinimapZoom(1);
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        String newAddress = mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
        String dimension = mc.level.dimension().identifier().toString();
        String identity = newAddress + "|" + dimension;
        if (!identity.equals(session) || connection != mc.getConnection()) {
            if(connection!=mc.getConnection() || !address.equals(newAddress))worldHistory.clear();
            service.worldChanged();
            session = identity; connection = mc.getConnection(); address = newAddress;
            selection = null; model = null; layer = null; manual = null; tick = 0;
            mapPlayers=List.of();playersObservedAt=0;
            tiles.clear();
        }
        if (tick++ % 20 != 0) return;
        List<ProfileSelector.Evidence> candidates = new ArrayList<>();
        String error = "";
        for (MapProfile p : config.profiles) {
            if (!p.matches(address,dimension)) continue;
            try {
                DynmapService.Snapshot s = service.request(p.baseUri()).snapshot;
                candidates.add(new ProfileSelector.Evidence(p,s.model(),s.players(),s.observedAt()));
                if (!s.error().isEmpty()) error = s.error();
            } catch (RuntimeException e) { error = "映射设置无效: " + e.getMessage(); }
        }
        MapProfile previous = selection == null ? null : selection.profile();
        if (!config.autoDetect && manual == null && !config.profiles.isEmpty())
            manual = config.profiles.get(Math.clamp(config.manualProfile,0,config.profiles.size()-1));
        selection = ProfileSelector.select(candidates,mc.player.getGameProfile().name(),mc.player.getX(),mc.player.getZ(),
                dimension,previous,config.autoDetect,manual,worldHistory);
        if(selection!=null && selection.identified())worldHistory.remember(selection.profile(),selection.world(),dimension);
        model = null; layer = null;mapPlayers=List.of();playersObservedAt=0;
        if (selection != null) {
            for (ProfileSelector.Evidence e : candidates) if (e.profile() == selection.profile()) {model=e.model();mapPlayers=e.players();playersObservedAt=e.observedAt();}
            if (model != null) layer = DynmapModel.layer(model.world(selection.world()),selection.profile().map);
            status = model == null ? (error.isEmpty()?"正在连接卫星地图…":error)
                    : layer == null ? "未找到网页世界/图层，请在设置里选择" : selection.reason();
            if (model != null && layer != null && !error.isEmpty()) status += " · " + error;
        } else status = error.isEmpty()?"此服务器尚未配置地图；按设置键添加":error;
    }
    public void resetSession() {
        localTerrain.close();
        session = ""; connection = null; address = ""; selection = null; model = null; layer = null; manual = null;
        tiles.clear(); service.reset(); status = "请连接已配置的服务器";
        worldHistory.clear();
        mapPlayers=List.of();playersObservedAt=0;
    }
    public void reload() { resetSession(); tick = 0; }
    public void save() {
        try { config.save(configPath); }
        catch (Exception e) {
            LOGGER.error("Cannot save map config",e);
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal("地图设置保存失败: "+e.getMessage()),false);
        }
    }
    public void cycle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<MapProfile> matching = config.profiles.stream().filter(p -> p.matches(address,mc.level.dimension().identifier().toString())).toList();
        if (matching.isEmpty()) return;
        int index = matching.indexOf(selection == null ? null : selection.profile());
        manual = matching.get((index+1)%matching.size()); config.autoDetect = false; tick = 0;
        config.manualProfile = config.profiles.indexOf(manual);
        mc.player.displayClientMessage(Component.literal("卫星地图: "+manual.name+"（手动，设置内可恢复自动）"),true);
    }
    public void automatic() { config.autoDetect = true; manual = null; tick = 0; save(); }
    public void changeMinimapZoom(int amount) {config.zoom=Math.clamp(config.zoom+amount,layer==null?-6:-layer.maxZoomIn(),layer==null?16:layer.displayMaxZoomOut());save();}
    public String title() { return selection == null ? "卫星地图" : selection.profile().name+" / "+selection.world(); }
}
