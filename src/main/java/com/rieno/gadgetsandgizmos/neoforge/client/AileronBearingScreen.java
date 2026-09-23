package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AileronBearingConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

// Edit Aileron Bearing settings
public class AileronBearingScreen extends AbstractSimiContainerScreen<AileronBearingMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int TITLE_CENTER_X = 128;
    private static final int TITLE_Y = 31;
    private static final int ORANGE_SLIDER_X = 59;
    private static final int CYAN_SLIDER_X = 144;
    private static final int SLIDER_CENTER_Y = 54;
    private static final int SLIDER_ACTIVE_WIDTH = 51;
    private static final int SLIDER_TRACK_WIDTH = 52;
    private static final int SLIDER_HIT_PADDING_X = 5;
    private static final int SLIDER_HIT_HEIGHT = 18;
    private static final int HANDLE_WIDTH = 6;
    private static final int HANDLE_HEIGHT = 17;
    private static final int RANGE_LABEL_Y = 65;
    private static final int RANGE_LABEL_WIDTH = 22;
    private static final int RANGE_LABEL_HEIGHT = 11;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Minimum angles
    private final EnumMap<BearingHead, Double> minAngles =
            new EnumMap<>(BearingHead.class);
    // Maximum angles
    private final EnumMap<BearingHead, Double> maxAngles =
            new EnumMap<>(BearingHead.class);
    // Active head
    private BearingHead activeHead;
    // Tracks whether min handle is being dragged
    private boolean draggingMinHandle;
    // Tracks whether aileron bearing is dirty
    private boolean dirty;
    // Current drag start mouse x
    private double dragStartMouseX;
    // Current drag start min angle
    private double dragStartMinAngle;
    // Current drag start max angle
    private double dragStartMaxAngle;
    // Current inline angle editor
    private EditBox angleEditor;
    // Head being edited inline
    private BearingHead editingHead;
    // Tracks whether the inline editor is editing the minimum
    private boolean editingMinAngle;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing
    public AileronBearingScreen(AileronBearingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        for (BearingHead head : BearingHead.values()) {
            minAngles.put(head, menu.getInitialMinAngle(head));
            maxAngles.put(head, menu.getInitialMaxAngle(head));
        }
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the aileron bearing
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
            CTCreateScreenHelper.renderAileronBearingGuiBackground(guiGraphics, x, y);
            guiGraphics.drawCenteredString(font, title, x + TITLE_CENTER_X, y + TITLE_Y,
                    CTCreateScreenHelper.BANNER_TITLE_COLOR);
            drawHeadRange(guiGraphics, x, y, mouseX, mouseY, BearingHead.SECONDARY);
            drawHeadRange(guiGraphics, x, y, mouseX, mouseY, BearingHead.PRIMARY);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the head range
    private void drawHeadRange(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                               BearingHead head) {
        int sliderLeft = x + sliderX(head);
        int sliderCenterY = y + SLIDER_CENTER_Y;
        int minX = valueToSliderX(minAngles.get(head), -rangeLimit(), rangeLimit(),
                sliderLeft, SLIDER_ACTIVE_WIDTH);
        int maxX = valueToSliderX(maxAngles.get(head), -rangeLimit(), rangeLimit(),
                sliderLeft, SLIDER_ACTIVE_WIDTH);
        CTCreateScreenHelper.renderTntHorizontalTrackFill(graphics, sliderLeft, sliderCenterY,
                SLIDER_TRACK_WIDTH, minX, maxX + 1);
        boolean minHovered = isHandleHovered(mouseX, mouseY, minX, sliderCenterY)
                || activeHead == head && draggingMinHandle;
        boolean maxHovered = isHandleHovered(mouseX, mouseY, maxX, sliderCenterY)
                || activeHead == head && !draggingMinHandle;
        CTCreateScreenHelper.renderTntHorizontalRangeMinHandle(graphics, minX, sliderCenterY, minHovered);
        CTCreateScreenHelper.renderTntHorizontalRangeMaxHandle(graphics, maxX, sliderCenterY, maxHovered);
        int labelY = y + RANGE_LABEL_Y;
        int minLabelCenter = sliderLeft;
        int maxLabelCenter = sliderLeft + SLIDER_ACTIVE_WIDTH;
        if (angleEditor == null || editingHead != head) {
            graphics.drawCenteredString(font, Component.literal(formatAngleLabel(minAngles.get(head))),
                    minLabelCenter, labelY,
                    CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
            graphics.drawCenteredString(font, Component.literal(formatAngleLabel(maxAngles.get(head))),
                    maxLabelCenter, labelY,
                    CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
        } else {
            angleEditor.render(graphics, mouseX, mouseY, 0.0F);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the aileron bearing
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderSliderTooltip(guiGraphics, mouseX, mouseY);
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0) {
            if (angleEditor != null) {
                if (angleEditor.mouseClicked(mouseX, mouseY, btn)) {
                    return true;
                }
                commitAngleEditor();
            }
            if (openRangeEndpointEditor(mouseX, mouseY, BearingHead.PRIMARY)
                    || openRangeEndpointEditor(mouseX, mouseY, BearingHead.SECONDARY)) {
                return true;
            }
            if (beginRangeDrag(mouseX, mouseY, BearingHead.PRIMARY)) {
                return true;
            }
            if (beginRangeDrag(mouseX, mouseY, BearingHead.SECONDARY)) {
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Open a range endpoint editor by clicking its label
    private boolean openRangeEndpointEditor(double mouseX, double mouseY, BearingHead head) {
        int sliderLeft = leftPos + sliderX(head);
        int labelY = topPos + RANGE_LABEL_Y;
        int minWidth = Math.max(RANGE_LABEL_WIDTH, font.width(formatAngleLabel(minAngles.get(head))) + 2);
        if (inside(mouseX, mouseY, sliderLeft - minWidth / 2, labelY - 1,
                minWidth, RANGE_LABEL_HEIGHT)) {
            openAngleEditor(head, true);
            return true;
        }
        int maxWidth = Math.max(RANGE_LABEL_WIDTH, font.width(formatAngleLabel(maxAngles.get(head))) + 2);
        if (inside(mouseX, mouseY, sliderLeft + SLIDER_ACTIVE_WIDTH - maxWidth / 2, labelY - 1,
                maxWidth, RANGE_LABEL_HEIGHT)) {
            openAngleEditor(head, false);
            return true;
        }
        return false;
    }

    // Handle keys while editing an angle
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (angleEditor != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitAngleEditor();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeAngleEditor();
                return true;
            }
            return angleEditor.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Handle typed characters while editing an angle
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return angleEditor != null
                ? angleEditor.charTyped(codePoint, modifiers)
                : super.charTyped(codePoint, modifiers);
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        if (btn == 0 && activeHead != null) {
            updateActiveRange(mouseX);
            return true;
        }
        return super.mouseDragged(screenMouseX, screenMouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        if (activeHead != null) {
            activeHead = null;
            if (dirty) {
                sendConfig();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle the close event
    @Override
    public void onClose() {
        commitAngleEditor();
        sendConfig();
        super.onClose();
    }

    // Open one inline angle editor
    private void openAngleEditor(BearingHead head, boolean minimum) {
        editingHead = head;
        editingMinAngle = minimum;
        int x = leftPos + sliderX(head) - 9;
        int y = topPos + RANGE_LABEL_Y - 3;
        angleEditor = new EditBox(font, x, y, SLIDER_ACTIVE_WIDTH + 18, 16,
                Component.literal((minimum ? "Min" : "Max") + " angle"));
        angleEditor.setMaxLength(16);
        angleEditor.setFilter(AileronBearingScreen::isPartialAngle);
        angleEditor.setValue(String.format(Locale.ROOT, "%.1f",
                minimum ? minAngles.get(head) : maxAngles.get(head)));
        angleEditor.setCursorPosition(angleEditor.getValue().length());
        angleEditor.setHighlightPos(0);
        angleEditor.setFocused(true);
    }

    // Commit the current inline angle editor
    private void commitAngleEditor() {
        if (angleEditor == null || editingHead == null) return;
        try {
            double value = Mth.clamp(Double.parseDouble(angleEditor.getValue()), -rangeLimit(), rangeLimit());
            if (editingMinAngle) {
                minAngles.put(editingHead, Math.min(value, maxAngles.get(editingHead)));
            } else {
                maxAngles.put(editingHead, Math.max(value, minAngles.get(editingHead)));
            }
            dirty = true;
        } catch (NumberFormatException ignored) {
        }
        closeAngleEditor();
        if (dirty) sendConfig();
    }

    // Close the inline angle editor
    private void closeAngleEditor() {
        angleEditor = null;
        editingHead = null;
    }

    // Check whether text is a valid partial angle
    private static boolean isPartialAngle(String value) {
        return value != null && value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)?");
    }

    // Begin the range drag
    private boolean beginRangeDrag(double mouseX, double mouseY, BearingHead head) {
        int x = leftPos + sliderX(head);
        int y = topPos + SLIDER_CENTER_Y - SLIDER_HIT_HEIGHT / 2;
        if (!inside(mouseX, mouseY, x - SLIDER_HIT_PADDING_X, y,
                SLIDER_TRACK_WIDTH + SLIDER_HIT_PADDING_X * 2, SLIDER_HIT_HEIGHT)) {
            return false;
        }
        draggingMinHandle = isMinHandleClosest(mouseX, head);
        activeHead = head;
        beginAngleDrag(mouseX, head);
        updateActiveRange(mouseX);
        return true;
    }

    // Update the active range
    private void updateActiveRange(double mouseX) {
        if (activeHead == null) {
            return;
        }
        double directValue = sliderXToValue(mouseX, leftPos + sliderX(activeHead), SLIDER_ACTIVE_WIDTH,
                -rangeLimit(), rangeLimit());
        double startDirectValue = sliderXToValue(dragStartMouseX, leftPos + sliderX(activeHead), SLIDER_ACTIVE_WIDTH,
                -rangeLimit(), rangeLimit());
        double startValue = draggingMinHandle ? dragStartMinAngle : dragStartMaxAngle;
        double val = CTSliderInputHelper.adjustDegrees(directValue, startDirectValue, startValue,
                -rangeLimit(), rangeLimit());
        val = Math.round(val * 10.0D) / 10.0D;
        if (draggingMinHandle) {
            minAngles.put(activeHead, Math.min(val, maxAngles.get(activeHead)));
        } else {
            maxAngles.put(activeHead, Math.max(val, minAngles.get(activeHead)));
        }
        dirty = true;
    }

    // Begin the angle drag
    private void beginAngleDrag(double mouseX, BearingHead head) {
        dragStartMouseX = mouseX;
        dragStartMinAngle = minAngles.get(head);
        dragStartMaxAngle = maxAngles.get(head);
    }

    // Send the config
    private void sendConfig() {
        if (menu.getContentPos() == null) {
            return;
        }
        dirty = false;
        PacketDistributor.sendToServer(new AileronBearingConfigPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()),
                minAngles.get(BearingHead.PRIMARY),
                maxAngles.get(BearingHead.PRIMARY),
                minAngles.get(BearingHead.SECONDARY),
                maxAngles.get(BearingHead.SECONDARY),
                slot(BearingHead.PRIMARY, AileronBearingBlockEntity.ControlDirection.CW, 0),
                slot(BearingHead.PRIMARY, AileronBearingBlockEntity.ControlDirection.CW, 1),
                slot(BearingHead.PRIMARY, AileronBearingBlockEntity.ControlDirection.CCW, 0),
                slot(BearingHead.PRIMARY, AileronBearingBlockEntity.ControlDirection.CCW, 1),
                slot(BearingHead.SECONDARY, AileronBearingBlockEntity.ControlDirection.CW, 0),
                slot(BearingHead.SECONDARY, AileronBearingBlockEntity.ControlDirection.CW, 1),
                slot(BearingHead.SECONDARY, AileronBearingBlockEntity.ControlDirection.CCW, 0),
                slot(BearingHead.SECONDARY, AileronBearingBlockEntity.ControlDirection.CCW, 1)));
    }

    // Get the slot
    private ItemStack slot(BearingHead head,
                           AileronBearingBlockEntity.ControlDirection dir, int offset) {
        return copySingle(menu.ghostInventory.getStackInSlot(AileronBearingMenu.slotIndex(head, dir) + offset));
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

    // Get the value to slider x
    private int valueToSliderX(double val, double minAllowed, double maxAllowed, int x, int width) {
        double normalized = Mth.clamp((val - minAllowed) / Math.max(1.0E-6D, maxAllowed - minAllowed), 0.0D, 1.0D);
        return x + (int) Math.round(normalized * width);
    }

    // Get the slider x to value
    private double sliderXToValue(double mouseX, int x, int width, double minAllowed, double maxAllowed) {
        double normalized = Mth.clamp((mouseX - x) / Math.max(1.0D, width), 0.0D, 1.0D);
        return Mth.lerp(normalized, minAllowed, maxAllowed);
    }

    // Check if the min handle is closest
    private boolean isMinHandleClosest(double mouseX, BearingHead head) {
        int x = leftPos + sliderX(head);
        int minX = valueToSliderX(minAngles.get(head), -rangeLimit(), rangeLimit(), x, SLIDER_ACTIVE_WIDTH);
        int maxX = valueToSliderX(maxAngles.get(head), -rangeLimit(), rangeLimit(), x, SLIDER_ACTIVE_WIDTH);
        return Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX);
    }

    // Draw the slider tooltip
    private void renderSliderTooltip(GuiGraphics graphics, int screenMouseX, int screenMouseY) {
        double mouseX = scalableGui.mouseX(screenMouseX);
        double mouseY = scalableGui.mouseY(screenMouseY);
        Component tooltip = sliderTooltip(mouseX, mouseY);
        if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, screenMouseX, screenMouseY);
        }
    }

    // Get the slider tooltip
    private Component sliderTooltip(double mouseX, double mouseY) {
        Component tooltip = sliderTooltip(mouseX, mouseY, BearingHead.PRIMARY);
        if (tooltip != null) {
            return tooltip;
        }
        return sliderTooltip(mouseX, mouseY, BearingHead.SECONDARY);
    }

    // Get the slider tooltip
    private Component sliderTooltip(double mouseX, double mouseY, BearingHead head) {
        int sliderLeft = leftPos + sliderX(head);
        int sliderCenterY = topPos + SLIDER_CENTER_Y;
        int minX = valueToSliderX(minAngles.get(head), -rangeLimit(), rangeLimit(),
                sliderLeft, SLIDER_ACTIVE_WIDTH);
        int maxX = valueToSliderX(maxAngles.get(head), -rangeLimit(), rangeLimit(),
                sliderLeft, SLIDER_ACTIVE_WIDTH);
        boolean overMin = isHandleHovered(mouseX, mouseY, minX, sliderCenterY);
        boolean overMax = isHandleHovered(mouseX, mouseY, maxX, sliderCenterY);
        if (!overMin && !overMax) {
            return null;
        }
        boolean min = overMin && (!overMax || isMinHandleClosest(mouseX, head));
        String headName = head == BearingHead.PRIMARY ? "Primary" : "Secondary";
        String handleName = min ? "Min" : "Max";
        double val = min ? minAngles.get(head) : maxAngles.get(head);
        return Component.literal(headName + " " + handleName + ": ")
                .append(formatAngle(val));
    }

    // Check if the handle is hovered
    private static boolean isHandleHovered(double mouseX, double mouseY, int centerX, int centerY) {
        return inside(mouseX, mouseY, centerX - HANDLE_WIDTH / 2, centerY - HANDLE_HEIGHT / 2,
                HANDLE_WIDTH, HANDLE_HEIGHT);
    }

    // Get the slider x
    private static int sliderX(BearingHead head) {
        return head == BearingHead.PRIMARY ? CYAN_SLIDER_X : ORANGE_SLIDER_X;
    }

    // Check if the point is inside the bounds
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

    // Get the range limit
    private double rangeLimit() {
        return CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
    }

    // Format one live range endpoint label
    private static String formatAngleLabel(double val) {
        return String.format(Locale.ROOT, "%.1f\u00B0", val);
    }

    // Format the angle
    private static Component formatAngle(double val) {
        return Component.literal(String.format(Locale.ROOT, "%.1f deg", val));
    }
}
