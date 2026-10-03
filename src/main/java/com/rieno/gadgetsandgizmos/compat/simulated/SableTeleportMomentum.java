package com.rieno.gadgetsandgizmos.compat.simulated;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.neoforge.network.TeleportFrameResetPayload;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Matches an entity's motion to the frame at its teleport destination. */
public final class SableTeleportMomentum {
    // A later teleport listener or Sable collision update can restore ship motion during this tick.
    // Hold the static destination through the entity's next complete server tick only.
    private static final Map<Entity, Long> STATIC_WORLD_ARRIVALS = Collections.synchronizedMap(new WeakHashMap<>());

    private SableTeleportMomentum() {
    }

    public record Destination(Vec3 worldPosition, Vec3 frameVelocity, boolean inSubLevel) {
    }

    public static Destination destination(ServerLevel level, Vec3 requestedPosition) {
        SubLevel subLevel = SableLevelApi.containing(level, requestedPosition);
        if (!(subLevel instanceof ServerSubLevel) || subLevel.isRemoved()) {
            return new Destination(requestedPosition, Vec3.ZERO, false);
        }
        Vec3 worldPosition = subLevel.logicalPose().transformPosition(requestedPosition);
        // Sable's point velocity includes angular motion and is measured in metres per second.
        Vec3 frameVelocity = Sable.HELPER.getVelocity(level, subLevel, requestedPosition).scale(1.0D / 20.0D);
        return new Destination(worldPosition, frameVelocity, true);
    }

    public static boolean hasMovingFrame(Entity entity, Destination destination) {
        return destination.inSubLevel() || onSubLevel(entity);
    }

    public static boolean onSubLevel(Entity entity) {
        if (SableLevelApi.tracking(entity) != null || SableLevelApi.containing(entity) != null) return true;
        // Sable's current tracking can clear between collision and teleport processing. Only use
        // its last tracked sub-level while the entity is still physically inside that ship's bounds.
        SubLevel last = Sable.HELPER.getLastTrackingSubLevel(entity);
        return last instanceof ServerSubLevel && !last.isRemoved()
                && last.boundingBox().intersects(new BoundingBox3d(entity.getBoundingBox().inflate(0.25D)));
    }

    public static DimensionTransition withPosition(DimensionTransition transition, Vec3 worldPosition) {
        return new DimensionTransition(transition.newLevel(), worldPosition, transition.speed(),
                transition.yRot(), transition.xRot(), transition.missingRespawnBlock(),
                transition.postDimensionTransition());
    }

    public static Vec3 relativeMotion(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        // Entities retained in a plot have local motion; ordinary tracked entities are in world space.
        SubLevel plot = SableLevelApi.containing(entity);
        return plot == null ? motion : plot.logicalPose().transformNormal(motion);
    }

    static Vec3 matchedVelocity(Vec3 relativeMotion, Destination destination) {
        // A teleport out of a moving sub-level must not carry motion into the static world.
        // Only destinations on a sub-level inherit that sub-level's point velocity.
        return destination.inSubLevel()
                ? relativeMotion.add(destination.frameVelocity()) : Vec3.ZERO;
    }

    public static void apply(Entity entity, Vec3 relativeMotion, Destination destination) {
        clearSourceFrame(entity);
        Vec3 velocity = matchedVelocity(relativeMotion, destination);
        if (Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)) {
            if (destination.inSubLevel()) STATIC_WORLD_ARRIVALS.remove(entity);
            else STATIC_WORLD_ARRIVALS.put(entity, entity.level().getGameTime());
            entity.setDeltaMovement(velocity);
            entity.hurtMarked = true;
            // The player position packet can reach the client before hurtMarked is sent next tick.
            // Send the final velocity immediately after the teleport packet as well.
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                if (!destination.inSubLevel()) sendClientFrameReset(player);
            }
        }
    }

    public static void beforeServerTick(Entity entity) {
        if (STATIC_WORLD_ARRIVALS.containsKey(entity)) {
            clearSourceFrame(entity);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    public static void afterServerTick(Entity entity) {
        Long arrivalTick = STATIC_WORLD_ARRIVALS.get(entity);
        if (arrivalTick == null || entity.level().getGameTime() <= arrivalTick) return;
        STATIC_WORLD_ARRIVALS.remove(entity);
        clearSourceFrame(entity);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            sendClientFrameReset(player);
        }
    }

    private static void clearSourceFrame(Entity entity) {
        if (entity instanceof EntityMovementExtension movement) {
            movement.sable$setTrackingSubLevel(null);
            movement.sable$setLastTrackingSubLevelID(null);
        }
        if (entity instanceof EntityStickExtension stick) {
            stick.sable$setPlotPosition(null);
        }
        if (entity instanceof LivingEntity && entity instanceof LivingEntityMovementExtension movement) {
            movement.sable$getInheritedVelocity().zero();
        }
    }

    private static void sendClientFrameReset(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new TeleportFrameResetPayload(player.level().dimension().location()));
    }
}
