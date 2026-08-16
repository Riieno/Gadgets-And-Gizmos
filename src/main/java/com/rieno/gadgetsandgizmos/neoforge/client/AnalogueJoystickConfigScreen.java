package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity.JoystickChannel;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity.InputMode;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity.ReleaseMode;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickConfigSnapshot;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickConfigPayload;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.client.renderer.Rect2i;

import java.util.EnumMap;
import java.util.List;

// Edit joystick channels, sensitivity, deadzone, tilt and release behavior
public class AnalogueJoystickConfigScreen extends AbstractSimiContainerScreen<AnalogueJoystickMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 296;
    private static final int HEIGHT = 344;
    private static final int CONTENT_SHIFT_X = 15;
    private static final int CHANNEL_ROW_X = 10 + CONTENT_SHIFT_X;
    private static final int CHANNEL_ROW_Y = 34;
    private static final int CHANNEL_ROW_WIDTH = 229;
    private static final int CHANNEL_ROW_HEIGHT = 20;
    private static final int CHANNEL_ROW_SPACING = 24;
    private static final int INVENTORY_BG_X = AnalogueJoystickMenu.PLAYER_SLOTS_X - 8;
    private static final int INVENTORY_BG_Y = AnalogueJoystickMenu.PLAYER_SLOTS_Y - 18;
    private static final int PREVIEW_FIRST_X = 154 + CONTENT_SHIFT_X;
    private static final int PREVIEW_SECOND_X = 174 + CONTENT_SHIFT_X;
    private static final int PREVIEW_Y_OFFSET = 1;
    private static final int CLEAR_BUTTON_X = 198 + CONTENT_SHIFT_X;
    private static final int CLEAR_BUTTON_WIDTH = 18;
    private static final int SETTINGS_X = 10 + CONTENT_SHIFT_X;
    private static final int SENSITIVITY_Y = 136;
    private static final int DEADZONE_Y = 158;
    private static final int MAX_TILT_Y = 180;
    private static final int RELEASE_MODE_Y = 202;
    private static final int INPUT_MODE_Y = 224;
    private static final int SETTINGS_SEPARATOR_Y = 246;
    private static final int SETTING_BUTTON_X = 135;
    private static final int SETTING_VALUE_X = 155;
    private static final int SETTING_VALUE_WIDTH = 62;
    private static final int SETTING_RIGHT_BUTTON_X = SETTING_VALUE_X + SETTING_VALUE_WIDTH + 8;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final AnalogueJoystickBlockEntity blockEntity;
    // Tracked first frequencies
    private final EnumMap<JoystickChannel, ItemStack> firstFrequencies = new EnumMap<>(JoystickChannel.class);
    // Tracked second frequencies
    private final EnumMap<JoystickChannel, ItemStack> secondFrequencies = new EnumMap<>(JoystickChannel.class);
    // Current sensitivity
    private float sensitivity;
    // Current deadzone
    private float deadzone;
    // Max tilt in degrees
    private float maxTiltDegrees;
    // Current release mode
    private ReleaseMode releaseMode;
    // Current player input mode
    private InputMode inputMode;
    // Tracks whether analogue joystick is dirty
    private boolean dirty;

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick config
    public AnalogueJoystickConfigScreen(AnalogueJoystickMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.blockEntity = menu.contentHolder;
        this.sensitivity = menu.getInitialSensitivity();
        this.deadzone = menu.getInitialDeadzone();
        this.maxTiltDegrees = menu.getInitialMaxTiltDegrees();
        this.releaseMode = menu.getInitialReleaseMode();
        this.inputMode = menu.getInitialInputMode();

        if (blockEntity != null) {
            for (JoystickChannel channel : JoystickChannel.values()) {
                firstFrequencies.put(channel, blockEntity.getFrequencyFirst(channel));
                secondFrequencies.put(channel, blockEntity.getFrequencySecond(channel));
            }
        } else {

            for (JoystickChannel channel : JoystickChannel.values()) {
                firstFrequencies.put(channel, ItemStack.EMPTY);
                secondFrequencies.put(channel, ItemStack.EMPTY);
            }
        }
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the analogue joystick config
    @Override
    protected void init() {
        super.init();
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);

        syncAllChannelsFromMenu();
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
        syncAllChannelsFromMenu();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the close event
    @Override
    public void onClose() {
        syncAllChannelsFromMenu();
        sendUpdate();
        super.onClose();
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
        int x = leftPos;
        int y = topPos;
        CTCreateScreenHelper.renderPanel(guiGraphics, x, y, WIDTH, HEIGHT);

        renderPlayerInventory(guiGraphics, x + INVENTORY_BG_X, y + INVENTORY_BG_Y);
        CTCreateScreenHelper.renderSeparator(guiGraphics, x + 4, y + 28, WIDTH - 8);
        CTCreateScreenHelper.renderSeparator(guiGraphics, x + 4, y + 130, WIDTH - 8);
        CTCreateScreenHelper.renderSeparator(guiGraphics, x + 4, y + SETTINGS_SEPARATOR_Y, WIDTH - 8);

        guiGraphics.drawString(font, title, x + (WIDTH - font.width(title)) / 2, y + 5,
                CTCreateScreenHelper.BANNER_TITLE_COLOR, false);
        guiGraphics.drawString(font,
                Component.translatable("createthrusters.analogue_joystick.config.hint").copy().withStyle(ChatFormatting.GRAY),
                x + 12 + CONTENT_SHIFT_X, y + 18, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);

        int rowY = y + CHANNEL_ROW_Y;
        for (JoystickChannel channel : JoystickChannel.values()) {
            renderChannelRow(guiGraphics, channel, x + CHANNEL_ROW_X, rowY, mouseX, mouseY);
            rowY += CHANNEL_ROW_SPACING;
        }

        renderSettingRow(guiGraphics,
            Component.translatable("createthrusters.analogue_joystick.sensitivity"),
            Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", sensitivity)), x + SETTINGS_X, y + SENSITIVITY_Y);
        renderSettingRow(guiGraphics,
                Component.translatable("createthrusters.analogue_joystick.deadzone"),
            Component.literal(Math.round(deadzone * 100.0f) + "%"), x + SETTINGS_X, y + DEADZONE_Y);
        renderSettingRow(guiGraphics,
                Component.translatable("createthrusters.analogue_joystick.max_tilt"),
            Component.literal(Math.round(maxTiltDegrees) + " deg"), x + SETTINGS_X, y + MAX_TILT_Y);
        renderCycleSettingRow(guiGraphics,
            Component.translatable("createthrusters.analogue_joystick.release_mode"),
            Component.translatable(releaseMode.translationKey()), x + SETTINGS_X, y + RELEASE_MODE_Y);
        renderCycleSettingRow(guiGraphics,
                Component.translatable("createthrusters.analogue_joystick.input_mode"),
                Component.translatable(inputMode.translationKey()), x + SETTINGS_X, y + INPUT_MODE_Y);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the foreground
    @Override
    protected void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }

    // Draw the analogue joystick config
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    // Get the extra areas
    @Override
    public List<Rect2i> getExtraAreas() {
        return scalableGui.extraAreas();
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0) {
            int rowY = topPos + CHANNEL_ROW_Y;
            for (JoystickChannel channel : JoystickChannel.values()) {
                if (inside(mouseX, mouseY, leftPos + CLEAR_BUTTON_X, rowY, CLEAR_BUTTON_WIDTH, CHANNEL_ROW_HEIGHT)) {
                    clearChannel(channel);

                    dirty = true;
                    sendUpdate();
                    return true;
                }
                rowY += CHANNEL_ROW_SPACING;
            }

            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + DEADZONE_Y, false)) {
                deadzone = Mth.clamp(deadzone + 0.02f, 0.0f, 0.95f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + SENSITIVITY_Y, false)) {
                sensitivity = Mth.clamp(sensitivity + 0.05f, 0.05f, 3.0f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + SENSITIVITY_Y, true)) {
                sensitivity = Mth.clamp(sensitivity - 0.05f, 0.05f, 3.0f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + DEADZONE_Y, true)) {
                deadzone = Mth.clamp(deadzone - 0.02f, 0.0f, 0.95f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + MAX_TILT_Y, true)) {
                maxTiltDegrees = Mth.clamp(maxTiltDegrees - 1.0f, 5.0f, 60.0f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + MAX_TILT_Y, false)) {
                maxTiltDegrees = Mth.clamp(maxTiltDegrees + 1.0f, 5.0f, 60.0f);
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + RELEASE_MODE_Y, true)
                    || clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + RELEASE_MODE_Y, false)) {
                releaseMode = releaseMode == ReleaseMode.MOMENTARY ? ReleaseMode.LATCHED : ReleaseMode.MOMENTARY;
                dirty = true;
                sendUpdate();
                return true;
            }
            if (clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + INPUT_MODE_Y, true)
                    || clickSetting(mouseX, mouseY, leftPos + SETTINGS_X + SETTING_BUTTON_X, topPos + INPUT_MODE_Y, false)) {
                inputMode = inputMode == InputMode.MOUSE ? InputMode.GAMEPAD : InputMode.MOUSE;
                dirty = true;
                sendUpdate();
                return true;
            }
        }

        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Draw the channel row
    private void renderChannelRow(GuiGraphics guiGraphics, JoystickChannel channel, int x, int y, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderInset(guiGraphics, x, y, CHANNEL_ROW_WIDTH, CHANNEL_ROW_HEIGHT,
                inside(mouseX, mouseY, x, y, CHANNEL_ROW_WIDTH, CHANNEL_ROW_HEIGHT), false);
        guiGraphics.drawString(font, Component.translatable(channel.getTranslationKey()), x + 7, y + 6,
                CTCreateScreenHelper.LABEL_COLOR, false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, leftPos + PREVIEW_FIRST_X + 1, y + PREVIEW_Y_OFFSET + 1, false, false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, leftPos + PREVIEW_SECOND_X + 1, y + PREVIEW_Y_OFFSET + 1, false, true);
        CTCreateScreenHelper.renderIconButton(guiGraphics, leftPos + CLEAR_BUTTON_X, y + 1, AllIcons.I_CONFIG_RESET,
                inside(mouseX, mouseY, leftPos + CLEAR_BUTTON_X, y + 1, CLEAR_BUTTON_WIDTH, CHANNEL_ROW_HEIGHT - 2),
                true, false);
    }

    // Draw the setting row
    private void renderSettingRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x + SETTING_BUTTON_X, y, 16, 16, Component.literal("-"), false, true, false);
        CTCreateScreenHelper.renderInset(guiGraphics, x + SETTING_VALUE_X, y, SETTING_VALUE_WIDTH, 16, false, false);
        guiGraphics.drawCenteredString(font, val, x + SETTING_VALUE_X + SETTING_VALUE_WIDTH / 2, y + 4, CTCreateScreenHelper.VALUE_COLOR);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x + SETTING_RIGHT_BUTTON_X, y, 16, 16, Component.literal("+"), false, true, false);
    }

    // Draw the cycle setting row
    private void renderCycleSettingRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y) {
        guiGraphics.drawString(font, label, x, y + 4, CTCreateScreenHelper.LABEL_COLOR, false);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x + SETTING_BUTTON_X, y, 16, 16, Component.literal("<"), false, true, false);
        CTCreateScreenHelper.renderInset(guiGraphics, x + SETTING_VALUE_X, y, SETTING_VALUE_WIDTH, 16, false, false);
        guiGraphics.drawCenteredString(font, val, x + SETTING_VALUE_X + SETTING_VALUE_WIDTH / 2, y + 4, CTCreateScreenHelper.VALUE_COLOR);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, x + SETTING_RIGHT_BUTTON_X, y, 16, 16, Component.literal(">"), false, true, false);
    }

    // Clear the channel
    private void clearChannel(JoystickChannel channel) {
        firstFrequencies.put(channel, ItemStack.EMPTY);
        secondFrequencies.put(channel, ItemStack.EMPTY);
        menu.ghostInventory.setStackInSlot(slotIndex(channel, false), ItemStack.EMPTY);
        menu.ghostInventory.setStackInSlot(slotIndex(channel, true), ItemStack.EMPTY);
    }

    // Sync all channels from the menu
    private void syncAllChannelsFromMenu() {
        boolean changed = false;
        for (JoystickChannel channel : JoystickChannel.values()) {
            ItemStack first = copySingle(menu.ghostInventory.getStackInSlot(slotIndex(channel, false)));
            ItemStack second = copySingle(menu.ghostInventory.getStackInSlot(slotIndex(channel, true)));
            ItemStack previousFirst = firstFrequencies.get(channel);
            ItemStack previousSecond = secondFrequencies.get(channel);
            if (!ItemStack.isSameItemSameComponents(previousFirst, first)
                    || !ItemStack.isSameItemSameComponents(previousSecond, second)) {
                firstFrequencies.put(channel, first);
                secondFrequencies.put(channel, second);
                changed = true;
            }
        }
        if (changed) {

            dirty = true;
            sendUpdate();
        }
    }

    // Get the slot index
    private static int slotIndex(JoystickChannel channel, boolean second) {
        return channel.ordinal() * 2 + (second ? 1 : 0);
    }

    // Send the update
    private void sendUpdate() {
        if (!dirty) {
            return;
        }
        if (menu.getContentPos() == null) {
            return;
        }
        dirty = false;
        PacketDistributor.sendToServer(new AnalogueJoystickConfigPayload(
            MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()),
                new AnalogueJoystickConfigSnapshot(
                        copySingle(firstFrequencies.get(JoystickChannel.FORWARD)),
                        copySingle(secondFrequencies.get(JoystickChannel.FORWARD)),
                        copySingle(firstFrequencies.get(JoystickChannel.BACKWARD)),
                        copySingle(secondFrequencies.get(JoystickChannel.BACKWARD)),
                        copySingle(firstFrequencies.get(JoystickChannel.LEFT)),
                        copySingle(secondFrequencies.get(JoystickChannel.LEFT)),
                        copySingle(firstFrequencies.get(JoystickChannel.RIGHT)),
                        copySingle(secondFrequencies.get(JoystickChannel.RIGHT)),
                        releaseMode == ReleaseMode.MOMENTARY,
                        sensitivity,
                        deadzone,
                        maxTiltDegrees,
                        inputMode == InputMode.GAMEPAD)));
    }

    // Handle the setting click
    private boolean clickSetting(double mouseX, double mouseY, int x, int y, boolean minus) {
        return inside(mouseX, mouseY, minus ? x : x + (SETTING_RIGHT_BUTTON_X - SETTING_BUTTON_X), y, 16, 16);
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
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Draw the slot
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
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
}
