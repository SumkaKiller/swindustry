package com.jokerdayn.swindustry.multiblock;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether one world position satisfies one character of a {@link MultiblockPattern}.
 *
 * <p>Beyond the yes/no test, a matcher declares a {@link Role}. That is what tells the framework
 * whether a position is <em>part of</em> the machine ({@link Role#WALL}), has to stay clear for the
 * machine to work ({@link Role#CAVITY}), or is none of the machine's business
 * ({@link Role#IGNORED}). Only walls take part in the reverse lookup that notices a structure being
 * broken, so the role matters as much as the test.</p>
 */
public interface BlockMatcher {

    /** What a matched position means to the machine. */
    enum Role {
        /** Solid part of the machine. Breaking one of these takes the machine apart. */
        WALL,
        /** Interior that has to stay clear — a combustion chamber, a flue, a working area. */
        CAVITY,
        /**
         * Outside the machine. Present in the pattern only to keep the layer strings rectangular,
         * so players may build whatever they like here.
         */
        IGNORED
    }

    boolean matches(LevelReader level, BlockPos pos, BlockState state);

    Role role();

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /** Matches anything, and the position is not considered part of the machine. */
    static BlockMatcher ignored() {
        return Impl.IGNORED;
    }

    /**
     * Requires the position to be free: air, or something a placed block would replace (tall grass,
     * water). Fire counts as free so a machine is allowed to burn inside itself.
     */
    static BlockMatcher cavity() {
        return Impl.CAVITY;
    }

    /** Requires the position to hold exactly air. Stricter than {@link #cavity()}. */
    static BlockMatcher air() {
        return Impl.AIR;
    }

    /** Requires exactly this block. */
    static BlockMatcher block(Supplier<? extends Block> block) {
        return new Impl(Role.WALL, (level, pos, state) -> state.is(block.get()));
    }

    /** Requires exactly this block. */
    static BlockMatcher block(Block block) {
        return new Impl(Role.WALL, (level, pos, state) -> state.is(block));
    }

    /** Requires one of the given blocks. */
    static BlockMatcher blocks(List<Supplier<? extends Block>> blocks) {
        List<Supplier<? extends Block>> candidates = List.copyOf(blocks);
        return new Impl(Role.WALL, (level, pos, state) -> {
            for (Supplier<? extends Block> supplier : candidates) {
                if (state.is(supplier.get())) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Requires any block carrying the tag. Prefer this over {@link #blocks} for machine walls: a tag
     * lets a later upgrade tier introduce a sturdier wall block without touching the pattern.
     */
    static BlockMatcher tag(TagKey<Block> tag) {
        return new Impl(Role.WALL, (level, pos, state) -> state.is(tag));
    }

    /** An arbitrary state test in the given role. */
    static BlockMatcher of(Role role, Predicate<BlockState> test) {
        return new Impl(role, (level, pos, state) -> test.test(state));
    }

    /** The position matches if any of the given matchers accepts it. All must share a role. */
    static BlockMatcher either(BlockMatcher first, BlockMatcher... rest) {
        for (BlockMatcher matcher : rest) {
            if (matcher.role() != first.role()) {
                throw new IllegalArgumentException(
                    "Cannot combine matchers with different roles: " + first.role() + " vs " + matcher.role());
            }
        }
        List<BlockMatcher> all = new java.util.ArrayList<>(rest.length + 1);
        all.add(first);
        all.addAll(List.of(rest));
        return new Impl(first.role(), (level, pos, state) -> {
            for (BlockMatcher matcher : all) {
                if (matcher.matches(level, pos, state)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** The one implementation; pairs a role with a test. */
    record Impl(Role role, Test test) implements BlockMatcher {

        /** The test half of a matcher, split out so {@link Impl} can be a record. */
        @FunctionalInterface
        public interface Test {
            boolean matches(LevelReader level, BlockPos pos, BlockState state);
        }

        static final BlockMatcher IGNORED =
            new Impl(Role.IGNORED, (level, pos, state) -> true);

        static final BlockMatcher AIR =
            new Impl(Role.CAVITY, (level, pos, state) -> state.isAir());

        static final BlockMatcher CAVITY = new Impl(Role.CAVITY, (level, pos, state) ->
            state.isAir() || state.canBeReplaced() || state.is(Blocks.FIRE));

        @Override
        public boolean matches(LevelReader level, BlockPos pos, BlockState state) {
            return test.matches(level, pos, state);
        }
    }
}
