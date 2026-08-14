package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Expose Contraption Diagram controls to the controller linker
@Mixin(value = DiagramEntity.class, remap = false)
public abstract class ContraptionDiagramLinkerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the link contraption diagram
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void ct$linkContraptionDiagram(Player player, Vec3 hitLocation, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> callback) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!(heldStack.getItem() instanceof ContraptionNetworkLinkerItem linker)) {
            return;
        }

        Entity diagram = (Entity) (Object) this;
        SubLevel subLevel = Sable.HELPER.getContaining(diagram);
        callback.setReturnValue(linker.useOnContraptionDiagram(diagram, subLevel, player, hand));
    }
}
