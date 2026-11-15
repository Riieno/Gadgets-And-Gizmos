package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Bind graph-local Lua definitions to independent node instances
public final class AdvancedLuaFunctions{
    public static final String PREFIX = "lua_function:";
    public static final String LIBRARY_ONLY = "LuaLibraryOnly";
    private AdvancedLuaFunctions(){}


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static ScriptFunction find(AdvancedGraphDocument graph, String id){
        return graph == null ? null : graph.luaFunctions().stream().filter(val -> val.id().equals(id)).findFirst().orElse(null);
    }

    // Remove a definition and its instances in the main graph and every function graph
    public static void remove(AdvancedGraphDocument graph, String id){
        if(graph == null) return;
        graph.luaFunctions().removeIf(function -> id.equals(function.id()));
        remove(graph.nodes(), graph.edges(), graph.groups(), id);
        graph.functions().forEach(function -> remove(function.nodes(), function.edges(), function.groups(), id));
    }

    private static void remove(List<AdvancedGraphDocument.Node> nodes, List<AdvancedGraphDocument.Edge> edges, List<CompoundTag> groups, String id){
        var removed = ScriptFunction.removeInstances(nodes, edges, id, node -> "createthrusters:lua".equals(node.type()) ? node.data().getString(ScriptFunction.REFERENCE) : "",
                AdvancedGraphDocument.Node::id, AdvancedGraphDocument.Edge::fromNode, AdvancedGraphDocument.Edge::toNode);
        for(var group : groups){
            var members = new java.util.LinkedHashSet<>(AdvancedGraphSelection.commentGroupNodeIds(group));
            if(members.removeAll(removed)) AdvancedGraphSelection.setCommentGroupNodeIds(group, members);
        }
    }

    public static boolean rename(AdvancedGraphDocument graph, String id, String name){
        var definition = find(graph, id);
        if(definition == null || !definition.rename(name)) return false;
        nodes(graph).stream().filter(node -> id.equals(node.data().getString(ScriptFunction.REFERENCE))).forEach(node -> definition.apply(node.data()));
        return true;
    }

    public static List<AdvancedGraphDocument.Node> nodes(AdvancedGraphDocument graph){
        List<AdvancedGraphDocument.Node> res = new ArrayList<>(graph.nodes());
        graph.functions().forEach(function -> res.addAll(function.nodes()));
        return res.stream().filter(node -> "createthrusters:lua".equals(node.type())).toList();
    }

    // Preserve legacy scripts by assigning each one a reusable definition before its first save
    public static void synchronize(AdvancedGraphDocument graph){
        for(var node : nodes(graph)){
            String id = node.data().getString(ScriptFunction.REFERENCE);
            ScriptFunction function = find(graph, id);
            if(function == null && (id.isBlank() || node.data().contains(LuaGraphNode.SOURCE))){
                String name = node.data().getCompound("Defaults").getCompound("name").getCompound("Payload").getString("Value");
                String reference;
                try{ reference = UUID.fromString(id).toString(); }
                catch(IllegalArgumentException ex){
                    String scope = graph.functions().stream().filter(owner -> owner.nodes().stream().anyMatch(val -> val == node))
                            .map(AdvancedGraphDocument.FunctionGraph::id).findFirst().orElse("main");
                    reference = ScriptFunction.legacyId(scope, node.id());
                }
                function = new ScriptFunction(reference, name.isBlank() ? "Lua Function" : name, node.data());
                graph.luaFunctions().add(function);
                node.data().putString(ScriptFunction.REFERENCE, function.id());
            }
            if(function != null && node.data().contains(ScriptFunction.APPLIED_VERSION)
                    && node.data().getInt(ScriptFunction.APPLIED_VERSION) != ScriptFunction.fingerprint(node.data())) function.update(node.data());
        }
        for(var node : nodes(graph)){
            var function = find(graph, node.data().getString(ScriptFunction.REFERENCE));
            if(function != null) function.apply(node.data());
        }
        prune(graph.nodes(), graph.edges());
        graph.functions().forEach(function -> prune(function.nodes(), function.edges()));
    }

    private static void prune(List<AdvancedGraphDocument.Node> nodes, List<AdvancedGraphDocument.Edge> edges){
        edges.removeIf(edge -> {
            var source = nodes.stream().filter(node -> "createthrusters:lua".equals(node.type()) && node.id().equals(edge.fromNode())).findFirst().orElse(null);
            var target = nodes.stream().filter(node -> "createthrusters:lua".equals(node.type()) && node.id().equals(edge.toNode())).findFirst().orElse(null);
            return source != null && !AdvancedGraphCatalog.outputs(source).containsKey(edge.fromPort())
                    || target != null && !AdvancedGraphCatalog.inputs(target).containsKey(edge.toPort());
        });
    }

    public static AdvancedGraphDocument.Node prototype(ScriptFunction function){
        CompoundTag data = function.data().copy();
        function.apply(data); data.putBoolean(LIBRARY_ONLY, true);
        return new AdvancedGraphDocument.Node("lua-library:" + function.id(), "createthrusters:lua", function.name(), 0, 0, data);
    }

    public static void renamePort(AdvancedGraphDocument graph, AdvancedGraphDocument.Node edited, String port, String name, boolean output){
        String id = edited.data().getString(ScriptFunction.REFERENCE);
        var definition = find(graph, id);
        if(definition != null) com.rieno.gadgetsandgizmos.lib.graph.ScriptNodePorts.rename(definition.data(), port, name, output,
                LuaGraphNode.ports(definition.data(), output).keySet());
        renamePort(graph.nodes(), graph.edges(), id, port, name, output);
        graph.functions().forEach(function -> renamePort(function.nodes(), function.edges(), id, port, name, output));
    }

    private static void renamePort(List<AdvancedGraphDocument.Node> nodes, List<AdvancedGraphDocument.Edge> edges,
                                   String id, String port, String name, boolean output){
        var ids = new java.util.HashSet<String>();
        for(var node : nodes){
            if(!id.equals(node.data().getString(ScriptFunction.REFERENCE)) || !"createthrusters:lua".equals(node.type())) continue;
            ids.add(node.id());
            com.rieno.gadgetsandgizmos.lib.graph.ScriptNodePorts.rename(node.data(), port, name, output,
                    LuaGraphNode.ports(node.data(), output).keySet());
        }
        for(int idx = 0; idx < edges.size(); idx++){
            var edge = edges.get(idx);
            if(output && ids.contains(edge.fromNode()) && edge.fromPort().equals(port))
                edges.set(idx, new AdvancedGraphDocument.Edge(edge.id(), edge.fromNode(), name, edge.toNode(), edge.toPort()));
            if(!output && ids.contains(edge.toNode()) && edge.toPort().equals(port))
                edges.set(idx, new AdvancedGraphDocument.Edge(edge.id(), edge.fromNode(), edge.fromPort(), edge.toNode(), name));
        }
    }

    // Add validation-only entries to the compiled graph without placing nodes in the authored graph
    public static void validationNodes(AdvancedGraphDocument source, AdvancedGraphDocument expanded){
        source.luaFunctions().forEach(function -> expanded.nodes().add(prototype(function)));
    }
}
