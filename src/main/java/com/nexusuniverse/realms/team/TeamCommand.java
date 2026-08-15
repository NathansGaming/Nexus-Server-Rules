package com.nexusuniverse.realms.team;

import com.nexusuniverse.realms.hints.HintManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TeamCommand implements CommandExecutor {

    private final TeamManager teams;
    private final HintManager hints;

    public TeamCommand(TeamManager teams, HintManager hints) {
        this.teams = teams;
        this.hints = hints;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "info" -> handleInfo(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "transfer" -> handleTransfer(player, args);
            case "settings" -> handleSettings(player, args);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /team <create <name>|invite <player>|kick <player>|leave|disband|info [name]"
                + "|promote <player>|demote <player>|transfer <player>|settings <doors|containers|pvp> <on|off>>");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team create <name>");
            return;
        }
        if (teams.teamOf(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "You're already in a team -- leave it first with /team leave.");
            return;
        }
        if (teams.byName(args[1]) != null) {
            player.sendMessage(ChatColor.RED + "A team with that name already exists.");
            return;
        }
        Team team = teams.create(args[1], player.getUniqueId());
        player.sendMessage(ChatColor.AQUA + "Founded " + team.name() + ". You're the leader.");
        hints.sendContextual(player, "team_create");
    }

    private void handleInvite(Player player, String[] args) {
        Team team = requireRank(player, TeamRole.OFFICER);
        if (team == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }
        if (teams.teamOf(target.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + target.getName() + " is already on a team.");
            return;
        }
        // direct-add rather than an invite/accept flow -- kept simple for this version; a real
        // invite queue (with expiry, /team accept, etc.) is a natural next step if this feels
        // too permissive for your server
        team.setRole(target.getUniqueId(), TeamRole.MEMBER);
        teams.save();
        player.sendMessage(ChatColor.AQUA + "Added " + target.getName() + " to " + team.name() + " as a member.");
        target.sendMessage(ChatColor.AQUA + "You were added to " + team.name() + " by " + player.getName() + ".");
    }

    private void handleKick(Player player, String[] args) {
        Team team = requireRank(player, TeamRole.OFFICER);
        if (team == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team kick <player>");
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(team.owner())) {
            player.sendMessage(ChatColor.RED + "You can't kick the leader -- use /team transfer or /team disband instead.");
            return;
        }
        TeamRole targetRole = team.roleOf(target.getUniqueId());
        if (targetRole == null) {
            player.sendMessage(ChatColor.RED + "That player isn't in your team.");
            return;
        }
        if (targetRole == TeamRole.OFFICER && !team.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the leader can kick an officer.");
            return;
        }
        team.removeMember(target.getUniqueId());
        teams.save();
        player.sendMessage(ChatColor.AQUA + "Removed " + args[1] + " from " + team.name() + ".");
    }

    private void handleLeave(Player player) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return;
        }
        if (team.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You're the leader -- use /team transfer to hand off leadership first, or /team disband to dissolve the team.");
            return;
        }
        team.removeMember(player.getUniqueId());
        teams.save();
        player.sendMessage(ChatColor.AQUA + "You left " + team.name() + ".");
    }

    private void handleDisband(Player player) {
        Team team = requireOwnedTeam(player);
        if (team == null) return;
        teams.disband(team.id());
        player.sendMessage(ChatColor.AQUA + team.name() + " has been disbanded. Any land it claimed is now unclaimed.");
    }

    private void handleInfo(Player player, String[] args) {
        Team team = args.length >= 2 ? teams.byName(args[1]) : teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + (args.length >= 2 ? "No team by that name." : "You're not in a team."));
            return;
        }
        player.sendMessage(ChatColor.GRAY + "--- " + team.name() + " ---");
        player.sendMessage(ChatColor.GRAY + "Leader: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(team.owner()).getName());
        player.sendMessage(ChatColor.GRAY + "Members: " + ChatColor.WHITE + team.members().size());
        player.sendMessage(ChatColor.GRAY + "Outsiders may: " + ChatColor.WHITE
                + (team.allowOutsiderDoors() ? "use doors, " : "")
                + (team.allowOutsiderContainers() ? "use containers, " : "")
                + (team.allowOutsiderPvp() ? "fight here" : "")
                + (!team.allowOutsiderDoors() && !team.allowOutsiderContainers() && !team.allowOutsiderPvp() ? "nothing" : ""));
    }

    private void handlePromote(Player player, String[] args) {
        Team team = requireOwnedTeam(player); // only the LEADER can change ranks, to avoid officers promoting each other around the leader
        if (team == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team promote <player>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        switch (teams.promote(team, target)) {
            case OK -> player.sendMessage(ChatColor.AQUA + args[1] + " is now an officer.");
            case NOT_A_MEMBER -> player.sendMessage(ChatColor.RED + "That player isn't in your team.");
            case ALREADY_AT_LIMIT -> player.sendMessage(ChatColor.RED + args[1] + " is already the leader.");
            case USE_TRANSFER_INSTEAD -> player.sendMessage(ChatColor.RED + "That would make them leader -- use /team transfer " + args[1] + " for that.");
        }
    }

    private void handleDemote(Player player, String[] args) {
        Team team = requireOwnedTeam(player);
        if (team == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team demote <player>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        switch (teams.demote(team, target)) {
            case OK -> player.sendMessage(ChatColor.AQUA + args[1] + " is now a regular member.");
            case NOT_A_MEMBER -> player.sendMessage(ChatColor.RED + "That player isn't in your team.");
            case ALREADY_AT_LIMIT -> player.sendMessage(ChatColor.RED + args[1] + " is already the lowest rank.");
            case USE_TRANSFER_INSTEAD -> player.sendMessage(ChatColor.RED + "You can't demote the leader -- transfer leadership first.");
        }
    }

    private void handleTransfer(Player player, String[] args) {
        Team team = requireOwnedTeam(player);
        if (team == null) return;
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /team transfer <player>");
            return;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (!teams.transferLeadership(team, player.getUniqueId(), target)) {
            player.sendMessage(ChatColor.RED + "That player needs to already be in your team.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + args[1] + " is now the leader of " + team.name() + ". You're an officer.");
    }

    private void handleSettings(Player player, String[] args) {
        Team team = requireRank(player, TeamRole.OFFICER);
        if (team == null) return;
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /team settings <doors|containers|pvp> <on|off>");
            return;
        }
        boolean value = args[2].equalsIgnoreCase("on");
        switch (args[1].toLowerCase()) {
            case "doors" -> team.setAllowOutsiderDoors(value);
            case "containers" -> team.setAllowOutsiderContainers(value);
            case "pvp" -> team.setAllowOutsiderPvp(value);
            default -> {
                player.sendMessage(ChatColor.RED + "Usage: /team settings <doors|containers|pvp> <on|off>");
                return;
            }
        }
        teams.save();
        player.sendMessage(ChatColor.AQUA + "Outsiders may now " + (value ? "" : "no longer ") + args[1].toLowerCase()
                + " in " + team.name() + "'s territory.");
    }

    private Team requireOwnedTeam(Player player) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return null;
        }
        if (!team.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the team leader can do that.");
            return null;
        }
        return team;
    }

    private Team requireRank(Player player, TeamRole minimum) {
        Team team = teams.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team.");
            return null;
        }
        if (!team.hasRole(player.getUniqueId(), minimum)) {
            player.sendMessage(ChatColor.RED + "You need to be at least an " + minimum.name().toLowerCase() + " to do that.");
            return null;
        }
        return team;
    }
}
