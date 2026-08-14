package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDirectThrottleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionPreciseThrottle;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterGraphData;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.TransientThrusterControlSync;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

// Expose Propulsion Thruster graph data
@Pseudo
@Mixin(
        targets = "dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity",
        remap = false
)
public abstract class PropulsionThrusterGraphDataMixin
        implements AdvancedGraphDataProvider, IDirectControlReceiver,
        PropulsionDirectThrottleAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether direct throttle is active
    @Unique
    private boolean createThrusters$directThrottleActive;
    // Current direct throttle
    @Unique
    private float createThrusters$directThrottle;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the direct power
    @Inject(method = "getPower", at = @At("HEAD"), cancellable = true, require = 0)
    private void createThrusters$readDirectPower(
            CallbackInfoReturnable<Float> callback
    ) {
        if (createThrusters$directThrottleActive) {
            callback.setReturnValue(createThrusters$directThrottle);
        }
    }

    // Write the direct throttle for client
    @Inject(method = "write", at = @At("TAIL"), require = 0)
    private void createThrusters$writeDirectThrottleForClient(
            CompoundTag tag,
            HolderLookup.Provider provider,
            boolean clientPacket,
            CallbackInfo callback
    ) {
        TransientThrusterControlSync.writeDirectThrottle(
                tag,
                clientPacket,
                createThrusters$directThrottleActive,
                createThrusters$directThrottle);
    }

    // Read the direct throttle for client
    @Inject(method = "read", at = @At("TAIL"), require = 0)
    private void createThrusters$readDirectThrottleForClient(
            CompoundTag tag,
            HolderLookup.Provider provider,
            boolean clientPacket,
            CallbackInfo callback
    ) {
        TransientThrusterControlSync.DirectThrottle directThrottle =
                TransientThrusterControlSync.readDirectThrottle(tag, clientPacket);
        createThrusters$directThrottleActive = directThrottle.active();
        createThrusters$directThrottle = directThrottle.throttle();
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        PropulsionPreciseThrottle.apply(
                (AbstractThrusterBlockEntity) (Object) this, val);
    }

    // Set the direct throttle
    @Override
    public void createThrusters$setDirectThrottle(double throttle) {
        AbstractThrusterBlockEntity self =
                (AbstractThrusterBlockEntity) (Object) this;
        AbstractThrusterBlockEntity target =
                PropulsionPreciseThrottle.resolveController(self);
        if (target != self && target instanceof PropulsionDirectThrottleAccess access) {
            access.createThrusters$setDirectThrottle(throttle);
            return;
        }
        createThrusters$directThrottle =
                PropulsionPreciseThrottle.normalize(throttle);
        createThrusters$directThrottleActive = true;
        self.dirtyThrust();
        self.notifyUpdate();
    }

    // Clear the direct throttle
    @Override
    public void createThrusters$clearDirectThrottle() {
        AbstractThrusterBlockEntity self =
                (AbstractThrusterBlockEntity) (Object) this;
        AbstractThrusterBlockEntity target =
                PropulsionPreciseThrottle.resolveController(self);
        if (target != self && target instanceof PropulsionDirectThrottleAccess access) {
            access.createThrusters$clearDirectThrottle();
            return;
        }
        if (!createThrusters$directThrottleActive) {
            return;
        }
        createThrusters$directThrottleActive = false;
        createThrusters$directThrottle = 0.0F;
        self.dirtyThrust();
        self.notifyUpdate();
    }

    // Check if this has direct throttle
    @Override
    public boolean createThrusters$hasDirectThrottle() {
        AbstractThrusterBlockEntity self =
                (AbstractThrusterBlockEntity) (Object) this;
        AbstractThrusterBlockEntity target =
                PropulsionPreciseThrottle.resolveController(self);
        return target != self && target instanceof PropulsionDirectThrottleAccess access
                ? access.createThrusters$hasDirectThrottle()
                : createThrusters$directThrottleActive;
    }

    // Get the direct throttle
    @Override
    public float createThrusters$getDirectThrottle() {
        AbstractThrusterBlockEntity self =
                (AbstractThrusterBlockEntity) (Object) this;
        AbstractThrusterBlockEntity target =
                PropulsionPreciseThrottle.resolveController(self);
        return target != self && target instanceof PropulsionDirectThrottleAccess access
                ? access.createThrusters$getDirectThrottle()
                : createThrusters$directThrottle;
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("throttle", "number");
        PropulsionVectorThrusterGraphData.addReadablePorts(ports, this);
        return ports;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("throttle", "number");
        PropulsionVectorThrusterGraphData.addWritablePorts(ports, this);
        return ports;
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        if ("throttle".equals(field)) {
            return AdvancedGraphDocument.Value.number(
                    createThrusters$hasDirectThrottle()
                            ? createThrusters$getDirectThrottle()
                            : ((AbstractThrusterBlockEntity) (Object) this).getThrottle());
        }
        AdvancedGraphDocument.Value vectorData = PropulsionVectorThrusterGraphData.read(this, field);
        return vectorData == null ? AdvancedGraphDocument.Value.number(0.0D) : vectorData;
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        if ("throttle".equals(field)) {
            return val != null && PropulsionPreciseThrottle.apply(
                    (AbstractThrusterBlockEntity) (Object) this, val.asNumber());
        }
        return PropulsionVectorThrusterGraphData.write(this, field, val);
    }
}
