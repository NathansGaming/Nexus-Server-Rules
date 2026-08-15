package com.nexusuniverse.survival.climate;

import com.nexusuniverse.survival.config.NexusSurvivalConfig;
import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import com.nexusuniverse.survival.seasons.SeasonBridge;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * Makes the season NexusSeasons reports (via SeasonBridge, the same reflective link
 * DiseaseManager/FeralZombieManager/TntZombieManager/ContagiousMobManager already use) actually
 * do something to a player physically, not just scale mob/disease numbers behind the scenes.
 *
 * "Exposed" for both winter and summer means: outdoors under open sky, checked via
 * World#getHighestBlockYAt rather than a light-level check (simple, and correct regardless of
 * whether it's day or night -- a roof blocks winter exposure just as well at 3am as at noon,
 * which a light-level check wouldn't necessarily agree with). Standing under any roof, overhang,
 * or inside a structure counts as sheltered from both hypothermia and heat exhaustion, which is
 * the whole point of building shelter in a survival server.
 *
 * "Snowing" specifically (not just "raining") uses the real vanilla threshold: the world is
 * storming AND the local biome temperature at that spot (height-adjusted, so altitude matters
 * the same way it does for vanilla's own snow) is below 0.15. A storm in a warm biome is rain,
 * not snow, and doesn't trigger hypothermia here either -- matching how the actual game decides
 * whether precipitation renders as snow.
 *
 * All three season buckets accumulate in discrete STAGES rather than flipping on instantly, and
 * recede the same way once the triggering condition stops -- so ducking indoors for a minute
 * doesn't undo an entire session of being caught out in a blizzard, but it does start recovery
 * immediately rather than requiring a full reset.
 */
public class ClimateManager {

    private static final double SNOW_TEMPERATURE_THRESHOLD = 0.15;

    private final PlayerDataManager playerData;
    private final NexusSurvivalConfig config;
    private final SeasonBridge seasonBridge;
    private final Random random = new Random();

    public ClimateManager(PlayerDataManager playerData, NexusSurvivalConfig config, SeasonBridge seasonBridge) {
        this.playerData = playerData;
        this.config = config;
        this.seasonBridge = seasonBridge;
    }

    /** Called every second from the central tick loop. */
    public void tick(Player player) {
        if (!config.getBoolean("climate.enabled", true)) return;

        String season = seasonBridge.currentSeasonName();
        if (season == null) return; // NexusSeasons not installed/connected -- no-op, nothing else in this plugin needs it either

        SurvivalPlayerData d = playerData.get(player);
        boolean exposed = isExposedToSky(player);

        switch (season) {
            case "WINTER" -> {
                tickWinter(player, d, exposed);
                decayHeat(d);
            }
            case "SUMMER" -> {
                tickSummer(player, d, exposed);
                decayHypothermia(player, d);
            }
            default -> {
                tickMildSeason(player, d);
                decayHypothermia(player, d);
                decayHeat(d);
            }
        }
    }

    // --- winter: hypothermia ---

    private void tickWinter(Player player, SurvivalPlayerData d, boolean exposed) {
        boolean snowingHere = exposed && player.getWorld().hasStorm() && isColdEnoughToSnow(player.getLocation());

        if (snowingHere) {
            int stageSeconds = Math.max(1, config.getInt("climate.winter.exposure-seconds-per-stage", 30));
            d.hypothermiaTickCounter++;
            if (d.hypothermiaTickCounter >= stageSeconds) {
                d.hypothermiaTickCounter = 0;
                int maxStage = Math.max(0, config.getInt("climate.winter.max-stage", 4));
                if (d.hypothermiaStage < maxStage) {
                    d.hypothermiaStage++;
                    if (d.hypothermiaStage == 1) {
                        player.sendMessage("§bYou're getting cold out here...");
                    }
                }
            }
        } else {
            decayHypothermia(player, d);
        }

        applyHypothermiaEffects(player, d);
    }

    private void decayHypothermia(Player player, SurvivalPlayerData d) {
        if (d.hypothermiaStage <= 0) {
            d.hypothermiaTickCounter = 0;
            return;
        }
        int recoverySeconds = Math.max(1, config.getInt("climate.winter.recovery-seconds-per-stage", 20));
        d.hypothermiaTickCounter++;
        if (d.hypothermiaTickCounter >= recoverySeconds) {
            d.hypothermiaTickCounter = 0;
            d.hypothermiaStage--;
            if (d.hypothermiaStage == 0) {
                player.sendMessage("§bYou've warmed back up.");
            }
        }
    }

    private void applyHypothermiaEffects(Player player, SurvivalPlayerData d) {
        if (d.hypothermiaStage <= 0) return;
        // stage 1: slow. stage 2: slower + mining fatigue. stage 3: + nausea. stage 4 (max by
        // default): all of the above plus real, if light, damage -- true hypothermia, not just
        // an inconvenience
        if (d.hypothermiaStage >= 1) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, false));
        if (d.hypothermiaStage >= 2) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, false));
        if (d.hypothermiaStage >= 2) player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, true, false));
        if (d.hypothermiaStage >= 3) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false));
        if (d.hypothermiaStage >= 4 && random.nextDouble() < 0.34) { // roughly once every 3s at max stage, not every single tick
            player.damage(1.0);
        }
    }

    // --- summer: heat exhaustion + faster thirst drain ---

    private void tickSummer(Player player, SurvivalPlayerData d, boolean exposed) {
        boolean hotHere = exposed && !player.getWorld().hasStorm();

        if (hotHere) {
            int stageSeconds = Math.max(1, config.getInt("climate.summer.exposure-seconds-per-stage", 30));
            d.heatTickCounter++;
            if (d.heatTickCounter >= stageSeconds) {
                d.heatTickCounter = 0;
                int maxStage = Math.max(0, config.getInt("climate.summer.max-stage", 3));
                if (d.heatStage < maxStage) {
                    d.heatStage++;
                    if (d.heatStage == 1) {
                        player.sendMessage("§eThe heat is getting to you...");
                    }
                }
            }
        } else {
            decayHeat(d);
        }

        applyHeatEffects(player, d);
    }

    private void decayHeat(SurvivalPlayerData d) {
        if (d.heatStage <= 0) {
            d.heatTickCounter = 0;
            d.thirstDrainMultiplier = 1.0;
            return;
        }
        int recoverySeconds = Math.max(1, config.getInt("climate.summer.recovery-seconds-per-stage", 20));
        d.heatTickCounter++;
        if (d.heatTickCounter >= recoverySeconds) {
            d.heatTickCounter = 0;
            d.heatStage--;
        }
        if (d.heatStage <= 0) d.thirstDrainMultiplier = 1.0;
    }

    private void applyHeatEffects(Player player, SurvivalPlayerData d) {
        if (d.heatStage <= 0) {
            d.thirstDrainMultiplier = 1.0;
            return;
        }
        double multiplier = config.getDouble("climate.summer.thirst-drain-multiplier", 1.75);
        d.thirstDrainMultiplier = multiplier;

        if (d.heatStage >= 1) player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, false));
        if (d.heatStage >= 2) player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, true, false));
        if (d.heatStage >= 3) player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 0, true, false));
    }

    // --- spring/fall: pleasant weather ---

    private void tickMildSeason(Player player, SurvivalPlayerData d) {
        if (!isExposedToSky(player)) return; // the buff is meant to reward being out enjoying the weather, not just existing

        int intervalSeconds = Math.max(1, config.getInt("climate.mild-seasons.check-interval-seconds", 60));
        d.mildWeatherTickCounter++;
        if (d.mildWeatherTickCounter < intervalSeconds) return;
        d.mildWeatherTickCounter = 0;

        double chance = config.getDouble("climate.mild-seasons.buff-chance", 0.25);
        if (random.nextDouble() >= chance) return;

        int durationTicks = 20 * Math.max(1, config.getInt("climate.mild-seasons.buff-duration-seconds", 20));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0));
        player.sendMessage("§aThe pleasant weather leaves you feeling refreshed.");
    }

    // --- shared helpers ---

    private boolean isExposedToSky(Player player) {
        Location loc = player.getLocation();
        int highestY = player.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        return loc.getBlockY() >= highestY;
    }

    /** Vanilla's own threshold for "this is cold enough for precipitation to render as snow" -- a storm below this temperature is snow, at or above it is rain. */
    private boolean isColdEnoughToSnow(Location location) {
        double temperature = location.getBlock().getTemperature();
        return temperature < SNOW_TEMPERATURE_THRESHOLD;
    }
}
