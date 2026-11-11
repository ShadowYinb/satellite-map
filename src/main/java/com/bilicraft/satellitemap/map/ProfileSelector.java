package com.bilicraft.satellitemap.map;

import com.bilicraft.satellitemap.config.MapProfile;
import com.bilicraft.satellitemap.net.DynmapService;
import java.util.*;

public final class ProfileSelector {
    public record Evidence(MapProfile profile, DynmapModel model, List<DynmapService.Player> players, long observedAt) {}
    public record Selection(MapProfile profile, String world, boolean identified, String reason) {}

    public static Selection select(List<Evidence> candidates, String username, double x, double z,
                                   String dimension, MapProfile previous, boolean auto, MapProfile manual) {
        return select(candidates,username,x,z,dimension,previous,auto,manual,new WorldResolver.History());
    }
    public static Selection select(List<Evidence> candidates,String username,double x,double z,
                                   String dimension,MapProfile previous,boolean auto,MapProfile manual,WorldResolver.History history) {
        if (candidates.isEmpty()) return null;
        List<Selection> matches = new ArrayList<>();
        for (Evidence e : candidates) {
            if (e.model == null || System.currentTimeMillis() - e.observedAt > 20000) continue;
            for (DynmapService.Player p : e.players) {
                if (!p.account().equalsIgnoreCase(username)) continue;
                if (!e.profile.world.isBlank() && !e.profile.world.equals(p.world())) continue;
                if(!WorldResolver.compatible(e.profile,p.world(),dimension,history))continue;
                // A stale web feed or a player on a different backend must not switch maps.
                if (Math.hypot(p.x()-x,p.z()-z) > 128) continue;
                matches.add(new Selection(e.profile,p.world(),true,"网页玩家位置匹配"));
            }
        }
        if (!auto && manual != null) {
            Evidence e = candidates.stream().filter(c -> c.profile == manual).findFirst().orElse(null);
            if (e != null) return fallback(e, matches.stream().filter(s -> s.profile == manual).findFirst().orElse(null),dimension,"手动选择",history);
        }
        int specificity=matches.stream().mapToInt(s->specificity(s.profile)).max().orElse(0);
        matches.removeIf(s->specificity(s.profile)<specificity);
        if (matches.size() == 1) return matches.getFirst();
        if (matches.size() > 1) {
            // Duplicate rules are ambiguous. Keep the current map and expose the ambiguity.
            Selection current = matches.stream().filter(s -> s.profile == previous).findFirst().orElse(matches.getFirst());
            return new Selection(current.profile,current.world,false,"多组同时匹配；可手动切换");
        }
        int priority=candidates.stream().mapToInt(c->c.profile.dimension.isBlank()?0:1).max().orElse(0);
        List<Evidence> eligible=candidates.stream().filter(c->(c.profile.dimension.isBlank()?0:1)==priority).toList();
        List<Evidence> resolved=eligible.stream().filter(c->!WorldResolver.fallback(c.profile,c.model,dimension,history).isEmpty()).toList();
        if(!resolved.isEmpty())eligible=resolved;
        Evidence e=eligible.stream().filter(c->c.profile==previous).findFirst().orElse(eligible.getFirst());
        return fallback(e,null,dimension,"未识别后端；使用当前组，可手动切换",history);
    }

    private static int specificity(MapProfile p) {return (p.dimension.isBlank()?0:2)+(p.world.isBlank()?0:1);}
    private static Selection fallback(Evidence e, Selection known, String dimension, String reason,WorldResolver.History history) {
        if (known != null) return new Selection(e.profile,known.world,known.identified,reason);
        if (!e.profile.world.isBlank()) return new Selection(e.profile,WorldResolver.fallback(e.profile,e.model,dimension,history),false,reason);
        String world=WorldResolver.fallback(e.profile,e.model,dimension,history);
        return new Selection(e.profile,world,false,world.isEmpty() && e.model!=null?"此维度未匹配地图；请设置网页世界名":reason);
    }
    private ProfileSelector() {}
}
