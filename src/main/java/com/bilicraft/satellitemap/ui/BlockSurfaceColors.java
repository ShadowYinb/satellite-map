package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.map.TerrainPalette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Uses actual top-face textures, including resource packs, instead of vanilla map item colors. */
final class BlockSurfaceColors {
    private record Surface(int rgb,int tint) {}
    private final Map<BlockState,Surface> surfaces=new HashMap<>();
    private final Map<Identifier,Integer> textures=new HashMap<>();
    private Object resources;
    int color(Minecraft mc,ClientLevel level,BlockPos pos,BlockState state,int fallback) {
        if(resources!=mc.getResourceManager()){clear();resources=mc.getResourceManager();}
        var surface=surfaces.computeIfAbsent(state,s->surface(mc,s,fallback));
        int tint=surface.tint<0?-1:mc.getBlockColors().getColor(state,level,pos,surface.tint);
        if(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && state.getFluidState().is(FluidTags.WATER))tint=BiomeColors.getAverageWaterColor(level,pos);
        return TerrainPalette.multiply(surface.rgb,tint);
    }
    private Surface surface(Minecraft mc,BlockState state,int fallback) {
        var model=mc.getBlockRenderer().getBlockModel(state);var sprite=model.particleIcon();int tint=-1;
        outer:for(var part:model.collectParts(RandomSource.create(0))) {
            var quads=new ArrayList<>(part.getQuads(Direction.UP));
            for(var q:part.getQuads(null)) {
                if(q.direction()==Direction.UP)quads.add(q);
                // Crossed grass/plant models have no horizontal face, but still need their tint.
                if(q.isTinted() && q.sprite().contents().name().equals(sprite.contents().name()))tint=q.tintIndex();
            }
            for(var quad:quads){sprite=quad.sprite();tint=quad.tintIndex();break outer;}
        }
        var contents=sprite.contents();var id=contents.name();int frameWidth=contents.width(),frameHeight=contents.height();
        int average=textures.computeIfAbsent(id,k->{
            var resource=k.withPath("textures/"+k.getPath()+".png");
            try(var input=mc.getResourceManager().open(resource);var pixels=NativeImage.read(input)) {
                long r=0,g=0,b=0,weight=0;
                for(int y=0;y<Math.min(frameHeight,pixels.getHeight());y++)for(int x=0;x<Math.min(frameWidth,pixels.getWidth());x++) {
                    int color=pixels.getPixel(x,y),alpha=color>>>24;
                    r+=((color>>16)&255)*alpha;g+=((color>>8)&255)*alpha;b+=(color&255)*alpha;weight+=alpha;
                }
                return weight==0?fallback:0xFF000000|(int)(r/weight)<<16|(int)(g/weight)<<8|(int)(b/weight);
            }catch(Exception ignored){return fallback;}
        });
        return new Surface(average,tint);
    }
    void clear(){surfaces.clear();textures.clear();}
}
