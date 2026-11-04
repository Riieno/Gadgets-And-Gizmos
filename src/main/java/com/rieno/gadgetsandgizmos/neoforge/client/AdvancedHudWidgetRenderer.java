package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudControlRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.gui.Font;
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
        }else if("text_input".equals(type) && "string".equals(value.type()) && !value.asString().isBlank()) text = value.asString();
        HudWidgetRenderer.draw(canvas, font, type, style(elm), width, height, text,
                HudStyleCatalog.amount(value.asNumber(), elm.getDouble("Min"), elm.getDouble("Max")),
                "toggle".equals(type) ? value.asBoolean() : pulse, pulse);
    }

    // Respect per-image bindings and ports instead of sharing one texture across every image
    public static String imageSource(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values){
        String source = elm.getString("Texture");
        String port = elm.getString("Port");
        if(AdvancedHudElementBinding.port(elm, "Texture").isBlank() && !port.isBlank() && values != null){
            var value = values.apply(port);
            if(value != null && "string".equals(value.type()) && !value.asString().isBlank()) source = value.asString();
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
