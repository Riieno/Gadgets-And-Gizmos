package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Build and identify the pre-equipped focused Thruster item variant
public final class IonThrusterStacks {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String INVENTORY_TAG = "ThrusterInventory";
    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_ID_TAG = "id";
    private static final String ITEM_COUNT_TAG = "count";
    private static final String ION_THRUSTER_NAME = "item.createthrusters.ion_thruster";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ion thruster stack utility
    private IonThrusterStacks() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create one Thruster with a Lens installed in its Lens slot
    public static ItemStack create() {
        if (CTItems.THRUSTER == null || CTItems.THRUSTER_LENSE == null || CTBlockEntities.THRUSTER == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(CTItems.THRUSTER.get());
        BlockItem.setBlockEntityData(stack, CTBlockEntities.THRUSTER.get(), focusedInventoryData());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(ION_THRUSTER_NAME)
                .withStyle(style -> style.withItalic(false)));
        return stack;
    }

    // Check whether this stack is an ion thruster with its Lens installed
    public static boolean isIonThruster(ItemStack stack) {
        if (stack.isEmpty() || CTItems.THRUSTER == null || !stack.is(CTItems.THRUSTER.get())) {
            return false;
        }

        CustomData blockEntityData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
        CompoundTag inventory = blockEntityData.copyTag().getCompound(INVENTORY_TAG);
        ListTag items = inventory.getList(ITEMS_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag item = items.getCompound(index);
            if (item.getInt(SLOT_TAG) == ThrusterBlockEntity.SLOT_LENS
                    && "createthrusters:thruster_lense".equals(item.getString(ITEM_ID_TAG))
                    && item.getInt(ITEM_COUNT_TAG) > 0) {
                return true;
            }
        }
        return false;
    }

    // Check whether this is a fresh Thruster that can be upgraded through crafting
    public static boolean isUnmodifiedThruster(ItemStack stack) {
        if (stack.isEmpty() || CTItems.THRUSTER == null || !stack.is(CTItems.THRUSTER.get())) {
            return false;
        }
        return stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).isEmpty();
    }

    // Create the exact block-entity payload read by the Thruster block entity
    private static CompoundTag focusedInventoryData() {
        CompoundTag lens = new CompoundTag();
        lens.putInt(SLOT_TAG, ThrusterBlockEntity.SLOT_LENS);
        lens.putString(ITEM_ID_TAG, "createthrusters:thruster_lense");
        lens.putInt(ITEM_COUNT_TAG, 1);

        ListTag items = new ListTag();
        items.add(lens);

        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", ThrusterBlockEntity.SLOT_SOLID_FUEL + 1);
        inventory.put(ITEMS_TAG, items);

        CompoundTag blockEntityData = new CompoundTag();
        blockEntityData.put(INVENTORY_TAG, inventory);
        return blockEntityData;
    }
}
