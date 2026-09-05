package com.jokerdayn.swindustry.multiblock;

import com.jokerdayn.swindustry.multiblock.network.KilnStructurePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for the block entity that owns a machine.
 *
 * <p>It keeps the {@link MultiblockInstance} produced the last time the pattern was checked, or
 * {@code null} when the machine is not assembled, plus a transient flag while validation is
 * waiting on an unloaded chunk. Neither is written to disk — on load the machine checks itself
 * again, so a world edited while the server was down can never leave it believing in missing
 * blocks.</p>
 *
 * <h2>When the check runs</h2>
 * <ul>
 *   <li>on a schedule, every {@link #revalidateInterval()} ticks, driven by the subclass ticker;</li>
 *   <li>immediately when one of the machine's own blocks is broken — see
 *       {@link MultiblockPatterns#invalidateAround};</li>
 *   <li>immediately whenever a subclass asks, for instance when a player opens the interface.</li>
 * </ul>
 *
 * <p>A check is a few dozen block-state reads inside already-loaded chunks, so running one per
 * second per machine costs nothing measurable. What it buys is that a machine can never keep
 * working after being taken apart, however it was taken apart.</p>
 */
public abstract class MultiblockControllerEntity extends BlockEntity {

    @Nullable
    private MultiblockInstance formed;

    /** True while validation is paused because at least one required chunk is unavailable. */
    private boolean validationDeferred;

    /** Game time of the next scheduled check; {@link Long#MIN_VALUE} forces one immediately. */
    private long nextCheckAt = Long.MIN_VALUE;

    /** False until this session's first conclusive structure check has completed. */
    private boolean firstValidationDone;

    protected MultiblockControllerEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The shape this machine has to match. */
    protected abstract MultiblockPattern pattern();

    /**
     * Which way the controller faces, pointing out of the machine. Return {@code null} if the state
     * does not carry a facing yet, which happens for a tick while a block is being replaced.
     */
    @Nullable
    protected abstract Direction controllerFacing(BlockState state);

    /** How many ticks a successful or failed check stays trusted. */
    protected int revalidateInterval() {
        return 20;
    }

    public boolean isFormed() {
        return formed != null && !validationDeferred;
    }

    /** Exposed read-only for blueprint tools; subclasses still own the pattern declaration. */
    public final MultiblockPattern structurePattern() {
        return pattern();
    }

    /** The current outward-facing direction used to orient a blueprint projection. */
    @Nullable
    public final Direction structureFacing() {
        return controllerFacing(getBlockState());
    }

    /** Every meaningful cell resolved against the current world for goggles and diagnostics. */
    public final List<MultiblockPattern.InspectionCell> inspectStructure() {
        if (level == null) {
            return List.of();
        }
        Direction facing = structureFacing();
        return facing == null ? List.of() : pattern().inspect(level, worldPosition, facing);
    }

    public final List<MultiblockPattern.Mismatch> structureMismatches() {
        if (level == null) {
            return List.of();
        }
        Direction facing = structureFacing();
        return facing == null ? List.of() : pattern().mismatches(level, worldPosition, facing, 0);
    }

    @Nullable
    public MultiblockInstance instance() {
        return isFormed() ? formed : null;
    }

    /** Drops the cached answer so the next check runs the pattern again. */
    public void invalidateStructure() {
        nextCheckAt = Long.MIN_VALUE;
    }

    /**
     * Runs the pattern now, regardless of the schedule.
     *
     * @return whether the machine is assembled
     */
    public boolean revalidate() {
        if (level == null) {
            return false;
        }
        nextCheckAt = level.getGameTime() + revalidateInterval();

        Direction facing = controllerFacing(getBlockState());
        MultiblockInstance previous = formed;
        MultiblockInstance next = null;
        if (facing != null) {
            MultiblockPattern.MatchResult result = pattern().evaluate(level, worldPosition, facing);
            if (!result.conclusive()) {
                // Keep the last verified instance only as a cache. isFormed() remains false and the
                // machine pauses until every required chunk can be checked again.
                validationDeferred = true;
                return false;
            }
            next = result.instance().orElse(null);
        }
        validationDeferred = false;
        formed = next;

        // Ownership registry: written only from verified instances so break events stay a single
        // map hit, and released the moment the machine stops matching or is removed.
        if (previous != null && previous != next) {
            MultiblockPatterns.release(this, previous);
        }
        if (next != null) {
            MultiblockPatterns.claim(this, next);
        }

        boolean firstValidation = !firstValidationDone;
        firstValidationDone = true;

        // Callbacks fire on the load-time transition too: derived state such as the kiln's
        // cured flag is recomputed from the actual walls instead of trusting saved NBT.
        if (formed != null && previous == null) {
            onFormed(formed);
            setChanged();
        } else if (formed == null && previous != null) {
            onUnformed();
            setChanged();
        } else if (formed != null) {
            onRevalidated(formed);
        } else if (firstValidation && formed == null) {
            // Loaded into a conclusively broken structure: operational state that survived the
            // save belonged to a machine that no longer exists.
            onLoadedBroken();
        }
        // Server-authoritative verdict push: broken machines broadcast their disagreement list
        // (≤1 Hz each); a fresh formation clears any stale ghost cache on clients.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (formed == null && !validationDeferred) {
                broadcastVerdict(serverLevel);
            } else if (previous == null && formed != null) {
                send(new com.jokerdayn.swindustry.multiblock.network.KilnStructurePayload(
                    worldPosition.immutable(), true, false, java.util.List.of()), serverLevel);
            }
        }
        return formed != null;
    }

    private void broadcastVerdict(net.minecraft.server.level.ServerLevel serverLevel) {
        Direction facing = controllerFacing(getBlockState());
        List<KilnStructurePayload.Cell> cells = new ArrayList<>();
        if (facing != null) {
            for (MultiblockPattern.InspectionCell cell : pattern().inspect(level, worldPosition, facing)) {
                if (!cell.matches()) {
                    cells.add(new KilnStructurePayload.Cell(
                        cell.pos().asLong(), cell.expected().role().ordinal()));
                }
            }
        }
        send(new KilnStructurePayload(worldPosition.immutable(), false, validationDeferred, cells), serverLevel);
    }

    /** Direct-to-trackers send bounded to the same 8-block radius the preview itself uses. */
    private void send(KilnStructurePayload payload, net.minecraft.server.level.ServerLevel serverLevel) {
        double centerX = worldPosition.getX() + 0.5;
        double centerZ = worldPosition.getZ() + 0.5;
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (player.level() == serverLevel
                && player.distanceToSqr(centerX, worldPosition.getY() + 0.5, centerZ) <= 64.0 * 64.0) {
                player.connection.send(payload);
            }
        }
    }

    /**
     * Runs the pattern only if the cached answer has expired. This is what a ticker should call.
     *
     * @return whether the machine is assembled
     */
    public boolean revalidateIfStale() {
        if (level == null) {
            return false;
        }
        if (level.getGameTime() >= nextCheckAt) {
            return revalidate();
        }
        return isFormed();
    }

    /** Called once when the machine goes from taken apart to assembled. */
    protected void onFormed(MultiblockInstance instance) {}

    /** Called after a formed machine is successfully checked again. */
    protected void onRevalidated(MultiblockInstance instance) {}

    /** Called once when the machine goes from assembled to taken apart. */
    protected void onUnformed() {}

    /**
     * Called once when this session's first conclusive validation finds the machine not assembled.
     * A teardown that happened while nobody was watching leaves operational state behind in the
     * save; subclasses drop it here so a rebuilt shell cannot inherit a burn it never lit.
     */
    protected void onLoadedBroken() {}

    @Override
    public void setRemoved() {
        // Deliberately does not fire onUnformed. That callback exists for a machine that came apart
        // while it still exists — a subclass will want to stop its fire and clear its state, which
        // usually means touching its own block. There is no block left to touch here.
        MultiblockPatterns.release(this, formed);
        formed = null;
        validationDeferred = false;
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        // The world around a freshly loaded chunk may differ from the world this machine last saw,
        // so nothing is trusted until the pattern has been run again.
        invalidateStructure();
    }

    /**
     * Convenience for a controller block's {@code neighborChanged}: something next to us moved, so
     * whatever we believed about the structure is no longer worth trusting.
     */
    public static void invalidateAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MultiblockControllerEntity controller) {
            controller.invalidateStructure();
        }
    }
}
