package com.nexusuniverse.realms.home;

import com.nexusuniverse.realms.RealmsConfig;
import com.nexusuniverse.realms.hints.HintManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class HomeCommand implements CommandExecutor {

    private static final String DEFAULT_HOME_NAME = "home";

    private final HomeManager homes;
    private final RealmsConfig config;
    private final HintManager hints;

    public HomeCommand(HomeManager homes, RealmsConfig config, HintManager hints) {
        this.homes = homes;
        this.config = config;
        this.hints = hints;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (label.toLowerCase()) {
            case "sethome" -> handleSetHome(player, args);
            case "home" -> handleHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes" -> handleListHomes(player);
            default -> {
                // unreachable given plugin.yml's command list, but keep a sane fallback
                player.sendMessage(ChatColor.YELLOW + "Usage: /sethome [name], /home [name], /delhome [name], /homes");
            }
        }
        return true;
    }

    private void handleSetHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : DEFAULT_HOME_NAME;
        int max = maxHomesFor(player);

        HomeManager.SetResult result = homes.set(player.getUniqueId(), name, player.getLocation(), max);
        if (result == HomeManager.SetResult.TOO_MANY) {
            player.sendMessage(ChatColor.RED + "You're at your home limit (" + max + "). Delete one first with /delhome <name>.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Home \"" + name + "\" set.");
        hints.sendContextual(player, "sethome_first");
    }

    private void handleHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : DEFAULT_HOME_NAME;
        Location home = homes.get(player.getUniqueId(), name);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "No home named \"" + name + "\". Set one with /sethome" + (name.equals(DEFAULT_HOME_NAME) ? "" : " " + name) + ".");
            return;
        }
        player.teleport(home);
        player.sendMessage(ChatColor.AQUA + "Teleported to \"" + name + "\".");
    }

    private void handleDelHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : DEFAULT_HOME_NAME;
        if (!homes.delete(player.getUniqueId(), name)) {
            player.sendMessage(ChatColor.RED + "No home named \"" + name + "\".");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Home \"" + name + "\" deleted.");
    }

    private void handleListHomes(Player player) {
        Map<String, Location> mine = homes.homesOf(player.getUniqueId());
        int max = maxHomesFor(player);
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any homes set (0/" + max + "). /sethome to make one.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "--- Your homes (" + mine.size() + "/" + max + ") ---");
        for (Map.Entry<String, Location> entry : mine.entrySet()) {
            Location loc = entry.getValue();
            player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + entry.getKey() + ChatColor.GRAY + " in "
                    + loc.getWorld().getName() + " at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    private int maxHomesFor(Player player) {
        return player.hasPermission("nexusrealms.homes.admin") ? config.homesMaxAdmin() : config.homesMaxDefault();
    }
}
