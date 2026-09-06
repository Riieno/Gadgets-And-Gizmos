package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.VectorBearingConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

// Edit Vector Bearing settings
public class VectorBearingScreen extends AbstractSimiContainerScreen<VectorBearingMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/vector_bearing.png");
    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int MAIN_BACKGROUND_X = 43;
    private static final int MAIN_BACKGROUND_Y = 8;
    private static final int MAIN_BACKGROUND_W = 170;
    private static final int MAIN_BACKGROUND_H = 150;
    private static final int INVENTORY_BACKGROUND_X = 41;
    private static final int INVENTORY_BACKGROUND_Y = 159;
    private static final int INVENTORY_BACKGROUND_W = 173;
    private static final int INVENTORY_BACKGROUND_H = 93;
    private static final int MODE_LEFT_ARROW_X = 77;
    private static final int MODE_RIGHT_ARROW_X = 135;
    private static final int MODE_ARROW_Y = 33;
    private static final int MODE_ARROW_W = 12;
    private static final int MODE_ARROW_H = 14;
    private static final int MODE_LABEL_CENTER_X = 112;
    private static final int MODE_LABEL_Y = 37;
    private static final int MODE_LABEL_H = 5;
    private static final int SLIDER_X = 181;
    private static final int SLIDER_FILL_X = 183;
    private static final int SLIDER_FILL_Y = 58;
    private static final int SLIDER_FILL_W = 13;
    private static final int SLIDER_FILL_H = 56;
    private static final int SLIDER_THUMB_MIN_Y = 54;
    private static final int SLIDER_THUMB_TRAVEL = 50;
    private static final int SLIDER_THUMB_W = 17;
    private static final int SLIDER_THUMB_H = 10;
    private static final int SLIDER_HIT_X = 177;
    private static final int SLIDER_HIT_Y = 54;
    private static final int SLIDER_HIT_W = 25;
    private static final int SLIDER_HIT_H = 60;
    private static final int MAX_TILT_LABEL_CENTER_X = 189;
    private static final int MAX_TILT_LABEL_Y = 117;
    private static final int MAX_TILT_LABEL_W = 50;
    private static final int MAX_TILT_INPUT_X = 175;
    private static final int MAX_TILT_INPUT_Y = 124;
    private static final int MAX_TILT_INPUT_W = 28;
    private static final int MAX_TILT_INPUT_H = 10;
    private static final int MAX_TILT_DEGREE_X = 204;
    private static final double MAX_TILT_DEGREES = 90.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Current control mode
    private VectorBearingBlockEntity.ControlMode controlMode;
    // Max tilt in degrees
    private double maxTiltDegrees;
    // Inline max tilt field
    private CTScaledEditBox maxTiltInput;
    // Tracks whether the max tilt field is being updated from the slider
    private boolean syncingMaxTiltInput;
    // Tracks whether the max tilt slider is being dragged
    private boolean draggingMaxTilt;
    // Tracks whether the local config differs from the server state
    private boolean dirty;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing
    public VectorBearingScreen(VectorBearingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        controlMode = menu.getInitialControlMode();
        maxTiltDegrees = menu.getInitialMaxTiltDegrees();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the vector bearing
    @Override
    protected void init() {
        super.init();
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
        createMaxTiltInput();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        scalableGui.push(guiGraphics);
        try {
            int x = leftPos;
            int y = topPos;
            guiGraphics.blit(BACKGROUND, x + MAIN_BACKGROUND_X, y + MAIN_BACKGROUND_Y,
                    MAIN_BACKGROUND_X, MAIN_BACKGROUND_Y,
                    MAIN_BACKGROUND_W, MAIN_BACKGROUND_H, WIDTH, HEIGHT);
            guiGraphics.blit(BACKGROUND, x + INVENTORY_BACKGROUND_X, y + INVENTORY_BACKGROUND_Y,
                    INVENTORY_BACKGROUND_X, INVENTORY_BACKGROUND_Y,
                    INVENTORY_BACKGROUND_W, INVENTORY_BACKGROUND_H, WIDTH, HEIGHT);
            drawModeLabel(guiGraphics, x, y);
            drawMaxTiltSlider(guiGraphics, x, y);
            guiGraphics.drawCenteredString(font,
                    Component.translatable("createthrusters.vector_bearing.config.max_tilt"),
                    x + MAX_TILT_LABEL_CENTER_X, y + MAX_TILT_LABEL_Y,
                    CTCreateScreenHelper.LABEL_COLOR);
            guiGraphics.drawString(font, Component.literal("\u00B0"),
                    x + MAX_TILT_DEGREE_X, y + MAX_TILT_INPUT_Y + 1,
                    CTCreateScreenHelper.VALUE_COLOR, false);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Create the inline max tilt field
    private void createMaxTiltInput() {
        maxTiltInput = addRenderableWidget(new CTScaledEditBox(scalableGui, font,
                leftPos + MAX_TILT_INPUT_X, topPos + MAX_TILT_INPUT_Y,
                MAX_TILT_INPUT_W, MAX_TILT_INPUT_H,
                Component.translatable("createthrusters.vector_bearing.config.max_tilt")));
        maxTiltInput.setBordered(false);
        maxTiltInput.setMaxLength(4);
        maxTiltInput.setTextColor(CTCreateScreenHelper.VALUE_COLOR);
        maxTiltInput.setFilter(VectorBearingScreen::isMaxTiltInput);
        maxTiltInput.setResponder(this::applyMaxTiltInput);
        syncMaxTiltInput();
    }

    // Draw the selected control mode label
    private void drawModeLabel(GuiGraphics graphics, int x, int y) {
        int sourceX;
        int sourceY;
        int width;
        switch (controlMode) {
            case COMPUTER -> {
                sourceX = 5;
                sourceY = 30;
                width = 38;
            }
            case REDSTONE -> {
                sourceX = 5;
                sourceY = 39;
                width = 38;
            }
            default -> {
                sourceX = 18;
                sourceY = 21;
                width = 25;
            }
        }
        graphics.blit(BACKGROUND, x + MODE_LABEL_CENTER_X - width / 2, y + MODE_LABEL_Y,
                width, MODE_LABEL_H, sourceX, sourceY, width, MODE_LABEL_H, WIDTH, HEIGHT);
    }

    // Draw the max tilt slider
    private void drawMaxTiltSlider(GuiGraphics graphics, int x, int y) {
        double normalized = maxTiltDegrees / MAX_TILT_DEGREES;
        int fillHeight = Mth.clamp((int) Math.round(normalized * SLIDER_FILL_H), 0, SLIDER_FILL_H);
        if (fillHeight > 0) {
            int fillOffset = SLIDER_FILL_H - fillHeight;
            graphics.blit(BACKGROUND, x + SLIDER_FILL_X, y + SLIDER_FILL_Y + fillOffset,
                    SLIDER_FILL_W, fillHeight, 222, 58 + fillOffset,
                    SLIDER_FILL_W, fillHeight, WIDTH, HEIGHT);
        }
        int thumbY = SLIDER_THUMB_MIN_Y
                + (int) Math.round((1.0D - normalized) * SLIDER_THUMB_TRAVEL);
        graphics.blit(BACKGROUND, x + SLIDER_X, y + thumbY,
                SLIDER_THUMB_W, SLIDER_THUMB_H, 220, 46,
                SLIDER_THUMB_W, SLIDER_THUMB_H, WIDTH, HEIGHT);
    }

    // Apply a typed max tilt value
    private void applyMaxTiltInput(String value) {
        if (syncingMaxTiltInput || value.isEmpty()) {
            return;
        }
        maxTiltDegrees = Mth.clamp(Double.parseDouble(value), 0.0D, MAX_TILT_DEGREES);
        dirty = true;
    }

    // Synchronize the inline max tilt field
    private void syncMaxTiltInput() {
        if (maxTiltInput == null || maxTiltInput.isFocused()) {
            return;
        }
        syncingMaxTiltInput = true;
        maxTiltInput.setValue(formatMaxTilt(maxTiltDegrees));
        syncingMaxTiltInput = false;
    }

    // Update the max tilt from a slider pointer position
    private void updateMaxTiltFromSlider(double mouseY) {
        double sliderCenterTop = topPos + SLIDER_THUMB_MIN_Y + SLIDER_THUMB_H / 2.0D;
        double normalized = Mth.clamp((sliderCenterTop + SLIDER_THUMB_TRAVEL - mouseY)
                / SLIDER_THUMB_TRAVEL, 0.0D, 1.0D);
        maxTiltDegrees = Math.round(normalized * MAX_TILT_DEGREES * 10.0D) / 10.0D;
        dirty = true;
        syncMaxTiltInput();
    }

    // Check whether the input can represent a max tilt
    private static boolean isMaxTiltInput(String value) {
        if (value.isEmpty()) {
            return true;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0.0D && parsed <= MAX_TILT_DEGREES;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Format the max tilt for the inline field
    private static String formatMaxTilt(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0) {
            if (inside(mouseX, mouseY, leftPos + MODE_LEFT_ARROW_X, topPos + MODE_ARROW_Y,
                    MODE_ARROW_W, MODE_ARROW_H)) {
                cycleControlMode(-1);
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + MODE_RIGHT_ARROW_X, topPos + MODE_ARROW_Y,
                    MODE_ARROW_W, MODE_ARROW_H)) {
                cycleControlMode(1);
                return true;
            }
            if (inside(mouseX, mouseY,
                    leftPos + MAX_TILT_LABEL_CENTER_X - MAX_TILT_LABEL_W / 2,
                    topPos + MAX_TILT_LABEL_Y, MAX_TILT_LABEL_W, MODE_LABEL_H + 2)) {
                maxTiltInput.setFocused(true);
                maxTiltInput.setCursorPosition(maxTiltInput.getValue().length());
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + SLIDER_HIT_X, topPos + SLIDER_HIT_Y,
                    SLIDER_HIT_W, SLIDER_HIT_H)) {
                maxTiltInput.setFocused(false);
                draggingMaxTilt = true;
                updateMaxTiltFromSlider(mouseY);
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Handle the max tilt slider drag
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0 && draggingMaxTilt) {
            updateMaxTiltFromSlider(mouseY);
            return true;
        }
        return super.mouseDragged(screenMouseX, screenMouseY, btn, dragX, dragY);
    }

    // Finish the max tilt slider drag
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        if (btn == 0 && draggingMaxTilt) {
            draggingMaxTilt = false;
            if (dirty) {
                sendConfig();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Cycle the selected control mode
    private void cycleControlMode(int offset) {
        VectorBearingBlockEntity.ControlMode[] modes = VectorBearingBlockEntity.ControlMode.values();
        int index = Math.floorMod(controlMode.ordinal() + offset, modes.length);
        controlMode = modes[index];
        dirty = true;
        sendConfig();
    }

    // Send the config
    private void sendConfig() {
        if (menu.getContentPos() == null) {
            return;
        }
        dirty = false;
        PacketDistributor.sendToServer(new VectorBearingConfigPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()),
                controlMode,
                maxTiltDegrees,
                copySingle(menu.ghostInventory.getStackInSlot(0)),
                copySingle(menu.ghostInventory.getStackInSlot(1)),
                copySingle(menu.ghostInventory.getStackInSlot(2)),
                copySingle(menu.ghostInventory.getStackInSlot(3)),
                copySingle(menu.ghostInventory.getStackInSlot(4)),
                copySingle(menu.ghostInventory.getStackInSlot(5)),
                copySingle(menu.ghostInventory.getStackInSlot(6)),
                copySingle(menu.ghostInventory.getStackInSlot(7))));
    }

    // Handle the close event
    @Override
    public void onClose() {
        sendConfig();
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

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
