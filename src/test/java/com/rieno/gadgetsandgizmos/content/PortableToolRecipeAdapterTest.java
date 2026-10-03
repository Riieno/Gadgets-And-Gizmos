package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.recipe.PortableToolRecipeAdapter;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlanner;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeIndex;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortableToolRecipeAdapterTest {
    @BeforeAll static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test void plansPortableToolAndReturnsDamagedTool() {
        ServerLevel level = mock(ServerLevel.class);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        ProcessingRecipe<?, ?> recipe = mock(ProcessingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT)));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.IRON_NUGGET));
        ResourceLocation recipeId = ResourceLocation.parse("test:hammered_iron");
        RecipeHolder<?> holder = new RecipeHolder<>(recipeId, recipe);
        doReturn(Optional.of(holder)).when(manager).byKey(recipeId);

        ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE);
        PortableToolRecipeAdapter adapter = new PortableToolRecipeAdapter(toolId, 1);
        var definition = adapter.recipes(level, holder).getFirst();
        assertEquals(WorkerRecipePlan.Operation.WORKER_CRAFTING, definition.operation());
        assertEquals(toolId, definition.ingredients().getLast().alternatives().getFirst().id());
        WorkerResourceKey toolKey = new WorkerResourceKey(WorkerResourceType.ITEM, toolId);
        assertEquals(Long.MAX_VALUE, adapter.reusableToolCredit(definition, 1, Map.of(toolKey, 1L)));
        WorkerResourceKey iron = new WorkerResourceKey(WorkerResourceType.ITEM,
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
        var schedule = WorkerRecipePlanner.planDetailed(definition.result(), 5L,
                Map.of(iron, 5L, toolKey, 1L), new WorkerRecipeIndex(List.of(definition)),
                WorkerRecipePlan.Operation.WORKER_CRAFTING, ignored -> true, ignored -> true,
                ignored -> true, ignored -> 0,
                (candidate, index) -> adapter.reusableToolCredit(candidate, index, Map.of(toolKey, 1L)));
        assertTrue(schedule.chain().executable(), schedule.failure().message());
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(5);
        WorkerRecipePlan plan = new WorkerRecipePlan(recipeId, definition.processorType(),
                definition.operation(), List.of(
                new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L),
                new WorkerRecipePlan.Input(toolKey, 1L)), definition.result(), 1L);
        var crafted = adapter.craft(level, plan, Map.of(toolKey, tool));
        assertTrue(crafted.output().is(Items.IRON_NUGGET));
        assertEquals(6, crafted.remainders().getFirst().getDamageValue());
        assertEquals(5, tool.getDamageValue());
    }
}
