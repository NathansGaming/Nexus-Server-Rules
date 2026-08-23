package com.realsociety.glowfusion.miniblocks;

import com.realsociety.glowfusion.GlowFusionPlugin;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Decides which materials a player is allowed to paint onto a mini-block
 * mosaic tile: every material that comes in the full 16 dye colors (wool,
 * concrete, concrete powder, terracotta, glazed terracotta, stained glass -
 * generated here rather than hand-listed, so a color can't accidentally be
 * left out of one of those families), plus whatever "plain" archetype
 * blocks (stone, planks, bricks, ore blocks, etc.) are listed in
 * config.yml under mini-blocks.plain-block-materials.
 *
 * Deliberately does NOT try to auto-detect "is this a plain full block" via
 * Material's own isBlock()/isSolid()/isOccluding() - those are true for
 * plenty of shapes that AREN'T simple full cubes (slabs and stairs are
 * solid; glass is a full cube but not occluding since light passes through
 * it), so there's no single reliable programmatic test that lands on
 * exactly "the archetypes and their colors, not the ones with functions"
 * the way this feature was actually asked for. An explicit, editable list
 * is honest about that instead of pretending to be smart about it - add
 * more names to config.yml freely if something you want is missing.
 */
public final class MiniBlockPalette {

    private static final String[] DYE_FAMILY_SUFFIXES = {
            "_WOOL", "_CONCRETE", "_CONCRETE_POWDER", "_TERRACOTTA", "_GLAZED_TERRACOTTA", "_STAINED_GLASS"
    };

    private static final String[] DYE_COLORS = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };

    private final Set<Material> eligible = new HashSet<>();

    public MiniBlockPalette(GlowFusionPlugin plugin) {
        for (String color : DYE_COLORS) {
            for (String suffix : DYE_FAMILY_SUFFIXES) {
                // A missing dye-family material here just means this server's
                // Paper build doesn't have that particular block (e.g. an
                // older/newer version) - not a config typo, so no warning.
                addIfKnown(color + suffix);
            }
        }

        List<String> plainNames = plugin.getConfig().getStringList("mini-blocks.plain-block-materials");
        for (String name : plainNames) {
            if (!addIfKnown(name)) {
                plugin.getLogger().log(Level.WARNING, "mini-blocks.plain-block-materials: '" + name
                        + "' isn't a material this server recognizes - skipping it. Check the spelling "
                        + "against this server's own Material names.");
            }
        }
    }

    private boolean addIfKnown(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            return false;
        }
        eligible.add(material);
        return true;
    }

    public boolean isEligible(Material material) {
        return eligible.contains(material);
    }
}
