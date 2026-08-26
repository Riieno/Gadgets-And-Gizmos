package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.LecternPortableContraptionController;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageSource;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.client.render.SimulatedDiagramMiniRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.PhysicsGogglesOverlayRegistry;
import com.rieno.gadgetsandgizmos.lib.display.DisplayWidgetProjection;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerProfilerPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudInteractionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGogglesDataPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGogglesDataRequestPayload;
import com.rieno.gadgetsandgizmos.util.CTPropulsionTelemetry;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

// Collect and render the client-only physics information shown by the goggles
public final class CTPhysicsGogglesClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CATEGORY = "key.categories.createthrusters";
    private static final ResourceLocation PROPULSION_GROUP = ResourceLocation.fromNamespaceAndPath("sable", "propulsion");
    private static final ResourceLocation GRAVITY_GROUP = ResourceLocation.fromNamespaceAndPath("sable", "gravity");
    private static final int PANEL_BG = 0xAA101820;
    private static final int PANEL_BG_STRONG = 0xD5101820;
    private static final int PANEL_BORDER = 0xCC58D7FF;
    private static final int PANEL_BORDER_ACTIVE = 0xFFF4D35E;
    private static final int TEXT = 0xFFE8F7FF;
    private static final int SUBTLE = 0xFF9FB3C4;
    private static final int MUTED = 0xFF5F7485;
    private static final int ACCENT = 0xFF58D7FF;
    private static final int GRAVITY = 0xFF4366D5;
    private static final int DIAGRAM_MINIMAP_WIDTH = 360;
    private static final int DIAGRAM_MINIMAP_HEIGHT = 250;
    private static final int DIAGRAM_MIN_WIDTH = 160;
    private static final int DIAGRAM_MAX_WIDTH = 720;
    private static final double DIAGRAM_ASPECT = 1.44D;
    private static final int CUSTOM_HUD_MIN_WIDTH = 60;
    private static final int CUSTOM_HUD_MIN_HEIGHT = 26;
    private static final int RESIZE_HANDLE_SIZE = 7;
    private static final int ACTIVE_DATA_REQUEST_INTERVAL_TICKS = 2;
    private static final int IDLE_DATA_REQUEST_INTERVAL_TICKS = 10;
    private static final int PROFILER_REPORT_INTERVAL_TICKS = 5;
    private static final long HUD_CONTEXT_GRACE_MS = 500L;
    private static final String OVERLAY_LAYOUT_FILE = "createthrusters_physics_goggles_overlays.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final KeyMapping TOGGLE_FORCE_GIZMO = new KeyMapping(
            "key.createthrusters.physics_goggles.toggle_force_gizmo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);
    private static final KeyMapping TOGGLE_DIAGRAM = new KeyMapping(
            "key.createthrusters.physics_goggles.toggle_diagram",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);
    private static final KeyMapping TOGGLE_PROPULSION = new KeyMapping(
            "key.createthrusters.physics_goggles.toggle_propulsion",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);
    private static final KeyMapping DIAGRAM_INTERACTIVE_MODE = new KeyMapping(
            "key.createthrusters.physics_goggles.diagram_interactive_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);
    private static final KeyMapping EDIT_OVERLAYS_MODE = new KeyMapping(
            "key.createthrusters.physics_goggles.edit_overlays_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private static final Map<OverlayId, OverlayBox> OVERLAY_BOXES = new EnumMap<>(OverlayId.class);
    private static final Map<String, OverlayBox> CUSTOM_HUD_BOXES = new HashMap<>();
    private static final SimulatedDiagramMiniRenderer DIAGRAM_RENDERER = new SimulatedDiagramMiniRenderer();
    private static final List<HudInteractionTarget> HUD_INTERACTION_TARGETS = new ArrayList<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared requested sub-level
    private static UUID requestedSubLevel;
    // Shared request cooldown
    private static int requestCooldown;
    // Shared latest data
    private static PhysicsGogglesDataPayload latestData;
    // Shared latest data millis
    private static long latestDataMillis;
    // Last context millis
    private static long lastContextMillis;
    // Tracks whether overlay layout is loaded
    private static boolean overlayLayoutLoaded;
    // Shared profiler report cooldown
    private static int profilerReportCooldown;
    // Shared portable controller pair id
    private static UUID portableControllerPairId;
    // Shared portable controller stack
    private static ItemStack portableControllerStack = ItemStack.EMPTY;
    // Shared portable controller pos
    private static BlockPos portableControllerPos;
    // Shared portable controller
    private static AdvancedContraptionControllerBlockEntity portableController;
    // Tracks whether portable controller pair authorized is set
    private static boolean portableControllerPairAuthorized;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT physics goggles client
    private CTPhysicsGogglesClient() {
    }

    // Register the key mappings
    public static void registerKeyMappings(RegisterKeyMappingsEvent evt) {
        evt.register(TOGGLE_FORCE_GIZMO);
        evt.register(TOGGLE_DIAGRAM);
        evt.register(TOGGLE_PROPULSION);
        evt.register(DIAGRAM_INTERACTIVE_MODE);
        evt.register(EDIT_OVERLAYS_MODE);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the CT physics goggles client
    public static void tick() {
        loadOverlayLayoutIfNeeded();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clearState();
            return;
        }

        while (TOGGLE_FORCE_GIZMO.consumeClick()) {
            boolean visible = toggleOverlayVisibility(OverlayId.FORCE_GIZMO);
            player.displayClientMessage(Component.translatable(visible
                    ? "createthrusters.physics_goggles.force_gizmo.visible"
                    : "createthrusters.physics_goggles.force_gizmo.hidden"), true);
        }
        while (TOGGLE_DIAGRAM.consumeClick()) {
            boolean visible = toggleOverlayVisibility(OverlayId.DIAGRAM);
            player.displayClientMessage(Component.translatable(visible
                    ? "createthrusters.physics_goggles.diagram.visible"
                    : "createthrusters.physics_goggles.diagram.hidden"), true);
        }
        while (TOGGLE_PROPULSION.consumeClick()) {
            boolean visible = toggleOverlayVisibility(OverlayId.PROPULSION);
            player.displayClientMessage(Component.translatable(visible
                    ? "createthrusters.physics_goggles.propulsion.visible"
                    : "createthrusters.physics_goggles.propulsion.hidden"), true);
        }
        while (DIAGRAM_INTERACTIVE_MODE.consumeClick()) {
            openInteractionScreen(OverlayMode.DIAGRAM);
        }
        while (EDIT_OVERLAYS_MODE.consumeClick()) {
            openInteractionScreen(OverlayMode.EDIT);
        }

        if (!isWearingPhysicsGoggles(player)) {
            requestedSubLevel = null;
            latestData = null;
            profilerReportCooldown = 0;
            clearPortableController();
            closeInteractionScreen(minecraft);
            return;
        }

        reportBoundCtrlProfiler(minecraft);

        UUID subLevel = resolveContextSubLevel(minecraft);
        if (subLevel != null) {
            lastContextMillis = System.currentTimeMillis();
        }
        if (subLevel == null && isContextGraceActive()) {
            subLevel = requestedSubLevel;
        }
        if (subLevel == null) {
            requestedSubLevel = null;
            latestData = null;
            return;
        }

        if (!subLevel.equals(requestedSubLevel)) {
            requestedSubLevel = subLevel;
            requestCooldown = 0;
            latestData = null;
        }

        if (requestCooldown-- <= 0) {
            requestCooldown = isOverlayVisible(OverlayId.DIAGRAM) || isOverlayVisible(OverlayId.FORCE_GIZMO)
                    ? ACTIVE_DATA_REQUEST_INTERVAL_TICKS
                    : IDLE_DATA_REQUEST_INTERVAL_TICKS;
            PacketDistributor.sendToServer(new PhysicsGogglesDataRequestPayload(subLevel));
        }

        if (isOverlayVisible(OverlayId.DIAGRAM) && latestData != null) {
            DIAGRAM_RENDERER.tickHosted(requestedSubLevel, latestData);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the data
    public static void applyData(PhysicsGogglesDataPayload payload) {
        latestData = payload;
        latestDataMillis = System.currentTimeMillis();
    }

    // Handle the render gui event
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !isWearingPhysicsGoggles(player)) {
            return;
        }

        if (minecraft.screen != null && !(minecraft.screen instanceof OverlayInteractionScreen)) {
            return;
        }

        if (minecraft.screen instanceof OverlayInteractionScreen) {
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        renderOverlay(evt.getGuiGraphics(), OverlayMode.NONE, -1, -1, partialTick);
        PhysicsGogglesOverlayRegistry.render(new PhysicsGogglesOverlayRegistry.Context(
                evt.getGuiGraphics(), player, minecraft.level, getLookedAtBlockEntity(minecraft),
                resolveContextSubLevel(minecraft), partialTick));
    }

    // Open the interaction screen
    private static void openInteractionScreen(OverlayMode mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isWearingPhysicsGoggles(minecraft.player)) {
            return;
        }
        if (minecraft.screen instanceof OverlayInteractionScreen screen && screen.mode == mode) {
            minecraft.setScreen(null);
            return;
        }
        minecraft.setScreen(new OverlayInteractionScreen(mode));
        minecraft.player.displayClientMessage(Component.translatable(mode == OverlayMode.DIAGRAM
                ? "createthrusters.physics_goggles.diagram_interactive.enabled"
                : "createthrusters.physics_goggles.edit.enabled"), true);
    }

    // Close the interaction screen
    private static void closeInteractionScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof OverlayInteractionScreen) {
            minecraft.setScreen(null);
        }
    }

    // Draw the overlay
    private static void renderOverlay(GuiGraphics graphics, OverlayMode mode, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Rect> occupied = new ArrayList<>();
        occupied.add(reservedGoggleTooltipRect(graphics));
        HUD_INTERACTION_TARGETS.clear();

        List<String> propulsionLines = getPropulsionLookLines(minecraft);
        if (propulsionLines != null && (mode == OverlayMode.EDIT || isOverlayVisible(OverlayId.PROPULSION))) {
            Rect rect = placeOverlay(graphics, OverlayId.PROPULSION, measurePanel(propulsionLines), occupied);
            if (rect != null) {
                drawPanel(graphics, rect.x(), rect.y(), propulsionLines, mode == OverlayMode.EDIT && rect.contains(mouseX, mouseY));
                renderVisibilityEditor(graphics, rect, OVERLAY_BOXES.get(OverlayId.PROPULSION), mode,
                        isOverlayVisible(OverlayId.PROPULSION));
                occupied.add(rect.padded(4));
            }
        }

        if ((mode == OverlayMode.EDIT || isOverlayVisible(OverlayId.FORCE_GIZMO)) && hasFreshData()) {
            Rect rect = placeOverlay(graphics, OverlayId.FORCE_GIZMO, new Size(124, 62), occupied);
            if (rect != null) {
                renderForceGizmo(graphics, rect, mode == OverlayMode.EDIT && rect.contains(mouseX, mouseY));
                renderVisibilityEditor(graphics, rect, OVERLAY_BOXES.get(OverlayId.FORCE_GIZMO), mode,
                        isOverlayVisible(OverlayId.FORCE_GIZMO));
                occupied.add(rect.padded(4));
            }
        }

        if ((mode == OverlayMode.EDIT || isOverlayVisible(OverlayId.DIAGRAM)) && hasFreshData()) {
            Rect rect = placeOverlay(graphics, OverlayId.DIAGRAM, new Size(DIAGRAM_MINIMAP_WIDTH, DIAGRAM_MINIMAP_HEIGHT), occupied);
            if (rect != null) {
                if (!DIAGRAM_RENDERER.render(graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                        mouseX, mouseY, partialTick, requestedSubLevel, latestData)) {
                    drawDiagramUnavailable(graphics, rect);
                } else if (mode == OverlayMode.EDIT && rect.contains(mouseX, mouseY)) {
                    outline(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BORDER_ACTIVE);
                }
                drawDiagramActionHints(graphics, rect);
                if (mode == OverlayMode.EDIT) {
                    renderResizeHandles(graphics, rect);
                }
                renderVisibilityEditor(graphics, rect, OVERLAY_BOXES.get(OverlayId.DIAGRAM), mode,
                        isOverlayVisible(OverlayId.DIAGRAM));
                occupied.add(rect.padded(4));
            }
        }

        BoundControllerBinding boundController = boundControllerBinding(minecraft);
        if (boundController != null) {
            renderCustomHudElements(graphics, boundController, occupied, mode, mouseX, mouseY);
        }

        if (mode == OverlayMode.EDIT) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("createthrusters.physics_goggles.edit.active"),
                    8, graphics.guiHeight() - 18, PANEL_BORDER_ACTIVE, false);
        }
    }

    // Check if the overlay is visible
    private static boolean isOverlayVisible(OverlayId id) {
        return OVERLAY_BOXES.computeIfAbsent(id, ignored -> new OverlayBox()).visible;
    }

    // Toggle a physics goggles overlay
    private static boolean toggleOverlayVisibility(OverlayId id) {
        OverlayBox box = OVERLAY_BOXES.computeIfAbsent(id, ignored -> new OverlayBox());
        box.visible = !box.visible;
        saveOverlayLayout();
        return box.visible;
    }

    // Draw the visibility editor
    private static void renderVisibilityEditor(GuiGraphics graphics, Rect rect, OverlayBox box,
                                               OverlayMode mode, boolean effectivelyVisible) {
        if (mode != OverlayMode.EDIT || box == null) {
            return;
        }
        if (!effectivelyVisible) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0x99101820);
        }
        Rect btn = visibilityButtonRect(rect);
        graphics.fill(btn.x(), btn.y(), btn.right(), btn.bottom(),
                box.visible ? 0xDD315A45 : 0xDD633B3B);
        outline(graphics, btn.x(), btn.y(), btn.right(), btn.bottom(), PANEL_BORDER_ACTIVE);
        graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("createthrusters.physics_goggles.edit.toggle_visibility"),
                btn.x() + btn.width() / 2, btn.y() + 3, 0xFFFFFFFF);
    }

    // Get the visibility button rect
    private static Rect visibilityButtonRect(Rect rect) {
        Font font = Minecraft.getInstance().font;
        int width = Math.min(rect.width() - 8, Math.max(18,
                font.width(Component.translatable("createthrusters.physics_goggles.edit.toggle_visibility")) + 8));
        return new Rect(rect.right() - width - 4, rect.y() + 4, width, 14);
    }

    // Draw the unavailable diagram state
    private static void drawDiagramUnavailable(GuiGraphics graphics, Rect rect) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BG_STRONG);
        outline(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BORDER);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("createthrusters.physics_goggles.diagram_unavailable"),
                rect.x() + 8, rect.y() + 8, TEXT, false);
    }

    // Draw the diagram action hints
    private static void drawDiagramActionHints(GuiGraphics graphics, Rect rect) {
        Font font = Minecraft.getInstance().font;
        List<String> lines = List.of(
                "Press [" + DIAGRAM_INTERACTIVE_MODE.getTranslatedKeyMessage().getString() + "] to interact",
                "Press [" + EDIT_OVERLAYS_MODE.getTranslatedKeyMessage().getString() + "] to edit");
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int x = Mth.clamp(rect.x() + Math.max(6, (rect.width() - width) / 2),
                0, Math.max(0, graphics.guiWidth() - width - 2));
        int y = rect.bottom() + 4;
        if (y + 20 > graphics.guiHeight()) {
            y = rect.bottom() - 24;
        }
        graphics.fill(x - 4, y - 2, x + width + 4, y + 20, 0x7F101820);
        graphics.drawString(font, lines.get(0), x, y, SUBTLE, false);
        graphics.drawString(font, lines.get(1), x, y + 10, SUBTLE, false);
    }

    // Get the propulsion look lines
    private static List<String> getPropulsionLookLines(Minecraft minecraft) {
        BlockEntity blockEntity = getLookedAtBlockEntity(minecraft);
        if (blockEntity != null) {
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
            Vec3 dir = thruster.getWorldThrustDirection();
            return List.of(
                Component.translatable("createthrusters.physics_goggles.propulsion").getString(),
                Component.translatable("createthrusters.physics_goggles.thrust",
                    CTPropulsionTelemetry.thrustComponent(thruster.getRealThrust()).getString()).getString(),
                Component.translatable("createthrusters.physics_goggles.lift",
                    CTPropulsionTelemetry.liftComponent(thruster.getLiftCapacity()).getString()).getString(),
                Component.translatable("createthrusters.physics_goggles.direction", vectorString(dir)).getString());
            }
            if (blockEntity instanceof ThrusterBearingBlockEntity bearing && !bearing.getAttachedThrustersById().isEmpty()) {
            return List.of(
                Component.translatable("createthrusters.physics_goggles.assembly_propulsion").getString(),
                Component.translatable("createthrusters.physics_goggles.thrust",
                    CTPropulsionTelemetry.thrustComponent(bearing.getAssemblyRealThrust()).getString()).getString(),
                Component.translatable("createthrusters.physics_goggles.lift",
                    CTPropulsionTelemetry.liftComponent(bearing.getAssemblyLiftCapacity()).getString()).getString(),
                Component.translatable("createthrusters.physics_goggles.thruster_count",
                    bearing.getAttachedThrustersById().size()).getString());
            }
            if (blockEntity instanceof BlockEntityPropeller propeller) {
            return List.of(
                Component.translatable("createthrusters.physics_goggles.propulsion").getString(),
                Component.translatable("createthrusters.physics_goggles.thrust",
                    CTPropulsionTelemetry.thrustComponent(CTPropulsionTelemetry.getRealThrust(propeller)).getString()).getString(),
                Component.translatable("createthrusters.physics_goggles.airflow", format(propeller.getAirflow())).getString(),
                Component.translatable("createthrusters.physics_goggles.air_pressure", format(propeller.getCurrentAirPressure())).getString());
            }
        }

        if (!hasFreshData()) {
            return null;
        }

        UUID activeSubLevel = requestedSubLevel;
        if (activeSubLevel == null || latestData == null || !activeSubLevel.equals(latestData.subLevelId())) {
            return null;
        }

        Vec3 propulsion = totalForce(PROPULSION_GROUP);
        int propulsionSources = countForceEntries(PROPULSION_GROUP);
        return List.of(
            Component.translatable("createthrusters.physics_goggles.propulsion").getString(),
                "Sublevel " + shortId(activeSubLevel),
            Component.translatable("createthrusters.physics_goggles.thrust",
                CTPropulsionTelemetry.thrustComponent(propulsion.length()).getString()).getString(),
            "Sources: " + propulsionSources,
            Component.translatable("createthrusters.physics_goggles.direction", vectorString(propulsion)).getString());
    }

    // Check if the context grace is active
    private static boolean isContextGraceActive() {
        return requestedSubLevel != null && System.currentTimeMillis() - lastContextMillis <= HUD_CONTEXT_GRACE_MS;
    }

        // Count the force entries
        private static int countForceEntries(ResourceLocation groupId) {
        if (latestData == null) {
            return 0;
        }
        int count = 0;
        for (PhysicsGogglesDataPayload.ForceVector force : latestData.forces()) {
            if (groupId.equals(force.groupId())) {
            count++;
            }
        }
        return count;
        }

        // Get the short id
        private static String shortId(UUID uuid) {
        String val = uuid.toString();
        return val.substring(0, Math.min(8, val.length()));
        }

    // Draw the force gizmo
    private static void renderForceGizmo(GuiGraphics graphics, Rect rect, boolean highlighted) {
        Vec3 propulsion = totalForce(PROPULSION_GROUP);
        Vec3 gravity = totalForce(GRAVITY_GROUP);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BG);
        outline(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), highlighted ? PANEL_BORDER_ACTIVE : PANEL_BORDER);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("createthrusters.physics_goggles.force_gizmo"), rect.x() + 5, rect.y() + 5, TEXT, false);

        drawForceArrow(graphics, rect.x() + 37, rect.y() + 35, propulsion, ACCENT);
        drawForceArrow(graphics, rect.x() + 87, rect.y() + 35, gravity, GRAVITY);
        graphics.drawString(Minecraft.getInstance().font, "P " + format(propulsion.length()), rect.x() + 13, rect.y() + 47, SUBTLE, false);
        graphics.drawString(Minecraft.getInstance().font, "G " + format(gravity.length()), rect.x() + 63, rect.y() + 47, SUBTLE, false);
    }

    // Place the overlay
    private static Rect placeOverlay(GuiGraphics graphics, OverlayId id, Size size, List<Rect> occupied) {
        OverlayBox box = OVERLAY_BOXES.computeIfAbsent(id, ignored -> new OverlayBox());
        if (box.userPlaced) {
            int width = id == OverlayId.DIAGRAM && box.width > 0 ? box.width : size.width();
            int height = id == OverlayId.DIAGRAM && box.height > 0 ? box.height : size.height();
            Rect saved = clampToScreen(new Rect(box.x, box.y, width, height), graphics.guiWidth(), graphics.guiHeight());
            box.set(saved);
            return saved;
        }

        Rect preferred = fallbackPositions(graphics, id, size).getFirst();
        Rect resolved = clampToScreen(preferred, graphics.guiWidth(), graphics.guiHeight());
        box.set(resolved);
        return resolved;
    }

    // Draw the custom HUD elements
    private static void renderCustomHudElements(GuiGraphics graphics, BoundControllerBinding binding,
                                                List<Rect> occupied, OverlayMode mode, int mouseX, int mouseY) {
        AdvancedContraptionControllerBlockEntity controller = binding.controller();
        int idx = 0;
        for (AdvancedGraphDocument.Node node : controller.getActiveGraph().nodes()) {
            if (!isCustomHudNode(node)) {
                continue;
            }
            boolean runtimeVisible = hudVisible(controller, node);
            if (mode != OverlayMode.EDIT && !runtimeVisible) {
                continue;
            }
            boolean advanced = "advanced_hud_element".equals(node.type());
            List<String> lines = advanced ? List.of() : customHudLines(controller, node);
            Size measured = advanced ? advancedHudSize(node) : measurePanel(lines);
            Rect rect = placeCustomHud(graphics, node.id(), measured, occupied, idx++);
            if (rect == null) {
                continue;
            }
            OverlayBox box = CUSTOM_HUD_BOXES.get(node.id());
            if (mode != OverlayMode.EDIT && (box == null || !box.visible)) {
                continue;
            }
            if (advanced) {
                drawAdvancedHudPanel(graphics, binding, node, rect, mode);
            } else {
                drawCustomHudPanel(graphics, rect, lines, mode == OverlayMode.EDIT && rect.contains(mouseX, mouseY));
            }
            if (mode == OverlayMode.EDIT) {
                renderResizeHandles(graphics, rect);
            }
            renderVisibilityEditor(graphics, rect, box, mode,
                    runtimeVisible && box != null && box.visible);
            occupied.add(rect.padded(4));
        }
        CUSTOM_HUD_BOXES.keySet().removeIf(id -> controller.getActiveGraph().nodes().stream()
                .noneMatch(node -> isCustomHudNode(node) && node.id().equals(id)));
    }

    // Check if this is a custom HUD node
    private static boolean isCustomHudNode(AdvancedGraphDocument.Node node) {
        return node != null && ("hud_element".equals(node.type()) || "advanced_hud_element".equals(node.type()));
    }

    // Get the advanced HUD size
    private static Size advancedHudSize(AdvancedGraphDocument.Node node) {
        int width = Math.max(CUSTOM_HUD_MIN_WIDTH, node.data().getInt("WidgetWidth"));
        int height = Math.max(CUSTOM_HUD_MIN_HEIGHT, node.data().getInt("WidgetHeight"));
        return new Size(width, height);
    }

    // Draw the advanced HUD panel
    private static void drawAdvancedHudPanel(GuiGraphics graphics, BoundControllerBinding binding,
                                             AdvancedGraphDocument.Node node, Rect rect, OverlayMode mode) {
        AdvancedContraptionControllerBlockEntity controller = binding.controller();
        int baseWidth = Math.max(1, node.data().getInt("WidgetWidth"));
        int baseHeight = Math.max(1, node.data().getInt("WidgetHeight"));
        double scaleX = rect.width() / (double) baseWidth;
        double scaleY = rect.height() / (double) baseHeight;
        double layoutScale = Math.min(scaleX, scaleY);
        ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
        for (int i = 0; i < elements.size(); i++) {
            CompoundTag sourceElement = elements.getCompound(i);
            CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                    sourceElement, port -> hudInputValue(controller, node, port));
            AdvancedHudElementStyle.applyDefaults(elm);
            if (elm.contains("Visible", Tag.TAG_BYTE) && !elm.getBoolean("Visible")) {
                continue;
            }
            int x = rect.x() + (int) Math.round(elm.getInt("X") * scaleX);
            int y = rect.y() + (int) Math.round(elm.getInt("Y") * scaleY);
            int w = Math.max(1, (int) Math.round(elm.getInt("W") * scaleX));
            int h = Math.max(1, (int) Math.round(elm.getInt("H") * scaleY));
            double elementScale = elm.contains("Scale", Tag.TAG_DOUBLE)
                    ? Math.max(0.01D, elm.getDouble("Scale")) : 1.0D;
            float rotation = (float) elm.getDouble("Rotation");
            graphics.pose().pushPose();
            graphics.pose().translate(x + w / 2.0D, y + h / 2.0D, 0.0D);
            if (rotation != 0.0F) {
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
            }
            graphics.pose().scale((float) elementScale, (float) elementScale, 1.0F);
            graphics.pose().translate(-(x + w / 2.0D), -(y + h / 2.0D), 0.0D);
            switch (elm.getString("Type")) {
                case "box" -> AdvancedHudElementRenderer.drawBox(
                        graphics, elm, x, y, w, h, layoutScale);
                case "image" -> drawAdvancedHudImage(graphics, elm,
                        hudImageInputText(controller, node,
                                elm.getString("Port").isBlank() ? "value" : elm.getString("Port")),
                        x, y, w, h);
                case "value" -> AdvancedHudElementRenderer.drawText(
                        graphics, Minecraft.getInstance().font, elm,
                        hudInputText(controller, node,
                                elm.getString("Port").isBlank() ? "value" : elm.getString("Port")),
                        x, y, w, h, layoutScale);
                case "button", "toggle", "slider" -> drawAdvHudInteractive(
                        graphics, controller, node, elm, x, y, w, h, layoutScale);
                default -> AdvancedHudElementRenderer.drawText(
                        graphics, Minecraft.getInstance().font, elm, elm.getString("Text"),
                        x, y, w, h, layoutScale);
            }
            graphics.pose().popPose();
            if (mode == OverlayMode.DIAGRAM && isInteractiveHudElement(elm)
                    && binding.pairId() != null && !elm.getString("InteractionId").isBlank()) {
                HUD_INTERACTION_TARGETS.add(new HudInteractionTarget(
                        binding.target(), binding.pairId(), node.id(),
                        elm.getString("InteractionId"), elm.getString("Type"),
                        new Rect(x, y, w, h),
                        elementScale, rotation,
                        elm.getDouble("Min"), elm.getDouble("Max"), elm.getDouble("Step")));
            }
        }
    }

    // Check if this is interactive HUD element
    private static boolean isInteractiveHudElement(CompoundTag elm) {
        return switch (elm.getString("Type")) {
            case "button", "toggle", "slider" -> true;
            default -> false;
        };
    }

    // Draw the adv HUD interactive
    private static void drawAdvHudInteractive(
            GuiGraphics graphics, AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, CompoundTag elm,
            int x, int y, int width, int height, double layoutScale) {
        String type = elm.getString("Type");
        String valuePort = elm.getString("ValuePort");
        AdvancedGraphDocument.Value val = valuePort.isBlank()
                ? null : hudOutputValue(controller, node, valuePort);
        boolean toggleActive = "toggle".equals(type)
                && (val == null ? elm.getBoolean("Value") : val.asBoolean());
        double current = val == null ? elm.getDouble("Value") : val.asNumber();
        AdvancedHudInteractiveStyleRenderer.draw(
                graphics, Minecraft.getInstance().font, elm,
                x, y, width, height, layoutScale, current, toggleActive, false);
    }

    // Draw the advanced HUD image
    private static void drawAdvancedHudImage(GuiGraphics graphics, CompoundTag elm, String inputTexture,
                                             int x, int y, int width, int height) {
        String textureId = AdvancedHudImageSource.withFallback(inputTexture, elm.getString("Texture"));
        if (AdvancedHudImageClient.draw(graphics, textureId, x, y, width, height)) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0x66374756);
        outline(graphics, x, y, x + width, y + height, PANEL_BORDER);
    }

    // Get the custom HUD lines
    private static List<String> customHudLines(AdvancedContraptionControllerBlockEntity controller,
                                               AdvancedGraphDocument.Node node) {
        String label = hudInputText(controller, node, "label");
        if (label.isBlank()) {
            label = node.data().getString("Label");
        }
        if (label.isBlank()) {
            label = "HUD";
        }
        List<String> lines = new ArrayList<>();
        lines.add(label);
        for (var entry : AdvancedGraphCatalog.inputs(node).entrySet()) {
            String port = entry.getKey();
            if ("label".equals(port) || "visible".equals(port) || "exec".equals(entry.getValue())) {
                continue;
            }
            String text = hudInputText(controller, node, port);
            if (!text.isBlank()) {
                String fieldLabel = node.data().getCompound("HudFieldLabels").getString(port);
                lines.add(fieldLabel.isBlank() ? text : fieldLabel + ": " + text);
            }
        }
        if (lines.size() == 1) {
            String text = hudInputText(controller, node, "value");
            if (!text.isBlank()) {
                lines.add("Value: " + text);
            }
        }
        return lines;
    }

    // Place the custom HUD
    private static Rect placeCustomHud(GuiGraphics graphics, String id, Size size, List<Rect> occupied, int idx) {
        OverlayBox box = CUSTOM_HUD_BOXES.computeIfAbsent(id, ignored -> new OverlayBox());
        int width = Math.max(CUSTOM_HUD_MIN_WIDTH, box.userPlaced && box.width > 0 ? box.width : size.width());
        int height = Math.max(CUSTOM_HUD_MIN_HEIGHT, box.userPlaced && box.height > 0 ? box.height : size.height());
        if (box.userPlaced) {
            Rect saved = clampToScreen(new Rect(box.x, box.y, width, height), graphics.guiWidth(), graphics.guiHeight());
            box.set(saved);
            return saved;
        }
        Rect preferred = new Rect(8, 8 + idx * (height + 6), width, height);
        Rect resolved = resolveCollision(preferred, occupied, graphics.guiWidth(), graphics.guiHeight());
        if (resolved == null) {
            resolved = clampToScreen(preferred, graphics.guiWidth(), graphics.guiHeight());
        }
        box.set(resolved);
        return resolved;
    }

    // Draw the custom HUD panel
    private static void drawCustomHudPanel(GuiGraphics graphics, Rect rect, List<String> lines, boolean highlighted) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BG);
        outline(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), highlighted ? PANEL_BORDER_ACTIVE : PANEL_BORDER);
        Font font = Minecraft.getInstance().font;
        int maxTextWidth = Math.max(0, rect.width() - 10);
        for (int idx = 0; idx < lines.size(); idx++) {
            String text = font.plainSubstrByWidth(lines.get(idx), maxTextWidth);
            graphics.drawString(font, text, rect.x() + 5, rect.y() + 5 + idx * 10,
                    idx == 0 ? TEXT : SUBTLE, false);
        }
    }

    // Report the bound controller profiler sample
    private static void reportBoundCtrlProfiler(Minecraft minecraft) {
        if (profilerReportCooldown-- > 0) {
            return;
        }
        profilerReportCooldown = PROFILER_REPORT_INTERVAL_TICKS - 1;
        BoundControllerBinding binding = boundControllerBinding(minecraft);
        if (binding == null || binding.controller().getActiveGraph().nodes().stream()
                .noneMatch(node -> node != null && node.type().startsWith("profiler_"))) {
            return;
        }
        int fps = minecraft.getFps();
        long frameTimeNanos = minecraft.getFrameTimeNs();
        binding.controller().updateClientProfilerSample(fps, frameTimeNanos);
        PacketDistributor.sendToServer(new AdvancedControllerProfilerPayload(
                binding.target(), fps, frameTimeNanos));
    }

    // Get the bound controller
    private static AdvancedContraptionControllerBlockEntity boundController(Minecraft minecraft) {
        BoundControllerBinding binding = boundControllerBinding(minecraft);
        return binding == null ? null : binding.controller();
    }

    // Get the bound controller binding
    private static BoundControllerBinding boundControllerBinding(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        ItemStack goggles = CTPhysicsGogglesClientUtil.getWornPhysicsGogglesStack(minecraft.player);
        if (goggles.isEmpty()) {
            return null;
        }
        CompoundTag root = goggles.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
        if (root.isEmpty()) {
            return null;
        }
        UUID subLevelId = root.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL)
                ? root.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL) : null;
        UUID pairId = root.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)
                ? root.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID) : null;
        BlockPos pos = new BlockPos(
                root.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_X),
                root.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Y),
                root.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Z));
        AdvancedContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                minecraft.level, subLevelId, pos,
                AdvancedContraptionControllerBlockEntity.class);
        if (controller != null) {
            AnalogueContraptionControllerClientHandler.applyCachedAdvancedGraphSnapshot(
                    controller, pos, subLevelId);
        } else {
            controller = resolvePortableBoundController(minecraft, null);
        }
        return controller == null ? null : new BoundControllerBinding(
                controller, MenuConfigTarget.of(pos, subLevelId), pairId);
    }

    // Resolve the portable bound controller
    static AdvancedContraptionControllerBlockEntity resolvePortableBoundController(
            Minecraft minecraft, Set<UUID> expectedPairIds) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            clearPortableController();
            return null;
        }
        ItemStack goggles = CTPhysicsGogglesClientUtil.getWornPhysicsGogglesStack(minecraft.player);
        CompoundTag binding = goggles.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
        UUID pairId = binding.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)
                ? binding.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID) : null;
        if (pairId == null || expectedPairIds != null && !expectedPairIds.contains(pairId)) {
            clearPortableController();
            return null;
        }

        BlockPos bindingPos = new BlockPos(
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_X),
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Y),
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Z));
        PortableControllerCandidate candidate = portableCtrlCandidate(
                minecraft, bindingPos, pairId);
        if (candidate == null) {
            clearPortableController();
            return null;
        }
        boolean sameCachedSource = portableController != null
                && pairId.equals(portableControllerPairId)
                && portableControllerStack == candidate.stack()
                && candidate.pos().equals(portableControllerPos)
                && portableController.getLevel() == minecraft.level;
        if (!PortableContraptionControllerItem.canUseGogglesPairCandidate(
                pairId,
                expectedPairIds,
                candidate.pairVerified(),
                portableControllerPairAuthorized,
                sameCachedSource)) {
            clearPortableController();
            return null;
        }
        if (sameCachedSource) {
            portableControllerPairAuthorized = true;
            return portableController;
        }
        return portableController(
                candidate.stack(), candidate.pos(), pairId, minecraft);
    }

    // Get the portable ctrl candidate
    private static PortableControllerCandidate portableCtrlCandidate(
            Minecraft minecraft, BlockPos bindingPos, UUID pairId
    ) {
        ItemStack lecternStack = LecternPortableContraptionController.getControllerStack(
                minecraft.level, bindingPos);
        if (isMatchingPortableCtrl(lecternStack, pairId)) {
            return new PortableControllerCandidate(lecternStack, bindingPos, true);
        }
        Inventory inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isMatchingPortableCtrl(stack, pairId)) {
                return new PortableControllerCandidate(stack, BlockPos.ZERO, true);
            }
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        if (isMatchingPortableCtrl(offhand, pairId)) {
            return new PortableControllerCandidate(offhand, BlockPos.ZERO, true);
        }

        if (isAdvPortableCtrl(lecternStack)) {
            return new PortableControllerCandidate(lecternStack, bindingPos, false);
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isAdvPortableCtrl(stack)) {
                return new PortableControllerCandidate(stack, BlockPos.ZERO, false);
            }
        }
        return isAdvPortableCtrl(offhand)
                ? new PortableControllerCandidate(offhand, BlockPos.ZERO, false)
                : null;
    }

    // Check if the portable ctrl is matching
    private static boolean isMatchingPortableCtrl(ItemStack stack, UUID pairId) {
        return isAdvPortableCtrl(stack)
                && AdvancedContraptionControllerBlockEntity.controllerDataHasGogglesTrackerPair(
                stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).getUnsafe(), pairId);
    }

    // Check if this is an advanced portable controller
    private static boolean isAdvPortableCtrl(ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof PortableContraptionControllerItem portable
                && portable.isAdvanced();
    }

    // Get the portable controller
    private static AdvancedContraptionControllerBlockEntity portableController(
            ItemStack stack, BlockPos pos, UUID pairId, Minecraft minecraft) {
        var reconstructed = PortableContraptionControllerItem.createControllerFromStack(
                stack, minecraft.level, true, pos);
        if (!(reconstructed instanceof AdvancedContraptionControllerBlockEntity advanced)) {
            clearPortableController();
            return null;
        }
        portableControllerPairId = pairId;
        portableControllerStack = stack;
        portableControllerPos = pos.immutable();
        portableController = advanced;
        portableControllerPairAuthorized = true;
        return advanced;
    }

    // Clear the portable controller
    private static void clearPortableController() {
        portableControllerPairId = null;
        portableControllerStack = ItemStack.EMPTY;
        portableControllerPos = null;
        portableController = null;
        portableControllerPairAuthorized = false;
    }

    // Store the portable controller candidate
    private record PortableControllerCandidate(
            ItemStack stack, BlockPos pos, boolean pairVerified
    ) {
    }

    // Get the graph value text
    private static String graphValueText(AdvancedGraphDocument.Value val) {
        if (val == null) {
            return "";
        }
        return switch (val.type()) {
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> {
                double num = val.asNumber();
                yield Double.isFinite(num) && num == Math.rint(num)
                        ? Long.toString(Math.round(num)) : format(num);
            }
            case "string", "direction" -> val.asString();
            default -> graphPayloadText(val.payload());
        };
    }

    // Get the graph payload text
    private static String graphPayloadText(CompoundTag payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        if (payload.contains("Type", Tag.TAG_STRING) && payload.contains("Payload", Tag.TAG_COMPOUND)) {
            return graphValueText(new AdvancedGraphDocument.Value(
                    payload.getString("Type"), payload.getCompound("Payload")));
        }
        if (payload.getAllKeys().size() == 1 && payload.contains("Value")) {
            return wrappedPayloadValueText(payload);
        }
        return payload.toString();
    }

    // Get the wrapped payload value text
    private static String wrappedPayloadValueText(CompoundTag payload) {
        if (payload.contains("Value", Tag.TAG_STRING)) {
            return payload.getString("Value");
        }
        if (payload.contains("Value", Tag.TAG_BYTE)) {
            return Boolean.toString(payload.getBoolean("Value"));
        }
        if (payload.contains("Value", Tag.TAG_SHORT) || payload.contains("Value", Tag.TAG_INT)
                || payload.contains("Value", Tag.TAG_LONG) || payload.contains("Value", Tag.TAG_FLOAT)
                || payload.contains("Value", Tag.TAG_DOUBLE)) {
            double num = payload.getDouble("Value");
            return Double.isFinite(num) && num == Math.rint(num)
                    ? Long.toString(Math.round(num)) : format(num);
        }
        Tag tag = payload.get("Value");
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("Type", Tag.TAG_STRING) && compound.contains("Payload", Tag.TAG_COMPOUND)) {
                return graphValueText(new AdvancedGraphDocument.Value(
                        compound.getString("Type"), compound.getCompound("Payload")));
            }
            return compound.toString();
        }
        return tag == null ? "" : tag.toString();
    }

    // Get the HUD input text
    private static String hudInputText(AdvancedContraptionControllerBlockEntity controller,
                                       AdvancedGraphDocument.Node node, String port) {
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        if (liveValue != null) {
            return graphValueText(liveValue);
        }
        return graphValueText(controller.previewGraphInput(controller.getActiveGraph(), node, port));
    }

    // Get the HUD input value
    private static AdvancedGraphDocument.Value hudInputValue(
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, String port) {
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        return liveValue == null
                ? controller.previewGraphInput(controller.getActiveGraph(), node, port)
                : graphValue(liveValue);
    }

    // Get the HUD output value
    private static AdvancedGraphDocument.Value hudOutputValue(
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, String port) {
        return graphValue(controller.getGraphLiveOutput(node.id(), port));
    }

    // Get the graph value
    private static AdvancedGraphDocument.Value graphValue(AdvancedGraphLiveValue liveValue) {
        if (liveValue == null) {
            return null;
        }
        return switch (liveValue.type()) {
            case "number" -> AdvancedGraphDocument.Value.number(liveValue.numberValue());
            case "boolean" -> AdvancedGraphDocument.Value.bool(liveValue.booleanValue());
            case "direction" -> AdvancedGraphDocument.Value.direction(liveValue.textValue());
            default -> AdvancedGraphDocument.Value.string(liveValue.textValue());
        };
    }

    // Send the HUD boolean
    private static void sendHudBoolean(HudInteractionTarget target, boolean val) {
        PacketDistributor.sendToServer(AdvancedHudInteractionPayload.bool(
                target.target(), target.pairId(), target.nodeId(), target.interactionId(), val));
    }

    // Send the HUD slider
    private static void sendHudSlider(HudInteractionTarget target, double mouseX, double mouseY) {
        double minimum = target.minimum();
        double maximum = target.maximum();
        if (!(maximum > minimum)) {
            maximum = minimum + 1.0D;
        }
        double fraction = target.horizontalFraction(mouseX, mouseY);
        double val = minimum + (maximum - minimum) * fraction;
        if (target.step() > 0.0D && Double.isFinite(target.step())) {
            val = minimum + Math.round((val - minimum) / target.step()) * target.step();
        }
        PacketDistributor.sendToServer(AdvancedHudInteractionPayload.number(
                target.target(), target.pairId(), target.nodeId(), target.interactionId(),
                Mth.clamp(val, minimum, maximum)));
    }

    // Get the HUD image input text
    private static String hudImageInputText(AdvancedContraptionControllerBlockEntity controller,
                                            AdvancedGraphDocument.Node node, String port) {
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        if (liveValue != null) {
            return AdvancedHudImageSource.fromPort(liveValue);
        }
        return AdvancedHudImageSource.fromPort(
                controller.previewGraphInput(controller.getActiveGraph(), node, port));
    }

    // Check if the HUD is visible
    private static boolean hudVisible(AdvancedContraptionControllerBlockEntity controller,
                                      AdvancedGraphDocument.Node node) {
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), "visible");
        if (liveValue != null) {
            return switch (liveValue.type()) {
                case "boolean" -> liveValue.booleanValue();
                case "number" -> liveValue.numberValue() != 0;
                case "string" -> !liveValue.textValue().isBlank()
                        && !"false".equalsIgnoreCase(liveValue.textValue());
                default -> true;
            };
        }
        AdvancedGraphDocument.Value preview = controller.previewGraphInput(
                controller.getActiveGraph(), node, "visible");
        if (preview == null) return true;
        return switch (preview.type()) {
            case "boolean" -> preview.asBoolean();
            case "number" -> preview.asNumber() != 0;
            case "string" -> !preview.asString().isBlank() && !"false".equalsIgnoreCase(preview.asString());
            default -> true;
        };
    }

    // Get the graph value text
    private static String graphValueText(AdvancedGraphLiveValue val) {
        if (val == null) {
            return "";
        }
        return switch (val.type()) {
            case "boolean" -> Boolean.toString(val.booleanValue());
            case "number" -> {
                double num = val.numberValue();
                yield Double.isFinite(num) && num == Math.rint(num)
                        ? Long.toString(Math.round(num)) : format(num);
            }
            default -> val.textValue();
        };
    }

    // Draw the resize handles
    private static void renderResizeHandles(GuiGraphics graphics, Rect rect) {
        outline(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_BORDER_ACTIVE);
        for (ResizeHandle handle : ResizeHandle.values()) {
            Rect handleRect = resizeHandleRect(rect, handle);
            graphics.fill(handleRect.x(), handleRect.y(), handleRect.right(), handleRect.bottom(), PANEL_BG_STRONG);
            outline(graphics, handleRect.x(), handleRect.y(), handleRect.right(), handleRect.bottom(), PANEL_BORDER_ACTIVE);
        }
    }

    // Resize the handle rect
    private static Rect resizeHandleRect(Rect rect, ResizeHandle handle) {
        int half = RESIZE_HANDLE_SIZE / 2;
        int centerX = switch (handle) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> rect.x();
            case TOP, BOTTOM -> rect.x() + rect.width() / 2;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> rect.right();
        };
        int centerY = switch (handle) {
            case TOP_LEFT, TOP, TOP_RIGHT -> rect.y();
            case LEFT, RIGHT -> rect.y() + rect.height() / 2;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> rect.bottom();
        };
        return new Rect(centerX - half, centerY - half, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
    }

    // Resize the handle
    private static ResizeHandle resizeHandleAt(Rect rect, double mouseX, double mouseY) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            if (resizeHandleRect(rect, handle).padded(2).contains(mouseX, mouseY)) {
                return handle;
            }
        }
        return null;
    }

    // Get the fallback positions
    private static List<Rect> fallbackPositions(GuiGraphics graphics, OverlayId id, Size size) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        return switch (id) {
            case PROPULSION -> List.of(
                    new Rect(centerX - size.width() - 18, centerY + 18, size.width(), size.height()),
                    new Rect(8, centerY + 18, size.width(), size.height()),
                    new Rect(8, height - size.height() - 28, size.width(), size.height()));
            case FORCE_GIZMO -> List.of(
                    new Rect(centerX - size.width() / 2, centerY + 58, size.width(), size.height()),
                    new Rect(width - size.width() - 8, height - size.height() - 28, size.width(), size.height()),
                    new Rect(8, height - size.height() - 28, size.width(), size.height()));
            case DIAGRAM -> List.of(
                    new Rect(width - size.width() - 8, 8, size.width(), size.height()),
                    new Rect(8, 8, size.width(), size.height()),
                    new Rect(width - size.width() - 8, height - size.height() - 28, size.width(), size.height()));
        };
    }

    // Resolve the collision
    private static Rect resolveCollision(Rect preferred, List<Rect> occupied, int screenWidth, int screenHeight) {
        Rect best = clampToScreen(preferred, screenWidth, screenHeight);
        if (!intersectsAny(best, occupied)) {
            return best;
        }
        for (int y = 8; y <= Math.max(8, screenHeight - preferred.height() - 8); y += 8) {
            for (int x = 8; x <= Math.max(8, screenWidth - preferred.width() - 8); x += 8) {
                Rect candidate = new Rect(x, y, preferred.width(), preferred.height());
                if (!intersectsAny(candidate, occupied)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // Clamp the screen
    private static Rect clampToScreen(Rect rect, int width, int height) {
        int x = Mth.clamp(rect.x(), 0, Math.max(0, width - rect.width()));
        int y = Mth.clamp(rect.y(), 0, Math.max(0, height - rect.height()));
        return new Rect(x, y, rect.width(), rect.height());
    }

    // Check if the rectangle intersects any occupied area
    private static boolean intersectsAny(Rect rect, List<Rect> occupied) {
        for (Rect other : occupied) {
            if (rect.intersects(other)) {
                return true;
            }
        }
        return false;
    }

    // Get the reserved goggle tooltip rect
    private static Rect reservedGoggleTooltipRect(GuiGraphics graphics) {
        int x = graphics.guiWidth() / 2 + 14;
        int y = graphics.guiHeight() / 2 - 18;
        int width = Math.min(260, Math.max(120, graphics.guiWidth() - x - 8));
        int height = Math.min(190, Math.max(80, graphics.guiHeight() - y - 20));
        return new Rect(x, y, width, height).padded(6);
    }

    // Get the measure panel
    private static Size measurePanel(List<String> lines) {
        Font font = Minecraft.getInstance().font;
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + 10;
        int height = lines.size() * 10 + 7;
        return new Size(width, height);
    }

    // Get the total force for a group
    private static Vec3 totalForce(ResourceLocation groupId) {
        if (latestData == null) {
            return Vec3.ZERO;
        }
        Vec3 total = Vec3.ZERO;
        for (PhysicsGogglesDataPayload.ForceVector force : latestData.forces()) {
            if (groupId.equals(force.groupId())) {
                total = total.add(force.forceX(), force.forceY(), force.forceZ());
            }
        }
        return total;
    }

    // Check if this has fresh data
    private static boolean hasFreshData() {
        return latestData != null && System.currentTimeMillis() - latestDataMillis <= 1500L;
    }

    // Draw the force arrow
    private static void drawForceArrow(GuiGraphics graphics, int originX, int originY, Vec3 force, int col) {
        if (force.lengthSqr() < 1.0E-6D) {
            graphics.fill(originX - 2, originY - 2, originX + 3, originY + 3, col);
            return;
        }
        Vec3 normal = force.normalize();
        int dx = (int) Math.round(normal.x() * 16.0D);
        int dy = (int) Math.round(-normal.y() * 16.0D);
        drawLine(graphics, originX, originY, originX + dx, originY + dy, col);
        graphics.fill(originX + dx - 2, originY + dy - 2, originX + dx + 3, originY + dy + 3, col);
    }

    // Draw the line
    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int col) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, col);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    // Draw the panel
    private static void drawPanel(GuiGraphics graphics, int x, int y, List<String> lines, boolean highlighted) {
        Size size = measurePanel(lines);
        graphics.fill(x, y, x + size.width(), y + size.height(), PANEL_BG);
        outline(graphics, x, y, x + size.width(), y + size.height(), highlighted ? PANEL_BORDER_ACTIVE : PANEL_BORDER);
        Font font = Minecraft.getInstance().font;
        for (int idx = 0; idx < lines.size(); idx++) {
            graphics.drawString(font, lines.get(idx), x + 5, y + 5 + idx * 10, idx == 0 ? TEXT : SUBTLE, false);
        }
    }

    // Draw the cursor mode tooltip
    private static void drawCursorModeTooltip(GuiGraphics graphics, OverlayMode mode, int mouseX, int mouseY) {
        List<String> lines;
        if (mode == OverlayMode.EDIT) {
            lines = List.of(
                    Component.translatable("createthrusters.physics_goggles.edit.tooltip.title").getString(),
                    Component.translatable("createthrusters.physics_goggles.edit.tooltip.drag").getString(),
                    Component.translatable("createthrusters.physics_goggles.edit.tooltip.resize").getString(),
                    Component.translatable("createthrusters.physics_goggles.mode.tooltip.exit",
                            EDIT_OVERLAYS_MODE.getTranslatedKeyMessage().getString()).getString());
        } else if (mode == OverlayMode.DIAGRAM) {
            lines = List.of(
                    Component.translatable("createthrusters.physics_goggles.diagram_interactive.tooltip.title").getString(),
                    Component.translatable("createthrusters.physics_goggles.diagram_interactive.tooltip.click").getString(),
                    Component.translatable("createthrusters.physics_goggles.diagram_interactive.tooltip.zoom").getString(),
                    Component.translatable("createthrusters.physics_goggles.mode.tooltip.exit",
                            DIAGRAM_INTERACTIVE_MODE.getTranslatedKeyMessage().getString()).getString());
        } else {
            return;
        }

        Size size = measurePanel(lines);
        int x = Mth.clamp(mouseX + 12, 0, Math.max(0, graphics.guiWidth() - size.width()));
        int y = Mth.clamp(mouseY + 12, 0, Math.max(0, graphics.guiHeight() - size.height()));
        drawPanel(graphics, x, y, lines, true);
    }

    // Draw the physics goggle outline
    private static void outline(GuiGraphics graphics, int x1, int y1, int x2, int y2, int col) {
        graphics.hLine(x1, x2 - 1, y1, col);
        graphics.hLine(x1, x2 - 1, y2 - 1, col);
        graphics.vLine(x1, y1, y2 - 1, col);
        graphics.vLine(x2 - 1, y1, y2 - 1, col);
    }

    // Resolve the context sublevel
    private static UUID resolveContextSubLevel(Minecraft minecraft) {
        BlockEntity blockEntity = getLookedAtBlockEntity(minecraft);
        UUID blockEntitySubLevel = SimulatedHelper.getContainingSubLevelId(blockEntity);
        if (blockEntitySubLevel != null) {
            return blockEntitySubLevel;
        }
        if (minecraft.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() != HitResult.Type.MISS) {
            UUID hitSubLevel = SableLevelApi.containingId(minecraft.level, blockHitResult.getLocation());
            if (hitSubLevel != null) {
                return hitSubLevel;
            }
        }
        return SableLevelApi.containingId(minecraft.level, minecraft.player == null ? null : minecraft.player.position());
    }

    // Get the looked at block entity
    private static BlockEntity getLookedAtBlockEntity(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
            return null;
        }
        return SimulatedHelper.findBlockEntityIncludingSubLevels(minecraft.level, blockHitResult.getBlockPos());
    }

    // Check if the player is wearing physics goggles
    private static boolean isWearingPhysicsGoggles(LocalPlayer player) {
        return CTPhysicsGogglesClientUtil.isWearingPhysicsGoggles(player);
    }

    // Clear the state
    private static void clearState() {
        requestedSubLevel = null;
        latestData = null;
        requestCooldown = 0;
        profilerReportCooldown = 0;
        lastContextMillis = 0L;
        clearPortableController();
        AdvancedHudImageClient.clearServerImages();
        DIAGRAM_RENDERER.close();
    }

    // Load the overlay layout if needed
    private static void loadOverlayLayoutIfNeeded() {
        if (overlayLayoutLoaded) {
            return;
        }
        overlayLayoutLoaded = true;

        Path file = overlayLayoutPath();
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) {
                return;
            }
            for (OverlayId id : OverlayId.values()) {
                JsonElement elm = root.get(id.name());
                if (!(elm instanceof JsonObject json)) {
                    continue;
                }
                OverlayBox box = OVERLAY_BOXES.computeIfAbsent(id, ignored -> new OverlayBox());
                box.x = getInt(json, "x", 0);
                box.y = getInt(json, "y", 0);
                box.width = getInt(json, "width", 0);
                box.height = getInt(json, "height", 0);
                box.userPlaced = json.has("userPlaced") && json.get("userPlaced").getAsBoolean();
                box.visible = !json.has("visible") || json.get("visible").getAsBoolean();
            }
            JsonElement customElement = root.get("CUSTOM_HUD");
            if (customElement instanceof JsonObject customHud) {
                for (Map.Entry<String, JsonElement> entry : customHud.entrySet()) {
                    if (!(entry.getValue() instanceof JsonObject json)) {
                        continue;
                    }
                    OverlayBox box = CUSTOM_HUD_BOXES.computeIfAbsent(entry.getKey(), ignored -> new OverlayBox());
                    box.x = getInt(json, "x", 0);
                    box.y = getInt(json, "y", 0);
                    box.width = getInt(json, "width", 0);
                    box.height = getInt(json, "height", 0);
                    box.userPlaced = json.has("userPlaced") && json.get("userPlaced").getAsBoolean();
                    box.visible = !json.has("visible") || json.get("visible").getAsBoolean();
                }
            }
        } catch (Exception ignored) {
        }
    }

    // Save the overlay layout
    private static void saveOverlayLayout() {
        Path file = overlayLayoutPath();
        if (file == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<OverlayId, OverlayBox> entry : OVERLAY_BOXES.entrySet()) {
                OverlayBox box = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("x", box.x);
                json.addProperty("y", box.y);
                json.addProperty("width", box.width);
                json.addProperty("height", box.height);
                json.addProperty("userPlaced", box.userPlaced);
                json.addProperty("visible", box.visible);
                root.add(entry.getKey().name(), json);
            }
            JsonObject customHud = new JsonObject();
            for (Map.Entry<String, OverlayBox> entry : CUSTOM_HUD_BOXES.entrySet()) {
                OverlayBox box = entry.getValue();
                JsonObject json = new JsonObject();
                json.addProperty("x", box.x);
                json.addProperty("y", box.y);
                json.addProperty("width", box.width);
                json.addProperty("height", box.height);
                json.addProperty("userPlaced", box.userPlaced);
                json.addProperty("visible", box.visible);
                customHud.add(entry.getKey(), json);
            }
            root.add("CUSTOM_HUD", customHud);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    // Get the overlay layout path
    private static Path overlayLayoutPath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath().resolve("config").resolve(OVERLAY_LAYOUT_FILE);
    }

    // Get the int
    private static int getInt(JsonObject json, String key, int fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // Get the vector string
    private static String vectorString(Vec3 vector) {
        return format(vector.x()) + ", " + format(vector.y()) + ", " + format(vector.z());
    }

    // Format the CT physics goggles client
    private static String format(double val) {
        return String.format(Locale.ROOT, "%,.2f", val);
    }

    // Define the overlay id values
    private enum OverlayId {
        PROPULSION,
        FORCE_GIZMO,
        DIAGRAM
    }

    // Define the overlay mode values
    private enum OverlayMode {
        NONE,
        DIAGRAM,
        EDIT
    }

    // Define the resize handle values
    private enum ResizeHandle {
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        RIGHT,
        BOTTOM_RIGHT,
        BOTTOM,
        BOTTOM_LEFT,
        LEFT
    }

    // Store the size
    private record Size(int width, int height) {
    }

    // Store the bound controller binding
    private record BoundControllerBinding(AdvancedContraptionControllerBlockEntity controller,
                                          MenuConfigTarget target, UUID pairId) {
    }

    // Store the HUD interaction target
    private record HudInteractionTarget(MenuConfigTarget target, UUID pairId, String nodeId,
                                         String interactionId, String type, Rect rect,
                                         double scale, double rotation,
                                         double minimum, double maximum, double step) {
        // Check if the HUD interaction contains the point
        private boolean contains(double mouseX, double mouseY) {
            DisplayWidgetProjection.Bounds bounds = projectionBounds();
            return DisplayWidgetProjection.unproject(
                    bounds, mouseX, mouseY, scale, rotation).isInside(bounds);
        }

        // Get the horizontal interaction fraction
        private double horizontalFraction(double mouseX, double mouseY) {
            DisplayWidgetProjection.Bounds bounds = projectionBounds();
            return DisplayWidgetProjection.unproject(
                    bounds, mouseX, mouseY, scale, rotation).horizontalFraction(bounds);
        }

        // Get the reusable widget projection bounds
        private DisplayWidgetProjection.Bounds projectionBounds() {
            return new DisplayWidgetProjection.Bounds(
                    rect.x(), rect.y(), rect.width(), rect.height());
        }
    }

    // Store the rect
    private record Rect(int x, int y, int width, int height) {
        // Get the right
        int right() {
            return x + width;
        }

        // Get the bottom
        int bottom() {
            return y + height;
        }

        // Check if this contains the value
        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        // Check if the rectangles intersect
        boolean intersects(Rect other) {
            return x < other.right() && right() > other.x() && y < other.bottom() && bottom() > other.y();
        }

        // Get the padded
        Rect padded(int padding) {
            return new Rect(x - padding, y - padding, width + padding * 2, height + padding * 2);
        }
    }

    // Handle the overlay box
    private static final class OverlayBox {
        // Current overlay box X coordinate
        private int x;
        // Current overlay box Y coordinate
        private int y;
        // Current overlay box width
        private int width;
        // Current overlay box height
        private int height;
        // Tracks whether user placed is set
        private boolean userPlaced;
        // Tracks whether overlay box is visible
        private boolean visible = true;

        // Set the overlay box
        private void set(Rect rect) {
            x = rect.x();
            y = rect.y();
            width = rect.width();
            height = rect.height();
        }

        // Get the rect
        private Rect rect() {
            return new Rect(x, y, width, height);
        }
    }

    // Draw and handle overlay interaction
    private static final class OverlayInteractionScreen extends Screen {
        // Overlay interaction mode
        private final OverlayMode mode;
        // Current dragging
        private OverlayId dragging;
        // Current dragging custom HUD
        private String draggingCustomHud;
        // Current resizing diagram
        private ResizeHandle resizingDiagram;
        // Current resizing custom HUD
        private String resizingCustomHud;
        // Current resizing custom HUD handle
        private ResizeHandle resizingCustomHudHandle;
        // Current resize start rect
        private Rect resizeStartRect;
        // Current resize start mouse x
        private int resizeStartMouseX;
        // Current resize start mouse y
        private int resizeStartMouseY;
        // Current drag offset x
        private int dragOffsetX;
        // Current drag offset y
        private int dragOffsetY;
        // Key close guard tick count
        private int keyCloseGuardTicks = 2;
        // Current pressed HUD interaction
        private HudInteractionTarget pressedHudInteraction;

        // Initialize the overlay interaction
        private OverlayInteractionScreen(OverlayMode mode) {
            super(Component.translatable(mode == OverlayMode.DIAGRAM
                    ? "createthrusters.physics_goggles.diagram_interactive.title"
                    : "createthrusters.physics_goggles.edit.title"));
            this.mode = mode;
        }

        // Draw the overlay interaction
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderOverlay(graphics, mode, mouseX, mouseY, partialTick);
            drawCursorModeTooltip(graphics, mode, mouseX, mouseY);
        }

        // Update the overlay interaction
        @Override
        public void tick() {

            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !isWearingPhysicsGoggles(player)) {
                Minecraft.getInstance().setScreen(null);
                return;
            }
            if (keyCloseGuardTicks > 0) {
                keyCloseGuardTicks--;
            }
        }

        // Handle mouse clicked
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // ------------------------------------INPUT CHECK------------------------------------
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // ------------------------------------DIAGRAM INPUT------------------------------------
            if (mode == OverlayMode.DIAGRAM) {
                for (int idx = HUD_INTERACTION_TARGETS.size() - 1; idx >= 0; idx--) {
                    HudInteractionTarget target = HUD_INTERACTION_TARGETS.get(idx);
                    if (!target.contains(mouseX, mouseY)) {
                        continue;
                    }
                    switch (target.type()) {
                        case "button" -> sendHudBoolean(target, true);
                        case "toggle" -> sendHudBoolean(target, true);
                        case "slider" -> {
                            pressedHudInteraction = target;
                            sendHudSlider(target, mouseX, mouseY);
                        }
                        default -> {
                            continue;
                        }
                    }
                    return true;
                }
                OverlayBox diagram = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
                if (diagram != null && diagram.rect().contains(mouseX, mouseY)
                        && DIAGRAM_RENDERER.mouseClicked(
                        diagram.x, diagram.y, diagram.width, diagram.height,
                        mouseX, mouseY, button, requestedSubLevel, latestData)) {
                    return true;
                }
            }
            // ------------------------------------EDITOR INPUT------------------------------------
            if (mode == OverlayMode.EDIT) {
                List<String> customIds = new ArrayList<>(CUSTOM_HUD_BOXES.keySet());
                for (int idx = customIds.size() - 1; idx >= 0; idx--) {
                    OverlayBox box = CUSTOM_HUD_BOXES.get(customIds.get(idx));
                    if (box != null && visibilityButtonRect(box.rect()).contains(mouseX, mouseY)) {
                        box.visible = !box.visible;
                        saveOverlayLayout();
                        return true;
                    }
                }
                for (OverlayId id : List.of(OverlayId.DIAGRAM, OverlayId.FORCE_GIZMO, OverlayId.PROPULSION)) {
                    OverlayBox box = OVERLAY_BOXES.get(id);
                    if (box != null && visibilityButtonRect(box.rect()).contains(mouseX, mouseY)) {
                        box.visible = !box.visible;
                        saveOverlayLayout();
                        return true;
                    }
                }
                for (int idx = customIds.size() - 1; idx >= 0; idx--) {
                    String id = customIds.get(idx);
                    OverlayBox box = CUSTOM_HUD_BOXES.get(id);
                    if (box == null) {
                        continue;
                    }
                    ResizeHandle handle = resizeHandleAt(box.rect(), mouseX, mouseY);
                    if (handle != null) {
                        resizingCustomHud = id;
                        resizingCustomHudHandle = handle;
                        resizeStartRect = box.rect();
                        resizeStartMouseX = (int) mouseX;
                        resizeStartMouseY = (int) mouseY;
                        return true;
                    }
                }
                for (int idx = customIds.size() - 1; idx >= 0; idx--) {
                    String id = customIds.get(idx);
                    OverlayBox box = CUSTOM_HUD_BOXES.get(id);
                    if (box != null && box.rect().contains(mouseX, mouseY)) {
                        draggingCustomHud = id;
                        dragOffsetX = (int) mouseX - box.x;
                        dragOffsetY = (int) mouseY - box.y;
                        return true;
                    }
                }
                OverlayBox diagram = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
                if (diagram != null) {
                    ResizeHandle handle = resizeHandleAt(diagram.rect(), mouseX, mouseY);
                    if (handle != null) {
                        resizingDiagram = handle;
                        resizeStartRect = diagram.rect();
                        resizeStartMouseX = (int) mouseX;
                        resizeStartMouseY = (int) mouseY;
                        return true;
                    }
                }
                for (OverlayId id : List.of(OverlayId.DIAGRAM, OverlayId.FORCE_GIZMO, OverlayId.PROPULSION)) {
                    OverlayBox box = OVERLAY_BOXES.get(id);
                    if (box != null && box.rect().contains(mouseX, mouseY)) {
                        dragging = id;
                        dragOffsetX = (int) mouseX - box.x;
                        dragOffsetY = (int) mouseY - box.y;
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Handle mouse dragged
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
            if (mode == OverlayMode.DIAGRAM) {
                if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedHudInteraction != null
                        && "slider".equals(pressedHudInteraction.type())) {
                    sendHudSlider(pressedHudInteraction, mouseX, mouseY);
                    return true;
                }
                OverlayBox diagram = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
                if (diagram != null && diagram.rect().contains(mouseX, mouseY)
                        && DIAGRAM_RENDERER.mouseDragged(
                        diagram.x, diagram.y, diagram.width, diagram.height,
                        mouseX, mouseY, btn, dragX, dragY, requestedSubLevel, latestData)) {
                    return true;
                }
            }
            if (mode != OverlayMode.EDIT || btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
            }
            if (resizingCustomHud != null) {
                resizeCustomHud(mouseX, mouseY);
                return true;
            }
            if (draggingCustomHud != null) {
                OverlayBox box = CUSTOM_HUD_BOXES.get(draggingCustomHud);
                if (box == null) {
                    return true;
                }
                Rect candidate = clampToScreen(new Rect((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY,
                        box.width, box.height), this.width, this.height);
                box.set(candidate);
                box.userPlaced = true;
                return true;
            }
            if (dragging == null) {
                if (resizingDiagram != null) {
                    resizeDiagram(mouseX, mouseY);
                    return true;
                }
                return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
            }
            OverlayBox box = OVERLAY_BOXES.get(dragging);
            if (box == null) {
                return true;
            }
            Rect candidate = clampToScreen(new Rect((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY, box.width, box.height),
                    this.width, this.height);

            box.set(candidate);
            box.userPlaced = true;
            return true;
        }

        // Handle mouse released
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int btn) {
            if (mode == OverlayMode.DIAGRAM) {
                if (pressedHudInteraction != null) {
                    if ("slider".equals(pressedHudInteraction.type())) {
                        sendHudSlider(pressedHudInteraction, mouseX, mouseY);
                    }
                    pressedHudInteraction = null;
                    return true;
                }
                OverlayBox diagram = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
                if (diagram != null && diagram.rect().contains(mouseX, mouseY)
                        && DIAGRAM_RENDERER.mouseReleased(
                        diagram.x, diagram.y, diagram.width, diagram.height,
                        mouseX, mouseY, btn, requestedSubLevel, latestData)) {
                    return true;
                }
            }
            dragging = null;
            draggingCustomHud = null;
            resizingDiagram = null;
            resizingCustomHud = null;
            resizingCustomHudHandle = null;
            resizeStartRect = null;
            return super.mouseReleased(mouseX, mouseY, btn);
        }

        // Resize the diagram
        private void resizeDiagram(double mouseX, double mouseY) {
            OverlayBox box = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
            if (box == null || resizeStartRect == null || resizingDiagram == null) {
                return;
            }
            Rect candidate = resizedDiagramRect(mouseX, mouseY);
            candidate = clampToScreen(candidate, this.width, this.height);

            box.set(candidate);
            box.userPlaced = true;
        }

        // Resize the custom HUD
        private void resizeCustomHud(double mouseX, double mouseY) {
            OverlayBox box = CUSTOM_HUD_BOXES.get(resizingCustomHud);
            if (box == null || resizeStartRect == null || resizingCustomHudHandle == null) {
                return;
            }
            int deltaX = (int) mouseX - resizeStartMouseX;
            int deltaY = (int) mouseY - resizeStartMouseY;
            int left = resizeStartRect.x();
            int top = resizeStartRect.y();
            int right = resizeStartRect.right();
            int bottom = resizeStartRect.bottom();

            if (resizingCustomHudHandle == ResizeHandle.TOP_LEFT
                    || resizingCustomHudHandle == ResizeHandle.LEFT
                    || resizingCustomHudHandle == ResizeHandle.BOTTOM_LEFT) {
                left += deltaX;
            }
            if (resizingCustomHudHandle == ResizeHandle.TOP_LEFT
                    || resizingCustomHudHandle == ResizeHandle.TOP
                    || resizingCustomHudHandle == ResizeHandle.TOP_RIGHT) {
                top += deltaY;
            }
            if (resizingCustomHudHandle == ResizeHandle.TOP_RIGHT
                    || resizingCustomHudHandle == ResizeHandle.RIGHT
                    || resizingCustomHudHandle == ResizeHandle.BOTTOM_RIGHT) {
                right += deltaX;
            }
            if (resizingCustomHudHandle == ResizeHandle.BOTTOM_LEFT
                    || resizingCustomHudHandle == ResizeHandle.BOTTOM
                    || resizingCustomHudHandle == ResizeHandle.BOTTOM_RIGHT) {
                bottom += deltaY;
            }

            if (right - left < CUSTOM_HUD_MIN_WIDTH) {
                if (resizingCustomHudHandle == ResizeHandle.TOP_LEFT
                        || resizingCustomHudHandle == ResizeHandle.LEFT
                        || resizingCustomHudHandle == ResizeHandle.BOTTOM_LEFT) {
                    left = right - CUSTOM_HUD_MIN_WIDTH;
                } else {
                    right = left + CUSTOM_HUD_MIN_WIDTH;
                }
            }
            if (bottom - top < CUSTOM_HUD_MIN_HEIGHT) {
                if (resizingCustomHudHandle == ResizeHandle.TOP_LEFT
                        || resizingCustomHudHandle == ResizeHandle.TOP
                        || resizingCustomHudHandle == ResizeHandle.TOP_RIGHT) {
                    top = bottom - CUSTOM_HUD_MIN_HEIGHT;
                } else {
                    bottom = top + CUSTOM_HUD_MIN_HEIGHT;
                }
            }

            Rect candidate = clampToScreen(new Rect(left, top, right - left, bottom - top), this.width, this.height);
            box.set(candidate);
            box.userPlaced = true;
        }

        // Get the resized diagram rect
        private Rect resizedDiagramRect(double mouseX, double mouseY) {
            int deltaX = (int) mouseX - resizeStartMouseX;
            int deltaY = (int) mouseY - resizeStartMouseY;
            int left = resizeStartRect.x();
            int top = resizeStartRect.y();
            int right = resizeStartRect.right();
            int bottom = resizeStartRect.bottom();

            int signedWidthDelta = switch (resizingDiagram) {
                case TOP_LEFT, LEFT, BOTTOM_LEFT -> -deltaX;
                case TOP, BOTTOM -> (int) Math.round(deltaY * DIAGRAM_ASPECT);
                case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> deltaX;
            };
            int signedHeightDelta = switch (resizingDiagram) {
                case TOP_LEFT, TOP, TOP_RIGHT -> -deltaY;
                case LEFT, RIGHT -> (int) Math.round(deltaX / DIAGRAM_ASPECT);
                case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> deltaY;
            };
            int targetWidth = resizeStartRect.width() + signedWidthDelta;
            int targetHeight = resizeStartRect.height() + signedHeightDelta;
            if (Math.abs(signedHeightDelta) > Math.abs(signedWidthDelta / DIAGRAM_ASPECT)) {
                targetWidth = (int) Math.round(targetHeight * DIAGRAM_ASPECT);
            } else {
                targetHeight = (int) Math.round(targetWidth / DIAGRAM_ASPECT);
            }
            int maxWidth = Math.min(DIAGRAM_MAX_WIDTH, Math.min(this.width - 8, (int) Math.round((this.height - 8) * DIAGRAM_ASPECT)));
            targetWidth = Mth.clamp(targetWidth, DIAGRAM_MIN_WIDTH, Math.max(DIAGRAM_MIN_WIDTH, maxWidth));
            targetHeight = Math.max((int) Math.round(targetWidth / DIAGRAM_ASPECT), Math.round(DIAGRAM_MIN_WIDTH / (float) DIAGRAM_ASPECT));

            boolean anchorRight = resizingDiagram == ResizeHandle.TOP_LEFT
                    || resizingDiagram == ResizeHandle.LEFT
                    || resizingDiagram == ResizeHandle.BOTTOM_LEFT;
            boolean anchorBottom = resizingDiagram == ResizeHandle.TOP_LEFT
                    || resizingDiagram == ResizeHandle.TOP
                    || resizingDiagram == ResizeHandle.TOP_RIGHT;
            if (anchorRight) {
                left = right - targetWidth;
            } else {
                right = left + targetWidth;
            }
            if (anchorBottom) {
                top = bottom - targetHeight;
            } else {
                bottom = top + targetHeight;
            }
            return new Rect(left, top, targetWidth, targetHeight);
        }

        // Check if this can place edited overlay
        private boolean canPlaceEditedOverlay(OverlayId edited, Rect candidate) {
            List<Rect> occupied = new ArrayList<>();
            occupied.add(new Rect(this.width / 2 + 14, this.height / 2 - 18,
                    Math.min(260, Math.max(120, this.width - (this.width / 2 + 14) - 8)),
                    Math.min(190, Math.max(80, this.height - (this.height / 2 - 18) - 20))).padded(6));
            for (Map.Entry<OverlayId, OverlayBox> entry : OVERLAY_BOXES.entrySet()) {
                if (entry.getKey() != edited) {
                    occupied.add(entry.getValue().rect().padded(4));
                }
            }
            return !intersectsAny(candidate.padded(4), occupied);
        }

        // Handle mouse scrolled
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (mode == OverlayMode.DIAGRAM) {
                OverlayBox diagram = OVERLAY_BOXES.get(OverlayId.DIAGRAM);
                if (diagram != null && diagram.rect().contains(mouseX, mouseY)
                        && DIAGRAM_RENDERER.mouseScrolled(
                        diagram.x, diagram.y, diagram.width, diagram.height,
                        mouseX, mouseY, scrollX, scrollY, requestedSubLevel, latestData)) {
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        // Handle key pressed
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE
                    || (keyCloseGuardTicks <= 0 && mode == OverlayMode.DIAGRAM && DIAGRAM_INTERACTIVE_MODE.matches(keyCode, scanCode))
                    || (keyCloseGuardTicks <= 0 && mode == OverlayMode.EDIT && EDIT_OVERLAYS_MODE.matches(keyCode, scanCode))) {
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Check if this is a pause screen
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        // Handle the close event
        @Override
        public void onClose() {
            super.onClose();
            saveOverlayLayout();
        }

        // Handle screen removal
        @Override
        public void removed() {
            super.removed();
            saveOverlayLayout();
        }
    }

}
