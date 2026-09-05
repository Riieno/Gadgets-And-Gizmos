package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rieno.gadgetsandgizmos.compat.sable.BearingCameraCollisionFilter;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

// Ignore bearing-owned mounted sublevels during contraption camera collision checks
@Mixin(value = SubLevelHelper.class, remap = false)
public abstract class BearingCameraCollisionMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add bearing-owned sublevels to the ignored connected chain
    @ModifyReturnValue(
            method = "getConnectedChain(Ldev/ryanhcode/sable/sublevel/SubLevel;)Ljava/util/Collection;",
            at = @At("RETURN")
    )
    private static Collection<SubLevel> createthrusters$ignoreBearingSubLevels(
            Collection<SubLevel> connectedChain) {
        return BearingCameraCollisionFilter.extendIgnoredChain(connectedChain);
    }
}
