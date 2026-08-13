package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What other machinery is allowed to do to ours.
 *
 * <p>The kiln's inventory is published on the loading port — the same hole a player reaches into.
 * A hopper under the port unloads it, a hopper beside it feeds it, and when Create arrives it has
 * something to point a chute at. Making that work now costs three lines and saves rebuilding the
 * kiln's insides later.</p>
 */
@EventBusSubscriber(modid = SWIndustry.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.CLAY_KILN.get(),
            (blockEntity, side) -> blockEntity.itemHandler(side));
    }
}
