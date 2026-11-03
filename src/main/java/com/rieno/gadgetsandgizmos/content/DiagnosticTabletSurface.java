package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.geometry.PlanarSurface;
import com.rieno.gadgetsandgizmos.lib.geometry.VoxelShapeTransforms;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

// Describe the clickable screen area for held and placed tablets
public final class DiagnosticTabletSurface {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PIXEL = 1.0D / 16.0D;
    private static final double SURFACE_OFFSET = 0.001D;
    private static final Vec3 CENTER = new Vec3(0.5D, 0.5D, 0.5D);
    private static final PlanarSurface[][][] SCREENS = new PlanarSurface[DiagnosticTabletView.values().length][6][4];
    private static final VoxelShape[][][] SHAPES = new VoxelShape[DiagnosticTabletView.values().length][6][4];

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static{
        for(DiagnosticTabletView view : DiagnosticTabletView.values()){
            PlanarSurface screen = createScreen(view);
            VoxelShape shape = createShape(view);
            for(Direction facing : Direction.values()){
                for(Direction horizontal : Direction.Plane.HORIZONTAL){
                    Quaterniond rotation = rotation(facing, horizontal);
                    PlanarSurface surface = screen.rotate(rotation, CENTER);
                    if(facing != Direction.DOWN){
                        surface = new PlanarSurface(surface.bottomRight(), surface.topRight(),
                                surface.topLeft(), surface.bottomLeft());
                    }
                    int idx = horizontal.get2DDataValue();
                    SCREENS[view.ordinal()][facing.ordinal()][idx] = view == DiagnosticTabletView.FLAT
                            && facing.getAxis().isHorizontal() ? screen(facing) : surface;
                    SHAPES[view.ordinal()][facing.ordinal()][idx] = VoxelShapeTransforms.rotate(shape, rotation, CENTER);
                }
            }
        }
    }

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
    public static PlanarSurface screen(Direction facing) {
        double low = PIXEL;
        double high = 15.0D * PIXEL;
        double top = 3.0D * PIXEL;
        double bottom = 14.0D * PIXEL;
        return switch (facing) {
            case UP -> new PlanarSurface(
                    new Vec3(low, 2.0D * PIXEL + SURFACE_OFFSET, top),
                    new Vec3(low, 2.0D * PIXEL + SURFACE_OFFSET, bottom),
                    new Vec3(high, 2.0D * PIXEL + SURFACE_OFFSET, bottom),
                    new Vec3(high, 2.0D * PIXEL + SURFACE_OFFSET, top));
            case DOWN -> new PlanarSurface(
                    new Vec3(low, 14.0D * PIXEL - SURFACE_OFFSET, 13.0D * PIXEL),
                    new Vec3(low, 14.0D * PIXEL - SURFACE_OFFSET, 2.0D * PIXEL),
                    new Vec3(high, 14.0D * PIXEL - SURFACE_OFFSET, 2.0D * PIXEL),
                    new Vec3(high, 14.0D * PIXEL - SURFACE_OFFSET, 13.0D * PIXEL));
            case NORTH -> new PlanarSurface(
                    new Vec3(high, 13.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(high, 2.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(low, 2.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET),
                    new Vec3(low, 13.0D * PIXEL, 14.0D * PIXEL - SURFACE_OFFSET));
            case SOUTH -> new PlanarSurface(
                    new Vec3(low, 14.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(low, 3.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(high, 3.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET),
                    new Vec3(high, 14.0D * PIXEL, 2.0D * PIXEL + SURFACE_OFFSET));
            case WEST -> new PlanarSurface(
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 14.0D * PIXEL, low),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 3.0D * PIXEL, low),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 3.0D * PIXEL, high),
                    new Vec3(14.0D * PIXEL - SURFACE_OFFSET, 14.0D * PIXEL, high));
            case EAST -> new PlanarSurface(
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 14.0D * PIXEL, high),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 3.0D * PIXEL, high),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 3.0D * PIXEL, low),
                    new Vec3(2.0D * PIXEL + SURFACE_OFFSET, 14.0D * PIXEL, low));
        };
    }

    // Get the screen for the selected model and mounting face
    public static PlanarSurface screen(BlockState state){
        return SCREENS[state.getValue(DiagnosticTabletBlock.VIEW).ordinal()][state.getValue(CTDirectionalBlock.FACING).ordinal()]
                [state.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING).get2DDataValue()];
    }

    // Get the selection and collision shape for the selected model
    public static VoxelShape shape(BlockState state){
        return SHAPES[state.getValue(DiagnosticTabletBlock.VIEW).ordinal()][state.getValue(CTDirectionalBlock.FACING).ordinal()]
                [state.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING).get2DDataValue()];
    }

    // Intersect the player's local ray with the screen instead of its enclosing shape
    public static @Nullable PlanarSurface.Point projectScreen(DiagnosticTabletBlockEntity tablet,
                                                              BlockHitResult hit, Player player){
        if(hit == null || player == null) return null;
        Vec3 pos = Vec3.atLowerCornerOf(tablet.getBlockPos());
        Vec3 start = SimulatedHelper.toContainingLocalPosition(tablet, player.getEyePosition()).subtract(pos);
        Vec3 direction = hit.getLocation().subtract(pos).subtract(start).normalize();
        return screen(tablet.getBlockState()).intersect(start, start.add(direction.scale(player.blockInteractionRange())));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Match the screen panel's authored origin and rotation in each model
    private static PlanarSurface createScreen(DiagnosticTabletView view){
        PlanarSurface flat = screen(Direction.UP);
        return switch(view){
            case FLAT -> flat;
            case NEG_22_5 -> flat.transform(point -> point.add(0.0D, 3.7D * PIXEL, 0.0D))
                    .rotate(new Quaterniond().rotateX(Math.toRadians(-22.5D)), pixel(0, 2.7D, 11));
            case ANGLED_45 -> flat.transform(point -> point.add(0.0D, 7.0D * PIXEL, 0.0D))
                    .rotate(new Quaterniond().rotateX(Math.toRadians(-45.0D)), pixel(0, 6, 11));
            case ANGLED_22_5 -> new PlanarSurface(
                    pixel(1, 0.4D, 8).add(0, 0, -SURFACE_OFFSET),
                    pixel(1, 11.4D, 8).add(0, 0, -SURFACE_OFFSET),
                    pixel(15, 11.4D, 8).add(0, 0, -SURFACE_OFFSET),
                    pixel(15, 0.4D, 8).add(0, 0, -SURFACE_OFFSET))
                    .rotate(new Quaterniond().rotateX(Math.toRadians(22.5D)), pixel(0, 8.4D, 11));
        };
    }

    // Include the panel and stand using their separate model rotations
    private static VoxelShape createShape(DiagnosticTabletView view){
        return switch(view){
            case FLAT -> Block.box(0, 0, 1.5D, 16, 2, 14.5D);
            case NEG_22_5 -> Shapes.or(
                    VoxelShapeTransforms.rotate(Block.box(0, 3.7D, 1.5D, 16, 5.7D, 14.5D),
                            new Quaterniond().rotateX(Math.toRadians(-22.5D)), pixel(0, 2.7D, 11)),
                    VoxelShapeTransforms.rotate(Block.box(3, -1, 13, 13, 5, 14),
                            new Quaterniond().rotateX(Math.toRadians(-45.0D)), pixel(7, -1, 12)));
            case ANGLED_45 -> Shapes.or(
                    VoxelShapeTransforms.rotate(Block.box(0, 7, 1.5D, 16, 9, 14.5D),
                            new Quaterniond().rotateX(Math.toRadians(-45.0D)), pixel(0, 6, 11)),
                    Block.box(3, 0, 9, 13, 6, 10));
            case ANGLED_22_5 -> Shapes.or(
                    VoxelShapeTransforms.rotate(Block.box(0, -1.1D, 8, 16, 11.9D, 10),
                            new Quaterniond().rotateX(Math.toRadians(22.5D)), pixel(0, 8.4D, 11)),
                    VoxelShapeTransforms.rotate(Block.box(3, -0.4D, 10, 13, 5.6D, 11),
                            new Quaterniond().rotateX(Math.toRadians(-22.5D)), pixel(7, -0.4D, 9)));
        };
    }

    // Match the blockstate's rotations about the block center
    private static Quaterniond rotation(Direction facing, Direction horizontal){
        if(facing.getAxis().isVertical()){
            Direction front = facing == Direction.UP ? horizontal : horizontal.getOpposite();
            double yaw = switch(front){
                case NORTH -> 0.0D;
                case EAST -> -Math.PI / 2.0D;
                case SOUTH -> -Math.PI;
                case WEST -> -Math.PI * 1.5D;
                default -> throw new IllegalArgumentException("Tablet viewing direction must be horizontal");
            };
            return new Quaterniond().rotateY(yaw).rotateX(facing == Direction.UP ? 0.0D : -Math.PI);
        }
        return switch(facing){
            case UP -> new Quaterniond();
            case DOWN -> new Quaterniond().rotateX(-Math.PI);
            case NORTH -> new Quaterniond().rotateX(-Math.PI / 2.0D);
            case SOUTH -> new Quaterniond().rotateY(-Math.PI).rotateX(-Math.PI / 2.0D);
            case WEST -> new Quaterniond().rotateY(-Math.PI * 1.5D).rotateX(-Math.PI / 2.0D);
            case EAST -> new Quaterniond().rotateY(-Math.PI / 2.0D).rotateX(-Math.PI / 2.0D);
        };
    }

    // Convert model pixels to block coordinates
    private static Vec3 pixel(double x, double y, double z){
        return new Vec3(x * PIXEL, y * PIXEL, z * PIXEL);
    }
}
