package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.SmartBatteryBlockEntity;
import com.rieno.gadgetsandgizmos.content.SmartContainerMenu;
import com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridPortableBattery;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.patryk3211.powergrid.equipment.portablebattery.BatteryUtils;

// Exercise the optional integrations with their real runtime mods in the isolated client world
@JeiPlugin
public final class ManifestEnergyJeiValidation implements IModPlugin{
    private static IJeiRuntime runtime;
    private static int bgX, bgY, fgX, fgY;
    private static boolean bgIdentity, fgIdentity;
    private static volatile SmartBatteryBlockEntity battery;
    private static volatile long initialEnergy;
    private static volatile String failure;
    private static volatile boolean charged;
    private static volatile boolean drained;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public ResourceLocation getPluginUid(){ return ResourceLocation.fromNamespaceAndPath("createthrusters", "manifest_energy_validation"); }
    @Override public void onRuntimeAvailable(IJeiRuntime res){
        runtime = res;
        if(!Boolean.getBoolean("gadgetsngizmos.manifestEnergyValidation")) return;
        NeoForge.EVENT_BUS.addListener(ManifestEnergyJeiValidation::background);
        NeoForge.EVENT_BUS.addListener(ManifestEnergyJeiValidation::foreground);
    }

    private static void background(ContainerScreenEvent.Render.Background evt){
        if(!(evt.getContainerScreen() instanceof SmartContainerScreen)) return;
        bgX = evt.getMouseX(); bgY = evt.getMouseY(); bgIdentity = evt.getGuiGraphics().pose().last().pose().equals(new org.joml.Matrix4f());
    }
    private static void foreground(ContainerScreenEvent.Render.Foreground evt){
        if(!(evt.getContainerScreen() instanceof SmartContainerScreen)) return;
        fgX = evt.getMouseX(); fgY = evt.getMouseY(); fgIdentity = evt.getGuiGraphics().pose().last().pose().equals(new org.joml.Matrix4f());
    }

    public static void focusEnergy(SmartContainerScreen screen){
        require(runtime != null && runtime.getIngredientListOverlay().isListDisplayed(), "JEI was not loaded and visible");
        try{
            Object overlay = runtime.getBookmarkOverlay();
            var listField = overlay.getClass().getDeclaredField("bookmarkList"); listField.setAccessible(true);
            Object list = listField.get(overlay);
            var factoryField = list.getClass().getDeclaredField("bookmarkFactory"); factoryField.setAccessible(true);
            Object factory = factoryField.get(list);
            Object ingredient = runtime.getIngredientManager().createTypedIngredient(new ItemStack(Items.COPPER_INGOT)).orElseThrow();
            Object bookmark = factory.getClass().getMethod("create", mezz.jei.api.ingredients.ITypedIngredient.class).invoke(factory, ingredient);
            list.getClass().getMethod("add", Class.forName("mezz.jei.gui.bookmarks.IBookmark")).invoke(list, bookmark);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
        var bounds = screen.viewport();
        pointer(bounds.left() + 27 * bounds.scaleX(), bounds.top() + 36 * bounds.scaleY());
    }
    public static void checkEnergy(){
        checkEvents();
        require(runtime.getBookmarkOverlay().getIngredientUnderMouse().isEmpty(), "FE icon still hovered a JEI bookmark");
        require(runtime.getIngredientListOverlay().getIngredientUnderMouse().isEmpty(), "FE icon still hovered the JEI sidebar");
    }
    public static void focusBookmark(SmartContainerScreen screen){
        for(int y = 24; y < screen.height - 28; y += 8){
            for(int x = 4; x < screen.getGuiLeft(); x += 8){
                pointer(x, y);
                if(runtime.getBookmarkOverlay().getIngredientUnderMouse().flatMap(val -> val.getItemStack()).filter(val -> val.is(Items.COPPER_INGOT)).isPresent()) return;
            }
        }
        throw new AssertionError("Copper bookmark was not visible");
    }
    public static void checkBookmark(){
        checkEvents();
        require(runtime.getBookmarkOverlay().getIngredientUnderMouse().flatMap(val -> val.getItemStack()).filter(val -> val.is(Items.COPPER_INGOT)).isPresent(), "Actual bookmark position did not hover copper");
    }
    public static void focusSidebar(SmartContainerScreen screen){
        for(int y = 24; y < screen.height - 28; y += 8){
            for(int x = screen.getGuiLeft() + screen.getXSize() + 4; x < screen.width - 4; x += 8){
                pointer(x, y);
                if(runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent()) return;
            }
        }
        throw new AssertionError("Right JEI sidebar was not visible");
    }
    public static void checkSidebar(){
        checkEvents();
        require(runtime.getIngredientListOverlay().getIngredientUnderMouse().isPresent(), "Actual sidebar position did not hover an ingredient");
    }
    private static void checkEvents(){
        var mc = Minecraft.getInstance(); var window = mc.getWindow();
        int x = (int)(mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int y = (int)(mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        require(bgX == x && bgY == y && fgX == x && fgY == y, "Container events transformed the sidebar pointer");
        require(bgIdentity && fgIdentity, "Container events transformed the sidebar graphics");
    }
    private static void pointer(double x, double y){
        var mc = Minecraft.getInstance(); var window = mc.getWindow();
        try{
            var xField = mc.mouseHandler.getClass().getDeclaredField("xpos"); xField.setAccessible(true);
            var yField = mc.mouseHandler.getClass().getDeclaredField("ypos"); yField.setAccessible(true);
            xField.setDouble(mc.mouseHandler, x * window.getScreenWidth() / window.getGuiScaledWidth());
            yField.setDouble(mc.mouseHandler, y * window.getScreenHeight() / window.getGuiScaledHeight());
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        NATIVE CHARGING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void prepareBattery(){
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            try{
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var level = player.serverLevel(); var pos = player.blockPosition().above(2);
                player.closeContainer(); level.removeBlock(pos, false);
                level.setBlockAndUpdate(pos, CTBlocks.SMART_BATTERY.get().defaultBlockState());
                battery = (SmartBatteryBlockEntity)level.getBlockEntity(pos);
                require(battery != null, "Smart Battery could not be placed");
                var stack = PowerGridPortableBattery.item(); var storage = PowerGridPortableBattery.storage(stack);
                require(storage != null && storage.getMaxEnergyStored() > 0, "Native portable battery adapter was unavailable");
                require(storage.receiveEnergy(100, true) == 100 && BatteryUtils.getCurrentCharge(stack) == 0, "Simulated charge mutated native charge");
                require(storage.receiveEnergy(-1, false) == 0 && storage.extractEnergy(-1, false) == 0 && storage.canExtract(), "Portable adapter allowed invalid transfers");
                require(battery.chargingItems().isItemValid(0, stack) && battery.chargingItems().isItemValid(9, stack), "Portable battery was rejected by charge or discharge slots");
                require(PowerGridPortableBattery.storage(new ItemStack(Items.STICK)) == null, "Portable adapter accepted another item");
                battery.receiveEnergy(1_000_000, false);
                long before = battery.getEnergyStored();
                require(battery.chargingItems().insertItem(9, stack, false).isEmpty(), "Portable battery insertion failed");
                battery.chargingItems().tick(battery.getEnergyHandler());
                var stored = battery.chargingItems().extractItem(9, 1, false);
                int charge = BatteryUtils.getCurrentCharge(stored);
                require(charge > 0 && before == battery.getEnergyStored() + charge, "Charging did not conserve FE in the native component");
                var saved = stored.saveOptional(level.registryAccess());
                require(BatteryUtils.getCurrentCharge(ItemStack.parseOptional(level.registryAccess(), (net.minecraft.nbt.CompoundTag)saved)) == charge, "Portable charge did not survive item saving");
                var full = PowerGridPortableBattery.storage(stored);
                int capacity = full.getMaxEnergyStored(); full.receiveEnergy(Integer.MAX_VALUE, false);
                require(full.getEnergyStored() == capacity && full.receiveEnergy(1, false) == 0, "Portable charge exceeded native capacity");
                int fullCharge = BatteryUtils.getCurrentCharge(stored);
                require(full.extractEnergy(100, true) == 100 && BatteryUtils.getCurrentCharge(stored) == fullCharge, "Simulated discharge mutated native charge");
                battery.extractEnergy(Long.MAX_VALUE, false);
                require(battery.chargingItems().insertItem(0, stored, false).isEmpty(), "Portable battery discharge insertion failed");
                battery.chargingItems().tick(battery.getEnergyHandler());
                var remaining = battery.chargingItems().extractItem(0, 1, false);
                require(battery.getEnergyStored() > 0 && battery.getEnergyStored() + BatteryUtils.getCurrentCharge(remaining) == fullCharge, "Portable draining did not conserve FE");
                var drainedStorage = PowerGridPortableBattery.storage(remaining);
                drainedStorage.extractEnergy(Integer.MAX_VALUE, false);
                require(drainedStorage.getEnergyStored() == 0 && drainedStorage.extractEnergy(1, false) == 0, "Portable draining exceeded native charge");
                battery.extractEnergy(Long.MAX_VALUE, false);
                initialEnergy = battery.receiveEnergy(1_000_000, false);
                player.getInventory().clearContent(); player.getInventory().setItem(9, PowerGridPortableBattery.item());
                battery.mountedTabletState().select("storage"); SmartContainerMenu.open(player, battery);
            }catch(Throwable err){ failure = err.toString(); }
        });
    }
    public static void checkCharging(){
        require(failure == null, "Server validation: " + failure);
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try{
                var stack = battery.chargingItems().getStackInSlot(9);
                int charge = BatteryUtils.getCurrentCharge(stack);
                require(!stack.isEmpty() && charge > 0, "Native charge slot did not charge the portable battery");
                require(battery.getEnergyStored() + charge == initialEnergy, "Native slot charging did not conserve FE");
                charged = true;
            }catch(Throwable err){ failure = err.toString(); }
        });
    }
    public static void checkDraining(){
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try{
                var stack = battery.chargingItems().getStackInSlot(0);
                require(!stack.isEmpty() && BatteryUtils.getCurrentCharge(stack) == 0, "Native discharge slot did not drain the portable battery");
                require(battery.getEnergyStored() == initialEnergy, "Native discharge slot did not restore the charged FE");
                drained = true;
            }catch(Throwable err){ failure = err.toString(); }
        });
    }
    public static boolean drained(){ require(failure == null, "Server validation: " + failure); return drained; }
    public static boolean charged(){ require(failure == null, "Server validation: " + failure); return charged; }
    private static void require(boolean val, String msg){ if(!val) throw new AssertionError(msg); }
}
