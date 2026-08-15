package com.nexusuniverse.realms.worldedit;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Rotates a Clipboard around the vertical (Y) axis in 90-degree steps, matching real WorldEdit's
 * //rotate convention: positive degrees turn the copy clockwise as seen from above, so a
 * clipboard facing north ends up facing east after a 90-degree rotation.
 *
 * Only multiples of 90 degrees are supported. That's not an arbitrary restriction -- it's the
 * only angle a block grid can represent exactly, both for the integer dx/dz offsets and for
 * directional block states (stairs, logs, fences, signs, etc). Anything else would need to
 * resample the shape onto a new grid and guess at block states, which silently corrupts the
 * copy rather than rotating it, so unsupported angles are refused by the caller instead.
 */
public final class ClipboardRotator {

    // The 16 compass points Bukkit's Rotatable (standing signs, banners, skulls) walks around, in
    // clockwise order starting at north -- a 90-degree turn is a shift of 4 positions in this list.
    private static final BlockFace[] SIXTEEN_POINT_COMPASS = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST
    };

    private ClipboardRotator() {
    }

    /** True only for angles this rotator can represent exactly: any multiple of 90. */
    public static boolean isSupportedAngle(int degrees) {
        return degrees % 90 == 0;
    }

    /** Returns a new, independently-boxed Clipboard rotated {@code degrees} clockwise; the source clipboard is untouched. */
    public static Clipboard rotate(Clipboard source, int degrees) {
        int steps = normalizeSteps(degrees);
        if (steps == 0) {
            // Still hand back a fresh copy rather than the same instance, so callers can always
            // treat the result as a new object regardless of the angle passed in.
            return new Clipboard(new ArrayList<>(source.entries()), source.minDx(), source.maxDx(), source.minDz(), source.maxDz());
        }

        List<Clipboard.Entry> rotatedEntries = new ArrayList<>(source.entries().size());
        int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;

        for (Clipboard.Entry entry : source.entries()) {
            int[] rotatedOffset = rotateOffset(entry.dx(), entry.dz(), steps);
            int newDx = rotatedOffset[0];
            int newDz = rotatedOffset[1];

            rotatedEntries.add(new Clipboard.Entry(newDx, entry.dy(), newDz, rotateBlockData(entry.blockData(), steps)));

            minDx = Math.min(minDx, newDx);
            maxDx = Math.max(maxDx, newDx);
            minDz = Math.min(minDz, newDz);
            maxDz = Math.max(maxDz, newDz);
        }

        return new Clipboard(rotatedEntries, minDx, maxDx, minDz, maxDz);
    }

    private static int normalizeSteps(int degrees) {
        int steps = (degrees / 90) % 4;
        if (steps < 0) steps += 4;
        return steps;
    }

    /** Clockwise-from-above turn: facing north (0,-1) becomes facing east (1,0) after one step. */
    private static int[] rotateOffset(int dx, int dz, int steps) {
        int x = dx, z = dz;
        for (int i = 0; i < steps; i++) {
            int newX = -z;
            int newZ = x;
            x = newX;
            z = newZ;
        }
        return new int[]{x, z};
    }

    private static String rotateBlockData(String blockDataString, int steps) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(blockDataString);
        } catch (IllegalArgumentException e) {
            // A stored BlockData string that doesn't parse on this server version -- leave it
            // alone, same fallback EditExecutor#paste already uses for a block that won't apply.
            return blockDataString;
        }

        for (int i = 0; i < steps; i++) {
            rotateOneStep(data);
        }
        return data.getAsString();
    }

    private static void rotateOneStep(BlockData data) {
        if (data instanceof Directional directional) {
            BlockFace rotated = rotateCardinal(directional.getFacing());
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        }
        if (data instanceof Orientable orientable) {
            // A pillar's axis only actually flips between steps -- X and Z swap on a 90-degree
            // turn, Y (already vertical) never changes.
            if (orientable.getAxis() == Axis.X) {
                orientable.setAxis(Axis.Z);
            } else if (orientable.getAxis() == Axis.Z) {
                orientable.setAxis(Axis.X);
            }
        }
        if (data instanceof MultipleFacing multipleFacing) {
            EnumSet<BlockFace> current = EnumSet.noneOf(BlockFace.class);
            current.addAll(multipleFacing.getFaces());

            for (BlockFace face : current) {
                multipleFacing.setFace(face, false);
            }
            for (BlockFace face : current) {
                BlockFace rotated = rotateCardinal(face);
                if (multipleFacing.getAllowedFaces().contains(rotated)) {
                    multipleFacing.setFace(rotated, true);
                }
            }
        }
        if (data instanceof Rotatable rotatable) {
            int index = indexOf(rotatable.getRotation());
            if (index >= 0) {
                rotatable.setRotation(SIXTEEN_POINT_COMPASS[(index + 4) % 16]);
            }
        }
    }

    private static BlockFace rotateCardinal(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face; // UP, DOWN, and anything non-cardinal stay put
        };
    }

    private static int indexOf(BlockFace face) {
        for (int i = 0; i < SIXTEEN_POINT_COMPASS.length; i++) {
            if (SIXTEEN_POINT_COMPASS[i] == face) return i;
        }
        return -1;
    }
}
