package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Draw the Contraption Network Linker Slot
public final class ContraptionNetworkLinkerSlotRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation LINKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/item/contraption_network_linker_linked.png");
    private static final ResourceLocation UNLINKED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/item/contraption_network_linker_unlinked.png");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker slot
    private ContraptionNetworkLinkerSlotRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the controller slot
    public static boolean renderControllerSlot(GuiGraphics graphics, Slot slot) {
        if (!isControllerLinkerSlot(slot)) {
            return false;
        }

        return renderSlotTexture(graphics, slot);
    }

    // Draw the slot texture
    private static boolean renderSlotTexture(GuiGraphics graphics, Slot slot) {
        if (slot == null) {
            return false;
        }

        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ContraptionNetworkLinkerItem)) {
            return false;
        }

        ResourceLocation texture = ContraptionNetworkLinkerItemRenderer.linkedBlockCount(stack) > 0
                ? LINKED_TEXTURE
                : UNLINKED_TEXTURE;
        graphics.blit(texture, slot.x, slot.y, 16, 16, 0.0f, 0.0f, 32, 32, 32, 32);
        return true;
    }

    // Check if this is a controller linker slot
    private static boolean isControllerLinkerSlot(Slot slot) {
        return slot != null
                && (slot.x == AnalogueContraptionControllerMenu.LINKER_SLOT_X
                && slot.y == AnalogueContraptionControllerMenu.LINKER_SLOT_Y
                || slot.x == AdvancedContraptionControllerMenu.LINKER_SLOT_X
                && slot.y == AdvancedContraptionControllerMenu.LINKER_SLOT_Y);
    }
}
