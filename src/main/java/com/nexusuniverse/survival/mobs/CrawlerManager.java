package com.nexusuniverse.survival.mobs;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A mob shot in the legs loses normal movement: its AI is switched off (so
 * vanilla pathfinding/jumping stops entirely), it's hidden, and a small,
 * custom-posed ArmorStand takes its place visually -- head tilted down,
 * arms angled forward like it's dragging itself, body pitched low. The
 * real mob is still there underneath (still has health, still deals
 * damage, still drops loot) -- the ArmorStand is purely a skin riding on
 * top of it, repositioned every tick to follow it exactly.
 *
 * Why not just use Pose.SWIMMING like the first attempt did: that only
 * changes anything visually for mobs that actually have a distinct
 * animation for that pose (players swimming, foxes sleeping, etc). A
 * Zombie has no "lying flat" animation in vanilla, so forcing that pose
 * flag did nothing you could see -- this replaces that approach entirely.
 *
 * PERSISTENCE NOTE: the crawler tag itself lives in PDC (survives a
 * restart). The companion ArmorStand is deliberately NOT persistent
 * (Entity#setPersistent(false)) so a restart cleanly discards it instead
 * of leaving an orphaned floating head behind -- ensureCompanion() then
 * rebuilds a fresh one the moment a previously-tagged crawler is
 * rediscovered (on chunk load or the startup scan).
 *
 * HONEST LIMITATION: this is a static pose, not a walk-cycle animation --
 * Bukkit can't author new skeletal animation frames. It reads as "a low,
 * hunched thing dragging itself toward you," not literal crawling
 * limb-over-limb motion. Movement is also still a straight-line vector
 * nudge, not real pathfinding -- it beelines for you and can get stuck on
 * a step, a fence, a one-block gap.
 */
public class CrawlerManager implements Listener {

    private static final double CRAWL_SPEED = 0.04;
    private static final double DETECTION_RADIUS = 16.0;

    private final NamespacedKey crawlerKey;
    private final Set<UUID> crawlers = new HashSet<>();
    private final Map<UUID, UUID> companionStands = new HashMap<>(); // crawler entity UUID -> its ArmorStand's UUID

    public CrawlerManager(Plugin plugin) {
        this.crawlerKey = new NamespacedKey(plugin, "crawler");
    }

    public void makeCrawler(LivingEntity entity) {
        if (!crawlers.add(entity.getUniqueId())) return; // already crawling
        entity.getPersistentDataContainer().set(crawlerKey, PersistentDataType.BYTE, (byte) 1);
        entity.setAI(false);
        entity.setInvisible(true); // the real mob is hidden -- the posed ArmorStand is what's actually seen

        var speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.0); // AI is off anyway; keeps state consistent if it's ever restored

        ensureCompanion(entity);
    }

    public boolean isCrawler(LivingEntity entity) {
        Byte tag = entity.getPersistentDataContainer().get(crawlerKey, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }

    /** Spawns a companion ArmorStand if this crawler doesn't currently have one (e.g. after a restart). */
    private void ensureCompanion(LivingEntity entity) {
        UUID existingStandId = companionStands.get(entity.getUniqueId());
        if (existingStandId != null && entity.getServer().getEntity(existingStandId) instanceof ArmorStand) {
            return; // already has a live companion
        }

        Location loc = entity.getLocation();
        if (loc.getWorld() == null) return;

        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(false);
        stand.setPersistent(false); // deliberately doesn't survive a restart -- see class doc

        var equipment = stand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(headFor(entity.getType())));
        }

        // low, hunched, reaching-forward pose
        stand.setBodyPose(new EulerAngle(Math.toRadians(75), 0, 0));
        stand.setHeadPose(new EulerAngle(Math.toRadians(-20), 0, 0));
        stand.setRightArmPose(new EulerAngle(Math.toRadians(-70), 0, Math.toRadians(10)));
        stand.setLeftArmPose(new EulerAngle(Math.toRadians(-70), 0, Math.toRadians(-10)));
        stand.setRightLegPose(new EulerAngle(Math.toRadians(20), 0, 0));
        stand.setLeftLegPose(new EulerAngle(Math.toRadians(20), 0, 0));

        companionStands.put(entity.getUniqueId(), stand.getUniqueId());
    }

    private Material headFor(EntityType type) {
        return switch (type) {
            case SKELETON, STRAY -> Material.SKELETON_SKULL; // no distinct vanilla Stray head, closest fit
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            default -> Material.ZOMBIE_HEAD; // ZOMBIE, HUSK, DROWNED -- no distinct vanilla heads for Husk/Drowned
        };
    }

    /** Call once at startup: catches every already-tagged crawler in every already-loaded chunk. */
    public void scanLoadedChunks(Iterable<? extends World> worlds) {
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living && isCrawler(living)) {
                    crawlers.add(living.getUniqueId());
                    ensureCompanion(living);
                }
            }
        }
    }

    /** Catches tagged crawlers in chunks that load after startup (e.g. a player walking back into an old area). */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living && isCrawler(living)) {
                crawlers.add(living.getUniqueId());
                ensureCompanion(living);
            }
        }
    }

    /** Called once per second from the central tick loop: manually drags every known crawler toward its nearest player. */
    public void tickAll(Server server) {
        crawlers.removeIf(id -> {
            Entity entity = server.getEntity(id);
            boolean gone = !(entity instanceof LivingEntity living) || living.isDead() || !living.isValid();
            if (gone) removeCompanion(server, id);
            return gone;
        });

        for (UUID id : crawlers) {
            if (!(server.getEntity(id) instanceof LivingEntity entity)) continue;
            Player nearest = findNearestPlayer(entity);
            Location moved = entity.getLocation();

            if (nearest != null) {
                Vector toTarget = nearest.getLocation().toVector().subtract(entity.getLocation().toVector());
                if (toTarget.lengthSquared() >= 0.01) {
                    Vector horizontal = toTarget.clone();
                    horizontal.setY(0);

                    if (horizontal.lengthSquared() > 0.0001) {
                        Vector step = horizontal.clone().normalize().multiply(CRAWL_SPEED);
                        moved = entity.getLocation().add(step);
                        moved.setDirection(horizontal); // horizontal-only -- looks forward, not up/down at the player's exact height
                        entity.teleport(moved);
                    }
                }
            }

            syncCompanion(server, id, moved);
        }
    }

    /** Keeps the companion ArmorStand riding on the (invisible) real mob's current position and facing. */
    private void syncCompanion(Server server, UUID crawlerId, Location location) {
        UUID standId = companionStands.get(crawlerId);
        if (standId == null) return;
        if (!(server.getEntity(standId) instanceof ArmorStand stand)) {
            companionStands.remove(crawlerId);
            return;
        }
        Location standLoc = location.clone();
        standLoc.setPitch(0); // pitch is expressed through the body/head pose, not the entity's own rotation
        stand.teleport(standLoc);
    }

    private void removeCompanion(Server server, UUID crawlerId) {
        UUID standId = companionStands.remove(crawlerId);
        if (standId != null && server.getEntity(standId) instanceof ArmorStand stand) {
            stand.remove();
        }
    }

    private Player findNearestPlayer(LivingEntity entity) {
        Player nearest = null;
        double closestSq = DETECTION_RADIUS * DETECTION_RADIUS;

        for (Entity nearby : entity.getNearbyEntities(DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS)) {
            if (!(nearby instanceof Player player)) continue;
            double distSq = player.getLocation().distanceSquared(entity.getLocation());
            if (distSq < closestSq) {
                closestSq = distSq;
                nearest = player;
            }
        }
        return nearest;
    }
}
