package com.jokerdayn.swindustry.progression;

/**
 * Things a player has done that leave no trace in their inventory.
 *
 * <p>Most objectives can be checked by looking at what someone is carrying — you either have
 * charcoal or you do not. These are the ones that cannot: lighting a fire and assembling a kiln are
 * moments, not possessions, so the code that makes them happen records them here.</p>
 *
 * <p>Order is written to disk as a bit index, so entries may be appended but never reordered or
 * removed.</p>
 */
public enum ProgressionMilestone {

    /** Struck a spark and got a fire out of it. */
    LIT_A_FIRE,

    /** Stood in front of a clay kiln that snapped together. */
    FORMED_A_KILN;

    public int bit() {
        return 1 << ordinal();
    }
}
