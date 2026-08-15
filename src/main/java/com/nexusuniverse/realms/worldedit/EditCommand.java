package com.nexusuniverse.realms.worldedit;

import com.nexusuniverse.realms.RealmsConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EditCommand implements CommandExecutor {

    private final SelectionManager selections;
    private final EditPermissionChecker permissions;
    private final EditExecutor executor;
    private final RealmsConfig config;
    private final WandItemFactory wandItems;
    private final ClipboardManager clipboards;

    public EditCommand(SelectionManager selections, EditPermissionChecker permissions, EditExecutor executor, RealmsConfig config, WandItemFactory wandItems, ClipboardManager clipboards) {
        this.selections = selections;
        this.permissions = permissions;
        this.executor = executor;
        this.config = config;
        this.wandItems = wandItems;
        this.clipboards = clipboards;
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
            case "wand" -> handleWand(player);
            case "pos1" -> handlePos1(player);
            case "pos2" -> handlePos2(player);
            case "set" -> handleSet(player, args);
            case "replace" -> handleReplace(player, args);
            case "walls" -> handleWalls(player, args);
            case "outline" -> handleOutline(player, args);
            case "copy" -> handleCopy(player);
            case "paste" -> handlePaste(player);
            case "rotate" -> handleRotate(player, args);
            case "undo" -> handleUndo(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /redit <wand|pos1|pos2|set <material>|replace <from> <to>|walls <material>|outline <material>|copy|paste|rotate <degrees>|undo>");
    }

    private void handleWand(Player player) {
        if (!requirePermission(player)) return;
        player.getInventory().addItem(wandItems.create(config.worldEditWandMaterial()));
        player.sendMessage(ChatColor.AQUA + "Left-click to set position 1, right-click for position 2.");
    }

    private void handlePos1(Player player) {
        if (!requirePermission(player)) return;
        selections.get(player.getUniqueId()).setPos1(player.getLocation());
        player.sendMessage(ChatColor.AQUA + "Position 1 set to your location.");
    }

    private void handlePos2(Player player) {
        if (!requirePermission(player)) return;
        selections.get(player.getUniqueId()).setPos2(player.getLocation());
        player.sendMessage(ChatColor.AQUA + "Position 2 set to your location.");
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /redit set <material>");
            return;
        }
        Material material = parseMaterial(player, args[1]);
        if (material == null) return;

        Selection selection = validateSelection(player);
        if (selection == null) return;

        int changed = executor.set(player, selection, material);
        player.sendMessage(ChatColor.AQUA + "Set " + changed + " block(s) to " + material.name() + ".");
    }

    private void handleReplace(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /redit replace <from> <to>");
            return;
        }
        Material from = parseMaterial(player, args[1]);
        Material to = parseMaterial(player, args[2]);
        if (from == null || to == null) return;

        Selection selection = validateSelection(player);
        if (selection == null) return;

        int changed = executor.replace(player, selection, from, to);
        player.sendMessage(ChatColor.AQUA + "Replaced " + changed + " block(s) of " + from.name() + " with " + to.name() + ".");
    }

    private void handleWalls(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /redit walls <material>");
            return;
        }
        Material material = parseMaterial(player, args[1]);
        if (material == null) return;

        Selection selection = validateSelection(player);
        if (selection == null) return;

        int changed = executor.walls(player, selection, material);
        player.sendMessage(ChatColor.AQUA + "Built walls out of " + changed + " block(s) of " + material.name() + ".");
    }

    private void handleOutline(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /redit outline <material>");
            return;
        }
        Material material = parseMaterial(player, args[1]);
        if (material == null) return;

        Selection selection = validateSelection(player);
        if (selection == null) return;

        int changed = executor.outline(player, selection, material);
        player.sendMessage(ChatColor.AQUA + "Outlined " + changed + " block(s) with " + material.name() + ".");
    }

    private void handleCopy(Player player) {
        // Same rules as set/replace/walls/outline: admins anywhere, default players only inside their own personal claim.
        Selection selection = validateSelection(player);
        if (selection == null) return;

        int copied = executor.copy(player, selection, clipboards);
        player.sendMessage(ChatColor.AQUA + "Copied " + copied + " block(s) to your clipboard.");
    }

    private void handlePaste(Player player) {
        if (!requirePermission(player)) return;

        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            player.sendMessage(ChatColor.RED + "Your clipboard is empty -- /redit copy a selection first.");
            return;
        }

        if (clipboard.size() > config.worldEditMaxVolume()) {
            player.sendMessage(ChatColor.RED + "That clipboard is " + clipboard.size() + " blocks -- over the "
                    + config.worldEditMaxVolume() + "-block limit.");
            return;
        }

        Location origin = player.getLocation();
        String world = origin.getWorld().getName();
        int minX = origin.getBlockX() + clipboard.minDx();
        int maxX = origin.getBlockX() + clipboard.maxDx();
        int minZ = origin.getBlockZ() + clipboard.minDz();
        int maxZ = origin.getBlockZ() + clipboard.maxDz();

        // Checked against where the paste would LAND, not where it was copied from -- a player can
        // copy inside their claim and still be blocked from pasting somewhere that isn't theirs.
        EditPermissionChecker.Result result = permissions.check(player, world, minX, minZ, maxX, maxZ);
        switch (result) {
            case NO_PERMISSION -> {
                player.sendMessage(ChatColor.RED + "No permission.");
                return;
            }
            case NOT_YOUR_CLAIM -> {
                player.sendMessage(ChatColor.RED + "That whole paste needs to land inside one of your own personal claims.");
                return;
            }
            case ALLOWED -> {
                // fall through
            }
        }

        int pasted = executor.paste(player, clipboard, origin);
        player.sendMessage(ChatColor.AQUA + "Pasted " + pasted + " block(s).");
    }

    private void handleRotate(Player player, String[] args) {
        if (!requirePermission(player)) return;

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /redit rotate <degrees>");
            return;
        }

        int degrees;
        try {
            degrees = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "\"" + args[1] + "\" isn't a whole number of degrees.");
            return;
        }

        if (!ClipboardRotator.isSupportedAngle(degrees)) {
            player.sendMessage(ChatColor.RED + "Rotation only works in multiples of 90 degrees.");
            return;
        }

        Clipboard clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            player.sendMessage(ChatColor.RED + "Your clipboard is empty -- /redit copy a selection first.");
            return;
        }

        clipboards.set(player.getUniqueId(), ClipboardRotator.rotate(clipboard, degrees));
        player.sendMessage(ChatColor.AQUA + "Rotated your clipboard " + degrees + " degrees.");
    }

    private void handleUndo(Player player) {
        if (!requirePermission(player)) return;
        int restored = executor.undo(player);
        if (restored < 0) {
            player.sendMessage(ChatColor.RED + "Nothing to undo.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Restored " + restored + " block(s).");
    }

    /** Shared checks for every actual editing operation: permission, complete selection, in-bounds ownership, and the volume cap. */
    private Selection validateSelection(Player player) {
        if (!requirePermission(player)) return null;

        Selection selection = selections.get(player.getUniqueId());
        if (!selection.isComplete()) {
            player.sendMessage(ChatColor.RED + "Select two positions first -- /redit wand, or /redit pos1 and /redit pos2.");
            return null;
        }

        if (selection.volume() > config.worldEditMaxVolume()) {
            player.sendMessage(ChatColor.RED + "That selection is " + selection.volume() + " blocks -- over the "
                    + config.worldEditMaxVolume() + "-block limit. Select something smaller.");
            return null;
        }

        EditPermissionChecker.Result result = permissions.check(player, selection);
        switch (result) {
            case NO_PERMISSION -> {
                player.sendMessage(ChatColor.RED + "No permission.");
                return null;
            }
            case NOT_YOUR_CLAIM -> {
                player.sendMessage(ChatColor.RED + "That whole selection needs to be inside one of your own personal claims.");
                return null;
            }
            case ALLOWED -> {
                // fall through
            }
        }

        return selection;
    }

    private boolean requirePermission(Player player) {
        if (player.hasPermission("nexusrealms.worldedit.use") || player.hasPermission("nexusrealms.worldedit.admin")) return true;
        player.sendMessage(ChatColor.RED + "No permission.");
        return false;
    }

    private Material parseMaterial(Player player, String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "\"" + name + "\" isn't a real material name.");
            return null;
        }
    }
}
