package com.nexusuniverse.kairos;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FightManager {

    private final NexusKairosPlugin plugin;
    private KairosBoss activeBoss;

    private static final int CRYSTAL_REQUIRED = 10;
    private static final double ARENA_TRIGGER_RADIUS = 15.0;

    public FightManager(NexusKairosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFightActive() {
        return activeBoss != null && !activeBoss.isDead();
    }

    /**
     * Attempts to start the fight. Returns a human-readable reason if it
     * fails, or null on success. Full ritual: location + 10 real
     * Fractured Crystals + a sacrifice (pig or enderman kills in the
     * End). No clearance-level gate, per the user's call.
     */
    public String attemptSummon(Player player) {
        if (isFightActive()) {
            return "Kairos is already active.";
        }

        Location arena = plugin.getArenaLocation();
        if (player.getLocation().distance(arena) > ARENA_TRIGGER_RADIUS) {
            return "You must be at the arena to summon Kairos.";
        }

        if (!hasEnoughCrystals(player)) {
            return "You need " + CRYSTAL_REQUIRED + " Fractured Crystals to summon Kairos. (Have: "
                    + countCrystals(player) + ")";
        }

        int sacrificeRequired = plugin.getConfig().getInt("fight.sacrifice-required", 5);
        int sacrificeCount = plugin.getSacrificeTracker().getCount(player);
        if (sacrificeCount < sacrificeRequired) {
            return "The ritual demands a sacrifice. Kill a pig or an enderman in the End. ("
                    + sacrificeCount + "/" + sacrificeRequired + ")";
        }

        consumeCrystals(player);
        plugin.getSacrificeTracker().reset(player);

        boolean trueEnding = plugin.getFightState().isTrueEndingArmed();
        int fightCount = plugin.getFightState().getFightCount() + 1; // this attempt's number

        activeBoss = new KairosBoss(plugin, fightCount, trueEnding);
        activeBoss.spawn(arena);
        return null;
    }

    private boolean hasEnoughCrystals(Player player) {
        return countCrystals(player) >= CRYSTAL_REQUIRED;
    }

    private int countCrystals(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getCrystalItems().isCrystal(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void consumeCrystals(Player player) {
        int remaining = CRYSTAL_REQUIRED;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!plugin.getCrystalItems().isCrystal(item)) continue;

            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
    }

    public void tick() {
        if (activeBoss != null) {
            activeBoss.tick();
            if (activeBoss.isDead()) {
                activeBoss = null;
            }
        }
    }
}
