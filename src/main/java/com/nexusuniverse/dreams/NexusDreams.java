package com.nexusuniverse.dreams;

import com.nexusuniverse.dreams.commands.NexusDreamsCommand;
import com.nexusuniverse.dreams.config.NexusDreamsConfig;
import com.nexusuniverse.dreams.dream.DreamAssessor;
import com.nexusuniverse.dreams.dream.DreamPresenter;
import com.nexusuniverse.dreams.integration.SurvivalBridge;
import com.nexusuniverse.dreams.listeners.SleepListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusDreams extends JavaPlugin {

    private NexusDreamsConfig config;
    private SurvivalBridge survivalBridge;
    private SleepListener sleepListener;

    @Override
    public void onEnable() {
        this.config = new NexusDreamsConfig(this);
        this.survivalBridge = new SurvivalBridge(this);

        DreamAssessor assessor = new DreamAssessor();
        DreamPresenter presenter = new DreamPresenter(getLogger());
        this.sleepListener = new SleepListener(config, survivalBridge, assessor, presenter);

        getServer().getPluginManager().registerEvents(sleepListener, this);
        getCommand("nexusdreams").setExecutor(new NexusDreamsCommand(sleepListener, config));

        getLogger().info("NexusDreams enabled - sleeping through the night now dreams.");
    }

    public NexusDreamsConfig getNexusDreamsConfig() {
        return config;
    }

    public SurvivalBridge getSurvivalBridge() {
        return survivalBridge;
    }
}
