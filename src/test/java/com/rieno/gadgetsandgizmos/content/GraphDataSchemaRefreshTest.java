package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphDataSchemaRefreshTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test
    void composedSchemasRefreshOnceAndObserveEveryTarget() throws Exception{
        Level level = mock(Level.class);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        var levelField = BlockEntity.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(controller, level);
        var graph = new AdvancedGraphDocument();
        ListTag targets = new ListTag();
        BlockEntity[] blocks = new BlockEntity[2];
        ControllerDiscoveryNode[] discoveries = new ControllerDiscoveryNode[2];
        for(int idx = 0; idx < 2; idx++){
            BlockPos pos = new BlockPos(idx, 64, 1);
            var linked = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "minecraft:chest", "Chest",
                    ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                    ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
            discoveries[idx] = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst();
            targets.add(discoveries[idx].toTag());
            blocks[idx] = mock(BlockEntity.class, withSettings().extraInterfaces(BlockEntityDataProvider.class));
            when(((BlockEntityDataProvider) blocks[idx]).isGraphDataSchemaReady()).thenReturn(true);
            when(level.getBlockEntity(pos)).thenReturn(blocks[idx]);
        }
        CompoundTag data = new CompoundTag();
        data.put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        var node = new AdvancedGraphDocument.Node("composed", "set_block_data", "", 0, 0, data);
        graph.nodes().add(node);
        var changed = AdvancedContraptionControllerBlockEntity.class
                .getDeclaredMethod("graphDataSchemaChanged", AdvancedGraphDocument.class);
        var refresh = AdvancedContraptionControllerBlockEntity.class
                .getDeclaredMethod("refreshMultiDataTargetPorts", AdvancedGraphDocument.Node.class);
        changed.setAccessible(true);
        refresh.setAccessible(true);
        try(var statics = mockStatic(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS)){
            for(ControllerDiscoveryNode target : discoveries){
                statics.when(() -> AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(level, node, target))
                        .thenReturn(new AdvancedContraptionControllerBlockEntity.GraphDataTarget(level, target.blockPos(), null));
                statics.when(() -> AdvancedContraptionControllerBlockEntity.graphDataTargetPortLayout(level, node, target, true))
                        .thenReturn(new GraphTargetPortLayout.Target(target.nodeId(), "Chest", List.of(
                                new GraphTargetPortLayout.Port("value", "Value", "number", List.of()))));
            }
            assertEquals(true, changed.invoke(controller, graph));
            refresh.invoke(controller, node);
            assertEquals(false, changed.invoke(controller, graph));
            when(((BlockEntityDataProvider) blocks[1]).graphDataSchemaRevision()).thenReturn(1L);
            assertEquals(true, changed.invoke(controller, graph));
            refresh.invoke(controller, node);
            assertEquals(false, changed.invoke(controller, graph));
            when(((BlockEntityDataProvider) blocks[0]).isGraphDataSchemaReady()).thenReturn(false);
            when(((BlockEntityDataProvider) blocks[1]).graphDataSchemaRevision()).thenReturn(2L);
            assertEquals(false, changed.invoke(controller, graph));
            refresh.invoke(controller, node);
            when(((BlockEntityDataProvider) blocks[0]).isGraphDataSchemaReady()).thenReturn(true);
            assertEquals(true, changed.invoke(controller, graph));
            refresh.invoke(controller, node);
            assertEquals(false, changed.invoke(controller, graph));
        }
    }
}
