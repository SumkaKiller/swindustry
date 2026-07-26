package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.KilnRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The kiln's own recipe type.
 *
 * <p>Separate from {@code minecraft:smelting} so that a recipe can be made impossible outside a
 * kiln, and so that a recipe can carry things a furnace has no word for — a required machine tier,
 * to begin with.</p>
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, SWIndustry.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, SWIndustry.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<KilnRecipe>> KILN_TYPE =
        RECIPE_TYPES.register("kiln", () -> RecipeType.simple(SWIndustry.id("kiln")));

    public static final DeferredHolder<RecipeSerializer<?>, KilnRecipe.Serializer> KILN_SERIALIZER =
        RECIPE_SERIALIZERS.register("kiln", KilnRecipe.Serializer::new);

    private ModRecipes() {}

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
