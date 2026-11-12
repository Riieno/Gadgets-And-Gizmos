package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetPaint;
import com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedDisplayWidgets;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudControlRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector;
import com.rieno.gadgetsandgizmos.lib.client.ui.WidgetCanvasRenderer;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudDropdown;
import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.function.Function;

// Bind ACC element data and artwork to the shared GUI and world widget painter
public final class AdvancedHudWidgetRenderer{
    private static final ResourceLocation TEXT_INPUT = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/widgets/text_input_widget.png");
    private static final GuiNineSlice TEXT_INPUT_FRAME = new GuiNineSlice(
            part(0, 0, 18, 5), part(20, 0, 17, 5), part(39, 0, 20, 5),
            part(0, 6, 18, 8), part(20, 6, 17, 8), part(39, 6, 20, 8),
            part(0, 15, 18, 4), part(20, 15, 17, 4), part(39, 15, 20, 4));

    private AdvancedHudWidgetRenderer(){}

    // Bind inspector media to the same assets and widget styles as the live HUD
    public static GraphPortInspector.ImagePreview inspectorPreview(Font font){
        return new GraphPortInspector.ImagePreview(){
            @Override public boolean draw(GuiGraphics graphics, GraphValue val, int x, int y, int width, int height){
                return AdvancedHudImageClient.draw(graphics, GraphRuntime.fromLibraryValue(val).payload(), x, y, width, height);
            }

            @Override public boolean drawWidget(GuiGraphics graphics, GraphValue val, int x, int y, int width, int height){
                var data = GraphWidgetValue.payload(val);
                var values = data.getCompound(GraphWidgetValue.VALUES);
                Function<String, AdvancedGraphDocument.Value> ports = key -> values.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)
                        ? AdvancedGraphDocument.Value.fromTag(values.getCompound(key)) : null;
                var appearance = data.contains(GraphWidgetValue.APPEARANCE, net.minecraft.nbt.Tag.TAG_COMPOUND)
                        ? data.getCompound(GraphWidgetValue.APPEARANCE)
                        : AdvancedDisplayWidgets.canvasAppearance(data, data.getBoolean("Constrain"), null);
                return WidgetCanvasRenderer.preview(graphics, val, x, y, width, height, canvasFrame(appearance),
                        (ctx, w, h) -> drawCanvasBackground(ctx, appearance, w, h),
                        (elm, w, h) -> drawElement(graphics, font, elm, w, h, ports,
                                element -> ViewReference.fromTag(element.getCompound("ViewReference"))));
            }
        };
    }

    // Include the editor's bezel and taskbar in the same fit as its content canvas
    public static WidgetCanvasRenderer.Frame canvasFrame(CompoundTag appearance){
        if(!appearance.getBoolean("Display")) return WidgetCanvasRenderer.Frame.NONE;
        int border = AccDisplayBlockEntity.BORDER_PIXELS;
        return new WidgetCanvasRenderer.Frame(border, border, border, border + Math.max(0, appearance.getInt("TaskbarHeight")));
    }

    // Paint the editor backdrop in canvas pixels before either renderer applies its viewport scale
    public static void drawCanvasBackground(GuiGraphics graphics, CompoundTag appearance, int width, int height){
        var frame = canvasFrame(appearance);
        if(appearance.getBoolean("Display")){
            HudDesignerAppearance.renderDisplay(graphics, 0, 0, width + frame.left() + frame.right(),
                    height + frame.top() + frame.bottom(), appearance.getBoolean("HalfHeight"),
                    Math.max(1, appearance.getInt("Columns")), Math.max(1, appearance.getInt("Rows")));
            int taskbar = Math.max(0, appearance.getInt("TaskbarHeight"));
            if(taskbar > 0) graphics.fill(frame.left(), frame.top() + height,
                    frame.left() + width, frame.top() + height + taskbar, 0xFF171717);
        }else{
            graphics.fill(0, 0, width, height, 0xFF303030);
            graphics.renderOutline(0, 0, width, height, 0xFF707070);
        }
    }

    // Keep images, camera overlays and controls identical in the designer and Widget inspector
    public static void drawElement(GuiGraphics graphics, Font font, CompoundTag elm, int width, int height,
                                   Function<String, AdvancedGraphDocument.Value> values,
                                   Function<CompoundTag, ViewReference> cameras){
        switch(elm.getString("Type")){
            case "camera_source" -> {
                var ref = cameras.apply(elm);
                ViewFeedRenderer.draw(graphics, font, ref, 0, 0, width, height);
                CameraWidgetClient.preview(graphics, font, ref, 0, 0, width, height);
            }
            case "image" -> {
                var canvas = HudWidgetRenderer.gui(graphics, font);
                var texture = AdvancedHudImageClient.resolveTexture(imageSource(elm, values));
                if(texture == null) HudWidgetRenderer.placeholder(canvas, font, "IMG", width, height);
                else canvas.draw(new GuiTextureRegion(texture, 1, 1, 0, 0, 1, 1), 0, 0, width, height);
            }
            default -> draw(HudWidgetRenderer.gui(graphics, font), font, elm, width, height, values, false);
        }
    }

    // Use the same saved and live values in the designer and projected display
    public static void draw(HudWidgetRenderer.Canvas canvas, Font font, CompoundTag elm, int width, int height,
                            Function<String, AdvancedGraphDocument.Value> values, boolean pulse){
        String type = elm.getString("Type");
        var value = value(elm, values);
        String text = elm.getString("Text");
        if("value".equals(type)){
            text = switch(value.type()){
                case "string", "direction" -> value.asString();
                case "boolean" -> Boolean.toString(value.asBoolean());
                default -> String.format(Locale.ROOT, "%.3f", value.asNumber());
            };
        }else if("text_input".equals(type) && "string".equals(value.type()) && !value.asString().isEmpty()) text = value.asString();
        if(AdvancedHudDropdown.TYPE.equals(type)){
            var options = AdvancedHudDropdown.options(elm, values);
            String key = AdvancedHudDropdown.selectedKey(elm, options, value);
            var selected = DropdownOptions.find(options, key);
            text = selected == null ? (text.isBlank() ? "Select an option" : text) : selected.label();
            String label = text;
            HudWidgetPaint.draw(canvas, font, width, style(elm), GraphColorValue.gradients(elm), false,
                    (target, style) -> HudWidgetRenderer.dropdown(target, font, style, width, height, label,
                            HudStyleCatalog.dropdownStyle(AdvancedHudInteractiveStyles.style(elm)), false, pulse));
            return;
        }
        String label = text;
        HudWidgetPaint.draw(canvas, font, width, style(elm), GraphColorValue.gradients(elm), "box".equals(type),
                (target, style) -> HudWidgetRenderer.draw(target, font, type, style, width, height, label,
                        HudStyleCatalog.amount(value.asNumber(), elm.getDouble("Min"), elm.getDouble("Max")),
                        "toggle".equals(type) ? value.asBoolean() : pulse, pulse));
    }

    // Respect per-image bindings and ports instead of sharing one texture across every image
    public static String imageSource(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values){
        String source = elm.getString("Texture");
        String port = elm.getString("Port");
        String bound = AdvancedHudElementBinding.port(elm, "Texture");
        if(!bound.isBlank()) port = bound;
        if(!port.isBlank() && values != null){
            var value = values.apply(port);
            String image = AdvancedHudImageClient.source(value);
            if(!image.isBlank()) source = image;
        }
        return source;
    }

    private static AdvancedGraphDocument.Value value(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values){
        String port = elm.getString("ValuePort");
        if(port.isBlank()) port = elm.getString("Port");
        if(AdvancedHudElementBinding.port(elm, "Value").isBlank() && !port.isBlank() && values != null){
            var value = values.apply(port);
            if(value != null) return value;
        }
        if(AdvancedHudDropdown.TYPE.equals(elm.getString("Type"))) return new AdvancedGraphDocument.Value("any", new CompoundTag());
        return "toggle".equals(elm.getString("Type"))
                ? AdvancedGraphDocument.Value.bool(elm.getBoolean("Value"))
                : "text_input".equals(elm.getString("Type")) ? AdvancedGraphDocument.Value.string("")
                : AdvancedGraphDocument.Value.number(elm.getDouble("Value"));
    }

    // Keep addon defaults and style identifiers outside the reusable drawing API
    public static HudWidgetRenderer.Style style(CompoundTag elm){
        var palette = AdvancedHudInteractiveStyleRenderer.palette(elm, 1);
        if("box".equals(elm.getString("Type"))) palette = new HudControlRenderer.Palette(
                AdvancedHudElementStyle.color(elm, "Color", AdvancedHudElementStyle.DEFAULT_BOX_BACKGROUND_COLOR),
                palette.accent(), palette.gradient(), palette.track(),
                AdvancedHudElementStyle.color(elm, "BorderColor", AdvancedHudElementStyle.DEFAULT_BOX_BORDER_COLOR),
                AdvancedHudElementStyle.borderWidth(elm), AdvancedHudElementStyle.borderRadius(elm), 1);
        String style = AdvancedHudInteractiveStyles.style(elm);
        return new HudWidgetRenderer.Style(palette, AdvancedHudElementStyle.fontSize(elm),
                AdvancedHudElementStyle.fontStyle(elm),
                AdvancedHudElementStyle.color(elm, "Color", AdvancedHudElementStyle.DEFAULT_TEXT_COLOR),
                HudStyleCatalog.controlStyle(style), HudStyleCatalog.progressStyle(style),
                HudStyleCatalog.checkbox(elm.getString("ToggleShape")), TEXT_INPUT_FRAME);
    }

    private static GuiTextureRegion part(int u, int v, int width, int height){
        return new GuiTextureRegion(TEXT_INPUT, 59, 19, u, v, width, height);
    }
}
