package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShippingRouteSplineSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Publish visible SCM schedule spline controls to one client
public record ShippingRouteSplinePayload(boolean visible, @Nullable UUID ownerId,
                                         long revision,
                                         List<ShippingRouteSplineSnapshot.Route> routes)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_ROUTES = 128;
    private static final int MAX_WAYPOINTS_PER_ROUTE = 4096;
    public static final Type<ShippingRouteSplinePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_route_spline"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShippingRouteSplinePayload> STREAM_CODEC =
            StreamCodec.of(ShippingRouteSplinePayload::encode, ShippingRouteSplinePayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize one bounded route snapshot payload
    public ShippingRouteSplinePayload {
        routes = routes == null ? List.of() : routes.stream()
                .filter(route -> route != null)
                .limit(MAX_ROUTES)
                .map(route -> new ShippingRouteSplineSnapshot.Route(
                        route.id(), route.scheduleEntry(), route.waypoints().stream()
                        .limit(MAX_WAYPOINTS_PER_ROUTE).toList()))
                .toList();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply one route spline snapshot on the client
    public static void handle(ShippingRouteSplinePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> clientClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.ShippingRouteSplineClient");
                Method method = clientClass.getMethod("setSnapshot", ShippingRouteSplinePayload.class);
                method.invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode one route spline snapshot
    private static void encode(RegistryFriendlyByteBuf buffer, ShippingRouteSplinePayload payload) {
        buffer.writeBoolean(payload.visible());
        buffer.writeBoolean(payload.ownerId() != null);
        if (payload.ownerId() != null) buffer.writeUUID(payload.ownerId());
        buffer.writeLong(payload.revision());
        buffer.writeVarInt(payload.routes().size());
        for (ShippingRouteSplineSnapshot.Route route : payload.routes()) {
            buffer.writeUUID(route.id());
            buffer.writeVarInt(route.scheduleEntry());
            buffer.writeVarInt(route.waypoints().size());
            for (Vec3 point : route.waypoints()) writeVec3(buffer, point);
        }
    }

    // Decode one route spline snapshot
    private static ShippingRouteSplinePayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        UUID ownerId = buffer.readBoolean() ? buffer.readUUID() : null;
        long revision = buffer.readLong();
        int routeCount = checkedCount(buffer.readVarInt(), MAX_ROUTES);
        List<ShippingRouteSplineSnapshot.Route> routes = new ArrayList<>(routeCount);
        for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
            UUID routeId = buffer.readUUID();
            int scheduleEntry = buffer.readVarInt();
            int waypointCount = checkedCount(buffer.readVarInt(), MAX_WAYPOINTS_PER_ROUTE);
            List<Vec3> waypoints = new ArrayList<>(waypointCount);
            for (int waypointIndex = 0; waypointIndex < waypointCount; waypointIndex++) {
                waypoints.add(readVec3(buffer));
            }
            routes.add(new ShippingRouteSplineSnapshot.Route(
                    routeId, scheduleEntry, waypoints));
        }
        return new ShippingRouteSplinePayload(visible, ownerId, revision, routes);
    }

    // Reject a malformed collection size
    private static int checkedCount(int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid route collection size: " + count);
        }
        return count;
    }

    // Write one world position
    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 position) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
    }

    // Read one world position
    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
