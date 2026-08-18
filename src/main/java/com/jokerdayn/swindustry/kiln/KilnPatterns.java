package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.SWIndustry;
import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.multiblock.MultiblockPatterns;
import com.jokerdayn.swindustry.multiblock.StructurePatternLoader;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The shape of the clay kiln.
 *
 * <p>Taken block for block from the kiln built in-game and exported to {@code furnace.nbt}: a
 * five-by-five firebox with the corners knocked off, two courses high, drawing in to a three-wide
 * throat and then a single flue running three blocks up. Fifty-six bricks and the loading port
 * they surround.</p>
 *
 * <pre>
 *   y = 4         y = 3        y = 1..2     y = 0 (Floor)
 *    · · · · ·   · · · · ·    · ▓ P ▓ ·    · ▓ ▓ ▓ ·      ▓  raw clay bricks
 *    · · ▓ · ·   · ▓ ▓ ▓ ·    ▓ · · · ▓    ▓ ▓ ▓ ▓ ▓      P  loading port (controller at y=1)
 *    · ▓ · ▓ ·   · ▓ · ▓ ·    ▓ · · · ▓    ▓ ▓ ▓ ▓ ▓      ·  outside the kiln
 *    · · ▓ · ·   · ▓ ▓ ▓ ·    ▓ · · · ▓    ▓ ▓ ▓ ▓ ▓
 *    · · · · ·   · · · · ·    · ▓ ▓ ▓ ·    · ▓ ▓ ▓ ·
 * </pre>
 *
 * <p>Written as if the port faced north; the framework handles the other three facings. The shell
 * is matched by tag rather than by block, so a later kiln tier can be a datapack entry.</p>
 *
 * <p>The geometry itself is loaded through {@link ResourceManager}, so world datapacks can
 * override {@code data/swindustry/structures/clay_kiln.nbt} exactly like any tag or recipe.
 * {@link KilnPatternReloadHook} binds it at server start and recompiles it on every reload.</p>
 */
public final class KilnPatterns {

    private KilnPatterns() {}

    private static final ResourceLocation STRUCTURE_ID = SWIndustry.id("structures/clay_kiln");

    /** Current geometry; swapped in place on every datapack reload. Volatile: reload threads write. */
    private static volatile MultiblockPattern clayKiln;

    /**
     * Tier one. Raw clay, held together by nothing but its own weight. Registered with
     * {@link MultiblockPatterns} so mining any wall block takes the kiln apart on the spot rather
     * than a second later.
     */
    public static MultiblockPattern clayKiln() {
        MultiblockPattern pattern = clayKiln;
        if (pattern == null) {
            synchronized (KilnPatterns.class) {
                pattern = clayKiln;
                if (pattern == null) {
                    // Headless checks and very-early callers have no bound ResourceManager; fall
                    // back to the copy baked into this jar.
                    pattern = StructurePatternLoader.builder()
                        .fromResource(STRUCTURE_ID)
                        .map(ModBlocks.RAW_CLAY_BRICKS, BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
                        .controller(ModBlocks.CLAY_KILN_PORT)
                        .build();
                    clayKiln = pattern;
                    MultiblockPatterns.register(pattern);
                }
            }
        }
        return pattern;
    }

    /** Rebuilds the pattern through pack resources; called on bind and on datapack reloads. */
    public static void initialize(ResourceManager resources) {
        MultiblockPattern previous = clayKiln;
        MultiblockPattern built = StructurePatternLoader.builder()
            .fromResource(resources, STRUCTURE_ID)
            .map(ModBlocks.RAW_CLAY_BRICKS, BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
            .controller(ModBlocks.CLAY_KILN_PORT)
            .build();
        clayKiln = built;
        if (previous == null) {
            MultiblockPatterns.register(built);
        } else {
            MultiblockPatterns.replace(previous, built);
        }
    }
}
