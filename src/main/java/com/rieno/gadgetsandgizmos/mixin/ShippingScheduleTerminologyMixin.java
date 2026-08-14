package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// Rename Shipping Schedule labels
@Mixin(value = Schedule.class, remap = false)
public abstract class ShippingScheduleTerminologyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rename the dock destination
    @Inject(method = "getTypeOptions", at = @At("RETURN"), cancellable = true)
    private static <T> void renameDockDestination(
            List<Pair<ResourceLocation, T>> types,
            CallbackInfoReturnable<List<? extends Component>> callback
    ) {
        if (!isShippingScheduleScreen() || (Object) types != Schedule.INSTRUCTION_TYPES) {
            return;
        }
        List<Component> labels = new ArrayList<>(callback.getReturnValue());
        ResourceLocation destination = Create.asResource("destination");
        for (int idx = 0; idx < types.size() && idx < labels.size(); idx++) {
            if (destination.equals(types.get(idx).getFirst())) {
                labels.set(idx, Component.translatable(
                        "createthrusters.shipping_schedule.navigate_to_dock"));
            }
        }
        callback.setReturnValue(labels);
    }

    // Check if this is a shipping schedule screen
    private static boolean isShippingScheduleScreen() {
        return Minecraft.getInstance().screen instanceof ScheduleScreen screen
                && screen.getMenu().contentHolder.getItem() instanceof ShippingScheduleItem;
    }
}
