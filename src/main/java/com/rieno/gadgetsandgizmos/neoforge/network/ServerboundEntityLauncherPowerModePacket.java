package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Handle Entity Launcher Power Mode
public record ServerboundEntityLauncherPowerModePacket(InteractionHand hand) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundEntityLauncherPowerModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "entity_launcher_power_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundEntityLauncherPowerModePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundEntityLauncherPowerModePacket::encode,
                    ServerboundEntityLauncherPowerModePacket::decode);

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

    // Handle the serverbound entity launcher power mode
    public static void handle(ServerboundEntityLauncherPowerModePacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                EntityLauncherItem.togglePowerMode(player, payload.hand());
            }
        });
    }

    // Encode the serverbound entity launcher power mode
    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundEntityLauncherPowerModePacket payload) {
        buffer.writeEnum(payload.hand());
    }

    // Decode the serverbound entity launcher power mode
    private static ServerboundEntityLauncherPowerModePacket decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundEntityLauncherPowerModePacket(buffer.readEnum(InteractionHand.class));
    }
}
