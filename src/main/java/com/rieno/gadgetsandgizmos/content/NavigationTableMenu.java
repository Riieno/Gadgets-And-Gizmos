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
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Sync Navigation Table settings
public class NavigationTableMenu extends AbstractContainerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAP_SLOT_START = 0;
    public static final int MAP_SLOT_COUNT = NavigationTableExtensionAccess.SLOT_COUNT;
    public static final int PLAYER_SLOT_START = MAP_SLOT_START + MAP_SLOT_COUNT;

    public static final int MAP_GRID_X = 18;
    public static final int MAP_GRID_Y = 34;
    public static final int PLAYER_SLOTS_X = 34;
    public static final int PLAYER_SLOTS_Y = 262;
    public static final int HOTBAR_SLOTS_Y = 320;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Content holder
    private final @Nullable NavigationTableExtensionAccess contentHolder;
    // Map container
    private final Container mapContainer;
    // Content position
    private final BlockPos contentPos;
    // Content sub-level id
    private final UUID contentSubLevelId;
    // Player
    private final Player player;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table menu
    public NavigationTableMenu(int id, Inventory playerInventory, BlockPos contentPos, UUID contentSubLevelId) {
        super(CTMenuTypes.NAVIGATION_TABLE.get(), id);
        this.contentPos = contentPos.immutable();
        this.contentSubLevelId = contentSubLevelId;
        this.player = playerInventory.player;
        this.contentHolder = resolveHolder(playerInventory);
        this.mapContainer = createMapContainer();
        addSlots(playerInventory);
    }

    // Initialize the navigation table menu
    public NavigationTableMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(CTMenuTypes.NAVIGATION_TABLE.get(), id);
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        this.contentPos = header.pos().immutable();
        this.contentSubLevelId = header.subLevelId();
        this.player = playerInventory.player;
        this.contentHolder = resolveHolder(playerInventory);
        this.mapContainer = createMapContainer();
        addSlots(playerInventory);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the holder
    private @Nullable NavigationTableExtensionAccess resolveHolder(Inventory playerInventory) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof NavigationTableExtensionAccess ext ? ext : null;
    }

    // Create the map container
    private Container createMapContainer() {
        return new Container() {
            // Get the container size
            @Override
            public int getContainerSize() {
                return MAP_SLOT_COUNT;
            }

            // Check if this is empty
            @Override
            public boolean isEmpty() {
                for (int i = 0; i < MAP_SLOT_COUNT; i++) {
                    if (!getItem(i).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            // Get the item
            @Override
            public ItemStack getItem(int slot) {
                if (contentHolder == null) {
                    return ItemStack.EMPTY;
                }
                return contentHolder.ct$getMapInSlot(slot);
            }

            // Remove the item
            @Override
            public ItemStack removeItem(int slot, int amount) {
                if (contentHolder == null) {
                    return ItemStack.EMPTY;
                }
                if (amount <= 0 || getItem(slot).isEmpty()) {
                    return ItemStack.EMPTY;
                }
                return contentHolder.ct$removeMapInSlot(slot, player);
            }

            // Remove the item no update
            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                if (contentHolder == null) {
                    return ItemStack.EMPTY;
                }
                return contentHolder.ct$removeMapInSlot(slot, player);
            }

            // Set the item
            @Override
            public void setItem(int slot, ItemStack stack) {
                if (contentHolder == null) {
                    return;
                }

                contentHolder.ct$setMapInSlot(slot, stack, player);
            }

            // Set the changed
            @Override
            public void setChanged() {
            }

            // Check if the still is valid
            @Override
            public boolean stillValid(Player player) {
                return contentHolder != null && player.distanceToSqr(
                        contentPos.getX() + 0.5,
                        contentPos.getY() + 0.5,
                        contentPos.getZ() + 0.5) <= 64.0;
            }

            // Clear the content
            @Override
            public void clearContent() {
                if (contentHolder == null) {
                    return;
                }
                for (int i = 0; i < MAP_SLOT_COUNT; i++) {
                    contentHolder.ct$setMapInSlot(i, ItemStack.EMPTY);
                }
            }
        };
    }

    // Get the content holder
    public @Nullable NavigationTableExtensionAccess getContentHolder() {
        return contentHolder;
    }

    // Get the content pos
    public BlockPos getContentPos() {
        return contentPos;
    }

    // Get the content sublevel id
    public UUID getContentSubLevelId() {
        return contentSubLevelId;
    }

    // Get the selected slot
    public int getSelectedSlot() {
        return contentHolder == null ? 0 : contentHolder.ct$getSelectedSlot();
    }

    // Get the run state
    public NavigationTableExtensionAccess.RunState getRunState() {
        return contentHolder == null ? NavigationTableExtensionAccess.RunState.IDLE : contentHolder.ct$getRunState();
    }

    // Get the relative angle deg
    public float getRelativeAngleDeg() {
        return contentHolder == null ? 0.0f : contentHolder.ct$getRelativeAngleDeg();
    }

    // Get the target label
    public String getTargetLabel() {
        if (contentHolder == null || contentHolder.ct$getTargetLabel() == null) {
            return "";
        }
        return contentHolder.ct$getTargetLabel();
    }

    // Add the slots
    private void addSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 5; ++col) {
                int slotIndex = col + row * 5;
                addSlot(new Slot(mapContainer, slotIndex, MAP_GRID_X + 2 + col * 18, MAP_GRID_Y + 2 + row * 18) {
                    // Check if this may place
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return NavigationTableMapResolver.isNavigationMap(stack);
                    }

                    // Get the max stack size
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot((Container) playerInventory, col + row * 9 + 9,
                        PLAYER_SLOTS_X + col * 18, PLAYER_SLOTS_Y + row * 18));
            }
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
            addSlot(new Slot((Container) playerInventory, hotbarSlot,
                    PLAYER_SLOTS_X + hotbarSlot * 18, HOTBAR_SLOTS_Y));
        }
    }

    // Move the stack quickly
    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (idx < MAP_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!NavigationTableMapResolver.isNavigationMap(stack)) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, MAP_SLOT_START, MAP_SLOT_START + MAP_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        slot.onTake(player, stack);
        return original;
    }

    // Check if the still is valid
    @Override
    public boolean stillValid(Player player) {
        return mapContainer.stillValid(player);
    }
}
