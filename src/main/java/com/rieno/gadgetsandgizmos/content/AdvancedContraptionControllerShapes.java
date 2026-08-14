package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Build cached controller collision shapes for every mounting direction
final class AdvancedContraptionControllerShapes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double UNIT = 1.0D / 16.0D;
    private static final VoxelShape MODEL_SHAPE = Shapes.or(
            box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            box(6.99D, 0.5858D, 0.0D, 16.0D, 9.0D, 8.4142D),
            box(7.99D, 2.7071D, 0.0D, 15.01D, 9.0711D, 6.364D),
            box(7.0D, 2.0D, 2.0D, 16.0D, 9.0D, 16.0D),
            box(6.0D, 5.0D, 8.0D, 7.0D, 8.0D, 15.0D),
            box(1.0D, 2.0D, 1.0D, 6.0D, 3.0D, 6.0D));
    private static final Map<Direction, Map<Direction, VoxelShape>> SHAPES_BY_ORIENTATION =
            ControllerShapeRotation.createOrientations(MODEL_SHAPE);
    private static final Map<ShapeKey, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller shapes
    private AdvancedContraptionControllerShapes() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape for mount
    static VoxelShape shapeForMount(Direction surfaceNormal, Direction horizontalFacing, int mountOffset) {
        ShapeKey key = new ShapeKey(surfaceNormal, horizontalFacing, Math.max(0, mountOffset));
        return SHAPES.computeIfAbsent(key, AdvancedContraptionControllerShapes::createShape);
    }

    // Create the shape
    private static VoxelShape createShape(ShapeKey key) {
        VoxelShape shape = SHAPES_BY_ORIENTATION
                .getOrDefault(key.surfaceNormal(), SHAPES_BY_ORIENTATION.get(Direction.UP))
                .getOrDefault(key.horizontalFacing(), MODEL_SHAPE);
        if (key.mountOffset() == 0) {
            return shape;
        }
        double offset = key.mountOffset() / (double) ControllerEmbeddedMount.PIXELS_PER_BLOCK;
        return ControllerShapeRotation.translate(
                shape,
                key.surfaceNormal().getStepX() * offset,
                key.surfaceNormal().getStepY() * offset,
                key.surfaceNormal().getStepZ() * offset);
    }

    // Get the box
    private static VoxelShape box(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        return Shapes.box(
                minX * UNIT, minY * UNIT, minZ * UNIT,
                maxX * UNIT, maxY * UNIT, maxZ * UNIT);
    }

    // Store the shape key
    private record ShapeKey(Direction surfaceNormal, Direction horizontalFacing, int mountOffset) {
    }
}
