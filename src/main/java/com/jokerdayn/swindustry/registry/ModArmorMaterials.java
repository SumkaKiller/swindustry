package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Bronze plate.
 *
 * <p>Sits between chainmail and iron: one point better than chain across the body, one short of
 * iron everywhere, and rather less hard-wearing than either. Good enough to survive the island,
 * not good enough to stop looking for something better.</p>
 *
 * <pre>
 *            boots  legs  chest  helm   durability
 *   chain        1     4      5     2         x15
 *   bronze       2     4      5     2         x13
 *   iron         2     5      6     2         x15
 * </pre>
 */
public final class ModArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, SWIndustry.MODID);

    /** Multiplier on each slot's base durability. Iron is 15. */
    public static final int BRONZE_DURABILITY = 13;

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BRONZE = ARMOR_MATERIALS.register(
        "bronze",
        () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), defense -> {
                defense.put(ArmorItem.Type.BOOTS, 2);
                defense.put(ArmorItem.Type.LEGGINGS, 4);
                defense.put(ArmorItem.Type.CHESTPLATE, 5);
                defense.put(ArmorItem.Type.HELMET, 2);
                defense.put(ArmorItem.Type.BODY, 4);
            }),
            10,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(ModTags.Items.BRONZE_INGOTS),
            // Resolves to assets/swindustry/textures/models/armor/bronze_layer_1.png (outer) and
            // _layer_2.png (the leggings layer).
            List.of(new ArmorMaterial.Layer(SWIndustry.id("bronze"))),
            0.0F,
            0.0F
        )
    );

    private ModArmorMaterials() {}

    public static Holder<ArmorMaterial> bronze() {
        return BRONZE;
    }

    public static void register(IEventBus modEventBus) {
        ARMOR_MATERIALS.register(modEventBus);
    }
}
