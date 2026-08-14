package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Analogue Contraption Controller Discovery Results
public record AnalogueContraptionControllerDiscoveryResultsPayload(BlockPos pos, UUID subLevelId, List<ControllerDiscoveryNode> nodes) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerDiscoveryResultsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_discovery_results"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerDiscoveryResultsPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AnalogueContraptionControllerDiscoveryResultsPayload::encode,
                    AnalogueContraptionControllerDiscoveryResultsPayload::decode);

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

    // Handle the analogue contraption controller discovery results
    public static void handle(AnalogueContraptionControllerDiscoveryResultsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {

            dispatchToScreen("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerConfigScreen", payload);
            dispatchToScreen("com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen", payload);
        });
    }

    // Dispatch the screen
    private static void dispatchToScreen(String className, AnalogueContraptionControllerDiscoveryResultsPayload payload) {
        try {
            Class<?> screenClass = Class.forName(className);
            screenClass
                    .getMethod("applyDiscoveryResults", BlockPos.class, UUID.class, List.class)
                    .invoke(null, payload.pos(), payload.subLevelId(), payload.nodes());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Encode the analogue contraption controller discovery results
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueContraptionControllerDiscoveryResultsPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.nodes().size());
        for (ControllerDiscoveryNode node : payload.nodes()) {
            buffer.writeNbt(node == null ? new CompoundTag() : node.toTag());
        }
    }

    // Decode the analogue contraption controller discovery results
    private static AnalogueContraptionControllerDiscoveryResultsPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = buffer.readVarInt();
        List<ControllerDiscoveryNode> nodes = new ArrayList<>(size);
        for (int idx = 0; idx < size; idx++) {
            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(emptyIfNull(buffer.readNbt()));
            if (node != null && node.isValid()) {
                nodes.add(node);
            }
        }
        return new AnalogueContraptionControllerDiscoveryResultsPayload(pos, subLevelId, nodes);
    }

    // Get the empty if null
    private static CompoundTag emptyIfNull(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }
}
