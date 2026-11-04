package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

// Exercise the actual ACC block renderer inside an active shader-pack world
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class WidgetShaderClientValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.widgetShaderValidation");
    private static final BlockPos ORIGIN = new BlockPos(0, 90, 0);
    private static boolean installed, complete;
    private static int ticks;
    private static AccDisplayBlockEntity display;
    private static CompoundTag frame;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        try{
            if(!installed){
                installed = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.overworld();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.setDayTime(6000);
                    level.setWeatherParameters(10000, 0, false, false);
                    for(int x = 0; x < 4; x++) for(int y = 0; y < 3; y++){
                        level.setBlock(ORIGIN.offset(x, y, 0), CTBlocks.ACC_DISPLAY.get().defaultBlockState()
                                .setValue(AccDisplayBlock.FACING, Direction.NORTH), 3);
                    }
                    for(var player : server.getPlayerList().getPlayers()){
                        player.setGameMode(GameType.SPECTATOR);
                        player.connection.teleport(2, 91.5 - player.getEyeHeight(), -5, 0, 0);
                    }
                });
            }
            if(++ticks < 80) return;
            if(display == null){
                for(int x = 0; x < 4; x++) for(int y = 0; y < 3; y++){
                    if(mc.level.getBlockEntity(ORIGIN.offset(x, y, 0)) instanceof AccDisplayBlockEntity candidate
                            && candidate.isNetworkRoot() && candidate.networkWidth() == 4 && candidate.networkHeight() == 3) display = candidate;
                }
                if(display == null){ if(ticks > 400) throw new AssertionError("ACC fixture did not join into a display"); return; }
                frame = frame(display);
            }
            var field = AccDisplayBlockEntity.class.getDeclaredField("displayFrame");
            field.setAccessible(true);
            field.set(display, frame);
        }catch(Throwable ex){ finish(ex); }
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post evt){
        if(OUTPUT == null || complete || display == null || ticks < 160) return;
        try{
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            if(!Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(iris))) throw new AssertionError("Shader pack is not active");
            Path output = Path.of(OUTPUT);
            Files.createDirectories(output);
            try(var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
                image.writeToFile(output.resolve("acc-widgets-shaders.png"));
                int magenta = 0, yellow = 0, cyan = 0;
                for(int y = image.getHeight() / 5; y < image.getHeight() * 4 / 5; y++){
                    for(int x = image.getWidth() / 5; x < image.getWidth() * 4 / 5; x++){
                        int color = image.getPixelRGBA(x, y);
                        int red = color & 255, green = color >>> 8 & 255, blue = color >>> 16 & 255;
                        if(red > green * 1.5 + 20 && blue > green * 1.5 + 20) magenta++;
                        if(red > blue * 1.5 + 20 && green > blue * 1.5 + 20) yellow++;
                        if(green > red * 1.5 + 20 && blue > red * 1.5 + 20) cyan++;
                    }
                }
                if(magenta < 100 || yellow < 100 || cyan < 100) throw new AssertionError(
                        "Widget fills missing under shaders: magenta=" + magenta + ", yellow=" + yellow + ", cyan=" + cyan);
                Files.writeString(output.resolve("result.txt"), "PASSED active shader-pack ACC rendering: magenta="
                        + magenta + ", yellow=" + yellow + ", cyan=" + cyan + "\n");
            }
            finish(null);
        }catch(Throwable ex){ finish(ex); }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static CompoundTag frame(AccDisplayBlockEntity display){
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", AdvancedGraphNodeFactory.Context.EMPTY);
        var target = new CompoundTag(); target.putLong("BlockPos", display.getBlockPos().asLong()); data.put("TargetData", target);
        data.putInt("WidgetWidth", 320); data.putInt("WidgetHeight", 180);
        var elements = new ListTag();
        String[] labels = {"Items in Chest: 323", "Items in Smart Vault: 7670", "FE in Smart Battery: 1500806085", "Stored Fluid: 121600"};
        int[] colors = {0xFF00FFFF, 0xFFFF00FF, 0xFFFF3030, 0xFFFFFF00};
        for(int idx = 0; idx < labels.length; idx++){
            int y = 12 + idx * 30;
            var text = new CompoundTag(); text.putString("Type", "text"); text.putString("Text", labels[idx]);
            text.putInt("X", 7); text.putInt("Y", y); text.putInt("W", 150); text.putInt("H", 18); text.putInt("FontSize", 9); elements.add(text);
            var progress = new CompoundTag(); progress.putString("Type", "progress"); progress.putString("Style", "striped");
            progress.putInt("X", 170); progress.putInt("Y", y); progress.putInt("W", 140); progress.putInt("H", 18);
            progress.putInt("AccentColor", colors[idx]); progress.putDouble("Min", 0); progress.putDouble("Max", 1);
            progress.putDouble("Value", .65); elements.add(progress);
        }
        data.put("WidgetElements", elements);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("shader_widget", "acc_display_widget", "Widget", 0, 0, data));
        var frame = new CompoundTag(); frame.putString("State", "graph"); frame.putString("Mode", "widgets"); frame.put("Graph", graph.toTag());
        return frame;
    }

    private static void finish(Throwable ex){
        complete = true;
        if(ex != null){
            try{
                var trace = new StringWriter(); ex.printStackTrace(new PrintWriter(trace));
                Path output = Path.of(OUTPUT); Files.createDirectories(output);
                Files.writeString(output.resolve("result.txt"), "FAILED\n" + trace);
            }catch(Exception ignored){}
        }
        Minecraft.getInstance().stop();
    }
}
