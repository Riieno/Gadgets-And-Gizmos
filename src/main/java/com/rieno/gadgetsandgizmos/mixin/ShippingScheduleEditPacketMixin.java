package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletScheduleSessions;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.schedule.ScheduleEditPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Handle Shipping Schedule Edit Packet
@Mixin(value = ScheduleEditPacket.class, remap = false)
public abstract class ShippingScheduleEditPacketMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the shipping schedule
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void saveShippingSchedule(ServerPlayer sender, CallbackInfo callback) {
        ScheduleEditPacket packet = (ScheduleEditPacket) (Object) this;
        if (DiagnosticTabletScheduleSessions.save(sender, packet.schedule())) {
            callback.cancel();
            return;
        }
        ItemStack held = sender.getMainHandItem();
        if (!(held.getItem() instanceof ShippingScheduleItem)) {
            return;
        }
        if (packet.schedule().entries.isEmpty()) {
            held.remove(AllDataComponents.TRAIN_SCHEDULE);
        } else {
            held.set(AllDataComponents.TRAIN_SCHEDULE, packet.schedule().write(sender.registryAccess()));
        }
        sender.getCooldowns().addCooldown(held.getItem(), 5);
        callback.cancel();
    }
}
