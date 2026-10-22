package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;

/** Persistent game state only. No Minecraft objects, wall-clock progression or generated resources. */
final class Settlement {
    enum Job { IDLE, LUMBERJACK, MINER, FARMER }
    enum Fate { PENDING, ARRIVING, RESIDENT, LEAVING, LEFT, DEAD }
    static final int CAPACITY=24, RADIUS=16, ARRIVAL_INTERVAL=6000;
    String dimension="minecraft:overworld";
    CodaBlockPos camp, stockpile;
    int arrival=1200, scanCursor;
    long elapsed;
    final Set<CodaBlockPos> beds=new LinkedHashSet<>(), ignoredBeds=new HashSet<>();
    final List<Settler> settlers=new ArrayList<>();
    static final class Settler {
        final UUID id; final String name;
        CodaBlockPos bed, spawn, work;
        Job job=Job.IDLE; Fate fate=Fate.PENDING;
        double hunger=80, loyalty=55;
        int hungerTicks, absentTicks, travelTicks, workIndex, retryTicks;
        boolean returning, prepared;
        String direction="north", activity="Arriving";
        CodaBlockPos replant; String seed="";
        Settler(UUID id,String name){this.id=id;this.name=name;}
        boolean resident(){return fate==Fate.PENDING||fate==Fate.ARRIVING||fate==Fate.RESIDENT;}
        boolean terminal(){return fate==Fate.LEFT||fate==Fate.DEAD;}
        /** One ten-second hunger step. Loyal residents still starve. */
        void hungerStep(){
            hunger=Math.max(0,hunger-1);
            if(hunger<30&&loyalty<80)loyalty=Math.max(0,loyalty-.05);
            if(fate==Fate.RESIDENT && ((loyalty<40&&hunger<=30)||(loyalty<80&&hunger<=10))) {
                fate=Fate.LEAVING;activity="Leaving: food shortage";travelTicks=0;bed=null;
            }
        }
        void meal(){hunger=Math.min(100,hunger+40);loyalty=Math.min(100,loyalty+.5);}
    }
    List<CodaBlockPos> freeBeds(){
        Set<CodaBlockPos> occupied=new HashSet<>();
        for(var s:settlers)if(s.resident()&&s.bed!=null)occupied.add(s.bed);
        return beds.stream().filter(b->!ignoredBeds.contains(b)&&!occupied.contains(b)).toList();
    }
    long population(){return settlers.stream().filter(Settler::resident).count();}
    static long distance(CodaBlockPos a,CodaBlockPos b){
        long x=(long)a.x()-b.x(),y=(long)a.y()-b.y(),z=(long)a.z()-b.z();return x*x+y*y+z*z;
    }
    static CodaBlockPos add(CodaBlockPos p,int x,int y,int z){return new CodaBlockPos(p.x()+x,p.y()+y,p.z()+z);}
    static boolean within(CodaBlockPos a,CodaBlockPos b,int r){return distance(a,b)<=(long)r*r;}
}
