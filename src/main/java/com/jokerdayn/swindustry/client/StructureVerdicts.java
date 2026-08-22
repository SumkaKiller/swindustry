package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.multiblock.network.KilnStructurePayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-side store of the latest server structure verdicts.
 *
 * <p>Entries expire quickly: the server re-sends while a machine stays broken and pushes an
 * explicit empty verdict on formation, so a stale ghost cannot outlive its truth for more than
 * a couple of seconds even if a packet is lost.</p>
 */
public final class StructureVerdicts {

    public record Verdict(boolean deferred, List<KilnStructurePayload.Cell> cells, long expiresAt) {}

    private static final long TTL_TICKS = 100;

    /** Packed controller position -> latest verdict. Main thread only. */
    private static final Long2ObjectOpenHashMap<Verdict> LATEST = new Long2ObjectOpenHashMap<>();

    private StructureVerdicts() {}

    public static void handle(KilnStructurePayload payload) {
        long now = currentTick();
        if (payload.formed()) {
            LATEST.remove(payload.pos().asLong());
            return;
        }
        LATEST.put(payload.pos().asLong(),
            new Verdict(payload.deferred(), payload.cells(), now + TTL_TICKS));
    }

    /** Cells the server disagrees with at this controller, or {@code null} when unknown/stale. */
    public static List<KilnStructurePayload.Cell> lookup(BlockPos controllerPos) {
        Verdict verdict = LATEST.get(controllerPos.asLong());
        if (verdict == null || currentTick() > verdict.expiresAt()) {
            return null;
        }
        return verdict.cells();
    }

    public static void clear() {
        LATEST.clear();
    }

    private static long currentTick() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }
}
