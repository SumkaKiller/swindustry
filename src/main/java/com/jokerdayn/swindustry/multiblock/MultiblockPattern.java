package com.jokerdayn.swindustry.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine shape, written down once and then matched against the world.
 *
 * <h2>Writing a pattern</h2>
 *
 * <p>Layers are given bottom-up. Inside a layer each string is one row along <b>+Z</b>, and each
 * character within that row steps along <b>+X</b> — so a layer reads exactly like a map drawn with
 * north at the top. Every layer must be rectangular and all layers must share a footprint.</p>
 *
 * <pre>{@code
 * MultiblockPattern.builder()
 *     .layer(" CPC ",
 *            "C___C",
 *            "C___C")
 *     .where('C', BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
 *     .where('P', BlockMatcher.blocks(ModBlocks.CLAY_KILN_PORT))
 *     .where('_', BlockMatcher.cavity())
 *     .where(' ', BlockMatcher.ignored())
 *     .controller('P')
 *     .build();
 * }</pre>
 *
 * <h2>Orientation</h2>
 *
 * <p>A pattern is authored as if its controller faced <b>north</b>, which puts the machine body
 * along +Z, away from the controller. {@link #match} rotates it into any of the four horizontal
 * facings, so nothing else in the codebase has to think about rotation.</p>
 *
 * <h2>Noticing damage</h2>
 *
 * <p>{@link #candidateControllerPositions} inverts the transform: given a block that was just
 * broken, it lists every position a controller could occupy that would have included that block. A
 * wall block uses this to take its machine apart the instant it is removed, rather than leaving the
 * machine to find out on its next scheduled check.</p>
 *
 * <p>Instances are immutable and safe to hold in a {@code static final} field.</p>
 */
public final class MultiblockPattern {

    /** Cell offset from the controller -> the character authored there. */
    private final Map<Vec3i, Character> cells;
    private final Map<Character, BlockMatcher> matchers;

    /** Wall cells as offsets from the controller, controller itself excluded. */
    private final List<Vec3i> wallOffsets;
    /** Cavity cells as offsets from the controller. */
    private final List<Vec3i> cavityOffsets;
    /** Distinct matchers used by wall cells, controller included. Keeps reverse lookups cheap. */
    private final Set<BlockMatcher> wallMatchers;

    private final Vec3i size;
    private final char controllerChar;

    private static final Direction[] HORIZONTALS =
        {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private MultiblockPattern(char[][][] grid, Map<Character, BlockMatcher> matchers,
                              char controllerChar, Vec3i controllerCell) {
        this.matchers = Map.copyOf(matchers);
        this.controllerChar = controllerChar;
        this.size = new Vec3i(grid[0][0].length, grid.length, grid[0].length);

        Map<Vec3i, Character> byOffset = new LinkedHashMap<>();
        List<Vec3i> walls = new ArrayList<>();
        List<Vec3i> cavities = new ArrayList<>();
        Set<BlockMatcher> wallMatcherSet = new LinkedHashSet<>();
        wallMatcherSet.add(matchers.get(controllerChar));

        for (int y = 0; y < grid.length; y++) {
            for (int z = 0; z < grid[y].length; z++) {
                for (int x = 0; x < grid[y][z].length; x++) {
                    char symbol = grid[y][z][x];
                    Vec3i offset = new Vec3i(
                        x - controllerCell.getX(),
                        y - controllerCell.getY(),
                        z - controllerCell.getZ());
                    byOffset.put(offset, symbol);

                    if (symbol == controllerChar) {
                        continue;
                    }
                    BlockMatcher matcher = matchers.get(symbol);
                    switch (matcher.role()) {
                        case WALL -> {
                            walls.add(offset);
                            wallMatcherSet.add(matcher);
                        }
                        case CAVITY -> cavities.add(offset);
                        case IGNORED -> { }
                    }
                }
            }
        }

        this.cells = Collections.unmodifiableMap(byOffset);
        this.wallOffsets = List.copyOf(walls);
        this.cavityOffsets = List.copyOf(cavities);
        this.wallMatchers = Set.copyOf(wallMatcherSet);
    }

    /**
     * Whether the given block could be one of this machine's walls at all.
     *
     * <p>A cheap filter for the reverse lookup: a block that no wall matcher would ever accept
     * cannot have belonged to this machine, so there is no point searching for a controller.</p>
     */
    public boolean couldBeWall(LevelReader level, BlockPos pos, BlockState state) {
        for (BlockMatcher matcher : wallMatchers) {
            if (matcher.matches(level, pos, state)) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Pattern dimensions as (x, y, z) in the authored orientation. */
    public Vec3i size() {
        return size;
    }

    /** How many blocks the machine costs to build, controller included. */
    public int blockCount() {
        return wallOffsets.size() + 1;
    }

    /** The character standing for the controller. Exposed for previews and diagnostics. */
    public char controllerChar() {
        return controllerChar;
    }

    /**
     * Tests the pattern against the world.
     *
     * @param controllerPos where the controller block sits
     * @param facing        the direction the controller faces, pointing out of the machine
     * @return the formed machine, or empty if any cell disagrees
     */
    public Optional<MultiblockInstance> match(LevelReader level, BlockPos controllerPos, Direction facing) {
        requireHorizontal(facing);

        Set<BlockPos> walls = new LinkedHashSet<>(wallOffsets.size() + 1);
        walls.add(controllerPos);
        for (Vec3i offset : wallOffsets) {
            BlockPos pos = toWorld(controllerPos, facing, offset);
            if (!accepts(level, pos, offset)) {
                return Optional.empty();
            }
            walls.add(pos);
        }

        Set<BlockPos> cavity = new LinkedHashSet<>(cavityOffsets.size());
        for (Vec3i offset : cavityOffsets) {
            BlockPos pos = toWorld(controllerPos, facing, offset);
            if (!accepts(level, pos, offset)) {
                return Optional.empty();
            }
            cavity.add(pos);
        }

        return Optional.of(new MultiblockInstance(controllerPos, facing,
            Collections.unmodifiableSet(walls), Collections.unmodifiableSet(cavity)));
    }

    /**
     * Which cell of the machine, if any, a world position occupies — the first missing block a
     * player should be told about. Returns empty when the whole pattern already matches.
     */
    public Optional<Mismatch> firstMismatch(LevelReader level, BlockPos controllerPos, Direction facing) {
        requireHorizontal(facing);
        for (Vec3i offset : wallOffsets) {
            BlockPos pos = toWorld(controllerPos, facing, offset);
            if (!accepts(level, pos, offset)) {
                return Optional.of(new Mismatch(pos, cells.get(offset), matchers.get(cells.get(offset))));
            }
        }
        for (Vec3i offset : cavityOffsets) {
            BlockPos pos = toWorld(controllerPos, facing, offset);
            if (!accepts(level, pos, offset)) {
                return Optional.of(new Mismatch(pos, cells.get(offset), matchers.get(cells.get(offset))));
            }
        }
        return Optional.empty();
    }

    /** One cell that failed to match, and what was expected there. */
    public record Mismatch(BlockPos pos, char symbol, BlockMatcher expected) {}

    // hasChunkAt is one of Mojang's "know what you are doing" deprecations rather than a removal
    // notice, and knowing what is loaded is exactly the question here: reading a block out of an
    // unloaded chunk would drag it into memory, and getBlockState would happily answer "air".
    @SuppressWarnings("deprecation")
    private boolean accepts(LevelReader level, BlockPos pos, Vec3i offset) {
        // A machine reaching into unloaded chunks is neither formed nor broken. Refusing to form is
        // the safe answer, because guessing would let it run on blocks nobody has read.
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return matchers.get(cells.get(offset)).matches(level, pos, state);
    }

    /**
     * Maps a cell offset in the authored orientation to its world position.
     *
     * <p>The authored orientation has the controller facing north, so rotating north onto
     * {@code facing} is the entire transform.</p>
     */
    public BlockPos toWorld(BlockPos controllerPos, Direction facing, Vec3i offset) {
        int x = offset.getX();
        int y = offset.getY();
        int z = offset.getZ();
        return switch (facing) {
            case NORTH -> controllerPos.offset(x, y, z);
            case EAST -> controllerPos.offset(-z, y, x);
            case SOUTH -> controllerPos.offset(-x, y, -z);
            case WEST -> controllerPos.offset(z, y, -x);
            default -> throw new IllegalArgumentException("Multiblocks only rotate horizontally, got " + facing);
        };
    }

    /**
     * Every position a controller could occupy such that {@code memberPos} would be one of its
     * walls, across all four facings.
     *
     * <p>Wall blocks use this to tear their machine down the moment they are broken. The list is
     * exact — it never misses a real controller and never reaches outside the pattern — but the
     * entries are only <em>candidates</em>: the caller still has to check what is actually there.</p>
     */
    public List<BlockPos> candidateControllerPositions(BlockPos memberPos) {
        List<BlockPos> candidates = new ArrayList<>(wallOffsets.size() * 4 + 1);
        candidates.add(memberPos.immutable());
        for (Direction facing : HORIZONTALS) {
            for (Vec3i offset : wallOffsets) {
                // memberPos = controller + rotate(offset)  =>  controller = memberPos - rotate(offset)
                BlockPos rotated = toWorld(BlockPos.ZERO, facing, offset);
                candidates.add(memberPos.subtract(rotated));
            }
        }
        return candidates;
    }

    /**
     * Walks the pattern cell by cell in the authored orientation. Handy for a hologram preview or a
     * "what am I still missing" command.
     */
    public void forEachCell(CellConsumer consumer) {
        cells.forEach((offset, symbol) -> consumer.accept(offset, symbol, matchers.get(symbol)));
    }

    /** Callback for {@link #forEachCell}. */
    @FunctionalInterface
    public interface CellConsumer {
        void accept(Vec3i offset, char symbol, BlockMatcher matcher);
    }

    private static void requireHorizontal(Direction facing) {
        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("Multiblocks only rotate horizontally, got " + facing);
        }
    }

    // ------------------------------------------------------------------

    /** Collects layers and character bindings, then validates them into a pattern. */
    public static final class Builder {

        private final List<String[]> layers = new ArrayList<>();
        private final Map<Character, BlockMatcher> matchers = new HashMap<>();
        private Character controllerChar;

        private Builder() {}

        /** Adds the next layer upwards. Rows run along +Z, characters within a row along +X. */
        public Builder layer(String... rows) {
            if (rows.length == 0) {
                throw new IllegalArgumentException("A layer needs at least one row");
            }
            int width = rows[0].length();
            for (String row : rows) {
                if (row.length() != width) {
                    throw new IllegalArgumentException(
                        "Layer rows must all be the same length; got \"" + row + "\" among rows of " + width);
                }
            }
            if (!layers.isEmpty()) {
                String[] first = layers.get(0);
                if (first.length != rows.length || first[0].length() != width) {
                    throw new IllegalArgumentException("All layers must share a footprint; expected "
                        + first[0].length() + "x" + first.length + ", got " + width + "x" + rows.length);
                }
            }
            layers.add(rows.clone());
            return this;
        }

        /** Binds a character to a matcher. */
        public Builder where(char symbol, BlockMatcher matcher) {
            matchers.put(symbol, matcher);
            return this;
        }

        /**
         * Marks the character standing for the controller block. It must appear exactly once across
         * the whole pattern; that cell is the anchor everything else is measured from.
         */
        public Builder controller(char symbol) {
            this.controllerChar = symbol;
            return this;
        }

        public MultiblockPattern build() {
            if (layers.isEmpty()) {
                throw new IllegalStateException("A pattern needs at least one layer");
            }
            if (controllerChar == null) {
                throw new IllegalStateException("A pattern needs a controller character");
            }
            if (!matchers.containsKey(controllerChar)) {
                throw new IllegalStateException(
                    "The controller character '" + controllerChar + "' has no matcher bound to it");
            }
            if (matchers.get(controllerChar).role() != BlockMatcher.Role.WALL) {
                throw new IllegalStateException("The controller must use a WALL-role matcher");
            }

            int sizeY = layers.size();
            int sizeZ = layers.get(0).length;
            int sizeX = layers.get(0)[0].length();

            char[][][] grid = new char[sizeY][sizeZ][sizeX];
            Vec3i controllerCell = null;
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    String row = layers.get(y)[z];
                    for (int x = 0; x < sizeX; x++) {
                        char symbol = row.charAt(x);
                        if (!matchers.containsKey(symbol)) {
                            throw new IllegalStateException(
                                "Pattern uses '" + symbol + "' but nothing was bound to it");
                        }
                        if (symbol == controllerChar) {
                            if (controllerCell != null) {
                                throw new IllegalStateException(
                                    "The controller character '" + symbol + "' appears more than once");
                            }
                            controllerCell = new Vec3i(x, y, z);
                        }
                        grid[y][z][x] = symbol;
                    }
                }
            }
            if (controllerCell == null) {
                throw new IllegalStateException(
                    "The controller character '" + controllerChar + "' does not appear in the pattern");
            }

            return new MultiblockPattern(grid, matchers, controllerChar, controllerCell);
        }
    }
}
