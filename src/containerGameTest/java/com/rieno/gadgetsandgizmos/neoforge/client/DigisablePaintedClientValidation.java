package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelPreviewRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import com.mojang.blaze3d.pipeline.RenderTarget;

// Render representative server snapshots and exercise actions without mutating live assemblies
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class DigisablePaintedClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.digisablePaintedValidation");
    private static final UUID BODY = UUID.randomUUID();
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static FixtureScreen screen;
    private static boolean complete;
    private static int stage;
    private static int ticks;

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
            if(++ticks > 600) throw new AssertionError("Painted Digisable timed out at stage " + stage);
            if(ticks < 30) return;
            switch(stage){
                case 0 -> {
                    require(screen.renderer.ownsTabletSurface(), "Digisable did not request the full frame");
                    capture("digisable-stored-empty.png");
                    screen.actions.clear(); screen.click(270, 160);
                    require(screen.actions.isEmpty(), "Empty Stored view allowed extraction");
                    screen.click(40, 160); require(screen.has("begin_reader", "store"), "Store From World was lost");
                    screen.click(290, 28); require(screen.mode == TabletInteractionMode.STANDARD, "Reader mode did not reset");
                    ListTag stored = new ListTag(); stored.add(entry(FIRST, "Cargo")); screen.data.put("Archives", stored);
                    advance();
                }
                case 1 -> {
                    screen.click(270, 160); require(screen.has("prepare_extract", FIRST.toString()), "Extraction placement was not requested");
                    screen.click(75, 28); require(screen.tab.equals("sublevels"), "Sublevels tab did not navigate");
                    advance();
                }
                case 2 -> {
                    require(preview().blockCount() > 0, "The assembly snapshot did not render");
                    try(var pixels = Screenshot.takeScreenshot((RenderTarget)field(preview(), "target"))){
                        var colors = new HashSet<Integer>();
                        for(int y = 0; y < pixels.getHeight(); y += 4) for(int x = 0; x < pixels.getWidth(); x += 4)
                            colors.add(pixels.getPixelRGBA(x, y));
                        require(colors.size() > 16, "The preview framebuffer contained no visible assembly");
                    }
                    capture("digisable-sublevels.png");
                    screen.click(270, 136); require(screen.has("store_selected", FIRST.toString()), "Store Selected was lost");
                    screen.click(270, 148); require(screen.has("teleport", FIRST.toString()), "Teleport was lost");
                    screen.actions.clear(); screen.click(270, 160);
                    require(!screen.has("delete", FIRST.toString()), "Deletion bypassed confirmation");
                    advance();
                }
                case 3 -> {
                    var confirm = screen.state.getClass().getDeclaredField("confirmDelete");
                    confirm.setAccessible(true); confirm.set(screen.state, UUID.randomUUID());
                    screen.actions.clear(); screen.click(270, 160);
                    require(!screen.has("delete", FIRST.toString()), "Deletion reused another assembly's confirmation");
                    screen.click(270, 160); require(screen.has("delete", FIRST.toString()), "Confirmed deletion was not routed");
                    screen.click(30, 45);
                    for(char ch : "873".toCharArray()) screen.renderer.chatTyped(screen.ctx(null, 0, 0), screen.state, ch, 0);
                    advance();
                }
                case 4 -> {
                    require(field(screen.state, "selected") == null, "Filtering retained a hidden selection");
                    capture("digisable-search.png");
                    screen.click(35, 65); require(screen.has("preview", SECOND.toString()), "Filtered row selected the wrong assembly");
                    screen.click(30, 45); screen.renderer.keyPressed(screen.ctx(null, 0, 0), screen.state, GLFW.GLFW_KEY_DELETE, 0, 0);
                    advance();
                }
                case 5 -> {
                    screen.renderer.mouseScrolled(screen.ctx(null, 0, 0), screen.state, screen.x(40), screen.y(95), 0, -3);
                    require(((ListScrollState)field(screen.state, "list")).offset() > 0, "Assembly list did not scroll");
                    float yaw = (float)field(preview(), "yaw");
                    float distance = (float)field(preview(), "distance");
                    screen.click(160, 100);
                    screen.renderer.mouseDragged(screen.ctx(null, 0, 0), screen.state, screen.x(170), screen.y(105), 0, 35, 10);
                    screen.renderer.mouseReleased(screen.ctx(null, 0, 0), screen.state, screen.x(170), screen.y(105), 0);
                    screen.renderer.mouseScrolled(screen.ctx(null, 0, 0), screen.state, screen.x(160), screen.y(100), 0, 1);
                    require(yaw != (float)field(preview(), "yaw") && distance != (float)field(preview(), "distance"), "Preview rotate or zoom failed");
                    screen.click(120, 28); require(screen.tab.equals("schematics"), "Schematics tab did not navigate");
                    advance();
                }
                case 6 -> {
                    capture("digisable-schematics.png");
                    screen.data.putUUID("SchematicSelected", UUID.randomUUID());
                    advance();
                }
                case 7 -> {
                    screen.actions.clear(); screen.click(270, 136);
                    require(!screen.has("prepare_build", FIRST.toString()), "Build used a stale validation");
                    screen.data.putUUID("SchematicSelected", FIRST);
                    advance();
                }
                case 8 -> {
                    screen.click(270, 136); require(screen.has("prepare_build", FIRST.toString()), "Validated Build was not routed");
                    screen.click(270, 148); require(screen.has("materials", FIRST.toString()), "Materials action was lost");
                    advance();
                }
                case 9 -> {
                    require((boolean)field(screen.state, "materials"), "Materials pane did not open");
                    capture("digisable-materials.png");
                    screen.renderer.mouseScrolled(screen.ctx(null, 0, 0), screen.state, screen.x(160), screen.y(100), 0, -2);
                    require(((ListScrollState)field(screen.state, "materialList")).offset() > 0, "Materials did not scroll");
                    screen.click(270, 160); require(screen.has("begin_reader", "pair_network"), "Network pairing was lost");
                    screen.data.putBoolean("Building", true);
                    advance();
                }
                case 10 -> {
                    screen.click(270, 136); require(screen.has("cancel_build", ""), "Construction cancellation was lost");
                    screen.data.putBoolean("Building", false);
                    screen.click(270, 148);
                    screen.click(180, 28);
                    advance();
                }
                case 11 -> { screen.click(180, 64); advance(); }
                case 12 -> {
                    require(field(screen.state, "selected") == null, "Format filtering retained an incompatible selection");
                    capture("digisable-format-filter.png");
                    screen.renderer.keyPressed(screen.ctx(null, 0, 0), screen.state, GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    screen.projected = true;
                    advance();
                }
                case 13 -> {
                    screen.renderer.mouseClicked(screen.ctx(null, 0, 0), screen.state, screen.x(30), screen.y(45), 1);
                    require(screen.actions.contains("edit:Search"), "Projected search did not request the host dialog");
                    finish("PASSED: painted Stored/Sublevels/Schematics; snapshot preview, rotation and zoom; search, format filters and scrolling; extraction and storage routing; deletion confirmation; validated Build, Materials and cancellation; Reader mode and projected search");
                }
                default -> throw new AssertionError("Unknown Digisable validation stage");
            }
        }catch(Throwable err){ LogUtils.getLogger().error("Painted Digisable validation failed", err); finish("FAILED: " + err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Object field(Object owner, String name) throws ReflectiveOperationException{
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static SubLevelPreviewRenderer preview() throws ReflectiveOperationException{ return (SubLevelPreviewRenderer)field(screen.state, "preview"); }
    private static void require(boolean value, String msg){ if(!value) throw new AssertionError(msg); }
    private static void advance(){ stage++; ticks = 0; }
    private static void capture(String name){ var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {}); }
    private static void finish(String msg){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), msg); }catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(msg); Minecraft.getInstance().stop();
    }

    private static CompoundTag entry(UUID id, String name){
        CompoundTag row = new CompoundTag(); row.putUUID("Id", id); row.putString("Name", name);
        row.putBoolean("Loaded", true); row.putBoolean("Unclaimed", true); row.putBoolean("Client", true);
        row.putDouble("X", 18); row.putDouble("Y", 67); row.putDouble("Z", 32); row.putInt("Distance", 8);
        row.putString("Source", PaidTabletApps.DIGISABLE.id().toString()); return row;
    }

    private static CompoundTag fixture(){
        CompoundTag data = new CompoundTag();
        for(String key : List.of("SublevelsAllowed", "StoreAllowed", "LocateAllowed", "TeleportAllowed", "DeleteAllowed", "RemoteStoreAllowed", "SchematicsAllowed", "SharedSchematicsLocal", "BuildReady")) data.putBoolean(key, true);
        data.putInt("ArchiveLimit", 16); data.putInt("SchematicMaximumBodies", 32); data.putInt("SchematicMaximumBlocks", 32768);
        ListTag live = new ListTag();
        for(int idx = 0; idx < 20; idx++) live.add(entry(idx == 0 ? FIRST : idx == 1 ? SECOND : UUID.randomUUID(), idx == 0 ? "27fd998b" : idx == 1 ? "873462jk" : "ad67b" + idx));
        data.put("Sublevels", live);
        ListTag files = new ListTag(); files.add(entry(FIRST, "Cube.nbt")); files.add(entry(SECOND, "Plane.nbt"));
        files.add(entry(UUID.randomUUID(), "Test_1.nbt")); files.add(entry(UUID.randomUUID(), "asdf.nbt")); data.put("Schematics", files);
        ListTag preview = new ListTag();
        for(int x = 0; x < 5; x++) for(int y = 0; y < 5; y++) for(int z = 0; z < 5; z++){
            var block = y == 4 ? Blocks.CUT_COPPER : (x == 0 || x == 4) && (z == 0 || z == 4) ? Blocks.OAK_LOG
                    : y == 2 && (x == 2 || z == 2) ? Blocks.GLASS : Blocks.OAK_PLANKS;
            CompoundTag row = new CompoundTag(); row.putUUID("Body", BODY); row.putLong("Pos", new BlockPos(x, y, z).asLong());
            row.putDouble("X", x); row.putDouble("Y", y); row.putDouble("Z", z); row.put("State", NbtUtils.writeBlockState(block.defaultBlockState())); preview.add(row);
        }
        data.put("Preview", preview); data.putUUID("PreviewRoot", BODY);
        ListTag materials = new ListTag();
        for(int idx = 0; idx < 12; idx++){
            CompoundTag row = new CompoundTag(); row.putString("Name", idx % 2 == 0 ? "Oak Planks" : "Copper Block");
            row.putInt("Required", 24); row.putLong("Available", 20); row.putLong("Missing", 4);
            row.put("Stack", new ItemStack(idx % 2 == 0 ? Blocks.OAK_PLANKS : Blocks.COPPER_BLOCK).saveOptional(Minecraft.getInstance().level.registryAccess())); materials.add(row);
        }
        data.put("Materials", materials); return data;
    }

    private static final class FixtureScreen extends Screen{
        private final TabletAppClientRenderer renderer = TabletAppClientRegistry.renderer(PaidTabletApps.DIGISABLE.id());
        private final TabletAppClientState state = renderer.createScreenState();
        private final CompoundTag data = fixture();
        private final List<String> actions = new ArrayList<>();
        private String tab = "archives";
        private TabletInteractionMode mode = TabletInteractionMode.READER;
        private boolean projected;
        private FixtureScreen(){ super(Component.literal("Painted Digisable validation")); }
        private double x(int x){ return (width - 492) / 2D + x * 492D / 320; }
        private double y(int y){ return (height - 286) / 2D + y * 286D / 180; }
        private boolean click(int x, int y){ return renderer.mouseClicked(ctx(null, 0, 0), state, x(x), y(y), 0); }
        private boolean has(String action, String value){ return actions.contains(action + ":" + value); }
        private TabletAppClientContext ctx(GuiGraphics graphics, int mouseX, int mouseY){
            var app = PaidTabletApps.DIGISABLE;
            var selectedTab = app.tabs().stream().filter(value -> value.id().equals(tab)).findFirst().orElseThrow();
            return new TabletAppClientContext(app, selectedTab, data, graphics, Minecraft.getInstance().font, 0, 0, 320, 180,
                    new TabletAppClientSurface((width - 492) / 2, (height - 286) / 2, 492, 286), mouseX, mouseY,
                    (action, value) -> {
                        actions.add(action + ":" + value);
                        if(action.equals("preview") || action.equals("schematic_preview")){
                            data.putUUID("Selected", UUID.fromString(value));
                            if(action.equals("schematic_preview")) data.putUUID("SchematicSelected", UUID.fromString(value));
                        }
                    }, () -> {}, new TabletAppClientUi(){
                        @Override public boolean projected(){ return projected; }
                        @Override public TabletInteractionMode interactionMode(){ return mode; }
                        @Override public void setInteractionMode(TabletInteractionMode next){ mode = next; }
                        @Override public void selectTab(String next){ tab = next; }
                        @Override public void editText(String prompt, String initial, int limit, java.util.function.Consumer<String> accept){ actions.add("edit:" + prompt); accept.accept("Cube"); }
                    });
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick){
            graphics.fill(0, 0, width, height, 0xEE17191C); renderer.render(ctx(graphics, mouseX, mouseY), state);
        }
        @Override public boolean isPauseScreen(){ return false; }
        @Override public void removed(){
            if(state instanceof AutoCloseable closeable) try{ closeable.close(); }catch(Exception err){ throw new IllegalStateException(err); }
        }
    }
}
