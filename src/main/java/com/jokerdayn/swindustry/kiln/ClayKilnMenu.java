package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The three slots of the loading port, plus the player's own pockets.
 *
 * <p>Laid out like a furnace on purpose. The kiln is strange enough already — forty-four blocks and
 * a flue — so the one screen a player looks at should not also be a puzzle.</p>
 */
public class ClayKilnMenu extends AbstractContainerMenu {

    private static final int KILN_SLOTS = ClayKilnBlockEntity.SLOT_COUNT;
    private static final int INVENTORY_START = KILN_SLOTS;
    private static final int INVENTORY_END = INVENTORY_START + 27;
    private static final int HOTBAR_END = INVENTORY_END + 9;

    private final IItemHandler kiln;
    private final ContainerData data;
    @Nullable
    private final ClayKilnBlockEntity blockEntity;
    private final BlockPos pos;

    /** Server side: everything is to hand. */
    public ClayKilnMenu(int containerId, Inventory playerInventory, IItemHandler kilnItems,
                        ContainerData data, ClayKilnBlockEntity blockEntity) {
        super(ModMenus.CLAY_KILN.get(), containerId);
        this.kiln = kilnItems;
        this.data = data;
        this.blockEntity = blockEntity;
        this.pos = blockEntity.getBlockPos();

        checkContainerDataCount(data, ClayKilnBlockEntity.DATA_COUNT);
        addKilnSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(data);
    }

    /**
     * Client side: rebuilt from the position in the opening packet.
     *
     * <p>The block entity is already on the client — it came with the chunk — so the same fields
     * back both sides, and the data slots keep the numbers on them current.</p>
     */
    public ClayKilnMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        super(ModMenus.CLAY_KILN.get(), containerId);
        this.pos = buffer.readBlockPos();

        ClayKilnBlockEntity found = playerInventory.player.level().getBlockEntity(pos)
            instanceof ClayKilnBlockEntity kilnEntity ? kilnEntity : null;
        this.blockEntity = found;
        this.kiln = found != null ? found.itemHandler() : new ItemStackHandler(KILN_SLOTS);
        this.data = new SimpleContainerData(ClayKilnBlockEntity.DATA_COUNT);

        addKilnSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(this.data);
    }

    private void addKilnSlots() {
        addSlot(new SlotItemHandler(kiln, ClayKilnBlockEntity.SLOT_INPUT, 56, 17));
        addSlot(new SlotItemHandler(kiln, ClayKilnBlockEntity.SLOT_FUEL, 56, 53));
        addSlot(new OutputSlot(kiln, ClayKilnBlockEntity.SLOT_OUTPUT, 116, 35));
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    /** Nothing may be put into the output slot, and taking from it is one-way. */
    private static class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    // ------------------------------------------------------------------

    public boolean isLit() {
        return data.get(ClayKilnBlockEntity.DATA_LIT_TIME) > 0;
    }

    /** How much of the current fuel is left, 0 to 1. */
    public float fuelRemaining() {
        int duration = data.get(ClayKilnBlockEntity.DATA_LIT_DURATION);
        if (duration <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) data.get(ClayKilnBlockEntity.DATA_LIT_TIME) / duration);
    }

    /** How far through the current smelt the kiln is, 0 to 1. */
    public float cookProgress() {
        int duration = data.get(ClayKilnBlockEntity.DATA_COOK_DURATION);
        if (duration <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) data.get(ClayKilnBlockEntity.DATA_COOK_PROGRESS) / duration);
    }

    public boolean isStructureFormed() {
        return data.get(ClayKilnBlockEntity.DATA_FORMED) != 0;
    }

    // ------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return false;
        }
        return player.level().getBlockEntity(pos) == blockEntity
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < KILN_SLOTS) {
            // Out of the kiln, into the player.
            if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (stack.getBurnTime(RecipeType.SMELTING) > 0) {
            // Fuel knows where it belongs.
            if (!moveItemStackTo(stack, ClayKilnBlockEntity.SLOT_FUEL, ClayKilnBlockEntity.SLOT_FUEL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, ClayKilnBlockEntity.SLOT_INPUT, ClayKilnBlockEntity.SLOT_INPUT + 1, false)) {
            // Anything else goes in the input, and failing that between inventory and hotbar.
            if (index < INVENTORY_END) {
                if (!moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
