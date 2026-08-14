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

// Handle Zipline Follow Chain
public record ServerboundZiplineFollowChainPacket(BlockPos pos, UUID subLevelId, boolean followChain)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundZiplineFollowChainPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "zipline_follow_chain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundZiplineFollowChainPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundZiplineFollowChainPacket::encode, ServerboundZiplineFollowChainPacket::decode);

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

    // Handle the serverbound zipline follow chain
    public static void handle(ServerboundZiplineFollowChainPacket payload, IPayloadContext ctx) {
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
            zipline.setFollowChain(payload.followChain());
        });
    }

    // Encode the serverbound zipline follow chain
    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundZiplineFollowChainPacket payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeBoolean(payload.followChain());
    }

    // Decode the serverbound zipline follow chain
    private static ServerboundZiplineFollowChainPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ServerboundZiplineFollowChainPacket(pos, subLevelId, buffer.readBoolean());
    }
}
