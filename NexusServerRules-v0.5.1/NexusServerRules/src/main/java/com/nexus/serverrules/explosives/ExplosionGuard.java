package com.nexus.serverrules.explosives;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

/**
 * Hard-blocks configured explosives, not by permission but by
 * cancelling the events that make them do anything - so there is no
 * permission node an operator (or anyone) can be granted to bypass
 * this. Deliberately layered rather than relying on a single event:
 *
 *   1. BlockIgniteEvent - stops TNT ever being lit in the first place
 *      (flint & steel, fire spread, lava, another explosion, etc.)
 *   2. EntityExplodeEvent - backstop for primed TNT / TNT minecarts
 *      that got created some other way (command-block "/summon
 *      primed_tnt", a plugin, a dispenser-launched TNT minecart) and
 *      would otherwise skip step 1 entirely. This is what makes it
 *      actually un-bypassable by an operator: even /summon-ing the
 *      entity directly still has to pass through this event to do
 *      any damage.
 *   3. BlockExplodeEvent - covers block-sourced explosions with no
 *      entity involved, which is how a respawn anchor's "used outside
 *      the Nether" explosion is delivered, and is a defensive catch-all
 *      for any other block-type explosion added to the config later.
 *
 * The banned set is config-driven (explosion-prevention.banned-items)
 * so more entries (beds in the Nether/End, for example) can be added
 * later without touching this class - see config.yml comments.
 */
public final class ExplosionGuard implements Listener {

    private final Set<String> banned; // uppercased logical names, e.g. "TNT", "RESPAWN_ANCHOR"

    public ExplosionGuard(Set<String> bannedItems) {
        this.banned = bannedItems;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        String name = event.getBlock().getType().name();
        if (banned.contains(name)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (banned.contains(logicalNameFor(event.getEntityType()))) {
            event.blockList().clear();
            event.setYield(0f);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        String name = event.getBlock().getType().name();
        if (banned.contains(name)) {
            event.blockList().clear();
            event.setYield(0f);
            event.setCancelled(true);
        }
    }

    /** Maps the entity types TNT actually explodes as back onto the "TNT" config entry. */
    private String logicalNameFor(EntityType type) {
        return switch (type) {
            case PRIMED_TNT, MINECART_TNT -> "TNT";
            default -> type.name().toUpperCase(Locale.ROOT);
        };
    }
}
