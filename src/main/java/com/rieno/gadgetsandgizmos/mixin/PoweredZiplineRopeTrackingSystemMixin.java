package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeTrackingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

// Resolve Powered Zipline rope tracking points through their Sable level
@Mixin(ServerRopeTrackingSystem.class)
public class PoweredZiplineRopeTrackingSystemMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current level
    @Shadow
    @Final
    private ServerLevel level;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Send the zipline tracking data
    @Inject(method = "sendTrackingData", at = @At("HEAD"), cancellable = true)
    private void createthrusters$sendZiplineTrackingData(int interpolationTick, CallbackInfo ci) {
        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate((Level) level);
        if (ropeManager == null) {
            ci.cancel();
            return;
        }
        Collection<ServerRopeStrand> strands = ropeManager.getAllStrands();
        for (ServerRopeStrand strand : strands) {
            if (!strand.isActive()) {
                continue;
            }
            if (strand.needsSync()) {
                strand.networkingStopped = false;
                RopeStrandHolderBehavior holder = createthrusters$getOwningHolder(strand);
                if (holder == null || holder.getOwnedStrand() == null) {
                    continue;
                }
                createthrusters$sendPacket(holder, holder.makeUpdatePacket());
                strand.justSynced();
                continue;
            }
            if (strand.networkingStopped) {
                continue;
            }
            strand.networkingStopped = true;
            RopeStrandHolderBehavior holder = createthrusters$getOwningHolder(strand);
            if (holder == null) {
                continue;
            }
            createthrusters$sendPacket(holder, holder.makeStopPacket());
        }
        ci.cancel();
    }

    // Get the owning holder
    private RopeStrandHolderBehavior createthrusters$getOwningHolder(ServerRopeStrand strand) {
        RopeAttachment attachment = strand.getAttachment(RopeAttachmentPoint.START);
        if (attachment == null) {
            return null;
        }
        BlockPos block = attachment.blockAttachment();
        BlockEntity blockEntity = level.getBlockEntity(block);
        if (blockEntity instanceof PoweredZiplineBlockEntity zipline) {
            RopeStrandHolderBehavior ziplineHolder = zipline.getOwnedHangingRopeHolder(strand.getUUID());
            if (ziplineHolder != null) {
                return ziplineHolder;
            }
        }
        return (RopeStrandHolderBehavior) RopeStrandHolderBehavior.get(blockEntity, RopeStrandHolderBehavior.TYPE);
    }

    // Send the packet
    private void createthrusters$sendPacket(RopeStrandHolderBehavior holder, CustomPacketPayload payload) {
        try {
            Object sink = holder.getClass().getMethod("getStrandPacketSink").invoke(holder);
            sink.getClass().getMethod("sendPacket", CustomPacketPayload[].class)
                    .invoke(sink, (Object) new CustomPacketPayload[]{payload});
        } catch (Exception ignored) {
        }
    }
}
