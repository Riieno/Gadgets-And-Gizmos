package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Send Advanced Controller Public Share
public record AdvancedControllerPublicSharePayload(BlockPos pos, UUID subLevelId,
                                                   boolean available, boolean completed,
                                                   boolean success, String message,
                                                   String url) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerPublicSharePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_public_share"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerPublicSharePayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            AdvancedControllerPublicSharePayload::encode,
            AdvancedControllerPublicSharePayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller public share
    public AdvancedControllerPublicSharePayload {
        message = message == null ? "" : message;
        url = url == null ? "" : url;
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

    // Handle the advanced controller public share
    public static void handle(AdvancedControllerPublicSharePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screenClass
                        .getMethod("applyPublicGraphShare", BlockPos.class, UUID.class,
                                boolean.class, boolean.class, boolean.class, String.class, String.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.available(),
                                payload.completed(), payload.success(), payload.message(), payload.url());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced controller public share
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerPublicSharePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) buffer.writeUUID(payload.subLevelId());
        buffer.writeBoolean(payload.available());
        buffer.writeBoolean(payload.completed());
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.message(), 512);
        buffer.writeUtf(payload.url(), 512);
    }

    // Decode the advanced controller public share
    private static AdvancedControllerPublicSharePayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new AdvancedControllerPublicSharePayload(
                pos,
                subLevelId,
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(512),
                buffer.readUtf(512));
    }
}
