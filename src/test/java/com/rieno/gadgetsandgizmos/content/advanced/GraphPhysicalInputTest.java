package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Read selected blocks before binding creation and retain their live values across graph saves
class GraphPhysicalInputTest{
    @Test void targetInputsPreviewAndExecuteFromTheWorldAfterSaving(){
        for(String type : List.of("discovered_target_input", "linker_face_input")){
            var controller = mock(AdvancedContraptionControllerBlockEntity.class);
            String sourcePort = "linker_face_input".equals(type)
                    ? GraphSignalRange.REDSTONE_SIGNAL_PORT : "value";
            double signal = 6.0D;
            when(controller.getGraphTargetData(any(), eq(sourcePort)))
                    .thenReturn(AdvancedGraphDocument.Value.number(signal));
            var graph = new AdvancedGraphDocument();
            var input = add(graph, "input", type);
            var ready = add(graph, "ready", "event_graph_ready");
            var setter = add(graph, "setter", "variable_set");
            setter.data().putString("Variable", "result");
            setter.data().putString("VariableType", "number");
            setter.data().getCompound("DynamicInputs").putString("value", "number");
            setter.data().getCompound("DynamicOutputs").putString("value", "number");
            graph.variables().put("result", AdvancedGraphDocument.Value.number(0));
            graph.edges().add(new AdvancedGraphDocument.Edge("exec", ready.id(), "exec", setter.id(), "exec"));
            graph.edges().add(new AdvancedGraphDocument.Edge("value", input.id(), "value", setter.id(), "value"));
            var preview = new GraphRuntime(controller, true);
            preview.beginPreviewSample(graph);
            assertEquals(signal, preview.previewOutput(graph, input, "value").asNumber());
            assertTrue(preview.previewOutput(graph, input, "active").asBoolean());
            var saved = AdvancedGraphDocument.fromTag(graph.toTag());
            var runtime = new GraphRuntime(controller);
            runtime.tick(saved, false);
            assertEquals(signal, saved.variables().get("result").asNumber());
            when(controller.getGraphTargetData(any(), eq(sourcePort)))
                    .thenReturn(AdvancedGraphDocument.Value.number(0));
            preview.invalidatePreview();
            preview.beginPreviewSample(graph);
            assertEquals(0, preview.previewOutput(graph, input, "value").asNumber());
            assertFalse(preview.previewOutput(graph, input, "active").asBoolean());
            verify(controller, never()).getGraphBindingValue(any());
        }
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
        graph.nodes().add(node);
        return node;
    }
}
