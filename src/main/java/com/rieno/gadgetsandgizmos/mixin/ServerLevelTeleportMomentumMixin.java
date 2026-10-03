package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.compat.simulated.SableTeleportMomentum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTeleportMomentumMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void createthrusters$beforeEntityTick(Entity entity, CallbackInfo ci) {
        SableTeleportMomentum.beforeServerTick(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void createthrusters$afterEntityTick(Entity entity, CallbackInfo ci) {
        SableTeleportMomentum.afterServerTick(entity);
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void createthrusters$beforePassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        SableTeleportMomentum.beforeServerTick(passenger);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void createthrusters$afterPassengerTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        SableTeleportMomentum.afterServerTick(passenger);
    }
}
