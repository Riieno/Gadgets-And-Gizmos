package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeLayout;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GraphEditorRenderingTest{
    @Test void canvasSearchFindsAuthoredVariableNamesAndKeepsFunctionScope() throws Exception{
        var graph = new AdvancedGraphDocument();
        var getter = node("get", "variable_get");getter.data().putString("Variable", "CargoCapacity");
        var setter = node("set", "variable_set");setter.data().putString("Variable", "CargoCapacity");
        var changed = node("changed", "event_variable_change");changed.data().putString("Variable", "CargoCapacity");
        var alias = node("alias", "constant_number");alias.data().putString(GraphNodeAlias.DATA_KEY, "Airship Fuel");
        graph.nodes().addAll(List.of(getter, setter, changed, alias));
        var screen = screen(graph, 1);
        set(screen, "graphSearchQuery", "cARGocapa");
        assertEquals(List.of("get", "set", "changed"), matches(screen));
        set(screen, "graphSearchQuery", "airship fuel");assertEquals(List.of("alias"), matches(screen));
        var function = new AdvancedGraphDocument.FunctionGraph("local", "Local");graph.functions().add(function);
        var local = node("local_get", "variable_get");local.data().putString("Variable", "LocalAltitude");function.nodes().add(local);
        set(screen, "activeFunctionId", function.id());set(screen, "graphSearchQuery", "localALT");
        assertEquals(List.of("local_get"), matches(screen));
        set(screen, "graphSearchQuery", "Cargo");assertTrue(matches(screen).isEmpty());
    }

    @Test void manyPortRowsKeepStableCoordinatesAndReuseTheirStructuralCache() throws Exception{
        for(double zoom : new double[]{.65, 1, 1.75}){
            var graph = new AdvancedGraphDocument();var node = node("large", "set_block_data");graph.nodes().add(node);
            CompoundTag inputs = new CompoundTag();CompoundTag sections = new CompoundTag();List<String> order = new ArrayList<>();
            for(int group = 0; group < 10; group++){
                ListTag members = new ListTag();
                for(int row = 0; row < 50; row++){
                    String port = "p" + (group * 50 + row);inputs.putString(port, "number");members.add(StringTag.valueOf(port));order.add(port);
                }
                CompoundTag section = new CompoundTag();section.put("Ports", members);sections.put("g" + group, section);
            }
            node.data().put("DynamicInputs", inputs);CompoundTag encoded = new CompoundTag();encoded.put("Inputs", sections);
            node.data().put(AdvancedGraphCatalog.PORT_SECTIONS_TAG, encoded);
            GraphPortOrder.write(node.data(), GraphPortOrder.INPUT_ORDER, order);
            var screen = screen(graph, zoom);invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            var layouts = (Map<?, ?>)field(screen.getClass(), "graphRenderPortLayouts").get(screen);
            Object layout = layouts.get(node.id());Method rowsMethod = layout.getClass().getDeclaredMethod("inputRows");rowsMethod.setAccessible(true);
            var rows = (GraphNodeLayout.PortRows)rowsMethod.invoke(layout);
            int firstRow = rows.offsets().get("p0");
            int origin = (int)invoke(screen, "nodeBodyTop", new Class<?>[]{AdvancedGraphDocument.Node.class, int.class}, node,
                    (int)invoke(screen, "screenY", new Class<?>[]{double.class}, node.y()));
            for(int frame = 0; frame < 5; frame++){
                invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);assertSame(layout, layouts.get(node.id()));
                assertSame(rows, rowsMethod.invoke(layout));
                for(int idx = 0; idx < 500; idx++){
                    int row = firstRow + idx + idx / 50;
                    assertEquals(origin + row * (int)(15 * zoom) + (int)(6.5 * zoom),
                            invoke(screen, "bodyControlCenterY", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class}, node, "p" + idx));
                }
            }
            Object header = invoke(screen, "portSection", new Class<?>[]{AdvancedGraphDocument.Node.class, String.class, boolean.class}, node, "p50", false);
            invoke(screen, "togglePortSection", new Class<?>[]{AdvancedGraphDocument.Node.class, header.getClass(), boolean.class}, node, header, false);
            invoke(screen, "prepareGraphRenderCache", new Class<?>[0]);
            Object rebuilt = ((Map<?, ?>)field(screen.getClass(), "graphRenderPortLayouts").get(screen)).get(node.id());
            assertNotSame(layout, rebuilt);var collapsed = (GraphNodeLayout.PortRows)rowsMethod.invoke(rebuilt);
            assertFalse(collapsed.offsets().containsKey("p50"));assertTrue(collapsed.offsets().containsKey("p100"));
        }
    }

    @Test void editorSuppressesOnlyTheMainWorldAndNeverPausesSimulation() throws Exception{
        var screen = screen(new AdvancedGraphDocument(), 1);
        assertTrue(screen.suppressWorldRendering());assertFalse(screen.isPauseScreen());
        set(screen, "projectionRendering", true);assertFalse(screen.suppressWorldRendering());assertFalse(screen.isPauseScreen());
    }

    private static List<String> matches(AdvancedContraptionControllerScreen screen) throws Exception{
        return ((List<AdvancedGraphDocument.Node>)invoke(screen, "graphSearchMatches", new Class<?>[0])).stream()
                .map(AdvancedGraphDocument.Node::id).toList();
    }
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
    }
    private static AdvancedContraptionControllerScreen screen(AdvancedGraphDocument graph, double zoom) throws Exception{
        var screen = mock(AdvancedContraptionControllerScreen.class, CALLS_REAL_METHODS);var font = mock(Font.class);
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
