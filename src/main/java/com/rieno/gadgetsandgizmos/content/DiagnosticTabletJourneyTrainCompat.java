package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Connect Diagnostic Tablet Journey Train to its optional mod without making it a hard dependency
final class DiagnosticTabletJourneyTrainCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet journey train compat
    private DiagnosticTabletJourneyTrainCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the stations
    static List<Station> stations() {
        try {
            Object railways = Class.forName("com.simibubi.create.Create")
                    .getField("RAILWAYS").get(null);
            Object networksValue = fieldValue(railways, "trackNetworks");
            if (!(networksValue instanceof Map<?, ?> networks)) return List.of();
            Object stationType = Class.forName(
                    "com.simibubi.create.content.trains.graph.EdgePointType")
                    .getField("STATION").get(null);
            Map<UUID, Station> stations = new LinkedHashMap<>();
            for (Object graph : networks.values()) {
                Collection<?> points = graphPoints(graph, stationType);
                for (Object point : points) {
                    String name = String.valueOf(fieldValue(point, "name"));
                    Object idValue = fieldValue(point, "id");
                    UUID id = idValue instanceof UUID uuid ? uuid
                            : UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (!name.isBlank() && !"null".equals(name)) {
                        stations.putIfAbsent(id, new Station(id, name));
                    }
                }
            }
            return stations.values().stream()
                    .sorted(Comparator.comparing(Station::name, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Station::id))
                    .toList();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return List.of();
        }
    }

    // Get the graph points
    private static Collection<?> graphPoints(Object graph, Object stationType)
            throws ReflectiveOperationException {
        for (Method method : graph.getClass().getMethods()) {
            if ("getPoints".equals(method.getName()) && method.getParameterCount() == 1) {
                Object res = method.invoke(graph, stationType);
                if (res instanceof Collection<?> collection) return collection;
            }
        }
        return List.of();
    }

    // Get the field value
    private static Object fieldValue(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // Store the station
    record Station(UUID id, String name) {
    }
}
