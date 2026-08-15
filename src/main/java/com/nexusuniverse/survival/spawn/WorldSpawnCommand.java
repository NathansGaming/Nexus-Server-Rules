package com.nexusuniverse.survival.spawn;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WorldSpawnCommand implements CommandExecutor {

    private final SpawnManager spawnManager;

    public WorldSpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Location location = spawnManager.spawnLocation();
        if (location == null) {
            player.sendMessage("§cWorld spawn isn't configured correctly -- ask an admin to check spawn.world in NexusSurvival's config.yml.");
            return true;
        }

        player.teleport(location);
        player.sendMessage("§aTeleported to world spawn.");
        return true;
    }
}
