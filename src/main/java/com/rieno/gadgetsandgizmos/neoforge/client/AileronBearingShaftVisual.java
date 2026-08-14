package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;

// Draw the Aileron Bearing Shaft visual
public class AileronBearingShaftVisual extends ShaftVisual<AileronBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing shaft visual
    public AileronBearingShaftVisual(VisualizationContext ctx, AileronBearingBlockEntity blockEntity,
                                     float partialTick) {
        super(ctx, blockEntity, partialTick);
        CTFlywheelVisuals.forceWhite(rotatingModel).setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the aileron bearing shaft visual
    @Override
    public void update(float partialTick) {
        super.update(partialTick);
        CTFlywheelVisuals.forceWhite(rotatingModel).setChanged();
    }
}
