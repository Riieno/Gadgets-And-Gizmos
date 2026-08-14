package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Advanced Controller Graph History
public record AdvancedControllerGraphHistoryPayload(
        BlockPos pos, UUID subLevelId, List<AdvancedGraphVersionHistory.Entry> entries,
        String message, CompoundTag restoredGraph) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerGraphHistoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_graph_history"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerGraphHistoryPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AdvancedControllerGraphHistoryPayload::encode,
                    AdvancedControllerGraphHistoryPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller graph history
    public AdvancedControllerGraphHistoryPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        message = message == null ? "" : message;
        restoredGraph = restoredGraph == null ? new CompoundTag() : restoredGraph.copy();
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

    // Handle the advanced controller graph history
    public static void handle(AdvancedControllerGraphHistoryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screenClass.getMethod("applyGraphHistory", BlockPos.class, UUID.class, List.class,
                                String.class, CompoundTag.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.entries(),
                                payload.message(), payload.restoredGraph());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced controller graph history
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerGraphHistoryPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        int size = Math.min(payload.entries().size(), AdvancedGraphVersionHistory.MAX_VERSIONS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            AdvancedGraphVersionHistory.Entry entry = payload.entries().get(i);
            buffer.writeVarInt(entry.index());
            buffer.writeVarInt(entry.revision());
            buffer.writeLong(entry.savedAt());
        }
        buffer.writeUtf(payload.message(), 512);
        GraphNbtPayloadCodec.write(buffer, payload.restoredGraph());
    }

    // Decode the advanced controller graph history
    private static AdvancedControllerGraphHistoryPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = Math.min(buffer.readVarInt(), AdvancedGraphVersionHistory.MAX_VERSIONS);
        List<AdvancedGraphVersionHistory.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new AdvancedGraphVersionHistory.Entry(
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readLong()));
        }
        String msg = buffer.readUtf(512);
        CompoundTag restoredGraph = GraphNbtPayloadCodec.read(buffer);
        return new AdvancedControllerGraphHistoryPayload(
                pos, subLevelId, entries, msg,
                restoredGraph == null ? new CompoundTag() : restoredGraph);
    }
}
