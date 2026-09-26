package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedstoneTargetWriteTest {
    private static final BlockPos POS = new BlockPos(3, 64, 5);
    private static final String SIGNAL = GraphSignalRange.REDSTONE_SIGNAL_PORT;

    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void setDataWritesRealRedstoneAlongsideBlockData() throws Exception {
        Level level = mock(Level.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.REDSTONE_WIRE.defaultBlockState());
        when(level.setBlock(any(), any(), anyInt())).thenReturn(true);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        controller.setLevel(level);
        var position = BlockEntity.class.getDeclaredField("worldPosition");
        position.setAccessible(true);
        position.set(controller, BlockPos.ZERO);
        var linked = new ContraptionNetworkLinkerData.LinkedTarget(POS, null,
                "minecraft:redstone_wire", "Wire", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var target = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst();
        CompoundTag data = new CompoundTag();
        data.put("TargetData", target.toTag());
        var node = new AdvancedGraphDocument.Node("output", "set_block_data", "", 0, 0, data);
        Map<String, AdvancedGraphDocument.Value> values = Map.of(
                SIGNAL, AdvancedGraphDocument.Value.number(0.75),
                "state_power", AdvancedGraphDocument.Value.number(5));
        try (var simulated = mockStatic(SimulatedHelper.class);
             var collector = mockStatic(SubLevelBlockEntityCollector.class);
             var bus = mockStatic(ContraptionNetworkLinkerSignalBus.class)) {
            collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(level, null)).thenReturn(level);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, POS)).thenReturn(true);
            assertTrue(controller.setGraphTargetData(node, values.keySet(), values::get));
            verify(level).setBlock(eq(POS), argThat(state -> state.getValue(BlockStateProperties.POWER) == 5), anyInt());
            bus.verify(() -> ContraptionNetworkLinkerSignalBus.setBlockSignal(
                    level, null, POS, "0:graph:output:output", 11));
        }
    }

    @Test
    void finalRedstoneWriterRoundsAndDoesNotReplaceRcsDataControls() {
        Level level = mock(Level.class);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var rcs = mock(RcsThrusterBlockEntity.class);
        when(rcs.getLevel()).thenReturn(level);
        when(rcs.getBlockPos()).thenReturn(POS);
        var linked = new ContraptionNetworkLinkerData.LinkedTarget(POS, null,
                "createthrusters:rcs_thruster", "RCS", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var target = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst()
                .asDirectTargetReference().withCompatMode(ControllerRedstoneCompat.DIRECT_ADAPTER);
        try (var simulated = mockStatic(SimulatedHelper.class);
             var bus = mockStatic(ContraptionNetworkLinkerSignalBus.class)) {
            simulated.when(() -> SimulatedHelper.findLoadedBlockEntityExact(level, null, POS)).thenReturn(rcs);
            ControllerRedstoneCompat.writeRedstoneTarget(level, target, null, "test", 11);
            bus.verify(() -> ContraptionNetworkLinkerSignalBus.setBlockSignal(level, null, POS, "test", 11));
            verify(rcs, never()).setControllerThrottle(any(), anyString(), anyFloat());
        }
    }

    @Test
    void normalizedOutputIsClampedAndRoundedAtTheFinalWriter() {
        Level level = mock(Level.class);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var linked = new ContraptionNetworkLinkerData.LinkedTarget(POS, null,
                "minecraft:stone", "Stone", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var target = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst()
                .asDirectTargetReference().withCompatMode(ControllerRedstoneCompat.VIRTUAL_BLOCK_REDSTONE);
        try (var simulated = mockStatic(SimulatedHelper.class);
             var collector = mockStatic(SubLevelBlockEntityCollector.class);
             var bus = mockStatic(ContraptionNetworkLinkerSignalBus.class)) {
            collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(level, null)).thenReturn(level);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, POS)).thenReturn(true);
            for (float[] sample : new float[][]{{-1, 0}, {0.75f, 11}, {0.5f, 8}, {1, 15}, {5, 15}, {Float.NaN, 0}}) {
                bus.clearInvocations();
                ControllerRedstoneCompat.writeTarget(level, target, null, "test", sample[0]);
                bus.verify(() -> ContraptionNetworkLinkerSignalBus.setBlockSignal(level, null, POS, "test", (int) sample[1]));
            }
        }
    }
}
