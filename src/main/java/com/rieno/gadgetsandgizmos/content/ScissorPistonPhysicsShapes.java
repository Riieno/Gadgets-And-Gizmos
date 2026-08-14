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

// Build the local collision and mass shapes used by each physical scissor-piston segment
final class ScissorPistonPhysicsShapes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double HEAD_DEPTH = 4.0D / 16.0D;
    private static final double BASE_LENGTH = 12.0D / 16.0D;
    static final double SABLE_PREDICTIVE_CONTACT_DISTANCE = 0.005D;

    // Sable starts Rapier contact slightly before two shapes actually touch
    // Keep double that distance around the head so its fixed joint cannot snag on the sides
    private static final double HEAD_CONTACT_CLEARANCE = SABLE_PREDICTIVE_CONTACT_DISTANCE * 2.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston physics shapes
    private ScissorPistonPhysicsShapes() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the assembled piston base shape
    static VoxelShape assembledBaseShape(Direction facing) {
        return Shapes.create(assembledBaseBox(facing));
    }

    // Get the head shape
    static VoxelShape headShape(Direction facing) {
        return Shapes.create(headBox(facing));
    }

    // Get the assembled piston base box
    static AABB assembledBaseBox(Direction facing) {
        return switch (facing) {
            case UP -> new AABB(0.0D, 0.0D, 0.0D, 1.0D, BASE_LENGTH, 1.0D);
            case DOWN -> new AABB(0.0D, 1.0D - BASE_LENGTH, 0.0D, 1.0D, 1.0D, 1.0D);
            case NORTH -> new AABB(0.0D, 0.0D, 1.0D - BASE_LENGTH, 1.0D, 1.0D, 1.0D);
            case SOUTH -> new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, BASE_LENGTH);
            case WEST -> new AABB(1.0D - BASE_LENGTH, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
            case EAST -> new AABB(0.0D, 0.0D, 0.0D, BASE_LENGTH, 1.0D, 1.0D);
        };
    }

    // Get the head box
    static AABB headBox(Direction facing) {
        double nearSide = HEAD_CONTACT_CLEARANCE;
        double farSide = 1.0D - HEAD_CONTACT_CLEARANCE;
        double nearHead = HEAD_DEPTH - HEAD_CONTACT_CLEARANCE;
        double farHead = 1.0D - HEAD_DEPTH + HEAD_CONTACT_CLEARANCE;
        return switch (facing) {
            case UP -> new AABB(nearSide, farHead, nearSide, farSide, 1.0D, farSide);
            case DOWN -> new AABB(nearSide, 0.0D, nearSide, farSide, nearHead, farSide);
            case NORTH -> new AABB(nearSide, nearSide, 0.0D, farSide, farSide, nearHead);
            case SOUTH -> new AABB(nearSide, nearSide, farHead, farSide, farSide, 1.0D);
            case WEST -> new AABB(0.0D, nearSide, nearSide, nearHead, farSide, farSide);
            case EAST -> new AABB(farHead, nearSide, nearSide, 1.0D, farSide, farSide);
        };
    }
}
