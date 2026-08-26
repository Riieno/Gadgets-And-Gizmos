package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import java.util.List;
import java.util.stream.Stream;

// List every addon peripheral adapter without initializing its class
public final class ComputerCraftPeripheralManifest {
    public static final List<String> CLASSES = Stream.concat(List.of(
            "AccDisplayPeripheral",
            "AdvancedContraptionControllerPeripheral",
            "AeroworksServoPeripheral",
            "AileronBearingPeripheral",
            "AnalogueContraptionControllerPeripheral",
            "AnalogueJoystickPeripheral",
            "BiDirectionalGearboxPeripheral",
            "ClawPeripheral",
            "CreateConnectedPeripheral",
            "DirectionalGearshiftPeripheral",
            "ExternalMachinePeripheral",
            "GyroscopeLinkPeripheral",
            "NavigationTablePeripheral",
            "RcsThrusterPeripheral",
            "RopeWinchCablePeripheral",
            "SimulatedThrottleLeverPeripheral",
            "ThrusterBearingPeripheral",
            "ThrusterPeripheral",
            "UniversalDisplayAdapterPeripheral",
            "VectorBearingPeripheral",
            "VirtualOrientationSourcePeripheral",
            "WheelMountPeripheral"
    ).stream().map(name ->
            "com.rieno.gadgetsandgizmos.compat.computercraft." + name), Stream.of(
            "com.rieno.gadgetsandgizmos.mixin.BasicNavigationTablePeripheralMixin",
            "com.rieno.gadgetsandgizmos.mixin.PropulsionVectorThrusterPeripheralMixin"
    )).toList();

    private ComputerCraftPeripheralManifest() {
    }
}
