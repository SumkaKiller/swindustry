package com.jokerdayn.swindustry.kiln;

import com.jokerdayn.swindustry.multiblock.BlockMatcher;
import com.jokerdayn.swindustry.multiblock.MultiblockPattern;
import com.jokerdayn.swindustry.multiblock.MultiblockPatterns;
import com.jokerdayn.swindustry.registry.ModBlocks;
import com.jokerdayn.swindustry.registry.ModTags;

/**
 * The shape of the clay kiln.
 *
 * <p>Taken block for block from the kiln built in-game and exported to {@code furnace.nbt}: a
 * five-by-five firebox with the corners knocked off, two courses high, drawing in to a three-wide
 * throat and then a single flue running three blocks up. Forty-three bricks and the loading port
 * they surround.</p>
 *
 * <pre>
 *   y = 3..5      y = 2        y = 0..1
 *    · · · · ·   · · · · ·    · ▓ P ▓ ·      ▓  raw clay bricks
 *    · · ▓ · ·   · ▓ ▓ ▓ ·    ▓ · · · ▓      P  loading port (the controller)
 *    · ▓ · ▓ ·   · ▓ · ▓ ·    ▓ · · · ▓      ·  outside the kiln
 *    · · ▓ · ·   · ▓ ▓ ▓ ·    ▓ · · · ▓
 *    · · · · ·   · · · · ·    · ▓ ▓ ▓ ·
 * </pre>
 *
 * <p>Written as if the port faced north; the framework handles the other three facings. The shell
 * is matched by tag rather than by block, so a later kiln tier can be a datapack entry.</p>
 */
public final class KilnPatterns {

    private KilnPatterns() {}

    /**
     * Tier one. Raw clay, held together by nothing but its own weight.
     *
     * <p>Registered with {@link MultiblockPatterns} so that mining any wall block takes the kiln
     * apart on the spot rather than a second later.</p>
     */
    public static final MultiblockPattern CLAY_KILN = MultiblockPatterns.register(
        MultiblockPattern.builder()
            // y = 0 — firebox floor course, with the loading port set into the front wall
            .layer(
                " CPC ",
                "C___C",
                "C___C",
                "C___C",
                " CCC ")
            // y = 1 — second course of the firebox
            .layer(
                " CCC ",
                "C___C",
                "C___C",
                "C___C",
                " CCC ")
            // y = 2 — the throat, drawing the fire in towards the flue
            .layer(
                "     ",
                " CCC ",
                " C_C ",
                " CCC ",
                "     ")
            // y = 3..5 — the flue
            .layer(
                "     ",
                "  C  ",
                " C_C ",
                "  C  ",
                "     ")
            .layer(
                "     ",
                "  C  ",
                " C_C ",
                "  C  ",
                "     ")
            .layer(
                "     ",
                "  C  ",
                " C_C ",
                "  C  ",
                "     ")
            .where('C', BlockMatcher.tag(ModTags.Blocks.KILN_WALL))
            .where('P', BlockMatcher.block(ModBlocks.CLAY_KILN_PORT))
            .where('_', BlockMatcher.cavity())
            .where(' ', BlockMatcher.ignored())
            .controller('P')
            .build());
}
