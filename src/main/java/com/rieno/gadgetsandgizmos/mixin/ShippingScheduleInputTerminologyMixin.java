package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Rename Shipping Schedule Input labels
@Mixin(value = IScheduleInput.class, remap = false)
public interface ShippingScheduleInputTerminologyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rename the dock destination title
    @Inject(method = "getTitleAs", at = @At("HEAD"), cancellable = true)
    private void renameDockDestinationTitle(
            String type,
            CallbackInfoReturnable<List<Component>> callback
    ) {
        if ((Object) this instanceof DestinationInstruction
                && "instruction".equals(type)
                && Minecraft.getInstance().screen instanceof ScheduleScreen screen
                && screen.getMenu().contentHolder.getItem() instanceof ShippingScheduleItem) {
            callback.setReturnValue(List.of(Component.translatable(
                    "createthrusters.shipping_schedule.navigate_to_dock")));
        }
    }
}
