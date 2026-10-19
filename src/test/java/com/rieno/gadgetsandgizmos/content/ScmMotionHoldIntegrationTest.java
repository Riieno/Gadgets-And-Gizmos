package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ScmMotionHoldIntegrationTest{
    private final ShipControlModuleRuntime runtime = createRuntime();

    private static ShipControlModuleRuntime createRuntime(){
        var controller = org.mockito.Mockito.mock(AdvancedContraptionControllerBlockEntity.class);
        org.mockito.Mockito.when(controller.getScmOrientation()).thenReturn(
                com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation.fromMount(null, null));
        org.mockito.Mockito.when(controller.getBlockPos()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        org.mockito.Mockito.when(controller.getScmConfigurationProfile()).thenReturn(ScmConfigurationProfile.empty());
        org.mockito.Mockito.when(controller.getShipControlMode()).thenReturn(
                com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry.resolve("airship"));
        return new ShipControlModuleRuntime(controller);
    }

    @AfterEach void removeRuntime() throws Exception{
        ((Set<?>)field("LIVE_RUNTIMES", null)).remove(runtime);
    }

    @Test void yawCommandsReleaseTheHoldWithoutCancellingOtherControls() throws Exception{
        issue("forward", "ship_forward", false);
        issue("yaw", "ship_yaw_left", false);
        issue("pitch", "ship_pitch", false);
        hold("hold", 1);
        assertEquals(3, commands().size());
        assertFalse(commands().containsKey("yaw\u0000ship_yaw_left"));
        hold("hold", 0.5);
        Object active = commands().get("hold\u0000ship_hold_yaw");
        var direction = nested("ActiveShipCommand").getDeclaredMethod("targetDirection");
        direction.setAccessible(true);
        assertEquals(Vec3.ZERO, direction.invoke(active), "Yaw damping captured a heading");
        issue("yaw", "ship_yaw_left", false);
        assertFalse(commands().containsKey("hold\u0000ship_hold_yaw"));
        assertTrue(commands().containsKey("yaw\u0000ship_yaw_left"));
        assertTrue(commands().containsKey("forward\u0000ship_forward"));
        assertTrue(commands().containsKey("pitch\u0000ship_pitch"));
        hold("hold", 1);
        assertEquals(Vec3.ZERO, direction.invoke(commands().get("hold\u0000ship_hold_yaw")));
    }

    @Test void zeroStrengthReleasesTheHoldWhenTheModuleIsUnavailable() throws Exception{
        hold("hold", 1);
        assertTrue(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", 0D)));
        assertTrue(commands().isEmpty());
        hold("hold", 1);
        assertTrue(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", -1D)));
        assertTrue(commands().isEmpty());
    }

    @Test void yawDampingCompletesWithoutChangingControlPhaseAndCanBePulsedAgain() throws Exception{
        var phaseField = ShipControlModuleRuntime.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        Object ready = java.util.Arrays.stream(phaseField.getType().getEnumConstants())
                .filter(value -> value.toString().equals("READY")).findFirst().orElseThrow();
        phaseField.set(runtime, ready);
        issue("forward", "ship_forward", false);
        hold("hold", 1);
        telemetry(Vec3.ZERO, new Vec3(1, 2, 3));
        assertFalse(((Set<?>)field("completedCommands", runtime)).contains("hold\u0000ship_hold_yaw"));
        telemetry(Vec3.ZERO, new Vec3(1, 0, 3));
        assertTrue(((Set<?>)field("completedCommands", runtime)).contains("hold\u0000ship_hold_yaw"));
        assertSame(ready, phaseField.get(runtime));
        assertFalse(runtime.isInitializing());
        assertFalse((boolean)field("resumeInitAfterLoad", runtime));
        assertTrue(commands().containsKey("forward\u0000ship_forward"));
        var actions = ShipControlModuleRuntime.class.getDeclaredMethod("activeControlActionTypes",
                Class.forName(ShipControlModuleRuntime.class.getName() + "$ControlDemand"));
        actions.setAccessible(true);
        assertTrue(((Set<?>)actions.invoke(runtime, new Object[]{null})).contains("ship_hold_yaw"));
        hold("hold", 1);
        assertFalse(((Set<?>)field("completedCommands", runtime)).contains("hold\u0000ship_hold_yaw"), "Later pulse did not rearm yaw damping");
        telemetry(Vec3.ZERO, Vec3.ZERO);
        issue("yaw", "ship_yaw", false);
        assertFalse(commands().containsKey("hold\u0000ship_hold_yaw"));
        assertTrue(commands().containsKey("yaw\u0000ship_yaw"));
        assertSame(ready, phaseField.get(runtime));
    }

    @Test void yawDampingCannotStartInitializationWhenTheScmIsUnavailable() throws Exception{
        Object phase = field("phase", runtime);
        assertFalse(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", 1D)));
        assertSame(phase, field("phase", runtime));
        assertFalse(runtime.isInitializing());
        assertFalse((boolean)field("resumeInitAfterLoad", runtime));
    }

    @Test void strengthNeverInvertsControlOrTurnsZeroIntoFullPower() throws Exception{
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("strength", Map.class);
        method.setAccessible(true);
        assertEquals(1D, method.invoke(null, Map.of()));
        for(double val : new double[]{-1, 0, Double.NaN, Double.POSITIVE_INFINITY}){
            assertEquals(0D, method.invoke(null, Map.of("strength", val)));
        }
        assertEquals(0.5D, method.invoke(null, Map.of("strength", 0.5D)));
        assertEquals(1D, method.invoke(null, Map.of("strength", 2D)));
    }

    @Test void schemaMigrationRemovesHoldAndPreservesStrength(){
        var legacy = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Node(
                "hold", "ship_hold_yaw", "", 0, 0, new net.minecraft.nbt.CompoundTag());
        var defaults = legacy.data().getCompound("Defaults");
        assertFalse(defaults.contains("hold"));
        assertEquals(1, com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.fromTag(defaults.getCompound("strength")).asNumber());
        defaults.put("hold", com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(false).toTag());
        defaults.put("strength", com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number(0).toTag());
        legacy.data().putBoolean("Hold", false);
        var dynamic = new net.minecraft.nbt.CompoundTag();
        dynamic.putString("hold", "boolean");
        legacy.data().put("DynamicInputs", dynamic);
        var authored = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Node(
                "hold", "ship_hold_yaw", "", 0, 0, legacy.data());
        assertFalse(authored.data().getCompound("Defaults").contains("hold"));
        assertFalse(authored.data().contains("Hold"));
        assertFalse(AdvancedGraphCatalog.inputs(authored).containsKey("hold"));
        assertEquals(0, com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.fromTag(authored.data().getCompound("Defaults").getCompound("strength")).asNumber());
    }

    @Test void loadedGraphsRemoveOnlyRetiredHoldWiresIncludingNestedFunctions(){
        var graph = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument();
        var node = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Node(
                "hold", "ship_hold_yaw", "", 0, 0, new net.minecraft.nbt.CompoundTag());
        var retired = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Edge("retired", "source", "value", "hold", "hold");
        var strength = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Edge("strength", "source", "value", "hold", "strength");
        graph.nodes().add(node); graph.edges().add(retired); graph.edges().add(strength);
        var function = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.FunctionGraph("test", "Test");
        function.nodes().add(node); function.edges().add(retired); function.edges().add(strength);
        graph.functions().add(function);
        var loaded = com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.fromTag(graph.toTag());
        assertEquals(java.util.List.of(strength), loaded.edges());
        assertEquals(java.util.List.of(strength), loaded.functions().getFirst().edges());
    }

    private void hold(String id, double strength) throws Exception{
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("updateYawHold", String.class, double.class);
        method.setAccessible(true);
        method.invoke(runtime, id, strength);
    }

    @Test void brakingPreservesLiftAndRotationAndCanBePulsedAgainAfterStopping() throws Exception{
        issue("forward", "ship_forward", false);
        issue("yaw", "ship_yaw_left", false);
        issue("pitch", "ship_pitch", false);
        issue("hover", "ship_hover", true);
        issue("stabilize", "ship_stabilize", true);
        issue("brake", "ship_brake", true);
        assertFalse(commands().containsKey("forward\u0000ship_forward"));
        assertEquals(5, commands().size());
        telemetry(new Vec3(2, 0, 0), new Vec3(0, 1, 0));
        assertTrue(commands().containsKey("brake\u0000ship_brake"), "Brake finished while the ship was moving");
        telemetry(Vec3.ZERO, new Vec3(0, 1, 0));
        assertTrue(((Set<?>)field("completedCommands", runtime)).contains("brake\u0000ship_brake"), "Angular movement prevented linear brake completion");
        var brakeStrength = ShipControlModuleRuntime.class.getDeclaredMethod("explicitBrakeStrength");
        brakeStrength.setAccessible(true);
        assertEquals(0D, brakeStrength.invoke(runtime));
        for(String key : new String[]{"yaw\u0000ship_yaw_left", "pitch\u0000ship_pitch", "hover\u0000ship_hover", "stabilize\u0000ship_stabilize"}){
            assertTrue(commands().containsKey(key), "Brake removed " + key);
        }
        issue("brake", "ship_brake", true);
        assertTrue(commands().containsKey("brake\u0000ship_brake"), "A second identical pulse was ignored");
        assertFalse(((Set<?>)field("completedCommands", runtime)).contains("brake\u0000ship_brake"));
        issue("forward", "ship_forward", false);
        assertFalse(commands().containsKey("brake\u0000ship_brake"), "New movement did not release the brake");
    }

    @Test void completedBrakeRoutingDoesNotThrowAndKeepsLiftAvailable() throws Exception{
        issue("brake", "ship_brake", true);
        telemetry(Vec3.ZERO, new Vec3(0, 1, 0));
        var actions = ShipControlModuleRuntime.class.getDeclaredMethod("activeControlActionTypes",
                Class.forName(ShipControlModuleRuntime.class.getName() + "$ControlDemand"));
        actions.setAccessible(true);
        var routed = (Set<?>)actions.invoke(runtime, new Object[]{null});
        assertFalse(routed.contains("ship_brake"));
        assertTrue(routed.contains("ship_ascend"));
    }

    @Test void everyScmNodeCanRouteCompletedCommandsWithoutChangingControlPhase() throws Exception{
        var routing = ShipControlModuleRuntime.class.getDeclaredMethod("activeControlActionTypes",
                Class.forName(ShipControlModuleRuntime.class.getName() + "$ControlDemand"));
        routing.setAccessible(true);
        Class<?> demandType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ControlDemand");
        var components = demandType.getRecordComponents();
        Class<?>[] types = java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[types.length];
        for(int idx = 0; idx < types.length; idx++){
            if(types[idx] == Vec3.class) values[idx] = Vec3.ZERO;
            else if(types[idx] == double.class) values[idx] = 0D;
            else{
                var none = types[idx].getDeclaredField("NONE");
                none.setAccessible(true);
                values[idx] = none.get(null);
            }
        }
        var constructor = demandType.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        Object neutralDemand = constructor.newInstance(values);
        int audited = 0;
        Object phase = field("phase", runtime);
        for(var definition : AdvancedGraphCatalog.registry().definitions()){
            String type = definition.id();
            if(!AdvancedGraphCatalog.isShipControlType(type)) continue;
            commands().clear();
            issue(type, type, definition.inputs().containsKey("strength"));
            assertNotNull(routing.invoke(runtime, new Object[]{null}), type);
            assertNotNull(routing.invoke(runtime, neutralDemand), type);
            ((Set<String>)field("completedCommands", runtime)).add(type + "\u0000" + type);
            assertNotNull(routing.invoke(runtime, new Object[]{null}), type);
            assertNotNull(routing.invoke(runtime, neutralDemand), type);
            assertSame(phase, field("phase", runtime), type);
            audited++;
        }
        assertTrue(audited >= 20, "SCM catalog audit did not exercise the control nodes");
    }

    @Test void allMotionStopsPreserveOtherControlsAndAcceptManualInputsAfterCompletion() throws Exception{
        for(String type : new String[]{"ship_brake", "ship_hold_yaw", "ship_decelerate"}){
            commands().clear();
            ((Map<?, ?>)field("lastIssuedCommands", runtime)).clear();
            ((Set<?>)field("completedCommands", runtime)).clear();
            issue("hover", "ship_hover", true);
            issue("stabilize", "ship_stabilize", true);
            issue("pitch", "ship_pitch", false);
            issue("roll", "ship_roll", false);
            issue("stop_motion", type, true);
            telemetry(Vec3.ZERO, Vec3.ZERO);
            for(String key : new String[]{"hover\u0000ship_hover", "stabilize\u0000ship_stabilize", "pitch\u0000ship_pitch", "roll\u0000ship_roll"}){
                assertTrue(commands().containsKey(key), type + " removed " + key);
            }
            assertFalse(runtime.isInitializing(), type);
            issue("manual_forward", "ship_forward", false);
            issue("manual_yaw", "ship_yaw", false);
            assertTrue(commands().containsKey("manual_forward\u0000ship_forward"), type);
            assertTrue(commands().containsKey("manual_yaw\u0000ship_yaw"), type);
        }
    }

    @Test void aSettledBrakeKeepsShipControlWithoutApplyingBraking() throws Exception{
        issue("brake", "ship_brake", true);
        telemetry(Vec3.ZERO, new Vec3(0, 1, 0));
        var active = ShipControlModuleRuntime.class.getDeclaredMethod("hasActiveControlCommands");
        active.setAccessible(true);
        assertEquals(true, active.invoke(runtime));
        var strength = ShipControlModuleRuntime.class.getDeclaredMethod("explicitBrakeStrength");
        strength.setAccessible(true);
        assertEquals(0D, strength.invoke(runtime));
        var manual = ShipControlModuleRuntime.class.getDeclaredMethod("hasManualControlInputs");
        manual.setAccessible(true);
        assertEquals(false, manual.invoke(runtime));
        issue("yaw", "ship_yaw", false);
        assertTrue(commands().containsKey("brake\u0000ship_brake"), "Yaw removed lift support");
        issue("forward", "ship_forward", false);
        assertFalse(commands().containsKey("brake\u0000ship_brake"));
    }

    @Test void holdYawIsAvailableAsAnExecutableScmNode(){
        var schema = AdvancedGraphCatalog.get("ship_hold_yaw");
        assertEquals("exec", schema.inputs().get("exec"));
        assertEquals("number", schema.inputs().get("strength"));
        assertFalse(schema.inputs().containsKey("hold"));
        assertNotNull(schema.outputs().get("success"));
    }

    @Test void zeroStrengthDisablesEachRecoveryNodeWithoutTouchingOtherControls() throws Exception{
        for(String type : new String[]{"ship_hover", "ship_stabilize", "ship_decelerate", "ship_brake", "ship_hold_yaw"}){
            commands().clear();
            issue("recovery", type, true);
            issue("pitch", "ship_pitch", false);
            assertTrue(runtime.execute("recovery", type, Map.of("strength", 0D)), type);
            assertFalse(commands().containsKey("recovery\u0000" + type), type);
            assertTrue(commands().containsKey("pitch\u0000ship_pitch"), type);
            assertFalse(runtime.isInitializing(), type);
        }
        hold("tiny", 0.00001D);
        assertTrue(commands().containsKey("tiny\u0000ship_hold_yaw"));
    }

    @Test void residualMotionDoesNotFinishYawOrBrakeEarly() throws Exception{
        hold("hold", 1);
        telemetry(Vec3.ZERO, new Vec3(0, 0.02, 0));
        assertFalse(((Set<?>)field("completedCommands", runtime)).contains("hold\u0000ship_hold_yaw"));
        issue("brake", "ship_brake", true);
        telemetry(new Vec3(0.02, 0, 0), Vec3.ZERO);
        assertFalse(((Set<?>)field("completedCommands", runtime)).contains("brake\u0000ship_brake"));
    }

    @Test void hoverUsesRecoverySpeedAndYieldsToManualHeightChanges() throws Exception{
        Class<?> telemetryType = nested("Telemetry");
        var ctor = telemetryType.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        var hover = ShipControlModuleRuntime.class.getDeclaredMethod("hoverVerticalCorrection", Map.Entry.class, telemetryType, boolean.class, ShipControlMap.class);
        hover.setAccessible(true);
        var factory = nested("ActiveShipCommand").getDeclaredMethod("altitude", String.class, String.class, double.class, double.class, double.class);
        factory.setAccessible(true);
        Object measured = ctor.newInstance(true, new Vec3(0, 9.99, 0), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        Map.Entry<String, Object> entry = new java.util.AbstractMap.SimpleEntry<>("hover", factory.invoke(null, "hover", "ship_hover", 10D, 0D, 0.1D));
        double slow = (double)hover.invoke(runtime, entry, measured, false, null);
        entry.setValue(factory.invoke(null, "hover", "ship_hover", 10D, 0D, 1D));
        double fast = (double)hover.invoke(runtime, entry, measured, false, null);
        assertTrue(fast > slow && slow > 0);
        assertEquals(0D, hover.invoke(runtime, entry, measured, true, null));
        assertEquals(0D, hover.invoke(runtime, entry, measured, false, null));
    }

    @Test void stabilizeUsesTheSelectedRecoverySpeed() throws Exception{
        var model = com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.sample(java.util.List.of(
                new com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.Actuator(Vec3.ZERO, new Vec3(1000, 0, 0), 0),
                new com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.Actuator(Vec3.ZERO, new Vec3(-1000, 0, 0), 0)), 0.05, 0.01, 0.001);
        var modelField = ShipControlModuleRuntime.class.getDeclaredField("adaptiveStateModel");
        modelField.setAccessible(true); modelField.set(runtime, model);
        var feedback = ShipControlModuleRuntime.class.getDeclaredMethod("uprightFeedback", Vec3.class, Vec3.class, double.class, boolean.class);
        feedback.setAccessible(true);
        Vec3 error = new Vec3(0.01, 0, 0);
        Vec3 slow = (Vec3)feedback.invoke(runtime, error, Vec3.ZERO, 0.1D, true);
        Vec3 fast = (Vec3)feedback.invoke(runtime, error, Vec3.ZERO, 1D, true);
        assertTrue(fast.x > slow.x && slow.x > 0);
        assertEquals(0, fast.y); assertEquals(0, fast.z);
    }

    @Test void levelingAndPitchRollDoNotReleaseResidualYawDamping() throws Exception{
        var intent = ShipControlModuleRuntime.class.getDeclaredMethod("hasYawSteeringRequest");
        intent.setAccessible(true);
        issue("hover", "ship_hover", true);
        issue("stabilize", "ship_stabilize", true);
        issue("pitch", "ship_pitch", false);
        issue("roll", "ship_roll", false);
        assertEquals(false, intent.invoke(runtime));
        issue("navigate", "ship_navigate", false);
        assertEquals(true, intent.invoke(runtime));
        commands().clear();
        var external = ShipControlModuleRuntime.class.getDeclaredField("additionalRotationActions");
        external.setAccessible(true);
        external.set(runtime, Set.of("ship_pitch", "ship_roll"));
        assertEquals(false, intent.invoke(runtime));
        external.set(runtime, Set.of("ship_yaw"));
        assertEquals(true, intent.invoke(runtime));
    }

    // Linear recovery must leave active hover in charge of height at either activation order
    @Test void brakingPreservesHoverStrengthInEitherActivationOrder() throws Exception{
        for(String type : new String[]{"ship_brake", "ship_decelerate"}){
            for(double strength : new double[]{0.25D, 0.5D, 1.0D}){
                for(boolean brakeFirst : new boolean[]{false, true}){
                    commands().clear();
                    recovery("hover", "ship_hover", 0.5D);
                    Vec3 velocity = new Vec3(0.01D, 0.02D, -0.03D);
                    double hoverY = demandVector(demand(velocity, Vec3.ZERO), "controlForce").y;
                    commands().clear();
                    if(brakeFirst) recovery("stop", type, strength);
                    recovery("hover", "ship_hover", 0.5D);
                    if(!brakeFirst) recovery("stop", type, strength);
                    Vec3 combined = demandVector(demand(velocity, Vec3.ZERO), "controlForce");
                    assertEquals(hoverY, combined.y, 1.0E-9D, type);
                    Vec3 stopping = com.rieno.gadgetsandgizmos.lib.scm.ScmMotionDamping.stopLinear(velocity, strength, 0.05D);
                    assertEquals(stopping.x, combined.x, 1.0E-9D, type);
                    assertEquals(stopping.z, combined.z, 1.0E-9D, type);
                    assertEquals(0.5D, commandStrength("hover", "ship_hover"));
                    assertEquals(strength, commandStrength("stop", type));
                }
            }
        }
    }

    // Disabling height control restores vertical braking without changing the stop node's gain
    @Test void disabledHoverRestoresThreeAxisBraking() throws Exception{
        Vec3 velocity = new Vec3(0.01D, 0.02D, -0.03D);
        for(String type : new String[]{"ship_brake", "ship_decelerate"}){
            commands().clear();
            recovery("stop", type, 0.5D);
            Vec3 expected = demandVector(demand(velocity, Vec3.ZERO), "controlForce");
            assertTrue(expected.y < 0);
            recovery("hover", "ship_hover", 0.25D);
            assertTrue(runtime.execute("hover", "ship_hover", Map.of("strength", 0D)));
            assertEquals(expected, demandVector(demand(velocity, Vec3.ZERO), "controlForce"));
        }
    }

    // Hover's implicit leveling must not increase an explicitly selected stabilization strength
    @Test void hoverPreservesExplicitStabilizationStrength() throws Exception{
        var actuators = new java.util.ArrayList<com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.Actuator>();
        for(Vec3 axis : java.util.List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1))){
            actuators.add(new com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.Actuator(Vec3.ZERO, axis.scale(1000), 0));
            actuators.add(new com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.Actuator(Vec3.ZERO, axis.scale(-1000), 0));
        }
        var model = ShipControlModuleRuntime.class.getDeclaredField("adaptiveStateModel");
        model.setAccessible(true);
        model.set(runtime, com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel.sample(actuators, 0.05, 0.01, 0.001));
        for(boolean hoverFirst : new boolean[]{false, true}){
            commands().clear();
            recovery("stabilize", "ship_stabilize", 0.25D);
            Vec3 rate = new Vec3(0.01D, 0, 0.02D);
            Vec3 expected = demandVector(demand(Vec3.ZERO, rate), "controlTorque");
            assertTrue(expected.lengthSqr() > 0);
            commands().clear();
            if(hoverFirst) recovery("hover", "ship_hover", 1.0D);
            recovery("stabilize", "ship_stabilize", 0.25D);
            if(!hoverFirst) recovery("hover", "ship_hover", 1.0D);
            Vec3 combined = demandVector(demand(Vec3.ZERO, rate), "controlTorque");
            assertEquals(0, expected.distanceTo(combined), 1.0E-9D);
            assertEquals(0.25D, commandStrength("stabilize", "ship_stabilize"));
        }
    }

    // A yaw hold keeps its selected gain while vertical and linear controls are added or updated
    @Test void hoverAndBrakePreserveYawHoldStrength() throws Exception{
        for(double strength : new double[]{0.25D, 0.5D, 1.0D}){
            commands().clear();
            recovery("hover", "ship_hover", 0.5D);
            Vec3 velocity = new Vec3(0.01D, 0.02D, 0.03D);
            double hoverY = demandVector(demand(velocity, Vec3.ZERO), "controlForce").y;
            commands().clear();
            hold("yaw", strength);
            Vec3 rate = new Vec3(0, 0.01D, 0);
            double expected = demandVector(demand(Vec3.ZERO, rate), "controlTorque").y;
            recovery("hover", "ship_hover", 0.5D);
            recovery("brake", "ship_brake", 1.0D);
            Object combined = demand(velocity, rate);
            assertEquals(expected, demandVector(combined, "controlTorque").y, 1.0E-9D);
            assertEquals(hoverY, demandVector(combined, "controlForce").y, 1.0E-9D);
            assertEquals(strength, commandStrength("yaw", "ship_hold_yaw"));
        }
    }

    // Register a recovery request without requiring a native ship fixture
    private void recovery(String id, String type, double strength) throws Exception{
        Class<?> command = nested("ActiveShipCommand");
        var factory = command.getDeclaredMethod("strength", String.class, String.class, double.class);
        factory.setAccessible(true);
        var register = ShipControlModuleRuntime.class.getDeclaredMethod("registerCommand", command);
        register.setAccessible(true);
        register.invoke(runtime, factory.invoke(null, id, type, strength));
    }

    // Read retained command values separately from the combined physical request
    private double commandStrength(String id, String type) throws Exception{
        var strength = nested("ActiveShipCommand").getDeclaredMethod("strength");
        strength.setAccessible(true);
        return (double)strength.invoke(commands().get(id + "\u0000" + type));
    }

    // Compose actual runtime force and torque requests using measured motion
    private Object demand(Vec3 velocity, Vec3 angular) throws Exception{
        var telemetry = nested("Telemetry").getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class);
        telemetry.setAccessible(true);
        var gravity = nested("GravityCompensation").getDeclaredField("NONE");
        gravity.setAccessible(true);
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("demandFor", nested("Telemetry"), ShipControlMap.class,
                com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Snapshot.class,
                com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi.Topology.class, nested("GravityCompensation"));
        method.setAccessible(true);
        return method.invoke(runtime, telemetry.newInstance(true, Vec3.ZERO, velocity, angular, Vec3.ZERO, Vec3.ZERO),
                new ShipControlMap(null, "", null, net.minecraft.core.BlockPos.ZERO, Vec3.ZERO, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), 0L),
                new com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi.Snapshot(null, false, false, 0, Vec3.ZERO, null, null),
                null, gravity.get(null));
    }

    // Read one force or torque component from the private runtime demand record
    private static Vec3 demandVector(Object demand, String name) throws Exception{
        var method = demand.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (Vec3)method.invoke(demand);
    }

    private void issue(String id, String type, boolean strength) throws Exception{
        Class<?> command = nested("ActiveShipCommand");
        var factory = command.getDeclaredMethod(strength ? "strength" : "amount", String.class, String.class, double.class);
        factory.setAccessible(true);
        var register = ShipControlModuleRuntime.class.getDeclaredMethod("registerCommand", command);
        register.setAccessible(true);
        register.invoke(runtime, factory.invoke(null, id, type, 1D));
    }

    private void telemetry(Vec3 velocity, Vec3 angular) throws Exception{
        Class<?> telemetry = nested("Telemetry");
        var ctor = telemetry.getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class);
        ctor.setAccessible(true);
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("updateCommandCompletion", telemetry);
        method.setAccessible(true);
        method.invoke(runtime, ctor.newInstance(true, Vec3.ZERO, velocity, angular, Vec3.ZERO, Vec3.ZERO));
    }

    private Class<?> nested(String name) throws Exception{
        return Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
    }
    private Object field(String name, Object target) throws Exception{
        var field = ShipControlModuleRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    @SuppressWarnings("unchecked") private Map<String, Object> commands() throws Exception{
        return (Map<String, Object>)field("activeCommands", runtime);
    }
}
