package com.jokerdayn.swindustry.multiblock;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The registry of every machine shape in the mod, and the ownership index that ties broken
 * blocks back to their machines.
 *
 * <p>While a structure is verified — and only then — its controller records the exact packed
 * position of every wall in {@link #OWNERSHIP}. When a part block is mined,
 * {@link #invalidateAround} is therefore a single map hit: no pattern enumeration, no candidate
 * geometry, no unloaded-chunk guards. Entries live exactly as long as verification does; the
 * scheduled revalidation remains as a safety net for anything exotic.</p>
 */
public final class MultiblockPatterns {

    private static final List<MultiblockPattern> REGISTERED = new ArrayList<>();

    /** Packed wall position -> controller currently verified to own that block. Server thread only. */
    private static final Long2ObjectOpenHashMap<MultiblockControllerEntity> OWNERSHIP =
        new Long2ObjectOpenHashMap<>();

    /** Packed cavity position -> controller that needs it kept clear, for neighbour pushes. */
    private static final Long2ObjectOpenHashMap<MultiblockControllerEntity> CAVITY_OWNERS =
        new Long2ObjectOpenHashMap<>();

    private MultiblockPatterns() {}

    /** Registers a pattern for diagnostics and previews, returning it for field initialisers. */
    public static MultiblockPattern register(MultiblockPattern pattern) {
        REGISTERED.add(pattern);
        return pattern;
    }

    /**
     * Swaps a rebuilt pattern in place. Datapack reloads compile fresh geometry; replacing by
     * position keeps identity-based comparisons working against the newest instance.
     */
    public static void replace(MultiblockPattern previous, MultiblockPattern replacement) {
        int index = REGISTERED.indexOf(previous);
        if (index >= 0) {
            REGISTERED.set(index, replacement);
        } else {
            REGISTERED.add(replacement);
        }
    }

    public static List<MultiblockPattern> all() {
        return List.copyOf(REGISTERED);
    }

    /**
     * Records exact wall membership straight from a freshly verified instance. Called after
     * every successful revalidation.
     */
    public static void claim(MultiblockControllerEntity controller, MultiblockInstance instance) {
        OWNERSHIP.put(instance.controllerPos().asLong(), controller);
        for (BlockPos wall : instance.walls()) {
            OWNERSHIP.put(wall.asLong(), controller);
        }
        for (BlockPos cell : instance.cavity()) {
            CAVITY_OWNERS.put(cell.asLong(), controller);
        }
    }

    /** Drops this controller's entries without touching a newer claimant's, if any. */
    public static void release(@Nullable MultiblockControllerEntity controller,
                               @Nullable MultiblockInstance instance) {
        if (controller == null || instance == null) {
            return;
        }
        OWNERSHIP.remove(instance.controllerPos().asLong(), controller);
        for (BlockPos wall : instance.walls()) {
            OWNERSHIP.remove(wall.asLong(), controller);
        }
        for (BlockPos cell : instance.cavity()) {
            CAVITY_OWNERS.remove(cell.asLong(), controller);
        }
    }

    /**
     * Push hook from part blocks' {@code neighborChanged}: something changed at {@code changedPos},
     * which may be one of our interior cells — most importantly, fluid may have entered.
     */
    public static void notifyNeighborChanged(Level level, BlockPos changedPos) {
        if (level.isClientSide) {
            return;
        }
        MultiblockControllerEntity owner = CAVITY_OWNERS.get(changedPos.asLong());
        if (owner != null && !owner.isRemoved() && owner.getLevel() == level) {
            owner.invalidateStructure();
        }
    }

    /**
     * Tells the machine that owns {@code pos} to recheck itself. The old block state is kept in
     * the signature for callers' readability but is no longer needed: ownership comes from the
     * index, not from matching geometry after the fact.
     */
    public static void invalidateAround(Level level, BlockPos pos, BlockState oldState) {
        if (level.isClientSide) {
            return;
        }
        MultiblockControllerEntity owner = OWNERSHIP.get(pos.asLong());
        if (owner != null && !owner.isRemoved() && owner.getLevel() == level) {
            owner.invalidateStructure();
        }
    }
}
