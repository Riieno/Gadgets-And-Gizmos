package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Keep launcher rope item handling available on the client
@Mixin(targets = "dev.simulated_team.simulated.content.items.rope.RopeItem.ClientRopeItemHandler")
public class ClientRopeItemHandlerMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the first holder visual attachment
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getCenter()Lnet/minecraft/world/phys/Vec3;", ordinal = 0)
    )
    private static Vec3 ct$useFirstHolderVisualAttachment(BlockPos pos) {
        return resolveVisualAttachment(pos);
    }

    // Handle the second holder visual attachment
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getCenter()Lnet/minecraft/world/phys/Vec3;", ordinal = 1)
    )
    private static Vec3 ct$useSecondHolderVisualAttachment(BlockPos pos) {
        return resolveVisualAttachment(pos);
    }

    // Resolve the visual attachment
    private static Vec3 resolveVisualAttachment(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null || pos == null) {
            return pos == null ? Vec3.ZERO : pos.getCenter();
        }
        RopeStrandHolderBehavior holder = RopeItem.getRopeHolder(level, pos);
        if (holder != null) {
            Vec3 visual = holder.getVisualAttachmentPoint();
            if (visual != null) {
                return visual;
            }
        }
        return pos.getCenter();
    }
}
