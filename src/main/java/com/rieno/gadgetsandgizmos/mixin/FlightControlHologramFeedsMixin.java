package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Apply the shared feed policy while Flight Control draws its native miniature scene
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.AttitudeDisplaySubLevelWorldRenderer", remap = false)
public abstract class FlightControlHologramFeedsMixin{
    @WrapMethod(method = "render")
    private static void createThrusters$skipMiniatureFeeds(RenderLevelStageEvent event, Operation<Void> original){
        try(var scope = HologramRenderScope.enter()){
            original.call(event);
        }
    }
}
