package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
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

import java.util.EnumMap;
import java.util.UUID;

// Sync Aileron Bearing settings
public class AileronBearingMenu extends GhostItemMenu<AileronBearingBlockEntity>
        implements ISimulatedMenuOpen, MenuBackedBlockEntityTarget<AileronBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 48;
    public static final int PLAYER_SLOTS_Y = 164;
    public static final int ORANGE_CW_X = 65;
    public static final int ORANGE_CCW_X = 89;
    public static final int CYAN_CW_X = 150;
    public static final int CYAN_CCW_X = 174;
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
    // Initial min angles
    private EnumMap<BearingHead, Double> initialMinAngles;
    // Initial max angles
    private EnumMap<BearingHead, Double> initialMaxAngles;
    // Tracks whether initial config is received
    private boolean receivedInitialConfig;
    // Initial ghost stacks
    private ItemStack[] initialGhostStacks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing menu
    public AileronBearingMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.AILERON_BEARING.get(), id, inv, extraData);
    }

    // Initialize the aileron bearing menu
    public AileronBearingMenu(int id, Inventory inv, AileronBearingBlockEntity blockEntity) {
        this(CTMenuTypes.AILERON_BEARING.get(), id, inv, blockEntity);
    }

    // Initialize the aileron bearing menu
    public AileronBearingMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
        }
    }

    // Initialize the aileron bearing menu
    public AileronBearingMenu(MenuType<?> type, int id, Inventory inv, AileronBearingBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            contentSubLevelId = SimulatedHelper.getContainingSubLevelId(contentHolder);
        }
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(AileronBearingBlockEntity contentHolder) {
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
    protected AileronBearingBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);
        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(playerInventory.player.level(), contentSubLevelId, contentPos);
        return blockEntity instanceof AileronBearingBlockEntity bearing ? bearing : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        initialMinAngles().clear();
        initialMaxAngles().clear();
        for (BearingHead head : BearingHead.values()) {
            initialMinAngles().put(head, buf.readDouble());
            initialMaxAngles().put(head, buf.readDouble());
        }
        initialGhostStacks = new ItemStack[8];
        for (int i = 0; i < initialGhostStacks.length; i++) {
            initialGhostStacks[i] = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        receivedInitialConfig = true;
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
        addFrequencySlots(BearingHead.PRIMARY,
                AileronBearingBlockEntity.ControlDirection.CW);
        addFrequencySlots(BearingHead.PRIMARY,
                AileronBearingBlockEntity.ControlDirection.CCW);
        addFrequencySlots(BearingHead.SECONDARY,
                AileronBearingBlockEntity.ControlDirection.CW);
        addFrequencySlots(BearingHead.SECONDARY,
                AileronBearingBlockEntity.ControlDirection.CCW);
    }

    // Add the frequency slots
    private void addFrequencySlots(BearingHead head,
                                   AileronBearingBlockEntity.ControlDirection dir) {
        int startSlot = slotIndex(head, dir);
        int x = frequencySlotX(head, dir);
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
    protected void saveData(AileronBearingBlockEntity contentHolder) {
        if (contentHolder == null) {
            return;
        }
        for (BearingHead head : BearingHead.values()) {
            for (AileronBearingBlockEntity.ControlDirection dir : AileronBearingBlockEntity.ControlDirection.values()) {
                int startSlot = slotIndex(head, dir);
                contentHolder.setFrequency(head, dir,
                        copySingle(ghostInventory.getStackInSlot(startSlot)),
                        copySingle(ghostInventory.getStackInSlot(startSlot + 1)));
            }
        }
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
    public AileronBearingBlockEntity getMenuConfigTargetBlockEntity() {
        return contentHolder;
    }

    // Get the initial min angle
    public double getInitialMinAngle(BearingHead head) {
        if (receivedInitialConfig) {
            return initialMinAngles().getOrDefault(head, -45.0D);
        }
        return contentHolder == null ? -45.0D : contentHolder.getMinAngle(head);
    }

    // Get the initial max angle
    public double getInitialMaxAngle(BearingHead head) {
        if (receivedInitialConfig) {
            return initialMaxAngles().getOrDefault(head, 45.0D);
        }
        return contentHolder == null ? 45.0D : contentHolder.getMaxAngle(head);
    }

    // Get the initial min angles
    private EnumMap<BearingHead, Double> initialMinAngles() {
        if (initialMinAngles == null) {
            initialMinAngles = new EnumMap<>(BearingHead.class);
        }
        return initialMinAngles;
    }

    // Get the initial max angles
    private EnumMap<BearingHead, Double> initialMaxAngles() {
        if (initialMaxAngles == null) {
            initialMaxAngles = new EnumMap<>(BearingHead.class);
        }
        return initialMaxAngles;
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
        for (BearingHead head : BearingHead.values()) {
            for (AileronBearingBlockEntity.ControlDirection dir : AileronBearingBlockEntity.ControlDirection.values()) {
                int startSlot = slotIndex(head, dir);
                ghostInventory.setStackInSlot(startSlot, copySingle(contentHolder.getFrequencyFirst(head, dir)));
                ghostInventory.setStackInSlot(startSlot + 1, copySingle(contentHolder.getFrequencySecond(head, dir)));
            }
        }
    }

    // Get the slot index
    public static int slotIndex(BearingHead head,
                                AileronBearingBlockEntity.ControlDirection dir) {
        return head.ordinal() * 4 + dir.ordinal() * 2;
    }

    // Get the frequency slot x
    public static int frequencySlotX(BearingHead head,
                                     AileronBearingBlockEntity.ControlDirection dir) {
        boolean primary = head == BearingHead.PRIMARY;
        boolean clockwise = dir == AileronBearingBlockEntity.ControlDirection.CW;
        if (primary) {
            return clockwise ? CYAN_CW_X : CYAN_CCW_X;
        }
        return clockwise ? ORANGE_CW_X : ORANGE_CCW_X;
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
