package com.nexusuniverse.morality;

import com.nexusuniverse.morality.api.NexusMoralityApi;
import com.nexusuniverse.morality.commands.NexusMoralityCommand;
import com.nexusuniverse.morality.config.NexusMoralityConfig;
import com.nexusuniverse.morality.encounter.EncounterListener;
import com.nexusuniverse.morality.encounter.EncounterManager;
import com.nexusuniverse.morality.karma.KarmaStore;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusMorality extends JavaPlugin {

    private NexusMoralityConfig config;
    private KarmaStore karmaStore;
    private EncounterManager encounterManager;
    private NexusMoralityApi api;

    @Override
    public void onEnable() {
        this.config = new NexusMoralityConfig(this);

        this.karmaStore = new KarmaStore(this);
        karmaStore.load();

        this.encounterManager = new EncounterManager(this, config, karmaStore);

        getServer().getPluginManager().registerEvents(new EncounterListener(encounterManager, config), this);
        getCommand("nexusmorality").setExecutor(new NexusMoralityCommand(encounterManager, karmaStore, config));

        // Registered so a future consumer (the planned NexusReputation passport, most
        // immediately) can look this up via Bukkit's ServicesManager + reflection, the exact
        // pattern NexusServerRules already uses for NexusRealms - see api/NexusMoralityApi.java.
        this.api = new NexusMoralityApi(karmaStore);
        getServer().getServicesManager().register(NexusMoralityApi.class, api, this, ServicePriority.Normal);

        // Central tick loop, once per second (20 ticks) - matches the shape NexusSurvival uses:
        // one scheduler task, the subsystem paces its own slower work (spawn rolls) internally
        // via its own counter rather than needing a second scheduler task.
        getServer().getScheduler().runTaskTimer(this, encounterManager::tick, 20L, 20L);

        getLogger().info("NexusMorality enabled - survivor encounters are live.");
    }

    @Override
    public void onDisable() {
        if (encounterManager != null) {
            encounterManager.shutdown();
        }
    }

    public NexusMoralityConfig getNexusMoralityConfig() {
        return config;
    }

    public KarmaStore getKarmaStore() {
        return karmaStore;
    }

    public EncounterManager getEncounterManager() {
        return encounterManager;
    }

    public NexusMoralityApi getApi() {
        return api;
    }
}
