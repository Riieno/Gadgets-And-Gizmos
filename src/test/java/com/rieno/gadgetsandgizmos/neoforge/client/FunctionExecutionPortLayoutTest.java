package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphFunctions;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionExecutionPortLayoutTest{
    @Test void interfaceAndCallExecutionPortsHaveDistinctBodyAnchorsAndHitTargets() throws Exception{
        for(double zoom : new double[]{0.35,0.65,1.0,1.75}){
            for(boolean collapsed : new boolean[]{false,true}){
                var graph = graph(collapsed);
                var function = graph.function("routes");
                for(boolean functionView : new boolean[]{false,true}){
                    var screen = screen(graph,functionView ? function.id() : null,zoom);
                    invoke(screen,"prepareGraphRenderCache",new Class<?>[0]);
                    for(var node : functionView ? function.nodes() : graph.nodes()){
                        int base = (int)invoke(screen,"screenY",new Class<?>[]{double.class},node.y());
                        int header = (int)field(AdvancedContraptionControllerScreen.class,"NODE_HEADER").get(null);
                        int height = (int)invoke(screen,"nodeHeight",new Class<?>[]{AdvancedGraphDocument.Node.class},node);
                        var anchors = new HashSet<Integer>();
                        for(boolean output : new boolean[]{false,true}){
                            var ports = output ? AdvancedGraphCatalog.outputs(node) : AdvancedGraphCatalog.inputs(node);
                            for(var port : ports.entrySet()){
                                if(!"exec".equals(port.getValue())) continue;
                                Object position = invoke(screen,"portPosition",new Class<?>[]{AdvancedGraphDocument.Node.class,String.class,boolean.class},node,port.getKey(),output);
                                int x = (int)accessor(position,"x"), y = (int)accessor(position,"y");
                                assertTrue(y > base + header * zoom,"Execution port is still in the title bar");
                                assertTrue(y < base + height * zoom,"Execution port extends beyond its node");
                                assertTrue(anchors.add(y),"Function execution ports overlap each other");
                                Object hit = invoke(screen,"portAt",new Class<?>[]{double.class,double.class},(double)x,(double)y);
                                assertNotNull(hit,"Execution port cannot be wired");
                                assertEquals(port.getKey(),accessor(hit,"port"),"Click selected a neighbouring port");
                                assertEquals(output,accessor(hit,"output"));
                                if(functionView){
                                    int nodeX = (int)invoke(screen,"screenX",new Class<?>[]{double.class},node.x());
                                    assertEquals(port.getKey(),invoke(screen,"functionInterfaceBodyPortAt",
                                            new Class<?>[]{AdvancedGraphDocument.Node.class,double.class,double.class},node,(double)(nodeX + 14),(double)y),
                                            "Execution label editor selected a different row");
                                }
                            }
                        }
                        assertEquals(functionView ? 3 : 6,anchors.size());
                    }
                }
            }
        }
    }

    @Test void functionExecutionLabelsParticipateInNodeWidthMeasurement() throws Exception{
        var graph = graph(false);var function = graph.function("routes");
        var input = function.nodes().getFirst();
        String label = "Start the docking sequence and wait for its completion";
        CompoundTag labels = new CompoundTag();labels.putString("start",label);
        input.data().put(AdvancedGraphFunctions.PORT_LABELS,labels);
        AdvancedGraphFunctions.configureCall(graph.nodes().getFirst(),function);
        for(boolean functionView : new boolean[]{false,true}){
            var screen = screen(graph,functionView ? function.id() : null,1);
            invoke(screen,"prepareGraphRenderCache",new Class<?>[0]);
            var node = functionView ? input : graph.nodes().getFirst();
            int width = (int)invoke(screen,"nodeWidth",new Class<?>[]{AdvancedGraphDocument.Node.class},node);
            assertTrue(width >= label.length() * 6 + 82,"Execution label width was ignored");
        }
    }

    @Test void setResetLatchHasTwoSeparateLabeledBodyPortsAtEveryZoom() throws Exception{
        for(double zoom : new double[]{0.35,0.65,1.0,1.75}) for(boolean collapsed : new boolean[]{false,true}){
            var graph = new AdvancedGraphDocument();
            var node = new AdvancedGraphDocument.Node("latch","sr_latch","",0,0,new CompoundTag());
            node.data().putBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY,collapsed);graph.nodes().add(node);
            var screen = screen(graph,null,zoom);invoke(screen,"prepareGraphRenderCache",new Class<?>[0]);
            int base = (int)invoke(screen,"screenY",new Class<?>[]{double.class},node.y());
            int header = (int)field(AdvancedContraptionControllerScreen.class,"NODE_HEADER").get(null);
            int height = (int)invoke(screen,"nodeHeight",new Class<?>[]{AdvancedGraphDocument.Node.class},node);
            int previous = Integer.MIN_VALUE;
            for(String input : List.of("set","reset")){
                Object position = invoke(screen,"portPosition",new Class<?>[]{AdvancedGraphDocument.Node.class,String.class,boolean.class},node,input,false);
                int x = (int)accessor(position,"x"), y = (int)accessor(position,"y");
                assertTrue(y > base + header * zoom && y < base + height * zoom,"Latch input lies outside its body");
                assertTrue(y > previous,"Set and Reset inputs overlap or are reversed");previous = y;
                Object hit = invoke(screen,"portAt",new Class<?>[]{double.class,double.class},(double)x,(double)y);
                assertNotNull(hit);assertEquals(input,accessor(hit,"port"));assertEquals(false,accessor(hit,"output"));
            }
        }
    }

    private static AdvancedGraphDocument graph(boolean collapsed){
        var graph = new AdvancedGraphDocument();
        var function = new AdvancedGraphDocument.FunctionGraph("routes","Routes");
        var input = new AdvancedGraphDocument.Node("input","function_input","",0,0,new CompoundTag());
        var output = new AdvancedGraphDocument.Node("output","function_output","",800,0,new CompoundTag());
        input.data().put("DynamicOutputs",ports("start","abort","reset"));
        output.data().put("DynamicInputs",ports("success","failure","cancelled"));
        input.data().putBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY,collapsed);
        output.data().putBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY,collapsed);
        function.nodes().addAll(List.of(input,output));graph.functions().add(function);
        var call = new AdvancedGraphDocument.Node("call","function_call","",0,0,new CompoundTag());
        AdvancedGraphFunctions.configureCall(call,function);
        call.data().putBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY,collapsed);graph.nodes().add(call);
        return graph;
    }

    private static CompoundTag ports(String... execution){
        CompoundTag ports = new CompoundTag();
        for(String id : execution) ports.putString(id,"exec");
        ports.putString("speed","number");ports.putString("enabled","boolean");
        return ports;
    }

    private static AdvancedContraptionControllerScreen screen(AdvancedGraphDocument graph,String function,double zoom) throws Exception{
        var screen = mock(AdvancedContraptionControllerScreen.class,CALLS_REAL_METHODS);
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length() * 6);
        when(font.split(any(Component.class),anyInt())).thenAnswer(call -> List.of(
                FormattedCharSequence.forward(((Component)call.getArgument(0)).getString(),Style.EMPTY)));
        field(Font.class,"lineHeight").setInt(font,9);
        set(screen,"font",font);set(screen,"draft",graph);set(screen,"activeFunctionId",function);set(screen,"zoom",zoom);
        set(screen,"width",1600);set(screen,"height",1000);set(screen,"selectedNodes",new HashSet<String>());
        set(screen,"collapsedNodes",new HashSet<String>());set(screen,"graphRenderNodes",new LinkedHashMap<>());
        set(screen,"graphRenderPortLayouts",new LinkedHashMap<>());
        return screen;
    }

    private static Object accessor(Object value,String name) throws Exception{
        Method method = value.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);
    }
    private static Object invoke(Object object,String name,Class<?>[] types,Object... args) throws Exception{
        Method method = AdvancedContraptionControllerScreen.class.getDeclaredMethod(name,types);method.setAccessible(true);
        return method.invoke(object,args);
    }
    private static void set(Object object,String name,Object value) throws Exception{ field(object.getClass(),name).set(object,value); }
    private static Field field(Class<?> type,String name) throws Exception{
        while(type != null){
            try{ Field field = type.getDeclaredField(name);field.setAccessible(true);return field; }
            catch(NoSuchFieldException missing){ type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
