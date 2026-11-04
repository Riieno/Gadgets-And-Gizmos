package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSplineData;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachLanes;
import com.rieno.gadgetsandgizmos.lib.navigation.SplinePathNetwork;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockApproachPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Bind approach drafts to the loaded dock and validate every confirmed edit on the server
public final class ShipDockApproachService{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long SESSION_TICKS = 24000L;
    private ShipDockApproachService(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void clear(){ SESSIONS.clear(); }

    public static void handle(ServerPlayer player, ShipDockApproachPayload payload){
        if(payload.action() != ShipDockApproachPayload.CONFIGURE && payload.action() != ShipDockApproachPayload.CONFIRM
                && payload.action() != ShipDockApproachPayload.RESET) return;
        if(!(SimulatedHelper.findLoadedBlockEntityExact(player.level(), payload.subLevelId(), payload.pos()) instanceof ShipDockBlockEntity dock)) return;
        if(player.isSpectator() || !player.mayBuild() || player.position().distanceToSqr(
                SimulatedHelper.toGlobalWorldPosition(dock, dock.getBlockPos().getCenter())) > 100.0D) return;
        if(payload.action() == ShipDockApproachPayload.RESET){
            dock.configureApproaches(Map.of());
            ShipControlModuleRuntime.resetApproachRoutes(dock.getDockId());
            SESSIONS.values().removeIf(session -> session.dockId().equals(dock.getDockId()));
            send(player, payload, ShipDockApproachPayload.HIDE, new CompoundTag());
            return;
        }
        ShipDockRegistry.Dock record = dock.createRouteRecord();
        if(record == null || record.connectorTargets().isEmpty() || record.connectorTargets().size() > 64){
            player.displayClientMessage(Component.translatable("createthrusters.ship_dock.approach_unavailable"), false);
            return;
        }
        if(payload.action() == ShipDockApproachPayload.CONFIGURE){
            UUID token = UUID.randomUUID();
            SESSIONS.put(player.getUUID(), new Session(token, dock.getDockId(), record, player.serverLevel().getGameTime(), player.serverLevel().dimension().location()));
            CompoundTag draft = snapshot(record);
            draft.putUUID("Token", token);
            send(player, payload, ShipDockApproachPayload.SNAPSHOT, draft);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        CompoundTag draft = payload.data();
        if(session == null || draft == null || !draft.hasUUID("Token") || !session.token().equals(draft.getUUID("Token"))
                || !session.dockId().equals(dock.getDockId()) || !session.dimension().equals(player.serverLevel().dimension().location())
                || player.serverLevel().getGameTime() - session.started() > SESSION_TICKS){
            reject(player, payload, true);
            return;
        }
        ListTag targets = draft.getList("Connectors", Tag.TAG_COMPOUND);
        if(targets.size() != session.record().connectorTargets().size() || targets.size() != record.connectorTargets().size()){
            reject(player, payload, true); return;
        }
        SplinePathNetwork network;
        try{
            network = draft.contains("Network", Tag.TAG_COMPOUND) ? SplinePathNetwork.fromTag(draft.getCompound("Network"))
                    : SplinePathNetwork.linear(WaypointSplineData.readPoints(draft.getList("Corridor", Tag.TAG_COMPOUND), DockingApproachPath.MAX_POINTS));
        }catch(IllegalArgumentException ex){ reject(player, payload, false); return; }
        if(!network.routable() || !DockingApproachLanes.structured(network) || network.nodes().stream().anyMatch(node -> !finite(node.position())
                || node.position().distanceToSqr(session.record().worldPosition()) > 128.0D * 128.0D)){
            reject(player, payload, false); return;
        }
        SplinePathNetwork traffic;
        try{ traffic = DockingApproachLanes.connected(network, record.connectorTargets().getFirst().up()); }
        catch(IllegalArgumentException ex){ reject(player, payload, false); return; }
        if(traffic.nodes().stream().anyMatch(node -> node.position().distanceToSqr(record.worldPosition()) > 128.0D * 128.0D)){
            reject(player, payload, false); return;
        }
        Map<ShipDockBlockEntity.ConnectorReference, DockingApproachPath> paths = new LinkedHashMap<>();
        int connectionCount = 0, controlCount = 0;
        for(int idx = 0; idx < targets.size(); idx++){
            CompoundTag val = targets.getCompound(idx);
            ShipDockRegistry.ConnectorTarget original = session.record().connectorTargets().get(idx);
            ShipDockRegistry.ConnectorTarget current = record.connectorTargets().get(idx);
            if(!original.pos().equals(current.pos()) || !Objects.equals(original.subLevelId(), current.subLevelId())
                    || !original.approachPath().equals(current.approachPath())){ reject(player, payload, true); return; }
            var axis = new DockingApproachAxis(original.worldPosition(), original.facing(), DockingApproachAxis.MAX_DISTANCE).followLanes(network);
            List<DockingApproachPath.Connection> connections;
            try{ connections = DockingApproachPath.readConnections(val.getList("Connections", Tag.TAG_COMPOUND)); }
            catch(IllegalArgumentException ex){ reject(player, payload, false); return; }
            connectionCount += connections.size();
            controlCount += connections.stream().mapToInt(connection -> connection.controls().size()).sum();
            if(connectionCount > 256 || controlCount > 256 || connections.stream().anyMatch(connection -> network.node(connection.junction()) == null
                    || connection.controls().stream().anyMatch(point -> point.distanceToSqr(record.worldPosition()) > 128.0D * 128.0D))){
                reject(player, payload, false); return;
            }
            paths.put(new ShipDockBlockEntity.ConnectorReference(current.subLevelId(), current.pos()),
                    DockingApproachPath.captureLanes(original.worldPosition(), original.facing(), original.up(),
                            axis.endpoint(), network, traffic));
        }
        dock.configureApproaches(paths);
        SESSIONS.remove(player.getUUID());
        send(player, payload, ShipDockApproachPayload.HIDE, new CompoundTag());
    }

    // Start with the connector-aligned primary and an outward traffic lane
    private static CompoundTag snapshot(ShipDockRegistry.Dock dock){
        CompoundTag draft = new CompoundTag();
        ListTag targets = new ListTag();
        for(var connector : dock.connectorTargets()){
            CompoundTag val = new CompoundTag();
            val.put("Tip", WaypointSplineData.vector(connector.worldPosition()));
            val.put("Facing", WaypointSplineData.vector(connector.facing()));
            val.put("Up", WaypointSplineData.vector(connector.up()));
            val.putDouble("Distance", connector.approachPath().distance());
            val.put("Connections", DockingApproachPath.connectionsTag(connector.approachPath().worldConnections(
                    connector.worldPosition(), connector.facing(), connector.up())));
            targets.add(val);
        }
        draft.put("Connectors", targets);
        var primary = dock.connectorTargets().getFirst();
        SplinePathNetwork network = primary.approachPath().worldLaneLayout(primary.worldPosition(), primary.facing(), primary.up());
        List<DockingApproachAxis> axes = dock.connectorTargets().stream().map(connector -> new DockingApproachAxis(
                connector.worldPosition(), connector.facing(), DockingApproachAxis.MAX_DISTANCE)).toList();
        network = DockingApproachLanes.prepare(network, axes, primary.up(), !primary.approachPath().laneLayout().isEmpty());
        for(int idx = 0; idx < axes.size(); idx++) targets.getCompound(idx).putDouble("Distance", axes.get(idx).followLanes(network).distance());
        draft.put("Corridor", WaypointSplineData.points(network.points(network.paths().getFirst())));
        draft.put("Network", network.toTag());
        draft.putBoolean("LaneLayout", true);
        return draft;
    }

    // Keep editable geometry on validation failure and reopen stale sessions explicitly
    private static void reject(ServerPlayer player, ShipDockApproachPayload request, boolean stale){
        player.displayClientMessage(Component.translatable(stale
                ? "createthrusters.ship_dock.approach_stale" : "createthrusters.ship_dock.approach_invalid"), false);
        if(stale){
            SESSIONS.remove(player.getUUID());
            send(player, request, ShipDockApproachPayload.HIDE, new CompoundTag());
        }
    }

    private static void send(ServerPlayer player, ShipDockApproachPayload request, int action, CompoundTag draft){
        PacketDistributor.sendToPlayer(player, new ShipDockApproachPayload(action, request.pos(), request.subLevelId(), draft));
    }

    private static boolean finite(Vec3 val){ return Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z); }
    private record Session(UUID token, UUID dockId, ShipDockRegistry.Dock record, long started, net.minecraft.resources.ResourceLocation dimension){}
}
