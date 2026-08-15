package com.nexusuniverse.survival.radiation;

import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RadiationManager {

    private static final double MAX_OXYGEN = 20.0;
    private static final int DRAIN_INTERVAL_SECONDS = 2;
    private static final int REGEN_INTERVAL_SECONDS = 2;
    private static final int SUFFOCATE_DAMAGE_INTERVAL_SECONDS = 1;

    private final PlayerDataManager playerData;
    private final RadiationItems radiationItems;
    private final List<RadiationZone> zones = new ArrayList<>();

    // wand: first click per player, waiting on the second
    private final Map<UUID, Location> pendingFirstPoint = new HashMap<>();
    private final Map<UUID, Location> pendingSecondPoint = new HashMap<>();

    public RadiationManager(PlayerDataManager playerData, RadiationItems radiationItems) {
        this.playerData = playerData;
        this.radiationItems = radiationItems;
    }

    public void setFirstPoint(Player player, Location loc) {
        pendingFirstPoint.put(player.getUniqueId(), loc);
        player.sendMessage("§dCorner 1 set: §f" + fmt(loc));
    }

    public void setSecondPoint(Player player, Location loc) {
        pendingSecondPoint.put(player.getUniqueId(), loc);
        player.sendMessage("§dCorner 2 set: §f" + fmt(loc));
    }

    public boolean createZone(Player player, String name) {
        Location a = pendingFirstPoint.get(player.getUniqueId());
        Location b = pendingSecondPoint.get(player.getUniqueId());
        if (a == null || b == null) {
            player.sendMessage("§cSet both corners with the wand first (left-click, then right-click).");
            return false;
        }
        if (findZone(name) != null) {
            player.sendMessage("§cA zone named \"" + name + "\" already exists.");
            return false;
        }
        zones.add(new RadiationZone(name, a, b));
        player.sendMessage("§aRadiation zone \"" + name + "\" created.");
        return true;
    }

    public boolean removeZone(String name) {
        return zones.removeIf(z -> z.getName().equalsIgnoreCase(name));
    }

    public List<String> listZoneNames() {
        return zones.stream().map(RadiationZone::getName).toList();
    }

    private RadiationZone findZone(String name) {
        return zones.stream().filter(z -> z.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private boolean isInAnyZone(Location loc) {
        for (RadiationZone zone : zones) {
            if (zone.contains(loc)) return true;
        }
        return false;
    }

    /** Called every second from the central tick loop. */
    public void tick(Player player) {
        SurvivalPlayerData d = playerData.get(player);
        boolean inZone = isInAnyZone(player.getLocation());
        boolean protectedByMask = radiationItems.isWearingGasMask(player);

        if (inZone && !protectedByMask) {
            d.radTickCounter++;
            if (d.radTickCounter >= DRAIN_INTERVAL_SECONDS) {
                d.radTickCounter = 0;
                d.radOxygen = Math.max(0, d.radOxygen - 1);
            }
            if (d.radOxygen <= 0 && d.radTickCounter % SUFFOCATE_DAMAGE_INTERVAL_SECONDS == 0) {
                player.damage(1.0);
            }
            d.radiationBar.setVisible(true);
        } else {
            d.radTickCounter++;
            if (d.radTickCounter >= REGEN_INTERVAL_SECONDS) {
                d.radTickCounter = 0;
                d.radOxygen = Math.min(MAX_OXYGEN, d.radOxygen + 1);
            }
            d.radiationBar.setVisible(d.radOxygen < MAX_OXYGEN);
        }

        updateBar(d);
    }

    private void updateBar(SurvivalPlayerData d) {
        double progress = d.radOxygen / MAX_OXYGEN;
        d.radiationBar.setProgress(Math.max(0, Math.min(1, progress)));
        d.radiationBar.setTitle("§aRad-O2: " + (int) d.radOxygen + "/" + (int) MAX_OXYGEN);
        d.radiationBar.setColor(d.radOxygen <= 6
                ? org.bukkit.boss.BarColor.RED
                : org.bukkit.boss.BarColor.GREEN);
    }

    private String fmt(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
