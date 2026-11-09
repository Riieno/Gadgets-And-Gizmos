package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.letitwalk.LetItWalkFlightControl;
import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.LegRuntime;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Apply optional SCM altitude goals after Let It Walk has checked native flight state and fuel
@Pseudo
@Mixin(targets = "dev.letitwalk.walker.WalkerFlight", remap = false)
public abstract class LetItWalkFlightControlMixin{
    // Preserve the native jet tick and replace only its terrain-relative height target
    @Inject(method = "tick", at = @At("RETURN"))
    private static void createThrusters$altitude(WalkerCoreBlockEntity core, ServerSubLevel body, List<LegRuntime> legs,
                                                Vec3 commandVelocity, Vector3d velocity, int keys, long time, CallbackInfo ci){
        LetItWalkFlightControl.apply(core, body);
    }
}
