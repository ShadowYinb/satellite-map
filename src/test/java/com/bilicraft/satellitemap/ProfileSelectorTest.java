package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.*;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.DynmapService;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileSelectorTest {
    private final DynmapModel.Layer layer=new DynmapModel.Layer("flat","flat","jpg",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
    private final DynmapModel model=new DynmapModel(URI.create("https://example.com/"),URI.create("https://example.com/tiles/"),"",List.of(new DynmapModel.World("Paralon",List.of(layer)),new DynmapModel.World("HyperionVoid",List.of(layer))));
    private ProfileSelector.Evidence evidence(MapProfile p,String world,double x) {
        return new ProfileSelector.Evidence(p,model,List.of(new DynmapService.Player("Tester",world,x,64,20)),System.currentTimeMillis());
    }
    @Test void addressesNormalizePortsCaseAliasesAndIpv6() {
        MapProfile p=new MapProfile();p.server="EXAMPLE.com, [::1]";
        assertTrue(p.matches("example.com:25565","minecraft:overworld"));
        assertTrue(p.matches("[::1]:25565","minecraft:overworld"));
        assertFalse(p.matches("example.com:25577","minecraft:overworld"));
        p.dimension="minecraft:the_nether";
        assertFalse(p.matches("example.com","minecraft:overworld"));
    }
    @Test void proxySwitchUsesNearbyPlayerOnSecondSite() {
        MapProfile a=new MapProfile(),b=new MapProfile();
        var result=ProfileSelector.select(List.of(evidence(a,"Paralon",5000),evidence(b,"HyperionVoid",11)),"Tester",10,20,"minecraft:overworld",a,true,null);
        assertSame(b,result.profile());assertEquals("HyperionVoid",result.world());assertTrue(result.identified());
    }
    @Test void explicitWorldSeparatesTwoProfilesForSameSite() {
        MapProfile a=new MapProfile(),b=new MapProfile();a.world="Paralon";b.world="HyperionVoid";
        var result=ProfileSelector.select(List.of(evidence(a,"HyperionVoid",10),evidence(b,"HyperionVoid",10)),"Tester",10,20,"minecraft:overworld",a,true,null);
        assertSame(b,result.profile());
    }
    @Test void manualSelectionOverridesWebDetection() {
        MapProfile a=new MapProfile(),b=new MapProfile();a.world="Paralon";
        var result=ProfileSelector.select(List.of(evidence(a,"Paralon",5000),evidence(b,"HyperionVoid",10)),"Tester",10,20,"minecraft:overworld",b,false,a);
        assertSame(a,result.profile());assertEquals("Paralon",result.world());
    }
    @Test void hiddenOrStalePlayersDoNotSwitchCurrentProfile() {
        MapProfile a=new MapProfile(),b=new MapProfile();
        var stale=new ProfileSelector.Evidence(b,model,List.of(new DynmapService.Player("Tester","HyperionVoid",10,64,20)),0);
        var result=ProfileSelector.select(List.of(new ProfileSelector.Evidence(a,model,List.of(),0),stale),"Tester",10,20,"minecraft:overworld",a,true,null);
        assertSame(a,result.profile());assertFalse(result.identified());
    }
    @Test void ambiguousMatchesAreReportedAndKeepCurrentProfile() {
        MapProfile a=new MapProfile(),b=new MapProfile();
        var result=ProfileSelector.select(List.of(evidence(a,"Paralon",10),evidence(b,"HyperionVoid",10)),"Tester",10,20,"minecraft:overworld",b,true,null);
        assertSame(b,result.profile());assertFalse(result.identified());assertTrue(result.reason().contains("多组"));
    }
    @Test void netherDoesNotSilentlyShowFirstOverworld() {
        MapProfile p=new MapProfile();
        var result=ProfileSelector.select(List.of(new ProfileSelector.Evidence(p,model,List.of(),0)),"Tester",0,0,"minecraft:the_nether",null,true,null);
        assertEquals("",result.world());
    }
    @Test void urlNormalizationPreservesSubdirectoryAndRemovesHash() {
        MapProfile p=new MapProfile();assertEquals("https://map.bilicraft.com/s2/hyperion/",p.baseUri().toString());
        p.url="https://example.com/dynmap/index.html#world;flat";
        assertEquals("https://example.com/dynmap/",p.baseUri().toString());
        p.url="file:///tmp/a";assertThrows(IllegalArgumentException.class,p::baseUri);
    }
}
