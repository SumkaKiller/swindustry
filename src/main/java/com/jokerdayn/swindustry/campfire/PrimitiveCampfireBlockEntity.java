package com.jokerdayn.swindustry.campfire;

import com.jokerdayn.swindustry.Config;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The clock on a burning campfire.
 *
 * <p>Only ticks while the fire is lit — {@code PrimitiveCampfireBlock#getTicker} hands out a ticker
 * for that state alone, so an unlit pile and a heap of embers cost the server nothing at all.</p>
 */
public class PrimitiveCampfireBlockEntity extends BlockEntity {

    private static final String KEY_BURN_TICKS = "BurnTicks";
    private static final String KEY_STRIKE_ATTEMPTS = "StrikeAttempts";

    /** How often the timer is written back to disk. Per-tick saving would be pure waste. */
    private static final int SAVE_INTERVAL = 20;

    private int burnTicks;
    private final CampfireIgnition ignition = new CampfireIgnition();

    /** Weather sampled at most once every two seconds; {@code isRainingAt} walks the heightmap. */
    private boolean rainingCache;
    private long rainingSampledAt = Long.MIN_VALUE;

    private boolean sampledRaining(Level level, BlockPos pos) {
        if (!Config.RAIN_EXTINGUISHES_CAMPFIRE.get()) {
            return false;
        }
        long now = level.getGameTime();
        if (rainingSampledAt == Long.MIN_VALUE || rainingSampledAt > now
            || now - rainingSampledAt >= 40) {
            rainingSampledAt = now;
            rainingCache = level.isRainingAt(pos.above());
        }
        return rainingCache;
    }

    public PrimitiveCampfireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRIMITIVE_CAMPFIRE.get(), pos, state);
    }

    /** Starts the clock. Called the moment a strike catches. */
    public void onLit() {
        burnTicks = 0;
        ignition.reset();
        setChanged();
    }

    /** Records a strike and returns its one-based attempt number. */
    public int recordStrikeAttempt() {
        int attempt = ignition.recordAttempt();
        setChanged();
        return attempt;
    }

    /** Clears failed-strike progress when the wood is cold again. */
    public void resetStrikeAttempts() {
        ignition.reset();
        setChanged();
    }

    public int strikeAttempts() {
        return ignition.attempts();
    }

    /** How far through its burn the fire is, 0 to 1. Drives nothing yet; useful for a gauge later. */
    public float burnProgress() {
        int total = Config.CAMPFIRE_BURN_TICKS.get();
        return total <= 0 ? 1.0F : Math.min(1.0F, (float) burnTicks / total);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrimitiveCampfireBlockEntity campfire) {
        if (campfire.sampledRaining(level, pos)) {
            campfire.burnTicks = 0;
            campfire.resetStrikeAttempts();
            level.setBlock(pos, state.setValue(PrimitiveCampfireBlock.STAGE, CampfireStage.UNLIT), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
            campfire.setChanged();
            return;
        }

        campfire.burnTicks++;

        if (campfire.burnTicks >= Config.CAMPFIRE_BURN_TICKS.get()) {
            level.setBlock(pos, state.setValue(PrimitiveCampfireBlock.STAGE, CampfireStage.EMBERS), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
            campfire.setChanged();
            return;
        }

        if (campfire.burnTicks % SAVE_INTERVAL == 0) {
            campfire.setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTicks = tag.getInt(KEY_BURN_TICKS);
        ignition.reset();
        for (int i = 0; i < Math.min(CampfireIgnition.MAX_ATTEMPTS, tag.getInt(KEY_STRIKE_ATTEMPTS)); i++) {
            ignition.recordAttempt();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_BURN_TICKS, burnTicks);
        tag.putInt(KEY_STRIKE_ATTEMPTS, ignition.attempts());
    }
}
