package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send ACC Display Text Input
public record AccDisplayTextInputPayload(
        MenuConfigTarget target,
        String nodeId,
        String interactionId,
        String value
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AccDisplayTextInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_text_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayTextInputPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayTextInputPayload::encode, AccDisplayTextInputPayload::decode);

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

    // Handle the ACC display text input
    public static void handle(AccDisplayTextInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || payload.target() == null || payload.nodeId().isBlank()
                    || payload.interactionId().isBlank()) {
                return;
            }
            AccDisplayBlockEntity display = SimulatedHelper.findBlockEntity(
                    player.level(), payload.target().subLevelId(), payload.target().pos(),
                    AccDisplayBlockEntity.class);
            if (display != null) {
                display.submitTextInput(player, payload.nodeId(),
                        payload.interactionId(), payload.value());
            }
        });
    }

    // Encode the ACC display text input
    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayTextInputPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.nodeId(), 128);
        buffer.writeUtf(payload.interactionId(), 128);
        buffer.writeUtf(payload.value(), 64);
    }

    // Decode the ACC display text input
    private static AccDisplayTextInputPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AccDisplayTextInputPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(64));
    }
}
