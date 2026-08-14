package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.network.packets.RopeBreakPacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep zipline rope breaks inside the owning sub-level
@Mixin(RopeBreakPacket.class)
public class PoweredZiplineRopeBreakPacketMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the break zipline owned rope
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void createthrusters$breakZiplineOwnedRope(ServerPacketContext context, CallbackInfo ci) {
        RopeBreakPacket packet = (RopeBreakPacket) (Object) this;
        ServerPlayer player = context.player();
        if (player == null) {
            return;
        }
        Level level = player.level();
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(packet.uuid());
        RopeAttachment start = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.START);
        if (start == null) {
            return;
        }
        PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                start.blockAttachment(), PoweredZiplineBlockEntity.class);
        if (zipline == null) {
            return;
        }
        if (zipline.destroyHangingRope(packet.uuid(), player, null)) {
            ci.cancel();
        }
    }
}
