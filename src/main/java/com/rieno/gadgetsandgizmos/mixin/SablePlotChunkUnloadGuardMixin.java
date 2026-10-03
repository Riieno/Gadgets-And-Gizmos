package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep Sable plot chunks out of vanilla's unload retry queue
@Mixin(ChunkMap.class)
public abstract class SablePlotChunkUnloadGuardMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sable persists plot chunks separately; their holders intentionally never become vanilla-save-ready
    @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
    private void createthrusters$skipSablePlotUnload(long chunkKey, ChunkHolder chunkHolder,
                                                      CallbackInfo callbackInfo) {
        if (chunkHolder != null
                && "dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder".equals(chunkHolder.getClass().getName())) {
            callbackInfo.cancel();
        }
    }
}
