package com.rieno.gadgetsandgizmos.content;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

// Exercise native Create shaft instances after client models and a level have loaded
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class VectorBearingShaftClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.bearingShaftValidation");
    private static boolean hidden, complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; }
        if(mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        complete = true;
        String result;
        try{
            HybridHologramValidation.checkVectorBearingShaft();
            result = "PASSED 6000 idle refreshes, all 36 facing changes, 0/16/-64/256 RPM geometry, fallback rendering and instance cleanup";
        }catch(Throwable err){
            err.printStackTrace();
            result = "FAILED " + err;
        }
        try{ Files.writeString(Path.of(OUTPUT), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ mc.stop(); }
    }
}
