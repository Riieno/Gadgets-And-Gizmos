package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Expose Gyroscope Link controls and telemetry to ComputerCraft
public class GyroscopeLinkPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final GyroscopeLinkBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link peripheral
    public GyroscopeLinkPeripheral(GyroscopeLinkBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "advanced_data_link";
    }

    // Get the additional types
    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of("gyroscope_link");
    }

    // Compare this gyroscope link peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof GyroscopeLinkPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Check if this is linked
    @LuaFunction
    public final boolean isLinked() {
        return blockEntity.isLinked();
    }

    // Get the target
    @LuaFunction
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
    @LuaFunction
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
    @LuaFunction
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
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("target", getTarget());
        status.put("mode", getMode());
        status.put("angles", getAngles());
        status.put("direction", getDirection());
        return status;
    }

    // Get the mode
    @LuaFunction
    public final String getMode() {
        return blockEntity.getTrackingMode().name().toLowerCase();
    }

    // Set the mode
    @LuaFunction
    public final void setMode(String modeName) throws LuaException {
        try {
            blockEntity.setTrackingMode(GyroscopeLinkBlockEntity.TrackingMode.valueOf(modeName.trim().toUpperCase()));
        } catch (IllegalArgumentException err) {
            throw new LuaException("unknown Advanced Data Link mode '" + modeName + "'");
        }
    }

    // Set the target
    @LuaFunction
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
    @LuaFunction
    public final void clearTarget() {
        blockEntity.setGyroTarget(null, null);
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "isLinked(): boolean",
                "getTarget(): table",
                "getAngles(): table",
                "getDirection(): table",
                "getStatus(): table",
                "getMode(): string",
                "setMode(mode:string)",
                "setTarget(x:number,y:number,z:number,dimension?:string)",
                "clearTarget()",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = Map.ofEntries(
            Map.entry("isLinked", "isLinked() -> true when an orientation source target is stored"),
            Map.entry("getTarget", "getTarget() -> live projected target with linked/x/y/z, local coordinates, subLevelId, and dimension"),
            Map.entry("getAngles", "getAngles() -> live angle table with x/z in radians and degrees when available"),
            Map.entry("getDirection", "getDirection() -> live direction vector derived from the linked source angles"),
            Map.entry("getStatus", "getStatus() -> combined target, angle, and direction tables"),
            Map.entry("getMode", "getMode() -> current Advanced Data Link tracking mode: 'live' or 'static'"),
            Map.entry("setMode", "setMode(mode) -> switch between live moving-target tracking and static world-position sampling"),
            Map.entry("setTarget", "setTarget(x,y,z,dimension?) -> set the linked sensor position"),
            Map.entry("clearTarget", "clearTarget() -> unlink this block"),
            Map.entry("methods", "methods() -> list of all callable peripheral methods"),
            Map.entry("help", "help() -> all docs, help('name') -> one entry")
        );
        if (method.isEmpty()) {
            return docs;
        }
        String key = method.get();
        if (!docs.containsKey(key)) {
            throw new LuaException("unknown method '" + key + "'");
        }
        return docs.get(key);
    }
}
