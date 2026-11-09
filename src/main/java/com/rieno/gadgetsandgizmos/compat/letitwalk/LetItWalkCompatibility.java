package com.rieno.gadgetsandgizmos.compat.letitwalk;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnection;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnectionRegistry;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsBodyApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotNavigationMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotSpeedControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotHeightControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSpeedLimit;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.letitwalk.content.LegFootBlock;
import dev.letitwalk.content.LegJointBlockEntity;
import dev.letitwalk.content.PilotControlsBlockEntity;
import dev.letitwalk.content.WalkerCoreBlock;
import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.WalkerBrain;
import dev.letitwalk.walker.WalkerFlight;
import dev.letitwalk.walker.WalkerClimb;
import dev.letitwalk.walker.WalkerFuel;
import dev.letitwalk.WalkersConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

// Adapt Let It Walk 0.1.4 without replacing its saved joints or native gait controller
public final class LetItWalkCompatibility{
    // Keep native compatibility registrations in the addon's namespace
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("createthrusters", "let_it_walk");

    // Prevent compatibility bridge instances
    private LetItWalkCompatibility(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Load this bridge only after the optional mod has been detected
    public static void register(){
        ScmControlModeRegistry.register(new ScmPilotNavigationMode(
                ResourceLocation.fromNamespaceAndPath("createthrusters", "walker"), "Walker",
                LetItWalkCompatibility::isWalker, LetItWalkCompatibility::profile));
        LetItWalkNodes.register();
        SableAssemblyConnectionRegistry.register(ID,
                val -> val instanceof WalkerCoreBlockEntity || val instanceof LegJointBlockEntity,
                LetItWalkCompatibility::connections);
        ScmControlProbeRegistry.register(ID, 200, (blockEntity, ctx) ->
                blockEntity instanceof PilotControlsBlockEntity station
                        ? List.of(new PilotProbe(station, ctx.target())) : List.of());
    }

    // Require both native blocks on the ACC's own loaded sublevel
    public static boolean isWalker(BlockEntity host){
        var body = SableLevelApi.containing(host);
        return findCore(host) != null && !SubLevelBlockEntityCollector.getBlockEntities(body,
                val -> val instanceof PilotControlsBlockEntity).isEmpty();
    }

    // Find a native core without requiring pilot controls or forcing chunks to load
    public static WalkerCoreBlockEntity findCore(BlockEntity host){
        return SubLevelBlockEntityCollector.getBlockEntities(SableLevelApi.containing(host),
                val -> val instanceof WalkerCoreBlockEntity).stream()
                .map(val -> (WalkerCoreBlockEntity)val).findFirst().orElse(null);
    }

    // Use the core's native forward direction for automatic SCM orientation
    public static Direction forward(BlockEntity host){
        var core = findCore(host);
        return core == null ? Direction.NORTH : core.getBlockState().getValue(WalkerCoreBlock.FACING);
    }

    // Inspect live feet instead of treating every assembled walker as ordinary ground running gear
    public static ScmPilotControl.Profile profile(BlockEntity host){
        var core = findCore(host);
        return core == null ? ScmPilotControl.Profile.GROUND : coreProfile(core);
    }

    // Refresh native foot kinds from loaded blocks before applying the mod's own capability thresholds
    private static ScmPilotControl.Profile coreProfile(WalkerCoreBlockEntity core){
        var legs = core.walking();
        for(var leg : legs){
            var pos = leg.built.footBlockPlot();
            if(core.getLevel() == null || !core.getLevel().isLoaded(pos)) continue;
            var block = core.getLevel().getBlockState(pos).getBlock();
            leg.built.footKind = block instanceof LegFootBlock foot ? foot.kind : LegFootBlock.Kind.NORMAL;
            leg.built.footKindKnown = true;
        }
        return new ScmPilotControl.Profile(WalkerClimb.canClimb(legs), WalkerFlight.canFly(legs), core.flying);
    }

    // Include every limb even when storage starts from a foot or an intermediate segment
    private static List<SableAssemblyConnection> connections(ServerSubLevel owner, BlockEntity blockEntity){
        List<SableAssemblyConnection> res = new ArrayList<>();
        if(blockEntity instanceof WalkerCoreBlockEntity core){
            for(var leg : core.legs){
                for(var id : leg.built.segments) res.add(SableAssemblyConnection.structural(id));
                for(var joint : leg.built.joints){
                    res.add(SableAssemblyConnection.structural(joint.parent));
                    res.add(SableAssemblyConnection.structural(joint.child));
                }
            }
        }else if(blockEntity instanceof LegJointBlockEntity joint && joint.child() != null){
            res.add(SableAssemblyConnection.structural(joint.child()));
        }
        return res;
    }

    // Own a native pilot input while retaining the ordinary SCM probe lifecycle
    private static final class PilotProbe implements ScmPilotControl{
        // Retain the station identity and target body for safe rebinding
        private final PilotControlsBlockEntity station;
        private final ScmTarget target;
        // Track the last input owned by this probe so a later player input is preserved
        private WalkerCoreBlockEntity controlledCore;
        private long lastAppliedTick = Long.MIN_VALUE;
        private int lastKeys;
        // Regulate native gear input independently of the SCM's corrective force
        private final ScmPilotSpeedControl speedControl = new ScmPilotSpeedControl();
        private final ScmPilotSpeedControl strafeSpeedControl = new ScmPilotSpeedControl();
        private final Object flightOwner = new Object();

        // Bind the selected pilot controls without taking control during discovery
        private PilotProbe(PilotControlsBlockEntity station, ScmTarget target){
            this.station = station;
            this.target = target;
        }

        // Identify the stored adapter independently of its selected block face
        @Override public String adapterId(){ return "let_it_walk_pilot"; }
        // Name the native control in SCM initialization
        @Override public String displayName(){ return "Let It Walk Pilot Controls"; }
        // Suppress injected redstone fallbacks for this native controller
        @Override public boolean suppressesFallbackProbes(){ return true; }
        // Keep duplicate faces on one pilot station under one control owner
        @Override public String controlGroupId(){ return "let_it_walk:" + target.blockStableId(); }
        // Expose a neutral-to-forward initialization range
        @Override public double minControl(){ return 0.0D; }
        // Expose the full native pilot range
        @Override public double maxControl(){ return 1.0D; }
        // Probe forward motion through the same native input used during navigation
        @Override public void apply(double control){ drive(localEffectDirection().scale(control), Vec3.ZERO); }
        // Locate the effect at the pilot station in plot coordinates
        @Override public Vec3 localEffectPosition(){ return station.getBlockPos().getCenter(); }
        // Follow the walker core's orientation rather than the pilot block's decoration
        @Override public Vec3 localEffectDirection(){
            WalkerCoreBlockEntity core = core();
            Direction facing = core == null ? Direction.NORTH : core.getBlockState().getValue(WalkerCoreBlock.FACING);
            return Vec3.atLowerCornerOf(facing.getNormal());
        }
        // Treat a missing or unloaded walker as an unavailable native controller
        @Override public boolean isAvailable(){ return core() != null; }
        // Describe current foot capabilities to native pilot consumers
        @Override public Profile profile(){
            var core = core();
            return core == null ? Profile.GROUND : coreProfile(core);
        }
        // Report native power without inventing a physical force model
        @Override public Reading read(){
            WalkerCoreBlockEntity core = core();
            return core == null ? new Reading(0, 0, false)
                    : new Reading(Math.abs(core.getTheoreticalSpeed()), Math.abs(core.throttle), core.isPowered());
        }

        // Combine movement and steering in one input instead of competing scalar probes
        @Override public void drive(Vec3 force, Vec3 torque){
            drive(force, torque, false, false);
        }

        // Preserve native held actions alongside movement and steering
        @Override public void drive(Vec3 force, Vec3 torque, boolean jump, boolean crouch){
            drive(new Command(force, torque, jump, crouch, false, null));
        }

        // Select native gears, body height and flight from the current navigation request
        @Override public void drive(Command command){
            WalkerCoreBlockEntity core = core();
            if(core == null) return;
            if(controlledCore != core){
                restore();
                controlledCore = core;
            }
            var capabilities = coreProfile(core);
            var body = SableLevelApi.containing(core);
            if(!(body instanceof ServerSubLevel sub)) return;
            var navigation = command.navigation();
            Vec3 force = command.force();
            if(navigation != null && !navigation.altitudeOnly() && (capabilities.canFly() || core.flying)) force = navigation.offset().normalize();
            Input input = ScmPilotControl.input(force, command.torque(), localEffectDirection(), new Vec3(0, 1, 0));
            boolean flightRequested = command.hover() || navigation != null && capabilities.canFly() && !navigation.docking()
                    || capabilities.canFly() && Math.abs(input.lift()) > 0.05D;
            if(capabilities.canFly() && flightRequested && !core.flying && core.isPowered() && !core.parked
                    && core.upright() && WalkersConfig.jetFlight() && WalkerFuel.hasLava(core)
                    && !WalkerFlight.flooded(core, core.walking())) WalkerFlight.toggle(core, sub, core.walking(), true);
            if(core.flying && !capabilities.canFly() && !core.jetsStopping) WalkerFlight.toggle(core, sub, core.walking(), core.isPowered());

            double baseSpeed = core.walking().stream().mapToDouble(leg -> leg.gait().speed()).min().orElse(0);
            baseSpeed *= Math.max(0.01D, core.frameScale) * (core.flying ? WalkerFlight.FLIGHT_SPEED : 1);
            int maxGear = WalkerBrain.maxGear(core.getTheoreticalSpeed());
            double[] speeds = new double[maxGear + 1];
            for(int idx = 0; idx < speeds.length; idx++) speeds[idx] = baseSpeed * WalkerBrain.GEAR_SPEED[idx];
            double effort = Math.max(Math.abs(input.forward()), Math.abs(input.strafe()));
            double requestedSpeed = navigation == null || navigation.altitudeOnly() ? speeds[maxGear] * effort : navigation.speed();
            requestedSpeed = new ScmSpeedLimit(command.maximumSpeed()).cap(requestedSpeed);
            if(navigation != null && !navigation.altitudeOnly() && core.flying){
                Vec3 offset = SableTransformApi.toWorldVector(sub, navigation.offset());
                requestedSpeed *= offset.length() > 1.0E-9D ? Math.hypot(offset.x, offset.z) / offset.length() : 0;
            }
            int gear = ScmPilotSpeedControl.gear(requestedSpeed, speeds);
            if(navigation != null){
                core.gear = gear;
                if(requestedSpeed <= 1.0E-5D){
                    core.throttle = 0;
                    core.strafe = 0;
                }
            }
            var handle = RigidBodyHandle.of(sub);
            var measured = handle.isValid() ? handle.getLinearVelocity(new Vector3d()) : new Vector3d();
            Vec3 velocity = navigation != null ? navigation.velocity() : command.maximumSpeed() > 0 && handle.isValid()
                    ? SableTransformApi.toLocalVector(sub, new Vec3(measured.x, measured.y, measured.z)) : Vec3.ZERO;
            Vec3 forward = localEffectDirection();
            Vec3 right = forward.cross(new Vec3(0, 1, 0));
            double plane = Math.hypot(input.forward(), input.strafe());
            double forwardSpeed = plane > 1.0E-9D ? requestedSpeed * Math.abs(input.forward()) / plane : 0;
            double strafeSpeed = plane > 1.0E-9D ? requestedSpeed * Math.abs(input.strafe()) / plane : 0;
            long tick = core.getLevel().getGameTime();
            boolean held = speedControl.drive(tick, forwardSpeed,
                    velocity.dot(forward) * Math.signum(input.forward()), speeds[gear]);
            boolean strafeHeld = strafeSpeedControl.drive(tick, strafeSpeed,
                    velocity.dot(right) * Math.signum(input.strafe()), speeds[gear] * 0.7D);
            int keys = held ? (input.forward() > 0.05D ? 1 : input.forward() < -0.05D ? 2 : 0) : 0;
            if(strafeHeld && Math.abs(input.strafe()) > 0.05D){
                keys |= WalkerBrain.KEY_CRAB | (input.strafe() > 0.0D ? 8 : 4);
                core.receiveTurn(-input.yaw() * 0.04D);
            }else if(navigation == null){
                keys |= input.yaw() > 0.05D ? 8 : input.yaw() < -0.05D ? 4 : 0;
            }else core.receiveTurn(-input.yaw() * 0.04D);
            if(command.jump()) keys |= WalkerBrain.KEY_JUMP;
            else if(!core.flying){
                int height = navigation != null && navigation.docking()
                        ? ScmPilotHeightControl.direction(navigation.offset().y, Math.min(0.1D, navigation.tolerance()))
                        : ScmPilotHeightControl.direction(input.lift(), 0.05D);
                keys |= height > 0 ? WalkerBrain.KEY_UP : height < 0 ? WalkerBrain.KEY_DOWN : 0;
            }
            if(command.crouch()) keys = (keys & ~WalkerBrain.KEY_UP) | WalkerBrain.KEY_DOWN;
            keys |= gear << WalkerBrain.GEAR_SHIFT;
            core.receiveLean(input.roll() * WalkerBrain.MAX_LEAN, input.pitch() * WalkerBrain.MAX_LEAN);
            lastAppliedTick = core.getLevel().getGameTime();
            lastKeys = keys;
            core.receiveInput(keys, lastAppliedTick);
            if(core.flying && !core.jetsStopping){
                LetItWalkFlightControl.request(flightOwner, core, sub, navigation, navigation == null ? input.lift() : 0, command.maximumSpeed());
            }else LetItWalkFlightControl.release(flightOwner, core);
            showKeys(keys & 0xF);
        }

        // Release only inputs still owned by this probe and stop renewing native movement
        @Override public void restore(){
            WalkerCoreBlockEntity core = controlledCore;
            if(core != null) LetItWalkFlightControl.release(flightOwner, core);
            if(core != null && SablePhysicsBodyApi.isBlockEntityLoaded(core)
                    && core.lastInputTime == lastAppliedTick && core.input == lastKeys){
                core.throttle = 0;
                core.strafe = 0;
                core.receiveInput(0, core.getLevel().getGameTime());
                core.receiveLean(0, 0);
                showKeys(0);
            }
            controlledCore = null;
            lastAppliedTick = Long.MIN_VALUE;
        }

        // Resolve the assembled core on this physical walker without loading remote chunks
        private WalkerCoreBlockEntity core(){
            if(!(station.getLevel() instanceof ServerLevel level) || station.isRemoved()
                    || !SablePhysicsBodyApi.isBlockEntityLoaded(station)
                    || !(SableLevelApi.subLevel(level, target.subLevelId()) instanceof ServerSubLevel owner)) return null;
            if(station.linkedWalker != null){
                WalkerCoreBlockEntity core = WalkerCoreBlockEntity.linked(level.getServer(), station.linkedWalker);
                if(core == null || core.getLevel() != level || !core.isAssembled()
                        || !SablePhysicsBodyApi.isBlockEntityLoaded(core)) return null;
                var body = SableLevelApi.containing(level, core.getBlockPos());
                return body != null && (body.getUniqueId().equals(owner.getUniqueId())
                        || core.legs.stream().anyMatch(leg -> leg.built.segments.contains(owner.getUniqueId()))) ? core : null;
            }
            return SubLevelBlockEntityCollector.getBlockEntities(owner,
                    val -> val instanceof WalkerCoreBlockEntity).stream()
                    .filter(val -> SablePhysicsBodyApi.isBlockEntityLoaded(val))
                    .map(val -> (WalkerCoreBlockEntity)val).filter(WalkerCoreBlockEntity::isAssembled).findFirst().orElse(null);
        }

        // Synchronize the visible pilot levers only when their displayed input changes
        private void showKeys(int keys){
            if(station.isRemoved() || station.getLevel() == null || station.keys == keys) return;
            station.keys = keys;
            station.setChanged();
            station.getLevel().sendBlockUpdated(station.getBlockPos(), station.getBlockState(), station.getBlockState(), 3);
        }
    }
}
