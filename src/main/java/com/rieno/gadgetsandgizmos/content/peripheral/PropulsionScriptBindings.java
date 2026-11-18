package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDirectThrottleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionPreciseThrottle;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngles;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethods;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralBinding;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

// Bind Propulsion's block controls without constructing its optional ComputerCraft peripherals
public final class PropulsionScriptBindings{
    private PropulsionScriptBindings(){}

    public static ScriptPeripheralBinding resolve(BlockEntity target){
        if(!(target instanceof AbstractThrusterBlockEntity thruster)) return null;
        String type = target.getType().builtInRegistryHolder().key().location().getPath();
        return new ScriptPeripheralBinding(){
            @Override public String getType(){ return type; }
            @Override public void bindMethods(ScriptMethods methods){ bind(methods, thruster, type); }
        };
    }

    private static void bind(ScriptMethods methods, AbstractThrusterBlockEntity thruster, String type){
        for(String name : List.of("getFuelAmountMb", "getFuelCapacityMb", "getEnergyAmountFe", "getEnergyCapacityFe", "getFuelAmount", "getFuelCapacity",
                "getBurnTimeRemaining", "isBurning", "setThrustConfig", "getThrustConfig", "getTargetThrustPN", "getTargetThrustKN",
                "setThrustOutput", "clearThrustOutput", "getMaxThrustOutputPn", "isCustomThrustOutputActive")) methods.bind(thruster, name, name);
        for(var alias : Map.of("getObstruction", "getUnobstructedBlocks", "getCurrentThrustPN", "getCurrentThrust", "getDisplayedThrustPN", "getDisplayedThrustPnForTooltip",
                "getAirflowMs", "getDisplayedAirflowMsForTooltip").entrySet()) methods.bind(thruster, alias.getKey(), alias.getValue());
        methods.bind("getCurrentThrustKN", args -> new Object[]{thruster.getCurrentThrust() / dev.propulsionteam.propulsionsimulated.PropulsionConfig.getThrustUnitsPerKnOrDefault()});
        methods.bind("getDisplayedThrustKN", args -> new Object[]{thruster.getDisplayedThrustPnForTooltip() / dev.propulsionteam.propulsionsimulated.PropulsionConfig.getThrustUnitsPerKnOrDefault()});
        methods.bind("setPower", args -> { throttle(thruster, number(args, 0) / 15.0); return new Object[0]; });
        methods.bind("setPowerNormalized", args -> { throttle(thruster, number(args, 0)); return new Object[0]; });
        methods.bind(thruster, "getPower", type.contains("vector") ? "getPower" : "getThrottle");
        if(!type.contains("vector")) return;
        methods.bind("setThrust", args -> { throttle(thruster, number(args, 0) / 15.0); return new Object[0]; });
        methods.bind("setThrustNormalized", args -> { throttle(thruster, number(args, 0)); return new Object[0]; });
        methods.bind(thruster, "getThrust", "getLegacyPowerInt");
        methods.bind(thruster, "getVectorX", "getCurrentVectorX"); methods.bind(thruster, "getVectorY", "getCurrentVectorY");
        methods.bind(thruster, "getTargetVectorX", "getTargetVectorX"); methods.bind(thruster, "getTargetVectorY", "getTargetVectorY");
        var coordinates = new ScriptMethods().bind(thruster, "set", "setVectorCoordinates");
        methods.bind("setVector", args -> coordinates.call("set", List.of(clamp(number(args, 0)), clamp(number(args, 1)))));
        methods.bind("setVectorX", args -> coordinates.call("set", List.of(clamp(number(args, 0)), coordinate(thruster, "getTargetVectorY"))));
        methods.bind("setVectorY", args -> coordinates.call("set", List.of(coordinate(thruster, "getTargetVectorX"), clamp(number(args, 0)))));
        if(thruster instanceof PropulsionVectorThrusterAngleAccess angles){
            methods.bind("setVectorAngles", args -> {
                double x = number(args, 0), y = number(args, 1), max = PropulsionVectorThrusterAngles.maxAngleDegrees();
                if(Math.abs(x) > max || Math.abs(y) > max) throw new ScriptException("Vector angles exceed the supported range");
                angles.createThrusters$setVectorAngles(x, y); return new Object[0];
            });
            methods.bind("getVectorAngles", args -> new Object[]{angles.createThrusters$getVectorAngles()});
            methods.bind("getVectorAngleLimits", args -> new Object[]{Map.of("min", -PropulsionVectorThrusterAngles.maxAngleDegrees(), "max", PropulsionVectorThrusterAngles.maxAngleDegrees())});
            methods.bind("clearVectorAngles", args -> { angles.createThrusters$clearVectorAngles(); return new Object[0]; });
        }
    }
    private static double coordinate(BlockEntity target, String method) throws Exception{
        return ((Number)target.getClass().getMethod(method).invoke(target)).doubleValue();
    }
    private static double number(List<Object> args, int idx){
        if(idx >= args.size() || !(args.get(idx) instanceof Number num) || !Double.isFinite(num.doubleValue())) throw new ScriptException("Expected a finite number");
        return ((Number)args.get(idx)).doubleValue();
    }
    private static double clamp(double val){ return Math.clamp(val, -1, 1); }
    private static void throttle(AbstractThrusterBlockEntity target, double value){
        var controller = PropulsionPreciseThrottle.resolveController(target);
        if(controller instanceof PropulsionDirectThrottleAccess access) access.createThrusters$setDirectThrottle((float)Math.clamp(value, 0, 1));
        else{
            controller.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL);
            controller.setDigitalInput((float)Math.clamp(value, 0, 1));
            controller.setChanged(); controller.dirtyThrust();
        }
    }
}
