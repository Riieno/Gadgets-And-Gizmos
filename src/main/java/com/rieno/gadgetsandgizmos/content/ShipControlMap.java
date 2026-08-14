package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Store the calibrated propulsion, bearing and display layout the SCM uses to control a ship
public record ShipControlMap(
        UUID id,
        String dimension,
        UUID rootSubLevelId,
        BlockPos controllerPosition,
        Vec3 centerOfMass,
        List<PropulsionUnit> units,
        List<BearingUnit> bearings,
        List<DockingConnector> dockingConnectors,
        List<CrnDisplay> crnDisplays,
        List<AccDisplay> accDisplays,
        long updatedAt
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship control map
    public ShipControlMap {
        dimension = dimension == null ? "" : dimension;
        controllerPosition = controllerPosition == null ? BlockPos.ZERO : controllerPosition.immutable();
        centerOfMass = centerOfMass == null ? Vec3.ZERO : centerOfMass;
        units = units == null ? List.of() : List.copyOf(units);
        bearings = bearings == null ? List.of() : List.copyOf(bearings);
        dockingConnectors = dockingConnectors == null
                ? List.of() : List.copyOf(dockingConnectors);
        crnDisplays = crnDisplays == null
                ? List.of()
                : crnDisplays.stream().filter(java.util.Objects::nonNull).distinct().toList();
        accDisplays = accDisplays == null
                ? List.of()
                : accDisplays.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    // Initialize the ship control map
    public ShipControlMap(
            UUID id,
            String dimension,
            UUID rootSubLevelId,
            BlockPos controllerPosition,
            Vec3 centerOfMass,
            List<PropulsionUnit> units,
            List<BearingUnit> bearings,
            List<DockingConnector> dockingConnectors,
            List<CrnDisplay> crnDisplays,
            long updatedAt
    ) {
        this(id, dimension, rootSubLevelId, controllerPosition, centerOfMass,
                units, bearings, dockingConnectors, crnDisplays, List.of(), updatedAt);
    }

    // Initialize the ship control map
    public ShipControlMap(
            UUID id,
            String dimension,
            UUID rootSubLevelId,
            BlockPos controllerPosition,
            Vec3 centerOfMass,
            List<PropulsionUnit> units,
            List<BearingUnit> bearings,
            List<DockingConnector> dockingConnectors,
            long updatedAt
    ) {
        this(id, dimension, rootSubLevelId, controllerPosition, centerOfMass,
                units, bearings, dockingConnectors, List.of(), List.of(), updatedAt);
    }

    // Initialize the ship control map
    public ShipControlMap(
            UUID id,
            String dimension,
            UUID rootSubLevelId,
            BlockPos controllerPosition,
            Vec3 centerOfMass,
            List<PropulsionUnit> units,
            List<BearingUnit> bearings,
            long updatedAt
    ) {
        this(id, dimension, rootSubLevelId, controllerPosition, centerOfMass,
                units, bearings, List.of(), List.of(), List.of(), updatedAt);
    }

    // Initialize the ship control map
    public ShipControlMap(
            UUID id,
            String dimension,
            UUID rootSubLevelId,
            BlockPos controllerPosition,
            Vec3 centerOfMass,
            List<PropulsionUnit> units,
            long updatedAt
    ) {
        this(id, dimension, rootSubLevelId, controllerPosition, centerOfMass,
                units, List.of(), List.of(), List.of(), List.of(), updatedAt);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the controllable unit count
    public int controllableUnitCount() {
        return (int) units.stream().filter(PropulsionUnit::controllable).count();
    }

    // Store the docking connector
    public record DockingConnector(
            int index,
            UUID subLevelId,
            BlockPos blockPosition,
            Vec3 rootTipPosition,
            Vec3 rootFacing
    ) {
        // Initialize the docking connector
        public DockingConnector {
            blockPosition = blockPosition == null
                    ? BlockPos.ZERO : blockPosition.immutable();
            rootTipPosition = rootTipPosition == null ? Vec3.ZERO : rootTipPosition;
            rootFacing = normalize(rootFacing);
        }
    }

    // Store a CRN display using its ship-local address so movement cannot break the link
    public record CrnDisplay(UUID subLevelId, BlockPos blockPosition) {
        // Initialize the CRN display
        public CrnDisplay {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
        }
    }

    // Store the ACC display network root found while the SCM maps the ship
    public record AccDisplay(UUID subLevelId, BlockPos blockPosition) {
        // Initialize the ACC display
        public AccDisplay {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
        }
    }

    // Store the bearing unit
    public record BearingUnit(
            int index,
            UUID hostSubLevelId,
            BlockPos blockPosition,
            String blockId,
            String adapter,
            List<UUID> childSubLevelIds,
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            List<BearingPose> poses
    ) {
        // Initialize the bearing unit
        public BearingUnit {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            adapter = adapter == null ? "" : adapter;
            childSubLevelIds = childSubLevelIds == null
                    ? List.of()
                    : childSubLevelIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            minX = finite(minX);
            maxX = Math.max(minX, finite(maxX));
            minZ = finite(minZ);
            maxZ = Math.max(minZ, finite(maxZ));
            poses = poses == null ? List.of() : List.copyOf(poses);
        }

        // Get the propulsion unit indices
        public List<Integer> propulsionUnitIndices() {
            return poses.stream()
                    .flatMap(pose -> pose.responses().stream())
                    .map(BearingResponse::propulsionUnitIndex)
                    .distinct()
                    .toList();
        }
    }

    // Store the bearing pose
    public record BearingPose(
            double angleX,
            double angleZ,
            List<BearingResponse> responses,
            List<AerodynamicSurface> aerodynamicSurfaces,
            double maxAerodynamicForce,
            double maxAerodynamicTorque
    ) {
        // Initialize the bearing pose
        public BearingPose(
                double angleX,
                double angleZ,
                List<BearingResponse> responses
        ) {
            this(angleX, angleZ, responses, List.of(), 0.0D, 0.0D);
        }

        // Initialize the bearing pose
        public BearingPose {
            angleX = finite(angleX);
            angleZ = finite(angleZ);
            responses = responses == null ? List.of() : List.copyOf(responses);
            aerodynamicSurfaces = aerodynamicSurfaces == null
                    ? List.of() : List.copyOf(aerodynamicSurfaces);
            maxAerodynamicForce = Math.max(0.0D, finite(maxAerodynamicForce));
            maxAerodynamicTorque = Math.max(0.0D, finite(maxAerodynamicTorque));
        }
    }

    // Store the aerodynamic surface
    public record AerodynamicSurface(
            int surfaceIndex,
            UUID subLevelId,
            BlockPos blockPosition,
            String blockId,
            Vec3 rootPosition,
            Vec3 normal,
            double parallelDragScalar,
            double directionlessDragScalar,
            double liftScalar
    ) {
        // Initialize the aerodynamic surface
        public AerodynamicSurface {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            rootPosition = rootPosition == null ? Vec3.ZERO : rootPosition;
            normal = normalize(normal);
            parallelDragScalar = Math.max(0.0D, finite(parallelDragScalar));
            directionlessDragScalar = Math.max(0.0D, finite(directionlessDragScalar));
            liftScalar = Math.max(0.0D, finite(liftScalar));
        }
    }

    // Store the bearing response
    public record BearingResponse(
            int propulsionUnitIndex,
            Vec3 rootPosition,
            Vec3 forceDirection,
            double maxThrust
    ) {
        // Initialize the bearing response
        public BearingResponse {
            rootPosition = rootPosition == null ? Vec3.ZERO : rootPosition;
            forceDirection = normalize(forceDirection);
            maxThrust = Math.max(0.0D, finite(maxThrust));
        }
    }

    // Store the propulsion unit
    public record PropulsionUnit(
            int index,
            UUID subLevelId,
            BlockPos blockPosition,
            String blockId,
            String adapter,
            boolean controllable,
            Vec3 rootPosition,
            Vec3 forceDirection,
            double minControl,
            double maxControl,
            double minThrust,
            double maxThrust,
            double maxSpeed,
            List<CalibrationSample> samples
    ) {
        // Initialize the propulsion unit
        public PropulsionUnit {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            adapter = adapter == null ? "passive" : adapter;
            rootPosition = rootPosition == null ? Vec3.ZERO : rootPosition;
            forceDirection = normalize(forceDirection);
            minControl = finite(minControl);
            maxControl = Math.max(minControl, finite(maxControl));
            minThrust = Math.max(0.0D, finite(minThrust));
            maxThrust = Math.max(minThrust, finite(maxThrust));
            maxSpeed = Math.max(0.0D, finite(maxSpeed));
            samples = samples == null ? List.of() : List.copyOf(samples);
        }

        // Get the propulsion unit torque direction
        public Vec3 torqueDirection(Vec3 centerOfMass) {
            Vec3 force = forceDirection.scale(maxThrust);
            return rootPosition.subtract(centerOfMass).cross(force);
        }
    }

    // Store the calibration sample
    public record CalibrationSample(
            double minControl,
            double maxControl,
            double control,
            double speed,
            double thrust,
            boolean active
    ) {
        // Initialize the calibration sample
        public CalibrationSample(double control, double speed, double thrust, boolean active) {
            this(0.0D, 1.0D, control, speed, thrust, active);
        }

        // Initialize the calibration sample
        public CalibrationSample {
            minControl = Math.max(0.0D, finite(minControl));
            maxControl = Math.max(minControl, finite(maxControl));
            control = finite(control);
            speed = finite(speed);
            thrust = finite(thrust);
        }
    }

    // Normalize the ship control map
    private static Vec3 normalize(@Nullable Vec3 val) {
        return val == null || val.lengthSqr() < 1.0E-12D ? Vec3.ZERO : val.normalize();
    }

    // Normalize the value to a finite result
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }
}
