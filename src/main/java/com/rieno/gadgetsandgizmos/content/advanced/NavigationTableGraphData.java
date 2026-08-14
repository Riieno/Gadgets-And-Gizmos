package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Store navigation table graph data
public final class NavigationTableGraphData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table graph data
    private NavigationTableGraphData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the target distance
    public static double targetDistance(
            BlockEntity blockEntity,
            @Nullable Vec3 localTarget
    ) {
        if (localTarget == null) {
            return -1.0D;
        }
        Vec3 tablePosition = SimulatedHelper.toGlobalWorldPosition(
                blockEntity, Vec3.atCenterOf(blockEntity.getBlockPos()));
        Vec3 targetPosition = SimulatedHelper.projectOutOfSubLevels(
                blockEntity.getLevel(), localTarget);
        return horizontalDistance(tablePosition, targetPosition);
    }

    // Get the target coordinates
    public static AdvancedGraphDocument.Value targetCoordinates(NavTableBlockEntity navigationTable) {
        Vec3 target = navigationTable == null ? null : navigationTable.getTargetPosition(true);
        return coordinateMap(target);
    }

    // Get the coordinate map
    static AdvancedGraphDocument.Value coordinateMap(@Nullable Vec3 target) {
        CompoundTag coordinates = new CompoundTag();
        coordinates.putDouble("x", target == null ? 0.0D : target.x);
        coordinates.putDouble("y", target == null ? 0.0D : target.y);
        coordinates.putDouble("z", target == null ? 0.0D : target.z);
        return AdvancedGraphDocument.Value.map(coordinates);
    }

    // Get the horizontal distance
    public static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }
}
