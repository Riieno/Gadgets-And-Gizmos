package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

// Request Analogue Contraption Controller Discovery
public record AnalogueContraptionControllerDiscoveryRequestPayload(BlockPos pos, UUID subLevelId) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerDiscoveryRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_discovery_request"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerDiscoveryRequestPayload> STREAM_CODEC =
        net.minecraft.network.codec.StreamCodec.of(
            AnalogueContraptionControllerDiscoveryRequestPayload::encode,
            AnalogueContraptionControllerDiscoveryRequestPayload::decode);

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

    // Handle the analogue contraption controller discovery request
    public static void handle(AnalogueContraptionControllerDiscoveryRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            AnalogueContraptionControllerBlockEntity controller = controllerFromOpenMenu(serverPlayer, payload);
            if (controller == null) {
                controller = SimulatedHelper.findBlockEntity(
                        serverPlayer.level(), payload.subLevelId(), payload.pos(), AnalogueContraptionControllerBlockEntity.class);
            }
            if (controller == null) {
                return;
            }

            PacketDistributor.sendToPlayer(serverPlayer,
                    new AnalogueContraptionControllerDiscoveryResultsPayload(payload.pos(), payload.subLevelId(), controller.getAssignableTargets()));
        });
    }

    // Get the controller from open menu
    private static AnalogueContraptionControllerBlockEntity controllerFromOpenMenu(ServerPlayer serverPlayer,
                                                                                  AnalogueContraptionControllerDiscoveryRequestPayload payload) {
        if (!(serverPlayer.containerMenu instanceof AnalogueContraptionControllerMenu menu)) {
            return null;
        }
        if (!Objects.equals(menu.getContentPos(), payload.pos())
                || !Objects.equals(menu.getContentSubLevelId(), payload.subLevelId())) {
            return null;
        }
        return menu.getMenuConfigTargetBlockEntity();
    }

    // Encode the analogue contraption controller discovery request
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueContraptionControllerDiscoveryRequestPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
    }

    // Decode the analogue contraption controller discovery request
    private static AnalogueContraptionControllerDiscoveryRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AnalogueContraptionControllerDiscoveryRequestPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readBoolean() ? buffer.readUUID() : null);
    }
}
