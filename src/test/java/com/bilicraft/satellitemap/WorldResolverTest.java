package com.bilicraft.satellitemap;

import com.bilicraft.satellitemap.config.MapProfile;
import com.bilicraft.satellitemap.map.*;
import com.bilicraft.satellitemap.net.DynmapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldResolverTest {
    @TempDir Path folder;
    private final MapProfile profile=new MapProfile();
    private final WorldResolver.History history=new WorldResolver.History();
    private DynmapModel model(String... names) {
        var layer=new DynmapModel.Layer("flat","flat","png",128,5,4,new double[]{4,0,0,0,0,-4,0,1,0},false);
        return new DynmapModel(profile.baseUri(),profile.baseUri().resolve("tiles/"),"standalone/dynmap_{world}.json",
                Arrays.stream(names).map(n->new DynmapModel.World(n,List.of(layer))).toList());
    }
    @Test void automaticNetherAndEndResolveNonDefaultWorldPrefixes() {
        var model=model("Paralon","Paralon_nether","Paralon_the_end","artist");
        assertEquals("Paralon_nether",WorldResolver.fallback(profile,model,"minecraft:the_nether",history));
        assertEquals("Paralon_the_end",WorldResolver.fallback(profile,model,"minecraft:the_end",history));
        assertEquals("minecraft:the_nether",WorldResolver.inferredDimension("DIM-1"));
        assertEquals("minecraft:the_end",WorldResolver.inferredDimension("DIM1"));
        assertNull(WorldResolver.inferredDimension("HyperionVoid"));
    }
    @Test void customDimensionUsesExactNames() {
        assertEquals("moon",WorldResolver.fallback(profile,model("Paralon","moon"),"space:moon",history));
    }
    @Test void rememberedSecondaryOverworldSurvivesHiddenPlayerFeed() {
        var model=model("Paralon","artist");history.remember(profile,"artist","minecraft:overworld");
        assertEquals("artist",WorldResolver.fallback(profile,model,"minecraft:overworld",history));
    }
    @Test void previousOverworldPlayerCannotReappearInNetherDuringFeedDelay() {
        var model=model("Paralon","Paralon_nether");history.remember(profile,"Paralon","minecraft:overworld");
        var evidence=new ProfileSelector.Evidence(profile,model,List.of(new DynmapService.Player("Tester","Paralon",10,64,20)),System.currentTimeMillis());
        var result=ProfileSelector.select(List.of(evidence),"Tester",10,20,"minecraft:the_nether",profile,true,null,history);
        assertEquals("Paralon_nether",result.world());assertFalse(result.identified());
    }
    @Test void exactDimensionRuleHasPriorityOverGenericProfile() {
        var specific=profile.copy();specific.dimension="minecraft:the_nether";specific.world="Hell";
        var model=model("Paralon","Hell");
        var result=ProfileSelector.select(List.of(new ProfileSelector.Evidence(profile,model,List.of(),0),
                new ProfileSelector.Evidence(specific,model,List.of(),0)),"Tester",0,0,"minecraft:the_nether",profile,true,null,history);
        assertSame(specific,result.profile());assertEquals("Hell",result.world());
    }
    @Test void overworldOnlyRuleDoesNotBlockGenericNetherMap() {
        var pinned=profile.copy();pinned.world="Paralon";
        var model=model("Paralon","world_nether");
        var result=ProfileSelector.select(List.of(new ProfileSelector.Evidence(pinned,model,List.of(),0),
                new ProfileSelector.Evidence(profile,model,List.of(),0)),"Tester",0,0,"minecraft:the_nether",pinned,true,null,history);
        assertSame(profile,result.profile());assertEquals("world_nether",result.world());
    }
    @Test void multipleNetherWorldsRequirePublicPlayerEvidence() {
        assertEquals("",WorldResolver.fallback(profile,model("a_nether","b_nether"),"minecraft:the_nether",history));
    }
    @Test void publicPlayerEvidenceSwitchesBetweenSecondaryOverworlds() {
        var model=model("Paralon","artist","artvoid");
        var evidence=new ProfileSelector.Evidence(profile,model,List.of(new DynmapService.Player("Tester","artist",10,64,20)),System.currentTimeMillis());
        var result=ProfileSelector.select(List.of(evidence),"Tester",10,20,"minecraft:overworld",profile,true,null,history);
        assertEquals("artist",result.world());assertTrue(result.identified());
    }
    @Test void metadataSurvivesRestartForOfflineTileProjection() throws Exception {
        var disk=new ModelDiskStore(folder);disk.write(model("Paralon","Paralon_nether"));
        var loaded=new ModelDiskStore(folder).read(profile.baseUri());assertNotNull(loaded);
        assertEquals("Paralon_nether",loaded.worlds().get(1).name());
        assertEquals(4,loaded.world("Paralon").layers().getFirst().matrix()[0]);
        assertNull(disk.read(URI.create("https://another.test/")));
    }
}
