package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.registry.ModItems;
import com.jokerdayn.swindustry.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Clips a reusable blueprint into the goggles without consuming the sheet. */
public final class GoggleCalibrationRecipe extends CustomRecipe {

    public GoggleCalibrationRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair pair = find(input);
        if (pair == null) {
            return ItemStack.EMPTY;
        }
        ItemStack calibrated = pair.goggles().copyWithCount(1);
        PrimitiveEngineerGogglesItem.selectBlueprint(calibrated, pair.blueprint().blueprintId());
        return calibrated;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.getItem() instanceof MultiblockBlueprintItem) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GOGGLE_CALIBRATION_SERIALIZER.get();
    }

    private static Pair find(CraftingInput input) {
        ItemStack goggles = ItemStack.EMPTY;
        MultiblockBlueprintItem blueprint = null;
        int found = 0;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            found++;
            if (stack.is(ModItems.PRIMITIVE_ENGINEER_GOGGLES) && goggles.isEmpty()) {
                goggles = stack;
            } else if (stack.getItem() instanceof MultiblockBlueprintItem blueprintItem && blueprint == null) {
                blueprint = blueprintItem;
            } else {
                return null;
            }
        }

        return found == 2 && !goggles.isEmpty() && blueprint != null
            ? new Pair(goggles, blueprint)
            : null;
    }

    private record Pair(ItemStack goggles, MultiblockBlueprintItem blueprint) {}
}
