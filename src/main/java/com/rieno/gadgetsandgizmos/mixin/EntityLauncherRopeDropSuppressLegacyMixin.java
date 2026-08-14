package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
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
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Apply launcher rope drop suppression to older Simulated versions
@Mixin(RopeStrandHolderBehavior.class)
public abstract class EntityLauncherRopeDropSuppressLegacyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the suppress launcher rope drops
    @Inject(method = "destroyRope", at = @At("HEAD"), cancellable = true)
    private void createthrusters$suppressLauncherRopeDrops(@Nullable ServerPlayer player,
                                                            @Nullable Vec3 ropeDropPos,
                                                            CallbackInfo ci) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        EntityLauncherAnchorBlockEntity launcherAnchor = self.blockEntity instanceof EntityLauncherAnchorBlockEntity anchor
                ? anchor
                : null;
        if (launcherAnchor == null && !(self.blockEntity instanceof LauncherEndpointBlockEntity)) {
            return;
        }

        if (launcherAnchor != null && player != null) {
            launcherAnchor.handleExternalRopeBreak();
        }

        ServerRopeStrand strand = self.getOwnedStrand();
        Level level = self.blockEntity.getLevel();
        if (strand != null && self.ownsRope() && level instanceof ServerLevel serverLevel) {
            RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
            RopeStrandHolderBehavior endHolder = resolveEndHolder(serverLevel, endAttachment);
            if (endHolder != null) {
                endHolder.detachRope();
                endHolder.blockEntity.notifyUpdate();
            }
            if (endAttachment != null
                    && EntityLauncherVirtualRopeEndpoints.find(serverLevel, endAttachment.subLevelID(),
                    endAttachment.blockAttachment()) != null) {
                EntityLauncherVirtualRopeEndpoints.remove(serverLevel, endAttachment.subLevelID(),
                        endAttachment.blockAttachment());
            }
            createthrusters$removeServerStrand(level);
        }
        self.detachRope();
        self.blockEntity.notifyUpdate();
        ci.cancel();
    }

    // Resolve the end holder
    @Nullable
    private static RopeStrandHolderBehavior resolveEndHolder(ServerLevel serverLevel, @Nullable RopeAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        BlockEntity blockEntity = EntityLauncherVirtualRopeEndpoints.find(serverLevel, attachment.subLevelID(),
                attachment.blockAttachment());
        if (blockEntity == null && attachment.subLevelID() != null) {
            blockEntity = SimulatedHelper.findBlockEntity(serverLevel, attachment.subLevelID(),
                    attachment.blockAttachment());
        }
        if (blockEntity == null) {
            blockEntity = serverLevel.getBlockEntity(attachment.blockAttachment());
        }
        if (blockEntity instanceof LauncherEndpointBlockEntity endpoint) {
            return endpoint.getRopeHolder();
        }
        if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
            return smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        }
        return null;
    }

    // Remove the server strand
    @Invoker("removeServerStrand")
    protected abstract void createthrusters$removeServerStrand(Level level);
}
