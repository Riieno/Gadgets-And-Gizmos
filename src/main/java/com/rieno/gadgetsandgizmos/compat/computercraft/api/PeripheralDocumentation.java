package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import dan200.computercraft.api.lua.LuaFunction;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

// Build immutable peripheral help from the annotations beside Lua methods
public final class PeripheralDocumentation {
    private static final ClassValue<Catalog> CATALOGS = new ClassValue<>() {
        @Override
        protected Catalog computeValue(Class<?> type) {
            Map<String, Entry> entries = new TreeMap<>();
            for (Method method : type.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                LuaFunction lua = method.getAnnotation(LuaFunction.class);
                PeripheralDoc doc = method.getAnnotation(PeripheralDoc.class);
                boolean addonMethod = method.getDeclaringClass().getName()
                        .startsWith("com.rieno.gadgetsandgizmos.");
                if (lua != null && doc == null && addonMethod) {
                    throw new IllegalStateException(method
                            + " has @LuaFunction without @PeripheralDoc");
                }
                if (lua == null && doc != null) {
                    throw new IllegalStateException(method
                            + " has @PeripheralDoc without @LuaFunction");
                }
                if (doc == null || doc.hidden()) {
                    continue;
                }
                List<String> exposedNames = lua.value().length == 0
                        ? List.of(method.getName()) : List.of(lua.value());
                if (exposedNames.size() != 1
                        || !exposedNames.getFirst().equals(doc.name())) {
                    throw new IllegalStateException(method + " documents '"
                            + doc.name() + "' but exposes " + exposedNames
                            + "; split aliases into individually documented wrapper methods");
                }
                Entry entry = new Entry(doc.name(), doc.signature(), doc.description(),
                        List.of(doc.examples()), doc.since(), doc.deprecatedBy());
                if (entries.putIfAbsent(entry.name(), entry) != null) {
                    throw new IllegalStateException(
                            "Duplicate peripheral documentation on " + type.getName()
                                    + ": " + entry.name());
                }
            }
            return new Catalog(List.copyOf(entries.values()));
        }
    };

    private PeripheralDocumentation() {
    }

    public static Catalog catalog(Class<?> peripheralType) {
        return CATALOGS.get(Objects.requireNonNull(peripheralType, "peripheralType"));
    }

    public record Entry(
            String name,
            String signature,
            String description,
            List<String> examples,
            String since,
            String deprecatedBy
    ) {
        public Entry {
            examples = List.copyOf(examples == null ? List.of() : examples);
        }
    }

    public record Catalog(List<Entry> entries) {
        public Catalog {
            entries = List.copyOf(entries == null ? List.of() : entries);
        }

        public List<String> signatures() {
            return entries.stream().map(Entry::signature).toList();
        }

        public Map<String, String> help() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Entry entry : entries) {
                result.put(entry.name(), entry.signature()
                        + " - " + entry.description());
            }
            return Map.copyOf(result);
        }
    }
}
