package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

// Expose bearing pose and attached-thruster control through a stable ComputerCraft API
@PeripheralTypeDoc("thruster_bearing")
public class ThrusterBearingPeripheral extends GadgetsPeripheral<ThrusterBearingBlockEntity> {
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

    // Initialize the thruster bearing peripheral
    public ThrusterBearingPeripheral(ThrusterBearingBlockEntity blockEntity) {
        super(blockEntity, "thruster_bearing");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the thrusters
    private Map<String, ThrusterBlockEntity> getThrusters() {
        return blockEntity.getAttachedThrustersById();
    }

    // Get the RCS thrusters
    private Map<String, RcsThrusterBlockEntity> getRcsThrusters() {
        return blockEntity.getAttachedRcsThrustersById();
    }

    // Resolve one RCS thruster by id or alias
    private RcsThrusterBlockEntity resolveRcsThruster(String idOrAlias) throws LuaException {
        Map<String, RcsThrusterBlockEntity> thrusters = getRcsThrusters();
        RcsThrusterBlockEntity direct = thrusters.get(idOrAlias);
        if (direct != null) return direct;
        String normalized = idOrAlias == null ? "" : idOrAlias.trim();
        for (RcsThrusterBlockEntity thruster : thrusters.values()) {
            if (normalized.equals(blockEntity.getRcsThrusterAlias(thruster))) return thruster;
        }
        throw new LuaException("unknown RCS thruster id or alias '" + idOrAlias + "'");
    }

    // Parse an RCS nozzle name
    private static Direction parseRcsNozzle(String nozzle) throws LuaException {
        String normalized = nozzle == null ? "" : nozzle.trim().toLowerCase(java.util.Locale.ROOT);
        Direction direction = RcsThrusterBlockEntity.nozzleFromChannel(normalized);
        if (direction == null || !normalized.equals(direction.getSerializedName())) {
            throw new LuaException("nozzle must be 'north', 'east', 'south' or 'west'");
        }
        return direction;
    }

    // Build one RCS thruster status table
    private Map<String, Object> buildRcsThrusterStatus(String id, RcsThrusterBlockEntity thruster) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", id);
        status.put("alias", blockEntity.getRcsThrusterAlias(thruster));
        status.put("rpm", thruster.getSpeed());
        status.put("maxThrust", thruster.getMaxNozzleThrust());
        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            status.put(nozzle.getSerializedName(), Map.of(
                    "throttle", thruster.getThrottle(nozzle),
                    "thrust", thruster.getNozzleThrust(nozzle),
                    "active", thruster.isNozzleActive(nozzle)));
        }
        return status;
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the forward signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getForwardSignal", signature = "getForwardSignal(): number",
            description = "Returns the forward signal.")
    public final int getForwardSignal() {
        return blockEntity.getForwardSignal();
    }

    // Get the backward signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBackwardSignal", signature = "getBackwardSignal(): number",
            description = "Returns the backward signal.")
    public final int getBackwardSignal() {
        return blockEntity.getBackwardSignal();
    }

    // Get the pivot angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getPivotAngle", signature = "getPivotAngle(): number",
            description = "Returns the pivot angle.")
    public final double getPivotAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the current angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the target angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetAngle", signature = "getTargetAngle(): number",
            description = "Returns the target angle.")
    public final double getTargetAngle() {
        return blockEntity.getTargetAngleDegrees();
    }

    // Get the bearing control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBearingControlMode", signature = "getBearingControlMode(): string",
            description = "Returns the bearing control mode.")
    public final String getBearingControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Get the servo input angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getServoInputAngle", signature = "getServoInputAngle(): number",
            description = "Returns the servo input angle.")
    public final double getServoInputAngle() {
        return blockEntity.getServoInputAngleDegrees();
    }

    // Get the min angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMinAngle", signature = "getMinAngle(): number",
            description = "Returns the min angle.")
    public final double getMinAngle() {
        return blockEntity.getMinAngleDegrees();
    }

    // Get the max angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxAngle", signature = "getMaxAngle(): number",
            description = "Returns the max angle.")
    public final double getMaxAngle() {
        return blockEntity.getMaxAngleDegrees();
    }

    // Set the pivot angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setPivotAngle", signature = "setPivotAngle(angleDeg: number)",
            description = "Sets the pivot angle.")
    public final void setPivotAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setPivotAngleDegrees(angleDeg);
    }

    // Set the bearing control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setBearingControlMode", signature = "setBearingControlMode(mode: 'auto'|'redstone'|'computer'|'servo')",
            description = "Sets the bearing control mode.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMinAngle", signature = "setMinAngle(angleDeg: number)",
            description = "Sets the min angle.")
    public final void setMinAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMinAngleDegrees(angleDeg);
    }

    // Set the max angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMaxAngle", signature = "setMaxAngle(angleDeg: number)",
            description = "Sets the max angle.")
    public final void setMaxAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMaxAngleDegrees(angleDeg);
    }

    // Clear the pivot override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearPivotOverride", signature = "clearPivotOverride()",
            description = "Clears the pivot override.")
    public final void clearPivotOverride() {
        blockEntity.clearPivotOverride();
    }

    // Get the list thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listThrusters", signature = "listThrusters(): table",
            description = "Returns the list thrusters.")
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

    // Get the list of RCS thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listRcsThrusters", signature = "listRcsThrusters(): table",
            description = "Returns the RCS thrusters attached to the bearing head.")
    public final Map<String, Object> listRcsThrusters() {
        Map<String, Object> thrusters = new LinkedHashMap<>();
        for (Map.Entry<String, RcsThrusterBlockEntity> entry : getRcsThrusters().entrySet()) {
            RcsThrusterBlockEntity thruster = entry.getValue();
            Map<String, Object> pos = ComputerCraftPositionHelper.blockPosition(thruster);
            Map<String, Object> data = buildRcsThrusterStatus(entry.getKey(), thruster);
            data.put("pos", List.of(pos.get("x"), pos.get("y"), pos.get("z")));
            data.put("position", pos);
            data.put("localPos", List.of(thruster.getBlockPos().getX(),
                    thruster.getBlockPos().getY(), thruster.getBlockPos().getZ()));
            thrusters.put(entry.getKey(), data);
        }
        return thrusters;
    }

    // Get the RCS thruster count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrusterCount", signature = "getRcsThrusterCount(): number",
            description = "Returns the attached RCS thruster count.")
    public final int getRcsThrusterCount() {
        return getRcsThrusters().size();
    }

    // Set one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRcsThrottle", signature = "setRcsThrottle(id|'all', nozzle, throttle: number 0..1 or 0..100)",
            description = "Sets one nozzle throttle on an attached RCS thruster.")
    public final void setRcsThrottle(String id, String nozzle, double throttle) throws LuaException {
        Direction direction = parseRcsNozzle(nozzle);
        float normalized = normalizeThrottle(throttle);
        if (id == null || id.isBlank() || "all".equalsIgnoreCase(id.trim())) {
            for (RcsThrusterBlockEntity thruster : getRcsThrusters().values()) {
                thruster.setComputerThrottle(direction, normalized);
            }
            return;
        }
        resolveRcsThruster(id).setComputerThrottle(direction, normalized);
    }

    // Get one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrottle", signature = "getRcsThrottle(id, nozzle): number",
            description = "Returns one nozzle throttle from an attached RCS thruster.")
    public final double getRcsThrottle(String id, String nozzle) throws LuaException {
        return resolveRcsThruster(id).getThrottle(parseRcsNozzle(nozzle));
    }

    // Clear one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearRcsThrottle", signature = "clearRcsThrottle(id|'all', nozzle)",
            description = "Clears one nozzle throttle on an attached RCS thruster.")
    public final void clearRcsThrottle(String id, String nozzle) throws LuaException {
        Direction direction = parseRcsNozzle(nozzle);
        if (id == null || id.isBlank() || "all".equalsIgnoreCase(id.trim())) {
            for (RcsThrusterBlockEntity thruster : getRcsThrusters().values()) {
                thruster.clearComputerThrottle(direction);
            }
            return;
        }
        resolveRcsThruster(id).clearComputerThrottle(direction);
    }

    // Get one RCS thruster status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrusterStatus", signature = "getRcsThrusterStatus(id): table",
            description = "Returns the status of one attached RCS thruster.")
    public final Map<String, Object> getRcsThrusterStatus(String id) throws LuaException {
        RcsThrusterBlockEntity thruster = resolveRcsThruster(id);
        return buildRcsThrusterStatus(id, thruster);
    }

    // Get the thruster count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrusterCount", signature = "getThrusterCount(): number",
            description = "Returns the thruster count.")
    public final int getThrusterCount() {
        return getThrusters().size();
    }

    // Get the owned thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getOwnedThrusters", signature = "getOwnedThrusters(): string[]",
            description = "Returns the owned thrusters.")
    public final List<String> getOwnedThrusters() {
        return List.copyOf(getThrusters().keySet());
    }

    // Get the ids
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "ids", signature = "ids(): string[]",
            description = "Returns the ids.")
    public final List<String> ids() {
        return getThrusters().values().stream()
                .map(blockEntity::getThrusterAlias)
                .filter(alias -> alias != null && !alias.isBlank())
                .toList();
    }

    // Get the network info
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNetworkInfo", signature = "getNetworkInfo(): table",
            description = "Returns the network info.")
    public final Map<String, Object> getNetworkInfo() {
        Map<String, ThrusterBlockEntity> thrusters = getThrusters();
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : thrusters.entrySet()) {
            aliases.put(entry.getKey(), blockEntity.getThrusterAlias(entry.getValue()));
        }

        return Map.of(
                "bearingType", getType(),
                "thrusterCount", thrusters.size(),
                "rcsThrusterCount", getRcsThrusters().size(),
                "ownedThrusters", List.copyOf(thrusters.keySet()),
                "aliases", aliases,
                "thrusters", listThrusters(),
                "rcsThrusters", listRcsThrusters()
        );
    }

    // Handle the thruster alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "thrusterAlias", signature = "thrusterAlias(id|alias, alias: string)",
            description = "Handle the thruster alias.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(id|'all', throttle: number 0..1 or 0..100)",
            description = "Sets the throttle.")
    public final void setThrottle(String thrusterId, double throttle) throws LuaException {
        float normalizedThrottle = normalizeThrottle(throttle);
        applyToThrusters(thrusterId, thruster -> thruster.setThrottle(normalizedThrottle));
    }

    // Get the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(id|'all'): number|table",
            description = "Returns the throttle.")
    public final Object getThrottle(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getThrottle);
        }
        return resolveThrusters(thrusterId).get(0).getThrottle();
    }

    // Get the throttle map
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottleMap", signature = "getThrottleMap(id|'all'): table",
            description = "Returns the throttle map.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setEnabled", signature = "setEnabled(id|'all', enabled: boolean)",
            description = "Sets the enabled.")
    public final void setEnabled(String thrusterId, boolean enabled) throws LuaException {
        applyToThrusters(thrusterId, thruster -> thruster.setEnabled(enabled));
    }

    // Check if the bearing is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isEnabled", signature = "isEnabled(id|'all'): boolean|table",
            description = "Returns whether the bearing is enabled.")
    public final Object isEnabled(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isEnabled);
        }
        return resolveThrusters(thrusterId).get(0).isEnabled();
    }

    // Get the fuel
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuel", signature = "getFuel(id|'all'): number|table",
            description = "Returns the fuel.")
    public final Object getFuel(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelAmount);
        }
        return resolveThrusters(thrusterId).get(0).getFuelAmount();
    }

    // Get the fuel capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelCapacity", signature = "getFuelCapacity(id|'all'): number|table",
            description = "Returns the fuel capacity.")
    public final Object getFuelCapacity(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getFuelCapacity();
    }

    // Get the fuel type
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelType", signature = "getFuelType(id|'all'): string|table",
            description = "Returns the fuel type.")
    public final Object getFuelType(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getFuelTypeId);
        }
        return resolveThrusters(thrusterId).get(0).getFuelTypeId();
    }

    // Get the burn time seconds
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBurnTimeSeconds", signature = "getBurnTimeSeconds(id|'all'): number|table",
            description = "Returns the burn time seconds.")
    public final Object getBurnTimeSeconds(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getEstimatedBurnSeconds);
        }
        return resolveThrusters(thrusterId).get(0).getEstimatedBurnSeconds();
    }

    // Get the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(id|'all'): string|table",
            description = "Returns the control mode.")
    public final Object getControlMode(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(thruster -> thruster.getControlMode().name().toLowerCase());
        }
        return resolveThrusters(thrusterId).get(0).getControlMode().name().toLowerCase();
    }

    // Set the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(id|'all', mode: 'auto'|'redstone'|'computer')",
            description = "Sets the control mode.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(id|'all'): number|table",
            description = "Returns the thrust.")
    public final Object getThrust(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getThrust);
        }
        return resolveThrusters(thrusterId).get(0).getThrust();
    }

    // Get the real thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRealThrust", signature = "getRealThrust(id|'all'): number|table",
            description = "Returns the real thrust.")
    public final Object getRealThrust(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getRealThrust);
        }
        return resolveThrusters(thrusterId).get(0).getRealThrust();
    }

    // Get the lift capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLiftCapacity", signature = "getLiftCapacity(id|'all'): number|table",
            description = "Returns the lift capacity.")
    public final Object getLiftCapacity(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getLiftCapacity);
        }
        return resolveThrusters(thrusterId).get(0).getLiftCapacity();
    }

    // Get the total real thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTotalRealThrust", signature = "getTotalRealThrust(): number",
            description = "Returns the total real thrust.")
    public final double getTotalRealThrust() {
        return blockEntity.getAssemblyRealThrust();
    }

    // Get the total lift capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTotalLiftCapacity", signature = "getTotalLiftCapacity(): number",
            description = "Returns the total lift capacity.")
    public final double getTotalLiftCapacity() {
        return blockEntity.getAssemblyLiftCapacity();
    }

    // Get the airflow
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAirflow", signature = "getAirflow(id|'all'): number|table",
            description = "Returns the airflow.")
    public final Object getAirflow(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getAirflow);
        }
        return resolveThrusters(thrusterId).get(0).getAirflow();
    }

    // Check if the bearing is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(id|'all'): boolean|table",
            description = "Returns whether the bearing is active.")
    public final Object isActive(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isActive);
        }
        return resolveThrusters(thrusterId).get(0).isActive();
    }

    // Check if soul mode is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isSoulMode", signature = "isSoulMode(id|'all'): boolean|table",
            description = "Returns whether soul mode is enabled.")
    public final Object isSoulMode(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::isSoulThruster);
        }
        return resolveThrusters(thrusterId).get(0).isSoulThruster();
    }

    // Set the soul mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSoulMode", signature = "setSoulMode(id|'all', enabled: boolean)",
            description = "Sets the soul mode.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearThrottleOverride", signature = "clearThrottleOverride(id|'all')",
            description = "Clears the throttle override.")
    public final void clearThrottleOverride(String thrusterId) throws LuaException {
        applyToThrusters(thrusterId, ThrusterBlockEntity::clearCcThrottleOverride);
    }

    // Get the redstone signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneSignal", signature = "getRedstoneSignal(id|'all'): number|table",
            description = "Returns the redstone signal.")
    public final Object getRedstoneSignal(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(ThrusterBlockEntity::getSignalStrength);
        }
        return resolveThrusters(thrusterId).get(0).getSignalStrength();
    }

    // Get the thruster status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrusterStatus", signature = "getThrusterStatus(id|'all'): table",
            description = "Returns the thruster status.")
    public final Object getThrusterStatus(String thrusterId) throws LuaException {
        if ("all".equalsIgnoreCase(thrusterId)) {
            return thrusterValueMap(thruster -> buildThrusterStatus(null, thruster));
        }
        ThrusterBlockEntity thruster = resolveThrusters(thrusterId).get(0);
        return buildThrusterStatus(thrusterId, thruster);
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
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
        status.put("rcsThrusterCount", blockEntity.getAttachedRcsThrustersById().size());
        status.put("rcsThrusters", listRcsThrusters());
        return status;
    }

    // Get the facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() {
        return blockEntity.getFacing().name().toLowerCase();
    }

    // Get the world facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() {
        return ComputerCraftPositionHelper.worldDirection(blockEntity, blockEntity.getFacing());
    }

    // Set the facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setFacing", signature = "setFacing(direction: string)",
            description = "Sets the facing.")
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
}
