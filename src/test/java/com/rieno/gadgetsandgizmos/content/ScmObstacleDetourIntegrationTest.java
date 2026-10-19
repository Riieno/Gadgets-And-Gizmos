package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify the consuming runtime keeps authored routes intact through successive live bypasses
class ScmObstacleDetourIntegrationTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void wideFlatAirshipsUseTheirActualHeightForRouteClearance() throws Exception{
        Class<?> hullType = nested("HullBounds");
        var ctor = hullType.getDeclaredConstructor(List.class, double.class, double.class,
                double.class, double.class, double.class, double.class);
        ctor.setAccessible(true);
        Object hull = ctor.newInstance(List.of(new AABB(-8, -0.5D, -2, 8, 0.5D, 2)),
                -8.0D, -0.5D, -2.0D, 8.0D, 0.5D, 2.0D);
        Method safetyMethod = ShipControlModuleRuntime.class.getDeclaredMethod("sableRouteSafety", hullType, boolean.class);
        safetyMethod.setAccessible(true);
        var safety = (SablePathfinder.Safety) safetyMethod.invoke(null, hull, false);
        AABB bounds = safety.boundsAt(Vec3.ZERO);
        assertEquals(1.5D, bounds.getYsize(), 1.0E-8D);
        assertEquals(-0.75D, bounds.minY, 1.0E-8D);
        assertTrue(bounds.getXsize() > 16.0D);
    }

    // Give local avoidance its own plan when a destination search is already queued
    @Test void liveBypassTakesOverAQueuedDestinationSearch() throws Exception{
        Object state = state();
        Vec3 target = new Vec3(0, 0, 12);
        var plan = SablePathfinder.queue(new SablePathfinder.Request(null,
                SablePathfinder.Location.world(Vec3.ZERO), SablePathfinder.Location.world(target),
                null, null, 1, 32, 128, 0.25D, query -> SablePathfinder.Traversal.blocked()));
        set(state, "queuedSableRoute", plan);
        set(state, "plannedTarget", target);
        Class<?>[] types = {Vec3.class, int.class, Vec3.class, double.class};
        assertTrue((boolean) invoke(state, "obstaclePlanInvalid", types, target, -1, null, 1.0D));
        set(state, "obstacleDetourQueued", true);
        assertFalse((boolean) invoke(state, "obstaclePlanInvalid", types, target, -1, null, 1.0D));
    }

    // Preserve a short vertical bypass even when the stationary hull already overlaps its first bend
    @Test void liveAirshipCompletesItsClimbBeforeAdvancingTheRoute() throws Exception{
        Object state = state();
        Vec3 climb = new Vec3(0, 0.375D, 0);
        Vec3 target = new Vec3(0, 0.375D, 4);
        invoke(state, "setSableRoute", new Class<?>[]{List.class, Vec3.class, Vec3.class, long.class},
                List.of(climb, target), target, Vec3.ZERO, 0L);
        Class<?> hullType = nested("HullBounds");
        var ctor = hullType.getDeclaredConstructor(List.class, double.class, double.class,
                double.class, double.class, double.class, double.class);
        ctor.setAccessible(true);
        Object hull = ctor.newInstance(List.of(new AABB(-1, -1, -1, 1, 1, 1)),
                -1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        Class<?>[] types = {Vec3.class, hullType, boolean.class, double.class, boolean.class};
        invoke(state, "advanceOverlappedRoute", types, Vec3.ZERO, hull, false, 0.1D, true);
        assertEquals(0, get(state, "waypointIndex"));
        assertTrue((boolean) invoke(state, "hasSpatialBend", new Class<?>[]{boolean.class}, true));
        assertEquals(0.0625D, (double) invoke(state, "waypointCaptureRadius",
                new Class<?>[]{boolean.class, double.class}, true, 2.0D));
        invoke(state, "advanceOverlappedRoute", types, climb, hull, false, 0.1D, true);
        assertEquals(1, get(state, "waypointIndex"));
    }

    @Test void successiveBypassesKeepTheScheduleAndReturnToTrafficPriorityAfterRejoining() throws Exception{
        Object state = state();
        Vec3 target = new Vec3(0, 0, 40);
        List<Vec3> authored = List.of(Vec3.ZERO, new Vec3(0, 0, 10), new Vec3(0, 0, 20),
                new Vec3(0, 0, 30), target);
        invoke(state, "adoptScheduledRoute", new Class<?>[]{List.class, Vec3.class, Vec3.class,
                Vec3.class, long.class, nested("CompletedScheduledRoute"), boolean.class},
                authored, Vec3.ZERO, target, Vec3.ZERO, 0L, null, false);
        var spline = (WaypointSpline) get(state, "scheduleSpline");
        List<Vec3> original = List.copyOf(spline.waypoints());
        assertFalse(committed(state));

        splice(state, List.of(new Vec3(-4, 0, 0), new Vec3(-4, 0, 12), new Vec3(0, 0, 12)),
                12.0D, target, Vec3.ZERO, 1L);
        assertTrue(committed(state));
        assertSame(spline, get(state, "scheduleSpline"));
        assertEquals(original, spline.waypoints());
        Object bypass = get(state, "waypoints");
        invoke(state, "recaptureScheduledSpline",
                new Class<?>[]{WaypointSpline.Projection.class, Vec3.class, long.class},
                spline.project(Vec3.ZERO, 0, 0.0D), Vec3.ZERO, 1L);
        assertSame(bypass, get(state, "waypoints"));
        assertTrue(committed(state));

        // A new blocker on the committed prefix must allow another bypass without replacing its source
        set(state, "waypointIndex", 1);
        splice(state, List.of(new Vec3(-6, 3, 0), new Vec3(-6, 3, 24), new Vec3(0, 0, 24)),
                24.0D, target, new Vec3(-4, 0, 0), 2L);
        assertTrue(committed(state));
        assertSame(spline, get(state, "scheduleSpline"));
        assertEquals(original, spline.waypoints());
        var retained = (List<?>) get(state, "waypoints");
        assertEquals(target, retained.getLast());
        set(state, "waypointIndex", get(state, "scheduleSplineResumeWaypoint"));
        assertFalse(committed(state));
        assertSame(spline, get(state, "scheduleSpline"));
        Vec3 merge = new Vec3(0, 0, 24);
        invoke(state, "recaptureScheduledSpline",
                new Class<?>[]{WaypointSpline.Projection.class, Vec3.class, long.class},
                spline.project(merge, 0, 0.0D), merge, 3L);
        assertEquals(0, get(state, "scheduleSplineResumeWaypoint"));
    }

    @Test void offsetNavigationAnchorsKeepTheWholeHullInsideTheLoadedEnvelope() throws Exception{
        Class<?> hullType = nested("HullBounds");
        var ctor = hullType.getDeclaredConstructor(List.class, double.class, double.class,
                double.class, double.class, double.class, double.class);
        ctor.setAccessible(true);
        Method safetyMethod = ShipControlModuleRuntime.class.getDeclaredMethod("sableRouteSafety", hullType, boolean.class);
        safetyMethod.setAccessible(true);
        for(AABB bounds : List.of(new AABB(-2, 1, -2, 2, 2, 2), new AABB(-2, -2, -2, 2, -1, 2))){
            Object hull = ctor.newInstance(List.of(bounds), bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
            var safety = (SablePathfinder.Safety) safetyMethod.invoke(null, hull, false);
            AABB envelope = safety.boundsAt(Vec3.ZERO);
            assertTrue(envelope.minY <= bounds.minY - 0.25D);
            assertTrue(envelope.maxY >= bounds.maxY + 0.25D);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Class<?> nested(String name) throws ClassNotFoundException{
        return Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
    }

    private static Object state() throws Exception{
        var ctor = nested("NavigationPathState").getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean committed(Object state) throws Exception{
        return (boolean) invoke(state, "hasCommittedObstacleDetour", new Class<?>[]{});
    }

    private static void splice(Object state, List<Vec3> prefix, double distance,
            Vec3 target, Vec3 origin, long tick) throws Exception{
        int rejoin = (int) invoke(state, "scheduleWaypointAtOrAfter", new Class<?>[]{double.class}, distance);
        assertTrue(rejoin >= 0);
        invoke(state, "spliceSableRoute", new Class<?>[]{List.class, int.class, Vec3.class, Vec3.class, long.class},
                prefix, rejoin, target, origin, tick);
    }

    private static Object invoke(Object state, String name, Class<?>[] types, Object... args) throws Exception{
        Method method = state.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(state, args);
    }

    private static Object get(Object state, String name) throws Exception{
        Field field = state.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(state);
    }

    private static void set(Object state, String name, Object val) throws Exception{
        Field field = state.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(state, val);
    }
}
