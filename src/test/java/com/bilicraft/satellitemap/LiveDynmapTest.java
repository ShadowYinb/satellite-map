package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.*;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
class LiveDynmapTest {
    @Test void bilicraftMetadataAndRealSatelliteTiles() throws Exception {
        DynmapModel model=DynmapService.discover(URI.create("https://map.bilicraft.com/s2/hyperion/"));
        assertNotNull(model.world("Paralon"));assertNotNull(model.world("HyperionVoid"));
        DynmapModel.Layer layer=DynmapModel.layer(model.world("Paralon"),"flat");
        assertEquals("jpg",layer.format());
        var update=WebAccess.json(model.base().resolve(model.updateTemplate().replace("{world}","Paralon").replace("{timestamp}",Long.toString(System.currentTimeMillis()))));
        assertTrue(update.has("players"));
        // Spawn at (4117, -591), matching the supplied site's world configuration.
        var projected=layer.project(4117,69,-591);int zoom=2;
        int col=(int)Math.floor(projected.x()*layer.scale(zoom)/layer.tileSize());
        int row=(int)Math.floor(projected.y()*layer.scale(zoom)/layer.tileSize());
        BufferedImage preview=new BufferedImage(384,384,BufferedImage.TYPE_INT_RGB);
        var graphics=preview.createGraphics();
        try {
            for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++) {
                var uri=model.tiles().resolve(layer.tilePath("Paralon",zoom,col+xx,row+yy));
                BufferedImage image=ImageIO.read(new ByteArrayInputStream(WebAccess.get(uri,4*1024*1024)));
                assertNotNull(image);assertEquals(128,image.getWidth());
                graphics.drawImage(image,(xx+1)*128,(yy+1)*128,null);
            }
        }finally{graphics.dispose();}
        Path out=Path.of("build/reports/live-map-preview.png");Files.createDirectories(out.getParent());ImageIO.write(preview,"png",out.toFile());
    }
}
