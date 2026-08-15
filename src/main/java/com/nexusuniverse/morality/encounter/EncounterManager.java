package com.nexusuniverse.morality.encounter;

import com.nexusuniverse.morality.config.NexusMoralityConfig;
import com.nexusuniverse.morality.karma.KarmaStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Owns the full lifecycle of survivor encounters: rolling for new spawns,
 * finding a safe location, tracking the help-radius/hold-seconds timer,
 * and resolving each encounter to HELPED, LOOTED, or EXPIRED. Ticked
 * once a second from NexusMorality's central scheduler loop, the same
 * "one tick loop, subsystems pace themselves with an internal counter"
 * shape NexusSurvival uses.
 *
 * Encounters are intentionally NOT persisted across a restart - this is
 * meant to be a short-lived, session-scoped moment, not a state machine
 * staff need to audit later. See shutdown() for why that means active
 * survivor entities get explicitly cleaned up on disable rather than
 * left to become untracked orphans in the world.
 */
public final class EncounterManager {

    private final JavaPlugin plugin;
    private final NexusMoralityConfig config;
    private final KarmaStore karmaStore;
    private final NamespacedKey tagKey;
    private final Random random = new Random();

    private final Map<UUID, SurvivorEncounter> active = new LinkedHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new LinkedHashMap<>();

    private int checkIntervalCounter;

    public EncounterManager(JavaPlugin plugin, NexusMoralityConfig config, KarmaStore karmaStore) {
        this.plugin = plugin;
        this.config = config;
        this.karmaStore = karmaStore;
        this.tagKey = new NamespacedKey(plugin, "survivor_encounter");
    }

    public NamespacedKey tagKey() {
        return tagKey;
    }

    public boolean isTaggedSurvivor(Entity entity) {
        if (entity == null) return false;
        Boolean tag = entity.getPersistentDataContainer().get(tagKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    public SurvivorEncounter get(UUID entityId) {
        return active.get(entityId);
    }

    public List<SurvivorEncounter> activeSnapshot() {
        return new ArrayList<>(active.values());
    }

    /** Called once a second from the plugin's central tick loop. */
    public void tick() {
        if (!config.getBoolean("encounter.enabled", true)) return;

        tickHelpProgress();
        expireOverdue();

        int intervalSeconds = Math.max(1, config.getInt("encounter.check-interval-seconds", 30));
        checkIntervalCounter++;
        if (checkIntervalCounter >= intervalSeconds) {
            checkIntervalCounter = 0;
            rollSpawns();
        }
    }

    private void tickHelpProgress() {
        int radius = config.getInt("encounter.help-radius-blocks", 6);
        int holdTarget = config.getInt("encounter.help-hold-seconds", 5);

        // Snapshot, not a live view - resolveHelped() below removes the resolved entry directly
        // from `active`, and mutating a map while a for-each loop holds its own iterator over
        // that same map throws ConcurrentModificationException the moment someone actually
        // finishes helping. Iterating a copy sidesteps that; the real removal still lands on the
        // live map either way.
        for (SurvivorEncounter enc : new ArrayList<>(active.values())) {
            if (enc.state != EncounterState.HELPING || enc.helperPlayerId == null) continue;

            Entity entity = Bukkit.getEntity(enc.entityId);
            Player helper = Bukkit.getPlayer(enc.helperPlayerId);

            if (entity == null || !entity.isValid() || helper == null || !helper.isOnline()) {
                // The survivor or the helper vanished mid-hold (logout, entity removed some
                // other way) - fall back to PENDING so a different player can still resolve it,
                // rather than leaving it permanently stuck in HELPING with no way to progress.
                enc.state = EncounterState.PENDING;
                enc.helperPlayerId = null;
                enc.holdSeconds = 0;
                continue;
            }

            boolean sameWorld = helper.getWorld().equals(entity.getWorld());
            double distSq = sameWorld ? helper.getLocation().distanceSquared(entity.getLocation()) : Double.MAX_VALUE;

            if (sameWorld && distSq <= (double) radius * radius) {
                enc.warnedAboutDistance = false;
                enc.holdSeconds++;
                if (enc.holdSeconds >= holdTarget) {
                    resolveHelped(enc, entity, helper);
                }
            } else {
                enc.holdSeconds = 0;
                if (!enc.warnedAboutDistance) {
                    enc.warnedAboutDistance = true;
                    helper.sendMessage("§eThe survivor grows wary - you wandered too far. Come back to finish helping them.");
                }
            }
        }
    }

    private void expireOverdue() {
        int despawnSeconds = config.getInt("encounter.despawn-seconds", 300);
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(despawnSeconds));

        for (Iterator<SurvivorEncounter> it = active.values().iterator(); it.hasNext(); ) {
            SurvivorEncounter enc = it.next();
            if (enc.isResolved()) {
                it.remove();
                continue;
            }
            if (enc.spawnedAt.isBefore(cutoff)) {
                enc.state = EncounterState.EXPIRED;
                removeEntity(enc.entityId);
                plugin.getLogger().info("[NexusMorality] Encounter at " + describeLocation(enc.spawnLocation)
                        + " expired unresolved after " + despawnSeconds + "s.");
                it.remove();
            }
        }
    }

    /** Marks a survivor that died some other way (fall damage, a mob, etc.) as a quiet non-event. */
    public void handleUnexpectedDeath(UUID entityId) {
        SurvivorEncounter enc = active.remove(entityId);
        if (enc == null || enc.isResolved()) return;
        enc.state = EncounterState.EXPIRED;
        plugin.getLogger().info("[NexusMorality] Encounter at " + describeLocation(enc.spawnLocation)
                + " ended (survivor died some other way) - no karma change.");
    }

    public void beginHelping(SurvivorEncounter enc, Player helper) {
        enc.state = EncounterState.HELPING;
        enc.helperPlayerId = helper.getUniqueId();
        enc.holdSeconds = 0;
        enc.warnedAboutDistance = false;
        helper.sendMessage("§aYou offer supplies to the survivor. Stay close while they recover...");
    }

    private void resolveHelped(SurvivorEncounter enc, Entity entity, Player helper) {
        enc.state = EncounterState.HELPED;
        int reward = config.getInt("karma.help-reward", 10);
        int newTotal = karmaStore.adjust(helper.getUniqueId(), reward);
        helper.sendMessage("§aThe survivor thanks you and heads off to find safety. §7(+" + reward + " karma, now " + newTotal + ")");

        dropItems(entity.getLocation(), config.getMaterialList("encounter.help-reward-items"));
        entity.remove();
        active.remove(enc.entityId);

        plugin.getLogger().info("[NexusMorality] HELPED - " + helper.getName() + " at " + describeLocation(enc.spawnLocation));
    }

    /** Called by EncounterListener the moment a player lands a hit on a tagged survivor. */
    public void resolveLooted(SurvivorEncounter enc, Entity entity, Player attacker) {
        if (enc.isResolved()) return; // already handled by an earlier hit this same tick/encounter

        enc.state = EncounterState.LOOTED;
        int penalty = config.getInt("karma.loot-penalty", 15);
        int newTotal = karmaStore.adjust(attacker.getUniqueId(), -penalty);
        attacker.sendMessage("§cYou attack the survivor and take what you can. §7(-" + penalty + " karma, now " + newTotal + ")");

        dropItems(entity.getLocation(), config.getMaterialList("encounter.loot-reward-items"));
        entity.remove();
        active.remove(enc.entityId);

        plugin.getLogger().info("[NexusMorality] LOOTED - " + attacker.getName() + " at " + describeLocation(enc.spawnLocation));
    }

    private void dropItems(Location location, List<Material> materials) {
        World world = location.getWorld();
        if (world == null) return;
        for (Material material : materials) {
            world.dropItemNaturally(location, new ItemStack(material, 1));
        }
    }

    private void rollSpawns() {
        if (active.size() >= config.getInt("encounter.max-concurrent", 3)) return;

        double chance = config.getDouble("encounter.spawn-chance", 0.03);
        long cooldownSeconds = config.getInt("encounter.player-cooldown-seconds", 900);
        long nowEpoch = Instant.now().getEpochSecond();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (active.size() >= config.getInt("encounter.max-concurrent", 3)) return;

            Long lastSpawn = playerCooldowns.get(player.getUniqueId());
            if (lastSpawn != null && nowEpoch - lastSpawn < cooldownSeconds) continue;

            if (random.nextDouble() >= chance) continue;

            Location spot = findSafeLocation(player);
            if (spot == null) continue; // couldn't find anywhere safe this round, try again next interval

            spawnEncounter(spot);
            playerCooldowns.put(player.getUniqueId(), nowEpoch);
        }
    }

    /** Force-spawns an encounter near a target, bypassing chance/cooldown but not max-concurrent or safety checks. Used by /nexusmorality trigger. */
    public boolean forceSpawnNear(Player target) {
        if (active.size() >= config.getInt("encounter.max-concurrent", 3)) return false;
        Location spot = findSafeLocation(target);
        if (spot == null) return false;
        spawnEncounter(spot);
        return true;
    }

    private Location findSafeLocation(Player near) {
        World world = near.getWorld();
        int minDist = config.getInt("encounter.min-distance-blocks", 15);
        int maxDist = Math.max(minDist, config.getInt("encounter.max-distance-blocks", 30));
        int minFromSpawn = config.getInt("encounter.min-distance-from-world-spawn", 200);
        Location worldSpawn = world.getSpawnLocation();
        double minFromSpawnSq = (double) minFromSpawn * minFromSpawn;

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = minDist + random.nextDouble() * (maxDist - minDist);
            int x = near.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = near.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            // getHighestBlockYAt returns the Y of the topmost non-air block ITSELF (the ground
            // block), not the empty space above it - standing height is groundY + 1, not groundY.
            int groundY = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, groundY, z).getType();
            Material feet = world.getBlockAt(x, groundY + 1, z).getType();
            Material head = world.getBlockAt(x, groundY + 2, z).getType();

            if (!ground.isSolid() || ground.name().contains("LEAVES")) continue;
            if (isLiquidOrUnsafe(ground) || !feet.isAir() || !head.isAir()) continue;

            Location candidate = new Location(world, x + 0.5, groundY + 1, z + 0.5);
            if (candidate.distanceSquared(worldSpawn) < minFromSpawnSq) continue;

            return candidate;
        }
        return null;
    }

    private boolean isLiquidOrUnsafe(Material material) {
        return material == Material.WATER || material == Material.LAVA || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS || material == Material.FIRE || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE;
    }

    private void spawnEncounter(Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class, v -> {
            v.setProfession(Villager.Profession.NONE);
            v.setAI(true);
            v.setCustomName("§fInjured Survivor");
            v.setCustomNameVisible(true);
            v.setRemoveWhenFarAway(false); // we control despawn ourselves via despawn-seconds
            v.setHealth(Math.min(6.0, v.getMaxHealth()));
            v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false));
            v.getPersistentDataContainer().set(tagKey, PersistentDataType.BOOLEAN, true);
        });

        SurvivorEncounter encounter = new SurvivorEncounter(villager.getUniqueId(), location.clone(), Instant.now());
        active.put(villager.getUniqueId(), encounter);

        plugin.getLogger().info("[NexusMorality] Spawned a survivor encounter at " + describeLocation(location));
    }

    private void removeEntity(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) entity.remove();
    }

    private String describeLocation(Location location) {
        World world = location.getWorld();
        return (world != null ? world.getName() : "?") + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /** Removes every currently-tracked survivor entity so a restart doesn't leave orphaned NPCs behind. */
    public void shutdown() {
        for (SurvivorEncounter enc : active.values()) {
            removeEntity(enc.entityId);
        }
        active.clear();
    }
}
