package com.jokerdayn.swindustry.progression;

import com.jokerdayn.swindustry.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps each player's objective up to date and tells their client about it.
 *
 * <h2>Why polling</h2>
 *
 * <p>Objectives are checked once a second rather than hooked onto pickup, craft and smelt events.
 * Those events miss too much — pulling a log out of a chest, being handed charcoal by another
 * player, {@code /give} during testing — and every one of those misses is a player stuck staring at
 * an objective they have already met. One pass over a 41-slot inventory per player per second is
 * far cheaper than being wrong.</p>
 */
public final class ProgressionEvents {

    /** One check a second. Fast enough that nobody notices the delay. */
    private static final int CHECK_INTERVAL = 20;

    private ProgressionEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        ProgressionState state = player.getData(ModAttachments.PROGRESSION);
        if (advance(player, state)) {
            ProgressionNetwork.sync(player, state);
        }
    }

    /**
     * Walks the player past every objective they already satisfy.
     *
     * <p>The loop matters: someone who joins carrying a full kit should end up at the right place
     * rather than one step along. It cannot run away, because {@code COMPLETE} is never
     * satisfied.</p>
     *
     * @return whether the step changed, and the client therefore needs telling
     */
    public static boolean advance(Player player, ProgressionState state) {
        ProgressionStep before = state.step();
        while (!state.step().isComplete() && state.step().isSatisfied(player, state)) {
            ProgressionStep next = state.step().next();
            state.setStep(next);
            if (player instanceof ServerPlayer server) {
                onStepReached(server, next);
            }
        }
        return state.step() != before;
    }

    private static void onStepReached(ServerPlayer player, ProgressionStep step) {
        // A short chime is the whole celebration. Anything louder would wear out over eight steps.
        player.level().playSound(null, player.blockPosition(),
            step.isComplete() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS, 0.4F, step.isComplete() ? 1.0F : 1.6F);
    }

    /**
     * Records a milestone for a player and pushes the result out.
     *
     * <p>The entry point for the two objectives that cannot be read off an inventory — call it from
     * wherever the moment actually happens.</p>
     */
    public static void record(@Nullable Player player, ProgressionMilestone milestone) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        ProgressionState state = server.getData(ModAttachments.PROGRESSION);
        boolean isNews = state.record(milestone);
        boolean moved = advance(server, state);
        if (isNews || moved) {
            server.setData(ModAttachments.PROGRESSION, state);
            ProgressionNetwork.sync(server, state);
        }
    }

    // ---- Occasions to push the current state at a client -----------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncTo(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncTo(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncTo(event.getEntity());
    }

    /**
     * Carries progress onto the fresh player object made on death or a return from the End.
     *
     * <p>The attachment is declared {@code copyOnDeath}, which covers dying; this also covers the
     * end-portal case and, more importantly, syncs the copy so the HUD does not go blank.</p>
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ProgressionState previous = event.getOriginal().getData(ModAttachments.PROGRESSION);
        ProgressionState current = event.getEntity().getData(ModAttachments.PROGRESSION);
        current.setStep(previous.step());
        current.setMilestoneBits(previous.milestoneBits());
        event.getEntity().setData(ModAttachments.PROGRESSION, current);
    }

    private static void syncTo(Player player) {
        if (player instanceof ServerPlayer server) {
            ProgressionState state = server.getData(ModAttachments.PROGRESSION);
            advance(server, state);
            ProgressionNetwork.sync(server, state);
        }
    }
}
