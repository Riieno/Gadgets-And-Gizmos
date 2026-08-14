package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.util.CTPropulsionTelemetry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Apply Contraption Diagram forces through nested sub-levels
@Mixin(value = DiagramEntity.class, remap = false)
public abstract class DiagramEntityNestedForceMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the child forces
    @ModifyReturnValue(
            method = "makeDiagramDataPacket(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Ldev/simulated_team/simulated/network/packets/contraption_diagram/DiagramDataPacket;",
            at = @At("RETURN")
    )
    private static DiagramDataPacket ct$appendChildForces(
            DiagramDataPacket original,
            ServerSubLevel serverSubLevel) {

        List<QueuedForceGroup.PointForce> mountForces = new ArrayList<>();
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(serverSubLevel)) {
            if (blockEntity instanceof ThrusterBearingBlockEntity bearing) {
                Vec3 localDirection = Vec3.atLowerCornerOf(bearing.getFacing().getNormal());
                Vec3 force = ct$resolveMountForceVector(bearing, bearing.getAttachedThrustersById(),
                        bearing.getAssemblyRealThrust(), localDirection);
                ct$addMountForce(mountForces, bearing.getBlockPos().getCenter(), force);
            }
        }

        if (mountForces.isEmpty()) {
            return original;
        }

        Map<ForceGroup, List<QueuedForceGroup.PointForce>> extended = ct$sanitizeForEncoding(original.forces());
        ForceGroup propulsionGroup = ct$resolvePropulsionGroup(extended);
        if (propulsionGroup == null) {
            return original;
        }

        List<QueuedForceGroup.PointForce> existing = extended.get(propulsionGroup);
        if (existing != null) {
            List<QueuedForceGroup.PointForce> merged = new ArrayList<>(existing);
            merged.addAll(mountForces);
            extended.put(propulsionGroup, merged);
        } else {
            extended.put(propulsionGroup, mountForces);
        }

        return new DiagramDataPacket(extended, original.mass());
    }

    // Sanitize nested forces for encoding
    private static Map<ForceGroup, List<QueuedForceGroup.PointForce>> ct$sanitizeForEncoding(
            Map<ForceGroup, List<QueuedForceGroup.PointForce>> src) {
        Map<ForceGroup, List<QueuedForceGroup.PointForce>> sanitized = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<ForceGroup, List<QueuedForceGroup.PointForce>> entry : src.entrySet()) {
            ForceGroup group = entry.getKey();
            if (group == null) {
                continue;
            }

            List<QueuedForceGroup.PointForce> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            List<QueuedForceGroup.PointForce> filtered = new ArrayList<>(values.size());
            for (QueuedForceGroup.PointForce pf : values) {
                if (pf == null || pf.point() == null || pf.force() == null) {
                    continue;
                }
                filtered.add(pf);
            }

            if (!filtered.isEmpty()) {
                sanitized.put(group, filtered);
            }
        }
        return sanitized;
    }

    // Add the mount force
    private static void ct$addMountForce(List<QueuedForceGroup.PointForce> target, Vec3 mountCenter, Vec3 forceVector) {
        if (mountCenter == null || forceVector == null || forceVector.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vector3dc point = new Vector3d(mountCenter.x, mountCenter.y, mountCenter.z);
        Vector3dc force = new Vector3d(forceVector.x, forceVector.y, forceVector.z);
        target.add(new QueuedForceGroup.PointForce(point, force));
    }

    // Resolve the mount force vector
    private static Vec3 ct$resolveMountForceVector(BlockEntity mount,
            Map<String, ThrusterBlockEntity> attachedThrusters,
            double assemblyRealThrust,
            Vec3 fallbackDirection) {
        Vec3 summedForce = Vec3.ZERO;
        for (ThrusterBlockEntity thruster : attachedThrusters.values()) {
            Vec3 dir = ct$resolveMountLocalThrusterDirection(mount, thruster);
            if (dir == null || dir.lengthSqr() <= 1.0E-6D) {
                continue;
            }

            double scaledThrust = thruster.getScaledThrust();
            if (Math.abs(scaledThrust) <= 1.0E-6D) {
                continue;
            }
            summedForce = summedForce.add(dir.normalize().scale(scaledThrust));
        }

        if (summedForce.lengthSqr() > 1.0E-6D) {
            return summedForce;
        }

        if (fallbackDirection == null || fallbackDirection.lengthSqr() <= 1.0E-6D) {
            return Vec3.ZERO;
        }

        double fallbackMagnitude = Math.max(0.0D, assemblyRealThrust);
        if (fallbackMagnitude <= 1.0E-6D) {
            for (ThrusterBlockEntity thruster : attachedThrusters.values()) {
                fallbackMagnitude += Math.max(0.0D, CTPropulsionTelemetry.getRealThrust(thruster));
            }
        }
        return fallbackDirection.normalize().scale(fallbackMagnitude);
    }

    // Resolve the mount local thruster direction
    private static Vec3 ct$resolveMountLocalThrusterDirection(BlockEntity mount, ThrusterBlockEntity thruster) {
        if (mount == null || thruster == null) {
            return Vec3.ZERO;
        }

        Vec3 thrusterLocal = thruster.getLocalThrustDirection();
        if (thrusterLocal == null || thrusterLocal.lengthSqr() <= 1.0E-6D) {
            return Vec3.ZERO;
        }

        Vec3 worldDirection = SimulatedHelper.toContainingWorldDirection(thruster, thrusterLocal);
        if (worldDirection == null || worldDirection.lengthSqr() <= 1.0E-6D) {
            worldDirection = thrusterLocal;
        }

        Vec3 mountLocal = SimulatedHelper.toContainingLocalDirection(mount, worldDirection);
        if (mountLocal == null || mountLocal.lengthSqr() <= 1.0E-6D) {
            mountLocal = worldDirection;
        }

        return mountLocal.normalize();
    }

    // Resolve the propulsion group
    private static ForceGroup ct$resolvePropulsionGroup(Map<ForceGroup, List<QueuedForceGroup.PointForce>> existingForces) {
        for (ForceGroup group : existingForces.keySet()) {
            if (group == null) {
                continue;
            }
            if (group.color() == 5930143) {
                return group;
            }
            Component name = group.name();
            if (name != null && name.getString().toLowerCase().contains("propulsion")) {
                return group;
            }
        }

        return ForceGroups.PROPULSION.get();
    }
}
