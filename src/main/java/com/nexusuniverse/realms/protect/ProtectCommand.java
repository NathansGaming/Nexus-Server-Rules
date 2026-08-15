package com.nexusuniverse.realms.protect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ProtectCommand implements CommandExecutor {

    private final RollbackManager rollback;
    private final WatchlistManager watchlist;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a");

    public ProtectCommand(RollbackManager rollback, WatchlistManager watchlist) {
        this.rollback = rollback;
        this.watchlist = watchlist;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexusrealms.protect.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "lookup" -> handleLookup(sender, args);
            case "rollback" -> handleRollback(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "watch" -> handleWatch(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /protect <lookup|rollback> <player|radius <n>|time <e.g. 2h>> "
                + "| inspect <player> | watch <add|remove|list> [material]");
    }

    // --- lookup / rollback share the same filter parsing ---

    private void handleLookup(CommandSender sender, String[] args) {
        RollbackFilter filter = parseFilter(sender, args);
        if (filter == null) return;

        rollback.lookup(filter, rows -> {
            if (rows.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No matching block changes found.");
                return;
            }
            sender.sendMessage(ChatColor.GRAY + "--- " + rows.size() + " matching change(s), most recent first ---");
            int shown = 0;
            for (BlockLogRow row : rows) {
                if (shown++ >= 30) {
                    sender.sendMessage(ChatColor.GRAY + "...and " + (rows.size() - 30) + " more (narrow your filter to see the rest).");
                    break;
                }
                sender.sendMessage(formatRow(row));
            }
        });
    }

    private void handleRollback(CommandSender sender, String[] args) {
        RollbackFilter filter = parseFilter(sender, args);
        if (filter == null) return;

        sender.sendMessage(ChatColor.YELLOW + "Rolling back matching changes...");
        rollback.rollback(filter, result ->
                sender.sendMessage(ChatColor.AQUA + "Restored " + result.restored() + " of " + result.matched()
                        + " matching block change(s)."));
    }

    /**
     * Shared arg parsing for lookup/rollback: /nexusrealms <lookup|rollback> [player <name>]
     * [radius <n>] [time <duration>]. "time" is required; player/radius are optional filters,
     * radius is centered on the sender's current location. Duration accepts a trailing
     * s/m/h/d/w unit (e.g. "2h", "30m", "1d") -- defaults to hours if no unit is given.
     */
    private RollbackFilter parseFilter(CommandSender sender, String[] args) {
        String playerName = null;
        double radius = -1;
        Long sinceMillis = null;

        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i].toLowerCase()) {
                case "player" -> playerName = args[++i];
                case "radius" -> {
                    try {
                        radius = Double.parseDouble(args[++i]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Radius must be a number.");
                        return null;
                    }
                }
                case "time" -> {
                    Long parsed = parseDuration(args[++i]);
                    if (parsed == null) {
                        sender.sendMessage(ChatColor.RED + "Couldn't parse time \"" + args[i] + "\" -- try e.g. 2h, 30m, 1d.");
                        return null;
                    }
                    sinceMillis = System.currentTimeMillis() - parsed;
                }
                default -> {
                    sender.sendMessage(ChatColor.RED + "Unknown filter \"" + args[i] + "\".");
                    return null;
                }
            }
        }

        if (sinceMillis == null) {
            sender.sendMessage(ChatColor.RED + "A time filter is required, e.g. \"time 2h\".");
            return null;
        }

        Location center = null;
        if (radius >= 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Radius filtering needs a player location -- run this in-game, or omit \"radius\".");
                return null;
            }
            center = player.getLocation();
        }

        return RollbackFilter.of(playerName, center, radius, sinceMillis);
    }

    /** Accepts a trailing s/m/h/d/w unit; bare numbers are treated as hours. */
    private Long parseDuration(String raw) {
        try {
            char unit = Character.toLowerCase(raw.charAt(raw.length() - 1));
            boolean hasUnit = "smhdw".indexOf(unit) >= 0;
            String numberPart = hasUnit ? raw.substring(0, raw.length() - 1) : raw;
            long value = Long.parseLong(numberPart);
            long unitMillis = switch (hasUnit ? unit : 'h') {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> 3_600_000L; // hours
            };
            return value * unitMillis;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatRow(BlockLogRow row) {
        long ageMillis = System.currentTimeMillis() - row.timestamp();
        double ageHours = ageMillis / 3_600_000.0;
        String ageDescription = ageHours < 1
                ? Math.round(ageMillis / 60_000.0) + "m ago"
                : String.format("%.1fh ago", ageHours);

        return ChatColor.GRAY + "[" + row.action() + "] " + ChatColor.WHITE + row.playerName()
                + ChatColor.GRAY + " -- " + DATE_FORMAT.format(new Date(row.timestamp()))
                + " (" + ageDescription + ") in " + ChatColor.YELLOW + row.world() + ChatColor.GRAY
                + " at " + row.x() + "," + row.y() + "," + row.z();
    }

    // --- inspect: a player's LIVE current inventory, not a log lookup ---

    private void handleInspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /protect inspect <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found (must be online).");
            return;
        }

        PlayerInventory inventory = target.getInventory();
        sender.sendMessage(ChatColor.GRAY + "--- " + target.getName() + "'s inventory ---");
        boolean any = false;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            any = true;
            sender.sendMessage(ChatColor.WHITE + "" + item.getAmount() + "x " + ChatColor.GRAY + item.getType().name());
        }
        if (!any) sender.sendMessage(ChatColor.GRAY + "(empty)");
    }

    // --- watchlist management ---

    private void handleWatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /protect watch <add|remove|list> [material]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                Material material = parseMaterial(sender, args);
                if (material == null) return;
                if (watchlist.add(material)) {
                    sender.sendMessage(ChatColor.AQUA + "Now watching " + material.name() + ".");
                } else {
                    sender.sendMessage(ChatColor.GRAY + material.name() + " is already on the watchlist.");
                }
            }
            case "remove" -> {
                Material material = parseMaterial(sender, args);
                if (material == null) return;
                if (watchlist.remove(material)) {
                    sender.sendMessage(ChatColor.AQUA + "No longer watching " + material.name() + ".");
                } else {
                    sender.sendMessage(ChatColor.GRAY + material.name() + " wasn't on the watchlist.");
                }
            }
            case "list" -> {
                List<Material> all = watchlist.all().stream().toList();
                if (all.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Watchlist is empty.");
                    return;
                }
                sender.sendMessage(ChatColor.GRAY + "--- Watchlist (" + all.size() + ") ---");
                for (Material material : all) {
                    sender.sendMessage(ChatColor.WHITE + "- " + material.name());
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /protect watch <add|remove|list> [material]");
        }
    }

    private Material parseMaterial(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /protect watch " + args[1] + " <material>");
            return null;
        }
        try {
            return Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "\"" + args[2] + "\" isn't a real material name.");
            return null;
        }
    }
}
