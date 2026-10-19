package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewInteractionClient;
import com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverClientGripHandler;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Adapt native addon and Simulated input handlers to authenticated camera interaction
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CameraInteractionClient implements ViewInteractionClient.InputHandler{
    private CameraInteractionClient(){}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(() -> {
            ViewInteractionClient.register(new CameraInteractionClient());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CameraInteractionClient::tick);
        });
    }

    // Surface slider dragging reuses the server's widget hit test without repeating button clicks
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var mc = Minecraft.getInstance();
        if(!ViewInteractionClient.held() || mc.screen != null || mc.level == null || mc.player == null
                || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        if(!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity clicked)) return;
        var display = clicked.networkRoot();
        if(display == null || !"widgets".equals(display.displayFrame().getString("Mode"))) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayWidgetInputPayload(
                com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget.of(display.getBlockPos(), SimulatedHelper.getContainingSubLevelId(display)), "", "", true));
    }

    @Override public boolean use(boolean down){
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null) return false;
        var cursor = ViewInteractionContext.cursor(mc.player);
        if(down && cursor != null){
            var ref = cursor.target().reference();
            var lever = SimulatedHelper.findBlockEntity(mc.level, ref.subLevelId(), ref.blockPos(), ThrottleLeverBlockEntity.class);
            if(lever != null) ThrottleLeverClientGripHandler.tickGrip(lever);
            var display = SimulatedHelper.findBlockEntity(mc.level, ref.subLevelId(), ref.blockPos(), com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity.class);
            if(display != null) AccHologramWidgetClient.record(display);
        }
        var key = mc.options.keyUse.getKey();
        var input = key.getType() == InputConstants.Type.MOUSE ? InteractCallback.Input.mouse(key.getValue())
                : key.getType() == InputConstants.Type.SCANCODE ? InteractCallback.Input.key(-1, key.getValue())
                : InteractCallback.Input.key(key.getValue(), 0);
        return SimulatedCommonClientEvents.onBeforeMouseInput(input, 0, down ? 1 : 0).cancelled();
    }
    @Override public boolean captured(){
        var mc = Minecraft.getInstance();
        if(mc.level != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity display
                && display.isSurfaceSlider(hit)) return true;
        return HoldInteractionManager.isActive() || AnalogueJoystickClientHandler.isDragging()
                || AccHologramWidgetClient.isDragging() || AccDisplayGuiProjection.isDragging()
                || AnalogueContraptionControllerClientHandler.physicalAdvancedController() != null;
    }
    @Override public boolean scroll(double horizontal, double vertical){
        return AnalogueContraptionControllerClientHandler.onMouseScroll(horizontal, vertical)
                || SimulatedCommonClientEvents.onMouseScroll(horizontal, vertical).cancelled();
    }
    @Override public boolean key(int key, int scanCode, int action, int modifiers){
        if(AnalogueContraptionControllerClientHandler.onKeyInput(key, scanCode, action)) return true;
        if(SimulatedCommonClientEvents.onBeforeMouseInput(InteractCallback.Input.key(key, scanCode), modifiers, action).cancelled()) return true;
        SimulatedCommonClientEvents.onAfterKeyPress(key, scanCode, action, modifiers);
        return false;
    }
    @Override public boolean mouse(int button, int action, int modifiers){
        if(AnalogueContraptionControllerClientHandler.onMouseButton(button, action)) return true;
        if(SimulatedCommonClientEvents.onBeforeMouseInput(InteractCallback.Input.mouse(button), modifiers, action).cancelled()) return true;
        SimulatedCommonClientEvents.onAfterMouseInput(button, action, modifiers);
        return false;
    }
    @Override public void release(){
        AnalogueJoystickClientHandler.releaseUse();
        if(!HoldInteractionManager.isActive()) return;
        for(var handler : dev.simulated_team.simulated.index.SimClickInteractions.CLICK_INTERACTION_ENTRIES){
            if(handler instanceof dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction hold && hold.isActive()) hold.release();
        }
        HoldInteractionManager.stop();
    }
}
