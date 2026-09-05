package com.rieno.gadgetsandgizmos.neoforge;

import com.rieno.gadgetsandgizmos.neoforge.network.ScmPathDebugPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Holds per-player SCM path debug subscriptions and forwards bounded live snapshots.
public final class ScmPathDebugService {
    private static final double VIEW_DISTANCE = 256.0D;
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private ScmPathDebugService() {
    }

    public static boolean isEnabled(ServerPlayer player) {
        return player != null && ENABLED_PLAYERS.contains(player.getUUID());
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            ENABLED_PLAYERS.add(player.getUUID());
        } else {
            ENABLED_PLAYERS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new ScmPathDebugPayload(false, BlockPos.ZERO,
                    "", "", false, Vec3.ZERO, Vec3.ZERO, 0, false, List.of(), List.of()));
        }
        return enabled;
    }

    public static boolean toggle(ServerPlayer player) {
        return setEnabled(player, !isEnabled(player));
    }

    public static boolean hasEnabledPlayers() {
        return !ENABLED_PLAYERS.isEmpty();
    }

    public static void publish(
            ServerLevel level,
            BlockPos controllerPos,
            String commandKey,
            String status,
            boolean groundVehicle,
            Vec3 position,
            Vec3 target,
            int activeWaypointIndex,
            boolean currentSegmentClear,
            List<Vec3> waypoints,
            List<Boolean> reverseWaypoints,
            List<PathSegment> exploredSegments
    ) {
        if (level == null || ENABLED_PLAYERS.isEmpty() || level.getGameTime() % 2L != 0L) {
            return;
        }
        Vec3 safePosition = position == null ? Vec3.ZERO : position;
        List<ScmPathDebugPayload.PathPoint> points = new ArrayList<>();
        if (waypoints != null) {
            for (int index = 0; index < waypoints.size() && index < 128; index++) {
                boolean reverse = reverseWaypoints != null && index < reverseWaypoints.size()
                        && Boolean.TRUE.equals(reverseWaypoints.get(index));
                points.add(new ScmPathDebugPayload.PathPoint(waypoints.get(index), reverse));
            }
        }
        ScmPathDebugPayload payload = new ScmPathDebugPayload(true, controllerPos, commandKey, status,
                groundVehicle, safePosition, target, activeWaypointIndex, currentSegmentClear, points,
                exploredSegments == null ? List.of() : exploredSegments.stream()
                        .limit(128)
                        .map(segment -> new ScmPathDebugPayload.PathSegment(
                                segment.from(), segment.to(), segment.clear()))
                        .toList());
        for (ServerPlayer player : level.players()) {
            if (ENABLED_PLAYERS.contains(player.getUUID())
                    && player.position().closerThan(safePosition, VIEW_DISTANCE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // A collision-checked planner segment retained only for the debug overlay.
    public record PathSegment(Vec3 from, Vec3 to, boolean clear) {
        public PathSegment {
            from = from == null ? Vec3.ZERO : from;
            to = to == null ? Vec3.ZERO : to;
        }
    }
}
