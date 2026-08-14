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

// Rotate the industrial motor's cached voxel shape for each orientation
final class IndustrialMotorShapeRotation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the industrial motor shape rotation
    private IndustrialMotorShapeRotation() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the industrial motor shape rotation from south
    static VoxelShape fromSouth(VoxelShape shape, Direction facing) {
        return switch (facing) {
            case NORTH -> rotateY(shape, 2);
            case EAST -> rotateY(shape, 1);
            case WEST -> rotateY(shape, 3);
            case UP -> rotateY(rotateX(shape, 3), 3);
            case DOWN -> rotateY(rotateX(shape, 1), 3);
            case SOUTH -> shape;
        };
    }

    // Rotate the x
    private static VoxelShape rotateX(VoxelShape shape, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        VoxelShape rotated = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        box.minX,
                        1.0D - box.maxZ,
                        box.minY,
                        box.maxX,
                        1.0D - box.minZ,
                        box.maxY
                )));
            }
            rotated = next.optimize();
        }
        return rotated;
    }

    // Rotate the y
    private static VoxelShape rotateY(VoxelShape shape, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        VoxelShape rotated = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        box.minZ,
                        box.minY,
                        1.0D - box.maxX,
                        box.maxZ,
                        box.maxY,
                        1.0D - box.minX
                )));
            }
            rotated = next.optimize();
        }
        return rotated;
    }
}
