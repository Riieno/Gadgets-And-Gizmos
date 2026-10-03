package com.rieno.gadgetsandgizmos.content.tablet;

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.registry.CTParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

// Emit a bounded soft smoke plume where a successfully archived assembly stood
final class DigisableStorageEffects{
    private DigisableStorageEffects(){}

    // Retain world bounds before native sublevels are removed
    static List<AABB> capture(ServerLevel level, UUID rootId){
        return SableAssemblyTopologyApi.discover(level, rootId).loadedBodies().stream()
                .map(body -> body.boundingBox())
                .filter(box -> box != null
                        && Double.isFinite(box.minX()) && Double.isFinite(box.minY()) && Double.isFinite(box.minZ())
                        && Double.isFinite(box.maxX()) && Double.isFinite(box.maxY()) && Double.isFinite(box.maxZ()))
                .map(box -> new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                .toList();
    }

    // Spread smoke over the previous occupied volume without unbounded particle traffic
    static void emit(ServerLevel level, List<AABB> bounds){
        if(bounds.isEmpty()) return;
        int stride = Math.max(1, (bounds.size() + 31) / 32);
        int selected = (bounds.size() + stride - 1) / stride;
        int clusters = Math.min(12, Math.max(1, 48 / selected));
        int particles = Math.max(8, 256 / (selected * clusters));
        for(int idx = 0; idx < bounds.size(); idx += stride){
            AABB box = bounds.get(idx);
            for(int cluster = 0; cluster < clusters; cluster++){
                double x = box.minX + level.random.nextDouble() * box.getXsize();
                double y = box.minY + level.random.nextDouble() * box.getYsize();
                double z = box.minZ + level.random.nextDouble() * box.getZsize();
                level.sendParticles(CTParticles.DIGISABLE_SMOKE.get(), x, y, z, particles,
                        0.55D, 0.55D, 0.55D, 0.35D);
            }
        }
    }
}
