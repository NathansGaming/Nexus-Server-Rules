package com.nexusuniverse.realms.protect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A live watchlist -- add a material once (/nexusrealms protect watch add <material>) and from
 * then on, the moment ANY player obtains one (picks it up off the ground, pulls it out of a
 * container, or crafts it -- see InventoryLogListener for where each of those actually gets
 * checked), every online player holding nexusrealms.protect.notify gets an immediate chat
 * notification naming who, what, and where. Persisted to config.yml so the list survives a
 * restart.
 */
public class WatchlistManager {

    private final JavaPlugin plugin;
    private final Set<Material> watched = new LinkedHashSet<>();

    public WatchlistManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        watched.clear();
        for (String name : plugin.getConfig().getStringList("protect.watchlist")) {
            try {
                watched.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("NexusRealms: \"" + name + "\" in protect.watchlist isn't a real material, skipping it.");
            }
        }
    }

    public boolean isWatched(Material material) {
        return watched.contains(material);
    }

    public boolean add(Material material) {
        boolean added = watched.add(material);
        if (added) save();
        return added;
    }

    public boolean remove(Material material) {
        boolean removed = watched.remove(material);
        if (removed) save();
        return removed;
    }

    public Set<Material> all() {
        return Set.copyOf(watched);
    }

    private void save() {
        plugin.getConfig().set("protect.watchlist", watched.stream().map(Enum::name).toList());
        plugin.saveConfig();
    }

    /** Call whenever a player is confirmed to have obtained (not just be holding -- actually gained) a watched item. */
    public void notifyObtained(Player player, Material material, int amount, String contextDescription) {
        if (!isWatched(material)) return;

        String message = ChatColor.GOLD + "[Watchlist] " + ChatColor.YELLOW + player.getName()
                + ChatColor.GRAY + " obtained " + ChatColor.WHITE + amount + "x " + prettyName(material)
                + ChatColor.GRAY + " (" + contextDescription + ")";

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusrealms.protect.notify")) {
                staff.sendMessage(message);
            }
        }
    }

    private String prettyName(Material material) {
        String raw = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
