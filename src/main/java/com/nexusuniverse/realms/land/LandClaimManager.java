package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.team.Team;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A team's territory is just the set of chunks it's claimed -- this is the "country" layer:
 * large, chunk-grid claims (same shape as most land-claim plugins in this genre) owned by a
 * whole team rather than an individual. PersonalClaimManager handles the smaller, radius-based
 * claims that get staked INSIDE a team's already-claimed chunks by individual trusted members.
 */
public class LandClaimManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<ChunkKey, UUID> claimedChunks = new HashMap<>();

    public LandClaimManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    public boolean isClaimed(Chunk chunk) {
        return claimedChunks.containsKey(keyOf(chunk));
    }

    public UUID ownerOf(Chunk chunk) {
        return claimedChunks.get(keyOf(chunk));
    }

    /** @return false if the chunk is already claimed by anyone (including this same team). */
    public boolean claim(Chunk chunk, Team team) {
        ChunkKey key = keyOf(chunk);
        if (claimedChunks.containsKey(key)) return false;
        claimedChunks.put(key, team.id());
        save();
        return true;
    }

    /** @return false if the chunk wasn't claimed by this team (or wasn't claimed at all). */
    public boolean unclaim(Chunk chunk, Team team) {
        ChunkKey key = keyOf(chunk);
        if (!team.id().equals(claimedChunks.get(key))) return false;
        claimedChunks.remove(key);
        save();
        return true;
    }

    public int chunkCountFor(UUID teamId) {
        int count = 0;
        for (UUID owner : claimedChunks.values()) {
            if (owner.equals(teamId)) count++;
        }
        return count;
    }

    /** Frees every chunk a disbanded team owned. */
    public void releaseAll(UUID teamId) {
        claimedChunks.values().removeIf(owner -> owner.equals(teamId));
        save();
    }

    /**
     * Unclaims every chunk this team currently owns, e.g. for /realms unclaim all. Unlike
     * releaseAll() (used on disband) this is a normal, player-triggered action, so it reports
     * back how many chunks it actually freed.
     * @return the number of chunks that were unclaimed.
     */
    public int unclaimAll(UUID teamId) {
        int before = claimedChunks.size();
        claimedChunks.values().removeIf(owner -> owner.equals(teamId));
        int unclaimed = before - claimedChunks.size();
        if (unclaimed > 0) save();
        return unclaimed;
    }

    private ChunkKey keyOf(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<ChunkKey, UUID> entry : claimedChunks.entrySet()) {
            flat.put(entry.getKey().toString(), entry.getValue().toString());
        }
        data.set("claims", flat);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "NexusRealms: failed to save claims.yml", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection("claims");
        if (section == null) return;

        for (String keyString : section.getKeys(false)) {
            try {
                ChunkKey key = ChunkKey.parse(keyString);
                UUID teamId = UUID.fromString(section.getString(keyString));
                claimedChunks.put(key, teamId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "NexusRealms: skipped a corrupt claim entry (" + keyString + ") in claims.yml", e);
            }
        }
    }
}
