package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphInputDefaults;
import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphExplanation;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSelectionAnalyser;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSelectionAnalysis;
import com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSemanticProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning.Operation.*;

// Adapt the existing ACC catalog and saved configuration to the reusable semantic API
public final class AdvancedGraphExplanation implements GraphSemanticProvider<AdvancedGraphDocument.Node>{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final AdvancedGraphDocument document;
    private final GraphExplanation.TextResolver text;
    private final Map<String, List<String>> variableRefs = new HashMap<>();
    private final Map<String, List<String>> eventRefs = new HashMap<>();
    private final Set<String> wired = new HashSet<>();
    private final Set<String> functionStack;
    private final Map<String, String> functionSummaries;
    private final int depth;

    public AdvancedGraphExplanation(AdvancedGraphDocument document,
                                    GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> graph,
                                    GraphExplanation.TextResolver text){
        this(document, graph, text, Set.of(), new HashMap<>(), 0);
    }

    private AdvancedGraphExplanation(AdvancedGraphDocument document,
                                     GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> graph,
                                     GraphExplanation.TextResolver text, Set<String> functionStack,
                                     Map<String, String> functionSummaries, int depth){
        this.document = document;
        this.text = text;
        this.functionStack = functionStack;
        this.functionSummaries = functionSummaries;
        this.depth = depth;
        for(var node : graph.nodes()){
            String variable = node.data().getString("Variable");
            if(!variable.isBlank() && Set.of("variable_get", "variable_set", "event_variable_change").contains(node.type()))
                variableRefs.computeIfAbsent(variable, ignored -> new ArrayList<>()).add(node.id());
            String event = node.data().getString("Event");
            if(!event.isBlank() && Set.of("event_named_controller", "send_named_controller_event").contains(node.type()))
                eventRefs.computeIfAbsent(event, ignored -> new ArrayList<>()).add(node.id());
        }
        for(var edge : graph.edges()) wired.add(edge.toNode() + "." + edge.toPort());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public GraphNodeMeaning describe(AdvancedGraphDocument.Node node){
        var registered = AdvancedGraphCatalog.get(node.type());
        Map<String, String> inputs = AdvancedGraphCatalog.inputs(node);
        Map<String, String> outputs = AdvancedGraphCatalog.outputs(node);
        String label = AdvancedGraphCatalog.displayName(node.type());
        if(label == null || label.isBlank()) label = node.type();
        var operation = operation(node, registered, inputs, outputs);
        Map<String, String> literals = new LinkedHashMap<>();
        Map<String, String> inputLabels = labels(node.data().getCompound("InputLabels"));
        Map<String, String> outputLabels = new LinkedHashMap<>();
        Map<String, String> settings = new LinkedHashMap<>();
        if(operation == GENERIC || operation == SOURCE){
            String description = text.resolve(AdvancedGraphCatalog.summaryTranslationKey(node.type()), "");
            if(!description.isBlank()) settings.put("description", description);
        }
        List<GraphNodeMeaning.Reference> refs = new ArrayList<>();
        List<String> targets = targets(node);
        List<String> targetNames = targetNames(node);
        if(!targetNames.isEmpty()) settings.put("targetLabel", String.join(", ", targetNames));
        for(var port : inputs.entrySet()){
            inputLabels.putIfAbsent(port.getKey(), human(port.getKey()));
            CompoundTag encoded = GraphInputDefaults.configuredValue(node.data(), port.getKey());
            // Map Range uses zero for unwired numeric ports, including omitted zero-valued editor fields
            if(encoded.isEmpty() && "map_range".equals(node.type()) && "number".equals(port.getValue()))
                encoded = AdvancedGraphDocument.Value.number(0).toTag();
            if(encoded.isEmpty() && "ramp".equals(node.type()) && "number".equals(port.getValue())){
                double fallback = switch(port.getKey()){
                    case "value" -> 1;
                    case "rise", "fall" -> .1;
                    case "reset_delay" -> node.data().getInt("ResetDelay");
                    default -> 0;
                };
                encoded = AdvancedGraphDocument.Value.number(fallback).toTag();
            }
            if(!encoded.isEmpty()) literals.put(port.getKey(), literal(encoded));
        }
        for(String port : outputs.keySet()){
            String portLabel = node.data().getCompound("OutputLabels").getString(port);
            if(portLabel.isBlank()) portLabel = human(port);
            outputLabels.put(port, t("source_output", "%s from %s", portLabel, label));
        }
        if(!targets.isEmpty() && operation == SOURCE && !wired.contains(node.id() + ".target")){
            String destinations = String.join(", ", targetNames.isEmpty() ? targets : targetNames);
            outputLabels.replaceAll((port, val) -> t("target_read", "%s for %s", val, destinations));
        }
        if(inputs.containsValue("exec")){
            outputLabels.replaceAll((port, val) -> "exec".equals(outputs.get(port)) ? val
                    : t("retained", "the execution-dependent %s", val));
        }
        if(node.type().startsWith("constant_")){
            settings.put("value", "constant_boolean".equals(node.type())
                    ? Boolean.toString(node.data().getBoolean("Value")) : primitive(node.data().get("Value")));
        }
        if("createthrusters:target".equals(node.type())){
            settings.put("value", targets.isEmpty() ? t("missing_target", "an unconfigured target")
                    : String.join(", ", targetNames.isEmpty() ? targets : targetNames));
        }
        if(operation == CONTROL){
            for(String port : outputs.keySet()) outputLabels.put(port, label);
        }
        if("compare".equals(node.type()) && !literals.containsKey("operator")){
            CompoundTag operator = GraphInputDefaults.configuredValue(node.data(), "operator");
            if(!operator.isEmpty()) literals.put("operator", literal(operator));
        }
        if("pulse_if".equals(node.type())){
            literals.put("operator", AdvancedGraphCatalog.pulseIfOperator(node));
            settings.put("predicate", "compare");
        }
        String variable = node.data().getString("Variable");
        if(Set.of("variable_get", "variable_set", "event_variable_change").contains(node.type())){
            settings.put("variable", variable);
            boolean resolved = !variable.isBlank() && document.variables().containsKey(variable);
            String initial = resolved ? literal(document.variables().get(variable).toTag()) : t("unknown_initial", "unknown initial value");
            String detail = t("variable", "Variable \"%s\" has saved initial value %s; later writes may change it.", variable, initial);
            refs.add(new GraphNodeMeaning.Reference("variable", variable, resolved,
                    variableRefs.getOrDefault(variable, List.of()).stream().filter(id -> !id.equals(node.id())).sorted().toList(), detail));
            if(operation == VARIABLE_READ) outputLabels.replaceAll((port, val) -> t("variable_value", "the mutable value of variable \"%s\"", variable));
            if(operation == VARIABLE_WRITE) settings.put("action", t("write_variable", "store a value in variable \"%s\"", variable));
        }
        if(operation == EVENT){
            settings.put("trigger", trigger(node, label));
            if(AdvancedGraphNodeFactory.isPulseBehaviorInputType(node.type())){
                settings.put("inputKind", "controller_channel_input".equals(node.type())
                        && node.data().getBoolean("GraphOwnedBinding") ? "key" : "binding");
                settings.put("binding", node.data().getString("BindingLabel"));
                String pulse = node.data().getString("PulseBehavior").trim().toLowerCase(java.util.Locale.ROOT);
                settings.put("pulse", switch(pulse){
                    case "rising", "rising_edge" -> "rising";
                    case "falling", "falling_edge" -> "falling";
                    default -> "both";
                });
            }
        }
        if("event_named_controller".equals(node.type()) || "send_named_controller_event".equals(node.type())){
            String event = node.data().getString("Event");
            settings.put("event", event);
            refs.add(new GraphNodeMeaning.Reference("named event", event, !event.isBlank(),
                    eventRefs.getOrDefault(event, List.of()).stream().filter(id -> !id.equals(node.id())).sorted().toList(),
                    t("named_event", "Named event \"%s\" uses the controller-event transport; delivery is not verified.", event)));
        }
        if(operation == FUNCTION) functionReference(node, refs, settings);
        if(operation == ACTION){
            settings.put("action", switch(node.type()){
                case "set_block_data" -> t("write_data", "write the configured block properties");
                case "direct_target_output", "linker_face_output" -> t("write_signal", "set the target signal");
                case "local_redstone_output" -> t("redstone", "set the local redstone output");
                case "controller_channel_output" -> t("channel", "set the controller channel output");
                case "wireless_frequency_output" -> t("wireless", "set the wireless redstone output");
                case "send_named_controller_event" -> t("send_event", "send named controller event \"%s\"", node.data().getString("Event"));
                case "function_output" -> t("return", "return the configured function outputs");
                default -> t("perform", "perform %s", label);
            });
            if(Set.of("set_block_data", "direct_target_output", "linker_face_output").contains(node.type())){
                if(wired.contains(node.id() + ".target") || !node.data().getCompound("TargetData").isEmpty()
                        || !node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND).isEmpty()
                        || !node.data().getString("Target").isBlank()) settings.put("controlDestination", "true");
            }else if(Set.of("local_redstone_output", "controller_channel_output", "wireless_frequency_output").contains(node.type())){
                settings.put("controlDestination", "true");
            }
        }
        Set<String> effects = new LinkedHashSet<>();
        if("set_block_data".equals(node.type())){
            CompoundTag bindings = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG);
            for(String port : inputs.keySet()){
                if(wired.contains(node.id() + "." + port) || literals.containsKey(port)
                        || node.data().getCompound("ForceWriteInputs").getBoolean(port)){
                    effects.add(port);
                    List<String> bound = new ArrayList<>();
                    for(Tag raw : bindings.getList(port, Tag.TAG_COMPOUND)){
                        CompoundTag binding = (CompoundTag)raw;
                        bound.add(human(binding.getString("Port")) + " [" + bindingTargetName(node, binding.getString("Target")) + "]");
                    }
                    if(!bound.isEmpty()) inputLabels.put(port, String.join(", ", bound));
                }
            }
            // An empty write set must not imply that every exposed property is being written
            if(effects.isEmpty()) effects.add("");
        }
        if(operation == VARIABLE_WRITE) effects.add("value");
        addConfiguration(node, refs);
        GraphNodeDefinition definition = new GraphNodeDefinition(node.type(),
                registered == null ? "unknown" : registered.category(), inputs, outputs, registered != null && registered.stateful());
        return new GraphNodeMeaning(definition, label, operation, literals, inputLabels, outputLabels,
                targets, settings, effects, refs);
    }

    // Explain the active editor topology while keeping function and variable definitions in the owning document
    public static GraphSelectionAnalysis analyse(AdvancedGraphDocument document,
            GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> graph, Collection<String> selection,
            GraphExplanation.TextResolver text){
        return GraphSelectionAnalyser.analyse(graph, selection, new AdvancedGraphExplanation(document, graph, text));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private GraphNodeMeaning.Operation operation(AdvancedGraphDocument.Node node, AdvancedGraphCatalog.Definition registered,
                                                 Map<String, String> inputs, Map<String, String> outputs){
        if(registered == null) return UNKNOWN;
        String type = node.type();
        if(type.startsWith("constant_") || type.equals("createthrusters:target")) return LITERAL;
        return switch(type){
            case "reroute" -> ROUTE;
            case "add" -> ADD;
            case "subtract" -> SUBTRACT;
            case "multiply" -> MULTIPLY;
            case "divide" -> DIVIDE;
            case "compare" -> COMPARE;
            case "and" -> AND;
            case "or" -> OR;
            case "not" -> NOT;
            case "clamp" -> CLAMP;
            case "map_range" -> MAP_RANGE;
            case "ramp" -> RAMP;
            case "round" -> ROUND;
            case "floor" -> FLOOR;
            case "ceil" -> CEIL;
            case "smoothing" -> SMOOTHING;
            case "data_branch" -> SELECT;
            case "pid", "lqr_controller", "adrc", "adrc_nth_order" -> CONTROL;
            case "branch", "gate" -> BRANCH;
            case "switch" -> AdvancedGraphCatalog.switchDataMode(node) ? GENERIC : SWITCH;
            case "bounded_loop" -> LOOP;
            case "do_once", "do_n", "delay", "debounce", "timer", "sequenced_execution",
                    "parallel_execution", "exec_combine", "for_each" -> FLOW;
            case "variable_get" -> VARIABLE_READ;
            case "variable_set" -> VARIABLE_WRITE;
            case "function_call" -> FUNCTION;
            case "ship_telemetry", "get_block_data", "createthrusters:get_targets" -> SOURCE;
            case "function_input" -> outputs.containsValue("exec") ? EVENT : SOURCE;
            default -> {
                if(!inputs.containsValue("exec") && outputs.containsValue("exec")) yield EVENT;
                if(type.startsWith("event_")) yield SOURCE;
                if(inputs.containsValue("exec") && !Set.of("logic", "flow", "math", "response", "list_map").contains(registered.category())) yield ACTION;
                if(outputs.isEmpty() && !inputs.isEmpty()) yield ACTION;
                yield GENERIC;
            }
        };
    }

    private String trigger(AdvancedGraphDocument.Node node, String label){
        String named = node.data().getString("Event");
        String binding = node.data().getString("BindingLabel");
        if(binding.isBlank()) binding = node.data().getString("BindingId");
        String base = switch(node.type()){
            case "event_tick" -> t("update", "the ACC updates");
            case "event_graph_ready" -> t("ready", "the graph becomes ready");
            case "event_periodic" -> t("periodic", "the configured interval elapses");
            case "event_trigger" -> t("graph_event", "graph event \"%s\" occurs", named);
            case "event_named_controller" -> t("controller_event", "named controller event \"%s\" arrives", named);
            case "event_variable_change" -> t("variable_changed", "variable \"%s\" changes", node.data().getString("Variable"));
            case "pulse_if" -> t("update", "the ACC updates");
            case "function_input" -> t("function_entry", "the containing function receives a call");
            default -> t("trigger", "%s produces an execution pulse", label);
        };
        return binding.isBlank() ? base : base + " [" + binding + "]";
    }

    private void functionReference(AdvancedGraphDocument.Node node, List<GraphNodeMeaning.Reference> refs, Map<String, String> settings){
        String id = node.data().getString(AdvancedGraphFunctions.FUNCTION_ID);
        var function = document.function(id);
        boolean resolved = function != null && !functionStack.contains(id);
        String name = function == null ? id : function.name();
        settings.put("function", name);
        settings.put("action", t("call", "call function \"%s\"", name));
        String detail = "";
        if(resolved){
            if(depth >= 3 || function.nodes().size() > 256 || functionSummaries.size() >= 32){
                detail = t("function_bounded", "Function \"%s\" is defined with %s nodes; its body is beyond the explanation expansion limit.", name, function.nodes().size());
            }else{
                String cacheKey = depth + ":" + id;
                detail = functionSummaries.get(cacheKey);
                if(detail == null){
                    Set<String> stack = new HashSet<>(functionStack);
                    stack.add(id);
                    GraphModel<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge> body = new GraphModel<>(){
                        @Override public Collection<AdvancedGraphDocument.Node> nodes(){ return function.nodes(); }
                        @Override public Collection<AdvancedGraphDocument.Edge> edges(){ return function.edges(); }
                    };
                    var analysis = GraphSelectionAnalyser.analyse(body, function.nodes().stream().map(AdvancedGraphDocument.Node::id).toList(),
                            new AdvancedGraphExplanation(document, body, text, Set.copyOf(stack), functionSummaries, depth + 1));
                    String summary = new GraphExplanation(text).explain(analysis);
                    if(summary.length() > 1600) summary = summary.substring(0, 1600) + "…";
                    detail = t("function_body", "Saved body of \"%s\": %s", name, summary);
                    functionSummaries.put(cacheKey, detail);
                    functionSummaries.put("markdown:" + cacheKey, new GraphExplanation(text).explainMarkdown(analysis));
                }
                String markdown = functionSummaries.get("markdown:" + cacheKey);
                if(markdown != null) settings.put("functionMarkdown", markdown);
            }
        }
        refs.add(new GraphNodeMeaning.Reference("function", name, resolved, List.of(), detail));
    }

    private void addConfiguration(AdvancedGraphDocument.Node node, List<GraphNodeMeaning.Reference> refs){
        List<String> vals = new ArrayList<>();
        for(String key : List.of("Face", "Channel", "BindingLabel", "ValueType", "AngleUnit", "Direction",
                "Mode", "Operator", "PreventIntegralWindup", "CollapseInputsToMap", "CollapseOutputsToMap")){
            Tag val = node.data().get(key);
            if(val != null) vals.add(human(key) + "=" + primitive(val));
        }
        if(!vals.isEmpty()) refs.add(new GraphNodeMeaning.Reference("configuration", "", true, List.of(),
                t("configuration", "Configuration: %s.", String.join(", ", vals))));
    }

    private List<String> targets(AdvancedGraphDocument.Node node){
        List<String> res = new ArrayList<>();
        CompoundTag data = node.data();
        CompoundTag literal = GraphInputDefaults.configuredValue(data, "target");
        CompoundTag selected = GraphTargetSelection.resolveDefaultBinding(data.getCompound("TargetData"),
                literal.getString("Type"), literal.getCompound("Payload"));
        if(!selected.isEmpty()) res.add(target(selected));
        for(Tag raw : data.getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
            String val = target((CompoundTag)raw);
            if(!res.contains(val)) res.add(val);
        }
        if(res.isEmpty() && !data.getString("TargetLabel").isBlank()) res.add(data.getString("TargetLabel"));
        if(res.isEmpty() && !data.getString("Target").isBlank()) res.add(t("unresolved_target", "an unresolved saved target"));
        if(res.isEmpty() && Set.of("set_block_data", "direct_target_output", "linker_face_output").contains(node.type()))
            res.add(t("missing_target", "an unconfigured target"));
        return List.copyOf(res);
    }

    // Use saved player-facing names in the readable trace while retaining full identities in the semantic metadata
    private List<String> targetNames(AdvancedGraphDocument.Node node){
        List<String> res = new ArrayList<>();
        for(CompoundTag saved : savedTargets(node)){
            String label = targetName(saved);
            if(!res.contains(label)) res.add(label);
        }
        if(res.isEmpty() && !node.data().getString("TargetLabel").isBlank()) res.add(node.data().getString("TargetLabel"));
        return res;
    }

    // Read the saved targets shared by destination and property-binding labels
    private List<CompoundTag> savedTargets(AdvancedGraphDocument.Node node){
        CompoundTag literal = GraphInputDefaults.configuredValue(node.data(), "target");
        CompoundTag selected = GraphTargetSelection.resolveDefaultBinding(node.data().getCompound("TargetData"),
                literal.getString("Type"), literal.getCompound("Payload"));
        List<CompoundTag> saved = new ArrayList<>();
        if(!selected.isEmpty()) saved.add(selected);
        for(Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)) saved.add((CompoundTag)raw);
        return saved;
    }

    // Resolve a bound property to its saved display name without exposing its internal key
    private String bindingTargetName(AdvancedGraphDocument.Node node, String id){
        for(CompoundTag saved : savedTargets(node)){
            if(id.equals(saved.getString("NodeId"))) return targetName(saved);
        }
        if(id.equals(node.data().getString("Target")) && !node.data().getString("TargetLabel").isBlank())
            return node.data().getString("TargetLabel");
        return t("unresolved_target", "an unresolved saved target");
    }

    // Keep missing labels readable instead of falling back to serialized target IDs
    private String targetName(CompoundTag saved){
        String label = saved.getString("Label");
        return label.isBlank() ? t("unresolved_target", "an unresolved saved target") : label;
    }

    private String target(CompoundTag saved){
        ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(saved);
        if(target == null) return saved.getString("Label").isBlank()
                ? t("unresolved_target", "an unresolved saved target") : saved.getString("Label");
        List<String> details = new ArrayList<>();
        if(!target.blockId().isBlank()) details.add(target.blockId());
        if(!target.groupId().isBlank()) details.add(t("group", "group %s", target.groupId()));
        BlockPos pos = target.blockPos();
        if(pos != null) details.add(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        if(target.subLevelId() != null) details.add(t("sublevel", "sublevel %s", target.subLevelId()));
        String label = targetName(saved);
        return label + (details.isEmpty() ? "" : " (" + String.join("; ", details) + ")");
    }

    private String literal(CompoundTag encoded){
        String type = encoded.getString("Type");
        CompoundTag payload = encoded.getCompound("Payload");
        return switch(type){
            case "target" -> targetName(payload);
            case "number" -> primitive(payload.get("Value"));
            case "boolean" -> Boolean.toString(payload.getBoolean("Value"));
            case "string", "direction" -> "\"" + clean(payload.getString("Value")) + "\"";
            case "map", "list", "player" -> t("collection", "a configured %s (%s entries)", type, payload.size());
            default -> t("typed_value", "a configured %s value", type);
        };
    }

    private String primitive(Tag val){
        if(val == null) return t("unconfigured", "unconfigured");
        if(val instanceof NumericTag number){
            double num = number.getAsDouble();
            return Double.isFinite(num) && num == Math.rint(num) && Math.abs(num) < 1.0E15
                    ? Long.toString((long)num) : Double.toString(num);
        }
        return "\"" + clean(val.getAsString()) + "\"";
    }

    private String clean(String val){
        String res = val.replaceAll("[\\p{Cntrl}§]", " ");
        return res.substring(0, Math.min(180, res.length()));
    }

    private Map<String, String> labels(CompoundTag values){
        Map<String, String> res = new LinkedHashMap<>();
        values.getAllKeys().stream().sorted().forEach(key -> res.put(key, clean(values.getString(key))));
        return res;
    }

    private String human(String val){ return val.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2"); }

    private String t(String key, String fallback, Object... args){
        return text.resolve("createthrusters.advanced_controller.explain." + key, fallback, args);
    }
}

