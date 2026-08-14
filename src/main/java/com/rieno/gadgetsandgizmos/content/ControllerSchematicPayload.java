package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.Create;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

// Carry Controller Schematic state between the client and server
final class ControllerSchematicPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String BLOCK_ENTITY_TAG = "ControllerSchematicPayload";
    static final String CONTROLLER_DATA_TAG = "ControllerData";
    static final String LINKER_DATA_TAG = "LinkerData";
    static final String CONTROLLER_KIND_TAG = "ControllerKind";
    static final String PLACEMENT_PREPARED_TAG = "PlacementPrepared";

    private static final int STORAGE_VERSION = 1;
    private static final int CHUNK_BYTES = 32 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CHUNKS =
            (MAX_COMPRESSED_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;
    private static final long MAX_DECOMPRESSED_BYTES = 128L * 1024L * 1024L;
    private static final String VERSION_TAG = "Version";
    private static final String COMPRESSED_BYTES_TAG = "CompressedBytes";
    private static final String CHUNKS_TAG = "Chunks";
    private static final String CHUNK_DATA_TAG = "Data";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller schematic
    private ControllerSchematicPayload() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the controller schematic
    static boolean write(CompoundTag blockEntityTag, CompoundTag payload) {
        if (blockEntityTag == null || payload == null || payload.isEmpty()) {
            return false;
        }
        blockEntityTag.remove(BLOCK_ENTITY_TAG);
        byte[] compressed = compress(payload);
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            Create.LOGGER.warn("Skipped controller schematic payload with {} compressed bytes",
                    compressed.length);
            return false;
        }

        CompoundTag envelope = new CompoundTag();
        envelope.putInt(VERSION_TAG, STORAGE_VERSION);
        envelope.putInt(COMPRESSED_BYTES_TAG, compressed.length);
        ListTag chunks = new ListTag();
        for (int offset = 0; offset < compressed.length; offset += CHUNK_BYTES) {
            CompoundTag chunk = new CompoundTag();
            chunk.putByteArray(CHUNK_DATA_TAG, Arrays.copyOfRange(
                    compressed, offset, Math.min(compressed.length, offset + CHUNK_BYTES)));
            chunks.add(chunk);
        }
        envelope.put(CHUNKS_TAG, chunks);
        blockEntityTag.put(BLOCK_ENTITY_TAG, envelope);
        return true;
    }

    // Take the controller schematic payload
    static @Nullable CompoundTag take(CompoundTag blockEntityTag) {
        if (blockEntityTag == null
                || !blockEntityTag.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag envelope = blockEntityTag.getCompound(BLOCK_ENTITY_TAG).copy();
        blockEntityTag.remove(BLOCK_ENTITY_TAG);
        return decode(envelope);
    }

    // Get the compress
    private static byte[] compress(CompoundTag payload) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(payload, output);
            return output.toByteArray();
        } catch (IOException | RuntimeException err) {
            Create.LOGGER.warn("Failed to encode controller schematic payload", err);
            return new byte[0];
        }
    }

    // Decode the controller schematic
    private static @Nullable CompoundTag decode(CompoundTag envelope) {
        if (envelope.getInt(VERSION_TAG) != STORAGE_VERSION) {
            Create.LOGGER.warn("Ignored controller schematic payload version {}",
                    envelope.getInt(VERSION_TAG));
            return null;
        }
        int expectedBytes = envelope.getInt(COMPRESSED_BYTES_TAG);
        ListTag chunks = envelope.getList(CHUNKS_TAG, Tag.TAG_COMPOUND);
        if (expectedBytes <= 0 || expectedBytes > MAX_COMPRESSED_BYTES
                || chunks.isEmpty() || chunks.size() > MAX_CHUNKS) {
            Create.LOGGER.warn("Ignored malformed controller schematic payload");
            return null;
        }

        ByteArrayOutputStream joined = new ByteArrayOutputStream(expectedBytes);
        for (int idx = 0; idx < chunks.size(); idx++) {
            byte[] data = chunks.getCompound(idx).getByteArray(CHUNK_DATA_TAG);
            if (data.length == 0 || data.length > CHUNK_BYTES
                    || joined.size() + data.length > expectedBytes) {
                Create.LOGGER.warn("Ignored malformed controller schematic payload chunk {}",
                        idx);
                return null;
            }
            joined.writeBytes(data);
        }
        if (joined.size() != expectedBytes) {
            Create.LOGGER.warn("Ignored incomplete controller schematic payload");
            return null;
        }

        try {
            return NbtIo.readCompressed(
                    new ByteArrayInputStream(joined.toByteArray()),
                    NbtAccounter.create(MAX_DECOMPRESSED_BYTES));
        } catch (IOException | RuntimeException err) {
            Create.LOGGER.warn("Failed to decode controller schematic payload", err);
            return null;
        }
    }
}
