package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Read optional Contraption Diagram controls without making that addon a required dependency
public final class ContraptionDiagramControllerCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, String> READABLE_PORTS = readablePortTypes();
    private static final Map<ServerSubLevel, CachedSnapshot> SNAPSHOT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption diagram controller compat
    private ContraptionDiagramControllerCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a target
    public static boolean isTarget(@Nullable ControllerDiscoveryNode target) {
        return target != null && ContraptionNetworkLinkerData.isContraptionDiagramTarget(target.blockId());
    }

    // Get the readable ports
    public static CompoundTag readablePorts() {
        CompoundTag ports = new CompoundTag();
        READABLE_PORTS.forEach(ports::putString);
        return ports;
    }

    // Check if the target exists
    public static boolean targetExists(Level level, @Nullable ControllerDiscoveryNode target) {
        if (!isTarget(target) || target.subLevelId() == null || level == null) {
            return false;
        }
        Object subLevel = SubLevelBlockEntityCollector.ensureSubLevelLoaded(level, target.subLevelId());
        if (subLevel == null) {
            return true;
        }
        return !(subLevel instanceof SubLevel resolved) || !resolved.isRemoved();
    }

    // Read the target data
    public static @Nullable AdvancedGraphDocument.Value readTargetData(
            Level level, @Nullable ControllerDiscoveryNode target, String port) {
        if (!isTarget(target)) {
            return null;
        }
        Snapshot snapshot = snapshot(level, target.subLevelId());
        return value(snapshot, port);
    }

    // Get the snapshot
    private static Snapshot snapshot(Level level, @Nullable UUID subLevelId) {
        if (level == null || subLevelId == null) {
            return Snapshot.unavailable(subLevelId);
        }
        Object resolved = SubLevelBlockEntityCollector.ensureSubLevelLoaded(level, subLevelId);
        if (!(resolved instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return Snapshot.unavailable(subLevelId);
        }

        long gameTime = subLevel.getLevel().getGameTime();
        CachedSnapshot cached = SNAPSHOT_CACHE.get(subLevel);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.snapshot();
        }

        Snapshot collected;
        try {
            collected = collect(subLevel);
        } catch (RuntimeException ignored) {
            collected = Snapshot.unavailable(subLevelId);
        }
        SNAPSHOT_CACHE.put(subLevel, new CachedSnapshot(gameTime, collected));
        return collected;
    }

    // Collect the contraption diagram controller compat
    private static Snapshot collect(ServerSubLevel subLevel) {
        subLevel.enableIndividualQueuedForcesTracking(true);
        MassData massData = subLevel.getMassTracker();
        double mass = massData == null ? 0.0D : finite(massData.getMass());
        double inverseMass = massData == null ? 0.0D : finite(massData.getInverseMass());
        Matrix3d inertiaTensor = copy(massData == null ? null : massData.getInertiaTensor());
        Vector3d centerOfMass = copy(massData == null ? null : massData.getCenterOfMass());
        Pose3dc pose = subLevel.logicalPose();
        Vector3d pos = copy(pose == null ? null : pose.position());
        Quaterniond orientation = copy(pose == null ? null : pose.orientation());
        Vector3d worldCenterOfMass = pose == null
                ? new Vector3d(centerOfMass)
                : pose.transformPosition(centerOfMass, new Vector3d());

        Vector3d linearVelocity = new Vector3d();
        Vector3d angularVelocity = new Vector3d();
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle != null && handle.isValid()) {
            handle.getLinearVelocity(linearVelocity);
            handle.getAngularVelocity(angularVelocity);
        }

        List<ForceSample> forces = diagramForces(subLevel, pose);
        Vector3d totalForce = new Vector3d();
        Vector3d totalTorque = new Vector3d();
        for (ForceSample force : forces) {
            totalForce.add(force.force());
            Vector3d torque = new Vector3d(force.point())
                    .sub(centerOfMass)
                    .cross(force.force());
            totalTorque.add(torque);
        }
        Vector3d worldTotalForce = pose == null
                ? new Vector3d(totalForce)
                : pose.transformNormal(totalForce, new Vector3d());
        Vector3d worldTotalTorque = pose == null
                ? new Vector3d(totalTorque)
                : pose.transformNormal(totalTorque, new Vector3d());

        String name = subLevel.getName();
        return new Snapshot(
                true,
                subLevel.getUniqueId(),
                name == null ? "" : name,
                mass,
                inverseMass,
                inertiaTensor,
                centerOfMass,
                worldCenterOfMass,
                pos,
                orientation,
                linearVelocity,
                angularVelocity,
                totalForce,
                worldTotalForce,
                totalTorque,
                worldTotalTorque,
                List.copyOf(forces));
    }

    // Get the diagram forces
    private static List<ForceSample> diagramForces(ServerSubLevel subLevel, @Nullable Pose3dc pose) {
        Map<ForceGroup, QueuedForceGroup> queued = subLevel.getQueuedForceGroups();
        if (queued == null || queued.isEmpty()) {
            return List.of();
        }
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        if (physicsSystem == null) {
            return List.of();
        }
        double timeStep = 0.05D / Math.max(1, physicsSystem.getConfig().substepsPerTick);
        Map<ForceGroup, List<QueuedForceGroup.PointForce>> recorded = new LinkedHashMap<>();
        queued.forEach((group, forceGroup) ->
                recorded.put(group, forceGroup.getRecordedPointForces()));
        return convertForces(recorded, pose, 1.0D / timeStep);
    }

    // Convert the forces
    private static List<ForceSample> convertForces(
            Map<ForceGroup, List<QueuedForceGroup.PointForce>> groupedForces,
            @Nullable Pose3dc pose,
            double scale) {
        if (groupedForces == null || groupedForces.isEmpty()) {
            return List.of();
        }
        List<ForceSample> samples = new ArrayList<>();
        for (Map.Entry<ForceGroup, List<QueuedForceGroup.PointForce>> entry : groupedForces.entrySet()) {
            ForceGroup group = entry.getKey();
            if (group == null || entry.getValue() == null) {
                continue;
            }
            ResourceLocation groupKey = ForceGroups.REGISTRY.getKey(group);
            String groupId = groupKey == null ? "" : groupKey.toString();
            String groupName = group.name() == null ? groupId : group.name().getString();
            for (QueuedForceGroup.PointForce pointForce : entry.getValue()) {
                if (pointForce == null || pointForce.point() == null || pointForce.force() == null) {
                    continue;
                }
                Vector3d point = copy(pointForce.point());
                Vector3d force = copy(pointForce.force()).mul(scale);
                Vector3d worldPoint = pose == null
                        ? new Vector3d(point)
                        : pose.transformPosition(point, new Vector3d());
                Vector3d worldForce = pose == null
                        ? new Vector3d(force)
                        : pose.transformNormal(force, new Vector3d());
                samples.add(new ForceSample(
                        groupId,
                        groupName,
                        group.color(),
                        point,
                        force,
                        worldPoint,
                        worldForce));
            }
        }
        return samples;
    }

    // Get the value
    private static AdvancedGraphDocument.Value value(Snapshot snapshot, String port) {
        return switch (port == null ? "" : port) {
            case "data" -> dataValue(snapshot);
            case "available" -> AdvancedGraphDocument.Value.bool(snapshot.available());
            case "sublevel_id" -> AdvancedGraphDocument.Value.string(
                    snapshot.subLevelId() == null ? "" : snapshot.subLevelId().toString());
            case "sublevel_name" -> AdvancedGraphDocument.Value.string(snapshot.subLevelName());
            case "mass" -> AdvancedGraphDocument.Value.number(snapshot.mass());
            case "inverse_mass" -> AdvancedGraphDocument.Value.number(snapshot.inverseMass());
            case "inertia_tensor" -> matrixValue(snapshot.inertiaTensor());
            case "center_of_mass" -> vectorValue(snapshot.centerOfMass());
            case "center_of_mass_x" -> AdvancedGraphDocument.Value.number(snapshot.centerOfMass().x());
            case "center_of_mass_y" -> AdvancedGraphDocument.Value.number(snapshot.centerOfMass().y());
            case "center_of_mass_z" -> AdvancedGraphDocument.Value.number(snapshot.centerOfMass().z());
            case "world_center_of_mass" -> vectorValue(snapshot.worldCenterOfMass());
            case "world_center_of_mass_x" -> AdvancedGraphDocument.Value.number(snapshot.worldCenterOfMass().x());
            case "world_center_of_mass_y" -> AdvancedGraphDocument.Value.number(snapshot.worldCenterOfMass().y());
            case "world_center_of_mass_z" -> AdvancedGraphDocument.Value.number(snapshot.worldCenterOfMass().z());
            case "position" -> vectorValue(snapshot.position());
            case "position_x" -> AdvancedGraphDocument.Value.number(snapshot.position().x());
            case "position_y" -> AdvancedGraphDocument.Value.number(snapshot.position().y());
            case "position_z" -> AdvancedGraphDocument.Value.number(snapshot.position().z());
            case "orientation" -> quaternionValue(snapshot.orientation());
            case "orientation_x" -> AdvancedGraphDocument.Value.number(snapshot.orientation().x());
            case "orientation_y" -> AdvancedGraphDocument.Value.number(snapshot.orientation().y());
            case "orientation_z" -> AdvancedGraphDocument.Value.number(snapshot.orientation().z());
            case "orientation_w" -> AdvancedGraphDocument.Value.number(snapshot.orientation().w());
            case "linear_velocity" -> vectorValue(snapshot.linearVelocity());
            case "linear_velocity_x" -> AdvancedGraphDocument.Value.number(snapshot.linearVelocity().x());
            case "linear_velocity_y" -> AdvancedGraphDocument.Value.number(snapshot.linearVelocity().y());
            case "linear_velocity_z" -> AdvancedGraphDocument.Value.number(snapshot.linearVelocity().z());
            case "speed" -> AdvancedGraphDocument.Value.number(snapshot.linearVelocity().length());
            case "angular_velocity" -> vectorValue(snapshot.angularVelocity());
            case "angular_velocity_x" -> AdvancedGraphDocument.Value.number(snapshot.angularVelocity().x());
            case "angular_velocity_y" -> AdvancedGraphDocument.Value.number(snapshot.angularVelocity().y());
            case "angular_velocity_z" -> AdvancedGraphDocument.Value.number(snapshot.angularVelocity().z());
            case "angular_speed" -> AdvancedGraphDocument.Value.number(snapshot.angularVelocity().length());
            case "total_force" -> vectorValue(snapshot.totalForce());
            case "total_force_x" -> AdvancedGraphDocument.Value.number(snapshot.totalForce().x());
            case "total_force_y" -> AdvancedGraphDocument.Value.number(snapshot.totalForce().y());
            case "total_force_z" -> AdvancedGraphDocument.Value.number(snapshot.totalForce().z());
            case "force_magnitude" -> AdvancedGraphDocument.Value.number(snapshot.totalForce().length());
            case "world_total_force" -> vectorValue(snapshot.worldTotalForce());
            case "world_total_force_x" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalForce().x());
            case "world_total_force_y" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalForce().y());
            case "world_total_force_z" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalForce().z());
            case "world_force_magnitude" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalForce().length());
            case "total_torque" -> vectorValue(snapshot.totalTorque());
            case "total_torque_x" -> AdvancedGraphDocument.Value.number(snapshot.totalTorque().x());
            case "total_torque_y" -> AdvancedGraphDocument.Value.number(snapshot.totalTorque().y());
            case "total_torque_z" -> AdvancedGraphDocument.Value.number(snapshot.totalTorque().z());
            case "torque_magnitude" -> AdvancedGraphDocument.Value.number(snapshot.totalTorque().length());
            case "world_total_torque" -> vectorValue(snapshot.worldTotalTorque());
            case "world_total_torque_x" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalTorque().x());
            case "world_total_torque_y" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalTorque().y());
            case "world_total_torque_z" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalTorque().z());
            case "world_torque_magnitude" -> AdvancedGraphDocument.Value.number(snapshot.worldTotalTorque().length());
            case "forces" -> forceListValue(snapshot.forces());
            case "force_count" -> AdvancedGraphDocument.Value.number(snapshot.forces().size());
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Get the data value
    private static AdvancedGraphDocument.Value dataValue(Snapshot snapshot) {
        CompoundTag values = new CompoundTag();
        for (String port : READABLE_PORTS.keySet()) {
            values.put(port, encode(value(snapshot, port)));
        }
        values.put("center_of_mass_space", encode(AdvancedGraphDocument.Value.string("contraption_local")));
        values.put("force_space", encode(AdvancedGraphDocument.Value.string("contraption_local")));
        values.put("velocity_space", encode(AdvancedGraphDocument.Value.string("world")));
        return AdvancedGraphDocument.Value.map(values);
    }

    // Force the list value
    private static AdvancedGraphDocument.Value forceListValue(List<ForceSample> forces) {
        CompoundTag values = new CompoundTag();
        for (int idx = 0; idx < forces.size(); idx++) {
            ForceSample force = forces.get(idx);
            CompoundTag entry = new CompoundTag();
            entry.put("group_id", encode(AdvancedGraphDocument.Value.string(force.groupId())));
            entry.put("group_name", encode(AdvancedGraphDocument.Value.string(force.groupName())));
            entry.put("color", encode(AdvancedGraphDocument.Value.number(force.color())));
            entry.put("point", encode(vectorValue(force.point())));
            entry.put("force", encode(vectorValue(force.force())));
            entry.put("magnitude", encode(AdvancedGraphDocument.Value.number(force.force().length())));
            entry.put("world_point", encode(vectorValue(force.worldPoint())));
            entry.put("world_force", encode(vectorValue(force.worldForce())));
            values.put(Integer.toString(idx), encode(AdvancedGraphDocument.Value.map(entry)));
        }
        return AdvancedGraphDocument.Value.list(values);
    }

    // Get the vector value
    private static AdvancedGraphDocument.Value vectorValue(Vector3dc vector) {
        CompoundTag values = new CompoundTag();
        values.put("x", encode(AdvancedGraphDocument.Value.number(vector.x())));
        values.put("y", encode(AdvancedGraphDocument.Value.number(vector.y())));
        values.put("z", encode(AdvancedGraphDocument.Value.number(vector.z())));
        return AdvancedGraphDocument.Value.map(values);
    }

    // Get the quaternion value
    private static AdvancedGraphDocument.Value quaternionValue(Quaterniondc quaternion) {
        CompoundTag values = new CompoundTag();
        values.put("x", encode(AdvancedGraphDocument.Value.number(quaternion.x())));
        values.put("y", encode(AdvancedGraphDocument.Value.number(quaternion.y())));
        values.put("z", encode(AdvancedGraphDocument.Value.number(quaternion.z())));
        values.put("w", encode(AdvancedGraphDocument.Value.number(quaternion.w())));
        return AdvancedGraphDocument.Value.map(values);
    }

    // Get the matrix value
    private static AdvancedGraphDocument.Value matrixValue(Matrix3dc matrix) {
        CompoundTag values = new CompoundTag();
        values.put("m00", encode(AdvancedGraphDocument.Value.number(matrix.m00())));
        values.put("m01", encode(AdvancedGraphDocument.Value.number(matrix.m01())));
        values.put("m02", encode(AdvancedGraphDocument.Value.number(matrix.m02())));
        values.put("m10", encode(AdvancedGraphDocument.Value.number(matrix.m10())));
        values.put("m11", encode(AdvancedGraphDocument.Value.number(matrix.m11())));
        values.put("m12", encode(AdvancedGraphDocument.Value.number(matrix.m12())));
        values.put("m20", encode(AdvancedGraphDocument.Value.number(matrix.m20())));
        values.put("m21", encode(AdvancedGraphDocument.Value.number(matrix.m21())));
        values.put("m22", encode(AdvancedGraphDocument.Value.number(matrix.m22())));
        return AdvancedGraphDocument.Value.map(values);
    }

    // Encode the contraption diagram controller compat
    private static CompoundTag encode(AdvancedGraphDocument.Value val) {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("Type", val.type());
        encoded.put("Payload", val.payload().copy());
        return encoded;
    }

    // Get the readable port types
    private static Map<String, String> readablePortTypes() {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("available", "boolean");
        ports.put("sublevel_id", "string");
        ports.put("sublevel_name", "string");
        ports.put("mass", "number");
        ports.put("inverse_mass", "number");
        ports.put("inertia_tensor", "map");
        addVectorPorts(ports, "center_of_mass");
        addVectorPorts(ports, "world_center_of_mass");
        addVectorPorts(ports, "position");
        ports.put("orientation", "map");
        ports.put("orientation_x", "number");
        ports.put("orientation_y", "number");
        ports.put("orientation_z", "number");
        ports.put("orientation_w", "number");
        addVectorPorts(ports, "linear_velocity");
        ports.put("speed", "number");
        addVectorPorts(ports, "angular_velocity");
        ports.put("angular_speed", "number");
        addVectorPorts(ports, "total_force");
        ports.put("force_magnitude", "number");
        addVectorPorts(ports, "world_total_force");
        ports.put("world_force_magnitude", "number");
        addVectorPorts(ports, "total_torque");
        ports.put("torque_magnitude", "number");
        addVectorPorts(ports, "world_total_torque");
        ports.put("world_torque_magnitude", "number");
        ports.put("forces", "list");
        ports.put("force_count", "number");
        return Collections.unmodifiableMap(ports);
    }

    // Add the vector ports
    private static void addVectorPorts(Map<String, String> ports, String name) {
        ports.put(name, "map");
        ports.put(name + "_x", "number");
        ports.put(name + "_y", "number");
        ports.put(name + "_z", "number");
    }

    // Copy the contraption diagram controller compat
    private static Vector3d copy(@Nullable Vector3dc vector) {
        return vector == null ? new Vector3d() : new Vector3d(vector);
    }

    // Copy the contraption diagram controller compat
    private static Quaterniond copy(@Nullable Quaterniondc quaternion) {
        return quaternion == null ? new Quaterniond() : new Quaterniond(quaternion);
    }

    // Copy the contraption diagram controller compat
    private static Matrix3d copy(@Nullable Matrix3dc matrix) {
        return matrix == null ? new Matrix3d() : new Matrix3d(matrix);
    }

    // Normalize the value to a finite result
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Store the cached snapshot
    private record CachedSnapshot(long gameTime, Snapshot snapshot) {
    }

    // Store the force sample
    private record ForceSample(String groupId, String groupName, int color,
                               Vector3d point, Vector3d force,
                               Vector3d worldPoint, Vector3d worldForce) {
    }

    // Store the snapshot
    private record Snapshot(boolean available,
                            @Nullable UUID subLevelId,
                            String subLevelName,
                            double mass,
                            double inverseMass,
                            Matrix3d inertiaTensor,
                            Vector3d centerOfMass,
                            Vector3d worldCenterOfMass,
                            Vector3d position,
                            Quaterniond orientation,
                            Vector3d linearVelocity,
                            Vector3d angularVelocity,
                            Vector3d totalForce,
                            Vector3d worldTotalForce,
                            Vector3d totalTorque,
                            Vector3d worldTotalTorque,
                            List<ForceSample> forces) {
        // Create an unavailable snapshot
        private static Snapshot unavailable(@Nullable UUID subLevelId) {
            return new Snapshot(
                    false,
                    subLevelId,
                    "",
                    0.0D,
                    0.0D,
                    new Matrix3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Quaterniond(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    new Vector3d(),
                    List.of());
        }
    }
}
