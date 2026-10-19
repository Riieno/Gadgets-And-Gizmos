package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GraphPortOrderingTest{
    @Test void mathCollectionAndComparisonNodesUseTheirDeclaredOperandOrder(){
        for(var entry : Map.of(
                "math_atan2", List.of("Y", "X"),
                "list_get", List.of("list", "index"),
                "map_get", List.of("map", "key"),
                "arr_filter", List.of("Array", "Key", "Value", "Operation"),
                "compare", List.of("a", "operator", "b")).entrySet()){
            var node = node(entry.getKey(), entry.getKey());
            assertEquals(entry.getValue(), List.copyOf(AdvancedGraphCatalog.inputs(node).keySet()), entry.getKey());
        }
        var atan = node("atan", "math_atan2");
        CompoundTag dynamic = new CompoundTag();dynamic.putString("X", "double");atan.data().put("DynamicInputs", dynamic);
        assertEquals(List.of("Y", "X"), List.copyOf(AdvancedGraphCatalog.inputs(atan).keySet()));
    }

    @Test void jsonAndListBreakoutsKeepSourceAndNumericIndexOrder(){
        assertEquals(List.of("zeta", "alpha", "middle"), List.copyOf(GraphRuntime.splitListOutputTypesFor(
                AdvancedGraphDocument.Value.string("{\"zeta\":1,\"alpha\":2,\"middle\":3}")).keySet()));
        CompoundTag list = new CompoundTag();
        for(int idx = 12; idx >= 0; idx--) list.put(Integer.toString(idx), AdvancedGraphDocument.Value.number(idx).toTag());
        assertEquals(IntStream.rangeClosed(0, 12).mapToObj(Integer::toString).toList(),
                List.copyOf(GraphRuntime.splitListOutputTypesFor(AdvancedGraphDocument.Value.list(list)).keySet()));
    }

    @Test void mapOrderSurvivesLibraryConversionSaveLoadAndLiveValues(){
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("zeta", 1);fields.put("alpha", 2);fields.put("middle", 3);
        var value = GraphRuntime.fromLibraryValue(GraphValue.map(fields));
        var restored = AdvancedGraphDocument.Value.fromTag(value.toTag());
        var expected = List.of("zeta", "alpha", "middle");
        assertEquals(expected, restored.entryOrder());
        assertEquals(expected, List.copyOf(((Map<?, ?>)GraphRuntime.toLibraryValue(restored).value()).keySet()));
        var live = AdvancedGraphLiveValue.from(restored);
        assertEquals(expected, List.copyOf(live.entryTypes().keySet()));
        assertEquals(expected, live.value().entryOrder());
        assertEquals(2, GraphRuntime.structuredValue(restored, "alpha").asNumber());
        var graph = new AdvancedGraphDocument();graph.variables().put("ordered", restored);
        assertEquals(expected, AdvancedGraphDocument.fromTag(graph.toTag()).variables().get("ordered").entryOrder());
    }

    @Test void savedPortOrderAndFunctionCallsKeepUserFieldOrder(){
        var graph = new AdvancedGraphDocument();
        var function = new AdvancedGraphDocument.FunctionGraph("ordered", "Ordered");
        graph.functions().add(function);
        var input = node("input", AdvancedGraphFunctions.INPUT_TYPE);
        var ports = new CompoundTag();ports.putString("zeta", "number");ports.putString("alpha", "string");
        input.data().put("DynamicOutputs", ports);
        GraphPortOrder.write(input.data(), GraphPortOrder.OUTPUT_ORDER, List.of("zeta", "alpha"));
        function.nodes().add(input);
        var call = node("call", AdvancedGraphFunctions.CALL_TYPE);
        AdvancedGraphFunctions.configureCall(call, function);graph.nodes().add(call);
        assertEquals(List.of("zeta", "alpha"), List.copyOf(AdvancedGraphCatalog.inputs(call).keySet()));
        var restored = AdvancedGraphDocument.fromTag(graph.toTag());
        assertEquals(List.of("zeta", "alpha"), List.copyOf(AdvancedGraphCatalog.inputs(restored.nodes().getFirst()).keySet()));
    }

    @Test void editorDefinitionsIncludeDefaultDynamicPortsWithoutChangingConfiguredNodes(){
        var combine = AdvancedGraphCatalog.editorDefinition("exec_combine");
        assertEquals(List.of("exec_1", "exec_2"), List.copyOf(combine.inputs().keySet()));
        assertSame(combine, AdvancedGraphCatalog.editorDefinition("exec_combine"));
        assertThrows(UnsupportedOperationException.class, () -> combine.inputs().clear());
        for(String type : List.of("parallel_execution", "sequenced_execution")){
            assertEquals(List.of("exec_1", "exec_2"), List.copyOf(AdvancedGraphCatalog.editorDefinition(type).outputs().keySet()));
        }
        var configured = node("configured", "exec_combine");
        configured.data().getCompound("DynamicInputs").putString("exec_3", "exec");
        assertEquals(3, AdvancedGraphCatalog.inputs(configured).size());
        assertEquals(2, combine.inputs().size());
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
}
