package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import ace.flight.navigation.NavigationProfileDefinition;
import ace.flight.navigation.NavigationProfileDraft;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Convert graph profile settings into Flight Control's validated editable records
public final class FlightControlProfiles{
    private FlightControlProfiles(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static NavigationProfileDraft draft(Map<String, GraphValue> vals, List<NavigationProfileDefinition.AfnChannel> channels){
        List<NavigationProfileDraft.PathDraft> paths = new ArrayList<>();
        for(GraphValue path : vals.get("paths").entries()) paths.add(path(path));
        if(paths.isEmpty()) paths.addAll(NavigationProfileDraft.defaultDraft().paths());
        GraphValue hover = vals.get("hover_attitude");
        return new NavigationProfileDraft(vals.get("profile_name").asString(), paths,
                NavigationProfileDraft.EndBehavior.valueOf(vals.get("end_behavior").asString().toUpperCase(Locale.ROOT)),
                new NavigationProfileDraft.HoverAttitudeDraft(number(hover, "pitch", 0), number(hover, "roll", 0), number(hover, "yaw", 0)), channels);
    }

    // Preserve all native route fields when the builder's GUI changes its template
    public static Map<String, GraphValue> values(NavigationProfileDraft draft, net.minecraft.core.HolderLookup.Provider provider){
        List<Map<String, Object>> paths = new ArrayList<>();
        for(var path : draft.paths()){
            Map<String, Object> val = new java.util.LinkedHashMap<>();
            val.put("x", path.x()); val.put("y", path.y()); val.put("z", path.z());
            val.put("pitch", axisValue(path.pitch())); val.put("roll", axisValue(path.roll())); val.put("yaw", axisValue(path.yaw()));
            val.put("linear_velocity_throttle", path.linearVelocityThrottle());
            val.put("angular_velocity_throttle", path.angularVelocityThrottle());
            val.put("x_reference", referenceValue(path.xReference()));
            val.put("y_reference", referenceValue(path.yReference()));
            val.put("z_reference", referenceValue(path.zReference()));
            if(path.flightPreset() instanceof NavigationProfileDefinition.OrbitFlightPreset orbit){
                val.put("flight_preset", Map.of("mode", "orbit", "radius", orbit.radius(), "direction", orbit.direction().name().toLowerCase(Locale.ROOT),
                        "normal", Map.of("x", orbit.normalX(), "y", orbit.normalY(), "z", orbit.normalZ())));
            }else val.put("flight_preset", Map.of("mode", "direct"));
            val.put("offset_frame", path.offsetFrame().orElse(""));
            val.put("attitude_control", path.attitudeControl().name().toLowerCase(Locale.ROOT));
            val.put("self_offset_frame", path.selfOffsetFrame());
            paths.add(val);
        }
        var hover = draft.hoverAttitude();
        return Map.of("profile_name", GraphValue.string(draft.profileName()), "paths", GraphValue.list(paths),
                "end_behavior", GraphValue.string(draft.endBehavior().name().toLowerCase(Locale.ROOT)),
                "hover_attitude", GraphValue.map(Map.of("pitch", hover.pitchDegrees(), "roll", hover.rollDegrees(), "yaw", hover.yawDegrees())),
                "afn_channels", GraphValue.list(draft.afnChannels().stream().map(channel -> Map.of("name", channel.name(),
                        "frequency", frequencyValue(channel.frequency(), provider))).toList()));
    }

    private static Map<String, Object> axisValue(NavigationProfileDraft.AxisDraft val){
        return Map.of("mode", val.mode().name().toLowerCase(Locale.ROOT), "value", val.value());
    }

    private static Map<String, Object> referenceValue(Optional<NavigationProfileDefinition.CoordinateVariableReference> val){
        if(val.isEmpty()) return Map.of();
        var ref = val.get();
        String source = ref instanceof NavigationProfileDefinition.SelfVariableReference ? "self"
                : ref instanceof NavigationProfileDefinition.SelfStaticVariableReference ? "self_static" : "afn";
        return Map.of("source", source, "component", ref.component().name().toLowerCase(Locale.ROOT),
                "channel", ref instanceof NavigationProfileDefinition.AfnVariableReference afn ? afn.channelName() : "");
    }

    private static Map<String, Object> frequencyValue(ace.flight.compat.afn.AfnFrequencyAddress val,
            net.minecraft.core.HolderLookup.Provider provider){
        return Map.of("first", selectorValue(val.first(), provider), "second", selectorValue(val.second(), provider));
    }

    private static Map<String, Object> selectorValue(ace.flight.compat.afn.AfnFrequencyAddress.FrequencySelector val,
            net.minecraft.core.HolderLookup.Provider provider){
        if(val.hasStackSnapshot()) return Map.of("item", val.itemId().toString(), "stack_nbt", val.stackSnapshot());
        return stackValue(val.toStack(), provider);
    }

    private static Map<String, Object> stackValue(ItemStack stack, net.minecraft.core.HolderLookup.Provider provider){
        return stack.isEmpty() ? Map.of("item", "minecraft:air") : Map.of("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                "stack_nbt", stack.save(provider).toString());
    }

    // Preserve native axis modes, route throttles, orbit guidance and coordinate references
    public static NavigationProfileDraft.PathDraft path(GraphValue val){
        GraphValue preset = val.member("flight_preset");
        NavigationProfileDefinition.FlightPreset flightPreset = NavigationProfileDefinition.DirectFlightPreset.INSTANCE;
        if("orbit".equals(preset.member("mode").asString())){
            GraphValue normal = preset.member("normal");
            flightPreset = new NavigationProfileDefinition.OrbitFlightPreset(number(preset, "radius", 10),
                    NavigationProfileDefinition.OrbitDirection.fromSerializedName(text(preset, "direction", "clockwise")),
                    number(normal, "x", 0), number(normal, "y", 1), number(normal, "z", 0));
        }
        String offsetFrame = val.member("offset_frame").asString();
        return new NavigationProfileDraft.PathDraft(number(val, "x", 0), number(val, "y", 0), number(val, "z", 0),
                axis(val.member("pitch")), axis(val.member("roll")), axis(val.member("yaw")),
                (int) number(val, "linear_velocity_throttle", NavigationProfileDefinition.DEFAULT_PATH_THROTTLE),
                (int) number(val, "angular_velocity_throttle", NavigationProfileDefinition.DEFAULT_PATH_THROTTLE),
                reference(val.member("x_reference")), reference(val.member("y_reference")), reference(val.member("z_reference")),
                flightPreset, offsetFrame.isBlank() ? Optional.empty() : Optional.of(offsetFrame),
                NavigationProfileDefinition.AttitudeControl.fromSerializedName(text(val, "attitude_control", "custom")),
                val.member("self_offset_frame").asBoolean());
    }

    // Build real frequency items so the native AFN service retains routing and authentication
    public static ItemStack frequencyItem(GraphValue val, net.minecraft.core.HolderLookup.Provider provider){
        String itemId = "string".equals(val.type()) ? val.asString() : text(val, "item", "minecraft:air");
        if(itemId.isBlank()) itemId = "minecraft:air";
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if(id == null) throw new IllegalArgumentException("Invalid AFN item " + itemId);
        if(!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unavailable AFN item " + itemId);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if(val.value() instanceof Map<?, ?> map && map.containsKey("dyed_color")){
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor((int) number(val, "dyed_color", -1), false));
        }
        String snapshot = val.member("stack_nbt").asString();
        if(!snapshot.isBlank()){
            try{
                var tag = net.minecraft.nbt.TagParser.parseTag(snapshot);
                stack = ItemStack.parseOptional(provider, tag);
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException err){ throw new IllegalArgumentException("Invalid AFN item data", err); }
        }
        return stack;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static NavigationProfileDraft.AxisDraft axis(GraphValue val){
        if(!(val.value() instanceof Map<?, ?>)) return NavigationProfileDraft.AxisDraft.fixedAngle(val.asNumber());
        return switch(text(val, "mode", "fixed_angle")){
            case "fixed_angle" -> NavigationProfileDraft.AxisDraft.fixedAngle(number(val, "value", number(val, "degrees", 0)));
            case "fixed_angular_velocity" -> NavigationProfileDraft.AxisDraft.fixedAngularVelocity(number(val, "value", number(val, "radians_per_second", 0)));
            case "velocity_direction" -> NavigationProfileDraft.AxisDraft.velocityDirection();
            default -> throw new IllegalArgumentException("Invalid profile axis mode");
        };
    }

    private static Optional<NavigationProfileDefinition.CoordinateVariableReference> reference(GraphValue val){
        if(!(val.value() instanceof Map<?, ?> map) || map.isEmpty()) return Optional.empty();
        var component = NavigationProfileDefinition.AfnVariableComponent.valueOf(text(val, "component", "value").toUpperCase(Locale.ROOT));
        return Optional.of(switch(text(val, "source", "afn")){
            case "self" -> NavigationProfileDefinition.SelfVariableReference.fromComponent(component);
            case "self_static" -> NavigationProfileDefinition.SelfStaticVariableReference.fromComponent(component);
            case "afn" -> new NavigationProfileDefinition.AfnVariableReference(val.member("channel").asString(), component);
            default -> throw new IllegalArgumentException("Invalid coordinate reference");
        });
    }

    private static String text(GraphValue val, String key, String fallback){
        String res = val.member(key).asString();
        return res.isBlank() ? fallback : res;
    }

    private static double number(GraphValue val, String key, double fallback){
        return val.value() instanceof Map<?, ?> map && map.containsKey(key) ? val.member(key).asNumber() : fallback;
    }
}
