package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.lib.geometry.BlockVolumeProvider;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Place and configure the remote head of a physical scissor piston
public class ScissorPistonLinkBlock extends CTDirectionalBlock
        implements IBE<ScissorPistonLinkBlockEntity>, BlockSubLevelAssemblyListener,
        BlockSubLevelCollisionShape {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final VoxelShape DOWN_SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape UP_SHAPE = Block.box(0, 12, 0, 16, 16, 16);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 16, 16, 4);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 12, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 0, 0, 4, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(12, 0, 0, 16, 16, 16);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston link block
    public ScissorPistonLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Select the full column from the moving head
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        var volume = BlockVolumeProvider.find(level, pos);
        return volume == null ? shapeForFacing(state.getValue(FACING)) : volume.shapeAt(pos);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return ScissorPistonPhysicsShapes.headShape(state.getValue(FACING));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle scissor piston link block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.mayBuild() || player.isCrouching() || isArmTarget(level, pos, state, player)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return level.getBlockEntity(pos) instanceof ScissorPistonLinkBlockEntity link
                && link.disassembleParent()
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    // Handle scissor piston link block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult res = useWithoutItem(state, level, pos, player, hit);
        return res.consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(ctx.getClickedPos()) instanceof ScissorPistonLinkBlockEntity link) {
            return link.removeExtensionFromParent(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    // Handle crouching wrench use
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        return onWrenched(state, ctx);
    }

    // Use the arm's mining speed when targeting the column below the head plate
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos){
        if(isArmTarget(level, pos, state, player)){
            return CTBlocks.SCISSOR_PISTON_ARM.get()
                    .defaultBlockState().getDestroyProgress(player, level, pos);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // Keep mining the upper arm from destroying the actual moving head
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid){
        var link = level.getBlockEntity(pos) instanceof ScissorPistonLinkBlockEntity head ? head : null;
        if(link != null && isArmTarget(level, pos, state, player)){
            if(!level.isClientSide) link.breakArm(player);
            return false;
        }
        var parent = link == null ? null : link.getParentPiston();
        boolean guard = !level.isClientSide && parent != null && !parent.isInternalArmBlockUpdate();
        if(guard) parent.beginInternalArmBlockUpdate();
        try{
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        }finally{
            if(guard) parent.endInternalArmBlockUpdate();
        }
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, ScissorPistonLinkBlockEntity::beginAssemblyTransfer);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, ScissorPistonLinkBlockEntity::finishAssemblyTransfer);
    }

    // Get the block entity class
    @Override
    public Class<ScissorPistonLinkBlockEntity> getBlockEntityClass() {
        return ScissorPistonLinkBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ScissorPistonLinkBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SCISSOR_PISTON_LINK.get();
    }

    // Get the shape for facing
    private static VoxelShape shapeForFacing(Direction facing) {
        return switch (facing) {
            case DOWN -> DOWN_SHAPE;
            case UP -> UP_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
        };
    }

    // Distinguish the extending column from the head plate in the active interaction frame
    private static boolean isArmTarget(BlockGetter level, BlockPos pos, BlockState state, Player player){
        var volume = BlockVolumeProvider.find(level, pos);
        if(volume == null) return false;
        var body = SableLevelApi.containing(player.level(), pos);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        Vec3 from = SableTransformApi.contextualPosition(player.level(), null,
                SableTransformApi.projectOut(player.level(), eye), body);
        Vec3 to = SableTransformApi.contextualPosition(player.level(), null,
                SableTransformApi.projectOut(player.level(), end), body);
        return volume.hitBefore(from, to, shapeForFacing(state.getValue(FACING)));
    }

}
