package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagTypes;
import net.minecraft.nbt.visitors.CollectToTag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// Move oversized payloads out of chunk NBT and restore them after load
public final class OversizedNbtRecovery {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LARGE_LEGACY_FIELDS = Set.of(
            "AdvancedDraftGraph",
            "AdvancedActiveGraph",
            "AdvancedGraphVersions",
            "StoredGraphs",
            "ClientSnapshot",
            "ContraptionNetworkLinker",
            "Nodes",
            "Edges");
    private static final AtomicInteger RECOVERY_LOG_COUNT = new AtomicInteger();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the oversized NBT recovery
    private OversizedNbtRecovery() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the sanitized
    public static @Nullable CompoundTag readSanitized(ByteBuf buffer, int readerIndex,
                                                       NbtAccounterException original) {
        buffer.readerIndex(readerIndex);
        SkippingCollector visitor = new SkippingCollector();
        try {
            ByteBufInputStream input = new ByteBufInputStream(buffer);
            int typeId = input.readByte();
            if (typeId == 0) {
                return null;
            }
            TagType<?> type = TagTypes.getType(typeId);
            type.parseRoot(input, visitor, NbtAccounter.unlimitedHeap());
            Tag res = visitor.getResult();
            if (!visitor.removedLegacyPayload() || !(res instanceof CompoundTag compound)) {
                buffer.readerIndex(readerIndex);
                throw original;
            }
            if (RECOVERY_LOG_COUNT.getAndIncrement() < 8) {
                LOGGER.warn(
                        "Recovered an oversized legacy controller/linker NBT packet by skipping embedded graph data");
            }
            return compound;
        } catch (IOException err) {
            buffer.readerIndex(readerIndex);
            throw new DecoderException("Failed to skip oversized legacy controller/linker NBT", err);
        }
    }

    // Handle the skipping collector
    private static final class SkippingCollector extends CollectToTag {
        // Tracks whether removed legacy payload is set
        private boolean removedLegacyPayload;

        // Visit the entry
        @Override
        public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
            if (LARGE_LEGACY_FIELDS.contains(id)) {
                removedLegacyPayload = true;
                return StreamTagVisitor.EntryResult.SKIP;
            }
            return super.visitEntry(type, id);
        }

        // Check if the legacy payload was removed
        private boolean removedLegacyPayload() {
            return removedLegacyPayload;
        }
    }
}
