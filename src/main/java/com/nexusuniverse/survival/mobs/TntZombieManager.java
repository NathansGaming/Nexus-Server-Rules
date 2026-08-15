package com.nexusuniverse.survival.mobs;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A suicide-bomber zombie variant: it doesn't attack or break blocks, it
 * just closes distance and detonates. Rather than hand-rolling an
 * explosion, it spawns a real TNTPrimed entity at the moment it triggers
 * and lets vanilla handle the actual blast -- damage, block destruction,
 * particles, and sound all read as authentic because they ARE vanilla TNT.
 */
public class TntZombieManager implements Listener {

    private static final String SUMMON_ITEM_ID = "tnt_zombie_summon";
    private static final double TRIGGER_RADIUS = 3.0;
    private static final int FUSE_TICKS = 25; // 1.25s -- enough warning to run, not enough to feel safe
    private static final float EXPLOSION_YIELD = 3.0f; // roughly a Creeper's blast radius

    private final NexusSurvivalConfig config;
    private final com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge;
    private final NamespacedKey tntZombieKey;
    private final NamespacedKey summonKey;
    private final Random random = new Random();
    private final Set<UUID> primed = new HashSet<>();

    public TntZombieManager(Plugin plugin, NexusSurvivalConfig config, com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge) {
        this.config = config;
        this.seasonBridge = seasonBridge;
        this.tntZombieKey = new NamespacedKey(plugin, "tnt_zombie");
        this.summonKey = new NamespacedKey(plugin, "tnt_zombie_summon_item");
    }

    private double seasonalMultiplier() {
        String season = seasonBridge.currentSeasonName();
        return season == null ? 1.0 : config.getDouble("seasons.danger-multiplier." + season.toLowerCase(), 1.0);
    }

    public boolean isTntZombie(Zombie zombie) {
        Byte tag = zombie.getPersistentDataContainer().get(tntZombieKey, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }

    public void upgrade(Zombie zombie) {
        zombie.getPersistentDataContainer().set(tntZombieKey, PersistentDataType.BYTE, (byte) 1);
        zombie.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + "TNT Zombie");
        zombie.setCustomNameVisible(true);

        var equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.TNT));
            equipment.setHelmetDropChance(0f);
        }
    }

    /** A small fraction of naturally-spawning zombies come in as bombers. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ZOMBIE) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (random.nextDouble() < config.getDouble("mobs.tnt-zombie.natural-spawn-chance", 0.03) * seasonalMultiplier()) {
            upgrade(zombie);
        }
    }

    /** Called once per second from the central tick loop: checks every online player for a TNT Zombie close enough to trigger. */
    public void tickAll(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            for (Entity nearby : player.getNearbyEntities(TRIGGER_RADIUS, TRIGGER_RADIUS, TRIGGER_RADIUS)) {
                if (!(nearby instanceof Zombie zombie) || !isTntZombie(zombie)) continue;
                if (!primed.add(zombie.getUniqueId())) continue; // already counting down
                detonate(zombie);
            }
        }
    }

    private void detonate(Zombie zombie) {
        Location loc = zombie.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        zombie.remove();

        TNTPrimed tnt = (TNTPrimed) loc.getWorld().spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(FUSE_TICKS);
        tnt.setYield(EXPLOSION_YIELD);
    }

    public ItemStack createSummonItem() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "TNT Zombie Summon");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click a block to unleash one."));
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
        Location spawnAt = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        if (spawnAt.getWorld() == null) return;

        Zombie zombie = (Zombie) spawnAt.getWorld().spawnEntity(spawnAt, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.CUSTOM);
        upgrade(zombie);

        inHand.setAmount(inHand.getAmount() - 1);
        event.getPlayer().sendMessage(ChatColor.GOLD + "A TNT Zombie has been unleashed.");
    }
}
