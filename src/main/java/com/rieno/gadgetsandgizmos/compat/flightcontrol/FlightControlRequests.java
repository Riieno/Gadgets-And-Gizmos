package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.physics.SableBodyFrameView;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmWrenchSourceRegistry;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Publish native control requests without giving native computers SCM's actuators
public final class FlightControlRequests{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<BlockEntity, Sample> SAMPLES = new ConcurrentHashMap<>();
    private static final ThreadLocal<AdvancedContraptionControllerBlockEntity> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_ACTUATORS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle> HANDLE = new ThreadLocal<>();
    private static final ThreadLocal<Vec3> COMPENSATION = ThreadLocal.withInitial(() -> Vec3.ZERO);

    private FlightControlRequests(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static AdvancedContraptionControllerBlockEntity host(BlockEntity component){
        if(component == null) return null;
        return FlightControlIntegration.componentHost(component) instanceof AdvancedContraptionControllerBlockEntity host ? host : null;
    }

    public static boolean managed(BlockEntity component){
        var host = host(component);
        return host != null && host.hasShipControlModule();
    }

    public static Object frame(BlockEntity component, Object body){
        var host = host(component);
        return host == null ? body : frame(host, body);
    }

    public static Object frame(Object body){
        var host = CONTEXT.get();
        return host == null ? body : frame(host, body);
    }

    private static Object frame(AdvancedContraptionControllerBlockEntity host, Object body){
        if(body instanceof SableBodyFrameView || !(body instanceof SubLevel subLevel)) return body;
        return new SableBodyFrameView(subLevel, FlightControlIntegration.orientation(host));
    }

    // Scope native direct-control pose sampling and restore any enclosing evaluation
    public static Scope enter(BlockEntity component, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle){
        var prev = CONTEXT.get();
        var prevHandle = HANDLE.get();
        Vec3 prevCompensation = COMPENSATION.get();
        CONTEXT.set(host(component));
        HANDLE.set(handle);
        COMPENSATION.set(Vec3.ZERO);
        return () -> {
            if(prev == null) CONTEXT.remove(); else CONTEXT.set(prev);
            if(prevHandle == null) HANDLE.remove(); else HANDLE.set(prevHandle);
            COMPENSATION.set(prevCompensation);
        };
    }

    public static dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle(){ return HANDLE.get(); }
    public static boolean inFrame(){ return CONTEXT.get() != null; }
    public static Vec3 compensation(){ return COMPENSATION.get(); }
    public static void recordCompensation(Vec3 val){ if(inFrame() && finite(val)) COMPENSATION.set(COMPENSATION.get().add(val)); }

    public interface Scope extends AutoCloseable{ @Override void close(); }

    public static boolean computing(){ return SUPPRESS_ACTUATORS.get(); }

    public static Scope suppressActuators(){
        boolean prev = SUPPRESS_ACTUATORS.get();
        SUPPRESS_ACTUATORS.set(true);
        return () -> SUPPRESS_ACTUATORS.set(prev);
    }

    public static void publish(BlockEntity component, Vec3 force, Vec3 torque, boolean active){
        publish(component, force, torque, Vec3.ZERO, active);
    }

    public static void publish(BlockEntity component, Vec3 force, Vec3 torque, Vec3 compensation, boolean active){
        var host = host(component);
        if(host == null || host.getLevel() == null || !host.hasShipControlModule()) return;
        if(!finite(force) || !finite(torque) || !finite(compensation)){ SAMPLES.remove(component); return; }
        SAMPLES.put(component, new Sample(host, force, torque, compensation, rotationActions(component), active,
                host.getLevel().getGameTime(), System.nanoTime()));
    }

    // Declare authored attitude axes independently of damping and world-frame yaw torque
    public static Set<String> rotationActions(BlockEntity component){
        if(component instanceof ace.flight.api.WrenchContributor contributor && !contributor.isContributing()) return Set.of();
        if(component instanceof ace.flight.block.MouseFlightControllerBlockEntity mouse){
            var host = host(component);
            var runtime = host == null ? null : FlightControlIntegration.runtime(host);
            if(runtime == null || !runtime.mouseRequested(mouse)) return Set.of();
            return Set.of("ship_yaw", "ship_pitch", "ship_roll");
        }
        if(component instanceof ace.flight.block.AttitudeHoldBlockEntity){
            var state = ace.flight.control.FlightControllerStore.get(component);
            return state.attitudeIgnoreYawError ? Set.of("ship_pitch", "ship_roll") : Set.of("ship_yaw", "ship_pitch", "ship_roll");
        }
        if(component instanceof ace.flight.block.AngularVelocityCouplerBlockEntity){
            var state = ace.flight.control.FlightControllerStore.get(component);
            Set<String> res = new java.util.LinkedHashSet<>();
            if(state.enableWx && Math.abs(state.targetWx) > 1.0E-5D) res.add("ship_pitch");
            if(state.enableWy && Math.abs(state.targetWy) > 1.0E-5D) res.add("ship_yaw");
            if(state.enableWz && Math.abs(state.targetWz) > 1.0E-5D) res.add("ship_roll");
            return Set.copyOf(res);
        }
        if(component instanceof ace.flight.block.FlightControlComputerBlockEntity){
            var host = host(component);
            var runtime = host == null ? null : FlightControlIntegration.runtime(host);
            return runtime == null ? Set.of() : runtime.rotationActions(component);
        }
        if(component instanceof ace.flight.block.CreativeFlightControlComputerBlockEntity) return Set.of("ship_yaw", "ship_pitch", "ship_roll");
        return Set.of();
    }

    public static ScmWrenchSourceRegistry.Wrench sample(ScmWrenchSourceRegistry.Request request){
        if(!(request.host() instanceof AdvancedContraptionControllerBlockEntity host)) return ScmWrenchSourceRegistry.Wrench.NONE;
        var body = SableLevelApi.containing(host);
        var root = host.getLevel() == null ? null : SableLevelApi.subLevel(host.getLevel(), request.rootSubLevelId());
        if(body == null || root == null || host.isShipControlInitializing()) return ScmWrenchSourceRegistry.Wrench.NONE;
        Vec3 force = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        Vec3 compensation = Vec3.ZERO;
        boolean active = false;
        Set<String> rotationActions = new java.util.LinkedHashSet<>();
        long now = System.nanoTime();
        for(var pair : SAMPLES.entrySet()){
            Sample val = pair.getValue();
            if(val.host != host) continue;
            if(pair.getKey().isRemoved() || request.tick() - val.tick > 2 || now - val.time > 250_000_000L){
                SAMPLES.remove(pair.getKey(), val);
                continue;
            }
            if(!val.active) continue;
            active = true;
            force = force.add(val.force);
            torque = torque.add(val.torque);
            compensation = compensation.add(val.compensation);
            rotationActions.addAll(val.rotationActions);
        }
        Vector3d f = body.logicalPose().orientation().transform(new Vector3d(force.x, force.y, force.z));
        Vector3d t = body.logicalPose().orientation().transform(new Vector3d(torque.x, torque.y, torque.z));
        Vector3d c = body.logicalPose().orientation().transform(new Vector3d(compensation.x, compensation.y, compensation.z));
        root.logicalPose().orientation().transformInverse(f);
        root.logicalPose().orientation().transformInverse(t);
        root.logicalPose().orientation().transformInverse(c);
        Vec3 resultForce = new Vec3(f.x, f.y, f.z);
        var localCenter = body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server
                ? server.getMassTracker().getCenterOfMass() : null;
        if(localCenter != null){
            var worldCenter = body.logicalPose().transformPosition(localCenter, new Vector3d());
            root.logicalPose().transformPositionInverse(worldCenter);
            torque = new Vec3(t.x, t.y, t.z).add(new Vec3(worldCenter.x, worldCenter.y, worldCenter.z)
                    .subtract(request.centerOfMass()).cross(resultForce));
        }else torque = new Vec3(t.x, t.y, t.z);
        return new ScmWrenchSourceRegistry.Wrench(active, resultForce, torque, new Vec3(c.x, c.y, c.z), rotationActions);
    }

    public static void remove(AdvancedContraptionControllerBlockEntity host){ SAMPLES.entrySet().removeIf(pair -> pair.getValue().host == host); }
    public static void remove(BlockEntity component){ SAMPLES.remove(component); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static boolean finite(Vec3 val){ return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z); }
    private record Sample(AdvancedContraptionControllerBlockEntity host, Vec3 force, Vec3 torque, Vec3 compensation,
                          Set<String> rotationActions, boolean active, long tick, long time){}
}
