package com.rieno.gadgetsandgizmos.util;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import dev.eriksonn.aeronautics.data.AeroLang;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.Position;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;

// Normalize propulsion force and throttle data across supported thruster mods
public final class CTPropulsionTelemetry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT propulsion telemetry
    private CTPropulsionTelemetry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the real thrust
    public static double getRealThrust(BlockEntityPropeller propeller) {

        return Math.abs(propeller.getScaledThrust());
    }

    // Get the lift capacity
    public static double getLiftCapacity(BlockEntityPropeller propeller) {
        double gravityStrength = getGravityStrength(propeller.getLevel(), propeller.getBlockPos().getCenter());
        if (gravityStrength <= 1.0E-6D) {
            return 0.0D;
        }
        return getRealThrust(propeller) / gravityStrength;
    }

    // Get the assembly real thrust
    public static double getAssemblyRealThrust(ThrusterBearingBlockEntity bearing) {
        double totalRealThrust = 0.0D;
        for (ThrusterBlockEntity thruster : bearing.getAttachedThrustersById().values()) {
            totalRealThrust += getRealThrust(thruster);
        }
        return totalRealThrust;
    }

    // Get the assembly lift capacity
    public static double getAssemblyLiftCapacity(ThrusterBearingBlockEntity bearing) {
        double gravityStrength = getGravityStrength(bearing.getLevel(), bearing.getBlockPos().getCenter());
        if (gravityStrength <= 1.0E-6D) {
            return 0.0D;
        }
        return getAssemblyRealThrust(bearing) / gravityStrength;
    }

    // Get the attached thruster real thrusts
    public static Map<String, Double> getAttachedThrusterRealThrusts(ThrusterBearingBlockEntity bearing) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : bearing.getAttachedThrustersById().entrySet()) {
            values.put(entry.getKey(), getRealThrust(entry.getValue()));
        }
        return values;
    }

    // Get the attached thruster lift capacities
    public static Map<String, Double> getAttachedThrusterLiftCapacities(ThrusterBearingBlockEntity bearing) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, ThrusterBlockEntity> entry : bearing.getAttachedThrustersById().entrySet()) {
            values.put(entry.getKey(), getLiftCapacity(entry.getValue()));
        }
        return values;
    }

    // Get the thrust component
    public static MutableComponent thrustComponent(double realThrust) {
        return AeroLang.pixelNewton(realThrust).component();
    }

    // Get the lift component
    public static MutableComponent liftComponent(double liftCapacity) {
        return AeroLang.kilopixelGram(liftCapacity).component();
    }

    // Get the gravity strength
    private static double getGravityStrength(Level level, Position pos) {
        if (level == null) {
            return 0.0D;
        }
        return DimensionPhysicsData.getGravity(level, new Vector3d(pos.x(), pos.y(), pos.z())).length();
    }
}
