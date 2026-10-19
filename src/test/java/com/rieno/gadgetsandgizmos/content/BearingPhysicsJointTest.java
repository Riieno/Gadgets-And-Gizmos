package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Exercise bearing attachment and retargeting through Sable's public joints
class BearingPhysicsJointTest{
    // Initialize registries before loading block entities
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // Reuse the existing vector joint while applying tilt and shaft rotation through its public frames
    @Test void vectorRetargetsAndReleasesASolverJoint() throws Exception{
        var assembly = new VectorBearingMountedAssembly();
        var pipeline = mock(PhysicsPipeline.class);
        var parent = mock(ServerSubLevel.class);
        var child = mock(ServerSubLevel.class);
        var handle = mock(GenericConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(eq(parent), eq(child), any(GenericConstraintConfiguration.class))).thenReturn(handle);
        var pos = new BlockPos(30000000, 70, 30000000);
        var anchor = new Vector3d(4.5, 70.5, 8.5);
        var orientation = new Quaterniond().rotateX(0.3).rotateZ(-0.4).rotateY(1.2);
        assertEquals(true, invoke(assembly, "ensureJoint", pos, Direction.UP, anchor, orientation, pipeline, parent, child));
        invoke(assembly, "retargetJoint", pos, Direction.UP, anchor, orientation);
        assertEquals(true, invoke(assembly, "ensureJoint", pos, Direction.UP, anchor, orientation, pipeline, parent, child));
        verify(handle).setFrame1(anchor, orientation);
        verify(handle).setFrame2(new Vector3d(30000000.5, 71, 30000000.5), new Quaterniond());
        verify(pipeline).addConstraint(eq(parent), eq(child), any(GenericConstraintConfiguration.class));
        verifyNoMoreInteractions(pipeline);
        verifyNoInteractions(parent, child);
        assembly.releaseJoint();
        verify(handle).remove();
    }

    // Release failed vector joints so the next tick can recreate them
    @Test void failedVectorRetargetRecreatesTheJoint() throws Exception{
        var assembly = new VectorBearingMountedAssembly();
        var pipeline = mock(PhysicsPipeline.class);
        var child = mock(ServerSubLevel.class);
        var handle = mock(GenericConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(child), any(GenericConstraintConfiguration.class))).thenReturn(handle);
        var pos = BlockPos.ZERO;
        var anchor = new Vector3d();
        var orientation = new Quaterniond();
        assertEquals(true, invoke(assembly, "ensureJoint", pos, Direction.NORTH, anchor, orientation, pipeline, null, child));
        doThrow(new IllegalStateException("scene unavailable")).when(handle).setFrame1(any(), any());
        invoke(assembly, "retargetJoint", pos, Direction.NORTH, anchor, orientation);
        verify(handle).remove();
        assertEquals(true, invoke(assembly, "ensureJoint", pos, Direction.NORTH, anchor, orientation, pipeline, null, child));
        verify(pipeline, times(2)).addConstraint(isNull(), eq(child), any(GenericConstraintConfiguration.class));
        verifyNoMoreInteractions(pipeline);
    }

    // Preserve the positive shaft axis for both opposing aileron heads
    @Test void opposingAileronHeadsUseRotaryJoints() throws Exception{
        var bearing = mock(AileronBearingBlockEntity.class);
        when(bearing.getHeadDirection(BearingHead.PRIMARY)).thenReturn(Direction.EAST);
        when(bearing.getHeadDirection(BearingHead.SECONDARY)).thenReturn(Direction.WEST);
        var pipeline = mock(PhysicsPipeline.class);
        var child = mock(ServerSubLevel.class);
        var handle = mock(RotaryConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(child), any(RotaryConstraintConfiguration.class))).thenReturn(handle);
        var pos = new BlockPos(30000000, 70, 30000000);
        var axis = new Vector3d(1, 0, 0);
        for(var head : BearingHead.values()){
            var assembly = new AileronBearingMountedAssembly(head);
            assertEquals(true, invoke(assembly, "ensureJoint", bearing, pos, new Vector3d(), axis, pipeline, null, child));
            assertEquals(true, invoke(assembly, "ensureJoint", bearing, pos, new Vector3d(), axis, pipeline, null, child));
            assembly.releaseJoint();
        }
        var config = ArgumentCaptor.forClass(RotaryConstraintConfiguration.class);
        verify(pipeline, times(2)).addConstraint(isNull(), eq(child), config.capture());
        assertEquals(30000001, config.getAllValues().get(0).pos2().x());
        assertEquals(30000000, config.getAllValues().get(1).pos2().x());
        for(var val : config.getAllValues()){
            assertEquals(axis, val.normal1());
            assertEquals(axis, val.normal2());
        }
        verify(handle, times(2)).remove();
        verifyNoMoreInteractions(pipeline);
    }

    // Attach a thruster plate using each side's facing and retain its assembly anchor
    @Test void thrusterUsesARotaryJointAndReleasesIt() throws Exception{
        var bearing = mock(ThrusterBearingBlockEntity.class);
        var level = mock(ServerLevel.class);
        var field = BlockEntity.class.getDeclaredField("level");
        field.setAccessible(true);
        field.set(bearing, level);
        var plate = new BlockPos(30000000, 70, 30000000);
        when(bearing.getPlatePos()).thenReturn(plate);
        when(bearing.getBlockPos()).thenReturn(new BlockPos(4, 70, 8));
        when(bearing.getFacing()).thenReturn(Direction.NORTH);
        var state = mock(BlockState.class);
        when(level.getBlockState(plate)).thenReturn(state);
        when(state.getBlock()).thenReturn(mock(SwivelBearingPlateBlock.class));
        when(state.getValue(SwivelBearingPlateBlock.FACING)).thenReturn(Direction.EAST);
        var frame = mock(NestedAssemblyFrame.class);
        var container = mock(ServerSubLevelContainer.class);
        var system = mock(SubLevelPhysicsSystem.class);
        var pipeline = mock(PhysicsPipeline.class);
        when(container.physicsSystem()).thenReturn(system);
        when(system.getPipeline()).thenReturn(pipeline);
        var child = mock(ServerSubLevel.class);
        var handle = mock(RotaryConstraintHandle.class);
        when(handle.isValid()).thenReturn(true);
        when(pipeline.addConstraint(isNull(), eq(child), any(RotaryConstraintConfiguration.class))).thenReturn(handle);
        try(var frames = mockStatic(NestedAssemblyFrame.class); var containers = mockStatic(SubLevelContainer.class)){
            frames.when(() -> NestedAssemblyFrame.resolve(bearing)).thenReturn(frame);
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            assertEquals(true, invoke(bearing, "attachThrusterLinks", child, new Vector3d(30000001.5, 70.5, 30000000.5)));
        }
        var config = ArgumentCaptor.forClass(RotaryConstraintConfiguration.class);
        verify(pipeline).addConstraint(isNull(), eq(child), config.capture());
        assertEquals(new Vector3d(4.5, 70.5, 7.5), config.getValue().pos1());
        assertEquals(30000001.499, config.getValue().pos2().x(), 1.0E-6);
        assertEquals(new Vector3d(0, 0, -1), config.getValue().normal1());
        assertEquals(new Vector3d(1, 0, 0), config.getValue().normal2());
        invoke(bearing, "removeThrusterLinkHandle");
        verify(handle).remove();
        verifyNoMoreInteractions(pipeline);
    }

    // Call private attachment paths without allocating plots or native scenes
    private static Object invoke(Object target, String name, Object... args) throws Exception{
        Method method = Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(val -> val.getName().equals(name) && val.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
