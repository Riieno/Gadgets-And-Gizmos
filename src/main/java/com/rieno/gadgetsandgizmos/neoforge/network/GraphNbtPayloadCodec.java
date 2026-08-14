package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// Send Graph NBT Payload Codec
final class GraphNbtPayloadCodec {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph NBT payload codec
    private GraphNbtPayloadCodec() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the graph NBT payload codec
    static void write(RegistryFriendlyByteBuf buffer, CompoundTag tag) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag == null ? new CompoundTag() : tag, output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_COMPRESSED_BYTES) {
                throw new EncoderException("Compressed graph payload exceeds 16 MiB");
            }
            buffer.writeByteArray(bytes);
        } catch (IOException err) {
            throw new EncoderException("Failed to encode compressed graph payload", err);
        }
    }

    // Read the graph NBT payload codec
    static CompoundTag read(RegistryFriendlyByteBuf buffer) {
        byte[] bytes = buffer.readByteArray(MAX_COMPRESSED_BYTES);
        try {
            return NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes),
                    NbtAccounter.create(128L * 1024L * 1024L));
        } catch (IOException | RuntimeException err) {
            throw new DecoderException("Failed to decode compressed graph payload", err);
        }
    }
}
