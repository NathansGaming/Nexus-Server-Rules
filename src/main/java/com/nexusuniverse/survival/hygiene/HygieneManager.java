package com.nexusuniverse.survival.hygiene;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import com.nexusuniverse.survival.disease.DiseaseManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.Random;

public class HygieneManager {

    private static final double MAX_DIRTINESS = 100.0;
    private static final int DIRTY_TICK_INTERVAL_SECONDS = 20; // +1 dirtiness per 20s worn

    private final PlayerDataManager playerData;
    private final DiseaseManager diseaseManager;
    private final NexusSurvivalConfig config;
    private final Random random = new Random();

    public HygieneManager(PlayerDataManager playerData, DiseaseManager diseaseManager, NexusSurvivalConfig config) {
        this.playerData = playerData;
        this.diseaseManager = diseaseManager;
        this.config = config;
    }

    public void wash(Player player) {
        SurvivalPlayerData d = playerData.get(player);
        d.dirtiness = 0;
        player.sendMessage("§bYou scrub your armor clean.");
    }

    /** Called every second from the central tick loop. */
    public void tick(Player player) {
        SurvivalPlayerData d = playerData.get(player);
        if (!isWearingAnyArmor(player)) return;

        d.hygieneTickCounter++;
        if (d.hygieneTickCounter < DIRTY_TICK_INTERVAL_SECONDS) return;
        d.hygieneTickCounter = 0;

        d.dirtiness = Math.min(MAX_DIRTINESS, d.dirtiness + 1);

        if (d.dirtiness >= MAX_DIRTINESS && d.infection == null) {
            double chance = config.getDouble("disease.hygiene-infection-chance", 0.05) * config.diseaseChanceMultiplier();
            if (random.nextDouble() < chance) {
                diseaseManager.infectRandom(player);
            }
        }
    }

    private boolean isWearingAnyArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        return inv.getHelmet() != null || inv.getChestplate() != null
                || inv.getLeggings() != null || inv.getBoots() != null;
    }
}
