package com.rieno.gadgetsandgizmos.compat.letitwalk;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsBodyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotHeightControl;
import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.Map;
import java.util.WeakHashMap;

// Extend the native jet controller to hold absolute altitude while preserving native thrust, fuel and power
public final class LetItWalkFlightControl{
    // Retain requests without keeping unloaded walker cores or worlds alive
    private static final Map<WalkerCoreBlockEntity, Request> REQUESTS = new WeakHashMap<>();

    // Prevent flight controller instances
    private LetItWalkFlightControl(){}

    // Track connector altitude during navigation and integrate explicit manual climb or descent
    public static void request(Object owner, WalkerCoreBlockEntity core, ServerSubLevel body,
                               ScmPilotControl.Navigation navigation, double lift, double maximumSpeed){
        long tick = core.getLevel().getGameTime();
        double y = body.logicalPose().transformPosition(core.getBlockPos().getCenter()).y;
        Request prev = REQUESTS.get(core);
        double altitude = prev != null && prev.owner() == owner && tick - prev.tick() <= 10 ? prev.altitude() : y;
        if(navigation != null) altitude = y + SableTransformApi.toWorldVector(body, navigation.offset()).y;
        else if(prev == null || prev.tick() != tick) altitude += Math.clamp(lift, -1, 1) * 4.0D * 0.05D;
        double tolerance = navigation == null ? 0.05D : Math.min(0.1D, navigation.tolerance());
        var handle = RigidBodyHandle.of(body);
        double referenceVelocity = navigation != null && navigation.docking() && handle.isValid()
                ? handle.getLinearVelocity(new Vector3d()).y - SableTransformApi.toWorldVector(body, navigation.velocity()).y : 0;
        double ceiling = maximumSpeed > 0 ? navigation == null || navigation.altitudeOnly()
                ? maximumSpeed : Math.min(maximumSpeed, navigation.speed()) : -1;
        double verticalSpeed = navigation != null && navigation.altitudeOnly() ? navigation.speed() : -1;
        REQUESTS.put(core, new Request(owner, tick, altitude, tolerance, referenceVelocity, ceiling, verticalSpeed));
        apply(core, body);
    }

    // Reapply altitude after the native controller computes its terrain-relative hover target
    public static void apply(WalkerCoreBlockEntity core, ServerSubLevel body){
        Request request = REQUESTS.get(core);
        if(request == null || core.getLevel() == null) return;
        long age = core.getLevel().getGameTime() - request.tick();
        if(age < 0 || age > 10 || !core.flying || core.jetsStopping || core.parked || !core.isPowered()
                || !SablePhysicsBodyApi.isLoaded(body)){
            REQUESTS.remove(core);
            return;
        }
        var handle = RigidBodyHandle.of(body);
        if(!handle.isValid()) return;
        double y = body.logicalPose().transformPosition(core.getBlockPos().getCenter()).y;
        double velocity = handle.getLinearVelocity(new Vector3d()).y;
        double desired = Math.clamp(request.referenceVelocity() + ScmPilotHeightControl.velocity(request.altitude() - y,
                velocity - request.referenceVelocity(), request.tolerance(), 4, 6), -6, 4);
        if(request.maximumSpeed() >= 0){
            var motion = handle.getLinearVelocity(new Vector3d());
            double vertical = Math.sqrt(Math.max(0, request.maximumSpeed() * request.maximumSpeed() - motion.x * motion.x - motion.z * motion.z));
            desired = Math.clamp(desired, -vertical, vertical);
        }
        if(request.verticalSpeed() >= 0) desired = Math.clamp(desired, -request.verticalSpeed(), request.verticalSpeed());
        double power = Math.clamp(core.jetPower, 0, 1);
        desired *= power * power * (3 - 2 * power);
        double scale = Math.max(0.01D, core.frameScale);
        double slope = Math.min(-(core.terrainNormal.x * core.targetVelocity.x + core.terrainNormal.z * core.targetVelocity.z)
                / Math.max(0.3D, core.terrainNormal.y), core.groundFallRate);
        core.heightError = (desired / scale - slope) / 4.0D;
    }

    // Remove only the active pilot owner's altitude override
    public static void release(Object owner, WalkerCoreBlockEntity core){
        Request request = REQUESTS.get(core);
        if(request != null && request.owner() == owner) REQUESTS.remove(core);
    }

    // Store detached altitude goals without references back to the core or pilot
    private record Request(Object owner, long tick, double altitude, double tolerance, double referenceVelocity, double maximumSpeed, double verticalSpeed){}
}
