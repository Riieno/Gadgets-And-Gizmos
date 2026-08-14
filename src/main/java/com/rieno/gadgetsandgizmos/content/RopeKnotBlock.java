package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Hold a small movable attachment point along a rope strand
public class RopeKnotBlock extends Block implements RopeHolderBlock<RopeKnotBlockEntity>, BlockSubLevelCollisionShape, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double HALF_SIZE = 1.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope knot block
    public RopeKnotBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 offset = level.getBlockEntity(pos) instanceof RopeKnotBlockEntity knot
                ? knot.getAttachmentOffset()
                : new Vec3(0.5D, 0.5D, 0.5D);
        double x = offset.x * 16.0D;
        double y = offset.y * 16.0D;
        double z = offset.z * 16.0D;
        return Block.box(x - HALF_SIZE, y - HALF_SIZE, z - HALF_SIZE,
                x + HALF_SIZE, y + HALF_SIZE, z + HALF_SIZE);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {
        return Shapes.empty();
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

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        com.simibubi.create.foundation.block.IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        RopeHolderBlock.super.afterMove(originLevel, resultingLevel, newState, oldPos, newPos);
    }

    // Get the block entity class
    @Override
    public Class<RopeKnotBlockEntity> getBlockEntityClass() {
        return RopeKnotBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends RopeKnotBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ROPE_KNOT.get();
    }
}
