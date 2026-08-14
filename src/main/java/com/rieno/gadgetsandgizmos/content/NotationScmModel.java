package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.NotationExpression;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Build a small read-only response model from the SCM's Sable calibration data
public final class NotationScmModel {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int RESPONSE_SAMPLE_COUNT = 9;
    private static final String SAMPLES_TAG = "Samples";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether notation SCM model is available
    private final boolean available;
    // Unit count
    private final int unitCount;
    // Bearing count
    private final int bearingCount;
    // Surface count
    private final int surfaceCount;
    // Last update time
    private final long updatedAt;
    // Tracked samples
    private final List<ResponseSample> samples;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the notation SCM model
    private NotationScmModel(boolean available, int unitCount, int bearingCount,
                             int surfaceCount, long updatedAt, List<ResponseSample> samples) {
        this.available = available;
        this.unitCount = Math.max(0, unitCount);
        this.bearingCount = Math.max(0, bearingCount);
        this.surfaceCount = Math.max(0, surfaceCount);
        this.updatedAt = Math.max(0L, updatedAt);
        this.samples = samples == null ? List.of() : List.copyOf(samples);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create an empty notation SCM model
    public static NotationScmModel empty() {
        return new NotationScmModel(false, 0, 0, 0, 0L, List.of());
    }

    // Create the notation SCM model
    public static NotationScmModel from(ShipControlMap map) {
        if (map == null) return empty();
        int surfaces = map.bearings().stream()
                .flatMap(bearing -> bearing.poses().stream())
                .mapToInt(pose -> pose.aerodynamicSurfaces().size()).max().orElse(0);
        List<ResponseSample> resp = new ArrayList<>(RESPONSE_SAMPLE_COUNT);
        for (int idx = 0; idx < RESPONSE_SAMPLE_COUNT; idx++) {
            double control = idx / (double) (RESPONSE_SAMPLE_COUNT - 1);
            resp.add(simulate(map, control));
        }
        int controllableUnits = map.controllableUnitCount();
        boolean hasResponseData = controllableUnits > 0 || surfaces > 0;
        return new NotationScmModel(hasResponseData, controllableUnits, map.bearings().size(),
                surfaces, map.updatedAt(), resp);
    }

    // Check if this is available
    public boolean available() {
        return available && samples.size() >= 2;
    }

    // Get the unit count
    public int unitCount() {
        return unitCount;
    }

    // Get the bearing count
    public int bearingCount() {
        return bearingCount;
    }

    // Get the surface count
    public int surfaceCount() {
        return surfaceCount;
    }

    // Get the last update time
    public long updatedAt() {
        return updatedAt;
    }

    // Evaluate the notation SCM model
    public double evaluate(OutputMode mode, double control) {
        if (!available()) return control;
        OutputMode selected = mode == null ? OutputMode.FORCE_MAGNITUDE : mode;
        double clamped = clamp(control, samples.getFirst().control, samples.getLast().control);
        ResponseSample prev = samples.getFirst();
        if (clamped <= prev.control) return prev.value(selected);
        for (int idx = 1; idx < samples.size(); idx++) {
            ResponseSample next = samples.get(idx);
            if (clamped <= next.control) {
                double span = next.control - prev.control;
                double amount = span == 0.0D ? 0.0D : (clamped - prev.control) / span;
                return prev.value(selected) + (next.value(selected) - prev.value(selected)) * amount;
            }
            prev = next;
        }
        return samples.getLast().value(selected);
    }

    // Get the curve
    public List<NotationExpression.CurveSample> curve(OutputMode mode) {
        OutputMode selected = mode == null ? OutputMode.FORCE_MAGNITUDE : mode;
        return samples.stream()
                .map(sample -> new NotationExpression.CurveSample(sample.control, sample.value(selected)))
                .toList();
    }

    // Write the notation SCM model data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Available", available);
        tag.putInt("UnitCount", unitCount);
        tag.putInt("BearingCount", bearingCount);
        tag.putInt("SurfaceCount", surfaceCount);
        tag.putLong("UpdatedAt", updatedAt);
        ListTag entries = new ListTag();
        samples.forEach(sample -> entries.add(sample.toTag()));
        tag.put(SAMPLES_TAG, entries);
        return tag;
    }

    // Read the notation SCM model data
    public static NotationScmModel fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return empty();
        List<ResponseSample> samples = new ArrayList<>();
        ListTag entries = tag.getList(SAMPLES_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < entries.size(); idx++) {
            ResponseSample sample = ResponseSample.fromTag(entries.getCompound(idx));
            if (sample != null) samples.add(sample);
        }
        samples.sort(Comparator.comparingDouble(sample -> sample.control));
        return new NotationScmModel(tag.getBoolean("Available"), tag.getInt("UnitCount"),
                tag.getInt("BearingCount"), tag.getInt("SurfaceCount"),
                tag.getLong("UpdatedAt"), samples);
    }

    // Get the simulate
    private static ResponseSample simulate(ShipControlMap map, double control) {
        Map<Integer, ShipControlMap.BearingResponse> bearingResponses = new LinkedHashMap<>();
        List<ShipControlMap.BearingPose> selectedPoses = new ArrayList<>();
        for (ShipControlMap.BearingUnit bearing : map.bearings()) {
            ShipControlMap.BearingPose pose = closestPose(bearing, control);
            if (pose == null) continue;
            selectedPoses.add(pose);
            pose.responses().forEach(resp -> bearingResponses.put(resp.propulsionUnitIndex(), resp));
        }

        Vec3 totalForce = Vec3.ZERO;
        Vec3 totalTorque = Vec3.ZERO;
        double speed = 0.0D;
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            if (!unit.controllable()) continue;
            double thrust = calibratedThrust(unit, control);
            ShipControlMap.BearingResponse bearing = bearingResponses.get(unit.index());
            Vec3 pos = unit.rootPosition();
            Vec3 dir = unit.forceDirection();
            if (bearing != null) {
                double ratio = unit.maxThrust() <= 1.0E-9D
                        ? control : clamp(thrust / unit.maxThrust(), 0.0D, 1.0D);
                thrust = bearing.maxThrust() * ratio;
                pos = bearing.rootPosition();
                dir = bearing.forceDirection();
            }
            Vec3 force = dir.scale(thrust);
            totalForce = totalForce.add(force);
            totalTorque = totalTorque.add(pos.subtract(map.centerOfMass()).cross(force));
            double speedRatio = unit.maxThrust() <= 1.0E-9D
                    ? control : clamp(thrust / unit.maxThrust(), 0.0D, 1.0D);
            speed = Math.max(speed, unit.maxSpeed() * speedRatio);
        }

        for (ShipControlMap.BearingPose pose : selectedPoses) {
            List<ShipControlMap.AerodynamicSurface> surfaces = pose.aerodynamicSurfaces();
            double weightTotal = surfaces.stream().mapToDouble(NotationScmModel::surfaceWeight).sum();
            if (surfaces.isEmpty() || weightTotal <= 1.0E-9D) continue;
            Vec3 surfaceForce = Vec3.ZERO;
            Vec3 surfaceTorque = Vec3.ZERO;
            for (ShipControlMap.AerodynamicSurface surface : surfaces) {
                Vec3 force = surface.normal().scale(surfaceWeight(surface) / weightTotal);
                surfaceForce = surfaceForce.add(force);
                surfaceTorque = surfaceTorque.add(
                        surface.rootPosition().subtract(map.centerOfMass()).cross(force));
            }
            surfaceForce = scaledAuthority(surfaceForce, pose.maxAerodynamicForce() * control);
            surfaceTorque = scaledAuthority(surfaceTorque, pose.maxAerodynamicTorque() * control);
            totalForce = totalForce.add(surfaceForce);
            totalTorque = totalTorque.add(surfaceTorque);
        }

        return new ResponseSample(control, totalForce.length(), totalForce.x, totalForce.y, totalForce.z,
                totalTorque.length(), totalTorque.x, totalTorque.y, totalTorque.z, speed);
    }

    // Get the closest pose
    private static ShipControlMap.BearingPose closestPose(ShipControlMap.BearingUnit bearing, double control) {
        if (bearing == null || bearing.poses().isEmpty()) return null;
        double targetX = bearing.minX() + (bearing.maxX() - bearing.minX()) * control;
        double targetZ = bearing.minZ() + (bearing.maxZ() - bearing.minZ()) * control;
        double spanX = Math.max(1.0D, Math.abs(bearing.maxX() - bearing.minX()));
        double spanZ = Math.max(1.0D, Math.abs(bearing.maxZ() - bearing.minZ()));
        ShipControlMap.BearingPose closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (ShipControlMap.BearingPose pose : bearing.poses()) {
            double deltaX = (pose.angleX() - targetX) / spanX;
            double deltaZ = (pose.angleZ() - targetZ) / spanZ;
            double distance = deltaX * deltaX + deltaZ * deltaZ;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = pose;
            }
        }
        return closest;
    }

    // Get the calibrated thrust
    private static double calibratedThrust(ShipControlMap.PropulsionUnit unit, double normalizedControl) {
        double control = unit.minControl() + (unit.maxControl() - unit.minControl()) * normalizedControl;
        List<ShipControlMap.CalibrationSample> curve = unit.samples().stream()
                .filter(ShipControlMap.CalibrationSample::active)
                .filter(sample -> Double.isFinite(sample.control()) && Double.isFinite(sample.thrust()))
                .sorted(Comparator.comparingDouble(ShipControlMap.CalibrationSample::control)).toList();
        if (curve.isEmpty()) {
            return unit.minThrust() + (unit.maxThrust() - unit.minThrust()) * normalizedControl;
        }
        ShipControlMap.CalibrationSample prev = curve.getFirst();
        if (control <= prev.control()) return Math.max(0.0D, prev.thrust());
        for (int idx = 1; idx < curve.size(); idx++) {
            ShipControlMap.CalibrationSample next = curve.get(idx);
            if (control <= next.control()) {
                double span = next.control() - prev.control();
                double amount = span == 0.0D ? 0.0D : (control - prev.control()) / span;
                return Math.max(0.0D, prev.thrust() + (next.thrust() - prev.thrust()) * amount);
            }
            prev = next;
        }
        return Math.max(0.0D, curve.getLast().thrust());
    }

    // Get the surface weight
    private static double surfaceWeight(ShipControlMap.AerodynamicSurface surface) {
        return Math.max(0.0D, surface.parallelDragScalar())
                + Math.max(0.0D, surface.directionlessDragScalar())
                + Math.max(0.0D, surface.liftScalar());
    }

    // Get the scaled authority
    private static Vec3 scaledAuthority(Vec3 dir, double magnitude) {
        return dir.lengthSqr() <= 1.0E-12D || magnitude <= 0.0D
                ? Vec3.ZERO : dir.normalize().scale(magnitude);
    }

    // Clamp the notation SCM model
    private static double clamp(double val, double minimum, double maximum) {
        return val < minimum ? minimum : Math.min(val, maximum);
    }

    // Define the output mode values
    public enum OutputMode {
        FORCE_MAGNITUDE("force", "Force magnitude"),
        FORCE_X("force_x", "Force X"),
        FORCE_Y("force_y", "Force Y"),
        FORCE_Z("force_z", "Force Z"),
        TORQUE_MAGNITUDE("torque", "Torque magnitude"),
        TORQUE_X("torque_x", "Torque X"),
        TORQUE_Y("torque_y", "Torque Y"),
        TORQUE_Z("torque_z", "Torque Z"),
        SPEED("speed", "Estimated speed");

        // Output mode id
        private final String id;
        // Display label
        private final String label;

        // Initialize the output mode
        OutputMode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Get the label
        public String label() {
            return label;
        }

        // Get the next
        public OutputMode next() {
            OutputMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        // Create the output mode from id
        public static OutputMode fromId(String id) {
            String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
            for (OutputMode mode : values()) {
                if (mode.id.equals(normalized)) return mode;
            }
            return FORCE_MAGNITUDE;
        }
    }

    // Store the response sample
    private record ResponseSample(double control, double forceMagnitude,
                                  double forceX, double forceY, double forceZ,
                                  double torqueMagnitude, double torqueX, double torqueY, double torqueZ,
                                  double speed) {
        // Get the value
        private double value(OutputMode mode) {
            return switch (mode) {
                case FORCE_MAGNITUDE -> forceMagnitude;
                case FORCE_X -> forceX;
                case FORCE_Y -> forceY;
                case FORCE_Z -> forceZ;
                case TORQUE_MAGNITUDE -> torqueMagnitude;
                case TORQUE_X -> torqueX;
                case TORQUE_Y -> torqueY;
                case TORQUE_Z -> torqueZ;
                case SPEED -> speed;
            };
        }

        // Write the response sample data
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Control", control);
            for (OutputMode mode : OutputMode.values()) tag.putDouble(mode.id, value(mode));
            return tag;
        }

        // Read the response sample data
        private static ResponseSample fromTag(CompoundTag tag) {
            if (tag == null || !tag.contains("Control", Tag.TAG_DOUBLE)) return null;
            return new ResponseSample(tag.getDouble("Control"),
                    tag.getDouble(OutputMode.FORCE_MAGNITUDE.id),
                    tag.getDouble(OutputMode.FORCE_X.id), tag.getDouble(OutputMode.FORCE_Y.id),
                    tag.getDouble(OutputMode.FORCE_Z.id), tag.getDouble(OutputMode.TORQUE_MAGNITUDE.id),
                    tag.getDouble(OutputMode.TORQUE_X.id), tag.getDouble(OutputMode.TORQUE_Y.id),
                    tag.getDouble(OutputMode.TORQUE_Z.id), tag.getDouble(OutputMode.SPEED.id));
        }
    }
}
