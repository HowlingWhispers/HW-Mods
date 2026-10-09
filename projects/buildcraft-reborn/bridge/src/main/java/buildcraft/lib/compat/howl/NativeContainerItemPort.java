package buildcraft.lib.compat.howl;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.item.ItemMatcher;
import buildcraft.api.v2.item.ItemPort;
import buildcraft.api.v2.item.ItemTransferPolicy;
import buildcraft.api.v2.item.ItemTransferResult;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minecraft Container endpoint for BCCE's UNCHANGED original ItemPort.
 *
 * This performs actual vanilla slot operations, not pipe routing or a
 * chest-to-chest teleport. The original BCCE pipe still owns when, which
 * and where an item travels. Calls must run on the authoritative server
 * thread; the adapter never discovers or creates a new inventory.
 */
public final class NativeContainerItemPort implements ItemPort {
    private final Container container;
    private final Direction queriedFace;

    public NativeContainerItemPort(Container container, Direction queriedFace) {
        this.container = Objects.requireNonNull(container, "native Minecraft container");
        this.queriedFace = queriedFace;
    }

    private int[] slots() {
        int size = container.getContainerSize();
        if (container instanceof WorldlyContainer sided && queriedFace != null) {
            int[] provided = sided.getSlotsForFace(queriedFace);
            boolean[] used = new boolean[size];
            int[] result = new int[provided.length];
            int length = 0;
            for (int i : provided) {
                if (i < 0 || i >= size || used[i]) continue;
                used[i] = true;
                result[length++] = i;
            }
            return java.util.Arrays.copyOf(result, length);
        }
        int[] all = new int[size];
        for (int i = 0; i < size; i++) all[i] = i;
        return all;
    }

    private boolean canInsert(int slot, ItemStack stack) {
        return container.canPlaceItem(slot, stack) &&
                (!(container instanceof WorldlyContainer sided) ||
                 queriedFace == null ||
                 sided.canPlaceItemThroughFace(slot, stack, queriedFace));
    }

    private boolean canExtract(int slot, ItemStack stack) {
        return !(container instanceof WorldlyContainer sided) ||
                queriedFace == null ||
                sided.canTakeItemThroughFace(slot, stack, queriedFace);
    }

    private record Change(int slot, ItemStack value) {}

    @Override
    public synchronized ItemTransferResult insert(ItemStack offered, OperationMode mode) {
        return insert(offered, ItemTransferPolicy.PARTIAL, mode);
    }

    @Override
    public synchronized ItemTransferResult insert(ItemStack offered,
                                                   ItemTransferPolicy policy, OperationMode mode) {
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(mode, "mode");
        int requested = offered.getCount();
        if (requested == 0 || offered.isEmpty()) return ItemTransferResult.nothing(requested);
        int remaining = requested;
        List<Change> changes = new ArrayList<>();

        // Match existing stacks first, then empty slots. This preserves
        // vanilla item component identity and does not merge unlike data.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot : slots()) {
                ItemStack original = container.getItem(slot);
                if ((pass == 0) != !original.isEmpty() ||
                        !canInsert(slot, offered)) continue;
                if (!original.isEmpty() &&
                        !ItemStack.isSameItemSameComponents(original, offered)) continue;
                int max = Math.min(container.getMaxStackSize(), offered.getMaxStackSize());
                int room = max - original.getCount();
                if (room <= 0) continue;
                int moving = Math.min(room, remaining);
                ItemStack updated = original.isEmpty()
                        ? offered.copyWithCount(moving)
                        : original.copyWithCount(original.getCount() + moving);
                changes.add(new Change(slot, updated));
                remaining -= moving;
                if (remaining == 0) break;
            }
        }
        if (policy == ItemTransferPolicy.ALL_OR_NOTHING && remaining != 0)
            return ItemTransferResult.nothing(requested);
        int accepted = requested - remaining;
        if (mode == OperationMode.EXECUTE && accepted != 0) {
            for (Change change : changes) container.setItem(change.slot(), change.value());
            container.setChanged();
        }
        return ItemTransferResult.ofInsertion(offered, accepted);
    }

    @Override
    public synchronized ItemTransferResult extract(ItemMatcher matcher, int maxCount,
                                                    OperationMode mode) {
        return extract(matcher, 0, maxCount, mode);
    }

    @Override
    public synchronized ItemTransferResult extract(ItemMatcher matcher, int minCount,
                                                    int maxCount, OperationMode mode) {
        Objects.requireNonNull(matcher, "matcher");
        Objects.requireNonNull(mode, "mode");
        if (minCount < 0 || maxCount < minCount)
            throw new IllegalArgumentException("Invalid BCCE extraction range");
        if (maxCount == 0) return ItemTransferResult.nothing(0);
        ItemStack sample = ItemStack.EMPTY;
        int remaining = maxCount;
        List<Change> changes = new ArrayList<>();
        for (int slot : slots()) {
            ItemStack stack = container.getItem(slot);
            if (stack == null || stack.isEmpty() || !matcher.matches(stack) ||
                    !canExtract(slot, stack)) continue;
            if (!sample.isEmpty() && !ItemStack.isSameItemSameComponents(sample, stack))
                continue;
            if (sample.isEmpty()) sample = stack.copy();
            int moving = Math.min(stack.getCount(), remaining);
            changes.add(new Change(slot,
                    stack.getCount() == moving ? ItemStack.EMPTY :
                            stack.copyWithCount(stack.getCount() - moving)));
            remaining -= moving;
            if (remaining == 0) break;
        }
        int taken = maxCount - remaining;
        if (taken < minCount) return ItemTransferResult.nothing(maxCount);
        if (mode == OperationMode.EXECUTE && taken > 0) {
            for (Change change : changes) container.setItem(change.slot(), change.value());
            container.setChanged();
        }
        return ItemTransferResult.ofExtraction(maxCount,
                taken == 0 ? ItemStack.EMPTY : sample.copyWithCount(taken));
    }
}
