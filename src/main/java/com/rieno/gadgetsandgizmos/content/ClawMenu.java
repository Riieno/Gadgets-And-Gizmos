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

// Sync Claw settings
public class ClawMenu extends GhostItemMenu<ClawBlockEntity> implements ISimulatedMenuOpen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 47;
    public static final int PLAYER_SLOTS_Y = 164;
    public static final int GHOST_SLOT_FIRST_X = 100;
    public static final int GHOST_SLOT_SECOND_X = 118;
    public static final int GHOST_SLOTS_Y = 118;

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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw menu
    public ClawMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.CLAW.get(), id, inv, extraData);
    }

    // Initialize the claw menu
    public ClawMenu(int id, Inventory inv, ClawBlockEntity blockEntity) {
        this(CTMenuTypes.CLAW.get(), id, inv, blockEntity);
    }

    // Initialize the claw menu
    public ClawMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the claw menu
    public ClawMenu(MenuType<?> type, int id, Inventory inv, ClawBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(ClawBlockEntity contentHolder) {
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
    protected ClawBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);

        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        var level = playerInventory.player.level();
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, contentSubLevelId, contentPos);
        return blockEntity instanceof ClawBlockEntity claw ? claw : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
    }

    // Get the content pos
    public BlockPos getContentPos() {
        if (contentHolder != null) {
            return contentHolder.getBlockPos();
        }
        return contentPos;
    }

    // Create the ghost inventory
    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(2);
    }

    // Add the slots
    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, GHOST_SLOT_FIRST_X, GHOST_SLOTS_Y));
        addSlot(new SlotItemHandler(ghostInventory, 1, GHOST_SLOT_SECOND_X, GHOST_SLOTS_Y));
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
    protected void saveData(ClawBlockEntity contentHolder) {

        if (contentHolder == null) {
            return;
        }
        contentHolder.setReceiverFrequency(
                copySingle(ghostInventory.getStackInSlot(0)),
                copySingle(ghostInventory.getStackInSlot(1)));
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
        ghostInventory.setStackInSlot(0, copySingle(contentHolder.getReceiverFrequencyFirst()));
        ghostInventory.setStackInSlot(1, copySingle(contentHolder.getReceiverFrequencySecond()));
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
}
