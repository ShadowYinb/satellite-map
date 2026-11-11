package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.*;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
class LiveOverlayTest {
    @Test void bilicraftPublicLayersAndOneWebsiteIconAreReadable() throws Exception {
        var model=DynmapService.discover(URI.create("https://map.bilicraft.com/s2/hyperion/"));
        var data=OverlayModel.parse(WebAccess.json(MarkerService.endpoint(model,"Paralon")));
        assertFalse(data.sets().isEmpty());assertTrue(data.sets().stream().anyMatch(set->set.id().equals("stargate")));
        var marker=data.sets().stream().flatMap(set->set.markers().stream()).findFirst().orElseThrow();
        var image=ImageIO.read(new ByteArrayInputStream(WebAccess.get(OverlayModel.iconUri(model.markerRoot(),marker.icon()),1024*1024)));
        assertNotNull(image);assertTrue(image.getWidth()>0 && image.getWidth()<=2048);
    }
}
