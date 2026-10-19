package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.RotationMath;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphValues;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class AngleFromCoordsGameTests{
    private AngleFromCoordsGameTests(){}

    @GameTest(template = "empty_container")
    public static void coordinateWiresDriveEveryRotationOutputAfterSaving(GameTestHelper ctx) throws Exception{
        var graph = graph();
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        GraphRuntime runtime = runtime(ctx);runtime.compile(graph);
        Vector3 origin = new Vector3(10, -5, 30);
        for(Vector3 direction : List.of(new Vector3(1, 0, 0), new Vector3(0, 1, 0),
                new Vector3(0, 0, -1), new Vector3(3, -4, 5))){
            graph.variables().put("origin", coordinate(origin));
            graph.variables().put("target", coordinate(origin.add(direction)));
            runtime.enqueue("coords");runtime.tick(graph, false);
            ctx.assertTrue(graph.variables().get("result_valid").asBoolean(), "Coordinate node became invalid");
            Quaternion rotation = MathGraphValues.quaternion(GraphRuntime.toLibraryValue(graph.variables().get("result_quaternion")));
            Vector3 aimed = rotation.rotate(new Vector3(0, 0, 1));
            ctx.assertTrue(aimed.subtract(direction.normalized()).magnitude() < 1.0E-10, "Quaternion did not aim toward Target - Origin");
            Quaternion euler = RotationMath.eulerZxzToQuaternion(MathGraphValues.euler(GraphRuntime.toLibraryValue(graph.variables().get("result_euler"))));
            Quaternion taitBryan = RotationMath.taitBryanXyzToQuaternion(MathGraphValues.taitBryan(GraphRuntime.toLibraryValue(graph.variables().get("result_tait_bryan"))));
            Vector3 axis = MathGraphValues.vector(GraphRuntime.toLibraryValue(graph.variables().get("result_axis")));
            double angle = graph.variables().get("result_angle").asNumber();
            double degrees = graph.variables().get("result_degrees").asNumber();
            ctx.assertTrue(degrees >= 0 && degrees < 360, "Degrees heading was outside a full turn");
            if(direction.x() == 0 && direction.z() == 0) ctx.assertTrue(degrees == 0, "Vertical target changed horizontal heading");
            else{
                Vector3 aimedHorizontal = Quaternion.fromAxisAngle(new Vector3(0, 1, 0), Math.toRadians(degrees)).rotate(new Vector3(0, 0, 1));
                Vector3 expectedHorizontal = new Vector3(direction.x(), 0, direction.z()).normalized();
                ctx.assertTrue(aimedHorizontal.subtract(expectedHorizontal).magnitude() < 1.0E-10, "Degrees did not aim toward the X/Z target");
            }
            for(Quaternion alternative : List.of(euler, taitBryan, Quaternion.fromAxisAngle(axis, angle))){
                ctx.assertTrue(Math.abs(rotation.dot(alternative)) > 1 - 1.0E-10, "Rotation outputs disagree");
            }
        }
        ctx.assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void coincidentCoordinatesClearPreviousOutputsAndThenRecover(GameTestHelper ctx) throws Exception{
        var graph = graph();GraphRuntime runtime = runtime(ctx);runtime.compile(graph);
        graph.variables().put("origin", coordinate(Vector3.ZERO));
        graph.variables().put("target", coordinate(new Vector3(1, 0, 0)));
        runtime.enqueue("coords");runtime.tick(graph, false);
        ctx.assertTrue(graph.variables().get("result_angle").asNumber() > 0, "Initial coordinate angle was not calculated");
        graph.variables().put("target", coordinate(Vector3.ZERO));
        runtime.enqueue("coords");runtime.tick(graph, false);
        ctx.assertTrue(!graph.variables().get("result_valid").asBoolean(), "Coincident coordinates retained valid=true");
        ctx.assertTrue(graph.variables().get("result_angle").asNumber() == 0, "Invalid coordinate angle retained an old value");
        for(String port : List.of("euler", "quaternion", "tait_bryan", "axis")){
            ctx.assertTrue(graph.variables().get("result_" + port).payload().isEmpty(), "Invalid coordinate map retained old data");
        }
        graph.variables().put("target", coordinate(new Vector3(0, 0, -1)));
        runtime.enqueue("coords");runtime.tick(graph, false);
        ctx.assertTrue(graph.variables().get("result_valid").asBoolean(), "Coordinate node did not recover");
        ctx.assertTrue(Math.abs(graph.variables().get("result_angle").asNumber() - Math.PI) < 1.0E-12, "Reverse target did not produce a half turn");
        ctx.assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());
        ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void coordinateAngleUsesNumericConversionPortsInSavedGraphs(GameTestHelper ctx) throws Exception{
        var graph = graph();
        for(String unit : List.of("degrees", "radians")){
            add(graph, unit, "createthrusters:" + (unit.equals("degrees") ? "radians_to_degrees" : "degrees_to_radians"));
            graph.variables().put("converted_" + unit, AdvancedGraphDocument.Value.number(0));
            var setter = add(graph, "save_converted_" + unit, "variable_set");setter.data().putString("Variable", "converted_" + unit);
            for(String schema : List.of("DynamicInputs", "DynamicOutputs")){
                var ports = setter.data().getCompound(schema);ports.putString("value", "number");setter.data().put(schema, ports);
            }
            edge(graph, "event", "exec", setter.id(), "exec");edge(graph, unit, "angle", setter.id(), "value");
        }
        edge(graph, "angle", "angle", "degrees", "angle");edge(graph, "degrees", "angle", "radians", "angle");
        graph = AdvancedGraphDocument.fromTag(graph.toTag());
        GraphRuntime runtime = runtime(ctx);runtime.compile(graph);
        graph.variables().put("origin", coordinate(Vector3.ZERO));
        for(Vector3 direction : List.of(new Vector3(0, 0, 1), new Vector3(1, 0, 0), new Vector3(0, 0, -1), new Vector3(3, -4, 5))){
            graph.variables().put("target", coordinate(direction));runtime.enqueue("coords");runtime.tick(graph, false);
            double expected = Math.acos(direction.normalized().z());
            ctx.assertTrue(Math.abs(graph.variables().get("converted_degrees").asNumber() - Math.toDegrees(expected)) < 1.0E-10,
                    "Numeric radians-to-degrees conversion did not preserve the coordinate angle");
            ctx.assertTrue(Math.abs(graph.variables().get("converted_radians").asNumber() - expected) < 1.0E-10,
                    "Numeric degrees-to-radians conversion did not round trip");
        }
        ctx.assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());ctx.succeed();
    }

    @GameTest(template = "empty_container")
    public static void coordinateDegreesWireDirectlyToBearingPivot(GameTestHelper ctx) throws Exception{
        var graph = graph();GraphRuntime runtime = runtime(ctx);
        BlockPos pos = new BlockPos(1, 3, 2);
        ctx.setBlock(pos, CTBlocks.THRUSTER_BEARING.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        var bearing = (ThrusterBearingBlockEntity)ctx.getBlockEntity(pos);
        bearing.setAngleMode(ThrusterBearingBlockEntity.AngleMode.SWIVEL);
        var target = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("bearing",
                com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.BEARING, "",
                "createthrusters:thruster_bearing", "Bearing", null, ctx.absolutePos(pos));
        var setter = add(graph, "pivot", "set_block_data");setter.data().put("TargetData", target.toTag());
        var inputs = setter.data().getCompound("DynamicInputs");inputs.putString("pivot_angle", "number");
        setter.data().put("DynamicInputs", inputs);
        edge(graph, "event", "exec", "pivot", "exec");edge(graph, "angle", "degrees", "pivot", "pivot_angle");
        graph = AdvancedGraphDocument.fromTag(graph.toTag());runtime.compile(graph);
        var center = ctx.absolutePos(pos).getCenter();Vector3 origin = new Vector3(center.x, center.y, center.z);
        graph.variables().put("origin", coordinate(origin));
        var shortestDelta = ThrusterBearingBlockEntity.class.getDeclaredMethod("shortestDeltaDeg", double.class, double.class);
        shortestDelta.setAccessible(true);double previous = 0;
        for(int step = 1; step <= 361; step++){
            double expected = step % 360;double radians = Math.toRadians(expected);
            Vector3 targetPosition = origin.add(new Vector3(Math.sin(radians) * 64, step % 2 == 0 ? 50 : -50, Math.cos(radians) * 64));
            graph.variables().put("target", coordinate(targetPosition));runtime.enqueue("coords");runtime.tick(graph, false);
            ctx.assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());
            var saved = bearing.saveWithoutMetadata(ctx.getLevel().registryAccess());
            ctx.assertTrue(saved.getBoolean("ComputerOverrideActive"), "Degrees wire did not activate bearing control");
            double actual = saved.getDouble("ComputerTargetAngleDeg");
            ctx.assertTrue(Math.abs(actual - expected) < 1.0E-7, "Bearing pivot target did not accept the full Degrees heading");
            double change = (double)shortestDelta.invoke(null, previous, actual);
            ctx.assertTrue(Math.abs(change - 1) < 1.0E-7, "Bearing reversed while crossing 180 or wrapping past 360");
            previous = actual;
        }
        ctx.assertTrue(runtime.diagnostics().isEmpty(), runtime.diagnostics().toString());ctx.succeed();
    }

    private static GraphRuntime runtime(GameTestHelper ctx) throws Exception{
        BlockPos pos = new BlockPos(2, 3, 2);
        ctx.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getBlockEntity(pos);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        return (GraphRuntime)field.get(controller);
    }

    private static AdvancedGraphDocument graph(){
        var graph = new AdvancedGraphDocument();
        var event = add(graph, "event", "event_trigger");event.data().putString("Event", "coords");
        add(graph, "angle", "createthrusters:angle_from_coords");
        for(String port : List.of("origin", "target")){
            var variable = add(graph, port, "variable_get");variable.data().putString("Variable", port);
            graph.variables().put(port, coordinate(new Vector3(0, 0, port.equals("target") ? 1 : 0)));
            edge(graph, port, "value", "angle", port);
        }
        for(String port : List.of("euler", "quaternion", "tait_bryan", "axis", "angle", "degrees", "valid")){
            String type = (port.equals("angle") || port.equals("degrees")) ? "number" : port.equals("valid") ? "boolean" : "map";
            graph.variables().put("result_" + port, type.equals("number") ? AdvancedGraphDocument.Value.number(0)
                    : type.equals("boolean") ? AdvancedGraphDocument.Value.bool(false)
                    : AdvancedGraphDocument.Value.map(new net.minecraft.nbt.CompoundTag()));
            var setter = add(graph, "save_" + port, "variable_set");setter.data().putString("Variable", "result_" + port);
            var inputs = setter.data().getCompound("DynamicInputs");inputs.putString("value", type);setter.data().put("DynamicInputs", inputs);
            var outputs = setter.data().getCompound("DynamicOutputs");outputs.putString("value", type);setter.data().put("DynamicOutputs", outputs);
            edge(graph, "event", "exec", setter.id(), "exec");edge(graph, "angle", port, setter.id(), "value");
        }
        return graph;
    }

    private static AdvancedGraphDocument.Value coordinate(Vector3 value){
        return GraphRuntime.fromLibraryValue(MathGraphValues.vector(value));
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
        graph.nodes().add(node);return node;
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }
}
