package com.nexus.voidrescue;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /voidrescue &lt;playerName|all|reload&gt; - the immediate, manual override
 * for a player stuck right now that a plain /tp or /kill can't reach. Bypasses
 * cooldown and every state check entirely: given a specific player name, it
 * unconditionally force-rescues them.
 */
public final class VoidRescueCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final RescueService rescueService;

    public VoidRescueCommand(JavaPlugin plugin, RescueService rescueService) {
        this.plugin = plugin;
        this.rescueService = rescueService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidrescue.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /voidrescue <playerName|all|reload>");
            return true;
        }

        String arg = args[0];

        if (arg.equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("[VoidRescue] Config reloaded.");
            return true;
        }

        if (arg.equalsIgnoreCase("all")) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || rescueService.isBelowVoidThreshold(player)) {
                    rescueService.rescue(player, "manual /voidrescue all by " + sender.getName(), true);
                    count++;
                }
            }
            sender.sendMessage("[VoidRescue] Force-rescued " + count + " player(s).");
            return true;
        }

        Player target = Bukkit.getPlayerExact(arg);
        if (target == null) {
            sender.sendMessage("[VoidRescue] Player '" + arg + "' isn't online.");
            return true;
        }
        rescueService.rescue(target, "manual /voidrescue by " + sender.getName(), true);
        sender.sendMessage("[VoidRescue] Force-rescued " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("all", "reload"));
        options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        String prefix = args[0].toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
    }
}
