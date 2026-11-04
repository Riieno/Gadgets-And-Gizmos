package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
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
            Files.writeString(Path.of(OUTPUT), "PASSED screen drag and projected click slider inputs for all strengths 0-15\n");
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
        channel.putInt("Strength", 0);
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
        }
    }

    private static TabletAppClientContext context(GuiGraphics graphics, CompoundTag data, boolean projected, List<Integer> sent){
        var app = TabletAppRegistry.definition(DiagnosticTabletData.appId("redstone_link"));
        var tab = app.tabs().stream().filter(candidate -> "home".equals(candidate.id())).findFirst().orElseThrow();
        return new TabletAppClientContext(app, tab, data, graphics, Minecraft.getInstance().font, 0, 0, 640, 360,
                new TabletAppClientSurface(0, 0, 640, 360), -1, -1, (action, value) -> {
                    if(!"channel_slider".equals(action)) throw new AssertionError("Unexpected slider action: " + action);
                    sent.add(Integer.parseInt(value.substring(value.indexOf('|') + 1)));
                }, () -> {}, new TabletAppClientUi(){
                    @Override public boolean projected(){ return projected; }
                });
    }
}
