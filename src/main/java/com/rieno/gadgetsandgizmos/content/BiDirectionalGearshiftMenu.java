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

// Sync Bidirectional Gearshift settings
public class BiDirectionalGearshiftMenu extends GhostItemMenu<BiDirectionalGearshiftBlockEntity>
        implements ISimulatedMenuOpen, MenuBackedBlockEntityTarget<BiDirectionalGearshiftBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 48;
    public static final int PLAYER_SLOTS_Y = 164;
    public static final int SECONDARY_CW_X = 65;
    public static final int SECONDARY_CCW_X = 89;
    public static final int PRIMARY_CW_X = 150;
    public static final int PRIMARY_CCW_X = 174;
    public static final int FIRST_FREQUENCY_Y = 84;
    public static final int SECOND_FREQUENCY_Y = 103;

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
    // Cached primary axis
    private Direction.Axis cachedPrimaryAxis;
    // Cached secondary axis
    private Direction.Axis cachedSecondaryAxis;
    // Cached primary mode
    private BiDirectionalGearshiftBlockEntity.AxisControlMode cachedPrimaryMode;
    // Cached secondary mode
    private BiDirectionalGearshiftBlockEntity.AxisControlMode cachedSecondaryMode;
    // Cached local mode
    private BiDirectionalGearshiftBlockEntity.LocalControlMode cachedLocalMode;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearshift menu
    public BiDirectionalGearshiftMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.BI_DIRECTIONAL_GEARSHIFT.get(), id, inv, extraData);
    }

    // Initialize the bi directional gearshift menu
    public BiDirectionalGearshiftMenu(int id, Inventory inv, BiDirectionalGearshiftBlockEntity blockEntity) {
        this(CTMenuTypes.BI_DIRECTIONAL_GEARSHIFT.get(), id, inv, blockEntity);
    }

    // Initialize the bi directional gearshift menu
    public BiDirectionalGearshiftMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            cacheOpenData(contentHolder);
        }
    }

    // Initialize the bi directional gearshift menu
    public BiDirectionalGearshiftMenu(MenuType<?> type, int id, Inventory inv,
                                      BiDirectionalGearshiftBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
            cacheOpenData(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(BiDirectionalGearshiftBlockEntity contentHolder) {
        super.initAndReadInventory(contentHolder);
        cacheOpenData(contentHolder);
        loadGhostInventoryFromBindings();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected BiDirectionalGearshiftBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);

        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof BiDirectionalGearshiftBlockEntity gearshift ? gearshift : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        cachedPrimaryAxis = buf.readEnum(Direction.Axis.class);
        cachedSecondaryAxis = buf.readEnum(Direction.Axis.class);
        cachedPrimaryMode = buf.readEnum(BiDirectionalGearshiftBlockEntity.AxisControlMode.class);
        cachedSecondaryMode = buf.readEnum(BiDirectionalGearshiftBlockEntity.AxisControlMode.class);
        cachedLocalMode = buf.readEnum(BiDirectionalGearshiftBlockEntity.LocalControlMode.class);
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
        addFrequencySlots(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE, 0);
        addFrequencySlots(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE, 2);
        addFrequencySlots(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE, 4);
        addFrequencySlots(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE, 6);
    }

    // Add the frequency slots
    private void addFrequencySlots(BiDirectionalGearshiftBlockEntity.AxisRole role,
                                   BiDirectionalGearshiftBlockEntity.RotationChannel channel,
                                   int startSlot) {
        int x = frequencySlotX(role, channel);
        addSlot(new SlotItemHandler(ghostInventory, startSlot, x, FIRST_FREQUENCY_Y));
        addSlot(new SlotItemHandler(ghostInventory, startSlot + 1, x, SECOND_FREQUENCY_Y));
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
    protected void saveData(BiDirectionalGearshiftBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        contentHolder.setFrequency(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE,
                copySingle(ghostInventory.getStackInSlot(0)), copySingle(ghostInventory.getStackInSlot(1)));
        contentHolder.setFrequency(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE,
                copySingle(ghostInventory.getStackInSlot(2)), copySingle(ghostInventory.getStackInSlot(3)));
        contentHolder.setFrequency(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE,
                copySingle(ghostInventory.getStackInSlot(4)), copySingle(ghostInventory.getStackInSlot(5)));
        contentHolder.setFrequency(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE,
                copySingle(ghostInventory.getStackInSlot(6)), copySingle(ghostInventory.getStackInSlot(7)));
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

    // Get the lane axis
    public Direction.Axis getLaneAxis(BiDirectionalGearshiftBlockEntity.AxisRole role) {
        Direction.Axis axis = role == BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY
                ? cachedSecondaryAxis
                : cachedPrimaryAxis;
        if (axis != null) {
            return axis;
        }
        return role == BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY
                ? Direction.Axis.X
                : Direction.Axis.Z;
    }

    // Get the axis mode
    public BiDirectionalGearshiftBlockEntity.AxisControlMode getAxisMode(
            BiDirectionalGearshiftBlockEntity.AxisRole role) {
        BiDirectionalGearshiftBlockEntity.AxisControlMode mode = role == BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY
                ? cachedSecondaryMode
                : cachedPrimaryMode;
        return mode == null ? BiDirectionalGearshiftBlockEntity.AxisControlMode.PASSTHROUGH : mode;
    }

    // Get the local mode
    public BiDirectionalGearshiftBlockEntity.LocalControlMode getLocalMode() {
        return cachedLocalMode == null ? BiDirectionalGearshiftBlockEntity.LocalControlMode.BOTH : cachedLocalMode;
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
    public BiDirectionalGearshiftBlockEntity getMenuConfigTargetBlockEntity() {
        return contentHolder;
    }

    // Get the frequency slot x
    public static int frequencySlotX(BiDirectionalGearshiftBlockEntity.AxisRole role,
                                     BiDirectionalGearshiftBlockEntity.RotationChannel channel) {
        boolean primary = role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY;
        boolean clockwise = channel == BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE;
        if (primary) {
            return clockwise ? PRIMARY_CW_X : PRIMARY_CCW_X;
        }
        return clockwise ? SECONDARY_CW_X : SECONDARY_CCW_X;
    }

    // Load the ghost inventory from bindings
    private void loadGhostInventoryFromBindings() {
        if (contentHolder == null) {
            return;
        }
        ghostInventory.setStackInSlot(0, copySingle(contentHolder.getFrequencyFirst(
                BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE)));
        ghostInventory.setStackInSlot(1, copySingle(contentHolder.getFrequencySecond(
                BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE)));
        ghostInventory.setStackInSlot(2, copySingle(contentHolder.getFrequencyFirst(
                BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE)));
        ghostInventory.setStackInSlot(3, copySingle(contentHolder.getFrequencySecond(
                BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE)));
        ghostInventory.setStackInSlot(4, copySingle(contentHolder.getFrequencyFirst(
                BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE)));
        ghostInventory.setStackInSlot(5, copySingle(contentHolder.getFrequencySecond(
                BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.CLOCKWISE)));
        ghostInventory.setStackInSlot(6, copySingle(contentHolder.getFrequencyFirst(
                BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE)));
        ghostInventory.setStackInSlot(7, copySingle(contentHolder.getFrequencySecond(
                BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY,
                BiDirectionalGearshiftBlockEntity.RotationChannel.COUNTER_CLOCKWISE)));
    }

    // Cache the open data
    private void cacheOpenData(BiDirectionalGearshiftBlockEntity gearshift) {
        if (gearshift == null) {
            return;
        }
        cachedPrimaryAxis = gearshift.getLaneAxis(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY);
        cachedSecondaryAxis = gearshift.getLaneAxis(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY);
        cachedPrimaryMode = gearshift.getAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY);
        cachedSecondaryMode = gearshift.getAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY);
        cachedLocalMode = gearshift.getLocalMode();
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
