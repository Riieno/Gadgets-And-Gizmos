package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.scm.ScmBuoyancyControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmBuoyancyIntegrationTest{
    @Test void hoverUsesOnlyActiveVerticalProvidersForWarmupFeedback() throws Exception{
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(controller.getScmConfigurationProfile()).thenReturn(ScmConfigurationProfile.empty());
        var runtime = new ShipControlModuleRuntime(controller);
        try{
            var source = mock(ScmBuoyancyControl.Source.class);
            when(source.responseTimeSeconds()).thenReturn(9D);
            var probe = new ScmBuoyancyControl("redstone_block_control", source);
            Class<?> actuatorType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ScmProbeActuator");
            var constructor = actuatorType.getDeclaredConstructor(Level.class, ScmTarget.class, BlockEntity.class, ScmControlProbe.class);
            constructor.setAccessible(true);
            Object actuator = constructor.newInstance(mock(Level.class), new ScmTarget(null, BlockPos.ZERO, "aeronautics:adjustable_burner", ""),
                    mock(BlockEntity.class), probe);
            var controls = ShipControlModuleRuntime.class.getDeclaredField("controlActuators");
            controls.setAccessible(true);
            ((java.util.Map<Integer, Object>)controls.get(runtime)).put(0, actuator);
            var unit = mock(ShipControlMap.PropulsionUnit.class);
            when(unit.index()).thenReturn(0);
            when(unit.maxThrust()).thenReturn(1962D);
            when(unit.forceDirection()).thenReturn(new Vec3(0, 1, 0));
            var map = mock(ShipControlMap.class);
            when(map.units()).thenReturn(List.of(unit));
            Class<?> telemetryType = Class.forName(ShipControlModuleRuntime.class.getName() + "$Telemetry");
            var telemetryConstructor = telemetryType.getDeclaredConstructors()[0];
            telemetryConstructor.setAccessible(true);
            Object telemetry = telemetryConstructor.newInstance(true, new Vec3(0, 9, 0), new Vec3(0, -0.1, 0),
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
            Class<?> commandType = Class.forName(ShipControlModuleRuntime.class.getName() + "$ActiveShipCommand");
            var altitude = commandType.getDeclaredMethod("altitude", String.class, String.class, double.class, double.class, double.class);
            altitude.setAccessible(true);
            var entry = new java.util.AbstractMap.SimpleEntry<>("hover", altitude.invoke(null, "hover", "ship_hover", 10D, 0D, 1D));
            var hover = ShipControlModuleRuntime.class.getDeclaredMethod("hoverVerticalCorrection", java.util.Map.Entry.class,
                    telemetryType, boolean.class, ShipControlMap.class);
            hover.setAccessible(true);
            assertEquals(com.rieno.gadgetsandgizmos.lib.scm.ScmStateFeedback.delayedRecoveryAcceleration(1, -0.1, 1, 0.05, 9),
                    (double)hover.invoke(runtime, entry, telemetry, false, map), 1e-9);
            for(Vec3 direction : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, -1, 0)}){
                when(unit.forceDirection()).thenReturn(direction);
                assertEquals(com.rieno.gadgetsandgizmos.lib.scm.ScmStateFeedback.recoveryAcceleration(1, -0.1, 1, 0.05),
                        (double)hover.invoke(runtime, entry, telemetry, false, map), 1e-9);
            }
            when(unit.forceDirection()).thenReturn(new Vec3(0, 1, 0));
            when(unit.maxThrust()).thenReturn(0D);
            assertEquals(com.rieno.gadgetsandgizmos.lib.scm.ScmStateFeedback.recoveryAcceleration(1, -0.1, 1, 0.05),
                    (double)hover.invoke(runtime, entry, telemetry, false, map), 1e-9);
        }finally{
            var live = ShipControlModuleRuntime.class.getDeclaredField("LIVE_RUNTIMES");
            live.setAccessible(true);
            ((java.util.Set<?>)live.get(null)).remove(runtime);
        }
    }

    @Test void savedRedstoneFaceBindingsUseNativeLiftAndIgnoreOldSignalCalibration() throws Exception{
        var source = mock(ScmBuoyancyControl.Source.class);
        when(source.sample()).thenReturn(new ScmBuoyancyControl.Sample(200, 100, 9.81,
                new Vec3(0, 1, 0), new Vec3(0, 6, 0)));
        var probe = new ScmBuoyancyControl("redstone_block_control", source);
        var target = new ScmTarget(UUID.randomUUID(), BlockPos.ZERO, "aeronautics:adjustable_burner", "",
                BlockPos.ZERO.north(), Direction.NORTH);
        Class<?> type = Class.forName(ShipControlModuleRuntime.class.getName() + "$ScmProbeActuator");
        var constructor = type.getDeclaredConstructor(Level.class, ScmTarget.class, BlockEntity.class, ScmControlProbe.class);
        constructor.setAccessible(true);
        Object actuator = constructor.newInstance(mock(Level.class), target, mock(BlockEntity.class), probe);
        var old = new ShipControlMap.PropulsionUnit(0, target.subLevelId(), BlockPos.ZERO,
                target.blockId(), "scm:redstone_block_control:face_north", true, Vec3.ZERO,
                new Vec3(0, 0, 1), 0, 1, 0, 1, 0,
                List.of(new ShipControlMap.CalibrationSample(0.5, 0, 0.1, true),
                        new ShipControlMap.CalibrationSample(1, 0, 1, true)));
        assertEquals(old.adapter(), call(actuator, "kind"));
        assertEquals(false, call(actuator, "isFaceActionControl"));
        assertEquals(new Vec3(0, 1, 0), call(actuator, "localForceDirection"));
        assertEquals(new Vec3(0, 6, 0), call(actuator, "localForcePosition"));
        assertEquals(1962D, call(actuator, "liveMaximumThrust", old));
        assertEquals(0.5D, call(actuator, "controlForDemand", old, 0.5D));
        when(source.sample()).thenReturn(new ScmBuoyancyControl.Sample(100, 50, 9.81,
                new Vec3(1, 0, 0), Vec3.ZERO));
        assertEquals(981D, call(actuator, "liveMaximumThrust", old));
        assertEquals(new Vec3(1, 0, 0), call(actuator, "localForceDirection"));
    }

    private static Object call(Object target, String name, Object... args) throws Exception{
        Class<?>[] types = java.util.Arrays.stream(args).map(val -> val instanceof Double ? double.class : val.getClass())
                .toArray(Class<?>[]::new);
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
