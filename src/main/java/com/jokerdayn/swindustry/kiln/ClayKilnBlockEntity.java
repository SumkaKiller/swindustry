package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.Config;
import com.jokerdayn.swindustry.multiblock.MultiblockControllerEntity;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import com.jokerdayn.swindustry.registry.ModRecipes;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the clay kiln does.
 *
 * <p>It is a furnace in the way it burns, and nothing like one in the way it exists. It only works
 * when forty-four blocks agree to be a kiln, it checks that agreement rather than assuming it, and
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
    public static final int DATA_COUNT = 8;

    private static final String KEY_ITEMS = "Items";
    private static final String KEY_LIT_TIME = "LitTime";
    private static final String KEY_LIT_DURATION = "LitDuration";
    private static final String KEY_COOK_PROGRESS = "CookProgress";
    private static final String KEY_COOK_DURATION = "CookDuration";
    private static final String KEY_EXPERIENCE = "StoredExperience";
    private static final String KEY_ACTIVE_JOB = "ActiveJob";
    private static final String KEY_HANDLER_SIZE = "Size";
    private static final int SAVE_INTERVAL = 20;

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

    private final IItemHandler inputAutomation = new RestrictedItemHandler(new int[] {SLOT_INPUT}, true, false);
    private final IItemHandler fuelAutomation = new RestrictedItemHandler(new int[] {SLOT_FUEL}, true, false);
    private final IItemHandler loadingPortAutomation =
        new RestrictedItemHandler(new int[] {SLOT_INPUT, SLOT_FUEL}, true, false);
    private final IItemHandler outputAutomation =
        new RestrictedItemHandler(new int[] {SLOT_OUTPUT, SLOT_FUEL}, false, true);

    private int litTime;
    private int litDuration;
    private int cookProgress;
    private int cookDuration = KilnRecipe.DEFAULT_COOKING_TIME;
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
        return KilnPatterns.CLAY_KILN;
    }

    @Nullable
    @Override
    protected Direction controllerFacing(BlockState state) {
        return state.hasProperty(ClayKilnPortBlock.FACING) ? state.getValue(ClayKilnPortBlock.FACING) : null;
    }

    /**
     * How good this kiln is. Raw clay is the bottom rung; a later shell material reads its tier off
     * the blocks it is actually built from and returns something higher.
     */
    public int tier() {
        return KilnRecipe.TIER_CLAY;
    }

    @Override
    protected void onFormed(com.jokerdayn.swindustry.multiblock.MultiblockInstance instance) {
        status = KilnStatus.IDLE;
    }

    @Override
    protected void onUnformed() {
        // A kiln pulled apart mid-burn loses the burn. The fuel is gone up the hole in the wall.
        litTime = 0;
        litDuration = 0;
        cookProgress = 0;
        activeJobKey = null;
        status = KilnStatus.INCOMPLETE;
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
        if (!operational) {
            kiln.status = KilnStatus.INCOMPLETE;
        } else {
            if (kiln.litTime > 0) {
                kiln.litTime--;
            }
            dirty = kiln.runCookingStep(level) || dirty;
        }

        BlockState currentState = kiln.getBlockState();
        boolean shouldLookLit = operational && kiln.isLit();
        if (currentState.hasProperty(ClayKilnPortBlock.LIT)
            && currentState.getValue(ClayKilnPortBlock.LIT) != shouldLookLit) {
            level.setBlock(pos, currentState.setValue(ClayKilnPortBlock.LIT, shouldLookLit), Block.UPDATE_ALL);
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
            status = KilnStatus.IDLE;
            return coolProgress();
        }

        Job job = findJob(level, input);
        if (job == null || job.result().isEmpty()) {
            status = KilnStatus.INVALID_RECIPE;
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

        status = KilnStatus.WORKING;
        cookProgress++;
        if (cookProgress >= cookDuration) {
            cookProgress = 0;
            finish(level, job);
            return true;
        }
        return dirty;
    }

    /** Heat in the clay fades instead of deleting a partial operation in a single tick. */
    private boolean coolProgress() {
        if (cookProgress <= 0) {
            activeJobKey = null;
            return false;
        }
        cookProgress = Math.max(0, cookProgress - 2);
        if (cookProgress == 0) {
            activeJobKey = null;
        }
        return true;
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
        ItemStack remainingInput = items.getStackInSlot(SLOT_INPUT).copy();
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

        // Experience leaves the kiln as it is earned rather than being banked against the output
        // slot. One less thing to serialise, and a visible sign the kiln did something.
        storedExperience += job.experience();
        if (storedExperience >= 1.0F && level instanceof net.minecraft.server.level.ServerLevel server) {
            int whole = (int) storedExperience;
            storedExperience -= whole;
            Direction facing = controllerFacing(getBlockState());
            BlockPos mouth = facing == null ? worldPosition : worldPosition.relative(facing);
            ExperienceOrb.award(server, net.minecraft.world.phys.Vec3.atCenterOf(mouth), whole);
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
                recipe.cookingTime(), recipe.experience(), recipe.minTier());
        }

        Optional<RecipeHolder<SmeltingRecipe>> furnaceMatch = furnaceRecipes.getRecipeFor(recipeInput, level);
        if (furnaceMatch.isPresent()) {
            RecipeHolder<SmeltingRecipe> holder = furnaceMatch.get();
            SmeltingRecipe recipe = holder.value();
            int time = scaledFurnaceCookingTime(recipe.getCookingTime());
            return new Job("smelting:" + holder.id(), recipe.assemble(recipeInput, level.registryAccess()),
                time, recipe.getExperience(), KilnRecipe.TIER_CLAY);
        }

        return null;
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
        return !stack.isEmpty() && (level == null || findJob(level, stack) != null);
    }

    /** One unit of work the kiln has agreed to do, flattened out of whichever recipe described it. */
    private record Job(String key, ItemStack result, int cookingTime, float experience, int minTier) {
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
        if (side == Direction.DOWN) {
            return outputAutomation;
        }
        if (side == Direction.UP) {
            return inputAutomation;
        }
        Direction facing = structureFacing();
        if (side == null || side == facing) {
            return loadingPortAutomation;
        }
        return fuelAutomation;
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
        float loadedExperience = tag.getFloat(KEY_EXPERIENCE);
        storedExperience = Float.isFinite(loadedExperience) && loadedExperience >= 0.0F
            ? loadedExperience
            : 0.0F;
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
        tag.putFloat(KEY_EXPERIENCE, storedExperience);
        if (cookProgress > 0 && activeJobKey != null) {
            tag.putString(KEY_ACTIVE_JOB, activeJobKey);
        }
    }

    /** A slot-mapped view used to keep pipes from extracting fuel or inserting into the output. */
    private final class RestrictedItemHandler implements IItemHandler {
        private final int[] slots;
        private final boolean insertion;
        private final boolean extraction;

        private RestrictedItemHandler(int[] slots, boolean insertion, boolean extraction) {
            this.slots = slots.clone();
            this.insertion = insertion;
            this.extraction = extraction;
        }

        @Override
        public int getSlots() {
            return slots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(mapped(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insertion ? items.insertItem(mapped(slot), stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!extraction) {
                return ItemStack.EMPTY;
            }
            int mappedSlot = mapped(slot);
            if (mappedSlot == SLOT_FUEL
                && items.getStackInSlot(mappedSlot).getBurnTime(RecipeType.SMELTING) > 0) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(mappedSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(mapped(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insertion && items.isItemValid(mapped(slot), stack);
        }

        private int mapped(int slot) {
            if (slot < 0 || slot >= slots.length) {
                throw new IndexOutOfBoundsException("Automation slot " + slot + " outside 0.." + (slots.length - 1));
            }
            return slots[slot];
        }
    }
}
