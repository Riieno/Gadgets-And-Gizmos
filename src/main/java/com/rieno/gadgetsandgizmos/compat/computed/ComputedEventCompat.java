package com.rieno.gadgetsandgizmos.compat.computed;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEvent;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventBus;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// Bridge optional Computed events into the addon without loading its classes when it is absent
public final class ComputedEventCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EVENT_BUS = "event_bus";
    private static final int MAX_VALUE_DEPTH = 16;
    private static final int MAX_TABLE_ENTRIES = 1024;
    private static final Map<BlockEntity, WeakReference<Object>> COMPUTERS = new WeakHashMap<>();
    private static final AtomicBoolean SEND_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RECEIVE_FAILURE_LOGGED = new AtomicBoolean();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared Lua API
    private static volatile LuaApi luaApi;
    private static NamedEventBus.Subscription transportSubscription;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computed event compat
    private ComputedEventCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Track the computer
    public static void trackComputer(BlockEntity computer, Object scheduler) {
        if (computer == null || scheduler == null) {
            return;
        }
        synchronized (COMPUTERS) {
            installTransport();
            COMPUTERS.put(computer, new WeakReference<>(scheduler));
        }
    }

    // Untrack the computer
    public static void untrackComputer(BlockEntity computer) {
        if (computer == null) {
            return;
        }
        synchronized (COMPUTERS) {
            COMPUTERS.remove(computer);
        }
    }

    // Forward the computed
    public static void forwardFromComputed(Object endpointHost, String emittedEvent,
                                           List<?> args) {
        if (!EVENT_BUS.equals(emittedEvent) || args == null || args.size() < 2
                || !(endpointHost instanceof BlockEntity src)
                || src.getLevel() == null || src.getLevel().isClientSide
                || src.getLevel().getServer() == null) {
            return;
        }

        try {
            LuaApi api = api(src.getClass().getClassLoader());
            String name = normalizeEventName(api.stringValue(args.get(0)));
            if (name.isBlank()) {
                return;
            }
            Object payload = api.toPlain(args.get(1), 0);
            AdvancedGraphDocument.Value data = fromComputedPayload(payload);
            MinecraftServer server = src.getLevel().getServer();
            Runnable forward = () -> {
                if (src.isRemoved() || src.getLevel() == null
                        || src.getLevel().getServer() != server) {
                    return;
                }
                NamedEventBus.publish(NamedEvent.of(source(src), name,
                        ComputedEventCompat.toLibraryValue(data), 0));
            };
            if (server.isSameThread()) {
                forward.run();
            } else {
                server.execute(forward);
            }
        } catch (ReflectiveOperationException | RuntimeException err) {
            if (RECEIVE_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("[CT][Compat] Could not translate a Computed custom event", err);
            }
        }
    }

    // Install the Computed endpoint on the shared named event transport bus
    private static synchronized void installTransport() {
        if (transportSubscription == null) {
            transportSubscription = NamedEventBus.subscribe("computed:event_bus",
                    ComputedEventCompat::publishToComputed);
        }
    }

    // Publish one shared named event to every matching Computed computer
    private static void publishToComputed(NamedEvent event) {
        if (event == null || event.source() == null) {
            return;
        }
        MinecraftServer server = event.source().server();
        Runnable publish = () -> publishToComputedNow(
                event, server);
        if (server.isSameThread()) {
            publish.run();
        } else {
            server.execute(publish);
        }
    }

    // Publish the computed now
    private static void publishToComputedNow(NamedEvent event, MinecraftServer server) {
        AdvancedGraphDocument.Value data = fromLibraryValue(event.data());
        List<ComputerTarget> targets = trackedComputers();
        for (ComputerTarget target : targets) {
            BlockEntity computer = target.computer();
            if (computer.isRemoved() || computer.getLevel() == null
                    || computer.getLevel().getServer() != server) {
                continue;
            }
            if (event.source().endpointId().equals(endpointId(computer))) {
                continue;
            }
            if (event.maximumDistance() > 0) {
                boolean sameDimension = event.source().dimension()
                        .equals(computer.getLevel().dimension());
                Vec3 computerPosition = SimulatedHelper.toGlobalWorldPosition(
                        computer, Vec3.atCenterOf(computer.getBlockPos()));
                double maximumDistanceSquared = event.maximumDistance() * (double) event.maximumDistance();
                if (!sameDimension || event.source().position() == null
                        || event.source().position().distanceToSqr(computerPosition) > maximumDistanceSquared) {
                    continue;
                }
            }
            try {
                emit(target.scheduler(), event.name(), data);
            } catch (ReflectiveOperationException | RuntimeException err) {
                if (SEND_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.warn("[CT][Compat] Could not deliver a named event to Computed", err);
                }
            }
        }
    }

    // Get the tracked computers
    private static List<ComputerTarget> trackedComputers() {
        ArrayList<ComputerTarget> targets = new ArrayList<>();
        synchronized (COMPUTERS) {
            COMPUTERS.entrySet().removeIf(entry ->
                    entry.getKey() == null || entry.getValue() == null
                            || entry.getValue().get() == null);
            COMPUTERS.forEach((computer, schedulerReference) -> {
                Object scheduler = schedulerReference.get();
                if (scheduler != null) {
                    targets.add(new ComputerTarget(computer, scheduler));
                }
            });
        }
        return targets;
    }

    // Convert advanced graph data to the reusable named event value boundary
    private static GraphValue toLibraryValue(AdvancedGraphDocument.Value data) {
        AdvancedGraphDocument.Value normalized = data == null
                ? AdvancedGraphDocument.Value.number(0)
                : data;
        return new GraphValue(normalized.type(), toPlainValue(normalized));
    }

    // Convert reusable named event data to the advanced graph runtime value boundary
    private static AdvancedGraphDocument.Value fromLibraryValue(GraphValue data) {
        return fromPlainValue(data == null ? 0.0D : data.value());
    }

    // Create a stable shared named event source for one Computed computer
    private static NamedEventSource source(BlockEntity computer) {
        return new NamedEventSource(computer.getLevel().getServer(),
                computer.getLevel().dimension(), SimulatedHelper.toGlobalWorldPosition(
                computer, Vec3.atCenterOf(computer.getBlockPos())), endpointId(computer));
    }

    // Create one stable endpoint id for a Computed computer
    private static String endpointId(BlockEntity computer) {
        return "computed/" + computer.getLevel().dimension().location()
                + "/" + computer.getBlockPos().asLong();
    }

    // Emit the computed event compat
    private static void emit(Object scheduler, String name, AdvancedGraphDocument.Value data)
            throws ReflectiveOperationException {
        LuaApi api = api(scheduler.getClass().getClassLoader());
        Object payload = api.fromPlain(toComputedPayload(data), 0);
        Object args = Array.newInstance(api.valueClass(), 2);
        Array.set(args, 0, api.string(name));
        Array.set(args, 1, payload);
        Method emit = scheduler.getClass().getMethod(
                "emit", String.class, args.getClass());
        emit.invoke(scheduler, EVENT_BUS, args);
    }

    // Convert the computed event compat to plain value
    static Object toPlainValue(AdvancedGraphDocument.Value val) {
        return toPlainValue(val, 0);
    }

    // Convert the computed event compat to plain value
    private static Object toPlainValue(AdvancedGraphDocument.Value val, int depth) {
        requireDepth(depth);
        if (val == null) {
            return 0.0D;
        }
        return switch (val.type()) {
            case "boolean" -> val.asBoolean();
            case "number" -> Double.isFinite(val.asNumber()) ? val.asNumber() : 0.0D;
            case "string", "direction" -> val.asString();
            case "list" -> listToPlain(val.payload(), depth + 1);
            case "map" -> mapToPlain(val.payload(), depth + 1);
            default -> nbtToPlain(val.payload(), depth + 1);
        };
    }

    // Create the computed event compat from plain value
    static AdvancedGraphDocument.Value fromPlainValue(Object val) {
        return fromPlainValue(val, 0);
    }

    // Create the computed event compat from plain value
    private static AdvancedGraphDocument.Value fromPlainValue(Object val, int depth) {
        requireDepth(depth);
        if (val instanceof Boolean bool) {
            return AdvancedGraphDocument.Value.bool(bool);
        }
        if (val instanceof Number num) {
            double converted = num.doubleValue();
            return AdvancedGraphDocument.Value.number(Double.isFinite(converted) ? converted : 0.0D);
        }
        if (val instanceof List<?> list) {
            requireTableSize(list.size());
            CompoundTag entries = new CompoundTag();
            for (int idx = 0; idx < list.size(); idx++) {
                entries.put(Integer.toString(idx),
                        encodeValue(fromPlainValue(list.get(idx), depth + 1)));
            }
            return AdvancedGraphDocument.Value.list(entries);
        }
        if (val instanceof Map<?, ?> map) {
            requireTableSize(map.size());
            CompoundTag entries = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.put(String.valueOf(entry.getKey()),
                        encodeValue(fromPlainValue(entry.getValue(), depth + 1)));
            }
            return AdvancedGraphDocument.Value.map(entries);
        }
        return AdvancedGraphDocument.Value.string(val == null ? "" : String.valueOf(val));
    }

    // Create the computed event compat from computed payload
    static AdvancedGraphDocument.Value fromComputedPayload(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return fromPlainValue(payload);
        }
        ArrayList<IndexedValue> fields = new ArrayList<>();
        boolean onlyComputedFields = !map.isEmpty();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            int idx = computedDataIndex(String.valueOf(entry.getKey()));
            if (idx < 1) {
                onlyComputedFields = false;
                break;
            }
            fields.add(new IndexedValue(idx, entry.getValue()));
        }
        if (onlyComputedFields && isContiguousDataFields(fields)) {
            fields.sort(Comparator.comparingInt(IndexedValue::index));
            if (fields.size() == 1) {
                return fromPlainValue(fields.getFirst().value());
            }
            return fromPlainValue(fields.stream().map(IndexedValue::value).toList());
        }
        if (map.containsKey("data")) {
            return fromPlainValue(map.get("data"));
        }
        return fromPlainValue(payload);
    }

    // Convert the computed event compat to computed payload
    static Map<String, Object> toComputedPayload(AdvancedGraphDocument.Value data) {
        return Map.of("data_1", toPlainValue(data));
    }

    // Normalize the event name
    static String normalizeEventName(String name) {
        return name == null ? "" : name.trim();
    }

    // Get the computed data index
    private static int computedDataIndex(String key) {
        if (key == null || !key.startsWith("data_")) {
            return -1;
        }
        try {
            return Integer.parseInt(key.substring("data_".length()));
        } catch (NumberFormatException err) {
            return -1;
        }
    }

    // Check if the data fields are contiguous
    private static boolean isContiguousDataFields(List<IndexedValue> fields) {
        if (fields.isEmpty()) {
            return false;
        }
        fields.sort(Comparator.comparingInt(IndexedValue::index));
        for (int idx = 0; idx < fields.size(); idx++) {
            if (fields.get(idx).index() != idx + 1) {
                return false;
            }
        }
        return true;
    }

    // Get the list to plain
    private static Object listToPlain(CompoundTag payload, int depth) {
        ArrayList<String> keys = new ArrayList<>(payload.getAllKeys());
        keys.removeIf(key -> !isNonNegativeInteger(key));
        requireTableSize(keys.size());
        keys.sort(Comparator.comparingInt(Integer::parseInt));
        ArrayList<Object> res = new ArrayList<>(keys.size());
        for (String key : keys) {
            res.add(toPlainValue(decodeValue(payload.getCompound(key)), depth));
        }
        return res;
    }

    // Map the plain
    private static Object mapToPlain(CompoundTag payload, int depth) {
        ArrayList<String> keys = new ArrayList<>(payload.getAllKeys());
        requireTableSize(keys.size());
        keys.sort(String::compareTo);
        LinkedHashMap<String, Object> res = new LinkedHashMap<>();
        for (String key : keys) {
            res.put(key, toPlainValue(decodeValue(payload.getCompound(key)), depth));
        }
        return res;
    }

    // Get the NBT to plain
    private static Object nbtToPlain(Tag tag, int depth) {
        requireDepth(depth);
        if (tag == null) {
            return "";
        }
        if (tag instanceof CompoundTag compound) {
            ArrayList<String> keys = new ArrayList<>(compound.getAllKeys());
            requireTableSize(keys.size());
            keys.sort(String::compareTo);
            LinkedHashMap<String, Object> res = new LinkedHashMap<>();
            for (String key : keys) {
                res.put(key, nbtToPlain(compound.get(key), depth + 1));
            }
            return res;
        }
        if (tag instanceof ListTag list) {
            requireTableSize(list.size());
            ArrayList<Object> res = new ArrayList<>(list.size());
            for (int idx = 0; idx < list.size(); idx++) {
                res.add(nbtToPlain(list.get(idx), depth + 1));
            }
            return res;
        }
        if (tag instanceof NumericTag numeric) {
            return tag.getId() == Tag.TAG_BYTE
                    ? numeric.getAsByte() != 0
                    : numeric.getAsDouble();
        }
        return tag.getAsString();
    }

    // Encode the value
    private static CompoundTag encodeValue(AdvancedGraphDocument.Value val) {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("Type", val.type());
        encoded.put("Payload", val.payload().copy());
        return encoded;
    }

    // Decode the value
    private static AdvancedGraphDocument.Value decodeValue(CompoundTag encoded) {
        if (encoded == null || !encoded.contains("Type", Tag.TAG_STRING)) {
            return AdvancedGraphDocument.Value.string("");
        }
        return new AdvancedGraphDocument.Value(
                encoded.getString("Type"), encoded.getCompound("Payload"));
    }

    // Check if this is a non-negative integer
    private static boolean isNonNegativeInteger(String val) {
        try {
            return Integer.parseInt(val) >= 0;
        } catch (NumberFormatException err) {
            return false;
        }
    }

    // Require the depth
    private static void requireDepth(int depth) {
        if (depth > MAX_VALUE_DEPTH) {
            throw new IllegalArgumentException(
                    "Computed event data exceeds the maximum depth of " + MAX_VALUE_DEPTH);
        }
    }

    // Require the table size
    private static void requireTableSize(int size) {
        if (size > MAX_TABLE_ENTRIES) {
            throw new IllegalArgumentException(
                    "Computed event data exceeds " + MAX_TABLE_ENTRIES + " table entries");
        }
    }

    // Get the API
    private static LuaApi api(ClassLoader classLoader) throws ReflectiveOperationException {
        LuaApi cached = luaApi;
        if (cached != null) {
            return cached;
        }
        synchronized (ComputedEventCompat.class) {
            if (luaApi == null) {
                luaApi = new LuaApi(classLoader);
            }
            return luaApi;
        }
    }

    // Store the computer target
    private record ComputerTarget(BlockEntity computer, Object scheduler) {
    }

    // Expose Lua
    private static final class LuaApi {
        // Value class
        private final Class<?> valueClass;
        // Resolved table constructor
        private final Constructor<?> tableConstructor;
        // Resolved nil field
        private final Field nilField;
        // Resolved boolean value of method
        private final Method booleanValueOf;
        // Resolved double value of method
        private final Method doubleValueOf;
        // Resolved string value of method
        private final Method stringValueOf;
        // Resolved set string method
        private final Method setString;
        // Resolved set integer method
        private final Method setInteger;
        // Resolved is nil method
        private final Method isNil;
        // Resolved is boolean method
        private final Method isBoolean;
        // Resolved is number method
        private final Method isNumber;
        // Resolved is string method
        private final Method isString;
        // Resolved is table method
        private final Method isTable;
        // Resolved to boolean method
        private final Method toBoolean;
        // Resolved to double method
        private final Method toDouble;
        // Resolved to java string method
        private final Method toJavaString;
        // Resolved keys method
        private final Method keys;
        // Resolved get method
        private final Method get;

        // Initialize the Lua API
        private LuaApi(ClassLoader classLoader) throws ReflectiveOperationException {
            valueClass = Class.forName("org.luaj.vm2.LuaValue", true, classLoader);
            Class<?> tableClass = Class.forName("org.luaj.vm2.LuaTable", true, classLoader);
            tableConstructor = tableClass.getConstructor();
            nilField = valueClass.getField("NIL");
            booleanValueOf = valueClass.getMethod("valueOf", boolean.class);
            doubleValueOf = valueClass.getMethod("valueOf", double.class);
            stringValueOf = valueClass.getMethod("valueOf", String.class);
            setString = tableClass.getMethod("set", String.class, valueClass);
            setInteger = tableClass.getMethod("set", int.class, valueClass);
            isNil = valueClass.getMethod("isnil");
            isBoolean = valueClass.getMethod("isboolean");
            isNumber = valueClass.getMethod("isnumber");
            isString = valueClass.getMethod("isstring");
            isTable = valueClass.getMethod("istable");
            toBoolean = valueClass.getMethod("toboolean");
            toDouble = valueClass.getMethod("todouble");
            toJavaString = valueClass.getMethod("tojstring");
            keys = tableClass.getMethod("keys");
            get = tableClass.getMethod("get", valueClass);
        }

        // Get the value class
        private Class<?> valueClass() {
            return valueClass;
        }

        // Create the table
        private Object newTable() throws ReflectiveOperationException {
            return tableConstructor.newInstance();
        }

        // Get the string
        private Object string(String val) throws ReflectiveOperationException {
            return stringValueOf.invoke(null, val);
        }

        // Get the string value
        private String stringValue(Object val) throws ReflectiveOperationException {
            return val != null && (boolean) isString.invoke(val)
                    ? (String) toJavaString.invoke(val)
                    : "";
        }

        // Set the Lua API
        private void set(Object table, String key, Object val)
                throws ReflectiveOperationException {
            setString.invoke(table, key, val);
        }

        // Create the Lua API from plain
        private Object fromPlain(Object val, int depth) throws ReflectiveOperationException {
            requireDepth(depth);
            if (val == null) {
                return nilField.get(null);
            }
            if (val instanceof Boolean bool) {
                return booleanValueOf.invoke(null, bool);
            }
            if (val instanceof Number num) {
                double converted = num.doubleValue();
                return doubleValueOf.invoke(null, Double.isFinite(converted) ? converted : 0.0D);
            }
            if (val instanceof List<?> list) {
                requireTableSize(list.size());
                Object table = newTable();
                for (int idx = 0; idx < list.size(); idx++) {
                    setInteger.invoke(table, idx + 1, fromPlain(list.get(idx), depth + 1));
                }
                return table;
            }
            if (val instanceof Map<?, ?> map) {
                requireTableSize(map.size());
                Object table = newTable();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    set(table, String.valueOf(entry.getKey()),
                            fromPlain(entry.getValue(), depth + 1));
                }
                return table;
            }
            return string(String.valueOf(val));
        }

        // Convert the Lua API to plain
        private Object toPlain(Object val, int depth) throws ReflectiveOperationException {
            requireDepth(depth);
            if (val == null || (boolean) isNil.invoke(val)) {
                return null;
            }
            if ((boolean) isBoolean.invoke(val)) {
                return toBoolean.invoke(val);
            }
            if ((boolean) isNumber.invoke(val)) {
                return toDouble.invoke(val);
            }
            if ((boolean) isString.invoke(val)) {
                return toJavaString.invoke(val);
            }
            if (!(boolean) isTable.invoke(val)) {
                return toJavaString.invoke(val);
            }

            Object[] tableKeys = (Object[]) keys.invoke(val);
            if (tableKeys.length > MAX_TABLE_ENTRIES) {
                throw new IllegalArgumentException(
                        "Computed event data exceeds " + MAX_TABLE_ENTRIES + " table entries");
            }
            ArrayList<IndexedValue> indexed = new ArrayList<>();
            LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
            boolean list = tableKeys.length > 0;
            for (Object key : tableKeys) {
                Object child = get.invoke(val, key);
                if ((boolean) isNumber.invoke(key)) {
                    double numericKey = (double) toDouble.invoke(key);
                    int integerKey = (int) numericKey;
                    if (numericKey >= 1 && numericKey == integerKey) {
                        indexed.add(new IndexedValue(
                                integerKey, toPlain(child, depth + 1)));
                        continue;
                    }
                }
                list = false;
                mapped.put((String) toJavaString.invoke(key),
                        toPlain(child, depth + 1));
            }
            if (list && isContiguous(indexed)) {
                indexed.sort(Comparator.comparingInt(IndexedValue::index));
                return indexed.stream().map(IndexedValue::value).toList();
            }
            for (IndexedValue entry : indexed) {
                mapped.put(Integer.toString(entry.index()), entry.value());
            }
            return mapped;
        }

        // Check if this is contiguous
        private static boolean isContiguous(List<IndexedValue> values) {
            if (values.isEmpty()) {
                return false;
            }
            values.sort(Comparator.comparingInt(IndexedValue::index));
            for (int idx = 0; idx < values.size(); idx++) {
                if (values.get(idx).index() != idx + 1) {
                    return false;
                }
            }
            return true;
        }
    }

    // Store the indexed value
    private record IndexedValue(int index, Object value) {
    }
}
