package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Portable Contraption Controller Mode
public record PortableContraptionControllerModePayload(InteractionHand hand, boolean advanced,
                                                       boolean active) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<PortableContraptionControllerModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "portable_contraption_controller_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableContraptionControllerModePayload> STREAM_CODEC =
            StreamCodec.of(PortableContraptionControllerModePayload::encode, PortableContraptionControllerModePayload::decode);

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

    // Handle the portable contraption controller mode
    public static void handle(PortableContraptionControllerModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PortableContraptionControllerRuntime.setActive(player, payload.hand(), payload.advanced(), payload.active());
            }
        });
    }

    // Encode the portable contraption controller mode
    private static void encode(RegistryFriendlyByteBuf buffer, PortableContraptionControllerModePayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.advanced());
        buffer.writeBoolean(payload.active());
    }

    // Decode the portable contraption controller mode
    private static PortableContraptionControllerModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new PortableContraptionControllerModePayload(
                buffer.readEnum(InteractionHand.class),
                buffer.readBoolean(),
                buffer.readBoolean());
    }
}
