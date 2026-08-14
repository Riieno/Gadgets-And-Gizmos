package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannel;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerCustomKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryNodePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryRequestPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerGhostSlotsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerKeyPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Edit controller channels, bindings and display settings while keeping the server copy authoritative
public class AnalogueContraptionControllerConfigScreen extends AbstractSimiContainerScreen<AnalogueContraptionControllerMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String NODE_SUMMARY_HELPER_KEY = "createthrusters.controller.node_browser.hold_shift";
    private static final String DIRECT_TARGET_OPTION_PREFIX = "::opt:";
    private static final String DIRECT_TARGET_PROPERTY_PREFIX = "::prop:";
    private static final String STEP_DOWN_KEY_SUFFIX = "#step_down";
    private static final ResourceLocation TYPEWRITER_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/linked_typewriter/linked_typewriter.png");
    private static final ResourceLocation TYPEWRITER_KEYS_TEX =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/linked_typewriter/linked_typewriter_keys.png");

    private static final int MAIN_W = 296;
    private static final int MAIN_H = 320;
        private static final int KEYBOARD_W = 246;
        private static final int KEYBOARD_X = (MAIN_W - KEYBOARD_W) / 2;
    private static final int MAIN_KEYS_BG_H = 127;
    private static final int KEY_MENU_W = 312;
    private static final int KEY_MENU_H = 220;
    private static final int KEY_ENTRY_W = 288;
    private static final int KEY_ENTRY_H = 24;
    private static final int MODIFY_PANEL_W = 296;
    private static final int MODIFY_PANEL_H = 236;

    private static final int KEY_START_U = 60;
    private static final int KEY_MID_U = 66;
    private static final int KEY_END_U = 68;
    private static final int KEY_INACTIVE_START_U = 73;
    private static final int KEY_INACTIVE_MID_U = 79;
    private static final int KEY_INACTIVE_END_U = 81;
    private static final int KEY_V = 127;
    private static final int KEY_H = 14;
    private static final int KEY_START_W = 6;
    private static final int KEY_MID_W = 2;
    private static final int KEY_END_W = 6;

    private static final int MAIN_KEYS_X = 8;
    private static final int MAIN_KEYS_Y = 21;
    private static final int MAIN_ROW_STEP = 14;
    private static final int FOOTER_Y = 190;
    private static final int RESET_BUTTON_W = 56;
    private static final int FOOTER_BUTTON_GAP = 6;
    private static final int FOOTER_RIGHT_BUTTON_W = 40;
    private static final int MAIN_INPUTS_PANEL_X = KEYBOARD_X;
    private static final int MAIN_INPUTS_PANEL_Y = 94;
    private static final int MAIN_INPUTS_PANEL_W = KEYBOARD_W;
    private static final int MAIN_INPUTS_PANEL_H = 86;
    private static final int MAIN_INPUTS_X = MAIN_INPUTS_PANEL_X + 4;
    private static final int MAIN_INPUTS_Y = MAIN_INPUTS_PANEL_Y + 18;
    private static final int MAIN_INPUTS_W = (MAIN_INPUTS_PANEL_W - 12) / 2;
    private static final int MAIN_INPUTS_H = 56;
    private static final int MAIN_INPUTS_ROW_H = 14;
    private static final int MAIN_INPUTS_VISIBLE_ROWS = 4;
    private static final int MAIN_BOUND_INPUTS_VISIBLE_ROWS = 4;

    private static final int LIST_X = 0;
    private static final int LIST_Y = -40;
    private static final int LIST_ROW_X = 12;
    private static final int LIST_ROW_Y = 44;
    private static final int LIST_ROW_SPACING = 28;
    private static final int LIST_VISIBLE_ROWS = 4;

    private static final int MODIFY_PANEL_X = 11;
    private static final int MODIFY_PANEL_Y = -56;
    private static final int MODIFY_TAB_Y = 24;
    private static final int MODIFY_ROW1_Y = 52;
    private static final int MODIFY_ROW_STEP = 16;
    private static final int MODIFY_LEFT_X = 14;
    private static final int MODIFY_FULL_ROW_W = 268;
    private static final int DISCOVERY_LIST_Y = 89;
    private static final int DISCOVERY_LIST_TOP_OFFSET = 22;
    private static final int DISCOVERY_OUTPUT_VISIBLE_ROWS = 6;
    private static final int TAB_W = 72;
    private static final int BUTTON_H = 18;
    private static final int SMALL_BUTTON_H = 14;
    private static final int SMALL_BUTTON_W = 16;
    private static final int ROUTING_TARGETS_X = 8;
    private static final int ROUTING_TARGETS_Y = 106;
    private static final int ROUTING_TARGETS_W = 276;
    private static final int ROUTING_TARGETS_H = 66;
    private static final int ROUTING_TARGETS_VISIBLE_ROWS = 5;
    private static final int EDIT_BUTTON_W = 34;
    private static final int CLEAR_BUTTON_W = 34;
    private static final int INACTIVE_TAB_TEXT_COLOR = 0x6A6A6A;
    private static final Direction[] SIDE_OPTIONS = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final String[] PRESET_OPTIONS = {"none", "yaw", "roll", "pitch", "throttle"};

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current bound block entity
    private AnalogueContraptionControllerBlockEntity blockEntity;
    // Tracked channel configs
    private final EnumMap<AnalogueControlChannel, EditableChannelConfig> channelConfigs = new EnumMap<>(AnalogueControlChannel.class);

    // Tracked custom entry configs
    private final List<EditableCustomKeyEntry> customEntryConfigs = new ArrayList<>();
    // Initial custom entry ids
    private final Set<String> initialCustomEntryIds = new HashSet<>();
    // Active pressed keys
    private final Set<Integer> activePressedKeys = new HashSet<>();
    // Tracked key rows
    private final List<KeyRow> keyRows = new ArrayList<>();
    // Tracked assigned entries
    private final List<AssignedKeyEntry> assignedEntries = new ArrayList<>();
    // Tracked live discovery nodes
    private final List<ControllerDiscoveryNode> liveDiscoveryNodes = new ArrayList<>();
    // Tracked expanded main input node ids
    private final Set<String> expandedMainInputNodeIds = new HashSet<>();

    // Current key editor
    private ControllerKeyEditor keyEditor;
    // Modifier
    private final ControllerModifierOverlay modifier = new ControllerModifierOverlay();
    // Last observed linker stack
    private ItemStack lastObservedLinkerStack = ItemStack.EMPTY;
    // Pending linker sync tick count
    private int pendingLinkerSyncTicks = 0;
    // Last edited channel
    private AnalogueControlChannel lastEditedChannel = AnalogueControlChannel.PITCH_UP;
    // Last main binding key code
    private int lastMainBindingKeyCode = InputConstants.KEY_SPACE;
    // Current main inputs scroll
    private int mainInputsScroll;
    // Current bound inputs scroll
    private int boundInputsScroll;
    // Current view mode
    private ViewMode viewMode = ViewMode.MAIN;

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller config
    public AnalogueContraptionControllerConfigScreen(AnalogueContraptionControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.blockEntity = menu.contentHolder;
        setWindowSize(MAIN_W, MAIN_H);
        loadConfigs();
        buildRows();
        rebuildAssignedEntries();
        liveDiscoveryNodes.addAll(sortedDiscoveryNodes(initialDiscoveryNodes()));

        for (CustomKeyEntry entry : menu.getInitialCustomEntries()) {
            EditableCustomKeyEntry ec = new EditableCustomKeyEntry();
            ec.id = entry.id();
            ec.keyCode = entry.keyCode;
            ec.stepDownKeyCode = entry.stepDownKeyCode;
            ec.label = entry.label;
            ec.mode = entry.mode;
            ec.riseRate = entry.riseRate;
            ec.fallRate = entry.fallRate;
            ec.stepAmount = entry.stepAmount;
            ec.stepDownAmount = entry.stepDownAmount;
            ec.deadzone = entry.deadzone;
            ec.smoothing = entry.smoothing;
            ec.localOutputSide = entry.localOutputSide;
            ec.first = entry.first;
            ec.second = entry.second;
            ec.inputFirst = entry.inputFirst;
            ec.inputSecond = entry.inputSecond;
            ec.directTarget = entry.directTarget;
            ec.inputTarget = entry.inputTarget;
            ec.bindingPreset = entry.bindingPreset;
            customEntryConfigs.add(ec);
            if (ec.id != null && !ec.id.isBlank()) {
                initialCustomEntryIds.add(ec.id);
            }
        }
        lastObservedLinkerStack = copySingle(menu.getCurrentLinkerStack());
        initKeyEditorSafely();
    }

    // Initialize the key editor safely
    private void initKeyEditorSafely() {
        try {
            keyEditor = new ControllerKeyEditor();
        } catch (Throwable ignored) {
            keyEditor = null;
            viewMode = ViewMode.MAIN;
        }
    }

    // Initialize the analogue contraption controller config
    @Override
    protected void init() {
        super.init();
        updateScalableGuiBounds();
        ensureBlockEntityLoaded();
        updateSlotVisibility();
        requestDiscoveryRefresh();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the container
    @Override
    protected void containerTick() {
        super.containerTick();
        ensureBlockEntityLoaded();
        detectLinkerSlotChanges();
        applyPendingLinkerSync();
        if (modifier.modifying && modifier.tab == ModifierTab.ROUTING) {
            if (modifier.editingCustomEntryId != null) {
                modifier.syncCustomEntryFromMenu();
            } else if (modifier.selectedChannel != null) {
                syncChannelFromMenu(modifier.selectedChannel);
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the close event
    @Override
    public void onClose() {
        if (modifier.modifying && modifier.selectedChannel != null) {
            syncChannelFromMenu(modifier.selectedChannel);
        }
        BlockPos controllerPos = getControllerPos();
        UUID controllerSubLevelId = getControllerSubLevelId();
        if (controllerPos != null) {
            for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
                EditableChannelConfig config = channelConfigs.get(channel);
                if (config == null || config.keyCode < 0 || !activePressedKeys.contains(config.keyCode)) {
                    continue;
                }
                PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, controllerSubLevelId, channel.id(), false));
            }
            for (EditableCustomKeyEntry entry : customEntryConfigs) {
                if (entry == null || entry.id == null || entry.id.isBlank()) {
                    continue;
                }
                if (entry.keyCode >= 0 && activePressedKeys.contains(entry.keyCode)) {
                    PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, controllerSubLevelId, entry.id, false));
                }
                if (entry.stepDownKeyCode >= 0 && activePressedKeys.contains(entry.stepDownKeyCode)) {
                    PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, controllerSubLevelId, entry.id + STEP_DOWN_KEY_SUFFIX, false));
                }
            }
        }
        activePressedKeys.clear();
        flushCustomEntriesOnClose();
        sendUpdates();
        super.onClose();
    }

    // Flush the custom entries on close
    private void flushCustomEntriesOnClose() {
        Set<String> currentIds = new HashSet<>();
        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry == null || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            currentIds.add(entry.id);
            if (!initialCustomEntryIds.contains(entry.id)) {
                sendCustomEntryAdd(entry);
            }
            sendCustomEntryUpdate(entry);
        }

        for (String existingId : initialCustomEntryIds) {
            if (!currentIds.contains(existingId)) {
                sendCustomEntryRemove(existingId);
            }
        }

        initialCustomEntryIds.clear();
        initialCustomEntryIds.addAll(currentIds);
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
        if (viewMode == ViewMode.MAIN) {
            renderMainScreen(guiGraphics, mouseX, mouseY);
        } else {
            if (keyEditor == null) {
                viewMode = ViewMode.MAIN;
                renderMainScreen(guiGraphics, mouseX, mouseY);
            } else {
                keyEditor.renderBackground(guiGraphics, mouseX, mouseY);
            }
        }

        if (modifier.modifying) {
            modifier.renderBackground(guiGraphics, mouseX, mouseY);
        }
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the foreground
    @Override
    protected void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }

    // Draw the analogue contraption controller config
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        updateScalableGuiBounds();
        if (modifier.modifying) {
            int screenW = minecraft != null ? minecraft.getWindow().getGuiScaledWidth() : width;
            int screenH = minecraft != null ? minecraft.getWindow().getGuiScaledHeight() : height;
            guiGraphics.fillGradient(0, 0, screenW, screenH, 1, -1072689136, -804253680);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    // Get the extra areas
    @Override
    public List<Rect2i> getExtraAreas() {
        return scalableGui.extraAreas();
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    // Draw the slot
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        scalableGui.pushFromOrigin(guiGraphics, leftPos, topPos);
        try {
            if (!ContraptionNetworkLinkerSlotRenderer.renderControllerSlot(guiGraphics, slot)) {
                super.renderSlot(guiGraphics, slot);
            }
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the slot highlight
    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        scalableGui.pushFromOrigin(guiGraphics, leftPos, topPos);
        try {
            super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Check if this is hovering
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return super.isHovering(x, y, width, height, scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        updateScalableGuiBounds();
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn != 0) {
            return super.mouseClicked(screenMouseX, screenMouseY, btn);
        }

        if (modifier.modifying) {
            return modifier.handleClick(mouseX, mouseY) || super.mouseClicked(screenMouseX, screenMouseY, btn);
        }

        boolean handled = switch (viewMode) {
            case MAIN -> clickMain(mouseX, mouseY);
            case KEY_EDITOR -> keyEditor != null && keyEditor.handleClick(mouseX, mouseY);
        };
        return handled || super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        updateScalableGuiBounds();
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (modifier.modifying && modifier.tab == ModifierTab.DISCOVERY
                && modifier.shiftDiscovery(mouseX, mouseY, scrollY > 0.0 ? -1 : 1)) {
            return true;
        }
        if (modifier.modifying && modifier.tab == ModifierTab.ROUTING
                && modifier.shiftRoutingTargets(mouseX, mouseY, scrollY > 0.0 ? -1 : 1)) {
            return true;
        }
        if (viewMode == ViewMode.MAIN) {
            int inputsX = leftPos + MAIN_INPUTS_X;
            int boundX = inputsX + MAIN_INPUTS_W + 4;
            int inputsY = topPos + MAIN_INPUTS_Y;
            if (inside(mouseX, mouseY, inputsX, inputsY, MAIN_INPUTS_W, MAIN_INPUTS_H)) {
                List<MainInputRow> opts = getMainInputRows();
                int maxScroll = maxMainInputsScroll(opts);
                mainInputsScroll = Mth.clamp(mainInputsScroll + (scrollY > 0.0 ? -1 : 1), 0, maxScroll);
                return true;
            }
            if (inside(mouseX, mouseY, boundX, inputsY, MAIN_INPUTS_W, MAIN_INPUTS_H)) {
                List<EditableCustomKeyEntry> boundOptions = getBoundMainInputEntries();
                int maxScroll = maxBoundInputsScroll(boundOptions);
                boundInputsScroll = Mth.clamp(boundInputsScroll + (scrollY > 0.0 ? -1 : 1), 0, maxScroll);
                return true;
            }
        }
        if (viewMode == ViewMode.KEY_EDITOR) {
            if (keyEditor == null) {
                viewMode = ViewMode.MAIN;
                return false;
            }
            keyEditor.shiftEntries(scrollY > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(screenMouseX, screenMouseY, scrollX, scrollY);
    }

    // Update the scalable gui bounds
    private void updateScalableGuiBounds() {
        Bounds bounds = new Bounds(leftPos, topPos, imageWidth, imageHeight);
        if (viewMode == ViewMode.KEY_EDITOR) {
            bounds.include(leftPos + LIST_X, topPos + LIST_Y, KEY_MENU_W, KEY_MENU_H);
        }
        if (viewMode == ViewMode.KEY_EDITOR || modifier.modifying) {
            bounds.include(leftPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_X - 8,
                    topPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_Y - 18,
                    CTScalableGui.PLAYER_INVENTORY_WIDTH,
                    CTScalableGui.PLAYER_INVENTORY_HEIGHT);
        }
        if (modifier.modifying) {
            int panelX = leftPos + MODIFY_PANEL_X;
            int panelY = topPos + MODIFY_PANEL_Y;
            bounds.include(panelX, panelY, MODIFY_PANEL_W, MODIFY_PANEL_H);
            if (modifier.tab == ModifierTab.DISCOVERY) {
                int toggleW = 12;
                int sidebarW = 92;
                int sidebarH = 108;
                int sidebarY = panelY + DISCOVERY_LIST_Y - 18;
                int toggleX = panelX - toggleW - 2;
                bounds.include(toggleX, sidebarY, toggleW, SMALL_BUTTON_H);
                if (!modifier.discoveryFiltersCollapsed) {
                    bounds.include(panelX - sidebarW - 2, sidebarY, sidebarW, sidebarH);
                }
            }
        }
        scalableGui.update(bounds.left, bounds.top, bounds.width(), bounds.height(), width, height);
    }

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifier.modifying && modifier.bindingPromptActive) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                modifier.bindingPromptActive = false;
                modifier.capturingStepDownKey = false;
                return true;
            }
            EditableCustomKeyEntry editingCustomEntry = findEditingCustomEntry();
            if (keyCode == InputConstants.KEY_BACKSPACE || keyCode == InputConstants.KEY_DELETE) {
                if (modifier.capturingStepDownKey && editingCustomEntry != null) {
                    editingCustomEntry.stepDownKeyCode = -1;
                    sendCustomEntryUpdate(editingCustomEntry);
                    propagateConfigToInputSiblings(editingCustomEntry);
                } else {
                    modifier.editingKeyCode = -1;
                }
            } else {
                if (modifier.capturingStepDownKey && editingCustomEntry != null) {
                    editingCustomEntry.stepDownKeyCode = normalizeKeyCode(keyCode);
                    sendCustomEntryUpdate(editingCustomEntry);
                    propagateConfigToInputSiblings(editingCustomEntry);
                } else {
                    modifier.editingKeyCode = normalizeKeyCode(keyCode);
                }
            }
            modifier.bindingPromptActive = false;
            modifier.capturingStepDownKey = false;
            return true;
        }

        int normalizedKeyCode = normalizeKeyCode(keyCode);
        Set<AnalogueControlChannel> matchingChannels = new HashSet<>();
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(channel);
            if (config == null || config.keyCode != normalizedKeyCode) {
                continue;
            }
            matchingChannels.add(channel);
        }

        List<CustomKeyMatch> matchingCustomEntries = findCustomEntriesByKey(normalizedKeyCode);

        if (matchingChannels.isEmpty() && matchingCustomEntries.isEmpty()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        BlockPos controllerPos = getControllerPos();
        if (activePressedKeys.add(normalizedKeyCode) && controllerPos != null) {
            for (AnalogueControlChannel channel : matchingChannels) {
                PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, getControllerSubLevelId(), channel.id(), true));
            }
            for (CustomKeyMatch match : matchingCustomEntries) {
                EditableCustomKeyEntry entry = match.entry();
                if (entry != null && entry.id != null && !entry.id.isBlank()) {
                    String channelId = match.stepDown() ? entry.id + STEP_DOWN_KEY_SUFFIX : entry.id;
                    PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, getControllerSubLevelId(), channelId, true));
                }
            }
        }
        return true;
    }

    // Handle key released
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        int normalizedKeyCode = normalizeKeyCode(keyCode);
        if (!activePressedKeys.remove(normalizedKeyCode)) {
            return super.keyReleased(keyCode, scanCode, modifiers);
        }

        Set<AnalogueControlChannel> matchingChannels = new HashSet<>();
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(channel);
            if (config == null || config.keyCode != normalizedKeyCode) {
                continue;
            }
            matchingChannels.add(channel);
        }

        List<CustomKeyMatch> matchingCustomEntries = findCustomEntriesByKey(normalizedKeyCode);

        if (matchingChannels.isEmpty() && matchingCustomEntries.isEmpty()) {
            return super.keyReleased(keyCode, scanCode, modifiers);
        }

        BlockPos controllerPos = getControllerPos();
        if (controllerPos != null) {
            for (AnalogueControlChannel channel : matchingChannels) {
                PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, getControllerSubLevelId(), channel.id(), false));
            }
            for (CustomKeyMatch match : matchingCustomEntries) {
                EditableCustomKeyEntry entry = match.entry();
                if (entry != null && entry.id != null && !entry.id.isBlank()) {
                    String channelId = match.stepDown() ? entry.id + STEP_DOWN_KEY_SUFFIX : entry.id;
                    PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(controllerPos, getControllerSubLevelId(), channelId, false));
                }
            }
        }
        return true;
    }

    // Draw the main screen
    private void renderMainScreen(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // -----------------------------------------------------SCREEN FRAME-----------------------------------------------------
        int x = leftPos;
        int y = topPos;
        List<Component> hoveredInputTooltip = null;
        guiGraphics.blit(TYPEWRITER_TEX, x + KEYBOARD_X, y, 0, 0, KEYBOARD_W, MAIN_KEYS_BG_H, 256, 256);

        int inputsPanelX = x + MAIN_INPUTS_PANEL_X;
        int inputsPanelY = y + MAIN_INPUTS_PANEL_Y;
        CTCreateScreenHelper.renderPanel(guiGraphics, inputsPanelX, inputsPanelY, MAIN_INPUTS_PANEL_W, MAIN_INPUTS_PANEL_H);

        // ------------------------------------INVENTORY AND LINKER------------------------------------
        renderPlayerInventory(guiGraphics,
            leftPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_X - 8,
            topPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_Y - 18);
        int linkerSlotX = leftPos + AnalogueContraptionControllerMenu.LINKER_SLOT_X;
        int linkerSlotY = topPos + AnalogueContraptionControllerMenu.LINKER_SLOT_Y;
        CTCreateScreenHelper.renderItemSlot(guiGraphics, linkerSlotX - 1, linkerSlotY - 1);
        Component linkerLabel = Component.literal("Linker");
        guiGraphics.drawString(font, linkerLabel,
                linkerSlotX + 8 - font.width(linkerLabel) / 2,
                linkerSlotY - 10,
                CTCreateScreenHelper.LABEL_COLOR, false);

        guiGraphics.drawCenteredString(font, title, x + MAIN_W / 2, y + 4, CTCreateScreenHelper.BANNER_TITLE_COLOR);

        // -----------------------------------------------------KEYBOARD-----------------------------------------------------
        for (int rowIndex = 0; rowIndex < keyRows.size(); rowIndex++) {
            keyRows.get(rowIndex).render(guiGraphics, x + KEYBOARD_X + MAIN_KEYS_X, y + MAIN_KEYS_Y + rowIndex * MAIN_ROW_STEP, mouseX, mouseY);
        }

        int inputsX = x + MAIN_INPUTS_X;
        int inputsY = y + MAIN_INPUTS_Y;
        int inputsButtonW = MAIN_INPUTS_W - SMALL_BUTTON_W - 2;
        int inputRenameW = 24;
        int inputBindW = Math.max(36, inputsButtonW - inputRenameW - 2);
        int boundX = inputsX + MAIN_INPUTS_W + 4;
        int boundButtonW = MAIN_INPUTS_W - SMALL_BUTTON_W - 2;
        int boundDeleteW = 24;
        int boundRenameW = 24;
        int boundEntryW = boundButtonW - boundDeleteW - boundRenameW - 4;
        guiGraphics.drawString(font,
            Component.literal("Inputs"),
            inputsX, inputsPanelY + 6, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        guiGraphics.drawString(font,
            Component.literal("Bound Inputs"),
            boundX, inputsPanelY + 6, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);

        CTCreateScreenHelper.renderInset(guiGraphics, inputsX, inputsY, inputsButtonW, MAIN_INPUTS_H,
                inside(mouseX, mouseY, inputsX, inputsY, inputsButtonW, MAIN_INPUTS_H), false);
        CTCreateScreenHelper.renderInset(guiGraphics, boundX, inputsY, boundButtonW, MAIN_INPUTS_H,
            inside(mouseX, mouseY, boundX, inputsY, boundButtonW, MAIN_INPUTS_H), false);

        // -----------------------------------------------------INPUT LISTS-----------------------------------------------------
        List<MainInputRow> opts = getMainInputRows();
        List<EditableCustomKeyEntry> boundOptions = getBoundMainInputEntries();
        mainInputsScroll = Mth.clamp(mainInputsScroll, 0, maxMainInputsScroll(opts));
        boundInputsScroll = Mth.clamp(boundInputsScroll, 0, maxBoundInputsScroll(boundOptions));

        if (opts.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("No detected inputs"),
                    inputsX + 4, inputsY + 4, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        } else {
            for (int row = 0; row < MAIN_INPUTS_VISIBLE_ROWS; row++) {
                int idx = mainInputsScroll + row;
                if (idx >= opts.size()) {
                    break;
                }
                MainInputRow option = opts.get(idx);
                int by = inputsY + row * MAIN_INPUTS_ROW_H;
                int nestedInset = option.child() ? 8 : 0;
                int rowX = inputsX + nestedInset;
                int rowW = inputsButtonW - nestedInset;
                int renameIconX = rowX + rowW - 17;
                String fullLabel = option.group()
                    ? option.node().label()
                    : option.option().label();
                String clippedLabel = trimToWidth(fullLabel, option.group() ? rowW - 10 : rowW - 28);
                if (option.group()) {
                    String prefix = option.expanded() ? "v " : "> ";
                    renderSmallButton(guiGraphics, rowX, by, rowW,
                        Component.literal(trimToWidth(prefix + clippedLabel, rowW - 8)),
                        mouseX, mouseY, true);
                } else {
                    renderSmallButton(guiGraphics, rowX, by, rowW,
                        Component.literal(clippedLabel),
                        mouseX, mouseY, true);
                        renderOverlayIcon(guiGraphics, renameIconX, by - 1,
                            AllIcons.I_CONFIG_OPEN, mouseX, mouseY);
                }
                if (hoveredInputTooltip == null
                        && inside(mouseX, mouseY, rowX, by, rowW, SMALL_BUTTON_H)) {
                    hoveredInputTooltip = discoveryNodeTooltip(option.node(), Component.literal(fullLabel), null);
                }
            }
        }

        if (boundOptions.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("No bound inputs"),
                    boundX + 4, inputsY + 4, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        } else {
            for (int row = 0; row < MAIN_BOUND_INPUTS_VISIBLE_ROWS; row++) {
                int idx = boundInputsScroll + row;
                if (idx >= boundOptions.size()) {
                break;
                }
                EditableCustomKeyEntry entry = boundOptions.get(idx);
                String label = boundInputSummary(entry);
                int by = inputsY + row * MAIN_INPUTS_ROW_H;
                int rowW = boundButtonW;
                int deleteIconX = boundX + rowW - 17;
                int renameIconX = deleteIconX - 16;
                String clippedLabel = trimToWidth(label, rowW - 40);
                renderSmallButton(guiGraphics, boundX, by, rowW,
                    Component.literal(clippedLabel),
                    mouseX, mouseY, true);
                renderOverlayIcon(guiGraphics, renameIconX, by - 1,
                    AllIcons.I_CONFIG_OPEN, mouseX, mouseY);
                renderOverlayIcon(guiGraphics, deleteIconX, by - 1,
                    AllIcons.I_TRASH, mouseX, mouseY);
                if (hoveredInputTooltip == null
                        && inside(mouseX, mouseY, boundX, by, rowW, SMALL_BUTTON_H)) {
                    List<String> routes = relatedRouteRows(entry);
                    if (font.width(label) > rowW - 40 || routes.size() > 1) {
                        hoveredInputTooltip = new ArrayList<>();
                        if (routes.isEmpty()) {
                            hoveredInputTooltip.add(Component.literal(label));
                        } else {
                            for (String route : routes) {
                                hoveredInputTooltip.add(Component.literal(route));
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------SCROLL CONTROLS------------------------------------
        boolean canScrollUp = mainInputsScroll > 0;
        boolean canScrollDown = mainInputsScroll < maxMainInputsScroll(opts);
        renderSmallButton(guiGraphics, inputsX + inputsButtonW + 2, inputsY, SMALL_BUTTON_W,
                Component.literal("^"), mouseX, mouseY, canScrollUp);
        renderSmallButton(guiGraphics, inputsX + inputsButtonW + 2, inputsY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W,
                Component.literal("v"), mouseX, mouseY, canScrollDown);
        boolean canBoundScrollUp = boundInputsScroll > 0;
        boolean canBoundScrollDown = boundInputsScroll < maxBoundInputsScroll(boundOptions);
        renderSmallButton(guiGraphics, boundX + boundButtonW + 2, inputsY, SMALL_BUTTON_W,
                Component.literal("^"), mouseX, mouseY, canBoundScrollUp);
        renderSmallButton(guiGraphics, boundX + boundButtonW + 2, inputsY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W,
                Component.literal("v"), mouseX, mouseY, canBoundScrollDown);

        renderTextButton(guiGraphics, x + KEYBOARD_X + 8, y + FOOTER_Y, RESET_BUTTON_W, Component.literal("Reset"), mouseX, mouseY);
        renderTextButton(guiGraphics, footerDoneX(), y + FOOTER_Y, FOOTER_RIGHT_BUTTON_W, Component.literal("Done"), mouseX, mouseY);

        if (!modifier.modifying && hoveredInputTooltip != null) {
            guiGraphics.renderTooltip(font, hoveredInputTooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    // Handle the main click
    private boolean clickMain(double mouseX, double mouseY) {
        int x = leftPos;
        int y = topPos;

        // -----------------------------------------------------KEYBOARD-----------------------------------------------------
        for (int rowIndex = 0; rowIndex < keyRows.size(); rowIndex++) {
            MainKey hit = keyRows.get(rowIndex).findHit(mouseX, mouseY, x + KEYBOARD_X + MAIN_KEYS_X, y + MAIN_KEYS_Y + rowIndex * MAIN_ROW_STEP);
            if (hit != null) {
                openMainBindingForKey(hit.keyCode);
                return true;
            }
        }

        // -----------------------------------------------------INPUT LISTS-----------------------------------------------------
        int inputsX = x + MAIN_INPUTS_X;
        int inputsY = y + MAIN_INPUTS_Y;
        int inputsButtonW = MAIN_INPUTS_W - SMALL_BUTTON_W - 2;
        int inputBindW = Math.max(36, inputsButtonW);
        int boundX = inputsX + MAIN_INPUTS_W + 4;
        int boundButtonW = MAIN_INPUTS_W - SMALL_BUTTON_W - 2;
        List<MainInputRow> opts = getMainInputRows();
        List<EditableCustomKeyEntry> boundOptions = getBoundMainInputEntries();
        mainInputsScroll = Mth.clamp(mainInputsScroll, 0, maxMainInputsScroll(opts));
        boundInputsScroll = Mth.clamp(boundInputsScroll, 0, maxBoundInputsScroll(boundOptions));

        if (inside(mouseX, mouseY, inputsX + inputsButtonW + 2, inputsY, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
            if (mainInputsScroll > 0) {
                mainInputsScroll--;
            }
            return true;
        }
        if (inside(mouseX, mouseY, inputsX + inputsButtonW + 2, inputsY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
            int maxScroll = maxMainInputsScroll(opts);
            if (mainInputsScroll < maxScroll) {
                mainInputsScroll++;
            }
            return true;
        }
        if (inside(mouseX, mouseY, boundX + boundButtonW + 2, inputsY, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
            if (boundInputsScroll > 0) {
                boundInputsScroll--;
            }
            return true;
        }
        if (inside(mouseX, mouseY, boundX + boundButtonW + 2, inputsY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
            int maxScroll = maxBoundInputsScroll(boundOptions);
            if (boundInputsScroll < maxScroll) {
                boundInputsScroll++;
            }
            return true;
        }
        // ------------------------------------AVAILABLE INPUTS------------------------------------
        for (int row = 0; row < MAIN_INPUTS_VISIBLE_ROWS; row++) {
            int idx = mainInputsScroll + row;
            if (idx >= opts.size()) {
                break;
            }
            MainInputRow option = opts.get(idx);
            int by = inputsY + row * MAIN_INPUTS_ROW_H;
            int nestedInset = option.child() ? 8 : 0;
            int rowX = inputsX + nestedInset;
            int rowW = inputsButtonW - nestedInset;
            int renameIconX = rowX + rowW - 17;
            if (inside(mouseX, mouseY, renameIconX, by - 1, 16, 16)) {
                if (!option.group()) {
                    renameMainInputOption(option.option());
                }
                return true;
            }
            if (inside(mouseX, mouseY, rowX, by, rowW, SMALL_BUTTON_H)) {
                if (option.group()) {
                    String nodeId = option.node().nodeId();
                    if (expandedMainInputNodeIds.contains(nodeId)) {
                        expandedMainInputNodeIds.remove(nodeId);
                    } else {
                        expandedMainInputNodeIds.add(nodeId);
                    }
                } else {
                    bindMainInputNode(option.option());
                }
                return true;
            }
        }

        // -----------------------------------------------------BOUND INPUTS-----------------------------------------------------
        for (int row = 0; row < MAIN_BOUND_INPUTS_VISIBLE_ROWS; row++) {
            int idx = boundInputsScroll + row;
            if (idx >= boundOptions.size()) {
                break;
            }
            EditableCustomKeyEntry entry = boundOptions.get(idx);
            int by = inputsY + row * MAIN_INPUTS_ROW_H;
            int rowW = boundButtonW;
            int deleteIconX = boundX + rowW - 17;
            int renameIconX = deleteIconX - 16;
            if (inside(mouseX, mouseY, deleteIconX, by - 1, 16, 16)) {
                removeBoundInputGroup(entry);
                boundInputsScroll = Mth.clamp(boundInputsScroll, 0, maxBoundInputsScroll(getBoundMainInputEntries()));
                return true;
            }
            if (inside(mouseX, mouseY, renameIconX, by - 1, 16, 16)) {
                renameBoundInputEntry(entry);
                return true;
            }
            if (inside(mouseX, mouseY, boundX, by, rowW, SMALL_BUTTON_H)) {
                modifier.startCustom(entry, ViewMode.MAIN);
                return true;
            }
        }

        // -----------------------------------------------------FOOTER-----------------------------------------------------
        if (inside(mouseX, mouseY, x + KEYBOARD_X + 8, y + FOOTER_Y, RESET_BUTTON_W, BUTTON_H)) {
            clearAllKeyAssignments();
            return true;
        }
        if (inside(mouseX, mouseY, footerDoneX(), y + FOOTER_Y, FOOTER_RIGHT_BUTTON_W, BUTTON_H)) {
            onClose();
            return true;
        }
        return false;
    }

    // Open the main binding for key
    private void openMainBindingForKey(int keyCode) {
        lastMainBindingKeyCode = keyCode;
        AnalogueControlChannel channel = findChannelByKey(keyCode);
        EditableCustomKeyEntry customEntry = findCustomEntryByKey(keyCode);
        if (channel != null) {
            lastEditedChannel = channel;
            modifier.start(channel, keyCode, ViewMode.MAIN);
        } else if (customEntry != null) {
            modifier.startCustom(customEntry, ViewMode.MAIN);
        } else {
            EditableCustomKeyEntry created = new EditableCustomKeyEntry();
            created.id = UUID.randomUUID().toString();
            created.keyCode = keyCode;
            created.label = keyLabel(keyCode);
            customEntryConfigs.add(created);
            sendCustomEntryAdd(created);
            modifier.startCustom(created, ViewMode.MAIN);
        }
    }

    // Bind the main input node
    private void bindMainInputNode(MainInputOption option) {
        if (option == null || option.node() == null) return;
        ControllerDirectTargetReference inputTarget = directTargetForMainInputOption(option);
        if (inputTarget == null) {
            return;
        }

        EditableCustomKeyEntry entry = new EditableCustomKeyEntry();
        entry.id = UUID.randomUUID().toString();
        entry.keyCode = -1;
        entry.label = option.label();
        customEntryConfigs.add(entry);
        sendCustomEntryAdd(entry);
        entry.inputTarget = inputTarget;
        entry.label = option.label();
        sendCustomEntryUpdate(entry);

        modifier.startCustomForInputBinding(entry, ViewMode.MAIN);
    }

    // Rename the main input option
    private void renameMainInputOption(MainInputOption option) {
        if (option == null || option.node() == null || minecraft == null) {
            return;
        }
        minecraft.setScreen(new ControllerTextInputScreen(this,
                Component.literal("Rename Input"),
                Component.literal("Name"),
                option.label(),
                val -> {
                    if (val == null || val.isBlank()) {
                        return;
                    }
                    updateDiscoveryNodeMetadata(option.node(), val.trim(), option.node().groupId());
                }));
    }

    // Rename the bound input entry
    private void renameBoundInputEntry(EditableCustomKeyEntry entry) {
        if (entry == null || minecraft == null) {
            return;
        }
        String initial = entry.label == null ? "Custom" : entry.label;
        minecraft.setScreen(new ControllerTextInputScreen(this,
                Component.literal("Rename Binding"),
                Component.literal("Name"),
                initial,
                val -> {
                    if (val == null || val.isBlank()) {
                        return;
                    }
                    entry.label = val.trim();
                    sendCustomEntryUpdate(entry);
                }));
    }

    // Load the configs
    private void loadConfigs() {
        for (AnalogueControlChannel definition : AnalogueControlChannel.values()) {
            EditableChannelConfig config = new EditableChannelConfig();
            config.channel = definition;
            config.mode = definition.defaultMode();
            AnalogueContraptionControllerMenu.InitialChannelState initialState = menu.getInitialChannelState(definition.id());
            if (initialState != null) {
                config.mode = initialState.mode();
                config.riseRate = initialState.riseRate();
                config.fallRate = initialState.fallRate();
                config.stepAmount = initialState.stepAmount();
                config.deadzone = initialState.deadzone();
                config.smoothing = initialState.smoothing();
                config.localOutputSide = initialState.localOutputSide();
                config.first = initialState.first();
                config.second = initialState.second();
                config.inputFirst = initialState.inputFirst();
                config.inputSecond = initialState.inputSecond();
                config.keyCode = initialState.keyCode();
                config.directTarget = initialState.directTarget();
                config.inputTarget = initialState.inputTarget();
                config.bindingMode = initialState.bindingMode();
                config.bindingPreset = initialState.bindingPreset();
            }
            channelConfigs.put(definition, config);
        }
    }

    // Ensure the block entity loaded
    private void ensureBlockEntityLoaded() {
        if (blockEntity == null) {
            blockEntity = resolveBlockEntity();
        }
    }

    // Resolve the block entity
    private AnalogueContraptionControllerBlockEntity resolveBlockEntity() {
        if (minecraft == null) {
            return null;
        }
        ClientLevel level = minecraft.level;
        BlockPos controllerPos = menu.getContentPos();
        if (level == null || controllerPos == null) {
            return null;
        }
        return SimulatedHelper.findBlockEntity(level, menu.getContentSubLevelId(), controllerPos, AnalogueContraptionControllerBlockEntity.class);
    }

    // Get the controller pos
    private BlockPos getControllerPos() {
        if (blockEntity != null) {
            return blockEntity.getBlockPos();
        }
        return menu.getContentPos();
    }

    // Get the controller sublevel id
    private UUID getControllerSubLevelId() {
        if (blockEntity != null) {
            return SimulatedHelper.getContainingSubLevelId(blockEntity);
        }
        return menu.getContentSubLevelId();
    }

    // Build the rows
    private void buildRows() {
        // -----------------------------------------------------ROW SETUP-----------------------------------------------------
        keyRows.clear();
        KeyRow first = new KeyRow();
        KeyRow second = new KeyRow();
        KeyRow third = new KeyRow();
        KeyRow fourth = new KeyRow();
        KeyRow fifth = new KeyRow();

        // -----------------------------------------------------NUMBER ROW-----------------------------------------------------
        first.add(14, 96, "`");
        first.add(14, 49, "1");
        first.add(14, 50, "2");
        first.add(14, 51, "3");
        first.add(14, 52, "4");
        first.add(14, 53, "5");
        first.add(14, 54, "6");
        first.add(14, 55, "7");
        first.add(14, 56, "8");
        first.add(14, 57, "9");
        first.add(14, 48, "0");
        first.add(14, 45, "-");
        first.add(14, 61, "=");
        first.add(26, 259, "Bksp");
        first.add(14, 261, "Ins");

        // ------------------------------------TOP LETTER ROW------------------------------------
        second.add(20, 258, "Tab");
        second.add(14, 81, "Q");
        second.add(14, 87, "W");
        second.add(14, 69, "E");
        second.add(14, 82, "R");
        second.add(14, 84, "T");
        second.add(14, 89, "Y");
        second.add(14, 85, "U");
        second.add(14, 73, "I");
        second.add(14, 79, "O");
        second.add(14, 80, "P");
        second.add(14, 91, "[");
        second.add(14, 93, "]");
        second.add(20, 92, "\\");
        second.add(14, 266, "PgU");

        // ------------------------------------HOME LETTER ROW------------------------------------
        third.add(26, 280, "Caps");
        third.add(14, 65, "A");
        third.add(14, 83, "S");
        third.add(14, 68, "D");
        third.add(14, 70, "F");
        third.add(14, 71, "G");
        third.add(14, 72, "H");
        third.add(14, 74, "J");
        third.add(14, 75, "K");
        third.add(14, 76, "L");
        third.add(14, 59, ";");
        third.add(14, 39, "'");
        third.add(28, 257, "Enter");
        third.add(14, 267, "PgD");

        // ------------------------------------BOTTOM LETTER ROW------------------------------------
        fourth.add(32, 340, "Shift");
        fourth.add(14, 90, "Z");
        fourth.add(14, 88, "X");
        fourth.add(14, 67, "C");
        fourth.add(14, 86, "V");
        fourth.add(14, 66, "B");
        fourth.add(14, 78, "N");
        fourth.add(14, 77, "M");
        fourth.add(14, 44, ",");
        fourth.add(14, 46, ".");
        fourth.add(14, 47, "/");
        fourth.add(22, 344, "Shift");
        fourth.add(14, 265, "Up");
        fourth.add(14, 269, "End");

        fifth.add(18, 341, "Ctrl");
        fifth.add(14, 343, "Win");
        fifth.add(14, 342, "Alt");
        fifth.add(88, 32, "Space");
        fifth.add(14, 346, "Alt");
        fifth.add(14, 348, "Menu");
        fifth.add(18, 345, "Ctrl");
        fifth.add(14, 263, "Left");
        fifth.add(14, 264, "Down");
        fifth.add(14, 262, "Right");

        keyRows.add(first);
        keyRows.add(second);
        keyRows.add(third);
        keyRows.add(fourth);
        keyRows.add(fifth);
    }

    // Rebuild the assigned entries
    private void rebuildAssignedEntries() {
        assignedEntries.clear();
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(channel);
            if (config == null || config.keyCode < 0) {
                continue;
            }
            assignedEntries.add(new AssignedKeyEntry(config.keyCode, channel));
        }
        assignedEntries.sort(Comparator.comparingInt(AssignedKeyEntry::keyCode));
    }

    // Find the assigned entry
    private AssignedKeyEntry findAssignedEntry(int keyCode) {
        for (AssignedKeyEntry entry : assignedEntries) {
            if (entry.keyCode == keyCode) {
                return entry;
            }
        }
        return null;
    }

    // Find the channel by key
    private AnalogueControlChannel findChannelByKey(int keyCode) {
        AssignedKeyEntry entry = findAssignedEntry(keyCode);
        return entry == null ? null : entry.channel;
    }

    // Find the custom entry by key
    private EditableCustomKeyEntry findCustomEntryByKey(int keyCode) {
        for (EditableCustomKeyEntry ec : customEntryConfigs) {
            if (ec.keyCode == keyCode || ec.stepDownKeyCode == keyCode) {
                return ec;
            }
        }
        return null;
    }

    // Find the custom entries by key
    private List<CustomKeyMatch> findCustomEntriesByKey(int keyCode) {
        List<CustomKeyMatch> matches = new ArrayList<>();
        for (EditableCustomKeyEntry ec : customEntryConfigs) {
            if (ec == null) {
                continue;
            }
            if (ec.keyCode == keyCode) {
                matches.add(new CustomKeyMatch(ec, false));
            }
            if (ec.stepDownKeyCode == keyCode) {
                matches.add(new CustomKeyMatch(ec, true));
            }
        }
        return matches;
    }

    // Find the custom entry by input target
    private EditableCustomKeyEntry findCustomEntryByInputTarget(ControllerDirectTargetReference inputTarget) {
        if (inputTarget == null) {
            return null;
        }
        String targetId = inputTarget.targetId();
        for (EditableCustomKeyEntry ec : customEntryConfigs) {
            if (ec.inputTarget == null) {
                continue;
            }
            if (Objects.equals(ec.inputTarget.targetId(), targetId)) {
                return ec;
            }
        }
        return null;
    }

    // Clear all key assignments
    private void clearAllKeyAssignments() {
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(channel);
            if (config != null) {

                config.mode = channel.defaultMode();
                config.riseRate = 0.08D;
                config.fallRate = 0.08D;
                config.stepAmount = 0.1D;
                config.deadzone = 0.0D;
                config.smoothing = 0.2D;
                config.localOutputSide = null;
                config.first = ItemStack.EMPTY;
                config.second = ItemStack.EMPTY;
                config.keyCode = -1;
                config.directTarget = null;
                config.inputTarget = null;
                config.bindingMode = "standard";
            }
        }

        for (EditableCustomKeyEntry ec : new ArrayList<>(customEntryConfigs)) {
            sendCustomEntryRemove(ec.id);
        }
        customEntryConfigs.clear();
        rebuildAssignedEntries();
        loadChannelToMenu(lastEditedChannel);
        sendUpdates();
    }

    // Commit the assignment
    private void commitAssignment(AnalogueControlChannel selectedChannel, int keyCode) {
        if (selectedChannel == null) {
            return;
        }
        EditableChannelConfig selectedConfig = channelConfigs.get(selectedChannel);
        if (selectedConfig == null) {
            return;
        }

        if (!selectedConfig.isCustomBinding()) {
            for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
                if (channel == selectedChannel) {
                    continue;
                }
                EditableChannelConfig config = channelConfigs.get(channel);
                if (config == null || config.isCustomBinding()) {
                    continue;
                }
                if (config.keyCode == keyCode) {
                    config.keyCode = -1;
                    sendSingleUpdate(channel);
                }
            }
        }

        selectedConfig.keyCode = keyCode;
        lastEditedChannel = selectedChannel;
        rebuildAssignedEntries();
        sendSingleUpdate(selectedChannel);
    }

    // Clear the assignment
    private void clearAssignmentFor(AnalogueControlChannel channel) {
        if (channel == null) {
            return;
        }
        EditableChannelConfig config = channelConfigs.get(channel);
        if (config != null) {
            config.keyCode = -1;
            config.first = ItemStack.EMPTY;
            config.second = ItemStack.EMPTY;
            config.directTarget = null;
            config.inputTarget = null;
            config.bindingPreset = "none";
            config.bindingMode = "standard";
        }
        rebuildAssignedEntries();
        sendSingleUpdate(channel);
    }

    // Remove the custom entry
    private void removeCustomEntry(EditableCustomKeyEntry entry) {
        if (entry == null) {
            return;
        }

        customEntryConfigs.removeIf(ec -> Objects.equals(ec.id, entry.id));
        sendCustomEntryRemove(entry.id);
        rebuildAssignedEntries();
    }

    // Remove the bound input group
    private void removeBoundInputGroup(EditableCustomKeyEntry src) {
        if (src == null) {
            return;
        }
        for (EditableCustomKeyEntry entry : new ArrayList<>(customEntryConfigs)) {
            if (shareBoundInputSource(src, entry)) {
                removeCustomEntry(entry);
            }
        }
    }

    // Load the channel to menu
    private void loadChannelToMenu(AnalogueControlChannel channel) {
        if (channel == null) {
            return;
        }
        EditableChannelConfig config = channelConfigs.get(channel);
        if (config == null) {
            return;
        }
        menu.setCurrentChannelId(channel.id());
        menu.ghostInventory.setStackInSlot(0, copySingle(config.first));
        menu.ghostInventory.setStackInSlot(1, copySingle(config.second));

        menu.ghostInventory.setStackInSlot(2, copySingle(config.inputFirst));
        menu.ghostInventory.setStackInSlot(3, copySingle(config.inputSecond));
        PacketDistributor.sendToServer(new AnalogueContraptionControllerGhostSlotsPayload(channel.id(),
                copySingle(config.first), copySingle(config.second),
                copySingle(config.inputFirst), copySingle(config.inputSecond)));
    }

    // Sync the channel from menu
    private void syncChannelFromMenu(AnalogueControlChannel channel) {
        if (channel == null) {
            return;
        }
        EditableChannelConfig config = channelConfigs.get(channel);
        if (config == null) {
            return;
        }
        ItemStack first = copySingle(menu.ghostInventory.getStackInSlot(0));
        ItemStack second = copySingle(menu.ghostInventory.getStackInSlot(1));

        ItemStack inputFirst = copySingle(menu.ghostInventory.getStackInSlot(2));
        ItemStack inputSecond = copySingle(menu.ghostInventory.getStackInSlot(3));
        boolean changed = !ItemStack.isSameItemSameComponents(config.first, first)
                || !ItemStack.isSameItemSameComponents(config.second, second)
                || !ItemStack.isSameItemSameComponents(config.inputFirst, inputFirst)
                || !ItemStack.isSameItemSameComponents(config.inputSecond, inputSecond);
        if (changed) {
            config.first = first;
            config.second = second;
            config.inputFirst = inputFirst;
            config.inputSecond = inputSecond;
            sendSingleUpdate(channel);
        }
    }

    // Send the custom entry update
    private void sendCustomEntryUpdate(EditableCustomKeyEntry ec) {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null || ec == null) return;
        PacketDistributor.sendToServer(new AnalogueContraptionControllerCustomKeyPayload(
                MenuConfigTarget.of(controllerPos, getControllerSubLevelId()), "update",
            ec.id, ec.keyCode, ec.stepDownKeyCode, ec.label == null ? "Custom" : ec.label,
                ec.mode == null ? "ramp" : ec.mode.name().toLowerCase(Locale.ROOT),
            (float) ec.riseRate, (float) ec.fallRate, (float) ec.stepAmount, (float) ec.stepDownAmount,
                (float) ec.deadzone, (float) ec.smoothing,
                ec.localOutputSide == null ? "" : ec.localOutputSide.getSerializedName(),
                ec.first, ec.second,
                ec.inputFirst, ec.inputSecond,
            ec.directTarget == null ? new CompoundTag() : ec.directTarget.toTag(),
            ec.inputTarget == null ? new CompoundTag() : ec.inputTarget.toTag(),
            ec.bindingPreset == null || ec.bindingPreset.isBlank() ? "none" : ec.bindingPreset));
    }

    // Send the custom entry add
    private void sendCustomEntryAdd(EditableCustomKeyEntry ec) {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null || ec == null) return;
        PacketDistributor.sendToServer(new AnalogueContraptionControllerCustomKeyPayload(
                MenuConfigTarget.of(controllerPos, getControllerSubLevelId()), "add",
                ec.id, ec.keyCode, ec.stepDownKeyCode, ec.label == null ? "Custom" : ec.label,
            "ramp", 0.08f, 0.08f, 0.1f, 0.1f, 0f, 0.2f, "", ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, new CompoundTag(), new CompoundTag(), "none"));
    }

    // Send the custom entry remove
    private void sendCustomEntryRemove(String entryId) {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null) return;
        PacketDistributor.sendToServer(new AnalogueContraptionControllerCustomKeyPayload(
                MenuConfigTarget.of(controllerPos, getControllerSubLevelId()), "remove",
            entryId, -1, -1, "", "ramp", 0, 0, 0, 0, 0, 0, "", ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, new CompoundTag(), new CompoundTag(), "none"));
    }

    // Send the single update
    private void sendSingleUpdate(AnalogueControlChannel channel) {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null || channel == null) {
            return;
        }
        EditableChannelConfig config = channelConfigs.get(channel);
        if (config == null) {
            return;
        }
        PacketDistributor.sendToServer(new AnalogueContraptionControllerConfigPayload(
            MenuConfigTarget.of(controllerPos, getControllerSubLevelId()),
                channel.id(),
                config.mode.name().toLowerCase(Locale.ROOT),
                (float) config.riseRate,
                (float) config.fallRate,
                (float) config.stepAmount,
                (float) config.deadzone,
                (float) config.smoothing,
                config.localOutputSide == null ? "" : config.localOutputSide.getSerializedName(),
                config.first,
                config.second,
                config.inputFirst,
                config.inputSecond,
                config.keyCode,
                config.directTarget == null ? new CompoundTag() : config.directTarget.toTag(),
                config.inputTarget == null ? new CompoundTag() : config.inputTarget.toTag(),
                config.bindingMode,
                config.bindingPreset));
    }

    // Send the updates
    private void sendUpdates() {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null) {
            return;
        }
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(channel);
            if (config == null) {
                continue;
            }
            PacketDistributor.sendToServer(new AnalogueContraptionControllerConfigPayload(
                    MenuConfigTarget.of(controllerPos, getControllerSubLevelId()),
                    channel.id(),
                    config.mode.name().toLowerCase(Locale.ROOT),
                    (float) config.riseRate,
                    (float) config.fallRate,
                    (float) config.stepAmount,
                    (float) config.deadzone,
                    (float) config.smoothing,
                    config.localOutputSide == null ? "" : config.localOutputSide.getSerializedName(),
                    config.first,
                    config.second,
                    config.inputFirst,
                    config.inputSecond,
                    config.keyCode,
                    config.directTarget == null ? new CompoundTag() : config.directTarget.toTag(),
                    config.inputTarget == null ? new CompoundTag() : config.inputTarget.toTag(),
                    config.bindingMode,
                    config.bindingPreset));
        }
    }

    // Refresh the discovery
    private void refreshDiscovery() {
        modifier.discoveryNodes.clear();
        modifier.latestDiscoveredNodeIds.clear();
        modifier.discoveryScroll = 0;
        modifier.discoveryNodes.addAll(sortedDiscoveryNodes(initialDiscoveryNodes()));
        syncKnownDiscoveryGroups(modifier.discoveryNodes);
        liveDiscoveryNodes.clear();
        liveDiscoveryNodes.addAll(modifier.discoveryNodes);
        requestDiscoveryRefresh();
    }

    // Get the initial discovery nodes
    private List<ControllerDiscoveryNode> initialDiscoveryNodes() {
        return mergeDiscoveryNodes(menu.getInitialStoredTargets(), menu.getInitialLinkerTargets());
    }

    // Request the discovery refresh
    private void requestDiscoveryRefresh() {
        BlockPos controllerPos = getControllerPos();
        if (controllerPos == null) {
            return;
        }
        PacketDistributor.sendToServer(new AnalogueContraptionControllerDiscoveryRequestPayload(controllerPos, getControllerSubLevelId()));
    }

    // Detect the linker slot changes
    private void detectLinkerSlotChanges() {
        ItemStack currentLinker = copySingle(menu.getCurrentLinkerStack());
        if (ItemStack.isSameItemSameComponents(currentLinker, lastObservedLinkerStack)) {
            return;
        }
        lastObservedLinkerStack = currentLinker;
        refreshBindingsFromLinkerStack(currentLinker);
        pendingLinkerSyncTicks = 20;
        refreshDiscovery();
    }

    // Apply the pending linker sync
    private void applyPendingLinkerSync() {
        if (pendingLinkerSyncTicks <= 0) {
            return;
        }
        ensureBlockEntityLoaded();
        if (blockEntity == null) {
            pendingLinkerSyncTicks--;
            return;
        }
        ItemStack blockEntityLinker = copySingle(blockEntity.getStoredLinker());
        ItemStack menuLinker = copySingle(menu.getCurrentLinkerStack());
        if (!ItemStack.isSameItemSameComponents(blockEntityLinker, menuLinker)) {
            pendingLinkerSyncTicks--;
            return;
        }
        refreshBindingsFromBlockEntity();
        pendingLinkerSyncTicks = 0;
    }

    // Refresh the bindings from linker stack
    private void refreshBindingsFromLinkerStack(ItemStack linkerStack) {
        Map<String, ContraptionNetworkLinkerData.ChannelBind> linkerBinds = linkerStack == null || linkerStack.isEmpty()
                ? Map.of()
                : ContraptionNetworkLinkerData.readClientChannelBindings(linkerStack);

        for (AnalogueControlChannel definition : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(definition);
            if (config == null) {
                config = new EditableChannelConfig();
                config.channel = definition;
                config.mode = definition.defaultMode();
                channelConfigs.put(definition, config);
            }

            ContraptionNetworkLinkerData.ChannelBind bind = linkerBinds.get(definition.id());
            if (bind != null) {
                config.directTarget = bind.directTarget();
                config.inputTarget = bind.inputTarget();
                continue;
            }

            if (ContraptionNetworkLinkerData.isLinkerFaceTarget(config.directTarget)) {
                config.directTarget = null;
            }
            if (ContraptionNetworkLinkerData.isLinkerFaceTarget(config.inputTarget)) {
                config.inputTarget = null;
            }
        }

        customEntryConfigs.removeIf(this::isLinkerBoundCustomEntry);
        if (linkerStack != null && !linkerStack.isEmpty() && minecraft != null && minecraft.level != null) {
            for (CompoundTag tag : ContraptionNetworkLinkerData.readClientCustomEntryBindings(linkerStack)) {
                CustomKeyEntry entry = CustomKeyEntry.fromTag(tag, minecraft.level.registryAccess());
                if (entry == null || entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                EditableCustomKeyEntry editable = editableCustomEntry(entry);
                customEntryConfigs.removeIf(existing -> Objects.equals(existing.id, editable.id));
                customEntryConfigs.add(editable);
                initialCustomEntryIds.add(editable.id);
            }
        }

        rebuildAssignedEntries();
    }

    // Check if this is a linker bound custom entry
    private boolean isLinkerBoundCustomEntry(EditableCustomKeyEntry entry) {
        return entry != null
                && (ContraptionNetworkLinkerData.isLinkerFaceTarget(entry.directTarget)
                || ContraptionNetworkLinkerData.isLinkerFaceTarget(entry.inputTarget));
    }

    // Get the editable custom entry
    private static EditableCustomKeyEntry editableCustomEntry(CustomKeyEntry entry) {
        EditableCustomKeyEntry ec = new EditableCustomKeyEntry();
        ec.id = entry.id();
        ec.keyCode = entry.keyCode;
        ec.stepDownKeyCode = entry.stepDownKeyCode;
        ec.label = entry.label;
        ec.mode = entry.mode;
        ec.riseRate = entry.riseRate;
        ec.fallRate = entry.fallRate;
        ec.stepAmount = entry.stepAmount;
        ec.stepDownAmount = entry.stepDownAmount;
        ec.deadzone = entry.deadzone;
        ec.smoothing = entry.smoothing;
        ec.localOutputSide = entry.localOutputSide;
        ec.first = copySingle(entry.first);
        ec.second = copySingle(entry.second);
        ec.inputFirst = copySingle(entry.inputFirst);
        ec.inputSecond = copySingle(entry.inputSecond);
        ec.directTarget = entry.directTarget;
        ec.inputTarget = entry.inputTarget;
        ec.bindingPreset = entry.bindingPreset;
        return ec;
    }

    // Refresh the bindings from block entity
    private void refreshBindingsFromBlockEntity() {
        ensureBlockEntityLoaded();
        if (blockEntity == null) {
            return;
        }

        for (AnalogueControlChannel definition : AnalogueControlChannel.values()) {
            EditableChannelConfig config = channelConfigs.get(definition);
            if (config == null) {
                config = new EditableChannelConfig();
                config.channel = definition;
                config.mode = definition.defaultMode();
                channelConfigs.put(definition, config);
            }

            AnalogueChannel liveChannel = blockEntity.getChannel(definition.id());
            if (liveChannel != null) {
                config.mode = liveChannel.getMode();
                config.riseRate = liveChannel.getRiseRate();
                config.fallRate = liveChannel.getFallRate();
                config.stepAmount = liveChannel.getStepAmount();
                config.deadzone = liveChannel.getDeadzone();
                config.smoothing = liveChannel.getSmoothing();
            }
            config.localOutputSide = blockEntity.getLocalOutputSide(definition.id());
            config.first = copySingle(blockEntity.getFrequencyFirst(definition.id()));
            config.second = copySingle(blockEntity.getFrequencySecond(definition.id()));
            config.inputFirst = copySingle(blockEntity.getInputFrequencyFirst(definition.id()));
            config.inputSecond = copySingle(blockEntity.getInputFrequencySecond(definition.id()));
            config.keyCode = blockEntity.getKeyBinding(definition.id());
            config.directTarget = blockEntity.getDirectTarget(definition.id());
            config.inputTarget = blockEntity.getInputTarget(definition.id());
            config.bindingMode = blockEntity.getBindingMode(definition.id());
            config.bindingPreset = blockEntity.getBindingPreset(definition.id());
        }

        customEntryConfigs.clear();
        for (CustomKeyEntry entry : blockEntity.getCustomKeyEntries()) {
            EditableCustomKeyEntry ec = new EditableCustomKeyEntry();
            ec.id = entry.id();
            ec.keyCode = entry.keyCode;
            ec.stepDownKeyCode = entry.stepDownKeyCode;
            ec.label = entry.label;
            ec.mode = entry.mode;
            ec.riseRate = entry.riseRate;
            ec.fallRate = entry.fallRate;
            ec.stepAmount = entry.stepAmount;
            ec.stepDownAmount = entry.stepDownAmount;
            ec.deadzone = entry.deadzone;
            ec.smoothing = entry.smoothing;
            ec.localOutputSide = entry.localOutputSide;
            ec.first = copySingle(entry.first);
            ec.second = copySingle(entry.second);
            ec.inputFirst = copySingle(entry.inputFirst);
            ec.inputSecond = copySingle(entry.inputSecond);
            ec.directTarget = entry.directTarget;
            ec.inputTarget = entry.inputTarget;
            ec.bindingPreset = entry.bindingPreset;
            customEntryConfigs.add(ec);
        }
        rebuildAssignedEntries();
    }

    // Get the sorted discovery nodes
    private static List<ControllerDiscoveryNode> sortedDiscoveryNodes(List<ControllerDiscoveryNode> nodes) {
        return nodes.stream()
                .filter(node -> node != null && node.isValid())
                .sorted(Comparator.comparing((ControllerDiscoveryNode node) -> node.groupId() == null ? "" : node.groupId())
                        .thenComparing(ControllerDiscoveryNode::label))
                .toList();
    }

    // Apply the discovery results
    public static void applyDiscoveryResults(BlockPos controllerPos, UUID controllerSubLevelId, List<ControllerDiscoveryNode> nodes) {
        Screen current = Minecraft.getInstance().screen;
        if (!(current instanceof AnalogueContraptionControllerConfigScreen screen)) {
            return;
        }
        BlockPos openControllerPos = screen.getControllerPos();
        if (openControllerPos == null || !openControllerPos.equals(controllerPos)) {
            return;
        }
        UUID openControllerSubLevelId = screen.getControllerSubLevelId();
        if (!java.util.Objects.equals(openControllerSubLevelId, controllerSubLevelId)) {
            return;
        }
        screen.modifier.discoveryNodes.clear();
        screen.modifier.latestDiscoveredNodeIds.clear();
        for (ControllerDiscoveryNode node : nodes) {
            if (node != null && node.isValid()) {
                screen.modifier.latestDiscoveredNodeIds.add(node.nodeId());
            }
        }
        screen.modifier.discoveryNodes.addAll(sortedDiscoveryNodes(mergeDiscoveryNodes(screen.initialDiscoveryNodes(), nodes)));
        screen.modifier.discoveryScroll = 0;
        screen.syncKnownDiscoveryGroups(screen.modifier.discoveryNodes);
        screen.liveDiscoveryNodes.clear();
        screen.liveDiscoveryNodes.addAll(screen.modifier.discoveryNodes);
    }

    // Sync the known discovery groups
    private void syncKnownDiscoveryGroups(List<ControllerDiscoveryNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (ControllerDiscoveryNode node : nodes) {
            if (node == null || node.groupId() == null || node.groupId().isBlank()) {
                continue;
            }
            modifier.knownDiscoveryGroupIds.add(node.groupId());
        }
    }

    // Merge the discovery nodes
    private static List<ControllerDiscoveryNode> mergeDiscoveryNodes(List<ControllerDiscoveryNode> primary,
                                                                     List<ControllerDiscoveryNode> secondary) {
        Map<String, ControllerDiscoveryNode> merged = new LinkedHashMap<>();
        addDiscoveryNodes(merged, primary);
        addDiscoveryNodes(merged, secondary);
        return new ArrayList<>(merged.values());
    }

    // Add the discovery nodes
    private static void addDiscoveryNodes(Map<String, ControllerDiscoveryNode> merged, List<ControllerDiscoveryNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (ControllerDiscoveryNode node : nodes) {
            if (node != null && node.isValid()) {
                merged.putIfAbsent(node.nodeId(), node);
            }
        }
    }

    // Update the discovery node metadata
    private void updateDiscoveryNodeMetadata(ControllerDiscoveryNode src, String label, String groupId) {
        if (src == null || !src.isValid()) {
            return;
        }
        String nextLabel = label == null ? "" : label.trim();
        String nextGroup = groupId == null ? "" : groupId.trim();
        ControllerDiscoveryNode updated = new ControllerDiscoveryNode(
                src.nodeId(),
                src.kind(),
                nextGroup,
                src.blockId(),
                nextLabel.isBlank() ? src.label() : nextLabel,
                src.subLevelId(),
                src.blockPos());
        if (!nextGroup.isBlank()) {
            modifier.knownDiscoveryGroupIds.add(nextGroup);
        }

        for (int i = 0; i < liveDiscoveryNodes.size(); i++) {
            ControllerDiscoveryNode node = liveDiscoveryNodes.get(i);
            if (node != null && src.nodeId().equals(node.nodeId())) {
                liveDiscoveryNodes.set(i, updated);
            }
        }
        for (int i = 0; i < modifier.discoveryNodes.size(); i++) {
            ControllerDiscoveryNode node = modifier.discoveryNodes.get(i);
            if (node != null && src.nodeId().equals(node.nodeId())) {
                modifier.discoveryNodes.set(i, updated);
            }
        }

        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry == null || entry.inputTarget == null || entry.label == null || entry.label.isBlank()) {
                continue;
            }
            if (entry.inputTarget.targetId() != null && entry.inputTarget.targetId().startsWith(src.nodeId())) {
                entry.label = updated.label();
                sendCustomEntryUpdate(entry);
            }
        }

        BlockPos controllerPos = getControllerPos();
        if (controllerPos != null) {
            PacketDistributor.sendToServer(new AnalogueContraptionControllerDiscoveryNodePayload(
                    MenuConfigTarget.of(controllerPos, getControllerSubLevelId()),
                    updated.toTag()));
        }
    }

    // Update the slot visibility
    private void updateSlotVisibility() {
        menu.playerSlotsActive = true;
        menu.ghostSlotsActive = modifier.modifying && modifier.tab == ModifierTab.ROUTING;

        menu.linkerSlotActive = viewMode == ViewMode.MAIN && !modifier.modifying;
    }

    // Draw the text button
    private void renderTextButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, width, BUTTON_H, label,
                inside(mouseX, mouseY, x, y, width, BUTTON_H), true, false);
    }

    // Draw the small button
    private void renderSmallButton(GuiGraphics guiGraphics, int x, int y, int width, Component label, int mouseX, int mouseY, boolean active) {
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, width, SMALL_BUTTON_H, label,
                inside(mouseX, mouseY, x, y, width, SMALL_BUTTON_H), active, false);
    }

    // Draw the small icon button
    private void renderSmallIconButton(GuiGraphics guiGraphics, int x, int y, AllIcons icon, int mouseX, int mouseY, boolean active) {
        CTCreateScreenHelper.renderIconButton(guiGraphics, x, y, icon,
                inside(mouseX, mouseY, x, y, SMALL_BUTTON_H, SMALL_BUTTON_H), active, false);
    }

    // Draw the overlay icon
    private void renderOverlayIcon(GuiGraphics guiGraphics, int x, int y, AllIcons icon, int mouseX, int mouseY) {
        icon.render(guiGraphics, x, y);
        if (inside(mouseX, mouseY, x, y, 16, 16)) {
            guiGraphics.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
        }
    }

    // Draw the tab button
    private void renderTabButton(GuiGraphics guiGraphics, int x, int y, Component label, int mouseX, int mouseY, boolean active) {

        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, TAB_W, SMALL_BUTTON_H, label,
                inside(mouseX, mouseY, x, y, TAB_W, SMALL_BUTTON_H), active, false,
            0xF3EEE4, INACTIVE_TAB_TEXT_COLOR, false);
    }

    // Draw the action row
    private void renderActionRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y,
                                 int width, int mouseX, int mouseY, boolean promptActive) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        int valueX = x + 76;
        int setWidth = 30;
        int clearWidth = 24;
        int setX = x + width - setWidth;
        int clearX = setX - clearWidth - 4;
        int valueWidth = Math.max(36, clearX - valueX - 4);
        CTCreateScreenHelper.renderInset(guiGraphics, valueX, y, valueWidth, SMALL_BUTTON_H, false, false);
        guiGraphics.drawCenteredString(font, val, valueX + valueWidth / 2, y + 3, CTCreateScreenHelper.VALUE_COLOR);
        renderSmallButton(guiGraphics, clearX, y, clearWidth, Component.literal("Clr"), mouseX, mouseY, true);
        renderSmallButton(guiGraphics, setX, y, setWidth, promptActive ? Component.literal("Press") : Component.literal("Set"), mouseX, mouseY, true);
    }

    // Draw the read only action row
    private void renderReadOnlyActionRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y,
                                         int width) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        int valueX = x + 76;
        int valueWidth = Math.max(36, width - 76);
        CTCreateScreenHelper.renderInset(guiGraphics, valueX, y, valueWidth, SMALL_BUTTON_H, false, false);
        guiGraphics.drawCenteredString(font, val, valueX + valueWidth / 2, y + 3, CTCreateScreenHelper.VALUE_COLOR);
    }

    // Draw the cycle row
    private void renderCycleRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y, int width, int mouseX, int mouseY) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        renderSmallButton(guiGraphics, x + 54, y, SMALL_BUTTON_W, Component.literal("<"), mouseX, mouseY, true);
        int valueX = x + 74;
        int rightX = x + width - SMALL_BUTTON_W;
        int valueWidth = Math.max(28, rightX - valueX - 4);
        CTCreateScreenHelper.renderInset(guiGraphics, valueX, y, valueWidth, SMALL_BUTTON_H, false, false);
        guiGraphics.drawCenteredString(font, val, valueX + valueWidth / 2, y + 3, CTCreateScreenHelper.VALUE_COLOR);
        renderSmallButton(guiGraphics, x + width - SMALL_BUTTON_W, y, SMALL_BUTTON_W, Component.literal(">"), mouseX, mouseY, true);
    }

    // Draw the numeric row
    private void renderNumericRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y, int width, int mouseX, int mouseY) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        renderSmallButton(guiGraphics, x + 54, y, SMALL_BUTTON_W, Component.literal("-"), mouseX, mouseY, true);
        int valueX = x + 74;
        int rightX = x + width - SMALL_BUTTON_W;
        int valueWidth = Math.max(28, rightX - valueX - 4);
        CTCreateScreenHelper.renderInset(guiGraphics, valueX, y, valueWidth, SMALL_BUTTON_H, false, false);
        guiGraphics.drawCenteredString(font, val, valueX + valueWidth / 2, y + 3, CTCreateScreenHelper.VALUE_COLOR);
        renderSmallButton(guiGraphics, x + width - SMALL_BUTTON_W, y, SMALL_BUTTON_W, Component.literal("+"), mouseX, mouseY, true);
    }

    // Draw the preview slot
    private void renderPreviewSlot(GuiGraphics guiGraphics, ItemStack stack, int x, int y, boolean secondFrequency) {
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, x + 1, y + 1, false, secondFrequency);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
        }
    }

    // Draw the key
    private void renderKey(GuiGraphics guiGraphics, int x, int y, int width, boolean active, boolean hovered) {
        int startU = active ? KEY_START_U : KEY_INACTIVE_START_U;
        int midU = active ? KEY_MID_U : KEY_INACTIVE_MID_U;
        int endU = active ? KEY_END_U : KEY_INACTIVE_END_U;
        int renderY = y + (hovered ? 2 : 0);
        guiGraphics.blit(TYPEWRITER_TEX, x, renderY, startU, KEY_V, KEY_START_W, KEY_H, 256, 256);
        int middleWidth = Math.max(0, width - KEY_START_W - KEY_END_W);
        for (int offset = 0; offset < middleWidth; offset += KEY_MID_W) {
            int renderWidth = Math.min(KEY_MID_W, middleWidth - offset);
            guiGraphics.blit(TYPEWRITER_TEX, x + KEY_START_W + offset, renderY, midU, KEY_V, renderWidth, KEY_H, 256, 256);
        }
        guiGraphics.blit(TYPEWRITER_TEX, x + width - KEY_END_W, renderY, endU, KEY_V, KEY_END_W, KEY_H, 256, 256);
    }

    // Get the mode label
    private static Component modeLabel(AnalogueChannelMode mode) {
        return Component.literal(mode.name().toLowerCase(Locale.ROOT));
    }

    // Get the local side label
    private static Component localSideLabel(Direction dir) {
        return dir == null ? Component.literal("None") : Component.literal(dir.getSerializedName());
    }

    // Format the rate
    private static Component formatRate(double val) {
        return Component.literal(String.format(Locale.ROOT, "%.2f", val));
    }

    // Format the percent
    private static Component formatPercent(double val) {
        return Component.literal(Math.round(val * 100.0D) + "%");
    }

    // Get the discovery node tooltip
    private List<Component> discoveryNodeTooltip(ControllerDiscoveryNode node, Component title,
                                                  @Nullable Component detail) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(title);
        if (node != null) {
            if (Screen.hasShiftDown()) {
                Component summary = Component.translatable(node.kind().summaryTranslationKey());
                if (!summary.getString().isBlank()) {
                    tooltip.add(summary.copy().withStyle(ChatFormatting.GRAY));
                }
            } else {
                tooltip.add(Component.translatable(NODE_SUMMARY_HELPER_KEY).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (detail != null && !detail.getString().isBlank()) {
            tooltip.add(detail);
        }
        return tooltip;
    }

    // Handle key binding label
    private static Component keyBindingLabel(int keyCode, boolean capturing) {
        if (capturing) {
            return Component.literal("Press");
        }
        if (keyCode < 0) {
            return Component.literal("None");
        }
        return Component.literal(keyLabel(keyCode));
    }

    // Get the custom key binding label
    private static String customKeyBindingLabel(EditableCustomKeyEntry entry) {
        if (entry == null) {
            return "None";
        }
        String up = entry.keyCode < 0 ? "None" : keyLabel(entry.keyCode);
        if (entry.stepDownKeyCode < 0) {
            return up;
        }
        return up + " / " + keyLabel(entry.stepDownKeyCode);
    }

    // Handle key label
    private static String keyLabel(int keyCode) {
        return InputConstants.getKey(keyCode, -1).getDisplayName().getString();
    }

    // Normalize the key code
    private static int normalizeKeyCode(int keyCode) {
        return keyCode < 0 ? -1 : keyCode;
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

    // Trim the width
    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Get the footer done x
    private int footerDoneX() {
        return leftPos + KEYBOARD_X + KEYBOARD_W - 8 - FOOTER_RIGHT_BUTTON_W;
    }

    // Handle the controller key editor
    private class ControllerKeyEditor {
        // Current scroll
        private int scroll;
        // Current quick bind channel
        private AnalogueControlChannel quickBindChannel = AnalogueControlChannel.PITCH_UP;

        // Get the total virtual row count
        private int totalVirtualRows() {
            return AnalogueControlChannel.values().length + 1 + customEntryConfigs.size() + 1;
        }

        // Draw the background
        void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y;
            CTCreateScreenHelper.renderPanel(guiGraphics, x, y, KEY_MENU_W, KEY_MENU_H);
            renderPlayerInventory(guiGraphics,
                    leftPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_X - 8,
                    topPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_Y - 18);
            guiGraphics.drawCenteredString(font,
                Component.translatable("createthrusters.analogue_controller.config.bindings_title"),
                x + KEY_MENU_W / 2, y + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);
            guiGraphics.drawString(font,
                Component.literal("Select a binding, then configure its key, routing, or direct target."),
                x + 12, y + 28, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);

            int nChannels = AnalogueControlChannel.values().length;
            for (int visible = 0; visible < LIST_VISIBLE_ROWS; visible++) {
                int idx = scroll + visible;
                if (idx >= totalVirtualRows()) break;
                int rowX = x + LIST_ROW_X;
                int rowY = y + LIST_ROW_Y + visible * LIST_ROW_SPACING;

                if (idx < nChannels) {

                    AnalogueControlChannel channel = AnalogueControlChannel.values()[idx];
                    EditableChannelConfig config = channelConfigs.get(channel);
                    boolean hovered = inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H);
                    CTCreateScreenHelper.renderInset(guiGraphics, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H, hovered, false);
                    guiGraphics.drawString(font, trimToWidth(Component.translatable(channel.translationKey()).getString(), 120), rowX + 8, rowY + 4, 16777215, false);
                    guiGraphics.drawString(font, trimToWidth(config == null ? "Unbound" : keyBindingLabel(config.keyCode, false).getString(), 120), rowX + 8, rowY + 14, CTCreateScreenHelper.VALUE_COLOR, false);
                    guiGraphics.drawString(font, trimToWidth(entrySummary(channel), 52), rowX + 128, rowY + 14, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                    renderSmallButton(guiGraphics, rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + EDIT_BUTTON_W + 8), rowY + 5, EDIT_BUTTON_W, Component.literal("Edit"), mouseX, mouseY, true);
                    renderSmallButton(guiGraphics, rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + 4), rowY + 5, CLEAR_BUTTON_W, Component.literal("Clear"), mouseX, mouseY, true);
                } else if (idx == nChannels) {

                    guiGraphics.drawString(font, Component.literal("— Custom Keys"), rowX + 8, rowY + 8, CTCreateScreenHelper.LABEL_COLOR, false);
                } else if (idx <= nChannels + customEntryConfigs.size()) {

                    int entryIdx = idx - nChannels - 1;
                    EditableCustomKeyEntry ec = customEntryConfigs.get(entryIdx);
                    boolean hovered = inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H);
                    CTCreateScreenHelper.renderInset(guiGraphics, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H, hovered, false);
                    guiGraphics.drawString(font, trimToWidth(ec.label == null || ec.label.isBlank() ? "Custom " + (entryIdx + 1) : ec.label, 120), rowX + 8, rowY + 4, 16777215, false);
                    guiGraphics.drawString(font, trimToWidth(customKeyBindingLabel(ec), 120), rowX + 8, rowY + 14, CTCreateScreenHelper.VALUE_COLOR, false);
                    renderSmallIconButton(guiGraphics, rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + EDIT_BUTTON_W + 8), rowY + 5,
                        AllIcons.I_CONFIG_OPEN, mouseX, mouseY, true);
                    renderSmallIconButton(guiGraphics, rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + 4), rowY + 5,
                        AllIcons.I_CONFIG_RESET, mouseX, mouseY, true);
                } else {

                    boolean hovered = inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H);
                    CTCreateScreenHelper.renderInset(guiGraphics, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H, hovered, false);
                    guiGraphics.drawCenteredString(font, Component.literal("+ Add Custom Key"), rowX + KEY_ENTRY_W / 2, rowY + 8, CTCreateScreenHelper.LABEL_COLOR);
                }
            }

            renderTextButton(guiGraphics, x + 8, y + KEY_MENU_H - 24, RESET_BUTTON_W, Component.literal("Reset"), mouseX, mouseY);
            renderTextButton(guiGraphics, x + KEY_MENU_W - 8 - FOOTER_RIGHT_BUTTON_W, y + KEY_MENU_H - 24, FOOTER_RIGHT_BUTTON_W, Component.literal("Back"), mouseX, mouseY);

            int linksY = y + KEY_MENU_H - 48;
            guiGraphics.drawString(font, Component.literal("Links (TX)"), x + 12, linksY, CTCreateScreenHelper.LABEL_COLOR, false);
            List<ControllerDiscoveryNode> transmitLinks = getTransmitLinkNodesDeduped();
            for (int i = 0; i < Math.min(2, transmitLinks.size()); i++) {
                ControllerDiscoveryNode node = transmitLinks.get(i);
                String label = redstoneLinkDisplayLabel(node);
                int bx = x + 56 + i * 92;
                renderSmallButton(guiGraphics, bx, linksY - 2, 88, Component.literal(trimToWidth(label, 74)), mouseX, mouseY, true);
            }

            guiGraphics.drawString(font,
                    Component.literal("Bind target: " + Component.translatable(quickBindChannel.translationKey()).getString()),
                    x + 12, y + KEY_MENU_H - 40, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        }

        // Draw the foreground
        void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        }

        // Handle the controller editor click
        boolean handleClick(double mouseX, double mouseY) {
            int x = leftPos + LIST_X;
            int y = topPos + LIST_Y;
            int nChannels = AnalogueControlChannel.values().length;
            for (int visible = 0; visible < LIST_VISIBLE_ROWS; visible++) {
                int idx = scroll + visible;
                if (idx >= totalVirtualRows()) break;
                int rowX = x + LIST_ROW_X;
                int rowY = y + LIST_ROW_Y + visible * LIST_ROW_SPACING;
                int editX = rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + EDIT_BUTTON_W + 8);
                int clearX = rowX + KEY_ENTRY_W - (CLEAR_BUTTON_W + 4);

                if (idx < nChannels) {
                    AnalogueControlChannel channel = AnalogueControlChannel.values()[idx];
                    EditableChannelConfig config = channelConfigs.get(channel);
                    if (inside(mouseX, mouseY, editX, rowY + 5, EDIT_BUTTON_W, SMALL_BUTTON_H)) {
                        quickBindChannel = channel;
                        modifier.start(channel, config == null ? -1 : config.keyCode, ViewMode.KEY_EDITOR);
                        return true;
                    }
                    if (inside(mouseX, mouseY, clearX, rowY + 5, CLEAR_BUTTON_W, SMALL_BUTTON_H)) {
                        quickBindChannel = channel;
                        clearAssignmentFor(channel);
                        return true;
                    }
                    if (inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H)) {
                        quickBindChannel = channel;
                        modifier.start(channel, config == null ? -1 : config.keyCode, ViewMode.KEY_EDITOR);
                        return true;
                    }
                } else if (idx == nChannels) {

                } else if (idx <= nChannels + customEntryConfigs.size()) {
                    int entryIdx = idx - nChannels - 1;
                    EditableCustomKeyEntry ec = customEntryConfigs.get(entryIdx);
                    if (inside(mouseX, mouseY, editX, rowY + 5, EDIT_BUTTON_W, SMALL_BUTTON_H)
                            || inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H)) {
                        modifier.startCustom(ec, ViewMode.KEY_EDITOR);
                        return true;
                    }
                    if (inside(mouseX, mouseY, clearX, rowY + 5, CLEAR_BUTTON_W, SMALL_BUTTON_H)) {
                        removeCustomEntry(ec);
                        return true;
                    }
                } else {

                    if (inside(mouseX, mouseY, rowX, rowY, KEY_ENTRY_W, KEY_ENTRY_H)) {
                        EditableCustomKeyEntry ec = new EditableCustomKeyEntry();
                        ec.id = UUID.randomUUID().toString();
                        ec.label = "Custom " + (customEntryConfigs.size() + 1);
                        customEntryConfigs.add(ec);
                        sendCustomEntryAdd(ec);
                        modifier.startCustom(ec, ViewMode.KEY_EDITOR);
                        return true;
                    }
                }
            }

            int linksY = y + KEY_MENU_H - 48;
            List<ControllerDiscoveryNode> transmitLinks = getTransmitLinkNodesDeduped();
            for (int i = 0; i < Math.min(2, transmitLinks.size()); i++) {
                int bx = x + 56 + i * 92;
                if (inside(mouseX, mouseY, bx, linksY - 2, 88, SMALL_BUTTON_H)) {
                    bindTransmitLinkToQuickChannel(transmitLinks.get(i));
                    return true;
                }
            }

            if (inside(mouseX, mouseY, x + 8, y + KEY_MENU_H - 24, RESET_BUTTON_W, BUTTON_H)) {
                clearAllKeyAssignments();
                return true;
            }
            if (inside(mouseX, mouseY, x + KEY_MENU_W - 8 - FOOTER_RIGHT_BUTTON_W, y + KEY_MENU_H - 24, FOOTER_RIGHT_BUTTON_W, BUTTON_H)) {
                viewMode = ViewMode.MAIN;
                updateSlotVisibility();
                return true;
            }
            return false;
        }

        // Shift the entries
        void shiftEntries(int dir) {
            int maxScroll = Math.max(0, totalVirtualRows() - LIST_VISIBLE_ROWS);
            scroll = Mth.clamp(scroll + dir, 0, maxScroll);
        }

        // Bind the input node to quick channel
        private void bindInputNodeToQuickChannel(ControllerDiscoveryNode node) {
            EditableChannelConfig config = channelConfigs.get(quickBindChannel);
            if (config == null || node == null) {
                return;
            }
            config.directTarget = node.asDirectTargetReference();
            lastEditedChannel = quickBindChannel;
            sendSingleUpdate(quickBindChannel);
        }

        // Bind the transmit link to quick channel
        private void bindTransmitLinkToQuickChannel(ControllerDiscoveryNode node) {
            EditableChannelConfig config = channelConfigs.get(quickBindChannel);
            if (config == null || node == null) {
                return;
            }
            LinkFrequencyItems items = resolveRedstoneLinkFreqItems(node);
            if (items == null) {
                return;
            }
            config.first = items.first();
            config.second = items.second();
            lastEditedChannel = quickBindChannel;
            sendSingleUpdate(quickBindChannel);
        }
    }

    // Handle the controller modifier overlay
    private class ControllerModifierOverlay {
        // Tracked discovery nodes
        private final List<ControllerDiscoveryNode> discoveryNodes = new ArrayList<>();
        // Tracked expanded discovery group ids
        private final Set<String> expandedDiscoveryGroupIds = new HashSet<>();
        // Tracked expanded group tree ids
        private final Set<String> expandedGroupTreeIds = new HashSet<>();
        // Tracked known discovery group ids
        private final Set<String> knownDiscoveryGroupIds = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        // Tracked latest discovered node ids
        private final Set<String> latestDiscoveredNodeIds = new HashSet<>();
        // Tracks whether controller modifier overlay is modifying
        private boolean modifying;
        // Tracks whether binding prompt is active
        private boolean bindingPromptActive;
        // Tracks whether controller modifier overlay is capturing step down key
        private boolean capturingStepDownKey;
        // Tracks whether discovery filters collapsed is set
        private boolean discoveryFiltersCollapsed;
        // Current editing key code
        private int editingKeyCode = -1;
        // Current discovery scroll
        private int discoveryScroll;
        // Current discovery group scroll
        private int discoveryGroupScroll;
        // Current discovery filter
        private DiscoveryFilter discoveryFilter = DiscoveryFilter.ALL;
        // Selected channel
        private AnalogueControlChannel selectedChannel;
        // Original channel
        private AnalogueControlChannel originalChannel;
        // Selected discovery row
        private DiscoveryRow selectedDiscoveryRow;
        // Selected discovery row keys
        private final Set<String> selectedDiscoveryRowKeys = new java.util.LinkedHashSet<>();
        // Selected discovery group id
        private String selectedDiscoveryGroupId = "";
        // Selected group tree node id
        private String selectedGroupTreeNodeId = "";
        // Selected group tree node ids
        private final Set<String> selectedGroupTreeNodeIds = new java.util.LinkedHashSet<>();

        // Current editing custom entry id
        private String editingCustomEntryId = null;
        // Current routing targets scroll
        private int routingTargetsScroll;
        // Pending routing change entry id
        private String pendingRoutingChangeEntryId;
        // Current tab
        private ModifierTab tab = ModifierTab.CONFIG;
        // Current return view
        private ViewMode returnView = ViewMode.MAIN;

        // Start the controller modifier overlay
        void start(AnalogueControlChannel channel, int keyCode, ViewMode returnView) {
            this.originalChannel = channel;
            this.selectedChannel = channel != null ? channel : lastEditedChannel;
            this.editingKeyCode = keyCode;
            this.editingCustomEntryId = null;
            this.returnView = returnView;

            this.bindingPromptActive = false;
            this.capturingStepDownKey = false;
            this.tab = ModifierTab.CONFIG;
            this.modifying = true;
            this.discoveryScroll = 0;
            this.discoveryGroupScroll = 0;
            this.routingTargetsScroll = 0;
            this.selectedDiscoveryRow = null;
            this.selectedDiscoveryRowKeys.clear();
            this.selectedDiscoveryGroupId = "";
            this.selectedGroupTreeNodeIds.clear();
            this.pendingRoutingChangeEntryId = null;
            this.discoveryFilter = DiscoveryFilter.ALL;
            this.discoveryFiltersCollapsed = false;
            this.expandedDiscoveryGroupIds.clear();
            this.expandedGroupTreeIds.clear();
            this.knownDiscoveryGroupIds.clear();
            this.latestDiscoveredNodeIds.clear();
            refreshDiscovery();
            loadChannelToMenu(this.selectedChannel);
            updateSlotVisibility();
        }

        // Start the discovery
        void startDiscovery(AnalogueControlChannel channel, int keyCode, ViewMode returnView) {
            start(channel, keyCode, returnView);
            this.tab = ModifierTab.DISCOVERY;
            this.bindingPromptActive = false;
            refreshDiscovery();
        }

        // Start the custom
        void startCustom(EditableCustomKeyEntry ec, ViewMode returnView) {

            this.editingCustomEntryId = ec.id;
            this.selectedChannel = null;
            this.originalChannel = null;
            this.editingKeyCode = ec.keyCode;
            this.returnView = returnView;

            this.bindingPromptActive = false;
            this.capturingStepDownKey = false;
            this.tab = ModifierTab.CONFIG;
            this.modifying = true;
            this.discoveryScroll = 0;
            this.discoveryGroupScroll = 0;
            this.routingTargetsScroll = 0;
            this.selectedDiscoveryRow = null;
            this.selectedDiscoveryRowKeys.clear();
            this.selectedDiscoveryGroupId = "";
            this.selectedGroupTreeNodeIds.clear();
            this.pendingRoutingChangeEntryId = null;
            this.discoveryFilter = DiscoveryFilter.ALL;
            this.discoveryFiltersCollapsed = false;
            this.expandedDiscoveryGroupIds.clear();
            this.expandedGroupTreeIds.clear();
            this.knownDiscoveryGroupIds.clear();
            this.latestDiscoveredNodeIds.clear();
            refreshDiscovery();
            loadRoutingGhostSlots(ec);
            updateSlotVisibility();
        }

        // Start the custom for input binding
        void startCustomForInputBinding(EditableCustomKeyEntry ec, ViewMode returnView) {

            this.editingCustomEntryId = ec.id;
            this.selectedChannel = null;
            this.originalChannel = null;
            this.editingKeyCode = ec.keyCode;
            this.returnView = returnView;
            this.bindingPromptActive = false;
            this.capturingStepDownKey = false;
            this.tab = ModifierTab.ROUTING;
            this.modifying = true;
            this.discoveryScroll = 0;
            this.discoveryGroupScroll = 0;
            this.routingTargetsScroll = 0;
            this.selectedDiscoveryRow = null;
            this.selectedDiscoveryRowKeys.clear();
            this.selectedDiscoveryGroupId = "";
            this.selectedGroupTreeNodeIds.clear();
            this.pendingRoutingChangeEntryId = null;
            this.discoveryFilter = DiscoveryFilter.ALL;
            this.discoveryFiltersCollapsed = false;
            this.expandedDiscoveryGroupIds.clear();
            this.expandedGroupTreeIds.clear();
            this.knownDiscoveryGroupIds.clear();
            this.latestDiscoveredNodeIds.clear();
            refreshDiscovery();
            loadRoutingGhostSlots(ec);
            updateSlotVisibility();
        }

        // Sync the custom entry from menu
        void syncCustomEntryFromMenu() {
            if (editingCustomEntryId == null) return;
            EditableCustomKeyEntry ec = findEditingCustomEntry();
            if (ec == null) return;
            ec.first = copySingle(menu.ghostInventory.getStackInSlot(0));
            ec.second = copySingle(menu.ghostInventory.getStackInSlot(1));
            ec.inputFirst = copySingle(menu.ghostInventory.getStackInSlot(2));
            ec.inputSecond = copySingle(menu.ghostInventory.getStackInSlot(3));
        }

        // Load the routing ghost slots
        private void loadRoutingGhostSlots(EditableCustomKeyEntry ec) {
            if (ec == null) {
                return;
            }
            menu.ghostInventory.setStackInSlot(0, ec.first == null ? ItemStack.EMPTY : ec.first.copy());
            menu.ghostInventory.setStackInSlot(1, ec.second == null ? ItemStack.EMPTY : ec.second.copy());
            menu.ghostInventory.setStackInSlot(2, ec.inputFirst == null ? ItemStack.EMPTY : ec.inputFirst.copy());
            menu.ghostInventory.setStackInSlot(3, ec.inputSecond == null ? ItemStack.EMPTY : ec.inputSecond.copy());
        }

        // Shift the routing targets
        boolean shiftRoutingTargets(double mouseX, double mouseY, int dir) {
            int panelX = leftPos + MODIFY_PANEL_X;
            int panelY = topPos + MODIFY_PANEL_Y;
            int boxX = panelX + ROUTING_TARGETS_X;
            int boxY = panelY + ROUTING_TARGETS_Y;
            if (!inside(mouseX, mouseY, boxX, boxY, ROUTING_TARGETS_W, ROUTING_TARGETS_H)) {
                return false;
            }
            int maxScroll = Math.max(0, routingTargetRows().size() - ROUTING_TARGETS_VISIBLE_ROWS);
            routingTargetsScroll = Mth.clamp(routingTargetsScroll + dir, 0, maxScroll);
            return true;
        }

        // Disable the control
        void disable() {
            if (editingCustomEntryId != null) {
                syncCustomEntryFromMenu();
            } else if (selectedChannel != null) {
                syncChannelFromMenu(selectedChannel);
            }
            editingCustomEntryId = null;
            modifying = false;
            bindingPromptActive = false;
            capturingStepDownKey = false;
            discoveryScroll = 0;
            discoveryGroupScroll = 0;
            selectedDiscoveryRow = null;
            selectedDiscoveryRowKeys.clear();
            selectedDiscoveryGroupId = "";
            selectedGroupTreeNodeId = "";
            selectedGroupTreeNodeIds.clear();
            pendingRoutingChangeEntryId = null;
            latestDiscoveredNodeIds.clear();
            viewMode = returnView;
            updateSlotVisibility();
        }

        // Draw the background
        void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY) {

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 2.0F);

            int panelX = leftPos + MODIFY_PANEL_X;
            int panelY = topPos + MODIFY_PANEL_Y;

            if (tab == ModifierTab.DISCOVERY) {
                renderDiscoveryFilterSidebar(guiGraphics, mouseX, mouseY, panelX, panelY);
            }
            CTCreateScreenHelper.renderPanel(guiGraphics, panelX, panelY, MODIFY_PANEL_W, MODIFY_PANEL_H);
            renderPlayerInventory(guiGraphics,
                    leftPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_X - 8,
                    topPos + AnalogueContraptionControllerMenu.PLAYER_SLOTS_Y - 18);

            guiGraphics.drawCenteredString(font,
                    Component.literal("Configure Binding"),
                    panelX + MODIFY_PANEL_W / 2, panelY + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);
                EditableCustomKeyEntry editingCustomEntry = findEditingCustomEntry();
                Component bindingTitle = editingCustomEntry != null
                    ? Component.literal(trimToWidth(editingCustomEntry.label == null || editingCustomEntry.label.isBlank() ? "Custom" : editingCustomEntry.label, 130))
                    : Component.translatable(selectedChannel.translationKey());
                guiGraphics.drawCenteredString(font, bindingTitle, panelX + 8 + TAB_W / 2, panelY + 18,
                    CTCreateScreenHelper.LABEL_COLOR);

                renderTabButton(guiGraphics, panelX + 8, panelY + MODIFY_TAB_Y, Component.literal("Config"), mouseX, mouseY,
                    tab == ModifierTab.CONFIG);
                renderTabButton(guiGraphics, panelX + 86, panelY + MODIFY_TAB_Y, Component.literal("Routing"), mouseX, mouseY,
                    tab == ModifierTab.ROUTING);
                renderTabButton(guiGraphics, panelX + 164, panelY + MODIFY_TAB_Y, Component.literal("Discovery"), mouseX, mouseY,
                    tab == ModifierTab.DISCOVERY);

            if (tab == ModifierTab.CONFIG) {
                renderConfigTab(guiGraphics, mouseX, mouseY, panelX, panelY);
            } else if (tab == ModifierTab.ROUTING) {
                renderRoutingTab(guiGraphics, mouseX, mouseY, panelX, panelY);
            } else {
                renderDiscoveryTab(guiGraphics, mouseX, mouseY, panelX, panelY);
            }

            renderTextButton(guiGraphics, panelX + 8, panelY + MODIFY_PANEL_H - 24, 48, Component.literal("Clear"), mouseX, mouseY);
            renderTextButton(guiGraphics, panelX + MODIFY_PANEL_W - 104, panelY + MODIFY_PANEL_H - 24, 44, Component.literal("Back"), mouseX, mouseY);
            renderTextButton(guiGraphics, panelX + MODIFY_PANEL_W - 56, panelY + MODIFY_PANEL_H - 24, 48, Component.literal("Save"), mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        // Draw the foreground
        void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        }

        // Handle the controller editor click
        boolean handleClick(double mouseX, double mouseY) {
            int panelX = leftPos + MODIFY_PANEL_X;
            int panelY = topPos + MODIFY_PANEL_Y;

            if (tab == ModifierTab.DISCOVERY && clickDiscoveryFilter(mouseX, mouseY, panelX, panelY)) {
                return true;
            }

            if (inside(mouseX, mouseY, panelX + 8, panelY + MODIFY_TAB_Y, TAB_W, SMALL_BUTTON_H)) {
                if (tab == ModifierTab.ROUTING) {
                    if (editingCustomEntryId != null) {
                        syncCustomEntryFromMenu();
                    } else if (selectedChannel != null) {
                        syncChannelFromMenu(selectedChannel);
                    }
                }
                tab = ModifierTab.CONFIG;
                updateSlotVisibility();
                return true;
            }
            if (inside(mouseX, mouseY, panelX + 86, panelY + MODIFY_TAB_Y, TAB_W, SMALL_BUTTON_H)) {
                tab = ModifierTab.ROUTING;
                if (editingCustomEntryId != null) {
                    EditableCustomKeyEntry ec = findEditingCustomEntry();
                    if (ec != null) {
                        loadRoutingGhostSlots(ec);
                    }
                } else {
                    loadChannelToMenu(selectedChannel);
                }
                updateSlotVisibility();
                return true;
            }
            if (inside(mouseX, mouseY, panelX + 164, panelY + MODIFY_TAB_Y, TAB_W, SMALL_BUTTON_H)) {
                if (tab == ModifierTab.ROUTING) {
                    if (editingCustomEntryId != null) {
                        syncCustomEntryFromMenu();
                    } else if (selectedChannel != null) {
                        syncChannelFromMenu(selectedChannel);
                    }
                }
                tab = ModifierTab.DISCOVERY;
                updateSlotVisibility();
                return true;
            }

            if (inside(mouseX, mouseY, panelX + 8, panelY + MODIFY_PANEL_H - 24, 48, BUTTON_H)) {
                clearCurrentBindingAndClose();
                return true;
            }
            if (inside(mouseX, mouseY, panelX + MODIFY_PANEL_W - 104, panelY + MODIFY_PANEL_H - 24, 44, BUTTON_H)) {
                disable();
                return true;
            }
            if (inside(mouseX, mouseY, panelX + MODIFY_PANEL_W - 56, panelY + MODIFY_PANEL_H - 24, 48, BUTTON_H)) {
                finish();
                return true;
            }

            return switch (tab) {
                case CONFIG -> clickConfig(mouseX, mouseY, panelX, panelY);
                case ROUTING -> clickRouting(mouseX, mouseY, panelX, panelY);
                case DISCOVERY -> clickDiscovery(mouseX, mouseY, panelX, panelY);
            };
        }

        // Draw the discovery filter sidebar
        private void renderDiscoveryFilterSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY, int panelX, int panelY) {
            int toggleW = 12;
            int sidebarW = 92;
            int sidebarH = 108;
            int sidebarY = panelY + DISCOVERY_LIST_Y - 18;
            int toggleX = panelX - toggleW - 2;
            int sidebarX = panelX - sidebarW - 2;

            renderSmallButton(guiGraphics, toggleX, sidebarY, toggleW,
                    Component.literal(discoveryFiltersCollapsed ? ">" : "<"), mouseX, mouseY, true);
            if (discoveryFiltersCollapsed) {
                return;
            }

            CTCreateScreenHelper.renderPanel(guiGraphics, sidebarX, sidebarY, sidebarW, sidebarH);
            guiGraphics.drawCenteredString(font, Component.literal("Filters"),
                    sidebarX + sidebarW / 2, sidebarY + 7, CTCreateScreenHelper.BANNER_TITLE_COLOR);

            int rowY = sidebarY + 22;
            for (DiscoveryFilter filter : DiscoveryFilter.values()) {
                boolean selected = discoveryFilter == filter;
                renderSmallButton(guiGraphics, sidebarX + 6, rowY, sidebarW - 12,
                        Component.literal(discoveryFilterLabel(filter)), mouseX, mouseY, true);
                if (selected) {
                    guiGraphics.fill(sidebarX + 6, rowY, sidebarX + sidebarW - 6, rowY + SMALL_BUTTON_H, 0x2233CC88);
                }
                rowY += 16;
            }
        }

        // Handle the discovery filter click
        private boolean clickDiscoveryFilter(double mouseX, double mouseY, int panelX, int panelY) {
            int toggleW = 12;
            int sidebarW = 92;
            int sidebarY = panelY + DISCOVERY_LIST_Y - 18;
            int toggleX = panelX - toggleW - 2;
            int sidebarX = panelX - sidebarW - 2;

            if (inside(mouseX, mouseY, toggleX, sidebarY, toggleW, SMALL_BUTTON_H)) {
                discoveryFiltersCollapsed = !discoveryFiltersCollapsed;
                return true;
            }
            if (discoveryFiltersCollapsed) {
                return false;
            }

            int rowY = sidebarY + 22;
            for (DiscoveryFilter filter : DiscoveryFilter.values()) {
                if (inside(mouseX, mouseY, sidebarX + 6, rowY, sidebarW - 12, SMALL_BUTTON_H)) {
                    discoveryFilter = filter;
                    discoveryScroll = 0;
                    selectedDiscoveryRow = null;
                    selectedDiscoveryRowKeys.clear();
                    return true;
                }
                rowY += 16;
            }
            return false;
        }

        // Get the discovery filter label
        private static String discoveryFilterLabel(DiscoveryFilter filter) {
            return switch (filter) {
                case ALL -> "All";
                case PRE_LINKED -> "Pre-Linked";
                case BOUND -> "Bound";
                case UNBOUND -> "Unbound";
                case DISCOVERED -> "Discovered";
            };
        }

        // Clear the current binding and close
        private void clearCurrentBindingAndClose() {

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec != null) {
                    removeCustomEntry(ec);
                } else {
                    sendCustomEntryRemove(editingCustomEntryId);
                }
                closeWithoutSaving();
                return;
            }
            clearAssignmentFor(originalChannel != null ? originalChannel : selectedChannel);
            closeWithoutSaving();
        }

        // Close the controller modifier overlay without saving
        private void closeWithoutSaving() {
            editingCustomEntryId = null;
            modifying = false;
            bindingPromptActive = false;
            capturingStepDownKey = false;
            discoveryScroll = 0;
            discoveryGroupScroll = 0;
            selectedDiscoveryRow = null;
            selectedDiscoveryRowKeys.clear();
            selectedDiscoveryGroupId = "";
            selectedGroupTreeNodeId = "";
            selectedGroupTreeNodeIds.clear();
            pendingRoutingChangeEntryId = null;
            latestDiscoveredNodeIds.clear();
            viewMode = returnView;
            updateSlotVisibility();
        }

        // Shift the discovery selection
        boolean shiftDiscovery(double mouseX, double mouseY, int dir) {
            int panelX = leftPos + MODIFY_PANEL_X;
            int panelY = topPos + MODIFY_PANEL_Y;
            int contentX = panelX + 8;
            int contentW = MODIFY_PANEL_W - 16;
            int listX = contentX;
            int listW = contentW - (SMALL_BUTTON_W + 2);
            int listY = panelY + DISCOVERY_LIST_Y + DISCOVERY_LIST_TOP_OFFSET;
            int listH = DISCOVERY_OUTPUT_VISIBLE_ROWS * MAIN_INPUTS_ROW_H;

            if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
                int max = Math.max(0, discoveryRowsForTab(expandedDiscoveryGroupIds).size() - DISCOVERY_OUTPUT_VISIBLE_ROWS);
                discoveryScroll = Mth.clamp(discoveryScroll + dir, 0, max);
                return true;
            }
            return false;
        }

        // Finish the controller modifier overlay
        private void finish() {
            if (editingCustomEntryId != null) {

                syncCustomEntryFromMenu();
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec != null) {
                    ec.keyCode = editingKeyCode;
                    sendCustomEntryUpdate(ec);
                }
                disable();
                return;
            }
            if (selectedChannel != null) {
                syncChannelFromMenu(selectedChannel);
            }
            if (editingKeyCode < 0) {
                clearAssignmentFor(originalChannel);
                disable();
                return;
            }
            commitAssignment(selectedChannel, editingKeyCode);
            disable();
        }

        // Cycle the mechanic
        private void cycleMechanic(int dir) {
            if (selectedChannel == null) {
                selectedChannel = lastEditedChannel;
                return;
            }
            if (tab == ModifierTab.ROUTING) {
                syncChannelFromMenu(selectedChannel);
            }
            AnalogueControlChannel[] channels = AnalogueControlChannel.values();
            int idx = 0;
            for (int i = 0; i < channels.length; i++) {
                if (channels[i] == selectedChannel) {
                    idx = i;
                    break;
                }
            }
            selectedChannel = channels[(idx + dir + channels.length) % channels.length];
            loadChannelToMenu(selectedChannel);
            refreshDiscovery();
        }

        // Draw the config tab
        private void renderConfigTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                int row1 = y + MODIFY_ROW1_Y;
                int row = row1;
                boolean inputBoundCustom = ec != null && ec.inputTarget != null;
                if (inputBoundCustom) {

                    renderReadOnlyActionRow(guiGraphics, Component.literal("Input"), Component.literal(trimToWidth(directTargetSummary(ec.inputTarget), 140)), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W);
                } else {
                    renderActionRow(guiGraphics, Component.literal("Step Up Key"), keyBindingLabel(editingKeyCode, bindingPromptActive), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY, bindingPromptActive);
                }
                row += MODIFY_ROW_STEP;
                renderCycleRow(guiGraphics, Component.literal("Preset"), bindingPresetLabel(ec == null ? "none" : ec.bindingPreset), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;
                renderCycleRow(guiGraphics, Component.literal("Response"), modeLabel(ec == null ? AnalogueChannelMode.RAMP : ec.mode), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;

                boolean showStepDownOptions = ec != null && ec.mode == AnalogueChannelMode.STEP;
                if (!inputBoundCustom && showStepDownOptions) {
                    renderActionRow(guiGraphics, Component.literal("Step Down Key"), keyBindingLabel(ec.stepDownKeyCode, bindingPromptActive && modifier.capturingStepDownKey), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY, bindingPromptActive && modifier.capturingStepDownKey);
                    row += MODIFY_ROW_STEP;
                }

                renderNumericRow(guiGraphics, Component.literal("Attack"), formatRate(ec == null ? 0.08 : ec.riseRate), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;
                renderNumericRow(guiGraphics, Component.literal("Decay"), formatRate(ec == null ? 0.08 : ec.fallRate), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;
                renderNumericRow(guiGraphics, Component.literal("Step"), formatPercent(ec == null ? 0.1 : ec.stepAmount), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;
                if (showStepDownOptions) {
                    renderNumericRow(guiGraphics, Component.literal("Step Down"), formatPercent(ec == null ? 0.1 : ec.stepDownAmount), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                    row += MODIFY_ROW_STEP;
                }
                renderNumericRow(guiGraphics, Component.literal("Deadzone"), formatPercent(ec == null ? 0 : ec.deadzone), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                row += MODIFY_ROW_STEP;
                renderNumericRow(guiGraphics, Component.literal("Smooth"), formatPercent(ec == null ? 0.2 : ec.smoothing), x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, mouseX, mouseY);
                guiGraphics.drawString(font,
                    (!inputBoundCustom && bindingPromptActive) ? Component.literal("Press any key to bind this entry.") : Component.literal(""),
                    x + 14, y + 42, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                return;
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            int row1 = y + MODIFY_ROW1_Y - MODIFY_ROW_STEP;
            renderCycleRow(guiGraphics, Component.literal("Type"), bindingTypeLabel(config), x + MODIFY_LEFT_X, row1, MODIFY_FULL_ROW_W, mouseX, mouseY);

            renderCycleRow(guiGraphics, Component.literal("Preset"), bindingLabel(config, selectedChannel), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderActionRow(guiGraphics, Component.literal("Key"), keyBindingLabel(editingKeyCode, bindingPromptActive), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 2, MODIFY_FULL_ROW_W, mouseX, mouseY, bindingPromptActive);
            renderCycleRow(guiGraphics, Component.literal("Response"), modeLabel(config.mode), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 3, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderNumericRow(guiGraphics, Component.literal("Attack"), formatRate(config.riseRate), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 4, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderNumericRow(guiGraphics, Component.literal("Decay"), formatRate(config.fallRate), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 5, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderNumericRow(guiGraphics, Component.literal("Step"), formatPercent(config.stepAmount), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 6, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderNumericRow(guiGraphics, Component.literal("Deadzone"), formatPercent(config.deadzone), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 7, MODIFY_FULL_ROW_W, mouseX, mouseY);
            renderNumericRow(guiGraphics, Component.literal("Smooth"), formatPercent(config.smoothing), x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 8, MODIFY_FULL_ROW_W, mouseX, mouseY);
            guiGraphics.drawString(font,
                bindingPromptActive ? Component.literal("Press any key to bind this entry.") : Component.literal(""),
                x + 14, y + 42, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        }

        // Draw the routing tab
        private void renderRoutingTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {

            Direction localSide;
            ControllerDirectTargetReference directTarget;
            ItemStack outputFirst, outputSecond;
            ItemStack inputFirst, inputSecond;
            EditableCustomKeyEntry editingEntry = findEditingCustomEntry();
            if (editingCustomEntryId != null) {
                localSide = editingEntry != null ? editingEntry.localOutputSide : null;
                directTarget = editingEntry != null ? editingEntry.directTarget : null;
                outputFirst = editingEntry != null ? editingEntry.first : ItemStack.EMPTY;
                outputSecond = editingEntry != null ? editingEntry.second : ItemStack.EMPTY;
                inputFirst = editingEntry != null ? editingEntry.inputFirst : ItemStack.EMPTY;
                inputSecond = editingEntry != null ? editingEntry.inputSecond : ItemStack.EMPTY;
            } else {
                EditableChannelConfig config = channelConfigs.get(selectedChannel);
                localSide = config != null ? config.localOutputSide : null;
                directTarget = config != null ? config.directTarget : null;
                outputFirst = config != null ? config.first : ItemStack.EMPTY;
                outputSecond = config != null ? config.second : ItemStack.EMPTY;
                inputFirst = ItemStack.EMPTY;
                inputSecond = ItemStack.EMPTY;
            }
            int row1 = y + MODIFY_ROW1_Y;
            renderCycleRow(guiGraphics, Component.literal("Local"), localSideLabel(localSide), x + MODIFY_LEFT_X, row1, MODIFY_FULL_ROW_W, mouseX, mouseY);

            int liveSlotY = topPos + AnalogueContraptionControllerMenu.GHOST_SLOTS_Y;
            int outFirstX = leftPos + AnalogueContraptionControllerMenu.GHOST_SLOT_OUTPUT_FIRST_X;
            int outSecondX = leftPos + AnalogueContraptionControllerMenu.GHOST_SLOT_OUTPUT_SECOND_X;
            int inFirstX = leftPos + AnalogueContraptionControllerMenu.GHOST_SLOT_INPUT_FIRST_X;
            int inSecondX = leftPos + AnalogueContraptionControllerMenu.GHOST_SLOT_INPUT_SECOND_X;
            CTCreateScreenHelper.renderBlockSlots(guiGraphics, outFirstX - 10, liveSlotY - 2, 2);
            CTCreateScreenHelper.renderBlockSlots(guiGraphics, inFirstX - 10, liveSlotY - 2, 2);
            guiGraphics.drawString(font, Component.literal("Output Link"), outFirstX - 24, liveSlotY - 10, CTCreateScreenHelper.LABEL_COLOR, false);
            guiGraphics.drawString(font, Component.literal("Input Link"), inFirstX - 18, liveSlotY - 10, CTCreateScreenHelper.LABEL_COLOR, false);
            renderPreviewSlot(guiGraphics, outputFirst, outFirstX, liveSlotY, false);
            renderPreviewSlot(guiGraphics, outputSecond, outSecondX, liveSlotY, true);
            renderPreviewSlot(guiGraphics, inputFirst, inFirstX, liveSlotY, false);
            renderPreviewSlot(guiGraphics, inputSecond, inSecondX, liveSlotY, true);

            int summaryY = y + ROUTING_TARGETS_Y;
            int summaryX = x + ROUTING_TARGETS_X;
            CTCreateScreenHelper.renderInset(guiGraphics, summaryX, summaryY, ROUTING_TARGETS_W, ROUTING_TARGETS_H, false, false);
                List<RoutingTargetRow> rows = routingActionRows();
                int maxScroll = Math.max(0, rows.size() - ROUTING_TARGETS_VISIBLE_ROWS);
            routingTargetsScroll = Mth.clamp(routingTargetsScroll, 0, maxScroll);
            for (int i = 0; i < ROUTING_TARGETS_VISIBLE_ROWS; i++) {
                int rowIndex = routingTargetsScroll + i;
                if (rowIndex >= rows.size()) {
                    break;
                }
                RoutingTargetRow row = rows.get(rowIndex);
                int rowY = summaryY + 4 + i * 12;
                int unbindX = summaryX + ROUTING_TARGETS_W - 98;
                int changeX = summaryX + ROUTING_TARGETS_W - 48;
                guiGraphics.drawString(font, Component.literal(trimToWidth(row.label(), ROUTING_TARGETS_W - 152)),
                    summaryX + 4, rowY + 1, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                renderSmallButton(guiGraphics, unbindX, rowY, 48, Component.literal("Unbind"), mouseX, mouseY, true);
                renderSmallButton(guiGraphics, changeX, rowY, 48, Component.literal("Change"), mouseX, mouseY,
                    row.changeable());
            }
            int scrollX = summaryX + ROUTING_TARGETS_W - 20;
            renderSmallButton(guiGraphics, scrollX, summaryY + 2, 16, Component.literal("^"), mouseX, mouseY, routingTargetsScroll > 0);
            renderSmallButton(guiGraphics, scrollX, summaryY + ROUTING_TARGETS_H - 16, 16, Component.literal("v"), mouseX, mouseY,
                    routingTargetsScroll < maxScroll);
        }

        // Get the routing target rows
        private List<String> routingTargetRows() {
            List<String> rows = new ArrayList<>();
            EditableCustomKeyEntry editingEntry = findEditingCustomEntry();
            ControllerDirectTargetReference directTarget;
            if (editingEntry != null) {
                directTarget = editingEntry.directTarget;
            } else {
                EditableChannelConfig config = channelConfigs.get(selectedChannel);
                directTarget = config == null ? null : config.directTarget;
            }
            rows.add("Target: " + directTargetSummary(directTarget));

            if (editingEntry != null) {
                rows.add("Input Link: " + (hasFrequencyPair(editingEntry.inputFirst, editingEntry.inputSecond)
                        ? itemName(editingEntry.inputFirst) + " / " + itemName(editingEntry.inputSecond)
                        : "None"));
                rows.add("Output Link: " + (hasFrequencyPair(editingEntry.first, editingEntry.second)
                        ? itemName(editingEntry.first) + " / " + itemName(editingEntry.second)
                        : "None"));
                rows.add("Linked routes:");
                List<String> relatedRoutes = relatedRouteRows(editingEntry);
                if (relatedRoutes.isEmpty()) {
                    rows.add("- No linked outputs yet");
                } else {
                    for (String relatedRoute : relatedRoutes) {
                        rows.add("- " + relatedRoute);
                    }
                }
            } else {
                EditableChannelConfig config = channelConfigs.get(selectedChannel);
                rows.add("Output Link: " + (config != null && hasFrequencyPair(config.first, config.second)
                        ? itemName(config.first) + " / " + itemName(config.second)
                        : "None"));
            }
            return rows;
        }

        // Draw the discovery tab
        private void renderDiscoveryTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {

            // ------------------------------------DISCOVERY HEADER------------------------------------
            ControllerDirectTargetReference activeDirectTarget;
            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                activeDirectTarget = ec != null ? ec.directTarget : null;
            } else {
                EditableChannelConfig config = channelConfigs.get(selectedChannel);
                activeDirectTarget = config != null ? config.directTarget : null;
            }
            List<DiscoveryRow> rows = discoveryRowsForTab(expandedDiscoveryGroupIds);
            discoveryScroll = Mth.clamp(discoveryScroll, 0, Math.max(0, rows.size() - DISCOVERY_OUTPUT_VISIBLE_ROWS));
            guiGraphics.drawString(font, Component.literal("Bind discovered outputs."), x + 8, y + 46, CTCreateScreenHelper.LABEL_COLOR, false);

            int selectedDiscoveryCount = selectedDiscoveryRowKeys.size();
            renderSmallButton(guiGraphics, x + 8, y + 70, 38, Component.literal("Scan"), mouseX, mouseY, true);
            renderSmallButton(guiGraphics, x + 50, y + 70, 40, Component.literal("Bind"), mouseX, mouseY,
                    selectedDiscoveryCount > 0);
            renderSmallButton(guiGraphics, x + 94, y + 70, 42, Component.literal("Unbind"), mouseX, mouseY,
                    selectedDiscoveryCount > 0);
            guiGraphics.drawString(font,
                    Component.literal(selectedDiscoveryCount + " selected"),
                    x + 8, y + 86, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            guiGraphics.drawString(font,
                    Component.literal(rows.size() + " outputs"),
                    x + 104, y + 86, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);

            // ------------------------------------OUTPUT LIST------------------------------------
            int contentX = x + 8;
            int contentW = MODIFY_PANEL_W - 16;
            int listX = contentX;
            int listW = contentW - (SMALL_BUTTON_W + 2);
            int listY = y + DISCOVERY_LIST_Y + DISCOVERY_LIST_TOP_OFFSET;
            int listVisibleRows = DISCOVERY_OUTPUT_VISIBLE_ROWS;
            int listH = listVisibleRows * MAIN_INPUTS_ROW_H;

            guiGraphics.drawString(font, Component.literal("Outputs"), listX, listY - 12,
                    CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            CTCreateScreenHelper.renderInset(guiGraphics, listX, listY, listW, listH,
                    inside(mouseX, mouseY, listX, listY, listW, listH), false);

            List<Component> discoveryRowTooltip = null;

            // ------------------------------------DISCOVERED TARGETS------------------------------------
            for (int visible = 0; visible < listVisibleRows; visible++) {
                int idx = discoveryScroll + visible;
                int rowY = listY + visible * MAIN_INPUTS_ROW_H;
                DiscoveryRow row = idx < rows.size() ? rows.get(idx) : null;
                if (row == null) {
                    continue;
                }
                int nestedInset = row.group() ? 0 : 8;
                int rowX = listX + nestedInset;
                int rowW = listW - nestedInset;
                boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, SMALL_BUTTON_H);
                boolean active = matchesTarget(activeDirectTarget, row, selectedChannel);
                boolean selected = isSelectedDiscoveryRow(row);

                boolean bound = !row.group() && isDiscoveryRowBound(row);
                String rowLabel;
                if (row.group()) {
                    rowLabel = (row.expanded() ? "v " : "> ") + trimToWidth(row.node().label(), rowW - 8);
                } else {
                    rowLabel = "- " + trimToWidth(row.option().label(), rowW - 8);
                }

                renderSmallButton(guiGraphics, rowX, rowY, rowW, Component.empty(), mouseX, mouseY, true);
                if (!row.group()) {
                    guiGraphics.fill(rowX, rowY, rowX + rowW, rowY + SMALL_BUTTON_H, 0x14000000);
                }
                if (hovered) {
                    guiGraphics.fill(rowX, rowY, rowX + rowW, rowY + SMALL_BUTTON_H, 0x16FFFFFF);
                }
                if (active || selected) {
                    guiGraphics.fill(rowX, rowY, rowX + rowW, rowY + SMALL_BUTTON_H, active ? 0x2233CC88 : 0x2266AAFF);
                } else if (bound) {

                    guiGraphics.fill(rowX, rowY, rowX + rowW, rowY + SMALL_BUTTON_H, 0x22FFAA33);
                }
                guiGraphics.drawString(font, Component.literal(rowLabel), rowX + 4, rowY + 3, CTCreateScreenHelper.LABEL_COLOR, false);

                if (hovered && discoveryRowTooltip == null) {
                    String fullLabel = row.group() ? row.node().label() : row.option().label();
                    String binding = bound && !row.group() ? getBindingTooltipForDiscoveryRow(row) : null;
                    discoveryRowTooltip = discoveryNodeTooltip(row.node(), Component.literal(fullLabel),
                            binding == null ? null : Component.literal(binding));
                }
            }

            // ------------------------------------LIST CONTROLS------------------------------------
            renderSmallButton(guiGraphics, listX + listW + 2, listY, SMALL_BUTTON_W,
                    Component.literal("^"), mouseX, mouseY, discoveryScroll > 0);
            renderSmallButton(guiGraphics, listX + listW + 2, listY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W,
                    Component.literal("v"), mouseX, mouseY,
                    discoveryScroll < Math.max(0, rows.size() - listVisibleRows));

            // ------------------------------------ROUTING / TOOLTIPS------------------------------------
            if (pendingRoutingChangeEntryId != null) {
                guiGraphics.drawString(font, Component.literal("Select an output and click Bind to change target."),
                        x + 8, y + MODIFY_PANEL_H - 38, CTCreateScreenHelper.VALUE_COLOR, false);
            }

            if (discoveryRowTooltip != null) {
                guiGraphics.renderTooltip(font, discoveryRowTooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }

        // Handle the config click
        private boolean clickConfig(double mouseX, double mouseY, int x, int y) {
            // ------------------------------------CONFIG LAYOUT------------------------------------
            int row1 = y + MODIFY_ROW1_Y - MODIFY_ROW_STEP;

            // ------------------------------------CUSTOM ENTRY------------------------------------
            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                boolean inputBoundCustom = ec != null && ec.inputTarget != null;
                int customRow1 = y + MODIFY_ROW1_Y;
                int row = customRow1;
                int keySetWidth = 30;
                int keyClearWidth = 24;
                int keySetX = x + MODIFY_LEFT_X + MODIFY_FULL_ROW_W - keySetWidth;
                int keyClearX = keySetX - keyClearWidth - 4;
                if (!inputBoundCustom) {
                    if (inside(mouseX, mouseY, keyClearX, row, keyClearWidth, SMALL_BUTTON_H)) {
                        editingKeyCode = -1;
                        bindingPromptActive = false;
                        modifier.capturingStepDownKey = false;
                        return true;
                    }
                    if (inside(mouseX, mouseY, keySetX, row, keySetWidth, SMALL_BUTTON_H)) {
                        bindingPromptActive = true;
                        modifier.capturingStepDownKey = false;
                        return true;
                    }
                }
                row += MODIFY_ROW_STEP;
                if (clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W)) {
                    cycleCustomPreset(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
                    return true;
                }
                row += MODIFY_ROW_STEP;
                if (clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W)) {
                    cycleMode(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
                    return true;
                }
                row += MODIFY_ROW_STEP;

                boolean showStepDownOptions = ec != null && ec.mode == AnalogueChannelMode.STEP;
                if (!inputBoundCustom && showStepDownOptions) {
                    if (inside(mouseX, mouseY, keyClearX, row, keyClearWidth, SMALL_BUTTON_H)) {
                        ec.stepDownKeyCode = -1;
                        sendCustomEntryUpdate(ec);
                        propagateConfigToInputSiblings(ec);
                        bindingPromptActive = false;
                        modifier.capturingStepDownKey = false;
                        return true;
                    }
                    if (inside(mouseX, mouseY, keySetX, row, keySetWidth, SMALL_BUTTON_H)) {
                        bindingPromptActive = true;
                        modifier.capturingStepDownKey = true;
                        return true;
                    }
                    row += MODIFY_ROW_STEP;
                }

                if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.01D, NumericField.RISE_RATE)) return true;
                row += MODIFY_ROW_STEP;
                if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.01D, NumericField.FALL_RATE)) return true;
                row += MODIFY_ROW_STEP;
                if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.05D, NumericField.STEP_AMOUNT)) return true;
                row += MODIFY_ROW_STEP;
                if (showStepDownOptions) {
                    if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.05D, NumericField.STEP_DOWN_AMOUNT)) return true;
                    row += MODIFY_ROW_STEP;
                }
                if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.02D, NumericField.DEADZONE)) return true;
                row += MODIFY_ROW_STEP;
                return clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row, MODIFY_FULL_ROW_W, 0.02D, NumericField.SMOOTHING);
            }
            if (clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row1, MODIFY_FULL_ROW_W)) {
                cycleBindingMode(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
                return true;
            }
            if (clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP, MODIFY_FULL_ROW_W)) {
                cyclePresetAndApply(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
                return true;
            }
            int keySetWidth = 30;
            int keyClearWidth = 24;
            int keySetX = x + MODIFY_LEFT_X + MODIFY_FULL_ROW_W - keySetWidth;
            int keyClearX = keySetX - keyClearWidth - 4;
            if (inside(mouseX, mouseY, keyClearX, row1 + MODIFY_ROW_STEP * 2, keyClearWidth, SMALL_BUTTON_H)) {
                editingKeyCode = -1;
                bindingPromptActive = false;
                return true;
            }
            if (inside(mouseX, mouseY, keySetX, row1 + MODIFY_ROW_STEP * 2, keySetWidth, SMALL_BUTTON_H)) {
                bindingPromptActive = true;
                return true;
            }
            if (clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 3, MODIFY_FULL_ROW_W)) {
                cycleMode(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
                return true;
            }
            if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 4, MODIFY_FULL_ROW_W, 0.01D, NumericField.RISE_RATE)) {
                return true;
            }
            if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 5, MODIFY_FULL_ROW_W, 0.01D, NumericField.FALL_RATE)) {
                return true;
            }
            if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 6, MODIFY_FULL_ROW_W, 0.05D, NumericField.STEP_AMOUNT)) {
                return true;
            }
            if (clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 7, MODIFY_FULL_ROW_W, 0.02D, NumericField.DEADZONE)) {
                return true;
            }
            return clickNumeric(mouseX, mouseY, x + MODIFY_LEFT_X, row1 + MODIFY_ROW_STEP * 8, MODIFY_FULL_ROW_W, 0.02D, NumericField.SMOOTHING);
        }

        // Cycle the binding mode
        private void cycleBindingMode(int dir) {
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null || dir == 0) {
                return;
            }
            config.bindingMode = config.isCustomBinding() ? "standard" : "custom";
            sendSingleUpdate(selectedChannel);
        }

        // Cycle the preset and apply
        private void cyclePresetAndApply(int dir) {
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null || dir == 0) {
                return;
            }
            config.bindingPreset = cyclePreset(config.bindingPreset, dir);
            applyPresetToConfig(config);
            sendSingleUpdate(selectedChannel);
        }

        // Cycle the custom preset
        private void cycleCustomPreset(int dir) {
            EditableCustomKeyEntry ec = findEditingCustomEntry();
            if (ec == null || dir == 0) {
                return;
            }
            ec.bindingPreset = cyclePreset(ec.bindingPreset, dir);

            sendCustomEntryUpdate(ec);
        }

    // Get the binding type label
    private static Component bindingTypeLabel(EditableChannelConfig config) {
        return Component.literal(config != null && config.isCustomBinding() ? "Custom" : "Preset");
    }

    // Get the binding label
    private static Component bindingLabel(EditableChannelConfig config, AnalogueControlChannel selectedChannel) {
        if (config == null) {
            return Component.literal("None");
        }
        return bindingPresetLabel(config.bindingPreset);
    }

    // Get the binding preset label
    private static Component bindingPresetLabel(String preset) {
        String normalized = preset == null || preset.isBlank() ? "none" : preset;
        return Component.literal("none".equalsIgnoreCase(normalized) ? "None" : capitalizePreset(normalized));
    }

    // Cycle the preset
    private static String cyclePreset(String currentPreset, int dir) {
        if (dir == 0) {
            return currentPreset == null || currentPreset.isBlank() ? "none" : currentPreset;
        }
        String normalized = currentPreset == null || currentPreset.isBlank()
                ? "none"
                : currentPreset.toLowerCase(Locale.ROOT);
        int idx = 0;
        for (int i = 0; i < PRESET_OPTIONS.length; i++) {
            if (PRESET_OPTIONS[i].equals(normalized)) {
                idx = i;
                break;
            }
        }
        return PRESET_OPTIONS[(idx + dir + PRESET_OPTIONS.length) % PRESET_OPTIONS.length];
    }

    // Get the capitalize preset
    private static String capitalizePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return "None";
        }
        String lower = preset.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // Apply the preset to config
    private void applyPresetToConfig(EditableChannelConfig config) {
        if (config == null) {
            return;
        }
        PresetTemplate template = resolvePresetTemplate(config.bindingPreset);
        config.mode = template.mode();
        config.riseRate = template.attack();
        config.fallRate = template.decay();
        config.stepAmount = template.step();
        config.deadzone = template.deadzone();
        config.smoothing = template.smoothing();
    }

    // Resolve the preset template
    private PresetTemplate resolvePresetTemplate(String presetId) {
        String normalized = presetId == null || presetId.isBlank() ? "none" : presetId.toLowerCase(Locale.ROOT);
        AnalogueControlChannel sourceChannel = switch (normalized) {
            case "yaw" -> AnalogueControlChannel.YAW_RIGHT;
            case "roll" -> AnalogueControlChannel.ROLL_RIGHT;
            case "pitch" -> AnalogueControlChannel.PITCH_UP;
            case "throttle" -> AnalogueControlChannel.THROTTLE_UP;
            default -> null;
        };
        if (sourceChannel == null) {
            return new PresetTemplate(AnalogueChannelMode.RAMP, 0.08D, 0.08D, 0.1D, 0.0D, 0.2D);
        }
        EditableChannelConfig src = channelConfigs.get(sourceChannel);
        if (src == null) {
            return new PresetTemplate(AnalogueChannelMode.RAMP, 0.08D, 0.08D, 0.1D, 0.0D, 0.2D);
        }
        return new PresetTemplate(src.mode, src.riseRate, src.fallRate, src.stepAmount, src.deadzone, src.smoothing);
    }
        // Handle the routing click
        private boolean clickRouting(double mouseX, double mouseY, int x, int y) {
            int row1 = y + MODIFY_ROW1_Y;
            int summaryX = x + ROUTING_TARGETS_X;
            int summaryY = y + ROUTING_TARGETS_Y;
            List<RoutingTargetRow> routeRows = routingActionRows();
            int maxRouteScroll = Math.max(0, routeRows.size() - ROUTING_TARGETS_VISIBLE_ROWS);
            routingTargetsScroll = Mth.clamp(routingTargetsScroll, 0, maxRouteScroll);
            for (int i = 0; i < ROUTING_TARGETS_VISIBLE_ROWS; i++) {
                int rowIndex = routingTargetsScroll + i;
                if (rowIndex >= routeRows.size()) {
                    break;
                }
                int rowY = summaryY + 4 + i * 12;
                int unbindX = summaryX + ROUTING_TARGETS_W - 98;
                int changeX = summaryX + ROUTING_TARGETS_W - 48;
                RoutingTargetRow row = routeRows.get(rowIndex);
                if (inside(mouseX, mouseY, unbindX, rowY, 48, SMALL_BUTTON_H)) {
                    unbindRouteRow(row);
                    return true;
                }
                if (inside(mouseX, mouseY, changeX, rowY, 48, SMALL_BUTTON_H) && row.changeable()) {
                    pendingRoutingChangeEntryId = row.entryId();
                    tab = ModifierTab.DISCOVERY;
                    updateSlotVisibility();
                    return true;
                }
            }

            int scrollX = summaryX + ROUTING_TARGETS_W - 20;
            if (inside(mouseX, mouseY, scrollX, summaryY + 2, 16, SMALL_BUTTON_H)) {
                routingTargetsScroll = Math.max(0, routingTargetsScroll - 1);
                return true;
            }
            if (inside(mouseX, mouseY, scrollX, summaryY + ROUTING_TARGETS_H - 16, 16, SMALL_BUTTON_H)) {
                int maxScroll = Math.max(0, routingActionRows().size() - ROUTING_TARGETS_VISIBLE_ROWS);
                routingTargetsScroll = Math.min(maxScroll, routingTargetsScroll + 1);
                return true;
            }
            if (!clickCycle(mouseX, mouseY, x + MODIFY_LEFT_X, row1, MODIFY_FULL_ROW_W)) {
                return false;
            }
            cycleLocalSide(isLeftCycle(mouseX, x + MODIFY_LEFT_X) ? -1 : 1);
            return true;
        }

        // Handle the discovery click
        private boolean clickDiscovery(double mouseX, double mouseY, int x, int y) {
            // ------------------------------------DISCOVERY ACTIONS------------------------------------
            List<DiscoveryRow> rows = discoveryRowsForTab(expandedDiscoveryGroupIds);
            boolean ctrlDown = Screen.hasControlDown();
            if (inside(mouseX, mouseY, x + 8, y + 70, 38, SMALL_BUTTON_H)) {
                refreshDiscovery();
                return true;
            }
            if (inside(mouseX, mouseY, x + 50, y + 70, 40, SMALL_BUTTON_H)) {
                bindAllSelectedDiscoveryRows();
                return true;
            }

            if (inside(mouseX, mouseY, x + 94, y + 70, 42, SMALL_BUTTON_H)) {
                unbindAllSelectedDiscoveryRows();
                return true;
            }

            // ------------------------------------LIST CONTROLS------------------------------------
            int contentX = x + 8;
            int contentW = MODIFY_PANEL_W - 16;
            int listX = contentX;
            int listW = contentW - (SMALL_BUTTON_W + 2);
            int listY = y + DISCOVERY_LIST_Y + DISCOVERY_LIST_TOP_OFFSET;
            int listVisibleRows = DISCOVERY_OUTPUT_VISIBLE_ROWS;

            if (inside(mouseX, mouseY, listX + listW + 2, listY, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
                if (discoveryScroll > 0) {
                    discoveryScroll--;
                }
                return true;
            }
            if (inside(mouseX, mouseY, listX + listW + 2, listY + SMALL_BUTTON_H + 2, SMALL_BUTTON_W, SMALL_BUTTON_H)) {
                int maxScroll = Math.max(0, rows.size() - listVisibleRows);
                if (discoveryScroll < maxScroll) {
                    discoveryScroll++;
                }
                return true;
            }
            // ------------------------------------TARGET SELECTION------------------------------------
            for (int visible = 0; visible < listVisibleRows; visible++) {
                int idx = discoveryScroll + visible;
                if (idx >= rows.size()) {
                    break;
                }
                int rowY = listY + visible * MAIN_INPUTS_ROW_H;
                DiscoveryRow clickedRow = rows.get(idx);
                int nestedInset = clickedRow.group() ? 0 : 8;
                int rowX = listX + nestedInset;
                int rowW = listW - nestedInset;
                if (!inside(mouseX, mouseY, rowX, rowY, rowW, SMALL_BUTTON_H)) {
                    continue;
                }
                ControllerDiscoveryNode clickedNode = clickedRow.node();
                if (clickedRow.group() && clickedRow.expandable()) {
                    if (expandedDiscoveryGroupIds.contains(clickedNode.nodeId())) {
                        expandedDiscoveryGroupIds.remove(clickedNode.nodeId());
                    } else {
                        expandedDiscoveryGroupIds.add(clickedNode.nodeId());
                    }
                    discoveryScroll = Mth.clamp(discoveryScroll, 0, Math.max(0, discoveryRowsForTab(expandedDiscoveryGroupIds).size() - DISCOVERY_OUTPUT_VISIBLE_ROWS));
                    return true;
                }

                selectedDiscoveryRow = clickedRow;
                String key = discoveryRowKey(clickedRow);
                if (ctrlDown) {
                    if (selectedDiscoveryRowKeys.contains(key)) {
                        selectedDiscoveryRowKeys.remove(key);
                    } else {
                        selectedDiscoveryRowKeys.add(key);
                    }
                } else {
                    selectedDiscoveryRowKeys.clear();
                    selectedDiscoveryRowKeys.add(key);
                }
                if (clickedNode != null && clickedNode.groupId() != null && !clickedNode.groupId().isBlank()) {
                    selectedDiscoveryGroupId = clickedNode.groupId();
                }
                return true;
            }
            return false;
        }

        // Bind the selected group targets
        private void bindSelectedGroupTargets() {
            if (selectedDiscoveryGroupId == null || selectedDiscoveryGroupId.isBlank()) {
                return;
            }
            List<DiscoveryRow> groupRows = selectedGroupDiscoveryRows();
            if (groupRows.isEmpty()) {
                return;
            }
            selectedDiscoveryRowKeys.clear();
            for (DiscoveryRow row : groupRows) {
                selectedDiscoveryRowKeys.add(discoveryRowKey(row));
            }
            bindAllSelectedDiscoveryRows();
        }

        // Get the selected group discovery rows
        private List<DiscoveryRow> selectedGroupDiscoveryRows() {
            List<DiscoveryRow> groupRows = new ArrayList<>();
            for (DiscoveryRow row : discoveryRowsForTab(expandedDiscoveryGroupIds)) {
                if (row == null || row.group() || row.node() == null) {
                    continue;
                }
                String groupId = row.node().groupId();
                if (groupId != null && groupId.equalsIgnoreCase(selectedDiscoveryGroupId)) {
                    groupRows.add(row);
                }
            }
            return groupRows;
        }

        // Assign the selected discovery rows to group
        private void assignSelectedDiscoveryRowsToGroup() {
            if (selectedDiscoveryGroupId == null || selectedDiscoveryGroupId.isBlank()) {
                return;
            }
            for (DiscoveryRow row : selectedDiscoveryRows()) {
                if (row == null || row.group() || row.node() == null) {
                    continue;
                }
                updateDiscoveryNodeMetadata(row.node(), row.node().label(), selectedDiscoveryGroupId);
            }
        }

        // Pull all selected inputs from their groups
        private void pullAllSelectedFromGroups() {
            Set<String> ids = new HashSet<>(selectedGroupTreeNodeIds);
            for (DiscoveryRow row : selectedDiscoveryRows()) {
                if (row != null && row.node() != null) {
                    ids.add(row.node().nodeId());
                }
            }
            for (ControllerDiscoveryNode node : discoveryNodesForTab()) {
                if (node != null && ids.contains(node.nodeId())) {
                    updateDiscoveryNodeMetadata(node, node.label(), "");
                }
            }
        }

        // Bind all selected discovery rows
        private void bindAllSelectedDiscoveryRows() {
            List<DiscoveryRow> selectedRows = selectedDiscoveryRows();
            if (selectedRows.isEmpty()) {
                return;
            }

            if (selectedRows.size() > 1 && editingCustomEntryId == null && selectedChannel != null) {
                EditableCustomKeyEntry base = createCustomEntryFromSelectedChannel();
                if (base != null) {
                    customEntryConfigs.add(base);
                    sendCustomEntryAdd(base);
                    sendCustomEntryUpdate(base);
                    startCustom(base, returnView);
                }
            }

            bindDiscoveryRow(selectedRows.get(0), false);
            for (int i = 1; i < selectedRows.size(); i++) {
                bindDiscoveryRow(selectedRows.get(i), true);
            }
        }

        // Create the custom entry from selected channel
        private EditableCustomKeyEntry createCustomEntryFromSelectedChannel() {
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null) {
                return null;
            }
            EditableCustomKeyEntry entry = new EditableCustomKeyEntry();
            entry.id = UUID.randomUUID().toString();
            entry.keyCode = config.keyCode;
            entry.label = Component.translatable(selectedChannel.translationKey()).getString();
            entry.bindingPreset = config.bindingPreset;
            entry.mode = config.mode;
            entry.riseRate = config.riseRate;
            entry.fallRate = config.fallRate;
            entry.stepAmount = config.stepAmount;
            entry.stepDownAmount = config.stepAmount;
            entry.deadzone = config.deadzone;
            entry.smoothing = config.smoothing;
            entry.localOutputSide = config.localOutputSide;
            entry.first = copySingle(config.first);
            entry.second = copySingle(config.second);
            entry.directTarget = config.directTarget;
            entry.inputTarget = config.inputTarget;
            return entry;
        }

        // Unbind all selected discovery rows
        private void unbindAllSelectedDiscoveryRows() {
            Set<String> selectedTargetIds = new HashSet<>();
            for (DiscoveryRow row : selectedDiscoveryRows()) {
                if (row == null || row.node() == null || row.group()) {
                    continue;
                }
                ControllerDirectTargetReference target = directTargetForDiscoveryOption(row.node(), row.option());
                if (target != null && target.targetId() != null && !target.targetId().isBlank()) {
                    selectedTargetIds.add(target.targetId());
                }
            }
            if (selectedTargetIds.isEmpty()) {
                return;
            }

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec != null && ec.directTarget != null && selectedTargetIds.contains(ec.directTarget.targetId())) {
                    ec.directTarget = null;
                }
            }
            for (EditableCustomKeyEntry entry : customEntryConfigs) {
                if (entry != null && entry.directTarget != null && selectedTargetIds.contains(entry.directTarget.targetId())) {
                    entry.directTarget = null;
                    sendCustomEntryUpdate(entry);
                }
            }
            if (selectedChannel != null) {
                EditableChannelConfig config = channelConfigs.get(selectedChannel);
                if (config != null && config.directTarget != null && selectedTargetIds.contains(config.directTarget.targetId())) {
                    config.directTarget = null;
                    sendSingleUpdate(selectedChannel);
                }
            }
        }

        // Get the selected discovery rows
        private List<DiscoveryRow> selectedDiscoveryRows() {
            List<DiscoveryRow> selectedRows = new ArrayList<>();
            List<DiscoveryRow> rows = discoveryRowsForTab(expandedDiscoveryGroupIds);
            for (DiscoveryRow row : rows) {
                if (row == null || row.node() == null || row.group()) {
                    continue;
                }
                if (selectedDiscoveryRowKeys.contains(discoveryRowKey(row))) {
                    selectedRows.add(row);
                }
            }
            if (selectedRows.isEmpty() && selectedDiscoveryRow != null && !selectedDiscoveryRow.group()) {
                selectedRows.add(selectedDiscoveryRow);
            }
            return selectedRows;
        }

        // Get the discovery row key
        private String discoveryRowKey(DiscoveryRow row) {
            if (row == null || row.node() == null) {
                return "";
            }
            String option = row.option() == null || row.option().label() == null ? "" : row.option().label();
            return row.node().nodeId() + "|" + option;
        }

        // Bind the discovery row
        private void bindDiscoveryRow(DiscoveryRow clickedRow, boolean forceNewCustomEntry) {
            // ------------------------------------DISCOVERY CHECKS------------------------------------
            if (clickedRow == null || clickedRow.node() == null || clickedRow.group()) {
                return;
            }
            ControllerDiscoveryNode clickedNode = clickedRow.node();
            // ------------------------------------TARGET RESOLUTION------------------------------------
            RedstoneLinkInfo redstoneLinkInfo = resolveRedstoneLinkInfo(clickedNode);
            ControllerDirectTargetReference optionTarget = directTargetForDiscoveryOption(clickedNode, clickedRow.option());
            if (redstoneLinkInfo == null && (optionTarget == null || !optionTarget.isBound())) {
                return;
            }
            ItemStack routeFirst = ItemStack.EMPTY;
            ItemStack routeSecond = ItemStack.EMPTY;
            EditableCustomKeyEntry editingEntry = findEditingCustomEntry();

            boolean spawnSiblingOutputEntry = forceNewCustomEntry;

            // ------------------------------------ROUTE CHANGE------------------------------------
            if (pendingRoutingChangeEntryId != null && clickedRow.option() != null) {
                if ("__channel__".equals(pendingRoutingChangeEntryId)) {
                    EditableChannelConfig config = channelConfigs.get(selectedChannel);
                    if (config != null) {
                        config.directTarget = optionTarget;
                        if (redstoneLinkInfo != null) {
                            config.first = copySingle(redstoneLinkInfo.first());
                            config.second = copySingle(redstoneLinkInfo.second());
                            config.directTarget = null;
                        }
                        sendSingleUpdate(selectedChannel);
                    }
                    pendingRoutingChangeEntryId = null;
                    tab = ModifierTab.ROUTING;
                    updateSlotVisibility();
                    return;
                }
                for (EditableCustomKeyEntry entry : customEntryConfigs) {
                    if (entry == null || !Objects.equals(entry.id, pendingRoutingChangeEntryId)) {
                        continue;
                    }
                    entry.directTarget = optionTarget;
                    if (redstoneLinkInfo != null) {
                        entry.first = copySingle(redstoneLinkInfo.first());
                        entry.second = copySingle(redstoneLinkInfo.second());
                        entry.directTarget = null;
                    }
                    sendCustomEntryUpdate(entry);
                    pendingRoutingChangeEntryId = null;
                    tab = ModifierTab.ROUTING;
                    updateSlotVisibility();
                    return;
                }
                pendingRoutingChangeEntryId = null;
            }

            if (!spawnSiblingOutputEntry && editingCustomEntryId != null && editingEntry != null
                    && editingEntry.directTarget != null && optionTarget != null
                    && !Objects.equals(editingEntry.directTarget.targetId(), optionTarget.targetId())) {

                spawnSiblingOutputEntry = true;
            }

            // ------------------------------------ENTRY BINDING------------------------------------
            if (!spawnSiblingOutputEntry) {
                if (editingCustomEntryId != null) {
                    EditableCustomKeyEntry ec = editingEntry;
                    if (ec != null) {
                        if (redstoneLinkInfo != null) {
                            ec.first = copySingle(redstoneLinkInfo.first());
                            ec.second = copySingle(redstoneLinkInfo.second());
                            ec.directTarget = null;
                        } else {
                            ec.directTarget = optionTarget;
                        }
                        routeFirst = copySingle(ec.first);
                        routeSecond = copySingle(ec.second);

                        sendCustomEntryUpdate(ec);
                    }
                } else {
                    EditableChannelConfig config = channelConfigs.get(selectedChannel);
                    if (config != null) {
                        if (redstoneLinkInfo != null) {
                            config.first = copySingle(redstoneLinkInfo.first());
                            config.second = copySingle(redstoneLinkInfo.second());
                            config.directTarget = null;
                        } else {
                            config.directTarget = optionTarget;
                        }
                        routeFirst = copySingle(config.first);
                        routeSecond = copySingle(config.second);
                        sendSingleUpdate(selectedChannel);
                    }
                }
            } else {
                EditableCustomKeyEntry entry = editingCustomEntryId != null && editingEntry != null
                        ? cloneCustomEntry(editingEntry)
                        : new EditableCustomKeyEntry();
                entry.id = UUID.randomUUID().toString();
                if (editingCustomEntryId == null || editingEntry == null) {
                    entry.keyCode = -1;
                }
                entry.label = clickedRow.option() == null ? clickedNode.label() : clickedRow.option().label();
                customEntryConfigs.add(entry);
                sendCustomEntryAdd(entry);
                if (redstoneLinkInfo != null) {
                    entry.first = copySingle(redstoneLinkInfo.first());
                    entry.second = copySingle(redstoneLinkInfo.second());
                    entry.directTarget = null;
                } else {
                    entry.directTarget = optionTarget;
                }
                sendCustomEntryUpdate(entry);
            }

            menu.ghostInventory.setStackInSlot(0, routeFirst);
            menu.ghostInventory.setStackInSlot(1, routeSecond);
            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec != null) {
                    menu.ghostInventory.setStackInSlot(2, copySingle(ec.inputFirst));
                    menu.ghostInventory.setStackInSlot(3, copySingle(ec.inputSecond));
                }
            } else {
                menu.ghostInventory.setStackInSlot(2, ItemStack.EMPTY);
                menu.ghostInventory.setStackInSlot(3, ItemStack.EMPTY);
            }
            tab = ModifierTab.ROUTING;
            updateSlotVisibility();
        }

        // Get the clone custom entry
        private EditableCustomKeyEntry cloneCustomEntry(EditableCustomKeyEntry src) {
            EditableCustomKeyEntry clone = new EditableCustomKeyEntry();
            clone.keyCode = src.keyCode;
            clone.label = src.label;
            clone.bindingPreset = src.bindingPreset;
            clone.mode = src.mode;
            clone.riseRate = src.riseRate;
            clone.fallRate = src.fallRate;
            clone.stepAmount = src.stepAmount;
            clone.stepDownAmount = src.stepDownAmount;
            clone.deadzone = src.deadzone;
            clone.smoothing = src.smoothing;
            clone.stepDownKeyCode = src.stepDownKeyCode;
            clone.localOutputSide = src.localOutputSide;
            clone.first = copySingle(src.first);
            clone.second = copySingle(src.second);
            clone.inputFirst = copySingle(src.inputFirst);
            clone.inputSecond = copySingle(src.inputSecond);
            clone.directTarget = src.directTarget;
            clone.inputTarget = src.inputTarget;
            return clone;
        }

        // Get the routing action rows
        private List<RoutingTargetRow> routingActionRows() {
            List<RoutingTargetRow> rows = new ArrayList<>();
            EditableCustomKeyEntry editingEntry = findEditingCustomEntry();
            if (editingEntry != null) {
                for (EditableCustomKeyEntry entry : customEntryConfigs) {
                    if (entry == null || !shareInputBinding(editingEntry, entry)) {
                        continue;
                    }
                    rows.add(new RoutingTargetRow(entry.id,
                            (entry.label == null || entry.label.isBlank() ? "Custom" : entry.label) + ": " + routingOutputSummary(entry),
                            true));
                }
                if (rows.isEmpty()) {
                    rows.add(new RoutingTargetRow("", "No routes", false));
                }
                return rows;
            }

            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            String channelName = selectedChannel == null ? "Channel" : Component.translatable(selectedChannel.translationKey()).getString();
            String output = config == null ? "Unbound" : (config.directTarget != null
                    ? "Target: " + directTargetLabel(config.directTarget)
                    : (hasFrequencyPair(config.first, config.second)
                    ? "Link: " + itemName(config.first) + " / " + itemName(config.second)
                    : "Unbound"));
                rows.add(new RoutingTargetRow("__channel__", channelName + ": " + output, true));
            return rows;
        }

        // Unbind the route row
        private void unbindRouteRow(RoutingTargetRow row) {
            if (row == null) {
                return;
            }
            if (!row.entryId().isBlank() && !"__channel__".equals(row.entryId())) {
                for (EditableCustomKeyEntry entry : customEntryConfigs) {
                    if (entry == null || !Objects.equals(entry.id, row.entryId())) {
                        continue;
                    }
                    entry.directTarget = null;
                    entry.first = ItemStack.EMPTY;
                    entry.second = ItemStack.EMPTY;
                    sendCustomEntryUpdate(entry);
                    return;
                }
                return;
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config != null) {
                config.directTarget = null;
                config.first = ItemStack.EMPTY;
                config.second = ItemStack.EMPTY;
                sendSingleUpdate(selectedChannel);
            }
        }

        // Get the discovery group tree rows
        private List<GroupTreeRow> discoveryGroupTreeRows() {
            Map<String, List<ControllerDiscoveryNode>> grouped = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String knownGroup : knownDiscoveryGroupIds) {
                if (knownGroup != null && !knownGroup.isBlank() && !isHiddenDiscoveryGroup(knownGroup)) {
                    grouped.putIfAbsent(knownGroup, new ArrayList<>());
                }
            }
            for (ControllerDiscoveryNode node : discoveryNodesForTab()) {
                if (node == null || !node.isValid()) {
                    continue;
                }
                String group = node.groupId();
                if (group == null || group.isBlank() || isHiddenDiscoveryGroup(group)) {
                    continue;
                }
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(node);
            }

            List<GroupTreeRow> rows = new ArrayList<>();
            for (Map.Entry<String, List<ControllerDiscoveryNode>> entry : grouped.entrySet()) {
                String group = entry.getKey();
                boolean expanded = expandedGroupTreeIds.contains(group);
                rows.add(GroupTreeRow.header(group, expanded));
                if (!expanded) {
                    continue;
                }
                List<ControllerDiscoveryNode> members = new ArrayList<>(entry.getValue());
                members.sort(Comparator.comparing(ControllerDiscoveryNode::label));
                for (ControllerDiscoveryNode member : members) {
                    rows.add(GroupTreeRow.member(group, member.nodeId(), member.label()));
                }
            }
            return rows;
        }

        // Check if this is hidden discovery group
        private static boolean isHiddenDiscoveryGroup(String groupId) {
            return groupId != null && groupId.toLowerCase(Locale.ROOT).startsWith("sublevel:");
        }

        // Check if the discovery row is selected
        private boolean isSelectedDiscoveryRow(DiscoveryRow row) {
            if (row == null || row.node() == null) {
                return false;
            }
            return selectedDiscoveryRowKeys.contains(discoveryRowKey(row));
        }

        // Cycle the mode
        private void cycleMode(int dir) {

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec == null) return;
                AnalogueChannelMode[] modes = AnalogueChannelMode.values();
                int idx = (ec.mode.ordinal() + dir + modes.length) % modes.length;
                ec.mode = modes[idx];

                sendCustomEntryUpdate(ec);

                propagateConfigToInputSiblings(ec);
                return;
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null) {
                return;
            }
            AnalogueChannelMode[] modes = AnalogueChannelMode.values();
            int idx = (config.mode.ordinal() + dir + modes.length) % modes.length;
            config.mode = modes[idx];
            sendSingleUpdate(selectedChannel);
        }

        // Cycle the local side
        private void cycleLocalSide(int dir) {

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec == null) return;
                int current = 0;
                for (int i = 0; i < SIDE_OPTIONS.length; i++) {
                    if (SIDE_OPTIONS[i] == ec.localOutputSide) { current = i; break; }
                }
                ec.localOutputSide = SIDE_OPTIONS[(current + dir + SIDE_OPTIONS.length) % SIDE_OPTIONS.length];
                sendCustomEntryUpdate(ec);
                return;
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null) {
                return;
            }
            int current = 0;
            for (int i = 0; i < SIDE_OPTIONS.length; i++) {
                if (SIDE_OPTIONS[i] == config.localOutputSide) {
                    current = i;
                    break;
                }
            }
            config.localOutputSide = SIDE_OPTIONS[(current + dir + SIDE_OPTIONS.length) % SIDE_OPTIONS.length];
            sendSingleUpdate(selectedChannel);
        }

        // Handle the numeric click
        private boolean clickNumeric(double mouseX, double mouseY, int x, int y, int width, double step, NumericField field) {
            boolean left = inside(mouseX, mouseY, x + 54, y, SMALL_BUTTON_W, SMALL_BUTTON_H);
            boolean right = inside(mouseX, mouseY, x + width - SMALL_BUTTON_W, y, SMALL_BUTTON_W, SMALL_BUTTON_H);
            if (left || right) {
                adjustField(field, left ? -step : step);
                return true;
            }
            int valueX = x + 74;
            int valueWidth = Math.max(28, x + width - SMALL_BUTTON_W - valueX - 4);
            if (!inside(mouseX, mouseY, valueX, y, valueWidth, SMALL_BUTTON_H)) {
                return false;
            }
            openNumericEditor(field);
            return true;
        }

        // Adjust the field
        private void adjustField(NumericField field, double delta) {
            setNumericFieldValue(field, numericFieldValue(field) + delta);
        }

        // Open the numeric editor
        private void openNumericEditor(NumericField field) {
            if (minecraft == null) {
                return;
            }
            boolean pct = isPctField(field);
            double scale = pct ? 100.0D : 1.0D;
            String initialValue = editableNumericText(numericFieldValue(field) * scale);
            String maximum = editableNumericText(numericFieldMax(field) * scale);
            String unit = pct ? "%" : "";
            minecraft.setScreen(new ControllerTextInputScreen(
                    AnalogueContraptionControllerConfigScreen.this,
                    Component.literal("Set " + numericFieldLabel(field)),
                    Component.literal("Value (0-" + maximum + unit + ")"),
                    initialValue,
                    text -> {
                        Double val = parseControllerNumericValue(text, pct);
                        if (val != null) {
                            setNumericFieldValue(field, val);
                        }
                    }));
        }

        // Get the numeric field value
        private double numericFieldValue(NumericField field) {
            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry entry = findEditingCustomEntry();
                if (entry == null) {
                    return 0.0D;
                }
                return switch (field) {
                    case RISE_RATE -> entry.riseRate;
                    case FALL_RATE -> entry.fallRate;
                    case STEP_AMOUNT -> entry.stepAmount;
                    case STEP_DOWN_AMOUNT -> entry.stepDownAmount;
                    case DEADZONE -> entry.deadzone;
                    case SMOOTHING -> entry.smoothing;
                };
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null) {
                return 0.0D;
            }
            return switch (field) {
                case RISE_RATE -> config.riseRate;
                case FALL_RATE -> config.fallRate;
                case STEP_AMOUNT, STEP_DOWN_AMOUNT -> config.stepAmount;
                case DEADZONE -> config.deadzone;
                case SMOOTHING -> config.smoothing;
            };
        }

        // Set the numeric field value
        private void setNumericFieldValue(NumericField field, double requestedValue) {
            if (!Double.isFinite(requestedValue)) {
                return;
            }
            double val = Mth.clamp(requestedValue, 0.0D, numericFieldMax(field));

            if (editingCustomEntryId != null) {
                EditableCustomKeyEntry ec = findEditingCustomEntry();
                if (ec == null) return;
                switch (field) {
                    case RISE_RATE -> ec.riseRate = val;
                    case FALL_RATE -> ec.fallRate = val;
                    case STEP_AMOUNT -> ec.stepAmount = val;
                    case STEP_DOWN_AMOUNT -> ec.stepDownAmount = val;
                    case DEADZONE -> ec.deadzone = val;
                    case SMOOTHING -> ec.smoothing = val;
                }
                sendCustomEntryUpdate(ec);

                propagateConfigToInputSiblings(ec);
                return;
            }
            EditableChannelConfig config = channelConfigs.get(selectedChannel);
            if (config == null) {
                return;
            }
            switch (field) {
                case RISE_RATE -> config.riseRate = val;
                case FALL_RATE -> config.fallRate = val;
                case STEP_AMOUNT, STEP_DOWN_AMOUNT -> config.stepAmount = val;
                case DEADZONE -> config.deadzone = val;
                case SMOOTHING -> config.smoothing = val;
            }
            sendSingleUpdate(selectedChannel);
        }

        // Get the numeric field max
        private double numericFieldMax(NumericField field) {
            return switch (field) {
                case DEADZONE -> 0.95D;
                case SMOOTHING -> 0.98D;
                default -> 1.0D;
            };
        }

        // Check if this is a percentage field
        private boolean isPctField(NumericField field) {
            return field != NumericField.RISE_RATE && field != NumericField.FALL_RATE;
        }

        // Get the numeric field label
        private String numericFieldLabel(NumericField field) {
            return switch (field) {
                case RISE_RATE -> "Attack";
                case FALL_RATE -> "Decay";
                case STEP_AMOUNT -> "Step Up";
                case STEP_DOWN_AMOUNT -> "Step Down";
                case DEADZONE -> "Deadzone";
                case SMOOTHING -> "Smooth";
            };
        }
    }

    // Parse the controller numeric value
    static @Nullable Double parseControllerNumericValue(String input, boolean pct) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim();
        boolean percentSuffix = normalized.endsWith("%");
        if (percentSuffix) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            double val = Double.parseDouble(normalized);
            if (!Double.isFinite(val)) {
                return null;
            }
            return pct || percentSuffix ? val / 100.0D : val;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Get the editable numeric text
    private static String editableNumericText(double val) {
        String text = String.format(Locale.ROOT, "%.4f", val);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    // Get the related route rows
    private List<String> relatedRouteRows(EditableCustomKeyEntry src) {
        if (src == null) {
            return List.of();
        }
        List<String> rows = new ArrayList<>();
        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry == null) {
                continue;
            }
            if (!shareBoundInputSource(src, entry)) {
                continue;
            }
            String name = entry.label == null || entry.label.isBlank() ? "Custom" : entry.label;
            String output = routingOutputSummary(entry);
            rows.add(name + " -> " + output);
        }
        return rows;
    }

    // Get the bound input summary
    private String boundInputSummary(EditableCustomKeyEntry src) {
        if (src == null) {
            return "Unbound";
        }
        String input = boundInputSourceSummary(src);
        List<String> routes = relatedRouteRows(src);
        if (routes.isEmpty()) {
            return input;
        }
        if (routes.size() == 1) {
            return input + " -> " + routingOutputSummary(src);
        }
        return input + " -> " + routes.size() + " outputs";
    }

    // Get the bound input source summary
    private static String boundInputSourceSummary(EditableCustomKeyEntry entry) {
        if (entry == null) {
            return "Input";
        }
        if (entry.inputTarget != null && entry.inputTarget.isBound()) {
            return directTargetLabel(entry.inputTarget);
        }
        if (entry.keyCode >= 0) {
            return keyLabel(entry.keyCode);
        }
        if (!entry.inputFirst.isEmpty() && !entry.inputSecond.isEmpty()) {
            return itemName(entry.inputFirst) + " / " + itemName(entry.inputSecond);
        }
        String label = entry.label == null ? "" : entry.label.trim();
        return label.isBlank() ? "Input" : label;
    }

    // Propagate the config to input siblings
    private void propagateConfigToInputSiblings(EditableCustomKeyEntry src) {
        if (src == null) {
            return;
        }
        for (EditableCustomKeyEntry sibling : customEntryConfigs) {
            if (sibling == null || Objects.equals(sibling.id, src.id)) {
                continue;
            }
            if (!shareInputBinding(src, sibling)) {
                continue;
            }
            sibling.mode = src.mode;
            sibling.riseRate = src.riseRate;
            sibling.fallRate = src.fallRate;
            sibling.stepAmount = src.stepAmount;
            sibling.stepDownAmount = src.stepDownAmount;
            sibling.stepDownKeyCode = src.stepDownKeyCode;
            sibling.deadzone = src.deadzone;
            sibling.smoothing = src.smoothing;
            sendCustomEntryUpdate(sibling);
        }
    }

    // Check if the entries share an input binding
    private static boolean shareInputBinding(EditableCustomKeyEntry a, EditableCustomKeyEntry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.inputTarget != null && b.inputTarget != null
                && Objects.equals(a.inputTarget.targetId(), b.inputTarget.targetId())) {
            return true;
        }
        if (a.keyCode >= 0 && a.keyCode == b.keyCode) {
            return true;
        }
        return frequencyPairMatches(a.inputFirst, a.inputSecond, b.inputFirst, b.inputSecond);
    }

    // Check if the entries share a bound input source
    private static boolean shareBoundInputSource(EditableCustomKeyEntry a, EditableCustomKeyEntry b) {
        if (a == null || b == null) {
            return false;
        }
        boolean aHasTarget = a.inputTarget != null && a.inputTarget.isBound();
        boolean bHasTarget = b.inputTarget != null && b.inputTarget.isBound();
        if (aHasTarget || bHasTarget) {
            return aHasTarget && bHasTarget && Objects.equals(a.inputTarget.targetId(), b.inputTarget.targetId());
        }
        return shareInputBinding(a, b);
    }

    // Check if the frequency pairs match
    private static boolean frequencyPairMatches(ItemStack aFirst, ItemStack aSecond, ItemStack bFirst, ItemStack bSecond) {
        return !aFirst.isEmpty() && !aSecond.isEmpty() && !bFirst.isEmpty() && !bSecond.isEmpty()
                && ItemStack.isSameItemSameComponents(aFirst, bFirst)
                && ItemStack.isSameItemSameComponents(aSecond, bSecond);
    }

    // Get the routing output summary
    private static String routingOutputSummary(EditableCustomKeyEntry entry) {
        if (entry == null) {
            return "Unbound";
        }
        if (entry.directTarget != null && entry.directTarget.isBound()) {
            return "Target: " + directTargetLabel(entry.directTarget);
        }
        if (!entry.first.isEmpty() && !entry.second.isEmpty()) {
            return "Link: " + itemName(entry.first) + " / " + itemName(entry.second);
        }
        if (entry.localOutputSide != null) {
            return "Local: " + entry.localOutputSide.getSerializedName();
        }
        return "Unbound";
    }

    // Get the direct target label
    private static String directTargetLabel(ControllerDirectTargetReference target) {
        if (target == null) {
            return "None";
        }
        String label = target.label();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return target.targetId() == null || target.targetId().isBlank() ? "None" : target.targetId();
    }

    // Get the item name
    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        return stack.getHoverName().getString();
    }

    // Check if this has frequency pair
    private static boolean hasFrequencyPair(ItemStack first, ItemStack second) {
        return first != null && !first.isEmpty() && second != null && !second.isEmpty();
    }

    // Get the entry summary
    private String entrySummary(AnalogueControlChannel channel) {
        EditableChannelConfig config = channelConfigs.get(channel);
        if (config == null) {
            return "";
        }
        if (config.directTarget != null) {
            return "Direct";
        }
        if (!config.first.isEmpty() || !config.second.isEmpty()) {
            return "Freq";
        }
        if (config.localOutputSide != null) {
            return config.localOutputSide.getSerializedName();
        }
        return config.mode.name().toLowerCase(Locale.ROOT);
    }

    // Check if this matches target
    private boolean matchesTarget(ControllerDirectTargetReference directTarget, DiscoveryRow row,
                                  AnalogueControlChannel activeChannel) {
        if (directTarget == null || row == null || row.node() == null) {
            return false;
        }
        String targetId = directTarget.targetId();
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        String baseTargetId = optionIndex >= 0 ? targetId.substring(0, optionIndex) : targetId;
        if (!baseTargetId.equals(row.node().nodeId())) {
            return false;
        }
        if (row.group()) {
            return true;
        }
        String optionChannelId = optionIndex >= 0
                ? targetId.substring(optionIndex + DIRECT_TARGET_OPTION_PREFIX.length())
                : "";
        int propertyIndex = optionChannelId.indexOf(DIRECT_TARGET_PROPERTY_PREFIX);
        if (propertyIndex >= 0) {
            optionChannelId = optionChannelId.substring(0, propertyIndex);
        }
        DiscoverySubOption rowOption = row.option();
        AnalogueControlChannel preferred = rowOption == null ? null : rowOption.preferredChannel();
        String targetChannelId = rowOption == null ? "" : rowOption.resolvedTargetChannelId();
        if (optionChannelId.isBlank()) {
            return preferred == null || preferred == activeChannel;
        }
        return !targetChannelId.isBlank() && targetChannelId.equals(optionChannelId);
    }

    // Get the direct target for discovery option
    private ControllerDirectTargetReference directTargetForDiscoveryOption(ControllerDiscoveryNode node,
                                                                           DiscoverySubOption option) {
        if (node == null) {
            return null;
        }

        String targetChannelId = option == null ? "" : option.resolvedTargetChannelId();
        if ((node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_INPUT
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_OUTPUT)
                && (option == null
                || (targetChannelId.isBlank()
                && ContraptionNetworkLinkerData.nodeUsesFaceOptions(node)))) {
            return null;
        }
        ControllerDirectTargetReference base = node.asDirectTargetReference();
        if (targetChannelId.isBlank()) {
            return base;
        }
        String targetId = node.nodeId() + DIRECT_TARGET_OPTION_PREFIX + targetChannelId;
        if (option.signalPropertyKey() != null && !option.signalPropertyKey().isBlank()) {
            targetId = targetId + DIRECT_TARGET_PROPERTY_PREFIX + option.signalPropertyKey().trim().toLowerCase(Locale.ROOT);
        }
        String label = option.label() == null || option.label().isBlank() ? base.label() : option.label();
        return new ControllerDirectTargetReference(targetId, base.targetTypeId(), base.groupId(), label,
                base.subLevelId(), base.blockPos());
    }

    // Get the direct target for main input option
    private ControllerDirectTargetReference directTargetForMainInputOption(MainInputOption option) {
        if (option == null || option.node() == null) {
            return null;
        }
        return directTargetForDiscoveryOption(option.node(), option.asDiscoverySubOption());
    }

    // Get the discovery nodes for tab
    private List<ControllerDiscoveryNode> discoveryNodesForTab() {
        Map<String, ControllerDiscoveryNode> receiveLinksByFrequency = new LinkedHashMap<>();
        List<ControllerDiscoveryNode> filtered = new ArrayList<>();
        for (ControllerDiscoveryNode node : liveDiscoveryNodes) {
            if (node == null || !node.isValid()) {
                continue;
            }
            if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.ANALOG_LEVER
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.THROTTLE
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.ANALOG_TRANSMISSION
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.MAGNET
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.KINETIC
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.MACHINE) {
                continue;
            }
            if (isInputNode(node)) {
                continue;
            }
            if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.REDSTONE_LINK) {
                RedstoneLinkInfo info = resolveRedstoneLinkInfo(node);
                if (info == null || info.transmitter()) {
                    continue;
                }
                if (receiveLinksByFrequency.containsKey(info.frequencyKey())) {
                    continue;
                }
                ControllerDiscoveryNode withFreqLabel = new ControllerDiscoveryNode(
                        node.nodeId(),
                        node.kind(),
                        node.groupId(),
                        node.blockId(),
                        node.label() + " [" + info.frequencyLabel() + "]",
                        node.subLevelId(),
                        node.blockPos());
                if (!shouldIncludeDiscoveryNode(withFreqLabel, info)) {
                    continue;
                }
                receiveLinksByFrequency.put(info.frequencyKey(), withFreqLabel);
                filtered.add(withFreqLabel);
                continue;
            }
            if (!shouldIncludeDiscoveryNode(node, null)) {
                continue;
            }
            filtered.add(node);
        }
        return filtered;
    }

    // Check if this should include discovery node
    private boolean shouldIncludeDiscoveryNode(ControllerDiscoveryNode node, RedstoneLinkInfo linkInfo) {
        DiscoveryFilter filter = modifier.discoveryFilter;
        if (filter == null || filter == DiscoveryFilter.ALL) {
            return true;
        }
        return switch (filter) {
            case PRE_LINKED -> node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.REDSTONE_LINK
                    && linkInfo != null
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_INPUT
                    || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_OUTPUT;
            case BOUND -> isDiscoveryNodeBound(node, linkInfo);
            case UNBOUND -> !isDiscoveryNodeBound(node, linkInfo);
            case DISCOVERED -> modifier.latestDiscoveredNodeIds.contains(node.nodeId());
            case ALL -> true;
        };
    }

    // Check if the discovery node is bound
    private boolean isDiscoveryNodeBound(ControllerDiscoveryNode node, RedstoneLinkInfo linkInfo) {
        if (node == null || !node.isValid()) {
            return false;
        }

        String nodeId = node.nodeId();
        for (EditableChannelConfig config : channelConfigs.values()) {
            if (config == null) {
                continue;
            }
            if (directTargetMatchesNode(config.directTarget, nodeId)) {
                return true;
            }
            if (linkInfo != null && frequencyPairMatches(config.first, config.second, linkInfo.first(), linkInfo.second())) {
                return true;
            }
        }

        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry == null) {
                continue;
            }
            if (directTargetMatchesNode(entry.directTarget, nodeId)) {
                return true;
            }
            if (linkInfo != null && frequencyPairMatches(entry.first, entry.second, linkInfo.first(), linkInfo.second())) {
                return true;
            }
        }
        return false;
    }

    // Check if the direct target matches the node
    private static boolean directTargetMatchesNode(ControllerDirectTargetReference target, String nodeId) {
        if (target == null || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        String targetId = target.targetId();
        if (targetId == null || targetId.isBlank()) {
            return false;
        }
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        String baseTargetId = optionIndex >= 0 ? targetId.substring(0, optionIndex) : targetId;
        return nodeId.equals(baseTargetId);
    }

        // Get the discovery rows for tab
        private List<DiscoveryRow> discoveryRowsForTab(Set<String> expandedGroupIds) {
        List<DiscoveryRow> rows = new ArrayList<>();
        for (ControllerDiscoveryNode node : discoveryNodesForTab()) {
            List<DiscoverySubOption> opts = discoverySubOptionsForNode(node);
            if (opts.size() <= 1) {
            DiscoverySubOption option = opts.isEmpty()
                ? new DiscoverySubOption(node.label(), null, null)
                : opts.get(0);
            rows.add(new DiscoveryRow(node, option, false, false, false));
            continue;
            }

            boolean expanded = expandedGroupIds != null && expandedGroupIds.contains(node.nodeId());
            rows.add(new DiscoveryRow(node, new DiscoverySubOption(node.label(), null, null), true, true, expanded));
            if (expanded) {
            for (DiscoverySubOption option : opts) {
                rows.add(new DiscoveryRow(node, option, false, false, false));
            }
            }
        }
        return rows;
        }

        // Get the discovery sub options for node
        private List<DiscoverySubOption> discoverySubOptionsForNode(ControllerDiscoveryNode node) {
        if (node == null) {
            return List.of();
        }

        String label = node.label() == null || node.label().isBlank() ? "Target" : node.label();
        return switch (node.kind()) {
            case THRUSTER -> List.of(
                new DiscoverySubOption(label + " - Throttle", AnalogueControlChannel.THROTTLE_UP, null));
            case FAN -> List.of(
                new DiscoverySubOption(label + " - Speed", AnalogueControlChannel.THROTTLE_UP, null));
            case BEARING -> List.of(
                new DiscoverySubOption(label + " - CW", AnalogueControlChannel.ROLL_RIGHT, null),
                new DiscoverySubOption(label + " - CCW", AnalogueControlChannel.ROLL_LEFT, null));
            case VECTOR_BEARING -> List.of(
                new DiscoverySubOption(label + " - Forward", AnalogueControlChannel.PITCH_UP, null),
                new DiscoverySubOption(label + " - Backward", AnalogueControlChannel.PITCH_DOWN, null),
                new DiscoverySubOption(label + " - Left", AnalogueControlChannel.ROLL_LEFT, null),
                new DiscoverySubOption(label + " - Right", AnalogueControlChannel.ROLL_RIGHT, null));
            case REDSTONE_LINK -> List.of(
                new DiscoverySubOption(label + " - Frequency", null, null));
            case GYROSCOPE_LINK -> List.of(
                new DiscoverySubOption(label + " - Pitch +", AnalogueControlChannel.PITCH_UP, null),
                new DiscoverySubOption(label + " - Pitch -", AnalogueControlChannel.PITCH_DOWN, null),
                new DiscoverySubOption(label + " - Yaw +", AnalogueControlChannel.YAW_RIGHT, null),
                new DiscoverySubOption(label + " - Yaw -", AnalogueControlChannel.YAW_LEFT, null));
            case JOYSTICK -> List.of(
                new DiscoverySubOption(label + " - Forward", AnalogueControlChannel.PITCH_UP, null),
                new DiscoverySubOption(label + " - Backward", AnalogueControlChannel.PITCH_DOWN, null),
                new DiscoverySubOption(label + " - Left", AnalogueControlChannel.YAW_LEFT, null),
                new DiscoverySubOption(label + " - Right", AnalogueControlChannel.YAW_RIGHT, null));
            case DOUBLE_BUTTON -> List.of(
                new DiscoverySubOption(label + " - Top", AnalogueControlChannel.THROTTLE_UP, "top_powered"),
                new DiscoverySubOption(label + " - Bottom", AnalogueControlChannel.THROTTLE_DOWN, "bottom_powered"));
            case ANALOG_LEVER, THROTTLE, ANALOG_TRANSMISSION, MAGNET, KINETIC, MACHINE -> List.of(
                new DiscoverySubOption(label + " - Value", AnalogueControlChannel.THROTTLE_UP, null));
            case CLAW -> List.of(
                new DiscoverySubOption(label + " - Grip", AnalogueControlChannel.THROTTLE_UP, null));
            case GIMBAL_SENSOR, NAVIGATION_TABLE -> List.of(
                new DiscoverySubOption(label + " - Pitch +", AnalogueControlChannel.PITCH_UP, null),
                new DiscoverySubOption(label + " - Pitch -", AnalogueControlChannel.PITCH_DOWN, null),
                new DiscoverySubOption(label + " - Roll +", AnalogueControlChannel.ROLL_RIGHT, null),
                new DiscoverySubOption(label + " - Roll -", AnalogueControlChannel.ROLL_LEFT, null));
            case STEERING_WHEEL -> List.of(
                new DiscoverySubOption(label + " - Left", AnalogueControlChannel.YAW_LEFT, null),
                new DiscoverySubOption(label + " - Right", AnalogueControlChannel.YAW_RIGHT, null));
            case WHEEL_MOUNT -> List.of(
                new DiscoverySubOption(label + " - Left", AnalogueControlChannel.YAW_LEFT, null),
                new DiscoverySubOption(label + " - Right", AnalogueControlChannel.YAW_RIGHT, null),
                new DiscoverySubOption(label + " - Brake", AnalogueControlChannel.THROTTLE_DOWN, null));
            case LINKER_FACE_INPUT, LINKER_FACE_OUTPUT -> {
                List<DiscoverySubOption> opts = new ArrayList<>();
                for (ContraptionNetworkLinkerData.FaceOption option :
                        ContraptionNetworkLinkerData.faceOptionsForNode(
                                minecraft == null ? null : minecraft.level, node)) {
                    opts.add(new DiscoverySubOption(option.label(), option.optionChannel(),
                            option.signalPropertyKey(), option.targetChannelId()));
                }
                if (opts.isEmpty()) {
                    opts.add(new DiscoverySubOption(label, null, null));
                }
                yield opts;
            }
            case DISPLAY, DISPLAY_ADAPTER, UNKNOWN -> List.of(
                new DiscoverySubOption(label, null, null));
        };
        }

    // Get the detected input nodes
    private List<ControllerDiscoveryNode> getDetectedInputNodes() {
        UUID controllerSubLevelId = getControllerSubLevelId();
        BlockPos controllerPos = getControllerPos();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        Set<String> seen = new HashSet<>();
        List<ControllerDiscoveryNode> nodes = new ArrayList<>();
        for (ControllerDiscoveryNode node : liveDiscoveryNodes) {
            if (!isInputNode(node) || node.blockPos() == null) {
                continue;
            }
            boolean linkerInputNode = node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_INPUT;
            if (!linkerInputNode && !Objects.equals(node.subLevelId(), controllerSubLevelId)) {
                continue;
            }
                if (!linkerInputNode
                    && controllerSubLevelId == null
                    && controllerPos != null) {
                BlockPos pos = node.blockPos();
                if (Math.abs(pos.getX() - controllerPos.getX()) > 16
                        || Math.abs(pos.getY() - controllerPos.getY()) > 16
                        || Math.abs(pos.getZ() - controllerPos.getZ()) > 16) {
                    continue;
                }
            }
            if (!linkerInputNode
                    && (level == null || SimulatedHelper.findBlockEntity(level, node.subLevelId(), node.blockPos()) == null)) {
                continue;
            }
            if (seen.add(node.nodeId())) {
                nodes.add(node);
            }
        }
        nodes.sort(Comparator.comparing(ControllerDiscoveryNode::label));
        return nodes;
    }

    // Get the main input options for node
    private List<MainInputOption> getMainInputOptionsForNode(ControllerDiscoveryNode node) {
        List<MainInputOption> opts = new ArrayList<>();
        if (node == null) {
            return opts;
        }
        String base = node.label() == null || node.label().isBlank() ? "Input" : node.label();
        if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.JOYSTICK) {
            opts.add(new MainInputOption(node, "Forward", AnalogueControlChannel.PITCH_UP, null));
            opts.add(new MainInputOption(node, "Backward", AnalogueControlChannel.PITCH_DOWN, null));
            opts.add(new MainInputOption(node, "Left", AnalogueControlChannel.YAW_LEFT, null));
            opts.add(new MainInputOption(node, "Right", AnalogueControlChannel.YAW_RIGHT, null));
        } else if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.DOUBLE_BUTTON) {
            opts.add(new MainInputOption(node, "Top", AnalogueControlChannel.THROTTLE_UP, "top_powered"));
            opts.add(new MainInputOption(node, "Bottom", AnalogueControlChannel.THROTTLE_DOWN, "bottom_powered"));
        } else if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.GIMBAL_SENSOR
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.NAVIGATION_TABLE) {
            opts.add(new MainInputOption(node, "Pitch +", AnalogueControlChannel.PITCH_UP, null));
            opts.add(new MainInputOption(node, "Pitch -", AnalogueControlChannel.PITCH_DOWN, null));
            opts.add(new MainInputOption(node, "Roll +", AnalogueControlChannel.ROLL_RIGHT, null));
            opts.add(new MainInputOption(node, "Roll -", AnalogueControlChannel.ROLL_LEFT, null));
        } else if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.GYROSCOPE_LINK) {
            opts.add(new MainInputOption(node, "Pitch +", AnalogueControlChannel.PITCH_UP, null));
            opts.add(new MainInputOption(node, "Pitch -", AnalogueControlChannel.PITCH_DOWN, null));
            opts.add(new MainInputOption(node, "Yaw +", AnalogueControlChannel.YAW_RIGHT, null));
            opts.add(new MainInputOption(node, "Yaw -", AnalogueControlChannel.YAW_LEFT, null));
        } else if (node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_INPUT) {
            for (ContraptionNetworkLinkerData.FaceOption option :
                    ContraptionNetworkLinkerData.faceOptionsForNode(
                            minecraft == null ? null : minecraft.level, node)) {
                opts.add(new MainInputOption(node, option.label(), option.optionChannel(),
                        option.signalPropertyKey(), option.targetChannelId()));
            }
            if (opts.isEmpty()) {
                opts.add(new MainInputOption(node, base, null, null));
            }
        } else {
            opts.add(new MainInputOption(node, base, null, null));
        }
        return opts;
    }

    // Get the main input rows
    private List<MainInputRow> getMainInputRows() {
        List<MainInputRow> rows = new ArrayList<>();

        Set<String> usedInputTargetIds = new HashSet<>();
        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry.inputTarget != null && entry.inputTarget.isBound()) {
                String targetId = entry.inputTarget.targetId();
                if (targetId != null && !targetId.isBlank()) {
                    usedInputTargetIds.add(targetId);
                }
            }
        }

        for (ControllerDiscoveryNode node : getDetectedInputNodes()) {
            List<MainInputOption> opts = getMainInputOptionsForNode(node);
            List<MainInputOption> availableOptions = new ArrayList<>();
            for (MainInputOption option : opts) {
                ControllerDirectTargetReference directTarget = directTargetForMainInputOption(option);
                String targetId = directTarget == null ? null : directTarget.targetId();
                if (targetId != null && usedInputTargetIds.contains(targetId)) {
                    continue;
                }
                availableOptions.add(option);
            }

            if (availableOptions.isEmpty()) {
                continue;
            }

            if (opts.size() <= 1) {
                MainInputOption option = availableOptions.get(0);
                rows.add(new MainInputRow(node, option, false, false, false));
                continue;
            }
            boolean expanded = expandedMainInputNodeIds.contains(node.nodeId());
            rows.add(new MainInputRow(node, new MainInputOption(node, node.label(), null, null), true, expanded, false));
            if (expanded) {
                for (MainInputOption option : availableOptions) {
                    rows.add(new MainInputRow(node, option, false, false, true));
                }
            }
        }
        return rows;
    }

    // Get the bound main input entries
    private List<EditableCustomKeyEntry> getBoundMainInputEntries() {

        List<EditableCustomKeyEntry> entries = new ArrayList<>();
        Set<String> seenInputTargetIds = new HashSet<>();
        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry.inputTarget == null || !entry.inputTarget.isBound()) {
                continue;
            }
            String targetId = entry.inputTarget.targetId();
            if (!seenInputTargetIds.add(targetId)) {
                continue;
            }
            entries.add(entry);
        }
        entries.sort(Comparator.comparing(entry -> {
            String label = entry.label;
            return label == null ? "" : label.toLowerCase(Locale.ROOT);
        }));
        return entries;
    }

    // Get the maximum main inputs scroll
    private static int maxMainInputsScroll(List<MainInputRow> opts) {
        return Math.max(0, opts.size() - MAIN_INPUTS_VISIBLE_ROWS);
    }

    // Get the maximum bound inputs scroll
    private static int maxBoundInputsScroll(List<EditableCustomKeyEntry> opts) {
        return Math.max(0, opts.size() - MAIN_BOUND_INPUTS_VISIBLE_ROWS);
    }

    // Get the transmit link nodes deduped
    private List<ControllerDiscoveryNode> getTransmitLinkNodesDeduped() {
        Map<String, ControllerDiscoveryNode> deduped = new LinkedHashMap<>();
        for (ControllerDiscoveryNode node : liveDiscoveryNodes) {
            if (node == null || node.kind() != com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.REDSTONE_LINK) {
                continue;
            }
            RedstoneLinkInfo info = resolveRedstoneLinkInfo(node);
            if (info == null) {
                continue;
            }
            deduped.putIfAbsent(info.frequencyKey(), node);
        }
        return new ArrayList<>(deduped.values());
    }

    // Check if this is an input node
    private static boolean isInputNode(ControllerDiscoveryNode node) {
        if (node == null) {
            return false;
        }
        return node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.JOYSTICK
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.DOUBLE_BUTTON
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.THROTTLE
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.GIMBAL_SENSOR
            || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.GYROSCOPE_LINK
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.NAVIGATION_TABLE
                || node.kind() == com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_INPUT;
    }

    // Check if the discovery row is bound
    private boolean isDiscoveryRowBound(DiscoveryRow row) {
        if (row == null || row.node() == null) {
            return false;
        }
        String nodeId = row.node().nodeId();

        for (EditableChannelConfig config : channelConfigs.values()) {
            if (config.directTarget != null && nodeId.contains(config.directTarget.targetId())) {
                return true;
            }
        }

        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry.directTarget != null && nodeId.contains(entry.directTarget.targetId())) {
                return true;
            }
        }

        return false;
    }

    // Get the binding tooltip for discovery row
    private @Nullable String getBindingTooltipForDiscoveryRow(DiscoveryRow row) {
        if (row == null || row.node() == null) {
            return null;
        }
        String nodeId = row.node().nodeId();
        List<String> bindings = new ArrayList<>();

        for (Map.Entry<AnalogueControlChannel, EditableChannelConfig> entry : channelConfigs.entrySet()) {
            EditableChannelConfig config = entry.getValue();
            if (config.directTarget != null && nodeId.contains(config.directTarget.targetId())) {
                bindings.add("Channel: " + entry.getKey().id());
            }
        }

        for (EditableCustomKeyEntry entry : customEntryConfigs) {
            if (entry.directTarget != null && nodeId.contains(entry.directTarget.targetId())) {
                String label = entry.label == null || entry.label.isBlank() ? entry.id : entry.label;
                bindings.add("Key: " + label);
            }
        }

        if (bindings.isEmpty()) {
            return null;
        }
        return "Bound to: " + String.join(", ", bindings);
    }

    // Get the redstone link display label
    private String redstoneLinkDisplayLabel(ControllerDiscoveryNode node) {
        RedstoneLinkInfo info = resolveRedstoneLinkInfo(node);
        if (info == null) {
            return node == null ? "Link" : node.label();
        }
        return info.frequencyLabel();
    }

    // Resolve the redstone link info
    private RedstoneLinkInfo resolveRedstoneLinkInfo(ControllerDiscoveryNode node) {
        if (node == null || node.blockPos() == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                minecraft.level,
                node.subLevelId(),
                node.blockPos());
        if (blockEntity == null) {
            return null;
        }
        try {
            java.lang.reflect.Field transmitterField = blockEntity.getClass().getDeclaredField("transmitter");
            transmitterField.setAccessible(true);
            boolean transmitter = transmitterField.getBoolean(blockEntity);

            java.lang.reflect.Field linkField = blockEntity.getClass().getDeclaredField("link");
            linkField.setAccessible(true);
            Object link = linkField.get(blockEntity);
            if (link == null) {
                return null;
            }
            java.lang.reflect.Method getNetworkKey = link.getClass().getMethod("getNetworkKey");
            Object couple = getNetworkKey.invoke(link);
            if (couple == null) {
                return null;
            }

            java.lang.reflect.Method getFirst = couple.getClass().getMethod("getFirst");
            java.lang.reflect.Method getSecond = couple.getClass().getMethod("getSecond");
            Object firstFreq = getFirst.invoke(couple);
            Object secondFreq = getSecond.invoke(couple);
            if (firstFreq == null || secondFreq == null) {
                return null;
            }
            java.lang.reflect.Method getStack = firstFreq.getClass().getMethod("getStack");
            ItemStack firstStack = copySingle((ItemStack) getStack.invoke(firstFreq));
            ItemStack secondStack = copySingle((ItemStack) getStack.invoke(secondFreq));
            String firstId = itemId(firstStack);
            String secondId = itemId(secondStack);
            String frequencyKey = firstId + "|" + secondId;
            String firstName = firstStack.isEmpty() ? "Empty" : firstStack.getHoverName().getString();
            String secondName = secondStack.isEmpty() ? "Empty" : secondStack.getHoverName().getString();
            String frequencyLabel = firstName + " / " + secondName;
            return new RedstoneLinkInfo(transmitter, frequencyKey, frequencyLabel, firstStack, secondStack);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Resolve the redstone link freq items
    private LinkFrequencyItems resolveRedstoneLinkFreqItems(ControllerDiscoveryNode node) {
        RedstoneLinkInfo info = resolveRedstoneLinkInfo(node);
        if (info == null) {
            return null;
        }
        return new LinkFrequencyItems(copySingle(info.first()), copySingle(info.second()));
    }

    // Get the item id
    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // Get the direct target summary
    private String directTargetSummary(ControllerDirectTargetReference directTarget) {
        return directTarget == null ? "Unbound" : directTarget.label();
    }

    // Find the editing custom entry
    private EditableCustomKeyEntry findEditingCustomEntry() {
        String id = modifier.editingCustomEntryId;
        if (id == null) return null;
        for (EditableCustomKeyEntry ec : customEntryConfigs) {
            if (id.equals(ec.id)) return ec;
        }
        return null;
    }

    // Handle the cycle click
    private static boolean clickCycle(double mouseX, double mouseY, int x, int y, int width) {
        return inside(mouseX, mouseY, x + 54, y, SMALL_BUTTON_W, SMALL_BUTTON_H)
                || inside(mouseX, mouseY, x + width - SMALL_BUTTON_W, y, SMALL_BUTTON_W, SMALL_BUTTON_H);
    }

    // Check if this is left cycle
    private static boolean isLeftCycle(double mouseX, int x) {
        return mouseX <= x + 70;
    }

    // Store the redstone link info
    private record RedstoneLinkInfo(boolean transmitter, String frequencyKey, String frequencyLabel,
                                    ItemStack first, ItemStack second) {
    }

    // Store the link frequency items
    private record LinkFrequencyItems(ItemStack first, ItemStack second) {
    }

    // Store the main input option
    private record MainInputOption(ControllerDiscoveryNode node, String label,
                                   AnalogueControlChannel preferredChannel,
                                   @Nullable String signalPropertyKey,
                                   @Nullable String targetChannelId) {
        // Initialize the main input option
        private MainInputOption(ControllerDiscoveryNode node, String label,
                                AnalogueControlChannel preferredChannel,
                                @Nullable String signalPropertyKey) {
            this(node, label, preferredChannel, signalPropertyKey,
                    preferredChannel == null ? null : preferredChannel.id());
        }

        // Get the main input option as discovery sub option
        private DiscoverySubOption asDiscoverySubOption() {
            return new DiscoverySubOption(label, preferredChannel, signalPropertyKey, targetChannelId);
        }
    }

    // Store the main input row
    private record MainInputRow(ControllerDiscoveryNode node, MainInputOption option,
                                boolean group, boolean expanded, boolean child) {
    }

    // Store the discovery sub option
    private record DiscoverySubOption(String label, AnalogueControlChannel preferredChannel,
                                      @Nullable String signalPropertyKey,
                                      @Nullable String targetChannelId) {
        // Initialize the discovery sub option
        private DiscoverySubOption(String label, AnalogueControlChannel preferredChannel,
                                   @Nullable String signalPropertyKey) {
            this(label, preferredChannel, signalPropertyKey,
                    preferredChannel == null ? null : preferredChannel.id());
        }

        // Get the resolved target channel id
        private String resolvedTargetChannelId() {
            if (targetChannelId != null && !targetChannelId.isBlank()) {
                return targetChannelId.trim().toLowerCase(Locale.ROOT);
            }
            return preferredChannel == null ? "" : preferredChannel.id();
        }
    }

    // Store the discovery row
    private record DiscoveryRow(ControllerDiscoveryNode node, DiscoverySubOption option,
                                boolean group, boolean expandable, boolean expanded) {
    }

    // Store the group tree row
    private record GroupTreeRow(String groupId, String nodeId, String label,
                                boolean groupHeader, boolean expanded) {
        // Get the header
        private static GroupTreeRow header(String groupId, boolean expanded) {
            return new GroupTreeRow(groupId, "", groupId, true, expanded);
        }

        // Get the member
        private static GroupTreeRow member(String groupId, String nodeId, String label) {
            return new GroupTreeRow(groupId, nodeId, label, false, false);
        }
    }

    // Store the routing target row
    private record RoutingTargetRow(String entryId, String label, boolean changeable) {
    }

    // Store the preset template
    private record PresetTemplate(AnalogueChannelMode mode, double attack, double decay,
                                  double step, double deadzone, double smoothing) {
    }

    // Define the numeric field values
    private enum NumericField {
        RISE_RATE,
        FALL_RATE,
        STEP_AMOUNT,
        STEP_DOWN_AMOUNT,
        DEADZONE,
        SMOOTHING
    }

    // Define the view mode values
    private enum ViewMode {
        MAIN,
        KEY_EDITOR
    }

    // Track the controller bounds
    private static final class Bounds {
        // Current left
        private int left;
        // Current top
        private int top;
        // Current right
        private int right;
        // Current bottom
        private int bottom;

        // Initialize the bounds
        private Bounds(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.right = left + width;
            this.bottom = top + height;
        }

        // Include the bounds
        private void include(int left, int top, int width, int height) {
            this.left = Math.min(this.left, left);
            this.top = Math.min(this.top, top);
            this.right = Math.max(this.right, left + width);
            this.bottom = Math.max(this.bottom, top + height);
        }

        // Get the width
        private int width() {
            return right - left;
        }

        // Get the height
        private int height() {
            return bottom - top;
        }
    }

    // Define the modifier tab values
    private enum ModifierTab {
        CONFIG,
        ROUTING,
        DISCOVERY
    }

    // Define the discovery filter values
    private enum DiscoveryFilter {
        ALL,
        PRE_LINKED,
        BOUND,
        UNBOUND,
        DISCOVERED
    }

    // Store the assigned key entry
    private record AssignedKeyEntry(int keyCode, AnalogueControlChannel channel) {
    }

    // Store the custom key match
    private record CustomKeyMatch(EditableCustomKeyEntry entry, boolean stepDown) {
    }

    // Store editable channel settings
    private static class EditableChannelConfig {
        // Current channel
        private AnalogueControlChannel channel = AnalogueControlChannel.PITCH_UP;
        // Current editable channel mode
        private AnalogueChannelMode mode = AnalogueChannelMode.RAMP;
        // Rise rate
        private double riseRate = 0.08D;
        // Fall rate
        private double fallRate = 0.08D;
        // Current step amount
        private double stepAmount = 0.1D;
        // Current deadzone
        private double deadzone;
        // Current smoothing
        private double smoothing = 0.2D;
        // Local output side
        private Direction localOutputSide;
        // Current first entry
        private ItemStack first = ItemStack.EMPTY;
        // Current second entry
        private ItemStack second = ItemStack.EMPTY;

        // Input first
        private ItemStack inputFirst = ItemStack.EMPTY;
        // Input second
        private ItemStack inputSecond = ItemStack.EMPTY;
        // Current key code
        private int keyCode = -1;
        // Current direct target
        private ControllerDirectTargetReference directTarget;
        // Input target
        private ControllerDirectTargetReference inputTarget;
        // Current binding preset
        private String bindingPreset = "none";

        // Current binding mode
        private String bindingMode = "standard";

        // Check if this is a custom binding
        private boolean isCustomBinding() {
            return "custom".equalsIgnoreCase(bindingMode);
        }
    }

    // Handle the editable custom key entry
    private static class EditableCustomKeyEntry {
        // Current editable custom key entry id
        private String id = "";
        // Current key code
        private int keyCode = -1;
        // Current step down key code
        private int stepDownKeyCode = -1;
        // Current display label
        private String label = "Custom";
        // Current binding preset
        private String bindingPreset = "none";
        // Current editable custom key entry mode
        private AnalogueChannelMode mode = AnalogueChannelMode.RAMP;
        // Rise rate
        private double riseRate = 0.08D;
        // Fall rate
        private double fallRate = 0.08D;
        // Current step amount
        private double stepAmount = 0.1D;
        // Current step down amount
        private double stepDownAmount = 0.1D;
        // Current deadzone
        private double deadzone = 0.0D;
        // Current smoothing
        private double smoothing = 0.2D;
        // Local output side
        private Direction localOutputSide = null;
        // Current first entry
        private ItemStack first = ItemStack.EMPTY;
        // Current second entry
        private ItemStack second = ItemStack.EMPTY;
        // Input first
        private ItemStack inputFirst = ItemStack.EMPTY;
        // Input second
        private ItemStack inputSecond = ItemStack.EMPTY;
        // Current direct target
        private ControllerDirectTargetReference directTarget = null;
        // Input target
        private ControllerDirectTargetReference inputTarget = null;
    }

    // Handle the key row
    private class KeyRow {
        // Tracked keys
        private final List<MainKey> keys = new ArrayList<>();

        // Add the key row
        void add(int width, int keyCode, String label) {
            keys.add(new MainKey(width, keyCode, label));
        }

        // Draw the key row
        void render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
            int offset = 0;
            for (MainKey key : keys) {

                boolean active = findAssignedEntry(key.keyCode) != null || findCustomEntryByKey(key.keyCode) != null;
                boolean hovered = inside(mouseX, mouseY, x + offset, y, key.width, KEY_H);
                renderKey(guiGraphics, x + offset, y, key.width, active, hovered);
                offset += key.width;
            }
        }

        // Find the hit
        MainKey findHit(double mouseX, double mouseY, int x, int y) {
            int offset = 0;
            for (MainKey key : keys) {
                if (inside(mouseX, mouseY, x + offset, y, key.width, KEY_H)) {
                    return key;
                }
                offset += key.width;
            }
            return null;
        }
    }

    // Handle the main key
    private static class MainKey {
        // Main key width
        private final int width;
        // Key code
        private final int keyCode;
        // Display label
        private final String label;

        // Initialize the main key
        private MainKey(int width, int keyCode, String label) {
            this.width = width;
            this.keyCode = keyCode;
            this.label = label;
        }
    }
}
