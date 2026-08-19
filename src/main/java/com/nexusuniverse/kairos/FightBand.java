package com.nexusuniverse.kairos;

/**
 * Story/mechanic content is organized into bands rather than hundreds of
 * hand-authored individual tiers. Numeric stats scale continuously per
 * fight count (see TierCalculator); bands gate which mechanics/dialogue
 * pools are available at a given fight count, so content stays fresh at
 * real milestones without needing hundreds of unique authored states.
 *
 * Ranges widen as they go, matching how these systems are normally
 * paced -- early fights are learning experiences and change fast, later
 * fights are for veterans and can stay in the same band longer.
 */
public enum FightBand {
    FRACTURED(1, 15, "§5Fractured"),
    ADAPTING(16, 40, "§dAdapting"),
    STABILIZING(41, 80, "§cStabilizing"),
    AWARE(81, 150, "§4Aware"),
    UNBOUND(151, 300, "§0Unbound"),
    TRANSCENDENT(301, Integer.MAX_VALUE, "§k§4Transcendent");

    public final int minFight;
    public final int maxFight;
    public final String displayName;

    FightBand(int minFight, int maxFight, String displayName) {
        this.minFight = minFight;
        this.maxFight = maxFight;
        this.displayName = displayName;
    }

    public static FightBand forFightCount(int fightCount) {
        for (FightBand band : values()) {
            if (fightCount >= band.minFight && fightCount <= band.maxFight) {
                return band;
            }
        }
        return TRANSCENDENT;
    }
}
