package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.ShipDockBlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply a pending Ship Dock binding when a Simulated docking connector is placed
@Mixin(BlockItem.class)
public abstract class DockingConnectorBlockItemPlacementMixin {
    @Inject(method = "place", at = @At("TAIL"))
    private void createthrusters$applyShipDockBinding(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!cir.getReturnValue().consumesAction() || context.getLevel().isClientSide) return;
        ShipDockBlockItem.applyPendingDockingConnectorBinding(context.getItemInHand(),
                context.getLevel().getBlockEntity(context.getClickedPos()));
    }
}