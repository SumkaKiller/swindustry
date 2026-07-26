package com.jokerdayn.swindustry.campfire;

import net.minecraft.util.StringRepresentable;

/**
 * The three lives of a primitive campfire.
 *
 * <p>It is a one-way trip: a pile of wood becomes a fire, and a fire becomes a heap of embers you
 * rake charcoal out of. Nothing turns embers back into a fire — the wood is spent, and the next one
 * has to be built and struck all over again.</p>
 */
public enum CampfireStage implements StringRepresentable {

    /** Built but cold. Right-click it with flint and stone to try for a spark. */
    UNLIT("unlit"),

    /** Burning. Gives light and heat, hurts anything standing in it, and is on a clock. */
    LIT("lit"),

    /** Burnt out. Break it open for charcoal and soot. */
    EMBERS("embers");

    private final String name;

    CampfireStage(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
