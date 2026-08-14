package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.SchematicBlockEntityConfigPayload;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Carry addon block settings through Sable schematics
@Mixin(BlockEntity.class)
public abstract class SableSchematicBlockEntityConfigMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture the schematic block data
    @Inject(method = "saveWithFullMetadata", at = @At("RETURN"))
    private void createThrusters$captureSchematicBlockData(
            HolderLookup.Provider provider,
            CallbackInfoReturnable<CompoundTag> callback
    ) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (SchematicBlockEntityConfigPayload.supports(
                BlockEntityType.getKey(blockEntity.getType()))) {
            SchematicBlockEntityConfigPayload.captureForSableSave(
                    callback.getReturnValue());
        }
    }

    // Restore the schematic block data
    @Inject(method = "loadWithComponents", at = @At("HEAD"))
    private void createThrusters$restoreSchematicBlockData(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback
    ) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (SchematicBlockEntityConfigPayload.supports(
                BlockEntityType.getKey(blockEntity.getType()))) {
            boolean placing = SchematicBlockEntityConfigPayload.restoreForSablePlacement(tag);
            if (placing && blockEntity instanceof ShipDockBlockEntity) {
                SchematicBlockEntityConfigPayload.clearShipDockPlacementIdentity(tag);
            }
        }
    }
}
