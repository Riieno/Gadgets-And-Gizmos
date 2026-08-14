package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.OversizedNbtRecovery;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Recover oversized controller NBT from network buffers
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufOversizedNbtMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Recover oversized legacy NBT
    @Redirect(
            method = "readNbt()Lnet/minecraft/nbt/CompoundTag;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag createthrusters$recoverOversizedLegacyNbt(ByteBuf buffer) {
        int readerIndex = buffer.readerIndex();
        try {
            return FriendlyByteBuf.readNbt(buffer);
        } catch (NbtAccounterException err) {
            return OversizedNbtRecovery.readSanitized(buffer, readerIndex, err);
        }
    }
}
