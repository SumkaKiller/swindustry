package com.jokerdayn.swindustry.kiln;

import net.minecraft.network.chat.Component;

/** A small, network-friendly description of what the kiln is doing or waiting for. */
public enum KilnStatus {

    INCOMPLETE(0, "incomplete"),
    IDLE(1, "idle"),
    INVALID_RECIPE(2, "invalid_recipe"),
    NEEDS_FUEL(3, "needs_fuel"),
    OUTPUT_BLOCKED(4, "output_blocked"),
    TIER_TOO_LOW(5, "tier_too_low"),
    WORKING(6, "working"),
    CHOKED(7, "choked");

    private static final KilnStatus[] BY_ID = values();

    private final int id;
    private final String translationSuffix;

    KilnStatus(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int id() {
        return id;
    }

    public Component label() {
        return Component.translatable("container.swindustry.clay_kiln.status." + translationSuffix);
    }

    public static KilnStatus byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : IDLE;
    }
}
