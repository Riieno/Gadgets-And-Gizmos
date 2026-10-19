package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.neoforge.compat.GadgetsThrusterCommand;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

// Initialize Flight Control's merged command before unlinking a native thruster
@Mixin(value = ThrusterBlockEntity.class, priority = 900)
public abstract class FlightControlGadgetsThrusterCommandMixin{
    @Unique private static volatile Field ct$gadgetsCommandField;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeCommand(CallbackInfo ci){
        ct$initializeCommand();
    }

    @Dynamic("Added by Flight Control's Gadgets thruster compatibility mixin")
    @Inject(method = {"clearFCCLinked", "clearFCLinked"}, at = @At("HEAD"), remap = false, require = 0)
    private void prepareUnlink(CallbackInfo ci){
        ct$initializeCommand();
    }

    // Resolve the field after Flight Control's mixin has merged it into the runtime class
    @Unique private void ct$initializeCommand(){
        try{
            Field field = ct$gadgetsCommandField;
            if(field == null){
                for(String name : new String[]{"cfc$gadgetsCommand", "cf$gadgetsCommand"}){
                    try{ field = ThrusterBlockEntity.class.getDeclaredField(name); break; }
                    catch(NoSuchFieldException ignored){}
                }
                if(field == null) return;
                field.setAccessible(true);
                ct$gadgetsCommandField = field;
            }
            if(field.get(this) == null) field.set(this, new GadgetsThrusterCommand());
        }catch(IllegalAccessException err){
            throw new IllegalStateException("Cannot initialize Flight Control's thruster command", err);
        }
    }
}
