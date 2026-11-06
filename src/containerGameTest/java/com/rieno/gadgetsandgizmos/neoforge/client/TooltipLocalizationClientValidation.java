package com.rieno.gadgetsandgizmos.neoforge.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.CTTooltipHelper;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.text.UserMessageException;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

// Check translated server snapshots against a real Japanese client language pack
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class TooltipLocalizationClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.tooltipLocalizationValidation");
    private static boolean complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        complete = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path out = Path.of(OUTPUT);
        try{
            if(!"ja_jp".equals(mc.getLanguageManager().getSelected())) throw new AssertionError("Japanese was not selected");
            validateNamespace(mc, "createthrusters");
            validateNamespace(mc, "gadgetsngizmos");
            var msg = Component.translatable("createthrusters.text.g_g_auto_is_available_only_on_an_initialized_ship")
                    .withStyle(ChatFormatting.RED);
            var handler = Class.forName("com.rieno.gadgetsandgizmos.content.DiagnosticTabletAutoApp")
                    .getDeclaredMethod("failure", Component.class);
            handler.setAccessible(true);
            var res = (TabletActionHandler.Result)handler.invoke(null, msg);
            if(res.success() || !(res.message().getContents() instanceof TranslatableContents))
                throw new AssertionError("The tablet failure lost its translation key");
            CompoundTag data = new CompoundTag();
            LocalizedText.put(data, "Error", LocalizedText.message(new UserMessageException(res.message())));
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            Component restored;
            try{
                DiagnosticTabletAppSnapshotPayload.STREAM_CODEC.encode(buf, new DiagnosticTabletAppSnapshotPayload(
                        ResourceLocation.fromNamespaceAndPath("createthrusters", "gg_auto"), data));
                restored = LocalizedText.read(DiagnosticTabletAppSnapshotPayload.STREAM_CODEC.decode(buf).data(), "Error");
            }finally{ buf.release(); }
            if(!restored.equals(msg) || !japanese(restored.getString()))
                throw new AssertionError("The received tablet error is not Japanese: " + restored.getString());
            var nested = Component.translatable("createthrusters.text.dock_renamed_to_1_s", CTTooltipHelper.readyIdle(true));
            LocalizedText.put(data, "Error", nested);
            if(!nested.equals(LocalizedText.read(data, "Error"))) throw new AssertionError("Nested component arguments were flattened");
            data.put("Error", new CompoundTag());
            if(!LocalizedText.read(data, "Error").getString().isEmpty()) throw new AssertionError("Malformed errors must be ignored");
            if(!japanese(CTTooltipHelper.readyIdle(true).getString()) || !japanese(CTTooltipHelper.linked(false).getString()))
                throw new AssertionError("Goggle state values are still English");
            try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "tooltip_localization_validation"))){
                renderer.render(640, 180, 640, 180, graphics -> {
                    graphics.fill(0, 0, 640, 180, 0xFF10191D);
                    graphics.drawWordWrap(mc.font, restored, 12, 12, 610, 0xFFFFFFFF);
                    graphics.drawString(mc.font, CTTooltipHelper.linked(false), 12, 50, 0xFFFFFFFF, false);
                    graphics.drawWordWrap(mc.font, Component.translatable("gadgetsngizmos.text.name_or_id_mod_sound_event_tag"), 12, 76, 610, 0xFFFFFFFF);
                    graphics.drawWordWrap(mc.font, Component.translatable("gadgetsngizmos.text.connect_to_an_initialized_ship_with_an_advanced_contraption_controller"), 12, 110, 610, 0xFFFFFFFF);
                });
                RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
                try(var screenshot = new NativeImage(640, 180, false)){
                    screenshot.downloadTexture(0, false);
                    screenshot.flipY();
                    screenshot.writeToFile(out.resolve("japanese-tooltips.png"));
                }
            }
            Files.writeString(out.resolve("result.txt"), "PASSED Japanese language resources, formatted text, goggle states, translated validation failures and compressed tablet snapshot round trips\n");
        }catch(Throwable err){
            var trace = new StringWriter();
            err.printStackTrace(new PrintWriter(trace));
            try{ Files.writeString(out.resolve("result.txt"), "FAILED\n" + trace); }
            catch(Exception ignored){}
        }finally{ mc.stop(); }
    }

    // Check the entries added for literal UI text using Minecraft's own translation parser
    private static void validateNamespace(Minecraft mc, String namespace) throws Exception{
        var id = ResourceLocation.fromNamespaceAndPath(namespace, "lang/en_us.json");
        try(var reader = mc.getResourceManager().getResource(id).orElseThrow().openAsReader()){
            var entries = JsonParser.parseReader(reader).getAsJsonObject();
            for(var entry : entries.entrySet()){
                if(!entry.getKey().startsWith(namespace + ".text.")) continue;
                var val = Component.translatable(entry.getKey(), "ARG_A", "ARG_B", "ARG_C", "ARG_D", "ARG_E", "ARG_F").getString();
                if(val.equals(entry.getKey()) || val.contains("GGX") || val.contains("%1$s"))
                    throw new AssertionError("Unresolved translation: " + entry.getKey() + " = " + val);
            }
        }
    }

    private static boolean japanese(String val){
        return val.codePoints().anyMatch(c -> c >= 0x3040 && c <= 0x30FF || c >= 0x4E00 && c <= 0x9FFF);
    }
}
