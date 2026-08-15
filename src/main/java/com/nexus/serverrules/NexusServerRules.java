package com.nexus.serverrules;

import com.nexus.serverrules.commands.AppealCommand;
import com.nexus.serverrules.commands.BanCommand;
import com.nexus.serverrules.commands.NexusRulesCommand;
import com.nexus.serverrules.detection.PatternRepository;
import com.nexus.serverrules.detection.ViolationDetector;
import com.nexus.serverrules.explosives.ExplosionGuard;
import com.nexus.serverrules.gui.GuiListener;
import com.nexus.serverrules.listeners.ChatListener;
import com.nexus.serverrules.listeners.GriefListener;
import com.nexus.serverrules.listeners.JoinListener;
import com.nexus.serverrules.punishment.PunishmentManager;
import com.nexus.serverrules.storage.PlayerNameCache;
import com.nexus.serverrules.storage.RestrictionStore;
import com.nexus.serverrules.storage.ReviewQueue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class NexusServerRules extends JavaPlugin {

    private PatternRepository patternRepository;
    private ViolationDetector violationDetector;
    private RestrictionStore restrictionStore;
    private PunishmentManager punishmentManager;
    private ReviewQueue reviewQueue;
    private PlayerNameCache playerNameCache;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();

        // Detection side: loads patterns.yml (creating the bundled
        // default on first run) and builds the fuzzy-matching engine.
        patternRepository = new PatternRepository(this);
        patternRepository.load();
        violationDetector = new ViolationDetector(patternRepository);

        // Punishment side: restrictions.yml is the source of truth for
        // who is currently locked down, loaded before anything else
        // touches it so a restart never silently forgets a restriction.
        restrictionStore = new RestrictionStore(this);
        restrictionStore.load();
        punishmentManager = new PunishmentManager(this, restrictionStore);

        // Local name->UUID cache so staff commands never risk a
        // blocking Mojang lookup mid-command. Loaded before the join
        // listener registers so it's ready for the very first join.
        playerNameCache = new PlayerNameCache(this);
        playerNameCache.load();

        // Review side: independent on-disk log plus in-memory queue for
        // the staff /nexusrules queue command.
        reviewQueue = new ReviewQueue(this);

        boolean strictMode = getConfig().getBoolean("chat-detection.strict-mode", true);
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, violationDetector, punishmentManager, reviewQueue, strictMode), this);
        getServer().getPluginManager().registerEvents(
                new JoinListener(punishmentManager, restrictionStore, playerNameCache), this);
        getServer().getPluginManager().registerEvents(
                new GuiListener(punishmentManager, restrictionStore, reviewQueue), this);

        if (getConfig().getBoolean("grief-detection.enabled", true)) {
            int windowSeconds = getConfig().getInt("grief-detection.window-seconds", 5);
            int blockThreshold = getConfig().getInt("grief-detection.block-threshold", 8);
            int registryCapacity = getConfig().getInt("grief-detection.registry-capacity", 50_000);
            getServer().getPluginManager().registerEvents(
                    new GriefListener(this, punishmentManager, reviewQueue, windowSeconds, blockThreshold, registryCapacity), this);
        } else {
            getLogger().info("[NexusServerRules] Griefing-burst detection disabled via config.yml.");
        }

        if (getConfig().getBoolean("explosion-prevention.enabled", true)) {
            Set<String> bannedItems = new HashSet<>();
            for (String raw : getConfig().getStringList("explosion-prevention.banned-items")) {
                bannedItems.add(raw.toUpperCase(Locale.ROOT));
            }
            getServer().getPluginManager().registerEvents(new ExplosionGuard(bannedItems), this);
            getLogger().info("[NexusServerRules] Explosion prevention active for: " + bannedItems);
        } else {
            getLogger().info("[NexusServerRules] Explosion prevention disabled via config.yml.");
        }

        var nexusRulesCommand = new NexusRulesCommand(this, patternRepository, punishmentManager, restrictionStore, reviewQueue, playerNameCache);
        getCommand("nexusrules").setExecutor(nexusRulesCommand);
        getCommand("nexusrules").setTabCompleter(nexusRulesCommand);

        var appealCommand = new AppealCommand(this);
        getCommand("appeal").setExecutor(appealCommand);

        getCommand("ban").setExecutor(new BanCommand(this, playerNameCache));

        getLogger().info("[NexusServerRules] Enabled - " + restrictionStore.allActive().size()
                + " player(s) currently under an active restriction.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[NexusServerRules] Disabled. Active restrictions remain persisted in restrictions.yml "
                + "and will be re-applied to affected players on their next join.");
    }

    public PatternRepository patternRepository() { return patternRepository; }
    public ViolationDetector violationDetector() { return violationDetector; }
    public RestrictionStore restrictionStore() { return restrictionStore; }
    public PunishmentManager punishmentManager() { return punishmentManager; }
    public ReviewQueue reviewQueue() { return reviewQueue; }
}
