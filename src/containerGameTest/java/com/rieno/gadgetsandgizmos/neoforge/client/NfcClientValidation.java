package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSession;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.Nfc;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Exercise the real tablet host, reader scans, scrolling and placed direct-control input
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class NfcClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.nfcValidation");
    private static final ResourceLocation NFC = DiagnosticTabletData.appId("nfc");
    private static final UUID HELD = UUID.randomUUID(), PLACED = UUID.randomUUID();
    private static final BlockPos CHEST = new BlockPos(2, -59, 2), ACCUMULATOR = new BlockPos(4, -59, 2), TABLET = CHEST.above();
    private static final ResourceLocation ACCUMULATOR_ID = ResourceLocation.parse("simulated:redstone_accumulator");
    private static int stage, ticks, originalScale;
    private static boolean started, complete;
    private static CompletableFuture<Void> work;

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
                require(CTFeatureToggles.isItemEnabled("diagnostic_tablet"), "Enable tablets in the isolated validation instance before testing NFC input");
                started = true;
                mc.options.pauseOnLostFocus = false;
                originalScale = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                server(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var level = player.serverLevel();
                    level.setBlockAndUpdate(CHEST.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(ACCUMULATOR.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                    ((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIAMOND, 64));
                    var block = BuiltInRegistries.BLOCK.getOptional(ACCUMULATOR_ID).orElseThrow();
                    level.setBlockAndUpdate(ACCUMULATOR, block.defaultBlockState());
                    var stack = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
                    DiagnosticTabletData.write(stack, DiagnosticTabletData.State.DEFAULT.withTabletId(HELD).withApp(NFC, "inspect"));
                    player.getInventory().selected = 0;
                    player.connection.send(new ClientboundSetCarriedItemPacket(0));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var database = DiagnosticTabletDatabase.forServer(player.server);
                    database.setInstalled(HELD, NFC, true);
                    database.setInstalled(PLACED, NFC, true);
                });
            }
            if(++ticks > 1200) throw new AssertionError("NFC validation timed out at stage " + stage);
            if(ticks < 25 || work != null && !work.isDone()) return;
            if(work != null) work.join();
            if(stage == 0){
                if(!HELD.equals(DiagnosticTabletData.read(mc.player.getMainHandItem()).tabletId())) return;
                require(TabletAppClientRegistry.renderer(NFC) instanceof Nfc, "Painted NFC renderer was not registered");
                DiagnosticTabletScreen.openItem(InteractionHand.MAIN_HAND, mc.player.getMainHandItem());
                advance();
                return;
            }
            if(stage == 2){ scan(CHEST); advance(); return; }
            if(stage == 5){ scan(ACCUMULATOR); advance(); return; }
            if(stage == 8){
                var tablet = mc.level.getBlockEntity(TABLET);
                if(!(tablet instanceof DiagnosticTabletBlockEntity placed) || !PLACED.equals(placed.state().tabletId())) return;
                DiagnosticTabletScreen.openBlock(placed);
                advance();
                return;
            }
            if(!(mc.screen instanceof DiagnosticTabletScreen<?> screen)) return;
            var ctx = context(screen);
            switch(stage){
                case 1 -> {
                    require(!ctx.data().getBoolean("Available"), "An unbound NFC app showed stale block data");
                    require(!ctx.ui().placed() && ctx.surface().width() == 492 && ctx.surface().height() == 286,
                            "NFC did not receive the complete handheld surface");
                    capture("nfc-empty.png");
                    require(click(screen, 50, 147, 0), "Inspect button did not handle its painted bounds");
                    require(mc.screen != screen && DiagnosticTabletData.read(mc.player.getMainHandItem()).mode() == TabletInteractionMode.READER,
                            "Inspect did not begin the existing reader action");
                    advance();
                }
                case 3 -> {
                    if(!ctx.data().getString("Block").equals("minecraft:chest")) return;
                    require(ctx.data().contains("State", Tag.TAG_COMPOUND) && ctx.data().getString("Dimension").equals("minecraft:overworld"),
                            "Inspection snapshot lost the block state or dimension");
                    require(ctx.data().getBoolean("BlockEntity") && !ctx.data().getList("Properties", Tag.TAG_COMPOUND).isEmpty(),
                            "Inspection lost the readable block properties");
                    capture("nfc-chest.png");
                    require(screen.mouseScrolled(x(screen, 250), y(screen, 110), 0, -8), "Information panel did not scroll");
                    require(scroll(screen).offset() > 0, "Scrolling did not move the inspection document");
                    advance();
                }
                case 4 -> {
                    capture("nfc-chest-scrolled.png");
                    require(click(screen, 309, 74, 0), "Painted scrollbar did not handle clicks");
                    require(screen.mouseDragged(x(screen, 309), y(screen, 157), 0, 0, 30), "Painted scrollbar did not drag");
                    screen.mouseReleased(x(screen, 309), y(screen, 157), 0);
                    require(scroll(screen).offset() > 0, "Dragging did not move the inspection document");
                    click(screen, 50, 147, 0);
                    advance();
                }
                case 6 -> {
                    if(!ctx.data().getString("Block").equals(ACCUMULATOR_ID.toString())) return;
                    require(ctx.data().getBoolean("Controllable"), "NFC lost the supported block's direct-control input");
                    require(scroll(screen).offset() == 0, "Scanning another block retained the old scroll offset");
                    capture("nfc-direct-control.png");
                    require(click(screen, 200, 156, 0), "Direct-signal slider did not handle clicks");
                    require(screen.mouseDragged(x(screen, 259), y(screen, 156), 0, 5, 0), "Direct-signal slider did not drag");
                    screen.mouseReleased(x(screen, 259), y(screen, 156), 0);
                    advance();
                }
                case 7 -> {
                    if(ctx.data().getInt("Signal") != 12) return;
                    capture("nfc-signal-applied.png");
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(TABLET, CTBlocks.DIAGNOSTIC_TABLET.get().defaultBlockState().setValue(DiagnosticTabletBlock.FACING, Direction.UP));
                        ((DiagnosticTabletBlockEntity)level.getBlockEntity(TABLET)).setState(
                                DiagnosticTabletData.State.DEFAULT.withTabletId(PLACED).withApp(NFC, "inspect"));
                    });
                    advance();
                }
                case 9 -> {
                    if(!ctx.data().getString("Block").equals("minecraft:chest")) return;
                    require(ctx.ui().placed() && !ctx.ui().projected(), "A placed tablet menu was treated as handheld");
                    capture("nfc-placed.png");
                    require(click(screen, 50, 147, 0) && mc.screen == screen, "Placed Inspect closed the menu instead of refreshing its support block");
                    var placed = (DiagnosticTabletBlockEntity)mc.level.getBlockEntity(TABLET);
                    mc.setScreen(DiagnosticTabletScreen.projectionBlock(placed));
                    advance();
                }
                case 10 -> {
                    require(ctx.ui().placed() && ctx.ui().projected(), "Projected NFC did not retain its placed source");
                    capture("nfc-projected.png");
                    require(click(screen, 50, 147, 1) && mc.screen == screen, "Projected Inspect did not accept the placed screen interaction");
                    finish("PASS");
                }
            }
        }catch(Throwable error){ error.printStackTrace(); finish("FAIL " + error); }
    }

    private static void scan(BlockPos pos){
        server(() -> {
            var player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var stack = player.getMainHandItem();
            require(!player.level().getBlockState(pos).isAir(), "The scan fixture has no block at " + pos);
            require("nfc_scan".equals(DiagnosticTabletData.read(stack).pendingAction()), "Reader action did not reach the server: "
                    + DiagnosticTabletData.read(stack) + "; installed: " + DiagnosticTabletDatabase.forServer(player.server).installedApps(HELD));
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        });
    }

    private static void server(Runnable action){
        work = new CompletableFuture<>();
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try{ action.run(); work.complete(null); }catch(Throwable error){ work.completeExceptionally(error); }
        });
    }

    private static TabletAppClientContext context(DiagnosticTabletScreen<?> screen) throws Exception{
        var method = DiagnosticTabletScreen.class.getDeclaredMethod("clientAppContext", TabletAppDefinition.class, GuiGraphics.class, int.class, int.class);
        method.setAccessible(true);
        return (TabletAppClientContext)method.invoke(screen, TabletAppRegistry.definition(NFC), null, -100, -100);
    }

    private static ListScrollState scroll(DiagnosticTabletScreen<?> screen) throws Exception{
        var field = DiagnosticTabletScreen.class.getDeclaredField("appClientSession"); field.setAccessible(true);
        var renderer = TabletAppClientRegistry.renderer(NFC);
        var state = ((TabletAppClientSession)field.get(screen)).stateFor(NFC, renderer);
        var scroll = state.getClass().getDeclaredField("scroll"); scroll.setAccessible(true);
        return (ListScrollState)scroll.get(state);
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
        Minecraft.getInstance().options.guiScale().set(originalScale);
        Minecraft.getInstance().stop();
    }
}
