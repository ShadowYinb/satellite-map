package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.ui.FullMapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.HashMap;

/** Visual checks of the outline from the interior and both sides of its eastern edge. */
final class BorderSmokeTest {
    static void run(ClientGameTestContext c) throws Exception {
        var saved=c.computeOnClient(mc->new HashMap<>(SatelliteMapClient.INSTANCE.config.fullmapOverlays.layers));
        c.runOnClient(mc->{var mod=SatelliteMapClient.INSTANCE;for(var set:mod.markers.request(mod.model,mod.selection.world()).data().sets())mod.config.fullmapOverlays.set(mod.model.base(),set.id(),set.id().equals("markers"));});
        c.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);c.waitForScreen(FullMapScreen.class);c.waitTicks(10);
        for(var test:new Object[][]{{40.0,"satellite-border-interior"},{77.0,"satellite-border-inside-hover"},{82.0,"satellite-border-outside-hover"}}) {
            double[] cursor=c.computeOnClient(mc->{double s=mc.getWindow().getGuiScale();return new double[]{(mc.screen.width/2.0+(Double)test[0])*s,mc.screen.height/2.0*s};});
            c.getInput().setCursorPos(cursor[0],cursor[1]);c.waitTicks(3);c.takeScreenshot((String)test[1]);
        }
        c.getInput().pressKey(SatelliteMapClient.INSTANCE.openMap);c.waitForScreen(null);
        c.runOnClient(mc->{var settings=SatelliteMapClient.INSTANCE.config.fullmapOverlays;settings.layers.clear();settings.layers.putAll(saved);});
    }
}
