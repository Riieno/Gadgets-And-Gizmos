package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ItemThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.PlayerPassengerCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Verify complete native inputs survive graph editing and persisted schedule round trips
class ShippingGraphParityTest{
    private static HolderLookup.Provider registries;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vanilla registry context used by native item serialization
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = HolderLookup.Provider.create(BuiltInRegistries.REGISTRY.stream().map(registry -> registry.asLookup()));
        ShippingScheduleInstructions.register();
    }

    // Cover every registered shipping instruction and wait condition without rewriting their native data
    @Test void roundTripsEveryRegisteredInput(){
        Schedule schedule = new Schedule();
        for(var type : Schedule.INSTRUCTION_TYPES){
            var instruction = type.getSecond().get();
            CompoundTag data = instruction.getData().copy();
            data.putString("Text", "(?:Cargo|Fuel) [0-9]+.*");
            instruction.setData(registries, data);
            List<List<ScheduleWaitCondition>> groups = new ArrayList<>();
            if(instruction.supportsConditions()){
                for(var condition : Schedule.CONDITION_TYPES){
                    groups.add(new ArrayList<>(List.of(condition.getSecond().get())));
                }
            }
            schedule.entries.add(new ScheduleEntry(instruction, groups));
        }
        schedule.cyclic = false;
        schedule.savedProgress = 2;
        AdvancedGraphDocument graph = ShippingScheduleGraph.fromSchedule(schedule, registries);
        assertEquals(schedule.write(registries), ShippingScheduleGraph.toSchedule(graph, registries).write(registries));
    }

    // Preserve configured filter components and AND/OR columns while replacing a native input
    @Test void preservesItemsRegexAndConditionColumns(){
        DestinationInstruction destination = new DestinationInstruction();
        CompoundTag data = new CompoundTag();
        data.putString("Text", "^Dock (?:A|B)\\d+$");
        destination.setData(registries, data);
        ItemThresholdCondition cargo = new ItemThresholdCondition();
        ItemStack item = new ItemStack(Items.IRON_INGOT);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Configured cargo"));
        cargo.setItem(0, item);
        PlayerPassengerCondition players = new PlayerPassengerCondition();
        CompoundTag seated = new CompoundTag();
        seated.putInt("Count", 0);
        seated.putInt("Exact", 0);
        players.setData(registries, seated);
        Schedule schedule = new Schedule(new ArrayList<>(List.of(new ScheduleEntry(destination,
                new ArrayList<>(List.of(new ArrayList<>(List.of(cargo, players)),
                        new ArrayList<>(List.of(new PlayerPassengerCondition()))))))), true, 0);
        AdvancedGraphDocument graph = ShippingScheduleGraph.fromSchedule(schedule, registries);
        String id = ShippingScheduleGraph.orderedNodes(graph).getFirst().id();
        DestinationInstruction replacement = new DestinationInstruction();
        CompoundTag edited = data.copy();
        edited.putString("Text", "^(Loading|Unloading).*\\[1\\]$");
        replacement.setData(registries, edited);
        assertTrue(ShippingScheduleGraph.setScheduleInput(graph, id, replacement, registries));
        Schedule result = ShippingScheduleGraph.toSchedule(graph, registries);
        assertEquals(id, ShippingScheduleGraph.orderedNodes(graph).getFirst().id());
        assertEquals(edited, result.entries.getFirst().instruction.getData());
        assertEquals(2, result.entries.getFirst().conditions.size());
        assertEquals(2, result.entries.getFirst().conditions.getFirst().size());
        assertEquals(cargo.write(registries), result.entries.getFirst().conditions.getFirst().getFirst().write(registries));
        assertEquals(0, ((PlayerPassengerCondition) result.entries.getFirst().conditions.getFirst().get(1)).getTarget());
        assertFalse(((PlayerPassengerCondition) result.entries.getFirst().conditions.getFirst().get(1)).canOvershoot());
    }

    // Keep complete item parameters when a detached condition changes type before being attached
    @Test void authorsDetachedConditions(){
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        assertTrue(ShippingScheduleGraph.appendInstruction(graph, new DestinationInstruction().getId(), registries));
        String parent = ShippingScheduleGraph.orderedNodes(graph).getFirst().id();
        String id = ShippingScheduleGraph.appendDetachedCondition(graph, new PlayerPassengerCondition().getId(), 50, 50, registries);
        ItemThresholdCondition cargo = new ItemThresholdCondition();
        cargo.setItem(0, new ItemStack(Items.GOLD_INGOT));
        CompoundTag data = new CompoundTag();
        data.putString("Threshold", "64");
        data.putInt("Operator", 2);
        data.putInt("Measure", 1);
        cargo.setData(registries, data);
        assertTrue(ShippingScheduleGraph.setScheduleInput(graph, id, cargo, registries));
        assertFalse(ShippingScheduleGraph.attachDetachedCondition(graph, id, parent, registries).isEmpty());
        Schedule result = ShippingScheduleGraph.toSchedule(graph, registries);
        assertEquals(cargo.write(registries), result.entries.getFirst().conditions.getFirst().getFirst().write(registries));
    }
}
