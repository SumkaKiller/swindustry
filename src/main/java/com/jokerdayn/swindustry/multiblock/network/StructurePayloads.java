package com.jokerdayn.swindustry.multiblock.network;

import com.jokerdayn.swindustry.SWIndustry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's client-bound payloads; protocol version bumped on any field change. */
@EventBusSubscriber(modid = SWIndustry.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class StructurePayloads {

    private StructurePayloads() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
            KilnStructurePayload.TYPE,
            KilnStructurePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                com.jokerdayn.swindustry.client.StructureVerdicts.handle(payload)));
    }
}
