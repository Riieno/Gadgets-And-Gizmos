package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplaySurfaceLayout;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.NotationDraftStore;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.display.DisplaySurfaceProjection;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayComputerInputPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

// Map input and rendering between scaled screen space and a live ACC display surface
public final class AccDisplayGuiProjection {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long FRAME_INTERVAL_MILLIS = 50L;
    private static final long SESSION_TIMEOUT_MILLIS = 30_000L;
    private static final float PROJECTED_CONTENT_Z = -0.20F;
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();
    private static final Map<AccDisplayBlockEntity, Session> SESSIONS = new IdentityHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active interaction
    private static Session activeInteraction;
    // Active mouse x
    private static double activeMouseX;
    // Active mouse y
    private static double activeMouseY;
    // Active mouse button
    private static int activeMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    // Shared computer interaction
    private static ComputerInteraction computerInteraction;
    // Last computer interaction hint
    private static long lastComputerInteractionHint;
    // Last purge
    private static long lastPurge;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display gui projection
    private AccDisplayGuiProjection() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ACC display gui projection
    public static boolean render(AccDisplayBlockEntity display, String mode,
                                 int width, int height, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (display == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }
        MultiBufferSource.BufferSource bufferSource =
                buffers instanceof MultiBufferSource.BufferSource direct
                        ? direct : minecraft.renderBuffers().bufferSource();
        Session session = session(display);
        if (!session.prepare(display, mode)) {
            return false;
        }
        long now = Util.getMillis();
        session.lastUse = now;
        if (session.target == null || now - session.lastFrame >= FRAME_INTERVAL_MILLIS) {
            session.renderFrame(mode, partialTick, bufferSource);
            session.lastFrame = now;
        }
        if (session.target == null || session.texture == null) {
            return false;
        }
        var vertices = buffers.getBuffer(RenderType.text(session.texture));
        Matrix4f matrix = poseStack.last().pose();
        vertices.addVertex(matrix, 0.0F, 0.0F, PROJECTED_CONTENT_Z).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, 0.0F, height, PROJECTED_CONTENT_Z).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, width, height, PROJECTED_CONTENT_Z).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F)
                .setLight(0x00F000F0);
        vertices.addVertex(matrix, width, 0.0F, PROJECTED_CONTENT_Z).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F)
                .setLight(0x00F000F0);
        purge(now);
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the ACC display GUI projection
    public static boolean interact(AccDisplayBlockEntity clicked, BlockHitResult hit,
                                   int mouseButton) {
        AccDisplayBlockEntity root = clicked == null ? null : clicked.networkRoot();
        if (root == null || hit == null) {
            return false;
        }
        if ("diagnostic_tablet".equals(root.displayFrame().getString("ActiveSource"))) {
            ScreenPoint point = contentPoint(root, screenPoint(root, clicked, hit));
            return point != null && DiagnosticTabletGuiProjection.interactAccDisplay(
                    root.diagnosticTabletSource(), point.x, point.y);
        }
        if (root.isComputerCraftDisplaySource()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isShiftKeyDown()) {
                toggleComputerInteraction(root);
                return true;
            }
            ScreenPoint point = contentPoint(root, screenPoint(root, clicked, hit));
            if (point == null) return false;
            computerInteraction = new ComputerInteraction(root, mouseButton, point.x, point.y, false);
            sendComputerInput(root, "click", point.x, point.y, mouseButton);
            return true;
        }
        if (!"graph".equals(root.displayFrame().getString("State"))) {
            return false;
        }
        String mode = root.displayFrame().getString("Mode");
        if (!"graph".equals(mode) && !"plotter".equals(mode)) {
            return false;
        }
        Session session = session(root);
        if (!session.prepare(root, mode)) {
            return false;
        }
        ScreenPoint point = screenPoint(root, clicked, hit);
        point = contentPoint(root, point);
        if (point == null) return false;
        double mouseX = point.x * session.guiWidth;
        double mouseY = point.y * session.guiHeight;
        if (activeInteraction != null) {
            releaseActiveInteraction(activeInteraction);
        }
        boolean handled = session.mouseClicked(mode, mouseX, mouseY, mouseButton);
        if (handled && session.openFocusedTextInput(mode)) {
            return true;
        }
        if (handled) {
            activeInteraction = session;
            activeMouseX = mouseX;
            activeMouseY = mouseY;
            activeMouseButton = mouseButton;
        }
        return handled;
    }

    // Handle the client tick event
    public static void onClientTick(ClientTickEvent.Pre evt) {
        long now = Util.getMillis();
        for (Session visible : new ArrayList<>(SESSIONS.values())) {
            if (now - visible.lastUse <= 250L) {
                visible.tick();
            }
        }
        purge(now);
        Session session = activeInteraction;
        if (session == null) {
            tickComputerInteraction();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean held = activeMouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT
                ? minecraft.options.keyAttack.isDown()
                : minecraft.options.keyUse.isDown();
        if (!held) {
            releaseActiveInteraction(session);
            return;
        }
        DisplayHit displayHit = currentDisplayHit();
        if (displayHit == null || displayHit.root != session.display) {
            releaseActiveInteraction(session);
            return;
        }
        ScreenPoint point = screenPoint(displayHit.root, displayHit.clicked, displayHit.hit);
        point = contentPoint(displayHit.root, point);
        if (point == null) {
            releaseActiveInteraction(session);
            return;
        }
        double mouseX = point.x * session.guiWidth;
        double mouseY = point.y * session.guiHeight;
        session.mouseDragged(mouseX, mouseY, activeMouseButton,
                mouseX - activeMouseX, mouseY - activeMouseY);
        activeMouseX = mouseX;
        activeMouseY = mouseY;
        tickComputerInteraction();
    }

    // Update the computer interaction
    private static void tickComputerInteraction() {
        ComputerInteraction interaction = computerInteraction;
        if (interaction == null) return;
        if (interaction.keyboardMode) {
            long now = Util.getMillis();
            if (now - lastComputerInteractionHint >= 1_000L
                    && Minecraft.getInstance().player != null) {
                lastComputerInteractionHint = now;
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("ACC Display interaction mode: Esc or Shift + Use to exit"), true);
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean held = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),
                interaction.mouseButton) == GLFW.GLFW_PRESS;
        DisplayHit hit = currentDisplayHit();
        if (!held || hit == null || hit.root != interaction.display) {
            sendComputerInput(interaction.display, "release", interaction.x, interaction.y,
                    interaction.mouseButton);
            computerInteraction = null;
            return;
        }
        ScreenPoint point = contentPoint(hit.root, screenPoint(hit.root, hit.clicked, hit.hit));
        if (point == null) return;
        if (Math.abs(point.x - interaction.x) > 0.001D || Math.abs(point.y - interaction.y) > 0.001D) {
            sendComputerInput(interaction.display, "drag", point.x, point.y, interaction.mouseButton);
            computerInteraction = new ComputerInteraction(interaction.display, interaction.mouseButton,
                    point.x, point.y, false);
        }
    }

    // Handle the raw key input
    public static boolean onRawKeyInput(int keyCode, int scanCode, int action, int modifiers) {
        ComputerInteraction interaction = computerInteraction;
        Minecraft minecraft = Minecraft.getInstance();
        if (interaction == null || !interaction.keyboardMode || minecraft.screen != null) return false;
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (action == GLFW.GLFW_PRESS) {
                computerInteraction = null;
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("ACC Display interaction mode off"), true);
                }
            }
            return true;
        }
        int key = computerKey(keyCode, scanCode);
        if (key < 0) return true;
        if (action == GLFW.GLFW_RELEASE) {
            sendComputerInput(interaction.display, "key_up", 0.0D, 0.0D, key);
        } else if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            sendComputerInput(interaction.display, "key", 0.0D, 0.0D, key);
            int character = computerCharacter(keyCode, modifiers);
            if (character >= 0) {
                sendComputerInput(interaction.display, "char", 0.0D, 0.0D, character);
            }
        }
        return true;
    }

    // Toggle computer interaction mode
    private static void toggleComputerInteraction(AccDisplayBlockEntity display) {
        if (computerInteraction != null && computerInteraction.display == display) {
            computerInteraction = null;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("ACC Display interaction mode off"), true);
            return;
        }
        computerInteraction = new ComputerInteraction(display, GLFW.GLFW_MOUSE_BUTTON_LEFT,
                0.0D, 0.0D, true);
        for (net.minecraft.client.KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            mapping.consumeClick();
            mapping.setDown(false);
        }
        lastComputerInteractionHint = 0L;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("ACC Display interaction mode: Esc or Shift + Use to exit"), true);
    }

    // Send the computer input
    private static void sendComputerInput(AccDisplayBlockEntity display, String action,
                                          double x, double y, int val) {
        PacketDistributor.sendToServer(new AccDisplayComputerInputPayload(
                MenuConfigTarget.of(display.getBlockPos(), SimulatedHelper.getContainingSubLevelId(display)),
                action, x, y, val));
    }

    // Get the computer key
    private static int computerKey(int key, int scanCode) {
        if (key < 0) return -1;
        String name = GLFW.glfwGetKeyName(key, scanCode);
        if (name != null && name.length() == 1) {
            char character = name.charAt(0);
            if (character >= 'a' && character <= 'z') return Character.toUpperCase(character);
            if (character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9') return character;
        }
        return key;
    }

    // Get the computer character
    private static int computerCharacter(int key, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return (shift ? 'A' : 'a') + key - GLFW.GLFW_KEY_A;
        }
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        String plain = "`1234567890-=[]\\;',./";
        String shifted = "~!@#$%^&*()_+{}|:\"<>?";
        int[] keys = {
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
                GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
                GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
                GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
                GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
                GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_SEMICOLON,
                GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_COMMA,
                GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH
        };
        for (int idx = 0; idx < keys.length; idx++) {
            if (key == keys[idx]) return (shift ? shifted : plain).charAt(idx);
        }
        return -1;
    }

    // Release the active interaction
    private static void releaseActiveInteraction(Session session) {
        session.mouseReleased(activeMouseX, activeMouseY, activeMouseButton);
        activeInteraction = null;
        activeMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    // Handle the mouse scrolling event
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt) {
        DisplayHit displayHit = currentDisplayHit();
        if (displayHit == null || !"graph".equals(displayHit.root.displayFrame().getString("State"))) {
            return;
        }
        String mode = displayHit.root.displayFrame().getString("Mode");
        if (!"graph".equals(mode) && !"plotter".equals(mode)) {
            return;
        }
        Session session = session(displayHit.root);
        if (!session.prepare(displayHit.root, mode)) {
            return;
        }
        ScreenPoint point = screenPoint(displayHit.root, displayHit.clicked, displayHit.hit);
        point = contentPoint(displayHit.root, point);
        if (point == null) return;
        if (session.mouseScrolled(mode, point.x * session.guiWidth,
                point.y * session.guiHeight, evt.getScrollDeltaX(), evt.getScrollDeltaY())) {
            evt.setCanceled(true);
        }
    }

    // Apply the function plotter data
    static void applyFunctionPlotterData(BlockPos pos, UUID subLevelId, String action,
                                         boolean success, String msg,
                                         List<NotationDraftStore.Summary> drafts,
                                         CompoundTag draft, CompoundTag scmModel) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (session.plotter != null && session.controllerTarget != null
                    && session.controllerTarget.pos().equals(pos)
                    && Objects.equals(session.controllerTarget.subLevelId(), subLevelId)) {
                session.plotter.applyServerData(action, success, msg, drafts, draft, scmModel);
            }
        }
    }

    // Apply the discovery results
    static void applyDiscoveryResults(BlockPos pos, UUID subLevelId,
                                      List<ControllerDiscoveryNode> nodes) {
        for (Session session : matchingSessions(pos, subLevelId)) {
            session.graph.applyProjectionDiscoveryResults(nodes);
        }
    }

    // Apply the graph action result
    static void applyGraphActionResult(BlockPos pos, UUID subLevelId, long requestId,
                                       boolean success, String msg, int serverRevision,
                                       boolean saveAttempted, boolean graphSaved,
                                       List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        for (Session session : matchingSessions(pos, subLevelId)) {
            session.graph.applyProjectionGraphActionResult(requestId, success, msg,
                    serverRevision, saveAttempted, graphSaved, diagnostics);
        }
    }

    // Apply the graph history
    static void applyGraphHistory(BlockPos pos, UUID subLevelId,
                                  List<AdvancedGraphVersionHistory.Entry> entries,
                                  String msg, CompoundTag restoredGraph) {
        for (Session session : matchingSessions(pos, subLevelId)) {
            session.graph.applyProjectionGraphHistory(entries, msg, restoredGraph);
        }
    }

    // Apply the shared graph manifests
    static void applySharedGraphManifests(BlockPos pos, UUID subLevelId,
                                          List<ControllerManifestStore.SharedGraphEntry> entries,
                                          String msg, String conflictingName,
                                          CompoundTag graphTag) {
        for (Session session : matchingSessions(pos, subLevelId)) {
            session.graph.applyProjectionSharedGraphManifests(
                    entries, msg, conflictingName, graphTag);
        }
    }

    // Apply the public graph share
    static void applyPublicGraphShare(BlockPos pos, UUID subLevelId,
                                      boolean available, boolean completed,
                                      boolean success, String msg, String url) {
        for (Session session : matchingSessions(pos, subLevelId)) {
            session.graph.applyProjectionPublicGraphShare(
                    available, completed, success, msg, url);
        }
    }

    // Apply the HUD image catalog
    static void applyHudImageCatalog(String uploadedName, String msg,
                                     boolean uploadResult) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (session.graph != null) {
                session.graph.applyProjectionHudImageCatalog(
                        uploadedName, msg, uploadResult);
            }
        }
    }

    // Get the matching sessions
    private static List<Session> matchingSessions(BlockPos pos, UUID subLevelId) {
        return SESSIONS.values().stream()
                .filter(session -> session.graph != null && session.controllerTarget != null
                        && session.controllerTarget.pos().equals(pos)
                        && Objects.equals(session.controllerTarget.subLevelId(), subLevelId))
                .toList();
    }

    // Clear the ACC display gui projection
    public static void clear() {
        if (activeInteraction != null) {
            releaseActiveInteraction(activeInteraction);
        }
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            session.close();
        }
        SESSIONS.clear();
        computerInteraction = null;
    }

    // Get the session
    private static Session session(AccDisplayBlockEntity display) {
        return SESSIONS.computeIfAbsent(display, Session::new);
    }

    // Purge the ACC display gui projection
    private static void purge(long now) {
        if (now - lastPurge < 5_000L) {
            return;
        }
        lastPurge = now;
        List<AccDisplayBlockEntity> stale = SESSIONS.entrySet().stream()
                .filter(entry -> entry.getKey().isRemoved()
                        || now - entry.getValue().lastUse > SESSION_TIMEOUT_MILLIS)
                .map(Map.Entry::getKey).toList();
        for (AccDisplayBlockEntity display : stale) {
            Session removed = SESSIONS.remove(display);
            if (removed != null) {
                removed.close();
            }
        }
    }

    // Get the current display hit
    private static DisplayHit currentDisplayHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(
                minecraft.level, hit.getBlockPos());
        if (!(blockEntity instanceof AccDisplayBlockEntity clicked)) {
            return null;
        }
        AccDisplayBlockEntity root = clicked.networkRoot();
        return root == null ? null : new DisplayHit(root, clicked, hit);
    }

    // Get the screen point
    private static ScreenPoint screenPoint(AccDisplayBlockEntity root,
                                           AccDisplayBlockEntity clicked,
                                           BlockHitResult hit) {
        DisplaySurfaceProjection.VisiblePoint point =
                DisplaySurfaceProjection.normalizedVisiblePoint(
                        root.getBlockPos(), clicked.getBlockPos(), root.screenRight(),
                        SimulatedHelper.toBlockLocalHitPosition(clicked, hit),
                        root.networkWidth(), root.networkHeight(),
                        AccDisplayBlockEntity.PIXELS_PER_BLOCK,
                        AccDisplayBlockEntity.BORDER_PIXELS,
                        AccDisplaySurfaceLayout.visiblePixelsPerRow(
                                root.getBlockState()),
                        AccDisplaySurfaceLayout.srcTopPixels(
                                root.getBlockState()));
        if (!point.inside()) return null;
        return new ScreenPoint(point.x(), point.y());
    }

    // Get the content point
    private static ScreenPoint contentPoint(AccDisplayBlockEntity root, ScreenPoint point) {
        if (point == null) return null;
        CompoundTag frame = root.displayFrame();
        if (frame.getList("Sources", Tag.TAG_COMPOUND).size() > 1) {
            double contentHeight = root.contentHeightFraction();
            if (point.y >= contentHeight) return null;
            point = new ScreenPoint(point.x, point.y / contentHeight);
        }
        if (!frame.contains("ContentNode", Tag.TAG_STRING)) return point;
        double width = Math.max(1.0D, frame.getDouble("ContentWidth"));
        double height = Math.max(1.0D, frame.getDouble("ContentHeight"));
        double scale = Math.max(0.01D, frame.getDouble("ContentScale"));
        double centerX = frame.getDouble("ContentX") + width * 0.5D;
        double centerY = frame.getDouble("ContentY") + height * 0.5D;
        double dx = point.x * 320.0D - centerX;
        double dy = point.y * 180.0D - centerY;
        double rad = Math.toRadians(-frame.getDouble("ContentRotation"));
        double localX = (dx * Math.cos(rad) - dy * Math.sin(rad)) / scale
                + width * 0.5D;
        double localY = (dx * Math.sin(rad) + dy * Math.cos(rad)) / scale
                + height * 0.5D;
        if (localX < 0.0D || localY < 0.0D || localX > width || localY > height) {
            return null;
        }
        return new ScreenPoint(localX / width, localY / height);
    }

    // Track the active session
    private static final class Session {
        // Display
        private final AccDisplayBlockEntity display;
        // Current graph
        private AdvancedContraptionControllerScreen graph;
        // Current plotter
        private FunctionPlotterScreen plotter;
        // Current controller target
        private MenuConfigTarget controllerTarget;
        // Current session target
        private TextureTarget target;
        // Current texture
        private ResourceLocation texture;
        // Current GUI width
        private int guiWidth;
        // Current GUI height
        private int guiHeight;
        // Last frame
        private long lastFrame;
        // Last use
        private long lastUse;

        // Initialize the session
        private Session(AccDisplayBlockEntity display) {
            this.display = display;
        }

        // Prepare the session
        private boolean prepare(AccDisplayBlockEntity requestedDisplay, String mode) {
            Minecraft minecraft = Minecraft.getInstance();
            CompoundTag frame = requestedDisplay.displayFrame();
            if (minecraft.level == null || minecraft.player == null
                    || !frame.contains("ControllerPos")) {
                return false;
            }
            BlockPos pos = BlockPos.of(frame.getLong("ControllerPos"));
            UUID subLevelId = frame.hasUUID("ControllerSubLevel")
                    ? frame.getUUID("ControllerSubLevel") : null;
            MenuConfigTarget nextTarget = MenuConfigTarget.of(pos, subLevelId);
            if (graph == null || !nextTarget.equals(controllerTarget)) {
                BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                        minecraft.level, subLevelId, pos);
                if (!(blockEntity instanceof AdvancedContraptionControllerBlockEntity controller)) {
                    return false;
                }
                closeScreens();
                controllerTarget = nextTarget;
                AdvancedContraptionControllerMenu menu = new AdvancedContraptionControllerMenu(
                        -TEXTURE_IDS.incrementAndGet(), minecraft.player.getInventory(), controller);
                graph = new AdvancedContraptionControllerScreen(menu,
                        minecraft.player.getInventory(), Component.literal("Advanced Contraption Controller"));
                graph.configureForProjection();
                guiWidth = safeGuiDimension(minecraft.getWindow().getGuiScaledWidth(), 320);
                guiHeight = safeGuiDimension(minecraft.getWindow().getGuiScaledHeight(), 240);
                graph.init(minecraft, guiWidth, guiHeight);
            }
            int nextWidth = safeGuiDimension(minecraft.getWindow().getGuiScaledWidth(), guiWidth);
            int nextHeight = safeGuiDimension(minecraft.getWindow().getGuiScaledHeight(), guiHeight);
            if (nextWidth != guiWidth || nextHeight != guiHeight) {
                guiWidth = nextWidth;
                guiHeight = nextHeight;
                graph.resize(minecraft, guiWidth, guiHeight);
                if (plotter != null) {
                    plotter.resize(minecraft, guiWidth, guiHeight);
                }
            }
            graph.updateProjectionGraph(requestedDisplay.graph());
            if ("plotter".equals(mode) && plotter == null) {
                plotter = new FunctionPlotterScreen(graph, controllerTarget);
                plotter.init(minecraft, guiWidth, guiHeight);
            }
            return true;
        }

        // Keep synthetic screen dimensions inside the range accepted by GUI integration mods
        private static int safeGuiDimension(int value, int fallback) {
            int resolved = value > 1 ? value : Math.max(2, fallback);
            return Math.min(9_999_999, resolved);
        }

        // Draw the frame
        private void renderFrame(String mode, float partialTick,
                                 MultiBufferSource.BufferSource bufferSource) {
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.getMainRenderTarget();
            int targetWidth = minecraft.getWindow().getWidth();
            int targetHeight = minecraft.getWindow().getHeight();
            if (targetWidth <= 0 || targetHeight <= 0) {
                return;
            }
            if (target == null) {
                target = new TextureTarget(targetWidth, targetHeight, true, Minecraft.ON_OSX);
                texture = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                        "acc_display_projection/" + TEXTURE_IDS.incrementAndGet());
                minecraft.getTextureManager().register(texture, new TargetTexture(target));
            } else if (target.width != targetWidth || target.height != targetHeight) {
                target.resize(targetWidth, targetHeight, Minecraft.ON_OSX);
            }
            bufferSource.endBatch();
            RenderSystem.backupProjectionMatrix();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                target.setClearColor(0.04F, 0.055F, 0.075F, 1.0F);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                Matrix4f projection = new Matrix4f().setOrtho(
                        0.0F, guiWidth, guiHeight, 0.0F, 1000.0F,
                        net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
                RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
                modelView.translation(0.0F, 0.0F,
                        10000.0F - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
                RenderSystem.applyModelViewMatrix();
                GuiGraphics graphics = new GuiGraphics(minecraft, bufferSource);
                int mouseX = activeInteraction == this ? (int) Math.round(activeMouseX) : -10000;
                int mouseY = activeInteraction == this ? (int) Math.round(activeMouseY) : -10000;
                if ("plotter".equals(mode) && plotter != null) {
                    plotter.renderProjection(graphics, mouseX, mouseY, partialTick);
                } else {
                    graph.renderProjection(graphics, mouseX, mouseY, partialTick);
                }
                graphics.flush();
            } finally {
                target.unbindWrite();
                main.bindWrite(true);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
        }

        // Handle mouse clicked
        private boolean mouseClicked(String mode, double mouseX, double mouseY,
                                     int mouseButton) {
            return "plotter".equals(mode) && plotter != null
                    ? plotter.mouseClicked(mouseX, mouseY, mouseButton)
                    : graph != null && graph.projectionMouseClicked(
                            mouseX, mouseY, mouseButton);
        }

        // Check if this is open focused text input
        private boolean openFocusedTextInput(String mode) {
            Screen projected = "plotter".equals(mode) && plotter != null ? plotter : graph;
            if (!(projected.getFocused() instanceof EditBox editBox)) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ControllerTextInputScreen(
                    null,
                    Component.literal("ACC Display Text Input"),
                    Component.literal("Enter text for the projected field"),
                    editBox.getValue(),
                    val -> {
                        editBox.setValue(val);
                        projected.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                        lastFrame = 0L;
                    },
                    512));
            return true;
        }

        // Update the session
        private void tick() {
            String mode = display.displayFrame().getString("Mode");
            if ("plotter".equals(mode) && plotter != null) {
                plotter.tick();
            } else if (graph != null) {
                graph.tick();
            }
        }

        // Handle mouse dragged
        private void mouseDragged(double mouseX, double mouseY, int mouseButton,
                                  double deltaX, double deltaY) {
            String mode = display.displayFrame().getString("Mode");
            if ("plotter".equals(mode) && plotter != null) {
                plotter.mouseDragged(mouseX, mouseY, mouseButton, deltaX, deltaY);
            } else if (graph != null) {
                graph.projectionMouseDragged(mouseX, mouseY, mouseButton, deltaX, deltaY);
            }
            lastFrame = 0L;
        }

        // Handle mouse released
        private void mouseReleased(double mouseX, double mouseY, int mouseButton) {
            String mode = display.displayFrame().getString("Mode");
            if ("plotter".equals(mode) && plotter != null) {
                plotter.mouseReleased(mouseX, mouseY, mouseButton);
            } else if (graph != null) {
                graph.projectionMouseReleased(mouseX, mouseY, mouseButton);
            }
            lastFrame = 0L;
        }

        // Handle mouse scrolled
        private boolean mouseScrolled(String mode, double mouseX, double mouseY,
                                      double scrollX, double scrollY) {
            boolean handled = "plotter".equals(mode) && plotter != null
                    ? plotter.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                    : graph != null && graph.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            if (handled) {
                lastFrame = 0L;
            }
            return handled;
        }

        // Close the screens
        private void closeScreens() {
            if (plotter != null) {
                plotter.removed();
                plotter = null;
            }
            if (graph != null) {
                graph.removed();
                graph = null;
            }
        }

        // Close the session
        private void close() {
            closeScreens();
            Minecraft minecraft = Minecraft.getInstance();
            if (texture != null) {
                minecraft.getTextureManager().release(texture);
                texture = null;
            }
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
        }
    }

    // Handle the target texture
    private static final class TargetTexture extends AbstractTexture {
        // Target texture target
        private final TextureTarget target;

        // Initialize the target texture
        private TargetTexture(TextureTarget target) {
            this.target = target;
        }

        // Get the id
        @Override
        public int getId() {
            return target.getColorTextureId();
        }

        // Release the id
        @Override
        public void releaseId() {
        }

        // Load the target texture
        @Override
        public void load(ResourceManager resourceManager) throws IOException {
        }

        // Close the target texture
        @Override
        public void close() {
        }
    }

    // Store the screen point
    private record ScreenPoint(double x, double y) {
    }

    // Store the computer interaction
    private record ComputerInteraction(AccDisplayBlockEntity display, int mouseButton,
                                       double x, double y, boolean keyboardMode) {
    }

    // Store the display hit
    private record DisplayHit(AccDisplayBlockEntity root,
                              AccDisplayBlockEntity clicked,
                              BlockHitResult hit) {
    }
}
