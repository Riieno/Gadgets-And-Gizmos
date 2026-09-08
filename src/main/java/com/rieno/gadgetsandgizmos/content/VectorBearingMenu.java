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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

// Sync Vector Bearing settings
public class VectorBearingMenu extends GhostItemMenu<VectorBearingBlockEntity>
        implements ISimulatedMenuOpen, MenuBackedBlockEntityTarget<VectorBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 48;
    public static final int PLAYER_SLOTS_Y = 170;
    private static final int NORTH_FIRST_X = 90;
    private static final int NORTH_SECOND_X = 120;
    private static final int NORTH_Y = 54;
    private static final int SOUTH_FIRST_X = 90;
    private static final int SOUTH_SECOND_X = 120;
    private static final int SOUTH_Y = 134;
    private static final int EAST_X = 145;
    private static final int EAST_FIRST_Y = 79;
    private static final int EAST_SECOND_Y = 109;
    private static final int WEST_X = 61;
    private static final int WEST_FIRST_Y = 79;
    private static final int WEST_SECOND_Y = 109;

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
    // Initial control mode
    private VectorBearingBlockEntity.ControlMode initialControlMode;
    // Initial max tilt in degrees
    private double initialMaxTiltDegrees;
    // Initial stabilization plane
    private VectorBearingBlockEntity.StabilizeAxis initialStabilizeAxis;
    // Initial stabilization state
    private boolean initialKeepStable;
    // Tracks whether initial config is received
    private boolean receivedInitialConfig;
    // Initial ghost stacks
    private ItemStack[] initialGhostStacks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing menu
    public VectorBearingMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.VECTOR_BEARING.get(), id, inv, extraData);
    }

    // Initialize the vector bearing menu
    public VectorBearingMenu(int id, Inventory inv, VectorBearingBlockEntity blockEntity) {
        this(CTMenuTypes.VECTOR_BEARING.get(), id, inv, blockEntity);
    }

    // Initialize the vector bearing menu
    public VectorBearingMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the vector bearing menu
    public VectorBearingMenu(MenuType<?> type, int id, Inventory inv, VectorBearingBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(VectorBearingBlockEntity contentHolder) {
        super.initAndReadInventory(contentHolder);
        loadGhostInventoryFromBindings();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected VectorBearingBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);
        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof VectorBearingBlockEntity bearing ? bearing : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        initialControlMode = buf.readEnum(VectorBearingBlockEntity.ControlMode.class);
        initialMaxTiltDegrees = buf.readDouble();
        initialStabilizeAxis = buf.readEnum(VectorBearingBlockEntity.StabilizeAxis.class);
        initialKeepStable = buf.readBoolean();
        receivedInitialConfig = true;
        initialGhostStacks = new ItemStack[8];
        for (int i = 0; i < initialGhostStacks.length; i++) {
            initialGhostStacks[i] = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
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
        addDirectionSlots(0, NORTH_FIRST_X, NORTH_Y, NORTH_SECOND_X, NORTH_Y);
        addDirectionSlots(2, SOUTH_FIRST_X, SOUTH_Y, SOUTH_SECOND_X, SOUTH_Y);
        addDirectionSlots(4, EAST_X, EAST_FIRST_Y, EAST_X, EAST_SECOND_Y);
        addDirectionSlots(6, WEST_X, WEST_FIRST_Y, WEST_X, WEST_SECOND_Y);
    }

    // Add the direction slots
    private void addDirectionSlots(int startSlot, int firstX, int firstY, int secondX, int secondY) {
        addSlot(new SlotItemHandler(ghostInventory, startSlot, firstX, firstY));
        addSlot(new SlotItemHandler(ghostInventory, startSlot + 1, secondX, secondY));
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
    protected void saveData(VectorBearingBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        saveDirection(contentHolder, Direction.NORTH, 0);
        saveDirection(contentHolder, Direction.SOUTH, 2);
        saveDirection(contentHolder, Direction.EAST, 4);
        saveDirection(contentHolder, Direction.WEST, 6);
    }

    // Save the direction
    private void saveDirection(VectorBearingBlockEntity contentHolder, Direction dir, int startSlot) {
        contentHolder.setFrequency(dir,
                copySingle(ghostInventory.getStackInSlot(startSlot)),
                copySingle(ghostInventory.getStackInSlot(startSlot + 1)));
    }

    // Handle the menu click
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        super.clicked(slotId, dragType, clickType, player);
        if (!player.level().isClientSide && slotId >= 36 && slotId < 36 + ghostInventory.getSlots()) {
            saveData(contentHolder);
        }
    }

    // Move the stack quickly
    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        ItemStack res = super.quickMoveStack(player, idx);
        if (!player.level().isClientSide) {
            saveData(contentHolder);
        }
        return res;
    }

    // Check if repeated input is allowed
    @Override
    protected boolean allowRepeats() {
        return false;
    }

    // Get the content pos
    public BlockPos getContentPos() {
        return contentPos;
    }

    // Get the content sublevel id
    public UUID getContentSubLevelId() {
        return contentSubLevelId;
    }

    // Get the menu config target pos
    @Override
    public BlockPos getMenuConfigTargetPos() {
        return contentPos;
    }

    // Get the menu config target sublevel id
    @Override
    public UUID getMenuConfigTargetSubLevelId() {
        return contentSubLevelId;
    }

    // Get the menu config target block entity
    @Override
    public VectorBearingBlockEntity getMenuConfigTargetBlockEntity() {
        return contentHolder;
    }

    // Load the ghost inventory from bindings
    private void loadGhostInventoryFromBindings() {
        if (initialGhostStacks != null) {
            int slots = Math.min(initialGhostStacks.length, ghostInventory.getSlots());
            for (int i = 0; i < slots; i++) {
                ghostInventory.setStackInSlot(i, copySingle(initialGhostStacks[i]));
            }
            return;
        }
        if (contentHolder == null) {
            return;
        }
        loadDirection(Direction.NORTH, 0);
        loadDirection(Direction.SOUTH, 2);
        loadDirection(Direction.EAST, 4);
        loadDirection(Direction.WEST, 6);
    }

    // Load the direction
    private void loadDirection(Direction dir, int startSlot) {
        ghostInventory.setStackInSlot(startSlot, copySingle(contentHolder.getFrequencyFirst(dir)));
        ghostInventory.setStackInSlot(startSlot + 1, copySingle(contentHolder.getFrequencySecond(dir)));
    }

    // Get the initial control mode
    public VectorBearingBlockEntity.ControlMode getInitialControlMode() {
        return initialControlMode == null ? VectorBearingBlockEntity.ControlMode.AUTO : initialControlMode;
    }

    // Get the initial max tilt degrees
    public double getInitialMaxTiltDegrees() {
        return receivedInitialConfig ? initialMaxTiltDegrees : 30.0D;
    }

    // Get the initial stabilization plane
    public VectorBearingBlockEntity.StabilizeAxis getInitialStabilizeAxis() {
        return initialStabilizeAxis == null ? VectorBearingBlockEntity.StabilizeAxis.XZ : initialStabilizeAxis;
    }

    // Check whether stabilization was initially enabled
    public boolean isInitialKeepStable() {
        return receivedInitialConfig && initialKeepStable;
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
