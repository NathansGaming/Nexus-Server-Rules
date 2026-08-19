package com.nexusuniverse.kairos;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusKairosPlugin extends JavaPlugin {

    private FightState fightState;
    private FightManager fightManager;
    private TierCalculator tierCalculator;
    private DialogueEngine dialogueEngine;
    private CrystalItems crystalItems;
    private SacrificeTracker sacrificeTracker;
    private ArenaHazards arenaHazards;
    private Location arenaLocation;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.fightState = new FightState(this);
        this.fightManager = new FightManager(this);
        this.dialogueEngine = new DialogueEngine(this);
        this.crystalItems = new CrystalItems(this);
        this.sacrificeTracker = new SacrificeTracker(this);
        this.arenaHazards = new ArenaHazards(this);

        this.tierCalculator = new TierCalculator(
                getConfig().getDouble("fight.base-health", 200.0),
                getConfig().getDouble("fight.base-damage", 6.0),
                getConfig().getDouble("fight.health-growth-per-fight", 4.0),
                getConfig().getDouble("fight.damage-growth-per-fight", 0.15)
        );

        loadArenaLocation();

        getCommand("kairos").setExecutor(new KairosCommand(this));
        getServer().getPluginManager().registerEvents(sacrificeTracker, this);

        getServer().getScheduler().runTaskTimer(this, () -> fightManager.tick(), 1L, 1L);

        getLogger().info("NexusKairos enabled -- fight count is currently " + fightState.getFightCount() + ".");
    }

    private void loadArenaLocation() {
        String worldName = getConfig().getString("arena.world", "world_the_end");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Arena world '" + worldName + "' not found/loaded yet -- "
                    + "arena location will resolve once it is.");
        }
        double x = getConfig().getDouble("arena.x", 1961);
        double y = getConfig().getDouble("arena.y", 65);
        double z = getConfig().getDouble("arena.z", -7);
        this.arenaLocation = new Location(world, x, y, z);
    }

    public Location getArenaLocation() {
        if (arenaLocation.getWorld() == null) {
            // retry resolving the world in case it loaded after startup
            loadArenaLocation();
        }
        return arenaLocation;
    }

    public FightState getFightState() {
        return fightState;
    }

    public FightManager getFightManager() {
        return fightManager;
    }

    public TierCalculator getTierCalculator() {
        return tierCalculator;
    }

    public DialogueEngine getDialogueEngine() {
        return dialogueEngine;
    }

    public CrystalItems getCrystalItems() {
        return crystalItems;
    }

    public SacrificeTracker getSacrificeTracker() {
        return sacrificeTracker;
    }

    public ArenaHazards getArenaHazards() {
        return arenaHazards;
    }
}
