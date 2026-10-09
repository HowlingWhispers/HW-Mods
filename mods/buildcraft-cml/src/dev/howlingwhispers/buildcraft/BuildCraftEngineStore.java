package dev.howlingwhispers.buildcraft;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Temporary, lossless discovery index for native engine blocks, scoped to the
 * authoritative Minecraft save. This is NOT a substitute for original BCCE
 * block-entity NBT / MJ state. It lets experimental native engines resume after
 * reopening a world, without storing state in global user configuration.
 */
public final class BuildCraftEngineStore {
    private static final int MAGIC = 0x4243454e; // BCEN
    private static final int VERSION = 1;
    private static final int DIGEST_LENGTH = 32;
    private static final int MAX_ENGINES = 100_000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String FILE = "engines.v1.dat";

    public record Engine(String dimension, CodaBlockPos position) {
        public Engine {
            if (dimension == null || dimension.length() > 150
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid engine dimension");
            if (position == null || Math.abs((long)position.x()) > 30_000_000
                    || Math.abs((long)position.z()) > 30_000_000
                    || Math.abs((long)position.y()) > 4_096)
                throw new IllegalArgumentException("Invalid engine position");
        }
    }

    private BuildCraftEngineStore() {}

    private static Path file(Path worldRoot, boolean create) throws IOException {
        if (worldRoot == null) throw new IOException("World save root is unavailable");
        Path root = worldRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("World save root is invalid or a symbolic link");
        Path one = root.resolve("cml");
        Path parent = one.resolve("buildcraft");
        // Do not follow world-local symlinks when saving or reading mod data.
        for (Path component : List.of(one, parent)) {
            if (Files.isSymbolicLink(component))
                throw new IOException("Refusing symlink in BuildCraft save path");
            if (Files.exists(component, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(component, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("BuildCraft save path is not a directory");
        }
        if (create) Files.createDirectories(parent);
        Path path = parent.resolve(FILE);
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink BuildCraft save");
        return path;
    }

    public static Set<Engine> load(Path worldRoot) throws IOException {
        Path path = file(worldRoot, false);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Set.of();
        long size = Files.size(path);
        if (size < 12 + DIGEST_LENGTH || size > MAX_BYTES)
            throw new IOException("Invalid BuildCraft engine save size");
        byte[] bytes = Files.readAllBytes(path);
        byte[] payload = Arrays.copyOf(bytes, bytes.length - DIGEST_LENGTH);
        byte[] hash = Arrays.copyOfRange(bytes, payload.length, bytes.length);
        if (!MessageDigest.isEqual(sha256(payload), hash))
            throw new IOException("BuildCraft engine save checksum mismatch; not overwriting");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
                throw new IOException("Unsupported BuildCraft engine save format");
            int count = in.readInt();
            if (count < 0 || count > MAX_ENGINES)
                throw new IOException("Invalid BuildCraft engine count");
            Set<Engine> engines = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Engine engine;
                try {
                    engine = new Engine(in.readUTF(),
                            new CodaBlockPos(in.readInt(), in.readInt(), in.readInt()));
                } catch (IllegalArgumentException failure) {
                    throw new IOException("Invalid stored engine position", failure);
                }
                if (!engines.add(engine))
                    throw new IOException("Duplicate engine entry in stored world state");
            }
            if (in.available() != 0) throw new IOException("Trailing BuildCraft engine save data");
            return Set.copyOf(engines);
        }
    }

    public static void save(Path worldRoot, Set<Engine> engines) throws IOException {
        if (engines == null || engines.size() > MAX_ENGINES)
            throw new IOException("Invalid BuildCraft engine count");
        List<Engine> sorted = new ArrayList<>(engines);
        sorted.sort(Comparator.comparing(Engine::dimension)
                .thenComparingInt(e -> e.position().x())
                .thenComparingInt(e -> e.position().y())
                .thenComparingInt(e -> e.position().z()));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(sorted.size());
            for (Engine engine : sorted) {
                out.writeUTF(engine.dimension());
                out.writeInt(engine.position().x());
                out.writeInt(engine.position().y());
                out.writeInt(engine.position().z());
                if (body.size() + DIGEST_LENGTH > MAX_BYTES)
                    throw new IOException("BuildCraft engine save exceeds maximum size");
            }
        }
        byte[] data = body.toByteArray();
        if (data.length + DIGEST_LENGTH > MAX_BYTES)
            throw new IOException("BuildCraft engine save exceeds maximum size");
        Path dest = file(worldRoot, true);
        // Existing corrupt state is not disposable. Refuse to clobber it even
        // if a mod tried to place another engine after a failed restore.
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) load(worldRoot);
        Path temp = Files.createTempFile(dest.getParent(), ".engines-", ".tmp");
        try {
            try (java.io.OutputStream output = Files.newOutputStream(temp)) {
                output.write(data);
                output.write(sha256(data));
            }
            Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
