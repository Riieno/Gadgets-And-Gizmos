package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
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
    private static final Map<DiagnosticTabletBlockEntity, Session> BLOCK_SESSIONS =
            new IdentityHashMap<>();
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
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || minecraft.player == null || minecraft.level == null) return false;
        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet),
                fingerprint, tablet)) return false;
        Direction facing = tablet.getBlockState().getValue(CTDirectionalBlock.FACING);
        return draw(session, DiagnosticTabletSurface.screen(facing), false, poseStack, buffers);
    }

    // Draw the ACC display
    public static boolean renderAccDisplay(DiagnosticTabletBlockEntity tablet, int width, int height,
                                           float partialTick, PoseStack poseStack,
                                           MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tablet == null || minecraft.player == null || minecraft.level == null) return false;
        Session session = BLOCK_SESSIONS.computeIfAbsent(tablet, ignored -> new Session());
        int fingerprint = DiagnosticTabletData.toTag(tablet.state()).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionBlock(tablet), fingerprint, tablet)) {
            return false;
        }
        long now = Util.getMillis();
        session.lastUse = now;
        if (session.texture == null) return false;
        var vertices = buffers.getBuffer(RenderType.text(session.texture));
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
        Minecraft minecraft = Minecraft.getInstance();
        if (hand == null || stack == null || stack.isEmpty() || minecraft.player == null) return false;
        Session session = ITEM_SESSIONS.computeIfAbsent(hand, ignored -> new Session());
        int fingerprint = DiagnosticTabletData.toTag(DiagnosticTabletData.read(stack)).toString().hashCode();
        if (!session.prepare(() -> DiagnosticTabletScreen.projectionItem(hand, stack),
                fingerprint, stack)) return false;
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
        purge(now);
    }

    // Handle the render frame event
    public static void onRenderFrame(RenderFrameEvent.Pre evt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        long now = Util.getMillis();
        renderFrames(BLOCK_SESSIONS, now,
                evt.getPartialTick().getGameTimeDeltaPartialTick(true));
        renderFrames(ITEM_SESSIONS, now,
                evt.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    // Update the diagnostic tablet gui projection
    private static <K> void tick(Map<K, Session> sessions, long now) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (session.screen != null && now - session.lastUse <= SESSION_TIMEOUT_MILLIS) {
                session.screen.tick();
            }
        }
    }

    // Draw the frames
    private static <K> void renderFrames(Map<K, Session> sessions, long now, float partialTick) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (session.screen != null && now - session.lastUse <= SESSION_TIMEOUT_MILLIS
                    && (session.projection == null || !session.projection.isReady() || now - session.lastFrame >= FRAME_INTERVAL_MILLIS)) {
                session.renderFrame(partialTick);
                session.lastFrame = now;
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
        var vertices = buffers.getBuffer(RenderType.textPolygonOffset(session.texture));
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

    // Purge the diagnostic tablet gui projection
    private static void purge(long now) {
        purge(BLOCK_SESSIONS, now);
        purge(ITEM_SESSIONS, now);
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
        BLOCK_SESSIONS.clear();
        ITEM_SESSIONS.clear();
    }

    // Track the active session
    private static final class Session {
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
            if (screen == null || fingerprint != nextFingerprint || source != nextSource) {
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
            projection.render(width, height, width, height,
                    graphics -> screen.render(graphics, -10000, -10000, partialTick));
        }

        // Release the projected app and its owned render surface
        private void close(){
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
