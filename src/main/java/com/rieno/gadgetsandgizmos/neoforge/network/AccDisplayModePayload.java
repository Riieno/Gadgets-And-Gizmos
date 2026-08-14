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

// Send ACC Display Mode
public record AccDisplayModePayload(
        MenuConfigTarget target,
        String mode
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AccDisplayModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayModePayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayModePayload::encode, AccDisplayModePayload::decode);

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

    // Handle the ACC display mode
    public static void handle(AccDisplayModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || payload.target() == null) {
                return;
            }
            AccDisplayBlockEntity display = SimulatedHelper.findBlockEntity(
                    player.level(), payload.target().subLevelId(), payload.target().pos(),
                    AccDisplayBlockEntity.class);
            if (display != null) {
                display.setDisplayMode(player, payload.mode());
            }
        });
    }

    // Encode the ACC display mode
    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayModePayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(AccDisplayBlockEntity.normalizeDisplayMode(payload.mode()), 64);
    }

    // Decode the ACC display mode
    private static AccDisplayModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new AccDisplayModePayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readUtf(64));
    }
}
