package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Add portable CRN data to ship display packets
@Pseudo
@Mixin(targets = "de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity", remap = false)
public abstract class RailwayNavigatorShipTelemetryMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the ship telemetry
    @Inject(method = "write", at = @At("TAIL"), require = 0)
    private void createthrusters$writeShipTelemetry(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo callback
    ) {
        RailwayNavigatorGraphCompat.writeShipTelemetry((BlockEntity) (Object) this, tag);
    }

    // Read the ship telemetry
    @Inject(method = "read", at = @At("TAIL"), require = 0)
    private void createthrusters$readShipTelemetry(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo callback
    ) {
        RailwayNavigatorGraphCompat.readShipTelemetry((BlockEntity) (Object) this, tag);
    }
}
