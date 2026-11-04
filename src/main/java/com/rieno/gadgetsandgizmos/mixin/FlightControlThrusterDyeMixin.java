package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.SmartVectorThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectState;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Save the applicator preset without replacing Flight Control's native flame settings
@Pseudo
@Mixin(value = SmartVectorThrusterBlockEntity.class, remap = false)
public abstract class FlightControlThrusterDyeMixin implements ColorEffectTarget{
    @Unique private final ColorEffectState createthrusters$dye = new ColorEffectState();

    @Override public boolean acceptsColorEffect(){ return CTConfigs.COMMON.dyeApplicatorFlightControl.get(); }

    @Override public ColorEffectTarget colorEffectTarget(){
        var owner = ((SmartVectorThrusterBlockEntity) (Object) this).getClusterControllerOrSelf();
        return owner instanceof ColorEffectTarget target ? target : this;
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

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void createthrusters$writeDye(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci){ createthrusters$dye.write(tag); }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void createthrusters$readDye(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci){ createthrusters$dye.read(tag); }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void createthrusters$syncDye(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir){
        createthrusters$dye.write(cir.getReturnValue());
    }
}
