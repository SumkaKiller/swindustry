package com.jokerdayn.swindustry.checks;

import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * Headless check on the geometry of the multiblock framework and the kiln built on it.
 *
 * <p>Rotation is the part of a multiblock that is easy to get subtly wrong and hard to notice: a
 * kiln that only assembles when it happens to face north looks like a bug in the player's building
 * rather than a bug in the mod. This runs the arithmetic in all four facings and insists it is
 * self-consistent, so a wrong sign in a rotation matrix fails the build instead of shipping.</p>
 *
 * <p>Run with {@code ./gradlew multiblockChecks}. Needs no world and no server — everything here is
 * arithmetic over {@code BlockPos}, and the only reason it bootstraps the game at all is that
 * touching a {@code DeferredBlock} reaches into the block registry.</p>
 */
public final class MultiblockGeometryCheck {

    /** Where the kiln from {@code furnace.nbt} puts its numbers. */
    private static final int EXPECTED_WALLS = 44;    // 43 bricks plus the loading port
    private static final int EXPECTED_CAVITY = 22;   // 3x3x2 firebox, the throat, three of flue
    private static final int EXPECTED_TOTAL_CELLS = 5 * 6 * 5;

    private static final Direction[] HORIZONTALS =
        {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final List<String> failures = new ArrayList<>();

    private MultiblockGeometryCheck() {}

    public static void main(String[] args) {
        // DeferredBlock reaches into BuiltInRegistries the moment ModBlocks is touched, and that
        // refuses to answer before the game has bootstrapped. Same three lines the worldgen checks
        // in SW: Worldgen Core open with.
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        MultiblockPattern kiln = KilnPatterns.CLAY_KILN;

        Map<BlockMatcher.Role, List<Vec3i>> byRole = new EnumMap<>(BlockMatcher.Role.class);
        for (BlockMatcher.Role role : BlockMatcher.Role.values()) {
            byRole.put(role, new ArrayList<>());
        }
        List<Vec3i> controllerCells = new ArrayList<>();
        kiln.forEachCell((offset, symbol, matcher) -> {
            byRole.get(matcher.role()).add(offset);
            if (symbol == kiln.controllerChar()) {
                controllerCells.add(offset);
            }
        });

        List<Vec3i> walls = byRole.get(BlockMatcher.Role.WALL);
        List<Vec3i> cavity = byRole.get(BlockMatcher.Role.CAVITY);
        int total = walls.size() + cavity.size() + byRole.get(BlockMatcher.Role.IGNORED).size();

        check("footprint is 5x6x5", kiln.size().equals(new Vec3i(5, 6, 5)),
            "got " + kiln.size());
        check("every cell is accounted for", total == EXPECTED_TOTAL_CELLS,
            "walls " + walls.size() + " + cavity " + cavity.size()
                + " + ignored " + byRole.get(BlockMatcher.Role.IGNORED).size() + " = " + total);
        check("44 blocks to build", walls.size() == EXPECTED_WALLS && kiln.blockCount() == EXPECTED_WALLS,
            "walls " + walls.size() + ", blockCount " + kiln.blockCount());
        check("22 cells must stay clear", cavity.size() == EXPECTED_CAVITY,
            "got " + cavity.size());
        check("exactly one controller cell, at the origin",
            controllerCells.size() == 1 && controllerCells.get(0).equals(Vec3i.ZERO),
            "got " + controllerCells);

        checkFlue(cavity);
        checkRotations(kiln, walls, cavity);
        checkReverseLookup(kiln, walls);
        checkShellSymmetry(walls, controllerCells.get(0));

        if (failures.isEmpty()) {
            System.out.println("\nmultiblock geometry: all checks passed");
            return;
        }
        System.out.println("\nmultiblock geometry: " + failures.size() + " FAILED");
        failures.forEach(f -> System.out.println("  " + f));
        System.exit(1);
    }

    /**
     * The flue has to be a single unbroken column, or smoke leaves the kiln through a wall and the
     * particle plume in {@code ClayKilnPortBlock} points at the wrong block.
     */
    private static void checkFlue(List<Vec3i> cavity) {
        Set<Vec3i> cells = new HashSet<>(cavity);
        for (int y = 2; y <= 5; y++) {
            Vec3i expected = new Vec3i(0, y, 2);
            check("flue is clear at y=" + y, cells.contains(expected), "missing " + expected);
        }
        long aboveThroat = cavity.stream().filter(c -> c.getY() >= 3).count();
        check("flue is exactly three cells tall", aboveThroat == 3, "got " + aboveThroat);
    }

    /**
     * Rotation must be a bijection that keeps the machine behind its controller.
     *
     * <p>Two things are checked per facing: that no two cells land on the same block, and that the
     * body of the machine sits on the far side of the controller from the way it faces. The second
     * is the one that catches a sign error — a pattern rotated the wrong way still occupies 150
     * distinct positions, it just grows out through the front of the loading hole.</p>
     */
    private static void checkRotations(MultiblockPattern kiln, List<Vec3i> walls, List<Vec3i> cavity) {
        BlockPos origin = new BlockPos(100, 64, -250);   // deliberately off-axis and negative

        for (Direction facing : HORIZONTALS) {
            Set<BlockPos> seen = new HashSet<>();
            List<Vec3i> all = new ArrayList<>(walls);
            all.addAll(cavity);
            for (Vec3i offset : all) {
                BlockPos pos = kiln.toWorld(origin, facing, offset);
                check("facing " + facing + ": no two cells share a block", seen.add(pos),
                    offset + " collides at " + pos);
            }

            // The centre of the firebox, relative to the controller, must point away from `facing`.
            double sumX = 0;
            double sumZ = 0;
            for (Vec3i offset : cavity) {
                BlockPos pos = kiln.toWorld(origin, facing, offset);
                sumX += pos.getX() - origin.getX();
                sumZ += pos.getZ() - origin.getZ();
            }
            double alongFacing = (sumX / cavity.size()) * facing.getStepX()
                + (sumZ / cavity.size()) * facing.getStepZ();
            check("facing " + facing + ": the kiln sits behind its port", alongFacing < -0.5,
                "body offset along facing is " + alongFacing + " (should be clearly negative)");

            // And the block directly in front of the port must not be part of the kiln, or a
            // player could never reach the hole.
            BlockPos inFront = origin.relative(facing);
            boolean blocked = all.stream().anyMatch(o -> kiln.toWorld(origin, facing, o).equals(inFront));
            check("facing " + facing + ": the port opens onto free space", !blocked,
                inFront + " is part of the structure");
        }
    }

    /**
     * {@code candidateControllerPositions} is the inverse of {@code toWorld}. If it ever misses,
     * mining a brick out of a wall leaves the kiln running on a hole.
     */
    private static void checkReverseLookup(MultiblockPattern kiln, List<Vec3i> walls) {
        BlockPos origin = new BlockPos(-31, 71, 4096);

        for (Direction facing : HORIZONTALS) {
            for (Vec3i offset : walls) {
                BlockPos member = kiln.toWorld(origin, facing, offset);
                List<BlockPos> candidates = kiln.candidateControllerPositions(member);
                check("facing " + facing + ": breaking " + offset + " finds its controller",
                    candidates.contains(origin),
                    "candidates for " + member + " did not include " + origin);
            }
        }

        // Sanity on the size of that list: four facings over the non-controller walls, plus the
        // member itself. Anything much larger means duplicated work on every block break.
        int expected = (walls.size() - 1) * 4 + 1;
        int actual = kiln.candidateControllerPositions(BlockPos.ZERO).size();
        check("reverse lookup checks " + expected + " positions", actual == expected,
            "got " + actual);
    }

    /**
     * The shell is four-fold symmetric about the kiln's axis — that is what lets the same pattern
     * serve all four facings without a second set of layer strings.
     */
    private static void checkShellSymmetry(List<Vec3i> walls, Vec3i controller) {
        // The axis runs through the flue, at authored (0, *, 2) relative to the controller.
        Set<Vec3i> shell = new HashSet<>(walls);
        int axisZ = 2;
        for (Vec3i offset : walls) {
            // a quarter turn about (0, axisZ): (dx, dz) -> (-dz, dx) measured from the axis
            int dx = offset.getX();
            int dz = offset.getZ() - axisZ;
            Vec3i turned = new Vec3i(-dz, offset.getY(), dx + axisZ);
            check("shell is symmetric under a quarter turn", shell.contains(turned),
                offset + " turns to " + turned + ", which is not part of the shell");
        }
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            return;
        }
        String message = what + " — " + detail;
        if (!failures.contains(message)) {
            failures.add(message);
        }
    }
}
