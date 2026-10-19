package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import com.rieno.gadgetsandgizmos.lib.physics.ProjectedLightSource;
import com.rieno.gadgetsandgizmos.lib.view.ControlledViewSource;
import com.rieno.gadgetsandgizmos.lib.view.RaycastViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlState;
import com.rieno.gadgetsandgizmos.lib.view.ViewRayBudget;
import com.rieno.gadgetsandgizmos.lib.view.ViewTransforms;
import com.rieno.gadgetsandgizmos.lib.view.ViewTargetLock;
import com.rieno.gadgetsandgizmos.lib.view.ViewTargetFraming;
import com.rieno.gadgetsandgizmos.lib.view.ViewTrackingTargets;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Own the Camera block's saved configuration and ray samples
public final class CameraBlockEntity extends SmartBlockEntity implements ControlledViewSource, RaycastViewSource,
        com.rieno.gadgetsandgizmos.lib.view.PredictedViewSource{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the camera controls independently of the node graph
    private final ViewRig rig = new ViewRig();
    private ViewRig.Mode mode = ViewRig.Mode.LOCKED;
    private ViewRig.Orientation orientation = ViewRig.Orientation.LOCAL;
    private ViewRaycast.Filter filter = new ViewRaycast.Filter(Set.of(), false);
    private double maxRayLength = 64;
    private double raysPerSecond = 20;
    private final ViewRayBudget rayBudget = new ViewRayBudget();
    private ViewRig.Mode overrideMode;
    private double graphPan;
    private double graphTilt;
    private boolean flashlight;
    private boolean autoTracking;
    private Boolean overrideTracking;
    private boolean lockTarget;
    private Boolean overrideTargetLock;
    private final ViewTargetLock<ViewTrackingTargets.Reference> targetLock = new ViewTargetLock<>();
    private boolean tracking;
    private AABB trackingBounds;
    private double patrolFov = 70;
    private ProjectedLightSource floodlight;
    // Synchronize exclusive mouse ownership without changing the configured mode
    private boolean controlled;
    // Cache all samples once per game tick
    private long sampledTick = Long.MIN_VALUE;
    private long raySampleTick = Long.MIN_VALUE;
    private List<ViewRaycast.Result> rays = List.of();
    private ViewRaycast.Result nearest = ViewRaycast.Result.miss(Vec3.ZERO, 0);
    private Quaterniond shapeMount;
    private Quaterniond shapeStand;
    private Quaterniond shapeCamera;
    private VoxelShape cachedShape;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the registered camera block entity
    public CameraBlockEntity(BlockPos pos, BlockState state){
        super(CTBlockEntities.CAMERA.get(), pos, state);
    }
    // Retain Create's standard block entity lifecycle
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    // Sample the active lens on the owning server thread
    @Override
    public void tick(){
        super.tick();
        if(level != null && !level.isClientSide){
            sampleRays();
            updateTracking();
        }
        updateViewEffects(1);
    }
    // Publish ordinary block lighting to every player and secondary camera renderer
    @Override
    public void updateViewEffects(float partialTick){
        if(level == null || level.isClientSide) return;
        if(flashlight){
            if(floodlight == null) floodlight = new ProjectedLightSource(
                    com.rieno.gadgetsandgizmos.registry.CTBlocks.PLUME_LIGHT.get().defaultBlockState());
            floodlight.update(level, viewPose(partialTick), maxRayLength, ViewReference.of(this));
        }else if(floodlight != null) floodlight.close();
    }
    // Release the projected light when the camera is removed or its chunk unloads
    @Override
    public void invalidate(){ if(floodlight != null) floodlight.close(); super.invalidate(); }
    // Apply graph configuration while a remote viewer retains mouse ownership
    public void configure(double length, double count, ViewRaycast.Filter filter, ViewRig.Mode mode,
                          ViewRig.Orientation orientation, double pan, double tilt){
        configure(length, count, filter, mode, orientation, pan, tilt, autoTracking);
    }
    // Retain an explicit display tracking choice until the graph changes its tracking default
    public void configure(double length, double count, ViewRaycast.Filter filter, ViewRig.Mode mode,
                          ViewRig.Orientation orientation, double pan, double tilt, boolean autoTracking){
        configure(length, count, filter, mode, orientation, pan, tilt, autoTracking, lockTarget);
    }
    // Let graph defaults select dynamic acquisition without resetting an unchanged display override
    public void configure(double length, double count, ViewRaycast.Filter filter, ViewRig.Mode mode,
                          ViewRig.Orientation orientation, double pan, double tilt, boolean autoTracking, boolean lockTarget){
        double nextLength = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        double nextCount = Double.isFinite(count) ? Math.clamp(count, 0, ViewRayBudget.MAX_PER_SECOND) : 0;
        double nextPan = ViewRig.wrapPan(pan);
        double nextTilt = Double.isFinite(tilt) ? Math.clamp(tilt, -135, 135) : 0;
        boolean changed = maxRayLength != nextLength || raysPerSecond != nextCount || !this.filter.equals(filter)
                || this.mode != mode || this.orientation != orientation || this.autoTracking != autoTracking
                || this.lockTarget != lockTarget;
        if(this.autoTracking != autoTracking) overrideTracking = null;
        this.autoTracking = autoTracking;
        if(this.lockTarget != lockTarget) overrideTargetLock = null;
        this.lockTarget = lockTarget;
        maxRayLength = nextLength;
        raysPerSecond = nextCount;
        this.filter = filter;
        if(this.mode != mode || nextPan != graphPan || nextTilt != graphTilt){
            changed |= overrideMode != null;
            overrideMode = null;
        }
        graphPan = nextPan;
        graphTilt = nextTilt;
        this.mode = mode;
        this.orientation = orientation;
        if(!controlled && overrideMode == null && mode == ViewRig.Mode.MANUAL){
            changed |= rig.pan() != nextPan || rig.tilt() != nextTilt;
            rig.aim(nextPan, nextTilt);
        }
        if(changed){
            setChanged();
            sendData();
        }
    }
    // Resolve the floor-model mounting rotation
    public Quaterniond mountOrientation(){
        var dir = getBlockState().getValue(CameraBlock.FACING);
        return new Quaterniond().rotationTo(0, 1, 0, dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }
    // Cache model-box bounds until either rendered joint changes
    public VoxelShape shape(){
        Quaterniond mount = mountOrientation();
        ViewRig.Joints joints = level == null ? ViewRig.joints(new ViewRig.Angles(0, 0)) : joints(0);
        Quaterniond stand = new Quaterniond(mount).mul(joints.stand());
        Quaterniond camera = new Quaterniond(stand).mul(joints.camera());
        if(cachedShape == null || !mount.equals(shapeMount) || !stand.equals(shapeStand) || !camera.equals(shapeCamera)){
            shapeMount = mount;
            shapeStand = stand;
            shapeCamera = camera;
            cachedShape = CameraBlock.shape(mount, stand, camera);
        }
        return cachedShape;
    }
    // Resolve the visual stand and camera transformations
    public ViewRig.Joints joints(float partialTick){
        ViewRig.Angles angles = viewAngles(partialTick);
        if(orientation == ViewRig.Orientation.LOCAL) return ViewRig.joints(angles);
        Quaterniond mount = ViewTransforms.orientation(this, mountOrientation(), partialTick);
        Quaterniond world = ViewRig.worldOrientation(mount, orientation, angles);
        return ViewRig.joints(mount, world);
    }
    // Resolve the live lens using the same joint transform as the partial renderer
    @Override
    public ViewPose viewPose(float partialTick){
        ViewRig.Angles angles = viewAngles(partialTick);
        return viewPose(partialTick, new ViewRig.State(angles.pan(), angles.tilt(), rig.fov()));
    }
    // Supply authoritative joint values while the library previews unacknowledged mouse motion
    @Override
    public ViewRig.State viewAim(){
        ViewRig.Angles angles = viewAngles(0);
        return new ViewRig.State(angles.pan(), angles.tilt(), rig.fov());
    }
    @Override
    public ViewPose viewPose(float partialTick, ViewRig.State aim){
        Quaterniond mount = ViewTransforms.orientation(this, mountOrientation(), partialTick);
        ViewRig.Angles angles = new ViewRig.Angles(aim.pan(), aim.tilt());
        Quaterniond world = ViewRig.worldOrientation(mount, orientation, angles);
        Vec3 pivot = ViewTransforms.position(this, getBlockPos().getCenter(), partialTick);
        var lens = world.transform(new org.joml.Vector3d(0, 0, -5.1D / 16.0D));
        return new ViewPose(pivot.add(lens.x, lens.y, lens.z), world, aim.fov());
    }
    // Fit the same tracked target when a display or tablet requests a portrait capture
    @Override
    public ViewPose viewPose(float partialTick, double aspect){
        ViewPose pose = viewPose(partialTick);
        return tracking && !controlled && trackingBounds != null ? ViewTargetFraming.fit(pose, trackingBounds, aspect) : pose;
    }
    // Forward remote mouse motion to Manual aiming
    @Override
    public void controlView(double panDelta, double tiltDelta, double zoomSteps){
        double prevPan = rig.pan();
        double prevTilt = rig.tilt();
        double prevFov = rig.fov();
        rig.turn(panDelta, tiltDelta);
        rig.zoom(zoomSteps);
        if(prevPan == rig.pan() && prevTilt == rig.tilt() && prevFov == rig.fov()) return;
        setChanged();
        sendData();
    }
    // Transfer mouse ownership while preserving the graph's configured mode
    @Override
    public void setViewControlled(boolean controlled){
        if(controlled && !this.controlled){
            ViewRig.Angles angles = viewAngles(0);
            rig.aim(angles.pan(), angles.tilt());
            stopTracking();
        }
        this.controlled = controlled;
        if(isViewAvailable()) sendData();
    }
    // Check whether the camera still belongs to a loaded block
    @Override
    public boolean isViewAvailable(){ return level != null && !isRemoved(); }
    // Retain tablet settings until the graph changes its configured mode
    private ViewRig.Mode effectiveMode(){ return overrideMode == null ? mode : overrideMode; }
    // Apply explicit aim while tracking without changing the configured sentry mode
    private ViewRig.Angles viewAngles(float partialTick){
        if(tracking && !controlled) return new ViewRig.Angles(rig.pan(), rig.tilt());
        return ViewRig.angles(controlled ? ViewRig.Mode.MANUAL : effectiveMode(),
                level.getGameTime() + partialTick, rig.pan(), rig.tilt());
    }
    private boolean effectiveTracking(){ return overrideTracking == null ? autoTracking : overrideTracking; }
    private boolean effectiveTargetLock(){ return overrideTargetLock == null ? lockTarget : overrideTargetLock; }
    // Expose the same settings to the tablet and projected display
    @Override
    public ViewControlState viewControlState(){
        return new ViewControlState(effectiveMode(), rig.fov(), flashlight, effectiveTracking(), effectiveTargetLock());
    }
    // Keep explicit remote settings independent of unchanged graph defaults
    @Override
    public void applyViewSettings(ViewControlState state){
        if(state.equals(viewControlState()) && overrideMode == state.mode()) return;
        if(state.mode() == ViewRig.Mode.MANUAL && effectiveMode() != ViewRig.Mode.MANUAL){
            ViewRig.Angles angles = viewAngles(0);
            rig.aim(angles.pan(), angles.tilt());
        }
        boolean changedFov = state.fov() != rig.fov();
        if(state.mode() != ViewRig.Mode.SENTRY || !state.autoTracking()) stopTracking();
        overrideMode = state.mode();
        if(changedFov){
            rig.setFov(state.fov());
            if(tracking) patrolFov = rig.fov();
        }
        flashlight = state.flashlight();
        overrideTracking = state.autoTracking();
        overrideTargetLock = state.lockTarget();
        setChanged();
        sendData();
    }
    // Build the existing ACC external source envelope
    public CompoundTag displayFrame(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Format", "camera");
        tag.put("ViewSource", ViewReference.of(this).toTag());
        tag.put("ViewControls", viewControlState().toTag());
        return tag;
    }
    // Read the nearest sample and the complete ray list
    public GraphValue graphValue(String port){
        sampleRays();
        return switch(port){
            case "ray_cast" -> GraphValue.list(rays.stream().map(hit -> hit.details(level)).toList());
            case "hit" -> GraphValue.bool(nearest.hit());
            case "hit_x" -> GraphValue.number(nearest.position().x);
            case "hit_y" -> GraphValue.number(nearest.position().y);
            case "hit_z" -> GraphValue.number(nearest.position().z);
            case "hit_details" -> nearest.details(level);
            case "is_sub_level" -> GraphValue.bool(nearest.subLevelId() != null);
            default -> GraphValue.number(0);
        };
    }
    // Fire one explicit centre ray using the lens range and filters, then publish it to graph outputs
    @Override
    public double interactionRange(){ return maxRayLength; }
    @Override
    public ViewRaycast.Filter interactionFilter(){ return filter; }
    @Override
    public ViewRaycast.Result castViewRay(){
        if(level == null || level.isClientSide) return ViewRaycast.Result.miss(Vec3.ZERO, 0);
        nearest = ViewRaycast.trace(level, viewPose(0), maxRayLength, filter, ViewReference.of(this), 0, 1);
        rays = List.of(nearest);
        sampledTick = raySampleTick = level.getGameTime();
        return nearest;
    }
    // Perform at most the configured ray budget once per tick
    private void sampleRays(){
        if(level == null || level.isClientSide || sampledTick == level.getGameTime()) return;
        sampledTick = level.getGameTime();
        int count = rayBudget.next(sampledTick, raysPerSecond);
        if(count == 0 && raysPerSecond > 0) return;
        raySampleTick = sampledTick;
        ViewPose pose = viewPose(0);
        nearest = ViewRaycast.Result.miss(pose.position().add(pose.forward().scale(maxRayLength)), maxRayLength);
        List<ViewRaycast.Result> samples = new ArrayList<>();
        ViewReference self = ViewReference.of(this);
        for(int idx = 0; idx < count; idx++){
            ViewRaycast.Result hit = ViewRaycast.trace(level, pose, maxRayLength, filter, self, idx, count);
            samples.add(hit);
            if(hit.hit() && (!nearest.hit() || hit.distance() < nearest.distance())) nearest = hit;
        }
        rays = List.copyOf(samples);
    }
    // Reconsider the nearest fresh hit unless the user has locked the current target
    private void updateTracking(){
        if(controlled || effectiveMode() != ViewRig.Mode.SENTRY || !effectiveTracking()){
            if(stopTracking()) sendData();
            return;
        }
        ViewReference self = ViewReference.of(this);
        var excluded = self.subLevelId();
        boolean fresh = raySampleTick == level.getGameTime() && !rays.isEmpty();
        var target = targetLock.update(viewPose(0).position(), maxRayLength,
                () -> fresh
                        ? ViewTrackingTargets.acquire(level, viewPose(0), maxRayLength, filter, self, rays) : List.of(),
                ref -> ViewTrackingTargets.resolve(level, ref, filter, excluded), effectiveTargetLock() || !fresh);
        if(target == null){
            if(stopTracking()) sendData();
            return;
        }
        boolean changed = !tracking || !target.bounds().equals(trackingBounds);
        if(!tracking) patrolFov = rig.fov();
        double prevPan = rig.pan();
        double prevTilt = rig.tilt();
        double prevFov = rig.fov();
        Vec3 pivot = ViewTransforms.position(this, getBlockPos().getCenter(), 0);
        Quaterniond mount = ViewTransforms.orientation(this, mountOrientation(), 0);
        ViewRig.Angles aim = ViewTargetFraming.aim(pivot, target.bounds().getCenter(), mount, orientation);
        rig.track(aim);
        tracking = true;
        trackingBounds = target.bounds();
        double fov = ViewTargetFraming.fieldOfView(viewPose(0), target.bounds(), 1);
        rig.frameTarget(fov);
        changed |= prevPan != rig.pan() || prevTilt != rig.tilt() || prevFov != rig.fov();
        if(changed) sendData();
    }
    // Resume the patrol with the user's retained zoom after releasing a target
    private boolean stopTracking(){
        targetLock.clear();
        if(!tracking) return false;
        tracking = false;
        trackingBounds = null;
        rig.setFov(patrolFov);
        return true;
    }
    // Save controls and configuration without persisting transient ray results
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.write(tag, provider, clientPacket);
        tag.putString("CameraMode", mode.name());
        tag.putString("CameraOrientation", orientation.name());
        if(overrideMode != null) tag.putString("CameraUiMode", overrideMode.name());
        tag.putBoolean("CameraFlashlight", flashlight);
        tag.putBoolean("CameraAutoTracking", autoTracking);
        tag.putBoolean("CameraLockTarget", lockTarget);
        if(overrideTracking != null) tag.putBoolean("CameraUiTracking", overrideTracking);
        if(overrideTargetLock != null) tag.putBoolean("CameraUiTargetLock", overrideTargetLock);
        if(clientPacket){
            tag.putBoolean("CameraControlled", controlled);
            tag.putBoolean("CameraTracking", tracking);
            if(tracking && trackingBounds != null) tag.put("CameraTrackedBounds", ViewTargetFraming.boundsTag(trackingBounds));
        }
        tag.putDouble("CameraPan", rig.pan());
        tag.putDouble("CameraTilt", rig.tilt());
        tag.putDouble("CameraGraphPan", graphPan);
        tag.putDouble("CameraGraphTilt", graphTilt);
        tag.putDouble("CameraFov", !clientPacket && tracking ? patrolFov : rig.fov());
        tag.putDouble("MaxRayLength", maxRayLength);
        tag.putDouble("RaysPerSecond", raysPerSecond);
        tag.putBoolean("FilterAllowlist", filter.allowlist());
        ListTag entries = new ListTag();
        filter.entries().stream().sorted().forEach(entry -> entries.add(StringTag.valueOf(entry)));
        tag.put("CameraFilter", entries);
    }
    // Restore finite controls and retain safe defaults for older block data
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.read(tag, provider, clientPacket);
        controlled = clientPacket && tag.getBoolean("CameraControlled");
        tracking = clientPacket && tag.getBoolean("CameraTracking");
        trackingBounds = tracking ? ViewTargetFraming.boundsFromTag(tag.getCompound("CameraTrackedBounds")) : null;
        if(!clientPacket) targetLock.clear();
        autoTracking = tag.getBoolean("CameraAutoTracking");
        overrideTracking = tag.contains("CameraUiTracking") ? tag.getBoolean("CameraUiTracking") : null;
        lockTarget = tag.getBoolean("CameraLockTarget");
        overrideTargetLock = tag.contains("CameraUiTargetLock") ? tag.getBoolean("CameraUiTargetLock") : null;
        try{ mode = ViewRig.Mode.valueOf(tag.getString("CameraMode")); }
        catch(IllegalArgumentException err){ mode = ViewRig.Mode.LOCKED; }
        try{ orientation = ViewRig.Orientation.valueOf(tag.getString("CameraOrientation")); }
        catch(IllegalArgumentException err){ orientation = ViewRig.Orientation.LOCAL; }
        try{ overrideMode = ViewRig.Mode.valueOf(tag.getString("CameraUiMode")); }
        catch(IllegalArgumentException err){ overrideMode = null; }
        flashlight = tag.getBoolean("CameraFlashlight");
        if(tracking) rig.track(new ViewRig.Angles(tag.getDouble("CameraPan"), tag.getDouble("CameraTilt")));
        else rig.aim(tag.getDouble("CameraPan"), tag.getDouble("CameraTilt"));
        graphPan = tag.contains("CameraGraphPan") ? tag.getDouble("CameraGraphPan") : rig.pan();
        graphTilt = tag.contains("CameraGraphTilt") ? tag.getDouble("CameraGraphTilt") : rig.tilt();
        rig.setFov(tag.contains("CameraFov") ? tag.getDouble("CameraFov") : 70);
        double length = tag.contains("MaxRayLength") ? tag.getDouble("MaxRayLength") : 64;
        maxRayLength = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        double rate = tag.contains("RaysPerSecond") ? tag.getDouble("RaysPerSecond")
                : tag.contains("RaysPerTick") ? tag.getInt("RaysPerTick") * 20.0D : 20;
        raysPerSecond = Double.isFinite(rate) ? Math.clamp(rate, 0, ViewRayBudget.MAX_PER_SECOND) : 0;
        Set<String> entries = tag.getList("CameraFilter", Tag.TAG_STRING).stream()
                .map(Tag::getAsString).collect(java.util.stream.Collectors.toSet());
        filter = new ViewRaycast.Filter(entries, tag.getBoolean("FilterAllowlist"));
    }
}
