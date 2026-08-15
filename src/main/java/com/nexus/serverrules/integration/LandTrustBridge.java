package com.nexus.serverrules.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional soft link to NexusRealms' public API (com.nexusuniverse.realms.api.NexusRealmsApi),
 * resolved entirely via reflection so NexusServerRules never needs NexusRealms as a compile
 * dependency and keeps working standalone - with land/claim awareness simply off - on a server
 * that doesn't have NexusRealms installed at all.
 *
 * Used by GriefListener to ask "is this player currently allowed to build here, per NexusRealms'
 * own claim/team trust rules" before counting a block break as suspicious - a teammate (or
 * anyone else NexusRealms already trusts on that specific claim) breaking a block someone else
 * on the same land placed is normal shared building, not griefing.
 *
 * The lookup is lazy (first call to canBuild(), not plugin startup) and cached after that, so
 * load order between the two plugins doesn't matter - by the time a real block is broken in
 * gameplay both plugins are long since enabled either way.
 */
public final class LandTrustBridge {

    private static final String API_CLASS_NAME = "com.nexusuniverse.realms.api.NexusRealmsApi";

    private final JavaPlugin plugin;
    private Object apiInstance;
    private Method canBuildMethod;
    private boolean resolved;
    private boolean warnedOnce;

    public LandTrustBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * True if playerId is trusted to build at location according to NexusRealms. Returns false -
     * meaning "no exemption available," never a claim about the player - if NexusRealms isn't
     * installed, hasn't registered its API yet, or the lookup/call fails for any reason (e.g. a
     * future NexusRealms version renames the method). This must never throw or block gameplay
     * just because the integration is missing or stale.
     */
    public boolean canBuild(UUID playerId, Location location) {
        if (!resolve()) return false;
        try {
            Object result = canBuildMethod.invoke(apiInstance, playerId, location);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("NexusRealms integration call failed - treating this break as having no land-trust data available.", e);
            return false;
        }
    }

    private synchronized boolean resolve() {
        if (resolved) return apiInstance != null && canBuildMethod != null;
        resolved = true;

        if (Bukkit.getPluginManager().getPlugin("NexusRealms") == null) {
            plugin.getLogger().info("[NexusServerRules] NexusRealms not found - griefing detection will not be "
                    + "claim/team-aware. Install NexusRealms if you want trusted teammates/claim visitors exempted.");
            return false;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                warnOnce("NexusRealms is installed but hasn't registered its API service - griefing detection "
                        + "will not be claim/team-aware until it does (check load order / NexusRealms version).", null);
                return false;
            }
            apiInstance = registration.getProvider();
            canBuildMethod = apiClass.getMethod("canBuild", UUID.class, Location.class);
            plugin.getLogger().info("[NexusServerRules] Linked to NexusRealms - griefing detection now exempts "
                    + "players currently trusted to build where they're breaking blocks (teammates, claim-trusted visitors, etc).");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("Found NexusRealms but couldn't link to its API (version mismatch?) - "
                    + "griefing detection will not be claim/team-aware.", e);
            apiInstance = null;
            canBuildMethod = null;
            return false;
        }
    }

    private void warnOnce(String message, Throwable cause) {
        if (warnedOnce) return;
        warnedOnce = true;
        if (cause != null) {
            plugin.getLogger().log(Level.WARNING, "[NexusServerRules] " + message, cause);
        } else {
            plugin.getLogger().warning("[NexusServerRules] " + message);
        }
    }
}
