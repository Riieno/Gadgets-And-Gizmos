package com.rieno.gadgetsandgizmos.content.advanced;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedDisplayWidgetTest{
    @Test void everyInteractiveElementHasItsOwnStableExecValueSection(){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var data = AdvancedGraphNodeFactory.createDefaultData(type, null);
            ListTag elements = new ListTag();
            for(String control : List.of("button", "toggle", "slider", "text_input")){
                CompoundTag elm = new CompoundTag(); elm.putString("Type", control); elm.putString("Text", "My " + control);
                elements.add(elm);
            }
            data.put("WidgetElements", elements);
            var node = new AdvancedGraphDocument.Node("widget", type, "", 0, 0, data);
            data = node.data(); elements = data.getList("WidgetElements", 10);
            var edges = new ArrayList<AdvancedGraphDocument.Edge>();
            AdvancedHudInteractions.synchronize(node, edges);
            var sections = data.getCompound("PortSections").getCompound("Outputs");
            assertEquals(4, sections.size());
            for(Tag raw : elements){
                CompoundTag elm = (CompoundTag)raw;
                var ports = sections.getCompound("hud:" + elm.getString("InteractionId")).getList("Ports", 8);
                assertEquals(List.of(StringTag.valueOf(elm.getString("ExecPort")), StringTag.valueOf(elm.getString("ValuePort"))), ports);
                assertEquals("Exec", data.getCompound("OutputLabels").getString(elm.getString("ExecPort")));
                edges.add(new AdvancedGraphDocument.Edge(UUID.randomUUID().toString(), "widget", elm.getString("ExecPort"), "sink", "exec"));
            }
            var before = data.getCompound("DynamicOutputs").copy();
            elements.getCompound(0).putString("Text", "Renamed");
            AdvancedHudInteractions.synchronize(node, edges);
            assertEquals(before, data.getCompound("DynamicOutputs")); assertEquals(4, edges.size());
            var order = new ArrayList<>(AdvancedGraphCatalog.outputs(node).keySet());
            assertEquals(elements.getCompound(0).getString("ExecPort"), order.get(0));
            assertEquals(elements.getCompound(0).getString("ValuePort"), order.get(1));
        }
    }

    @Test void designerNodesHaveMinimalInputsAndEmptyCanvasesRemainEmptyOnReload(){
        for(String type : List.of("acc_display_widget", "acc_hologram_widget")){
            var data = AdvancedGraphNodeFactory.createDefaultData(type, null);
            var node = new AdvancedGraphDocument.Node("widget", type, "", 0, 0, data);
            var graph = new AdvancedGraphDocument(); graph.nodes().add(node);
            var restored = graph.copy().nodes().getFirst();
            var inputs = AdvancedGraphCatalog.inputs(restored);
            assertTrue(inputs.containsKey("target")); assertTrue(inputs.containsKey("value"));
            assertFalse(inputs.containsKey("color")); assertFalse(inputs.containsKey("font_size"));
            assertFalse(inputs.containsKey("label")); assertFalse(restored.data().getCompound("Defaults").contains("label"));
            assertFalse(data.contains("WidgetType")); assertTrue(restored.data().getList("WidgetElements", 10).isEmpty());
            if(type.equals("acc_display_widget")) assertEquals(Set.of("target", "visible", "value"), inputs.keySet());
            else{
                assertTrue(inputs.keySet().containsAll(List.of("offset_x", "offset_y", "offset_z", "size_x", "size_y", "projection_rotation")));
                assertEquals(0.25, data.getCompound("Defaults").getCompound("offset_z").getCompound("Payload").getDouble("Value"));
            }
        }
    }

    @Test void legacyWidgetsBakeUnwiredSettingsAndKeepWiredCustomization(){
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", null);
        data.remove("DesignerWidget"); data.putString("WidgetType", "button");
        CompoundTag defaults = data.getCompound("Defaults");
        defaults.put("x", AdvancedGraphDocument.Value.number(17).toTag());
        defaults.put("color", AdvancedGraphDocument.Value.number(0xFF123456).toTag());
        data.put("Defaults", defaults);
        var graph = new AdvancedGraphDocument(); graph.nodes().add(new AdvancedGraphDocument.Node("widget", "acc_display_widget", "", 0, 0, data));
        graph.edges().add(new AdvancedGraphDocument.Edge("color", "source", "value", "widget", "color"));
        graph.edges().add(new AdvancedGraphDocument.Edge("label", "text", "value", "widget", "label"));
        var saved = graph.copy();
        var restored = saved.nodes().getFirst();
        var elm = restored.data().getList("WidgetElements", 10).getCompound(0);
        assertFalse(saved.edges().stream().anyMatch(edge -> "label".equals(edge.toPort())));
        assertEquals(17, elm.getInt("X")); assertTrue(AdvancedHudElementBinding.port(elm, "X").isEmpty());
        assertEquals("color", AdvancedHudElementBinding.port(elm, "Color"));
        assertTrue(AdvancedGraphCatalog.inputs(restored).containsKey("color"));
        assertFalse(AdvancedGraphCatalog.inputs(restored).containsKey("label"));
        assertTrue(AdvancedHudElementBinding.port(elm, "Text").isEmpty());
        assertEquals("", elm.getString("Text"));
        assertFalse(elm.getBoolean("ManagedWidget")); assertFalse(restored.data().contains("WidgetType"));
        assertEquals("button", elm.getString("Type"));
    }

    @Test void resizingAFullDisplayCanvasPreservesRelativePlacement(){
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", null);
        ListTag elements = new ListTag(); CompoundTag elm = new CompoundTag();
        elm.putInt("X", 14); elm.putInt("Y", 18); elm.putInt("W", 70); elm.putInt("H", 36); elements.add(elm);
        data.put("WidgetElements", elements);
        var node = new AdvancedGraphDocument.Node("widget", "acc_display_widget", "", 0, 0, data);
        AdvancedDisplayWidgets.resizeCanvas(node, 280, 144);
        elm = node.data().getList("WidgetElements", 10).getCompound(0);
        assertEquals(28, elm.getInt("X")); assertEquals(36, elm.getInt("Y"));
        assertEquals(140, elm.getInt("W")); assertEquals(72, elm.getInt("H"));
    }
}
