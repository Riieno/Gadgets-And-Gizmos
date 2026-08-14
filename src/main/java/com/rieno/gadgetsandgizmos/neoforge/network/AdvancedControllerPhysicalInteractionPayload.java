package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Carry the start and stop of the local controller-input mode to the live ACC graph
public record AdvancedControllerPhysicalInteractionPayload(
        BlockPos pos, UUID subLevelId, boolean active, boolean remote, String keyPressed
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerPhysicalInteractionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                    "advanced_controller_physical_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerPhysicalInteractionPayload>
            STREAM_CODEC = StreamCodec.of(AdvancedControllerPhysicalInteractionPayload::encode,
            AdvancedControllerPhysicalInteractionPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller physical interaction
    public AdvancedControllerPhysicalInteractionPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        keyPressed = keyPressed == null ? "" : keyPressed.strip();
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

    // Handle the advanced controller physical interaction
    public static void handle(AdvancedControllerPhysicalInteractionPayload payload,
                              IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            AdvancedContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                    player.level(), payload.subLevelId(), payload.pos(),
                    AdvancedContraptionControllerBlockEntity.class);
            if (controller == null) {
                return;
            }
            boolean permitted = controller.canPlayerUse(player)
                    || DiagnosticTabletRemoteSessions.isInteractionAuthorized(
                    player, payload.pos(), payload.subLevelId())
                    || !payload.active()
                    && controller.hasPhysicalInteractionParticipant(player.getUUID());
            if (!permitted) {
                return;
            }
            controller.handlePhysicalInteraction(player, payload.active(), payload.remote(),
                    payload.keyPressed().length() > 32 ? payload.keyPressed().substring(0, 32)
                            : payload.keyPressed());
        });
    }

    // Encode the advanced controller physical interaction
    private static void encode(RegistryFriendlyByteBuf buffer,
                               AdvancedControllerPhysicalInteractionPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeBoolean(payload.active());
        buffer.writeBoolean(payload.remote());
        buffer.writeUtf(payload.keyPressed(), 32);
    }

    // Decode the advanced controller physical interaction
    private static AdvancedControllerPhysicalInteractionPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new AdvancedControllerPhysicalInteractionPayload(pos, subLevelId,
                buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32));
    }
}
