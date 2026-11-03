package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStoreConfig;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppIcons;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyles;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Verify real config migration, reload synchronization and icon rendering in an isolated client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class TabletAppIconsClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.tabletAppIconsValidation");
    private static final TabletAppDefinition CUSTOM = new TabletAppDefinition(ResourceLocation.parse("test:custom_icon"),
            Component.literal("Custom"), Component.empty(), 0x332211, List.of());
    private static String original;
    private static int stage, ticks, customDraws;
    private static boolean complete;
    private static CompletableFuture<Void> reload;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 60) return;
        try{
            if(++ticks < 30) return;
            if(ticks > 1200) throw new AssertionError("Icon validation timed out at stage " + stage);
            if(reload != null && !reload.isDone()) return;
            if(reload != null) reload.join();
            switch(stage){
                case 0 -> {
                    mc.options.pauseOnLostFocus = false;
                    GLFW.glfwHideWindow(mc.getWindow().getWindow());
                    for(var app : TabletAppRegistry.apps()){
                        var style = TabletAppIconStyles.style(app);
                        require(style.forceFallback(), "Login did not synchronize forced fallback for " + app.id());
                        require(BuiltInRegistries.ITEM.getOptional(style.item()).orElse(Items.AIR) != Items.AIR, "Unknown default icon item " + style.item());
                        require(!style.item().getPath().equals("paper"), "Bundled app has no matching item: " + app.id());
                    }
                    original = Files.readString(DiagnosticTabletAppStoreConfig.path());
                    TabletAppIcons.register(CUSTOM.id(), (graphics, x, y, size) -> {
                        customDraws++;
                        graphics.fill(x, y, x + size, y + size, 0xFF00FF00);
                    });
                    mc.setScreen(new Fixture());
                    advance();
                }
                case 1 -> {
                    capture("app-icons-fallback.png");
                    JsonObject root = config();
                    var apps = root.getAsJsonObject("apps");
                    apps.getAsJsonObject("createthrusters:media").remove("icon");
                    apps.getAsJsonObject("createthrusters:media").addProperty("count", 37);
                    apps.getAsJsonObject("createthrusters:cctv").getAsJsonObject("icon").addProperty("force_fallback", false);
                    saveAndReload(root);
                    advance();
                }
                case 2 -> {
                    if(style("cctv").forceFallback()) return;
                    var media = config().getAsJsonObject("apps").getAsJsonObject("createthrusters:media");
                    require(media.get("count").getAsInt() == 37, "Migration changed an existing price");
                    require(media.getAsJsonObject("icon").get("force_fallback").getAsBoolean(), "Migration did not restore editable icon defaults");
                    require(!config().getAsJsonObject("apps").getAsJsonObject("createthrusters:cctv")
                            .getAsJsonObject("icon").get("force_fallback").getAsBoolean(), "Migration overwrote the painted icon preference");
                    capture("app-icons-painted-cctv.png");
                    var root = config();
                    var configured = root.getAsJsonObject("apps").getAsJsonObject("createthrusters:media");
                    configured.remove("item");
                    configured.remove("count");
                    configured.add("icon",
                            new TabletAppIconStyle(0xFF00FF, ResourceLocation.parse("minecraft:diamond_block"), true).toJson());
                    saveAndReload(root);
                    advance();
                }
                case 3 -> {
                    if(style("media").color() != 0xFF00FF) return;
                    require(style("media").item().equals(ResourceLocation.parse("minecraft:diamond_block")), "Reload did not synchronize the icon item");
                    require(DiagnosticTabletAppStoreConfig.price(TabletAppRegistry.definition(DiagnosticTabletData.appId("media"))).free(),
                            "An icon-only override changed Media's free price");
                    capture("app-icons-configured.png");
                    var styles = new HashMap<ResourceLocation, TabletAppIconStyle>();
                    for(var app : TabletAppRegistry.apps()) styles.put(app.id(), TabletAppIconStyles.style(app));
                    styles.put(CUSTOM.id(), new TabletAppIconStyle(0xFF00FF, ResourceLocation.parse("minecraft:chest"), true));
                    TabletAppIconStyles.apply(styles);
                    customDraws = 0;
                    advance();
                }
                case 4 -> {
                    require(customDraws == 0, "Forced fallback still invoked a custom icon renderer");
                    capture("app-icons-custom-forced.png");
                    var styles = new HashMap<ResourceLocation, TabletAppIconStyle>();
                    for(var app : TabletAppRegistry.apps()) styles.put(app.id(), TabletAppIconStyles.style(app));
                    styles.put(CUSTOM.id(), new TabletAppIconStyle(0xFF00FF, ResourceLocation.parse("minecraft:chest"), false));
                    TabletAppIconStyles.apply(styles);
                    advance();
                }
                case 5 -> {
                    require(customDraws > 0, "Disabling forced fallback did not restore the registered renderer");
                    capture("app-icons-custom-restored.png");
                    finish("PASS");
                }
            }
        }catch(Throwable error){
            error.printStackTrace();
            finish("FAIL " + error);
        }
    }

    private static TabletAppIconStyle style(String app){ return TabletAppIconStyles.style(TabletAppRegistry.definition(DiagnosticTabletData.appId(app))); }
    private static JsonObject config() throws Exception{ return JsonParser.parseString(Files.readString(DiagnosticTabletAppStoreConfig.path())).getAsJsonObject(); }
    private static void advance(){ stage++; ticks = 0; }
    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }

    private static void saveAndReload(JsonObject root) throws Exception{
        Files.writeString(DiagnosticTabletAppStoreConfig.path(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
        reload = new CompletableFuture<>();
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((res, error) -> {
            if(error == null) reload.complete(null); else reload.completeExceptionally(error);
        }));
    }

    private static void capture(String name){
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }

    private static void finish(String res){
        complete = true;
        try{
            if(original != null) Files.writeString(DiagnosticTabletAppStoreConfig.path(), original);
            Files.writeString(Path.of(OUTPUT), res);
        }catch(Exception error){ throw new RuntimeException(error); }
        Minecraft.getInstance().stop();
    }

    private static final class Fixture extends Screen{
        private Fixture(){ super(Component.literal("Tablet app icons")); }
        @Override public boolean isPauseScreen(){ return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta){
            graphics.fill(0, 0, width, height, 0xFF202020);
            int idx = 0;
            for(var app : TabletAppRegistry.apps()){
                int x = 20 + idx % 7 * 52, y = 25 + idx / 7 * 65;
                TabletAppIcons.render(app, graphics, x, y, 32);
                graphics.drawString(font, app.id().getPath().substring(0, Math.min(8, app.id().getPath().length())), x, y + 36, 0xFFFFFFFF, false);
                idx++;
            }
            TabletAppIcons.render(CUSTOM, graphics, 20, 175, 32);
        }
    }
}
