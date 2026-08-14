package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Advanced Controller Shared Graphs
public record AdvancedControllerSharedGraphsPayload(BlockPos pos, UUID subLevelId,
                                                    List<ControllerManifestStore.SharedGraphEntry> entries,
                                                    String message,
                                                    String conflictingName,
                                                    CompoundTag graph) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerSharedGraphsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_shared_graphs"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerSharedGraphsPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AdvancedControllerSharedGraphsPayload::encode,
                    AdvancedControllerSharedGraphsPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller shared graphs
    public AdvancedControllerSharedGraphsPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        message = message == null ? "" : message;
        conflictingName = conflictingName == null ? "" : conflictingName;
        graph = graph == null ? new CompoundTag() : graph.copy();
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

    // Handle the advanced controller shared graphs
    public static void handle(AdvancedControllerSharedGraphsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screenClass
                        .getMethod("applySharedGraphManifests", BlockPos.class, UUID.class, List.class,
                                String.class, String.class, CompoundTag.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.entries(), payload.message(),
                                payload.conflictingName(), payload.graph());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced controller shared graphs
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerSharedGraphsPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.entries().size());
        for (ControllerManifestStore.SharedGraphEntry entry : payload.entries()) {
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.name());
        }
        buffer.writeUtf(payload.message());
        buffer.writeUtf(payload.conflictingName());
        GraphNbtPayloadCodec.write(buffer, payload.graph());
    }

    // Decode the advanced controller shared graphs
    private static AdvancedControllerSharedGraphsPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = buffer.readVarInt();
        List<ControllerManifestStore.SharedGraphEntry> entries = new ArrayList<>(size);
        for (int idx = 0; idx < size; idx++) {
            entries.add(new ControllerManifestStore.SharedGraphEntry(buffer.readUtf(), buffer.readUtf()));
        }
        String msg = buffer.readUtf();
        String conflictingName = buffer.readUtf();
        CompoundTag graph = GraphNbtPayloadCodec.read(buffer);
        return new AdvancedControllerSharedGraphsPayload(pos, subLevelId, entries, msg, conflictingName,
                graph == null ? new CompoundTag() : graph);
    }
}
