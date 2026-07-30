package com.jokerdayn.swindustry.campfire;

/**
 * The deterministic progression of a primitive campfire's ignition attempts.
 *
 * <p>The chance belongs to the attempt number, not to the individual player or striker. A failed
 * strike therefore makes the next strike more likely to catch, and the fifth strike is guaranteed
 * to succeed.</p>
 */
public final class CampfireIgnition {

    private static final double[] CHANCES = {0.15, 0.25, 0.50, 0.75, 1.00};

    /** Number of attempts after which the campfire is guaranteed to light. */
    public static final int MAX_ATTEMPTS = CHANCES.length;

    private int attempts;

    /** Records one strike and returns its one-based attempt number. */
    public int recordAttempt() {
        attempts = Math.min(MAX_ATTEMPTS, attempts + 1);
        return attempts;
    }

    /** Returns the one-based attempt number that will be used next. */
    public int nextAttempt() {
        return Math.min(MAX_ATTEMPTS, attempts + 1);
    }

    /** Clears the progression, for example when rain extinguishes an unlit campfire. */
    public void reset() {
        attempts = 0;
    }

    public int attempts() {
        return attempts;
    }

    /** Returns the chance for a one-based attempt number. */
    public static double chanceForAttempt(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt number must be positive: " + attempt);
        }
        return CHANCES[Math.min(MAX_ATTEMPTS, attempt) - 1];
    }

    /** Applies the chance for the supplied attempt to a roll in the range [0, 1). */
    public static boolean succeeds(int attempt, double roll) {
        if (roll < 0.0 || roll >= 1.0) {
            throw new IllegalArgumentException("Roll must be in the range [0, 1): " + roll);
        }
        return roll < chanceForAttempt(attempt);
    }

    public CampfireIgnition() {}
}
