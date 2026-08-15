package com.nexusuniverse.realms;

import com.nexusuniverse.realms.land.ChunkUpgradeTier;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RealmsConfig {

    private final JavaPlugin plugin;

    public RealmsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        // saveDefaultConfig() only writes config.yml the very first time this plugin is
        // installed -- copyDefaults(true) + saveConfig() merges in anything a later update adds
        // to an already-existing config.yml, instead of it silently never showing up.
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    // --- homes ---

    /** Falls back to this if the player doesn't hold nexusrealms.homes.admin. */
    public int homesMaxDefault() {
        return Math.max(0, plugin.getConfig().getInt("homes.max-default", 1));
    }

    public int homesMaxAdmin() {
        return Math.max(0, plugin.getConfig().getInt("homes.max-admin", 5));
    }

    // --- country-level (chunk) claiming ---

    /** Every new team starts with this many chunks claimable, free, before any upgrade purchase. */
    public int landStartingChunks() {
        return Math.max(0, plugin.getConfig().getInt("land.starting-chunks", 10));
    }

    /**
     * The highest chunk-cap a team can reach by purchasing normal-tier upgrades (see
     * landUpgradeTiersNormal()). 0 means unlimited -- kept for backward compatibility with
     * servers that had this key at 0 before the upgrade system existed.
     */
    public int maxChunksPerTeam() {
        return Math.max(0, plugin.getConfig().getInt("land.max-chunks-per-team", 1000));
    }

    /** Same idea as maxChunksPerTeam(), but the higher ceiling available through op-tier pricing. */
    public int maxChunksPerTeamOp() {
        return Math.max(0, plugin.getConfig().getInt("land.max-chunks-per-team-op", 10000));
    }

    /** Safety cap on /realms admin claim's radius argument (in chunks) -- guards against an accidental huge number claiming (and disk-writing) an enormous area in one command. */
    public int landAdminBulkClaimMaxRadius() {
        return Math.max(0, plugin.getConfig().getInt("land.admin-bulk-claim-max-radius", 25));
    }

    /** The player-facing equivalent of landAdminBulkClaimMaxRadius() -- how many chunks /realms claim radius is allowed to actually claim in one run, regardless of the radius requested. Unlike the admin version, this does NOT auto-raise the team's chunk cap -- it still stops at whatever cap the team currently has, same as a normal single-chunk claim, so it can't be used to skip the paid /realms upgrade tiers. */
    public int landPlayerRadiusClaimMaxChunks() {
        return Math.max(0, plugin.getConfig().getInt("land.player-radius-claim-max-chunks", 50));
    }

    /** Separate from the chunk cap above -- bounds how large a radius a player is even allowed to request, purely so a huge number (e.g. 5000) can't make the server loop over millions of chunk lookups searching for 50 unclaimed ones. */
    public int landPlayerRadiusClaimMaxSearchRadius() {
        return Math.max(0, plugin.getConfig().getInt("land.player-radius-claim-max-search-radius", 15));
    }

    /** The normal purchase ladder -- flat price to reach each chunk-cap tier, sorted ascending by chunk count. */
    public List<ChunkUpgradeTier> landUpgradeTiersNormal() {
        return parseTiers("land.upgrade-tiers");
    }

    /** The discounted, higher-ceiling ladder available to players holding nexusrealms.chunks.optier. */
    public List<ChunkUpgradeTier> landUpgradeTiersOp() {
        return parseTiers("land.upgrade-tiers-op");
    }

    private List<ChunkUpgradeTier> parseTiers(String path) {
        List<ChunkUpgradeTier> tiers = new ArrayList<>();
        List<Map<?, ?>> rows = plugin.getConfig().getMapList(path);
        for (Map<?, ?> row : rows) {
            Object chunksValue = row.get("chunks");
            Object priceValue = row.get("price");
            if (chunksValue == null || priceValue == null) continue;
            int chunks = ((Number) chunksValue).intValue();
            double price = ((Number) priceValue).doubleValue();
            tiers.add(new ChunkUpgradeTier(chunks, price));
        }
        tiers.sort(Comparator.comparingInt(ChunkUpgradeTier::chunks));
        return Collections.unmodifiableList(tiers);
    }

    // --- open terrain (unclaimed land) default rules -- free for anyone, no claim required ---

    public boolean openTerrainAllowBuild() {
        return plugin.getConfig().getBoolean("open-terrain.allow-build", true);
    }

    public boolean openTerrainAllowContainers() {
        return plugin.getConfig().getBoolean("open-terrain.allow-containers", true);
    }

    public boolean openTerrainAllowDoors() {
        return plugin.getConfig().getBoolean("open-terrain.allow-doors", true);
    }

    public boolean openTerrainAllowPvp() {
        return plugin.getConfig().getBoolean("open-terrain.allow-pvp", false);
    }

    public boolean openTerrainAllowElytra() {
        return plugin.getConfig().getBoolean("open-terrain.allow-elytra", true);
    }

    public void setOpenTerrainSetting(String key, boolean value) {
        plugin.getConfig().set("open-terrain." + key, value);
        plugin.saveConfig();
    }

    // --- personal claims (a trusted member's small claim inside their own team's territory) ---

    public int personalClaimRadius() {
        return Math.max(1, plugin.getConfig().getInt("land.personal-claim.radius", 15));
    }

    public int personalClaimMaxPerMember() {
        return Math.max(0, plugin.getConfig().getInt("land.personal-claim.max-per-member", 2));
    }

    // --- worldedit-inspired region editing ---

    public org.bukkit.Material worldEditWandMaterial() {
        String name = plugin.getConfig().getString("worldedit.wand-material", "GOLDEN_AXE");
        try {
            return org.bukkit.Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return org.bukkit.Material.GOLDEN_AXE;
        }
    }

    /** Applies to every /redit operation, admin or not -- a safety net against one accidental huge selection freezing the server. */
    public long worldEditMaxVolume() {
        return Math.max(1, plugin.getConfig().getLong("worldedit.max-volume", 250_000));
    }

    public int worldEditUndoDepth() {
        return Math.max(1, plugin.getConfig().getInt("worldedit.undo-depth", 5));
    }

    // --- territory border notifications (popup when crossing into/out of claimed land) ---

    public boolean borderNotifyEnabled() {
        return plugin.getConfig().getBoolean("border.enabled", true);
    }

    /** "title" (big popup), "actionbar" (small message above the hotbar), or "chat". Falls back to "title" for anything unrecognized. */
    public String borderNotifyStyle() {
        String style = plugin.getConfig().getString("border.style", "title");
        return style == null ? "title" : style.toLowerCase();
    }

    public int borderTitleFadeInTicks() {
        return Math.max(0, plugin.getConfig().getInt("border.title-fade-in-ticks", 10));
    }

    public int borderTitleStayTicks() {
        return Math.max(0, plugin.getConfig().getInt("border.title-stay-ticks", 40));
    }

    public int borderTitleFadeOutTicks() {
        return Math.max(0, plugin.getConfig().getInt("border.title-fade-out-ticks", 10));
    }

    /** Shown entering a chunk claimed by a team the player is NOT a member of. Blank disables just this message. */
    public String borderMessageEnteringOther() {
        return plugin.getConfig().getString("border.messages.entering-other", "&e&lEntering {team}'s territory");
    }

    /** Shown entering a chunk claimed by the player's OWN team. Blank disables just this message. */
    public String borderMessageEnteringOwn() {
        return plugin.getConfig().getString("border.messages.entering-own", "&a&lEntering your territory (&f{team}&a)");
    }

    /** Shown entering unclaimed open terrain (from anyone's territory, or on first join). Blank disables just this message. */
    public String borderMessageEnteringOpenTerrain() {
        return plugin.getConfig().getString("border.messages.entering-open-terrain", "&7&lEntering open terrain");
    }
}
