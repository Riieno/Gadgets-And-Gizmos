package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAttachmentApi;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LinkerFaceAttachmentTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test void everyConfiguredFaceBindsToItsSupportingNeighbor(){
        var block = mock(ContraptionNetworkLinkerPlaneBlock.class, CALLS_REAL_METHODS);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        for(Direction dir : Direction.values()){
            for(Direction face : Direction.values()){
                when(state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face))).thenReturn(face == dir);
            }
            assertTrue(block.isAttachedTo(state, dir.getOpposite()));
            assertFalse(block.isAttachedTo(state, dir));
        }
    }

    @Test void temporaryMissingSupportDoesNotDeleteAFaceDuringTransfer(){
        var block = mock(ContraptionNetworkLinkerPlaneBlock.class, CALLS_REAL_METHODS);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        for(Direction dir : Direction.values()){
            when(state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir))).thenReturn(dir == Direction.NORTH);
        }
        ServerLevel level = mock(ServerLevel.class);
        BlockPos face = new BlockPos(10, 70, 10);
        BlockPos support = face.south();
        var plane = mock(ContraptionNetworkLinkerPlaneBlockEntity.class);
        when(level.getBlockEntity(face)).thenReturn(plane);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(support, new BlockPos(1000, 70, 1000),
                0, Rotation.NONE, level);
        SubLevelAttachmentApi.moveBlocks(level, transform, List.of(support, face), () ->
                block.neighborChanged(state, level, face, Blocks.STONE, support, false));
        verify(plane, never()).removePlane(any());

        block.neighborChanged(state, level, face, Blocks.STONE, support, false);
        verify(plane).removePlane(Direction.NORTH);
    }
}
