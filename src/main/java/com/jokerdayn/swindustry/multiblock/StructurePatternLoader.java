package com.jokerdayn.swindustry.multiblock;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * High-performance, data-driven loader and exporter for multiblock patterns using Minecraft structure
 * templates (.nbt).
 *
 * <h2>Overview</h2>
 * <p>Allows large multiblock structures to be built and saved in-game using Structure Blocks, and then
 * loaded directly into a {@link MultiblockPattern} with zero manual ASCII drawing. It supports tag
 * mappings, cavity/ignored conventions, custom controller resolution, and round-trip NBT export.</p>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>{@code minecraft:structure_void} automatically maps to {@link BlockMatcher#ignored()}.</li>
 *   <li>{@code minecraft:air}, {@code cave_air}, and {@code void_air} map to {@link BlockMatcher#cavity()}.</li>
 *   <li>Unspecified coordinates within the bounding box default to {@link BlockMatcher#cavity()}.</li>
 * </ul>
 */
public final class StructurePatternLoader {

    private StructurePatternLoader() {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads a multiblock pattern directly from a resource stream using default block mappings.
     */
    public static MultiblockPattern load(InputStream stream, Block controllerBlock) {
        return builder()
            .fromStream(stream)
            .controller(controllerBlock)
            .build();
    }

    /**
     * Loads a multiblock pattern directly from an NBT compound tag.
     */
    public static MultiblockPattern load(CompoundTag nbt, Block controllerBlock) {
        return builder()
            .fromTag(nbt)
            .controller(controllerBlock)
            .build();
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static final class Builder {

        private CompoundTag rootTag;
        private final Map<String, BlockMatcher> nameMappings = new HashMap<>();
        private final Map<Block, BlockMatcher> blockMappings = new HashMap<>();
        private final List<PredicateRule> customRules = new ArrayList<>();

        private Predicate<BlockStateInfo> controllerSelector;
        private Vec3i explicitControllerPos;
        private char controllerChar = 'P';
        private boolean strict = false;

        private Builder() {}

        /** Loads the structure from a classpath resource or mod asset. */
        public Builder fromResource(ResourceLocation location) {
            String path = location.getPath().endsWith(".nbt") ? location.getPath() : location.getPath() + ".nbt";
            String fullPath = "data/" + location.getNamespace() + "/" + path;
            InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath);
            if (is == null) {
                // Also check without the data/ prefix in case path was already qualified
                is = getClass().getClassLoader().getResourceAsStream(location.getPath());
            }
            if (is == null) {
                throw new IllegalArgumentException("Could not find structure resource: " + fullPath);
            }
            return fromStream(is);
        }

        /** Loads the structure from an input stream (gzip compressed or uncompressed NBT). */
        public Builder fromStream(InputStream inputStream) {
            try (BufferedInputStream bis = new BufferedInputStream(inputStream)) {
                this.rootTag = readNbt(bis);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read multiblock structure NBT stream", e);
            }
            return this;
        }

        /** Loads the structure from a file on disk. */
        public Builder fromFile(File file) {
            try (InputStream is = new FileInputStream(file)) {
                return fromStream(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read structure file: " + file, e);
            }
        }

        /** Loads the structure from a file path. */
        public Builder fromPath(Path path) {
            return fromFile(path.toFile());
        }

        /** Uses an existing {@link CompoundTag}. */
        public Builder fromTag(CompoundTag tag) {
            this.rootTag = Objects.requireNonNull(tag, "tag");
            return this;
        }

        /** Uses an existing {@link StructureTemplate}. */
        public Builder fromTemplate(StructureTemplate template) {
            CompoundTag tag = new CompoundTag();
            template.save(tag);
            return fromTag(tag);
        }

        /** Maps a block by its string registry name (e.g. "swindustry:raw_clay_bricks"). */
        public Builder map(String blockName, BlockMatcher matcher) {
            nameMappings.put(blockName, matcher);
            return this;
        }

        /** Maps a block by its {@link ResourceLocation}. */
        public Builder map(ResourceLocation blockId, BlockMatcher matcher) {
            return map(blockId.toString(), matcher);
        }

        /** Maps a specific {@link Block} to a matcher. */
        public Builder map(Block block, BlockMatcher matcher) {
            blockMappings.put(block, matcher);
            return this;
        }

        /** Maps a supplier resolving to a {@link Block} to a matcher. */
        public Builder map(Supplier<? extends Block> blockSupplier, BlockMatcher matcher) {
            if (blockSupplier instanceof net.neoforged.neoforge.registries.DeferredHolder<?, ?> dh) {
                return map(dh.getId(), matcher);
            }
            customRules.add(info -> {
                try {
                    Block b = blockSupplier.get();
                    return (b != null && (b == info.block() || (info.block() == null && info.name().equals(BuiltInRegistries.BLOCK.getKey(b).toString())))) ? matcher : null;
                } catch (Exception e) {
                    return null;
                }
            });
            return this;
        }

        /** Maps all blocks carrying the given tag to a {@link BlockMatcher#tag(TagKey)}. */
        public Builder mapTag(TagKey<Block> tag) {
            return map(tag, BlockMatcher.tag(tag));
        }

        /** Maps a tag key to a custom matcher. */
        public Builder map(TagKey<Block> tag, BlockMatcher matcher) {
            customRules.add(info -> info.block() != null && info.block().defaultBlockState().is(tag) ? matcher : null);
            return this;
        }

        /** Explicitly designates a block type as {@link BlockMatcher#cavity()}. */
        public Builder mapCavity(Block block) {
            return map(block, BlockMatcher.cavity());
        }

        /** Explicitly designates a block type as {@link BlockMatcher#ignored()}. */
        public Builder mapIgnored(Block block) {
            return map(block, BlockMatcher.ignored());
        }

        /** Sets the controller by looking for the unique position with this block. */
        public Builder controller(Block block) {
            this.controllerSelector = info -> info.block() == block || (info.block() == null && info.name().equals(BuiltInRegistries.BLOCK.getKey(block).toString()));
            return this;
        }

        /** Sets the controller by looking for the unique position with this block supplier. */
        public Builder controller(Supplier<? extends Block> blockSupplier) {
            if (blockSupplier instanceof net.neoforged.neoforge.registries.DeferredHolder<?, ?> dh) {
                String targetName = dh.getId().toString();
                this.controllerSelector = info -> targetName.equals(info.name());
                return this;
            }
            this.controllerSelector = info -> {
                try {
                    Block b = blockSupplier.get();
                    return b != null && (info.block() == b || (info.block() == null && info.name().equals(BuiltInRegistries.BLOCK.getKey(b).toString())));
                } catch (Exception e) {
                    return false;
                }
            };
            return this;
        }

        /** Sets the controller by matching against a predicate. */
        public Builder controller(Predicate<BlockStateInfo> selector) {
            this.controllerSelector = selector;
            return this;
        }

        /** Sets the controller explicitly by local template grid coordinates (x, y, z). */
        public Builder controllerPos(int x, int y, int z) {
            this.explicitControllerPos = new Vec3i(x, y, z);
            return this;
        }

        /** Sets the controller explicitly by local template grid coordinates. */
        public Builder controllerPos(Vec3i pos) {
            this.explicitControllerPos = pos;
            return this;
        }

        /** Sets the character used internally for the controller anchor. Default is 'P'. */
        public Builder controllerChar(char c) {
            this.controllerChar = c;
            return this;
        }

        /** When true, any unmapped block will throw an exception instead of resolving to {@link BlockMatcher#block(Block)}. */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /** Compiles the structure template into a validated {@link MultiblockPattern}. */
        public MultiblockPattern build() {
            if (rootTag == null) {
                throw new IllegalStateException("No structure source provided (use fromResource, fromStream, or fromTag)");
            }

            // 1. Read size
            Vec3i size = readSize(rootTag);
            int sizeX = size.getX();
            int sizeY = size.getY();
            int sizeZ = size.getZ();
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalStateException("Invalid structure dimensions: " + size);
            }

            // 2. Read palette
            List<BlockStateInfo> palette = readPalette(rootTag);

            // 3. Read blocks and populate 3D grid
            int[][][] blockGrid = new int[sizeY][sizeZ][sizeX];
            // Default fill with air (index pointing to air or -1 for cavity)
            int airPaletteIndex = findOrRegisterAir(palette);
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        blockGrid[y][z][x] = airPaletteIndex;
                    }
                }
            }

            ListTag blocksTag = rootTag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockEntry = blocksTag.getCompound(i);
                Vec3i pos = readPos(blockEntry);
                int stateIndex = blockEntry.getInt("state");
                if (pos.getX() >= 0 && pos.getX() < sizeX
                    && pos.getY() >= 0 && pos.getY() < sizeY
                    && pos.getZ() >= 0 && pos.getZ() < sizeZ) {
                    blockGrid[pos.getY()][pos.getZ()][pos.getX()] = stateIndex;
                }
            }

            // 4. Resolve BlockMatcher for each palette entry
            Map<Integer, BlockMatcher> paletteMatchers = new HashMap<>();
            for (int i = 0; i < palette.size(); i++) {
                BlockStateInfo info = palette.get(i);
                BlockMatcher matcher = resolveMatcher(info);
                paletteMatchers.put(i, matcher);
            }

            // 5. Find controller position
            Vec3i controllerCell = explicitControllerPos;
            if (controllerCell == null) {
                if (controllerSelector == null) {
                    throw new IllegalStateException("Controller not specified; call controller(...) or controllerPos(...)");
                }
                List<Vec3i> candidates = new ArrayList<>();
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        for (int x = 0; x < sizeX; x++) {
                            int stateIdx = blockGrid[y][z][x];
                            BlockStateInfo info = palette.get(stateIdx);
                            if (controllerSelector.test(info)) {
                                candidates.add(new Vec3i(x, y, z));
                            }
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    throw new IllegalStateException("No controller block found in structure template matching criteria");
                }
                if (candidates.size() > 1) {
                    throw new IllegalStateException("Found " + candidates.size() + " controller candidates in structure template: " + candidates);
                }
                controllerCell = candidates.get(0);
            }

            // 6. Assign unique characters to matchers and build char[][][] grid
            Map<BlockMatcher, Character> matcherToChar = new HashMap<>();
            Map<Character, BlockMatcher> charToMatcher = new LinkedHashMap<>();

            // Assign controller character
            int controllerStateIdx = blockGrid[controllerCell.getY()][controllerCell.getZ()][controllerCell.getX()];
            BlockMatcher controllerMatcher = paletteMatchers.get(controllerStateIdx);
            if (controllerMatcher == null || controllerMatcher.role() != BlockMatcher.Role.WALL) {
                // Ensure controller has a valid WALL matcher
                BlockStateInfo ctrlInfo = palette.get(controllerStateIdx);
                ResourceLocation id = ResourceLocation.tryParse(ctrlInfo.name());
                controllerMatcher = BlockMatcher.block(() -> BuiltInRegistries.BLOCK.get(id));
            }
            charToMatcher.put(controllerChar, controllerMatcher);

            char nextChar = 'A';
            char[][][] charGrid = new char[sizeY][sizeZ][sizeX];

            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        if (x == controllerCell.getX() && y == controllerCell.getY() && z == controllerCell.getZ()) {
                            charGrid[y][z][x] = controllerChar;
                            continue;
                        }

                        int stateIdx = blockGrid[y][z][x];
                        BlockMatcher matcher = paletteMatchers.get(stateIdx);
                        if (matcher == null) {
                            matcher = BlockMatcher.cavity();
                        }

                        Character assigned = matcherToChar.get(matcher);
                        if (assigned == null) {
                            while (nextChar == controllerChar || charToMatcher.containsKey(nextChar)) {
                                nextChar++;
                            }
                            assigned = nextChar++;
                            matcherToChar.put(matcher, assigned);
                            charToMatcher.put(assigned, matcher);
                        }
                        charGrid[y][z][x] = assigned;
                    }
                }
            }

            return MultiblockPattern.of(charGrid, charToMatcher, controllerChar, controllerCell);
        }

        private BlockMatcher resolveMatcher(BlockStateInfo info) {
            String name = info.name();
            // 1. Check exact name mappings
            if (nameMappings.containsKey(name)) {
                return nameMappings.get(name);
            }
            // 2. Check block mappings
            if (info.block() != null && blockMappings.containsKey(info.block())) {
                return blockMappings.get(info.block());
            }
            // 3. Check custom rules
            for (PredicateRule rule : customRules) {
                BlockMatcher match = rule.evaluate(info);
                if (match != null) {
                    return match;
                }
            }
            // 4. Default conventions
            if ("minecraft:structure_void".equals(name)) {
                return BlockMatcher.ignored();
            }
            if ("minecraft:air".equals(name) || "minecraft:cave_air".equals(name) || "minecraft:void_air".equals(name)) {
                return BlockMatcher.cavity();
            }
            // 5. Fallback
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                if (strict) {
                    throw new IllegalStateException("Unmapped block in structure template: " + name);
                }
                return BlockMatcher.block(() -> BuiltInRegistries.BLOCK.get(id));
            }
            if (strict) {
                throw new IllegalStateException("Unrecognized and unmapped block in structure template: " + name);
            }
            return BlockMatcher.ignored();
        }

        private static int findOrRegisterAir(List<BlockStateInfo> palette) {
            for (int i = 0; i < palette.size(); i++) {
                if ("minecraft:air".equals(palette.get(i).name())) {
                    return i;
                }
            }
            palette.add(new BlockStateInfo("minecraft:air", Blocks.AIR, new CompoundTag()));
            return palette.size() - 1;
        }
    }

    // ------------------------------------------------------------------
    // Exporter
    // ------------------------------------------------------------------

    /**
     * Exports an existing {@link MultiblockPattern} into a standard Minecraft StructureTemplate NBT compound tag.
     */
    public static CompoundTag exportToTag(MultiblockPattern pattern, Function<BlockMatcher, String> blockNameProvider) {
        Vec3i size = pattern.size();
        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();

        CompoundTag root = new CompoundTag();
        root.put("size", new IntArrayTag(new int[]{sizeX, sizeY, sizeZ}));
        root.putInt("DataVersion", 3955); // 1.21.1 data version

        List<String> paletteNames = new ArrayList<>();
        Map<String, Integer> paletteIndices = new HashMap<>();

        // Add standard air
        paletteNames.add("minecraft:air");
        paletteIndices.put("minecraft:air", 0);

        // Compute controllerCell in grid
        // In MultiblockPattern, grid starts at (0,0,0) and size is (sizeX, sizeY, sizeZ).
        // Let's find min offset across all cells to derive controllerCell:
        int[] minOffset = new int[]{0, 0, 0};
        pattern.forEachCell((offset, symbol, matcher) -> {
            minOffset[0] = Math.min(minOffset[0], offset.getX());
            minOffset[1] = Math.min(minOffset[1], offset.getY());
            minOffset[2] = Math.min(minOffset[2], offset.getZ());
        });
        Vec3i controllerCell = new Vec3i(-minOffset[0], -minOffset[1], -minOffset[2]);
        ListTag blocksTag = new ListTag();

        pattern.forEachCell((offset, symbol, matcher) -> {
            String blockName;
            if (matcher.role() == BlockMatcher.Role.IGNORED) {
                blockName = "minecraft:structure_void";
            } else if (matcher.role() == BlockMatcher.Role.CAVITY) {
                blockName = "minecraft:air";
            } else {
                blockName = blockNameProvider.apply(matcher);
                if (blockName == null || blockName.isBlank()) {
                    blockName = "minecraft:stone";
                }
            }

            int paletteIdx = paletteIndices.computeIfAbsent(blockName, k -> {
                paletteNames.add(k);
                return paletteNames.size() - 1;
            });

            Vec3i gridPos = new Vec3i(
                offset.getX() + controllerCell.getX(),
                offset.getY() + controllerCell.getY(),
                offset.getZ() + controllerCell.getZ());

            CompoundTag blockEntry = new CompoundTag();
            blockEntry.put("pos", new IntArrayTag(new int[]{gridPos.getX(), gridPos.getY(), gridPos.getZ()}));
            blockEntry.putInt("state", paletteIdx);
            blocksTag.add(blockEntry);
        });

        ListTag paletteTag = new ListTag();
        for (String name : paletteNames) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            paletteTag.add(state);
        }

        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());

        return root;
    }

    /** Exports a pattern directly to an NBT file. */
    public static void exportToFile(MultiblockPattern pattern, Path targetFile,
                                    Function<BlockMatcher, String> blockNameProvider) throws IOException {
        CompoundTag tag = exportToTag(pattern, blockNameProvider);
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        try (OutputStream os = new FileOutputStream(targetFile.toFile())) {
            NbtIo.writeCompressed(tag, os);
        }
    }

    // ------------------------------------------------------------------
    // Helpers & Records
    // ------------------------------------------------------------------

    public record BlockStateInfo(String name, Block block, CompoundTag properties) {}

    @FunctionalInterface
    public interface PredicateRule {
        BlockMatcher evaluate(BlockStateInfo info);
    }

    private static CompoundTag readNbt(InputStream inputStream) throws IOException {
        inputStream.mark(2);
        int b1 = inputStream.read();
        int b2 = inputStream.read();
        inputStream.reset();

        // GZIP magic header 0x1F, 0x8B
        if (b1 == 0x1F && b2 == 0x8B) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        } else {
            return NbtIo.read(new DataInputStream(inputStream), NbtAccounter.unlimitedHeap());
        }
    }

    private static Vec3i readSize(CompoundTag tag) {
        if (tag.contains("size", Tag.TAG_INT_ARRAY)) {
            int[] arr = tag.getIntArray("size");
            if (arr.length >= 3) {
                return new Vec3i(arr[0], arr[1], arr[2]);
            }
        }
        if (tag.contains("size", Tag.TAG_LIST)) {
            ListTag list = tag.getList("size", Tag.TAG_INT);
            if (list.size() >= 3) {
                return new Vec3i(list.getInt(0), list.getInt(1), list.getInt(2));
            }
        }
        throw new IllegalStateException("Structure tag missing valid 'size' tag: " + tag);
    }

    private static Vec3i readPos(CompoundTag blockTag) {
        if (blockTag.contains("pos", Tag.TAG_INT_ARRAY)) {
            int[] arr = blockTag.getIntArray("pos");
            if (arr.length >= 3) {
                return new Vec3i(arr[0], arr[1], arr[2]);
            }
        }
        if (blockTag.contains("pos", Tag.TAG_LIST)) {
            ListTag list = blockTag.getList("pos", Tag.TAG_INT);
            if (list.size() >= 3) {
                return new Vec3i(list.getInt(0), list.getInt(1), list.getInt(2));
            }
        }
        throw new IllegalStateException("Block entry missing valid 'pos' tag: " + blockTag);
    }

    private static List<BlockStateInfo> readPalette(CompoundTag root) {
        List<BlockStateInfo> palette = new ArrayList<>();
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        if (paletteTag.isEmpty() && root.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) {
                paletteTag = palettes.getList(0);
            }
        }
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            String name = entry.getString("Name");
            CompoundTag props = entry.getCompound("Properties");
            Block block = null;
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                block = BuiltInRegistries.BLOCK.get(id);
            }
            palette.add(new BlockStateInfo(name, block, props));
        }
        return palette;
    }
}
