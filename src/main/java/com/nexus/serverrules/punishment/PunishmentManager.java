package com.nexus.serverrules.punishment;

import com.nexus.serverrules.detection.ViolationResult;
import com.nexus.serverrules.storage.RestrictionStore;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.logging.Level;

public final class PunishmentManager {

    // Effectively-permanent: reapplied on join anyway, and cleared
    // explicitly on /nexusrules clear rather than left to expire.
    private static final int EFFECT_DURATION_TICKS = Integer.MAX_VALUE;
    private static final int BLINDNESS_AMPLIFIER = 0;
    private static final int SLOWNESS_AMPLIFIER = 6; // Slowness VII - near-total immobilization

    private final JavaPlugin plugin;
    private final RestrictionStore store;

    public PunishmentManager(JavaPlugin plugin, RestrictionStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /**
     * Applies a full restriction to an online player and persists it.
     * Idempotent - if the player is already restricted, this just
     * re-asserts the effects (handles the case of a second violation
     * while already flagged) without stacking duplicate entries.
     */
    public void punish(Player player, ViolationResult violation) {
        Restriction existing = store.get(player.getUniqueId());
        String previousGameMode = existing != null
                ? existing.previousGameMode
                : player.getGameMode().name();

        Restriction restriction = new Restriction(
                player.getUniqueId(),
                player.getName(),
                violation.category(),
                violation.shortReason(),
                violation.rawMessage(),
                violation.timestamp(),
                previousGameMode
        );
        store.put(restriction);

        applyEffects(player);

        plugin.getLogger().warning("[NexusServerRules] AUTO-PUNISHED " + player.getName()
                + " (" + player.getUniqueId() + ") - " + violation.shortReason()
                + " - message: \"" + violation.rawMessage() + "\"");
    }

    /** Applies the actual client-visible restriction effects. Safe to call repeatedly. */
    public void applyEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_DURATION_TICKS, BLINDNESS_AMPLIFIER, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS, SLOWNESS_AMPLIFIER, false, false));
        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    /**
     * Clears a restriction: removes potion effects, restores the
     * player's original gamemode, unmutes, and removes the persisted
     * record. Requires the calling command to have already checked
     * the nexusrules.clear permission.
     */
    public boolean clear(UUID playerId, String staffName) {
        Restriction restriction = store.get(playerId);
        if (restriction == null) return false;

        restriction.active = false;
        restriction.clearedBy = staffName;
        restriction.clearedAt = java.time.Instant.now();

        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            online.removePotionEffect(PotionEffectType.BLINDNESS);
            online.removePotionEffect(PotionEffectType.SLOWNESS);
            try {
                online.setGameMode(GameMode.valueOf(restriction.previousGameMode));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Unknown stored gamemode '"
                        + restriction.previousGameMode + "' for " + restriction.playerName + ", defaulting to SURVIVAL", ex);
                online.setGameMode(GameMode.SURVIVAL);
            }
            online.sendMessage("§a[NexusServerRules] Your restriction has been cleared by staff. You may speak and move freely again.");
        }

        store.remove(playerId);
        plugin.getLogger().info("[NexusServerRules] " + staffName + " cleared restriction for " + restriction.playerName);
        return true;
    }

    public boolean isRestricted(UUID playerId) {
        return store.isRestricted(playerId);
    }
}
