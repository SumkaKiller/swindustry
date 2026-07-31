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
import net.minecraft.network.chat.Component;
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
    public static final int DATA_COUNT = 5;

    private static final String KEY_ITEMS = "Items";
    private static final String KEY_LIT_TIME = "LitTime";
    private static final String KEY_LIT_DURATION = "LitDuration";
    private static final String KEY_COOK_PROGRESS = "CookProgress";
    private static final String KEY_EXPERIENCE = "StoredExperience";

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_FUEL -> stack.getBurnTime(RecipeType.SMELTING) > 0;
                case SLOT_OUTPUT -> false;
                default -> true;
            };
        }
    };

    private int litTime;
    private int litDuration;
    private int cookProgress;
    private int cookDuration = KilnRecipe.DEFAULT_COOKING_TIME;
    private float storedExperience;

    private final RecipeManager.CachedCheck<SingleRecipeInput, KilnRecipe> kilnRecipes;
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> furnaceRecipes;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> litTime;
                case DATA_LIT_DURATION -> litDuration;
                case DATA_COOK_PROGRESS -> cookProgress;
                case DATA_COOK_DURATION -> cookDuration;
                case DATA_FORMED -> isFormed() ? 1 : 0;
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
    protected void onUnformed() {
        // A kiln pulled apart mid-burn loses the burn. The fuel is gone up the hole in the wall.
        litTime = 0;
        litDuration = 0;
        cookProgress = 0;
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
        boolean wasLit = kiln.isLit();
        boolean dirty = false;

        if (kiln.litTime > 0) {
            kiln.litTime--;
        }

        if (!kiln.revalidateIfStale()) {
            // Not a kiln right now. Whatever heat was left is not doing anything useful.
            if (kiln.cookProgress != 0) {
                kiln.cookProgress = 0;
                dirty = true;
            }
        } else {
            dirty = kiln.runCookingStep(level) || dirty;
        }

        if (wasLit != kiln.isLit() && state.hasProperty(ClayKilnPortBlock.LIT)) {
            level.setBlock(pos, state.setValue(ClayKilnPortBlock.LIT, kiln.isLit()), Block.UPDATE_ALL);
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

        Job job = input.isEmpty() ? null : findJob(level, input);
        boolean canProceed = job != null && job.isAllowedIn(tier()) && fitsInOutput(job.result());

        if (!canProceed) {
            // Heat already in the bricks bleeds away rather than vanishing, so a moment's
            // interruption is not a total loss.
            if (cookProgress > 0) {
                cookProgress = Math.max(0, cookProgress - 2);
                dirty = true;
            }
            return dirty;
        }

        cookDuration = job.cookingTime();

        if (!isLit() && !fuel.isEmpty()) {
            litTime = burnDuration(fuel);
            litDuration = litTime;
            if (isLit()) {
                consumeFuel(fuel);
                dirty = true;
            }
        }

        if (!isLit()) {
            if (cookProgress > 0) {
                cookProgress = Math.max(0, cookProgress - 2);
                dirty = true;
            }
            return dirty;
        }

        cookProgress++;
        if (cookProgress >= cookDuration) {
            cookProgress = 0;
            finish(level, job);
            dirty = true;
        }
        return dirty;
    }

    private void consumeFuel(ItemStack fuel) {
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) {
            items.setStackInSlot(SLOT_FUEL, remainder);
        }
    }

    private void finish(Level level, Job job) {
        items.getStackInSlot(SLOT_INPUT).shrink(1);

        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, job.result().copy());
        } else {
            output.grow(job.result().getCount());
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
            return true;
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
            KilnRecipe recipe = kilnMatch.get().value();
            return new Job(recipe.assemble(recipeInput, level.registryAccess()),
                recipe.cookingTime(), recipe.experience(), recipe.minTier());
        }

        Optional<RecipeHolder<SmeltingRecipe>> furnaceMatch = furnaceRecipes.getRecipeFor(recipeInput, level);
        if (furnaceMatch.isPresent()) {
            SmeltingRecipe recipe = furnaceMatch.get().value();
            int time = recipe.getCookingTime() > 0
                ? recipe.getCookingTime()
                : Config.KILN_DEFAULT_COOK_TICKS.get();
            return new Job(recipe.assemble(recipeInput, level.registryAccess()),
                time, recipe.getExperience(), KilnRecipe.TIER_CLAY);
        }

        return null;
    }

    /** One unit of work the kiln has agreed to do, flattened out of whichever recipe described it. */
    private record Job(ItemStack result, int cookingTime, float experience, int minTier) {
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

    public void dropContents() {
        if (level == null) {
            return;
        }
        NonNullList<ItemStack> contents = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            contents.set(slot, items.getStackInSlot(slot));
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
        if (tag.contains(KEY_ITEMS)) {
            items.deserializeNBT(registries, tag.getCompound(KEY_ITEMS));
        }
        litTime = tag.getInt(KEY_LIT_TIME);
        litDuration = tag.getInt(KEY_LIT_DURATION);
        cookProgress = tag.getInt(KEY_COOK_PROGRESS);
        storedExperience = tag.getFloat(KEY_EXPERIENCE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_ITEMS, items.serializeNBT(registries));
        tag.putInt(KEY_LIT_TIME, litTime);
        tag.putInt(KEY_LIT_DURATION, litDuration);
        tag.putInt(KEY_COOK_PROGRESS, cookProgress);
        tag.putFloat(KEY_EXPERIENCE, storedExperience);
    }
}
