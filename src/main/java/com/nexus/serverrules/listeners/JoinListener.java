package com.nexus.serverrules.listeners;

import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.storage.PlayerNameCache;
import com.nexus.serverrules.storage.RestrictionStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Potion effects and gamemode are session state on the Minecraft
 * client/server connection - they do NOT survive a disconnect on
 * their own. This listener is what makes a restriction actually
 * "persist across logout" as designed: on every join we check the
 * on-disk store (not just re-trust whatever state the client shows
 * up in) and re-apply blindness/slowness/adventure mode if the
 * player still has an active, staff-uncleared restriction.
 */
public final class JoinListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final RestrictionStore restrictionStore;
    private final PlayerNameCache playerNameCache;

    public JoinListener(PunishmentManager punishmentManager, RestrictionStore restrictionStore, PlayerNameCache playerNameCache) {
        this.punishmentManager = punishmentManager;
        this.restrictionStore = restrictionStore;
        this.playerNameCache = playerNameCache;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Record on every join regardless of restriction status - this
        // is what lets /ban, /nexusrules clear, and /nexusrules info
        // resolve a name to a UUID later without a blocking web lookup.
        playerNameCache.record(player.getName(), player.getUniqueId());

        if (!restrictionStore.isRestricted(player.getUniqueId())) return;

        punishmentManager.applyEffects(player);
        player.sendMessage("§c[NexusServerRules] You are still under an active restriction from a previous "
                + "session. It has not expired and cannot be undone by logging out - a staff member needs to "
                + "review your case. Use /appeal <message> if you want to explain your side.");
    }
}
