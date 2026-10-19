package com.rieno.gadgetsandgizmos.content;

import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.SourcedGraphCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControllerSharedGraphsTest{
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void refreshFindsNewFilesOnBothSidesAndRetainsTheirIndependentData() throws Exception{
        Path client = directory.resolve("client/shared_graphs");
        Path server = directory.resolve("server/shared_graphs");
        assertTrue(ControllerManifestStore.listSharedGraphs(client).isEmpty());
        for(Path side : List.of(client, server)){
            var graph = new AdvancedGraphDocument();
            graph.nodes().add(new AdvancedGraphDocument.Node("event", "event_tick", "", 0, 0, new net.minecraft.nbt.CompoundTag()));
            var saved = ControllerManifestStore.saveSharedGraph(side, "Steering",
                    ContraptionNetworkLinkerData.sharedRootWithGraph("Steering", graph), ControllerManifestStore.SharedGraphSaveMode.REJECT);
            assertTrue(saved.saved());
            var loaded = ControllerManifestStore.loadSharedGraph(side.resolve(saved.id() + ".json"));
            assertNotNull(loaded);
            assertEquals(1, ContraptionNetworkLinkerData.readStoredGraphs(loaded.linkerData()).size());
        }
        var first = ControllerManifestStore.listSharedGraphs(client).getFirst();
        var merged = SourcedGraphCatalog.merge(ControllerManifestStore.listSharedGraphs(client),
                ControllerManifestStore.listSharedGraphs(server),
                ControllerManifestStore.SharedGraphEntry::id, ControllerManifestStore.SharedGraphEntry::name);
        assertEquals(List.of("[C] Steering", "[S] Steering"), merged.stream().map(SourcedGraphCatalog.Entry::label).toList());
        ControllerManifestStore.saveSharedGraph(client, "Braking",
                ContraptionNetworkLinkerData.sharedRootWithGraph("Braking", new AdvancedGraphDocument()),
                ControllerManifestStore.SharedGraphSaveMode.REJECT);
        assertEquals(2, ControllerManifestStore.listSharedGraphs(client).size());
        assertEquals(1, ControllerManifestStore.listSharedGraphs(server).size());
        Path original = client.resolve(first.id() + ".json");
        Path renamed = client.resolve("renamed graph (1).json");
        Files.move(original, renamed);
        assertTrue(ControllerManifestStore.listSharedGraphs(client).stream().anyMatch(entry -> entry.id().equals("renamed graph (1)")));
        assertNotNull(ControllerManifestStore.loadSharedGraph(client, "renamed graph (1)"));
        assertNull(ControllerManifestStore.loadSharedGraph(client, "../server/shared_graphs/" + first.id()));
        var root = JsonParser.parseString(Files.readString(renamed)).getAsJsonObject();
        root.remove("linkerData");
        Files.writeString(renamed, root.toString());
        assertEquals(1, ContraptionNetworkLinkerData.readStoredGraphs(
                ControllerManifestStore.loadSharedGraph(renamed).linkerData()).size());
    }
}
