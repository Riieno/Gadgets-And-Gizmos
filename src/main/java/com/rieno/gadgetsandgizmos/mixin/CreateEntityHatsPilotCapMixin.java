package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingSchedulePilot;
import com.rieno.gadgetsandgizmos.lib.client.render.HeadwearRenderSource;
import com.rieno.gadgetsandgizmos.neoforge.client.CTPartialModels;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.equipment.hats.EntityHats;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Select the pilot cap in Create's entity-size-aware hat renderer
@Mixin(value = EntityHats.class, remap = false)
public class CreateEntityHatsPilotCapMixin {
    // Get the pilot cap model for scheduled pilots or cap wearers
    @Inject(method = "getHatFor", at = @At("HEAD"), cancellable = true)
    private static void createthrusters$pilotCap(LivingEntity wearer,
                                                 CallbackInfoReturnable<PartialModel> callback) {
        if (wearer == null) return;
        LivingEntity source = HeadwearRenderSource.resolve(wearer);
        var head = source.getItemBySlot(EquipmentSlot.HEAD);
        boolean scheduledPilot = source.getVehicle() instanceof SeatEntity seat
                && ShippingSchedulePilot.isScheduleWorkspacePilot(source, seat);
        if (scheduledPilot || (CTItems.PILOT_CAP != null && head.is(CTItems.PILOT_CAP.get()))) {
            callback.setReturnValue(CTPartialModels.PILOT_CAP);
        }
    }
}
