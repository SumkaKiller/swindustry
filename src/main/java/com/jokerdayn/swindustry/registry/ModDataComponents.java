package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data carried by item stacks rather than by a world block or entity. */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SWIndustry.MODID);

    /** Which reusable blueprint is currently clipped into a pair of engineer's goggles. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> SELECTED_BLUEPRINT =
        COMPONENTS.registerComponentType("selected_blueprint", builder -> builder
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .cacheEncoding());

    private ModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
