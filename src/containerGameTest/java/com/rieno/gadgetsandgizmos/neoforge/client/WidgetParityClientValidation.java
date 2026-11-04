package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.render.PlanarQuadClipBuffer;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderGeometryCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import com.rieno.gadgetsandgizmos.lib.display.WidgetElementLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Compare real GUI and world widget pixels after resource loading completes
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class WidgetParityClientValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.widgetParityValidation");
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
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        complete = true;
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path output = Path.of(OUTPUT);
        try{
            Files.createDirectories(output);
            validate(output);
        }catch(Throwable ex){
            try{
                var trace = new StringWriter();
                ex.printStackTrace(new PrintWriter(trace));
                Files.writeString(output.resolve("result.txt"), "FAILED\n" + trace);
            }catch(Exception ignored){}
        }finally{ mc.stop(); }
    }

    // Cover every control theme, font style, canvas aspect ratio and clipped text
    private static void validate(Path output) throws Exception{
        var randomField = FontSet.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        var glyphRandom = (RandomSource)randomField.get(null);
        List<CompoundTag> cases = new ArrayList<>();
        for(String type : List.of("box", "text", "value", "text_input", "image", "missing_image", "camera_source")) cases.add(element(type));
        for(String type : List.of("button", "toggle", "slider")) for(var style : HudStyleCatalog.ControlStyle.values()){
            CompoundTag elm = element(type);
            AdvancedHudInteractiveStyles.setStyle(elm, style.id());
            cases.add(elm);
        }
        for(var style : HudStyleCatalog.ProgressStyle.values()){
            CompoundTag elm = element("progress");
            AdvancedHudInteractiveStyles.setStyle(elm, style.id());
            cases.add(elm);
        }
        for(String style : AdvancedHudElementStyle.FONT_STYLE_OPTIONS){
            CompoundTag elm = element("text");
            AdvancedHudElementStyle.setFontStyle(elm, style);
            cases.add(elm);
        }
        var small = element("text_input"); small.putInt("W", 14); small.putInt("H", 7); cases.add(small);
        var clipped = element("text"); clipped.putInt("X", 270); clipped.putInt("Y", 177);
        clipped.putInt("W", 50); clipped.putInt("H", 3); clipped.putDouble("FontSize", 22); cases.add(clipped);
        var alphaImage = element("alpha_image"); cases.add(alphaImage);
        var pixels = new NativeImage(32, 32, false);
        for(int y = 0; y < 32; y++) for(int x = 0; x < 32; x++) pixels.setPixelRGBA(x, y, (x * 255 / 31 << 24) | 0x60B0EF);
        Minecraft.getInstance().getTextureManager().register(id("widget_parity_alpha"), new DynamicTexture(pixels));
        int count = 0;
        var cache = new RenderGeometryCache(true);
        try(var gui = new FramebufferGuiRenderer(id("widget_parity_gui"));
            var surface = new FramebufferGuiRenderer(id("widget_parity_surface"))){
            for(int[] raster : new int[][]{{320, 180}, {553, 271}, {249, 421}}){
                for(int idx = 0; idx < cases.size(); idx++){
                    CompoundTag elm = cases.get(idx).copy();
                    if(raster[0] != 320){ elm.putDouble("Rotation", 27); elm.putDouble("Scale", .83); }
                    CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> null);
                    glyphRandom.setSeed(12345);
                    gui.render(raster[0], raster[1], raster[0], raster[1], graphics -> draw(graphics, resolved, raster, false));
                    glyphRandom.setSeed(12345);
                    surface.render(raster[0], raster[1], raster[0], raster[1], graphics -> cache.render(new PoseStack(),
                            graphics.bufferSource(), true, buffers -> draw(graphics, resolved, raster, true, buffers)));
                    String name = raster[0] + "x" + raster[1] + "-" + idx + "-" + elm.getString("Type");
                    try(var expected = capture(gui, raster); var actual = capture(surface, raster)){
                        compare(expected, actual, name, output);
                        if(idx == 0 || "text_input".equals(elm.getString("Type"))){
                            expected.writeToFile(output.resolve(name + "-designer.png"));
                            actual.writeToFile(output.resolve(name + "-display.png"));
                        }
                    }
                    count++;
                }
                var back = element("box"); back.putInt("Color", 0xFF435577);
                var image = element("alpha_image");
                var front = element("box"); front.putInt("X", 82); front.putInt("Y", 61); front.putInt("Color", 0x80449922);
                var scene = List.of(back, image, front).stream().map(elm -> AdvancedHudElementBinding.resolvedCopy(elm, port -> null)).toList();
                gui.render(raster[0], raster[1], raster[0], raster[1], graphics -> {
                    for(var elm : scene) draw(graphics, elm, raster, false);
                });
                for(boolean refresh : new boolean[]{true, false}){
                    surface.render(raster[0], raster[1], raster[0], raster[1], graphics -> cache.render(new PoseStack(),
                            graphics.bufferSource(), refresh, buffers -> {
                                for(var elm : scene) draw(graphics, elm, raster, true, buffers);
                            }));
                    try(var expected = capture(gui, raster); var actual = capture(surface, raster)){
                        compare(expected, actual, raster[0] + "-overlapping-cached-" + refresh, output);
                    }
                    count++;
                }
            }
        }finally{ Minecraft.getInstance().getTextureManager().release(id("widget_parity_alpha")); }
        Files.writeString(output.resolve("result.txt"), "PASSED " + count + " widget pixel comparisons\n");
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void draw(GuiGraphics graphics, CompoundTag elm, int[] raster, boolean world){
        draw(graphics, elm, raster, world, graphics.bufferSource());
    }

    private static void draw(GuiGraphics graphics, CompoundTag elm, int[] raster, boolean world, MultiBufferSource buffers){
        var poses = graphics.pose();
        if(!world) graphics.enableScissor(0, 0, raster[0], raster[1]);
        poses.pushPose();
        poses.scale(raster[0] / 320F, raster[1] / 180F, world ? -1 : 1);
        try(var clip = world ? new PlanarQuadClipBuffer(buffers, poses.last().pose(), 320, 180) : null){
            var element = WidgetElementLayout.resolve(elm, 320, 180, true);
            HudWidgetRenderer.transform(poses, element);
            var font = Minecraft.getInstance().font;
            var canvas = world ? HudWidgetRenderer.surface(poses, clip, font, -.2F, -.004F, -.6F, LightTexture.FULL_BRIGHT)
                    : HudWidgetRenderer.gui(graphics, font);
            int width = element.bounds().width(), height = element.bounds().height();
            switch(elm.getString("Type")){
                case "camera_source" -> {
                    ViewFeedRenderer.background(canvas, width, height);
                    ViewFeedRenderer.placeholder(canvas, font, null, width, height);
                }
                case "missing_image" -> HudWidgetRenderer.placeholder(canvas, font, "IMG", width, height);
                case "image" -> canvas.draw(new GuiTextureRegion(ResourceLocation.withDefaultNamespace("textures/item/apple.png"), 16, 16, 0, 0, 16, 16), 0, 0, width, height);
                case "alpha_image" -> canvas.draw(new GuiTextureRegion(id("widget_parity_alpha"), 32, 32, 0, 0, 32, 32), 0, 0, width, height);
                default -> AdvancedHudWidgetRenderer.draw(canvas, font, elm, width, height, port -> null, true);
            }
        }finally{
            poses.popPose();
            if(!world) graphics.disableScissor();
        }
    }

    private static NativeImage capture(FramebufferGuiRenderer renderer, int[] raster){
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
        var image = new NativeImage(raster[0], raster[1], false);
        image.downloadTexture(0, false);
        return image;
    }

    // Permit only isolated raster edge rounding and one-channel blending differences
    private static void compare(NativeImage expected, NativeImage actual, String name, Path output) throws Exception{
        int mismatch = 0, ink = 0, background = expected.getPixelRGBA(0, 0);
        for(int y = 0; y < expected.getHeight(); y++) for(int x = 0; x < expected.getWidth(); x++){
            int a = expected.getPixelRGBA(x, y), b = actual.getPixelRGBA(x, y);
            if(a != background || b != background) ink++;
            for(int shift = 0; shift < 32; shift += 8){
                if(Math.abs((a >>> shift & 255) - (b >>> shift & 255)) <= 2) continue;
                mismatch++;
                break;
            }
        }
        if(mismatch <= Math.max(8, ink / 200)) return;
        expected.writeToFile(output.resolve(name + "-designer.png"));
        actual.writeToFile(output.resolve(name + "-display.png"));
        throw new AssertionError(name + ": " + mismatch + " differing pixels out of " + ink + " painted pixels");
    }

    private static CompoundTag element(String type){
        var elm = new CompoundTag();
        elm.putString("Type", type); elm.putString("Text", "Widget Mm Test");
        elm.putInt("X", 43); elm.putInt("Y", 39); elm.putInt("W", 154); elm.putInt("H", 43);
        elm.putDouble("Value", .63); elm.putDouble("Min", 0); elm.putDouble("Max", 1);
        elm.putDouble("FontSize", 14); elm.putInt("BorderWidth", 2); elm.putInt("BorderRadius", 8);
        if("toggle".equals(type)) elm.putBoolean("Value", true);
        AdvancedHudElementStyle.applyDefaults(elm);
        return elm;
    }

    private static ResourceLocation id(String path){ return ResourceLocation.fromNamespaceAndPath("createthrusters", path); }
}

