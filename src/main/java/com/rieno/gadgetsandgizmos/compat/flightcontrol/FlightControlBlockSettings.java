package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import ace.flight.block.*;
import ace.flight.compat.afn.FccAfnHost;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Read the same native settings edited by block menus
final class FlightControlBlockSettings{
    private FlightControlBlockSettings(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static Map<String, GraphValue> read(BlockEntity component, FlightControlNodes.Spec spec){
        Map<String, GraphValue> res = new LinkedHashMap<>();
        for(var setting : spec.fields()){
            BlockEntity target = component;
            String name = setting.nativeName().isBlank() ? setting.name() : setting.nativeName();
            if(component instanceof CreativeFlightControlComputerBlockEntity creative && setting.port().startsWith("mouse_")){
                target = creative.getFlightTerminal();
                if(setting.nativeName().isBlank()) name = camel(setting.port().substring(6));
            }
            Object val = property(target, name);
            if(val == null) val = property(target, camel(setting.port()));
            if(val instanceof Number || val instanceof Boolean || val instanceof String || val instanceof Enum<?>){
                if(val instanceof Enum<?> option) val = "number".equals(setting.type()) ? option.ordinal() : option.name().toLowerCase(Locale.ROOT);
                if("string".equals(setting.type()) && val instanceof Number num){
                    String port = setting.port();
                    if("mode".equals(port) && component instanceof FlightControlComputerBlockEntity) val = num.intValue() == 0 ? "work" : "debug";
                    else if(setting.options() != null && num.intValue() >= 0 && num.intValue() < setting.options().size()) val = setting.options().get(num.intValue());
                }
                res.put(setting.port(), GraphValue.of(val));
            }
        }
        if(component instanceof FlightControlComputerBlockEntity computer){
            res.put("enabled", GraphValue.bool(computer.working));
            res.put("fixed_wing_takeoff_height", GraphValue.number(computer.getFixedWingFlightHeights().takeoff()));
            res.put("fixed_wing_approach_height", GraphValue.number(computer.getFixedWingFlightHeights().approach()));
        }
        if(component instanceof CreativeFlightControlComputerBlockEntity creative){
            res.put("enabled", GraphValue.bool(creative.isComputerDefaultEnabled()));
            Object config = property(creative, "fixedWingConfig");
            if(config != null) for(var setting : spec.fields()){
                if(!setting.port().startsWith("fixed_wing_")) continue;
                Object val = property(config, camel(setting.port().substring(11)));
                if(val != null) res.put(setting.port(), GraphValue.of(val));
            }
        }
        if(component instanceof ForwardIndicatorBlockEntity indicator){
            var val = property(indicator, "recordedFacing");
            if(val instanceof Enum<?> facing) res.put("body_forward_facing", GraphValue.string(facing.name().toLowerCase(Locale.ROOT)));
        }
        if(component instanceof NavigationProfileBuilderBlockEntity builder){
            res.putAll(FlightControlProfiles.values(builder.getEditorSnapshot().draft(), component.getLevel().registryAccess()));
        }
        if(component instanceof NavigationTerminalBlockEntity terminal){
            ItemStack card = terminal.getDepotBehaviour().getHeldItemStack();
            res.put("profile", GraphValue.map(item(card, component)));
            res.put("enabled", GraphValue.bool(terminal.getEditorSnapshot().defaultEnabled()));
            res.put("afn_auto_infinite_range", GraphValue.bool(terminal.getEditorSnapshot().autoAfnInfiniteRange()));
            Map<String, Object> ranges = new LinkedHashMap<>();
            terminal.getEditorSnapshot().afnChannels().forEach(channel -> ranges.put(channel.id().toString(), channel.infiniteRange()));
            res.put("afn_channel_ranges", GraphValue.map(ranges));
            res.put("redstone_outputs", GraphValue.list(terminal.redstoneOutput().rules().stream().map(rule -> Map.of(
                    "kind", rule.kind().name().toLowerCase(Locale.ROOT), "waypoint", rule.waypoint(), "threshold", rule.threshold(),
                    "first", item(rule.first(), component), "second", item(rule.second(), component))).toList()));
        }
        if(component instanceof AerodynamicTrailCreatorBlockEntity){
            for(String port : new String[]{"length", "thickness", "min_speed"}){
                Object val = property(component, camel(port) + "Tenths");
                if(val instanceof Number num) res.put(port, GraphValue.number(num.doubleValue() / 10D));
            }
            Object density = property(component, "densityPercent");
            if(density instanceof Number num) res.put("density_percent", GraphValue.number(num.doubleValue()));
            Object enabled = property(component, "defaultEnabled");
            if(enabled instanceof Boolean val) res.put("enabled", GraphValue.bool(val));
        }
        if(component instanceof FccAfnHost nativeHost){
            var afn = nativeHost.getAfnBroadcaster();
            res.put("afn_frequency", GraphValue.map(Map.of("first", item(afn.getAfnFrequencyItems().getItem(0), component),
                    "second", item(afn.getAfnFrequencyItems().getItem(1), component))));
            res.put("afn_broadcast_enabled", GraphValue.bool(afn.isAfnBroadcastEnabled()));
            res.put("afn_broadcast_pose", GraphValue.bool(afn.isAfnBroadcastPose()));
            res.put("afn_infinite_range", GraphValue.bool(afn.isAfnBroadcastInfiniteRange()));
        }
        res.keySet().retainAll(spec.fields().stream().map(FlightControlNodes.Setting::port).collect(java.util.stream.Collectors.toSet()));
        return res;
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static Object property(Object target, String name){
        if(name == null || name.isBlank()) return null;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for(String method : new String[]{name, "get" + suffix, "is" + suffix}){
            try{ return target.getClass().getMethod(method).invoke(target); }
            catch(ReflectiveOperationException ignored){}
        }
        for(Class<?> type = target.getClass(); type != null; type = type.getSuperclass()){
            try{
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            }catch(ReflectiveOperationException ignored){}
        }
        return null;
    }

    static Map<String, Object> item(ItemStack stack, BlockEntity component){
        if(stack.isEmpty()) return Map.of("item", "minecraft:air");
        return Map.of("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                "stack_nbt", stack.save(component.getLevel().registryAccess()).toString());
    }

    private static String camel(String val){
        StringBuilder res = new StringBuilder();
        boolean upper = false;
        for(char ch : val.toCharArray()){
            if(ch == '_'){ upper = true; continue; }
            res.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return res.toString();
    }
}
