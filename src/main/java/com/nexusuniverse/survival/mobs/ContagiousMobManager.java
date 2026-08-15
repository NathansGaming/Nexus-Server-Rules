package com.nexusuniverse.survival.mobs;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import com.nexusuniverse.survival.disease.Disease;
import com.nexusuniverse.survival.disease.DiseaseManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A handful of disease-carrying variants, one per base mob type, each
 * tied to a specific Disease. Unlike the generic environmental mob
 * sourcing in Disease.sourceMobs() (any zombie has a small baseline
 * chance to pass Fever Rot on a hit), these are dedicated carriers: they
 * spawn naturally just like normal mobs (upgrading a fraction of vanilla
 * spawns in place, the same trick as Feral/TNT Zombies), they're visibly
 * marked so you can learn to recognize and avoid them, and they infect at
 * a meaningfully higher rate -- both on bite AND just from standing too
 * close, which the generic environmental sourcing never did.
 *
 * Also handles "turning": when a zombie-family mob (Zombie/Husk/Drowned --
 * the biting family, not the skeleton/arrow family) lands a hit on a
 * passive animal, there's a chance the animal doesn't just die -- it's
 * replaced with a fresh disease carrier that then comes after players
 * too. Villagers are deliberately excluded: vanilla already has its own
 * proper zombie-villager conversion, no need to compete with it.
 *
 * HONEST LIMITATION: there's no vanilla "zombie cow" or "zombie sheep"
 * entity to spawn as a faithful reskin -- Bukkit can't render a custom
 * mob model. What actually happens is the animal is removed and a real
 * disease-carrier (Plague Zombie, Plague Husk, etc.) spawns in its place.
 * It reads as "that cow just turned into a zombie," it just isn't
 * literally a zombie-textured cow.
 */
public class ContagiousMobManager implements Listener {

    private static final Map<EntityType, Disease> VARIANTS = Map.of(
            EntityType.ZOMBIE, Disease.FEVER_ROT,
            EntityType.HUSK, Disease.BREATH_FEVER,
            EntityType.DROWNED, Disease.SWAMP_FEVER,
            EntityType.SKELETON, Disease.BONE_CHILL,
            EntityType.WITHER_SKELETON, Disease.WITHERING_PLAGUE
    );

    // the "biting" family that can turn a passive animal -- skeletons shoot arrows, that's a kill, not an infection
    private static final Set<EntityType> TURNING_ATTACKERS = Set.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED);

    private static final Set<EntityType> TURNABLE_ANIMALS = Set.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.HORSE, EntityType.MOOSHROOM, EntityType.GOAT
    );

    private static final double PROXIMITY_RADIUS = 2.0;
    private static final String SUMMON_ITEM_ID = "plague_mob_summon";

    private final NamespacedKey diseaseKey;
    private final NamespacedKey summonKey;
    private final DiseaseManager diseaseManager;
    private final NexusSurvivalConfig config;
    private final com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge;
    private final Random random = new Random();

    public ContagiousMobManager(Plugin plugin, DiseaseManager diseaseManager, NexusSurvivalConfig config, com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge) {
        this.diseaseKey = new NamespacedKey(plugin, "contagious_disease");
        this.summonKey = new NamespacedKey(plugin, "plague_mob_summon_item");
        this.diseaseManager = diseaseManager;
        this.config = config;
        this.seasonBridge = seasonBridge;
    }

    private double seasonalMultiplier() {
        String season = seasonBridge.currentSeasonName();
        return season == null ? 1.0 : config.getDouble("seasons.danger-multiplier." + season.toLowerCase(), 1.0);
    }

    public void upgrade(LivingEntity entity, Disease disease) {
        entity.getPersistentDataContainer().set(diseaseKey, PersistentDataType.STRING, disease.name());
        entity.setCustomName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Plague " + prettyName(entity.getType()));
        entity.setCustomNameVisible(true);
    }

    public Disease getCarriedDisease(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(diseaseKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Disease.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Spawns a random disease-carrier variant at the given location. Used by the summon item and by turning. */
    public LivingEntity spawnRandomCarrier(Location location) {
        if (location.getWorld() == null) return null;

        List<EntityType> types = new ArrayList<>(VARIANTS.keySet());
        EntityType chosenType = types.get(random.nextInt(types.size()));
        Disease chosenDisease = VARIANTS.get(chosenType);

        Entity spawned = location.getWorld().spawnEntity(location, chosenType, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(spawned instanceof LivingEntity living)) return null;

        upgrade(living, chosenDisease);
        return living;
    }

    /** A small fraction of natural spawns of each covered base type come in as disease carriers. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Disease disease = VARIANTS.get(event.getEntityType());
        if (disease == null) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (random.nextDouble() < config.getDouble("mobs.plague-carriers.natural-spawn-chance", 0.06) * seasonalMultiplier()) {
            upgrade(living, disease);
        }
    }

    /** Getting bitten by a carrier is a real, high-odds way to catch what it's carrying. */
    @EventHandler
    public void onBite(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof LivingEntity attacker) {
            Disease disease = getCarriedDisease(attacker);
            if (disease != null && random.nextDouble() < config.getDouble("mobs.plague-carriers.bite-infection-chance", 0.35) * config.diseaseChanceMultiplier()) {
                diseaseManager.infect(player, disease);
            }
        }

        handleTurning(event);
    }

    /**
     * A zombie-family mob biting a passive animal has a chance to turn it
     * into a fresh disease carrier instead of just killing it -- the
     * animal's death is cancelled, it's removed, and a Plague variant
     * spawns in its place, immediately hostile and immediately contagious.
     */
    private void handleTurning(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker) || !TURNING_ATTACKERS.contains(attacker.getType())) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || !TURNABLE_ANIMALS.contains(victim.getType())) return;

        if (random.nextDouble() >= config.getDouble("mobs.turning.chance", 0.25)) return;

        event.setCancelled(true); // spare it from a normal death -- it's about to become something worse
        Location location = victim.getLocation();
        String victimName = prettyName(victim.getType());
        victim.remove();

        LivingEntity turned = spawnRandomCarrier(location);
        if (turned != null) {
            location.getWorld().spawnParticle(org.bukkit.Particle.DUST, location.add(0, turned.getHeight() / 2, 0),
                    16, 0.3, 0.3, 0.3, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
            for (Player nearby : location.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(location) <= 20 * 20) {
                    nearby.sendMessage(ChatColor.DARK_GREEN + "A " + victimName + " nearby just turned...");
                }
            }
        }
    }

    /** Called once per second from the central tick loop: standing too close to a carrier risks catching it with no bite needed. */
    public void tickAll(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            if (diseaseManager.getInfection(player) != null) continue;

            for (Entity nearby : player.getNearbyEntities(PROXIMITY_RADIUS, PROXIMITY_RADIUS, PROXIMITY_RADIUS)) {
                if (!(nearby instanceof LivingEntity living)) continue;
                Disease disease = getCarriedDisease(living);
                if (disease == null) continue;

                if (random.nextDouble() < config.getDouble("mobs.plague-carriers.proximity-infection-chance", 0.05) * config.diseaseChanceMultiplier()) {
                    diseaseManager.infect(player, disease);
                }
                break; // one roll per player per second is enough, even near multiple carriers
            }
        }
    }

    private String prettyName(EntityType type) {
        String raw = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    // --- Admin summon item: picks a random variant from VARIANTS on use ---

    public ItemStack createSummonItem() {
        ItemStack item = new ItemStack(Material.ROTTEN_FLESH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GREEN + "Plague Carrier Summon");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right-click a block to unleash a random");
        lore.add(ChatColor.GRAY + "disease-carrying mob.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(summonKey, PersistentDataType.STRING, SUMMON_ITEM_ID);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSummonItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(summonKey, PersistentDataType.STRING);
        return SUMMON_ITEM_ID.equals(id);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        if (!isSummonItem(inHand)) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        var spawnAt = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        spawnRandomCarrier(spawnAt);

        inHand.setAmount(inHand.getAmount() - 1);
        event.getPlayer().sendMessage(ChatColor.DARK_GREEN + "A disease carrier has been unleashed.");
    }
}
