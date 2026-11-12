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
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector;
import com.rieno.gadgetsandgizmos.lib.client.ui.ImageSourceModal;
import com.rieno.gadgetsandgizmos.lib.display.PresentationLayout;
import com.rieno.gadgetsandgizmos.lib.graph.GradientGraphNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspection;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValueOutline;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Verify port hit testing, picker lifetime and world projection in a real Minecraft client
final class PortInspectorClientValidation{
    // Keep this validation inside the existing visual graph client run
    private PortInspectorClientValidation(){}

    // Capture the actual addon controls and exercise their pointer callbacks
    static void validate(Minecraft mc, Path out) throws Exception{
        var controller = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO,
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1236, mc.player.getInventory(), controller);
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Port Inspector"));
        screen.configureForProjection(); screen.init(mc, 800, 500);
        Path pinsFile = out.resolve("inspector-pin-validation.json");
        java.nio.file.Files.writeString(pinsFile, "[]");
        set(screen, "inspectorPins", new GraphPortInspectorPins(pinsFile)); set(screen, "inspectorPinsLoaded", true);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("gradient", "createthrusters:gradient", 60, 40));
        graph.nodes().add(node("color", "constant_color", 360, 40));
        screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var gradient = draft.nodes().getFirst();
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        capture(out.resolve("gradient-value-layout.png"), ctx -> screen.render(ctx, -1, -1, 0));
        int bodyTop = (int)invoke(screen, "nodeBodyTopUnits", gradient);
        int rowY = (int)invoke(screen, "bodyControlCenterY", gradient, "value");
        int nodeY = (int)invoke(screen, "screenY", gradient.y());
        check(rowY > nodeY + bodyTop + 64, "The Value input still overlaps the gradient builder");
        int x = (int)invoke(screen, "screenX", gradient.x()) + 12;
        int y = nodeY + bodyTop + 8 + 36 + 10;
        invoke(screen, "clickGradient", gradient, (double)x, (double)y, 0);
        screen.mouseReleased(x, y, 0);
        invoke(screen, "clickGradient", gradient, (double)x, (double)y, 0);
        var picker = (ColorPickerModal)field(screen, "graphColorPicker");
        check(picker != null && picker.isOpen(), "A gradient stop did not open its color picker");
        capture(out.resolve("gradient-picker.png"), ctx -> screen.render(ctx, -1, -1, 0));
        picker.mouseClicked(305 + 14 + 40, 162 + 30 + 20, 0);
        check(field(screen, "graphColorPicker") == picker && picker.isOpen(), "The gradient picker closed during a live color change");
        picker.cancel();
        check(field(screen, "graphColorPicker") == null, "The gradient picker did not close on Cancel");

        var samples = GraphRuntime.fromLibraryValue(GraphValue.map(GradientGraphNode.samples(GradientGraphNode.preset(gradient.data()))));
        @SuppressWarnings("unchecked") var outputs = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeOutputs");
        outputs.put("gradient:gradient", samples);
        set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
        var pos = invoke(screen, "portPosition", gradient, "gradient", true);
        int portX = (int)invoke(pos, "x"), portY = (int)invoke(pos, "y");
        var hit = invoke(screen, "portAt", (double)portX, (double)portY);
        check(hit != null && "gradient".equals(invoke(hit, "port")), "The gradient port hit selected another port");
        var selected = (AdvancedGraphDocument.Value)invoke(screen, "inspectedPortValue", hit);
        check(selected.equals(samples), "The inspector displayed a value from another port");
        check(!(boolean)invoke(screen, "drawShiftPortInspector", new GuiGraphics(mc, mc.renderBuffers().bufferSource()), portX, portY),
                "The inspector appeared without holding Shift");
        capture(out.resolve("shift-inspector-gradient.png"), ctx -> {
            screen.render(ctx, -1, -1, 0);
            GraphPortInspector.render(ctx, mc.font, 800, 500, portX, portY,
                    new GraphPortInspection("Gradient", "map", true, "Draft preview", GraphRuntime.toLibraryValue(samples), null),
                    appearance(screen, "map"), null);
        });
        var nested = GraphValue.map(Map.of("position", GraphValue.list(List.of(GraphValue.number(0), GraphValue.bool(false))),
                "name", GraphValue.string("A port-specific value")));
        capture(out.resolve("shift-inspector-map.png"), ctx -> GraphPortInspector.render(ctx, mc.font, 800, 500, 760, 460,
                new GraphPortInspection("Contents", "map", false, "Connected input", nested, null), appearance(screen, "map"), null));
        capture(out.resolve("shift-inspector-color.png"), ctx -> GraphPortInspector.render(ctx, mc.font, 800, 500, 400, 200,
                new GraphPortInspection("Color", "color", true, "Draft preview", GraphValue.number(0xAA12EF34L), null),
                appearance(screen, "color"), null));
        validatePinnedInspectors(mc, out, screen, controller, pinsFile, nested);
        validateColorInput(out, screen);
        var image = new ImageSourceModal("", source -> {}, () -> {}, () -> {}).withAppearance(
                new ImageSourceModal.TextureAppearance(new com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion(
                        ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/text_input.png"), 190, 87, 0, 0, 190, 87),
                        CTColorPickerAppearance.DEFAULT.cancel(), 0xFF29231A, 0xFFCCCCCC));
        capture(out.resolve("text-input-image-modal.png"), ctx -> image.render(ctx, mc.font, 800, 500, -1, -1, CTHelpModalAppearance.COLORS));
        validateHologram(controller, out);
        validateWidgetInspectors(mc, out, screen);
    }

    // Preview both authored canvases through their real Widget ports and retained graph bubbles
    private static void validateWidgetInspectors(Minecraft mc, Path out, AdvancedContraptionControllerScreen screen) throws Exception{
        var graph = new AdvancedGraphDocument();
        for(String type : List.of("acc_display_widget", "advanced_hud_element")){
            var widget = node(type, type, 40, type.equals("acc_display_widget") ? 40 : 230);
            widget.data().putInt("WidgetWidth", 360); widget.data().putInt("WidgetHeight", 264);
            var elements = new ListTag();
            var box = new CompoundTag(); box.putString("Type", "box"); box.putInt("W", 200); box.putInt("H", 110);
            box.putInt("Color", 0xFF152C3D); elements.add(box);
            var text = new CompoundTag(); text.putString("Type", "text"); text.putString("Text", "Power Systems");
            text.putInt("X", 10); text.putInt("Y", 8); text.putInt("W", 180); text.putInt("H", 16); text.putInt("Color", -1); elements.add(text);
            var progress = new CompoundTag(); progress.putString("Type", "progress"); progress.putString("Text", "Charge 70%");
            progress.putInt("X", 10); progress.putInt("Y", 32); progress.putInt("W", 180); progress.putInt("H", 20);
            progress.putDouble("Min", 0); progress.putDouble("Max", 1); progress.putDouble("Value", .7); elements.add(progress);
            var slider = new CompoundTag(); slider.putString("Type", "slider"); slider.putDouble("Value", .77);
            slider.putInt("X", 80); slider.putInt("Y", 116); slider.putInt("W", 200); slider.putInt("H", 30);
            slider.putDouble("Min", 0); slider.putDouble("Max", 1); slider.putInt("AccentColor", 0xFFFF00E0); elements.add(slider);
            var image = new CompoundTag(); image.putString("Type", "image"); image.putString("Texture", "minecraft:textures/block/stone.png");
            image.putInt("X", 150); image.putInt("Y", 62); image.putInt("W", 72); image.putInt("H", 26); elements.add(image);
            widget.data().put("WidgetElements", elements); graph.nodes().add(widget);
        }
        screen.updateProjectionGraph(graph); set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        @SuppressWarnings("unchecked") var outputs = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeOutputs");
        var runtime = new GraphRuntime(null, true);
        for(var widget : draft.nodes()) outputs.put(widget.id() + ":widget", runtime.previewOutput(draft, widget, "widget"));
        set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
        for(var widget : draft.nodes()){
            var position = invoke(screen, "portPosition", widget, "widget", true);
            Object hit = invoke(screen, "portAt", ((Number)invoke(position, "x")).doubleValue(), ((Number)invoke(position, "y")).doubleValue());
            check(hit != null, "The Widget output is not exposed to port hit testing");
            var inspection = (GraphPortInspection)invoke(screen, "portInspection", hit);
            check("widget".equals(inspection.resolvedType()) && inspection.preview() == GraphPortInspection.Preview.WIDGET,
                    "The Widget port did not request its rich preview");
            capture(out.resolve(widget.id() + "-widget-hover.png"), ctx -> GraphPortInspector.render(ctx, mc.font, 800, 500, 450, 180,
                    inspection, appearance(screen, "widget").withTitle(Component.literal(widget.id() + ": Widget")),
                    AdvancedHudWidgetRenderer.inspectorPreview(mc.font)));
            invoke(screen, "setPortInspectorPinned", hit, true);
            capture(out.resolve(widget.id() + "-widget-pinned.png"), ctx -> screen.render(ctx, -1, -1, 0));
            @SuppressWarnings("unchecked") var bubbles = (Map<?, GraphPortInspector.Bubble>)field(screen, "pinnedInspectors");
            var bubble = bubbles.values().stream().filter(val -> val.bounds() != null
                    && ((GraphPortInspection)uncheckedField(val, "port")).preview() == GraphPortInspection.Preview.WIDGET).findFirst().orElseThrow();
            check(field(bubble, "treeArea") == null, "Widget metadata appeared as a map tree");
            validateWidgetStreamingChanges(mc, out, screen, widget, hit, bubble, inspection);
            clickLocal(screen, bubble, (GraphPortInspector.Rect)field(bubble, "modeButton"));
            capture(out.resolve(widget.id() + "-widget-simple.png"), ctx -> screen.render(ctx, -1, -1, 0));
            check(bubble.state().mode() == GraphPortInspectorPins.Mode.SIMPLE, "Widget inspector did not support Simple mode");
            invoke(screen, "setPortInspectorPinned", hit, false);
            validateWidgetCanvasParity(mc, out, screen, widget, inspection.value());
        }
        runtime.clear();
    }

    // Apply a real runtime delta to an existing hover target and pinned bubble without reopening its screen
    private static void validateWidgetStreamingChanges(Minecraft mc, Path out, AdvancedContraptionControllerScreen screen,
                                                       AdvancedGraphDocument.Node widget, Object hit,
                                                       GraphPortInspector.Bubble bubble, GraphPortInspection initial) throws Exception{
        var original = widget.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
        var state = bubble.state();
        var canvas = com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.payload(initial.value());
        var values = canvas.getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.VALUES);
        try{
            var elements = widget.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND);
            elements.getCompound(1).putString("Text", "Edited without reopening");
            var box = new CompoundTag(); box.putString("Type", "box"); box.putInt("Color", 0xFFFF00FF);
            box.putInt("X", 300); box.putInt("Y", 220); box.putInt("W", 40); box.putInt("H", 24); elements.add(box);
            CompoundTag edited = widget.data().copy();
            edited.put(com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.APPEARANCE,
                    canvas.getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.APPEARANCE));
            var updated = com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.capture(edited, values, canvas.getBoolean("Constrain"),
                    elm -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding.resolvedCopy(elm,
                            key -> values.contains(key) ? AdvancedGraphDocument.Value.fromTag(values.getCompound(key)) : null));
            applyWidgetSample(mc, screen, widget, GraphRuntime.fromLibraryValue(updated));
            var hover = (GraphPortInspection)invoke(screen, "portInspection", hit);
            check(updated.equals(hover.value()), "The hover inspector retained its previous Widget snapshot");
            capture(out.resolve(widget.id() + "-widget-live-edit.png"), ctx -> screen.render(ctx, -1, -1, 0));
            var pinned = (GraphPortInspection)field(bubble, "port");
            check(updated.equals(pinned.value()) && state.equals(bubble.state()), "The pinned Widget did not refresh in place");
        }finally{
            widget.data().put("WidgetElements", original);
            applyWidgetSample(mc, screen, widget, GraphRuntime.fromLibraryValue(initial.value()));
            capture(out.resolve(widget.id() + "-widget-live-restored.png"), ctx -> screen.render(ctx, -1, -1, 0));
        }
    }

    private static void applyWidgetSample(Minecraft mc, AdvancedContraptionControllerScreen screen,
                                           AdvancedGraphDocument.Node widget, AdvancedGraphDocument.Value val) throws Exception{
        long revision = (long)field(screen, "draftSessionRevision") + 1;
        set(screen, "draftSessionRevision", revision);
        var update = new com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal.Update<AdvancedGraphDocument.Value>(UUID.randomUUID(),
                1, false, List.of(new com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal.Event<>(1, 1, (int)revision, null,
                com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal.Channel.OUTPUT, widget.id() + ":widget", val)));
        var changes = com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeNbt.write(update, AdvancedGraphDocument.Value::toTag);
        var previous = mc.screen;
        try{
            mc.screen = screen;
            AdvancedContraptionControllerScreen.applyDraftRuntime(new com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerDraftRuntimePayload(
                    screen.getMenu().containerId, (UUID)field(screen, "draftSessionId"), revision, true, changes));
        }finally{ mc.screen = previous; }
    }

    // Compare the editor's content pixels with the Widget adapter at full and reduced scale
    private static void validateWidgetCanvasParity(Minecraft mc, Path out, AdvancedContraptionControllerScreen screen,
                                                   AdvancedGraphDocument.Node widget, GraphValue value) throws Exception{
        invoke(screen, "openHud", widget);
        var data = com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.payload(value);
        var appearance = data.getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.APPEARANCE);
        var frame = AdvancedHudWidgetRenderer.canvasFrame(appearance);
        @SuppressWarnings("unchecked") var inputSamples = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeInputs");
        var values = data.getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphWidgetValue.VALUES);
        for(String key : values.getAllKeys()) if(AdvancedGraphCatalog.inputs(widget).containsKey(key))
            inputSamples.put(widget.id() + ":" + key, AdvancedGraphDocument.Value.fromTag(values.getCompound(key)));
        set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
        int width = data.getInt("WidgetWidth") + frame.left() + frame.right();
        int height = data.getInt("WidgetHeight") + frame.top() + frame.bottom();
        var canvasType = invoke(screen, "hudCanvasBounds").getClass();
        var constructor = canvasType.getDeclaredConstructor(int.class, int.class, int.class, int.class); constructor.setAccessible(true);
        for(double scale : new double[]{1, .5}){
            int w = (int)Math.round(width * scale), h = (int)Math.round(height * scale);
            Object canvas = constructor.newInstance(90, 90, w + 20, h + 20);
            Path editor = out.resolve(widget.id() + "-editor-" + scale + ".png");
            Path inspector = out.resolve(widget.id() + "-inspector-" + scale + ".png");
            capture(editor, ctx -> {
                try{ invoke(screen, "paintHudCanvas", ctx, widget, canvas); }
                catch(Exception err){ throw new RuntimeException(err); }
            });
            capture(inspector, ctx -> check(AdvancedHudWidgetRenderer.inspectorPreview(mc.font)
                    .drawWidget(ctx, value, 100, 100, w, h), "The Widget canvas did not render"));
            try(var a = NativeImage.read(java.nio.file.Files.readAllBytes(editor));
                var b = NativeImage.read(java.nio.file.Files.readAllBytes(inspector))){
                int differences = 0;
                for(int y = 0; y < a.getHeight(); y++) for(int x = 0; x < a.getWidth(); x++)
                    if(a.getPixelRGBA(x, y) != b.getPixelRGBA(x, y)) differences++;
                check(differences == 0, widget.type() + " differs from its editor at scale " + scale + ": " + differences + " pixels");
            }
        }
        set(screen, "hudOpen", false);
    }

    private static Object uncheckedField(Object obj, String name){
        try{ return field(obj, name); }catch(Exception err){ throw new RuntimeException(err); }
    }

    // Exercise persistent state and tree navigation through the real controller input paths
    private static void validatePinnedInspectors(Minecraft mc, Path out, AdvancedContraptionControllerScreen screen,
                                                 AdvancedContraptionControllerBlockEntity controller, Path pinsFile,
                                                 GraphValue nested) throws Exception{
        var graph = ((AdvancedGraphDocument)field(screen, "draft")).copy();
        graph.nodes().add(new AdvancedGraphDocument.Node("collection", "map_create", "Inventory", 60, 240,
                AdvancedGraphNodeFactory.createDefaultData("map_create", null)));
        screen.updateProjectionGraph(graph);
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var collection = draft.nodes().get(2);
        var collectionPos = invoke(screen, "portPosition", collection, "map", true);
        Object hit = invoke(screen, "portAt", ((Number)invoke(collectionPos, "x")).doubleValue(),
                ((Number)invoke(collectionPos, "y")).doubleValue());
        check(hit != null && "map".equals(invoke(hit, "port")), "The collection inspector selected another port");
        @SuppressWarnings("unchecked") var outputs = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeOutputs");
        outputs.put("collection:map", GraphRuntime.fromLibraryValue(nested));
        outputs.put("gradient:color", AdvancedGraphDocument.Value.number(0xFF25C6D8));
        set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
        boolean dirty = (boolean)field(screen, "draftDirty");
        @SuppressWarnings("unchecked") var actions = (List<String>)invoke(screen, "portContextActions", hit);
        check(actions.contains("Port Inspector: Off"), "The port context menu did not offer a pin toggle");
        var port = invoke(screen, "portPosition", collection, "map", true);
        invoke(screen, "rightClickGraph", ((Number)invoke(port, "x")).doubleValue(), ((Number)invoke(port, "y")).doubleValue());
        invoke(screen, "runContextMenuAction", "Port Inspector: Off");
        set(screen, "contextMenu", null);
        check((boolean)invoke(screen, "isPortInspectorPinned", hit), "The port inspector did not stay pinned");
        check((boolean)field(screen, "draftDirty") == dirty, "Pinning an inspector changed the graph");
        capture(out.resolve("pinned-inspector-root.png"), ctx -> screen.render(ctx, -1, -1, 0));
        @SuppressWarnings("unchecked") var bubbles = (List<GraphPortInspector.Bubble>)field(screen, "renderedInspectors");
        check(bubbles.size() == 1, "A pinned port did not render independently of Shift");
        var bubble = bubbles.getFirst();
        check("Inventory".equals(invoke(screen, "nodeTitle", collection)), "The inspector did not use its owning node's name");
        clickTreeRow(screen, bubble, List.of("position"));
        var outline = (GraphValueOutline)field(bubble, "outline");
        var expanded = outline.snapshot(nested, 100).rows();
        check(expanded.stream().anyMatch(row -> row.path().equals(List.of("position", "0")))
                && expanded.stream().anyMatch(row -> row.path().equals(List.of("name"))),
                "Expanding a list replaced the root tree or hid its sibling entries");
        capture(out.resolve("pinned-inspector-list.png"), ctx -> screen.render(ctx, -1, -1, 0));
        double zoom = (double)field(screen, "zoom");
        var treeArea = (GraphPortInspector.Rect)field(bubble, "treeArea");
        check(screen.mouseScrolled(localX(bubble, treeArea.x() + 20), localY(bubble, treeArea.y() + 10), 0, -1),
                "The bubble did not capture tree scrolling");
        check((double)field(screen, "zoom") == zoom, "Browsing a list zoomed the graph behind it");
        clickTreeRow(screen, bubble, List.of("position"));
        check(outline.snapshot(nested, 100).rows().size() == 3, "Collapsing a list left its children visible");
        capture(out.resolve("pinned-inspector-root-return.png"), ctx -> screen.render(ctx, -1, -1, 0));

        var advancedBounds = bubble.bounds();
        clickLocal(screen, bubble, (GraphPortInspector.Rect)field(bubble, "modeButton"));
        capture(out.resolve("pinned-inspector-simple.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(bubble.state().mode() == GraphPortInspectorPins.Mode.SIMPLE && bubble.bounds().height() < advancedBounds.height() / 2,
                "Simple mode did not reduce the bubble to a compact value display");
        check(field(bubble, "treeArea") == null, "Simple mode still rendered tree controls");
        clickLocal(screen, bubble, (GraphPortInspector.Rect)field(bubble, "modeButton"));
        capture(out.resolve("pinned-inspector-advanced.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(bubble.state().mode() == GraphPortInspectorPins.Mode.ADVANCED, "The Advanced toggle did not restore the full preview");

        var originalState = bubble.state();
        var bounds = bubble.bounds();
        set(screen, "panX", 42D); set(screen, "panY", -17D);
        capture(out.resolve("pinned-inspector-panned.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(bubble.bounds().x() == bounds.x() + 42 && bubble.bounds().y() == bounds.y() - 17
                && bubble.state().equals(originalState), "Panning moved the bubble's authored graph position");
        set(screen, "panX", 0D); set(screen, "panY", 0D); set(screen, "zoom", .5D);
        capture(out.resolve("pinned-inspector-half-zoom.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(Math.abs(bubble.bounds().width() * 2 - bounds.width()) <= 1
                && Math.abs(bubble.bounds().height() * 2 - bounds.height()) <= 1,
                "The bubble did not scale with its graph");
        double dragX = localX(bubble, 45), dragY = localY(bubble, 10);
        screen.mouseClicked(dragX, dragY, 0);
        screen.mouseDragged(dragX + 30, dragY - 20, 0, 30, -20);
        screen.mouseReleased(dragX + 30, dragY - 20, 0);
        capture(out.resolve("pinned-inspector-dragged.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(Math.abs(bubble.state().x() - originalState.x() - 60) < .001
                && Math.abs(bubble.state().y() - originalState.y() + 40) < .001,
                "Dragging at half zoom did not use graph distances");
        clickLocal(screen, bubble, (GraphPortInspector.Rect)field(bubble, "modeButton"));
        var savedState = bubble.state();
        set(screen, "panX", -5000D);
        capture(out.resolve("pinned-inspector-offscreen.png"), ctx -> screen.render(ctx, -1, -1, 0));
        check(bubble.bounds() == null, "An offscreen inspector stayed attached to the viewport edge");
        set(screen, "panX", 0D); set(screen, "zoom", 1D);

        var menu = new AdvancedContraptionControllerMenu(-1237, mc.player.getInventory(), controller);
        var reopened = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Reopened Inspector"));
        reopened.configureForProjection(); reopened.init(mc, 800, 500);
        set(reopened, "inspectorPins", new GraphPortInspectorPins(pinsFile)); set(reopened, "inspectorPinsLoaded", true);
        reopened.updateProjectionGraph((AdvancedGraphDocument)field(screen, "draft"));
        set(reopened, "zoom", 1D); set(reopened, "panX", 0D); set(reopened, "panY", 0D);
        var reopenedDraft = (AdvancedGraphDocument)field(reopened, "draft");
        var reopenedNode = reopenedDraft.nodes().get(2);
        var pos = invoke(reopened, "portPosition", reopenedNode, "map", true);
        var reopenedHit = invoke(reopened, "portAt", ((Number)invoke(pos, "x")).doubleValue(), ((Number)invoke(pos, "y")).doubleValue());
        check(reopenedHit != null && (boolean)invoke(reopened, "isPortInspectorPinned", reopenedHit),
                "The pinned port did not survive a screen and preference-store restart");
        capture(out.resolve("pinned-inspector-reopened.png"), ctx -> reopened.render(ctx, -1, -1, 0));
        @SuppressWarnings("unchecked") var restoredBubbles = (List<GraphPortInspector.Bubble>)field(reopened, "renderedInspectors");
        check(restoredBubbles.size() == 1 && restoredBubbles.getFirst().state().equals(savedState),
                "The bubble's graph position or mode changed after reopening");
        clickLocal(reopened, restoredBubbles.getFirst(), (GraphPortInspector.Rect)field(restoredBubbles.getFirst(), "closeButton"));
        check(new GraphPortInspectorPins(pinsFile).ports((String)invoke(reopened, "portInspectorScope")).isEmpty(),
                "Unpinning did not persist");
        set(screen, "inspectorPins", new GraphPortInspectorPins(pinsFile));

        set(screen, "zoom", .4D);
        var node = draft.nodes().getFirst();
        double nodeX = (int)invoke(screen, "screenX", node.x()) + 15D;
        double nodeY = (int)invoke(screen, "screenY", node.y()) + 15D;
        var primary = invoke(screen, "hoveredInspectorPort", nodeX, nodeY);
        check(primary != null && "color".equals(invoke(primary, "port")) && (boolean)invoke(primary, "output"),
                "A zoomed-out node did not inspect its primary data output");
        capture(out.resolve("zoomed-out-inspector.png"), ctx -> {
            screen.render(ctx, -1, -1, 0);
            try{
                var inspected = (GraphPortInspection)invoke(screen, "portInspection", primary);
                GraphPortInspector.render(ctx, mc.font, 800, 500, (int)nodeX, (int)nodeY, inspected, appearance(screen, "color"), null);
            }catch(Exception err){ throw new RuntimeException(err); }
        });
        set(screen, "zoom", 1D);
    }

    // Expand or collapse one visible row without navigating away from its siblings
    private static void clickTreeRow(AdvancedContraptionControllerScreen screen, GraphPortInspector.Bubble bubble, List<String> path) throws Exception{
        for(Object row : (List<?>)field(bubble, "rows")){
            if(!path.equals(invoke(row, "path"))) continue;
            clickLocal(screen, bubble, (GraphPortInspector.Rect)invoke(row, "bounds")); return;
        }
        throw new AssertionError("Missing tree row " + path);
    }

    // Send pointer input through the same scaled coordinates as the graph bubble
    private static void clickLocal(AdvancedContraptionControllerScreen screen, GraphPortInspector.Bubble bubble, GraphPortInspector.Rect rect) throws Exception{
        double x = localX(bubble, rect.x() + rect.width() / 2.0), y = localY(bubble, rect.y() + rect.height() / 2.0);
        check(screen.mouseClicked(x, y, 0), "The inspector click reached the graph"); screen.mouseReleased(x, y, 0);
    }

    // Apply the latest rendered horizontal graph transform to a local test point
    private static double localX(GraphPortInspector.Bubble bubble, double x) throws Exception{
        return (double)field(bubble, "originX") + x * (double)field(bubble, "scale");
    }

    // Apply the latest rendered vertical graph transform to a local test point
    private static double localY(GraphPortInspector.Bubble bubble, double y) throws Exception{
        return (double)field(bubble, "originY") + y * (double)field(bubble, "scale");
    }

    // Capture a wired color input with its semantic label, hex value and swatch
    private static void validateColorInput(Path out, AdvancedContraptionControllerScreen screen) throws Exception{
        var graph = ((AdvancedGraphDocument)field(screen, "draft")).copy();
        graph.edges().add(new AdvancedGraphDocument.Edge("color_wire", "gradient", "color", "color", "value"));
        screen.updateProjectionGraph(graph);
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var color = draft.nodes().get(1);
        @SuppressWarnings("unchecked") var inputs = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeInputs");
        inputs.put("color:value", AdvancedGraphDocument.Value.number(0xAA12EF34L));
        set(screen, "draftRuntimeRevision", field(screen, "draftSessionRevision"));
        check("Color".equals(invoke(screen, "inputDisplayLabel", color, "value")), "The color input still uses the Value label");
        var pos = invoke(screen, "portPosition", color, "value", false);
        var hit = invoke(screen, "portAt", ((Number)invoke(pos, "x")).doubleValue(), ((Number)invoke(pos, "y")).doubleValue());
        var inspection = (GraphPortInspection)invoke(screen, "portInspection", hit);
        check(inspection.resolvedType().equals("color") && inspection.summary().startsWith("#AA12EF34"),
                "The wired color input exposed its numeric storage type");
        capture(out.resolve("wired-color-input.png"), ctx -> screen.render(ctx, -1, -1, 0));
    }

    // Resolve the exact chrome and colors used by the current controller theme
    private static GraphPortInspector.Appearance appearance(AdvancedContraptionControllerScreen screen, String type){
        try{ return (GraphPortInspector.Appearance)invoke(screen, "portInspectorAppearance", type); }
        catch(Exception err){ throw new RuntimeException(err); }
    }

    // Draw an unbound canvas above the controller and reject it after a target is assigned
    private static void validateHologram(AdvancedContraptionControllerBlockEntity controller, Path out) throws Exception{
        var widget = node("hologram", "acc_hologram_widget", 0, 0);
        var elements = new ListTag(); var box = new CompoundTag();
        box.putString("Type", "box"); box.putBoolean("Visible", true);
        box.putInt("X", 0); box.putInt("Y", 0);
        box.putInt("W", widget.data().getInt("WidgetWidth")); box.putInt("H", widget.data().getInt("WidgetHeight"));
        box.putInt("Color", 0xFF25C6D8); elements.add(box); widget.data().put("WidgetElements", elements);
        var layout = PresentationLayout.write(List.of(new PresentationLayout.Element(widget.id(), widget.type(), "", widget.data())));
        layout.putUUID("Generation", UUID.randomUUID()); controller.applyClientHudLayout(layout);
        check(AdvancedControllerHolograms.hasWidgets(controller), "An unbound ACC hologram was culled");
        capture(out.resolve("untargeted-hologram.png"), ctx -> {
            ctx.fill(400, 320, 480, 400, 0xFF777777);
            ctx.pose().pushPose();
            try{
                ctx.pose().translate(400, 400, 10); ctx.pose().scale(80, -80, -80);
                AdvancedControllerHolograms.render(controller, ctx.pose(), ctx.bufferSource());
            }finally{ ctx.pose().popPose(); }
        });
        var target = new CompoundTag(); target.putLong("BlockPos", BlockPos.ZERO.asLong()); widget.data().put("TargetData", target);
        layout = PresentationLayout.write(List.of(new PresentationLayout.Element(widget.id(), widget.type(), "", widget.data())));
        layout.putUUID("Generation", UUID.randomUUID()); controller.applyClientHudLayout(layout);
        check(!AdvancedControllerHolograms.hasWidgets(controller), "A targeted hologram also rendered above the ACC");
    }

    // Preview an embedded image through the same value bridge used by the hovered image port
    static void validateImage(Minecraft mc, Path out, AdvancedGraphDocument.Value image) throws Exception{
        capture(out.resolve("shift-inspector-image.png"), ctx -> GraphPortInspector.render(ctx, mc.font, 800, 500, 400, 220,
                new GraphPortInspection("Image", "image", true, "Draft preview", GraphRuntime.toLibraryValue(image), null),
                CTHelpModalAppearance.COLORS, (graphics, val, x, y, w, h) -> AdvancedHudImageClient.draw(graphics,
                        GraphRuntime.fromLibraryValue(val).payload(), x, y, w, h)));
    }

    // Capture GUI and projected surfaces through the production framebuffer renderer
    private static void capture(Path path, Consumer<GuiGraphics> draw) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "port_inspector_validation"))){
            renderer.render(1200, 750, 800, 500, draw);
            RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(1200, 750, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(path);
            }
        }
    }

    // Read private UI state only inside the client validation harness
    private static Object field(Object obj, String name) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(obj);
    }

    // Seed runtime samples without sending validation data over the network
    private static void set(Object obj, String name, Object val) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); field.set(obj, val);
    }

    // Exercise production hit testing and modal callbacks without exposing them as addon APIs
    private static Object invoke(Object obj, String name, Object... args) throws Exception{
        for(var method : obj.getClass().getDeclaredMethods()) if(method.getName().equals(name) && method.getParameterCount() == args.length){
            method.setAccessible(true); return method.invoke(obj, args);
        }
        throw new NoSuchMethodException(name);
    }

    // Use the editor's own defaults for every validation node
    private static AdvancedGraphDocument.Node node(String id, String type, double x, double y){
        return new AdvancedGraphDocument.Node(id, type, "", x, y, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }

    // Stop validation at the first incorrect interaction or projection
    private static void check(boolean condition, String msg){ if(!condition) throw new AssertionError(msg); }
}
