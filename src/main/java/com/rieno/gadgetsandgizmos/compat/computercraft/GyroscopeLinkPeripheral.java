package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Expose Gyroscope Link controls and telemetry to ComputerCraft
@PeripheralTypeDoc({"advanced_data_link", "gyroscope_link"})
public class GyroscopeLinkPeripheral extends GadgetsPeripheral<GyroscopeLinkBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link peripheral
    public GyroscopeLinkPeripheral(GyroscopeLinkBlockEntity blockEntity) {
        super(blockEntity, "advanced_data_link");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the additional types
    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of("gyroscope_link");
    }

    // Check if this is linked
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isLinked", signature = "isLinked(): boolean",
            description = "Returns whether this is linked.")
    public final boolean isLinked() {
        return blockEntity.isLinked();
    }

    // Get the target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTarget", signature = "getTarget(): table",
            description = "Returns the target.")
    public final Map<String, Object> getTarget() {
        Map<String, Object> target = new LinkedHashMap<>();
        BlockPos pos = blockEntity.getGyroPos();
        target.put("linked", isLinked());
        var resolvedTarget = blockEntity.getResolvedGyroTarget();
        if (resolvedTarget != null) {
            target.putAll(ComputerCraftPositionHelper.blockPosition(resolvedTarget));
        } else if (blockEntity.getGyroLocalPos() != null) {
            target.putAll(ComputerCraftPositionHelper.blockPosition(
                    blockEntity.getLevel(), blockEntity.getGyroSubLevelId(), blockEntity.getGyroLocalPos()));
        } else if (pos != null) {
            target.putAll(ComputerCraftPositionHelper.blockPosition(blockEntity.getLevel(), null, pos));
        }
        target.put("dimension", blockEntity.getGyroDimension() == null ? "" : blockEntity.getGyroDimension());
        return target;
    }

    // Get the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() {
        Map<String, Object> angles = new LinkedHashMap<>();
        double[] rad = blockEntity.getLinkedAnglesRadians();
        double[] deg = blockEntity.getLinkedAnglesDegrees();
        angles.put("linked", blockEntity.isLinked());
        angles.put("live", rad != null);
        if (rad != null && deg != null) {
            angles.put("xRadians", rad[0]);
            angles.put("zRadians", rad[1]);
            angles.put("xDegrees", deg[0]);
            angles.put("zDegrees", deg[1]);
        }
        return angles;
    }

    // Get the direction
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getDirection", signature = "getDirection(): table",
            description = "Returns the direction.")
    public final Map<String, Object> getDirection() {
        Map<String, Object> dir = new LinkedHashMap<>();
        Vec3 linkedDirection = blockEntity.getLinkedDirection();
        dir.put("linked", blockEntity.isLinked());
        dir.put("live", linkedDirection != null);
        if (linkedDirection != null) {
            dir.put("x", linkedDirection.x);
            dir.put("y", linkedDirection.y);
            dir.put("z", linkedDirection.z);
        }
        return dir;
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("target", getTarget());
        status.put("mode", getMode());
        status.put("angles", getAngles());
        status.put("direction", getDirection());
        return status;
    }

    // Get the mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() {
        return blockEntity.getTrackingMode().name().toLowerCase();
    }

    // Set the mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMode", signature = "setMode(mode:string)",
            description = "Sets the mode.")
    public final void setMode(String modeName) throws LuaException {
        try {
            blockEntity.setTrackingMode(GyroscopeLinkBlockEntity.TrackingMode.valueOf(modeName.trim().toUpperCase()));
        } catch (IllegalArgumentException err) {
            throw new LuaException("unknown Advanced Data Link mode '" + modeName + "'");
        }
    }

    // Set the target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setTarget", signature = "setTarget(x:number,y:number,z:number,dimension?:string)",
            description = "Sets the target.")
    public final void setTarget(int x, int y, int z, Optional<String> dimension) throws LuaException {
        String dim = dimension.orElseGet(() -> blockEntity.getLevel() == null
                ? null
                : blockEntity.getLevel().dimension().location().toString());
        if (dim == null) {
            throw new LuaException("dimension is required when the link is not in a level");
        }
        blockEntity.setGyroTarget(new BlockPos(x, y, z), ResourceLocation.parse(dim));
    }

    // Clear the target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearTarget", signature = "clearTarget()",
            description = "Clears the target.")
    public final void clearTarget() {
        blockEntity.setGyroTarget(null, null);
    }
}
