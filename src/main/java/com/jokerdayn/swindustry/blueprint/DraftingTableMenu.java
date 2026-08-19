package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Three inputs: paper, soot ink and a reusable controller sample; one blueprint output. */
public final class DraftingTableMenu extends AbstractContainerMenu {

    public static final int SLOT_PAPER = 0;
    public static final int SLOT_INK = 1;
    public static final int SLOT_REFERENCE = 2;
    public static final int SLOT_RESULT = 3;
    private static final int MACHINE_SLOTS = 4;
    private static final int INVENTORY_END = MACHINE_SLOTS + 27;
    private static final int HOTBAR_END = INVENTORY_END + 9;

    private final ContainerLevelAccess access;
    private final Level level;
    private long lastSoundTime;

    private final Container inputs = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            DraftingTableMenu.this.slotsChanged(this);
            super.setChanged();
        }
    };

    private final ResultContainer result = new ResultContainer();

    public DraftingTableMenu(int containerId, Inventory playerInventory) {
        this(com.jokerdayn.swindustry.registry.ModMenus.DRAFTING_TABLE.get(),
            containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public DraftingTableMenu(MenuType<?> menuType, int containerId, Inventory playerInventory,
                             ContainerLevelAccess access) {
        super(menuType, containerId);
        this.access = access;
        this.level = playerInventory.player.level();

        addSlot(new Slot(inputs, SLOT_PAPER, 18, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
            }
        });
        addSlot(new Slot(inputs, SLOT_INK, 45, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SOOT.get());
            }
        });
        addSlot(new Slot(inputs, SLOT_REFERENCE, 82, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isDraftingReference(stack);
            }
        });
        addSlot(new Slot(result, SLOT_RESULT, 142, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                ItemStack reference = inputs.getItem(SLOT_REFERENCE);
                result.awardUsedRecipes(player, List.of(reference));
                DraftingTableMenu.this.slots.get(SLOT_PAPER).remove(1);
                DraftingTableMenu.this.slots.get(SLOT_INK).remove(1);
                stack.onCraftedBy(player.level(), player, stack.getCount());
                access.execute((level, pos) -> {
                    long gameTime = level.getGameTime();
                    if (lastSoundTime != gameTime) {
                        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                            SoundSource.BLOCKS, 0.8F, 0.9F);
                        lastSoundTime = gameTime;
                    }
                });
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                    8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.DRAFTING_TABLE.get());
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack paper = inputs.getItem(SLOT_PAPER);
        ItemStack ink = inputs.getItem(SLOT_INK);
        ItemStack reference = inputs.getItem(SLOT_REFERENCE);
        if (paper.isEmpty() || ink.isEmpty() || reference.isEmpty()) {
            clearResult();
            return;
        }

        Optional<MultiblockBlueprints.Definition> definition =
            MultiblockBlueprints.byControllerSample(reference);
        if (definition.isEmpty()) {
            clearResult();
            return;
        }

        // Paper and soot are spent on take; the sample itself is returned untouched.
        ItemStack output = MultiblockBlueprints.blueprintStack(definition.get());
        if (output.isEmpty() || !output.isItemEnabled(level.enabledFeatures())) {
            clearResult();
            return;
        }
        setResult(output);
    }

    private boolean isDraftingReference(ItemStack stack) {
        return MultiblockBlueprints.byControllerSample(stack).isPresent();
    }

    private void clearResult() {
        result.setRecipeUsed(null);
        setResult(ItemStack.EMPTY);
    }

    private void setResult(ItemStack stack) {
        ItemStack previous = result.getItem(SLOT_RESULT);
        if (!ItemStack.matches(previous, stack)) {
            result.setItem(SLOT_RESULT, stack);
            broadcastChanges();
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
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
        if (index == SLOT_RESULT) {
            original.onCraftedBy(player.level(), player, original.getCount());
            if (!moveItemStackTo(stack, MACHINE_SLOTS, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.PAPER)) {
            if (!moveItemStackTo(stack, SLOT_PAPER, SLOT_PAPER + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.SOOT.get())) {
            if (!moveItemStackTo(stack, SLOT_INK, SLOT_INK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isDraftingReference(stack)) {
            if (!moveItemStackTo(stack, SLOT_REFERENCE, SLOT_REFERENCE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (index < INVENTORY_END) {
                if (!moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, MACHINE_SLOTS, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        broadcastChanges();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        result.removeItemNoUpdate(SLOT_RESULT);
        access.execute((level, pos) -> clearContainer(player, inputs));
    }
}
