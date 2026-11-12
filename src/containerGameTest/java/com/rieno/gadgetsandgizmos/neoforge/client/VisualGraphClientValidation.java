package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ThrusterDyeMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorGradientEditor;
import com.rieno.gadgetsandgizmos.lib.client.ui.ImageSourceModal;
import com.rieno.gadgetsandgizmos.lib.color.ColorStopEditor;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport;
import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudDropdownScreen;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudControlRenderer;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

// Exercise real editor controls and capture the rendered graph and modal surfaces
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class VisualGraphClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.visualGraphValidation");
    private static boolean complete;
    private static AdvancedGraphDocument.Value pendingImage;
    private static int imageTicks;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null) return;
        if(complete){
            if(pendingImage != null) finishImageValidation();
            return;
        }
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        complete = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path out = Path.of(OUTPUT);
        try{
            Files.createDirectories(out);
            validate(mc, out);
        }catch(Throwable err){
            var trace = new StringWriter(); err.printStackTrace(new PrintWriter(trace));
            try{ Files.writeString(out.resolve("result.txt"), "FAILED\n" + trace); }catch(Exception ignored){}
        }finally{ if(pendingImage == null) mc.stop(); }
    }

    private static void validate(Minecraft mc, Path out) throws Exception{
        var linked = new java.util.ArrayList<com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedTarget>();
        var ctrl = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
            @Override public List<com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedTarget> getLinkedTargets(){ return List.copyOf(linked); }
        };
        ctrl.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1234, mc.player.getInventory(), ctrl);
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Visual Nodes"));
        screen.configureForProjection(); screen.init(mc, 800, 500);
        validateColorAlpha(screen, out);
        validateWidgetProperties(mc, ctrl, linked, screen, out);
        validateControllerTextAndPaints(mc, screen, out);
        validateDimensionPressure(mc, screen, out);
        validateAngleConversion(screen, out);
        validateDropdowns(mc, screen, out);
        validateWires(mc, screen, out);
        validateStraightTurns(screen, out);
        validateWireHover(screen, out);
        var graph = new AdvancedGraphDocument();
        var image = node("image", "image_reference", 50, 30);
        image.data().putString("Source", "minecraft:textures/block/stone.png");
        var gradient = node("gradient", "createthrusters:gradient", 290, 30);
        var widget = node("widget", "acc_display_widget", 340, 260);
        var inputs = new CompoundTag(); inputs.putString("shared", "number"); inputs.putString("picture", "image");
        widget.data().put("DynamicInputs", inputs);
        var elements = new ListTag(); var elm = new CompoundTag();
        elm.putString("Type", "image"); elm.putInt("X", 20); elm.putInt("Y", 20); elm.putInt("W", 80); elm.putInt("H", 60);
        elm.putString("Texture", "minecraft:textures/block/stone.png"); elements.add(elm); widget.data().put("WidgetElements", elements);
        graph.nodes().addAll(List.of(image, gradient, widget));
        screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        image = draft.nodes().get(0); gradient = draft.nodes().get(1); widget = draft.nodes().get(2);
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        capture(out.resolve("nodes.png"), graphics -> screen.render(graphics, -1, -1, 0));
        var editor = (ColorGradientEditor)invoke(screen, "gradientEditor", gradient);
        int before = editor.editor().preset().stops().size();
        check(editor.mouseClicked(66, (int)invoke(screen, "nodeBodyTopUnits", gradient) + 20, 0, true, false, () -> {}), "The gradient preview did not accept Ctrl+click");
        check(editor.editor().preset().stops().size() == before + 1, "Ctrl+click did not insert a gradient stop");
        float position = editor.editor().preset().stops().get(editor.editor().selected()).position();
        editor.mouseClicked(12 + position * 215, (int)invoke(screen, "nodeBodyTopUnits", gradient) + 8 + 36 + 10, 0, false, true, () -> {});
        check(editor.editor().preset().stops().size() == before, "Shift+click did not remove the selected stop");
        check(ImageSourceModal.validUrl("https://example.test/image.png") && !ImageSourceModal.validUrl("file:///image.png"), "Image URL validation failed");
        var range = (double[])invoke(screen, "numberRange", gradient, "value");
        check(range[0] == 0 && range[1] == 100, "The gradient input slider does not cover 0–100");
        var upload = invoke(screen, "imageReferenceUploadButtonBounds", image);
        screen.mouseClicked((int)invoke(upload, "x") + (int)invoke(upload, "width") / 2D,
                (int)invoke(upload, "y") + (int)invoke(upload, "height") / 2D, 0);
        check(field(screen, "imageSourceModal") != null, "The Image node's Upload button did not open its modal");
        capture(out.resolve("image-modal.png"), graphics -> screen.render(graphics, -1, -1, 0));
        var modal = (ImageSourceModal)field(screen, "imageSourceModal");
        var url = (net.minecraft.client.gui.components.EditBox)field(modal, "url"); url.setValue("https://example.test/image.png");
        modal.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(image.data().getString("Source").equals(url.getValue()) && field(screen, "imageSourceModal") == null, "The Image modal did not save its URL");
        PortInspectorClientValidation.validate(mc, out);
        invoke(screen, "openHud", widget); set(screen, "hudSelectedIdx", 0); set(screen, "hudDataBindings", true);
        var selected = (CompoundTag)invoke(screen, "hudElement");
        var options = (List<?>)invoke(screen, "hudBindingOptions");
        check(options.contains("shared") && options.contains("picture") && !options.contains("X"), "Data dropdowns list parameters instead of ports");
        for(String property : List.of("X", "Y")){
            set(screen, "hudBindingDropdownKey", property);
            var bounds = invoke(screen, "hudBindingDropdownBounds", Math.min(8, options.size()));
            invoke(screen, "clickHudBindingDropdown", (int)invoke(bounds, "x") + 8D,
                    (int)invoke(bounds, "y") + 2D + options.indexOf("shared") * 18 + 8);
        }
        capture(out.resolve("hud-parameters.png"), graphics -> screen.render(graphics, -1, -1, 0));
        set(screen, "hudBindingDropdownKey", "X");
        capture(out.resolve("hud-port-dropdown.png"), graphics -> screen.render(graphics, -1, -1, 0));
        check(AdvancedHudElementBinding.port(selected, "X").equals(AdvancedHudElementBinding.port(selected, "Y")), "The designer lost a shared binding");
        var dyeMenu = new ThrusterDyeMenu(-1235, mc.player.getInventory(), 0);
        var dye = new ThrusterDyeScreen(dyeMenu, mc.player.getInventory(), Component.literal("Thruster Dye Applicator"));
        dye.init(mc, 800, 500);
        var dyeEditor = (ColorStopEditor)field(dye, "editor");
        dyeEditor.mode(com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset.Mode.GRADIENT);
        capture(out.resolve("applicator-gradient.png"), graphics -> dye.render(graphics, -1, -1, 0));
        var dyeGradient = (ColorGradientEditor)field(dye, "gradient");
        int left = (int)field(dyeGradient, "x"), top = (int)field(dyeGradient, "y");
        dyeGradient.mouseClicked(left + 69, top + 20, 0, true, false, () -> {});
        check(dyeEditor.preset().stops().size() == 3, "The applicator did not add a clicked stop");
        dyeGradient.mouseClicked(left + 69, top + 52 + 10, 0, false, true, () -> {});
        check(dyeEditor.preset().stops().size() == 2, "The applicator did not remove a Shift-clicked stop");
        Path fixture = out.resolve("embedded-fixture.png");
        try(var pixels = new NativeImage(2, 2, false)){
            pixels.setPixelRGBA(0, 0, 0xFF0000FF); pixels.setPixelRGBA(1, 0, 0xFF00FF00);
            pixels.setPixelRGBA(0, 1, 0xFFFF0000); pixels.setPixelRGBA(1, 1, 0xFFFFFFFF);
            pixels.writeToFile(fixture);
        }
        var data = new CompoundTag();
        AdvancedGraphImageAssets.embedBase64(data, "image/png", java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(fixture)));
        pendingImage = GraphRuntime.fromLibraryValue(GraphRuntime.toLibraryValue(AdvancedGraphImageAssets.imageValue(data)));
        check(java.util.Arrays.equals(Files.readAllBytes(fixture), java.util.Base64.getDecoder().decode(
                AdvancedGraphImageAssets.base64(pendingImage.payload()))), "The image wire changed its PNG bytes");
        AdvancedHudImageClient.source(pendingImage);
        check(AdvancedHudImageClient.resolveTexture(pendingImage.asString()) == null,
                "An unloaded graph image was exposed as a missing resource texture");
    }

    // Edit alpha through the ACC modal and verify live values, cancellation and saved opacity
    private static void validateColorAlpha(AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var graph = new AdvancedGraphDocument(); var color = node("color", "constant_color", 60, 40);
        color.data().putInt("Value", 0xFF123456); graph.nodes().add(color); screen.updateProjectionGraph(graph);
        color = ((AdvancedGraphDocument)field(screen, "draft")).nodes().getFirst();
        check("HEX".equals(invoke(screen, "outputDisplayLabel", color, "hex"))
                && "RGB".equals(invoke(screen, "outputDisplayLabel", color, "rgb")), "Color output abbreviations were expanded");
        invoke(screen, "openGraphColorPicker", color, null);
        capture(out.resolve("color-alpha-modal.png"), graphics -> screen.render(graphics, -1, -1, 0));
        var picker = (com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal)field(screen, "graphColorPicker");
        typeAlpha(picker, "128");
        check(color.data().getInt("Value") == 0x80123456, "Alpha editing changed RGB or failed to update the live value");
        check("#80123456".equals(field(picker, "hex")), "Alpha and HEX fields did not stay synchronized");
        capture(out.resolve("color-alpha-translucent.png"), graphics -> screen.render(graphics, -1, -1, 0));
        picker.cancel();
        check(color.data().getInt("Value") == 0xFF123456, "Cancelling alpha did not restore the saved color");
        invoke(screen, "openGraphColorPicker", color, null);
        capture(out.resolve("color-alpha-reopened.png"), graphics -> screen.render(graphics, -1, -1, 0));
        picker = (com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal)field(screen, "graphColorPicker");
        typeAlpha(picker, "999");
        check(color.data().getInt("Value") == 0xFF123456, "Alpha was not clamped to 255");
        typeAlpha(picker, "0"); picker.accept();
        check(color.data().getInt("Value") == 0x00123456, "Accepting alpha zero discarded transparent RGB channels");
        var saved = AdvancedGraphDocument.fromTag(((AdvancedGraphDocument)field(screen, "draft")).toTag()).nodes().getFirst();
        check(saved.data().getInt("Value") == 0x00123456, "Saved alpha did not survive graph serialization");
        capture(out.resolve("color-node-outputs.png"), graphics -> screen.render(graphics, -1, -1, 0));
    }

    // Type through the scaled alpha field used by the painted ACC picker
    private static void typeAlpha(com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal picker, String text) throws Exception{
        var region = CTColorPickerAppearance.DEFAULT.layout().alphaField();
        float scale = (float)field(picker, "pointerScale");
        picker.mouseClicked((int)field(picker, "pointerLeft") + (region.x() + 2) * scale,
                (int)field(picker, "pointerTop") + (region.y() + 2) * scale, 0);
        for(char ch : text.toCharArray()) picker.charTyped(ch, 0);
    }

    // Exercise camera picker staging and slider fields in all three widget editors
    private static void validateWidgetProperties(Minecraft mc, AdvancedContraptionControllerBlockEntity ctrl,
            List<com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedTarget> linked,
            AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        @SuppressWarnings("unchecked")
        var targets = (List<com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode>)field(screen, "liveGraphTargets");
        var prevTargets = List.copyOf(targets);
        BlockPos cameraPos = mc.player.blockPosition().offset(2, 1, 0);
        var prevState = mc.level.getBlockState(cameraPos);
        mc.level.setBlock(cameraPos, CTBlocks.CAMERA.get().defaultBlockState(), 3);
        var liveCamera = (com.rieno.gadgetsandgizmos.content.CameraBlockEntity)mc.level.getBlockEntity(cameraPos);
        check(liveCamera != null && liveCamera.isViewAvailable(), "The client test camera is unavailable");
        linked.add(new com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedTarget(
                cameraPos.relative(net.minecraft.core.Direction.EAST), null,
                "createthrusters:contraption_network_linker_plane", "Selected Camera",
                com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkMode.INPUT,
                com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.TargetScope.FACE,
                List.of(new com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedFace(net.minecraft.core.Direction.DOWN, "", ""),
                        new com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedFace(net.minecraft.core.Direction.EAST, "", ""))));
        var camera = com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.toDiscoveryNodes(linked).getFirst()
                .withBlockTarget("createthrusters:camera", "Selected Camera", cameraPos);
        var display = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("display",
                com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.MACHINE, "", "createthrusters:acc_display",
                "Output Display", null, BlockPos.ZERO);
        targets.clear(); targets.addAll(List.of(camera, display));
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var graph = new AdvancedGraphDocument(); var widget = node("widget", type, 20, 20);
            widget.data().put("TargetData", display.toTag());
            var elements = new ListTag(); var elm = new CompoundTag();
            elm.putString("Type", "camera_source"); elm.putString("InteractionId", "camera_element");
            elm.putInt("W", 112); elm.putInt("H", 63); elements.add(elm);
            widget.data().put("WidgetElements", elements); graph.nodes().add(widget);
            screen.updateProjectionGraph(graph);
            widget = ((AdvancedGraphDocument)field(screen, "draft")).nodes().getFirst();
            set(screen, "hudSelectedIdx", 0); set(screen, "hudDataBindings", false);
            invoke(screen, "openHud", widget);
            elm = (CompoundTag)invoke(screen, "hudElement");
            var btn = invoke(screen, "hudPortBtnBounds");
            invoke(screen, "clickHud", ((int)invoke(btn, "x") + 2D), ((int)invoke(btn, "y") + 2D), 0);
            check((boolean)field(screen, "graphTargetPickerOpen"), type + " camera selector did not open its picker");
            check(((List<?>)invoke(screen, "graphTargetPickerChoices")).contains(camera)
                    && !((List<?>)invoke(screen, "graphTargetPickerChoices")).contains(display), type + " picker included a display as a camera");
            invoke(screen, "toggleGraphTargetPickerSelection", camera);
            capture(out.resolve(type + "-camera-picker.png"), graphics -> screen.render(graphics, -1, -1, 0));
            invoke(screen, "closeGraphTargetPicker");
            check(!elm.contains("CameraSource"), type + " saved a cancelled camera selection");
            invoke(screen, "openHudCameraPicker", widget, elm);
            invoke(screen, "toggleGraphTargetPickerSelection", camera);
            invoke(screen, "applyGraphTargetPickerSelections");
            check(com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudCameraSource.pickerSelection(camera, linked)
                    .equals(elm.getCompound("CameraSource")), type + " did not save the picked camera and support face");
            check("east".equals(elm.getCompound("CameraSource").getString("Face")), type + " lost the selected camera's support face");
            var ref = com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudCameraSource.reference(ctrl, widget, elm, port -> null);
            check(com.rieno.gadgetsandgizmos.lib.view.ViewReference.of(liveCamera).equals(ref), type + " could not preview the picked camera");
            check(display.toTag().equals(widget.data().getCompound("TargetData")), type + " changed the widget's display target");
            capture(out.resolve(type + "-camera-properties.png"), graphics -> screen.render(graphics, -1, -1, 0));
            invoke(screen, "openHudCameraPicker", widget, elm);
            invoke(screen, "toggleGraphTargetPickerSelection", camera);
            invoke(screen, "applyGraphTargetPickerSelections");
            check(!elm.contains("CameraSource"), type + " did not clear the deselected camera");
            AdvancedHudElementBinding.bind(elm, "CameraSource", "");
            elm.putString("Type", "slider"); elm.putDouble("Min", 0); elm.putDouble("Max", 1); elm.putDouble("Step", 0.1);
            invoke(screen, "syncHudFields");
            var minimum = (net.minecraft.client.gui.components.EditBox)field(screen, "hudMinimum");
            var maximum = (net.minecraft.client.gui.components.EditBox)field(screen, "hudMaximum");
            var step = (net.minecraft.client.gui.components.EditBox)field(screen, "hudStep");
            check(minimum.visible && maximum.visible && step.visible, type + " slider range fields were hidden");
            minimum.setValue("-25"); maximum.setValue("75"); step.setValue("0.25");
            check(elm.getDouble("Min") == -25 && elm.getDouble("Max") == 75 && elm.getDouble("Step") == 0.25,
                    type + " slider fields did not update saved values");
            step.setValue("NaN");
            check(elm.getDouble("Step") == 0.25, type + " accepted a non-finite slider step");
            step.setValue("0.25");
            capture(out.resolve(type + "-slider-properties.png"), graphics -> screen.render(graphics, -1, -1, 0));
            var tab = invoke(screen, "hudInspectorTab", true);
            invoke(screen, "clickHud", ((int)invoke(tab, "x") + 2D), ((int)invoke(tab, "y") + 2D), 0);
            check(!minimum.visible && !maximum.visible && !step.visible, type + " kept slider fields on the Ports tab");
            check("Ports".equals(Component.translatable("createthrusters.text.ports").getString()), "The Ports label was not translated");
            capture(out.resolve(type + "-ports.png"), graphics -> screen.render(graphics, -1, -1, 0));
            validateTextureProperties(screen, elm, type, out);
            set(screen, "hudOpen", false);
        }
        targets.clear(); targets.addAll(prevTargets);
        linked.clear();
        mc.level.setBlock(cameraPos, prevState, 3);
        set(screen, "hudDataBindings", false);
        invoke(screen, "layoutHudWidgets");
    }

    // Pick an actual resource through the modal and edit the separate web URL field
    private static void validateTextureProperties(AdvancedContraptionControllerScreen screen, CompoundTag elm, String type, Path out) throws Exception{
        set(screen, "hudDataBindings", false); elm.putString("Type", "image");
        elm.putString("Texture", "minecraft:textures/block/stone.png");
        AdvancedHudElementBinding.bind(elm, "Texture", "picture");
        invoke(screen, "syncHudFields");
        var url = (net.minecraft.client.gui.components.EditBox)field(screen, "hudTexture");
        check(url.visible && url.getValue().isEmpty(), type + " showed a resource identifier in the URL field");
        var bounds = invoke(screen, "hudTextureButtonBounds");
        invoke(screen, "clickHud", (int)invoke(bounds, "x") + 2D, (int)invoke(bounds, "y") + 2D, 0);
        var modal = (com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionModal)field(screen, "texturePickerModal");
        check(modal != null && modal.isOpen(), type + " Select Texture did not open the modal");
        var grid = field(modal, "grid");
        var search = (net.minecraft.client.gui.components.EditBox)field(grid, "search");
        search.setValue("@minecraft textures/block/dirt.png");
        var filtered = (List<?>)field(grid, "filtered");
        check(filtered.size() == 1, type + " texture search did not filter by namespace and resource path");
        capture(out.resolve(type + "-texture-picker.png"), graphics -> screen.render(graphics, -1, -1, 0));
        var transform = invoke(modal, "transform", 800, 500);
        var region = CTTexturePickerAppearance.DEFAULT.grid().grid();
        double x = (int)invoke(transform, "x") + (region.x() + 16) * (float)invoke(transform, "scale");
        double y = (int)invoke(transform, "y") + (region.y() + 16) * (float)invoke(transform, "scale");
        screen.mouseClicked(x, y, 0);
        check(modal.selectedIds().equals(List.of("minecraft:textures/block/dirt.png")), type + " texture card selection failed");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(elm.getString("Texture").equals("minecraft:textures/block/stone.png"), type + " changed a cancelled texture");
        invoke(screen, "openHudTexturePicker");
        modal = (com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionModal)field(screen, "texturePickerModal");
        grid = field(modal, "grid"); search = (net.minecraft.client.gui.components.EditBox)field(grid, "search");
        search.setValue("@minecraft textures/block/dirt.png"); screen.mouseClicked(x, y, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(elm.getString("Texture").equals("minecraft:textures/block/dirt.png") && url.getValue().isEmpty(), type + " did not accept the selected texture");
        url.setValue("minecraft:textures/block/stone.png");
        check(elm.getString("Texture").equals("minecraft:textures/block/dirt.png"), type + " accepted a resource as a web URL");
        url.setValue("https://example.test/image.png");
        check(elm.getString("Texture").equals(url.getValue()), type + " did not save a valid web image URL");
        check(AdvancedHudElementBinding.port(elm, "Texture").equals("picture"), type + " lost its data override");
        capture(out.resolve(type + "-image-properties.png"), graphics -> screen.render(graphics, -1, -1, 0));
    }

    // Exercise routed picking, automatic reroute sockets and the persisted Tools toggle on the real screen
    @SuppressWarnings("unchecked")
    private static void validateControllerTextAndPaints(Minecraft mc, AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var graph = new AdvancedGraphDocument();
        var gamepad = node("device", "gamepad_input", 40, 30);
        var space = node("space", "constant_string", 330, 30); space.data().putString("Value", " ");
        var split = node("split", "split_string", 330, 160);
        graph.nodes().addAll(List.of(gamepad, space, split)); screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        gamepad = draft.nodes().get(0); space = draft.nodes().get(1); split = draft.nodes().get(2);
        check((boolean)invoke(screen, "hasTypeControl", gamepad), "Gamepad Input has no device selector");
        var opts = (List<String>)invoke(screen, "typeControlOptions", gamepad);
        check(opts.contains("automatic"), "The device selector lost legacy automatic mode");
        for(var device : com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers.connected())
            check(opts.contains(device.id()), "The selector omitted a connected controller");
        invoke(screen, "openPropertyDropdown", gamepad, "DeviceSelector", 20.0, 40.0, 250.0, List.of("automatic", "missing/0"));
        var dropdown = field(screen, "optionDropdown");
        int rowTop = (int)invoke(screen, "optionDropdownRowsTop", dropdown);
        int rowHeight = (int)field(screen, "OPTION_DROPDOWN_ITEM_HEIGHT");
        invoke(screen, "clickOptionDropdown", (double)(int)invoke(dropdown, "x") + 10, (double)rowTop + rowHeight + 2, 0);
        check("missing/0".equals(gamepad.data().getString("DeviceSelector")), "The device dropdown did not save the selected device");
        check("missing/0".equals(AdvancedGraphDocument.fromTag(draft.toTag()).nodes().getFirst().data().getString("DeviceSelector")),
                "The selected device did not survive graph save/load");
        Files.writeString(out.resolve("controller-devices.txt"), com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers.connected().toString());
        invoke(screen, "configureBinding", gamepad);
        var emptyKeys = field(screen, "optionDropdown");
        check(emptyKeys != null && ((List<?>)invoke(emptyKeys, "options")).isEmpty(), "A disconnected device listed unrelated controller inputs");
        invoke(screen, "closeOptionDropdown");
        for(var device : com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers.connected()){
            gamepad.data().putString("DeviceSelector", device.id());
            String previousBinding = gamepad.data().getString("BindingId");
            invoke(screen, "configureBinding", gamepad);
            check(previousBinding.equals(gamepad.data().getString("BindingId")), "Opening the Key dropdown cycled the binding");
            var keys = field(screen, "optionDropdown");
            var available = com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers.bindingOptions(device.id());
            check(((List<?>)invoke(keys, "options")).equals(available.stream().map(com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings.BindingOption::id).toList()),
                    "The Key dropdown did not match the selected device's reported inputs");
            capture(out.resolve("controller-key-dropdown.png"), graphics -> screen.render(graphics, -1, -1, 0));
            if(!available.isEmpty()){
                var selected = available.getFirst();
                invoke(screen, "filterOptionDropdown", selected.label());
                keys = field(screen, "optionDropdown");
                check(((List<?>)invoke(keys, "options")).contains(selected.id()), "The Key dropdown could not search by input name");
                int keyTop = (int)invoke(screen, "optionDropdownRowsTop", keys);
                invoke(screen, "clickOptionDropdown", (double)(int)invoke(keys, "x") + 10, (double)keyTop + 2, 0);
                check(selected.id().equals(gamepad.data().getString("BindingId")) && selected.label().equals(gamepad.data().getString("BindingLabel")),
                        "Selecting a Key option did not save its binding and display name");
                check(selected.id().equals(AdvancedGraphDocument.fromTag(draft.toTag()).nodes().getFirst().data().getString("BindingId")),
                        "The selected controller input did not survive save/load");
            }else invoke(screen, "closeOptionDropdown");
        }
        gamepad.data().putString("DeviceSelector", "missing/0");
        check("Disconnected controller".equals(invoke(screen, "typeControlValue", gamepad)), "A missing device silently fell back to automatic");
        check(" ".equals(invoke(screen, "propertyValueLabel", space, "Value")), "A string constant containing space was shown as unset");
        invoke(screen, "putInputDefault", split, "delimiter", "string", " ");
        check(" ".equals(invoke(screen, "inputString", split, "delimiter", "fallback")), "The string editor discarded a space delimiter");
        check(" ".equals(invoke(screen, "inputValueLabel", split, "delimiter", "string")), "The delimiter displayed as unset");
        capture(out.resolve("device-selector-and-space.png"), graphics -> screen.render(graphics, -1, -1, 0));
        var colors = GraphRuntime.fromLibraryValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.map(
                com.rieno.gadgetsandgizmos.lib.graph.GradientGraphNode.samples(
                        com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset.fromColors(List.of(0xFF0000, 0x00FF00, 0x0000FF), false))));
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var canvasGraph = new AdvancedGraphDocument();
            var gradient = node("gradient", "createthrusters:gradient", 30, 20);
            gradient.data().put(com.rieno.gadgetsandgizmos.lib.graph.GradientGraphNode.PRESET,
                    com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset.fromColors(List.of(0xFF0000, 0x00FF00, 0x0000FF), false).write());
            var widget = node("widget", type, 330, 40);
            var inputs = new CompoundTag(); inputs.putString("paint", "number"); widget.data().put("DynamicInputs", inputs);
            var elements = new ListTag(); var box = new CompoundTag();
            box.putString("Type", "box"); box.putInt("X", 10); box.putInt("Y", 10); box.putInt("W", 100); box.putInt("H", 35);
            AdvancedHudElementBinding.bind(box, "Color", "paint"); elements.add(box); widget.data().put("WidgetElements", elements);
            canvasGraph.nodes().addAll(List.of(gradient, widget));
            canvasGraph.edges().add(new AdvancedGraphDocument.Edge("paint", "gradient", "gradient", "widget", "paint"));
            screen.updateProjectionGraph(canvasGraph);
            var current = (AdvancedGraphDocument)field(screen, "draft"); widget = current.nodes().get(1);
            var preview = (CompoundTag)invoke(screen, "hudPreviewElement", widget, widget.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0));
            check(com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue.gradient(preview, "Color").isPresent(), type + " designer lost a connected gradient");
            invoke(screen, "openHud", widget);
            capture(out.resolve(type + "-gradient-editor.png"), graphics -> screen.render(graphics, -1, -1, 0));
            set(screen, "hudOpen", false);
        }
        capture(out.resolve("gradient-widget-elements.png"), graphics -> {
            graphics.fill(0, 0, 800, 500, 0xFF161D26);
            int idx = 0;
            for(String type : List.of("box", "text", "button", "toggle", "slider", "progress", "dropdown")){
                var elm = new CompoundTag(); elm.putString("Type", type); elm.putString("Text", "Gradient colors");
                elm.putInt("W", 240); elm.putInt("H", 34); elm.putDouble("Min", 0); elm.putDouble("Max", 1); elm.putDouble("Value", .75);
                elm.putString("Style", "material_filled");
                for(String property : List.of("Color", "BackgroundColor", "AccentColor", "BorderColor")) AdvancedHudElementBinding.bind(elm, property, "paint");
                var resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> colors);
                check(com.rieno.gadgetsandgizmos.lib.graph.GraphColorValue.gradient(resolved, "Color").isPresent(), "The editor lost gradient paint");
                graphics.pose().pushPose();
                try{
                    graphics.pose().translate(40, 20 + idx++ * 62, 0);
                    AdvancedHudWidgetRenderer.draw(com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer.gui(graphics, mc.font),
                            mc.font, resolved, 240, 34, port -> colors, true);
                }finally{ graphics.pose().popPose(); }
            }
        });
    }

    private static void validateWires(Minecraft mc, AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var preferences = AdvancedControllerUiPreferences.load();
        var initialMode = field(screen, "wireMode");
        var initialAvoidance = field(screen, "avoidWireNodes");
        try{
            var graph = new AdvancedGraphDocument();
            graph.nodes().addAll(List.of(node("source", "constant_number", 450, 70),
                    node("target", "createthrusters:convert_angle", 30, 70),
                    node("blocker", "createthrusters:convert_angle", 240, 30),
                    node("reroute", "reroute", 340, 230), node("lower", "createthrusters:convert_angle", 30, 320),
                    node("vertical0", "reroute", 730, 40), node("vertical1", "reroute", 730, 180), node("vertical2", "reroute", 730, 320)));
            graph.edges().addAll(List.of(new AdvancedGraphDocument.Edge("backward", "source", "value", "target", "angle"),
                    new AdvancedGraphDocument.Edge("reroute-in", "source", "value", "reroute", "value"),
                    new AdvancedGraphDocument.Edge("reroute-out", "reroute", "value", "lower", "angle"),
                    new AdvancedGraphDocument.Edge("vertical-in", "vertical0", "value", "vertical1", "value"),
                    new AdvancedGraphDocument.Edge("vertical-out", "vertical1", "value", "vertical2", "value")));
            screen.updateProjectionGraph(graph);
            set(screen, "wireMode", GraphWireGeometry.Mode.STRAIGHT);
            set(screen, "avoidWireNodes", false);
            set(screen, "zoom", 0.65D); set(screen, "panX", 0D); set(screen, "panY", 20D);
            invoke(screen, "prepareGraphWires");
            var paths = (java.util.Map<String, GraphWireGeometry.Path>)field(screen, "graphWirePaths");
            check(paths.size() == 5, "The graph omitted a routed wire");
            for(var path : paths.values()) for(var part : path.segments()){
                double x = Math.abs(part.end().x() - part.start().x()), y = Math.abs(part.end().y() - part.start().y());
                check(x < 1.0E-7D || y < 1.0E-7D || Math.abs(x - y) < 1.0E-7D, "Straight rendering used a diagonal other than 45 degrees");
            }
            var draft = (AdvancedGraphDocument)field(screen, "draft");
            capture(out.resolve("wires-straight-default.png"), graphics -> screen.render(graphics, -1, -1, 0));
            var avoidanceRow = invoke(screen, "toolsMenuRowBounds", 10);
            set(screen, "toolsMenuOpen", true);
            var saveRow = invoke(screen, "toolsMenuRowBounds", 3);
            boolean saveOnClose = (boolean)field(screen, "saveOnClose");
            for(int idx = 0; idx < 2; idx++){
                screen.mouseClicked((int)invoke(saveRow, "x") + 8D, (int)invoke(saveRow, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                check((boolean)field(screen, "toolsMenuOpen"), "Save on close dismissed the Tools menu");
            }
            check((boolean)field(screen, "saveOnClose") == saveOnClose, "Save on close did not complete its toggle cycle");
            screen.mouseClicked((int)invoke(avoidanceRow, "x") + 8D,
                    (int)invoke(avoidanceRow, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            check((boolean)field(screen, "avoidWireNodes") && AdvancedControllerUiPreferences.load().avoidWireNodes(),
                    "Avoid nodes did not turn on and persist");
            check((boolean)field(screen, "toolsMenuOpen"), "Avoid nodes dismissed the Tools menu");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            check(!(boolean)field(screen, "toolsMenuOpen"), "Escape did not close the Tools menu");
            invoke(screen, "prepareGraphWires");
            validateWireClearance(screen, draft, paths);
            var reroute = draft.nodes().stream().filter(node -> node.id().equals("reroute")).findFirst().orElseThrow();
            var vertical = draft.nodes().stream().filter(node -> node.id().equals("vertical1")).findFirst().orElseThrow();
            var input = invoke(screen, "portPosition", reroute, "value", false);
            var output = invoke(screen, "portPosition", reroute, "value", true);
            check((int)invoke(output, "x") < (int)invoke(input, "x"), "The backward reroute still faces right");
            var up = invoke(screen, "portPosition", vertical, "value", false);
            var down = invoke(screen, "portPosition", vertical, "value", true);
            check(invoke(up, "x").equals(invoke(down, "x")) && (int)invoke(up, "y") < (int)invoke(down, "y"), "The vertical reroute did not rotate its sockets");
            var hit = invoke(screen, "portAt", ((Number)invoke(down, "x")).doubleValue(), ((Number)invoke(down, "y")).doubleValue());
            check(hit != null && (boolean)invoke(hit, "output"), "The rotated output socket cannot be selected");
            capture(out.resolve("wires-straight.png"), graphics -> screen.render(graphics, -1, -1, 0));
            var backward = paths.get("backward");
            check(backward.bounds().right() > backward.points().getFirst().x()
                    && backward.bounds().left() < backward.points().getLast().x(), "A backward wire did not escape both node sides");
            for(double zoom : new double[]{1, 0.65, 0.35}){
                set(screen, "zoom", zoom); set(screen, "panX", 11D); set(screen, "panY", 17D);
                invoke(screen, "prepareGraphWires");
                check(paths.get("backward") == backward, "Pan or zoom rebuilt the graph-space routes");
                var viewport = (GraphViewport)invoke(screen, "graphViewport");
                var point = backward.pointAt(0.45);
                check(invoke(screen, "edgeAt", viewport.screenX(point.x()), viewport.screenY(point.y())) != null,
                        "Wire picking diverged from the routed line after pan and zoom");
            }
            set(screen, "zoom", 0.65D); set(screen, "panX", 0D); set(screen, "panY", 20D);
            set(screen, "toolsMenuOpen", true);
            capture(out.resolve("wires-tools-menu.png"), graphics -> screen.render(graphics, -1, -1, 0));
            var row = invoke(screen, "toolsMenuRowBounds", 9);
            screen.mouseClicked((int)invoke(row, "x") + 8D, (int)invoke(row, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            check(field(screen, "wireMode") == GraphWireGeometry.Mode.ROUNDED, "Straight did not cycle to Smooth");
            check((boolean)field(screen, "toolsMenuOpen"), "Selecting Smooth dismissed the Tools menu");
            check(AdvancedControllerUiPreferences.load().wireMode().equals("rounded"), "The Smooth preference was not saved");
            var smoothReopened = new AdvancedContraptionControllerScreen(screen.getMenu(), mc.player.getInventory(), Component.literal("Reopened Smooth"));
            check(field(smoothReopened, "wireMode") == GraphWireGeometry.Mode.ROUNDED, "A reopened controller lost its Smooth preference");
            capture(out.resolve("wires-tools-menu-smooth.png"), graphics -> screen.render(graphics, -1, -1, 0));
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            capture(out.resolve("wires-smooth.png"), graphics -> screen.render(graphics, -1, -1, 0));
            validateWireClearance(screen, draft, paths);
            var rounded = paths.get("backward"); var roundedPoint = rounded.pointAt(.45);
            var roundedViewport = (GraphViewport)invoke(screen, "graphViewport");
            check(invoke(screen, "edgeAt", roundedViewport.screenX(roundedPoint.x()), roundedViewport.screenY(roundedPoint.y())) != null,
                    "Smooth wire picking diverged from its rendered path");
            set(screen, "toolsMenuOpen", true);
            for(var expected : List.of(GraphWireGeometry.Mode.BEZIER, GraphWireGeometry.Mode.STRAIGHT,
                    GraphWireGeometry.Mode.ROUNDED, GraphWireGeometry.Mode.BEZIER)){
                screen.mouseClicked((int)invoke(row, "x") + 8D, (int)invoke(row, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                check(field(screen, "wireMode") == expected && (boolean)field(screen, "toolsMenuOpen"),
                        "Repeated wire clicks broke the mode order or dismissed the Tools menu");
            }
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            check(field(screen, "wireMode") == GraphWireGeometry.Mode.BEZIER, "The Tools toggle did not select Bezier wires");
            check(AdvancedControllerUiPreferences.load().wireMode().equals("bezier"), "The wire mode was not saved to disk");
            var reopened = new AdvancedContraptionControllerScreen(screen.getMenu(), mc.player.getInventory(), Component.literal("Reopened"));
            check(field(reopened, "wireMode") == GraphWireGeometry.Mode.BEZIER, "A reopened controller lost its wire preference");
            check((boolean)field(reopened, "avoidWireNodes"), "A reopened controller lost its avoidance preference");
            capture(out.resolve("wires-bezier.png"), graphics -> screen.render(graphics, -1, -1, 0));
            check(paths.values().stream().allMatch(path -> !path.beziers().isEmpty()), "A Bezier wire contained a polyline fallback");
            validateWireClearance(screen, draft, paths);
            check(paths.get("backward") != backward, "Switching mode retained the old straight path");
            invoke(screen, "handleToolsMenuClick", (int)invoke(avoidanceRow, "x") + 8D,
                    (int)invoke(avoidanceRow, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            check(!(boolean)field(screen, "avoidWireNodes") && !AdvancedControllerUiPreferences.load().avoidWireNodes(),
                    "Avoid nodes did not turn off and persist");
            var forward = new AdvancedGraphDocument();
            forward.nodes().addAll(List.of(node("forward-source", "constant_number", 20, 30),
                    node("forward-target", "createthrusters:convert_angle", 440, 190), node("forward-blocker", "createthrusters:convert_angle", 250, 70)));
            forward.edges().add(new AdvancedGraphDocument.Edge("forward", "forward-source", "value", "forward-target", "angle"));
            screen.updateProjectionGraph(forward);
            set(screen, "zoom", 0.65D); set(screen, "panX", 0D); set(screen, "panY", 20D);
            capture(out.resolve("wires-bezier-default.png"), graphics -> screen.render(graphics, -1, -1, 0));
            check(paths.get("forward").beziers().size() == 1, "Disabled avoidance still detoured around the unrelated node");
            set(screen, "wireMode", GraphWireGeometry.Mode.ROUNDED);
            capture(out.resolve("wires-smooth-default.png"), graphics -> screen.render(graphics, -1, -1, 0));
            set(screen, "wireMode", GraphWireGeometry.Mode.BEZIER);
            set(screen, "toolsMenuOpen", true);
            capture(out.resolve("wires-tools-menu-bezier.png"), graphics -> screen.render(graphics, -1, -1, 0));
            invoke(screen, "handleToolsMenuClick", (int)invoke(avoidanceRow, "x") + 8D,
                    (int)invoke(avoidanceRow, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            capture(out.resolve("wires-bezier-avoid-nodes.png"), graphics -> screen.render(graphics, -1, -1, 0));
            validateWireClearance(screen, (AdvancedGraphDocument)field(screen, "draft"), paths);
            var templates = invoke(screen, "toolsMenuRowBounds", 6);
            screen.mouseClicked((int)invoke(templates, "x") + 8D, (int)invoke(templates, "y") + 8D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            check(!(boolean)field(screen, "toolsMenuOpen"), "Opening Templates left the Tools menu open");
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }finally{
            AdvancedControllerUiPreferences.save(preferences);
            set(screen, "wireMode", initialMode); set(screen, "graphWireSignature", Long.MIN_VALUE);
            set(screen, "avoidWireNodes", initialAvoidance);
            set(screen, "toolsMenuOpen", false);
        }
    }

    // Reproduce shallow steps and steep reversals with real numeric ports at close spacing
    @SuppressWarnings("unchecked")
    private static void validateStraightTurns(AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var initialMode = field(screen, "wireMode"); var initialAvoidance = field(screen, "avoidWireNodes");
        try{
            var graph = new AdvancedGraphDocument();
            graph.nodes().addAll(List.of(node("shallow-source", "absolute", 20, 30), node("shallow-target", "absolute", 400, 30),
                    node("steep-source", "absolute", 20, 180), node("steep-target", "absolute", 400, 300)));
            graph.edges().addAll(List.of(new AdvancedGraphDocument.Edge("shallow", "shallow-source", "value", "shallow-target", "value"),
                    new AdvancedGraphDocument.Edge("steep", "steep-source", "value", "steep-target", "value")));
            screen.updateProjectionGraph(graph);
            set(screen, "wireMode", GraphWireGeometry.Mode.STRAIGHT); set(screen, "avoidWireNodes", false);
            set(screen, "zoom", 0.8D); set(screen, "panX", 0D); set(screen, "panY", 20D);
            invoke(screen, "prepareGraphWires");
            var draft = (AdvancedGraphDocument)field(screen, "draft");
            for(int idx = 0; idx < 2; idx++){
                var source = draft.nodes().get(idx * 2); var target = draft.nodes().get(idx * 2 + 1);
                var a = (GraphWireGeometry.Point)invoke(screen, "graphPortPosition", source, "value", true);
                var b = (GraphWireGeometry.Point)invoke(screen, "graphPortPosition", target, "value", false);
                invoke(screen, "replaceMovedNode", idx * 2 + 1, target, a.x() + 40 - b.x(), a.y() + (idx == 0 ? 4 : 120) - b.y(), false);
            }
            invoke(screen, "prepareGraphWires");
            var paths = (java.util.Map<String, GraphWireGeometry.Path>)field(screen, "graphWirePaths");
            for(var entry : paths.entrySet()){
                var path = entry.getValue();
                check(path.segments().size() <= (entry.getKey().equals("shallow") ? 3 : 5), "A numeric wire still contained unnecessary steps");
                for(var part : path.segments()){
                    double dx = part.end().x() - part.start().x(), dy = part.end().y() - part.start().y();
                    check(dx >= -1.0E-7D && dy >= -1.0E-7D, "A numeric wire doubled back near its socket");
                    check(Math.abs(dx) < 1.0E-7D || Math.abs(dy) < 1.0E-7D || Math.abs(dx - dy) < 1.0E-7D,
                            "A numeric wire used an arbitrary diagonal");
                }
            }
            validateWireClearance(screen, draft, paths);
            capture(out.resolve("wires-straight-clean-turns.png"), graphics -> screen.render(graphics, -1, -1, 0));
            set(screen, "wireMode", GraphWireGeometry.Mode.ROUNDED);
            capture(out.resolve("wires-smooth-clean-turns.png"), graphics -> screen.render(graphics, -1, -1, 0));
            validateWireClearance(screen, draft, paths);
        }finally{
            set(screen, "wireMode", initialMode); set(screen, "avoidWireNodes", initialAvoidance);
            set(screen, "graphWireSignature", Long.MIN_VALUE);
        }
    }

    // Check opacity in the actual framebuffer and trace the hovered wire through a node and another wire
    @SuppressWarnings("unchecked")
    private static void validateWireHover(AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var initialMode = field(screen, "wireMode"); var initialAvoidance = field(screen, "avoidWireNodes");
        try{
            for(var mode : List.of(GraphWireGeometry.Mode.STRAIGHT, GraphWireGeometry.Mode.ROUNDED, GraphWireGeometry.Mode.BEZIER)){
                var graph = new AdvancedGraphDocument();
                graph.nodes().addAll(List.of(node("trace-source", "absolute", 20, 60), node("trace-target", "absolute", 480, 240),
                        node("other-source", "absolute", 20, 310), node("other-target", "absolute", 480, 30), node("occluder", "absolute", 250, 140)));
                graph.edges().addAll(List.of(new AdvancedGraphDocument.Edge("trace", "trace-source", "value", "trace-target", "value"),
                        new AdvancedGraphDocument.Edge("other", "other-source", "value", "other-target", "value")));
                screen.updateProjectionGraph(graph);
                set(screen, "wireMode", mode); set(screen, "avoidWireNodes", false);
                set(screen, "zoom", 0.7D); set(screen, "panX", 0D); set(screen, "panY", 20D);
                invoke(screen, "prepareGraphWires");
                var paths = (java.util.Map<String, GraphWireGeometry.Path>)field(screen, "graphWirePaths");
                var covered = paths.get("trace").pointAt(0.3);
                var draft = (AdvancedGraphDocument)field(screen, "draft");
                var blocker = draft.nodes().get(4);
                invoke(screen, "replaceMovedNode", 4, blocker,
                        covered.x() - (int)invoke(screen, "nodeWidth", blocker) * 0.45 - blocker.x(),
                        covered.y() - (int)invoke(screen, "nodeHeight", blocker) * 0.7 - blocker.y(), false);
                invoke(screen, "prepareGraphWires");
                var primary = paths.get("trace"); var other = paths.get("other");
                var viewport = (GraphViewport)invoke(screen, "graphViewport");
                var bodies = new java.util.ArrayList<GraphWireGeometry.Bounds>();
                for(var node : draft.nodes()) bodies.add((GraphWireGeometry.Bounds)invoke(screen, "wireNodeBounds", node));
                var sample = other.points().stream().filter(point -> bodies.stream().noneMatch(bounds -> bounds.inflated(20).contains(point))
                        && primary.distanceSquared(point.x(), point.y()) > 400).findFirst().orElseThrow();
                GraphWireGeometry.Point crossing = null;
                for(int idx = 100; idx < 900; idx++){
                    var point = primary.pointAt(idx / 1000D);
                    if(other.distanceSquared(point.x(), point.y()) < 0.25 && bodies.stream().noneMatch(bounds -> bounds.inflated(2).contains(point))){ crossing = point; break; }
                }
                check(crossing != null, "The hover fixture did not cross both wire paths");
                var hover = primary.pointAt(0.8);
                int mouseX = (int)Math.round(viewport.screenX(hover.x())), mouseY = (int)Math.round(viewport.screenY(hover.y()));
                var picked = (AdvancedGraphDocument.Edge)invoke(screen, "edgeAt", (double)mouseX, (double)mouseY);
                check(picked != null && picked.id().equals("trace"), "The hover fixture picked the wrong wire");
                String prefix = "wire-hover-" + mode.id();
                var edges = List.copyOf(draft.edges()); draft.edges().clear();
                invoke(screen, "clearGraphRenderCache");
                Path background = out.resolve(prefix + "-background.png");
                capture(background, graphics -> screen.render(graphics, -1, -1, 0));
                draft.edges().addAll(edges); invoke(screen, "clearGraphRenderCache");
                Path normal = out.resolve(prefix + "-normal.png"), focused = out.resolve(prefix + "-focused.png"), restored = out.resolve(prefix + "-restored.png");
                capture(normal, graphics -> screen.render(graphics, -1, -1, 0));
                capture(focused, graphics -> screen.render(graphics, mouseX, mouseY, 0));
                check(((AdvancedGraphDocument.Edge)field(screen, "hoveredGraphWire")).id().equals("trace"), "The hovered wire was not retained for the foreground pass");
                capture(restored, graphics -> screen.render(graphics, -1, -1, 0));
                check(field(screen, "hoveredGraphWire") == null, "Leaving a wire retained the highlight");
                try(var bg = NativeImage.read(Files.readAllBytes(background)); var before = NativeImage.read(Files.readAllBytes(normal));
                    var focus = NativeImage.read(Files.readAllBytes(focused)); var after = NativeImage.read(Files.readAllBytes(restored))){
                    int x = (int)Math.round(viewport.screenX(sample.x()) * 1.5), y = (int)Math.round(viewport.screenY(sample.y()) * 1.5);
                    int bestX = x, bestY = y, contrast = -1;
                    for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++){
                        int diff = pixelContrast(before.getPixelRGBA(x + dx, y + dy), bg.getPixelRGBA(x + dx, y + dy));
                        if(diff > contrast){ contrast = diff; bestX = x + dx; bestY = y + dy; }
                    }
                    check(contrast > 100, "The opacity fixture missed the other wire");
                    int base = bg.getPixelRGBA(bestX, bestY), opaque = before.getPixelRGBA(bestX, bestY), dimmed = focus.getPixelRGBA(bestX, bestY);
                    for(int shift : new int[]{0, 8, 16}){
                        int a = base >>> shift & 255, b = opaque >>> shift & 255, actual = dimmed >>> shift & 255;
                        check(Math.abs(actual - Math.round(a + (b - a) * 0.4F)) <= 2, "Other wire opacity did not reach forty percent at a segment join");
                    }
                    check(after.getPixelRGBA(bestX, bestY) == opaque, "Leaving hover did not restore normal wire opacity");
                    check(!whitePixelNear(before, viewport, covered) && whitePixelNear(focus, viewport, covered), "The highlighted wire stayed hidden beneath a node");
                    check(!whitePixelNear(before, viewport, crossing) && whitePixelNear(focus, viewport, crossing), "Another wire covered the hovered wire");
                }
            }
        }finally{
            set(screen, "wireMode", initialMode); set(screen, "avoidWireNodes", initialAvoidance);
            set(screen, "graphWireSignature", Long.MIN_VALUE);
        }
    }

    private static int pixelContrast(int a, int b){
        int total = 0;
        for(int shift : new int[]{0, 8, 16}){ int delta = (a >>> shift & 255) - (b >>> shift & 255); total += delta * delta; }
        return total;
    }

    private static boolean whitePixelNear(NativeImage image, GraphViewport viewport, GraphWireGeometry.Point point){
        int x = (int)Math.round(viewport.screenX(point.x()) * 1.5), y = (int)Math.round(viewport.screenY(point.y()) * 1.5);
        for(int dx = -1; dx <= 1; dx++) for(int dy = -1; dy <= 1; dy++){
            int col = image.getPixelRGBA(x + dx, y + dy);
            if((col & 255) >= 250 && (col >>> 8 & 255) >= 250 && (col >>> 16 & 255) >= 250) return true;
        }
        return false;
    }

    // Check complete socket leads as well as the middle detour against rendered node bodies
    private static void validateWireClearance(AdvancedContraptionControllerScreen screen, AdvancedGraphDocument graph,
                                               java.util.Map<String, GraphWireGeometry.Path> paths) throws Exception{
        for(var node : graph.nodes()){
            if(node.type().equals("reroute")) continue;
            var bounds = ((GraphWireGeometry.Bounds)invoke(screen, "wireNodeBounds", node)).inflated(-0.1);
            for(var path : paths.values()) for(var part : path.segments()){
                int steps = Math.max(2, (int)Math.ceil(part.length()));
                for(int idx = 1; idx < steps; idx++){
                    double t = idx / (double)steps;
                    var point = new GraphWireGeometry.Point(part.start().x() + (part.end().x() - part.start().x()) * t,
                            part.start().y() + (part.end().y() - part.start().y()) * t);
                    check(!bounds.contains(point), "A routed wire crossed the rendered body of " + node.id() + " at " + point);
                }
            }
        }
    }

    // Verify both designers, styled option popups and basic HUD text wrapping
    private static void validateDropdowns(Minecraft mc, AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var places = new java.util.LinkedHashMap<String, GraphValue>();
        places.put("Home", GraphValue.map(java.util.Map.of("x", 12, "y", 64, "z", -8)));
        places.put("Storage", GraphValue.map(java.util.Map.of("x", -20, "y", 64, "z", 42)));
        for(int idx = 0; idx < 14; idx++) places.put("Destination " + idx, GraphValue.list(List.of(idx, 64, -idx)));
        var data = GraphValue.map(places);
        for(String type : List.of("advanced_hud_element", "acc_display_widget")){
            var graph = new AdvancedGraphDocument(); var widget = node("dropdown", type, 40, 40);
            widget.data().put("WidgetElements", new ListTag());
            var inputs = widget.data().getCompound("DynamicInputs");
            inputs.putString("places", "map"); inputs.putString("items", "list"); inputs.putString("title", "string");
            for(String port : List.of("connected_map", "connected_list", "stored_map", "stored_list", "connected_number", "live_map", "live_list", "live_string")) inputs.putString(port, "any");
            widget.data().put("DynamicInputs", inputs);
            var defaults = widget.data().getCompound("Defaults");
            defaults.put("places", GraphRuntime.fromLibraryValue(data).toTag());
            defaults.put("stored_map", GraphRuntime.fromLibraryValue(data).toTag());
            defaults.put("stored_list", GraphRuntime.fromLibraryValue(GraphValue.list(List.of("Home", "Storage"))).toTag());
            var source = node("collections", "get_block_data", 280, 40);
            var outputs = source.data().getCompound("DynamicOutputs");
            outputs.putString("map_data", "map"); outputs.putString("list_data", "list"); outputs.putString("number_data", "number");
            source.data().put("DynamicOutputs", outputs);
            graph.nodes().addAll(List.of(widget, source));
            for(String kind : List.of("map", "list", "number")) graph.edges().add(new AdvancedGraphDocument.Edge(
                    "options_" + kind, source.id(), kind + "_data", widget.id(), "connected_" + kind));
            screen.updateProjectionGraph(graph);
            widget = ((AdvancedGraphDocument)field(screen, "draft")).nodes().getFirst();
            invoke(screen, "addAdvancedHudElement", widget, "dropdown"); invoke(screen, "openHud", widget);
            long previousRevision = (long)field(screen, "draftRuntimeRevision");
            var live = (java.util.Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeInputs");
            set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
            live.put((String)invoke(screen, "draftRuntimeKey", widget.id(), "live_map"), GraphRuntime.fromLibraryValue(data));
            live.put((String)invoke(screen, "draftRuntimeKey", widget.id(), "live_list"), GraphRuntime.fromLibraryValue(GraphValue.list(List.of("Home"))));
            live.put((String)invoke(screen, "draftRuntimeKey", widget.id(), "live_string"), AdvancedGraphDocument.Value.string("..."));
            set(screen, "hudInteractivePalette", true);
            set(screen, "hudSelectedIdx", 0); set(screen, "hudBindingDropdownKey", "Options");
            var choices = (List<?>)invoke(screen, "hudBindingOptions");
            check(choices.contains("places") && choices.contains("items") && !choices.contains("title"), "Dropdown options accepted a scalar input");
            check(choices.containsAll(List.of("connected_map", "connected_list", "stored_map", "stored_list", "live_map", "live_list"))
                    && !choices.contains("connected_number") && !choices.contains("live_string"), "Dropdown options hid collection values on Any ports or accepted a scalar wire: " + choices
                    + "; connected map=" + invoke(screen, "hudPortType", widget, "connected_map")
                    + "; connected list=" + invoke(screen, "hudPortType", widget, "connected_list")
                    + "; stored map=" + invoke(screen, "hudPortType", widget, "stored_map")
                    + "; stored list=" + invoke(screen, "hudPortType", widget, "stored_list"));
            var elm = (CompoundTag)invoke(screen, "hudElement");
            set(screen, "hudDataBindings", true);
            capture(out.resolve("dropdown-options-bindings-" + type + ".png"), graphics -> screen.render(graphics, -1, -1, 0));
            int row = choices.indexOf("connected_map");
            int scroll = Math.max(0, row - 7);
            set(screen, "hudBindingDropdownScroll", scroll);
            var binding = invoke(screen, "hudBindingDropdownBounds", Math.min(8, choices.size()));
            invoke(screen, "clickHudBindingDropdown", (int)invoke(binding, "x") + 8D,
                    (int)invoke(binding, "y") + 2D + (row - scroll) * 18 + 4);
            check("connected_map".equals(AdvancedHudElementBinding.port(elm, "Options")), "The resolved collection port could not be bound");
            set(screen, "hudDataBindings", false);
            AdvancedHudElementBinding.bind(elm, "Options", "places");
            check(((List<?>)invoke(screen, "hudBindableProps", elm)).containsAll(List.of("Options", "X", "W", "Color", "FontSize", "BorderRadius")),
                    "The dropdown lost designer customization bindings");
            set(screen, "hudBindingDropdownKey", null);
            capture(out.resolve("dropdown-designer-" + type + ".png"), graphics -> screen.render(graphics, -1, -1, 0));
            invoke(screen, "closeHud");
            for(String port : List.of("live_map", "live_list", "live_string")) live.remove(invoke(screen, "draftRuntimeKey", widget.id(), port));
            set(screen, "draftRuntimeRevision", previousRevision);
        }
        capture(out.resolve("dropdown-presets.png"), graphics -> {
            graphics.fill(0, 0, 1200, 750, 0xFF202B30);
            int y = 50;
            for(var preset : HudStyleCatalog.DropdownStyle.values()){
                var elm = new CompoundTag(); elm.putString("Type", "dropdown"); elm.putString("Text", "Destination");
                elm.putString("Style", preset.id()); elm.putString("ValuePort", "selected"); AdvancedHudElementStyle.applyDefaults(elm);
                AdvancedHudElementBinding.bind(elm, "Options", "places");
                graphics.drawString(mc.font, preset.label(), 30, y + 9, -1, false);
                graphics.pose().pushPose(); graphics.pose().translate(170, y, 0);
                AdvancedHudWidgetRenderer.draw(com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer.gui(graphics, mc.font), mc.font,
                        elm, 210, 28, port -> "places".equals(port) ? GraphRuntime.fromLibraryValue(data)
                                : "selected".equals(port) ? GraphRuntime.fromLibraryValue(places.get("Home")) : null, false);
                graphics.pose().popPose(); y += 70;
            }
            try{
                Class<?> rect = Class.forName(CTPhysicsGogglesClient.class.getName() + "$Rect");
                var ctor = rect.getDeclaredConstructor(int.class, int.class, int.class, int.class); ctor.setAccessible(true);
                var draw = CTPhysicsGogglesClient.class.getDeclaredMethod("drawCustomHudPanel", GuiGraphics.class, rect, List.class, boolean.class);
                draw.setAccessible(true);
                draw.invoke(null, graphics, ctor.newInstance(440, 50, 180, 100),
                        List.of("Basic HUD wraps long text inside its bounds", "Storage: Home coordinates are available across several lines."), false);
            }catch(Exception err){ throw new IllegalStateException(err); }
        });
        var elm = new CompoundTag(); elm.putString("Type", "dropdown"); AdvancedHudElementStyle.applyDefaults(elm);
        var entries = DropdownOptions.entries(data); var selected = new String[]{null};
        var popup = new HudDropdownScreen(null, () -> entries, null, "Destination", AdvancedHudWidgetRenderer.style(elm),
                HudStyleCatalog.DropdownStyle.GLASSMORPHIC, new HudControlRenderer.Rect(400, 120, 220, 28), key -> selected[0] = key);
        popup.init(mc, 1200, 750);
        capture(out.resolve("dropdown-popup.png"), graphics -> {
            graphics.fill(0, 0, 1200, 750, 0xFF202B30); popup.render(graphics, 420, 183, 0);
        });
        popup.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0); popup.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("Storage".equals(selected[0]), "Keyboard navigation returned an option label or the wrong identity");
        var bottom = new HudDropdownScreen(null, () -> entries, null, "Destination", AdvancedHudWidgetRenderer.style(elm),
                HudStyleCatalog.DropdownStyle.FLAT, new HudControlRenderer.Rect(400, 700, 220, 28), key -> selected[0] = key);
        bottom.init(mc, 1200, 750);
        var bounds = (HudControlRenderer.Rect)invoke(bottom, "popup", entries.size());
        check(bounds.bottom() <= 700 && bounds.y() >= 0, "The dropdown popup escaped the screen instead of opening above its control");
        bottom.mouseScrolled(410, bounds.y() + 4, 0, -1);
        selected[0] = null;
        bottom.mouseClicked(410, bounds.y() + 6, 0);
        check("Storage".equals(selected[0]), "Scrolling and mouse selection chose the wrong item");
        bottom.keyPressed(GLFW.GLFW_KEY_END, 0, 0); bottom.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(entries.getLast().key().equals(selected[0]), "End did not navigate to the final option");
        selected[0] = "unchanged";
        var same = new HudDropdownScreen(null, () -> entries, "Home", "Destination", AdvancedHudWidgetRenderer.style(elm),
                HudStyleCatalog.DropdownStyle.MATERIAL, new HudControlRenderer.Rect(400, 120, 220, 28), key -> selected[0] = key);
        same.init(mc, 1200, 750); same.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("unchanged".equals(selected[0]), "Selecting the current option submitted another interaction");
        same.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check("unchanged".equals(selected[0]), "Cancelling a dropdown submitted an interaction");
    }

    // Show degree components with the matching physical axes in the real graph editor
    private static void validateAngleConversion(AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var graph = new AdvancedGraphDocument();
        for(String format : List.of("euler", "tait_bryan")){
            var node = node(format, "createthrusters:convert_angle", format.equals("euler") ? 40 : 340, 60);
            node.data().putString(com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.INPUT_TYPE, "degrees");
            node.data().putString(com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.OUTPUT_TYPE, format);
            graph.nodes().add(node);
        }
        screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var euler = draft.nodes().getFirst();
        check(invoke(screen, "outputDisplayLabel", euler, "alpha").equals("Alpha (Z, deg)"), "Euler alpha uses the wrong axis");
        check(invoke(screen, "outputDisplayLabel", euler, "beta").equals("Beta (Y, deg)"), "Euler beta uses the wrong axis");
        check(invoke(screen, "outputDisplayLabel", euler, "gamma").equals("Gamma (Z, deg)"), "Euler gamma uses the wrong axis");
        check(invoke(screen, "propertyValueLabel", euler, com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.OUTPUT_TYPE).equals("Euler (ZYZ)"), "The selector omitted the proper Euler convention");
        check(invoke(screen, "propertyOptionLabel", euler, com.rieno.gadgetsandgizmos.lib.graph.math.AngleConversionNode.INPUT_TYPE, "tait_bryan").equals("Tait-Bryan (XYZ)"), "The selector mislabeled yaw/pitch/roll");
        check(invoke(screen, "outputDisplayLabel", draft.nodes().getLast(), "pitch").equals("Pitch (Y, deg)"), "Tait-Bryan omitted its degree units");
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        capture(out.resolve("angle-components.png"), graphics -> screen.render(graphics, -1, -1, 0));
    }

    // Use the production dropdown to select and retain a dimension advertised to the player
    private static void validateDimensionPressure(Minecraft mc, AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("pressure", "createthrusters:dimension_pressure", 50, 60));
        screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var pressure = draft.nodes().getFirst();
        var opts = (List<String>)invoke(screen, "inputOptions", pressure, "dimension");
        check(mc.getConnection().levels().stream().allMatch(key -> opts.contains(key.location().toString())),
                "Dimension Pressure omitted a dimension advertised to the player");
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        invoke(screen, "openOptionDropdown", pressure, "dimension", "string", 180D, 160D, 180D, opts);
        var dropdown = field(screen, "optionDropdown");
        check((boolean)invoke(dropdown, "searchable"), "Dimension selection is not searchable");
        capture(out.resolve("dimension-pressure-dropdown.png"), graphics -> screen.render(graphics, -1, -1, 0));
        int rowsTop = (int)invoke(screen, "optionDropdownRowsTop", dropdown);
        int rowHeight = (int)staticField(screen.getClass(), "OPTION_DROPDOWN_ITEM_HEIGHT");
        invoke(screen, "clickOptionDropdown", (int)invoke(dropdown, "x") + 8D, rowsTop + rowHeight + 3D, 0);
        var saved = AdvancedGraphDocument.fromTag(draft.toTag()).nodes().getFirst();
        check(opts.get(1).equals(saved.data().getCompound("Defaults").getCompound("dimension")
                .getCompound("Payload").getString("Value")), "The selected dimension did not survive save/load");
    }

    private static Object staticField(Class<?> type, String name) throws Exception{
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    // Wait for normal asynchronous decoding without drawing the Image node or blocking its render-thread callback
    private static void finishImageValidation(){
        var mc = Minecraft.getInstance(); Path out = Path.of(OUTPUT);
        try{
            var elm = new CompoundTag(); elm.putString("Type", "image"); AdvancedHudElementBinding.bind(elm, "Texture", "picture");
            String source = AdvancedHudWidgetRenderer.imageSource(elm, port -> pendingImage);
            if(!AdvancedHudImageClient.hasLoadedTexture(source)){
                check(++imageTicks < 200, "Embedded image ports could not decode without opening the Image node");
                return;
            }
            check(source.equals(pendingImage.asString()), "The image parameter lost its asset reference");
            capture(out.resolve("embedded-image-widget.png"), graphics -> check(AdvancedHudImageClient.draw(graphics, source, 20, 20, 160, 160), "The decoded image could not render"));
            PortInspectorClientValidation.validateImage(mc, out, pendingImage);
            Files.writeString(out.resolve("result.txt"), "PASSED image modal and Upload button, embedded image port rendering, shared parameter assignments, node gradient shortcuts and applicator gradient shortcuts\n");
        }catch(Throwable err){
            var trace = new StringWriter(); err.printStackTrace(new PrintWriter(trace));
            try{ Files.writeString(out.resolve("result.txt"), "FAILED\n" + trace); }catch(Exception ignored){}
        }
        pendingImage = null; mc.stop();
    }

    private static void capture(Path path, Consumer<GuiGraphics> draw) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "visual_graph_validation"))){
            renderer.render(1200, 750, 800, 500, draw);
            RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(1200, 750, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(path);
            }
        }
    }

    private static Object field(Object obj, String name) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(obj);
    }
    private static void set(Object obj, String name, Object value) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); field.set(obj, value);
    }
    private static Object invoke(Object obj, String name, Object... args) throws Exception{
        for(var method : obj.getClass().getDeclaredMethods()) if(method.getName().equals(name) && method.getParameterCount() == args.length){
            method.setAccessible(true); return method.invoke(obj, args);
        }
        throw new NoSuchMethodException(name);
    }
    private static AdvancedGraphDocument.Node node(String id, String type, double x, double y){
        return new AdvancedGraphDocument.Node(id, type, "", x, y, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
    private static void check(boolean condition, String message){ if(!condition) throw new AssertionError(message); }
}
