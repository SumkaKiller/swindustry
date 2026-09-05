package com.jokerdayn.swindustry.client;

import com.jokerdayn.swindustry.multiblock.network.KilnStructurePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
    private static final int MAX_ENTRIES = 1024;

    /** A position is only unique inside one dimension. Main thread only. */
    private record PositionKey(ResourceKey<Level> dimension, long packedPos) {}

    /** Dimension + packed controller position -> latest verdict. Main thread only. */
    private static final Map<PositionKey, Verdict> LATEST = new HashMap<>();
    private static ClientLevel activeLevel;

    private StructureVerdicts() {}

    public static void handle(KilnStructurePayload payload) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        pruneExpired(now);
        PositionKey key = new PositionKey(level.dimension(), payload.pos().asLong());
        if (payload.formed()) {
            LATEST.remove(key);
            return;
        }
        LATEST.put(key,
            new Verdict(payload.deferred(), payload.cells(), now + TTL_TICKS));
        trimToLimit();
    }

    /** Cells the server disagrees with at this controller, or {@code null} when unknown/stale. */
    public static List<KilnStructurePayload.Cell> lookup(BlockPos controllerPos) {
        ClientLevel level = currentLevel();
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        pruneExpired(now);
        Verdict verdict = LATEST.get(new PositionKey(level.dimension(), controllerPos.asLong()));
        return verdict == null ? null : verdict.cells();
    }

    public static void clear() {
        LATEST.clear();
        activeLevel = null;
    }

    private static ClientLevel currentLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != activeLevel) {
            LATEST.clear();
            activeLevel = level;
        }
        return level;
    }

    private static void pruneExpired(long now) {
        Iterator<Map.Entry<PositionKey, Verdict>> iterator = LATEST.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().getValue().expiresAt()) {
                iterator.remove();
            }
        }
    }

    private static void trimToLimit() {
        while (LATEST.size() > MAX_ENTRIES) {
            PositionKey oldestKey = null;
            long oldestExpiry = Long.MAX_VALUE;
            for (Map.Entry<PositionKey, Verdict> entry : LATEST.entrySet()) {
                if (entry.getValue().expiresAt() < oldestExpiry) {
                    oldestExpiry = entry.getValue().expiresAt();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            LATEST.remove(oldestKey);
        }
    }
}
