package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem;
import com.rieno.gadgetsandgizmos.lib.client.render.HeadOffsetArmorModel;
import com.rieno.gadgetsandgizmos.lib.client.ui.EquipmentSlotScrollHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGogglesHeightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;

// Adjust equipped goggles from inventory and render every wearer's saved height
public final class PhysicsGogglesHeightClient{
    private PhysicsGogglesHeightClient(){}

    // Let the server own the worn stack so inventory and equipment sync agree
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre evt){
        var player = Minecraft.getInstance().player;
        EquipmentSlotScrollHandler.handle(evt, player, EquipmentSlot.HEAD,
                PhysicsGogglesItem::canAdjustHeight,
                direction -> PacketDistributor.sendToServer(
                        new PhysicsGogglesHeightPayload(player.containerMenu.containerId, direction)));
    }

    // Keep normal armor textures, tint and glint while moving the rendered goggles
    public static IClientItemExtensions armorExtensions(){
        return new IClientItemExtensions(){
            @Override
            public Model getGenericArmorModel(LivingEntity wearer, ItemStack stack, EquipmentSlot slot,
                                              HumanoidModel<?> original){
                if(slot != EquipmentSlot.HEAD) return original;
                float offset = PhysicsGogglesItem.HEIGHT_OFFSET.offsetPixels(stack);
                return offset == 0 ? original : new HeadOffsetArmorModel(original, offset);
            }
        };
    }
}
