package com.jokerdayn.swindustry.command;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnPortBlock;
import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Debug commands for SW: Industry. */
@EventBusSubscriber(modid = SWIndustry.MODID)
public final class ModCommands {

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("swindustry")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn_kiln")
                    .executes(ctx -> spawnKiln(ctx.getSource(), false))
                )
                .then(Commands.literal("spawn_cured_kiln")
                    .executes(ctx -> spawnKiln(ctx.getSource(), true))
                )
        );

        dispatcher.register(
            Commands.literal("spawn_kiln")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> spawnKiln(ctx.getSource(), false))
        );

        dispatcher.register(
            Commands.literal("spawn_cured_kiln")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> spawnKiln(ctx.getSource(), true))
        );
    }

    private static int spawnKiln(CommandSourceStack source, boolean cured) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can spawn a kiln"));
            return 0;
        }

        Level level = player.level();
        Direction facing = player.getDirection();
        Direction portFacing = facing.getOpposite();
        BlockPos controllerPos = player.blockPosition().relative(facing, 3);

        MultiblockPattern pattern = KilnPatterns.clayKiln();
        BlockState wallState = cured
            ? ModBlocks.CLAY_BRICKS.get().defaultBlockState()
            : ModBlocks.RAW_CLAY_BRICKS.get().defaultBlockState();
        BlockState portState = ModBlocks.CLAY_KILN_PORT.get().defaultBlockState()
            .setValue(ClayKilnPortBlock.FACING, portFacing)
            .setValue(ClayKilnPortBlock.CURED, cured);

        pattern.forEachCell((offset, symbol, matcher) -> {
            BlockPos target = pattern.toWorld(controllerPos, portFacing, offset);
            if (symbol == pattern.controllerChar()) {
                level.setBlock(target, portState, 3);
            } else if (matcher.role() == BlockMatcher.Role.WALL) {
                level.setBlock(target, wallState, 3);
            } else if (matcher.role() == BlockMatcher.Role.CAVITY) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            }
        });

        source.sendSuccess(() -> Component.literal("Spawned " + (cured ? "cured" : "raw") + " clay kiln facing " + portFacing), true);
        return 1;
    }
}
