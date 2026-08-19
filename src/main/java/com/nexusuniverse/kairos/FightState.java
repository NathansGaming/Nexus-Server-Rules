package com.nexusuniverse.kairos;

public class FightState {

    private final NexusKairosPlugin plugin;
    private int fightCount;
    private boolean trueEndingArmed;

    public FightState(NexusKairosPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        this.fightCount = plugin.getConfig().getInt("fight.count", 0);
        this.trueEndingArmed = plugin.getConfig().getBoolean("true-ending-armed", false);
    }

    public void save() {
        plugin.getConfig().set("fight.count", fightCount);
        plugin.getConfig().set("true-ending-armed", trueEndingArmed);
        plugin.saveConfig();
    }

    public int getFightCount() {
        return fightCount;
    }

    /** Call when a fight actually begins (ritual completes successfully). */
    public int incrementAndGet() {
        fightCount++;
        save();
        return fightCount;
    }

    public boolean isTrueEndingArmed() {
        return trueEndingArmed;
    }

    public void setTrueEndingArmed(boolean armed) {
        this.trueEndingArmed = armed;
        save();
    }

    public void forceFightCount(int value) {
        this.fightCount = value;
        save();
    }
}
