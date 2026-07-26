package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.progression.ClientProgression;
import com.jokerdayn.swindustry.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring: the kiln screen, the objective panel, and the tick that drives its fade. */
public final class SWIndustryClient {

    private SWIndustryClient() {}

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.CLAY_KILN.get(), ClayKilnScreen::new);
        }

        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(SWIndustry.id("progression"), new ProgressionHudLayer());
        }
    }

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT)
    public static final class GameBus {

        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ClientProgression.tick();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // Otherwise the next world would open showing whatever the last one ended on.
            ClientProgression.reset();
        }
    }
}
