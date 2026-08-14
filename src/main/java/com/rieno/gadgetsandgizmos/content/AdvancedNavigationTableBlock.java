package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableMapResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Insert maps and open navigation controls for a ship-aware navigation table
public class AdvancedNavigationTableBlock extends NavTableBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<AdvancedNavigationTableBlock> CODEC =
            simpleCodec(AdvancedNavigationTableBlock::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced navigation table block
    public AdvancedNavigationTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the codec
    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

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

    // Handle advanced navigation table block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return openMenu(level, pos, player);
        }
        return retrieveSelectedMap(level, pos, player).consumesAction()
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    // Handle advanced navigation table block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            InteractionResult res = openMenu(level, pos, player);
            return res.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            InteractionResult res = retrieveSelectedMap(level, pos, player);
            return res.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!NavigationTableMapResolver.isNavigationMap(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof NavigationTableExtensionAccess nav)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int selectedSlot = nav.ct$getSelectedSlot();
        int targetSlot = nav.ct$getMapInSlot(selectedSlot).isEmpty() ? selectedSlot : -1;
        if (targetSlot < 0) {
            for (int slot = 0; slot < NavigationTableExtensionAccess.SLOT_COUNT; slot++) {
                if (nav.ct$getMapInSlot(slot).isEmpty()) {
                    targetSlot = slot;
                    break;
                }
            }
        }
        if (targetSlot < 0) {
            targetSlot = selectedSlot;
        }

        ItemStack replaced = nav.ct$removeMapInSlot(targetSlot, player);
        if (!replaced.isEmpty()) {
            player.getInventory().placeItemBackInInventory(replaced);
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        nav.ct$setMapInSlot(targetSlot, copy, player);
        nav.ct$setSelectedSlot(targetSlot);
        nav.ct$startNavigation();
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        playInsertSound(level, pos);
        return ItemInteractionResult.CONSUME;
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof NavigationTableExtensionAccess nav) {
                for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
                    ItemStack stack = nav.ct$removeMapInSlot(i, null);
                    if (!stack.isEmpty()) {
                        Block.popResource(level, pos, stack);
                    }
                }
            }
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends AdvancedNavigationTableBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedNavigationTableBlockEntity(pos, state);
    }

    // Handle the neighboring block change
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AdvancedNavigationTableBlockEntity navTable) {
            navTable.updateRedstoneSlotSelection();
        }
    }

    // Check if the block connects to redstone
    @Override
    public boolean commonConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction dir) {
        if (dir == AdvancedNavigationTableBlockEntity.getRedstoneInputFace(state)) {
            return true;
        }
        return super.commonConnectRedstone(state, level, pos, dir);
    }

    // Get the required items
    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        ItemStack tableStack = new ItemStack(CTBlocks.ADVANCED_NAVIGATION_TABLE.get());
        if (!(blockEntity instanceof NavigationTableExtensionAccess nav)) {
            return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, tableStack);
        }

        List<ItemRequirement.StackRequirement> requirements = new ArrayList<>();
        requirements.add(new ItemRequirement.StackRequirement(tableStack, ItemRequirement.ItemUseType.CONSUME));
        for (int i = 0; i < NavigationTableExtensionAccess.SLOT_COUNT; i++) {
            ItemStack stack = nav.ct$getMapInSlot(i);
            if (!stack.isEmpty()) {
                requirements.add(new ItemRequirement.StrictNbtStackRequirement(stack.copy(),
                        ItemRequirement.ItemUseType.CONSUME));
            }
        }
        return new ItemRequirement(requirements);
    }

    // Open the menu
    private static InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AdvancedNavigationTableBlockEntity navTable)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                            new NavigationTableMenu(id, inventory, pos,
                                    SimulatedHelper.getContainingSubLevelId(navTable)),
                    Component.translatable("createthrusters.navigation_table.screen.title")),
                    buffer -> MenuOpenHeader.encode(buffer, pos, SimulatedHelper.getContainingSubLevelId(navTable)));
        }
        return InteractionResult.CONSUME;
    }

    // Retrieve the selected map
    private static InteractionResult retrieveSelectedMap(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof NavigationTableExtensionAccess nav)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return !nav.ct$getMapInSlot(nav.ct$getSelectedSlot()).isEmpty()
                    || nav.ct$getRunState() != NavigationTableExtensionAccess.RunState.IDLE
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        boolean changed = false;
        ItemStack selectedMap = nav.ct$removeMapInSlot(nav.ct$getSelectedSlot(), player);
        if (!selectedMap.isEmpty()) {
            player.getInventory().placeItemBackInInventory(selectedMap);
            changed = true;
        }
        if (nav.ct$getRunState() != NavigationTableExtensionAccess.RunState.IDLE) {
            nav.ct$stopNavigation();
            changed = true;
        }
        if (changed) {
            playExtractSound(level, pos);
        }
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    // Play the insert sound
    private static void playInsertSound(Level level, BlockPos pos) {
        float pitch = 0.8f + level.random.nextFloat() * 0.4f;
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.75f, pitch);
    }

    // Play the extract sound
    private static void playExtractSound(Level level, BlockPos pos) {
        float pitch = 0.8f + level.random.nextFloat() * 0.4f;
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.75f, pitch);
    }
}
