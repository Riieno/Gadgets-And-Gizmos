package com.rieno.gadgetsandgizmos.content;

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseCapturePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseInputPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

// Exercise mouse routing and session targeting in the isolated native client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class MouseCaptureClientValidation{
    private static boolean complete;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        String output = System.getProperty("createthrusters.mouseCaptureValidation");
        if(output == null || complete || Minecraft.getInstance().getOverlay() != null) return;
        complete = true;
        String result;
        try{
            verify();
            result = "PASSED: mouse look release, capture, recapture and block/portable/lectern session routing";
        }catch(Throwable err){
            LogUtils.getLogger().error("Mouse capture client validation failed", err);
            result = "FAILED: " + err;
        }
        try{ Files.writeString(Path.of(output), result); }
        catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(result);
        Minecraft.getInstance().stop();
    }

    private static void verify() throws ReflectiveOperationException{
        var controller = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO,
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        Map<java.lang.reflect.Field, Object> prev = new LinkedHashMap<>();
        try{
            set(prev, "activeController", controller);
            set(prev, "activeControllerPos", BlockPos.ZERO);
            set(prev, "activeControllerSubLevelId", null);
            set(prev, "activePortable", false);
            set(prev, "activeLecternPos", null);
            set(prev, "gateUseInputUntilRelease", false);
            set(prev, "lastSentMouseX", 0D);
            set(prev, "lastSentMouseY", 0D);
            require(!AnalogueContraptionControllerClientHandler.onMouseMove(22, 11), "An uncaptured controller blocked mouse look");
            require(!AnalogueContraptionControllerClientHandler.onMouseButton(0, 1), "An uncaptured controller blocked mouse clicks");
            require(!AnalogueContraptionControllerClientHandler.onMouseScroll(0, 1), "An uncaptured controller blocked scrolling");
            var captured = Map.of("axis", "mouse_x", "button", "left_click");
            var block = new AdvancedControllerMouseCapturePayload(AdvancedControllerMouseInputPayload.Target.BLOCK,
                    BlockPos.ZERO, null, InteractionHand.MAIN_HAND, captured);
            AnalogueContraptionControllerClientHandler.applyMouseCapture(block);
            require(controller.getCapturedMouseInputs().contains("mouse_x"), "Block capture state was not applied");
            set(prev, "lastSentMouseX", .2D);
            require(AnalogueContraptionControllerClientHandler.onMouseMove(22, 0), "Captured mouse movement did not lock look");
            controller.applyClientMouseInput("left_click", 1, true);
            AnalogueContraptionControllerClientHandler.applyMouseCapture(block.withRequests(Map.of()));
            require(controller.getMouseInputValue("mouse_x") == .2D && controller.isMouseInputActive("left_click"), "Capture release cleared held values");
            require(!AnalogueContraptionControllerClientHandler.onMouseMove(22, 11), "Releasing capture did not restore look");
            require(controller.getMouseInputValue("mouse_x") == .2D, "Normal mouse look changed the held axis");
            var wrong = new AdvancedControllerMouseCapturePayload(block.target(), new BlockPos(2, 0, 0), null, block.hand(), captured);
            AnalogueContraptionControllerClientHandler.applyMouseCapture(wrong);
            require(controller.getCapturedMouseInputs().isEmpty(), "A different controller captured the session");
            set(prev, "activeControllerPos", null);
            set(prev, "activePortable", true);
            set(prev, "activePortableHand", InteractionHand.OFF_HAND);
            set(prev, "activePortableController", controller);
            var portable = new AdvancedControllerMouseCapturePayload(AdvancedControllerMouseInputPayload.Target.PORTABLE,
                    BlockPos.ZERO, null, InteractionHand.OFF_HAND, captured);
            AnalogueContraptionControllerClientHandler.applyMouseCapture(portable);
            require(controller.getCapturedMouseInputs().contains("mouse_x"), "Portable capture was not applied");
            AnalogueContraptionControllerClientHandler.applyMouseCapture(portable.withRequests(Map.of()));
            set(prev, "activePortable", false);
            set(prev, "activeLecternPos", BlockPos.ZERO);
            set(prev, "activeLecternController", controller);
            var lectern = new AdvancedControllerMouseCapturePayload(AdvancedControllerMouseInputPayload.Target.LECTERN,
                    BlockPos.ZERO, null, InteractionHand.MAIN_HAND, captured);
            AnalogueContraptionControllerClientHandler.applyMouseCapture(lectern);
            require(controller.getCapturedMouseInputs().contains("mouse_x"), "Lectern recapture was not applied");
            AnalogueContraptionControllerClientHandler.applyMouseCapture(lectern.withRequests(Map.of()));
        }finally{
            for(var entry : prev.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }

    private static void set(Map<java.lang.reflect.Field, Object> prev, String name, Object val) throws ReflectiveOperationException{
        var field = AnalogueContraptionControllerClientHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        if(!prev.containsKey(field)) prev.put(field, field.get(null));
        field.set(null, val);
    }

    private static void require(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}
