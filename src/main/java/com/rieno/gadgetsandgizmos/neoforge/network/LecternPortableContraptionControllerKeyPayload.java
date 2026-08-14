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

// Send Lectern Portable Contraption Controller Key
public record LecternPortableContraptionControllerKeyPayload(BlockPos pos, boolean advanced, String channelId,
                                                             boolean pressed) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<LecternPortableContraptionControllerKeyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "lectern_portable_contraption_controller_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LecternPortableContraptionControllerKeyPayload> STREAM_CODEC =
            StreamCodec.of(LecternPortableContraptionControllerKeyPayload::encode,
                    LecternPortableContraptionControllerKeyPayload::decode);

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

    // Handle the lectern portable contraption controller key
    public static void handle(LecternPortableContraptionControllerKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PortableContraptionControllerRuntime.handleLecternKey(
                        player, payload.pos(), payload.advanced(), payload.channelId(), payload.pressed());
            }
        });
    }

    // Encode the lectern portable contraption controller key
    private static void encode(RegistryFriendlyByteBuf buffer,
                               LecternPortableContraptionControllerKeyPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.advanced());
        buffer.writeUtf(payload.channelId());
        buffer.writeBoolean(payload.pressed());
    }

    // Decode the lectern portable contraption controller key
    private static LecternPortableContraptionControllerKeyPayload decode(RegistryFriendlyByteBuf buffer) {
        return new LecternPortableContraptionControllerKeyPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readBoolean());
    }
}
