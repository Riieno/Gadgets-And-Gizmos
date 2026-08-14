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

// Sync RCS Thruster settings
public class RcsThrusterMenu extends GhostItemMenu<RcsThrusterBlockEntity>
        implements MenuBackedBlockEntityTarget<RcsThrusterBlockEntity>, ISimulatedMenuOpen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 47;
    public static final int PLAYER_SLOTS_Y = 172;
    public static final int GHOST_SLOT_FIRST_X = 104;
    public static final int GHOST_SLOT_SECOND_X = 122;
    public static final int GHOST_SLOTS_START_Y = 48;
    public static final int GHOST_SLOT_ROW_SPACING = 26;

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

    // Initialize the RCS thruster menu
    public RcsThrusterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.RCS_THRUSTER.get(), id, inventory, extraData);
    }

    // Initialize the RCS thruster menu
    public RcsThrusterMenu(int id, Inventory inventory, RcsThrusterBlockEntity blockEntity) {
        this(CTMenuTypes.RCS_THRUSTER.get(), id, inventory, blockEntity);
    }

    // Initialize the RCS thruster menu
    public RcsThrusterMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            RegistryFriendlyByteBuf extraData
    ) {
        super(type, id, inventory, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the RCS thruster menu
    public RcsThrusterMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            RcsThrusterBlockEntity blockEntity
    ) {
        super(type, id, inventory, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(RcsThrusterBlockEntity contentHolder) {
        super.initAndReadInventory(contentHolder);
        loadGhostInventory();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected RcsThrusterBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);
        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof RcsThrusterBlockEntity rcsThruster ? rcsThruster : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buffer) {
    }

    // Get the content pos
    public BlockPos getContentPos() {
        return contentHolder == null ? contentPos : contentHolder.getBlockPos();
    }

    // Get the menu config target pos
    @Override
    public BlockPos getMenuConfigTargetPos() {
        return getContentPos();
    }

    // Get the menu config target sublevel id
    @Override
    public UUID getMenuConfigTargetSubLevelId() {
        return contentSubLevelId;
    }

    // Get the menu config target block entity
    @Override
    public RcsThrusterBlockEntity getMenuConfigTargetBlockEntity() {
        if (contentHolder == null && contentPos != null
                && playerInventory != null && playerInventory.player != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                    playerInventory.player.level(), contentSubLevelId, contentPos);
            if (blockEntity instanceof RcsThrusterBlockEntity rcsThruster) {
                contentHolder = rcsThruster;
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
        for (int row = 0; row < 4; row++) {
            int y = GHOST_SLOTS_START_Y + row * GHOST_SLOT_ROW_SPACING;
            addSlot(new SlotItemHandler(ghostInventory, row * 2, GHOST_SLOT_FIRST_X, y));
            addSlot(new SlotItemHandler(ghostInventory, row * 2 + 1, GHOST_SLOT_SECOND_X, y));
        }
    }

    // Add the player slots
    @Override
    protected void addPlayerSlots(int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot((Container) playerInventory, column + row * 9 + 9,
                        x + column * 18, y + row * 18));
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new Slot((Container) playerInventory, slot, x + slot * 18, y + 58));
        }
    }

    // Save the data
    @Override
    protected void saveData(RcsThrusterBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        Direction[] nozzles = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (int row = 0; row < nozzles.length; row++) {
            contentHolder.setFrequency(nozzles[row],
                    copySingle(ghostInventory.getStackInSlot(row * 2)),
                    copySingle(ghostInventory.getStackInSlot(row * 2 + 1)));
        }
    }

    // Check if repeated input is allowed
    @Override
    protected boolean allowRepeats() {
        return false;
    }

    // Load the ghost inventory
    private void loadGhostInventory() {
        if (contentHolder == null) {
            return;
        }
        Direction[] nozzles = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (int row = 0; row < nozzles.length; row++) {
            ghostInventory.setStackInSlot(row * 2, contentHolder.getFrequencyFirst(nozzles[row]));
            ghostInventory.setStackInSlot(row * 2 + 1, contentHolder.getFrequencySecond(nozzles[row]));
        }
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
