package com.jokerdayn.swindustry;

import com.jokerdayn.swindustry.progression.ProgressionEvents;
import com.jokerdayn.swindustry.progression.ProgressionNetwork;
import com.jokerdayn.swindustry.registry.ModArmorMaterials;
import com.jokerdayn.swindustry.registry.ModAttachments;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModCreativeTabs;
import com.jokerdayn.swindustry.registry.ModItems;
import com.jokerdayn.swindustry.registry.ModMenus;
import com.jokerdayn.swindustry.registry.ModRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod entry point for SW: Industry — the multiblock half of the Sea Worthy pack.
 *
 * <p>Deliberately thin. The reusable machinery lives in {@code multiblock}, content registration in
 * {@code registry}, and each machine owns its own package. Everything below is wiring.</p>
 *
 * <p>This mod loads after SW: Worldgen Core and reaches across to it only through resource
 * locations and tags — never through a compiled reference — so the two jars can be built,
 * versioned and shipped independently.</p>
 */
@Mod(SWIndustry.MODID)
public class SWIndustry {

    public static final String MODID = "swindustry";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The sibling mod that owns the ocean, the boulders and the bronze ore. */
    public static final String WORLDGEN_CORE = "swworldgencore";

    public SWIndustry(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModArmorMaterials.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(ProgressionNetwork::register);
        NeoForge.EVENT_BUS.register(ProgressionEvents.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** A resource location in SW: Worldgen Core's namespace. */
    public static ResourceLocation coreId(String path) {
        return ResourceLocation.fromNamespaceAndPath(WORLDGEN_CORE, path);
    }
}
