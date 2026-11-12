package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderGeometryCache;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.file.Path;
import java.util.List;

// Check the actual world widget painter and retained batches against a known red camera scene
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CameraWidgetSurfaceValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final RenderGeometryCache[] GEOMETRY = {new RenderGeometryCache(true), new RenderGeometryCache(true)};
    private static final Vector4f[] POINTS = {new Vector4f(), new Vector4f()};
    private static final AccDisplayBlockEntity[] DISPLAYS = new AccDisplayBlockEntity[2];
    private static ViewReference source;
    private static Throwable failure;
    private static int draws, checks;

    private CameraWidgetSurfaceValidation(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Use real widget data while keeping the fixture independent of server display discovery
    static void start(ViewReference ref){
        source = ref; draws = checks = 0; failure = null;
        for(int idx = 0; idx < DISPLAYS.length; idx++){
            String type = idx == 0 ? "acc_display_widget" : "acc_hologram_widget";
            CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(type, null);
            data.putInt("WidgetWidth", 144); data.putInt("WidgetHeight", 81);
            CompoundTag elm = new CompoundTag();
            elm.putString("Type", "camera_source"); elm.putString("InteractionId", "camera");
            elm.putInt("W", 144); elm.putInt("H", 81);
            ListTag elements = new ListTag(); elements.add(elm); data.put("WidgetElements", elements);
            data.getCompound("Defaults").put("visible", AdvancedGraphDocument.Value.bool(true).toTag());
            data.getCompound("Defaults").put("size_x", AdvancedGraphDocument.Value.number(2).toTag());
            data.getCompound("Defaults").put("size_y", AdvancedGraphDocument.Value.number(1.125).toTag());
            data.getCompound("Defaults").put("offset_z", AdvancedGraphDocument.Value.number(0).toTag());
            var node = new AdvancedGraphDocument.Node(type, type, type, 0, 0, data);
            DISPLAYS[idx] = new AccDisplayBlockEntity(new BlockPos(idx, -54, 0), CTBlocks.ACC_DISPLAY.get().defaultBlockState()){
                @Override public List<AdvancedGraphDocument.Node> widgetNodes(){ return List.of(node); }
                @Override public boolean targetsThisDisplay(AdvancedGraphDocument.Node candidate){ return true; }
                @Override public ViewReference widgetCamera(String nodeId, String interactionId){ return source; }
            };
            DISPLAYS[idx].setLevel(Minecraft.getInstance().level);
        }
    }

    // Render on world depth and shader targets, including the display's normal clipping and cache path
    @SubscribeEvent
    public static void render(RenderLevelStageEvent evt){
        if(source == null || failure != null || ViewSceneRenderer.isCapturing()
                || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        try{
            var mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            var camera = evt.getCamera().getPosition();
            var method = AccDisplayRenderer.class.getDeclaredMethod("renderWidgets", AccDisplayBlockEntity.class,
                    List.class, int.class, int.class, PoseStack.class, net.minecraft.client.renderer.MultiBufferSource.class);
            method.setAccessible(true);
            for(int idx = 0; idx < DISPLAYS.length; idx++){
                var poses = new PoseStack();
                poses.mulPose(evt.getPoseStack().last().pose());
                poses.translate(-camera.x + (idx == 0 ? -1.8 : .8), -camera.y - 53.6, -camera.z);
                poses.mulPose(Axis.YP.rotationDegrees((float)Math.sin(draws * .04) * 12));
                poses.scale(1F / 72, -1F / 72, -1F / 72);
                var renderer = (AccDisplayRenderer)mc.getBlockEntityRenderDispatcher().getRenderer(DISPLAYS[idx]);
                final int slot = idx;
                GEOMETRY[idx].render(poses, buffers, draws % 3 == 0, target -> {
                    // A missing feed must reveal blue rather than the red wall in the player's world
                    HudWidgetRenderer.surface(new PoseStack(), target, mc.font, .3F, -.01F, .2F,
                            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                            .quad(0, 0, 0, 81, 144, 81, 144, 0, 0xFF1638D0, 0);
                    try{ method.invoke(renderer, DISPLAYS[slot], DISPLAYS[slot].widgetNodes(), 144, 81, new PoseStack(), target); }
                    catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
                });
                var matrix = new Matrix4f(evt.getProjectionMatrix()).mul(evt.getModelViewMatrix()).mul(poses.last().pose());
                POINTS[idx].set(144 * .4F, 81 * .3F, -.2F, 1).mul(matrix);
                POINTS[idx].div(POINTS[idx].w);
            }
            buffers.endBatch(); draws++;
        }catch(Throwable err){ failure = err; }
    }

    // Sample successive completed frames so alternating placeholder and feed planes cannot pass
    static boolean check(Path output) throws Exception{
        if(failure != null) throw new AssertionError("World camera widget fixture failed", failure);
        if(draws < 5) return false;
        var target = Minecraft.getInstance().getMainRenderTarget();
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try(var image = new NativeImage(target.width, target.height, false)){
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
            image.downloadTexture(0, false); image.flipY();
            image.writeToFile(output.resolve("world-camera-widgets.png"));
            for(int idx = 0; idx < POINTS.length; idx++){
                int x = Math.round((POINTS[idx].x * .5F + .5F) * target.width);
                int y = Math.round((.5F - POINTS[idx].y * .5F) * target.height);
                if(x < 1 || y < 1 || x >= target.width - 1 || y >= target.height - 1)
                    throw new AssertionError("World widget sample is outside the viewport: " + x + ", " + y);
                int red = 0;
                for(int dy = -1; dy <= 1; dy++) for(int dx = -1; dx <= 1; dx++){
                    int pixel = image.getPixelRGBA(x + dx, y + dy);
                    if((pixel & 255) > 30 && (pixel & 255) > (pixel >> 8 & 255) * 1.5
                            && (pixel & 255) > (pixel >> 16 & 255) * 1.5) red++;
                }
                if(red < 6) throw new AssertionError((idx == 0 ? "ACC Display" : "Hologram")
                        + " camera feed was hidden by its background at " + x + ", " + y);
            }
            return ++checks >= 20;
        }finally{ GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); GL13.glActiveTexture(active); }
    }

    static void stop(){ source = null; for(var geometry : GEOMETRY) geometry.clear(); }
}
