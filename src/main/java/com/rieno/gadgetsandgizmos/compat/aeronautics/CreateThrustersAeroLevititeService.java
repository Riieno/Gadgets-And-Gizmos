package com.rieno.gadgetsandgizmos.compat.aeronautics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.eriksonn.aeronautics.service.AeroLevititeService;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;

// Supply the Aeronautics Levitite fluid and bucket
public class CreateThrustersAeroLevititeService implements AeroLevititeService {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the bucket
    @Override
    public Item getBucket() {

        return Items.WATER_BUCKET;
    }

    // Get the fluid
    @Override
    public Fluid getFluid() {

        return Fluids.WATER;
    }
}
