package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.*;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.DynmapService;
import com.bilicraft.satellitemap.ui.OverlayRenderer;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlayModelTest {
    @TempDir Path folder;
    private OverlayModel parse(String sets){return OverlayModel.parse(JsonParser.parseString("{\"sets\":"+sets+"}").getAsJsonObject());}
    @Test void arbitraryWebsiteGroupsIncludingEmptySetsRemainSelectable() {
        var data=parse("{\"guilds\":{\"label\":\"公会区域\",\"hide\":true},\"warzone\":{\"label\":\"warzone\"},\"new-plugin\":{\"label\":\"新插件\"}}");
        assertEquals(3,data.sets().size());assertTrue(data.sets().getFirst().hidden());assertEquals(0,data.sets().get(1).count());
    }
    @Test void pointIconsAndMarkupLabelsAreParsedWithoutExecutingHtml() {
        var marker=parse("{\"stargate\":{\"markers\":{\"a\":{\"label\":\"<b>星门 &amp; 基地</b>\",\"icon\":\"gate icon\",\"x\":12,\"y\":70,\"z\":-15,\"dim\":\"32x32\"}}}}").sets().getFirst().markers().getFirst();
        assertEquals("星门 & 基地",marker.label());assertEquals(16,marker.size());assertEquals(-15,marker.point().z());
        assertEquals("https://example.test/sub/tiles/_markers_/gate+icon.png",OverlayModel.iconUri(URI.create("https://example.test/sub/tiles/"),marker.icon()).toString().replace("%20","+"));
        assertEquals("hi",OverlayModel.plain("<script>bad()</script><b>§ahi</b>"));
    }
    @Test void residenceTwoCornerAreasBecomeFilledRectangles() {
        var shape=parse("{\"residence.markerset\":{\"areas\":{\"home\":{\"x\":[-10,20],\"z\":[30,50],\"ytop\":100,\"fillcolor\":\"#336699\",\"fillopacity\":0.5}}}}").sets().getFirst().shapes().getFirst();
        assertTrue(shape.closed());assertEquals(4,shape.points().size());assertEquals(100,shape.points().getFirst().y());assertEquals(0x80336699,shape.style().fill());
    }
    @Test void polygonsLinesAndCirclesPreserveTheirGeometry() {
        var data=parse("{\"custom\":{\"areas\":{\"p\":{\"x\":[0,10,0],\"z\":[0,0,10]}},\"lines\":{\"l\":{\"x\":[1,2,3],\"y\":[4,5,6],\"z\":[7,8,9]}},\"circles\":{\"c\":{\"x\":10,\"z\":20,\"xr\":5,\"zr\":8}}}}");
        var shapes=data.sets().getFirst().shapes();assertEquals(3,shapes.size());assertFalse(shapes.get(1).closed());assertEquals(5,shapes.get(1).points().get(1).y());assertEquals(48,shapes.get(2).points().size());
    }
    @Test void malformedItemsDoNotDiscardOtherValidMarkers() {
        var set=parse("{\"markers\":{\"markers\":{\"bad\":{\"x\":\"NaN\"},\"good\":{\"x\":5,\"z\":6}},\"areas\":{\"bad\":{\"x\":[0,1],\"z\":[2]}}}}").sets().getFirst();
        assertEquals(1,set.markers().size());assertEquals("good",set.markers().getFirst().id());assertTrue(set.shapes().isEmpty());
    }
    @Test void perMapLayerOverridesSurviveSaveAndStayIsolatedByWebsite() throws Exception {
        var config=MapConfig.defaults();var set=parse("{\"markers\":{\"hide\":false}}").sets().getFirst();URI a=URI.create("https://a.test/"),b=URI.create("https://b.test/");
        config.minimapOverlays.set(a,set.id(),false);config.fullmapOverlays.players=false;
        Path path=folder.resolve("config.json");config.save(path);var loaded=MapConfig.load(path);
        assertFalse(loaded.minimapOverlays.enabled(a,set));assertTrue(loaded.fullmapOverlays.enabled(a,set));assertTrue(loaded.minimapOverlays.enabled(b,set));assertFalse(loaded.fullmapOverlays.players);
    }
    @Test void nearbyPlayersOverrideWebsiteCoordinatesAndOtherWorldsStayHidden() {
        var web=List.of(new DynmapService.Player("Alice","world",1,64,2),new DynmapService.Player("Other","nether",1,64,2),new DynmapService.Player("Self","world",1,64,2));
        var local=List.of(new DynmapService.Player("ALICE","world",9,70,10));
        var visible=OverlayRenderer.visiblePlayers(web,local,"world","Self");assertEquals(1,visible.size());assertEquals(9,visible.getFirst().x());
    }
    @Test void websiteZoomRangesRespectMinimumAndMaximum() {
        assertTrue(OverlayModel.zoomVisible(0,0,2));assertFalse(OverlayModel.zoomVisible(3,0,2));assertTrue(OverlayModel.zoomVisible(20,-1,-1));
    }
    @Test void geometryClipsLongLinesAndFillsConcavePolygonsInsideViewport() {
        var line=OverlayGeometry.clipLine(new OverlayGeometry.Point(-1e9,5),new OverlayGeometry.Point(1e9,5),0,0,10,10);
        assertNotNull(line);assertEquals(0,line[0],.001);assertEquals(10,line[2],.001);
        var points=List.of(new OverlayGeometry.Point(-5,-5),new OverlayGeometry.Point(10,-5),new OverlayGeometry.Point(10,3),new OverlayGeometry.Point(3,3),new OverlayGeometry.Point(3,10),new OverlayGeometry.Point(-5,10));
        var spans=OverlayGeometry.spans(points,0,0,8,8);assertFalse(spans.isEmpty());
        assertTrue(spans.stream().allMatch(s->s.x0()>=0 && s.x1()<=8 && s.y()>=0 && s.y()<8));
        assertTrue(OverlayGeometry.contains(points,1,7));assertFalse(OverlayGeometry.contains(points,7,7));
    }
}
