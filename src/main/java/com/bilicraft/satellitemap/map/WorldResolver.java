package com.bilicraft.satellitemap.map;

import com.bilicraft.satellitemap.config.MapProfile;
import java.util.*;

public final class WorldResolver {
    /** Learned only from uniquely identified players; reused across dimension transitions. */
    public static final class History {
        private final Map<String,String> dimensions=new HashMap<>(),preferred=new HashMap<>();
        private String site(MapProfile p) {return p.baseUri().toString();}
        public void remember(MapProfile p,String world,String dimension) {
            dimensions.put(site(p)+"|"+world,dimension);preferred.put(site(p)+"|"+dimension,world);
        }
        public String dimension(MapProfile p,String world) {return dimensions.get(site(p)+"|"+world);}
        public String preferred(MapProfile p,String dimension) {return preferred.get(site(p)+"|"+dimension);}
        public void clear() {dimensions.clear();preferred.clear();}
    }
    public static String inferredDimension(String world) {
        String name=world.toLowerCase(Locale.ROOT);
        if(name.equals("dim-1") || name.matches(".*(?:^|[_:/.-])(?:the_)?nether(?:$|[_:/.-]).*"))return "minecraft:the_nether";
        if(name.equals("dim1") || name.matches(".*(?:^|[_:/.-])(?:the_)?end(?:$|[_:/.-]).*"))return "minecraft:the_end";
        if(name.equals("world") || name.equals("overworld") || name.endsWith(":overworld"))return "minecraft:overworld";
        return null;
    }
    public static boolean compatible(MapProfile p,String world,String dimension,History history) {
        if(!p.dimension.isBlank())return p.dimension.equals(dimension);
        String known=history.dimension(p,world);
        if(known==null)known=inferredDimension(world);
        return known==null || known.equals(dimension);
    }
    public static String fallback(MapProfile p,DynmapModel model,String dimension,History history) {
        if(!p.world.isBlank()) {
            boolean established=!p.dimension.isBlank() || history.dimension(p,p.world)!=null || inferredDimension(p.world)!=null
                    || dimension.equals("minecraft:overworld") || p.world.equals(dimension) || p.world.equals(dimension.substring(dimension.indexOf(':')+1));
            return established && compatible(p,p.world,dimension,history)?p.world:"";
        }
        if(model==null)return "";
        String remembered=history.preferred(p,dimension);
        if(remembered!=null && model.world(remembered)!=null && compatible(p,remembered,dimension,history))return remembered;
        String path=dimension.substring(dimension.indexOf(':')+1);
        // Exact custom dimension names have priority over name conventions.
        for(String name:List.of(dimension,path))if(model.world(name)!=null && compatible(p,name,dimension,history))return name;
        List<String> typed=model.worlds().stream().map(DynmapModel.World::name)
                .filter(w->dimension.equals(inferredDimension(w)) && compatible(p,w,dimension,history)).toList();
        if(typed.size()==1)return typed.getFirst();
        // Multiple Overworld instances need the public player feed or an explicit rule.
        if(dimension.equals("minecraft:overworld") && typed.isEmpty())return model.worlds().stream()
                .map(DynmapModel.World::name).filter(w->compatible(p,w,dimension,history) && inferredDimension(w)==null)
                .findFirst().orElse("");
        return "";
    }
    private WorldResolver() {}
}
