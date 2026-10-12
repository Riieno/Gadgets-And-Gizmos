package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.item.TimedTransformingItem;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

// Compacting produces this intermediary; cooling or water contact makes it usable.
public final class ScorchingOxidizedSolidFuelItem extends TimedTransformingItem{
    public ScorchingOxidizedSolidFuelItem(Properties properties){
        super(properties, () -> CTItems.OXIDIZED_SOLID_FUEL.get(),
                () -> CTDataComponents.COOLING_FINISH_TICK, 600);
    }

    @Override
    protected boolean canTransformInFluid(Fluid fluid){
        return fluid.builtInRegistryHolder().is(FluidTags.WATER);
    }

    @Override
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType){
        return 0;
    }

    @Override
    protected void onWaitingInventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected){
        if(!(entity instanceof Player player) || player.tickCount % 20 != 0) return;
        player.hurt(level.damageSources().onFire(), 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag){
        super.appendHoverText(stack, ctx, tooltip, flag);
        tooltip.add(Component.translatable("item.createthrusters.scorching_oxidized_solid_fuel.cooling")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.createthrusters.scorching_oxidized_solid_fuel.warning")
                .withStyle(ChatFormatting.RED));
    }
}
