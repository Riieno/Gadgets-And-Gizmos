package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

// Bridge Sable Server Level Plot Contraptions
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot")
public class SableServerLevelPlotContraptionsBridgeMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked contraptions
    @Shadow
    @Final
    private Set<KinematicContraption> contraptions;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the contraptions
    public ObjectCollection<KinematicContraption> getContraptions() {
        if (contraptions instanceof ObjectCollection<KinematicContraption> objectCollection) {
            return objectCollection;
        }
        return new ObjectArrayList<>(contraptions);
    }
}
