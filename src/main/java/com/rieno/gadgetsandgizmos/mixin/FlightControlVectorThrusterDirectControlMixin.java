package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.control.VectorThrustReceiver;
import com.rieno.gadgetsandgizmos.lib.control.ControlOwnerReceiver;
import com.rieno.gadgetsandgizmos.lib.control.ControllerOwnership;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Expose live SCM throttle control for Flight Control vector thrusters
@Pseudo
@Mixin(targets = "ace.flight.block.SmartVectorThrusterBlockEntity", remap = false)
public abstract class FlightControlVectorThrusterDirectControlMixin
        implements IDirectControlReceiver, VectorThrustReceiver, ControlOwnerReceiver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Shadow public boolean active;
    @Shadow public boolean hasNozzle;
    @Shadow public float halfAngleDeg;
    @Shadow public abstract Direction getNozzleDirection();
    @Shadow public abstract float getEffectiveMaxThrustForComputer();
    @Shadow public abstract boolean hasPropellantForThrust();
    @Shadow public abstract boolean isClusterMember();
    @Shadow public abstract net.minecraft.core.BlockPos getLinkedComputerPos();
    @Shadow public abstract net.minecraft.core.BlockPos getLinkedSwivelMountPos();
    @Shadow public abstract void setAllocatedForce(double[] force);
    @Shadow public volatile double[] allocatedForce;
    @Shadow public abstract void syncToClient();
    @Shadow public abstract float[] getBodyThrustCenterOffset();

    // Tracks whether SCM currently owns native direct thrust production
    @Unique
    private volatile boolean createThrusters$scmDirectThrottleActive;
    @Unique private volatile String createThrusters$forceOwner;
    @Unique private volatile Vec3 createThrusters$requestedForce = Vec3.ZERO;
    @Unique private Vec3 createThrusters$lastSyncedForce = Vec3.ZERO;
    @Unique private long createThrusters$lastSyncTick = Long.MIN_VALUE;
    @Unique private boolean createThrusters$applyingForce;
    @Unique private volatile ControllerOwnership createThrusters$displayOwner = ControllerOwnership.NONE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Expose the native nozzle's live steering range
    @Override
    public double controllerThrustConeDegrees(){ return halfAngleDeg; }

    // Synchronize ownership separately from the native computer link
    @Override
    public void setControllerOwner(String channelId, String displayKey){
        ControllerOwnership next = createThrusters$displayOwner.claim(channelId, displayKey);
        if(next.equals(createThrusters$displayOwner)) return;
        createThrusters$displayOwner = next;
        syncToClient();
    }

    @Override
    public void releaseControllerOwner(String channelId){
        ControllerOwnership next = createThrusters$displayOwner.release(channelId);
        if(next.equals(createThrusters$displayOwner)) return;
        createThrusters$displayOwner = next;
        syncToClient();
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void createThrusters$writeOwner(net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir){
        cir.getReturnValue().put("GgControllerOwner", createThrusters$displayOwner.save());
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void createThrusters$readOwner(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci){
        var component = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        if(component.getLevel() != null && component.getLevel().isClientSide){
            createThrusters$displayOwner = ControllerOwnership.read(tag.getCompound("GgControllerOwner"));
        }
    }

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void createThrusters$ownerTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
            boolean sneaking, CallbackInfoReturnable<Boolean> cir){
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlOwnership.updateTooltip(tooltip, createThrusters$displayOwner);
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        float throttle = Float.isFinite(val) ? Mth.clamp(val, 0.0F, 1.0F) : 0.0F;
        Direction nozzle = getNozzleDirection();
        double force = Math.max(0.0D, getEffectiveMaxThrustForComputer()) * throttle;
        applyVectorControllerForce(channelId, new Vec3(
                -nozzle.getStepX() * force,
                -nozzle.getStepY() * force,
                -nozzle.getStepZ() * force
        ));
    }

    // Retain zero commands so native redstone cannot restart an owned engine
    @Override
    public void applyVectorControllerForce(String channelId, Vec3 force){
        if(channelId == null || channelId.isBlank() || force == null || !Double.isFinite(force.x)
                || !Double.isFinite(force.y) || !Double.isFinite(force.z)) return;
        if(createThrusters$forceOwner != null && !createThrusters$forceOwner.equals(channelId)) return;
        createThrusters$forceOwner = channelId;
        createThrusters$requestedForce = force;
        createThrusters$scmDirectThrottleActive = true;
        createThrusters$applyNativeForce();
    }

    // Restore native input only when the channel owning the engine releases it
    @Override
    public void releaseVectorControllerForce(String channelId){
        if(channelId == null || createThrusters$forceOwner == null
                || !java.util.Objects.equals(channelId, createThrusters$forceOwner)) return;
        createThrusters$forceOwner = null;
        createThrusters$requestedForce = Vec3.ZERO;
        createThrusters$scmDirectThrottleActive = false;
        releaseControllerOwner(channelId);
        setAllocatedForce(new double[3]);
    }

    // Preserve native enabled, nozzle and propellant checks during every server tick
    @Unique
    private void createThrusters$applyNativeForce(){
        Vec3 force = active && hasNozzle && !isClusterMember() && hasPropellantForThrust()
                ? createThrusters$requestedForce : Vec3.ZERO;
        double[] projected = new double[]{force.x, force.y, force.z};
        Direction nozzle = getNozzleDirection();
        FlightControlAllocationAccess.createThrusters$project(projected,
                new double[][]{{-nozzle.getStepX(), -nozzle.getStepY(), -nozzle.getStepZ()}},
                new double[]{Math.max(0, getEffectiveMaxThrustForComputer())}, new double[]{halfAngleDeg}, 1, 0);
        createThrusters$applyingForce = true;
        try{ setAllocatedForce(projected); }
        finally{ createThrusters$applyingForce = false; }
        var component = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        long tick = component.getLevel() == null ? 0 : component.getLevel().getGameTime();
        Vec3 applied = new Vec3(projected[0], projected[1], projected[2]);
        if(tick - createThrusters$lastSyncTick >= 2 || createThrusters$lastSyncTick == Long.MIN_VALUE){
            if(applied.distanceToSqr(createThrusters$lastSyncedForce) > 1.0E-6D){
                createThrusters$lastSyncedForce = applied;
                createThrusters$lastSyncTick = tick;
                syncToClient();
            }
        }
    }

    // Keep native fuel consumption and visuals on the same allocated force
    @Inject(method = "updateDirectRedstoneForce(Z)V", at = @At("HEAD"), cancellable = true)
    private void createThrusters$retainVectorForce(boolean sync, CallbackInfo ci){
        if(createThrusters$forceOwner == null) return;
        if((getLinkedComputerPos() != null || getLinkedSwivelMountPos() != null)
                && !com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.managed(
                com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.engineComputer((net.minecraft.world.level.block.entity.BlockEntity) (Object) this))){
            releaseVectorControllerForce(createThrusters$forceOwner);
            return;
        }
        createThrusters$applyNativeForce();
        ci.cancel();
    }

    // Allow the native physics tick to consume SCM's live allocated force
    @Inject(method = "shouldProduceDirectRedstoneThrust", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void createThrusters$allowScmDirectThrust(
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (createThrusters$scmDirectThrottleActive
                && active
                && hasNozzle
                && hasPropellantForThrust()) {
            callback.setReturnValue(true);
        }
    }

    // Native computer preview must leave the SCM allocation and its animation untouched
    @Inject(method = "setAllocatedForce", at = @At("HEAD"), cancellable = true)
    private void createThrusters$protectScmAllocation(double[] force, CallbackInfo ci){
        if(com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.computing()
                || createThrusters$scmDirectThrottleActive && !createThrusters$applyingForce) ci.cancel();
    }

    // Preserve native engine physics even when a bound computer retains its physical links
    @Inject(method = "sable$physicsTick", at = @At("HEAD"), cancellable = true)
    private void createThrusters$ownedPhysics(dev.ryanhcode.sable.sublevel.ServerSubLevel body,
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double dt, CallbackInfo ci){
        if(!createThrusters$scmDirectThrottleActive) return;
        ci.cancel();
        double[] force = allocatedForce;
        if(force == null || dt <= 0 || !active || !hasNozzle || isClusterMember() || !hasPropellantForThrust()) return;
        var component = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        var pos = component.getBlockPos();
        float[] offset = getBodyThrustCenterOffset();
        var point = new org.joml.Vector3d(pos.getX() + .5 + offset[0], pos.getY() + .5 + offset[1], pos.getZ() + .5 + offset[2]);
        try{
            ace.flight.physics.FlightPhysicsController.applyPointForce(body, handle, point,
                    new org.joml.Vector3d(force[0], force[1], force[2]).mul(dt), "PROPULSION");
        }catch(Exception err){
            if(!body.isRemoved()) throw new IllegalStateException("Native SCM thrust application failed", err);
        }
    }
}
