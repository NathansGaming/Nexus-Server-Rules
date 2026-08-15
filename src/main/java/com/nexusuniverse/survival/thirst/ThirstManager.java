package com.nexusuniverse.survival.thirst;

import com.nexusuniverse.survival.data.PlayerDataManager;
import com.nexusuniverse.survival.data.SurvivalPlayerData;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ThirstManager {

    private static final double MAX_THIRST = 180.0;
    private static final double DRINK_RESTORE = 54.0;
    private static final double RAW_DRINK_RESTORE = 27.0; // half -- untreated water isn't as reliably absorbed
    // ThirstItems.CANTEEN_MAX_CHARGES (6) * this = MAX_THIRST exactly -- a full Canteen is worth
    // one full bar of water, just spread across sips instead of gulped in one go like a bottle
    private static final double CANTEEN_SIP_RESTORE = 30.0;
    private static final int DECAY_INTERVAL_SECONDS = 5; // lose 1 thirst every 5s (same 15-minute drain-to-empty as before, just finer-grained)
    private static final int DEHYDRATION_DAMAGE_INTERVAL_SECONDS = 4;

    private final PlayerDataManager playerData;

    public ThirstManager(PlayerDataManager playerData) {
        this.playerData = playerData;
    }

    public void drink(Player player) {
        restore(player, DRINK_RESTORE);
        player.sendMessage("§bYou drink some water. Thirst restored.");
    }

    public void drinkRaw(Player player) {
        restore(player, RAW_DRINK_RESTORE);
        player.sendMessage("§7You drink some untreated water. Thirst partially restored.");
    }

    /** One sip from a Canteen -- weaker than a full Water Bottle, but doesn't use the item up. */
    public void drinkCanteenSip(Player player) {
        restore(player, CANTEEN_SIP_RESTORE);
        player.sendMessage("§bYou take a sip from your canteen. Thirst restored.");
    }

    private void restore(Player player, double amount) {
        SurvivalPlayerData d = playerData.get(player);
        d.thirst = Math.min(MAX_THIRST, d.thirst + amount);
    }

    /** Called every second from the central tick loop. */
    public void tick(Player player) {
        SurvivalPlayerData d = playerData.get(player);

        // scaled by d.thirstDrainMultiplier (set by ClimateManager -- >1.0 during summer heat
        // exposure) rather than always incrementing by exactly 1, so "thirst drains faster in
        // summer" is a real rate change, not a separate second drain mechanism layered on top
        d.thirstTickCounter += d.thirstDrainMultiplier;
        if (d.thirstTickCounter >= DECAY_INTERVAL_SECONDS) {
            d.thirstTickCounter -= DECAY_INTERVAL_SECONDS; // subtract rather than reset, keeps fractional overshoot from a >1.0 multiplier accurate
            d.thirst = Math.max(0, d.thirst - 1);
        }

        if (d.thirst <= 0) {
            d.dehydrationDamageTickCounter++;
            if (d.dehydrationDamageTickCounter >= DEHYDRATION_DAMAGE_INTERVAL_SECONDS) {
                d.dehydrationDamageTickCounter = 0;
                player.damage(1.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false));
            }
        } else {
            d.dehydrationDamageTickCounter = 0;
        }

        updateBar(player, d);
    }

    private void updateBar(Player player, SurvivalPlayerData d) {
        double progress = d.thirst / MAX_THIRST;
        d.thirstBar.setProgress(Math.max(0, Math.min(1, progress)));
        d.thirstBar.setTitle("§bThirst: " + (int) d.thirst + "/" + (int) MAX_THIRST);
        d.thirstBar.setColor(d.thirst <= 36 ? BarColor.RED : BarColor.BLUE);
    }
}
