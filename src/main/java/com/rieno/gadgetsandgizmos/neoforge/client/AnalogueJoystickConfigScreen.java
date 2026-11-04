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
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int WIDTH = 354;
    private static final int HEIGHT = 269;
    private static final int SETTINGS_X = 182;
    private static final int SENSITIVITY_Y = 33;
    private static final int DEADZONE_Y = 53;
    private static final int MAX_TILT_Y = 73;
    private static final int RELEASE_MODE_Y = 93;
    private static final int INPUT_MODE_Y = 113;
    private static final int SETTING_BUTTON_X = 91;
    private static final int SETTING_VALUE_X = 105;
    private static final int SETTING_VALUE_WIDTH = 44;
    private static final int SETTING_RIGHT_BUTTON_X = 153;
    private static final int SETTING_BUTTON_Y = 4;
    private static final int SETTING_BUTTON_SIZE = 10;
    private static final int CLEAR_X = 8;
    private static final int CLEAR_Y = 151;
    private static final int CLEAR_WIDTH = 80;
    private static final int CLEAR_HEIGHT = 14;
    private static final int CONFIRM_X = 332;
    private static final int CONFIRM_Y = 149;
    private static final int CONFIRM_SIZE = 18;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/analogue_joystick.png"), WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT);
    private static final GuiNineSlice CLEAR_BUTTON = PaintedGuiStyle.slice(new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/template.png"), 508, 268, 104, 176, 18, 16), 3, 3, 3, 3);
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
    // Channel retained for the footer clear action
    private JoystickChannel activeChannel = JoystickChannel.FORWARD;

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
        try{
            int x = leftPos;
            int y = topPos;
            BACKGROUND.draw(guiGraphics, x, y);
            GuiControlPreviews.drawFittedText(guiGraphics, font, title, x + 8, y + 1,
                    WIDTH - 16, 11, 1, TEXT_COLOR, true);
            JoystickChannel hovered = channelAt(mouseX, mouseY);
            if(hovered != null) activeChannel = hovered;
            renderSettingRow(guiGraphics, Component.translatable("createthrusters.analogue_joystick.sensitivity"),
                    Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", sensitivity)),
                    x + SETTINGS_X, y + SENSITIVITY_Y, false, mouseX, mouseY);
            renderSettingRow(guiGraphics, Component.translatable("createthrusters.analogue_joystick.deadzone"),
                    Component.literal(Math.round(deadzone * 100.0f) + "%"),
                    x + SETTINGS_X, y + DEADZONE_Y, false, mouseX, mouseY);
            renderSettingRow(guiGraphics, Component.translatable("createthrusters.analogue_joystick.max_tilt"),
                    Component.literal(Math.round(maxTiltDegrees) + " deg"),
                    x + SETTINGS_X, y + MAX_TILT_Y, false, mouseX, mouseY);
            renderSettingRow(guiGraphics, Component.translatable("createthrusters.analogue_joystick.release_mode"),
                    Component.translatable(releaseMode.translationKey()),
                    x + SETTINGS_X, y + RELEASE_MODE_Y, true, mouseX, mouseY);
            renderSettingRow(guiGraphics, Component.translatable("createthrusters.analogue_joystick.input_mode"),
                    Component.translatable(inputMode.translationKey()),
                    x + SETTINGS_X, y + INPUT_MODE_Y, true, mouseX, mouseY);
            CLEAR_BUTTON.draw(guiGraphics, x + CLEAR_X, y + CLEAR_Y, CLEAR_WIDTH, CLEAR_HEIGHT);
            if(inside(mouseX, mouseY, x + CLEAR_X, y + CLEAR_Y, CLEAR_WIDTH, CLEAR_HEIGHT)){
                guiGraphics.fill(x + CLEAR_X + 1, y + CLEAR_Y + 1, x + CLEAR_X + CLEAR_WIDTH - 1,
                        y + CLEAR_Y + CLEAR_HEIGHT - 1, 0x22FFFFFF);
            }
            GuiControlPreviews.drawFittedText(guiGraphics, font,
                    Component.translatable("createthrusters.analogue_joystick.config.clear_channel",
                            Component.translatable(activeChannel.getTranslationKey())),
                    x + CLEAR_X + 3, y + CLEAR_Y + 1, CLEAR_WIDTH - 6, CLEAR_HEIGHT - 2, 1, TEXT_COLOR, true);
            if(inside(mouseX, mouseY, x + CONFIRM_X, y + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                guiGraphics.fill(x + CONFIRM_X, y + CONFIRM_Y, x + CONFIRM_X + CONFIRM_SIZE,
                        y + CONFIRM_Y + CONFIRM_SIZE, 0x22FFFFFF);
            }
            if(inside(mouseX, mouseY, x + 64, y + 54, 50, 54)){
                guiGraphics.renderTooltip(font, List.of(
                        Component.translatable("createthrusters.analogue_joystick.config.hint"),
                        Component.translatable("createthrusters.analogue_joystick.config.ghost_hint")),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
        }finally{
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw labels through the painted layout
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY){
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
            if(inside(mouseX, mouseY, leftPos + CONFIRM_X, topPos + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                onClose();
                return true;
            }
            if(inside(mouseX, mouseY, leftPos + CLEAR_X, topPos + CLEAR_Y, CLEAR_WIDTH, CLEAR_HEIGHT)){
                clearChannel(activeChannel);
                dirty = true;
                sendUpdate();
                return true;
            }
            JoystickChannel channel = channelAt(mouseX, mouseY);
            if(channel != null) activeChannel = channel;

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

    // Draw labels and glyphs over the atlas's existing settings controls
    private void renderSettingRow(GuiGraphics guiGraphics, Component label, Component val, int x, int y,
                                  boolean cycle, int mouseX, int mouseY){
        GuiControlPreviews.drawFittedText(guiGraphics, font, label, x + 4, y + 2, 74, 13, 1, TEXT_COLOR, false);
        GuiControlPreviews.drawFittedText(guiGraphics, font, val, x + SETTING_VALUE_X, y + 5,
                SETTING_VALUE_WIDTH, 8, 0.8F, TEXT_COLOR, true);
        renderSettingButton(guiGraphics, x + SETTING_BUTTON_X, y + SETTING_BUTTON_Y,
                cycle ? "<" : "-", mouseX, mouseY);
        renderSettingButton(guiGraphics, x + SETTING_RIGHT_BUTTON_X, y + SETTING_BUTTON_Y,
                cycle ? ">" : "+", mouseX, mouseY);
    }

    // Shade a baked step or cycle button without covering its painted border
    private void renderSettingButton(GuiGraphics guiGraphics, int x, int y, String label, int mouseX, int mouseY){
        if(inside(mouseX, mouseY, x, y, SETTING_BUTTON_SIZE, SETTING_BUTTON_SIZE)){
            guiGraphics.fill(x + 1, y + 1, x + SETTING_BUTTON_SIZE - 1, y + SETTING_BUTTON_SIZE - 1, 0x22FFFFFF);
        }
        GuiControlPreviews.drawFittedText(guiGraphics, font, Component.literal(label), x + 1, y + 1,
                SETTING_BUTTON_SIZE - 2, SETTING_BUTTON_SIZE - 2, 0.8F, TEXT_COLOR, true);
    }

    // Keep the footer clear action tied to the last hovered or clicked frequency pair
    private JoystickChannel channelAt(double mouseX, double mouseY){
        for(JoystickChannel channel : JoystickChannel.values()){
            for(int idx = 0; idx < 2; idx++){
                int x = leftPos + AnalogueJoystickMenu.frequencySlotX(channel, idx == 1);
                int y = topPos + AnalogueJoystickMenu.frequencySlotY(channel, idx == 1);
                if(inside(mouseX, mouseY, x - 4, y - 4, 24, 24)) return channel;
            }
        }
        return null;
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
        return inside(mouseX, mouseY, minus ? x : x + (SETTING_RIGHT_BUTTON_X - SETTING_BUTTON_X),
                y + SETTING_BUTTON_Y, SETTING_BUTTON_SIZE, SETTING_BUTTON_SIZE);
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
