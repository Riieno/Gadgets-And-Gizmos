package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.geometry.ScissorLinkageLayout;
import com.rieno.gadgetsandgizmos.lib.geometry.BlockVolumeProvider;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    // Fit the shared linkage geometry to the scissor arm model
    public static final ScissorLinkageLayout.Profile ARM_PROFILE =
            new ScissorLinkageLayout.Profile(4.0 / 16, 1.0 / 16, 1.5 / 16, .9, 1);

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

    // Select the same continuous column from every occupied forwarding cell
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

    // Shorten the shared volume without cutting a hole in its forwarding cells
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid){
        if(level.getBlockEntity(pos) instanceof ScissorPistonArmBlockEntity arm){
            var head = arm.getPistonHead();
            if(head != null){
                if(!level.isClientSide) head.breakArm(player);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
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

    // Expose the head-owned volume from every occupied arm cell
    private static VoxelShape armShape(BlockGetter level, BlockPos pos){
        var volume = BlockVolumeProvider.find(level, pos);
        return volume == null ? Shapes.empty() : volume.shapeAt(pos);
    }
}
