package com.rieno.gadgetsandgizmos.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep physics-thread visual updates on the owning game thread and coalesce repeated sync requests
@Pseudo
@Mixin(targets = "ace.flight.block.SmartVectorThrusterBlockEntity", remap = false)
public abstract class FlightControlThrusterSyncMixin{
    @Inject(method = "syncToClient", at = @At("HEAD"), cancellable = true)
    private void createThrusters$sync(CallbackInfo ci){
        BlockEntity component = (BlockEntity) (Object) this;
        if(!(component.getLevel() instanceof ServerLevel)) return;
        ci.cancel();
        BlockEntityDataSync.enqueue(component);
    }
}
