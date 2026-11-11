package com.bilicraft.satellitemap.config;

import java.net.URI;
import java.util.Locale;

public class MapProfile {
    public String name = "新地图";
    public String server = "mc.bilicraft.com:25577";
    public String url = "https://map.bilicraft.com/s2/hyperion/#";
    public String world = "";
    public String map = "";
    public String dimension = "";

    public MapProfile copy() {
        MapProfile p = new MapProfile();
        p.name = name; p.server = server; p.url = url;
        p.world = world; p.map = map; p.dimension = dimension;
        return p;
    }

    public URI baseUri() {
        URI u = URI.create(url.trim());
        if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))
                || u.getHost() == null || u.getUserInfo() != null) {
            throw new IllegalArgumentException("请输入有效的 http/https Dynmap 地址");
        }
        String path = u.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (path.endsWith(".html") || path.endsWith(".php")) path = path.substring(0, path.lastIndexOf('/') + 1);
        if (!path.endsWith("/")) path += "/";
        return URI.create(u.getScheme() + "://" + u.getRawAuthority() + path);
    }

    public boolean matches(String address, String currentDimension) {
        boolean addressMatch = false;
        for (String entry : server.split("[,;\\s]+")) {
            if (normalizeAddress(entry).equals(normalizeAddress(address))) addressMatch = true;
        }
        return addressMatch && (dimension.isBlank() || dimension.equals(currentDimension));
    }

    public static String normalizeAddress(String address) {
        String s = address.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "";
        if (s.startsWith("[")) return s.contains("]:") ? s : s + ":25565";
        if (s.chars().filter(c -> c == ':').count() > 1) return "[" + s + "]:25565";
        if (!s.contains(":")) s += ":25565";
        return s;
    }
}
