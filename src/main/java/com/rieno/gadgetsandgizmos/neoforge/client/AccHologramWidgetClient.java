package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayWidgetGeometry;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayWidgetInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;
import java.util.WeakHashMap;

// Floating widgets have no block collider; pick the visible projected controls directly
public final class AccHologramWidgetClient{
    private static final Map<AccDisplayBlockEntity, Long> VISIBLE = new WeakHashMap<>();
    private static Selection pressed;
    private AccHologramWidgetClient(){}

    public static void record(AccDisplayBlockEntity display){ VISIBLE.put(display, System.nanoTime()); }
    public static boolean isDragging(){ return pressed != null && "slider".equals(pressed.hit().element().getString("Type")); }
    public static void clear(){ VISIBLE.clear(); pressed = null; }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event){
        if(!event.isUseItem()) return;
        var minecraft = Minecraft.getInstance();
        if(minecraft.screen != null || minecraft.player == null) return;
        Selection selected = pick();
        if(selected == null) return;
        if(pressed == null){ pressed = selected; send(selected, false); }
        event.setSwingHand(false); event.setCanceled(true);
    }

    public static void onClientTick(ClientTickEvent.Pre event){
        var minecraft = Minecraft.getInstance();
        if(minecraft.level == null){ clear(); return; }
        if(minecraft.screen != null || minecraft.player == null || !minecraft.options.keyUse.isDown()){
            pressed = null; return;
        }
        if(pressed == null || !"slider".equals(pressed.hit().element().getString("Type"))) return;
        Selection current = pick();
        if(current != null && current.display() == pressed.display()
                && current.hit().node().id().equals(pressed.hit().node().id())
                && current.hit().element().getString("InteractionId").equals(pressed.hit().element().getString("InteractionId"))){
            send(current, true);
        }
    }

    private static Selection pick(){
        var minecraft = Minecraft.getInstance();
        if(minecraft.player == null || minecraft.level == null) return null;
        var eye = minecraft.player.getEyePosition();
        var view = minecraft.player.getViewVector(1);
        double reach = minecraft.player.blockInteractionRange();
        var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.cursor(minecraft.player);
        if(cursor != null){
            var target = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.pick(minecraft.player);
            if(target != null) reach = Math.min(reach, target.distance() + 0.05);
        }else if(minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS){
            reach = Math.min(reach, eye.distanceTo(minecraft.hitResult.getLocation()) + 0.05);
        }
        long now = System.nanoTime();
        VISIBLE.entrySet().removeIf(entry -> entry.getKey().isRemoved()
                || now - entry.getValue() > 1_000_000_000L);
        Selection best = null;
        for(var display : VISIBLE.keySet()){
            if(display.getLevel() == null || !display.getLevel().isClientSide
                    || !display.getLevel().dimension().equals(minecraft.level.dimension())) continue;
            var hit = AccDisplayWidgetGeometry.pick(display, eye, view, reach);
            if(hit != null && (best == null || hit.distance() < best.hit().distance())) best = new Selection(display, hit);
        }
        return best;
    }

    private static void send(Selection selection, boolean drag){
        var display = selection.display();
        PacketDistributor.sendToServer(new AccDisplayWidgetInputPayload(
                MenuConfigTarget.of(display.getBlockPos(), SimulatedHelper.getContainingSubLevelId(display)),
                selection.hit().node().id(), selection.hit().element().getString("InteractionId"), drag));
    }
    private record Selection(AccDisplayBlockEntity display, AccDisplayWidgetGeometry.Hit hit){}
}
