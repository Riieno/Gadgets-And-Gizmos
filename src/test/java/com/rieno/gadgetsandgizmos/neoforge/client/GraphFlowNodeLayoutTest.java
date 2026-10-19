package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphFlowNodeLayoutTest{
    @Test void dataTargetRowsAndWireTargetsStayInsideTheirGroups() throws Exception{
        for(boolean output : new boolean[]{false, true}){
            for(double zoom : new double[]{0.65, 1, 1.75}){
                var graph = new AdvancedGraphDocument();
                var data = groupedDataNode(output);
                graph.nodes().add(data);
                var screen = screen(graph, zoom);
                invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
                int previous = Integer.MIN_VALUE;
                for(String port : List.of("a_first", "a_map", "a_map.child", "a_last", "b_first", "b_last")){
                    Object pos = invoke(screen, "portPosition",
                            new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, data, port, output);
                    int x = (int)accessor(pos, "x"), y = (int)accessor(pos, "y");
                    assertTrue(y > previous, "Target group rows interleave at " + port + ", output=" + output);
                    previous = y;
                    Object hit = invoke(screen, "portAt", new Class<?>[]{double.class, double.class}, (double)x, (double)y);
                    assertNotNull(hit, port);
                    assertEquals(port, accessor(hit, "port"), "Wire hit target left its displayed group");
                }
            }
        }
    }

    @Test void expandedMapChildrenCollapseWithTheirTargetGroup() throws Exception{
        for(boolean output : new boolean[]{false, true}){
            var graph = new AdvancedGraphDocument();
            var data = groupedDataNode(output);
            graph.nodes().add(data);
            var screen = screen(graph, 1);
            invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            Object section = invoke(screen, "portSection",
                    new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, data, "a_map", output);
            Object childSection = invoke(screen, "portSection",
                    new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, data, "a_map.child", output);
            assertNotNull(childSection, "Expanded MAP child has no target group");
            assertEquals(accessor(section, "id"), accessor(childSection, "id"));
            invoke(screen, "togglePortSection",
                    new Class<?>[]{AdvancedGraphDocument.Node.class, section.getClass(), boolean.class}, data, section, output);
            assertEquals(false, invoke(screen, "isDataPortVisible",
                    new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, data, "a_map.child", output));
            assertEquals(true, invoke(screen, "isDataPortVisible",
                    new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, data, "b_first", output));
        }
    }

    private static AdvancedGraphDocument.Node groupedDataNode(boolean output){
        var data = node("data", output ? "get_block_data" : "set_block_data", 0);
        var dynamic = new CompoundTag();
        var order = List.of("a_last", "b_last", "a_map.child", "a_map", "b_first", "a_first");
        for(String port : order) dynamic.putString(port, port.equals("a_map") ? "map" : "number");
        data.data().put(output ? "DynamicOutputs" : "DynamicInputs", dynamic);
        GraphPortOrder.write(data.data(), output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER, order);
        var sections = new CompoundTag();
        sections.put("a", section("a_first", "a_map", "a_last"));
        sections.put("b", section("b_first", "b_last"));
        var encoded = new CompoundTag();
        encoded.put(output ? "Outputs" : "Inputs", sections);
        data.data().put(AdvancedGraphCatalog.PORT_SECTIONS_TAG, encoded);
        var mapping = new CompoundTag();
        mapping.putString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG, "a_map");
        mapping.putString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG, "child");
        var mappings = new CompoundTag();
        mappings.put("a_map.child", mapping);
        data.data().put(output ? AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG
                : AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG, mappings);
        return data;
    }

    private static CompoundTag section(String... ports){
        var section = new CompoundTag();
        var entries = new ListTag();
        for(String port : ports) entries.add(StringTag.valueOf(port));
        section.put("Ports", entries);
        return section;
    }

    @Test void loopOutputsHaveDistinctWireTargetsAndOnlyPassthroughUsesTheTitleBar() throws Exception{
        for(double zoom : new double[]{0.35, 0.65, 1, 1.75}){
            var graph = new AdvancedGraphDocument();
            var loop = node("loop", "bounded_loop", 0);
            graph.nodes().add(loop);
            var screen = screen(graph, zoom);
            invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            int header = field(AdvancedContraptionControllerScreen.class, "NODE_HEADER").getInt(null);
            int base = (int)invoke(screen, "screenY", new Class<?>[]{double.class}, loop.y());
            var rows = new HashSet<Integer>();
            for(String port : List.of("exec", "body", "complete", "index")){
                Object pos = invoke(screen, "portPosition", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, loop, port, true);
                int x = (int)accessor(pos, "x"), y = (int)accessor(pos, "y");
                assertTrue(rows.add(y), "Loop sockets overlap");
                assertEquals(port.equals("exec"), y < base + header * zoom);
                Object hit = invoke(screen, "portAt", new Class<?>[]{double.class, double.class}, (double)x, (double)y);
                assertNotNull(hit, port);
                assertEquals(port, accessor(hit, "port"), "Hit selected a neighbouring loop output");
                assertEquals(true, accessor(hit, "output"));
            }
        }
    }

    @Test void pulseIfKeepsItsOperatorBetweenTwoSocketsAndMatchesBToA() throws Exception{
        var graph = new AdvancedGraphDocument();
        var source = node("source", "constant_string", 0);
        var pulse = node("condition", "pulse_if", 500);
        graph.nodes().addAll(List.of(source, pulse));
        graph.edges().add(new AdvancedGraphDocument.Edge("a", "source", "value", "condition", "a"));
        var screen = screen(graph, 1);
        invoke(screen, "synchronizeComparePorts", new Class<?>[0]);
        assertEquals("any", AdvancedGraphCatalog.inputs(pulse).get("a"));
        assertEquals("string", AdvancedGraphCatalog.inputs(pulse).get("b"));
        assertEquals(List.of("a", "b"), List.copyOf(AdvancedGraphCatalog.inputs(pulse).keySet()));
        assertEquals(2, AdvancedGraphCatalog.get("pulse_if").inputs().size());
        assertEquals(List.of("a", "operator", "b"), List.copyOf(((java.util.Map<?, ?>)invoke(screen,
                "nodeInputs", new Class<?>[]{AdvancedGraphDocument.Node.class}, pulse)).keySet()));
        invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
        int previous = Integer.MIN_VALUE;
        for(String port : List.of("a", "operator", "b")){
            Object pos = invoke(screen, "portPosition", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, pulse, port, false);
            int x = (int)accessor(pos, "x"), y = (int)accessor(pos, "y");
            assertTrue(y > previous);previous = y;
            Object hit = invoke(screen, "portAt", new Class<?>[]{double.class, double.class}, (double)x, (double)y);
            if(port.equals("operator")) assertNull(hit, "Operator setting exposes a third socket");
            else assertEquals(port, accessor(hit, "port"));
        }
    }

    @Test void flipSelectorHasNoWireSocketAndNthCountCannotBecomeNegative() throws Exception{
        var graph = new AdvancedGraphDocument();
        var flip = node("flip", "flip_flop", 0);graph.nodes().add(flip);
        var screen = screen(graph, 1);
        assertEquals(List.of("Every Pulse", "Every Second Pulse", "Every Nth Pulse"),
                invoke(screen, "inputOptions", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class}, flip, "flip_on"));
        invoke(screen, "putInputDefault", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, String.class, Object.class},
                flip, "flip_on", "string", "Every Nth Pulse");
        invoke(screen, "putInputDefault", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, String.class, Object.class},
                flip, "skip_pulses", "number", -5);
        assertEquals(0, AdvancedGraphDocument.Value.fromTag(flip.data().getCompound("Defaults").getCompound("skip_pulses")).asNumber());
        assertEquals("number", AdvancedGraphCatalog.inputs(flip).get("skip_pulses"));
        invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
        Object pos = invoke(screen, "portPosition", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, flip, "flip_on", false);
        assertNull(invoke(screen, "portAt", new Class<?>[]{double.class, double.class},
                (double)(int)accessor(pos, "x"), (double)(int)accessor(pos, "y")));
    }

    private static AdvancedGraphDocument.Node node(String id, String type, double x){
        return new AdvancedGraphDocument.Node(id, type, "", x, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }

    private static AdvancedContraptionControllerScreen screen(AdvancedGraphDocument graph, double zoom) throws Exception{
        var screen = mock(AdvancedContraptionControllerScreen.class, CALLS_REAL_METHODS);
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length() * 6);
        when(font.split(any(Component.class), anyInt())).thenAnswer(call -> List.of(
                FormattedCharSequence.forward(((Component)call.getArgument(0)).getString(), Style.EMPTY)));
        field(Font.class, "lineHeight").setInt(font, 9);
        set(screen, "font", font);set(screen, "draft", graph);set(screen, "zoom", zoom);
        set(screen, "width", 1600);set(screen, "height", 1000);
        set(screen, "selectedNodes", new HashSet<String>());set(screen, "collapsedNodes", new HashSet<String>());
        set(screen, "graphRenderNodes", new LinkedHashMap<>());set(screen, "graphRenderPortLayouts", new LinkedHashMap<>());
        return screen;
    }

    private static Object accessor(Object value, String name) throws Exception{
        Method method = value.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception{
        Method method = AdvancedContraptionControllerScreen.class.getDeclaredMethod(name, types);method.setAccessible(true);return method.invoke(object, args);
    }
    private static void set(Object object, String name, Object value) throws Exception{ field(object.getClass(), name).set(object, value); }
    private static Field field(Class<?> type, String name) throws Exception{
        while(type != null){
            try{ Field field = type.getDeclaredField(name);field.setAccessible(true);return field; }
            catch(NoSuchFieldException missing){ type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
