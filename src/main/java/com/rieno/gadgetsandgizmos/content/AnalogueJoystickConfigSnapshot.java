package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

// Store Analogue Joystick Config snapshot
public record AnalogueJoystickConfigSnapshot(ItemStack forwardFirst,
                                             ItemStack forwardSecond,
                                             ItemStack backwardFirst,
                                             ItemStack backwardSecond,
                                             ItemStack leftFirst,
                                             ItemStack leftSecond,
                                             ItemStack rightFirst,
                                             ItemStack rightSecond,
                                             boolean momentary,
                                             float sensitivity,
                                             float deadzone,
                                             float maxTiltDegrees,
                                             boolean gamepadInput) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueJoystickConfigSnapshot> STREAM_CODEC = StreamCodec.of(
            AnalogueJoystickConfigSnapshot::encode,
            AnalogueJoystickConfigSnapshot::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the analogue joystick config snapshot
    public void applyTo(AnalogueJoystickBlockEntity joystick) {
        joystick.setChannelBinding(AnalogueJoystickBlockEntity.JoystickChannel.FORWARD, forwardFirst, forwardSecond);
        joystick.setChannelBinding(AnalogueJoystickBlockEntity.JoystickChannel.BACKWARD, backwardFirst, backwardSecond);
        joystick.setChannelBinding(AnalogueJoystickBlockEntity.JoystickChannel.LEFT, leftFirst, leftSecond);
        joystick.setChannelBinding(AnalogueJoystickBlockEntity.JoystickChannel.RIGHT, rightFirst, rightSecond);
        joystick.setReleaseMode(momentary
                ? AnalogueJoystickBlockEntity.ReleaseMode.MOMENTARY
                : AnalogueJoystickBlockEntity.ReleaseMode.LATCHED);
        joystick.setDragSensitivity(sensitivity);
        joystick.setDeadzone(deadzone);
        joystick.setMaxTiltDegrees(maxTiltDegrees);
        joystick.setInputMode(gamepadInput
                ? AnalogueJoystickBlockEntity.InputMode.GAMEPAD
                : AnalogueJoystickBlockEntity.InputMode.MOUSE);
    }

    // Encode the analogue joystick config snapshot
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueJoystickConfigSnapshot snapshot) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.forwardFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.forwardSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.backwardFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.backwardSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.leftFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.leftSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.rightFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.rightSecond());
        ByteBufCodecs.BOOL.encode(buffer, snapshot.momentary());
        ByteBufCodecs.FLOAT.encode(buffer, snapshot.sensitivity());
        ByteBufCodecs.FLOAT.encode(buffer, snapshot.deadzone());
        ByteBufCodecs.FLOAT.encode(buffer, snapshot.maxTiltDegrees());
        ByteBufCodecs.BOOL.encode(buffer, snapshot.gamepadInput());
    }

    // Decode the analogue joystick config snapshot
    private static AnalogueJoystickConfigSnapshot decode(RegistryFriendlyByteBuf buffer) {
        return new AnalogueJoystickConfigSnapshot(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer));
    }
}
