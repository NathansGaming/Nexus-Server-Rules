package com.realsociety.glowfusion.popouts;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The builder-facing side of the popout system: select two corners, capture
 * an "off" (stowed) and an "on" (deployed) snapshot of that same space, then
 * bind a lever to the pair. See plugin.yml for the command's permission.
 */
public final class PopoutCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("pos1", "pos2", "saveoff", "saveon", "bind", "unbind", "list", "remove", "info", "help");

    private final GlowFusionPlugin plugin;
    private final PopoutStore store;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public PopoutCommand(GlowFusionPlugin plugin, PopoutStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("glowfusion.popout.admin")) {
            player.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1" -> setPos(player, pos1, "1");
            case "pos2" -> setPos(player, pos2, "2");
            case "saveoff" -> save(player, args, PopoutDefinition.State.OFF);
            case "saveon" -> save(player, args, PopoutDefinition.State.ON);
            case "bind" -> bind(player, args);
            case "unbind" -> unbind(player);
            case "list" -> list(player);
            case "remove" -> remove(player, args);
            case "info" -> info(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void setPos(Player player, Map<UUID, Location> map, String label) {
        Block target = player.getTargetBlockExact(100);
        if (target == null) {
            player.sendMessage("§cLook at a block within 100 blocks first.");
            return;
        }
        map.put(player.getUniqueId(), target.getLocation());
        player.sendMessage("§aPosition " + label + " set to §f" + describe(target.getLocation()));
    }

    private void save(Player player, String[] args, PopoutDefinition.State state) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /popout " + (state == PopoutDefinition.State.OFF ? "saveoff" : "saveon") + " <name>");
            return;
        }
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        if (p1 == null || p2 == null) {
            player.sendMessage("§cSet both corners first with /popout pos1 and /popout pos2.");
            return;
        }
        if (!p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§cPosition 1 and 2 are in different worlds.");
            return;
        }

        Region region = Region.of(p1, p2);
        long max = plugin.getConfig().getLong("popouts.max-blocks", 20000);
        if (region.blockCount() > max) {
            player.sendMessage("§cThat region is " + region.describeSize() + " - larger than the "
                    + max + "-block limit (popouts.max-blocks in config.yml). Pick a smaller area.");
            return;
        }

        String name = args[1];
        PopoutDefinition existing = store.getDefinition(name);
        if (existing != null) {
            Region other = state == PopoutDefinition.State.OFF ? existing.hasOn() ? existing.getRegion() : null
                    : existing.hasOff() ? existing.getRegion() : null;
            if (other != null && !other.sameBoundsAs(region)) {
                player.sendMessage("§cThis region doesn't match the corners you used for '" + name
                        + "''s other snapshot. Reselect the exact same /popout pos1 and pos2 and try again.");
                return;
            }
        }

        List<String> entries = PopoutDefinition.capture(region);
        PopoutDefinition def = existing != null ? existing : new PopoutDefinition(name, region, null, null);
        def.setRegion(region);
        if (state == PopoutDefinition.State.OFF) {
            def.setOffEntries(entries);
        } else {
            def.setOnEntries(entries);
        }
        store.saveDefinition(def);

        player.sendMessage("§aCaptured the §f" + (state == PopoutDefinition.State.OFF ? "OFF (stowed)" : "ON (deployed)")
                + "§a state of '" + name + "' - " + region.describeSize() + ".");
        if (def.isComplete()) {
            player.sendMessage("§aBoth states captured! Look at a lever and run §f/popout bind " + name);
        } else {
            player.sendMessage("§7Now build the " + (state == PopoutDefinition.State.OFF ? "deployed" : "stowed")
                    + " look in the same spot and run §f/popout save" + (state == PopoutDefinition.State.OFF ? "on" : "off") + " " + name);
        }
    }

    private void bind(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /popout bind <name>");
            return;
        }
        Block target = player.getTargetBlockExact(100);
        if (target == null || target.getType() != Material.LEVER) {
            player.sendMessage("§cLook directly at a lever first.");
            return;
        }
        String name = args[1];
        PopoutDefinition def = store.getDefinition(name);
        if (def == null) {
            player.sendMessage("§cNo popout named '" + name + "' exists. See /popout list.");
            return;
        }
        if (!def.isComplete()) {
            player.sendMessage("§cCapture both /popout saveoff " + name + " and /popout saveon " + name + " before binding.");
            return;
        }
        store.bind(target.getLocation(), name);
        player.sendMessage("§aThat lever is now bound to '" + name + "'. Flip it on to deploy, off to stow.");
    }

    private void unbind(Player player) {
        Block target = player.getTargetBlockExact(100);
        if (target == null || target.getType() != Material.LEVER) {
            player.sendMessage("§cLook directly at a lever first.");
            return;
        }
        if (store.unbind(target.getLocation())) {
            player.sendMessage("§aThat lever is no longer bound to a popout.");
        } else {
            player.sendMessage("§7That lever wasn't bound to anything.");
        }
    }

    private void list(Player player) {
        List<String> names = store.listNames();
        if (names.isEmpty()) {
            player.sendMessage("§7No popouts saved yet. Start with /popout pos1 and /popout pos2.");
            return;
        }
        player.sendMessage("§aSaved popouts:");
        for (String name : names) {
            PopoutDefinition def = store.getDefinition(name);
            String status = (def.hasOff() ? "§aOFF✓" : "§7OFF✗") + " " + (def.hasOn() ? "§aON✓" : "§7ON✗");
            player.sendMessage("§f - " + name + " §7(" + status + "§7)");
        }
    }

    private void remove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /popout remove <name>");
            return;
        }
        if (store.removeDefinition(args[1])) {
            player.sendMessage("§aRemoved popout '" + args[1] + "' and unbound any levers using it.");
        } else {
            player.sendMessage("§cNo popout named '" + args[1] + "' exists.");
        }
    }

    private void info(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        player.sendMessage("§7Position 1: §f" + (p1 == null ? "not set" : describe(p1)));
        player.sendMessage("§7Position 2: §f" + (p2 == null ? "not set" : describe(p2)));
        if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§7Region size: §f" + Region.of(p1, p2).describeSize());
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== GlowFusion Popouts ===");
        player.sendMessage("§f/popout pos1§7 and §f/popout pos2§7 - select the two corners of the space");
        player.sendMessage("§f/popout saveoff <name>§7 - capture the stowed look of that space");
        player.sendMessage("§f/popout saveon <name>§7 - capture the deployed look of that space");
        player.sendMessage("§f/popout bind <name>§7 - look at a lever and bind it (on = deployed, off = stowed)");
        player.sendMessage("§f/popout unbind§7 - look at a lever and unbind it");
        player.sendMessage("§f/popout list§7, §f/popout remove <name>§7, §f/popout info");
    }

    private static String describe(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("bind") || sub.equals("remove") || sub.equals("saveoff") || sub.equals("saveon")) {
                List<String> out = new ArrayList<>();
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String name : store.listNames()) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(name);
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
