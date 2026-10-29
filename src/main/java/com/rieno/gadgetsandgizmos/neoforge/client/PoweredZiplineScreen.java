package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineMenu;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSliderStyle;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineConfigPacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int WIDTH = 246;
    private static final int HEIGHT = 224;
    private static final int CONFIG_X = 140;
    private static final int SLIDER_WIDTH = 76;
    private static final int SLIDER_HEIGHT = 16;
    private static final int CONFIRM_X = 213;
    private static final int CONFIRM_Y = 105;
    private static final int CONFIRM_SIZE = 18;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters",
            "textures/gui/powered_zipline.png");
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(TEXTURE, 246, 261, 0, 0, WIDTH, HEIGHT);
    private static final PaintedSliderStyle SLIDER = new PaintedSliderStyle(
            new GuiTextureRegion(TEXTURE, 246, 261, 0, 234, 76, 8),
            new GuiTextureRegion(TEXTURE, 246, 261, 0, 245, 6, 16), 6);
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
        maxSpeedSliderY = topPos + 34;
        dampingSliderX = leftPos + CONFIG_X;
        dampingSliderY = topPos + 66;
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
        try{
            int x = leftPos;
            int y = topPos;
            // Crop above the spare slider artwork while retaining the painted inventory
            BACKGROUND.draw(guiGraphics, x, y);
            GuiControlPreviews.drawFittedText(guiGraphics, font, title, x + 8, y + 1, 222, 11, 1, TEXT_COLOR, true);
            GuiControlPreviews.drawFittedText(guiGraphics, font,
                    Component.translatable("createthrusters.powered_zipline.config.forward"),
                    x + 23, y + 31, 48, 16, 1, TEXT_COLOR, false);
            GuiControlPreviews.drawFittedText(guiGraphics, font,
                    Component.translatable("createthrusters.powered_zipline.config.backward"),
                    x + 23, y + 63, 48, 16, 1, TEXT_COLOR, false);
            renderSliderLabel(guiGraphics, "createthrusters.powered_zipline.config.max_speed", formatSpeed(maxSpeed), y + 24);
            renderHorizontalSlider(guiGraphics, maxSpeedSliderX, maxSpeedSliderY, maxSpeedToPercent(maxSpeed),
                    draggingMaxSpeed || isOverHorizontalHandle(mouseX, mouseY, maxSpeedSliderX, maxSpeedSliderY, maxSpeedToPercent(maxSpeed)));
            renderSliderLabel(guiGraphics, "createthrusters.powered_zipline.config.damping", formatPercent(damping), y + 56);
            renderHorizontalSlider(guiGraphics, dampingSliderX, dampingSliderY, damping,
                    draggingDamping || isOverHorizontalHandle(mouseX, mouseY, dampingSliderX, dampingSliderY, damping));
            if(inside(mouseX, mouseY, x + CONFIRM_X, y + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                guiGraphics.fill(x + CONFIRM_X, y + CONFIRM_Y, x + CONFIRM_X + CONFIRM_SIZE,
                        y + CONFIRM_Y + CONFIRM_SIZE, 0x22FFFFFF);
            }
        }finally{
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
            if(inside(mouseX, mouseY, leftPos + CONFIRM_X, topPos + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                onClose();
                return true;
            }
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
        maxSpeed = percentToMaxSpeed((float)SLIDER.fractionAt(mouseX, maxSpeedSliderX, SLIDER_WIDTH));
        dirty = true;
    }

    // Update the damping value
    private void updateDampingValue(double mouseX) {
        damping = (float)SLIDER.fractionAt(mouseX, dampingSliderX, SLIDER_WIDTH);
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
    private void renderHorizontalSlider(GuiGraphics guiGraphics, int x, int y, float percent, boolean hovered){
        SLIDER.draw(guiGraphics, x, y, SLIDER_WIDTH, SLIDER_HEIGHT, percent, hovered);
    }

    // Check if this is over the horizontal handle
    private boolean isOverHorizontalHandle(double mouseX, double mouseY, int x, int y, float percent){
        return inside(mouseX, mouseY, SLIDER.thumbLeft(x, SLIDER_WIDTH, percent), y, SLIDER.thumbWidth(), SLIDER_HEIGHT);
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

    // Fit each setting and value above its painted rail
    private void renderSliderLabel(GuiGraphics guiGraphics, String key, Component val, int y){
        GuiControlPreviews.drawFittedText(guiGraphics, font, Component.translatable(key),
                leftPos + CONFIG_X, y, 48, 10, 0.8F, TEXT_COLOR, false);
        GuiControlPreviews.drawFittedText(guiGraphics, font, val,
                leftPos + CONFIG_X + 48, y, 28, 10, 0.8F, TEXT_COLOR, true);
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
