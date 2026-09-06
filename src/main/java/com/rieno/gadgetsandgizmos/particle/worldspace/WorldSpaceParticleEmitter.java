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
        worldLevel.addParticle(options,
                worldPosition.x, worldPosition.y, worldPosition.z,
                worldVelocity.x, worldVelocity.y, worldVelocity.z);
    }
}
