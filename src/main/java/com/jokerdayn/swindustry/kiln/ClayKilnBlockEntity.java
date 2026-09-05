package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.Config;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockInstance;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModItems;
import com.jokerdayn.swindustry.registry.ModRecipes;
import com.jokerdayn.swindustry.registry.ModTags;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the clay kiln does.
 *
 * <p>It is a furnace in the way it burns, and nothing like one in the way it exists. It only works
 * when fifty-seven blocks agree to be a kiln, it checks that agreement rather than assuming it, and
 * it goes cold the moment the agreement breaks.</p>
 *
 * <h2>What it will smelt</h2>
 *
 * <p>Two sources, checked in that order:</p>
 * <ol>
 *   <li>{@link KilnRecipe} — things only a kiln can do, and the only place a recipe can demand a
 *       better kiln than the one it is standing in;</li>
 *   <li>ordinary {@code minecraft:smelting} recipes — because the pack takes the vanilla furnace
 *       away, and the kiln has to be able to cook a fish.</li>
 * </ol>
 */
public class ClayKilnBlockEntity extends MultiblockControllerEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Indices into {@link #dataAccess}, shared with the screen. */
    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOK_PROGRESS = 2;
    public static final int DATA_COOK_DURATION = 3;
    public static final int DATA_FORMED = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_TIER = 6;
    public static final int DATA_FUEL_SECONDS = 7;
    public static final int DATA_HEAT = 8;
    public static final int DATA_COUNT = 9;

    public static final int MAX_HEAT = 1000;

    private static final String KEY_ITEMS = "Items";
    private static final String KEY_LIT_TIME = "LitTime";
    private static final String KEY_LIT_DURATION = "LitDuration";
    private static final String KEY_COOK_PROGRESS = "CookProgress";
    private static final String KEY_COOK_DURATION = "CookDuration";
    private static final String KEY_EXPERIENCE = "StoredExperience";
    private static final String KEY_ACTIVE_JOB = "ActiveJob";
    private static final String KEY_HEAT = "Heat";
    private static final String KEY_SOAK = "Soak";
    private static final String KEY_SOAK_STARTS = "SoakStarts";
    private static final String KEY_HANDLER_SIZE = "Size";
    private static final int SAVE_INTERVAL = 20;

    /**
     * Thermal soak: ticks of fire accumulated while formed and lit. Each raw brick records the
     * soak value it started from ({@link #soakStarts}); it cures when {@code soak - start}
     * reaches its position's zone requirement.
     */
    private float soak;

    /** Packed raw-wall position -> soak when that brick entered the current firing cycle. */
    private final Long2IntOpenHashMap soakStarts = new Long2IntOpenHashMap();

    /** Packed positions of wall cells currently raw; memory-side, rebuilt on every form. */
    private final LongOpenHashSet rawWalls = new LongOpenHashSet();

    /** Weather sampled at most once a second across exposed blocks. */
    private float rainExposureRatio = 0.0F;
    private long rainSampledAt = Long.MIN_VALUE;

    /** Cached exposed wall blocks bordering exterior air/world (for water contact checks). */
    private final List<BlockPos> exteriorWallBlocks = new java.util.ArrayList<>();

    /** Cached top-facing blocks open to the sky (for rain exposure checks). */
    private final List<BlockPos> skyExposedBlocks = new java.util.ArrayList<>();

    /** Timestamps for throttling audio cues so they remain gentle and pleasant. */
    private long lastQuenchSoundAt = Long.MIN_VALUE;
    private long lastRainSoundAt = Long.MIN_VALUE;

    /**
     * Cached heat ceiling; {@code -1} means "recompute from the shell census". The ceiling only
     * moves when a brick cures or the machine forms/breaks, so it is recomputed exactly there
     * instead of rescanning all 56 walls every lit tick.
     */
    private int ceilingCache = -1;

    /** Derived tier of the shell material; recomputed with the same census pass. */
    private int tierCache = -1;

    /** Set when something may have touched the interior; triggers one bounded flood scan. */
    private boolean floodCheckQueued;

    /**
     * Chimney natural ventilation:
     * - {@link #chimneyChoked}: true if flue exit is blocked by a solid block (choked / closed damper);
     *   drops heat ceiling to smoldering (250) and billows smoke from the mouth.
     * - {@link #extraChimneyHeight}: number of valid chimney courses (1 to 4) built above the base flue;
     *   each extra block speeds up heating and increases maximum heat ceiling.
     */
    private int extraChimneyHeight;
    private boolean chimneyChoked;
    private long draftSampledAt = Long.MIN_VALUE;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> level == null || canProcess(stack);
                case SLOT_FUEL -> stack.getBurnTime(RecipeType.SMELTING) > 0;
                case SLOT_OUTPUT -> false;
                default -> false;
            };
        }
    };

    private int litTime;
    private int litDuration;
    private int cookProgress;
    private float cookProgressFraction;
    private int cookDuration = KilnRecipe.DEFAULT_COOKING_TIME;
    private int heat;
    private float storedExperience;
    private KilnStatus status = KilnStatus.INCOMPLETE;
    @Nullable
    private String activeJobKey;

    private final RecipeManager.CachedCheck<SingleRecipeInput, KilnRecipe> kilnRecipes;
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> furnaceRecipes;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // Vanilla data slots are signed shorts on the wire. Scale long operations instead
                // of letting lava fuel or datapack cooking times wrap into negative client values.
                case DATA_LIT_TIME -> scaleForDataSlot(litTime, litDuration);
                case DATA_LIT_DURATION -> Math.min(litDuration, Short.MAX_VALUE);
                case DATA_COOK_PROGRESS -> scaleForDataSlot(cookProgress, cookDuration);
                case DATA_COOK_DURATION -> Math.min(cookDuration, Short.MAX_VALUE);
                case DATA_FORMED -> isFormed() ? 1 : 0;
                case DATA_STATUS -> status.id();
                case DATA_TIER -> tier();
                case DATA_FUEL_SECONDS -> Math.min(Short.MAX_VALUE, Mth.ceil(litTime / 20.0F));
                case DATA_HEAT -> Math.min(MAX_HEAT, Math.max(0, heat));
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> litTime = value;
                case DATA_LIT_DURATION -> litDuration = value;
                case DATA_COOK_PROGRESS -> cookProgress = value;
                case DATA_COOK_DURATION -> cookDuration = value;
                case DATA_STATUS -> status = KilnStatus.byId(value);
                case DATA_HEAT -> heat = Math.min(MAX_HEAT, Math.max(0, value));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ClayKilnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAY_KILN.get(), pos, state);
        this.kilnRecipes = RecipeManager.createCheck(ModRecipes.KILN_TYPE.get());
        this.furnaceRecipes = RecipeManager.createCheck(RecipeType.SMELTING);
    }

    /**
     * A wrench can rotate the port without replacing its block entity. Both the cached structure
     * and NeoForge's sided capability cache depend on that facing, so invalidate them together.
     */
    @Deprecated
    @Override
    public void setBlockState(BlockState state) {
        Direction previousFacing = controllerFacing(getBlockState());
        super.setBlockState(state);
        if (previousFacing != controllerFacing(state)) {
            invalidateStructure();
            invalidateCapabilities();
        }
    }

    private static int scaleForDataSlot(int value, int duration) {
        int safeValue = Math.max(0, value);
        if (duration > Short.MAX_VALUE) {
            return Mth.clamp(Mth.floor((double) safeValue / duration * Short.MAX_VALUE),
                0, Short.MAX_VALUE);
        }
        return Math.min(safeValue, Short.MAX_VALUE);
    }

    // ------------------------------------------------------------------
    // Multiblock
    // ------------------------------------------------------------------

    @Override
    protected MultiblockPattern pattern() {
        return KilnPatterns.clayKiln();
    }

    @Nullable
    @Override
    protected Direction controllerFacing(BlockState state) {
        return state.hasProperty(ClayKilnPortBlock.FACING) ? state.getValue(ClayKilnPortBlock.FACING) : null;
    }

    /**
     * How good this kiln is. Derived from the shell census: tier 0 while any wall is raw clay,
     * then the highest {@code kiln_wall_tier_N} tag every wall satisfies.
     */
    public int tier() {
        if (tierCache < 0) {
            refreshShellCensus();
        }
        return tierCache;
    }

    /**
     * Fully derived: a formed machine with no raw clay left in its walls counts as cured. Never
     * persisted, so a shell edited while the server was down cannot be believed in.
     */
    public boolean isCured() {
        return instance() != null && rawWalls.isEmpty();
    }

    public int effectiveMaxHeat() {
        if (ceilingCache < 0) {
            refreshShellCensus();
        }
        if (chimneyChoked) {
            // Choked damper: fire is smothered down to smoldering heat (250 / 25%)
            return 250;
        }
        int max = ceilingCache;
        if (extraChimneyHeight > 0) {
            // Natural draft bonus: each extra chimney course raises maximum heat ceiling by 75 (up to 900)
            max = Math.min(900, max + extraChimneyHeight * 75);
        }
        return max;
    }

    public boolean isChimneyChoked() {
        return chimneyChoked;
    }

    public int extraChimneyHeight() {
        return extraChimneyHeight;
    }

    @SuppressWarnings("deprecation")
    private static boolean hasLoadedChunk(Level level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }

    /**
     * Checks chimney ventilation and natural draft.
     * Evaluates up to 4 additional chimney courses above the base multiblock flue opening.
     * If the opening is capped or obstructed, the kiln becomes choked (smoldering mode).
     * Any raw clay bricks built into the chimney courses are tracked so they cure from the heat.
     */
    private void updateDraft(Level level) {
        long now = level.getGameTime();
        if (draftSampledAt != Long.MIN_VALUE && now - draftSampledAt < 20) {
            return;
        }
        draftSampledAt = now;

        Direction facing = controllerFacing(getBlockState());
        if (facing == null) {
            extraChimneyHeight = 0;
            chimneyChoked = false;
            return;
        }

        BlockPos flue = pattern().toWorld(worldPosition, facing, ClayKilnPortBlock.FLUE_TOP);
        if (!hasLoadedChunk(level, flue)) {
            return;
        }
        BlockPos chimneyExit = flue;
        boolean choked = false;
        int extra = 0;

        for (int step = 1; step <= 4; step++) {
            BlockPos above = chimneyExit.above();
            if (!hasLoadedChunk(level, above)) {
                return;
            }
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                choked = true;
                break;
            }
            BlockPos north = above.north();
            BlockPos south = above.south();
            BlockPos east = above.east();
            BlockPos west = above.west();
            if (!hasLoadedChunk(level, north) || !hasLoadedChunk(level, south)
                || !hasLoadedChunk(level, east) || !hasLoadedChunk(level, west)) {
                return;
            }
            if (level.getBlockState(north).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(south).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(east).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(west).is(ModTags.Blocks.KILN_WALL)) {
                chimneyExit = above;
                extra = step;
                if (!level.isClientSide) {
                    BlockPos[] ring = new BlockPos[] { north, south, east, west };
                    for (BlockPos wall : ring) {
                        if (level.getBlockState(wall).is(ModBlocks.RAW_CLAY_BRICKS.get())) {
                            long packed = wall.asLong();
                            if (rawWalls.add(packed)) {
                                soakStarts.put(packed, Math.min(Integer.MAX_VALUE, (int) soak));
                            }
                        }
                    }
                }
            } else {
                break;
            }
        }
        if (!choked) {
            BlockPos exitPos = chimneyExit.above();
            if (!hasLoadedChunk(level, exitPos)) {
                return;
            }
            BlockState exitAbove = level.getBlockState(exitPos);
            if (!exitAbove.isAir() && !exitAbove.canBeReplaced()) {
                choked = true;
            }
        }

        if (this.chimneyChoked != choked) {
            this.chimneyChoked = choked;
            invalidateCeiling();
        }
        this.extraChimneyHeight = extra;
    }

    /**
     * One pass over the shell answers both questions the machine asks of it: how hot it may get
     * (cure ratio) and what tier of shell material it is built from (highest tag every wall
     * satisfies, resolved only while fully cured).
     */
    private void refreshShellCensus() {
        MultiblockInstance instance = instance();

        if (isCured()) {
            ceilingCache = 600;
        } else {
            float ratio = curedRatio();
            ceilingCache = Math.min(600, 300 + Math.round(300.0F * ratio));
        }

        int resolved = 0;
        if (instance != null && level != null && !level.isClientSide && isCured()) {
            for (int candidate = ModTags.Blocks.KILN_WALL_TIERS.size(); candidate >= 1; candidate--) {
                if (shellMatchesTier(instance, ModTags.Blocks.KILN_WALL_TIERS.get(candidate - 1))) {
                    resolved = candidate;
                    break;
                }
            }
        }
        tierCache = resolved;
    }

    private boolean shellMatchesTier(com.jokerdayn.swindustry.multiblock.MultiblockInstance instance,
                                     TagKey<Block> tierTag) {
        for (BlockPos wall : instance.walls()) {
            if (wall.equals(worldPosition)) {
                continue;
            }
            if (!level.getBlockState(wall).is(tierTag)) {
                return false;
            }
        }
        return true;
    }

    private void invalidateCeiling() {
        ceilingCache = -1;
        tierCache = -1;
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        // Something may have entered or obstructed the interior; queue one bounded scan instead
        // of polling the cavity every tick.
        floodCheckQueued = true;
        draftSampledAt = Long.MIN_VALUE;
    }

    public float curedRatio() {
        MultiblockInstance instance = instance();
        if (instance == null || level == null) {
            return isCured() ? 1.0F : 0.0F;
        }
        int rawCount = 0;
        int curedCount = 0;
        for (BlockPos wallPos : instance.walls()) {
            if (wallPos.equals(worldPosition)) {
                continue;
            }
            BlockState state = level.getBlockState(wallPos);
            if (state.is(ModBlocks.RAW_CLAY_BRICKS.get())) {
                rawCount++;
            } else if (state.is(ModBlocks.CLAY_BRICKS.get())) {
                curedCount++;
            }
        }
        int total = rawCount + curedCount;
        return total > 0 ? (float) curedCount / total : (isCured() ? 1.0F : 0.0F);
    }

    @Override
    protected void onFormed(com.jokerdayn.swindustry.multiblock.MultiblockInstance instance) {
        status = KilnStatus.IDLE;

        // Rebuild raw-wall tracking from the actual world without resetting per-brick progress.
        // The map is persisted, while rawWalls is deliberately transient, so a loaded kiln must
        // keep entries restored by loadAdditional. A newly inserted brick has no entry and starts
        // at the current soak value; the original batch is recorded explicitly with start 0 so
        // that a later save/load can distinguish it from a newly inserted brick.
        LongOpenHashSet previouslyTrackedRaw = new LongOpenHashSet(rawWalls);
        rawWalls.clear();
        exteriorWallBlocks.clear();
        skyExposedBlocks.clear();
        if (level != null && !level.isClientSide) {
            exteriorWallBlocks.add(worldPosition.immutable());
            if (!instance.walls().contains(worldPosition.above()) && !instance.cavity().contains(worldPosition.above())) {
                skyExposedBlocks.add(worldPosition.immutable());
            }

            for (BlockPos pos : instance.walls()) {
                if (!instance.walls().contains(pos.above()) && !instance.cavity().contains(pos.above())) {
                    skyExposedBlocks.add(pos.immutable());
                }

                boolean isExterior = false;
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    if (!instance.walls().contains(neighbor) && !instance.cavity().contains(neighbor)) {
                        isExterior = true;
                        break;
                    }
                }
                if (isExterior) {
                    exteriorWallBlocks.add(pos.immutable());
                }

                if (pos.equals(worldPosition)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.RAW_CLAY_BRICKS.get())) {
                    continue;
                }
                long packed = pos.asLong();
                rawWalls.add(packed);
                if (!soakStarts.containsKey(packed)) {
                    int start = previouslyTrackedRaw.contains(packed)
                        ? 0
                        : Math.min(Integer.MAX_VALUE, Math.max(0, (int) soak));
                    soakStarts.put(packed, start);
                }
            }
        }
        invalidateCeiling();
    }

    @Override
    protected void onRevalidated(MultiblockInstance instance) {
        if (reconcileRawWallTracking(instance)) {
            invalidateCeiling();
            setChanged();
        }
    }

    /**
     * Reconciles raw walls even when a replacement still matches the multiblock pattern. A cured
     * wall can become raw without making the structure unformed, so transition callbacks alone are
     * not enough to maintain curing state or the derived heat/tier caches.
     */
    private boolean reconcileRawWallTracking(
        MultiblockInstance instance) {
        if (level == null || level.isClientSide) {
            return false;
        }
        LongOpenHashSet previouslyTrackedRaw = new LongOpenHashSet(rawWalls);
        boolean changed = false;
        for (BlockPos pos : instance.walls()) {
            if (pos.equals(worldPosition)) {
                continue;
            }
            long packed = pos.asLong();
            if (level.getBlockState(pos).is(ModBlocks.RAW_CLAY_BRICKS.get())) {
                if (rawWalls.add(packed)) {
                    changed = true;
                }
                if (!soakStarts.containsKey(packed)) {
                    int start = previouslyTrackedRaw.contains(packed)
                        ? 0
                        : Math.min(Integer.MAX_VALUE, Math.max(0, (int) soak));
                    soakStarts.put(packed, start);
                    changed = true;
                }
            } else if (rawWalls.remove(packed)) {
                soakStarts.remove(packed);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    protected void onUnformed() {
        // A kiln pulled apart mid-burn loses the burn. The fuel is gone up the hole in the wall.
        purgeOperationalState();
        clearLitVisual();
    }

    @Override
    protected void onLoadedBroken() {
        // Loaded with the shell already breached: fuel and progress in the save belonged to a
        // machine that no longer exists by the time this session starts.
        purgeOperationalState();
        rawWalls.clear();
        soakStarts.clear();
        heat = 0;
        clearLitVisual();
    }

    /**
     * Forgets curing progress when a tracked raw wall is replaced. Keeping this separate from
     * structure invalidation lets unchanged walls retain their accumulated exposure after a repair.
     */
    public void forgetRawWall(BlockPos pos) {
        long packed = pos.asLong();
        rawWalls.remove(packed);
        soakStarts.remove(packed);
    }

    private void purgeOperationalState() {
        litTime = 0;
        litDuration = 0;
        cookProgress = 0;
        cookProgressFraction = 0.0F;
        // Do not zero heat here: let heat dissipate smoothly or be quenched by water/rain.
        activeJobKey = null;
        exteriorWallBlocks.clear();
        skyExposedBlocks.clear();
        extraChimneyHeight = 0;
        chimneyChoked = false;
        draftSampledAt = Long.MIN_VALUE;
        rainSampledAt = Long.MIN_VALUE;
        rainExposureRatio = 0.0F;
        status = KilnStatus.INCOMPLETE;
        invalidateCeiling();
    }

    /**
     * Immediate push notification from a part block or port block when water touches it.
     */
    public void notifyWaterContact(BlockPos waterPos) {
        floodCheckQueued = true;
        if (level instanceof ServerLevel serverLevel && (isLit() || heat > 0 || soak > 0)) {
            quenchAt(serverLevel, waterPos, false);
        }
    }

    /**
     * Spawns white steam particles and plays a satisfying hissing sound,
     * scaled dynamically in volume, pitch, and particle count by the kiln's temperature.
     */
    private void quenchAt(ServerLevel level, BlockPos contactPos, boolean internal) {
        float heatRatio = Math.max(0.1F, (float) heat / MAX_HEAT);

        long now = level.getGameTime();
        if (lastQuenchSoundAt == Long.MIN_VALUE || lastQuenchSoundAt > now
            || now - lastQuenchSoundAt >= 8) {
            lastQuenchSoundAt = now;
            // Hotter kilns hiss with greater volume and higher steam pitch
            float candleVol = 0.50F + 0.35F * heatRatio;
            float sizzleVol = 0.75F + 0.40F * heatRatio;
            float pitch = 1.20F + 0.30F * heatRatio + level.random.nextFloat() * 0.15F;

            level.playSound(null, contactPos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS,
                candleVol, pitch * 0.85F);
            level.playSound(null, contactPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                sizzleVol, pitch);
        }

        // White steam particles with 1.5x increased count
        int smokeCount = Math.max(3, Math.round((internal ? 9 : 5) * heatRatio));
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
            contactPos.getX() + 0.5, contactPos.getY() + 0.5, contactPos.getZ() + 0.5,
            smokeCount, 0.12, 0.05, 0.12, 0.005);
    }

    private void playRainSizzle(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (lastRainSoundAt == Long.MIN_VALUE || lastRainSoundAt > now
            || now - lastRainSoundAt >= 8) {
            lastRainSoundAt = now;
            float heatRatio = Math.max(0.1F, (float) heat / MAX_HEAT);
            float candleVol = 0.45F + 0.30F * heatRatio;
            float sizzleVol = 0.70F + 0.35F * heatRatio;
            float pitch = 1.25F + 0.30F * heatRatio + level.random.nextFloat() * 0.15F;

            level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS,
                candleVol, pitch * 0.85F);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                sizzleVol, pitch);
        }
    }

    private float updateRainExposure(Level level) {
        long now = level.getGameTime();
        if (rainSampledAt != Long.MIN_VALUE && now - rainSampledAt < 20) {
            return rainExposureRatio;
        }
        rainSampledAt = now;

        if (!level.isRaining() || skyExposedBlocks.isEmpty()) {
            rainExposureRatio = 0.0F;
            return 0.0F;
        }

        int exposedCount = 0;
        for (BlockPos skyPos : skyExposedBlocks) {
            BlockPos rainPos = skyPos.above();
            if (hasLoadedChunk(level, rainPos) && level.isRainingAt(rainPos)) {
                exposedCount++;
            }
        }
        rainExposureRatio = (float) exposedCount / skyExposedBlocks.size();
        return rainExposureRatio;
    }

    private void clearLitVisual() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(ClayKilnPortBlock.LIT) && state.getValue(ClayKilnPortBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(ClayKilnPortBlock.LIT, Boolean.FALSE), Block.UPDATE_ALL);
        }
    }

    /** Tells a player the first thing that is wrong with their half-built kiln. */
    public void tellPlayerWhatIsMissing(Player player) {
        if (level == null) {
            return;
        }
        Direction facing = controllerFacing(getBlockState());
        Optional<MultiblockPattern.Mismatch> mismatch = facing == null
            ? Optional.empty()
            : pattern().firstMismatch(level, worldPosition, facing);

        mismatch.ifPresentOrElse(
            problem -> player.displayClientMessage(Component.translatable(
                "message.swindustry.kiln_incomplete",
                problem.pos().getX(), problem.pos().getY(), problem.pos().getZ()), true),
            () -> player.displayClientMessage(
                Component.translatable("message.swindustry.kiln_incomplete_unknown"), true));
    }

    // ------------------------------------------------------------------
    // Burning
    // ------------------------------------------------------------------

    public boolean isLit() {
        return litTime > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClayKilnBlockEntity kiln) {
        boolean dirty = false;

        boolean operational = kiln.revalidateIfStale();
        MultiblockInstance instance = operational ? kiln.instance() : null;

        // Water & Rain checks: flowing water and rain actively cool the kiln and its bricks
        if (operational && instance != null) {
            boolean isHot = kiln.isLit() || kiln.heat > 0 || (!kiln.rawWalls.isEmpty() && kiln.soak > 0);

            // Water check: cavity flooding or water poured/flowing against walls
            if (isHot && (kiln.floodCheckQueued || level.getGameTime() % 10 == 0)) {
                kiln.floodCheckQueued = false;
                boolean flooded = false;
                BlockPos floodPos = null;

                for (BlockPos cavityPos : instance.cavity()) {
                    if (hasLoadedChunk(level, cavityPos)
                        && level.getFluidState(cavityPos).is(FluidTags.WATER)) {
                        flooded = true;
                        floodPos = cavityPos;
                        break;
                    }
                }

                // Also check if water is flowing into flue opening
                if (!flooded) {
                    Direction facing = kiln.controllerFacing(state);
                    if (facing != null) {
                        BlockPos flue = kiln.pattern().toWorld(pos, facing, ClayKilnPortBlock.FLUE_TOP);
                        BlockPos chimneyTop = flue.above(kiln.extraChimneyHeight);
                        BlockPos chimneyAbove = chimneyTop.above();
                        if (hasLoadedChunk(level, chimneyTop) && hasLoadedChunk(level, chimneyAbove)
                            && (level.getFluidState(chimneyAbove).is(FluidTags.WATER)
                            || level.getFluidState(chimneyTop).is(FluidTags.WATER))) {
                            flooded = true;
                            floodPos = chimneyTop;
                        }
                    }
                }

                if (flooded) {
                    if (level instanceof ServerLevel serverLevel) {
                        kiln.quenchAt(serverLevel, floodPos != null ? floodPos : pos, true);
                    }
                    kiln.litTime = 0;
                    kiln.heat = Math.max(0, kiln.heat - 60);
                    kiln.soak = Math.max(0.0F, kiln.soak - 8.0F);
                    dirty = true;
                }

                // Check exterior walls touched by water
                int waterTouchingBricks = 0;
                BlockPos waterContactPos = null;
                for (BlockPos wallPos : kiln.exteriorWallBlocks) {
                    for (Direction dir : Direction.values()) {
                        BlockPos adj = wallPos.relative(dir);
                        if (!instance.walls().contains(adj) && !instance.cavity().contains(adj)) {
                            if (hasLoadedChunk(level, adj)
                                && level.getFluidState(adj).is(FluidTags.WATER)) {
                                waterTouchingBricks++;
                                waterContactPos = adj;
                                long packed = wallPos.asLong();
                                if (kiln.rawWalls.contains(packed)) {
                                    kiln.soakStarts.put(packed, Math.min(Integer.MAX_VALUE, (int) kiln.soak));
                                }
                                break;
                            }
                        }
                    }
                }

                if (waterTouchingBricks > 0) {
                    if (level instanceof ServerLevel serverLevel && waterContactPos != null) {
                        kiln.quenchAt(serverLevel, waterContactPos, false);
                    }
                    int cooling = Math.min(kiln.heat, 15 + 5 * Math.min(waterTouchingBricks, 5));
                    kiln.heat = Math.max(0, kiln.heat - cooling);
                    kiln.soak = Math.max(0.0F, kiln.soak - 3.0F * Math.min(waterTouchingBricks, 3));
                    dirty = true;
                }
            }

            // Rain exposure: downpours actively cool down unshielded hot kilns
            float rainRatio = kiln.updateRainExposure(level);
            if (rainRatio > 0.0F && kiln.heat > 0) {
                int rainCool = Math.round(2.0F * rainRatio);
                if (rainCool > 0) {
                    kiln.heat = Math.max(0, kiln.heat - rainCool);
                    dirty = true;
                }
                if (level instanceof ServerLevel serverLevel) {
                    // White rain steam particles across the exposed hot roof bricks
                    int particleInterval = kiln.heat >= 600 ? 1 : (kiln.heat >= 250 ? 2 : 3);
                    if (level.getGameTime() % particleInterval == 0 && !kiln.skyExposedBlocks.isEmpty()) {
                        BlockPos randomExposed = kiln.skyExposedBlocks.get(level.random.nextInt(kiln.skyExposedBlocks.size()));
                        if (level.isRainingAt(randomExposed.above())) {
                            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                randomExposed.getX() + 0.5, randomExposed.getY() + 1.02, randomExposed.getZ() + 0.5,
                                2, 0.15, 0.02, 0.15, 0.002);
                        }
                    }
                    if (kiln.heat >= 100 && !kiln.skyExposedBlocks.isEmpty()
                        && level.random.nextInt(kiln.heat >= 500 ? 10 : 20) == 0) {
                        BlockPos soundPos = kiln.skyExposedBlocks.get(level.random.nextInt(kiln.skyExposedBlocks.size()));
                        if (level.isRainingAt(soundPos.above())) {
                            kiln.playRainSizzle(serverLevel, soundPos);
                        }
                    }
                }
            }
        }

        if (!operational) {
            kiln.status = KilnStatus.INCOMPLETE;
            if (kiln.heat > 0) {
                int coolRate = 1;
                if (level.isRainingAt(pos.above())) {
                    coolRate += 2;
                    if (level instanceof ServerLevel serverLevel) {
                        int interval = kiln.heat >= 500 ? 2 : 4;
                        if (level.getGameTime() % interval == 0) {
                            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5,
                                2, 0.12, 0.02, 0.12, 0.002);
                        }
                        if (level.random.nextInt(kiln.heat >= 500 ? 15 : 25) == 0) {
                            kiln.playRainSizzle(serverLevel, pos);
                        }
                    }
                }
                for (Direction dir : Direction.values()) {
                    BlockPos adjacent = pos.relative(dir);
                    if (hasLoadedChunk(level, adjacent)
                        && level.getFluidState(adjacent).is(FluidTags.WATER)) {
                        coolRate += 20;
                        if (level instanceof ServerLevel serverLevel) {
                            kiln.quenchAt(serverLevel, adjacent, false);
                        }
                        break;
                    }
                }
                kiln.heat = Math.max(0, kiln.heat - coolRate);
                dirty = true;
            }
        } else {
            kiln.updateDraft(level);
            if (kiln.litTime > 0) {
                kiln.litTime--;
            }
            if (kiln.isLit()) {
                int effectiveMax = kiln.effectiveMaxHeat();
                // Target with gentle fire flicker around the effective max (±2°C)
                int target = effectiveMax;
                if (kiln.heat >= effectiveMax - 6) {
                    int flicker = level.random.nextInt(5) - 2;
                    target = Math.min(MAX_HEAT, Math.max(0, effectiveMax + flicker));
                }

                if (kiln.heat < target) {
                    // Natural organic heating: small random variation per tick
                    int heatRate = kiln.chimneyChoked ? 1 : (level.random.nextInt(3) + 1 + (kiln.extraChimneyHeight / 2));
                    kiln.heat = Math.min(target, kiln.heat + heatRate);
                    dirty = true;
                } else if (kiln.heat > target) {
                    if (level.random.nextBoolean()) {
                        kiln.heat = Math.max(target, kiln.heat - 1);
                        dirty = true;
                    }
                }
                dirty = kiln.tickSoak(level) || dirty;
            } else {
                if (kiln.heat > 0) {
                    // Smooth gradual cooling with clay thermal mass
                    if (level.random.nextInt(4) != 0) {
                        kiln.heat = Math.max(0, kiln.heat - 1);
                        dirty = true;
                    }
                }
            }
            dirty = kiln.runCookingStep(level) || dirty;
        }

        if (operational && kiln.isLit() && kiln.chimneyChoked && level instanceof ServerLevel serverLevel) {
            if (level.getGameTime() % 8 == 0) {
                Direction facing = kiln.controllerFacing(state);
                if (facing != null) {
                    double mouthX = pos.getX() + 0.5 + facing.getStepX() * 0.52;
                    double mouthY = pos.getY() + 0.4;
                    double mouthZ = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                        mouthX, mouthY, mouthZ, 1,
                        facing.getStepX() * 0.02, 0.01, facing.getStepZ() * 0.02, 0.005);
                }
            }
        }

        BlockState currentState = kiln.getBlockState();
        boolean shouldLookLit = operational && kiln.isLit();
        boolean shouldLookCured = kiln.isCured();
        boolean stateChanged = false;
        BlockState nextState = currentState;

        if (nextState.hasProperty(ClayKilnPortBlock.LIT)
            && nextState.getValue(ClayKilnPortBlock.LIT) != shouldLookLit) {
            nextState = nextState.setValue(ClayKilnPortBlock.LIT, shouldLookLit);
            stateChanged = true;
        }
        if (nextState.hasProperty(ClayKilnPortBlock.CURED)
            && nextState.getValue(ClayKilnPortBlock.CURED) != shouldLookCured) {
            nextState = nextState.setValue(ClayKilnPortBlock.CURED, shouldLookCured);
            stateChanged = true;
        }

        if (stateChanged) {
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
            dirty = true;
        }

        // Mark the chunk periodically while values change. Without this, an autosave can clear the
        // dirty flag and a later unload can restore stale fuel or cooking progress.
        if ((kiln.isLit() || kiln.cookProgress > 0) && level.getGameTime() % SAVE_INTERVAL == 0) {
            dirty = true;
        }

        if (dirty) {
            kiln.setChanged();
        }
    }

    /** One tick of the fire, the fuel and the thing being cooked. Returns whether to save. */
    private boolean runCookingStep(Level level) {
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        ItemStack fuel = items.getStackInSlot(SLOT_FUEL);
        boolean dirty = false;
        if (input.isEmpty()) {
            status = chimneyChoked ? KilnStatus.CHOKED : KilnStatus.IDLE;
            return coolProgress();
        }

        Job job = findJob(level, input);
        if (job == null || job.result().isEmpty()) {
            status = chimneyChoked ? KilnStatus.CHOKED : KilnStatus.INVALID_RECIPE;
            return coolProgress();
        }
        if (!job.isAllowedIn(tier())) {
            status = KilnStatus.TIER_TOO_LOW;
            return coolProgress();
        }
        if (!fitsInOutput(job.result())) {
            status = KilnStatus.OUTPUT_BLOCKED;
            return coolProgress();
        }

        if (cookProgress > 0 && activeJobKey != null && !activeJobKey.equals(job.key())) {
            // Progress belongs to a recipe, not merely to whatever happens to occupy the input
            // slot. Otherwise a slow recipe can be swapped for a fast one just before completion.
            cookProgress = 0;
            cookProgressFraction = 0.0F;
            dirty = true;
        }
        activeJobKey = job.key();

        cookDuration = Math.max(1, job.cookingTime());

        if (!isLit() && !fuel.isEmpty()) {
            litTime = burnDuration(fuel);
            litDuration = litTime;
            if (isLit()) {
                consumeFuel(level, fuel);
                dirty = true;
            }
        }

        if (!isLit()) {
            status = KilnStatus.NEEDS_FUEL;
            return coolProgress() || dirty;
        }

        // Temperature-driven smelting mechanics:
        // Must reach operating temperature to smelt (with 4-degree natural flicker tolerance)
        int reqHeat = Math.max(1, job.requiredHeat());
        if (heat < reqHeat - 4) {
            if (effectiveMaxHeat() < reqHeat - 4) {
                status = chimneyChoked ? KilnStatus.CHOKED : KilnStatus.TOO_COLD;
            } else {
                status = chimneyChoked ? KilnStatus.CHOKED : KilnStatus.WARMING_UP;
            }
            return dirty;
        }

        // Target temperature reached: 100% full speed smelting
        status = chimneyChoked ? KilnStatus.CHOKED : KilnStatus.WORKING;
        cookProgress++;
        dirty = true;

        if (cookProgress >= cookDuration) {
            cookProgress = 0;
            cookProgressFraction = 0.0F;
            finish(level, job);
            return true;
        }
        return dirty;
    }

    /** Heat in the clay fades instead of deleting a partial operation in a single tick. */
    private boolean coolProgress() {
        if (cookProgress <= 0) {
            activeJobKey = null;
            cookProgressFraction = 0.0F;
            return false;
        }
        cookProgress = Math.max(0, cookProgress - 1);
        if (cookProgress == 0) {
            activeJobKey = null;
            cookProgressFraction = 0.0F;
        }
        return true;
    }

    /**
     * Thermal soak, one tick of fire at a time.
     *
     * <p>Bricks cure across their thermal exposure zones (~155-210 s) measured from when they
     * entered a hot shell, not from a shared counter. Rain falling on exposed bricks chills them,
     * slowing curing down dramatically and even reversing progress if the kiln is cold.</p>
     *
     * <p>Steady-state cost is O(pending bricks) over pure memory; no block reads happen unless
     * something is actually due to convert.</p>
     */
    private boolean tickSoak(Level level) {
        if (!isLit() || !isFormed() || rawWalls.isEmpty()) {
            return false;
        }

        float rainRatio = updateRainExposure(level);
        if (rainRatio > 0.0F) {
            // Cold rain chills the soaking bricks; low heat causes net cooling
            if (heat < 250) {
                soak = Math.max(0.0F, soak - 0.3F * rainRatio);
            } else {
                soak += Math.max(0.1F, 1.0F - rainRatio * 0.8F);
            }
        } else {
            soak += 1.0F;
        }

        boolean changed = false;
        int floorY = worldPosition.getY() - 1;
        it.unimi.dsi.fastutil.longs.LongIterator iterator = rawWalls.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            BlockPos pos = BlockPos.of(packed);
            int start = soakStarts.get(packed); // fastutil default: 0
            if (soak - start < calculateBrickRequiredTicks(pos, floorY)) {
                continue;
            }

            // Stale-entry safety: the cell may have been mined or swapped since formation.
            if (!level.getBlockState(pos).is(ModBlocks.RAW_CLAY_BRICKS.get())) {
                iterator.remove();
                soakStarts.remove(packed);
                continue;
            }

            level.setBlock(pos, ModBlocks.CLAY_BRICKS.get().defaultBlockState(), Block.UPDATE_ALL);
            spawnCuringEffects(level, pos);
            changed = true;
            invalidateCeiling();
            iterator.remove();
        }

        if (rawWalls.isEmpty()) {
            playKilnCompletionSound(level);
        }
        return changed;
    }

    /**
     * Calculates the exact curing time required for a brick based on its thermodynamic zone and
     * position in the kiln geometry.
     */
    private static int calculateBrickRequiredTicks(BlockPos pos, int floorY) {
        int relY = pos.getY() - floorY;
        float baseSeconds;
        float spreadSeconds;

        switch (relY) {
            case 1 -> { // Chamber lower: 155 - 175 s (mean 165s, spread ±10s)
                baseSeconds = 165.0F;
                spreadSeconds = 10.0F;
            }
            case 2 -> { // Chamber upper: 165 - 185 s (mean 175s, spread ±10s)
                baseSeconds = 175.0F;
                spreadSeconds = 10.0F;
            }
            case 0 -> { // Massive floor: 175 - 200 s (mean 187.5s, spread ±12.5s)
                baseSeconds = 187.5F;
                spreadSeconds = 12.5F;
            }
            case 3 -> { // Vault: 180 - 205 s (mean 192.5s, spread ±12.5s)
                baseSeconds = 192.5F;
                spreadSeconds = 12.5F;
            }
            case 4 -> { // Chimney: 185 - 210 s (mean 197.5s, spread ±12.5s)
                baseSeconds = 197.5F;
                spreadSeconds = 12.5F;
            }
            default -> {
                baseSeconds = 200.0F;
                spreadSeconds = 10.0F;
            }
        }

        // Deterministic spread (-spreadSeconds .. +spreadSeconds) based on coordinates
        long hash = ((long) pos.getX() * 3129871L) ^ ((long) pos.getY() * 116123L) ^ ((long) pos.getZ() * 91871L);
        float fraction = ((Math.abs(hash) % 10000) / 10000.0F) * 2.0F - 1.0F;
        float targetSeconds = baseSeconds + (fraction * spreadSeconds);

        return (int) (targetSeconds * 20.0F);
    }

    private void spawnCuringEffects(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                6, 0.12, 0.05, 0.12, 0.005
            );
            serverLevel.playSound(
                null, pos,
                SoundEvents.CANDLE_EXTINGUISH,
                SoundSource.BLOCKS,
                0.5F, 1.2F
            );
            serverLevel.playSound(
                null, pos,
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS,
                0.35F, 1.6F + serverLevel.random.nextFloat() * 0.2F
            );
        }
    }

    private void playKilnCompletionSound(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                null, worldPosition,
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.BLOCKS,
                0.6F, 1.2F
            );
        }
    }

    private void consumeFuel(Level level, ItemStack fuel) {
        ItemStack remainingFuel = fuel.copy();
        remainingFuel.shrink(1);
        ItemStack remainder = fuel.getCraftingRemainingItem();

        if (remainingFuel.isEmpty()) {
            items.setStackInSlot(SLOT_FUEL, remainder);
            return;
        }

        items.setStackInSlot(SLOT_FUEL, remainingFuel);
        if (!remainder.isEmpty()) {
            // A single slot cannot hold both the rest of a stackable fuel and its container item.
            // Preserve both: keep the fuel loaded and return the remainder through the kiln mouth.
            Direction facing = controllerFacing(getBlockState());
            BlockPos mouth = facing == null ? worldPosition : worldPosition.relative(facing);
            Containers.dropItemStack(level, mouth.getX() + 0.5, mouth.getY() + 0.5,
                mouth.getZ() + 0.5, remainder);
        }
    }

    private void finish(Level level, Job job) {
        ItemStack inputStack = items.getStackInSlot(SLOT_INPUT);
        boolean wasCharrable = inputStack.is(ModTags.Items.KILN_CHARRABLE);

        ItemStack remainingInput = inputStack.copy();
        remainingInput.shrink(1);
        items.setStackInSlot(SLOT_INPUT, remainingInput);

        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, job.result().copy());
        } else {
            ItemStack combined = output.copy();
            combined.grow(job.result().getCount());
            items.setStackInSlot(SLOT_OUTPUT, combined);
        }

        if (remainingInput.isEmpty()) {
            activeJobKey = null;
        }

        Direction facing = controllerFacing(getBlockState());
        BlockPos mouth = facing == null ? worldPosition : worldPosition.relative(facing);

        // Smoldering wood charring produces soot alongside charcoal
        if (wasCharrable && chimneyChoked && !level.isClientSide) {
            Containers.dropItemStack(level, mouth.getX() + 0.5, mouth.getY() + 0.5, mouth.getZ() + 0.5,
                new ItemStack(ModItems.SOOT.get()));
        }

        // Experience leaves the kiln as it is earned rather than being banked against the output
        // slot. One less thing to serialise, and a visible sign the kiln did something.
        storedExperience += job.experience();
        if (storedExperience >= 1.0F && level instanceof ServerLevel server) {
            int whole = (int) storedExperience;
            storedExperience -= whole;
            ExperienceOrb.award(server, Vec3.atCenterOf(mouth), whole);
        }
    }

    private int burnDuration(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        int vanilla = fuel.getBurnTime(RecipeType.SMELTING);
        return vanilla <= 0 ? 0 : Math.max(1, (int) (vanilla * Config.KILN_FUEL_EFFICIENCY.get()));
    }

    private boolean fitsInOutput(ItemStack result) {
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return result.getCount() <= items.getSlotLimit(SLOT_OUTPUT)
                && result.getCount() <= result.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        int combined = output.getCount() + result.getCount();
        return combined <= items.getSlotLimit(SLOT_OUTPUT) && combined <= output.getMaxStackSize();
    }

    /**
     * What the kiln would make out of this stack, if anything.
     *
     * <p>Kiln recipes win over furnace recipes, so a pack can quietly override how the kiln handles
     * something the vanilla furnace also knows about.</p>
     */
    @Nullable
    private Job findJob(Level level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        Optional<RecipeHolder<KilnRecipe>> kilnMatch = kilnRecipes.getRecipeFor(recipeInput, level);
        if (kilnMatch.isPresent()) {
            RecipeHolder<KilnRecipe> holder = kilnMatch.get();
            KilnRecipe recipe = holder.value();
            return new Job("kiln:" + holder.id(), recipe.assemble(recipeInput, level.registryAccess()),
                recipe.cookingTime(), recipe.experience(), recipe.minTier(), recipe.requiredHeat());
        }

        Optional<RecipeHolder<SmeltingRecipe>> furnaceMatch = furnaceRecipes.getRecipeFor(recipeInput, level);
        if (furnaceMatch.isPresent()) {
            RecipeHolder<SmeltingRecipe> holder = furnaceMatch.get();
            SmeltingRecipe recipe = holder.value();
            int time = scaledFurnaceCookingTime(recipe.getCookingTime());
            ItemStack result = recipe.assemble(recipeInput, level.registryAccess());
            int heatReq = resolveRequiredHeat(input, result);
            return new Job("smelting:" + holder.id(), result,
                time, recipe.getExperience(), 0, heatReq);
        }

        return null;
    }

    /**
     * Maps vanilla and modded items to their balanced smelting temperatures in kiln heat units (0 - 1000°C).
     */
    public static int resolveRequiredHeat(ItemStack input, ItemStack result) {
        // 1. Food: meats, fish, potatoes, kelp, etc. (150°C - Stage 0 raw kiln)
        if (input.has(net.minecraft.core.component.DataComponents.FOOD)
            || result.has(net.minecraft.core.component.DataComponents.FOOD)) {
            return 150;
        }

        // 2. Wood / Pyrolysis to Charcoal (250°C - Stage 0 raw kiln)
        if (result.is(net.minecraft.world.item.Items.CHARCOAL)
            || input.is(net.minecraft.tags.ItemTags.LOGS)) {
            return 250;
        }

        // 3. Iron: requires blast fan forced air (1000°C - Stage 4 fan)
        if (input.is(net.minecraft.world.item.Items.RAW_IRON)
            || result.is(net.minecraft.world.item.Items.IRON_INGOT)
            || input.is(net.minecraft.tags.ItemTags.IRON_ORES)) {
            return 1000;
        }

        // 4. Bronze & Copper (900°C - Stage 3 tall chimney +4)
        if (input.is(net.minecraft.world.item.Items.RAW_COPPER)
            || result.is(net.minecraft.world.item.Items.COPPER_INGOT)
            || input.is(net.minecraft.tags.ItemTags.COPPER_ORES)
            || input.is(ModTags.Items.BRONZE_RAW)
            || result.is(ModTags.Items.BRONZE_INGOTS)) {
            return 900;
        }

        // 5. Gold: (800°C - Stage 2 chimney +3)
        if (input.is(net.minecraft.world.item.Items.RAW_GOLD)
            || result.is(net.minecraft.world.item.Items.GOLD_INGOT)
            || input.is(net.minecraft.tags.ItemTags.GOLD_ORES)) {
            return 800;
        }

        // 6. Glass from sand (750°C - Stage 2 chimney +2)
        if (input.is(net.minecraft.world.item.Items.SAND)
            || input.is(net.minecraft.world.item.Items.RED_SAND)
            || result.is(net.minecraft.world.item.Items.GLASS)) {
            return 750;
        }

        // 7. Stone & stone bricks sintering (600°C - Stage 1 cured kiln)
        if (input.is(net.minecraft.world.item.Items.COBBLESTONE)
            || input.is(net.minecraft.world.item.Items.COBBLED_DEEPSLATE)
            || input.is(net.minecraft.world.item.Items.STONE_BRICKS)
            || input.is(net.minecraft.world.item.Items.STONE)
            || input.is(net.minecraft.world.item.Items.BASALT)) {
            return 600;
        }

        // 8. Clay, Terracotta, Bricks (600°C - Stage 1 cured kiln)
        if (input.is(net.minecraft.world.item.Items.CLAY_BALL)
            || input.is(net.minecraft.world.item.Items.CLAY)
            || result.is(net.minecraft.world.item.Items.BRICK)
            || result.is(net.minecraft.world.item.Items.TERRACOTTA)) {
            return 600;
        }

        return 600;
    }

    /**
     * Makes the configured vanilla baseline effective while retaining relative times from modded
     * smelting recipes. At the default 200 every recipe keeps its declared duration unchanged.
     */
    private static int scaledFurnaceCookingTime(int recipeTime) {
        int configuredBaseline = Config.KILN_DEFAULT_COOK_TICKS.get();
        if (recipeTime <= 0) {
            return configuredBaseline;
        }
        long scaled = (long) recipeTime * configuredBaseline;
        long rounded = (scaled + KilnRecipe.DEFAULT_COOKING_TIME / 2L)
            / KilnRecipe.DEFAULT_COOKING_TIME;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, rounded));
    }

    /** Used by slots and shift-click routing so obvious non-recipes never clog the input. */
    public boolean canProcess(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Existence-only probe: building full jobs here would copy result stacks on every
        // simulated hopper insertion just to throw them away.
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return kilnRecipes.getRecipeFor(input, level).isPresent()
            || furnaceRecipes.getRecipeFor(input, level).isPresent();
    }

    /** One unit of work the kiln has agreed to do, flattened out of whichever recipe described it. */
    private record Job(String key, ItemStack result, int cookingTime, float experience, int minTier, int requiredHeat) {
        boolean isAllowedIn(int tier) {
            return tier >= minTier;
        }
    }

    // ------------------------------------------------------------------
    // Contents
    // ------------------------------------------------------------------

    /**
     * The kiln's inventory, as seen from outside.
     *
     * <p>Exposed on the port so that hoppers, and later anything Create can point at it, load and
     * unload the kiln through the same hole a player uses.</p>
     */
    public IItemHandler itemHandler() {
        return items;
    }

    /**
     * Automation follows furnace conventions, with the mouth itself accepting both materials and
     * fuel: top inserts input, other sides insert fuel, bottom extracts output and spent fuel
     * containers.
     */
    public IItemHandler itemHandler(@Nullable Direction side) {
        return KilnInventoryViews.forSide(items, side, structureFacing());
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        NonNullList<ItemStack> contents = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            contents.set(slot, items.getStackInSlot(slot).copy());
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
        Containers.dropContents(level, worldPosition, contents);
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.swindustry.clay_kiln");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ClayKilnMenu(containerId, playerInventory, items, dataAccess, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_ITEMS, Tag.TAG_COMPOUND)) {
            // ItemStackHandler trusts its serialized Size field. Pin it to the machine's real slot
            // count so a malformed or older save cannot turn every fixed slot access into a crash.
            CompoundTag itemTag = tag.getCompound(KEY_ITEMS).copy();
            itemTag.putInt(KEY_HANDLER_SIZE, SLOT_COUNT);
            items.deserializeNBT(registries, itemTag);
        }
        litTime = Math.max(0, tag.getInt(KEY_LIT_TIME));
        litDuration = tag.contains(KEY_LIT_DURATION)
            ? Math.max(litTime, tag.getInt(KEY_LIT_DURATION))
            : litTime;
        cookDuration = tag.contains(KEY_COOK_DURATION)
            ? Math.max(1, tag.getInt(KEY_COOK_DURATION))
            : KilnRecipe.DEFAULT_COOKING_TIME;
        cookProgress = Mth.clamp(tag.getInt(KEY_COOK_PROGRESS), 0, cookDuration - 1);
        // Legacy saves map their counter straight into the new soak clock; the old cured flag is
        // dropped — "cured" is always re-derived from the walls on first formation nowadays.
        if (tag.contains(KEY_SOAK, Tag.TAG_FLOAT)) {
            soak = Math.max(0, tag.getFloat(KEY_SOAK));
        } else {
            soak = Math.max(0, tag.getInt("CuringProgress"));
        }
        soakStarts.clear();
        net.minecraft.nbt.ListTag soakStartList = tag.getList(KEY_SOAK_STARTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < soakStartList.size(); i++) {
            CompoundTag entry = soakStartList.getCompound(i);
            if (entry.contains("Pos", Tag.TAG_LONG) && entry.contains("Start", Tag.TAG_INT)) {
                soakStarts.put(entry.getLong("Pos"), Math.max(0, entry.getInt("Start")));
            }
        }
        float loadedExperience = tag.getFloat(KEY_EXPERIENCE);
        storedExperience = Float.isFinite(loadedExperience) && loadedExperience >= 0.0F
            ? loadedExperience
            : 0.0F;
        heat = Mth.clamp(tag.getInt(KEY_HEAT), 0, MAX_HEAT);
        chimneyChoked = tag.getBoolean("Choked");
        extraChimneyHeight = Math.max(0, tag.getInt("ExtraChimney"));
        activeJobKey = cookProgress > 0 && tag.contains(KEY_ACTIVE_JOB, Tag.TAG_STRING)
            ? tag.getString(KEY_ACTIVE_JOB)
            : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_ITEMS, items.serializeNBT(registries));
        tag.putInt(KEY_LIT_TIME, litTime);
        tag.putInt(KEY_LIT_DURATION, litDuration);
        tag.putInt(KEY_COOK_PROGRESS, cookProgress);
        tag.putInt(KEY_COOK_DURATION, cookDuration);
        tag.putFloat(KEY_SOAK, soak);
        net.minecraft.nbt.ListTag soakStartList = new net.minecraft.nbt.ListTag();
        for (long packed : soakStarts.keySet().toLongArray()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", packed);
            entry.putInt("Start", soakStarts.get(packed));
            soakStartList.add(entry);
        }
        tag.put(KEY_SOAK_STARTS, soakStartList);
        tag.putFloat(KEY_EXPERIENCE, storedExperience);
        tag.putInt(KEY_HEAT, heat);
        tag.putBoolean("Choked", chimneyChoked);
        tag.putInt("ExtraChimney", extraChimneyHeight);
        if (cookProgress > 0 && activeJobKey != null) {
            tag.putString(KEY_ACTIVE_JOB, activeJobKey);
        }
    }

}
