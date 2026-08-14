package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Limit the short collision bypass used to release a stuck rope winch assembly
public final class RopeWinchUnstickWindow {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long WINDOW_TICKS = 2L;
    private static final Map<UUID, UnstickWindow> WINDOWS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope winch unstick window
    private RopeWinchUnstickWindow() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Activate the rope winch unstick window
    public static void activate(Player player, ClawBlockEntity claw) {
        Level level = player.level();
        if (level == null) {
            return;
        }

        Set<UUID> subLevelIds = new LinkedHashSet<>();
        UUID clawSubLevelId = SimulatedHelper.getContainingSubLevelId(claw);
        if (clawSubLevelId != null) {
            subLevelIds.add(clawSubLevelId);
        }

        UUID heldSubLevelId = claw.getGrabbedConnectorSubLevelId();
        if (heldSubLevelId != null) {
            subLevelIds.add(heldSubLevelId);
        }

        if (subLevelIds.isEmpty()) {
            return;
        }

        long expiresAt = level.getGameTime() + WINDOW_TICKS;
        WINDOWS.compute(player.getUUID(), (uuid, existing) -> {
            if (existing == null || existing.expiresAt < level.getGameTime()) {
                return new UnstickWindow(expiresAt, subLevelIds);
            }
            Set<UUID> merged = new LinkedHashSet<>(existing.subLevelIds);
            merged.addAll(subLevelIds);
            return new UnstickWindow(Math.max(existing.expiresAt, expiresAt), merged);
        });
    }

    // Check if this should ignore
    public static boolean shouldIgnore(Entity entity, @Nullable SubLevel subLevel) {
        if (!(entity instanceof Player player) || subLevel == null) {
            return false;
        }

        Level level = player.level();
        UnstickWindow window = WINDOWS.get(player.getUUID());
        if (level == null || window == null) {
            return false;
        }
        if (level.getGameTime() >= window.expiresAt) {
            WINDOWS.remove(player.getUUID(), window);
            return false;
        }

        UUID subLevelId = subLevel.getUniqueId();
        return subLevelId != null && window.subLevelIds.contains(subLevelId);
    }

    // Filter the rope winch unstick window
    public static Iterable<SubLevel> filter(Entity entity, Iterable<SubLevel> intersecting) {
        if (!(entity instanceof Player)) {
            return intersecting;
        }

        List<SubLevel> filtered = new ArrayList<>();
        for (SubLevel subLevel : intersecting) {
            if (!shouldIgnore(entity, subLevel)) {
                filtered.add(subLevel);
            }
        }
        return filtered;
    }

    // Store the unstick window
    private record UnstickWindow(long expiresAt, Set<UUID> subLevelIds) {
    }
}
