package com.nexusuniverse.survival.disease;

import com.nexusuniverse.survival.NexusSurvivalPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class DiseaseListener implements Listener {

    private final NexusSurvivalPlugin plugin;

    public DiseaseListener(NexusSurvivalPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Disease cureType = plugin.getDiseaseItems().readCureType(event.getItem());
        if (cureType == null) return;

        Player player = event.getPlayer();
        boolean cured = plugin.getDiseaseManager().cure(player, cureType);
        if (!cured) {
            player.sendMessage("§7That cure doesn't match what you have. Nothing happens.");
        }
    }

    /** Natural (hunger-driven) regen is disabled while infected -- otherwise it would quietly out-heal Critical-tier damage. */
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (plugin.getDiseaseManager().getInfection(player) != null) {
            event.setCancelled(true);
        }
    }

    /** Dying while infected leaves a lingering infectious site behind, and gets a dedicated death message + record. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Disease infection = plugin.getDiseaseManager().getInfection(player);
        if (infection == null) return;

        event.setDeathMessage("§4\u2620 " + player.getName() + " succumbed to " + infection.getDisplayName() + ".");
        plugin.getDiseaseManager().markPlagueSite(player.getLocation(), infection);
        plugin.getDiseaseManager().recordPlagueDeath(player.getName(), infection);
    }
}

