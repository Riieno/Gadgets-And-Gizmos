package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.SplineRouteTraversal;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import com.rieno.gadgetsandgizmos.lib.physics.SableMapMarkers;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Exercise loaded Sable markers and read-only extraction of the movement controller's retained paths
@GameTestHolder("createthrusters_physics_regressions")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletMapGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", batch = "tablet_map")
    public static void nearbyShipsExcludeTheCurrentCraftAndDoNotLoadChunks(GameTestHelper helper){
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 12, 2)).getCenter();
        var bodies = new ArrayList<ServerSubLevel>();
        try{
            for(Vec3 pos : List.of(center, center.add(35, 0, 20), center.add(600, 0, 0))){
                var body = ShipDoorGameTests.newBody(helper, pos);
                bodies.add(body);
                body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
                body.updateBoundingBox();
            }
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var markers = SableMapMarkers.capture(level, center, Set.of(bodies.getFirst().getUniqueId()), 128);
            helper.assertTrue(markers.size() == 1 && markers.getFirst().id().equals(bodies.get(1).getUniqueId()),
                    "Nearby marker selection included this craft or a distant craft: " + markers);
            helper.assertTrue(loaded == level.getChunkSource().getLoadedChunksCount(), "Map discovery loaded terrain chunks");
        }finally{ SableSubLevelLifecycleApi.remove(level, bodies); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_map")
    public static void autoMarkersRequireAnInitializedScm(GameTestHelper helper) throws ReflectiveOperationException{
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 12, 2)).getCenter();
        var bodies = new ArrayList<ServerSubLevel>();
        try{
            for(int idx = 0; idx < 4; idx++){
                var body = ShipDoorGameTests.newBody(helper, center.add(idx * 25, 0, 0));
                bodies.add(body);
                body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
                body.updateBoundingBox();
            }
            var own = mapController(helper, bodies.get(0), true);
            var ship = mapController(helper, bodies.get(1), true);
            mapController(helper, bodies.get(3), false);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var excluded = Set.of(bodies.getFirst().getUniqueId());
            var markers = DiagnosticTabletAutoApp.nearbyShips(level, center, excluded);
            helper.assertTrue(own.getShipControlGraphValue("ready").asBoolean()
                    && ship.getShipControlGraphValue("ready").asBoolean(), "The initialized SCM fixtures were not ready");
            helper.assertTrue(markers.size() == 1 && markers.getFirst().id().equals(bodies.get(1).getUniqueId()),
                    "Auto marked a loose assembly, uninitialized controller or its own craft: " + markers);
            ((ShipControlModuleRuntime)field(ship, "shipControlRuntime")).setMapId(null);
            helper.assertTrue(DiagnosticTabletAutoApp.nearbyShips(level, center, excluded).isEmpty(),
                    "Auto retained a marker after SCM initialization was cleared");
            helper.assertTrue(loaded == level.getChunkSource().getLoadedChunksCount(), "SCM marker filtering loaded terrain chunks");
        }finally{ SableSubLevelLifecycleApi.remove(level, bodies); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_map")
    @SuppressWarnings("unchecked")
    public static void routeSnapshotsUseTheRetainedCurveWithoutAdvancingOrPlanning(GameTestHelper helper) throws ReflectiveOperationException{
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        var acc = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(pos);
        Object runtime = field(acc, "shipControlRuntime");
        var commandType = nested(runtime, "ActiveShipCommand");
        var create = commandType.getDeclaredMethod("target", String.class, String.class, Vec3.class, double.class, double.class, boolean.class);
        create.setAccessible(true);
        var spline = WaypointSpline.of(List.of(Vec3.ZERO, new Vec3(20, 0, -40), new Vec3(60, 0, -30), new Vec3(80, 0, 0)));
        var target = spline.waypoints().getLast();
        String key = "tablet_map_fixture" + (char)0 + "ship_navigate";
        ((Map<String, Object>)field(runtime, "activeCommands")).put(key, create.invoke(null, "tablet_map_fixture", "ship_navigate", target, .65D, 1D, true));
        var constructor = nested(runtime, "NavigationPathState").getDeclaredConstructor();
        constructor.setAccessible(true);
        var state = constructor.newInstance();
        set(state, "scheduleSpline", spline);
        set(state, "scheduleSplineSegment", 1);
        set(state, "minimumScheduleSplineFraction", .6D);
        ((Map<String, Object>)field(runtime, "navigationPathStates")).put(key, state);
        Vec3 current = spline.segments().get(1).pointAtFraction(.6);
        var schedule = acc.navigationMapRoute(current, null);
        helper.assertTrue(schedule.spline() && schedule.points().size() > 3 && schedule.target().equals(target), "The retained schedule spline was replaced by a direct line");
        helper.assertTrue((int)field(state, "scheduleSplineSegment") == 1 && (double)field(state, "minimumScheduleSplineFraction") == .6D
                && field(state, "queuedSableRoute") == null, "Map extraction changed the route cursor or started a planner");
        var approach = new SplineRouteTraversal(spline, false);
        set(approach, "segment", 1); set(approach, "fraction", .6D);
        ((Map<String, SplineRouteTraversal>)field(runtime, "authoredDockRoutes")).put(key, approach);
        var dock = acc.navigationMapRoute(current, null);
        helper.assertTrue(dock.equals(schedule) && approach.segment() == 1 && approach.fraction() == .6D, "The dock preview changed or skipped the active approach spline");
        helper.succeed();
    }

    private static AdvancedContraptionControllerBlockEntity mapController(GameTestHelper helper, ServerSubLevel body,
                                                                         boolean initialized) throws ReflectiveOperationException{
        var view = body.getPlot().getEmbeddedLevelAccessor();
        view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var pos = body.getPlot().getCenterBlock().offset(0, 2, 0);
        var controller = (AdvancedContraptionControllerBlockEntity)body.getLevel().getBlockEntity(pos);
        if(initialized){
            var map = new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                    body.getUniqueId(), pos, Vec3.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
            ShipControlMapStore.save(helper.getLevel(), map);
            var runtime = (ShipControlModuleRuntime)field(controller, "shipControlRuntime");
            runtime.setMapId(map.id());
            runtime.tick();
        }
        body.updateBoundingBox();
        return controller;
    }

    private static Class<?> nested(Object owner, String name){
        return Arrays.stream(owner.getClass().getDeclaredClasses()).filter(type -> type.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static void set(Object owner, String name, Object val) throws ReflectiveOperationException{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, val);
    }
}
