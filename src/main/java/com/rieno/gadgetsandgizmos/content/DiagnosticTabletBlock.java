package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;

import java.util.ArrayList;
import java.util.List;

// Handle the placed Diagnostic Tablet
public class DiagnosticTabletBlock extends CTDirectionalBlock implements IBE<DiagnosticTabletBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final EnumProperty<DiagnosticTabletView> VIEW =
            EnumProperty.create("view", DiagnosticTabletView.class);
    public static final DirectionProperty HORIZONTAL_FACING =
            DirectionProperty.create("horizontal_facing", Direction.Plane.HORIZONTAL);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet block
    public DiagnosticTabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VIEW, DiagnosticTabletView.FLAT)
                .setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        Direction horizontal = facing.getAxis().isVertical() ? ctx.getHorizontalDirection().getOpposite() : facing;
        return defaultBlockState().setValue(FACING, facing).setValue(HORIZONTAL_FACING, horizontal);
    }

    // Register the model view alongside the mounting face
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder){
        super.createBlockStateDefinition(builder);
        builder.add(VIEW, HORIZONTAL_FACING);
    }

    // Rotate the mounting face and the floor or ceiling viewing direction together
    @Override
    public BlockState rotate(BlockState state, Rotation rotation){
        return super.rotate(state, rotation).setValue(HORIZONTAL_FACING,
                rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    // Mirror the viewing direction even when the mounting face is vertical
    @Override
    public BlockState mirror(BlockState state, Mirror mirror){
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(HORIZONTAL_FACING, mirror.mirror(state.getValue(HORIZONTAL_FACING)));
    }

    // Let Create update the model without rotating the mounting face
    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace){
        return state.setValue(VIEW, state.getValue(VIEW).next());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle diagnostic tablet block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return interactPlacedTablet(level, pos, player, hit);
    }

    // Let the wrench handle cycling and sneak pickup before screen interaction
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit){
        if(stack.getItem() instanceof WrenchItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // Handle the placed tablet
    public InteractionResult interactPlacedTablet(Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
        PlacedInteraction interaction = resolveInteraction(level, hit);
        return interaction == null ? InteractionResult.PASS
                : interactPlacedTablet(level, player, interaction);
    }

    // Handle the placed tablet
    public InteractionResult interactPlacedTablet(Level level, Player player,
                                                   PlacedInteraction interaction) {
        if (!CTFeatureToggles.isBlockEnabled("diagnostic_tablet")) {
            return InteractionResult.PASS;
        }
        DiagnosticTabletBlockEntity tablet = interaction.tablet();
        BlockHitResult hit = interaction.hit();
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) DiagnosticTabletItem.openClientScreen(tablet);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(DiagnosticTabletSurface.projectScreen(tablet, hit, player) == null){
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DiagnosticTabletItem.interactClientProjection(tablet, hit, 0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Resolve the interaction
    public static @Nullable PlacedInteraction resolveInteraction(Level level, BlockHitResult hit) {
        if (level == null || hit == null) return null;
        BlockEntity target = level.getBlockEntity(hit.getBlockPos());
        boolean localFrame = target instanceof DiagnosticTabletBlockEntity;
        if (!localFrame) {
            target = SimulatedHelper.findBlockEntityIncludingSubLevels(level, hit.getBlockPos());
        }
        if (!(target instanceof DiagnosticTabletBlockEntity tablet)
                || !(tablet.getBlockState().getBlock() instanceof DiagnosticTabletBlock block)) {
            return null;
        }

        BlockPos tabletPos = tablet.getBlockPos();
        Vec3 localLocation = localFrame ? hit.getLocation()
                : SimulatedHelper.toContainingLocalPosition(tablet, hit.getLocation());
        Direction localDirection = hit.getDirection();
        if (!localFrame) {
            Vec3 worldNormal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            Vec3 localNormal = SimulatedHelper.toContainingLocalDirection(tablet, worldNormal);
            localDirection = Direction.getNearest(localNormal.x, localNormal.y, localNormal.z);
        }
        BlockHitResult localHit = new BlockHitResult(localLocation, localDirection,
                tabletPos, hit.isInside());
        return new PlacedInteraction(block, tablet, localHit);
    }

    // Store the placed interaction
    public record PlacedInteraction(DiagnosticTabletBlock block,
                                    DiagnosticTabletBlockEntity tablet,
                                    BlockHitResult hit) {
    }

    // Check if this is screen hit
    public static boolean isScreenHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        if(hit == null) return false;
        var surface = DiagnosticTabletSurface.screen(state);
        if(surface.normal().dot(Vec3.atLowerCornerOf(hit.getDirection().getNormal())) <= 0.0D) return false;
        return surface.project(
                hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())) != null;
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return DiagnosticTabletSurface.shape(state);
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof DiagnosticTabletBlockEntity tablet) {
            tablet.setState(DiagnosticTabletData.read(stack));
            tablet.setPlayback(DiagnosticTabletMediaApp.readPlayback(stack));
        }
    }

    // Get the drops
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof DiagnosticTabletBlockEntity tablet) {
            copyTabletState(drops, tablet.state());
            for(ItemStack drop : drops){
                if(drop.getItem() instanceof DiagnosticTabletItem) DiagnosticTabletMediaApp.writePlayback(drop, tablet.playback());
            }
        }
        return drops;
    }

    // Get the clone item stack
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                       LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (!stack.isEmpty()
                && level.getBlockEntity(pos) instanceof DiagnosticTabletBlockEntity tablet) {
            DiagnosticTabletData.write(stack, tablet.state());
            DiagnosticTabletMediaApp.writePlayback(stack, tablet.playback());
        }
        return stack;
    }

    // Copy the tablet state
    private static void copyTabletState(List<ItemStack> drops,
                                        DiagnosticTabletData.State state) {
        for (ItemStack drop : drops) {
            if (drop.getItem() instanceof DiagnosticTabletItem) {
                DiagnosticTabletData.write(drop, state);
            }
        }
    }

    // Get the block entity class
    @Override
    public Class<DiagnosticTabletBlockEntity> getBlockEntityClass() {
        return DiagnosticTabletBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends DiagnosticTabletBlockEntity> getBlockEntityType() {
        return CTBlockEntities.DIAGNOSTIC_TABLET.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagnosticTabletBlockEntity(pos, state);
    }
}
