package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet block
    public DiagnosticTabletBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
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
        BlockState state = tablet.getBlockState();
        BlockPos pos = tablet.getBlockPos();
        BlockHitResult hit = interaction.hit();
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) DiagnosticTabletItem.openClientScreen(tablet);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!isScreenHit(state, pos, hit)) {
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
        Direction facing = state.getValue(FACING);
        if (hit == null || hit.getDirection() != facing) return false;
        return DiagnosticTabletSurface.screen(facing).project(
                hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())) != null;
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return DiagnosticTabletSurface.shape(state.getValue(FACING));
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof DiagnosticTabletBlockEntity tablet) {
            tablet.setState(DiagnosticTabletData.read(stack));
        }
    }

    // Get the drops
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof DiagnosticTabletBlockEntity tablet) {
            copyTabletState(drops, tablet.state());
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
