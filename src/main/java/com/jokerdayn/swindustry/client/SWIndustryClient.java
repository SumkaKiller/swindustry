package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring for workstation screens. */
public final class SWIndustryClient {

    private SWIndustryClient() {}

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.CLAY_KILN.get(), ClayKilnScreen::new);
            event.register(ModMenus.DRAFTING_TABLE.get(), DraftingTableScreen::new);
        }
    }

}
