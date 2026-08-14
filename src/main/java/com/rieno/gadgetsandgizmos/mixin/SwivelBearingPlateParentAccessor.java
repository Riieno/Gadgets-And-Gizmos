package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

// Expose Swivel Bearing Plate Parent
@Mixin(SwivelBearingPlateBlockEntity.class)
public interface SwivelBearingPlateParentAccessor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the parent
    @Accessor("parent")
    BlockPos createthrusters$getParent();

    // Get the parent sublevel id
    @Accessor("parentSubLevelId")
    UUID createthrusters$getParentSubLevelId();
}
