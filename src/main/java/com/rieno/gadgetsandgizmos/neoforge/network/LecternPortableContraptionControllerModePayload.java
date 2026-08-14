package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Lectern Portable Contraption Controller Mode
public record LecternPortableContraptionControllerModePayload(BlockPos pos, boolean advanced,
                                                              boolean active) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<LecternPortableContraptionControllerModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "lectern_portable_contraption_controller_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LecternPortableContraptionControllerModePayload> STREAM_CODEC =
            StreamCodec.of(LecternPortableContraptionControllerModePayload::encode,
                    LecternPortableContraptionControllerModePayload::decode);

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

    // Handle the lectern portable contraption controller mode
    public static void handle(LecternPortableContraptionControllerModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PortableContraptionControllerRuntime.setLecternActive(
                        player, payload.pos(), payload.advanced(), payload.active());
            }
        });
    }

    // Encode the lectern portable contraption controller mode
    private static void encode(RegistryFriendlyByteBuf buffer,
                               LecternPortableContraptionControllerModePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.advanced());
        buffer.writeBoolean(payload.active());
    }

    // Decode the lectern portable contraption controller mode
    private static LecternPortableContraptionControllerModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new LecternPortableContraptionControllerModePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readBoolean(),
                buffer.readBoolean());
    }
}
