package com.jokerdayn.swindustry.gametest;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import com.jokerdayn.swindustry.kiln.ClayKilnPortBlock;
import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Integration checks against registered blocks, real inventories and server-side persistence. */
@GameTestHolder(SWIndustry.MODID)
@PrefixGameTestTemplate(false)
public final class KilnCuringTests {
    private KilnCuringTests() {}

    @GameTest(template = "gametest/clay_kiln", timeoutTicks = 200)
    public static void emptyInputAndReload(GameTestHelper helper) {
        ClayKilnBlockEntity kiln = create(helper);
        inventory(kiln).setStackInSlot(1, new ItemStack(Items.STICK, 64));
        tick(kiln, 5);
        helper.assertTrue(!kiln.isLit() && inventory(kiln).getStackInSlot(1).getCount() == 64,
            "Fuel alone must not ignite an empty kiln");
        helper.assertTrue(kiln.requestCuring(), "Raw shell must accept curing");
        helper.assertTrue(!kiln.requestCuring(), "Repeated request must be idempotent");
        tick(kiln, 110);
        helper.assertTrue(kiln.isLit() && inventory(kiln).getStackInSlot(1).getCount() < 63,
            "Empty-input curing must ignite and replenish fuel");
        helper.assertTrue(inventory(kiln).getStackInSlot(0).isEmpty()
            && inventory(kiln).getStackInSlot(2).isEmpty(), "Curing must not create recipe items");
        CompoundTag saved = save(kiln);
        helper.assertTrue(saved.getBoolean("CuringRequested") && saved.getFloat("Soak") > 0,
            "Request and shell exposure must be saved");
        kiln = restore(kiln, saved);
        tick(kiln, 1);
        helper.assertTrue(save(kiln).getBoolean("CuringRequested")
            && save(kiln).getFloat("Soak") > saved.getFloat("Soak"),
            "A new block entity must resume saved curing");
        // Advance only exposure, retaining real wall tracking and remaining fuel.
        CompoundTag nearlyDone = save(kiln);
        nearlyDone.putFloat("Soak", 10000);
        kiln = restore(kiln, nearlyDone);
        tick(kiln, 1);
        helper.assertTrue(kiln.isCured() && !save(kiln).getBoolean("CuringRequested"),
            "Last fired wall must finish the request");
        helper.assertTrue(!kiln.requestCuring(), "Fired shell must reject a new request");
        int fuelLeft = inventory(kiln).getStackInSlot(1).getCount();
        tick(kiln, 150);
        helper.assertTrue(!kiln.isLit() && inventory(kiln).getStackInSlot(1).getCount() == fuelLeft,
            "Remaining fire must burn out without spending another fuel item");
        helper.succeed();
    }

    @GameTest(template = "gametest/clay_kiln", timeoutTicks = 200)
    public static void waitingAndBrokenShell(GameTestHelper helper) {
        ClayKilnBlockEntity kiln = create(helper);
        helper.assertTrue(kiln.requestCuring(), "Request may wait for fuel");
        tick(kiln, 2);
        helper.assertTrue(!kiln.isLit() && save(kiln).getBoolean("CuringRequested"),
            "No fuel must pause rather than discard request");
        inventory(kiln).setStackInSlot(1, new ItemStack(Items.STICK, 64));
        // An occupied output does not prevent firing the shell.
        inventory(kiln).setStackInSlot(2, new ItemStack(Items.STONE, 64));
        tick(kiln, 2);
        helper.assertTrue(kiln.isLit(), "Loaded fuel must start queued curing even with full output");
        BlockPos wall = kiln.instance().walls().stream()
            .filter(pos -> !pos.equals(kiln.getBlockPos())).findFirst().orElseThrow();
        helper.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        kiln.revalidate();
        helper.assertTrue(!kiln.isLit() && !save(kiln).getBoolean("CuringRequested"),
            "Breaking the shell must cancel curing");
        helper.assertTrue(!kiln.requestCuring(), "Broken shell must reject request");
        helper.getLevel().setBlock(wall, ModBlocks.RAW_CLAY_BRICKS.get().defaultBlockState(), 3);
        kiln.revalidate();
        tick(kiln, 2);
        helper.assertTrue(!kiln.isLit(), "Repair must not silently restart cancelled curing");
        helper.succeed();
    }

    @GameTest(template = "gametest/clay_kiln", timeoutTicks = 200)
    public static void normalRecipeStillIgnites(GameTestHelper helper) {
        ClayKilnBlockEntity kiln = create(helper);
        inventory(kiln).setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        inventory(kiln).setStackInSlot(1, new ItemStack(Items.COAL, 2));
        tick(kiln, 1);
        helper.assertTrue(kiln.isLit() && !save(kiln).getBoolean("CuringRequested"),
            "Ordinary recipes must still ignite without the curing button");
        helper.succeed();
    }

    private static ClayKilnBlockEntity create(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 0));
        var pattern = KilnPatterns.clayKiln();
        pattern.forEachCell((offset, symbol, matcher) -> {
            var state = symbol == pattern.controllerChar()
                ? ModBlocks.CLAY_KILN_PORT.get().defaultBlockState().setValue(ClayKilnPortBlock.FACING, Direction.NORTH)
                : matcher.role() == BlockMatcher.Role.WALL
                    ? ModBlocks.RAW_CLAY_BRICKS.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
            helper.getLevel().setBlock(pattern.toWorld(origin, Direction.NORTH, offset), state, 3);
        });
        ClayKilnBlockEntity kiln = (ClayKilnBlockEntity) helper.getLevel().getBlockEntity(origin);
        helper.assertTrue(kiln != null && kiln.revalidate(), "Test shell must form");
        return kiln;
    }

    private static ItemStackHandler inventory(ClayKilnBlockEntity kiln) {
        return (ItemStackHandler) kiln.itemHandler();
    }

    private static CompoundTag save(ClayKilnBlockEntity kiln) {
        return kiln.saveWithoutMetadata(kiln.getLevel().registryAccess());
    }

    private static ClayKilnBlockEntity restore(ClayKilnBlockEntity old, CompoundTag data) {
        var level = old.getLevel();
        var replacement = new ClayKilnBlockEntity(old.getBlockPos(), old.getBlockState());
        level.removeBlockEntity(old.getBlockPos());
        replacement.loadWithComponents(data, level.registryAccess());
        level.setBlockEntity(replacement);
        return replacement;
    }

    private static void tick(ClayKilnBlockEntity kiln, int count) {
        for (int i = 0; i < count; i++) {
            ClayKilnBlockEntity.serverTick(kiln.getLevel(), kiln.getBlockPos(), kiln.getBlockState(), kiln);
        }
    }
}

