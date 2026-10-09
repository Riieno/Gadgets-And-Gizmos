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
import com.rieno.gadgetsandgizmos.lib.view.ViewControlState;
import com.rieno.gadgetsandgizmos.lib.view.ViewRayBudget;
import com.rieno.gadgetsandgizmos.lib.view.ViewTransforms;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Own the Camera block's saved configuration and ray samples
public final class CameraBlockEntity extends SmartBlockEntity implements ControlledViewSource{
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
    private ProjectedLightSource floodlight;
    // Synchronize exclusive mouse ownership without changing the configured mode
    private boolean controlled;
    // Cache all samples once per game tick
    private long sampledTick = Long.MIN_VALUE;
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
        if(level != null && !level.isClientSide) sampleRays();
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
        double nextLength = Double.isFinite(length) ? Math.clamp(length, 0, ViewRaycast.MAX_LENGTH) : 0;
        double nextCount = Double.isFinite(count) ? Math.clamp(count, 0, ViewRayBudget.MAX_PER_SECOND) : 0;
        double nextPan = Double.isFinite(pan) ? Math.clamp(pan, -180, 180) : 0;
        double nextTilt = Double.isFinite(tilt) ? Math.clamp(tilt, -135, 135) : 0;
        boolean changed = maxRayLength != nextLength || raysPerSecond != nextCount || !this.filter.equals(filter)
                || this.mode != mode || this.orientation != orientation;
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
        Quaterniond mount = ViewTransforms.orientation(this, mountOrientation(), partialTick);
        ViewRig.Angles angles = ViewRig.angles(controlled ? ViewRig.Mode.MANUAL : effectiveMode(),
                level.getGameTime() + partialTick, rig.pan(), rig.tilt());
        Quaterniond world = ViewRig.worldOrientation(mount, orientation, angles);
        if(orientation == ViewRig.Orientation.LOCAL){
            return ViewRig.joints(angles);
        }
        return ViewRig.joints(mount, world);
    }
    // Resolve the live lens using the same joint transform as the partial renderer
    @Override
    public ViewPose viewPose(float partialTick){
        Quaterniond mount = ViewTransforms.orientation(this, mountOrientation(), partialTick);
        ViewRig.Angles angles = ViewRig.angles(controlled ? ViewRig.Mode.MANUAL : effectiveMode(),
                level.getGameTime() + partialTick, rig.pan(), rig.tilt());
        Quaterniond world = ViewRig.worldOrientation(mount, orientation, angles);
        Vec3 pivot = ViewTransforms.position(this, getBlockPos().getCenter(), partialTick);
        var lens = world.transform(new org.joml.Vector3d(0, 0, -5.1D / 16.0D));
        return new ViewPose(pivot.add(lens.x, lens.y, lens.z), world, rig.fov());
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
            ViewRig.Angles angles = ViewRig.angles(effectiveMode(), level.getGameTime(), rig.pan(), rig.tilt());
            rig.aim(angles.pan(), angles.tilt());
        }
        this.controlled = controlled;
        if(isViewAvailable()) sendData();
    }
    // Check whether the camera still belongs to a loaded block
    @Override
    public boolean isViewAvailable(){ return level != null && !isRemoved(); }
    // Retain tablet settings until the graph changes its configured mode
    private ViewRig.Mode effectiveMode(){ return overrideMode == null ? mode : overrideMode; }
    // Expose the same settings to the tablet and projected display
    @Override
    public ViewControlState viewControlState(){ return new ViewControlState(effectiveMode(), rig.fov(), flashlight); }
    // Keep explicit remote settings independent of unchanged graph defaults
    @Override
    public void applyViewSettings(ViewControlState state){
        if(state.equals(viewControlState()) && overrideMode == state.mode()) return;
        if(state.mode() == ViewRig.Mode.MANUAL && effectiveMode() != ViewRig.Mode.MANUAL){
            ViewRig.Angles angles = ViewRig.angles(effectiveMode(), level.getGameTime(), rig.pan(), rig.tilt());
            rig.aim(angles.pan(), angles.tilt());
        }
        overrideMode = state.mode();
        rig.setFov(state.fov());
        flashlight = state.flashlight();
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
    // Perform at most the configured ray budget once per tick
    private void sampleRays(){
        if(level == null || level.isClientSide || sampledTick == level.getGameTime()) return;
        sampledTick = level.getGameTime();
        int count = rayBudget.next(sampledTick, raysPerSecond);
        if(count == 0 && raysPerSecond > 0) return;
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
    // Save controls and configuration without persisting transient ray results
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.write(tag, provider, clientPacket);
        tag.putString("CameraMode", mode.name());
        tag.putString("CameraOrientation", orientation.name());
        if(overrideMode != null) tag.putString("CameraUiMode", overrideMode.name());
        tag.putBoolean("CameraFlashlight", flashlight);
        if(clientPacket) tag.putBoolean("CameraControlled", controlled);
        tag.putDouble("CameraPan", rig.pan());
        tag.putDouble("CameraTilt", rig.tilt());
        tag.putDouble("CameraGraphPan", graphPan);
        tag.putDouble("CameraGraphTilt", graphTilt);
        tag.putDouble("CameraFov", rig.fov());
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
        try{ mode = ViewRig.Mode.valueOf(tag.getString("CameraMode")); }
        catch(IllegalArgumentException err){ mode = ViewRig.Mode.LOCKED; }
        try{ orientation = ViewRig.Orientation.valueOf(tag.getString("CameraOrientation")); }
        catch(IllegalArgumentException err){ orientation = ViewRig.Orientation.LOCAL; }
        try{ overrideMode = ViewRig.Mode.valueOf(tag.getString("CameraUiMode")); }
        catch(IllegalArgumentException err){ overrideMode = null; }
        flashlight = tag.getBoolean("CameraFlashlight");
        rig.aim(tag.getDouble("CameraPan"), tag.getDouble("CameraTilt"));
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
