package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShipTargetPoint;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmFlightBehavior;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Comparator;

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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        register("event_tick", "events", Map.of(), Map.of("exec", "exec"), false);
        register("event_graph_ready", "events", Map.of(),
                Map.of("exec", "exec", "ready", "boolean"), false);
        register("event_periodic", "events", Map.of("period", "number"), Map.of("exec", "exec"), true);
        register("event_value_change", "events", Map.of("value", "any"),
                Map.of("exec", "exec", "value", "any"), true);
        register("event_trigger", "events", Map.of(), Map.of("exec", "exec"), false);
        register("event_physical_interaction", "events", Map.of(), physicalInteractionOutputs(), false);
        register("event_redstone_change", "events", Map.of(), Map.of("exec", "exec", "value", "number"), false);
        register("event_channel_change", "events", Map.of(), Map.of("exec", "exec", "value", "number"), false);
        register("event_variable_change", "events", Map.of(), Map.of("exec", "exec", "value", "any"), false);
        register("event_named_controller", "events", Map.of(),
                Map.of("exec", "exec", "data", "any"), false);
        register("event_delta_time", "events", Map.of(), Map.of("delta_time", "number"), false);
        register("send_named_controller_event", "events",
                Map.of("exec", "exec", "data", "any", "distance", "number"),
                Map.of("exec", "exec"), false);
        register("profiler_fps", "profiler", Map.of(), Map.of("value", "number"), false);
        register("profiler_mspt", "profiler", Map.of(), Map.of("value", "number"), false);
        register("profiler_tps", "profiler", Map.of(), Map.of("value", "number"), false);
        register("profiler_frametime", "profiler", Map.of(), Map.of("value", "number"), false);
        register("constant_number", "core", Map.of(), Map.of("value", "number"), false);
        register("constant_boolean", "core", Map.of(), Map.of("value", "boolean"), false);
        register("constant_string", "core", Map.of(), Map.of("value", "string"), false);
        register("variable_get", "variables", Map.of(), Map.of("value", "any"), false);
        register("variable_set", "variables", Map.of("exec", "exec", "type", "string", "default", "any", "value", "any"), Map.of("exec", "exec", "value", "any"), true);
        register("branch", "logic", Map.of("exec", "exec", "condition", "boolean"), Map.of("true", "exec", "false", "exec"), false);
        register("data_branch", "logic", Map.of("condition", "boolean", "true", "any", "false", "any"), Map.of("value", "any"), false);
        register("switch", "logic", Map.of("exec", "exec", "selector", "number"),
                Map.of("default", "exec"), false);
        register("and", "logic", Map.of("a", "boolean", "b", "boolean"), Map.of("value", "boolean"), false);
        register("or", "logic", Map.of("a", "boolean", "b", "boolean"), Map.of("value", "boolean"), false);
        register("not", "logic", Map.of("value", "boolean"), Map.of("value", "boolean"), false);
        register("compare", "logic", Map.of("a", "any", "b", "any", "operator", "string"), Map.of("value", "boolean"), false);
        register("latch", "logic", Map.of("exec", "exec", "value", "boolean"), Map.of("exec", "exec", "value", "boolean"), true);
        register("edge_detector", "logic", Map.of("value", "boolean"), Map.of("rising", "boolean", "falling", "boolean"), true);
        register("pulse_on_change", "logic", Map.of("value", "any"), Map.of("exec", "exec"), true);
        register("gate", "logic", Map.of("exec", "exec", "open", "boolean"), Map.of("exec", "exec"), true);
        register("flip_flop", "logic", Map.of("exec", "exec"), Map.of("exec", "exec", "value", "boolean"), true);
        register("do_once", "flow", Map.of("exec", "exec", "reset", "boolean"), Map.of("exec", "exec"), true);
        register("do_n", "flow", Map.of("exec", "exec", "count", "number", "reset", "exec"), Map.of("exec", "exec", "index", "number"), true);
        register("bounded_loop", "flow", Map.of("exec", "exec", "iterations", "number"), Map.of("body", "exec", "complete", "exec", "index", "number"), true);
        register("delay", "flow", Map.of("exec", "exec", "ticks", "number"), Map.of("exec", "exec"), true);
        register("debounce", "flow", Map.of("exec", "exec", "ticks", "number"), Map.of("exec", "exec"), true);
        register("timer", "flow", Map.of("start", "exec", "stop", "exec", "duration", "number"), Map.of("complete", "exec", "elapsed", "number"), true);
        register("parallel_execution", "flow", Map.of("exec", "exec"), Map.of(), false);
        register("sequenced_execution", "flow", Map.of("exec", "exec"), Map.of(), false);
        register("exec_combine", "flow", Map.of(), Map.of("exec", "exec"), false);
        register("add", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("subtract", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("multiply", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("divide", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("modulo", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("min", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("max", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("average", "math", Map.of("a", "number", "b", "number"), Map.of("value", "number"), false);
        register("absolute", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("lerp", "math", Map.of("exec", "exec", "a", "number", "b", "number", "amount", "number"), Map.of("exec", "exec", "value", "number"), true);
        register("sin", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("cos", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("-x", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("math_sqrt", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_tan", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_acos", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_asin", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_atan", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_atan2", "math", Map.of("Y", "number", "X", "number"),
                Map.of("Out", "number"), false);
        register("math_power", "math", Map.of("Base", "number", "Exp", "number"),
                Map.of("Result", "number"), false);
        register("math_exp", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_ln", "math", Map.of("In", "number"), Map.of("Out", "number"), false);
        register("math_log", "math", Map.of("In", "number", "Base", "number"),
                Map.of("Out", "number"), false);
        register("math_average", "math", Map.of("In", "number", "Samples", "number"),
                Map.of("Average", "number"), true);
        register("math_delta", "math", Map.of("In", "number"), Map.of("Delta", "number"), true);
        register("math_peak_tracker", "math", Map.of("In", "number"),
                Map.of("Min", "number", "Max", "number"), true);
        register("round", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("floor", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("ceil", "math", Map.of("value", "number"), Map.of("value", "number"), false);
        register("clamp", "math", Map.of("value", "number", "min", "number", "max", "number"), Map.of("value", "number"), false);
        register("map_range", "math", Map.of("value", "number", "in_min", "number", "in_max", "number", "out_min", "number", "out_max", "number"), Map.of("value", "number"), false);
        register("curve", "math",
                Map.of("exec", "exec", "value", "number", "min", "number", "max", "number", "speed", "number"),
                Map.of("exec", "exec", "value", "number"), true);
        register("ramp", "response", Map.of("exec", "exec", "value", "number", "rise", "number", "fall", "number", "reset_delay", "number"), Map.of("exec", "exec", "value", "number"), true);
        register("step_response", "response", Map.of("value", "number", "step", "number"), Map.of("value", "number"), true);
        register("deadzone", "response",
                Map.of("value", "number", "start", "number", "resist", "number"),
                Map.of("value", "number"), false);
        register("smoothing", "response", Map.of("exec", "exec", "value", "number", "amount", "number"), Map.of("exec", "exec", "value", "number"), true);
        register("pid", "response", Map.of("target", "number", "actual", "number", "p", "number", "i", "number", "d", "number", CONTROLLER_RESET_PORT, "boolean"), Map.of("value", "number"), true);
        register("lqr_controller", "response", Map.of("target", "number", "actual", "number", "gain", "number",
                "feed_forward", "number", "min", "number", "max", "number"), Map.of("value", "number"), false);
        register("adrc", "response", Map.of("target", "number", "actual", "number", CONTROLLER_RESET_PORT, "boolean", "delta_time", "number",
                "controller_bandwidth", "number", "observer_bandwidth", "number", "plant_gain", "number",
                "output_limit", "number"), Map.of("value", "number", "disturbance", "number"), true);
        register("adrc_nth_order", "response", Map.ofEntries(
                Map.entry("target", "number"),
                Map.entry("actual", "number"),
                Map.entry(CONTROLLER_RESET_PORT, "boolean"),
                Map.entry("order", "number"),
                Map.entry("delta_time", "number"),
                Map.entry("controller_bandwidth", "number"),
                Map.entry("observer_bandwidth", "number"),
                Map.entry("plant_gain", "number"),
                Map.entry("output_limit", "number")),
                Map.of("value", "number", "disturbance", "number"), true);
        register("vector_multiply", "math", Map.of("a", "map", "b", "map"), Map.of("value", "map"), false);
        register("vector_subtract", "math", Map.of("a", "map", "b", "map"), Map.of("value", "map"), false);
        register("vector_add", "math", Map.of("a", "map", "b", "map"), Map.of("value", "map"), false);
        register("vector_invert", "math", Map.of("value", "map"), Map.of("value", "map"), false);
        register("vector_magnitude", "math", Map.of("value", "map"), Map.of("value", "number"), false);
        register("vector_difference", "math", Map.of("a", "map", "b", "map"), Map.of("value", "map"), false);
        register("vector_distance", "math", Map.of("a", "map", "b", "map"), Map.of("value", "number"), false);
        register("quaternion_to_euler", "math", Map.of("quaternion", "map"), Map.of("euler", "map"), false);
        register("quaternion_to_tait_bryan", "math", Map.of("quaternion", "map"), Map.of("tait_bryan", "map"), false);
        register("euler_to_quaternion", "math", Map.of("euler", "map"), Map.of("quaternion", "map"), false);
        register("tait_bryan_to_quaternion", "math", Map.of("tait_bryan", "map"), Map.of("quaternion", "map"), false);
        register("euler_to_tait_bryan", "math", Map.of("euler", "map"), Map.of("tait_bryan", "map"), false);
        register("tait_bryan_to_euler", "math", Map.of("tait_bryan", "map"), Map.of("euler", "map"), false);
        register("random", "math", Map.of(), Map.of("value", "number"), false);
        register("random_int", "math", Map.of("max", "number"), Map.of("value", "number"), false);
        register("random_float_in_range", "math", Map.of("min", "number", "max", "number"), Map.of("value", "number"), false);
        register("random_int_in_range", "math", Map.of("min", "number", "max", "number"), Map.of("value", "number"), false);
        register("string_concat", "data", Map.of("a", "string", "b", "string"), Map.of("value", "string"), false);
        register("split_string", "data",
                Map.of("string", "string", "delimiter", "string"), Map.of("list", "list"), false);
        register("substring", "data",
                Map.of("string", "string", "start_index", "number", "end_index", "number"),
                Map.of("value", "string"), false);
        register("find_in_string", "data",
                Map.of("string", "string", "search", "string"), Map.of("index", "number"), false);
        register("str_split", "string", Map.of("In", "string", "Split On", "string"),
                Map.of("Out", "list"), false);
        register("str_reverse", "string", Map.of("In", "string"), Map.of("Out", "string"), false);
        register("str_append", "string",
                Map.of("Source", "string", "Text", "string", "Separator", "string"),
                Map.of("Result", "string"), false);
        register("str_prepend", "string",
                Map.of("Source", "string", "Text", "string", "Separator", "string"),
                Map.of("Result", "string"), false);
        register("str_trim", "string", Map.of("In", "string"), Map.of("Out", "string"), false);
        register("str_replace", "string",
                Map.of("In", "string", "Find", "string", "Replace", "string"),
                Map.of("Out", "string"), false);
        register("str_length", "string", Map.of("In", "string"), Map.of("Length", "number"), false);
        register("str_contains", "string", Map.of("In", "string", "Search", "string"),
                Map.of("Result", "boolean"), false);
        register("str_starts_with", "string", Map.of("In", "string", "Prefix", "string"),
                Map.of("Result", "boolean"), false);
        register("str_ends_with", "string", Map.of("In", "string", "Suffix", "string"),
                Map.of("Result", "boolean"), false);
        register("str_upper", "string", Map.of("In", "string"), Map.of("Out", "string"), false);
        register("str_lower", "string", Map.of("In", "string"), Map.of("Out", "string"), false);
        register("str_parse_array", "string", Map.of("In", "string", "Delimiter", "string"),
                Map.of("Array", "list"), false);
        register("str_join_array", "string", Map.of("Array", "list", "Separator", "string"),
                Map.of("String", "string"), false);
        register("str_regex", "string", Map.of("In", "string", "Pattern", "string"),
                Map.of("Matches", "list", "Found", "boolean"), false);
        register("convert_type", "data", Map.of("value", "any"), Map.of("value", "string"), false);
        register("list_create", "data", Map.of("value", "any"), Map.of("list", "list"), false);
        register("list_get", "data", Map.of("list", "list", "index", "number"), Map.of("value", "any"), false);
        register("map_create", "data", Map.of("key", "string", "value", "any"), Map.of("map", "map"), false);
        register("map_get", "data", Map.of("map", "map", "key", "string"), Map.of("value", "any"), false);
        register("arr_get", "list_map", Map.of("Arr", "list", "Index", "number"),
                Map.of("Item", "any"), false);
        register("arr_shuffle", "list_map", Map.of("exec", "exec", "Arr", "list"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_sort", "list_map", Map.of("exec", "exec", "Arr", "list"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_slice", "list_map", Map.of("Arr", "list", "Start", "number", "End", "number"),
                Map.of("Out Arr", "list"), false);
        register("arr_append_arr", "list_map",
                Map.of("exec", "exec", "Source", "list", "Append", "list"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("collection_merge", "list_map", Map.of("A", "any", "B", "any"),
                Map.of("Result", "any"), false);
        register("arr_add", "list_map", Map.of("exec", "exec", "Arr", "list", "Item", "any"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_add_unique", "list_map",
                Map.of("exec", "exec", "Arr", "list", "Item", "any"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_insert", "list_map", Map.ofEntries(
                        Map.entry("exec", "exec"), Map.entry("Arr", "list"),
                        Map.entry("Item", "any"), Map.entry("Index", "number")),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_remove", "list_map", Map.of("exec", "exec", "Arr", "list", "Index", "number"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_clear", "list_map", Map.of("exec", "exec", "Arr", "list"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_find", "list_map", Map.of("Arr", "list", "Item", "any"),
                Map.of("Index", "number"), false);
        register("arr_contains", "list_map", Map.of("Arr", "list", "Item", "any"),
                Map.of("Result", "boolean"), false);
        register("arr_length", "list_map", Map.of("Arr", "list"), Map.of("Length", "number"), false);
        register("arr_last_index", "list_map", Map.of("Arr", "list"), Map.of("Index", "number"), false);
        register("arr_sort_desc", "list_map", Map.of("exec", "exec", "Arr", "list"),
                Map.of("exec", "exec", "Out Arr", "list"), false);
        register("arr_filter", "list_map",
                Map.of("Array", "list", "Key", "string", "Value", "any", "Operation", "string"),
                Map.of("Result", "list"), false);
        register("split_list", "data", Map.of("value", "any"), Map.of(), false);
        register("break_out", "data", Map.of("value", "any"), Map.of(), false);
        register("get_block_data", "data", Map.of("target", "target"), Map.of("data", "map"), false);
        register("set_block_data", "data", Map.of("exec", "exec", "target", "target"), Map.of("exec", "exec", "success", "boolean"), false);
        register("hud_element", "hud", Map.of("label", "string", "visible", "boolean"), Map.of(), true);
        register("advanced_hud_element", "hud", Map.of("label", "string", "visible", "boolean"), Map.of(), true);
        register("acc_display_widget", "hud", Map.ofEntries(
                Map.entry("label", "string"), Map.entry("value", "any"),
                Map.entry("visible", "boolean"), Map.entry("x", "number"),
                Map.entry("y", "number"), Map.entry("width", "number"),
                Map.entry("height", "number"), Map.entry("rotation", "number"),
                Map.entry("scale", "number"), Map.entry("font_size", "number"),
                Map.entry("color", "any"), Map.entry("background_color", "any"),
                Map.entry("accent_color", "any"), Map.entry("track_color", "any"),
                Map.entry("border_color", "any"), Map.entry("border_width", "number"),
                Map.entry("border_radius", "number"), Map.entry("minimum", "number"),
                Map.entry("maximum", "number")), Map.of(), true);
        register("acc_hologram_widget", "hud", Map.ofEntries(
                Map.entry("label", "string"), Map.entry("value", "any"),
                Map.entry("visible", "boolean"), Map.entry("x", "number"),
                Map.entry("y", "number"), Map.entry("width", "number"),
                Map.entry("height", "number"), Map.entry("rotation", "number"),
                Map.entry("scale", "number"), Map.entry("font_size", "number"),
                Map.entry("color", "any"), Map.entry("background_color", "any"),
                Map.entry("accent_color", "any"), Map.entry("track_color", "any"),
                Map.entry("border_color", "any"), Map.entry("border_width", "number"),
                Map.entry("border_radius", "number"), Map.entry("minimum", "number"),
                Map.entry("maximum", "number")), Map.of(), true);
        register("acc_display_graph", "hud", Map.of(
                "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), Map.of(), true);
        register("acc_display_plotter", "hud", Map.of(
                "value", "number", "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), Map.of(), true);
        register("acc_display_external", "hud", Map.of(
                "visible", "boolean", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), Map.of(), true);
        register("acc_display_crn", "hud", Map.of(
                "visible", "boolean", "text", "string", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), Map.of(), true);
        // Kept separate from the legacy CRN node so new graphs say exactly what
        // they present while saved graphs retain their existing node id.
        register("acc_display_shipping_information", "hud", Map.of(
                "visible", "boolean", "text", "string", "x", "number", "y", "number",
                "width", "number", "height", "number", "scale", "number",
                "rotation", "number"), Map.of(), true);
        register("acc_display_scm_information", "hud", Map.ofEntries(
                Map.entry("visible", "boolean"),
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
                Map.entry("show_inertia", "boolean")), Map.of(), true);
        register("acc_display_mode", "hud",
                Map.of("exec", "exec", "target", "target", "mode", "string"),
                Map.of("exec", "exec", "mode", "string", "success", "boolean"), false);
        register("validate_number", "data", Map.of("value", "number"), Map.of("value", "number", "valid", "boolean"), false);
        register("reroute", "core", Map.of("value", "any"), Map.of("value", "any"), false);
        register("sticky_note", "core", Map.of(), Map.of(), false);
        register("image_reference", "core", Map.of(), Map.of(), false);
        register(AdvancedGraphFunctions.CALL_TYPE, "functions", Map.of(), Map.of(), true);
        register(AdvancedGraphFunctions.INPUT_TYPE, "functions", Map.of(), Map.of(), false);
        register(AdvancedGraphFunctions.OUTPUT_TYPE, "functions", Map.of(), Map.of(), false);
        register("play_sound", "controller", Map.of("exec", "exec", "stop", "exec", "sound", "string",
                "world", "boolean", "x", "number", "y", "number", "z", "number", "volume", "number",
                "pitch", "number", "loop", "boolean"),
                Map.of("exec", "exec"), false);
        register("controller_channel_input", "controller", Map.of(), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("gamepad_input", "controller", Map.of(), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("mouse_input", "controller", Map.of("reset_axis", "boolean", "timeout", "number"),
                Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("controller_channel_output", "controller", Map.of("exec", "exec", "value", "number"), Map.of("exec", "exec"), false);
        register("local_redstone_input", "controller", Map.of(), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("local_redstone_output", "controller", Map.of("exec", "exec", "value", "number", "face", "direction"), Map.of("exec", "exec"), false);
        register("wireless_frequency_input", "controller", Map.of("frequency", "frequency"), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("wireless_frequency_output", "controller", Map.of("exec", "exec", "frequency", "frequency", "value", "number"), Map.of("exec", "exec"), false);
        register("discovered_target_input", "controller", Map.of("target", "target"), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("direct_target_output", "controller", Map.of("exec", "exec", "target", "target", "value", "number"), Map.of("exec", "exec"), false);
        register("linker_face_input", "controller", Map.of("target", "target", "face", "direction"), Map.of("exec", "exec", "value", "number", "active", "boolean"), false);
        register("linker_face_output", "controller", Map.of("exec", "exec", "target", "target", "face", "direction", "value", "number"), Map.of("exec", "exec"), false);
        register("controller_tracker", "controller", Map.of(), trackingOutputs(), false);
        register("portable_tracker", "controller", Map.of(), gogglesTrackingOutputs(), false);
        register("reset_outputs", "controller", Map.of("exec", "exec"), Map.of("exec", "exec"), false);
        register("ship_scan_configuration", "ship_control", Map.of(
                        "exec", "exec",
                        "ship_name", "string",
                        "control_mode", "string",
                        "display_progress", "boolean",
                        "force_full_initialization", "boolean",
                        "ignore_bearings", "boolean",
                        "ignore_sails", "boolean",
                        "ignore_thrusters", "boolean"),
                initOutputs(), true);
        register("ship_stop_initialization", "ship_control",
                Map.of("exec", "exec"), commandOutputs(), false);
        register("ship_status", "ship_control", Map.of(), Map.ofEntries(
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
        register("scm_configuration", "ship_control", Map.of(), Map.ofEntries(
                Map.entry("configured", "boolean"),
                Map.entry("scanning", "boolean"),
                Map.entry("candidate_count", "number"),
                Map.entry("group_count", "number"),
                Map.entry("action_binding_count", "number"),
                Map.entry("excluded_unit_count", "number"),
                Map.entry("action_groups", "map")), false);
        register("scm_brain_debug", "ship_control", Map.of(), Map.ofEntries(
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
        register("ship_coupler_status", "ship_control", Map.of(), Map.of(
                "coupler_count", "number",
                "coupled_count", "number",
                "carriage_count", "number",
                "any_coupled", "boolean",
                "all_coupled", "boolean",
                "coupler_status", "string"), false);
        register("ship_flight_behavior", "ship_control",
                Map.of("exec", "exec", "behavior", "string"),
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
        register("ship_stabilize", "ship_control",
                Map.of("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_decelerate", "ship_control",
                Map.of("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_brake", "ship_control",
                Map.of("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_hover", "ship_control",
                Map.of("exec", "exec", "strength", "number"), commandOutputs(), true);
        register("ship_climb", "ship_control",
                Map.of("exec", "exec", "y", "number", "speed", "number"), targetCommandOutputs(), true);
        register("ship_face", "ship_control",
                Map.of("exec", "exec", "x", "number", "y", "number", "z", "number", "speed", "number"),
                targetCommandOutputs(), true);
        register("ship_dock", "ship_control", Map.ofEntries(
                        Map.entry("exec", "exec"),
                        Map.entry("x", "number"),
                        Map.entry("y", "number"),
                        Map.entry("z", "number"),
                        Map.entry("speed", "number"),
                        Map.entry("tolerance", "number"),
                        Map.entry("lock_rotation", "boolean"),
                        Map.entry("target_point", "string"),
                        Map.entry("target_direction_x", "number"),
                        Map.entry("target_direction_y", "number"),
                        Map.entry("target_direction_z", "number"),
                        Map.entry("target_up_x", "number"),
                        Map.entry("target_up_y", "number"),
                        Map.entry("target_up_z", "number")),
                targetCommandOutputs(), true);
        register("ship_navigate", "ship_control", Map.ofEntries(
                        Map.entry("exec", "exec"),
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
        register("ship_follow", "ship_control", Map.ofEntries(
                        Map.entry("exec", "exec"),
                        Map.entry("x", "number"),
                        Map.entry("y", "number"),
                        Map.entry("z", "number"),
                        Map.entry("speed", "number"),
                        Map.entry("follow_distance", "number"),
                        Map.entry("avoid_collisions", "boolean")),
                commandOutputs(), true);
        register("ship_stop", "ship_control", Map.of("exec", "exec"), commandOutputs(), false);
        register("ship_couple_carriage", "ship_control",
                Map.of("exec", "exec", "endpoint", "number"),
                targetCommandOutputs(), true);
        register("ship_decouple_carriage", "ship_control",
                Map.of("exec", "exec", "endpoint", "number"),
                targetCommandOutputs(), true);
        register("ship_telemetry", "ship_control",
                Map.of(
                        "collision_detection_distance", "number",
                        "collision_poll_rate", "number"), Map.ofEntries(
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
        register("shipping_travel_metrics", "ship_control", Map.of(), Map.ofEntries(
                Map.entry("shipping_active", "boolean"),
                Map.entry("shipping_pilot_present", "boolean"),
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
        register("shipping_manifest", "ship_control", Map.of(), Map.ofEntries(
                Map.entry("shipping_manifest_title", "string"),
                Map.entry("shipping_manifest_current_stop", "string"),
                Map.entry("shipping_manifest_target_stop", "string"),
                Map.entry("shipping_manifest_next_stop", "string"),
                Map.entry("shipping_manifest_stop_count", "number"),
                Map.entry("shipping_manifest_current_entry", "number"),
                Map.entry("shipping_manifest_next_entry", "number"),
                Map.entry("shipping_manifest_is_cyclic", "boolean"),
                Map.entry("shipping_manifest_stops", "list")), false);
        register("shipping_start", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
        register("shipping_pause", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
        register("shipping_resume", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
        register("shipping_stop", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
        register("shipping_restart", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
        register("shipping_skip", "shipping_schedule", Map.of("exec", "exec"),
                commandOutputs(), false);
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
        return Map.ofEntries(
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
        return Map.copyOf(outputs);
    }

    // Get the amount inputs
    private static Map<String, String> amountInputs() {
        return Map.of("exec", "exec", "amount", "number");
    }

    // Get the command outputs
    private static Map<String, String> commandOutputs() {
        return Map.of("exec", "exec", "success", "boolean");
    }

    // Get the target command outputs
    private static Map<String, String> targetCommandOutputs() {
        return Map.of("exec", "exec", "success", "boolean", "complete", "exec");
    }

    // Initialize the outputs
    private static Map<String, String> initOutputs() {
        return Map.ofEntries(
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
        return Map.ofEntries(
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
                || connected
                || !node.data().getBoolean(SHIP_SPEED_PERCENT_TAG)) {
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

    // Get the inputs
    public static Map<String, String> inputs(AdvancedGraphDocument.Node node) {
        Definition definition = get(node.type());
        CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
        Map<String, String> baseInputs = definition == null ? Map.of() : definition.inputs();
        if ("switch".equals(node.type()) && switchDataMode(node)) {
            baseInputs = Map.of(
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
            baseInputs = Map.of();
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
        Map<String, String> ports = mergePorts(baseInputs, dynamicInputs);
        if (node.data().getBoolean(COLLAPSE_INPUTS_TO_MAP_TAG)
                && ports.entrySet().stream().anyMatch(entry -> !"exec".equals(entry.getValue()))) {
            ports = new LinkedHashMap<>(ports);
            ports.put(COLLAPSED_INPUT_MAP_PORT, "map");
        }
        return ports;
    }

    // Check if this is a CRN static text mode
    public static boolean isCrnStaticTextMode(String mode) {
        return ShipInformationDisplayModes.isStaticText(mode);
    }

    // Get the outputs
    public static Map<String, String> outputs(AdvancedGraphDocument.Node node) {
        Definition definition = get(node.type());
        Map<String, String> baseOutputs = definition == null ? Map.of() : definition.outputs();
        CompoundTag dynamicOutputs = node.data().getCompound("DynamicOutputs");
        if ("switch".equals(node.type())) {
            if (switchDataMode(node)) {
                baseOutputs = Map.of("value", "any");
                dynamicOutputs = new CompoundTag();
            } else {
                baseOutputs = Map.of("default", "exec");
                dynamicOutputs = dynamicOutputs.copy();
                for (String port : dynamicOutputs.getAllKeys()) {
                    if (port.startsWith("case_")) {
                        dynamicOutputs.putString(port, "exec");
                    }
                }
            }
        }
        Map<String, String> ports = mergePorts(baseOutputs, dynamicOutputs);
        if (node.data().getBoolean(COLLAPSE_OUTPUTS_TO_MAP_TAG)
                && ports.entrySet().stream().anyMatch(entry -> !"exec".equals(entry.getValue()))) {
            ports = new LinkedHashMap<>(ports);
            ports.put(COLLAPSED_OUTPUT_MAP_PORT, "map");
        }
        return ports;
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
    private static Map<String, String> mergePorts(Map<String, String> base, CompoundTag dynamic) {
        Map<String, String> values = new LinkedHashMap<>(base);
        for (String key : dynamic.getAllKeys()) values.put(key, dynamic.getString(key));
        Map<String, String> ports = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(PORT_ORDER))
                .forEach(entry -> ports.put(entry.getKey(), entry.getValue()));
        return ports;
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

    private static final Comparator<String> PORT_ORDER = (left, right) -> {
        int group = Integer.compare(portGroup(left), portGroup(right));
        return group != 0 ? group : naturalCompare(left, right);
    };

    // Get the port group
    private static int portGroup(String port) {
        return switch (port) {
            case "exec", "start", "pause", "stop", "reset" -> -100;
            case "target" -> -90;
            case "label" -> -89;
            case "visible" -> -88;
            case "frequency" -> -80;
            case "condition", "selector" -> -70;
            case "string" -> -69;
            case "delimiter", "search" -> -68;
            case "start_index" -> -67;
            case "end_index" -> -66;
            case "a" -> -60;
            case "operator" -> -59;
            case "b" -> -58;
            case "true" -> -50;
            case "false" -> -49;
            case "default" -> -40;
            case "display_text" -> 10;
            case "display_lines" -> 11;
            case PID_INTEGRAL_MIN_PORT -> 20;
            case PID_INTEGRAL_MAX_PORT -> 21;
            default -> {
                if (port.startsWith("exec_")) yield -99;
                if (port.startsWith("display_line_")) yield 12;
                if (port.startsWith("display_")) yield 13;
                if (port.startsWith("value_")) yield 80;
                if (port.startsWith("field_")) yield 81;
                if (port.startsWith("setting_")) yield 20;
                if (port.startsWith("state_")) yield 30;
                if (port.startsWith("item_")) yield 40;
                if (port.startsWith("fluid_")) yield 50;
                yield 0;
            }
        };
    }

    // Get the natural compare
    private static int naturalCompare(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = leftIndex;
                int rightEnd = rightIndex;
                while (leftEnd < left.length() && Character.isDigit(left.charAt(leftEnd))) leftEnd++;
                while (rightEnd < right.length() && Character.isDigit(right.charAt(rightEnd))) rightEnd++;
                int num = Integer.compare(Integer.parseInt(left.substring(leftIndex, leftEnd)),
                        Integer.parseInt(right.substring(rightIndex, rightEnd)));
                if (num != 0) return num;
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            int character = Character.compare(Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (character != 0) return character;
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length(), right.length());
    }

    // Check if the values are compatible
    public static boolean compatible(String from, String to) {
        if (from == null || to == null) return false;
        if ("exec".equals(from) || "exec".equals(to)) return from.equals(to);
        if (from.equals(to) || "any".equals(from) || "any".equals(to)) return true;
        return "string".equals(to) && !"exec".equals(from);
    }

    // Check if the values are compatible
    public static boolean compatible(String from, AdvancedGraphDocument.Node toNode, String toPort) {
        String to = toNode == null ? null : inputs(toNode).get(toPort);
        if ("set_block_data".equals(toNode == null ? "" : toNode.type())
                && from != null && to != null && !"exec".equals(from) && !"exec".equals(to)) {
            return true;
        }
        return compatible(from, to);
    }

    // Get the advanced graph catalog display name
    public static String displayName(String id) {
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
            case "variable_get" -> "Get Variable";
            case "variable_set" -> "Set Variable";
            case "data_branch" -> "Select Data";
            case "switch" -> "Switch";
            case "edge_detector" -> "Edge Detector";
            case "pulse_on_change" -> "Pulse On Change";
            case "flip_flop" -> "Flip-Flop";
            case "sticky_note" -> "Sticky Note";
            case "image_reference" -> "Image";
            case "function_call" -> "Function";
            case "function_input" -> "Function Inputs";
            case "function_output" -> "Function Outputs";
            case "do_once" -> "Do Once";
            case "do_n" -> "Do N";
            case "bounded_loop" -> "Bounded Loop";
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
            case "linker_face_input" -> "Linker Face Input";
            case "linker_face_output" -> "Linker Face Output";
            case "controller_tracker" -> "Controller Tracker";
            case "portable_tracker" -> "Goggles Tracker";
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
            case "ship_stabilize" -> "Stabilize";
            case "ship_decelerate" -> "Decelerate";
            case "ship_brake" -> "Brake";
            case "ship_hover" -> "Hover";
            case "ship_climb" -> "Climb to Y Level";
            case "ship_face" -> "Face Coordinates";
            case "ship_dock" -> "Dock at Coordinates";
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
            case "adrc_nth_order" -> "ADRC Nth Order";
            default -> titleCase(id);
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
            case "shipping_schedule" -> "Shipping Schedule";
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
            case "ship_control" -> 0xFF4FC3C8;
            case "shipping_schedule" -> 0xFFDB8C4B;
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
