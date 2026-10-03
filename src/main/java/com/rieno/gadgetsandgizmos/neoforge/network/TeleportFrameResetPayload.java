package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells the arriving player's client to leave its old Sable motion frame. */
public record TeleportFrameResetPayload(ResourceLocation dimension) implements CustomPacketPayload {
    public static final Type<TeleportFrameResetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "teleport_frame_reset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportFrameResetPayload> STREAM_CODEC =
            StreamCodec.of(TeleportFrameResetPayload::encode, TeleportFrameResetPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleportFrameResetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> client = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.TeleportFrameResetClient");
                client.getMethod("handle", TeleportFrameResetPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TeleportFrameResetPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
    }

    private static TeleportFrameResetPayload decode(RegistryFriendlyByteBuf buffer) {
        return new TeleportFrameResetPayload(buffer.readResourceLocation());
    }
}
