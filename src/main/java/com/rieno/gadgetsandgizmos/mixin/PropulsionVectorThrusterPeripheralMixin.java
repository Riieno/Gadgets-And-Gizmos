package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngles;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Map;

// Expose Propulsion Vector Thruster to ComputerCraft
@Pseudo
@Mixin(
        targets = {
                "dev.propulsionteam.propulsionsimulated.compat.computercraft.VectorThrusterPeripheral",
                "dev.propulsionteam.propulsionsimulated.compat.computercraft.LiquidVectorThrusterPeripheral"
        },
        remap = false
)
@PeripheralTypeDoc({"vector_thruster", "liquid_vector_thruster"})
public abstract class PropulsionVectorThrusterPeripheralMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the vector angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setVectorAngles",
            signature = "setVectorAngles(xDegrees: number, yDegrees: number)",
            description = "Sets the vector angle override in degrees.")
    public final void setVectorAngles(double xDegrees, double yDegrees) throws LuaException {
        ct$angleTarget().createThrusters$setVectorAngles(
                ct$validateAngle("xDegrees", xDegrees, PropulsionVectorThrusterAngles.maxAngleDegrees()),
                ct$validateAngle("yDegrees", yDegrees, PropulsionVectorThrusterAngles.maxAngleDegrees()));
    }

    // Get the vector angles
    @LuaFunction
    @PeripheralDoc(name = "getVectorAngles", signature = "getVectorAngles(): table",
            description = "Returns the requested and applied vector angles in degrees.")
    public final Map<String, Object> getVectorAngles() throws LuaException {
        return ct$angleTarget().createThrusters$getVectorAngles();
    }

    // Get the vector angle limits
    @LuaFunction
    @PeripheralDoc(name = "getVectorAngleLimits", signature = "getVectorAngleLimits(): table",
            description = "Returns the minimum and maximum vector angles in degrees.")
    public final Map<String, Object> getVectorAngleLimits() throws LuaException {
        return Map.of(
                "min", -PropulsionVectorThrusterAngles.maxAngleDegrees(),
                "max", PropulsionVectorThrusterAngles.maxAngleDegrees());
    }

    // Clear the vector angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearVectorAngles", signature = "clearVectorAngles()",
            description = "Clears the vector angle override.")
    public final void clearVectorAngles() throws LuaException {
        ct$angleTarget().createThrusters$clearVectorAngles();
    }

    // Get the angle target
    private PropulsionVectorThrusterAngleAccess ct$angleTarget() throws LuaException {
        PropulsionVectorThrusterAngleAccess access = PropulsionVectorThrusterAngles.findAngleTarget(this);
        // Newer Propulsion builds expose their own vector peripheral. Keep the
        // established degree-based methods as aliases on that same endpoint.
        if (PropulsionVectorThrusterAngles.hasDedicatedComputerCraftInterface(this) && access != null) {
            return access;
        }
        if (access != null) {
            return access;
        }

        throw new LuaException("direct vector angle control is unavailable for this thruster");
    }

    // Validate the angle
    private static double ct$validateAngle(String name, double val, double max) throws LuaException {
        if (!Double.isFinite(val)) {
            throw new LuaException(name + " must be finite");
        }
        if (val < -max || val > max) {
            throw new LuaException(name + " must be between " + (-max) + " and " + max + " degrees");
        }
        return val;
    }
}
