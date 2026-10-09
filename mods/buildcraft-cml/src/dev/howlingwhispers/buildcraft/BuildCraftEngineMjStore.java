package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.buildcraft.BuildCraftEngineStore.Engine;
import dev.howlingwhispers.buildcraft.BuildCraftRedstoneEngine.Snapshot;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists original BCCE redstone-engine MJ, heat and piston-stroke state in the
 * existing world's H.O.W.L. namespace. The separate engine-position index
 * remains backwards compatible. This is an intermediate bridge until native
 * Snapshot 3 BlockEntity NBT owns the same fields.
 *
 * MPL-2.0: derived engine state layout, see BuildCraftRedstoneEngine.
 */
public final class BuildCraftEngineMjStore {
    private static final int MAGIC = 0x42434d4a; // BCMJ
    private static final int VERSION = 1;
    private static final int HASH_BYTES = 32;
    private static final int MAX_BYTES = 8*1024*1024;
    private static final int MAX_ENGINES = 100_000;
    private static final String NAME = "engine-mj.v1.dat";

    private BuildCraftEngineMjStore() {}

    private static Path file(Path worldRoot, boolean create) throws IOException {
        if (worldRoot == null) throw new IOException("World save path is required");
        Path root = worldRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root))
            throw new IOException("Invalid Minecraft world root");
        Path cml = root.resolve("cml");
        Path directory = cml.resolve("buildcraft");
        for (Path entry : List.of(cml,directory)) {
            if (Files.isSymbolicLink(entry))
                throw new IOException("Refusing symlink in BCCE MJ save path");
            if (Files.exists(entry,LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Invalid BCCE MJ save directory");
        }
        if (create) Files.createDirectories(directory);
        Path target = directory.resolve(NAME);
        if (Files.isSymbolicLink(target))
            throw new IOException("Refusing symlink BCCE MJ file");
        return target;
    }

    public static Map<Engine, Snapshot> load(Path worldRoot) throws IOException {
        Path target = file(worldRoot,false);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        long size = Files.size(target);
        if (size < 12 + HASH_BYTES || size > MAX_BYTES)
            throw new IOException("Invalid BCCE MJ save size");
        byte[] data = Files.readAllBytes(target);
        int bodySize = data.length-HASH_BYTES;
        byte[] body = Arrays.copyOf(data, bodySize);
        if (!MessageDigest.isEqual(sha256(body),
                Arrays.copyOfRange(data,bodySize,data.length)))
            throw new IOException("BCCE MJ checksum mismatch: original save preserved");
        try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt()!=MAGIC || in.readInt()!=VERSION)
                throw new IOException("Unrecognized BCCE MJ save version");
            int count=in.readInt();
            if (count<0 || count>MAX_ENGINES)
                throw new IOException("Invalid BCCE MJ engine count");
            Map<Engine,Snapshot> result=new HashMap<>();
            for (int i=0;i<count;i++) {
                try {
                    Engine engine=new Engine(in.readUTF(),
                            new CodaBlockPos(in.readInt(),in.readInt(),in.readInt()));
                    Snapshot state=new Snapshot(in.readDouble(),in.readLong(),
                            in.readFloat(),in.readInt(),in.readBoolean(),in.readBoolean());
                    if(result.putIfAbsent(engine,state)!=null)
                        throw new IOException("Duplicate BCCE MJ state");
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid BCCE MJ save engine state",ex);
                }
            }
            if(in.available()!=0)throw new IOException("Trailing BCCE MJ save data");
            return Map.copyOf(result);
        }
    }

    public static void save(Path worldRoot,Map<Engine,Snapshot> snapshots) throws IOException {
        if(snapshots==null || snapshots.size()>MAX_ENGINES)
            throw new IOException("Invalid BCCE MJ save count");
        List<Map.Entry<Engine,Snapshot>> sorted=new ArrayList<>(snapshots.entrySet());
        sorted.sort(Comparator.comparing((Map.Entry<Engine,Snapshot> e)->e.getKey().dimension())
                .thenComparingInt(e->e.getKey().position().x())
                .thenComparingInt(e->e.getKey().position().y())
                .thenComparingInt(e->e.getKey().position().z()));
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(sorted.size());
            for(var item:sorted) {
                Engine engine=item.getKey();
                Snapshot state=item.getValue();
                out.writeUTF(engine.dimension());
                out.writeInt(engine.position().x());
                out.writeInt(engine.position().y());
                out.writeInt(engine.position().z());
                out.writeDouble(state.heat());
                out.writeLong(state.powerMicroMj());
                out.writeFloat(state.progress());
                out.writeInt(state.progressPart());
                out.writeBoolean(state.powered());
                out.writeBoolean(state.pumping());
                if(buffer.size()+HASH_BYTES>MAX_BYTES)
                    throw new IOException("BCCE MJ save exceeds size limit");
            }
        }
        byte[] payload=buffer.toByteArray();
        if(payload.length+HASH_BYTES>MAX_BYTES)
            throw new IOException("BCCE MJ save exceeds size limit");
        Path target=file(worldRoot,true);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))load(worldRoot);
        Path temporary=Files.createTempFile(target.getParent(),".engine-mj-",".tmp");
        try{
            try(var out=Files.newOutputStream(temporary)){
                out.write(payload);out.write(sha256(payload));
            }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
