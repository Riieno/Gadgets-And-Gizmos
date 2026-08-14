package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickConfigSnapshot;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Analogue Joystick Config
public record AnalogueJoystickConfigPayload(MenuConfigTarget target,
                                            AnalogueJoystickConfigSnapshot snapshot) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueJoystickConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_joystick_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueJoystickConfigPayload> STREAM_CODEC = StreamCodec.composite(
            MenuConfigTarget.STREAM_CODEC,
            AnalogueJoystickConfigPayload::target,
            AnalogueJoystickConfigSnapshot.STREAM_CODEC,
            AnalogueJoystickConfigPayload::snapshot,
            AnalogueJoystickConfigPayload::new);

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

    // Handle the analogue joystick config
    public static void handle(AnalogueJoystickConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {

            AnalogueJoystickBlockEntity joystick = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    AnalogueJoystickMenu.class,
                    AnalogueJoystickBlockEntity.class);
            if (joystick == null) {
                return;
            }

            payload.snapshot().applyTo(joystick);
        });
    }
}