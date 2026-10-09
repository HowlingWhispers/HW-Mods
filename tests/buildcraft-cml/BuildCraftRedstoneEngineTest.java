import buildcraft.api.v2.energy.MjAmount;
import dev.howlingwhispers.buildcraft.BuildCraftRedstoneEngine;

public final class BuildCraftRedstoneEngineTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual-expected)<tolerance, message + " ("+actual+" vs "+expected+")");
    }
    private static final class Receiver implements BuildCraftRedstoneEngine.MjEndpoint {
        final boolean pulse;
        long accepted;
        int executions;
        Receiver(boolean pulse) { this.pulse=pulse; }
        @Override public boolean redstoneReceiver(){ return pulse; }
        @Override public long simulateInsert(long offered){ return offered; }
        @Override public long executeInsert(long offered){ accepted+=offered; executions++; return offered; }
    }
    public static void main(String[] args) {
        // Original API2 source, not a new conversion invented for H.O.W.L.
        check(MjAmount.MICRO_MJ_PER_MJ==1_000_000L,"BCCE micro-MJ unit");
        check(MjAmount.ofMj(3).microMj()==3_000_000L,"BCCE exact conversion");
        BuildCraftRedstoneEngine engine = new BuildCraftRedstoneEngine();
        check(BuildCraftRedstoneEngine.OUTPUT_PER_TICK==50_000,"BCCE redstone engine output is 0.05 MJ/t");
        check(BuildCraftRedstoneEngine.MAX_POWER==1_000_000,"BCCE engine capacity is 1 MJ");
        check(BuildCraftRedstoneEngine.MIN_POWER_RECEIVED==100_000,"Original receiver minimum");
        check(BuildCraftRedstoneEngine.MAX_POWER_RECEIVED==4_000_000,"Original max accepted MJ");
        check(engine.stage()==BuildCraftRedstoneEngine.Stage.BLUE,"Cold engine is BLUE");
        near(engine.pistonSpeed(),0.005,0.0000001,"Cold redstone piston uses half base speed");
        for(int t=1;t<=40;t++)engine.tick(t,true,BuildCraftRedstoneEngine.MjEndpoint.NONE);
        check(engine.storedMicroMj()==1_000_000,"Unconnected engine stores only one MJ");
        check(engine.heat()>20.0,"Powered engine warms on its 16-tick heating cadence");
        engine.tick(41,false,BuildCraftRedstoneEngine.MjEndpoint.NONE);
        check(engine.storedMicroMj()==0,"Unpowered engine drains and stops generating MJ");
        near(engine.heat(),engine.snapshot().heat(),0.00001,"Snapshot retains heat");

        Receiver redstone = new Receiver(true);
        BuildCraftRedstoneEngine extraction = new BuildCraftRedstoneEngine();
        boolean sawMidpoint=false;
        int totalPulses=0;
        for(int t=1;t<=220;t++){
            var step=extraction.tick(t,true,redstone);
            if(step.midpointPulse()){
                sawMidpoint=true;
                totalPulses++;
                check(step.pistonProgress()>0.5f,"Original redstone receiver pulse at piston midpoint");
                check(step.transferredMicroMj()>0,"MJ moves at midpoint, not by chest teleport");
            }else {
                check(step.transferredMicroMj()==0,"Never stream into a pulsed receiver");
            }
        }
        check(sawMidpoint,"Redstone receiver receives piston-driven MJ");
        check(redstone.executions==totalPulses,"Only one execute per piston midpoint");
        check(redstone.accepted>0,"Real energy credited to MJ receiver");

        Receiver continuous=new Receiver(false);
        BuildCraftRedstoneEngine ordinary=new BuildCraftRedstoneEngine();
        for(int t=1;t<=10;t++)ordinary.tick(t,true,continuous);
        check(continuous.executions==10,"Ordinary receiver gets continuous MJ updates");
        check(continuous.accepted==500_000,"Ordinary receiver received exact ten BCCE outputs");
        check(ordinary.storedMicroMj()==0,"Energy ownership conserved");

        // Restoring mid-stroke does not restart the piston or double its pulse.
        Receiver uninterrupted=new Receiver(true);
        BuildCraftRedstoneEngine a=new BuildCraftRedstoneEngine();
        for(int t=1;t<=80;t++) a.tick(t,true,uninterrupted);
        var snap=a.snapshot();
        BuildCraftRedstoneEngine b=new BuildCraftRedstoneEngine(snap);
        Receiver restarted=new Receiver(true);
        Receiver control=new Receiver(true);
        for(int t=81;t<=200;t++){
            var live=a.tick(t,true,control);
            var restored=b.tick(t,true,restarted);
            check(live.midpointPulse()==restored.midpointPulse(),
                    "Restored piston follows same exact midpoint schedule");
            check(live.transferredMicroMj()==restored.transferredMicroMj(),
                    "Restored engine conserves same transferred MJ");
        }
        check(a.snapshot().equals(b.snapshot()),"BCCE piston/heat/MJ state identical after reload");
        check(control.accepted==restarted.accepted,"No pulse duplication at restore boundary");

        BuildCraftRedstoneEngine.MjEndpoint broken=new BuildCraftRedstoneEngine.MjEndpoint(){
            public boolean redstoneReceiver(){return false;}
            public long simulateInsert(long n){return n+1;}
            public long executeInsert(long n){return n;}
        };
        try{
            new BuildCraftRedstoneEngine().tick(1,true,broken);
            throw new AssertionError("Over-accepting receiver permitted");
        }catch(IllegalStateException correct){checks++;}
        System.out.println("PASS: "+checks+" original BCCE 8.0.23 engine MJ/heat/piston behavior assertions");
    }
}
