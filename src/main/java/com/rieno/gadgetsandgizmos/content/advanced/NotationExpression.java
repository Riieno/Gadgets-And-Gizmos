package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Parse function notation, evaluates it and convert it into graph nodes for the plotter
public final class NotationExpression {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_RECURSION_DEPTH = 32;
    private static final Set<String> BUILT_INS = Set.of(
            "abs", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sqrt", "pow", "exp", "ln", "log", "min", "max", "avg",
            "round", "floor", "ceil", "clamp", "mod", "sign",
            "sinh", "cosh", "tanh", "hypot");
    private static final Set<String> CONSTANTS = Set.of("pi", "e", "tau");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the notation expression
    private NotationExpression() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Parse the notation expression
    public static Program parse(List<String> sources) {
        List<String> safeSources = sources == null ? List.of() : sources;
        Set<String> knownIdentifiers = declaredIds(safeSources);
        Set<String> valueIdentifiers = declaredValueIds(safeSources);
        List<ParsedLine> parsed = new ArrayList<>();
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (int idx = 0; idx < safeSources.size(); idx++) {
            String src = safeSources.get(idx) == null ? "" : safeSources.get(idx);
            if (src.isBlank()) {
                parsed.add(new ParsedLine(idx, src, "", null, null, "", -1, 0,
                        false, false, "", ""));
                continue;
            }
            try {
                LineParts parts = splitLine(src);
                Set<String> lineIdentifiers = new HashSet<>(knownIdentifiers);
                Set<String> lineValues = new HashSet<>(valueIdentifiers);
                if (!parts.parameter().isBlank()) {
                    lineIdentifiers.add(parts.parameter().toLowerCase(Locale.ROOT));
                    lineValues.add(parts.parameter().toLowerCase(Locale.ROOT));
                }
                Expr expression;
                try {
                    expression = new Parser(parts.expression().text(), lineIdentifiers, lineValues).parse();
                } catch (ParseException error) {
                    throw new ParseException(error.getMessage(),
                            parts.expression().sourceColumn(error.position, src.length()));
                }
                Definition definition = null;
                if (!parts.name().isBlank()) {
                    String key = parts.name().toLowerCase(Locale.ROOT);
                    if (BUILT_INS.contains(key) || CONSTANTS.contains(key)
                            || "x".equals(key) || "y".equals(key)) {
                        throw new ParseException("'" + parts.name() + "' is reserved", 0);
                    }
                    if (definitions.containsKey(key)) {
                        throw new ParseException("Duplicate definition '" + parts.name() + "'", 0);
                    }
                    definition = new Definition(parts.name(), parts.parameter(), expression);
                    definitions.put(key, definition);
                }
                String label = parts.label().isBlank() ? "y" : parts.label();
                parsed.add(new ParsedLine(idx, src, label, expression, definition, "", -1, 0,
                        parts.forcePlot(), parts.autoPlot(), parts.coordinate(), parts.parameter()));
            } catch (ParseException err) {
                parsed.add(new ParsedLine(idx, src, "", null, null,
                        err.getMessage(), err.position, 1, false, false, "", ""));
            }
        }
        return new Program(parsed, definitions);
    }

    // Get the declared ids
    private static Set<String> declaredIds(List<String> sources) {
        Set<String> identifiers = new HashSet<>(BUILT_INS);
        identifiers.addAll(CONSTANTS);
        identifiers.add("x");
        identifiers.add("y");
        for (String requested : sources) {
            String src = requested == null ? "" : requested.strip();
            if (src.isBlank()) continue;
            try {
                int equals = topLevelEquals(src);
                if (equals < 0) continue;
                String left = src.substring(0, equals).strip();
                int open = left.indexOf('(');
                String name = open < 0 ? left : left.substring(0, open).strip();
                if ((open >= 0 || !implicitRelationLeft(left))
                        && name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    identifiers.add(name.toLowerCase(Locale.ROOT));
                }
                if (open >= 0 && left.endsWith(")")) {
                    String parameter = left.substring(open + 1, left.length() - 1).strip();
                    if (parameter.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        identifiers.add(parameter.toLowerCase(Locale.ROOT));
                    }
                }
            } catch (ParseException ignored) {
            }
        }
        return Set.copyOf(identifiers);
    }

    // Get the declared value ids
    private static Set<String> declaredValueIds(List<String> sources) {
        Set<String> values = new HashSet<>(CONSTANTS);
        values.add("x");
        values.add("y");
        for (String requested : sources) {
            String src = requested == null ? "" : requested.strip();
            if (src.isBlank()) continue;
            try {
                int equals = topLevelEquals(src);
                if (equals < 0) continue;
                String left = src.substring(0, equals).strip();
                if (left.indexOf('(') >= 0 || implicitRelationLeft(left)) continue;
                if (left.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    String name = left.toLowerCase(Locale.ROOT);
                    if (!BUILT_INS.contains(name) && !CONSTANTS.contains(name)) values.add(name);
                }
            } catch (ParseException ignored) {
            }
        }
        return Set.copyOf(values);
    }

    // Get the split line
    private static LineParts splitLine(String src) {
        int equals = topLevelEquals(src);
        if (equals < 0) {
            return new LineParts("", "", "y", sourceSlice(src, 0, src.length()), false, true, "");
        }
        int leftStart = skipWhitespace(src, 0, equals);
        int leftEnd = trimWhitespace(src, leftStart, equals);
        int rightStart = skipWhitespace(src, equals + 1, src.length());
        int rightEnd = trimWhitespace(src, rightStart, src.length());
        String left = src.substring(leftStart, leftEnd);
        String right = src.substring(rightStart, rightEnd);
        if (right.isBlank()) {
            throw new ParseException("Expected an expression", equals + 1);
        }
        int open = left.indexOf('(');
        if (open >= 0) {
            if (!left.endsWith(")") || left.indexOf(')', open) != left.length() - 1) {
                throw new ParseException("Invalid function definition", 0);
            }
            String name = left.substring(0, open).strip();
            String parameter = left.substring(open + 1, left.length() - 1).strip();
            requireId(name, leftStart);
            requireId(parameter, leftStart + open + 1);
            if (BUILT_INS.contains(name.toLowerCase(Locale.ROOT))) {
                return new LineParts("", "", src,
                        implicitExpression(src, leftStart, leftEnd, equals, rightStart, rightEnd),
                        true, false, "implicit");
            }
            String coordinate = coordinateName(name);
            return coordinate.isBlank()
                    ? new LineParts(name, parameter, name + "(" + parameter + ")",
                    sourceSlice(src, rightStart, rightEnd), true, false, "")
                    : new LineParts("", parameter, coordinate + "(" + parameter + ")",
                    sourceSlice(src, rightStart, rightEnd), false, false, coordinate);
        }
        if (implicitRelationLeft(left)) {
            return new LineParts("", "", src,
                    implicitExpression(src, leftStart, leftEnd, equals, rightStart, rightEnd),
                    true, false, "implicit");
        }
        requireId(left, leftStart);
        String coordinate = coordinateName(left);
        return coordinate.isBlank()
                ? new LineParts(left, "", left, sourceSlice(src, rightStart, rightEnd), false, false, "")
                : new LineParts("", "", coordinate, sourceSlice(src, rightStart, rightEnd), false, false, coordinate);
    }

    // Preserve original source columns for a direct parser slice.
    private static SourceExpression sourceSlice(String source, int requestedStart, int requestedEnd) {
        int start = Math.max(0, Math.min(requestedStart, source.length()));
        int end = Math.max(start, Math.min(requestedEnd, source.length()));
        String text = source.substring(start, end);
        int[] columns = new int[text.length() + 1];
        for (int index = 0; index <= text.length(); index++) {
            columns[index] = start + index;
        }
        return new SourceExpression(text, columns);
    }

    // Preserve the source origin of both sides of an implicit relation rewritten for the parser.
    private static SourceExpression implicitExpression(String source, int leftStart, int leftEnd,
                                                       int equals, int rightStart, int rightEnd) {
        StringBuilder text = new StringBuilder();
        List<Integer> columns = new ArrayList<>();
        appendGenerated(text, columns, "(", leftStart);
        appendSourceSlice(text, columns, source, leftStart, leftEnd);
        appendGenerated(text, columns, ") - (", equals);
        appendSourceSlice(text, columns, source, rightStart, rightEnd);
        appendGenerated(text, columns, ")", rightEnd);
        int[] mapping = new int[columns.size() + 1];
        for (int index = 0; index < columns.size(); index++) {
            mapping[index] = columns.get(index);
        }
        mapping[columns.size()] = rightEnd;
        return new SourceExpression(text.toString(), mapping);
    }

    // Append source characters and their exact columns to a rewritten expression.
    private static void appendSourceSlice(StringBuilder text, List<Integer> columns,
                                          String source, int requestedStart, int requestedEnd) {
        int start = Math.max(0, Math.min(requestedStart, source.length()));
        int end = Math.max(start, Math.min(requestedEnd, source.length()));
        for (int index = start; index < end; index++) {
            text.append(source.charAt(index));
            columns.add(index);
        }
    }

    // Append parser-only text with a nearby original source column.
    private static void appendGenerated(StringBuilder text, List<Integer> columns,
                                        String generated, int sourceColumn) {
        for (int index = 0; index < generated.length(); index++) {
            text.append(generated.charAt(index));
            columns.add(sourceColumn);
        }
    }

    // Skip source whitespace without losing its original column positions.
    private static int skipWhitespace(String source, int requestedStart, int end) {
        int index = Math.max(0, requestedStart);
        while (index < end && Character.isWhitespace(source.charAt(index))) index++;
        return index;
    }

    // Remove trailing source whitespace without losing its original column positions.
    private static int trimWhitespace(String source, int start, int requestedEnd) {
        int end = Math.max(start, Math.min(requestedEnd, source.length()));
        while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
        return end;
    }

    // Get the coordinate name
    private static String coordinateName(String requested) {
        String name = requested == null ? "" : requested.toLowerCase(Locale.ROOT);
        return "x".equals(name) || "y".equals(name) ? name : "";
    }

    // Check if the requested relation is left implicit
    private static boolean implicitRelationLeft(String requested) {
        String left = requested == null ? "" : requested.strip();
        if (!left.matches("[A-Za-z_][A-Za-z0-9_]*")) return true;
        String normalized = left.toLowerCase(Locale.ROOT);
        return "xy".equals(normalized) || "yx".equals(normalized);
    }

    // Find the top-level equals sign
    private static int topLevelEquals(String src) {
        int depth = 0;
        int equals = -1;
        for (int idx = 0; idx < src.length(); idx++) {
            char current = src.charAt(idx);
            if (current == '(') depth++;
            else if (current == ')') depth--;
            else if (current == '=' && depth == 0) {
                if (equals >= 0) throw new ParseException("Only one assignment is allowed", idx);
                equals = idx;
            }
            if (depth < 0) throw new ParseException("Unexpected ')'", idx);
        }
        if (depth != 0) throw new ParseException("Unclosed '('", src.length());
        return equals;
    }

    // Require the id
    private static void requireId(String val, int pos) {
        if (val == null || !val.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ParseException("Expected a name", pos);
        }
    }

    // Define the series kind values
    public enum SeriesKind {
        CARTESIAN,
        PARAMETRIC,
        IMPLICIT
    }

    // Store the series
    public record Series(int lineIndex, int pairedLineIndex, String label, String source,
                         SeriesKind kind, String parameter) {
        // Initialize the series
        public Series {
            label = label == null ? "" : label;
            source = source == null ? "" : source;
            kind = kind == null ? SeriesKind.CARTESIAN : kind;
            parameter = parameter == null || parameter.isBlank() ? "x" : parameter;
        }

        // Check if the expression is parametric
        public boolean parametric() {
            return kind == SeriesKind.PARAMETRIC;
        }

        // Check if the expression is implicit
        public boolean implicit() {
            return kind == SeriesKind.IMPLICIT;
        }
    }

    // Store the point
    public record Point(double x, double y) {
    }

    // Store line results
    public record LineResult(int lineIndex, String source, String label, String error,
                             int errorColumn, int errorLength) {
        // Check if this is valid
        public boolean valid() {
            return error == null || error.isBlank();
        }

        // Get a source-safe parse error column.
        public int safeErrorColumn() {
            return Math.max(0, Math.min(errorColumn, source == null ? 0 : source.length()));
        }

        // Get a source-safe parse error length.
        public int safeErrorLength() {
            int sourceLength = source == null ? 0 : source.length();
            int remaining = Math.max(1, sourceLength - safeErrorColumn());
            return Math.max(1, Math.min(errorLength, remaining));
        }
    }

    // Store the curve sample
    public record CurveSample(double input, double output) {
    }

    // Store compile results
    public record CompileResult(AdvancedGraphDocument.FunctionGraph function, String error) {
        // Check if the expression parsed successfully
        public boolean successful() {
            return function != null && (error == null || error.isBlank());
        }
    }

    // Store the parsed notation program
    public static final class Program {
        // Tracked lines
        private final List<ParsedLine> lines;
        // Tracked definitions
        private final Map<String, Definition> definitions;
        // Tracked series
        private final List<Series> series;
        // Parametric indexed by line
        private final Map<Integer, ParametricBinding> parametricByLine;

        // Initialize the program
        private Program(List<ParsedLine> lines, Map<String, Definition> definitions) {
            this.definitions = Map.copyOf(definitions);
            Map<Integer, ParametricBinding> bindings = new HashMap<>();
            for (ParametricBinding binding : findParametricBindings(lines)) {
                bindings.put(binding.xLine, binding);
                bindings.put(binding.yLine, binding);
            }
            parametricByLine = Map.copyOf(bindings);
            List<ParsedLine> validated = new ArrayList<>(lines.size());
            for (ParsedLine line : lines) {
                if (line.expression == null || !line.error.isBlank()) {
                    validated.add(line);
                    continue;
                }
                try {
                    Map<String, Double> variables = new HashMap<>();
                    variables.put("x", 0.0D);
                    boolean implicit = implicitLine(line);
                    if (implicit) variables.put("y", 0.0D);
                    ParametricBinding binding = parametricByLine.get(line.index);
                    if (binding != null) variables.put(binding.parameter, 0.0D);
                    if (line.definition != null && !line.definition.parameter.isBlank()) {
                        variables.put(line.definition.parameter.toLowerCase(Locale.ROOT), 0.0D);
                    }
                    if ("x".equals(line.coordinate) && binding == null) {
                        throw new EvaluationException("Parametric x requires a matching y expression");
                    }
                    evaluate(line.expression, variables, new LinkedHashSet<>(), 0);
                    validated.add(line);
                } catch (EvaluationException err) {
                    validated.add(new ParsedLine(line.index, line.source, line.label, line.expression,
                            line.definition, err.getMessage(), -1, 0, false, false,
                            line.coordinate, line.parameter));
                }
            }
            this.lines = List.copyOf(validated);
            List<Series> collected = new ArrayList<>();
            for (ParsedLine line : validated) {
                ParametricBinding binding = parametricByLine.get(line.index);
                if (binding != null) {
                    if (line.index != binding.xLine) continue;
                    ParsedLine yLine = validated.get(binding.yLine);
                    if (line.expression != null && line.error.isBlank()
                            && yLine.expression != null && yLine.error.isBlank()) {
                        collected.add(new Series(line.index, binding.yLine,
                                "x(" + binding.parameter + "), y(" + binding.parameter + ")",
                                line.source + "; " + yLine.source, SeriesKind.PARAMETRIC, binding.parameter));
                    }
                    continue;
                }
                if ("x".equals(line.coordinate)) continue;
                if (implicitLine(line)) {
                    if (line.expression != null && line.error.isBlank()) {
                        collected.add(new Series(line.index, -1, line.label, line.source,
                                SeriesKind.IMPLICIT, "x,y"));
                    }
                    continue;
                }
                boolean plotted = "y".equals(line.coordinate) || line.forcePlot || line.autoPlot
                        && dependsOnX(line.expression, new HashMap<>(), new LinkedHashSet<>(), 0);
                if (line.expression != null && line.error.isBlank() && plotted) {
                    collected.add(new Series(line.index, -1, line.label, line.source,
                            SeriesKind.CARTESIAN, "x"));
                }
            }
            series = List.copyOf(collected);
        }

        // Get the lines
        public List<LineResult> lines() {
            return lines.stream().map(line ->
                    new LineResult(line.index, line.source, line.label, line.error,
                            line.errorColumn, line.errorLength)).toList();
        }

        // Get the series
        public List<Series> series() {
            return series;
        }

        // Get the final series
        public Series finalSeries() {
            return series.isEmpty() ? null : series.getLast();
        }

        // Check if the expression line was plotted
        public boolean plotted(int lineIndex) {
            return series.stream().anyMatch(series ->
                    series.lineIndex == lineIndex || series.pairedLineIndex == lineIndex);
        }

        // Evaluate the line
        public double evaluateLine(int lineIndex, double x) {
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                throw new EvaluationException("Function line is out of range");
            }
            ParsedLine line = lines.get(lineIndex);
            ParametricBinding binding = parametricByLine.get(lineIndex);
            Map<String, Double> variables = new HashMap<>();
            variables.put("x", x);
            if (binding != null) variables.put(binding.parameter, x);
            if (line.definition != null && !line.definition.parameter.isBlank()) {
                variables.put(line.definition.parameter.toLowerCase(Locale.ROOT), x);
            }
            return evaluate(line.expression, variables, new LinkedHashSet<>(), 0);
        }

        // Evaluate the program
        public double evaluate(Series target, double x) {
            return evaluatePoint(target, x).y;
        }

        // Evaluate the point
        public Point evaluatePoint(Series target, double input) {
            ParsedLine line = line(target);
            if (target.implicit()) {
                throw new EvaluationException("Implicit series require both x and y values");
            }
            Map<String, Double> variables = new HashMap<>();
            variables.put("x", input);
            if (target.parametric()) {
                variables.clear();
                variables.put(target.parameter.toLowerCase(Locale.ROOT), input);
                ParsedLine paired = lines.get(target.pairedLineIndex);
                return new Point(
                        evaluate(line.expression, variables, new LinkedHashSet<>(), 0),
                        evaluate(paired.expression, variables, new LinkedHashSet<>(), 0));
            }
            if (line.definition != null && !line.definition.parameter.isBlank()) {
                variables.put(line.definition.parameter.toLowerCase(Locale.ROOT), input);
            }
            return new Point(input, evaluate(line.expression, variables, new LinkedHashSet<>(), 0));
        }

        // Evaluate the implicit
        public double evaluateImplicit(Series target, double x, double y) {
            if (target == null || !target.implicit()) {
                throw new EvaluationException("The selected series is not implicit");
            }
            ParsedLine line = line(target);
            Map<String, Double> variables = new HashMap<>();
            variables.put("x", x);
            variables.put("y", y);
            return evaluate(line.expression, variables, new LinkedHashSet<>(), 0);
        }

        // Get the parameter minimum
        public double parameterMinimum(Series target) {
            return target != null && target.parametric()
                    ? rangeValue(target.parameter, true, 0.0D) : 0.0D;
        }

        // Get the parameter maximum
        public double parameterMaximum(Series target) {
            if (target == null || !target.parametric()) return 0.0D;
            double minimum = parameterMinimum(target);
            double maximum = rangeValue(target.parameter, false, Math.PI * 2.0D);
            return Double.isFinite(maximum) && maximum > minimum ? maximum : minimum + Math.PI * 2.0D;
        }

        // Compile the program
        public CompileResult compile(Series target, String functionName, List<CurveSample> responseCurve) {
            ParsedLine line;
            try {
                line = line(target);
            } catch (IllegalArgumentException err) {
                return new CompileResult(null, err.getMessage());
            }
            try {
                GraphCompiler compiler = new GraphCompiler(this, functionName,
                        target.implicit() ? List.of("x", "y")
                                : List.of(target.parametric() ? target.parameter : "x"),
                        target.parametric() ? List.of("x", "y")
                                : List.of(target.implicit() ? "value" : "y"));
                Map<String, Ref> substitutions = new HashMap<>();
                if (target.implicit()) {
                    Ref res = compiler.compile(line.expression, substitutions, new LinkedHashSet<>(), 0);
                    if (responseCurve != null && responseCurve.size() >= 2) {
                        res = compiler.compileResponseCurve(res, responseCurve);
                    }
                    return new CompileResult(compiler.finish(Map.of("value", res)), "");
                } else if (target.parametric()) {
                    substitutions.put(target.parameter.toLowerCase(Locale.ROOT), compiler.inputReference());
                    ParsedLine paired = lines.get(target.pairedLineIndex);
                    Ref xResult = compiler.compile(line.expression, substitutions, new LinkedHashSet<>(), 0);
                    Ref yResult = compiler.compile(paired.expression, substitutions, new LinkedHashSet<>(), 0);
                    if (responseCurve != null && responseCurve.size() >= 2) {
                        xResult = compiler.compileResponseCurve(xResult, responseCurve);
                        yResult = compiler.compileResponseCurve(yResult, responseCurve);
                    }
                    return new CompileResult(compiler.finish(Map.of("x", xResult, "y", yResult)), "");
                } else if (line.definition != null && !line.definition.parameter.isBlank()) {
                    substitutions.put(line.definition.parameter.toLowerCase(Locale.ROOT), compiler.inputReference());
                }
                Ref res = compiler.compile(line.expression, substitutions, new LinkedHashSet<>(), 0);
                if (responseCurve != null && responseCurve.size() >= 2) {
                    res = compiler.compileResponseCurve(res, responseCurve);
                }
                return new CompileResult(compiler.finish(Map.of("y", res)), "");
            } catch (EvaluationException err) {
                return new CompileResult(null, err.getMessage());
            }
        }

        // Build one display line
        private ParsedLine line(Series target) {
            if (target == null || target.lineIndex < 0 || target.lineIndex >= lines.size()) {
                throw new IllegalArgumentException("No function is selected");
            }
            ParsedLine line = lines.get(target.lineIndex);
            if (line.expression == null) {
                throw new IllegalArgumentException(line.error.isBlank() ? "The selected line is empty" : line.error);
            }
            return line;
        }

        // Check if the parsed line is implicit
        private boolean implicitLine(ParsedLine line) {
            return "implicit".equals(line.coordinate)
                    || line.definition == null && line.coordinate.isBlank()
                    && freeVariables(line.expression, new LinkedHashSet<>(), 0).contains("y");
        }

        // Find the parametric bindings
        private List<ParametricBinding> findParametricBindings(List<ParsedLine> parsed) {
            List<ParsedLine> xLines = parsed.stream().filter(line -> "x".equals(line.coordinate)).toList();
            List<ParsedLine> yLines = parsed.stream().filter(line -> "y".equals(line.coordinate)).toList();
            Set<Integer> usedYLines = new HashSet<>();
            List<ParametricBinding> results = new ArrayList<>();
            for (ParsedLine xLine : xLines) {
                ParsedLine selected = null;
                String selectedParameter = "";
                int selectedDistance = Integer.MAX_VALUE;
                for (ParsedLine yLine : yLines) {
                    if (usedYLines.contains(yLine.index)) continue;
                    String parameter = parametricParameter(xLine, yLine);
                    if (parameter.isBlank()) continue;
                    int distance = Math.abs(xLine.index - yLine.index);
                    if (distance < selectedDistance) {
                        selected = yLine;
                        selectedParameter = parameter;
                        selectedDistance = distance;
                    }
                }
                if (selected != null) {
                    usedYLines.add(selected.index);
                    results.add(new ParametricBinding(xLine.index, selected.index, selectedParameter));
                }
            }
            return List.copyOf(results);
        }

        // Get the parametric parameter
        private String parametricParameter(ParsedLine xLine, ParsedLine yLine) {
            Set<String> variables = new LinkedHashSet<>();
            variables.addAll(freeVariables(xLine.expression, new LinkedHashSet<>(), 0));
            variables.addAll(freeVariables(yLine.expression, new LinkedHashSet<>(), 0));
            if (!xLine.parameter.isBlank()) variables.add(xLine.parameter.toLowerCase(Locale.ROOT));
            if (!yLine.parameter.isBlank()) variables.add(yLine.parameter.toLowerCase(Locale.ROOT));
            variables.removeAll(CONSTANTS);
            if (variables.size() != 1) return "";
            String parameter = variables.iterator().next();
            return "x".equals(parameter) || "y".equals(parameter) ? "" : parameter;
        }

        // Get the free variables
        private Set<String> freeVariables(Expr expression, Set<String> stack, int depth) {
            if (expression == null || depth > MAX_RECURSION_DEPTH) return Set.of();
            if (expression instanceof NumberExpr) return Set.of();
            if (expression instanceof VariableExpr variable) {
                String key = variable.name.toLowerCase(Locale.ROOT);
                if (CONSTANTS.contains(key)) return Set.of();
                Definition definition = definitions.get(key);
                if (definition == null || !definition.parameter.isBlank()) return Set.of(key);
                if (!stack.add(key)) return Set.of();
                Set<String> res = freeVariables(definition.expression, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            if (expression instanceof UnaryExpr unary) {
                return freeVariables(unary.value, stack, depth + 1);
            }
            if (expression instanceof BinaryExpr binary) {
                Set<String> res = new LinkedHashSet<>(freeVariables(binary.left, stack, depth + 1));
                res.addAll(freeVariables(binary.right, stack, depth + 1));
                return res;
            }
            if (expression instanceof CallExpr call) {
                Set<String> res = new LinkedHashSet<>();
                for (Expr argument : call.arguments) {
                    res.addAll(freeVariables(argument, stack, depth + 1));
                }
                String key = call.name.toLowerCase(Locale.ROOT);
                Definition definition = definitions.get(key);
                if (!BUILT_INS.contains(key) && definition != null
                        && !definition.parameter.isBlank() && stack.add(key)) {
                    Set<String> external = new LinkedHashSet<>(
                            freeVariables(definition.expression, stack, depth + 1));
                    external.remove(definition.parameter.toLowerCase(Locale.ROOT));
                    res.addAll(external);
                    stack.remove(key);
                }
                return res;
            }
            return Set.of();
        }

        // Get the range value
        private double rangeValue(String parameter, boolean minimum, double fallback) {
            String normalized = parameter == null ? "t" : parameter.toLowerCase(Locale.ROOT);
            List<String> names = minimum
                    ? List.of(normalized + "_min", normalized + "min")
                    : List.of(normalized + "_max", normalized + "max");
            for (String name : names) {
                Definition definition = definitions.get(name);
                if (definition == null || !definition.parameter.isBlank()) continue;
                try {
                    double val = evaluate(definition.expression, new HashMap<>(), new LinkedHashSet<>(), 0);
                    if (Double.isFinite(val)) return val;
                } catch (EvaluationException ignored) {
                }
            }
            return fallback;
        }

        // Evaluate the program
        private double evaluate(Expr expression, Map<String, Double> variables,
                                Set<String> stack, int depth) {
            if (depth > MAX_RECURSION_DEPTH) throw new EvaluationException("Function recursion is too deep");
            if (expression instanceof NumberExpr num) return num.value;
            if (expression instanceof VariableExpr variable) {
                String key = variable.name.toLowerCase(Locale.ROOT);
                if (variables.containsKey(key)) return variables.get(key);
                if ("pi".equals(key)) return Math.PI;
                if ("e".equals(key)) return Math.E;
                if ("tau".equals(key)) return Math.PI * 2.0D;
                Definition definition = definitions.get(key);
                if (definition == null || !definition.parameter.isBlank()) {
                    throw new EvaluationException("Unknown value '" + variable.name + "'");
                }
                if (!stack.add(key)) throw new EvaluationException("Recursive definition '" + variable.name + "'");
                double res = evaluate(definition.expression, variables, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            if (expression instanceof UnaryExpr unary) {
                double val = evaluate(unary.value, variables, stack, depth + 1);
                return unary.operator == '-' ? -val : val;
            }
            if (expression instanceof BinaryExpr binary) {
                double left = evaluate(binary.left, variables, stack, depth + 1);
                double right = evaluate(binary.right, variables, stack, depth + 1);
                return switch (binary.operator) {
                    case '+' -> left + right;
                    case '-' -> left - right;
                    case '*' -> left * right;
                    case '/' -> right == 0.0D ? 0.0D : left / right;
                    case '%' -> right == 0.0D ? 0.0D : left % right;
                    case '^' -> Math.pow(left, right);
                    default -> throw new EvaluationException("Unsupported operator '" + binary.operator + "'");
                };
            }
            if (expression instanceof CallExpr call) {
                List<Double> args = new ArrayList<>(call.arguments.size());
                for (Expr argument : call.arguments) {
                    args.add(evaluate(argument, variables, stack, depth + 1));
                }
                String key = call.name.toLowerCase(Locale.ROOT);
                if (BUILT_INS.contains(key)) return builtIn(key, args);
                Definition definition = definitions.get(key);
                if (definition == null || definition.parameter.isBlank()) {
                    throw new EvaluationException("Unknown function '" + call.name + "'");
                }
                requireArity(call.name, args.size(), 1);
                if (!stack.add(key)) throw new EvaluationException("Recursive function '" + call.name + "'");
                Map<String, Double> nested = new HashMap<>(variables);
                nested.put(definition.parameter.toLowerCase(Locale.ROOT), args.getFirst());
                double res = evaluate(definition.expression, nested, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            throw new EvaluationException("Unsupported expression");
        }

        // Check if the expression depends on x
        private boolean dependsOnX(Expr expression, Map<String, Boolean> variables,
                                   Set<String> stack, int depth) {
            if (expression == null || depth > MAX_RECURSION_DEPTH) return false;
            if (expression instanceof NumberExpr) return false;
            if (expression instanceof VariableExpr variable) {
                String key = variable.name.toLowerCase(Locale.ROOT);
                if (variables.containsKey(key)) return variables.get(key);
                if ("x".equals(key)) return true;
                if (CONSTANTS.contains(key)) return false;
                Definition definition = definitions.get(key);
                if (definition == null || !definition.parameter.isBlank() || !stack.add(key)) return false;
                boolean res = dependsOnX(definition.expression, variables, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            if (expression instanceof UnaryExpr unary) {
                return dependsOnX(unary.value, variables, stack, depth + 1);
            }
            if (expression instanceof BinaryExpr binary) {
                return dependsOnX(binary.left, variables, stack, depth + 1)
                        || dependsOnX(binary.right, variables, stack, depth + 1);
            }
            if (expression instanceof CallExpr call) {
                if (BUILT_INS.contains(call.name.toLowerCase(Locale.ROOT))) {
                    return call.arguments.stream()
                            .anyMatch(argument -> dependsOnX(argument, variables, stack, depth + 1));
                }
                String key = call.name.toLowerCase(Locale.ROOT);
                Definition definition = definitions.get(key);
                if (definition == null || definition.parameter.isBlank() || !stack.add(key)) return false;
                boolean argumentDepends = !call.arguments.isEmpty()
                        && dependsOnX(call.arguments.getFirst(), variables, stack, depth + 1);
                Map<String, Boolean> nested = new HashMap<>(variables);
                nested.put(definition.parameter.toLowerCase(Locale.ROOT), argumentDepends);
                boolean res = dependsOnX(definition.expression, nested, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            return false;
        }
    }

    // Evaluate a built-in expression function
    private static double builtIn(String name, List<Double> args) {
        return switch (name) {
            case "abs" -> unary(name, args, Math::abs);
            case "sin" -> unary(name, args, Math::sin);
            case "cos" -> unary(name, args, Math::cos);
            case "tan" -> unary(name, args, Math::tan);
            case "asin" -> unary(name, args, Math::asin);
            case "acos" -> unary(name, args, Math::acos);
            case "atan" -> unary(name, args, Math::atan);
            case "sqrt" -> unary(name, args, Math::sqrt);
            case "exp" -> unary(name, args, Math::exp);
            case "ln" -> unary(name, args, Math::log);
            case "round" -> unary(name, args, val -> Math.round(val));
            case "floor" -> unary(name, args, Math::floor);
            case "ceil" -> unary(name, args, Math::ceil);
            case "sign" -> unary(name, args, Math::signum);
            case "sinh" -> unary(name, args, Math::sinh);
            case "cosh" -> unary(name, args, Math::cosh);
            case "tanh" -> unary(name, args, Math::tanh);
            case "atan2" -> binary(name, args, Math::atan2);
            case "hypot" -> binary(name, args, Math::hypot);
            case "pow" -> binary(name, args, Math::pow);
            case "min" -> aggregate(name, args, args.stream().mapToDouble(Double::doubleValue).min().orElse(0.0D));
            case "max" -> aggregate(name, args, args.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D));
            case "avg" -> aggregate(name, args, args.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D));
            case "mod" -> binary(name, args, (a, b) -> b == 0.0D ? 0.0D : a % b);
            case "log" -> {
                if (args.size() == 1) yield Math.log10(args.getFirst());
                requireArity(name, args.size(), 2);
                yield Math.log(args.getFirst()) / Math.log(args.get(1));
            }
            case "clamp" -> {
                requireArity(name, args.size(), 3);
                yield Math.min(Math.max(args.getFirst(), args.get(1)), args.get(2));
            }
            default -> throw new EvaluationException("Unknown function '" + name + "'");
        };
    }

    // Get the unary
    private static double unary(String name, List<Double> args, java.util.function.DoubleUnaryOperator operator) {
        requireArity(name, args.size(), 1);
        return operator.applyAsDouble(args.getFirst());
    }

    // Get the binary
    private static double binary(String name, List<Double> args, java.util.function.DoubleBinaryOperator operator) {
        requireArity(name, args.size(), 2);
        return operator.applyAsDouble(args.getFirst(), args.get(1));
    }

    // Combine the notation expression
    private static double aggregate(String name, List<Double> args, double res) {
        if (args.isEmpty()) throw new EvaluationException(name + " expects at least 1 argument");
        return res;
    }

    // Require the arity
    private static void requireArity(String name, int actual, int expected) {
        if (actual != expected) {
            throw new EvaluationException(name + " expects " + expected + " argument" + (expected == 1 ? "" : "s"));
        }
    }

    // Handle the graph compiler
    private static final class GraphCompiler {
        // Program
        private final Program program;
        // Function
        private final AdvancedGraphDocument.FunctionGraph function;
        // Input
        private final AdvancedGraphDocument.Node input;
        // Output
        private final AdvancedGraphDocument.Node output;
        // Input references
        private final Map<String, Ref> inputReferences;
        // Primary input port
        private final String primaryInputPort;
        // Tracked node columns
        private final Map<String, Integer> nodeColumns = new HashMap<>();
        // Tracked column rows
        private final Map<Integer, Integer> columnRows = new HashMap<>();

        // Initialize the graph compiler
        private GraphCompiler(Program program, String functionName,
                              List<String> requestedInputPorts, List<String> outputPortNames) {
            this.program = program;
            List<String> inputPortNames = requestedInputPorts == null || requestedInputPorts.isEmpty()
                    ? List.of("x") : requestedInputPorts.stream()
                    .map(port -> port == null || port.isBlank() ? "x" : port.toLowerCase(Locale.ROOT))
                    .distinct().toList();
            primaryInputPort = inputPortNames.getFirst();
            function = new AdvancedGraphDocument.FunctionGraph(UUID.randomUUID().toString(), functionName);
            CompoundTag inputData = new CompoundTag();
            CompoundTag inputPorts = new CompoundTag();
            inputPortNames.forEach(port -> inputPorts.putString(port, "number"));
            inputData.put("DynamicOutputs", inputPorts);
            input = new AdvancedGraphDocument.Node(UUID.randomUUID().toString(),
                    AdvancedGraphFunctions.INPUT_TYPE, "Inputs", 0.0D, 80.0D, inputData);
            Map<String, Ref> references = new LinkedHashMap<>();
            inputPortNames.forEach(port -> references.put(port, new Ref(input.id(), port)));
            inputReferences = Map.copyOf(references);
            CompoundTag outputData = new CompoundTag();
            CompoundTag outputPorts = new CompoundTag();
            outputPortNames.forEach(port -> outputPorts.putString(port, "number"));
            outputData.put("DynamicInputs", outputPorts);
            output = new AdvancedGraphDocument.Node(UUID.randomUUID().toString(),
                    AdvancedGraphFunctions.OUTPUT_TYPE, "Outputs", 760.0D, 80.0D, outputData);
            function.nodes().add(input);
            function.nodes().add(output);
            nodeColumns.put(input.id(), 0);
        }

        // Finish the graph compiler
        private AdvancedGraphDocument.FunctionGraph finish(Map<String, Ref> results) {
            results.forEach((port, res) -> connect(res, output, port));
            double maximumX = function.nodes().stream().mapToDouble(AdvancedGraphDocument.Node::x)
                    .max().orElse(540.0D);
            function.nodes().remove(output);
            AdvancedGraphDocument.Node positionedOutput = new AdvancedGraphDocument.Node(
                    output.id(), output.type(), output.label(), maximumX + 210.0D, 80.0D, output.data());
            function.nodes().add(positionedOutput);
            for (int idx = 0; idx < function.edges().size(); idx++) {
                AdvancedGraphDocument.Edge edge = function.edges().get(idx);
                if (edge.toNode().equals(output.id())) {
                    function.edges().set(idx, new AdvancedGraphDocument.Edge(
                            edge.id(), edge.fromNode(), edge.fromPort(), positionedOutput.id(), edge.toPort()));
                }
            }
            function.setViewport(100.0D, 70.0D, 0.8D);
            return function;
        }

        // Get the input reference
        private Ref inputReference() {
            return inputReferences.get(primaryInputPort);
        }

        // Compile the graph compiler
        private Ref compile(Expr expression, Map<String, Ref> substitutions,
                            Set<String> stack, int depth) {
            if (depth > MAX_RECURSION_DEPTH) throw new EvaluationException("Function recursion is too deep");
            if (expression instanceof NumberExpr num) return constant(num.value);
            if (expression instanceof VariableExpr variable) {
                String key = variable.name.toLowerCase(Locale.ROOT);
                Ref replacement = substitutions.get(key);
                if (replacement != null) return replacement;
                Ref inputValue = inputReferences.get(key);
                if (inputValue != null) return inputValue;
                if ("pi".equals(key)) return constant(Math.PI);
                if ("e".equals(key)) return constant(Math.E);
                if ("tau".equals(key)) return constant(Math.PI * 2.0D);
                Definition definition = program.definitions.get(key);
                if (definition == null || !definition.parameter.isBlank()) {
                    throw new EvaluationException("Unknown value '" + variable.name + "'");
                }
                if (!stack.add(key)) throw new EvaluationException("Recursive definition '" + variable.name + "'");
                Ref res = compile(definition.expression, substitutions, stack, depth + 1);
                stack.remove(key);
                return res;
            }
            if (expression instanceof UnaryExpr unary) {
                Ref val = compile(unary.value, substitutions, stack, depth + 1);
                return unary.operator == '-' ? operation("subtract", "value", Map.of("b", val), Map.of("a", 0.0D)) : val;
            }
            if (expression instanceof BinaryExpr binary) {
                Ref left = compile(binary.left, substitutions, stack, depth + 1);
                Ref right = compile(binary.right, substitutions, stack, depth + 1);
                String type = switch (binary.operator) {
                    case '+' -> "add";
                    case '-' -> "subtract";
                    case '*' -> "multiply";
                    case '/' -> "divide";
                    case '%' -> "modulo";
                    case '^' -> "math_power";
                    default -> throw new EvaluationException("Unsupported operator '" + binary.operator + "'");
                };
                return "math_power".equals(type)
                        ? operation(type, "Result", Map.of("Base", left, "Exp", right), Map.of())
                        : operation(type, "value", Map.of("a", left, "b", right), Map.of());
            }
            if (expression instanceof CallExpr call) {
                String key = call.name.toLowerCase(Locale.ROOT);
                if (!BUILT_INS.contains(key)) {
                    Definition definition = program.definitions.get(key);
                    if (definition == null || definition.parameter.isBlank()) {
                        throw new EvaluationException("Unknown function '" + call.name + "'");
                    }
                    requireArity(call.name, call.arguments.size(), 1);
                    if (!stack.add(key)) throw new EvaluationException("Recursive function '" + call.name + "'");
                    Ref argument = compile(call.arguments.getFirst(), substitutions, stack, depth + 1);
                    Map<String, Ref> nested = new HashMap<>(substitutions);
                    nested.put(definition.parameter.toLowerCase(Locale.ROOT), argument);
                    Ref res = compile(definition.expression, nested, stack, depth + 1);
                    stack.remove(key);
                    return res;
                }
                return compileBuiltIn(call, substitutions, stack, depth + 1);
            }
            throw new EvaluationException("Unsupported expression");
        }

        // Compile a built-in expression call
        private Ref compileBuiltIn(CallExpr call, Map<String, Ref> substitutions,
                                   Set<String> stack, int depth) {
            String name = call.name.toLowerCase(Locale.ROOT);
            int expected = "clamp".equals(name) ? 3
                    : "log".equals(name) && call.arguments.size() == 1 ? 1
                    : Set.of("atan2", "hypot", "pow", "mod", "log").contains(name) ? 2 : 1;
            if (Set.of("min", "max", "avg").contains(name)) {
                if (call.arguments.isEmpty()) {
                    throw new EvaluationException(name + " expects at least 1 argument");
                }
            } else {
                requireArity(name, call.arguments.size(), expected);
            }
            List<Ref> args = call.arguments.stream()
                    .map(argument -> compile(argument, substitutions, stack, depth + 1)).toList();
            return switch (name) {
                case "abs" -> operation("absolute", "value", Map.of("value", args.getFirst()), Map.of());
                case "sin", "cos", "round", "floor", "ceil" ->
                        operation(name, "value", Map.of("value", args.getFirst()), Map.of());
                case "tan", "asin", "acos", "atan", "sqrt", "exp", "ln" -> {
                    String type = switch (name) {
                        case "tan" -> "math_tan";
                        case "asin" -> "math_asin";
                        case "acos" -> "math_acos";
                        case "atan" -> "math_atan";
                        case "sqrt" -> "math_sqrt";
                        case "exp" -> "math_exp";
                        default -> "math_ln";
                    };
                    yield operation(type, "Out", Map.of("In", args.getFirst()), Map.of());
                }
                case "atan2" -> operation("math_atan2", "Out",
                        Map.of("Y", args.getFirst(), "X", args.get(1)), Map.of());
                case "hypot" -> {
                    Ref leftSquare = operation("math_power", "Result",
                            Map.of("Base", args.getFirst()), Map.of("Exp", 2.0D));
                    Ref rightSquare = operation("math_power", "Result",
                            Map.of("Base", args.get(1)), Map.of("Exp", 2.0D));
                    Ref sum = operation("add", "value", Map.of("a", leftSquare, "b", rightSquare), Map.of());
                    yield operation("math_sqrt", "Out", Map.of("In", sum), Map.of());
                }
                case "pow" -> operation("math_power", "Result",
                        Map.of("Base", args.getFirst(), "Exp", args.get(1)), Map.of());
                case "min", "max" -> fold(name, args);
                case "avg" -> average(args);
                case "mod" -> operation("modulo", "value",
                        Map.of("a", args.getFirst(), "b", args.get(1)), Map.of());
                case "log" -> {
                    Ref base = args.size() == 1 ? constant(10.0D) : args.get(1);
                    yield operation("math_log", "Out",
                            Map.of("In", args.getFirst(), "Base", base), Map.of());
                }
                case "clamp" -> operation("clamp", "value",
                        Map.of("value", args.getFirst(), "min", args.get(1), "max", args.get(2)), Map.of());
                case "sign" -> sign(args.getFirst());
                case "sinh" -> hyperbolic(args.getFirst(), false, false);
                case "cosh" -> hyperbolic(args.getFirst(), true, false);
                case "tanh" -> hyperbolic(args.getFirst(), false, true);
                default -> throw new EvaluationException("Unknown function '" + name + "'");
            };
        }

        // Get the fold
        private Ref fold(String type, List<Ref> args) {
            Ref res = args.getFirst();
            for (int idx = 1; idx < args.size(); idx++) {
                res = operation(type, "value", Map.of("a", res, "b", args.get(idx)), Map.of());
            }
            return res;
        }

        // Get the average
        private Ref average(List<Ref> args) {
            if (args.size() == 1) return args.getFirst();
            Ref sum = fold("add", args);
            return operation("divide", "value", Map.of("a", sum), Map.of("b", (double) args.size()));
        }

        // Get the sign
        private Ref sign(Ref val) {
            Ref positive = operation("compare", "value", Map.of("a", val),
                    Map.of("b", 0.0D), Map.of("operator", ">"));
            Ref negative = operation("compare", "value", Map.of("a", val),
                    Map.of("b", 0.0D), Map.of("operator", "<"));
            Ref nonPositive = operation("data_branch", "value", Map.of("condition", negative),
                    Map.of("true", -1.0D, "false", 0.0D));
            return operation("data_branch", "value",
                    Map.of("condition", positive, "false", nonPositive), Map.of("true", 1.0D));
        }

        // Get the hyperbolic
        private Ref hyperbolic(Ref val, boolean cosine, boolean tangent) {
            Ref positive = operation("math_exp", "Out", Map.of("In", val), Map.of());
            Ref negativeInput = operation("subtract", "value", Map.of("b", val), Map.of("a", 0.0D));
            Ref negative = operation("math_exp", "Out", Map.of("In", negativeInput), Map.of());
            Ref numerator = operation(cosine ? "add" : "subtract", "value",
                    Map.of("a", positive, "b", negative), Map.of());
            Ref base = operation("divide", "value", Map.of("a", numerator), Map.of("b", 2.0D));
            if (!tangent) return base;
            Ref denominatorSum = operation("add", "value", Map.of("a", positive, "b", negative), Map.of());
            Ref denominator = operation("divide", "value", Map.of("a", denominatorSum), Map.of("b", 2.0D));
            return operation("divide", "value", Map.of("a", base, "b", denominator), Map.of());
        }

        // Compile the response curve
        private Ref compileResponseCurve(Ref src, List<CurveSample> requested) {
            List<CurveSample> samples = requested.stream()
                    .filter(sample -> Double.isFinite(sample.input) && Double.isFinite(sample.output))
                    .sorted(Comparator.comparingDouble(CurveSample::input)).toList();
            if (samples.size() < 2) return src;
            Ref clamped = operation("clamp", "value", Map.of("value", src),
                    Map.of("min", samples.getFirst().input, "max", samples.getLast().input));
            Ref res = segment(clamped, samples.get(samples.size() - 2), samples.getLast());
            for (int idx = samples.size() - 3; idx >= 0; idx--) {
                CurveSample start = samples.get(idx);
                CurveSample end = samples.get(idx + 1);
                Ref line = segment(clamped, start, end);
                Ref condition = operation("compare", "value", Map.of("a", clamped),
                        Map.of("b", end.input), Map.of("operator", "<="));
                res = operation("data_branch", "value",
                        Map.of("condition", condition, "true", line, "false", res), Map.of());
            }
            return res;
        }

        // Get the segment
        private Ref segment(Ref src, CurveSample start, CurveSample end) {
            double span = end.input - start.input;
            double slope = span == 0.0D ? 0.0D : (end.output - start.output) / span;
            double intercept = start.output - slope * start.input;
            Ref scaled = operation("multiply", "value", Map.of("a", src), Map.of("b", slope));
            return operation("add", "value", Map.of("a", scaled), Map.of("b", intercept));
        }

        // Get the constant
        private Ref constant(double val) {
            CompoundTag data = new CompoundTag();
            data.putDouble("Value", val);
            AdvancedGraphDocument.Node node = node("constant_number", "Number", data, 1);
            return new Ref(node.id(), "value");
        }

        // Get the operation
        private Ref operation(String type, String outputPort, Map<String, Ref> inputs,
                              Map<String, Double> numericDefaults) {
            return operation(type, outputPort, inputs, numericDefaults, Map.of());
        }

        // Get the operation
        private Ref operation(String type, String outputPort, Map<String, Ref> inputs,
                              Map<String, Double> numericDefaults, Map<String, String> stringDefaults) {
            CompoundTag data = new CompoundTag();
            CompoundTag defaults = new CompoundTag();
            numericDefaults.forEach((port, val) ->
                    defaults.put(port, AdvancedGraphDocument.Value.number(val).toTag()));
            stringDefaults.forEach((port, val) ->
                    defaults.put(port, AdvancedGraphDocument.Value.string(val).toTag()));
            if (!defaults.isEmpty()) data.put("Defaults", defaults);
            int column = inputs.values().stream()
                    .mapToInt(src -> nodeColumns.getOrDefault(src.nodeId, 0))
                    .max().orElse(0) + 1;
            AdvancedGraphDocument.Node node = node(type, AdvancedGraphCatalog.displayName(type), data, column);
            inputs.forEach((port, src) -> connect(src, node, port));
            return new Ref(node.id(), outputPort);
        }

        // Get the node
        private AdvancedGraphDocument.Node node(String type, String label, CompoundTag data, int requestedColumn) {
            int column = Math.max(1, requestedColumn);
            int row = columnRows.getOrDefault(column, 0);
            columnRows.put(column, row + 1);
            AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(
                    UUID.randomUUID().toString(), type, label,
                    column * 190.0D, 20.0D + row * 105.0D, data);
            function.nodes().add(node);
            nodeColumns.put(node.id(), column);
            return node;
        }

        // Connect the graph compiler
        private void connect(Ref src, AdvancedGraphDocument.Node target, String port) {
            function.edges().add(AdvancedGraphFunctions.edge(src.nodeId, src.port, target.id(), port));
        }
    }

    // Store the ref
    private record Ref(String nodeId, String port) {
    }

    // Store the parsed line
    private record ParsedLine(int index, String source, String label, Expr expression,
                              Definition definition, String error, int errorColumn, int errorLength,
                              boolean forcePlot, boolean autoPlot,
                              String coordinate, String parameter) {
        // Initialize the parsed line
        private ParsedLine {
            coordinate = coordinate == null ? "" : coordinate;
            parameter = parameter == null ? "" : parameter;
        }
    }

    // Store the parametric binding
    private record ParametricBinding(int xLine, int yLine, String parameter) {
        // Initialize the parametric binding
        private ParametricBinding {
            parameter = parameter == null ? "t" : parameter.toLowerCase(Locale.ROOT);
        }
    }

    // Store the definition
    private record Definition(String name, String parameter, Expr expression) {
        // Initialize the definition
        private Definition {
            name = name == null ? "" : name;
            parameter = parameter == null ? "" : parameter;
        }
    }

    // Store the line parts
    private record SourceExpression(String text, int[] sourceColumns) {
        private SourceExpression {
            text = text == null ? "" : text;
            sourceColumns = sourceColumns == null ? new int[]{0} : sourceColumns.clone();
        }

        private int sourceColumn(int expressionColumn, int sourceLength) {
            if (sourceColumns.length == 0) return Math.max(0, sourceLength);
            int index = Math.max(0, Math.min(expressionColumn, sourceColumns.length - 1));
            return Math.max(0, Math.min(sourceColumns[index], Math.max(0, sourceLength)));
        }
    }

    // Store the source-aware line parts
    private record LineParts(String name, String parameter, String label, SourceExpression expression,
                             boolean forcePlot, boolean autoPlot, String coordinate) {
    }

    // Expose the expr
    private sealed interface Expr permits NumberExpr, VariableExpr, UnaryExpr, BinaryExpr, CallExpr {
    }

    // Store the number expr
    private record NumberExpr(double value) implements Expr {
    }

    // Store the variable expr
    private record VariableExpr(String name) implements Expr {
    }

    // Store the unary expr
    private record UnaryExpr(char operator, Expr value) implements Expr {
    }

    // Store the binary expr
    private record BinaryExpr(char operator, Expr left, Expr right) implements Expr {
    }

    // Store the call expr
    private record CallExpr(String name, List<Expr> arguments) implements Expr {
        // Initialize the call expr
        private CallExpr {
            arguments = List.copyOf(arguments);
        }
    }

    // Handle the evaluation exception
    public static final class EvaluationException extends RuntimeException {
        // Initialize the evaluation exception
        private EvaluationException(String msg) {
            super(msg);
        }
    }

    // Handle the parse exception
    private static final class ParseException extends RuntimeException {
        // Parse exception position
        private final int position;

        // Initialize the parse exception
        private ParseException(String msg, int pos) {
            super(msg);
            this.position = Math.max(0, pos);
        }
    }

    // Parse notation expressions
    private static final class Parser {
        // Parser source
        private final String source;
        // Tracked known identifiers
        private final Set<String> knownIdentifiers;
        // Tracked value identifiers
        private final Set<String> valueIdentifiers;
        // Current parser position
        private int position;

        // Initialize the parser
        private Parser(String src, Set<String> knownIdentifiers, Set<String> valueIdentifiers) {
            this.source = src == null ? "" : src;
            this.knownIdentifiers = knownIdentifiers == null ? Set.of() : knownIdentifiers;
            this.valueIdentifiers = valueIdentifiers == null ? Set.of() : valueIdentifiers;
        }

        // Parse the parser
        private Expr parse() {
            Expr res = addSubtract();
            skipWhitespace();
            if (position != source.length()) throw error("Unexpected '" + source.charAt(position) + "'");
            return res;
        }

        // Add the subtract
        private Expr addSubtract() {
            Expr res = multiplyDivide();
            while (true) {
                skipWhitespace();
                if (match('+')) res = new BinaryExpr('+', res, multiplyDivide());
                else if (match('-')) res = new BinaryExpr('-', res, multiplyDivide());
                else return res;
            }
        }

        // Get the multiply divide
        private Expr multiplyDivide() {
            Expr res = unary();
            while (true) {
                skipWhitespace();
                if (match('*')) res = new BinaryExpr('*', res, unary());
                else if (match('/')) res = new BinaryExpr('/', res, unary());
                else if (match('%')) res = new BinaryExpr('%', res, unary());
                else if (startsImplicitFactor()) res = new BinaryExpr('*', res, unary());
                else return res;
            }
        }

        // Check if this starts with implicit factor
        private boolean startsImplicitFactor() {
            skipWhitespace();
            if (position >= source.length()) return false;
            char current = source.charAt(position);
            return current == '(' || current == '.' || Character.isDigit(current)
                    || Character.isLetter(current) || current == '_';
        }

        // Get the unary
        private Expr unary() {
            skipWhitespace();
            if (match('+')) return new UnaryExpr('+', unary());
            if (match('-')) return new UnaryExpr('-', unary());
            return power();
        }

        // Get the power
        private Expr power() {
            Expr res = primary();
            skipWhitespace();
            if (match('^')) res = new BinaryExpr('^', res, unary());
            return res;
        }

        // Get the primary
        private Expr primary() {
            skipWhitespace();
            if (match('(')) {
                Expr nested = addSubtract();
                skipWhitespace();
                if (!match(')')) throw error("Expected ')'");
                return nested;
            }
            if (position < source.length()
                    && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                return number();
            }
            if (position < source.length()
                    && (Character.isLetter(source.charAt(position)) || source.charAt(position) == '_')) {
                String name = id();
                skipWhitespace();
                if (valueIdentifiers.contains(name.toLowerCase(Locale.ROOT)) || !match('(')) {
                    return new VariableExpr(name);
                }
                List<Expr> args = new ArrayList<>();
                skipWhitespace();
                if (!match(')')) {
                    do {
                        args.add(addSubtract());
                        skipWhitespace();
                    } while (match(','));
                    if (!match(')')) throw error("Expected ')' after arguments");
                }
                return new CallExpr(name, args);
            }
            throw error("Expected a number, name, or '('");
        }

        // Read the numeric value
        private Expr number() {
            int start = position;
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            if (position < source.length() && source.charAt(position) == '.') {
                position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            if (position < source.length() && source.charAt(position) == '.') {
                throw new ParseException("Invalid number", position);
            }
            if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                int exponentEnd = position + 1;
                if (exponentEnd < source.length()
                        && (source.charAt(exponentEnd) == '+' || source.charAt(exponentEnd) == '-')) exponentEnd++;
                int digitStart = exponentEnd;
                while (exponentEnd < source.length() && Character.isDigit(source.charAt(exponentEnd))) exponentEnd++;
                if (exponentEnd > digitStart) position = exponentEnd;
            }
            try {
                return new NumberExpr(Double.parseDouble(source.substring(start, position)));
            } catch (NumberFormatException err) {
                throw new ParseException("Invalid number", start);
            }
        }

        // Get the id
        private String id() {
            int start = position;
            int end = position + 1;
            while (end < source.length()) {
                char current = source.charAt(end);
                if (!Character.isLetterOrDigit(current) && current != '_') break;
                end++;
            }
            String full = source.substring(start, end);
            String normalized = full.toLowerCase(Locale.ROOT);
            int next = end;
            while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
            if (knownIdentifiers.contains(normalized)
                    || next < source.length() && source.charAt(next) == '(') {
                position = end;
                return full;
            }
            int prefixLength = 0;
            for (String known : knownIdentifiers) {
                if (BUILT_INS.contains(known) || known.length() >= full.length()) continue;
                if (normalized.startsWith(known) && known.length() > prefixLength) {
                    prefixLength = known.length();
                }
            }
            if (prefixLength > 0) {
                position = start + prefixLength;
                return source.substring(start, position);
            }
            position = end;
            return full;
        }

        // Skip the whitespace
        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        // Match the next expression character
        private boolean match(char expected) {
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        // Get the error
        private ParseException error(String msg) {
            return new ParseException(msg, position);
        }
    }
}
