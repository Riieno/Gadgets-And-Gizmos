package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Supply graph-hosted controllers with their ACC's logical power source
@Pseudo
@Mixin(targets = {
        "ace.flight.block.AttitudeHoldBlockEntity",
        "ace.flight.block.AltitudeHoldBlockEntity",
        "ace.flight.block.VelocityCouplerBlockEntity",
        "ace.flight.block.AngularVelocityCouplerBlockEntity",
        "ace.flight.block.DistanceCouplerBlockEntity",
        "ace.flight.block.RotationControllerBlockEntity",
        "ace.flight.block.AngularVelocityControllerBlockEntity"
}, remap = false)
public abstract class FlightControlHostedPowerMixin{
    @Inject(method = {"hasWorkPower", "hasKineticPower"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void createThrusters$hostedPower(CallbackInfoReturnable<Boolean> cir){
        if(HostedBlockEntities.host((BlockEntity) (Object) this) != null) cir.setReturnValue(true);
    }
}
