package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Expose Directional Gearshift Block
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock")
public interface DirectionalGearshiftBlockInvoker {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Detach the kinetics
    @Invoker("detachKinetics")
    void ct$detachKinetics(Level worldIn, BlockPos pos, boolean reAttachNextTick);
}