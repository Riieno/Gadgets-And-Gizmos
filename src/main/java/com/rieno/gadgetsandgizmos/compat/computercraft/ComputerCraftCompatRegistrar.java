package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VirtualOrientationSourceBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

// Register ComputerCraft peripherals only when ComputerCraft is available
public final class ComputerCraftCompatRegistrar {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computer craft compat registrar
    private ComputerCraftCompatRegistrar() {
    }

    // Register the capabilities
    public static void registerCapabilities(RegisterCapabilitiesEvent evt) {
        ComputerCraftRednetEventBridge.install();
        registerDisplays(evt);
        registerPropulsion(evt);
        registerBearings(evt);
        registerControllers(evt);
        registerToolsAndNavigation(evt);
        registerOptionalMachines(evt);
    }

    // Register the displays
    private static void registerDisplays(RegisterCapabilitiesEvent evt) {
        if (CTBlockEntities.ACC_DISPLAY != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.ACC_DISPLAY.get(),
                    (AccDisplayBlockEntity be, Direction side) -> new AccDisplayPeripheral(be));
        }
        if (CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER != null) {
            evt.registerBlockEntity(PeripheralCapability.get(),
                    CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER.get(),
                    (UniversalDisplayAdapterBlockEntity be, Direction side) ->
                            new UniversalDisplayAdapterPeripheral(be));
        }
    }

    // Register the propulsion blocks
    private static void registerPropulsion(RegisterCapabilitiesEvent evt) {
        if (CTBlockEntities.THRUSTER != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.THRUSTER.get(),
                    (ThrusterBlockEntity be, Direction side) -> new ThrusterPeripheral(be));
        }
        if (CTBlockEntities.RCS_THRUSTER != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.RCS_THRUSTER.get(),
                    (RcsThrusterBlockEntity be, Direction side) -> new RcsThrusterPeripheral(be));
        }
    }

    // Register the bearings
    private static void registerBearings(RegisterCapabilitiesEvent evt) {
        if (CTBlockEntities.THRUSTER_BEARING != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.THRUSTER_BEARING.get(),
                    (ThrusterBearingBlockEntity be, Direction side) -> new ThrusterBearingPeripheral(be));
        }
        if (CTBlockEntities.VECTOR_BEARING != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.VECTOR_BEARING.get(),
                    (VectorBearingBlockEntity be, Direction side) -> new VectorBearingPeripheral(be));
        }
        if (CTBlockEntities.AILERON_BEARING != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.AILERON_BEARING.get(),
                    (AileronBearingBlockEntity be, Direction side) -> new AileronBearingPeripheral(be));
        }
    }

    // Register the controllers
    private static void registerControllers(RegisterCapabilitiesEvent evt) {
        if (CTBlockEntities.GYROSCOPE_LINK != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.GYROSCOPE_LINK.get(),
                    (GyroscopeLinkBlockEntity be, Direction side) -> new GyroscopeLinkPeripheral(be));
        }

        if (CTBlockEntities.VIRTUAL_ORIENTATION_SOURCE != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.VIRTUAL_ORIENTATION_SOURCE.get(),
                    (VirtualOrientationSourceBlockEntity be, Direction side) -> new VirtualOrientationSourcePeripheral(be));
        }
        if (CTBlockEntities.BIDIRECTIONAL_GEARBOX != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.BIDIRECTIONAL_GEARBOX.get(),
                    (BiDirectionalGearboxBlockEntity be, Direction side) -> new BiDirectionalGearboxPeripheral(be));
        }
        if (CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get(),
                    (AnalogueContraptionControllerBlockEntity be, Direction side) -> new AnalogueContraptionControllerPeripheral(be));
        }
        if (CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(),
                    (AdvancedContraptionControllerBlockEntity be, Direction side) -> new AdvancedContraptionControllerPeripheral(be));
        }
        if (CTBlockEntities.ANALOGUE_JOYSTICK != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.ANALOGUE_JOYSTICK.get(),
                    (AnalogueJoystickBlockEntity be, Direction side) -> new AnalogueJoystickPeripheral(be));
        }
    }

    // Register the tools and navigation blocks
    private static void registerToolsAndNavigation(RegisterCapabilitiesEvent evt) {
        if (CTBlockEntities.CLAW != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.CLAW.get(),
                    (ClawBlockEntity be, Direction side) -> new ClawPeripheral(be));
        }
        if (CTBlockEntities.ADVANCED_NAVIGATION_TABLE != null) {
            evt.registerBlockEntity(PeripheralCapability.get(), CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get(),
                    (AdvancedNavigationTableBlockEntity be, Direction side) -> new NavigationTablePeripheral(be, be));
        }
    }

    // Register the optional machines
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerOptionalMachines(RegisterCapabilitiesEvent evt) {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse("simulated:throttle_lever"))
                .ifPresent(type -> evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> new SimulatedThrottleLeverPeripheral(be)));

        try {
            Class<?> simTypesClass = Class.forName("dev.simulated_team.simulated.index.SimBlockEntityTypes");
            Object ropeWinchEntry = simTypesClass.getField("ROPE_WINCH").get(null);
            Object ropeWinchType = ropeWinchEntry.getClass().getMethod("get").invoke(ropeWinchEntry);
            if (ropeWinchType instanceof BlockEntityType<?> blockEntityType) {
                evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) blockEntityType,
                        (be, side) -> be instanceof RopeWinchPeripheralBridge bridge
                                ? new RopeWinchCablePeripheral(bridge)
                                : null);
            }
        } catch (Exception ignored) {
        }

        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse("offroad:wheel_mount"))
                .ifPresent(type -> evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> new WheelMountPeripheral(be)));

        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse("simulated:directional_gearshift"))
                .ifPresent(type -> evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> be instanceof BlockEntity blockEntity
                                ? new DirectionalGearshiftPeripheral(blockEntity)
                                : null));
        registerExternalMachine(evt, "simulated:laser_pointer", "laser_pointer");
        registerExternalMachine(evt, "simulated:laser_sensor", "laser_sensor");
        registerExternalMachine(evt, "simulated:ir_sensor", "laser_sensor");
        registerExternalMachine(evt, "simulated:analogue_transmission", "analogue_transmission");
        registerExternalMachine(evt, "simulated:simple", "analogue_transmission");
        registerExternalMachine(evt, "simulated:redstone_accumulator", "redstone_accumulator");
        registerExternalMachine(evt, "simulated:redstone_inductor", "redstone_inductor");
        registerExternalMachine(evt, "simulated:redstone_magnet", "redstone_magnet");
        registerExternalMachine(evt, "simulated:optical_sensor", "optical_sensor");
        registerExternalMachine(evt, "simulated:docking_connector", "docking_connector");
        registerExternalMachine(evt, "simulated:altitude_sensor", "altitude_sensor");

        // ------------------------------------AERONAUTICS MACHINES------------------------------------
        registerExternalMachine(evt, "aeronautics:adjustable_burner", "hot_air_burner");
        registerExternalMachine(evt, "aeronautics:steam_vent", "steam_vent");
        registerExternalMachine(evt, "aeronautics:mounted_potato_cannon", "mounted_potato_cannon");

        // ------------------------------------AEROWORKS SERVOS------------------------------------
        registerAeroworksServo(evt, "aeroworks:mechanical_servo");
        registerAeroworksServo(evt, "aeroworks:stepper_servo");

        // ------------------------------------GENERIC CREATE MACHINES------------------------------------
        BuiltInRegistries.BLOCK_ENTITY_TYPE.forEach(type -> {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id != null && "create_connected".equals(id.getNamespace())) {
                evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> be instanceof BlockEntity blockEntity
                                ? new CreateConnectedPeripheral(blockEntity)
                                : null);
            }
        });
    }

    // Register the external machine
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerExternalMachine(RegisterCapabilitiesEvent evt, String blockEntityId, String peripheralType) {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse(blockEntityId))
                .ifPresent(type -> evt.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> be instanceof BlockEntity blockEntity
                                ? new ExternalMachinePeripheral(blockEntity, peripheralType)
                                : null));
    }

    // Register the aeroworks servo
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerAeroworksServo(
            RegisterCapabilitiesEvent evt,
            String blockEntityId
    ) {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse(blockEntityId))
                .ifPresent(type -> evt.registerBlockEntity(
                        PeripheralCapability.get(), (BlockEntityType) type,
                        (be, side) -> be instanceof BlockEntity blockEntity
                                ? new AeroworksServoPeripheral(blockEntity)
                                : null));
    }
}
