package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.ShippingRouteSplineSnapshot;
import com.rieno.gadgetsandgizmos.lib.client.render.WaypointSplineEditor;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteEditPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteSplinePayload;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

// Adapt schedule ownership and packets to the shared spline editor
public final class ShippingRouteSplineClient{
    private static UUID ownerId;
    private static final WaypointSplineEditor EDITOR = new WaypointSplineEditor((route, action, idx, position) -> {
        if(ownerId != null) PacketDistributor.sendToServer(new ShippingRouteEditPayload(ownerId, route,
                ShippingRouteSplineSnapshot.EditAction.valueOf(action.name()), idx, position));
    });

    private ShippingRouteSplineClient(){}

    public static void setSnapshot(ShippingRouteSplinePayload payload){
        ownerId = payload.visible() ? payload.ownerId() : null;
        EDITOR.setRoutes(ownerId == null ? java.util.List.of() : payload.routes().stream()
                .map(route -> new WaypointSplineEditor.Route(route.id(), route.waypoints(), null)).toList());
    }

    public static void clear(){ ownerId = null; EDITOR.clear(); }
    public static boolean isVisible(UUID requested){ return requested != null && requested.equals(ownerId); }
    public static void onClientTick(ClientTickEvent.Pre evt){ if(!ShipDockApproachClient.editing()) EDITOR.onClientTick(evt); }
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered evt){ if(!ShipDockApproachClient.editing()) EDITOR.onInteractionKeyMappingTriggered(evt); }
    public static void onRenderWorld(RenderLevelStageEvent evt){ if(!ShipDockApproachClient.editing()) EDITOR.onRenderWorld(evt); }
}
