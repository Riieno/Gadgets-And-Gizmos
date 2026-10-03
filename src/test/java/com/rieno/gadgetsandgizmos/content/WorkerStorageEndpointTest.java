package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerStorageEndpointTest{
    private static final BlockPos POS = new BlockPos(4, 64, 8);

    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // Discover crafting tables even though they have no block entity or item capability
    @Test void linkedCraftingTableIsAProcessorWithoutBeingStorage(){
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(POS)).thenReturn(Blocks.CRAFTING_TABLE.defaultBlockState());
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(java.util.Map.of('P', Ingredient.of(Items.OAK_PLANKS)), "PPP", "P P", "PPP"),
                new ItemStack(Items.CHEST));
        var recipeId = ResourceLocation.withDefaultNamespace("chest");
        when(manager.byKey(recipeId)).thenReturn(java.util.Optional.of(new RecipeHolder<>(recipeId, recipe)));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.getStoredLinker()).thenReturn(ItemStack.EMPTY);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(POS, null, "minecraft:crafting_table", "Table",
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        try(var linker = mockStatic(ContraptionNetworkLinkerData.class);
            var simulated = mockStatic(SimulatedHelper.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var access = mockStatic(WorkerContainerAccess.class)){
            linker.when(() -> ContraptionNetworkLinkerData.readTargets(ItemStack.EMPTY)).thenReturn(List.of(target));
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(target)).thenReturn("table");
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, POS)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.isLoaded(level, POS)).thenReturn(true);
            var endpoints = WorkerStorageEndpoint.linked(controller, true);
            assertEquals(1, endpoints.size());
            var station = endpoints.getFirst();
            var planks = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("oak_planks"));
            var plan = new WorkerRecipePlan(ResourceLocation.withDefaultNamespace("chest"),
                    ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                    List.of(new WorkerRecipePlan.Input(planks, 8)), planks, 1);
            assertTrue(station.supportsRecipePlan(plan));
            assertEquals("minecraft:crafting_table", station.snapshot().blockId());
            assertFalse(station.acceptsDelivery());
            assertEquals(0L, station.insert(new WorkerResourcePacket(planks, 4, null), false));
        }
    }

    // Re-read a source when its block entity appears after the first discovery pass
    @Test void unloadedContainerDoesNotPoisonLaterDiscovery(){
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(POS)).thenReturn(Blocks.CHEST.defaultBlockState());
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.getStoredLinker()).thenReturn(ItemStack.EMPTY);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(POS, null, "minecraft:chest", "Chest",
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 7));
        try(var linker = mockStatic(ContraptionNetworkLinkerData.class);
            var simulated = mockStatic(SimulatedHelper.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var access = mockStatic(WorkerContainerAccess.class)){
            linker.when(() -> ContraptionNetworkLinkerData.readTargets(ItemStack.EMPTY)).thenReturn(List.of(target));
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(target)).thenReturn("chest");
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, POS)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.isLoaded(level, POS)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, POS, null)).thenReturn(List.of(inventory));
            assertTrue(WorkerStorageEndpoint.linked(controller, true).isEmpty());
            when(level.getBlockEntity(POS)).thenReturn(mock(BlockEntity.class));
            var source = WorkerStorageEndpoint.linked(controller, true).getFirst();
            var logs = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("oak_log"));
            assertEquals(7, source.available(logs));
            assertTrue(source.canExtract(logs));
            assertFalse(source.isWorkerManagedStorage());
            assertTrue(source.isWorkerRecipeSource());
            assertFalse(source.acceptsDelivery());
            assertTrue(WorkerStorageEndpoint.linked(controller, false).isEmpty());
            inventory.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 11));
            assertEquals(11, source.available(logs));
            when(level.getBlockEntity(POS)).thenReturn(null);
            assertEquals(0, source.available(logs));
        }
    }

    @Test void linkedMillstoneAcceptsARecipeSpecificMillingRoute(){
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(POS)).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(level.getBlockEntity(POS)).thenReturn(mock(MillstoneBlockEntity.class));
        when(level.getRecipeManager()).thenReturn(mock(RecipeManager.class));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.getStoredLinker()).thenReturn(ItemStack.EMPTY);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(POS, null, "create:millstone", "Millstone",
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var area = new ContraptionNetworkLinkerData.LinkedArea(java.util.UUID.randomUUID(), null,
                new WorkerArea(POS, POS), "Milling line");
        try(var linker = mockStatic(ContraptionNetworkLinkerData.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class);
            var access = mockStatic(WorkerContainerAccess.class)){
            linker.when(() -> ContraptionNetworkLinkerData.readTargets(ItemStack.EMPTY)).thenReturn(List.of(target));
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(target)).thenReturn("millstone");
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, POS)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.isLoaded(level, POS)).thenReturn(true);
            WorkerRecipePlan plan = new WorkerRecipePlan(ResourceLocation.parse("create:milling/compat/ae2/fluix_crystal"),
                    ResourceLocation.parse("create:milling"), WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                            ResourceLocation.parse("ae2:fluix_crystal")), 1)),
                    new WorkerResourceKey(WorkerResourceType.ITEM,
                            ResourceLocation.parse("ae2:fluix_dust")), 1);
            assertTrue(WorkerStorageEndpoint.linked(controller, true).stream()
                    .anyMatch(endpoint -> endpoint.supportsRecipePlan(plan)));
            linker.when(() -> ContraptionNetworkLinkerData.readTargets(ItemStack.EMPTY)).thenReturn(List.of());
            linker.when(() -> ContraptionNetworkLinkerData.readAreas(ItemStack.EMPTY)).thenReturn(List.of(area));
            collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(level, null)).thenReturn(level);
            assertTrue(WorkerStorageEndpoint.linked(controller, true).stream()
                    .anyMatch(endpoint -> endpoint.isAreaMachine() && endpoint.supportsRecipePlan(plan)));
        }
    }
}
