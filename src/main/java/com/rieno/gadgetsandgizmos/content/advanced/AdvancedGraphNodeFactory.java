package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.List;
import java.util.Map;

// Build identical node defaults for the client editor and server integrations
public final class AdvancedGraphNodeFactory {
    public static final int STICKY_NOTE_DEFAULT_WIDTH = 190;
    public static final int STICKY_NOTE_DEFAULT_HEIGHT = 130;

    private static final String HUD_FIELD_LABELS = "HudFieldLabels";
    private static final String WIDGET_ELEMENTS = "WidgetElements";
    private static final int MIN_EXECUTION_OUTPUTS = 2;
    private static final int MAX_EXECUTION_OUTPUTS = 16;
    private static final int MIN_EXECUTION_INPUTS = 2;
    private static final int MAX_EXECUTION_INPUTS = 16;
    private static final double DEFAULT_SMOOTHING_AMOUNT = 0.25D;
    private static final List<String> COMPARE_OPERATOR_OPTIONS =
            List.of("==", ">", "<", ">=", "<=", "!=");
    private static final List<String> FILTER_OPERATION_OPTIONS =
            List.of("==", "!=", ">", "<", "contains", "starts_with");
    private static final List<String> VARIABLE_TYPE_OPTIONS = List.of(
            "boolean", "integer", "float", "string", "direction",
            "frequency", "target", "list", "map");

    private AdvancedGraphNodeFactory() {
    }

    public record Context(String firstGogglesPairId,
                          String nextVariableName,
                          String firstVariableName) {
        public static final Context EMPTY =
                new Context("", "variable", "variable");

        public Context {
            firstGogglesPairId = normalize(firstGogglesPairId);
            nextVariableName = fallback(nextVariableName, "variable");
            firstVariableName = fallback(firstVariableName, "variable");
        }

        private static String normalize(String value) {
            return value == null ? "" : value.strip();
        }

        private static String fallback(String value, String fallback) {
            String normalized = normalize(value);
            return normalized.isBlank() ? fallback : normalized;
        }
    }

    public static CompoundTag createDefaultData(String type, Context context) {
        Context safeContext = context == null ? Context.EMPTY : context;
        CompoundTag data = new CompoundTag();
        if ("sticky_note".equals(type)) {
            data.putString("Text", "# Note");
            data.putInt("Width", STICKY_NOTE_DEFAULT_WIDTH);
            data.putInt("Height", STICKY_NOTE_DEFAULT_HEIGHT);
        }
        if ("image_reference".equals(type)) {
            data.putString("Source", "");
            data.putInt("Width", 240);
            data.putInt("Height", 180);
        }
        if ("event_trigger".equals(type)) data.putString("Event", "custom");
        if ("gamepad_input".equals(type)) {
            HardwareControllerBindings.options().stream().findFirst().ifPresent(option -> {
                data.putString("BindingId", option.id());
                data.putString("BindingLabel", option.label());
            });
        }
        if ("event_named_controller".equals(type) || "send_named_controller_event".equals(type)) {
            data.putString("Event", "event");
        }
        if ("send_named_controller_event".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("distance", graphDefault("number", 0.0D));
            data.put("Defaults", defaults);
        }
        if ("split_string".equals(type)
                || "substring".equals(type)
                || "find_in_string".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("string", graphDefault("string", ""));
            if ("split_string".equals(type)) {
                defaults.put("delimiter", graphDefault("string", ","));
            } else if ("substring".equals(type)) {
                defaults.put("start_index", graphDefault("number", 0.0D));
                defaults.put("end_index", graphDefault("number", 0.0D));
            } else {
                defaults.put("search", graphDefault("string", ""));
            }
            data.put("Defaults", defaults);
        }
        if ("str_split".equals(type) || "str_parse_array".equals(type)
                || "str_join_array".equals(type) || "str_regex".equals(type)
                || "math_atan2".equals(type) || "math_power".equals(type)
                || "math_ln".equals(type) || "math_log".equals(type)
                || "math_average".equals(type) || "arr_slice".equals(type)
                || "arr_filter".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            if ("str_split".equals(type)) defaults.put("Split On", graphDefault("string", ","));
            if ("str_parse_array".equals(type)) defaults.put("Delimiter", graphDefault("string", ","));
            if ("str_join_array".equals(type)) defaults.put("Separator", graphDefault("string", ", "));
            if ("str_regex".equals(type)) defaults.put("Pattern", graphDefault("string", ".*"));
            if ("math_atan2".equals(type)) defaults.put("X", graphDefault("number", 1.0D));
            if ("math_power".equals(type)) {
                defaults.put("Base", graphDefault("number", 1.0D));
                defaults.put("Exp", graphDefault("number", 1.0D));
            }
            if ("math_ln".equals(type)) defaults.put("In", graphDefault("number", 1.0D));
            if ("math_log".equals(type)) {
                defaults.put("In", graphDefault("number", 10.0D));
                defaults.put("Base", graphDefault("number", 10.0D));
            }
            if ("math_average".equals(type)) defaults.put("Samples", graphDefault("number", 10.0D));
            if ("arr_slice".equals(type)) defaults.put("End", graphDefault("number", 1.0D));
            if ("arr_filter".equals(type)) {
                defaults.put("Operation", graphDefault("string", "=="));
                CompoundTag inputOptions = data.getCompound("InputOptions");
                ListTag operations = new ListTag();
                for (String operation : FILTER_OPERATION_OPTIONS) {
                    operations.add(StringTag.valueOf(operation));
                }
                inputOptions.put("Operation", operations);
                data.put("InputOptions", inputOptions);
            }
            data.put("Defaults", defaults);
        }
        // -----------------------------------------------------SHIP DEFAULTS-----------------------------------------------------

        if ("ship_dock".equals(type) || "ship_navigate".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("target_point", graphDefault("string", "center_of_mass"));
            data.put("Defaults", defaults);
        }
        if ("ship_flight_behavior".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("behavior", graphDefault(
                    "string", AdvancedGraphCatalog.defaultShipFlightBehavior()));
            data.put("Defaults", defaults);
        }
        if ("ship_initialize".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("control_mode", graphDefault(
                    "string", AdvancedGraphCatalog.defaultShipControlMode()));
            data.put("Defaults", defaults);
        }
        if (AdvancedGraphCatalog.isShipCouplerCommandType(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("endpoint", graphDefault("number", -1.0D));
            data.put("Defaults", defaults);
        }
        if (AdvancedGraphCatalog.isShipSpeedNode(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("speed", graphDefault(
                    "number", AdvancedGraphCatalog.defaultShipSpeed(type)));
            data.put("Defaults", defaults);
        }
        if ("ship_telemetry".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("collision_detection_distance", graphDefault(
                    "number",
                    AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE));
            defaults.put("collision_poll_rate", graphDefault(
                    "number", AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE));
            data.put("Defaults", defaults);
        }
        if ("ship_follow".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("follow_distance", graphDefault("number", 8.0D));
            defaults.put("avoid_collisions", graphDefault("boolean", false));
            data.put("Defaults", defaults);
        }
        if (AdvancedGraphCatalog.isConstructorType(type)) {
            data.putBoolean(AdvancedGraphCatalog.DYNAMIC_CONSTRUCTOR_TAG, true);
            CompoundTag inputs = new CompoundTag();
            CompoundTag labels = new CompoundTag();
            CompoundTag defaults = data.getCompound("Defaults");
            String port = "list_create".equals(type) ? "value_1" : "field_1";
            inputs.putString(port, "any");
            labels.putString(port, "list_create".equals(type) ? "Value 1" : "Field 1");
            defaults.put(port, graphDefault("string", ""));
            data.put("DynamicInputs", inputs);
            data.put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
            data.put("Defaults", defaults);
        }
        if ("portable_tracker".equals(type)
                && !safeContext.firstGogglesPairId().isBlank()) {
            data.putString("GogglesPair", safeContext.firstGogglesPairId());
        }
        if ("mouse_input".equals(type)) {
            data.putString("MouseInput", "left_click");
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("reset_axis", graphDefault("boolean", false));
            defaults.put("timeout", graphDefault("number", 20.0D));
            data.put("Defaults", defaults);
        }
        if ("lqr_controller".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("target", graphDefault("number", 0.0D));
            defaults.put("actual", graphDefault("number", 0.0D));
            defaults.put("gain", graphDefault("number", 1.0D));
            defaults.put("feed_forward", graphDefault("number", 0.0D));
            defaults.put("min", graphDefault("number", -1.0D));
            defaults.put("max", graphDefault("number", 1.0D));
            data.put("Defaults", defaults);
        }
        if ("adrc".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("target", graphDefault("number", 0.0D));
            defaults.put("actual", graphDefault("number", 0.0D));
            defaults.put("delta_time", graphDefault("number", 0.05D));
            defaults.put("controller_bandwidth", graphDefault("number", 1.0D));
            defaults.put("observer_bandwidth", graphDefault("number", 4.0D));
            defaults.put("plant_gain", graphDefault("number", 1.0D));
            defaults.put("output_limit", graphDefault("number", 1.0D));
            data.put("Defaults", defaults);
        }
        if (isPulseBehaviorInputType(type)) data.putString("PulseBehavior", "both");
        if ("variable_set".equals(type)) data.putString("Variable", safeContext.nextVariableName());
        if ("variable_get".equals(type) || "event_variable_change".equals(type)) {
            data.putString("Variable", safeContext.firstVariableName());
        }
        if ("delay".equals(type) || "debounce".equals(type)) data.putInt("Ticks", 20);
        if ("event_periodic".equals(type)) data.putInt("Period", 20);
        if ("random_int".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("max", graphDefault("number", 2.0D));
            data.put("Defaults", defaults);
        }
        if ("random_float_in_range".equals(type) || "random_int_in_range".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("min", graphDefault("number", 0.0D));
            defaults.put("max", graphDefault("number", 1.0D));
            data.put("Defaults", defaults);
        }
        if ("switch".equals(type)) {
            data.putString(AdvancedGraphCatalog.SWITCH_TYPE_TAG,
                    AdvancedGraphCatalog.SWITCH_EXECUTION_TYPE);
        }
        if (isExecutionSplitterType(type)) {
            data.putInt("OutputCount", MIN_EXECUTION_OUTPUTS);
            putExecutionOutputs(type, data, MIN_EXECUTION_OUTPUTS);
        }
        if (isExecutionCombinerType(type)) {
            data.putInt("InputCount", MIN_EXECUTION_INPUTS);
            putExecutionInputs(data, MIN_EXECUTION_INPUTS);
        }
        if ("convert_type".equals(type)) {
            data.putString("OutputType", "string");
            CompoundTag outputs = new CompoundTag();
            outputs.putString("value", "string");
            data.put("DynamicOutputs", outputs);
        }
        if ("compare".equals(type)) {
            CompoundTag defaults = new CompoundTag();
            defaults.put("operator", graphDefault("string", "=="));
            data.put("Defaults", defaults);
            putCompareOperatorOptions(data);
        }
        if ("smoothing".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("amount", graphDefault("number", DEFAULT_SMOOTHING_AMOUNT));
            data.put("Defaults", defaults);
        }
        // -----------------------------------------------------HUD DEFAULTS------------------------------------------------------

        if (isHudType(type)) {
            data.putString("Label", isAccDisplayWidgetType(type) ? "ACC Display"
                    : "advanced_hud_element".equals(type) ? "Advanced HUD" : "HUD");
            CompoundTag inputs = data.getCompound("DynamicInputs");
            inputs.putString("value", "any");
            data.put("DynamicInputs", inputs);
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("value", graphDefault("string", ""));
            defaults.put("visible", graphDefault("boolean", true));
            if (isAccDisplayWidgetType(type)) {
                defaults.put("label", graphDefault("string", "Widget"));
                defaults.put("x", graphDefault("number", 4.0D));
                defaults.put("y", graphDefault("number", 4.0D));
                defaults.put("width", graphDefault("number", 132.0D));
                defaults.put("height", graphDefault("number", 64.0D));
                defaults.put("rotation", graphDefault("number", 0.0D));
                defaults.put("scale", graphDefault("number", 1.0D));
                defaults.put("font_size", graphDefault("number",
                        AdvancedHudElementStyle.DEFAULT_FONT_SIZE));
                defaults.put("color", graphDefault("number",
                        AdvancedHudElementStyle.DEFAULT_TEXT_COLOR));
                defaults.put("background_color", graphDefault("number",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_BACKGROUND_COLOR));
                defaults.put("accent_color", graphDefault("number",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_ACCENT_COLOR));
                defaults.put("track_color", graphDefault("number",
                        AdvancedHudElementStyle.DEFAULT_WIDGET_TRACK_COLOR));
                defaults.put("border_color", graphDefault("number", 0xFF527185));
                defaults.put("border_width", graphDefault("number", 1.0D));
                defaults.put("border_radius", graphDefault("number", 4.0D));
                defaults.put("minimum", graphDefault("number", 0.0D));
                defaults.put("maximum", graphDefault("number", 1.0D));
            }
            data.put("Defaults", defaults);
            CompoundTag labels = new CompoundTag();
            labels.putString("value", "Value");
            data.put(HUD_FIELD_LABELS, labels);
            if ("advanced_hud_element".equals(type) || isAccDisplayWidgetType(type)) {
                data.putInt("WidgetWidth", 140);
                data.putInt("WidgetHeight", 72);
                ListTag elements = new ListTag();
                if (isAccDisplayWidgetType(type)) {
                    data.putString("WidgetType", "text");
                    CompoundTag elm = new CompoundTag();
                    elm.putBoolean("ManagedWidget", true);
                    elm.putString("Type", "text");
                    elm.putString("Text", "Widget");
                    elm.putInt("X", 4);
                    elm.putInt("Y", 4);
                    elm.putInt("W", 132);
                    elm.putInt("H", 64);
                    bindManagedAccWidget(elm);
                    AdvancedHudElementStyle.applyDefaults(elm);
                    elements.add(elm);
                }
                data.put(WIDGET_ELEMENTS, elements);
            }
        }
        if ("acc_display_graph".equals(type) || "acc_display_plotter".equals(type)
                || "acc_display_external".equals(type)
                || "acc_display_crn".equals(type)
                || "acc_display_shipping_information".equals(type)
                || "acc_display_scm_information".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("visible", graphDefault("boolean", true));
            if ("acc_display_plotter".equals(type)) {
                defaults.put("value", graphDefault("number", 0.0D));
            }
            if ("acc_display_crn".equals(type) || "acc_display_shipping_information".equals(type)) {
                data.putString("DisplayMode",
                        ShipInformationDisplayModes.DEFAULT);
                defaults.put("text", graphDefault("string", ""));
            }
            if ("acc_display_scm_information".equals(type)) {
                for (String port : AdvancedGraphCatalog.get(type).inputs().keySet()) {
                    if (port.startsWith("show_")) {
                        defaults.put(port, graphDefault("boolean", false));
                    }
                }
            }
            defaults.put("x", graphDefault("number", 0.0D));
            defaults.put("y", graphDefault("number", 0.0D));
            defaults.put("width", graphDefault("number", 320.0D));
            defaults.put("height", graphDefault("number", 180.0D));
            defaults.put("scale", graphDefault("number", 1.0D));
            defaults.put("rotation", graphDefault("number", 0.0D));
            data.put("Defaults", defaults);
        }
        if ("acc_display_mode".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("mode", graphDefault("string", AccDisplayBlockEntity.DISPLAY_MODE_AUTO));
            data.put("Defaults", defaults);
            CompoundTag inputOptions = data.getCompound("InputOptions");
            ListTag opts = new ListTag();
            AccDisplayBlockEntity.DISPLAY_MODES.forEach(
                    mode -> opts.add(StringTag.valueOf(mode)));
            inputOptions.put("mode", opts);
            data.put("InputOptions", inputOptions);
        }
        if ("play_sound".equals(type)) {
            CompoundTag defaults = data.getCompound("Defaults");
            defaults.put("sound", graphDefault("string", "minecraft:block.note_block.pling"));
            defaults.put("world", graphDefault("boolean", false));
            defaults.put("x", graphDefault("number", 0.0));
            defaults.put("y", graphDefault("number", 0.0));
            defaults.put("z", graphDefault("number", 0.0));
            defaults.put("volume", graphDefault("number", 1.0));
            defaults.put("pitch", graphDefault("number", 1.0));
            defaults.put("loop", graphDefault("boolean", false));
            data.put("Defaults", defaults);
        }
        if ("variable_set".equals(type)) {
            CompoundTag inputs = new CompoundTag();
            inputs.putString("default", "boolean");
            inputs.putString("value", "boolean");
            data.put("DynamicInputs", inputs);
            CompoundTag outputs = new CompoundTag();
            outputs.putString("value", "boolean");
            data.put("DynamicOutputs", outputs);
            data.putString("VariableType", "boolean");
            CompoundTag defaults = new CompoundTag();
            CompoundTag typeTag = new CompoundTag();
            CompoundTag typePayload = new CompoundTag();
            typeTag.putString("Type", "string");
            typePayload.putString("Value", "boolean");
            typeTag.put("Payload", typePayload);
            defaults.put("type", typeTag);
            CompoundTag val = new CompoundTag();
            CompoundTag valuePayload = new CompoundTag();
            val.putString("Type", "boolean");
            valuePayload.putBoolean("Value", false);
            val.put("Payload", valuePayload);
            defaults.put("default", val);
            data.put("Defaults", defaults);
            putVariableTypeOptions(data);
        }
        if ("curve".equals(type)) {
            data.putString("CurveType", "linear");
            CompoundTag defaults = new CompoundTag();
            defaults.put("value", graphDefault("number", AdvancedGraphCurve.DEFAULT_MAXIMUM));
            defaults.put("min", graphDefault("number", AdvancedGraphCurve.DEFAULT_MINIMUM));
            defaults.put("max", graphDefault("number", AdvancedGraphCurve.DEFAULT_MAXIMUM));
            defaults.put("speed", graphDefault("number", AdvancedGraphCurve.DEFAULT_SPEED));
            data.put("Defaults", defaults);
            net.minecraft.nbt.ListTag points = new net.minecraft.nbt.ListTag();
            CompoundTag start = new CompoundTag();
            start.putDouble("X", 0.0);
            start.putDouble("Y", 0.0);
            CompoundTag end = new CompoundTag();
            end.putDouble("X", 1.0);
            end.putDouble("Y", 1.0);
            points.add(start);
            points.add(end);
            data.put("Points", points);
        }
        return data;
    }

    public static CompoundTag graphDefault(String type, Object val) {
        CompoundTag entry = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        entry.putString("Type", type);
        if (val instanceof Boolean bool) payload.putBoolean("Value", bool);
        else if (val instanceof Number num) payload.putDouble("Value", num.doubleValue());
        else if (val instanceof CompoundTag compound) payload.merge(compound.copy());
        else payload.putString("Value", String.valueOf(val));
        entry.put("Payload", payload);
        return entry;
    }

    public static void putVariableTypeOptions(CompoundTag data) {
        CompoundTag inputOptions = data.getCompound("InputOptions");
        ListTag values = new ListTag();
        for (String option : VARIABLE_TYPE_OPTIONS) {
            values.add(StringTag.valueOf(option));
        }
        inputOptions.put("type", values);
        data.put("InputOptions", inputOptions);
    }

    public static void putCompareOperatorOptions(CompoundTag data) {
        CompoundTag inputOptions = data.getCompound("InputOptions");
        ListTag values = new ListTag();
        for (String option : COMPARE_OPERATOR_OPTIONS) {
            values.add(StringTag.valueOf(option));
        }
        inputOptions.put("operator", values);
        data.put("InputOptions", inputOptions);
    }

    public static boolean isPulseBehaviorInputType(String type) {
        return "controller_channel_input".equals(type)
                || "gamepad_input".equals(type)
                || "local_redstone_input".equals(type)
                || "wireless_frequency_input".equals(type);
    }

    public static boolean isHudType(String type) {
        return "hud_element".equals(type)
                || "advanced_hud_element".equals(type)
                || isAccDisplayWidgetType(type);
    }

    public static boolean isAccDisplayWidgetType(String type) {
        return "acc_display_widget".equals(type)
                || "acc_hologram_widget".equals(type);
    }

    public static void bindManagedAccWidget(CompoundTag element) {
        Map<String, String> bindings = Map.ofEntries(
                Map.entry("Text", "label"), Map.entry("X", "x"),
                Map.entry("Y", "y"), Map.entry("W", "width"),
                Map.entry("H", "height"), Map.entry("Rotation", "rotation"),
                Map.entry("Scale", "scale"), Map.entry("FontSize", "font_size"),
                Map.entry("Color", "color"),
                Map.entry("BackgroundColor", "background_color"),
                Map.entry("AccentColor", "accent_color"),
                Map.entry("TrackColor", "track_color"),
                Map.entry("BorderColor", "border_color"),
                Map.entry("BorderWidth", "border_width"),
                Map.entry("BorderRadius", "border_radius"),
                Map.entry("Min", "minimum"), Map.entry("Max", "maximum"));
        bindings.forEach((property, port) ->
                AdvancedHudElementBinding.bind(element, property, port));
    }

    private static boolean isExecutionSplitterType(String type) {
        return "parallel_execution".equals(type)
                || "sequenced_execution".equals(type)
                || "switch".equals(type);
    }

    private static boolean isExecutionCombinerType(String type) {
        return "exec_combine".equals(type);
    }

    private static void putExecutionOutputs(
            String type, CompoundTag data, int count) {
        int clamped = Math.max(MIN_EXECUTION_OUTPUTS,
                Math.min(MAX_EXECUTION_OUTPUTS, count));
        CompoundTag outputs = new CompoundTag();
        for (int idx = 0; idx < clamped; idx++) {
            String port = "switch".equals(type)
                    ? "case_" + idx : "exec_" + (idx + 1);
            outputs.putString(port, "exec");
        }
        data.put("DynamicOutputs", outputs);
    }

    private static void putExecutionInputs(CompoundTag data, int count) {
        int clamped = Math.max(MIN_EXECUTION_INPUTS,
                Math.min(MAX_EXECUTION_INPUTS, count));
        CompoundTag inputs = new CompoundTag();
        for (int idx = 1; idx <= clamped; idx++) {
            inputs.putString("exec_" + idx, "exec");
        }
        data.put("DynamicInputs", inputs);
    }
}
