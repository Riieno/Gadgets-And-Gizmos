package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Read optional graph data providers through guarded reflection and cache their resolved methods
public final class AdvancedGraphReflectiveData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<String> EXCLUDED = Set.of(
            "class", "level", "block_pos", "block_state", "type", "update_tag", "update_packet",
            "render_bounding_box", "display_name", "persistent_data", "model_data", "removed", "changed",
            "controller", "source", "network", "lazy_tick_rate", "extra_data", "width", "height",
            "assemble_next_tick", "stalled", "previous_angle", "animated_speed", "animated_offset",
            "fluid_level", "client_hold_lever_in_place", "parent", "plate_pos", "sub_level_id");
    private static final ClassValue<Map<String, ReadableAccessor>> READABLE_ACCESSORS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected Map<String, ReadableAccessor> computeValue(Class<?> type) {
            Map<String, ReadableAccessor> accessors = new LinkedHashMap<>();
            for (Method method : type.getMethods()) {
                String field = getterField(method);
                String valueType = valueType(method.getReturnType());
                if (field != null && valueType != null) {
                    accessors.putIfAbsent(field, new ReadableAccessor(method, valueType));
                }
            }
            return Collections.unmodifiableMap(accessors);
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph reflective data
    private AdvancedGraphReflectiveData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the readable data
    public static Map<String, String> readableData(Object target) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (target == null) return fields;
        for (Map.Entry<String, ReadableAccessor> entry : READABLE_ACCESSORS.get(target.getClass()).entrySet()) {
            if (!EXCLUDED.contains(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue().type());
            }
        }
        return fields;
    }

    // Get the writable data
    public static Map<String, String> writableData(Object target) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (target == null) return fields;
        for (Method method : target.getClass().getMethods()) {
            String field = setterField(method);
            if (field == null) continue;
            if (method.getParameterCount() == 1) {
                String type = valueType(method.getParameterTypes()[0]);
                if (type != null) fields.putIfAbsent(field, type);
                continue;
            }
            if (method.getParameterCount() == 2 && selectorValues(method.getParameterTypes()[0]) != null) {
                String type = valueType(method.getParameterTypes()[1]);
                if (type == null) continue;
                for (Object selector : selectorValues(method.getParameterTypes()[0])) {
                    fields.putIfAbsent(field + "_" + selectorName(selector), type);
                }
            }
            if (method.getParameterCount() > 1 && allParametersSupported(method)) {
                fields.putIfAbsent("call_" + field, "map");
            }
        }
        return fields;
    }

    // Get the writable options
    public static CompoundTag writableOptions(Object target) {
        CompoundTag opts = new CompoundTag();
        if (target == null) return opts;
        for (Method method : target.getClass().getMethods()) {
            String field = setterField(method);
            if (field == null || method.getParameterCount() != 1) continue;
            Object[] values = selectorValues(method.getParameterTypes()[0]);
            if (values == null) continue;
            ListTag choices = new ListTag();
            for (Object val : values) choices.add(StringTag.valueOf(selectorName(val)));
            opts.put(field, choices);
        }
        return opts;
    }

    // Read the advanced graph reflective data
    public static AdvancedGraphDocument.Value read(Object target, String field) {
        if (target == null || field == null) return null;
        ReadableAccessor accessor = READABLE_ACCESSORS.get(target.getClass()).get(field);
        if (accessor == null) return null;
        try {
            return toValue(accessor.method().invoke(target));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    // Write the advanced graph reflective data
    public static boolean write(Object target, String field, AdvancedGraphDocument.Value val) {
        if (target == null || field == null || val == null || !writableData(target).containsKey(field)) return false;
        for (Method method : target.getClass().getMethods()) {
            String methodField = setterField(method);
            if (methodField == null) continue;
            try {
                Object res;
                if (method.getParameterCount() == 1 && field.equals(methodField)) {
                    Object argument = fromValue(val, method.getParameterTypes()[0]);
                    if (argument == null && method.getParameterTypes()[0].isPrimitive()) continue;
                    res = method.invoke(target, argument);
                } else if (method.getParameterCount() == 2 && field.startsWith(methodField + "_")) {
                    Object selector = selectorValue(method.getParameterTypes()[0], field.substring(methodField.length() + 1));
                    Object argument = fromValue(val, method.getParameterTypes()[1]);
                    if (selector == null || argument == null && method.getParameterTypes()[1].isPrimitive()) continue;
                    res = method.invoke(target, selector, argument);
                } else if (method.getParameterCount() > 1 && field.equals("call_" + methodField)
                        && allParametersSupported(method)) {
                    Object[] args = new Object[method.getParameterCount()];
                    for (int idx = 0; idx < args.length; idx++) {
                        args[idx] = fromMapValue(val.payload(), "arg" + idx, method.getParameterTypes()[idx]);
                    }
                    res = method.invoke(target, args);
                } else {
                    continue;
                }
                return !(res instanceof Boolean bool) || bool;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    // Get a field name from its getter
    private static String getterField(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                || method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) return null;
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) return snake(name.substring(3));
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return snake(name.substring(2));
        }
        return null;
    }

    // Get the setter field
    private static String setterField(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                || method.getParameterCount() < 1 || method.getParameterCount() > 2) return null;
        String name = method.getName();
        String field = name.startsWith("set") && name.length() > 3 ? snake(name.substring(3)) : null;
        return field == null || EXCLUDED.contains(field) ? null : field;
    }

    // Get the snake
    private static String snake(String val) {
        StringBuilder normalized = new StringBuilder(val.length() + 4);
        for (int idx = 0; idx < val.length(); idx++) {
            char current = val.charAt(idx);
            if (idx > 0 && Character.isUpperCase(current)) {
                char prev = val.charAt(idx - 1);
                if (Character.isLowerCase(prev) || Character.isDigit(prev)) {
                    normalized.append('_');
                }
            }
            normalized.append(current);
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    // Get the value type
    private static String valueType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class && type != char.class) return "number";
        if (type == Direction.class) return "direction";
        if ("de.mrjulsen.mcdragonlib.util.DLColor".equals(type.getName())) return "number";
        if (type == String.class || type.isEnum() || type == ResourceLocation.class || type == UUID.class
                || type == Block.class || type == Item.class || type == ItemStack.class || type == BlockState.class
                || type == Component.class) return "string";
        if (type == Vec3.class || type == CompoundTag.class || type == BlockPos.class) return "map";
        return null;
    }

    // Convert the advanced graph reflective data to value
    private static AdvancedGraphDocument.Value toValue(Object val) {
        if (val instanceof Boolean bool) return AdvancedGraphDocument.Value.bool(bool);
        if (val instanceof Number num) return AdvancedGraphDocument.Value.number(num.doubleValue());
        if (val instanceof Direction dir) return AdvancedGraphDocument.Value.direction(dir.getName());
        if ("de.mrjulsen.mcdragonlib.util.DLColor".equals(val == null ? "" : val.getClass().getName())) {
            try {
                return AdvancedGraphDocument.Value.number(((Number) val.getClass().getMethod("getAsARGB").invoke(val)).doubleValue());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return AdvancedGraphDocument.Value.number(0);
            }
        }
        if (val instanceof Enum<?> enumeration) return AdvancedGraphDocument.Value.string(enumeration.name().toLowerCase(Locale.ROOT));
        if (val instanceof Block block) return AdvancedGraphDocument.Value.string(BuiltInRegistries.BLOCK.getKey(block).toString());
        if (val instanceof Item item) return AdvancedGraphDocument.Value.string(BuiltInRegistries.ITEM.getKey(item).toString());
        if (val instanceof ItemStack stack) return AdvancedGraphDocument.Value.string(
                stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
        if (val instanceof BlockState state) return AdvancedGraphDocument.Value.string(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (val instanceof Component component) return AdvancedGraphDocument.Value.string(component.getString());
        if (val instanceof CompoundTag tag) return AdvancedGraphDocument.Value.map(tag);
        if (val instanceof BlockPos pos) {
            CompoundTag data = new CompoundTag();
            data.putInt("x", pos.getX());
            data.putInt("y", pos.getY());
            data.putInt("z", pos.getZ());
            return AdvancedGraphDocument.Value.map(data);
        }
        if (val instanceof Vec3 vector) {
            CompoundTag data = new CompoundTag();
            data.putDouble("x", vector.x);
            data.putDouble("y", vector.y);
            data.putDouble("z", vector.z);
            return AdvancedGraphDocument.Value.map(data);
        }
        return AdvancedGraphDocument.Value.string(val == null ? "" : val.toString());
    }

    // Create the advanced graph reflective data from value
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object fromValue(AdvancedGraphDocument.Value val, Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return val.asBoolean();
        if (type == byte.class || type == Byte.class) return (byte) val.asNumber();
        if (type == short.class || type == Short.class) return (short) val.asNumber();
        if (type == int.class || type == Integer.class) return (int) val.asNumber();
        if (type == long.class || type == Long.class) return (long) val.asNumber();
        if (type == float.class || type == Float.class) return (float) val.asNumber();
        if (type == double.class || type == Double.class) return val.asNumber();
        if (type == String.class) return val.asString();
        if (type == ResourceLocation.class) return ResourceLocation.tryParse(val.asString());
        if (type == UUID.class) return UUID.fromString(val.asString());
        if (type == Block.class) return BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(val.asString()));
        if (type == Item.class) return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(val.asString()));
        if (type == ItemStack.class) {
            String[] parts = val.asString().trim().split("\\s+x\\s+", 2);
            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            ItemStack stack = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty() && parts.length == 2) {
                try {
                    stack.setCount(Math.max(1, Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {
                }
            }
            return stack;
        }
        if (type == BlockState.class) {
            ResourceLocation id = ResourceLocation.tryParse(val.asString());
            return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).orElse(null);
        }
        if (type == Component.class) return Component.literal(val.asString());
        if ("de.mrjulsen.mcdragonlib.util.DLColor".equals(type.getName())) {
            try {
                return type.getMethod("fromInt", int.class).invoke(null, (int) val.asNumber());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        if (type.isEnum()) return Enum.valueOf((Class<? extends Enum>) type, val.asString().trim().toUpperCase(Locale.ROOT));
        if (type == CompoundTag.class) return val.payload().copy();
        if (type == BlockPos.class) {
            CompoundTag data = val.payload();
            return new BlockPos(data.getInt("x"), data.getInt("y"), data.getInt("z"));
        }
        if (type == Vec3.class) {
            CompoundTag data = val.payload();
            return new Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
        }
        return null;
    }

    // Get the selector values
    private static Object[] selectorValues(Class<?> type) {
        if (type == Direction.class) return Direction.values();
        return type.isEnum() ? type.getEnumConstants() : null;
    }

    // Get the selector value
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object selectorValue(Class<?> type, String name) {
        if (type == Direction.class) return Direction.byName(name);
        if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<? extends Enum>) type, name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    // Get the selector name
    private static String selectorName(Object val) {
        if (val instanceof Direction dir) return dir.getName();
        if (val instanceof Enum<?> enumeration) return enumeration.name().toLowerCase(Locale.ROOT);
        return String.valueOf(val).toLowerCase(Locale.ROOT);
    }

    // Check if all parameters supported
    private static boolean allParametersSupported(Method method) {
        for (Class<?> type : method.getParameterTypes()) {
            if (valueType(type) == null) return false;
        }
        return true;
    }

    // Create the advanced graph reflective data from map value
    private static Object fromMapValue(CompoundTag map, String key, Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return map.getBoolean(key);
        if (type == byte.class || type == Byte.class) return map.getByte(key);
        if (type == short.class || type == Short.class) return map.getShort(key);
        if (type == int.class || type == Integer.class) return map.getInt(key);
        if (type == long.class || type == Long.class) return map.getLong(key);
        if (type == float.class || type == Float.class) return map.getFloat(key);
        if (type == double.class || type == Double.class) return map.getDouble(key);
        if (type == CompoundTag.class) return map.getCompound(key).copy();
        if (type == Vec3.class || type == BlockPos.class) {
            return fromValue(AdvancedGraphDocument.Value.map(map.getCompound(key)), type);
        }
        return fromValue(AdvancedGraphDocument.Value.string(map.getString(key)), type);
    }

    // Store the readable accessor
    private record ReadableAccessor(Method method, String type) {
    }
}
