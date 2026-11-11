package com.bilicraft.satellitemap.map;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public record DynmapModel(URI base, URI tiles, String updateTemplate, List<World> worlds,URI markers) {
    public DynmapModel(URI base,URI tiles,String updateTemplate,List<World> worlds) {this(base,tiles,updateTemplate,worlds,tiles);}
    public URI markerRoot() {return markers==null?tiles:markers;}
    public DynmapModel withMarkers(URI root) {return new DynmapModel(base,tiles,updateTemplate,worlds,root);}
    public record World(String name, List<Layer> layers) {}
    public record Point(double x, double y) {}
    public record Layer(String name, String prefix, String format, int tileSize,
                        int maxZoomOut, int maxZoomIn, double[] matrix, boolean nightAndDay) {
        public int displayMaxZoomOut() {return Math.min(16,maxZoomOut+4);}
        public Point project(double x, double y, double z) {
            return new Point(matrix[0]*x + matrix[1]*y + matrix[2]*z,
                    tileSize - (matrix[3]*x + matrix[4]*y + matrix[5]*z));
        }
        public Point unproject(double u, double v, double height) {
            double a = u - matrix[1]*height, b = tileSize - v - matrix[4]*height;
            double determinant = matrix[0]*matrix[5] - matrix[2]*matrix[3];
            if (Math.abs(determinant) < 1e-10) return new Point(0, 0);
            return new Point((a*matrix[5]-matrix[2]*b)/determinant,
                    (matrix[0]*b-a*matrix[3])/determinant);
        }
        // col/row are tile indices in the projected, downward-positive screen grid.
        public String tilePath(String world, int zoomOut, int col, int row) {
            int step = 1 << zoomOut;
            int tx = Math.multiplyExact(col, step), ty = Math.multiplyExact(-row, step);
            String zoom = zoomOut == 0 ? "" : "z".repeat(zoomOut) + "_";
            return encode(world) + "/" + encode(prefix) + "/" + (tx >> 5) + "_" + (ty >> 5)
                    + "/" + zoom + tx + "_" + ty + "." + format;
        }
        public double scale(int zoomOut) { return Math.scalb(1.0, -zoomOut); }
    }

    public static DynmapModel parse(URI base, URI tiles, String update, JsonObject json) {
        List<World> worlds = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray("worlds")) {
            JsonObject w = value.getAsJsonObject(); List<Layer> layers = new ArrayList<>();
            for (JsonElement map : w.getAsJsonArray("maps")) {
                JsonObject m = map.getAsJsonObject();
                if (!m.has("worldtomap") || !m.has("prefix")) continue;
                double[] matrix = new double[9]; JsonArray a = m.getAsJsonArray("worldtomap");
                if (a.size() != 9) continue;
                for (int i=0; i<9; i++) matrix[i] = a.get(i).getAsDouble();
                String format = string(m, "image-format", "png").toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpg", "jpeg").contains(format)) continue;
                layers.add(new Layer(string(m,"name","flat"), m.get("prefix").getAsString(), format,
                        128 << Math.clamp(integer(m,"tilescale",0),0,4),
                        Math.clamp(integer(m,"mapzoomout",5),0,12),
                        Math.clamp(integer(m,"mapzoomin",4),0,6), matrix,
                        m.has("nightandday") && m.get("nightandday").getAsBoolean()));
            }
            if (!layers.isEmpty()) worlds.add(new World(w.get("name").getAsString(), List.copyOf(layers)));
        }
        if (worlds.isEmpty()) throw new IllegalArgumentException("该地址没有可用的 Dynmap HD 地图");
        return new DynmapModel(base, tiles, update, List.copyOf(worlds));
    }

    public World world(String requested) {
        return worlds.stream().filter(w -> w.name.equals(requested)).findFirst().orElse(null);
    }
    public static Layer layer(World w, String requested) {
        if (w == null) return null;
        if (!requested.isBlank()) return w.layers.stream().filter(m -> m.name.equals(requested)).findFirst().orElse(null);
        return w.layers.stream().filter(m -> m.name.equals("flat")).findFirst().orElse(w.layers.getFirst());
    }
    public static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+","%20"); }
    public static String string(JsonObject o,String key,String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
    private static int integer(JsonObject o,String key,int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
}
