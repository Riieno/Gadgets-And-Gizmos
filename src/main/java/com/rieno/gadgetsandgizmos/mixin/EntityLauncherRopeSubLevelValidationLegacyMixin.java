package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.EntityLauncherVirtualRopeEndpoints;
import com.rieno.gadgetsandgizmos.content.LauncherEndpointBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Apply launcher rope endpoint validation to older Simulated versions
@Mixin(RopeStrandHolderBehavior.class)
public class EntityLauncherRopeSubLevelValidationLegacyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current owned server strand
    @Shadow
    @Nullable
    private ServerRopeStrand ownedServerStrand;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the sublevel end attachment
    @Inject(method = "destroyRopeIfAttachmentBroken", at = @At("HEAD"), cancellable = true)
    private void createthrusters$resolveSubLevelEndAttachment(CallbackInfo ci) {
        ServerRopeStrand strand = ownedServerStrand;
        if (strand == null) {
            return;
        }

        RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment == null) {
            return;
        }

        Level rawLevel = ((RopeStrandHolderBehavior) (Object) this).blockEntity.getLevel();
        if (!(rawLevel instanceof ServerLevel serverLevel) || !strand.areAttachmentsLoaded(serverLevel)) {
            return;
        }

        BlockEntity blockEntity = EntityLauncherVirtualRopeEndpoints.find(serverLevel,
                endAttachment.subLevelID(), endAttachment.blockAttachment());
        if (blockEntity == null && endAttachment.subLevelID() != null) {
            blockEntity = SimulatedHelper.findBlockEntity(serverLevel,
                    endAttachment.subLevelID(), endAttachment.blockAttachment());
        }
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return;
        }
        RopeStrandHolderBehavior holder = resolveHolderBehavior(smartBlockEntity);
        if (holder != null) {
            ci.cancel();
        }
    }

    // Remove the virtual end attachment
    @Inject(method = "destroyRope", at = @At("HEAD"))
    private void createthrusters$removeVirtualEndAttachment(@Nullable ServerPlayer player, @Nullable Vec3 ropeDropPos,
                                                            CallbackInfo ci) {
        ServerRopeStrand strand = ownedServerStrand;
        if (strand == null) {
            return;
        }

        RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment == null) {
            return;
        }

        Level rawLevel = ((RopeStrandHolderBehavior) (Object) this).blockEntity.getLevel();
        if (rawLevel instanceof ServerLevel serverLevel
                && EntityLauncherVirtualRopeEndpoints.find(serverLevel, endAttachment.subLevelID(),
                endAttachment.blockAttachment()) != null) {
            EntityLauncherVirtualRopeEndpoints.remove(serverLevel, endAttachment.subLevelID(),
                    endAttachment.blockAttachment());
        }
    }

    // Resolve the holder behavior
    private static RopeStrandHolderBehavior resolveHolderBehavior(SmartBlockEntity smartBlockEntity) {
        if (smartBlockEntity instanceof LauncherEndpointBlockEntity endpoint) {
            return endpoint.getRopeHolder();
        }
        return smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }
}
