package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.RequiredArgsConstructor;

import java.io.File;

@RequiredArgsConstructor
public class JVMGraphRuntime extends DummyRuntime {
    public final File debugDir;
    public AbstractJVMGraph compiledGraph;
    public AbstractJVMGraph previewGraph;
    public AbstractJVMGraph previewGraphOr(AdvancedGraphDocument document){
        if(previewGraph==null || previewGraph.graphRevision!=document.revision()){
            previewGraph=JVMGraphCompiler.compile(document,debugDir);
        }
        return previewGraph;
    }
    public AbstractJVMGraph compiledGraphOr(AdvancedGraphDocument document){
        if(compiledGraph==null || compiledGraph.graphRevision!=document.revision()){
            compiledGraph=JVMGraphCompiler.compile(document,debugDir);
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
        AbstractJVMGraph jvmGraph = previewGraphOr(graph);
        if(jvmGraph ==null)return null;
        return jvmGraph.outputOf(node,port);
    }

    @Override
    public AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        AbstractJVMGraph jvmGraph = previewGraphOr(graph);
        if(jvmGraph ==null)return null;
        return jvmGraph.inputOf(node,port);
    }

}
