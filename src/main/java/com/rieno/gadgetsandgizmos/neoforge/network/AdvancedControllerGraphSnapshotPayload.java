package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Send one revisioned graph snapshot so the editor cannot overwrite newer server changes
public record AdvancedControllerGraphSnapshotPayload(
        BlockPos pos,
        UUID subLevelId,
        int draftRevision,
        int activeRevision,
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

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_BYTES = 64 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CHUNKS = MAX_COMPRESSED_BYTES / CHUNK_BYTES;
    private static final int MAX_CONCURRENT_CLIENT_TRANSFERS = 8;
    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000L;
    private static final Map<UUID, SnapshotAssembly> CLIENT_TRANSFERS = new LinkedHashMap<>();
    private static final Map<GraphKey, GraphSnapshot> CLIENT_SNAPSHOTS =
            new LinkedHashMap<>(16, 0.75F, true) {
                // Remove the eldest entry
                @Override
                protected boolean removeEldestEntry(Map.Entry<GraphKey, GraphSnapshot> eldest) {
                    return size() > 32;
                }
            };
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared client handler
    private static ClientHandler clientHandler = (pos, subLevelId, snapshot) -> {
    };

    public static final Type<AdvancedControllerGraphSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_graph_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerGraphSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerGraphSnapshotPayload::encode,
                    AdvancedControllerGraphSnapshotPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller graph snapshot
    public AdvancedControllerGraphSnapshotPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
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

    // Send the advanced controller graph snapshot
    public static void send(ServerPlayer player, BlockPos pos, UUID subLevelId,
                            AdvancedGraphDocument draft, AdvancedGraphDocument active) {
        if (player == null || pos == null) {
            return;
        }
        CompoundTag graphs = new CompoundTag();
        graphs.put("Draft", draft == null ? new CompoundTag() : draft.toTag());
        graphs.put("Active", active == null ? new CompoundTag() : active.toTag());
        byte[] compressed = compress(graphs);
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            LOGGER.warn("Skipped oversized advanced controller graph snapshot at {} ({} compressed bytes)",
                    pos, compressed.length);
            return;
        }

        int draftRevision = draft == null ? 0 : draft.revision();
        int activeRevision = active == null ? 0 : active.revision();
        UUID transferId = UUID.randomUUID();
        int chunkCount = Math.max(1, (compressed.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        for (int idx = 0; idx < chunkCount; idx++) {
            int start = idx * CHUNK_BYTES;
            int end = Math.min(compressed.length, start + CHUNK_BYTES);
            PacketDistributor.sendToPlayer(player, new AdvancedControllerGraphSnapshotPayload(
                    pos, subLevelId, draftRevision, activeRevision, transferId,
                    idx, chunkCount, Arrays.copyOfRange(compressed, start, end)));
        }
    }

    // Get the client snapshot
    public static GraphSnapshot clientSnapshot(BlockPos pos, UUID subLevelId,
                                               int draftRevision, int activeRevision) {
        GraphSnapshot snapshot = CLIENT_SNAPSHOTS.get(new GraphKey(pos, subLevelId));
        if (snapshot == null
                || snapshot.draftRevision() != draftRevision
                || snapshot.activeRevision() != activeRevision) {
            return GraphSnapshot.EMPTY;
        }
        return snapshot.copy();
    }

    // Get the latest client snapshot
    public static GraphSnapshot latestClientSnapshot(BlockPos pos, UUID subLevelId) {
        return CLIENT_SNAPSHOTS.get(new GraphKey(pos, subLevelId));
    }

    // Clear the client state
    public static void clearClientState() {
        CLIENT_TRANSFERS.clear();
        CLIENT_SNAPSHOTS.clear();
    }

    // Install the client handler
    public static void installClientHandler(ClientHandler handler) {
        clientHandler = handler == null ? (pos, subLevelId, snapshot) -> {
        } : handler;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advanced controller graph snapshot
    public static void handle(AdvancedControllerGraphSnapshotPayload payload, IPayloadContext ctx) {
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
            CompoundTag graphs = decompress(assembly.join());
            if (graphs == null) {
                return;
            }
            GraphSnapshot snapshot = new GraphSnapshot(
                    payload.draftRevision(),
                    payload.activeRevision(),
                    graphs.getCompound("Draft"),
                    graphs.getCompound("Active"));
            CLIENT_SNAPSHOTS.put(
                    new GraphKey(payload.pos(), payload.subLevelId()), snapshot);
            clientHandler.apply(payload.pos(), payload.subLevelId(), snapshot);
        });
    }

    // Check if this is valid
    private boolean valid() {
        return chunkCount > 0
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
            NbtIo.writeCompressed(tag, output);
            return output.toByteArray();
        } catch (IOException err) {
            LOGGER.warn("Failed to encode advanced controller graph snapshot", err);
            return new byte[0];
        }
    }

    // Get the decompress
    private static CompoundTag decompress(byte[] bytes) {
        try {
            return NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes),
                    NbtAccounter.create(128L * 1024L * 1024L));
        } catch (IOException | RuntimeException err) {
            LOGGER.warn("Failed to decode advanced controller graph snapshot", err);
            return null;
        }
    }

    // Encode the advanced controller graph snapshot
    private static void encode(RegistryFriendlyByteBuf buffer,
                               AdvancedControllerGraphSnapshotPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.draftRevision());
        buffer.writeVarInt(payload.activeRevision());
        buffer.writeUUID(payload.transferId());
        buffer.writeVarInt(payload.chunkIndex());
        buffer.writeVarInt(payload.chunkCount());
        buffer.writeByteArray(payload.data());
    }

    // Decode the advanced controller graph snapshot
    private static AdvancedControllerGraphSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new AdvancedControllerGraphSnapshotPayload(
                pos,
                subLevelId,
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readByteArray(CHUNK_BYTES));
    }

    // Store the graph snapshot
    public record GraphSnapshot(int draftRevision, int activeRevision,
                                CompoundTag draft, CompoundTag active) {
        private static final GraphSnapshot EMPTY =
                new GraphSnapshot(0, 0, new CompoundTag(), new CompoundTag());

        // Initialize the graph snapshot
        public GraphSnapshot {
            draft = draft == null ? new CompoundTag() : draft.copy();
            active = active == null ? new CompoundTag() : active.copy();
        }

        // Get the draft
        public CompoundTag draft() {
            return draft.copy();
        }

        // Get the active
        public CompoundTag active() {
            return active.copy();
        }

        // Copy the graph snapshot
        private GraphSnapshot copy() {
            return new GraphSnapshot(draftRevision, activeRevision, draft, active);
        }
    }

    // Store the graph key
    private record GraphKey(BlockPos pos, UUID subLevelId) {
        // Initialize the graph key
        private GraphKey {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
        }
    }

    // Expose the client handler
    @FunctionalInterface
    public interface ClientHandler {
        // Apply the client handler
        void apply(BlockPos pos, UUID subLevelId, GraphSnapshot snapshot);
    }

    // Handle the snapshot assembly
    private static final class SnapshotAssembly {
        // Snapshot assembly position
        private final BlockPos pos;
        // Sub-level id
        private final UUID subLevelId;
        // Draft revision
        private final int draftRevision;
        // Active revision
        private final int activeRevision;
        // Chunks
        private final byte[][] chunks;
        // Current received
        private int received;
        // Current total bytes
        private int totalBytes;
        // Last update time
        private long updatedAt = Util.getMillis();

        // Initialize the snapshot assembly
        private SnapshotAssembly(AdvancedControllerGraphSnapshotPayload first) {
            pos = first.pos();
            subLevelId = first.subLevelId();
            draftRevision = first.draftRevision();
            activeRevision = first.activeRevision();
            chunks = new byte[first.chunkCount()][];
        }

        // Accept the snapshot assembly
        private boolean accept(AdvancedControllerGraphSnapshotPayload payload) {
            if (!pos.equals(payload.pos())
                    || !java.util.Objects.equals(subLevelId, payload.subLevelId())
                    || draftRevision != payload.draftRevision()
                    || activeRevision != payload.activeRevision()
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
