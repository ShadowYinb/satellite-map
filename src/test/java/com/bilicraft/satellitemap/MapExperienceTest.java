package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.config.*;
import com.bilicraft.satellitemap.net.WebAccess;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class MapExperienceTest {
    @TempDir Path folder;
    private final DynmapModel.Layer layer=new DynmapModel.Layer("flat","flat","png",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
    @Test void bilicraftHoverMarkupSeparatesTitleFromCreationMetadata() {
        String html="<div class=\"main\"><div>[国铁]泽地站</div><style>.toptip{display:none;}</style><div class=\"toptip\"><div>创建时间：2024-12-08 14:32</div><div>玩家：Builder</div></div></div>";
        assertEquals("[国铁]泽地站",MarkerText.name(html));
        assertTrue(MarkerText.detail(html,"<p>详细介绍</p>").contains("创建时间：2024-12-08 14:32"));
        assertTrue(MarkerText.detail(html,"<p>详细介绍</p>").contains("详细介绍"));assertFalse(MarkerText.text(html).contains("display:none"));
    }
    @Test void plainNamesAndEncodedCharactersRemainReadable() {
        assertEquals("基地 & 城堡",MarkerText.name("<b>基地 &amp; 城堡</b>"));assertEquals("2024纪念公园",MarkerText.name("2024纪念公园"));
        assertEquals("基地",MarkerText.name("基地 创建时间：2024-01-01"));
    }
    @Test void fourAdditionalZoomLevelsDoNotRequestNonexistentWebsiteLevels() {
        assertEquals(9,layer.displayMaxZoomOut());
        var model=new DynmapModel(URI.create("https://example.test/"),URI.create("https://example.test/tiles/"),"",List.of());
        var planned=TilePyramid.requests(model,"world",layer,9,-100000,-80000,100000,80000);
        assertFalse(planned.isEmpty());assertTrue(planned.stream().allMatch(t->t.level()<=5));
    }
    @Test void downloadPlanStartsWithLargeTilesAndFocusBeforeEdges() {
        var model=new DynmapModel(URI.create("https://example.test/"),URI.create("https://example.test/tiles/"),"",List.of());
        var planned=TilePyramid.requests(model,"world",layer,2,-1000,-800,1000,800);
        assertEquals(5,planned.getFirst().level());
        for(int i=1;i<planned.size();i++)assertTrue(planned.get(i-1).level()>=planned.get(i).level());
        for(int level=0;level<=5;level++) {
            int target=level;var filtered=planned.stream().filter(t->t.level()==target).toList();double previous=-1;
            for(var tile:filtered){double distance=Math.hypot((tile.col()+.5)*tile.size(128),(tile.row()+.5)*tile.size(128));assertTrue(distance>=previous);previous=distance;}
        }
    }
    @Test void hugeViewsKeepTheirDownloadPlanBounded() {
        var model=new DynmapModel(URI.create("https://example.test/"),URI.create("https://example.test/tiles/"),"",List.of());
        var planned=TilePyramid.requests(model,"world",layer,9,-1e7,-1e7,1e7,1e7);assertFalse(planned.isEmpty());assertTrue(planned.size()<=600);
    }
    @Test void iconSizesAndRadarOptionsPersistSeparatelyForBothMaps() throws Exception {
        var config=MapConfig.defaults();config.minimapOverlays.playerSize=16;config.minimapOverlays.mobSize=12;config.fullmapOverlays.markerSize=24;config.minimapOverlays.neutral=false;
        Path file=folder.resolve("config.json");config.save(file);var read=MapConfig.load(file);
        assertEquals(16,read.minimapOverlays.playerSize);assertEquals(6,read.fullmapOverlays.playerSize);assertEquals(24,read.fullmapOverlays.markerSize);assertFalse(read.minimapOverlays.neutral);
    }
    @Test void oldConfigurationGetsHoldTabDefaultAndKeepsExistingLayers() throws Exception {
        Path file=folder.resolve("config.json");Files.writeString(file,"{\"profiles\":[],\"minimapOverlays\":{\"labels\":false,\"layers\":{\"site|markers\":false}}}");
        var config=MapConfig.load(file);assertTrue(config.minimapOverlays.tabDetails);assertTrue(config.minimapOverlays.labels);assertFalse(config.minimapOverlays.layers.get("site|markers"));
    }
    @Test void mobClassificationDistinguishesNeutralAngerFromPassiveAnimals() {
        assertEquals(MobRadar.Kind.HOSTILE,MobRadar.kind(true,false,false));assertEquals(MobRadar.Kind.FRIENDLY,MobRadar.kind(false,false,false));
        assertEquals(MobRadar.Kind.NEUTRAL,MobRadar.kind(true,true,false));assertEquals(MobRadar.Kind.HOSTILE,MobRadar.kind(false,true,true));
    }
    @Test void gzipMetadataIsDecodedAndDecompressedSizeIsLimited() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);byte[] raw="卫星地图".repeat(2000).getBytes(StandardCharsets.UTF_8);
        var output=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(output)){gzip.write(raw);}byte[] zipped=output.toByteArray();
        server.createContext("/",e->{e.getResponseHeaders().set("Content-Encoding","gzip");e.sendResponseHeaders(200,zipped.length);try(var out=e.getResponseBody()){out.write(zipped);}e.close();});server.start();
        URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        try {assertArrayEquals(raw,WebAccess.get(uri,raw.length));assertThrows(IOException.class,()->WebAccess.get(uri,100));}finally{server.stop(0);}
    }
    @Test void sequentialRequestsReuseOneHttpConnection() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var ports=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        server.createContext("/",e->{ports.add(e.getRemoteAddress().getPort());e.sendResponseHeaders(200,1);try(var out=e.getResponseBody()){out.write(1);}e.close();});server.start();
        URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        try {WebAccess.get(uri,1024);WebAccess.get(uri,1024);assertEquals(1,ports.size());}finally{server.stop(0);}
    }
}
