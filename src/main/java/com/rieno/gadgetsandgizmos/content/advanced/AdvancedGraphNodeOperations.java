package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Hold the shared value conversions and operations used by compiled graph nodes
final class AdvancedGraphNodeOperations {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph node operations
    private AdvancedGraphNodeOperations() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the moving average
    static AdvancedGraphDocument.Value movingAverage(Map<String, AdvancedGraphDocument.Value> state,
                                                     String nodeId, double input, double requestedSamples) {
        int samples = finiteIndex(requestedSamples, 1);
        samples = Math.max(1, Math.min(100, samples));
        String key = nodeId + ":moving_average";
        List<AdvancedGraphDocument.Value> values = listValues(state.get(key));
        values.add(AdvancedGraphDocument.Value.number(input));
        while (values.size() > samples) values.removeFirst();
        state.put(key, list(values));
        return AdvancedGraphDocument.Value.number(
                values.stream().mapToDouble(AdvancedGraphDocument.Value::asNumber).average().orElse(0.0D));
    }

    // Get the value delta
    static AdvancedGraphDocument.Value valueDelta(Map<String, AdvancedGraphDocument.Value> state,
                                                  String nodeId, double input) {
        String key = nodeId + ":value_delta";
        AdvancedGraphDocument.Value prev = state.put(key, AdvancedGraphDocument.Value.number(input));
        return AdvancedGraphDocument.Value.number(prev == null ? 0.0D : input - prev.asNumber());
    }

    // Get the peak
    static AdvancedGraphDocument.Value peak(Map<String, AdvancedGraphDocument.Value> state,
                                            String nodeId, double input, String port) {
        String minimumKey = nodeId + ":peak_min";
        String maximumKey = nodeId + ":peak_max";
        double minimum = Math.min(input,
                state.getOrDefault(minimumKey, AdvancedGraphDocument.Value.number(input)).asNumber());
        double maximum = Math.max(input,
                state.getOrDefault(maximumKey, AdvancedGraphDocument.Value.number(input)).asNumber());
        state.put(minimumKey, AdvancedGraphDocument.Value.number(minimum));
        state.put(maximumKey, AdvancedGraphDocument.Value.number(maximum));
        return AdvancedGraphDocument.Value.number("Min".equals(port) ? minimum : maximum);
    }

    // Get the split
    static AdvancedGraphDocument.Value split(String input, String delimiter) {
        String val = input == null ? "" : input;
        String separator = delimiter == null ? "" : delimiter;
        List<AdvancedGraphDocument.Value> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            val.codePoints().forEach(codePoint ->
                    parts.add(AdvancedGraphDocument.Value.string(Character.toString(codePoint))));
            return list(parts);
        }
        int start = 0;
        int separatorIndex;
        while ((separatorIndex = val.indexOf(separator, start)) >= 0) {
            parts.add(AdvancedGraphDocument.Value.string(val.substring(start, separatorIndex)));
            start = separatorIndex + separator.length();
        }
        parts.add(AdvancedGraphDocument.Value.string(val.substring(start)));
        return list(parts);
    }

    // Get the regex
    static AdvancedGraphDocument.Value regex(String input, String pattern, String port) {
        List<AdvancedGraphDocument.Value> matches = new ArrayList<>();
        try {
            Matcher matcher = Pattern.compile(pattern == null || pattern.isEmpty() ? ".*" : pattern)
                    .matcher(input == null ? "" : input);
            if (matcher.find()) {
                for (int group = 0; group <= matcher.groupCount(); group++) {
                    matches.add(AdvancedGraphDocument.Value.string(
                            matcher.group(group) == null ? "" : matcher.group(group)));
                }
            }
        } catch (PatternSyntaxException ignored) {
        }
        return "Found".equals(port)
                ? AdvancedGraphDocument.Value.bool(!matches.isEmpty())
                : list(matches);
    }

    // Join the advanced graph node operations
    static String join(AdvancedGraphDocument.Value list, String separator) {
        String delimiter = separator == null ? "" : separator;
        List<String> values = listValues(list).stream().map(AdvancedGraphNodeOperations::valueText).toList();
        return String.join(delimiter, values);
    }

    // Replace the advanced graph node operations
    static String replace(String input, String find, String replacement) {
        String val = input == null ? "" : input;
        String search = find == null ? "" : find;
        String substituted = replacement == null ? "" : replacement;
        if (!search.isEmpty()) return val.replace(search, substituted);
        return String.join(substituted, val.codePoints()
                .mapToObj(Character::toString).toList());
    }

    // Get the advanced graph node operations value
    static AdvancedGraphDocument.Value get(AdvancedGraphDocument.Value list, double requestedIndex) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        int idx = finiteIndex(requestedIndex, 0);
        return idx >= 0 && idx < values.size()
                ? values.get(idx) : AdvancedGraphDocument.Value.number(0);
    }

    // Get the shuffle
    static AdvancedGraphDocument.Value shuffle(AdvancedGraphDocument.Value list) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        Collections.shuffle(values, ThreadLocalRandom.current());
        return list(values);
    }

    // Sort the advanced graph node operations
    static AdvancedGraphDocument.Value sort(AdvancedGraphDocument.Value list, boolean descending) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        Comparator<AdvancedGraphDocument.Value> comparator = AdvancedGraphNodeOperations::compareValues;
        values.sort(descending ? comparator.reversed() : comparator);
        return list(values);
    }

    // Get the slice
    static AdvancedGraphDocument.Value slice(AdvancedGraphDocument.Value list,
                                             double requestedStart, double requestedEnd) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        int start = sliceIndex(requestedStart, values.size());
        int end = sliceIndex(requestedEnd, values.size());
        if (end < start) end = start;
        return list(values.subList(start, end));
    }

    // Add the advanced graph node operations
    static AdvancedGraphDocument.Value append(AdvancedGraphDocument.Value src,
                                              AdvancedGraphDocument.Value appended) {
        List<AdvancedGraphDocument.Value> values = listValues(src);
        values.addAll(listValues(appended));
        return list(values);
    }

    // Merge the advanced graph node operations
    static AdvancedGraphDocument.Value merge(AdvancedGraphDocument.Value first,
                                             AdvancedGraphDocument.Value second) {
        if (first != null && second != null && "list".equals(first.type()) && "list".equals(second.type())) {
            return append(first, second);
        }
        if (first != null && second != null && "map".equals(first.type()) && "map".equals(second.type())) {
            CompoundTag values = first.payload().copy();
            for (String key : second.payload().getAllKeys()) {
                values.put(key, second.payload().get(key).copy());
            }
            return AdvancedGraphDocument.Value.map(values);
        }
        return second == null ? AdvancedGraphDocument.Value.number(0) : second;
    }

    // Add the advanced graph node operations
    static AdvancedGraphDocument.Value add(AdvancedGraphDocument.Value list,
                                           AdvancedGraphDocument.Value item, boolean unique) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        if (!unique || values.stream().noneMatch(val -> sameValue(val, item))) values.add(item);
        return list(values);
    }

    // Insert the advanced graph node operations
    static AdvancedGraphDocument.Value insert(AdvancedGraphDocument.Value list,
                                              AdvancedGraphDocument.Value item, double requestedIndex) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        int idx = finiteIndex(requestedIndex, 0);
        if (idx < 0) idx = Math.max(0, values.size() + idx);
        idx = Math.min(idx, values.size());
        values.add(idx, item);
        return list(values);
    }

    // Remove the advanced graph node operations
    static AdvancedGraphDocument.Value remove(AdvancedGraphDocument.Value list, double requestedIndex) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        int idx = finiteIndex(requestedIndex, 0);
        if (idx < 0) idx += values.size();
        if (idx >= 0 && idx < values.size()) values.remove(idx);
        return list(values);
    }

    // Find the advanced graph node operations
    static int find(AdvancedGraphDocument.Value list, AdvancedGraphDocument.Value item) {
        List<AdvancedGraphDocument.Value> values = listValues(list);
        for (int idx = 0; idx < values.size(); idx++) {
            if (sameValue(values.get(idx), item)) return idx;
        }
        return -1;
    }

    // Filter the advanced graph node operations
    static AdvancedGraphDocument.Value filter(AdvancedGraphDocument.Value list, String key,
                                              AdvancedGraphDocument.Value match, String operation) {
        List<AdvancedGraphDocument.Value> res = new ArrayList<>();
        String field = key == null ? "" : key;
        String normalizedOperation = operation == null || operation.isBlank() ? "==" : operation;
        for (AdvancedGraphDocument.Value item : listValues(list)) {
            AdvancedGraphDocument.Value candidate = field.isBlank() ? item : mapValue(item, field);
            boolean accepted = switch (normalizedOperation) {
                case "!=" -> !sameValue(candidate, match);
                case ">" -> numericValue(candidate) > numericValue(match);
                case "<" -> numericValue(candidate) < numericValue(match);
                case "contains" -> valueText(candidate).toLowerCase(Locale.ROOT)
                        .contains(valueText(match).toLowerCase(Locale.ROOT));
                case "starts_with" -> valueText(candidate).toLowerCase(Locale.ROOT)
                        .startsWith(valueText(match).toLowerCase(Locale.ROOT));
                default -> sameValue(candidate, match);
            };
            if (accepted) res.add(item);
        }
        return list(res);
    }

    // Get the length
    static int length(AdvancedGraphDocument.Value list) {
        return listValues(list).size();
    }

    // Map the value
    private static AdvancedGraphDocument.Value mapValue(AdvancedGraphDocument.Value val, String key) {
        if (val == null || !"map".equals(val.type()) || !val.payload().contains(key)) {
            return AdvancedGraphDocument.Value.number(0);
        }
        return AdvancedGraphDocument.Value.fromTag(val.payload().getCompound(key));
    }

    // Get the list values
    private static List<AdvancedGraphDocument.Value> listValues(AdvancedGraphDocument.Value list) {
        List<AdvancedGraphDocument.Value> values = new ArrayList<>();
        if (list == null || !"list".equals(list.type())) return values;
        List<String> keys = new ArrayList<>();
        for (String key : list.payload().getAllKeys()) {
            try {
                Integer.parseInt(key);
                keys.add(key);
            } catch (NumberFormatException ignored) {
            }
        }
        keys.sort(Comparator.comparingInt(Integer::parseInt));
        for (String key : keys) {
            values.add(AdvancedGraphDocument.Value.fromTag(list.payload().getCompound(key)));
        }
        return values;
    }

    // Get the list
    private static AdvancedGraphDocument.Value list(List<AdvancedGraphDocument.Value> values) {
        CompoundTag payload = new CompoundTag();
        for (int idx = 0; idx < values.size(); idx++) {
            payload.put(Integer.toString(idx), values.get(idx).toTag());
        }
        return AdvancedGraphDocument.Value.list(payload);
    }

    // Check if this uses the same value
    private static boolean sameValue(AdvancedGraphDocument.Value first, AdvancedGraphDocument.Value second) {
        if (first == second) return true;
        return first != null && second != null
                && first.type().equals(second.type()) && first.payload().equals(second.payload());
    }

    // Get the compare values
    private static int compareValues(AdvancedGraphDocument.Value first, AdvancedGraphDocument.Value second) {
        if ("number".equals(first.type()) && "number".equals(second.type())) {
            return Double.compare(first.asNumber(), second.asNumber());
        }
        if ("boolean".equals(first.type()) && "boolean".equals(second.type())) {
            return Boolean.compare(first.asBoolean(), second.asBoolean());
        }
        return valueText(first).compareToIgnoreCase(valueText(second));
    }

    // Get the numeric value
    private static double numericValue(AdvancedGraphDocument.Value val) {
        if (val == null) return 0.0D;
        if ("number".equals(val.type())) return val.asNumber();
        try {
            return Double.parseDouble(valueText(val));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    // Get the value text
    private static String valueText(AdvancedGraphDocument.Value val) {
        if (val == null) return "";
        return switch (val.type()) {
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> {
                double num = val.asNumber();
                yield Double.isFinite(num) && num == Math.rint(num)
                        ? Long.toString(Math.round(num)) : Double.toString(num);
            }
            case "string", "direction" -> val.asString();
            default -> val.payload().toString();
        };
    }

    // Get the slice index
    private static int sliceIndex(double requestedIndex, int size) {
        int idx = finiteIndex(requestedIndex, 0);
        if (idx < 0) return Math.max(0, size + idx);
        return Math.min(idx, size);
    }

    // Get the finite index
    private static int finiteIndex(double requestedIndex, int fallback) {
        return Double.isFinite(requestedIndex) ? (int) requestedIndex : fallback;
    }
}
