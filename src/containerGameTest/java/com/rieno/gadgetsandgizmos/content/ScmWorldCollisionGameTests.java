package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Exercise SCM world queries against native terrain and a live Sable hull
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ScmWorldCollisionGameTests{
    private ScmWorldCollisionGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain world terrain when the destination is a docking connector
    @GameTest(template = "empty_container")
    public static void dockingApproachStillDetectsWorldWalls(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            BlockPos wall = fixture.base.offset(0, 0, 3);
            for(int x = 0; x < 8; x++){
                for(int y = 0; y < 8; y++) ctx.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            }
            call(fixture.runtime, "setDockCollisionExclusions", new Class<?>[]{Set.class, boolean.class}, Set.of(), true);
            Object scan = call(fixture.runtime, "collisionScanContext", new Class<?>[0]);
            ctx.assertTrue((boolean) call(scan, "includeWorldBlocks", new Class<?>[0]),
                    "Docking disabled root-world collision checks");
            double distance = (double) call(fixture.runtime, "collisionDistance",
                    new Class<?>[]{Vec3.class, Vec3.class, double.class}, fixture.anchor, new Vec3(0, 0, 1), 6.0D);
            ctx.assertTrue(distance < 1.0D, "SCM missed a world wall during docking: " + distance);
            ctx.succeed();
        }
    }

    // Detect a terrain post between sparse leading-face probes and react before contact
    @GameTest(template = "empty_container")
    public static void wideHullDetectsWorldPostsBetweenProbes(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            ctx.getLevel().setBlock(fixture.base.offset(2, 2, 3), Blocks.STONE.defaultBlockState(), 3);
            Vec3 direction = new Vec3(0, 0, 1);
            double sparse = (double) call(fixture.runtime, "collisionDistance",
                    new Class<?>[]{Vec3.class, Vec3.class, double.class}, fixture.anchor, direction, 6.0D);
            ctx.assertTrue(sparse >= 6.0D, "Fixture no longer places the post between probes: " + sparse);
            Class<?>[] types = {Vec3.class, Vec3.class, double.class, boolean.class, boolean.class, double.class};
            call(fixture.runtime, "reactiveSafetyCollisionDistance", types,
                    fixture.anchor, direction, 6.0D, false, false, 6.0D);
            List<?> scans = (List<?>) field(fixture.runtime, "safetyHullScans");
            ctx.assertTrue(!scans.isEmpty(), "SCM accepted sparse clear probes without checking the hull");
            for(Object pending : scans){
                var work = (SubLevelParticleOcclusion.SweptBoundsScan) field(pending, "work");
                for(int idx = 0; idx < 1000 && !work.isComplete(); idx++) work.advance(64, 0L);
                ctx.assertTrue(work.isComplete(), "Retained world sweep failed to finish");
            }
            ((Map<?, ?>) field(fixture.runtime, "safetyCollisionCache")).clear();
            double exact = (double) call(fixture.runtime, "reactiveSafetyCollisionDistance", types,
                    fixture.anchor, direction, 6.0D, false, false, 6.0D);
            ctx.assertTrue(exact < 1.0D, "SCM missed a post inside its swept hull: " + exact);
            Class<?> commandType = nested("ActiveShipCommand");
            Object command = call(commandType, null, "target",
                    new Class<?>[]{String.class, String.class, Vec3.class, double.class, double.class, boolean.class},
                    "world_post", "ship_navigate", fixture.anchor.add(0, 0, 12), 0.6D, 0.75D, true);
            Class<?> telemetryType = nested("Telemetry");
            var ctor = telemetryType.getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class,
                    Vec3.class, Vec3.class, Vec3.class);
            ctor.setAccessible(true);
            Object telemetry = ctor.newInstance(true, fixture.anchor, new Vec3(0, 0, 2), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
            Class<?> guidanceType = nested("NavigationGuidance");
            ctor = guidanceType.getDeclaredConstructor(Vec3.class, Vec3.class, double.class,
                    boolean.class, boolean.class, boolean.class, Vec3.class);
            ctor.setAccessible(true);
            Object guidance = ctor.newInstance(direction, fixture.anchor.add(0, 0, 12), 0.25D,
                    false, true, false, direction);
            Object avoided = call(fixture.runtime, "applyReactiveCollisionAvoidance",
                    new Class<?>[]{String.class, telemetryType, commandType, guidanceType},
                    "world_post", telemetry, command, guidance);
            ctx.assertTrue((boolean) call(avoided, "reactiveCollisionOverride", new Class<?>[0]),
                    "Terrain hit did not take reactive collision authority");
            ctx.assertTrue(((Vec3) call(avoided, "direction", new Class<?>[0])).lengthSqr() > 1.0E-8D,
                    "Terrain avoidance stopped despite clear escape space");
            ctx.succeed();
        }
    }

    // Keep every autonomous movement reactive after a schedule reset, including connector capture
    @GameTest(template = "empty_container")
    @SuppressWarnings("unchecked")
    public static void autonomousCommandsAvoidNearbyShipsAfterStopMarkers(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            var container = SubLevelContainer.getContainer(ctx.getLevel());
            var intruder = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
            try{
                var plot = intruder.getPlot();
                BlockPos center = plot.getCenterBlock();
                plot.newEmptyChunk(plot.getCenterChunk());
                plot.getLoadedChunks().iterator().next().getChunk().setBlockState(center, Blocks.STONE.defaultBlockState(), false);
                container.physicsSystem().getPipeline().onStatsChanged(intruder);
                Pose3d pose = new Pose3d();
                pose.position().set(fixture.base.getX() + 2, fixture.base.getY() + 2, fixture.base.getZ() + 2.5D);
                pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
                intruder.logicalPose().set(pose);
                intruder.updateLastPose();
                intruder.updateBoundingBox();
                Class<?> commandType = nested("ActiveShipCommand");
                Object marker = call(commandType, null, "idle", new Class<?>[]{String.class, String.class},
                        "shipping_schedule_undock_reset", "ship_stop");
                ((Map<String, Object>) field(fixture.runtime, "activeCommands")).put("stop", marker);
                set(fixture.runtime, "additionalRotationActions", Set.of("ship_pitch", "ship_roll"));
                set(fixture.runtime, "additionalTranslationActions", Set.of("ship_ascend", "ship_descend"));
                Class<?> telemetryType = nested("Telemetry");
                var ctor = telemetryType.getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class,
                        Vec3.class, Vec3.class, Vec3.class);
                ctor.setAccessible(true);
                Object telemetry = ctor.newInstance(true, fixture.anchor, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
                Class<?> guidanceType = nested("NavigationGuidance");
                Object stopped = call(guidanceType, null, "stopped", new Class<?>[]{Vec3.class}, fixture.anchor);
                for(String type : List.of("ship_navigate", "ship_follow", "ship_climb", "ship_dock")){
                    call(fixture.runtime, "setDockCollisionExclusions", new Class<?>[]{Set.class, boolean.class},
                            Set.of(), "ship_dock".equals(type));
                    Object command = call(commandType, null, "target",
                            new Class<?>[]{String.class, String.class, Vec3.class, double.class, double.class, boolean.class},
                            "autonomous_peer", type, fixture.anchor.add(0, 0, 12), 0.6D, 0.75D, true);
                    Object avoided = call(fixture.runtime, "applyReactiveCollisionAvoidance",
                            new Class<?>[]{String.class, telemetryType, commandType, guidanceType},
                            type, telemetry, command, stopped);
                    ctx.assertTrue((boolean) call(avoided, "reactiveCollisionOverride", new Class<?>[0]),
                            type + " ignored an adjacent sub-level after a Stop marker");
                    ctx.assertTrue(((Vec3) call(avoided, "direction", new Class<?>[0])).lengthSqr() > 1.0E-8D,
                            type + " failed to escape with free space around the hull");
                }
                ctx.succeed();
            }finally{
                SableSubLevelLifecycleApi.remove(ctx.getLevel(), List.of(intruder));
            }
        }
    }

    @GameTest(template = "empty_container")
    public static void pendingHullClearanceBrakesWithoutInventingAnObstacle(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            Vec3 direction = new Vec3(0,0,1);
            Object context = call(fixture.runtime,"collisionScanContext",new Class<?>[0]);
            Object hull = call(fixture.runtime,"cachedShipHullBounds",new Class<?>[]{Vec3.class,nested("CollisionScanContext")},fixture.anchor,context);
            @SuppressWarnings("unchecked") List<net.minecraft.world.phys.AABB> bounds = (List<net.minecraft.world.phys.AABB>)call(hull,
                    "worldBoundsAt",new Class<?>[]{Vec3.class,double.class,boolean.class},fixture.anchor,0.125D,false);
            var work = SubLevelParticleOcclusion.beginSweptBoundsBlockingDistanceScan(ctx.getLevel(),fixture.body,
                    fixture.anchor,direction,6D,bounds,true,Set.of(fixture.body.getUniqueId()),true,0.125D);
            Vec3 forward = (Vec3)call(fixture.runtime,"rootDirectionToWorld",new Class<?>[]{Vec3.class},
                    call(fixture.runtime,"controllerForwardRoot",new Class<?>[0]));
            Vec3 up = (Vec3)call(fixture.runtime,"rootDirectionToWorld",new Class<?>[]{Vec3.class},
                    call(fixture.runtime,"controllerUpRoot",new Class<?>[0]));
            var pendingCtor = nested("PendingHullScan").getDeclaredConstructors()[0];pendingCtor.setAccessible(true);
            Object pending = pendingCtor.newInstance(fixture.anchor,direction,forward,up,6D,false,
                    ctx.getLevel().getGameTime(),bounds,work);
            set(pending,"workTick",ctx.getLevel().getGameTime());
            @SuppressWarnings("unchecked") List<Object> scans = (List<Object>)field(fixture.runtime,"safetyHullScans");scans.add(pending);
            double clearance = (double)call(fixture.runtime,"reactiveSafetyCollisionDistance",
                    new Class<?>[]{Vec3.class,Vec3.class,double.class,boolean.class,boolean.class,double.class},
                    fixture.anchor,direction,6D,false,false,6D);
            ctx.assertTrue(clearance >= 6D && !work.isComplete(),"Unfinished clearance invented a wall or escaped its frame budget");
            var commandType = nested("ActiveShipCommand");
            Object command = call(commandType,null,"target",new Class<?>[]{String.class,String.class,Vec3.class,double.class,double.class,boolean.class},
                    "pending_speed","ship_navigate",fixture.anchor.add(0,0,30),8D,0.5D,true);
            var telemetryType = nested("Telemetry");var ctor = telemetryType.getDeclaredConstructors()[0];ctor.setAccessible(true);
            Object telemetry = ctor.newInstance(true,fixture.anchor,new Vec3(0,0,8),Vec3.ZERO,Vec3.ZERO,Vec3.ZERO);
            var guidanceType = nested("NavigationGuidance");
            ctor = guidanceType.getDeclaredConstructor(Vec3.class,Vec3.class,double.class,boolean.class,boolean.class,boolean.class,Vec3.class);ctor.setAccessible(true);
            Object guidance = ctor.newInstance(direction,fixture.anchor.add(0,0,30),0.6D,false,true,false,direction);
            Object plan = call(fixture.runtime,"navigationSpeedPlan",new Class<?>[]{String.class,telemetryType,commandType,guidanceType,Vec3.class,Vec3.class,boolean.class},
                    "pending_speed",telemetry,command,guidance,direction,forward,false);
            ctx.assertTrue((double)call(plan,"permittedSpeed",new Class<?>[0]) == 0D,
                    "Cruise accelerated through an unverified hull corridor");
            ctx.succeed();
        }
    }

    // A physical child stays inside ownership while its articulation changes the live hull
    @GameTest(template = "empty_container")
    public static void attachedChildDoesNotCollideWithItselfOrDiscardRouteOnSway(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            var container = SubLevelContainer.getContainer(ctx.getLevel());
            var child = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
            dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle joint = null;
            try{
                var plot = child.getPlot();
                BlockPos center = plot.getCenterBlock();
                plot.newEmptyChunk(plot.getCenterChunk());
                plot.getLoadedChunks().iterator().next().getChunk().setBlockState(center, Blocks.STONE.defaultBlockState(), false);
                container.physicsSystem().getPipeline().onStatsChanged(child);
                child.logicalPose().rotationPoint().set(center.getX(), center.getY(), center.getZ());
                child.logicalPose().position().set(fixture.base.getX()+4, fixture.base.getY()+3, fixture.base.getZ()+2);
                child.updateLastPose(); child.updateBoundingBox();
                Object before = call(fixture.runtime, "collisionScanContext", new Class<?>[0]);
                joint = (dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle)
                        com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi.addConstraint(
                        container.physicsSystem().getPipeline(), fixture.body, child,
                        com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi.fixedConfiguration(
                        new org.joml.Vector3d(fixture.body.getPlot().getCenterBlock().getX(),
                                fixture.body.getPlot().getCenterBlock().getY(),fixture.body.getPlot().getCenterBlock().getZ()),
                        new org.joml.Vector3d(center.getX(),center.getY(),center.getZ()),new org.joml.Quaterniond()));
                Object attached = call(fixture.runtime, "collisionScanContext", new Class<?>[0]);
                ctx.assertTrue(attached != before, "Attachment did not invalidate ownership during the same tick");
                Set<?> ids = (Set<?>)call(attached, "excludedSubLevelIds", new Class<?>[0]);
                ctx.assertTrue(ids.contains(child.getUniqueId()) && ids.contains(fixture.body.getUniqueId()),
                        "Attached child was treated as an external obstacle");
                call(fixture.runtime,"cachedShipHullBounds",new Class<?>[]{Vec3.class,nested("CollisionScanContext")},fixture.anchor,attached);
                Class<?> stateType = nested("NavigationPathState");
                var ctor = stateType.getDeclaredConstructor(); ctor.setAccessible(true);
                Object state = ctor.newInstance();
                set(state,"nextReplanTick",12345L);
                @SuppressWarnings("unchecked") Map<String,Object> states = (Map<String,Object>)field(fixture.runtime,"navigationPathStates");
                states.put("sway_route",state);
                child.logicalPose().position().add(1,0,0);
                child.logicalPose().orientation().rotateY(0.5);
                child.updateLastPose(); child.updateBoundingBox();
                set(fixture.runtime,"collisionHullCacheTick",Long.MIN_VALUE);
                call(fixture.runtime,"cachedShipHullBounds",new Class<?>[]{Vec3.class,nested("CollisionScanContext")},fixture.anchor,attached);
                ctx.assertTrue((long)field(state,"nextReplanTick") == 12345L, "Normal child sway restarted route planning");
                double distance = (double)call(fixture.runtime,"collisionDistance",new Class<?>[]{Vec3.class,Vec3.class,double.class},
                        fixture.anchor,new Vec3(0,0,1),4.0D);
                ctx.assertTrue(distance >= 4, "Own child blocked the compound hull: " + distance);
                checkAttachedTraffic(ctx,fixture,child,joint);
                Object detached = call(fixture.runtime,"collisionScanContext",new Class<?>[0]);
                ctx.assertTrue(!((Set<?>)call(detached,"excludedSubLevelIds",new Class<?>[0])).contains(child.getUniqueId()),
                        "Detached child stayed invisible to avoidance");
                ctx.succeed();
            }finally{
                if(joint != null && joint.isValid()) joint.remove();
                SableSubLevelLifecycleApi.remove(ctx.getLevel(),List.of(child));
            }
        }
    }

    @GameTest(template = "empty_container")
    public static void cachedTerrainOccupancySeesEditsImmediately(GameTestHelper ctx){
        BlockPos pos = ctx.absolutePos(new BlockPos(2,12,2));
        var chunk = ctx.getLevel().getChunkAt(pos);
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        ctx.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        int empty = com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex.nextOccupiedY(section,pos.getX(),pos.getY(),pos.getZ());
        ctx.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        int occupied = com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex.nextOccupiedY(section,pos.getX(),pos.getY(),pos.getZ());
        ctx.assertTrue(empty != (pos.getY() & 15) && occupied == (pos.getY() & 15), "Cached air hid a newly placed obstacle");
        ctx.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        int removed = com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex.nextOccupiedY(section,pos.getX(),pos.getY(),pos.getZ());
        ctx.assertTrue(removed != (pos.getY() & 15), "Removed terrain stayed occupied in the local map");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            ctx.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            section.write(buffer);
            ctx.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex.nextOccupiedY(section,pos.getX(),pos.getY(),pos.getZ());
            section.read(buffer);
            ctx.assertTrue(com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex.nextOccupiedY(section,
                    pos.getX(),pos.getY(),pos.getZ()) == (pos.getY() & 15), "Full section replacement kept cached air");
        }finally{
            buffer.release();
            ctx.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        ctx.succeed();
    }

    // Measure escape authority in the world frame after the hull rotates
    @GameTest(template = "empty_container")
    public static void rotatedShipsUseTheirActualEscapeAuthority(GameTestHelper ctx) throws Exception{
        try(Fixture fixture = new Fixture(ctx)){
            fixture.body.logicalPose().orientation().rotateY(Math.PI * 0.5D);
            fixture.body.updateLastPose();
            fixture.body.updateBoundingBox();
            Vec3 worldForce = (Vec3) call(fixture.runtime, "rootDirectionToWorld",
                    new Class<?>[]{Vec3.class}, new Vec3(10, 0, 0));
            var model = ScmAdaptiveStateModel.sample(List.of(new ScmAdaptiveStateModel.Actuator(
                    worldForce, Vec3.ZERO, 0)), 0.05D, 0.01D, 0.01D);
            ctx.assertTrue(model.available(), "Directional fixture has no measured actuator authority");
            set(fixture.runtime, "adaptiveStateModel", model);
            Vec3 supported = (Vec3) call(fixture.runtime, "rootDirectionToWorld",
                    new Class<?>[]{Vec3.class}, new Vec3(1, 0, 0));
            Vec3 unsupported = (Vec3) call(fixture.runtime, "rootDirectionToWorld",
                    new Class<?>[]{Vec3.class}, new Vec3(0, 0, 1));
            ctx.assertTrue((boolean) call(fixture.runtime, "canTranslateDetour", new Class<?>[]{Vec3.class}, supported),
                    "World rotation hid a powered escape axis");
            ctx.assertTrue(!(boolean) call(fixture.runtime, "canTranslateDetour", new Class<?>[]{Vec3.class}, unsupported),
                    "World rotation invented thrust on an unpowered escape axis");
            ctx.succeed();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build a wide native hull above the test structure without a calibration pass
    private static final class Fixture implements AutoCloseable{
        private final GameTestHelper ctx;
        private final BlockPos base;
        private final ServerSubLevel body;
        private final ShipControlModuleRuntime runtime;
        private final Vec3 anchor;

        private Fixture(GameTestHelper ctx) throws Exception{
            this.ctx = ctx;
            base = ctx.absolutePos(new BlockPos(1, 12, 1));
            var container = SubLevelContainer.getContainer(ctx.getLevel());
            body = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
            var plot = body.getPlot();
            BlockPos center = plot.getCenterBlock();
            plot.newEmptyChunk(plot.getCenterChunk());
            var chunk = plot.getLoadedChunks().iterator().next().getChunk();
            for(int x = 0; x < 8; x++){
                for(int y = 0; y < 8; y++){
                    for(int z = 0; z < 2; z++) chunk.setBlockState(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }
            }
            container.physicsSystem().getPipeline().onStatsChanged(body);
            Pose3d pose = new Pose3d();
            pose.position().set(base.getX(), base.getY(), base.getZ());
            pose.rotationPoint().set(center.getX(), center.getY(), center.getZ());
            body.logicalPose().set(pose);
            body.updateLastPose();
            body.updateBoundingBox();
            var controller = new AdvancedContraptionControllerBlockEntity(base.offset(10, 0, 0),
                    CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
            controller.setLevel(ctx.getLevel());
            runtime = (ShipControlModuleRuntime) field(controller, "shipControlRuntime");
            set(runtime, "rootSubLevel", body);
            anchor = Vec3.atLowerCornerOf(base).add(4, 4, 1);
        }

        @Override public void close(){
            runtime.close();
            SableSubLevelLifecycleApi.remove(ctx.getLevel(), List.of(body));
        }
    }

    // Existing carriage route intents must share the physical assembly's ownership
    @SuppressWarnings("unchecked")
    private static void checkAttachedTraffic(GameTestHelper ctx, Fixture fixture, ServerSubLevel child,
            dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle joint) throws Exception{
        var controller = new AdvancedContraptionControllerBlockEntity(fixture.base.offset(12,0,0),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(ctx.getLevel());
        var other = (ShipControlModuleRuntime)field(controller,"shipControlRuntime");
        try{
            set(other,"rootSubLevel",child);
            var commandType = nested("ActiveShipCommand");
            Object command = call(commandType,null,"target",new Class<?>[]{String.class,String.class,Vec3.class,double.class,double.class,boolean.class},
                    "owned_traffic","ship_navigate",fixture.anchor.add(0,0,20),4D,0.5D,true);
            ((Map<String,Object>)field(fixture.runtime,"activeCommands")).put("owned_traffic",command);
            ((Map<String,Object>)field(other,"activeCommands")).put("owned_traffic",command);
            var routeType = nested("TimedRouteTrafficIntent");var ctor = routeType.getDeclaredConstructors()[0];ctor.setAccessible(true);
            var subject = new com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority.Participant(
                    fixture.body.getUniqueId(),fixture.anchor,new Vec3(0,0,4),List.of(fixture.anchor,fixture.anchor.add(0,0,20)),
                    com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder.RouteMode.FLIGHT,2D,2D,4D);
            var peer = new com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority.Participant(
                    child.getUniqueId(),fixture.anchor.add(0,0,2),new Vec3(0,0,-4),List.of(fixture.anchor.add(0,0,2),fixture.anchor.add(0,0,-20)),
                    com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder.RouteMode.FLIGHT,2D,2D,4D);
            ((Map<String,Object>)field(fixture.runtime,"routeTrafficIntents")).put("owned_traffic",ctor.newInstance(ctx.getLevel().getGameTime(),subject));
            ((Map<String,Object>)field(other,"routeTrafficIntents")).put("owned_traffic",ctor.newInstance(ctx.getLevel().getGameTime(),peer));
            var decision = (com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority.Decision)call(fixture.runtime,
                    "routeTrafficDecision",new Class<?>[]{String.class},"owned_traffic");
            ctx.assertTrue(!decision.conflict(),"Coupled carriage's SCM competed with its own route");
            joint.remove();
            ((Map<?,?>)field(fixture.runtime,"routeTrafficDecisions")).clear();
            decision = (com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority.Decision)call(fixture.runtime,
                    "routeTrafficDecision",new Class<?>[]{String.class},"owned_traffic");
            ctx.assertTrue(decision.conflict(),"Detached ship stayed invisible to route traffic checks");
        }finally{ other.close(); }
    }

    // Resolve a private production value type
    private static Class<?> nested(String name) throws Exception{
        return Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
    }

    // Read retained work without widening the runtime API
    private static Object field(Object receiver, String name) throws Exception{
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    // Configure the native root for the consuming runtime
    private static void set(Object receiver, String name, Object val) throws Exception{
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(receiver, val);
    }

    // Invoke the SCM entry point used by live control
    private static Object call(Object receiver, String name, Class<?>[] types, Object... args) throws Exception{
        return call(receiver.getClass(), receiver, name, types, args);
    }

    private static Object call(Class<?> type, Object receiver, String name, Class<?>[] types, Object... args) throws Exception{
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }
}
