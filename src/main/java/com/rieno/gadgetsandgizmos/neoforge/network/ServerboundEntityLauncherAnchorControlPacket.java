package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Handle Entity Launcher Anchor Control
public record ServerboundEntityLauncherAnchorControlPacket(BlockPos pos, int action, float xRot, float yRot)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int AIM = 0;
    public static final int USE_PRESSED = 1;
    public static final int USE_RELEASED = 2;
    public static final int DISMOUNT = 3;

    public static final Type<ServerboundEntityLauncherAnchorControlPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "entity_launcher_anchor_control"));
    public static final StreamCodec<ByteBuf, ServerboundEntityLauncherAnchorControlPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ServerboundEntityLauncherAnchorControlPacket::pos,
                    ByteBufCodecs.VAR_INT, ServerboundEntityLauncherAnchorControlPacket::action,
                    ByteBufCodecs.FLOAT, ServerboundEntityLauncherAnchorControlPacket::xRot,
                    ByteBufCodecs.FLOAT, ServerboundEntityLauncherAnchorControlPacket::yRot,
                    ServerboundEntityLauncherAnchorControlPacket::new);

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

    // Handle the serverbound entity launcher anchor control
    public static void handle(ServerboundEntityLauncherAnchorControlPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            EntityLauncherAnchorBlockEntity anchor = EntityLauncherAnchorBlockEntity.getMountedLauncher(player);
            if (anchor == null) {
                anchor = SimulatedHelper.findBlockEntityIncludingSubLevels(
                        player.level(), payload.pos(), EntityLauncherAnchorBlockEntity.class);
            }
            if (anchor != null) {
                anchor.handleMountedControl(player, payload.action(), payload.xRot(), payload.yRot());
            }
        });
    }
}
