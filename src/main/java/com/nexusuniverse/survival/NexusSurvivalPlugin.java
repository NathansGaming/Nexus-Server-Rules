package com.nexusuniverse.survival;

import com.nexusuniverse.survival.climate.ClimateManager;
import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.disease.DiseaseItems;
import com.nexusuniverse.survival.disease.DiseaseListener;
import com.nexusuniverse.survival.disease.DiseaseManager;
import com.nexusuniverse.survival.disease.DiseaseSourceListener;
import com.nexusuniverse.survival.hygiene.HygieneListener;
import com.nexusuniverse.survival.hygiene.HygieneManager;
import com.nexusuniverse.survival.mobs.BleedingTracker;
import com.nexusuniverse.survival.mobs.ContagiousMobManager;
import com.nexusuniverse.survival.mobs.CrawlerManager;
import com.nexusuniverse.survival.mobs.FeralZombieManager;
import com.nexusuniverse.survival.mobs.LimbShootingListener;
import com.nexusuniverse.survival.mobs.TntZombieManager;
import com.nexusuniverse.survival.radiation.RadiationItems;
import com.nexusuniverse.survival.radiation.RadiationListener;
import com.nexusuniverse.survival.radiation.RadiationManager;
import com.nexusuniverse.survival.seasons.SeasonBridge;
import com.nexusuniverse.survival.spawn.SpawnListener;
import com.nexusuniverse.survival.spawn.SpawnManager;
import com.nexusuniverse.survival.spawn.WorldSpawnCommand;
import com.nexusuniverse.survival.thirst.ThirstItems;
import com.nexusuniverse.survival.thirst.ThirstListener;
import com.nexusuniverse.survival.thirst.ThirstManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class NexusSurvivalPlugin extends JavaPlugin {

    private NexusSurvivalConfig config;
    private PlayerDataManager playerDataManager;

    private ThirstItems thirstItems;
    private ThirstManager thirstManager;

    private RadiationItems radiationItems;
    private RadiationManager radiationManager;

    private HygieneManager hygieneManager;

    private DiseaseItems diseaseItems;
    private DiseaseManager diseaseManager;

    private FeralZombieManager feralZombieManager;
    private TntZombieManager tntZombieManager;
    private CrawlerManager crawlerManager;
    private BleedingTracker bleedingTracker;
    private ContagiousMobManager contagiousMobManager;
    private ClimateManager climateManager;
    private SpawnManager spawnManager;

    @Override
    public void onEnable() {
        this.config = new NexusSurvivalConfig(this);
        this.playerDataManager = new PlayerDataManager();
        SeasonBridge seasonBridge = new SeasonBridge();

        this.thirstItems = new ThirstItems(this);
        this.thirstManager = new ThirstManager(playerDataManager);
        com.nexusuniverse.survival.thirst.WaterPurificationRecipe.register(this, thirstItems);

        this.radiationItems = new RadiationItems(this);
        this.radiationManager = new RadiationManager(playerDataManager, radiationItems);

        this.diseaseItems = new DiseaseItems(this);
        this.diseaseManager = new DiseaseManager(playerDataManager, config, seasonBridge);
        com.nexusuniverse.survival.disease.DiseaseCureRecipes.registerAll(this, diseaseItems, thirstItems);

        this.hygieneManager = new HygieneManager(playerDataManager, diseaseManager, config);

        this.feralZombieManager = new FeralZombieManager(this, config, seasonBridge);
        this.tntZombieManager = new TntZombieManager(this, config, seasonBridge);
        this.crawlerManager = new CrawlerManager(this);
        this.bleedingTracker = new BleedingTracker();
        this.contagiousMobManager = new ContagiousMobManager(this, diseaseManager, config, seasonBridge);
        this.climateManager = new ClimateManager(playerDataManager, config, seasonBridge);

        this.spawnManager = new SpawnManager(this, config);
        spawnManager.applyWorldSpawn();

        getCommand("nexussurvival").setExecutor(new NexusSurvivalCommand(this));
        getCommand("spawn").setExecutor(new WorldSpawnCommand(spawnManager));

        getServer().getPluginManager().registerEvents(new ThirstListener(this), this);
        getServer().getPluginManager().registerEvents(new RadiationListener(this), this);
        getServer().getPluginManager().registerEvents(new HygieneListener(this), this);
        getServer().getPluginManager().registerEvents(new DiseaseListener(this), this);
        getServer().getPluginManager().registerEvents(new DiseaseSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(spawnManager), this);
        getServer().getPluginManager().registerEvents(feralZombieManager, this);
        getServer().getPluginManager().registerEvents(tntZombieManager, this);
        getServer().getPluginManager().registerEvents(new LimbShootingListener(bleedingTracker, crawlerManager), this);
        getServer().getPluginManager().registerEvents(contagiousMobManager, this);
        getServer().getPluginManager().registerEvents(crawlerManager, this);

        // Catches any crawlers left over from a previous session in chunks
        // that are already loaded at startup (onChunkLoad only covers
        // chunks that load AFTER this point).
        crawlerManager.scanLoadedChunks(Bukkit.getWorlds());

        // central tick loop: once per second (20 ticks) for all systems.
        // Each subsystem is individually isolated -- an exception in one
        // (say, a bug in the newer mob-tracking code) gets logged but
        // can't abort the whole pass and starve everything after it,
        // including the thirst bar update for players later in the loop.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                safeTick("thirst", () -> thirstManager.tick(player));
                safeTick("radiation", () -> radiationManager.tick(player));
                safeTick("hygiene", () -> hygieneManager.tick(player));
                safeTick("disease", () -> diseaseManager.tick(player));
                safeTick("climate", () -> climateManager.tick(player));
            }
            safeTick("disease-global", () -> diseaseManager.tickGlobal(Bukkit.getOnlinePlayers()));
            safeTick("tnt-zombie", () -> tntZombieManager.tickAll(Bukkit.getOnlinePlayers()));
            safeTick("crawler", () -> crawlerManager.tickAll(getServer()));
            safeTick("bleeding", () -> bleedingTracker.tick(getServer()));
            safeTick("contagious-mob", () -> contagiousMobManager.tickAll(Bukkit.getOnlinePlayers()));
        }, 20L, 20L);

        getLogger().info("NexusSurvival enabled -- thirst, radiation zones, hygiene, disease, and hostile mob overhauls are live.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.clearAll();
        }
    }

    /** Runs one subsystem's tick in isolation -- a failure here is logged, not allowed to abort everything after it. */
    private void safeTick(String subsystem, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "NexusSurvival: error ticking '" + subsystem + "' -- this pass skipped it, will retry next tick.", t);
        }
    }

    public NexusSurvivalConfig getNexusSurvivalConfig() {
        return config;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ThirstItems getThirstItems() {
        return thirstItems;
    }

    public ThirstManager getThirstManager() {
        return thirstManager;
    }

    public RadiationItems getRadiationItems() {
        return radiationItems;
    }

    public RadiationManager getRadiationManager() {
        return radiationManager;
    }

    public HygieneManager getHygieneManager() {
        return hygieneManager;
    }

    public DiseaseItems getDiseaseItems() {
        return diseaseItems;
    }

    public DiseaseManager getDiseaseManager() {
        return diseaseManager;
    }

    public FeralZombieManager getFeralZombieManager() {
        return feralZombieManager;
    }

    public TntZombieManager getTntZombieManager() {
        return tntZombieManager;
    }

    public ContagiousMobManager getContagiousMobManager() {
        return contagiousMobManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
}
