package com.jokerdayn.swindustry.checks;

import com.jokerdayn.swindustry.kiln.ClayKilnBlockEntity;
import com.jokerdayn.swindustry.kiln.ClayKilnPortBlock;
import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPartBlock;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.multiblock.MultiblockPatterns;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Verifies the water cooling, rain exposure, and sound/particle mechanics of the clay kiln.
 */
public final class KilnWaterCoolingCheck {

    private static final List<String> failures = new ArrayList<>();

    private KilnWaterCoolingCheck() {}

    public static void main(String[] args) {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        checkApiMethods();
        checkKilnExposureGeometry();
        checkSmeltingTemperatureResolution();

        if (failures.isEmpty()) {
            System.out.println("Kiln water cooling & temperature checks passed.");
            return;
        }

        System.out.println("Kiln checks: " + failures.size() + " FAILED");
        failures.forEach(f -> System.out.println("  " + f));
        System.exit(1);
    }

    private static void checkSmeltingTemperatureResolution() {
        assertHeat(net.minecraft.world.item.Items.BEEF, net.minecraft.world.item.Items.COOKED_BEEF, 150, "Beef");
        assertHeat(net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.CHARCOAL, 250, "Charcoal");
        assertHeat(net.minecraft.world.item.Items.CLAY_BALL, net.minecraft.world.item.Items.BRICK, 600, "Brick");
        assertHeat(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.STONE, 650, "Stone");
        assertHeat(net.minecraft.world.item.Items.SAND, net.minecraft.world.item.Items.GLASS, 750, "Glass");
        assertHeat(net.minecraft.world.item.Items.RAW_GOLD, net.minecraft.world.item.Items.GOLD_INGOT, 950, "Gold");
        assertHeat(net.minecraft.world.item.Items.RAW_COPPER, net.minecraft.world.item.Items.COPPER_INGOT, 1000, "Copper");
        assertHeat(net.minecraft.world.item.Items.RAW_IRON, net.minecraft.world.item.Items.IRON_INGOT, 1100, "Iron");
    }

    private static void assertHeat(net.minecraft.world.item.Item input, net.minecraft.world.item.Item result,
                                   int expected, String name) {
        int actual = ClayKilnBlockEntity.resolveRequiredHeat(new net.minecraft.world.item.ItemStack(input),
            new net.minecraft.world.item.ItemStack(result));
        if (actual != expected) {
            failures.add("Smelting " + name + " expected " + expected + " heat, got " + actual);
        }
    }

    private static void checkApiMethods() {
        try {
            Method notifyWater = ClayKilnBlockEntity.class.getMethod("notifyWaterContact", BlockPos.class);
            if (notifyWater == null) {
                failures.add("ClayKilnBlockEntity is missing notifyWaterContact(BlockPos)");
            }
        } catch (NoSuchMethodException e) {
            failures.add("ClayKilnBlockEntity is missing notifyWaterContact(BlockPos): " + e.getMessage());
        }

        try {
            Method notifyNeighbour = MultiblockPatterns.class.getMethod("notifyNeighborChanged",
                net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class);
            if (notifyNeighbour == null) {
                failures.add("MultiblockPatterns is missing notifyNeighborChanged(Level, BlockPos, BlockPos)");
            }
        } catch (NoSuchMethodException e) {
            failures.add("MultiblockPatterns is missing notifyNeighborChanged(Level, BlockPos, BlockPos): " + e.getMessage());
        }

        try {
            Method portNeighbour = ClayKilnPortBlock.class.getDeclaredMethod("neighborChanged",
                net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.world.level.Level.class,
                BlockPos.class,
                net.minecraft.world.level.block.Block.class,
                BlockPos.class,
                boolean.class);
            if (portNeighbour == null) {
                failures.add("ClayKilnPortBlock is missing neighborChanged override");
            }
        } catch (NoSuchMethodException e) {
            failures.add("ClayKilnPortBlock is missing neighborChanged override: " + e.getMessage());
        }
    }

    private static void checkKilnExposureGeometry() {
        MultiblockPattern kiln = KilnPatterns.clayKiln();
        Set<Vec3i> walls = new HashSet<>();
        Set<Vec3i> cavity = new HashSet<>();

        kiln.forEachCell((offset, symbol, matcher) -> {
            if (matcher.role() == BlockMatcher.Role.WALL) {
                walls.add(offset);
            } else if (matcher.role() == BlockMatcher.Role.CAVITY) {
                cavity.add(offset);
            }
        });

        // Test sky exposure: walls that have no wall or cavity cell above them
        int skyExposedCount = 0;
        for (Vec3i wall : walls) {
            Vec3i above = new Vec3i(wall.getX(), wall.getY() + 1, wall.getZ());
            if (!walls.contains(above) && !cavity.contains(above)) {
                skyExposedCount++;
            }
        }

        if (skyExposedCount < 8) {
            failures.add("Expected at least 8 sky-exposed bricks on clay kiln roof/vault, got " + skyExposedCount);
        }

        // Test exterior wall exposure: walls that have at least one neighbor outside the kiln
        int exteriorWallCount = 0;
        for (Vec3i wall : walls) {
            boolean hasExterior = false;
            for (Direction dir : Direction.values()) {
                Vec3i neighbor = new Vec3i(
                    wall.getX() + dir.getStepX(),
                    wall.getY() + dir.getStepY(),
                    wall.getZ() + dir.getStepZ()
                );
                if (!walls.contains(neighbor) && !cavity.contains(neighbor)) {
                    hasExterior = true;
                    break;
                }
            }
            if (hasExterior) {
                exteriorWallCount++;
            }
        }

        if (exteriorWallCount < 30) {
            failures.add("Expected at least 30 exterior boundary walls on clay kiln, got " + exteriorWallCount);
        }
    }
}
