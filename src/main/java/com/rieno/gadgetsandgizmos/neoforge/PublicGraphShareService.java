package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

// Check and publish graph manifests through the configured sharing service
public final class PublicGraphShareService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final URI PUBLIC_SITE = URI.create("https://gg-mc.com/");
    public static final int GRAPH_ID_LENGTH = 16;

    private static final URI STATUS_URI = PUBLIC_SITE.resolve("api/status.php");
    private static final URI UPLOAD_URI = PUBLIC_SITE.resolve("api/upload.php");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long STATUS_CACHE_MILLIS = 10_000L;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cached availability
    private static volatile Availability cachedAvailability = new Availability(false, "Not checked");
    // Availability cache time
    private static volatile long cachedAvailabilityAt;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the public graph share service
    private PublicGraphShareService() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the availability
    public static CompletableFuture<Availability> availability() {
        long now = System.currentTimeMillis();
        if (now - cachedAvailabilityAt < STATUS_CACHE_MILLIS) {
            return CompletableFuture.completedFuture(cachedAvailability);
        }
        HttpRequest req = baseRequest(STATUS_URI).GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    Availability availability;
                    if (resp.statusCode() / 100 != 2 || resp.body().length() > 16_384) {
                        availability = new Availability(false, "Public graph sharing is unavailable");
                    } else {
                        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
                        boolean available = body.has("available") && body.get("available").getAsBoolean();
                        availability = new Availability(available,
                                available ? "Public graph sharing is available"
                                        : "Public graph sharing is unavailable");
                    }
                    cacheAvailability(availability);
                    return availability;
                })
                .exceptionally(error -> {
                    Availability availability = new Availability(false, "Public graph sharing is unavailable");
                    cacheAvailability(availability);
                    return availability;
                });
    }

    // Upload the public graph
    public static CompletableFuture<ShareResult> upload(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()
                || manifestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > ControllerGraphWebServer.MAX_MANIFEST_BYTES) {
            return CompletableFuture.completedFuture(
                    new ShareResult(false, "", "", "The graph manifest is empty or too large"));
        }
        HttpRequest req = baseRequest(UPLOAD_URI)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(manifestJson))
                .build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> parseUploadResponse(resp.statusCode(), resp.body()))
                .exceptionally(error -> new ShareResult(
                        false, "", "", "Public graph sharing is unavailable"));
    }

    // Get the download
    public static CompletableFuture<String> download(String graphId) {
        String id = normalizeGraphId(graphId);
        if (id.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid graph id"));
        }
        URI manifestUri = PUBLIC_SITE.resolve("api/manifest.php?id=" + id);
        HttpRequest req = baseRequest(manifestUri).GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenCompose(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Shared graph was not found"));
                    }
                    if (resp.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > MAX_RESPONSE_BYTES) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Shared graph is too large"));
                    }
                    return CompletableFuture.completedFuture(resp.body());
                });
    }

    // Get the share URL
    public static String shareUrl(String graphId) {
        String id = normalizeGraphId(graphId);
        return id.isBlank() ? "" : PUBLIC_SITE.resolve(id).toString();
    }

    // Normalize the graph id
    public static String normalizeGraphId(String graphId) {
        String id = graphId == null ? "" : graphId.trim().toLowerCase(Locale.ROOT);
        return id.matches("[a-f0-9]{" + GRAPH_ID_LENGTH + "}") ? id : "";
    }

    // Parse the upload response
    static ShareResult parseUploadResponse(int statusCode, String responseBody) {
        if (statusCode / 100 != 2 || responseBody == null
                || responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16_384) {
            return new ShareResult(false, "", "", "The public graph server rejected the upload");
        }
        try {
            JsonObject body = JsonParser.parseString(responseBody).getAsJsonObject();
            String id = normalizeGraphId(body.has("id") ? body.get("id").getAsString() : "");
            if (id.isBlank()) {
                return new ShareResult(false, "", "", "The public graph server returned an invalid id");
            }
            return new ShareResult(true, id, shareUrl(id), "Graph uploaded");
        } catch (RuntimeException ignored) {
            return new ShareResult(false, "", "", "The public graph server returned invalid JSON");
        }
    }

    // Get the base request
    private static HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Create-Gadgets-and-Gizmos-Graph-Share/1");
    }

    // Cache the availability
    private static void cacheAvailability(Availability availability) {
        cachedAvailability = availability;
        cachedAvailabilityAt = System.currentTimeMillis();
    }

    // Store the availability
    public record Availability(boolean available, String message) {
    }

    // Store share results
    public record ShareResult(boolean shared, String id, String url, String message) {
    }
}
