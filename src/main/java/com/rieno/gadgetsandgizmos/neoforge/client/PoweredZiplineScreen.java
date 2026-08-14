package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineMenu;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineConfigPacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

// Edit Powered Zipline settings
public class PoweredZiplineScreen extends AbstractSimiContainerScreen<PoweredZiplineMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 260;
    private static final int HEIGHT = 190;
    private static final int CONFIG_X = 146;
    private static final int CONFIG_VALUE_RIGHT_PADDING = 16;
    private static final int SLIDER_WIDTH = 98;
    private static final int SLIDER_HEIGHT = 12;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Maximum speed slider x
    private int maxSpeedSliderX;
    // Maximum speed slider y
    private int maxSpeedSliderY;
    // Current damping slider x
    private int dampingSliderX;
    // Current damping slider y
    private int dampingSliderY;
    // Max speed
    private float maxSpeed;
    // Current damping
    private float damping;
    // Tracks whether max speed is being dragged
    private boolean draggingMaxSpeed;
    // Tracks whether damping is being dragged
    private boolean draggingDamping;
    // Tracks whether powered zipline is dirty
    private boolean dirty;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline
    public PoweredZiplineScreen(PoweredZiplineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        maxSpeed = menu.getMaxSpeedState();
        damping = menu.getDampingState();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the powered zipline
    @Override
    protected void init() {
        super.init();
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
        maxSpeedSliderX = leftPos + CONFIG_X;
        maxSpeedSliderY = topPos + 54;
        dampingSliderX = leftPos + CONFIG_X;
        dampingSliderY = topPos + 84;
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
        renderPlayerInventory(guiGraphics, x + PoweredZiplineMenu.PLAYER_SLOTS_X - 8, y + PoweredZiplineMenu.PLAYER_SLOTS_Y - 18);

        guiGraphics.drawCenteredString(font, title, x + WIDTH / 2, y + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);
        guiGraphics.drawString(font, Component.translatable("createthrusters.powered_zipline.config.forward"), x + 12, y + 42,
                CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.powered_zipline.config.backward"), x + 12, y + 74,
                CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.powered_zipline.config.max_speed"), x + CONFIG_X, y + 42,
                CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        drawRightAligned(guiGraphics, formatSpeed(maxSpeed), x + WIDTH - CONFIG_VALUE_RIGHT_PADDING, y + 42);
        renderHorizontalSlider(guiGraphics, maxSpeedSliderX, maxSpeedSliderY, maxSpeedToPercent(maxSpeed),
                isOverHorizontalHandle(mouseX, mouseY, maxSpeedSliderX, maxSpeedSliderY, maxSpeedToPercent(maxSpeed)));
        guiGraphics.drawString(font, Component.translatable("createthrusters.powered_zipline.config.damping"), x + CONFIG_X, y + 72,
                CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        drawRightAligned(guiGraphics, formatPercent(damping), x + WIDTH - CONFIG_VALUE_RIGHT_PADDING, y + 72);
        renderHorizontalSlider(guiGraphics, dampingSliderX, dampingSliderY, damping,
                isOverHorizontalHandle(mouseX, mouseY, dampingSliderX, dampingSliderY, damping));

        CTCreateScreenHelper.renderBlockSlots(guiGraphics, x + PoweredZiplineMenu.FORWARD_FIRST_X - 10,
                y + PoweredZiplineMenu.FORWARD_Y - 2, 2);
        CTCreateScreenHelper.renderBlockSlots(guiGraphics, x + PoweredZiplineMenu.BACKWARD_FIRST_X - 10,
                y + PoweredZiplineMenu.BACKWARD_Y - 2, 2);

        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, x + PoweredZiplineMenu.FORWARD_FIRST_X + 1,
                y + PoweredZiplineMenu.FORWARD_Y + 1, false, false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, x + PoweredZiplineMenu.FORWARD_SECOND_X + 1,
                y + PoweredZiplineMenu.FORWARD_Y + 1, false, true);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, x + PoweredZiplineMenu.BACKWARD_FIRST_X + 1,
                y + PoweredZiplineMenu.BACKWARD_Y + 1, false, false);
        CTCreateScreenHelper.renderFrequencyGhostSlot(guiGraphics, x + PoweredZiplineMenu.BACKWARD_SECOND_X + 1,
                y + PoweredZiplineMenu.BACKWARD_Y + 1, false, true);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the powered zipline
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
            if (inside(mouseX, mouseY, maxSpeedSliderX, maxSpeedSliderY, SLIDER_WIDTH, SLIDER_HEIGHT)) {
                draggingMaxSpeed = true;
                updateMaxSpeedValue(mouseX);
                return true;
            }
            if (inside(mouseX, mouseY, dampingSliderX, dampingSliderY, SLIDER_WIDTH, SLIDER_HEIGHT)) {
                draggingDamping = true;
                updateDampingValue(mouseX);
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        if (btn == 0 && draggingMaxSpeed) {
            updateMaxSpeedValue(mouseX);
            return true;
        }
        if (btn == 0 && draggingDamping) {
            updateDampingValue(mouseX);
            return true;
        }
        return super.mouseDragged(screenMouseX, screenMouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        boolean wasDragging = draggingMaxSpeed || draggingDamping;
        draggingMaxSpeed = false;
        draggingDamping = false;
        if (wasDragging) {
            sendUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle the close event
    @Override
    public void onClose() {
        sendUpdate();
        super.onClose();
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

    // Update the max speed value
    private void updateMaxSpeedValue(double mouseX) {
        maxSpeed = percentToMaxSpeed((float) Mth.clamp((mouseX - maxSpeedSliderX) / SLIDER_WIDTH, 0.0D, 1.0D));
        dirty = true;
    }

    // Update the damping value
    private void updateDampingValue(double mouseX) {
        damping = (float) Mth.clamp((mouseX - dampingSliderX) / SLIDER_WIDTH, 0.0D, 1.0D);
        dirty = true;
    }

    // Send the update
    private void sendUpdate() {
        if (!dirty || menu.getContentPos() == null) {
            return;
        }
        dirty = false;
        menu.setMotionConfigurationState(maxSpeed, damping);
        PacketDistributor.sendToServer(new ServerboundZiplineConfigPacket(
                menu.getContentPos(), menu.getContentSubLevelId(), maxSpeed, damping));
    }

    // Draw the horizontal slider
    private void renderHorizontalSlider(GuiGraphics guiGraphics, int x, int y, float percent, boolean hovered) {
        float clamped = Mth.clamp(percent, 0.0f, 1.0f);
        CTCreateScreenHelper.renderInset(guiGraphics, x, y, SLIDER_WIDTH, SLIDER_HEIGHT, false, false);
        guiGraphics.fill(x + 4, y + 4, x + SLIDER_WIDTH - 4, y + 8, 0xFF3D2F20);
        int fillRight = x + 4 + Math.round(clamped * (SLIDER_WIDTH - 8));
        guiGraphics.fill(x + 4, y + 4, fillRight, y + 8, 0xFFC6843F);
        int handleX = x + Math.round(clamped * SLIDER_WIDTH) - 4;
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, handleX, y - 1, 8, SLIDER_HEIGHT + 2,
                Component.empty(), hovered, true, false);
    }

    // Check if this is over the horizontal handle
    private boolean isOverHorizontalHandle(double mouseX, double mouseY, int x, int y, float percent) {
        int handleX = x + Math.round(Mth.clamp(percent, 0.0f, 1.0f) * SLIDER_WIDTH) - 4;
        return inside(mouseX, mouseY, handleX, y - 1, 8, SLIDER_HEIGHT + 2);
    }

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Get the maximum speed to percent
    private float maxSpeedToPercent(float speed) {
        return (speed - PoweredZiplineBlockEntity.MIN_CONFIGURED_MAX_SPEED)
                / (PoweredZiplineBlockEntity.MAX_CONFIGURED_MAX_SPEED - PoweredZiplineBlockEntity.MIN_CONFIGURED_MAX_SPEED);
    }

    // Get the percent to max speed
    private float percentToMaxSpeed(float percent) {
        return Mth.lerp(Mth.clamp(percent, 0.0f, 1.0f),
                PoweredZiplineBlockEntity.MIN_CONFIGURED_MAX_SPEED,
                PoweredZiplineBlockEntity.MAX_CONFIGURED_MAX_SPEED);
    }

    // Draw the right aligned
    private void drawRightAligned(GuiGraphics guiGraphics, Component text, int right, int y) {
        guiGraphics.drawString(font, text, right - font.width(text), y, CTCreateScreenHelper.VALUE_COLOR, false);
    }

    // Format the speed
    private static Component formatSpeed(float speed) {
        return Component.literal(String.format(Locale.ROOT, "%.2f/t", speed));
    }

    // Format the percent
    private static Component formatPercent(float val) {
        return Component.literal(Math.round(Mth.clamp(val, 0.0f, 1.0f) * 100.0f) + "%");
    }
}
