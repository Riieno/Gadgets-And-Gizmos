package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelLink;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGantryBeltWheelShearPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// Rendered belts have no block collider; keep their visible endpoints for shears picking.
public final class PhysicsGantryBeltWheelShearsClient {
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static final Map<LinkKey, VisibleBelt> VISIBLE = new HashMap<>();
    private static int recordsSinceCleanup;

    private PhysicsGantryBeltWheelShearsClient() {}

    public static void record(PhysicsGantryBeltWheelBlockEntity first,
                              PhysicsGantryBeltWheelBlockEntity second, Vec3 start, Vec3 end) {
        if (first.getLevel() == null || second.getLevel() == null) return;
        var key = new LinkKey(first.getBlockPos(), SimulatedHelper.getContainingSubLevelId(first),
                second.getBlockPos(), SimulatedHelper.getContainingSubLevelId(second));
        long now = System.nanoTime();
        VISIBLE.put(key, new VisibleBelt(Minecraft.getInstance().level, start, end, now));
        if (++recordsSinceCleanup >= 128) {
            recordsSinceCleanup = 0;
            VISIBLE.values().removeIf(belt -> now - belt.seenAt() > MAX_AGE_NANOS);
        }
    }

    public static void clear() {
        VISIBLE.clear();
        recordsSinceCleanup = 0;
    }

    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        var hand = event.getHand();
        var stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ShearsItem) && !stack.is(Tags.Items.TOOLS_SHEAR)) return;

        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 view = minecraft.player.getViewVector(1.0F);
        double reach = minecraft.player.blockInteractionRange();
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, eye.distanceTo(minecraft.hitResult.getLocation()) + 0.05D);
        }
        long now = System.nanoTime();
        PhysicsGantryBeltWheelLink.BeltHit selected = null;
        LinkKey selectedKey = null;
        Iterator<Map.Entry<LinkKey, VisibleBelt>> iterator = VISIBLE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            VisibleBelt belt = entry.getValue();
            if (belt.level() != minecraft.level || now - belt.seenAt() > MAX_AGE_NANOS) {
                iterator.remove();
                continue;
            }
            var hit = PhysicsGantryBeltWheelLink.pickBelt(eye, view, reach, belt.start(), belt.end());
            if (hit != null && (selected == null || hit.rayDistance() < selected.rayDistance())) {
                selected = hit;
                selectedKey = entry.getKey();
            }
        }
        if (selectedKey == null) return;
        PacketDistributor.sendToServer(new PhysicsGantryBeltWheelShearPayload(selectedKey.first(),
                selectedKey.firstLevel(), selectedKey.second(), selectedKey.secondLevel(), hand));
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private record LinkKey(BlockPos first, UUID firstLevel, BlockPos second, UUID secondLevel) {}
    private record VisibleBelt(net.minecraft.world.level.Level level, Vec3 start, Vec3 end, long seenAt) {}
}
