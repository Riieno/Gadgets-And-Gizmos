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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

    private static final int WIDTH = 260;
    private static final int HEIGHT = 252;
    private static final int MODE_Y = 36;
    private static final int MODE_X = 42;
    private static final int MODE_W = 56;
    private static final int MODE_H = 14;
    private static final int MODE_GAP = 10;
    private static final int TILT_Y = 58;
    private static final int TILT_BUTTON_W = 18;
    private static final int TILT_VALUE_W = 58;
    private static final int TILT_X = 140;
    private static final int LABEL_X = 32;
    private static final int SLOT_LABEL_X = 58;

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
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

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
            renderPlayerInventory(guiGraphics, x + VectorBearingMenu.PLAYER_SLOTS_X - 8,
                    y + VectorBearingMenu.PLAYER_SLOTS_Y - 18);
            guiGraphics.drawCenteredString(font, title, x + WIDTH / 2, y + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);
            drawModeButtons(guiGraphics, x, y, mouseX, mouseY);
            drawMaxTilt(guiGraphics, x, y, mouseX, mouseY);
            drawFrequencyRows(guiGraphics, x, y, mouseX, mouseY);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the mode buttons
    private void drawModeButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("createthrusters.vector_bearing.config.mode"),
                x + LABEL_X, y + MODE_Y + 3, CTCreateScreenHelper.LABEL_COLOR, false);
        drawModeButton(graphics, x, y, mouseX, mouseY, 0, VectorBearingBlockEntity.ControlMode.AUTO);
        drawModeButton(graphics, x, y, mouseX, mouseY, 1, VectorBearingBlockEntity.ControlMode.COMPUTER);
        drawModeButton(graphics, x, y, mouseX, mouseY, 2, VectorBearingBlockEntity.ControlMode.REDSTONE);
    }

    // Draw the mode button
    private void drawModeButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, int idx,
                                VectorBearingBlockEntity.ControlMode mode) {
        int bx = x + MODE_X + idx * (MODE_W + MODE_GAP);
        int by = y + MODE_Y;
        boolean hovered = inside(mouseX, mouseY, bx, by, MODE_W, MODE_H);
        CTCreateScreenHelper.renderTextButton(graphics, font, bx, by, MODE_W, MODE_H,
                Component.translatable("createthrusters.vector_bearing.mode." + mode.name().toLowerCase(Locale.ROOT)),
                hovered, controlMode == mode, true);
    }

    // Draw the max tilt
    private void drawMaxTilt(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("createthrusters.vector_bearing.config.max_tilt"),
                x + LABEL_X, y + TILT_Y + 3, CTCreateScreenHelper.LABEL_COLOR, false);
        int minusX = x + TILT_X;
        int valueX = minusX + TILT_BUTTON_W + 4;
        int plusX = valueX + TILT_VALUE_W + 4;
        CTCreateScreenHelper.renderTextButton(graphics, font, minusX, y + TILT_Y, TILT_BUTTON_W, MODE_H,
                Component.literal("-"), inside(mouseX, mouseY, minusX, y + TILT_Y, TILT_BUTTON_W, MODE_H), true, true);
        CTCreateScreenHelper.renderTextButton(graphics, font, valueX, y + TILT_Y, TILT_VALUE_W, MODE_H,
                Component.literal(String.format(Locale.ROOT, "%.0f deg", maxTiltDegrees)), false, true, true);
        CTCreateScreenHelper.renderTextButton(graphics, font, plusX, y + TILT_Y, TILT_BUTTON_W, MODE_H,
                Component.literal("+"), inside(mouseX, mouseY, plusX, y + TILT_Y, TILT_BUTTON_W, MODE_H), true, true);
    }

    // Draw the frequency rows
    private void drawFrequencyRows(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawFrequencyRow(graphics, x, y, mouseX, mouseY, Direction.NORTH, VectorBearingMenu.NORTH_Y);
        drawFrequencyRow(graphics, x, y, mouseX, mouseY, Direction.SOUTH, VectorBearingMenu.SOUTH_Y);
        drawFrequencyRow(graphics, x, y, mouseX, mouseY, Direction.EAST, VectorBearingMenu.EAST_Y);
        drawFrequencyRow(graphics, x, y, mouseX, mouseY, Direction.WEST, VectorBearingMenu.WEST_Y);
    }

    // Draw the frequency row
    private void drawFrequencyRow(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                                  Direction dir, int rowY) {
        graphics.drawString(font, directionLabel(dir), x + SLOT_LABEL_X, y + rowY + 5,
                CTCreateScreenHelper.LABEL_COLOR, false);
        CTCreateScreenHelper.renderBlockSlots(graphics, x + VectorBearingMenu.FIRST_X - 10, y + rowY - 2, 2);
        CTCreateScreenHelper.renderFrequencyGhostSlot(graphics, x + VectorBearingMenu.FIRST_X + 1, y + rowY + 1,
                inside(mouseX, mouseY, x + VectorBearingMenu.FIRST_X, y + rowY, 18, 18), false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(graphics, x + VectorBearingMenu.SECOND_X + 1, y + rowY + 1,
                inside(mouseX, mouseY, x + VectorBearingMenu.SECOND_X, y + rowY, 18, 18), true);
    }

    // Get the direction label
    private Component directionLabel(Direction dir) {
        return Component.translatable("createthrusters.vector_bearing.direction." + dir.getName());
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
            for (int i = 0; i < 3; i++) {
                int bx = leftPos + MODE_X + i * (MODE_W + MODE_GAP);
                if (inside(mouseX, mouseY, bx, topPos + MODE_Y, MODE_W, MODE_H)) {
                    controlMode = VectorBearingBlockEntity.ControlMode.values()[i];
                    sendConfig();
                    return true;
                }
            }

            int minusX = leftPos + TILT_X;
            int plusX = minusX + TILT_BUTTON_W + 4 + TILT_VALUE_W + 4;
            if (inside(mouseX, mouseY, minusX, topPos + TILT_Y, TILT_BUTTON_W, MODE_H)) {
                maxTiltDegrees = Math.max(0.0D, maxTiltDegrees - 1.0D);
                sendConfig();
                return true;
            }
            if (inside(mouseX, mouseY, plusX, topPos + TILT_Y, TILT_BUTTON_W, MODE_H)) {
                maxTiltDegrees = Math.min(89.0D, maxTiltDegrees + 1.0D);
                sendConfig();
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Send the config
    private void sendConfig() {
        if (menu.getContentPos() == null) {
            return;
        }
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
