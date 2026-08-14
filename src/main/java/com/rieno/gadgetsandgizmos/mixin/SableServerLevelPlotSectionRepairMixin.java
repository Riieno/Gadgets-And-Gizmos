package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.compat.sable.SablePlotSectionDataRepair;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Repair Sable Server Level Plot Section
@Mixin(ServerLevelPlot.class)
public class SableServerLevelPlotSectionRepairMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CREATETHRUSTERS_LOGGER = LogUtils.getLogger();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the repair invalid section keys
    @Inject(method = "load", at = @At("HEAD"))
    private void createthrusters$repairInvalidSectionKeys(CompoundTag plotTag, CallbackInfo callbackInfo) {
        ServerLevelPlot plot = (ServerLevelPlot) (Object) this;
        int sectionCount = plot.getSubLevel().getLevel().getSectionsCount();
        int removed = SablePlotSectionDataRepair.removeInvalidSectionKeys(plotTag, sectionCount);
        if (removed > 0) {
            CREATETHRUSTERS_LOGGER.warn(
                    "[CT][Compat] Removed {} invalid Sable plot section entries before loading; valid indexes are 0 through {}",
                    removed, sectionCount - 1);
        }
    }
}
