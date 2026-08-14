package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Handle Advanced Controller Graph Action Result
public record AdvancedControllerGraphActionResultPayload(BlockPos pos, UUID subLevelId, long requestId,
                                                         boolean success, String message, int serverRevision,
                                                         boolean saveAttempted, boolean graphSaved,
                                                         List<AdvancedGraphValidator.Diagnostic> diagnostics)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerGraphActionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_graph_action_result"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerGraphActionResultPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AdvancedControllerGraphActionResultPayload::encode,
                    AdvancedControllerGraphActionResultPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller graph action result
    public AdvancedControllerGraphActionResultPayload {
        message = message == null ? "" : message;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
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

    // Handle the advanced controller graph action result
    public static void handle(AdvancedControllerGraphActionResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screenClass
                        .getMethod("applyGraphActionResult", BlockPos.class, UUID.class, long.class,
                                boolean.class, String.class, int.class, boolean.class, boolean.class, List.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.requestId(),
                                payload.success(), payload.message(), payload.serverRevision(),
                                payload.saveAttempted(), payload.graphSaved(), payload.diagnostics());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced controller graph action result
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerGraphActionResultPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeLong(payload.requestId());
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.message());
        buffer.writeVarInt(payload.serverRevision());
        buffer.writeBoolean(payload.saveAttempted());
        buffer.writeBoolean(payload.graphSaved());
        buffer.writeVarInt(Math.min(payload.diagnostics().size(), 128));
        for (int i = 0; i < payload.diagnostics().size() && i < 128; i++) {
            AdvancedGraphValidator.Diagnostic diagnostic = payload.diagnostics().get(i);
            buffer.writeUtf(diagnostic.severity(), 32);
            buffer.writeUtf(diagnostic.code(), 128);
            buffer.writeUtf(diagnostic.message(), 1024);
            buffer.writeUtf(diagnostic.nodeId(), 128);
            buffer.writeUtf(diagnostic.edgeId(), 128);
        }
    }

    // Decode the advanced controller graph action result
    private static AdvancedControllerGraphActionResultPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        long requestId = buffer.readLong();
        boolean success = buffer.readBoolean();
        String msg = buffer.readUtf();
        int serverRevision = buffer.readVarInt();
        boolean saveAttempted = buffer.readBoolean();
        boolean graphSaved = buffer.readBoolean();
        int diagnosticCount = Math.min(buffer.readVarInt(), 128);
        List<AdvancedGraphValidator.Diagnostic> diagnostics = new ArrayList<>(diagnosticCount);
        for (int i = 0; i < diagnosticCount; i++) {
            diagnostics.add(new AdvancedGraphValidator.Diagnostic(
                    buffer.readUtf(32), buffer.readUtf(128), buffer.readUtf(1024),
                    buffer.readUtf(128), buffer.readUtf(128)));
        }
        return new AdvancedControllerGraphActionResultPayload(
                pos, subLevelId, requestId, success, msg,
                serverRevision, saveAttempted, graphSaved, diagnostics);
    }
}
