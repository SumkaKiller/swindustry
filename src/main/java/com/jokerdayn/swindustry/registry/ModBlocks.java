package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.campfire.PrimitiveCampfireBlock;
import com.jokerdayn.swindustry.kiln.ClayKilnPortBlock;
import com.jokerdayn.swindustry.multiblock.MultiblockPartBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block this mod adds. There are only three; the machines are made of them. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(SWIndustry.MODID);

    /**
     * A heap of sticks and logs waiting for a spark. Light comes from the block state, so an unlit
     * pile is dark, a burning one lights the beach, and embers are dark again.
     */
    public static final DeferredBlock<PrimitiveCampfireBlock> PRIMITIVE_CAMPFIRE = BLOCKS.register(
        "primitive_campfire",
        () -> new PrimitiveCampfireBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.PODZOL)
            .strength(0.6f)
            .sound(SoundType.WOOD)
            .lightLevel(PrimitiveCampfireBlock::lightEmission)
            .noOcclusion()
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY))
    );

    /**
     * Clay, shaped and dried but never fired. Forty-three of them make a kiln.
     *
     * <p>A {@link MultiblockPartBlock}, which is the whole of its cleverness: it holds no data and
     * costs nothing extra, it just tells any machine it belonged to when a player takes it away.</p>
     */
    public static final DeferredBlock<MultiblockPartBlock> RAW_CLAY_BRICKS = BLOCKS.register(
        "raw_clay_bricks",
        () -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.2f, 3.0f)
            .sound(SoundType.MUD_BRICKS))
    );

    /** The mouth of the kiln, and the only part of it that thinks. */
    public static final DeferredBlock<ClayKilnPortBlock> CLAY_KILN_PORT = BLOCKS.register(
        "clay_kiln_port",
        () -> new ClayKilnPortBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.5f, 3.0f)
            .sound(SoundType.MUD_BRICKS)
            .lightLevel(ClayKilnPortBlock::lightEmission))
    );

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
