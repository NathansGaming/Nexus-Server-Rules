package com.nexusuniverse.dreams.dream;

import com.nexusuniverse.dreams.config.NexusDreamsConfig;
import com.nexusuniverse.dreams.integration.SurvivalBridge;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a player's current state into a DreamOutcome. Two signal tiers:
 *
 *  - Rich (NexusSurvival linked): thirst, rad-oxygen, hygiene, and
 *    infection severity, via SurvivalBridge.
 *  - Fallback (no NexusSurvival): vanilla health%, hunger%, and whether
 *    any conventionally-bad potion effect is currently active.
 *
 * The overall average of whichever signals are available becomes the
 * mechanical severity (strain, then bucketed into a DreamTier); the
 * single WORST signal becomes the narrative flavor - so a player who's
 * otherwise fine but critically irradiated gets a radiation-flavored
 * dream even if the math says only "troubled," not "nightmare."
 */
public final class DreamAssessor {

    private static final Set<PotionEffectType> BAD_EFFECTS = Set.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.HUNGER,
            PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.SLOWNESS, PotionEffectType.DARKNESS
    );

    private record Signal(DreamFlavor flavor, double goodness) {
    }

    public DreamOutcome assess(Player player, SurvivalBridge bridge, NexusDreamsConfig config) {
        List<Signal> signals = new ArrayList<>();
        UUID id = player.getUniqueId();

        if (bridge.isAvailable()) {
            signals.add(new Signal(DreamFlavor.THIRST, bridge.thirstFraction(id)));
            signals.add(new Signal(DreamFlavor.RADIATION, bridge.radOxygenFraction(id)));
            signals.add(new Signal(DreamFlavor.HYGIENE, bridge.hygieneFraction(id)));
            signals.add(new Signal(DreamFlavor.DISEASE, diseaseGoodness(bridge, id)));
        } else {
            signals.add(new Signal(DreamFlavor.VANILLA, healthGoodness(player)));
            signals.add(new Signal(DreamFlavor.VANILLA, hungerGoodness(player)));
            signals.add(new Signal(DreamFlavor.VANILLA, activeEffectsGoodness(player)));
        }

        double averageGoodness = signals.stream().mapToDouble(Signal::goodness).average().orElse(1.0);
        double strain = 1.0 - averageGoodness;

        Signal worst = signals.stream().min(Comparator.comparingDouble(Signal::goodness)).orElse(signals.get(0));

        return new DreamOutcome(bucket(strain, config), worst.flavor(), strain);
    }

    /** 1.0 healthy, stepping down with each severity tier (0=Mild..3=Critical); 1.0 if not infected at all. */
    private double diseaseGoodness(SurvivalBridge bridge, UUID id) {
        if (!bridge.isInfected(id)) return 1.0;
        int severity = Math.max(0, Math.min(3, bridge.infectionSeverity(id)));
        return switch (severity) {
            case 0 -> 0.6;
            case 1 -> 0.4;
            case 2 -> 0.2;
            default -> 0.0;
        };
    }

    private double healthGoodness(Player player) {
        double max = player.getMaxHealth();
        return max <= 0 ? 1.0 : clamp(player.getHealth() / max);
    }

    private double hungerGoodness(Player player) {
        return clamp(player.getFoodLevel() / 20.0);
    }

    private double activeEffectsGoodness(Player player) {
        boolean anyBad = player.getActivePotionEffects().stream()
                .anyMatch(effect -> BAD_EFFECTS.contains(effect.getType()));
        return anyBad ? 0.3 : 1.0;
    }

    private DreamTier bucket(double strain, NexusDreamsConfig config) {
        double peacefulMax = config.getDouble("dream.peaceful-max-strain", 0.25);
        double restlessMax = config.getDouble("dream.restless-max-strain", 0.50);
        double troubledMax = config.getDouble("dream.troubled-max-strain", 0.75);

        if (strain <= peacefulMax) return DreamTier.PEACEFUL;
        if (strain <= restlessMax) return DreamTier.RESTLESS;
        if (strain <= troubledMax) return DreamTier.TROUBLED;
        return DreamTier.NIGHTMARE;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
