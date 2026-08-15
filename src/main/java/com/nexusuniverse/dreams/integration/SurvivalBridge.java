package com.nexusuniverse.dreams.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional soft link to NexusSurvival's public API
 * (com.nexusuniverse.survival.api.NexusSurvivalApi), resolved entirely via
 * reflection so NexusDreams never needs NexusSurvival as a compile
 * dependency - same pattern NexusServerRules uses for NexusRealms
 * (integration/LandTrustBridge.java) and NexusMorality is set up to be
 * consumed the same way in turn.
 *
 * When NexusSurvival isn't installed (or the lookup fails for any
 * reason), isAvailable() returns false and DreamAssessor falls back to
 * vanilla-only signals - this must never throw or block a player's sleep
 * just because the integration is missing or stale.
 */
public final class SurvivalBridge {

    private static final String API_CLASS_NAME = "com.nexusuniverse.survival.api.NexusSurvivalApi";

    private final JavaPlugin plugin;
    private Object apiInstance;
    private Method thirstFractionMethod;
    private Method radOxygenFractionMethod;
    private Method hygieneFractionMethod;
    private Method isInfectedMethod;
    private Method infectionSeverityMethod;
    private boolean resolved;
    private boolean warnedOnce;

    public SurvivalBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return resolve();
    }

    public double thirstFraction(UUID playerId) {
        return callDouble(thirstFractionMethod, playerId, 1.0);
    }

    public double radOxygenFraction(UUID playerId) {
        return callDouble(radOxygenFractionMethod, playerId, 1.0);
    }

    public double hygieneFraction(UUID playerId) {
        return callDouble(hygieneFractionMethod, playerId, 1.0);
    }

    public boolean isInfected(UUID playerId) {
        if (!resolve()) return false;
        try {
            Object result = isInfectedMethod.invoke(apiInstance, playerId);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("call to isInfected failed", e);
            return false;
        }
    }

    public int infectionSeverity(UUID playerId) {
        if (!resolve()) return 0;
        try {
            Object result = infectionSeverityMethod.invoke(apiInstance, playerId);
            return result instanceof Integer value ? value : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("call to infectionSeverity failed", e);
            return 0;
        }
    }

    private double callDouble(Method method, UUID playerId, double fallback) {
        if (!resolve()) return fallback;
        try {
            Object result = method.invoke(apiInstance, playerId);
            return result instanceof Double value ? value : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("a stat lookup failed", e);
            return fallback;
        }
    }

    private synchronized boolean resolve() {
        if (resolved) return apiInstance != null;
        resolved = true;

        if (Bukkit.getPluginManager().getPlugin("NexusSurvival") == null) {
            plugin.getLogger().info("[NexusDreams] NexusSurvival not found - dreams will use vanilla "
                    + "health/hunger signals only, not thirst/radiation/hygiene/infection.");
            return false;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                warnOnce("NexusSurvival is installed but hasn't registered its API service yet "
                        + "(check load order / NexusSurvival version) - falling back to vanilla signals", null);
                return false;
            }
            apiInstance = registration.getProvider();
            thirstFractionMethod = apiClass.getMethod("thirstFraction", UUID.class);
            radOxygenFractionMethod = apiClass.getMethod("radOxygenFraction", UUID.class);
            hygieneFractionMethod = apiClass.getMethod("hygieneFraction", UUID.class);
            isInfectedMethod = apiClass.getMethod("isInfected", UUID.class);
            infectionSeverityMethod = apiClass.getMethod("infectionSeverity", UUID.class);
            plugin.getLogger().info("[NexusDreams] Linked to NexusSurvival - dreams now reflect real "
                    + "thirst/radiation/hygiene/infection state instead of just vanilla health/hunger.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("Found NexusSurvival but couldn't link to its API (version mismatch?) - "
                    + "falling back to vanilla signals", e);
            apiInstance = null;
            return false;
        }
    }

    private void warnOnce(String message, Throwable cause) {
        if (warnedOnce) return;
        warnedOnce = true;
        if (cause != null) {
            plugin.getLogger().log(Level.WARNING, "[NexusDreams] " + message, cause);
        } else {
            plugin.getLogger().warning("[NexusDreams] " + message);
        }
    }
}
