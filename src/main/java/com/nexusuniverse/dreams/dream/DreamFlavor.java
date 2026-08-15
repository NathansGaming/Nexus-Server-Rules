package com.nexusuniverse.dreams.dream;

/**
 * Which single signal was worst going into the dream - drives which
 * flavor-text bucket in config.yml gets used. THIRST/RADIATION/HYGIENE/
 * DISEASE only ever come from a linked NexusSurvival (see
 * integration/SurvivalBridge); VANILLA is the fallback-signal flavor used
 * when NexusSurvival isn't installed at all.
 */
public enum DreamFlavor {
    THIRST,
    RADIATION,
    HYGIENE,
    DISEASE,
    VANILLA;

    /** Matches the config.yml key under each flavor.<tier>.* section. */
    public String configKey() {
        return name().toLowerCase();
    }
}
