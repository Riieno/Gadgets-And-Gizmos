package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Check Tom's Storage terminal range across Sable sub-levels
@Mixin(targets = "com.tom.storagemod.block.entity.StorageTerminalBlockEntity", remap = false)
public abstract class TomsStorageTerminalRangeCompatMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current level
    @Shadow
    protected Level level;

    // Current world position
    @Shadow
    protected BlockPos worldPosition;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the sublevel aware distance
    @Redirect(
            method = "canInteractWith",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
            ),
            require = 0
    )
    private double ct$useSubLevelAwareDistance(Player player, double x, double y, double z) {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return player.distanceToSqr(x, y, z);
        }

        BlockEntity terminal = SimulatedHelper.findBlockEntityIncludingSubLevels(currentLevel, this.worldPosition);
        Vec3 terminalPos = terminal != null ? SimulatedHelper.toContainingWorldPosition(terminal, this.worldPosition.getCenter()) : null;
        if (terminalPos == null) {
            terminalPos = new Vec3(x, y, z);
        }
        return SimulatedHelper.distanceSquaredWithSubLevels(currentLevel, player.position(), terminalPos);
    }
}
