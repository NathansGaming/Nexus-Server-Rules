package com.nexusuniverse.dreams.dream;

import com.nexusuniverse.dreams.config.NexusDreamsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Applies the title/subtitle text and mechanical potion effects for a resolved DreamOutcome. */
public final class DreamPresenter {

    private final Logger logger;

    public DreamPresenter(Logger logger) {
        this.logger = logger;
    }

    public void present(Player player, DreamOutcome outcome, NexusDreamsConfig config) {
        String tierKey = outcome.tier().name().toLowerCase();
        String line = config.randomFlavorLine(tierKey, outcome.flavor().configKey());

        showTitle(player, titleFor(outcome.tier()), line);
        applyEffects(player, outcome.tier(), config);
    }

    private String titleFor(DreamTier tier) {
        return switch (tier) {
            case PEACEFUL -> "§bPeaceful Dream";
            case RESTLESS -> "§7Restless Sleep";
            case TROUBLED -> "§6Troubled Dream";
            case NIGHTMARE -> "§4Nightmare";
        };
    }

    private void showTitle(Player player, String titleText, String subtitleText) {
        Component title = LegacyComponentSerializer.legacySection().deserialize(titleText);
        Component subtitle = LegacyComponentSerializer.legacySection().deserialize("§f" + subtitleText);
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1));
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void applyEffects(Player player, DreamTier tier, NexusDreamsConfig config) {
        switch (tier) {
            case PEACEFUL -> apply(player, config, "dream.peaceful-effect", "dream.peaceful-effect-seconds",
                    "dream.peaceful-effect-amplifier", PotionEffectType.REGENERATION, 20, 0);
            case RESTLESS -> { /* flavor only, no mechanical effect */ }
            case TROUBLED -> apply(player, config, "dream.troubled-effect", "dream.troubled-effect-seconds",
                    "dream.troubled-effect-amplifier", PotionEffectType.NAUSEA, 15, 0);
            case NIGHTMARE -> {
                apply(player, config, "dream.nightmare-effect", "dream.nightmare-effect-seconds",
                        "dream.nightmare-effect-amplifier", PotionEffectType.DARKNESS, 20, 0);
                apply(player, config, "dream.nightmare-secondary-effect", "dream.nightmare-secondary-seconds",
                        "dream.nightmare-secondary-amplifier", PotionEffectType.WEAKNESS, 30, 0);
                playNightmareSound(player, config);
            }
        }
    }

    private void apply(Player player, NexusDreamsConfig config, String typePath, String secondsPath,
                        String amplifierPath, PotionEffectType fallbackType, int fallbackSeconds, int fallbackAmplifier) {
        String typeName = config.getString(typePath, fallbackType.getName());
        PotionEffectType type = typeName != null ? PotionEffectType.getByName(typeName.toUpperCase()) : null;
        if (type == null) type = fallbackType;

        int seconds = config.getInt(secondsPath, fallbackSeconds);
        int amplifier = config.getInt(amplifierPath, fallbackAmplifier);

        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true));
    }

    private void playNightmareSound(Player player, NexusDreamsConfig config) {
        String soundName = config.getString("dream.nightmare-sound", "ENTITY_ELDER_GUARDIAN_CURSE");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.6f, 0.8f);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "[NexusDreams] Unknown sound '" + soundName + "' for dream.nightmare-sound - skipped.", e);
        }
    }
}
