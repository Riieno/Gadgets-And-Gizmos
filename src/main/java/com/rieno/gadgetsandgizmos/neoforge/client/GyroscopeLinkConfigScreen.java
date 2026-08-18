package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkMenu;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogComponent;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.GyroscopeLinkConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.List;

// Edit Gyroscope Link tracking, cardinal outputs and Redstone Link frequencies
public class GyroscopeLinkConfigScreen extends AbstractSimiContainerScreen<GyroscopeLinkMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 244;
    private static final int HEIGHT = 414;
    private static final int TAB_Y = 24;
    private static final int TAB_W = 72;
    private static final int TAB_H = 18;
    private static final int TRACKING_TAB_X = 12;
    private static final int FREQUENCY_TAB_X = 90;
    private static final int MODE_BUTTON_Y = 108;
    private static final int MODE_BUTTON_W = 70;
    private static final int MODE_BUTTON_H = 18;
    private static final int LIVE_BUTTON_X = 22;
    private static final int STATIC_BUTTON_X = 104;
    private static final int RANGE_X = 16;
    private static final int RANGE_WIDTH = WIDTH - 32;
    private static final int RANGE_INPUT_WIDTH = 78;
    private static final int RANGE_INPUT_HEIGHT = 16;
    private static final int SOURCE_COMPONENT_X = 16;
    private static final int SOURCE_COMPONENT_Y = 188;
    private static final int SOURCE_COMPONENT_WIDTH = 190;
    private static final int SOURCE_COMPONENT_HEIGHT = 18;
    private static final int STATE_LABEL_Y = 218;
    private static final int STATE_BUTTON_Y = 232;
    private static final int RANGE_SOURCE_Y = 260;
    private static final int RANGE_OUTPUT_Y = 306;
    private static final int RANGE_CLAMP_Y = 352;
    private static final List<DirectionalAnalogComponent> SOURCE_COMPONENTS =
            List.of(DirectionalAnalogComponent.values());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scaled screen layout
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Tracked cardinal configs
    private final EnumMap<Direction, GyroscopeLinkBlockEntity.CardinalOutputConfig> cardinalConfigs = new EnumMap<>(Direction.class);
    // Tracked minimum inputs
    private final EnumMap<RangeField, EditBox> minimumInputs = new EnumMap<>(RangeField.class);
    // Tracked maximum inputs
    private final EnumMap<RangeField, EditBox> maximumInputs = new EnumMap<>(RangeField.class);
    // Current tab
    private Tab currentTab = Tab.TRACKING;
    // Current tracking mode
    private GyroscopeLinkBlockEntity.TrackingMode trackingMode;
    // Selected direction
    private Direction selectedDirection = Direction.NORTH;
    // Active range field
    private RangeField activeRangeField;
    // Tracks whether min handle is being dragged
    private boolean draggingMinHandle;
    // Active range x
    private int activeRangeX;
    // Active range width
    private int activeRangeWidth;
    // Tracks whether range inputs are being synced
    private boolean syncingRangeInputs;
    // Tracks whether the joystick component dropdown is open
    private boolean sourceComponentDropdownOpen;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link config
    public GyroscopeLinkConfigScreen(GyroscopeLinkMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.trackingMode = menu.getInitialTrackingMode();
        GyroscopeLinkBlockEntity blockEntity = menu.getMenuConfigTargetBlockEntity();
        if (blockEntity != null) {
            cardinalConfigs.put(Direction.NORTH, blockEntity.getCardinalOutputConfig(Direction.NORTH));
            cardinalConfigs.put(Direction.SOUTH, blockEntity.getCardinalOutputConfig(Direction.SOUTH));
            cardinalConfigs.put(Direction.EAST, blockEntity.getCardinalOutputConfig(Direction.EAST));
            cardinalConfigs.put(Direction.WEST, blockEntity.getCardinalOutputConfig(Direction.WEST));
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                cardinalConfigs.put(dir, new GyroscopeLinkBlockEntity.CardinalOutputConfig(
                        GyroscopeLinkBlockEntity.defaultSourceComponent(dir)));
            }
        }
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the gyroscope link config
    @Override
    protected void init() {
        super.init();
        updateScalableGuiBounds();
        createRangeInputs();
        syncRangeInputs();
        updateRangeInputVisibility();
        menu.setFrequencySlotsActive(currentTab == Tab.FREQUENCY);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the range inputs
    private void createRangeInputs() {
        minimumInputs.clear();
        maximumInputs.clear();
        createRangeInputPair(RangeField.SOURCE, RANGE_SOURCE_Y);
        createRangeInputPair(RangeField.OUTPUT, RANGE_OUTPUT_Y);
        createRangeInputPair(RangeField.CLAMP, RANGE_CLAMP_Y);
    }

    // Create the range input pair
    private void createRangeInputPair(RangeField field, int y) {
        EditBox minimum = createRangeInput(field, true, leftPos + RANGE_X, topPos + y + 12);
        EditBox maximum = createRangeInput(field, false,
                leftPos + WIDTH - RANGE_X - RANGE_INPUT_WIDTH, topPos + y + 12);
        minimum.setTextColor(0xFFE06B72);
        maximum.setTextColor(0xFF7191E3);
        minimumInputs.put(field, minimum);
        maximumInputs.put(field, maximum);
    }

    // Create the range input
    private EditBox createRangeInput(RangeField field, boolean minimum, int x, int y) {
        EditBox input = new CTScaledEditBox(scalableGui, font, x, y, RANGE_INPUT_WIDTH, RANGE_INPUT_HEIGHT,
                Component.literal(minimum ? "Minimum angle" : "Maximum angle"));
        input.setMaxLength(12);
        input.setFilter(GyroscopeLinkConfigScreen::isValidAngleInput);
        input.setResponder(val -> applyTypedRangeValue(field, minimum, val));
        return addRenderableWidget(input);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the gyroscope link config
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        updateScalableGuiBounds();
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        if (currentTab != Tab.TRACKING || !menu.hasAnalogueJoystickSource()) {
            return;
        }

        int guiMouseX = scalableGui.mouseX(mouseX);
        int guiMouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
            renderSourceComponentDropdown(guiGraphics, leftPos, topPos, guiMouseX, guiMouseY);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int guiMouseX = scalableGui.mouseX(mouseX);
        int guiMouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
            int x = leftPos;
            int y = topPos;
            CTCreateScreenHelper.renderPanel(guiGraphics, x, y, WIDTH, HEIGHT);
            if (currentTab == Tab.FREQUENCY) {
                renderPlayerInventory(guiGraphics, x + GyroscopeLinkMenu.PLAYER_SLOTS_X - 8, y + GyroscopeLinkMenu.PLAYER_SLOTS_Y - 18);
            }
            guiGraphics.drawCenteredString(font, title, x + WIDTH / 2, y + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);

            renderTab(guiGraphics, x + TRACKING_TAB_X, y + TAB_Y, Component.translatable("createthrusters.gyroscope_link.tab.tracking"),
                    currentTab == Tab.TRACKING, guiMouseX, guiMouseY);
            renderTab(guiGraphics, x + FREQUENCY_TAB_X, y + TAB_Y, Component.translatable("createthrusters.gyroscope_link.tab.frequency"),
                    currentTab == Tab.FREQUENCY, guiMouseX, guiMouseY);
            CTCreateScreenHelper.renderSeparator(guiGraphics, x + 10, y + 48, WIDTH - 20);

            if (currentTab == Tab.TRACKING) {
                renderTrackingTab(guiGraphics, x, y, guiMouseX, guiMouseY);
            } else {
                renderFrequencyTab(guiGraphics, x, y, guiMouseX, guiMouseY);
            }
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the tracking tab
    private void renderTrackingTab(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        GyroscopeLinkBlockEntity.CardinalOutputConfig config = getSelectedConfig();
        boolean linked = menu.getMenuConfigTargetBlockEntity() != null
            ? menu.getMenuConfigTargetBlockEntity().isLinked()
            : menu.getContentPos() != null;
        guiGraphics.drawString(font, Component.translatable("createthrusters.gyroscope_link.config.status"),
                x + 16, y + 58, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, linked ? Component.translatable("createthrusters.gyroscope_link.message.linked")
                        : Component.translatable("createthrusters.gyroscope_link.message.not_linked"),
                x + 16, y + 70, CTCreateScreenHelper.VALUE_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.gyroscope_link.config.mode"),
                x + 16, y + 92, CTCreateScreenHelper.LABEL_COLOR, false);

        renderModeButton(guiGraphics, x + LIVE_BUTTON_X, y + MODE_BUTTON_Y,
                Component.translatable("createthrusters.gyroscope_link.mode.live"),
                trackingMode == GyroscopeLinkBlockEntity.TrackingMode.LIVE, mouseX, mouseY);
        renderModeButton(guiGraphics, x + STATIC_BUTTON_X, y + MODE_BUTTON_Y,
                Component.translatable("createthrusters.gyroscope_link.mode.static"),
                trackingMode == GyroscopeLinkBlockEntity.TrackingMode.STATIC, mouseX, mouseY);

        guiGraphics.drawString(font, Component.translatable("createthrusters.gyroscope_link.config.face"),
            x + 16, y + 136, CTCreateScreenHelper.LABEL_COLOR, false);
        renderFaceButtonRow(guiGraphics, x + 16, y + 150, mouseX, mouseY);

        guiGraphics.drawString(font, Component.translatable("createthrusters.gyroscope_link.config.state"),
            x + 16, y + STATE_LABEL_Y, CTCreateScreenHelper.LABEL_COLOR, false);
        renderToggleButton(guiGraphics, x + 16, y + STATE_BUTTON_Y, 92,
            Component.translatable(config.enabled
                ? "createthrusters.gyroscope_link.config.enabled"
                : "createthrusters.gyroscope_link.config.disabled"),
            config.enabled, mouseX, mouseY);
        renderToggleButton(guiGraphics, x + 114, y + STATE_BUTTON_Y, 92,
            Component.translatable(config.redstoneEnabled
                ? "createthrusters.gyroscope_link.config.redstone_on"
                : "createthrusters.gyroscope_link.config.redstone_off"),
            config.redstoneEnabled, mouseX, mouseY);

        renderRangeGroup(guiGraphics, x + RANGE_X, y + RANGE_SOURCE_Y, mouseX, mouseY,
            Component.translatable("createthrusters.gyroscope_link.config.source_range"),
            config.sourceMinDegrees, config.sourceMaxDegrees, -360.0D, 360.0D, RangeField.SOURCE);
        renderRangeGroup(guiGraphics, x + RANGE_X, y + RANGE_OUTPUT_Y, mouseX, mouseY,
            Component.translatable("createthrusters.gyroscope_link.config.output_range"),
            config.outputMin, config.outputMax, -180.0D, 180.0D, RangeField.OUTPUT);
        renderRangeGroup(guiGraphics, x + RANGE_X, y + RANGE_CLAMP_Y, mouseX, mouseY,
            Component.translatable("createthrusters.gyroscope_link.config.clamp_range"),
            config.clampMin, config.clampMax, -180.0D, 180.0D, RangeField.CLAMP);
    }

    // Draw the joystick component dropdown
    private void renderSourceComponentDropdown(
            GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (!menu.hasAnalogueJoystickSource()) {
            return;
        }

        graphics.drawString(font,
                Component.translatable("createthrusters.gyroscope_link.config.joystick_axis"),
                x + SOURCE_COMPONENT_X, y + SOURCE_COMPONENT_Y - 12,
                CTCreateScreenHelper.LABEL_COLOR, false);
        DirectionalAnalogComponent selected = getSelectedConfig().sourceComponent;
        Component selectedLabel = Component.translatable(sourceComponentTranslationKey(selected));
        CTCreateScreenHelper.renderTextButton(graphics, font,
                x + SOURCE_COMPONENT_X, y + SOURCE_COMPONENT_Y,
                SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT,
                selectedLabel,
                inside(mouseX, mouseY,
                        x + SOURCE_COMPONENT_X, y + SOURCE_COMPONENT_Y,
                        SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT),
                sourceComponentDropdownOpen, false,
                CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, true);

        if (!sourceComponentDropdownOpen) {
            return;
        }
        for (int idx = 0; idx < SOURCE_COMPONENTS.size(); idx++) {
            DirectionalAnalogComponent component = SOURCE_COMPONENTS.get(idx);
            int optionY = y + SOURCE_COMPONENT_Y + SOURCE_COMPONENT_HEIGHT * (idx + 1);
            boolean active = component == selected;
            CTCreateScreenHelper.renderTextButton(graphics, font,
                    x + SOURCE_COMPONENT_X, optionY,
                    SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT,
                    Component.translatable(sourceComponentTranslationKey(component)),
                    inside(mouseX, mouseY,
                            x + SOURCE_COMPONENT_X, optionY,
                            SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT),
                    active, active,
                    CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, true);
        }
    }

    // Handle the joystick component dropdown
    private boolean clickSourceComponentDropdown(double mouseX, double mouseY) {
        if (!menu.hasAnalogueJoystickSource()) {
            sourceComponentDropdownOpen = false;
            return false;
        }

        int x = leftPos + SOURCE_COMPONENT_X;
        int y = topPos + SOURCE_COMPONENT_Y;
        if (inside(mouseX, mouseY, x, y, SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT)) {
            sourceComponentDropdownOpen = !sourceComponentDropdownOpen;
            return true;
        }
        if (!sourceComponentDropdownOpen) {
            return false;
        }

        for (int idx = 0; idx < SOURCE_COMPONENTS.size(); idx++) {
            int optionY = y + SOURCE_COMPONENT_HEIGHT * (idx + 1);
            if (!inside(mouseX, mouseY, x, optionY,
                    SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT)) {
                continue;
            }
            DirectionalAnalogComponent selected = SOURCE_COMPONENTS.get(idx);
            sourceComponentDropdownOpen = false;
            updateSelectedConfig(config -> config.sourceComponent = selected);
            return true;
        }
        sourceComponentDropdownOpen = false;
        return false;
    }

    // Get the addon translation key for a joystick component
    private static String sourceComponentTranslationKey(DirectionalAnalogComponent component) {
        DirectionalAnalogComponent resolved = component == null
                ? DirectionalAnalogComponent.FORWARD : component;
        return "createthrusters.directional_analog." + resolved.id();
    }

        // Draw the face button row
        private void renderFaceButtonRow(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int buttonWidth = 42;
        renderFaceButton(guiGraphics, x, y, buttonWidth, Component.literal("N"), Direction.NORTH, mouseX, mouseY);
        renderFaceButton(guiGraphics, x + 46, y, buttonWidth, Component.literal("S"), Direction.SOUTH, mouseX, mouseY);
        renderFaceButton(guiGraphics, x + 92, y, buttonWidth, Component.literal("E"), Direction.EAST, mouseX, mouseY);
        renderFaceButton(guiGraphics, x + 138, y, buttonWidth, Component.literal("W"), Direction.WEST, mouseX, mouseY);
        }

        // Draw the face button
        private void renderFaceButton(GuiGraphics guiGraphics, int x, int y, int width, Component label,
                      Direction dir, int mouseX, int mouseY) {
        boolean active = selectedDirection == dir;
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, width, 18, label,
            inside(mouseX, mouseY, x, y, width, 18), active, active, CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, true);
        }

        // Draw the toggle button
        private void renderToggleButton(GuiGraphics guiGraphics, int x, int y, int width, Component label,
                        boolean active, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, width, 18, label,
            inside(mouseX, mouseY, x, y, width, 18), active, active,
            CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, true);
        }

        // Draw the range group
        private void renderRangeGroup(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY,
                    Component label,
                    double minValue, double maxValue, double minAllowed, double maxAllowed,
                    RangeField field) {
        guiGraphics.drawString(font, label, x, y, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawCenteredString(font, Component.literal("to"), x + RANGE_WIDTH / 2, y + 16,
                CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
        renderRangeSlider(guiGraphics, x, y + 32, RANGE_WIDTH, 10,
            minValue, maxValue, minAllowed, maxAllowed,
            field, mouseX, mouseY);
        }

        // Draw the range slider
        private void renderRangeSlider(GuiGraphics guiGraphics, int x, int y, int width, int height,
                       double minValue, double maxValue, double minAllowed, double maxAllowed,
                       RangeField field, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        CTCreateScreenHelper.renderInset(guiGraphics, x, y, width, height, hovered, false);
        int trackY = y + height / 2 - 1;
        guiGraphics.fill(x + 2, trackY, x + width - 2, trackY + 2, 0xFF5B4633);
        int minX = valueToSliderX(minValue, minAllowed, maxAllowed, x, width);
        int maxX = valueToSliderX(maxValue, minAllowed, maxAllowed, x, width);
        guiGraphics.fill(minX, trackY - 1, maxX, trackY + 3, 0xFFB77D3C);
        guiGraphics.fill(minX - 2, y + 1, minX + 2, y + height - 1, 0xFF8F2A32);
        guiGraphics.fill(maxX - 2, y + 1, maxX + 2, y + height - 1, 0xFF2D4A92);
        if (activeRangeField == field) {
            guiGraphics.fill(minX - 3, y, minX + 3, y + height, draggingMinHandle ? 0x44FFFFFF : 0x22000000);
            guiGraphics.fill(maxX - 3, y, maxX + 3, y + height, draggingMinHandle ? 0x22000000 : 0x44FFFFFF);
        }
        }

        // Get the value to slider x
        private int valueToSliderX(double val, double minAllowed, double maxAllowed, int x, int width) {
        double normalized = Mth.clamp((val - minAllowed) / Math.max(1.0E-6D, maxAllowed - minAllowed), 0.0D, 1.0D);
        return x + 2 + (int) Math.round(normalized * (width - 4));
        }

        // Get the slider x to value
        private double sliderXToValue(double mouseX, int x, int width, double minAllowed, double maxAllowed) {
        double normalized = Mth.clamp((mouseX - (x + 2)) / Math.max(1.0D, width - 4), 0.0D, 1.0D);
        return Mth.lerp(normalized, minAllowed, maxAllowed);
        }

    // Get the selected config
    private GyroscopeLinkBlockEntity.CardinalOutputConfig getSelectedConfig() {
        return cardinalConfigs.computeIfAbsent(selectedDirection,
            ignored -> {
                GyroscopeLinkBlockEntity blockEntity = menu.getMenuConfigTargetBlockEntity();
                return blockEntity == null
                    ? new GyroscopeLinkBlockEntity.CardinalOutputConfig(
                            GyroscopeLinkBlockEntity.defaultSourceComponent(selectedDirection))
                    : blockEntity.getCardinalOutputConfig(selectedDirection);
            });
        }

        // Update the selected config
        private void updateSelectedConfig(java.util.function.Consumer<GyroscopeLinkBlockEntity.CardinalOutputConfig> mutator) {
        GyroscopeLinkBlockEntity.CardinalOutputConfig config = getSelectedConfig().copy();
        mutator.accept(config);
        cardinalConfigs.put(selectedDirection, config);
        sendConfigUpdate();
        }

        // Check if the point is on range
        private boolean isPointOnRange(double mouseX, double mouseY, int x, int y, int width, int height) {
        return inside(mouseX, mouseY, x, y, width, height);
        }

    // Draw the frequency tab
    private void renderFrequencyTab(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        guiGraphics.drawString(font, Component.translatable("createthrusters.gyroscope_link.config.frequency"),
                x + 16, y + 54, CTCreateScreenHelper.LABEL_COLOR, false);

        renderFrequencyRow(guiGraphics, x, y, 0,
                Component.translatable("createthrusters.gyroscope_link.direction.north"), mouseX, mouseY);
        renderFrequencyRow(guiGraphics, x, y, 1,
                Component.translatable("createthrusters.gyroscope_link.direction.south"), mouseX, mouseY);
        renderFrequencyRow(guiGraphics, x, y, 2,
                Component.translatable("createthrusters.gyroscope_link.direction.east"), mouseX, mouseY);
        renderFrequencyRow(guiGraphics, x, y, 3,
                Component.translatable("createthrusters.gyroscope_link.direction.west"), mouseX, mouseY);
    }

    // Draw the frequency row
    private void renderFrequencyRow(GuiGraphics guiGraphics, int x, int y, int row,
                                    Component label, int mouseX, int mouseY) {
        int slotY = y + GyroscopeLinkMenu.GHOST_SLOTS_START_Y
                + row * GyroscopeLinkMenu.GHOST_SLOT_ROW_SPACING;
        int labelY = slotY + (18 - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, x + 20, labelY, CTCreateScreenHelper.VALUE_COLOR, false);
        renderFrequencySlotPair(guiGraphics, x, slotY, mouseX, mouseY);
    }

    // Draw the frequency slot pair
    private void renderFrequencySlotPair(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int slot1X = x + GyroscopeLinkMenu.GHOST_SLOT_FIRST_X;
        int slot2X = x + GyroscopeLinkMenu.GHOST_SLOT_SECOND_X;
        CTCreateScreenHelper.renderBlockSlots(guiGraphics, slot1X - 10, y - 2, 2);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, slot1X + 1, y + 1,
                inside(mouseX, mouseY, slot1X, y, 18, 18), false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, slot2X + 1, y + 1,
                inside(mouseX, mouseY, slot2X, y, 18, 18), true);
    }

    // Draw the tab
    private void renderTab(GuiGraphics guiGraphics, int x, int y, Component label, boolean active, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, TAB_W, TAB_H, label,
                inside(mouseX, mouseY, x, y, TAB_W, TAB_H), active, false,
                CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, false);
    }

    // Draw the mode button
    private void renderModeButton(GuiGraphics guiGraphics, int x, int y, Component label, boolean active, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x, y, MODE_BUTTON_W, MODE_BUTTON_H, label,
                inside(mouseX, mouseY, x, y, MODE_BUTTON_W, MODE_BUTTON_H), active, active,
                CTCreateScreenHelper.BANNER_TITLE_COLOR, 0x6A6A6A, true);
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double guiMouseX = scalableGui.mouseX(mouseX);
        double guiMouseY = scalableGui.mouseY(mouseY);
        if (currentTab == Tab.TRACKING && btn == 0
                && clickSourceComponentDropdown(guiMouseX, guiMouseY)) {
            return true;
        }
        EditBox clickedRangeInput = currentTab == Tab.TRACKING && btn == 0
                ? findRangeInputAt(guiMouseX, guiMouseY)
                : null;
        if (clickedRangeInput != null && super.mouseClicked(mouseX, mouseY, btn)) {
            clickedRangeInput.setCursorPosition(clickedRangeInput.getValue().length());
            clickedRangeInput.setHighlightPos(0);
            return true;
        }
        if (inside(guiMouseX, guiMouseY, leftPos + TRACKING_TAB_X, topPos + TAB_Y, TAB_W, TAB_H)) {
            currentTab = Tab.TRACKING;
            sourceComponentDropdownOpen = false;
            menu.setFrequencySlotsActive(false);
            updateRangeInputVisibility();
            return true;
        }
        if (inside(guiMouseX, guiMouseY, leftPos + FREQUENCY_TAB_X, topPos + TAB_Y, TAB_W, TAB_H)) {
            currentTab = Tab.FREQUENCY;
            sourceComponentDropdownOpen = false;
            menu.setFrequencySlotsActive(true);
            updateRangeInputVisibility();
            return true;
        }
        if (currentTab == Tab.TRACKING) {
            if (btn != 0) {
                return super.mouseClicked(mouseX, mouseY, btn);
            }
            if (inside(guiMouseX, guiMouseY, leftPos + LIVE_BUTTON_X, topPos + MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H)) {
                trackingMode = GyroscopeLinkBlockEntity.TrackingMode.LIVE;
                sendConfigUpdate();
                return true;
            }
            if (inside(guiMouseX, guiMouseY, leftPos + STATIC_BUTTON_X, topPos + MODE_BUTTON_Y, MODE_BUTTON_W, MODE_BUTTON_H)) {
                trackingMode = GyroscopeLinkBlockEntity.TrackingMode.STATIC;
                sendConfigUpdate();
                return true;
            }
            if (clickFaceButton(guiMouseX, guiMouseY, leftPos + 16, topPos + 150, 42)) {
                syncRangeInputs();
                return true;
            }
            if (clickToggle(guiMouseX, guiMouseY,
                    leftPos + 16, topPos + STATE_BUTTON_Y, 92, true)) {
                return true;
            }
            if (clickToggle(guiMouseX, guiMouseY,
                    leftPos + 114, topPos + STATE_BUTTON_Y, 92, false)) {
                return true;
            }
            if (beginRangeDrag(guiMouseX, guiMouseY, leftPos + RANGE_X, topPos + RANGE_SOURCE_Y + 32, RANGE_WIDTH, 10, RangeField.SOURCE)
                    || beginRangeDrag(guiMouseX, guiMouseY, leftPos + RANGE_X, topPos + RANGE_OUTPUT_Y + 32, RANGE_WIDTH, 10, RangeField.OUTPUT)
                    || beginRangeDrag(guiMouseX, guiMouseY, leftPos + RANGE_X, topPos + RANGE_CLAMP_Y + 32, RANGE_WIDTH, 10, RangeField.CLAMP)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Find the range input
    private EditBox findRangeInputAt(double mouseX, double mouseY) {
        for (EditBox input : minimumInputs.values()) {
            if (input.visible && inside(mouseX, mouseY, input.getX(), input.getY(), input.getWidth(), input.getHeight())) {
                return input;
            }
        }
        for (EditBox input : maximumInputs.values()) {
            if (input.visible && inside(mouseX, mouseY, input.getX(), input.getY(), input.getWidth(), input.getHeight())) {
                return input;
            }
        }
        return null;
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        double guiMouseX = scalableGui.mouseX(mouseX);
        if (currentTab == Tab.TRACKING && btn == 0 && activeRangeField != null) {
            updateActiveRange(guiMouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        boolean wasDragging = activeRangeField != null;
        activeRangeField = null;
        draggingMinHandle = false;
        if (wasDragging) {
            sendConfigUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle the close event
    @Override
    public void onClose() {
        sendConfigUpdate();
        super.onClose();
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
        if (currentTab != Tab.FREQUENCY) {
            return;
        }
        scalableGui.pushFromOrigin(guiGraphics, leftPos, topPos);
        try {
            super.renderSlot(guiGraphics, slot);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the slot highlight
    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (currentTab != Tab.FREQUENCY) {
            return;
        }
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

    // Send the config update
    private void sendConfigUpdate() {
        if (menu.getContentPos() == null) {
            return;
        }
        GyroscopeLinkBlockEntity.CardinalOutputConfig config = getSelectedConfig();
        PacketDistributor.sendToServer(new GyroscopeLinkConfigPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()),
                trackingMode.name().toLowerCase(),
                copySingle(menu.ghostInventory.getStackInSlot(0)),
                copySingle(menu.ghostInventory.getStackInSlot(1)),
                copySingle(menu.ghostInventory.getStackInSlot(2)),
                copySingle(menu.ghostInventory.getStackInSlot(3)),
                copySingle(menu.ghostInventory.getStackInSlot(4)),
                copySingle(menu.ghostInventory.getStackInSlot(5)),
                copySingle(menu.ghostInventory.getStackInSlot(6)),
                copySingle(menu.ghostInventory.getStackInSlot(7)),
                selectedDirection.getSerializedName(),
                config.sourceComponent.id(),
                config.enabled,
                config.redstoneEnabled,
                config.sourceMinDegrees,
                config.sourceMaxDegrees,
                config.outputMin,
                config.outputMax,
                config.clampMin,
                config.clampMax));
    }

    // Handle the face button click
    private boolean clickFaceButton(double mouseX, double mouseY, int x, int y, int width) {
        if (inside(mouseX, mouseY, x, y, width, 18)) {
            selectedDirection = Direction.NORTH;
            sourceComponentDropdownOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + 46, y, width, 18)) {
            selectedDirection = Direction.SOUTH;
            sourceComponentDropdownOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + 92, y, width, 18)) {
            selectedDirection = Direction.EAST;
            sourceComponentDropdownOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + 138, y, width, 18)) {
            selectedDirection = Direction.WEST;
            sourceComponentDropdownOpen = false;
            return true;
        }
        return false;
    }

    // Handle the toggle click
    private boolean clickToggle(double mouseX, double mouseY, int x, int y, int width, boolean enabledToggle) {
        if (!inside(mouseX, mouseY, x, y, width, 18)) {
            return false;
        }
        updateSelectedConfig(config -> {
            if (enabledToggle) {
                config.enabled = !config.enabled;
            } else {
                config.redstoneEnabled = !config.redstoneEnabled;
            }
        });
        return true;
    }

    // Begin the range drag
    private boolean beginRangeDrag(double mouseX, double mouseY, int x, int y, int width, int height, RangeField field) {
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        activeRangeField = field;
        activeRangeX = x;
        activeRangeWidth = width;
        draggingMinHandle = Math.abs(mouseX - valueToSliderX(getRangeValue(field, true), getRangeMin(field), getRangeMax(field), x, width))
                <= Math.abs(mouseX - valueToSliderX(getRangeValue(field, false), getRangeMin(field), getRangeMax(field), x, width));
        updateActiveRange(mouseX);
        return true;
    }

    // Update the active range
    private void updateActiveRange(double mouseX) {
        RangeField field = activeRangeField;
        if (field == null) {
            return;
        }
        int x = activeRangeX;
        int width = activeRangeWidth;
        updateSelectedConfig(config -> {
            double val = sliderXToValue(mouseX, x, width, getRangeMin(field), getRangeMax(field));
            switch (field) {
                case SOURCE -> {
                    if (draggingMinHandle) {
                        config.sourceMinDegrees = Math.min(val, config.sourceMaxDegrees);
                    } else {
                        config.sourceMaxDegrees = Math.max(val, config.sourceMinDegrees);
                    }
                }
                case OUTPUT -> {
                    if (draggingMinHandle) {
                        config.outputMin = Math.min(val, config.outputMax);
                    } else {
                        config.outputMax = Math.max(val, config.outputMin);
                    }
                }
                case CLAMP -> {
                    if (draggingMinHandle) {
                        config.clampMin = Math.min(val, config.clampMax);
                    } else {
                        config.clampMax = Math.max(val, config.clampMin);
                    }
                }
            }
        });
        syncRangeInputs();
    }

    // Apply the typed range value
    private void applyTypedRangeValue(RangeField field, boolean minimum, String text) {
        if (syncingRangeInputs || text == null || text.isBlank() || "-".equals(text)
                || ".".equals(text) || "-.".equals(text)) {
            return;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return;
        }
        double val = Mth.clamp(parsed, getRangeMin(field), getRangeMax(field));
        updateSelectedConfig(config -> setRangeValue(config, field, minimum, val));
        syncCounterpartRangeInput(field, minimum);
    }

    // Set the range value
    private void setRangeValue(GyroscopeLinkBlockEntity.CardinalOutputConfig config, RangeField field,
                               boolean minimum, double val) {
        switch (field) {
            case SOURCE -> {
                if (minimum) {
                    config.sourceMinDegrees = val;
                    config.sourceMaxDegrees = Math.max(config.sourceMaxDegrees, val);
                } else {
                    config.sourceMaxDegrees = val;
                    config.sourceMinDegrees = Math.min(config.sourceMinDegrees, val);
                }
            }
            case OUTPUT -> {
                if (minimum) {
                    config.outputMin = val;
                    config.outputMax = Math.max(config.outputMax, val);
                } else {
                    config.outputMax = val;
                    config.outputMin = Math.min(config.outputMin, val);
                }
            }
            case CLAMP -> {
                if (minimum) {
                    config.clampMin = val;
                    config.clampMax = Math.max(config.clampMax, val);
                } else {
                    config.clampMax = val;
                    config.clampMin = Math.min(config.clampMin, val);
                }
            }
        }
    }

    // Sync the counterpart range input
    private void syncCounterpartRangeInput(RangeField field, boolean editedMinimum) {
        EditBox counterpart = editedMinimum ? maximumInputs.get(field) : minimumInputs.get(field);
        if (counterpart == null) {
            return;
        }
        syncingRangeInputs = true;
        counterpart.setValue(formatInputValue(getRangeValue(field, !editedMinimum)));
        syncingRangeInputs = false;
    }

    // Sync the range inputs
    private void syncRangeInputs() {
        syncingRangeInputs = true;
        for (RangeField field : RangeField.values()) {
            EditBox minimum = minimumInputs.get(field);
            EditBox maximum = maximumInputs.get(field);
            if (minimum != null) {
                minimum.setValue(formatInputValue(getRangeValue(field, true)));
            }
            if (maximum != null) {
                maximum.setValue(formatInputValue(getRangeValue(field, false)));
            }
        }
        syncingRangeInputs = false;
    }

    // Update the range input visibility
    private void updateRangeInputVisibility() {
        boolean visible = currentTab == Tab.TRACKING;
        for (EditBox input : minimumInputs.values()) {
            input.visible = visible;
            input.active = visible;
            if (!visible) {
                input.setFocused(false);
            }
        }
        for (EditBox input : maximumInputs.values()) {
            input.visible = visible;
            input.active = visible;
            if (!visible) {
                input.setFocused(false);
            }
        }
    }

    // Get the range min
    private double getRangeMin(RangeField field) {
        return switch (field) {
            case SOURCE -> -360.0D;
            case OUTPUT, CLAMP -> -180.0D;
        };
    }

    // Get the range max
    private double getRangeMax(RangeField field) {
        return switch (field) {
            case SOURCE -> 360.0D;
            case OUTPUT, CLAMP -> 180.0D;
        };
    }

    // Get the range value
    private double getRangeValue(RangeField field, boolean min) {
        GyroscopeLinkBlockEntity.CardinalOutputConfig config = getSelectedConfig();
        return switch (field) {
            case SOURCE -> min ? config.sourceMinDegrees : config.sourceMaxDegrees;
            case OUTPUT -> min ? config.outputMin : config.outputMax;
            case CLAMP -> min ? config.clampMin : config.clampMax;
        };
    }

    // Update the scalable gui bounds
    private void updateScalableGuiBounds() {
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
    }

    // Check if the angle input is valid
    private static boolean isValidAngleInput(String val) {
        return val == null || val.isEmpty() || val.matches("-?\\d*(\\.\\d*)?");
    }

    // Format the input value
    private static String formatInputValue(double val) {
        return String.format(java.util.Locale.ROOT, "%.1f", val);
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

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Define the range field values
    private enum RangeField {
        SOURCE,
        OUTPUT,
        CLAMP
    }

    // Define the tab values
    private enum Tab {
        TRACKING,
        FREQUENCY
    }
}
