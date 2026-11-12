package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageSource;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.graph.GradientGraphNode;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Exercise saved visual nodes through the production graph and HUD value envelopes
@GameTestHolder("createthrusters_visual_graph")
@PrefixGameTestTemplate(false)
public final class VisualGraphGameTests{
    // Retain unsigned numeric colors on the existing output while exporting their packed channels
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void colorComponentsPreserveUnsignedColorInputs(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var src = node("source", "constant_number"); src.data().putDouble("Value", 4294967295D);
        var color = node("color", "constant_color"); graph.nodes().addAll(List.of(src, color));
        graph.edges().add(new AdvancedGraphDocument.Edge("color", "source", "value", "color", "value"));
        var runtime = new GraphRuntime(null, true);
        try{
            helper.assertTrue(runtime.previewOutput(graph, color, "value").asNumber() == 4294967295D,
                    "Component outputs changed the existing unsigned color value");
            helper.assertTrue("#FFFFFF".equals(runtime.previewOutput(graph, color, "hex").asString())
                    && runtime.previewOutput(graph, color, "alpha").asNumber() == 255,
                    "An unsigned color did not export its packed channels");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Export saved and connected color components through the production graph runtime
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void colorComponentsFollowSavedAndConnectedValues(GameTestHelper helper){
        for(boolean wired : List.of(false, true)){
            var graph = new AdvancedGraphDocument();
            var color = node("color", "constant_color"); color.data().putInt("Value", 0x8012AB03);
            graph.nodes().add(color);
            if(wired){
                var src = node("source", "constant_color"); src.data().putInt("Value", 0x00010203);
                graph.nodes().add(src);
                graph.edges().add(new AdvancedGraphDocument.Edge("color", "source", "value", "color", "value"));
            }
            graph = AdvancedGraphDocument.fromTag(graph.toTag()); color = graph.nodes().getFirst();
            var runtime = new GraphRuntime(null, true);
            try{
                helper.assertTrue((int)runtime.previewOutput(graph, color, "value").asNumber() == (wired ? 0x00010203 : 0x8012AB03),
                        "The existing color output changed");
                helper.assertTrue((wired ? "#010203" : "#12AB03").equals(runtime.previewOutput(graph, color, "hex").asString()),
                        "The HEX output did not match the effective color");
                var rgb = GraphRuntime.toLibraryValue(runtime.previewOutput(graph, color, "rgb"));
                helper.assertTrue(rgb.entries().stream().map(val -> (int)val.asNumber()).toList()
                        .equals(wired ? List.of(1, 2, 3) : List.of(18, 171, 3)), "The RGB list lost its channel order");
                helper.assertTrue(runtime.previewOutput(graph, color, "alpha").asNumber() == (wired ? 0 : 128),
                        "The Alpha output lost opacity");
            }finally{ runtime.clear(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gradientBindingsSurviveEveryWidgetOutputAndReturnToSolid(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var widget = node(type, type);
            var inputs = new CompoundTag(); inputs.putString("paint", "number"); widget.data().put("DynamicInputs", inputs);
            var elements = new net.minecraft.nbt.ListTag(); var elm = new CompoundTag(); elm.putString("Type", "box");
            elm.putInt("W", 40); elm.putInt("H", 20); elm.putInt("Color", 0xFFFFFFFF);
            AdvancedHudElementBinding.bind(elm, "Color", "paint");
            AdvancedHudElementBinding.bind(elm, "BorderColor", "paint");
            elements.add(elm); widget.data().put("WidgetElements", elements);
            helper.assertTrue(AdvancedGraphCatalog.compatible("map", widget, "paint"), "A color-bound port rejected a gradient");
            var gradient = node("gradient", "createthrusters:gradient");
            gradient.data().put(GradientGraphNode.PRESET, ColorEffectPreset.fromColors(List.of(0xFF0000, 0x00FF00, 0x0000FF), false).write());
            var graph = new AdvancedGraphDocument(); graph.nodes().addAll(List.of(gradient, widget));
            graph.edges().add(new AdvancedGraphDocument.Edge("paint", "gradient", "gradient", type, "paint"));
            graph = AdvancedGraphDocument.fromTag(graph.toTag()); widget = graph.nodes().get(1);
            var runtime = new GraphRuntime(null, true);
            var input = runtime.previewInput(graph, widget, "paint");
            helper.assertTrue("map".equals(input.type()) && input.entryOrder().size() == 100, "The color port converted gradient samples to a scalar");
            var output = runtime.previewOutput(graph, widget, "widget");
            var bridged = GraphRuntime.fromLibraryValue(GraphRuntime.toLibraryValue(output));
            helper.assertTrue("widget".equals(bridged.type()), type + " Widget output lost its type: " + bridged.type());
            var emitted = bridged.payload().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue.gradient(emitted, "Color").orElseThrow().colors().size() == 100,
                    "The Widget output discarded its resolved gradient");
            var resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> input);
            var ramp = com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue.gradient(resolved, "Color").orElseThrow();
            helper.assertTrue(ramp.colors().size() == 100 && ramp.sample(0) == 0xFFFF0000 && ramp.sample(1) == 0xFF0000FF,
                    "Resolved element lost gradient colors");
            var solid = AdvancedHudElementBinding.resolvedCopy(resolved, port -> AdvancedGraphDocument.Value.number(0xFF00FF00));
            helper.assertTrue(!solid.contains(com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue.GRADIENTS)
                    && solid.getInt("Color") == 0xFF00FF00, "A solid value retained stale gradient paint");
            runtime.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gamepadNodesReadIndependentDevicesAndReleaseDisconnectedInputs(GameTestHelper helper){
        var ctrl = new AdvancedContraptionControllerBlockEntity(net.minecraft.core.BlockPos.ZERO,
                com.rieno.gadgetsandgizmos.registry.CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        ctrl.setLevel(helper.getLevel());
        var graph = new AdvancedGraphDocument();
        for(String device : List.of("wheel/0", "stick/1")){
            var input = node(device, "gamepad_input");
            input.data().putString("BindingId", "hardware:axis_0"); input.data().putString("DeviceSelector", device);
            graph.nodes().add(input);
        }
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        ctrl.applyHardwareControllerInput(java.util.Map.of("hardware:wheel/0/axis_0", .25, "hardware:stick/1/axis_0", -.75));
        var runtime = new GraphRuntime(ctrl, true);
        helper.assertTrue(runtime.previewOutput(graph, graph.nodes().getFirst(), "value").asNumber() == .25
                && runtime.previewOutput(graph, graph.nodes().getLast(), "value").asNumber() == -.75, "Device selectors shared one input source");
        ctrl.applyHardwareControllerInput(java.util.Map.of("hardware:stick/1/axis_0", -.5));
        runtime.clear(); runtime = new GraphRuntime(ctrl, true);
        helper.assertTrue(runtime.previewOutput(graph, graph.nodes().getFirst(), "value").asNumber() == 0
                && runtime.previewOutput(graph, graph.nodes().getLast(), "value").asNumber() == -.5, "A disconnected device retained input or switched to another controller");
        runtime.clear(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void whitespaceSurvivesStringNodesAndSaveReload(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var space = node("space", "constant_string"); space.data().putString("Value", " ");
        var split = node("split", "split_string");
        var defaults = new CompoundTag(); defaults.put("delimiter", AdvancedGraphDocument.Value.string(" ").toTag());
        defaults.put("string", AdvancedGraphDocument.Value.string("a b").toTag()); split.data().put("Defaults", defaults);
        graph.nodes().addAll(List.of(space, split));
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        var runtime = new GraphRuntime(null, true);
        helper.assertTrue(" ".equals(runtime.previewOutput(graph, graph.nodes().getFirst(), "value").asString()), "A space constant was lost");
        helper.assertTrue(" ".equals(runtime.previewInput(graph, graph.nodes().get(1), "delimiter").asString()), "A space delimiter was lost");
        helper.assertTrue(" ".equals(com.rieno.gadgetsandgizmos.lib.graph.GraphInputDefaults.text(graph.nodes().get(1).data(), "delimiter", "fallback")),
                "The editor replaced whitespace with an unset fallback");
        runtime.clear(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void embeddedImageOutputsSurviveSaveAndValueBridges(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var image = node("image", "image_reference");
        AdvancedGraphImageAssets.embedBase64(image.data(), "image/png", "saved-image-data".repeat(4000));
        graph.nodes().add(image);
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        image = graph.nodes().getFirst();
        var runtime = new GraphRuntime(null, true);
        var output = runtime.previewOutput(graph, image, "image");
        helper.assertTrue("image".equals(AdvancedGraphCatalog.outputs(image).get("image")) && "image".equals(output.type()), "Image output lost its type");
        var bridged = GraphRuntime.fromLibraryValue(GraphRuntime.toLibraryValue(output));
        helper.assertTrue(output.payload().equals(bridged.payload()), "The reusable graph bridge discarded embedded image data");
        helper.assertTrue(AdvancedGraphImageAssets.base64(bridged.payload()).equals("saved-image-data".repeat(4000)), "Chunked image bytes were truncated");
        var live = AdvancedGraphLiveValue.from(bridged);
        helper.assertTrue(AdvancedHudImageSource.fromPort(live).equals(output.asString())
                && live.value().payload().equals(output.payload()), "HUD synchronization discarded the source or asset");
        runtime.clear(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gradientOutputsUseLivePercentagesAndOneHundredColors(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var value = node("value", "constant_number"); value.data().putDouble("Value", 50);
        var gradient = node("gradient", "createthrusters:gradient");
        gradient.data().put(GradientGraphNode.PRESET, ColorEffectPreset.fromColors(List.of(0xFF0000, 0x0000FF), false).write());
        graph.nodes().addAll(List.of(value, gradient));
        graph.edges().add(new AdvancedGraphDocument.Edge("value", "value", "value", "gradient", "value"));
        graph = AdvancedGraphDocument.fromTag(graph.toTag()); gradient = graph.nodes().get(1);
        var runtime = new GraphRuntime(null, true);
        helper.assertTrue((int)runtime.previewOutput(graph, gradient, "color").asNumber() == 0xFF800080, "The sampled color did not use the connected percentage");
        var output = runtime.previewOutput(graph, gradient, "gradient");
        helper.assertTrue("map".equals(output.type()) && output.payload().size() == 100 && output.entryOrder().size() == 100, "Gradient output lost its hundred ordered samples");
        var colors = GraphRuntime.toLibraryValue(output);
        helper.assertTrue((int)colors.member("0").asNumber() == 0xFFFF0000 && (int)colors.member("99").asNumber() == 0xFF0000FF, "Gradient output lost an endpoint");
        runtime.clear(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hudParametersCanShareOnePort(GameTestHelper helper){
        var elm = new CompoundTag(); elm.putString("Type", "box"); elm.putInt("X", 1); elm.putInt("Y", 2);
        AdvancedHudElementBinding.bind(elm, "X", "position");
        AdvancedHudElementBinding.bind(elm, "Y", "position");
        var resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> AdvancedGraphDocument.Value.number(42));
        helper.assertTrue(resolved.getInt("X") == 42 && resolved.getInt("Y") == 42, "Reusing a data port cleared another parameter");
        AdvancedHudElementBinding.bind(elm, "X", "");
        helper.assertTrue("position".equals(AdvancedHudElementBinding.port(elm, "Y")), "Restoring one default cleared another parameter");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void imageInputsAcceptLegacySourcesAndRetainTypedAssets(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var widget = node(type, type);
            var inputs = new CompoundTag(); inputs.putString("picture", "string"); widget.data().put("DynamicInputs", inputs);
            var elements = new net.minecraft.nbt.ListTag(); var element = new CompoundTag(); element.putString("Type", "image");
            AdvancedHudElementBinding.bind(element, "Texture", "picture"); elements.add(element); widget.data().put("WidgetElements", elements);
            helper.assertTrue("image".equals(AdvancedGraphCatalog.inputs(widget).get("picture"))
                    && AdvancedGraphCatalog.compatible("image", widget, "picture")
                    && AdvancedGraphCatalog.compatible("string", widget, "picture"), "An image input rejected a typed asset or legacy source");
            var graph = new AdvancedGraphDocument(); var source = node("source", "image_reference");
            AdvancedGraphImageAssets.embedBase64(source.data(), "image/png", "encoded-image");
            graph.nodes().addAll(List.of(source, widget));
            graph.edges().add(new AdvancedGraphDocument.Edge("picture", "source", "image", widget.id(), "picture"));
            var runtime = new GraphRuntime(null, true);
            var input = runtime.previewInput(graph, widget, "picture");
            helper.assertTrue("image".equals(input.type()) && AdvancedGraphImageAssets.base64(input.payload()).equals("encoded-image"),
                    "A legacy image wire discarded its embedded asset");
            runtime.clear();
        }
        var image = new CompoundTag(); image.putString("Source", "https://example.test/image.png");
        var elm = new CompoundTag(); elm.putString("Texture", "old");
        AdvancedHudElementBinding.bind(elm, "Texture", "picture");
        var resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> AdvancedGraphImageAssets.imageValue(image));
        helper.assertTrue(resolved.getString("Texture").equals(image.getString("Source")), "Image parameters could not read an image value");
        helper.succeed();
    }

    // Keep generated widgets typed and self-contained across wires, saves and runtime value bridges
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void widgetOutputsResolveLiveBindingsAndRetainAssets(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget")){
            var graph = new AdvancedGraphDocument();
            var widget = node("widget", type); widget.data().putInt("WidgetWidth", 200); widget.data().putInt("WidgetHeight", 100);
            var inputs = widget.data().getCompound("DynamicInputs"); inputs.putString("caption", "string"); inputs.putString("picture", "image");
            var elements = new net.minecraft.nbt.ListTag(); var text = new CompoundTag();
            text.putString("Type", "text"); text.putString("Text", "Saved"); text.putInt("W", 180); text.putInt("H", 20);
            AdvancedHudElementBinding.bind(text, "Text", "caption"); elements.add(text);
            var image = new CompoundTag(); image.putString("Type", "image"); image.putString("Texture", "");
            image.putInt("Y", 24); image.putInt("W", 40); image.putInt("H", 40);
            AdvancedHudElementBinding.bind(image, "Texture", "picture"); elements.add(image); widget.data().put("WidgetElements", elements);
            var caption = node("caption", "constant_string"); caption.data().putString("Value", "Live caption");
            var picture = node("picture", "image_reference"); AdvancedGraphImageAssets.embedBase64(picture.data(), "image/png", "image-bytes".repeat(4000));
            var pass = node("pass", "reroute"); graph.nodes().addAll(List.of(widget, caption, picture, pass));
            graph.edges().add(new AdvancedGraphDocument.Edge("caption", "caption", "value", "widget", "caption"));
            graph.edges().add(new AdvancedGraphDocument.Edge("image", "picture", "image", "widget", "picture"));
            graph.edges().add(new AdvancedGraphDocument.Edge("widget", "widget", "widget", "pass", "value"));
            graph = AdvancedGraphDocument.fromTag(graph.toTag()); widget = graph.nodes().getFirst(); pass = graph.nodes().getLast();
            helper.assertTrue("widget".equals(AdvancedGraphCatalog.outputs(widget).get("widget")), "Missing Widget output");
            var runtime = new GraphRuntime(null, true);
            var value = runtime.previewOutput(graph, pass, "value");
            helper.assertTrue("widget".equals(value.type()), "A wire discarded the Widget data type");
            var rendered = value.payload().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND);
            helper.assertTrue("Live caption".equals(rendered.getCompound(0).getString("Text")), "Widget output used saved text instead of its live binding");
            helper.assertTrue("Saved".equals(widget.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("Text")),
                    "Generating a widget changed the designer's static default");
            var asset = AdvancedGraphDocument.Value.fromTag(value.payload().getCompound(
                    com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.VALUES).getCompound("picture"));
            helper.assertTrue(AdvancedGraphImageAssets.base64(asset.payload()).equals("image-bytes".repeat(4000)), "Widget snapshot discarded embedded image bytes");
            helper.assertTrue(value.equals(GraphRuntime.fromLibraryValue(GraphRuntime.toLibraryValue(value)))
                    && value.equals(AdvancedGraphLiveValue.from(value).value())
                    && value.equals(AdvancedGraphDocument.Value.fromTag(value.toTag())), "Widget transport changed the canvas or its native tag types");
            var inspection = new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspection("Widget", "any", true, GraphRuntime.toLibraryValue(value), null);
            helper.assertTrue(inspection.preview() == com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspection.Preview.WIDGET,
                    "Generic ports did not retain Widget preview support");
            runtime.clear();
        }
        helper.succeed();
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
}
