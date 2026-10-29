package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderSurfaceVisibility;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRequests;
import com.rieno.gadgetsandgizmos.lib.display.DisplayFrameDemand;
import com.rieno.gadgetsandgizmos.content.CTDirectionalBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletSurface;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Project tablet apps onto controller displays and clamp input to the visible panel
public final class DiagnosticTabletGuiProjection {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long FRAME_INTERVAL_MILLIS = 50L;
    private static final long SESSION_TIMEOUT_MILLIS = 5_000L;
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();
    private static final ResourceLocation CONTAINER_PREVIEW = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/apps/smart_container_manifest.png");
    private static long frame;
    private static final Map<DiagnosticTabletBlockEntity, Session> BLOCK_SESSIONS =
            new IdentityHashMap<>();
    private static final Map<net.minecraft.world.level.block.entity.BlockEntity, Session> CONTAINER_SESSIONS = new IdentityHashMap<>();
    private static final Map<InteractionHand, Session> ITEM_SESSIONS =
            new EnumMap<>(InteractionHand.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet gui projection
    private DiagnosticTabletGuiProjection() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the block
    public static boolean renderBlock(DiagnosticTabletBlockEntity tablet, float partialTick,
                                      PoseStack poseStack, MultiBufferSource buffers) {
        if(HologramRenderScope.isActive()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || minecraft.player == null || minecraft.level == null) return false;
        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        Direction facing = tablet.getBlockState().getValue(CTDirectionalBlock.FACING);
        if(session.lastFrame != 0L && !session.visibility.isVisible()){
            return draw(session, DiagnosticTabletSurface.screen(facing), false, poseStack, buffers);
        }
        int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet),
                fingerprint, tablet)) return false;
        return draw(session, DiagnosticTabletSurface.screen(facing), false, poseStack, buffers);
    }

    // Project the dedicated manifest onto a partial attached to a smart multiblock
    public static boolean renderContainer(net.minecraft.world.level.block.entity.BlockEntity target,
            DiagnosticTabletSurface.Surface surface, PoseStack poses, MultiBufferSource buffers){
        return renderContainer(target, surface, poses, buffers, true);
    }

    public static boolean renderContainer(net.minecraft.world.level.block.entity.BlockEntity target,
            DiagnosticTabletSurface.Surface surface, PoseStack poses, MultiBufferSource buffers, boolean live){
        if(HologramRenderScope.isActive()) return false;
        Session session = CONTAINER_SESSIONS.get(target);
        if(ViewSceneRenderer.isCapturing() || !live){
            if(session != null && session.projection != null && session.projection.isReady()){
                if(!ViewSceneRenderer.isCapturing()) session.demand.request(frame, false);
                return draw(session, surface, false, poses, buffers);
            }
            return drawContainerPreview(surface, poses, buffers);
        }
        session = containerSession(target);
        session.demand.request(frame, true);
        if(session.lastFrame != 0L && !session.visibility.isVisible()) return draw(session, surface, false, poses, buffers);
        if(!session.prepare(() -> DiagnosticTabletScreen.containerScreen(target, true), com.rieno.gadgetsandgizmos.content.SmartContainerTablets.tab(target).hashCode(), target)) return false;
        return draw(session, surface, false, poses, buffers);
    }

    public static boolean interactContainer(net.minecraft.world.level.block.entity.BlockEntity target, double x, double y){
        Session session = containerSession(target);
        session.demand.request(frame, true);
        if(!session.prepare(() -> DiagnosticTabletScreen.containerScreen(target, true), com.rieno.gadgetsandgizmos.content.SmartContainerTablets.tab(target).hashCode(), target)) return false;
        session.lastUse = Util.getMillis();
        boolean handled = session.screen.mouseClicked(x * DiagnosticTabletScreen.projectionWidth(),
                y * DiagnosticTabletScreen.projectionHeight(), 0);
        if(handled) session.lastFrame = 0L;
        return handled;
    }

    // Draw the ACC display
    public static boolean renderAccDisplay(DiagnosticTabletBlockEntity tablet, int width, int height,
                                           float partialTick, PoseStack poseStack,
                                           MultiBufferSource buffers) {
        if(HologramRenderScope.isActive()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || minecraft.player == null || minecraft.level == null) return false;
        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        if(session.lastFrame == 0L || session.visibility.isVisible()){
            int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
            if(!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet), fingerprint, tablet)) return false;
        }
        long now = Util.getMillis();
        session.lastUse = now;
        if (session.texture == null) return false;
        RenderType type = RenderType.text(session.texture);
        var vertices = buffers.getBuffer(ViewSceneRenderer.isCapturing() ? type : session.visibility.track(type));
        Matrix4f matrix = poseStack.last().pose();
        vertices.addVertex(matrix, 0.0F, 0.0F, -0.20F).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, 0.0F, height, -0.20F).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, width, height, -0.20F).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, width, 0.0F, -0.20F).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F)
                .setLight(0x00F000F0);
        return true;
    }

    // Keep the last projected image when a distant ACC display presents it without refresh demand
    static void retainAccDisplay(DiagnosticTabletBlockEntity tablet){
        Session session = BLOCK_SESSIONS.get(tablet);
        if(session != null) session.lastUse = Util.getMillis();
    }

    // Handle the ACC display
    public static boolean interactAccDisplay(DiagnosticTabletBlockEntity tablet,
                                              double normalizedX, double normalizedY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || minecraft.player == null) return false;
        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet), fingerprint, tablet)) {
            return false;
        }
        session.lastUse = Util.getMillis();
        boolean handled = session.screen.mouseClicked(
                Mth.clamp(normalizedX, 0.0D, 1.0D) * DiagnosticTabletScreen.projectionWidth(),
                Mth.clamp(normalizedY, 0.0D, 1.0D) * DiagnosticTabletScreen.projectionHeight(), 0);
        if (handled) session.lastFrame = 0L;
        return handled;
    }

    // Draw the item
    public static boolean renderItem(InteractionHand hand, ItemStack stack,
                                     PoseStack poseStack, MultiBufferSource buffers) {
        if(HologramRenderScope.isActive()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (hand == null || stack == null || stack.isEmpty() || minecraft.player == null) return false;
        Session session = ITEM_SESSIONS.computeIfAbsent(hand, ignored -> new Session());
        if(session.lastFrame != 0L && !session.visibility.isVisible()){
            return draw(session, DiagnosticTabletSurface.screen(Direction.UP), true, poseStack, buffers);
        }
        DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
        int fingerprint = DiagnosticTabletData.toTag(state).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionItem(hand, stack),
                fingerprint, state.tabletId() == null ? stack : state.tabletId())) return false;
        return draw(session, DiagnosticTabletSurface.screen(Direction.UP), true, poseStack, buffers);
    }

    // Handle projected tablet block interaction
    public static boolean interactBlock(DiagnosticTabletBlockEntity tablet,
                                        BlockHitResult hit, int mouseButton) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || hit == null || minecraft.player == null) return false;
        Direction facing = tablet.getBlockState().getValue(CTDirectionalBlock.FACING);
        if (hit.getDirection() != facing) return false;
        DiagnosticTabletSurface.Point point = DiagnosticTabletSurface.screen(facing).project(
                hit.getLocation().subtract(tablet.getBlockPos().getX(),
                        tablet.getBlockPos().getY(), tablet.getBlockPos().getZ()));
        if (point == null) return false;

        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet),
                fingerprint, tablet)) return false;
        session.lastUse = Util.getMillis();
        boolean handled = session.screen.mouseClicked(
                point.x() * DiagnosticTabletScreen.projectionWidth(),
                point.y() * DiagnosticTabletScreen.projectionHeight(), mouseButton);
        if (handled) {
            session.lastFrame = 0L;
        }
        return handled;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the client tick event
    public static void onClientTick(ClientTickEvent.Pre evt) {
        long now = Util.getMillis();
        tick(BLOCK_SESSIONS, now);
        tick(ITEM_SESSIONS, now);
        tick(CONTAINER_SESSIONS, now);
        purge(now);
    }

    // Handle the render frame event
    public static void onRenderFrame(RenderFrameEvent.Pre evt) {
        frame++;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        long now = Util.getMillis();
        renderFrames(BLOCK_SESSIONS, now,
                evt.getPartialTick().getGameTimeDeltaPartialTick(true));
        renderFrames(CONTAINER_SESSIONS, now, evt.getPartialTick().getGameTimeDeltaPartialTick(true));
        renderFrames(ITEM_SESSIONS, now,
                evt.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    // Update the diagnostic tablet gui projection
    private static <K> void tick(Map<K, Session> sessions, long now) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if(session.screen != null && (!session.container || session.demand.live(frame)) && now - session.lastUse <= SESSION_TIMEOUT_MILLIS
                    && (session.lastFrame == 0L || session.visibility.isVisible())){
                session.screen.tick();
            }
        }
    }

    // Draw the frames
    private static <K> void renderFrames(Map<K, Session> sessions, long now, float partialTick) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if(session.screen != null && (!session.container || session.demand.live(frame)) && now - session.lastUse <= SESSION_TIMEOUT_MILLIS
                    && (session.lastFrame == 0L || session.visibility.isVisible())){
                session.feeds.refresh();
                if(session.projection == null || !session.projection.isReady() || now - session.lastFrame >= FRAME_INTERVAL_MILLIS){
                    session.renderFrame(partialTick);
                    session.lastFrame = now;
                }
            }
        }
    }

    // Draw the diagnostic tablet gui projection
    private static boolean draw(Session session, DiagnosticTabletSurface.Surface surface,
                                boolean itemCoordinates, PoseStack poseStack,
                                MultiBufferSource buffers) {
        long now = Util.getMillis();
        session.lastUse = now;
        if (session.projection == null || !session.projection.isReady() || session.texture == null) return false;

        Vec3 offset = itemCoordinates ? new Vec3(-0.5D, -0.5D, -0.5D) : Vec3.ZERO;
        Vec3 topLeft = surface.topLeft().add(offset);
        Vec3 bottomLeft = surface.bottomLeft().add(offset);
        Vec3 bottomRight = surface.bottomRight().add(offset);
        Vec3 topRight = surface.topRight().add(offset);
        RenderType type = RenderType.textPolygonOffset(session.texture);
        var vertices = buffers.getBuffer(ViewSceneRenderer.isCapturing() ? type : session.visibility.track(type));
        Matrix4f matrix = poseStack.last().pose();
        vertex(vertices, matrix, topLeft, 0.0F, 1.0F);
        vertex(vertices, matrix, bottomLeft, 0.0F, 0.0F);
        vertex(vertices, matrix, bottomRight, 1.0F, 0.0F);
        vertex(vertices, matrix, topRight, 1.0F, 1.0F);
        return true;
    }

    // Add the projection vertex
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vertices,
                               Matrix4f matrix, Vec3 point, float u, float v) {
        vertices.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(0xFFFFFFFF).setUv(u, v).setLight(0x00F000F0);
    }

    // A never-opened distant tablet uses a shared image without allocating a screen or framebuffer
    private static boolean drawContainerPreview(DiagnosticTabletSurface.Surface surface, PoseStack poses, MultiBufferSource buffers){
        var target = buffers.getBuffer(RenderType.textPolygonOffset(CONTAINER_PREVIEW));
        var matrix = poses.last().pose();
        vertex(target, matrix, surface.topLeft(), 0, 1);
        vertex(target, matrix, surface.bottomLeft(), 0, 0);
        vertex(target, matrix, surface.bottomRight(), 1, 0);
        vertex(target, matrix, surface.topRight(), 1, 1);
        return true;
    }

    private static Session containerSession(net.minecraft.world.level.block.entity.BlockEntity target){
        return CONTAINER_SESSIONS.computeIfAbsent(target, ignored -> { var session = new Session(); session.container = true; return session; });
    }

    // Purge the diagnostic tablet gui projection
    private static void purge(long now) {
        purge(BLOCK_SESSIONS, now);
        purge(ITEM_SESSIONS, now);
        purge(CONTAINER_SESSIONS, now);
    }

    // Purge the diagnostic tablet gui projection
    private static <K> void purge(Map<K, Session> sessions, long now) {
        for (K key : new ArrayList<>(sessions.keySet())) {
            Session session = sessions.get(key);
            if (session != null && now - session.lastUse > SESSION_TIMEOUT_MILLIS) {
                sessions.remove(key);
                session.close();
            }
        }
    }

    // Clear the diagnostic tablet gui projection
    public static void clear() {
        BLOCK_SESSIONS.values().forEach(Session::close);
        ITEM_SESSIONS.values().forEach(Session::close);
        CONTAINER_SESSIONS.values().forEach(Session::close);
        CONTAINER_SESSIONS.clear();
        BLOCK_SESSIONS.clear();
        ITEM_SESSIONS.clear();
    }

    // Track the active session
    private static final class Session {
        private final DisplayFrameDemand demand = new DisplayFrameDemand();
        private boolean container;
        private final RenderSurfaceVisibility visibility = new RenderSurfaceVisibility();
        private final ViewSceneRequests feeds = new ViewSceneRequests();
        // Current screen
        private DiagnosticTabletScreen screen;
        // Current fingerprint
        private int fingerprint = Integer.MIN_VALUE;
        // Current session source
        private Object source;
        // Current session target
        private FramebufferGuiRenderer projection;
        // Current texture
        private ResourceLocation texture;
        // Last frame
        private long lastFrame;
        // Last use
        private long lastUse;

        // Prepare the session
        private boolean prepare(Supplier<DiagnosticTabletScreen> screenFactory,
                                int nextFingerprint, Object nextSource) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return false;
            if (screen == null || fingerprint != nextFingerprint || !Objects.equals(source, nextSource)) {
                if (screen != null) screen.removed();
                screen = screenFactory.get();
                fingerprint = nextFingerprint;
                source = nextSource;
                screen.init(minecraft, DiagnosticTabletScreen.projectionWidth(),
                        DiagnosticTabletScreen.projectionHeight());
                lastFrame = 0L;
            }
            return true;
        }

        // Draw tablet app views without consuming pending world geometry
        private void renderFrame(float partialTick){
            int width = DiagnosticTabletScreen.projectionWidth();
            int height = DiagnosticTabletScreen.projectionHeight();
            if(projection == null){
                texture = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                        "diagnostic_tablet_projection/" + TEXTURE_IDS.incrementAndGet());
                projection = new FramebufferGuiRenderer(texture);
            }
            feeds.record(() -> projection.render(width, height, width, height,
                    graphics -> screen.render(graphics, -10000, -10000, partialTick)));
        }

        // Release the projected app and its owned render surface
        private void close(){
            visibility.close();
            if(screen != null){
                screen.removed();
                screen = null;
            }
            source = null;
            if(projection != null){
                projection.close();
                projection = null;
                texture = null;
            }
        }
    }
}
