package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Handle Zipline Mount
public record ServerboundZiplineMountPacket(BlockPos pos, boolean stop) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundZiplineMountPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "zipline_mount"));
    public static final StreamCodec<ByteBuf, ServerboundZiplineMountPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ServerboundZiplineMountPacket::pos,
            ByteBufCodecs.BOOL, ServerboundZiplineMountPacket::stop,
            ServerboundZiplineMountPacket::new);

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

    // Handle the serverbound zipline mount
    public static void handle(ServerboundZiplineMountPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(
                    player.level(), payload.pos(), PoweredZiplineBlockEntity.class);
            if (zipline == null) {
                return;
            }
            player.fallDistance = 0.0f;
            zipline.setRidingPlayer(payload.stop() ? null : player.getUUID());
            if (payload.stop()) {
                ServerChainConveyorHandler.handleStopRidingPacket(player);
            } else {
                ServerChainConveyorHandler.handleTTLPacket(player);
            }
        });
    }
}
