package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.neoforge.network.TeleportFrameResetPayload;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class TeleportFrameResetClient {
    private TeleportFrameResetClient() {
    }

    public static void handle(TeleportFrameResetPayload payload) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.level().dimension().location().equals(payload.dimension())) {
            clear(player);
        }
    }

    public static void clear(Player player) {
        if (player instanceof EntityMovementExtension movement) {
            movement.sable$setTrackingSubLevel(null);
            movement.sable$setLastTrackingSubLevelID(null);
        }
        if (player instanceof EntityStickExtension stick) stick.sable$setPlotPosition(null);
        if (player instanceof LivingEntityMovementExtension inherited) {
            inherited.sable$getInheritedVelocity().zero();
        }
        player.setDeltaMovement(Vec3.ZERO);
    }
}
