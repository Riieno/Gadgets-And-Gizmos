package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

// Rotate cached controller collision shapes for each mounted face
final class ControllerShapeRotation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller shape rotation
    private ControllerShapeRotation() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the orientations
    static Map<Direction, Map<Direction, VoxelShape>> createOrientations(VoxelShape src) {
        EnumMap<Direction, Map<Direction, VoxelShape>> byNormal = new EnumMap<>(Direction.class);
        for (Direction normal : Direction.values()) {
            EnumMap<Direction, VoxelShape> byHorizontal = new EnumMap<>(Direction.class);
            for (Direction horizontal : Direction.Plane.HORIZONTAL) {
                byHorizontal.put(horizontal, rotate(src, normal, horizontal));
            }
            byNormal.put(normal, byHorizontal);
        }
        return byNormal;
    }

    // Translate the controller shape
    static VoxelShape translate(VoxelShape src, double xOffset, double yOffset, double zOffset) {
        VoxelShape translated = Shapes.empty();
        for (AABB box : src.toAabbs()) {
            translated = Shapes.or(translated, Shapes.create(new AABB(
                    box.minX + xOffset,
                    box.minY + yOffset,
                    box.minZ + zOffset,
                    box.maxX + xOffset,
                    box.maxY + yOffset,
                    box.maxZ + zOffset)));
        }
        return translated;
    }

    // Rotate the controller shape rotation
    private static VoxelShape rotate(VoxelShape src, Direction surfaceNormal, Direction horizontalFacing) {
        if (surfaceNormal == Direction.UP && horizontalFacing == Direction.SOUTH) {
            return src;
        }

        VoxelShape rotated = Shapes.empty();
        for (AABB box : src.toAabbs()) {
            rotated = Shapes.or(rotated, Shapes.create(transformBox(box, surfaceNormal, horizontalFacing)));
        }
        return rotated;
    }

    // Transform the box
    private static AABB transformBox(AABB box, Direction surfaceNormal, Direction horizontalFacing) {
        double[][] points = {
                transformPoint(box.minX, box.minY, box.minZ, surfaceNormal, horizontalFacing),
                transformPoint(box.minX, box.minY, box.maxZ, surfaceNormal, horizontalFacing),
                transformPoint(box.minX, box.maxY, box.minZ, surfaceNormal, horizontalFacing),
                transformPoint(box.minX, box.maxY, box.maxZ, surfaceNormal, horizontalFacing),
                transformPoint(box.maxX, box.minY, box.minZ, surfaceNormal, horizontalFacing),
                transformPoint(box.maxX, box.minY, box.maxZ, surfaceNormal, horizontalFacing),
                transformPoint(box.maxX, box.maxY, box.minZ, surfaceNormal, horizontalFacing),
                transformPoint(box.maxX, box.maxY, box.maxZ, surfaceNormal, horizontalFacing)
        };
        double minX = 1.0D;
        double minY = 1.0D;
        double minZ = 1.0D;
        double maxX = 0.0D;
        double maxY = 0.0D;
        double maxZ = 0.0D;
        for (double[] point : points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            minZ = Math.min(minZ, point[2]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
            maxZ = Math.max(maxZ, point[2]);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Transform the point
    private static double[] transformPoint(double x, double y, double z, Direction surfaceNormal,
                                           Direction horizontalFacing) {
        Direction zAxis = surfaceNormal.getAxis().isVertical() ? horizontalFacing : Direction.UP;
        Direction xAxis = cross(surfaceNormal, zAxis);
        double localX = x - 0.5D;
        double localY = y - 0.5D;
        double localZ = z - 0.5D;
        return new double[] {
                0.5D + xAxis.getStepX() * localX + surfaceNormal.getStepX() * localY + zAxis.getStepX() * localZ,
                0.5D + xAxis.getStepY() * localX + surfaceNormal.getStepY() * localY + zAxis.getStepY() * localZ,
                0.5D + xAxis.getStepZ() * localX + surfaceNormal.getStepZ() * localY + zAxis.getStepZ() * localZ
        };
    }

    // Get the cross
    private static Direction cross(Direction first, Direction second) {
        int x = first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY();
        int y = first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ();
        int z = first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX();
        for (Direction dir : Direction.values()) {
            if (dir.getStepX() == x && dir.getStepY() == y && dir.getStepZ() == z) {
                return dir;
            }
        }
        return Direction.EAST;
    }
}
