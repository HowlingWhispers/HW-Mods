import buildcraft.lib.compat.howl.ActiveModNamespace;
import buildcraft.lib.compat.howl.BlockCapability;
import buildcraft.lib.compat.howl.ModelData;
import buildcraft.lib.compat.howl.ModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;

public final class NativeCapabilityModelDataTest {
    private static void check(boolean condition, String error) {
        if (!condition) throw new AssertionError(error);
    }

    public static void main(String[] args) {
        Identifier pipeId = Identifier.parse("buildcraftlib:pipe");
        BlockCapability<String,Direction> first = BlockCapability.createSided(pipeId,String.class);
        BlockCapability<String,Direction> same = BlockCapability.createSided(pipeId,String.class);
        check(first == same, "Original BCCE CAP_PIPE identity must be canonical");
        check(first.checkedValue("original BCCE tile value").equals("original BCCE tile value"),
              "Must preserve original value");
        check(first.checkedValue(null)==null, "Missing capability is absent");
        try {
            BlockCapability.createSided(pipeId,Integer.class);
            throw new AssertionError("Conflicting capability type was accepted");
        } catch (IllegalArgumentException expected) { /* strict, no silent corruption */ }
        try {
            first.checkedValue(42);
            throw new AssertionError("Misregistered original capability accepted");
        } catch (ClassCastException expected) { /* strict, no fake value */ }

        ModelProperty<String> originalGeometry = new ModelProperty<>();
        ModelProperty<String> anotherProperty = new ModelProperty<>();
        ModelData.Builder builder = ModelData.builder().with(originalGeometry,"original baked quads");
        ModelData firstSnapshot = builder.build();
        builder.with(originalGeometry,"new baked quads");
        ModelData secondSnapshot = builder.build();
        check(firstSnapshot.get(originalGeometry).equals("original baked quads"),
              "Old chunk snapshot must remain immutable");
        check(secondSnapshot.get(originalGeometry).equals("new baked quads"),
              "New chunk snapshot contains updated original renderer data");
        check(firstSnapshot.get(anotherProperty)==null,
              "Unrelated model property cannot leak data");
        check(ModelData.EMPTY.get(originalGeometry)==null,
              "Empty original model state must be empty");
        check(!firstSnapshot.has(anotherProperty), "Property keys must preserve identity");
        boolean missingScopeRejected = false;
        try { ActiveModNamespace.get(); }
        catch (IllegalStateException expected) { missingScopeRejected = true; }
        check(missingScopeRejected, "Pipe registration without a loader scope must fail");
        try (var outer = ActiveModNamespace.enter("hw_buildcraft_reborn")) {
            check(ActiveModNamespace.get().equals("hw_buildcraft_reborn"),
                  "Original pipe must inherit loader namespace");
            try (var inner = ActiveModNamespace.enter("buildcraft_transport")) {
                check(ActiveModNamespace.get().equals("buildcraft_transport"),
                      "Nested module scope must override parent");
            }
            check(ActiveModNamespace.get().equals("hw_buildcraft_reborn"),
                  "Nested scope must restore parent");
        }
        missingScopeRejected = false;
        try { ActiveModNamespace.get(); }
        catch (IllegalStateException expected) { missingScopeRejected = true; }
        check(missingScopeRejected, "Closed namespace must not leak to other registrations");
        System.out.println("PASS: original BCCE capability identities and immutable model payload bridge");
        System.out.println("NOT PLAYABLE: no native Level provider or chunk renderer wired yet");
    }
}
