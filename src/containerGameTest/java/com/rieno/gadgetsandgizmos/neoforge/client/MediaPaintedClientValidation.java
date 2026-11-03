package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletMediaApp;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.media.MediaSession;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.Media;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// Check painted controls and capture the player and playlist editor in an isolated client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class MediaPaintedClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.mediaPaintedValidation");
    private static Fixture fixture;
    private static int stage;
    private static int ticks;
    private static boolean complete;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 40) return;
        mc.options.pauseOnLostFocus = false;
        try{
            if(fixture == null){
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                fixture = new Fixture();
                mc.setScreen(fixture);
            }
            if(++ticks < 15) return;
            ticks = 0;
            switch(stage++){
                case 0 -> {
                    capture("media-player.png");
                    fixture.click(130, 70);
                    require(fixture.session.playback().playing(), "Disc card did not start playback");
                    fixture.click(50, 165);
                }
                case 1 -> { fixture.click(135, 43); }
                case 2 -> {
                    require(fixture.session.playlists().size() == 1, "New playlist did not save");
                    capture("media-playlist-editor.png");
                    fixture.click(159, 60);
                    fixture.click(227, 60);
                    require(fixture.session.playlists().getFirst().discs().size() == 2, "Membership controls did not add discs");
                }
                case 3 -> {
                    capture("media-playlist-members.png");
                    fixture.click(291, 43);
                }
                case 4 -> {
                    capture("media-playlists.png");
                    fixture.click(140, 63);
                    require(fixture.session.selected() != null && fixture.session.playback().playing(), "Playlist selection did not play");
                    fixture.click(292, 63);
                }
                case 5 -> {
                    fixture.click(159, 60);
                    require(fixture.session.playlists().getFirst().discs().size() == 1, "Membership control did not remove a disc");
                    fixture.click(291, 43);
                }
                case 6 -> {
                    fixture.click(250, 43);
                }
                case 7 -> {
                    fixture.click(120, 26);
                    for(char ch : "no_matching_music_disc".toCharArray()) fixture.renderer.chatTyped(fixture.ctx(null), fixture.state, ch, 0);
                }
                case 8 -> { capture("media-search-empty.png"); finish("PASS"); }
            }
        }catch(Throwable err){ finish("FAIL " + err); }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }

    private static void capture(String name){
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {});
    }

    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), result); }catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }

    private static final class Fixture extends Screen{
        private final Media renderer = new Media();
        private final TabletAppClientState state = renderer.createScreenState();
        private final MediaSession session = new MediaSession();

        private Fixture(){ super(Component.literal("Media validation")); }
        @Override public boolean isPauseScreen(){ return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial){
            graphics.fill(0, 0, width, height, 0xFF151515);
            renderer.render(ctx(graphics), state);
        }

        private TabletAppClientContext ctx(GuiGraphics graphics){
            var app = TabletAppRegistry.definition(DiagnosticTabletMediaApp.ID);
            return new TabletAppClientContext(app, app.tabs().getFirst(), session.toTag(), graphics,
                    Minecraft.getInstance().font, 0, 0, width, height, new TabletAppClientSurface(0, 0, width, height),
                    0, 0, this::action, () -> {}, new TabletAppClientUi(){
                @Override public void editText(String prompt, String initial, int maximum, java.util.function.Consumer<String> accept){
                    accept.accept(initial.isEmpty() ? "Ship music" : initial);
                }
            });
        }

        private void click(int x, int y){
            require(renderer.mouseClicked(ctx(null), state, width * x / 320D, height * y / 180D, 0), "Control did not receive click: " + x + "," + y);
        }

        private void action(String action, String val){
            var mc = Minecraft.getInstance();
            long now = mc.level.getGameTime();
            java.util.function.ToIntFunction<ResourceLocation> lengths = id -> {
                var track = MusicDiscCatalog.resolve(mc.level.registryAccess(), id);
                return track == null ? 0 : track.durationTicks();
            };
            switch(action){
                case "play" -> session.play(ResourceLocation.parse(val), now, lengths);
                case "playlist_create" -> session.create(val);
                case "playlist_select" -> session.select(val.isEmpty() ? null : UUID.fromString(val), now, lengths);
                case "playlist_toggle" -> {
                    var parts = val.split("\\|", 2);
                    session.toggle(UUID.fromString(parts[0]), ResourceLocation.parse(parts[1]), now, lengths);
                }
                default -> throw new AssertionError("Unexpected action " + action);
            }
        }
    }
}
