package com.nexusuniverse.survival.disease;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class DiseaseManager {

    // how often (in ticks of the central 20-tick loop, i.e. seconds) symptoms re-apply
    private static final int SYMPTOM_INTERVAL_SECONDS = 5;

    // contagion radius/chance live per-Disease (see Disease.contagionRadius/contagionChance) --
    // this is just how often the check itself runs
    private static final int CONTAGION_CHECK_INTERVAL_SECONDS = 10;

    // lingering in a source biome, checked periodically rather than every tick
    private static final int BIOME_EXPOSURE_INTERVAL_SECONDS = 30;

    // severity: an uncured infection gets worse the longer it's left untreated.
    // Each tier bumps symptom strength; the top tier also deals real, repeated
    // damage, disables natural regen, and makes the player visibly glow --
    // getting cured is the only way to stop it.
    private static final int MAX_SEVERITY = 3;
    private static final String[] SEVERITY_LABELS = {"Mild", "Moderate", "Severe", "CRITICAL"};
    private static final BarColor[] SEVERITY_COLORS = {BarColor.YELLOW, BarColor.RED, BarColor.RED, BarColor.PURPLE};

    // a player who dies while infected leaves behind an infectious site --
    // anyone who lingers near the remains risks catching the same disease
    private static final long PLAGUE_SITE_DURATION_TICKS = 20L * 60 * 5; // 5 minutes
    private static final double PLAGUE_SITE_RADIUS = 3.0;
    private static final int MAX_RECORDED_DEATHS = 20;

    private static final String[] FLAVOR_MESSAGES = {
            "§7Your vision blurs at the edges...",
            "§7A cold sweat runs down your spine...",
            "§7Something feels deeply wrong...",
            "§7Your hands won't stop shaking...",
            "§7You can hear your own heartbeat...",
            "§7Your skin feels too tight..."
    };
    private static final String[] CRITICAL_FLAVOR_MESSAGES = {
            "§4§lYour body is failing...",
            "§4§lYou don't have much time...",
            "§4§lThis might be it...",
            "§4§lEverything feels distant now..."
    };

    private record PlagueSite(Location location, Disease disease, long expiresAtTick) {}

    private final PlayerDataManager playerData;
    private final NexusSurvivalConfig config;
    private final com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge;
    private final Random random = new Random();
    private final List<PlagueSite> plagueSites = new ArrayList<>();
    private final Deque<String> recentPlagueDeaths = new ArrayDeque<>();
    private long currentTick = 0;

    public DiseaseManager(PlayerDataManager playerData, NexusSurvivalConfig config, com.nexusuniverse.survival.seasons.SeasonBridge seasonBridge) {
        this.playerData = playerData;
        this.config = config;
        this.seasonBridge = seasonBridge;
    }

    /** 1.0 (no change) if NexusSeasons isn't installed; otherwise the configured danger-multiplier for the current season. */
    private double seasonalMultiplier() {
        String season = seasonBridge.currentSeasonName();
        return season == null ? 1.0 : config.getDouble("seasons.danger-multiplier." + season.toLowerCase(), 1.0);
    }

    public void infect(Player player, Disease disease) {
        SurvivalPlayerData d = playerData.get(player);
        if (d.infection != null) return; // already sick -- one disease at a time
        if (currentTick < d.immuneUntilTick) return; // recently cured -- riding out the immunity window
        d.infection = disease;
        d.symptomTickCounter = 0;
        d.severityTickCounter = 0;
        d.infectionSeverity = 0;

        player.sendMessage("§4You feel unwell... §c[" + disease.getDisplayName() + "]");
        player.sendMessage(disease.getDescription());
        player.sendTitle("§4§lINFECTED", "§c" + disease.getDisplayName(), 10, 50, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 0.6f, 0.8f);
    }

    public void infectRandom(Player player) {
        Disease[] all = Disease.values();
        infect(player, all[random.nextInt(all.length)]);
    }

    public boolean cure(Player player, Disease disease) {
        SurvivalPlayerData d = playerData.get(player);
        if (d.infection != disease) return false;
        d.infection = null;
        d.infectionSeverity = 0;
        d.severityTickCounter = 0;
        d.infectionBar.setVisible(false);
        player.setGlowing(false);

        int immunityMinutes = config.getInt("disease.post-cure-immunity-minutes", 60);
        d.immuneUntilTick = currentTick + (20L * 60 * immunityMinutes);

        player.sendMessage("§aYou feel the " + disease.getDisplayName() + " lift. You're cured.");
        player.sendMessage("§7You're immune to catching anything new for the next " + immunityMinutes + " minutes.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        return true;
    }

    /** True while a recently-cured player's immunity window is still active. */
    public boolean isImmune(Player player) {
        return currentTick < playerData.get(player).immuneUntilTick;
    }

    /** Real seconds left on the current immunity window, or 0 if not immune. */
    public long immunitySecondsRemaining(Player player) {
        long remainingTicks = playerData.get(player).immuneUntilTick - currentTick;
        return remainingTicks > 0 ? remainingTicks / 20 : 0;
    }

    public Disease getInfection(Player player) {
        return playerData.get(player).infection;
    }

    public int getSeverity(Player player) {
        return playerData.get(player).infectionSeverity;
    }

    /** Called once per second from the central tick loop. */
    public void tick(Player player) {
        SurvivalPlayerData d = playerData.get(player);

        // Environmental exposure runs regardless of infection status -- this is how a
        // currently-healthy player catches something from lingering in a source biome.
        d.biomeExposureTickCounter++;
        if (d.biomeExposureTickCounter >= BIOME_EXPOSURE_INTERVAL_SECONDS) {
            d.biomeExposureTickCounter = 0;
            tryInfectFromBiome(player, d);
        }

        if (d.infection == null) {
            if (d.infectionBar.isVisible()) d.infectionBar.setVisible(false);
            return;
        }

        d.severityTickCounter++;
        int severityIntervalSeconds = config.getInt("disease.severity-interval-seconds", 120);
        if (d.severityTickCounter >= severityIntervalSeconds && d.infectionSeverity < MAX_SEVERITY) {
            d.severityTickCounter = 0;
            d.infectionSeverity++;
            onSeverityIncrease(player, d);
        }

        updateInfectionBar(d);

        d.symptomTickCounter++;
        if (d.symptomTickCounter >= SYMPTOM_INTERVAL_SECONDS) {
            d.symptomTickCounter = 0;
            applySymptoms(player, d);
        }

        d.contagionTickCounter++;
        if (d.contagionTickCounter >= CONTAGION_CHECK_INTERVAL_SECONDS) {
            d.contagionTickCounter = 0;
            trySpread(player, d.infection);
        }
    }

    /**
     * Called once per second from the central tick loop, OUTSIDE the per-player
     * loop (this handles all online players and all active sites together, not
     * one player at a time) -- expires old plague sites and rolls infection
     * chance for anyone lingering near an active one.
     */
    public void tickGlobal(Iterable<? extends Player> onlinePlayers) {
        currentTick += 20;
        plagueSites.removeIf(site -> site.expiresAtTick() <= currentTick);
        if (plagueSites.isEmpty()) return;

        for (Player player : onlinePlayers) {
            if (getInfection(player) != null) continue;

            for (PlagueSite site : plagueSites) {
                if (site.location().getWorld() != player.getWorld()) continue;
                if (player.getLocation().distanceSquared(site.location()) > PLAGUE_SITE_RADIUS * PLAGUE_SITE_RADIUS) continue;

                if (random.nextDouble() < site.disease().contagionChance() * config.diseaseChanceMultiplier()) {
                    infect(player, site.disease());
                    player.sendMessage("§4The remains nearby made you sick.");
                }
                break; // one roll per player per second is enough, even near multiple sites
            }
        }
    }

    /** Call from a death listener: leaves an infectious site behind if the player died while sick. */
    public void markPlagueSite(Location location, Disease disease) {
        plagueSites.add(new PlagueSite(location.clone(), disease, currentTick + PLAGUE_SITE_DURATION_TICKS));
    }

    public void recordPlagueDeath(String playerName, Disease disease) {
        recentPlagueDeaths.addFirst(playerName + " -- " + disease.getDisplayName());
        while (recentPlagueDeaths.size() > MAX_RECORDED_DEATHS) {
            recentPlagueDeaths.removeLast();
        }
    }

    public List<String> getRecentPlagueDeaths() {
        return new ArrayList<>(recentPlagueDeaths);
    }

    private void applySymptoms(Player player, SurvivalPlayerData d) {
        for (PotionEffect base : d.infection.getSymptoms()) {
            int amplifier = base.getAmplifier() + d.infectionSeverity;
            player.addPotionEffect(new PotionEffect(base.getType(), base.getDuration(), amplifier, base.isAmbient(), base.hasParticles()));
        }

        if (d.infectionSeverity >= MAX_SEVERITY) {
            player.damage(config.getDouble("disease.critical-damage", 2.0));
            player.sendActionBar(pickRandom(CRITICAL_FLAVOR_MESSAGES));
        } else {
            player.sendActionBar(pickRandom(FLAVOR_MESSAGES));
        }
    }

    private void onSeverityIncrease(Player player, SurvivalPlayerData d) {
        String label = SEVERITY_LABELS[d.infectionSeverity];
        player.sendMessage("§4§lYour " + d.infection.getDisplayName() + " has worsened to " + label + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 0.6f + (d.infectionSeverity * 0.1f));

        if (d.infectionSeverity >= MAX_SEVERITY) {
            player.sendTitle("§4§lCRITICAL", "§cYour " + d.infection.getDisplayName() + " is life-threatening.", 10, 60, 20);
            player.setGlowing(true); // visibly marks the sick so others can see and steer clear
        }
    }

    private void updateInfectionBar(SurvivalPlayerData d) {
        if (!d.infectionBar.isVisible()) d.infectionBar.setVisible(true);
        String label = SEVERITY_LABELS[d.infectionSeverity];
        d.infectionBar.setTitle("§d\u2623 " + d.infection.getDisplayName() + " §7- " + label);
        d.infectionBar.setColor(SEVERITY_COLORS[d.infectionSeverity]);
        d.infectionBar.setProgress(1.0 - (d.infectionSeverity / (double) (MAX_SEVERITY + 1)));
    }

    private String pickRandom(String[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    /** Rolls a spread chance against every healthy player within the disease's own contagion radius. */
    private void trySpread(Player source, Disease disease) {
        double radius = disease.contagionRadius();
        for (Entity nearby : source.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player target)) continue;
            if (getInfection(target) != null) continue; // already sick with something -- one disease at a time

            if (random.nextDouble() < disease.contagionChance() * config.diseaseChanceMultiplier()) {
                infect(target, disease);
                source.sendMessage("§7" + target.getName() + " caught your " + disease.getDisplayName() + ".");
            }
        }
    }

    private void tryInfectFromBiome(Player player, SurvivalPlayerData d) {
        if (d.infection != null) return;

        Biome biome = player.getLocation().getBlock().getBiome();
        for (Disease disease : Disease.values()) {
            if (!disease.sourceBiomes().contains(biome)) continue;
            if (random.nextDouble() < config.getDouble("disease.biome-exposure-chance", 0.08) * seasonalMultiplier() * config.diseaseChanceMultiplier()) {
                infect(player, disease);
            }
            return; // one roll is enough even if (unlikely) more than one disease shares this biome
        }
    }

    /** Call from a block-break/harvest listener with the block's Material. */
    public void tryInfectFromBlock(Player player, Material material) {
        if (getInfection(player) != null) return;

        for (Disease disease : Disease.values()) {
            if (!disease.sourceBlocks().contains(material)) continue;
            if (random.nextDouble() < config.getDouble("disease.block-contact-chance", 0.10) * seasonalMultiplier() * config.diseaseChanceMultiplier()) {
                infect(player, disease);
            }
            return;
        }
    }

    /** Call from a damage listener with the attacking mob's EntityType. */
    public void tryInfectFromMob(Player player, EntityType mobType) {
        if (getInfection(player) != null) return;

        for (Disease disease : Disease.values()) {
            if (!disease.sourceMobs().contains(mobType)) continue;
            if (random.nextDouble() < config.getDouble("disease.mob-contact-chance", 0.10) * seasonalMultiplier() * config.diseaseChanceMultiplier()) {
                infect(player, disease);
            }
            return;
        }
    }
}
