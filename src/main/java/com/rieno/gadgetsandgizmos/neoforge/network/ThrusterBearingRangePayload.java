package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.AngleMode;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity.ControlMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Thruster Bearing Range
public record ThrusterBearingRangePayload(BlockPos pos, double minAngleDeg, double maxAngleDeg,
                                          ControlMode controlMode, AngleMode angleMode,
                                          boolean inverted) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ThrusterBearingRangePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "thruster_bearing_range"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrusterBearingRangePayload> STREAM_CODEC = StreamCodec.of(
            ThrusterBearingRangePayload::encode,
            ThrusterBearingRangePayload::decode);

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

    // Handle the thruster bearing range
    public static void handle(ThrusterBearingRangePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player().level().getBlockEntity(payload.pos()) instanceof ThrusterBearingBlockEntity bearing)) {
                return;
            }

            bearing.setAngleRangeDegrees(payload.minAngleDeg(), payload.maxAngleDeg());
            bearing.setControlMode(payload.controlMode());
            bearing.setAngleMode(payload.angleMode());
            bearing.setInverted(payload.inverted());
        });
    }

    // Encode the thruster bearing range
    private static void encode(RegistryFriendlyByteBuf buffer, ThrusterBearingRangePayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeDouble(payload.minAngleDeg());
        buffer.writeDouble(payload.maxAngleDeg());
        buffer.writeEnum(payload.controlMode());
        buffer.writeEnum(payload.angleMode());
        buffer.writeBoolean(payload.inverted());
    }

    // Decode the thruster bearing range
    private static ThrusterBearingRangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ThrusterBearingRangePayload(buffer.readBlockPos(), buffer.readDouble(), buffer.readDouble(),
            buffer.readEnum(ControlMode.class), buffer.readEnum(AngleMode.class), buffer.readBoolean());
    }
}
