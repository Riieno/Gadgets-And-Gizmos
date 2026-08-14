package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Solve bounded propulsion commands which best match the SCM's requested force and torque
public final class ShipControlAllocator {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int AXES = 6;
    private static final int YAW_TORQUE_AXIS = 4;
    private static final int ITERATIONS = 96;
    private static final double REGULARIZATION = 0.0025D;
    private static final double TRANSLATION_PRIORITY_SCALE = 8.0D;
    private static final double STABLE_UP_ALIGNMENT = 0.35D;
    private static final double STABLE_UP_RESIDUAL_LIMIT = 0.18D;
    private static final double STABLE_FORCE_ERROR_LIMIT = 0.025D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship control allocator
    private ShipControlAllocator() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Allocate the ship control allocator
    public static Allocation allocate(ShipControlMap map, Vec3 desiredForce, Vec3 desiredTorque) {
        return solve(map, desiredForce, desiredTorque, null, 1.0D);
    }

    // Allocate the translation priority
    public static Allocation allocateTranslationPriority(
            ShipControlMap map,
            Vec3 desiredForce,
            Vec3 desiredTorque
    ) {
        return solve(
                map, desiredForce, desiredTorque, null,
                TRANSLATION_PRIORITY_SCALE);
    }

    // Allocate the stable
    public static Allocation allocateStable(
            ShipControlMap map,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection
    ) {
        if (map == null || preferredDirection == null
                || preferredDirection.lengthSqr() <= 1.0E-12D) {
            return allocate(map, desiredForce, desiredTorque);
        }

        Vec3 preferred = preferredDirection.normalize();
        boolean[] preferredUnits = new boolean[map.units().size()];
        boolean hasPreferredUnit = false;
        for (int idx = 0; idx < map.units().size(); idx++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(idx);
            boolean aligned = unit.controllable() && unit.maxThrust() > 1.0E-9D
                    && unit.forceDirection().dot(preferred) >= STABLE_UP_ALIGNMENT;
            preferredUnits[idx] = aligned;
            hasPreferredUnit |= aligned;
        }
        if (!hasPreferredUnit) {
            return allocate(map, desiredForce, desiredTorque);
        }

        Allocation preferredAllocation = solve(
                map, desiredForce, desiredTorque, preferredUnits, 1.0D);
        if (preferredAllocation.residual() <= STABLE_UP_RESIDUAL_LIMIT
                && meetsForceDemand(map, preferredAllocation.controls(), desiredForce)) {
            return preferredAllocation;
        }
        return allocate(map, desiredForce, desiredTorque);
    }

    // Allocate the stable translation priority
    public static Allocation allocateStableTranslationPriority(
            ShipControlMap map,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection
    ) {
        if (map == null || preferredDirection == null
                || preferredDirection.lengthSqr() <= 1.0E-12D) {
            return allocateTranslationPriority(map, desiredForce, desiredTorque);
        }

        Vec3 preferred = preferredDirection.normalize();
        boolean[] preferredUnits = new boolean[map.units().size()];
        boolean hasPreferredUnit = false;
        for (int idx = 0; idx < map.units().size(); idx++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(idx);
            boolean aligned = unit.controllable() && unit.maxThrust() > 1.0E-9D
                    && unit.forceDirection().dot(preferred) >= STABLE_UP_ALIGNMENT;
            preferredUnits[idx] = aligned;
            hasPreferredUnit |= aligned;
        }
        if (!hasPreferredUnit) {
            return allocateTranslationPriority(map, desiredForce, desiredTorque);
        }

        Allocation preferredAllocation = solve(
                map, desiredForce, desiredTorque, preferredUnits,
                TRANSLATION_PRIORITY_SCALE);
        if (preferredAllocation.residual() <= STABLE_UP_RESIDUAL_LIMIT
                && meetsForceDemand(map, preferredAllocation.controls(), desiredForce)) {
            return preferredAllocation;
        }
        return allocateTranslationPriority(map, desiredForce, desiredTorque);
    }

    // Allocate the articulated
    public static Allocation allocateArticulated(
            ShipControlMap map,
            Map<UUID, Vec3> bodyCenters,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection,
            boolean translationPriority
    ) {
        return allocateArticulated(
                map, bodyCenters, desiredForce, desiredTorque,
                preferredDirection, translationPriority, null);
    }

    // Allocate the articulated
    public static Allocation allocateArticulated(
            ShipControlMap map,
            Map<UUID, Vec3> bodyCenters,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection,
            boolean translationPriority,
            Workspace workspace
    ) {
        if (map == null || map.units().isEmpty()) {
            return new Allocation(new double[map == null ? 0 : map.units().size()], 1.0D);
        }
        Workspace activeWorkspace = workspace == null ? new Workspace() : workspace;
        activeWorkspace.prepare(map);
        if (activeWorkspace.bodyIds.length == 1
                && map.rootSubLevelId().equals(activeWorkspace.bodyIds[0])) {
            Vec3 center = bodyCenters == null
                    ? map.centerOfMass()
                    : bodyCenters.getOrDefault(activeWorkspace.bodyIds[0], map.centerOfMass());
            return allocateRequestedSubset(
                    map, activeWorkspace.unitGroups[0], center,
                    desiredForce, desiredTorque, preferredDirection, translationPriority);
        }

        Map<UUID, Vec3> centers = bodyCenters == null ? Map.of() : bodyCenters;
        Vec3 requestedTorque = finiteVector(desiredTorque);
        List<CarriageDemand> demands = new ArrayList<>(activeWorkspace.bodyIds.length);
        for (UUID bodyId : activeWorkspace.bodyIds) {
            boolean yawAuthority = map.rootSubLevelId().equals(bodyId);
            Vec3 bodyTorque = yawAuthority
                    ? requestedTorque
                    : new Vec3(requestedTorque.x, 0.0D, requestedTorque.z);
            demands.add(new CarriageDemand(
                    bodyId, Set.of(bodyId),
                    centers.getOrDefault(bodyId, map.centerOfMass()),
                    1.0D, bodyTorque, yawAuthority));
        }
        return allocateArticulated(
                map, demands, desiredForce, preferredDirection,
                translationPriority, activeWorkspace);
    }

    // Allocate the articulated
    public static Allocation allocateArticulated(
            ShipControlMap map,
            Collection<CarriageDemand> carriageDemands,
            Vec3 desiredForce,
            Vec3 preferredDirection,
            boolean translationPriority
    ) {
        return allocateArticulated(
                map, carriageDemands, desiredForce, preferredDirection,
                translationPriority, null);
    }

    // Split movement using each carriage's real mass, thrust and centre
    public static Allocation allocateArticulated(
            ShipControlMap map,
            Collection<CarriageDemand> carriageDemands,
            Vec3 desiredForce,
            Vec3 preferredDirection,
            boolean translationPriority,
            Workspace workspace
    ) {
        if (map == null || map.units().isEmpty()) {
            return new Allocation(new double[map == null ? 0 : map.units().size()], 1.0D);
        }
        if (workspace != null) {
            workspace.prepare(map);
        }

        List<ResolvedCarriage> carriages = resolveCarriages(map, carriageDemands);
        if (carriages.isEmpty()) {
            return allocateRequested(
                    map, desiredForce, Vec3.ZERO,
                    preferredDirection, translationPriority);
        }

        Vec3[] forceDemands = massWeightedForceDemands(
                map, carriages, finiteVector(desiredForce));
        double[] controls = new double[map.units().size()];
        double residual = 0.0D;
        for (int carriageIndex = 0; carriageIndex < carriages.size(); carriageIndex++) {
            ResolvedCarriage carriage = carriages.get(carriageIndex);
            CarriageDemand demand = carriage.demand();
            Vec3 torque = finiteVector(demand.desiredTorque());
            if (!demand.yawAuthority()) {
                torque = new Vec3(torque.x, 0.0D, torque.z);
            }
            int[] carriageUnitIndices = carriage.unitIndices();
            Allocation carriageAllocation = allocateRequestedSubset(
                    map, carriageUnitIndices, demand.centerOfMass(),
                    forceDemands[carriageIndex], torque,
                    preferredDirection, translationPriority,
                    demand.yawAuthority());
            double[] carriageControls = carriageAllocation.controls();
            for (int localIndex = 0;
                 localIndex < carriageUnitIndices.length && localIndex < carriageControls.length;
                 localIndex++) {
                controls[carriageUnitIndices[localIndex]] = carriageControls[localIndex];
            }
            residual = Math.max(residual, carriageAllocation.residual());
        }
        return new Allocation(controls, residual);
    }

    // Resolve the carriages
    private static List<ResolvedCarriage> resolveCarriages(
            ShipControlMap map,
            Collection<CarriageDemand> carriageDemands
    ) {
        if (carriageDemands == null || carriageDemands.isEmpty()) {
            return List.of();
        }
        List<CarriageDemand> ordered = carriageDemands.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing((CarriageDemand demand) ->
                                !demand.bodyIds().contains(map.rootSubLevelId()))
                        .thenComparing(demand -> demand.carriageId().toString()))
                .toList();
        if (ordered.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> carriageByBody = new LinkedHashMap<>();
        List<List<Integer>> unitsByCarriage = new ArrayList<>(ordered.size());
        for (int idx = 0; idx < ordered.size(); idx++) {
            unitsByCarriage.add(new ArrayList<>());
            for (UUID bodyId : ordered.get(idx).bodyIds()) {
                carriageByBody.putIfAbsent(bodyId, idx);
            }
        }
        int fallbackCarriage = 0;
        Integer primary = carriageByBody.get(map.rootSubLevelId());
        if (primary != null) {
            fallbackCarriage = primary;
        }
        for (int unitIndex = 0; unitIndex < map.units().size(); unitIndex++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(unitIndex);
            int carriageIndex = carriageByBody.getOrDefault(
                    unit.subLevelId(), fallbackCarriage);
            unitsByCarriage.get(carriageIndex).add(unitIndex);
        }

        List<ResolvedCarriage> resolved = new ArrayList<>(ordered.size());
        for (int idx = 0; idx < ordered.size(); idx++) {
            int[] unitIndices = unitsByCarriage.get(idx).stream()
                    .mapToInt(Integer::intValue).toArray();
            resolved.add(new ResolvedCarriage(ordered.get(idx), unitIndices));
        }
        return List.copyOf(resolved);
    }

    // Get the mass weighted force demands
    private static Vec3[] massWeightedForceDemands(
            ShipControlMap map,
            List<ResolvedCarriage> carriages,
            Vec3 desiredForce
    ) {
        double[][] normalized = new double[carriages.size()][3];
        double[] requested = {
                clamp(desiredForce.x), clamp(desiredForce.y), clamp(desiredForce.z)
        };
        for (int axis = 0; axis < requested.length; axis++) {
            double sign = Math.signum(requested[axis]);
            if (sign == 0.0D) {
                continue;
            }
            double[] capacities = new double[carriages.size()];
            double totalCapacity = 0.0D;
            for (int carriageIndex = 0;
                 carriageIndex < carriages.size(); carriageIndex++) {
                capacities[carriageIndex] = directionalCapacity(
                        map, carriages.get(carriageIndex).unitIndices(), axis, sign);
                totalCapacity += capacities[carriageIndex];
            }
            if (totalCapacity <= 1.0E-9D) {
                continue;
            }
            double requestedPhysical = Math.abs(requested[axis]) * totalCapacity;
            double[] assigned = massWeightedCapacityShare(
                    carriages, capacities, requestedPhysical);
            for (int carriageIndex = 0;
                 carriageIndex < carriages.size(); carriageIndex++) {
                if (capacities[carriageIndex] > 1.0E-9D) {
                    normalized[carriageIndex][axis] = sign * Mth.clamp(
                            assigned[carriageIndex] / capacities[carriageIndex],
                            0.0D, 1.0D);
                }
            }
        }

        Vec3[] res = new Vec3[carriages.size()];
        for (int idx = 0; idx < res.length; idx++) {
            res[idx] = new Vec3(
                    normalized[idx][0], normalized[idx][1], normalized[idx][2]);
        }
        return res;
    }

    // Get the directional capacity
    private static double directionalCapacity(
            ShipControlMap map,
            int[] unitIndices,
            int axis,
            double sign
    ) {
        double capacity = 0.0D;
        for (int unitIndex : unitIndices) {
            if (unitIndex < 0 || unitIndex >= map.units().size()) {
                continue;
            }
            ShipControlMap.PropulsionUnit unit = map.units().get(unitIndex);
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            double component = switch (axis) {
                case 0 -> force.x;
                case 1 -> force.y;
                default -> force.z;
            };
            capacity += directionalCapacity(component, sign);
        }
        return capacity;
    }

    // Get the mass weighted capacity share
    private static double[] massWeightedCapacityShare(
            List<ResolvedCarriage> carriages,
            double[] capacities,
            double requestedPhysical
    ) {
        double[] assigned = new double[carriages.size()];
        boolean[] active = new boolean[carriages.size()];
        double remaining = Math.max(0.0D, requestedPhysical);
        for (int idx = 0; idx < active.length; idx++) {
            active[idx] = capacities[idx] > 1.0E-9D;
        }
        while (remaining > 1.0E-9D) {
            double totalWeight = 0.0D;
            int activeCount = 0;
            for (int idx = 0; idx < active.length; idx++) {
                if (active[idx]) {
                    totalWeight += effectiveMass(carriages.get(idx).demand());
                    activeCount++;
                }
            }
            if (activeCount == 0) {
                break;
            }

            List<Integer> saturated = new ArrayList<>();
            for (int idx = 0; idx < active.length; idx++) {
                if (!active[idx]) {
                    continue;
                }
                double weight = effectiveMass(carriages.get(idx).demand());
                double share = remaining * (totalWeight <= 1.0E-9D
                        ? 1.0D / activeCount : weight / totalWeight);
                if (share >= capacities[idx] - assigned[idx] - 1.0E-9D) {
                    saturated.add(idx);
                }
            }
            if (saturated.isEmpty()) {
                for (int idx = 0; idx < active.length; idx++) {
                    if (!active[idx]) {
                        continue;
                    }
                    double weight = effectiveMass(carriages.get(idx).demand());
                    assigned[idx] += remaining * (totalWeight <= 1.0E-9D
                            ? 1.0D / activeCount : weight / totalWeight);
                }
                break;
            }
            for (int idx : saturated) {
                double available = Math.max(0.0D, capacities[idx] - assigned[idx]);
                assigned[idx] += available;
                remaining = Math.max(0.0D, remaining - available);
                active[idx] = false;
            }
        }
        return assigned;
    }

    // Get the effective mass
    private static double effectiveMass(CarriageDemand demand) {
        return demand != null && Double.isFinite(demand.mass()) && demand.mass() > 1.0E-9D
                ? demand.mass() : 1.0D;
    }

    // Allocate the requested subset
    private static Allocation allocateRequestedSubset(
            ShipControlMap map,
            int[] unitIndices,
            Vec3 centerOfMass,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection,
            boolean translationPriority
    ) {
        return allocateRequestedSubset(
                map, unitIndices, centerOfMass, desiredForce, desiredTorque,
                preferredDirection, translationPriority, true);
    }

    // Allocate the requested subset
    private static Allocation allocateRequestedSubset(
            ShipControlMap map,
            int[] unitIndices,
            Vec3 centerOfMass,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection,
            boolean translationPriority,
            boolean yawTorqueEnabled
    ) {
        boolean stable = preferredDirection != null
                && preferredDirection.lengthSqr() > 1.0E-12D;
        double translationScale = translationPriority
                ? TRANSLATION_PRIORITY_SCALE : 1.0D;
        if (!stable) {
            return solveSubset(
                    map, unitIndices, centerOfMass, desiredForce, desiredTorque,
                    null, translationScale, yawTorqueEnabled);
        }

        Vec3 preferred = preferredDirection.normalize();
        boolean[] preferredUnits = new boolean[unitIndices.length];
        boolean hasPreferredUnit = false;
        for (int localIndex = 0; localIndex < unitIndices.length; localIndex++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(unitIndices[localIndex]);
            boolean aligned = unit.controllable() && unit.maxThrust() > 1.0E-9D
                    && unit.forceDirection().dot(preferred) >= STABLE_UP_ALIGNMENT;
            preferredUnits[localIndex] = aligned;
            hasPreferredUnit |= aligned;
        }
        if (!hasPreferredUnit) {
            return solveSubset(
                    map, unitIndices, centerOfMass, desiredForce, desiredTorque,
                    null, translationScale, yawTorqueEnabled);
        }

        Allocation preferredAllocation = solveSubset(
                map, unitIndices, centerOfMass, desiredForce, desiredTorque,
                preferredUnits, translationScale, yawTorqueEnabled);
        if (preferredAllocation.residual() <= STABLE_UP_RESIDUAL_LIMIT
                && meetsForceDemandSubset(
                map, unitIndices, preferredAllocation.controls(), desiredForce)) {
            return preferredAllocation;
        }
        return solveSubset(
                map, unitIndices, centerOfMass, desiredForce, desiredTorque,
                null, translationScale, yawTorqueEnabled);
    }

    // Check if the control subset meets the force demand
    private static boolean meetsForceDemandSubset(
            ShipControlMap map,
            int[] unitIndices,
            double[] controls,
            Vec3 desiredForce
    ) {
        double[] target = {
                clamp(desiredForce.x), clamp(desiredForce.y), clamp(desiredForce.z)
        };
        double[] capacity = new double[3];
        double[] achieved = new double[3];
        for (int localIndex = 0; localIndex < unitIndices.length; localIndex++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(unitIndices[localIndex]);
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            double control = localIndex < controls.length ? controls[localIndex] : 0.0D;
            capacity[0] += normalizationCapacity(force.x, target[0]);
            capacity[1] += normalizationCapacity(force.y, target[1]);
            capacity[2] += normalizationCapacity(force.z, target[2]);
            achieved[0] += force.x * control;
            achieved[1] += force.y * control;
            achieved[2] += force.z * control;
        }
        for (int axis = 0; axis < target.length; axis++) {
            if (Math.abs(target[axis]) <= 1.0E-9D) {
                continue;
            }
            if (capacity[axis] <= 1.0E-9D
                    || Math.abs(achieved[axis] / capacity[axis] - target[axis])
                    > STABLE_FORCE_ERROR_LIMIT) {
                return false;
            }
        }
        return true;
    }

    // Get the solve subset
    private static Allocation solveSubset(
            ShipControlMap map,
            int[] unitIndices,
            Vec3 centerOfMass,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            boolean[] enabledUnits,
            double translationScale,
            boolean yawTorqueEnabled
    ) {
        double[] controls = new double[unitIndices.length];
        double[][] allColumns = new double[unitIndices.length][AXES];
        double[][] columns = new double[unitIndices.length][AXES];
        for (int localIndex = 0; localIndex < unitIndices.length; localIndex++) {
            int unitIndex = unitIndices[localIndex];
            ShipControlMap.PropulsionUnit unit = map.units().get(unitIndex);
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D
                    || unit.forceDirection().lengthSqr() <= 1.0E-12D) {
                controls[localIndex] = 0.0D;
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            Vec3 torque = unit.rootPosition().subtract(centerOfMass).cross(force);
            double[] column = allColumns[localIndex];
            column[0] = force.x;
            column[1] = force.y;
            column[2] = force.z;
            column[3] = torque.x;
            column[4] = torque.y;
            column[5] = torque.z;
            if (enabledUnits == null
                    || localIndex < enabledUnits.length && enabledUnits[localIndex]) {
                System.arraycopy(column, 0, columns[localIndex], 0, AXES);
            } else {
                controls[localIndex] = 0.0D;
            }
        }

        double[] target = {
                clamp(desiredForce.x), clamp(desiredForce.y), clamp(desiredForce.z),
                clamp(desiredTorque.x), clamp(desiredTorque.y), clamp(desiredTorque.z)
        };
        if (!yawTorqueEnabled) {
            target[YAW_TORQUE_AXIS] = 0.0D;
            for (double[] column : allColumns) {
                column[YAW_TORQUE_AXIS] = 0.0D;
            }
            for (double[] column : columns) {
                column[YAW_TORQUE_AXIS] = 0.0D;
            }
        }
        normalizeColumns(allColumns, columns, target, translationScale);
        solveControls(columns, controls, target);
        return allocationResult(columns, controls, target);
    }

    // Normalize the columns
    private static void normalizeColumns(
            double[][] allColumns,
            double[][] columns,
            double[] target,
            double translationScale
    ) {
        for (int axis = 0; axis < AXES; axis++) {
            double capacity = 0.0D;
            for (double[] column : allColumns) {
                capacity += normalizationCapacity(column[axis], target[axis]);
            }
            if (capacity <= 1.0E-9D) {
                for (double[] column : allColumns) {
                    capacity += Math.abs(column[axis]);
                }
                if (capacity <= 1.0E-9D) {
                    target[axis] = 0.0D;
                    continue;
                }
            }
            for (double[] column : columns) {
                column[axis] /= capacity;
            }
        }
        double forceScale = Math.max(1.0D, translationScale);
        if (forceScale <= 1.0D) {
            return;
        }
        for (int axis = 0; axis < 3; axis++) {
            target[axis] *= forceScale;
            for (double[] column : columns) {
                column[axis] *= forceScale;
            }
        }
    }

    // Solve the controls
    private static void solveControls(
            double[][] columns,
            double[] controls,
            double[] target
    ) {
        double spectralBound = REGULARIZATION;
        for (double[] column : columns) {
            for (double val : column) {
                spectralBound += val * val;
            }
        }
        double step = 0.85D / Math.max(0.01D, spectralBound);
        double[] achieved = new double[AXES];
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            multiply(columns, controls, achieved);
            for (int idx = 0; idx < controls.length; idx++) {
                double gradient = REGULARIZATION * controls[idx];
                for (int axis = 0; axis < AXES; axis++) {
                    gradient += columns[idx][axis] * (achieved[axis] - target[axis]);
                }
                controls[idx] = Mth.clamp(
                        controls[idx] - step * gradient, 0.0D, 1.0D);
            }
        }
    }

    // Get the allocation result
    private static Allocation allocationResult(
            double[][] columns,
            double[] controls,
            double[] target
    ) {
        double[] achieved = new double[AXES];
        multiply(columns, controls, achieved);
        double residual = 0.0D;
        double targetEnergy = 0.0D;
        for (int axis = 0; axis < AXES; axis++) {
            double error = achieved[axis] - target[axis];
            residual += error * error;
            targetEnergy += target[axis] * target[axis];
        }
        return new Allocation(controls, Math.sqrt(residual / Math.max(1.0D, targetEnergy)));
    }

    // Hold the control allocation workspace
    public static final class Workspace {
        // Current map id
        private UUID mapId;
        // Current root sub-level id
        private UUID rootSubLevelId;
        // Current unit ids
        private int[] unitIds = new int[0];
        // Current unit body ids
        private UUID[] unitBodyIds = new UUID[0];
        // Current body ids
        private UUID[] bodyIds = new UUID[0];
        // Current unit groups
        private int[][] unitGroups = new int[0][];

        // Check if this requires body centers
        public boolean requiresBodyCenters(ShipControlMap map) {
            if (map == null || map.units().isEmpty()) {
                return false;
            }
            prepare(map);
            return bodyIds.length != 1
                    || !map.rootSubLevelId().equals(bodyIds[0]);
        }

        // Reset the workspace
        public void reset() {
            mapId = null;
            rootSubLevelId = null;
            unitIds = new int[0];
            unitBodyIds = new UUID[0];
            bodyIds = new UUID[0];
            unitGroups = new int[0][];
        }

        // Prepare the workspace
        private void prepare(ShipControlMap map) {
            if (matches(map)) {
                return;
            }
            mapId = map.id();
            rootSubLevelId = map.rootSubLevelId();
            unitIds = new int[map.units().size()];
            unitBodyIds = new UUID[map.units().size()];
            Map<UUID, List<Integer>> grouped = new LinkedHashMap<>();
            for (int idx = 0; idx < map.units().size(); idx++) {
                ShipControlMap.PropulsionUnit unit = map.units().get(idx);
                unitIds[idx] = unit.index();
                unitBodyIds[idx] = unit.subLevelId();
                grouped.computeIfAbsent(unit.subLevelId(), ignored -> new ArrayList<>())
                        .add(idx);
            }
            bodyIds = grouped.keySet().toArray(UUID[]::new);
            unitGroups = new int[bodyIds.length][];
            for (int groupIndex = 0; groupIndex < bodyIds.length; groupIndex++) {
                List<Integer> indices = grouped.get(bodyIds[groupIndex]);
                unitGroups[groupIndex] = indices.stream().mapToInt(Integer::intValue).toArray();
            }
        }

        // Check if this matches the value
        private boolean matches(ShipControlMap map) {
            if (!java.util.Objects.equals(mapId, map.id())
                    || !java.util.Objects.equals(rootSubLevelId, map.rootSubLevelId())
                    || unitIds.length != map.units().size()) {
                return false;
            }
            for (int idx = 0; idx < unitIds.length; idx++) {
                ShipControlMap.PropulsionUnit unit = map.units().get(idx);
                if (unitIds[idx] != unit.index()
                        || !java.util.Objects.equals(unitBodyIds[idx], unit.subLevelId())) {
                    return false;
                }
            }
            return true;
        }

    }

    // Allocate the requested
    private static Allocation allocateRequested(
            ShipControlMap map,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 preferredDirection,
            boolean translationPriority
    ) {
        boolean stable = preferredDirection != null
                && preferredDirection.lengthSqr() > 1.0E-12D;
        if (stable) {
            return translationPriority
                    ? allocateStableTranslationPriority(
                    map, desiredForce, desiredTorque, preferredDirection)
                    : allocateStable(map, desiredForce, desiredTorque, preferredDirection);
        }
        return translationPriority
                ? allocateTranslationPriority(map, desiredForce, desiredTorque)
                : allocate(map, desiredForce, desiredTorque);
    }

    // Check if the controls meet the force demand
    private static boolean meetsForceDemand(ShipControlMap map, double[] controls, Vec3 desiredForce) {
        double[] target = {
                clamp(desiredForce.x), clamp(desiredForce.y), clamp(desiredForce.z)
        };
        double[] capacity = new double[3];
        double[] achieved = new double[3];
        for (int idx = 0; idx < map.units().size(); idx++) {
            ShipControlMap.PropulsionUnit unit = map.units().get(idx);
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            double[] components = {force.x, force.y, force.z};
            double control = idx < controls.length ? controls[idx] : 0.0D;
            for (int axis = 0; axis < components.length; axis++) {
                capacity[axis] += normalizationCapacity(components[axis], target[axis]);
                achieved[axis] += components[axis] * control;
            }
        }
        for (int axis = 0; axis < target.length; axis++) {
            if (Math.abs(target[axis]) <= 1.0E-9D) {
                continue;
            }
            if (capacity[axis] <= 1.0E-9D
                    || Math.abs(achieved[axis] / capacity[axis] - target[axis])
                    > STABLE_FORCE_ERROR_LIMIT) {
                return false;
            }
        }
        return true;
    }

    // Get the solve
    private static Allocation solve(
            ShipControlMap map,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            boolean[] enabledUnits,
            double translationScale
    ) {
        List<ShipControlMap.PropulsionUnit> units = map == null ? List.of() : map.units();
        double[] controls = new double[units.size()];
        if (map == null || units.isEmpty()) {
            return new Allocation(controls, 1.0D);
        }

        // ------------------------------------CONTROL MATRIX------------------------------------
        double[][] allColumns = new double[units.size()][AXES];
        double[][] columns = new double[units.size()][AXES];
        for (int idx = 0; idx < units.size(); idx++) {
            ShipControlMap.PropulsionUnit unit = units.get(idx);
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D
                    || unit.forceDirection().lengthSqr() <= 1.0E-12D) {
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            Vec3 torque = unit.rootPosition().subtract(map.centerOfMass()).cross(force);
            allColumns[idx][0] = force.x;
            allColumns[idx][1] = force.y;
            allColumns[idx][2] = force.z;
            allColumns[idx][3] = torque.x;
            allColumns[idx][4] = torque.y;
            allColumns[idx][5] = torque.z;
            if (enabledUnits == null || idx < enabledUnits.length && enabledUnits[idx]) {
                System.arraycopy(allColumns[idx], 0, columns[idx], 0, AXES);
            }
        }

        // -----------------------------------------------------TARGET DEMAND-----------------------------------------------------
        double[] target = {
                clamp(desiredForce.x), clamp(desiredForce.y), clamp(desiredForce.z),
                clamp(desiredTorque.x), clamp(desiredTorque.y), clamp(desiredTorque.z)
        };
        for (int axis = 0; axis < AXES; axis++) {
            double capacity = 0.0D;
            for (double[] column : allColumns) {
                capacity += normalizationCapacity(column[axis], target[axis]);
            }
            if (capacity <= 1.0E-9D) {
                for (double[] column : allColumns) {
                    capacity += Math.abs(column[axis]);
                }
                if (capacity <= 1.0E-9D) {
                    target[axis] = 0.0D;
                    continue;
                }
            }
            for (double[] column : columns) {
                column[axis] /= capacity;
            }
        }
        double forceScale = Math.max(1.0D, translationScale);
        if (forceScale > 1.0D) {
            for (int axis = 0; axis < 3; axis++) {
                target[axis] *= forceScale;
                for (double[] column : columns) {
                    column[axis] *= forceScale;
                }
            }
        }

        // -----------------------------------------------------SOLVER STEP-----------------------------------------------------
        double spectralBound = REGULARIZATION;
        for (double[] column : columns) {
            double norm = 0.0D;
            for (double val : column) {
                norm += val * val;
            }
            spectralBound += norm;
        }
        double step = 0.85D / Math.max(0.01D, spectralBound);
        double[] achieved = new double[AXES];
        // ------------------------------------PROJECTED SOLVER------------------------------------
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            multiply(columns, controls, achieved);
            for (int idx = 0; idx < controls.length; idx++) {
                double gradient = REGULARIZATION * controls[idx];
                for (int axis = 0; axis < AXES; axis++) {
                    gradient += columns[idx][axis] * (achieved[axis] - target[axis]);
                }
                controls[idx] = Mth.clamp(controls[idx] - step * gradient, 0.0D, 1.0D);
            }
        }

        multiply(columns, controls, achieved);
        double residual = 0.0D;
        double targetEnergy = 0.0D;
        for (int axis = 0; axis < AXES; axis++) {
            double error = achieved[axis] - target[axis];
            residual += error * error;
            targetEnergy += target[axis] * target[axis];
        }
        return new Allocation(controls, Math.sqrt(residual / Math.max(1.0D, targetEnergy)));
    }

    // Get the normalization capacity
    private static double normalizationCapacity(double available, double target) {
        if (target > 1.0E-9D) {
            return Math.max(0.0D, available);
        }
        if (target < -1.0E-9D) {
            return Math.max(0.0D, -available);
        }
        return Math.abs(available);
    }

    // Normalize the physical force
    public static Vec3 normalizePhysicalForce(ShipControlMap map, Vec3 physicalForce) {
        if (map == null || physicalForce == null) {
            return Vec3.ZERO;
        }

        double capacityX = 0.0D;
        double capacityY = 0.0D;
        double capacityZ = 0.0D;
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            Vec3 force = unit.forceDirection().scale(unit.maxThrust());
            capacityX += directionalCapacity(force.x, physicalForce.x);
            capacityY += directionalCapacity(force.y, physicalForce.y);
            capacityZ += directionalCapacity(force.z, physicalForce.z);
        }

        return new Vec3(
                normalizePhysicalComponent(physicalForce.x, capacityX),
                normalizePhysicalComponent(physicalForce.y, capacityY),
                normalizePhysicalComponent(physicalForce.z, capacityZ));
    }

    // Get the directional capacity
    private static double directionalCapacity(double availableForce, double requestedForce) {
        if (requestedForce > 0.0D) {
            return Math.max(0.0D, availableForce);
        }
        if (requestedForce < 0.0D) {
            return Math.max(0.0D, -availableForce);
        }
        return 0.0D;
    }

    // Normalize the physical component
    private static double normalizePhysicalComponent(double force, double capacity) {
        if (!Double.isFinite(force) || !Double.isFinite(capacity) || capacity <= 1.0E-9D) {
            return 0.0D;
        }
        return Mth.clamp(force / capacity, -1.0D, 1.0D);
    }

    // Multiply the control workspace matrix
    private static void multiply(double[][] columns, double[] controls, double[] res) {
        java.util.Arrays.fill(res, 0.0D);
        for (int idx = 0; idx < controls.length; idx++) {
            for (int axis = 0; axis < AXES; axis++) {
                res[axis] += columns[idx][axis] * controls[idx];
            }
        }
    }

    // Clamp the ship control allocator
    private static double clamp(double val) {
        return Double.isFinite(val) ? Mth.clamp(val, -1.0D, 1.0D) : 0.0D;
    }

    // Get the finite vector
    private static Vec3 finiteVector(Vec3 val) {
        return val != null
                && Double.isFinite(val.x)
                && Double.isFinite(val.y)
                && Double.isFinite(val.z)
                ? val : Vec3.ZERO;
    }

    // Store the carriage demand
    public record CarriageDemand(
            UUID carriageId,
            Set<UUID> bodyIds,
            Vec3 centerOfMass,
            double mass,
            Vec3 desiredTorque,
            boolean yawAuthority
    ) {
        // Initialize the carriage demand
        public CarriageDemand {
            LinkedHashSet<UUID> normalizedBodies = new LinkedHashSet<>();
            if (bodyIds != null) {
                bodyIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(UUID::toString))
                        .forEach(normalizedBodies::add);
            }
            if (carriageId == null) {
                carriageId = normalizedBodies.stream().findFirst()
                        .orElse(new UUID(0L, 0L));
            }
            normalizedBodies.add(carriageId);
            LinkedHashSet<UUID> orderedBodies = new LinkedHashSet<>();
            normalizedBodies.stream()
                    .sorted(Comparator.comparing(UUID::toString))
                    .forEach(orderedBodies::add);
            bodyIds = Collections.unmodifiableSet(orderedBodies);
            centerOfMass = finiteVector(centerOfMass);
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
            desiredTorque = finiteVector(desiredTorque);
        }

        // Initialize the carriage demand
        public CarriageDemand(
                UUID carriageId,
                Collection<UUID> bodyIds,
                Vec3 centerOfMass,
                double mass,
                Vec3 desiredTorque,
                boolean yawAuthority
        ) {
            this(carriageId,
                    bodyIds == null ? Set.of() : new LinkedHashSet<>(bodyIds),
                    centerOfMass, mass, desiredTorque, yawAuthority);
        }
    }

    // Store the resolved carriage
    private record ResolvedCarriage(CarriageDemand demand, int[] unitIndices) {
        // Initialize the resolved carriage
        private ResolvedCarriage {
            unitIndices = unitIndices == null ? new int[0] : unitIndices.clone();
        }

        // Get the unit indices
        @Override
        public int[] unitIndices() {
            return unitIndices.clone();
        }
    }

    // Store the allocation
    public record Allocation(double[] controls, double residual) {
        // Initialize the allocation
        public Allocation {
            controls = controls == null ? new double[0] : controls.clone();
            residual = Double.isFinite(residual) ? Math.max(0.0D, residual) : 1.0D;
        }

        // Get the controls
        @Override
        public double[] controls() {
            return controls.clone();
        }
    }
}
