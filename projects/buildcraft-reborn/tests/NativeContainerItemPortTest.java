import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.item.ItemMatcher;
import buildcraft.api.v2.item.ItemTransferPolicy;
import buildcraft.lib.compat.howl.NativeContainerItemPort;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NativeContainerItemPortTest {
    private static int checks;
    private static void check(boolean pass, String what) {
        checks++;
        if (!pass) throw new AssertionError(what);
    }
    public static void main(String[] args) {
        // REAL Mojang container and item components, no fake storage fixture.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SimpleContainer chest = new SimpleContainer(2);
        // Minecraft 26.1+ binds ItemStack component prototypes only when
        // a real world/datapack reload completes, NOT in Bootstrap.bootStrap.
        // Until the BCCE original tiles load in-world, this is a source-ABI
        // test and the mutation test must report BLOCKED, never fake a pass.
        ItemStack iron;
        try {
            iron = new ItemStack(Items.IRON_INGOT, 30);
        } catch (NullPointerException unbound) {
            if (!"Components not bound yet".equals(unbound.getMessage())) throw unbound;
            check(chest.isEmpty(), "Genuine empty Minecraft Container available");
            System.out.println("BLOCKED: native ItemStack components not bound before Minecraft world reload");
            System.out.println("Native BCCE ItemPort adapter compiled; real slot mutation NOT tested here.");
            return;
        }
        chest.setItem(0, iron);
        chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 64));
        NativeContainerItemPort port = new NativeContainerItemPort(chest, null);
        ItemStack offered = new ItemStack(Items.IRON_INGOT, 40);

        var simulated = port.insert(offered, OperationMode.SIMULATE);
        check(simulated.transferredCount() == 34, "Respect vanilla stack limit");
        check(chest.getItem(0).getCount() == 30, "Simulation MUST NOT mutate chest");
        check(offered.getCount() == 40, "Offered source stack unchanged");
        var exact = port.insert(offered, ItemTransferPolicy.ALL_OR_NOTHING,
                OperationMode.EXECUTE);
        check(!exact.movedAnything(), "Exact insertion must be atomic");
        check(chest.getItem(0).getCount() == 30, "Failed exact insertion has no writes");
        var partial = port.insert(offered, OperationMode.EXECUTE);
        check(partial.transferredCount() == 34, "True partial insertion result");
        check(chest.getItem(0).getCount() == 64, "Actual Minecraft slot updated");
        check(chest.getItem(1).getCount() == 64, "Unrelated item preserved");

        var readOnly = port.extract(
                stack -> stack.is(Items.IRON_INGOT), 20, OperationMode.SIMULATE);
        check(readOnly.transferredCount() == 20, "Simulation reports extractable iron");
        check(chest.getItem(0).getCount() == 64, "Simulated extraction unchanged");
        var removed = port.extract(stack -> stack.is(Items.IRON_INGOT),
                20, OperationMode.EXECUTE);
        check(removed.transferredCount() == 20, "Real BCCE extraction result");
        check(removed.transferred().is(Items.IRON_INGOT), "Actual native ItemStack retained");
        check(chest.getItem(0).getCount() == 44, "Real chest decremented once");
        var rejected = port.extract(stack -> stack.is(Items.IRON_INGOT),
                60, 60, OperationMode.EXECUTE);
        check(!rejected.movedAnything(), "Exact extraction must refuse short reads");
        check(chest.getItem(0).getCount() == 44, "Failed extraction is not destructive");
        check(port.extract(ItemMatcher.none(), 20, OperationMode.EXECUTE)
                .transferredCount() == 0, "BCCE none matcher must not extract");

        chest.setItem(1, ItemStack.EMPTY);
        var complete = port.insert(new ItemStack(Items.IRON_INGOT, 25),
                ItemTransferPolicy.ALL_OR_NOTHING, OperationMode.EXECUTE);
        check(complete.completed(), "Empty native slot permits exact insertion");
        check(chest.getItem(1).getCount() == 5, "Existing first slot filled before empty slot");
        check(chest.getItem(0).getCount() == 64, "First slot merged with same components");
        check(port.extract(ItemMatcher.any(), 90, OperationMode.SIMULATE)
                .transferredCount() == 69, "Multi-slot extraction counts only identical items");
        check(chest.getItem(0).getCount() == 64 && chest.getItem(1).getCount() == 5,
              "Multi-slot simulation leaves chest untouched");
        System.out.println("PASS: " + checks
                + " real Minecraft SimpleContainer+BCCE ItemPort insertion/extraction checks");
        System.out.println("Original pipe flow and in-game gameplay still not compiled.");
    }
}
