package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerTracker;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinItem;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.neoforge.network.GizmosLinkHighlightPayload;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Provide guarded server commands for inspecting and repairing Sable tracking state
public final class CTSableTrackingCommands {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int OP_PERMISSION_LEVEL = 2;
    private static final SimpleCommandExceptionType NO_PLAYERS_SELECTED =
            new SimpleCommandExceptionType(Component.literal("No online players were selected."));
    private static final SimpleCommandExceptionType NO_LOOKED_AT_BLOCK =
            new SimpleCommandExceptionType(Component.literal("No target block found. Provide a range or look at a block."));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT sable tracking commands
    private CTSableTrackingCommands() {
    }

    // Register the commands
    public static void registerCommands(RegisterCommandsEvent evt) {
        registerCommands(evt.getDispatcher());
    }

    // Register the commands
    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gizmos")
                .then(Commands.literal("cleanup_tracking_points")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .executes(ctx -> cleanupTrackingPoints(ctx.getSource())))
                .then(Commands.literal("clear_links")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .executes(ctx -> clearLinks(ctx.getSource(), null))
                        .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0D))
                                .executes(ctx -> clearLinks(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "range")))))
                .then(Commands.literal("show_links")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .executes(ctx -> showLinks(ctx.getSource(), null))
                        .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0D))
                                .executes(ctx -> showLinks(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "range")))))
                .then(Commands.literal("hide_links")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .executes(ctx -> hideLinks(ctx.getSource())))
                .then(Commands.literal("remove_disabled_items")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .then(Commands.literal("range")
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(
                                                0, CTServerFeatureCleanup.MAX_CHUNK_RANGE))
                                        .executes(ctx -> queueDisabledItemCleanup(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "chunks")))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> cleanupDisabledPlayerItems(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"))))))
                .then(Commands.literal("reset_music_disk_collected")
                        .requires(src -> src.hasPermission(OP_PERMISSION_LEVEL))
                        .executes(ctx -> resetMusicDiskCollected(
                                ctx.getSource(),
                                List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> resetMusicDiskCollected(
                                        ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "targets"))))));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Queue the disabled item cleanup
    private static int queueDisabledItemCleanup(CommandSourceStack src, int chunkRange) {
        BlockPos sourcePos = BlockPos.containing(src.getPosition());
        CTServerFeatureCleanup.RangeCleanupRequest req = CTServerFeatureCleanup.queueLoadedChunkRange(
                src.getLevel(), new ChunkPos(sourcePos), chunkRange);

        if (req.disabledItemTypes() == 0) {
            src.sendSuccess(() -> Component.literal(
                    "No disabled Create Gadgets & Gizmos item types are currently configured."), true);
            return Command.SINGLE_SUCCESS;
        }
        if (req.loadedChunks() == 0) {
            src.sendSuccess(() -> Component.literal(
                    "No loaded chunks were found within " + req.range() + " chunk(s) of the command source."),
                    true);
            return Command.SINGLE_SUCCESS;
        }

        src.sendSuccess(() -> Component.literal(
                "Queued disabled-item cleanup for " + req.queuedChunks()
                        + " of " + req.loadedChunks() + " loaded chunk(s) within a "
                        + req.range() + "-chunk range. Already queued chunks continue their existing cleanup."),
                true);
        return req.queuedChunks() > 0 ? req.queuedChunks() : Command.SINGLE_SUCCESS;
    }

    // Clean up the disabled player items
    private static int cleanupDisabledPlayerItems(CommandSourceStack src, ServerPlayer player) {
        boolean changed = CTServerFeatureCleanup.cleanupPlayer(player);
        src.sendSuccess(() -> Component.literal(changed
                ? "Removed disabled Create Gadgets & Gizmos items from " + player.getGameProfile().getName() + "."
                : "No disabled Create Gadgets & Gizmos items were found for "
                        + player.getGameProfile().getName() + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    // Clear the links
    private static int clearLinks(CommandSourceStack src, Double range) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LinkSelection selection = linkSelection(player, range);

        ContraptionNetworkLinkerTracker.ClearLinksSummary liveSummary =
                ContraptionNetworkLinkerTracker.get(src.getServer()).clearLinks(level, selection::matches);
        int databaseUpdates = ControllerManifestStore.rewriteLinkerManifests(root ->
                ContraptionNetworkLinkerData.removeTargets(root,
                        target -> selection.matches(target, targetWorldCenter(level, target))));
        PacketDistributor.sendToPlayer(player, new GizmosLinkHighlightPayload(false, new CompoundTag()));

        src.sendSuccess(() -> Component.literal("Removed " + liveSummary.removedTargets()
                + " live linker target(s), updated " + liveSummary.updatedLinkers()
                + " live linker(s), and updated " + databaseUpdates + " database linker record(s)."), true);
        return liveSummary.removedTargets() > 0 || databaseUpdates > 0
                ? Math.max(1, liveSummary.removedTargets() + databaseUpdates)
                : Command.SINGLE_SUCCESS;
    }

    // Show the links
    private static int showLinks(CommandSourceStack src, Double range) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LinkSelection selection = linkSelection(player, range);
        Map<String, ContraptionNetworkLinkerData.LinkedTarget> targets = new LinkedHashMap<>();

        for (ContraptionNetworkLinkerData.LinkedTarget target :
                ContraptionNetworkLinkerTracker.get(src.getServer()).collectLinks(level, selection::matches)) {
            targets.put(ContraptionNetworkLinkerData.nodeIdForTarget(target), target);
        }
        for (CompoundTag linkerData : ControllerManifestStore.loadLinkerManifestData()) {
            for (ContraptionNetworkLinkerData.LinkedTarget target : ContraptionNetworkLinkerData.readTargets(linkerData)) {
                if (selection.matches(target, targetWorldCenter(level, target))) {
                    targets.put(ContraptionNetworkLinkerData.nodeIdForTarget(target), target);
                }
            }
        }

        CompoundTag root = ContraptionNetworkLinkerData.writeRoot(new ArrayList<>(targets.values()),
                ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetMode.AUTO);
        PacketDistributor.sendToPlayer(player, new GizmosLinkHighlightPayload(true, root));
        src.sendSuccess(() -> Component.literal("Showing " + targets.size() + " linker target highlight(s)."), false);
        return targets.isEmpty() ? Command.SINGLE_SUCCESS : targets.size();
    }

    // Hide the links
    private static int hideLinks(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new GizmosLinkHighlightPayload(false, new CompoundTag()));
        src.sendSuccess(() -> Component.literal("Hidden command linker highlights."), false);
        return Command.SINGLE_SUCCESS;
    }

    // Get the link selection
    private static LinkSelection linkSelection(ServerPlayer player, Double range) throws CommandSyntaxException {
        if (range != null) {
            return LinkSelection.range(player.position(), range);
        }
        HitResult hit = player.pick(128.0D, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            throw NO_LOOKED_AT_BLOCK.create();
        }
        return LinkSelection.exact(blockHit.getBlockPos());
    }

    // Get the target world center
    private static Vec3 targetWorldCenter(ServerLevel level, ContraptionNetworkLinkerData.LinkedTarget target) {
        if (target == null || target.blockPos() == null) {
            return Vec3.ZERO;
        }
        if (target.subLevelId() != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, target.subLevelId());
            if (subLevel != null) {
                Vec3 transformed = SimulatedHelper.toContainingWorldPosition(subLevel, Vec3.atCenterOf(target.blockPos()));
                if (transformed != null) {
                    return transformed;
                }
            }
        }
        return Vec3.atCenterOf(target.blockPos());
    }

    // Store the link selection
    private record LinkSelection(Vec3 center, double radius, BlockPos exactPos) {
        // Get the range
        private static LinkSelection range(Vec3 center, double radius) {
            return new LinkSelection(center == null ? Vec3.ZERO : center, Math.max(0.0D, radius), null);
        }

        // Get the exact
        private static LinkSelection exact(BlockPos pos) {
            BlockPos immutable = pos == null ? BlockPos.ZERO : pos.immutable();
            return new LinkSelection(Vec3.atCenterOf(immutable), 0.0D, immutable);
        }

        // Check if this matches the value
        private boolean matches(ContraptionNetworkLinkerData.LinkedTarget target, Vec3 worldCenter) {
            if (target == null || worldCenter == null) {
                return false;
            }
            if (exactPos != null) {
                return exactPos.equals(BlockPos.containing(worldCenter))
                        || (target.subLevelId() == null && exactPos.equals(target.blockPos()));
            }
            return center.distanceToSqr(worldCenter) <= radius * radius;
        }
    }

    // Reset the music disk collected
    private static int resetMusicDiskCollected(CommandSourceStack src,
                                               Collection<? extends Entity> targets) throws CommandSyntaxException {
        int playerCount = 0;
        int resetCount = 0;

        for (Entity entity : targets) {
            if (!(entity instanceof ServerPlayer player)) {
                continue;
            }
            playerCount++;
            if (PlayerMannequinItem.resetKineticCurrencyRewardClaim(player)) {
                resetCount++;
            }
        }

        if (playerCount == 0) {
            throw NO_PLAYERS_SELECTED.create();
        }

        int finalPlayerCount = playerCount;
        int finalResetCount = resetCount;
        src.sendSuccess(() -> Component.literal("Reset Kinetic Currency music disc acquisition for "
                + finalResetCount + " of " + finalPlayerCount + " selected player(s)."), true);
        return resetCount > 0 ? resetCount : Command.SINGLE_SUCCESS;
    }

    // Clean up the tracking points
    private static int cleanupTrackingPoints(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        Set<UUID> protectedIds = protectedTrackingPointIds(server);
        CleanupSummary total = new CleanupSummary();

        for (ServerLevel level : server.getAllLevels()) {
            total = total.add(cleanupLevel(level, protectedIds));
        }

        CleanupSummary res = total;
        src.sendSuccess(() -> Component.literal(
                "Sable tracking point cleanup scanned " + res.scanned()
                        + " entries across " + res.levels()
                        + " levels, removed " + res.removed()
                        + " duplicates, kept " + res.kept()
                        + " entries (" + res.protectedKept() + " protected)."), true);
        return res.removed() > 0 ? res.removed() : Command.SINGLE_SUCCESS;
    }

    // Get the protected tracking point ids
    private static Set<UUID> protectedTrackingPointIds(MinecraftServer server) {
        Set<UUID> protectedIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            protectedIds.add(player.getGameProfile().getId());
        }
        protectedIds.addAll(ContraptionNetworkLinkerTracker.get(server).activeTrackingPointIds());
        return protectedIds;
    }

    // Clean up the level
    private static CleanupSummary cleanupLevel(ServerLevel level, Set<UUID> protectedIds) {
        SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        Set<TrackingPointSignature> protectedSignatures = new HashSet<>();
        int scanned = 0;
        int kept = 0;
        int protectedKept = 0;
        int removed = 0;

        for (var entry : data.getAllTrackingPoints()) {
            scanned++;
            if (protectedIds.contains(entry.getKey())) {
                protectedSignatures.add(TrackingPointSignature.of(entry.getValue()));
            }
        }

        Set<TrackingPointSignature> keptUnprotectedSignatures = new HashSet<>();
        for (var entry : data.getAllTrackingPoints()) {
            UUID id = entry.getKey();
            TrackingPointSignature signature = TrackingPointSignature.of(entry.getValue());

            if (protectedIds.contains(id)) {
                kept++;
                protectedKept++;
                continue;
            }

            if (protectedSignatures.contains(signature)) {
                data.removeTrackingPoint(id);
                removed++;
                continue;
            }

            if (keptUnprotectedSignatures.add(signature)) {
                kept++;
            } else {
                data.removeTrackingPoint(id);
                removed++;
            }
        }

        return new CleanupSummary(1, scanned, kept, protectedKept, removed);
    }

    // Store the tracking point signature
    private record TrackingPointSignature(boolean inSubLevel,
                                          UUID subLevelId,
                                          GlobalSavedSubLevelPointer pointer,
                                          VectorSignature point,
                                          VectorSignature placeholder) {
        // Create the tracking point signature
        private static TrackingPointSignature of(TrackingPoint trackingPoint) {
            return new TrackingPointSignature(
                    trackingPoint.inSubLevel(),
                    trackingPoint.subLevelID(),
                    trackingPoint.lastSavedSubLevelPointer(),
                    VectorSignature.of(trackingPoint.point()),
                    VectorSignature.of(trackingPoint.globalPlaceholderPosition()));
        }
    }

    // Store the vector signature
    private record VectorSignature(long x, long y, long z) {
        // Create the vector signature
        private static VectorSignature of(Vector3d vector) {
            if (vector == null) {
                return null;
            }
            return new VectorSignature(
                    Double.doubleToLongBits(vector.x()),
                    Double.doubleToLongBits(vector.y()),
                    Double.doubleToLongBits(vector.z()));
        }
    }

    // Store the cleanup summary
    private record CleanupSummary(int levels, int scanned, int kept, int protectedKept, int removed) {
        // Initialize the cleanup summary
        private CleanupSummary() {
            this(0, 0, 0, 0, 0);
        }

        // Add the cleanup summary
        private CleanupSummary add(CleanupSummary other) {
            return new CleanupSummary(
                    levels + other.levels,
                    scanned + other.scanned,
                    kept + other.kept,
                    protectedKept + other.protectedKept,
                    removed + other.removed);
        }
    }
}
