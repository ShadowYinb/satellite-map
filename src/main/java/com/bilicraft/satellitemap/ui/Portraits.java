package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.SatelliteMapClient;
import com.bilicraft.satellitemap.map.DynmapModel;
import com.bilicraft.satellitemap.map.RadarAppearance;
import com.bilicraft.satellitemap.map.MobRadar;
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
    private record CropKey(Identifier texture,Crop crop){}
    private static final Map<CropKey,Boolean> visibleCrops=new HashMap<>();
    private static Object resourceModel;
    private static boolean visible(Identifier texture,Crop crop) {
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        if(resourceModel!=model){resourceModel=model;visibleCrops.clear();}
        return visibleCrops.computeIfAbsent(new CropKey(texture,crop),key->{
            try(var input=mc.getResourceManager().open(texture);var pixels=com.mojang.blaze3d.platform.NativeImage.read(input)) {
                int x0=crop.x*pixels.getWidth()/crop.textureWidth,y0=crop.y*pixels.getHeight()/crop.textureHeight;
                int x1=(crop.x+crop.w)*pixels.getWidth()/crop.textureWidth,y1=(crop.y+crop.h)*pixels.getHeight()/crop.textureHeight;
                int filled=0,count=0;
                for(int py=y0;py<Math.min(y1,pixels.getHeight());py++)for(int px=x0;px<Math.min(x1,pixels.getWidth());px++){count++;if((pixels.getPixel(px,py)>>>24)>32)filled++;}
                return count>0 && filled>=Math.max(1,count/4);
            }catch(Exception e){return false;}
        });
    }
    public static boolean mob(GuiGraphics g,Mob mob,int x,int y,int size){return mob(g,mob,x,y,size,1);}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean mob(GuiGraphics g,Mob mob,int x,int y,int size,double opacity) {
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
            case "bat"->new Crop(2,9,4,3,32,32);
            case "horse","skeleton_horse","zombie_horse"->new Crop(7,20,6,5,64,64);
            case "parrot"->new Crop(2,4,2,3,32,32);
            default->null;
        };
        if(crop==null) {
            fallback(g,mob,x,y,size,opacity);return true;
        }
        try {
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
            if(!(renderer instanceof LivingEntityRenderer living)){fallback(g,mob,x,y,size,opacity);return true;}
            var state=living.createRenderState(mob,0);Identifier texture=living.getTextureLocation((net.minecraft.client.renderer.entity.state.LivingEntityRenderState)state);
            int left=x-size/2,top=y-size/2;
            if(!visible(texture,crop)){fallback(g,mob,x,y,size,opacity);return true;}
            MapRenderer.blitUv(g,texture,left,top,size,crop.x/(float)crop.textureWidth,(crop.x+crop.w)/(float)crop.textureWidth,crop.y/(float)crop.textureHeight,(crop.y+crop.h)/(float)crop.textureHeight,RadarAppearance.fade(0xFFFFFFFF,opacity));
            return true;
        }catch(RuntimeException e){fallback(g,mob,x,y,size,opacity);return true;}
    }
    private static void fallback(GuiGraphics g,Mob mob,int x,int y,int size,double opacity) {
        int left=x-size/2,top=y-size/2,color=RadarAppearance.fade(MobRadar.classify(mob).color,opacity);
        g.fill(left,top,left+size,top+size,color);
        int eye=Math.max(1,size/6),ink=RadarAppearance.fade(0xFF23313F,opacity);
        g.fill(left+eye,top+size/3,left+eye*2,top+size/3+eye,ink);
        g.fill(left+size-eye*2,top+size/3,left+size-eye,top+size/3+eye,ink);
        g.fill(left+size/3,top+size*2/3,left+size*2/3+1,top+size*2/3+eye,ink);
    }
    private Portraits(){}
}
