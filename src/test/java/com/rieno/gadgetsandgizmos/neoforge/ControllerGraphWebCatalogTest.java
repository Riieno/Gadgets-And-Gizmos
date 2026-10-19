package com.rieno.gadgetsandgizmos.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControllerGraphWebCatalogTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }

    @Test void liveCatalogExportsResolvedPortsDefaultsAndConditionalSelectors(){
        var catalog = ControllerGraphWebServer.nodeCatalogJson();
        assertEquals(2, catalog.get("schemaVersion").getAsInt());
        assertEquals(AdvancedGraphDocument.CURRENT_VERSION, catalog.get("graphVersion").getAsInt());
        Map<String, JsonObject> nodes = new LinkedHashMap<>();
        catalog.getAsJsonArray("nodes").forEach(raw -> nodes.put(raw.getAsJsonObject().get("id").getAsString(), raw.getAsJsonObject()));
        for(var definition : AdvancedGraphCatalog.all()){
            var node = new AdvancedGraphDocument.Node("", definition.id(), "", 0, 0,
                    AdvancedGraphNodeFactory.createDefaultData(definition.id(), null));
            JsonObject exported = nodes.get(definition.id());assertNotNull(exported, definition.id());
            assertEquals(List.copyOf(AdvancedGraphCatalog.inputs(node).keySet()), List.copyOf(exported.getAsJsonObject("inputs").keySet()), definition.id());
            assertEquals(List.copyOf(AdvancedGraphCatalog.outputs(node).keySet()), List.copyOf(exported.getAsJsonObject("outputs").keySet()), definition.id());
            assertTrue(exported.has("defaultData"), definition.id());
        }
        assertEquals(List.of("exec_1", "exec_2"), List.copyOf(nodes.get("exec_combine").getAsJsonObject("inputs").keySet()));
        assertEquals(List.of("Y", "X"), List.copyOf(nodes.get("math_atan2").getAsJsonObject("inputs").keySet()));
        assertEquals(3, nodes.get("flip_flop").getAsJsonArray("variants").size());
        assertTrue(nodes.get("flip_flop").getAsJsonArray("variants").get(2).getAsJsonObject().getAsJsonObject("inputs").has("skip_pulses"));
        assertEquals("a", nodes.get("pulse_if").getAsJsonObject("matchingInputs").get("b").getAsString());
    }

    @Test void bothWebDeploymentsPackageTheCompleteExperimentalNodeCatalog() throws Exception{
        try(var input = ControllerGraphWebServer.class.getResourceAsStream("/assets/createthrusters/web/graph-editor/nodes.json")){
            assertNotNull(input);
            var catalog = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("experimental", catalog.get("sourceBuild").getAsString());
            var ids = new java.util.HashSet<String>();
            catalog.getAsJsonArray("nodes").forEach(raw -> assertTrue(ids.add(raw.getAsJsonObject().get("id").getAsString())));
            // Runtime providers may add live nodes beyond this packaged experimental snapshot
            assertTrue(ids.size() >= 300);
            for(var raw : catalog.getAsJsonArray("nodes")){
                var node = raw.getAsJsonObject();
                assertTrue(node.has("inputs") && node.has("outputs") && node.has("defaultData"));
            }
            assertTrue(ids.containsAll(List.of("worker_routine", "worker_move_items", "bounded_loop", "pulse_if", "createthrusters:collection_push")));
        }
    }
}
