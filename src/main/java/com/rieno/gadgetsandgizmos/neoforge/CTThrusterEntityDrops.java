package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ProcessingUpgradeType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;

// Apply thruster air-processing recipes to affected entities
public final class CTThrusterEntityDrops {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long MARKER_MAX_AGE_TICKS = 200L;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT thruster entity drops
    private CTThrusterEntityDrops() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the living drops
    public static boolean handleLivingDrops(LivingDropsEvent evt) {
        LivingEntity killed = evt.getEntity();
        if (isRecentMarker(killed, ThrusterBlockEntity.ENTITY_HAUNTING_CONVERSION_TIME_KEY)) {
            clearThrusterDropMarkers(killed);
            evt.setCanceled(true);
            return true;
        }

        ProcessingUpgradeType processingType = getRecentDropProcessingType(killed);
        if (processingType == ProcessingUpgradeType.SMOKING || processingType == ProcessingUpgradeType.SMELTING) {
            cookDrops(evt.getDrops(), killed.level(), processingType);
        }
        clearThrusterDropMarkers(killed);
        return false;
    }

    // Get the recent drop processing type
    private static ProcessingUpgradeType getRecentDropProcessingType(LivingEntity killed) {
        if (!isRecentMarker(killed, ThrusterBlockEntity.ENTITY_DROP_PROCESSING_TIME_KEY)) {
            return ProcessingUpgradeType.NONE;
        }
        String typeName = killed.getPersistentData().getString(ThrusterBlockEntity.ENTITY_DROP_PROCESSING_TYPE_KEY);
        try {
            return ProcessingUpgradeType.valueOf(typeName);
        } catch (IllegalArgumentException ignored) {
            return ProcessingUpgradeType.NONE;
        }
    }

    // Check if this is recent marker
    private static boolean isRecentMarker(LivingEntity killed, String timeKey) {
        if (!killed.getPersistentData().contains(timeKey)) {
            return false;
        }
        long markerAge = killed.level().getGameTime() - killed.getPersistentData().getLong(timeKey);
        return markerAge >= 0L && markerAge <= MARKER_MAX_AGE_TICKS;
    }

    // Clear the thruster drop markers
    private static void clearThrusterDropMarkers(LivingEntity killed) {
        killed.getPersistentData().remove(ThrusterBlockEntity.ENTITY_DROP_PROCESSING_TYPE_KEY);
        killed.getPersistentData().remove(ThrusterBlockEntity.ENTITY_DROP_PROCESSING_TIME_KEY);
        killed.getPersistentData().remove(ThrusterBlockEntity.ENTITY_HAUNTING_CONVERSION_KEY);
        killed.getPersistentData().remove(ThrusterBlockEntity.ENTITY_HAUNTING_CONVERSION_TIME_KEY);
    }

    // Cook the drops
    private static void cookDrops(Collection<ItemEntity> drops, Level level, ProcessingUpgradeType processingType) {
        for (ItemEntity drop : drops) {
            ItemStack cooked = cookStack(drop.getItem(), level, processingType);
            if (!cooked.isEmpty()) {
                drop.setItem(cooked);
            }
        }
    }

    // Get the cook stack
    private static ItemStack cookStack(ItemStack stack, Level level, ProcessingUpgradeType processingType) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) {
            return ItemStack.EMPTY;
        }

        ItemStack res = processingType == ProcessingUpgradeType.SMOKING
                ? cookingResult(stack, level, RecipeType.SMOKING)
                : cookingResult(stack, level, RecipeType.SMELTING);
        if (res.isEmpty()) {
            res = processingType == ProcessingUpgradeType.SMOKING
                    ? cookingResult(stack, level, RecipeType.SMELTING)
                    : cookingResult(stack, level, RecipeType.SMOKING);
        }
        if (res.isEmpty()) {
            return ItemStack.EMPTY;
        }

        res.setCount(res.getCount() * stack.getCount());
        return res;
    }

    // Get the cooking result
    private static <T extends AbstractCookingRecipe> ItemStack cookingResult(ItemStack stack, Level level,
            RecipeType<T> recipeType) {
        return level.getRecipeManager()
                .getRecipeFor(recipeType, new SingleRecipeInput(stack), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()).copy())
                .orElse(ItemStack.EMPTY);
    }
}
