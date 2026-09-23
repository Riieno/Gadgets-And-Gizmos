package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Expose live SCM throttle control for Flight Control vector thrusters
@Pseudo
@Mixin(targets = "ace.flight.block.SmartVectorThrusterBlockEntity", remap = false)
public abstract class FlightControlVectorThrusterDirectControlMixin
        implements IDirectControlReceiver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Shadow public boolean active;
    @Shadow public boolean hasNozzle;
    @Shadow public abstract Direction getNozzleDirection();
    @Shadow public abstract float getEffectiveMaxThrustForComputer();
    @Shadow public abstract boolean hasPropellantForThrust();
    @Shadow public abstract void setAllocatedForce(double[] force);

    // Tracks whether SCM currently owns native direct thrust production
    @Unique
    private boolean createThrusters$scmDirectThrottleActive;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        float throttle = Float.isFinite(val) ? Mth.clamp(val, 0.0F, 1.0F) : 0.0F;
        createThrusters$scmDirectThrottleActive = throttle > 1.0E-4F;
        if (!createThrusters$scmDirectThrottleActive) {
            setAllocatedForce(new double[3]);
            return;
        }
        Direction nozzle = getNozzleDirection();
        double force = Math.max(0.0D, getEffectiveMaxThrustForComputer()) * throttle;
        setAllocatedForce(new double[]{
                -nozzle.getStepX() * force,
                -nozzle.getStepY() * force,
                -nozzle.getStepZ() * force
        });
    }

    // Allow the native physics tick to consume SCM's live allocated force
    @Inject(method = "shouldProduceDirectRedstoneThrust", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void createThrusters$allowScmDirectThrust(
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (createThrusters$scmDirectThrottleActive
                && active
                && hasNozzle
                && hasPropellantForThrust()) {
            callback.setReturnValue(true);
        }
    }
}
