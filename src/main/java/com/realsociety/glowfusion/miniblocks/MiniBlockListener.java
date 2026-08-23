package com.realsociety.glowfusion.miniblocks;

import com.realsociety.glowfusion.GlowFusionPlugin;
import com.realsociety.glowfusion.slabs.DualSlabListener;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lets a player paint a small colored tile onto one cell of a 4x4 grid on a
 * single face of an existing, plain, non-interactive block - stack enough
 * of them and you get real stripes, pixel art, or signage on a wall, all on
 * ONE real block's face, instead of needing a separate real block per pixel.
 *
 * <p>Same "visual, not physical" idea VerticalSlabListener already
 * established: every tile is a small {@link BlockDisplay}, not a real
 * block, so there's no fine collision to match the pattern - walking into a
 * painted wall still just hits the one real solid block underneath it, the
 * same as it always did. Unlike vertical slabs, though, the real block is
 * never touched (never set to AIR): a mosaic is a decal added on TOP of an
 * existing block's face, not a replacement for the block, so it can go on
 * any plain wall/floor you've already built without giving up that block's
 * own solidity. That also means, unlike vertical slabs, there's no YAML
 * store here and nothing to desync after a crash - the real block's own
 * state was never changed in the first place, only extra decorative
 * entities were added, and those are ordinary entities Minecraft already
 * saves/restores with the chunk on its own.</p>
 *
 * <p>Off by default for every player - painting only happens for players who
 * have turned their own mini-block paint mode ON via {@code /glowfusion
 * mini}, so nobody's normal building with wool, stone, planks, etc. is ever
 * hijacked unless they've explicitly opted in for that session. Controls,
 * once paint mode is on and while looking at the exact spot on the face you
 * want: right-click with an eligible material in hand paints that cell
 * (replacing whatever was already there); sneak + right-click with an empty
 * hand erases just that one cell. Breaking the real block clears every tile
 * on every face of it at once.</p>
 *
 * <p>HONEST LIMITATION: {@code Material#isInteractable()} (used below to
 * avoid hijacking a chest/furnace/door/etc.'s own right-click) is
 * documented by the Spigot/Paper community as occasionally over-inclusive
 * - stairs, fences, and piston heads report true despite having no real
 * menu or behavior. That only costs a few harmless block types the ability
 * to be painted on, an acceptable trade for reliably never hijacking a
 * genuine GUI block's own interaction.</p>
 */
public final class MiniBlockListener implements Listener {

    private static final int GRID = 4;
    private static final double CELL = 1.0 / GRID; // also each tile's full width/height/depth - a true little cube, not a decal
    private static final double SEARCH_RADIUS = 0.6; // matches DualSlabListener/VerticalSlabListener's own corner-anchored search radius
    private static final Display.Brightness FULL_BRIGHTNESS = new Display.Brightness(15, 15);

    public static final NamespacedKey MINI_KEY = new NamespacedKey("glowfusion", "mini_block_tile");
    private static final NamespacedKey MINI_ID_KEY = new NamespacedKey("glowfusion", "mini_block_tile_id");

    private final GlowFusionPlugin plugin;
    private final MiniBlockPalette palette;

    /**
     * Players who've opted their own session into mini-block paint mode via
     * {@code /glowfusion mini}. In-memory only and off by default for
     * everyone - this feature must never hijack a player's normal building
     * with an eligible material unless they've explicitly turned it on.
     */
    private final Set<UUID> paintModeEnabled = new HashSet<>();

    public MiniBlockListener(GlowFusionPlugin plugin, MiniBlockPalette palette) {
        this.plugin = plugin;
        this.palette = palette;
    }

    /** @return true if paint mode is now ON for this player, false if it's now OFF. */
    public boolean togglePaintMode(Player player) {
        UUID id = player.getUniqueId();
        if (paintModeEnabled.remove(id)) {
            return false;
        }
        paintModeEnabled.add(id);
        return true;
    }

    public boolean isPaintModeEnabled(Player player) {
        return paintModeEnabled.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.featureEnabled("mini-blocks.enabled")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!isPaintModeEnabled(player)) {
            return; // opt-in only, off by default - toggle with /glowfusion mini so normal building is never hijacked
        }

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (clicked == null || !isPaintableFace(face)) {
            return;
        }
        if (clicked.getType().isInteractable()) {
            return; // don't hijack a chest/furnace/door/crafting table/etc.'s own right-click
        }
        if (face == BlockFace.UP && DualSlabListener.findFusedDisplay(clicked.getLocation()) != null) {
            return; // that face is already showing a fused dual-slab top - break that first
        }

        ItemStack inHand = event.getItem();
        boolean erasing = inHand == null && player.isSneaking();
        boolean painting = inHand != null && !player.isSneaking() && palette.isEligible(inHand.getType());
        if (!erasing && !painting) {
            return;
        }
        if (plugin.requiresPermission("mini-blocks.require-permission")
                && !player.hasPermission("glowfusion.miniblocks")) {
            return;
        }

        int[] cell = resolveCell(clicked, face, event.getInteractionPoint());
        String id = tileId(face, cell[0], cell[1]);
        Location corner = clicked.getLocation();

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        BlockDisplay existing = findTile(corner, id);

        if (erasing) {
            if (existing != null) {
                dropAndRemove(clicked, existing, player);
            }
            return;
        }

        if (existing != null) {
            existing.remove(); // repainting this exact cell - out with the old color first
        }
        spawnTile(clicked, face, cell[0], cell[1], id, inHand.getType().createBlockData());

        if (plugin.featureEnabled("mini-blocks.consume-item") && player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        if (plugin.featureEnabled("mini-blocks.play-sound")) {
            clicked.getWorld().playSound(clicked.getLocation(), Sound.BLOCK_STONE_PLACE, 0.6f, 1.6f);
        }
    }

    private void dropAndRemove(Block clicked, BlockDisplay display, Player player) {
        Material dropped = display.getBlock().getMaterial();
        display.remove();
        if (player.getGameMode() != GameMode.CREATIVE && dropped != null) {
            clicked.getWorld().dropItemNaturally(clicked.getLocation(), new ItemStack(dropped));
        }
    }

    private static boolean isPaintableFace(BlockFace face) {
        return face == BlockFace.UP || face == BlockFace.DOWN || face == BlockFace.NORTH
                || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    /**
     * Works out which of the 4x4 grid cells on the clicked face the player
     * actually meant, from Paper's exact interaction point - falling back
     * to the middle of the grid if that's unavailable (it's nullable by the
     * API's own contract) rather than failing the whole interaction.
     */
    private int[] resolveCell(Block clicked, BlockFace face, Location interactionPoint) {
        double lx = 0.5, ly = 0.5, lz = 0.5;
        if (interactionPoint != null) {
            lx = interactionPoint.getX() - clicked.getX();
            ly = interactionPoint.getY() - clicked.getY();
            lz = interactionPoint.getZ() - clicked.getZ();
        }
        double a;
        double b;
        switch (face) {
            case UP, DOWN -> { a = lx; b = lz; }
            case NORTH, SOUTH -> { a = lx; b = ly; }
            default -> { a = lz; b = ly; } // EAST, WEST
        }
        int i = clampCell((int) Math.floor(a * GRID));
        int j = clampCell((int) Math.floor(b * GRID));
        return new int[]{i, j};
    }

    private static int clampCell(int value) {
        return Math.max(0, Math.min(GRID - 1, value));
    }

    private static String tileId(BlockFace face, int i, int j) {
        return face.name() + ":" + i + ":" + j;
    }

    private void spawnTile(Block clicked, BlockFace face, int i, int j, String id, BlockData data) {
        Transformation transform = transformFor(face, i, j);
        clicked.getWorld().spawn(clicked.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(data);
            display.setTransformation(transform);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            // Without an explicit brightness, a Display entity falls back to
            // "default rendering brightness behavior based on the environment" -
            // in a dim/dark spot that renders the tile solid black instead of
            // its real color. Force full brightness so the tile's actual color
            // always shows, regardless of ambient light.
            display.setBrightness(FULL_BRIGHTNESS);
            display.getPersistentDataContainer().set(MINI_KEY, PersistentDataType.BYTE, (byte) 1);
            display.getPersistentDataContainer().set(MINI_ID_KEY, PersistentDataType.STRING, id);
        });
    }

    /**
     * Every tile on a block - regardless of which face or cell it's
     * visually rendered at - is spawned at the exact same point (the
     * block's own corner); what actually places each one is its own
     * Transformation, not its entity location, so one small search right
     * at that corner finds all of them at once, and the tile-id PDC string
     * is what tells them apart afterward.
     */
    private List<BlockDisplay> findTiles(Location blockCorner) {
        List<BlockDisplay> found = new ArrayList<>();
        for (Entity entity : blockCorner.getWorld().getNearbyEntities(blockCorner, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            if (entity instanceof BlockDisplay display
                    && display.getPersistentDataContainer().has(MINI_KEY, PersistentDataType.BYTE)) {
                found.add(display);
            }
        }
        return found;
    }

    private BlockDisplay findTile(Location blockCorner, String id) {
        for (BlockDisplay display : findTiles(blockCorner)) {
            if (id.equals(display.getPersistentDataContainer().get(MINI_ID_KEY, PersistentDataType.STRING))) {
                return display;
            }
        }
        return null;
    }

    /**
     * Translation is the corner of this one tile's box (in the block's own
     * local 0-1 coordinate space, same convention VerticalSlabListener's
     * transformFor already uses), scale is that box's size - a true
     * CELLxCELLxCELL cube (a small but full-height/width/depth block, not a
     * thin decal) sitting just outside the named face, offset into the
     * correct one of the 16 grid cells.
     */
    private static Transformation transformFor(BlockFace face, int i, int j) {
        float x = 0f, y = 0f, z = 0f;
        float sx = (float) CELL, sy = (float) CELL, sz = (float) CELL;
        switch (face) {
            case UP -> {
                x = (float) (i * CELL);
                z = (float) (j * CELL);
                y = 1.0f;
            }
            case DOWN -> {
                x = (float) (i * CELL);
                z = (float) (j * CELL);
                y = (float) -CELL;
            }
            case NORTH -> {
                x = (float) (i * CELL);
                y = (float) (j * CELL);
                z = (float) -CELL;
            }
            case SOUTH -> {
                x = (float) (i * CELL);
                y = (float) (j * CELL);
                z = 1.0f;
            }
            case WEST -> {
                z = (float) (i * CELL);
                y = (float) (j * CELL);
                x = (float) -CELL;
            }
            default -> { // EAST
                z = (float) (i * CELL);
                y = (float) (j * CELL);
                x = 1.0f;
            }
        }
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /**
     * Forcibly removes every tile tracked at this exact block, on any face,
     * regardless of whether they still make visual sense - e.g. another
     * plugin or WorldEdit replaced/removed the real block underneath them
     * without going through this class's own onBreak/onExplode handlers,
     * leaving "floating" tiles behind with nothing real left to justify
     * them. Backs the "/glowfusion unstick" recovery command, the same way
     * DualSlabListener#forceClearFusedDisplay and
     * VerticalSlabListener#forceClear do for their own features.
     *
     * @return true if anything was actually cleared.
     */
    public boolean forceClearTiles(Location blockCorner) {
        List<BlockDisplay> tiles = findTiles(blockCorner);
        for (BlockDisplay display : tiles) {
            display.remove();
        }
        return !tiles.isEmpty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        removeAllTilesAndDrop(event.getBlock(), true, event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeAllTilesAndDrop(block, false, null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeAllTilesAndDrop(block, false, null);
        }
    }

    private void removeAllTilesAndDrop(Block block, boolean dropItems, Location dropAt) {
        List<BlockDisplay> tiles = findTiles(block.getLocation());
        for (BlockDisplay display : tiles) {
            Material material = display.getBlock().getMaterial();
            display.remove();
            if (dropItems && material != null) {
                block.getWorld().dropItemNaturally(dropAt != null ? dropAt : block.getLocation(), new ItemStack(material));
            }
        }
    }

    /** Keeps players/mobs/projectiles from damaging or popping a tile directly. */
    @EventHandler(ignoreCancelled = true)
    public void onDisplayDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof BlockDisplay display
                && display.getPersistentDataContainer().has(MINI_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
