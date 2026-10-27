package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSplineData;
import com.rieno.gadgetsandgizmos.lib.client.render.WaypointSplineEditor;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachLanes;
import com.rieno.gadgetsandgizmos.lib.client.render.SplineModelRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.DesaturatedModelRenderTypes;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import com.rieno.gadgetsandgizmos.lib.navigation.SplinePathNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockApproachPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

// Keep the dock editing draft visible until the server accepts its confirmation
public final class ShipDockApproachClient{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int ENTER_COLOR = 0xFF4FE081;
    private static final int EXIT_COLOR = 0xFFF06A6A;
    private static final int ANCHOR_COLOR = 0xFFFFFFFF;
    private static final int SIGHTLINE_COLOR = 0xFFB080FF;
    private static final WaypointSplineEditor EDITOR = new WaypointSplineEditor(ShipDockApproachClient::edit);
    private static ShipDockApproachPayload draft;
    private static SplinePathNetwork network = SplinePathNetwork.EMPTY;
    private static Map<UUID, Integer> axisLocks = Map.of();
    private static List<DockingApproachAxis> axes = List.of();
    private static SplinePathNetwork renderedLayout = SplinePathNetwork.EMPTY;
    private static SplinePathNetwork renderedTraffic = SplinePathNetwork.EMPTY;
    private static List<DockingApproachAxis> renderedDetections = List.of();
    private static Map<UUID, Crossing> crossings = Map.of();
    private record Crossing(SplinePathNetwork.Snap snap, DockingApproachAxis axis){}
    private ShipDockApproachClient(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static boolean editing(){ return draft != null; }
    public static boolean editing(BlockPos pos, UUID subLevelId){
        return draft != null && draft.pos().equals(pos) && Objects.equals(draft.subLevelId(), subLevelId);
    }
    public static void configure(BlockPos pos, UUID subLevelId){
        PacketDistributor.sendToServer(new ShipDockApproachPayload(ShipDockApproachPayload.CONFIGURE, pos, subLevelId, new CompoundTag()));
    }
    public static void reset(BlockPos pos, UUID subLevelId){
        PacketDistributor.sendToServer(new ShipDockApproachPayload(ShipDockApproachPayload.RESET, pos, subLevelId, new CompoundTag()));
    }
    public static void confirm(){
        if(draft != null) PacketDistributor.sendToServer(new ShipDockApproachPayload(
                ShipDockApproachPayload.CONFIRM, draft.pos(), draft.subLevelId(), draft.data().copy()));
    }
    public static void accept(ShipDockApproachPayload payload){
        if(payload.action() == ShipDockApproachPayload.HIDE){
            if(editing(payload.pos(), payload.subLevelId())) clear();
            return;
        }
        draft = payload;
        network = payload.data().contains("Network") ? SplinePathNetwork.fromTag(payload.data().getCompound("Network"))
                : SplinePathNetwork.linear(WaypointSplineData.readPoints(payload.data().getList("Corridor", Tag.TAG_COMPOUND), DockingApproachPath.MAX_POINTS));
        axes = readAxes();
        network = DockingApproachLanes.prepare(network, axes, up(), payload.data().getBoolean("LaneLayout"));
        renderedLayout = SplinePathNetwork.EMPTY;
        EDITOR.setWorldRenderer(ShipDockApproachClient::renderModels);
        rebuild();
    }
    public static void clear(){
        draft = null; network = SplinePathNetwork.EMPTY; renderedLayout = SplinePathNetwork.EMPTY;
        renderedTraffic = SplinePathNetwork.EMPTY; axisLocks = Map.of(); axes = List.of(); EDITOR.clear();
        renderedDetections = List.of();
        crossings = Map.of();
    }
    public static void onClientTick(ClientTickEvent.Pre evt){ EDITOR.onClientTick(evt); }
    public static void onRenderWorld(RenderLevelStageEvent evt){ EDITOR.onRenderWorld(evt); }
    public static void onRenderGui(RenderGuiEvent.Post evt){ EDITOR.onRenderGui(evt); }
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt){ EDITOR.onMouseScrolling(evt); }
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt){
        if(draft != null) EDITOR.onInteractionKeyMappingTriggered(evt);
    }

    // Each shared node supplies the same role, color and position on every connected curve
    private static void rebuild(){
        if(draft == null) return;
        ListTag targets = draft.data().getList("Connectors", Tag.TAG_COMPOUND);
        axes = readAxes();
        for(int idx = 0; idx < targets.size(); idx++) targets.getCompound(idx).putDouble("Distance", axes.get(idx).followLanes(network).distance());
        Map<UUID, Integer> locks = new java.util.LinkedHashMap<>();
        for(var node : network.nodes()) for(int idx = 0; idx < axes.size(); idx++){
            if(axes.get(idx).contains(node.position())){ locks.put(node.id(), idx); break; }
        }
        axisLocks = Map.copyOf(locks);
        draft.data().put("Network", network.toTag());
        draft.data().put("Corridor", WaypointSplineData.points(network.isEmpty() ? List.of() : network.points(network.paths().getFirst())));
        for(int idx = 0; idx < targets.size(); idx++){
            int owner = idx;
            targets.getCompound(idx).put("Connections", DockingApproachPath.connectionsTag(axisLocks.entrySet().stream()
                    .filter(entry -> entry.getValue() == owner).map(entry -> new DockingApproachPath.Connection(entry.getKey(), List.of())).toList()));
        }
        List<WaypointSplineEditor.Route> routes = new ArrayList<>();
        for(SplinePathNetwork.Path path : network.paths()) routes.add(new WaypointSplineEditor.Route(
                path.id(), network.points(path), policy(path)));
        Map<UUID, Crossing> markers = new java.util.LinkedHashMap<>();
        for(var axis : axes) for(var crossing : axis.intersections(network)){
            if(crossing.nodeId() != null) continue;
            UUID id = new UUID(-2, markers.size() + 1);
            markers.put(id, new Crossing(crossing, axis));
            routes.add(new WaypointSplineEditor.Route(id, List.of(crossing.position()), new WaypointSplineEditor.EditPolicy(){
                @Override public boolean canMove(int idx, int count){ return true; }
                @Override public int pickPriority(int idx, int count, boolean modifier){ return 30; }
                @Override public boolean canAdd(){ return false; }
                @Override public boolean canRemove(int idx, int count){ return false; }
                @Override public Vec3 constrain(int idx, Vec3 position){ return axis.constrain(position); }
                @Override public boolean onRouteScroll(int idx, int segment, Vec3 position, double delta){
                    var path = network.paths().stream().filter(val -> val.id().equals(crossing.pathId())).findFirst().orElseThrow();
                    return policy(path).onRouteScroll(-1, crossing.segment(), position, delta);
                }
                @Override public List<Component> tooltip(int idx, int count, Vec3 position, boolean branching){
                    return List.of(Component.translatable("createthrusters.ship_dock.approach_detection_crossing"),
                            Component.translatable("createthrusters.ship_dock.approach_axis_hint"));
                }
            }));
        }
        crossings = Map.copyOf(markers);
        ListTag connectors = draft.data().getList("Connectors", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < connectors.size(); idx++){
            CompoundTag val = connectors.getCompound(idx);
            Vec3 tip = WaypointSplineData.vector(val.getCompound("Tip"));
            Vec3 facing = WaypointSplineData.vector(val.getCompound("Facing"));
            Vec3 target = tip.add(facing.scale(val.getDouble("Distance")));
            routes.add(new WaypointSplineEditor.Route(new UUID(-1, idx + 1), List.of(tip, target), new WaypointSplineEditor.EditPolicy(){
                @Override public boolean canMove(int point, int count){ return false; }
                @Override public int pickPriority(int point, int count, boolean branchModifier){ return -10; }
                @Override public boolean canRemove(int point, int count){ return false; }
                @Override public boolean canAdd(){ return false; }
                @Override public List<Component> tooltip(int point, int count, Vec3 position, boolean branching){
                    return point == 1 ? List.of(Component.translatable("createthrusters.ship_dock.approach_alignment"),
                            Component.translatable("createthrusters.ship_dock.approach_detection_follows_lane")) : List.of();
                }
            }));
        }
        EDITOR.setRoutes(routes);
        var mc = net.minecraft.client.Minecraft.getInstance();
        Component use = mc == null ? Component.literal("Right click") : mc.options.keyUse.getTranslatedKeyMessage();
        Component sprint = mc == null ? Component.literal("Ctrl") : mc.options.keySprint.getTranslatedKeyMessage();
        Component sneak = mc == null ? Component.literal("Shift") : mc.options.keyShift.getTranslatedKeyMessage();
        EDITOR.setToolbar(List.of(Component.translatable("createthrusters.ship_dock.approach_toolbar_points", use, sprint, use),
                Component.translatable("createthrusters.ship_dock.approach_toolbar_lanes", sprint, sneak),
                Component.translatable("createthrusters.ship_dock.approach_toolbar_direction"),
                Component.translatable("createthrusters.ship_dock.approach_toolbar_confirm")));
    }

    private static List<DockingApproachAxis> readAxes(){
        ListTag targets = draft.data().getList("Connectors", Tag.TAG_COMPOUND);
        List<DockingApproachAxis> detections = new ArrayList<>();
        for(int idx = 0; idx < targets.size(); idx++){
            CompoundTag val = targets.getCompound(idx);
            detections.add(new DockingApproachAxis(WaypointSplineData.vector(val.getCompound("Tip")),
                    WaypointSplineData.vector(val.getCompound("Facing")), DockingApproachAxis.MAX_DISTANCE));
        }
        return List.copyOf(detections);
    }

    private static WaypointSplineEditor.EditPolicy policy(SplinePathNetwork.Path path){
        return new WaypointSplineEditor.EditPolicy(){
            private SplinePathNetwork.Node node(int idx){ return network.node(path.nodes().get(idx)); }
            @Override public boolean canMove(int idx, int count){ return true; }
            @Override public int pickPriority(int idx, int count, boolean branchModifier){
                return node(idx).role() != SplinePathNetwork.Role.JUNCTION ? 20 : branchModifier ? 20 : 0;
            }
            @Override public boolean canAdd(){ return network.nodes().size() + network.paths().size()
                    + 16 * (network.paths().size() - 1) <= SplinePathNetwork.MAX_NODES; }
            @Override public boolean addWithSprint(){ return false; }
            @Override public boolean removeWithSprint(){ return true; }
            @Override public boolean canRemove(int idx, int count){
                UUID id = node(idx).id();
                return network.canRemoveEndpoint(id, network.paths().getFirst().id())
                        || idx > 0 && idx < count - 1 && network.paths().stream().noneMatch(lane -> attached(lane.nodes().get(idx)));
            }
            @Override public boolean canDuplicate(int idx, int count){ return false; }
            @Override public Integer pointColor(int idx, int count){ return color(node(idx).role()); }
            @Override public List<Vec3> previewControls(UUID activeRoute, int idx, Vec3 position, List<Vec3> controls){
                SplinePathNetwork.Path active = network.paths().stream().filter(candidate -> candidate.id().equals(activeRoute)).findFirst().orElse(null);
                if(active == null || idx < 0 || idx >= active.nodes().size()) return controls;
                var preview = DockingApproachLanes.movePoint(network, DockingApproachLanes.laneIndex(network, activeRoute), idx, position, axes);
                return preview.points(path);
            }
            @Override public Vec3 constrain(int idx, Vec3 position){
                return DockingApproachLanes.constrainPoint(node(idx).position(), axes, position);
            }
            @Override public boolean onScroll(int idx, double delta){
                if(node(idx).role() == SplinePathNetwork.Role.JUNCTION) return false;
                update(DockingApproachLanes.flip(network));
                return true;
            }
            @Override public boolean onRouteScroll(int idx, int segment, Vec3 position, double delta){
                var mc = net.minecraft.client.Minecraft.getInstance();
                int lane = DockingApproachLanes.laneIndex(network, path.id());
                if(mc.options.keySprint.isDown()){
                    resizeLanes(network.paths().size() + (delta > 0 ? 1 : -1));
                    return true;
                }
                if(mc.options.keyShift.isDown()){
                    Vec3 look = mc.player.getLookAngle();
                    var direction = net.minecraft.core.Direction.getNearest(look.x, look.y, look.z);
                    Vec3 offset = Vec3.atLowerCornerOf(direction.getNormal()).scale(delta > 0 ? 0.25D : -0.25D);
                    update(DockingApproachLanes.translate(network, lane, offset, axes));
                    return true;
                }
                return idx >= 0 && onScroll(idx, delta);
            }
            @Override public List<Component> routeTooltip(int idx, int count, Vec3 position, boolean branching){
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(path.id().equals(network.paths().getFirst().id())
                        ? "createthrusters.ship_dock.approach_primary_lane" : "createthrusters.ship_dock.approach_traffic_lane"));
                if(idx >= 0) lines.addAll(tooltip(idx, count, position, branching));
                return lines;
            }
            @Override public List<Component> tooltip(int idx, int count, Vec3 position, boolean branching){
                SplinePathNetwork.Node point = node(idx);
                if(axisLocks.containsKey(point.id())) return List.of(Component.translatable("createthrusters.ship_dock.approach_detection_crossing"),
                        Component.translatable("createthrusters.ship_dock.approach_axis_hint"));
                if(point.role() == SplinePathNetwork.Role.JUNCTION) return List.of(roleLabel(point.role()),
                        Component.translatable("createthrusters.ship_dock.approach_progression_hint"));
                return List.of(roleLabel(point.role()), Component.translatable("createthrusters.ship_dock.approach_scroll_hint"),
                        Component.translatable("createthrusters.ship_dock.approach_freeform_hint"));
            }
        };
    }

    private static int color(SplinePathNetwork.Role role){
        return switch(role){ case ENTER -> ENTER_COLOR; case EXIT -> EXIT_COLOR; case JUNCTION -> ANCHOR_COLOR; };
    }
    private static Component roleLabel(SplinePathNetwork.Role role){
        return Component.translatable("createthrusters.ship_dock.approach_" + role.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(switch(role){ case ENTER -> ChatFormatting.GREEN; case EXIT -> ChatFormatting.RED; case JUNCTION -> ChatFormatting.GOLD; });
    }
    private static void update(SplinePathNetwork updated){
        network = updated;
        rebuild();
    }

    private static boolean attached(UUID id){ return axisLocks.containsKey(id); }

    private static Vec3 facing(){ return axes.isEmpty() ? new Vec3(0, 0, 1) : axes.getFirst().facing(); }
    private static Vec3 up(){
        ListTag connectors = draft.data().getList("Connectors", Tag.TAG_COMPOUND);
        return connectors.isEmpty() ? new Vec3(0, 1, 0) : WaypointSplineData.vector(connectors.getCompound(0).getCompound("Up"));
    }

    private static void resizeLanes(int count){
        update(DockingApproachLanes.resize(network, count, facing()));
    }

    // Render supplied block models using the same geometry and previews used for picking
    private static void renderModels(RenderLevelStageEvent evt, List<WaypointSplineEditor.RenderedRoute> routes){
        var mc = net.minecraft.client.Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = evt.getCamera().getPosition(), up = up();
        Map<UUID, Vec3> positions = new java.util.HashMap<>();
        for(var route : routes){
            var path = network.paths().stream().filter(val -> val.id().equals(route.id())).findFirst().orElse(null);
            if(path == null) continue;
            for(int idx = 0; idx < path.nodes().size(); idx++) positions.put(path.nodes().get(idx), route.spline().waypoints().get(idx));
        }
        var layout = new SplinePathNetwork(network.nodes().stream().map(node -> new SplinePathNetwork.Node(node.id(),
                positions.getOrDefault(node.id(), node.position()), node.role())).toList(), network.paths());
        if(!layout.equals(renderedLayout)){
            renderedLayout = layout;
            try{ renderedTraffic = DockingApproachLanes.connected(layout, up); }
            catch(IllegalArgumentException ex){ renderedTraffic = layout; }
            renderedDetections = axes.stream().map(axis -> axis.followLanes(layout)).toList();
        }
        for(var path : renderedTraffic.paths()) SplineModelRenderer.render(evt.getPoseStack(), camera, buffers,
                renderedTraffic.spline(path.id()), path.id().equals(renderedTraffic.paths().getFirst().id())
                        ? CTPartialModels.SHIP_DOCK_APPROACH_PRIMARY : CTPartialModels.SHIP_DOCK_APPROACH_EXTRA, up, 0xFFFFFF);
        for(var route : routes){
            if(route.id().getMostSignificantBits() == -2){
                Vec3 point = route.spline().waypoints().getFirst();
                var marker = crossings.get(route.id());
                SplineModelRenderer.renderPoint(evt.getPoseStack(), camera, buffers, CTPartialModels.SHIP_DOCK_APPROACH_ANCHOR,
                        point, network.spline(marker.snap().pathId()).project(point).tangent(), up, ANCHOR_COLOR);
                continue;
            }
            boolean detection = route.id().getMostSignificantBits() == -1;
            WaypointSpline spline = route.spline();
            if(detection){
                var axis = renderedDetections.get((int)route.id().getLeastSignificantBits() - 1);
                spline = WaypointSpline.of(List.of(axis.tip(), axis.endpoint()));
            }
            if(detection) SplineModelRenderer.render(evt.getPoseStack(), camera, buffers, spline,
                    CTPartialModels.SHIP_DOCK_APPROACH_EXTRA, up, SIGHTLINE_COLOR, DesaturatedModelRenderTypes.cutoutMipped());
            for(int idx = 0; idx < spline.waypoints().size(); idx++){
                if(detection && idx == 1) continue;
                Vec3 point = spline.waypoints().get(idx);
                SplinePathNetwork.Path path = network.paths().stream().filter(val -> val.id().equals(route.id())).findFirst().orElse(null);
                int color = detection ? ANCHOR_COLOR : color(network.node(path.nodes().get(idx)).role());
                Vec3 direction = spline.project(point).tangent();
                SplineModelRenderer.renderPoint(evt.getPoseStack(), camera, buffers, CTPartialModels.SHIP_DOCK_APPROACH_ANCHOR,
                        point, direction, up, color);
            }
        }
    }

    private static void edit(UUID routeId, WaypointSplineEditor.EditAction action, int idx, Vec3 position){
        if(draft == null) return;
        Crossing crossing = crossings.get(routeId);
        if(crossing != null){
            if(action != WaypointSplineEditor.EditAction.MOVE) return;
            var snap = crossing.snap();
            int lane = DockingApproachLanes.laneIndex(network, snap.pathId());
            var inserted = DockingApproachLanes.insert(network, lane, snap.segment(), snap.position());
            if(inserted == network) return;
            UUID node = inserted.paths().get(lane).nodes().get(snap.segment() + 1);
            inserted = inserted.move(node, snap.position());
            update(DockingApproachLanes.movePoint(inserted, lane, snap.segment() + 1, crossing.axis().constrain(position), axes));
            return;
        }
        SplinePathNetwork.Path path = network.paths().stream().filter(candidate -> candidate.id().equals(routeId)).findFirst().orElse(null);
        if(path != null){
            try{
                if(action == WaypointSplineEditor.EditAction.ADD){
                    SplinePathNetwork updated = DockingApproachLanes.insert(network, DockingApproachLanes.laneIndex(network, routeId), idx, position);
                    update(updated);
                    return;
                }
                if(idx < 0 || idx >= path.nodes().size()) return;
                int lane = DockingApproachLanes.laneIndex(network, routeId);
                SplinePathNetwork updated = switch(action){
                    case MOVE -> DockingApproachLanes.movePoint(network, lane, idx, position, axes);
                    case REMOVE -> idx == 0 || idx == path.nodes().size() - 1 ? DockingApproachLanes.removeLane(network, lane)
                            : DockingApproachLanes.removeColumn(network, idx, axes);
                    default -> network;
                };
                update(updated);
            }catch(IllegalArgumentException ignored){}
            return;
        }
    }

}
