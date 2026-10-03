package com.rieno.gadgetsandgizmos.compat.recipe;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProcessingRecipeViews;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

// Keep thruster recipe viewer modes aligned with the exhaust upgrade types
public final class ThrusterProcessingDisplays{
    private ThrusterProcessingDisplays(){}

    public enum Mode{
        SMELTING("thruster_smelting", "minecraft:smelting", "minecraft:blasting"),
        SMOKING("thruster_smoking", "minecraft:smoking"),
        HAUNTING("thruster_haunting", "create:haunting");

        private final String path;
        private final List<ResourceLocation> recipeTypes;

        Mode(String path, String... recipeTypes){
            this.path = path;
            this.recipeTypes = java.util.Arrays.stream(recipeTypes).map(ResourceLocation::parse).toList();
        }

        // Get this viewer category's owning-mod id
        public ResourceLocation id(){
            return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, path);
        }

        // Get recipes that the matching exhaust upgrade can process
        public List<WorkerProcessingRecipeViews.View> recipes(Level level){
            return WorkerProcessingRecipeViews.recipes(level, recipeTypes);
        }

        // Get the machine and every compatible upgrade tier
        public List<ItemStack> catalysts(){
            List<ItemStack> stacks = new java.util.ArrayList<>();
            stacks.add(new ItemStack(CTItems.THRUSTER.get()));
            for(int tier = 1; tier <= 4; tier++) stacks.add(upgrade(tier));
            return List.copyOf(stacks);
        }

        // Use the thruster itself as the category icon
        public ItemStack icon(){
            return new ItemStack(CTItems.THRUSTER.get());
        }

        // Get this processing mode's upgrade at the requested tier
        public ItemStack upgrade(int tier){
            return switch(this){
                case SMELTING -> switch(tier){
                    case 1 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T1.get());
                    case 2 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T2.get());
                    case 3 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T3.get());
                    default -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T4.get());
                };
                case SMOKING -> switch(tier){
                    case 1 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T1.get());
                    case 2 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T2.get());
                    case 3 -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T3.get());
                    default -> new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T4.get());
                };
                case HAUNTING -> switch(tier){
                    case 1 -> new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T1.get());
                    case 2 -> new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T2.get());
                    case 3 -> new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T3.get());
                    default -> new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T4.get());
                };
            };
        }
    }
}
