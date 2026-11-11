package com.bilicraft.satellitemap.map;

import com.bilicraft.satellitemap.net.WebAccess;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.*;

/** HTTP revalidation and disk persistence, independent of Minecraft's GPU. */
public final class TileRepository {
    public record Refresh(TileDiskStore.Tile tile,boolean changed,String storageError) {}
    private final TileDiskStore disk;
    public TileRepository(TileDiskStore disk) {this.disk=disk;}
    public TileDiskStore.Tile local(URI uri) {return disk.read(uri);}
    public Refresh refresh(URI uri,TileDiskStore.Tile previous) throws IOException {
        var response=WebAccess.conditional(uri,4*1024*1024,previous==null?"":previous.etag(),previous==null?0:previous.lastModified());
        if(response.notModified() && previous==null)throw new IOException("网站返回 304，但没有本地瓦片");
        byte[] png=response.notModified()?previous.png():png(response.bytes());
        boolean changed=previous==null || !Arrays.equals(previous.png(),png);
        var current=new TileDiskStore.Tile(png,response.etag(),response.lastModified(),System.currentTimeMillis());
        String storageError="";
        try {disk.write(uri,current);} catch(IOException e) {storageError="本地缓存写入失败: "+e.getMessage();}
        return new Refresh(current,changed,storageError);
    }
    private static byte[] png(byte[] bytes) throws IOException {
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("不支持的图片格式（支持 PNG/JPG）");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input);
                if(reader.getWidth(0)>2048 || reader.getHeight(0)>2048)throw new IOException("瓦片尺寸过大");
                // PNG needs validation, but no second compression pass.
                if(reader.getFormatName().equalsIgnoreCase("png")) {reader.read(0);return bytes;}
                BufferedImage image=reader.read(0);ByteArrayOutputStream output=new ByteArrayOutputStream();
                ImageIO.write(image,"png",output);return output.toByteArray();
            } finally {reader.dispose();}
        }
    }
}
