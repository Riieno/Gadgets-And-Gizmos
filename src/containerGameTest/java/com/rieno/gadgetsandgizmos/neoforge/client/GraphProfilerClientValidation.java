package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.GraphProfilerPanel;
import com.rieno.gadgetsandgizmos.lib.graph.GraphProfileNbt;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphProfilePayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

// Exercise the profiler's actual controls and render compiled runtime measurements
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class GraphProfilerClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.graphProfilerValidation");
    private static boolean complete;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        complete = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path out = Path.of(OUTPUT);
        try{
            Files.createDirectories(out);
            validate(mc, out);
            Files.writeString(out.resolve("result.txt"), "PASSED\nConfig gate, toolbar toggle, compiled timings, mode selection, stale snapshots, SCM exclusion and cleanup.");
        }catch(Throwable err){
            var trace = new java.io.StringWriter(); err.printStackTrace(new java.io.PrintWriter(trace));
            try{ Files.writeString(out.resolve("result.txt"), "FAILED\n" + trace); }catch(Exception ignored){}
        }finally{ mc.stop(); }
    }

    private static void validate(Minecraft mc, Path out) throws Exception{
        boolean enabled = CTConfigs.SERVER.nodeGraphProfiler.get();
        var prev = mc.screen;
        var controller = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO,
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1240, mc.player.getInventory(), controller);
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Graph Profiler"));
        var runtime = new GraphRuntime(controller, true);
        try{
            CTConfigs.SERVER.nodeGraphProfiler.set(false);
            screen.configureForProjection(); screen.init(mc, 800, 500);
            var graph = new AdvancedGraphDocument();
            graph.nodes().add(node("tick", "event_tick", -100, 30));
            graph.nodes().add(node("counter", "count", 100, 30));
            graph.nodes().add(node("idle", "constant_number", -100, 230));
            graph.edges().add(new AdvancedGraphDocument.Edge("pulse", "tick", "exec", "counter", "exec"));
            screen.updateProjectionGraph(graph);
            set(screen, "leftSidebarCollapsed", true);
            mc.screen = screen;
            var button = (Button)field(screen, "graphProfilerButton");
            check(!button.visible, "The default-disabled feature exposed a toolbar button");
            CTConfigs.SERVER.nodeGraphProfiler.set(true);
            capture(out.resolve("disabled.png"), ctx -> screen.render(ctx, -1, -1, 0));
            check(button.visible, "Server enabling did not reveal the stopwatch");
            button.onPress();
            check((boolean)invoke(screen, "graphProfilerActive"), "The stopwatch did not open the sidebar");
            runtime.retainGraphProfiling();
            for(int idx = 0; idx < 6; idx++) runtime.tick(graph, false);
            var profile = runtime.graphProfile();
            UUID id = (UUID)field(screen, "draftSessionId");
            long revision = (long)field(screen, "draftSessionRevision");
            AdvancedContraptionControllerScreen.applyGraphProfile(new AdvancedControllerGraphProfilePayload(
                    menu.containerId, id, revision, false, GraphProfileNbt.write(profile)));
            var panel = (GraphProfilerPanel)field(screen, "graphProfilerPanel");
            check(panel.profile().equals(profile) && panel.label("counter").endsWith("\u03bcs"), "Runtime timings lost microsecond units");
            capture(out.resolve("runtime.png"), ctx -> screen.render(ctx, -1, -1, 0));
            int right = (int)invoke(screen, "graphRight");
            screen.mouseClicked(right + (800 - right) * 0.75, 63, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            check(panel.simulated() && panel.profile().ticks() == 0, "Simulation selection retained runtime metrics");
            AdvancedContraptionControllerScreen.applyGraphProfile(new AdvancedControllerGraphProfilePayload(
                    menu.containerId, id, revision, false, GraphProfileNbt.write(profile)));
            check(panel.profile().ticks() == 0, "A stale runtime snapshot crossed the mode boundary");
            AdvancedContraptionControllerScreen.applyGraphProfile(new AdvancedControllerGraphProfilePayload(
                    menu.containerId, id, revision, true, GraphProfileNbt.write(profile)));
            capture(out.resolve("simulated.png"), ctx -> screen.render(ctx, -1, -1, 0));
            check(panel.profile().equals(profile), "Simulation metrics were not accepted");
            set(screen, "scmGraphOverview", true); invoke(screen, "tickGraphProfiler");
            check(!(boolean)invoke(screen, "graphProfilerActive") && !(boolean)field(screen, "graphProfilerWatching"),
                    "The SCM tab retained its profiling subscription");
            set(screen, "scmGraphOverview", false); invoke(screen, "tickGraphProfiler");
            button.onPress();
            check(!(boolean)invoke(screen, "graphProfilerActive") && !(boolean)field(screen, "graphProfilerWatching"),
                    "Closing the sidebar retained on-node timings or its subscription");
            CTConfigs.SERVER.nodeGraphProfiler.set(false);
            capture(out.resolve("server-disabled.png"), ctx -> screen.render(ctx, -1, -1, 0));
            check(!button.visible, "Disabling the server gate left the stopwatch visible");
        }finally{
            runtime.releaseGraphProfiling(); runtime.clear();
            CTConfigs.SERVER.nodeGraphProfiler.set(enabled);
            mc.screen = prev;
        }
    }

    private static AdvancedGraphDocument.Node node(String id, String type, double x, double y){
        return new AdvancedGraphDocument.Node(id, type, id, x, y,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static void capture(Path path, Consumer<GuiGraphics> draw) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "graph_profiler_validation"))){
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

    private static void set(Object obj, String name, Object val) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); field.set(obj, val);
    }

    private static Object invoke(Object obj, String name) throws Exception{
        var method = obj.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(obj);
    }

    private static void check(boolean condition, String msg){ if(!condition) throw new AssertionError(msg); }
}
