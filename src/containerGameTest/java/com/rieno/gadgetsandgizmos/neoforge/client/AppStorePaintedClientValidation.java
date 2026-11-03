package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDescriptions;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Exercise the painted store with server-shaped snapshots and recorded purchase requests
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class AppStorePaintedClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.appStorePaintedValidation");
    private static FixtureScreen screen;
    private static int stage;
    private static int ticks;
    private static boolean complete;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 60) return;
        mc.options.pauseOnLostFocus = false;
        try{
            if(screen == null){ screen = new FixtureScreen(); mc.setScreen(screen); }
            if(++ticks > 600) throw new AssertionError("Painted store timed out at stage " + stage);
            if(ticks < 20) return;
            switch(stage){
                case 0 -> {
                    require(screen.renderer.ownsTabletSurface(), "Store did not request the complete tablet frame");
                    capture("app-store-home.png");
                    require(selected().isEmpty(), "Store opened a listing before one was selected");
                    screen.click(60, 55);
                    require(selected().equals(app("block360").id().toString()), "First card selected the wrong app");
                    advance();
                }
                case 1 -> {
                    capture("app-store-installed-detail.png");
                    screen.actions.clear(); screen.click(280, 75);
                    require(screen.actions.isEmpty(), "Installed app retained a purchase control");
                    screen.click(20, 26);
                    advance();
                }
                case 2 -> {
                    screen.renderer.mouseScrolled(screen.ctx(null, 0, 0), screen.state, screen.x(150), screen.y(100), 0, -3);
                    require(scroll("discover").offset() > 0, "Discover gallery did not scroll");
                    screen.click(20, 172);
                    require(selected().isEmpty(), "Clipped cards could be selected outside the gallery");
                    advance();
                }
                case 3 -> {
                    capture("app-store-home-scrolled.png");
                    screen.click(250, 24);
                    for(char ch : "jOuRnEy".toCharArray()) screen.renderer.chatTyped(screen.ctx(null, 0, 0), screen.state, ch, 0);
                    advance();
                }
                case 4 -> {
                    require(scroll("discover").offset() == 0, "Search retained a hidden gallery offset");
                    screen.click(60, 55);
                    require(selected().equals(app("journey").id().toString()), "Search was not case insensitive");
                    advance();
                }
                case 5 -> {
                    capture("app-store-install-detail.png");
                    screen.actions.clear(); screen.click(280, 75); screen.click(280, 75);
                    require(screen.actions.equals(List.of("purchase_app:" + app("journey").id())), "Install did not send exactly one server request");
                    row(app("journey")).putBoolean("Installed", true);
                    advance();
                }
                case 6 -> {
                    require(selected().equals(app("journey").id().toString()), "Snapshot refresh discarded the selected listing");
                    screen.actions.clear(); screen.click(280, 75);
                    require(screen.actions.isEmpty(), "Updated installation still accepted purchases");
                    screen.click(60, 115);
                    require(selected().equals(app("journey").id().toString()), "Explore heading selected an app");
                    screen.click(60, 170);
                    require(selected().equals(app("journey").id().toString()), "Explore cards accepted a click below the painted panel");
                    screen.click(60, 130);
                    require(selected().equals(app("block360").id().toString()), "Explore card did not open its listing");
                    advance();
                }
                case 7 -> {
                    screen.renderer.mouseScrolled(screen.ctx(null, 0, 0), screen.state, screen.x(160), screen.y(150), 0, -1);
                    require(scroll("explore").offset() > 0, "Explore applications did not scroll");
                    stage = 16; ticks = 0;
                }
                case 16 -> {
                    capture("app-store-explore-scrolled.png");
                    require(screen.renderer.keyPressed(screen.ctx(null, 0, 0), screen.state, GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape did not return to browsing");
                    require(selected().isEmpty(), "Escape kept the detail view open");
                    screen.click(80, 26);
                    stage = 8; ticks = 0;
                }
                case 8 -> {
                    capture("app-store-installed.png");
                    screen.click(160, 55);
                    require(selected().equals(app("gg_auto").id().toString()), "Installed filter included an unavailable app");
                    screen.click(20, 26);
                    screen.click(250, 24);
                    for(char ch : "Digisable".toCharArray()) screen.renderer.chatTyped(screen.ctx(null, 0, 0), screen.state, ch, 0);
                    advance();
                }
                case 9 -> {
                    screen.click(60, 55);
                    require(selected().equals(PaidTabletApps.DIGISABLE.id().toString()), "Paid search selected another app");
                    advance();
                }
                case 10 -> {
                    capture("app-store-paid-detail.png");
                    screen.actions.clear(); screen.click(280, 75);
                    require(screen.actions.equals(List.of("purchase_app:" + PaidTabletApps.DIGISABLE.id())), "Paid app did not route purchase through the server");
                    screen.data.putString("Error", "You cannot afford this application");
                    advance();
                }
                case 11 -> {
                    capture("app-store-error.png");
                    screen.data.remove("Error");
                    screen.rows().remove(row(PaidTabletApps.DIGISABLE));
                    advance();
                }
                case 12 -> {
                    require(selected().isEmpty(), "Removed apps retained stale detail views");
                    screen.projected = true;
                    advance();
                }
                case 13 -> {
                    screen.actions.clear();
                    screen.renderer.mouseClicked(screen.ctx(null, 0, 0), screen.state, screen.x(250), screen.y(24), 1);
                    require(screen.actions.contains("edit:Search"), "Projected search did not use the host dialog");
                    advance();
                }
                case 14 -> {
                    screen.click(60, 55);
                    require(selected().equals(app("redstone_link").id().toString()), "Dialog search did not select an owned app");
                    advance();
                }
                case 15 -> {
                    screen.actions.clear(); screen.click(280, 75);
                    require(screen.actions.equals(List.of("purchase_app:" + app("redstone_link").id())), "Owned app did not route reinstallation");
                    finish("PASSED: painted Discover and Installed galleries; installed, free, paid and owned listings; clipped scrolling, search, details and recommendations; one purchase request per click sequence; server snapshot installation updates; removal, Escape and projected search");
                }
                default -> throw new AssertionError("Unknown painted store stage");
            }
        }catch(Throwable err){ LogUtils.getLogger().error("Painted App Store validation failed", err); finish("FAILED: " + err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Object field(String name) throws ReflectiveOperationException{
        var field = screen.state.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen.state);
    }

    private static String selected() throws ReflectiveOperationException{ return (String)field("selectedId"); }
    private static ListScrollState scroll(String name) throws ReflectiveOperationException{ return (ListScrollState)field(name); }
    private static void require(boolean val, String msg){ if(!val) throw new AssertionError(msg); }
    private static void advance(){ stage++; ticks = 0; }
    private static void capture(String name){ var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {}); }

    private static void finish(String msg){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), msg); }catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(msg);
        Minecraft.getInstance().stop();
    }

    private static TabletAppDefinition app(String id){ return TabletAppRegistry.definition(DiagnosticTabletData.appId(id)); }

    private static CompoundTag row(TabletAppDefinition app){
        return screen.rows().stream().map(tag -> (CompoundTag)tag)
                .filter(tag -> tag.getString("Id").equals(app.id().toString())).findFirst().orElseThrow();
    }

    private static CompoundTag fixture(){
        CompoundTag data = new CompoundTag();
        ListTag apps = new ListTag();
        List<TabletAppDefinition> definitions = new ArrayList<>(List.of(app("block360"),
                app("gg_auto"), PaidTabletApps.BLOCKMATES, app("journey"),
                app("rdp"), PaidTabletApps.CCTV, app("nfc"),
                PaidTabletApps.DIGISABLE, app("redstone_link"), app("scm"), PaidTabletApps.MANIFEST));
        for(int idx = 0; idx < definitions.size(); idx++){
            var app = definitions.get(idx);
            CompoundTag row = new CompoundTag();
            row.putString("Id", app.id().toString()); row.putString("Name", app.title().getString());
            row.putString("Description", app.description().getString());
            row.putString("Details", TabletAppDescriptions.details(app).getString());
            row.putBoolean("Installed", idx == 0 || idx == 1 || idx == 4 || idx == 6);
            row.putBoolean("Owned", idx == 8 || row.getBoolean("Installed"));
            row.putString("PriceItem", "minecraft:emerald");
            row.putInt("PriceCount", idx == 7 || idx == 8 ? 16 : 0);
            apps.add(row);
        }
        data.put("Apps", apps);
        return data;
    }

    private static final class FixtureScreen extends Screen{
        private final TabletAppDefinition app = TabletAppRegistry.definition(DiagnosticTabletData.appId("app_store"));
        private final TabletAppClientRenderer renderer = TabletAppClientRegistry.renderer(app.id());
        private final TabletAppClientState state = renderer.createScreenState();
        private final CompoundTag data = fixture();
        private final List<String> actions = new ArrayList<>();
        private boolean projected;

        private FixtureScreen(){ super(Component.literal("Painted App Store validation")); }
        private double x(int x){ return (width - 492) / 2 + x * 492D / 320; }
        private double y(int y){ return (height - 286) / 2 + y * 286D / 180; }
        private boolean click(int x, int y){ return renderer.mouseClicked(ctx(null, 0, 0), state, x(x), y(y), 0); }
        private ListTag rows(){ return data.getList("Apps", net.minecraft.nbt.Tag.TAG_COMPOUND); }

        private TabletAppClientContext ctx(GuiGraphics graphics, int mouseX, int mouseY){
            return new TabletAppClientContext(app, app.tabs().getFirst(), data, graphics, Minecraft.getInstance().font,
                    0, 0, 320, 180, new TabletAppClientSurface((width - 492) / 2, (height - 286) / 2, 492, 286), mouseX, mouseY,
                    (action, val) -> actions.add(action + ":" + val), () -> {}, new TabletAppClientUi(){
                        @Override public boolean projected(){ return projected; }
                        @Override public void editText(String prompt, String initial, int limit, java.util.function.Consumer<String> accept){
                            actions.add("edit:" + prompt); accept.accept("Redstone");
                        }
                    });
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick){
            graphics.fill(0, 0, width, height, 0xFF17191C);
            renderer.render(ctx(graphics, mouseX, mouseY), state);
        }
        @Override public boolean isPauseScreen(){ return false; }
    }
}
