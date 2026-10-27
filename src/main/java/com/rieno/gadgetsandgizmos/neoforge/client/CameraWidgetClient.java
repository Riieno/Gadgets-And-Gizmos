package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayWidgetGeometry;
import com.rieno.gadgetsandgizmos.lib.client.render.ClientViewOwnership;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedControls;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlAction;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayCameraControlPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayCameraInteractPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayCameraTakeoverPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudInteractionPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Route each widget's overlay through its paired HUD or display authorization
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CameraWidgetClient{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Key, Session> SESSIONS = new LinkedHashMap<>();
    private static final ViewFeedControls PREVIEW = new ViewFeedControls();
    private static Session activeWorld;
    private CameraWidgetClient(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Preview the overlay while designer gestures continue editing the element bounds
    public static void preview(GuiGraphics graphics, Font font, ViewReference ref, int x, int y, int width, int height){
        PREVIEW.render(graphics, font, ref, x, y, width, height, (action, input) -> {});
    }

    public static void renderGui(Key key, GuiGraphics graphics, Font font, ViewReference ref,
                                 int x, int y, int width, int height){
        if(ClientViewOwnership.isReserved()){ releaseHud(); return; }
        var mc = Minecraft.getInstance();
        if(mc.screen == null || !mc.isWindowActive()) releaseHud();
        Session session = session(key, ref);
        session.controls.render(graphics, font, ref, x, y, width, height, session::send);
    }

    public static void renderSurface(AccDisplayBlockEntity display, String nodeId, String interactionId,
            boolean hologram, ViewReference ref, Font font, int width, int height, float z,
            PoseStack stack, MultiBufferSource buffers){
        if(ClientViewOwnership.isReserved() || ViewSceneRenderer.isCapturing()) return;
        Session session = session(key(display, nodeId, interactionId), ref);
        session.display = display;
        session.hologram = hologram;
        session.controls.renderSurface(font, ref, width, height, z, stack, buffers, session::send);
    }

    public static boolean click(Key key, double x, double y, int button){
        Session session = SESSIONS.get(key);
        return !ClientViewOwnership.isReserved() && recent(session) && session.controls.mouseClicked(x, y, button);
    }
    public static void drag(Key key, double x, double y){
        Session session = SESSIONS.get(key);
        if(recent(session)) session.controls.mouseDragged(x, y);
    }
    public static void release(Key key){
        Session session = SESSIONS.get(key);
        if(session != null) session.controls.mouseReleased();
    }

    // Consume feed clicks while only painted control regions issue camera commands
    public static boolean interactWorld(AccDisplayBlockEntity display, AccDisplayWidgetGeometry.Hit hit, int button){
        if(hit == null || !"camera_source".equals(hit.element().getString("Type"))) return false;
        Key key = key(display, hit.node().id(), hit.element().getString("InteractionId"));
        releaseWorld();
        if(click(key, hit.fraction(), hit.verticalFraction(), button)){
            Session session = SESSIONS.get(key);
            if(session.controls.isDragging()) activeWorld = session;
        }
        return true;
    }

    public static void updateWorld(AccDisplayBlockEntity display, AccDisplayWidgetGeometry.Hit hit, boolean hologram){
        if(activeWorld == null || activeWorld.hologram != hologram) return;
        var mc = Minecraft.getInstance();
        if(ClientViewOwnership.isReserved() || mc.screen != null || mc.player == null || !mc.isWindowActive()
                || !mc.options.keyUse.isDown() && !ViewInteractionClient.held()
                || display != activeWorld.display || hit == null
                || !activeWorld.key.nodeId().equals(hit.node().id())
                || !activeWorld.key.interactionId().equals(hit.element().getString("InteractionId"))
                || !recent(activeWorld)){
            releaseWorld();
            return;
        }
        activeWorld.controls.mouseDragged(hit.fraction(), hit.verticalFraction());
    }

    public static boolean isDragging(){ return activeWorld != null && activeWorld.controls.isDragging(); }
    public static void releaseWorld(){
        if(activeWorld != null) activeWorld.controls.mouseReleased();
        activeWorld = null;
    }
    public static void releaseHud(){
        SESSIONS.values().stream().filter(session -> session.display == null).forEach(session -> session.controls.mouseReleased());
    }
    public static void clear(){
        SESSIONS.values().forEach(session -> session.controls.mouseReleased());
        SESSIONS.clear();
        PREVIEW.mouseReleased();
        activeWorld = null;
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event){
        if(event.getLevel().isClientSide()) clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Key key(AccDisplayBlockEntity display, String nodeId, String interactionId){
        return new Key(MenuConfigTarget.of(display.getBlockPos(), SimulatedHelper.getContainingSubLevelId(display)),
                null, nodeId, interactionId);
    }
    private static boolean recent(Session session){ return session != null && Util.getMillis() - session.lastUse < 500; }
    private static Session session(Key key, ViewReference ref){
        long now = Util.getMillis();
        SESSIONS.entrySet().removeIf(entry -> {
            if(now - entry.getValue().lastUse < 10_000) return false;
            entry.getValue().controls.mouseReleased();
            if(activeWorld == entry.getValue()) activeWorld = null;
            return true;
        });
        Session session = SESSIONS.computeIfAbsent(key, Session::new);
        session.source = ref;
        session.lastUse = now;
        return session;
    }

    public record Key(MenuConfigTarget target, UUID pairId, String nodeId, String interactionId){}

    private static final class Session{
        private final Key key;
        private final ViewFeedControls controls = new ViewFeedControls();
        private AccDisplayBlockEntity display;
        private boolean hologram;
        private ViewReference source;
        private long lastUse;
        private Session(Key key){ this.key = key; }

        private void send(ViewControlAction action, ViewControlInput input){
            if(source == null || ClientViewOwnership.isReserved()) return;
            if(key.pairId() != null){
                PacketDistributor.sendToServer(AdvancedHudInteractionPayload.camera(key.target(), key.pairId(),
                        key.nodeId(), key.interactionId(), action, input));
            }else if(display != null){
                switch(action){
                    case CONTROL -> PacketDistributor.sendToServer(new AccDisplayCameraControlPayload(key.target(), source.toTag(), input.toTag()));
                    case TAKE_CONTROL -> PacketDistributor.sendToServer(new AccDisplayCameraTakeoverPayload(key.target(), source.toTag()));
                    case INTERACT -> PacketDistributor.sendToServer(new AccDisplayCameraInteractPayload(key.target(), source.toTag()));
                }
            }
        }
    }
}
