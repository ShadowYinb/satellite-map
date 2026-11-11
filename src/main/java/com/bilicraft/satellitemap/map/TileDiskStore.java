package com.bilicraft.satellitemap.map;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;

/** Atomic, URL-isolated persistent tiles. All methods run on background workers. */
public final class TileDiskStore {
    public record Tile(byte[] png, String etag, long lastModified, long checkedAt) {}
    private static final int MAGIC=0x534D5431, MAX_PNG=20*1024*1024;
    public static final long DEFAULT_LIMIT=512L*1024*1024;
    private final Path root;
    private final long limit;
    private long nextPrune;

    public TileDiskStore(Path root) {this(root,DEFAULT_LIMIT);}
    public TileDiskStore(Path root,long limit) {this.root=root;this.limit=limit;}
    public static String key(URI uri) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(uri.toASCIIString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    public Path path(URI uri) {String hash=key(uri);return root.resolve(hash.substring(0,2)).resolve(hash+".tile");}
    public void scan(java.util.function.Consumer<URI> found) {
        if(!Files.exists(root))return;
        try(var files=Files.walk(root,2)) {
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".tile")).limit(100000).toList()) {
                if(Thread.currentThread().isInterrupted())return;
                try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    if(in.readInt()==MAGIC) {URI uri=URI.create(in.readUTF());if(path(uri).equals(file))found.accept(uri);}
                } catch(IOException | RuntimeException ignored){}
            }
        } catch(IOException ignored){}
    }
    public Tile read(URI uri) {
        Path file=path(uri);
        try {
            if(Files.size(file)>MAX_PNG+131072) return null;
            try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if(in.readInt()!=MAGIC || !in.readUTF().equals(uri.toASCIIString()))return null;
                String etag=in.readUTF();long modified=in.readLong(),checked=in.readLong(),crc=in.readLong();
                int length=in.readInt();if(length<=0 || length>MAX_PNG)return null;
                byte[] png=in.readNBytes(length);CRC32 checksum=new CRC32();checksum.update(png);
                if(png.length!=length || checksum.getValue()!=crc || in.read()!=-1)return null;
                // Access time drives eviction, independently of the HTTP validation time.
                try {Files.setLastModifiedTime(file,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));} catch(IOException ignored){}
                return new Tile(png,etag,modified,checked);
            }
        } catch(IOException | RuntimeException ignored) {return null;}
    }
    public void write(URI uri,Tile tile) throws IOException {
        if(tile.png.length<=0 || tile.png.length>MAX_PNG)throw new IOException("瓦片缓存尺寸无效");
        Path file=path(uri);Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"tile-",".tmp");
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                CRC32 crc=new CRC32();crc.update(tile.png);
                out.writeInt(MAGIC);out.writeUTF(uri.toASCIIString());out.writeUTF(tile.etag);
                out.writeLong(tile.lastModified);out.writeLong(tile.checkedAt);out.writeLong(crc.getValue());
                out.writeInt(tile.png.length);out.write(tile.png);
            }
            try {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
        pruneIfDue();
    }
    private synchronized void pruneIfDue() throws IOException {
        long now=System.currentTimeMillis();if(now<nextPrune)return;nextPrune=now+60000;
        prune();
    }
    public synchronized void prune() throws IOException {
        if(!Files.exists(root))return;
        record FileInfo(Path path,long size,long accessed) {}
        List<FileInfo> files=new ArrayList<>();long total=0;
        try(var paths=Files.walk(root,2)) {
            for(Path p:paths.filter(p->p.getFileName().toString().endsWith(".tile")).toList()) {
                try {long size=Files.size(p);files.add(new FileInfo(p,size,Files.getLastModifiedTime(p).toMillis()));total+=size;}
                catch(NoSuchFileException ignored){}
            }
        }
        files.sort(Comparator.comparingLong(FileInfo::accessed));
        for(FileInfo file:files) {if(total<=limit)break;if(Files.deleteIfExists(file.path))total-=file.size;}
    }
}
