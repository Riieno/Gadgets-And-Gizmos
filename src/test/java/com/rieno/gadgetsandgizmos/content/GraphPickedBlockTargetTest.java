package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphPickedBlockTargetTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // Retain ordinary picked faces when the Get / Set Data schema is rebuilt
    @Test void pickedFacesRemainAvailableWithoutLinkerOptions(){
        var target = target(UUID.randomUUID(), new BlockPos(1000, 70, 1000));
        var node = node(target, Direction.SOUTH);
        AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node, target);
        assertEquals(Direction.SOUTH, AdvancedContraptionControllerBlockEntity.selectedDataTargetFace(node, target));
        assertEquals(6, node.data().getCompound("InputOptions").getList("face", 8).size());
        assertEquals("direction", node.data().getCompound("DynamicInputs").getString("face"));
    }

    // Read and write loaded child blocks even when world chunks report their plot as unloaded
    @Test void loadedChildDataTargetsUseTheirSelectedFace(){
        Level level = mock(Level.class);
        var target = target(UUID.randomUUID(), new BlockPos(1000, 70, 1000));
        var node = node(target, Direction.WEST);
        when(level.getBlockState(any())).thenReturn(Blocks.REDSTONE_WIRE.defaultBlockState());
        try(var simulated = mockStatic(SimulatedHelper.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(level, target.subLevelId())).thenReturn(level);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(eq(level), any(), any())).thenReturn(true);
            var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(level, node, target);
            assertNotNull(resolved);
            assertEquals(target.blockPos(), resolved.pos());
            assertEquals(Direction.WEST, resolved.side());
            assertTrue(AdvancedContraptionControllerBlockEntity.graphDataPorts(level, resolved.pos(), true).contains("state_power"));
            assertFalse(level.isLoaded(resolved.pos()));
        }
    }

    // Keep composed port bindings and rotated face writes after native transfers and graph reloads
    @Test void composedGetAndSetBindingsFollowNativeTransfers() throws Exception{
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(mock(MinecraftServer.class));
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        var state = new AtomicReference<>(Blocks.REDSTONE_WIRE.defaultBlockState());
        when(level.getBlockState(any())).thenAnswer(ctx -> state.get());
        when(level.setBlock(any(), any(), anyInt())).thenAnswer(ctx -> {
            state.set(ctx.getArgument(1));
            return true;
        });
        AtomicLong tick = new AtomicLong();
        when(level.getGameTime()).thenAnswer(ctx -> tick.get());
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        controller.setLevel(level);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(AdvancedContraptionControllerBlockEntity.class, controller, "graphTargetAccessCache", new IdentityHashMap<>());
        setField(AdvancedContraptionControllerBlockEntity.class, controller, "missingGraphTargetAccessCache", new HashSet<>());
        var ctor = SubLevelTrackingPointSavedData.class.getDeclaredConstructor(ServerLevel.class);
        ctor.setAccessible(true);
        var points = ctor.newInstance(level);
        UUID initialId = UUID.randomUUID();
        BlockPos initial = new BlockPos(1000, 70, 1000);
        var target = target(initialId, initial);
        var original = node(target, Direction.EAST);
        CompoundTag saved = original.data().copy();
        try(var storage = mockStatic(SubLevelTrackingPointSavedData.class);
            var simulated = mockStatic(SimulatedHelper.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var levels = mockStatic(SableLevelApi.class, CALLS_REAL_METHODS);
            var bus = mockStatic(ContraptionNetworkLinkerSignalBus.class)){
            storage.when(() -> SubLevelTrackingPointSavedData.getOrLoad(level)).thenReturn(points);
            collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(eq(level), any())).thenReturn(level);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(eq(level), any(), any())).thenReturn(true);
            BlockPos pos = initial;
            Direction face = Direction.EAST;
            UUID subLevelId = initialId;
            for(int step = 0; step < 4; step++){
                tick.incrementAndGet();
                var node = new AdvancedGraphDocument.Node(original.id(), original.type(), "", 0, 0, saved.copy());
                var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(level, node, target);
                assertNotNull(resolved);
                assertEquals(pos, resolved.pos());
                assertEquals(face, resolved.side());
                var preview = ControllerDiscoveryNode.fromTag(node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, 10).getCompound(0));
                assertEquals(pos, preview.blockPos());
                assertEquals(subLevelId, preview.subLevelId());
                assertEquals(target.nodeId(), preview.nodeId());
                Map<String, AdvancedGraphDocument.Value> writes = Map.of(
                        "state_power", AdvancedGraphDocument.Value.number(step + 5),
                        GraphSignalRange.REDSTONE_SIGNAL_PORT, AdvancedGraphDocument.Value.number(11));
                assertTrue(controller.setGraphTargetData(node, writes.keySet(), writes::get));
                assertEquals(step + 5, controller.getGraphTargetData(node, "state_power").asNumber());
                BlockPos expectedPos = pos;
                UUID expectedId = subLevelId;
                Direction expectedFace = face;
                verify(level).setBlock(eq(pos), argThat(val -> val.getValue(BlockStateProperties.POWER) == writes.get("state_power").asNumber()), anyInt());
                bus.verify(() -> ContraptionNetworkLinkerSignalBus.setSignal(level, expectedId, expectedPos,
                        expectedFace, "0:graph:output:picked", 11, null, true));
                assertEquals(target.nodeId(), node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG)
                        .getList("state_power", 10).getCompound(0).getString("Target"));
                assertEquals("east", node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG).getString(target.nodeId()));
                bus.clearInvocations();
                BlockPos moved = pos.offset(1000, 0, 0);
                ServerSubLevel destination = step == 1 ? null : mock(ServerSubLevel.class);
                subLevelId = destination == null ? null : UUID.randomUUID();
                if(destination != null) when(destination.getUniqueId()).thenReturn(subLevelId);
                Rotation rotation = step == 1 ? Rotation.CLOCKWISE_90 : Rotation.NONE;
                SubLevelAssemblyHelper.moveTrackingPoints(level, BoundingBox3i.from(List.of(pos)), destination,
                        new SubLevelAssemblyHelper.AssemblyTransform(pos, moved, step == 1 ? 3 : 0, rotation, level));
                face = rotation.rotate(face);
                pos = moved;
            }
        }
    }

    // Build the same stable block identity used by the 3D picker
    private static ControllerDiscoveryNode target(UUID subLevelId, BlockPos pos){
        return new ControllerDiscoveryNode("scm_picker:" + subLevelId + ":" + pos.asLong(),
                ControllerDiscoveryKind.MACHINE, "scm_picker", "", "Block", subLevelId, pos);
    }

    // Build one composed target with its saved face and generated data port binding
    private static AdvancedGraphDocument.Node node(ControllerDiscoveryNode target, Direction face){
        CompoundTag data = new CompoundTag();
        data.put("TargetData", target.toTag());
        data.putString("Target", target.nodeId());
        ListTag targets = new ListTag();
        targets.add(target.toTag());
        data.put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        CompoundTag faces = new CompoundTag();
        faces.putString(target.nodeId(), face.getSerializedName());
        data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces);
        CompoundTag binding = new CompoundTag();
        binding.putString("Target", target.nodeId());
        binding.putString("Port", "state_power");
        ListTag entries = new ListTag();
        entries.add(binding);
        CompoundTag bindings = new CompoundTag();
        bindings.put("state_power", entries);
        data.put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        return new AdvancedGraphDocument.Node("picked", "set_block_data", "", 0, 0, data);
    }

    // Initialize block-entity state skipped by Mockito's constructor-free instances
    private static void setField(Class<?> type, Object instance, String name, Object val) throws Exception{
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, val);
    }
}
