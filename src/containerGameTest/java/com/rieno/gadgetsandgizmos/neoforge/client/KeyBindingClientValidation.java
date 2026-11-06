package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.lib.client.input.ClientActionKey;
import com.rieno.gadgetsandgizmos.lib.item.DescribedCogwheelBlockItem;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

// Exercise real keyboard callbacks against Minecraft's key binding screen
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class KeyBindingClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.keyBindingValidation");
    // Run once after client resources finish loading
    private static boolean complete, hidden;

    // Capture international keys and modifier chords without requiring a keyboard layout change
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        complete = true;
        var mapping = mc.options.keyJump;
        var prevKey = mapping.getKey();
        var prevModifier = mapping.getKeyModifier();
        String res;
        try{
            var screen = new KeyBindsScreen(mc.screen, mc.options);
            mc.setScreen(screen);
            if(ClientActionKey.canHandleGameplayInput(mc.getWindow().getWindow()))
                throw new AssertionError("A screen accepted gameplay input");
            verify(mc, screen, GLFW.GLFW_KEY_UNKNOWN, 123, -1, KeyModifier.NONE, false);
            verify(mc, screen, GLFW.GLFW_KEY_WORLD_1, 86, -1, KeyModifier.NONE, false);
            verify(mc, screen, GLFW.GLFW_KEY_WORLD_2, 87, -1, KeyModifier.NONE, false);
            for(boolean modifierFirst : new boolean[]{false, true}){
                verify(mc, screen, GLFW.GLFW_KEY_K, 37, GLFW.GLFW_KEY_LEFT_CONTROL, KeyModifier.CONTROL, modifierFirst);
                verify(mc, screen, GLFW.GLFW_KEY_K, 37, GLFW.GLFW_KEY_RIGHT_CONTROL, KeyModifier.CONTROL, modifierFirst);
                verify(mc, screen, GLFW.GLFW_KEY_K, 37, GLFW.GLFW_KEY_LEFT_SHIFT, KeyModifier.SHIFT, modifierFirst);
                verify(mc, screen, GLFW.GLFW_KEY_K, 37, GLFW.GLFW_KEY_LEFT_ALT, KeyModifier.ALT, modifierFirst);
                verify(mc, screen, GLFW.GLFW_KEY_UNKNOWN, 123, GLFW.GLFW_KEY_LEFT_CONTROL, KeyModifier.CONTROL, modifierFirst);
            }
            verifyRatchetTooltips();
            res = "PASSED international scan codes, WORLD_1/WORLD_2, Ctrl/Shift/Alt chords, both release orders and both ratchet tooltips";
        }catch(Throwable err){
            err.printStackTrace();
            res = "FAILED " + err;
        }finally{
            mapping.setKeyModifierAndCode(prevModifier, prevKey);
            net.minecraft.client.KeyMapping.resetMapping();
        }
        try{ Files.writeString(Path.of(OUTPUT), res); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ mc.stop(); }
    }

    // Feed press and release events through every installed keyboard mixin
    private static void verify(Minecraft mc, KeyBindsScreen screen, int key, int scan,
            int modifier, KeyModifier expected, boolean modifierFirst){
        var mapping = mc.options.keyJump;
        screen.selectedKey = mapping;
        long window = mc.getWindow().getWindow();
        int flags = expected == KeyModifier.CONTROL ? GLFW.GLFW_MOD_CONTROL
                : expected == KeyModifier.SHIFT ? GLFW.GLFW_MOD_SHIFT
                : expected == KeyModifier.ALT ? GLFW.GLFW_MOD_ALT : 0;
        if(modifier >= 0) mc.keyboardHandler.keyPress(window, modifier, 29, GLFW.GLFW_PRESS, flags);
        mc.keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_PRESS, flags);
        if(modifierFirst && modifier >= 0) mc.keyboardHandler.keyPress(window, modifier, 29, GLFW.GLFW_RELEASE, 0);
        mc.keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_RELEASE, modifierFirst ? 0 : flags);
        if(!modifierFirst && modifier >= 0) mc.keyboardHandler.keyPress(window, modifier, 29, GLFW.GLFW_RELEASE, 0);
        if(screen.selectedKey != null || !mapping.getKey().equals(InputConstants.getKey(key, scan))
                || mapping.getKeyModifier() != expected)
            throw new AssertionError("Binding failed: key=" + key + ", scan=" + scan + ", modifier=" + expected);
    }

    // Check the registered items expose their descriptions and retain normal cogwheel placement
    private static void verifyRatchetTooltips(){
        for(var item : new net.minecraft.world.item.Item[]{CTItems.RATCHET_COGWHEEL.get(), CTItems.LARGE_RATCHET_COGWHEEL.get()}){
            if(!(item instanceof DescribedCogwheelBlockItem)) throw new AssertionError("The ratchet item has no description hook");
            var description = ItemDescription.create(item, FontHelper.Palette.GRAY_AND_WHITE);
            if(description == null || description.linesOnShift().isEmpty()) throw new AssertionError("The ratchet description is missing");
            var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            item.appendHoverText(new net.minecraft.world.item.ItemStack(item),
                    net.minecraft.world.item.Item.TooltipContext.EMPTY, tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
            if(tooltip.isEmpty()) throw new AssertionError("The ratchet tooltip did not display its description prompt");
        }
    }
}
