package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// Send operator-visible Sable pathfinder debug routes to a client.
public record PathfinderDebugRendererPayload(
        boolean routesEnabled,
        boolean brainsEnabled,
        List<SablePathfinder.DebugRoute> routes,
        List<AutopilotDebugSnapshot> brains
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_ROUTES = 128;
    private static final int MAX_WAYPOINTS_PER_ROUTE = 16_384;
    private static final int MAX_CHECKED_SEGMENTS_PER_ROUTE = 128;
    private static final int MAX_ROUTE_ID_LENGTH = 160;
    private static final int MAX_BRAINS = 128;
    private static final int MAX_BRAIN_SECTIONS = 16;
    private static final int MAX_SECTION_ENTRIES = 32;
    private static final int MAX_VEHICLE_NAME_LENGTH = 96;
    private static final int MAX_SECTION_NAME_LENGTH = 48;
    private static final int MAX_ENTRY_LABEL_LENGTH = 64;
    private static final int MAX_ENTRY_VALUE_LENGTH = 256;
    public static final Type<PathfinderDebugRendererPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "pathfinder_debug_renderer"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            PathfinderDebugRendererPayload> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            PathfinderDebugRendererPayload::encode, PathfinderDebugRendererPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the pathfinder debug renderer payload
    public PathfinderDebugRendererPayload {
        routes = routes == null ? List.of() : routes.stream()
                .filter(route -> route != null)
                .limit(MAX_ROUTES)
                .map(PathfinderDebugRendererPayload::boundedRoute)
                .toList();
        brains = brains == null ? List.of() : brains.stream()
                .filter(snapshot -> snapshot != null)
                .limit(MAX_BRAINS)
                .map(PathfinderDebugRendererPayload::boundedBrain)
                .toList();
    }

    // Preserve the route-only constructor for integrations which do not publish brain state.
    public PathfinderDebugRendererPayload(
            boolean enabled,
            List<SablePathfinder.DebugRoute> routes
    ) {
        this(enabled, false, routes, List.of());
    }

    // Preserve the combined constructor for integrations which intentionally toggle both layers.
    public PathfinderDebugRendererPayload(
            boolean enabled,
            List<SablePathfinder.DebugRoute> routes,
            List<AutopilotDebugSnapshot> brains
    ) {
        this(enabled, enabled, routes, brains);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the operator pathfinder debug payload on the client.
    public static void handle(PathfinderDebugRendererPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> rendererClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.CTPathfinderDebugRenderer");
                Method method = rendererClass.getMethod(
                        "setSnapshot", boolean.class, boolean.class,
                        List.class, List.class);
                method.invoke(null, payload.routesEnabled(), payload.brainsEnabled(),
                        payload.routes(), payload.brains());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the debug route snapshot.
    private static void encode(RegistryFriendlyByteBuf buffer, PathfinderDebugRendererPayload payload) {
        buffer.writeBoolean(payload.routesEnabled());
        buffer.writeBoolean(payload.brainsEnabled());
        buffer.writeVarInt(payload.routes().size());
        for (SablePathfinder.DebugRoute route : payload.routes()) {
            buffer.writeUtf(route.id(), MAX_ROUTE_ID_LENGTH);
            writeVec3(buffer, route.origin());
            writeVec3(buffer, route.target());
            buffer.writeEnum(route.outcome());
            buffer.writeBoolean(route.targetLegValidated());
            buffer.writeEnum(route.style());
            buffer.writeVarInt(route.waypoints().size());
            for (SablePathfinder.Waypoint waypoint : route.waypoints()) {
                writeVec3(buffer, waypoint.position());
            }
            buffer.writeVarInt(route.checkedSegments().size());
            for (SablePathfinder.DebugSegment checked : route.checkedSegments()) {
                writeVec3(buffer, checked.start());
                writeVec3(buffer, checked.end());
                buffer.writeEnum(checked.result());
            }
        }
        buffer.writeVarInt(payload.brains().size());
        for (AutopilotDebugSnapshot snapshot : payload.brains()) {
            buffer.writeUUID(snapshot.vehicleId());
            buffer.writeUtf(snapshot.vehicleName(), MAX_VEHICLE_NAME_LENGTH);
            writeVec3(buffer, snapshot.anchor());
            buffer.writeEnum(snapshot.state());
            buffer.writeLong(snapshot.gameTime());
            buffer.writeVarInt(snapshot.sections().size());
            for (AutopilotDebugSnapshot.Section section : snapshot.sections()) {
                buffer.writeUtf(section.name(), MAX_SECTION_NAME_LENGTH);
                buffer.writeVarInt(section.entries().size());
                for (AutopilotDebugSnapshot.Entry entry : section.entries()) {
                    buffer.writeUtf(entry.label(), MAX_ENTRY_LABEL_LENGTH);
                    buffer.writeUtf(entry.value(), MAX_ENTRY_VALUE_LENGTH);
                    buffer.writeEnum(entry.tone());
                }
            }
        }
    }

    // Decode the debug route snapshot.
    private static PathfinderDebugRendererPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean routesEnabled = buffer.readBoolean();
        boolean brainsEnabled = buffer.readBoolean();
        int routeCount = boundedCount(buffer.readVarInt(), MAX_ROUTES);
        List<SablePathfinder.DebugRoute> routes = new ArrayList<>(routeCount);
        for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
            String id = buffer.readUtf(MAX_ROUTE_ID_LENGTH);
            Vec3 origin = readVec3(buffer);
            Vec3 target = readVec3(buffer);
            SablePathfinder.Outcome outcome = buffer.readEnum(SablePathfinder.Outcome.class);
            boolean targetLegValidated = buffer.readBoolean();
            SablePathfinder.DebugRouteStyle style = buffer.readEnum(
                    SablePathfinder.DebugRouteStyle.class);
            int waypointCount = boundedCount(buffer.readVarInt(), MAX_WAYPOINTS_PER_ROUTE);
            List<SablePathfinder.Waypoint> waypoints = new ArrayList<>(waypointCount);
            for (int waypointIndex = 0; waypointIndex < waypointCount; waypointIndex++) {
                waypoints.add(new SablePathfinder.Waypoint(readVec3(buffer), null));
            }
            int checkedCount = boundedCount(buffer.readVarInt(), MAX_CHECKED_SEGMENTS_PER_ROUTE);
            List<SablePathfinder.DebugSegment> checkedSegments = new ArrayList<>(checkedCount);
            for (int checkedIndex = 0; checkedIndex < checkedCount; checkedIndex++) {
                checkedSegments.add(new SablePathfinder.DebugSegment(
                        readVec3(buffer), readVec3(buffer),
                        buffer.readEnum(SablePathfinder.TraversalResult.class)));
            }
            routes.add(new SablePathfinder.DebugRoute(
                    id, origin, target, waypoints, outcome, checkedSegments, targetLegValidated, style));
        }
        int brainCount = boundedCount(buffer.readVarInt(), MAX_BRAINS);
        List<AutopilotDebugSnapshot> brains = new ArrayList<>(brainCount);
        for (int brainIndex = 0; brainIndex < brainCount; brainIndex++) {
            java.util.UUID vehicleId = buffer.readUUID();
            String vehicleName = buffer.readUtf(MAX_VEHICLE_NAME_LENGTH);
            Vec3 anchor = readVec3(buffer);
            AutopilotDebugSnapshot.State state = buffer.readEnum(
                    AutopilotDebugSnapshot.State.class);
            long gameTime = buffer.readLong();
            int sectionCount = boundedCount(
                    buffer.readVarInt(), MAX_BRAIN_SECTIONS);
            List<AutopilotDebugSnapshot.Section> sections = new ArrayList<>(sectionCount);
            for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
                String sectionName = buffer.readUtf(MAX_SECTION_NAME_LENGTH);
                int entryCount = boundedCount(
                        buffer.readVarInt(), MAX_SECTION_ENTRIES);
                List<AutopilotDebugSnapshot.Entry> entries = new ArrayList<>(entryCount);
                for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                    entries.add(new AutopilotDebugSnapshot.Entry(
                            buffer.readUtf(MAX_ENTRY_LABEL_LENGTH),
                            buffer.readUtf(MAX_ENTRY_VALUE_LENGTH),
                            buffer.readEnum(AutopilotDebugSnapshot.Tone.class)));
                }
                sections.add(new AutopilotDebugSnapshot.Section(sectionName, entries));
            }
            brains.add(new AutopilotDebugSnapshot(
                    vehicleId, vehicleName, anchor, state, gameTime, sections));
        }
        return new PathfinderDebugRendererPayload(
                routesEnabled, brainsEnabled, routes, brains);
    }

    // Bound a received collection size.
    private static int boundedCount(int count, int maximum) {
        return Math.max(0, Math.min(maximum, count));
    }

    // Bound one outgoing route snapshot.
    private static SablePathfinder.DebugRoute boundedRoute(SablePathfinder.DebugRoute route) {
        String id = route.id().length() <= MAX_ROUTE_ID_LENGTH
                ? route.id() : route.id().substring(0, MAX_ROUTE_ID_LENGTH);
        List<SablePathfinder.Waypoint> waypoints = route.waypoints().stream()
                .filter(waypoint -> waypoint != null)
                .limit(MAX_WAYPOINTS_PER_ROUTE)
                .toList();
        List<SablePathfinder.DebugSegment> checkedSegments = route.checkedSegments().stream()
                .filter(segment -> segment != null)
                .limit(MAX_CHECKED_SEGMENTS_PER_ROUTE)
                .toList();
        return new SablePathfinder.DebugRoute(
                id, route.origin(), route.target(), waypoints, route.outcome(), checkedSegments,
                route.targetLegValidated(), route.style());
    }

    // Bound one outgoing autopilot brain snapshot.
    private static AutopilotDebugSnapshot boundedBrain(
            AutopilotDebugSnapshot snapshot
    ) {
        List<AutopilotDebugSnapshot.Section> sections = snapshot.sections().stream()
                .limit(MAX_BRAIN_SECTIONS)
                .map(section -> new AutopilotDebugSnapshot.Section(
                        boundedText(section.name(), MAX_SECTION_NAME_LENGTH),
                        section.entries().stream().limit(MAX_SECTION_ENTRIES)
                                .map(entry -> new AutopilotDebugSnapshot.Entry(
                                        boundedText(entry.label(), MAX_ENTRY_LABEL_LENGTH),
                                        boundedText(entry.value(), MAX_ENTRY_VALUE_LENGTH),
                                        entry.tone()))
                                .toList()))
                .toList();
        return new AutopilotDebugSnapshot(
                snapshot.vehicleId(),
                boundedText(snapshot.vehicleName(), MAX_VEHICLE_NAME_LENGTH),
                snapshot.anchor(), snapshot.state(), snapshot.gameTime(), sections);
    }

    // Bound one caller-owned diagnostic string by code point.
    private static String boundedText(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) <= maximumLength) return safe;
        int end = safe.offsetByCodePoints(0, maximumLength);
        return safe.substring(0, end);
    }

    // Write one world position.
    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 position) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
    }

    // Read one world position.
    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
