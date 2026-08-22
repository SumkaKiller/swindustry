package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's single creative tab, laid out in the order the game teaches it. */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SWIndustry.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + SWIndustry.MODID))
            .icon(() -> new ItemStack(ModItems.CLAY_KILN_PORT.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.PRIMITIVE_CAMPFIRE.get());
                output.accept(ModItems.SOOT.get());
                output.accept(ModItems.PRIMITIVE_ENGINEER_GOGGLES.get());
                output.accept(ModItems.RAW_CLAY_BRICKS.get());
                output.accept(ModItems.CLAY_BRICKS.get());
                output.accept(ModItems.CLAY_KILN_PORT.get());
                output.accept(ModItems.BRONZE_SWORD.get());
                output.accept(ModItems.BRONZE_PICKAXE.get());
                output.accept(ModItems.BRONZE_AXE.get());
                output.accept(ModItems.BRONZE_SHOVEL.get());
                output.accept(ModItems.BRONZE_HOE.get());
                output.accept(ModItems.BRONZE_HELMET.get());
                output.accept(ModItems.BRONZE_CHESTPLATE.get());
                output.accept(ModItems.BRONZE_LEGGINGS.get());
                output.accept(ModItems.BRONZE_BOOTS.get());
            })
            .build()
    );

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
