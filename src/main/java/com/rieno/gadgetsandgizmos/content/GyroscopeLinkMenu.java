package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.ISimulatedMenuOpen;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityTarget;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

// Sync Gyroscope Link settings
public class GyroscopeLinkMenu extends GhostItemMenu<GyroscopeLinkBlockEntity>
        implements MenuBackedBlockEntityTarget<GyroscopeLinkBlockEntity>, ISimulatedMenuOpen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 32;
    public static final int PLAYER_SLOTS_Y = 258;
    public static final int GHOST_SLOT_FIRST_X = 80;
    public static final int GHOST_SLOT_SECOND_X = 98;
    public static final int GHOST_SLOTS_START_Y = 70;
    public static final int GHOST_SLOT_ROW_SPACING = 30;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current content position
    private BlockPos contentPos;
    // Current content sub-level id
    private UUID contentSubLevelId;
    // Initial tracking mode
    private GyroscopeLinkBlockEntity.TrackingMode initialTrackingMode = GyroscopeLinkBlockEntity.TrackingMode.LIVE;
    // Tracks whether frequency slots are active
    private boolean frequencySlotsActive;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link menu
    public GyroscopeLinkMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.GYROSCOPE_LINK.get(), id, inv, extraData);
    }

    // Initialize the gyroscope link menu
    public GyroscopeLinkMenu(int id, Inventory inv, GyroscopeLinkBlockEntity blockEntity) {
        this(CTMenuTypes.GYROSCOPE_LINK.get(), id, inv, blockEntity);
    }

    // Initialize the gyroscope link menu
    public GyroscopeLinkMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the gyroscope link menu
    public GyroscopeLinkMenu(MenuType<?> type, int id, Inventory inv, GyroscopeLinkBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
            initialTrackingMode = contentHolder.getTrackingMode();
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(GyroscopeLinkBlockEntity contentHolder) {
        super.initAndReadInventory(contentHolder);
        loadGhostInventoryFromBinding();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected GyroscopeLinkBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);

        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        var level = playerInventory.player.level();
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, contentSubLevelId, contentPos);
        return blockEntity instanceof GyroscopeLinkBlockEntity gyro ? gyro : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        try {
            initialTrackingMode = GyroscopeLinkBlockEntity.TrackingMode.valueOf(buf.readUtf());
        } catch (IllegalArgumentException ignored) {
            initialTrackingMode = GyroscopeLinkBlockEntity.TrackingMode.LIVE;
        }
    }

    // Get the content pos
    public BlockPos getContentPos() {
        if (contentHolder != null) {
            return contentHolder.getBlockPos();
        }
        return contentPos;
    }

    // Get the content sublevel id
    public UUID getContentSubLevelId() {
        return contentSubLevelId;
    }

    // Get the initial tracking mode
    public GyroscopeLinkBlockEntity.TrackingMode getInitialTrackingMode() {
        return initialTrackingMode == null ? GyroscopeLinkBlockEntity.TrackingMode.LIVE : initialTrackingMode;
    }

    // Set the player slots active
    public void setPlayerSlotsActive(boolean playerSlotsActive) {
        setFrequencySlotsActive(playerSlotsActive);
    }

    // Set the frequency slots active
    public void setFrequencySlotsActive(boolean frequencySlotsActive) {
        this.frequencySlotsActive = frequencySlotsActive;
    }

    // Get the menu config target pos
    @Override
    public BlockPos getMenuConfigTargetPos() {
        return getContentPos();
    }

    // Get the menu config target sublevel id
    @Override
    public UUID getMenuConfigTargetSubLevelId() {
        return getContentSubLevelId();
    }

    // Get the menu config target block entity
    @Override
    public GyroscopeLinkBlockEntity getMenuConfigTargetBlockEntity() {
        if (contentHolder == null && contentPos != null && playerInventory != null && playerInventory.player != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
            if (blockEntity instanceof GyroscopeLinkBlockEntity gyro) {
                contentHolder = gyro;
            }
        }
        return contentHolder;
    }

    // Create the ghost inventory
    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(8);
    }

    // Add the slots
    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);

        addFrequencySlotPair(0, 0);
        addFrequencySlotPair(2, 1);
        addFrequencySlotPair(4, 2);
        addFrequencySlotPair(6, 3);
    }

    // Add the frequency slot pair
    private void addFrequencySlotPair(int slotIndex, int row) {
        int slotY = GHOST_SLOTS_START_Y + row * GHOST_SLOT_ROW_SPACING;
        addSlot(new ActiveGhostSlot(ghostInventory, slotIndex, GHOST_SLOT_FIRST_X, slotY));
        addSlot(new ActiveGhostSlot(ghostInventory, slotIndex + 1, GHOST_SLOT_SECOND_X, slotY));
    }

    // Add the player slots
    @Override
    protected void addPlayerSlots(int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new ActivePlayerSlot((Container) playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
            addSlot(new ActivePlayerSlot((Container) playerInventory, hotbarSlot, x + hotbarSlot * 18, y + 58));
        }
    }

    // Save the data
    @Override
    protected void saveData(GyroscopeLinkBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        contentHolder.setCardinalFrequency(Direction.NORTH,
                copySingle(ghostInventory.getStackInSlot(0)),
                copySingle(ghostInventory.getStackInSlot(1)));
        contentHolder.setCardinalFrequency(Direction.SOUTH,
                copySingle(ghostInventory.getStackInSlot(2)),
                copySingle(ghostInventory.getStackInSlot(3)));
        contentHolder.setCardinalFrequency(Direction.EAST,
                copySingle(ghostInventory.getStackInSlot(4)),
                copySingle(ghostInventory.getStackInSlot(5)));
        contentHolder.setCardinalFrequency(Direction.WEST,
                copySingle(ghostInventory.getStackInSlot(6)),
                copySingle(ghostInventory.getStackInSlot(7)));
    }

    // Check if repeated input is allowed
    @Override
    protected boolean allowRepeats() {
        return false;
    }

    // Load the ghost inventory from binding
    private void loadGhostInventoryFromBinding() {
        if (contentHolder == null) {
            return;
        }
        ghostInventory.setStackInSlot(0, copySingle(contentHolder.getCardinalFrequencyFirst(Direction.NORTH)));
        ghostInventory.setStackInSlot(1, copySingle(contentHolder.getCardinalFrequencySecond(Direction.NORTH)));
        ghostInventory.setStackInSlot(2, copySingle(contentHolder.getCardinalFrequencyFirst(Direction.SOUTH)));
        ghostInventory.setStackInSlot(3, copySingle(contentHolder.getCardinalFrequencySecond(Direction.SOUTH)));
        ghostInventory.setStackInSlot(4, copySingle(contentHolder.getCardinalFrequencyFirst(Direction.EAST)));
        ghostInventory.setStackInSlot(5, copySingle(contentHolder.getCardinalFrequencySecond(Direction.EAST)));
        ghostInventory.setStackInSlot(6, copySingle(contentHolder.getCardinalFrequencyFirst(Direction.WEST)));
        ghostInventory.setStackInSlot(7, copySingle(contentHolder.getCardinalFrequencySecond(Direction.WEST)));
    }

    // Copy one item
    private static ItemStack copySingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Handle the active ghost slot
    private class ActiveGhostSlot extends SlotItemHandler {
        // Initialize the active ghost slot
        public ActiveGhostSlot(ItemStackHandler itemHandler, int idx, int xPosition, int yPosition) {
            super(itemHandler, idx, xPosition, yPosition);
        }

        // Check if this is active
        @Override
        public boolean isActive() {
            return frequencySlotsActive;
        }
    }

    // Handle the active player slot
    private class ActivePlayerSlot extends Slot {
        // Initialize the active player slot
        public ActivePlayerSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        // Check if this is active
        @Override
        public boolean isActive() {
            return frequencySlotsActive;
        }
    }
}
