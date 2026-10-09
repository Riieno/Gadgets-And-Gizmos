package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerCraftingIntegrationTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // A 1024-item request fits in sixteen of the worker's twenty-seven slots and needs one delivery
    @Test void deliversSixteenStacksInOneTrip() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            Object runtime = ctx.request(Items.IRON_INGOT, 1024, false);
            ctx.tick(runtime);
            assertEquals(1024, contents(ctx.inventory, Items.IRON_INGOT));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(1024, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(0, contents(ctx.inventory, Items.IRON_INGOT));
            assertNull(queue(runtime).current());
        }
    }

    // Use all twenty-seven slots before starting a second trip for the remaining two stacks
    @Test void fillsWorkerBeforeSplittingLargerRequests() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            Object runtime = ctx.request(Items.IRON_INGOT, 1856, false);
            ctx.tick(runtime);
            assertEquals(1728, contents(ctx.inventory, Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(1728, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(128, contents(ctx.inventory, Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(1856, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertNull(queue(runtime).current());
        }
    }

    // Preserve personal inventory contents while using every remaining cargo slot
    @Test void respectsOccupiedWorkerSlots() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            for(int idx = 0; idx < 13; idx++) ctx.inventory.setStackInSlot(idx, new ItemStack(Items.COBBLESTONE, 64));
            Object runtime = ctx.request(Items.IRON_INGOT, 1024, false);
            ctx.tick(runtime);
            assertEquals(896, contents(ctx.inventory, Items.IRON_INGOT));
            ctx.tick(runtime);
            ctx.tick(runtime);
            assertEquals(128, contents(ctx.inventory, Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(1024, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(832, contents(ctx.inventory, Items.COBBLESTONE));
            assertNull(queue(runtime).current());
        }
    }

    // Count free space across both partially filled stacks and empty player slots
    @Test void limitsPickupToActualRecipientCapacity() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            var items = ctx.recipient.getInventory().items;
            for(int idx = 0; idx < 33; idx++) items.set(idx, new ItemStack(Items.COBBLESTONE, 64));
            items.set(33, new ItemStack(Items.IRON_INGOT, 32));
            items.set(34, new ItemStack(Items.IRON_INGOT, 32));
            Object runtime = ctx.request(Items.IRON_INGOT, 1024, false);
            ctx.tick(runtime);
            assertEquals(128, contents(ctx.inventory, Items.IRON_INGOT));
            ctx.tick(runtime);
            assertEquals(192, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(896, queue(runtime).current().task().remainingAmount());
        }
    }

    // Accept available player space and retain the rest without another source pickup
    @Test void retainsUndeliveredCargoWhenRecipientFillsInventory() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            Object runtime = ctx.request(Items.IRON_INGOT, 1024, false);
            ctx.tick(runtime);
            assertEquals(1024, contents(ctx.inventory, Items.IRON_INGOT));
            var items = ctx.recipient.getInventory().items;
            for(int idx = 0; idx < 35; idx++) items.set(idx, new ItemStack(Items.COBBLESTONE, 64));
            ctx.tick(runtime);
            assertEquals(64, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(960, contents(ctx.inventory, Items.IRON_INGOT));
            items.set(33, ItemStack.EMPTY);
            items.set(34, ItemStack.EMPTY);
            ctx.tick(runtime);
            assertEquals(192, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(832, contents(ctx.inventory, Items.IRON_INGOT));
        }
    }

    // Exercise real SCM adapters, creative-crate inventory and server ticks through both plank batches
    @Test void craftsTwoTablesFromInfiniteLogsWithoutExternalStaging() throws Exception{
        try(var ctx = new Fixture(Items.OAK_LOG)){
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("crafting_table", Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4)));
            Object runtime = ctx.request(Items.CRAFTING_TABLE, 2);
            assertEquals(8, queue(runtime).current().task().requestedAmount());
            assertEquals(2, queue(runtime).planned().size());
            ctx.tick(runtime);
            var write = runtime.getClass().getDeclaredMethod("toTag");
            var read = runtime.getClass().getDeclaredMethod("fromTag", CompoundTag.class);
            write.setAccessible(true);
            read.setAccessible(true);
            runtime = read.invoke(null, write.invoke(runtime));
            ctx.finish(runtime);
            assertEquals(2, ctx.recipient.getInventory().countItem(Items.CRAFTING_TABLE));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.OAK_PLANKS));
            for(int idx = 0; idx < ctx.inventory.getSlots(); idx++) assertTrue(ctx.inventory.getStackInSlot(idx).isEmpty());
        }
    }

    // Keep sibling resources available while executing a deeper mixed-ingredient tree
    @Test void craftsPickaxeThroughPlanksAndSticks() throws Exception{
        try(var ctx = new Fixture(Items.OAK_LOG)){
            var pickaxe = recipe("wooden_pickaxe", Items.WOODEN_PICKAXE, 1, Items.OAK_PLANKS, 5);
            when(pickaxe.value().getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.OAK_PLANKS),
                    Ingredient.of(Items.STICK), Ingredient.of(Items.STICK)));
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("stick", Items.STICK, 4, Items.OAK_PLANKS, 2), pickaxe));
            ctx.finish(ctx.request(Items.WOODEN_PICKAXE, 1), true);
            assertEquals(1, ctx.recipient.getInventory().countItem(Items.WOODEN_PICKAXE));
            verify(ctx.controller, never()).retryWorkerRecipe(argThat(order -> order.recipePlan() != null), anyMap());
        }
    }

    // A linked table has no inventory, but must execute 3x3 recipes using an infinite linked source
    @Test void craftsIronBlocksAtLinkedTableFromInfiniteIngots() throws Exception{
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            ctx.recipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_block"),
                    new ShapedRecipe("", CraftingBookCategory.MISC,
                            ShapedRecipePattern.of(java.util.Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III", "III", "III"),
                            new ItemStack(Items.IRON_BLOCK)))));
            Object runtime = ctx.request(Items.IRON_BLOCK, 16);
            assertEquals(WorkerRecipePlan.Operation.CRAFTING, queue(runtime).current().recipePlan().operation());
            ctx.finish(runtime);
            assertEquals(16, ctx.recipient.getInventory().countItem(Items.IRON_BLOCK));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            verify(ctx.controller, never()).retryWorkerRecipe(argThat(order -> order.recipePlan() != null), anyMap());
        }
    }

    @Test void usesEquippedRecipeIngredientAndReturnsItsRemainderToTools() throws Exception{
        try(var ctx = new Fixture(Items.OAK_PLANKS)){
            ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
            ctx.tools.setStackInSlot(0, tool.copy());
            RecipeHolder<?> holder = recipe("tool_crafted_stick", Items.STICK, 1, Items.OAK_PLANKS, 2);
            CraftingRecipe crafting = (CraftingRecipe)holder.value();
            when(crafting.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.IRON_PICKAXE)));
            doReturn(NonNullList.of(ItemStack.EMPTY,
                    ItemStack.EMPTY, tool.copy())).when(crafting).getRemainingItems(any());
            ctx.recipes(List.of(holder));
            ctx.finish(ctx.request(Items.STICK, 1));
            assertEquals(1, ctx.recipient.getInventory().countItem(Items.STICK));
            assertEquals(1, contents(ctx.tools, Items.IRON_PICKAXE));
        }
    }

    // Run smelting prerequisites and a real 3x3 recipe through the worker's persistent state machine
    @Test void plansRawIronFromAnOrdinaryLinkedSource() throws Exception{
        try(var ctx = new Fixture(Items.RAW_IRON)){
            when(ctx.level.getBlockEntity(ctx.sourcePos)).thenReturn(mock(BlockEntity.class));
            var furnacePos = new BlockPos(8, 64, 0);
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(furnacePos,
                    Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(ctx.level);
            ctx.link(furnacePos, Blocks.FURNACE.defaultBlockState(), furnace);
            ctx.recipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_from_raw"),
                    new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_IRON),
                            new ItemStack(Items.IRON_INGOT), 0, 200))));
            Object runtime = ctx.request(Items.IRON_INGOT, 1);
            assertEquals(ResourceLocation.withDefaultNamespace("iron_from_raw"),
                    queue(runtime).current().recipePlan().recipeId());
        }
    }

    // Run smelting prerequisites and a real 3x3 recipe through the worker's persistent state machine
    @Test void fuelsFurnaceThenCraftsBlockAcrossReloads() throws Exception{
        try(var ctx = new Fixture(Items.RAW_IRON);
            var fuels = mockStatic(net.neoforged.neoforge.event.EventHooks.class, CALLS_REAL_METHODS)){
            fuels.when(() -> net.neoforged.neoforge.event.EventHooks.getItemBurnTime(any(), anyInt(), any()))
                    .thenAnswer(call -> ((ItemStack)call.getArgument(0)).is(Items.COAL) ? 1600 : 0);
            var furnacePos = new BlockPos(8, 64, 0);
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(furnacePos, Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(ctx.level);
            ctx.link(furnacePos, Blocks.FURNACE.defaultBlockState(), furnace);
            ctx.inventory.setStackInSlot(26, new ItemStack(Items.COAL, 9));
            ctx.recipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("a_iron_ingot_from_ore"),
                            new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0, 200)),
                    new RecipeHolder<>(ResourceLocation.withDefaultNamespace("z_iron_ingot_from_raw"),
                            new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), 0, 200)),
                    new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_block"),
                            new ShapedRecipe("", CraftingBookCategory.MISC,
                                    ShapedRecipePattern.of(java.util.Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III", "III", "III"),
                                    new ItemStack(Items.IRON_BLOCK)))));
            Object runtime = ctx.request(Items.IRON_BLOCK, 1);
            for(int idx = 0; idx < 300 && queue(runtime).current() != null; idx++){
                if(!furnace.getItem(0).isEmpty() && !furnace.getItem(1).isEmpty()){
                    assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
                    assertTrue(furnace.getItem(1).is(Items.COAL));
                    furnace.setItem(0, ItemStack.EMPTY);
                    furnace.setItem(1, ItemStack.EMPTY);
                    furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 9));
                }
                ctx.tick(runtime);
                var write = runtime.getClass().getDeclaredMethod("toTag");
                var read = runtime.getClass().getDeclaredMethod("fromTag", CompoundTag.class);
                write.setAccessible(true);
                read.setAccessible(true);
                runtime = read.invoke(null, write.invoke(runtime));
            }
            var status = runtime.getClass().getDeclaredField("status");
            status.setAccessible(true);
            assertNull(queue(runtime).current(), status.get(runtime).toString());
            assertEquals(1, ctx.recipient.getInventory().countItem(Items.IRON_BLOCK));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
        }
    }

    // A furnace with input and fuel cannot satisfy a processing job until its output slot changes.
    @Test void furnaceInputDoesNotBecomeWorkerCargoBeforeSmelting() throws Exception{
        try(var ctx = new Fixture(Items.RAW_IRON);
            var fuels = mockStatic(net.neoforged.neoforge.event.EventHooks.class, CALLS_REAL_METHODS)){
            fuels.when(() -> net.neoforged.neoforge.event.EventHooks.getItemBurnTime(any(), anyInt(), any()))
                    .thenAnswer(call -> ((ItemStack)call.getArgument(0)).is(Items.COAL) ? 1600 : 0);
            var furnacePos = new BlockPos(8, 64, 0);
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(furnacePos, Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(ctx.level);
            ctx.link(furnacePos, Blocks.FURNACE.defaultBlockState(), furnace);
            ctx.inventory.setStackInSlot(26, new ItemStack(Items.COAL, 9));
            ctx.recipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_ingot_from_raw"),
                            new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_IRON),
                                    new ItemStack(Items.IRON_INGOT), 0, 200)),
                    new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_block"),
                            new ShapedRecipe("", CraftingBookCategory.MISC,
                                    ShapedRecipePattern.of(java.util.Map.of('I', Ingredient.of(Items.IRON_INGOT)),
                                            "III", "III", "III"), new ItemStack(Items.IRON_BLOCK)))));
            Object runtime = ctx.request(Items.IRON_BLOCK, 1);
            for(int idx = 0; idx < 80; idx++) ctx.tick(runtime);
            assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
            assertTrue(furnace.getItem(2).isEmpty());
            assertEquals(0, contents(ctx.inventory, Items.IRON_INGOT));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_INGOT));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_BLOCK));
            assertNotNull(queue(runtime).current());
        }
    }

    // Plan a tagged final recipe using linked logs and several carried intermediate inputs
    @Test void plansCogwheelShapeWithPartialAlloyAndIron() throws Exception{
        try(var ctx = new Fixture(Items.OAK_LOG)){
            ctx.inventory.setStackInSlot(0, new ItemStack(Items.FLINT));
            ctx.inventory.setStackInSlot(1, new ItemStack(Items.ANDESITE, 2));
            ctx.inventory.setStackInSlot(2, new ItemStack(Items.IRON_INGOT));
            var alloy = recipe("alloy", Items.FLINT, 1, Items.ANDESITE, 4);
            when(alloy.value().getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.ANDESITE), Ingredient.of(Items.ANDESITE),
                    Ingredient.of(Items.IRON_NUGGET), Ingredient.of(Items.IRON_NUGGET)));
            var cogwheel = recipe("cogwheel", Items.COMPASS, 1, Items.STICK, 2);
            when(cogwheel.value().getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.STICK), Ingredient.of(Items.BIRCH_PLANKS, Items.OAK_PLANKS)));
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("iron_nugget", Items.IRON_NUGGET, 9, Items.IRON_INGOT, 1),
                    alloy, recipe("shaft", Items.STICK, 8, Items.FLINT, 2), cogwheel));
            Object runtime = ctx.request(Items.COMPASS, 2);
            assertEquals(List.of(Items.IRON_NUGGET, Items.FLINT, Items.STICK, Items.OAK_PLANKS, Items.COMPASS),
                    java.util.stream.Stream.concat(java.util.stream.Stream.of(queue(runtime).current()),
                            queue(runtime).planned().stream()).filter(order -> order.recipePlan() != null)
                            .map(order -> BuiltInRegistries.ITEM.get(order.outputResource().id())).toList());
            ctx.finish(runtime);
            assertEquals(2, ctx.recipient.getInventory().countItem(Items.COMPASS));
        }
    }

    // Use personal working stock when no linked container holds a recipe ingredient
    @Test void craftsFromAssignedWorkersOwnInventory() throws Exception{
        try(var ctx = new Fixture(Items.COBBLESTONE)){
            ctx.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("crafting_table", Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4)));
            ctx.finish(ctx.request(Items.CRAFTING_TABLE, 1));
            assertEquals(1, ctx.recipient.getInventory().countItem(Items.CRAFTING_TABLE));
            assertEquals(0, contents(ctx.inventory, Items.OAK_LOG));
        }
    }

    // Finish every requested result after a multi-batch intermediate moves through worker inventory
    @Test void craftsMultipleTablesWithoutStrandingIntermediates() throws Exception{
        try(var ctx = new Fixture(Items.OAK_LOG)){
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("crafting_table", Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4)));
            ctx.finish(ctx.request(Items.CRAFTING_TABLE, 2), true);
            assertEquals(2, ctx.recipient.getInventory().countItem(Items.CRAFTING_TABLE));
            assertEquals(0, contents(ctx.inventory, Items.OAK_PLANKS));
        }
    }

    // Clear an occupied cargo slot into linked storage before using a personal recipe input
    @Test void stowsUnrelatedInventoryBeforeCrafting() throws Exception{
        try(var ctx = new Fixture(Items.COBBLESTONE)){
            ctx.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
            for(int slot = 1; slot < ctx.inventory.getSlots(); slot++)
                ctx.inventory.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
            BlockPos storagePos = new BlockPos(9, 64, 0);
            var storage = new ItemStackHandler(27);
            var vault = mock(SmartVaultBlockEntity.class);
            when(vault.getItemHandler()).thenReturn(storage);
            ctx.link(storagePos, Blocks.BARREL.defaultBlockState(), vault);
            ctx.recipes(List.of(recipe("oak_planks", Items.OAK_PLANKS, 4, Items.OAK_LOG, 1),
                    recipe("crafting_table", Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4)));
            ctx.finish(ctx.request(Items.CRAFTING_TABLE, 1));
            assertEquals(64, contents(storage, Items.COBBLESTONE));
            assertEquals(1, ctx.recipient.getInventory().countItem(Items.CRAFTING_TABLE));
        }
    }

    // Existing machine output does not count as output from the current processing batch.
    @Test void furnaceMustProduceNewOutputForCurrentBatch() throws Exception{
        try(var ctx = new Fixture(Items.RAW_IRON);
            var fuels = mockStatic(net.neoforged.neoforge.event.EventHooks.class, CALLS_REAL_METHODS)){
            fuels.when(() -> net.neoforged.neoforge.event.EventHooks.getItemBurnTime(any(), anyInt(), any()))
                    .thenAnswer(call -> ((ItemStack)call.getArgument(0)).is(Items.COAL) ? 1600 : 0);
            var furnacePos = new BlockPos(8, 64, 0);
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(furnacePos, Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(ctx.level);
            furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 9));
            ctx.link(furnacePos, Blocks.FURNACE.defaultBlockState(), furnace);
            ctx.inventory.setStackInSlot(26, new ItemStack(Items.COAL, 9));
            ctx.recipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_ingot_from_raw"),
                            new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_IRON),
                                    new ItemStack(Items.IRON_INGOT), 0, 200)),
                    new RecipeHolder<>(ResourceLocation.withDefaultNamespace("iron_block"),
                            new ShapedRecipe("", CraftingBookCategory.MISC,
                                    ShapedRecipePattern.of(java.util.Map.of('I', Ingredient.of(Items.IRON_INGOT)),
                                            "III", "III", "III"), new ItemStack(Items.IRON_BLOCK)))));
            Object runtime = ctx.request(Items.IRON_BLOCK, 1);
            for(int idx = 0; idx < 80; idx++){
                ctx.tick(runtime);
                var write = runtime.getClass().getDeclaredMethod("toTag");
                var read = runtime.getClass().getDeclaredMethod("fromTag", CompoundTag.class);
                write.setAccessible(true);
                read.setAccessible(true);
                runtime = read.invoke(null, write.invoke(runtime));
            }
            assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
            assertEquals(9, furnace.getItem(2).getCount());
            assertEquals(0, contents(ctx.inventory, Items.IRON_INGOT));
            assertEquals(0, ctx.recipient.getInventory().countItem(Items.IRON_BLOCK));
        }
    }

    // A failed assembly may merge with older byproduct stock; claim only the new result
    @Test void collectsOnlyNewFailedAssemblyOutput() {
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            BlockPos machinePos = new BlockPos(9, 64, 0);
            var output = new ItemStackHandler(1);
            output.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 10));
            BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(WorkerMachine.class));
            WorkerMachine machine = (WorkerMachine)blockEntity;
            when(machine.itemInputs(any())).thenReturn(List.of(new ItemStackHandler(1)));
            when(machine.itemOutputs(any())).thenReturn(List.of(output));
            ctx.link(machinePos, Blocks.FURNACE.defaultBlockState(), blockEntity);
            ResourceLocation id = ResourceLocation.parse("test:chance_assembly");
            var recipe = new SequencedAssemblyRecipe(null);
            recipe.resultPool.add(new ProcessingOutput(Items.CLOCK, 1, 0.8F));
            recipe.resultPool.add(new ProcessingOutput(Items.GOLD_NUGGET, 1, 0.2F));
            ctx.recipes(List.of(new RecipeHolder<>(id, recipe)));
            WorkerRecipePlan plan = new WorkerRecipePlan(id, ResourceLocation.parse("create:sequenced_assembly"),
                    WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L)),
                    new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(Items.CLOCK)), 1L, -1);
            var endpoint = WorkerStorageEndpoint.linked(ctx.controller, true).stream()
                    .filter(candidate -> candidate.position().equals(machinePos)).findFirst().orElseThrow().forRecipe(plan);
            var failed = new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(Items.GOLD_NUGGET));
            var ledger = new WorkerOutputLedger(java.util.Map.of(failed, 10L));
            assertTrue(WorkerRecipeCatalog.hasChanceResult(ctx.level, plan));
            assertFalse(endpoint.hasRecipeOutcome(plan, ledger));
            output.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 11));
            assertTrue(endpoint.hasRecipeOutcome(plan, ledger));
            WorkerResourcePacket claimed = endpoint.alternateRecipeResult(plan, ledger);
            assertNotNull(claimed);
            assertEquals(1L, claimed.amount());
            assertEquals(10, output.getStackInSlot(0).getCount());
            assertFalse(endpoint.hasRecipeOutcome(plan, ledger));
        }
    }

    // Keep a linked machine's ingredient delivery inside its SCM area
    @Test void areaMachineDoesNotInsertIntoOutsideContainer(){
        BlockPos machinePos = new BlockPos(9, 64, 0);
        WorkerArea bounds = new WorkerArea(machinePos, machinePos);
        var area = new ContraptionNetworkLinkerData.LinkedArea(UUID.randomUUID(), null,
                bounds, "Assembly line");
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            var outside = new ItemStackHandler(1);
            var inside = new ItemStackHandler(1);
            BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(WorkerMachine.class));
            WorkerMachine machine = (WorkerMachine)blockEntity;
            when(machine.itemInputs(any())).thenReturn(List.of(outside));
            when(machine.itemInputs(any(), eq(bounds))).thenReturn(List.of(inside));
            ctx.link(machinePos, Blocks.FURNACE.defaultBlockState(), blockEntity);
            ctx.linker.when(() -> ContraptionNetworkLinkerData.readAreas(ItemStack.EMPTY))
                    .thenReturn(List.of(area));
            ctx.collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(ctx.level, null))
                    .thenReturn(ctx.level);
            WorkerRecipePlan plan = new WorkerRecipePlan(ResourceLocation.parse("test:assembly"),
                    ResourceLocation.parse("create:sequenced_assembly"), WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L)),
                    new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(Items.CLOCK)), 1L);
            var endpoint = WorkerStorageEndpoint.linked(ctx.controller, true).stream()
                    .filter(candidate -> candidate.position().equals(machinePos)).findFirst().orElseThrow()
                    .forRecipe(plan);
            assertTrue(endpoint.referenceId().startsWith("worker:area:"), endpoint.referenceId());
            var resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
            assertEquals(1L, endpoint.insert(new WorkerResourcePacket(resource, 1L, null), false));
            assertTrue(outside.getStackInSlot(0).isEmpty());
            assertEquals(1, inside.getStackInSlot(0).getCount());
        }
    }

    @Test void faceAndAreaLinksRetainOrdinaryProcessingMachines(){
        BlockPos machinePos = new BlockPos(9, 64, 0);
        BlockPos planePos = machinePos.north();
        WorkerArea bounds = new WorkerArea(machinePos, machinePos);
        var area = new ContraptionNetworkLinkerData.LinkedArea(UUID.randomUUID(), null, bounds, "Press");
        var face = new ContraptionNetworkLinkerData.LinkedTarget(planePos, null,
                "createthrusters:contraption_network_linker_plane", "Press",
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.FACE,
                List.of(new ContraptionNetworkLinkerData.LinkedFace(net.minecraft.core.Direction.NORTH,
                        "Input", "")));
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(WorkerMachine.class));
            WorkerMachine machine = (WorkerMachine)blockEntity;
            ResourceLocation recipeId = ResourceLocation.parse("test:pressed_item");
            ResourceLocation processor = ResourceLocation.parse("create:pressing");
            WorkerRecipePlan plan = new WorkerRecipePlan(recipeId, processor,
                    WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L)),
                    new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_NUGGET)), 1L);
            when(machine.maySupportRecipe(recipeId, processor)).thenReturn(true);
            when(machine.supports(plan)).thenReturn(true);
            when(machine.supports(plan, bounds)).thenReturn(true);
            ctx.link(machinePos, Blocks.FURNACE.defaultBlockState(), blockEntity);
            var planeState = mock(net.minecraft.world.level.block.state.BlockState.class);
            when(planeState.getBlock()).thenReturn(mock(ContraptionNetworkLinkerPlaneBlock.class));
            when(ctx.level.getBlockState(planePos)).thenReturn(planeState);
            ctx.access.when(() -> WorkerContainerAccess.isLoaded(ctx.level, planePos)).thenReturn(true);
            ctx.targets.set(ctx.targets.size() - 1, face);
            var faceEndpoint = WorkerStorageEndpoint.linked(ctx.controller, true).stream()
                    .filter(endpoint -> endpoint.position().equals(machinePos)).findFirst().orElseThrow();
            assertTrue(faceEndpoint.supportsRecipePlan(plan));

            ctx.linker.when(() -> ContraptionNetworkLinkerData.readAreas(ItemStack.EMPTY)).thenReturn(List.of(area));
            ctx.collector.when(() -> SubLevelBlockEntityCollector.resolveTargetLevel(ctx.level, null))
                    .thenReturn(ctx.level);
            var areaEndpoint = WorkerStorageEndpoint.linked(ctx.controller, true).stream()
                    .filter(endpoint -> endpoint.position().equals(machinePos) && endpoint.isAreaMachine())
                    .findFirst().orElseThrow();
            assertTrue(areaEndpoint.supportsRecipePlan(plan));
        }
    }

    // Creative crates can satisfy planning beyond one displayed stack and cannot destroy returned cargo
    @Test void linkedCreativeCrateIsAnUnlimitedSourceOnly(){
        try(var ctx = new Fixture(Items.IRON_INGOT)){
            var source = WorkerStorageEndpoint.linked(ctx.controller, false).getFirst();
            var ingot = new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace("iron_ingot"));
            assertTrue(source.isWorkerManagedStorage());
            assertTrue(source.canExtract(ingot));
            assertEquals(Long.MAX_VALUE, source.available(ingot));
            assertFalse(source.acceptsDelivery());
            assertFalse(source.canInsert(ingot));
            assertFalse(source.canInsert(new ItemStack(Items.IRON_INGOT)));
            assertEquals(0, source.space(ingot));
            assertEquals(0, source.insert(new WorkerResourcePacket(ingot, 4, null), false));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FIXTURES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Replace world movement with arrival while retaining request compilation and the complete worker tick state machine
    private static final class Fixture implements AutoCloseable{
        final ServerLevel level = mock(ServerLevel.class);
        final AdvancedContraptionControllerBlockEntity controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        final WorkerPodBlockEntity pod = mock(WorkerPodBlockEntity.class, CALLS_REAL_METHODS);
        final PlayerMannequinEntity worker = mock(PlayerMannequinEntity.class);
        final ServerPlayer recipient = mock(ServerPlayer.class);
        final ItemStackHandler inventory = new ItemStackHandler(27);
        final ItemStackHandler tools = new ItemStackHandler(6);
        final List<MockedStatic<?>> mocks = new ArrayList<>();
        final UUID workerId = UUID.randomUUID();
        final UUID playerId = UUID.randomUUID();
        final BlockPos sourcePos = new BlockPos(3, 64, 0);
        final BlockPos tablePos = new BlockPos(6, 64, 0);
        final List<ContraptionNetworkLinkerData.LinkedTarget> targets = new ArrayList<>();
        MockedStatic<ContraptionNetworkLinkerData> linker;
        MockedStatic<SubLevelBlockEntityCollector> collector;
        MockedStatic<WorkerContainerAccess> access;

        Fixture(Item supplied){
            try{
                var lookup = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("workerRecipeLookup");
                lookup.setAccessible(true);
                lookup.set(controller, new WorkerRecipeLookup());
            }catch(ReflectiveOperationException ex){ throw new AssertionError(ex); }
            controller.setLevel(level);
            pod.setLevel(level);
            doNothing().when(pod).setChanged();
            doNothing().when(pod).sendData();
            doReturn(ItemStack.EMPTY).when(controller).getStoredLinker();
            doReturn(List.of(new WorkerStatusSnapshot(workerId, "Worker", WorkerProfile.Job.ANY,
                    true, null, BlockPos.ZERO, "", "", null, List.of())))
                    .when(controller).managedWorkers();
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
            when(level.getBlockState(sourcePos)).thenReturn(Blocks.BARREL.defaultBlockState());
            when(level.getBlockState(tablePos)).thenReturn(Blocks.CRAFTING_TABLE.defaultBlockState());
            when(level.getBlockEntity(sourcePos)).thenReturn(mock(CreativeCrateBlockEntity.class));
            when(level.getEntity(workerId)).thenReturn(worker);
            when(worker.isAlive()).thenReturn(true);
            when(worker.getVariant()).thenReturn(new PlayerMannequinVariant("steve", null, "", "", null, false));
            when(worker.position()).thenReturn(new Vec3(0, 64, 0));
            when(worker.blockPosition()).thenReturn(new BlockPos(0, 64, 0));
            when(worker.workerInventory()).thenReturn(inventory);
            when(worker.workerCurios()).thenReturn(tools);
            doAnswer(call -> {
                var stock = new java.util.LinkedHashMap<>(new WorkerInventoryEndpoint(workerId,
                        worker::blockPosition, inventory, level.registryAccess(), stack -> true, false).contents());
                new WorkerInventoryEndpoint(workerId, worker::blockPosition, tools, level.registryAccess(),
                        stack -> true, false).contents().forEach((key, count) -> stock.merge(key, count, Long::sum));
                return stock;
            }).when(pod).workerPlanningStock(any());
            when(worker.getItemBySlot(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
            var server = mock(MinecraftServer.class);
            var players = mock(PlayerList.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayer(playerId)).thenReturn(recipient);
            when(recipient.getUUID()).thenReturn(playerId);
            when(recipient.serverLevel()).thenReturn(level);
            when(recipient.level()).thenReturn(level);
            when(recipient.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            when(recipient.blockPosition()).thenReturn(new BlockPos(10, 64, 0));
            when(recipient.getInventory()).thenReturn(new Inventory(recipient));
            when(recipient.getAbilities()).thenReturn(new Abilities());
            linker = scoped(ContraptionNetworkLinkerData.class);
            var source = target(sourcePos, "create:creative_crate");
            var table = target(tablePos, "minecraft:crafting_table");
            targets.addAll(List.of(source, table));
            linker.when(() -> ContraptionNetworkLinkerData.readTargets(ItemStack.EMPTY)).thenReturn(targets);
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(source)).thenReturn("source");
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(table)).thenReturn("table");
            scoped(SimulatedHelper.class);
            scoped(com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore.class)
                    .when(() -> com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore.get(level))
                    .thenReturn(new com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore());
            collector = scoped(SubLevelBlockEntityCollector.class);
            access = scoped(WorkerContainerAccess.class);
            for(BlockPos pos : List.of(sourcePos, tablePos)){
                collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)).thenReturn(true);
                access.when(() -> WorkerContainerAccess.isLoaded(level, pos)).thenReturn(true);
            }
            var items = new BottomlessItemHandler(() -> new ItemStack(supplied));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, sourcePos, null)).thenReturn(List.of(items));
            var paths = scoped(WorkerPathing.class);
            var navigator = mock(WorkerPathing.LiveNavigator.class);
            paths.when(() -> WorkerPathing.liveNavigator(anyDouble(), anyBoolean())).thenReturn(navigator);
            paths.when(() -> WorkerPathing.liveNavigator(anyDouble(), anyBoolean(), any()))
                    .thenReturn(navigator);
            paths.when(() -> WorkerPathing.reachableInteractionPosition(eq(level), anyCollection(), any(), any()))
                    .thenReturn(new Vec3(0, 64, 0));
            paths.when(() -> WorkerPathing.reachableInteractionPosition(
                    eq(level), anyCollection(), any(), any(), any())).thenReturn(new Vec3(0, 64, 0));
            when(navigator.advance(eq(level), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(new WorkerPathing.NavigationStep(WorkerPathing.NavigationState.ARRIVED, new Vec3(0, 64, 0)));
            scoped(WorkerDeliveryTravel.class);
            scoped(PackageStyles.class).when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
        }

        void link(BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                  net.minecraft.world.level.block.entity.BlockEntity be){
            var target = target(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            targets.add(target);
            linker.when(() -> ContraptionNetworkLinkerData.nodeIdForTarget(target)).thenReturn("machine:" + pos.asLong());
            when(level.getBlockState(pos)).thenReturn(state);
            when(level.getBlockEntity(pos)).thenReturn(be);
            collector.when(() -> SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.isLoaded(level, pos)).thenReturn(true);
        }

        void recipes(List<RecipeHolder<?>> recipes){
            var manager = mock(RecipeManager.class);
            when(level.getRecipeManager()).thenReturn(manager);
            when(manager.getRecipes()).thenReturn(recipes);
            for(var holder : recipes) when(manager.byKey(holder.id())).thenReturn(Optional.of(holder));
        }

        Object request(Item res, int amount) throws Exception{
            return request(res, amount, true);
        }

        Object request(Item res, int amount, boolean craft) throws Exception{
            var receiver = mock(WorkerPodBlockEntity.class);
            List<WorkerWorkOrder> orders = new ArrayList<>();
            when(receiver.submit(anyList())).thenAnswer(call -> orders.addAll(call.getArgument(0)));
            when(receiver.compatibleWorkers(eq(WorkerResourceType.ITEM), anyCollection()))
                    .thenReturn(List.of(workerId));
            when(receiver.workerPlanningStock(workerId)).thenAnswer(call ->
            {
                var stock = new java.util.LinkedHashMap<>(new WorkerInventoryEndpoint(workerId,
                        worker::blockPosition, inventory, level.registryAccess(), stack -> true, false).contents());
                new WorkerInventoryEndpoint(UUID.randomUUID(), worker::blockPosition, tools,
                        level.registryAccess(), stack -> true, false).contents().forEach((key, count) ->
                        stock.merge(key, count, Long::sum));
                return stock;
            });
            when(receiver.submit(eq(workerId), anyList()))
                    .thenAnswer(call -> orders.addAll(call.getArgument(1)));
            try(var pods = mockStatic(WorkerPodBlockEntity.class)){
                pods.when(() -> WorkerPodBlockEntity.linkedPods(controller)).thenReturn(List.of(receiver));
                var failure = controller.requestItems(new WorkerItemRequest(UUID.randomUUID(),
                        BuiltInRegistries.ITEM.getKey(res), amount, craft, "", playerId));
                assertFalse(failure.failed(), failure.message());
            }
            CompoundTag tag = new CompoundTag();
            tag.put("Profile", new WorkerProfile(workerId, "Worker", WorkerProfile.Job.ANY, true).toTag());
            WorkerTaskQueue queue = new WorkerTaskQueue();
            queue.enqueueAll(orders);
            tag.put("Queue", queue.toTag());
            var type = Class.forName(WorkerPodBlockEntity.class.getName() + "$WorkerRuntime");
            var read = type.getDeclaredMethod("fromTag", CompoundTag.class);
            read.setAccessible(true);
            Object runtime = read.invoke(null, tag);
            long deadline = System.nanoTime() + 10_000_000_000L;
            while(craft && queue(runtime).current() != null && queue(runtime).current().lookingUpRecipe()){
                assertTrue(System.nanoTime() < deadline, "Recipe lookup did not finish");
                tick(runtime);
            }
            return runtime;
        }

        void finish(Object runtime) throws Exception{
            finish(runtime, false);
        }

        void finish(Object runtime, boolean reload) throws Exception{
            for(int idx = 0; idx < 256 && queue(runtime).current() != null; idx++){
                tick(runtime);
                if(reload){
                    var write = runtime.getClass().getDeclaredMethod("toTag");
                    var read = runtime.getClass().getDeclaredMethod("fromTag", CompoundTag.class);
                    write.setAccessible(true);
                    read.setAccessible(true);
                    runtime = read.invoke(null, write.invoke(runtime));
                }
            }
            var status = runtime.getClass().getDeclaredField("status");
            status.setAccessible(true);
            assertNull(queue(runtime).current(), status.get(runtime).toString());
        }

        void tick(Object runtime) throws Exception{
            var tick = WorkerPodBlockEntity.class.getDeclaredMethod("tickWorker", ServerLevel.class,
                    AdvancedContraptionControllerBlockEntity.class, runtime.getClass(), boolean.class);
            tick.setAccessible(true);
            var scheduler = DeferredWorkScheduler.forServer(level.getServer());
            long deadline = System.nanoTime() + 10_000_000_000L;
            var status = runtime.getClass().getDeclaredField("status");
            status.setAccessible(true);
            do{
                scheduler.tick();
                tick.invoke(pod, level, controller, runtime, true);
                if(!"Looking up recipe".equals(status.get(runtime))) break;
                assertTrue(System.nanoTime() < deadline, "Deferred recipe query did not finish");
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }while(true);
        }

        <T> MockedStatic<T> scoped(Class<T> type){
            MockedStatic<T> mock = mockStatic(type);
            mocks.add(mock);
            return mock;
        }

        @Override public void close(){
            DeferredWorkScheduler.forServer(level.getServer()).close();
            WorkerRecipeCatalog.invalidate();
            for(var mock : mocks.reversed()) mock.close();
        }
    }

    private static ContraptionNetworkLinkerData.LinkedTarget target(BlockPos pos, String id){
        return new ContraptionNetworkLinkerData.LinkedTarget(pos, null, id, id,
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
    }

    private static WorkerTaskQueue queue(Object runtime) throws Exception{
        var field = runtime.getClass().getDeclaredField("queue");
        field.setAccessible(true);
        return (WorkerTaskQueue) field.get(runtime);
    }

    private static int contents(ItemStackHandler inventory, Item item){
        int amount = 0;
        for(int idx = 0; idx < inventory.getSlots(); idx++){
            ItemStack stack = inventory.getStackInSlot(idx);
            if(stack.is(item)) amount += stack.getCount();
        }
        return amount;
    }

    private static RecipeHolder<?> recipe(String id, Item res, int amount, Item input, int slots){
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(slots <= 4);
        when(recipe.canCraftInDimensions(3, 3)).thenReturn(slots <= 9);
        when(recipe.getIngredients()).thenReturn(NonNullList.withSize(slots, Ingredient.of(input)));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(res, amount));
        when(recipe.matches(any(), any())).thenReturn(true);
        when(recipe.assemble(any(), any())).thenReturn(new ItemStack(res, amount));
        when(recipe.getRemainingItems(any())).thenAnswer(call -> NonNullList.withSize(((CraftingInput)call.getArgument(0)).size(), ItemStack.EMPTY));
        return new RecipeHolder<>(ResourceLocation.withDefaultNamespace(id), recipe);
    }
}
