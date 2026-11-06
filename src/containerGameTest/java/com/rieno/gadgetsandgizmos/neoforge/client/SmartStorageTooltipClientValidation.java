package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Verify every requested tooltip through Minecraft's language parser and registered item hooks
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class SmartStorageTooltipClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.smartStorageTooltipValidation");
    // Run once after client resources finish loading
    private static boolean complete, hidden;

    // Validate and render all six translated sets without entering a saved world
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        complete = true;
        Path out = Path.of(OUTPUT);
        String prev = mc.getLanguageManager().getSelected();
        try{
            for(String lang : List.of("en_us", "de_de", "es_es", "fr_fr", "ja_jp", "ru_ru")){
                mc.getLanguageManager().setSelected(lang);
                mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
                validate(mc, lang, out);
            }
            Files.writeString(out.resolve("result.txt"), "PASSED 42 translated summaries, description prompts and item tooltip hooks across six languages\n");
        }catch(Throwable err){
            err.printStackTrace();
            try{ Files.writeString(out.resolve("result.txt"), "FAILED " + err); }
            catch(Exception writeErr){ throw new RuntimeException(writeErr); }
        }finally{
            mc.getLanguageManager().setSelected(prev);
            mc.stop();
        }
    }

    // Check each item's actual description and retain a rendered sheet for inspection
    private static void validate(Minecraft mc, String lang, Path out) throws Exception{
        Item[] items = {CTItems.WORKER_POD.get(), CTItems.SCISSOR_PISTON.get(), CTItems.SMART_TANK.get(),
                CTItems.SMART_VAULT.get(), CTItems.SMART_BATTERY.get(), CTItems.SHIPPING_SCHEDULE.get(), CTItems.SHIP_DOCK.get()};
        var descriptions = new ArrayList<ItemDescription>();
        for(Item item : items){
            String key = item.getDescriptionId() + ".tooltip.summary";
            if(!I18n.exists(key)) throw new AssertionError(lang + " is missing " + key);
            var description = ItemDescription.create(item, FontHelper.Palette.GRAY_AND_WHITE);
            if(description == null || description.linesOnShift().size() <= description.lines().size())
                throw new AssertionError(lang + " has no expanded description for " + key);
            var tooltip = new ArrayList<Component>();
            item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
            for(Component line : description.getCurrentLines()){
                if(!line.getString().isBlank() && tooltip.stream().filter(line::equals).count() != 1)
                    throw new AssertionError(lang + " did not append exactly one description for " + key);
            }
            descriptions.add(description);
        }
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "smart_storage_tooltips"))){
            renderer.render(800, 850, 800, 850, graphics -> {
                graphics.fill(0, 0, 800, 850, 0xFF10191D);
                int y = 12;
                for(int idx = 0; idx < items.length; idx++){
                    graphics.drawString(mc.font, items[idx].getDescription(), 12, y, 0xFFFFCC66, false);
                    y += 14;
                    for(Component line : descriptions.get(idx).linesOnShift()){
                        graphics.drawString(mc.font, line, 12, y, 0xFFFFFFFF, false);
                        y += 11;
                    }
                    y += 14;
                }
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var screenshot = new NativeImage(800, 850, false)){
                screenshot.downloadTexture(0, false);
                screenshot.flipY();
                screenshot.writeToFile(out.resolve(lang + ".png"));
            }
        }
    }
}
