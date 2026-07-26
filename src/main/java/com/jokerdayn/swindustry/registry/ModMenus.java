package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Screens the player can open. */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, SWIndustry.MODID);

    /**
     * Built through {@link IMenuTypeExtension#create} so the opening packet can carry the kiln's
     * position, which is how the client half finds the same block entity the server is using.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ClayKilnMenu>> CLAY_KILN =
        MENUS.register("clay_kiln", () -> IMenuTypeExtension.create(ClayKilnMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
