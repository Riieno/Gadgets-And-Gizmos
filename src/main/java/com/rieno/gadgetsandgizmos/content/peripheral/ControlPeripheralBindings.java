package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.RopeWinchPeripheralBridge;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralBinding;
import net.minecraft.world.level.block.entity.BlockEntity;

// Resolve the same device implementations used by the ComputerCraft capability registrations
public final class ControlPeripheralBindings{
    private ControlPeripheralBindings(){}

    public static ScriptPeripheralBinding resolve(BlockEntity target){
        if(target instanceof AccDisplayBlockEntity || target instanceof UniversalDisplayAdapterBlockEntity)
            return new ScriptPeripheralBinding(){ @Override public String getType(){ return "monitor"; } };
        if(target instanceof ThrusterBlockEntity be) return new ThrusterPeripheralControl(be);
        if(target instanceof RcsThrusterBlockEntity be) return new RcsThrusterPeripheralControl(be);
        if(target instanceof ThrusterBearingBlockEntity be) return new ThrusterBearingPeripheralControl(be);
        if(target instanceof VectorBearingBlockEntity be) return new VectorBearingPeripheralControl(be);
        if(target instanceof AileronBearingBlockEntity be) return new AileronBearingPeripheralControl(be);
        if(target instanceof AdvancedContraptionControllerBlockEntity be) return new AdvancedContraptionControllerPeripheralControl(be);
        if(target instanceof AnalogueContraptionControllerBlockEntity be) return new AnalogueContraptionControllerPeripheralControl(be);
        if(target instanceof AnalogueJoystickBlockEntity be) return new AnalogueJoystickPeripheralControl(be);
        if(target instanceof GyroscopeLinkBlockEntity be) return new GyroscopeLinkPeripheralControl(be);
        if(target instanceof BiDirectionalGearboxBlockEntity be) return new BiDirectionalGearboxPeripheralControl(be);
        if(target instanceof ClawBlockEntity be) return new ClawPeripheralControl(be);
        if(target instanceof RopeWinchPeripheralBridge be) return new RopeWinchCablePeripheralControl(be);
        if(target instanceof NavigationTableExtensionAccess ext) return new NavigationTablePeripheralControl(target, ext);
        if(net.neoforged.fml.ModList.get().isLoaded("simulated")){
            var navigation = BasicNavigationScriptBindings.resolve(target); if(navigation != null) return navigation;
        }
        if(net.neoforged.fml.ModList.get().isLoaded("createpropulsion")){
            var propulsion = PropulsionScriptBindings.resolve(target); if(propulsion != null) return propulsion;
        }
        var id = target.getType().builtInRegistryHolder().key().location();
        if(id.getNamespace().equals("create_connected")) return new CreateConnectedPeripheralControl(target);
        return switch(id.toString()){
            case "simulated:throttle_lever" -> new SimulatedThrottleLeverPeripheralControl(target);
            case "simulated:wheel_mount" -> new WheelMountPeripheralControl(target);
            case "simulated:directional_gearshift" -> new DirectionalGearshiftPeripheralControl(target);
            case "aeroworks:mechanical_servo", "aeroworks:stepper_servo" -> new AeroworksServoPeripheralControl(target);
            case "simulated:laser_pointer" -> new ExternalMachinePeripheralControl(target, "laser_pointer");
            case "simulated:laser_sensor", "simulated:ir_sensor" -> new ExternalMachinePeripheralControl(target, "laser_sensor");
            case "simulated:analogue_transmission", "simulated:simple" -> new ExternalMachinePeripheralControl(target, "analogue_transmission");
            case "simulated:redstone_accumulator" -> new ExternalMachinePeripheralControl(target, "redstone_accumulator");
            case "simulated:redstone_inductor" -> new ExternalMachinePeripheralControl(target, "redstone_inductor");
            case "simulated:redstone_magnet" -> new ExternalMachinePeripheralControl(target, "redstone_magnet");
            case "simulated:optical_sensor" -> new ExternalMachinePeripheralControl(target, "optical_sensor");
            case "simulated:docking_connector" -> new ExternalMachinePeripheralControl(target, "docking_connector");
            case "simulated:altitude_sensor" -> new ExternalMachinePeripheralControl(target, "altitude_sensor");
            case "aeronautics:adjustable_burner", "aeronautics:hot_air_burner" -> new ExternalMachinePeripheralControl(target, "hot_air_burner");
            case "aeronautics:steam_vent" -> new ExternalMachinePeripheralControl(target, "steam_vent");
            case "aeronautics:mounted_potato_cannon" -> new ExternalMachinePeripheralControl(target, "mounted_potato_cannon");
            default -> null;
        };
    }
}
