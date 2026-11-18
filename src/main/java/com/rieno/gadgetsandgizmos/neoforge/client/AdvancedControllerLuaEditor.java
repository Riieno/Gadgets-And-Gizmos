package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunction;
import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.client.ui.ScriptEditorWorkspace;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Bind the shared script editor to ACC document nodes and authoritative port samples
final class AdvancedControllerLuaEditor{
    interface Host{
        AdvancedGraphDocument graph();
        String name(AdvancedGraphDocument.Node node);
        String saveStatus();
        default String saveStatus(AdvancedGraphDocument.Node node){ return saveStatus(); }
        default String saveError(AdvancedGraphDocument.Node node){ return ""; }
        AdvancedGraphDocument.Value input(AdvancedGraphDocument.Node node, String port);
        AdvancedGraphDocument.Value output(AdvancedGraphDocument.Node node, String port);
        void changed(AdvancedGraphDocument.Node node, String source);
        void create();
        void place(String id);
        default void rename(String id){}
        default void delete(String id){}
        default void wrap(){}
        default void save(){}
        default void terminalInput(AdvancedGraphDocument.Node node, ScriptInput input){}
        default List<com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral> peripherals(){ return List.of(); }
    }

    private final Host host;
    private String selected = "";
    private final ScriptEditorWorkspace workspace;

    AdvancedControllerLuaEditor(Font font, Host host){
        this.host = host;
        workspace = new ScriptEditorWorkspace(font, id -> { selected = id; refresh(); }, (id, source) -> {
            AdvancedGraphDocument.Node node = nodes(host.graph()).stream().filter(val -> identity(val).equals(id)).findFirst().orElse(null);
            if(node != null) host.changed(node, source);
        });
        workspace.browserTitles("ACC Scripts", "Computers");
        workspace.actions(host::create, host::place);
        workspace.documentActions(host::rename, host::delete);
        workspace.saveDocument(host::save);
        workspace.wrapPeripheral(host::wrap);
        workspace.terminalInput((id, input) -> nodes(host.graph()).stream().filter(node -> identity(node).equals(id)).findFirst()
                .ifPresent(node -> host.terminalInput(node, input)));
        if(net.neoforged.fml.ModList.get().isLoaded("computercraft"))
            workspace.api(com.rieno.gadgetsandgizmos.compat.computercraft.client.LuaEditorCompletions.names());
        else workspace.api(com.rieno.gadgetsandgizmos.lib.script.ScriptDocumentation.catalog(
                com.rieno.gadgetsandgizmos.content.peripheral.AdvancedContraptionControllerPeripheralControl.class).entries().stream()
                .flatMap(entry -> java.util.stream.Stream.of("gng." + entry.name() + "(", "acc." + entry.name() + "(")).toList());
    }

    static List<AdvancedGraphDocument.Node> nodes(AdvancedGraphDocument graph){
        if(graph == null) return List.of();
        AdvancedLuaFunctions.synchronize(graph);
        return graph.luaFunctions().stream().map(AdvancedLuaFunctions::prototype).toList();
    }

    void select(AdvancedGraphDocument.Node node){ selected = node.data().getString(ScriptFunction.REFERENCE); refresh(); workspace.openDocument(selected); }
    ScriptEditorWorkspace workspace(){ return workspace; }
    String selected(){ return selected; }

    private String identity(AdvancedGraphDocument.Node node){ return node.data().getString(ScriptFunction.REFERENCE); }

    boolean refresh(){
        workspace.peripherals(host.peripherals());
        List<AdvancedGraphDocument.Node> nodes = nodes(host.graph());
        if(nodes.isEmpty()){ selected = ""; workspace.refresh(List.of(), ""); return true; }
        if(nodes.stream().noneMatch(node -> identity(node).equals(selected))) selected = identity(nodes.getFirst());
        List<ScriptEditorWorkspace.Document> docs = new ArrayList<>();
        for(var node : nodes){
            Map<String, GraphValue> results = new LinkedHashMap<>();
            Map<String, GraphValue> inputs = new LinkedHashMap<>();
            AdvancedGraphCatalog.inputs(node).forEach((port, type) -> {
                var val = host.input(node, port);
                if(val != null && !"exec".equals(type)) inputs.put(port, "frequency".equals(val.type())
                        ? new GraphValue("frequency", com.rieno.gadgetsandgizmos.lib.graph.GraphDataNbt.fromCompound(val.payload()))
                        : GraphRuntime.toLibraryValue(val));
            });
            List<String> outputs = AdvancedGraphCatalog.outputs(node).entrySet().stream()
                    .filter(entry -> !"exec".equals(entry.getValue()) && !List.of("valid", "error", "busy", LuaGraphNode.INSTANCE).contains(entry.getKey()))
                    .map(Map.Entry::getKey).toList();
            for(String port : outputs){
                var val = host.output(node, port);
                if(val != null) results.put(port, GraphRuntime.toLibraryValue(val));
            }
            var error = host.output(node, "error");
            var busy = host.output(node, "busy");
            var valid = host.output(node, "valid");
            String message = error == null ? "" : error.asString();
            if(message.isBlank()) message = host.saveError(node);
            boolean running = busy != null && busy.asBoolean();
            String status = running ? "Running…" : !message.isEmpty() ? "Script error"
                    : valid != null && valid.asBoolean() ? "Valid" : "Waiting for result";
            if(!host.saveStatus(node).isBlank()) status += " • " + host.saveStatus(node);
            var terminal = host.output(node, ScriptTerminal.PORT);
            docs.add(new ScriptEditorWorkspace.Document(identity(node), host.name(node), node.data().getString(LuaGraphNode.SOURCE),
                    AdvancedGraphCatalog.inputs(node).entrySet().stream().filter(entry -> !"exec".equals(entry.getValue())).map(Map.Entry::getKey).toList(),
                    outputs, results, status, message, inputs, terminal == null ? null : ScriptTerminal.fromValue(GraphRuntime.toLibraryValue(terminal))));
        }
        workspace.refresh(docs, selected);
        return true;
    }
}
