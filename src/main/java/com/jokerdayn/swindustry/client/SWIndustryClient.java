package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.registry.ModMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

/** Client-only wiring for workstation screens and debug shortcuts. */
public final class SWIndustryClient {

    public static final KeyMapping SPAWN_KILN_KEY = new KeyMapping(
        "key.swindustry.spawn_kiln",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F12,
        "key.categories.swindustry"
    );

    private SWIndustryClient() {}

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.CLAY_KILN.get(), ClayKilnScreen::new);
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(SPAWN_KILN_KEY);
        }
    }

    @EventBusSubscriber(modid = SWIndustry.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class GameBus {

        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (SPAWN_KILN_KEY.consumeClick() && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("spawn_kiln");
            }
        }
    }
}
