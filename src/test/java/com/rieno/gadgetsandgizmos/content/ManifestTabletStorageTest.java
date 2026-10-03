package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManifestTabletStorageTest{
    @Test void requiresAnAttachedInstalledTabletWithManifestOpen(){
        Level level = mock(Level.class);
        BlockPos storagePos = new BlockPos(2, 64, 2);
        BlockPos tabletPos = storagePos.east();
        var tablet = mock(DiagnosticTabletBlockEntity.class);
        BlockState state = mock(BlockState.class);
        UUID tabletId = UUID.randomUUID();
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockEntity(tabletPos)).thenReturn(tablet);
        when(tablet.getBlockState()).thenReturn(state);
        when(state.hasProperty(DiagnosticTabletBlock.FACING)).thenReturn(true);
        when(state.getValue(DiagnosticTabletBlock.FACING)).thenReturn(Direction.EAST);
        when(tablet.state()).thenReturn(DiagnosticTabletData.State.DEFAULT
                .withTabletId(tabletId).withApp(PaidTabletApps.MANIFEST.id(), "cargo"));
        TabletStorage storage = mock(TabletStorage.class);
        when(storage.installedApps(tabletId)).thenReturn(Set.of(PaidTabletApps.MANIFEST.id()));
        try(var api = mockStatic(TabletStorageApi.class)){
            api.when(TabletStorageApi::storage).thenReturn(storage);
            assertTrue(ManifestTabletStorage.attached(level, storagePos));
            when(tablet.state()).thenReturn(DiagnosticTabletData.State.DEFAULT
                    .withTabletId(tabletId).withApp(PaidTabletApps.BLOCKMATES.id(), "workers"));
            assertFalse(ManifestTabletStorage.attached(level, storagePos));
            when(tablet.state()).thenReturn(DiagnosticTabletData.State.DEFAULT
                    .withTabletId(tabletId).withApp(PaidTabletApps.MANIFEST.id(), "cargo"));
            when(storage.installedApps(tabletId)).thenReturn(Set.of());
            assertFalse(ManifestTabletStorage.attached(level, storagePos));
        }
    }
}
