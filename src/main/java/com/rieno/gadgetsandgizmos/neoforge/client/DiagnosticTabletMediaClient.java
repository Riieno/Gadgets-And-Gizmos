package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletMediaApp;
import com.rieno.gadgetsandgizmos.lib.client.sound.MediaPlaybackEngine;
import com.rieno.gadgetsandgizmos.lib.media.MediaSources;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Play portable tablets privately and receive placed sources through the library observer
public final class DiagnosticTabletMediaClient{
    private static final MediaPlaybackEngine ENGINE = new MediaPlaybackEngine();
    private DiagnosticTabletMediaClient(){}

    public static void register(){ MediaSources.setObserver(ENGINE::observe); }

    public static void onClientTick(ClientTickEvent.Post evt){
        var mc = Minecraft.getInstance();
        if(mc.player != null && mc.level != null){
            for(int idx = 0; idx < 9; idx++) observe(mc.player.getInventory().getItem(idx));
            observe(mc.player.getOffhandItem());
        }
        ENGINE.tick();
    }

    private static void observe(ItemStack stack){
        if(!(stack.getItem() instanceof DiagnosticTabletItem)) return;
        var state = DiagnosticTabletMediaApp.readPlayback(stack);
        if(state.discId() == null) return;
        MediaSources.observe(DiagnosticTabletData.read(stack).tabletId(), state, Vec3.ZERO, true);
    }
}
