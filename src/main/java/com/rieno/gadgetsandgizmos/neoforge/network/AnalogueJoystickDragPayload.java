package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Send Analogue Joystick Drag
public record AnalogueJoystickDragPayload(BlockPos pos, UUID subLevelId, boolean stop, float localX,
                                          float localZ) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueJoystickDragPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_joystick_drag"));
    public static final StreamCodec<ByteBuf, AnalogueJoystickDragPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AnalogueJoystickDragPayload::pos,
            ByteBufCodecs.STRING_UTF8, payload -> payload.subLevelId() == null ? "" : payload.subLevelId().toString(),
            ByteBufCodecs.BOOL, AnalogueJoystickDragPayload::stop,
            ByteBufCodecs.FLOAT, AnalogueJoystickDragPayload::localX,
            ByteBufCodecs.FLOAT, AnalogueJoystickDragPayload::localZ,
            (pos, subLevelId, stop, localX, localZ) -> new AnalogueJoystickDragPayload(pos,
                subLevelId.isBlank() ? null : UUID.fromString(subLevelId), stop, localX, localZ));

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

    // Handle the analogue joystick drag
    public static void handle(AnalogueJoystickDragPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueJoystickBlockEntity joystick = SimulatedHelper.findBlockEntity(
                    ctx.player().level(), payload.subLevelId(), payload.pos(), AnalogueJoystickBlockEntity.class);
            if (joystick == null) {
                return;
            }
            joystick.applyPlayerDragInput(ctx.player().getUUID(), payload.localX(), payload.localZ(), !payload.stop());
        });
    }
}
