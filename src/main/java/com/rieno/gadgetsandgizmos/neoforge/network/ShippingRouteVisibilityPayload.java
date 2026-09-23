package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.neoforge.ShippingRouteOverlayService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Toggle one player's server-authoritative Shipping Schedule route overlay
public record ShippingRouteVisibilityPayload(@Nullable InteractionHand hand,
                                             @Nullable UUID ownerId,
                                             boolean visible)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingRouteVisibilityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_route_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShippingRouteVisibilityPayload> STREAM_CODEC =
            StreamCodec.of(ShippingRouteVisibilityPayload::encode,
                    ShippingRouteVisibilityPayload::decode);

    // Select route visibility through the held schedule item.
    public ShippingRouteVisibilityPayload(InteractionHand hand, boolean visible) {
        this(hand, null, visible);
    }

    // Select route visibility directly from the owning SCM schedule graph.
    public ShippingRouteVisibilityPayload(UUID ownerId, boolean visible) {
        this(null, ownerId, visible);
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

    // Apply one route-overlay preference on the server
    public static void handle(ShippingRouteVisibilityPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (payload.ownerId() != null) {
                    ShippingRouteOverlayService.setVisible(
                            player, payload.ownerId(), payload.visible());
                } else if (payload.hand() != null) {
                    ShippingRouteOverlayService.setVisible(
                            player, payload.hand(), payload.visible());
                }
            }
        });
    }

    // Encode the route-overlay preference
    private static void encode(RegistryFriendlyByteBuf buffer,
                               ShippingRouteVisibilityPayload payload) {
        buffer.writeBoolean(payload.ownerId() != null);
        if (payload.ownerId() != null) buffer.writeUUID(payload.ownerId());
        else buffer.writeEnum(payload.hand() == null
                ? InteractionHand.MAIN_HAND : payload.hand());
        buffer.writeBoolean(payload.visible());
    }

    // Decode the route-overlay preference
    private static ShippingRouteVisibilityPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean directOwner = buffer.readBoolean();
        return directOwner
                ? new ShippingRouteVisibilityPayload(buffer.readUUID(), buffer.readBoolean())
                : new ShippingRouteVisibilityPayload(
                buffer.readEnum(InteractionHand.class), buffer.readBoolean());
    }
}
