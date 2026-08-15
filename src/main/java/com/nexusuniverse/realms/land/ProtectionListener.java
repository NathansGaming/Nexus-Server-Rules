package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.RealmsConfig;
import com.nexusuniverse.realms.team.Team;
import com.nexusuniverse.realms.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Three nested layers, checked in this order for any protected action:
 *  1. Inside a personal claim? -- that claim's own owner/trust list decides, full stop. Even a
 *     fellow team member with no personal trust grant is blocked here; team membership doesn't
 *     override an individual claim's own trust list.
 *  2. Not inside a personal claim, but inside the team's claimed territory? -- team membership
 *     decides for building; the team's own configurable settings (Team#allowOutsiderDoors/
 *     Containers/Pvp) decide for everything else, for non-members specifically.
 *  3. Unclaimed open terrain -- governed by RealmsConfig's open-terrain.* defaults (build/
 *     containers/doors/pvp/elytra), server-wide rather than per-team since nobody owns it. Build/
 *     containers/doors/elytra are all ON by default -- open terrain means free to use, no claim
 *     required. PvP is the one exception, off by default here.
 *
 * Ahead of all three: anyone currently bypassing (AdminBypassManager -- on automatically for
 * nexusrealms.bypass holders) skips every one of these checks outright, for build/interact/pvp
 * alike, everywhere. That's deliberate -- bypass means exactly "act like none of this exists,"
 * not "one extra layer that still asks permission."
 */
public class ProtectionListener implements Listener {

    private final TeamManager teams;
    private final LandClaimManager land;
    private final PersonalClaimManager personalClaims;
    private final RealmsConfig config;
    private final AdminBypassManager bypass;

    public ProtectionListener(TeamManager teams, LandClaimManager land, PersonalClaimManager personalClaims, RealmsConfig config, AdminBypassManager bypass) {
        this.teams = teams;
        this.land = land;
        this.personalClaims = personalClaims;
        this.config = config;
        this.bypass = bypass;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't break blocks here.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't place blocks here.");
        }
    }

    /**
     * Bucket placement (water, lava, powder snow -- one shared vanilla event covers all of them)
     * is its own separate event from BlockPlaceEvent above, NOT a variant of it -- emptying a
     * bucket never fires BlockPlaceEvent at all, so without this handler someone could walk onto
     * another team's claimed land and freely dump water or lava with zero protection, completely
     * bypassing the exact same canBuild() check that already stops them placing a normal block.
     * The block that's actually about to be modified is the clicked block's neighbor in the
     * direction of blockFace, not the clicked block itself.
     */
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        if (!canBuild(event.getPlayer(), location)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't place that here.");
        }
    }

    /** Same idea in reverse -- scooping water back up out of someone's claimed land (draining a moat, etc.) is exactly as much a build action as placing it, so it gets the same check. */
    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't take that from here.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isProtectedInteraction(block)) return;

        if (!canInteract(event.getPlayer(), block.getLocation(), isContainer(block))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't use that here.");
        }
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (bypass.isBypassing(attacker)) return; // no restriction at all -- fight anywhere

        Location location = victim.getLocation();
        PersonalClaim personalClaim = personalClaims.claimAt(location);
        if (personalClaim != null) {
            // fighting inside someone's personal claim needs BUILDER trust in it (same bar as
            // building there) from BOTH participants -- if neither is trusted, no fight happens
            boolean attackerAllowed = personalClaim.hasAccess(attacker.getUniqueId(), ClaimPermission.BUILDER);
            boolean victimAllowed = personalClaim.hasAccess(victim.getUniqueId(), ClaimPermission.BUILDER);
            if (!attackerAllowed || !victimAllowed) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You can't fight here.");
            }
            return;
        }

        UUID teamId = land.ownerOf(location.getChunk());
        if (teamId == null) {
            // unclaimed open terrain -- governed by the server-wide default, not a per-team setting
            if (!config.openTerrainAllowPvp()) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "PvP isn't allowed on open terrain.");
            }
            return;
        }

        Team team = teams.byId(teamId);
        if (team == null) return;

        boolean bothMembers = team.isMember(attacker.getUniqueId()) && team.isMember(victim.getUniqueId());
        if (!bothMembers && !team.allowOutsiderPvp()) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PvP isn't allowed in " + team.name() + "'s territory.");
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return; // only care about starting a glide, not stopping one
        if (!(event.getEntity() instanceof Player player)) return;

        Location location = player.getLocation();
        UUID teamId = land.ownerOf(location.getChunk());
        if (teamId != null) return; // elytra restriction is open-terrain-only for now

        if (!config.openTerrainAllowElytra()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Elytra flight isn't allowed on open terrain.");
        }
    }

    private boolean canBuild(Player player, Location location) {
        if (bypass.isBypassing(player)) return true;

        PersonalClaim personalClaim = personalClaims.claimAt(location);
        if (personalClaim != null) {
            return personalClaim.hasAccess(player.getUniqueId(), ClaimPermission.BUILDER);
        }

        var chunk = location.getChunk();
        UUID ownerId = land.ownerOf(chunk);
        if (ownerId == null) return config.openTerrainAllowBuild(); // unclaimed open terrain -- free to build by default

        Team team = teams.byId(ownerId);
        return team != null && team.isMember(player.getUniqueId());
    }

    private boolean canInteract(Player player, Location location, boolean isContainer) {
        if (bypass.isBypassing(player)) return true;

        PersonalClaim personalClaim = personalClaims.claimAt(location);
        if (personalClaim != null) {
            return personalClaim.hasAccess(player.getUniqueId(), ClaimPermission.VISITOR);
        }

        UUID ownerId = land.ownerOf(location.getChunk());
        if (ownerId == null) {
            // unclaimed open terrain
            return isContainer ? config.openTerrainAllowContainers() : config.openTerrainAllowDoors();
        }

        Team team = teams.byId(ownerId);
        if (team == null) return true;
        if (team.isMember(player.getUniqueId())) return true;

        return isContainer ? team.allowOutsiderContainers() : team.allowOutsiderDoors();
    }

    private boolean isContainer(Block block) {
        return block.getState() instanceof Container;
    }

    private boolean isProtectedInteraction(Block block) {
        return isContainer(block) || isDoorLike(block.getType());
    }

    private boolean isDoorLike(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON") || name.equals("LEVER");
    }
}
