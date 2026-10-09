import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjPortRole;
import buildcraft.api.v2.energy.MjTransferPolicy;
import buildcraft.api.v2.energy.MjTransferResult;
import dev.howlingwhispers.buildcraft.BuildCraftMjPortAdapter;
import dev.howlingwhispers.buildcraft.BuildCraftRedstoneEngine;
import java.util.Set;

public final class BuildCraftMjPortAdapterTest {
    static int checks;
    static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    /** BCCE's exact MjPort interface implemented by an isolated test receiver. */
    static final class Receiver implements MjPort {
        long stored;
        final long capacity;
        final boolean accepting;
        int simulations, executions;
        Receiver(long capacity, boolean accepting) {
            this.capacity=capacity; this.accepting=accepting;
        }
        @Override public boolean canInsert() { return accepting; }
        @Override public boolean canExtract() { return false; }
        @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
            long accepted = Math.min(offered.microMj(),capacity-stored);
            if (mode.isSimulation()) simulations++;
            else { stored += accepted; executions++; }
            return MjTransferResult.of(offered,MjAmount.ofMicro(accepted));
        }
        @Override public MjTransferResult extract(MjAmount requested,OperationMode mode) {
            return MjTransferResult.none(requested);
        }
        @Override public MjAmount stored() { return MjAmount.ofMicro(stored); }
        @Override public MjAmount capacity() { return MjAmount.ofMicro(capacity); }
    }

    public static void main(String[] args) {
        check(OperationMode.SIMULATE.isSimulation(),"BCCE SIMULATE mode");
        check(!OperationMode.EXECUTE.isSimulation(),"BCCE EXECUTE mode");
        Receiver receiver=new Receiver(200_000,true);
        var adapter=new BuildCraftMjPortAdapter(receiver,Set.of(MjPortRole.CONSUMER));
        check(!adapter.redstoneReceiver(),"Ordinary MJ port not piston-pulsed");
        check(adapter.simulateInsert(400_000)==200_000,"Partial simulation capacity");
        check(receiver.stored==0,"Simulation must not modify MJ");
        check(adapter.executeInsert(400_000)==200_000,"Exact transfer amount");
        check(receiver.stored==200_000,"Only executed MJ is credited");
        check(adapter.executeInsert(1000)==0,"Full receiver is not overcharged");

        var engine=new BuildCraftRedstoneEngine();
        Receiver live=new Receiver(1_000_000,true);
        var endpoint=new BuildCraftMjPortAdapter(live,Set.of(MjPortRole.CONSUMER));
        long total=0;
        for(int tick=1;tick<=10;tick++)
            total+=engine.tick(tick,true,endpoint).transferredMicroMj();
        check(total==500_000,"Original engine generates half an MJ in ten ticks");
        check(live.stored==total,"MJ ownership conserved across BCCE adapter");
        check(engine.storedMicroMj()==0,"Only accepted MJ leaves original engine");

        Receiver pulseReceiver=new Receiver(3_000_000,true);
        var pulsed=new BuildCraftMjPortAdapter(pulseReceiver,Set.of(MjPortRole.REDSTONE_RECEIVER));
        check(pulsed.redstoneReceiver(),"Original redstone receiver semantics");
        BuildCraftRedstoneEngine redstone=new BuildCraftRedstoneEngine();
        boolean sawPulse=false;
        for(int tick=1;tick<=300;tick++) {
            var result=redstone.tick(tick,true,pulsed);
            if(result.midpointPulse()) {
                sawPulse=true;
                check(result.transferredMicroMj()>0,"Piston midpoint transfers MJ");
            } else check(result.transferredMicroMj()==0,"No streaming between pulses");
        }
        check(sawPulse && pulseReceiver.stored>0,"BCCE piston energy reached original port");

        Receiver closed=new Receiver(500_000,false);
        var refused=new BuildCraftMjPortAdapter(closed,Set.of(MjPortRole.CONSUMER));
        check(refused.simulateInsert(50_000)==0 && refused.executeInsert(50_000)==0,
                "canInsert guard respected");
        check(closed.simulations==0 && closed.executions==0,"Closed ports never invoked");

        Receiver strict=new Receiver(50_000,true);
        var none=strict.insert(MjAmount.ofMicro(100_000),
                MjTransferPolicy.ALL_OR_NOTHING,OperationMode.EXECUTE);
        check(none.transferred().isZero(),"Original all-or-nothing refuses partial transfer");
        check(strict.stored==0,"All-or-nothing request did not mutate receiver");

        try {
            new BuildCraftMjPortAdapter(receiver,Set.of(MjPortRole.PROVIDER));
            throw new AssertionError("MJ producer misclassified as consumer");
        } catch(IllegalArgumentException expected) { checks++; }

        MjPort dishonest=new MjPort() {
            public MjTransferResult insert(MjAmount amount,OperationMode mode) {
                return MjTransferResult.of(MjAmount.ofMicro(amount.microMj()+1),amount);
            }
            public MjTransferResult extract(MjAmount amount,OperationMode mode) {
                return MjTransferResult.none(amount);
            }
            public MjAmount stored(){ return MjAmount.ZERO; }
            public MjAmount capacity(){ return MjAmount.ofMj(1); }
        };
        try {
            new BuildCraftMjPortAdapter(dishonest,Set.of(MjPortRole.CONSUMER))
                    .simulateInsert(100_000);
            throw new AssertionError("Changed requested MJ must be refused");
        } catch(IllegalStateException expected) { checks++; }
        System.out.println("PASS: "+checks+" original BCCE API2 MJ compatibility assertions");
    }
}
