import buildcraft.lib.compat.minecraft.persistence.BCBlockEntity;
import buildcraft.lib.compat.minecraft.persistence.BCValueInput;
import buildcraft.lib.compat.minecraft.persistence.BCValueOutput;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.TagValueInput;

/** Exercises the original BCCE persistence superclass with actual Mojang ValueIO and registries. */
public final class OriginalPersistenceTest {
    private static final class SavedFields extends BCBlockEntity {
        final boolean flat;
        int ownerMarker;
        CompoundTag payload = new CompoundTag();

        SavedFields(boolean flat) {
            super(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse("minecraft:chest")),
                  BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            this.flat = flat;
        }
        protected boolean storesMachineDataAtRoot() { return flat; }
        protected void writeCommonData(BCValueOutput output) { output.writeInt("owner_marker", ownerMarker); }
        protected void readCommonData(BCValueInput input) { ownerMarker = input.readInt("owner_marker"); }
        protected void writeData(BCValueOutput output) { output.put("pipe", payload.copy()); }
        protected void readData(BCValueInput input) { payload = input.readCompound("pipe").copy(); }
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (boolean flat : new boolean[]{false, true}) {
            var original = new SavedFields(flat);
            // Native world loading supplies the registry provider; bind through Mojang's real load path.
            original.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
            original.ownerMarker = 19;
            original.payload.putString("unknown_pipe_id", "buildcrafttransport:wood_item");
            original.payload.putInt("marker_count", 37);
            original.payload.putLong("relative_tick", 83L);
            CompoundTag saved = original.saveCustomOnly(registries);
            check(saved.contains("pipe") == flat, "pipe holder must preserve its flat root layout");
            check(saved.contains("bc_legacy") != flat, "other machines must retain bc_legacy nesting");
            var restored = new SavedFields(flat);
            restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
            check(restored.ownerMarker == 19, "common owner data lost");
            check(restored.payload.equals(original.payload), "machine payload changed");
            check(restored.saveCustomOnly(registries).equals(saved), "save/load/save changed original layout");
        }
        System.out.println("PASS: original BCCE flat/nested persistence round trips on native Snapshot 3 ValueIO");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
