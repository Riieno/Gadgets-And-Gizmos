package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Exercise the live connector status exposed through the Get Data adapter registry
class DockingConnectorDataTest {
    @BeforeAll
    static void registerAdapters(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CTBlockEntityDataAdapters.register();
    }

    @Test
    void connectionStatusIsBooleanAndReadOnly(){
        var connector = mock(DockingConnectorBlockEntity.class);
        assertEquals("boolean", BlockEntityDataAdapterRegistry.readableData(connector).get("connected"));
        assertFalse(BlockEntityDataAdapterRegistry.writableData(connector).containsKey("connected"));
        assertFalse(BlockEntityDataAdapterRegistry.write(connector, "connected", GraphValue.bool(true)));
    }

    @Test
    void samplesFollowConnectionAndDisconnectionWithoutRefreshingTheSchema(){
        var connector = mock(DockingConnectorBlockEntity.class);
        assertEquals(GraphValue.bool(false), BlockEntityDataAdapterRegistry.read(connector, "connected"));
        when(connector.isLocked()).thenReturn(true);
        when(connector.hasOtherConnector()).thenReturn(true);
        assertEquals(GraphValue.bool(true), BlockEntityDataAdapterRegistry.read(connector, "connected"));
        when(connector.isLocked()).thenReturn(false);
        when(connector.hasOtherConnector()).thenReturn(false);
        assertEquals(GraphValue.bool(false), BlockEntityDataAdapterRegistry.read(connector, "connected"));
    }

    @Test
    void aPairStillApproachingAndAnOrphanLockAreDisconnected(){
        var connector = mock(DockingConnectorBlockEntity.class);
        when(connector.hasOtherConnector()).thenReturn(true);
        assertEquals(GraphValue.bool(false), BlockEntityDataAdapterRegistry.read(connector, "connected"));
        when(connector.hasOtherConnector()).thenReturn(false);
        when(connector.isLocked()).thenReturn(true);
        assertEquals(GraphValue.bool(false), BlockEntityDataAdapterRegistry.read(connector, "connected"));
    }

    @Test
    void removedConnectorsAndUnrelatedBlocksDoNotReportAConnection(){
        var connector = mock(DockingConnectorBlockEntity.class);
        when(connector.isLocked()).thenReturn(true);
        when(connector.hasOtherConnector()).thenReturn(true);
        when(connector.isRemoved()).thenReturn(true);
        assertEquals(GraphValue.bool(false), BlockEntityDataAdapterRegistry.read(connector, "connected"));
        var unrelated = mock(BlockEntity.class);
        assertFalse(BlockEntityDataAdapterRegistry.readableData(unrelated).containsKey("connected"));
        assertNull(BlockEntityDataAdapterRegistry.read(unrelated, "connected"));
    }
}
