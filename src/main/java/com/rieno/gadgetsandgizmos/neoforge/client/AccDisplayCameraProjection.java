package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.view.ProjectedViewControlCanvas;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlPanel;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.view.ControlledViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayCameraControlPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.IdentityHashMap;
import java.util.Map;

// Route the display's projected controls without allocating another scene or framebuffer
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class AccDisplayCameraProjection{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<AccDisplayBlockEntity, Session> SESSIONS = new IdentityHashMap<>();
    private static Session active;
    private static int button;
    private AccDisplayCameraProjection(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void render(AccDisplayBlockEntity display, ViewReference ref, int width, int height,
                              Font font, PoseStack stack, MultiBufferSource buffers, float z){
        if(ViewSceneRenderer.isCapturing() || display == null || ref == null || width < 32 || height < 12) return;
        var src = ref.resolve(Minecraft.getInstance().level);
        if(!(src instanceof ControlledViewSource camera)) return;
        long now = Util.getMillis();
        SESSIONS.entrySet().removeIf(entry -> {
            if(!entry.getKey().isRemoved() && now - entry.getValue().lastUse < 10_000) return false;
            entry.getValue().controls.mouseReleased();
            if(active == entry.getValue()) active = null;
            return true;
        });
        Session session = SESSIONS.computeIfAbsent(display, Session::new);
        if(!ref.equals(session.source)){ session.controls.mouseReleased(); session.source = ref; }
        float scale = Math.min(width / 640.0F, height / 160.0F);
        session.width = Math.max(1, Math.round(width / scale));
        session.height = Math.max(1, Math.round(height / scale));
        session.lastUse = now;
        stack.pushPose();
        stack.scale(scale, scale, 1);
        session.controls.renderOverlay(new ProjectedViewControlCanvas(stack, buffers,
                        ResourceLocation.withDefaultNamespace("textures/misc/white.png"), z - 0.02F),
                font, 0, 0, session.width, session.height, camera.viewControlState(), input -> PacketDistributor.sendToServer(
                        new AccDisplayCameraControlPayload(new MenuConfigTarget(display.getBlockPos(),
                                SimulatedHelper.getContainingSubLevelId(display)), ref.toTag(), input.toTag())));
        stack.popPose();
    }
    public static boolean interact(AccDisplayBlockEntity display, double x, double y, int mouseButton){
        Session session = SESSIONS.get(display);
        if(session == null || !session.source.equals(display.displayedCamera())) return false;
        if(!session.controls.mouseClicked(x * session.width, y * session.height, mouseButton)) return false;
        if(active != null && active != session) active.controls.mouseReleased();
        active = session;
        button = mouseButton;
        return true;
    }
    public static void updatePointer(AccDisplayBlockEntity display, double x, double y){
        if(active == null) return;
        var mc = Minecraft.getInstance();
        if(display != active.display || mc.level == null || mc.screen != null
                || !active.source.equals(display.displayedCamera())
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), button) != GLFW.GLFW_PRESS){
            release();
            return;
        }
        active.controls.mouseDragged(x * active.width, y * active.height);
        active.controls.tick();
    }
    public static void release(){ if(active != null) active.controls.mouseReleased(); active = null; }
    // Drop cached world surfaces when the player changes levels or disconnects
    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload evt){
        if(!evt.getLevel().isClientSide()) return;
        release();
        SESSIONS.values().forEach(session -> session.controls.mouseReleased());
        SESSIONS.clear();
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final class Session{
        private final AccDisplayBlockEntity display;
        private final ViewControlPanel controls = new ViewControlPanel();
        private ViewReference source;
        private int width;
        private int height;
        private long lastUse;
        private Session(AccDisplayBlockEntity display){ this.display = display; }
    }
}
