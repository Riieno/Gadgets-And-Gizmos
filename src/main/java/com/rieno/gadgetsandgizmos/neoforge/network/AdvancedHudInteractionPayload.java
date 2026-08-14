package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Send Advanced HUD Interaction
public record AdvancedHudInteractionPayload(MenuConfigTarget target, UUID pairId, String nodeId,
                                            String interactionId, ValueKind valueKind,
                                            double numberValue, boolean booleanValue)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedHudInteractionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_hud_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedHudInteractionPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedHudInteractionPayload::encode, AdvancedHudInteractionPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the numeric value
    public static AdvancedHudInteractionPayload number(MenuConfigTarget target, UUID pairId,
                                                       String nodeId, String interactionId, double val) {
        return new AdvancedHudInteractionPayload(
                target, pairId, nodeId, interactionId, ValueKind.NUMBER, val, false);
    }

    // Get the bool
    public static AdvancedHudInteractionPayload bool(MenuConfigTarget target, UUID pairId,
                                                     String nodeId, String interactionId, boolean val) {
        return new AdvancedHudInteractionPayload(
                target, pairId, nodeId, interactionId, ValueKind.BOOLEAN, 0.0D, val);
    }

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

    // Handle the advanced HUD interaction
    public static void handle(AdvancedHudInteractionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || payload.target() == null
                    || payload.pairId() == null
                    || payload.nodeId().isBlank()
                    || payload.interactionId().isBlank()
                    || !AdvancedContraptionControllerBlockEntity.playerHasGogglesPair(player, payload.pairId())) {
                return;
            }

            AdvancedGraphDocument.Value val = payload.valueKind() == ValueKind.BOOLEAN
                    ? AdvancedGraphDocument.Value.bool(payload.booleanValue())
                    : AdvancedGraphDocument.Value.number(payload.numberValue());
            AdvancedContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                    player.level(), payload.target().subLevelId(), payload.target().pos(),
                    AdvancedContraptionControllerBlockEntity.class);
            if (controller != null
                    && controller.getGogglesTrackerPairLabels().containsKey(payload.pairId())) {
                controller.handleHudInteraction(payload.nodeId(), payload.interactionId(), val);
                return;
            }
            PortableContraptionControllerRuntime.handleHudInteraction(
                    player, payload.target().pos(), payload.pairId(),
                    payload.nodeId(), payload.interactionId(), val);
        });
    }

    // Encode the advanced HUD interaction
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedHudInteractionPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUUID(payload.pairId());
        buffer.writeUtf(payload.nodeId(), 128);
        buffer.writeUtf(payload.interactionId(), 128);
        buffer.writeEnum(payload.valueKind());
        buffer.writeDouble(payload.numberValue());
        buffer.writeBoolean(payload.booleanValue());
    }

    // Decode the advanced HUD interaction
    private static AdvancedHudInteractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AdvancedHudInteractionPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUUID(),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readEnum(ValueKind.class),
                buffer.readDouble(),
                buffer.readBoolean());
    }

    // Define the value kind values
    public enum ValueKind {
        NUMBER,
        BOOLEAN
    }
}
