package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import com.rieno.gadgetsandgizmos.lib.menuconfig.ISimulatedMenuOpen;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityTarget;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Sync controller configuration and its filtered item slots between the screen and server
public class AnalogueContraptionControllerMenu extends GhostItemMenu<AnalogueContraptionControllerBlockEntity>
    implements ISimulatedMenuOpen, MenuBackedBlockEntityTarget<AnalogueContraptionControllerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PLAYER_SLOTS_X = 50;

    public static final int PLAYER_SLOTS_Y = 232;
    public static final int GHOST_SLOT_OUTPUT_FIRST_X = 104;
    public static final int GHOST_SLOT_OUTPUT_SECOND_X = 124;
    public static final int GHOST_SLOT_INPUT_FIRST_X = 176;
    public static final int GHOST_SLOT_INPUT_SECOND_X = 196;
    public static final int GHOST_SLOTS_Y = 26;
    public static final int LINKER_SLOT_X = 256;
    public static final int LINKER_SLOT_Y = 44;
    public static final int GOGGLES_INPUT_SLOT_X = 154;
    public static final int GOGGLES_INPUT_SLOT_Y = 112;
    public static final int GOGGLES_OUTPUT_SLOT_X = 236;
    public static final int GOGGLES_OUTPUT_SLOT_Y = 112;
    public static final String GOGGLES_BIND_ROOT = "CreateThrustersControllerBinding";
    public static final String GOGGLES_BIND_X = "X";
    public static final String GOGGLES_BIND_Y = "Y";
    public static final String GOGGLES_BIND_Z = "Z";
    public static final String GOGGLES_BIND_SUBLEVEL = "SubLevel";
    public static final String GOGGLES_BIND_PAIR_ID = "PairId";
    public static final String GOGGLES_BIND_PAIR_LABEL = "PairLabel";
    private static final int GOGGLES_LINK_DURATION = 40;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether player slots are active
    public boolean playerSlotsActive;
    // Tracks whether ghost slots are active
    public boolean ghostSlotsActive;
    // Current ghost slot mask
    public int ghostSlotMask = 0xF;
    // Tracks whether linker slot is active
    public boolean linkerSlotActive;
    // Tracks whether goggles slot is active
    public boolean gogglesSlotActive;
    // Current channel id
    private String currentChannelId = AnalogueControlChannel.PITCH_UP.id();
    // Current content position
    private BlockPos contentPos;
    // Current content sub-level id
    private UUID contentSubLevelId;

    // Initial channel states
    private Map<String, InitialChannelState> initialChannelStates;
    // Initial stored targets
    private List<ControllerDiscoveryNode> initialStoredTargets;

    // Initial custom entries
    private List<CustomKeyEntry> initialCustomEntries;

    // Current linker container
    private Container linkerContainer;
    // Current goggles container
    private Container gogglesContainer;
    // Current goggles link progress
    private int gogglesLinkProgress;
    // Current goggles link duration
    private int gogglesLinkDuration = GOGGLES_LINK_DURATION;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller menu
    public AnalogueContraptionControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(CTMenuTypes.ANALOGUE_CONTRAPTION_CONTROLLER.get(), id, inv, extraData);
    }

    // Initialize the analogue contraption controller menu
    public AnalogueContraptionControllerMenu(int id, Inventory inv, AnalogueContraptionControllerBlockEntity blockEntity) {
        this(CTMenuTypes.ANALOGUE_CONTRAPTION_CONTROLLER.get(), id, inv, blockEntity);
    }

    // Initialize the analogue contraption controller menu
    public AnalogueContraptionControllerMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
        addDataSlot(new DataSlot() {
            // Get the analogue contraption controller menu value
            @Override
            public int get() {
                return gogglesLinkProgress;
            }

            // Set the analogue contraption controller menu
            @Override
            public void set(int val) {
                gogglesLinkProgress = val;
            }
        });
        addDataSlot(new DataSlot() {
            // Get the analogue contraption controller menu value
            @Override
            public int get() {
                return gogglesLinkDuration;
            }

            // Set the analogue contraption controller menu
            @Override
            public void set(int val) {
                gogglesLinkDuration = val;
            }
        });
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            linkerContainer().setItem(0, contentHolder.getStoredLinker().copy());
        }
        loadCurrentChannelGhostInventory();
    }

    // Initialize the analogue contraption controller menu
    public AnalogueContraptionControllerMenu(MenuType<?> type, int id, Inventory inv, AnalogueContraptionControllerBlockEntity blockEntity) {
        super(type, id, inv, blockEntity);
        addDataSlot(new DataSlot() {
            // Get the analogue contraption controller menu value
            @Override
            public int get() {
                return gogglesLinkProgress;
            }

            // Set the analogue contraption controller menu
            @Override
            public void set(int val) {
                gogglesLinkProgress = val;
            }
        });
        addDataSlot(new DataSlot() {
            // Get the analogue contraption controller menu value
            @Override
            public int get() {
                return gogglesLinkDuration;
            }

            // Set the analogue contraption controller menu
            @Override
            public void set(int val) {
                gogglesLinkDuration = val;
            }
        });
        if (contentHolder != null) {
            contentPos = contentHolder.getBlockPos();
            linkerContainer().setItem(0, contentHolder.getStoredLinker().copy());
        }
        captureInitialState(blockEntity);
        loadCurrentChannelGhostInventory();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected AnalogueContraptionControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {

        MenuOpenHeader header = MenuOpenHeader.decode(extraData);
        contentPos = header.pos();
        contentSubLevelId = header.subLevelId();
        readExtraOpenData(extraData);

        if (playerInventory == null || playerInventory.player == null) {
            return null;
        }
        var level = playerInventory.player.level();
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, contentSubLevelId, contentPos);
        return blockEntity instanceof AnalogueContraptionControllerBlockEntity controller ? controller : null;
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buf) {
        readInitialState(buf);
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
    public AnalogueContraptionControllerBlockEntity getMenuConfigTargetBlockEntity() {
        return contentHolder;
    }

    // Get the initial channel state
    public InitialChannelState getInitialChannelState(String channelId) {
        return channelStateStore().get(channelId);
    }

    // Get the initial stored targets
    public List<ControllerDiscoveryNode> getInitialStoredTargets() {
        return List.copyOf(storedTargetStore());
    }

    // Get the initial linker targets
    public List<ControllerDiscoveryNode> getInitialLinkerTargets() {
        return ContraptionNetworkLinkerData.toDiscoveryNodes(getAuthoritativeLinkerStack());
    }

    // Get the current linker stack
    public ItemStack getCurrentLinkerStack() {
        return copySingle(getAuthoritativeLinkerStack());
    }

    // Get the goggles output stack
    public ItemStack getGogglesOutputStack() {
        return copySingle(gogglesContainer().getItem(1));
    }

    // Get the initial custom entries
    public List<CustomKeyEntry> getInitialCustomEntries() {
        if (initialCustomEntries == null) return List.of();
        return List.copyOf(initialCustomEntries);
    }

    // Get the graph binding options
    public List<GraphBindingOption> getGraphBindingOptions() {
        List<GraphBindingOption> opts = new ArrayList<>();
        for (Map.Entry<String, InitialChannelState> entry : channelStateStore().entrySet()) {
            if (entry.getValue().keyCode >= 0) {
                opts.add(new GraphBindingOption(entry.getKey(), "Key " + entry.getValue().keyCode, "key"));
            }
        }
        for (CustomKeyEntry entry : getInitialCustomEntries()) {
            String label = entry.label == null || entry.label.isBlank() ? "Custom Key" : entry.label;
            opts.add(new GraphBindingOption(entry.id(), label, "custom_key"));
        }
        return List.copyOf(opts);
    }

    // Get the gamepad binding options
    public List<GraphBindingOption> getGamepadBindingOptions() {
        return HardwareControllerBindings.options().stream()
                .map(option -> new GraphBindingOption(option.id(), option.label(), "hardware_controller"))
                .toList();
    }

    // Get the graph target options
    public List<ControllerDiscoveryNode> getGraphTargetOptions() {
        Map<String, ControllerDiscoveryNode> targets = new LinkedHashMap<>();
        for (ControllerDiscoveryNode node : getInitialStoredTargets()) targets.putIfAbsent(node.nodeId(), node);
        for (ControllerDiscoveryNode node : getInitialLinkerTargets()) targets.putIfAbsent(node.nodeId(), node);
        return List.copyOf(targets.values());
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
        addSlot(new ActiveGhostSlot(ghostInventory, 0, GHOST_SLOT_OUTPUT_FIRST_X, GHOST_SLOTS_Y));
        addSlot(new ActiveGhostSlot(ghostInventory, 1, GHOST_SLOT_OUTPUT_SECOND_X, GHOST_SLOTS_Y));
        addSlot(new ActiveGhostSlot(ghostInventory, 2, GHOST_SLOT_INPUT_FIRST_X, GHOST_SLOTS_Y));
        addSlot(new ActiveGhostSlot(ghostInventory, 3, GHOST_SLOT_INPUT_SECOND_X, GHOST_SLOTS_Y));
        addSlot(new ActiveLinkerSlot(linkerContainer(), 0, linkerSlotX(), linkerSlotY()));
        addSlot(new ActiveGogglesInputSlot(gogglesContainer(), 0, gogglesInputSlotX(), gogglesInputSlotY()));
        addSlot(new ActiveGogglesOutputSlot(gogglesContainer(), 1, gogglesOutputSlotX(), gogglesOutputSlotY()));
    }

    // Get the linker slot x
    protected int linkerSlotX() {
        return LINKER_SLOT_X;
    }

    // Get the linker slot y
    protected int linkerSlotY() {
        return LINKER_SLOT_Y;
    }

    // Get the goggles input slot x
    protected int gogglesInputSlotX() {
        return GOGGLES_INPUT_SLOT_X;
    }

    // Get the goggles input slot y
    protected int gogglesInputSlotY() {
        return GOGGLES_INPUT_SLOT_Y;
    }

    // Get the goggles output slot x
    protected int gogglesOutputSlotX() {
        return GOGGLES_OUTPUT_SLOT_X;
    }

    // Get the goggles output slot y
    protected int gogglesOutputSlotY() {
        return GOGGLES_OUTPUT_SLOT_Y;
    }

    // Handle the menu click
    @Override
    public void clicked(int slotId, int btn, ClickType clickType, net.minecraft.world.entity.player.Player player) {
        if (slotId >= 0 && slotId < slots.size()
                && (slots.get(slotId) instanceof ActiveLinkerSlot
                || slots.get(slotId) instanceof ActiveGogglesInputSlot
                || slots.get(slotId) instanceof ActiveGogglesOutputSlot)) {
            if (clickType == ClickType.QUICK_MOVE) {
                quickMoveStack(player, slotId);
                return;
            }
            clickLinkerSlot(slotId, clickType);
            return;
        }
        super.clicked(slotId, btn, clickType, player);
    }

    // Move the stack quickly
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int idx) {
        if (idx < 0 || idx >= slots.size()) return ItemStack.EMPTY;
        Slot src = slots.get(idx);
        if (!src.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = src.getItem();
        ItemStack original = sourceStack.copy();
        int linkerIndex = -1;
        int gogglesInputIndex = -1;
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            if (slots.get(slotIndex) instanceof ActiveLinkerSlot) {
                linkerIndex = slotIndex;
            } else if (slots.get(slotIndex) instanceof ActiveGogglesInputSlot) {
                gogglesInputIndex = slotIndex;
            }
        }
        if (src instanceof ActiveLinkerSlot
                || src instanceof ActiveGogglesInputSlot
                || src instanceof ActiveGogglesOutputSlot) {
            if (!moveControllerStackTo(sourceStack, 0, Math.min(36, slots.size()), false)) {
                return ItemStack.EMPTY;
            }
        } else if (src instanceof ActivePlayerSlot && sourceStack.getItem() instanceof ContraptionNetworkLinkerItem) {
            if (linkerIndex < 0) return ItemStack.EMPTY;
            if (!moveControllerStackTo(sourceStack, linkerIndex, linkerIndex + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (src instanceof ActivePlayerSlot
                && PhysicsGogglesItem.isFunctionalGoggles(sourceStack)) {
            if (gogglesInputIndex < 0) return ItemStack.EMPTY;
            if (!moveControllerStackTo(sourceStack, gogglesInputIndex, gogglesInputIndex + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (sourceStack.isEmpty()) src.set(ItemStack.EMPTY);
        else src.setChanged();
        src.onTake(player, sourceStack);
        return original;
    }

    // Move the controller stack
    private boolean moveControllerStackTo(
            ItemStack stack, int startIndex, int endIndex, boolean reverse
    ) {
        if (stack.isEmpty() || startIndex < 0 || endIndex > slots.size()
                || startIndex >= endIndex) {
            return false;
        }
        boolean moved = false;
        int idx = reverse ? endIndex - 1 : startIndex;
        int step = reverse ? -1 : 1;

        if (stack.isStackable()) {
            while (!stack.isEmpty() && idx >= startIndex && idx < endIndex) {
                Slot target = slots.get(idx);
                ItemStack existing = target.getItem();
                if (!existing.isEmpty()
                        && target.mayPlace(stack)
                        && ItemStack.isSameItemSameComponents(existing, stack)) {
                    int maximum = Math.min(target.getMaxStackSize(), stack.getMaxStackSize());
                    int transferable = Math.min(stack.getCount(), maximum - existing.getCount());
                    if (transferable > 0) {
                        existing.grow(transferable);
                        stack.shrink(transferable);
                        target.setChanged();
                        moved = true;
                    }
                }
                idx += step;
            }
        }

        idx = reverse ? endIndex - 1 : startIndex;
        while (!stack.isEmpty() && idx >= startIndex && idx < endIndex) {
            Slot target = slots.get(idx);
            if (!target.hasItem() && target.mayPlace(stack)) {
                int transferable = Math.min(stack.getCount(),
                        Math.min(target.getMaxStackSize(), stack.getMaxStackSize()));
                ItemStack inserted = stack.copy();
                inserted.setCount(transferable);
                target.set(inserted);
                target.setChanged();
                stack.shrink(transferable);
                moved = true;
                break;
            }
            idx += step;
        }
        return moved;
    }

    // Handle the linker slot click
    private void clickLinkerSlot(int slotId, ClickType clickType) {
        if (clickType == ClickType.THROW) {
            return;
        }
        Slot slot = getSlot(slotId);
        if (slot == null) {
            return;
        }

        ItemStack carried = getCarried();
        ItemStack slotStack = slot.getItem();

        if (clickType == ClickType.CLONE) {
            return;
        }

        if (carried.isEmpty()) {
            if (!slotStack.isEmpty()) {
                setCarried(slotStack.copy());
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
            }
            return;
        }

        if (!slot.mayPlace(carried)) {
            return;
        }

        ItemStack placed = carried.copy();
        placed.setCount(1);
        slot.set(placed);
        carried.shrink(1);
        if (!slotStack.isEmpty()) {
            setCarried(slotStack.copy());
        } else {
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
        slot.setChanged();
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
    protected void saveData(AnalogueContraptionControllerBlockEntity contentHolder) {
        if (contentHolder == null || contentHolder.getLevel() == null || contentHolder.getLevel().isClientSide) {
            return;
        }
        ItemStack linker = copySingle(getAuthoritativeLinkerStack());
        if (!sameSingleItem(contentHolder.getStoredLinker(), linker)) {
            contentHolder.getLinkerSlotHandler().setStackInSlot(0, linker);
        }

        contentHolder.setChannelFrequency(currentChannelId,
                copySingle(ghostInventory.getStackInSlot(0)),
                copySingle(ghostInventory.getStackInSlot(1)));
        contentHolder.setChannelInputFrequency(currentChannelId,
                copySingle(ghostInventory.getStackInSlot(2)),
                copySingle(ghostInventory.getStackInSlot(3)));
    }

    // Check if repeated input is allowed
    @Override
    protected boolean allowRepeats() {
        return false;
    }

    // Set the current channel id
    public void setCurrentChannelId(String currentChannelId) {
        if (currentChannelId == null || currentChannelId.isBlank()) {
            return;
        }
        this.currentChannelId = currentChannelId;
    }

    // Get the linker container
    private Container linkerContainer() {
        if (linkerContainer == null) {
            linkerContainer = new SimpleContainer(1) {
                // Set the changed
                @Override
                public void setChanged() {
                    super.setChanged();
                    if (contentHolder == null || contentHolder.getLevel() == null || contentHolder.getLevel().isClientSide) {
                        return;
                    }
                    ItemStack linker = copySingle(getItem(0));
                    if (!sameSingleItem(contentHolder.getStoredLinker(), linker)) {
                        contentHolder.getLinkerSlotHandler().setStackInSlot(0, linker);
                    }
                }
            };
        }
        return linkerContainer;
    }

    // Get the goggles container
    private Container gogglesContainer() {
        if (gogglesContainer == null) {
            gogglesContainer = new SimpleContainer(2) {
                // Set the changed
                @Override
                public void setChanged() {
                    super.setChanged();
                    onGogglesSlotsChanged();
                }
            };
        }
        return gogglesContainer;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the goggles slots changed event
    private void onGogglesSlotsChanged() {
        if (playerInventory == null || playerInventory.player == null || playerInventory.player.level().isClientSide) {
            return;
        }
        ItemStack input = gogglesContainer().getItem(0);
        ItemStack output = gogglesContainer().getItem(1);
        if (input.isEmpty() || !output.isEmpty()) {
            gogglesLinkProgress = 0;
        } else if (gogglesLinkProgress == 0) {
            gogglesLinkDuration = GOGGLES_LINK_DURATION;
        }
    }

    // Broadcast the changes
    @Override
    public void broadcastChanges() {
        tickGogglesLinking();
        super.broadcastChanges();
    }

    // Update the goggles linking
    private void tickGogglesLinking() {
        if (playerInventory == null || playerInventory.player == null || playerInventory.player.level().isClientSide) {
            return;
        }
        ItemStack input = gogglesContainer().getItem(0);
        ItemStack output = gogglesContainer().getItem(1);
        if (input.isEmpty() || !output.isEmpty()) {
            gogglesLinkProgress = 0;
            return;
        }
        if (gogglesLinkDuration <= 0) {
            gogglesLinkDuration = GOGGLES_LINK_DURATION;
        }
        gogglesLinkProgress = Math.min(gogglesLinkDuration, gogglesLinkProgress + 1);
        if (gogglesLinkProgress < gogglesLinkDuration) {
            return;
        }
        ItemStack linked = input.copy();
        linked.setCount(1);
        String previousPairLabel = PhysicsGogglesItem.linkedPairLabel(linked);
        AdvancedContraptionControllerBlockEntity advanced =
                contentHolder instanceof AdvancedContraptionControllerBlockEntity controller ? controller : null;
        UUID pairId = advanced == null ? null : UUID.randomUUID();
        String pairLabel = advanced == null ? null : advanced.nextGogglesTrackerPairLabel();
        boolean linkedToController = advanced == null
                ? bindGogglesToController(linked, getContentPos(), getContentSubLevelId())
                : bindGogglesToController(
                        linked, getContentPos(), getContentSubLevelId(), pairId, pairLabel);
        if (!linkedToController) {
            gogglesLinkProgress = 0;
            return;
        }
        if (advanced != null) {
            advanced.registerGogglesTrackerPair(pairId, pairLabel);
        }
        linked = renameLinkedGoggles(linked, previousPairLabel, pairLabel);
        gogglesContainer().setItem(0, ItemStack.EMPTY);
        if (advanced == null) {
            playerInventory.placeItemBackInInventory(linked);
        } else {
            gogglesContainer().setItem(1, linked);
        }
        gogglesLinkProgress = 0;
    }

    // Bind the goggles to controller
    public static boolean bindGogglesToController(ItemStack stack, BlockPos pos, UUID subLevelId) {
        return bindGogglesToController(stack, pos, subLevelId, null, null);
    }

    // Bind the goggles to controller
    public static boolean bindGogglesToController(ItemStack stack, BlockPos pos, UUID subLevelId,
                                                  UUID pairId, String pairLabel) {
        if (!PhysicsGogglesItem.isFunctionalGoggles(stack) || pos == null) {
            return false;
        }
        CompoundTag customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag binding = new CompoundTag();
        binding.putInt(GOGGLES_BIND_X, pos.getX());
        binding.putInt(GOGGLES_BIND_Y, pos.getY());
        binding.putInt(GOGGLES_BIND_Z, pos.getZ());
        if (subLevelId != null) {
            binding.putUUID(GOGGLES_BIND_SUBLEVEL, subLevelId);
        }
        if (pairId != null) {
            binding.putUUID(GOGGLES_BIND_PAIR_ID, pairId);
        }
        if (pairLabel != null && !pairLabel.isBlank()) {
            binding.putString(GOGGLES_BIND_PAIR_LABEL, pairLabel.trim());
        }
        customData.put(GOGGLES_BIND_ROOT, binding);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        return true;
    }

    // Rename the linked goggles
    private static ItemStack renameLinkedGoggles(
            ItemStack stack, String previousPairLabel, String pairLabel
    ) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String currentName = stack.getHoverName().getString();
        String linkedName = relinkedGogglesName(
                currentName,
                stack.getItem().getName(stack).getString(),
                stack.has(DataComponents.CUSTOM_NAME),
                previousPairLabel,
                pairLabel);
        if (!currentName.equals(linkedName)) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(linkedName).setStyle(Style.EMPTY.withItalic(false)));
        }
        return stack;
    }

    // Get the relinked goggles name
    static String relinkedGogglesName(
            String currentName,
            String defaultName,
            boolean hasCustomName,
            String previousPairLabel,
            String pairLabel
    ) {
        String nextLabel = pairLabel == null || pairLabel.isBlank()
                ? "Linked" : pairLabel.trim();
        String nextSuffix = " [" + nextLabel + "]";
        if (!hasCustomName) {
            return (defaultName == null ? "" : defaultName) + nextSuffix;
        }
        String current = currentName == null ? "" : currentName;
        String base = defaultName == null ? "" : defaultName;
        if (!base.isBlank()
                && current.startsWith(base)
                && isGeneratedGogglesSuffixChain(current.substring(base.length()))) {
            return base + nextSuffix;
        }
        if (previousPairLabel == null || previousPairLabel.isBlank()) {
            return current;
        }
        String previousSuffix = " [" + previousPairLabel.trim() + "]";
        return current.endsWith(previousSuffix)
                ? current.substring(0, current.length() - previousSuffix.length()) + nextSuffix
                : current;
    }

    // Check if this is a generated goggles suffix chain
    private static boolean isGeneratedGogglesSuffixChain(String val) {
        if (val == null || val.isEmpty()) {
            return false;
        }
        int cursor = 0;
        while (cursor < val.length()) {
            if (!val.startsWith(" [", cursor)) {
                return false;
            }
            int end = val.indexOf(']', cursor + 2);
            if (end <= cursor + 2) {
                return false;
            }
            cursor = end + 1;
        }
        return true;
    }

    // Get the goggles link progress
    public int getGogglesLinkProgress() {
        return gogglesLinkProgress;
    }

    // Get the goggles link duration
    public int getGogglesLinkDuration() {
        return gogglesLinkDuration <= 0 ? GOGGLES_LINK_DURATION : gogglesLinkDuration;
    }

    // Save the goggles container
    protected CompoundTag saveGogglesContainer(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ItemStack input = gogglesContainer().getItem(0);
        ItemStack output = gogglesContainer().getItem(1);
        if (!input.isEmpty()) {
            tag.put("Input", copySingle(input).saveOptional(provider));
        }
        if (!output.isEmpty()) {
            tag.put("Output", copySingle(output).saveOptional(provider));
        }
        tag.putInt("Progress", gogglesLinkProgress);
        tag.putInt("Duration", getGogglesLinkDuration());
        return tag;
    }

    // Load the goggles container
    protected void loadGogglesContainer(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag == null) {
            return;
        }
        ItemStack input = tag.contains("Input", Tag.TAG_COMPOUND)
                ? copySingle(ItemStack.parseOptional(provider, tag.getCompound("Input")))
                : ItemStack.EMPTY;
        ItemStack output = tag.contains("Output", Tag.TAG_COMPOUND)
                ? copySingle(ItemStack.parseOptional(provider, tag.getCompound("Output")))
                : ItemStack.EMPTY;
        gogglesContainer().setItem(0, input);
        gogglesContainer().setItem(1, output);
        gogglesLinkProgress = Math.max(0, tag.getInt("Progress"));
        gogglesLinkDuration = Math.max(1, tag.contains("Duration") ? tag.getInt("Duration") : GOGGLES_LINK_DURATION);
    }

    // Get the authoritative linker stack
    private ItemStack getAuthoritativeLinkerStack() {
        if (linkerContainer != null) {
            ItemStack menuStack = linkerContainer.getItem(0);
            if (contentHolder == null || contentHolder.getLevel() == null || contentHolder.getLevel().isClientSide) {
                return menuStack;
            }
        }
        if (contentHolder != null) {
            return contentHolder.getStoredLinker();
        }
        return linkerContainer == null ? ItemStack.EMPTY : linkerContainer.getItem(0);
    }

    // Capture the initial state
    private void captureInitialState(AnalogueContraptionControllerBlockEntity blockEntity) {
        Map<String, InitialChannelState> channelStates = channelStateStore();
        List<ControllerDiscoveryNode> storedTargets = storedTargetStore();
        channelStates.clear();
        storedTargets.clear();
        contentSubLevelId = blockEntity == null ? null : SimulatedHelper.getContainingSubLevelId(blockEntity);
        if (blockEntity == null) {
            return;
        }

        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            var liveChannel = blockEntity.getChannel(channel.id());
            if (liveChannel == null) {
                continue;
            }
            channelStates.put(channel.id(), new InitialChannelState(
                    liveChannel.getMode(),
                    liveChannel.getRiseRate(),
                    liveChannel.getFallRate(),
                    liveChannel.getStepAmount(),
                    liveChannel.getDeadzone(),
                    liveChannel.getSmoothing(),
                    blockEntity.getLocalOutputSide(channel.id()),
                    copySingle(blockEntity.getFrequencyFirst(channel.id())),
                    copySingle(blockEntity.getFrequencySecond(channel.id())),
                    copySingle(blockEntity.getInputFrequencyFirst(channel.id())),
                    copySingle(blockEntity.getInputFrequencySecond(channel.id())),
                    blockEntity.getKeyBinding(channel.id()),
                    blockEntity.getDirectTarget(channel.id()),
                    blockEntity.getInputTarget(channel.id()),
                    blockEntity.getBindingMode(channel.id()),
                    blockEntity.getBindingPreset(channel.id())));
        }

        storedTargets.addAll(blockEntity.getStoredTargets());

        if (initialCustomEntries == null) initialCustomEntries = new ArrayList<>();
        initialCustomEntries.clear();
        initialCustomEntries.addAll(blockEntity.getCustomKeyEntries());
    }

    // Read the initial state
    private void readInitialState(RegistryFriendlyByteBuf extraData) {

        // -----------------------------------------------------STATE RESET-----------------------------------------------------
        Map<String, InitialChannelState> channelStates = channelStateStore();
        List<ControllerDiscoveryNode> storedTargets = storedTargetStore();
        channelStates.clear();
        storedTargets.clear();

        // ------------------------------------STORED TARGETS------------------------------------
        int storedTargetCount = extraData.readVarInt();
        for (int idx = 0; idx < storedTargetCount; idx++) {
            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(emptyIfNull(extraData.readNbt()));
            if (node != null && node.isValid()) {
                storedTargets.add(node);
            }
        }

        // -----------------------------------------------------CHANNELS-----------------------------------------------------
        int channelCount = extraData.readVarInt();
        for (int idx = 0; idx < channelCount; idx++) {
            String channelId = extraData.readUtf();
            AnalogueChannelMode mode = parseMode(extraData.readUtf());
            double riseRate = extraData.readFloat();
            double fallRate = extraData.readFloat();
            double stepAmount = extraData.readFloat();
            double deadzone = extraData.readFloat();
            double smoothing = extraData.readFloat();
            String sideName = extraData.readUtf();
            Direction localOutputSide = sideName.isBlank() ? null : Direction.byName(sideName);
            ItemStack first = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            ItemStack second = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));

            ItemStack inputFirst = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            ItemStack inputSecond = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            int keyCode = extraData.readInt();
            ControllerDirectTargetReference directTarget = ControllerDirectTargetReference.fromTag(emptyIfNull(extraData.readNbt()));
                ControllerDirectTargetReference inputTarget = ControllerDirectTargetReference.fromTag(emptyIfNull(extraData.readNbt()));
            String bindingMode = extraData.readUtf();
                String bindingPreset = extraData.readUtf();
            channelStates.put(channelId, new InitialChannelState(mode, riseRate, fallRate, stepAmount,
                    deadzone, smoothing, localOutputSide, first, second, inputFirst, inputSecond, keyCode, directTarget, inputTarget, bindingMode, bindingPreset));
        }

        // ------------------------------------CUSTOM ENTRIES------------------------------------
        if (initialCustomEntries == null) initialCustomEntries = new ArrayList<>();
        initialCustomEntries.clear();
        int customCount = extraData.readVarInt();
        for (int i = 0; i < customCount; i++) {
            String entryId = extraData.readUtf();
            int entryKeyCode = extraData.readInt();
            int entryStepDownKeyCode = extraData.readInt();
            String entryLabel = extraData.readUtf();
            String entryModeStr = extraData.readUtf();
            com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode entryMode;
            try {
                entryMode = com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode.valueOf(entryModeStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                entryMode = com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode.RAMP;
            }
            double entryRise = extraData.readFloat();
            double entryFall = extraData.readFloat();
            double entryStep = extraData.readFloat();
            double entryStepDown = extraData.readFloat();
            double entryDead = extraData.readFloat();
            double entrySmooth = extraData.readFloat();
            String entrySide = extraData.readUtf();
            Direction entryLocalSide = entrySide.isBlank() ? null : Direction.byName(entrySide);
            ItemStack entryFirst = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            ItemStack entrySecond = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            ItemStack entryInputFirst = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            ItemStack entryInputSecond = copySingle(ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
            CompoundTag entryDirectTag = emptyIfNull(extraData.readNbt());
            CompoundTag entryInputTag = emptyIfNull(extraData.readNbt());
            String entryBindingPreset = extraData.readUtf();
            CustomKeyEntry entry = new CustomKeyEntry(entryId);
            entry.keyCode = entryKeyCode;
            entry.stepDownKeyCode = entryStepDownKeyCode;
            entry.label = entryLabel;
            entry.mode = entryMode;
            entry.riseRate = entryRise;
            entry.fallRate = entryFall;
            entry.stepAmount = entryStep;
            entry.stepDownAmount = entryStepDown;
            entry.deadzone = entryDead;
            entry.smoothing = entrySmooth;
            entry.localOutputSide = entryLocalSide;
            entry.first = entryFirst;
            entry.second = entrySecond;
            entry.inputFirst = entryInputFirst;
            entry.inputSecond = entryInputSecond;
            entry.directTarget = ControllerDirectTargetReference.fromTag(entryDirectTag);
            entry.inputTarget = ControllerDirectTargetReference.fromTag(entryInputTag);
            entry.bindingPreset = entryBindingPreset == null || entryBindingPreset.isBlank() ? "none" : entryBindingPreset;
            initialCustomEntries.add(entry);
        }
    }

    // Parse the mode
    private static AnalogueChannelMode parseMode(String serialized) {
        try {
            return AnalogueChannelMode.valueOf(serialized.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException err) {
            return AnalogueChannelMode.RAMP;
        }
    }

    // Get the empty if null
    private static CompoundTag emptyIfNull(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    // Load the current channel ghost inventory
    private void loadCurrentChannelGhostInventory() {
        if (contentHolder == null) {
            return;
        }

        ghostInventory.setStackInSlot(0, copySingle(contentHolder.getFrequencyFirst(currentChannelId)));
        ghostInventory.setStackInSlot(1, copySingle(contentHolder.getFrequencySecond(currentChannelId)));
        ghostInventory.setStackInSlot(2, copySingle(contentHolder.getInputFrequencyFirst(currentChannelId)));
        ghostInventory.setStackInSlot(3, copySingle(contentHolder.getInputFrequencySecond(currentChannelId)));
    }

    // Get the channel state store
    private Map<String, InitialChannelState> channelStateStore() {
        if (initialChannelStates == null) {
            initialChannelStates = new LinkedHashMap<>();
        }
        return initialChannelStates;
    }

    // Get the stored target store
    private List<ControllerDiscoveryNode> storedTargetStore() {
        if (initialStoredTargets == null) {
            initialStoredTargets = new ArrayList<>();
        }
        return initialStoredTargets;
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

    // Check if this uses the same single item
    protected static boolean sameSingleItem(ItemStack first, ItemStack second) {
        if (first == null || first.isEmpty()) {
            return second == null || second.isEmpty();
        }
        if (second == null || second.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }

    // Store initial channel state
    public static final class InitialChannelState {
        // Initial channel mode
        private final AnalogueChannelMode mode;
        // Rise rate
        private final double riseRate;
        // Fall rate
        private final double fallRate;
        // Step amount
        private final double stepAmount;
        // Deadzone
        private final double deadzone;
        // Smoothing
        private final double smoothing;
        // Local output side
        private final Direction localOutputSide;
        // First entry
        private final ItemStack first;
        // Second entry
        private final ItemStack second;

        // Input first
        private final ItemStack inputFirst;
        // Input second
        private final ItemStack inputSecond;
        // Key code
        private final int keyCode;
        // Direct target
        private final ControllerDirectTargetReference directTarget;
        // Input target
        private final ControllerDirectTargetReference inputTarget;

        // Binding mode
        private final String bindingMode;
        // Binding preset
        private final String bindingPreset;

        // Initialize the initial channel state
        private InitialChannelState(AnalogueChannelMode mode, double riseRate, double fallRate, double stepAmount,
                                    double deadzone, double smoothing, Direction localOutputSide,
                                    ItemStack first, ItemStack second, ItemStack inputFirst, ItemStack inputSecond,
                                    int keyCode,
                                    ControllerDirectTargetReference directTarget,
                                    ControllerDirectTargetReference inputTarget, String bindingMode,
                                    String bindingPreset) {
            this.mode = mode == null ? AnalogueChannelMode.RAMP : mode;
            this.riseRate = riseRate;
            this.fallRate = fallRate;
            this.stepAmount = stepAmount;
            this.deadzone = deadzone;
            this.smoothing = smoothing;
            this.localOutputSide = localOutputSide;
            this.first = copySingle(first);
            this.second = copySingle(second);
            this.inputFirst = copySingle(inputFirst);
            this.inputSecond = copySingle(inputSecond);
            this.keyCode = keyCode;
            this.directTarget = directTarget;
            this.inputTarget = inputTarget;
            this.bindingMode = bindingMode == null || bindingMode.isBlank() ? "standard" : bindingMode;
            this.bindingPreset = bindingPreset == null || bindingPreset.isBlank() ? "none" : bindingPreset;
        }

        // Get the mode
        public AnalogueChannelMode mode() {
            return mode;
        }

        // Get the rise rate
        public double riseRate() {
            return riseRate;
        }

        // Get the fall rate
        public double fallRate() {
            return fallRate;
        }

        // Get the step amount
        public double stepAmount() {
            return stepAmount;
        }

        // Get the deadzone
        public double deadzone() {
            return deadzone;
        }

        // Get the smoothing
        public double smoothing() {
            return smoothing;
        }

        // Get the local output side
        public Direction localOutputSide() {
            return localOutputSide;
        }

        // Get the first
        public ItemStack first() {
            return copySingle(first);
        }

        // Get the second
        public ItemStack second() {
            return copySingle(second);
        }

        // Get the input first
        public ItemStack inputFirst() {
            return copySingle(inputFirst);
        }

        // Get the input second
        public ItemStack inputSecond() {
            return copySingle(inputSecond);
        }

        // Handle key code
        public int keyCode() {
            return keyCode;
        }

        // Get the direct target
        public ControllerDirectTargetReference directTarget() {
            return directTarget;
        }

        // Get the input target
        public ControllerDirectTargetReference inputTarget() {
            return inputTarget;
        }

        // Get the binding mode
        public String bindingMode() {
            return bindingMode;
        }

        // Get the binding preset
        public String bindingPreset() {
            return bindingPreset;
        }
    }

    // Store the graph binding option
    public record GraphBindingOption(String id, String label, String kind) {
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
            return playerSlotsActive || !playerInventory.player.level().isClientSide;
        }
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
            return ghostSlotsActive && (ghostSlotMask & (1 << getSlotIndex())) != 0;
        }
    }

    // Handle the active linker slot
    private class ActiveLinkerSlot extends Slot {
        // Initialize the active linker slot
        public ActiveLinkerSlot(Container container, int idx, int xPosition, int yPosition) {
            super(container, idx, xPosition, yPosition);
        }

        // Check if this may place
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getItem() instanceof ContraptionNetworkLinkerItem;
        }

        // Get the max stack size
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        // Check if this is active
        @Override
        public boolean isActive() {
            return linkerSlotActive || !playerInventory.player.level().isClientSide;
        }
    }

    // Handle the active goggles input slot
    private class ActiveGogglesInputSlot extends Slot {
        // Initialize the active goggles input slot
        public ActiveGogglesInputSlot(Container container, int idx, int xPosition, int yPosition) {
            super(container, idx, xPosition, yPosition);
        }

        // Check if this may place
        @Override
        public boolean mayPlace(ItemStack stack) {
            return PhysicsGogglesItem.isFunctionalGoggles(stack);
        }

        // Get the max stack size
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        // Check if this is active
        @Override
        public boolean isActive() {
            return gogglesSlotActive || !playerInventory.player.level().isClientSide;
        }
    }

    // Handle the active goggles output slot
    private class ActiveGogglesOutputSlot extends Slot {
        // Initialize the active goggles output slot
        public ActiveGogglesOutputSlot(Container container, int idx, int xPosition, int yPosition) {
            super(container, idx, xPosition, yPosition);
        }

        // Check if this may place
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        // Get the max stack size
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        // Check if this is active
        @Override
        public boolean isActive() {
            return gogglesSlotActive || !playerInventory.player.level().isClientSide;
        }
    }
}
