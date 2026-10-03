package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.simulated.SableTeleportMomentum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityTeleportMomentumMixin {
    @WrapMethod(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;")
    private Entity createthrusters$changeDimension(DimensionTransition transition, Operation<Entity> original) {
        Entity self = (Entity) (Object) this;
        SableTeleportMomentum.Destination destination = SableTeleportMomentum.destination(
                transition.newLevel(), transition.pos());
        if (!SableTeleportMomentum.hasMovingFrame(self, destination)) return original.call(transition);
        DimensionTransition adjusted = destination.inSubLevel()
                ? SableTeleportMomentum.withPosition(transition, destination.worldPosition()) : transition;
        Entity arrived = original.call(adjusted);
        if (arrived != null) {
            SableTeleportMomentum.apply(arrived, transition.speed(), destination);
        }
        return arrived;
    }

    @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
    private boolean createthrusters$teleportTo(ServerLevel level, double x, double y, double z,
                                                Set<RelativeMovement> relativeMovements, float yaw, float pitch,
                                                Operation<Boolean> original) {
        Entity self = (Entity) (Object) this;
        SableTeleportMomentum.Destination destination = SableTeleportMomentum.destination(level, new Vec3(x, y, z));
        if (!SableTeleportMomentum.hasMovingFrame(self, destination)) {
            return original.call(level, x, y, z, relativeMovements, yaw, pitch);
        }
        Vec3 relativeMotion = SableTeleportMomentum.relativeMotion(self);
        Vec3 position = destination.worldPosition();
        boolean teleported = original.call(level, position.x, position.y, position.z,
                relativeMovements, yaw, pitch);
        if (teleported) {
            Entity arrived = self.isRemoved() ? level.getEntity(self.getUUID()) : self;
            if (arrived != null) {
                SableTeleportMomentum.apply(arrived, relativeMotion, destination);
            }
        }
        return teleported;
    }

    @WrapMethod(method = "teleportTo(DDD)V")
    private void createthrusters$teleportTo(double x, double y, double z, Operation<Void> original) {
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            original.call(x, y, z);
            return;
        }
        SableTeleportMomentum.Destination destination = SableTeleportMomentum.destination(level, new Vec3(x, y, z));
        if (!SableTeleportMomentum.hasMovingFrame(self, destination)) {
            original.call(x, y, z);
            return;
        }
        Vec3 relativeMotion = SableTeleportMomentum.relativeMotion(self);
        Vec3 position = destination.worldPosition();
        original.call(position.x, position.y, position.z);
        SableTeleportMomentum.apply(self, relativeMotion, destination);
    }
}
