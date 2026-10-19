package com.rieno.gadgetsandgizmos.content;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmLoadRecoveryTest{
    private final AdvancedContraptionControllerBlockEntity controller = mock(AdvancedContraptionControllerBlockEntity.class);
    private final ShippingScheduleRuntime schedule = new ShippingScheduleRuntime(controller);
    private final ShipControlModuleRuntime flight = new ShipControlModuleRuntime(controller);

    @AfterEach void removeRuntimes() throws Exception{
        ((Set<?>)field(schedule, "LIVE_RUNTIMES").get(null)).remove(schedule);
        ((Set<?>)field(flight, "LIVE_RUNTIMES").get(null)).remove(flight);
    }

    @Test void chunkUnloadKeepsDockAndWaitState() throws Exception{
        savedDock("WAITING");
        schedule.suspendForChunkUnload();
        assertSavedDock("WAITING");
        schedule.resumeAfterLoad();
        assertSavedDock("WAITING");
        verify(controller, never()).activateShipDockingConnector(anyInt());
    }

    @Test void shutdownKeepsAnEstablishedDockAndDoesNotDisengageIt() throws Exception{
        savedDock("DOCKED");
        schedule.prepareForServerShutdown();
        assertSavedDock("DOCKED");
        verify(controller, never()).activateShipDockingConnector(anyInt());
    }

    @Test void unavailableRootDefersMapLoadingRatherThanLatchingAnError() throws Exception{
        set(flight, "mapId", UUID.randomUUID());
        try(var levels = mockStatic(SableLevelApi.class)){
            call(flight, "loadMap");
        }
        assertEquals("IDLE", get(flight, "phase").toString());
        assertFalse((boolean)get(flight, "mapLoadAttempted"));
        assertNull(get(flight, "map"));
    }

    @Test void loadedChunksDoNotReleaseThePoseBeforeTheScheduleRestoresControl() throws Exception{
        ServerSubLevel root = mock(ServerSubLevel.class);
        UUID id = UUID.randomUUID();
        set(flight, "rootSubLevel", root);
        ((Map<UUID, Object>)get(flight, "initializationSubLevels")).put(id, root);
        FixedConstraintHandle constraint = mock(FixedConstraintHandle.class);
        when(constraint.isValid()).thenReturn(true);
        ((List<Object>)get(flight, "freezeHandles")).add(constraint);
        set(flight, "resumePoseHoldAfterLoad", true);
        set(flight, "resumePoseHoldTimeoutTicks", 100);
        set(flight, "resumePoseHoldTicks", 0);
        set(flight, "phase", phase(flight, "READY"));
        set(flight, "map", mock(ShipControlMap.class));
        try(var residency = mockStatic(SableSubLevelResidency.class)){
            residency.when(() -> SableSubLevelResidency.areFullyLoaded(anyCollection())).thenReturn(true);
            assertEquals(false, call(flight, "tickResumePoseHold"));
            verify(constraint, never()).remove();
            flight.requestPostLoadPoseRelease();
            assertEquals(true, call(flight, "tickResumePoseHold"));
            verify(constraint).remove();
        }
    }

    @Test void chunkUnloadRetainsNonScheduleFlightCommands() throws Exception{
        when(controller.getShipName()).thenReturn("Recovery test");
        Class<?> commandType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ActiveShipCommand");
        var amount = commandType.getDeclaredMethod("amount", String.class, String.class, double.class);
        amount.setAccessible(true);
        Object command = amount.invoke(null, "takeoff", "ship_ascend", 0.5D);
        ((Map<String, Object>)get(flight, "activeCommands")).put("takeoff", command);
        set(flight, "phase", phase(flight, "READY"));
        flight.suspendForChunkUnload();
        assertEquals(1, flight.createShutdownSnapshot().getList("ActiveCommands", 10).size());
        assertEquals("takeoff", flight.createShutdownSnapshot().getList("ActiveCommands", 10).getCompound(0).getString("Id"));
    }

    @Test void blockedDescentCannotCancelTheSelectedClimb() throws Exception{
        var level = mock(net.minecraft.world.level.Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.getScmConfigurationProfile()).thenReturn(ScmConfigurationProfile.empty());
        set(flight, "verifiedSafetyTick", 0L);
        set(flight, "verifiedSafetyClearance", 0.0D);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 target = new Vec3(0, 10, 0);
        var telemetryType = Class.forName(ShipControlModuleRuntime.class.getName() + "$Telemetry");
        var telemetryCtor = telemetryType.getDeclaredConstructor(boolean.class, Vec3.class,
                Vec3.class, Vec3.class, Vec3.class, Vec3.class);
        telemetryCtor.setAccessible(true);
        Object telemetry = telemetryCtor.newInstance(true, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        var commandType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ActiveShipCommand");
        var commandFactory = commandType.getDeclaredMethod("target", String.class, String.class,
                Vec3.class, double.class, double.class, boolean.class);
        commandFactory.setAccessible(true);
        Object command = commandFactory.invoke(null, "recover", "ship_navigate", target, 1.0D, 0.1D, true);
        var guidance = new ShipControlModuleRuntime.NavigationGuidance(up, target, 1.0D,
                false, true, true, up, true);
        var speedPlan = ShipControlModuleRuntime.class.getDeclaredMethod("navigationSpeedPlan", String.class,
                telemetryType, commandType, ShipControlModuleRuntime.NavigationGuidance.class,
                Vec3.class, Vec3.class, boolean.class);
        speedPlan.setAccessible(true);
        try(var levels = mockStatic(SableLevelApi.class)){
            Object plan = speedPlan.invoke(flight, "recover", telemetry, command, guidance,
                    up, new Vec3(0, 0, 1), false);
            var speed = plan.getClass().getDeclaredMethod("permittedSpeed");
            speed.setAccessible(true);
            assertTrue((double)speed.invoke(plan) > 0.0D,
                    "A zero clearance measured in another direction disabled the clear climb");
        }
    }

    private void savedDock(String phase) throws Exception{
        set(schedule, "phase", phase(schedule, phase));
        set(schedule, "currentDockId", new UUID(0, 1));
        set(schedule, "attachedDockId", new UUID(0, 1));
        set(schedule, "currentShipConnectorIndex", 3);
        set(schedule, "conditionProgress", new int[]{7, 9});
        set(schedule, "connectorlessHoverTarget", new Vec3(4, 70, 8));
    }

    private void assertSavedDock(String phase) throws Exception{
        assertEquals(phase, get(schedule, "phase").toString());
        assertEquals(new UUID(0, 1), get(schedule, "currentDockId"));
        assertEquals(new UUID(0, 1), get(schedule, "attachedDockId"));
        assertEquals(3, get(schedule, "currentShipConnectorIndex"));
        assertArrayEquals(new int[]{7, 9}, (int[])get(schedule, "conditionProgress"));
        assertEquals(new Vec3(4, 70, 8), get(schedule, "connectorlessHoverTarget"));
    }

    private static Field field(Object obj, String name) throws Exception{
        Field field = obj.getClass().getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static Object get(Object obj, String name) throws Exception{ return field(obj, name).get(obj); }
    private static void set(Object obj, String name, Object value) throws Exception{ field(obj, name).set(obj, value); }
    private static Object phase(Object obj, String name) throws Exception{
        return Enum.valueOf((Class)field(obj, "phase").getType(), name);
    }
    private static Object call(Object obj, String name) throws Exception{
        var method = obj.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(obj);
    }
}
