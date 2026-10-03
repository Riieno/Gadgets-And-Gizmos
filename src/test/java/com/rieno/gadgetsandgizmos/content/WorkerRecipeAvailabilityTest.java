package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerRecipeAvailabilityTest{
    private static final WorkerResourceKey MATERIAL = new WorkerResourceKey(WorkerResourceType.ITEM,
            ResourceLocation.withDefaultNamespace("raw_material"));

    @Test void usesAnExtractableFaceWithoutCountingTheSameContainerTwice() throws Exception{
        UUID shared = UUID.randomUUID();
        WorkerStorageEndpoint blockedFace = source(shared, 4L, false);
        WorkerStorageEndpoint extractableFace = source(shared, 4L, true);
        WorkerStorageEndpoint otherContainer = source(UUID.randomUUID(), 5L, true);

        Method availability = AdvancedContraptionControllerBlockEntity.class
                .getDeclaredMethod("workerRecipeAvailability", List.class);
        availability.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<WorkerResourceKey, Long> stock = (Map<WorkerResourceKey, Long>)availability.invoke(null,
                List.of(blockedFace, extractableFace, otherContainer));
        assertEquals(9L, stock.get(MATERIAL));
    }

    private static WorkerStorageEndpoint source(UUID storageId, long count, boolean extractable){
        WorkerStorageEndpoint source = mock(WorkerStorageEndpoint.class);
        when(source.isAvailable()).thenReturn(true);
        when(source.storageId()).thenReturn(storageId);
        when(source.canExtract(MATERIAL)).thenReturn(extractable);
        when(source.snapshot()).thenReturn(new WorkerEndpointSnapshot(UUID.randomUUID(), null, BlockPos.ZERO,
                "Materials", "minecraft:barrel", true, false,
                List.of(new WorkerEndpointSnapshot.ResourceAmount(MATERIAL, count, count))));
        if(extractable) when(source.available(MATERIAL)).thenReturn(count);
        return source;
    }
}
