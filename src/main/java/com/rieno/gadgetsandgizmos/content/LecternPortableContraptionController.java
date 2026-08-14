package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// Open and drive a portable controller mounted in a lectern while preserving its item data
public final class LecternPortableContraptionController {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the lectern portable contraption
    private LecternPortableContraptionController() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Place the lectern
    public static boolean placeOnLectern(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                || state.getValue(LecternBlock.HAS_BOOK)
                || !getControllerStack(level, pos).isEmpty()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof LecternBlockEntity lectern)) {
            return false;
        }

        PortableContraptionControllerRuntime.transferToLectern(
                level, pos, player, stack, portable.isAdvanced());
        lectern.setBook(stack.consumeAndReturn(1, player));
        PortableContraptionControllerRuntime.activateLectern(level, pos, portable.isAdvanced());
        BlockState updatedState = portableLecternState(state);
        level.setBlock(pos, updatedState, 3);
        syncLectern(level, pos);
        level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return true;
    }

    // Handle the portable lectern
    public static InteractionResult usePortableLectern(BlockState state, Level level, BlockPos pos, Player player) {
        ItemStack stack = getControllerStack(level, pos);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)) {
            return InteractionResult.PASS;
        }
        ensurePortableLecternState(level, pos);

        boolean advanced = portable.isAdvanced();
        if (PortableContraptionControllerItem.wantsMenu(player)) {
            if (level.isClientSide) {
                clearClientLecternInteractMode(pos);
            }
            return retrieveController(level, pos, player)
                    ? InteractionResult.sidedSuccess(level.isClientSide)
                    : InteractionResult.PASS;
        }

        if (level.isClientSide) {
            toggleClientLecternInteractMode(pos, advanced);
        }
        return InteractionResult.SUCCESS;
    }

    // Retrieve the lectern controller
    public static boolean retrieveController(Level level, BlockPos pos, Player player) {
        ItemStack stack = getControllerStack(level, pos);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem) || player == null) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof LecternBlockEntity lectern)) {
            return false;
        }

        ItemStack retrieved = lectern.getBook().copyAndClear();
        if (!(retrieved.getItem() instanceof PortableContraptionControllerItem)) {
            return false;
        }
        PortableContraptionControllerRuntime.copyLecternRuntimeToStack(level, pos, retrieved);
        ItemStack retrievedTemplate = retrieved.copy();
        ItemStack[] inventoryBefore = snapshotInventory(player.getInventory());
        finishLecternClear(level, pos, lectern, player, true);
        boolean added = player.getInventory().add(retrieved);
        ItemStack destination = added
                ? findAddedStack(player.getInventory(), inventoryBefore, retrievedTemplate)
                : retrieved;
        PortableContraptionControllerRuntime.finishLecternRemoval(
                level, pos, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, destination);
        if (!added) {
            player.drop(retrieved, false);
        }
        level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.8F, 0.8F);
        return true;
    }

    // Drop the lectern controller
    public static boolean dropController(Level level, BlockPos pos) {
        ItemStack stack = getControllerStack(level, pos);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof LecternBlockEntity lectern)) {
            return false;
        }
        ItemStack dropped = lectern.getBook().copyAndClear();
        PortableContraptionControllerRuntime.stopLecternAt(level, pos);
        if (!dropped.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), dropped);
        }
        finishLecternClear(level, pos, lectern, null, false);
        return true;
    }

    // Check if this is a portable lectern
    public static boolean isPortableLectern(Level level, BlockPos pos) {
        return getControllerStack(level, pos).getItem() instanceof PortableContraptionControllerItem;
    }

    // Get the controller stack
    public static ItemStack getControllerStack(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return ItemStack.EMPTY;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LecternBlockEntity lectern) {
            return lectern.getBook();
        }
        return ItemStack.EMPTY;
    }

    // Check if the portable is matching
    public static boolean isMatchingPortable(ItemStack stack, boolean advanced) {
        return stack.getItem() instanceof PortableContraptionControllerItem portable
                && portable.isAdvanced() == advanced;
    }

    // Create the controller from lectern
    public static @Nullable AnalogueContraptionControllerBlockEntity createControllerFromLectern(Level level,
                                                                                                  BlockPos pos,
                                                                                                  boolean advanced) {
        ItemStack stack = getControllerStack(level, pos);
        if (!isMatchingPortable(stack, advanced)) {
            return null;
        }
        return PortableContraptionControllerItem.createControllerFromStack(stack, level, advanced, pos);
    }

    // Save the controller to lectern
    public static boolean saveControllerToLectern(Level level, BlockPos pos,
                                                  AnalogueContraptionControllerBlockEntity controller,
                                                  boolean advanced,
                                                  @Nullable CompoundTag portableMenuContainers) {
        ItemStack stack = getControllerStack(level, pos);
        if (!isMatchingPortable(stack, advanced)) {
            return false;
        }
        boolean stackChanged = PortableContraptionControllerItem.saveControllerToStackIfChanged(
                stack, controller, advanced, portableMenuContainers);
        if (!stackChanged) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LecternBlockEntity lectern) {
            lectern.setChanged();
        }
        ensurePortableLecternState(level, pos);
        syncLectern(level, pos);
        notifyOutputNeighbors(level, pos);
        return true;
    }

    // Get the comparator output
    public static int getComparatorOutput(Level level, BlockPos pos, Direction comparatorFacing) {
        if (!isPortableLectern(level, pos) || comparatorFacing == null) {
            return -1;
        }
        Direction lecternSide = comparatorFacing.getOpposite();
        if (lecternSide.getAxis().isVertical()) {
            return 0;
        }

        int liveSignal = PortableContraptionControllerRuntime.getLecternLocalOutput(level, pos, lecternSide);
        if (liveSignal >= 0) {
            return liveSignal;
        }

        ItemStack stack = getControllerStack(level, pos);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)) {
            return -1;
        }
        AnalogueContraptionControllerBlockEntity controller =
                PortableContraptionControllerItem.createControllerFromStack(stack, level, portable.isAdvanced(), pos);
        return controller.getLocalOutputSignal(lecternSide);
    }

    // Get the max comparator output
    public static int getMaxComparatorOutput(Level level, BlockPos pos) {
        if (!isPortableLectern(level, pos)) {
            return -1;
        }
        int max = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int liveSignal = PortableContraptionControllerRuntime.getLecternLocalOutput(level, pos, dir);
            if (liveSignal >= 0) {
                max = Math.max(max, liveSignal);
            } else {
                ItemStack stack = getControllerStack(level, pos);
                if (stack.getItem() instanceof PortableContraptionControllerItem portable) {
                    AnalogueContraptionControllerBlockEntity controller =
                            PortableContraptionControllerItem.createControllerFromStack(
                                    stack, level, portable.isAdvanced(), pos);
                    max = Math.max(max, controller.getLocalOutputSignal(dir));
                }
            }
        }
        return max;
    }

    // Notify the output neighbors
    public static void notifyOutputNeighbors(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.below(), state.getBlock());
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            level.updateNeighborsAt(pos.relative(dir), state.getBlock());
        }
    }

    // Get the portable lectern state
    private static BlockState portableLecternState(BlockState state) {
        if (state.hasProperty(LecternBlock.HAS_BOOK)) {
            state = state.setValue(LecternBlock.HAS_BOOK, false);
        }
        if (state.hasProperty(LecternBlock.POWERED)) {
            state = state.setValue(LecternBlock.POWERED, false);
        }
        return state;
    }

    // Ensure the portable lectern state
    private static void ensurePortableLecternState(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState updatedState = portableLecternState(state);
        if (updatedState != state) {
            level.setBlock(pos, updatedState, 3);
        }
    }

    // Sync the lectern
    private static void syncLectern(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    // Finish the lectern clear
    private static void finishLecternClear(Level level, BlockPos pos, LecternBlockEntity lectern,
                                           @Nullable Player player, boolean resetState) {
        lectern.clearContent();
        lectern.setChanged();
        BlockState state = level.getBlockState(pos);
        if (resetState && state.getBlock() instanceof LecternBlock) {
            if (level.isClientSide) {
                level.setBlock(pos, portableLecternState(state), 3);
            } else {
                LecternBlock.resetBookState(player, level, pos, state, false);
            }
        }
        syncLectern(level, pos);
        notifyOutputNeighbors(level, pos);
    }

    // Capture item identity before insertion can merge the stack
    private static ItemStack[] snapshotInventory(Inventory inventory) {
        ItemStack[] snapshot = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = inventory.getItem(slot);
        }
        return snapshot;
    }

    // Find the added stack
    private static ItemStack findAddedStack(Inventory inventory, ItemStack[] prev, ItemStack template) {
        ItemStack fallback = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(candidate, template)) {
                continue;
            }
            fallback = candidate;
            boolean existed = false;
            for (ItemStack prior : prev) {
                if (candidate == prior) {
                    existed = true;
                    break;
                }
            }
            if (!existed) {
                return candidate;
            }
        }
        return fallback;
    }

    // Open the menu
    private static void openMenu(Level level, Player player, BlockPos pos, ItemStack stack, boolean advanced) {
        if (level.isClientSide || !(player instanceof ServerPlayer)) {
            return;
        }
        if (isMatchingPortableMenuOpen(player.containerMenu, advanced, pos)) {
            return;
        }

        AnalogueContraptionControllerBlockEntity controller =
                PortableContraptionControllerItem.createControllerFromStack(stack, level, advanced, pos);
        PortableControllerSaveTarget saveTarget = (changedController, portableMenuContainers) ->
                saveControllerToLectern(level, pos, changedController, advanced, portableMenuContainers);
        MenuProvider provider = new MenuProvider() {
            // Get the display name
            @Override
            public Component getDisplayName() {
                return controller.getDisplayName();
            }

            // Create the menu
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                if (advanced && controller instanceof PortableAdvancedContraptionControllerBlockEntity advancedController) {
                    AdvancedControllerGraphSnapshotPayload.send(
                            (ServerPlayer) menuPlayer,
                            advancedController.getBlockPos(),
                            SimulatedHelper.getContainingSubLevelId(advancedController),
                            advancedController.getDraftGraph(),
                            advancedController.getActiveGraph());
                    return new PortableAdvancedContraptionControllerMenu(containerId, inventory, advancedController,
                            stack, saveTarget);
                }
                if (controller instanceof PortableAnalogueContraptionControllerBlockEntity portableController) {
                    return new PortableAnalogueContraptionControllerMenu(containerId, inventory, portableController,
                            saveTarget);
                }
                return null;
            }
        };
        CompoundTag portableData = PortableContraptionControllerItem.readControllerData(stack);
        player.openMenu(provider, buffer -> {
            controller.sendToMenu(buffer);
            buffer.writeNbt(portableData.copy());
        });
    }

    // Check if the matching portable menu is open
    private static boolean isMatchingPortableMenuOpen(AbstractContainerMenu menu, boolean advanced, BlockPos pos) {
        if (advanced && menu instanceof PortableAdvancedContraptionControllerMenu portableMenu) {
            return Objects.equals(portableMenu.getContentPos(), pos);
        }
        if (!advanced && menu instanceof PortableAnalogueContraptionControllerMenu portableMenu) {
            return Objects.equals(portableMenu.getContentPos(), pos);
        }
        return false;
    }

    // Toggle lectern interaction mode
    private static void toggleClientLecternInteractMode(BlockPos pos, boolean advanced) {
        try {
            Class<?> handlerClass = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
            handlerClass.getMethod("toggleLecternPortableInteractMode", BlockPos.class, boolean.class)
                    .invoke(null, pos, advanced);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Clear the client lectern interact mode
    private static void clearClientLecternInteractMode(BlockPos pos) {
        try {
            Class<?> handlerClass = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
            handlerClass.getMethod("clearLecternPortableInteractMode", BlockPos.class).invoke(null, pos);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
