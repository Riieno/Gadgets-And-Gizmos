package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletMediaApp;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapData;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.GgAuto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Verify painted launcher routing, resource paging and navigation controls in an isolated client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class AutoPaintedClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.autoPaintedValidation");
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
            if(++ticks < (stage == 0 ? 100 : 25)) return;
            ticks = 0;
            switch(stage++){
                case 0 -> {
                    capture("auto-dashboard.png");
                    fixture.data.putDouble("Yaw", 90);
                    fixture.data.putString("Facing", "East");
                    stage = 90;
                }
                case 90 -> {
                    capture("auto-map-east.png");
                    fixture.data.putDouble("Yaw", 45);
                    stage = 91;
                }
                case 91 -> {
                    capture("auto-map-diagonal.png");
                    fixture.data.putDouble("Yaw", 179);
                    stage = 92;
                }
                case 92 -> {
                    capture("auto-map-south-before.png");
                    fixture.data.putDouble("Yaw", -179);
                    stage = 93;
                }
                case 93 -> {
                    capture("auto-map-south-after.png");
                    fixture.data.putDouble("Yaw", 359);
                    stage = 94;
                }
                case 94 -> {
                    capture("auto-map-north-before.png");
                    fixture.data.putDouble("Yaw", 1);
                    stage = 95;
                }
                case 95 -> {
                    capture("auto-map-north-after.png");
                    stage = 1;
                    fixture.click(164, 160);
                    require(DiagnosticTabletMediaApp.ID.equals(fixture.opened), "Media launcher did not open Media");
                    fixture.click(275, 132);
                    require(fixture.actions.contains("media_next:"), "Embedded transport did not reach the tablet's Media session");
                    fixture.click(188, 160);
                    require(fixture.tab.equals("manifest"), "Manifest did not open inside Auto");
                }
                case 1 -> {
                    capture("auto-manifest-items.png");
                    fixture.renderer.mouseScrolled(fixture.ctx(null), fixture.state, fixture.x(200), fixture.y(100), 0, -3);
                    require(fixture.data.getCompound("Manifest").getInt("Offset") > 0, "Manifest did not request later cargo");
                    require(fixture.data.getCompound("Manifest").getInt("Offset") % 4 == 0, "Cargo card paging did not preserve four-column rows");
                }
                case 2 -> { capture("auto-manifest-items-paged.png"); fixture.click(83, 44); }
                case 3 -> {
                    capture("auto-manifest-fluids.png");
                    require(fixture.data.getCompound("Manifest").getList("Rows", Tag.TAG_COMPOUND).getCompound(0).getLong("Amount") == 1200,
                            "Fluid manifest lost the ship's fluid amount");
                    fixture.click(132, 44);
                }
                case 4 -> {
                    capture("auto-manifest-fuel.png");
                    require(fixture.data.getCompound("Manifest").getInt("Total") == 3, "Fuel did not include items, fluids and FE");
                    fixture.click(140, 160);
                }
                case 5 -> { fixture.click(140, 160); }
                case 6 -> {
                    capture("auto-navigation.png");
                    fixture.click(110, 99);
                    fixture.click(84, 112);
                    fixture.click(135, 112);
                    require(fixture.actions.contains("navigate:10, 80, 20") && fixture.actions.contains("hover:") && fixture.actions.contains("dock:"),
                            "Painted navigation lost an existing ship command");
                    fixture.click(110, 125);
                }
                case 7 -> {
                    capture("auto-statistics.png");
                    fixture.click(140, 160);
                    fixture.data.putBoolean("MediaInstalled", false);
                    fixture.data.putBoolean("ManifestInstalled", false);
                }
                case 8 -> {
                    capture("auto-launcher-gated.png");
                    require(!fixture.renderer.mouseClicked(fixture.ctx(null), fixture.state, fixture.x(188), fixture.y(160), 0),
                            "Uninstalled Manifest still had a launcher target");
                    fixture.data.putBoolean("MediaInstalled", true);
                    fixture.data.putBoolean("ManifestInstalled", true);
                }
                case 9 -> { fixture.click(188, 160); }
                case 10 -> {
                    fixture.click(200, 44);
                    for(char ch : "no_matching_resource".toCharArray()) fixture.renderer.chatTyped(fixture.ctx(null), fixture.state, ch, 0);
                }
                case 11 -> {
                    capture("auto-manifest-search.png");
                    require(fixture.data.getCompound("Manifest").getInt("Total") == 0, "Resource search did not filter the manifest");
                    finish("PASS");
                }
            }
        }catch(Throwable err){ finish("FAIL " + err); }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
    private static void capture(String name){
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
    }
    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), result); }catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }

    private static final class Fixture extends Screen{
        private final GgAuto renderer = new GgAuto();
        private final TabletAppClientState state = renderer.createScreenState();
        private final CompoundTag data = new CompoundTag();
        private final List<String> actions = new ArrayList<>();
        private final ResourceRouting.Pool cargo;
        private final ResourceRouting.Pool fuel;
        private String tab = "drive";
        private ResourceLocation opened;

        private Fixture(){
            super(Component.literal("G&G Auto validation"));
            var mc = Minecraft.getInstance();
            var pos = mc.player.position();
            data.putBoolean("Available", true);
            data.putBoolean("MediaInstalled", true);
            data.putBoolean("ManifestInstalled", true);
            data.putString("Ship", "Validation ship");
            data.putString("Facing", "North");
            data.putDouble("Yaw", 0);
            data.putDouble("Speed", 73);
            data.putDouble("Weight", 123456);
            data.putDouble("Fuel", .72);
            data.putDouble("X", pos.x); data.putDouble("Y", pos.y); data.putDouble("Z", pos.z);
            data.putBoolean("HasTarget", true);
            var target = pos.add(70, 0, -10);
            data.putDouble("TargetX", target.x); data.putDouble("TargetY", target.y); data.putDouble("TargetZ", target.z);
            var spline = WaypointSpline.of(List.of(pos, pos.add(20, 0, -50), pos.add(60, 0, -40), target));
            data.put("NavigationMap", new NavigationMapData(List.of(
                    new NavigationMapData.Marker(UUID.randomUUID(), pos.add(-50, 0, 15), 30),
                    new NavigationMapData.Marker(UUID.randomUUID(), pos.add(20, 0, 45), 160)),
                    NavigationMapData.spline(pos, target, spline, 0, 0)).toTag());
            var server = mc.getSingleplayerServer();
            if(server != null) server.execute(() -> {
                var level = server.overworld();
                int y = BlockPos.containing(pos).getY() - 1;
                for(int dx = -72; dx <= 72; dx++) for(int dz = -72; dz <= 72; dz++){
                    if(Math.abs(dx) > 3 && Math.abs(dz) > 3) continue;
                    var block = Math.abs(dx) <= 3 ? Blocks.BLUE_WOOL : Blocks.YELLOW_WOOL;
                    level.setBlock(BlockPos.containing(pos.x + dx, y, pos.z + dz), block.defaultBlockState(), 3);
                }
            });
            data.putLong("EtaSeconds", 1400);
            data.putLong("Cargo", 140); data.putLong("Fluids", 1200); data.putLong("Energy", 8000);
            data.put("Media", new MediaPlaybackState(ResourceLocation.withDefaultNamespace("music_disc_cat"), true,
                    1200, mc.level.getGameTime(), 3700, 1).toTag());
            var items = new ItemStackHandler(70);
            var registered = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(70).toList();
            for(int idx = 0; idx < registered.size(); idx++) items.setStackInSlot(idx, new ItemStack(registered.get(idx), 2));
            var tank = new FluidTank(4000);
            tank.fill(new FluidStack(Fluids.WATER, 1200), IFluidHandler.FluidAction.EXECUTE);
            cargo = new ResourceRouting.Pool(List.of(items), List.of(tank), List.of());
            var reserve = new ItemStackHandler(1);
            reserve.setStackInSlot(0, new ItemStack(Items.COAL, 64));
            var energy = new EnergyStorage(10000);
            energy.receiveEnergy(8000, false);
            fuel = new ResourceRouting.Pool(List.of(reserve), List.of(tank), List.of(energy));
        }

        @Override public boolean isPauseScreen(){ return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial){
            graphics.fill(0, 0, width, height, 0xFF151515);
            renderer.render(ctx(graphics), state);
        }
        @Override public void removed(){
            try{ ((AutoCloseable)state).close(); }catch(Exception err){ throw new RuntimeException(err); }
        }

        private TabletAppClientContext ctx(GuiGraphics graphics){
            var app = TabletAppRegistry.definition(DiagnosticTabletData.appId("gg_auto"));
            var selected = app.tabs().stream().filter(val -> val.id().equals(tab)).findFirst().orElseThrow();
            return new TabletAppClientContext(app, selected, data, graphics, Minecraft.getInstance().font,
                    0, 0, width, height, new TabletAppClientSurface(0, 0, width, height), 0, 0, this::action, () -> {}, new TabletAppClientUi(){
                @Override public void selectTab(String val){ tab = val; renderer.refresh(ctx(null), state); }
                @Override public void openApp(ResourceLocation id, String target){ opened = id; }
                @Override public void editText(String prompt, String initial, int maximum, java.util.function.Consumer<String> accept){ accept.accept("10, 80, 20"); }
            });
        }
        private double x(int val){ return width * val / 320D; }
        private double y(int val){ return height * val / 180D; }
        private void click(int x, int y){ require(renderer.mouseClicked(ctx(null), state, x(x), y(y), 0), "Control did not receive click: " + x + "," + y); }

        private void action(String action, String val){
            actions.add(action + ":" + val);
            if(!action.equals("refresh") || !tab.equals("manifest")) return;
            String[] args = val.split("\\|", 3);
            var page = ContainerNetworkSnapshot.capturePool(args[0].equals("fuel") ? fuel : cargo,
                    args[0].equals("items") ? "item" : args[0].equals("fluids") ? "fluid" : "", args[2], Integer.parseInt(args[1]), 32);
            page.putString("Category", args[0]); page.putString("Filter", args[2]);
            data.put("Manifest", page);
        }
    }
}
