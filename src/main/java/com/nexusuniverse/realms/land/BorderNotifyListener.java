package com.nexusuniverse.realms.land;

import com.nexusuniverse.realms.RealmsConfig;
import com.nexusuniverse.realms.team.Team;
import com.nexusuniverse.realms.team.TeamManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Pops up a message ("Entering So-and-so's territory") whenever a player crosses a chunk that
 * changes who owns the land -- walking across it (PlayerMoveEvent) or warping into it
 * (PlayerTeleportEvent, e.g. /home). Only fires on an actual chunk-ownership change, not every
 * step -- comparing the previous chunk's owner against the new chunk's owner, so walking around
 * inside the same team's territory (or wandering unclaimed open terrain) stays silent.
 *
 * Three distinct messages, independently configurable and independently disable-able (blank =
 * off) in RealmsConfig: entering a team you're NOT on (the main ask -- a heads-up you've stepped
 * onto someone else's claim), entering your OWN team's land, and entering unclaimed open terrain.
 */
public class BorderNotifyListener implements Listener {

    private final TeamManager teams;
    private final LandClaimManager land;
    private final RealmsConfig config;

    public BorderNotifyListener(TeamManager teams, LandClaimManager land, RealmsConfig config) {
        this.teams = teams;
        this.land = land;
        this.config = config;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        checkCrossing(event.getPlayer(), event.getFrom(), to);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        checkCrossing(event.getPlayer(), event.getFrom(), to);
    }

    private void checkCrossing(Player player, Location from, Location to) {
        if (!config.borderNotifyEnabled()) return;
        if (from == null || from.getWorld() == null || to.getWorld() == null) return;

        Chunk fromChunk = from.getChunk();
        Chunk toChunk = to.getChunk();
        boolean sameChunk = fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()
                && from.getWorld().equals(to.getWorld());
        if (sameChunk) return;

        UUID fromOwner = land.ownerOf(fromChunk);
        UUID toOwner = land.ownerOf(toChunk);
        if (Objects.equals(fromOwner, toOwner)) return; // same owner (including both unclaimed) -- no real border crossed

        String raw;
        String teamName = null;
        if (toOwner == null) {
            raw = config.borderMessageEnteringOpenTerrain();
        } else {
            Team team = teams.byId(toOwner);
            teamName = team != null ? team.name() : "an unknown team";
            boolean own = team != null && team.isMember(player.getUniqueId());
            raw = own ? config.borderMessageEnteringOwn() : config.borderMessageEnteringOther();
        }

        if (raw == null || raw.isEmpty()) return; // this specific message is disabled
        if (teamName != null) raw = raw.replace("{team}", teamName);
        send(player, ChatColor.translateAlternateColorCodes('&', raw));
    }

    private void send(Player player, String message) {
        switch (config.borderNotifyStyle()) {
            case "actionbar" -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            case "chat" -> player.sendMessage(message);
            default -> player.sendTitle(message, "", config.borderTitleFadeInTicks(),
                    config.borderTitleStayTicks(), config.borderTitleFadeOutTicks());
        }
    }
}
