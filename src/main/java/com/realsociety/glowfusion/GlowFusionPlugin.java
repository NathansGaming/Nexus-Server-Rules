package com.realsociety.glowfusion;

import com.realsociety.glowfusion.buttons.GlowingButtonListener;
import com.realsociety.glowfusion.buttons.LightStore;
import com.realsociety.glowfusion.miniblocks.MiniBlockListener;
import com.realsociety.glowfusion.miniblocks.MiniBlockPalette;
import com.realsociety.glowfusion.popouts.PopoutCommand;
import com.realsociety.glowfusion.popouts.PopoutListener;
import com.realsociety.glowfusion.popouts.PopoutStore;
import com.realsociety.glowfusion.slabs.DualSlabListener;
import com.realsociety.glowfusion.stacks.StackSizeListener;
import com.realsociety.glowfusion.stacks.StackSizeManager;
import com.realsociety.glowfusion.vertical.VerticalSlabListener;
import com.realsociety.glowfusion.vertical.VerticalSlabStore;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class GlowFusionPlugin extends JavaPlugin {

    private LightStore lightStore;
    private GlowingButtonListener buttonListener;
    private PopoutStore popoutStore;
    private VerticalSlabListener verticalSlabListener;
    private MiniBlockListener miniBlockListener;
    private StackSizeManager stackSizeManager;
    private StackSizeListener stackSizeListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.lightStore = new LightStore(this);
        this.lightStore.load();

        this.popoutStore = new PopoutStore(this);
        this.popoutStore.load();

        VerticalSlabStore verticalSlabStore = new VerticalSlabStore(this);
        verticalSlabStore.load();

        DualSlabListener slabListener = new DualSlabListener(this);
        getServer().getPluginManager().registerEvents(slabListener, this);

        this.verticalSlabListener = new VerticalSlabListener(this, verticalSlabStore);
        getServer().getPluginManager().registerEvents(verticalSlabListener, this);

        MiniBlockPalette miniBlockPalette = new MiniBlockPalette(this);
        this.miniBlockListener = new MiniBlockListener(this, miniBlockPalette);
        getServer().getPluginManager().registerEvents(miniBlockListener, this);

        this.buttonListener = new GlowingButtonListener(this, lightStore);
        getServer().getPluginManager().registerEvents(buttonListener, this);
        buttonListener.startVerificationTask();

        getServer().getPluginManager().registerEvents(new PopoutListener(this, popoutStore), this);
        PopoutCommand popoutCommand = new PopoutCommand(this, popoutStore);
        PluginCommand popoutPluginCommand = getCommand("popout");
        if (popoutPluginCommand != null) {
            popoutPluginCommand.setExecutor(popoutCommand);
            popoutPluginCommand.setTabCompleter(popoutCommand);
        }

        if (featureEnabled("bigger-stacks.enabled")) {
            this.stackSizeManager = new StackSizeManager(this);
            this.stackSizeListener = new StackSizeListener(this, stackSizeManager);
            getServer().getPluginManager().registerEvents(stackSizeListener, this);
            stackSizeListener.startSweepTask();
        }

        getLogger().info("GlowFusion enabled - dual-color slab fusing, glowing buttons, lever popouts, "
                + "vertical slabs, mini-block mosaics, and bigger item stacks are live.");
    }

    @Override
    public void onDisable() {
        if (buttonListener != null) {
            buttonListener.stopVerificationTask();
        }
        if (stackSizeListener != null) {
            stackSizeListener.stopSweepTask();
        }
        if (lightStore != null) {
            lightStore.save();
        }
        getLogger().info("GlowFusion disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Unlike reload/unstick, "mini" is meant for any regular player to toggle
        // their own paint mode - so the admin permission is checked per-subcommand
        // here rather than on the command node itself (which would block everyone
        // without glowfusion.admin from even running /glowfusion mini).
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("glowfusion.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            reloadConfig();
            if (buttonListener != null) {
                buttonListener.reload();
            }
            if (stackSizeManager != null) {
                stackSizeManager.reload();
            }
            sender.sendMessage("§a[GlowFusion] Configuration reloaded.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("unstick")) {
            if (!sender.hasPermission("glowfusion.admin")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            return handleUnstick(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("mini")) {
            return handleMiniToggle(sender);
        }
        sender.sendMessage("§7Usage: /" + label + " reload  |  /" + label + " unstick  |  /" + label + " mini");
        return true;
    }

    /**
     * Mini-block mosaics are opt-in per player and off by default, so a
     * player's normal right-click block placement with wool, stone, planks,
     * etc. is never hijacked unless they've explicitly turned this on for
     * their own session. Running this again turns it back off.
     */
    private boolean handleMiniToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can toggle mini-block paint mode.");
            return true;
        }
        if (miniBlockListener == null) {
            sender.sendMessage("§cMini-block mosaics aren't available right now.");
            return true;
        }
        if (requiresPermission("mini-blocks.require-permission") && !player.hasPermission("glowfusion.miniblocks")) {
            player.sendMessage("§cYou don't have permission to use mini-block mosaics.");
            return true;
        }
        boolean nowEnabled = miniBlockListener.togglePaintMode(player);
        if (nowEnabled) {
            player.sendMessage("§a[GlowFusion] Mini-block paint mode is now ON. Right-click a plain block's "
                    + "face with an eligible material to paint a tile there, or sneak + right-click with an "
                    + "empty hand to erase one. Run /glowfusion mini again to turn this off.");
        } else {
            player.sendMessage("§7[GlowFusion] Mini-block paint mode is now OFF - blocks place normally again.");
        }
        return true;
    }

    /**
     * Recovery command for a block that's stuck "glitched" - can't be
     * broken or built over - because this plugin's own tracking (a fused
     * display, a standing-slab entry, etc.) got out of sync with reality.
     * That desync is usually an unclean shutdown: our YAML stores save
     * immediately on every change, but the world's own entities/blocks only
     * save on its normal autosave schedule, so a crash between the two can
     * leave the plugin believing a spot is occupied when nothing is really
     * there anymore (or vice versa). Have the player look directly at the
     * glitched block and run this to force it back to a clean state.
     */
    private boolean handleUnstick(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cStand where you can see the glitched block, look straight at it, "
                    + "and run this command as a player.");
            return true;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage("§cLook directly at the glitched block (within 8 blocks) and try again.");
            return true;
        }
        boolean clearedVertical = verticalSlabListener != null
                && verticalSlabListener.forceClear(target.getLocation());
        boolean clearedFused = DualSlabListener.forceClearFusedDisplay(target.getLocation());
        boolean clearedMini = miniBlockListener != null
                && miniBlockListener.forceClearTiles(target.getLocation());
        if (clearedVertical || clearedFused || clearedMini) {
            sender.sendMessage("§a[GlowFusion] Cleared stuck GlowFusion data at that block. "
                    + "Try breaking or placing there again.");
        } else {
            sender.sendMessage("§7[GlowFusion] Nothing GlowFusion-related was found stuck at that block.");
        }
        return true;
    }

    public static GlowFusionPlugin get() {
        return JavaPlugin.getPlugin(GlowFusionPlugin.class);
    }

    public boolean featureEnabled(String path) {
        return getConfig().getBoolean(path, true);
    }

    public boolean requiresPermission(String path) {
        return getConfig().getBoolean(path, true);
    }

    @Override
    public String toString() {
        return Objects.requireNonNullElse(getDescription().getFullName(), "GlowFusion");
    }
}
