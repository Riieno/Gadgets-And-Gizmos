package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.CustomRenderedArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Wear the pilot cap as leather armor and draw its model with Create's fitted hat layer
public class PilotCapItem extends PhysicsGogglesItem implements CustomRenderedArmorItem {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "pilot_cap");

    // Initialize the pilot cap item
    public PilotCapItem(Item.Properties properties) {
        super(properties, TEXTURE);
    }

    // The fitted hat layer draws this armor piece
    @Override
    public void renderArmorPiece(HumanoidArmorLayer<?, ?, ?> layer, PoseStack poseStack,
                                 MultiBufferSource buffers, LivingEntity wearer, EquipmentSlot slot,
                                 int light, HumanoidModel<?> model, ItemStack stack) {
    }
}
