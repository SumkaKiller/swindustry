package com.jokerdayn.swindustry.checks;

import com.jokerdayn.swindustry.campfire.CampfireIgnition;

/** Headless regression checks for the five-step campfire ignition progression. */
public final class CampfireIgnitionCheck {

    public static void main(String[] args) {
        checkChances();
        checkBoundaries();
        checkProgressionAndReset();
        System.out.println("Campfire ignition checks passed.");
    }

    private static void checkChances() {
        double[] expected = {0.15, 0.25, 0.50, 0.75, 1.00};
        for (int attempt = 1; attempt <= expected.length; attempt++) {
            check("chance for attempt " + attempt,
                CampfireIgnition.chanceForAttempt(attempt) == expected[attempt - 1],
                "got " + CampfireIgnition.chanceForAttempt(attempt));
        }
        check("attempts after the fifth stay guaranteed", CampfireIgnition.chanceForAttempt(6) == 1.0,
            "chance after fifth attempt is not 100%");
    }

    private static void checkBoundaries() {
        double[] chances = {0.15, 0.25, 0.50, 0.75};
        for (int i = 0; i < chances.length; i++) {
            int attempt = i + 1;
            check("attempt " + attempt + " succeeds below its chance",
                CampfireIgnition.succeeds(attempt, Math.nextDown(chances[i])), "roll should succeed");
            check("attempt " + attempt + " fails at its chance",
                !CampfireIgnition.succeeds(attempt, chances[i]), "roll should fail at the boundary");
        }
        check("fifth attempt always succeeds", CampfireIgnition.succeeds(5, Math.nextDown(1.0)),
            "fifth attempt did not succeed");
    }

    private static void checkProgressionAndReset() {
        CampfireIgnition ignition = new CampfireIgnition();
        for (int attempt = 1; attempt <= CampfireIgnition.MAX_ATTEMPTS; attempt++) {
            check("next attempt is " + attempt, ignition.nextAttempt() == attempt,
                "got " + ignition.nextAttempt());
            check("recorded attempt is " + attempt, ignition.recordAttempt() == attempt,
                "got " + ignition.attempts());
        }
        check("attempt count is capped", ignition.recordAttempt() == CampfireIgnition.MAX_ATTEMPTS,
            "attempt count exceeded maximum");
        ignition.reset();
        check("reset returns to first attempt", ignition.nextAttempt() == 1,
            "got " + ignition.nextAttempt());
    }

    private static void check(String name, boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError(name + ": " + detail);
        }
    }

    private CampfireIgnitionCheck() {}
}
