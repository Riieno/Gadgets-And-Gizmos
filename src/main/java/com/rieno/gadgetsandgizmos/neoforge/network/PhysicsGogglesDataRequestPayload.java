package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Request one bounded physics snapshot for the goggles overlay
public record PhysicsGogglesDataRequestPayload(UUID subLevelId) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation FALLBACK_PROPULSION_ID = ResourceLocation.fromNamespaceAndPath("sable", "propulsion");
    private static final ResourceLocation FALLBACK_GRAVITY_ID = ResourceLocation.fromNamespaceAndPath("sable", "gravity");
    private static final int PROPULSION_COLOR = 0x5A7DFF;
    private static final int GRAVITY_COLOR = 0x216ED5;

    public static final Type<PhysicsGogglesDataRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "physics_goggles_data_request"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, PhysicsGogglesDataRequestPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(PhysicsGogglesDataRequestPayload::encode, PhysicsGogglesDataRequestPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the physics goggles data request
    public static void handle(PhysicsGogglesDataRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            Object resolved = SubLevelBlockEntityCollector.getSubLevel(serverPlayer.level(), payload.subLevelId());
            if (!(resolved instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) {
                PacketDistributor.sendToPlayer(serverPlayer, new PhysicsGogglesDataPayload(
                        payload.subLevelId(), 0.0D, PhysicsGogglesDataPayload.Bounds.empty(), List.of()));
                return;
            }

            PacketDistributor.sendToPlayer(serverPlayer, collectData(subLevel, serverPlayer.level(), payload.subLevelId()));
        });
    }

    // Collect the data
    private static PhysicsGogglesDataPayload collectData(ServerSubLevel subLevel, Level level, UUID fallbackId) {
        List<PhysicsGogglesDataPayload.ForceVector> forces = new ArrayList<>();
        BoundsBuilder bounds = new BoundsBuilder();
        subLevel.enableIndividualQueuedForcesTracking(true);
        collectRecordedQueuedForces(subLevel, level, forces, bounds);
        boolean hasPropulsionForces = containsForceGroup(forces, FALLBACK_PROPULSION_ID);

        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            bounds.include(blockEntity.getBlockPos());
            if (hasPropulsionForces) {
                continue;
            }
            if (!(blockEntity instanceof BlockEntityPropeller propeller) || !propeller.isActive()) {
                continue;
            }

            Vec3 dir = getPropulsionDirection(blockEntity, propeller);
            double scaledThrust = propeller.getScaledThrust();
            if (dir.lengthSqr() < 1.0E-6D || Math.abs(scaledThrust) < 1.0E-6D) {
                continue;
            }

            Vec3 force = dir.normalize().scale(scaledThrust);
            BlockPos pos = propeller.getBlockPos();
            bounds.include(pos);
            forces.add(new PhysicsGogglesDataPayload.ForceVector(
                    FALLBACK_PROPULSION_ID,
                    PROPULSION_COLOR,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    force.x(),
                    force.y(),
                    force.z()));
        }

        MassData massData = subLevel.getMassTracker();
        double mass = massData == null ? 0.0D : massData.getMass();
        Vector3dc centerOfMass = massData == null ? null : massData.getCenterOfMass();
        if (centerOfMass != null && mass > 0.0D && !containsForceGroup(forces, FALLBACK_GRAVITY_ID)) {
            bounds.include(centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
            Vector3d gravity = DimensionPhysicsData.getGravity(level, centerOfMass).mul(mass);
            forces.add(new PhysicsGogglesDataPayload.ForceVector(
                    FALLBACK_GRAVITY_ID,
                    GRAVITY_COLOR,
                    centerOfMass.x(),
                    centerOfMass.y(),
                    centerOfMass.z(),
                    gravity.x(),
                    gravity.y(),
                    gravity.z()));
        }

        UUID uniqueId = subLevel.getUniqueId();
        return new PhysicsGogglesDataPayload(uniqueId == null ? fallbackId : uniqueId,
                mass, bounds.build(), forces);
    }

    // Check if this contains force group
    private static boolean containsForceGroup(List<PhysicsGogglesDataPayload.ForceVector> forces, ResourceLocation groupId) {
        for (PhysicsGogglesDataPayload.ForceVector force : forces) {
            if (groupId.equals(force.groupId())) {
                return true;
            }
        }
        return false;
    }

    // Collect the recorded queued forces
    private static int collectRecordedQueuedForces(ServerSubLevel subLevel, Level level,
                                                   List<PhysicsGogglesDataPayload.ForceVector> forces,
                                                   BoundsBuilder bounds) {
        Map<ForceGroup, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups == null || queuedForceGroups.isEmpty()) {
            return 0;
        }

        double timeStep = physicsTimeStep(level);
        int count = 0;
        for (Map.Entry<ForceGroup, QueuedForceGroup> entry : queuedForceGroups.entrySet()) {
            ForceGroup forceGroup = entry.getKey();
            QueuedForceGroup queuedForceGroup = entry.getValue();
            ResourceLocation groupId = ForceGroups.REGISTRY.getKey(forceGroup);
            if (groupId == null || forceGroup == null || queuedForceGroup == null) {
                continue;
            }
            int col = forceGroup.color();
            for (QueuedForceGroup.PointForce pointForce : queuedForceGroup.getRecordedPointForces()) {
                if (pointForce == null || pointForce.point() == null || pointForce.force() == null) {
                    continue;
                }
                Vector3dc pointVector = pointForce.point();
                Vector3dc forceVector = pointForce.force();
                bounds.include(pointVector.x(), pointVector.y(), pointVector.z());
                forces.add(new PhysicsGogglesDataPayload.ForceVector(
                        groupId,
                        col,
                        pointVector.x(),
                        pointVector.y(),
                        pointVector.z(),
                        forceVector.x() / timeStep,
                        forceVector.y() / timeStep,
                        forceVector.z() / timeStep));
                count++;
            }
        }
        return count;
    }

    // Get the propulsion direction
    private static Vec3 getPropulsionDirection(BlockEntity blockEntity, BlockEntityPropeller propeller) {
        if (blockEntity instanceof ThrusterBlockEntity thruster) {
            return thruster.getLocalThrustDirection();
        }

        Direction dir = propeller.getBlockDirection();
        return dir == null ? Vec3.ZERO : Vec3.atLowerCornerOf((Vec3i) dir.getNormal());
    }

    // Get the physics time step
    private static double physicsTimeStep(Level level) {
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        int substeps = physicsSystem == null ? 1 : Math.max(1, physicsSystem.getConfig().substepsPerTick);
        return 0.05D / substeps;
    }

    // Handle the bounds builder
    private static final class BoundsBuilder {
        // Tracks whether bounds builder is initialized
        private boolean initialized;
        // Minimum x
        private double minX;
        // Minimum y
        private double minY;
        // Minimum z
        private double minZ;
        // Maximum x
        private double maxX;
        // Maximum y
        private double maxY;
        // Maximum z
        private double maxZ;

        // Include the bounds builder
        private void include(BlockPos pos) {
            include(pos.getX(), pos.getY(), pos.getZ());
            include(pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        }

        // Include the bounds builder
        private void include(double x, double y, double z) {
            if (!initialized) {
                initialized = true;
                minX = maxX = x;
                minY = maxY = y;
                minZ = maxZ = z;
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        // Build the bounds builder
        private PhysicsGogglesDataPayload.Bounds build() {
            return initialized
                    ? new PhysicsGogglesDataPayload.Bounds(minX, minY, minZ, maxX, maxY, maxZ)
                    : PhysicsGogglesDataPayload.Bounds.empty();
        }
    }

    // Encode the physics goggles data request
    private static void encode(RegistryFriendlyByteBuf buffer, PhysicsGogglesDataRequestPayload payload) {
        buffer.writeUUID(payload.subLevelId());
    }

    // Decode the physics goggles data request
    private static PhysicsGogglesDataRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PhysicsGogglesDataRequestPayload(buffer.readUUID());
    }
}
