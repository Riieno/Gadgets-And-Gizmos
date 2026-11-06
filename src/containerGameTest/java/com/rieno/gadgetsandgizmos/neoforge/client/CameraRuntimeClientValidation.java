package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.CameraBlock;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

// Exercise real Sodium captures, off-screen pauses and renderer reuse in an isolated world
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CameraRuntimeClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.cameraRuntimeValidation");
    private static final BlockPos CAMERA = new BlockPos(0, -58, 0);
    // Retain only the validation world's fixture and pause cycle
    private static boolean hidden, installed, complete, showing;
    private static volatile boolean prepared;
    private static volatile Throwable serverFailure;
    private static long started, changed, lastRendered, worstResumeMillis;
    private static int stage, cycles;
    private static ViewReference source;
    private static Object retainedCapture, retainedRenderer;

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
            mc.setScreen(null);
            if(!installed){ installed = true; install(mc); return; }
            if(!prepared || !(mc.level.getBlockEntity(CAMERA) instanceof CameraBlockEntity camera)) return;
            if(source == null){ source = ViewReference.of(camera); showing = true; changed = System.nanoTime(); }
            Object capture = captures().get(source);
            if(capture == null && stage > 0) throw new AssertionError("A paused feed expired its cached scene");
            if(capture == null || !((Boolean)field(capture, "ready"))) return;
            long now = System.nanoTime();
            switch(stage){
                case 0 -> {
                    if(now - changed < 2_000_000_000L) return;
                    retainedCapture = capture;
                    retainedRenderer = field(capture, "renderer");
                    if(retainedRenderer == null) throw new AssertionError("No capture renderer was initialized");
                    if(cycles == 0) captureImage(capture);
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
                        finish("PASSED real capture rendering, two pauses over six seconds, no hidden captures and no renderer reallocation on return; worst resume "
                                + worstResumeMillis + " ms");
                    }else{ stage = 0; changed = now; }
                }
            }
        }catch(Throwable err){ err.printStackTrace(); finish("FAILED " + err); }
    }

    // Retain the actual captured terrain image for visual inspection
    private static void captureImage(Object capture) throws Exception{
        var target = (TextureTarget)field(capture, "target");
        RenderSystem.bindTexture(target.getColorTextureId());
        try(var image = new NativeImage(target.width, target.height, false)){
            image.downloadTexture(0, false);
            image.flipY();
            image.writeToFile(Path.of(OUTPUT).resolve("feed.png"));
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
                for(int x = -12; x <= 12; x++) for(int z = -18; z <= 8; z++)
                    level.setBlock(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState(), 3);
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
    // Persist the result before stopping the isolated client
    private static void finish(String result){
        if(complete) return;
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT).resolve("result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ Minecraft.getInstance().stop(); }
    }
}
