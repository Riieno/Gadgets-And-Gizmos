package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsMotionApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise moving destination bodies through the production docking control path
@GameTestHolder("createthrusters_docking_lanes")
@PrefixGameTestTemplate(false)
public final class MovingDockingGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void stationaryDockKeepsItsOriginalCommandAndFeedback(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            String id = "graph_dock:stationary", key = id + '\0' + "ship_dock";
            issue(runtime, id, fixture.tip(), fixture.platform().getUniqueId());
            Object cmd = commands(runtime).get(key);
            invoke(runtime, "refreshDockReferences");
            helper.assertTrue(commands(runtime).get(key) == cmd, "A stationary target changed its command pose");
            Object telemetry = point(runtime, cmd);
            helper.assertTrue(invoke(runtime, "dockRelativeTelemetry", telemetry, cmd, null) == telemetry,
                    "Stationary docking changed the original feedback");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void translatingDockMatchesVelocityAndCompletesWhileMoving(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            String id = "graph_dock:translation", key = id + '\0' + "ship_dock";
            issue(runtime, id, fixture.tip(), fixture.platform().getUniqueId());
            Object cmd = commands(runtime).get(key);
            Vec3 drift = new Vec3(3, .5, -2);
            setMotion(fixture.platform(), drift, Vec3.ZERO);
            setMotion(fixture.body(), drift.add(2, 0, 0), Vec3.ZERO);
            invoke(runtime, "refreshDockReferences");
            invoke(runtime, "invalidateTelemetryCache");
            invoke(runtime, "updateCommandCompletion", invoke(runtime, "telemetry"));
            helper.assertTrue(!runtime.isCommandComplete(id, "ship_dock"), "Capture accepted excessive closure speed");
            setMotion(fixture.body(), drift, Vec3.ZERO);
            invoke(runtime, "invalidateTelemetryCache");
            Object relative = invoke(runtime, "dockRelativeTelemetry", point(runtime, cmd), cmd, null);
            near(helper, Vec3.ZERO, (Vec3)invoke(relative, "velocity"), "Common translation was treated as closure speed");
            Object guidance = invoke(runtime, "dockingApproachGuidance", point(runtime, cmd), cmd);
            Object demand = demand(runtime, cmd, guidance, point(runtime, cmd));
            helper.assertTrue((double)invoke(invoke(demand, "speedPlan"), "brakeStrength") < 1.0E-6,
                    "The Brake group tried to stop a velocity-matched ship");
            invoke(runtime, "updateCommandCompletion", invoke(runtime, "telemetry"));
            helper.assertTrue(runtime.isCommandComplete(id, "ship_dock"), "A moving matched connector could not complete");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void rotationMatchesPointVelocityAndAngularRate(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            String id = "graph_dock:rotation", key = id + '\0' + "ship_dock";
            issue(runtime, id, fixture.tip(), fixture.platform().getUniqueId());
            Vec3 baselineTorque = (Vec3)invoke(runtime, "dockingAlignmentTorque", invoke(runtime, "telemetry"), commands(runtime).get(key));
            Vec3 angular = new Vec3(.1, .25, -.05);
            setMotion(fixture.platform(), new Vec3(2, 1, -1), angular);
            var station = SablePhysicsMotionApi.sample(fixture.platform()).orElseThrow();
            Vector3d center = fixture.body().logicalPose().transformPosition(fixture.body().getMassTracker().getCenterOfMass(), new Vector3d());
            Vec3 velocity = station.velocityAt(fixture.tip()).subtract(angular.cross(
                    fixture.tip().subtract(new Vec3(center.x, center.y, center.z))));
            setMotion(fixture.body(), velocity, angular);
            invoke(runtime, "refreshDockReferences");
            invoke(runtime, "invalidateTelemetryCache");
            Object cmd = commands(runtime).get(key);
            Object relative = invoke(runtime, "dockRelativeTelemetry", point(runtime, cmd), cmd, null);
            near(helper, Vec3.ZERO, (Vec3)invoke(relative, "velocity"), "Rotation ignored the connector's tangential velocity");
            near(helper, Vec3.ZERO, (Vec3)invoke(relative, "angularVelocity"), "Rotation damping opposed the destination's turn");
            near(helper, baselineTorque, (Vec3)invoke(runtime, "dockingAlignmentTorque", invoke(runtime, "telemetry"), cmd),
                    "Alignment torque opposed an already matched turn rate");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void approachConvergesOnATranslatingConnector(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            var system = SubLevelPhysicsSystem.get(helper.getLevel());
            system.getPipeline().teleport(fixture.body(), new Vector3d(0, 32, 0), new Quaterniond());
            system.getPipeline().teleport(fixture.platform(), new Vector3d(100, 32, 0), new Quaterniond());
            system.updatePose(fixture.body());
            system.updatePose(fixture.platform());
            invoke(runtime, "invalidateTelemetryCache");
            String id = "graph_dock:converging", key = id + '\0' + "ship_dock";
            issue(runtime, id, fixture.tip().add(0, 0, -3), fixture.platform().getUniqueId());
            Vec3 drift = new Vec3(1.2, .3, -.5), velocity = Vec3.ZERO;
            setMotion(fixture.platform(), drift, Vec3.ZERO);
            for(int step = 0; step < 400; step++){
                move(fixture.platform(), drift.scale(.05), new Quaterniond(fixture.platform().logicalPose().orientation()));
                invoke(runtime, "refreshDockReferences");
                invoke(runtime, "invalidateTelemetryCache");
                Object cmd = commands(runtime).get(key), telemetry = point(runtime, cmd);
                Object guidance = invoke(runtime, "dockingApproachGuidance", telemetry, cmd);
                Vec3 acceleration = (Vec3)invoke(invoke(demand(runtime, cmd, guidance, telemetry), "output"), "force");
                velocity = velocity.add(acceleration.scale(.05));
                move(fixture.body(), velocity.scale(.05), new Quaterniond(fixture.body().logicalPose().orientation()));
                setMotion(fixture.body(), velocity, Vec3.ZERO);
            }
            Object cmd = commands(runtime).get(key);
            helper.assertTrue(((Vec3)invoke(cmd, "targetPosition")).distanceTo(fixture.tip()) <= .3,
                    "The approach did not converge within its connector capture tolerance");
            helper.assertTrue(velocity.subtract(drift).length() < .03, "The approach did not settle to the destination velocity");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void distantRotatingEntryUsesTheTargetPointsVelocity(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            String id = "graph_dock:rotating_entry", key = id + '\0' + "ship_dock";
            Vec3 target = fixture.tip().add(0, 0, -100);
            issue(runtime, id, target, fixture.platform().getUniqueId());
            setMotion(fixture.platform(), new Vec3(2, 0, 1), new Vec3(0, .25, 0));
            var frame = SablePhysicsMotionApi.sample(fixture.platform()).orElseThrow();
            setMotion(fixture.body(), frame.velocityAt(target), Vec3.ZERO);
            invoke(runtime, "refreshDockReferences");
            invoke(runtime, "invalidateTelemetryCache");
            Object cmd = commands(runtime).get(key), telemetry = point(runtime, cmd);
            Object guidance = invoke(runtime, "dockingApproachGuidance", telemetry, cmd);
            Object relative = invoke(runtime, "dockRelativeTelemetry", telemetry, cmd, guidance);
            near(helper, Vec3.ZERO, (Vec3)invoke(relative, "velocity"),
                    "The approach invented orbital speed at the distant ship instead of tracking the entry");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void movingLaneRetainsProgressAndSurvivesReload(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            Vec3 start = fixture.tip(), target = start.add(10, 0, -10);
            String id = "graph_dock:moving_lane", key = id + '\0' + "ship_navigate";
            helper.assertTrue(runtime.executeApproachSpline(id, Map.of("x", target.x, "y", target.y, "z", target.z, "speed", 4.0),
                    Map.of("target_point", "docking_connector:0", "dock_sub_level_id", fixture.platform().getUniqueId().toString(),
                            "dock_reference_sub_level_id", fixture.platform().getUniqueId().toString()),
                    List.of(start, start.add(0, 0, -10), target), false, UUID.randomUUID()), "The moving lane was rejected");
            var route = (SplineRouteTraversal)((Map<?, ?>)state(runtime, "authoredDockRoutes")).get(key);
            route.update(start.add(0, 0, -10), 1.25, .5);
            route.update(start.add(4, 0, -10), 1.25, .5);
            int segment = route.segment();
            double fraction = route.fraction();
            var prev = SablePhysicsMotionApi.sample(fixture.platform()).orElseThrow();
            var snapshot = runtime.createShutdownSnapshot();
            move(fixture.platform(), new Vec3(6, 2, -3), new Quaterniond().rotateXYZ(.2, .4, -.1));
            runtime.restoreShutdownSnapshot(snapshot);
            invoke(runtime, "refreshDockReferences");
            var next = SablePhysicsMotionApi.sample(fixture.platform()).orElseThrow();
            Object cmd = commands(runtime).get(key);
            near(helper, next.movePositionFrom(prev, target), (Vec3)invoke(cmd, "targetPosition"), "Reload lost the local docking target");
            var moved = (SplineRouteTraversal)((Map<?, ?>)state(runtime, "authoredDockRoutes")).get(key);
            helper.assertTrue(moved.captured() && moved.segment() == segment && Math.abs(moved.fraction() - fraction) < 1.0E-8,
                    "Moving the lane reset its ordered progress");
            near(helper, next.movePositionFrom(prev, start), moved.entry(), "The lane entry remained at an old world position");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void changingDockMassCannotShiftTheBoundConnector(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            var body = fixture.platform();
            Vec3 target = fixture.tip();
            Vector3d local = new Vector3d(target.x, target.y, target.z);
            body.logicalPose().transformPositionInverse(local);
            Vector3d prevCenter = new Vector3d(body.getMassTracker().getCenterOfMass());
            String id = "graph_dock:mass_change", key = id + '\0' + "ship_dock";
            issue(runtime, id, target, body.getUniqueId());
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                for(int idx = 0; idx <= 12; idx++) body.getPlot().getEmbeddedLevelAccessor()
                        .setBlock(new BlockPos(0, 6, idx), Blocks.AIR.defaultBlockState(), 3);
            });
            body.buildMassTracker();
            body.updateMergedMassData(1.0F);
            helper.assertTrue(prevCenter.distance(body.getMassTracker().getCenterOfMass()) > .05,
                    "The fixture did not change its centre of mass");
            invoke(runtime, "refreshDockReferences");
            Vector3d expected = body.logicalPose().transformPosition(local, new Vector3d());
            near(helper, new Vec3(expected.x, expected.y, expected.z), (Vec3)invoke(commands(runtime).get(key), "targetPosition"),
                    "Changing body mass shifted the connector's material target");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "movingDock")
    public static void missingDestinationCannotCompleteAndOrdinaryCoordinatesStayFixed(GameTestHelper helper) throws Exception{
        try(var fixture = ShipDockTrafficGameTests.fixture(helper)){
            var runtime = fixture.runtime();
            String id = "graph_dock:missing";
            issue(runtime, id, fixture.tip(), UUID.randomUUID());
            invoke(runtime, "updateCommandCompletion", invoke(runtime, "telemetry"));
            helper.assertTrue(!runtime.isCommandComplete(id, "ship_dock"), "An unloaded destination completed docking");
            Vec3 target = fixture.tip().add(0, 0, 20);
            helper.assertTrue(runtime.execute("coordinates", "ship_navigate", Map.of("x", target.x, "y", target.y, "z", target.z),
                    Map.of("target_point", "docking_connector:0", "dock_sub_level_id", fixture.platform().getUniqueId().toString())),
                    "Coordinate command was rejected");
            move(fixture.platform(), new Vec3(10, 0, 0), new Quaterniond().rotateY(.4));
            invoke(runtime, "refreshDockReferences");
            near(helper, target, (Vec3)invoke(commands(runtime).get("coordinates" + '\0' + "ship_navigate"), "targetPosition"),
                    "Ordinary world coordinates started following a dock body");
            helper.succeed();
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void issue(ShipControlModuleRuntime runtime, String id, Vec3 target, UUID body){
        if(!runtime.execute(id, "ship_dock", Map.of("x", target.x, "y", target.y, "z", target.z, "speed", .35, "tolerance", .25,
                "target_direction_z", -1.0, "target_up_y", 1.0),
                Map.of("target_point", "docking_connector:0", "dock_sub_level_id", body.toString(),
                        "dock_reference_sub_level_id", body.toString())))
            throw new IllegalStateException("Docking command rejected");
    }

    private static Object point(ShipControlModuleRuntime runtime, Object cmd) throws Exception{
        return invoke(runtime, "commandTargetTelemetry", invoke(runtime, "telemetry"), cmd);
    }

    private static Object demand(ShipControlModuleRuntime runtime, Object cmd, Object guidance, Object telemetry) throws Exception{
        Vec3 forward = (Vec3)invoke(runtime, "controllerForwardRoot"), up = (Vec3)invoke(runtime, "controllerUpRoot");
        String key = invoke(cmd, "id") + "\0" + invoke(cmd, "type");
        return invoke(runtime, "controlModeDemand", key, telemetry, cmd, guidance,
                forward, up, forward.cross(up).normalize(), false);
    }

    private static void setMotion(ServerSubLevel body, Vec3 velocity, Vec3 angular){
        var handle = RigidBodyHandle.of(body);
        var linearDelta = new Vector3d(velocity.x, velocity.y, velocity.z).sub(handle.getLinearVelocity(new Vector3d()));
        var angularDelta = new Vector3d(angular.x, angular.y, angular.z).sub(handle.getAngularVelocity(new Vector3d()));
        handle.addLinearAndAngularVelocity(linearDelta, angularDelta);
    }

    private static void move(ServerSubLevel body, Vec3 offset, Quaterniond rotation){
        var system = SubLevelPhysicsSystem.get(body.getLevel());
        system.getPipeline().teleport(body, new Vector3d(body.logicalPose().position()).add(offset.x, offset.y, offset.z), rotation);
        system.updatePose(body);
    }

    private static void near(GameTestHelper helper, Vec3 expected, Vec3 actual, String message){
        helper.assertTrue(expected.distanceTo(actual) < .02, message + ": " + expected + " != " + actual);
    }

    private static Map<?, ?> commands(Object runtime) throws Exception{ return (Map<?, ?>)state(runtime, "activeCommands"); }
    private static Object state(Object target, String name) throws Exception{
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception{
        for(Method method : target.getClass().getDeclaredMethods()){
            if(!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            for(int idx = 0; idx < args.length; idx++) if(args[idx] != null && !method.getParameterTypes()[idx].isPrimitive()
                    && !method.getParameterTypes()[idx].isInstance(args[idx])) matches = false;
            if(!matches) continue;
            method.setAccessible(true);
            return method.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }
}
