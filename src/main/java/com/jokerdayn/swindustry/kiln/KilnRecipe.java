package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Something only a kiln can do.
 *
 * <p>The kiln also runs ordinary furnace recipes, so this type is not for the everyday work of
 * cooking meat and melting sand. It is for the things the progression hangs on — bronze first —
 * which must be impossible anywhere else, and for the parts of a recipe a furnace has no concept
 * of.</p>
 *
 * <p>That is what {@link #minTier} is. Machines in this pack are meant to grow, and a recipe that
 * demands a better kiln than the one in front of you is how a growing machine earns its upgrade.
 * The clay kiln is tier one; a recipe asking for tier two is visible, understood, and refused.</p>
 *
 * @param ingredient  what goes in
 * @param result      what comes out
 * @param experience  experience granted when the result is taken
 * @param cookingTime ticks of continuous heat required
 * @param minTier     the lowest kiln tier that can run this
 */
public record KilnRecipe(
    Ingredient ingredient,
    ItemStack result,
    float experience,
    int cookingTime,
    int minTier,
    int requiredHeat
) implements Recipe<SingleRecipeInput> {

    /** What a raw clay kiln is worth. Every later shell material raises this. */
    public static final int TIER_CLAY = 1;

    public static final int DEFAULT_COOKING_TIME = 200;
    public static final int DEFAULT_REQUIRED_HEAT = 900;

    public KilnRecipe(Ingredient ingredient, ItemStack result, float experience, int cookingTime, int minTier) {
        this(ingredient, result, experience, cookingTime, minTier, DEFAULT_REQUIRED_HEAT);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.KILN_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.KILN_TYPE.get();
    }

    /** Whether a kiln of the given tier is good enough to run this. */
    public boolean isTierSufficient(int tier) {
        return tier >= minTier;
    }

    // ------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<KilnRecipe> {

        private static final MapCodec<KilnRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(KilnRecipe::ingredient),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(KilnRecipe::result),
            Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("experience", 0.0F)
                .forGetter(KilnRecipe::experience),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("cookingtime", DEFAULT_COOKING_TIME)
                .forGetter(KilnRecipe::cookingTime),
            Codec.intRange(TIER_CLAY, Integer.MAX_VALUE).optionalFieldOf("tier", TIER_CLAY)
                .forGetter(KilnRecipe::minTier),
            Codec.intRange(0, 2000).optionalFieldOf("heat", DEFAULT_REQUIRED_HEAT)
                .forGetter(KilnRecipe::requiredHeat)
        ).apply(instance, KilnRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, KilnRecipe> STREAM_CODEC =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, KilnRecipe::ingredient,
                ItemStack.STREAM_CODEC, KilnRecipe::result,
                ByteBufCodecs.FLOAT, KilnRecipe::experience,
                ByteBufCodecs.VAR_INT, KilnRecipe::cookingTime,
                ByteBufCodecs.VAR_INT, KilnRecipe::minTier,
                ByteBufCodecs.VAR_INT, KilnRecipe::requiredHeat,
                KilnRecipe::new);

        @Override
        public MapCodec<KilnRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, KilnRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
