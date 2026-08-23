package com.nexusuniverse.chroma.command;

import com.nexusuniverse.chroma.color.ColorTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class NexusChromaCommand implements CommandExecutor {

    private static final int PAGE_SIZE = 15;

    private final ColorTable colorTable;

    public NexusChromaCommand(ColorTable colorTable) {
        this.colorTable = colorTable;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(
                    "Usage: /nexuschroma <info|list [page]|add <material> <hex>|remove <material>|reload>",
                    NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> {
                sender.sendMessage(Component.text(
                        "NexusChroma: " + colorTable.all().size() + " block color(s) loaded.",
                        NamedTextColor.AQUA));
                return true;
            }
            case "list" -> {
                return handleList(sender, args);
            }
            case "add" -> {
                return handleAdd(sender, args);
            }
            case "remove" -> {
                return handleRemove(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("nexuschroma.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                    return true;
                }
                colorTable.load();
                sender.sendMessage(Component.text(
                        "Reloaded colors.yml -- " + colorTable.all().size() + " block color(s) loaded.",
                        NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private boolean handleList(CommandSender sender, String[] args) {
        List<Material> materials = new ArrayList<>(colorTable.all().keySet());
        int totalPages = Math.max(1, (materials.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Page must be a number.", NamedTextColor.RED));
                return true;
            }
        }
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage(Component.text(
                "NexusChroma colors -- page " + page + "/" + totalPages + " (" + materials.size() + " total):",
                NamedTextColor.AQUA));

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, materials.size());
        for (int i = from; i < to; i++) {
            Material material = materials.get(i);
            Color color = colorTable.get(material);
            sender.sendMessage(Component.text(
                    " - " + material.name() + "  #" + ColorTable.toHex(color),
                    NamedTextColor.GRAY));
        }
        if (page < totalPages) {
            sender.sendMessage(Component.text("Next: /nexuschroma list " + (page + 1), NamedTextColor.DARK_GRAY));
        }
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuschroma.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /nexuschroma add <material> <hex>", NamedTextColor.YELLOW));
            return true;
        }
        Material material;
        try {
            material = Material.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown material: " + args[1], NamedTextColor.RED));
            return true;
        }
        Color color = ColorTable.parseHex(args[2]);
        if (color == null) {
            sender.sendMessage(Component.text("Invalid hex color -- use RRGGBB, e.g. 7D7D7D.", NamedTextColor.RED));
            return true;
        }
        colorTable.set(material, color);
        sender.sendMessage(Component.text(
                "Set " + material.name() + " -> #" + ColorTable.toHex(color) + " and saved to colors.yml.",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuschroma.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /nexuschroma remove <material>", NamedTextColor.YELLOW));
            return true;
        }
        Material material;
        try {
            material = Material.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown material: " + args[1], NamedTextColor.RED));
            return true;
        }
        boolean removed = colorTable.remove(material);
        sender.sendMessage(removed
                ? Component.text("Removed " + material.name() + " from colors.yml.", NamedTextColor.GREEN)
                : Component.text(material.name() + " wasn't in the color table.", NamedTextColor.YELLOW));
        return true;
    }
}
