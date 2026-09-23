package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.AngleMode;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.ControlMode;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterBearingRangePayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// Edit the bearing's pitch, yaw and control ranges with paired angle sliders
public class ThrusterBearingRangeScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH      = 316;
    private static final int HEIGHT     = 188;
    private static final int BAR_HEIGHT = 92;
    private static final int BUTTON_WIDTH = 150;
    private static final int ANGLE_OPTION_HEIGHT = 16;
    private static final int RANGE_OPTION_WIDTH = 72;
    private static final int SWIVEL_OPTION_WIDTH = 102;
    private static final int ANGLE_OPTION_GAP = 8;
    private static final int ANGLE_LABEL_WIDTH = 150;
    private static final int ANGLE_LABEL_HEIGHT = 24;

    private static final int ALT_TRACK_W   = 42;
    private static final int ALT_TRACK_SH  = 200;
    private static final int ALT_LIT_U     = 48;
    private static final int ALT_LIT_W     = 10;
    private static final int ALT_LIT_SH    = 200;
    private static final int ALT_GRAB_U    = 64;
    private static final int ALT_GRAB_V    = 0;
    private static final int ALT_GRAB_W    = 26;
    private static final int ALT_GRAB_H    = 16;

    private static final int LEFT_LIT_OFF   =  2;
    private static final int RIGHT_LIT_OFF  = 29;
    private static final int LEFT_GRAB_OFF  = -6;
    private static final int RIGHT_GRAB_OFF = 22;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Thruster bearing being configured
    private final ThrusterBearingBlockEntity be;
    // Limit
    private final double limit;

    // Current bg x
    private int bgX;
    // Current bar top
    private int barTop;
    // Current mode x
    private int modeX;
    // Current mode y
    private int modeY;
    // Current opt x
    private int optX;
    // Current opt y
    private int optY;
    // Current invert x
    private int invertX;
    // Current invert y
    private int invertY;
    // Min angle in degrees
    private double minAngleDeg;
    // Max angle in degrees
    private double maxAngleDeg;
    // Current control mode
    private ControlMode controlMode;
    // Current angle mode
    private AngleMode angleMode;
    // Tracks whether inverted is set
    private boolean inverted;
    // Tracks whether drag min is set
    private boolean dragMin;
    // Tracks whether drag max is set
    private boolean dragMax;
    // Tracks whether thruster bearing range is dirty
    private boolean dirty;
    // Current drag start mouse y
    private double dragStartMouseY;
    // Drag start min angle in degrees
    private double dragStartMinAngleDeg;
    // Drag start max angle in degrees
    private double dragStartMaxAngleDeg;
    // Current inline angle editor
    private EditBox angleEditor;
    // Tracks whether the inline editor is editing the minimum
    private boolean editingMinAngle;

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing range
    public ThrusterBearingRangeScreen(ThrusterBearingBlockEntity blockEntity) {
        super(Component.translatable("createthrusters.thruster_bearing.range_screen.title"));
        this.be = blockEntity;
        this.limit = CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        this.minAngleDeg = blockEntity.getMinAngleDegrees();
        this.maxAngleDeg = blockEntity.getMaxAngleDegrees();
        this.controlMode = blockEntity.getControlMode();
        this.angleMode = blockEntity.getAngleMode();
        this.inverted = blockEntity.isInverted();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the thruster bearing range
    @Override
    protected void init() {
        super.init();
        scalableGui.update(guiLeft, guiTop, windowWidth, windowHeight, width, height);
        bgX       = guiLeft + 24;
        barTop    = guiTop  + 34;
        modeX = guiLeft + 100;
        modeY = guiTop  + 98;
        optX = guiLeft + 100;
        optY = guiTop + 124;
        invertX = guiLeft + 100;
        invertY = guiTop + 158;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster bearing range
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the window
    @Override
    protected void renderWindow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
        // -----------------------------------------------------WINDOW FRAME-----------------------------------------------------
        CTCreateScreenHelper.renderPanel(guiGraphics, guiLeft, guiTop, windowWidth, windowHeight);

        guiGraphics.drawCenteredString(font, title, guiLeft + windowWidth / 2, guiTop + 6,
            CTCreateScreenHelper.BANNER_TITLE_COLOR);

        CTCreateScreenHelper.renderSeparator(guiGraphics, guiLeft + 8, guiTop + 18, windowWidth - 16);

        // -----------------------------------------------------ANGLE RANGE-----------------------------------------------------
        boolean rangeMode = angleMode == AngleMode.RANGE;
        if (rangeMode) {

            int sliderPanelX = bgX - 12;
            int sliderPanelW = 66;
            CTCreateScreenHelper.renderInset(guiGraphics, sliderPanelX, guiTop + 24, sliderPanelW, 118, false, false);

            guiGraphics.blit(CTCreateScreenHelper.ALTITUDE_TEX,
                bgX, barTop, ALT_TRACK_W, BAR_HEIGHT,
                0, 0, ALT_TRACK_W, ALT_TRACK_SH, 256, 256);

            renderBarFillAndHandle(guiGraphics,
                bgX + LEFT_LIT_OFF,  bgX + LEFT_GRAB_OFF,  minAngleDeg,
                isOverLeftSlider(mouseX, mouseY));
            renderBarFillAndHandle(guiGraphics,
                bgX + RIGHT_LIT_OFF, bgX + RIGHT_GRAB_OFF, maxAngleDeg,
                isOverRightSlider(mouseX, mouseY));

            renderHandleLabel(guiGraphics, Component.literal("Min"), bgX + LEFT_GRAB_OFF, minAngleDeg);
            renderHandleLabel(guiGraphics, Component.literal("Max"), bgX + RIGHT_GRAB_OFF, maxAngleDeg);
        } else {

            int infoX = guiLeft + 26;
            int infoY = guiTop + 34;
            int infoW = windowWidth - 52;
            int infoH = 42;
            CTCreateScreenHelper.renderInset(guiGraphics, infoX, infoY, infoW, infoH, false, false);
            Component heading = Component.translatable("createthrusters.thruster_bearing.angle_mode")
                .copy().append(Component.literal(": "))
                .append(Component.translatable("createthrusters.thruster_bearing.angle_mode.swivel"));
            guiGraphics.drawCenteredString(font,
                heading,
                infoX + infoW / 2,
                infoY + 8,
                CTCreateScreenHelper.LABEL_COLOR);
            guiGraphics.drawCenteredString(font,
                Component.translatable("createthrusters.thruster_bearing.angle_mode.swivel_hint"),
                infoX + infoW / 2,
                infoY + 22,
                CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
        }

        int textX = guiLeft + 100;
        if (rangeMode) {
            if (angleEditor == null || !editingMinAngle) {
                guiGraphics.drawString(font,
                    Component.translatable("createthrusters.thruster_bearing.min_angle"),
                    textX, guiTop + 34, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                guiGraphics.drawString(font, formatAngle(minAngleDeg), textX, guiTop + 46, CTCreateScreenHelper.VALUE_COLOR, false);
            }
            if (angleEditor == null || editingMinAngle) {
                guiGraphics.drawString(font,
                    Component.translatable("createthrusters.thruster_bearing.max_angle"),
                    textX, guiTop + 60, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                guiGraphics.drawString(font, formatAngle(maxAngleDeg), textX, guiTop + 72, CTCreateScreenHelper.VALUE_COLOR, false);
            }
            if (angleEditor != null) {
                angleEditor.render(guiGraphics, mouseX, mouseY, partialTicks);
            }
        }

        CTCreateScreenHelper.renderSeparator(guiGraphics, textX, guiTop + 86, windowWidth - (textX - guiLeft) - 14);

        guiGraphics.drawString(font,
                Component.translatable("createthrusters.thruster_bearing.control_mode"),
                textX, guiTop + 90, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, modeX, modeY, BUTTON_WIDTH, 18,
                Component.translatable(getModeTranslationKey(controlMode)),
                inside(mouseX, mouseY, modeX, modeY, BUTTON_WIDTH, 18), true, false);

        guiGraphics.drawString(font,
            Component.translatable("createthrusters.thruster_bearing.angle_mode"),
            textX, guiTop + 116, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);

        for (int i = 0; i < 2; i++) {
            AngleMode option = i == 0 ? AngleMode.RANGE : AngleMode.SWIVEL;
            int optionX = angleModeOptionX(i);
            int optionWidth = angleModeOptionWidth(i);
            boolean hovered = inside(mouseX, mouseY, optionX, optY, optionWidth, ANGLE_OPTION_HEIGHT);
            CTCreateScreenHelper.renderTextButton(guiGraphics, font,
                optionX, optY, optionWidth, ANGLE_OPTION_HEIGHT,
                    Component.translatable(getAngleModeTranslationKey(option)),
                    hovered, angleMode == option, false);
        }

        CTCreateScreenHelper.renderSeparator(guiGraphics, textX, guiTop + 148,
            windowWidth - (textX - guiLeft) - 14);
        guiGraphics.drawString(font,
            Component.translatable("createthrusters.thruster_bearing.invert"),
            textX, guiTop + 150, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, invertX, invertY, BUTTON_WIDTH, 18,
            Component.translatable(inverted
                ? "createthrusters.thruster_bearing.invert_on"
                : "createthrusters.thruster_bearing.invert_off"),
            inside(mouseX, mouseY, invertX, invertY, BUTTON_WIDTH, 18), true, inverted);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn != 0) {
            return super.mouseClicked(screenMouseX, screenMouseY, btn);
        }

        if (angleEditor != null) {
            if (angleEditor.mouseClicked(mouseX, mouseY, btn)) {
                return true;
            }
            commitAngleEditor();
        }
        if (angleMode == AngleMode.RANGE
                && inside(mouseX, mouseY, guiLeft + 100, guiTop + 32,
                ANGLE_LABEL_WIDTH, ANGLE_LABEL_HEIGHT)) {
            openAngleEditor(true);
            return true;
        }
        if (angleMode == AngleMode.RANGE
                && inside(mouseX, mouseY, guiLeft + 100, guiTop + 58,
                ANGLE_LABEL_WIDTH, ANGLE_LABEL_HEIGHT)) {
            openAngleEditor(false);
            return true;
        }
        if (angleMode == AngleMode.RANGE && isOverLeftSlider(mouseX, mouseY)) {
            dragMin = true;
            beginAngleDrag(mouseY);
            updateDraggedValue(mouseY);
            return true;
        }
        if (angleMode == AngleMode.RANGE && isOverRightSlider(mouseX, mouseY)) {
            dragMax = true;
            beginAngleDrag(mouseY);
            updateDraggedValue(mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, modeX, modeY, BUTTON_WIDTH, 18)) {
            controlMode = nextControlMode(controlMode);
            dirty = true;
            return true;
        }
        for (int i = 0; i < 2; i++) {
            AngleMode option = i == 0 ? AngleMode.RANGE : AngleMode.SWIVEL;
            int optionX = angleModeOptionX(i);
            if (inside(mouseX, mouseY, optionX, optY, angleModeOptionWidth(i), ANGLE_OPTION_HEIGHT)) {
                if (angleMode != option) {
                    angleMode = option;
                    dirty = true;
                }
                return true;
            }
        }
        if (inside(mouseX, mouseY, invertX, invertY, BUTTON_WIDTH, 18)) {
            inverted = !inverted;
            dirty = true;
            return true;
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
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
                angleEditor = null;
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
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0 && (dragMin || dragMax)) {
            updateDraggedValue(mouseY);
            return true;
        }
        return super.mouseDragged(screenMouseX, screenMouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        boolean wasDragging = dragMin || dragMax;
        dragMin = false;
        dragMax = false;
        if (wasDragging) {
            sendRangeUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle screen removal
    @Override
    public void removed() {
        commitAngleEditor();
        sendRangeUpdate();
        super.removed();
    }

    // Open one inline angle editor
    private void openAngleEditor(boolean minimum) {
        editingMinAngle = minimum;
        int y = guiTop + (minimum ? 34 : 60);
        angleEditor = new EditBox(font, guiLeft + 100, y, ANGLE_LABEL_WIDTH, 20,
                Component.translatable(minimum
                        ? "createthrusters.thruster_bearing.min_angle"
                        : "createthrusters.thruster_bearing.max_angle"));
        angleEditor.setMaxLength(16);
        angleEditor.setFilter(ThrusterBearingRangeScreen::isPartialAngle);
        angleEditor.setValue(String.format(Locale.ROOT, "%.1f", minimum ? minAngleDeg : maxAngleDeg));
        angleEditor.setCursorPosition(angleEditor.getValue().length());
        angleEditor.setHighlightPos(0);
        angleEditor.setFocused(true);
    }

    // Commit the current inline angle editor
    private void commitAngleEditor() {
        if (angleEditor == null) return;
        try {
            double value = Mth.clamp(Double.parseDouble(angleEditor.getValue()), -limit, limit);
            if (editingMinAngle) {
                minAngleDeg = Math.min(value, maxAngleDeg);
            } else {
                maxAngleDeg = Math.max(value, minAngleDeg);
            }
            dirty = true;
        } catch (NumberFormatException ignored) {
        }
        angleEditor = null;
        sendRangeUpdate();
    }

    // Check whether text is a valid partial angle
    private static boolean isPartialAngle(String value) {
        return value != null && value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)?");
    }

    // Update the dragged value
    private void updateDraggedValue(double mouseY) {
        double directAngle = angleFromMouseY(mouseY);
        double startDirectAngle = angleFromMouseY(dragStartMouseY);
        if (dragMin) {
            double angle = CTSliderInputHelper.adjustDegrees(directAngle, startDirectAngle, dragStartMinAngleDeg,
                    -limit, limit);
            minAngleDeg = Math.min(angle, maxAngleDeg);
        } else if (dragMax) {
            double angle = CTSliderInputHelper.adjustDegrees(directAngle, startDirectAngle, dragStartMaxAngleDeg,
                    -limit, limit);
            maxAngleDeg = Math.max(angle, minAngleDeg);
        }
        dirty = true;
    }

    // Begin the angle drag
    private void beginAngleDrag(double mouseY) {
        dragStartMouseY = mouseY;
        dragStartMinAngleDeg = minAngleDeg;
        dragStartMaxAngleDeg = maxAngleDeg;
    }

    // Get the angle from mouse y
    private double angleFromMouseY(double mouseY) {
        double normalized = 1.0D - Mth.clamp((mouseY - barTop) / BAR_HEIGHT, 0.0D, 1.0D);
        return Mth.clamp(Mth.lerp(normalized, -limit, limit), -limit, limit);
    }

    // Get the handle center y
    private int getHandleCenterY(double angleDeg) {
        double normalized = Mth.clamp((angleDeg + limit) / (limit * 2.0D), 0.0D, 1.0D);
        return barTop + (int) Math.round((1.0D - normalized) * BAR_HEIGHT);
    }

    // Check if this is over the left slider
    private boolean isOverLeftSlider(double mx, double my) {
        int grabX = bgX + LEFT_GRAB_OFF;
        int grabY = getHandleCenterY(minAngleDeg) - ALT_GRAB_H / 2 - 1;
        return inside(mx, my, grabX - 1, grabY, ALT_GRAB_W + 2, ALT_GRAB_H + 2);
    }

    // Check if this is over the right slider
    private boolean isOverRightSlider(double mx, double my) {
        int grabX = bgX + RIGHT_GRAB_OFF;
        int grabY = getHandleCenterY(maxAngleDeg) - ALT_GRAB_H / 2 - 1;
        return inside(mx, my, grabX - 1, grabY, ALT_GRAB_W + 2, ALT_GRAB_H + 2);
    }

    // Draw the bar fill and handle
    private void renderBarFillAndHandle(GuiGraphics g, int litX, int grabX,
                                        double angleDeg, boolean hovered) {
        double normalized = Mth.clamp((angleDeg + limit) / (limit * 2.0D), 0.0D, 1.0D);

        int litH = (int) Math.round(normalized * BAR_HEIGHT);
        if (litH > 0) {
            int litSrcY = (int) Math.round((1.0D - normalized) * ALT_LIT_SH);
            int litSrcH = (int) Math.round(normalized * ALT_LIT_SH);
            g.blit(CTCreateScreenHelper.ALTITUDE_TEX,
                    litX, barTop + BAR_HEIGHT - litH, ALT_LIT_W, litH,
                    ALT_LIT_U, litSrcY, ALT_LIT_W, litSrcH,
                    256, 256);
        }

        int handleCY = getHandleCenterY(angleDeg);
        g.blit(CTCreateScreenHelper.ALTITUDE_TEX,
                grabX, handleCY - ALT_GRAB_H / 2,
                ALT_GRAB_W, ALT_GRAB_H,
                ALT_GRAB_U, ALT_GRAB_V, ALT_GRAB_W, ALT_GRAB_H,
                256, 256);
    }

    // Draw the handle label
    private void renderHandleLabel(GuiGraphics guiGraphics, Component label, int grabX, double angleDeg) {
        int handleCenterY = getHandleCenterY(angleDeg);
        int labelY = Mth.clamp(handleCenterY + ALT_GRAB_H / 2 + 2,
            barTop + 2,
            barTop + BAR_HEIGHT - 8);
        guiGraphics.drawCenteredString(font, label, grabX + ALT_GRAB_W / 2,
            labelY, CTCreateScreenHelper.LABEL_COLOR);
    }

        // Get the angle mode option x
        private int angleModeOptionX(int idx) {
            if (idx <= 0) {
                return optX;
            }
            return optX + RANGE_OPTION_WIDTH + ANGLE_OPTION_GAP;
        }

        // Get the angle mode option width
        private int angleModeOptionWidth(int idx) {
            return idx == 0 ? RANGE_OPTION_WIDTH : SWIVEL_OPTION_WIDTH;
        }

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Send the range update
    private void sendRangeUpdate() {
        if (!dirty) return;
        dirty = false;
        PacketDistributor.sendToServer(new ThrusterBearingRangePayload(
                be.getBlockPos(), minAngleDeg, maxAngleDeg, controlMode, angleMode, inverted));
    }

    // Format the angle
    private Component formatAngle(double angleDeg) {
        return Component.literal(String.format("%.1f deg", angleDeg));
    }

    // Get the next control mode
    private static ControlMode nextControlMode(ControlMode currentMode) {
        return switch (currentMode) {
            case AUTO -> ControlMode.REDSTONE;
            case REDSTONE -> ControlMode.COMPUTER;
            case COMPUTER -> ControlMode.SERVO;
            case SERVO -> ControlMode.AUTO;
        };
    }

    // Get the mode translation key
    private static String getModeTranslationKey(ControlMode controlMode) {
        return switch (controlMode) {
            case AUTO -> "createthrusters.thruster_bearing.control_mode.auto";
            case REDSTONE -> "createthrusters.thruster_bearing.control_mode.redstone";
            case COMPUTER -> "createthrusters.thruster_bearing.control_mode.computer";
            case SERVO -> "createthrusters.thruster_bearing.control_mode.servo";
        };
    }

    // Get the next angle mode
    @SuppressWarnings("unused")
    private static AngleMode nextAngleMode(AngleMode currentMode) {
        return currentMode == AngleMode.RANGE ? AngleMode.SWIVEL : AngleMode.RANGE;
    }

    // Get the angle mode translation key
    private static String getAngleModeTranslationKey(AngleMode angleMode) {
        return switch (angleMode) {
            case RANGE -> "createthrusters.thruster_bearing.angle_mode.range";
            case SWIVEL -> "createthrusters.thruster_bearing.angle_mode.swivel";
        };
    }
}
