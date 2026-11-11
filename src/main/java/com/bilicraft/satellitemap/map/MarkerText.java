package com.bilicraft.satellitemap.map;

/** Keep the short title separate from the website's hover-only HTML. */
public final class MarkerText {
    public static String text(String raw) {
        if(raw==null)return "";
        String value=raw.replaceAll("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>","")
                .replaceAll("(?i)<br\\s*/?>|</(?:div|p|li|h[1-6])>","\n");
        StringBuilder result=new StringBuilder();
        for(String line:value.split("\\R")){String clean=OverlayModel.plain(line);if(!clean.isBlank()){if(!result.isEmpty())result.append('\n');result.append(clean);}}
        return result.substring(0,Math.min(result.length(),1500));
    }
    public static String name(String raw) {
        String value=text(raw);int end=value.indexOf('\n');if(end>=0)value=value.substring(0,end);
        return value.replaceFirst("(?i)\\s*(?:创建时间|创建日期|创建者|created\\s+(?:at|on|by))\\s*[:：].*$","").trim();
    }
    public static String detail(String raw,String description) {
        String a=text(raw),b=text(description);return b.isBlank() || a.equals(b)?a:a+"\n"+b;
    }
    private MarkerText(){}
}
