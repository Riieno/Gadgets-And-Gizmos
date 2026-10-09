package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.HudProjectorBlockEntity;
import ace.flight.control.FlightControllerState;
import ace.flight.physics.AttitudeTargetUtil;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmAttitude;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Keep native HUD telemetry in the same signed frame as its projection
@Pseudo
@Mixin(targets = "ace.flight.block.HudProjectorBlockEntity", remap = false)
public abstract class FlightControlHudStateMixin{
    @Redirect(method = "refreshDisplayedAttitude", at = @At(value = "INVOKE", target =
            "Lace/flight/physics/AttitudeTargetUtil;pitchYawRoll(Ljava/lang/Object;Lace/flight/control/FlightControllerState;)Lace/flight/physics/AttitudeTargetUtil$AttitudeAngles;"))
    private static AttitudeTargetUtil.AttitudeAngles createThrusters$craftAttitude(Object nativeBody,
            FlightControllerState nativeFrame, Level level, BlockPos pos, BlockState state,
            HudProjectorBlockEntity hud, Object knownBody, RigidBodyHandle handle, double dt,
            boolean sync) throws Exception{
        if(FlightControlIntegration.componentHost(hud) instanceof AdvancedContraptionControllerBlockEntity host){
            var body = SableLevelApi.containing(host);
            if(body != null){
                var attitude = ScmAttitude.measure(body.logicalPose().orientation(), FlightControlIntegration.orientation(host));
                return new AttitudeTargetUtil.AttitudeAngles(attitude.pitch(), attitude.yaw(), attitude.roll());
            }
        }
        return AttitudeTargetUtil.pitchYawRoll(nativeBody, nativeFrame);
    }
}
