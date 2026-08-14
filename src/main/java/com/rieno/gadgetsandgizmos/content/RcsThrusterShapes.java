package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

// Build the RCS thruster collision and selection shapes for every facing
final class RcsThrusterShapes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double UNIT = 1.0D / 16.0D;
    private static final Map<Direction, VoxelShape> BASE_SHAPES = createBaseShapes();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster shapes
    private RcsThrusterShapes() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the base shape
    static VoxelShape baseShape(Direction mountingFace) {
        return BASE_SHAPES.getOrDefault(mountingFace, BASE_SHAPES.get(Direction.DOWN));
    }

    // Create the base shapes
    private static Map<Direction, VoxelShape> createBaseShapes() {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            VoxelShape mountingPlate = orientedBox(dir,
                    0.0D, 1.0D,
                    5.5D, 10.5D,
                    4.5D, 11.5D);
            VoxelShape body = orientedBox(dir,
                    1.0D, 5.0D,
                    5.0D, 11.0D,
                    4.0D, 12.0D);
            shapes.put(dir, Shapes.or(mountingPlate, body).optimize());
        }
        return Map.copyOf(shapes);
    }

    // Get the oriented box
    private static VoxelShape orientedBox(
            Direction mountingFace,
            double depthMin,
            double depthMax,
            double shortMin,
            double shortMax,
            double longMin,
            double longMax
    ) {
        double axisMin = mountingFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? depthMin
                : 16.0D - depthMax;
        double axisMax = mountingFace.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? depthMax
                : 16.0D - depthMin;
        return switch (mountingFace.getAxis()) {
            case X -> box(axisMin, longMin, shortMin, axisMax, longMax, shortMax);
            case Y -> box(shortMin, axisMin, longMin, shortMax, axisMax, longMax);
            case Z -> box(shortMin, longMin, axisMin, shortMax, longMax, axisMax);
        };
    }

    // Get the box
    private static VoxelShape box(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        return Shapes.box(
                minX * UNIT, minY * UNIT, minZ * UNIT,
                maxX * UNIT, maxY * UNIT, maxZ * UNIT);
    }
}
