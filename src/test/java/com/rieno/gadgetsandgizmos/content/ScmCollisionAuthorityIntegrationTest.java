package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Verify the addon skips world probes before manual control can be overridden
class ScmCollisionAuthorityIntegrationTest{
    private final ShipControlModuleRuntime runtime = new ShipControlModuleRuntime(null);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @AfterEach void removeRuntime() throws Exception{
        var field = ShipControlModuleRuntime.class.getDeclaredField("LIVE_RUNTIMES");
        field.setAccessible(true);
        ((Set<?>) field.get(null)).remove(runtime);
    }

    @Test void manualAndIdleCommandsReturnBeforeAccessingWorldState() throws Exception{
        Object guidance = call(nested("NavigationGuidance"), null, "stopped",
                new Class<?>[]{Vec3.class}, Vec3.ZERO);
        for(String type : new String[]{"ship_forward", "ship_accelerate", "ship_ascend", "ship_yaw",
                "ship_hover", "ship_stabilize", "ship_face"}){
            assertSame(guidance, reactive(target(type, true), guidance), type);
        }
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_climb", "ship_dock"}){
            assertSame(guidance, reactive(target(type, false), guidance), type);
        }
    }

    @Test void optedInNavigationAndAllDockingCommandsRetainAuthority() throws Exception{
        Class<?>[] types = {nested("ActiveShipCommand")};
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_climb", "ship_dock"}){
            assertTrue((boolean) call(ShipControlModuleRuntime.class, runtime,
                    "collisionAvoidanceEnabled", types, target(type, true)), type);
        }
        Object dock = call(nested("ActiveShipCommand"), null, "target",
                new Class<?>[]{String.class, String.class, Vec3.class, double.class, double.class, boolean.class},
                "shipping_schedule_dock", "ship_dock", Vec3.ZERO, 0.25D, 0.18D, true);
        assertTrue((boolean) call(ShipControlModuleRuntime.class, runtime,
                "collisionAvoidanceEnabled", types, dock));
        assertTrue((boolean) call(ShipControlModuleRuntime.class, runtime,
                "collisionAvoidanceEnabled", types, target("ship_dock", true)));
    }

    @Test void scheduleResetAndBrakingSupportDoNotDisableAutonomousAvoidance() throws Exception{
        commands().put("stop", call(nested("ActiveShipCommand"), null, "idle",
                new Class<?>[]{String.class, String.class}, "shipping_schedule_undock_reset", "ship_stop"));
        commands().put("brake", call(nested("ActiveShipCommand"), null, "strength",
                new Class<?>[]{String.class, String.class, double.class}, "shipping_schedule_undock_brake", "ship_decelerate", 1.0D));
        for(String type : new String[]{"ship_navigate", "ship_follow", "ship_climb", "ship_dock"}){
            assertTrue((boolean) call(ShipControlModuleRuntime.class, runtime,
                    "collisionAvoidanceEnabled", new Class<?>[]{nested("ActiveShipCommand")}, target(type, true)), type);
        }
    }

    @Test void mixedManualCommandsSuspendProbesAndPreserveManualRouting() throws Exception{
        Object guidance = call(nested("NavigationGuidance"), null, "stopped",
                new Class<?>[]{Vec3.class}, Vec3.ZERO);
        Map<String, Object> commands = commands();
        for(String type : new String[]{"ship_forward", "ship_accelerate", "ship_ascend", "ship_yaw"}){
            commands.clear();
            commands.put("manual", call(nested("ActiveShipCommand"), null, "amount",
                    new Class<?>[]{String.class, String.class, double.class}, "manual", type, 1.0D));
            assertSame(guidance, reactive(target("ship_navigate", true), guidance), type);
            Set<?> actions = (Set<?>) call(ShipControlModuleRuntime.class, runtime,
                    "activeControlActionTypes", new Class<?>[0]);
            assertTrue(actions.contains(type), type);
            assertFalse(actions.contains("ship_navigate"), type);
        }
    }

    @Test void optionalAxisControllersKeepAutonomousCollisionChecksEnabled() throws Exception{
        for(String fieldName : new String[]{"additionalTranslationActions", "additionalRotationActions"}){
            var field = ShipControlModuleRuntime.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(runtime, Set.of("ship_forward"));
            assertTrue((boolean) call(ShipControlModuleRuntime.class, runtime, "collisionAvoidanceEnabled",
                    new Class<?>[]{nested("ActiveShipCommand")}, target("ship_follow", true)));
            field.set(runtime, Set.of());
        }
    }

    @Test void optedInClimbsKeepTheirColumnAndExposeLateralPropulsion() throws Exception{
        Class<?>[] types = {String.class, String.class, double.class, double.class,
                double.class, boolean.class, Vec3.class};
        Object climb = call(nested("ActiveShipCommand"), null, "altitude", types,
                "climb", "ship_climb", 80.0D, 0.6D, 1.0D, true, new Vec3(12, 40, 24));
        assertEquals(new Vec3(12, 80, 24), call(nested("ActiveShipCommand"), climb,
                "targetPosition", new Class<?>[0]));
        commands().put("climb", climb);
        Set<?> actions = (Set<?>) call(ShipControlModuleRuntime.class, runtime,
                "activeControlActionTypes", new Class<?>[0]);
        assertTrue(actions.containsAll(Set.of("ship_ascend", "ship_descend", "ship_forward",
                "ship_backward", "ship_strafe_left", "ship_strafe_right")));
        Object direct = call(nested("ActiveShipCommand"), null, "altitude", types,
                "climb", "ship_climb", 80.0D, 0.6D, 1.0D, false, Vec3.ZERO);
        assertFalse((boolean) call(nested("ActiveShipCommand"), climb, "sameRequestAs",
                new Class<?>[]{nested("ActiveShipCommand")}, direct));
        commands().put("climb", direct);
        actions = (Set<?>) call(ShipControlModuleRuntime.class, runtime,
                "activeControlActionTypes", new Class<?>[0]);
        assertFalse(actions.contains("ship_strafe_left"));
    }

    @Test void climbExposesAnAvoidanceOptionWhichDefaultsOff(){
        assertEquals("boolean", AdvancedGraphCatalog.get("ship_climb").inputs().get("avoid_collisions"));
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData("ship_climb", null);
        assertFalse(AdvancedGraphDocument.Value.fromTag(
                data.getCompound("Defaults").getCompound("avoid_collisions")).asBoolean());
        assertFalse(ShipControlModuleRuntime.collisionAvoidanceRequested(Map.of()));
        assertTrue(ShipControlModuleRuntime.collisionAvoidanceRequested(Map.of("avoid_collisions", 1.0D)));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create a command without consulting game state
    private Object target(String type, boolean avoid) throws Exception{
        return call(nested("ActiveShipCommand"), null, "target",
                new Class<?>[]{String.class, String.class, Vec3.class, double.class, double.class, boolean.class},
                "test", type, new Vec3(0, 20, 0), 0.6D, 0.75D, avoid);
    }

    // Invoke the safety entry point with absent world state to detect unwanted probes
    private Object reactive(Object command, Object guidance) throws Exception{
        return call(ShipControlModuleRuntime.class, runtime, "applyReactiveCollisionAvoidance",
                new Class<?>[]{String.class, nested("Telemetry"), nested("ActiveShipCommand"),
                        nested("NavigationGuidance")}, "test", null, command, guidance);
    }

    @SuppressWarnings("unchecked")
    // Access the current command set for composition checks
    private Map<String, Object> commands() throws Exception{
        var field = ShipControlModuleRuntime.class.getDeclaredField("activeCommands");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(runtime);
    }

    // Resolve a private runtime value type
    private static Class<?> nested(String name) throws Exception{
        return Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
    }

    // Invoke the production entry point without widening its visibility
    private static Object call(Class<?> type, Object receiver, String name,
            Class<?>[] argTypes, Object... args) throws Exception{
        Method method = type.getDeclaredMethod(name, argTypes);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }
}
