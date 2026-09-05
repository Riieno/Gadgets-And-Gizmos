package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

// Keep the instanced Physics Gantry Belt Wheel model aligned with its live block state
public class PhysicsGantryBeltWheelVisual extends EncasedCogVisual {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel visual
    public PhysicsGantryBeltWheelVisual(VisualizationContext ctx, PhysicsGantryBeltWheelBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity,false, partialTick,Models.partial(AllPartialModels.SHAFTLESS_COGWHEEL));
    }

}
