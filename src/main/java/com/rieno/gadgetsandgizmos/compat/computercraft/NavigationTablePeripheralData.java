package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.NavigationTableGraphData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

// Store navigation table peripheral data
public final class NavigationTablePeripheralData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table peripheral data
    private NavigationTablePeripheralData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the table position
    public static Map<String, Object> tablePosition(BlockEntity blockEntity) {
        return ComputerCraftPositionHelper.blockPosition(blockEntity);
    }

    // Get the target position
    public static @Nullable Map<String, Object> targetPosition(BlockEntity blockEntity,
                                                               @Nullable Vec3 localTarget) {
        if (localTarget == null) {
            return null;
        }
        Vec3 worldTarget = SimulatedHelper.projectOutOfSubLevels(blockEntity.getLevel(), localTarget);
        Map<String, Object> pos = ComputerCraftPositionHelper.worldPosition(
                blockEntity.getLevel(), worldTarget);
        Object targetSubLevel = SimulatedHelper.getContainingSubLevel(blockEntity.getLevel(), localTarget);
        UUID targetSubLevelId = SimulatedHelper.getSubLevelId(targetSubLevel);
        if (targetSubLevelId != null) {
            pos.put("localX", localTarget.x);
            pos.put("localY", localTarget.y);
            pos.put("localZ", localTarget.z);
        }
        pos.put("subLevelId", targetSubLevelId == null ? "" : targetSubLevelId.toString());
        return pos;
    }

    // Get the target distance
    public static double targetDistance(BlockEntity blockEntity, @Nullable Vec3 localTarget) {
        return NavigationTableGraphData.targetDistance(blockEntity, localTarget);
    }

    // Get the horizontal distance
    static double horizontalDistance(Vec3 first, Vec3 second) {
        return NavigationTableGraphData.horizontalDistance(first, second);
    }
}
