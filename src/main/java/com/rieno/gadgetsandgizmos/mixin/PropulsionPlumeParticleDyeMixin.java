package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectParticleRendering;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Use the desaturating particle layer only when the server supplied an optional preset
@Pseudo
@Mixin(targets = {
        "dev.propulsionteam.propulsionsimulated.particles.plume.PlumeParticle",
        "dev.propulsionteam.propulsionsimulated.particles.plasma.PlasmaParticle",
        "dev.propulsionteam.propulsionsimulated.particles.ion.IonParticle"
}, remap = false)
public abstract class PropulsionPlumeParticleDyeMixin{
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void createthrusters$particleDye(CallbackInfoReturnable<ParticleRenderType> cir){
        var type = ColorEffectParticleRendering.renderType((Particle) (Object) this);
        if(type != null) cir.setReturnValue(type);
    }
}
