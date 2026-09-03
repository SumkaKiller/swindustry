package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.item.PrimitiveEngineerGogglesItem;
import com.jokerdayn.swindustry.registry.ModBlockEntities;
import com.jokerdayn.swindustry.registry.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The mouth of the kiln, and the one block of it that thinks.
 *
 * <p>Everything a player does with a kiln happens here: what goes in, what comes out, and whether
 * the pile of bricks around it is actually a kiln yet. The other fifty-six blocks are just
 * bricks.</p>
 *
 * <p>It faces out of the structure, so you stand in front of the hole and the kiln is behind
 * it — which is also exactly how it ends up oriented if you simply place it facing yourself.</p>
 */
public class ClayKilnPortBlock extends BaseEntityBlock {

    public static final MapCodec<ClayKilnPortBlock> CODEC = simpleCodec(ClayKilnPortBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty CURED = BooleanProperty.create("cured");

    public ClayKilnPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, Boolean.FALSE)
            .setValue(CURED, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, CURED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Faces the player who placed it, which puts the hole on the outside of the kiln they are
        // about to build behind it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /** Light level, wired up from the block properties in {@code ModBlocks}. */
    public static int lightEmission(BlockState state) {
        return state.getValue(LIT) ? 13 : 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown() && PrimitiveEngineerGogglesItem.inspectWorn(player, kiln)) {
            return InteractionResult.CONSUME;
        }

        // Check on demand rather than trusting the last scheduled result: a player who just placed
        // the final brick should be able to open the kiln in the same breath.
        if (!kiln.revalidate()) {
            kiln.tellPlayerWhatIsMissing(player);
            return InteractionResult.CONSUME;
        }

        player.openMenu(kiln, buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln ? kiln : null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln) {
                kiln.dropContents();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ClayKilnBlockEntity kiln) {
            if (level.getFluidState(fromPos).is(net.minecraft.tags.FluidTags.WATER)) {
                kiln.notifyWaterContact(fromPos);
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
    }

    // ------------------------------------------------------------------

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);

        // Glow and crackle at the mouth.
        if (random.nextInt(8) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F, false);
        }
        double mouthX = pos.getX() + 0.5 + facing.getStepX() * 0.52;
        double mouthZ = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
        level.addParticle(ParticleTypes.SMOKE, mouthX, pos.getY() + 0.4 + random.nextDouble() * 0.3, mouthZ,
            0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, mouthX, pos.getY() + 0.25 + random.nextDouble() * 0.2, mouthZ,
            0.0, 0.0, 0.0);

        // Smoke leaving the flue. If the chimney is choked, smoke cannot escape upwards,
        // and thick black smoke billows from the mouth instead.
        BlockPos flue = KilnPatterns.clayKiln().toWorld(pos, facing, FLUE_TOP);
        BlockPos chimneyExit = flue;
        boolean choked = false;

        for (int step = 1; step <= 4; step++) {
            BlockPos above = chimneyExit.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                choked = true;
                break;
            }
            if (level.getBlockState(above.north()).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(above.south()).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(above.east()).is(ModTags.Blocks.KILN_WALL)
                && level.getBlockState(above.west()).is(ModTags.Blocks.KILN_WALL)) {
                chimneyExit = above;
            } else {
                break;
            }
        }
        if (!choked) {
            BlockState exitAbove = level.getBlockState(chimneyExit.above());
            if (!exitAbove.isAir() && !exitAbove.canBeReplaced()) {
                choked = true;
            }
        }

        if (choked) {
            level.addParticle(ParticleTypes.SMOKE, mouthX, pos.getY() + 0.35 + random.nextDouble() * 0.2, mouthZ,
                facing.getStepX() * 0.015, 0.01, facing.getStepZ() * 0.01);
        } else {
            // Small, frequent smoke at chimney exit that drifts gently and does not shoot up into the sky
            level.addParticle(ParticleTypes.SMOKE,
                chimneyExit.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25,
                chimneyExit.getY() + 0.85 + random.nextDouble() * 0.1,
                chimneyExit.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25,
                (random.nextDouble() - 0.5) * 0.005, 0.015 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.005);
        }
    }

    /** Where the flue opens, relative to the port, in the pattern's authored orientation. */
    public static final Vec3i FLUE_TOP = new Vec3i(0, 3, 2);

    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClayKilnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.CLAY_KILN.get(),
            ClayKilnBlockEntity::serverTick);
    }
}
