package com.nexusuniverse.realms.hints;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Two genuinely different mechanisms, both aimed at the same goal ("teach them all the commands
 * without a wiki"):
 *
 *  - PERIODIC: a broadcast tip, cycling through the full configured list one at a time, on a
 *    plain real-seconds interval -- always-on background teaching for anyone online, regardless
 *    of what they've done.
 *  - CONTEXTUAL: fired from inside a command's own success path right after a player does
 *    something meaningful (claims their first chunk, stakes their first personal plot, etc.) --
 *    "you just did X, here's the natural next command." Shown to that ONE player, not broadcast,
 *    and by default only the very first time they trigger each hint key (hints.contextual.
 *    repeat-every-time can turn that off) -- an experienced player claiming their 50th chunk
 *    doesn't need the same tutorial line every time, but their first chunk is exactly when it's
 *    useful. "Already seen" is tracked per player and persisted, so it survives a restart.
 */
public class HintManager {

    private final JavaPlugin plugin;
    private final File progressFile;
    private final Map<UUID, Set<String>> seenHints = new HashMap<>();
    private BukkitTask periodicTask;
    private int periodicIndex = 0;

    public HintManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "hint-progress.yml");
        loadProgress();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hints.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("hints.periodic.enabled", true)) return;

        int intervalSeconds = Math.max(5, plugin.getConfig().getInt("hints.periodic.interval-seconds", 600));
        long intervalTicks = 20L * intervalSeconds;
        periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNextTip, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (periodicTask != null) periodicTask.cancel();
        saveProgress();
    }

    private void broadcastNextTip() {
        List<String> messages = plugin.getConfig().getStringList("hints.periodic.messages");
        if (messages.isEmpty()) return;

        String raw = messages.get(periodicIndex % messages.size());
        periodicIndex++;
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', raw));
    }

    /**
     * Call from a command's own success path, e.g. hints.sendContextual(player, "land_claim")
     * right after /realms claim succeeds. key must match a list under config's
     * hints.contextual.<key> to do anything.
     */
    public void sendContextual(Player player, String key) {
        if (!plugin.getConfig().getBoolean("hints.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("hints.contextual.enabled", true)) return;

        boolean repeatEveryTime = plugin.getConfig().getBoolean("hints.contextual.repeat-every-time", false);
        if (!repeatEveryTime) {
            Set<String> seen = seenHints.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
            if (!seen.add(key)) return; // already seen this one and we're not repeating -- nothing to send
            saveProgress();
        }

        List<String> messages = plugin.getConfig().getStringList("hints.contextual." + key);
        for (String raw : messages) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
        }
    }

    private void saveProgress() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : seenHints.entrySet()) {
            data.set("seen." + entry.getKey(), List.copyOf(entry.getValue()));
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save hint-progress.yml", e);
        }
    }

    private void loadProgress() {
        if (!progressFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(progressFile);
        var section = data.getConfigurationSection("seen");
        if (section == null) return;

        for (String idKey : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idKey);
                seenHints.put(id, new HashSet<>(data.getStringList("seen." + idKey)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped corrupt hint progress for \"" + idKey + "\"", e);
            }
        }
    }
}
