package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

// Sync Thruster settings
public class ThrusterMenu extends AbstractContainerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int THRUSTER_SLOT_SECTION_Y = 77;
    public static final int SLOT_UPGRADE_X    = 188;
    public static final int SLOT_UPGRADE_Y    = 81;
    public static final int SLOT_LIST_X       = 188;
    public static final int SLOT_LIST_Y       = 99;
    public static final int SLOT_LENS_X       = 188;
    public static final int SLOT_LENS_Y       = 117;
    public static final int SLOT_SOLID_FUEL_X = 216;
    public static final int SLOT_SOLID_FUEL_Y = 120;
    public static final int PLAYER_SLOTS_X    = 47;
    public static final int PLAYER_SLOTS_Y    = 164;

    public static final int PLAYER_SLOT_START = 4;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Content holder
    public final ThrusterBlockEntity contentHolder;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster menu
    public ThrusterMenu(int id, Inventory playerInventory, ThrusterBlockEntity blockEntity) {
        super(CTMenuTypes.THRUSTER.get(), id);
        this.contentHolder = blockEntity;
        addSlots(playerInventory);
    }

    // Initialize the thruster menu
    public ThrusterMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(CTMenuTypes.THRUSTER.get(), id);
        BlockEntity be = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        this.contentHolder = (be instanceof ThrusterBlockEntity t) ? t : null;
        addSlots(playerInventory);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the slots
    private void addSlots(Inventory playerInventory) {
        if (contentHolder != null) {
            addSlot(new SlotItemHandler(contentHolder.getItemInventory(), ThrusterBlockEntity.SLOT_UPGRADE,
                    SLOT_UPGRADE_X, SLOT_UPGRADE_Y));
            addSlot(new SlotItemHandler(contentHolder.getItemInventory(), ThrusterBlockEntity.SLOT_LIST,
                SLOT_LIST_X, SLOT_LIST_Y));
            addSlot(new SlotItemHandler(contentHolder.getItemInventory(), ThrusterBlockEntity.SLOT_LENS,
                SLOT_LENS_X, SLOT_LENS_Y));
            addSlot(new SlotItemHandler(contentHolder.getItemInventory(), ThrusterBlockEntity.SLOT_SOLID_FUEL,
                SLOT_SOLID_FUEL_X, SLOT_SOLID_FUEL_Y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot((Container) playerInventory,
                        col + row * 9 + 9,
                        PLAYER_SLOTS_X + col * 18,
                        PLAYER_SLOTS_Y + row * 18));
            }
        }

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot((Container) playerInventory,
                    hotbar,
                    PLAYER_SLOTS_X + hotbar * 18,
                    PLAYER_SLOTS_Y + 58));
        }
    }

    // Move the stack quickly
    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (contentHolder == null) return ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (idx < PLAYER_SLOT_START) {

            if (!moveItemStackTo(stack, PLAYER_SLOT_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {

            boolean moved = false;
            for (int i = 0; i < PLAYER_SLOT_START; i++) {
                if (moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) {

                if (idx < PLAYER_SLOT_START + 27) {
                    if (!moveItemStackTo(stack, PLAYER_SLOT_START + 27, slots.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_START + 27, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // Check if the still is valid
    @Override
    public boolean stillValid(Player player) {
        if (contentHolder == null) return false;
        return contentHolder.getLevel() != null
                && player.distanceToSqr(contentHolder.getBlockPos().getX() + 0.5,
                        contentHolder.getBlockPos().getY() + 0.5,
                        contentHolder.getBlockPos().getZ() + 0.5) < 64.0;
    }
}
