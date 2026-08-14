package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Describe the clickable screen area for held and placed tablets
public final class DiagnosticTabletSurface {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PIXEL = 1.0D / 16.0D;
    private static final double SURFACE_OFFSET = 0.001D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet surface
    private DiagnosticTabletSurface() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    public static VoxelShape shape(Direction facing) {
        return switch (facing) {
            case UP -> Block.box(0, 0, 2, 16, 2, 15);
            case DOWN -> Block.box(0, 14, 1, 16, 16, 14);
            case NORTH -> Block.box(0, 1, 14, 16, 14, 16);
            case SOUTH -> Block.box(0, 2, 0, 16, 15, 2);
            case WEST -> Block.box(14, 2, 0, 16, 15, 16);
            case EAST -> Block.box(0, 2, 1, 2, 15, 16);
        };
    }

    // Get the screen
    public static Surface screen(Direction facing) {
        double low = PIXEL;
        double high = 15.0D * PIXEL;
        double top = 3.0D * PIXEL;
        double bottom = 14.0D * PIXEL;
        return switch (facing) {
            case UP -> new Surface(
                    new Vec3(low, 2.0D * PIXEL + SURFACE_OFFSET, top),
                    new Vec3(low, 2.0D * PIXEL + SURFACE_OFFSET, bottom),
                    new Vec3(high, 2.0D * PIXEL + SURFACE_OFFSET, bottom),
                    new Vec3(high, 2.0D * PIXEL + SURFACE_OFFSET, top));
            case DOWN -> new Surface(
                    new Vec3(low, 14.0D * PIXEL - SURFACE_OFFSET, 13.0D * PIXEL),
                    new Vec3(low, 14.0D * PIXEL - SURFACE_OFFSET, 2.0D * PIXEL),
                    new Vec3(high, 14.0D * PIXEL - SURFACE_OFFSET, 2.0D * PIXEL),
                    new Vec3(high, 14.0D * PIXEL - SURFACE_OFFSET, 13.0D * PIXEL));
            case NORTH -> new Surface(
                    new Vec3(high, 13.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(high, 2.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(low, 2.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(low, 13.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET));
            case SOUTH -> new Surface(
                    new Vec3(low, 14.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(low, 3.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(high, 3.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(high, 14.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET));
            case WEST -> new Surface(
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 14.0D * PIXEL, low),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 3.0D * PIXEL, low),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 3.0D * PIXEL, high),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 14.0D * PIXEL, high));
            case EAST -> new Surface(
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 14.0D * PIXEL, high),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 3.0D * PIXEL, high),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 3.0D * PIXEL, low),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 14.0D * PIXEL, low));
        };
    }

    // Store the surface
    public record Surface(Vec3 topLeft, Vec3 bottomLeft, Vec3 bottomRight, Vec3 topRight) {
        // Get the project
        public @Nullable Point project(Vec3 localHit) {
            Vec3 horizontal = topRight.subtract(topLeft);
            Vec3 vertical = bottomLeft.subtract(topLeft);
            Vec3 offset = localHit.subtract(topLeft);
            double u = offset.dot(horizontal) / horizontal.lengthSqr();
            double v = offset.dot(vertical) / vertical.lengthSqr();
            double tolerance = 0.015D;
            if (u < -tolerance || u > 1.0D + tolerance
                    || v < -tolerance || v > 1.0D + tolerance) {
                return null;
            }
            return new Point(Math.max(0.0D, Math.min(1.0D, u)),
                    Math.max(0.0D, Math.min(1.0D, v)));
        }
    }

    // Store the point
    public record Point(double x, double y) {
    }
}
