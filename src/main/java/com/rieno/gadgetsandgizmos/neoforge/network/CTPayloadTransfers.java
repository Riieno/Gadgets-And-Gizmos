package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.network.PayloadTransferChannel;
import com.rieno.gadgetsandgizmos.lib.network.BlockEntitySnapshotChannel;
import net.minecraft.resources.ResourceLocation;

// Own the addon transfer identifier while the library handles serialization and pacing
public final class CTPayloadTransfers{
    public static final PayloadTransferChannel CHANNEL = new PayloadTransferChannel(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "payload_transfer"));
    public static final BlockEntitySnapshotChannel DISPLAY_DATA = new BlockEntitySnapshotChannel(CHANNEL,
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "display_snapshot"), (player, target) -> {
                var entity = com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.findLoadedBlockEntityExact(
                        player.serverLevel(), target.subLevelId(), target.pos());
                if(!(entity instanceof com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity)) return null;
                var point = com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.toGlobalWorldPosition(entity, target.pos().getCenter());
                boolean watching = player.serverLevel().getChunkSource().chunkMap.getPlayers(
                        new net.minecraft.world.level.ChunkPos(target.pos()), false).contains(player);
                return watching || point != null && player.distanceToSqr(point) <= 256 * 256 ? entity : null;
            });

    // Keep channel ownership static for packet registration and producers
    private CTPayloadTransfers(){}
}
