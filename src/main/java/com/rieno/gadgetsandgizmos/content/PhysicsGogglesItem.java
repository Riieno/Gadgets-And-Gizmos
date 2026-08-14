package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.equipment.armor.BaseArmorItem;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

// Show physics overlays while worn and explain their controls in the tooltip
public class PhysicsGogglesItem extends BaseArmorItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ArmorItem.Type TYPE = ArmorItem.Type.HELMET;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters", "physics_goggles");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics goggles item
    public PhysicsGogglesItem(Item.Properties properties) {
        super(ArmorMaterials.LEATHER, TYPE, properties, TEXTURE);
        GogglesItem.addIsWearingPredicate(player ->
                isFunctionalGoggles(player.getItemBySlot(EquipmentSlot.HEAD)));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CTTooltipHelper.addCreateDescription(this, tooltip);
        String pairLabel = linkedPairLabel(stack);
        if (!pairLabel.isBlank()) {
            tooltip.add(Component.literal("Linked as: " + pairLabel)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    // Update the inventory
    @Override
    public void inventoryTick(
            ItemStack stack, Level level, Entity entity, int slotId, boolean selected
    ) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        updateLinkedName(stack, level);
    }

    // Update the linked name
    static void updateLinkedName(ItemStack stack, Level level) {
        if (level == null || level.isClientSide || !stack.has(DataComponents.CUSTOM_NAME)) {
            return;
        }
        String pairLabel = linkedPairLabel(stack);
        if (pairLabel.isBlank()) {
            return;
        }
        String currentName = stack.getHoverName().getString();
        String normalizedName = AnalogueContraptionControllerMenu.relinkedGogglesName(
                currentName,
                stack.getItem().getName(stack).getString(),
                true,
                pairLabel,
                pairLabel);
        if (!currentName.equals(normalizedName)) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(normalizedName)
                            .setStyle(Style.EMPTY.withItalic(false)));
        }
    }

    // Check if this is functional goggles
    public static boolean isFunctionalGoggles(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return com.rieno.gadgetsandgizmos.registry.CTItems.PHYSICS_GOGGLES != null
                && stack.is(com.rieno.gadgetsandgizmos.registry.CTItems.PHYSICS_GOGGLES.get());
    }

    // Get the linked pair label
    static String linkedPairLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag binding = stack.getOrDefault(
                        DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .getUnsafe()
                .getCompound(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
        return linkedPairLabelFromBinding(binding);
    }

    // Get the linked pair label from binding
    static String linkedPairLabelFromBinding(CompoundTag binding) {
        if (binding == null) {
            return "";
        }
        if (!binding.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)) {
            return "";
        }
        String label = binding.getString(
                AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_LABEL).trim();
        return label.isBlank()
                ? binding.getUUID(
                        AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID).toString()
                : label;
    }
}
