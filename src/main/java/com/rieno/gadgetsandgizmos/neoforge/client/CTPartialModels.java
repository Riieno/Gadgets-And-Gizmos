package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

// Register the partial models
public final class CTPartialModels {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final PartialModel PHYSICS_STAFF_CORE_GLOW = item("physics_staff/core_glow");
    public static final PartialModel PHYSICS_STAFF_CORE = item("physics_staff/core");
    public static final PartialModel PHYSICS_STAFF_RING = item("physics_staff/ring");
    public static final PartialModel PHYSICS_STAFF_SIGMA = item("physics_staff/sigma");
    public static final PartialModel PHYSICS_STAFF_INNER_CUBE = item("physics_staff/inner_cube");
    public static final PartialModel PHYSICS_STAFF_OUTER_CUBE = item("physics_staff/outer_cube");
    public static final PartialModel CONTRAPTION_NETWORK_LINKER_ITEM = block("linker/item");
    public static final PartialModel CONTRAPTION_NETWORK_LINKER_NO_SCREEN = block("linker/no_screen");
    public static final PartialModel CONTRAPTION_NETWORK_LINKER_SCREEN = block("linker/screen");
    public static final PartialModel CONTRAPTION_NETWORK_LINKER_LINKED_GUI = item("contraption_network_linker_linked");
    public static final PartialModel CONTRAPTION_NETWORK_LINKER_UNLINKED_GUI = item("contraption_network_linker_unlinked");

    public static final PartialModel THRUSTER_FLAME = block("thruster/flame");
    public static final PartialModel THRUSTER_FLAME_SUPERHEATED = block("thruster/flame_superheated");
    public static final PartialModel[] THRUSTER_FLAME_FRAMES = frames("thruster/flame_", 4);
    public static final PartialModel[] THRUSTER_FLAME_SUPERHEATED_FRAMES = frames("thruster/flame_superheated_", 4);
    public static final PartialModel THRUSTER_FOCUSED_ENGINE = block("thruster/focused_engine");
    public static final PartialModel THRUSTER_FOCUSED_ENGINE_POWERED = block("thruster/focused_engine_powered");
    public static final PartialModel RCS_AXIS = block("rcs_axis");
    public static final PartialModel FUEL_OXIDIZER_COG = CTFeatureToggles.isBlockEnabled("fuel_oxidizer")
            ? block("fuel_oxidizer/cog")
            : null;

    public static final PartialModel THRUSTER_BEARING_PLATE = block("thruster_mount/metal_bearing_plate");
    public static final PartialModel THRUSTER_BEARING_PIPE_CONNECTOR = block("thruster_bearing/pipe_connector");
    public static final PartialModel THRUSTER_BEARING_SWIVEL_LINK = block("thruster_bearing/swivel_bearing_link");
    public static final PartialModel THRUSTER_BEARING_PISTON_HEAD = block("thruster_mount/piston_head");
    public static final PartialModel THRUSTER_BEARING_PISTON_POLE = block("thruster_mount/piston_pole");
    public static final PartialModel VECTOR_BEARING_PLATE = block("vector_bearing/metal_bearing_plate");
    public static final PartialModel VECTOR_BEARING_PISTON_HEAD = block("vector_bearing/piston_head");
    public static final PartialModel VECTOR_BEARING_PISTON_POLE = block("vector_bearing/piston_pole");
    public static final PartialModel AILERON_BEARING_PLATE = block("aileron_bearing/plate");
    public static final PartialModel AILERON_BEARING_HEAD_CYAN   = block("aileron_bearing/cyanhead");
    public static final PartialModel AILERON_BEARING_HEAD_ORANGE = block("aileron_bearing/orangehead");
    public static final PartialModel SCISSOR_PISTON_HEAD = block("scissor_piston/head");
    public static final PartialModel SCISSOR_PISTON_ARM = block("scissor_piston/scissor_arm");
    public static final PartialModel SCISSOR_PISTON_SHAFT = block("scissor_piston/shaft");
    public static final PartialModel PHYSICS_GANTRY_BELT_SEGMENT = block("physics_gantry_belt_wheel/belt_segment");

    public static final PartialModel ANALOGUE_JOYSTICK_HANDLE = block("analogue_joystick/handle");
    public static final PartialModel DOUBLE_BUTTON_TOP = block("double_button/button_top");
    public static final PartialModel DOUBLE_BUTTON_BOTTOM = block("double_button/button_bottom");

    public static final PartialModel CLAW_JAW_LEFT  = block("claw/left");
    public static final PartialModel CLAW_JAW_RIGHT = block("claw/right");
    public static final PartialModel ENTITY_LAUNCHER_CLAW = item("entity_launcher/claw");
    public static final PartialModel ENTITY_LAUNCHER_BASE = block("entity_launcher/base");
    public static final PartialModel ENTITY_LAUNCHER_CANNON_BARREL = block("entity_launcher/cannon_barrel");
    public static final PartialModel PHYSICS_GANTRY_COGS = block("gantry_carriage/wheels");

    public static final PartialModel GANTRY_SHAFT_START                = block("gantry_shaft/block_start");
    public static final PartialModel GANTRY_SHAFT_MIDDLE               = block("gantry_shaft/block_middle");
    public static final PartialModel GANTRY_SHAFT_END                  = block("gantry_shaft/block_end");
    public static final PartialModel GANTRY_SHAFT_SINGLE               = block("gantry_shaft/block_single");
    public static final PartialModel GANTRY_SHAFT_START_POWERED        = block("gantry_shaft_start_powered");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_POWERED       = block("gantry_shaft_middle_powered");
    public static final PartialModel GANTRY_SHAFT_END_POWERED          = block("gantry_shaft_end_powered");
    public static final PartialModel GANTRY_SHAFT_SINGLE_POWERED       = block("gantry_shaft_single_powered");
    public static final PartialModel GANTRY_SHAFT_START_FLIPPED        = block("gantry_shaft_start_flipped");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_FLIPPED       = block("gantry_shaft_middle_flipped");
    public static final PartialModel GANTRY_SHAFT_END_FLIPPED          = block("gantry_shaft_end_flipped");
    public static final PartialModel GANTRY_SHAFT_SINGLE_FLIPPED       = block("gantry_shaft_single_flipped");
    public static final PartialModel GANTRY_SHAFT_START_POWERED_FLIPPED  = block("gantry_shaft_start_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_POWERED_FLIPPED = block("gantry_shaft_middle_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_END_POWERED_FLIPPED    = block("gantry_shaft_end_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_SINGLE_POWERED_FLIPPED = block("gantry_shaft_single_powered_flipped");
    public static final PartialModel ALTERNATOR_ROTOR = block("alternator/rotor");
    public static final PartialModel ALTERNATOR_VU_NEEDLE = block("alternator/vu_needle");
    public static final PartialModel INDUSTRIAL_MOTOR_STRESS_NEEDLE = block("industrial_motor/stress_needle");
    public static final PartialModel ANALOGUE_CONTROLLER_BASE = block("analogue_controller/block");
    public static final PartialModel ANALOGUE_CONTROLLER_JOYSTICK = block("analogue_controller/joystick");
    public static final PartialModel ANALOGUE_CONTROLLER_THROTTLE = block("analogue_controller/throttle_lever");
    public static final PartialModel ANALOGUE_CONTROLLER_CLOCK = block("analogue_controller/clock");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_1 = block("analogue_controller/button_1");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_2 = block("analogue_controller/button_2");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_3 = block("analogue_controller/button_3");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_4 = block("analogue_controller/button_4");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_5 = block("analogue_controller/button_5");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_6 = block("analogue_controller/button_6");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_7 = block("analogue_controller/button_7");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_8 = block("analogue_controller/button_8");
    public static final PartialModel ANALOGUE_CONTROLLER_BUTTON_9 = block("analogue_controller/button_9");
    public static final PartialModel ANALOGUE_CONTROLLER_OMETER_0 = block("analogue_controller/ometer_0");
    public static final PartialModel ANALOGUE_CONTROLLER_OMETER_1 = block("analogue_controller/ometer_1");
    public static final PartialModel ANALOGUE_CONTRAPTION_CONTROLLER_BODY = block("analogue_contraption_controller/block");
    public static final PartialModel ANALOGUE_CONTRAPTION_CONTROLLER_OMETER_0 = block("analogue_contraption_controller/ometer_0");
    public static final PartialModel ANALOGUE_CONTRAPTION_CONTROLLER_OMETER_1 = block("analogue_contraption_controller/ometer_1");
    public static final PartialModel ADVANCED_CONTROLLER_BASE = block("advanced_controller/block");
    public static final PartialModel ADVANCED_CONTROLLER_JOYSTICK = block("advanced_controller/joystick");
    public static final PartialModel ADVANCED_CONTROLLER_THROTTLE = block("advanced_controller/throttle_lever");
    public static final PartialModel ADVANCED_CONTROLLER_CLOCK = block("advanced_controller/clock");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_1 = block("advanced_controller/button_1");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_2 = block("advanced_controller/button_2");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_3 = block("advanced_controller/button_3");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_4 = block("advanced_controller/button_4");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_5 = block("advanced_controller/button_5");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_6 = block("advanced_controller/button_6");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_7 = block("advanced_controller/button_7");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_8 = block("advanced_controller/button_8");
    public static final PartialModel ADVANCED_CONTROLLER_BUTTON_9 = block("advanced_controller/button_9");
    public static final PartialModel ADVANCED_CONTROLLER_OMETER_0 = block("advanced_controller/ometer_0");
    public static final PartialModel ADVANCED_CONTROLLER_OMETER_1 = block("advanced_controller/ometer_1");
    public static final PartialModel ADVANCED_CONTRAPTION_CONTROLLER_BODY = block("advanced_contraption_controller/block");
    public static final PartialModel ADVANCED_NAVIGATION_TABLE_INDICATOR = block("advanced_navigation_table/redstone_indicator");
    public static final PartialModel ADVANCED_NAVIGATION_TABLE_POINTER = block("advanced_navigation_table/nav_table_pointer");
    public static final PartialModel SHIP_DOCK_FLAG = block("ship_dock/flag");
    public static final PartialModel SHIP_DOCKING_CONNECTOR_MAIN_PISTON_BOTTOM = block("ship_docking_connector/main_piston_1");
    public static final PartialModel SHIP_DOCKING_CONNECTOR_MAIN_PISTON_TOP = block("ship_docking_connector/main_piston_2");
    public static final PartialModel SHIP_DOCKING_CONNECTOR_SIDE_PISTON_BOTTOM = block("ship_docking_connector/side_piston_1");
    public static final PartialModel SHIP_DOCKING_CONNECTOR_SIDE_PISTON_TOP = block("ship_docking_connector/side_piston_2");
    public static final PartialModel SHIP_DOCKING_CONNECTOR_FOOT = block("ship_docking_connector/foot");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT partial models
    private CTPartialModels() {
    }

    // Initialize the CT partial models
    public static void init() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block
    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + path));
    }

    // Get the frames
    private static PartialModel[] frames(String prefix, int count) {
        PartialModel[] models = new PartialModel[count];
        for (int i = 0; i < count; i++) {
            models[i] = block(prefix + i);
        }
        return models;
    }

    // Get the item
    private static PartialModel item(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "item/" + path));
    }

}
