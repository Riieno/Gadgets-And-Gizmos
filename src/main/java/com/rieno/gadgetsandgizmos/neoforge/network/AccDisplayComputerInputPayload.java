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

// Send ACC Display Computer Input
public record AccDisplayComputerInputPayload(MenuConfigTarget target, String action,
                                             double x, double y, int value)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AccDisplayComputerInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_computer_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayComputerInputPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayComputerInputPayload::encode, AccDisplayComputerInputPayload::decode);

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

    // Handle the ACC display computer input
    public static void handle(AccDisplayComputerInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || payload.target() == null
                    || !isAction(payload.action())) return;
            AccDisplayBlockEntity display = SimulatedHelper.findBlockEntity(player.level(),
                    payload.target().subLevelId(), payload.target().pos(), AccDisplayBlockEntity.class);
            if (display != null) {
                display.submitComputerCraftInput(player, payload.action(),
                        Math.clamp(payload.x(), 0.0D, 1.0D), Math.clamp(payload.y(), 0.0D, 1.0D),
                        payload.value());
            }
        });
    }

    // Check if this is an action
    private static boolean isAction(String action) {
        return "click".equals(action) || "drag".equals(action) || "release".equals(action)
                || "char".equals(action) || "key".equals(action) || "key_up".equals(action);
    }

    // Encode the ACC display computer input
    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayComputerInputPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.action(), 12);
        buffer.writeDouble(payload.x());
        buffer.writeDouble(payload.y());
        buffer.writeVarInt(payload.value());
    }

    // Decode the ACC display computer input
    private static AccDisplayComputerInputPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AccDisplayComputerInputPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUtf(12), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
    }
}
