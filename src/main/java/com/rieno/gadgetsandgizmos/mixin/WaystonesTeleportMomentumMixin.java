package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.rieno.gadgetsandgizmos.compat.simulated.SableTeleportMomentum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.blay09.mods.waystones.core.WaystoneTeleportManager", remap = false)
public abstract class WaystonesTeleportMomentumMixin {
    @Inject(method = "teleportEntity", at = @At("HEAD"), remap = false, require = 0)
    private static void createthrusters$captureSourceFrame(CallbackInfoReturnable<Object> cir,
                                                            @Local(argsOnly = true) Entity source,
                                                            @Share("sourceOnSubLevel") LocalBooleanRef sourceOnSubLevel) {
        sourceOnSubLevel.set(SableTeleportMomentum.onSubLevel(source));
    }

    // Waystones clears vertical velocity after Entity.teleportTo, so restore the destination frame's
    // vertical component only after its entire teleport operation has completed.
    @Inject(method = "teleportEntity", at = @At("RETURN"), remap = false, require = 0)
    private static void createthrusters$restoreDestinationVerticalVelocity(CallbackInfoReturnable<Object> cir,
                                                                            @Share("sourceOnSubLevel") LocalBooleanRef sourceOnSubLevel) {
        Object result = cir.getReturnValue();
        if (result == null) return;
        try {
            Class<?> resultType = result.getClass();
            if (!Boolean.TRUE.equals(resultType.getMethod("isSuccessful").invoke(result))) return;
            Entity arrived = (Entity) resultType.getMethod("entity").invoke(result);
            Object destination = resultType.getMethod("resolvedDestination").invoke(result);
            if (arrived == null || destination == null) return;
            Class<?> destinationType = destination.getClass();
            if (!(destinationType.getMethod("level").invoke(destination) instanceof ServerLevel level)
                    || !(destinationType.getMethod("location").invoke(destination) instanceof Vec3 position)) return;
            SableTeleportMomentum.Destination frame = SableTeleportMomentum.destination(level, position);
            if (!frame.inSubLevel() && sourceOnSubLevel.get()) {
                // Waystones and its event listeners can change motion after Entity.teleportTo returns.
                SableTeleportMomentum.apply(arrived, Vec3.ZERO, frame);
                return;
            }
            if (!frame.inSubLevel()) return;
            // Waystones leaves fall-flying velocity intact, so there is nothing to restore.
            if (arrived instanceof LivingEntity living && living.isFallFlying()) return;
            Vec3 motion = arrived.getDeltaMovement();
            arrived.setDeltaMovement(new Vec3(motion.x, frame.frameVelocity().y, motion.z));
            arrived.hurtMarked = true;
        } catch (ReflectiveOperationException ignored) {
            // The optional Waystones API changed; the common Entity teleport hook still applies.
        }
    }
}
