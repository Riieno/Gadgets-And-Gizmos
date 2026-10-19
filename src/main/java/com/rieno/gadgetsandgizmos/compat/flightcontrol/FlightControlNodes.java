package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphHostServices;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodePresentationRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Register the native control blocks as optional Main Graph nodes
public final class FlightControlNodes{
    public static final String PREFIX = "createthrusters:flight_control_";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Set<String> RETIRED_INPUTS = Set.of("block_position", "computer", "computer_position",
            "engines", "sensors", "targets", "target", "linked_targets", "face_assignments");

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    private FlightControlNodes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load the settings audited against Flight Control 0.7.7
    public static void register(){
        try(var stream = FlightControlNodes.class.getResourceAsStream(
                "/data/createthrusters/compat/flight_control_nodes.json")){
            if(stream == null) throw new IllegalStateException("Missing Flight Control node schema");
            List<Spec> specs = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<List<Spec>>(){}.getType());
            for(Spec spec : specs){
                String type = PREFIX + spec.path();
                SPECS.put(type, spec);
                Map<String, String> inputs = new LinkedHashMap<>();
                Map<String, GraphValue> defaults = new LinkedHashMap<>();
                Map<String, List<String>> groups = new LinkedHashMap<>();
                Map<String, List<String>> options = new LinkedHashMap<>();
                for(Setting field : spec.fields()){
                    inputs.put(field.port(), field.type());
                    defaults.put(field.port(), field.defaultValue());
                    groups.computeIfAbsent(field.group(), key -> new ArrayList<>()).add(field.port());
                    if(field.options() != null && !field.options().isEmpty()) options.put(field.port(), field.options());
                }
                for(Action action : spec.actions()){
                    String port = "signal_" + action.id();
                    inputs.put(port, action.type());
                    defaults.put(port, "boolean".equals(action.type()) ? GraphValue.bool(false) : GraphValue.number(0));
                    groups.computeIfAbsent("signals", key -> new ArrayList<>()).add(port);
                }
                List<GraphNodePresentationRegistry.Section> sections = new ArrayList<>();
                groups.forEach((id, ports) -> sections.add(new GraphNodePresentationRegistry.Section(id, title(id), ports)));
                GraphApi.nodes().register(new GraphNodeDefinition(type, "flight_control", inputs,
                        Map.of("available", "boolean", "active", "boolean", "component", "string", "telemetry", "map",
                                "profile", "map", "status", "string"), true));
                GraphNodePresentationRegistry.register(type, new GraphNodePresentationRegistry.Presentation(defaults, sections, options));
                GraphApi.runtimes().register(type, (ctx, vals) -> ctx.service(GraphHostServices.NODE_OUTPUTS)
                        .map(outputs -> outputs.snapshot(ctx.nodeId()))
                        .orElse(Map.of("available", GraphValue.bool(false), "active", GraphValue.bool(false),
                                "component", GraphValue.string(ctx.nodeId()),
                                "profile", GraphValue.map(Map.of("component", GraphValue.string(ctx.nodeId()))),
                                "status", GraphValue.string("Unavailable"))));
            }
        }catch(java.io.IOException err){ throw new IllegalStateException("Cannot read Flight Control node schema", err); }
    }

    public static Spec get(String type){ return SPECS.get(type); }
    public static boolean isNode(String type){ return SPECS.containsKey(type); }

    // Present stable field names as readable graph labels
    public static String title(String id){
        StringBuilder res = new StringBuilder();
        for(String word : id.replace(PREFIX, "").split("_")){
            if(word.isBlank()) continue;
            if(!res.isEmpty()) res.append(' ');
            res.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return res.toString();
    }

    public record Spec(String path, String className, List<Setting> fields, List<String> config, List<Action> actions){ }
    public record Action(String id, String type){ }
    public record Setting(String name, String nativeName, String port, String type, Object defaultVal, String group, List<String> options){
        public GraphValue defaultValue(){
            return switch(type){
                case "boolean" -> GraphValue.bool(Boolean.TRUE.equals(defaultVal));
                case "number" -> GraphValue.number(defaultVal instanceof Number num ? num.doubleValue() : 0);
                case "string" -> GraphValue.string(defaultVal == null ? "" : defaultVal.toString());
                case "list" -> GraphValue.list(defaultVal instanceof List<?> list ? list : List.of());
                case "map" -> GraphValue.map(defaultVal instanceof Map<?, ?> map ? map : Map.of());
                default -> new GraphValue("any", null);
            };
        }
    }
}
