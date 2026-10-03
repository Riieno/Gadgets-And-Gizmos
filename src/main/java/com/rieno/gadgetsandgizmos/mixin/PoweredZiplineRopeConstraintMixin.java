package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A zipline's hanging rope can outlive the sub-level at its other end. Simulated
// rebuilds constraints before block entities tick, so defer that rebuild until
// both saved anchors refer to a loaded body in the correct coordinate space.
@Mixin(ServerRopeStrand.class)
public class PoweredZiplineRopeConstraintMixin {
    @Inject(method = "reattachConstraints", at = @At("HEAD"), cancellable = true)
    private void createthrusters$deferUnresolvedZiplineRope(ServerLevel level, CallbackInfo ci) {
        ServerRopeStrand strand = (ServerRopeStrand) (Object) this;
        RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
        RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
        if (start == null || end == null || start.blockAttachment() == null || end.blockAttachment() == null) {
            return;
        }

        BlockEntity owner = start.subLevelID() == null
                ? level.isLoaded(start.blockAttachment()) ? level.getBlockEntity(start.blockAttachment()) : null
                : SubLevelBlockEntityCollector.getBlockEntity(
                        SubLevelBlockEntityCollector.getSubLevel(level, start.subLevelID()),
                        start.blockAttachment());
        if (!(owner instanceof PoweredZiplineBlockEntity)) {
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && (!anchorResolves(container, start) || !anchorResolves(container, end))) {
            strand.removeConstraints();
            ci.cancel();
        }
    }

    private static boolean anchorResolves(ServerSubLevelContainer container, RopeAttachment attachment) {
        BlockPos position = attachment.blockAttachment();
        var anchor = JOMLConversion.toJOML(position.getCenter());
        if (attachment.subLevelID() == null) {
            return !container.inBounds(anchor);
        }
        if (!(container.getSubLevel(attachment.subLevelID()) instanceof ServerSubLevel subLevel)
                || subLevel.isRemoved() || subLevel.getPlot() == null) {
            return false;
        }
        return subLevel.getPlot().contains(anchor);
    }
}
