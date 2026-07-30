package com.jokerdayn.swindustry.progression;

/**
 * The client's copy of the current objective.
 *
 * <p>Kept free of any client-only Minecraft type on purpose, so that the payload handler that
 * writes to it can live in a shared class without dragging rendering code onto a server.</p>
 */
public final class ClientProgression {

    private static ProgressionStep step = ProgressionStep.CHOP_WOOD;
    private static int milestoneBits;

    private ClientProgression() {}

    public static void accept(ProgressionStep newStep, int newMilestoneBits) {
        step = newStep;
        milestoneBits = newMilestoneBits;
    }

    public static ProgressionStep step() {
        return step;
    }

    public static int milestoneBits() {
        return milestoneBits;
    }

    /** Resets on disconnect, so the next world does not inherit the last one's objective. */
    public static void reset() {
        step = ProgressionStep.CHOP_WOOD;
        milestoneBits = 0;
    }
}
