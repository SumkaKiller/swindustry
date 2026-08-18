package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.SWIndustry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Binds machine geometry to live pack resources.
 *
 * <p>{@link ServerAboutToStartEvent} performs the first load through the server's resource
 * manager; {@link AddReloadListenerEvent} recompiles every shape on each datapack reload. A
 * reload that changes geometry does not need special machine handling: controllers already
 * revalidate against {@code KilnPatterns#clayKiln()} on their schedule, so a running kiln simply
 * pauses or unforms itself against the new shape within a second.</p>
 */
@EventBusSubscriber(modid = SWIndustry.MODID)
public final class KilnPatternReloadHook {

    private KilnPatternReloadHook() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        KilnPatterns.initialize(event.getServer().getResourceManager());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                                  ProfilerFiller preparations, ProfilerFiller apply,
                                                  Executor prepExecutor, Executor applyExecutor) {
                return CompletableFuture.runAsync(() -> { }, prepExecutor)
                    .thenCompose(barrier::wait)
                    .thenRunAsync(() -> KilnPatterns.initialize(resources), applyExecutor);
            }
        });
    }
}
