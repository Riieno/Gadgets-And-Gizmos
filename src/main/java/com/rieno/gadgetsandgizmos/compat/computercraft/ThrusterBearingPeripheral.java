package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

// Expose bearing pose and attached-thruster control through a stable ComputerCraft API
public class ThrusterBearingPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final ThrusterBearingBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing peripheral
    public ThrusterBearingPeripheral(ThrusterBearingBlockEntity blockEntity) {
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
        return "thruster_bearing";
    }

    // Compare this thruster bearing peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ThrusterBearingPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the thrusters
    private Map<String, ThrusterBlockEntity> getThrusters() {
        return blockEntity.getAttachedThrustersById();
    }

    // Find the thruster id by alias
    private String findThrusterIdByAlias(String alias) {
        String normalized = alias == null ? "" : alias.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ThrusterBlockEntity> entry : getThrusters().entrySet()) {
            if (normalized.equals(blockEntity.getThrusterAlias(entry.getValue()))) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Resolve the thrusters
    private List<ThrusterBlockEntity> resolveThrusters(String thrusterId) throws LuaException {
        Map<String, ThrusterBlockEntity> thrusters = getThrusters();
        if (thrusters.isEmpty()) {
            throw new LuaException("no attached thrusters found");
        }
        if (thrusterId == null || thrusterId.isBlank() || "all".equalsIgnoreCase(thrusterId.trim())) {
            return List.copyOf(thrusters.values());
        }
        ThrusterBlockEntity thruster = thrusters.get(thrusterId);
        if (thruster == null) {
            String resolvedId = findThrusterIdByAlias(thrusterId);
            if (resolvedId != null) {
                thruster = thrusters.get(resolvedId);
            }
        }
        if (thruster == null) {
            throw new LuaException("unknown thruster id or alias '" + thrusterId + "'");
        }
        return List.of(thruster);
    }

    // Get the thruster value map
    private Map<String, Object> thrusterValueMap(Function<ThrusterBlockEntity, Object> mapper) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : getThrusters().entrySet()) {
            values.put(entry.getKey(), mapper.apply(entry.getValue()));
        }
        return values;
    }

    // Apply the thrusters
    private void applyToThrusters(String thrusterId, Consumer<ThrusterBlockEntity> action) throws LuaException {
        for (ThrusterBlockEntity thruster : resolveThrusters(thrusterId)) {
            action.accept(thruster);
        }
    }

    // Build the thruster status
    private Map<String, Object> buildThrusterStatus(String thrusterId, ThrusterBlockEntity thruster) {
        Map<String, Object> status = new LinkedHashMap<>();
        if (thrusterId != null) {
            status.put("id", thrusterId);
        }
        status.put("alias", blockEntity.getThrusterAlias(thruster));
        status.put("enabled", thruster.isEnabled());
        status.put("throttle", thruster.getThrottle());
        status.put("computerThrottle", thruster.getComputerThrottle());
        status.put("controlMode", thruster.getControlMode().name().toLowerCase());
        status.put("fuel", thruster.getFuelAmount());
        status.put("fuelCapacity", thruster.getFuelCapacity());
        status.put("fuelType", thruster.getFuelTypeId());
        status.put("burnTimeSeconds", thruster.getEstimatedBurnSeconds());
        status.put("thrust", thruster.getThrust());
        status.put("realThrust", thruster.getRealThrust());
        status.put("liftCapacity", thruster.getLiftCapacity());
        status.put("airflow", thruster.getAirflow());
        status.put("active", thruster.isActive());
        status.put("soulMode", thruster.isSoulThruster());
        status.put("redstoneSignal", thruster.getSignalStrength());
        return status;
    }

    // Normalize the throttle
    private static float normalizeThrottle(double throttle) throws LuaException {
        if (Double.isNaN(throttle) || Double.isInfinite(throttle) || throttle < 0.0D) {
            throw new LuaException("throttle must be between 0..1 or 0..100");
        }
        if (throttle <= 1.0D) {
            return (float) throttle;
        }
        if (throttle <= 100.0D) {
            return (float) (throttle / 100.0D);
        }
        throw new LuaException("throttle must be between 0..1 or 0..100");
    }

    // Get the name
    @LuaFunction
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the forward signal
    @LuaFunction
    public final int getForwardSignal() {
        return blockEntity.getForwardSignal();
    }

    // Get the backward signal
    @LuaFunction
    public final int getBackwardSignal() {
        return blockEntity.getBackwardSignal();
    }

    // Get the pivot angle
    @LuaFunction
    public final double getPivotAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the current angle
    @LuaFunction
    public final double getCurrentAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the target angle
    @LuaFunction
    public final double getTargetAngle() {
        return blockEntity.getTargetAngleDegrees();
    }

    // Get the bearing control mode
    @LuaFunction
    public final String getBearingControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Get the servo input angle
    @LuaFunction
    public final double getServoInputAngle() {
        return blockEntity.getServoInputAngleDegrees();
    }

    // Get the min angle
    @LuaFunction
    public final double getMinAngle() {
        return blockEntity.getMinAngleDegrees();
    }

    // Get the max angle
    @LuaFunction
    public final double getMaxAngle() {
        return blockEntity.getMaxAngleDegrees();
    }

    // Set the pivot angle
    @LuaFunction
    public final void setPivotAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setPivotAngleDegrees(angleDeg);
    }

    // Set the bearing control mode
    @LuaFunction
    public final void setBearingControlMode(String mode) throws LuaException {
        if (mode == null) {
            throw new LuaException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        }
        String normalized = mode.trim().toLowerCase();
        ThrusterBearingBlockEntity.ControlMode controlMode = switch (normalized) {
            case "auto" -> ThrusterBearingBlockEntity.ControlMode.AUTO;
            case "redstone" -> ThrusterBearingBlockEntity.ControlMode.REDSTONE;
            case "computer" -> ThrusterBearingBlockEntity.ControlMode.COMPUTER;
            case "servo" -> ThrusterBearingBlockEntity.ControlMode.SERVO;
            default -> throw new LuaException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        };
        blockEntity.setControlMode(controlMode);
    }

    // Set the min angle
    @LuaFunction
    public final void setMinAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMinAngleDegrees(angleDeg);
    }

    // Set the max angle
    @LuaFunction
    public final void setMaxAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMaxAngleDegrees(angleDeg);
    }

    // Clear the pivot override
    @LuaFunction
    public final void clearPivotOverride() {
        blockEntity.clearPivotOverride();
    }

    // Get the list thrusters
    @LuaFunction
    public final Map<String, Object> listThrusters() {
        Map<String, Object> thrusters = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : getThrusters().entrySet()) {
            ThrusterBlockEntity thruster = entry.getValue();
            Map<String, Object> pos = ComputerCraftPositionHelper.blockPosition(thruster);
            thrusters.put(entry.getKey(), Map.of(
                    "alias", blockEntity.getThrusterAlias(thruster),
                    "pos", List.of(pos.get("x"), pos.get("y"), pos.get("z")),
                    "position", pos,
                    "localPos", List.of(thruster.getBlockPos().getX(), thruster.getBlockPos().getY(), thruster.getBlockPos().getZ()),
                    "fuelType", thruster.getFuelTypeId(),
                    "controlMode", thruster.getControlMode().name().toLowerCase(),
                    "enabled", thruster.isEnabled(),
                    "throttle", thruster.getThrottle()));
        }
        return thrusters;
    }

    // Get the thruster count
    @LuaFunction
    public final int getThrusterCount() {
        return getThrusters().size();
    }

    // Get the owned thrusters
    @LuaFunction
    public final List<String> getOwnedThrusters() {
        return List.copyOf(getThrusters().keySet());
    }

    // Get the ids
    @LuaFunction
    public final List<String> ids() {
        return getThrusters().values().stream()
                .map(blockEntity::getThrusterAlias)
                .filter(alias -> alias != null && !alias.isBlank())
                .toList();
    }

    // Get the network info
    @LuaFunction
    public final Map<String, Object> getNetworkInfo() {
        Map<String, ThrusterBlockEntity> thrusters = getThrusters();
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : thrusters.entrySet()) {
            aliases.put(entry.getKey(), blockEntity.getThrusterAlias(entry.getValue()));
        }

        return Map.of(
                "bearingType", getType(),
                "thrusterCount", thrusters.size(),
                "ownedThrusters", List.copyOf(thrusters.keySet()),
                "aliases", aliases,
                "thrusters", listThrusters()
        );
    }

    // Handle the thruster alias
    @LuaFunction
    public final void thrusterAlias(String thrusterId, String alias) throws LuaException {
        Map<String, ThrusterBlockEntity> thrusters = getThrusters();
        ThrusterBlockEntity thruster = thrusters.get(thrusterId);
        String resolvedId = thrusterId;
        if (thruster == null) {
            resolvedId = findThrusterIdByAlias(thrusterId);
            thruster = resolvedId == null ? null : thrusters.get(resolvedId);
        }
        if (thruster == null) {
            throw new LuaException("unknown thruster id or alias '" + thrusterId + "'");
        }
        String normalizedAlias = alias == null ? "" : alias.trim();
        if (!normalizedAlias.isEmpty()) {
            String existingId = findThrusterIdByAlias(normalizedAlias);
            if (existingId != null && !existingId.equals(resolvedId)) {
                throw new LuaException("alias already assigned to another thruster");
            }
        }
        blockEntity.setThrusterAlias(resolvedId, normalizedAlias);
    }

    // Set the throttle
    @LuaFunction
    public final void setThrottle(String thrusterId, double throttle) throws LuaException {
        float normalizedThrottle = normalizeThrottle(throttle);
        applyToThrusters(thrusterId, thruster -> thruster.setThrottle(normalizedThrottle));
    }

    // Get the throttle
    @LuaFunction
    public final Object getThrottle(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getThrottle);
        }
        return resolveThrusters(thrusterId).get(0).getThrottle();
    }

    // Get the throttle map
    @LuaFunction
    public final Map<String, Object> getThrottleMap(String thrusterId) throws LuaException {
        if (thrusterId == null || thrusterId.isBlank() || "all".equalsIgnoreCase(thrusterId.trim())) {
            return thrusterValueMap(ThrusterBlockEntity::getThrottle);
        }

        Map<String, ThrusterBlockEntity> thrusters = getThrusters();
        String resolvedId = thrusterId;
        ThrusterBlockEntity thruster = thrusters.get(resolvedId);
        if (thruster == null) {
            resolvedId = findThrusterIdByAlias(thrusterId);
            thruster = resolvedId == null ? null : thrusters.get(resolvedId);
        }
        if (thruster == null) {
            throw new LuaException("unknown thruster id or alias '" + thrusterId + "'");
        }

        Map<String, Object> throttleMap = new LinkedHashMap<>();
        throttleMap.put(resolvedId, thruster.getThrottle());
        return throttleMap;
    }

    // Set the enabled
    @LuaFunction
    public final void setEnabled(String thrusterId, boolean enabled) throws LuaException {
        applyToThrusters(thrusterId, thruster -> thruster.setEnabled(enabled));
    }

    // Check if the bearing is enabled
    @LuaFunction
    public final Object isEnabled(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isEnabled);
        }
        return resolveThrusters(thrusterId).get(0).isEnabled();
    }

    // Get the fuel
    @LuaFunction
    public final Object getFuel(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelAmount);
        }
        return resolveThrusters(thrusterId).get(0).getFuelAmount();
    }

    // Get the fuel capacity
    @LuaFunction
    public final Object getFuelCapacity(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getFuelCapacity();
    }

    // Get the fuel type
    @LuaFunction
    public final Object getFuelType(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelTypeId);
        }
        return resolveThrusters(thrusterId).get(0).getFuelTypeId();
    }

    // Get the burn time seconds
    @LuaFunction
    public final Object getBurnTimeSeconds(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getEstimatedBurnSeconds);
        }
        return resolveThrusters(thrusterId).get(0).getEstimatedBurnSeconds();
    }

    // Get the control mode
    @LuaFunction
    public final Object getControlMode(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(thruster -> thruster.getControlMode().name().toLowerCase());
        }
        return resolveThrusters(thrusterId).get(0).getControlMode().name().toLowerCase();
    }

    // Set the control mode
    @LuaFunction
    public final void setControlMode(String thrusterId, String mode) throws LuaException {
        if (mode == null) {
            throw new LuaException("mode must be 'auto', 'redstone' or 'computer'");
        }
        String normalized = mode.trim().toLowerCase();
        ThrusterBlockEntity.ControlMode controlMode = switch (normalized) {
            case "auto" -> ThrusterBlockEntity.ControlMode.AUTO;
            case "redstone" -> ThrusterBlockEntity.ControlMode.REDSTONE;
            case "computer" -> ThrusterBlockEntity.ControlMode.COMPUTER;
            default -> throw new LuaException("mode must be 'auto', 'redstone' or 'computer'");
        };
        applyToThrusters(thrusterId, thruster -> thruster.setControlMode(controlMode));
    }

    // Get the thrust
    @LuaFunction
    public final Object getThrust(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getThrust);
        }
        return resolveThrusters(thrusterId).get(0).getThrust();
    }

    // Get the real thrust
    @LuaFunction
    public final Object getRealThrust(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getRealThrust);
        }
        return resolveThrusters(thrusterId).get(0).getRealThrust();
    }

    // Get the lift capacity
    @LuaFunction
    public final Object getLiftCapacity(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getLiftCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getLiftCapacity();
    }

    // Get the total real thrust
    @LuaFunction
    public final double getTotalRealThrust() {
        return blockEntity.getAssemblyRealThrust();
    }

    // Get the total lift capacity
    @LuaFunction
    public final double getTotalLiftCapacity() {
        return blockEntity.getAssemblyLiftCapacity();
    }

    // Get the airflow
    @LuaFunction
    public final Object getAirflow(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getAirflow);
        }
        return resolveThrusters(thrusterId).get(0).getAirflow();
    }

    // Check if the bearing is active
    @LuaFunction
    public final Object isActive(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isActive);
        }
        return resolveThrusters(thrusterId).get(0).isActive();
    }

    // Check if soul mode is enabled
    @LuaFunction
    public final Object isSoulMode(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isSoulThruster);
        }
        return resolveThrusters(thrusterId).get(0).isSoulThruster();
    }

    // Set the soul mode
    @LuaFunction
    public final void setSoulMode(String thrusterId, boolean enabled) throws LuaException {
        applyToThrusters(thrusterId, thruster -> {
            if (enabled) {
                thruster.enableSoulThruster();
            } else {
                thruster.disableSoulThruster();
            }
        });
    }

    // Clear the throttle override
    @LuaFunction
    public final void clearThrottleOverride(String thrusterId) throws LuaException {
        applyToThrusters(thrusterId, ThrusterBlockEntity::clearCcThrottleOverride);
    }

    // Get the redstone signal
    @LuaFunction
    public final Object getRedstoneSignal(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getSignalStrength);
        }
        return resolveThrusters(thrusterId).get(0).getSignalStrength();
    }

    // Get the thruster status
    @LuaFunction
    public final Object getThrusterStatus(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(thruster -> buildThrusterStatus(null, thruster));
        }
        ThrusterBlockEntity thruster = resolveThrusters(thrusterId).get(0);
        return buildThrusterStatus(thrusterId, thruster);
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("facing", getFacing());
        status.put("worldFacing", getWorldFacing());
        status.put("forwardSignal", blockEntity.getForwardSignal());
        status.put("backwardSignal", blockEntity.getBackwardSignal());
        status.put("controlMode", blockEntity.getControlMode().name().toLowerCase());
        status.put("servoInputAngle", blockEntity.getServoInputAngleDegrees());
        status.put("pivotAngle", blockEntity.getCurrentPivotAngleDegrees());
        status.put("currentAngle", blockEntity.getCurrentPivotAngleDegrees());
        status.put("targetAngle", blockEntity.getTargetAngleDegrees());
        status.put("minAngle", blockEntity.getMinAngleDegrees());
        status.put("maxAngle", blockEntity.getMaxAngleDegrees());
        status.put("totalRealThrust", blockEntity.getAssemblyRealThrust());
        status.put("totalLiftCapacity", blockEntity.getAssemblyLiftCapacity());
        status.put("thrusterCount", blockEntity.getAttachedThrustersById().size());
        status.put("thrusters", listThrusters());
        return status;
    }

    // Get the facing
    @LuaFunction
    public final String getFacing() {
        return blockEntity.getFacing().name().toLowerCase();
    }

    // Get the world facing
    @LuaFunction
    public final Map<String, Object> getWorldFacing() {
        return ComputerCraftPositionHelper.worldDirection(blockEntity, blockEntity.getFacing());
    }

    // Set the facing
    @LuaFunction
    public final void setFacing(String direction) throws LuaException {
        Direction dir;
        try {
            dir = Direction.valueOf(direction.trim().toUpperCase());
        } catch (Exception e) {
            throw new LuaException("direction must be one of north/south/east/west/up/down");
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        var state = blockEntity.getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            level.setBlock(blockEntity.getBlockPos(), state.setValue(BlockStateProperties.FACING, dir), 3);
        }
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getForwardSignal(): number",
                "getBackwardSignal(): number",
                "getBearingControlMode(): string",
                "getServoInputAngle(): number",
                "getPivotAngle(): number",
                "getCurrentAngle(): number",
                "getTargetAngle(): number",
                "getMinAngle(): number",
                "getMaxAngle(): number",
                "setBearingControlMode(mode: 'auto'|'redstone'|'computer'|'servo')",
                "setPivotAngle(angleDeg: number)",
                "setMinAngle(angleDeg: number)",
                "setMaxAngle(angleDeg: number)",
                "clearPivotOverride()",
                "listThrusters(): table",
                "getThrusterCount(): number",
                "getOwnedThrusters(): string[]",
                "ids(): string[]",
                "getNetworkInfo(): table",
                "thrusterAlias(id|alias, alias: string)",
                "setThrottle(id|'all', throttle: number 0..1 or 0..100)",
                "getThrottle(id|'all'): number|table",
                "getThrottleMap(id|'all'): table",
                "setEnabled(id|'all', enabled: boolean)",
                "isEnabled(id|'all'): boolean|table",
                "getFuel(id|'all'): number|table",
                "getFuelCapacity(id|'all'): number|table",
                "getFuelType(id|'all'): string|table",
                "getBurnTimeSeconds(id|'all'): number|table",
                "getControlMode(id|'all'): string|table",
                "setControlMode(id|'all', mode: 'auto'|'redstone'|'computer')",
                "getThrust(id|'all'): number|table",
                "getRealThrust(id|'all'): number|table",
                "getLiftCapacity(id|'all'): number|table",
                "getTotalRealThrust(): number",
                "getTotalLiftCapacity(): number",
                "getAirflow(id|'all'): number|table",
                "isActive(id|'all'): boolean|table",
                "isSoulMode(id|'all'): boolean|table",
                "setSoulMode(id|'all', enabled: boolean)",
                "clearThrottleOverride(id|'all')",
                "getRedstoneSignal(id|'all'): number|table",
                "getThrusterStatus(id|'all'): table",
                "getFacing(): string",
                "getWorldFacing(): table",
                "setFacing(direction: string)",
                "getStatus(): table",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = Map.ofEntries(
                Map.entry("getForwardSignal", "getForwardSignal() -> redstone signal on facing side"),
                Map.entry("getBackwardSignal", "getBackwardSignal() -> redstone signal on opposite side"),
                Map.entry("getPivotAngle", "getPivotAngle() -> current physical pivot angle in degrees"),
                Map.entry("getCurrentAngle", "getCurrentAngle() -> current physical pivot angle in degrees"),
                Map.entry("getTargetAngle", "getTargetAngle() -> requested pivot target in degrees"),
                Map.entry("getMinAngle", "getMinAngle() -> current minimum allowed pivot angle in degrees"),
                Map.entry("getMaxAngle", "getMaxAngle() -> current maximum allowed pivot angle in degrees"),
                Map.entry("setPivotAngle", "setPivotAngle(angleDeg) -> enable computer control and set pivot angle"),
                Map.entry("setMinAngle", "setMinAngle(angleDeg) -> set the minimum allowed pivot angle in degrees"),
                Map.entry("setMaxAngle", "setMaxAngle(angleDeg) -> set the maximum allowed pivot angle in degrees"),
                Map.entry("clearPivotOverride", "clearPivotOverride() -> return to redstone control"),
                Map.entry("listThrusters", "listThrusters() -> table of attached thruster ids and quick telemetry"),
                Map.entry("getThrusterCount", "getThrusterCount() -> number of thrusters currently attached to this bearing"),
                Map.entry("getOwnedThrusters", "getOwnedThrusters() -> list of thruster ids currently owned by this bearing"),
                Map.entry("ids", "ids() -> list of thruster aliases for the thrusters currently attached to this bearing"),
                Map.entry("getNetworkInfo", "getNetworkInfo() -> table containing owned thrusters, aliases, and quick telemetry"),
                Map.entry("thrusterAlias", "thrusterAlias(id|alias, alias) -> assign or clear a persistent alias for a thruster"),
                Map.entry("setThrottle", "setThrottle(id|'all', throttle) -> set thruster throttle; accepts 0..1 or 0..100"),
                Map.entry("getThrottle", "getThrottle(id|'all') -> throttle for one thruster or id->value table"),
                Map.entry("getThrottleMap", "getThrottleMap(id|'all') -> always returns a table of thruster id -> throttle"),
                Map.entry("setEnabled", "setEnabled(id|'all', enabled) -> enable or disable attached thrusters"),
                Map.entry("isEnabled", "isEnabled(id|'all') -> enabled state for one thruster or id->value table"),
                Map.entry("getFuel", "getFuel(id|'all') -> current fuel amount for one thruster or id->value table"),
                Map.entry("getFuelCapacity", "getFuelCapacity(id|'all') -> fuel capacity for one thruster or id->value table"),
                Map.entry("getFuelType", "getFuelType(id|'all') -> active fuel id for one thruster or id->value table"),
                Map.entry("getBurnTimeSeconds", "getBurnTimeSeconds(id|'all') -> remaining burn time for one thruster or id->value table"),
                Map.entry("getControlMode", "getControlMode(id|'all') -> control mode for one thruster or id->value table"),
                Map.entry("setControlMode", "setControlMode(id|'all', mode) -> set auto/redstone/computer control mode"),
                Map.entry("getThrust", "getThrust(id|'all') -> thrust for one thruster or id->value table"),
                Map.entry("getRealThrust", "getRealThrust(id|'all') -> scaled real thrust for one thruster or id->value table"),
                Map.entry("getLiftCapacity", "getLiftCapacity(id|'all') -> lift capacity for one thruster or id->value table"),
                Map.entry("getTotalRealThrust", "getTotalRealThrust() -> summed real thrust across all thrusters attached to this bearing"),
                Map.entry("getTotalLiftCapacity", "getTotalLiftCapacity() -> summed assembly lift capacity derived from total real thrust and local gravity"),
                Map.entry("getAirflow", "getAirflow(id|'all') -> airflow for one thruster or id->value table"),
                Map.entry("isActive", "isActive(id|'all') -> active state for one thruster or id->value table"),
                Map.entry("isSoulMode", "isSoulMode(id|'all') -> soul mode state for one thruster or id->value table"),
                Map.entry("setSoulMode", "setSoulMode(id|'all', enabled) -> switch normal/soul mode"),
                Map.entry("clearThrottleOverride", "clearThrottleOverride(id|'all') -> return thruster control to redstone"),
                Map.entry("getRedstoneSignal", "getRedstoneSignal(id|'all') -> redstone strength for one thruster or id->value table"),
                Map.entry("getThrusterStatus", "getThrusterStatus(id|'all') -> full telemetry table for one thruster or all"),
                Map.entry("getFacing", "getFacing() -> current local block facing"),
                Map.entry("getWorldFacing", "getWorldFacing() -> projected world-space facing vector"),
                Map.entry("setFacing", "setFacing(direction) -> rotate block facing"),
                Map.entry("getStatus", "getStatus() -> table with bearing telemetry plus attached thruster ids"),
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
