package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.debug.DebugProps;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JVMGraphRuntime extends DummyRuntime {
    public final DebugProps debugProps;
    public AbstractJVMGraph compiledGraph;
    public AbstractJVMGraph previewGraph;
    public AbstractJVMGraph previewGraphOr(AdvancedGraphDocument document){
        if(previewGraph == null || hasDifference(previewGraph, document)){
            previewGraph=JVMGraphCompiler.compile(document, debugProps);
        }
        return previewGraph;
    }

    private boolean hasDifference(AbstractJVMGraph jvmGraph, AdvancedGraphDocument document) {
        return jvmGraph.graphRevision != document.revision() || jvmGraph.graph!=document;
    }

    public AbstractJVMGraph compiledGraphOr(AdvancedGraphDocument document){
        if(compiledGraph==null || hasDifference(compiledGraph,document)){
            compiledGraph=JVMGraphCompiler.compile(document, debugProps);
        }
        return compiledGraph;
    }

    @Override
    public AdvancedGraphDocument.Value previewFunctionOutput(AdvancedGraphDocument graph, String functionId, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewFunctionInput(AdvancedGraphDocument graph, String functionId, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        return previewOutput(graph, node, port,false);
    }
    //@Override
    public AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port,boolean checkPort) {
        AbstractJVMGraph jvmGraph = previewGraphOr(graph);
        if(jvmGraph ==null)return null;
        return jvmGraph.outputOf(node,port,checkPort);
    }

    @Override
    public AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        AbstractJVMGraph jvmGraph = previewGraphOr(graph);
        if(jvmGraph ==null)return null;
        return jvmGraph.inputOf(node,port);
    }

}
