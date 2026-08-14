package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rieno.gadgetsandgizmos.compat.sable.AileronBearingCameraCollisionFilter;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

// Ignore the mounted aileron during camera collision checks
@Mixin(value = SubLevelHelper.class, remap = false)
public abstract class AileronBearingCameraCollisionMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ignore aileron sub levels
    @ModifyReturnValue(
            method = "getConnectedChain(Ldev/ryanhcode/sable/sublevel/SubLevel;)Ljava/util/Collection;",
            at = @At("RETURN")
    )
    private static Collection<SubLevel> createthrusters$ignoreAileronSubLevels(
            Collection<SubLevel> connectedChain) {
        return AileronBearingCameraCollisionFilter.extendIgnoredChain(connectedChain);
    }
}
