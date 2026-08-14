package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Protect Sable sub-level Container Removal
@Mixin(SubLevelContainer.class)
public abstract class SableSubLevelContainerRemovalSafetyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get all sub levels
    @Shadow
    public abstract List<? extends SubLevel> getAllSubLevels();

    // Get the sublevel
    @Shadow
    public abstract SubLevel getSubLevel(java.util.UUID uuid);

    // Remove the sublevel
    @Shadow
    public abstract void removeSubLevel(SubLevel subLevel, SubLevelRemovalReason reason);

    // Process the removals safely
    @Inject(method = "processSubLevelRemovals", at = @At("HEAD"), cancellable = true)
    private void createthrusters$processRemovalsSafely(CallbackInfo callbackInfo) {
        SubLevelContainer container = (SubLevelContainer) (Object) this;
        for (SubLevel subLevel : List.copyOf(container.getAllSubLevels())) {
            if (subLevel instanceof ServerSubLevel serverSubLevel
                    && !serverSubLevel.isRemoved()
                    && serverSubLevel.getMassTracker() != null
                    && serverSubLevel.getMassTracker().isInvalid()) {
                serverSubLevel.getPlot().destroyAllBlocks();
                serverSubLevel.markRemoved();
            }
            if (!subLevel.isRemoved()
                    || container.getSubLevel(subLevel.getUniqueId()) != subLevel) {
                continue;
            }
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }
        callbackInfo.cancel();
    }
}
