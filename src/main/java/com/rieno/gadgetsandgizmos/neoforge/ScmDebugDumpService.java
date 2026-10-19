package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ShipControlModuleRuntime;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Write one bounded server-side SCM trace for the vehicle occupied by an operator
public final class ScmDebugDumpService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long DUMP_DURATION_NANOS = Duration.ofMinutes(5L).toNanos();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final System.Logger LOGGER = System.getLogger(
            ScmDebugDumpService.class.getName());
    private static final Map<UUID, DumpSession> SESSIONS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SCM debug dump service
    private ScmDebugDumpService() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Start a five-minute trace for only the connected vehicle occupied by this player.
    public static StartResult start(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return StartResult.failed("A server player is required.");
        }
        Object subLevel = SimulatedHelper.getEntityTrackingSubLevel(player);
        if (subLevel == null) {
            subLevel = SimulatedHelper.getContainingSubLevel(
                    player.level(), player.position());
        }
        UUID playerSubLevelId = SimulatedHelper.getSubLevelId(subLevel);
        if (playerSubLevelId == null) {
            return StartResult.failed("Stand on the Sable vehicle controlled by the SCM first.");
        }
        ShipControlModuleRuntime.DebugDumpTarget target =
                ShipControlModuleRuntime.debugDumpTarget(
                        player.serverLevel(), playerSubLevelId);
        if (target == null) {
            return StartResult.failed(
                    "No live SCM controls the connected vehicle the player is currently on.");
        }
        MinecraftServer server = player.getServer();
        Path file = server.getWorldPath(LevelResource.ROOT)
                .resolve("debug")
                .resolve("gizmos")
                .resolve(FILE_TIME.format(Instant.now()) + "-"
                        + safeFileName(player.getGameProfile().getName())
                        + "-scm-" + target.vehicleId().toString().substring(0, 8)
                        + ".log")
                .toAbsolutePath()
                .normalize();
        BufferedWriter writer;
        try {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not create SCM debug dump", err);
            return StartResult.failed("Could not create the SCM debug dump file: "
                    + err.getMessage());
        }
        long now = System.nanoTime();
        DumpSession session = new DumpSession(player.getUUID(),
                player.serverLevel().dimension(), target.vehicleId(),
                target.vehicleName(), file, writer, now, now + DUMP_DURATION_NANOS);
        try {
            writeHeader(session, playerSubLevelId);
            writer.flush();
        } catch (IOException err) {
            closeQuietly(writer);
            LOGGER.log(System.Logger.Level.ERROR, "Could not initialize SCM debug dump", err);
            return StartResult.failed("Could not initialize the SCM debug dump file: "
                    + err.getMessage());
        }
        DumpSession previous = SESSIONS.put(player.getUUID(), session);
        if (previous != null) finish(previous, "replaced by a new dump");
        ShipControlModuleRuntime.setAutopilotDebugDumpCollectionEnabled(true);
        return StartResult.started(file, target.vehicleName(), target.vehicleId());
    }

    // Sample active dumps after every completed server tick.
    public static void onServerTick(ServerTickEvent.Post evt) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = evt.getServer();
        long now = System.nanoTime();
        Iterator<DumpSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            DumpSession session = iterator.next();
            if (now >= session.deadlineNanos()) {
                finish(session, "five-minute duration complete");
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(session.dimension());
            try {
                ShipControlModuleRuntime.DebugDumpFrame frame = level == null
                        ? null : ShipControlModuleRuntime.debugDumpFrame(
                        level, session.vehicleId());
                writeFrame(session, frame, now);
                if (server.getTickCount() % 20 == 0) session.writer().flush();
            } catch (IOException err) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Could not continue SCM debug dump " + session.file(), err);
                closeQuietly(session.writer());
                iterator.remove();
            }
        }
        if (SESSIONS.isEmpty()) {
            ShipControlModuleRuntime.setAutopilotDebugDumpCollectionEnabled(false);
        }
    }

    // Close all files cleanly when the integrated or dedicated server stops.
    public static void onServerStopped(ServerStoppedEvent evt) {
        for (DumpSession session : SESSIONS.values()) {
            finish(session, "server stopped");
        }
        SESSIONS.clear();
        ShipControlModuleRuntime.setAutopilotDebugDumpCollectionEnabled(false);
    }

    // Write immutable session metadata before the first tick sample.
    private static void writeHeader(DumpSession session, UUID playerSubLevelId)
            throws IOException {
        BufferedWriter writer = session.writer();
        writer.write("# Gadgets & Gizmos temporary SCM debug dump");
        writer.newLine();
        writer.write("# started_utc=" + Instant.now());
        writer.newLine();
        writer.write("# duration_seconds=300");
        writer.newLine();
        writer.write("# player=" + session.playerId());
        writer.newLine();
        writer.write("# player_sub_level=" + playerSubLevelId);
        writer.newLine();
        writer.write("# vehicle_id=" + session.vehicleId());
        writer.newLine();
        writer.write("# vehicle_name=" + oneLine(session.vehicleName()));
        writer.newLine();
        writer.write("# dimension=" + session.dimension().location());
        writer.newLine();
        writer.newLine();
    }

    // Write one complete SCM tick including the low-level native joint lifecycle.
    private static void writeFrame(
            DumpSession session,
            ShipControlModuleRuntime.DebugDumpFrame frame,
            long now
    ) throws IOException {
        BufferedWriter writer = session.writer();
        writer.write("=== sample=" + session.samples()
                + " elapsed_ms=" + Duration.ofNanos(
                Math.max(0L, now - session.startedNanos())).toMillis()
                + " utc=" + Instant.now() + " ===");
        writer.newLine();
        if (frame == null) {
            writer.write("snapshot=unavailable");
            writer.newLine();
            writer.newLine();
            session.incrementSamples();
            return;
        }
        AutopilotDebugSnapshot snapshot = frame.snapshot();
        writer.write("vehicle.id=" + snapshot.vehicleId());
        writer.newLine();
        writer.write("vehicle.name=" + oneLine(snapshot.vehicleName()));
        writer.newLine();
        writer.write("vehicle.state=" + snapshot.state());
        writer.newLine();
        writer.write("game_time=" + snapshot.gameTime());
        writer.newLine();
        writer.write("anchor=" + snapshot.anchor());
        writer.newLine();
        for (AutopilotDebugSnapshot.Section section : snapshot.sections()) {
            writer.write("[" + oneLine(section.name()) + "]");
            writer.newLine();
            for (AutopilotDebugSnapshot.Entry entry : section.entries()) {
                writer.write(oneLine(entry.label()) + "=" + oneLine(entry.value())
                        + " | tone=" + entry.tone());
                writer.newLine();
            }
        }
        writer.write("[Internal runtime]");
        writer.newLine();
        for (String line : frame.internalLines()) {
            writer.write(oneLine(line));
            writer.newLine();
        }
        writer.newLine();
        session.incrementSamples();
    }

    // Finish one dump with a readable reason and a durable final flush.
    private static void finish(DumpSession session, String reason) {
        try {
            session.writer().write("# ended_utc=" + Instant.now());
            session.writer().newLine();
            session.writer().write("# end_reason=" + oneLine(reason));
            session.writer().newLine();
            session.writer().write("# samples=" + session.samples());
            session.writer().newLine();
            session.writer().flush();
        } catch (IOException err) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not finalize SCM debug dump " + session.file(), err);
        } finally {
            closeQuietly(session.writer());
        }
    }

    // Close a failed writer without masking the original error.
    private static void closeQuietly(BufferedWriter writer) {
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }

    // Keep one caller-provided name inside a single safe path segment.
    private static String safeFileName(String value) {
        String safe = value == null ? "player"
                : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "player" : safe;
    }

    // Prevent diagnostic values from injecting structural lines into the dump.
    private static String oneLine(String value) {
        if (value == null) return "null";
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }

    // Return one command-facing result without exposing the active writer.
    public record StartResult(
            boolean started,
            Path file,
            String vehicleName,
            UUID vehicleId,
            String message
    ) {
        private static StartResult started(Path file, String vehicleName, UUID vehicleId) {
            return new StartResult(true, file, vehicleName, vehicleId, "");
        }

        private static StartResult failed(String message) {
            return new StartResult(false, null, "", null, message);
        }
    }

    // Retain one selected vehicle and file until its bounded trace completes.
    private static final class DumpSession {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final UUID vehicleId;
        private final String vehicleName;
        private final Path file;
        private final BufferedWriter writer;
        private final long startedNanos;
        private final long deadlineNanos;
        private long samples;

        private DumpSession(
                UUID playerId,
                ResourceKey<Level> dimension,
                UUID vehicleId,
                String vehicleName,
                Path file,
                BufferedWriter writer,
                long startedNanos,
                long deadlineNanos
        ) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.file = file;
            this.writer = writer;
            this.startedNanos = startedNanos;
            this.deadlineNanos = deadlineNanos;
        }

        private UUID playerId() {
            return playerId;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private UUID vehicleId() {
            return vehicleId;
        }

        private String vehicleName() {
            return vehicleName;
        }

        private Path file() {
            return file;
        }

        private BufferedWriter writer() {
            return writer;
        }

        private long startedNanos() {
            return startedNanos;
        }

        private long deadlineNanos() {
            return deadlineNanos;
        }

        private long samples() {
            return samples;
        }

        private void incrementSamples() {
            samples++;
        }
    }
}
