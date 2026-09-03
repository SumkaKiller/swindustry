package com.jokerdayn.swindustry.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plain block that knows to tell its machine when it is taken away.
 *
 * <p>Part blocks stay dumb on purpose: no block entity, no stored owner, nothing to keep in sync.
 * A wall of fifty-six of these costs exactly as much as fifty-six of any other block. The only
 * thing this class adds is the notification on removal, which is what lets a machine notice damage
 * immediately instead of on its next scheduled check.</p>
 */
public class MultiblockPartBlock extends Block {

    public MultiblockPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // onRemove also fires when a block merely changes one of its own properties. Only an actual
        // change of block can break a machine.
        if (!state.is(newState.getBlock())) {
            MultiblockPatterns.invalidateAround(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos fromPos, boolean isMoving) {
        // A change beside one of our walls may have flooded or obstructed the interior,
        // or water may have touched this brick block directly.
        MultiblockPatterns.notifyNeighborChanged(level, pos, fromPos);
    }
}
