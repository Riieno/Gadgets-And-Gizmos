package com.rieno.gadgetsandgizmos.content.advanced.export;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.lib.graph.export.GraphCodeEmitter;
import com.rieno.gadgetsandgizmos.lib.graph.export.GraphCodeEmitterRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// Convert the supported ACC graph subset into deterministic, bounded Lua source
public final class AdvancedGraphLuaExporter {
    public static final String TARGET = "cc_lua";
    private static final int MAX_SOURCE_CHARACTERS = 256 * 1024;
    private static final int MAX_SOURCE_LINES = 8192;
    private static final int MAX_EXECUTION_STEPS = 4096;
    private static final double TIMER_SECONDS = 0.05D;
    private static final String KEY_SEPARATOR = "\u0000";

    // One emitter can be asked for a data expression or an execution body
    public enum Phase {
        DATA,
        EXECUTION
    }

    // Addon-owned surface presented to built-in and third-party Lua emitters
    public interface EmissionContext {
        Phase phase();

        AdvancedGraphDocument.Node node();

        String outputPort();

        String incomingPort();

        boolean connected(String inputPort);

        String input(String inputPort);

        String luaNumber(double value);

        String luaString(String value);

        void require(Phase required);

        void result(String expression);

        void line(String statement);

        void follow(String outputPort);

        void followWhen(String condition, String outputPort);

        void branch(String condition, String truePort, String falsePort);

        void fail(String code, String message);
    }

    // Return structured export failures without throwing through the peripheral
    public record Diagnostic(String severity, String code, String message, String nodeId) {
        public Diagnostic {
            severity = safe(severity);
            code = safe(code);
            message = safe(message);
            nodeId = safe(nodeId);
        }
    }

    // Export result consumed by the ComputerCraft endpoint
    public record Result(boolean success, String source, List<Diagnostic> diagnostics) {
        public Result {
            source = source == null ? "" : source;
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public String message() {
            return diagnostics.stream()
                    .filter(diagnostic -> "error".equals(diagnostic.severity()))
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message()
                            + (diagnostic.nodeId().isBlank()
                            ? "" : " [node " + diagnostic.nodeId() + "]"))
                    .collect(Collectors.joining("; "));
        }
    }

    // Parsed, bounded endpoint options
    private record ExportOptions(String programName, boolean allowTimerApproximation) {
    }

    // Internal controlled failure which becomes a graph diagnostic
    private static final class ExportFailure extends RuntimeException {
        private final String code;
        private final String nodeId;

        private ExportFailure(String code, String message, String nodeId) {
            super(message);
            this.code = safe(code);
            this.nodeId = safe(nodeId);
        }
    }

    // Shared emitters are registered once during common class initialization
    private static final GraphCodeEmitterRegistry<EmissionContext> EMITTERS =
            new GraphCodeEmitterRegistry<>();

    static {
        registerBuiltIns();
    }

    // Controller copied by view selection; no live document is exposed
    private final AdvancedContraptionControllerBlockEntity controller;

    // Initialize the exporter
    public AdvancedGraphLuaExporter(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    // Register an addon or third-party node emitter before an export is requested
    public static void registerEmitter(
            String nodeType,
            GraphCodeEmitter<EmissionContext> emitter
    ) {
        EMITTERS.register(TARGET, nodeType, emitter);
    }

    // Export one copied graph view
    public Result export(String requestedView, Map<?, ?> rawOptions) {
        try {
            String view = normalizeView(requestedView);
            ExportOptions options = readOptions(rawOptions);
            AdvancedGraphDocument graph = "draft".equals(view)
                    ? controller.getDraftGraph() : controller.getActiveGraph();

            AdvancedGraphValidator.Result validation = AdvancedGraphValidator.validate(
                    graph,
                    controller.isGogglesTrackerAvailable(),
                    controller.isControllerTrackerAvailable());
            if (!validation.valid()) {
                return new Result(false, "", validation.diagnostics().stream()
                        .map(diagnostic -> new Diagnostic(
                                diagnostic.severity(),
                                diagnostic.code(),
                                diagnostic.message(),
                                diagnostic.nodeId()))
                        .toList());
            }

            String source = new Compiler(graph, options).compile();
            int lineCount = source.isEmpty() ? 0 : (int) source.lines().count();
            if (source.length() > MAX_SOURCE_CHARACTERS || lineCount > MAX_SOURCE_LINES) {
                throw failure("source_limit",
                        "Generated Lua exceeds " + MAX_SOURCE_CHARACTERS
                                + " characters or " + MAX_SOURCE_LINES + " lines", "");
            }
            return new Result(true, source, List.of());
        } catch (ExportFailure failure) {
            return new Result(false, "", List.of(new Diagnostic(
                    "error", failure.code, failure.getMessage(), failure.nodeId)));
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            return new Result(false, "", List.of(new Diagnostic(
                    "error", "exporter_failure", message, "")));
        }
    }

    // Compile one already validated graph copy
    private static final class Compiler {
        private final AdvancedGraphDocument graph;
        private final ExportOptions options;
        private final List<AdvancedGraphDocument.Node> sortedNodes;
        private final Map<String, AdvancedGraphDocument.Edge> incoming = new LinkedHashMap<>();
        private final Map<String, List<AdvancedGraphDocument.Edge>> outgoing = new LinkedHashMap<>();

        private Compiler(AdvancedGraphDocument graph, ExportOptions options) {
            this.graph = graph;
            this.options = options;
            this.sortedNodes = graph.nodes().stream()
                    .sorted(Comparator.comparing(AdvancedGraphDocument.Node::id))
                    .toList();
            for (AdvancedGraphDocument.Edge edge : graph.edges()) {
                incoming.put(key(edge.toNode(), edge.toPort()), edge);
                outgoing.computeIfAbsent(key(edge.fromNode(), edge.fromPort()), ignored -> new ArrayList<>())
                        .add(edge);
            }
        }

        private String compile() {
            preflight();
            StringBuilder source = new StringBuilder(16384);
            source.append("-- Generated ACC graph: ")
                    .append(options.programName()).append('\n');
            source.append("-- Document revision: ").append(graph.revision()).append("\n\n");
            emitRuntime(source);
            emitVariables(source);
            emitValueFunctions(source);
            emitExecutionFunctions(source);
            emitEventLoop(source);
            return source.toString();
        }

        // Reject every unsupported semantic before returning any source
        private void preflight() {
            if (!graph.functions().isEmpty()) {
                throw failure("unsupported_functions",
                        "Function graphs are not translated by the first safe exporter", "");
            }
            boolean hasEntry = false;
            for (AdvancedGraphDocument.Node node : graph.nodes()) {
                emitter(node);
                if (isEntry(node.type())) {
                    hasEntry = true;
                }
                if ("event_tick".equals(node.type()) && !options.allowTimerApproximation()) {
                    throw failure("timing_requires_opt_in",
                            "event_tick requires allowTimerApproximation=true because a CC timer is not the server tick phase",
                            node.id());
                }
            }
            if (!hasEntry) {
                throw failure("missing_entry",
                        "Export needs event_graph_ready, event_trigger, or event_tick", "");
            }
            for (AdvancedGraphDocument.Value value : graph.variables().values()) {
                scalarLiteral(value, "");
            }
        }

        // Emit bounded helpers used by all generated nodes
        private void emitRuntime(StringBuilder source) {
            source.append("local __cache = {}\n")
                    .append("local __evaluating = {}\n")
                    .append("local __valueFns = {}\n")
                    .append("local __execFns = {}\n")
                    .append("local __pending = {}\n\n")
                    .append("local function __divide(a, b)\n")
                    .append("    if b == 0 then return 0 end\n")
                    .append("    return a / b\n")
                    .append("end\n\n")
                    .append("local function __remainder(a, b)\n")
                    .append("    if b == 0 then return 0 end\n")
                    .append("    local whole = math.modf(a / b)\n")
                    .append("    return a - whole * b\n")
                    .append("end\n\n")
                    .append("local function __round(value)\n")
                    .append("    return math.floor(value + 0.5)\n")
                    .append("end\n\n")
                    .append("local function __clamp(value, minimum, maximum)\n")
                    .append("    return math.min(math.max(value, minimum), maximum)\n")
                    .append("end\n\n")
                    .append("local function __select(condition, whenTrue, whenFalse)\n")
                    .append("    if condition then return whenTrue end\n")
                    .append("    return whenFalse\n")
                    .append("end\n\n")
                    .append("local function __value(nodeId, port)\n")
                    .append("    local key = nodeId .. \"\\0\" .. port\n")
                    .append("    local cached = __cache[key]\n")
                    .append("    if cached ~= nil then return cached end\n")
                    .append("    if __evaluating[key] then error(\"ACC data cycle at \" .. nodeId .. \".\" .. port, 0) end\n")
                    .append("    local fn = __valueFns[key]\n")
                    .append("    if fn == nil then error(\"Missing ACC value emitter for \" .. nodeId .. \".\" .. port, 0) end\n")
                    .append("    __evaluating[key] = true\n")
                    .append("    local ok, value = pcall(fn)\n")
                    .append("    __evaluating[key] = nil\n")
                    .append("    if not ok then error(value, 0) end\n")
                    .append("    __cache[key] = value\n")
                    .append("    return value\n")
                    .append("end\n\n")
                    .append("local function __push(nodeId, incomingPort)\n")
                    .append("    __pending[#__pending + 1] = { nodeId, incomingPort }\n")
                    .append("end\n\n")
                    .append("local function __dispatch(roots)\n")
                    .append("    __cache = {}\n")
                    .append("    __evaluating = {}\n")
                    .append("    __pending = {}\n")
                    .append("    for index = #roots, 1, -1 do __push(roots[index], \"\") end\n")
                    .append("    local steps = 0\n")
                    .append("    while #__pending > 0 do\n")
                    .append("        steps = steps + 1\n")
                    .append("        if steps > ").append(MAX_EXECUTION_STEPS)
                    .append(" then error(\"ACC graph exceeded the execution-step limit\", 0) end\n")
                    .append("        local call = table.remove(__pending)\n")
                    .append("        local fn = __execFns[call[1]]\n")
                    .append("        if fn == nil then error(\"Missing ACC execution emitter for \" .. call[1], 0) end\n")
                    .append("        fn(call[2])\n")
                    .append("    end\n")
                    .append("end\n\n");
        }

        // Emit initial scalar graph variables in name order
        private void emitVariables(StringBuilder source) {
            source.append("local __variables = {\n");
            graph.variables().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> source.append("    [")
                            .append(luaString(entry.getKey())).append("] = ")
                            .append(scalarLiteral(entry.getValue(), "")).append(",\n"));
            source.append("}\n\n");
        }

        // Emit one lazily cached function for every data output
        private void emitValueFunctions(StringBuilder source) {
            for (AdvancedGraphDocument.Node node : sortedNodes) {
                AdvancedGraphCatalog.outputs(node).entrySet().stream()
                        .filter(entry -> !"exec".equals(entry.getValue()))
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            Context context = new Context(Phase.DATA, node, entry.getKey(), "");
                            emitter(node).emit(context);
                            String expression = context.expression();
                            if (expression == null) {
                                throw failure("missing_expression",
                                        "Emitter did not produce output '" + entry.getKey() + "'", node.id());
                            }
                            source.append("-- node ").append(commentText(node.id())).append(" / ")
                                    .append(commentText(node.type())).append(" / ")
                                    .append(commentText(entry.getKey())).append('\n');
                            source.append("__valueFns[")
                                    .append(luaString(key(node.id(), entry.getKey())))
                                    .append("] = function()\n    return ")
                                    .append(expression).append("\nend\n\n");
                        });
            }
        }

        // Emit stack-driven execution functions so graph cycles cannot overflow the Lua call stack
        private void emitExecutionFunctions(StringBuilder source) {
            for (AdvancedGraphDocument.Node node : sortedNodes) {
                if (!hasExecution(node)) {
                    continue;
                }
                Context context = new Context(Phase.EXECUTION, node, "", "__incoming");
                emitter(node).emit(context);
                source.append("-- node ").append(commentText(node.id())).append(" / ")
                        .append(commentText(node.type())).append(" / execution\n");
                source.append("__execFns[").append(luaString(node.id()))
                        .append("] = function(__incoming)\n");
                if (context.lines().isEmpty()) {
                    source.append("    error(\"Emitter produced an empty execution body\", 0)\n");
                } else {
                    context.lines().forEach(line -> source.append("    ").append(line).append('\n'));
                }
                source.append("end\n\n");
            }
        }

        // Emit explicit CC event mappings in document-node order
        private void emitEventLoop(StringBuilder source) {
            List<AdvancedGraphDocument.Node> ready = entries("event_graph_ready");
            List<AdvancedGraphDocument.Node> ticks = entries("event_tick");
            Map<String, List<AdvancedGraphDocument.Node>> triggers = new TreeMap<>();
            for (AdvancedGraphDocument.Node node : graph.nodes()) {
                if ("event_trigger".equals(node.type())) {
                    triggers.computeIfAbsent(node.data().getString("Event"), ignored -> new ArrayList<>())
                            .add(node);
                }
            }

            source.append("local __readyRoots = ").append(rootTable(ready)).append('\n');
            source.append("local __tickRoots = ").append(rootTable(ticks)).append('\n');
            source.append("local __triggerRoots = {\n");
            triggers.forEach((name, nodes) -> source.append("    [")
                    .append(luaString(name)).append("] = ").append(rootTable(nodes)).append(",\n"));
            source.append("}\n\n")
                    .append("if #__readyRoots > 0 then __dispatch(__readyRoots) end\n")
                    .append("if #__tickRoots == 0 and next(__triggerRoots) == nil then return __variables end\n\n")
                    .append("local __timer = nil\n")
                    .append("if #__tickRoots > 0 then __timer = os.startTimer(")
                    .append(luaNumber(TIMER_SECONDS)).append(") end\n\n")
                    .append("while true do\n")
                    .append("    local event = table.pack(os.pullEventRaw())\n")
                    .append("    if event[1] == \"terminate\" then return __variables end\n")
                    .append("    if __timer ~= nil and event[1] == \"timer\" and event[2] == __timer then\n")
                    .append("        __dispatch(__tickRoots)\n")
                    .append("        __timer = os.startTimer(").append(luaNumber(TIMER_SECONDS)).append(")\n")
                    .append("    elseif event[1] == \"acc_graph_trigger\" then\n")
                    .append("        local roots = __triggerRoots[tostring(event[2] or \"\")]\n")
                    .append("        if roots ~= nil then __dispatch(roots) end\n")
                    .append("    end\n")
                    .append("end\n");
        }

        private List<AdvancedGraphDocument.Node> entries(String type) {
            return graph.nodes().stream().filter(node -> type.equals(node.type())).toList();
        }

        private String rootTable(List<AdvancedGraphDocument.Node> roots) {
            return roots.stream().map(node -> luaString(node.id()))
                    .collect(Collectors.joining(", ", "{ ", " }"));
        }

        private GraphCodeEmitter<EmissionContext> emitter(AdvancedGraphDocument.Node node) {
            return EMITTERS.find(TARGET, node.type()).orElseThrow(() ->
                    failure("unsupported_node",
                            "No cc_lua emitter is registered for '" + node.type() + "'", node.id()));
        }

        private boolean hasExecution(AdvancedGraphDocument.Node node) {
            return isEntry(node.type())
                    || AdvancedGraphCatalog.inputs(node).containsValue("exec")
                    || AdvancedGraphCatalog.outputs(node).containsValue("exec");
        }

        // One context is valid for one node phase only
        private final class Context implements EmissionContext {
            private final Phase phase;
            private final AdvancedGraphDocument.Node node;
            private final String outputPort;
            private final String incomingPort;
            private final List<String> lines = new ArrayList<>();
            private String expression;

            private Context(
                    Phase phase,
                    AdvancedGraphDocument.Node node,
                    String outputPort,
                    String incomingPort
            ) {
                this.phase = phase;
                this.node = node;
                this.outputPort = outputPort;
                this.incomingPort = incomingPort;
            }

            @Override
            public Phase phase() {
                return phase;
            }

            @Override
            public AdvancedGraphDocument.Node node() {
                return node;
            }

            @Override
            public String outputPort() {
                return outputPort;
            }

            @Override
            public String incomingPort() {
                return incomingPort;
            }

            @Override
            public boolean connected(String inputPort) {
                return incoming.containsKey(key(node.id(), inputPort));
            }

            @Override
            public String input(String inputPort) {
                String type = AdvancedGraphCatalog.inputs(node).get(inputPort);
                if (type == null) {
                    throw failure("unknown_input",
                            "Node has no input port '" + inputPort + "'", node.id());
                }
                if ("exec".equals(type)) {
                    throw failure("exec_as_data",
                            "Execution port '" + inputPort + "' cannot be read as data", node.id());
                }
                AdvancedGraphDocument.Edge edge = incoming.get(key(node.id(), inputPort));
                if (edge != null) {
                    return "__value(" + luaString(edge.fromNode()) + ", "
                            + luaString(edge.fromPort()) + ")";
                }
                return defaultLiteral(node, inputPort, type);
            }

            @Override
            public String luaNumber(double value) {
                return AdvancedGraphLuaExporter.luaNumber(value);
            }

            @Override
            public String luaString(String value) {
                return AdvancedGraphLuaExporter.luaString(value);
            }

            @Override
            public void require(Phase required) {
                if (phase != required) {
                    throw failure("unsupported_phase",
                            "Emitter does not support " + phase.name().toLowerCase(Locale.ROOT), node.id());
                }
            }

            @Override
            public void result(String value) {
                require(Phase.DATA);
                String normalized = oneLine(value, "expression");
                if (expression != null) {
                    throw failure("duplicate_expression",
                            "Emitter produced more than one expression", node.id());
                }
                expression = normalized;
            }

            @Override
            public void line(String statement) {
                require(Phase.EXECUTION);
                lines.add(oneLine(statement, "statement"));
            }

            @Override
            public void follow(String port) {
                require(Phase.EXECUTION);
                appendFollow(lines, "", port);
            }

            @Override
            public void followWhen(String condition, String port) {
                require(Phase.EXECUTION);
                lines.add("if " + oneLine(condition, "condition") + " then");
                appendFollow(lines, "    ", port);
                lines.add("end");
            }

            @Override
            public void branch(String condition, String truePort, String falsePort) {
                require(Phase.EXECUTION);
                lines.add("if " + oneLine(condition, "condition") + " then");
                appendFollow(lines, "    ", truePort);
                lines.add("else");
                appendFollow(lines, "    ", falsePort);
                lines.add("end");
            }

            @Override
            public void fail(String code, String message) {
                throw failure(code, message, node.id());
            }

            private String expression() {
                return expression;
            }

            private List<String> lines() {
                return lines;
            }

            private void appendFollow(List<String> target, String indent, String port) {
                List<AdvancedGraphDocument.Edge> edges = outgoing.getOrDefault(
                        key(node.id(), port), List.of());
                for (int index = edges.size() - 1; index >= 0; index--) {
                    AdvancedGraphDocument.Edge edge = edges.get(index);
                    target.add(indent + "__push(" + luaString(edge.toNode()) + ", "
                            + luaString(edge.toPort()) + ")");
                }
            }

            private String oneLine(String value, String name) {
                if (value == null || value.isBlank()
                        || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    throw failure("invalid_emitter_" + name,
                            "Emitter " + name + " must be one non-blank line", node.id());
                }
                return value;
            }
        }

        // Decode only values whose Lua representation is lossless in this first pass
        private String defaultLiteral(
                AdvancedGraphDocument.Node node,
                String port,
                String declaredType
        ) {
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (defaults.contains(port, Tag.TAG_COMPOUND)) {
                CompoundTag encoded = defaults.getCompound(port);
                String type = encoded.getString("Type");
                CompoundTag payload = encoded.getCompound("Payload");
                return switch (type) {
                    case "number" -> luaNumber(payload.getDouble("Value"));
                    case "boolean" -> Boolean.toString(payload.getBoolean("Value"));
                    case "string" -> luaString(payload.getString("Value"));
                    default -> throw failure("unsupported_value",
                            "Default for port '" + port + "' has unsupported type '" + type + "'",
                            node.id());
                };
            }
            return switch (declaredType) {
                case "boolean" -> "false";
                case "string" -> "\"\"";
                case "number", "any" -> "0";
                default -> throw failure("unsupported_value",
                        "Port '" + port + "' has unsupported default type '" + declaredType + "'",
                        node.id());
            };
        }
    }

    // Register built-ins whose current Java semantics have an explicit Lua translation below
    private static void registerBuiltIns() {
        registerEmitter("event_tick", AdvancedGraphLuaExporter::emitEntry);
        registerEmitter("event_trigger", AdvancedGraphLuaExporter::emitEntry);
        registerEmitter("event_graph_ready", context -> {
            if (context.phase() == Phase.DATA) {
                requirePort(context, "ready");
                context.result("true");
            } else {
                context.follow("exec");
            }
        });
        registerEmitter("constant_number", context -> {
            context.require(Phase.DATA);
            requirePort(context, "value");
            context.result(context.luaNumber(context.node().data().getDouble("Value")));
        });
        registerEmitter("constant_boolean", context -> {
            context.require(Phase.DATA);
            requirePort(context, "value");
            context.result(Boolean.toString(context.node().data().getBoolean("Value")));
        });
        registerEmitter("constant_string", context -> {
            context.require(Phase.DATA);
            requirePort(context, "value");
            context.result(context.luaString(context.node().data().getString("Value")));
        });
        registerEmitter("variable_get", AdvancedGraphLuaExporter::emitVariableGet);
        registerEmitter("variable_set", AdvancedGraphLuaExporter::emitVariableSet);
        registerEmitter("branch", context -> {
            context.require(Phase.EXECUTION);
            context.branch(context.input("condition"), "true", "false");
        });
        registerEmitter("gate", context -> {
            context.require(Phase.EXECUTION);
            context.followWhen(context.input("open"), "exec");
        });
        registerEmitter("reroute", context -> {
            if (context.phase() == Phase.DATA) {
                context.result(context.input("value"));
            } else {
                context.follow("value");
            }
        });
        registerData("data_branch", context -> "__select(" + context.input("condition")
                + ", " + context.input("true") + ", " + context.input("false") + ")");
        registerData("and", context -> "((" + context.input("a") + ") and ("
                + context.input("b") + "))");
        registerData("or", context -> "((" + context.input("a") + ") or ("
                + context.input("b") + "))");
        registerData("not", context -> "(not (" + context.input("value") + "))");
        registerBinary("add", "+");
        registerBinary("subtract", "-");
        registerBinary("multiply", "*");
        registerData("divide", context -> "__divide(" + context.input("a")
                + ", " + context.input("b") + ")");
        registerData("modulo", context -> "__remainder(" + context.input("a")
                + ", " + context.input("b") + ")");
        registerData("min", context -> "math.min(" + context.input("a")
                + ", " + context.input("b") + ")");
        registerData("max", context -> "math.max(" + context.input("a")
                + ", " + context.input("b") + ")");
        registerData("average", context -> "((" + context.input("a") + " + "
                + context.input("b") + ") / 2)");
        registerUnary("absolute", "value", "math.abs");
        registerUnary("sin", "value", "math.sin");
        registerUnary("cos", "value", "math.cos");
        registerData("x-1", context -> "(-(" + context.input("value") + "))");
        registerData("round", context -> "__round(" + context.input("value") + ")");
        registerUnary("floor", "value", "math.floor");
        registerUnary("ceil", "value", "math.ceil");
        registerData("clamp", context -> "__clamp(" + context.input("value")
                + ", " + context.input("min") + ", " + context.input("max") + ")");
        registerUnary("math_sqrt", "In", "math.sqrt");
        registerUnary("math_tan", "In", "math.tan");
        registerUnary("math_acos", "In", "math.acos");
        registerUnary("math_asin", "In", "math.asin");
        registerUnary("math_atan", "In", "math.atan");
        registerData("math_atan2", context -> "math.atan(" + context.input("Y")
                + ", " + context.input("X") + ")");
        registerData("math_power", context -> "((" + context.input("Base") + ") ^ ("
                + context.input("Exp") + "))");
        registerUnary("math_exp", "In", "math.exp");
        registerUnary("math_ln", "In", "math.log");
        registerData("math_log", context -> "(math.log(" + context.input("In")
                + ") / math.log(" + context.input("Base") + "))");
    }

    private static void emitEntry(EmissionContext context) {
        context.require(Phase.EXECUTION);
        context.follow("exec");
    }

    private static void emitVariableGet(EmissionContext context) {
        context.require(Phase.DATA);
        requirePort(context, "value");
        context.result("__variables[" + context.luaString(
                context.node().data().getString("Variable")) + "]");
    }

    private static void emitVariableSet(EmissionContext context) {
        String variable = context.luaString(context.node().data().getString("Variable"));
        if (context.phase() == Phase.DATA) {
            requirePort(context, "value");
            context.result("__variables[" + variable + "]");
            return;
        }
        String value = context.connected("value")
                ? context.input("value") : context.input("default");
        context.line("local __newValue = " + value);
        context.line("__variables[" + variable + "] = __newValue");
        context.line("__cache = {}");
        context.follow("exec");
    }

    private static void registerBinary(String nodeType, String operator) {
        registerData(nodeType, context -> "((" + context.input("a") + ") "
                + operator + " (" + context.input("b") + "))");
    }

    private static void registerUnary(String nodeType, String inputPort, String function) {
        registerData(nodeType, context -> function + "(" + context.input(inputPort) + ")");
    }

    private static void registerData(
            String nodeType,
            Function<EmissionContext, String> expression
    ) {
        registerEmitter(nodeType, context -> {
            context.require(Phase.DATA);
            context.result(expression.apply(context));
        });
    }

    private static void requirePort(EmissionContext context, String expected) {
        if (!expected.equals(context.outputPort())) {
            context.fail("unsupported_output",
                    "Emitter has no translation for output '" + context.outputPort() + "'");
        }
    }

    private static String normalizeView(String view) {
        String normalized = view == null ? "active" : view.strip().toLowerCase(Locale.ROOT);
        if (!"active".equals(normalized) && !"draft".equals(normalized)) {
            throw failure("invalid_view", "View must be 'active' or 'draft'", "");
        }
        return normalized;
    }

    private static ExportOptions readOptions(Map<?, ?> raw) {
        Map<?, ?> options = raw == null ? Map.of() : raw;
        String programName = "acc_graph";
        boolean allowTimerApproximation = false;
        for (Map.Entry<?, ?> entry : options.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw failure("invalid_option", "Export option names must be strings", "");
            }
            switch (key) {
                case "programName" -> {
                    if (!(entry.getValue() instanceof String name)) {
                        throw failure("invalid_option", "programName must be a string", "");
                    }
                    String normalized = name.strip();
                    if (normalized.isEmpty() || normalized.length() > 64
                            || normalized.chars().anyMatch(Character::isISOControl)) {
                        throw failure("invalid_option",
                                "programName must contain 1-64 printable characters", "");
                    }
                    programName = normalized;
                }
                case "allowTimerApproximation" -> {
                    if (!(entry.getValue() instanceof Boolean allowed)) {
                        throw failure("invalid_option",
                                "allowTimerApproximation must be boolean", "");
                    }
                    allowTimerApproximation = allowed;
                }
                default -> throw failure("invalid_option",
                        "Unknown export option '" + key + "'", "");
            }
        }
        return new ExportOptions(programName, allowTimerApproximation);
    }

    private static boolean isEntry(String type) {
        return "event_graph_ready".equals(type)
                || "event_trigger".equals(type)
                || "event_tick".equals(type);
    }

    private static String scalarLiteral(AdvancedGraphDocument.Value value, String nodeId) {
        if (value == null) {
            throw failure("unsupported_value", "Null graph value cannot be exported", nodeId);
        }
        return switch (value.type()) {
            case "number" -> luaNumber(value.asNumber());
            case "boolean" -> Boolean.toString(value.asBoolean());
            case "string" -> luaString(value.asString());
            default -> throw failure("unsupported_value",
                    "Graph value type '" + value.type() + "' has no lossless Lua scalar form", nodeId);
        };
    }

    private static String luaNumber(double value) {
        if (!Double.isFinite(value)) {
            throw failure("non_finite_number", "Lua export rejects NaN and infinity", "");
        }
        return Double.toString(value);
    }

    // Keep document-owned labels inside one generated Lua comment line
    private static String commentText(String value) {
        String normalized = safe(value);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            result.append(Character.isISOControl(character) ? '?' : character);
        }
        return result.toString();
    }

    private static String luaString(String value) {
        String normalized = value == null ? "" : value;
        StringBuilder result = new StringBuilder(normalized.length() + 2).append('"');
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32 || character == 127) {
                        result.append(String.format(Locale.ROOT, "\\%03d", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static String key(String nodeId, String port) {
        return safe(nodeId) + KEY_SEPARATOR + safe(port);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static ExportFailure failure(String code, String message, String nodeId) {
        return new ExportFailure(code, message, nodeId);
    }
}
