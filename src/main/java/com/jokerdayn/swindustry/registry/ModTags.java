package com.jokerdayn.swindustry.registry;

import com.jokerdayn.swindustry.SWIndustry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Every tag the mod reads.
 *
 * <p>Two of these carry real architectural weight:</p>
 *
 * <ul>
 *   <li>{@link Blocks#KILN_WALL} is what the kiln pattern matches against, not a specific block.
 *       Adding a sturdier wall for a later kiln tier is then a one-line datapack change instead of
 *       a code change.</li>
 *   <li>{@link Items#BRONZE_INGOTS} is how this mod reaches bronze without ever naming SW: Worldgen
 *       Core's classes. If bronze ever moves house, only the tag file follows it.</li>
 * </ul>
 */
public final class ModTags {

    private ModTags() {}

    public static final class Blocks {
        private Blocks() {}

        /** Anything the clay kiln's shell may be built from. */
        public static final TagKey<Block> KILN_WALL = block(SWIndustry.id("kiln_wall"));

        private static TagKey<Block> block(ResourceLocation id) {
            return TagKey.create(Registries.BLOCK, id);
        }
    }

    public static final class Items {
        private Items() {}

        /** Bronze ingots, wherever they come from. Repairs bronze gear and feeds its recipes. */
        public static final TagKey<Item> BRONZE_INGOTS = common("ingots/bronze");

        /** Unsmelted bronze, as mined out of the island boulders. */
        public static final TagKey<Item> BRONZE_RAW = common("raw_materials/bronze");

        /**
         * The sharp half of a fire striker — flint and anything a pack maker considers equivalent.
         */
        public static final TagKey<Item> FIRE_STRIKER_FLINT = item(SWIndustry.id("fire_strikers/flint"));

        /** The hard half of a fire striker — the loose stones lying about the island. */
        public static final TagKey<Item> FIRE_STRIKER_STONE = item(SWIndustry.id("fire_strikers/stone"));

        /** Wood the kiln will char. Kept separate from vanilla logs so charring can be tuned. */
        public static final TagKey<Item> KILN_CHARRABLE = item(SWIndustry.id("kiln_charrable"));

        private static TagKey<Item> item(ResourceLocation id) {
            return TagKey.create(Registries.ITEM, id);
        }

        private static TagKey<Item> common(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
