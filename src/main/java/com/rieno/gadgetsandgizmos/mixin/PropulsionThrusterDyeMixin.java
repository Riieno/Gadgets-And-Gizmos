package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDyeCompatibility;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectParticles;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectState;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Retain native dye settings and send the optional effect alongside server-emitted particles
@Pseudo
@Mixin(targets = "dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity", remap = false)
public abstract class PropulsionThrusterDyeMixin implements ColorEffectTarget{
    @Unique private final ColorEffectState createthrusters$dye = new ColorEffectState();

    @Override public boolean acceptsColorEffect(){ return PropulsionDyeCompatibility.enabled(); }

    @Override public ColorEffectTarget colorEffectTarget(){
        var owner = (AbstractThrusterBlockEntity) (Object) this;
        // Multiblock variants keep their controller separately from the base thruster
        var controller = owner instanceof ThrusterBlockEntity thruster ? thruster.getControllerBE()
                : owner instanceof CreativeThrusterBlockEntity creative ? creative.getControllerBE() : owner;
        return controller instanceof ColorEffectTarget target ? target : this;
    }

    @Override public ColorEffectPreset colorEffect(){
        var owner = colorEffectTarget();
        return owner == this ? createthrusters$dye.preset() : owner.colorEffect();
    }

    @Override public void applyColorEffect(ColorEffectPreset preset){
        if(!acceptsColorEffect()) return;
        var owner = colorEffectTarget();
        if(owner != this) owner.applyColorEffect(preset);
        else createthrusters$dye.apply((BlockEntity) (Object) this, preset);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createthrusters$writeDye(CompoundTag tag, HolderLookup.Provider provider, boolean packet, CallbackInfo ci){ createthrusters$dye.write(tag); }

    @Inject(method = "read", at = @At("TAIL"))
    private void createthrusters$readDye(CompoundTag tag, HolderLookup.Provider provider, boolean packet, CallbackInfo ci){ createthrusters$dye.read(tag); }

    @ModifyArg(method = {"emitParticles", "emitResolvedParticles"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z"), index = 1, require = 0)
    private ParticleOptions createthrusters$serverParticleDye(ParticleOptions original){
        return acceptsColorEffect() ? ColorEffectParticles.wrap(original, PropulsionDyeCompatibility.GROUP, colorEffect()) : original;
    }

    @ModifyArg(method = {"emitParticles", "emitResolvedParticles"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"), index = 0, require = 0)
    private ParticleOptions createthrusters$clientParticleDye(ParticleOptions original){
        return acceptsColorEffect() ? ColorEffectParticles.wrap(original, PropulsionDyeCompatibility.GROUP, colorEffect()) : original;
    }
}
