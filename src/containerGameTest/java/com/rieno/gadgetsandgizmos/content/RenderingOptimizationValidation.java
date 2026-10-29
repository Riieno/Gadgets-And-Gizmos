package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Run opt-in GPU lifecycle assertions in an isolated development client, outside distribution jars
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class RenderingOptimizationValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final boolean ENABLED = Boolean.getBoolean("gadgetsngizmos.renderingValidation");
    private static final List<ViewReference> SOURCES = new ArrayList<>();
    private static final List<AbstractTexture> TEXTURES = new ArrayList<>();
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static int stage;
    private static int frames;
    private static long since;
    private static boolean installed;
    private static boolean complete;
    private static ResourceLocation original;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(!ENABLED || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if(mc.screen instanceof PauseScreen) mc.setScreen(null);
        if(installed) return;
        installed = true;
        for(int idx = 0; idx < 9; idx++){
            SOURCES.add(new ViewReference(mc.level.dimension().location(), null, new BlockPos(5, -59, idx + 2)));
        }
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            for(int x = -6; x <= 8; x++){
                for(int z = -6; z <= 12; z++){
                    level.setBlock(new BlockPos(x, -60, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(new BlockPos(4, -59, 0), CTBlocks.WORLD_HOLOGRAM_PROJECTOR.get().defaultBlockState(), 3);
            level.setBlock(new BlockPos(4, -59, -2), CTBlocks.THRUSTER.get().defaultBlockState(), 3);
            level.setBlock(HologramRenderingValidation.SOURCE, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), 3);
            level.setBlock(HybridHologramValidation.TANK, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(HybridHologramValidation.TANK, CTBlocks.SMART_TANK.get().defaultBlockState(), 3);
            level.setBlock(HybridHologramValidation.CHEST, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
            level.setBlock(HybridHologramValidation.COG, com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState(), 3);
            level.setBlock(DisplayGeometryValidation.SOURCE, CTBlocks.ACC_DISPLAY.get().defaultBlockState(), 3);
            for(var source : SOURCES) level.setBlock(source.blockPos(), CTBlocks.CAMERA.get().defaultBlockState(), 3);
            for(var player : server.getPlayerList().getPlayers()){
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.connection.teleport(.5, -57, .5, -90, 20);
            }
        });
        since = System.nanoTime();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post evt){
        if(!ENABLED || !installed || complete) return;
        try{
            checkFrame();
        }catch(Throwable err){
            complete = true;
            LogUtils.getLogger().error("Rendering optimization validation failed", err);
            result("FAILED: " + err);
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void checkFrame(){
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null) return;
        long elapsed = System.nanoTime() - since;
        if(elapsed > 30_000_000_000L) throw new AssertionError("Camera validation timed out at stage " + stage);
        switch(stage){
            case 0 -> {
                ResourceLocation texture = request(0);
                if(texture == null) return;
                original = texture;
                TEXTURES.add(mc.getTextureManager().getTexture(texture));
                advance(1);
            }
            case 1 -> {
                if(elapsed < 4_000_000_000L) return;
                require(original.equals(request(0)), "An idle feed was rebuilt before five seconds");
                advance(2);
            }
            case 2 -> {
                if(elapsed < 6_000_000_000L) return;
                require(TEXTURES.getFirst().getId() < 0, "Expired feed retained its GPU attachment");
                advance(3);
            }
            case 3 -> {
                ResourceLocation texture = request(0);
                if(texture == null) return;
                require(!original.equals(texture), "Expired feed reused its disposed registration");
                TEXTURES.clear();
                advance(4);
            }
            case 4 -> {
                var ready = new HashSet<ResourceLocation>();
                for(int idx = 0; idx < 8; idx++){
                    ResourceLocation texture = request(idx);
                    if(texture != null) ready.add(texture);
                }
                if(ready.size() != 8) return;
                TEXTURES.clear();
                for(var texture : ready) TEXTURES.add(mc.getTextureManager().getTexture(texture));
                advance(5);
            }
            case 5 -> {
                for(int idx = 0; idx < 8; idx++) request(idx);
                require(request(8) == null, "A ninth feed displaced an actively requested source");
                if(++frames >= 30) advance(6);
            }
            case 6 -> {
                var ready = new HashSet<ResourceLocation>();
                for(int idx = 1; idx < 9; idx++){
                    ResourceLocation texture = request(idx);
                    if(texture != null) ready.add(texture);
                }
                if(ready.size() != 8) return;
                require(request(0) == null, "A retired feed bypassed the active source limit");
                require(TEXTURES.stream().anyMatch(texture -> texture.getId() < 0),
                        "Replacing an idle feed retained every old GPU attachment");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Camera capture left an OpenGL error");
                reload = mc.reloadResourcePacks();
                advance(7);
            }
            case 7 -> {
                if(!reload.isDone()) return;
                reload.join();
                advance(8);
            }
            case 8 -> {
                ResourceLocation texture = request(1);
                if(texture == null) return;
                if(elapsed < 1_000_000_000L) return;
                AbstractTexture retained = mc.getTextureManager().getTexture(texture);
                require(retained.getId() >= 0, "Resource reload lost the live capture attachment");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Reloaded capture left an OpenGL error");
                ViewSceneRenderer.clear();
                require(retained.getId() < 0, "World cleanup retained a GPU attachment");
                HologramRenderingValidation.begin();
                advance(9);
            }
            case 9 -> {
                if(!HologramRenderingValidation.finish()) return;
                HybridHologramValidation.run();
                DisplayGeometryValidation.run();
                ContainerRenderingValidation.run();
                GeometryBufferValidation.run();
                HologramResolutionValidation.run();
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Base hologram validation left an OpenGL error");
                TankWindowRenderingValidation.begin();
                advance(10);
            }
            case 10 -> {
                if(!TankWindowRenderingValidation.finish()) return;
                complete = true;
                result("PASSED: camera lifecycle; hologram GPU retention, clipping and cleanup; tank interpolation; animated and frozen native partials; static fallback geometry; closed tank meshes; ACC geometry reuse, immediate frame updates and camera isolation; static container previews and tank presentation modes; server-to-client tank housing and hidden tablet GUI access; hologram LOD mesh reduction, quality switching, transparent detail and resource cleanup; packed vertex buffers");
                LogUtils.getLogger().info("Rendering optimization validation passed");
            }
            default -> throw new AssertionError("Unknown validation stage " + stage);
        }
    }

    private static ResourceLocation request(int idx){ return ViewSceneRenderer.request(SOURCES.get(idx), 128, 128); }
    private static void require(boolean value, String message){ if(!value) throw new AssertionError(message); }
    private static void advance(int next){ stage = next; frames = 0; since = System.nanoTime(); }

    private static void result(String text){
        String destination = System.getProperty("gadgetsngizmos.renderingValidationResult", "");
        if(destination.isBlank()) return;
        try{ Files.writeString(Path.of(destination), text); }
        catch(java.io.IOException err){ throw new IllegalStateException("Cannot write rendering validation result", err); }
    }
}
