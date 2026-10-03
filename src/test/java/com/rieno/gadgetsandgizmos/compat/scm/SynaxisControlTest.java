package com.rieno.gadgetsandgizmos.compat.scm;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.lib.scm.ScmRotaryAngles;
import com.verr1.synaxis.content.blocks.motor.AbstractDynamicMotorBlockEntity;
import com.verr1.synaxis.content.blocks.motor.DynamicJointMotorBlockEntity;
import com.verr1.synaxis.content.blocks.motor.DynamicRevoluteMotorBlockEntity;
import com.verr1.synaxis.foundation.physics.PhysicsBodyView;
import com.verr1.synaxis.foundation.physics.control.motor.DynamicMotorConfig;
import com.verr1.synaxis.foundation.physics.control.motor.RevoluteMotorKinematics;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockMakers;
import org.mockito.ArgumentCaptor;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.ArgumentMatchers.any;

class SynaxisControlTest {
    @Test
    void preservesEquivalentTargetsThroughTheScmActuatorWrapper() throws Exception {
        AbstractDynamicMotorBlockEntity motor = mock(DynamicJointMotorBlockEntity.class);
        when(motor.connected()).thenReturn(true);
        when(motor.angleMode()).thenReturn(true);
        when(motor.currentAngle()).thenReturn(Math.toRadians(-179));
        when(motor.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(motor.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        when(motor.renderAxis()).thenReturn(Direction.EAST);
        ScmControlProbe probe = probe(motor);
        Level level = mock(Level.class);
        ScmTarget target = new ScmTarget(UUID.randomUUID(), BlockPos.ZERO, "synaxis:dynamic_joint_motor", "hip");
        Class<?> type = Class.forName("com.rieno.gadgetsandgizmos.content.ShipControlModuleRuntime$ScmProbeActuator");
        Constructor<?> constructor = type.getDeclaredConstructor(Level.class, ScmTarget.class,
                BlockEntity.class, ScmControlProbe.class);
        constructor.setAccessible(true);
        Object actuator = constructor.newInstance(level, target, motor, probe);
        var apply = type.getDeclaredMethod("apply", double.class);
        apply.setAccessible(true);
        try(MockedStatic<SimulatedHelper> helper = mockStatic(SimulatedHelper.class)){
            helper.when(() -> SimulatedHelper.findLoadedBlockEntityExact(level, target.subLevelId(),
                    target.blockPosition())).thenReturn(motor);
            // A target calculated before a wrapper replacement may use the adjacent turn.
            apply.invoke(actuator, Math.toRadians(181));
        }
        ArgumentCaptor<Double> sent = ArgumentCaptor.forClass(Double.class);
        verify(motor).setTarget(sent.capture());
        assertEquals(ScmRotaryAngles.wrap(Math.toRadians(181)), sent.getValue(), 1.0E-8D);
    }

    @Test
    void preservesAnAuthoredLimitEndingAtPositivePi() throws Exception {
        AbstractDynamicMotorBlockEntity motor = mock(DynamicJointMotorBlockEntity.class);
        when(motor.connected()).thenReturn(true);
        when(motor.angleMode()).thenReturn(true);
        when(motor.currentAngle()).thenReturn(Math.PI * 0.5D);
        when(motor.jointLimitEnabled()).thenReturn(true);
        when(motor.jointLimitMin()).thenReturn(0.0D);
        when(motor.jointLimitMax()).thenReturn(Math.PI);
        when(motor.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(motor.renderAxis()).thenReturn(Direction.EAST);
        ScmControlProbe probe = probe(motor);
        assertEquals(0.0D, probe.minControl(), 1.0E-8D);
        assertEquals(Math.PI, probe.maxControl(), 1.0E-8D);
    }

    @Test
    void nativeFeedbackUsesTheIkAxisSignForEveryMounting(){
        RevoluteMotorKinematics kinematics = new RevoluteMotorKinematics();
        for(Direction direction : Direction.values()){
            DynamicMotorConfig config = mock(DynamicMotorConfig.class);
            when(config.motorDirection()).thenReturn(direction);
            when(config.companionDirection()).thenReturn(direction);
            PhysicsBodyView self = mock(PhysicsBodyView.class, withSettings().mockMaker(MockMakers.PROXY));
            PhysicsBodyView child = mock(PhysicsBodyView.class, withSettings().mockMaker(MockMakers.PROXY));
            when(self.bodyToWorldDirection(any(Vector3dc.class), any(Vector3d.class)))
                    .thenAnswer(call -> ((Vector3d) call.getArgument(1)).set((Vector3dc) call.getArgument(0)));
            when(self.omega()).thenReturn(new Vector3d());
            when(child.omega()).thenReturn(new Vector3d());
            for(double angle : new double[]{-3.12D, -0.7D, 0.7D, 3.12D}){
                Quaterniond rotation = new Quaterniond().rotationAxis(angle,
                        direction.getStepX(), direction.getStepY(), direction.getStepZ());
                when(child.bodyToWorldDirection(any(Vector3dc.class), any(Vector3d.class)))
                        .thenAnswer(call -> rotation.transform((Vector3dc) call.getArgument(0),
                                (Vector3d) call.getArgument(1)));
                assertEquals(angle, kinematics.measure(self, child, config).angle(), 1.0E-8D,
                        "Synaxis feedback must agree with positive IK rotation for " + direction);
            }
        }
    }

    @Test
    void preservesTheAuthoredArcAcrossTheNativeAngleSeam() throws Exception {
        for(AbstractDynamicMotorBlockEntity motor : motors()){
            when(motor.connected()).thenReturn(true);
            when(motor.angleMode()).thenReturn(true);
            when(motor.currentAngle()).thenReturn(Math.toRadians(179));
            when(motor.jointLimitEnabled()).thenReturn(true);
            when(motor.jointLimitMin()).thenReturn(Math.toRadians(-170));
            when(motor.jointLimitMax()).thenReturn(Math.toRadians(170));
            when(motor.jointLimitAcrossWrap()).thenReturn(true);
            when(motor.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(motor.renderAxis()).thenReturn(Direction.EAST);
            ScmControlProbe probe = probe(motor);
            assertEquals(Math.toRadians(170), probe.minControl(), 1.0E-8D);
            assertEquals(Math.toRadians(190), probe.maxControl(), 1.0E-8D);
            probe.apply(Math.toRadians(181));
            verify(motor).setTarget(ScmRotaryAngles.wrap(Math.toRadians(181)));
        }
    }

    @Test
    void retainsContinuousFeedbackAcrossTheNativeAngleSeam() throws Exception {
        for(AbstractDynamicMotorBlockEntity motor : motors()){
            when(motor.connected()).thenReturn(true);
            when(motor.angleMode()).thenReturn(true);
            when(motor.target()).thenReturn(0.0D);
            when(motor.currentAngle()).thenReturn(Math.PI - 0.02D);
            when(motor.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(motor.renderAxis()).thenReturn(Direction.EAST);
            ScmControlProbe probe = probe(motor);
            assertTrue(probe.maxControl() > Math.PI + 0.5D,
                    "An unlimited motor must not hit an artificial stop at the native angle seam");
            assertEquals(Math.PI - 0.02D, probe.neutralControl() + probe.read().effect(), 1.0E-8D);
            when(motor.currentAngle()).thenReturn(-Math.PI + 0.02D);
            assertEquals(Math.PI + 0.02D, probe.neutralControl() + probe.read().effect(), 1.0E-8D);
        }
    }

    @BeforeAll
    static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(MockedStatic<LoadingModList> loader = mockStatic(LoadingModList.class)){
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test
    void sendsNativeAngleTargetsToBothMotorTypesOnDistinctSublevels() throws Exception {
        for(AbstractDynamicMotorBlockEntity motor : motors()){
            when(motor.connected()).thenReturn(true);
            when(motor.angleMode()).thenReturn(true);
            when(motor.target()).thenReturn(0.2D);
            when(motor.currentAngle()).thenReturn(0.7D);
            when(motor.getBlockPos()).thenReturn(new BlockPos(10, 20, 30));
            when(motor.getDirection()).thenReturn(Direction.DOWN);
            when(motor.renderAxis()).thenReturn(Direction.EAST);
            when(motor.selfOffsetZ()).thenReturn(0.25D);
            when(motor.lockRequested()).thenReturn(true);
            when(motor.autoLockEnabled()).thenReturn(true);
            ScmControlProbe probe = probe(motor);

            probe.apply(1.1D);
            verify(motor).setAngleMode(true);
            verify(motor).setTarget(1.1D);
            verify(motor).setLockRequested(false);
            verify(motor).setAutoLockEnabled(false);
            assertEquals(new Vec3(10.5D, 19.5D, 30.75D), probe.localEffectPosition());
            assertEquals(new Vec3(1, 0, 0), probe.localEffectDirection());
            assertEquals(0.7D, probe.neutralControl() + probe.read().effect(), 1.0E-8D);

            probe.restore();
            verify(motor).setTarget(0.2D);
            verify(motor).setLockRequested(true);
            verify(motor).setAutoLockEnabled(true);
        }
    }

    @Test
    void exposesAUsableGraphTargetAndJointLimitControls(){
        SynaxisDataAdapter adapter = new SynaxisDataAdapter();
        for(AbstractDynamicMotorBlockEntity motor : motors()){
            assertTrue(adapter.supports(motor));
            assertTrue(adapter.ports(motor).stream().anyMatch(port -> "motor_target".equals(port.id())));
            assertFalse(adapter.ports(motor).stream().anyMatch(port -> "target".equals(port.id())));
            assertTrue(adapter.write(motor, "motor_target", GraphValue.number(1.25D)));
            verify(motor).setTarget(1.25D);
            when(motor.target()).thenReturn(1.25D);
            assertEquals(1.25D, adapter.read(motor, "motor_target").asNumber());
            assertTrue(adapter.write(motor, "joint_limit_enabled", GraphValue.bool(true)));
            verify(motor).applyJointLimit();
            assertTrue(adapter.write(motor, "joint_limit_enabled", GraphValue.bool(false)));
            verify(motor).clearJointLimit();
        }
    }

    @Test
    void doesNotTreatAVelocityTargetAsTheNeutralJointAngle() throws Exception {
        AbstractDynamicMotorBlockEntity motor = mock(DynamicJointMotorBlockEntity.class);
        when(motor.connected()).thenReturn(true);
        when(motor.angleMode()).thenReturn(false);
        when(motor.target()).thenReturn(20.0D);
        when(motor.currentAngle()).thenReturn(0.4D);
        when(motor.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(motor.renderAxis()).thenReturn(Direction.EAST);
        ScmControlProbe probe = probe(motor);
        assertEquals(0.4D, probe.neutralControl(), 1.0E-8D);
        probe.apply(1.2D);
        verify(motor).setTarget(1.2D);
        probe.restore();
        verify(motor).setTarget(20.0D);
        verify(motor).setAngleMode(false);
    }

    private static List<AbstractDynamicMotorBlockEntity> motors(){
        return List.of(mock(DynamicRevoluteMotorBlockEntity.class), mock(DynamicJointMotorBlockEntity.class));
    }

    private static ScmControlProbe probe(AbstractDynamicMotorBlockEntity motor) throws Exception {
        Class<?> type = Class.forName(OptionalScmCompatibility.class.getName() + "$SynaxisJointProbe");
        Constructor<?> constructor = type.getDeclaredConstructor(BlockEntity.class, ScmTarget.class,
                Vec3.class, boolean.class);
        constructor.setAccessible(true);
        ScmTarget target = new ScmTarget(UUID.randomUUID(), motor.getBlockPos(), "synaxis:dynamic_joint_motor", "knee");
        return (ScmControlProbe) constructor.newInstance(motor, target, Vec3.ZERO, false);
    }
}
