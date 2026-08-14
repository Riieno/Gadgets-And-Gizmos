package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.SchematicBlockEntityConfigPayload;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Prepare addon block data for Create schematics
@Mixin(BlockHelper.class)
public abstract class CreateSchematicSafeNbtMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prepare the addon safe NBT
    @Inject(method = "prepareBlockEntityData", at = @At("RETURN"), cancellable = true)
    private static void ct$discardEmptyAddonSafeNbt(Level level, BlockState state, BlockEntity blockEntity,
                                                     CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag data = cir.getReturnValue();
        if (data != null && blockEntity instanceof ShipDockBlockEntity) {
            SchematicBlockEntityConfigPayload.clearShipDockPlacementIdentity(data);
        }
        if (data == null || !data.isEmpty()) {
            return;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId != null && CreateThrusters.MOD_ID.equals(blockId.getNamespace())) {
            cir.setReturnValue(null);
        }
    }
}
