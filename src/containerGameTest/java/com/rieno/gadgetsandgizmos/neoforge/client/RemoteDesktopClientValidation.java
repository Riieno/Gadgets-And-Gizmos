package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.client.ui.TextInputScreen;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiCanvasTransform;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.RemoteDesktop;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Exercise real reader pairing, rename/delete persistence and painted remote-action routing
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class RemoteDesktopClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.remoteDesktopValidation");
    private static final ResourceLocation RDP = DiagnosticTabletData.appId("rdp");
    private static final UUID TABLET = UUID.randomUUID();
    private static final BlockPos CONTROLLER = new BlockPos(6, -59, 2);
    private static int stage, ticks, originalScale;
    private static boolean started, complete;
    private static String deviceKey;
    private static CompletableFuture<Void> work;
    private static RouteFixture fixture;

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
            if(!started){
                require(CTFeatureToggles.isItemEnabled("diagnostic_tablet"), "Enable tablets in the isolated validation instance");
                started = true;
                mc.options.pauseOnLostFocus = false;
                originalScale = mc.options.guiScale().get(); mc.options.guiScale().set(2); mc.resizeDisplay();
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var level = player.serverLevel();
                    level.setBlockAndUpdate(CONTROLLER.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CONTROLLER, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
                    var stack = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
                    DiagnosticTabletData.write(stack, DiagnosticTabletData.State.DEFAULT.withTabletId(TABLET).withApp(RDP, "devices"));
                    player.getInventory().selected = 0; player.connection.send(new ClientboundSetCarriedItemPacket(0));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    DiagnosticTabletDatabase.forServer(player.server).setInstalled(TABLET, RDP, true);
                });
            }
            if(++ticks > 1200) throw new AssertionError("Remote Desktop validation timed out at stage " + stage);
            if(ticks < 25 || work != null && !work.isDone()) return;
            if(work != null) work.join();
            if(stage == 0){
                if(!TABLET.equals(DiagnosticTabletData.read(mc.player.getMainHandItem()).tabletId())) return;
                require(TabletAppClientRegistry.renderer(RDP) instanceof RemoteDesktop, "Painted Remote Desktop was not registered");
                DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem()); advance(); return;
            }
            if(stage == 2){
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    require(DiagnosticTabletData.read(player.getMainHandItem()).mode() == TabletInteractionMode.READER, "Add new did not enable server Reader mode");
                    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(CONTROLLER), Direction.UP, CONTROLLER, false)));
                    require(DiagnosticTabletAppStorage.bindings(player.server, TABLET, RDP).size() == 1, "Reader did not pair the controller");
                });
                advance(); return;
            }
            if(stage == 3){
                if(DiagnosticTabletData.read(mc.player.getMainHandItem()).mode() != TabletInteractionMode.STANDARD) return;
                DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem()); advance(); return;
            }
            if(stage == 5 && mc.screen instanceof ControllerTextInputScreen dialog){
                var field = TextInputScreen.class.getDeclaredField("input"); field.setAccessible(true);
                ((EditBox)field.get(dialog)).setValue("Factory Control");
                dialog.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); advance(); return;
            }
            if(stage == 9){
                fixture = new RouteFixture(); mc.setScreen(fixture); advance(); return;
            }
            if(stage == 10){
                fixture.click(30, 78, 0); fixture.click(80, 78, 0); fixture.click(125, 78, 0);
                require(fixture.commands.equals(List.of("remote_control|controller", "open_graph|controller", "open_plotter|controller")), "Controller buttons routed the wrong actions: " + fixture.commands);
                fixture.commands.clear();
                fixture.click(190, 78, 0); fixture.click(237, 78, 0); fixture.click(284, 78, 0);
                require(fixture.commands.equals(List.of("remote_control|computer")), "Computer graph or plot controls were enabled");
                fixture.commands.clear();
                fixture.click(30, 150, 0); fixture.click(80, 150, 0); fixture.click(125, 150, 0);
                require(fixture.commands.isEmpty(), "Offline device controls sent an action");
                fixture.projected = true; fixture.click(30, 78, 1); fixture.projected = false;
                require(fixture.commands.equals(List.of("remote_control|controller")), "Projected device control did not route its interaction");
                capture("rdp-card-actions.png");
                require(fixture.renderer.mouseScrolled(fixture.ctx(null), fixture.state, fixture.x(250), fixture.y(110), 0, -1), "Device pages did not scroll");
                advance(); return;
            }
            if(stage == 11){
                fixture.commands.clear(); capture("rdp-persistent-add-new.png"); fixture.click(75, 60, 0);
                require(fixture.commands.equals(List.of("begin_reader|")), "Add new was not the final card after the device pages");
                DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem()); advance(); return;
            }
            if(!(mc.screen instanceof DiagnosticTabletScreen<?> screen)) return;
            var ctx = context(screen);
            var devices = ctx.data().getList("Devices", Tag.TAG_COMPOUND);
            switch(stage){
                case 1 -> {
                    require(devices.isEmpty() && ctx.surface().width() == 492, "Empty device page did not use the painted tablet surface");
                    capture("rdp-empty.png");
                    require(click(screen, 75, 60, 0) && mc.screen != screen, "Add new did not enter the existing reader flow");
                    advance();
                }
                case 4 -> {
                    if(devices.size() != 1) return;
                    var device = devices.getCompound(0); deviceKey = device.getString("Key");
                    require(device.getBoolean("Online") && device.getBoolean("GraphAvailable") && device.getBoolean("PlotAvailable"), "Paired controller lost its remote capabilities");
                    capture("rdp-controller.png");
                    require(click(screen, 125, 34, 0), "Eyedropper did not handle its bounds");
                    stage = 20; ticks = 0;
                }
                case 20 -> {
                    capture("rdp-card-color-picker.png");
                    typeColor(screen, "#773344");
                    screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    stage = 21; ticks = 0;
                }
                case 21 -> {
                    if(devices.isEmpty() || devices.getCompound(0).getInt("Color") != 0x773344) return;
                    capture("rdp-colored-card.png");
                    screen.onClose(); DiagnosticTabletClientAppData.clear();
                    DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem());
                    stage = 22; ticks = 0;
                }
                case 22 -> {
                    if(devices.isEmpty() || devices.getCompound(0).getInt("Color") != 0x773344) return;
                    require(click(screen, 125, 34, 0), "Saved card color did not retain its eyedropper");
                    stage = 23; ticks = 0;
                }
                case 23 -> {
                    typeColor(screen, "#00FF00");
                    screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    stage = 24; ticks = 0;
                }
                case 24 -> {
                    require(mc.screen == screen && devices.getCompound(0).getInt("Color") == 0x773344, "Cancelling a color edit changed the saved color or closed the app");
                    capture("rdp-color-cancelled.png");
                    require(click(screen, 148, 34, 0) && mc.screen instanceof ControllerTextInputScreen, "Rename pen did not open the existing text dialog");
                    stage = 5; ticks = 0;
                }
                case 6 -> {
                    if(devices.isEmpty() || !devices.getCompound(0).getString("Name").equals("Factory Control")) return;
                    require(devices.getCompound(0).getInt("Color") == 0x773344, "Renaming lost the controller card's color");
                    capture("rdp-renamed.png");
                    server(() -> {
                        var server = mc.getSingleplayerServer();
                        for(int idx = 0; idx < 4; idx++) DiagnosticTabletAppStorage.addBinding(server, TABLET, RDP,
                                new DiagnosticTabletData.Binding("controller", null, new BlockPos(40 + idx, -59, 2), "Offline " + idx), false);
                    });
                    advance();
                }
                case 7 -> {
                    if(devices.size() != 5) return;
                    capture("rdp-devices.png");
                    require(screen.mouseScrolled(x(screen, 250), y(screen, 110), 0, -1), "Bound controllers beyond the old three-device limit were inaccessible");
                    advance();
                }
                case 8 -> { capture("rdp-devices-page-two.png"); advance(); }
                case 12 -> {
                    if(devices.isEmpty() || !devices.getCompound(0).getString("Name").equals("Factory Control")) return;
                    screen.mouseScrolled(x(screen, 250), y(screen, 110), 0, 1);
                    stage = 14; ticks = 0;
                }
                case 14 -> {
                    require(click(screen, 137, 34, 0), "Shared delete icon did not handle its bounds");
                    stage = 13; ticks = 0;
                }
                case 13 -> {
                    if(devices.size() != 4) return;
                    for(int idx = 0; idx < devices.size(); idx++) require(!devices.getCompound(idx).getString("Key").equals(deviceKey), "Delete retained the controller binding");
                    capture("rdp-controller-removed.png");
                    screen.mouseScrolled(x(screen, 250), y(screen, 110), 0, -1);
                    server(() -> require(!DiagnosticTabletAppStorage.data(mc.getSingleplayerServer(), TABLET, RDP)
                            .getCompound("DeviceColors").contains(deviceKey), "Deleting a controller retained its saved card color"));
                    stage = 25; ticks = 0;
                }
                case 25 -> {
                    require(click(screen, 75, 60, 0) && mc.screen != screen, "Add new was not at the end after deleting a device");
                    finish("PASS");
                }
            }
        }catch(Throwable error){ error.printStackTrace(); finish("FAIL " + error); }
    }

    private static void server(Runnable action){
        work = new CompletableFuture<>();
        Minecraft.getInstance().getSingleplayerServer().execute(() -> { try{ action.run(); work.complete(null); }catch(Throwable error){ work.completeExceptionally(error); } });
    }
    private static void typeColor(DiagnosticTabletScreen<?> screen, String hex) throws Exception{
        var appearance = CTColorPickerAppearance.DEFAULT;
        var transform = GuiCanvasTransform.fit(appearance.panel().width(), appearance.panel().height(), 320, 180, 8);
        require(click(screen, (int)Math.round(transform.screenX(65)), (int)Math.round(transform.screenY(140)), 0), "Color picker hex field did not accept input");
        for(char ch : hex.toCharArray()) require(screen.charTyped(ch, 0), "Color picker did not accept hex input");
    }
    private static TabletAppClientContext context(DiagnosticTabletScreen<?> screen) throws Exception{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("clientAppContext", TabletAppDefinition.class, GuiGraphics.class, int.class, int.class); method.setAccessible(true);
        return (TabletAppClientContext)method.invoke(screen, TabletAppRegistry.definition(RDP), null, -100, -100);
    }
    private static double x(DiagnosticTabletScreen<?> screen, int x) throws Exception{ return context(screen).surface().left() + x * 492D / 320; }
    private static double y(DiagnosticTabletScreen<?> screen, int y) throws Exception{ return context(screen).surface().top() + y * 286D / 180; }
    private static boolean click(DiagnosticTabletScreen<?> screen, int x, int y, int button) throws Exception{ return screen.mouseClicked(x(screen, x), y(screen, y), button); }
    private static void advance(){ stage++; ticks = 0; }
    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
    private static void capture(String name){ var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {}); }
    private static void finish(String res){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), res); }catch(Exception error){ throw new RuntimeException(error); }
        Minecraft.getInstance().options.guiScale().set(originalScale); Minecraft.getInstance().stop();
    }

    private static final class RouteFixture extends Screen{
        private final RemoteDesktop renderer = (RemoteDesktop)TabletAppClientRegistry.renderer(RDP);
        private final TabletAppClientState state = renderer.createScreenState();
        private final CompoundTag data = new CompoundTag();
        private final List<String> commands = new ArrayList<>();
        private boolean projected;

        private RouteFixture(){
            super(Component.literal("Remote cards"));
            var devices = new ListTag();
            devices.add(device("controller", true, true)); devices.add(device("computer", true, false));
            devices.add(device("offline", false, false)); devices.add(device("spare", false, false)); data.put("Devices", devices);
        }
        private static CompoundTag device(String key, boolean online, boolean graph){
            var row = new CompoundTag(); row.putString("Key", key); row.putString("Name", key); row.putBoolean("Online", online);
            row.putBoolean("GraphAvailable", graph); row.putBoolean("PlotAvailable", graph); return row;
        }
        private TabletAppClientContext ctx(GuiGraphics graphics){
            var app = TabletAppRegistry.definition(RDP);
            return new TabletAppClientContext(app, app.tabs().getFirst(), data, graphics, font, 0, 0, width, height,
                    new TabletAppClientSurface(0, 0, width, height), -100, -100, (action, val) -> commands.add(action + "|" + val), () -> {},
                    new TabletAppClientUi(){ @Override public boolean projected(){ return projected; } });
        }
        private double x(int x){ return x * width / 320D; }
        private double y(int y){ return y * height / 180D; }
        private void click(int x, int y, int button){ require(renderer.mouseClicked(ctx(null), state, x(x), y(y), button), "Device control did not handle " + x + "," + y); }
        @Override public boolean isPauseScreen(){ return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta){ renderer.render(ctx(graphics), state); }
    }
}
