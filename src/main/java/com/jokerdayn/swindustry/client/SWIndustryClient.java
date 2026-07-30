package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.progression.ClientProgression;
import com.jokerdayn.swindustry.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring for the kiln screen and progression state lifecycle. */
public final class SWIndustryClient {

    private SWIndustryClient() {}

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.CLAY_KILN.get(), ClayKilnScreen::new);
        }
    }

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
    public static final class GameBus {

        private GameBus() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // Otherwise the next world would inherit the last one's progression state.
            ClientProgression.reset();
        }
    }
}
