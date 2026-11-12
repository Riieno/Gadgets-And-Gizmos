package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.CameraBlock;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.rieno.gadgetsandgizmos.content.CameraBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

// Exercise real Sodium captures, off-screen pauses and renderer reuse in an isolated world
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CameraRuntimeClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.cameraRuntimeValidation");
    private static final boolean LODS = Boolean.getBoolean("gadgetsngizmos.cameraLodValidation");
    private static final BlockPos CAMERA = new BlockPos(0, -58, 0);
    // Retain only the validation world's fixture and pause cycle
    private static boolean hidden, installed, complete, showing;
    private static volatile boolean prepared;
    private static volatile Throwable serverFailure;
    private static long started, changed, lastRendered, worstResumeMillis;
    private static int stage, cycles;
    private static ViewReference source;
    private static Object retainedCapture, retainedRenderer;
    private static AdvancedContraptionControllerScreen editor;
    private static long editorPixels, observedFrame = -1;
    private static int nearFrames;

    // Alternate visible and paused captures for longer than the old five-second expiry
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; started = System.nanoTime(); }
        try{
            if(System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Camera runtime validation timed out");
            if(serverFailure != null) throw new AssertionError("Camera fixture failed", serverFailure);
            if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            mc.options.pauseOnLostFocus = false;
            if(stage < 4) mc.setScreen(null);
            if(!installed){
                installed = true;
                if(LODS){
                    if(com.rieno.gadgetsandgizmos.config.CTConfigs.CLIENT.cameraFeedLods.get())
                        throw new AssertionError("Camera LODs must default to disabled");
                    com.rieno.gadgetsandgizmos.lib.client.view.ViewLodCompat.setEnabled(() -> true);
                }
                install(mc); return;
            }
            if(!prepared || !(mc.level.getBlockEntity(CAMERA) instanceof CameraBlockEntity camera)) return;
            if(source == null){ source = ViewReference.of(camera); showing = true; changed = System.nanoTime(); }
            Object capture = captures().get(source);
            if(capture == null && stage > 0) throw new AssertionError("A paused feed expired its cached scene");
            if(capture == null || !((Boolean)field(capture, "ready"))) return;
            long now = System.nanoTime();
            long frame = (Long)field(capture, "rendered");
            if(showing && frame != observedFrame){ nearFrames++; observedFrame = frame; }
            switch(stage){
                case 0 -> {
                    if(now - changed < 2_000_000_000L) return;
                    retainedCapture = capture;
                    retainedRenderer = field(capture, "renderer");
                    if(retainedRenderer == null) throw new AssertionError("No capture renderer was initialized");
                    if(cycles == 0){ captureImage(capture); editorImage("world-before-editor.png"); }
                    showing = false; stage = 1; changed = now;
                }
                case 1 -> {
                    if(now - changed < 500_000_000L) return;
                    lastRendered = (Long)field(capture, "rendered");
                    stage = 2;
                }
                case 2 -> {
                    if(capture != retainedCapture || field(capture, "renderer") != retainedRenderer)
                        throw new AssertionError("An occluded feed discarded its renderer");
                    if((Long)field(capture, "rendered") != lastRendered)
                        throw new AssertionError("A paused feed continued capturing");
                    if(now - changed < 6_500_000_000L) return;
                    showing = true; stage = 3; changed = now;
                }
                case 3 -> {
                    if(capture != retainedCapture || field(capture, "renderer") != retainedRenderer)
                        throw new AssertionError("Returning to the feed allocated another renderer");
                    if((Long)field(capture, "rendered") == lastRendered) return;
                    if(now - changed > 1_000_000_000L) throw new AssertionError("Resuming a retained feed stalled for over one second");
                    worstResumeMillis = Math.max(worstResumeMillis, (now - changed) / 1_000_000L);
                    if(++cycles == 2){
                        if(LODS){
                            var type = com.rieno.gadgetsandgizmos.lib.client.view.ViewLodCompat.class;
                            var failure = type.getDeclaredField("lastFailure"); failure.setAccessible(true);
                            var passes = type.getDeclaredField("renderedPasses"); passes.setAccessible(true);
                            if(!failure.get(null).equals("")) throw new AssertionError("LOD integration failed: " + failure.get(null));
                            if(net.neoforged.fml.ModList.get().isLoaded("voxy")){
                                var rendererType = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
                                if(rendererType.getMethod("voxy$getRenderSystem").invoke(retainedRenderer) != null)
                                    throw new AssertionError("A camera allocated another Voxy world and geometry cache");
                            }
                            if(passes.getLong(null) == 0){
                                var provider = type.getDeclaredField("distantHorizons"); provider.setAccessible(true);
                                Object dh = provider.get(null); String reason = "";
                                if(dh != null){
                                    var scenes = (Map<?, ?>)field(dh, "scenes");
                                    for(Object scene : scenes.values()){
                                        Object params = field(scene, "params");
                                        reason += params.getClass().getMethod("getValidationErrorMessage").invoke(params);
                                    }
                                }
                                throw new AssertionError("No camera LOD passes were rendered; frame=" + field(capture, "lodFrame") + "; DH=" + dh + "; " + reason);
                            }
                            captureImage(capture);
                        }
                        if(LODS && nearFrames < 20) throw new AssertionError("LOD work throttled near-scene updates: " + nearFrames);
                        CameraWidgetSurfaceValidation.start(source);
                        showing = false; stage = 7; changed = now;
                    }else{ stage = 0; changed = now; }
                }
                case 7 -> {
                    if(!CameraWidgetSurfaceValidation.check(Path.of(OUTPUT))) return;
                    CameraWidgetSurfaceValidation.stop();
                    showing = true; stage = 4; changed = now;
                }
                case 4 -> {
                    var ctrl = new com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity(
                            CAMERA.above(), com.rieno.gadgetsandgizmos.registry.CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
                    ctrl.setLevel(mc.level);
                    var menu = new com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu(-1234, mc.player.getInventory(), ctrl){
                        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){ return true; }
                    };
                    editor = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), net.minecraft.network.chat.Component.literal("LOD editor validation"));
                    editor.configureForProjection(); mc.setScreen(editor);
                    var graph = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument();
                    graph.nodes().add(new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Node(
                            "number", "constant_number", "Live editor", 70, 70,
                            com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory.createDefaultData("constant_number", null)));
                    editor.updateProjectionGraph(graph);
                    stage = 5; changed = now;
                }
                case 5 -> {
                    if(now - changed < 2_000_000_000L) return;
                    if(mc.screen != editor) throw new AssertionError("The editor fixture closed before input validation");
                    editorPixels = editorImage("editor-before.png");
                    var bounds = invoke(editor, "toolsToolbarButtonBounds");
                    editor.mouseClicked((int)invoke(bounds, "x") + 3D, (int)invoke(bounds, "y") + 3D, 0);
                    if(!(Boolean)field(editor, "toolsMenuOpen")) throw new AssertionError("The ACC editor stopped accepting clicks during LOD captures");
                    stage = 6; changed = now;
                }
                case 6 -> {
                    if(now - changed < 500_000_000L) return;
                    if(mc.screen != editor) throw new AssertionError("The editor fixture closed before redraw validation");
                    if(editorImage("editor-after.png") == editorPixels) throw new AssertionError("The ACC editor is a static image during LOD captures");
                    editor.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    if((Boolean)field(editor, "toolsMenuOpen")) throw new AssertionError("The ACC editor stopped accepting keyboard input");
                    mc.setScreen(null);
                    com.rieno.gadgetsandgizmos.lib.client.view.ViewLodCompat.setEnabled(() -> false);
                    com.rieno.gadgetsandgizmos.lib.client.view.ViewLodCompat.isEnabled();
                    finish("PASSED live near terrain and foreground occlusion, world display/hologram camera widgets, independent LOD refresh, two hidden pauses, renderer reuse, ACC mouse/keyboard and redraw; near updates "
                            + nearFrames + "; worst resume " + worstResumeMillis + " ms");
                }
            }
        }catch(Throwable err){ err.printStackTrace(); finish("FAILED " + err); }
    }

    // Retain the actual captured terrain image for visual inspection
    private static void captureImage(Object capture) throws Exception{
        var target = (TextureTarget)field(capture, "target");
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
        try(var image = new NativeImage(target.width, target.height, false)){
            image.downloadTexture(0, false);
            image.flipY();
            image.writeToFile(Path.of(OUTPUT).resolve("feed.png"));
            int lit = 0;
            for(int y = 0; y < target.height; y++) for(int x = 0; x < target.width; x++){
                int pixel = image.getPixelRGBA(x, y);
                if((pixel & 255) > 20 || (pixel >> 8 & 255) > 20 || (pixel >> 16 & 255) > 20) lit++;
            }
            if(lit < target.width * target.height / 100) throw new AssertionError("The captured image is black");
            int center = image.getPixelRGBA(target.width / 2, target.height / 2);
            if((center & 255) < (center >> 8 & 255) * 1.5 || (center & 255) < (center >> 16 & 255) * 1.5)
                throw new AssertionError("Distant terrain overwrote the red foreground wall: " + Integer.toHexString(center));
        }finally{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL13.glActiveTexture(active);
        }
    }

    // Use the same feed renderer as camera widgets while a display is visible
    @SubscribeEvent
    public static void render(RenderGuiEvent.Post evt){
        if(OUTPUT == null || complete || !showing || source == null) return;
        var mc = Minecraft.getInstance();
        ViewFeedRenderer.draw(evt.getGuiGraphics(), mc.font, source, 12, 12, 320, 180);
    }

    // Install a small camera scene without touching the user's running world
    private static void install(Minecraft mc){
        mc.getSingleplayerServer().execute(() -> {
            try{
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.teleportTo(.5, -56, 6.5);
                player.connection.teleport(.5, -56, 6.5, 180, 0);
                for(int x = -12; x <= 12; x++) for(int z = -18; z <= 8; z++)
                    level.setBlock(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState(), 3);
                for(int x = -8; x <= 8; x++) for(int y = -62; y <= -50; y++)
                    level.setBlock(new BlockPos(x, y, -6), Blocks.RED_CONCRETE.defaultBlockState(), 3);
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createthrusters:camera"));
                level.setBlock(CAMERA, block.defaultBlockState().setValue(CameraBlock.FACING, Direction.UP), 3);
                var camera = (CameraBlockEntity)level.getBlockEntity(CAMERA);
                camera.configure(64, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL,
                        ViewRig.Orientation.LOCAL, 0, -90);
                prepared = true;
            }catch(Throwable err){ serverFailure = err; }
        });
    }

    // Inspect allocation identity without adding production diagnostic hooks
    private static Object field(Object owner, String name) throws Exception{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    // Inspect the retained source map after each actual renderer frame
    private static Map<?, ?> captures() throws Exception{
        var field = ViewSceneRenderer.class.getDeclaredField("CAPTURES");
        field.setAccessible(true);
        return (Map<?, ?>)field.get(null);
    }
    // Read the actual screen after rendering rather than only inspecting its input flags
    private static long editorImage(String name) throws Exception{
        var target = Minecraft.getInstance().getMainRenderTarget();
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try(var image = new NativeImage(target.width, target.height, false)){
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId()); image.downloadTexture(0, false);
            image.flipY(); image.writeToFile(Path.of(OUTPUT).resolve(name));
            if(name.equals("world-before-editor.png")){
                int center = image.getPixelRGBA(target.width / 2, target.height / 3);
                if((center & 255) < (center >> 8 & 255) * 1.5 || (center & 255) < (center >> 16 & 255) * 1.5)
                    throw new AssertionError("The main world lost foreground occlusion after a camera LOD draw: " + Integer.toHexString(center));
            }
            long hash = 1;
            for(int y = 0; y < target.height; y++) for(int x = 0; x < target.width; x++) hash = hash * 31 + image.getPixelRGBA(x, y);
            return hash;
        }finally{ GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); GL13.glActiveTexture(active); }
    }
    // Exercise the real editor controls without exposing test hooks in production APIs
    private static Object invoke(Object owner, String name, Object... args) throws Exception{
        for(var method : owner.getClass().getDeclaredMethods()){
            if(method.getName().equals(name) && method.getParameterCount() == args.length){ method.setAccessible(true); return method.invoke(owner, args); }
        }
        throw new NoSuchMethodException(name);
    }
    // Persist the result before stopping the isolated client
    private static void finish(String result){
        if(complete) return;
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT).resolve("result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ Minecraft.getInstance().stop(); }
    }
}
