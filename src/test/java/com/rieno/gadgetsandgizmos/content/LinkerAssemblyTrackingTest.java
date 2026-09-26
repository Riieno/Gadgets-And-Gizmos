package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkerAssemblyTrackingTest {
    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void originalReferenceSurvivesNativeSableTransfersAndTrackerReconciliation() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
        BlockPos initial = new BlockPos(1, 64, 1);
        var linked = new ContraptionNetworkLinkerData.LinkedTarget(initial, null,
                "minecraft:redstone_lamp", "Lamp", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        ControllerDirectTargetReference original = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked))
                .getFirst().asDirectTargetReference();
        UUID trackingId = UUID.randomUUID();
        CompoundTag target = new CompoundTag();
        target.putString("NodeId", original.targetId());
        target.putString("Scope", "block");
        target.putString("Mode", "output");
        target.putString("BlockId", "minecraft:redstone_lamp");
        target.putLong("CurrentLocalPos", initial.asLong());
        target.putUUID("TrackingPointId", trackingId);
        ListTag targets = new ListTag(); targets.add(target);
        CompoundTag linker = new CompoundTag();
        linker.putUUID("LinkerId", UUID.randomUUID());
        linker.putString("Dimension", "minecraft:overworld");
        linker.put("Targets", targets);
        ListTag linkers = new ListTag(); linkers.add(linker);
        CompoundTag saved = new CompoundTag(); saved.put("Linkers", linkers);
        var load = ContraptionNetworkLinkerTracker.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        var tracker = (ContraptionNetworkLinkerTracker) load.invoke(null, saved, null);
        var records = ContraptionNetworkLinkerTracker.class.getDeclaredField("trackedLinkers");
        records.setAccessible(true);
        Object record = ((Map<?, ?>) records.get(tracker)).values().iterator().next();
        var reconcile = ContraptionNetworkLinkerTracker.class.getDeclaredMethod("reconcileTrackedLinker",
                record.getClass(), ServerLevel.class, ItemStack.class, List.class,
                ContraptionNetworkLinkerData.LinkMode.class, ContraptionNetworkLinkerData.TargetMode.class);
        reconcile.setAccessible(true);
        var ctor = SubLevelTrackingPointSavedData.class.getDeclaredConstructor(ServerLevel.class);
        ctor.setAccessible(true);
        var points = ctor.newInstance(level);
        points.setTrackingPoint(trackingId, new TrackingPoint(false, null, null,
                new Vector3d(1.5, 64.5, 1.5), null));
        try (var storage = mockStatic(SubLevelTrackingPointSavedData.class);
             var simulated = mockStatic(SimulatedHelper.class);
             var collector = mockStatic(SubLevelBlockEntityCollector.class);
             var linkerData = mockStatic(ContraptionNetworkLinkerData.class, CALLS_REAL_METHODS)) {
            storage.when(() -> SubLevelTrackingPointSavedData.getOrLoad(level)).thenReturn(points);
            linkerData.when(() -> ContraptionNetworkLinkerData.rewriteTrackedTargets(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(true);
            ServerSubLevel parent = mock(ServerSubLevel.class);
            ServerSubLevel child = mock(ServerSubLevel.class);
            UUID parentId = UUID.randomUUID();
            UUID childId = UUID.randomUUID();
            when(parent.getUniqueId()).thenReturn(parentId);
            when(child.getUniqueId()).thenReturn(childId);
            simulated.when(() -> SimulatedHelper.getSubLevelId(parent)).thenReturn(parentId);
            BlockPos from = initial;
            // World -> body -> nested body -> parent body -> world.
            for (int step = 0; step < 4; step++) {
                BlockPos to = step == 3 ? new BlockPos(20, 70, 20) : new BlockPos(1000 * (step + 1), 64, 1000);
                ServerSubLevel destination = step == 0 ? parent : step == 1 ? child : null;
                UUID destinationId = step == 3 ? null : step == 1 ? childId : parentId;
                if (step == 2) {
                    // Sable's disassembly path passes null even when the destination is a parent plot.
                    collector.when(() -> SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, to)).thenReturn(true);
                    simulated.when(() -> SimulatedHelper.getContainingSubLevel(level, to.getCenter())).thenReturn(parent);
                }
                var transform = new SubLevelAssemblyHelper.AssemblyTransform(from, to, 0, Rotation.NONE, level);
                var bounds = new BoundingBox3i(from.getX(), from.getY(), from.getZ(), from.getX(), from.getY(), from.getZ());
                tracker.prepareAssemblyTargets(level, bounds, destination, transform);
                SubLevelAssemblyHelper.moveTrackingPoints(level, bounds, destination, transform);
                tracker.finishAssemblyTargets(level, transform);
                reconcile.invoke(tracker, record, level, ItemStack.EMPTY, List.of(linked),
                        ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetMode.BLOCK);
                ControllerDirectTargetReference resolved = tracker.resolveTargetReference(level, original);
                assertEquals(to, resolved.blockPos(), "Lost original link at transfer " + step);
                assertEquals(destinationId, resolved.subLevelId());
                assertEquals(to, BlockPos.containing(points.getTrackingPoint(trackingId).point().x,
                        points.getTrackingPoint(trackingId).point().y, points.getTrackingPoint(trackingId).point().z));
                // References saved in graphs must survive reloading the tracker as well.
                tracker = (ContraptionNetworkLinkerTracker) load.invoke(null, tracker.save(new CompoundTag(), null), null);
                record = ((Map<?, ?>) records.get(tracker)).values().iterator().next();
                assertEquals(to, tracker.resolveTargetReference(level, original).blockPos());
                from = to;
            }
        }
    }
}
