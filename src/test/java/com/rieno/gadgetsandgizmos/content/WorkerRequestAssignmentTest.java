package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerItemRequest;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProfile;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerStatusSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerTask;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerWorkOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerRequestAssignmentTest{
    private static final ResourceLocation ITEM = ResourceLocation.withDefaultNamespace("stone");

    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // Prefer an idle worker on a later pod over a busy worker on the first pod
    @Test void automaticRequestUsesIdleWorkerAcrossPods(){
        UUID busyId = UUID.randomUUID();
        UUID idleId = UUID.randomUUID();
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        var firstPod = pod(busyId);
        var secondPod = pod(idleId);
        doReturn(List.of(snapshot(busyId, true, order(20)), snapshot(idleId, true, null)))
                .when(controller).managedWorkers();
        when(firstPod.submit(eq(busyId), anyList())).thenReturn(true);
        when(secondPod.submit(eq(idleId), anyList())).thenReturn(true);
        try(var pods = mockStatic(WorkerPodBlockEntity.class)){
            pods.when(() -> WorkerPodBlockEntity.linkedPods(controller)).thenReturn(List.of(firstPod, secondPod));
            assertFalse(controller.requestItems(request(null)).failed());
        }
        verify(secondPod).submit(eq(idleId), anyList());
        verify(firstPod, never()).submit(eq(busyId), anyList());
    }

    // Honor an explicit worker choice even when another worker is idle
    @Test void selectedWorkerReceivesRequest(){
        UUID busyId = UUID.randomUUID();
        UUID idleId = UUID.randomUUID();
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        var firstPod = pod(busyId);
        var secondPod = pod(idleId);
        doReturn(List.of(snapshot(busyId, true, order(20)), snapshot(idleId, true, null)))
                .when(controller).managedWorkers();
        when(firstPod.submit(eq(busyId), anyList())).thenReturn(true);
        try(var pods = mockStatic(WorkerPodBlockEntity.class)){
            pods.when(() -> WorkerPodBlockEntity.linkedPods(controller)).thenReturn(List.of(firstPod, secondPod));
            assertFalse(controller.requestItems(request(busyId)).failed());
        }
        verify(firstPod).submit(eq(busyId), anyList());
        verify(secondPod, never()).submit(eq(idleId), anyList());
    }

    // Never bypass a paused worker by silently assigning the request elsewhere
    @Test void pausedSelectedWorkerIsRejected(){
        UUID pausedId = UUID.randomUUID();
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        var pod = pod(pausedId);
        doReturn(List.of(snapshot(pausedId, false, null))).when(controller).managedWorkers();
        try(var pods = mockStatic(WorkerPodBlockEntity.class)){
            pods.when(() -> WorkerPodBlockEntity.linkedPods(controller)).thenReturn(List.of(pod));
            assertTrue(controller.requestItems(request(pausedId)).failed());
        }
        verify(pod, never()).submit(eq(pausedId), anyList());
    }

    // Accept and save a craft request before reading the recipe manager or linked inventories
    @Test void craftRequestQueuesLookupBeforeDiscoveringRecipes(){
        UUID workerId = UUID.randomUUID();
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        var pod = pod(workerId);
        doReturn(List.of(snapshot(workerId, true, null))).when(controller).managedWorkers();
        when(pod.submit(eq(workerId), anyList())).thenReturn(true);
        var request = new WorkerItemRequest(null, ITEM, 12, true, "", UUID.randomUUID(), workerId);
        try(var pods = mockStatic(WorkerPodBlockEntity.class)){
            pods.when(() -> WorkerPodBlockEntity.linkedPods(controller)).thenReturn(List.of(pod));
            assertFalse(controller.requestItems(request).failed());
        }
        ArgumentCaptor<List<WorkerWorkOrder>> submitted = ArgumentCaptor.forClass(List.class);
        verify(pod).submit(eq(workerId), submitted.capture());
        WorkerWorkOrder order = submitted.getValue().getFirst();
        assertTrue(order.lookingUpRecipe());
        assertEquals(12, order.task().requestedAmount());
        assertEquals(request.id(), order.id());
        assertEquals(request.destinationId(), order.destinationEndpointId());
        assertEquals(order, WorkerWorkOrder.fromTag(order.toTag()));
    }

    private static WorkerPodBlockEntity pod(UUID workerId){
        var pod = mock(WorkerPodBlockEntity.class);
        when(pod.compatibleWorkers(eq(WorkerResourceType.ITEM), anyCollection()))
                .thenAnswer(call -> {
                    Collection<UUID> selected = call.getArgument(1);
                    return selected.contains(workerId) ? List.of(workerId) : List.of();
                });
        return pod;
    }

    private static WorkerStatusSnapshot snapshot(UUID workerId, boolean enabled, WorkerWorkOrder current){
        return new WorkerStatusSnapshot(workerId, "Worker", WorkerProfile.Job.ITEMS,
                enabled, null, BlockPos.ZERO, "", "", current, List.of());
    }

    private static WorkerWorkOrder order(long completed){
        WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM, ITEM);
        WorkerTask task = new WorkerTask(null, "Deliver stone", resource, 100, completed, 0, true);
        return WorkerWorkOrder.automatic(task);
    }

    private static WorkerItemRequest request(UUID workerId){
        return new WorkerItemRequest(null, ITEM, 1, false, "", UUID.randomUUID(), workerId);
    }
}
