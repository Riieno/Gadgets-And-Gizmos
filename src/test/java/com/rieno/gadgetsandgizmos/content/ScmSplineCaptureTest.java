package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyCache;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSplineConstraint;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import java.util.Arrays;
import java.util.UUID;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ScmSplineCaptureTest{
    @BeforeAll
    static void bootstrap(){
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void allVehicleModesRetireStaleDetoursOnAlignedOverlap() throws Exception{
        for(ResourceLocation mode : List.of(ScmBuiltinControlModes.GROUND_SEA_ID,
                ScmBuiltinControlModes.PLANE_ID, ScmBuiltinControlModes.AIRSHIP_ID)){
            try(Fixture ctx = new Fixture(mode, 22.5D, 0.0D)){
                ctx.restore();
                assertFalse((boolean)read(ctx.state, "obstacleDetourQueued"));
                assertFalse((boolean)read(ctx.state, "scheduleRouteRejoinActive"));
                assertEquals(0, read(ctx.state, "scheduleSplineResumeWaypoint"));
                assertSame(ctx.spline, read(ctx.state, "scheduleSpline"));
                assertEquals(7, read(ctx.state, "precomputedScheduleEntry"));
                List<?> route = (List<?>)read(ctx.state, "waypoints");
                assertTrue(((Vec3)route.getFirst()).x >= 5.0D);
                assertTrue(route.stream().allMatch(point -> Math.abs(((Vec3)point).z) < 1.0E-8D));
            }
        }
    }

    @Test
    void overlapOutsideTheHeadingLimitKeepsLiveRecovery() throws Exception{
        try(Fixture ctx = new Fixture(ScmBuiltinControlModes.GROUND_SEA_ID, 22.6D, 0.0D)){
            ctx.restore();
            assertTrue((boolean)read(ctx.state, "obstacleDetourQueued"));
            assertEquals(3, read(ctx.state, "scheduleSplineResumeWaypoint"));
        }
    }

    @Test
    void collisionReleaseCooldownStillKeepsLiveAvoidanceInControl() throws Exception{
        try(Fixture ctx = new Fixture(ScmBuiltinControlModes.GROUND_SEA_ID, 0.0D, 0.0D)){
            write(ctx.state, "splineReattachAfterTick", 120L);
            ctx.restore();
            assertTrue((boolean)read(ctx.state, "obstacleDetourQueued"));
            assertEquals(3, read(ctx.state, "scheduleSplineResumeWaypoint"));
        }
    }

    @Test
    void groundAndSeaIgnoreHeightWhileFlightRequiresPhysicalOverlap() throws Exception{
        try(Fixture ctx = new Fixture(ScmBuiltinControlModes.GROUND_SEA_ID, 0.0D, 8.0D)){
            ctx.restore();
            assertFalse((boolean)read(ctx.state, "obstacleDetourQueued"));
        }
        try(Fixture ctx = new Fixture(ScmBuiltinControlModes.PLANE_ID, 0.0D, 8.0D)){
            ctx.restore();
            assertTrue((boolean)read(ctx.state, "obstacleDetourQueued"));
        }
    }

    @Test
    void movingGroundVehicleKeepsItsJointWhenOnlySupportAndCeilingAreNearby() throws Exception{
        try(Fixture ctx = new Fixture(ScmBuiltinControlModes.GROUND_SEA_ID, 0, 1);
            CollisionFixture collision = new CollisionFixture(ctx)){
            Object command = record("ActiveShipCommand", "route", "ship_navigate", 0.0D, 1.0D, 0.0D,
                    new Vec3(60, 1, 0), 4.0D, 1.0D, 0.25D, true, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS, -1, Vec3.ZERO, Vec3.ZERO, 7, null);
            ((Map<String, Object>)read(ctx.runtime, "activeCommands")).put("route", command);
            SableSplineConstraint constraint = (SableSplineConstraint)read(ctx.runtime, "scheduleSplineConstraint");
            var step = SableSplineConstraint.class.getDeclaredMethod("step", double.class);
            step.setAccessible(true);
            for(int tick = 0; tick < 12; tick++){
                collision.pose.position().set(5 + tick * 0.2D, 1, 0);
                Object telemetry = record("Telemetry", true, new Vec3(5 + tick * 0.2D, 1, 0),
                        new Vec3(4, 0, 0), Vec3.ZERO, Vec3.ZERO);
                Object guidance = record("NavigationGuidance", new Vec3(1, 0, 0), new Vec3(10, 1, 0),
                        0.6D, false, false, false, new Vec3(1, 0, 0), false, 0.0D, 0.0D, false);
                invoke(ctx.runtime, "restoreConstraintCapturableScheduledRoute", "route", telemetry);
                guidance = invoke(ctx.runtime, "scheduledSplineConstraintGuidance", "route", telemetry, command, guidance, false);
                Object reactive = invoke(ctx.runtime, "applyReactiveCollisionAvoidance", "route", telemetry, command, guidance);
                assertFalse((boolean)read(reactive, "reactiveCollisionOverride"));
                assertTrue((boolean)read(ctx.runtime, "scheduleSplineConstraintRequested"),
                        (String)read(ctx.runtime, "scheduleSplineConstraintDecision"));
                step.invoke(constraint, 0.05D);
                assertTrue(constraint.rigid(), constraint.diagnostic());
                assertEquals(0.0D, (double)read(guidance, "steeringFeedForward"), 1.0E-8D);
            }
            ctx.runtime.close();
            step.invoke(constraint, 0.05D);
        }
    }

    // Keep calculated route steering distinct from collision detours
    @Test
    void authoredSplineKeepsLeadSteeringForCoupledCarriages() throws Exception{
        UUID lead = UUID.randomUUID();
        UUID tail = UUID.randomUUID();
        var topology = new SableAssemblyTopologyApi.Topology(lead, true, List.of(), List.of(), List.of(
                new SableAssemblyTopologyApi.CarriagePartition(lead, List.of(lead), 0, true),
                new SableAssemblyTopologyApi.CarriagePartition(tail, List.of(tail), 1, false)), 1, "train");
        Object spline = record("NavigationGuidance", new Vec3(1, 0, 0), new Vec3(10, 0, 0),
                0.6D, false, false, true, new Vec3(1, 0, 0), false, 0.0D, 0.0D, true);
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("isAssemblyCollisionTranslation",
                SableAssemblyTopologyApi.Topology.class, spline.getClass());
        method.setAccessible(true);
        assertFalse((boolean)method.invoke(null, topology, spline));
        Object avoidance = record("NavigationGuidance", new Vec3(0, 0, 1), new Vec3(0, 0, 10),
                0.6D, false, false, true, new Vec3(0, 0, 1), true, 0.0D, 0.0D, false);
        assertTrue((boolean)method.invoke(null, topology, avoidance));
    }

    // Construct the runtime's existing value objects without widening the public API
    private static Object record(String name, Object... values) throws Exception{
        Class<?> type = Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
        Class<?>[] types = Arrays.stream(type.getRecordComponents()).map(component -> component.getType()).toArray(Class<?>[]::new);
        var ctor = type.getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(values);
    }

    // Exercise the real navigation and clearance methods
    private static Object invoke(Object owner, String name, Object... values) throws Exception{
        var method = Arrays.stream(owner.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == values.length)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(owner, values);
    }

    // Keep collision geometry and the physics body live while the vehicle moves along the route
    private static final class CollisionFixture implements AutoCloseable{
        private final MockedStatic<SubLevelBlockEntityCollector> collector = mockStatic(SubLevelBlockEntityCollector.class);
        private final MockedStatic<SableLevelApi> levels = mockStatic(SableLevelApi.class);
        private final MockedStatic<SubLevelPhysicsSystem> systems = mockStatic(SubLevelPhysicsSystem.class);
        private final Pose3d pose = new Pose3d();

        @SuppressWarnings({"unchecked", "rawtypes"})
        private CollisionFixture(Fixture ctx) throws Exception{
            try{
                UUID id = UUID.randomUUID();
                when(ctx.root.getUniqueId()).thenReturn(id);
                when(ctx.root.getLevel()).thenReturn(ctx.level);
                when(ctx.root.logicalPose()).thenReturn(pose);
                when(ctx.root.boundingBox()).thenAnswer(call -> new BoundingBox3d(
                        pose.position().x - 1, 0, -1, pose.position().x + 1, 2, 1));
                when(ctx.controller.getScmConfigurationProfile()).thenReturn(ScmConfigurationProfile.empty());
                ServerChunkCache chunks = mock(ServerChunkCache.class);
                LevelChunk chunk = mock(LevelChunk.class);
                when(ctx.level.getChunkSource()).thenReturn(chunks);
                when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
                when(chunk.getBlockState(any())).thenAnswer(call -> {
                    int y = ((BlockPos)call.getArgument(0)).getY();
                    return y < 0 || y >= 2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                });
                levels.when(() -> SableLevelApi.serverLevel(ctx.level)).thenReturn(ctx.level);
                collector.when(() -> SubLevelBlockEntityCollector.getSubLevels(ctx.level)).thenReturn(List.of());
                SableAssemblyTopologyCache cache = mock(SableAssemblyTopologyCache.class);
                SableAssemblyTopologyApi.Topology topology = new SableAssemblyTopologyApi.Topology(id, true,
                        List.of(new SableAssemblyTopologyApi.Body(id, ctx.root, 0, 0, id)), List.of(),
                        List.of(new SableAssemblyTopologyApi.CarriagePartition(id, List.of(id), 0, true)), 0, "single");
                when(cache.get(ctx.root)).thenReturn(topology);
                write(ctx.runtime, "assemblyTopologyCache", cache);
                write(ctx.runtime, "cachedAssemblyTopology", topology);
                write(ctx.runtime, "connectedSubLevelsRootId", id);
                SubLevelPhysicsSystem system = mock(SubLevelPhysicsSystem.class);
                PhysicsPipeline pipeline = mock(PhysicsPipeline.class);
                RigidBodyHandle body = mock(RigidBodyHandle.class);
                GenericConstraintHandle joint = mock(GenericConstraintHandle.class);
                MassData mass = mock(MassData.class);
                when(ctx.root.getMassTracker()).thenReturn(mass);
                when(mass.getMass()).thenReturn(100.0D);
                when(system.getPhysicsHandle(ctx.root)).thenReturn(body);
                when(system.getPipeline()).thenReturn(pipeline);
                when(body.isValid()).thenReturn(true);
                when(body.getLinearVelocity(any(Vector3d.class))).thenAnswer(call -> ((Vector3d)call.getArgument(0)).set(4, 0, 0));
                when(body.getAngularVelocity(any(Vector3d.class))).thenAnswer(call -> ((Vector3d)call.getArgument(0)).zero());
                when(joint.isValid()).thenReturn(true);
                when(pipeline.readPose(same(ctx.root), any(Pose3d.class))).thenReturn(pose);
                when(pipeline.addConstraint(isNull(), same(ctx.root), any(GenericConstraintConfiguration.class))).thenReturn(joint);
                systems.when(() -> SubLevelPhysicsSystem.get(ctx.level)).thenReturn(system);
                Object phase = read(ctx.runtime, "phase");
                write(ctx.runtime, "phase", Enum.valueOf((Class)phase.getClass(), "READY"));
                invoke(ctx.runtime, "collisionScanContext");
                ((Map<String, Object>)read(ctx.runtime, "navigationPathStates")).put("route", ctx.state);
            }catch(Exception | Error err){
                close();
                throw err;
            }
        }

        @Override
        public void close(){
            systems.close();
            levels.close();
            collector.close();
        }
    }

    // Read private runtime state without adding test hooks to the public API
    private static Object read(Object owner, String name) throws Exception{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    // Seed one real route transition
    private static void write(Object owner, String name, Object val) throws Exception{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, val);
    }

    // Supply a live hull and an unfinished detour through the SCM's actual overlap path
    private static final class Fixture implements AutoCloseable{
        private final AdvancedContraptionControllerBlockEntity controller =
                mock(AdvancedContraptionControllerBlockEntity.class);
        private final ServerLevel level = mock(ServerLevel.class);
        private final ServerSubLevel root = mock(ServerSubLevel.class);
        private final MockedStatic<SimulatedHelper> simulated = mockStatic(SimulatedHelper.class);
        private final WaypointSpline spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(60, 0, 0)));
        private final ShipControlModuleRuntime runtime;
        private final Object state;
        private final Object telemetry;

        @SuppressWarnings("unchecked")
        private Fixture(ResourceLocation modeId, double heading, double height) throws Exception{
            ScmControlMode mode = mock(ScmControlMode.class);
            when(mode.id()).thenReturn(modeId);
            when(controller.getShipControlMode()).thenReturn(mode);
            when(controller.getScmOrientation()).thenReturn(new ScmOrientation(Direction.EAST, Direction.UP));
            when(controller.getLevel()).thenReturn(level);
            when(level.getGameTime()).thenReturn(100L);
            Pose3d pose = new Pose3d();
            double halfHeading = Math.toRadians(heading) * 0.5D;
            pose.orientation().set(0.0D, Math.sin(halfHeading), 0.0D, Math.cos(halfHeading));
            when(root.logicalPose()).thenReturn(pose);
            when(root.boundingBox()).thenReturn(new BoundingBox3d(4, height - 1, -1, 6, height + 1, 1));
            simulated.when(() -> SimulatedHelper.getContainingSubLevel(controller)).thenReturn(root);
            runtime = new ShipControlModuleRuntime(controller);
            write(runtime, "rootSubLevel", root);
            Class<?> stateClass = Class.forName(ShipControlModuleRuntime.class.getName() + "$NavigationPathState");
            var stateCtor = stateClass.getDeclaredConstructor();
            stateCtor.setAccessible(true);
            state = stateCtor.newInstance();
            write(state, "precomputedScheduleRoute", true);
            write(state, "precomputedScheduleEntry", 7);
            write(state, "scheduleSpline", spline);
            write(state, "scheduleRouteRejoinActive", true);
            write(state, "obstacleDetourQueued", true);
            write(state, "waypoints", List.of(new Vec3(5, 0, 4), new Vec3(5, 0, 8),
                    new Vec3(20, 0, 0), new Vec3(60, 0, 0)));
            write(state, "reverseWaypoints", List.of(false, false, false, false));
            write(state, "scheduleSplineResumeWaypoint", 3);
            write(state, "plannedTarget", new Vec3(60, 0, 0));
            ((Map<String, Object>)read(runtime, "navigationPathStates")).put("route", state);
            Class<?> telemetryClass = Class.forName(ShipControlModuleRuntime.class.getName() + "$Telemetry");
            var telemetryCtor = telemetryClass.getDeclaredConstructor(boolean.class,
                    Vec3.class, Vec3.class, Vec3.class, Vec3.class);
            telemetryCtor.setAccessible(true);
            telemetry = telemetryCtor.newInstance(true, new Vec3(5, height, 0),
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        }

        // Run the same recapture check used before and after live navigation
        private void restore() throws Exception{
            var method = ShipControlModuleRuntime.class.getDeclaredMethod(
                    "restoreConstraintCapturableScheduledRoute", String.class, telemetry.getClass());
            method.setAccessible(true);
            method.invoke(runtime, "route", telemetry);
        }

        @Override
        public void close(){
            try{
                runtime.close();
            }finally{
                simulated.close();
            }
        }
    }
}
