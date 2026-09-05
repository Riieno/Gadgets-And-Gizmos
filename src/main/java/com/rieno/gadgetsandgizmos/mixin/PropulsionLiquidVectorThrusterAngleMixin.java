package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngles;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Handle Propulsion Liquid Vector Thruster angles
@Pseudo
@Mixin(targets = {
        "dev.propulsionteam.propulsionsimulated.content.thruster.liquid_vector_thruster.LiquidVectorThrusterBlockEntity",
        "dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.liquid_vector_thruster.LiquidVectorThrusterBlockEntity"
},
        remap = false)
public abstract class PropulsionLiquidVectorThrusterAngleMixin implements PropulsionVectorThrusterAngleAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Target vector x
    @Shadow
    private float targetVectorX;
    // Target vector y
    @Shadow
    private float targetVectorY;
    // Current vector x
    @Shadow
    private float currentVectorX;
    // Current vector y
    @Shadow
    private float currentVectorY;
    // Current prev vector x
    @Shadow
    private float prevVectorX;
    // Current prev vector y
    @Shadow
    private float prevVectorY;

    // Tracks whether vector angle override is set
    @Unique
    private boolean createThrusters$vectorAngleOverride;
    // Vector angle x in degrees
    @Unique
    private float createThrusters$vectorAngleXDegrees;
    // Vector angle y in degrees
    @Unique
    private float createThrusters$vectorAngleYDegrees;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the direct angle targets
    @Inject(method = "updateMappedTargets", at = @At("HEAD"), cancellable = true, require = 0)
    private void ct$useDirectAngleTargets(CallbackInfo ci) {
        if (this.createThrusters$vectorAngleOverride) {
            this.createThrusters$applyDirectAngleTargets();
            ci.cancel();
            return;
        }

        if (PropulsionVectorThrusterAngles.shouldIgnoreUnconfiguredVectorSignals(this)) {
            this.createThrusters$clearVectorSignals();
            this.createThrusters$resetToSignalMappedVectors();
            ci.cancel();
        }
    }

    // Clear the direct angles for legacy vector control
    @Inject(method = "setVectorCoordinates", at = @At("HEAD"), require = 0)
    private void ct$clearDirectAnglesForLegacyVectorControl(float x, float y, CallbackInfo ci) {
        this.createThrusters$clearOverrideOnly();
    }

    // Write the direct angles
    @Inject(method = "write", at = @At("TAIL"), require = 0)
    private void ct$writeDirectAngles(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                      CallbackInfo ci) {
        PropulsionVectorThrusterAngles.write(
                tag,
                this.createThrusters$vectorAngleOverride,
                this.createThrusters$vectorAngleXDegrees,
                this.createThrusters$vectorAngleYDegrees);
    }

    // Read the direct angles
    @Inject(method = "read", at = @At("TAIL"), require = 0)
    private void ct$readDirectAngles(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                     CallbackInfo ci) {
        if (!PropulsionVectorThrusterAngles.hasStoredOverride(tag)) {
            this.createThrusters$clearOverrideOnly();
            if (PropulsionVectorThrusterAngles.shouldIgnoreUnconfiguredVectorSignals(this)) {
                this.createThrusters$clearVectorSignals();
                this.createThrusters$resetToSignalMappedVectors();
            } else if (this.createThrusters$hasNoVectorSignals()) {
                this.createThrusters$resetToSignalMappedVectors();
            }
            return;
        }

        this.createThrusters$vectorAngleOverride = true;
        this.createThrusters$vectorAngleXDegrees = PropulsionVectorThrusterAngles.readStoredAngle(
                tag, PropulsionVectorThrusterAngles.ANGLE_X_TAG);
        this.createThrusters$vectorAngleYDegrees = PropulsionVectorThrusterAngles.readStoredAngle(
                tag, PropulsionVectorThrusterAngles.ANGLE_Y_TAG);
        this.createThrusters$applyDirectAngleTargets();
    }

    // Set the vector angles
    @Override
    public void createThrusters$setVectorAngles(double xDegrees, double yDegrees) {
        this.createThrusters$vectorAngleOverride = true;
        this.createThrusters$vectorAngleXDegrees = PropulsionVectorThrusterAngles.clampDegrees(xDegrees);
        this.createThrusters$vectorAngleYDegrees = PropulsionVectorThrusterAngles.clampDegrees(yDegrees);
        this.createThrusters$applyDirectAngleTargets();
        PropulsionVectorThrusterAngles.sync(this);
    }

    // Clear the vector angles
    @Override
    public void createThrusters$clearVectorAngles() {
        if (!this.createThrusters$vectorAngleOverride) {
            return;
        }

        this.createThrusters$clearOverrideOnly();
        this.createThrusters$applySignalMappedTargets();
        PropulsionVectorThrusterAngles.sync(this);
    }

    // Get the vector angles
    @Override
    public Map<String, Object> createThrusters$getVectorAngles() {
        return PropulsionVectorThrusterAngles.angles(
                this.targetVectorX,
                this.targetVectorY,
                this.currentVectorX,
                this.currentVectorY,
                this.createThrusters$vectorAngleOverride);
    }

    // Clear the override only
    @Unique
    private void createThrusters$clearOverrideOnly() {
        this.createThrusters$vectorAngleOverride = false;
        this.createThrusters$vectorAngleXDegrees = 0.0f;
        this.createThrusters$vectorAngleYDegrees = 0.0f;
    }

    // Apply the direct angle targets
    @Unique
    private void createThrusters$applyDirectAngleTargets() {
        this.targetVectorX = PropulsionVectorThrusterAngles.normalizedAngle(this.createThrusters$vectorAngleXDegrees);
        this.targetVectorY = PropulsionVectorThrusterAngles.normalizedAngle(this.createThrusters$vectorAngleYDegrees);
    }

    // Apply the signal mapped targets
    @Unique
    private void createThrusters$applySignalMappedTargets() {
        this.targetVectorX = Mth.clamp((PropulsionVectorThrusterAngles.vectorSignal(this, "westSignal")
                - PropulsionVectorThrusterAngles.vectorSignal(this, "eastSignal")) / 15.0f, -1.0f, 1.0f);
        this.targetVectorY = Mth.clamp((PropulsionVectorThrusterAngles.vectorSignal(this, "downSignal")
                - PropulsionVectorThrusterAngles.vectorSignal(this, "upSignal")) / 15.0f, -1.0f, 1.0f);
    }

    // Reset the signal mapped vectors
    @Unique
    private void createThrusters$resetToSignalMappedVectors() {
        this.createThrusters$applySignalMappedTargets();
        this.currentVectorX = this.targetVectorX;
        this.currentVectorY = this.targetVectorY;
        this.prevVectorX = this.targetVectorX;
        this.prevVectorY = this.targetVectorY;
    }

    // Check if this has no vector signals
    @Unique
    private boolean createThrusters$hasNoVectorSignals() {
        return PropulsionVectorThrusterAngles.hasNoVectorSignals(this);
    }

    // Clear the vector signals
    @Unique
    private void createThrusters$clearVectorSignals() {
        PropulsionVectorThrusterAngles.clearVectorSignals(this);
    }
}
