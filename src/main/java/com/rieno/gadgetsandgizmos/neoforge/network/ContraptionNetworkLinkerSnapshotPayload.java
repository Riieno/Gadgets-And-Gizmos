package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.simibubi.create.Create;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Send Contraption Network Linker Snapshot
public record ContraptionNetworkLinkerSnapshotPayload(
        String manifestId,
        int revision,
        String hash,
        UUID transferId,
        int chunkIndex,
        int chunkCount,
        byte[] data
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int CHUNK_BYTES = 64 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CHUNKS = MAX_COMPRESSED_BYTES / CHUNK_BYTES;
    private static final int MAX_CONCURRENT_CLIENT_TRANSFERS = 8;
    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000L;
    private static final Map<UUID, Map<String, String>> SENT_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, SnapshotAssembly> CLIENT_TRANSFERS = new HashMap<>();

    public static final Type<ContraptionNetworkLinkerSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "contraption_network_linker_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionNetworkLinkerSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(ContraptionNetworkLinkerSnapshotPayload::encode,
                    ContraptionNetworkLinkerSnapshotPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker snapshot
    public ContraptionNetworkLinkerSnapshotPayload {
        manifestId = manifestId == null ? "" : manifestId;
        hash = hash == null ? "" : hash;
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

    // Send the contraption network linker snapshot if changed
    public static void sendIfChanged(ServerPlayer player, ItemStack linker) {
        if (player == null || linker == null || linker.isEmpty()) {
            return;
        }
        ControllerManifestStore.ManifestSnapshot snapshot =
                ContraptionNetworkLinkerData.clientSnapshotForSync(linker);
        if (snapshot == null || snapshot.id().isBlank()) {
            return;
        }
        String version = snapshot.revision() + ":" + snapshot.hash();
        Map<String, String> sent = SENT_SNAPSHOTS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (version.equals(sent.get(snapshot.id()))) {
            return;
        }

        CompoundTag clientData = ContraptionNetworkLinkerData.clientSnapshotData(snapshot);
        byte[] compressed = compress(clientData);
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            Create.LOGGER.warn("Skipped oversized linker client snapshot {} ({} compressed bytes)",
                    snapshot.id(), compressed.length);
            return;
        }

        UUID transferId = UUID.randomUUID();
        int chunkCount = Math.max(1, (compressed.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int idx = 0; idx < chunkCount; idx++) {
            int start = idx * CHUNK_BYTES;
            int end = Math.min(compressed.length, start + CHUNK_BYTES);
            PacketDistributor.sendToPlayer(player, new ContraptionNetworkLinkerSnapshotPayload(
                    snapshot.id(), snapshot.revision(), snapshot.hash(), transferId,
                    idx, chunkCount, Arrays.copyOfRange(compressed, start, end)));
        }
        sent.put(snapshot.id(), version);
    }

    // Clear the server state
    public static void clearServerState() {
        SENT_SNAPSHOTS.clear();
    }

    // Clear the server state
    public static void clearServerState(UUID playerId) {
        if (playerId != null) {
            SENT_SNAPSHOTS.remove(playerId);
        }
    }

    // Clear the client state
    public static void clearClientState() {
        CLIENT_TRANSFERS.clear();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the contraption network linker snapshot
    public static void handle(ContraptionNetworkLinkerSnapshotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            cleanupExpiredTransfers();
            if (!payload.valid()) {
                CLIENT_TRANSFERS.remove(payload.transferId());
                return;
            }
            SnapshotAssembly assembly = CLIENT_TRANSFERS.get(payload.transferId());
            if (assembly == null) {
                if (CLIENT_TRANSFERS.size() >= MAX_CONCURRENT_CLIENT_TRANSFERS) {
                    return;
                }
                assembly = new SnapshotAssembly(payload);
                CLIENT_TRANSFERS.put(payload.transferId(), assembly);
            }
            if (!assembly.accept(payload)) {
                CLIENT_TRANSFERS.remove(payload.transferId());
                return;
            }
            if (!assembly.complete()) {
                return;
            }
            CLIENT_TRANSFERS.remove(payload.transferId());
            CompoundTag root = decompress(assembly.join());
            if (root != null) {
                ContraptionNetworkLinkerData.installClientSnapshot(
                        payload.manifestId(), payload.revision(), payload.hash(), root);
            }
        });
    }

    // Check if this is valid
    private boolean valid() {
        return !manifestId.isBlank()
                && manifestId.length() <= 128
                && hash.length() <= 128
                && chunkCount > 0
                && chunkCount <= MAX_CHUNKS
                && chunkIndex >= 0
                && chunkIndex < chunkCount
                && data.length > 0
                && data.length <= CHUNK_BYTES;
    }

    // Clean up the expired transfers
    private static void cleanupExpiredTransfers() {
        long oldest = Util.getMillis() - TRANSFER_TIMEOUT_MILLIS;
        Iterator<SnapshotAssembly> iterator = CLIENT_TRANSFERS.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().updatedAt < oldest) {
                iterator.remove();
            }
        }
    }

    // Get the compress
    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag == null ? new CompoundTag() : tag, output);
            return output.toByteArray();
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to encode linker client snapshot", err);
            return new byte[0];
        }
    }

    // Get the decompress
    private static CompoundTag decompress(byte[] bytes) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(64L * 1024L * 1024L));
        } catch (IOException | RuntimeException err) {
            Create.LOGGER.warn("Failed to decode linker client snapshot", err);
            return null;
        }
    }

    // Encode the contraption network linker snapshot
    private static void encode(RegistryFriendlyByteBuf buffer,
                               ContraptionNetworkLinkerSnapshotPayload payload) {
        buffer.writeUtf(payload.manifestId(), 128);
        buffer.writeVarInt(payload.revision());
        buffer.writeUtf(payload.hash(), 128);
        buffer.writeUUID(payload.transferId());
        buffer.writeVarInt(payload.chunkIndex());
        buffer.writeVarInt(payload.chunkCount());
        buffer.writeByteArray(payload.data());
    }

    // Decode the contraption network linker snapshot
    private static ContraptionNetworkLinkerSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ContraptionNetworkLinkerSnapshotPayload(
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(CHUNK_BYTES));
    }

    // Handle the snapshot assembly
    private static final class SnapshotAssembly {
        // Manifest id
        private final String manifestId;
        // Revision
        private final int revision;
        // Hash
        private final String hash;
        // Chunks
        private final byte[][] chunks;
        // Current received
        private int received;
        // Current total bytes
        private int totalBytes;
        // Last update time
        private long updatedAt = Util.getMillis();

        // Initialize the snapshot assembly
        private SnapshotAssembly(ContraptionNetworkLinkerSnapshotPayload first) {
            manifestId = first.manifestId();
            revision = first.revision();
            hash = first.hash();
            chunks = new byte[first.chunkCount()][];
        }

        // Accept the snapshot assembly
        private boolean accept(ContraptionNetworkLinkerSnapshotPayload payload) {
            if (!manifestId.equals(payload.manifestId())
                    || revision != payload.revision()
                    || !hash.equals(payload.hash())
                    || chunks.length != payload.chunkCount()) {
                return false;
            }
            int idx = payload.chunkIndex();
            if (chunks[idx] != null) {
                return Arrays.equals(chunks[idx], payload.data());
            }
            if (totalBytes + payload.data().length > MAX_COMPRESSED_BYTES) {
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

        // Join the snapshot assembly
        private byte[] join() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                output.writeBytes(chunk);
            }
            return output.toByteArray();
        }
    }
}
