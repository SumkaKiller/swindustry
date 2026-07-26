package com.jokerdayn.swindustry.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * What bronze is worth in the hand.
 *
 * <p>Bronze stands in for iron in this pack — there is no iron to find — so it has to open the same
 * doors iron does. That is the one thing not up for negotiation: it uses
 * {@link BlockTags#INCORRECT_FOR_IRON_TOOL}, so it mines everything an iron pick would, and the
 * progression can carry on past it.</p>
 *
 * <p>Everything else sits deliberately short of iron. It is softer metal, dug out of boulders by
 * hand and cooked in a mud kiln, and it should feel like it: it wears out sooner, cuts slower and
 * hits lighter than the iron it replaces. What it buys back is that it can be had at all.</p>
 *
 * <pre>
 *            uses   speed   damage   ench.
 *   stone     131     4.0      1.0       5
 *   bronze    180     5.0      1.5      12
 *   iron      250     6.0      2.0      14
 * </pre>
 *
 * <p>The enchantability sits above iron on purpose: bronze is the top of the tree for a long while,
 * so the table has to be worth walking to.</p>
 */
public final class ModToolTiers {

    private ModToolTiers() {}

    public static final Tier BRONZE = new SimpleTier(
        BlockTags.INCORRECT_FOR_IRON_TOOL,
        180,
        5.0F,
        1.5F,
        12,
        () -> Ingredient.of(ModTags.Items.BRONZE_INGOTS)
    );
}
