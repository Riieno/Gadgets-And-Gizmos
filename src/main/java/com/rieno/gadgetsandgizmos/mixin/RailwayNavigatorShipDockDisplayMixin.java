package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.create.ShipDockDisplaySource;
import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Show Ship Dock predictions through Railway Navigator displays
@Pseudo
@Mixin(targets = "de.mrjulsen.crn.block.display.AdvancedDisplayTarget", remap = false)
public abstract class RailwayNavigatorShipDockDisplayMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Accept the ship dock predictions
    @Inject(method = "acceptText", at = @At("HEAD"), require = 0)
    private void createthrusters$acceptShipDockPredictions(
            int line,
            List<MutableComponent> text,
            DisplayLinkContext context,
            CallbackInfo callback
    ) {
        if (!(context.blockEntity().activeSource instanceof ShipDockDisplaySource)) {
            return;
        }
        RailwayNavigatorGraphCompat.populateShipDockPredictions(context);
    }
}
