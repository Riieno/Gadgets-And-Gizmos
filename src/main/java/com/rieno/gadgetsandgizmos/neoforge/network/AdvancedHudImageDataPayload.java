package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.UUID;

// Send Advanced HUD Image Data
public record AdvancedHudImageDataPayload(String filename, UUID transferId,
                                          int chunkIndex, int chunkCount, byte[] data)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedHudImageDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_hud_image_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedHudImageDataPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedHudImageDataPayload::encode, AdvancedHudImageDataPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image data
    public AdvancedHudImageDataPayload {
        filename = filename == null ? "" : filename;
        transferId = transferId == null ? UUID.randomUUID() : transferId;
        data = data == null ? new byte[0] : data.clone();
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

    // Send the advanced HUD image data
    public static void send(ServerPlayer player, String filename, byte[] imageData) {
        UUID transferId = UUID.randomUUID();
        int chunkCount = Math.max(1, (imageData.length + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES - 1)
                / AdvancedHudImageStore.TRANSFER_CHUNK_BYTES);
        for (int idx = 0; idx < chunkCount; idx++) {
            int start = idx * AdvancedHudImageStore.TRANSFER_CHUNK_BYTES;
            int end = Math.min(imageData.length, start + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES);
            PacketDistributor.sendToPlayer(player, new AdvancedHudImageDataPayload(
                    filename, transferId, idx, chunkCount, Arrays.copyOfRange(imageData, start, end)));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advanced HUD image data
    public static void handle(AdvancedHudImageDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> client = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedHudImageClient");
                client.getMethod("acceptImageChunk", String.class, UUID.class,
                                int.class, int.class, byte[].class)
                        .invoke(null, payload.filename(), payload.transferId(),
                                payload.chunkIndex(), payload.chunkCount(), payload.data());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced HUD image data
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedHudImageDataPayload payload) {
        buffer.writeUtf(payload.filename(), 96);
        buffer.writeUUID(payload.transferId());
        buffer.writeVarInt(payload.chunkIndex());
        buffer.writeVarInt(payload.chunkCount());
        buffer.writeByteArray(payload.data());
    }

    // Decode the advanced HUD image data
    private static AdvancedHudImageDataPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AdvancedHudImageDataPayload(
                buffer.readUtf(96),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(AdvancedHudImageStore.TRANSFER_CHUNK_BYTES));
    }
}
