package com.nexusuniverse.kairos;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Wraps an EnderDragon entity as "Kairos" -- keeps the literal
 * ender-dragon-replacement feel you wanted, while our own code layers
 * scaling, mob waves, teleport strikes, dialogue, and the escape
 * mechanic on top. Vanilla dragon AI still drives base movement/attack
 * behavior once the intro finishes; this class adds everything
 * story-specific, including the portal intro/outro sequence.
 */
public class KairosBoss {

    private enum Phase { INTRO, ACTIVE, OUTRO_ESCAPE, OUTRO_DEATH, DONE }

    private static final int INTRO_TICKS = 100;   // 5s of portal buildup before he's revealed
    private static final int OUTRO_TICKS = 80;    // 4s of portal closing before he vanishes
    private static final int PORTAL_EFFECT_INTERVAL = 5;
    private static final double PORTAL_RADIUS = 3.0;

    private final NexusKairosPlugin plugin;
    private final int fightCount;
    private final boolean trueEnding;
    private final Random random = new Random();

    private EnderDragon dragon;
    private Location arenaCenter;
    private Phase phase = Phase.INTRO;
    private boolean dead = false;
    private int tickCounter = 0;
    private int phaseTickCounter = 0;

    public KairosBoss(NexusKairosPlugin plugin, int fightCount, boolean trueEnding) {
        this.plugin = plugin;
        this.fightCount = fightCount;
        this.trueEnding = trueEnding;
    }

    public void spawn(Location location) {
        this.arenaCenter = location.clone();

        dragon = (EnderDragon) location.getWorld().spawnEntity(location, EntityType.ENDER_DRAGON);
        dragon.setCustomName("§5§lKAIROS §7[Fight #" + fightCount + "]");
        dragon.setCustomNameVisible(true);
        dragon.setInvulnerable(true);
        if (dragon instanceof Mob mob) {
            mob.setAI(false);
        }

        TierCalculator calc = plugin.getTierCalculator();
        double health = calc.healthFor(fightCount);
        dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        dragon.setHealth(health);

        double speed = calc.speedMultiplierFor(fightCount);
        if (dragon.getAttribute(Attribute.GENERIC_FLYING_SPEED) != null) {
            dragon.getAttribute(Attribute.GENERIC_FLYING_SPEED).setBaseValue(0.6 * speed);
        }

        // hide him from everyone at the arena until the portal reveal completes
        for (Player player : nearbyPlayers(location, 80)) {
            player.hideEntity(plugin, dragon);
        }

        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.4f);
        phase = Phase.INTRO;
        phaseTickCounter = 0;
    }

    public boolean isDead() {
        return dead || dragon == null || dragon.isDead();
    }

    /** Called every server tick this fight is active by the manager. */
    public void tick() {
        if (isDead()) return;
        tickCounter++;
        phaseTickCounter++;

        switch (phase) {
            case INTRO -> tickIntro();
            case ACTIVE -> tickActive();
            case OUTRO_ESCAPE, OUTRO_DEATH -> tickOutro();
            case DONE -> { /* nothing left to do */ }
        }
    }

    private void tickIntro() {
        Location center = arenaCenter;

        if (phaseTickCounter % PORTAL_EFFECT_INTERVAL == 0) {
            drawPortalRing(center, PORTAL_RADIUS * (phaseTickCounter / (double) INTRO_TICKS + 0.3));
            center.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 1.0f);
        }

        if (phaseTickCounter >= INTRO_TICKS) {
            revealDragon();
        }
    }

    private void revealDragon() {
        Location center = arenaCenter;
        center.getWorld().spawnParticle(Particle.FLASH, center, 1);
        center.getWorld().spawnParticle(Particle.PORTAL, center, 200, 1.5, 2, 1.5, 1);
        center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.2f, 0.6f);

        for (Player player : nearbyPlayers(center, 80)) {
            player.showEntity(plugin, dragon);
        }

        dragon.setInvulnerable(false);
        if (dragon instanceof Mob mob) {
            mob.setAI(true);
        }

        // setAI(true) only re-enables goal-based behavior. An EnderDragon's
        // actual flight isn't goal-driven, it's driven by its Phase (see
        // EnderDragon.Phase / the vanilla EnderDragonPhaseManager). Because
        // our dragon is spawned into the *same* shared per-world dragon
        // battle that the vanilla exit-portal fight uses, that battle can
        // hand a freshly-spawned dragon a non-combat phase (most commonly
        // HOVER) if it thinks the "real" fight is already resolved -- which
        // looks exactly like "spawned fine, alive, but never moves." Force
        // it into the normal active-flight phase explicitly so we don't
        // depend on whatever phase the shared battle handed us.
        dragon.setPhase(EnderDragon.Phase.CIRCLING);

        phase = Phase.ACTIVE;
        phaseTickCounter = 0;

        plugin.getDialogueEngine().requestLine(fightCount, null, "fight_start")
                .thenAccept(line -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> broadcastNear(center, line)));
    }

    private void tickActive() {
        TierCalculator calc = plugin.getTierCalculator();

        // escape check -- normal fights only
        if (!trueEnding) {
            double healthPercent = dragon.getHealth() / dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            double escapeAt = plugin.getConfig().getDouble("fight.escape-health-percent", 0.12);
            if (healthPercent <= escapeAt) {
                beginOutro(Phase.OUTRO_ESCAPE);
                return;
            }
        }

        if (dragon.getHealth() <= 0) {
            beginOutro(Phase.OUTRO_DEATH);
            return;
        }

        // watchdog: the world's shared vanilla dragon battle (EnderDragonBattle)
        // auto-adopts ANY EnderDragon spawned in this dimension and can force
        // it into a non-combat phase (HOVER, FLY_TO_PORTAL, LAND_ON_PORTAL,
        // LEAVE_PORTAL) on basically any tick it likes -- not just once at
        // spawn. A periodic check (previously every 40 ticks / ~2s) loses
        // that tug-of-war: vanilla can re-assert the passive phase on the
        // very next tick after we fix it, so the dragon spent most of every
        // 2-second window stuck. Checking every tick means vanilla never
        // gets more than a single tick to win before we correct it back, so
        // he actually keeps moving instead of "being there but frozen."
        EnderDragon.Phase dragonPhase = dragon.getPhase();
        if (dragonPhase == EnderDragon.Phase.HOVER
                || dragonPhase == EnderDragon.Phase.FLY_TO_PORTAL
                || dragonPhase == EnderDragon.Phase.LAND_ON_PORTAL
                || dragonPhase == EnderDragon.Phase.LEAVE_PORTAL) {
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
        }

        // mob waves every ~20 seconds (400 ticks), scaled by band
        if (tickCounter % 400 == 0) {
            spawnMobWave(calc.mobWavesFor(fightCount));
        }

        // teleport strikes every ~15 seconds once unlocked
        if (calc.teleportStrikesUnlocked(fightCount) && tickCounter % 300 == 0) {
            teleportStrike();
        }

        // arena hazards every ~12.5 seconds once unlocked (Adapting band onward)
        if (calc.arenaHazardsUnlocked(fightCount) && tickCounter % 250 == 0) {
            plugin.getArenaHazards().trigger(arenaCenter, nearbyPlayers(dragon.getLocation(), 40));
        }

        // occasional dialogue mid-fight
        if (tickCounter % 600 == 0) {
            Location current = dragon.getLocation();
            plugin.getDialogueEngine().requestLine(fightCount, null, "mid_fight")
                    .thenAccept(line -> plugin.getServer().getScheduler().runTask(plugin,
                            () -> broadcastNear(current, line)));
        }
    }

    private void beginOutro(Phase outroPhase) {
        phase = outroPhase;
        phaseTickCounter = 0;

        dragon.setInvulnerable(true);
        if (dragon instanceof Mob mob) {
            mob.setAI(false);
        }

        String eventType = outroPhase == Phase.OUTRO_ESCAPE ? "escape" : "death";
        Location location = dragon.getLocation();
        plugin.getDialogueEngine().requestLine(fightCount, null, eventType)
                .thenAccept(line -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> broadcastNear(location, line)));

        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_HURT, 0.8f, 0.5f);
    }

    private void tickOutro() {
        Location center = dragon.getLocation();

        // slow ascent into the closing portal, sells the "leaving" motion
        dragon.teleport(center.clone().add(0, 0.05, 0));

        if (phaseTickCounter % PORTAL_EFFECT_INTERVAL == 0) {
            double shrinkFactor = 1.0 - (phaseTickCounter / (double) OUTRO_TICKS);
            drawPortalRing(center, Math.max(0.3, PORTAL_RADIUS * shrinkFactor));
        }

        if (phaseTickCounter >= OUTRO_TICKS) {
            finishOutro();
        }
    }

    private void finishOutro() {
        Location center = dragon.getLocation();
        center.getWorld().spawnParticle(Particle.FLASH, center, 1);
        center.getWorld().spawnParticle(Particle.PORTAL, center, 250, 1, 1.5, 1, 1.2);
        center.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        boolean wasDeath = phase == Phase.OUTRO_DEATH;
        dragon.remove();
        dead = true;
        phase = Phase.DONE;

        if (wasDeath) {
            broadcastNear(center, "§4§lTHE TRUE ENDING BEGINS.");
            plugin.getFightState().incrementAndGet();
            plugin.getFightState().setTrueEndingArmed(false);
            // TODO: hook your actual true-ending sequence here (cutscene,
            // rewards, story-flag updates, whatever that looks like)
        } else {
            int newCount = plugin.getFightState().incrementAndGet();
            plugin.getLogger().info("Kairos escaped. Fight count is now " + newCount + ".");

            int threshold = plugin.getConfig().getInt("fight.true-ending-unlock-count", 50);
            if (!plugin.getFightState().isTrueEndingArmed() && newCount >= threshold) {
                plugin.getFightState().setTrueEndingArmed(true);
                org.bukkit.Bukkit.broadcastMessage(
                        "§4§lSomething is different now. §7Kairos will not run again.");
            }
        }
    }

    /**
     * Draws a rough ring of portal-style particles around a point --
     * used for both the intro buildup and the outro closing, just at
     * different radii/directions of change.
     */
    private void drawPortalRing(Location center, double radius) {
        int points = 20;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location point = new Location(center.getWorld(), x, center.getY() + 1, z);
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, point, 2, 0, 0.2, 0, 0.01);
            if (i % 4 == 0) {
                center.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnMobWave(int count) {
        if (count <= 0) return;
        Location center = dragon.getLocation();
        List<EntityType> pool = List.of(EntityType.ENDERMAN, EntityType.PHANTOM, EntityType.VEX);
        for (int i = 0; i < count; i++) {
            EntityType type = pool.get(random.nextInt(pool.size()));
            Location spawnAt = center.clone().add(
                    random.nextInt(10) - 5, random.nextInt(4), random.nextInt(10) - 5);
            center.getWorld().spawnEntity(spawnAt, type);
        }
    }

    private void teleportStrike() {
        List<Player> nearby = nearbyPlayers(dragon.getLocation(), 40);
        if (nearby.isEmpty()) return;

        Player target = nearby.get(random.nextInt(nearby.size()));
        Location strikeAt = target.getLocation().clone().add(0, 3, 0);
        dragon.teleport(strikeAt);
        dragon.getWorld().spawnParticle(Particle.DRAGON_BREATH, strikeAt, 30);
        target.damage(plugin.getTierCalculator().damageFor(fightCount), dragon);
    }

    private List<Player> nearbyPlayers(Location center, double radius) {
        return center.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(center) <= radius)
                .toList();
    }

    private void broadcastNear(Location center, String message) {
        for (Player player : nearbyPlayers(center, 60)) {
            player.sendMessage(message);
        }
    }

    public UUID getEntityId() {
        return dragon != null ? dragon.getUniqueId() : null;
    }
}
