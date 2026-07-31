package com.jokerdayn.swindustry.campfire;

import com.jokerdayn.swindustry.Config;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import com.jokerdayn.swindustry.registry.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A heap of sticks and logs, and the first real obstacle of the game.
 *
 * <p>There is no flint and steel yet, so the only way to light it is to hold a piece of flint in
 * one hand and a stone in the other and strike them together over the wood. Each failed strike
 * improves the next chance: 15%, 25%, 50%, 75%, then a guaranteed catch on the fifth strike.</p>
 *
 * <p>Once lit the fire is on a clock. It burns, it goes out on its own, and what is left is a heap
 * of embers holding the charcoal that everything after this depends on.</p>
 */
public class PrimitiveCampfireBlock extends BaseEntityBlock {

    public static final MapCodec<PrimitiveCampfireBlock> CODEC = simpleCodec(PrimitiveCampfireBlock::new);
    public static final EnumProperty<CampfireStage> STAGE = EnumProperty.create("stage", CampfireStage.class);

    /** A shallow pile — you walk over it rather than into it. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

    private static final float FIRE_DAMAGE = 1.0F;

    public PrimitiveCampfireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, CampfireStage.UNLIT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(STAGE, CampfireStage.UNLIT);
    }

    /** Light level, wired up from the block properties in {@code ModBlocks}. */
    public static int lightEmission(BlockState state) {
        return state.getValue(STAGE) == CampfireStage.LIT ? 15 : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    /** Needs something solid beneath it — a campfire hanging in the air is not a campfire. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == net.minecraft.core.Direction.DOWN && !canSurvive(state, level, pos)
            ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
            : state;
    }

    // ------------------------------------------------------------------
    // Striking a spark
    // ------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(STAGE) != CampfireStage.UNLIT) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        // Flint and steel still works, for anyone who gets that far or is handed one.
        if (player.getItemInHand(hand).is(Items.FLINT_AND_STEEL)) {
            if (!level.isClientSide) {
                ignite(level, pos, state, player);
                player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Both hands are read no matter which one triggered this, so only the main hand is allowed
        // to act — otherwise one right-click would roll the dice twice. The off hand still has to
        // report whether the strike was handled, or it would go on to place a block afterwards.
        if (hand != InteractionHand.MAIN_HAND) {
            return isValidStrikerPair(main, off)
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return attemptStrike(level, pos, state, player, main, off);
    }

    /**
     * Also reachable with an empty main hand, so that flint in the off hand alone still gets the
     * "you need both" nudge rather than silence.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (state.getValue(STAGE) != CampfireStage.UNLIT) {
            return InteractionResult.PASS;
        }
        return attemptStrike(level, pos, state, player,
            player.getMainHandItem(), player.getOffhandItem()).result();
    }

    /** One attempt at a spark: validate the pair, spend the cooldown, roll the dice. */
    private ItemInteractionResult attemptStrike(Level level, BlockPos pos, BlockState state,
                                                Player player, ItemStack main, ItemStack off) {
        if (!isValidStrikerPair(main, off)) {
            // Only complain if they were plausibly trying to light it. Someone holding a stack of
            // bricks is building, and swallowing that click to lecture them would be worse than
            // saying nothing.
            if (isStriker(main) || isStriker(off)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.swindustry.need_strikers"), true);
                }
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // The cooldown rides on the striker items themselves, so the player can see it swirl and
        // holding right-click cannot roll twenty times a second.
        if (player.getCooldowns().isOnCooldown(main.getItem())
            || player.getCooldowns().isOnCooldown(off.getItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        int cooldown = Config.STRIKE_COOLDOWN_TICKS.get();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(main.getItem(), cooldown);
            if (off.getItem() != main.getItem()) {
                player.getCooldowns().addCooldown(off.getItem(), cooldown);
            }
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
            0.6F, 1.0F + (level.random.nextFloat() - 0.5F) * 0.3F);

        PrimitiveCampfireBlockEntity campfire = getCampfire(level, pos);
        int attempt = campfire == null ? 1 : campfire.recordStrikeAttempt();
        if (CampfireIgnition.succeeds(attempt, level.random.nextDouble())) {
            ignite(level, pos, state, player);
        } else if (level instanceof ServerLevel server) {
            // A failed strike still throws sparks, so the player can see the attempt landed.
            server.sendParticles(ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
            server.sendParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.0);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Nullable
    private static PrimitiveCampfireBlockEntity getCampfire(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PrimitiveCampfireBlockEntity campfire ? campfire : null;
    }

    /**
     * Whether the pair of held items can throw a spark.
     *
     * <p>With {@link Config#REQUIRE_DISTINCT_STRIKERS} on — the default — one hand has to hold
     * flint and the other stone, in either order. Turn it off and any two striking materials will
     * do, including two of the same.</p>
     */
    public static boolean isValidStrikerPair(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (Config.REQUIRE_DISTINCT_STRIKERS.get()) {
            return (isFlint(a) && isStone(b)) || (isStone(a) && isFlint(b));
        }
        return isStriker(a) && isStriker(b);
    }

    private static boolean isFlint(ItemStack stack) {
        return stack.is(ModTags.Items.FIRE_STRIKER_FLINT);
    }

    private static boolean isStone(ItemStack stack) {
        return stack.is(ModTags.Items.FIRE_STRIKER_STONE);
    }

    private static boolean isStriker(ItemStack stack) {
        return isFlint(stack) || isStone(stack);
    }

    private void ignite(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        level.setBlock(pos, state.setValue(STAGE, CampfireStage.LIT), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level.getBlockEntity(pos) instanceof PrimitiveCampfireBlockEntity campfire) {
            campfire.onLit();
        }
    }

    // ------------------------------------------------------------------
    // Being on fire
    // ------------------------------------------------------------------

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(STAGE) == CampfireStage.LIT && entity instanceof LivingEntity && !entity.fireImmune()) {
            entity.hurt(level.damageSources().campfire(), FIRE_DAMAGE);
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        CampfireStage stage = state.getValue(STAGE);
        if (stage == CampfireStage.UNLIT) {
            return;
        }

        if (stage == CampfireStage.LIT) {
            if (random.nextInt(10) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
            }
            for (int i = 0; i < 2; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    pos.getY() + random.nextDouble() + random.nextDouble(),
                    pos.getZ() + 0.5 + random.nextDouble() / 3.0 * (random.nextBoolean() ? 1 : -1),
                    0.0, 0.07, 0.0);
            }
        } else if (random.nextInt(4) == 0) {
            // Embers still breathe a thread of smoke, so a spent fire reads as spent rather than broken.
            level.addParticle(ParticleTypes.SMOKE,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                pos.getY() + 0.4,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Block entity
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrimitiveCampfireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || state.getValue(STAGE) != CampfireStage.LIT) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.PRIMITIVE_CAMPFIRE.get(),
            PrimitiveCampfireBlockEntity::serverTick);
    }
}
