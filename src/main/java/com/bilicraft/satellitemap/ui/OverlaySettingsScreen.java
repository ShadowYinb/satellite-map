package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.config.OverlaySettings;
import com.bilicraft.satellitemap.map.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.util.*;
import java.util.function.IntConsumer;

/** Layer visibility, sizes, typography and radar share one screen and one map selector. */
public final class OverlaySettingsScreen extends Screen {
    private final Screen parent;private final SatelliteMapClient mod;
    private boolean full;private int section,page;private URI site;
    private List<OverlayModel.MarkerSet> sets=List.of();private String signature="",message="正在读取网站图层…";
    public OverlaySettingsScreen(Screen parent,SatelliteMapClient mod,boolean full){super(Component.literal("图层与玩家"));this.parent=parent;this.mod=mod;this.full=full;}
    private OverlaySettings options(){return full?mod.config.fullmapOverlays:mod.config.minimapOverlays;}
    @Override protected void init(){
        int left=width/2-150;var c=options();
        button(full?"当前: 大地图":"当前: 小地图",left,30,145,()->{full=!full;page=0;rebuildWidgets();});
        button("自身位置: "+state(c.self),left+155,30,145,()->{c.self=!c.self;rebuildWidgets();});
        button("显示图层",left,56,96,()->{section=0;rebuildWidgets();});
        button("图标文字",left+102,56,96,()->{section=1;rebuildWidgets();});
        button("生物雷达",left+204,56,96,()->{section=2;rebuildWidgets();});
        if(section==0){
            button("在线玩家: "+state(c.players),left,82,145,()->{c.players=!c.players;rebuildWidgets();});
            button("玩家名称: "+state(c.playerNames),left+155,82,145,()->{c.playerNames=!c.playerNames;rebuildWidgets();});
            button("图层标签: "+state(c.labels),left,108,145,()->{c.labels=!c.labels;rebuildWidgets();});
            button("恢复网页默认",left+155,108,145,()->{if(site!=null)c.layers.keySet().removeIf(k->k.startsWith(site+"|"));rebuildWidgets();});
            int rows=Math.max(1,(height-190)/24),count=rows*2;
            page=Math.clamp(page,0,Math.max(0,(sets.size()-1)/count));
            for(int i=page*count;i<Math.min(sets.size(),(page+1)*count);i++){
                var set=sets.get(i);int position=i-page*count;
                button(MapRenderer.clip(set.label(),88)+" ("+set.count()+"): "+state(c.enabled(site,set)),left+(position%2)*155,140+(position/2)*24,145,()->{c.set(site,set.id(),!c.enabled(site,set));rebuildWidgets();});
            }
            button("<",left,height-27,28,()->{page--;rebuildWidgets();});
            button(">",left+32,height-27,28,()->{page++;if(page*count>=sets.size())page=0;rebuildWidgets();});
        }else if(section==1){
            slider("玩家头像",left,86,4,32,c.playerSize,v->c.playerSize=v);slider("标记图标",left+155,86,4,32,c.markerSize,v->c.markerSize=v);
            slider("自身头像",left,114,4,24,c.selfSize,v->c.selfSize=v);slider("生物头像",left+155,114,4,24,c.mobSize,v->c.mobSize=v);
            slider("文字大小",left,142,4,18,c.textSize,v->c.textSize=v);
            button(full?"文字: 按图层开关":"文字: "+(c.tabDetails?"按住 TAB":"常显"),left+155,142,145,()->{if(full){section=0;}else c.tabDetails=!c.tabDetails;rebuildWidgets();});
            button("图标与字号重置为 6",left,170,300,()->{c.playerSize=c.markerSize=c.selfSize=c.mobSize=c.textSize=6;rebuildWidgets();});
        }else{
            button("生物雷达: "+state(c.mobs),left,86,300,()->{c.mobs=!c.mobs;rebuildWidgets();});
            button("敌对: "+state(c.hostile),left,114,96,()->{c.hostile=!c.hostile;rebuildWidgets();});
            button("中立: "+state(c.neutral),left+102,114,96,()->{c.neutral=!c.neutral;rebuildWidgets();});
            button("友好: "+state(c.friendly),left+204,114,96,()->{c.friendly=!c.friendly;rebuildWidgets();});
            slider("雷达范围",left,142,16,256,c.radarRadius,v->c.radarRadius=v);slider("垂直范围",left+155,142,8,128,c.radarHeight,v->c.radarHeight=v);
        }
        button("完成",left+204,height-27,96,this::onClose);
    }
    private void slider(String text,int x,int y,int min,int max,int initial,IntConsumer apply){
        addRenderableWidget(new AbstractSliderButton(x,y,145,20,Component.literal(text+": "+initial),(initial-min)/(double)(max-min)){
            private int number(){return (int)Math.round(min+value*(max-min));}
            @Override protected void updateMessage(){setMessage(Component.literal(text+": "+number()));}
            @Override protected void applyValue(){apply.accept(number());}
        });
    }
    @Override public void tick(){
        DynmapModel model=mod.model;String world=mod.selection==null?"":mod.selection.world();
        if(model==null && !mod.config.profiles.isEmpty()){
            var profile=mod.config.profiles.getFirst();try{model=mod.service.request(profile.baseUri()).snapshot.model();if(model!=null)world=profile.world.isBlank()?model.worlds().getFirst().name():profile.world;}catch(RuntimeException ignored){}
        }
        if(model==null || world.isEmpty())return;
        site=model.base();var snapshot=mod.markers.request(model,world);message=snapshot.error().isEmpty()?"小地图与大地图分别保存显示、尺寸和文字设置。":snapshot.error();
        var next=snapshot.data().sets();String key=site+"|"+world+"|"+next.stream().map(s->s.id()+":"+s.count()).toList();
        if(!key.equals(signature)){signature=key;sets=next;rebuildWidgets();}
    }
    private static String state(boolean enabled){return enabled?"显示":"隐藏";}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,MapRenderer.PANEL);g.drawCenteredString(font,title,width/2,10,MapRenderer.ACCENT);
        if(section==0 && sets.isEmpty())MapRenderer.text(g,"网站尚未返回图层数据。",width/2-150,142,MapRenderer.TEXT);
        String hint=section==0?message:section==1?"图标与文字独立缩放；默认均为 6 GUI 像素。":"红点敌对 / 黄点中立 / 白点友好；TAB 显示头像。";
        MapRenderer.text(g,MapRenderer.clip(hint,300),width/2-150,height-42,0xFF9BAFBE);super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public void onClose(){mod.save();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
