package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Handle Lectern Portable Contraption Controller Block Entity
@Mixin(BlockEntity.class)
public abstract class LecternPortableContraptionControllerBlockEntityMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the portable controller update packet
    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void createthrusters$getPortableControllerUpdatePacket(
            CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        if ((Object) this instanceof LecternBlockEntity lectern) {
            cir.setReturnValue(ClientboundBlockEntityDataPacket.create(lectern));
        }
    }

    // Handle the portable controller update tag
    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
    private void createthrusters$getPortableControllerUpdateTag(HolderLookup.Provider registries,
                                                               CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof LecternBlockEntity) {
            cir.setReturnValue(((BlockEntity) (Object) this).saveWithoutMetadata(registries));
        }
    }
}
