package com.nexusuniverse.kairos;

/**
 * Turns a raw fight count into concrete numbers: health, damage, how
 * many extra mobs spawn, whether teleport-strikes are unlocked, etc.
 * This is the "hundreds of tiers" engine -- every fight count produces
 * a slightly different result, no two fights read from the same block
 * of stats, without needing hundreds of hand-authored configurations.
 */
public class TierCalculator {

    private final double baseHealth;
    private final double baseDamage;
    private final double healthGrowth;
    private final double damageGrowth;

    public TierCalculator(double baseHealth, double baseDamage, double healthGrowth, double damageGrowth) {
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.healthGrowth = healthGrowth;
        this.damageGrowth = damageGrowth;
    }

    public double healthFor(int fightCount) {
        return baseHealth + (healthGrowth * fightCount);
    }

    public double damageFor(int fightCount) {
        return baseDamage + (damageGrowth * fightCount);
    }

    /**
     * How many extra mobs spawn during the fight. Unlocked starting in
     * the ADAPTING band, then scales up within each band rather than
     * growing forever unbounded (keeps it from becoming unplayable at
     * fight #400).
     */
    public int mobWavesFor(int fightCount) {
        FightBand band = FightBand.forFightCount(fightCount);
        return switch (band) {
            case FRACTURED -> 0;
            case ADAPTING -> 1;
            case STABILIZING -> 2;
            case AWARE -> 3;
            case UNBOUND -> 4;
            case TRANSCENDENT -> 5;
        };
    }

    /**
     * Whether teleport-strike attacks are unlocked at this fight count.
     */
    public boolean teleportStrikesUnlocked(int fightCount) {
        return FightBand.forFightCount(fightCount).ordinal() >= FightBand.STABILIZING.ordinal();
    }

    /**
     * Whether arena hazards (floor gaps, fire, etc.) are active.
     */
    public boolean arenaHazardsUnlocked(int fightCount) {
        return FightBand.forFightCount(fightCount).ordinal() >= FightBand.ADAPTING.ordinal();
    }

    /**
     * Movement speed multiplier -- small, capped growth so the fight
     * stays winnable at high fight counts rather than becoming
     * literally unhittable.
     */
    public double speedMultiplierFor(int fightCount) {
        double raw = 1.0 + (fightCount * 0.002);
        return Math.min(raw, 1.6);
    }
}
