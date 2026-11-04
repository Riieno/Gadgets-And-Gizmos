package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkMenu;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.DualThumbSlider;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiDropdownControl;
import com.rieno.gadgetsandgizmos.lib.client.ui.InlineTextEditor;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedRangeSliderStyle;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogComponent;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.GyroscopeLinkConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// Configure Advanced Data Link frequencies and cardinal mappings on one painted screen
public class GyroscopeLinkConfigScreen extends AbstractSimiContainerScreen<GyroscopeLinkMenu>{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 354;
    private static final int HEIGHT = 283;
    private static final int LIVE_X = 264;
    private static final int STATIC_X = 306;
    private static final int MODE_Y = 24;
    private static final int MODE_WIDTH = 39;
    private static final int BUTTON_HEIGHT = 10;
    private static final int FACE_X = 140;
    private static final int FACE_Y = 54;
    private static final int FACE_WIDTH = 36;
    private static final int FACE_STEP = 12;
    private static final int OUTPUT_X = 210;
    private static final int REDSTONE_X = 280;
    private static final int STATE_Y = 66;
    private static final int RANGE_X = 212;
    private static final int RANGE_WIDTH = 106;
    private static final int RANGE_HEIGHT = 8;
    private static final int MIN_INPUT_X = 187;
    private static final int MAX_INPUT_X = 323;
    private static final int RANGE_INPUT_WIDTH = 20;
    private static final int SOURCE_COMPONENT_X = 85;
    private static final int SOURCE_COMPONENT_Y = 165;
    private static final int SOURCE_COMPONENT_WIDTH = 100;
    private static final int SOURCE_COMPONENT_HEIGHT = 14;
    private static final int CONFIRM_X = 332;
    private static final int CONFIRM_Y = 163;
    private static final int CONFIRM_SIZE = 18;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final int MIN_COLOR = 0xFFE06B72;
    private static final int MAX_COLOR = 0xFF7191E3;
    private static final List<Direction> FACES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final List<DirectionalAnalogComponent> SOURCE_COMPONENTS = List.of(DirectionalAnalogComponent.values());
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters",
            "textures/gui/advanced_data_link.png");
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(TEXTURE, 354, 323, 0, 0, WIDTH, HEIGHT);
    private static final PaintedRangeSliderStyle RANGE_STYLE = new PaintedRangeSliderStyle(
            new GuiTextureRegion(TEXTURE, 354, 323, 0, 296, 106, 8),
            new GuiTextureRegion(TEXTURE, 354, 323, 9, 307, 7, 14),
            new GuiTextureRegion(TEXTURE, 354, 323, 0, 307, 7, 14),
            PaintedRangeSliderStyle.Axis.HORIZONTAL, false);
    private static final GuiNineSlice COMPONENT_BUTTON = PaintedGuiStyle.slice(
            new GuiTextureRegion(TEXTURE, 354, 323, 210, 66, 39, 10), 2, 2, 2, 2);
    private static final GuiDropdownControl.Colors DROPDOWN_COLORS = new GuiDropdownControl.Colors(0, 0, TEXT_COLOR, TEXT_COLOR);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final CTScalableGui scalableGui = new CTScalableGui();
    private final EnumMap<Direction, GyroscopeLinkBlockEntity.CardinalOutputConfig> cardinalConfigs = new EnumMap<>(Direction.class);
    private final EnumMap<RangeField, DualThumbSlider> rangeSliders = new EnumMap<>(RangeField.class);
    private GyroscopeLinkBlockEntity.TrackingMode trackingMode;
    private Direction selectedDirection = Direction.NORTH;
    private RangeField activeRangeField;
    private InlineTextEditor numberEditor;
    private RangeField editingRangeField;
    private boolean editingMinimum;
    private boolean sourceComponentDropdownOpen;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Retain every face's settings independently while editing the selected face
    public GyroscopeLinkConfigScreen(GyroscopeLinkMenu menu, Inventory playerInventory, Component title){
        super(menu, playerInventory, Component.translatable("block.createthrusters.gyroscope_link"));
        trackingMode = menu.getInitialTrackingMode();
        GyroscopeLinkBlockEntity blockEntity = menu.getMenuConfigTargetBlockEntity();
        for(Direction dir : FACES){
            cardinalConfigs.put(dir, blockEntity == null
                    ? new GyroscopeLinkBlockEntity.CardinalOutputConfig(GyroscopeLinkBlockEntity.defaultSourceComponent(dir))
                    : blockEntity.getCardinalOutputConfig(dir));
        }
        setWindowSize(WIDTH, HEIGHT);
    }

    // Keep inventory and frequency slots usable beside the tracking controls
    @Override
    protected void init(){
        super.init();
        finishRangeDrag();
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
        menu.setFrequencySlotsActive(true);
        syncRangeSliders();
    }

    // Crop the frame above the fill and thumb partials
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY){
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try{
            BACKGROUND.draw(graphics, leftPos, topPos);
            text(graphics, title, 8, 1, WIDTH - 16, 11, 1, TEXT_COLOR, true);
            text(graphics, Component.translatable("createthrusters.gyroscope_link.tab.frequency").append(":"),
                    8, 23, 116, 11, 0.8F, TEXT_COLOR, true);
            text(graphics, Component.translatable("createthrusters.gyroscope_link.config.mode"),
                    142, 23, 116, 11, 0.8F, TEXT_COLOR, false);
            paintedButton(graphics, Component.translatable("createthrusters.gyroscope_link.mode.live"),
                    LIVE_X, MODE_Y, MODE_WIDTH, BUTTON_HEIGHT,
                    trackingMode == GyroscopeLinkBlockEntity.TrackingMode.LIVE, mouseX, mouseY);
            paintedButton(graphics, Component.translatable("createthrusters.gyroscope_link.mode.static"),
                    STATIC_X, MODE_Y, MODE_WIDTH, BUTTON_HEIGHT,
                    trackingMode == GyroscopeLinkBlockEntity.TrackingMode.STATIC, mouseX, mouseY);
            text(graphics, Component.translatable("createthrusters.gyroscope_link.config.servo_faces"),
                    138, 40, 40, 12, 0.8F, TEXT_COLOR, true);
            for(int idx = 0; idx < FACES.size(); idx++){
                Direction dir = FACES.get(idx);
                paintedButton(graphics, directionLabel(dir), FACE_X, FACE_Y + idx * FACE_STEP,
                        FACE_WIDTH, BUTTON_HEIGHT, dir == selectedDirection, mouseX, mouseY);
            }
            text(graphics, Component.translatable("createthrusters.gyroscope_link.config.selected_face", directionLabel(selectedDirection)),
                    186, 40, 158, 10, 0.8F, TEXT_COLOR, true);
            var config = getSelectedConfig();
            text(graphics, Component.translatable("createthrusters.gyroscope_link.config.output_state"),
                    200, 56, 60, 9, 0.8F, TEXT_COLOR, true);
            text(graphics, Component.translatable("createthrusters.gyroscope_link.config.redstone_output"),
                    268, 56, 64, 9, 0.8F, TEXT_COLOR, true);
            paintedButton(graphics, Component.translatable(config.enabled
                            ? "createthrusters.gyroscope_link.config.enabled" : "createthrusters.gyroscope_link.config.disabled"),
                    OUTPUT_X, STATE_Y, MODE_WIDTH, BUTTON_HEIGHT, config.enabled, mouseX, mouseY);
            paintedButton(graphics, Component.translatable(config.redstoneEnabled ? "options.on" : "options.off"),
                    REDSTONE_X, STATE_Y, MODE_WIDTH, BUTTON_HEIGHT, config.redstoneEnabled, mouseX, mouseY);
            for(RangeField field : RangeField.values()) renderRange(graphics, field, mouseX, mouseY);
            if(numberEditor != null){
                positionNumberEditor();
                numberEditor.render(graphics, mouseX, mouseY, partialTicks);
            }
            renderSourceComponentField(graphics, mouseX, mouseY);
            if(inside(mouseX, mouseY, leftPos + CONFIRM_X, topPos + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                graphics.fill(leftPos + CONFIRM_X, topPos + CONFIRM_Y, leftPos + CONFIRM_X + CONFIRM_SIZE,
                        topPos + CONFIRM_Y + CONFIRM_SIZE, 0x22FFFFFF);
            }
            renderHints(graphics, mouseX, mouseY);
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Keep dropdown options above inventory icons and the other controls
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks){
        super.render(graphics, mouseX, mouseY, partialTicks);
        if(!sourceComponentDropdownOpen || !menu.hasAnalogueJoystickSource()) return;
        scalableGui.push(graphics);
        try{
            graphics.pose().translate(0, 0, 400);
            for(int idx = 0; idx < SOURCE_COMPONENTS.size(); idx++){
                DirectionalAnalogComponent component = SOURCE_COMPONENTS.get(idx);
                int y = componentOptionY(idx);
                COMPONENT_BUTTON.draw(graphics, leftPos + SOURCE_COMPONENT_X, topPos + y,
                        SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT);
                paintedButton(graphics, sourceComponentLabel(component), SOURCE_COMPONENT_X, y,
                        SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT,
                        component == getSelectedConfig().sourceComponent, scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
            }
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Route painted controls before allowing the normal inventory interaction
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn){
        double localX = scalableGui.mouseX(mouseX);
        double localY = scalableGui.mouseY(mouseY);
        if(numberEditor != null && numberEditor.mouseClicked(localX, localY, btn)) return true;
        if(sourceComponentDropdownOpen){
            for(int idx = 0; idx < SOURCE_COMPONENTS.size(); idx++){
                if(!inside(localX, localY, leftPos + SOURCE_COMPONENT_X, topPos + componentOptionY(idx),
                        SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT)) continue;
                if(btn == 0){
                    DirectionalAnalogComponent component = SOURCE_COMPONENTS.get(idx);
                    sourceComponentDropdownOpen = false;
                    updateSelectedConfig(config -> config.sourceComponent = component);
                }
                return true;
            }
        }
        if(btn != 0) return super.mouseClicked(mouseX, mouseY, btn);
        if(menu.hasAnalogueJoystickSource() && inside(localX, localY, leftPos + SOURCE_COMPONENT_X,
                topPos + SOURCE_COMPONENT_Y, SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT)){
            sourceComponentDropdownOpen = !sourceComponentDropdownOpen;
            return true;
        }
        sourceComponentDropdownOpen = false;
        if(inside(localX, localY, leftPos + CONFIRM_X, topPos + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
            onClose();
            return true;
        }
        if(inside(localX, localY, leftPos + LIVE_X, topPos + MODE_Y, MODE_WIDTH, BUTTON_HEIGHT)
                || inside(localX, localY, leftPos + STATIC_X, topPos + MODE_Y, MODE_WIDTH, BUTTON_HEIGHT)){
            trackingMode = localX < leftPos + STATIC_X ? GyroscopeLinkBlockEntity.TrackingMode.LIVE
                    : GyroscopeLinkBlockEntity.TrackingMode.STATIC;
            sendConfigUpdate();
            return true;
        }
        for(int idx = 0; idx < FACES.size(); idx++){
            if(!inside(localX, localY, leftPos + FACE_X, topPos + FACE_Y + idx * FACE_STEP, FACE_WIDTH, BUTTON_HEIGHT)) continue;
            finishRangeDrag();
            selectedDirection = FACES.get(idx);
            syncRangeSliders();
            return true;
        }
        if(inside(localX, localY, leftPos + OUTPUT_X, topPos + STATE_Y, MODE_WIDTH, BUTTON_HEIGHT)){
            updateSelectedConfig(config -> config.enabled = !config.enabled);
            return true;
        }
        if(inside(localX, localY, leftPos + REDSTONE_X, topPos + STATE_Y, MODE_WIDTH, BUTTON_HEIGHT)){
            updateSelectedConfig(config -> config.redstoneEnabled = !config.redstoneEnabled);
            return true;
        }
        for(RangeField field : RangeField.values()){
            if(inside(localX, localY, leftPos + MIN_INPUT_X, topPos + field.y, RANGE_INPUT_WIDTH, RANGE_HEIGHT)){
                openNumberEditor(field, true);
                return true;
            }
            if(inside(localX, localY, leftPos + MAX_INPUT_X, topPos + field.y, RANGE_INPUT_WIDTH, RANGE_HEIGHT)){
                openNumberEditor(field, false);
                return true;
            }
            int x = leftPos + RANGE_X;
            int y = topPos + field.y;
            if(!RANGE_STYLE.contains(localX, localY, x, y, RANGE_WIDTH, RANGE_HEIGHT)) continue;
            DualThumbSlider slider = rangeSliders.get(field);
            boolean overThumb = RANGE_STYLE.isOverThumb(localX, localY, x, y, RANGE_WIDTH, RANGE_HEIGHT, slider.lowerFraction())
                    || RANGE_STYLE.isOverThumb(localX, localY, x, y, RANGE_WIDTH, RANGE_HEIGHT, slider.upperFraction());
            activeRangeField = field;
            if(slider.beginDrag(sliderFraction(field, localX, localY), !overThumb)) publishRange(field);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY){
        double localX = scalableGui.mouseX(mouseX);
        double localY = scalableGui.mouseY(mouseY);
        if(numberEditor != null && numberEditor.mouseDragged(localX, localY, btn)) return true;
        if(btn == 0 && activeRangeField != null){
            double step = activeRangeField == RangeField.SOURCE && menu.hasAnalogueJoystickSource() ? 0.05 : 5;
            if(rangeSliders.get(activeRangeField).dragTo(sliderFraction(activeRangeField, localX, localY),
                    hasShiftDown() && !hasControlDown() ? 0.25 : 1, hasControlDown() ? step : 0)){
                publishRange(activeRangeField);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn){
        if(numberEditor != null && numberEditor.mouseReleased(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY), btn)) return true;
        if(btn == 0 && activeRangeField != null){
            finishRangeDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Retain direct number entry and keyboard traversal through the six endpoints
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(numberEditor != null){
            if(keyCode == GLFW.GLFW_KEY_TAB){
                int idx = editingRangeField.ordinal() * 2 + (editingMinimum ? 0 : 1);
                numberEditor.accept();
                idx = Math.floorMod(idx + (hasShiftDown() ? -1 : 1), RangeField.values().length * 2);
                openNumberEditor(RangeField.values()[idx / 2], idx % 2 == 0);
                return true;
            }
            return numberEditor.keyPressed(keyCode, scanCode, modifiers);
        }
        if(keyCode == GLFW.GLFW_KEY_ESCAPE && sourceComponentDropdownOpen){
            sourceComponentDropdownOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers){
        if(numberEditor == null) return super.charTyped(codePoint, modifiers);
        if(Character.isDigit(codePoint) || codePoint == '-' || codePoint == '.') numberEditor.charTyped(codePoint, modifiers);
        return true;
    }

    @Override
    public void onClose(){
        if(numberEditor != null) numberEditor.accept();
        finishRangeDrag();
        sendConfigUpdate();
        super.onClose();
    }

    @Override
    public List<Rect2i> getExtraAreas(){ return scalableGui.extraAreas(); }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY){}

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot){
        scalableGui.pushFromOrigin(graphics, leftPos, topPos);
        try{ super.renderSlot(graphics, slot); }
        finally{ scalableGui.pop(graphics); }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick){
        scalableGui.pushFromOrigin(graphics, leftPos, topPos);
        try{ super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick); }
        finally{ scalableGui.pop(graphics); }
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY){
        return super.isHovering(x, y, width, height, scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Share one geometry between painted thumbs and pointer input
    private void renderRange(GuiGraphics graphics, RangeField field, int mouseX, int mouseY){
        text(graphics, Component.translatable(field.translationKey), RANGE_X, field.y - 11,
                RANGE_WIDTH, 9, 0.8F, TEXT_COLOR, true);
        DualThumbSlider slider = rangeSliders.get(field);
        DualThumbSlider.Thumb highlighted = activeRangeField == field ? slider.selectedThumb()
                : RANGE_STYLE.contains(mouseX, mouseY, leftPos + RANGE_X, topPos + field.y, RANGE_WIDTH, RANGE_HEIGHT)
                        ? slider.nearestThumb(sliderFraction(field, mouseX, mouseY)) : null;
        RANGE_STYLE.draw(graphics, leftPos + RANGE_X, topPos + field.y, RANGE_WIDTH, RANGE_HEIGHT, slider, highlighted);
        if(numberEditor == null || editingRangeField != field || !editingMinimum){
            text(graphics, Component.literal(String.format(Locale.ROOT, "%+.1f", getRangeValue(field, true))), MIN_INPUT_X, field.y,
                    RANGE_INPUT_WIDTH, RANGE_HEIGHT, 0.65F, MIN_COLOR, true);
        }
        if(numberEditor == null || editingRangeField != field || editingMinimum){
            text(graphics, Component.literal(String.format(Locale.ROOT, "%+.1f", getRangeValue(field, false))), MAX_INPUT_X, field.y,
                    RANGE_INPUT_WIDTH, RANGE_HEIGHT, 0.65F, MAX_COLOR, true);
        }
    }

    private double sliderFraction(RangeField field, double mouseX, double mouseY){
        return RANGE_STYLE.fractionAt(mouseX, mouseY, leftPos + RANGE_X, topPos + field.y, RANGE_WIDTH, RANGE_HEIGHT);
    }

    private void syncRangeSliders(){
        for(RangeField field : RangeField.values()){
            DualThumbSlider slider = rangeSliders.get(field);
            if(slider == null){
                rangeSliders.put(field, new DualThumbSlider(-field.limit, field.limit, getRangeValue(field, true), getRangeValue(field, false)));
            }else{
                slider.setRange(getRangeValue(field, true), getRangeValue(field, false));
            }
        }
    }

    private void publishRange(RangeField field){
        DualThumbSlider slider = rangeSliders.get(field);
        updateSelectedConfig(config -> {
            setRangeValue(config, field, true, slider.lower());
            setRangeValue(config, field, false, slider.upper());
        });
    }

    private void finishRangeDrag(){
        if(activeRangeField == null) return;
        rangeSliders.get(activeRangeField).endDrag();
        activeRangeField = null;
        sendConfigUpdate();
    }

    // Use the painted wells with the library editor's selection and clipboard behavior
    private void openNumberEditor(RangeField field, boolean minimum){
        finishRangeDrag();
        editingRangeField = field;
        editingMinimum = minimum;
        numberEditor = new InlineTextEditor(font, Component.translatable(field.translationKey),
                formatInputValue(getRangeValue(field, minimum)), 24,
                val -> { numberEditor = null; applyTypedRangeValue(field, minimum, val); },
                () -> numberEditor = null);
        numberEditor.setDecorated(false);
        numberEditor.setTextColor(minimum ? MIN_COLOR : MAX_COLOR);
        numberEditor.setRightAligned(!minimum);
        positionNumberEditor();
    }

    private void positionNumberEditor(){
        numberEditor.setBounds(leftPos + (editingMinimum ? MIN_INPUT_X : MAX_INPUT_X),
                topPos + editingRangeField.y + 1, RANGE_INPUT_WIDTH, 0.65F);
    }

    private void applyTypedRangeValue(RangeField field, boolean minimum, String text){
        try{
            double val = Double.parseDouble(text.strip());
            if(!Double.isFinite(val)) return;
            double bounded = Math.clamp(val, -field.limit, field.limit);
            updateSelectedConfig(config -> setRangeValue(config, field, minimum, bounded));
            syncRangeSliders();
        }catch(NumberFormatException ignored){}
    }

    // Preserve typed endpoint behavior by moving the counterpart when an endpoint passes it
    private void setRangeValue(GyroscopeLinkBlockEntity.CardinalOutputConfig config, RangeField field, boolean minimum, double val){
        switch(field){
            case SOURCE -> {
                if(minimum){ config.sourceMinDegrees = val; config.sourceMaxDegrees = Math.max(config.sourceMaxDegrees, val); }
                else{ config.sourceMaxDegrees = val; config.sourceMinDegrees = Math.min(config.sourceMinDegrees, val); }
            }
            case OUTPUT -> {
                if(minimum){ config.outputMin = val; config.outputMax = Math.max(config.outputMax, val); }
                else{ config.outputMax = val; config.outputMin = Math.min(config.outputMin, val); }
            }
            case CLAMP -> {
                if(minimum){ config.clampMin = val; config.clampMax = Math.max(config.clampMax, val); }
                else{ config.clampMax = val; config.clampMin = Math.min(config.clampMin, val); }
            }
        }
    }

    private double getRangeValue(RangeField field, boolean minimum){
        var config = getSelectedConfig();
        return switch(field){
            case SOURCE -> minimum ? config.sourceMinDegrees : config.sourceMaxDegrees;
            case OUTPUT -> minimum ? config.outputMin : config.outputMax;
            case CLAMP -> minimum ? config.clampMin : config.clampMax;
        };
    }

    private GyroscopeLinkBlockEntity.CardinalOutputConfig getSelectedConfig(){
        return cardinalConfigs.computeIfAbsent(selectedDirection, dir ->
                new GyroscopeLinkBlockEntity.CardinalOutputConfig(GyroscopeLinkBlockEntity.defaultSourceComponent(dir)));
    }

    private void updateSelectedConfig(Consumer<GyroscopeLinkBlockEntity.CardinalOutputConfig> mutator){
        var config = getSelectedConfig().copy();
        mutator.accept(config);
        cardinalConfigs.put(selectedDirection, config);
        sendConfigUpdate();
    }

    // Keep joystick component selection available without covering the range controls
    private void renderSourceComponentField(GuiGraphics graphics, int mouseX, int mouseY){
        if(!menu.hasAnalogueJoystickSource()) return;
        text(graphics, Component.translatable("createthrusters.gyroscope_link.config.joystick_axis"),
                8, SOURCE_COMPONENT_Y + 1, 72, SOURCE_COMPONENT_HEIGHT - 2, 0.8F, 0xFF303030, false);
        COMPONENT_BUTTON.draw(graphics, leftPos + SOURCE_COMPONENT_X, topPos + SOURCE_COMPONENT_Y,
                SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT);
        if(inside(mouseX, mouseY, leftPos + SOURCE_COMPONENT_X, topPos + SOURCE_COMPONENT_Y, SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT)){
            graphics.fill(leftPos + SOURCE_COMPONENT_X + 1, topPos + SOURCE_COMPONENT_Y + 1,
                    leftPos + SOURCE_COMPONENT_X + SOURCE_COMPONENT_WIDTH - 1,
                    topPos + SOURCE_COMPONENT_Y + SOURCE_COMPONENT_HEIGHT - 1, 0x22FFFFFF);
        }
        GuiDropdownControl.drawContents(graphics, font, sourceComponentLabel(getSelectedConfig().sourceComponent).getString(),
                leftPos + SOURCE_COMPONENT_X, topPos + SOURCE_COMPONENT_Y, SOURCE_COMPONENT_WIDTH, SOURCE_COMPONENT_HEIGHT,
                0.8, DROPDOWN_COLORS, true);
    }

    private static int componentOptionY(int idx){
        return SOURCE_COMPONENT_Y - SOURCE_COMPONENT_HEIGHT * (SOURCE_COMPONENTS.size() - idx);
    }

    private static Component sourceComponentLabel(DirectionalAnalogComponent component){
        return Component.translatable("createthrusters.directional_analog." + (component == null ? DirectionalAnalogComponent.FORWARD : component).id());
    }

    private static Component directionLabel(Direction dir){
        return Component.translatable("createthrusters.gyroscope_link.face." + dir.getSerializedName());
    }

    private void renderHints(GuiGraphics graphics, int mouseX, int mouseY){
        if(numberEditor != null || sourceComponentDropdownOpen || activeRangeField != null) return;
        if(inside(mouseX, mouseY, leftPos + 8, topPos + 23, 116, 11)){
            graphics.renderTooltip(font, List.of(
                    Component.translatable("createthrusters.gyroscope_link.config.frequency_hint.line1"),
                    Component.translatable("createthrusters.gyroscope_link.config.frequency_hint.line2")),
                    java.util.Optional.empty(), mouseX, mouseY);
        }else if(inside(mouseX, mouseY, leftPos + 138, topPos + 23, 120, 11)){
            var blockEntity = menu.getMenuConfigTargetBlockEntity();
            graphics.renderTooltip(font, List.of(
                    Component.translatable(blockEntity != null && blockEntity.isLinked()
                            ? "createthrusters.gyroscope_link.message.linked" : "createthrusters.gyroscope_link.message.not_linked"),
                    Component.translatable("createthrusters.gyroscope_link.config.mode_hint.line1"),
                    Component.translatable("createthrusters.gyroscope_link.config.mode_hint.line2")),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    // Draw only labels and hover feedback over baked buttons
    private void paintedButton(GuiGraphics graphics, Component label, int x, int y, int width, int height,
                               boolean selected, int mouseX, int mouseY){
        if(inside(mouseX, mouseY, leftPos + x, topPos + y, width, height)){
            graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + width - 1, topPos + y + height - 1, 0x22FFFFFF);
        }
        text(graphics, label, x + 2, y + 1, width - 4, height - 2, 0.8F, selected ? TEXT_COLOR : 0xFF303030, true);
    }

    private void text(GuiGraphics graphics, Component label, int x, int y, int width, int height, float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(graphics, font, label, leftPos + x, topPos + y, width, height, scale, color, centered);
    }

    // Retain the packet fields and cardinal frequency ordering used by existing saves
    private void sendConfigUpdate(){
        if(menu.getContentPos() == null) return;
        var config = getSelectedConfig();
        PacketDistributor.sendToServer(new GyroscopeLinkConfigPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()), trackingMode.name().toLowerCase(Locale.ROOT),
                copySingle(menu.ghostInventory.getStackInSlot(0)), copySingle(menu.ghostInventory.getStackInSlot(1)),
                copySingle(menu.ghostInventory.getStackInSlot(2)), copySingle(menu.ghostInventory.getStackInSlot(3)),
                copySingle(menu.ghostInventory.getStackInSlot(4)), copySingle(menu.ghostInventory.getStackInSlot(5)),
                copySingle(menu.ghostInventory.getStackInSlot(6)), copySingle(menu.ghostInventory.getStackInSlot(7)),
                selectedDirection.getSerializedName(), config.sourceComponent.id(), config.enabled, config.redstoneEnabled,
                config.sourceMinDegrees, config.sourceMaxDegrees, config.outputMin, config.outputMax, config.clampMin, config.clampMax));
    }

    private static String formatInputValue(double val){ return String.format(Locale.ROOT, "%.1f", val); }

    private static ItemStack copySingle(ItemStack stack){
        if(stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height){
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum RangeField{
        SOURCE(89, 360, "createthrusters.gyroscope_link.config.source_range"),
        OUTPUT(114, 180, "createthrusters.gyroscope_link.config.output_range"),
        CLAMP(138, 180, "createthrusters.gyroscope_link.config.clamp_range");

        private final int y;
        private final double limit;
        private final String translationKey;

        RangeField(int y, double limit, String translationKey){
            this.y = y;
            this.limit = limit;
            this.translationKey = translationKey;
        }
    }
}
