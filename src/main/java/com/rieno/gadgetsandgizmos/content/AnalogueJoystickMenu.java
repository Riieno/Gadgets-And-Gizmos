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

// Sync Analogue Joystick settings
public class AnalogueJoystickMenu extends GhostItemMenu<AnalogueJoystickBlockEntity>
    implements MenuBackedBlockEntityTarget<AnalogueJoystickBlockEntity>, ISimulatedMenuOpen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 82;
    public static final int PLAYER_SLOTS_Y = 264;
    public static final int GHOST_SLOT_START_INDEX = 36;
    private static final int CHANNEL_ROW_Y = 34;
    private static final int CHANNEL_ROW_SPACING = 24;
    private static final int GHOST_SLOT_FIRST_X = 170;
    private static final int GHOST_SLOT_SECOND_X = 190;
    private static final int GHOST_SLOTS_Y = 36;
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

    // Initial sensitivity
    private float initialSensitivity;
    // Initial deadzone
    private float initialDeadzone;
    // Initial max tilt in degrees
    private float initialMaxTiltDegrees;
    // Initial release mode
    private AnalogueJoystickBlockEntity.ReleaseMode initialReleaseMode;
    // Initial player input mode
    private AnalogueJoystickBlockEntity.InputMode initialInputMode;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick menu
    public AnalogueJoystickMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.ANALOGUE_JOYSTICK.get(), id, inv, extraData);
    }

    // Initialize the analogue joystick menu
    public AnalogueJoystickMenu(int id, Inventory inv, AnalogueJoystickBlockEntity blockEntity) {
        this(CTMenuTypes.ANALOGUE_JOYSTICK.get(), id, inv, blockEntity);
    }

    // Initialize the analogue joystick menu
    public AnalogueJoystickMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the analogue joystick menu
    public AnalogueJoystickMenu(MenuType<?> type, int id, Inventory inv, AnalogueJoystickBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
            captureInitialSettings(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(AnalogueJoystickBlockEntity contentHolder) {
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
    protected AnalogueJoystickBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {

        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);

        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        var level = playerInventory.player.level();
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, contentSubLevelId, contentPos);
        return blockEntity instanceof AnalogueJoystickBlockEntity joystick ? joystick : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        initialSensitivity = buf.readFloat();
        initialDeadzone = buf.readFloat();
        initialMaxTiltDegrees = buf.readFloat();
        initialReleaseMode = AnalogueJoystickBlockEntity.ReleaseMode.read(buf.readUtf());
        initialInputMode = AnalogueJoystickBlockEntity.InputMode.read(buf.readUtf());
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

    // Get the initial deadzone
    public float getInitialDeadzone() {
        return initialDeadzone;
    }

    // Get the initial sensitivity
    public float getInitialSensitivity() {
        return initialSensitivity;
    }

    // Get the initial max tilt degrees
    public float getInitialMaxTiltDegrees() {
        return initialMaxTiltDegrees;
    }

    // Get the initial release mode
    public AnalogueJoystickBlockEntity.ReleaseMode getInitialReleaseMode() {
        return initialReleaseMode;
    }

    // Get the initial player input mode
    public AnalogueJoystickBlockEntity.InputMode getInitialInputMode() {
        return initialInputMode;
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
    public AnalogueJoystickBlockEntity getMenuConfigTargetBlockEntity() {
        return contentHolder;
    }

    // Create the ghost inventory
    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(AnalogueJoystickBlockEntity.JoystickChannel.values().length * 2);
    }

    // Add the slots
    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
        for (AnalogueJoystickBlockEntity.JoystickChannel channel : AnalogueJoystickBlockEntity.JoystickChannel.values()) {
            int rowOffset = channel.ordinal() * CHANNEL_ROW_SPACING;
            addSlot(new SlotItemHandler(ghostInventory, channel.ordinal() * 2,
                    GHOST_SLOT_FIRST_X, GHOST_SLOTS_Y + rowOffset));
            addSlot(new SlotItemHandler(ghostInventory, channel.ordinal() * 2 + 1,
                    GHOST_SLOT_SECOND_X, GHOST_SLOTS_Y + rowOffset));
        }
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
    protected void saveData(AnalogueJoystickBlockEntity contentHolder) {

    }

    // Check if repeated input is allowed
    @Override
    protected boolean allowRepeats() {
        return false;
    }

    // Load the ghost inventory from bindings
    private void loadGhostInventoryFromBindings() {
        if (contentHolder == null) {
            return;
        }
        for (AnalogueJoystickBlockEntity.JoystickChannel channel : AnalogueJoystickBlockEntity.JoystickChannel.values()) {
            int slotIndex = channel.ordinal() * 2;

            ghostInventory.setStackInSlot(slotIndex, copySingle(contentHolder.getFrequencyFirst(channel)));
            ghostInventory.setStackInSlot(slotIndex + 1, copySingle(contentHolder.getFrequencySecond(channel)));
        }
    }

    // Capture the initial settings
    private void captureInitialSettings(AnalogueJoystickBlockEntity joystick) {
        initialSensitivity = joystick.getDragSensitivity();
        initialDeadzone = joystick.getDeadzone();
        initialMaxTiltDegrees = joystick.getMaxTiltDegrees();
        initialReleaseMode = joystick.getReleaseMode();
        initialInputMode = joystick.getInputMode();
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
