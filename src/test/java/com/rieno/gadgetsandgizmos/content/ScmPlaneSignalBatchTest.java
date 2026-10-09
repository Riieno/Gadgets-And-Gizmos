package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScmPlaneSignalBatchTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }
    @AfterEach void clear(){ ContraptionNetworkLinkerSignalBus.clearAll(); }

    @Test
    void publishesTheWholeFleetBeforeAnyNeighborReadsIt() throws Exception{
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        try(var sable = mockStatic(SableLevelApi.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.ensureTargetLoaded(eq(level), isNull(), any()))
                    .thenReturn(true);
            doAnswer(call -> {
                for(int idx = 0; idx < 100; idx++){
                    assertEquals(12, ContraptionNetworkLinkerSignalBus.getPlaneBlockSignal(
                            level, new BlockPos(idx * 3, 0, 0), Direction.NORTH));
                }
                return null;
            }).when(level).neighborChanged(any(), any(), any());
            var revision = ContraptionNetworkLinkerSignalBus.class.getDeclaredField("planeSignalRevision");
            revision.setAccessible(true);
            long prev = revision.getLong(null);
            var batch = new ContraptionNetworkLinkerSignalBus.PlaneSignalBatch(level);
            for(int idx = 0; idx < 100; idx++){
                batch.set(null, new BlockPos(idx * 3, 0, 0), Direction.NORTH, "scm", 12);
            }
            batch.apply();
            assertEquals(prev + 1, revision.getLong(null));
            verify(level, times(100)).neighborChanged(any(), any(), any());
            clearInvocations(level);
            for(int idx = 0; idx < 100; idx++){
                BlockPos pos = new BlockPos(idx * 3, 0, 0);
                batch.set(null, pos, Direction.NORTH, "scm", 0);
                batch.set(null, pos, Direction.NORTH, "scm", 12);
            }
            batch.apply();
            assertEquals(prev + 1, revision.getLong(null));
            verify(level, never()).neighborChanged(any(), any(), any());
        }
    }

    @Test
    void releasingTheStrongestFacePreservesOtherSourcesAndAttachedFaces(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        try(var sable = mockStatic(SableLevelApi.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            collector.when(() -> SubLevelBlockEntityCollector.ensureTargetLoaded(eq(level), isNull(), any()))
                    .thenReturn(true);
            var batch = new ContraptionNetworkLinkerSignalBus.PlaneSignalBatch(level);
            var north = BlockPos.ZERO.relative(Direction.NORTH);
            var south = BlockPos.ZERO.relative(Direction.SOUTH);
            batch.set(null, north, Direction.NORTH, "scm", 15);
            batch.set(null, north, Direction.NORTH, "other", 7);
            batch.set(null, south, Direction.SOUTH, "scm", 10);
            batch.apply();
            assertEquals(15, ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, BlockPos.ZERO));
            batch.set(null, north, Direction.NORTH, "scm", 0);
            batch.apply();
            assertEquals(7, ContraptionNetworkLinkerSignalBus.getPlaneBlockSignal(level, north, Direction.NORTH));
            assertEquals(10, ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, BlockPos.ZERO));
            batch.set(null, south, Direction.SOUTH, "scm", 0);
            batch.apply();
            assertEquals(7, ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, BlockPos.ZERO));
            ContraptionNetworkLinkerSignalBus.clearSource(level, "other");
            assertEquals(0, ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, BlockPos.ZERO));
        }
    }
}
