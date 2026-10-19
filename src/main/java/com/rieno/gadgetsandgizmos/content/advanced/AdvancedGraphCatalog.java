package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphTextInputs;
import com.rieno.gadgetsandgizmos.content.ShipTargetPoint;
import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmFlightBehavior;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.math.MathGraphNodes;
import com.rieno.gadgetsandgizmos.lib.graph.GraphCounters;
import com.rieno.gadgetsandgizmos.lib.graph.GraphDataOperations;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;

// Define every ACC graph node and the ports exposed to the editor and runtime
public final class AdvancedGraphCatalog {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String DYNAMIC_CONSTRUCTOR_TAG = "DynamicConstructor";
    public static final String INPUT_LABELS_TAG = "InputLabels";
    public static final String PID_PREVENT_INTEGRAL_WINDUP_TAG = "PreventIntegralWindup";
    public static final String PID_INTEGRAL_MIN_PORT = "integral_min";
    public static final String PID_INTEGRAL_MAX_PORT = "integral_max";
    public static final String CONTROLLER_RESET_PORT = "reset";
    public static final String COUNT_DIRECTION_TAG = "Direction";
    public static final String CHAT_BROADCAST_TAG = "BroadcastNearby";
    public static final String COLLAPSE_INPUTS_TO_MAP_TAG = "CollapseInputsToMap";
    public static final String COLLAPSE_OUTPUTS_TO_MAP_TAG = "CollapseOutputsToMap";
    public static final String COLLAPSED_INPUT_MAP_PORT = "input_map";
    public static final String COLLAPSED_OUTPUT_MAP_PORT = "output_map";
    /** Maps dynamically exposed input ports to their source MAP port and key. */
    public static final String INLINE_MAP_INPUTS_TAG = "InlineMapInputs";
    /** Maps dynamically exposed output ports to their source MAP port and key. */
    public static final String INLINE_MAP_OUTPUTS_TAG = "InlineMapOutputs";
    /** Maps generated MAP ports to the retained block-data leaf port schemas. */
    public static final String DATA_PORT_GROUPS_TAG = "DataPortGroups";
    /** Stores every selected target for a multi-target Get / Set Data node. */
    public static final String DATA_TARGETS_TAG = "DataTargets";
    /** Stores the selected assembled-block face for each data target preview. */
    public static final String DATA_TARGET_FACES_TAG = "DataTargetFaces";
    /** Maps generated data ports to their target-local source ports. */
    public static final String DATA_TARGET_BINDINGS_TAG = "DataTargetBindings";
    /** Stores labelled, collapsible node-port sections. */
    public static final String PORT_SECTIONS_TAG = "PortSections";
    /** Stores collapsed labelled node-port sections. */
    public static final String COLLAPSED_PORT_SECTIONS_TAG = "CollapsedPortSections";
    /** Stores the player-selected data-target port merge state. */
    public static final String MERGE_LIKE_PORTS_TAG = "MergeLikePorts";
    public static final String INLINE_MAP_SOURCE_TAG = "Source";
    public static final String INLINE_MAP_KEY_TAG = "Key";
    public static final String SHIP_SPEED_PERCENT_TAG = "ShipSpeedPercent";
    public static final double DEFAULT_COLLISION_DETECTION_DISTANCE = 64.0D;
    public static final double DEFAULT_COLLISION_POLL_RATE = 20.0D;
    public static final double MAX_COLLISION_POLL_RATE = 20.0D;
    public static final String SWITCH_TYPE_TAG = "SwitchType";
    public static final String SWITCH_EXECUTION_TYPE = "execution";
    public static final String SWITCH_DATA_TYPE = "data";

    // Store the definition
    public record Definition(String id, String category, Map<String, String> inputs, Map<String, String> outputs,
                             boolean stateful) {
    }

    private static final GraphNodeRegistry REGISTRY = GraphApi.nodes();
    private static final Map<String, Definition> EDITOR_DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, MathGraphNodes.Node> MATH_NODES = mathNodes();
    private static final List<com.rieno.gadgetsandgizmos.lib.graph.GraphCollectionNodes.Node> COLLECTION_NODES =
            com.rieno.gadgetsandgizmos.lib.graph.GraphCollectionNodes.nodes("createthrusters");

    public static com.rieno.gadgetsandgizmos.lib.graph.GraphCollectionNodes.Node collectionNode(String type){
        return COLLECTION_NODES.stream().filter(node -> node.definition().id().equals(type)).findFirst().orElse(null);
    }


    // Let this addon own the new node ids while retaining saved rotation ids
    private static Map<String, MathGraphNodes.Node> mathNodes(){
        Map<String, MathGraphNodes.Node> nodes = new LinkedHashMap<>();
        for(MathGraphNodes.Node node : MathGraphNodes.nodes("createthrusters")) nodes.put(node.definition().id(), node);
        for(String id : List.of("quaternion_to_euler", "quaternion_to_tait_bryan", "euler_to_quaternion",
                "tait_bryan_to_quaternion", "euler_to_tait_bryan", "tait_bryan_to_euler")){
            nodes.put(id, MathGraphNodes.rotationNode(id));
        }
        return Map.copyOf(nodes);
    }

    public static MathGraphNodes.Node mathNode(String id){
        return MATH_NODES.get(id);
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        register("event_tick", "events", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec"), false);
        register("camera", "controller", GraphPortOrder.ports(
                "interact_with_hit", "exec", "target", "target", "max_ray_length", "number", "rays_per_second", "number",
                "filter", "any", "filter_type", "string", "mode", "string", "orientation_mode", "string",
                "auto_tracking", "boolean", "lock_target", "boolean"),
                GraphPortOrder.ports("ray_cast", "list", "hit", "boolean", "hit_x", "number", "hit_y", "number",
                        "hit_z", "number", "hit_details", "map", "is_sub_level", "boolean"), true);
        register("control_camera", "controller", GraphPortOrder.ports("exec", "exec", "target", "target"), GraphPortOrder.ports(), true);
        register("acc_display_camera_source", "hud", GraphPortOrder.ports(
                "target", "target", "source", "target", "name", "string"), GraphPortOrder.ports(), true);
        register("event_graph_ready", "events", GraphPortOrder.ports(),
                GraphPortOrder.ports("exec", "exec", "ready", "boolean"), false);
        register("event_periodic", "events", GraphPortOrder.ports("period", "number"), GraphPortOrder.ports("exec", "exec"), true);
        register("event_value_change", "events", GraphPortOrder.ports("value", "any"),
                GraphPortOrder.ports("exec", "exec", "value", "any"), true);
        register("event_trigger", "events", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec"), false);
        register("watch_chat", "events", GraphPortOrder.ports("start_word", "string", "players", "list", "hide_message", "boolean"),
                GraphPortOrder.ports("exec", "exec", "value", "list"), true);
        register("send_chat_message", "events", GraphPortOrder.ports("exec", "exec", "message", "string", "players", "list"),
                GraphPortOrder.ports("exec", "exec"), false);
        register("createthrusters:announce", "events", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("players", "list"), Map.entry("action", "string"),
                Map.entry("message", "string"), Map.entry("text_format", "string"), Map.entry("color", "color"),
                Map.entry("color_mode", "string"), Map.entry("bold", "string"), Map.entry("italic", "string"),
                Map.entry("underlined", "string"), Map.entry("strikethrough", "string"), Map.entry("obfuscated", "string"),
                Map.entry("fade_in", "number"), Map.entry("stay", "number"), Map.entry("fade_out", "number"),
                Map.entry("time_unit", "string")), GraphPortOrder.ports("exec", "exec"), false);
        register("event_physical_interaction", "events", GraphPortOrder.ports(), physicalInteractionOutputs(), false);
        register("event_redstone_change", "events", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "number"), false);
        register("event_channel_change", "events", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "number"), false);
        register("event_variable_change", "events", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "any"), false);
        register("event_named_controller", "events", GraphPortOrder.ports(),
                GraphPortOrder.ports("exec", "exec", "data", "any"), false);
        register("event_delta_time", "events", GraphPortOrder.ports(), GraphPortOrder.ports("delta_time", "number"), false);
        register("send_named_controller_event", "events",
                GraphPortOrder.ports("exec", "exec", "data", "any", "distance", "number"),
                GraphPortOrder.ports("exec", "exec"), false);
        register("profiler_fps", "profiler", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("profiler_mspt", "profiler", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("profiler_tps", "profiler", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("profiler_frametime", "profiler", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("constant_number", "core", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("constant_boolean", "core", GraphPortOrder.ports(), GraphPortOrder.ports("value", "boolean"), false);
        register("constant_string", "core", GraphPortOrder.ports(), GraphPortOrder.ports("value", "string"), false);
        register("constant_color", "core", GraphPortOrder.ports(), GraphPortOrder.ports("value", "color"), false);
        register("variable_get", "variables", GraphPortOrder.ports(), GraphPortOrder.ports("value", "any"), false);
        register("variable_set", "variables", GraphPortOrder.ports("exec", "exec", "type", "string", "default", "any", "value", "any"), GraphPortOrder.ports("exec", "exec", "value", "any"), true);
        register("branch", "logic", GraphPortOrder.ports("exec", "exec", "condition", "boolean"), GraphPortOrder.ports("true", "exec", "false", "exec"), false);
        register("data_branch", "logic", GraphPortOrder.ports("condition", "boolean", "true", "any", "false", "any"), GraphPortOrder.ports("value", "any"), false);
        register("switch", "logic", GraphPortOrder.ports("exec", "exec", "selector", "number"),
                GraphPortOrder.ports("default", "exec"), false);
        register("and", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("or", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("not", "logic", GraphPortOrder.ports("value", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("xor", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("nor", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("nand", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("xnor", "logic", GraphPortOrder.ports("a", "boolean", "b", "boolean"), GraphPortOrder.ports("value", "boolean"), false);
        register("sr_latch", "logic", GraphPortOrder.ports("set", "exec", "reset", "exec"), GraphPortOrder.ports("exec", "exec", "state", "boolean"), true);
        register("compare", "logic", GraphPortOrder.ports("a", "any", "operator", "string", "b", "any"), GraphPortOrder.ports("value", "boolean"), false);
        register("latch", "logic", GraphPortOrder.ports("exec", "exec", "value", "boolean"), GraphPortOrder.ports("exec", "exec", "value", "boolean"), true);
        register("edge_detector", "logic", GraphPortOrder.ports("value", "boolean"), GraphPortOrder.ports("rising", "boolean", "falling", "boolean"), true);
        register("pulse_on_change", "logic", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports("exec", "exec"), true);
        register("gate", "logic", GraphPortOrder.ports("exec", "exec", "open", "boolean"), GraphPortOrder.ports("exec", "exec"), true);
        register("pulse_if", "events", GraphPortOrder.ports("a", "any", "b", "any"), GraphPortOrder.ports("exec", "exec"), false);
        register("flip_flop", "logic", GraphPortOrder.ports("exec", "exec", "flip_on", "string"), GraphPortOrder.ports("exec", "exec", "value", "boolean"), true);
        register("do_once", "flow", GraphPortOrder.ports("exec", "exec", "reset", "boolean"), GraphPortOrder.ports("exec", "exec"), true);
        register("do_n", "flow", GraphPortOrder.ports("exec", "exec", "count", "number", "reset", "boolean"), GraphPortOrder.ports("exec", "exec", "index", "number"), true);
        register("count", "flow", GraphPortOrder.ports("exec", "exec", "step", "number", "decrement", "boolean", "reset", "boolean", "remember_count", "boolean"), GraphPortOrder.ports("exec", "exec", "value", "number"), true);
        register("bounded_loop", "flow", GraphPortOrder.ports("exec", "exec", "iterations", "number"), GraphPortOrder.ports("exec", "exec", "body", "exec", "complete", "exec", "index", "number"), true);
        register("delay", "flow", GraphPortOrder.ports("exec", "exec", "ticks", "number"), GraphPortOrder.ports("exec", "exec"), true);
        register("debounce", "flow", GraphPortOrder.ports("exec", "exec", "ticks", "number"), GraphPortOrder.ports("exec", "exec"), true);
        register("timer", "flow", GraphPortOrder.ports("start", "exec", "stop", "exec", "duration", "number"), GraphPortOrder.ports("complete", "exec", "elapsed", "number"), true);
        register("parallel_execution", "flow", GraphPortOrder.ports("exec", "exec"), GraphPortOrder.ports(), false);
        register("sequenced_execution", "flow", GraphPortOrder.ports("exec", "exec"), GraphPortOrder.ports(), false);
        register("exec_combine", "flow", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec"), false);
        register("add", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("subtract", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("multiply", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("divide", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("modulo", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("min", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("max", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("average", "math", GraphPortOrder.ports("a", "number", "b", "number"), GraphPortOrder.ports("value", "number"), false);
        register("absolute", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("lerp", "math", GraphPortOrder.ports("exec", "exec", "a", "number", "b", "number", "amount", "number"), GraphPortOrder.ports("exec", "exec", "value", "number"), true);
        register("sin", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("cos", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("-x", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("math_sqrt", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_tan", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_acos", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_asin", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_atan", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_atan2", "math", GraphPortOrder.ports("Y", "number", "X", "number"),
                GraphPortOrder.ports("Out", "number"), false);
        register("math_power", "math", GraphPortOrder.ports("Base", "number", "Exp", "number"),
                GraphPortOrder.ports("Result", "number"), false);
        register("math_exp", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_ln", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Out", "number"), false);
        register("math_log", "math", GraphPortOrder.ports("In", "number", "Base", "number"),
                GraphPortOrder.ports("Out", "number"), false);
        register("math_average", "math", GraphPortOrder.ports("In", "number", "Samples", "number"),
                GraphPortOrder.ports("Average", "number"), true);
        register("math_delta", "math", GraphPortOrder.ports("In", "number"), GraphPortOrder.ports("Delta", "number"), true);
        register("math_peak_tracker", "math", GraphPortOrder.ports("In", "number"),
                GraphPortOrder.ports("Min", "number", "Max", "number"), true);
        register("round", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("floor", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("ceil", "math", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number"), false);
        register("clamp", "math", GraphPortOrder.ports("value", "number", "min", "number", "max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("map_range", "math", GraphPortOrder.ports("value", "number", "in_min", "number", "in_max", "number", "out_min", "number", "out_max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("curve", "math",
                GraphPortOrder.ports("exec", "exec", "value", "number", "min", "number", "max", "number", "speed", "number"),
                GraphPortOrder.ports("exec", "exec", "value", "number"), true);
        register("ramp", "response", GraphPortOrder.ports("exec", "exec", "value", "number", "rise", "number", "fall", "number", "reset_delay", "number"), GraphPortOrder.ports("exec", "exec", "value", "number"), true);
        register("step_response", "response", GraphPortOrder.ports("value", "number", "step", "number"), GraphPortOrder.ports("value", "number"), true);
        register("deadzone", "response",
                GraphPortOrder.ports("value", "number", "start", "number", "resist", "number"),
                GraphPortOrder.ports("value", "number"), false);
        register("smoothing", "response", GraphPortOrder.ports("exec", "exec", "value", "number", "amount", "number"), GraphPortOrder.ports("exec", "exec", "value", "number"), true);
        register("pid", "response", GraphPortOrder.ports("target", "number", "actual", "number", "p", "number", "i", "number", "d", "number", CONTROLLER_RESET_PORT, "boolean"), GraphPortOrder.ports("value", "number"), true);
        register("lqr_controller", "response", GraphPortOrder.ports("target", "number", "actual", "number", "gain", "number",
                "feed_forward", "number", "min", "number", "max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("adrc", "response", GraphPortOrder.ports("target", "number", "actual", "number", CONTROLLER_RESET_PORT, "boolean", "delta_time", "number",
                "controller_bandwidth", "number", "observer_bandwidth", "number", "plant_gain", "number",
                "output_limit", "number"), GraphPortOrder.ports("value", "number", "disturbance", "number"), true);
        register("adrc_nth_order", "response", GraphPortOrder.entries(
                Map.entry("target", "number"),
                Map.entry("actual", "number"),
                Map.entry(CONTROLLER_RESET_PORT, "boolean"),
                Map.entry("order", "number"),
                Map.entry("delta_time", "number"),
                Map.entry("controller_bandwidth", "number"),
                Map.entry("observer_bandwidth", "number"),
                Map.entry("plant_gain", "number"),
                Map.entry("output_limit", "number")),
                GraphPortOrder.ports("value", "number", "disturbance", "number"), true);
        for(MathGraphNodes.Node math : MATH_NODES.values()){
            REGISTRY.register(math.definition());
            GraphApi.runtimes().register(math.definition().id(), math.executor());
        }
        register("vector_multiply", "math", GraphPortOrder.ports("a", "map", "b", "map"), GraphPortOrder.ports("value", "map"), false);
        register("vector_subtract", "math", GraphPortOrder.ports("a", "map", "b", "map"), GraphPortOrder.ports("value", "map"), false);
        register("vector_add", "math", GraphPortOrder.ports("a", "map", "b", "map"), GraphPortOrder.ports("value", "map"), false);
        register("vector_invert", "math", GraphPortOrder.ports("value", "map"), GraphPortOrder.ports("value", "map"), false);
        register("vector_magnitude", "math", GraphPortOrder.ports("value", "map"), GraphPortOrder.ports("value", "number"), false);
        register("vector_difference", "math", GraphPortOrder.ports("a", "map", "b", "map"), GraphPortOrder.ports("value", "map"), false);
        register("vector_distance", "math", GraphPortOrder.ports("a", "map", "b", "map"), GraphPortOrder.ports("value", "number"), false);
        register("random", "math", GraphPortOrder.ports(), GraphPortOrder.ports("value", "number"), false);
        register("random_int", "math", GraphPortOrder.ports("max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("random_float_in_range", "math", GraphPortOrder.ports("min", "number", "max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("random_int_in_range", "math", GraphPortOrder.ports("min", "number", "max", "number"), GraphPortOrder.ports("value", "number"), false);
        register("string_concat", "data", GraphPortOrder.ports("a", "string", "b", "string"), GraphPortOrder.ports("value", "string"), false);
        register("split_string", "data",
                GraphPortOrder.ports("string", "string", "delimiter", "string"), GraphPortOrder.ports("list", "list"), false);
        register("substring", "data",
                GraphPortOrder.ports("string", "string", "start_index", "number", "end_index", "number"),
                GraphPortOrder.ports("value", "string"), false);
        register("find_in_string", "data",
                GraphPortOrder.ports("string", "string", "search", "string"), GraphPortOrder.ports("index", "number"), false);
        register("str_split", "string", GraphPortOrder.ports("In", "string", "Split On", "string"),
                GraphPortOrder.ports("Out", "list"), false);
        register("str_reverse", "string", GraphPortOrder.ports("In", "string"), GraphPortOrder.ports("Out", "string"), false);
        register("str_append", "string",
                GraphPortOrder.ports("Source", "string", "Text", "string", "Separator", "string"),
                GraphPortOrder.ports("Result", "string"), false);
        register("str_prepend", "string",
                GraphPortOrder.ports("Source", "string", "Text", "string", "Separator", "string"),
                GraphPortOrder.ports("Result", "string"), false);
        register("str_trim", "string", GraphPortOrder.ports("In", "string"), GraphPortOrder.ports("Out", "string"), false);
        register("str_replace", "string",
                GraphPortOrder.ports("In", "string", "Find", "string", "Replace", "string"),
                GraphPortOrder.ports("Out", "string"), false);
        register("str_length", "string", GraphPortOrder.ports("In", "string"), GraphPortOrder.ports("Length", "number"), false);
        register("str_contains", "string", GraphPortOrder.ports("In", "string", "Search", "string"),
                GraphPortOrder.ports("Result", "boolean"), false);
        register("str_starts_with", "string", GraphPortOrder.ports("In", "string", "Prefix", "string"),
                GraphPortOrder.ports("Result", "boolean"), false);
        register("str_ends_with", "string", GraphPortOrder.ports("In", "string", "Suffix", "string"),
                GraphPortOrder.ports("Result", "boolean"), false);
        register("str_upper", "string", GraphPortOrder.ports("In", "string"), GraphPortOrder.ports("Out", "string"), false);
        register("str_lower", "string", GraphPortOrder.ports("In", "string"), GraphPortOrder.ports("Out", "string"), false);
        register("str_parse_array", "string", GraphPortOrder.ports("In", "string", "Delimiter", "string"),
                GraphPortOrder.ports("Array", "list"), false);
        register("str_join_array", "string", GraphPortOrder.ports("Array", "list", "Separator", "string"),
                GraphPortOrder.ports("String", "string"), false);
        register("str_regex", "string", GraphPortOrder.ports("In", "string", "Pattern", "string"),
                GraphPortOrder.ports("Matches", "list", "Found", "boolean"), false);
        register("convert_type", "data", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports("value", "string"), false);
        register("createthrusters:length", "data", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports("length", "number"), false);
        GraphApi.runtimes().register("createthrusters:length", (ctx, inputs) ->
                Map.of("length", GraphValue.number(GraphDataOperations.length(inputs.get("value")))));
        register("createthrusters:strip_json", "data", GraphPortOrder.ports("value", "string"), GraphPortOrder.ports("value", "string"), false);
        GraphApi.runtimes().register("createthrusters:strip_json", (ctx, inputs) ->
                Map.of("value", GraphValue.string(GraphDataOperations.stripJson(
                        inputs.getOrDefault("value", GraphValue.string("")).asString()))));
        for(var collection : COLLECTION_NODES){
            REGISTRY.register(collection.definition());
            GraphApi.runtimes().register(collection.definition().id(), collection.executor());
        }
        register("list_create", "data", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports("list", "list"), false);
        register("list_get", "data", GraphPortOrder.ports("list", "list", "index", "number"), GraphPortOrder.ports("value", "any"), false);
        register("map_create", "data", GraphPortOrder.ports("key", "string", "value", "any"), GraphPortOrder.ports("map", "map"), false);
        register("map_get", "data", GraphPortOrder.ports("map", "map", "key", "string"), GraphPortOrder.ports("value", "any"), false);
        register("arr_get", "list_map", GraphPortOrder.ports("Arr", "list", "Index", "number"),
                GraphPortOrder.ports("Item", "any"), false);
        register("arr_shuffle", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_sort", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_slice", "list_map", GraphPortOrder.ports("Arr", "list", "Start", "number", "End", "number"),
                GraphPortOrder.ports("Out Arr", "list"), false);
        register("arr_append_arr", "list_map",
                GraphPortOrder.ports("exec", "exec", "Source", "list", "Append", "list"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("collection_merge", "list_map", GraphPortOrder.ports("A", "any", "B", "any"),
                GraphPortOrder.ports("Result", "any"), false);
        register("arr_add", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list", "Item", "any"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_add_unique", "list_map",
                GraphPortOrder.ports("exec", "exec", "Arr", "list", "Item", "any"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_insert", "list_map", GraphPortOrder.entries(
                        Map.entry("exec", "exec"), Map.entry("Arr", "list"),
                        Map.entry("Item", "any"), Map.entry("Index", "number")),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_remove", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list", "Index", "number"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_clear", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_find", "list_map", GraphPortOrder.ports("Arr", "list", "Item", "any"),
                GraphPortOrder.ports("Index", "number"), false);
        register("arr_contains", "list_map", GraphPortOrder.ports("Arr", "list", "Item", "any"),
                GraphPortOrder.ports("Result", "boolean"), false);
        register("arr_length", "list_map", GraphPortOrder.ports("Arr", "list"), GraphPortOrder.ports("Length", "number"), false);
        register("arr_last_index", "list_map", GraphPortOrder.ports("Arr", "list"), GraphPortOrder.ports("Index", "number"), false);
        register("arr_sort_desc", "list_map", GraphPortOrder.ports("exec", "exec", "Arr", "list"),
                GraphPortOrder.ports("exec", "exec", "Out Arr", "list"), false);
        register("arr_filter", "list_map",
                GraphPortOrder.ports("Array", "list", "Key", "string", "Value", "any", "Operation", "string"),
                GraphPortOrder.ports("Result", "list"), false);
        register("split_list", "data", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports(), false);
        register("break_out", "data", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports(), false);
        register("get_block_data", "data", GraphPortOrder.ports("target", "target", "merge_like_ports", "boolean"),
                GraphPortOrder.ports("data", "map"), false);
        register("set_block_data", "data", GraphPortOrder.ports("exec", "exec", "target", "target",
                "merge_like_ports", "boolean", GraphSignalRange.REDSTONE_SIGNAL_PORT, "number"),
                GraphPortOrder.ports("exec", "exec", "success", "boolean"), false);
        register("hud_element", "hud", GraphPortOrder.ports("label", "string", "visible", "boolean"), GraphPortOrder.ports(), true);
        register("advanced_hud_element", "hud", GraphPortOrder.ports("label", "string", "visible", "boolean"), GraphPortOrder.ports(), true);
        register("acc_display_widget", "hud", GraphPortOrder.ports("target", "target", "visible", "boolean"), GraphPortOrder.ports(), true);
        register("acc_hologram_widget", "hud", GraphPortOrder.entries(
                Map.entry("target", "target"), Map.entry("visible", "boolean"),
                Map.entry("offset_x", "number"), Map.entry("offset_y", "number"), Map.entry("offset_z", "number"),
                Map.entry("size_x", "number"), Map.entry("size_y", "number"), Map.entry("projection_rotation", "number")), GraphPortOrder.ports(), true);
        register("acc_display_graph", "hud", GraphPortOrder.ports(
                "target", "target", "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), GraphPortOrder.ports(), true);
        register("acc_display_plotter", "hud", GraphPortOrder.ports(
                "target", "target", "value", "number", "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), GraphPortOrder.ports(), true);
        register("acc_display_external", "hud", GraphPortOrder.ports(
                "target", "target", "source", "target", "name", "string", "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), GraphPortOrder.ports(), true);
        register("acc_display_crn", "hud", GraphPortOrder.ports(
                "target", "target", "visible", "boolean", "text", "string", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), GraphPortOrder.ports(), true);
        // Kept separate from the legacy CRN node so new graphs say exactly what
        // they present while saved graphs retain their existing node id.
        register("acc_display_shipping_information", "hud", GraphPortOrder.ports(
                "target", "target", "visible", "boolean", "text", "string", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), GraphPortOrder.ports(), true);
        register("acc_display_scm_information", "hud", GraphPortOrder.entries(
                Map.entry("target", "target"), Map.entry("visible", "boolean"),
                Map.entry("show_status", "boolean"),
                Map.entry("show_ready", "boolean"),
                Map.entry("show_initialization", "boolean"),
                Map.entry("show_mass", "boolean"),
                Map.entry("show_weight", "boolean"),
                Map.entry("show_facing", "boolean"),
                Map.entry("show_center_of_mass", "boolean"),
                Map.entry("show_center_of_lift", "boolean"),
                Map.entry("show_position", "boolean"),
                Map.entry("show_velocity", "boolean"),
                Map.entry("show_speed", "boolean"),
                Map.entry("show_angular_velocity", "boolean"),
                Map.entry("show_orientation", "boolean"),
                Map.entry("show_collision", "boolean"),
                Map.entry("show_navigation", "boolean"),
                Map.entry("show_inertia", "boolean")), GraphPortOrder.ports(), true);
        register("acc_display_mode", "hud",
                GraphPortOrder.ports("exec", "exec", "target", "target", "mode", "string"),
                GraphPortOrder.ports("exec", "exec", "mode", "string", "success", "boolean"), false);
        register("validate_number", "data", GraphPortOrder.ports("value", "number"), GraphPortOrder.ports("value", "number", "valid", "boolean"), false);
        register("reroute", "core", GraphPortOrder.ports("value", "any"), GraphPortOrder.ports("value", "any"), false);
        register("sticky_note", "core", GraphPortOrder.ports(), GraphPortOrder.ports(), false);
        register("image_reference", "core", GraphPortOrder.ports(), GraphPortOrder.ports(), false);
        register(AdvancedGraphFunctions.CALL_TYPE, "functions", GraphPortOrder.ports(), GraphPortOrder.ports(), true);
        register(AdvancedGraphFunctions.INPUT_TYPE, "functions", GraphPortOrder.ports(), GraphPortOrder.ports(), false);
        register(AdvancedGraphFunctions.OUTPUT_TYPE, "functions", GraphPortOrder.ports(), GraphPortOrder.ports(), false);
        register("play_sound", "controller", GraphPortOrder.ports("exec", "exec", "stop", "exec", "sound", "string",
                "world", "boolean", "x", "number", "y", "number", "z", "number", "volume", "number",
                "pitch", "number", "loop", "boolean"),
                GraphPortOrder.ports("exec", "exec"), false);
        register("controller_channel_input", "controller", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("gamepad_input", "controller", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("mouse_input", "controller", GraphPortOrder.ports("reset_axis", "boolean", "timeout", "number"),
                GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("controller_channel_output", "controller", GraphPortOrder.ports("exec", "exec", "value", "number"), GraphPortOrder.ports("exec", "exec"), false);
        register("local_redstone_input", "controller", GraphPortOrder.ports(), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("local_redstone_output", "controller", GraphPortOrder.ports("exec", "exec", "value", "number", "face", "direction"), GraphPortOrder.ports("exec", "exec"), false);
        register("wireless_frequency_input", "controller", GraphPortOrder.ports("frequency", "frequency"), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("wireless_frequency_output", "controller", GraphPortOrder.ports("exec", "exec", "frequency", "frequency", "value", "number"), GraphPortOrder.ports("exec", "exec"), false);
        register("discovered_target_input", "controller", GraphPortOrder.ports("target", "target"), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("direct_target_output", "controller", GraphPortOrder.ports("exec", "exec", "target", "target", "value", "number"), GraphPortOrder.ports("exec", "exec"), false);
        register("desmos_plot_point", "controller",
                GraphPortOrder.ports("exec", "exec", "name", "string", "x", "number", "y", "number", "color", "color",
                        "x_functionality", "string"),
                GraphPortOrder.ports("exec", "exec"), false);
        register("desmos_reset_plotter", "controller", GraphPortOrder.ports("exec", "exec", "name", "string"),
                GraphPortOrder.ports("exec", "exec"), false);
        register("linker_face_input", "controller", GraphPortOrder.ports("target", "target", "face", "direction"), GraphPortOrder.ports("exec", "exec", "value", "number", "active", "boolean"), false);
        register("linker_face_output", "controller", GraphPortOrder.ports("exec", "exec", "target", "target", "face", "direction", "value", "number"), GraphPortOrder.ports("exec", "exec"), false);
        register("controller_tracker", "controller", GraphPortOrder.ports(), trackingOutputs(), false);
        register("portable_tracker", "controller", GraphPortOrder.ports(), gogglesTrackingOutputs(), false);
        register("ship_tracker", "controller", GraphPortOrder.ports("filter", "string", "tracking_distance", "number"),
                GraphPortOrder.ports("available", "boolean", "distance", "number", "coordinates", "map",
                        "relative_angle", "map", "ship_name", "string", "sub_level", "string",
                        "count", "number", "ships", "list"), false);
        register("reset_outputs", "controller", GraphPortOrder.ports("exec", "exec"), GraphPortOrder.ports("exec", "exec"), false);
        register("ship_scan_configuration", "ship_control", GraphPortOrder.ports(
                        "exec", "exec",
                        "ship_name", "string",
                        "control_mode", "string",
                        "display_progress", "boolean",
                        "force_full_initialization", "boolean",
                        "ignore_bearings", "boolean",
                        "ignore_sails", "boolean",
                        "ignore_thrusters", "boolean",
                        "ignore_docking_connectors", "boolean"),
                initOutputs(), true);
        register("ship_stop_initialization", "ship_control",
                GraphPortOrder.ports("exec", "exec"), commandOutputs(), false);
        register("ship_status", "ship_control", GraphPortOrder.ports(), GraphPortOrder.entries(
                Map.entry("attached", "boolean"),
                Map.entry("initialized", "boolean"),
                Map.entry("initializing", "boolean"),
                Map.entry("progress", "number"),
                Map.entry("unit_count", "number"),
                Map.entry("bearing_count", "number"),
                Map.entry("vector_thruster_count", "number"),
                Map.entry("docking_connector_count", "number"),
                Map.entry("controllable_count", "number"),
                Map.entry("mass", "number"),
                Map.entry("weight", "number"),
                Map.entry("facing", "string"),
                Map.entry("center_of_mass_x", "number"),
                Map.entry("center_of_mass_y", "number"),
                Map.entry("center_of_mass_z", "number"),
                Map.entry("center_of_lift_x", "number"),
                Map.entry("center_of_lift_y", "number"),
                Map.entry("center_of_lift_z", "number"),
                Map.entry("inertia_tensor", "map"),
                Map.entry("status", "string"),
                Map.entry("map_id", "string")), false);
        register("scm_configuration", "ship_control", GraphPortOrder.ports(), GraphPortOrder.entries(
                Map.entry("configured", "boolean"),
                Map.entry("scanning", "boolean"),
                Map.entry("candidate_count", "number"),
                Map.entry("group_count", "number"),
                Map.entry("action_binding_count", "number"),
                Map.entry("excluded_unit_count", "number"),
                Map.entry("action_groups", "map")), false);
        register("scm_brain_debug", "ship_control", GraphPortOrder.ports(), GraphPortOrder.entries(
                Map.entry("scm_brain_available", "boolean"),
                Map.entry("scm_brain_state", "string"),
                Map.entry("scm_brain_reason", "string"),
                Map.entry("scm_brain_vehicle_name", "string"),
                Map.entry("scm_brain_vehicle_id", "string"),
                Map.entry("scm_brain_game_time", "number"),
                Map.entry("scm_brain_anchor_x", "number"),
                Map.entry("scm_brain_anchor_y", "number"),
                Map.entry("scm_brain_anchor_z", "number"),
                Map.entry("scm_brain_data", "map")), false);
        register("ship_coupler_status", "ship_control", GraphPortOrder.ports(), GraphPortOrder.ports(
                "coupler_count", "number",
                "coupled_count", "number",
                "carriage_count", "number",
                "any_coupled", "boolean",
                "all_coupled", "boolean",
                "coupler_status", "string"), false);
        register("ship_flight_behavior", "ship_control",
                GraphPortOrder.ports("exec", "exec", "behavior", "string"),
                commandOutputs(), false);
        register("ship_yaw", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_yaw_right", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_yaw_left", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_pitch", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_pitch_up", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_pitch_down", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_pan", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_tilt", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_roll", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_roll_right", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_roll_left", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_accelerate", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_forward", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_reverse", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_backward", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_strafe", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_strafe_left", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_strafe_right", "ship_control", amountInputs(), targetCommandOutputs(), true);
        register("ship_ascend", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_descend", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_jump", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_crouch", "ship_control", amountInputs(), commandOutputs(), false);
        register("ship_stabilize", "ship_control",
                GraphPortOrder.ports("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_decelerate", "ship_control",
                GraphPortOrder.ports("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_hold_yaw", "ship_control",
                GraphPortOrder.ports("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_brake", "ship_control",
                GraphPortOrder.ports("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_hover", "ship_control",
                GraphPortOrder.ports("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_climb", "ship_control",
                GraphPortOrder.ports("exec", "exec", "y", "number", "speed", "number", "avoid_collisions", "boolean"),
                targetCommandOutputs(), true);
        register("ship_face", "ship_control",
                GraphPortOrder.ports("exec", "exec", "x", "number", "y", "number", "z", "number", "speed", "number"),
                targetCommandOutputs(), true);
        register("ship_dock", "ship_control",
                GraphPortOrder.ports("exec", "exec", "dock", "string", "speed", "number"),
                targetCommandOutputs(), true);
        register("ship_navigate", "ship_control", GraphPortOrder.entries(
                        Map.entry("exec", "exec"),
                        Map.entry("offset", "map"),
                        Map.entry("x", "number"),
                        Map.entry("y", "number"),
                        Map.entry("z", "number"),
                        Map.entry("speed", "number"),
                        Map.entry("tolerance", "number"),
                        Map.entry("avoid_collisions", "boolean"),
                        Map.entry("lock_rotation", "boolean"),
                        Map.entry("target_point", "string"),
                        Map.entry("target_direction_x", "number"),
                        Map.entry("target_direction_y", "number"),
                        Map.entry("target_direction_z", "number"),
                        Map.entry("target_up_x", "number"),
                        Map.entry("target_up_y", "number"),
                        Map.entry("target_up_z", "number")),
                targetCommandOutputs(), true);
        register("ship_follow", "ship_control", GraphPortOrder.entries(
                        Map.entry("exec", "exec"),
                        Map.entry("x", "number"),
                        Map.entry("y", "number"),
                        Map.entry("z", "number"),
                        Map.entry("speed", "number"),
                        Map.entry("follow_distance", "number"),
                        Map.entry("avoid_collisions", "boolean")),
                commandOutputs(), true);
        register("ship_stop", "ship_control", GraphPortOrder.ports("exec", "exec"), commandOutputs(), false);
        register("ship_couple_carriage", "ship_control",
                GraphPortOrder.ports("exec", "exec", "endpoint", "number"),
                targetCommandOutputs(), true);
        register("ship_decouple_carriage", "ship_control",
                GraphPortOrder.ports("exec", "exec", "endpoint", "number"),
                targetCommandOutputs(), true);
        register("ship_telemetry", "ship_control",
                GraphPortOrder.ports(
                        "collision_detection_distance", "number",
                        "collision_poll_rate", "number"), GraphPortOrder.entries(
                Map.entry("ready", "boolean"),
                Map.entry("x", "number"),
                Map.entry("y", "number"),
                Map.entry("z", "number"),
                Map.entry("velocity_x", "number"),
                Map.entry("velocity_y", "number"),
                Map.entry("velocity_z", "number"),
                Map.entry("speed", "number"),
                Map.entry("angular_velocity_x", "number"),
                Map.entry("angular_velocity_y", "number"),
                Map.entry("angular_velocity_z", "number"),
                Map.entry("yaw", "number"),
                Map.entry("pitch", "number"),
                Map.entry("roll", "number"),
                Map.entry("mass", "number"),
                Map.entry("weight", "number"),
                Map.entry("facing", "string"),
                Map.entry("center_of_mass_x", "number"),
                Map.entry("center_of_mass_y", "number"),
                Map.entry("center_of_mass_z", "number"),
                Map.entry("center_of_lift_x", "number"),
                Map.entry("center_of_lift_y", "number"),
                Map.entry("center_of_lift_z", "number"),
                Map.entry("inertia_tensor", "map"),
                Map.entry("nearest_collision_distance", "number"),
                Map.entry("collision_distance_forward", "number"),
                Map.entry("collision_distance_backward", "number"),
                Map.entry("collision_distance_left", "number"),
                Map.entry("collision_distance_right", "number"),
                Map.entry("collision_distance_up", "number"),
                Map.entry("collision_distance_down", "number"),
                Map.entry("collision_scan_range", "number"),
                Map.entry("navigation_target_distance", "number")), false);
        register("shipping_travel_metrics", "ship_control", GraphPortOrder.ports(), GraphPortOrder.entries(
                Map.entry("shipping_active", "boolean"),
                Map.entry("shipping_pilot_present", "boolean"),
                Map.entry("shipping_seated_players", "number"),
                Map.entry("shipping_seat_count", "number"),
                Map.entry("shipping_available_seats", "number"),
                Map.entry("shipping_docked", "boolean"),
                Map.entry("shipping_waiting", "boolean"),
                Map.entry("shipping_diverted", "boolean"),
                Map.entry("shipping_needs_refuel", "boolean"),
                Map.entry("shipping_target_has_connector", "boolean"),
                Map.entry("shipping_name", "string"),
                Map.entry("shipping_schedule_title", "string"),
                Map.entry("shipping_status", "string"),
                Map.entry("shipping_phase", "string"),
                Map.entry("shipping_current_stop", "string"),
                Map.entry("shipping_target_stop", "string"),
                Map.entry("shipping_next_stop", "string"),
                Map.entry("shipping_current_entry", "number"),
                Map.entry("shipping_next_entry", "number"),
                Map.entry("shipping_stop_count", "number"),
                Map.entry("shipping_remaining_stops", "number"),
                Map.entry("shipping_progress_percent", "number"),
                Map.entry("shipping_eta_seconds", "number"),
                Map.entry("shipping_eta_minutes", "number"),
                Map.entry("shipping_distance_to_target", "number"),
                Map.entry("shipping_throttle", "number"),
                Map.entry("shipping_cruise_speed", "number"),
                Map.entry("shipping_phase_elapsed_seconds", "number"),
                Map.entry("shipping_position_x", "number"),
                Map.entry("shipping_position_y", "number"),
                Map.entry("shipping_position_z", "number"),
                Map.entry("shipping_target_x", "number"),
                Map.entry("shipping_target_y", "number"),
                Map.entry("shipping_target_z", "number"),
                Map.entry("shipping_fuel_amount", "number"),
                Map.entry("shipping_fuel_capacity", "number"),
                Map.entry("shipping_fuel_ratio", "number"),
                Map.entry("shipping_fuel_reserve", "number"),
                Map.entry("shipping_fuel_use_per_tick", "number"),
                Map.entry("shipping_fuel_reserve_seconds", "number")), false);
        register("shipping_manifest", "ship_control", GraphPortOrder.ports(), GraphPortOrder.entries(
                Map.entry("shipping_manifest_title", "string"),
                Map.entry("shipping_manifest_current_stop", "string"),
                Map.entry("shipping_manifest_target_stop", "string"),
                Map.entry("shipping_manifest_next_stop", "string"),
                Map.entry("shipping_manifest_stop_count", "number"),
                Map.entry("shipping_manifest_current_entry", "number"),
                Map.entry("shipping_manifest_next_entry", "number"),
                Map.entry("shipping_manifest_is_cyclic", "boolean"),
                Map.entry("shipping_manifest_stops", "list")), false);
        register("shipping_start", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        register("shipping_pause", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        register("shipping_resume", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        register("shipping_stop", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        register("shipping_restart", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        register("shipping_skip", "shipping_schedule", GraphPortOrder.ports("exec", "exec"),
                commandOutputs(), false);
        // ------------------------------------WORKER GRAPH------------------------------------
        register("worker_routine", "worker", GraphPortOrder.ports("workers", "string", "loop_routine", "boolean", "idle_delay", "number"),
                GraphPortOrder.ports("start", "exec", "worker", "worker"), true);
        register("worker_task_event", "worker", GraphPortOrder.entries(
                Map.entry("task_name", "string"), Map.entry("default_priority", "number"),
                Map.entry("interrupt_policy", "string"), Map.entry("resume_previous_task", "boolean"),
                Map.entry("allow_re_entry", "boolean")), GraphPortOrder.entries(
                Map.entry("triggered", "exec"), Map.entry("worker", "worker"),
                Map.entry("requester", "entity"), Map.entry("priority", "number"),
                Map.entry("request", "task_request")), true);
        register("worker_source_container", "worker_containers", GraphPortOrder.entries(
                Map.entry("mode", "string"), Map.entry("target", "target"),
                Map.entry("preference", "string"), Map.entry("search_range", "number")),
                GraphPortOrder.ports("source", "container_selector"), false);
        register("worker_destination_container", "worker_containers", GraphPortOrder.entries(
                Map.entry("mode", "string"), Map.entry("target", "target"),
                Map.entry("preference", "string"), Map.entry("search_range", "number")),
                GraphPortOrder.ports("destination", "container_selector"), false);
        register("worker_item_filter", "worker_filters", GraphPortOrder.entries(
                Map.entry("tags", "tag_filter"), Map.entry("enabled", "boolean"),
                Map.entry("mode", "string"), Map.entry("filter_type", "string"), Map.entry("items", "string"),
                Map.entry("match_components", "boolean"), Map.entry("ignore_damage", "boolean"),
                Map.entry("match_mod", "string")), GraphPortOrder.ports("filter", "item_filter"), false);
        register("worker_fluid_filter", "worker_filters", GraphPortOrder.entries(
                Map.entry("tags", "tag_filter"), Map.entry("enabled", "boolean"),
                Map.entry("mode", "string"), Map.entry("filter_type", "string"), Map.entry("fluids", "string"),
                Map.entry("match_components", "boolean")), GraphPortOrder.ports("filter", "fluid_filter"), false);
        register("worker_tag_filter", "worker_filters", GraphPortOrder.entries(
                Map.entry("mode", "string"), Map.entry("match", "string"), Map.entry("tags", "string")),
                GraphPortOrder.ports("filter", "tag_filter"), false);
        register("worker_move_items", "worker_transfer", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("source", "container_selector"),
                Map.entry("filter", "item_filter"), Map.entry("amount", "number"),
                Map.entry("amount_mode", "string"), Map.entry("retry", "boolean"),
                Map.entry("timeout", "number")), GraphPortOrder.entries(
                Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                Map.entry("items", "item_payload"), Map.entry("moved_amount", "number"),
                Map.entry("failure_reason", "failure_reason")), true);
        register("worker_move_fluid", "worker_transfer", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("source", "container_selector"),
                Map.entry("filter", "fluid_filter"), Map.entry("amount", "number"),
                Map.entry("amount_mode", "string"), Map.entry("retry", "boolean"),
                Map.entry("timeout", "number")), GraphPortOrder.entries(
                Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                Map.entry("fluid", "fluid_payload"), Map.entry("moved_amount", "number"),
                Map.entry("failure_reason", "failure_reason")), true);
        register("worker_move_fe", "worker_transfer", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("source", "container_selector"),
                Map.entry("amount", "number"), Map.entry("amount_mode", "string"),
                Map.entry("minimum_source_reserve", "number"), Map.entry("retry", "boolean")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("fe", "fe_payload"), Map.entry("moved_amount", "number"),
                        Map.entry("failure_reason", "failure_reason")), true);
        register("worker_deposit", "worker_transfer", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("destination", "container_selector"),
                Map.entry("items", "item_payload"), Map.entry("fluid", "fluid_payload"),
                Map.entry("fe", "fe_payload"), Map.entry("amount_mode", "string"),
                Map.entry("retry", "boolean"), Map.entry("return_remainder", "boolean")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("remaining_items", "item_payload"),
                        Map.entry("remaining_fluid", "fluid_payload"),
                        Map.entry("remaining_fe", "fe_payload"),
                        Map.entry("failure_reason", "failure_reason")), true);
        register("worker_process", "worker_actions", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("processor", "target"),
                Map.entry("source", "container_selector"), Map.entry("items", "item_payload"), Map.entry("fluid", "fluid_payload"),
                Map.entry("fe", "fe_payload"), Map.entry("result_item_filter", "item_filter"),
                Map.entry("result_fluid_filter", "fluid_filter"), Map.entry("operation", "string"),
                Map.entry("count", "number"),
                Map.entry("collect_result", "boolean"), Map.entry("timeout", "number")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("items", "item_payload"), Map.entry("fluid", "fluid_payload"),
                        Map.entry("fe", "fe_payload"), Map.entry("failure_reason", "failure_reason")), true);
        register("worker_craft", "worker_actions", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("target", "target"),
                Map.entry("source", "container_selector"),
                Map.entry("items", "item_payload"),
                Map.entry("result_item_filter", "item_filter"),
                Map.entry("count", "number"), Map.entry("use_worker_inventory", "boolean")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("items", "item_payload"), Map.entry("failure_reason", "failure_reason")), true);
        register("worker_give_items", "worker_actions", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("items", "item_payload"),
                Map.entry("recipient", "entity"), Map.entry("follow_recipient", "boolean"),
                Map.entry("drop_if_full", "boolean"), Map.entry("timeout", "number")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("remaining_items", "item_payload"),
                        Map.entry("failure_reason", "failure_reason")), true);
        register("worker_move_to", "worker_actions", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("target", "target"), Map.entry("entity", "entity"),
                Map.entry("position", "position"), Map.entry("target_mode", "string"),
                Map.entry("stopping_distance", "number"), Map.entry("follow_moving_target", "boolean"),
                Map.entry("timeout", "number")), GraphPortOrder.entries(Map.entry("arrived", "exec"),
                Map.entry("failed", "exec"), Map.entry("failure_reason", "failure_reason")), true);
        register("worker_return_to_pod", "worker_actions", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"), Map.entry("pod", "target")),
                GraphPortOrder.entries(Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("failure_reason", "failure_reason")), true);
        register("request_worker_task", "worker_bridge", GraphPortOrder.entries(
                Map.entry("exec", "exec"), Map.entry("worker", "worker"),
                Map.entry("worker_group", "worker_group"), Map.entry("workers", "string"),
                Map.entry("request_type", "string"), Map.entry("destination_type", "string"),
                Map.entry("priority", "number"), Map.entry("requester", "entity"), Map.entry("task", "string"),
                Map.entry("items", "string"), Map.entry("item_amount", "number"),
                Map.entry("item_destination", "target"), Map.entry("fluids", "string"),
                Map.entry("fluid_amount", "number"), Map.entry("fluid_destination", "target"),
                Map.entry("fe_amount", "number"), Map.entry("fe_destination", "target"),
                Map.entry("destination_player", "string"), Map.entry("can_craft", "boolean"),
                Map.entry("interrupt", "string"), Map.entry("wait_for_completion", "boolean")),
                GraphPortOrder.entries(Map.entry("accepted", "exec"), Map.entry("rejected", "exec"),
                        Map.entry("complete", "exec"), Map.entry("failed", "exec"),
                        Map.entry("request", "task_request"),
                        Map.entry("failure_reason", "failure_reason")), true);
        register("cancel_worker_task", "worker_bridge", GraphPortOrder.ports("exec", "exec", "request", "task_request"),
                GraphPortOrder.ports("cancelled", "exec", "not_found", "exec"), true);
    }

    // Initialize the advanced graph catalog
    private AdvancedGraphCatalog() {
    }

    // Register the advanced graph catalog
    private static void register(String id, String category, Map<String, String> inputs, Map<String, String> outputs,
                                 boolean stateful) {
        REGISTRY.register(id, category, inputs, outputs, stateful);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the tracking outputs
    private static Map<String, String> trackingOutputs() {
        return GraphPortOrder.entries(
                Map.entry("available", "boolean"),
                Map.entry("holding_player", "boolean"),
                Map.entry("on_lectern", "boolean"),
                Map.entry("is_player", "boolean"),
                Map.entry("is_mannequin", "boolean"),
                Map.entry("is_armor_stand", "boolean"),
                Map.entry("x", "number"),
                Map.entry("y", "number"),
                Map.entry("z", "number"),
                Map.entry("dimension", "string"),
                Map.entry("sub_level", "string"),
                Map.entry("wearer_type", "string"),
                Map.entry("wearer_name", "string"),
                Map.entry("wearer_uuid", "string"),
                Map.entry("player_name", "string"),
                Map.entry("player_uuid", "string"));
    }

    // Get the goggles tracking outputs
    private static Map<String, String> gogglesTrackingOutputs() {
        Map<String, String> outputs = new LinkedHashMap<>(trackingOutputs());
        outputs.put("player_facing", "map");
        outputs.put("looking_at", "map");
        outputs.putAll(EntityTelemetryApi.ports());
        return GraphPortOrder.copy(outputs);
    }

    // Get the amount inputs
    private static Map<String, String> amountInputs() {
        return GraphPortOrder.ports("exec", "exec", "amount", "number");
    }

    // Get the command outputs
    private static Map<String, String> commandOutputs() {
        return GraphPortOrder.ports("exec", "exec", "success", "boolean");
    }

    // Get the target command outputs
    private static Map<String, String> targetCommandOutputs() {
        return GraphPortOrder.ports("exec", "exec", "success", "boolean", "complete", "exec");
    }

    // Initialize the outputs
    private static Map<String, String> initOutputs() {
        return GraphPortOrder.entries(
                Map.entry("exec", "exec"),
                Map.entry("success", "boolean"),
                Map.entry("complete", "exec"),
                Map.entry("progress", "string"),
                Map.entry("progress_percent", "number"),
                Map.entry("current_test", "string"));
    }

    // Get the advanced graph catalog value
    public static Definition get(String id) {
        if ("json_split".equals(id)) id = "split_list";
        GraphNodeDefinition definition = REGISTRY.get(id);
        return definition == null ? null : fromLibraryDefinition(definition);
    }

    // Include ports generated by defaults when the editor filters or connects a new node
    public static synchronized Definition editorDefinition(String id){
        Definition registered = get(id);
        if(registered == null) return null;
        return EDITOR_DEFINITIONS.computeIfAbsent(registered.id(), type -> {
            var node = new AdvancedGraphDocument.Node("", type, "", 0, 0,
                    AdvancedGraphNodeFactory.createDefaultData(type, null));
            return new Definition(type, registered.category(), GraphPortOrder.copy(inputs(node)),
                    GraphPortOrder.copy(outputs(node)), registered.stateful());
        });
    }

    // Get all advanced graph catalog values
    public static List<Definition> all() {
        return REGISTRY.definitions().stream()
                .filter(definition -> !"acc_display_mode".equals(definition.id())
                        && !isRetiredScmActionType(definition.id()))
                .map(AdvancedGraphCatalog::fromLibraryDefinition)
                .toList();
    }

    // Get the registry
    public static GraphNodeRegistry registry() {
        return REGISTRY;
    }

    // Create the advanced graph catalog from library definition
    private static Definition fromLibraryDefinition(GraphNodeDefinition definition) {
        return new Definition(definition.id(), definition.category(), definition.inputs(),
                definition.outputs(), definition.stateful());
    }

    // Check if this is a ship control type
    public static boolean isShipControlType(String type) {
        Definition definition = get(type);
        return definition != null && "ship_control".equals(definition.category());
    }

    // Check whether a direct, signed SCM primitive may appear in a reusable
    // graph function. Route and target commands keep their state in the root
    // graph so their completion semantics remain unambiguous.
    public static boolean isScmFunctionPrimitiveType(String type) {
        return "ship_forward".equals(type)
                || "ship_backward".equals(type)
                || "ship_reverse".equals(type)
                || "ship_ascend".equals(type)
                || "ship_descend".equals(type)
                || "ship_jump".equals(type)
                || "ship_crouch".equals(type)
                || "ship_yaw".equals(type)
                || "ship_yaw_left".equals(type)
                || "ship_yaw_right".equals(type)
                || "ship_pitch".equals(type)
                || "ship_pitch_up".equals(type)
                || "ship_pitch_down".equals(type)
                || "ship_roll".equals(type)
                || "ship_roll_left".equals(type)
                || "ship_roll_right".equals(type)
                || "ship_strafe".equals(type)
                || "ship_strafe_left".equals(type)
                || "ship_strafe_right".equals(type)
                || "ship_accelerate".equals(type)
                || "ship_hold_yaw".equals(type)
                || "ship_brake".equals(type);
    }

    // Check if this is a ship control passive type
    public static boolean isShipControlPassiveType(String type) {
        return "ship_status".equals(type) || "ship_telemetry".equals(type)
                || "ship_coupler_status".equals(type)
                || "scm_configuration".equals(type)
                || "scm_brain_debug".equals(type)
                || "shipping_travel_metrics".equals(type)
                || "shipping_manifest".equals(type);
    }

    // Check whether a public SCM node may be replaced by its configured action
    // function at a main-graph call site. Passive data readers retain their direct
    // behaviour, and function bodies retain explicit SCM nodes to avoid recursion.
    public static boolean isScmActionDispatchType(String type) {
        Definition definition = get(type);
        return definition != null && "ship_control".equals(definition.category())
                && !isShipControlPassiveType(type)
                && definition.inputs().containsKey("exec");
    }

    // These commands are preserved only so existing saved graphs and schedule
    // data remain executable. New graphs expose Forward and Backward instead.
    public static boolean isRetiredScmActionType(String type) {
        return "ship_accelerate".equals(type)
                || "ship_decelerate".equals(type)
                || "ship_reverse".equals(type);
    }

    // Check whether an SCM action is available for new action-function and
    // block-group configuration. Retired commands intentionally remain
    // dispatchable for compatibility with graphs saved before their removal.
    public static boolean isPublicScmActionDispatchType(String type) {
        return isScmActionDispatchType(type) && !isRetiredScmActionType(type);
    }

    // Get the physical interaction outputs
    private static Map<String, String> physicalInteractionOutputs() {
        return GraphPortOrder.entries(
                Map.entry("exec", "exec"),
                Map.entry("event", "string"),
                Map.entry("entered", "boolean"),
                Map.entry("exited", "boolean"),
                Map.entry("player", "map"),
                Map.entry("player_name", "string"),
                Map.entry("player_uuid", "string"),
                Map.entry("key_pressed", "string"),
                Map.entry("hand", "string"),
                Map.entry("look_direction", "map"),
                Map.entry("look_x", "number"),
                Map.entry("look_y", "number"),
                Map.entry("look_z", "number"),
                Map.entry("health", "number"),
                Map.entry("is_interacting", "boolean"),
                Map.entry("remote", "boolean"),
                Map.entry("sneaking", "boolean"),
                Map.entry("creative", "boolean"),
                Map.entry("x", "number"),
                Map.entry("y", "number"),
                Map.entry("z", "number"));
    }

    // Check if this is a ship coupler command type
    public static boolean isShipCouplerCommandType(String type) {
        return "ship_couple_carriage".equals(type)
                || "ship_decouple_carriage".equals(type);
    }

    // Check if this is a shipping schedule control type
    public static boolean isShippingScheduleControlType(String type) {
        return "shipping_start".equals(type)
                || "shipping_pause".equals(type)
                || "shipping_resume".equals(type)
                || "shipping_stop".equals(type)
                || "shipping_restart".equals(type)
                || "shipping_skip".equals(type);
    }

    // Check if this is a ship speed node
    public static boolean isShipSpeedNode(String type) {
        return "ship_climb".equals(type)
                || "ship_face".equals(type)
                || "ship_dock".equals(type)
                || "ship_navigate".equals(type)
                || "ship_follow".equals(type);
    }

    // Check if this is a ship speed port
    public static boolean isShipSpeedPort(AdvancedGraphDocument.Node node, String port) {
        return node != null && "speed".equals(port) && isShipSpeedNode(node.type());
    }

    // Create the default ship speed percent
    public static double defaultShipSpeedPercent(String type) {
        return "ship_dock".equals(type) ? 35.0D : 60.0D;
    }

    // Create the default ship speed
    public static double defaultShipSpeed(String type) {
        return defaultShipSpeedPercent(type) / 100.0D;
    }

    // Normalize the ship speed input
    public static double normalizeShipSpeedInput(
            AdvancedGraphDocument.Node node,
            String port,
            double val
    ) {
        return normalizeShipSpeedInput(node, port, val, false);
    }

    // Normalize the ship speed input
    public static double normalizeShipSpeedInput(
            AdvancedGraphDocument.Node node,
            String port,
            double val,
            boolean connected
    ) {
        if (!isShipSpeedPort(node, port)
                || !connected && !node.data().getBoolean(SHIP_SPEED_PERCENT_TAG)) {
            return val;
        }
        double percent = Double.isFinite(val)
                ? Math.max(0.0D, Math.min(100.0D, val))
                : defaultShipSpeedPercent(node.type());
        return percent / 100.0D;
    }

    // Normalize the collision detection distance
    public static double normalizeCollisionDetectionDistance(double val) {
        return Double.isFinite(val)
                ? Math.max(0.0D, val)
                : DEFAULT_COLLISION_DETECTION_DISTANCE;
    }

    // Normalize the collision poll rate
    public static double normalizeCollisionPollRate(double val) {
        return Double.isFinite(val)
                ? Math.max(0.0D, Math.min(MAX_COLLISION_POLL_RATE, val))
                : DEFAULT_COLLISION_POLL_RATE;
    }

    // Get the collision poll interval ticks
    public static long collisionPollIntervalTicks(double pollsPerSecond) {
        double normalized = normalizeCollisionPollRate(pollsPerSecond);
        if (normalized <= 0.0D) {
            return Long.MAX_VALUE;
        }
        double ticks = Math.ceil(20.0D / normalized);
        return ticks >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, (long) ticks);
    }

    // Group optional navigation alignment inputs while retaining saved leaf ports
    public static CompoundTag dataPortGroups(AdvancedGraphDocument.Node node) {
        CompoundTag groups = node == null ? new CompoundTag()
                : node.data().getCompound(DATA_PORT_GROUPS_TAG).copy();
        if (node != null && "ship_navigate".equals(node.type())) {
            CompoundTag offset = new CompoundTag();
            offset.putString("target_point", "string");
            for (String port : List.of("target_direction_x", "target_direction_y",
                    "target_direction_z", "target_up_x", "target_up_y", "target_up_z")) {
                offset.putString(port, "number");
            }
            groups.put("offset", offset);
        }
        return groups;
    }

    // Check if this is a ship control completion type
    public static boolean isShipControlCompletionType(String type) {
        return "ship_initialize".equals(type)
                || "ship_yaw".equals(type)
                || "ship_yaw_right".equals(type)
                || "ship_yaw_left".equals(type)
                || "ship_pitch".equals(type)
                || "ship_pitch_up".equals(type)
                || "ship_pitch_down".equals(type)
                || "ship_pan".equals(type)
                || "ship_tilt".equals(type)
                || "ship_roll".equals(type)
                || "ship_roll_right".equals(type)
                || "ship_roll_left".equals(type)
                || "ship_strafe".equals(type)
                || "ship_strafe_left".equals(type)
                || "ship_strafe_right".equals(type)
                || "ship_climb".equals(type)
                || "ship_face".equals(type)
                || "ship_dock".equals(type)
                || "ship_navigate".equals(type)
                || "ship_couple_carriage".equals(type)
                || "ship_decouple_carriage".equals(type);
    }

    // Get the ship target point options
    public static List<String> shipTargetPointOptions() {
        return ShipTargetPoint.serializedValues();
    }

    // Get the ship flight behavior options
    public static List<String> shipFlightBehaviorOptions() {
        return ScmFlightBehavior.ids();
    }

    // Get the ship control mode options
    public static List<String> shipControlModeOptions() {
        return ScmControlModeRegistry.serializedIds();
    }

    // Create the default ship control mode
    public static String defaultShipControlMode() {
        return "airship";
    }

    // Get the ship control mode label
    public static String shipControlModeLabel(String modeId) {
        return ScmControlModeRegistry.resolve(modeId).displayName();
    }

    // Create the default ship flight behavior
    public static String defaultShipFlightBehavior() {
        return ScmFlightBehavior.PREFER_SHIP_DIRECTION.id();
    }

    // Get the ship flight behavior translation key
    public static String shipFlightBehaviorTranslationKey(String behaviorId) {
        return "createthrusters.scm.flight_behavior."
                + ScmFlightBehavior.fromId(behaviorId).id();
    }

    // Get the summary translation key
    public static String summaryTranslationKey(String id) {
        return "createthrusters.advanced_controller.node." + id + ".summary";
    }

    // Preserve saved broadcast toggles while restricting their audience to nearby players
    public static boolean chatBroadcastNearby(AdvancedGraphDocument.Node node){
        return node.data().contains(CHAT_BROADCAST_TAG, Tag.TAG_BYTE)
                ? node.data().getBoolean(CHAT_BROADCAST_TAG) : node.data().getBoolean("BroadcastGlobally");
    }

    // Read the configured direction for one retained counter
    public static GraphCounters.Direction countDirection(AdvancedGraphDocument.Node node){
        CompoundTag defaults = node.data().getCompound("Defaults");
        return defaults.contains("decrement")
                ? (AdvancedGraphDocument.Value.fromTag(defaults.getCompound("decrement")).asBoolean()
                ? GraphCounters.Direction.DECREMENT : GraphCounters.Direction.INCREMENT)
                : GraphCounters.Direction.fromId(node.data().getString(COUNT_DIRECTION_TAG));
    }

    // Read the comparison selector without exposing it as a wire input
    public static String pulseIfOperator(AdvancedGraphDocument.Node node){
        String operator = com.rieno.gadgetsandgizmos.lib.graph.GraphComparison.normalizeOperator(
                node.data().getCompound("Defaults").getCompound("operator").getCompound("Payload").getString("Value"));
        return operator.isBlank() ? "==" : operator;
    }

    // Get the inputs
    public static Map<String, String> inputs(AdvancedGraphDocument.Node node) {
        Definition definition = get(node.type());
        CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
        if("acc_display_widget".equals(node.type()) || "acc_hologram_widget".equals(node.type())){
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.remove("label");
        }
        if("ship_dock".equals(node.type())){
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.getAllKeys().removeIf(key -> !Set.of("exec", "dock", "speed").contains(key));
        }
        if("do_n".equals(node.type())){
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.remove("reset");
            dynamicInputs.remove("count");
        }
        Map<String, String> baseInputs = definition == null ? GraphPortOrder.ports() : definition.inputs();
        if("pulse_if".equals(node.type())){
            baseInputs = new LinkedHashMap<>(baseInputs);
            baseInputs.put("b", com.rieno.gadgetsandgizmos.lib.graph.GraphComparison.operandType(
                    dynamicInputs.getString("b"), node.data().getCompound("Defaults").getCompound("a").getString("Type")));
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.remove("a");
        }
        if("flip_flop".equals(node.type())){
            dynamicInputs = dynamicInputs.copy();
            if(com.rieno.gadgetsandgizmos.lib.graph.GraphCounters.FlipMode.fromId(
                    node.data().getCompound("Defaults").getCompound("flip_on").getCompound("Payload").getString("Value"))
                    == com.rieno.gadgetsandgizmos.lib.graph.GraphCounters.FlipMode.EVERY_NTH_PULSE){
                dynamicInputs.putString("skip_pulses", "number");
            }else dynamicInputs.remove("skip_pulses");
        }
        if("camera".equals(node.type()) && "Manual".equalsIgnoreCase(
                node.data().getCompound("Defaults").getCompound("mode").getCompound("Payload").getString("Value"))){
            baseInputs = new LinkedHashMap<>(baseInputs);
            baseInputs.put("pan", "number");
            baseInputs.put("tilt", "number");
        }
        if ("switch".equals(node.type()) && switchDataMode(node)) {
            baseInputs = GraphPortOrder.ports(
                    "selector", "number",
                    "default", AdvancedGraphPortState.switchCaseType(node, "default"));
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.remove("exec");
            dynamicInputs.remove("value");
            CompoundTag legacyOutputs = node.data().getCompound("DynamicOutputs");
            for (String port : legacyOutputs.getAllKeys()) {
                if (port.startsWith("case_") && !dynamicInputs.contains(port)) {
                    dynamicInputs.putString(
                            port, AdvancedGraphPortState.switchCaseType(node, port));
                }
            }
            for (String port : dynamicInputs.getAllKeys()) {
                if (port.startsWith("case_")) {
                    dynamicInputs.putString(
                            port, AdvancedGraphPortState.switchCaseType(node, port));
                }
            }
        }
        if (isDynamicConstructor(node)) {
            baseInputs = GraphPortOrder.ports();
        }
        if (("advanced_hud_element".equals(node.type())
                || "acc_display_widget".equals(node.type())
                || "acc_hologram_widget".equals(node.type()))
                && node.data().getCompound("Defaults").contains("image")
                && !dynamicInputs.contains("image")) {
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.putString("image", "string");
        }
        if ("pid".equals(node.type())) {
            dynamicInputs = dynamicInputs.copy();
            if (node.data().getBoolean(PID_PREVENT_INTEGRAL_WINDUP_TAG)) {
                dynamicInputs.putString(PID_INTEGRAL_MIN_PORT, "number");
                dynamicInputs.putString(PID_INTEGRAL_MAX_PORT, "number");
            } else {
                dynamicInputs.remove(PID_INTEGRAL_MIN_PORT);
                dynamicInputs.remove(PID_INTEGRAL_MAX_PORT);
            }
        }
        if ("acc_display_crn".equals(node.type())
                && !isCrnStaticTextMode(node.data().getString("DisplayMode"))) {
            baseInputs = new LinkedHashMap<>(baseInputs);
            baseInputs.remove("text");
            dynamicInputs = dynamicInputs.copy();
            dynamicInputs.remove("text");
        }
        if("set_block_data".equals(node.type()) && !AdvancedGraphTargetSignals.ports(node).isEmpty()){
            baseInputs = new LinkedHashMap<>(baseInputs);
            baseInputs.remove(GraphSignalRange.REDSTONE_SIGNAL_PORT);
        }
        Map<String, String> ports = "string_concat".equals(node.type())
                ? GraphTextInputs.inputs(node.data())
                : mergePorts(baseInputs, dynamicInputs, node, false);
        if (node.data().getBoolean(COLLAPSE_INPUTS_TO_MAP_TAG)
                && ports.entrySet().stream().anyMatch(entry -> !"exec".equals(entry.getValue()))) {
            ports = new LinkedHashMap<>(ports);
            ports.put(COLLAPSED_INPUT_MAP_PORT, "map");
        }
        if("set_block_data".equals(node.type())){
            ports = com.rieno.gadgetsandgizmos.lib.graph.GraphInputVisibility.filter(
                    ports, node.data(), node.data().getCompound(DATA_TARGET_BINDINGS_TAG));
        }
        if("pulse_if".equals(node.type()) || "flip_flop".equals(node.type())){
            Map<String, String> ordered = new LinkedHashMap<>();
            for(String port : "pulse_if".equals(node.type()) ? List.of("a", "operator", "b") : List.of("exec", "flip_on", "skip_pulses")){
                if(ports.containsKey(port)) ordered.put(port, ports.get(port));
            }
            ports.forEach(ordered::putIfAbsent);
            ports = ordered;
        }
        return com.rieno.gadgetsandgizmos.lib.graph.GraphNodePresentationRegistry.orderedInputs(node.type(), ports);
    }

    // Check if this is a CRN static text mode
    public static boolean isCrnStaticTextMode(String mode) {
        return ShipInformationDisplayModes.isStaticText(mode);
    }

    // Get the outputs
    public static Map<String, String> outputs(AdvancedGraphDocument.Node node) {
        Definition definition = get(node.type());
        Map<String, String> baseOutputs = definition == null ? GraphPortOrder.ports() : definition.outputs();
        CompoundTag dynamicOutputs = node.data().getCompound("DynamicOutputs");
        if ("switch".equals(node.type())) {
            if (switchDataMode(node)) {
                baseOutputs = GraphPortOrder.ports("value", "any");
                dynamicOutputs = new CompoundTag();
            } else {
                baseOutputs = GraphPortOrder.ports("default", "exec");
                dynamicOutputs = dynamicOutputs.copy();
                for (String port : dynamicOutputs.getAllKeys()) {
                    if (port.startsWith("case_")) {
                        dynamicOutputs.putString(port, "exec");
                    }
                }
            }
        }
        Map<String, String> ports = mergePorts(baseOutputs, dynamicOutputs, node, true);
        if("bounded_loop".equals(node.type())){
            Map<String, String> ordered = new LinkedHashMap<>();
            for(String port : List.of("exec", "body", "complete", "index")) ordered.put(port, ports.get(port));
            ports = ordered;
        }
        if (node.data().getBoolean(COLLAPSE_OUTPUTS_TO_MAP_TAG)
                && ports.entrySet().stream().anyMatch(entry -> !"exec".equals(entry.getValue()))) {
            ports = new LinkedHashMap<>(ports);
            ports.put(COLLAPSED_OUTPUT_MAP_PORT, "map");
        }
        return com.rieno.gadgetsandgizmos.lib.graph.HudInteractionPorts.ordered(node.data(), ports);
    }

    // Switch the type
    public static String switchType(AdvancedGraphDocument.Node node) {
        if (node == null || !"switch".equals(node.type())) {
            return SWITCH_EXECUTION_TYPE;
        }
        String type = node.data().getString(SWITCH_TYPE_TAG).trim().toLowerCase(Locale.ROOT);
        return SWITCH_DATA_TYPE.equals(type) ? SWITCH_DATA_TYPE : SWITCH_EXECUTION_TYPE;
    }

    // Check if the node uses switch data mode
    public static boolean switchDataMode(AdvancedGraphDocument.Node node) {
        return SWITCH_DATA_TYPE.equals(switchType(node));
    }

    // Switch the output type
    public static String switchOutputType(AdvancedGraphDocument.Node node) {
        return switchDataMode(node) ? "any" : "exec";
    }

    // Merge the ports
    private static Map<String, String> mergePorts(Map<String, String> base, CompoundTag dynamic,
                                                   AdvancedGraphDocument.Node node, boolean output){
        return GraphPortOrder.merge(base, dynamic, GraphPortOrder.read(node.data(),
                output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER));
    }

    // Check if this is a constructor type
    public static boolean isConstructorType(String type) {
        return "list_create".equals(type) || "map_create".equals(type);
    }

    // Check if this is dynamic constructor
    public static boolean isDynamicConstructor(AdvancedGraphDocument.Node node) {
        return node != null && isConstructorType(node.type())
                && node.data().getBoolean(DYNAMIC_CONSTRUCTOR_TAG);
    }

    // Get the constructor input label
    public static String constructorInputLabel(AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null) {
            return "";
        }
        String label = node.data().getCompound(INPUT_LABELS_TAG).getString(port).strip();
        return label.isBlank() ? port : label;
    }

    // Check if the values are compatible
    public static boolean compatible(String from, String to) {
        if (from == null || to == null) return false;
        if ("exec".equals(from) || "exec".equals(to)) return from.equals(to);
        if (from.equals(to) || "any".equals(from) || "any".equals(to)) return true;
        if("map".equals(from) && "target".equals(to)) return true;
        if ("color".equals(to)) return "number".equals(from) || "string".equals(from);
        return "string".equals(to) && !"exec".equals(from);
    }

    // Check if the values are compatible
    public static boolean compatible(String from, AdvancedGraphDocument.Node toNode, String toPort) {
        String to = toNode == null ? null : inputs(toNode).get(toPort);
        // Keep saved execution-reset wires valid after exposing the boolean reset control
        if("exec".equals(from) && toNode != null && "do_n".equals(toNode.type())
                && "reset".equals(toPort)) return true;
        if ("set_block_data".equals(toNode == null ? "" : toNode.type())
                && from != null && to != null && !"exec".equals(from) && !"exec".equals(to)) {
            return true;
        }
        return compatible(from, to);
    }

    // Get the advanced graph catalog display name
    public static String displayName(String id) {
        MathGraphNodes.Node math = mathNode(id);
        if(math != null) return math.title();
        return switch (id) {
            // ------------------------------------EVENTS / INPUTS------------------------------------
            case "event_tick" -> "On Update";
            case "event_graph_ready" -> "On Graph Ready";
            case "event_periodic" -> "On Interval";
            case "event_value_change" -> "On Value Change";
            case "event_trigger" -> "On Graph Event";
            case "event_physical_interaction" -> "On Physical Interaction";
            case "event_redstone_change" -> "On Redstone Change";
            case "event_channel_change" -> "On Channel Change";
            case "event_variable_change" -> "On Variable Change";
            case "event_named_controller" -> "On Named Controller Event";
            case "event_delta_time" -> "Delta Time";
            case "send_named_controller_event" -> "Send Named Controller Event";
            case "watch_chat" -> "Watch Chat";
            case "send_chat_message" -> "Send Chat Message";
            case "createthrusters:announce" -> "Announce";
            case "average" -> "Average";
            case "profiler_fps" -> "FPS";
            case "profiler_mspt" -> "MSPT";
            case "profiler_tps" -> "TPS";
            case "profiler_frametime" -> "Frametime";
            case "mouse_input" -> "Mouse Input";
            // ------------------------------------VALUES / FLOW------------------------------------
            case "constant_number" -> "Number";
            case "constant_boolean" -> "Boolean";
            case "constant_string" -> "Text";
            case "constant_color" -> "Color";
            case "variable_get" -> "Get Variable";
            case "variable_set" -> "Set Variable";
            case "data_branch" -> "Select Data";
            case "switch" -> "Switch";
            case "edge_detector" -> "Edge Detector";
            case "pulse_on_change" -> "Pulse On Change";
            case "flip_flop" -> "Flip-Flop";
            case "xor" -> "XOR";
            case "nor" -> "NOR";
            case "nand" -> "NAND";
            case "xnor" -> "XNOR";
            case "sr_latch" -> "SR-Latch";
            case "sticky_note" -> "Sticky Note";
            case "image_reference" -> "Image";
            case "function_call" -> "Function";
            case "function_input" -> "Function Inputs";
            case "function_output" -> "Function Outputs";
            case "do_once" -> "Do Once";
            case "do_n" -> "Do N";
            case "count" -> "Count";
            case "bounded_loop" -> "Bounded Loop";
            case "pulse_if" -> "Pulse If";
            case "parallel_execution" -> "Parallel Execution";
            case "sequenced_execution" -> "Sequenced Execution";
            case "exec_combine" -> "Exec Combine";
            // ------------------------------------MATH / RESPONSE------------------------------------
            case "map_range" -> "Map Range";
            case "math_sqrt" -> "SQRT";
            case "math_tan" -> "Tan";
            case "math_acos" -> "ACos";
            case "math_asin" -> "ASin";
            case "math_atan" -> "Atan";
            case "math_atan2" -> "Atan2";
            case "math_power" -> "Power";
            case "math_exp" -> "Exp";
            case "math_ln" -> "LN";
            case "math_log" -> "Log";
            case "math_average" -> "Moving Average";
            case "math_delta" -> "Value Delta";
            case "math_peak_tracker" -> "Peak";
            case "random" -> "Random Float";
            case "random_int" -> "Random Int";
            case "random_float_in_range" -> "Random Float in Range";
            case "random_int_in_range" -> "Random Int in Range";
            case "step_response" -> "Step Response";
            // ------------------------------------------------TEXT / COLLECTIONS-------------------------------------------------
            case "string_concat" -> "Join Text";
            case "split_string" -> "Split String";
            case "substring" -> "Substring";
            case "find_in_string" -> "Find in String";
            case "str_split" -> "Split";
            case "str_reverse" -> "Reverse";
            case "str_append" -> "Append";
            case "str_prepend" -> "Prepend";
            case "str_trim" -> "Trim";
            case "str_replace" -> "Replace";
            case "str_length" -> "Length";
            case "str_contains" -> "Contains";
            case "str_starts_with" -> "Starts With";
            case "str_ends_with" -> "Ends With";
            case "str_upper" -> "To Upper";
            case "str_lower" -> "To Lower";
            case "str_parse_array" -> "Parse Into List";
            case "str_join_array" -> "Join From List";
            case "str_regex" -> "Regex";
            case "convert_type" -> "Convert Type";
            case "createthrusters:length" -> "Length";
            case "createthrusters:collection_push" -> "Push (Map/List)";
            case "createthrusters:collection_pop" -> "Pop (Map/List)";
            case "createthrusters:collection_contains" -> "Contains (Map/List)";
            case "createthrusters:collection_index_of" -> "Index Of (Map/List)";
            case "createthrusters:strip_json" -> "Strip JSON";
            case "list_create" -> "Make List";
            case "list_get" -> "Get List Value";
            case "map_create" -> "Create Structure";
            case "map_get" -> "Get Structure Value";
            case "arr_get" -> "Get Index";
            case "arr_shuffle" -> "Shuffle";
            case "arr_sort" -> "Sort";
            case "arr_slice" -> "Slice";
            case "arr_append_arr" -> "Append";
            case "collection_merge" -> "Merge";
            case "arr_add" -> "Add";
            case "arr_add_unique" -> "Add Unique";
            case "arr_insert" -> "Insert";
            case "arr_remove" -> "Remove Index";
            case "arr_clear" -> "Clear";
            case "arr_find" -> "Find";
            case "arr_contains" -> "Contains";
            case "arr_length" -> "Length";
            case "arr_last_index" -> "Last Index";
            case "arr_sort_desc" -> "Sort Descending";
            case "arr_filter" -> "Filter";
            case "split_list", "json_split" -> "Split List";
            case "break_out" -> "Break Out";
            // ------------------------------------DISPLAY / IO------------------------------------
            case "get_block_data" -> "Get Data";
            case "set_block_data" -> "Set Data";
            case "hud_element" -> "HUD Element";
            case "advanced_hud_element" -> "Advanced HUD Element";
            case "acc_display_widget" -> "ACC Display Widget";
            case "acc_hologram_widget" -> "ACC Hologram Widget";
            case "acc_display_graph" -> "ACC Display Node Graph";
            case "acc_display_plotter" -> "ACC Display Function Plotter";
            case "acc_display_external" -> "ACC Display External Source";
            case "camera" -> "Camera";
            case "control_camera" -> "Control Camera";
            case "acc_display_camera_source" -> "ACC Display Camera Source";
            case "acc_display_crn" -> "ACC Display Ship Information";
            case "acc_display_shipping_information" -> "ACC Display Shipping Information";
            case "acc_display_scm_information" -> "ACC Display SCM Information";
            case "acc_display_mode" -> "Set Display Mode";
            case "validate_number" -> "Validate Number";
            case "play_sound" -> "Play Sound";
            case "controller_channel_input" -> "Configured Key Input";
            case "gamepad_input" -> "Gamepad Input";
            case "controller_channel_output" -> "Configured Key Output";
            case "local_redstone_input" -> "Local Redstone Input";
            case "local_redstone_output" -> "Local Redstone Output";
            case "wireless_frequency_input" -> "Redstone Link Input";
            case "wireless_frequency_output" -> "Redstone Link Output";
            case "discovered_target_input" -> "Discovered Target Input";
            case "direct_target_output" -> "Direct Target Output";
            case "desmos_plot_point" -> "Desmos: Plot Point";
            case "desmos_reset_plotter" -> "Desmos: Reset Plotter";
            case "linker_face_input" -> "Linker Face Input";
            case "linker_face_output" -> "Linker Face Output";
            case "controller_tracker" -> "Controller Tracker";
            case "portable_tracker" -> "Goggles Tracker";
            case "ship_tracker" -> "Ship Tracker";
            case "reset_outputs" -> "Reset Outputs";
            // ------------------------------------SHIP / SHIPPING------------------------------------
            case "ship_initialize" -> "Initialize Control Module";
            case "ship_stop_initialization" -> "Stop Initialization";
            case "ship_status" -> "Control Module Status";
            case "scm_brain_debug" -> "SCM Brain Debug";
            case "ship_flight_behavior" -> "Set Flight Behavior";
            case "ship_yaw" -> "Yaw";
            case "ship_yaw_right" -> "Yaw Right";
            case "ship_yaw_left" -> "Yaw Left";
            case "ship_pitch" -> "Pitch";
            case "ship_pitch_up" -> "Pitch Up";
            case "ship_pitch_down" -> "Pitch Down";
            case "ship_pan" -> "Pan";
            case "ship_tilt" -> "Tilt";
            case "ship_roll" -> "Roll";
            case "ship_roll_right" -> "Roll Right";
            case "ship_roll_left" -> "Roll Left";
            case "ship_accelerate" -> "Accelerate";
            case "ship_forward" -> "Forward";
            case "ship_reverse" -> "Reverse (Legacy)";
            case "ship_backward" -> "Backward";
            case "ship_strafe" -> "Strafe";
            case "ship_strafe_left" -> "Strafe Left";
            case "ship_strafe_right" -> "Strafe Right";
            case "ship_ascend" -> "Ascend";
            case "ship_descend" -> "Descend";
            case "ship_jump" -> "Jump";
            case "ship_crouch" -> "Crouch";
            case "ship_stabilize" -> "Stabilize";
            case "ship_decelerate" -> "Decelerate";
            case "ship_hold_yaw" -> "Hold yaw";
            case "ship_brake" -> "Brake";
            case "ship_hover" -> "Hover";
            case "ship_climb" -> "Climb to Y Level";
            case "ship_face" -> "Face Coordinates";
            case "ship_dock" -> "Dock at";
            case "ship_navigate" -> "Navigate";
            case "ship_follow" -> "Follow";
            case "ship_stop" -> "Stop Ship Control";
            case "ship_couple_carriage" -> "Couple Carriage";
            case "ship_decouple_carriage" -> "Decouple Carriage";
            case "ship_coupler_status" -> "Ship Coupler Status";
            case "ship_telemetry" -> "Ship Telemetry";
            case "shipping_travel_metrics" -> "Shipping Travel Metrics";
            case "shipping_manifest" -> "Shipping Manifest";
            case "shipping_pause" -> "Pause";
            case "shipping_resume" -> "Resume";
            case "shipping_restart" -> "Restart";
            case "shipping_skip" -> "Skip";
            // ------------------------------------WORKER GRAPH------------------------------------
            case "worker_routine" -> "Worker Routine";
            case "worker_task_event" -> "Worker Task Event";
            case "worker_source_container" -> "Source Container";
            case "worker_destination_container" -> "Destination Container";
            case "worker_item_filter" -> "Item Filter";
            case "worker_fluid_filter" -> "Fluid Filter";
            case "worker_tag_filter" -> "Tag Filter";
            case "worker_move_items" -> "Move Items";
            case "worker_move_fluid" -> "Move Fluid";
            case "worker_move_fe" -> "Move FE";
            case "worker_deposit" -> "Deposit";
            case "worker_process" -> "Process";
            case "worker_craft" -> "Craft";
            case "worker_give_items" -> "Give Items";
            case "worker_move_to" -> "Move To";
            case "worker_return_to_pod" -> "Return to Pod";
            case "request_worker_task" -> "Worker Request";
            case "cancel_worker_task" -> "Cancel Worker Task";
            case "adrc_nth_order" -> "ADRC Nth Order";
            default -> id.startsWith("createthrusters:flight_control_")
                    ? titleCase(id.substring("createthrusters:flight_control_".length())) : titleCase(id);
        };
    }

    // Get the category name
    public static String categoryName(String category) {
        return switch (category) {
            case "core" -> "Core";
            case "events" -> "Events";
            case "profiler" -> "Game Profiler";
            case "variables" -> "Variables";
            case "logic" -> "Flow & Logic";
            case "flow" -> "Execution";
            case "math" -> "Math";
            case "response" -> "Curves & Response";
            case "data" -> "Data";
            case "string" -> "String";
            case "list_map" -> "List / Map";
            case "controller" -> "Controller I/O";
            case "hud" -> "HUD";
            case "functions" -> "Functions";
            case "ship_control" -> "Ship Control";
            case "flight_control" -> "Flight Control";
            case "shipping_schedule" -> "Shipping Schedule";
            case "worker" -> "Worker";
            case "worker_containers" -> "Containers";
            case "worker_filters" -> "Filters";
            case "worker_transfer" -> "Transfer";
            case "worker_actions" -> "Actions";
            case "worker_bridge" -> "Worker";
            default -> titleCase(category);
        };
    }

    // Get the category color
    public static int categoryColor(String category) {
        return switch (category) {
            case "events", "profiler" -> 0xFFE45B67;
            case "core" -> 0xFF9099A5;
            case "variables" -> 0xFFB987E8;
            case "logic" -> 0xFFE09B53;
            case "flow" -> 0xFFF0C75E;
            case "math" -> 0xFF59C58B;
            case "response" -> 0xFF44B8B0;
            case "data" -> 0xFF5D9FE3;
            case "string" -> 0xFF6BAED6;
            case "list_map" -> 0xFF7A8EE8;
            case "controller" -> 0xFF4BBCE8;
            case "hud" -> 0xFFF4D35E;
            case "functions" -> 0xFF8F73D8;
            case "ship_control", "flight_control" -> 0xFF4FC3C8;
            case "shipping_schedule" -> 0xFFDB8C4B;
            case "worker" -> 0xFFF0C75E;
            case "worker_containers" -> 0xFFBC8A55;
            case "worker_filters" -> 0xFF5D9FE3;
            case "worker_transfer" -> 0xFF59C58B;
            case "worker_actions" -> 0xFFA57AE7;
            case "worker_bridge" -> 0xFFF0C75E;
            default -> 0xFF7D8A99;
        };
    }

    // Get the title case
    private static String titleCase(String val) {
        String[] words = val.toLowerCase(Locale.ROOT).split("_");
        StringBuilder res = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!res.isEmpty()) res.append(' ');
            res.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return res.toString();
    }
}
