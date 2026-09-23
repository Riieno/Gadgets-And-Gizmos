package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.scratch.ScratchBlockDefinition;
import com.rieno.gadgetsandgizmos.lib.scratch.ScratchBlockRegistry;
import com.simibubi.create.content.trains.schedule.Schedule;
import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Supplier;

// Publish the current Create shipping instruction and condition palette as reusable Scratch blocks.
public final class ShippingScheduleScratchBlocks {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shipping schedule block library
    private static final ScratchBlockRegistry REGISTRY = new ScratchBlockRegistry();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction
    private ShippingScheduleScratchBlocks() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the fully populated schedule block library. Refreshing keeps optional Create integrations visible.
    public static synchronized ScratchBlockRegistry registry() {
        for (Pair<ResourceLocation, Supplier<? extends com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction>> entry
                : Schedule.INSTRUCTION_TYPES) {
            ResourceLocation id = entry.getFirst();
            if (id == null) continue;
            REGISTRY.register(new ScratchBlockDefinition(
                    ShippingScheduleGraph.instructionBlockType(id), "instructions", title(id), 0xFFE59A42,
                    Map.of("schedule_entry", "instruction", "shape", "stack")));
        }
        for (Pair<ResourceLocation, Supplier<? extends com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition>> entry
                : Schedule.CONDITION_TYPES) {
            ResourceLocation id = entry.getFirst();
            if (id == null) continue;
            REGISTRY.register(new ScratchBlockDefinition(
                    ShippingScheduleGraph.conditionBlockType(id), "conditions", title(id), 0xFF6BA7DB,
                    Map.of("schedule_condition", "condition", "shape", "stack")));
        }
        // These are ordinary graph nodes rather than Create instruction
        // surrogates. That keeps the reusable Scratch editor capable of
        // multiple scripts and nested logic without inventing a second graph
        // contract or corrupting older shipping-schedule entries.
        registerFlow("start", "Start", 0xFFFFBF00, "hat");
        registerFlow("end", "End", 0xFFFFBF00, "cap");
        registerFlow("repeat", "Repeat N Times", 0xFFFFAB19, "c");
        registerFlow("loop", "Loop", 0xFFFFAB19, "c");
        return REGISTRY;
    }

    private static void registerFlow(String id, String title, int colour, String shape) {
        REGISTRY.register(new ScratchBlockDefinition(
                ShippingScheduleGraph.flowBlockType(id), "flow", title, colour,
                Map.of("scratch_flow", id, "shape", shape)));
    }

    // Convert a resource id into a compact block title
    private static String title(ResourceLocation id) {
        String[] words = id.getPath().replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? id.toString() : result.toString();
    }
}
