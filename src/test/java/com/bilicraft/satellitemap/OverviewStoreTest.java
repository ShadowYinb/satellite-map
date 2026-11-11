package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverviewStoreTest {
    @TempDir Path folder;
    private final DynmapModel.Layer layer=new DynmapModel.Layer("flat","flat","png",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
    private final URI base=URI.create("https://example.test/tiles/");
    private URI tile(String world,int level,int col,int row){return base.resolve(layer.tilePath(world,level,col,row));}
    private URI root(String world){return base.resolve(world+"/flat/");}
    private byte[] png(int color) throws Exception {var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,color);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();}
    private BufferedImage image(Map<OverviewStore.Key,byte[]> updates,OverviewStore.Key key) throws Exception {assertTrue(updates.containsKey(key));return ImageIO.read(new ByteArrayInputStream(updates.get(key)));}
    @Test void negativeCoordinatesGenerateCorrectParentQuadrants() throws Exception {
        try(var store=new OverviewStore(folder)){
            store.configure(root("world"),3);store.accept(tile("world",0,1,-1),png(0xFFFF2233));store.flush();
            var result=image(store.drain(100),new OverviewStore.Key(root("world"),1,0,-1));
            assertEquals(0xFFFF2233,result.getRGB(100,100));assertEquals(0,result.getRGB(10,10));
        }
    }
    @Test void coarseRefreshCannotEraseAlreadyLoadedFineCoverage() throws Exception {
        try(var store=new OverviewStore(folder)){
            store.configure(root("world"),3);store.accept(tile("world",1,0,0),png(0xFF1144CC));store.accept(tile("world",0,0,0),png(0xFFFF2233));
            store.accept(tile("world",1,0,0),png(0xFF22CC44));store.flush();
            var result=image(store.drain(100),new OverviewStore.Key(root("world"),2,0,0));
            assertEquals(0xFFFF2233,result.getRGB(10,10));assertEquals(0xFF22CC44,result.getRGB(50,50));
        }
    }
    @Test void overviewSurvivesRestartWithoutAnyWebsiteRequest() throws Exception {
        var key=new OverviewStore.Key(root("world"),4,-1,1);
        try(var store=new OverviewStore(folder)){store.configure(root("world"),4);store.accept(tile("world",0,-1,16),png(0xFF34AB56));store.flush();}
        try(var store=new OverviewStore(folder)){assertTrue(store.request(key));store.flush();var result=image(store.drain(100),key);assertEquals(0xFF34AB56,result.getRGB(127,0));}
    }
    @Test void worldAndLayerNamespacesNeverShareCoverage() throws Exception {
        try(var store=new OverviewStore(folder)){
            store.configure(root("a"),2);store.configure(root("b"),2);store.accept(tile("a",0,0,0),png(0xFFFF2233));store.accept(tile("b",0,0,0),png(0xFF2244FF));store.flush();
            var updates=store.drain(100);assertEquals(0xFFFF2233,image(updates,new OverviewStore.Key(root("a"),1,0,0)).getRGB(10,10));
            assertEquals(0xFF2244FF,image(updates,new OverviewStore.Key(root("b"),1,0,0)).getRGB(10,10));
        }
    }
    @Test void fineCoveragePriorityAlsoSurvivesRestart() throws Exception {
        var key=new OverviewStore.Key(root("world"),2,0,0);
        try(var store=new OverviewStore(folder)){store.configure(root("world"),2);store.accept(tile("world",0,0,0),png(0xFFFF2233));store.flush();}
        try(var store=new OverviewStore(folder)){store.configure(root("world"),2);store.accept(tile("world",1,0,0),png(0xFF2244FF));store.flush();assertEquals(0xFFFF2233,image(store.drain(100),key).getRGB(10,10));}
    }
    @Test void downsamplingAveragesDetailInsteadOfAliasingItAway() throws Exception {
        var pixels=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)pixels.setRGB(x,y,(x+y)%2==0?0xFFFFFFFF:0xFF000000);
        var bytes=new ByteArrayOutputStream();ImageIO.write(pixels,"png",bytes);
        try(var store=new OverviewStore(folder)) {
            store.configure(root("world"),2);store.accept(tile("world",0,0,0),bytes.toByteArray());store.flush();
            var result=image(store.drain(100),new OverviewStore.Key(root("world"),1,0,0));
            assertEquals(0xFF7F7F7F,result.getRGB(10,10));
        }
    }
}
