package com.nexusuniverse.chroma.color;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The default color table NexusChroma ships with. It's written to
 * colors.yml the very first time the plugin starts on a server, and
 * NEVER read again after that -- every startup after the first reads
 * only from colors.yml, so editing this class later has no effect
 * unless colors.yml is deleted. Think of this as a seed, not a live
 * source of truth.
 *
 * Coverage and honesty about confidence:
 *  - ~70 hand-picked stone / dirt / wood / ore blocks, eyeballed
 *    against their real in-game texture as closely as I could without
 *    a running client to sample pixels from. Good starting points, not
 *    guaranteed pixel-exact.
 *  - All 16 WOOL colors use Mojang's actual published dye hex values
 *    -- these are the entries I'm most confident are accurate.
 *  - All 16 CONCRETE and all 16 TERRACOTTA colors are NOT hand-typed
 *    -- I don't trust my memory of those exact hexes enough to present
 *    them as authoritative, so instead they're derived programmatically
 *    from the WOOL value of the same color name (see ColorMath):
 *    concrete = more saturated + a touch darker, terracotta =
 *    desaturated + warmed + a touch darker. They'll be in the right
 *    neighborhood, not pixel-verified.
 *
 * Every single entry can be retuned after first launch with
 * "/nexuschroma add <material> <hex>" -- no rebuild needed.
 */
public final class DefaultColors {

    private DefaultColors() {
    }

    /** {dye name, hex} -- the 16 standard Minecraft dye colors, used directly for *_WOOL and as the base for deriving *_CONCRETE / *_TERRACOTTA. */
    private static final String[][] DYES = {
            {"WHITE", "F9FFFE"}, {"ORANGE", "F9801D"}, {"MAGENTA", "C74EBD"},
            {"LIGHT_BLUE", "3AB3DA"}, {"YELLOW", "FED83D"}, {"LIME", "80C71F"},
            {"PINK", "F38BAA"}, {"GRAY", "474F52"}, {"LIGHT_GRAY", "9D9D97"},
            {"CYAN", "169C9C"}, {"PURPLE", "8932B8"}, {"BLUE", "3C44AA"},
            {"BROWN", "835432"}, {"GREEN", "5E7C16"}, {"RED", "B02E26"},
            {"BLACK", "1D1D21"}
    };

    public static Map<Material, String> build() {
        Map<Material, String> map = new LinkedHashMap<>();

        // -- stone / mineral family --
        map.put(Material.STONE, "7D7D7D");
        map.put(Material.COBBLESTONE, "7A7A7A");
        map.put(Material.MOSSY_COBBLESTONE, "6D7A5C");
        map.put(Material.STONE_BRICKS, "7A7A7A");
        map.put(Material.MOSSY_STONE_BRICKS, "717F62");
        map.put(Material.CRACKED_STONE_BRICKS, "767676");
        map.put(Material.SMOOTH_STONE, "A6A6A6");
        map.put(Material.ANDESITE, "888888");
        map.put(Material.DIORITE, "B7B7B4");
        map.put(Material.GRANITE, "95655A");
        map.put(Material.DEEPSLATE, "4C4C4E");
        map.put(Material.COBBLED_DEEPSLATE, "4A4A4C");
        map.put(Material.POLISHED_DEEPSLATE, "454548");
        map.put(Material.TUFF, "6B6C60");
        map.put(Material.CALCITE, "E5E4D8");
        map.put(Material.END_STONE, "DCDCA0");
        map.put(Material.OBSIDIAN, "14101D");
        map.put(Material.BLACKSTONE, "2B2529");
        map.put(Material.BASALT, "4C4B4E");
        map.put(Material.POLISHED_BASALT, "82817F");
        map.put(Material.QUARTZ_BLOCK, "ECE6DC");
        map.put(Material.AMETHYST_BLOCK, "8D69C7");
        map.put(Material.BRICKS, "966255");
        map.put(Material.SANDSTONE, "DDCE9E");
        map.put(Material.RED_SANDSTONE, "AA5E27");

        // -- dirt / ground family --
        map.put(Material.DIRT, "866043");
        map.put(Material.GRASS_BLOCK, "6B8E4E");
        map.put(Material.PODZOL, "6C4A2D");
        map.put(Material.MYCELIUM, "6C6167");
        map.put(Material.COARSE_DIRT, "6F5231");
        map.put(Material.ROOTED_DIRT, "8C6A46");
        map.put(Material.SAND, "DBCD9C");
        map.put(Material.RED_SAND, "A85723");
        map.put(Material.GRAVEL, "85807A");
        map.put(Material.CLAY, "A1A6B4");
        map.put(Material.ICE, "7DA9C7");
        map.put(Material.PACKED_ICE, "94B8D6");
        map.put(Material.SNOW_BLOCK, "F5F9F9");

        // -- nether / end --
        map.put(Material.NETHERRACK, "6E3A34");
        map.put(Material.SOUL_SAND, "503A2C");
        map.put(Material.SOUL_SOIL, "4B392A");
        map.put(Material.CRIMSON_NYLIUM, "932423");
        map.put(Material.WARPED_NYLIUM, "147C7A");

        // -- wood --
        map.put(Material.OAK_PLANKS, "B08A56");
        map.put(Material.SPRUCE_PLANKS, "7A5B34");
        map.put(Material.BIRCH_PLANKS, "D6C68C");
        map.put(Material.JUNGLE_PLANKS, "B27C50");
        map.put(Material.ACACIA_PLANKS, "B4602A");
        map.put(Material.DARK_OAK_PLANKS, "4B3722");
        map.put(Material.MANGROVE_PLANKS, "7B3F32");
        map.put(Material.CHERRY_PLANKS, "E5B4B8");
        map.put(Material.CRIMSON_PLANKS, "6B3A44");
        map.put(Material.WARPED_PLANKS, "297E77");
        map.put(Material.OAK_LOG, "6E5636");
        map.put(Material.SPRUCE_LOG, "3E2E1C");
        map.put(Material.BIRCH_LOG, "DCD6C6");
        map.put(Material.JUNGLE_LOG, "55381F");
        map.put(Material.ACACIA_LOG, "6A392A");
        map.put(Material.DARK_OAK_LOG, "382A1A");

        // -- ore / mineral blocks --
        map.put(Material.COAL_BLOCK, "101010");
        map.put(Material.IRON_BLOCK, "DCDCDC");
        map.put(Material.GOLD_BLOCK, "F9E13B");
        map.put(Material.DIAMOND_BLOCK, "67DBCE");
        map.put(Material.EMERALD_BLOCK, "33C359");
        map.put(Material.LAPIS_BLOCK, "24439E");
        map.put(Material.REDSTONE_BLOCK, "AB0F04");
        map.put(Material.NETHERITE_BLOCK, "4B4441");
        map.put(Material.COPPER_BLOCK, "C36246");
        map.put(Material.EXPOSED_COPPER, "9C775B");
        map.put(Material.WEATHERED_COPPER, "6B9077");
        map.put(Material.OXIDIZED_COPPER, "55916F");

        // -- wool (confident, real dye hex values) + derived concrete/terracotta --
        for (String[] dye : DYES) {
            String name = dye[0];
            String hex = dye[1];
            int rgb = Integer.parseInt(hex, 16);

            map.put(Material.valueOf(name + "_WOOL"), hex);
            map.put(Material.valueOf(name + "_CONCRETE"),
                    String.format("%06X", ColorMath.concreteFromDye(rgb)));
            map.put(Material.valueOf(name + "_TERRACOTTA"),
                    String.format("%06X", ColorMath.terracottaFromDye(rgb)));
        }

        return map;
    }
}
