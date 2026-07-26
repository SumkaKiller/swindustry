package com.jokerdayn.swindustry.multiblock;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One machine that currently exists in the world: where it stands, which way it faces, and every
 * block it is made of.
 *
 * <p>Produced by {@link MultiblockPattern#match} and thrown away the moment the machine stops
 * matching, so the positions inside are always the ones that were verified — never a guess left
 * over from an earlier check.</p>
 *
 * @param controllerPos the controller block, also the anchor the pattern was measured from
 * @param facing        which way the controller faces, pointing out of the machine
 * @param walls         every solid block of the machine, controller included
 * @param cavity        every position the machine needs to keep clear
 */
public record MultiblockInstance(
    BlockPos controllerPos,
    Direction facing,
    Set<BlockPos> walls,
    Set<BlockPos> cavity
) {

    /** Whether the position is one of this machine's solid blocks. */
    public boolean containsWall(BlockPos pos) {
        return walls.contains(pos);
    }

    /** Whether the position is part of this machine at all, cavity included. */
    public boolean contains(BlockPos pos) {
        return walls.contains(pos) || cavity.contains(pos);
    }

    /** The box enclosing the whole machine, useful for particles and area effects. */
    public AABB bounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : walls) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    /** The centre of the hollow interior, where a machine's smoke and glow belong. */
    public Vec3 cavityCenter() {
        if (cavity.isEmpty()) {
            return Vec3.atCenterOf(controllerPos);
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (BlockPos pos : cavity) {
            x += pos.getX() + 0.5;
            y += pos.getY() + 0.5;
            z += pos.getZ() + 0.5;
        }
        return new Vec3(x / cavity.size(), y / cavity.size(), z / cavity.size());
    }

    /** The highest cavity position — the mouth of a chimney, for a smoke plume. */
    public BlockPos topOfCavity() {
        BlockPos highest = controllerPos;
        for (BlockPos pos : cavity) {
            if (pos.getY() > highest.getY()) {
                highest = pos;
            }
        }
        return highest;
    }
}
