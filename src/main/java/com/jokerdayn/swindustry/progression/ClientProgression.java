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

    /**
     * When the objective last changed, as a client tick count. The HUD uses it to fade a new
     * objective in rather than have it appear mid-frame.
     */
    private static long changedAtTick = Long.MIN_VALUE;

    private static long clientTick;

    private ClientProgression() {}

    public static void accept(ProgressionStep newStep, int newMilestoneBits) {
        if (newStep != step) {
            changedAtTick = clientTick;
        }
        step = newStep;
        milestoneBits = newMilestoneBits;
    }

    public static ProgressionStep step() {
        return step;
    }

    public static int milestoneBits() {
        return milestoneBits;
    }

    /** Ticks since the objective last changed, or a large number if it has been stable. */
    public static long ticksSinceChange() {
        return changedAtTick == Long.MIN_VALUE ? Long.MAX_VALUE : clientTick - changedAtTick;
    }

    /** Advanced once per client tick so {@link #ticksSinceChange()} means something. */
    public static void tick() {
        clientTick++;
    }

    /** Resets on disconnect, so the next world does not inherit the last one's objective. */
    public static void reset() {
        step = ProgressionStep.CHOP_WOOD;
        milestoneBits = 0;
        changedAtTick = Long.MIN_VALUE;
    }
}
