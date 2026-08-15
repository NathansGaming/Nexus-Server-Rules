package com.nexusuniverse.survival.mobs;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

import java.util.List;
import java.util.Random;

/**
 * A "Feral Zombie" isn't a new entity type -- Bukkit can't register those
 * without NMS. It's a normal Zombie with boosted attributes, a custom name,
 * and a PDC tag, either rolled naturally on a small fraction of vanilla
 * zombie spawns, or placed deliberately with the summon item.
 */
public class FeralZombieManager implements Listener {

    private static final String SUMMON_ITEM_ID = "feral_zombie_summon";

    private static final double HEALTH_MULTIPLIER = 2.5;
    private static final double DAMAGE_MULTIPLIER = 2.0;
    private static final double SPEED_MULTIPLIER = 1.2;

    private final Plugin plugin;
    private final NexusSurvivalConfig config;
    private final com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge;
    private final NamespacedKey feralKey;
    private final NamespacedKey summonKey;
    private final Random random = new Random();

    public FeralZombieManager(Plugin plugin, NexusSurvivalConfig config, com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge) {
        this.plugin = plugin;
        this.config = config;
        this.seasonBridge = seasonBridge;
        this.feralKey = new NamespacedKey(plugin, "feral_zombie");
        this.summonKey = new NamespacedKey(plugin, "feral_summon_item");
    }

    private double seasonalMultiplier() {
        String season = seasonBridge.currentSeasonName();
        return season == null ? 1.0 : config.getDouble("seasons.danger-multiplier." + season.toLowerCase(), 1.0);
    }

    public boolean isFeral(LivingEntity entity) {
        Byte tag = entity.getPersistentDataContainer().get(feralKey, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }

    public void upgrade(Zombie zombie) {
        zombie.getPersistentDataContainer().set(feralKey, PersistentDataType.BYTE, (byte) 1);
        zombie.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Feral Zombie");
        zombie.setCustomNameVisible(true);

        scaleAttribute(zombie, Attribute.GENERIC_MAX_HEALTH, HEALTH_MULTIPLIER);
        zombie.setHealth(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        scaleAttribute(zombie, Attribute.GENERIC_ATTACK_DAMAGE, DAMAGE_MULTIPLIER);
        scaleAttribute(zombie, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MULTIPLIER);
    }

    private void scaleAttribute(LivingEntity entity, Attribute attribute, double multiplier) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) return;
        instance.setBaseValue(instance.getBaseValue() * multiplier);
    }

    /** A small fraction of naturally-spawning zombies come in feral. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ZOMBIE) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (random.nextDouble() < config.getDouble("mobs.feral-zombie.natural-spawn-chance", 0.05) * seasonalMultiplier()) {
            upgrade(zombie);
        }
    }

    public ItemStack createSummonItem() {
        ItemStack item = new ItemStack(Material.ZOMBIE_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "Feral Zombie Summon");
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
        event.getPlayer().sendMessage(ChatColor.DARK_RED + "A Feral Zombie has been unleashed.");
    }
}
