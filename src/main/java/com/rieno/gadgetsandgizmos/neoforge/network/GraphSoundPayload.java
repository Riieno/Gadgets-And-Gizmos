package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Send Graph Sound
public record GraphSoundPayload(String playbackId, String soundId, double x, double y, double z,
                                float volume, float pitch, boolean loop, boolean stop,
                                @Nullable UUID subLevelId, double localX, double localY, double localZ)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<GraphSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "graph_sound"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, GraphSoundPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(GraphSoundPayload::encode, GraphSoundPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep callers which supply a fixed world position compatible
    public GraphSoundPayload(String playbackId, String soundId, double x, double y, double z,
            float volume, float pitch, boolean loop, boolean stop){
        this(playbackId, soundId, x, y, z, volume, pitch, loop, stop, null, x, y, z);
    }

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

    // Handle the graph sound
    public static void handle(GraphSoundPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> client = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.GraphSoundClient");
                client.getMethod("handle", GraphSoundPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the graph sound
    private static void encode(RegistryFriendlyByteBuf buffer, GraphSoundPayload payload) {
        buffer.writeUtf(payload.playbackId(), 256);
        buffer.writeUtf(payload.soundId(), 256);
        buffer.writeDouble(payload.x());
        buffer.writeDouble(payload.y());
        buffer.writeDouble(payload.z());
        buffer.writeFloat(payload.volume());
        buffer.writeFloat(payload.pitch());
        buffer.writeBoolean(payload.loop());
        buffer.writeBoolean(payload.stop());
        buffer.writeBoolean(payload.subLevelId() != null);
        if(payload.subLevelId() != null){
            buffer.writeUUID(payload.subLevelId());
            buffer.writeDouble(payload.localX());
            buffer.writeDouble(payload.localY());
            buffer.writeDouble(payload.localZ());
        }
    }

    // Decode the graph sound
    private static GraphSoundPayload decode(RegistryFriendlyByteBuf buffer) {
        GraphSoundPayload fixed = new GraphSoundPayload(
                buffer.readUtf(256), buffer.readUtf(256),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean());
        if(!buffer.readBoolean()) return fixed;
        return new GraphSoundPayload(fixed.playbackId(), fixed.soundId(), fixed.x(), fixed.y(), fixed.z(),
                fixed.volume(), fixed.pitch(), fixed.loop(), fixed.stop(), buffer.readUUID(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
