package com.bilicraft.satellitemap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import java.util.*;

/** Uses only entities already sent to this client; no server scanning. */
public final class MobRadar {
    public enum Kind {
        HOSTILE(0xFFFF5366),NEUTRAL(0xFFFFCF68),FRIENDLY(0xFFF1F5FC);
        public final int color;Kind(int color){this.color=color;}
    }
    private List<Mob> visible=List.of();private int tick;
    public void tick(Minecraft mc) {
        if(mc.level==null || mc.player==null){visible=List.of();return;}
        if(tick++%5!=0)return;
        List<Mob> next=new ArrayList<>();
        for(Entity e:mc.level.entitiesForRendering())if(e instanceof Mob mob && e.isAlive() && !e.isInvisible() && Math.hypot(e.getX()-mc.player.getX(),e.getZ()-mc.player.getZ())<=256)next.add(mob);
        next.sort(Comparator.comparingDouble(e->e.distanceToSqr(mc.player)));visible=List.copyOf(next.subList(0,Math.min(256,next.size())));
    }
    public List<Mob> visible(){return visible;}
    public static Kind classify(Mob mob) {
        String name=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        boolean neutral=mob instanceof NeutralMob || Set.of("piglin","wolf","polar_bear","llama","trader_llama","dolphin","bee","enderman","iron_golem").contains(name);
        boolean angry=mob.getTarget()!=null || (mob instanceof NeutralMob n && n.isAngry());
        return kind(mob.getType().getCategory()==MobCategory.MONSTER,neutral,angry);
    }
    public static Kind kind(boolean monster,boolean neutral,boolean angry) {
        if(neutral)return angry?Kind.HOSTILE:Kind.NEUTRAL;
        return monster || angry?Kind.HOSTILE:Kind.FRIENDLY;
    }
}
