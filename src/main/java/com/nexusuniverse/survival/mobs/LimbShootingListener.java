package com.nexusuniverse.survival.mobs;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Minecraft has no real per-limb hit detection -- a mob is one bounding
 * box, not a head/arms/legs model you can hit separately. This
 * approximates it from the arrow's height relative to the target's own
 * hitbox at the moment of impact, and each region has a real, permanent
 * consequence (not just a cosmetic label):
 *
 *  - HEAD: a decapitation -- always fatal, always drops the skull
 *  - ARM: Weakness, and the arm always drops as a trophy
 *  - LEG: the mob becomes a crawler (see CrawlerManager) -- AI switched
 *    off, forced into a prone pose, and manually dragged toward you at a
 *    fraction of normal speed instead of walking
 *
 * Any non-fatal hit also starts a lingering bleed (see BleedingTracker).
 *
 * Applies to the humanoid undead family (zombie and skeleton lines) --
 * the mobs where "shoot a limb off" reads as a coherent idea. Extending
 * this to more mobs later is just adding entries to DISMEMBERABLE.
 */
public class LimbShootingListener implements Listener {

    private static final Set<EntityType> DISMEMBERABLE = Set.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON
    );

    private static final int ARM_WEAKEN_DURATION_TICKS = 100;

    private final BleedingTracker bleedingTracker;
    private final CrawlerManager crawlerManager;

    public LimbShootingListener(BleedingTracker bleedingTracker, CrawlerManager crawlerManager) {
        this.bleedingTracker = bleedingTracker;
        this.crawlerManager = crawlerManager;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (!DISMEMBERABLE.contains(target.getType())) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        LimbPart part = determineHitLimb(target, arrow.getLocation());
        applyEffect(target, part, shooter);
    }

    private LimbPart determineHitLimb(LivingEntity target, Location hitLocation) {
        double minY = target.getBoundingBox().getMinY();
        double maxY = target.getBoundingBox().getMaxY();
        double span = Math.max(0.01, maxY - minY);
        double relative = (hitLocation.getY() - minY) / span;

        if (relative > 0.75) return LimbPart.HEAD;
        if (relative < 0.35) return LimbPart.LEG;
        return LimbPart.ARM;
    }

    private void applyEffect(LivingEntity target, LimbPart part, Player shooter) {
        if (part == LimbPart.HEAD) {
            spawnGore(target, 24);
            ItemStack skull = LimbDrops.create(target.getType(), LimbPart.HEAD);
            target.getWorld().dropItemNaturally(target.getLocation(), skull);
            shooter.sendActionBar("§4\u2620 Decapitated!");
            target.damage(target.getHealth() + 1, shooter); // decapitation is always fatal
            return; // target is dead -- nothing left to bleed or drop further
        }

        if (part == LimbPart.ARM) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ARM_WEAKEN_DURATION_TICKS, 2, true, true));
            target.getWorld().dropItemNaturally(target.getLocation(), LimbDrops.create(target.getType(), LimbPart.ARM));
        } else {
            crawlerManager.makeCrawler(target);
            target.getWorld().dropItemNaturally(target.getLocation(), LimbDrops.create(target.getType(), LimbPart.LEG));
        }

        spawnGore(target, 12);
        bleedingTracker.startBleeding(target);
    }

    private void spawnGore(LivingEntity target, int count) {
        Location base = target.getLocation();
        target.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, target.getHeight() / 2, 0),
                count, 0.3, 0.3, 0.3, new Particle.DustOptions(Color.RED, 1.2f));

        // ground splatter: bigger, flatter, sits low -- the "hit the floor" half of the effect
        target.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, 0.05, 0),
                count, 0.5, 0.02, 0.5, new Particle.DustOptions(Color.RED, 1.4f));
    }
}
