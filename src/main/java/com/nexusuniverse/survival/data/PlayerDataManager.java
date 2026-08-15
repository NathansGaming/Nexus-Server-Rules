package com.nexusuniverse.survival.data;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the UUID -> SurvivalPlayerData map. All four managers (thirst,
 * radiation, hygiene, disease) read/write through this instead of keeping
 * their own maps, so join/quit cleanup happens in exactly one place.
 */
public class PlayerDataManager {

    private final Map<UUID, SurvivalPlayerData> data = new ConcurrentHashMap<>();

    public SurvivalPlayerData get(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar thirstBar = Bukkit.createBossBar("§bThirst: 180/180", BarColor.BLUE, BarStyle.SOLID);
            thirstBar.setProgress(1.0);
            thirstBar.addPlayer(player);

            BossBar radiationBar = Bukkit.createBossBar("§aRad-O2: 20/20", BarColor.GREEN, BarStyle.SOLID);
            radiationBar.setProgress(1.0);
            radiationBar.setVisible(false); // only shown while inside a radiation zone

            BossBar infectionBar = Bukkit.createBossBar("§cInfected", BarColor.PURPLE, BarStyle.SEGMENTED_6);
            infectionBar.setProgress(1.0);
            infectionBar.addPlayer(player);
            infectionBar.setVisible(false); // only shown while sick

            return new SurvivalPlayerData(thirstBar, radiationBar, infectionBar);
        });
    }

    public void remove(Player player) {
        SurvivalPlayerData removed = data.remove(player.getUniqueId());
        if (removed != null) {
            removed.thirstBar.removeAll();
            removed.radiationBar.removeAll();
            removed.infectionBar.removeAll();
        }
    }

    public void clearAll() {
        for (SurvivalPlayerData d : data.values()) {
            d.thirstBar.removeAll();
            d.radiationBar.removeAll();
            d.infectionBar.removeAll();
        }
        data.clear();
    }
}
