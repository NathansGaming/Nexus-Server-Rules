package com.nexusuniverse.realms.guidebook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GuidebookCommand implements CommandExecutor {

    private final GuidebookItem item;

    public GuidebookCommand(GuidebookItem item) {
        this.item = item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexusrealms.guidebook.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /nexusguide give <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found (must be online).");
            return true;
        }

        ItemStack book = item.create();
        var leftover = target.getInventory().addItem(book);
        for (ItemStack extra : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), extra);
        }
        sender.sendMessage(ChatColor.AQUA + "Gave " + target.getName() + " a copy of the handbook.");
        target.sendMessage(ChatColor.GRAY + "You were given a copy of the Nexus Realms Handbook by " + sender.getName() + ".");
        return true;
    }
}
