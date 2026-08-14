package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.EntityLauncherVirtualRopeEndpoints;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
import com.rieno.gadgetsandgizmos.content.LauncherEndpointBlockEntity;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Restore launcher rope attachment points after their Sable sub-level is resolved
@Mixin(ServerRopeStrand.class)
public class EntityLauncherRopeSubLevelAttachmentMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the sublevel attachment
    @Inject(method = "applyAttachment", at = @At("HEAD"), cancellable = true)
    private void createthrusters$resolveSubLevelAttachment(RopeAttachment attachment, ServerLevel level,
                                                           CallbackInfo ci) {
        BlockEntity blockEntity = EntityLauncherVirtualRopeEndpoints.find(level, attachment.subLevelID(),
                attachment.blockAttachment());
        if (blockEntity == null && attachment.subLevelID() != null) {
            blockEntity = SimulatedHelper.findBlockEntity(level, attachment.subLevelID(), attachment.blockAttachment());
        }
        if (blockEntity == null && attachment.subLevelID() == null) {
            BlockEntity candidate = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachment.blockAttachment());
            if (candidate instanceof EntityLauncherAnchorBlockEntity
                    || candidate instanceof LauncherEndpointBlockEntity) {
                blockEntity = candidate;
            }
        }
        if (blockEntity == null) {
            return;
        }
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return;
        }

        RopeStrandHolderBehavior ropeHolder = resolveHolderBehavior(smartBlockEntity);
        if (ropeHolder == null) {
            return;
        }

        ServerSubLevel subLevel = null;
        java.util.UUID subLevelId = attachment.subLevelID() != null
                ? attachment.subLevelID()
                : SimulatedHelper.getContainingSubLevelId(blockEntity);
        if (subLevelId != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }
            subLevel = (ServerSubLevel) container.getSubLevel(subLevelId);
            if (subLevel == null) {
                return;
            }
        }

        RopeHandle.AttachmentPoint point = attachment.point() == RopeAttachmentPoint.END
                ? RopeHandle.AttachmentPoint.END
                : RopeHandle.AttachmentPoint.START;
        Vector3d attachmentPoint = JOMLConversion.toJOML((Position) ropeHolder.getAttachmentPoint());
        ((RopePhysicsObject) (Object) this).setAttachment(point, attachmentPoint, subLevel);
        ci.cancel();
    }

    // Resolve the holder behavior
    private static RopeStrandHolderBehavior resolveHolderBehavior(SmartBlockEntity smartBlockEntity) {

        if (smartBlockEntity instanceof LauncherEndpointBlockEntity endpoint) {
            return endpoint.getRopeHolder();
        }
        return smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }
}
