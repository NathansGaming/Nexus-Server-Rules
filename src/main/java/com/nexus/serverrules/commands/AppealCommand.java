package com.nexus.serverrules.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Deliberately a separate command (not routed through normal chat) so
 * it is unaffected by the chat-cancelling mute in ChatListener - a
 * restricted player always has this one channel to reach staff,
 * regardless of what triggered their restriction. This is the safety
 * valve for false positives: since restrictions require manual staff
 * clearing rather than auto-expiring, a wrongly-flagged player needs
 * a guaranteed way to say so.
 */
public final class AppealCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final File appealsLog;

    // Simple per-player cooldown so /appeal can't be used to spam staff.
    private final java.util.Map<java.util.UUID, Instant> lastAppeal = new java.util.HashMap<>();
    private static final long COOLDOWN_SECONDS = 60;

    public AppealCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.appealsLog = new File(plugin.getDataFolder(), "appeals-log.txt");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /appeal.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§eUsage: /appeal <your message to staff>");
            return true;
        }

        Instant last = lastAppeal.get(player.getUniqueId());
        if (last != null && Instant.now().getEpochSecond() - last.getEpochSecond() < COOLDOWN_SECONDS) {
            player.sendMessage("§eYou can send another appeal in a moment - staff have already been notified.");
            return true;
        }
        lastAppeal.put(player.getUniqueId(), Instant.now());

        String message = String.join(" ", args);
        String alert = "§b[Appeal] " + player.getName() + ": " + message;

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusrules.notify")) {
                staff.sendMessage(alert);
            }
        }
        plugin.getLogger().info("[NexusServerRules] " + alert);
        appendToDisk(player.getName(), player.getUniqueId().toString(), message);

        player.sendMessage("§a[NexusServerRules] Your appeal was sent to staff. Someone will review your case.");
        return true;
    }

    private void appendToDisk(String name, String uuid, String message) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            try (FileWriter writer = new FileWriter(appealsLog, true)) {
                writer.write(String.format("[%s] %s (%s): %s%n", Instant.now(), name, uuid, message));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[NexusServerRules] Failed to write appeals-log.txt", e);
        }
    }
}
