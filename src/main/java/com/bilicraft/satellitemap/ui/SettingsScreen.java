package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.config.*;
import com.bilicraft.satellitemap.map.DynmapModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final SatelliteMapClient mod;
    private final List<MapProfile> profiles = new ArrayList<>();
    private int selected, page, mapWidth,mapHeight,corner,zoom,refresh,offsetX,offsetY;
    private boolean enabled,auto;
    private EditBox first,second,third;
    private String error = "";

    public SettingsScreen(Screen parent,SatelliteMapClient mod) {
        super(Component.literal("卫星地图设置")); this.parent=parent;this.mod=mod;
        mod.config.profiles.forEach(p -> profiles.add(p.copy()));
        selected=mod.selection==null?0:Math.max(0,mod.config.profiles.indexOf(mod.selection.profile()));
        mapWidth=mod.config.width();mapHeight=mod.config.height();corner=mod.config.corner;zoom=mod.config.zoom;
        offsetX=mod.config.offsetX;offsetY=mod.config.offsetY;
        refresh=mod.config.refreshSeconds;enabled=mod.config.minimapEnabled;auto=mod.config.autoDetect;
    }
    @Override protected void init() {
        first=null;second=null;third=null;
        int left=width/2-150;
        button("服务器映射",left,28,72,()->switchPage(0));
        button("世界 / 图层",left+76,28,72,()->switchPage(1));
        button("小地图显示",left+152,28,72,()->switchPage(2));
        button("图层与玩家",left+228,28,72,()->{stash();minecraft.setScreen(new OverlaySettingsScreen(this,mod,false));});
        if (page<2) {
            button("<",left,54,22,()->move(-1));
            button(">",left+26,54,22,()->move(1));
            button("新增",left+198,54,48,()->{stash();MapProfile p=new MapProfile();p.name="地图 "+(profiles.size()+1);p.server=mod.address;profiles.add(p);selected=profiles.size()-1;rebuildWidgets();});
            button("删除",left+252,54,48,()->{if(!profiles.isEmpty()){profiles.remove(selected);selected=Math.max(0,selected-1);rebuildWidgets();}});
            if (!profiles.isEmpty()) {
                MapProfile p=profiles.get(selected);
                first=field(left+78,84,page==0?p.name:p.world);
                second=field(left+78,112,page==0?p.server:p.map);
                third=field(left+78,140,page==0?p.url:p.dimension);
            }
        } else {
            button("小地图: "+(enabled?"显示":"隐藏"),left,64,145,()->{enabled=!enabled;rebuildWidgets();});
            button("识别: "+(auto?"自动":"手动"),left+155,64,145,()->{auto=!auto;rebuildWidgets();});
            slider("宽度",left,92,96,320,mapWidth,v->mapWidth=v);
            slider("高度",left+155,92,96,320,mapHeight,v->mapHeight=v);
            String[] corners={"右上","左上","右下","左下"};
            button("锚点: "+corners[corner],left,120,145,()->{corner=(corner+1)%4;offsetX=0;offsetY=0;rebuildWidgets();});
            button("拖动调整位置…",left+155,120,145,this::position);
            slider("缩放级别",left,148,-6,16,zoom,v->zoom=v);
            button("瓦片刷新: "+refresh+" 秒",left+155,148,145,()->{refresh=refresh>=300?15:refresh<30?30:refresh<60?60:refresh<120?120:300;rebuildWidgets();});
            button("原版按键绑定…",left,176,300,()->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options)));
        }
        button("取消",width/2-102,height-27,96,this::onClose);
        button("保存并应用",width/2+6,height-27,96,this::apply);
    }
    private void slider(String label,int x,int y,int min,int max,int initial,java.util.function.IntConsumer changed) {
        addRenderableWidget(new AbstractSliderButton(x,y,145,20,Component.literal(label+": "+initial),(initial-min)/(double)(max-min)) {
            private int number() {return (int)Math.round(min+value*(max-min));}
            @Override protected void updateMessage() {setMessage(Component.literal(label+": "+number()));}
            @Override protected void applyValue() {changed.accept(number());}
        });
    }
    private void position() {
        MapConfig draft=new MapConfig();draft.minimapWidth=mapWidth;draft.minimapHeight=mapHeight;
        draft.corner=corner;draft.zoom=zoom;draft.offsetX=offsetX;draft.offsetY=offsetY;
        minecraft.setScreen(new MinimapPositionScreen(this,mod,draft,()->{corner=draft.corner;offsetX=draft.offsetX;offsetY=draft.offsetY;}));
    }
    private Button button(String label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());
    }
    private EditBox field(int x,int y,String text) {
        EditBox box=new EditBox(font,x,y,222,20,Component.literal("地图设置"));
        box.setMaxLength(2048);box.setValue(text);return addRenderableWidget(box);
    }
    private void stash() {
        if (page>=2 || profiles.isEmpty() || first==null) return;
        MapProfile p=profiles.get(selected);
        if (page==0) {p.name=first.getValue().trim();p.server=second.getValue().trim();p.url=third.getValue().trim();}
        else {p.world=first.getValue().trim();p.map=second.getValue().trim();p.dimension=third.getValue().trim();}
    }
    private void move(int direction) {stash();if(!profiles.isEmpty())selected=Math.floorMod(selected+direction,profiles.size());rebuildWidgets();}
    @Override protected void repositionElements() { stash(); super.repositionElements(); }
    private void switchPage(int value) {stash();page=value;error="";rebuildWidgets();}
    private void apply() {
        stash();
        for (int i=0;i<profiles.size();i++) {
            MapProfile p=profiles.get(i);
            try {
                if(p.name.isBlank() || p.server.isBlank())throw new IllegalArgumentException("名称和服务器地址不能为空");
                p.baseUri();
                if (!p.dimension.isBlank() && !p.dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                    throw new IllegalArgumentException("维度应为 minecraft:overworld 等标识");
            } catch(RuntimeException e) {error="第 "+(i+1)+" 组: "+e.getMessage();return;}
        }
        MapConfig c=mod.config;
        c.profiles.clear();profiles.forEach(p->c.profiles.add(p.copy()));
        c.minimapEnabled=enabled;c.autoDetect=auto;c.minimapWidth=mapWidth;c.minimapHeight=mapHeight;
        c.corner=corner;c.zoom=zoom;c.refreshSeconds=refresh;c.offsetX=offsetX;c.offsetY=offsetY;
        c.manualProfile=selected;
        mod.save();mod.reload();minecraft.setScreen(parent);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,MapRenderer.PANEL);
        g.drawCenteredString(font,title,width/2,10,MapRenderer.ACCENT);
        int left=width/2-150;
        if (page<2 && !profiles.isEmpty()) {
            MapProfile p=profiles.get(selected);
            MapRenderer.text(g,MapRenderer.clip((selected+1)+"/"+profiles.size()+"  "+p.name,138),left+54,60,MapRenderer.TEXT);
            String[] labels=page==0?new String[]{"组名","服务器地址","地图网址"}:new String[]{"网页世界名","图层名","游戏维度"};
            for (int i=0;i<3;i++)MapRenderer.text(g,labels[i],left,90+28*i,MapRenderer.TEXT);
            if (page==0) {
                MapRenderer.text(g,"同一入口可建多组；多个地址用逗号分隔。",left,168,0xFF9BAFBE);
                MapRenderer.text(g,MapRenderer.clip("设置: "+mod.openSettings.getTranslatedKeyMessage().getString()+" / 切换组: "+mod.cycleProfile.getTranslatedKeyMessage().getString(),300),left,180,0xFF9BAFBE);
            } else {
                MapRenderer.text(g,"留空自动选择；网页世界名和游戏维度可不同。",left,168,0xFF9BAFBE);
                try {
                    var snapshot=mod.service.request(p.baseUri()).snapshot;
                    String worlds=snapshot.model()==null?"正在读取世界列表…":"世界: "+String.join(", ",snapshot.model().worlds().stream().map(DynmapModel.World::name).toList());
                    MapRenderer.text(g,MapRenderer.clip(worlds,300),left,180,MapRenderer.ACCENT);
                    if (mx>=left && mx<left+300 && my>=176 && my<=192 && snapshot.model()!=null)
                        g.setTooltipForNextFrame(font,font.split(Component.literal(worlds+"\n图层: "+String.join(", ",snapshot.model().worlds().getFirst().layers().stream().map(DynmapModel.Layer::name).toList())),280),mx,my);
                }catch(RuntimeException ignored){}
            }
        } else if (page<2) MapRenderer.text(g,"暂无映射，点击“新增”添加。",left,96,MapRenderer.TEXT);
        else {
            if(error.isEmpty())MapRenderer.text(g,"缩放值越小越近；位置、尺寸使用 GUI 像素。",left,199,0xFF9BAFBE);
        }
        if (!error.isEmpty()) MapRenderer.text(g,MapRenderer.clip(error,width-16),8,height-42,0xFFFF8A80);
        super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {}
    @Override public void onClose() {minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
