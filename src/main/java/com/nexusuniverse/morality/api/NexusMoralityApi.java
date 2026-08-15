package com.nexusuniverse.morality.api;

import com.nexusuniverse.morality.karma.KarmaStore;

import java.util.UUID;

/**
 * Public read API for a player's morality-encounter karma. Registered
 * with Bukkit's ServicesManager on enable (see NexusMorality#onEnable),
 * the same pattern NexusRealms uses for NexusRealmsApi - a consuming
 * plugin (the planned NexusReputation passport, most immediately) looks
 * this up via reflection + Bukkit.getServicesManager().getRegistration(),
 * exactly like NexusServerRules' integration/LandTrustBridge.java does
 * for NexusRealms. That means NexusMorality never needs to be a
 * compile-time dependency of anything consuming it, and this plugin
 * keeps working standalone - karma just goes untracked by anything else
 * - if nothing downstream is installed.
 *
 * Deliberately a tiny, stable surface: one method, primitive/JDK types
 * only (UUID in, int out), so a reflective caller only ever needs
 * Class.forName + getMethod("getKarma", UUID.class) to stay working
 * across NexusMorality versions.
 */
public final class NexusMoralityApi {

    private final KarmaStore karmaStore;

    public NexusMoralityApi(KarmaStore karmaStore) {
        this.karmaStore = karmaStore;
    }

    /** A player's current karma total. 0 for a player with no recorded encounters. */
    public int getKarma(UUID playerId) {
        return karmaStore.get(playerId);
    }
}
