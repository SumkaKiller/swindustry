package com.jokerdayn.swindustry.kiln;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The kiln's inventory as automation sees it.
 *
 * <p>Furnace conventions with one twist: the mouth accepts both materials and fuel, so a hopper
 * beside the port feeds it exactly where a player would. Nothing may extract live fuel, which is
 * what keeps a coal line from draining the burner while still letting spent containers leave
 * through the bottom.</p>
 */
public final class KilnInventoryViews {

    private KilnInventoryViews() {}

    /** Sided resolution used by the capability registration on the loading port. */
    public static IItemHandler forSide(ItemStackHandler items, @Nullable Direction side,
                                       @Nullable Direction facing) {
        if (side == Direction.DOWN) {
            return new Restricted(items, new int[] {ClayKilnBlockEntity.SLOT_OUTPUT, ClayKilnBlockEntity.SLOT_FUEL},
                false, true);
        }
        if (side == Direction.UP) {
            return new Restricted(items, new int[] {ClayKilnBlockEntity.SLOT_INPUT}, true, false);
        }
        if (side == null || side == facing) {
            return new Restricted(items,
                new int[] {ClayKilnBlockEntity.SLOT_INPUT, ClayKilnBlockEntity.SLOT_FUEL}, true, false);
        }
        return new Restricted(items, new int[] {ClayKilnBlockEntity.SLOT_FUEL}, true, false);
    }

    /**
     * A slot-mapped view. Insertion/extraction flags keep pipes from inserting into the output
     * or extracting fuel that is still worth burning.
     */
    private static final class Restricted implements IItemHandler {
        private final ItemStackHandler items;
        private final int[] slots;
        private final boolean insertion;
        private final boolean extraction;

        private Restricted(ItemStackHandler items, int[] slots, boolean insertion, boolean extraction) {
            this.items = items;
            this.slots = slots.clone();
            this.insertion = insertion;
            this.extraction = extraction;
        }

        @Override
        public int getSlots() {
            return slots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(mapped(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insertion ? items.insertItem(mapped(slot), stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!extraction) {
                return ItemStack.EMPTY;
            }
            int mappedSlot = mapped(slot);
            if (mappedSlot == ClayKilnBlockEntity.SLOT_FUEL
                && items.getStackInSlot(mappedSlot).getBurnTime(RecipeType.SMELTING) > 0) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(mappedSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(mapped(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insertion && items.isItemValid(mapped(slot), stack);
        }

        private int mapped(int slot) {
            if (slot < 0 || slot >= slots.length) {
                throw new IndexOutOfBoundsException(
                    "Automation slot " + slot + " outside 0.." + (slots.length - 1));
            }
            return slots[slot];
        }
    }
}
