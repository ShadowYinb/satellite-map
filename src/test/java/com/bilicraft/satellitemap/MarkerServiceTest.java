package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.MarkerService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkerServiceTest {
    @TempDir Path folder;
    private DynmapModel model(URI base){return new DynmapModel(base,base.resolve("tiles/"),"",List.of());}
    private MarkerService.Snapshot waitFor(MarkerService service,DynmapModel model,java.util.function.Predicate<MarkerService.Snapshot> ready) throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline) {var snapshot=service.request(model,"world");if(ready.test(snapshot))return snapshot;Thread.sleep(20);}
        throw new AssertionError("Marker request timed out");
    }
    @Test void markerLayersSurviveWebsiteFailureAndProcessRestart() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        server.createContext("/tiles/_markers_/marker_world.json",exchange->{byte[] body="{\"sets\":{\"stargate\":{\"label\":\"星门\"}}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}exchange.close();});
        server.start();
        try(var first=new MarkerService(folder)) {assertEquals("星门",waitFor(first,model(base),s->!s.data().sets().isEmpty() && Files.exists(folder.resolve(TileDiskStore.key(MarkerService.endpoint(model(base),"world"))+".json"))).data().sets().getFirst().label());}
        finally{server.stop(0);}
        try(var restarted=new MarkerService(folder)) {var snapshot=waitFor(restarted,model(base),s->!s.data().sets().isEmpty());assertEquals("stargate",snapshot.data().sets().getFirst().id());}
    }
    @Test void newWebsiteDataReplacesCachedLayersIncludingDeletions() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");var model=model(base);
        Files.writeString(folder.resolve(TileDiskStore.key(MarkerService.endpoint(model,"world"))+".json"),"{\"sets\":{\"old\":{}}}");
        server.createContext("/tiles/_markers_/marker_world.json",exchange->{byte[] body="{\"sets\":{\"new\":{}}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}exchange.close();});server.start();
        try(var service=new MarkerService(folder)) {
            var snapshot=waitFor(service,model,s->s.data().sets().stream().anyMatch(set->set.id().equals("new")));
            assertEquals(1,snapshot.data().sets().size());
        }finally{server.stop(0);}
    }
}
