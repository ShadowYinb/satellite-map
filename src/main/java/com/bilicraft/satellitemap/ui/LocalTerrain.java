package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.map.DynmapModel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix3x2f;
import java.util.*;

/** Only reads chunks already received by the client. All world/GPU access stays on the client thread. */
public final class LocalTerrain implements AutoCloseable {
    private static final int RADIUS=6,SCANS_PER_TICK=4;
    private static final class Entry {
        final LevelChunk chunk;final Identifier id;final DynamicTexture texture;
        int[] colors;long scanned;
        Entry(LevelChunk chunk,Identifier id,DynamicTexture texture){this.chunk=chunk;this.id=id;this.texture=texture;}
    }
    private final Map<ChunkPos,Entry> entries=new HashMap<>();
    private ClientLevel level;
    private long ticks;
    private int serial,lastSlice=Integer.MIN_VALUE;

    public void tick(Minecraft mc,boolean enabled) {
        if(level!=mc.level){close();level=mc.level;}
        if(level==null || mc.player==null || !enabled)return;
        ticks++;
        int cx=mc.player.chunkPosition().x,cz=mc.player.chunkPosition().z;
        int slice=level.dimensionType().hasCeiling()?mc.player.blockPosition().getY():Integer.MAX_VALUE;
        if(slice!=lastSlice){for(var entry:entries.values())entry.scanned=0;lastSlice=slice;}
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()) {
            var e=iterator.next();var p=e.getKey();
            if(Math.abs(p.x-cx)>RADIUS || Math.abs(p.z-cz)>RADIUS || loaded(p)!=e.getValue().chunk) {
                mc.getTextureManager().release(e.getValue().id);iterator.remove();
            }
        }
        List<ChunkPos> pending=new ArrayList<>();
        for(int z=cz-RADIUS;z<=cz+RADIUS;z++)for(int x=cx-RADIUS;x<=cx+RADIUS;x++) {
            var pos=new ChunkPos(x,z);var entry=entries.get(pos);
            if((entry==null || ticks-entry.scanned>=20) && loaded(pos)!=null)pending.add(pos);
        }
        // Populate the closest missing chunks first; established chunks refresh fairly by age.
        pending.sort(Comparator.<ChunkPos>comparingInt(p->entries.containsKey(p)?1:0)
                .thenComparingLong(p->entries.containsKey(p)?entries.get(p).scanned:0)
                .thenComparingInt(p->(p.x-cx)*(p.x-cx)+(p.z-cz)*(p.z-cz)));
        for(var pos:pending.stream().limit(SCANS_PER_TICK).toList())scan(mc,pos,slice);
    }
    private LevelChunk loaded(ChunkPos pos){return level.getChunkSource().getChunk(pos.x,pos.z,ChunkStatus.FULL,false);}
    private void scan(Minecraft mc,ChunkPos chunkPos,int slice) {
        var chunk=loaded(chunkPos);if(chunk==null)return;
        int[] colors=new int[256],heights=new int[256];var pos=new BlockPos.MutableBlockPos();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int index=z*16+x,y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            if(slice!=Integer.MAX_VALUE)y=Math.min(y,slice+1);
            int color=0xFF0C141C;
            for(;y>=level.getMinY();y--) {
                pos.set(chunkPos.getMinBlockX()+x,y,chunkPos.getMinBlockZ()+z);
                var state=chunk.getBlockState(pos);var mapColor=state.getMapColor(level,pos);
                if(mapColor==MapColor.NONE)continue;
                color=mapColor.calculateARGBColor(MapColor.Brightness.HIGH);
                int tint=mc.getBlockColors().getColor(state,level,pos,0);
                if(tint!=-1)color=0xFF000000|(tint&0xFFFFFF);
                break;
            }
            heights[index]=y;
            // Small relief shading retains individual blocks and makes height changes legible.
            int slope=z==0?0:Integer.compare(y,heights[index-16]);
            double shade=slope>0?1.07:slope<0?.84:1;
            colors[index]=0xFF000000|Math.min(255,(int)(((color>>16)&255)*shade))<<16|
                    Math.min(255,(int)(((color>>8)&255)*shade))<<8|Math.min(255,(int)((color&255)*shade));
        }
        Entry entry=entries.get(chunkPos);
        if(entry==null) {
            var pixels=new NativeImage(16,16,true);
            var texture=new DynamicTexture(()->"Satellite map live client chunk",pixels);
            var id=Identifier.fromNamespaceAndPath("satellite_map","local_chunk_"+serial++);
            mc.getTextureManager().register(id,texture);entry=new Entry(chunk,id,texture);entries.put(chunkPos,entry);
        }
        if(!Arrays.equals(entry.colors,colors)) {
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)entry.texture.getPixels().setPixel(x,z,colors[z*16+x]);
            entry.texture.upload();entry.colors=colors;
        }
        entry.scanned=ticks;
    }
    public void draw(GuiGraphics g,DynmapModel.Layer layer,int x,int y,int width,int height,double left,double top,double scale,double projectionY) {
        if(level==null || level!=Minecraft.getInstance().level)return;
        var m=layer.matrix();
        // A top-down chunk bitmap follows the website's X/Z projection, including rotated maps.
        var transform=new Matrix3x2f((float)(m[0]*scale),(float)(-m[3]*scale),(float)(m[2]*scale),(float)(-m[5]*scale),0,0);
        for(var item:entries.entrySet()) {
            var entry=item.getValue();if(loaded(item.getKey())!=entry.chunk)continue;
            var origin=layer.project(item.getKey().getMinBlockX(),projectionY,item.getKey().getMinBlockZ());
            double px=x+origin.x()*scale-left,py=y+origin.y()*scale-top;
            double extent=16*scale*(Math.abs(m[0])+Math.abs(m[2])+Math.abs(m[3])+Math.abs(m[5]));
            if(px+extent<x || py+extent<y || px-extent>x+width || py-extent>y+height)continue;
            g.pose().pushMatrix();g.pose().translate((float)px,(float)py);g.pose().mul(transform);
            g.blit(entry.id,0,0,16,16,0,1,0,1);g.pose().popMatrix();
        }
    }
    @Override public void close() {
        var manager=Minecraft.getInstance().getTextureManager();
        for(var entry:entries.values())manager.release(entry.id);
        entries.clear();level=null;lastSlice=Integer.MIN_VALUE;
    }
}
