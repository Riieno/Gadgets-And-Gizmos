package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.simulated.SableTeleportMomentum;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerConnectionTeleportMomentumMixin {
    @Shadow public ServerPlayer player;

    @WrapMethod(method = "teleport(DDDFFLjava/util/Set;)V")
    private void createthrusters$teleport(double x, double y, double z, float yaw, float pitch,
                                           Set<RelativeMovement> relativeSet, Operation<Void> original) {
        SableTeleportMomentum.Destination destination = SableTeleportMomentum.destination(
                player.serverLevel(), new Vec3(x, y, z));
        if (!SableTeleportMomentum.hasMovingFrame(player, destination)
                || player.position().distanceToSqr(destination.worldPosition()) < 1.0E-6D) {
            original.call(x, y, z, yaw, pitch, relativeSet);
            return;
        }
        Vec3 relativeMotion = SableTeleportMomentum.relativeMotion(player);
        Vec3 position = destination.worldPosition();
        original.call(position.x, position.y, position.z, yaw, pitch, relativeSet);
        SableTeleportMomentum.apply(player, relativeMotion, destination);
    }
}
