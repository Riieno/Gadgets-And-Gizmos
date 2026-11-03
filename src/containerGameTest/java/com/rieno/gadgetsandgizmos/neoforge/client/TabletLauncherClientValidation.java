package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletSettingsApp;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLauncherLayout;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLauncherResources;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.ui.TextInputScreen;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppUsage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletLauncherThemes;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.glfw.GLFW;

// Exercise painted launchers, settings actions and pack reloads in an isolated game
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class TabletLauncherClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.tabletLauncherValidation");
    private static final UUID TABLET_ID = UUID.randomUUID();
    private static final ResourceLocation SETTINGS = DiagnosticTabletData.appId("settings");
    private static volatile boolean prepared;
    private static boolean started;
    private static boolean complete;
    private static int stage;
    private static int ticks;
    private static CompletableFuture<Void> reload;
    private static ResourceLocation runApp;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        mc.options.pauseOnLostFocus = false;
        try{
            if(!started){
                started = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayers().getFirst();
                    ItemStack stack = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
                    DiagnosticTabletData.write(stack, DiagnosticTabletData.State.DEFAULT.withTabletId(TABLET_ID));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var database = DiagnosticTabletDatabase.forServer(server);
                    for(var app : TabletAppRegistry.apps()) database.setInstalled(TABLET_ID, app.id(), true);
                    for(int idx = 0; idx < 5; idx++) TabletAppUsage.recordLaunch(database, TABLET_ID, SETTINGS);
                    prepared = true;
                });
            }
            if(++ticks > 1200) throw new AssertionError("Launcher validation timed out at stage " + stage + ", screen " + mc.screen);
            if(!prepared || !mc.player.getMainHandItem().is(CTItems.DIAGNOSTIC_TABLET.get())) return;
            if(stage == 11 && ticks >= 30 && mc.screen instanceof ControllerTextInputScreen dialog){
                var field = TextInputScreen.class.getDeclaredField("input");
                field.setAccessible(true);
                ((EditBox)field.get(dialog)).setValue("Painted Tablet");
                dialog.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                advance();
                return;
            }
            if(stage == 0){
                if(mc.player.tickCount < 60) return;
                DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem());
                advance();
                return;
            }
            if(!(mc.screen instanceof DiagnosticTabletScreen<?> screen) || ticks < 30) return;
            CompoundTag settings = settings(screen);
            if(settings.getList("Apps", Tag.TAG_COMPOUND).isEmpty()) return;
            int left = coordinate(screen, "left");
            int top = coordinate(screen, "top");
            switch(stage){
                case 1 -> {
                    require(TabletLauncherResources.themes(settings.getList("Wallpapers", Tag.TAG_COMPOUND)).size() == 9,
                            "Client pack themes were not discovered");
                    require(TabletLauncherThemes.dataPackThemes().stream().anyMatch(theme -> theme.title().equals("Server Forest")),
                            "Server data pack theme was not discovered");
                    require(texture(screen).equals(DiagnosticTabletSettingsApp.DEFAULT_LAUNCHER_TEXTURE), "Incorrect default launcher");
                    capture("launcher-streaks.png");
                    var rect = TabletLauncherLayout.quickLaunch(TabletLauncherLayout.bounds(left, top, 492, 286), 0);
                    require(screen.mouseClicked(rect.left() + 5, rect.top() + 5, 0), "Quicklaunch did not handle its painted slot");
                    require(state(screen).app().equals(SETTINGS), "Quicklaunch did not open the most used app");
                    advance();
                }
                case 2 -> {
                    TabletAppClientContext ctx = context(screen);
                    require(ctx.surface().left() == left && ctx.surface().top() == top
                            && ctx.surface().width() == 492 && ctx.surface().height() == 286,
                            "Settings did not receive the full painted tablet frame");
                    require(ctx.app().tabs().getFirst().title().getString().equals("Themes"), "Tablet tab was not renamed");
                    require(settings.getUUID("TabletId").equals(TABLET_ID), "Settings snapshot lost tablet identity");
                    capture("settings-themes.png");
                    click(screen, 200, 65);
                    advance();
                }
                case 3 -> {
                    if(!settings.getString("Wallpaper").endsWith("launcher_ice.png")) return;
                    select(screen, "home", "");
                    advance();
                }
                case 4 -> {
                    require(texture(screen).getPath().endsWith("launcher_ice.png"), "Theme selection was not applied");
                    capture("launcher-ice.png");
                    select(screen, "settings", "tablet");
                    scroll(screen, -6);
                    advance();
                }
                case 5 -> {
                    capture("settings-themes-scrolled.png");
                    click(screen, 130, 60);
                    advance();
                }
                case 6 -> {
                    if(!settings.getString("Wallpaper").startsWith("launcher_validation:")) return;
                    select(screen, "home", "");
                    advance();
                }
                case 7 -> {
                    require(texture(screen).getNamespace().equals("launcher_validation"), "Pack theme was not selected");
                    capture("launcher-pack-theme.png");
                    select(screen, "settings", "tablet");
                    click(screen, 45, 67);
                    advance();
                }
                case 8 -> {
                    require(state(screen).tab().equals("apps"), "Apps sidebar did not update the saved tab");
                    capture("settings-apps.png");
                    require(scroll(screen, -4), "Application list did not scroll");
                    advance();
                }
                case 9 -> {
                    capture("settings-apps-scrolled.png");
                    click(screen, 45, 89);
                    advance();
                }
                case 10 -> {
                    require(state(screen).tab().equals("about"), "About sidebar did not update the saved tab");
                    capture("settings-about.png");
                    require(click(screen, 286, 136), "Painted rename control did not handle its bounds");
                    require(mc.screen instanceof ControllerTextInputScreen, "Rename control did not open the text dialog");
                    advance();
                }
                case 12 -> {
                    if(!settings.getString("Name").equals("Painted Tablet")) return;
                    capture("settings-about-renamed.png");
                    click(screen, 45, 67);
                    var apps = settings.getList("Apps", Tag.TAG_COMPOUND);
                    int idx = firstOptionalApp(apps);
                    runApp = ResourceLocation.parse(apps.getCompound(idx).getString("Id"));
                    scrollToApp(screen, idx);
                    require(click(screen, 265, 70 + Math.min(idx, 1) * 49), "Run control did not handle its bounds");
                    require(state(screen).app().equals(runApp), "Run control did not open the selected application");
                    advance();
                }
                case 13 -> {
                    select(screen, "settings", "apps");
                    advance();
                }
                case 14 -> {
                    var apps = settings.getList("Apps", Tag.TAG_COMPOUND);
                    int idx = firstOptionalApp(apps);
                    scrollToApp(screen, idx);
                    require(click(screen, 289, 70 + Math.min(idx, 1) * 49), "Remove control did not handle its bounds");
                    advance();
                }
                case 15 -> {
                    var apps = settings.getList("Apps", Tag.TAG_COMPOUND);
                    if(apps.getCompound(firstOptionalApp(apps)).getBoolean("Installed")) return;
                    select(screen, "home", "");
                    advance();
                }
                case 16 -> {
                    mc.getResourcePackRepository().setSelected(List.of("vanilla", "mod_resources"));
                    reload = mc.reloadResourcePacks();
                    advance();
                }
                case 17 -> {
                    if(!reload.isDone()) return;
                    reload.join();
                    require(TabletLauncherResources.themes(settings.getList("Wallpapers", Tag.TAG_COMPOUND)).size() == 3,
                            "Removed resource pack themes remained available");
                    require(texture(screen).equals(DiagnosticTabletSettingsApp.DEFAULT_LAUNCHER_TEXTURE), "Missing theme did not fall back");
                    capture("launcher-reload-fallback.png");
                    finish("PASSED: painted launcher; full settings atlas and sidebar; theme selection, clipping and scrolling; application list scrolling, Run and Remove; About identity and rename persistence; data pack discovery; resource pack removal and fallback");
                }
                default -> throw new AssertionError("Unknown launcher validation stage " + stage);
            }
        }catch(Throwable err){
            LogUtils.getLogger().error("Tablet launcher validation failed", err);
            finish("FAILED: " + err);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static int coordinate(DiagnosticTabletScreen<?> screen, String name) throws ReflectiveOperationException{
        var field = DiagnosticTabletScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private static DiagnosticTabletData.State state(DiagnosticTabletScreen<?> screen) throws ReflectiveOperationException{
        var field = DiagnosticTabletScreen.class.getDeclaredField("state");
        field.setAccessible(true);
        return (DiagnosticTabletData.State)field.get(screen);
    }

    private static ResourceLocation texture(DiagnosticTabletScreen<?> screen) throws ReflectiveOperationException{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("launcherTexture");
        method.setAccessible(true);
        return (ResourceLocation)method.invoke(screen);
    }

    private static CompoundTag settings(DiagnosticTabletScreen<?> screen) throws ReflectiveOperationException{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("settingsData");
        method.setAccessible(true);
        return (CompoundTag)method.invoke(screen);
    }

    private static TabletAppClientContext context(DiagnosticTabletScreen<?> screen) throws ReflectiveOperationException{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("clientAppContext",
                com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition.class, GuiGraphics.class, int.class, int.class);
        method.setAccessible(true);
        return (TabletAppClientContext)method.invoke(screen, TabletAppRegistry.definition(SETTINGS), null, 0, 0);
    }

    private static boolean click(DiagnosticTabletScreen<?> screen, int x, int y) throws ReflectiveOperationException{
        return screen.mouseClicked(coordinate(screen, "left") + x * 492D / 320,
                coordinate(screen, "top") + y * 286D / 180, 0);
    }

    private static boolean scroll(DiagnosticTabletScreen<?> screen, double delta) throws ReflectiveOperationException{
        return screen.mouseScrolled(coordinate(screen, "left") + 220 * 492D / 320,
                coordinate(screen, "top") + 95 * 286D / 180, 0, delta);
    }

    private static void scrollToApp(DiagnosticTabletScreen<?> screen, int idx) throws ReflectiveOperationException{
        scroll(screen, 100);
        scroll(screen, -Math.max(0, idx - 1) * 49D / 18);
    }

    private static int firstOptionalApp(net.minecraft.nbt.ListTag apps){
        for(int idx = 0; idx < apps.size(); idx++){
            if(!apps.getCompound(idx).getBoolean("Required")) return idx;
        }
        throw new AssertionError("No optional application was available for management checks");
    }

    private static void select(DiagnosticTabletScreen<?> screen, String app, String tab) throws ReflectiveOperationException{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("select", ResourceLocation.class, String.class);
        method.setAccessible(true);
        method.invoke(screen, DiagnosticTabletData.appId(app), tab);
    }

    private static void capture(String name){
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
    }

    private static void advance(){ stage++; ticks = 0; }
    private static void require(boolean val, String msg){ if(!val) throw new AssertionError(msg); }

    private static void finish(String msg){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), msg); }
        catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(msg);
        Minecraft.getInstance().stop();
    }
}
