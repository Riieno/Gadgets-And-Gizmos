package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;

// Keep staff actions configurable while retaining Simulated's drag and lock implementation
public final class PhysicsStaffActionClient {
    private static final PhysicsStaffClientHandler.PhysicsStaffMouseHandler MOUSE = new PhysicsStaffClientHandler.PhysicsStaffMouseHandler();

    private PhysicsStaffActionClient() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.player != null && minecraft.screen == null
                && PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(minecraft.player);
        boolean drag = CTActionKeys.STAFF_DRAG.sample(active).wasPressed(1);
        boolean lock = CTActionKeys.STAFF_LOCK.sample(active).wasPressed(1);
        if (!active) return;
        if (lock) SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.onItemUsed(PhysicsStaffAction.LOCK);
        boolean placing = isPlacementUse()
                && minecraft.options.keyUse.getKey().equals(CTActionKeys.STAFF_DRAG.mapping().getKey());
        if (drag && !placing) {
            SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.onItemUsed(PhysicsStaffAction.START_DRAG);
        }
        if (SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.getDragSession() != null) {
            double direction = (CTActionKeys.STAFF_PUSH.isHeld() ? 1.0D : 0.0D)
                    - (CTActionKeys.STAFF_PULL.isHeld() ? 1.0D : 0.0D);
            if (direction != 0) MOUSE.onScroll(0, direction * 0.1D);
        }
    }

    public static boolean isPlacementUse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hit)) return false;
        return PhysicsStaffItem.canPlaceAnchor(minecraft.player, minecraft.player.level(),
                hit.getBlockPos().relative(hit.getDirection()));
    }

    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(minecraft.player)
                || !event.isAttack() && !event.isUseItem() || event.isUseItem() && isPlacementUse()) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}
