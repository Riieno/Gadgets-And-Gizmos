package com.rieno.gadgetsandgizmos.ponder;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.mojang.logging.LogUtils;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

// Register the addon's CT Ponder integration
public class CTPonderPlugin implements PonderPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the mod id
    @Override
    public String getModId() {
        return CreateThrusters.MOD_ID;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the scenes
    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        try {

            PonderSceneRegistrationHelper<DeferredHolder<?, ?>> entries = helper.withKeyFunction(DeferredHolder::getId);

            if (CTBlocks.THRUSTER != null) {
                entries.addStoryBoard(CTBlocks.THRUSTER, "thruster/thruster_fuel", CTPonderScenes::thrusterPropulsion,
                        AllCreatePonderTags.KINETIC_APPLIANCES);
                entries.addStoryBoard(CTBlocks.THRUSTER, "thruster/thruster_fe", CTPonderScenes::thrusterFeMode,
                        AllCreatePonderTags.KINETIC_APPLIANCES);
                entries.addStoryBoard(CTBlocks.THRUSTER, "thruster/thruster_processing", CTPonderScenes::thrusterProcessing,
                        AllCreatePonderTags.KINETIC_APPLIANCES);
            }
            if (CTBlocks.ALTERNATOR != null) {
                entries.addStoryBoard(CTBlocks.ALTERNATOR, "industrial_alternator/industrial_alternator_2",
                        CTPonderScenes::industrialAlternator, AllCreatePonderTags.KINETIC_APPLIANCES);
            }
            if (CTBlocks.INDUSTRIAL_MOTOR != null) {
                entries.addStoryBoard(CTBlocks.INDUSTRIAL_MOTOR, "industrial_motor/industrial_motor_1",
                        CTPonderScenes::industrialMotor, AllCreatePonderTags.KINETIC_APPLIANCES);
            }
            if (CTBlocks.VARIABLE_TRANSMISSION != null) {
                entries.addStoryBoard(CTBlocks.VARIABLE_TRANSMISSION, "variable_transmission/variable_transmission",
                        CTPonderScenes::variableTransmissionScaling, AllCreatePonderTags.KINETIC_RELAYS, AllCreatePonderTags.REDSTONE);
            }

            if (CTBlocks.THRUSTER_BEARING != null) {
                entries.addStoryBoard(CTBlocks.THRUSTER_BEARING, "thruster_bearing/thruster_bearing_controller", CTPonderScenes::bearingController,
                        AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR);
                entries.addStoryBoard(CTBlocks.THRUSTER_BEARING, "thruster_bearing/thruster_bearing_datalink", CTPonderScenes::bearingDataLink,
                        AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR, AllCreatePonderTags.REDSTONE);
                entries.addStoryBoard(CTBlocks.THRUSTER_BEARING, "thruster_bearing/thruster_bearing_servo", CTPonderScenes::bearingServo,
                        AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR, AllCreatePonderTags.REDSTONE);
                entries.addStoryBoard(CTBlocks.THRUSTER_BEARING, "thruster_bearing/thruster_bearing_fuel", CTPonderScenes::bearingFuel,
                        AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR, AllCreatePonderTags.FLUIDS, AllCreatePonderTags.REDSTONE);
            }
            if (CTBlocks.CLAW != null) {
                entries.addStoryBoard(CTBlocks.CLAW, "claw/claw", CTPonderScenes::clawBasics,
                        AllCreatePonderTags.KINETIC_APPLIANCES);
            }
            if (CTBlocks.BIDIRECTIONAL_GEARBOX != null) {
                entries.addStoryBoard(CTBlocks.BIDIRECTIONAL_GEARBOX, "advanced_gearbox/advanced_gearbox_1", CTPonderScenes::gearboxPassthrough,
                        AllCreatePonderTags.KINETIC_RELAYS);
                entries.addStoryBoard(CTBlocks.BIDIRECTIONAL_GEARBOX, "advanced_gearbox/advanced_gearbox_servo", CTPonderScenes::gearboxServo,
                        AllCreatePonderTags.KINETIC_RELAYS, AllCreatePonderTags.REDSTONE);
            }
            if (CTBlocks.ANALOGUE_JOYSTICK != null) {
                entries.addStoryBoard(CTBlocks.ANALOGUE_JOYSTICK, "analogue_joystick/joystick_rs_link_1", CTPonderScenes::joystickControls,
                        AllCreatePonderTags.REDSTONE);
            }
            if (CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER != null) {
                entries.addStoryBoard(CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER, "analogue_contraption_controller/contraption_controller_1",
                        CTPonderScenes::controllerChannels, AllCreatePonderTags.REDSTONE);
            }
            if (CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER != null) {
                entries.addStoryBoard(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER, "advanced_contraption_controller/contraption_controller_1",
                        CTPonderScenes::controllerChannels, AllCreatePonderTags.REDSTONE);
            }
            if (CTBlocks.PHYSICS_GANTRY_SHAFT != null) {
                entries.addStoryBoard(CTBlocks.PHYSICS_GANTRY_SHAFT, "physics_gantry/physics_gantry_complete",
                        CTPonderScenes::physicsGantry, AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR);
            }
            if (CTBlocks.PHYSICS_GANTRY_CARRIAGE != null) {
                entries.addStoryBoard(CTBlocks.PHYSICS_GANTRY_CARRIAGE, "physics_gantry/physics_gantry_complete",
                        CTPonderScenes::physicsGantry, AllCreatePonderTags.KINETIC_APPLIANCES, AllCreatePonderTags.MOVEMENT_ANCHOR);
            }
            if (CTBlocks.PHYSICS_GANTRY_BELT_WHEEL != null) {
                entries.addStoryBoard(CTBlocks.PHYSICS_GANTRY_BELT_WHEEL, "physics_gantry/physics_gantry_complete",
                        CTPonderScenes::physicsGantry, AllCreatePonderTags.KINETIC_RELAYS, AllCreatePonderTags.MOVEMENT_ANCHOR);
            }

        } catch (Throwable throwable) {

            CT_LOGGER.error("[CT][Ponder] Failed to register CT ponder scenes; continuing without CT scene entries", throwable);
        }
    }

    // Register the tags
    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        try {

            PonderTagRegistrationHelper<DeferredHolder<?, ?>> entries = helper.withKeyFunction(DeferredHolder::getId);

            var kineticAppliances = entries.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES);
            addIfPresent(kineticAppliances, CTBlocks.THRUSTER);
            addIfPresent(kineticAppliances, CTBlocks.THRUSTER_BEARING);
            addIfPresent(kineticAppliances, CTBlocks.CLAW);
            addIfPresent(kineticAppliances, CTBlocks.ALTERNATOR);
            addIfPresent(kineticAppliances, CTBlocks.INDUSTRIAL_MOTOR);
            addIfPresent(kineticAppliances, CTBlocks.PHYSICS_GANTRY_SHAFT);
            addIfPresent(kineticAppliances, CTBlocks.PHYSICS_GANTRY_CARRIAGE);
            addIfPresent(kineticAppliances, CTItems.THRUSTER);

            var movementAnchor = entries.addToTag(AllCreatePonderTags.MOVEMENT_ANCHOR);
            addIfPresent(movementAnchor, CTBlocks.THRUSTER_BEARING);
            addIfPresent(movementAnchor, CTBlocks.PHYSICS_GANTRY_SHAFT);
            addIfPresent(movementAnchor, CTBlocks.PHYSICS_GANTRY_CARRIAGE);
            addIfPresent(movementAnchor, CTBlocks.PHYSICS_GANTRY_BELT_WHEEL);

            var redstone = entries.addToTag(AllCreatePonderTags.REDSTONE);
            addIfPresent(redstone, CTBlocks.GYROSCOPE_LINK);
            addIfPresent(redstone, CTBlocks.ANALOGUE_JOYSTICK);
            addIfPresent(redstone, CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER);
            addIfPresent(redstone, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER);
            addIfPresent(redstone, CTBlocks.BIDIRECTIONAL_GEARBOX);
            addIfPresent(redstone, CTBlocks.VARIABLE_TRANSMISSION);

            var kineticRelays = entries.addToTag(AllCreatePonderTags.KINETIC_RELAYS);
            addIfPresent(kineticRelays, CTBlocks.BIDIRECTIONAL_GEARBOX);
            addIfPresent(kineticRelays, CTBlocks.VARIABLE_TRANSMISSION);
            addIfPresent(kineticRelays, CTBlocks.PHYSICS_GANTRY_BELT_WHEEL);

        } catch (Throwable throwable) {

            CT_LOGGER.error("[CT][Ponder] Failed to register CT ponder tags; continuing without CT tag entries", throwable);
        }
    }

    // Add the CT ponder plugin if present
    private static void addIfPresent(MultiTagBuilder.Tag<DeferredHolder<?, ?>> builder,
                                     DeferredHolder<?, ?> holder) {
        if (holder != null) {
            builder.add(holder);
        }
    }
}
