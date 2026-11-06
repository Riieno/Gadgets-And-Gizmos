package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlLayout;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlPanel;
import com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlState;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Check overlay visibility, hidden hit regions and real client rendering across feed sizes
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class CameraOverlayClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.cameraOverlayValidation");
    // Run once after fonts and client configuration finish loading
    private static boolean complete, hidden;

    // Validate the shared overlay without opening or changing a saved world
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        complete = true;
        Path out = Path.of(OUTPUT);
        boolean prevGrid = ViewfinderConfig.GRID.get();
        try{
            ViewfinderConfig.GRID.set(true);
            for(int[] size : new int[][]{{640, 360}, {640, 160}, {360, 640}, {320, 180}}){
                validate(mc, size[0], size[1], out);
            }
            Files.writeString(out.resolve("result.txt"), "PASSED four feed sizes, hidden overlay pixels, hidden hit regions, drag release and visibility restoration\n");
        }catch(Throwable err){
            err.printStackTrace();
            try{ Files.writeString(out.resolve("result.txt"), "FAILED " + err); }
            catch(Exception writeErr){ throw new RuntimeException(writeErr); }
        }finally{
            ViewfinderConfig.GRID.set(prevGrid);
            mc.stop();
        }
    }

    // Hide while dragging and check that only the bottom-left button remains active and painted
    private static void validate(Minecraft mc, int width, int height, Path out) throws Exception{
        var panel = new ViewControlPanel();
        var state = new ViewControlState(ViewRig.Mode.MANUAL, 70, false);
        var inputs = new ArrayList<ViewControlInput>();
        panel.setTakeControlAction(() -> { throw new AssertionError("Hidden takeover activated"); });
        panel.setInteractAction(() -> { throw new AssertionError("Hidden interaction activated"); });
        var layout = ViewControlLayout.overlay(17, 13, width, height);
        var toggle = layout.overlayToggle();
        panel.renderOverlay(new HiddenCanvas(null), mc.font, 17, 13, width, height, state, inputs::add);
        if(toggle.x() < 17 || toggle.y() < 13 || toggle.x() + toggle.width() > 17 + width
                || toggle.y() + toggle.height() > 13 + height)
            throw new AssertionError("Toggle lies outside the feed");
        if(Math.hypot(toggle.x() + toggle.width() - layout.stickX(), toggle.centerY() - layout.stickY()) <= layout.radius() + 5)
            throw new AssertionError("Toggle overlaps the stick hit region");
        if(!panel.mouseClicked(layout.stickX(), layout.stickY(), 0) || !panel.isDragging())
            throw new AssertionError("Stick fixture did not capture the pointer");
        if(!panel.mouseClicked(toggle.centerX(), toggle.centerY(), 0) || panel.isOverlayVisible() || panel.isDragging())
            throw new AssertionError("Hiding did not release the stick");
        inputs.clear();
        panel.renderOverlay(new HiddenCanvas(toggle), mc.font, 17, 13, width, height, state, inputs::add);
        var regions = new ArrayList<>(List.of(layout.zoom(), layout.flashlight(), layout.tracking(), layout.targetLock(),
                layout.takeControl(), layout.frame(), layout.grid(), layout.interact()));
        regions.addAll(layout.modes());
        for(var bounds : regions){
            if(panel.mouseClicked(bounds.centerX(), bounds.centerY(), 0))
                throw new AssertionError("A hidden control accepted a click");
        }
        if(panel.mouseClicked(layout.stickX(), layout.stickY(), 0) || panel.mouseDragged(layout.stickX(), layout.stickY())
                || !inputs.isEmpty()) throw new AssertionError("A hidden control emitted camera input");
        if(!new ViewControlPanel().isOverlayVisible()) throw new AssertionError("Visibility leaked into another feed");
        if(!panel.mouseClicked(toggle.centerX(), toggle.centerY(), 1) || !panel.isOverlayVisible() || !ViewfinderConfig.GRID.get())
            throw new AssertionError("The toggle did not restore the overlay and grid preference");
        if(width == 640 && height == 360){
            for(boolean visible : new boolean[]{true, false}){
                panel.setOverlayVisible(visible);
                render(mc, panel, state, visible, out);
            }
        }
    }

    // Capture the real GUI path over a contrasting feed background
    private static void render(Minecraft mc, ViewControlPanel panel, ViewControlState state, boolean visible, Path out) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "camera_overlay_validation"))){
            renderer.render(640, 360, 640, 360, graphics -> {
                graphics.fill(0, 0, 640, 360, 0xFF273E54);
                panel.renderOverlay(graphics, mc.font, 0, 0, 640, 360, state, input -> {});
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var screenshot = new NativeImage(640, 360, false)){
                screenshot.downloadTexture(0, false);
                screenshot.flipY();
                screenshot.writeToFile(out.resolve(visible ? "visible.png" : "hidden.png"));
            }
        }
    }

    // Reject any projected drawing outside the visibility toggle while the overlay is hidden
    private record HiddenCanvas(ViewControlLayout.Bounds toggle) implements ViewControlPanel.Canvas{
        @Override public void fill(int left, int top, int right, int bottom, int color){
            if(toggle != null && (left < toggle.x() || top < toggle.y()
                    || right > toggle.x() + toggle.width() || bottom > toggle.y() + toggle.height()))
                throw new AssertionError("Hidden overlay drew outside the toggle");
        }
        @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow){
            if(toggle != null) throw new AssertionError("Hidden overlay drew status text");
        }
    }
}
