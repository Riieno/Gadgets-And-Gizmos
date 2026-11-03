package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiCanvasTransform;
import com.rieno.gadgetsandgizmos.lib.client.ui.ItemPickerScreen;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.ClockCardSettings;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.RedstoneLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
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

// Exercise the painted grid and the existing pickers through their real render and input paths
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class RedstoneCardsClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.redstoneCardsValidation");
    private static Fixture fixture;
    private static int stage, ticks;
    private static boolean complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 40) return;
        try{
            if(fixture == null){
                mc.options.pauseOnLostFocus = false;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                require(TabletAppClientRegistry.renderer(DiagnosticTabletData.appId("redstone_link")) instanceof RedstoneLink, "Painted Redstone Link was not registered");
                fixture = new Fixture(); mc.setScreen(fixture);
            }
            if(++ticks < 25) return;
            ticks = 0;
            switch(stage++){
                case 0 -> {
                    capture("redstone-grid-edit.png");
                    capture("redstone-tint-full.png");
                    fixture.channels.getCompound(1).putInt("Strength", 0);
                    stage = 90;
                }
                case 90 -> {
                    capture("redstone-tint-empty.png");
                    fixture.channels.getCompound(1).putInt("Strength", 7);
                    stage = 91;
                }
                case 91 -> {
                    capture("redstone-tint-half.png");
                    fixture.channels.getCompound(1).putInt("Strength", 13);
                    require(!fixture.renderer.mouseClicked(fixture.ctx(null), fixture.state, fixture.x(236), fixture.y(148), 0), "An installed Media app still added a permanent card");
                    fixture.click(109, 33);
                    stage = 1;
                }
                case 1 -> {
                    capture("redstone-designer.png");
                    fixture.click(175, 31); fixture.click(202, 105); fixture.click(255, 128);
                }
                case 2 -> { capture("redstone-icon-picker.png"); fixture.search("minecraft:diamond"); }
                case 3 -> { fixture.selectFirstItem(); }
                case 4 -> { fixture.click(130, 145); }
                case 5 -> {
                    capture("redstone-color-picker.png");
                    var style = CTColorPickerAppearance.DEFAULT;
                    var transform = GuiCanvasTransform.fit(style.panel().width(), style.panel().height(), 320, 180, 8);
                    fixture.click(transform.screenX(65), transform.screenY(140));
                    for(char ch : "#773344".toCharArray()) fixture.renderer.chatTyped(fixture.ctx(null), fixture.state, ch, 0);
                    fixture.key(GLFW.GLFW_KEY_ENTER);
                }
                case 6 -> { fixture.click(116, 123); }
                case 7 -> { capture("redstone-frequency-inventory.png"); fixture.click(139, 46); }
                case 8 -> { capture("redstone-frequency-item-picker.png"); fixture.search("minecraft:emerald"); }
                case 9 -> { fixture.selectFirstItem(); }
                case 10 -> {
                    fixture.renderer.mouseClicked(fixture.ctx(null), fixture.state, fixture.x(157), fixture.y(43), 1);
                    var before = fixture.state.saveDraft().getCompound("Card").copy();
                    fixture.click(85, 91);
                    require(Minecraft.getInstance().player.getInventory().getItem(9).isEmpty()
                            && Minecraft.getInstance().player.containerMenu.getCarried().getCount() == 12, "Inventory clicking did not perform native pickup");
                    require(before.equals(fixture.state.saveDraft().getCompound("Card")), "Inventory pickup immediately assigned a frequency slot");
                    fixture.release(157, 43);
                    require(Minecraft.getInstance().player.containerMenu.getCarried().getCount() == 12, "Ghost placement consumed the held inventory stack");
                    stage = 100;
                }
                case 100 -> {
                    capture("redstone-frequency-carried.png");
                    fixture.click(85, 91);
                    require(Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()
                            && Minecraft.getInstance().player.getInventory().getItem(9).getCount() == 12, "Held items could not be put back into inventory");
                    fixture.click(50, 160);
                    stage = 11;
                }
                case 11 -> { capture("redstone-designer-configured.png"); fixture.click(280, 162); }
                case 12 -> {
                    require(fixture.saved != null && fixture.saved.getString("Label").equals("Edited Factory"), "Card naming was not saved");
                    var style = ControlCardStyle.fromTag(fixture.saved.getCompound("Card"));
                    require(style.size() == ControlCardStyle.Size.WIDE && style.color() == 0x773344 && style.icon().getPath().equals("diamond_block"), "Card size, color or icon did not survive editor save: " + style);
                    require(fixture.saved.getString("FirstItem").equals("minecraft:emerald_block") && fixture.saved.getString("SecondItem").equals("minecraft:stone")
                            && fixture.saved.getInt("SecondColor") == 0x34ABCD, "Frequency pickers lost the chosen items or inventory color");
                    capture("redstone-grid-updated.png"); fixture.click(11, 48);
                }
                case 13 -> {
                    capture("redstone-grid-home.png"); fixture.click(119, 80);
                    require(fixture.actions.stream().anyMatch(val -> val.startsWith("channel_toggle:")), "Toggle did not reach its channel");
                    fixture.renderer.mouseClicked(fixture.ctx(null), fixture.state, fixture.x(100), fixture.y(150), 0);
                    fixture.renderer.mouseDragged(fixture.ctx(null), fixture.state, fixture.x(180), fixture.y(150), 0, 20, 0);
                    fixture.renderer.mouseReleased(fixture.ctx(null), fixture.state, fixture.x(180), fixture.y(150), 0);
                    require(fixture.actions.stream().anyMatch(val -> val.startsWith("channel_slider:")), "Slider did not send a strength");
                    fixture.click(11, 77);
                }
                case 14 -> {
                    fixture.channels.add(Fixture.channel("Tall Control", "button", ControlCardStyle.Size.TALL, 0x303030, "minecraft:lever"));
                    fixture.channels.add(Fixture.channel("Extra", "toggle", ControlCardStyle.Size.SINGLE, 0x303030, "minecraft:redstone"));
                    fixture.renderer.mouseScrolled(fixture.ctx(null), fixture.state, fixture.x(200), fixture.y(100), 0, -1);
                }
                case 15 -> { capture("redstone-grid-scrolled.png"); fixture.channels.clear(); fixture.data.putBoolean("MediaInstalled", false); }
                case 16 -> { fixture.click(80, 60); }
                case 17 -> { fixture.click(135, 162); fixture.click(202, 105); fixture.click(202, 105); fixture.click(255, 148); }
                case 18 -> {
                    capture("redstone-designer-tall.png");
                    var draft = fixture.state.saveDraft().getCompound("Card");
                    require(draft.getString("Mode").equals("button") && ControlCardStyle.fromTag(draft.getCompound("Card")).size() == ControlCardStyle.Size.TALL
                            && draft.getInt("Strength") == 7, "New card type, signal strength or repeated size edits were lost");
                    fixture.click(280, 162);
                }
                case 19 -> {
                    require(fixture.saved.getString("FirstItem").equals("minecraft:air") && fixture.saved.getString("SecondItem").equals("minecraft:air"), "An empty frequency card was not saved");
                    fixture.click(80, 150);
                    require(fixture.actions.stream().anyMatch(val -> val.startsWith("channel_button:")), "Button activation did not send its pulse");
                }
                case 20 -> { capture("redstone-button-pressed.png"); fixture.release(80, 150); }
                case 21 -> { capture("redstone-button-released.png"); fixture.click(108, 33); }
                case 22 -> { fixture.click(135, 162); fixture.click(135, 162); fixture.data.putBoolean("MediaInstalled", true); }
                case 23 -> {
                    capture("redstone-media-designer.png");
                    require(fixture.state.saveDraft().getCompound("Card").getString("Mode").equals("media"), "Media was not offered as a card type");
                    fixture.click(280, 162);
                }
                case 24 -> {
                    require(fixture.saved.getString("Mode").equals("media") && fixture.channels.size() == 1, "Media card editing created an extra permanent card");
                    fixture.click(80, 150);
                    require(fixture.actions.contains("media_pause:"), "The chosen Media card did not reach the existing player");
                    fixture.click(80, 53);
                    require(ResourceLocation.parse("createthrusters:media").equals(fixture.opened), "Clicking the Media track title did not open Media");
                }
                case 25 -> { capture("redstone-media-card.png"); fixture.click(96, 33); }
                case 26 -> {
                    require(fixture.channels.isEmpty(), "The selected Media card could not be removed");
                    capture("redstone-no-permanent-media.png");
                    fixture.click(80, 60);
                }
                case 27 -> { fixture.click(116, 123); }
                case 28 -> { fixture.click(85, 91); }
                case 29 -> {
                    require(!Minecraft.getInstance().player.containerMenu.getCarried().isEmpty(), "The close test did not pick up a stack");
                    fixture.key(GLFW.GLFW_KEY_ESCAPE);
                }
                case 30 -> {
                    require(Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()
                            && Minecraft.getInstance().player.getInventory().getItem(9).getCount() == 12, "Closing the modal lost held inventory items");
                }
                case 31 -> { for(int idx = 0; idx < 4; idx++) fixture.click(135, 162); fixture.click(202, 105); }
                case 32 -> {
                    capture("redstone-clock-analogue.png");
                    fixture.click(70, 122); fixture.click(150, 122);
                }
                case 33 -> { capture("redstone-clock-digital.png"); fixture.click(100, 136); }
                case 34 -> {
                    capture("redstone-clock-timezones.png");
                    for(char ch : "Europe/London".toCharArray()) fixture.renderer.chatTyped(fixture.ctx(null), fixture.state, ch, 0);
                    fixture.key(GLFW.GLFW_KEY_ENTER);
                }
                case 35 -> {
                    var clock = ControlCardStyle.fromTag(fixture.state.saveDraft().getCompound("Card").getCompound("Card")).clock();
                    require(clock.digital() && clock.realTime() && clock.timezone().equals("Europe/London"), "Clock settings were not retained by the editor");
                    capture("redstone-clock-configured.png"); fixture.click(280, 162);
                }
                case 36 -> {
                    require(fixture.saved.getString("Mode").equals("clock"), "Clock was not saved as a card type");
                    fixture.channels.add(Fixture.channel("Second", "toggle", ControlCardStyle.Size.SINGLE, 0x445577, "minecraft:lever"));
                    fixture.channels.add(Fixture.channel("Third", "button", ControlCardStyle.Size.SINGLE, 0x775544, "minecraft:stone_button"));
                }
                case 37 -> {
                    fixture.click(80, 34);
                    fixture.renderer.mouseDragged(fixture.ctx(null), fixture.state, fixture.x(236), fixture.y(58), 0, 100, 20);
                    fixture.renderer.mouseDragged(fixture.ctx(null), fixture.state, fixture.x(236), fixture.y(58), 0, 0, 0);
                }
                case 38 -> { capture("redstone-card-drag.png"); fixture.release(236, 58); }
                case 39 -> {
                    capture("redstone-card-reordered.png");
                    require(fixture.channels.getCompound(0).getString("Label").equals("Second") && fixture.channels.getCompound(1).getString("Mode").equals("clock"),
                            "Drag-to-reorder did not keep a stable destination");
                    require(fixture.actions.stream().filter(action -> action.startsWith("card_reorder:")).count() == 1, "Reordering sent repeated writes while dragging");
                    finish("PASS");
                }
            }
        }catch(Throwable err){ err.printStackTrace(); finish("FAIL " + err); }
    }

    private static void require(boolean value, String message){ if(!value) throw new AssertionError(message); }
    private static void capture(String name){ var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {}); }
    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), result); }catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }

    private static final class Fixture extends Screen{
        private final RedstoneLink renderer = new RedstoneLink(CTColorPickerAppearance.DEFAULT);
        private final TabletAppClientState state = renderer.createScreenState();
        private final CompoundTag data = new CompoundTag();
        private final ListTag channels = new ListTag();
        private final List<String> actions = new ArrayList<>();
        private String tab = "devices";
        private CompoundTag saved;
        private int pointerX, pointerY;
        private ResourceLocation opened;

        private Fixture(){
            super(Component.literal("Redstone cards validation"));
            var factory = channel("Factory", "toggle", ControlCardStyle.Size.SINGLE, 0xFF00FF, "minecraft:spyglass"); factory.putBoolean("Active", true);
            channels.add(factory);
            var slider = channel("Slider", "slider", ControlCardStyle.Size.WIDE, 0x00FF00, "minecraft:carrot");
            slider.putInt("Strength", 15); channels.add(slider);
            channels.add(channel("Lights", "toggle", ControlCardStyle.Size.SINGLE, 0x303030, "minecraft:tnt"));
            data.put("Channels", channels); data.putInt("MaxChannels", 24); data.putLong("UpdatedAt", 1);
            data.putBoolean("MediaInstalled", true);
            data.put("Media", new MediaPlaybackState(ResourceLocation.parse("minecraft:music_disc_cat"), true,
                    200, Minecraft.getInstance().level.getGameTime(), 3600, 1).toTag());
            var stack = new ItemStack(Items.STONE, 12); stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x34ABCD, false));
            var server = Minecraft.getInstance().getSingleplayerServer();
            var playerId = Minecraft.getInstance().player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                player.getInventory().setItem(9, stack);
                player.inventoryMenu.broadcastChanges();
            });
        }
        @Override public boolean isPauseScreen(){ return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial){ renderer.render(ctx(graphics), state); }
        private TabletAppClientContext ctx(GuiGraphics graphics){
            var app = TabletAppRegistry.definition(DiagnosticTabletData.appId("redstone_link"));
            var selected = app.tabs().stream().filter(val -> val.id().equals(tab)).findFirst().orElseThrow();
            return new TabletAppClientContext(app, selected, data, graphics, Minecraft.getInstance().font, 0, 0, width, height,
                    new TabletAppClientSurface(0, 0, width, height), pointerX, pointerY, this::action, () -> {}, new TabletAppClientUi(){
                @Override public void selectTab(String value){ tab = value; }
                @Override public void openApp(ResourceLocation id, String target){ opened = id; }
                @Override public void editText(String prompt, String initial, int maximum, java.util.function.Consumer<String> accept){ accept.accept(prompt.startsWith("Signal strength") ? "7" : "Edited Factory"); }
            });
        }
        private double x(double value){ return width * value / 320D; }
        private double y(double value){ return height * value / 180D; }
        private void click(double x, double y){
            pointerX = (int)x(x); pointerY = (int)y(y);
            require(renderer.mouseClicked(ctx(null), state, x(x), y(y), 0), "Control did not receive click: " + x + "," + y);
        }
        private void release(double x, double y){
            pointerX = (int)x(x); pointerY = (int)y(y);
            renderer.mouseReleased(ctx(null), state, x(x), y(y), 0);
        }
        private void key(int key){ renderer.keyPressed(ctx(null), state, key, 0, 0); }
        private void search(String value){ key(GLFW.GLFW_KEY_TAB); for(char ch : value.toCharArray()) renderer.chatTyped(ctx(null), state, ch, 0); }
        private void selectFirstItem(){
            var appearance = ItemPickerScreen.defaultAppearance();
            var transform = GuiCanvasTransform.fit(appearance.panel().width(), appearance.panel().height(), 320, 180, 8, 2);
            var grid = appearance.grid().grid();
            click(transform.screenX(grid.x() + 15), transform.screenY(grid.y() + 15)); key(GLFW.GLFW_KEY_ENTER);
        }
        private void action(String action, String val){
            actions.add(action + ":" + val);
            if(action.equals("card_reorder")){
                var ordered = new ArrayList<CompoundTag>();
                for(String id : val.split("\\|")) for(var tag : channels){
                    var row = (CompoundTag)tag;
                    if(row.getUUID("Id").toString().equals(id)) ordered.add(row.copy());
                }
                channels.clear(); ordered.forEach(channels::add);
                data.putLong("UpdatedAt", data.getLong("UpdatedAt") + 1);
                return;
            }
            if(action.equals("channel_remove")){
                for(int idx = channels.size() - 1; idx >= 0; idx--) if(channels.getCompound(idx).getUUID("Id").toString().equals(val)) channels.remove(idx);
                return;
            }
            if(!action.equals("card_save")) return;
            try{ saved = TagParser.parseTag(val); }catch(Exception err){ throw new RuntimeException(err); }
            var id = saved.hasUUID("Id") ? saved.getUUID("Id") : UUID.randomUUID();
            saved.putUUID("Id", id);
            if(saved.getString("FirstItem").isBlank()) saved.putString("FirstItem", "minecraft:air");
            if(saved.getString("SecondItem").isBlank()) saved.putString("SecondItem", "minecraft:air");
            boolean found = false;
            for(int idx = 0; idx < channels.size(); idx++) if(channels.getCompound(idx).getUUID("Id").equals(id)){ channels.set(idx, saved.copy()); found = true; break; }
            if(!found) channels.add(saved.copy());
            data.putUUID("SavedCard", id); data.putLong("UpdatedAt", data.getLong("UpdatedAt") + 1);
        }
        private static CompoundTag channel(String label, String mode, ControlCardStyle.Size size, int color, String icon){
            var tag = new CompoundTag(); tag.putUUID("Id", UUID.randomUUID()); tag.putString("Label", label); tag.putString("Mode", mode);
            tag.putString("FirstItem", "minecraft:diamond"); tag.putInt("FirstColor", -1);
            tag.putString("SecondItem", "minecraft:emerald"); tag.putInt("SecondColor", -1); tag.putInt("Strength", 13);
            tag.put("Card", new ControlCardStyle(size, color, ResourceLocation.parse(icon)).toTag()); return tag;
        }
    }
}
