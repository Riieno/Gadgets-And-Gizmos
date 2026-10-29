package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.AngleMode;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.ControlMode;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.DualThumbSlider;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedRangeSliderStyle;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterBearingRangePayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// Configure servo steering with a shared range rail and editable angle stops
public class ThrusterBearingRangeScreen extends AbstractSimiScreen{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 214;
    private static final int HEIGHT = 185;
    private static final int TAB_Y = 26;
    private static final int TAB_WIDTH = 66;
    private static final int TAB_HEIGHT = 15;
    private static final int FIELD_X = 59;
    private static final int FIELD_WIDTH = 138;
    private static final int FIELD_HEIGHT = 12;
    private static final int MIN_FIELD_Y = 59;
    private static final int MAX_FIELD_Y = 80;
    private static final int CONTROL_X = 57;
    private static final int INVERT_X = 135;
    private static final int BUTTON_Y = 134;
    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SLIDER_X = 25;
    private static final int SLIDER_Y = 69;
    private static final int SLIDER_WIDTH = 8;
    private static final int SLIDER_HEIGHT = 76;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters",
            "textures/gui/servo_bearing.png");
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(TEXTURE, 280, 240, 66, 0, WIDTH, HEIGHT);
    private static final GuiTextureRegion RANGE_PANEL = new GuiTextureRegion(TEXTURE, 280, 240, 0, 78, 38, 103);
    private static final PaintedRangeSliderStyle RANGE_STYLE = new PaintedRangeSliderStyle(
            new GuiTextureRegion(TEXTURE, 280, 240, 0, 0, 8, 76),
            new GuiTextureRegion(TEXTURE, 280, 240, 10, 0, 14, 7),
            new GuiTextureRegion(TEXTURE, 280, 240, 10, 8, 14, 7),
            PaintedRangeSliderStyle.Axis.VERTICAL, false);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final ThrusterBearingBlockEntity be;
    private final DualThumbSlider range;
    private final CTScalableGui scalableGui = new CTScalableGui();
    private ControlMode controlMode;
    private AngleMode angleMode;
    private boolean inverted;
    private boolean dirty;
    private EditBox angleEditor;
    private boolean editingMinAngle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the bearing's current settings when opening either steering mode
    public ThrusterBearingRangeScreen(ThrusterBearingBlockEntity blockEntity){
        super(Component.translatable("block.createthrusters.thruster_bearing"));
        be = blockEntity;
        double limit = CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        range = new DualThumbSlider(-limit, limit, blockEntity.getMinAngleDegrees(), blockEntity.getMaxAngleDegrees());
        controlMode = blockEntity.getControlMode();
        angleMode = blockEntity.getAngleMode();
        inverted = blockEntity.isInverted();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Fit the painted frame and preserve an unfinished typed value across a resize
    @Override
    protected void init(){
        super.init();
        scalableGui.update(guiLeft, guiTop, windowWidth, windowHeight, width, height);
        range.endDrag();
        if(angleEditor != null){
            String val = angleEditor.getValue();
            openAngleEditor(editingMinAngle);
            angleEditor.setValue(val);
        }
    }

    // Draw the two layouts over the same cropped atlas frame
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks){
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try{
            BACKGROUND.draw(graphics, guiLeft, guiTop);
            text(graphics, title, 8, 1, WIDTH - 16, 11, TEXT_COLOR, true);
            for(int idx = 0; idx < 2; idx++){
                AngleMode option = idx == 0 ? AngleMode.RANGE : AngleMode.SWIVEL;
                button(graphics, Component.translatable(getAngleModeTranslationKey(option)),
                        idx == 0 ? 60 : 136, TAB_Y, TAB_WIDTH, TAB_HEIGHT, mouseX, mouseY, angleMode == option);
            }
            if(angleMode == AngleMode.RANGE){
                RANGE_PANEL.draw(graphics, guiLeft + 10, guiTop + 54);
                double fraction = sliderFraction(mouseX, mouseY);
                DualThumbSlider.Thumb highlighted = range.dragging() ? range.selectedThumb()
                        : RANGE_STYLE.contains(mouseX, mouseY, guiLeft + SLIDER_X, guiTop + SLIDER_Y,
                                SLIDER_WIDTH, SLIDER_HEIGHT) ? range.nearestThumb(fraction) : null;
                RANGE_STYLE.draw(graphics, guiLeft + SLIDER_X, guiTop + SLIDER_Y,
                        SLIDER_WIDTH, SLIDER_HEIGHT, range, highlighted);
                if(angleEditor == null || !editingMinAngle){
                    text(graphics, angleLabel(true), FIELD_X, MIN_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, TEXT_COLOR, false);
                }
                if(angleEditor == null || editingMinAngle){
                    text(graphics, angleLabel(false), FIELD_X, MAX_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, TEXT_COLOR, false);
                }
                if(angleEditor != null) angleEditor.render(graphics, mouseX, mouseY, partialTicks);
            }else{
                Component heading = Component.translatable("createthrusters.thruster_bearing.angle_mode")
                        .append(": ").append(Component.translatable(getAngleModeTranslationKey(AngleMode.SWIVEL)));
                text(graphics, heading, FIELD_X, MIN_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, TEXT_COLOR, false);
                text(graphics, Component.translatable("createthrusters.thruster_bearing.angle_mode.swivel_hint"),
                        FIELD_X, MAX_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, TEXT_COLOR, false);
            }
            text(graphics, Component.translatable("createthrusters.thruster_bearing.config_screen.control_mode_label"),
                    CONTROL_X, 120, BUTTON_WIDTH, 12, TEXT_COLOR, true);
            text(graphics, Component.translatable("createthrusters.thruster_bearing.config_screen.inverted_label"),
                    INVERT_X, 120, BUTTON_WIDTH, 12, TEXT_COLOR, true);
            button(graphics, Component.translatable(getModeTranslationKey(controlMode)),
                    CONTROL_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY, true);
            button(graphics, Component.translatable("createthrusters.thruster_bearing.config_screen.inverted." + inverted),
                    INVERT_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY, true);
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Share the painted hit areas with tabs, typed fields and the range rail
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn){
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if(btn != 0) return super.mouseClicked(screenMouseX, screenMouseY, btn);
        if(angleEditor != null){
            if(angleEditor.mouseClicked(mouseX, mouseY, btn)) return true;
            commitAngleEditor();
        }
        for(int idx = 0; idx < 2; idx++){
            if(!inside(mouseX, mouseY, guiLeft + (idx == 0 ? 60 : 136), guiTop + TAB_Y, TAB_WIDTH, TAB_HEIGHT)) continue;
            AngleMode next = idx == 0 ? AngleMode.RANGE : AngleMode.SWIVEL;
            if(angleMode != next){
                angleMode = next;
                range.endDrag();
                dirty = true;
            }
            return true;
        }
        if(angleMode == AngleMode.RANGE){
            if(inside(mouseX, mouseY, guiLeft + FIELD_X, guiTop + MIN_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT)){
                openAngleEditor(true);
                return true;
            }
            if(inside(mouseX, mouseY, guiLeft + FIELD_X, guiTop + MAX_FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT)){
                openAngleEditor(false);
                return true;
            }
            int x = guiLeft + SLIDER_X;
            int y = guiTop + SLIDER_Y;
            if(RANGE_STYLE.contains(mouseX, mouseY, x, y, SLIDER_WIDTH, SLIDER_HEIGHT)){
                boolean overThumb = RANGE_STYLE.isOverThumb(mouseX, mouseY, x, y, SLIDER_WIDTH, SLIDER_HEIGHT, range.lowerFraction())
                        || RANGE_STYLE.isOverThumb(mouseX, mouseY, x, y, SLIDER_WIDTH, SLIDER_HEIGHT, range.upperFraction());
                dirty |= range.beginDrag(sliderFraction(mouseX, mouseY), !overThumb);
                return true;
            }
        }
        if(inside(mouseX, mouseY, guiLeft + CONTROL_X, guiTop + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)){
            controlMode = nextControlMode(controlMode);
            dirty = true;
            return true;
        }
        if(inside(mouseX, mouseY, guiLeft + INVERT_X, guiTop + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)){
            inverted = !inverted;
            dirty = true;
            return true;
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Keep the existing fine movement and five-degree snapping modifiers
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY){
        if(btn == 0 && range.dragging()){
            double fraction = sliderFraction(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
            dirty |= range.dragTo(fraction, hasShiftDown() && !hasControlDown() ? 0.25 : 1, hasControlDown() ? 5 : 0);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    // Persist both stops together when releasing the selected thumb
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn){
        if(btn == 0 && range.endDrag()){
            sendRangeUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Commit a typed value with Enter and cancel it with Escape
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(angleEditor == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER){
            commitAngleEditor();
            return true;
        }
        if(keyCode == GLFW.GLFW_KEY_ESCAPE){
            angleEditor = null;
            return true;
        }
        return angleEditor.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers){
        return angleEditor != null ? angleEditor.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
    }

    // Retain edits when leaving the screen
    @Override
    public void removed(){
        commitAngleEditor();
        range.endDrag();
        sendRangeUpdate();
        super.removed();
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Edit a value inside its painted well
    private void openAngleEditor(boolean minimum){
        editingMinAngle = minimum;
        angleEditor = new EditBox(font, guiLeft + FIELD_X + 2, guiTop + (minimum ? MIN_FIELD_Y : MAX_FIELD_Y),
                FIELD_WIDTH - 6, FIELD_HEIGHT, Component.translatable(minimum
                        ? "createthrusters.thruster_bearing.min_angle" : "createthrusters.thruster_bearing.max_angle"));
        angleEditor.setBordered(false);
        angleEditor.setTextColor(TEXT_COLOR);
        angleEditor.setMaxLength(16);
        angleEditor.setFilter(ThrusterBearingRangeScreen::isPartialAngle);
        angleEditor.setValue(String.format(Locale.ROOT, "%.1f", minimum ? range.lower() : range.upper()));
        angleEditor.setCursorPosition(angleEditor.getValue().length());
        angleEditor.setHighlightPos(0);
        angleEditor.setFocused(true);
    }

    // Clamp typed stops through the same ordering rules as the thumbs
    private void commitAngleEditor(){
        if(angleEditor == null) return;
        try{
            double val = Double.parseDouble(angleEditor.getValue());
            if(Double.isFinite(val)) dirty |= editingMinAngle ? range.setLower(val) : range.setUpper(val);
        }catch(NumberFormatException ignored){
        }
        angleEditor = null;
        sendRangeUpdate();
    }

    private static boolean isPartialAngle(String val){
        return val != null && val.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)?");
    }

    private double sliderFraction(double mouseX, double mouseY){
        return RANGE_STYLE.fractionAt(mouseX, mouseY, guiLeft + SLIDER_X, guiTop + SLIDER_Y, SLIDER_WIDTH, SLIDER_HEIGHT);
    }

    private Component angleLabel(boolean minimum){
        double val = minimum ? range.lower() : range.upper();
        String formatted = String.format(Locale.ROOT, Math.abs(val - Math.rint(val)) < 0.0001 ? "%.0f deg" : "%.1f deg", val);
        return Component.translatable(minimum ? "createthrusters.thruster_bearing.min_angle"
                : "createthrusters.thruster_bearing.max_angle").append(": ").append(formatted);
    }

    // Paint only the hover state and label over each baked button
    private void button(GuiGraphics graphics, Component label, int x, int y, int width, int height,
                        int mouseX, int mouseY, boolean selected){
        if(inside(mouseX, mouseY, guiLeft + x, guiTop + y, width, height)){
            graphics.fill(guiLeft + x + 1, guiTop + y + 1, guiLeft + x + width - 1, guiTop + y + height - 1, 0x22FFFFFF);
        }
        text(graphics, label, x + 2, y + 1, width - 4, height - 2, selected ? TEXT_COLOR : 0xFF303030, true);
    }

    private void text(GuiGraphics graphics, Component label, int x, int y, int width, int height, int color, boolean centered){
        GuiControlPreviews.drawFittedText(graphics, font, label, guiLeft + x, guiTop + y, width, height, 1, color, centered);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height){
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Preserve the existing network contract and serialized modes
    private void sendRangeUpdate(){
        if(!dirty) return;
        dirty = false;
        PacketDistributor.sendToServer(new ThrusterBearingRangePayload(
                be.getBlockPos(), range.lower(), range.upper(), controlMode, angleMode, inverted));
    }

    private static ControlMode nextControlMode(ControlMode currentMode){
        return switch(currentMode){
            case AUTO -> ControlMode.REDSTONE;
            case REDSTONE -> ControlMode.COMPUTER;
            case COMPUTER -> ControlMode.SERVO;
            case SERVO -> ControlMode.AUTO;
        };
    }

    private static String getModeTranslationKey(ControlMode mode){
        return switch(mode){
            case AUTO -> "createthrusters.thruster_bearing.control_mode.auto";
            case REDSTONE -> "createthrusters.thruster_bearing.control_mode.redstone";
            case COMPUTER -> "createthrusters.thruster_bearing.control_mode.computer";
            case SERVO -> "createthrusters.thruster_bearing.control_mode.servo";
        };
    }

    private static String getAngleModeTranslationKey(AngleMode mode){
        return mode == AngleMode.RANGE ? "createthrusters.thruster_bearing.angle_mode.range"
                : "createthrusters.thruster_bearing.angle_mode.swivel";
    }
}
