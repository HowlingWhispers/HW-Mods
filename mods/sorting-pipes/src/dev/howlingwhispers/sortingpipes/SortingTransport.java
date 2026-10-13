package dev.howlingwhispers.sortingpipes;

import dev.howlingwhispers.codaloader.api.*;
import dev.howlingwhispers.sortingpipes.PipeSettings.Key;
import java.util.*;

/** Loaded native ticker signals drive bounded per-item route discovery and chest transactions. */
public final class SortingTransport {
    public static final String NS="howl_sorting_pipes:";
    public static final String EXTRACT=NS+"extractor_pipe", PIPE=NS+"transport_pipe", SORT=NS+"sorting_pipe";
    public static final String ROUND=NS+"round_robin", EQUAL=NS+"equalizer", HOLDER=NS+"pipe_holder";
    public static final Map<String,int[]> DIRECTIONS;
    static {
        var m=new LinkedHashMap<String,int[]>();
        m.put("east",new int[]{1,0,0}); m.put("west",new int[]{-1,0,0}); m.put("up",new int[]{0,1,0});
        m.put("down",new int[]{0,-1,0}); m.put("south",new int[]{0,0,1}); m.put("north",new int[]{0,0,-1});
        DIRECTIONS=Collections.unmodifiableMap(m);
    }
    private record Hop(CodaBlockPos pos,int length) {}
    private record Target(CodaBlockPos pos,int length) {}
    private final Set<Key> ticking=new HashSet<>();
    private final Map<Key,Integer> cursors=new HashMap<>();
    private final Map<Key,Long> delivered=new HashMap<>();
    private final Map<Key,Integer> itemCursors=new HashMap<>();
    private final PipeSettings settings;
    private String session="";
    private volatile String status="Waiting for loaded pipes.";
    private long moved;
    private int scanCursor;
    private String lastError="";
    public SortingTransport(PipeSettings settings) { this.settings=settings; }
    public void pipeTick(CodaBlockEntityTick event) {
        if (ticking.size()<4096) ticking.add(new Key(event.dimension(),event.position()));
    }
    public String status() { return status+" Delivered "+moved+" items this session."; }
    public void serverTick(CodaServerTickContext tick) {
        Set<Key> live=Set.copyOf(ticking); ticking.clear();
        if (!session.equals(tick.sessionId())) {
            session=tick.sessionId(); cursors.clear(); itemCursors.clear(); delivered.clear(); settings.reset(); moved=0; scanCursor=0; lastError="";
        }
        if (tick.tick()%8!=0 || tick.world().isEmpty()) return;
        CodaWorldView w=tick.world().orElseThrow();
        try {
            var directory=w.worldDirectory();
            if (directory.isEmpty()) { status="Save directory unavailable; transport paused."; return; }
            settings.load(directory.orElseThrow());
            List<Key> extractors=live.stream().sorted(Comparator.comparing(Key::dimension)
                    .thenComparingInt(k->k.pos().x()).thenComparingInt(k->k.pos().y()).thenComparingInt(k->k.pos().z())).toList();
            int processed=0, success=0;
            int start=extractors.isEmpty()?0:Math.floorMod(scanCursor,extractors.size());
            int scanned=0;
            for (;scanned<extractors.size();scanned++) {
                Key origin=extractors.get((start+scanned)%extractors.size());
                if (!block(w,origin.dimension(),origin.pos(),EXTRACT)) continue;
                if (++processed>16) { status="Active extractor budget reached (16 per scan)."; break; }
                if (tick.tick()-delivered.getOrDefault(origin,Long.MIN_VALUE/2)<16) continue;
                if (pulse(w,origin)) { success++; moved++; delivered.put(origin,tick.tick()); }
            }
            scanCursor=extractors.isEmpty()?0:(start+scanned)%extractors.size();
            delivered.keySet().retainAll(live); cursors.keySet().retainAll(live); itemCursors.keySet().retainAll(live);
            status=success>0 ? "Sorting pipes working." : "Idle: check input direction, filters, output room and attachments.";
            lastError="";
        } catch (Exception e) {
            status="Transport paused: "+e.getMessage();
            if (!status.equals(lastError)) { System.err.println("[Sorting Pipes] "+status); lastError=status; }
        }
    }
    private boolean pulse(CodaWorldView w,Key origin) throws Exception {
        String dim=origin.dimension(); var config=settings.get(origin);
        List<CodaBlockPos> sources=new ArrayList<>();
        if (config.input().equals("auto")) {
            for (var p:neighbors(origin.pos())) if (w.itemInventory(dim,p).isPresent()) sources.add(p);
            if (sources.size()!=1) return false; // ambiguity must never turn an output into an input
        } else {
            var p=offset(origin.pos(),DIRECTIONS.get(config.input()));
            if (w.itemInventory(dim,p).isPresent()) sources.add(p);
        }
        if (sources.isEmpty()) return false;
        CodaBlockPos source=sources.getFirst();
        var inventory=w.itemInventory(dim,source).orElseThrow();
        List<String> ids=inventory.slots().stream().filter(s->s.count()>0 && config.filter().allows(s.itemId()))
                .map(CodaItemInventoryView.Slot::itemId).distinct().toList();
        if (ids.isEmpty()) return false;
        boolean equal=false, round=false;
        for (var p:neighbors(origin.pos())) { equal|=block(w,dim,p,EQUAL); round|=block(w,dim,p,ROUND); }
        if (equal && round) return false; // player must choose one distribution policy
        int itemStart=Math.floorMod(itemCursors.getOrDefault(origin,0),ids.size());
        int attempts=Math.min(4,ids.size());
        for (int i=0;i<attempts;i++) {
            String id=ids.get((itemStart+i)%ids.size());
            List<Target> targets=routes(w,origin,source,id);
            if (targets.isEmpty()) continue;
            int cursor=Math.floorMod(cursors.getOrDefault(origin,0),targets.size());
            List<Target> ordered=new ArrayList<>();
            for (int j=0;j<targets.size();j++) ordered.add(targets.get((cursor+j)%targets.size()));
            if (equal) {
                Map<CodaBlockPos,Long> counts=new HashMap<>();
                for (var target:ordered) counts.put(target.pos(),w.itemInventory(dim,target.pos()).orElseThrow().count(id));
                ordered.sort(Comparator.comparingLong(t->counts.get(t.pos()))); // stable rotation breaks equal-stock ties
            } else if (!round) ordered=new ArrayList<>(targets);
            for (Target target:ordered) {
                // Every route has a fixed, bounded pulse cadence. One item; no loss-prone in-flight buffer.
                if (w.transferFiltered(dim,source,target.pos(),1,CodaItemFilter.only(id))>0) {
                    cursors.put(origin,targets.indexOf(target)+1); itemCursors.put(origin,(itemStart+i+1)%ids.size()); return true;
                }
            }
        }
        itemCursors.put(origin,(itemStart+attempts)%ids.size());
        return false;
    }
    /** BFS is performed separately per item so a blocked branch cannot hide another valid path. */
    private List<Target> routes(CodaWorldView w,Key origin,CodaBlockPos source,String item) throws Exception {
        ArrayDeque<Hop> queue=new ArrayDeque<>(); Set<CodaBlockPos> visited=new HashSet<>();
        Map<CodaBlockPos,Target> result=new LinkedHashMap<>();
        queue.add(new Hop(origin.pos(),0)); visited.add(origin.pos());
        while (!queue.isEmpty()) {
            Hop hop=queue.removeFirst();
            if (hop.length()>=32) continue;
            for (var p:neighbors(hop.pos())) {
                if (visited.contains(p)) continue;
                if (p.equals(source) || !w.isChunkLoaded(origin.dimension(),p)) continue;
                boolean pipe=block(w,origin.dimension(),p,PIPE) || block(w,origin.dimension(),p,SORT);
                if (pipe) {
                    if (!settings.get(new Key(origin.dimension(),p)).filter().allows(item)) continue;
                    if (!visited.contains(p) && visited.size()<128) { visited.add(p); queue.addLast(new Hop(p,hop.length()+1)); }
                } else if (!p.equals(origin.pos()) && w.itemInventory(origin.dimension(),p).isPresent()) {
                    result.putIfAbsent(p,new Target(p,hop.length()+1));
                }
            }
        }
        return List.copyOf(result.values());
    }
    static boolean block(CodaWorldView w,String dim,CodaBlockPos p,String id) throws Exception {
        return w.isChunkLoaded(dim,p) && w.isBlock(dim,p,id);
    }
    public static List<CodaBlockPos> neighbors(CodaBlockPos p) { return DIRECTIONS.values().stream().map(d->offset(p,d)).toList(); }
    static CodaBlockPos offset(CodaBlockPos p,int[] d) { return new CodaBlockPos(p.x()+d[0],p.y()+d[1],p.z()+d[2]); }
}
