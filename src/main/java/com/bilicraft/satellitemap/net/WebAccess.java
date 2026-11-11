package com.bilicraft.satellitemap.net;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

public final class WebAccess {
    private static final HttpClient CLIENT=createClient();
    private static HttpClient createClient(){
        try{return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_2).build();}
        catch(RuntimeException e){return null;} // Some Windows JBR installations cannot initialize NIO selectors.
    }
    private WebAccess() {}
    public record Response(byte[] bytes,String etag,long lastModified,boolean notModified) {}
    public static byte[] get(URI uri,int limit) throws IOException {return conditional(uri,limit,"",0).bytes();}
    public static Response conditional(URI uri,int limit,String etag,long lastModified) throws IOException {return request(uri,limit,etag,lastModified,4);}
    private static Response request(URI uri,int limit,String etag,long modified,int redirects) throws IOException {
        if(uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT)))throw new IOException("仅支持 HTTP / HTTPS");
        if(CLIENT==null)return fallback(uri,limit,etag,modified,redirects);
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("User-Agent","SatelliteMap/1.3 (Minecraft Fabric client)")
                .header("Accept","application/json,image/*,text/javascript,*/*").header("Accept-Encoding","gzip").header("Cache-Control","no-cache");
        if(!etag.isEmpty())builder.header("If-None-Match",etag);
        if(modified>0)builder.header("If-Modified-Since",DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(modified).atZone(ZoneId.of("GMT"))));
        HttpResponse<byte[]> response;
        try {response=CLIENT.send(builder.GET().build(),info->new LimitedBody(limit+65536));}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("请求已取消",e);}
        int status=response.statusCode();
        if(Set.of(301,302,303,307,308).contains(status)) {
            var location=response.headers().firstValue("Location").orElse(null);
            if(redirects==0 || location==null)throw new IOException("重定向过多或无效");
            return request(uri.resolve(location),limit,etag,modified,redirects-1);
        }
        String tag=response.headers().firstValue("ETag").orElse(status==304?etag:"");long time=0;
        try {time=ZonedDateTime.parse(response.headers().firstValue("Last-Modified").orElse(""),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}catch(RuntimeException ignored){}
        if(status==304)return new Response(null,tag,time>0?time:modified,true);
        if(status!=200)throw new IOException("HTTP "+status+" · "+uri.getPath());
        byte[] body=response.body();
        if(response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
            try(var input=new GZIPInputStream(new ByteArrayInputStream(body))){body=input.readNBytes(limit+1);}
        }
        if(body.length>limit)throw new IOException("响应过大");return new Response(body,tag,time,false);
    }
    private static Response fallback(URI uri,int limit,String etag,long modified,int redirects) throws IOException {
        var c=(HttpURLConnection)uri.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","SatelliteMap/1.3");c.setRequestProperty("Accept-Encoding","gzip");
        if(!etag.isEmpty())c.setRequestProperty("If-None-Match",etag);if(modified>0)c.setIfModifiedSince(modified);
        boolean consumed=false;
        try {
            int status=c.getResponseCode();
            if(Set.of(301,302,303,307,308).contains(status)) {
                String location=c.getHeaderField("Location");if(redirects==0 || location==null)throw new IOException("重定向过多或无效");
                return request(uri.resolve(location),limit,etag,modified,redirects-1);
            }
            String tag=Objects.toString(c.getHeaderField("ETag"),status==304?etag:"");long time=c.getLastModified();
            if(status==304){consumed=true;return new Response(null,tag,time>0?time:modified,true);}
            if(status!=200)throw new IOException("HTTP "+status+" · "+uri.getPath());
            if(c.getContentLengthLong()>limit+65536)throw new IOException("响应过大");
            try(InputStream raw=c.getInputStream();InputStream input="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw) {
                byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("响应过大");consumed=true;return new Response(bytes,tag,time,false);
            }
        }finally{if(!consumed)c.disconnect();}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(Long.MAX_VALUE);}
        @Override public void onNext(List<ByteBuffer> parts){
            for(var part:parts){if((long)buffer.size()+part.remaining()>limit){subscription.cancel();result.completeExceptionally(new IOException("响应过大"));return;}
                byte[] bytes=new byte[part.remaining()];part.get(bytes);buffer.writeBytes(bytes);}
        }
        @Override public void onError(Throwable error){result.completeExceptionally(error);}
        @Override public void onComplete(){result.complete(buffer.toByteArray());}
    }
    public static String text(URI uri) throws IOException{return new String(get(uri,4*1024*1024),StandardCharsets.UTF_8);}
    public static JsonObject json(URI uri) throws IOException {
        try{return JsonParser.parseString(text(uri)).getAsJsonObject();}catch(RuntimeException e){throw new IOException("不是有效的 Dynmap JSON: "+uri.getPath(),e);}
    }
}
