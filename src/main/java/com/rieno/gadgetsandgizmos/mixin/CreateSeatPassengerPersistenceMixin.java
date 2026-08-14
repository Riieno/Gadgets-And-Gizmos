package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingSchedulePilot;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep Create Seat Passenger saved
@Mixin(value = SeatEntity.class, remap = false)
public abstract class CreateSeatPassengerPersistenceMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the mob passengers persistent
    @Inject(method = "tick", at = @At("HEAD"))
    private void createthrusters$keepMobPassengersPersistent(CallbackInfo callbackInfo) {
        SeatEntity seat = (SeatEntity) (Object) this;
        if (seat.level().isClientSide) {
            return;
        }
        for (Entity passenger : seat.getPassengers()) {
            if (passenger instanceof Mob mob
                    && !mob.isPersistenceRequired()
                    && ShippingSchedulePilot.isShipControllerSeat(
                            mob, seat)) {
                mob.setPersistenceRequired();
            }
        }
    }
}
