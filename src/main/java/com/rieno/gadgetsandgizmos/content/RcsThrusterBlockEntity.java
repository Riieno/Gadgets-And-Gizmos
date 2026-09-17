package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.particle.worldspace.WorldSpaceParticleEmitter;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.lib.physics.SablePointImpulseApi;
import com.rieno.gadgetsandgizmos.particle.RcsSteamParticleOptions;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

// Split one kinetic RCS block into four independently controlled physical nozzles
public class RcsThrusterBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, IHaveGoggleInformation, MenuProvider,
        IDirectControlReceiver, AdvancedGraphDataProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double MAX_NOZZLE_THRUST_PN = 215.0D;
    public static final double FULL_POWER_RPM = 256.0D;
    private static final double BACKTANK_AIR_PER_MAX_NOZZLE_SECOND = 2.0D;
    private static final double SECONDS_PER_SERVER_TICK = 1.0D / 20.0D;

    private static final String COMPUTER_SOURCE = "computercraft";
    private static final double NOZZLE_EMITTER_OFFSET = 0.56D;
    private static final double NOZZLE_HEIGHT_OFFSET = -0.31D;
    private static final int LINK_RECEIVER_REFRESH_TICKS = 5;
    private static final Direction[] NOZZLES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Map<String, String> GRAPH_READABLE_DATA = createGraphReadableData();
    private static final Map<String, String> GRAPH_WRITABLE_DATA = createGraphWritableData();
    private static final ClassValue<ContainingLevelAccess> CONTAINING_LEVEL_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected ContainingLevelAccess computeValue(Class<?> type) {
            try {
                return new ContainingLevelAccess(type.getMethod("getLevel"));
            } catch (NoSuchMethodException | SecurityException ignored) {
                return new ContainingLevelAccess(null);
            }
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked frequency bindings
    private final EnumMap<Direction, FrequencyBinding> frequencyBindings = new EnumMap<>(Direction.class);
    // Tracked receivers
    private final EnumMap<Direction, NozzleReceiver> receivers = new EnumMap<>(Direction.class);
    // Registered receivers
    private final EnumSet<Direction> registeredReceivers = EnumSet.noneOf(Direction.class);
    // Tracked redstone throttles
    private final EnumMap<Direction, Float> redstoneThrottles = new EnumMap<>(Direction.class);
    // Tracked exact throttle sources
    private final EnumMap<Direction, Map<String, Float>> exactThrottleSources = new EnumMap<>(Direction.class);
    // Tracked client throttles
    private final EnumMap<Direction, Float> clientThrottles = new EnumMap<>(Direction.class);
    // Tracked particle emission debt
    private final EnumMap<Direction, Float> particleEmissionDebt = new EnumMap<>(Direction.class);
    // Effective throttle bits
    private final AtomicIntegerArray effectiveThrottleBits =
            new AtomicIntegerArray(Direction.values().length);
    // Force positions
    private final Vector3d[] forcePositions = new Vector3d[Direction.values().length];
    // Force directions
    private final Vector3d[] forceDirections = new Vector3d[Direction.values().length];
    // Current force geometry facing
    private Direction forceGeometryFacing;
    // Current receiver location level
    private Level receiverLocationLevel;
    // Current receiver location game time
    private long receiverLocationGameTime = Long.MIN_VALUE;
    // Current receiver location
    private BlockPos receiverLocation;
    // Current backtank drain remainder
    private double backtankDrainRemainder;
    // Assembly ComputerCraft id
    private String ccId = "";
    // Assembly ComputerCraft alias
    private String ccAlias = "";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster
    public RcsThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.RCS_THRUSTER.get(), pos, state);
        for (Direction nozzle : NOZZLES) {
            frequencyBindings.put(nozzle, new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY));
            receivers.put(nozzle, new NozzleReceiver(nozzle));
            redstoneThrottles.put(nozzle, 0.0F);
            exactThrottleSources.put(nozzle, new ConcurrentHashMap<>());
            clientThrottles.put(nozzle, 0.0F);
            particleEmissionDebt.put(nozzle, 0.0F);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    // Initialize the RCS thruster
    @Override
    public void initialize() {
        super.initialize();
        refreshReceiverRegistrations();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the RCS thruster
    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            tickNozzleParticles();
        } else {
            tickBacktankPressure();
            if (level.getGameTime() % LINK_RECEIVER_REFRESH_TICKS == 0L) {
                refreshReceiverStrengths();
            }
        }
    }

    // Remove the RCS thruster
    @Override
    public void remove() {
        unregisterReceivers();
        super.remove();
    }

    // Invalidate the RCS thruster
    @Override
    public void invalidate() {
        unregisterReceivers();
        super.invalidate();
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(Direction nozzle) {
        FrequencyBinding binding = frequencyBindings.get(nozzle);
        return binding == null ? ItemStack.EMPTY : copySingle(binding.first());
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(Direction nozzle) {
        FrequencyBinding binding = frequencyBindings.get(nozzle);
        return binding == null ? ItemStack.EMPTY : copySingle(binding.second());
    }

    // Set the frequency
    public void setFrequency(Direction nozzle, ItemStack first, ItemStack second) {
        if (!isNozzle(nozzle)) {
            return;
        }
        ItemStack nextFirst = copySingle(first);
        ItemStack nextSecond = copySingle(second);
        FrequencyBinding current = frequencyBindings.get(nozzle);
        if (current != null
                && ItemStack.isSameItemSameComponents(current.first(), nextFirst)
                && ItemStack.isSameItemSameComponents(current.second(), nextSecond)) {
            return;
        }
        unregisterReceiver(nozzle);
        frequencyBindings.put(nozzle, new FrequencyBinding(nextFirst, nextSecond));
        registerReceiver(nozzle);
        setChanged();
        sendData();
    }

    // Get the redstone throttle
    public float getRedstoneThrottle(Direction nozzle) {
        return isNozzle(nozzle) ? redstoneThrottles.getOrDefault(nozzle, 0.0F) : 0.0F;
    }

    // Get the throttle
    public float getThrottle(Direction nozzle) {
        if (!isNozzle(nozzle)) {
            return 0.0F;
        }
        if (level != null && level.isClientSide) {
            return clientThrottles.getOrDefault(nozzle, 0.0F);
        }
        return effectiveThrottle(nozzle);
    }

    // Resolve the effective throttle
    static float resolveEffectiveThrottle(float redstoneThrottle, @Nullable Map<String, Float> sources) {
        float maximum = Mth.clamp(redstoneThrottle, 0.0F, 1.0F);
        if (sources != null) {
            for (float val : sources.values()) {
                maximum = Math.max(maximum, Mth.clamp(val, 0.0F, 1.0F));
            }
        }
        return maximum;
    }

    // Set the computer throttle
    public void setComputerThrottle(Direction nozzle, float throttle) {
        setControllerThrottle(nozzle, COMPUTER_SOURCE, throttle);
    }

    // Clear the computer throttle
    public void clearComputerThrottle(Direction nozzle) {
        clearControllerThrottle(nozzle, COMPUTER_SOURCE);
    }

    // Get the assembly ComputerCraft id
    public String getCcId() {
        return ccId;
    }

    // Set the assembly ComputerCraft id
    public void setCcId(String id) {
        String normalized = id == null ? "" : id;
        if (ccId.equals(normalized)) return;
        ccId = normalized;
        bindingChanged();
    }

    // Get the assembly ComputerCraft alias
    public String getAssemblyComputerCraftAlias() {
        return ccAlias;
    }

    // Set the assembly ComputerCraft alias
    public void setAssemblyComputerCraftAlias(String alias) {
        String normalized = alias == null ? "" : alias;
        if (ccAlias.equals(normalized)) return;
        ccAlias = normalized;
        bindingChanged();
    }

    // Persist and synchronize an assembly binding change
    private void bindingChanged() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    // Check if this has computer throttle
    public boolean hasComputerThrottle(Direction nozzle) {
        Map<String, Float> sources = exactThrottleSources.get(nozzle);
        return sources != null && sources.containsKey(COMPUTER_SOURCE);
    }

    // Set the controller throttle
    public void setControllerThrottle(Direction nozzle, String sourceId, float throttle) {
        if (!isNozzle(nozzle) || sourceId == null || sourceId.isBlank()) {
            return;
        }
        float clamped = Mth.clamp(throttle, 0.0F, 1.0F);
        Float prev = exactThrottleSources.get(nozzle).put(sourceId, clamped);
        if (prev == null || Math.abs(prev - clamped) > 1.0E-5F) {
            recomputeEffectiveThrottle(nozzle);
            throttleChanged();
        }
    }

    // Clear the controller throttle
    public void clearControllerThrottle(Direction nozzle, String sourceId) {
        if (!isNozzle(nozzle) || sourceId == null || sourceId.isBlank()) {
            return;
        }
        if (exactThrottleSources.get(nozzle).remove(sourceId) != null) {
            recomputeEffectiveThrottle(nozzle);
            throttleChanged();
        }
    }

    // Clear the controller source
    public void clearControllerSource(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        boolean changed = false;
        for (Direction nozzle : NOZZLES) {
            Map<String, Float> sources = exactThrottleSources.get(nozzle);
            if (sources.remove(sourceId) != null) {
                recomputeEffectiveThrottle(nozzle);
                changed = true;
            }
        }
        if (changed) {
            throttleChanged();
        }
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        Direction nozzle = nozzleFromChannel(channelId);
        if (nozzle == null) {
            return;
        }
        setControllerThrottle(nozzle, "direct:" + normalizeChannel(channelId), val);
    }

    // Get the nozzle from channel
    public static @Nullable Direction nozzleFromChannel(@Nullable String channelId) {
        String normalized = normalizeChannel(channelId);
        return switch (normalized) {
            case "north", "nozzle_north", "north_nozzle", "north_throttle", "north_thrust", "yaw_left" ->
                    Direction.NORTH;
            case "east", "nozzle_east", "east_nozzle", "east_throttle", "east_thrust", "roll_right" ->
                    Direction.EAST;
            case "south", "nozzle_south", "south_nozzle", "south_throttle", "south_thrust", "yaw_right" ->
                    Direction.SOUTH;
            case "west", "nozzle_west", "west_nozzle", "west_throttle", "west_thrust", "roll_left" ->
                    Direction.WEST;
            default -> null;
        };
    }

    // Normalize the channel
    private static String normalizeChannel(@Nullable String channelId) {
        return channelId == null ? "" : channelId.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    // Get the max nozzle thrust
    public double getMaxNozzleThrust() {
        BacktankBlockEntity backtank = connectedBacktank();
        if (backtank != null) {
            return backtank.getAirLevel() > 0 ? MAX_NOZZLE_THRUST_PN : 0.0D;
        }
        return speedToMaxThrust(getSpeed());
    }

    // Get the speed to max thrust
    public static double speedToMaxThrust(double rpm) {
        if (!Double.isFinite(rpm)) {
            return 0.0D;
        }
        return MAX_NOZZLE_THRUST_PN
                * Mth.clamp(Math.abs(rpm) / FULL_POWER_RPM, 0.0D, 1.0D);
    }

    // Get the nozzle thrust
    public double getNozzleThrust(Direction nozzle) {
        return getMaxNozzleThrust() * getThrottle(nozzle);
    }

    // Check if the nozzle is active
    public boolean isNozzleActive(Direction nozzle) {
        return getMaxNozzleThrust() > 1.0E-6D && getThrottle(nozzle) > 1.0E-6F;
    }

    // Get the local nozzle direction
    public Vec3 getLocalNozzleDirection(Direction nozzle) {
        return nozzleDirection(getBlockState().getValue(RcsThrusterBlock.FACING), nozzle);
    }

    // Get the nozzle direction
    public static Vec3 nozzleDirection(Direction bottom, Direction nozzle) {
        if (bottom == null || !isNozzle(nozzle)) {
            return Vec3.ZERO;
        }
        Vec3 outward = Vec3.atLowerCornerOf(bottom.getOpposite().getNormal());
        Vec3 north = bottom.getAxis().isHorizontal()
                ? Vec3.atLowerCornerOf(Direction.DOWN.getNormal())
                : Vec3.atLowerCornerOf(Direction.NORTH.getNormal());
        Vec3 east = north.cross(outward);
        return switch (nozzle) {
            case NORTH -> north;
            case EAST -> east;
            case SOUTH -> north.scale(-1.0D);
            case WEST -> east.scale(-1.0D);
            default -> Vec3.ZERO;
        };
    }

    // Get the local force direction
    public Vec3 getLocalForceDirection(Direction nozzle) {
        return getLocalNozzleDirection(nozzle).scale(-1.0D);
    }

    // Get the local force position
    public Vec3 getLocalForcePosition(Direction nozzle) {
        Direction bottom = getBlockState().getValue(RcsThrusterBlock.FACING);
        Vec3 outward = Vec3.atLowerCornerOf(bottom.getOpposite().getNormal());
        return getBlockPos().getCenter()
                .add(getLocalNozzleDirection(nozzle).scale(0.35D))
                .add(outward.scale(-0.30D));
    }

    // Get the local particle emitter position
    Vec3 getLocalParticleEmitterPosition(Direction nozzle) {
        Direction bottom = getBlockState().getValue(RcsThrusterBlock.FACING);
        return getBlockPos().getCenter().add(nozzleEmitterOffset(bottom, nozzle));
    }

    // Get the nozzle emitter offset
    static Vec3 nozzleEmitterOffset(Direction bottom, Direction nozzle) {
        if (bottom == null || !isNozzle(nozzle)) {
            return Vec3.ZERO;
        }
        Vec3 outward = Vec3.atLowerCornerOf(bottom.getOpposite().getNormal());
        return nozzleDirection(bottom, nozzle).scale(NOZZLE_EMITTER_OFFSET)
                .add(outward.scale(NOZZLE_HEIGHT_OFFSET));
    }

    // Update the nozzle particles
    private void tickNozzleParticles() {
        if (level == null || !level.isClientSide || !CTConfigs.SERVER.enableThrusterParticles.get()) {
            return;
        }
        float particleScale = (float) Mth.clamp(
                CTConfigs.CLIENT.thrusterParticleScale.get(), 0.0D, 1.0D);
        if (particleScale <= 0.0F || getMaxNozzleThrust() <= 1.0E-6D) {
            return;
        }

        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            float throttle = getThrottle(nozzle);
            if (throttle <= 1.0E-4F) {
                particleEmissionDebt.put(nozzle, 0.0F);
                continue;
            }
            float debt = particleEmissionDebt.getOrDefault(nozzle, 0.0F)
                    + (0.22F + throttle * 0.78F) * particleScale;
            while (debt >= 1.0F) {
                spawnNozzleParticle(nozzle, throttle, particleScale);
                debt -= 1.0F;
            }
            particleEmissionDebt.put(nozzle, debt);
        }
    }

    // Spawn the nozzle particle
    private void spawnNozzleParticle(Direction nozzle, float throttle, float particleScale) {
        if (level == null) {
            return;
        }
        Vec3 localDirection = getLocalNozzleDirection(nozzle);
        if (localDirection.lengthSqr() < 1.0E-6D) {
            return;
        }
        localDirection = localDirection.normalize();

        Vec3 localEmitter = getLocalParticleEmitterPosition(nozzle)
                .add(randomPerpendicular(localDirection, 0.025D));
        double speed = 0.035D + throttle * 0.055D;
        Vec3 localMotion = localDirection.scale(speed)
                .add(randomPerpendicular(localDirection, 0.009D));
        float shade = 0.72F + level.random.nextFloat() * 0.23F;
        float scale = particleScale * (0.55F + level.random.nextFloat() * 0.30F + throttle * 0.20F);
        WorldSpaceParticleEmitter.addParticle(this,
                new RcsSteamParticleOptions(shade, shade, Math.min(1.0F, shade + 0.025F), scale),
                localEmitter, localMotion);
    }

    // Get the random perpendicular
    private Vec3 randomPerpendicular(Vec3 dir, double radius) {
        if (level == null || radius <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 reference = Math.abs(dir.y) > 0.8D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisA = dir.cross(reference).normalize();
        Vec3 axisB = dir.cross(axisA).normalize();
        double offsetA = (level.random.nextDouble() - 0.5D) * 2.0D * radius;
        double offsetB = (level.random.nextDouble() - 0.5D) * 2.0D * radius;
        return axisA.scale(offsetA).add(axisB.scale(offsetB));
    }

    // Update the physics
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        double maximum = getMaxNozzleThrust();
        if (!Double.isFinite(maximum) || maximum <= 1.0E-6D
                || !Double.isFinite(timeStep) || timeStep <= 0.0D) {
            return;
        }
        ensureForceGeometry();
        var forceGroup = ForceGroups.PROPULSION.get();
        for (Direction nozzle : NOZZLES) {
            int nozzleIndex = nozzle.ordinal();
            double thrust = maximum * effectiveThrottle(nozzle);
            if (!Double.isFinite(thrust) || thrust <= 1.0E-6D) {
                continue;
            }
            SablePointImpulseApi.applyDirectional(
                    subLevel,
                    handle,
                    forceGroup,
                    forcePositions[nozzleIndex],
                    forceDirections[nozzleIndex],
                    thrust,
                    timeStep);
        }
    }

    // Get the connected backtank
    private @Nullable BacktankBlockEntity connectedBacktank() {
        if (level == null) return null;
        Direction shaftFace = getBlockState().getValue(RcsThrusterBlock.FACING);
        net.minecraft.world.level.block.entity.BlockEntity candidate =
                level.getBlockEntity(worldPosition.relative(shaftFace));
        if (!(candidate instanceof BacktankBlockEntity backtank)
                || !(backtank.getBlockState().getBlock() instanceof BacktankBlock block)
                || !block.hasShaftTowards(level, backtank.getBlockPos(), backtank.getBlockState(),
                shaftFace.getOpposite())) {
            return null;
        }
        return backtank;
    }

    // Update the backtank pressure
    private void tickBacktankPressure() {
        BacktankBlockEntity backtank = connectedBacktank();
        if (backtank == null) {
            backtankDrainRemainder = 0.0D;
            return;
        }
        double nozzleLoad = 0.0D;
        for (Direction nozzle : NOZZLES) {
            nozzleLoad += effectiveThrottle(nozzle);
        }
        consumeBacktankPressure(backtank, nozzleLoad, SECONDS_PER_SERVER_TICK);
    }

    // Consume the backtank pressure
    private void consumeBacktankPressure(@Nullable BacktankBlockEntity backtank,
                                         double nozzleLoad, double timeStep) {
        if (backtank == null || level == null || level.isClientSide || nozzleLoad <= 0.0D
                || backtank.getAirLevel() <= 0) return;
        backtankDrainRemainder += nozzleLoad * BACKTANK_AIR_PER_MAX_NOZZLE_SECOND * timeStep;
        int consumed = (int) Math.floor(backtankDrainRemainder);
        if (consumed <= 0) return;
        backtankDrainRemainder -= consumed;
        int previousSignal = backtank.getComparatorOutput();
        backtank.setAirLevel(Math.max(0, backtank.getAirLevel() - consumed));
        backtank.setChanged();
        if (backtank.getComparatorOutput() != previousSignal) {
            level.updateNeighbourForOutputSignal(backtank.getBlockPos(), backtank.getBlockState().getBlock());
        }
    }

    // Ensure the force geometry
    private void ensureForceGeometry() {
        Direction facing = getBlockState().getValue(RcsThrusterBlock.FACING);
        if (facing == forceGeometryFacing) {
            return;
        }
        forceGeometryFacing = facing;
        for (Direction nozzle : NOZZLES) {
            Vec3 pos = getLocalForcePosition(nozzle);
            Vec3 dir = getLocalForceDirection(nozzle);
            int nozzleIndex = nozzle.ordinal();
            forcePositions[nozzleIndex] = new Vector3d(pos.x, pos.y, pos.z);
            forceDirections[nozzleIndex] = new Vector3d(dir.x, dir.y, dir.z);
        }
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        return GRAPH_READABLE_DATA;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        return GRAPH_WRITABLE_DATA;
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        Direction nozzle = nozzleFromChannel(field);
        if (nozzle != null) {
            if (normalizeChannel(field).endsWith("_thrust")) {
                return AdvancedGraphDocument.Value.number(getNozzleThrust(nozzle));
            }
            return AdvancedGraphDocument.Value.number(getThrottle(nozzle));
        }
        return switch (normalizeChannel(field)) {
            case "rpm" -> AdvancedGraphDocument.Value.number(getSpeed());
            case "max_nozzle_thrust" -> AdvancedGraphDocument.Value.number(getMaxNozzleThrust());
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        Direction nozzle = nozzleFromChannel(field);
        if (nozzle == null || val == null || !normalizeChannel(field).endsWith("_throttle")) {
            return false;
        }
        setControllerThrottle(nozzle, "advanced_graph:" + normalizeChannel(field),
                (float) Mth.clamp(val.asNumber(), 0.0D, 1.0D));
        return true;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.rcs_thruster")));
        tooltip.add(CTTooltipHelper.line(
                Component.translatable("createthrusters.goggle.rcs_thruster.rpm"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f", Math.abs(getSpeed())), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(
                Component.translatable("createthrusters.goggle.rcs_thruster.max_thrust"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.2f pN", getMaxNozzleThrust()),
                        ChatFormatting.GREEN)));
        if (CTTooltipHelper.showGoggleDetails(isPlayerSneaking)) {
            for (Direction nozzle : Direction.Plane.HORIZONTAL) {
                tooltip.add(CTTooltipHelper.line(
                        Component.literal(capitalize(nozzle.getSerializedName())),
                        CTTooltipHelper.value(String.format(Locale.ROOT, "%.3f", getThrottle(nozzle)),
                                ChatFormatting.YELLOW)));
            }
        }
        if (ModList.get().isLoaded("computercraft") && !ccAlias.isBlank()) {
            tooltip.add(CTTooltipHelper.line(
                    Component.translatable("createthrusters.goggle.thruster.cc_alias"),
                    CTTooltipHelper.value(ccAlias, ChatFormatting.AQUA)));
        }
        return true;
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RcsThrusterMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.rcs_thruster.config.title");
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        MenuOpenHeader.encode(buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
    }

    // Write the RCS thruster
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putString("AssemblyComputerCraftId", ccId);
        tag.putString("AssemblyComputerCraftAlias", ccAlias);
        for (Direction nozzle : NOZZLES) {
            writeFrequency(tag, provider, nozzle);
            Map<String, Float> sources = exactThrottleSources.get(nozzle);
            if (sources != null && sources.containsKey(COMPUTER_SOURCE)) {
                tag.putFloat(computerThrottleKey(nozzle), sources.get(COMPUTER_SOURCE));
            }
            if (clientPacket) {
                tag.putFloat(effectiveThrottleKey(nozzle), getThrottle(nozzle));
            }
        }
    }

    // Read the RCS thruster
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        ccId = tag.getString("AssemblyComputerCraftId");
        ccAlias = tag.getString("AssemblyComputerCraftAlias");
        unregisterReceivers();
        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            frequencyBindings.put(nozzle, readFrequency(tag, provider, nozzle));
            Map<String, Float> sources = exactThrottleSources.get(nozzle);
            sources.clear();
            if (tag.contains(computerThrottleKey(nozzle))) {
                sources.put(COMPUTER_SOURCE,
                        Mth.clamp(tag.getFloat(computerThrottleKey(nozzle)), 0.0F, 1.0F));
            }
            recomputeEffectiveThrottle(nozzle);
            if (clientPacket) {
                clientThrottles.put(nozzle, Mth.clamp(
                        tag.getFloat(effectiveThrottleKey(nozzle)), 0.0F, 1.0F));
            }
        }
        refreshReceiverRegistrations();
    }

    // Write the frequency
    private void writeFrequency(CompoundTag tag, HolderLookup.Provider provider, Direction nozzle) {
        FrequencyBinding binding = frequencyBindings.get(nozzle);
        if (binding == null || binding.isEmpty()) {
            return;
        }
        CompoundTag frequencyTag = new CompoundTag();
        if (!binding.first().isEmpty()) {
            frequencyTag.put("First", binding.first().saveOptional(provider));
        }
        if (!binding.second().isEmpty()) {
            frequencyTag.put("Second", binding.second().saveOptional(provider));
        }
        tag.put(frequencyKey(nozzle), frequencyTag);
    }

    // Read the frequency
    private static FrequencyBinding readFrequency(
            CompoundTag tag,
            HolderLookup.Provider provider,
            Direction nozzle
    ) {
        String key = frequencyKey(nozzle);
        if (!tag.contains(key)) {
            return new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY);
        }
        CompoundTag frequencyTag = tag.getCompound(key);
        ItemStack first = frequencyTag.contains("First")
                ? ItemStack.parseOptional(provider, frequencyTag.getCompound("First"))
                : ItemStack.EMPTY;
        ItemStack second = frequencyTag.contains("Second")
                ? ItemStack.parseOptional(provider, frequencyTag.getCompound("Second"))
                : ItemStack.EMPTY;
        return new FrequencyBinding(copySingle(first), copySingle(second));
    }

    // Refresh the receiver registrations
    private void refreshReceiverRegistrations() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction nozzle : NOZZLES) {
            if (hasFrequency(nozzle)) {
                registerReceiver(nozzle);
            } else {
                unregisterReceiver(nozzle);
            }
        }
    }

    // Register the receiver
    private void registerReceiver(Direction nozzle) {
        if (level == null || level.isClientSide || registeredReceivers.contains(nozzle)
                || !hasFrequency(nozzle)) {
            return;
        }
        NozzleReceiver receiver = receivers.get(nozzle);
        if (receiver != null) {
            Level linkLevel = resolveLinkLevel(level);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(linkLevel, receiver);
            registeredReceivers.add(nozzle);
            refreshReceiverStrength(nozzle, linkLevel);
        }
    }

    // Remove the receiver
    private void unregisterReceiver(Direction nozzle) {
        if (level == null || level.isClientSide || !registeredReceivers.remove(nozzle)) {
            return;
        }
        NozzleReceiver receiver = receivers.get(nozzle);
        if (receiver != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(resolveLinkLevel(level), receiver);
        }
        setRedstoneThrottle(nozzle, 0.0F);
    }

    // Remove the receivers
    private void unregisterReceivers() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction nozzle : NOZZLES) {
            unregisterReceiver(nozzle);
        }
    }

    // Refresh the receiver strengths
    private void refreshReceiverStrengths() {
        if (level == null || level.isClientSide || registeredReceivers.isEmpty()) {
            return;
        }
        Level linkLevel = resolveLinkLevel(level);
        for (Direction nozzle : NOZZLES) {
            if (registeredReceivers.contains(nozzle)) {
                refreshReceiverStrength(nozzle, linkLevel);
            }
        }
    }

    // Refresh the receiver strength
    private void refreshReceiverStrength(Direction nozzle, Level linkLevel) {
        NozzleReceiver receiver = receivers.get(nozzle);
        if (receiver == null || !registeredReceivers.contains(nozzle)) {
            return;
        }
        int networkPower = 0;
        for (IRedstoneLinkable link : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(linkLevel, receiver)) {
            if (link == receiver || !link.isAlive()
                    || !RedstoneLinkNetworkHandler.withinRange(receiver, link)) {
                continue;
            }
            networkPower = Math.max(networkPower, Mth.clamp(link.getTransmittedStrength(), 0, 15));
            if (networkPower >= 15) {
                break;
            }
        }
        receiver.setReceivedStrength(networkPower);
    }

    // Set the redstone throttle
    private void setRedstoneThrottle(Direction nozzle, float throttle) {
        float clamped = Mth.clamp(throttle, 0.0F, 1.0F);
        float prev = redstoneThrottles.getOrDefault(nozzle, 0.0F);
        if (Math.abs(prev - clamped) <= 1.0E-5F) {
            return;
        }
        redstoneThrottles.put(nozzle, clamped);
        recomputeEffectiveThrottle(nozzle);
        throttleChanged();
    }

    // Recompute the effective throttle
    private void recomputeEffectiveThrottle(Direction nozzle) {
        float throttle = resolveEffectiveThrottle(
                redstoneThrottles.getOrDefault(nozzle, 0.0F), exactThrottleSources.get(nozzle));
        effectiveThrottleBits.set(nozzle.ordinal(), Float.floatToRawIntBits(throttle));
    }

    // Get the effective throttle
    private float effectiveThrottle(Direction nozzle) {
        return Float.intBitsToFloat(effectiveThrottleBits.get(nozzle.ordinal()));
    }

    // Handle the throttle changed
    private void throttleChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    // Check if this has frequency
    private boolean hasFrequency(Direction nozzle) {
        FrequencyBinding binding = frequencyBindings.get(nozzle);
        return binding != null && !binding.isEmpty();
    }

    // Resolve the link level
    private static Level resolveLinkLevel(Level currentLevel) {
        Method getLevel = CONTAINING_LEVEL_ACCESS.get(currentLevel.getClass()).getLevel();
        if (getLevel == null) {
            return currentLevel;
        }
        try {
            Object containingLevel = getLevel.invoke(currentLevel);
            if (containingLevel instanceof Level worldLevel) {
                return worldLevel;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            if (currentLevel.getServer() != null) {
                Level dimensionLevel = currentLevel.getServer().getLevel(currentLevel.dimension());
                if (dimensionLevel != null) {
                    return dimensionLevel;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return currentLevel;
    }

    // Get the receiver location
    private BlockPos getReceiverLocation() {
        Level currentLevel = level;
        if (currentLevel == null) {
            return worldPosition;
        }
        long gameTime = currentLevel.getGameTime();
        if (receiverLocation != null
                && receiverLocationLevel == currentLevel
                && receiverLocationGameTime == gameTime) {
            return receiverLocation;
        }
        Vec3 projected = SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(worldPosition));
        receiverLocation = projected == null ? worldPosition : BlockPos.containing(projected);
        receiverLocationLevel = currentLevel;
        receiverLocationGameTime = gameTime;
        return receiverLocation;
    }

    // Expose containing level
    private record ContainingLevelAccess(Method getLevel) {
    }

    // Check if this is nozzle
    private static boolean isNozzle(@Nullable Direction dir) {
        return dir != null && dir.getAxis().isHorizontal();
    }

    // Get the frequency key
    private static String frequencyKey(Direction nozzle) {
        return "Frequency_" + capitalize(nozzle.getSerializedName());
    }

    // Get the computer throttle key
    private static String computerThrottleKey(Direction nozzle) {
        return "ComputerThrottle_" + capitalize(nozzle.getSerializedName());
    }

    // Get the effective throttle key
    private static String effectiveThrottleKey(Direction nozzle) {
        return "EffectiveThrottle_" + capitalize(nozzle.getSerializedName());
    }

    // Get the capitalize
    private static String capitalize(String val) {
        return Character.toUpperCase(val.charAt(0)) + val.substring(1);
    }

    // Copy one item
    private static ItemStack copySingle(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Create the graph readable data
    private static Map<String, String> createGraphReadableData() {
        LinkedHashMap<String, String> ports = new LinkedHashMap<>();
        for (Direction nozzle : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            ports.put(nozzle.getSerializedName() + "_throttle", "number");
            ports.put(nozzle.getSerializedName() + "_thrust", "number");
        }
        ports.put("rpm", "number");
        ports.put("max_nozzle_thrust", "number");
        return Map.copyOf(ports);
    }

    // Create the graph writable data
    private static Map<String, String> createGraphWritableData() {
        LinkedHashMap<String, String> ports = new LinkedHashMap<>();
        for (Direction nozzle : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            ports.put(nozzle.getSerializedName() + "_throttle", "number");
        }
        return Map.copyOf(ports);
    }

    // Store the frequency binding
    private record FrequencyBinding(ItemStack first, ItemStack second) {
        // Check if this is empty
        private boolean isEmpty() {
            return first.isEmpty() && second.isEmpty();
        }
    }

    // Handle the nozzle receiver
    private class NozzleReceiver implements IRedstoneLinkable {
        // Nozzle
        private final Direction nozzle;

        // Initialize the nozzle receiver
        private NozzleReceiver(Direction nozzle) {
            this.nozzle = nozzle;
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
            setRedstoneThrottle(nozzle, Mth.clamp(networkPower, 0, 15) / 15.0F);
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return true;
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {
            return level != null && !isRemoved();
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            FrequencyBinding binding = frequencyBindings.get(nozzle);
            if (binding == null) {
                return Couple.create(
                        RedstoneLinkNetworkHandler.Frequency.EMPTY,
                        RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                    RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            return getReceiverLocation();
        }
    }
}
