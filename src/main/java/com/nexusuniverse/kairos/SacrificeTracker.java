package com.nexusuniverse.kairos;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kairos's summon ritual requires a sacrifice: killing pigs or
 * endermen while in the End. Endermen already spawn there naturally;
 * a pig requires the player to actually bring one in, which is its
 * own small challenge. Either counts equally toward the requirement.
 *
 * Counts are per-player and NOT reset by death/logout -- only consumed
 * on a successful summon (see FightManager), so partial progress isn't
 * lost if someone gets interrupted mid-ritual.
 */
public class SacrificeTracker implements Listener {

    private final NexusKairosPlugin plugin;
    private final Map<UUID, Integer> counts = new HashMap<>();

    public SacrificeTracker(NexusKairosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        if (event.getEntityType() != EntityType.PIG && event.getEntityType() != EntityType.ENDERMAN) return;

        String endWorldName = plugin.getConfig().getString("arena.world", "world_the_end");
        if (!event.getEntity().getWorld().getName().equals(endWorldName)) return;

        Player killer = event.getEntity().getKiller();
        counts.merge(killer.getUniqueId(), 1, Integer::sum);

        int required = plugin.getConfig().getInt("fight.sacrifice-required", 5);
        int current = counts.get(killer.getUniqueId());
        if (current <= required) {
            killer.sendMessage("§5Sacrifice: §f" + current + "/" + required);
        }
    }

    public int getCount(Player player) {
        return counts.getOrDefault(player.getUniqueId(), 0);
    }

    public void reset(Player player) {
        counts.remove(player.getUniqueId());
    }
}
