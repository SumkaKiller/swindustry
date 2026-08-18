package com.jokerdayn.swindustry.checks;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.kiln.KilnPatterns;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.multiblock.StructurePatternLoader;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModTags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Headless verification suite for the NBT Structure Pattern loader and exporter engine.
 */
public final class StructurePatternCheck {

    private static final List<String> failures = new ArrayList<>();

    private StructurePatternCheck() {}

    public static void main(String[] args) throws Exception {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        testKilnNbtLoading();
        testNbtExportAndRoundTrip();
        testCustomMappingsAndConventions();
        testControllerAutoDiscovery();

        if (failures.isEmpty()) {
            System.out.println("StructurePatternCheck: all NBT multiblock engine checks passed.");
            return;
        }

        System.out.println("StructurePatternCheck: " + failures.size() + " FAILED");
        failures.forEach(f -> System.out.println("  " + f));
        System.exit(1);
    }

    private static void testKilnNbtLoading() {
        MultiblockPattern kiln = KilnPatterns.clayKiln();

        check("Kiln footprint is 5x5x5", kiln.size().equals(new Vec3i(5, 5, 5)), "got " + kiln.size());
        check("Kiln blockCount is 57", kiln.blockCount() == 57, "got " + kiln.blockCount());

        List<Vec3i> walls = new ArrayList<>();
        List<Vec3i> cavities = new ArrayList<>();
        List<Vec3i> ignored = new ArrayList<>();

        kiln.forEachCell((offset, symbol, matcher) -> {
            switch (matcher.role()) {
                case WALL -> walls.add(offset);
                case CAVITY -> cavities.add(offset);
                case IGNORED -> ignored.add(offset);
            }
        });

        check("Kiln wall cells = 57 (including controller)", walls.size() == 57, "got " + walls.size());
        check("Kiln cavity cells = 20", cavities.size() == 20, "got " + cavities.size());
        check("Kiln total accounted cells = 125", (walls.size() + cavities.size() + ignored.size()) == 125,
            "got total " + (walls.size() + cavities.size() + ignored.size()));
    }

    private static void testNbtExportAndRoundTrip() throws Exception {
        MultiblockPattern original = KilnPatterns.clayKiln();

        // Export pattern to NBT Tag
        CompoundTag exportedTag = StructurePatternLoader.exportToTag(original, matcher -> {
            if (matcher.role() == BlockMatcher.Role.WALL) {
                return "swindustry:raw_clay_bricks";
            }
            return "minecraft:air";
        });

        check("Exported tag contains 'size'", exportedTag.contains("size"), "missing size");
        check("Exported tag contains 'palette'", exportedTag.contains("palette"), "missing palette");
        check("Exported tag contains 'blocks'", exportedTag.contains("blocks"), "missing blocks");

        // Round trip write to byte array and reload
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(exportedTag, baos);

        byte[] bytes = baos.toByteArray();
        check("Compressed NBT bytes non empty", bytes.length > 0, "0 bytes");

        MultiblockPattern reloaded = StructurePatternLoader.builder()
            .fromStream(new ByteArrayInputStream(bytes))
            .map("swindustry:raw_clay_bricks", BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
            .controllerPos(2, 1, 0)
            .build();

        check("Reloaded pattern footprint matches", reloaded.size().equals(original.size()), "got " + reloaded.size());
        check("Reloaded pattern blockCount matches", reloaded.blockCount() == original.blockCount(),
            "expected " + original.blockCount() + " got " + reloaded.blockCount());
    }

    private static void testCustomMappingsAndConventions() {
        // Build a synthetic 3x3x3 structure in NBT
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("size", new int[]{3, 3, 3});

        net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
        palette.add(createState("minecraft:structure_void"));
        palette.add(createState("minecraft:air"));
        palette.add(createState("minecraft:stone"));
        palette.add(createState("minecraft:diamond_block"));
        tag.put("palette", palette);

        net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
        // Controller at (1, 0, 0)
        blocks.add(createBlock(1, 0, 0, 3));
        // Wall at (0, 0, 0)
        blocks.add(createBlock(0, 0, 0, 2));
        // Cavity at (1, 1, 1)
        blocks.add(createBlock(1, 1, 1, 1));
        // Ignored at (2, 2, 2)
        blocks.add(createBlock(2, 2, 2, 0));
        tag.put("blocks", blocks);

        MultiblockPattern pattern = StructurePatternLoader.builder()
            .fromTag(tag)
            .controllerPos(1, 0, 0)
            .build();

        check("Synthetic pattern size is 3x3x3", pattern.size().equals(new Vec3i(3, 3, 3)), "got " + pattern.size());
    }

    private static void testControllerAutoDiscovery() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("size", new int[]{3, 1, 3});

        net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
        palette.add(createState("minecraft:iron_block"));
        palette.add(createState("minecraft:gold_block"));
        tag.put("palette", palette);

        net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
        // 1 gold block as controller
        blocks.add(createBlock(1, 0, 0, 1));
        // 2 iron blocks
        blocks.add(createBlock(0, 0, 0, 0));
        blocks.add(createBlock(2, 0, 0, 0));
        tag.put("blocks", blocks);

        MultiblockPattern pattern = StructurePatternLoader.builder()
            .fromTag(tag)
            .controller(info -> "minecraft:gold_block".equals(info.name()))
            .build();

        check("Controller auto discovery set size", pattern.size().equals(new Vec3i(3, 1, 3)), "got " + pattern.size());
        check("Controller blockCount is 3", pattern.blockCount() == 3, "got " + pattern.blockCount());
    }

    private static CompoundTag createState(String name) {
        CompoundTag compound = new CompoundTag();
        compound.putString("Name", name);
        return compound;
    }

    private static CompoundTag createBlock(int x, int y, int z, int state) {
        CompoundTag compound = new CompoundTag();
        compound.putIntArray("pos", new int[]{x, y, z});
        compound.putInt("state", state);
        return compound;
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
