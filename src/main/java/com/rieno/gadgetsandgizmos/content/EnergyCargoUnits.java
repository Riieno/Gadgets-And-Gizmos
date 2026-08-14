package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition;
import net.minecraft.network.chat.Component;

import java.util.List;

// Convert Forge Energy amounts into the cargo units used by shipping schedules
public final class EnergyCargoUnits {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final List<Unit> UNITS = List.of(
            new Unit("FE", 1L),
            new Unit("KFE", 1_000L),
            new Unit("MFE", 1_000_000L),
            new Unit("GFE", 1_000_000_000L));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the energy cargo units
    private EnergyCargoUnits() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the options
    public static List<Component> options() {
        return UNITS.stream().map(unit -> (Component) Component.literal(unit.label())).toList();
    }

    // Get the label
    public static String label(int measure) {
        return unit(measure).label();
    }

    // Convert the energy cargo units to FE
    public static long toFe(int amount, int measure) {
        return Math.max(0L, amount) * unit(measure).multiplier();
    }

    // Get the display
    public static int display(long fe, int measure) {
        long val = Math.max(0L, fe) / unit(measure).multiplier();
        return (int) Math.min(Integer.MAX_VALUE, val);
    }

    // Test the energy cargo units
    public static boolean test(CargoThresholdCondition.Ops operator, long current, long target) {
        if (operator == null) {
            return false;
        }
        return switch (operator) {
            case GREATER -> current > target;
            case LESS -> current < target;
            case EQUAL -> current == target;
        };
    }

    // Get the unit
    private static Unit unit(int measure) {
        return UNITS.get(Math.floorMod(measure, UNITS.size()));
    }

    // Store the unit
    private record Unit(String label, long multiplier) {
    }
}
