package com.rieno.gadgetsandgizmos.content.advanced;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NamedDockNodeTest{
    @Test void dockingOffersADockNameAndNoCoordinateOrOrientationInputs(){
        assertEquals("Dock at", AdvancedGraphCatalog.displayName("ship_dock"));
        assertEquals(Set.of("exec", "dock", "speed"), AdvancedGraphCatalog.get("ship_dock").inputs().keySet());
        assertEquals("string", AdvancedGraphCatalog.get("ship_dock").inputs().get("dock"));
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData("ship_dock", AdvancedGraphNodeFactory.Context.EMPTY);
        assertEquals("string", data.getCompound("Defaults").getCompound("dock").getString("Type"));
        assertFalse(data.getCompound("Defaults").contains("target_point"));
        assertEquals("Ship Dock", data.getCompound("InputLabels").getString("dock"));
    }

    @Test void savedCoordinateNodesAndNestedFunctionsKeepIdsButRequireAnExplicitDockName(){
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        var dock = legacy("main");
        graph.nodes().add(dock);
        graph.nodes().add(new AdvancedGraphDocument.Node("number", "constant_number", "", 0, 0, new CompoundTag()));
        graph.edges().add(new AdvancedGraphDocument.Edge("old", "number", "value", "main", "x"));
        graph.edges().add(new AdvancedGraphDocument.Edge("speed", "number", "value", "main", "speed"));
        var function = new AdvancedGraphDocument.FunctionGraph("f", "Dock");
        function.nodes().add(legacy("nested"));
        graph.functions().add(function);
        var restored = AdvancedGraphDocument.fromTag(graph.toTag());
        assertEquals("main", restored.nodes().getFirst().id());
        assertEquals(Set.of("exec", "dock", "speed"), AdvancedGraphCatalog.inputs(restored.nodes().getFirst()).keySet());
        assertEquals("", restored.nodes().getFirst().data().getCompound("Defaults").getCompound("dock").getCompound("Payload").getString("Value"));
        assertEquals(1, restored.edges().size());
        assertEquals("speed", restored.edges().getFirst().id());
        assertFalse(restored.function("f").nodes().getFirst().data().getCompound("Defaults").contains("x"));
        restored.nodes().getFirst().data().getCompound("Defaults").put("dock", AdvancedGraphDocument.Value.string("Cargo Dock").toTag());
        assertEquals("Cargo Dock", AdvancedGraphDocument.fromTag(restored.toTag()).nodes().getFirst()
                .data().getCompound("Defaults").getCompound("dock").getCompound("Payload").getString("Value"));
    }

    private static AdvancedGraphDocument.Node legacy(String id){
        CompoundTag data = new CompoundTag();
        CompoundTag defaults = new CompoundTag();
        defaults.put("x", AdvancedGraphDocument.Value.number(50).toTag());
        defaults.put("target_point", AdvancedGraphDocument.Value.string("center_of_mass").toTag());
        data.put("Defaults", defaults);
        CompoundTag dynamic = new CompoundTag();
        dynamic.putString("x", "number");
        dynamic.putString("target_direction_x", "number");
        data.put("DynamicInputs", dynamic);
        return new AdvancedGraphDocument.Node(id, "ship_dock", "", 0, 0, data);
    }
}
