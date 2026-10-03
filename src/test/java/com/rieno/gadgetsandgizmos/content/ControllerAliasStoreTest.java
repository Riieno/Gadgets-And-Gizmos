package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControllerAliasStoreTest {
    @TempDir Path world;

    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @AfterEach
    void closeDatabase() {
        ControllerSqliteStore.closeAll();
    }

    @Test
    void controllersJoinAnAliasAndReceiveItsGraphUpdates() {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world);
        Level level = mock(Level.class);
        when(level.getServer()).thenReturn(server);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        assertNotNull(ControllerSqliteStore.saveController("controller-one", 0,
                "advanced_controller", level, BlockPos.ZERO, null,
                new CompoundTag(), graph("first"), graph("first"), ""));
        assertNotNull(ControllerSqliteStore.saveController("controller-two", 0,
                "advanced_controller", level, new BlockPos(1, 0, 0), null,
                new CompoundTag(), graph("second"), graph("second"), ""));

        ControllerSqliteStore.ControllerAliasGraph created = ControllerSqliteStore.bindControllerAlias(
                level, "controller-one", "Flight Deck", graph("first"), graph("first"));
        assertNotNull(created);
        assertEquals("flight deck", created.alias());
        assertEquals("first", created.draft().getString("Marker"));

        ControllerSqliteStore.ControllerAliasGraph joined = ControllerSqliteStore.bindControllerAlias(
                level, "controller-two", "FLIGHT DECK", graph("second"), graph("second"));
        assertNotNull(joined);
        assertEquals("first", joined.draft().getString("Marker"));

        ControllerSqliteStore.ControllerAliasGraph changed = ControllerSqliteStore.publishControllerAliasGraph(
                level, "controller-two", joined.revision(), graph("updated"), graph("updated"));
        assertNotNull(changed);
        assertEquals("updated", ControllerSqliteStore.controllerAliasGraph(level, "controller-one")
                .active().getString("Marker"));
        assertNull(ControllerSqliteStore.publishControllerAliasGraph(
                level, "controller-one", created.revision(), graph("stale"), graph("stale")));

        ControllerSqliteStore.closeAll();
        assertEquals("updated", ControllerSqliteStore.controllerAliasGraph(level, "controller-two")
                .draft().getString("Marker"));
        assertNotNull(ControllerSqliteStore.bindControllerAlias(
                level, "controller-two", "", graph("updated"), graph("updated")));
        assertNull(ControllerSqliteStore.controllerAliasGraph(level, "controller-two"));
        assertEquals("updated", ControllerSqliteStore.controllerAliasGraph(level, "controller-one")
                .draft().getString("Marker"));
    }

    private static CompoundTag graph(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Marker", marker);
        return tag;
    }
}
