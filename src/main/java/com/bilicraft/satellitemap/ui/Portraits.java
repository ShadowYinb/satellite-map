package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.map.DynmapModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import java.util.*;

public final class Portraits {
    public static void player(GuiGraphics g,SatelliteMapClient mod,String account,int x,int y,int size) {
        var mc=Minecraft.getInstance();Identifier skin=null;
        if(mc.player!=null && mc.player.getGameProfile().name().equalsIgnoreCase(account))skin=mc.player.getSkin().body().texturePath();
        else if(mc.getConnection()!=null){var info=mc.getConnection().getPlayerInfoIgnoreCase(account);if(info!=null)skin=info.getSkin().body().texturePath();}
        int half=size/2,left=x-half,top=y-half;
        if(skin!=null){skin(g,skin,left,top,size);return;}
        if(mod.model!=null) {
            var url=mod.model.markerRoot().resolve("faces/16x16/"+DynmapModel.encode(account)+".png");
            var head=mod.icons.get(url,300,20);
            if(head!=null){g.blit(head,left,top,left+size,top+size,0,1,0,1);return;}
        }
        skin(g,DefaultPlayerSkin.getDefaultTexture(),left,top,size);
    }
    private static void skin(GuiGraphics g,Identifier texture,int x,int y,int size) {
        g.blit(texture,x,y,x+size,y+size,8/64f,16/64f,8/64f,16/64f);
        g.blit(texture,x,y,x+size,y+size,40/64f,48/64f,8/64f,16/64f);
    }
    private record Crop(int x,int y,int w,int h,int textureWidth,int textureHeight){}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean mob(GuiGraphics g,Mob mob,int x,int y,int size) {
        String name=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();Crop crop;
        if(Set.of("zombie","husk","drowned","zombie_villager","piglin","piglin_brute","zombified_piglin","witch","villager","wandering_trader").contains(name))crop=new Crop(8,8,8,8,64,64);
        else if(Set.of("skeleton","stray","wither_skeleton","bogged","creeper","enderman","blaze").contains(name))crop=new Crop(8,8,8,8,64,32);
        else crop=switch(name){
            case "cow","mooshroom"->new Crop(6,6,8,8,64,64);
            case "pig"->new Crop(8,8,8,8,64,64);
            case "sheep"->new Crop(6,6,6,6,64,32);
            case "wolf"->new Crop(4,4,6,6,64,32);
            case "cat","ocelot"->new Crop(5,4,5,4,64,32);
            case "chicken"->new Crop(2,3,4,6,64,32);
            case "spider","cave_spider"->new Crop(32,4,8,8,64,32);
            case "slime","magma_cube"->new Crop(8,8,8,8,64,32);
            case "ghast"->new Crop(16,16,16,16,64,32);
            case "iron_golem"->new Crop(8,8,8,10,128,128);
            case "bee"->new Crop(6,6,7,6,64,64);
            default->null;
        };
        if(crop==null) {
            // Vanilla's inventory portrait supports unfamiliar/modded entity models too.
            try {
                int half=size/2,scale=Math.max(1,(int)(size/Math.max(.5,mob.getBbHeight())));
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,x-half,y-half,x+half,y+half,scale,0,x,y,mob);
                return true;
            }catch(RuntimeException e){return false;}
        }
        try {
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
            if(!(renderer instanceof LivingEntityRenderer living))return false;
            var state=living.createRenderState(mob,0);Identifier texture=living.getTextureLocation((net.minecraft.client.renderer.entity.state.LivingEntityRenderState)state);
            int left=x-size/2,top=y-size/2;
            g.blit(texture,left,top,left+size,top+size,crop.x/(float)crop.textureWidth,(crop.x+crop.w)/(float)crop.textureWidth,crop.y/(float)crop.textureHeight,(crop.y+crop.h)/(float)crop.textureHeight);
            return true;
        }catch(RuntimeException e){return false;}
    }
    private Portraits(){}
}
