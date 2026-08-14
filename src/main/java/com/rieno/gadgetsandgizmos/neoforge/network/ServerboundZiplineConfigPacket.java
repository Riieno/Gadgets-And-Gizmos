package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Handle Zipline Config
public record ServerboundZiplineConfigPacket(BlockPos pos, UUID subLevelId, float maxSpeed, float damping)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundZiplineConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "zipline_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundZiplineConfigPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundZiplineConfigPacket::encode, ServerboundZiplineConfigPacket::decode);

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

    // Handle the serverbound zipline config
    public static void handle(ServerboundZiplineConfigPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntity(
                    player.level(), payload.subLevelId(), payload.pos(), PoweredZiplineBlockEntity.class);
            if (zipline == null) {
                zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(
                        player.level(), payload.pos(), PoweredZiplineBlockEntity.class);
            }
            if (zipline == null) {
                return;
            }
            zipline.setMotionConfiguration(payload.maxSpeed(), payload.damping());
        });
    }

    // Encode the serverbound zipline config
    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundZiplineConfigPacket payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeFloat(payload.maxSpeed());
        buffer.writeFloat(payload.damping());
    }

    // Decode the serverbound zipline config
    private static ServerboundZiplineConfigPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ServerboundZiplineConfigPacket(pos, subLevelId, buffer.readFloat(), buffer.readFloat());
    }
}
