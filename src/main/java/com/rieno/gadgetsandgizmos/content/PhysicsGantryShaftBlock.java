package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.placement.PoleHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Build powered multipart shafts for physical gantry carriage movement
public class PhysicsGantryShaftBlock extends DirectionalKineticBlock
        implements IBE<PhysicsGantryShaftBlockEntity>, IWrenchable, BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Property<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());
    private static final Map<ShaftMoveKey, ShaftMoveContext> SHAFT_MOVE_CONTEXTS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry shaft block
    public PhysicsGantryShaftBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(PART, Part.SINGLE));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(PART, POWERED));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle physics gantry shaft block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!placementHelper.matchesItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        PlacementOffset offset = placementHelper.getOffset(player, level, state, pos, hitResult);
        if (offset.isSuccessful()) {
            BlockState placedState = offset.getTransform().apply(defaultBlockState());
            if (wouldJoinDuplicateShaftFaces(level, offset.getBlockPos(), placedState)) {
                showPlacementMessage(level, player, "createthrusters.physics_gantry.error.shaft_merge_multiple_carriages");
                return ItemInteractionResult.FAIL;
            }
        }
        return offset.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);
    }

    // Get the shape
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(FACING).getAxis());
    }

    // Get the render shape
    @Override
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // Update the shape
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour,
                                  LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = facing.getAxis();
        if (dir.getAxis() != axis) {
            return state;
        }

        boolean connect = CTBlocks.PHYSICS_GANTRY_SHAFT.get() == neighbour.getBlock() && neighbour.getValue(FACING) == facing;
        Part part = state.getValue(PART);

        if (dir.getAxisDirection() == facing.getAxisDirection()) {
            if (connect) {
                if (part == Part.END) part = Part.MIDDLE;
                if (part == Part.SINGLE) part = Part.START;
            } else {
                if (part == Part.MIDDLE) part = Part.END;
                if (part == Part.START) part = Part.SINGLE;
            }
        } else if (connect) {
            if (part == Part.START) part = Part.MIDDLE;
            if (part == Part.SINGLE) part = Part.END;
        } else {
            if (part == Part.MIDDLE) part = Part.START;
            if (part == Part.END) part = Part.SINGLE;
        }

        return state.setValue(PART, part);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }

        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        Direction face = ctx.getClickedFace();

        BlockState neighbour = world.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
        BlockState clickedState = CTBlocks.PHYSICS_GANTRY_SHAFT.get() == neighbour.getBlock()
                ? neighbour
                : world.getBlockState(pos.relative(face.getOpposite()));

        if (CTBlocks.PHYSICS_GANTRY_SHAFT.get() == clickedState.getBlock()
                && clickedState.getValue(FACING).getAxis() == state.getValue(FACING).getAxis()) {
            Direction facing = clickedState.getValue(FACING);
            state = state.setValue(FACING,
                    ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown() ? facing : facing.getOpposite());
        }

        state = state.setValue(POWERED, false);
        if (wouldJoinDuplicateShaftFaces(world, pos, state)) {
            showPlacementMessage(world, ctx.getPlayer(),
                    "createthrusters.physics_gantry.error.shaft_merge_multiple_carriages");
            return null;
        }
        return state;
    }

    // Check if this would join duplicate shaft faces
    private static boolean wouldJoinDuplicateShaftFaces(Level level, BlockPos shaftPos, BlockState shaftState) {
        return PhysicsGantryCarriageBlockEntity.wouldJoinDuplicateShaftFaces(
                level, shaftPos, shaftState.getValue(FACING), null);
    }

    // Show the placement message
    private static void showPlacementMessage(Level level, Player player, String translationKey) {
        if (!level.isClientSide && player != null) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        InteractionResult onWrenched = super.onWrenched(state, ctx);
        if (onWrenched.consumesAction()) {
            BlockPos pos = ctx.getClickedPos();
            Level world = ctx.getLevel();
            neighborChanged(world.getBlockState(pos), world, pos, state.getBlock(), pos, false);
        }
        return onWrenched;
    }

    // Handle the place event
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && oldState.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            Part oldPart = oldState.getValue(PART);
            Part part = state.getValue(PART);
            if ((oldPart != Part.MIDDLE && part == Part.MIDDLE || oldPart == Part.SINGLE && part != Part.SINGLE)
                    && level.getBlockEntity(pos) instanceof PhysicsGantryShaftBlockEntity be) {
                be.checkAttachedCarriageBlocks();
            }
        }
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos neighbourPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }

        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 2);
        }
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Check if the states are kinetically equivalent
    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState);
    }

    // Get the particle target radius
    @Override
    public float getParticleTargetRadius() {
        return 0.35f;
    }

    // Get the particle initial radius
    @Override
    public float getParticleInitialRadius() {
        return 0.25f;
    }

    // Check if this is pathfindable
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (originLevel == null || oldPos == null || newPos == null) {
            return;
        }

        UUID sourceSubLevelId = null;
        BlockEntity shaft = originLevel.getBlockEntity(oldPos);
        if (shaft != null) {
            sourceSubLevelId = SimulatedHelper.getContainingSubLevelId(shaft);
        }
        SHAFT_MOVE_CONTEXTS.put(new ShaftMoveKey(originLevel.dimension().location().toString(), oldPos.immutable(), newPos.immutable()),
                new ShaftMoveContext(sourceSubLevelId));
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (originLevel == null || resultingLevel == null || oldPos == null || newPos == null) {
            return;
        }

        ShaftMoveContext moveContext = SHAFT_MOVE_CONTEXTS.remove(
                new ShaftMoveKey(originLevel.dimension().location().toString(), oldPos.immutable(), newPos.immutable()));
        Direction shaftDirection = newState.getValue(FACING);
        UUID shaftSubLevelId = null;
        BlockEntity movedShaft = resultingLevel.getBlockEntity(newPos);
        if (movedShaft != null) {
            shaftSubLevelId = SimulatedHelper.getContainingSubLevelId(movedShaft);
        }

        Set<PhysicsGantryCarriageBlockEntity> candidates = new LinkedHashSet<>();
        collectNearbyAttachedCarriages(originLevel, oldPos, shaftDirection, candidates);
        collectNearbyAttachedCarriages(resultingLevel, oldPos, shaftDirection, candidates);
        collectNearbyAttachedCarriages(resultingLevel, newPos, shaftDirection, candidates);
        collectCarriagesAttachedToMovedShaft(resultingLevel, oldPos, shaftDirection,
                moveContext == null ? null : moveContext.sourceSubLevelId(), candidates);

        AABB searchBounds = new AABB(oldPos).inflate(80.0D);
        for (Object subLevel : SimulatedHelper.getIntersectingSubLevels(resultingLevel, searchBounds)) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage) {
                    candidates.add(carriage);
                }
            }
        }

        for (PhysicsGantryCarriageBlockEntity carriage : candidates) {
            if (!carriage.retargetAttachedShaftAfterMove(oldPos,
                    moveContext == null ? null : moveContext.sourceSubLevelId(),
                    newPos,
                    shaftDirection,
                    shaftSubLevelId)) {
                carriage.retargetAttachedShaftAfterAssembly(oldPos, newPos, shaftDirection, shaftSubLevelId);
            }
        }
    }

    // Collect the nearby attached carriages
    private static void collectNearbyAttachedCarriages(Level level, BlockPos shaftPos, Direction shaftDirection,
                                                       Set<PhysicsGantryCarriageBlockEntity> candidates) {
        if (level == null || shaftPos == null || shaftDirection == null || candidates == null) {
            return;
        }

        for (Direction dir : Iterate.directions) {
            if (dir.getAxis() == shaftDirection.getAxis()) {
                continue;
            }

            BlockPos carriagePos = shaftPos.relative(dir);
            PhysicsGantryCarriageBlockEntity carriage = SimulatedHelper.findBlockEntityIncludingSubLevels(
                    level,
                    carriagePos,
                    PhysicsGantryCarriageBlockEntity.class);
            if (carriage != null && carriage.isAttachedToShaftBlock(shaftPos, shaftDirection)) {
                candidates.add(carriage);
            }
        }
    }

    // Collect the carriages attached to moved shaft
    private static void collectCarriagesAttachedToMovedShaft(Level level, BlockPos shaftPos, Direction shaftDirection,
                                                             UUID shaftSubLevelId,
                                                             Set<PhysicsGantryCarriageBlockEntity> candidates) {
        if (level == null || shaftPos == null || shaftDirection == null || candidates == null) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        for (Object subLevel : container.getAllSubLevels()) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage
                        && carriage.isAttachedToShaftBlock(shaftPos, shaftDirection, shaftSubLevelId)) {
                    candidates.add(carriage);
                }
            }
        }
    }

    // Get the block entity class
    @Override
    public Class<PhysicsGantryShaftBlockEntity> getBlockEntityClass() {
        return PhysicsGantryShaftBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends PhysicsGantryShaftBlockEntity> getBlockEntityType() {
        return CTBlockEntities.PHYSICS_GANTRY_SHAFT.get();
    }

    // Define the part values
    public enum Part implements StringRepresentable {
        START,
        MIDDLE,
        END,
        SINGLE;

        // Get the serialized name
        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }

    // Handle shared placement operations
    public static class PlacementHelper extends PoleHelper<Direction> {
        // Initialize the placement
        public PlacementHelper() {
            super(s -> s.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get(),
                    s -> s.getValue(DirectionalKineticBlock.FACING).getAxis(),
                    DirectionalKineticBlock.FACING);
        }

        // Get the item predicate
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.is(CTBlocks.PHYSICS_GANTRY_SHAFT.get().asItem());
        }

        // Get the offset
        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
            offset.withTransform(offset.getTransform().andThen(s -> s.setValue(POWERED, false)));
            return offset;
        }
    }

    // Store the shaft move key
    private record ShaftMoveKey(String dimension, BlockPos oldPos, BlockPos newPos) {
    }

    // Store shaft move context
    private record ShaftMoveContext(UUID sourceSubLevelId) {
    }
}
