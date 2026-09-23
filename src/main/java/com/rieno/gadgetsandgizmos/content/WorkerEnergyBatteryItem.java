package com.rieno.gadgetsandgizmos.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

// Store recoverable worker FE in a disposable battery
public class WorkerEnergyBatteryItem extends Item {
    public static final int MAX_ENERGY = 100_000;
    private static final String ENERGY_TAG = "WorkerEnergy";

    // Initialize the worker energy battery
    public WorkerEnergyBatteryItem(Properties properties) {
        super(properties);
    }

    // Create one battery with the requested bounded charge
    public static ItemStack charged(Item item, long energy) {
        ItemStack stack = new ItemStack(item);
        setEnergy(stack, (int) Math.min(MAX_ENERGY, Math.max(0L, energy)));
        return stack;
    }

    // Get the FE stored in one battery item
    public static int energy(ItemStack stack) {
        if (!isInternal(stack)) return 0;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(ENERGY_TAG)) return MAX_ENERGY;
        return Math.max(0, Math.min(MAX_ENERGY, data.copyTag().getInt(ENERGY_TAG)));
    }

    // Set the FE stored in one battery item
    public static void setEnergy(ItemStack stack, int energy) {
        if (!isInternal(stack)) return;
        int bounded = Math.max(0, Math.min(MAX_ENERGY, energy));
        if (bounded == MAX_ENERGY) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(ENERGY_TAG, bounded);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // Transfer this battery's stored FE into a clicked consumer
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        IEnergyStorage storage = ctx.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                ctx.getClickedPos(), ctx.getClickedFace());
        if (storage == null || !storage.canReceive()) return InteractionResult.PASS;
        ItemStack held = ctx.getItemInHand();
        int stored = energy(held);
        int accepted = storage.receiveEnergy(stored, true);
        if (accepted <= 0) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        int inserted = storage.receiveEnergy(stored, false);
        if (inserted <= 0) return InteractionResult.PASS;
        int remaining = stored - inserted;
        Player player = ctx.getPlayer();
        if (held.getCount() > 1 && remaining > 0) {
            held.shrink(1);
            ItemStack remainder = charged(this, remaining);
            if (player == null || !player.getInventory().add(remainder)) {
                if (player != null) player.drop(remainder, false);
            }
        } else if (remaining > 0) {
            setEnergy(held, remaining);
        } else {
            held.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    // Show the stored charge as a durability-style bar
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack) < MAX_ENERGY;
    }

    // Get the durability-style charge bar width
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / MAX_ENERGY);
    }

    // Get the durability-style charge bar colour
    @Override
    public int getBarColor(ItemStack stack) {
        return 0x4FD8FF;
    }

    // Describe the stored charge
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(energy(stack) + " / " + MAX_ENERGY + " FE")
                .withStyle(ChatFormatting.AQUA));
    }

    // Check whether a stack is an internal worker presentation item
    public static boolean isInternal(ItemStack stack) {
        return stack != null && stack.getItem() instanceof WorkerEnergyBatteryItem;
    }
}
