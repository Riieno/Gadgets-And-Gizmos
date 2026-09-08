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
import com.mojang.math.Axis;
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
    private static final int MODE_LEFT_ARROW_X = 47;
    private static final int MODE_RIGHT_ARROW_X = 105;
    private static final int MODE_ARROW_Y = 29;
    private static final int MODE_ARROW_W = 12;
    private static final int MODE_ARROW_H = 14;
    private static final int MODE_LABEL_CENTER_X = 84;
    private static final int MODE_LABEL_Y = 33;
    private static final int MODE_LABEL_H = 5;
    private static final int STABILIZE_LEFT_ARROW_X = 137;
    private static final int STABILIZE_RIGHT_ARROW_X = 195;
    private static final int STABILIZE_ARROW_Y = 29;
    private static final int STABILIZE_LABEL_CENTER_X = 174;
    private static final int STABILIZE_LABEL_Y = 33;
    private static final int STABILIZE_TOGGLE_X = 194;
    private static final int STABILIZE_TOGGLE_Y = 50;
    private static final int STABILIZE_TOGGLE_W = 15;
    private static final int STABILIZE_TOGGLE_H = 10;
    private static final int SLIDER_FILL_X = 144;
    private static final int SLIDER_FILL_Y = 135;
    private static final int SLIDER_FILL_W = 56;
    private static final int SLIDER_FILL_H = 13;
    private static final int SLIDER_THUMB_MIN_X = 140;
    private static final int SLIDER_THUMB_Y = 133;
    private static final int SLIDER_THUMB_TRAVEL = 50;
    private static final int SLIDER_THUMB_W = 10;
    private static final int SLIDER_THUMB_H = 17;
    private static final int SLIDER_HIT_X = 140;
    private static final int SLIDER_HIT_Y = 130;
    private static final int SLIDER_HIT_W = 64;
    private static final int SLIDER_HIT_H = 22;
    private static final int AXIS_LABEL_SOURCE_X = 224;
    private static final int AXIS_LABEL_SOURCE_X_Y = 128;
    private static final int AXIS_LABEL_SOURCE_Y_Y = 136;
    private static final int AXIS_LABEL_SOURCE_Z_Y = 144;
    private static final int AXIS_LABEL_SOURCE_XZ_Y = 152;
    private static final int AXIS_LABEL_SOURCE_XY_Y = 160;
    private static final int AXIS_LABEL_SOURCE_ZY_Y = 168;
    private static final int AXIS_LABEL_SINGLE_W = 22;
    private static final int AXIS_LABEL_PLANE_W = 28;
    private static final int AXIS_LABEL_H = 5;
    private static final int COMPASS_RED_LEFT_X = 78;
    private static final int COMPASS_RED_LEFT_Y = 102;
    private static final int COMPASS_RED_RIGHT_X = 110;
    private static final int COMPASS_RED_RIGHT_Y = 98;
    private static final int COMPASS_RED_SOURCE_X = 0;
    private static final int COMPASS_RED_LEFT_SOURCE_Y = 128;
    private static final int COMPASS_RED_RIGHT_SOURCE_Y = 140;
    private static final int COMPASS_RED_W = 32;
    private static final int COMPASS_RED_H = 4;
    private static final int COMPASS_BLUE_TOP_X = 106;
    private static final int COMPASS_BLUE_TOP_Y = 70;
    private static final int COMPASS_BLUE_BOTTOM_X = 112;
    private static final int COMPASS_BLUE_BOTTOM_Y = 100;
    private static final int COMPASS_BLUE_TOP_SOURCE_X = 0;
    private static final int COMPASS_BLUE_BOTTOM_SOURCE_X = 28;
    private static final int COMPASS_BLUE_SOURCE_Y = 144;
    private static final int COMPASS_BLUE_W = 4;
    private static final int COMPASS_BLUE_H = 32;
    private static final int[][] COMPASS_STABLE_LIGHTS = {
            {100, 92}, {117, 92}, {100, 109}, {117, 109}
    };
    private static final int COMPASS_STABLE_LIGHT_SOURCE_X = 0;
    private static final int COMPASS_STABLE_LIGHT_SOURCE_Y = 176;
    private static final int COMPASS_STABLE_LIGHT_SIZE = 3;
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
    // Selected stabilization plane
    private VectorBearingBlockEntity.StabilizeAxis stabilizeAxis;
    // Tracks whether stabilization is enabled
    private boolean keepStable;
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
        stabilizeAxis = menu.getInitialStabilizeAxis();
        keepStable = menu.isInitialKeepStable();
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
            drawStabilizeAxisLabel(guiGraphics, x, y);
            drawStabilizationState(guiGraphics, x, y);
            drawMaxTiltSlider(guiGraphics, x, y);
        } finally {
            scalableGui.pop(guiGraphics);
        }
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

    // Draw the selected stabilization plane
    private void drawStabilizeAxisLabel(GuiGraphics graphics, int x, int y) {
        int sourceY = AXIS_LABEL_SOURCE_XZ_Y;
        int labelWidth = AXIS_LABEL_PLANE_W;
        switch (stabilizeAxis) {
            case X -> {
                sourceY = AXIS_LABEL_SOURCE_X_Y;
                labelWidth = AXIS_LABEL_SINGLE_W;
            }
            case Y -> {
                sourceY = AXIS_LABEL_SOURCE_Y_Y;
                labelWidth = AXIS_LABEL_SINGLE_W;
            }
            case Z -> {
                sourceY = AXIS_LABEL_SOURCE_Z_Y;
                labelWidth = AXIS_LABEL_SINGLE_W;
            }
            case XZ -> {
                sourceY = AXIS_LABEL_SOURCE_XZ_Y;
                labelWidth = AXIS_LABEL_PLANE_W;
            }
            case XY -> {
                sourceY = AXIS_LABEL_SOURCE_XY_Y;
                labelWidth = AXIS_LABEL_PLANE_W;
            }
            case ZY -> {
                sourceY = AXIS_LABEL_SOURCE_ZY_Y;
                labelWidth = AXIS_LABEL_PLANE_W;
            }
        };
        graphics.blit(BACKGROUND, x + STABILIZE_LABEL_CENTER_X - labelWidth / 2,
                y + STABILIZE_LABEL_Y, labelWidth, AXIS_LABEL_H,
                AXIS_LABEL_SOURCE_X, sourceY, labelWidth, AXIS_LABEL_H, WIDTH, HEIGHT);
    }

    // Draw the active stabilization indicators
    private void drawStabilizationState(GuiGraphics graphics, int x, int y) {
        boolean stabilizesX = keepStable && switch (stabilizeAxis) {
            case X, XZ, XY -> true;
            case Y, Z, ZY -> false;
        };
        boolean stabilizesZ = keepStable && switch (stabilizeAxis) {
            case Z, XZ, ZY -> true;
            case X, Y, XY -> false;
        };
        if (stabilizesX) {
            graphics.blit(BACKGROUND, x + COMPASS_RED_LEFT_X, y + COMPASS_RED_LEFT_Y,
                    COMPASS_RED_W, COMPASS_RED_H, COMPASS_RED_SOURCE_X,
                    COMPASS_RED_LEFT_SOURCE_Y, COMPASS_RED_W, COMPASS_RED_H, WIDTH, HEIGHT);
            graphics.blit(BACKGROUND, x + COMPASS_RED_RIGHT_X, y + COMPASS_RED_RIGHT_Y,
                    COMPASS_RED_W, COMPASS_RED_H, COMPASS_RED_SOURCE_X,
                    COMPASS_RED_RIGHT_SOURCE_Y, COMPASS_RED_W, COMPASS_RED_H, WIDTH, HEIGHT);
        }
        if (stabilizesZ) {
            graphics.blit(BACKGROUND, x + COMPASS_BLUE_TOP_X, y + COMPASS_BLUE_TOP_Y,
                    COMPASS_BLUE_W, COMPASS_BLUE_H, COMPASS_BLUE_TOP_SOURCE_X,
                    COMPASS_BLUE_SOURCE_Y, COMPASS_BLUE_W, COMPASS_BLUE_H, WIDTH, HEIGHT);
            graphics.blit(BACKGROUND, x + COMPASS_BLUE_BOTTOM_X, y + COMPASS_BLUE_BOTTOM_Y,
                    COMPASS_BLUE_W, COMPASS_BLUE_H, COMPASS_BLUE_BOTTOM_SOURCE_X,
                    COMPASS_BLUE_SOURCE_Y, COMPASS_BLUE_W, COMPASS_BLUE_H, WIDTH, HEIGHT);
        }
        for (int[] light : COMPASS_STABLE_LIGHTS) {
            if (stabilizesX && stabilizesZ) {
                graphics.blit(BACKGROUND, x + light[0], y + light[1], COMPASS_STABLE_LIGHT_SIZE,
                        COMPASS_STABLE_LIGHT_SIZE, COMPASS_STABLE_LIGHT_SOURCE_X,
                        COMPASS_STABLE_LIGHT_SOURCE_Y, COMPASS_STABLE_LIGHT_SIZE,
                        COMPASS_STABLE_LIGHT_SIZE, WIDTH, HEIGHT);
            }
        }
        if (keepStable) {
            graphics.blit(BACKGROUND, x + STABILIZE_TOGGLE_X, y + STABILIZE_TOGGLE_Y,
                    STABILIZE_TOGGLE_W, STABILIZE_TOGGLE_H,
                    16, 65, STABILIZE_TOGGLE_W, STABILIZE_TOGGLE_H, WIDTH, HEIGHT);
        }
    }

    // Draw the max tilt slider
    private void drawMaxTiltSlider(GuiGraphics graphics, int x, int y) {
        double normalized = maxTiltDegrees / MAX_TILT_DEGREES;
        int fillWidth = Mth.clamp((int) Math.round(normalized * SLIDER_FILL_W), 0, SLIDER_FILL_W);
        if (fillWidth < SLIDER_FILL_W) {
            graphics.fill(x + SLIDER_FILL_X + fillWidth, y + SLIDER_FILL_Y,
                    x + SLIDER_FILL_X + SLIDER_FILL_W, y + SLIDER_FILL_Y + SLIDER_FILL_H,
                    0xFF3B130F);
        }
        int thumbX = SLIDER_THUMB_MIN_X + (int) Math.round(normalized * SLIDER_THUMB_TRAVEL);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + thumbX + SLIDER_THUMB_W, y + SLIDER_THUMB_Y, 0.0D);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
            graphics.blit(BACKGROUND, 0, 0, 17, 10, 220, 46, 17, 10, WIDTH, HEIGHT);
        } finally {
            graphics.pose().popPose();
        }
    }

    // Update the max tilt from a slider pointer position
    private void updateMaxTiltFromSlider(double mouseX) {
        double sliderCenterLeft = leftPos + SLIDER_THUMB_MIN_X + SLIDER_THUMB_W / 2.0D;
        double normalized = Mth.clamp((mouseX - sliderCenterLeft) / SLIDER_THUMB_TRAVEL, 0.0D, 1.0D);
        maxTiltDegrees = Math.round(normalized * MAX_TILT_DEGREES * 10.0D) / 10.0D;
        dirty = true;
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
            if (inside(mouseX, mouseY, leftPos + STABILIZE_LEFT_ARROW_X, topPos + STABILIZE_ARROW_Y,
                    MODE_ARROW_W, MODE_ARROW_H)) {
                cycleStabilizeAxis(-1);
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + STABILIZE_RIGHT_ARROW_X, topPos + STABILIZE_ARROW_Y,
                    MODE_ARROW_W, MODE_ARROW_H)) {
                cycleStabilizeAxis(1);
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + STABILIZE_TOGGLE_X, topPos + STABILIZE_TOGGLE_Y,
                    STABILIZE_TOGGLE_W, STABILIZE_TOGGLE_H)) {
                keepStable = !keepStable;
                dirty = true;
                sendConfig();
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + SLIDER_HIT_X, topPos + SLIDER_HIT_Y,
                    SLIDER_HIT_W, SLIDER_HIT_H)) {
                draggingMaxTilt = true;
                updateMaxTiltFromSlider(mouseX);
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
        mouseX = scalableGui.mouseX(mouseX);
        if (btn == 0 && draggingMaxTilt) {
            updateMaxTiltFromSlider(mouseX);
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

    // Cycle the selected stabilization plane
    private void cycleStabilizeAxis(int offset) {
        VectorBearingBlockEntity.StabilizeAxis[] axes = VectorBearingBlockEntity.StabilizeAxis.values();
        int index = Math.floorMod(stabilizeAxis.ordinal() + offset, axes.length);
        stabilizeAxis = axes[index];
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
                stabilizeAxis,
                keepStable,
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
