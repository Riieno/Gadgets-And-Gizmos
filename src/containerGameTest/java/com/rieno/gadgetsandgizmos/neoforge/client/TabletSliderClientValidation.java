package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import java.util.UUID;

// Drive real painted slider hit targets through screen and projected click input
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class TabletSliderClientValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.tabletSliderValidation");
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
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try{
            validate();
            Files.writeString(Path.of(OUTPUT), "PASSED slider rendering, fixed-output visibility, screen drag and projected clicks for strengths 0-15\n");
        }catch(Throwable ex){
            ex.printStackTrace();
            try{ Files.writeString(Path.of(OUTPUT), "FAILED " + ex); }catch(Exception ignored){}
        }finally{ mc.stop(); }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void validate(){
        var id = DiagnosticTabletData.appId("redstone_link");
        var renderer = TabletAppClientRegistry.renderer(id);
        if(renderer == null) throw new AssertionError("Redstone Link renderer is not registered");
        var data = new CompoundTag(); var channels = new ListTag(); var channel = new CompoundTag();
        channel.putUUID("Id", UUID.randomUUID()); channel.putString("Mode", "slider"); channel.putString("Label", "Analog output");
        channel.putInt("Strength", 15); channel.putInt("SliderValue", 0);
        channel.put("Card", new ControlCardStyle(ControlCardStyle.Size.WIDE, 0x3399AA,
                ResourceLocation.withDefaultNamespace("redstone")).toTag());
        channels.add(channel); data.put("Channels", channels);
        List<Integer> sent = new ArrayList<>();
        try(var surface = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "slider_validation"))){
            for(boolean projected : new boolean[]{false, true}){
                var state = renderer.createScreenState(); sent.clear();
                surface.render(640, 360, 640, 360, graphics -> renderer.render(context(graphics, data, projected, sent), state));
                for(int strength = 0; strength <= 15; strength++){
                    double x = (51 + Math.min(135.99, 136 * strength / 15D)) * 2, y = 80 * 2;
                    var ctx = context(null, data, projected, sent);
                    boolean handled = projected || strength == 0 ? renderer.mouseClicked(ctx, state, x, y, 0)
                            : renderer.mouseDragged(ctx, state, x, y, 0, 1, 0);
                    if(!handled || sent.size() != strength + 1 || sent.getLast() != strength){
                        throw new AssertionError("Slider did not publish " + strength + " before release, projected=" + projected + ": " + sent);
                    }
                    if(projected && renderer.mouseDragged(ctx, state, x, y, 0, 0, 0)) throw new AssertionError("Projected click retained a drag");
                }
                if(!projected){
                    var ctx = context(null, data, false, sent);
                    renderer.mouseDragged(ctx, state, 1000, 160, 0, 0, 0);
                    renderer.mouseReleased(ctx, state, 1000, 160, 0);
                    if(sent.size() != 16) throw new AssertionError("Unchanged drag or release sent duplicate output: " + sent);
                }
            }
            var state = renderer.createScreenState();
            channel.putInt("SliderValue", 6);
            int original = renderHash(surface, data, state);
            channel.putInt("Strength", 0);
            if(renderHash(surface, data, state) != original) throw new AssertionError("Fixed output changed the displayed slider value");
            channel.putInt("SliderValue", 15);
            if(renderHash(surface, data, state) == original) throw new AssertionError("Live slider value did not change its rendering");
            var draft = new CompoundTag(); draft.putBoolean("Editing", true); draft.put("Card", channel.copy());
            state.loadDraft(draft);
            List<String> edits = new ArrayList<>();
            surface.render(640, 360, 640, 360, graphics -> renderer.render(context(graphics, data, false, sent, edits), state));
            renderer.mouseClicked(context(null, data, false, sent, edits), state, 255 * 2, 148 * 2, 0);
            if(!edits.isEmpty()) throw new AssertionError("Slider still exposes the fixed Output Signal editor");
            for(String mode : List.of("button", "toggle")){
                draft.getCompound("Card").putString("Mode", mode); state.loadDraft(draft);
                surface.render(640, 360, 640, 360, graphics -> renderer.render(context(graphics, data, false, sent, edits), state));
                renderer.mouseClicked(context(null, data, false, sent, edits), state, 255 * 2, 148 * 2, 0);
                if(edits.size() != 1 || state.saveDraft().getCompound("Card").getInt("Strength") != 7){
                    throw new AssertionError("Fixed output editor stopped working for " + mode);
                }
                edits.clear();
            }
        }
    }

    private static int renderHash(FramebufferGuiRenderer surface, CompoundTag data,
                                  com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState state){
        var renderer = TabletAppClientRegistry.renderer(DiagnosticTabletData.appId("redstone_link"));
        surface.render(640, 360, 640, 360, graphics -> renderer.render(context(graphics, data, false, new ArrayList<>()), state));
        RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(surface.texture()).getId());
        try(var image = new NativeImage(640, 360, false)){
            image.downloadTexture(0, false);
            int hash = 1;
            for(int y = 0; y < 360; y++) for(int x = 0; x < 640; x++) hash = 31 * hash + image.getPixelRGBA(x, y);
            return hash;
        }
    }

    private static TabletAppClientContext context(GuiGraphics graphics, CompoundTag data, boolean projected, List<Integer> sent){
        return context(graphics, data, projected, sent, new ArrayList<>());
    }

    private static TabletAppClientContext context(GuiGraphics graphics, CompoundTag data, boolean projected, List<Integer> sent, List<String> edits){
        var app = TabletAppRegistry.definition(DiagnosticTabletData.appId("redstone_link"));
        var tab = app.tabs().stream().filter(candidate -> "home".equals(candidate.id())).findFirst().orElseThrow();
        return new TabletAppClientContext(app, tab, data, graphics, Minecraft.getInstance().font, 0, 0, 640, 360,
                new TabletAppClientSurface(0, 0, 640, 360), -1, -1, (action, value) -> {
                    if(!"channel_slider".equals(action)) throw new AssertionError("Unexpected slider action: " + action);
                    sent.add(Integer.parseInt(value.substring(value.indexOf('|') + 1)));
                }, () -> {}, new TabletAppClientUi(){
                    @Override public boolean projected(){ return projected; }
                    @Override public void editText(String prompt, String initial, int maximum, java.util.function.Consumer<String> accept){
                        edits.add(prompt); accept.accept("7");
                    }
                });
    }
}
