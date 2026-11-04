package com.rieno.gadgetsandgizmos.compat.recipe;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeDefinition;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Adapt item-operated processing recipes to the worker's portable crafting path.
public final class PortableToolRecipeAdapter implements WorkerRecipeCatalog.Adapter {
    private final ResourceLocation toolId;
    private final int damagePerUse;

    public PortableToolRecipeAdapter(ResourceLocation toolId, int damagePerUse) {
        this.toolId = toolId;
        this.damagePerUse = Math.max(0, damagePerUse);
    }

    @Override public List<WorkerRecipeDefinition> recipes(Level level, RecipeHolder<?> holder) {
        Item tool = BuiltInRegistries.ITEM.get(toolId);
        if(tool == Items.AIR || holder.value().getIngredients().isEmpty()) return List.of();
        ItemStack output = holder.value().getResultItem(level.registryAccess());
        if(output.isEmpty()) return List.of();
        List<WorkerRecipeDefinition.Ingredient> ingredients = new ArrayList<>();
        for(Ingredient ingredient : holder.value().getIngredients()) {
            if(ingredient.isEmpty()) continue;
            List<WorkerResourceKey> alternatives = Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .distinct().toList();
            if(alternatives.isEmpty()) return List.of();
            ingredients.add(new WorkerRecipeDefinition.Ingredient(alternatives, 1L));
        }
        ingredients.add(new WorkerRecipeDefinition.Ingredient(
                List.of(new WorkerResourceKey(WorkerResourceType.ITEM, toolId)), 1L));
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        return List.of(new WorkerRecipeDefinition(holder.id(), type,
                WorkerRecipePlan.Operation.WORKER_CRAFTING, ingredients,
                new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(output.getItem())),
                output.getCount()));
    }

    @Override public WorkerCraftingGrid.Result craft(Level level, WorkerRecipePlan plan,
                                                      Map<WorkerResourceKey, ItemStack> equipped) {
        if(plan == null || plan.operation() != WorkerRecipePlan.Operation.WORKER_CRAFTING
                || plan.inputs().isEmpty() || !plan.inputs().getLast().resource().id().equals(toolId))
            return WorkerCraftingGrid.Result.EMPTY;
        var recipe = WorkerRecipeCatalog.recipe(level, plan);
        if(recipe == null) return WorkerCraftingGrid.Result.EMPTY;
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if(result.isEmpty() || !BuiltInRegistries.ITEM.getKey(result.getItem()).equals(plan.result().id()))
            return WorkerCraftingGrid.Result.EMPTY;
        WorkerResourceKey toolKey = new WorkerResourceKey(WorkerResourceType.ITEM, toolId);
        ItemStack equippedTool = equipped.get(toolKey);
        ItemStack tool = equippedTool == null ? ItemStack.EMPTY : equippedTool.copy();
        if(tool.isEmpty()) return WorkerCraftingGrid.Result.EMPTY;
        if(damagePerUse > 0 && tool.isDamageableItem()) {
            int damage = tool.getDamageValue() + damagePerUse;
            tool = damage >= tool.getMaxDamage() ? ItemStack.EMPTY : tool.copy();
            if(!tool.isEmpty()) tool.setDamageValue(damage);
        }
        return new WorkerCraftingGrid.Result(result, tool.isEmpty() ? List.of() : List.of(tool));
    }

    @Override public long reusableToolCredit(WorkerRecipeDefinition recipe, int ingredientIndex,
                                             Map<WorkerResourceKey, Long> equippedStock) {
        if(recipe == null || ingredientIndex != recipe.ingredients().size() - 1) return 0L;
        WorkerResourceKey tool = new WorkerResourceKey(WorkerResourceType.ITEM, toolId);
        return equippedStock.getOrDefault(tool, 0L) > 0L ? Long.MAX_VALUE : 0L;
    }

    @Override public long maximumBatch(WorkerRecipePlan plan) { return 1L; }

    @Override public boolean isReusableToolInput(WorkerRecipePlan plan, int ingredientIndex) {
        return plan != null && ingredientIndex == plan.inputs().size() - 1
                && plan.inputs().get(ingredientIndex).resource().id().equals(toolId);
    }
}
