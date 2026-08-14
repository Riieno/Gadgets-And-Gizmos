package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Select bearing poses which best match the requested ship force and torque
public final class ShipBearingPlanner {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final double ANGLE_STEP_DEGREES = 5.0D;
    static final double AERODYNAMIC_TIME_STEP = 1.0D / 20.0D;
    static final double[] WIND_SWEEP_SPEEDS = {2.0D, 8.0D, 24.0D};
    static final int WIND_SWEEP_TEST_COUNT = 26 * WIND_SWEEP_SPEEDS.length;
    private static final int PLAN_PASSES = 4;
    private static final int YAW_TORQUE_AXIS = 4;
    private static final double UNCOMMANDED_AXIS_WEIGHT = 0.45D;
    private static final double MOVEMENT_WEIGHT = 0.012D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship bearing planner
    private ShipBearingPlanner() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the angle sweep
    public static List<Double> angleSweep(double minimum, double maximum) {
        double min = finite(minimum);
        double max = Math.max(min, finite(maximum));
        Set<Double> values = new LinkedHashSet<>();
        values.add(roundAngle(min));
        values.add(roundAngle(max));
        if (min <= 0.0D && max >= 0.0D) {
            values.add(0.0D);
        }
        double firstStep = Math.ceil(min / ANGLE_STEP_DEGREES) * ANGLE_STEP_DEGREES;
        for (double angle = firstStep; angle <= max + 1.0E-6D; angle += ANGLE_STEP_DEGREES) {
            values.add(roundAngle(Mth.clamp(angle, min, max)));
        }
        return values.stream().sorted().toList();
    }

    // Build the single-axis poses
    public static List<Pose> singleAxisPoses(double minimum, double maximum) {
        return angleSweep(minimum, maximum).stream()
                .map(angle -> new Pose(angle, 0.0D))
                .toList();
    }

    // Get the sampled single axis poses
    public static List<Pose> sampledSingleAxisPoses(
            double minimum, double maximum, int sampleCount
    ) {
        return sampledAngleSweep(minimum, maximum, sampleCount).stream()
                .map(angle -> new Pose(angle, 0.0D))
                .toList();
    }

    // Get the vector poses
    public static List<Pose> vectorPoses(double maximumTilt) {
        double limit = Math.max(0.0D, finite(maximumTilt));
        List<Double> values = angleSweep(-limit, limit);
        List<Pose> poses = new ArrayList<>();
        for (double x : values) {
            for (double z : values) {
                if (Math.hypot(x, z) <= limit + 1.0E-6D) {
                    poses.add(new Pose(x, z));
                }
            }
        }
        poses.sort(java.util.Comparator
                .comparingDouble((Pose pose) -> Math.hypot(pose.angleX(), pose.angleZ()))
                .thenComparingDouble(Pose::angleX)
                .thenComparingDouble(Pose::angleZ));
        return List.copyOf(poses);
    }

    // Get the sampled vector poses
    public static List<Pose> sampledVectorPoses(double maximumTilt, int samplesPerAxis) {
        double limit = Math.max(0.0D, finite(maximumTilt));
        List<Double> values = sampledAngleSweep(-limit, limit, samplesPerAxis);
        List<Pose> poses = new ArrayList<>();
        for (double x : values) {
            for (double z : values) {
                if (Math.hypot(x, z) <= limit + 1.0E-6D) {
                    poses.add(new Pose(x, z));
                }
            }
        }
        return sortedPoses(poses);
    }

    // Get the independent axis poses
    public static List<Pose> independentAxisPoses(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ
    ) {
        List<Pose> poses = new ArrayList<>();
        for (double x : angleSweep(minimumX, maximumX)) {
            for (double z : angleSweep(minimumZ, maximumZ)) {
                poses.add(new Pose(x, z));
            }
        }
        poses.sort(java.util.Comparator
                .comparingDouble((Pose pose) -> Math.hypot(pose.angleX(), pose.angleZ()))
                .thenComparingDouble(Pose::angleX)
                .thenComparingDouble(Pose::angleZ));
        return List.copyOf(poses);
    }

    // Get the sampled independent axis poses
    public static List<Pose> sampledIndependentAxisPoses(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            int samplesPerAxis
    ) {
        List<Pose> poses = new ArrayList<>();
        for (double x : sampledAngleSweep(minimumX, maximumX, samplesPerAxis)) {
            for (double z : sampledAngleSweep(minimumZ, maximumZ, samplesPerAxis)) {
                poses.add(new Pose(x, z));
            }
        }
        return sortedPoses(poses);
    }

    // Get the sampled angle sweep
    public static List<Double> sampledAngleSweep(
            double minimum, double maximum, int sampleCount
    ) {
        double min = finite(minimum);
        double max = Math.max(min, finite(maximum));
        int count = Math.max(2, sampleCount);
        Set<Double> values = new LinkedHashSet<>();
        for (int idx = 0; idx < count; idx++) {
            double fraction = idx / (double) (count - 1);
            values.add(roundAngle(Mth.lerp(fraction, min, max)));
        }
        if (min <= 0.0D && max >= 0.0D) {
            values.add(0.0D);
        }
        return values.stream().sorted().toList();
    }

    // Get the sorted poses
    private static List<Pose> sortedPoses(List<Pose> poses) {
        poses.sort(java.util.Comparator
                .comparingDouble((Pose pose) -> Math.hypot(pose.angleX(), pose.angleZ()))
                .thenComparingDouble(Pose::angleX)
                .thenComparingDouble(Pose::angleZ));
        return List.copyOf(poses);
    }

    // Get the descendant sub levels
    public static Set<UUID> descendantSubLevels(
            Collection<UUID> roots,
            Map<UUID, ? extends Collection<UUID>> childrenBySubLevel
    ) {
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        if (roots != null) {
            roots.stream().filter(java.util.Objects::nonNull).forEach(pending::addLast);
        }
        Set<UUID> descendants = new HashSet<>();
        while (!pending.isEmpty()) {
            UUID current = pending.removeFirst();
            if (!descendants.add(current)) {
                continue;
            }
            Collection<UUID> children = childrenBySubLevel == null
                    ? null : childrenBySubLevel.get(current);
            if (children != null) {
                children.stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(child -> !descendants.contains(child))
                        .forEach(pending::addLast);
            }
        }
        return Set.copyOf(descendants);
    }

    // Select the pose
    public static int selectPose(
            ShipControlMap.BearingUnit bearing,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 centerOfMass,
            double currentX,
            double currentZ
    ) {
        return selectPose(
                bearing, desiredForce, desiredTorque, centerOfMass,
                currentX, currentZ, Vec3.ZERO, Vec3.ZERO, 1.0D);
    }

    // Select the pose
    public static int selectPose(
            ShipControlMap.BearingUnit bearing,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 centerOfMass,
            double currentX,
            double currentZ,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            double airPressure
    ) {
        if (bearing == null || bearing.poses().isEmpty()) {
            return -1;
        }
        Map<Integer, Integer> selected = selectPoses(
                List.of(bearing), desiredForce, desiredTorque, centerOfMass,
                Map.of(bearing.index(), new Pose(currentX, currentZ)),
                relativeAirflow, angularVelocity, airPressure);
        return selected.getOrDefault(bearing.index(), -1);
    }

    // Select the poses
    public static Map<Integer, Integer> selectPoses(
            List<ShipControlMap.BearingUnit> bearings,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 centerOfMass,
            Map<Integer, Pose> currentPoses,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            double airPressure
    ) {
        return selectPoses(
                bearings, desiredForce, desiredTorque, centerOfMass,
                currentPoses, relativeAirflow, angularVelocity, airPressure, true);
    }

    // Select the poses
    static Map<Integer, Integer> selectPoses(
            List<ShipControlMap.BearingUnit> bearings,
            Vec3 desiredForce,
            Vec3 desiredTorque,
            Vec3 centerOfMass,
            Map<Integer, Pose> currentPoses,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            double airPressure,
            boolean yawTorqueEnabled
    ) {
        // -----------------------------------------------------DEMAND CHECKS-----------------------------------------------------
        if (bearings == null || bearings.isEmpty()) {
            return Map.of();
        }
        Vec3 forceDemand = finite(desiredForce);
        Vec3 torqueDemand = finite(desiredTorque);
        if (!yawTorqueEnabled) {
            torqueDemand = new Vec3(torqueDemand.x, 0.0D, torqueDemand.z);
        }
        if (forceDemand.lengthSqr() <= 1.0E-12D
                && torqueDemand.lengthSqr() <= 1.0E-12D) {
            Map<Integer, Integer> neutral = new LinkedHashMap<>();
            for (ShipControlMap.BearingUnit bearing : bearings) {
                if (bearing != null && !bearing.poses().isEmpty()) {
                    neutral.put(bearing.index(), closestPose(bearing.poses(), 0.0D, 0.0D));
                }
            }
            return Map.copyOf(neutral);
        }

        Vec3 center = finite(centerOfMass);
        // -----------------------------------------------------BEARING PLANS-----------------------------------------------------
        List<BearingPlan> plans = bearings.stream()
                .filter(java.util.Objects::nonNull)
                .filter(bearing -> !bearing.poses().isEmpty())
                .map(bearing -> buildPlan(
                        bearing, center, relativeAirflow, angularVelocity, airPressure))
                .toList();
        if (plans.isEmpty()) {
            return Map.of();
        }

        double[] target = components(forceDemand, torqueDemand);
        if (!yawTorqueEnabled) {
            target[YAW_TORQUE_AXIS] = 0.0D;
        }
        double[] capacities = responseCapacities(plans, target);
        if (!yawTorqueEnabled) {
            capacities[YAW_TORQUE_AXIS] = Double.POSITIVE_INFINITY;
        }
        for (int axis = 0; axis < target.length; axis++) {
            if (capacities[axis] <= 1.0E-12D) {
                target[axis] = 0.0D;
                capacities[axis] = 1.0D;
            }
        }

        int[] selected = new int[plans.size()];
        double[] achieved = new double[6];
        for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
            BearingPlan plan = plans.get(planIndex);
            Pose current = currentPoses == null ? null : currentPoses.get(plan.bearing().index());
            selected[planIndex] = current == null
                    ? closestPose(plan.bearing().poses(), 0.0D, 0.0D)
                    : closestPose(plan.bearing().poses(), current.angleX(), current.angleZ());
            addNormalized(achieved, plan.responses().get(selected[planIndex]), capacities, 1.0D);
        }

        // ------------------------------------PLAN REFINEMENT------------------------------------
        for (int pass = 0; pass < PLAN_PASSES; pass++) {
            boolean changed = false;
            for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
                BearingPlan plan = plans.get(planIndex);
                int prev = selected[planIndex];
                addNormalized(achieved, plan.responses().get(prev), capacities, -1.0D);

                Pose current = currentPoses == null
                        ? null : currentPoses.get(plan.bearing().index());
                int best = prev;
                double bestCost = Double.MAX_VALUE;
                for (int poseIndex = 0; poseIndex < plan.responses().size(); poseIndex++) {
                    double cost = responseCost(
                            achieved, plan.responses().get(poseIndex), capacities, target);
                    cost += movementCost(plan.bearing(), plan.bearing().poses().get(poseIndex), current);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = poseIndex;
                    }
                }
                selected[planIndex] = best;
                addNormalized(achieved, plan.responses().get(best), capacities, 1.0D);
                changed |= best != prev;
            }
            if (!changed) {
                break;
            }
        }

        Map<Integer, Integer> res = new LinkedHashMap<>();
        for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
            res.put(plans.get(planIndex).bearing().index(), selected[planIndex]);
        }
        return Map.copyOf(res);
    }

    // Get the pose response
    static AerodynamicResponse poseResponse(
            ShipControlMap.BearingPose pose,
            Vec3 centerOfMass,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            double airPressure
    ) {
        if (pose == null) {
            return AerodynamicResponse.ZERO;
        }
        Vec3 center = finite(centerOfMass);
        Vec3 force = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        for (ShipControlMap.BearingResponse resp : pose.responses()) {
            Vec3 responseForce =
                    resp.forceDirection().scale(Math.max(0.0D, resp.maxThrust()));
            force = force.add(responseForce);
            torque = torque.add(
                    resp.rootPosition().subtract(center).cross(responseForce));
        }
        AerodynamicResponse aerodynamic = simulateAerodynamics(
                pose.aerodynamicSurfaces(), relativeAirflow, angularVelocity,
                center, airPressure);
        return new AerodynamicResponse(
                force.add(aerodynamic.force()), torque.add(aerodynamic.torque()));
    }

    // Get the simulate aerodynamics
    static AerodynamicResponse simulateAerodynamics(
            Collection<ShipControlMap.AerodynamicSurface> surfaces,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            Vec3 centerOfMass,
            double airPressure
    ) {
        if (surfaces == null || surfaces.isEmpty()) {
            return AerodynamicResponse.ZERO;
        }
        Vec3 linear = finite(relativeAirflow);
        Vec3 angular = finite(angularVelocity);
        Vec3 center = finite(centerOfMass);
        double pressure = Math.max(0.0D, finite(airPressure));
        Vec3 totalForce = Vec3.ZERO;
        Vec3 totalTorque = Vec3.ZERO;
        for (ShipControlMap.AerodynamicSurface surface : surfaces) {
            Vec3 leverArm = surface.rootPosition().subtract(center);
            Vec3 surfaceVelocity = linear.add(angular.cross(leverArm));
            Vec3 force = aerodynamicForce(surface, surfaceVelocity, pressure);
            totalForce = totalForce.add(force);
            totalTorque = totalTorque.add(leverArm.cross(force));
        }
        return new AerodynamicResponse(totalForce, totalTorque);
    }

    // Get the wind sweep authority
    static AerodynamicResponse windSweepAuthority(
            Collection<ShipControlMap.AerodynamicSurface> surfaces,
            Vec3 centerOfMass,
            double airPressure
    ) {
        double maximumForce = 0.0D;
        double maximumTorque = 0.0D;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Vec3 dir = new Vec3(x, y, z);
                    if (dir.lengthSqr() <= 1.0E-12D) {
                        continue;
                    }
                    dir = dir.normalize();
                    for (double speed : WIND_SWEEP_SPEEDS) {
                        AerodynamicResponse resp = simulateAerodynamics(
                                surfaces, dir.scale(speed), Vec3.ZERO,
                                centerOfMass, airPressure);
                        maximumForce = Math.max(maximumForce, resp.force().length());
                        maximumTorque = Math.max(maximumTorque, resp.torque().length());
                    }
                }
            }
        }
        return new AerodynamicResponse(
                new Vec3(maximumForce, 0.0D, 0.0D),
                new Vec3(maximumTorque, 0.0D, 0.0D));
    }

    // Get the aerodynamic force
    private static Vec3 aerodynamicForce(
            ShipControlMap.AerodynamicSurface surface,
            Vec3 relativeVelocity,
            double airPressure
    ) {
        Vec3 normal = surface.normal();
        if (normal.lengthSqr() <= 1.0E-12D || relativeVelocity.lengthSqr() <= 1.0E-12D
                || airPressure <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 parallelDrag = normal.scale(
                normal.dot(relativeVelocity) * surface.parallelDragScalar()
                        * airPressure * AERODYNAMIC_TIME_STEP);
        Vec3 directionlessDrag = relativeVelocity.scale(
                surface.directionlessDragScalar() * airPressure * AERODYNAMIC_TIME_STEP);
        Vec3 lift = normal.scale(
                relativeVelocity.subtract(parallelDrag).length()
                        * surface.liftScalar() * airPressure * AERODYNAMIC_TIME_STEP);
        return parallelDrag.add(directionlessDrag).add(lift).scale(-1.0D);
    }

    // Build the plan
    private static BearingPlan buildPlan(
            ShipControlMap.BearingUnit bearing,
            Vec3 centerOfMass,
            Vec3 relativeAirflow,
            Vec3 angularVelocity,
            double airPressure
    ) {
        List<AerodynamicResponse> responses = bearing.poses().stream()
                .map(pose -> poseResponse(
                        pose, centerOfMass, relativeAirflow, angularVelocity, airPressure))
                .toList();
        return new BearingPlan(bearing, responses);
    }

    // Get the response capacities
    private static double[] responseCapacities(List<BearingPlan> plans, double[] target) {
        double[] capacities = new double[6];
        for (BearingPlan plan : plans) {
            for (int axis = 0; axis < capacities.length; axis++) {
                double available = 0.0D;
                for (AerodynamicResponse resp : plan.responses()) {
                    double val = component(resp, axis);
                    if (target[axis] > 1.0E-9D) {
                        available = Math.max(available, val);
                    } else if (target[axis] < -1.0E-9D) {
                        available = Math.max(available, -val);
                    } else {
                        available = Math.max(available, Math.abs(val));
                    }
                }
                capacities[axis] += Math.max(0.0D, available);
            }
        }
        return capacities;
    }

    // Get the response cost
    private static double responseCost(
            double[] achieved,
            AerodynamicResponse candidate,
            double[] capacities,
            double[] target
    ) {
        double cost = 0.0D;
        for (int axis = 0; axis < target.length; axis++) {
            double val = achieved[axis] + component(candidate, axis) / capacities[axis];
            double error = val - target[axis];
            double weight = Math.abs(target[axis]) <= 1.0E-9D
                    ? UNCOMMANDED_AXIS_WEIGHT : 1.0D;
            cost += error * error * weight;
        }
        return cost;
    }

    // Get the movement cost
    private static double movementCost(
            ShipControlMap.BearingUnit bearing,
            ShipControlMap.BearingPose pose,
            Pose current
    ) {
        if (current == null) {
            return 0.0D;
        }
        double rangeX = Math.max(1.0D, bearing.maxX() - bearing.minX());
        double rangeZ = Math.max(1.0D, bearing.maxZ() - bearing.minZ());
        return MOVEMENT_WEIGHT * (
                Math.abs(pose.angleX() - current.angleX()) / rangeX
                        + Math.abs(pose.angleZ() - current.angleZ()) / rangeZ);
    }

    // Add the normalized
    private static void addNormalized(
            double[] total,
            AerodynamicResponse resp,
            double[] capacities,
            double scale
    ) {
        for (int axis = 0; axis < total.length; axis++) {
            total[axis] += component(resp, axis) / capacities[axis] * scale;
        }
    }

    // Get the components
    private static double[] components(Vec3 force, Vec3 torque) {
        return new double[]{
                Mth.clamp(force.x, -1.0D, 1.0D),
                Mth.clamp(force.y, -1.0D, 1.0D),
                Mth.clamp(force.z, -1.0D, 1.0D),
                Mth.clamp(torque.x, -1.0D, 1.0D),
                Mth.clamp(torque.y, -1.0D, 1.0D),
                Mth.clamp(torque.z, -1.0D, 1.0D)
        };
    }

    // Get the component
    private static double component(AerodynamicResponse resp, int axis) {
        return switch (axis) {
            case 0 -> resp.force().x;
            case 1 -> resp.force().y;
            case 2 -> resp.force().z;
            case 3 -> resp.torque().x;
            case 4 -> resp.torque().y;
            case 5 -> resp.torque().z;
            default -> 0.0D;
        };
    }

    // Get the closest pose
    private static int closestPose(List<ShipControlMap.BearingPose> poses, double x, double z) {
        int selected = 0;
        double selectedDistance = Double.MAX_VALUE;
        for (int idx = 0; idx < poses.size(); idx++) {
            ShipControlMap.BearingPose pose = poses.get(idx);
            double distance = Math.hypot(pose.angleX() - x, pose.angleZ() - z);
            if (distance < selectedDistance) {
                selectedDistance = distance;
                selected = idx;
            }
        }
        return selected;
    }

    // Get the round angle
    private static double roundAngle(double angle) {
        return Math.rint(finite(angle) * 1000.0D) / 1000.0D;
    }

    // Normalize the value to a finite result
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Normalize the value to a finite result
    private static Vec3 finite(Vec3 val) {
        if (val == null || !Double.isFinite(val.x)
                || !Double.isFinite(val.y) || !Double.isFinite(val.z)) {
            return Vec3.ZERO;
        }
        return val;
    }

    // Store the aerodynamic response
    record AerodynamicResponse(Vec3 force, Vec3 torque) {
        private static final AerodynamicResponse ZERO =
                new AerodynamicResponse(Vec3.ZERO, Vec3.ZERO);

        // Initialize the aerodynamic response
        AerodynamicResponse {
            force = finite(force);
            torque = finite(torque);
        }
    }

    // Store the bearing plan
    private record BearingPlan(
            ShipControlMap.BearingUnit bearing,
            List<AerodynamicResponse> responses
    ) {
    }

    // Store the pose
    public record Pose(double angleX, double angleZ) {
        // Initialize the pose
        public Pose {
            angleX = finite(angleX);
            angleZ = finite(angleZ);
        }
    }
}
