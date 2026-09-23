package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShippingRouteSplineSnapshot;
import com.rieno.gadgetsandgizmos.neoforge.ShippingRouteOverlayService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Request one validated edit to a visible Shipping Schedule route spline
public record ShippingRouteEditPayload(UUID ownerId, UUID routeId,
                                       ShippingRouteSplineSnapshot.EditAction action,
                                       int index, Vec3 position)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingRouteEditPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_route_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShippingRouteEditPayload> STREAM_CODEC =
            StreamCodec.of(ShippingRouteEditPayload::encode, ShippingRouteEditPayload::decode);

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

    // Apply one route edit on the server
    public static void handle(ShippingRouteEditPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                ShippingRouteOverlayService.edit(player, payload.ownerId(), payload.routeId(),
                        payload.action(), payload.index(), payload.position());
            }
        });
    }

    // Encode one route edit
    private static void encode(RegistryFriendlyByteBuf buffer, ShippingRouteEditPayload payload) {
        buffer.writeUUID(payload.ownerId());
        buffer.writeUUID(payload.routeId());
        buffer.writeEnum(payload.action());
        buffer.writeVarInt(payload.index());
        writeVec3(buffer, payload.position());
    }

    // Decode one route edit
    private static ShippingRouteEditPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ShippingRouteEditPayload(
                buffer.readUUID(), buffer.readUUID(),
                buffer.readEnum(ShippingRouteSplineSnapshot.EditAction.class),
                buffer.readVarInt(), readVec3(buffer));
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
