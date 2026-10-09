package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings;
import com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route native display packets through the owning ACC
@Pseudo
@Mixin(targets = {
        "ace.flight.block.HudProjectorBlockEntity",
        "ace.flight.block.AttitudeDisplayBlockEntity",
        "ace.flight.block.CreativeFlightControlComputerBlockEntity",
        "ace.flight.block.ForwardIndicatorBlockEntity",
        "ace.flight.block.AerodynamicTrailCreatorBlockEntity"
}, remap = false)
public abstract class FlightControlHostedSyncMixin{
    @Inject(method = {"sendData", "syncToClient", "saveAndSync"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void createThrusters$hostedSync(CallbackInfo ci){
        BlockEntity component = (BlockEntity) (Object) this;
        if(HostedBlockEntities.notifyHostData(component, true)){ ci.cancel(); return; }
        if(BlockEntityBindings.host(component) != null){
            BlockEntityDataSync.enqueue(component);
            ci.cancel();
        }
    }
}
