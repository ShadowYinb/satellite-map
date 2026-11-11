package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TileRepositoryTest {
    @TempDir Path folder;
    private HttpServer server;
    private URI uri;
    private volatile byte[] body;
    private volatile String etag="\"v1\"",seenTag,seenDate;
    private volatile int status=200,requests;
    private static final String DATE="Wed, 11 Nov 2026 10:00:00 GMT";
    @BeforeEach void start() throws Exception {
        body=image(0xFF336699);server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/tiles/",exchange->{
            requests++;seenTag=exchange.getRequestHeaders().getFirst("If-None-Match");seenDate=exchange.getRequestHeaders().getFirst("If-Modified-Since");
            exchange.getResponseHeaders().set("ETag",etag);exchange.getResponseHeaders().set("Last-Modified",DATE);
            if(status==304)exchange.sendResponseHeaders(304,-1);
            else {exchange.sendResponseHeaders(status,body.length);try(var out=exchange.getResponseBody()){out.write(body);}}
            exchange.close();
        });server.start();uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/tiles/world/flat/0_0/0_0.png");
    }
    @AfterEach void stop() {server.stop(0);}
    private TileRepository repository() {return new TileRepository(new TileDiskStore(folder));}
    private static byte[] image(int color) throws IOException {
        var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,color);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    @Test void downloadedTilesSurviveRestartAndOffline() throws Exception {
        var initial=repository().refresh(uri,null);assertTrue(initial.changed());assertEquals("",initial.storageError());
        server.stop(0);
        var persisted=repository().local(uri);assertNotNull(persisted);assertArrayEquals(initial.tile().png(),persisted.png());
        assertEquals(1,requests);assertThrows(IOException.class,()->repository().refresh(uri,persisted));
        assertArrayEquals(persisted.png(),repository().local(uri).png());
    }
    @Test void unchanged304UsesValidatorsAndKeepsPixels() throws Exception {
        var previous=repository().refresh(uri,null).tile();status=304;
        var current=repository().refresh(uri,previous);
        assertEquals("\"v1\"",seenTag);assertEquals(DATE,seenDate);assertFalse(current.changed());
        assertArrayEquals(previous.png(),current.tile().png());assertTrue(current.tile().checkedAt()>=previous.checkedAt());
        assertArrayEquals(previous.png(),repository().local(uri).png());
    }
    @Test void newWebsitePixelsAtomicallyReplaceOldLocalData() throws Exception {
        var previous=repository().refresh(uri,null).tile();body=image(0xFFCC2211);etag="\"v2\"";
        var next=repository().refresh(uri,previous);assertTrue(next.changed());
        var persisted=repository().local(uri);assertEquals(etag,persisted.etag());
        assertEquals(0xFFCC2211,ImageIO.read(new ByteArrayInputStream(persisted.png())).getRGB(0,0));
        try(var files=Files.walk(folder)) {assertFalse(files.anyMatch(p->p.toString().endsWith(".tmp")));}
    }
    @Test void badNetworkResponsesNeverOverwriteUsefulLocalTile() throws Exception {
        var previous=repository().refresh(uri,null).tile();status=503;
        assertThrows(IOException.class,()->repository().refresh(uri,previous));status=200;body="not an image".getBytes();
        assertThrows(IOException.class,()->repository().refresh(uri,previous));
        assertArrayEquals(previous.png(),repository().local(uri).png());
    }
    @Test void unchanged200AvoidsUnnecessaryTextureReplacement() throws Exception {
        var previous=repository().refresh(uri,null).tile();assertFalse(repository().refresh(uri,previous).changed());
    }
    @Test void corruptedDiskTileIsIgnoredAndCanBeRepaired() throws Exception {
        repository().refresh(uri,null);var disk=new TileDiskStore(folder);Path file=disk.path(uri);
        byte[] corrupted=Files.readAllBytes(file);corrupted[corrupted.length-1]^=1;Files.write(file,corrupted);
        assertNull(repository().local(uri));repository().refresh(uri,null);assertNotNull(repository().local(uri));
    }
    @Test void sitesWorldsAndLayersHaveSeparateCacheFiles() {
        var disk=new TileDiskStore(folder);Set<Path> paths=new HashSet<>();
        for(String url:List.of("https://a.test/tiles/world/flat/0.png","https://b.test/tiles/world/flat/0.png",
                "https://a.test/tiles/world_nether/flat/0.png","https://a.test/tiles/world/surface/0.png",
                "https://a.test/other/tiles/world/flat/0.png"))paths.add(disk.path(URI.create(url)));
        assertEquals(5,paths.size());assertTrue(paths.stream().allMatch(p->p.normalize().startsWith(folder)));
    }
    @Test void evictionKeepsMostRecentlyUsedTiles() throws Exception {
        var disk=new TileDiskStore(folder,250);byte[] png=image(0xFF001122);
        URI second=uri.resolve("second.png");disk.write(uri,new TileDiskStore.Tile(png,"",0,1));
        Files.setLastModifiedTime(disk.path(uri),java.nio.file.attribute.FileTime.fromMillis(1));
        disk.write(second,new TileDiskStore.Tile(png,"",0,1));disk.prune();
        assertNull(disk.read(uri));assertNotNull(disk.read(second));
    }
    @Test void unwritableCacheStillAllowsOnlineRendering() throws Exception {
        Path notDirectory=folder.resolve("file");Files.writeString(notDirectory,"x");
        var result=new TileRepository(new TileDiskStore(notDirectory)).refresh(uri,null);
        assertTrue(result.changed());assertFalse(result.storageError().isEmpty());assertNotNull(result.tile().png());
    }
}
