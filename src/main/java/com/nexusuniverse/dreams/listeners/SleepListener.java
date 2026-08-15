package com.nexusuniverse.dreams.listeners;

import com.nexusuniverse.dreams.config.NexusDreamsConfig;
import com.nexusuniverse.dreams.dream.DreamAssessor;
import com.nexusuniverse.dreams.dream.DreamOutcome;
import com.nexusuniverse.dreams.dream.DreamPresenter;
import com.nexusuniverse.dreams.integration.SurvivalBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hooks TimeSkipEvent rather than PlayerBedEnterEvent/PlayerBedLeaveEvent
 * on purpose - those fire for every individual get-in/get-out of bed,
 * including someone who climbs in and immediately climbs back out, or a
 * bed that gets interrupted. TimeSkipEvent with SkipReason.NIGHT_SKIP
 * fires exactly once, exactly when enough players sleeping actually
 * causes the server to skip to morning - the real "the night happened"
 * moment this plugin cares about. At that point every player still
 * isSleeping() in the affected world just genuinely slept through it.
 */
public final class SleepListener implements Listener {

    private final NexusDreamsConfig config;
    private final SurvivalBridge bridge;
    private final DreamAssessor assessor;
    private final DreamPresenter presenter;

    private final Map<UUID, Long> lastDreamEpoch = new LinkedHashMap<>();

    public SleepListener(NexusDreamsConfig config, SurvivalBridge bridge, DreamAssessor assessor, DreamPresenter presenter) {
        this.config = config;
        this.bridge = bridge;
        this.assessor = assessor;
        this.presenter = presenter;
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (!config.getBoolean("dream.enabled", true)) return;
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) return;

        for (Player player : event.getWorld().getPlayers()) {
            if (!player.isSleeping()) continue;
            if (onCooldown(player.getUniqueId())) continue;

            dream(player);
        }
    }

    /** Forces a dream for a player right now, bypassing the sleeping check and cooldown. Used by /nexusdreams trigger. */
    public void forceDream(Player player) {
        dream(player);
    }

    private void dream(Player player) {
        DreamOutcome outcome = assessor.assess(player, bridge, config);
        presenter.present(player, outcome, config);
        lastDreamEpoch.put(player.getUniqueId(), Instant.now().getEpochSecond());
    }

    private boolean onCooldown(UUID playerId) {
        Long last = lastDreamEpoch.get(playerId);
        if (last == null) return false;
        long cooldownSeconds = config.getInt("dream.cooldown-seconds", 60);
        return Instant.now().getEpochSecond() - last < cooldownSeconds;
    }
}
