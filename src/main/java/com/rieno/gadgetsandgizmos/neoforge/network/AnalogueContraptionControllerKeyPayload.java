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
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

// Send Analogue Contraption Controller Key
public record AnalogueContraptionControllerKeyPayload(BlockPos pos, UUID subLevelId, String channelId,
                                                      boolean pressed) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerKeyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerKeyPayload> STREAM_CODEC = StreamCodec.of(
        AnalogueContraptionControllerKeyPayload::encode,
        AnalogueContraptionControllerKeyPayload::decode);

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

    // Handle the analogue contraption controller key
    public static void handle(AnalogueContraptionControllerKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity controller = controllerFromOpenMenu(ctx, payload);
            boolean openMenuTarget = controller != null;
            if (controller == null) {
                controller = SimulatedHelper.findBlockEntity(
                        ctx.player().level(), payload.subLevelId(), payload.pos(), AnalogueContraptionControllerBlockEntity.class);
            }
            if (controller == null) {
                return;
            }
            if (ctx.player() instanceof ServerPlayer player && !openMenuTarget
                    && !controller.canPlayerUse(player)
                    && !DiagnosticTabletRemoteSessions.isInteractionAuthorized(
                    player, payload.pos(), payload.subLevelId())) return;

            controller.handleControllerKeyInput(payload.channelId(), payload.pressed());
        });
    }

    // Get the controller from open menu
    private static AnalogueContraptionControllerBlockEntity controllerFromOpenMenu(IPayloadContext ctx,
                                                                                  AnalogueContraptionControllerKeyPayload payload) {
        if (!(ctx.player() instanceof ServerPlayer serverPlayer)
                || !(serverPlayer.containerMenu instanceof AnalogueContraptionControllerMenu menu)) {
            return null;
        }
        if (!Objects.equals(menu.getContentPos(), payload.pos())
                || !Objects.equals(menu.getContentSubLevelId(), payload.subLevelId())) {
            return null;
        }
        return menu.getMenuConfigTargetBlockEntity();
    }

    // Encode the analogue contraption controller key
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueContraptionControllerKeyPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeUtf(payload.channelId());
        buffer.writeBoolean(payload.pressed());
    }

    // Decode the analogue contraption controller key
    private static AnalogueContraptionControllerKeyPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AnalogueContraptionControllerKeyPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readUtf(),
                buffer.readBoolean());
    }
}
