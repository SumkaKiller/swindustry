package com.jokerdayn.swindustry.multiblock.network;

import com.jokerdayn.swindustry.SWIndustry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-authoritative structure verdict for one machine.
 *
 * <p>Sent when a machine is unformed (and on formation, as an explicit clear), carrying every
 * cell the server found wrong together with its expected {@link com.jokerdayn.swindustry.multiblock.BlockMatcher.Role}
 * ordinal. The client renders straight from this instead of re-walking patterns at frame rate,
 * and never has to assume its jar holds the same geometry as the server's.</p>
 */
public record KilnStructurePayload(BlockPos pos, boolean formed, boolean deferred,
                                   List<Cell> cells) implements CustomPacketPayload {

    /** One disagreed cell: packed position plus expected {@code Role} ordinal. */
    public record Cell(long packed, int roleOrdinal) {}

    public static final Type<KilnStructurePayload> TYPE =
        new Type<>(SWIndustry.id("structure_verdict"));

    public static final StreamCodec<FriendlyByteBuf, KilnStructurePayload> STREAM_CODEC =
        StreamCodec.ofMember(KilnStructurePayload::write, KilnStructurePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(formed);
        buf.writeBoolean(deferred);
        buf.writeVarInt(cells.size());
        for (Cell cell : cells) {
            buf.writeLong(cell.packed());
            buf.writeVarInt(cell.roleOrdinal());
        }
    }

    private static KilnStructurePayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean formed = buf.readBoolean();
        boolean deferred = buf.readBoolean();
        int count = buf.readVarInt();
        List<Cell> cells = new ArrayList<>(Math.min(count, 128));
        for (int i = 0; i < count; i++) {
            cells.add(new Cell(buf.readLong(), buf.readVarInt()));
        }
        return new KilnStructurePayload(pos.immutable(), formed, deferred, List.copyOf(cells));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
