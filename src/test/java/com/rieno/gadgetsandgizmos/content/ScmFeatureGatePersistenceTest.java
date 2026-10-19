package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSteeringMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Preserve authored profiles while the server temporarily disables their control modes
class ScmFeatureGatePersistenceTest{
    private boolean groundSea;
    private boolean plane;

    @BeforeEach void retainAvailability(){
        groundSea = ScmBuiltinControlModes.isGroundSeaEnabled();
        plane = ScmBuiltinControlModes.isPlaneEnabled();
    }

    @AfterEach void restoreAvailability(){
        ScmBuiltinControlModes.setGroundSeaEnabled(groundSea);
        ScmBuiltinControlModes.setPlaneEnabled(plane);
    }

    @Test void disabledModesSurviveProfileRoundTrips(){
        ScmBuiltinControlModes.setGroundSeaEnabled(false);
        ScmBuiltinControlModes.setPlaneEnabled(false);
        for(String vehicle : new String[]{"car", "plane"}){
            for(ScmSteeringMode steering : new ScmSteeringMode[]{ScmSteeringMode.FRONT_WHEEL, ScmSteeringMode.TANK}){
                ScmConfigurationProfile profile = ScmConfigurationProfile.empty();
                profile.setVehicleType(vehicle);
                profile.setSteeringType(steering.id());
                ScmConfigurationProfile saved = ScmConfigurationProfile.fromTag(profile.toTag());
                assertEquals(vehicle, saved.vehicleType());
                assertEquals(steering.id(), saved.steeringType());
                assertEquals(ScmBuiltinControlModes.AIRSHIP_ID, ScmControlModeRegistry.resolve(saved.vehicleType()).id());
                assertEquals(ScmSteeringMode.CUSTOM,
                        ScmSteeringMode.fromId(saved.steeringType()).availableOrFallback(ScmSteeringMode.CUSTOM));
            }
        }
    }
}
