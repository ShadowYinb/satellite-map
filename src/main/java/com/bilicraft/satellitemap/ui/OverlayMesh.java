package com.bilicraft.satellitemap.ui;

import com.bilicraft.satellitemap.map.OverlayGeometry;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import java.util.List;

/** Native GUI quads preserve fractional positions for region fills and outlines alike. */
final class OverlayMesh {
    static void draw(GuiGraphics g,List<OverlayGeometry.Quad> quads,int color) {
        if(quads.isEmpty() || (color>>>24)==0)return;
        double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(var quad:quads)for(var point:quad.vertices()){left=Math.min(left,point.x());top=Math.min(top,point.y());right=Math.max(right,point.x());bottom=Math.max(bottom,point.y());}
        int x=(int)Math.floor(left),y=(int)Math.floor(top);
        var pose=new Matrix3x2f(g.pose());
        var bounds=new ScreenRectangle(x,y,(int)Math.ceil(right)-x+1,(int)Math.ceil(bottom)-y+1).transformMaxBounds(pose);
        var scissor=g.scissorStack.peek();if(scissor!=null)bounds=bounds.intersection(scissor);
        if(bounds==null)return;
        g.guiRenderState.submitGuiElement(new State(List.copyOf(quads),color,pose,scissor,bounds));
    }
    private record State(List<OverlayGeometry.Quad> quads,int color,Matrix3x2f pose,ScreenRectangle scissorArea,ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline(){return RenderPipelines.GUI;}
        @Override public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        @Override public void buildVertices(VertexConsumer vertices){
            for(var quad:quads)for(var point:List.of(quad.a(),quad.d(),quad.c(),quad.b()))vertices.addVertexWith2DPose(pose,(float)point.x(),(float)point.y()).setColor(color);
        }
    }
    private OverlayMesh(){}
}
