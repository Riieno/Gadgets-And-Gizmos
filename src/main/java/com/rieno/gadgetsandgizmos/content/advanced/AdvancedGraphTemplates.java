package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// Create built-in starter graphs with stable node and edge IDs
public final class AdvancedGraphTemplates {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String TEMPLATE_ROOT = "/assets/createthrusters/advanced_graph_templates/";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph templates
    private AdvancedGraphTemplates() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the advanced graph templates
    public static AdvancedGraphDocument create(String id) {
        String template = id == null ? "blank" : id.toLowerCase(Locale.ROOT);
        if ("increment_decrement_on_hold".equals(template) || "toggle_latch".equals(template)) {
            return readBundledTemplate(template);
        }
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.setTemplateId(template);
        if ("linker_router".equals(template)) {
            CompoundTag trigger = new CompoundTag();
            trigger.putString("Event", "linker_input");
            graph.nodes().add(new AdvancedGraphDocument.Node("linker_event", "event_trigger", "Linker Input", 80, 100, trigger));
        }
        return graph;
    }

    // Read the bundled template
    private static AdvancedGraphDocument readBundledTemplate(String template) {
        String path = TEMPLATE_ROOT + template + ".json";
        try (InputStream stream = AdvancedGraphTemplates.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled graph template " + path);
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
            if (!(converted instanceof CompoundTag graphTag)) {
                throw new IllegalStateException("Bundled graph template is not an object: " + path);
            }
            AdvancedGraphDocument graph = AdvancedGraphDocument.fromTag(graphTag);
            graph.setTemplateId(template);
            graph.setRevision(0);
            return graph;
        } catch (IOException err) {
            throw new IllegalStateException("Failed to read bundled graph template " + path, err);
        }
    }
}
