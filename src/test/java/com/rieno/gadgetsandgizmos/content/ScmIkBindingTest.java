package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmLeggedLocomotion;
import com.rieno.gadgetsandgizmos.lib.scm.ScmLocomotionFrame;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSubLevelRelationRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ScmIkBindingTest {
    @Test
    void manualIkBindingsNeverFallBackToAnExistingAutoGroup(){
        var reference = new ScmConfigurationProfile.UnitReference(UUID.randomUUID(), BlockPos.ZERO, "");
        var group = new ScmConfigurationProfile.Group("motors", "Motors", Set.of(reference));
        var profile = ScmConfigurationProfile.empty();
        UUID mapId = UUID.randomUUID();
        profile.replace(mapId, List.of(group), Map.of(ScmConfigurationProfile.AUTO_ACTION, "motors"), Set.of());
        assertTrue(profile.usesAutomaticIkBindings());
        for(String action : List.of("ik_leg_1_hip", "ik_leg_2_knee", "ik_arm_1_hip", "ik_arm_2_knee")){
            profile.replace(mapId, List.of(group), Map.of(ScmConfigurationProfile.AUTO_ACTION, "motors",
                    action, "motors"), Set.of());
            assertFalse(profile.usesAutomaticIkBindings());
            assertFalse(ScmConfigurationProfile.fromTag(profile.toTag()).usesAutomaticIkBindings());
        }
        assertFalse(ScmConfigurationProfile.empty().usesAutomaticIkBindings());
    }

    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(MockedStatic<net.neoforged.fml.loading.LoadingModList> loader =
                    mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var modList = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(modList.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(modList);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test
    void runtimeTopologyIncludesRegisteredOptionalJointLinks() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel root = body(level);
        ServerSubLevel thigh = body(level);
        ServerSubLevel shin = body(level);
        var container = mock(ServerSubLevelContainer.class);
        when(container.getAllSubLevels()).thenReturn(List.of(root, thigh, shin));
        var links = List.of(new ScmSubLevelRelationRegistry.Relation(root.getUniqueId(), thigh.getUniqueId(), "test:hip"),
                new ScmSubLevelRelationRegistry.Relation(thigh.getUniqueId(), shin.getUniqueId(), "test:knee"));
        try(MockedStatic<SubLevelContainer> containers = mockStatic(SubLevelContainer.class);
            MockedStatic<SubLevelBlockEntityCollector> collector = mockStatic(SubLevelBlockEntityCollector.class);
            MockedStatic<ScmSubLevelRelationRegistry> relations = mockStatic(ScmSubLevelRelationRegistry.class)){
            containers.when(() -> SubLevelContainer.getContainer(level)).thenReturn(container);
            relations.when(() -> ScmSubLevelRelationRegistry.relations(any(), any())).thenReturn(links);
            var discover = ShipControlModuleRuntime.class.getDeclaredMethod("discoverAssemblyTopology", ServerSubLevel.class);
            discover.setAccessible(true);
            var topology = (SableAssemblyTopologyApi.Topology) discover.invoke(null, root);
            assertEquals(Set.of(root.getUniqueId(), thigh.getUniqueId(), shin.getUniqueId()), topology.loadedBodyIds());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void incompleteLegAndArmChainsHoldTheirMeasuredAngleInsteadOfFallingBack() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ServerSubLevel root = body(level);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(level);
        ShipControlModuleRuntime runtime = new ShipControlModuleRuntime(controller);
        var unit = new ShipControlMap.PropulsionUnit(0, root.getUniqueId(), BlockPos.ZERO,
                "synaxis:dynamic_revolute_motor", "scm:synaxis_revolute_joint", true,
                Vec3.ZERO, new Vec3(1, 0, 0), -Math.PI, Math.PI, 0, 1, 1, List.of());
        AtomicReference<Double> sent = new AtomicReference<>();
        Object actuator = actuator(unit, sent);
        var controls = ShipControlModuleRuntime.class.getDeclaredField("controlActuators");
        controls.setAccessible(true);
        ((Map<Integer, Object>) controls.get(runtime)).put(0, actuator);
        Object hip = construct("IkRoleBinding", List.of(unit), List.of());
        Object empty = construct("IkRoleBinding", List.of(), List.of());
        var link = new ScmSubLevelRelationRegistry.Relation(root.getUniqueId(), UUID.randomUUID(),
                "test:hip", root.getUniqueId(), BlockPos.ZERO);
        var solve = ShipControlModuleRuntime.class.getDeclaredMethod("ikApplyRedundantLimb", ServerSubLevel.class,
                ShipControlMap.class, nested("IkLimbBinding"), ScmLeggedLocomotion.LimbTarget.class,
                List.class, ScmLocomotionFrame.class);
        solve.setAccessible(true);
        try(MockedStatic<SableLevelApi> levels = mockStatic(SableLevelApi.class)){
            levels.when(() -> SableLevelApi.subLevel(level, root.getUniqueId())).thenReturn(root);
            for(String id : List.of("leg_1", "arm_1")){
                Object binding = construct("IkLimbBinding", id, null, empty, hip, empty, empty, empty,
                        List.of(hip), empty, Vec3.ZERO, new Vec3(0, -2, 0), null);
                var target = new ScmLeggedLocomotion.LimbTarget(id, new Vec3(0, -1, 0.4), null, 0.25, false);
                Object res = solve.invoke(runtime, root, null, binding, target, List.of(link),
                        ScmLocomotionFrame.fromUpAndForward(new Vec3(0, 1, 0), new Vec3(0, 0, 1)));
                assertEquals("HOLD", res.toString());
                assertEquals(0.7D, sent.get(), 1.0E-8D);
            }

            var kneeUnit = new ShipControlMap.PropulsionUnit(1, root.getUniqueId(), new BlockPos(0, -1, 0),
                    "synaxis:dynamic_joint_motor", "scm:synaxis_revolute_joint", true,
                    new Vec3(0, -1, 0), new Vec3(1, 0, 0), -Math.PI, Math.PI, 0, 1, 1, List.of());
            AtomicReference<Double> kneeSent = new AtomicReference<>();
            ((Map<Integer, Object>) controls.get(runtime)).put(1, actuator(kneeUnit, kneeSent));
            Object knee = construct("IkRoleBinding", List.of(kneeUnit), List.of());
            var kneeLink = new ScmSubLevelRelationRegistry.Relation(link.childSubLevelId(), UUID.randomUUID(),
                    "test:knee", root.getUniqueId(), kneeUnit.blockPosition());
            for(String id : List.of("leg_1", "arm_1")){
                Object binding = construct("IkLimbBinding", id, null, empty, hip, knee, empty, empty,
                        List.of(hip, knee), empty, Vec3.ZERO, new Vec3(0, -2, 0), null);
                var target = new ScmLeggedLocomotion.LimbTarget(id, new Vec3(0, -1.4, 0.4),
                        new ScmLeggedLocomotion.JointAngles(0, 0.3, 1.0, 1.5), 0.25, false);
                Object res = solve.invoke(runtime, root, null, binding, target, List.of(link, kneeLink),
                        ScmLocomotionFrame.fromUpAndForward(new Vec3(0, 1, 0), new Vec3(0, 0, 1)));
                assertEquals("MEASURED", res.toString());
                assertTrue(Math.abs(sent.get() - 0.7D) > 1.0E-4D);
                assertTrue(Math.abs(kneeSent.get() - 0.7D) > 1.0E-4D);
                assertTrue(Math.abs(sent.get() - 0.7D) <= 0.35000001D);
                assertTrue(Math.abs(kneeSent.get() - 0.7D) <= 0.35000001D);
            }
        }
    }

    private static Object actuator(ShipControlMap.PropulsionUnit unit,
            AtomicReference<Double> sent) throws Exception {
        Object reading = construct("Reading", 0.0D, 0.7D, true);
        return mock(nested("Actuator"), call -> switch(call.getMethod().getName()){
            case "isAvailable", "controllable", "requiresContinuousControl" -> true;
            case "kind" -> unit.adapter();
            case "minControl" -> -Math.PI;
            case "maxControl" -> Math.PI;
            case "neutralControl" -> 0.0D;
            case "read" -> reading;
            case "localForcePosition" -> unit.rootPosition();
            case "localForceDirection" -> unit.forceDirection();
            case "apply" -> { sent.set(call.getArgument(0)); yield null; }
            default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
    }

    private static Class<?> nested(String name) throws ClassNotFoundException {
        return Class.forName(ShipControlModuleRuntime.class.getName() + "$" + name);
    }

    private static Object construct(String name, Object... args) throws Exception {
        for(var constructor : nested(name).getDeclaredConstructors()){
            if(constructor.getParameterCount() != args.length) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new IllegalArgumentException(name);
    }

    private static ServerSubLevel body(ServerLevel level){
        ServerSubLevel body = mock(ServerSubLevel.class);
        ServerLevelPlot plot = mock(ServerLevelPlot.class);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getLevel()).thenReturn(level);
        when(body.getPlot()).thenReturn(plot);
        when(plot.getBlockEntityActors()).thenReturn(List.of());
        return body;
    }
}
