package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
 * <p>Laid out like a furnace on purpose. The kiln is strange enough already — fifty-seven blocks and
 * a flue — so the one screen a player looks at should not also be a puzzle.</p>
 */
public class ClayKilnMenu extends AbstractContainerMenu {

    private static final int KILN_SLOTS = ClayKilnBlockEntity.SLOT_COUNT;
    private static final int INVENTORY_START = KILN_SLOTS;
    private static final int INVENTORY_END = INVENTORY_START + 27;
    private static final int HOTBAR_END = INVENTORY_END + 9;

    private final IItemHandler kiln;
    private final ContainerData data;
    private final ContainerLevelAccess access;
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
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

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
        this.pos = buffer != null && buffer.readableBytes() >= 8 ? buffer.readBlockPos() : BlockPos.ZERO;

        ClayKilnBlockEntity found = playerInventory.player.level().getBlockEntity(pos)
            instanceof ClayKilnBlockEntity kilnEntity ? kilnEntity : null;
        this.blockEntity = found;
        this.kiln = found != null ? found.itemHandler() : new ItemStackHandler(KILN_SLOTS);
        this.data = new SimpleContainerData(ClayKilnBlockEntity.DATA_COUNT);
        this.access = found != null
            ? ContainerLevelAccess.create(playerInventory.player.level(), pos)
            : ContainerLevelAccess.NULL;

        addKilnSlots();
        addPlayerSlots(playerInventory);
        addDataSlots(this.data);
    }

    private void addKilnSlots() {
        addSlot(new KilnSlot(kiln, ClayKilnBlockEntity.SLOT_INPUT, 42, 17));
        addSlot(new KilnSlot(kiln, ClayKilnBlockEntity.SLOT_FUEL, 42, 53));
        addSlot(new OutputSlot(kiln, ClayKilnBlockEntity.SLOT_OUTPUT, 102, 35));
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

    /**
     * Vanilla's shift-click merge mutates an existing slot stack in place and then only calls
     * {@link Slot#setChanged()}. SlotItemHandler's default implementation points at a dummy
     * container, so explicitly dirty the real block entity or a partial merge can vanish on unload.
     */
    private class KilnSlot extends SlotItemHandler {
        KilnSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (blockEntity != null && blockEntity.getLevel() != null
                && !blockEntity.getLevel().isClientSide) {
                blockEntity.setChanged();
            }
        }
    }

    /** Nothing may be put into the output slot, and taking from it is one-way. */
    private class OutputSlot extends KilnSlot {
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

    /** Kiln heat progress from 0.0 (cold) to 1.0 (blazing). */
    public float heatProgress() {
        return Math.min(1.0F, (float) data.get(ClayKilnBlockEntity.DATA_HEAT) / ClayKilnBlockEntity.MAX_HEAT);
    }

    public int heatPercent() {
        return Math.round(heatProgress() * 100.0F);
    }

    public HeatState heatState() {
        return HeatState.fromProgress(heatProgress());
    }

    public enum HeatState {
        COLD("container.swindustry.clay_kiln.heat.cold"),
        WARM("container.swindustry.clay_kiln.heat.warm"),
        HOT("container.swindustry.clay_kiln.heat.hot"),
        BLAZING("container.swindustry.clay_kiln.heat.blazing");

        private final String translationKey;

        HeatState(String translationKey) {
            this.translationKey = translationKey;
        }

        public net.minecraft.network.chat.Component label() {
            return net.minecraft.network.chat.Component.translatable(translationKey);
        }

        public static HeatState fromProgress(float progress) {
            if (progress >= 0.75F) {
                return BLAZING;
            } else if (progress >= 0.50F) {
                return HOT;
            } else if (progress >= 0.25F) {
                return WARM;
            }
            return COLD;
        }
    }

    public boolean isStructureFormed() {
        return data.get(ClayKilnBlockEntity.DATA_FORMED) != 0;
    }

    public KilnStatus status() {
        return KilnStatus.byId(data.get(ClayKilnBlockEntity.DATA_STATUS));
    }

    public int tier() {
        return data.get(ClayKilnBlockEntity.DATA_TIER);
    }

    public int fuelTicksRemaining() {
        return Math.max(0, data.get(ClayKilnBlockEntity.DATA_FUEL_SECONDS)) * 20;
    }

    public int cookPercent() {
        return Math.round(cookProgress() * 100.0F);
    }

    // ------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        // Vanilla furnace semantics: type-checked against live world state, so a chunk reload
        // that recreates the block entity no longer closes the screen.
        return stillValid(access, player, ModBlocks.CLAY_KILN_PORT.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
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
        } else if (blockEntity != null && blockEntity.canProcess(stack)) {
            // Recipe inputs win over fuel. Logs can be both, and the kiln's charcoal progression
            // is much less frustrating when shift-click sends them to the input by default.
            if (!moveItemStackTo(stack, ClayKilnBlockEntity.SLOT_INPUT,
                ClayKilnBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getBurnTime(RecipeType.SMELTING) > 0) {
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
