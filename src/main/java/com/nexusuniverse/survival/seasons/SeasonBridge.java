package com.nexusuniverse.survival.seasons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Soft integration with NexusSeasons -- a separate, independently-built
 * plugin, so there's no compile-time dependency to reach across (that
 * would mean sharing a Maven artifact between two plugins that are each
 * meant to work standalone). Instead this looks up NexusSeasons'
 * NexusSeasonsAPI via Bukkit's ServicesManager and calls it reflectively.
 *
 * If NexusSeasons isn't installed, or hasn't finished enabling yet,
 * every method here just returns null/false and callers fall back to
 * their normal (non-seasonal) behavior -- nothing else in NexusSurvival
 * needs to know or care whether the other plugin exists.
 */
public class SeasonBridge {

    private static final String API_CLASS_NAME = "com.nexusuniverse.seasons.NexusSeasonsAPI";

    private Object api;
    private Method getCurrentSeasonMethod;

    public boolean isConnected() {
        if (api == null) tryConnect(); // NexusSeasons might enable after NexusSurvival did -- keep retrying lazily
        return api != null;
    }

    private void tryConnect() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(apiClass);
            if (provider == null) return;

            this.api = provider.getProvider();
            this.getCurrentSeasonMethod = apiClass.getMethod("getCurrentSeason");
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            // NexusSeasons isn't installed -- stay disconnected
        }
    }

    /** The current season's enum name (e.g. "WINTER"), or null if NexusSeasons isn't available. */
    public String currentSeasonName() {
        if (!isConnected()) return null;
        try {
            Object season = getCurrentSeasonMethod.invoke(api);
            return season != null ? season.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
