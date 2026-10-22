package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded, validated per-world state. Failed writes retain the previous file. Corruption never resets a camp. */
final class SettlementStore {
    private final Path folder,file;
    SettlementStore(Path world)throws IOException{
        Path root=world.toAbsolutePath().normalize();
        if(!Files.isDirectory(root)||Files.isSymbolicLink(root))throw new IOException("Invalid world directory");
        folder=root.resolve("howl_frontier");file=folder.resolve("settlement.properties");
        if(Files.isSymbolicLink(folder)||Files.isSymbolicLink(file))throw new IOException("Refusing symlinked Frontier state");
    }
    Settlement load()throws IOException{
        if(!Files.exists(file))return new Settlement();
        if(Files.size(file)>1024*1024)throw new IOException("Frontier state exceeds limit");
        Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
        try{
            if(!"1".equals(p.getProperty("schema")))throw new IllegalArgumentException("Unknown state schema");
            Settlement c=new Settlement();c.dimension=p.getProperty("dimension");
            if(!"minecraft:overworld".equals(c.dimension))throw new IllegalArgumentException("Unsupported camp dimension");
            c.camp=position(p.getProperty("camp"));c.stockpile=position(p.getProperty("stockpile"));
            c.arrival=integer(p,"arrival",0,Settlement.ARRIVAL_INTERVAL);c.elapsed=Long.parseLong(p.getProperty("elapsed"));
            if(c.elapsed<0)throw new IllegalArgumentException("Invalid camp time");
            for(String key:List.of("beds","ignored")){
                int count=integer(p,key+".count",0,256);
                for(int i=0;i<count;i++){
                    CodaBlockPos b=position(p.getProperty(key+"."+i));
                    if(b==null||c.camp==null||!Settlement.within(b,c.camp,32))throw new IllegalArgumentException("Invalid camp bed");
                    (key.equals("beds")?c.beds:c.ignoredBeds).add(b);
                }
            }
            int count=integer(p,"settlers",0,512);Set<UUID> ids=new HashSet<>();Set<CodaBlockPos> claims=new HashSet<>();
            for(int i=0;i<count;i++){
                String k="s."+i+".";UUID id=UUID.fromString(p.getProperty(k+"id"));
                if(!ids.add(id))throw new IllegalArgumentException("Duplicate settler UUID");
                String name=p.getProperty(k+"name");if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Invalid settler name");
                var s=new Settlement.Settler(id,name);
                s.bed=position(p.getProperty(k+"bed"));s.spawn=position(p.getProperty(k+"spawn"));s.work=position(p.getProperty(k+"work"));
                if(s.spawn==null||c.camp==null||!Settlement.within(s.spawn,c.camp,48)
                        ||(s.work!=null&&!Settlement.within(s.work,c.camp,32))
                        ||(s.bed!=null&&!Settlement.within(s.bed,c.camp,32)))throw new IllegalArgumentException("Invalid settler coordinates");
                s.job=Settlement.Job.valueOf(p.getProperty(k+"job"));s.fate=Settlement.Fate.valueOf(p.getProperty(k+"fate"));
                s.hunger=decimal(p,k+"hunger");s.loyalty=decimal(p,k+"loyalty");
                s.hungerTicks=integer(p,k+"hungerTicks",0,199);s.travelTicks=integer(p,k+"travelTicks",0,1000000);
                s.workIndex=integer(p,k+"workIndex",0,1000000);s.returning=bool(p,k+"returning");s.prepared=bool(p,k+"prepared");
                s.replant=position(p.getProperty(k+"replant"));s.seed=p.getProperty(k+"seed","");s.direction=p.getProperty(k+"direction");
                if(!Set.of("north","south","east","west").contains(s.direction))throw new IllegalArgumentException("Invalid work direction");
                if(s.replant!=null&&(!Settlement.within(s.replant,c.camp,40)||!s.seed.matches("minecraft:[a-z_]+")))throw new IllegalArgumentException("Invalid replant task");
                if(s.resident()&&s.bed!=null&&!claims.add(s.bed))throw new IllegalArgumentException("Duplicate bed claim");
                c.settlers.add(s);
            }
            if(c.population()>Settlement.CAPACITY)throw new IllegalArgumentException("Camp capacity exceeded");
            return c;
        }catch(RuntimeException invalid){throw new IOException("Frontier state is invalid; original file preserved",invalid);}
    }
    void save(Settlement c)throws IOException{
        if(Files.isSymbolicLink(folder)||Files.isSymbolicLink(file))throw new IOException("Refusing symlinked Frontier state");
        Files.createDirectories(folder);Properties p=new Properties();
        p.setProperty("schema","1");p.setProperty("dimension",c.dimension);put(p,"camp",c.camp);put(p,"stockpile",c.stockpile);
        p.setProperty("arrival",""+c.arrival);p.setProperty("elapsed",""+c.elapsed);
        for(String key:List.of("beds","ignored")){
            Collection<CodaBlockPos> beds=key.equals("beds")?c.beds:c.ignoredBeds;p.setProperty(key+".count",""+beds.size());int i=0;
            for(var b:beds)put(p,key+"."+i++,b);
        }
        p.setProperty("settlers",""+c.settlers.size());
        for(int i=0;i<c.settlers.size();i++){
            var s=c.settlers.get(i);String k="s."+i+".";
            p.setProperty(k+"id",s.id.toString());p.setProperty(k+"name",s.name);put(p,k+"bed",s.bed);put(p,k+"spawn",s.spawn);put(p,k+"work",s.work);
            p.setProperty(k+"job",s.job.name());p.setProperty(k+"fate",s.fate.name());
            p.setProperty(k+"hunger",""+s.hunger);p.setProperty(k+"loyalty",""+s.loyalty);p.setProperty(k+"hungerTicks",""+s.hungerTicks);
            p.setProperty(k+"travelTicks",""+s.travelTicks);p.setProperty(k+"workIndex",""+s.workIndex);
            p.setProperty(k+"returning",""+s.returning);p.setProperty(k+"prepared",""+s.prepared);p.setProperty(k+"direction",s.direction);
            put(p,k+"replant",s.replant);p.setProperty(k+"seed",s.seed);
        }
        Path tmp=Files.createTempFile(folder,"settlement-",".tmp");
        try{
            try(var out=Files.newOutputStream(tmp)){p.store(out,"H.O.W.L. Frontier 0.1.0-dev.1 - world-owned state");}
            try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            if(Files.exists(file)){
                Path backup=folder.resolve("settlement.previous.properties");if(Files.isSymbolicLink(backup))throw new IOException("Refusing symlinked backup");
                Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}
    }
    private static boolean bool(Properties p,String key){String v=p.getProperty(key);if(!Set.of("true","false").contains(v))throw new IllegalArgumentException("Invalid boolean");return Boolean.parseBoolean(v);}
    private static int integer(Properties p,String key,int min,int max){int n=Integer.parseInt(p.getProperty(key));if(n<min||n>max)throw new IllegalArgumentException("Invalid "+key);return n;}
    private static double decimal(Properties p,String key){double d=Double.parseDouble(p.getProperty(key));if(!Double.isFinite(d)||d<0||d>100)throw new IllegalArgumentException("Invalid "+key);return d;}
    private static void put(Properties p,String k,CodaBlockPos b){p.setProperty(k,b==null?"":b.x()+","+b.y()+","+b.z());}
    private static CodaBlockPos position(String value){
        if(value==null||value.isEmpty())return null;String[] v=value.split(",",-1);if(v.length!=3)throw new IllegalArgumentException("Invalid position");
        var b=new CodaBlockPos(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2]));
        if(Math.abs((long)b.x())>29999984||Math.abs((long)b.z())>29999984||b.y() < -64||b.y()>319)throw new IllegalArgumentException("Position outside Overworld bounds");return b;
    }
}
