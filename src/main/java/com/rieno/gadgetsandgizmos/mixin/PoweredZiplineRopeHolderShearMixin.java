package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Shear only the selected zipline rope
@Mixin(RopeHolderBlock.class)
public interface PoweredZiplineRopeHolderShearMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the shear zipline owned rope
    @Inject(method = "shearRope", at = @At("HEAD"), cancellable = true)
    private static void createthrusters$shearZiplineOwnedRope(RopeHolderBlock<?> block, Level level, BlockPos pos,
                                                              ServerPlayer player,
                                                              CallbackInfoReturnable<ItemInteractionResult> cir) {
        BlockEntity usedBlockEntity = level.getBlockEntity(pos);
        if (!(usedBlockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return;
        }
        RopeStrandHolderBehavior usedHolder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (usedHolder == null) {
            return;
        }
        ServerRopeStrand strand = usedHolder.getAttachedStrand();
        RopeAttachment start = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.START);
        if (start == null) {
            return;
        }
        PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                start.blockAttachment(), PoweredZiplineBlockEntity.class);
        if (zipline == null) {
            return;
        }
        if (zipline.destroyHangingRope(strand.getUUID(), player, pos.getCenter())) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        }
    }
}
