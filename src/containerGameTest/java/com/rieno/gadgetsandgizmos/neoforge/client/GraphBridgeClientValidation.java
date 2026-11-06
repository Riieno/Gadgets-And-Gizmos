package com.rieno.gadgetsandgizmos.neoforge.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.storage.SharedDocumentMetadata;
import com.rieno.gadgetsandgizmos.neoforge.ControllerGraphWebServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

// Verify the website bridge and painted Share modal without bundled editor assets
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class GraphBridgeClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.graphBridgeValidation");
    private static boolean complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        complete = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path out = Path.of(OUTPUT);
        String prevPort = System.getProperty("createthrusters.graphEditorPort");
        try{
            ControllerGraphWebServer.stopClient();
            int port;
            try(var socket = new ServerSocket(0)){ port = socket.getLocalPort(); }
            System.setProperty("createthrusters.graphEditorPort", Integer.toString(port));
            Path dir = Files.createTempDirectory(out, "shared-graphs-");
            check(ControllerGraphWebServer.startClient(dir, null), "The local bridge did not start");
            try(var client = HttpClient.newHttpClient()){
                URI base = URI.create("http://127.0.0.1:" + port);
                var status = request(client, base, "/api/status", "GET", "", "");
                check(status.statusCode() == 200 && JsonParser.parseString(status.body()).getAsJsonObject().get("running").getAsBoolean(), "Bridge status is unavailable");
                for(String path : new String[]{"/", "/index.html", "/app.js", "/app.css", "/nodes.json", "/graph-nodes.js", "/vendor/riestat-plugin-studio.js", "/fonts/PixelifySans-Variable.ttf"}){
                    check(request(client, base, path, "GET", "", "").statusCode() == 404, "An editor route remains available: " + path);
                }
                String importPath = "/api/import-remote/0123456789abcdef";
                var confirm = request(client, base, importPath, "GET", "", "");
                var token = Pattern.compile("name=\"token\" value=\"([^\"]+)\"").matcher(confirm.body());
                check(confirm.statusCode() == 200 && token.find() && confirm.body().contains("<form method=\"post\">"), "Website imports lost their confirmation form");
                check(!confirm.body().contains("app.js") && !confirm.body().contains("app.css"), "The import page depends on removed assets");
                check(request(client, base, importPath, "POST", "token=invalid", "application/x-www-form-urlencoded").statusCode() == 403, "Import confirmation was bypassed");
                var consume = ControllerGraphWebServer.class.getDeclaredMethod("consumeRemoteImportToken", String.class, String.class);
                consume.setAccessible(true);
                check((Boolean)consume.invoke(null, "0123456789abcdef", token.group(1)), "The website confirmation token is unusable");
                check(!(Boolean)consume.invoke(null, "0123456789abcdef", token.group(1)), "A confirmation token can be reused");
                check(request(client, base, "/api/import-remote/invalid", "GET", "", "").statusCode() == 400, "Invalid website graph ids were accepted");
                var metadata = new SharedDocumentMetadata("Original Author", UUID.fromString("4562d28f-e1f4-4c87-8b19-1c78eca279cb"),
                        Instant.parse("2026-01-01T00:00:00Z"), "createthrusters", "2.0.1", "1.21.1");
                String manifest = ControllerManifestStore.sharedGraphJson("Bridge Import Test", new AdvancedGraphDocument(), metadata);
                var saved = request(client, base, "/api/manifests?name=bridge-test&mode=increment", "POST", manifest, "application/json");
                check(saved.statusCode() == 200, "The bridge cannot import manifests: " + saved.body());
                String id = JsonParser.parseString(saved.body()).getAsJsonObject().get("id").getAsString();
                check(Files.isRegularFile(dir.resolve(id + ".json")), "The imported graph was not written to client storage");
                var loaded = request(client, base, "/api/manifests/" + id, "GET", "", "");
                var original = JsonParser.parseString(manifest).getAsJsonObject();
                var restored = JsonParser.parseString(loaded.body()).getAsJsonObject();
                check(loaded.statusCode() == 200 && loaded.body().contains("Original Author"), "Import lost the graph attribution");
                check(original.getAsJsonObject("linkerData").get("StoredGraphs").equals(restored.getAsJsonObject("linkerData").get("StoredGraphs")), "Import changed the graph payload");
                check(request(client, base, "/api/manifests", "GET", "", "").body().contains(id), "Imported graphs are missing from the shared list");
                check(request(client, base, "/api/nodes", "GET", "", "").statusCode() == 200, "The website node catalog API is unavailable");
            }
            renderModal(mc, out);
            Files.writeString(out.resolve("result.txt"), "PASSED bridge status, removed editor routes, website confirmation and single-use tokens, manifest import/export, attribution, catalog API and painted Share modal\n");
        }catch(Throwable err){
            var trace = new StringWriter();
            err.printStackTrace(new PrintWriter(trace));
            try{ Files.writeString(out.resolve("result.txt"), "FAILED\n" + trace); }
            catch(Exception ignored){}
        }finally{
            ControllerGraphWebServer.stopClient();
            if(prevPort == null) System.clearProperty("createthrusters.graphEditorPort");
            else System.setProperty("createthrusters.graphEditorPort", prevPort);
            mc.stop();
        }
    }

    private static HttpResponse<String> request(HttpClient client, URI base, String path, String method, String body, String contentType) throws Exception{
        var req = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if(!contentType.isBlank()) req.header("Content-Type", contentType);
        return client.send(req.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    // Check the removed button's area now belongs to the painted Share Link button
    private static void renderModal(Minecraft mc, Path out) throws Exception{
        check(Arrays.stream(AdvancedControllerShareModal.Control.values()).noneMatch(control -> control.name().equals("EDITOR")), "The Share modal still exposes the editor");
        var bounds = new UiRect(0, 0, AdvancedControllerShareModal.WIDTH, AdvancedControllerShareModal.HEIGHT);
        check(AdvancedControllerShareModal.Control.SHARE.bounds(bounds).contains(80, 105), "The Share Link button does not fill its painted row");
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "share_bridge_validation"))){
            renderer.render(680, 440, 340, 220, graphics -> AdvancedControllerShareModal.draw(graphics, mc.font, bounds, -1, -1, control -> true, false, ""));
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(680, 440, false)){
                image.downloadTexture(0, false);
                image.flipY();
                image.writeToFile(out.resolve("share-modal.png"));
            }
        }
    }

    private static void check(boolean valid, String msg){
        if(!valid) throw new AssertionError(msg);
    }
}
