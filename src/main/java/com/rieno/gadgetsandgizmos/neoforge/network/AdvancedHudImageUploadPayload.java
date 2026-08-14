package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageStore;
import net.minecraft.Util;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Upload Advanced HUD Image
public record AdvancedHudImageUploadPayload(UUID transferId, String filename,
                                            int chunkIndex, int chunkCount, byte[] data)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000L;
    private static final int MAX_CHUNKS = (AdvancedHudImageStore.MAX_IMAGE_BYTES
            + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES - 1)
            / AdvancedHudImageStore.TRANSFER_CHUNK_BYTES;
    private static final int MAX_ACTIVE_UPLOADS_PER_PLAYER = 4;
    private static final Map<TransferKey, UploadAssembly> UPLOADS = new HashMap<>();

    public static final Type<AdvancedHudImageUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_hud_image_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedHudImageUploadPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedHudImageUploadPayload::encode, AdvancedHudImageUploadPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image upload
    public AdvancedHudImageUploadPayload {
        transferId = transferId == null ? UUID.randomUUID() : transferId;
        filename = filename == null ? "" : filename;
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

    // Send the advanced HUD image upload
    public static void send(String filename, byte[] imageData) {
        UUID transferId = UUID.randomUUID();
        int chunkCount = Math.max(1, (imageData.length + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES - 1)
                / AdvancedHudImageStore.TRANSFER_CHUNK_BYTES);
        for (int idx = 0; idx < chunkCount; idx++) {
            int start = idx * AdvancedHudImageStore.TRANSFER_CHUNK_BYTES;
            int end = Math.min(imageData.length, start + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES);
            PacketDistributor.sendToServer(new AdvancedHudImageUploadPayload(
                    transferId, filename, idx, chunkCount, Arrays.copyOfRange(imageData, start, end)));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advanced HUD image upload
    public static void handle(AdvancedHudImageUploadPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            cleanupExpired();
            if (!(player.containerMenu instanceof AdvancedContraptionControllerMenu)) {
                sendResult(player, "", "Open an Advanced Contraption Controller to upload HUD images.");
                return;
            }
            if (!validChunk(payload)) {
                sendResult(player, "", "The image upload was rejected.");
                return;
            }
            TransferKey key = new TransferKey(player.getUUID(), payload.transferId());
            if (!UPLOADS.containsKey(key) && UPLOADS.keySet().stream()
                    .filter(active -> active.playerId().equals(player.getUUID()))
                    .count() >= MAX_ACTIVE_UPLOADS_PER_PLAYER) {
                sendResult(player, "", "Too many image uploads are already in progress.");
                return;
            }
            UploadAssembly assembly = UPLOADS.computeIfAbsent(key,
                    ignored -> new UploadAssembly(payload.filename(), payload.chunkCount()));
            if (!assembly.accept(payload)) {
                UPLOADS.remove(key);
                sendResult(player, "", "The image upload was incomplete or invalid.");
                return;
            }
            if (!assembly.complete()) {
                return;
            }
            UPLOADS.remove(key);
            byte[] imageData = assembly.join();
            AdvancedHudImageStore.SaveResult res = AdvancedHudImageStore.save(
                    player.getServer(), payload.filename(), imageData);
            sendResult(player, res.success() ? res.storedName() : "", res.message());
            if (res.success()) {
                AdvancedHudImageDataPayload.send(player, res.storedName(), imageData);
            }
        });
    }

    // Check if the chunk is valid
    private static boolean validChunk(AdvancedHudImageUploadPayload payload) {
        return !payload.filename().isBlank()
                && payload.filename().length() <= 128
                && payload.chunkCount() > 0
                && payload.chunkCount() <= MAX_CHUNKS
                && payload.chunkIndex() >= 0
                && payload.chunkIndex() < payload.chunkCount()
                && payload.data().length > 0
                && payload.data().length <= AdvancedHudImageStore.TRANSFER_CHUNK_BYTES;
    }

    // Send the result
    private static void sendResult(ServerPlayer player, String uploadedName, String msg) {
        PacketDistributor.sendToPlayer(player, new AdvancedHudImageCatalogPayload(
                AdvancedHudImageStore.list(player.getServer()), uploadedName, msg, true));
    }

    // Clean up the expired
    private static void cleanupExpired() {
        long oldest = Util.getMillis() - TRANSFER_TIMEOUT_MILLIS;
        Iterator<UploadAssembly> iterator = UPLOADS.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().updatedAt < oldest) {
                iterator.remove();
            }
        }
    }

    // Encode the advanced HUD image upload
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedHudImageUploadPayload payload) {
        buffer.writeUUID(payload.transferId());
        buffer.writeUtf(payload.filename(), 128);
        buffer.writeVarInt(payload.chunkIndex());
        buffer.writeVarInt(payload.chunkCount());
        buffer.writeByteArray(payload.data());
    }

    // Decode the advanced HUD image upload
    private static AdvancedHudImageUploadPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AdvancedHudImageUploadPayload(
                buffer.readUUID(),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(AdvancedHudImageStore.TRANSFER_CHUNK_BYTES));
    }

    // Store the transfer key
    private record TransferKey(UUID playerId, UUID transferId) {
    }

    // Handle the upload assembly
    private static final class UploadAssembly {
        // Filename
        private final String filename;
        // Chunks
        private final byte[][] chunks;
        // Current received
        private int received;
        // Current total bytes
        private int totalBytes;
        // Last update time
        private long updatedAt = Util.getMillis();

        // Initialize the upload assembly
        private UploadAssembly(String filename, int chunkCount) {
            this.filename = filename;
            this.chunks = new byte[chunkCount][];
        }

        // Accept the upload assembly
        private boolean accept(AdvancedHudImageUploadPayload payload) {
            if (!filename.equals(payload.filename()) || chunks.length != payload.chunkCount()) {
                return false;
            }
            int idx = payload.chunkIndex();
            if (chunks[idx] != null) {
                return Arrays.equals(chunks[idx], payload.data());
            }
            if (totalBytes + payload.data().length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                return false;
            }
            chunks[idx] = payload.data().clone();
            received++;
            totalBytes += payload.data().length;
            updatedAt = Util.getMillis();
            return true;
        }

        // Check if this is complete
        private boolean complete() {
            return received == chunks.length;
        }

        // Join the upload assembly
        private byte[] join() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                output.writeBytes(chunk);
            }
            return output.toByteArray();
        }
    }
}
