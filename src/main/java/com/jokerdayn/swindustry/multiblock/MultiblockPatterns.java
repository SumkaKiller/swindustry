package com.jokerdayn.swindustry.multiblock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The list of every machine shape in the mod.
 *
 * <p>Its only job is the reverse lookup. When any block is broken, {@link #invalidateAround} asks
 * each registered pattern where a controller would have to stand for that block to have been part
 * of it, and tells any controller it finds there to check itself. That is what makes a machine fall
 * apart the instant a player mines one brick out of its wall, rather than a second later.</p>
 *
 * <p>Registering a new machine is one line at its pattern's definition site, and every part block
 * in the mod immediately covers it. Nothing else has to be wired up.</p>
 */
public final class MultiblockPatterns {

    private static final List<MultiblockPattern> REGISTERED = new ArrayList<>();

    private MultiblockPatterns() {}

    /** Registers a pattern for reverse lookup and returns it, so it can wrap a field initialiser. */
    public static MultiblockPattern register(MultiblockPattern pattern) {
        REGISTERED.add(pattern);
        return pattern;
    }

    public static List<MultiblockPattern> all() {
        return List.copyOf(REGISTERED);
    }

    /**
     * Tells every machine that could have contained {@code pos} to check itself.
     *
     * @param brokenState the state that <em>used to</em> be at {@code pos}; the world has already
     *                    moved on by the time this is called, so the old state is what decides
     *                    which patterns are worth searching
     */
    public static void invalidateAround(Level level, BlockPos pos, BlockState brokenState) {
        if (level.isClientSide) {
            return;
        }
        for (MultiblockPattern pattern : REGISTERED) {
            if (!pattern.couldBeWall(level, pos, brokenState)) {
                continue;
            }
            for (BlockPos candidate : pattern.candidateControllerPositions(pos)) {
                // Never reach into an unloaded chunk: doing so would drag it into memory just to
                // ask a question whose answer stops mattering the moment the chunk unloads again.
                if (!level.isLoaded(candidate)) {
                    continue;
                }
                if (level.getBlockEntity(candidate) instanceof MultiblockControllerEntity controller) {
                    controller.invalidateStructure();
                }
            }
        }
    }
}
