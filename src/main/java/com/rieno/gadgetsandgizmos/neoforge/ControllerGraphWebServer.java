package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// Serve the local graph editor and keep its controller sessions limited to the current server
public final class ControllerGraphWebServer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int DEFAULT_PORT = 48574;
    public static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;

    private static final String RESOURCE_ROOT = "/assets/createthrusters/web/graph-editor/";
    private static final int MAX_PENDING_REMOTE_IMPORTS = 32;
    private static final long REMOTE_IMPORT_TIMEOUT_MILLIS = 2 * 60 * 1000L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, StaticResource> STATIC_RESOURCES = Map.of(
            "/", new StaticResource("index.html", "text/html; charset=utf-8"),
            "/index.html", new StaticResource("index.html", "text/html; charset=utf-8"),
            "/app.css", new StaticResource("app.css", "text/css; charset=utf-8"),
            "/app.js", new StaticResource("app.js", "text/javascript; charset=utf-8"),
            "/gag_logo.png", new StaticResource("gag_logo.png", "image/png"),
            "/fonts/PixelifySans-Variable.ttf",
            new StaticResource("fonts/PixelifySans-Variable.ttf", "font/ttf"),
            "/fonts/OFL-PixelifySans.txt",
            new StaticResource("fonts/OFL-PixelifySans.txt", "text/plain; charset=utf-8"),
            "/vendor/riestat-plugin-studio.js",
            new StaticResource("vendor/riestat-plugin-studio.js", "text/javascript; charset=utf-8")
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared active server
    private static HttpServer server;
    // Shared executor
    private static ExecutorService executor;
    // Shared bound port
    private static int boundPort;
    private static final AtomicReference<PublishedOpenGraph> PUBLISHED_OPEN_GRAPH = new AtomicReference<>();
    private static final Map<String, PendingRemoteImport> PENDING_REMOTE_IMPORTS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller graph web server
    private ControllerGraphWebServer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server started event
    public static synchronized void onServerStarted(ServerStartedEvent evt) {
        start();
    }

    // Handle the server stopped event
    public static synchronized void onServerStopped(ServerStoppedEvent evt) {
        stop();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Start the controller graph web server
    static synchronized boolean start() {
        if (server != null) return true;
        int requestedPort = configuredPort();
        try {
            HttpServer created = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), requestedPort), 16);
            AtomicInteger threadNumber = new AtomicInteger();
            ExecutorService createdExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable,
                        "create-thrusters-graph-web-" + threadNumber.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            created.createContext("/", ControllerGraphWebServer::handle);
            created.setExecutor(createdExecutor);
            created.start();
            server = created;
            executor = createdExecutor;
            boundPort = created.getAddress().getPort();
            LOGGER.info("Contraption Graph Workshop is available at http://localhost:{}/", boundPort);
            return true;
        } catch (IOException err) {
            LOGGER.warn("Could not start the Contraption Graph Workshop on localhost:{}",
                    requestedPort, err);
            return false;
        }
    }

    // Stop the controller graph web server
    static synchronized void stop() {
        HttpServer runningServer = server;
        ExecutorService runningExecutor = executor;
        server = null;
        executor = null;
        boundPort = 0;
        synchronized (PENDING_REMOTE_IMPORTS) {
            PENDING_REMOTE_IMPORTS.clear();
        }
        if (runningServer != null) runningServer.stop(0);
        if (runningExecutor != null) runningExecutor.shutdownNow();
    }

    // Get the configured port
    private static int configuredPort() {
        String configured = System.getProperty("createthrusters.graphEditorPort", "").trim();
        if (configured.isBlank()) return DEFAULT_PORT;
        try {
            int val = Integer.parseInt(configured);
            return val >= 1024 && val <= 65535 ? val : DEFAULT_PORT;
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    // Get the local editor URI
    public static URI localEditorUri() {
        int port = boundPort > 0 ? boundPort : configuredPort();
        return URI.create("http://127.0.0.1:" + port + "/");
    }

    // Publish the open graph
    public static void publishOpenGraph(String name, AdvancedGraphDocument graph, int fingerprint) {
        if (graph == null) {
            PUBLISHED_OPEN_GRAPH.set(null);
            return;
        }
        PublishedOpenGraph prev = PUBLISHED_OPEN_GRAPH.get();
        AdvancedGraphDocument publishedGraph = prev != null && prev.fingerprint() == fingerprint
                ? prev.graph() : graph.copy();
        String displayName = name == null || name.isBlank() ? "Currently Open Graph" : name.trim();
        PUBLISHED_OPEN_GRAPH.set(new PublishedOpenGraph(
                displayName, publishedGraph, fingerprint, System.currentTimeMillis()));
    }

    // Clear the open graph
    public static void clearOpenGraph() {
        PUBLISHED_OPEN_GRAPH.set(null);
    }

    // Handle the controller graph web server
    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                applyCommonHeaders(exchange.getResponseHeaders());
                String path = exchange.getRequestURI().getRawPath();
                if (path == null || path.isBlank()) path = "/";
                if (path.startsWith("/api/")) {
                    handleApi(exchange, path);
                    return;
                }
                StaticResource resource = STATIC_RESOURCES.get(path);
                if (resource == null) {
                    sendJsonError(exchange, 404, "Not found");
                    return;
                }
                if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                    sendJsonError(exchange, 405, "Method not allowed");
                    return;
                }
                sendResource(exchange, resource);
            } catch (Exception err) {
                LOGGER.warn("Contraption Graph Workshop request failed", err);
                if (exchange.getResponseCode() < 0) {
                    sendJsonError(exchange, 500, "Request failed");
                }
            }
        }
    }

    // Route the graph editor API request
    private static void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if ("/api/status".equals(path) && "GET".equals(method)) {
            JsonObject status = new JsonObject();
            status.addProperty("running", true);
            status.addProperty("port", boundPort);
            status.addProperty("sharedDirectory", "shared_graphs");
            sendJson(exchange, 200, status);
            return;
        }
        if ("/api/nodes".equals(path) && "GET".equals(method)) {
            sendJson(exchange, 200, nodeCatalogJson());
            return;
        }
        if ("/api/current-graph".equals(path) && "GET".equals(method)) {
            sendJson(exchange, 200, currentGraphJson());
            return;
        }
        if ("/api/share/status".equals(path) && "GET".equals(method)) {
            PublicGraphShareService.Availability availability =
                    PublicGraphShareService.availability().join();
            JsonObject resp = new JsonObject();
            resp.addProperty("available", availability.available());
            resp.addProperty("message", availability.message());
            resp.addProperty("site", PublicGraphShareService.PUBLIC_SITE.toString());
            sendJson(exchange, 200, resp);
            return;
        }
        if ("/api/share".equals(path) && "POST".equals(method)) {
            uploadPublicGraph(exchange);
            return;
        }
        String importPrefix = "/api/import-remote/";
        if (path.startsWith(importPrefix)) {
            String requestedId = path.substring(importPrefix.length());
            if ("GET".equals(method)) {
                confirmRemoteGraph(exchange, requestedId);
            } else if ("POST".equals(method)) {
                finishRemoteGraphImport(exchange, requestedId);
            } else {
                sendJsonError(exchange, 405, "Method not allowed");
            }
            return;
        }
        if ("/api/manifests".equals(path) && "GET".equals(method)) {
            sendJson(exchange, 200, manifestListJson());
            return;
        }
        if ("/api/manifests".equals(path) && "POST".equals(method)) {
            Map<String, String> query = queryParameters(exchange.getRequestURI().getRawQuery());
            importManifest(exchange, query.getOrDefault("name", ""),
                    saveMode(query.get("mode")));
            return;
        }
        String prefix = "/api/manifests/";
        if (path.startsWith(prefix)) {
            String id = decode(path.substring(prefix.length()));
            if (id.isBlank()) {
                sendJsonError(exchange, 400, "A manifest id is required");
            } else if ("GET".equals(method)) {
                String json = ControllerManifestStore.readSharedGraphJson(id);
                if (json == null) sendJsonError(exchange, 404, "Manifest not found");
                else send(exchange, 200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8));
            } else if ("PUT".equals(method)) {
                importManifest(exchange, id, ControllerManifestStore.SharedGraphSaveMode.OVERWRITE);
            } else {
                sendJsonError(exchange, 405, "Method not allowed");
            }
            return;
        }
        sendJsonError(exchange, 404, "API route not found");
    }

    // Upload the public graph
    private static void uploadPublicGraph(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            sendJsonError(exchange, 415, "Graph uploads require application/json");
            return;
        }
        byte[] body;
        try {
            body = readLimited(exchange.getRequestBody(), MAX_MANIFEST_BYTES);
        } catch (RequestTooLargeException err) {
            sendJsonError(exchange, 413, "Manifest is larger than 2 MiB");
            return;
        }
        PublicGraphShareService.ShareResult res = PublicGraphShareService.upload(
                new String(body, StandardCharsets.UTF_8)).join();
        if (!res.shared()) {
            sendJsonError(exchange, 503, res.message());
            return;
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("id", res.id());
        resp.addProperty("url", res.url());
        resp.addProperty("message", res.message());
        sendJson(exchange, 200, resp);
    }

    // Confirm the remote graph
    private static void confirmRemoteGraph(HttpExchange exchange, String requestedId) throws IOException {
        String id = PublicGraphShareService.normalizeGraphId(decode(requestedId));
        if (id.isBlank()) {
            sendHtml(exchange, 400, importResultPage(false, "Invalid graph id", ""));
            return;
        }
        String token = createRemoteImportToken(id);
        sendHtml(exchange, 200, importConfirmationPage(id, token));
    }

    // Finish the remote graph import
    private static void finishRemoteGraphImport(HttpExchange exchange, String requestedId) throws IOException {
        String id = PublicGraphShareService.normalizeGraphId(decode(requestedId));
        byte[] body;
        try {
            body = readLimited(exchange.getRequestBody(), 512);
        } catch (RequestTooLargeException err) {
            sendHtml(exchange, 413, importResultPage(false, "Import confirmation is invalid", ""));
            return;
        }
        Map<String, String> fields = queryParameters(new String(body, StandardCharsets.UTF_8));
        if (id.isBlank() || !consumeRemoteImportToken(id, fields.get("token"))) {
            sendHtml(exchange, 403, importResultPage(false, "Import confirmation expired", ""));
            return;
        }
        try {
            String manifest = PublicGraphShareService.download(id).get(7, TimeUnit.SECONDS);
            ControllerManifestStore.SharedGraphSaveResult res = ControllerManifestStore.importSharedGraph(
                    id, manifest, ControllerManifestStore.SharedGraphSaveMode.INCREMENT);
            if (!res.saved()) {
                sendHtml(exchange, 400, importResultPage(false,
                        "The graph could not be added to this Minecraft instance", ""));
                return;
            }
            sendHtml(exchange, 200, importResultPage(true,
                    "Graph downloaded into this instance", res.id()));
        } catch (Exception err) {
            sendHtml(exchange, 503, importResultPage(false,
                    "The shared graph server could not be reached", ""));
        }
    }

    // Create the remote import token
    private static String createRemoteImportToken(String id) {
        synchronized (PENDING_REMOTE_IMPORTS) {
            long now = System.currentTimeMillis();
            PENDING_REMOTE_IMPORTS.entrySet().removeIf(
                    entry -> entry.getValue().expiresAt() < now);
            while (PENDING_REMOTE_IMPORTS.size() >= MAX_PENDING_REMOTE_IMPORTS) {
                PENDING_REMOTE_IMPORTS.remove(PENDING_REMOTE_IMPORTS.keySet().iterator().next());
            }
            String token = UUID.randomUUID().toString();
            PENDING_REMOTE_IMPORTS.put(token,
                    new PendingRemoteImport(id, now + REMOTE_IMPORT_TIMEOUT_MILLIS));
            return token;
        }
    }

    // Consume the remote import token
    private static boolean consumeRemoteImportToken(String id, String token) {
        if (token == null || token.isBlank()) return false;
        synchronized (PENDING_REMOTE_IMPORTS) {
            PendingRemoteImport pending = PENDING_REMOTE_IMPORTS.remove(token);
            return pending != null && pending.expiresAt() >= System.currentTimeMillis()
                    && pending.graphId().equals(id);
        }
    }

    // Import the confirmation page
    private static String importConfirmationPage(String id, String token) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Confirm Contraption Graph Import</title></head>"
                + "<body style=\"margin:0;background:#0c1013;color:#e8e0cd;font:18px sans-serif;"
                + "display:grid;place-items:center;min-height:100vh\"><main style=\"padding:28px;"
                + "border:1px solid #3e4b52;background:#171e22;max-width:520px\">"
                + "<h1 style=\"font-size:24px;color:#d6bb72\">Add this shared graph?</h1>"
                + "<p>This will download graph <code>" + id + "</code> into this Minecraft instance.</p>"
                + "<form method=\"post\"><input type=\"hidden\" name=\"token\" value=\"" + token + "\">"
                + "<button type=\"submit\" style=\"font:inherit;padding:10px 16px\">Add Shared Graph</button>"
                + "</form></main></body></html>";
    }

    // Import the result page
    private static String importResultPage(boolean success, String msg, String id) {
        String col = success ? "#78a76f" : "#bd6559";
        String detail = id == null || id.isBlank() ? "" : "<p>Saved as <code>" + id + ".json</code></p>";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Contraption Graph Import</title></head>"
                + "<body style=\"margin:0;background:#0c1013;color:#e8e0cd;font:18px sans-serif;"
                + "display:grid;place-items:center;min-height:100vh\"><main style=\"padding:28px;"
                + "border:1px solid #3e4b52;background:#171e22;max-width:520px\">"
                + "<h1 style=\"font-size:24px;color:" + col + "\">" + msg + "</h1>"
                + detail + "<p>You can close this tab and refresh Shared Graphs in the controller.</p>"
                + "</main></body></html>";
    }

    // Import the manifest
    private static void importManifest(HttpExchange exchange, String name,
                                       ControllerManifestStore.SharedGraphSaveMode mode) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            sendJsonError(exchange, 415, "Manifest uploads require application/json");
            return;
        }
        byte[] body;
        try {
            body = readLimited(exchange.getRequestBody(), MAX_MANIFEST_BYTES);
        } catch (RequestTooLargeException err) {
            sendJsonError(exchange, 413, "Manifest is larger than 2 MiB");
            return;
        }
        ControllerManifestStore.SharedGraphSaveResult res = ControllerManifestStore.importSharedGraph(
                name, new String(body, StandardCharsets.UTF_8), mode);
        JsonObject resp = new JsonObject();
        resp.addProperty("status", res.status().name().toLowerCase(Locale.ROOT));
        resp.addProperty("id", res.id());
        if (res.saved()) {
            sendJson(exchange, 200, resp);
        } else if (res.status() == ControllerManifestStore.SharedGraphSaveStatus.EXISTS) {
            sendJson(exchange, 409, resp);
        } else {
            sendJsonError(exchange, 400, "The manifest could not be imported");
        }
    }

    // Get the node catalog JSON
    static JsonObject nodeCatalogJson() {
        JsonObject root = new JsonObject();
        JsonArray definitions = new JsonArray();
        for (AdvancedGraphCatalog.Definition definition : AdvancedGraphCatalog.all()) {
            JsonObject node = new JsonObject();
            node.addProperty("id", definition.id());
            node.addProperty("name", AdvancedGraphCatalog.displayName(definition.id()));
            node.addProperty("category", definition.category());
            node.addProperty("categoryName", AdvancedGraphCatalog.categoryName(definition.category()));
            node.addProperty("color", String.format(Locale.ROOT, "#%06X",
                    AdvancedGraphCatalog.categoryColor(definition.category()) & 0xFFFFFF));
            node.addProperty("stateful", definition.stateful());
            node.add("inputs", GSON.toJsonTree(orderedPorts(definition.inputs())));
            node.add("outputs", GSON.toJsonTree(orderedPorts(definition.outputs())));
            definitions.add(node);
        }
        root.add("nodes", definitions);
        return root;
    }

    // Get the ordered ports
    private static Map<String, String> orderedPorts(Map<String, String> ports) {
        Map<String, String> ordered = new LinkedHashMap<>();
        ports.entrySet().stream()
                .sorted((left, right) -> {
                    int execution = Boolean.compare(!"exec".equals(left.getValue()),
                            !"exec".equals(right.getValue()));
                    return execution != 0 ? execution : left.getKey().compareToIgnoreCase(right.getKey());
                })
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    // Get the current graph JSON
    private static JsonObject currentGraphJson() {
        JsonObject resp = new JsonObject();
        PublishedOpenGraph published = PUBLISHED_OPEN_GRAPH.get();
        if (published != null && System.currentTimeMillis() - published.updatedAt() > 5_000L) {
            PUBLISHED_OPEN_GRAPH.compareAndSet(published, null);
            published = null;
        }
        ServerOpenGraph serverGraph = readServerOpenGraph();
        AdvancedGraphDocument graph = published != null ? published.graph()
                : serverGraph == null ? null : serverGraph.graph();
        if (graph == null) {
            resp.addProperty("available", false);
            return resp;
        }
        String name = published != null ? published.name() : "Currently Open Graph";
        resp.addProperty("available", true);
        resp.addProperty("name", name);
        resp.addProperty("fingerprint", published != null
                ? published.fingerprint() : graph.simulationFingerprint());
        resp.add("manifest", JsonParser.parseString(
                ControllerManifestStore.sharedGraphJson(name, graph)));
        JsonObject runtime = new JsonObject();
        JsonObject liveInputs = new JsonObject();
        JsonObject liveOutputs = new JsonObject();
        JsonObject pulses = new JsonObject();
        if (serverGraph != null) {
            addRuntimeValues(runtime, liveInputs, serverGraph.inputs());
            addRuntimeValues(runtime, liveOutputs, serverGraph.outputs());
            serverGraph.executionPulses().forEach(pulses::addProperty);
        }
        resp.add("runtime", runtime);
        resp.add("inputs", liveInputs);
        resp.add("outputs", liveOutputs);
        resp.add("executionPulses", pulses);
        return resp;
    }

    // Add the runtime values
    private static void addRuntimeValues(JsonObject runtime, JsonObject flat,
                                         Map<String, AdvancedGraphDocument.Value> values) {
        values.forEach((key, val) -> {
            JsonElement jsonValue = graphValueJson(val);
            flat.add(key, jsonValue.deepCopy());
            int separator = key.lastIndexOf(':');
            if (separator <= 0 || separator >= key.length() - 1) return;
            String nodeId = key.substring(0, separator);
            String port = key.substring(separator + 1);
            JsonObject node = runtime.has(nodeId)
                    ? runtime.getAsJsonObject(nodeId) : new JsonObject();
            node.add(port, jsonValue);
            runtime.add(nodeId, node);
        });
    }

    // Get the graph value JSON
    private static JsonElement graphValueJson(AdvancedGraphDocument.Value val) {
        if (val == null) return com.google.gson.JsonNull.INSTANCE;
        return switch (val.type()) {
            case "number" -> GSON.toJsonTree(val.asNumber());
            case "boolean" -> GSON.toJsonTree(val.asBoolean());
            case "string", "direction" -> GSON.toJsonTree(val.asString());
            default -> NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, val.payload());
        };
    }

    // Read the server open graph
    private static ServerOpenGraph readServerOpenGraph() {
        MinecraftServer minecraftServer = ServerLifecycleHooks.getCurrentServer();
        if (minecraftServer == null) return null;
        CompletableFuture<ServerOpenGraph> res = new CompletableFuture<>();
        minecraftServer.execute(() -> {
            ServerOpenGraph found = null;
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                if (!(player.containerMenu instanceof AdvancedContraptionControllerMenu menu)) continue;
                AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
                if (controller == null) continue;
                found = new ServerOpenGraph(
                        controller.getDraftGraph(),
                        controller.getGraphLiveInputsSnapshot(),
                        controller.getGraphLiveOutputsSnapshot(),
                        controller.getGraphExecutionPulsesSnapshot());
                break;
            }
            res.complete(found);
        });
        try {
            return res.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return null;
        }
    }

    // Get the manifest list JSON
    private static JsonObject manifestListJson() {
        JsonObject root = new JsonObject();
        JsonArray manifests = new JsonArray();
        for (ControllerManifestStore.SharedGraphEntry entry : ControllerManifestStore.listSharedGraphs()) {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("id", entry.id());
            manifest.addProperty("name", entry.name());
            manifests.add(manifest);
        }
        root.add("manifests", manifests);
        return root;
    }

    // Save the mode
    private static ControllerManifestStore.SharedGraphSaveMode saveMode(String val) {
        if (val == null) return ControllerManifestStore.SharedGraphSaveMode.REJECT;
        return switch (val.toLowerCase(Locale.ROOT)) {
            case "overwrite" -> ControllerManifestStore.SharedGraphSaveMode.OVERWRITE;
            case "increment" -> ControllerManifestStore.SharedGraphSaveMode.INCREMENT;
            default -> ControllerManifestStore.SharedGraphSaveMode.REJECT;
        };
    }

    // Query the parameters
    private static Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> res = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return res;
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String val = separator < 0 ? "" : pair.substring(separator + 1);
            res.put(decode(key), decode(val));
        }
        return res;
    }

    // Decode the controller graph web server
    private static String decode(String val) {
        return URLDecoder.decode(val == null ? "" : val, StandardCharsets.UTF_8);
    }

    // Read the limited
    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 64 * 1024));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (output.size() + read > maximum) throw new RequestTooLargeException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    // Send the resource
    private static void sendResource(HttpExchange exchange, StaticResource resource) throws IOException {
        try (InputStream input = ControllerGraphWebServer.class.getResourceAsStream(
                RESOURCE_ROOT + resource.path())) {
            if (input == null) {
                sendJsonError(exchange, 404, "Packaged editor resource is missing");
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", resource.contentType());
            headers.set("Cache-Control", resource.path().startsWith("vendor/")
                    ? "public, max-age=31536000, immutable" : "no-store");
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            input.transferTo(exchange.getResponseBody());
        }
    }

    // Send the JSON error
    private static void sendJsonError(HttpExchange exchange, int status, String msg) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", msg);
        sendJson(exchange, status, body);
    }

    // Send the JSON
    private static void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8",
                GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    // Send the HTML
    private static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    // Send the controller graph web server
    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    // Apply the common headers
    private static void applyCommonHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: blob:; connect-src 'self'; worker-src 'self' blob:; "
                        + "font-src 'self' data:");
    }

    // Store the static resource
    private record StaticResource(String path, String contentType) {
    }

    // Store the published open graph
    private record PublishedOpenGraph(String name, AdvancedGraphDocument graph,
                                      int fingerprint, long updatedAt) {
    }

    // Store the pending remote import
    private record PendingRemoteImport(String graphId, long expiresAt) {
    }

    // Store the server open graph
    private record ServerOpenGraph(AdvancedGraphDocument graph,
                                   Map<String, AdvancedGraphDocument.Value> inputs,
                                   Map<String, AdvancedGraphDocument.Value> outputs,
                                   Map<String, Long> executionPulses) {
    }

    // Handle the request too large exception
    private static final class RequestTooLargeException extends IOException {
    }
}
