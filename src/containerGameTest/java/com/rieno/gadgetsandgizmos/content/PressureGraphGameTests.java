package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ContraptionDiagramControllerCompat;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTargets;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.DimensionPressureGraphNode;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsTelemetry;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

// Compare graph pressure readings with Sable's dimension and world-position APIs
@GameTestHolder("createthrusters_targets")
@PrefixGameTestTemplate(false)
public final class PressureGraphGameTests{
    // Preserve a selected dimension through save/load and read every loaded dimension on the server
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dimensionPressureUsesSelectedDimensionAndPercentages(GameTestHelper helper){
        var ctrl = controller(helper);
        var runtime = new GraphRuntime(ctrl, true);
        var opts = DimensionPressureGraphNode.dimensionOptions(helper.getLevel().getServer().levelKeys());
        helper.assertTrue(opts.contains(DimensionPressureGraphNode.CURRENT_DIMENSION), "Missing current-dimension option");
        for(var level : helper.getLevel().getServer().getAllLevels()){
            String id = level.dimension().location().toString();
            helper.assertTrue(opts.contains(id), "Missing dimension: " + id);
            var graph = new AdvancedGraphDocument();
            var pressure = node("pressure", "createthrusters:dimension_pressure");
            pressure.data().getCompound("Defaults").put("dimension", AdvancedGraphDocument.Value.string(id).toTag());
            graph.nodes().add(pressure);
            graph = AdvancedGraphDocument.fromTag(graph.toTag());
            pressure = graph.nodes().getFirst();
            double expected = DimensionPhysicsData.of(level).basePressure()
                    .orElseGet(() -> DimensionPhysicsData.getDefault(level).basePressure().orElseThrow()) * 100.0D;
            assertPressure(helper, runtime.previewOutput(graph, pressure, "air_pressure"), expected);
        }
        var graph = new AdvancedGraphDocument();
        var pressure = node("pressure", "createthrusters:dimension_pressure"); graph.nodes().add(pressure);
        assertPressure(helper, runtime.previewOutput(graph, pressure, "air_pressure"),
                SablePhysicsTelemetry.dimensionPressure(helper.getLevel()) * 100.0D);
        for(String id : List.of("missing:dimension", "invalid dimension")){
            pressure.data().getCompound("Defaults").put("dimension", AdvancedGraphDocument.Value.string(id).toTag());
            runtime.clear();
            assertPressure(helper, runtime.previewOutput(graph, pressure, "air_pressure"), 0.0D);
        }
        runtime.clear(); helper.succeed();
    }

    // Read the same native ship through a diagram target and Ship Telemetry at its transformed altitude
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void diagramAndShipTelemetryUseWorldAltitude(GameTestHelper helper){
        var body = ShipDoorGameTests.newBody(helper, helper.absolutePos(BlockPos.ZERO).getCenter().add(0, 120, 0));
        body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
        body.updateBoundingBox();
        helper.runAtTickTime(10, () -> {
            try{
                var snapshot = SablePhysicsTelemetry.sample(helper.getLevel(), body.getUniqueId());
                helper.assertTrue(snapshot.available() && snapshot.mass() > 0, "Native ship telemetry is unavailable");
                double expected = DimensionPhysicsData.getAirPressure(helper.getLevel(), snapshot.worldCenterOfMass()) * 100.0D;
                double local = DimensionPhysicsData.getAirPressure(helper.getLevel(), snapshot.centerOfMass()) * 100.0D;
                helper.assertTrue(Math.abs(expected - local) > 1, "Fixture does not distinguish world altitude from plot altitude");
                var target = new ControllerDiscoveryNode("pressure-ship", ControllerDiscoveryKind.UNKNOWN, "fixture",
                        ContraptionNetworkLinkerData.CONTRAPTION_DIAGRAM_TARGET_ID, "Ship", body.getUniqueId(), body.getPlot().getCenterBlock());
                assertPressure(helper, ContraptionDiagramControllerCompat.readTargetData(helper.getLevel(), target, "air_pressure"), expected);
                var data = ContraptionDiagramControllerCompat.readTargetData(helper.getLevel(), target, "data");
                assertPressure(helper, AdvancedGraphDocument.Value.fromTag(data.payload().getCompound("air_pressure")), expected);
                var ctrl = controller(helper);
                var read = node("read", "get_block_data");
                AdvancedGraphTargets.apply(helper.getLevel(), read, AdvancedGraphDocument.Value.target(target.toTag()), List.of());
                var collapsed = read.data().getCompound(AdvancedGraphCatalog.COLLAPSED_PORT_SECTIONS_TAG);
                collapsed.getAllKeys().forEach(key -> collapsed.putBoolean(key, false));
                String port = AdvancedGraphCatalog.outputs(read).keySet().stream()
                        .filter(key -> "Air Pressure".equals(read.data().getCompound("OutputLabels").getString(key)))
                        .findFirst().orElseThrow();
                var graph = new AdvancedGraphDocument(); graph.nodes().add(read);
                var runtime = new GraphRuntime(ctrl, true);
                assertPressure(helper, runtime.previewOutput(graph, read, port), expected);
                runtime.clear();
                var ship = new ShipControlModuleRuntime(ctrl);
                set(ship, "rootSubLevel", body);
                set(ship, "map", new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                        body.getUniqueId(), ctrl.getBlockPos(), new Vec3(snapshot.centerOfMass().x, snapshot.centerOfMass().y,
                        snapshot.centerOfMass().z), List.of(), List.of(), List.of(), List.of(), List.of(), 0));
                helper.assertTrue("number".equals(AdvancedGraphCatalog.get("ship_telemetry").outputs().get("air_pressure")),
                        "Ship Telemetry did not expose Air Pressure");
                assertPressure(helper, ship.graphValue("air_pressure"), expected);
                helper.succeed();
            }catch(ReflectiveOperationException err){ throw new AssertionError(err); }
            finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
        });
    }

    // Keep unavailable pressure readings finite without inventing an atmosphere
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unavailablePressureIsZero(GameTestHelper helper){
        helper.assertTrue(SablePhysicsTelemetry.airPressure(null, new Vector3d()) == 0
                && SablePhysicsTelemetry.airPressure(helper.getLevel(), new Vector3d(Double.NaN)) == 0,
                "Unavailable pressure did not return zero");
        assertPressure(helper, new ShipControlModuleRuntime(controller(helper)).graphValue("air_pressure"), 0);
        var target = new ControllerDiscoveryNode("missing", ControllerDiscoveryKind.UNKNOWN, "fixture",
                ContraptionNetworkLinkerData.CONTRAPTION_DIAGRAM_TARGET_ID, "Missing", null, BlockPos.ZERO);
        assertPressure(helper, ContraptionDiagramControllerCompat.readTargetData(helper.getLevel(), target, "air_pressure"), 0);
        helper.succeed();
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, AdvancedGraphCatalog.displayName(type), 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        var ctrl = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        ctrl.setLevel(helper.getLevel()); return ctrl;
    }

    private static void assertPressure(GameTestHelper helper, AdvancedGraphDocument.Value val, double expected){
        helper.assertTrue(val != null && "number".equals(val.type()) && Math.abs(val.asNumber() - expected) < 1.0E-6D,
                "Expected pressure " + expected + ", got " + val);
    }

    private static void set(Object owner, String name, Object val) throws ReflectiveOperationException{
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, val);
    }
}
