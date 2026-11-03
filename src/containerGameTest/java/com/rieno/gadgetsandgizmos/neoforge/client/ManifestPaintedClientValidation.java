package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.SmartContainerManifest;
import com.rieno.gadgetsandgizmos.content.SmartContainerMenu;
import com.rieno.gadgetsandgizmos.content.SmartContainerTablets;
import com.rieno.gadgetsandgizmos.content.SmartVaultBlockEntity;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.client.tablet.*;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.Manifest;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Check painted app routing and real server-owned inventory clicks in an isolated client world
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class ManifestPaintedClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.manifestPaintedValidation");
    private static FixtureScreen fixture;
    private static int stage;
    private static int ticks;
    private static boolean complete;
    private static volatile String serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 60) return;
        mc.options.pauseOnLostFocus = false;
        try{
            if(serverFailure != null) throw new AssertionError(serverFailure);
            if(fixture == null){ fixture = new FixtureScreen(); mc.setScreen(fixture); }
            if(++ticks > 600) throw new AssertionError("Manifest validation timed out at stage " + stage);
            if(ticks < 20) return;
            switch(stage){
                case 0 -> {
                    for(String name : List.of("Netherrack", "Creative Oxidized Fuel", "Attitude Display", "A very long unbroken resource name without enough space")){
                        var fitted = GuiControlPreviews.fitWrappedText(mc.font, Component.literal(name), 47, 14, .85F, 2);
                        require(!fitted.lines().isEmpty() && fitted.lines().size() <= 2, "Item name was omitted or truncated");
                        require(fitted.lines().size() * mc.font.lineHeight * fitted.scale() <= 14.001F, "Item name exceeded its painted height");
                        require(fitted.lines().stream().allMatch(line -> mc.font.width(line) * fitted.scale() <= 47.001F), "Item name exceeded its painted width");
                    }
                    require(PaidTabletApps.MANIFEST.tabs().stream().noneMatch(tab -> tab.id().equals("stock")), "Handheld Stock tab remains");
                    require(SmartContainerManifest.APP.tabs().stream().noneMatch(tab -> tab.id().equals("stock")), "Mounted Stock tab remains");
                    capture("manifest-contents.png");
                    fixture.wheel(100, 100, -3);
                    require(fixture.state.scroll("contents").offset() > 0, "Cargo cards did not scroll");
                    stage = 22; ticks = 0;
                }
                case 22 -> {
                    capture("manifest-contents-long-counts.png");
                    fixture.click(78, 165); require(fixture.tab.equals("resources"), "Resources footer did not navigate");
                    stage = 1; ticks = 0;
                }
                case 1 -> { capture("manifest-resources.png"); fixture.click(126, 165); next(); }
                case 2 -> {
                    require(fixture.tab.equals("network"), "Network footer did not navigate");
                    capture("manifest-network.png");
                    fixture.wheel(75, 100, -3);
                    require(fixture.state.scroll("network_items").offset() > 0, "Network items did not scroll");
                    require(fixture.state.scroll("network_resources").offset() == 0, "Item paging moved resource pane");
                    int offset = fixture.state.scroll("network_items").offset();
                    fixture.wheel(250, 100, -1);
                    require(fixture.state.scroll("network_resources").offset() > 0, "Network resources did not scroll");
                    require(fixture.state.scroll("network_items").offset() == offset, "Resource paging moved item pane");
                    next();
                }
                case 3 -> { capture("manifest-network-paged.png"); fixture.smart(); next(); }
                case 4 -> {
                    capture("manifest-battery-resources.png");
                    var connectors = new ListTag();
                    for(int idx = 0; idx < 12; idx++){
                        var row = new CompoundTag(); row.putLong("Member", 0); row.putLong("Connector", new BlockPos(idx, 70, 0).asLong());
                        row.putString("Face", "north"); row.putDouble("Volts", 12); row.putDouble("Watts", 64);
                        row.putDouble("ActualVolts", 12); row.putDouble("ActualAmps", 2); row.putDouble("TransferWatts", idx % 2 == 0 ? 24 : -24);
                        connectors.add(row);
                    }
                    fixture.data.put("PowerGridConnectors", connectors);
                    fixture.state.scroll("power").scrollBy(100);
                    next();
                }
                case 5 -> { fixture.state.scroll("power").scrollBy(100); next(); }
                case 6 -> {
                    capture("manifest-connectors.png");
                    fixture.actions.clear(); fixture.click(190, 50);
                    require(fixture.actions.stream().anyMatch(val -> val.startsWith("powergrid_output:")), "Connector output dialog was lost");
                    fixture.data.putBoolean("CanConfigure", false); next();
                }
                case 7 -> {
                    fixture.actions.clear(); fixture.click(190, 50);
                    require(fixture.actions.isEmpty(), "Connector setting ignored ownership");
                    fixture.data.putBoolean("CanConfigure", true);
                    fixture.data.putString("ContainerKind", "tank"); fixture.data.putBoolean("PowerGrid", false); next();
                }
                case 8 -> {
                    capture("manifest-tank-routing.png");
                    fixture.actions.clear(); fixture.click(200, 60);
                    require(fixture.actions.contains("fluid_selection:fuel|minecraft:water"), "Fuel fluid selection was lost");
                    fixture.click(175, 165); next();
                }
                case 9 -> {
                    capture("manifest-tank-storage.png");
                    fixture.renderer.mouseClicked(fixture.ctx(null, 0, 0), fixture.state, fixture.x(100), fixture.y(65), 1);
                    require(fixture.actions.contains("storage_control:112"), "Bucket exchange was lost");
                    fixture.tab = "transfers"; stage = 19; ticks = 0;
                }
                case 19 -> {
                    capture("manifest-tank-transfers.png");
                    fixture.click(250, 29);
                    fixture.data.putString("ContainerKind", "battery"); fixture.data.putBoolean("PowerGrid", true);
                    stage = 20; ticks = 0;
                }
                case 20 -> {
                    capture("manifest-battery-transfers.png");
                    fixture.wheel(75, 100, -2);
                    require(fixture.state.scroll("transfer_in").offset() > 0 && fixture.state.scroll("transfer_out").offset() == 0,
                            "Electrical transfer lists did not scroll independently");
                    prepareNative(); stage = 10; ticks = 0;
                }
                case 10 -> {
                    if(!(mc.screen instanceof SmartContainerScreen screen) || !screen.getMenu().getSlot(0).hasItem()) return;
                    require(screen.viewport().top() >= 0 && screen.viewport().top() + screen.viewport().height() <= screen.height, "Attached inventory was outside the window");
                    require(screen.getMenu().slots.size() == SmartContainerMenu.PLAYER_START + 36, "Native slots were lost");
                    stage = 18; ticks = 0;
                }
                case 18 -> {
                    capture("manifest-storage-native.png");
                    nativeClick(nativeScreen(), 16, 48, 0); stage = 11; ticks = 0;
                }
                case 11 -> {
                    var screen = nativeScreen();
                    require(screen.getMenu().getCarried().is(Items.DIAMOND) && screen.getMenu().getCarried().getCount() == 32, "Scaled cargo click did not pick up the actual stack");
                    nativeClick(screen, 88, 196, 0); next();
                }
                case 12 -> {
                    var screen = nativeScreen(); var menu = screen.getMenu();
                    require(menu.getCarried().isEmpty() && menu.getSlot(SmartContainerMenu.PLAYER_START).getItem().getCount() == 32, "Baked player inventory did not receive the carried stack");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, SmartContainerMenu.PLAYER_START, 0, ClickType.QUICK_MOVE, mc.player);
                    next();
                }
                case 13 -> {
                    var screen = nativeScreen();
                    require(screen.getMenu().getSlot(0).getItem().getCount() == 32 && screen.getMenu().getSlot(SmartContainerMenu.PLAYER_START).getItem().isEmpty(), "Shift-click did not move through the real storage capability");
                    nativeClick(screen, 16, 48, 1); next();
                }
                case 14 -> {
                    var screen = nativeScreen();
                    require(screen.getMenu().getCarried().getCount() == 16, "Right click did not take half of the stack");
                    var bounds = screen.viewport();
                    double x1 = bounds.left() + 106 * bounds.scaleX(), x2 = bounds.left() + 124 * bounds.scaleX(), y = bounds.top() + 196 * bounds.scaleY();
                    screen.mouseClicked(x1, y, 0);
                    screen.mouseDragged(x1, y, 0, 1, 0); screen.mouseDragged(x2, y, 0, x2 - x1, 0); screen.mouseReleased(x2, y, 0);
                    next();
                }
                case 15 -> {
                    var screen = nativeScreen(); var menu = screen.getMenu();
                    require(menu.getCarried().isEmpty(), "Native drag left a carried remainder");
                    require(menu.getSlot(SmartContainerMenu.PLAYER_START + 1).getItem().getCount() == 8
                            && menu.getSlot(SmartContainerMenu.PLAYER_START + 2).getItem().getCount() == 8
                            && menu.getSlot(0).getItem().getCount() == 16, "Native drag did not conserve and split the stack");
                    nativeClick(screen, 250, 27, 0);
                    for(char ch : "no_matching_item".toCharArray()) screen.charTyped(ch, 0);
                    next();
                }
                case 16 -> {
                    var screen = nativeScreen();
                    require(screen.getMenu().totalRows() == 0 && screen.getMenu().getSlot(0).getItem().isEmpty(), "Storage search did not filter the native grid");
                    capture("manifest-storage-search.png");
                    screen.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0); next();
                }
                case 17 -> {
                    var screen = nativeScreen();
                    require(screen.getMenu().getSlot(0).getItem().getCount() == 16, "Clearing search lost real storage contents");
                    nativeClick(screen, 78, 165, 0); stage = 21; ticks = 0;
                }
                case 21 -> {
                    var screen = nativeScreen(); var bounds = screen.viewport();
                    require(bounds.width() <= DiagnosticTabletScreen.projectionWidth() && bounds.height() <= DiagnosticTabletScreen.projectionHeight(), "Container expanded beyond tablet size");
                    if(screen.width >= 508 && screen.height >= 302)
                        require(bounds.width() == DiagnosticTabletScreen.projectionWidth() && bounds.height() == DiagnosticTabletScreen.projectionHeight(), "Container did not match the tablet dimensions");
                    require(screen.getGuiLeft() == bounds.left() && screen.getXSize() == bounds.width(), "Inventory overlays received canvas bounds");
                    capture("manifest-container-normal-size.png");
                    if(Boolean.getBoolean("gadgetsngizmos.manifestEnergyValidation")){
                        ManifestEnergyJeiValidation.focusEnergy(screen); stage = 23; ticks = 0; break;
                    }
                    finish("PASSED: native atlas cards and gauge frames; fitted counts and complete wrapped names; independent network and transfer panes; full transfer-unit labels; tablet-sized container viewport and overlay bounds; native pickup, right-click, shift-click, inventory drag and search without item loss");
                }
                case 23 -> { ManifestEnergyJeiValidation.checkEnergy(); ManifestEnergyJeiValidation.focusBookmark(nativeScreen()); next(); }
                case 24 -> { ManifestEnergyJeiValidation.checkBookmark(); ManifestEnergyJeiValidation.focusSidebar(nativeScreen()); next(); }
                case 25 -> { ManifestEnergyJeiValidation.checkSidebar(); ManifestEnergyJeiValidation.prepareBattery(); next(); }
                case 26 -> {
                    var screen = nativeScreen();
                    if(screen.getMenu().kind != 2 || !screen.getMenu().getSlot(SmartContainerMenu.PLAYER_START).hasItem()) return;
                    nativeClick(screen, 88, 196, 0); next();
                }
                case 27 -> {
                    require(!nativeScreen().getMenu().getCarried().isEmpty(), "Portable battery pickup failed");
                    nativeClick(nativeScreen(), 88, 115, 0); next();
                }
                case 28 -> {
                    require(nativeScreen().getMenu().getSlot(SmartContainerMenu.GRID_SIZE + 9).hasItem(), "Portable battery did not enter the native charge row");
                    capture("manifest-portable-battery-charging.png"); ManifestEnergyJeiValidation.checkCharging(); next();
                }
                case 29 -> {
                    if(!ManifestEnergyJeiValidation.charged()) return;
                    nativeClick(nativeScreen(), 88, 115, 0); stage = 32; ticks = 0;
                }
                case 32 -> {
                    require(!nativeScreen().getMenu().getCarried().isEmpty(), "Charged portable pickup failed");
                    nativeClick(nativeScreen(), 88, 75, 0); next();
                }
                case 33 -> {
                    require(nativeScreen().getMenu().getSlot(SmartContainerMenu.GRID_SIZE).hasItem(), "Portable battery did not enter the native discharge row");
                    capture("manifest-portable-battery-draining.png"); ManifestEnergyJeiValidation.checkDraining(); next();
                }
                case 34 -> {
                    if(!ManifestEnergyJeiValidation.drained()) return;
                    nativeClick(nativeScreen(), 78, 165, 0); stage = 30; ticks = 0;
                }
                case 30 -> { ManifestEnergyJeiValidation.focusEnergy(nativeScreen()); next(); }
                case 31 -> {
                    ManifestEnergyJeiValidation.checkEnergy(); capture("manifest-energy-icons-and-jei.png");
                    finish("PASSED: JEI bookmark and ingredient sidebar hover in window coordinates; untransformed background and foreground screen events; Worker Energy Battery and portable battery icons; actual native charge and discharge slot pickup and insertion; native portable charge and drain simulation, bounds, persistence and FE conservation; all Manifest inventory interaction regressions");
                }
                default -> throw new AssertionError("Unknown Manifest validation stage");
            }
        }catch(Throwable err){ LogUtils.getLogger().error("Painted Manifest validation failed", err); finish("FAILED: " + err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    ========================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void prepareNative(){
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            try{
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                int[] extracted = {-1};
                var handler = new net.neoforged.neoforge.items.ItemStackHandler(147){
                    @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
                        extracted[0] = slot; return super.extractItem(slot, amount, simulate);
                    }
                };
                var grid = new com.rieno.gadgetsandgizmos.lib.inventory.ItemStorageGrid();
                grid.refresh(List.of(handler, handler), "", 20, 2, 17);
                require(grid.rows() == 9 && grid.row() == 7, "Wide storage pages did not clamp or deduplicate");
                grid.extract(0, 1); require(extracted[0] == 119, "Wide storage page used another capability slot");
                grid.refresh(List.of(handler), "", 3, 2); grid.extract(0, 1);
                require(extracted[0] == 27, "Existing nine-column storage pages changed");
                grid.refresh(List.of(handler), "missing", 5, 2, 17);
                require(grid.rows() == 0 && grid.row() == 0, "Filtering did not reset the unavailable storage page");
                var level = player.serverLevel(); var pos = player.blockPosition().above(2);
                level.removeBlock(pos, false);
                level.setBlockAndUpdate(pos, CTBlocks.SMART_VAULT.get().defaultBlockState());
                var vault = (SmartVaultBlockEntity)level.getBlockEntity(pos);
                require(vault != null, "Could not place isolated storage");
                vault.mountedTabletState().select("stock");
                require(SmartContainerTablets.tab(vault).equals("contents"), "Saved Stock did not fall back to Contents");
                vault.mountedTabletState().select("storage");
                var rest = vault.getItemHandler().insertItem(0, new ItemStack(Items.DIAMOND, 32), false);
                require(rest.isEmpty(), "Could not seed isolated storage");
                player.getInventory().clearContent();
                var targets = new ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
                var items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(54).toList();
                for(int chestIdx = 0; chestIdx < 2; chestIdx++){
                    var chestPos = pos.offset(3 + chestIdx * 2, 0, 0); level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                    var chest = (ChestBlockEntity)level.getBlockEntity(chestPos);
                    for(int idx = 0; idx < 27; idx++) chest.setItem(idx, new ItemStack(items.get(chestIdx * 27 + idx)));
                    targets.add(chest);
                }
                var batteryPos = pos.offset(0, 0, 3); level.setBlockAndUpdate(batteryPos, CTBlocks.SMART_BATTERY.get().defaultBlockState());
                targets.add(level.getBlockEntity(batteryPos));
                var split = ContainerNetworkSnapshot.captureSplit(targets, 40, 0, 5);
                require(split.getCompound("Items").getInt("Total") == 54 && split.getCompound("Items").getInt("Offset") == 40, "Server item page was truncated");
                require(split.getCompound("Resources").getInt("Total") > 0 && split.getCompound("Resources").getInt("Offset") == 0, "Large item network hid FE");
                SmartContainerMenu.open(player, vault);
            }catch(Throwable err){ serverFailure = err.toString(); }
        });
    }
    private static SmartContainerScreen nativeScreen(){
        var screen = Minecraft.getInstance().screen;
        if(screen instanceof SmartContainerScreen nativeScreen) return nativeScreen;
        throw new AssertionError("Native storage screen closed unexpectedly: " + screen);
    }
    private static void nativeClick(SmartContainerScreen screen, int x, int y, int btn){
        var bounds = screen.viewport(); double px = bounds.left() + x * bounds.scaleX(), py = bounds.top() + y * bounds.scaleY();
        screen.mouseClicked(px, py, btn); screen.mouseReleased(px, py, btn);
    }
    private static void next(){ stage++; ticks = 0; }
    private static void require(boolean val, String msg){ if(!val) throw new AssertionError(msg); }
    private static void capture(String name){ var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {}); }
    private static void finish(String msg){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), msg); }catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(msg); Minecraft.getInstance().stop();
    }

    private static CompoundTag snapshot(){
        var data = new CompoundTag(); data.putBoolean("Attached", true); data.putBoolean("CanConfigure", true);
        data.putString("ContainerKind", "battery"); data.putLong("Energy", 1688845373L); data.putLong("EnergyCapacity", 5000000000L);
        data.putBoolean("PowerGrid", true); data.putDouble("PowerGridWattHours", 2351.235); data.putDouble("PowerGridCapacityWh", 6944.444);
        data.putLong("FluidAmount", 20000); data.putLong("FluidCapacity", 64000);
        var rows = new ListTag(); var level = Minecraft.getInstance().level;
        var samples = List.of(Items.NETHERRACK, Items.AMETHYST_SHARD, Items.ITEM_FRAME, Items.IRON_BARS, Items.PAPER, Items.IRON_INGOT, Items.OAK_PLANKS, Items.OAK_LOG, Items.CARROT, Items.DIAMOND, Items.BREAD, Items.COAL);
        var names = List.of("Netherrack", "Creative Oxidized Fuel", "Attitude Display", "Shaft", "Iron Sheet", "Iron Ingot", "Oak Planks", "Oak Log");
        long[] counts = {62, 1, 1, 6, 55, 534, 2, 1836, Long.MAX_VALUE};
        for(int idx = 0; idx < samples.size(); idx++){
            var stack = new ItemStack(samples.get(idx)); var row = new CompoundTag();
            row.put("Item", stack.saveOptional(level.registryAccess())); row.putString("Name", idx < names.size() ? names.get(idx) : stack.getHoverName().getString());
            row.putString("Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); row.putLong("Count", idx < counts.length ? counts[idx] : 28 - idx * 2);
            rows.add(row);
        }
        data.put("Items", rows); var fluids = new ListTag();
        for(String id : List.of("water", "lava")){
            var row = new CompoundTag(); row.putString("Id", "minecraft:" + id); row.putString("Name", id); row.putLong("Amount", 10000); row.putLong("Capacity", 32000); fluids.add(row);
        }
        data.put("Fluids", fluids); data.put("FluidChoices", fluids.copy()); data.put("Inventory", inventory("0|0"));
        return data;
    }
    private static CompoundTag inventory(String value){
        var root = new CompoundTag(); String[] offsets = value.split("\\|", 2);
        for(int pane = 0; pane < 2; pane++){
            int offset = Integer.parseInt(offsets[pane]); var page = new CompoundTag(); var rows = new ListTag();
            page.putInt("Total", pane == 0 ? 50 : 8); page.putInt("Offset", offset);
            for(int idx = offset; idx < Math.min(offset + 32, page.getInt("Total")); idx++){
                var row = new CompoundTag(); row.putString("Type", pane == 0 ? "item" : idx == 0 ? "energy" : "fluid");
                row.putString("Id", pane == 0 ? "minecraft:stick" : idx == 0 ? "neoforge:energy" : "minecraft:water");
                row.putString("Name", pane == 0 ? "Cargo " + idx : idx == 0 ? "FE" : "Water " + idx); row.putLong("Amount", 28); row.putLong("Capacity", 100);
                rows.add(row);
            }
            page.put("Rows", rows); root.put(pane == 0 ? "Items" : "Resources", page);
        }
        return root;
    }

    private static final class FixtureScreen extends Screen{
        private TabletAppClientRenderer renderer = new Manifest();
        private PaintedContainerRenderer.State state = (PaintedContainerRenderer.State)renderer.createScreenState();
        private com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition app = PaidTabletApps.MANIFEST;
        private final CompoundTag data = snapshot();
        private final List<String> actions = new ArrayList<>();
        private String tab = "cargo";
        private FixtureScreen(){ super(Component.literal("Painted Manifest validation")); }
        private void smart(){ app = SmartContainerManifest.APP; renderer = new SmartContainerManifestRenderer(); state = (PaintedContainerRenderer.State)renderer.createScreenState(); tab = "resources"; }
        private double x(int x){ return (width - 492) / 2 + x * 492D / 320; }
        private double y(int y){ return (height - 286) / 2 + y * 286D / 180; }
        private void click(int x, int y){ renderer.mouseClicked(ctx(null, 0, 0), state, x(x), y(y), 0); }
        private void wheel(int x, int y, double delta){ renderer.mouseScrolled(ctx(null, 0, 0), state, x(x), y(y), 0, delta); }
        private TabletAppClientContext ctx(GuiGraphics graphics, int mouseX, int mouseY){
            var selected = app.tabs().stream().filter(val -> val.id().equals(tab)).findFirst().orElseThrow();
            return new TabletAppClientContext(app, selected, data, graphics, Minecraft.getInstance().font, 0, 0, 320, 180,
                    new TabletAppClientSurface((width - 492) / 2, (height - 286) / 2, 492, 286), mouseX, mouseY, (action, val) -> {
                        actions.add(action + ":" + val);
                        if(action.equals("refresh") && val.contains("|")) data.put("Inventory", inventory(val));
                    }, () -> {}, new TabletAppClientUi(){
                        @Override public void selectTab(String id){ tab = id; }
                        @Override public void editText(String prompt, String initial, int limit, java.util.function.Consumer<String> accept){ accept.accept("24"); }
                    });
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick){ graphics.fill(0, 0, width, height, 0xFF17191C); renderer.render(ctx(graphics, -10000, -10000), state); }
        @Override public boolean isPauseScreen(){ return false; }
    }
}
