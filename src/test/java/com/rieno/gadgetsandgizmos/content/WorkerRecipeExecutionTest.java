package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.worker.WorkerToolStorageEndpoint;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.content.logistics.box.PackageStyles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerRecipeExecutionTest{
    private static final WorkerResourceKey LOG = item("oak_log");
    private static final WorkerResourceKey PLANK = item("oak_planks");
    private static final WorkerResourceKey TABLE = item("crafting_table");

    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test void toolStorageCapabilitySuppliesItemsWithoutOpeningItsScreen(){
        var server = mock(net.minecraft.server.level.ServerLevel.class);
        when(server.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var worker = mock(PlayerMannequinEntity.class);
        when(worker.getUUID()).thenReturn(UUID.randomUUID());
        when(worker.blockPosition()).thenReturn(BlockPos.ZERO);
        var tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.STICK));
        when(worker.workerTools()).thenReturn(tools);
        var handler = mock(net.neoforged.neoforge.items.IItemHandler.class);
        when(handler.getSlots()).thenReturn(1);
        when(handler.getStackInSlot(0)).thenReturn(new ItemStack(Items.IRON_INGOT, 3));
        when(handler.extractItem(0, 3, true)).thenReturn(new ItemStack(Items.IRON_INGOT, 3));
        when(handler.extractItem(0, 2, true)).thenReturn(new ItemStack(Items.IRON_INGOT, 2));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.itemHandler(any(ItemStack.class))).thenReturn(handler);
            var endpoint = new WorkerToolStorageEndpoint(server, worker, false);
            assertTrue(endpoint.isAvailable());
            assertEquals(3L, endpoint.available(item("iron_ingot")));
            assertEquals(2L, endpoint.extract(item("iron_ingot"), 2L, true).amount());
            verify(handler, never()).extractItem(anyInt(), anyInt(), eq(false));
        }
    }

    @Test void toolStorageCapabilitySuppliesFluidAndPersistsItsContainer(){
        var server = mock(net.minecraft.server.level.ServerLevel.class);
        when(server.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var worker = mock(PlayerMannequinEntity.class);
        when(worker.getUUID()).thenReturn(UUID.randomUUID());
        when(worker.blockPosition()).thenReturn(BlockPos.ZERO);
        var tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.BUCKET));
        when(worker.workerTools()).thenReturn(tools);
        var handler = mock(net.neoforged.neoforge.fluids.capability.IFluidHandlerItem.class);
        var lava = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
        when(handler.getTanks()).thenReturn(1);
        when(handler.getFluidInTank(0)).thenReturn(lava);
        when(handler.drain(any(net.neoforged.neoforge.fluids.FluidStack.class),
                eq(net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)))
                .thenReturn(lava);
        when(handler.drain(any(net.neoforged.neoforge.fluids.FluidStack.class),
                eq(net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)))
                .thenReturn(new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.LAVA, 500));
        when(handler.getContainer()).thenReturn(new ItemStack(Items.BUCKET));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.fluidHandler(any(ItemStack.class))).thenReturn(handler);
            var endpoint = new WorkerToolStorageEndpoint(server, worker, false);
            var resource = new WorkerResourceKey(WorkerResourceType.FLUID,
                    ResourceLocation.withDefaultNamespace("lava"));
            assertEquals(1000L, endpoint.available(resource));
            assertEquals(500L, endpoint.extract(resource, 500L, false).amount());
            assertEquals(Items.BUCKET, tools.getStackInSlot(0).getItem());
        }
    }

    @Test void recognizesFilledBucketAsTankAndEmptyContainerRecipe(){
        // Fluid types are registered by NeoForge in a running game, not in this unit-test bootstrap.
        try(var fluidUtil = mockStatic(net.neoforged.neoforge.fluids.FluidUtil.class)){
            fluidUtil.when(() -> net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(
                    any(net.neoforged.neoforge.fluids.FluidStack.class))).thenAnswer(call -> {
                var stack = (net.neoforged.neoforge.fluids.FluidStack)call.getArgument(0);
                return stack.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                        ? new ItemStack(Items.LAVA_BUCKET) : ItemStack.EMPTY;
            });
            var route = AdvancedContraptionControllerBlockEntity.filledContainerRecipe(
                    ResourceLocation.withDefaultNamespace("lava_bucket"));
            assertNotNull(route);
            assertEquals(ResourceLocation.withDefaultNamespace("lava"), route.fluidId());
            assertEquals(ResourceLocation.withDefaultNamespace("bucket"), route.emptyContainerId());
            assertEquals(1000, route.millibuckets());
            assertNull(AdvancedContraptionControllerBlockEntity.filledContainerRecipe(
                    ResourceLocation.withDefaultNamespace("iron_ingot")));
        }
    }

    @Test void fluidContainerOrderVisitsEmptyItemBeforeTank() throws Exception{
        var ctx = new Fixture();
        WorkerResourceKey bucket = item("bucket");
        WorkerResourceKey water = new WorkerResourceKey(WorkerResourceType.FLUID,
                ResourceLocation.withDefaultNamespace("water"));
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        WorkerWorkOrder order = new WorkerWorkOrder(id,
                new WorkerTask(id, "Fill bucket", water, 1, 0, 0, true),
                WorkerWorkOrder.Mode.FILL_CONTAINER, null, recipient, null, bucket, 1000);
        WorkerStorageEndpoint container = mock(WorkerStorageEndpoint.class);
        WorkerStorageEndpoint tank = mock(WorkerStorageEndpoint.class);
        WorkerEndpoint player = mock(WorkerEndpoint.class);
        when(container.id()).thenReturn(UUID.randomUUID());
        when(container.position()).thenReturn(BlockPos.ZERO);
        when(container.isWorkerRecipeSource()).thenReturn(true);
        when(container.canExtract(bucket)).thenReturn(true);
        when(container.available(bucket)).thenReturn(1L);
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", new ItemStack(Items.BUCKET).saveOptional(ctx.level.registryAccess()));
        when(container.extractMatchingItem(eq(bucket), eq(1L), any(), eq(true)))
                .thenReturn(new WorkerResourcePacket(bucket, 1L, payload));
        when(tank.id()).thenReturn(UUID.randomUUID());
        when(tank.position()).thenReturn(new BlockPos(4, 0, 0));
        when(tank.isWorkerRecipeSource()).thenReturn(true);
        when(tank.canExtract(water)).thenReturn(true);
        when(tank.available(water)).thenReturn(1000L);
        when(tank.extract(water, 1000L, true)).thenReturn(new WorkerResourcePacket(water, 1000L, null));
        when(player.id()).thenReturn(recipient);
        when(player.isAvailable()).thenReturn(true);
        when(player.insert(any(WorkerResourcePacket.class), eq(true))).thenReturn(1L);
        var handler = mock(net.neoforged.neoforge.fluids.capability.IFluidHandlerItem.class);
        when(handler.fill(any(net.neoforged.neoforge.fluids.FluidStack.class), any())).thenReturn(1000);
        when(handler.getContainer()).thenReturn(new ItemStack(Items.WATER_BUCKET));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.fluidHandler(any(ItemStack.class))).thenReturn(handler);
            var assign = WorkerPodBlockEntity.class.getDeclaredMethod("fluidContainerAssignment",
                    WorkerWorkOrder.class, List.class, PlayerMannequinEntity.class);
            assign.setAccessible(true);
            var route = (WorkerDispatcher.WorkAssignment)assign.invoke(ctx.pod, order,
                    List.of(container, tank, player), ctx.worker);
            assertNotNull(route);
            assertSame(container, route.source());
            assertSame(tank, route.processor());
            assertSame(player, route.target());
            verify(container, never()).extractMatchingItem(eq(bucket), eq(1L), any(), eq(false));
            verify(tank, never()).extract(eq(water), anyLong(), eq(false));
        }
    }

    @Test void fluidContainerOrderCanUseWirelessFluidSource() throws Exception{
        var ctx = new Fixture();
        WorkerResourceKey bucket = item("bucket");
        WorkerResourceKey lava = new WorkerResourceKey(WorkerResourceType.FLUID,
                ResourceLocation.withDefaultNamespace("lava"));
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        WorkerWorkOrder order = new WorkerWorkOrder(id,
                new WorkerTask(id, "Fill bucket", lava, 1, 0, 0, true),
                WorkerWorkOrder.Mode.FILL_CONTAINER, null, recipient, null, bucket, 1000);
        WorkerStorageEndpoint container = mock(WorkerStorageEndpoint.class);
        WorkerInventoryEndpoint wireless = mock(WorkerInventoryEndpoint.class);
        WorkerEndpoint player = mock(WorkerEndpoint.class);
        when(container.id()).thenReturn(UUID.randomUUID());
        when(container.position()).thenReturn(BlockPos.ZERO);
        when(container.isWorkerRecipeSource()).thenReturn(true);
        when(container.canExtract(bucket)).thenReturn(true);
        when(container.available(bucket)).thenReturn(1L);
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", new ItemStack(Items.BUCKET).saveOptional(ctx.level.registryAccess()));
        when(container.extractMatchingItem(eq(bucket), eq(1L), any(), eq(true)))
                .thenReturn(new WorkerResourcePacket(bucket, 1L, payload));
        when(wireless.position()).thenReturn(new BlockPos(4, 0, 0));
        when(wireless.canExtract(lava)).thenReturn(true);
        when(wireless.available(lava)).thenReturn(1000L);
        when(wireless.extract(lava, 1000L, true))
                .thenReturn(new WorkerResourcePacket(lava, 1000L, null));
        when(player.id()).thenReturn(recipient);
        when(player.isAvailable()).thenReturn(true);
        when(player.insert(any(WorkerResourcePacket.class), eq(true))).thenReturn(1L);
        var handler = mock(net.neoforged.neoforge.fluids.capability.IFluidHandlerItem.class);
        when(handler.fill(any(net.neoforged.neoforge.fluids.FluidStack.class), any())).thenReturn(1000);
        when(handler.getContainer()).thenReturn(new ItemStack(Items.LAVA_BUCKET));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.fluidHandler(any(ItemStack.class))).thenReturn(handler);
            var assign = WorkerPodBlockEntity.class.getDeclaredMethod("fluidContainerAssignment",
                    WorkerWorkOrder.class, List.class, PlayerMannequinEntity.class);
            assign.setAccessible(true);
            var route = (WorkerDispatcher.WorkAssignment)assign.invoke(ctx.pod, order,
                    List.of(container, wireless, player), ctx.worker);
            assertNotNull(route);
            assertSame(container, route.source());
            assertSame(wireless, route.processor());
            assertSame(player, route.target());
        }
    }

    // A press job must use its planned output even when the recipe manager contains inventory-based saw recipes
    @Test void plannedProcessorWaitDoesNotProbeUnrelatedRecipes() throws Exception{
        var ctx = new Fixture();
        var id = UUID.randomUUID();
        var plan = new WorkerRecipePlan(ResourceLocation.parse("test:pressing"), ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(LOG, 1)), TABLE, 2);
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Press", LOG, 2, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, ctx.storage.id(), ctx.storage.id(), ctx.storage.id(), TABLE, 2).withRecipePlan(plan);
        Object runtime = runtime(order);
        set(runtime, "assignment", new WorkerDispatcher.WorkAssignment(ctx.storage, ctx.storage, ctx.storage, 1));
        set(runtime, "processingInputAmount", 64L);
        set(runtime, "recipeInputIndex", 1);
        when(ctx.storage.available(TABLE)).thenReturn(2L);
        when(ctx.storage.extract(TABLE, 2, false)).thenReturn(new WorkerResourcePacket(TABLE, 2, null));
        var cutting = mock(com.simibubi.create.content.kinetics.saw.CuttingRecipe.class, CALLS_REAL_METHODS);
        try(var finder = mockStatic(com.simibubi.create.foundation.recipe.RecipeFinder.class);
            var simulated = mockStatic(SimulatedHelper.class); var packages = mockStatic(PackageStyles.class)){
            finder.when(() -> com.simibubi.create.foundation.recipe.RecipeFinder.get(isNull(), eq(ctx.level), any()))
                    .thenReturn(List.of(new RecipeHolder<>(ResourceLocation.parse("test:unrelated_cutting"), cutting)));
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            var collect = WorkerPodBlockEntity.class.getDeclaredMethod("collectProcessorOutput", runtime.getClass(), PlayerMannequinEntity.class);
            collect.setAccessible(true);
            collect.invoke(ctx.pod, runtime, ctx.worker);
            assertEquals(2, cargo(runtime).amount());
            assertEquals(TABLE, cargo(runtime).resource());
            finder.verifyNoInteractions();
            verify(ctx.storage).extract(TABLE, 2, false);
        }
    }

    @Test void portableCraftingGridCanBeRecoveredButNeverUsedAsStorage(){
        com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.registerPortableCraftingTool(
                BuiltInRegistries.ITEM.getKey(Items.CRAFTING_TABLE));
        var server = mock(net.minecraft.server.level.ServerLevel.class);
        when(server.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var worker = mock(PlayerMannequinEntity.class);
        when(worker.getUUID()).thenReturn(UUID.randomUUID());
        when(worker.blockPosition()).thenReturn(BlockPos.ZERO);
        var tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE));
        when(worker.workerTools()).thenReturn(tools);
        var handler = mock(net.neoforged.neoforge.items.IItemHandler.class);
        when(handler.getSlots()).thenReturn(1);
        when(handler.getStackInSlot(0)).thenReturn(new ItemStack(Items.IRON_INGOT, 2));
        when(handler.extractItem(0, 2, true)).thenReturn(new ItemStack(Items.IRON_INGOT, 2));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.itemHandler(any(ItemStack.class))).thenReturn(handler);
            var endpoint = new WorkerToolStorageEndpoint(server, worker, false);
            assertEquals(2L, endpoint.extract(item("iron_ingot"), 2L, true).amount());
            assertEquals(0L, endpoint.space(item("iron_ingot")));
            assertEquals(0L, endpoint.insert(new WorkerResourcePacket(item("iron_ingot"), 1L, null), false));
            verify(handler, never()).insertItem(anyInt(), any(ItemStack.class), anyBoolean());
        }
    }

    @Test void equippedCraftingToolCompletesOneBatchWithoutFillingItsGrid() throws Exception{
        var ctx = new Fixture();
        com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.registerPortableCraftingTool(TABLE.id());
        var tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE));
        when(ctx.worker.workerCurios()).thenReturn(tools);
        ctx.inventory.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 4));
        WorkerWorkOrder original = ctx.tableOrder();
        WorkerRecipePlan plan = new WorkerRecipePlan(original.recipePlan().recipeId(), TABLE.id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 4),
                        new WorkerRecipePlan.Input(TABLE, 1)), TABLE, 1);
        WorkerWorkOrder order = original.withRecipePlan(plan);
        Object runtime = runtime(order);
        var stock = new WorkerInventoryEndpoint(order.task().id(), ctx.worker::blockPosition,
                ctx.inventory, ctx.level.registryAccess(), stack -> true, false);
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("craftWithPortableTool",
                AdvancedContraptionControllerBlockEntity.class, runtime.getClass(),
                WorkerWorkOrder.class, List.class, WorkerInventoryEndpoint.class,
                WorkerInventoryEndpoint.class, List.class, PlayerMannequinEntity.class);
        method.setAccessible(true);
        try(var packages = mockStatic(com.simibubi.create.content.logistics.box.PackageStyles.class)){
            packages.when(com.simibubi.create.content.logistics.box.PackageStyles::getDefaultBox)
                    .thenReturn(new ItemStack(Items.CHEST));
            method.invoke(ctx.pod, ctx.controller, runtime, order, List.of(ctx.storage), stock,
                    null, List.of(ctx.storage, stock), ctx.worker);
        }
        assertEquals(0L, stock.available(PLANK));
        assertEquals(Items.CRAFTING_TABLE, tools.getStackInSlot(0).getItem());
        assertEquals(TABLE, cargo(runtime).resource());
        assertEquals(1L, cargo(runtime).amount());
    }

    @Test void portableCraftCollectsLinkedIngredientsBeforeTheSingleCraft() throws Exception{
        var ctx = new Fixture();
        com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.registerPortableCraftingTool(TABLE.id());
        var tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.CRAFTING_TABLE));
        when(ctx.worker.workerCurios()).thenReturn(tools);
        when(ctx.storage.available(PLANK)).thenReturn(4L);
        WorkerWorkOrder original = ctx.tableOrder();
        WorkerRecipePlan plan = new WorkerRecipePlan(original.recipePlan().recipeId(), TABLE.id(),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 4),
                        new WorkerRecipePlan.Input(TABLE, 1)), TABLE, 1);
        WorkerWorkOrder order = original.withRecipePlan(plan);
        Object runtime = runtime(order);
        var stock = new WorkerInventoryEndpoint(order.task().id(), ctx.worker::blockPosition,
                ctx.inventory, ctx.level.registryAccess(), stack -> true, false);
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("craftWithPortableTool",
                AdvancedContraptionControllerBlockEntity.class, runtime.getClass(),
                WorkerWorkOrder.class, List.class, WorkerInventoryEndpoint.class,
                WorkerInventoryEndpoint.class, List.class, PlayerMannequinEntity.class);
        method.setAccessible(true);
        method.invoke(ctx.pod, ctx.controller, runtime, order, List.of(ctx.storage), stock,
                null, List.of(ctx.storage, stock), ctx.worker);
        assertEquals(WorkerWorkOrder.Mode.TRANSFER, queue(runtime).current().mode());
        assertEquals(PLANK, queue(runtime).current().task().resource());
        assertEquals(4L, queue(runtime).current().task().requestedAmount());
        assertEquals(order.id(), queue(runtime).planned().getFirst().id());
        assertNull(cargo(runtime));
    }

    @Test void missingNuggetsSwitchUntouchedIngotOrderToRawCopperOnce() throws Exception{
        var ctx = new Fixture();
        var nugget = new WorkerResourceKey(WorkerResourceType.ITEM,
                ResourceLocation.parse("create:copper_nugget"));
        var raw = item("raw_copper");
        var ingot = item("copper_ingot");
        UUID id = UUID.randomUUID();
        var nuggetPlan = new WorkerRecipePlan(ResourceLocation.parse("create:copper_ingot_from_nuggets"),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(nugget, 9)), ingot, 1);
        var smeltPlan = new WorkerRecipePlan(ResourceLocation.parse("minecraft:copper_ingot_from_smelting_raw_copper"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(raw, 1)), ingot, 1);
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Copper ingot", nugget, 1, 0, 0, true),
                WorkerWorkOrder.Mode.AUTO_CRAFT, null, null, null, ingot, 1).withRecipePlan(nuggetPlan);
        var replacement = new WorkerWorkOrder(id, new WorkerTask(id, "Copper ingot", raw, 1, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, null, null, null, ingot, 1).withRecipePlan(smeltPlan);
        Object runtime = runtime(order);
        when(ctx.storage.available(nugget)).thenReturn(0L);
        when(ctx.storage.available(raw)).thenReturn(370L);
        doReturn(List.of(replacement)).when(ctx.controller).retryWorkerRecipe(eq(order), anyMap());
        var switchRoute = WorkerPodBlockEntity.class.getDeclaredMethod("switchUnavailableRecipeRoute",
                AdvancedContraptionControllerBlockEntity.class, runtime.getClass(),
                WorkerWorkOrder.class, List.class);
        switchRoute.setAccessible(true);
        assertEquals(true, switchRoute.invoke(ctx.pod, ctx.controller, runtime, order, List.of(ctx.storage)));
        assertEquals(smeltPlan, queue(runtime).current().recipePlan());
        assertEquals(raw, queue(runtime).current().task().resource());
        assertEquals(false, switchRoute.invoke(ctx.pod, ctx.controller, runtime, order, List.of(ctx.storage)));
        assertEquals(id, get(reload(runtime), "unavailableRouteReviewed"));
        verify(ctx.controller, times(1)).retryWorkerRecipe(eq(order), anyMap());
    }

    // A machine adapter cannot hand a worker a result without removing it from its output port.
    @Test void rejectsClaimedOutputThatMachineDidNotLose() throws Exception{
        var ctx = new Fixture();
        var id = UUID.randomUUID();
        var plan = new WorkerRecipePlan(ResourceLocation.parse("test:smelting"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(LOG, 7)), TABLE, 7);
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Smelt", LOG, 7, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, ctx.storage.id(), ctx.storage.id(), ctx.storage.id(), TABLE, 7)
                .withRecipePlan(plan);
        Object runtime = runtime(order);
        set(runtime, "assignment", new WorkerDispatcher.WorkAssignment(ctx.storage, ctx.storage, ctx.storage, 7));
        set(runtime, "recipeInputIndex", 1);
        when(ctx.storage.isProcessingMachine()).thenReturn(true);
        when(ctx.storage.available(TABLE)).thenReturn(7L);
        when(ctx.storage.extract(TABLE, 7, false)).thenReturn(new WorkerResourcePacket(TABLE, 7, null));
        var collect = WorkerPodBlockEntity.class.getDeclaredMethod("collectProcessorOutput", runtime.getClass(),
                PlayerMannequinEntity.class);
        collect.setAccessible(true);
        collect.invoke(ctx.pod, runtime, ctx.worker);
        assertNull(cargo(runtime));
        assertEquals("Processor output extraction was not confirmed", get(runtime, "status"));
        assertNotNull(queue(runtime).current());
    }

    // Treat a machine's alternate item as usable when the next recipe ingredient accepts both variants
    @Test void acceptsTaggedIntermediateAndUsesItInTheNextPlan() throws Exception{
        var ctx = new Fixture();
        var expected = item("iron_ingot");
        var alternate = item("gold_ingot");
        var consumerId = ResourceLocation.parse("test:tagged_consumer");
        RecipeHolder<?> consumer = recipe("tagged_consumer", new ItemStack(Items.CRAFTING_TABLE),
                Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT));
        when(ctx.level.getRecipeManager().byKey(consumerId)).thenReturn(Optional.of(consumer));
        UUID firstId = UUID.randomUUID();
        var producerPlan = new WorkerRecipePlan(ResourceLocation.parse("test:press"),
                ResourceLocation.parse("create:pressing"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(LOG, 1)), expected, 1);
        var producer = new WorkerWorkOrder(firstId, new WorkerTask(firstId, "Press", LOG, 1, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, null, null, null, expected, 1).withRecipePlan(producerPlan);
        UUID secondId = UUID.randomUUID();
        var consumerPlan = new WorkerRecipePlan(consumerId, ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(expected, 1)), TABLE, 1);
        var next = new WorkerWorkOrder(secondId, new WorkerTask(secondId, "Craft", expected, 1, 0, 0, true),
                WorkerWorkOrder.Mode.AUTO_CRAFT, null, null, null, TABLE, 1).withRecipePlan(consumerPlan);
        Object runtime = runtime(producer);
        queue(runtime).enqueue(next);
        set(runtime, "assignment", new WorkerDispatcher.WorkAssignment(ctx.storage, ctx.storage, ctx.storage, 1));
        set(runtime, "recipeInputIndex", 1);
        when(ctx.storage.available(alternate)).thenReturn(1L);
        UUID endpointId = ctx.storage.id();
        when(ctx.storage.snapshot()).thenReturn(new WorkerEndpointSnapshot(endpointId, null, BlockPos.ZERO,
                "Press", "create:depot", true, true,
                List.of(new WorkerEndpointSnapshot.ResourceAmount(alternate, 1, 64))));
        when(ctx.storage.extract(alternate, 1, false)).thenReturn(new WorkerResourcePacket(alternate, 1, null));
        try(var packages = mockStatic(PackageStyles.class)){
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            var collect = WorkerPodBlockEntity.class.getDeclaredMethod("collectProcessorOutput", runtime.getClass(),
                    PlayerMannequinEntity.class);
            collect.setAccessible(true);
            collect.invoke(ctx.pod, runtime, ctx.worker);
            assertEquals(alternate, cargo(runtime).resource());
        }
        queue(runtime).completeCurrent();
        set(runtime, "carried", null);
        set(runtime, "recipeInputIndex", 0);
        var select = WorkerPodBlockEntity.class.getDeclaredMethod("selectTaggedRecipeInput", runtime.getClass(),
                WorkerWorkOrder.class, List.class);
        select.setAccessible(true);
        WorkerWorkOrder updated = (WorkerWorkOrder) select.invoke(ctx.pod, runtime, next, List.of(ctx.storage));
        assertEquals(alternate, updated.recipePlan().inputs().getFirst().resource());
        assertEquals(alternate, queue(runtime).current().recipePlan().inputs().getFirst().resource());
    }

    // Collect later ingredients into normal worker inventory during the first source visit
    @Test void prefetchesOtherRecipeInputsFromTheCurrentSource() throws Exception{
        var ctx = new Fixture();
        UUID id = UUID.randomUUID();
        var plan = new WorkerRecipePlan(ResourceLocation.parse("test:mixed"),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(LOG, 1), new WorkerRecipePlan.Input(PLANK, 2)), TABLE, 1);
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Mixed", LOG, 8, 0, 0, true),
                WorkerWorkOrder.Mode.AUTO_CRAFT, null, null, null, TABLE, 1).withRecipePlan(plan);
        Object runtime = runtime(order);
        set(runtime, "assignment", new WorkerDispatcher.WorkAssignment(ctx.storage, ctx.storage, ctx.storage, 8));
        set(runtime, "recipeBatchCount", 8L);
        when(ctx.storage.available(PLANK)).thenReturn(16L);
        when(ctx.storage.extract(eq(PLANK), eq(16L), anyBoolean()))
                .thenReturn(new WorkerResourcePacket(PLANK, 16, null));
        var prefetch = WorkerPodBlockEntity.class.getDeclaredMethod("prefetchRecipeInputs", runtime.getClass(),
                WorkerWorkOrder.class, PlayerMannequinEntity.class);
        prefetch.setAccessible(true);
        prefetch.invoke(ctx.pod, runtime, order, ctx.worker);
        assertEquals(16, ctx.inventory.getStackInSlot(0).getCount());
        assertEquals(Items.OAK_PLANKS, ctx.inventory.getStackInSlot(0).getItem());
        verify(ctx.storage).extract(PLANK, 16L, false);
    }

    // Older graph orders still match saw inputs safely and select a machine filter without changing a shared recipe
    @Test void legacyProcessingUsesTypedMatchingAndTheMachineFilter() throws Exception{
        var ctx = new Fixture();
        var id = UUID.randomUUID();
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Process", LOG, 3, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, ctx.storage.id(), ctx.storage.id(), ctx.storage.id(), TABLE, 6);
        var cutting = mock(com.simibubi.create.content.kinetics.saw.CuttingRecipe.class, CALLS_REAL_METHODS);
        var ingredients = com.simibubi.create.content.processing.recipe.ProcessingRecipe.class.getDeclaredField("ingredients");
        ingredients.setAccessible(true);
        ingredients.set(cutting, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG)));
        doReturn(NonNullList.create()).when(cutting).getFluidIngredients();
        doReturn(List.of(new ItemStack(Items.CRAFTING_TABLE, 2))).when(cutting).getRollableResultsAsItemStacks();
        doReturn(RecipeType.SMELTING).when(cutting).getType();
        when(ctx.storage.selectRecipeResult(any())).thenReturn(true);
        var recipeId = ResourceLocation.parse("test:legacy_cutting");
        try(var finder = mockStatic(com.simibubi.create.foundation.recipe.RecipeFinder.class)){
            finder.when(() -> com.simibubi.create.foundation.recipe.RecipeFinder.get(isNull(), eq(ctx.level), any()))
                    .thenReturn(List.of(new RecipeHolder<>(recipeId, cutting)));
            var amount = WorkerPodBlockEntity.class.getDeclaredMethod("recipeOutputAmount", WorkerWorkOrder.class, long.class);
            amount.setAccessible(true);
            assertEquals(6L, amount.invoke(ctx.pod, order, 3L));
            var prepare = WorkerPodBlockEntity.class.getDeclaredMethod("prepareSelectedProcessorResult",
                    WorkerWorkOrder.class, WorkerResourcePacket.class, WorkerEndpoint.class);
            prepare.setAccessible(true);
            assertEquals(true, prepare.invoke(ctx.pod, order, new WorkerResourcePacket(LOG, 3, null), ctx.storage));
            verify(ctx.storage).selectRecipeResult(argThat(plan -> plan.recipeId().equals(recipeId)
                    && plan.result().equals(TABLE) && plan.resultAmount() == 2));
            verify(cutting, never()).enforceNextResult(any());
        }
    }

    // Carry an unfinished assembly back after reload without supplying all ingredients a second time
    @Test void preservesAssemblyRecirculationAcrossReload() throws Exception{
        var ctx = new Fixture();
        var id = UUID.randomUUID();
        var plan = new WorkerRecipePlan(TABLE.id(), ResourceLocation.parse("create:sequenced_assembly"),
                WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(LOG, 1)), TABLE, 1);
        var order = new WorkerWorkOrder(id, new WorkerTask(id, "Assembly", LOG, 1, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, ctx.storage.id(), ctx.storage.id(), ctx.storage.id(), TABLE, 1).withRecipePlan(plan);
        Object runtime = runtime(order);
        var assignment = new WorkerDispatcher.WorkAssignment(ctx.storage, ctx.storage, ctx.storage, 1);
        set(runtime, "assignment", assignment);
        set(runtime, "recipeInputIndex", 1);
        set(runtime, "cargoSourceEndpoint", ctx.storage.id());
        set(runtime, "cargoProcessorEndpoint", ctx.storage.id());
        set(runtime, "cargoTargetEndpoint", ctx.storage.id());
        var packet = new WorkerResourcePacket(PLANK, 1, null);
        when(ctx.storage.recirculatingRecipeResult(eq(plan), any())).thenReturn(packet);
        when(ctx.storage.selectRecipeResult(plan)).thenReturn(true);
        try(var simulated = mockStatic(SimulatedHelper.class); var packages = mockStatic(PackageStyles.class);
            var storage = mockStatic(WorkerStorageEndpoint.class)){
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            var collect = WorkerPodBlockEntity.class.getDeclaredMethod("collectProcessorOutput", runtime.getClass(), PlayerMannequinEntity.class);
            collect.setAccessible(true);
            collect.invoke(ctx.pod, runtime, ctx.worker);
            assertEquals(PLANK, cargo(runtime).resource());
            runtime = reload(runtime);
            assertEquals(true, get(runtime, "recirculatingRecipe"));
            invoke(ctx.pod, "resumeCargo", runtime, ctx.controller, ctx.worker);
            arrive(ctx, runtime);
            assertNull(cargo(runtime));
            assertEquals(false, get(runtime, "recirculatingRecipe"));
            assertEquals(1, get(runtime, "recipeInputIndex"));
            assertEquals("WAITING_PROCESSOR_OUTPUT", get(runtime, "stage").toString());
            verify(ctx.storage).insert(argThat(val -> val.resource().equals(PLANK)), eq(false));
        }
    }

    private static Object get(Object runtime, String name) throws Exception{
        var field = runtime.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(runtime);
    }

    private static void set(Object runtime, String name, Object val) throws Exception{
        var field = runtime.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(runtime, val);
    }

    // Compile the real addon recipe adapter and preserve both orders in the submitted job
    @Test void submitsPlanksBeforeTableFromLogsOnly(){
        var ctx = new Fixture();
        var receiver = mock(WorkerPodBlockEntity.class);
        UUID workerId = UUID.randomUUID();
        doReturn(List.of(new WorkerStatusSnapshot(workerId, "Worker", WorkerProfile.Job.ANY,
                true, null, BlockPos.ZERO, "", "", null, List.of())))
                .when(ctx.controller).managedWorkers();
        when(receiver.compatibleWorkers(eq(WorkerResourceType.ITEM), anyCollection()))
                .thenReturn(List.of(workerId));
        when(receiver.submit(eq(workerId), anyList())).thenReturn(true);
        try(var storage = mockStatic(WorkerStorageEndpoint.class); var pods = mockStatic(WorkerPodBlockEntity.class)){
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            pods.when(() -> WorkerPodBlockEntity.linkedPods(ctx.controller)).thenReturn(List.of(receiver));
            var request = new WorkerItemRequest(UUID.randomUUID(), TABLE.id(), 1, true, "", UUID.randomUUID());
            ctx.controller.requestItems(request);
            ArgumentCaptor<List<WorkerWorkOrder>> submitted = ArgumentCaptor.forClass(List.class);
            verify(receiver).submit(eq(workerId), submitted.capture());
            assertEquals(List.of(PLANK, TABLE), submitted.getValue().stream().map(WorkerWorkOrder::outputResource).toList());
            assertEquals(4, submitted.getValue().getFirst().task().requestedAmount());
            assertEquals(request.id(), submitted.getValue().getLast().id());
            assertEquals(request.destinationId(), submitted.getValue().getLast().destinationEndpointId());
        }
    }

    // Compile the complete recipe tree through both direct and sequenced graph craft nodes
    @Test void graphCraftKeepsEveryPrerequisiteAfterMoveItems() throws Exception{
        var ctx = new Fixture();
        var graph = new AdvancedGraphDocument.FunctionGraph("workers", "Workers");
        var move = new AdvancedGraphDocument.Node("move", "worker_move_items", "Move", 0, 0, new CompoundTag());
        var craft = new AdvancedGraphDocument.Node("craft", "worker_craft", "Craft", 0, 0, new CompoundTag());
        CompoundTag payload = new CompoundTag();
        payload.putString("Value", TABLE.id().toString());
        CompoundTag value = new CompoundTag();
        value.put("Payload", payload);
        CompoundTag defaults = new CompoundTag();
        defaults.put("items", value);
        CompoundTag data = new CompoundTag();
        data.put("Defaults", defaults);
        var filter = new AdvancedGraphDocument.Node("result", "worker_item_filter", "Result", 0, 0, data);
        graph.nodes().addAll(List.of(move, craft, filter));
        graph.edges().add(new AdvancedGraphDocument.Edge("next", "move", "complete", "craft", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("output", "result", "filter", "craft", "result_item_filter"));
        var request = new WorkerTaskRequest(UUID.randomUUID(), "Table", Set.of(), 0,
                WorkerTaskRequest.InterruptPolicy.QUEUE, true, new CompoundTag());
        var compile = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod("workerGraphOrder",
                AdvancedGraphDocument.FunctionGraph.class, AdvancedGraphDocument.Node.class, WorkerTaskRequest.class);
        compile.setAccessible(true);
        try(var storage = mockStatic(WorkerStorageEndpoint.class);
            var pods = mockStatic(WorkerPodBlockEntity.class)){
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            pods.when(() -> WorkerPodBlockEntity.linkedPods(ctx.controller)).thenReturn(List.of());
            for(var start : List.of(move, craft)){
                Object compiled = compile.invoke(ctx.controller, graph, start, request);
                assertNotNull(compiled);
                var orders = compiled.getClass().getDeclaredMethod("orders");
                orders.setAccessible(true);
                List<WorkerWorkOrder> chain = (List<WorkerWorkOrder>) orders.invoke(compiled);
                assertEquals(List.of(PLANK, TABLE), chain.stream().map(WorkerWorkOrder::outputResource).toList());
                assertEquals(LOG, chain.getFirst().recipePlan().inputs().getFirst().resource());
            }
            CompoundTag target = new CompoundTag();
            target.putString("NodeId", "worker:endpoint:" + ctx.storage.id());
            CompoundTag targets = new CompoundTag();
            targets.put("source", target);
            craft.data().put("WorkerTargets", targets);
            Object compiled = compile.invoke(ctx.controller, graph, craft, request);
            assertNotNull(compiled);
            var orders = compiled.getClass().getDeclaredMethod("orders");
            orders.setAccessible(true);
            List<WorkerWorkOrder> chain = (List<WorkerWorkOrder>)orders.invoke(compiled);
            assertTrue(chain.stream().allMatch(order -> ctx.storage.id().equals(order.sourceEndpointId())));
            target.putString("NodeId", "worker:endpoint:unavailable");
            assertNull(compile.invoke(ctx.controller, graph, craft, request));
        }
    }

    // Repair a persisted table-only order instead of waiting forever for planks
    @Test void restoredTableOrderCraftsItsMissingPlanksBeforeResuming() throws Exception{
        var ctx = new Fixture();
        Object runtime = runtime(ctx.tableOrder());
        try(var storage = mockStatic(WorkerStorageEndpoint.class); var simulated = mockStatic(SimulatedHelper.class);
            var packages = mockStatic(PackageStyles.class)){
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            WorkerTaskQueue queue = queue(runtime);
            assertEquals(PLANK, queue.current().outputResource());
            assertEquals(LOG, queue.current().recipePlan().inputs().getFirst().resource());
            assertEquals(TABLE, queue.planned().getFirst().outputResource());
            assertEquals(4, queue.current().task().requestedAmount());
            runtime = reload(runtime);
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            arrive(ctx, runtime);
            assertEquals(TABLE, queue(runtime).current().outputResource());
            assertEquals(4, cargo(runtime).amount());
            restoreProgress(runtime);
            invoke(ctx.pod, "useCarriedWorkerCraftInput", runtime, ctx.controller, ctx.worker);
            assertEquals(TABLE, cargo(runtime).resource());
            assertEquals(1, cargo(runtime).amount());
            arrive(ctx, runtime);
            assertNull(queue(runtime).current());
            verify(ctx.storage).extract(LOG, 1, false);
            verify(ctx.storage).insert(argThat(packet -> packet.resource().equals(TABLE) && packet.amount() == 1), eq(false));
            for(int idx = 0; idx < ctx.inventory.getSlots(); idx++) assertTrue(ctx.inventory.getStackInSlot(idx).isEmpty());
        }
    }

    @Test void clearsStagedInputsBeforeMakingPrerequisiteInTheSameProcessor() throws Exception{
        var ctx = new Fixture();
        var parentId = UUID.randomUUID();
        var parentPlan = new WorkerRecipePlan(TABLE.id(), ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(LOG, 1), new WorkerRecipePlan.Input(PLANK, 1)), TABLE, 1);
        var parent = new WorkerWorkOrder(parentId,
                new WorkerTask(parentId, "Make table", TABLE, 1, 0, 0, true),
                WorkerWorkOrder.Mode.PROCESS, null, null, ctx.storage.id(), TABLE, 1).withRecipePlan(parentPlan);
        var prerequisitePlan = new WorkerRecipePlan(PLANK.id(), ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(LOG, 1)), PLANK, 1);
        var chain = new WorkerRecipeChain(List.of(new WorkerRecipeChain.Step(prerequisitePlan, 1)));
        var staged = mock(WorkerStorageEndpoint.class);
        when(ctx.storage.supportsRecipePlan(parentPlan)).thenReturn(true);
        when(ctx.storage.supportsRecipePlan(prerequisitePlan)).thenReturn(true);
        when(ctx.storage.forStagedInputs(parentPlan)).thenReturn(staged);
        when(ctx.storage.preloadedInputs(parentPlan, 1L)).thenReturn(List.of(1L, 0L));
        var occupied = new java.util.LinkedHashMap<WorkerResourceKey, Long>();
        occupied.put(LOG, 1L);
        occupied.put(PLANK, 1L);
        when(staged.stagedInputItems()).thenReturn(occupied);
        when(staged.available(LOG)).thenReturn(1L);
        when(staged.available(PLANK)).thenReturn(1L);
        when(staged.position()).thenReturn(BlockPos.ZERO);
        when(staged.extract(LOG, 1L, true)).thenReturn(new WorkerResourcePacket(LOG, 1L, null));
        when(staged.extract(LOG, 1L, false)).thenReturn(new WorkerResourcePacket(LOG, 1L, null));
        when(staged.extract(PLANK, 1L, true)).thenReturn(new WorkerResourcePacket(PLANK, 1L, null));
        when(staged.extract(PLANK, 1L, false)).thenReturn(new WorkerResourcePacket(PLANK, 1L, null));
        doReturn(new WorkerRecipeChain(List.of()), chain).when(ctx.controller).workerRecipePrerequisites(
                eq(parent), eq(0), eq(0L), eq(1L),
                anyList(), anyMap());
        Object runtime = runtime(parent);
        set(runtime, "recipeBatchCount", 1L);
        var inventory = new WorkerInventoryEndpoint(UUID.randomUUID(), ctx.worker::blockPosition,
                ctx.inventory, ctx.level.registryAccess(), stack -> true, true);
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("queueMissingRecipeInputs",
                AdvancedContraptionControllerBlockEntity.class, runtime.getClass(), WorkerWorkOrder.class,
                List.class, WorkerInventoryEndpoint.class, PlayerMannequinEntity.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(ctx.pod, ctx.controller, runtime, parent,
                List.of(ctx.storage), inventory, ctx.worker));
        assertEquals(WorkerWorkOrder.Mode.RECLAIM_INPUT, queue(runtime).current().mode());
        assertEquals(WorkerWorkOrder.Mode.RECLAIM_INPUT, queue(runtime).planned().getFirst().mode());
        assertEquals(PLANK, queue(runtime).planned().get(1).outputResource());
        assertEquals(parent.id(), queue(runtime).planned().get(2).id());
        verify(ctx.controller, times(2)).workerRecipePrerequisites(eq(parent), eq(0), eq(0L), eq(1L),
                anyList(), anyMap());
        try(var storage = mockStatic(WorkerStorageEndpoint.class); var simulated = mockStatic(SimulatedHelper.class);
            var packages = mockStatic(PackageStyles.class)){
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            arrive(ctx, runtime);
            arrive(ctx, runtime);
            assertEquals(WorkerWorkOrder.Mode.RECLAIM_INPUT, queue(runtime).current().mode());
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            arrive(ctx, runtime);
            arrive(ctx, runtime);
            assertEquals(PLANK, queue(runtime).current().outputResource());
            assertEquals(1, ctx.inventory.getStackInSlot(0).getCount());
            assertEquals(Items.OAK_LOG, ctx.inventory.getStackInSlot(0).getItem());
            assertEquals(Items.OAK_PLANKS, ctx.inventory.getStackInSlot(1).getItem());
        }
    }

    @Test void workerNameplateShowsCurrentJobAndTask() throws Exception{
        var ctx = new Fixture();
        Object runtime = runtime(ctx.tableOrder());
        set(runtime, "status", "Collecting ingredients");
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("applyName", runtime.getClass(),
                PlayerMannequinEntity.class);
        method.setAccessible(true);
        method.invoke(null, runtime, ctx.worker);
        verify(ctx.worker).setCustomName(argThat(name -> name.getString().contains("Crafting Crafting Table")
                && !name.getString().contains("Collecting ingredients")));
    }

    @Test void workerCollectsNearbyLooseItemsIntoAnExistingStack() throws Exception{
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var worker = mock(PlayerMannequinEntity.class);
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 63));
        when(worker.workerInventory()).thenReturn(inventory);
        when(worker.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 2, 1));
        var item = mock(ItemEntity.class);
        when(item.getItem()).thenReturn(new ItemStack(Items.OAK_PLANKS, 3));
        when(level.getEntitiesOfClass(eq(ItemEntity.class), any(AABB.class), any()))
                .thenReturn(List.of(item));
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("collectNearbyItems",
                net.minecraft.server.level.ServerLevel.class, PlayerMannequinEntity.class);
        method.setAccessible(true);
        method.invoke(null, level, worker);
        assertEquals(64, inventory.getStackInSlot(0).getCount());
        verify(item).setItem(argThat(stack -> stack.is(Items.OAK_PLANKS) && stack.getCount() == 2));
        verify(worker).take(item, 1);
    }

    // Carry every crafted intermediate into the next recipe batch without losing its count
    @Test void carriedIntermediatesCraftMultipleRequestedResults() throws Exception{
        var ctx = new Fixture();
        Object runtime = runtime(ctx.tableOrder(2));
        try(var storage = mockStatic(WorkerStorageEndpoint.class); var simulated = mockStatic(SimulatedHelper.class);
            var packages = mockStatic(PackageStyles.class)){
            packages.when(PackageStyles::getDefaultBox).thenReturn(ItemStack.EMPTY);
            storage.when(() -> WorkerStorageEndpoint.linked(ctx.controller, true)).thenReturn(List.of(ctx.storage));
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            assertEquals(PLANK, queue(runtime).current().outputResource());
            assertEquals(8L, queue(runtime).current().task().requestedAmount());
            invoke(ctx.pod, "assign", runtime, ctx.controller, ctx.worker);
            arrive(ctx, runtime);
            assertEquals(TABLE, queue(runtime).current().outputResource(),
                    "status=" + get(runtime, "status") + ", batch=" + get(runtime, "recipeBatchCount")
                            + ", cargo=" + cargo(runtime));
            assertEquals(8L, cargo(runtime).amount());
            runtime = reload(runtime);
            restoreProgress(runtime);
            invoke(ctx.pod, "useCarriedWorkerCraftInput", runtime, ctx.controller, ctx.worker);
            assertEquals(2L, get(runtime, "recipeBatchCount"));
            assertEquals(TABLE, cargo(runtime).resource());
            assertEquals(2L, cargo(runtime).amount());
            arrive(ctx, runtime);
            assertNull(queue(runtime).current());
            verify(ctx.storage).extract(LOG, 2L, false);
            verify(ctx.storage).insert(argThat(packet -> packet.resource().equals(TABLE) && packet.amount() == 2L), eq(false));
        }
    }

    // Recreate only the state needed by the server recipe and assignment paths
    private static final class Fixture{
        final Level level = mock(Level.class);
        final AdvancedContraptionControllerBlockEntity controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        final WorkerPodBlockEntity pod = mock(WorkerPodBlockEntity.class, CALLS_REAL_METHODS);
        final WorkerStorageEndpoint storage = mock(WorkerStorageEndpoint.class);
        final PlayerMannequinEntity worker = mock(PlayerMannequinEntity.class);
        final ItemStackHandler inventory = new ItemStackHandler(36);

        Fixture(){
            controller.setLevel(level);
            pod.setLevel(level);
            doNothing().when(pod).setChanged();
            doNothing().when(pod).sendData();
            doReturn(java.util.Map.of()).when(pod).workerPlanningStock(any());
            when(worker.position()).thenReturn(Vec3.ZERO);
            when(worker.blockPosition()).thenReturn(BlockPos.ZERO);
            when(worker.workerInventory()).thenReturn(inventory);
            when(worker.workerCurios()).thenReturn(new ItemStackHandler(0));
            when(worker.getItemBySlot(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
            when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            UUID storageId = UUID.randomUUID();
            when(storage.id()).thenReturn(storageId);
            when(storage.forSource(any())).thenReturn(storage);
            when(storage.forRecipe(any())).thenReturn(storage);
            when(storage.storageId()).thenReturn(storageId);
            when(storage.position()).thenReturn(BlockPos.ZERO);
            when(storage.isAvailable()).thenReturn(true);
            when(storage.isWorkerManagedStorage()).thenReturn(true);
            when(storage.isWorkerRecipeSource()).thenReturn(true);
            when(storage.acceptsDelivery()).thenReturn(true);
            when(storage.canExtract(any())).thenReturn(true);
            when(storage.canInsert(any(WorkerResourceKey.class))).thenReturn(true);
            when(storage.space(any())).thenReturn(64L);
            when(storage.available(LOG)).thenReturn(64L);
            when(storage.extract(eq(LOG), anyLong(), eq(false)))
                    .thenAnswer(call -> new WorkerResourcePacket(LOG, call.getArgument(1), null));
            when(storage.insert(any(WorkerResourcePacket.class), anyBoolean()))
                    .thenAnswer(call -> ((WorkerResourcePacket) call.getArgument(0)).amount());
            when(storage.snapshot()).thenReturn(new WorkerEndpointSnapshot(storageId, null, BlockPos.ZERO,
                    "Logs", "create:creative_crate", true, true,
                    List.of(new WorkerEndpointSnapshot.ResourceAmount(LOG, 64, 64))));
            RecipeManager manager = mock(RecipeManager.class);
            when(level.getRecipeManager()).thenReturn(manager);
            List<RecipeHolder<?>> recipes = List.of(
                    recipe("oak_planks", new ItemStack(Items.OAK_PLANKS, 4), Ingredient.of(Items.OAK_LOG)),
                    recipe("crafting_table", new ItemStack(Items.CRAFTING_TABLE),
                            Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.OAK_PLANKS),
                            Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.OAK_PLANKS)));
            when(manager.getRecipes()).thenReturn(recipes);
            for(RecipeHolder<?> holder : recipes) when(manager.byKey(holder.id())).thenReturn(Optional.of(holder));
        }

        WorkerWorkOrder tableOrder(){
            return tableOrder(1);
        }

        WorkerWorkOrder tableOrder(long amount){
            UUID id = UUID.randomUUID();
            var plan = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                    WorkerRecipePlan.Operation.WORKER_CRAFTING, List.of(new WorkerRecipePlan.Input(PLANK, 4)), TABLE, 1);
            return new WorkerWorkOrder(id, new WorkerTask(id, "Table", PLANK, amount, 0, 0, true),
                    WorkerWorkOrder.Mode.AUTO_CRAFT, null, null, null, TABLE, 1).withRecipePlan(plan);
        }
    }

    // Feed individual vanilla ingredient slots through the addon recipe adapter
    private static RecipeHolder<?> recipe(String id, ItemStack res, Ingredient... ingredients){
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.canCraftInDimensions(2, 2)).thenReturn(true);
        when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, ingredients));
        when(recipe.getResultItem(any())).thenReturn(res);
        when(recipe.matches(any(), any())).thenReturn(true);
        when(recipe.assemble(any(), any())).thenReturn(res);
        when(recipe.getRemainingItems(any())).thenAnswer(call -> NonNullList.withSize(((CraftingInput)call.getArgument(0)).size(), ItemStack.EMPTY));
        return new RecipeHolder<>(ResourceLocation.withDefaultNamespace(id), recipe);
    }

    private static Object runtime(WorkerWorkOrder order) throws Exception{
        CompoundTag tag = new CompoundTag();
        tag.put("Profile", new WorkerProfile(UUID.randomUUID(), "Worker", WorkerProfile.Job.ANY, true).toTag());
        WorkerTaskQueue queue = new WorkerTaskQueue();
        queue.enqueue(order);
        tag.put("Queue", queue.toTag());
        var type = Class.forName(WorkerPodBlockEntity.class.getName() + "$WorkerRuntime");
        var read = type.getDeclaredMethod("fromTag", CompoundTag.class);
        read.setAccessible(true);
        return read.invoke(null, tag);
    }

    private static WorkerTaskQueue queue(Object runtime) throws Exception{
        var field = runtime.getClass().getDeclaredField("queue");
        field.setAccessible(true);
        return (WorkerTaskQueue) field.get(runtime);
    }

    private static Object reload(Object runtime) throws Exception{
        var write = runtime.getClass().getDeclaredMethod("toTag");
        var read = runtime.getClass().getDeclaredMethod("fromTag", CompoundTag.class);
        write.setAccessible(true);
        read.setAccessible(true);
        return read.invoke(null, write.invoke(runtime));
    }

    private static WorkerResourcePacket cargo(Object runtime) throws Exception{
        var field = runtime.getClass().getDeclaredField("carried");
        field.setAccessible(true);
        return (WorkerResourcePacket) field.get(runtime);
    }

    private static void restoreProgress(Object runtime) throws Exception{
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("restoreRecipeProgress", runtime.getClass());
        method.setAccessible(true);
        method.invoke(null, runtime);
    }

    private static void arrive(Fixture ctx, Object runtime) throws Exception{
        var method = WorkerPodBlockEntity.class.getDeclaredMethod("arrive", runtime.getClass(), PlayerMannequinEntity.class);
        method.setAccessible(true);
        method.invoke(ctx.pod, runtime, ctx.worker);
    }

    private static void invoke(WorkerPodBlockEntity pod, String name, Object runtime,
                               AdvancedContraptionControllerBlockEntity controller, PlayerMannequinEntity worker) throws Exception{
        var method = WorkerPodBlockEntity.class.getDeclaredMethod(name,
                AdvancedContraptionControllerBlockEntity.class, runtime.getClass(), PlayerMannequinEntity.class);
        method.setAccessible(true);
        method.invoke(pod, controller, runtime, worker);
    }

    private static WorkerResourceKey item(String id){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(id));
    }
}
