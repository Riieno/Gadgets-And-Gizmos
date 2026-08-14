package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.ISimulatedMenuOpen;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.BlockPos;
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

// Sync Powered Zipline settings
public class PoweredZiplineMenu extends GhostItemMenu<PoweredZiplineBlockEntity> implements ISimulatedMenuOpen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 31;
    public static final int PLAYER_SLOTS_Y = 112;
    public static final int FORWARD_FIRST_X = 82;
    public static final int FORWARD_SECOND_X = 102;
    public static final int BACKWARD_FIRST_X = 82;
    public static final int BACKWARD_SECOND_X = 102;
    public static final int FORWARD_Y = 38;
    public static final int BACKWARD_Y = 70;

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
    // Tracks whether follow chain state is set
    private boolean followChainState;
    // Maximum speed state
    private float maxSpeedState;
    // Current damping state
    private float dampingState;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline menu
    public PoweredZiplineMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.POWERED_ZIPLINE.get(), id, inv, extraData);
    }

    // Initialize the powered zipline menu
    public PoweredZiplineMenu(int id, Inventory inv, PoweredZiplineBlockEntity blockEntity) {
        this(CTMenuTypes.POWERED_ZIPLINE.get(), id, inv, blockEntity);
    }

    // Initialize the powered zipline menu
    public PoweredZiplineMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the powered zipline menu
    public PoweredZiplineMenu(MenuType<?> type, int id, Inventory inv, PoweredZiplineBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
            followChainState = contentHolder.isFollowChain();
            maxSpeedState = contentHolder.getConfiguredMaxSpeed();
            dampingState = contentHolder.getConfiguredDamping();
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(PoweredZiplineBlockEntity contentHolder) {
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
    protected PoweredZiplineBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);
        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof PoweredZiplineBlockEntity zipline ? zipline : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        maxSpeedState = PoweredZiplineBlockEntity.DEFAULT_MAX_SPEED;
        dampingState = PoweredZiplineBlockEntity.DEFAULT_DAMPING;
        if (buf.readableBytes() > 0) {
            followChainState = buf.readBoolean();
        }
        if (buf.readableBytes() >= Float.BYTES * 2) {
            maxSpeedState = buf.readFloat();
            dampingState = buf.readFloat();
        }
    }

    // Create the ghost inventory
    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(4);
    }

    // Add the slots
    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, FORWARD_FIRST_X, FORWARD_Y));
        addSlot(new SlotItemHandler(ghostInventory, 1, FORWARD_SECOND_X, FORWARD_Y));
        addSlot(new SlotItemHandler(ghostInventory, 2, BACKWARD_FIRST_X, BACKWARD_Y));
        addSlot(new SlotItemHandler(ghostInventory, 3, BACKWARD_SECOND_X, BACKWARD_Y));
    }

    // Add the player slots
    @Override
    protected void addPlayerSlots(int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot((Container) playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
            addSlot(new Slot((Container) playerInventory, hotbarSlot, x + hotbarSlot * 18, y + 58));
        }
    }

    // Save the data
    @Override
    protected void saveData(PoweredZiplineBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        contentHolder.setForwardFrequency(copySingle(ghostInventory.getStackInSlot(0)),
                copySingle(ghostInventory.getStackInSlot(1)));
        contentHolder.setBackwardFrequency(copySingle(ghostInventory.getStackInSlot(2)),
                copySingle(ghostInventory.getStackInSlot(3)));
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
        ghostInventory.setStackInSlot(0, copySingle(contentHolder.getForwardFrequencyFirst()));
        ghostInventory.setStackInSlot(1, copySingle(contentHolder.getForwardFrequencySecond()));
        ghostInventory.setStackInSlot(2, copySingle(contentHolder.getBackwardFrequencyFirst()));
        ghostInventory.setStackInSlot(3, copySingle(contentHolder.getBackwardFrequencySecond()));
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

    // Get the content pos
    public BlockPos getContentPos() {
        return contentPos;
    }

    // Get the content sublevel id
    public UUID getContentSubLevelId() {
        return contentSubLevelId;
    }

    // Check if this is follow chain state
    public boolean isFollowChainState() {
        return contentHolder != null ? contentHolder.isFollowChain() : followChainState;
    }

    // Set the follow chain state
    public void setFollowChainState(boolean followChainState) {
        this.followChainState = followChainState;
        if (contentHolder != null) {
            contentHolder.setFollowChain(followChainState);
        }
    }

    // Get the max speed state
    public float getMaxSpeedState() {
        return maxSpeedState;
    }

    // Get the damping state
    public float getDampingState() {
        return dampingState;
    }

    // Set the motion configuration state
    public void setMotionConfigurationState(float maxSpeed, float damping) {
        maxSpeedState = maxSpeed;
        dampingState = damping;
        if (contentHolder != null) {
            contentHolder.setMotionConfiguration(maxSpeed, damping);
        }
    }
}
