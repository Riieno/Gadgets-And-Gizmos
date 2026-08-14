package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Shape and move each replaceable segment of an assembled scissor piston
public class ScissorPistonArmBlock extends CTDirectionalBlock
        implements IBE<ScissorPistonArmBlockEntity>, BlockSubLevelAssemblyListener,
        BlockSubLevelCollisionShape {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty EXPOSED = BooleanProperty.create("exposed");
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float HEADWARD_OFFSET = 4.0F / 16.0F;
    private static final float ARM_RADIUS = 1.5F / 16.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston arm block
    public ScissorPistonArmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(EXPOSED, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return armShape(level, pos);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return armShape(level, pos);
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.empty();
    }

    // Check if this can be replaced
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return !state.getValue(EXPOSED);
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EXPOSED);
    }

    // Get the render shape
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(ctx.getClickedPos()) instanceof ScissorPistonArmBlockEntity arm) {
            return arm.removeExtensionFromParent(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    // Handle crouching wrench use
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        return onWrenched(state, ctx);
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ScissorPistonArmBlockEntity arm) {
            arm.removeExtensionFromExternalBreak();
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, ScissorPistonArmBlockEntity::beginAssemblyTransfer);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, ScissorPistonArmBlockEntity::finishAssemblyTransfer);
    }

    // Get the block entity class
    @Override
    public Class<ScissorPistonArmBlockEntity> getBlockEntityClass() {
        return ScissorPistonArmBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ScissorPistonArmBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SCISSOR_PISTON_ARM.get();
    }

    // Get the arm shape
    private static VoxelShape armShape(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ScissorPistonArmBlockEntity arm)) {
            return Shapes.empty();
        }

        int links = arm.getArmLinkCount();
        float extension = Math.max(0.0F, (float) arm.getExtension());
        if (extension <= 0.001F) {
            return Shapes.empty();
        }

        float sectionLength = Mth.clamp(extension / links, 0.0F, 1.0F);
        float sideSpan = (float) Math.sqrt(Math.max(0.0F, 1.0F - sectionLength * sectionLength));
        float scale = Mth.lerp(sectionLength, 0.9F, 1.0F);
        int cellDistance = arm.getCellDistanceFromHead();
        Quaternionf baseRotation = rotationFromUp(arm.getPistonFacing());

        VoxelShape shape = Shapes.empty();
        float bestCenterOffset = Float.NaN;
        float bestScore = Float.MAX_VALUE;
        for (int link = 0; link < links; link++) {
            float distanceFromHead = extension - (link + 0.5F) * sectionLength;
            if (cellForDistance(distanceFromHead) != cellDistance) {
                continue;
            }
            float centerOffset = cellDistance - distanceFromHead;
            float score = Math.abs(centerOffset);
            if (score < bestScore) {
                bestCenterOffset = centerOffset;
                bestScore = score;
            }
        }

        if (Float.isNaN(bestCenterOffset)) {
            return Shapes.empty();
        }

        shape = addArmShape(shape, baseRotation, bestCenterOffset, sectionLength, sideSpan, scale,
                cellDistance, extension, false);
        shape = addArmShape(shape, baseRotation, bestCenterOffset, sectionLength, sideSpan, scale,
                cellDistance, extension, true);
        return shape.optimize();
    }

    // Get the cell for distance
    private static int cellForDistance(float distanceFromHead) {
        return Math.max(1, Math.round(distanceFromHead));
    }

    // Add the arm shape
    private static VoxelShape addArmShape(VoxelShape shape, Quaternionf baseRotation, float centerOffset,
                                          float sectionLength, float sideSpan, float scale,
                                          int cellDistance, float extension,
                                          boolean offsetWest) {
        float side = offsetWest ? -sideSpan : sideSpan;
        Vector3f dir = new Vector3f(side, sectionLength, 0.0F);
        if (dir.lengthSquared() < 1.0E-6F) {
            dir.set(0.0F, 1.0F, 0.0F);
        } else {
            dir.normalize();
        }

        Vector3f center = new Vector3f(offsetWest ? -PIXEL : 0.0F, centerOffset + HEADWARD_OFFSET, 0.0F);
        float halfLength = scale * 0.5F;
        Vector3f start = new Vector3f(dir).mul(-halfLength).add(center);
        Vector3f end = new Vector3f(dir).mul(halfLength).add(center);

        float minX = Math.min(start.x, end.x) - ARM_RADIUS;
        float minY = Math.min(start.y, end.y) - ARM_RADIUS;
        float minZ = Math.min(start.z, end.z) - ARM_RADIUS;
        float maxX = Math.max(start.x, end.x) + ARM_RADIUS;
        float maxY = Math.max(start.y, end.y) + ARM_RADIUS;
        float maxZ = Math.max(start.z, end.z) + ARM_RADIUS;

        float exposedStart = cellDistance + 1.0F - extension;
        if (exposedStart >= 1.0F - 1.0E-5F) {
            return shape;
        }
        minY = Math.max(minY, Mth.clamp(exposedStart, 0.0F, 1.0F) - 0.5F);
        if (maxY - minY <= 1.0E-5F) {
            return shape;
        }

        return Shapes.or(shape, rotatedClampedBox(baseRotation, minX, minY, minZ, maxX, maxY, maxZ));
    }

    // Get the rotated clamped box
    private static VoxelShape rotatedClampedBox(Quaternionf baseRotation, float minX, float minY, float minZ,
                                                float maxX, float maxY, float maxZ) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        includeRotatedCorner(baseRotation, minX, minY, minZ, min, max);
        includeRotatedCorner(baseRotation, maxX, minY, minZ, min, max);
        includeRotatedCorner(baseRotation, minX, maxY, minZ, min, max);
        includeRotatedCorner(baseRotation, maxX, maxY, minZ, min, max);
        includeRotatedCorner(baseRotation, minX, minY, maxZ, min, max);
        includeRotatedCorner(baseRotation, maxX, minY, maxZ, min, max);
        includeRotatedCorner(baseRotation, minX, maxY, maxZ, min, max);
        includeRotatedCorner(baseRotation, maxX, maxY, maxZ, min, max);
        return clampedBox(0.5F + min.x, 0.5F + min.y, 0.5F + min.z,
                0.5F + max.x, 0.5F + max.y, 0.5F + max.z);
    }

    // Include the rotated corner
    private static void includeRotatedCorner(Quaternionf baseRotation, float x, float y, float z,
                                             Vector3f min, Vector3f max) {
        Vector3f point = new Vector3f(x, y, z);
        baseRotation.transform(point);
        min.min(point);
        max.max(point);
    }

    // Get the clamped box
    private static VoxelShape clampedBox(float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ) {
        double clampedMinX = Mth.clamp(minX, 0.0F, 1.0F);
        double clampedMinY = Mth.clamp(minY, 0.0F, 1.0F);
        double clampedMinZ = Mth.clamp(minZ, 0.0F, 1.0F);
        double clampedMaxX = Mth.clamp(maxX, 0.0F, 1.0F);
        double clampedMaxY = Mth.clamp(maxY, 0.0F, 1.0F);
        double clampedMaxZ = Mth.clamp(maxZ, 0.0F, 1.0F);
        if (clampedMaxX - clampedMinX <= 1.0E-5D
                || clampedMaxY - clampedMinY <= 1.0E-5D
                || clampedMaxZ - clampedMinZ <= 1.0E-5D) {
            return Shapes.empty();
        }
        return Shapes.create(new AABB(clampedMinX, clampedMinY, clampedMinZ,
                clampedMaxX, clampedMaxY, clampedMaxZ));
    }

    // Get the rotation from up
    private static Quaternionf rotationFromUp(Direction dir) {
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), directionVector(dir));
    }

    // Get the direction vector
    private static Vector3f directionVector(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }
}
