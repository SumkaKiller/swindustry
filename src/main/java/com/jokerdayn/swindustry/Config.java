package com.jokerdayn.swindustry;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config: synced to clients automatically, so gameplay predicates read the same values on
 * both logical sides. Everything a pack maker would want to retune without recompiling — how
 * stubborn striking is, how long the first fire lasts and what it leaves behind — lives here
 * rather than as a constant buried in a block class. The five-step ignition progression is defined
 * by {@code CampfireIgnition} because it is gameplay logic rather than a tuning knob.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- Primitive campfire -------------------------------------------------

    public static final ModConfigSpec.IntValue STRIKE_COOLDOWN_TICKS = BUILDER
        .comment("Ticks between strikes. Stops a held right-click from rolling the dice 20 times a second.")
        .translation("swindustry.configuration.strikeCooldown")
        .defineInRange("campfire.strikeCooldownTicks", 8, 0, 200);

    public static final ModConfigSpec.BooleanValue REQUIRE_DISTINCT_STRIKERS = BUILDER
        .comment("When true, one hand must hold flint and the other stone.",
            "When false, any pair of striking materials will do, including two of the same.")
        .translation("swindustry.configuration.requireDistinctStrikers")
        .define("campfire.requireDistinctStrikers", true);

    public static final ModConfigSpec.IntValue CAMPFIRE_BURN_TICKS = BUILDER
        .comment("How long a lit campfire burns before it collapses into embers. 1200 ticks = 60 seconds.")
        .translation("swindustry.configuration.campfireBurnTicks")
        .defineInRange("campfire.burnTicks", 1200, 20, 72000);

    public static final ModConfigSpec.BooleanValue RAIN_EXTINGUISHES_CAMPFIRE = BUILDER
        .comment("When true, rain falling on an open campfire puts it out and the burn is wasted.")
        .translation("swindustry.configuration.rainExtinguishes")
        .define("campfire.rainExtinguishes", true);

    // What a burnt-out campfire yields is deliberately *not* configured here. It is a loot table
    // (data/swindustry/loot_table/blocks/primitive_campfire.json), which is the idiomatic place for
    // drops and is overridable by any datapack — duplicating those numbers in a config would only
    // create a second source of truth that silently disagrees with the first.

    // ---- Clay kiln ----------------------------------------------------------

    public static final ModConfigSpec.IntValue KILN_DEFAULT_COOK_TICKS = BUILDER
        .comment("Cook time the kiln uses for a plain vanilla smelting recipe. Vanilla furnaces use 200.")
        .translation("swindustry.configuration.kilnCookTicks")
        .defineInRange("kiln.defaultCookTicks", 200, 1, 24000);

    public static final ModConfigSpec.DoubleValue KILN_FUEL_EFFICIENCY = BUILDER
        .comment("Multiplier on how long fuel lasts in the kiln. Below 1.0 makes it hungrier than a furnace.")
        .translation("swindustry.configuration.kilnFuelEfficiency")
        .defineInRange("kiln.fuelEfficiency", 1.0, 0.1, 10.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
