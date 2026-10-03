package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRuntimePersistenceTest{
    // Keep a partially collected parent recipe suspended across a prerequisite and world reload
    @Test void resumesPartialRecipeAfterPrerequisiteReload() throws Exception{
        CompoundTag tag = runtimeTag();
        WorkerTaskQueue queue = WorkerTaskQueue.fromTag(tag.getCompound("Queue"));
        WorkerWorkOrder parent = queue.current();
        WorkerRecipePlan plan = parent.recipePlan();
        parent = parent.withRecipePlan(new WorkerRecipePlan(plan.recipeId(), plan.processorType(), plan.operation(),
                List.of(new WorkerRecipePlan.Input(WorkerResourceKey.energy(), 3),
                        new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                                ResourceLocation.withDefaultNamespace("oak_planks")), 4)), plan.result(), plan.resultAmount()));
        queue.updateCurrent(parent);
        UUID processor = UUID.randomUUID();
        var dependency = new WorkerWorkOrder(UUID.randomUUID(), parent.task(), parent.mode(),
                null, null, null, parent.outputResource(), 1).withRecipePlan(parent.recipePlan());
        queue.prepend(List.of(dependency));
        tag.put("Queue", queue.toTag());
        CompoundTag progress = new CompoundTag();
        progress.putUUID("Order", parent.id());
        progress.putInt("RecipeInputIndex", 1);
        progress.putLong("RecipeInputDelivered", 2);
        progress.putUUID("RecipeProcessor", processor);
        ListTag suspended = new ListTag();
        suspended.add(progress);
        tag.put("SuspendedRecipes", suspended);
        var type = Class.forName(WorkerPodBlockEntity.class.getName() + "$WorkerRuntime");
        var read = type.getDeclaredMethod("fromTag", CompoundTag.class);
        var write = type.getDeclaredMethod("toTag");
        var restore = WorkerPodBlockEntity.class.getDeclaredMethod("restoreRecipeProgress", type);
        var queueField = type.getDeclaredField("queue");
        read.setAccessible(true);
        write.setAccessible(true);
        restore.setAccessible(true);
        queueField.setAccessible(true);
        Object runtime = read.invoke(null, tag);
        CompoundTag saved = (CompoundTag) write.invoke(runtime);
        runtime = read.invoke(null, saved);
        ((WorkerTaskQueue) queueField.get(runtime)).completeCurrent();
        restore.invoke(null, runtime);
        saved = (CompoundTag) write.invoke(runtime);
        assertEquals(1, saved.getInt("RecipeInputIndex"));
        assertEquals(2, saved.getLong("RecipeInputDelivered"));
        assertEquals(processor, saved.getUUID("RecipeProcessor"));
        assertTrue(saved.getList("SuspendedRecipes", 10).isEmpty());
    }

    // Resume the output wait instead of collecting the same ingredients again after load
    @Test void restoresCompletedProcessorInputsFromLegacyRuntime() throws Exception{
        CompoundTag tag = runtimeTag();
        tag.putInt("RecipeInputIndex", 1);
        tag.putUUID("CargoProcessor", UUID.randomUUID());
        var type = Class.forName(WorkerPodBlockEntity.class.getName() + "$WorkerRuntime");
        var read = type.getDeclaredMethod("fromTag", CompoundTag.class);
        read.setAccessible(true);
        Object restored = read.invoke(null, tag);
        var stage = type.getDeclaredField("stage");
        stage.setAccessible(true);
        assertEquals("WAITING_PROCESSOR_OUTPUT", stage.get(restored).toString());
        var write = type.getDeclaredMethod("toTag");
        write.setAccessible(true);
        CompoundTag saved = (CompoundTag) write.invoke(restored);
        assertTrue(saved.getBoolean("WaitingProcessor"));
        assertEquals(tag.getUUID("CargoProcessor"), saved.getUUID("CargoProcessor"));
    }

    // Keep the already delivered count separate from the surplus being returned
    @Test void retainsSurplusAndDeliveredCountAcrossReload() throws Exception{
        CompoundTag tag = runtimeTag();
        tag.putLong("DeliveryAmount", 4);
        tag.putLong("FulfilledDelivery", 1);
        tag.putBoolean("ReturningSurplus", true);
        tag.put("Cargo", new WorkerResourcePacket(WorkerResourceKey.energy(), 3, null).toTag());
        var type = Class.forName(WorkerPodBlockEntity.class.getName() + "$WorkerRuntime");
        var read = type.getDeclaredMethod("fromTag", CompoundTag.class);
        var write = type.getDeclaredMethod("toTag");
        read.setAccessible(true);
        write.setAccessible(true);
        CompoundTag saved = (CompoundTag) write.invoke(read.invoke(null, tag));
        assertEquals(1, saved.getLong("FulfilledDelivery"));
        assertTrue(saved.getBoolean("ReturningSurplus"));
        assertEquals(3, WorkerResourcePacket.fromTag(saved.getCompound("Cargo")).amount());
    }

    private static CompoundTag runtimeTag(){
        UUID id = UUID.randomUUID();
        var key = WorkerResourceKey.energy();
        var plan = new WorkerRecipePlan(ResourceLocation.withDefaultNamespace("test"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(key, 1)), key, 1);
        var task = new WorkerTask(id, "Craft", key, 1, 0, 0, true);
        var queue = new WorkerTaskQueue();
        queue.enqueue(new WorkerWorkOrder(id, task, WorkerWorkOrder.Mode.PROCESS, null, null, null, key, 1)
                .withRecipePlan(plan));
        CompoundTag tag = new CompoundTag();
        tag.put("Profile", new WorkerProfile(UUID.randomUUID(), "Worker", WorkerProfile.Job.ANY, true).toTag());
        tag.put("Queue", queue.toTag());
        return tag;
    }
}
