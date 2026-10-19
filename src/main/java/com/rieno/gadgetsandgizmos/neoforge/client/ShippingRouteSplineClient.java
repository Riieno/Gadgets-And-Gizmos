package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingRouteSplineSnapshot;
import com.rieno.gadgetsandgizmos.lib.client.render.WaypointSplineRenderer;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteEditPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteSplinePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Render and manipulate the selected server-authoritative Shipping Schedule spline
public final class ShippingRouteSplineClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PICK_DISTANCE = 128.0D;
    private static final double CURVE_PICK_RADIUS = 0.32D;
    private static final double WAYPOINT_PICK_RADIUS = 0.45D;
    private static final double PICK_SAMPLE_SPACING = 0.35D;
    private static final int CURVE_COLOR = 0xFF56DCEB;
    private static final int WAYPOINT_COLOR = 0xFFFFB347;
    private static final int ENDPOINT_COLOR = 0xFF4FE081;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Whether the schedule-owned overlay is independently visible
    private static boolean visible;
    // Stable SCM route owner selected by the schedule
    private static @Nullable UUID ownerId;
    // Latest server-authoritative route controls
    private static List<ShippingRouteSplineSnapshot.Route> routes = List.of();
    // Immutable splines built once for the current server snapshot
    private static Map<UUID, WaypointSpline> splines = Map.of();
    // Currently highlighted curve or waypoint
    private static @Nullable Pick hovered;
    // Active hold-interact waypoint drag
    private static @Nullable Drag drag;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Shipping Schedule spline client
    private ShippingRouteSplineClient() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply one server-authoritative route snapshot
    public static void setSnapshot(ShippingRouteSplinePayload payload) {
        visible = payload.visible() && payload.ownerId() != null;
        ownerId = visible ? payload.ownerId() : null;
        routes = visible ? List.copyOf(payload.routes()) : List.of();
        Map<UUID, WaypointSpline> updatedSplines = new LinkedHashMap<>();
        for (ShippingRouteSplineSnapshot.Route route : routes) {
            updatedSplines.put(route.id(), WaypointSpline.of(route.waypoints()));
        }
        splines = Map.copyOf(updatedSplines);
        hovered = null;
        if (!visible || drag != null && routes.stream()
                .noneMatch(route -> route.id().equals(drag.routeId()))) drag = null;
    }

    // Clear the client route state
    public static void clear() {
        visible = false;
        ownerId = null;
        routes = List.of();
        splines = Map.of();
        hovered = null;
        drag = null;
    }

    // Check whether one SCM owner currently supplies the visible route overlay.
    public static boolean isVisible(UUID requestedOwnerId) {
        return visible && requestedOwnerId != null && requestedOwnerId.equals(ownerId);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update route picking and an active hold-interact drag
    public static void onClientTick(ClientTickEvent.Pre evt) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!visible || minecraft.level == null || player == null || minecraft.screen != null) {
            hovered = null;
            drag = null;
            return;
        }
        if (drag != null) {
            if (!hasWrench(player)) {
                drag = null;
                hovered = null;
                return;
            }
            if (!minecraft.options.keyUse.isDown()) {
                finishDrag();
                return;
            }
            Vec3 preview = player.getEyePosition().add(
                    player.getLookAngle().scale(drag.rayDistance()));
            drag = new Drag(drag.routeId(), drag.waypointIndex(),
                    drag.rayDistance(), drag.original(), preview);
            hovered = Pick.waypoint(drag.routeId(), drag.waypointIndex(),
                    preview, drag.rayDistance());
            return;
        }
        hovered = hasWrench(player) ? pick(player) : null;
    }

    // Capture route edits before normal held-item interaction
    public static void onInteractionKeyMappingTriggered(
            InputEvent.InteractionKeyMappingTriggered evt
    ) {
        if (!evt.isUseItem() || !visible) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.screen != null
                || !hasWrench(player)) return;
        if (drag != null) {
            consume(evt);
            return;
        }
        Pick selected = hovered == null ? pick(player) : hovered;
        if (selected == null || ownerId == null) return;
        ShippingRouteSplineSnapshot.Route route = route(selected.routeId());
        if (route == null) return;
        if (player.isShiftKeyDown() && selected.waypointIndex() > 0
                && selected.waypointIndex() < route.waypoints().size() - 1) {
            sendEdit(selected.routeId(), ShippingRouteSplineSnapshot.EditAction.REMOVE,
                    selected.waypointIndex(), selected.position());
            consume(evt);
            return;
        }
        if (minecraft.options.keySprint.isDown() && selected.segmentIndex() >= 0) {
            sendEdit(selected.routeId(), ShippingRouteSplineSnapshot.EditAction.ADD,
                    selected.segmentIndex(), selected.position());
            consume(evt);
            return;
        }
        if (selected.waypointIndex() <= 0
                || selected.waypointIndex() >= route.waypoints().size() - 1) return;
        drag = new Drag(selected.routeId(), selected.waypointIndex(),
                selected.rayDistance(), selected.position(), selected.position());
        consume(evt);
    }

    // Render the visible spline controls independently of the pathfinder debug overlay
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (!visible || routes.isEmpty()
                || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        List<WaypointSplineRenderer.RouteVisual> visuals = new ArrayList<>();
        for (ShippingRouteSplineSnapshot.Route route : routes) {
            if (route.waypoints().size() < 2) continue;
            int selectedWaypoint = hovered != null && route.id().equals(hovered.routeId())
                    ? hovered.waypointIndex() : -1;
            visuals.add(new WaypointSplineRenderer.RouteVisual(
                    renderedSpline(route), CURVE_COLOR, WAYPOINT_COLOR,
                    ENDPOINT_COLOR, SELECTED_COLOR, selectedWaypoint));
        }
        Camera camera = evt.getCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        WaypointSplineRenderer.render(
                evt.getPoseStack(), camera.getPosition(), bufferSource, visuals);
    }

    // Finish and publish one waypoint drag
    private static void finishDrag() {
        Drag completed = drag;
        drag = null;
        if (completed == null || completed.original().distanceToSqr(completed.preview()) <= 1.0E-6D) return;
        sendEdit(completed.routeId(), ShippingRouteSplineSnapshot.EditAction.MOVE,
                completed.waypointIndex(), completed.preview());
    }

    // Pick the nearest curve and prefer an equally close authored waypoint
    private static @Nullable Pick pick(LocalPlayer player) {
        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Pick best = null;
        for (ShippingRouteSplineSnapshot.Route route : routes) {
            WaypointSpline spline = renderedSpline(route);
            WaypointSpline.RayHit curve = spline.raycast(origin, direction,
                    PICK_DISTANCE, CURVE_PICK_RADIUS, PICK_SAMPLE_SPACING);
            if (curve.found() && (best == null || curve.rayDistance() < best.rayDistance())) {
                best = Pick.curve(route.id(), curve.segmentIndex(),
                        curve.position(), curve.rayDistance());
            }
            WaypointSpline.WaypointHit waypoint = spline.raycastWaypoint(
                    origin, direction, PICK_DISTANCE, WAYPOINT_PICK_RADIUS);
            if (waypoint.found() && (best == null
                    || waypoint.rayDistance() <= best.rayDistance() + WAYPOINT_PICK_RADIUS)) {
                best = Pick.waypoint(route.id(), waypoint.waypointIndex(),
                        waypoint.position(), waypoint.rayDistance());
            }
        }
        return best;
    }

    // Get route controls with the active local drag preview applied
    private static List<Vec3> renderedControls(ShippingRouteSplineSnapshot.Route route) {
        if (drag == null || !route.id().equals(drag.routeId())
                || drag.waypointIndex() < 0
                || drag.waypointIndex() >= route.waypoints().size()) return route.waypoints();
        List<Vec3> controls = new ArrayList<>(route.waypoints());
        controls.set(drag.waypointIndex(), drag.preview());
        return controls;
    }

    // Reuse snapshot curves while rebuilding only the route under an active local drag.
    private static WaypointSpline renderedSpline(ShippingRouteSplineSnapshot.Route route) {
        if (drag != null && route.id().equals(drag.routeId())) {
            return WaypointSpline.of(renderedControls(route));
        }
        WaypointSpline spline = splines.get(route.id());
        return spline == null ? WaypointSpline.of(route.waypoints()) : spline;
    }

    // Resolve one visible route by id
    private static @Nullable ShippingRouteSplineSnapshot.Route route(UUID routeId) {
        return routes.stream().filter(route -> route.id().equals(routeId)).findFirst().orElse(null);
    }

    // Send one route edit to the server
    private static void sendEdit(UUID routeId, ShippingRouteSplineSnapshot.EditAction action,
                                 int index, Vec3 position) {
        if (ownerId == null) return;
        PacketDistributor.sendToServer(new ShippingRouteEditPayload(
                ownerId, routeId, action, index, position));
    }

    // Suppress normal wrench interaction for one handled spline edit
    private static void consume(InputEvent.InteractionKeyMappingTriggered evt) {
        evt.setSwingHand(false);
        evt.setCanceled(true);
    }

    // Check whether the player holds a wrench in either hand
    private static boolean hasWrench(LocalPlayer player) {
        return player.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                || player.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH);
    }

    // Store one current route pick
    private record Pick(UUID routeId, int segmentIndex, int waypointIndex,
                        Vec3 position, double rayDistance) {
        // Create a curve pick
        private static Pick curve(UUID routeId, int segmentIndex,
                                  Vec3 position, double rayDistance) {
            return new Pick(routeId, segmentIndex, -1, position, rayDistance);
        }

        // Create a waypoint pick
        private static Pick waypoint(UUID routeId, int waypointIndex,
                                     Vec3 position, double rayDistance) {
            return new Pick(routeId, -1, waypointIndex, position, rayDistance);
        }
    }

    // Store one active hold-interact waypoint drag
    private record Drag(UUID routeId, int waypointIndex, double rayDistance,
                        Vec3 original, Vec3 preview) {
    }
}
