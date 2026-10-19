package com.rieno.gadgetsandgizmos.compat.curios.client;

import com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem;
import com.rieno.gadgetsandgizmos.lib.client.render.AccessoryHeadwearRenderer;
import com.rieno.gadgetsandgizmos.neoforge.client.CTPartialModels;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.resources.ResourceLocation;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

// Register addon headwear only when the optional Curios client is present
public final class CuriosHeadwearCompat{
    private CuriosHeadwearCompat(){}

    public static void register(){
        CuriosRendererRegistry.register(CTItems.PHYSICS_GOGGLES.get(), () -> new Renderer(AccessoryHeadwearRenderer.armor(
                ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/models/armor/physics_goggles_layer_1.png"),
                PhysicsGogglesItem.HEIGHT_OFFSET::offsetPixels)));
        CuriosRendererRegistry.register(CTItems.PILOT_CAP.get(), () -> new Renderer(AccessoryHeadwearRenderer.hat(
                () -> CTPartialModels.PILOT_CAP, PhysicsGogglesItem.HEIGHT_OFFSET::offsetPixels)));
    }

    private record Renderer(AccessoryHeadwearRenderer renderer) implements top.theillusivec4.curios.api.client.ICurioRenderer{
        @Override
        public <T extends net.minecraft.world.entity.LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> void render(
                net.minecraft.world.item.ItemStack stack, top.theillusivec4.curios.api.SlotContext ctx,
                com.mojang.blaze3d.vertex.PoseStack poses, net.minecraft.client.renderer.entity.RenderLayerParent<T, M> parent,
                net.minecraft.client.renderer.MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTick, float age, float yaw, float pitch){
            if(ctx.visible()) renderer.render(stack, ctx.entity(), parent.getModel(), poses, buffers, light);
        }
    }
}
