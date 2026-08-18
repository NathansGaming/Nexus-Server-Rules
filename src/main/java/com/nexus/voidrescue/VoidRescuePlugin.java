package com.nexus.voidrescue;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidRescuePlugin extends JavaPlugin {

    private RescueService rescueService;
    private VoidWatchdogTask watchdogTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.rescueService = new RescueService(this, getConfig());

        getServer().getPluginManager().registerEvents(new RescueListener(rescueService), this);

        PluginCommand command = getCommand("voidrescue");
        if (command != null) {
            VoidRescueCommand executor = new VoidRescueCommand(this, rescueService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("[VoidRescue] 'voidrescue' command missing from plugin.yml - check the jar wasn't repackaged incorrectly.");
        }

        int scanIntervalTicks = getConfig().getInt("scanIntervalTicks", 10);
        int stuckDeadTicks = getConfig().getInt("stuckDeadTicks", 40);
        this.watchdogTask = new VoidWatchdogTask(rescueService, scanIntervalTicks, stuckDeadTicks);
        watchdogTask.runTaskTimer(this, 20L, Math.max(1L, scanIntervalTicks));

        getLogger().info("[VoidRescue] Enabled. Scanning every " + scanIntervalTicks
                + " ticks; use /voidrescue <player|all|reload> to force a rescue right now.");
    }

    @Override
    public void onDisable() {
        if (watchdogTask != null) {
            try {
                watchdogTask.cancel();
            } catch (IllegalStateException ignored) {
                // Already cancelled (e.g. scheduler already shut down) - fine to ignore.
            }
        }
        getLogger().info("[VoidRescue] Disabled.");
    }
}
