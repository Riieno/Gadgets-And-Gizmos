package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

// Register the shipping schedule instructions
public final class ShippingScheduleInstructions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping schedule instructions
    private ShippingScheduleInstructions() {
    }

    // Register the shipping schedule instructions
    public static void register() {
        register(RefuelIfInstruction.ID, RefuelIfInstruction::new);
        register(CarriageCouplingInstruction.ID, CarriageCouplingInstruction::new);
        register(ParkAtInstruction.ID, ParkAtInstruction::new);
    }

    // Register the shipping schedule instructions
    private static void register(
            ResourceLocation id,
            Supplier<? extends ScheduleInstruction> factory
    ) {
        if (Schedule.INSTRUCTION_TYPES.stream()
                .noneMatch(entry -> id.equals(entry.getFirst()))) {
            Schedule.INSTRUCTION_TYPES.add(Pair.of(id, factory));
        }
    }
}
