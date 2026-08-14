package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add rope strands to Powered Ziplines from the rope item
@Mixin(RopeItem.class)
public class PoweredZiplineRopeItemUseMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Connect the zipline first point
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void createthrusters$connectFromZiplineFirstPoint(UseOnContext context,
                                                              CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = context.getItemInHand();
        if (!stack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
            return;
        }
        Level level = context.getLevel();
        BlockPos firstPos = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
        PoweredZiplineBlockEntity zipline = firstPos == null ? null
                : SimulatedHelper.findBlockEntityIncludingSubLevels(level, firstPos, PoweredZiplineBlockEntity.class);
        if (zipline == null || !zipline.hasPathAttachment()) {
            return;
        }
        if (!level.isClientSide()
                && zipline.tryAttachHangingRopeToTarget(player, stack, context.getClickedPos())) {
            stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
