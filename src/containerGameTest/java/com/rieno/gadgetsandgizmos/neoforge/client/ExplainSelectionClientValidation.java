package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.ExplainSelectionGameTests;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphExplanation;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.PlainTextModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.GraphCalloutBubbles;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphExplanation;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
import java.util.stream.Collectors;

// Render the actual generated explanation and exercise the modal without opening an ACC runtime
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class ExplainSelectionClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.explainSelectionValidation");
    private static boolean complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        complete = true;
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path output = Path.of(OUTPUT);
        try{
            Files.createDirectories(output);
            validate(output);
            Files.writeString(output.resolve("result.txt"), "PASSED painted frame, target display names, profiler descriptions, copy, selection, scrolling, resizing, Show In Graph, graph-space zoom scaling, fixed node attachments and right-click close\n");
        }catch(Throwable ex){
            try{
                var trace = new StringWriter();
                ex.printStackTrace(new PrintWriter(trace));
                Files.writeString(output.resolve("result.txt"), "FAILED\n" + trace);
            }catch(Exception ignored){}
        }finally{ mc.stop(); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void validate(Path output) throws Exception{
        var mc = Minecraft.getInstance();
        var graph = ExplainSelectionGameTests.throttleGraph();
        GraphExplanation.TextResolver text = (key, fallback, args) -> Component.translatableWithFallback(key, fallback, args).getString();
        String explanation = new GraphExplanation(text).explainMarkdown(AdvancedGraphExplanation.analyse(graph, graph,
                graph.nodes().stream().map(AdvancedGraphDocument.Node::id).collect(Collectors.toSet()), text));
        if(!explanation.contains("throttle") || !explanation.contains("**Map Range**") || !explanation.contains("0–15"))
            throw new AssertionError("Translated explanation lost configured behaviour: " + explanation);
        Files.writeString(output.resolve("explanation.txt"), explanation);
        var modal = new PlainTextModal(Component.translatable("createthrusters.advanced_controller.explain.title"),
                explanation, Component.translatable("createthrusters.advanced_controller.explain.copy"))
                .withAppearance(CTHelpModalAppearance.EXPLANATION).withMarkdown();
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "explain_validation"))){
            render(renderer, modal, output.resolve("explanation.png"), 700, 360);
            copy(modal, explanation, false);
            var transform = CTHelpModalAppearance.EXPLANATION.frame().transform(700, 360);
            modal.mouseClicked(700, 360, transform.screenX(10), transform.screenY(45), GLFW.GLFW_MOUSE_BUTTON_LEFT, () -> {});
            modal.mouseDragged(700, 360, transform.screenY(65));
            modal.mouseReleased();
            render(renderer, modal, output.resolve("selected.png"), 700, 360);
            copy(modal, explanation, true);
            modal.keyPressed(GLFW.GLFW_KEY_HOME, () -> {});
            render(renderer, modal, output.resolve("compact.png"), 320, 240);
            var longModal = new PlainTextModal(Component.literal("Explain Selection"), (explanation + "\n\n").repeat(40), Component.literal("Copy"))
                    .withAppearance(CTHelpModalAppearance.EXPLANATION).withMarkdown();
            render(renderer, longModal, output.resolve("long-top.png"), 320, 240);
            longModal.mouseScrolled(-8);
            render(renderer, longModal, output.resolve("long-scrolled.png"), 320, 240);
            if(java.util.Arrays.equals(Files.readAllBytes(output.resolve("long-top.png")), Files.readAllBytes(output.resolve("long-scrolled.png"))))
                throw new AssertionError("Scrolling did not change the visible text");
            var linker = ExplainSelectionGameTests.linkerGraph();
            var before = linker.toTag().copy();
            var linkerAnalysis = AdvancedGraphExplanation.analyse(linker, linker,
                    linker.nodes().stream().map(AdvancedGraphDocument.Node::id).collect(Collectors.toSet()), text);
            var explanationText = new GraphExplanation(text);
            String linkerText = explanationText.explainMarkdown(linkerAnalysis);
            if(linkerText.contains("ct_linker") || linkerText.contains("ct linker")
                    || !linkerText.contains("Redstone Lamp West Linker Plane"))
                throw new AssertionError("The linker explanation exposed an internal target: " + linkerText);
            var bubbles = new GraphCalloutBubbles(Component.translatable("createthrusters.advanced_controller.explain.close_bubbles"));
            var linkerModal = new PlainTextModal(Component.translatable("createthrusters.advanced_controller.explain.title"),
                    linkerText, Component.translatable("createthrusters.advanced_controller.explain.copy"))
                    .withAppearance(CTHelpModalAppearance.EXPLANATION).withMarkdown()
                    .withAction(Component.translatable("createthrusters.advanced_controller.explain.show_in_graph"),
                            () -> bubbles.setEntries(explanationText.explainSteps(linkerAnalysis).stream()
                                    .map(step -> new GraphCalloutBubbles.Entry(step.nodeId(), step.markdown())).toList()));
            Files.writeString(output.resolve("linker-explanation.txt"), linkerText);
            render(renderer, linkerModal, output.resolve("linker-explanation.png"), 700, 520);
            copy(linkerModal, linkerText, false);
            var copyRegion = CTHelpModalAppearance.EXPLANATION.copy();
            linkerModal.mouseClicked(700, 360, transform.screenX(copyRegion.x() - 3 - copyRegion.width() / 2.0),
                    transform.screenY(copyRegion.y() + copyRegion.height() / 2.0), GLFW.GLFW_MOUSE_BUTTON_LEFT, () -> {});
            if(bubbles.isEmpty()) throw new AssertionError("Show In Graph did not create its node bubbles");
            String firstStep = explanationText.explainSteps(linkerAnalysis).getFirst().markdown();
            String plainStep = com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument.parse(firstStep).nodes().stream()
                    .map(com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument.Node::plainText)
                    .collect(Collectors.joining("\n"));
            int bubbleHeight = mc.font.split(Component.literal(plainStep), 234).size() * (mc.font.lineHeight + 2) + 12;
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles.png"), 0, 1);
            verifyBubbleBounds(output.resolve("graph-bubbles.png"), 0, 1, bubbleHeight);
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-panned.png"), 30, 0.8);
            verifyBubbleBounds(output.resolve("graph-bubbles-panned.png"), 30, 0.8, bubbleHeight);
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-zoomed.png"), 0, 2);
            verifyBubbleBounds(output.resolve("graph-bubbles-zoomed.png"), 0, 2, bubbleHeight);
            verifyTextScale(output.resolve("graph-bubbles.png"), output.resolve("graph-bubbles-zoomed.png"), bubbleHeight);
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-clipped.png"), -80, 1);
            try(var clipped = NativeImage.read(Files.readAllBytes(output.resolve("graph-bubbles-clipped.png")))){
                if(clipped.getPixelRGBA(0, 250 - bubbleHeight - 10 - 80 + 4) == 0xFF404040)
                    throw new AssertionError("Panning clamped the bubble to the viewport edge");
            }
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-small.png"), 0, 0.35);
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-panned.png"), 30, 0.8);
            var bubbleView = new GraphViewport(0, 0, 30, 30, 0.8);
            double clickX = bubbleView.screenX(170), clickY = bubbleView.screenY(235);
            if(!bubbles.mouseClicked(clickX, clickY, GLFW.GLFW_MOUSE_BUTTON_RIGHT))
                throw new AssertionError("Right-clicking a scaled bubble was not consumed");
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-menu.png"), 30, 0.8);
            bubbles.mouseClicked(clickX + 10, clickY + 10, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if(!bubbles.isEmpty()) throw new AssertionError("The scaled close-bubbles action left annotations visible");
            bubbles.setEntries(explanationText.explainSteps(linkerAnalysis).stream()
                    .map(step -> new GraphCalloutBubbles.Entry(step.nodeId(), step.markdown())).toList());
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles.png"), 0, 1);
            if(!bubbles.mouseClicked(170, 235, GLFW.GLFW_MOUSE_BUTTON_RIGHT))
                throw new AssertionError("Right-clicking a bubble was not consumed");
            renderBubbles(renderer, bubbles, output.resolve("graph-bubbles-menu.png"), 0, 1);
            bubbles.mouseClicked(180, 245, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if(!bubbles.isEmpty()) throw new AssertionError("The close-bubbles button left annotations visible");
            if(!before.equals(linker.toTag())) throw new AssertionError("Showing graph bubbles changed saved node data");
            var profiler = new AdvancedGraphDocument();
            for(String type : java.util.List.of("profiler_mspt", "profiler_fps", "profiler_tps", "profiler_frametime")){
                profiler.nodes().add(new AdvancedGraphDocument.Node(type, type, "", 0, 0,
                        com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory.createDefaultData(type,
                                com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory.Context.EMPTY)));
            }
            String profilerText = explanationText.explainMarkdown(AdvancedGraphExplanation.analyse(profiler, profiler,
                    profiler.nodes().stream().map(AdvancedGraphDocument.Node::id).toList(), text));
            if(!profilerText.contains("milliseconds per tick") || !profilerText.contains("frames per second")
                    || !profilerText.contains("ticks per second") || !profilerText.contains("frame time in milliseconds")
                    || profilerText.contains("configured output"))
                throw new AssertionError("Profiler translations lost their output meanings: " + profilerText);
            Files.writeString(output.resolve("profiler-explanation.txt"), profilerText);
            boolean[] clickedClose = {false};
            linkerModal.mouseClicked(700, 360, transform.screenX(272), transform.screenY(7),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, () -> clickedClose[0] = true);
            if(!clickedClose[0]) throw new AssertionError("The painted close control did not close the modal");
            boolean[] closed = {false};
            modal.keyPressed(GLFW.GLFW_KEY_ESCAPE, () -> closed[0] = true);
            if(!closed[0]) throw new AssertionError("Escape did not close the modal");
        }
    }

    private static void render(FramebufferGuiRenderer renderer, PlainTextModal modal, Path path, int width, int height) throws Exception{
        renderer.render(width, height, width, height,
                graphics -> modal.render(graphics, Minecraft.getInstance().font, width, height, 0, 0, CTHelpModalAppearance.COLORS));
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
        try(var image = new NativeImage(width, height, false)){
            image.downloadTexture(0, false);
            image.flipY();
            image.writeToFile(path);
        }
    }

    // Draw nodes and callouts with identical authored coordinates and viewport transforms
    private static void renderBubbles(FramebufferGuiRenderer renderer, GraphCalloutBubbles bubbles, Path path,
            int pan, double zoom) throws Exception{
        var viewport = new GraphViewport(0, 0, pan, pan, zoom);
        renderer.render(700, 520, 700, 520, graphics -> {
            var font = Minecraft.getInstance().font;
            graphics.fill(0, 0, 700, 520, 0xFF202020);
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(viewport.originX() + viewport.panX(), viewport.originY() + viewport.panY(), 0);
                graphics.pose().scale((float)zoom, (float)zoom, 1);
                for(int idx = 0; idx < 2; idx++){
                    int x = 80 + idx * 300;
                    graphics.fill(x, 250, x + 180, 315, 0xFF303030);
                    graphics.renderOutline(x, 250, 180, 65, 0xFFE5BA64);
                    graphics.drawString(font, idx == 0 ? "Configured Key Input" : "Set Data", x + 8, 258, 0xFFFFFFFF, false);
                }
            }finally{ graphics.pose().popPose(); }
            bubbles.render(graphics, font, new GraphCalloutBubbles.Rect(0, 0, 700, 520), viewport, id ->
                    new GraphCalloutBubbles.GraphAnchor(id.equals("key") ? 80 : 380, 250, 180),
                    CTHelpModalAppearance.COLORS, -1000, -1000);
        });
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
        try(var image = new NativeImage(700, 520, false)){
            image.downloadTexture(0, false);
            image.flipY();
            image.writeToFile(path);
        }
    }

    // Measure rendered borders so fixed-pixel bubbles and screen-space repositioning fail validation
    private static void verifyBubbleBounds(Path path, int pan, double zoom, int height) throws Exception{
        int left = (int)Math.round(pan + 45 * zoom);
        int right = (int)Math.round(pan + 295 * zoom);
        int top = (int)Math.round(pan + (250 - height - 10) * zoom);
        int bottom = (int)Math.round(pan + 240 * zoom);
        int firstX = Integer.MAX_VALUE, lastX = Integer.MIN_VALUE;
        int firstY = Integer.MAX_VALUE, lastY = Integer.MIN_VALUE;
        try(var image = NativeImage.read(Files.readAllBytes(path))){
            for(int y = Math.max(0, top - 2); y < Math.min(image.getHeight(), bottom + 2); y++){
                for(int x = Math.max(0, left - 2); x < Math.min(image.getWidth(), right + 2); x++){
                    if(image.getPixelRGBA(x, y) != 0xFF404040) continue;
                    firstX = Math.min(firstX, x); lastX = Math.max(lastX, x);
                    firstY = Math.min(firstY, y); lastY = Math.max(lastY, y);
                }
            }
        }
        if(Math.abs(firstX - (double)left) > 2 || Math.abs(lastX + 1.0 - right) > 2
                || Math.abs(firstY - (double)top) > 2 || Math.abs(lastY + 1.0 - bottom) > 2)
            throw new AssertionError("Bubble borders did not scale with the graph at zoom " + zoom + ": "
                    + firstX + "," + firstY + " to " + lastX + "," + lastY);
    }

    // Verify that glyphs scale with the panel rather than remaining at the screen font size
    private static void verifyTextScale(Path normal, Path enlarged, int height) throws Exception{
        int first = glyphHeight(normal, 1, height), second = glyphHeight(enlarged, 2, height);
        if(first < 1 || Math.abs(second - first * 2) > 2)
            throw new AssertionError("Bubble text did not share graph zoom: " + first + " to " + second);
    }

    // Measure the first line's white glyphs inside its scaled content padding
    private static int glyphHeight(Path path, int zoom, int height) throws Exception{
        int top = (250 - height - 10 + 6) * zoom;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        try(var image = NativeImage.read(Files.readAllBytes(path))){
            for(int y = top; y < top + 11 * zoom; y++){
                for(int x = 53 * zoom; x < 287 * zoom; x++){
                    if(image.getPixelRGBA(x, y) != 0xFFFFFFFF) continue;
                    min = Math.min(min, y); max = Math.max(max, y);
                }
            }
        }
        return max < min ? 0 : max - min + 1;
    }

    // Permit temporary clipboard contention from the desktop during the native client test
    private static void copy(PlainTextModal modal, String expected, boolean selection) throws Exception{
        var transform = CTHelpModalAppearance.EXPLANATION.frame().transform(700, 360);
        var copy = CTHelpModalAppearance.EXPLANATION.copy();
        for(int attempt = 0; attempt < 10; attempt++){
            modal.mouseClicked(700, 360, transform.screenX(copy.x() + copy.width() / 2.0),
                    transform.screenY(copy.y() + copy.height() / 2.0), GLFW.GLFW_MOUSE_BUTTON_LEFT, () -> {});
            Thread.sleep(50);
            String val = Minecraft.getInstance().keyboardHandler.getClipboard();
            if(selection ? !val.isBlank() && val.length() < expected.length() : expected.equals(val)) return;
        }
        throw new AssertionError("The native clipboard did not return the copied explanation");
    }
}

