package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerTracker;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Track sub-level Assembly Helper Linker
@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperLinkerTrackingMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Move the linker targets
    @Inject(method = "moveTrackingPoints", at = @At("HEAD"))
    private static void createthrusters$moveLinkerTargets(ServerLevel level,
                                                          BoundingBox3ic bounds,
                                                          ServerSubLevel destinationSubLevel,
                                                          SubLevelAssemblyHelper.AssemblyTransform transform,
                                                          CallbackInfo ci) {
        ContraptionNetworkLinkerTracker.get(level.getServer())
                .remapAssemblyTargets(level, bounds, destinationSubLevel, transform);
    }
}
