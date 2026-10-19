package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNumberDisplay;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphPreciseValuesTest{
    @Test void numericNodesAndResolvedAnyInputsAndOutputsExposeTheToggle() throws Exception{
        var graph = new AdvancedGraphDocument();
        var number = add(graph, "number", "constant_number");
        var compare = add(graph, "compare", "compare");
        var text = add(graph, "text", "constant_string");
        var reroute = add(graph, "reroute", "reroute");
        var getter = add(graph, "variable", "variable_get");
        getter.data().putString("Variable", "precise");
        graph.variables().put("precise", AdvancedGraphDocument.Value.number(Math.PI));
        graph.edges().add(new AdvancedGraphDocument.Edge("number-to-compare", number.id(), "value", compare.id(), "a"));
        graph.edges().add(new AdvancedGraphDocument.Edge("variable-to-reroute", getter.id(), "value", reroute.id(), "value"));
        var screen = screen(graph, 1);
        for(var node : List.of(number, compare, getter)){
            assertEquals(true, invoke(screen, "hasNumericDisplayPorts", new Class<?>[]{AdvancedGraphDocument.Node.class}, node), node.type());
        }
        assertEquals(false, invoke(screen, "hasNumericDisplayPorts", new Class<?>[]{AdvancedGraphDocument.Node.class}, text));
        assertEquals(false, invoke(screen, "hasNumericDisplayPorts", new Class<?>[]{AdvancedGraphDocument.Node.class}, reroute));
        Object rerouteButton = invoke(screen, "preciseValuesBounds", new Class<?>[]{AdvancedGraphDocument.Node.class}, reroute);
        assertNull(invoke(screen, "preciseValuesAt", new Class<?>[]{double.class, double.class},
                (int)accessor(rerouteButton, "x") + 2.0, (int)accessor(rerouteButton, "y") + 2.0));
        graph.variables().put("precise", AdvancedGraphDocument.Value.string("text"));
        assertEquals(false, invoke(screen, "hasNumericDisplayPorts", new Class<?>[]{AdvancedGraphDocument.Node.class}, getter));
        var literal = add(graph, "literal", "pulse_if");
        defaults(literal, "a", AdvancedGraphDocument.Value.number(Math.PI));
        assertEquals(true, invoke(screen, "hasNumericDisplayPorts", new Class<?>[]{AdvancedGraphDocument.Node.class}, literal));
    }

    @Test void constantInlineAndLiveAnyValuesUseFullPrecisionOnlyWhenEnabled() throws Exception{
        var graph = new AdvancedGraphDocument();
        var node = add(graph, "number", "constant_number");
        node.data().putDouble("Value", Math.PI);
        defaults(node, "amount", AdvancedGraphDocument.Value.number(Math.PI));
        var screen = screen(graph, 1);
        for(boolean precise : List.of(false, true)){
            GraphNumberDisplay.setPrecise(node.data(), precise);
            String expected = precise ? Double.toString(Math.PI) : "3.14";
            assertEquals(expected, invoke(screen, "propertyValueLabel", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class}, node, "Value"));
            assertEquals(expected, invoke(screen, "inputValueLabel", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, String.class}, node, "amount", "number"));
            assertEquals(expected, invoke(screen, "graphValueLabel", new Class<?>[]{AdvancedGraphDocument.Node.class, AdvancedGraphDocument.Value.class, String.class},
                    node, AdvancedGraphDocument.Value.number(Math.PI), "any"));
            assertEquals(expected, invoke(screen, "graphValueLabel", new Class<?>[]{AdvancedGraphDocument.Node.class, AdvancedGraphLiveValue.class, String.class},
                    node, AdvancedGraphLiveValue.from(AdvancedGraphDocument.Value.number(Math.PI)), "any"));
        }
        assertEquals(Math.PI, node.data().getDouble("Value"));
        assertEquals(Math.PI, AdvancedGraphDocument.Value.fromTag(node.data().getCompound("Defaults").getCompound("amount")).asNumber());
    }

    @Test void displayPreferenceSurvivesSavingAndUndoWithoutChangingRuntimeOrPreview() throws Exception{
        var graph = new AdvancedGraphDocument();
        var number = add(graph, "number", "constant_number");number.data().putDouble("Value", Math.PI);
        var runtime = new GraphRuntime(null, true);runtime.compile(graph);
        runtime.beginPreviewSample(graph);
        assertEquals(Math.PI, runtime.previewOutput(graph, number, "value").asNumber());
        var before = runtime.createShutdownSnapshot();
        int fingerprint = graph.programFingerprint();
        int simulationFingerprint = graph.simulationFingerprint();
        var screen = screen(graph, 1);
        invoke(screen, "togglePreciseValues", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
        assertEquals(fingerprint, graph.programFingerprint());
        assertEquals(simulationFingerprint, graph.simulationFingerprint());
        var restored = AdvancedGraphDocument.fromTag(graph.toTag());
        assertTrue(GraphNumberDisplay.precise(restored.nodes().getFirst().data()));
        assertEquals(before, runtime.createShutdownSnapshot());
        runtime.beginPreviewSample(graph);
        assertEquals(Math.PI, runtime.previewOutput(graph, number, "value").asNumber());
        assertFalse(GraphNumberDisplay.precise(((AdvancedGraphDocument)((ArrayDeque<?>)field(screen.getClass(), "undo").get(screen)).peek()).nodes().getFirst().data()));
        assertEquals(true, field(screen.getClass(), "draftDirty").get(screen));
    }

    @Test void buttonPrecedesTheTitleAndWideningDoesNotStretchTheHeader() throws Exception{
        for(boolean v2 : List.of(false, true)) for(double zoom : new double[]{0.65, 1, 1.75}){
            var graph = new AdvancedGraphDocument();
            var number = add(graph, "number", "constant_number");
            var screen = screen(graph, zoom);set(screen, "v2Ui", v2);
            int compactWidth = (int)invoke(screen, "nodeWidth", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            int compactHeight = (int)invoke(screen, "nodeHeight", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            var compactFrame = (com.rieno.gadgetsandgizmos.lib.graph.GraphNodeLayout.Frame)invoke(screen,
                    "nodeFrameLayout", new Class<?>[]{int.class}, (int)Math.round(compactHeight * zoom));
            invoke(screen, "togglePreciseValues", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            int width = (int)invoke(screen, "nodeWidth", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            assertTrue(width > compactWidth);
            assertTrue(width >= GraphNumberDisplay.PRECISE_WIDTH_SAMPLE.length() * 6 + 82);
            Object bounds = invoke(screen, "preciseValuesBounds", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            int x = (int)accessor(bounds, "x"), y = (int)accessor(bounds, "y");
            int base = (int)invoke(screen, "screenY", new Class<?>[]{double.class}, number.y());
            int height = (int)invoke(screen, "nodeHeight", new Class<?>[]{AdvancedGraphDocument.Node.class}, number);
            var frame = (com.rieno.gadgetsandgizmos.lib.graph.GraphNodeLayout.Frame)invoke(screen,
                    "nodeFrameLayout", new Class<?>[]{int.class}, (int)Math.round(height * zoom));
            assertEquals(compactHeight, height);
            assertEquals(compactFrame.titleHeight(), frame.titleHeight());
            assertTrue(y >= base);
            assertTrue(y + (int)accessor(bounds, "height") <= base + frame.titleHeight());
            int titleX = (int)invoke(screen, "nodeTitleLeft", new Class<?>[]{AdvancedGraphDocument.Node.class, int.class}, number, x);
            assertTrue(titleX > x + (int)accessor(bounds, "width"));
            assertSame(number, invoke(screen, "preciseValuesAt", new Class<?>[]{double.class, double.class}, x + 2.0, y + 2.0));
            assertNull(invoke(screen, "portAt", new Class<?>[]{double.class, double.class}, x + 2.0, y + 2.0));
        }
    }

    @Test void structuredOutputRefreshUpdatesSocketOrderAndMapKeysWithoutSorting() throws Exception{
        var graph = new AdvancedGraphDocument();
        var split = add(graph, "split", "split_list");
        var getter = add(graph, "getter", "map_get");
        var screen = screen(graph, 1);
        var controller = mock(com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity.class);
        var menu = mock(com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu.class);
        when(menu.getMenuConfigTargetBlockEntity()).thenReturn(controller);set(screen, "menu", menu);
        for(var keys : List.of(List.of("zeta", "alpha", "middle"), List.of("middle", "zeta", "alpha"))){
            var fields = new LinkedHashMap<String, Object>();keys.forEach(key -> fields.put(key, 1));
            var value = GraphRuntime.fromLibraryValue(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.map(fields));
            when(controller.previewGraphInput(graph, split, "value")).thenReturn(value);
            when(controller.previewGraphInput(graph, getter, "map")).thenReturn(value);
            int signature = (int)invoke(screen, "graphPortLayoutSignature", new Class<?>[0]);
            invoke(screen, "syncStructuredDataNodes", new Class<?>[0]);
            assertNotEquals(signature, invoke(screen, "graphPortLayoutSignature", new Class<?>[0]));
            invoke(screen, "clearGraphRenderCache", new Class<?>[0]);
            assertEquals(keys, List.copyOf(AdvancedGraphCatalog.outputs(split).keySet()));
            assertEquals(keys, getter.data().getCompound("InputOptions").getList("key", 8).stream()
                    .map(net.minecraft.nbt.Tag::getAsString).toList());
            invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            int previousY = Integer.MIN_VALUE;
            for(String port : keys){
                Object position = invoke(screen, "portPosition", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, split, port, true);
                int y = (int)accessor(position, "y");assertTrue(y > previousY);previousY = y;
            }
        }
    }

    @Test void functionBrowsersIncludeEveryAvailableCategoryAndCollectionNode() throws Exception{
        var graph = new AdvancedGraphDocument();var function = new AdvancedGraphDocument.FunctionGraph("function", "Function");
        graph.functions().add(function);
        var screen = screen(graph, 1);set(screen, "activeFunctionId", function.id());
        set(screen, "collapsedCategories", new HashSet<String>());set(screen, "collapsedMiniCategories", new HashSet<String>());
        var menu = mock(com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu.class);
        when(menu.getMenuConfigTargetBlockEntity()).thenReturn(mock(com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity.class));
        set(screen, "menu", menu);
        var expected = new HashSet<String>();
        for(var definition : AdvancedGraphCatalog.all()){
            if(!"function_call".equals(definition.id()) && (boolean)invoke(screen, "isNodeAvailableInEditor", new Class<?>[]{String.class}, definition.id())) expected.add(definition.id());
        }
        var entries = (List<?>)invoke(screen, "nodeBrowserEntries", new Class<?>[]{String.class}, "");
        var actual = new HashSet<String>();
        for(Object entry : entries) if(!(boolean)accessor(entry, "category")) actual.add((String)accessor(entry, "id"));
        assertTrue(actual.containsAll(expected));
        assertTrue(actual.containsAll(List.of("arr_filter", "arr_insert", "math_atan2", "function_input", "function_output",
                "exec_combine", "parallel_execution", "sequenced_execution", "bounded_loop",
                "createthrusters:collection_push", "createthrusters:collection_pop", "createthrusters:collection_contains", "createthrusters:collection_index_of")));
        invoke(screen, "openMiniBrowser", new Class<?>[]{int.class, int.class, String.class, String.class, boolean.class}, 50, 50, null, null, false);
        entries = (List<?>)invoke(screen, "miniBrowserEntries", new Class<?>[0]);actual.clear();
        for(Object entry : entries) if(!(boolean)accessor(entry, "category")) actual.add((String)accessor(entry, "id"));
        assertTrue(actual.containsAll(expected));
        var entry = new AdvancedGraphDocument.Node("entry", "function_input", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("function_input", null));
        var ports = new net.minecraft.nbt.CompoundTag();ports.putString("pulse", "exec");entry.data().put("DynamicOutputs", ports);
        var sink = new AdvancedGraphDocument.Node("sink", "count", "", 0, 0, AdvancedGraphNodeFactory.createDefaultData("count", null));
        function.nodes().addAll(List.of(entry, sink));
        invoke(screen, "openMiniBrowser", new Class<?>[]{int.class, int.class, String.class, String.class, boolean.class}, 50, 50, "entry", "pulse", true);
        entries = (List<?>)invoke(screen, "miniBrowserEntries", new Class<?>[0]);actual.clear();
        for(Object item : entries) if(!(boolean)accessor(item, "category")) actual.add((String)accessor(item, "id"));
        assertTrue(actual.contains("exec_combine"), "A dragged execution output must offer Exec Combine");
        invoke(screen, "openMiniBrowser", new Class<?>[]{int.class, int.class, String.class, String.class, boolean.class}, 50, 50, "sink", "exec", false);
        entries = (List<?>)invoke(screen, "miniBrowserEntries", new Class<?>[0]);actual.clear();
        for(Object item : entries) if(!(boolean)accessor(item, "category")) actual.add((String)accessor(item, "id"));
        assertTrue(actual.containsAll(List.of("exec_combine", "parallel_execution", "sequenced_execution", "bounded_loop")));
        var combine = new AdvancedGraphDocument.Node("combine", "exec_combine", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("exec_combine", null));
        assertEquals(false, invoke(screen, "isStandaloneExecSource", new Class<?>[]{AdvancedGraphDocument.Node.class}, combine));
        assertEquals(true, invoke(screen, "isStandaloneExecSource", new Class<?>[]{String.class}, "event_tick"));
    }

    @Test void receivingAnAppliedSnapshotRefreshesThePreviewEvenWhenTheDraftIsAlreadySaved() throws Exception{
        var graph = new AdvancedGraphDocument();add(graph, "loop", "bounded_loop");
        var screen = screen(graph, 1);set(screen, "savedDraft", graph.copy());
        set(screen, "previewActiveGraph", new AdvancedGraphDocument());set(screen, "previewActiveGraphRevision", graph.revision());
        set(screen, "simulatedDraft", graph);
        var snapshot = new com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload.GraphSnapshot(
                graph.revision(), graph.revision(), graph.toTag(), graph.toTag(), new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag());
        invoke(screen, "adoptControllerGraphSnapshot", new Class<?>[]{snapshot.getClass()}, snapshot);
        assertNull(field(screen.getClass(), "previewActiveGraph").get(screen));
        assertNull(field(screen.getClass(), "simulatedDraft").get(screen));
        assertSame(graph, field(screen.getClass(), "draft").get(screen));
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", graph.nodes().size() * 500, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, null));graph.nodes().add(node);return node;
    }

    private static void defaults(AdvancedGraphDocument.Node node, String port, AdvancedGraphDocument.Value value){
        var defaults = node.data().getCompound("Defaults");defaults.put(port, value.toTag());node.data().put("Defaults", defaults);
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
        set(screen, "undo", new ArrayDeque<AdvancedGraphDocument>());set(screen, "redo", new ArrayDeque<AdvancedGraphDocument>());
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
