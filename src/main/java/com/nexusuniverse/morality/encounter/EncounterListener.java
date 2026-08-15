package com.nexusuniverse.morality.encounter;

import com.nexusuniverse.morality.config.NexusMoralityConfig;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class EncounterListener implements Listener {

    private final EncounterManager manager;
    private final NexusMoralityConfig config;

    public EncounterListener(EncounterManager manager, NexusMoralityConfig config) {
        this.manager = manager;
        this.config = config;
    }

    /**
     * The "help" path: right-clicking a tagged survivor with an accepted
     * item offers help and starts the proximity hold (see
     * EncounterManager#tickHelpProgress). Always cancelled once we
     * recognize the entity as one of ours, accepted item or not - a
     * Profession.NONE villager has no real trades, but we don't want to
     * rely on that; full control over the interaction avoids any vanilla
     * GUI surprises.
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // avoid double-firing for the paired off-hand event

        Entity clicked = event.getRightClicked();
        if (!manager.isTaggedSurvivor(clicked)) return;
        event.setCancelled(true);

        SurvivorEncounter encounter = manager.get(clicked.getUniqueId());
        if (encounter == null || encounter.isResolved()) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType() == Material.AIR) return; // nothing offered - ignore quietly, no spam

        Material type = inHand.getType();
        Set<Material> accepted = config.getMaterialSet("encounter.accepted-help-items");
        if (!type.isEdible() && !accepted.contains(type)) return; // not something that reads as "help" - ignore

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (inHand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                inHand.setAmount(inHand.getAmount() - 1);
            }
        }

        manager.beginHelping(encounter, player);
    }

    /**
     * The "loot and leave" path: any hit on a tagged survivor that a
     * player is responsible for (melee or a projectile they fired)
     * resolves the encounter immediately, on the first hit - not on
     * eventual death. This is deliberate: waiting for a kill would let a
     * player back out of a lethal-but-unfinished hit with no
     * consequence, and the "attack it at all" moment is the real moral
     * choice, not whether the weapon happened to one-shot it.
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!manager.isTaggedSurvivor(victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return; // hurt by something other than a player - not a moral choice, see onDeath

        SurvivorEncounter encounter = manager.get(victim.getUniqueId());
        if (encounter == null || encounter.isResolved()) return;

        manager.resolveLooted(encounter, victim, attacker);
    }

    /**
     * Fallback for a survivor that dies some other way entirely - fall
     * damage, drowning, a hostile mob wandering by - without ever going
     * through onDamage's player-attack path. Treated as a quiet
     * non-event: no karma change either direction, just cleanup. If this
     * fires for an encounter onDamage already resolved, EncounterManager
     * already removed it from the active map, so get() returns null and
     * handleUnexpectedDeath() below is a safe no-op.
     */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!manager.isTaggedSurvivor(entity)) return;
        manager.handleUnexpectedDeath(entity.getUniqueId());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
