package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// Sends a bounded, read-only SCM navigation snapshot to an opted-in debug client.
public record ScmPathDebugPayload(
        boolean enabled,
        BlockPos controllerPos,
        String commandKey,
        String status,
        boolean groundVehicle,
        Vec3 position,
        Vec3 target,
        int activeWaypointIndex,
        boolean currentSegmentClear,
        List<PathPoint> waypoints,
        List<PathSegment> exploredSegments
) implements CustomPacketPayload {
    private static final int MAX_WAYPOINTS = 128;
    public static final Type<ScmPathDebugPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "scm_path_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScmPathDebugPayload> STREAM_CODEC =
            StreamCodec.of(ScmPathDebugPayload::encode, ScmPathDebugPayload::decode);

    public ScmPathDebugPayload {
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        commandKey = commandKey == null ? "" : commandKey;
        status = status == null ? "" : status;
        position = position == null ? Vec3.ZERO : position;
        target = target == null ? Vec3.ZERO : target;
        activeWaypointIndex = Math.max(0, activeWaypointIndex);
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints.subList(0,
                Math.min(waypoints.size(), MAX_WAYPOINTS)));
        exploredSegments = exploredSegments == null ? List.of() : List.copyOf(exploredSegments.subList(0,
                Math.min(exploredSegments.size(), MAX_WAYPOINTS)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScmPathDebugPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> renderer = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.ScmPathDebugRenderer");
                renderer.getMethod("apply", ScmPathDebugPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ScmPathDebugPayload payload) {
        buffer.writeBoolean(payload.enabled());
        BlockPos.STREAM_CODEC.encode(buffer, payload.controllerPos());
        buffer.writeUtf(payload.commandKey(), 192);
        buffer.writeUtf(payload.status(), 256);
        buffer.writeBoolean(payload.groundVehicle());
        writeVec3(buffer, payload.position());
        writeVec3(buffer, payload.target());
        buffer.writeVarInt(payload.activeWaypointIndex());
        buffer.writeBoolean(payload.currentSegmentClear());
        buffer.writeVarInt(payload.waypoints().size());
        for (PathPoint waypoint : payload.waypoints()) {
            writeVec3(buffer, waypoint.position());
            buffer.writeBoolean(waypoint.reverse());
        }
        buffer.writeVarInt(payload.exploredSegments().size());
        for (PathSegment segment : payload.exploredSegments()) {
            writeVec3(buffer, segment.from());
            writeVec3(buffer, segment.to());
            buffer.writeBoolean(segment.clear());
        }
    }

    private static ScmPathDebugPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        BlockPos controllerPos = BlockPos.STREAM_CODEC.decode(buffer);
        String commandKey = buffer.readUtf(192);
        String status = buffer.readUtf(256);
        boolean groundVehicle = buffer.readBoolean();
        Vec3 position = readVec3(buffer);
        Vec3 target = readVec3(buffer);
        int activeWaypointIndex = buffer.readVarInt();
        boolean currentSegmentClear = buffer.readBoolean();
        int count = Math.min(MAX_WAYPOINTS, Math.max(0, buffer.readVarInt()));
        List<PathPoint> waypoints = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            waypoints.add(new PathPoint(readVec3(buffer), buffer.readBoolean()));
        }
        int exploredCount = Math.min(MAX_WAYPOINTS, Math.max(0, buffer.readVarInt()));
        List<PathSegment> exploredSegments = new ArrayList<>(exploredCount);
        for (int index = 0; index < exploredCount; index++) {
            exploredSegments.add(new PathSegment(readVec3(buffer), readVec3(buffer), buffer.readBoolean()));
        }
        return new ScmPathDebugPayload(enabled, controllerPos, commandKey, status, groundVehicle,
                position, target, activeWaypointIndex, currentSegmentClear, waypoints, exploredSegments);
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 value) {
        Vec3 safe = value == null ? Vec3.ZERO : value;
        buffer.writeDouble(safe.x);
        buffer.writeDouble(safe.y);
        buffer.writeDouble(safe.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    // One waypoint and its selected driving gear.
    public record PathPoint(Vec3 position, boolean reverse) {
        public PathPoint {
            position = position == null ? Vec3.ZERO : position;
        }
    }

    // A planner candidate: cyan/grey is traversable, red was rejected by collision checks.
    public record PathSegment(Vec3 from, Vec3 to, boolean clear) {
        public PathSegment {
            from = from == null ? Vec3.ZERO : from;
            to = to == null ? Vec3.ZERO : to;
        }
    }
}
