package com.rieno.gadgetsandgizmos.particle.worldspace;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Spawn an emitter's local particle in the root client world after resolving its Sable pose.
public final class WorldSpaceParticleEmitter {
    private static ParticleSpawner viewDistanceSpawner;
    private static ViewCoverageProbe viewCoverageProbe;

    // Accept a client particle spawner without loading client classes on the server
    public static void installViewDistanceSpawner(ParticleSpawner spawner) {
        viewDistanceSpawner = spawner;
    }

    // Accept a client view coverage probe without loading client classes on the server
    public static void installViewCoverageProbe(ViewCoverageProbe probe) {
        viewCoverageProbe = probe;
    }


    // Pass world space particles to the client renderer
    @FunctionalInterface
    public interface ParticleSpawner {
        void add(Level level, ParticleOptions options, Vec3 position, Vec3 velocity);
    }

    @FunctionalInterface
    public interface ViewCoverageProbe {
        double projectedWidth(Level level, Vec3 start, Vec3 end, double radius);
    }


    private WorldSpaceParticleEmitter() {
    }

    public static @Nullable Level resolveWorldLevel(@Nullable BlockEntity emitter) {
        if (emitter == null || emitter.getLevel() == null) {
            return null;
        }
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(emitter);
        if (containingSubLevel instanceof SubLevel sableSubLevel) {
            return sableSubLevel.getLevel();
        }
        return emitter.getLevel();
    }

    public static void addParticle(
            @Nullable BlockEntity emitter,
            ParticleOptions options,
            Vec3 localPosition,
            Vec3 localVelocity
    ) {
        addParticle(emitter, options, localPosition, localVelocity, false);
    }

    // Spawn a transformed particle within the client's view distance
    public static void addParticleWithinViewDistance(
            @Nullable BlockEntity emitter,
            ParticleOptions options,
            Vec3 localPosition,
            Vec3 localVelocity
    ) {
        addParticle(emitter, options, localPosition, localVelocity, true);
    }

    // Estimate the visible width of a transformed particle trail
    public static double projectedWidth(
            @Nullable BlockEntity emitter, Vec3 localStart, Vec3 localEnd, double radius
    ) {
        if (emitter == null || localStart == null || localEnd == null || viewCoverageProbe == null) return 0.0D;
        Level worldLevel = resolveWorldLevel(emitter);
        if (worldLevel == null || !worldLevel.isClientSide) return 0.0D;
        Vec3 start = SimulatedHelper.toGlobalWorldPosition(emitter, localStart);
        Vec3 end = SimulatedHelper.toGlobalWorldPosition(emitter, localEnd);
        if (start == null || end == null) return 0.0D;
        return viewCoverageProbe.projectedWidth(worldLevel, start, end, radius);
    }


    // Transform the local position and velocity before spawning
    private static void addParticle(
            @Nullable BlockEntity emitter,
            ParticleOptions options,
            Vec3 localPosition,
            Vec3 localVelocity,
            boolean viewDistance
    ) {
        if (emitter == null || options == null || localPosition == null || localVelocity == null) {
            return;
        }
        Level worldLevel = resolveWorldLevel(emitter);
        if (worldLevel == null || !worldLevel.isClientSide) {
            return;
        }

        Vec3 worldPosition = SimulatedHelper.toGlobalWorldPosition(emitter, localPosition);
        Vec3 worldVelocityEnd = SimulatedHelper.toGlobalWorldPosition(
                emitter, localPosition.add(localVelocity));
        if (worldPosition == null || worldVelocityEnd == null) {
            return;
        }
        Vec3 worldVelocity = worldVelocityEnd.subtract(worldPosition);
        if (viewDistance && viewDistanceSpawner != null) {
            viewDistanceSpawner.add(worldLevel, options, worldPosition, worldVelocity);
            return;
        }
        worldLevel.addParticle(options,
                worldPosition.x, worldPosition.y, worldPosition.z,
                worldVelocity.x, worldVelocity.y, worldVelocity.z);
    }
}
