package com.rieno.gadgetsandgizmos.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.simulated.DockingConnectorBindingAccess;
import com.rieno.gadgetsandgizmos.neoforge.client.ShipDockingConnectorRenderer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Draw bound Simulated docking connectors with the Ship Dock texture set
@Mixin(value = DockingConnectorRenderer.class, remap = false)
public abstract class DockingConnectorRendererMixin {
    @Inject(
            method = "renderSafe(Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"),
            cancellable = true)
    private void createthrusters$renderBoundConnector(
            DockingConnectorBlockEntity connector,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay,
            CallbackInfo ci
    ) {
        if (!(connector instanceof DockingConnectorBindingAccess access)
                || !access.createthrusters$usesBoundTexture()) return;
        ShipDockingConnectorRenderer.render(connector, partialTicks, poseStack, buffer, light);
        ci.cancel();
    }
}