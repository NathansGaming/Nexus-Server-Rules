package com.nexusuniverse.survival.api;

import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Public read-only API for a player's current survival stats. Registered
 * with Bukkit's ServicesManager on enable (see NexusSurvivalPlugin), the
 * same pattern NexusRealms uses for NexusRealmsApi - a consuming plugin
 * (NexusDreams first, more likely later) looks this up via reflection +
 * Bukkit.getServicesManager().getRegistration(), exactly like
 * NexusServerRules' integration/LandTrustBridge.java does for NexusRealms.
 * NexusSurvival never needs to know who's consuming this, and keeps
 * working completely standalone if nothing downstream is installed.
 *
 * Every fraction is normalized to 0.0 (worst) - 1.0 (best/full), so a
 * caller doesn't need to know this plugin's internal scales (thirst is
 * 0-180, rad-oxygen is 0-20, dirtiness is 0-100 and inverted) to make
 * sense of the numbers. Returns the "everything's fine" value (1.0 for
 * fractions, false/0 for infection) for a player who isn't currently
 * online rather than throwing - SurvivalPlayerData only exists for
 * players PlayerDataManager has actually seen this session, and a
 * reflective caller should never get an exception out of this class.
 */
public final class NexusSurvivalApi {

    private static final double MAX_THIRST = 180.0;
    private static final double MAX_RAD_OXYGEN = 20.0;
    private static final double MAX_DIRTINESS = 100.0;

    private final PlayerDataManager playerDataManager;

    public NexusSurvivalApi(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    /** 1.0 = fully hydrated, 0.0 = empty/dehydrated. */
    public double thirstFraction(UUID playerId) {
        SurvivalPlayerData data = dataOrNull(playerId);
        return data == null ? 1.0 : clamp(data.thirst / MAX_THIRST);
    }

    /** 1.0 = full rad-oxygen (safe), 0.0 = suffocating on radiation. */
    public double radOxygenFraction(UUID playerId) {
        SurvivalPlayerData data = dataOrNull(playerId);
        return data == null ? 1.0 : clamp(data.radOxygen / MAX_RAD_OXYGEN);
    }

    /** 1.0 = spotless, 0.0 = filthy. Inverted from the internal 0(clean)-100(filthy) scale. */
    public double hygieneFraction(UUID playerId) {
        SurvivalPlayerData data = dataOrNull(playerId);
        return data == null ? 1.0 : clamp(1.0 - (data.dirtiness / MAX_DIRTINESS));
    }

    public boolean isInfected(UUID playerId) {
        SurvivalPlayerData data = dataOrNull(playerId);
        return data != null && data.infection != null;
    }

    /** 0 (Mild) - 3 (Critical). 0 if not currently infected. */
    public int infectionSeverity(UUID playerId) {
        SurvivalPlayerData data = dataOrNull(playerId);
        return (data == null || data.infection == null) ? 0 : data.infectionSeverity;
    }

    private SurvivalPlayerData dataOrNull(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return null;
        return playerDataManager.get(player);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
