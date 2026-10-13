package dev.howlingwhispers.sortingpipes;

import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.sortingpipes.PipeSettings.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic routes and conservation tests with no Minecraft classes in the mod JAR. */
public final class SortingTransportTest {
    private static int checks;
    static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static CodaBlockPos p(int x,int z) { return new CodaBlockPos(x,64,z); }
    private static final String DIM="minecraft:overworld",IRON="minecraft:iron_ingot",GOLD="minecraft:gold_ingot";
    static final class World implements CodaWorldView {
        final Path dir;
        final Map<CodaBlockPos,String> blocks=new HashMap<>();
        final Map<CodaBlockPos,Map<String,Integer>> stock=new HashMap<>();
        final Set<CodaBlockPos> full=new HashSet<>(),unloaded=new HashSet<>();
        int transfers;
        long pulseCounter;
        World() throws Exception { dir=Files.createTempDirectory("sorting-pipes-test-"); }
        public Optional<Path> worldDirectory() { return Optional.of(dir); }
        public List<String> dimensions() { return List.of(DIM); }
        public boolean isChunkLoaded(String dim,CodaBlockPos pos) { return !unloaded.contains(pos); }
        public boolean isBlock(String dim,CodaBlockPos pos,String id) { return id.equals(blocks.get(pos)); }
        public Optional<CodaInventoryView> inventory(String dim,CodaBlockPos pos) { throw new AssertionError("Use supported typed inventories"); }
        public Optional<CodaItemInventoryView> itemInventory(String dim,CodaBlockPos pos) {
            if (!isChunkLoaded(dim,pos) || !stock.containsKey(pos)) return Optional.empty();
            var slots=new ArrayList<CodaItemInventoryView.Slot>();
            for (var e:new TreeMap<>(stock.get(pos)).entrySet()) slots.add(new CodaItemInventoryView.Slot(slots.size(),e.getKey(),e.getValue(),64));
            return Optional.of(new CodaItemInventoryView(pos,slots));
        }
        public int transferFiltered(String dim,CodaBlockPos from,CodaBlockPos to,int max,CodaItemFilter filter) {
            check(isChunkLoaded(dim,from)&&isChunkLoaded(dim,to),"Never writes unloaded endpoints");
            check(!from.equals(to),"Never transfers to source");
            if (full.contains(to)) return 0;
            for (var e:stock.get(from).entrySet()) if (e.getValue()>0 && filter.allows(e.getKey())) {
                int count=Math.min(max,e.getValue()); e.setValue(e.getValue()-count); stock.get(to).merge(e.getKey(),count,Integer::sum); transfers+=count; return count;
            }
            return 0;
        }
        void chest(CodaBlockPos pos,String id,int count) { stock.put(pos,new HashMap<>()); if (count>0) stock.get(pos).put(id,count); }
        int count(CodaBlockPos pos,String id) { return stock.get(pos).getOrDefault(id,0); }
        int total(String id) { return stock.values().stream().mapToInt(m->m.getOrDefault(id,0)).sum(); }
    }
    private record Rig(World w,PipeSettings s,SortingTransport t) {}
    static Rig rig(String attachment) throws Exception {
        World w=new World(); PipeSettings s=new PipeSettings(); SortingTransport t=new SortingTransport(s);
        w.blocks.put(p(0,0),SortingTransport.EXTRACT); w.blocks.put(p(1,0),SortingTransport.PIPE);
        w.blocks.put(p(2,0),SortingTransport.PIPE); w.blocks.put(p(2,1),SortingTransport.SORT); w.blocks.put(p(2,-1),SortingTransport.SORT);
        w.chest(p(-1,0),IRON,64); w.chest(p(3,1),IRON,0); w.chest(p(3,-1),IRON,0);
        if (attachment!=null) w.blocks.put(p(0,1),attachment);
        tick(w,t,1,"a"); s.load(w.dir); return new Rig(w,s,t);
    }
    static void tick(World w,SortingTransport t,long n,String session) {
        for (var e:w.blocks.entrySet()) if (e.getValue().endsWith("_pipe") && !w.unloaded.contains(e.getKey())) t.pipeTick(new CodaBlockEntityTick(SortingTransport.HOLDER,DIM,e.getKey()));
        t.serverTick(new CodaServerTickContext(session,n,Optional.of(w)));
    }
    static void run(Rig r,int pulses) { for (int n=1;n<=pulses;n++) tick(r.w,r.t,++r.w.pulseCounter*16L,"a"); }
    static Setting whitelist(String id) { return new Setting(CodaItemFilter.only(id),"auto"); }
    public static void main(String[] args) throws Exception {
        check(!CodaItemFilter.only(IRON).allows(GOLD),"Whitelist rejects other ID");
        check(new CodaItemFilter(CodaItemFilter.Mode.BLACKLIST,Set.of(IRON)).allows(GOLD),"Blacklist allows other ID");
        check(!new CodaItemFilter(CodaItemFilter.Mode.WHITELIST,Set.of()).allows(IRON),"Empty whitelist blocks all");
        Rig r=rig(SortingTransport.ROUND); run(r,10);
        check(r.w.count(p(3,1),IRON)==5 && r.w.count(p(3,-1),IRON)==5,"Round robin divides 10 items 5/5");
        check(r.w.total(IRON)==64,"Round robin conserves items");
        r.w.full.add(p(3,1)); run(r,4);
        check(r.w.count(p(3,1),IRON)==5 && r.w.count(p(3,-1),IRON)==9,"Full output skipped");
        Rig e=rig(SortingTransport.EQUAL); e.w.stock.get(p(3,1)).put(IRON,10); run(e,14);
        check(e.w.count(p(3,1),IRON)==12 && e.w.count(p(3,-1),IRON)==12,"Equalizer catches up empty stock then splits ties");
        check(e.w.total(IRON)==74,"Equalizer conserves items including existing stock");
        Rig f=rig(SortingTransport.ROUND); f.s.update(new Key(DIM,p(2,1)),whitelist(IRON));
        f.s.update(new Key(DIM,p(2,-1)),whitelist(GOLD)); f.w.stock.get(p(-1,0)).put(GOLD,20); run(f,12);
        check(f.w.count(p(3,1),GOLD)==0 && f.w.count(p(3,-1),IRON)==0,"Sorting branches reject wrong items");
        check(f.w.count(p(3,1),IRON)>0 && f.w.count(p(3,-1),GOLD)>0,"Both matching item types make progress");
        check(f.w.total(IRON)==64 && f.w.total(GOLD)==20,"Sorted items conserved");
        PipeSettings reload=new PipeSettings(); reload.load(f.w.dir);
        check(reload.get(new Key(DIM,p(2,-1))).filter().equals(CodaItemFilter.only(GOLD)),"Filter survives save/reload");
        check(reload.get(new Key("minecraft:the_nether",p(2,-1))).filter().allows(IRON),"Dimensions isolate settings");
        Rig unloaded=rig(SortingTransport.ROUND); unloaded.w.unloaded.add(p(2,1)); run(unloaded,5);
        check(unloaded.w.count(p(3,1),IRON)==0 && unloaded.w.count(p(3,-1),IRON)==5,"Unloaded branch excluded");
        Rig conflict=rig(SortingTransport.ROUND); conflict.w.blocks.put(p(0,-1),SortingTransport.EQUAL); run(conflict,3);
        check(conflict.w.transfers==0,"Conflicting attachments pause extractor");
        Rig ambiguous=rig(null); ambiguous.w.chest(p(0,-1),GOLD,9); run(ambiguous,3);
        check(ambiguous.w.transfers==0,"Ambiguous auto input does not drain output chest");
        ambiguous.s.update(new Key(DIM,p(0,0)),new Setting(CodaItemFilter.all(),"west")); run(ambiguous,3);
        check(ambiguous.w.count(p(-1,0),IRON)==61,"Explicit input chooses source");
        Rig broken=rig(null); broken.w.blocks.remove(p(1,0)); run(broken,4);
        check(broken.w.transfers==0,"Broken pipe route transfers nothing");
        Rig loop=rig(null); loop.w.blocks.put(p(1,1),SortingTransport.PIPE); loop.w.blocks.put(p(1,-1),SortingTransport.PIPE); run(loop,3);
        check(loop.w.transfers==3,"Cyclic routes terminate and deliver one item per pulse");
        Rig blank=rig(null); blank.s.update(new Key(DIM,p(0,0)),new Setting(new CodaItemFilter(CodaItemFilter.Mode.WHITELIST,Set.of()),"auto")); run(blank,3);
        check(blank.w.transfers==0,"Empty whitelist extractor blocks everything");
        Rig world2=rig(null); tick(world2.w,world2.t,16,"new-session");
        check(world2.w.transfers==1,"New session resets cadence");
        System.out.println("PASS: "+checks+" sorting, routing, distribution, persistence and conservation checks");
    }
}
