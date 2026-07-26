package com.jokerdayn.swindustry.progression;

import com.jokerdayn.swindustry.SWIndustry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Getting the current objective from the server, which decides it, to the client, which draws it.
 *
 * <p>A hand-written payload rather than attachment auto-sync: a player is not tracked by their own
 * client's entity tracker, and this HUD exists to be seen by exactly that player.</p>
 */
public final class ProgressionNetwork {

    private ProgressionNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            SyncPayload.TYPE,
            SyncPayload.STREAM_CODEC,
            ProgressionNetwork::handleOnClient);
    }

    /** Sends a player their own progress. */
    public static void sync(ServerPlayer player, ProgressionState state) {
        PacketDistributor.sendToPlayer(player,
            new SyncPayload(state.step().ordinal(), state.milestoneBits()));
    }

    private static void handleOnClient(SyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientProgression.accept(
            ProgressionStep.byOrdinal(payload.stepOrdinal()), payload.milestoneBits()));
    }

    /**
     * @param stepOrdinal   the current step, by position in {@link ProgressionStep}
     * @param milestoneBits packed {@link ProgressionMilestone} flags
     */
    public record SyncPayload(int stepOrdinal, int milestoneBits) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncPayload> TYPE =
            new CustomPacketPayload.Type<>(SWIndustry.id("progression"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::stepOrdinal,
                ByteBufCodecs.VAR_INT, SyncPayload::milestoneBits,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
