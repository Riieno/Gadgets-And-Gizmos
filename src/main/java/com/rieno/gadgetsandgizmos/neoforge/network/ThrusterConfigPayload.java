package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ControlMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Thruster Config
public record ThrusterConfigPayload(BlockPos pos, boolean enabled, float minThrottle,
                                   float maxThrottle, ControlMode controlMode,
                                   float beamMaxOpacity,
                                   float plumeColorRatio,
                                   boolean filterSound,
                                   boolean filterParticles,
                                   boolean filterDamage,
                                   boolean focusedRejectAirPressure) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ThrusterConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "thruster_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrusterConfigPayload> STREAM_CODEC = StreamCodec.of(
            ThrusterConfigPayload::encode,
            ThrusterConfigPayload::decode);

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

    // Handle the thruster config
    public static void handle(ThrusterConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player().level().getBlockEntity(payload.pos()) instanceof ThrusterBlockEntity thruster)) {
                return;
            }

                thruster.applyConfiguration(payload.enabled(),
                    Mth.clamp(payload.minThrottle(), 0.0f, 1.0f),
                    Mth.clamp(payload.maxThrottle(), 0.0f, 1.0f),
                    payload.controlMode(),
                    Mth.clamp(payload.beamMaxOpacity(), 0.0f, 1.0f),
                    Mth.clamp(payload.plumeColorRatio(), 0.0f, 1.0f),
                    payload.filterSound(), payload.filterParticles(), payload.filterDamage(),
                    payload.focusedRejectAirPressure());
        });
    }

    // Encode the thruster config
    private static void encode(RegistryFriendlyByteBuf buffer, ThrusterConfigPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.enabled());
        buffer.writeFloat(payload.minThrottle());
        buffer.writeFloat(payload.maxThrottle());
        buffer.writeEnum(payload.controlMode());
        buffer.writeFloat(payload.beamMaxOpacity());
        buffer.writeFloat(payload.plumeColorRatio());
        buffer.writeBoolean(payload.filterSound());
        buffer.writeBoolean(payload.filterParticles());
        buffer.writeBoolean(payload.filterDamage());
        buffer.writeBoolean(payload.focusedRejectAirPressure());
    }

    // Decode the thruster config
    private static ThrusterConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ThrusterConfigPayload(buffer.readBlockPos(), buffer.readBoolean(),
            buffer.readFloat(), buffer.readFloat(), buffer.readEnum(ControlMode.class),
            buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(),
            buffer.readBoolean(), buffer.readBoolean());
    }
}
