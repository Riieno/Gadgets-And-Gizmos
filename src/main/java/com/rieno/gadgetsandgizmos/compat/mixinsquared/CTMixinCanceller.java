package com.rieno.gadgetsandgizmos.compat.mixinsquared;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;

// Disable optional mixins when their target mod is missing
public final class CTMixinCanceller implements MixinCanceller {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String BNB_FLUID_PIPE_MIXIN = "com.kipti.bnb.mixin.dyeable.pipes.FluidPipeBlockMixin";
    private static final String CREATE_FLUID_PIPE_BLOCK = "com.simibubi.create.content.fluids.pipes.FluidPipeBlock";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this should cancel
    @Override
    public boolean shouldCancel(List<String> targetClasses, String mixinClassName) {
        if (!BNB_FLUID_PIPE_MIXIN.equals(mixinClassName)) {
            return false;
        }

        if (!targetClasses.contains(CREATE_FLUID_PIPE_BLOCK)) {
            return false;
        }

        return isLoaded("bits_n_bobs") && isLoaded("tfmg");
    }

    // Check if this is loaded
    private static boolean isLoaded(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        return loadingModList != null && loadingModList.getModFileById(modId) != null;
    }
}
