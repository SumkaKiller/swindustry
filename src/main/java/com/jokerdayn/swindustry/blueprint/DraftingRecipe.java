package com.jokerdayn.swindustry.blueprint;

import com.jokerdayn.swindustry.registry.ModItems;
import com.jokerdayn.swindustry.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** Maps a reusable machine sample in the drafting table to its blueprint sheet. */
public record DraftingRecipe(Ingredient reference, ItemStack result) implements Recipe<SingleRecipeInput> {

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return reference.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.DRAFTING_TABLE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DRAFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.DRAFTING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<DraftingRecipe> {

        private static final MapCodec<DraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("reference").forGetter(DraftingRecipe::reference),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(DraftingRecipe::result)
        ).apply(instance, DraftingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, DraftingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, DraftingRecipe::reference,
                ItemStack.STREAM_CODEC, DraftingRecipe::result,
                DraftingRecipe::new);

        @Override
        public MapCodec<DraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
