package dev.howlingwhispers.sortingpipes;

import dev.howlingwhispers.codaloader.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Per-save, dimension-keyed configuration. Replaced atomically before going live. */
public final class PipeSettings {
    public record Key(String dimension, CodaBlockPos pos) {
        public Key {
            if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid dimension");
        }
        String encode() { return dimension + "@" + pos.x() + "," + pos.y() + "," + pos.z(); }
        static Key decode(String value) {
            String[] a=value.split("@", -1), p=a[1].split(",", -1);
            return new Key(a[0], new CodaBlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])));
        }
    }
    public record Setting(CodaItemFilter filter, String input) {
        public Setting { if (!Set.of("auto","east","west","up","down","south","north").contains(input)) throw new IllegalArgumentException("Invalid source direction"); }
        public static Setting defaults() { return new Setting(CodaItemFilter.all(), "auto"); }
    }
    private Map<Key,Setting> entries=new HashMap<>();
    private Path root;
    public void load(Path save) throws IOException {
        Path normal=save.toAbsolutePath().normalize();
        if (normal.equals(root)) return;
        Path file=normal.resolve("howl_sorting_pipes/settings.properties");
        Map<Key,Setting> next=new HashMap<>();
        if (Files.exists(file)) {
            if (Files.size(file)>2_000_000) throw new IOException("Pipe settings exceed size limit");
            Properties p=new Properties();
            try (Reader r=Files.newBufferedReader(file)) { p.load(r); }
            if (!"1".equals(p.getProperty("schema"))) throw new IOException("Unsupported pipe settings schema");
            try {
                for (String key:p.stringPropertyNames()) {
                    if (key.equals("schema")) continue;
                    String[] v=p.getProperty(key).split("\\|",-1);
                    if (v.length!=3) throw new IllegalArgumentException("Invalid settings row");
                    Set<String> items=v[1].isEmpty()?Set.of():Set.copyOf(Arrays.asList(v[1].split(",")));
                    next.put(Key.decode(key),new Setting(new CodaItemFilter(CodaItemFilter.Mode.valueOf(v[0]), items),v[2]));
                }
            } catch (RuntimeException e) { throw new IOException("Invalid pipe settings; transport paused",e); }
        }
        entries=next; root=normal;
    }
    public Setting get(Key k) { return entries.getOrDefault(k,Setting.defaults()); }
    public void update(Key key, Setting setting) throws IOException {
        if (root==null) throw new IOException("World configuration unavailable");
        Map<Key,Setting> next=new HashMap<>(entries); next.put(key,setting);
        if (next.size()>4096) throw new IOException("At most 4096 configured pipes per world");
        Properties p=new Properties(); p.setProperty("schema","1");
        for (var e:next.entrySet()) p.setProperty(e.getKey().encode(),e.getValue().filter().mode()+"|"
                +String.join(",",new TreeSet<>(e.getValue().filter().items()))+"|"+e.getValue().input());
        Path dir=root.resolve("howl_sorting_pipes"); Files.createDirectories(dir);
        Path temp=Files.createTempFile(dir,"settings-",".tmp");
        try {
            try (Writer w=Files.newBufferedWriter(temp)) { p.store(w,"H.O.W.L. Sorting Pipes"); }
            try { Files.move(temp,dir.resolve("settings.properties"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,dir.resolve("settings.properties"),StandardCopyOption.REPLACE_EXISTING); }
            entries=next;
        } finally { Files.deleteIfExists(temp); }
    }
    public void reset() { root=null; entries=new HashMap<>(); }
}
