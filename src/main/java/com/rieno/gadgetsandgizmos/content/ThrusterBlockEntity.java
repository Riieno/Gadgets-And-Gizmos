package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.math.AngleHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.particle.worldspace.WorldSpaceParticleEmitter;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SablePointImpulseApi;
import com.rieno.gadgetsandgizmos.particle.ColoredCloudParticleOptions;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.util.CTPropulsionTelemetry;
import com.rieno.gadgetsandgizmos.util.MobHauntingConversions;
import com.rieno.gadgetsandgizmos.util.ThrusterFuelData;
import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import net.createmod.catnip.math.VecHelper;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

// Handle thrust, fuel, fan processing and throttle control
public class ThrusterBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelPropellerActor,
        BlockEntityPropeller, IHaveGoggleInformation, IAirCurrentSource, MenuProvider,
        com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity,
        com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver, AdvancedGraphDataProvider {

    // Define the control mode values
    public enum ControlMode {
        AUTO,
        REDSTONE,
        COMPUTER
    }

    // Store the mounted thruster mount
    private record MountedThrusterMount(BlockPos pos, Direction direction) {
    }

    // Store the world exhaust geometry
    private record WorldExhaustGeometry(
            Vec3 origin,
            Vec3 direction,
            double maxDistance,
            boolean blocked,
            AABB bounds
    ) {
    }

    // Store the world exhaust hit
    private record WorldExhaustHit(Vec3 worldPosition, double axialDistance) {
    }

    // Define the exhaust particle style values
    private enum ExhaustParticleStyle {
        DEFAULT,
        EXPERIENCE,
        WATER_BUBBLE
    }

    // Define the processing upgrade type values
    public enum ProcessingUpgradeType {
        NONE,
        SMOKING,
        SMELTING,
        HAUNTING
    }

    // Store the solid fuel profile
    private record SolidFuelProfile(int burnTicks, boolean infiniteBurn, double powerMultiplier,
            ExhaustParticleStyle particleStyle, String fuelId) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the propeller
    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int FUEL_CAPACITY = 16000;
    public static final int SLOT_UPGRADE = 0;

    public static final int SLOT_LIST   = 1;
    public static final int SLOT_LENS   = 2;

    public static final int SLOT_SOLID_FUEL = 3;

    @Deprecated public static final int SLOT_MODIFIER = SLOT_LENS;
    private static final double DEFAULT_MAX_THRUST = 960.0;
    private static final double DEFAULT_BEAM_THRUST_MULTIPLIER = 1.5D;
    private static final Map<String, String> GRAPH_CONTROL_DATA = Map.of(
            "throttle", "number",
            "plume_color_ratio", "number",
            "beam_max_opacity", "number");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Energy storage
    private final EnergyStorage energyStorage = new BeamEnergyStorage();
    private static final double DEFAULT_MAX_AIRFLOW = 80.0;
    private static final double NEUTRAL_FUEL_BURN_TIME_TICKS_PER_BUCKET = 180.0D;
    private static final String ITEM_PROCESSING_PROGRESS_KEY = "CreateThrustersProcessingProgress";
    public static final String ENTITY_DROP_PROCESSING_TYPE_KEY = "CreateThrustersDropProcessingType";
    public static final String ENTITY_DROP_PROCESSING_TIME_KEY = "CreateThrustersDropProcessingTime";
    public static final String ENTITY_HAUNTING_CONVERSION_KEY = "CreateThrustersHauntingConversion";
    public static final String ENTITY_HAUNTING_CONVERSION_TIME_KEY = "CreateThrustersHauntingConversionTime";
    private static final float THRUSTER_FIRE_DAMAGE = 2.0F;
    private static final float THRUSTER_SMELTING_DAMAGE = 4.0F;
    private static final float THRUSTER_HAUNTING_DAMAGE = 2.0F;
    private static final float THRUSTER_FIRE_SECONDS = 4.0F;
    private static final float THRUSTER_SMOKING_FIRE_SECONDS = 2.0F;
    private static final float THRUSTER_SMELTING_FIRE_SECONDS = 10.0F;
    private static final double THRUSTER_ENTITY_QUERY_RADIUS = 1.25D;
    private static final double THRUSTER_HANDLER_QUERY_RADIUS = 1.0D;
    private static final int EXHAUST_OCCLUSION_CACHE_TICKS = 10;
    private static final int SIGNAL_POLL_INTERVAL_TICKS = 5;
    private static final int CLIENT_SYNC_INTERVAL_TICKS = 5;
    private static final int FOCUSED_AUTO_PULL_PER_TICK = 16_384;
    private static final int SHIP_CONTROL_PERSISTENCE_VERSION = 1;
    private static final String SHIP_CONTROL_PERSISTENCE_VERSION_TAG = "ShipControlPersistenceVersion";
    private static final String SHIP_CONTROL_OVERRIDE_ACTIVE_TAG = "ShipControlOverrideActive";
    private static final String SHIP_CONTROL_RESTORE_MODE_TAG = "ShipControlRestoreMode";
    private static final String SHIP_CONTROL_RESTORE_THROTTLE_TAG = "ShipControlRestoreComputerThrottle";
    private static final double PARTICLE_BLOCK_STOP_MARGIN = 0.18D;
    private static final double CONVERSION_POSITION_STEP = 0.0625D;
    private static final double CONVERSION_POSITION_MAX_NUDGE = 1.0D;
    private static final int DEFAULT_BEAM_COLOR = 0xFF3A3A;
    private static final float DEFAULT_PLUME_COLOR_RATIO = 0.8f;
    private static final Component CONFIG_LABEL = Component.translatable("createthrusters.thruster.config_screen.title");
    private static final Vector3d[] FACING_FORCE_DIRECTIONS = createFacingForceDirections();

    // Fuel tank
    private final FluidTank fuelTank = new FluidTank(FUEL_CAPACITY, this::isValidFuel) {
        // Fill the thruster
        @Override
        public int fill(FluidStack resource, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
            if (!canAcceptFuel()) {
                return 0;
            }
            return OxidizedFuelStorageAccess.allow(() -> super.fill(resource, action));
        }
    };
    // Thruster inventory
    private final ItemStackHandler thrusterInventory = new ItemStackHandler(4) {
        // Handle the contents changed event
        @Override
        protected void onContentsChanged(int slot) {
            if (suppressInventoryModeSync) {
                setChanged();
                return;
            }
            boolean wasFocusedMode = focusedMode;
            syncModesFromInventory();
            setChanged();
            if (level != null && !level.isClientSide) {
                if (wasFocusedMode != focusedMode) {
                    refreshCapabilities();
                    return;
                }
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        // Get the slot limit
        @Override
        public int getSlotLimit(int slot) {
            if (slot == SLOT_SOLID_FUEL) {
                return 64;
            }

            return 1;
        }

        // Check if the item is valid
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_UPGRADE) {
                return stack.getItem() instanceof ProcessingUpgradeItem || stack.getItem() instanceof PropulsionUpgradeItem;
            }
            if (slot == SLOT_LIST) {
                return ThrusterBlock.isCreateFilter(stack);
            }
            if (slot == SLOT_LENS) {
                return isThrusterLens(stack);
            }
            if (slot == SLOT_SOLID_FUEL) {
                return canUseAsSolidFuel(stack);
            }
            return false;
        }
    };

    // Focus input
    private final IEnergyStorage focusInput = new FocusedModeEnergyInputView();
    // Current throttle
    private float throttle;
    // Current redstone throttle
    private float redstoneThrottle;
    // Current computer throttle
    private float computerThrottle;

    // Tracked direct signals
    private final Map<String, Float> directSignals = new LinkedHashMap<>();
    // Current ship control envelope
    private @org.jetbrains.annotations.Nullable ShipControlEnvelope shipControlEnvelope;
    // Tracked ship control damage protected sub levels
    private final Map<String, Set<UUID>> shipControlDamageProtectedSubLevels =
            new LinkedHashMap<>();
    // Tracks whether legacy ship control persistence is set
    private boolean legacyShipControlPersistence;
    // Stabilizer throttle offset
    private float stabilizerThrottleOffset;
    // Current control mode
    private ControlMode controlMode = ControlMode.REDSTONE;
    // Tracks whether thruster is enabled
    private boolean enabled = true;
    // Current custom name
    @org.jetbrains.annotations.Nullable
    private String customName;
    // Signal strength
    private int signalStrength;
    // Solid fuel tick count
    private int solidFuelTicks;
    // Current fractional fuel drain
    private double fractionalFuelDrain;
    // Current fractional solid fuel drain
    private double fractionalSolidFuelDrain;
    // Tracks whether soul thruster is set
    private boolean soulThruster;
    // Tracks whether peaceful mode is set
    private boolean peacefulMode;
    // Controls whether sound is filtered
    private boolean filterSound = true;
    // Controls whether particles are filtered
    private boolean filterParticles = true;
    // Controls whether damage is filtered
    private boolean filterDamage = true;
    // Tracks whether mode is focused
    private boolean focusedMode;
    // Tracks whether reject air pressure is focused
    private boolean focusedRejectAirPressure = true;
    // Tracks whether beam fueled this tick is set
    private boolean beamFueledThisTick;
    // Current processing upgrade type
    private ProcessingUpgradeType processingUpgradeType = ProcessingUpgradeType.NONE;
    // Current processing upgrade tier
    private int processingUpgradeTier;
    // Current propulsion upgrade tier
    private int propulsionUpgradeTier;
    // Current beam color
    private int beamColor = DEFAULT_BEAM_COLOR;
    // Current beam max opacity
    private float beamMaxOpacity = 0.34f;
    // Current plume color ratio
    private float plumeColorRatio = DEFAULT_PLUME_COLOR_RATIO;
    // Tracks whether infinite solid fuel is set
    private boolean infiniteSolidFuel;
    // Current solid fuel type id
    private String solidFuelTypeId = "";
    // Current solid fuel power multiplier
    private double solidFuelPowerMultiplier = 1.0D;
    // Current solid fuel particle style
    private ExhaustParticleStyle solidFuelParticleStyle = ExhaustParticleStyle.DEFAULT;
    // Tracks whether suppress inventory mode sync is set
    private boolean suppressInventoryModeSync;
    // Minimum throttle
    private float minThrottle = 0.0f;
    // Maximum throttle
    private float maxThrottle = 1.0f;
    // Current CC id
    private String ccId = "";
    // Current CC alias
    private String ccAlias = "";
    // Current air current update cooldown
    private int airCurrentUpdateCooldown;
    // Current entity search cooldown
    private int entitySearchCooldown;
    // Current signal poll cooldown
    private int signalPollCooldown;
    // Client sync phase
    private final int clientSyncPhase;
    // Tracks whether persistent tick is dirty
    private boolean persistentTickStateDirty;
    // Tracks whether client sync is pending
    private boolean clientSyncPending;
    // Tracks whether update air flow is set
    private boolean updateAirFlow = true;
    // Last airflow direction
    private Direction lastAirflowDirection;
    // Air current
    private final AirCurrent airCurrent = new ThrusterAirCurrent(this);
    // Current exhaust occlusion cache time
    private long exhaustOcclusionCacheTime = Long.MIN_VALUE;
    // Current exhaust occlusion requested distance
    private double exhaustOcclusionRequestedDistance = -1.0D;
    // Current exhaust occlusion distance
    private double exhaustOcclusionDistance = -1.0D;
    // Current exhaust occlusion start
    private Vec3 exhaustOcclusionStart = Vec3.ZERO;
    // Current exhaust occlusion direction
    private Vec3 exhaustOcclusionDirection = Vec3.ZERO;

    // Current ovr dir
    private Vec3 ovrDir = null;
    // Current ovr dir local
    private Vec3 ovrDirLocal = null;
    // Force point
    private final Vector3d forcePoint;
    // Cached override force direction
    private Vector3d cachedOverrideForceDirection;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster
    public ThrusterBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.THRUSTER.get(), pos, blockState);
        forcePoint = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        clientSyncPhase = Math.floorMod(pos.hashCode(), CLIENT_SYNC_INTERVAL_TICKS);
        signalPollCooldown = Math.floorMod(pos.hashCode(), SIGNAL_POLL_INTERVAL_TICKS);
    }

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, ThrusterBlockEntity be) {
        be.tick();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the thruster
    @Override
    public void tick() {
        super.tick();
        Level level = getLevel();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            tickClient();
            return;
        }
        if (signalPollCooldown-- <= 0) {
            signalPollCooldown = SIGNAL_POLL_INTERVAL_TICKS - 1;
            updateSignal();
        }
        enforceBeamSafeBuffer();
        int previousFuelAmount = fuelTank.getFluidAmount();
        int previousSolidFuelTicks = solidFuelTicks;
        int previousEnergy = energyStorage.getEnergyStored();
        double previousFuelDrain = fractionalFuelDrain;
        double previousSolidFuelDrain = fractionalSolidFuelDrain;
        boolean previousInfiniteSolidFuel = infiniteSolidFuel;
        pullFocusedEnergy();
        beamFueledThisTick = false;
        consumeFuel();
        boolean clientStateChanged = previousFuelAmount != fuelTank.getFluidAmount()
                || previousSolidFuelTicks != solidFuelTicks
                || previousEnergy != energyStorage.getEnergyStored()
                || previousInfiniteSolidFuel != infiniteSolidFuel;
        if (clientStateChanged
                || Double.compare(previousFuelDrain, fractionalFuelDrain) != 0
                || Double.compare(previousSolidFuelDrain, fractionalSolidFuelDrain) != 0) {
            markTickStateChanged(clientStateChanged);
        }
        tickServer();
        if (clientSyncPending
                && Math.floorMod(level.getGameTime(), CLIENT_SYNC_INTERVAL_TICKS) == clientSyncPhase) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            clientSyncPending = false;
        }
    }

    // Mark the tick state changed
    private void markTickStateChanged(boolean syncClient) {
        if (!persistentTickStateDirty) {
            persistentTickStateDirty = true;
            setChanged();
        }
        clientSyncPending |= syncClient;
    }

    // Update the client
    private void tickClient() {
        tickAirCurrent(true);
    }

    // Update the server
    private void tickServer() {
        syncComputerAttachment();
        tickAirCurrent(false);
    }

    // Sync the computer attachment
    private void syncComputerAttachment() {
        if (ccId.isEmpty() && ccAlias.isEmpty()) {
            return;
        }
        if (SimulatedHelper.getContainingSubLevelId(this) != null) {
            return;
        }
        setCcId("");
        setCcAlias("");
    }

    // Update the air current
    private void tickAirCurrent(boolean clientSide) {
        Direction flowDirection = getAirFlowDirection();
        if (flowDirection != lastAirflowDirection) {
            lastAirflowDirection = flowDirection;
            updateAirFlow = true;
        }

        if (airCurrentUpdateCooldown-- <= 0) {
            airCurrentUpdateCooldown = 20;
            updateAirFlow = true;
        }

        if (updateAirFlow) {
            updateAirFlow = false;
            airCurrent.rebuild();
            clearOcclusionCache();
        }

        if (clientSide && isActive() && CTConfigs.SERVER.enableThrusterParticles.get()) {
            if (getLocalParticleScale() <= 0.0D) {
                return;
            }
            if (focusedMode) {

                return;
            }

            if (isParticleFiltered()) {
                airCurrent.tick();
                return;
            }

            spawnProcessingTrailParticles();
            return;
        }

        if (getSpeed() == 0.0f) {
            return;
        }

        if (entitySearchCooldown-- <= 0) {
            entitySearchCooldown = 5;
            airCurrent.findEntities();
        }

        airCurrent.findAffectedHandlers();

        airCurrent.tick();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           PARTICLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Spawn the processing trail particles
    private void spawnProcessingTrailParticles() {
        Level level = this.level;
        if (level == null || !level.isClientSide || !isActive()) {
            return;
        }
        if (isParticleFiltered()) {
            return;
        }

        Vec3 localDirection = getVisualEffectDirection();
        if (localDirection.lengthSqr() < 1.0E-6D) {
            return;
        }
        localDirection = localDirection.normalize();
        Vec3 localStart = Vec3.atCenterOf(worldPosition).add(localDirection.scale(1.15D));
        double requestedDistance = Math.max(airCurrent.maxDistance, 0.5D);
        WorldExhaustGeometry geometry = getWorldExhaustGeometry(0.0D);
        double particleStartOffset = getParticleStartOffset(localStart, geometry);
        double requestedParticleDistance = Math.max(0.0D, requestedDistance - particleStartOffset);
        double maxDistance = Math.max(0.0D, geometry.maxDistance() - particleStartOffset);
        boolean particleRangeBlocked = maxDistance + 0.125D < requestedParticleDistance;
        if (particleRangeBlocked) {
            maxDistance = Math.max(0.0D, maxDistance - PARTICLE_BLOCK_STOP_MARGIN);
        }
        if (maxDistance <= 0.05D) {
            return;
        }
        float throttle = Mth.clamp(getAppliedThrottle(), 0.0f, 1.0f);
        double intensity = Math.max(0.0D, CTConfigs.SERVER.thrusterParticleIntensity.get()) * getLocalParticleScale();
        if (intensity <= 0.0D) {
            return;
        }
        FanProcessingType forcedType = getForcedProcessingType();
        Level worldParticleLevel = WorldSpaceParticleEmitter.resolveWorldLevel(this);
        if (worldParticleLevel == null) {
            return;
        }

        boolean spawnManualProcessingParticles = forcedType != null;
        double naturalExhaustRange = Mth.lerp(throttle, 2.0D, 5.5D);
        double exhaustCloudRange = Math.min(maxDistance, naturalExhaustRange);
        double visibleRangeRatio = Mth.clamp(maxDistance / Math.max(0.5D, naturalExhaustRange), 0.2D, 1.0D);
        if (forcedType != null && spawnManualProcessingParticles) {
            int processingSamples = Math.max(1, Mth.ceil((3.0f + throttle * 14.0f)
                    * (float) intensity * (float) visibleRangeRatio));
            for (int i = 0; i < processingSamples; i++) {
                double normalizedDistance = (i + level.random.nextDouble()) / processingSamples;
                double progress = maxDistance * normalizedDistance;
                Vec3 localSample = localStart.add(localDirection.scale(progress));
                double normalizedExhaustDistance = exhaustCloudRange <= 1.0E-6D ? 1.0D : progress / exhaustCloudRange;
                double density = progress > exhaustCloudRange
                        ? 0.0D
                        : Math.pow(Math.max(0.0D, 1.0D - normalizedExhaustDistance), 3.75D);
                int processingBursts = progress > exhaustCloudRange
                        ? 1
                        : Math.max(1, Mth.ceil((float) (1.0D + density * (1.0D + throttle * 2.5D))));
                for (int burst = 0; burst < processingBursts; burst++) {
                    Vec3 jitter = progress > exhaustCloudRange
                            ? Vec3.ZERO
                            : randomExhaustSpread(level, localDirection, (0.02D + progress * 0.015D) * Math.max(0.15D, density));
                    Vec3 particlePos = localSample.add(jitter);
                    if (isParticleInRange(localStart, localDirection, particlePos, maxDistance)) {
                        Vec3 worldParticlePos = SimulatedHelper.toGlobalWorldPosition(this, particlePos);
                        forcedType.spawnProcessingParticles(worldParticleLevel, worldParticlePos);
                    }
                }
            }
        }

        double plumeRange = Math.max(0.1D, exhaustCloudRange);
        int plumeSamples = Math.max(2, Mth.ceil((8.0f + throttle * 20.0f)
                * (float) intensity * (float) visibleRangeRatio));
        for (int i = 0; i < plumeSamples; i++) {
            double normalizedDistance = (i + level.random.nextDouble()) / plumeSamples;
            double progress = plumeRange * normalizedDistance;
            Vec3 localSample = localStart.add(localDirection.scale(progress));
            double normalizedExhaustDistance = progress / naturalExhaustRange;
            double density = Math.pow(Math.max(0.0D, 1.0D - normalizedExhaustDistance), 2.85D);
            spawnFuelTrailParticles(level, localSample, localDirection, density, throttle, progress, plumeRange,
                    particleRangeBlocked);
        }
    }

    // Get the particle start offset
    private double getParticleStartOffset(Vec3 localStart, WorldExhaustGeometry geometry) {
        if (localStart == null || geometry == null) {
            return 0.0D;
        }

        Vec3 worldStart = SimulatedHelper.toGlobalWorldPosition(this, localStart);
        if (worldStart == null) {
            return 0.0D;
        }
        return Math.max(0.0D, worldStart.subtract(geometry.origin()).dot(geometry.direction()));
    }

    // Get the exhaust distance
    private double getExhaustDistance(Vec3 start, Vec3 dir, double maxDist) {
        if (level == null || maxDist <= 0.0D || start == null || dir == null
                || dir.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }

        dir = dir.normalize();
        long gameTime = level.getGameTime();
        if (isOcclusionCacheValid(gameTime, start, dir, maxDist)) {
            return Mth.clamp(exhaustOcclusionDistance, 0.0D, maxDist);
        }

        double distance = findBlockingDistance(start, dir, maxDist);
        exhaustOcclusionCacheTime = gameTime;
        exhaustOcclusionStart = start;
        exhaustOcclusionDirection = dir;
        exhaustOcclusionRequestedDistance = maxDist;
        exhaustOcclusionDistance = distance;
        return Mth.clamp(distance, 0.0D, maxDist);
    }

    // Find the blocking distance
    private double findBlockingDistance(Vec3 start, Vec3 dir, double maxDist) {
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(this);
        boolean includeWorldLevel = ovrDir != null || containingSubLevel != null;
        return SubLevelParticleOcclusion.findBlockingDistance(getWorldSpaceQueryLevel(), containingSubLevel,
                start, dir, maxDist, includeWorldLevel);
    }

    // Check if the occlusion cache is valid
    private boolean isOcclusionCacheValid(long gameTime, Vec3 start, Vec3 dir, double maxDist) {
        if (exhaustOcclusionDistance < 0.0D) {
            return false;
        }
        if (gameTime - exhaustOcclusionCacheTime >= EXHAUST_OCCLUSION_CACHE_TICKS) {
            return false;
        }
        return Math.abs(exhaustOcclusionRequestedDistance - maxDist) < 0.0625D
                && exhaustOcclusionStart.distanceToSqr(start) < 0.01D
                && exhaustOcclusionDirection.distanceToSqr(dir) < 1.0E-4D;
    }

    // Clear the occlusion cache
    private void clearOcclusionCache() {
        exhaustOcclusionCacheTime = Long.MIN_VALUE;
        exhaustOcclusionRequestedDistance = -1.0D;
        exhaustOcclusionDistance = -1.0D;
    }

    // Check if the particle is in range
    private static boolean isParticleInRange(Vec3 start, Vec3 dir, Vec3 sample, double maxDist) {
        if (start == null || dir == null || sample == null) {
            return false;
        }
        double progress = sample.subtract(start).dot(dir);
        return progress >= -0.03125D && progress <= maxDist + 0.03125D;
    }

    // Get the local particle scale
    private static double getLocalParticleScale() {
        return Mth.clamp(CTConfigs.CLIENT.thrusterParticleScale.get(), 0.0D, 1.0D);
    }

    // Spawn the fuel trail particles
    private void spawnFuelTrailParticles(Level level, Vec3 sample, Vec3 dir, double density,
            float throttle, double progress, double exhaustCloudRange, boolean particleRangeBlocked) {
        if (progress > exhaustCloudRange || density <= 0.0005D) {
            return;
        }

        double range = Math.max(0.5D, exhaustCloudRange);
        double progressRatio = Mth.clamp(progress / range, 0.0D, 1.0D);
        ExhaustParticleStyle particleStyle = getExhaustStyle();

        float baseRed = 1.0f;
        float baseGreen = 0.95f;
        float baseBlue = 0.82f;
        if (particleStyle == ExhaustParticleStyle.EXPERIENCE) {
            baseRed = 0.65f;
            baseGreen = 1.0f;
            baseBlue = 0.42f;
        } else if (particleStyle == ExhaustParticleStyle.WATER_BUBBLE) {
            baseRed = 0.48f;
            baseGreen = 0.82f;
            baseBlue = 1.0f;
        } else if (soulThruster) {
            baseRed = 0.38f;
            baseGreen = 0.22f;
            baseBlue = 0.95f;
        }

        if (beamColor != DEFAULT_BEAM_COLOR) {
            float colorRatio = getPlumeColorRatio();
            float dyedRed = ((beamColor >> 16) & 0xFF) / 255.0f;
            float dyedGreen = ((beamColor >> 8) & 0xFF) / 255.0f;
            float dyedBlue = (beamColor & 0xFF) / 255.0f;
            baseRed = Mth.lerp(colorRatio, baseRed, dyedRed);
            baseGreen = Mth.lerp(colorRatio, baseGreen, dyedGreen);
            baseBlue = Mth.lerp(colorRatio, baseBlue, dyedBlue);
        }

        double forwardDriftScale = 1.0D;
        if (particleRangeBlocked) {
            forwardDriftScale = Mth.clamp((exhaustCloudRange - progress) / 0.75D, 0.05D, 1.0D);
        }
        Vec3 drift = dir.scale((1.35D + throttle * 3.15D) * forwardDriftScale);
        int plumeBursts = Math.max(1, Mth.ceil((float) (density * (1.0D + throttle * 5.0D))));
        for (int burst = 0; burst < plumeBursts; burst++) {
            Vec3 jitter = randomExhaustSpread(level, dir,
                    (0.012D + progressRatio * 0.065D) * Math.max(0.25D, density));
            Vec3 vel = drift.scale(0.82D + density * 0.28D + level.random.nextDouble() * 0.1D)
                    .add(jitter.scale(1.2D + throttle * 0.6D));
            float brightness = 0.9f + level.random.nextFloat() * 0.16f;
            float red = Mth.clamp(baseRed * brightness, 0.0f, 1.0f);
            float green = Mth.clamp(baseGreen * brightness, 0.0f, 1.0f);
            float blue = Mth.clamp(baseBlue * brightness, 0.0f, 1.0f);
            WorldSpaceParticleEmitter.addParticle(this,
                    new ColoredCloudParticleOptions(red, green, blue),
                    sample.add(jitter), vel);
        }
    }

    // Get the random exhaust spread
    private Vec3 randomExhaustSpread(Level level, Vec3 dir, double radius) {
        Vec3 axisA = Math.abs(dir.y) > 0.8D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : dir.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 axisB = dir.cross(axisA).normalize();
        double offsetA = (level.random.nextDouble() - 0.5D) * 2.0D * radius;
        double offsetB = (level.random.nextDouble() - 0.5D) * 2.0D * radius;
        return axisA.scale(offsetA).add(axisB.scale(offsetB));
    }

    // Get the fan equivalent speed
    private float getFanEquivalentSpeed() {

        float base = getAppliedThrottle() * 256.0f * (float) getPropulsionPowerMultiplier();
        if (soulThruster) {
            base *= 1.45f;
        }
        return base;
    }

    // Check if this is player creative flying
    private static boolean isPlayerCreativeFlying(Entity entity) {
        if (entity instanceof Player player) {
            return player.isCreative() && player.getAbilities().flying;
        }
        return false;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              FUEL
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Consume the fuel
    private void consumeFuel() {
        float appliedThrottle = getAppliedThrottle();
        if (!enabled || appliedThrottle <= 0.0f) {
            return;
        }

        if (focusedMode) {
            int feToConsume = getRequiredBeamFePerTick();
            if (energyStorage.extractEnergy(feToConsume, true) >= feToConsume) {
                energyStorage.extractEnergy(feToConsume, false);
                beamFueledThisTick = true;
            }
            return;
        }

        if (infiniteSolidFuel) {
            ItemStack queuedFuel = thrusterInventory.getStackInSlot(SLOT_SOLID_FUEL);
            if (queuedFuel.isEmpty() || !isInfiniteSolidFuelItem(queuedFuel)) {
                clearSolidFuelProfile();
            }
            return;
        }

        if (solidFuelTicks <= 0) {
            primeSolidFuelFromSlot();
        }

        if (infiniteSolidFuel) {
            return;
        }

        if (solidFuelTicks > 0) {
            fractionalSolidFuelDrain += getProcessingFuelDrainMultiplier();
            int solidTicksToDrain = Math.max(1, (int) fractionalSolidFuelDrain);
            solidFuelTicks -= solidTicksToDrain;
            fractionalSolidFuelDrain -= solidTicksToDrain;
            if (solidFuelTicks <= 0) {
                solidFuelTicks = 0;
                clearSolidFuelProfile();
                fractionalSolidFuelDrain = 0.0D;
            }
            return;
        }

        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) {
            return;
        }

        double throttleExponent = Math.max(0.1D, CTConfigs.COMMON.thrusterFuelThrottleExponent.get());
        ThrusterFuelData.FuelProfile fuelProfile = ThrusterFuelData.getProfile(fuel.getFluid());
        if (fuelProfile == null) {
            return;
        }
        double scaledUsePerTick = getFluidFuelUsePerTick(fuelProfile, appliedThrottle, throttleExponent);

        fractionalFuelDrain += scaledUsePerTick;
        int toDrain = (int) fractionalFuelDrain;
        if (toDrain <= 0) {
            return;
        }

        int available = fuelTank.getFluidAmount();
        int drainNow = Math.min(available, toDrain);
        if (drainNow > 0) {
            fuelTank.drain(drainNow, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            fractionalFuelDrain -= drainNow;
        }

    }

    // Update the signal
    public void updateSignal() {
        if (level == null) {
            return;
        }
        int updatedSignalStrength = level.getBestNeighborSignal(worldPosition);
        if (updatedSignalStrength == signalStrength) {
            return;
        }
        signalStrength = updatedSignalStrength;
        redstoneThrottle = signalStrength / 15.0f;
        refreshThrottle();
        markTickStateChanged(true);
    }

    // Get the fuel tank
    public FluidTank getFuelTank() {
        return fuelTank;
    }

    // Get the item inventory
    public IItemHandler getItemInventory() {
        return thrusterInventory;
    }

    // Extract the inventory slot
    public ItemStack extractInventorySlot(int slot, net.minecraft.world.entity.player.Player giveToPlayer) {
        ItemStack current = thrusterInventory.getStackInSlot(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        thrusterInventory.setStackInSlot(slot, ItemStack.EMPTY);
        if (giveToPlayer != null) {
            if (!giveToPlayer.getInventory().add(current.copy())) {
                giveToPlayer.drop(current.copy(), false);
            }
        }
        return current;
    }

    // Extract the inventory slot for block removal
    public ItemStack extractInventorySlotForBlockRemoval(int slot) {
        ItemStack current = thrusterInventory.getStackInSlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        suppressInventoryModeSync = true;
        try {
            thrusterInventory.setStackInSlot(slot, ItemStack.EMPTY);
        } finally {
            suppressInventoryModeSync = false;
        }
        return current;
    }

    // Insert the inventory slot
    public boolean insertInventorySlot(int slot, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !thrusterInventory.isItemValid(slot, stack) || !thrusterInventory.getStackInSlot(slot).isEmpty()) {
            return false;
        }
        thrusterInventory.setStackInSlot(slot, stack.copyWithCount(1));
        return true;
    }

    // Get the focus input
    public IEnergyStorage getFocusInput() {
        return focusInput;
    }

    // Get the energy storage
    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    // Try to insert upgrade item
    public boolean tryInsertUpgradeItem(Player player, ItemStack heldStack) {
        return tryInsertInventoryItem(player, heldStack, SLOT_UPGRADE);
    }

    // Try to insert modifier item
    public boolean tryInsertModifierItem(Player player, ItemStack heldStack) {
        return tryInsertInventoryItem(player, heldStack, SLOT_MODIFIER);
    }

    // Try to insert inventory item
    private boolean tryInsertInventoryItem(Player player, ItemStack heldStack, int slot) {
        if (heldStack.isEmpty() || !thrusterInventory.isItemValid(slot, heldStack) || !thrusterInventory.getStackInSlot(slot).isEmpty()) {
            return false;
        }
        ItemStack single = heldStack.copyWithCount(1);
        thrusterInventory.setStackInSlot(slot, single);
        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }
        return true;
    }

    // Set the throttle
    public void setThrottle(float throttle) {
        float clamped = Mth.clamp(throttle, 0.0f, 1.0f);
        if (Math.abs(computerThrottle - clamped) <= 1.0E-5F && controlMode == ControlMode.COMPUTER) {
            return;
        }
        computerThrottle = clamped;
        controlMode = ControlMode.COMPUTER;
        refreshThrottle();
        markTickStateChanged(true);
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        return GRAPH_CONTROL_DATA;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        return GRAPH_CONTROL_DATA;
    }

    // Get the graph control data
    static Map<String, String> graphControlData() {
        return GRAPH_CONTROL_DATA;
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "throttle" -> AdvancedGraphDocument.Value.number(getThrottle());
            case "plume_color_ratio" -> AdvancedGraphDocument.Value.number(getPlumeColorRatio());
            case "beam_max_opacity" -> AdvancedGraphDocument.Value.number(getBeamMaxOpacity());
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value value) {
        if (!GRAPH_CONTROL_DATA.containsKey(field) || value == null) {
            return false;
        }
        double requested = value.asNumber();
        if (!Double.isFinite(requested)) {
            return false;
        }
        float normalized = (float) Mth.clamp(requested, 0.0D, 1.0D);
        switch (field) {
            case "throttle" -> setThrottle(normalized);
            case "plume_color_ratio" -> setPlumeColorRatio(normalized);
            case "beam_max_opacity" -> setBeamMaxOpacity(normalized);
            default -> {
                return false;
            }
        }
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         DIRECT CONTROL
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float value) {
        String normalizedChannel = channelId == null || channelId.isBlank() ? "controller" : channelId;
        float clamped = Mth.clamp(value, 0.0f, 1.0f);
        boolean changed;
        if (clamped <= 1.0E-4f) {
            changed = directSignals.remove(normalizedChannel) != null;
        } else {
            Float prev = directSignals.put(normalizedChannel, clamped);
            changed = prev == null || Math.abs(prev - clamped) > 1.0E-5F;
        }
        if (!changed) {
            return;
        }

        computerThrottle = getDirectThrottle();
        controlMode = ControlMode.COMPUTER;
        refreshThrottle();
        markTickStateChanged(true);
    }

    // Apply the ship control envelope
    public void applyShipControlEnvelope(String channelId, float minimum, float maximum, float throttle) {
        String normalizedChannel = channelId == null || channelId.isBlank() ? "ship_control" : channelId;
        float clampedMinimum = Mth.clamp(minimum, 0.0f, 1.0f);
        float clampedMaximum = Mth.clamp(Math.max(clampedMinimum, maximum), 0.0f, 1.0f);
        float clampedThrottle = Mth.clamp(throttle, 0.0f, 1.0f);
        if (clampedThrottle <= 1.0E-4f) {
            clearShipControlEnvelope(normalizedChannel);
            return;
        }
        ShipControlEnvelope requested =
                new ShipControlEnvelope(normalizedChannel, clampedMinimum, clampedMaximum, clampedThrottle);
        if (requested.equals(shipControlEnvelope)) {
            return;
        }
        shipControlEnvelope = requested;
        markTickStateChanged(true);
    }

    // Clear the ship control envelope
    public void clearShipControlEnvelope(String channelId) {
        String normalizedChannel = channelId == null || channelId.isBlank() ? "ship_control" : channelId;
        if (shipControlEnvelope != null && shipControlEnvelope.channelId().equals(normalizedChannel)) {
            shipControlEnvelope = null;
            markTickStateChanged(true);
        }
    }

    // Set the ship control damage protected sub levels
    public void setShipControlDamageProtectedSubLevels(
            String channelId,
            Set<UUID> subLevelIds
    ) {
        String normalizedChannel = channelId == null || channelId.isBlank()
                ? "ship_control" : channelId;
        Set<UUID> protectedIds = new HashSet<>();
        if (subLevelIds != null) {
            for (UUID subLevelId : subLevelIds) {
                if (subLevelId != null) {
                    protectedIds.add(subLevelId);
                }
            }
        }
        if (protectedIds.isEmpty()) {
            shipControlDamageProtectedSubLevels.remove(normalizedChannel);
        } else {
            shipControlDamageProtectedSubLevels.put(
                    normalizedChannel, Set.copyOf(protectedIds));
        }
    }

    // Clear the ship control damage protection
    public void clearShipControlDamageProtection(String channelId) {
        String normalizedChannel = channelId == null || channelId.isBlank()
                ? "ship_control" : channelId;
        shipControlDamageProtectedSubLevels.remove(normalizedChannel);
    }

    // Release the orphaned ship control
    public boolean releaseOrphanedShipControl(String channelId) {
        String normalizedChannel = channelId == null || channelId.isBlank() ? "ship_control" : channelId;
        boolean changed = false;
        if (shipControlEnvelope != null && shipControlEnvelope.channelId().equals(normalizedChannel)) {
            shipControlEnvelope = null;
            changed = true;
        }
        if (directSignals.remove(normalizedChannel) != null) {
            changed = true;
        }
        if (legacyShipControlPersistence) {
            legacyShipControlPersistence = false;
            changed = true;
            if (controlMode == ControlMode.COMPUTER && computerThrottle > 1.0E-4F
                    && directSignals.isEmpty()) {
                computerThrottle = 0.0F;
                controlMode = ControlMode.REDSTONE;
            }
        }
        if (changed) {
            refreshThrottle();
            setChanged();
            sendData();
        }
        return changed;
    }

    // Get the direct throttle
    private float getDirectThrottle() {
        float throttle = 0.0f;
        for (float val : directSignals.values()) {
            throttle = Math.max(throttle, Mth.clamp(val, 0.0f, 1.0f));
        }
        return throttle;
    }

    // Get the custom name
    @Override
    public @org.jetbrains.annotations.Nullable String getCustomName() {
        return customName;
    }

    // Set the custom name
    @Override
    public void setCustomName(@org.jetbrains.annotations.Nullable String name) {
        this.customName = (name != null && !name.isBlank()) ? name.strip() : null;
        setChanged();
        sendData();
    }

    // Get the throttle
    public float getThrottle() {
        return level != null && level.isClientSide && shipControlEnvelope != null
                ? shipControlEnvelope.throttle()
                : throttle;
    }

    // Clear the CC throttle override
    public void clearCcThrottleOverride() {
        controlMode = ControlMode.REDSTONE;
        computerThrottle = 0.0f;
        refreshThrottle();
        setChanged();
    }

    // Get the control mode
    public ControlMode getControlMode() {
        return controlMode;
    }

    // Set the control mode
    public void setControlMode(ControlMode mode) {
        this.controlMode = mode;
        refreshThrottle();
        setChanged();
    }

    // Get the computer throttle
    public float getComputerThrottle() {
        return computerThrottle;
    }

    // Set the enabled
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Check if this is soul thruster
    public boolean isSoulThruster() {
        return soulThruster;
    }

    // Enable the soul thruster
    public boolean enableSoulThruster() {
        return true;
    }

    // Disable the soul thruster
    public boolean disableSoulThruster() {
        return true;
    }

    // Check if this is a peaceful mode
    public boolean isPeacefulMode() {
        return peacefulMode;
    }

    // Check if this is sound filtered
    public boolean isSoundFiltered() {
        return peacefulMode && filterSound;
    }

    // Check if this is particle filtered
    public boolean isParticleFiltered() {
        return peacefulMode && filterParticles;
    }

    // Check if this is damage filtered
    public boolean isDamageFiltered() {
        return peacefulMode && filterDamage;
    }

    // Check if the filter sound is enabled
    public boolean isFilterSoundEnabled() {
        return filterSound;
    }

    // Check if filtered particles are enabled
    public boolean isFilterParticlesEnabled() {
        return filterParticles;
    }

    // Check if the filter damage is enabled
    public boolean isFilterDamageEnabled() {
        return filterDamage;
    }

    // Check if this is a focused mode
    public boolean isFocusedMode() {
        return focusedMode;
    }

    // Check if this can accept fuel
    public boolean canAcceptFuel() {
        return !focusedMode;
    }

    // Set the focused mode
    public boolean setFocusedMode(boolean focusedMode) {
        if (this.focusedMode == focusedMode) {
            return false;
        }
        this.focusedMode = focusedMode;
        if (focusedMode) {
            clearFuelReserves();
        }
        syncFocusedBlockState();
        setChanged();
        if (level != null && !level.isClientSide) {
            refreshCapabilities();
        }
        return true;
    }

    // Toggle focused thrust mode
    public boolean toggleFocusedMode() {
        if (focusedMode) {
            thrusterInventory.setStackInSlot(SLOT_LENS, ItemStack.EMPTY);
        } else if (CTItems.THRUSTER_LENSE != null && thrusterInventory.getStackInSlot(SLOT_LENS).isEmpty()) {
            thrusterInventory.setStackInSlot(SLOT_LENS, new ItemStack(CTItems.THRUSTER_LENSE.get()));
        }
        return focusedMode;
    }

    // Get the beam color
    public int getBeamColor() {
        return beamColor;
    }

    // Get the beam max opacity
    public float getBeamMaxOpacity() {
        return Mth.clamp(beamMaxOpacity, 0.0f, 1.0f);
    }

    // Set the beam max opacity
    public void setBeamMaxOpacity(float beamMaxOpacity) {
        float clamped = Mth.clamp(beamMaxOpacity, 0.0f, 1.0f);
        if (Math.abs(this.beamMaxOpacity - clamped) < 0.0001f) {
            return;
        }
        this.beamMaxOpacity = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Get the plume color ratio
    public float getPlumeColorRatio() {
        return Mth.clamp(plumeColorRatio, 0.0f, 1.0f);
    }

    // Set the plume color ratio
    public void setPlumeColorRatio(float plumeColorRatio) {
        float clamped = Mth.clamp(plumeColorRatio, 0.0f, 1.0f);
        if (Math.abs(this.plumeColorRatio - clamped) < 0.0001f) {
            return;
        }
        this.plumeColorRatio = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Set the beam color
    public boolean setBeamColor(int beamColor) {
        int normalized = beamColor & 0xFFFFFF;
        if (this.beamColor == normalized) {
            return false;
        }
        this.beamColor = normalized;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    // Set the peaceful mode
    public boolean setPeacefulMode(boolean peacefulMode) {
        if (this.peacefulMode == peacefulMode) {
            return false;
        }
        this.peacefulMode = peacefulMode;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    // Toggle peaceful thrust mode
    public boolean togglePeacefulMode() {

        setPeacefulMode(!peacefulMode);
        return peacefulMode;
    }

    // Try to insert solid fuel
    public boolean tryInsertSolidFuel(Player player, ItemStack stack) {
        if (!canAcceptFuel()) {
            return false;
        }
        if (!canUseAsSolidFuel(stack)) {
            return false;
        }

        ItemStack current = thrusterInventory.getStackInSlot(SLOT_SOLID_FUEL);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return false;
        }

        int limit = thrusterInventory.getSlotLimit(SLOT_SOLID_FUEL);
        int currentCount = current.getCount();
        if (currentCount >= limit) {
            return false;
        }

        ItemStack updated = current.isEmpty() ? stack.copyWithCount(1) : current.copyWithCount(currentCount + 1);
        thrusterInventory.setStackInSlot(SLOT_SOLID_FUEL, updated);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    // Check if this can be used as solid fuel
    public static boolean canUseAsSolidFuel(ItemStack stack) {
        return getSolidFuelProfile(stack) != null;
    }

    // Check if this is thruster lens
    private static boolean isThrusterLens(ItemStack stack) {
        return CTItems.THRUSTER_LENSE != null && stack.is(CTItems.THRUSTER_LENSE.get());
    }

    // Get the solid fuel profile
    private static SolidFuelProfile getSolidFuelProfile(ItemStack stack) {
        ThrusterFuelData.ItemFuelProfile profile = ThrusterFuelData.getSolidFuelProfile(stack);
        if (profile == null) {
            return null;
        }
        return new SolidFuelProfile(profile.burnTicks(), profile.infiniteBurn(), profile.propulsionMultiplier(),
                mapParticleStyle(profile.particleStyle()), profile.fuelId());
    }

    // Clear the solid fuel profile
    private void clearSolidFuelProfile() {
        infiniteSolidFuel = false;
        solidFuelTypeId = "";
        solidFuelPowerMultiplier = 1.0D;
        solidFuelParticleStyle = ExhaustParticleStyle.DEFAULT;
    }

    // Check if this is enabled
    public boolean isEnabled() {
        return enabled;
    }

    // Get the min throttle
    public float getMinThrottle() {
        return minThrottle;
    }

    // Get the max throttle
    public float getMaxThrottle() {
        return maxThrottle;
    }

    // Apply the configuration
    public void applyConfiguration(boolean enabled, float minThrottle, float maxThrottle,
            ControlMode controlMode, float beamMaxOpacity, float plumeColorRatio,
            boolean filterSound, boolean filterParticles, boolean filterDamage,
            boolean focusedRejectAirPressure) {
        this.enabled = enabled;
        this.minThrottle = Mth.clamp(minThrottle, 0.0f, 1.0f);
        this.maxThrottle = Mth.clamp(Math.max(this.minThrottle, maxThrottle), 0.0f, 1.0f);
        this.controlMode = controlMode == null ? ControlMode.REDSTONE : controlMode;
        this.beamMaxOpacity = Mth.clamp(beamMaxOpacity, 0.0f, 1.0f);
        this.plumeColorRatio = Mth.clamp(plumeColorRatio, 0.0f, 1.0f);
        this.filterSound = filterSound;
        this.filterParticles = filterParticles;
        this.filterDamage = filterDamage;
        this.focusedRejectAirPressure = focusedRejectAirPressure;
        refreshThrottle();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Get the fuel amount
    public int getFuelAmount() {
        if (focusedMode) {
            return 0;
        }
        if (infiniteSolidFuel) {
            return Integer.MAX_VALUE;
        }
        return fuelTank.getFluidAmount() + solidFuelTicks;
    }

    // Get the fuel capacity
    public int getFuelCapacity() {
        if (focusedMode) {
            return 0;
        }
        return fuelTank.getCapacity();
    }

    // Get the estimated burn seconds
    public double getEstimatedBurnSeconds() {
        if (focusedMode) {
            return 0.0D;
        }
        if (infiniteSolidFuel) {
            return Double.POSITIVE_INFINITY;
        }
        if (solidFuelTicks > 0) {
            return solidFuelTicks / 20.0D;
        }
        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) {
            return 0.0D;
        }
        float appliedThrottle = getAppliedThrottle();
        if (!enabled || appliedThrottle <= 0.0f) {
            return Double.POSITIVE_INFINITY;
        }
        double throttleExponent = Math.max(0.1D, CTConfigs.COMMON.thrusterFuelThrottleExponent.get());
        ThrusterFuelData.FuelProfile fuelProfile = ThrusterFuelData.getProfile(fuel.getFluid());
        if (fuelProfile == null) {
            return 0.0D;
        }
        double usePerTick = getFluidFuelUsePerTick(fuelProfile, appliedThrottle, throttleExponent);
        if (usePerTick <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return (fuelTank.getFluidAmount() / usePerTick) / 20.0D;
    }

    // Get the fuel consumption mb per tick
    public double getFuelConsumptionMbPerTick() {
        if (focusedMode || !isActive() || infiniteSolidFuel || solidFuelTicks > 0) {
            return 0.0D;
        }
        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) return 0.0D;
        ThrusterFuelData.FuelProfile fuelProfile = ThrusterFuelData.getProfile(fuel.getFluid());
        if (fuelProfile == null) return 0.0D;
        double throttleExponent = Math.max(0.1D, CTConfigs.COMMON.thrusterFuelThrottleExponent.get());
        return getFluidFuelUsePerTick(
                fuelProfile, Mth.clamp(getAppliedThrottle(), 0.0F, 1.0F), throttleExponent);
    }

    // Get the estimated fuel consumption mb per tick
    public double getEstimatedFuelConsumptionMbPerTick(float throttle) {
        if (focusedMode || infiniteSolidFuel) {
            return 0.0D;
        }
        float requestedThrottle = Mth.clamp(throttle, 0.0F, 1.0F);
        if (solidFuelTicks > 0) {
            return requestedThrottle > 0.0F ? 1.0D : 0.0D;
        }
        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) {
            return 0.0D;
        }
        ThrusterFuelData.FuelProfile fuelProfile = ThrusterFuelData.getProfile(fuel.getFluid());
        if (fuelProfile == null) {
            return 0.0D;
        }
        double throttleExponent = Math.max(
                0.1D, CTConfigs.COMMON.thrusterFuelThrottleExponent.get());
        return getFluidFuelUsePerTick(
                fuelProfile, requestedThrottle, throttleExponent);
    }

    // Format the duration
    private static String formatDuration(double seconds) {
        if (Double.isInfinite(seconds)) {
            return "Idle";
        }
        long total = Math.max(0L, Math.round(seconds));
        long mins = total / 60L;
        long secs = total % 60L;
        return mins + "m " + secs + "s";
    }

    // Get the fuel display name
    private static String getFuelDisplayName(FluidStack fuel) {
        return fuel.getHoverName().getString();
    }

    // Get the propulsion power multiplier
    private double getPropulsionPowerMultiplier() {
        double propulsionUpgradeMultiplier = getActivePropulsionUpgradeMultiplier();
        if (infiniteSolidFuel || solidFuelTicks > 0) {
            return solidFuelPowerMultiplier * propulsionUpgradeMultiplier;
        }
        FluidStack fuel = fuelTank.getFluid();
        if (!fuel.isEmpty()) {
            ThrusterFuelData.FuelProfile profile = ThrusterFuelData.getProfile(fuel.getFluid());
            if (profile != null) {
                propulsionUpgradeMultiplier *= profile.propulsionMultiplier();
                if (OxidizedFuel.isOxidized(fuel)) {
                    propulsionUpgradeMultiplier *= OxidizedFuel.propulsionMultiplier();
                }
            }
        }
        return propulsionUpgradeMultiplier;
    }

    // Get the propulsion upgrade multiplier for tier
    public static double getPropulsionUpgradeMultiplierForTier(int tier) {
        return switch (Mth.clamp(tier, 1, 4)) {
            case 1 -> 2.0D;
            case 2 -> 4.0D;
            case 3 -> 8.0D;
            default -> 16.0D;
        };
    }

    // Get the active propulsion upgrade multiplier
    private double getActivePropulsionUpgradeMultiplier() {
        if (propulsionUpgradeTier <= 0) {
            return 1.0D;
        }
        return Math.min(getPropulsionUpgradeMultiplierForTier(propulsionUpgradeTier),
                CTConfigs.COMMON.propulsionUpgradeMaxMultiplier.get());
    }

    // Get the exhaust style
    private ExhaustParticleStyle getExhaustStyle() {
        if (infiniteSolidFuel || solidFuelTicks > 0) {
            return solidFuelParticleStyle;
        }
        ThrusterFuelData.FuelProfile profile = ThrusterFuelData.getProfile(fuelTank.getFluid().getFluid());
        if (profile == null) {
            return ExhaustParticleStyle.DEFAULT;
        }
        return mapParticleStyle(profile.particleStyle());
    }

    // Map the particle style
    private static ExhaustParticleStyle mapParticleStyle(String particleStyle) {
        return switch (particleStyle.toLowerCase(java.util.Locale.ROOT)) {
            case "experience", "xp" -> ExhaustParticleStyle.EXPERIENCE;
            case "water_bubble", "bubble" -> ExhaustParticleStyle.WATER_BUBBLE;
            default -> ExhaustParticleStyle.DEFAULT;
        };
    }

    // Get the effective burn time ticks per bucket
    private static double getEffectiveBurnTimeTicksPerBucket(ThrusterFuelData.FuelProfile profile) {
        return Math.max(1.0D, profile.burnTimeTicksPerBucket() * profile.weight());
    }

    // Get the fluid fuel use per tick
    private double getFluidFuelUsePerTick(ThrusterFuelData.FuelProfile profile, float appliedThrottle, double throttleExponent) {
        double baseFuelUsePerTick = Math.max(0.01D, CTConfigs.COMMON.thrusterBaseFuelUsePerTick.get());
        double neutralizedBurnRatio = NEUTRAL_FUEL_BURN_TIME_TICKS_PER_BUCKET / getEffectiveBurnTimeTicksPerBucket(profile);
        return baseFuelUsePerTick
                * neutralizedBurnRatio
                * Math.pow(appliedThrottle, throttleExponent)
                * getProcessingFuelDrainMultiplier();
    }

    // Get the fuel burn rate damage multiplier
    private double getFuelBurnRateDamageMultiplier() {
        if (!isActive()) {
            return 0.0D;
        }

        float appliedThrottle = Mth.clamp(getAppliedThrottle(), 0.0f, 1.0f);
        if (focusedMode) {
            double baselineFePerTick = Math.max(1.0D, CTConfigs.COMMON.focusedModeFePerTick.get());
            return Mth.clamp(getRequiredBeamFePerTick() / baselineFePerTick, 0.25D, 32.0D);
        }

        if (infiniteSolidFuel || solidFuelTicks > 0) {
            double throttleFactor = Mth.clamp(appliedThrottle, 0.25f, 1.0f);
            double fuelPower = Math.max(0.25D, solidFuelPowerMultiplier);
            return Mth.clamp(getProcessingFuelDrainMultiplier() * fuelPower * throttleFactor, 0.25D, 32.0D);
        }

        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) {
            return 1.0D;
        }

        ThrusterFuelData.FuelProfile fuelProfile = ThrusterFuelData.getProfile(fuel.getFluid());
        if (fuelProfile == null) {
            return 1.0D;
        }

        double throttleExponent = Math.max(0.1D, CTConfigs.COMMON.thrusterFuelThrottleExponent.get());
        double baselineFuelUse = Math.max(0.01D, CTConfigs.COMMON.thrusterBaseFuelUsePerTick.get());
        double fuelUsePerTick = getFluidFuelUsePerTick(fuelProfile, appliedThrottle, throttleExponent);
        return Mth.clamp(fuelUsePerTick / baselineFuelUse, 0.25D, 32.0D);
    }

    // Scale the thruster damage
    private float scaleThrusterDamage(float baseDamage) {
        return (float) Mth.clamp(baseDamage * getFuelBurnRateDamageMultiplier(), 0.5D, 80.0D);
    }

    // Check if the fuel is valid
    private boolean isValidFuel(FluidStack fluidStack) {
        return ThrusterFuelData.isFuel(fluidStack.getFluid());
    }

    // Check if this is an infinite solid fuel item
    private static boolean isInfiniteSolidFuelItem(ItemStack stack) {
        SolidFuelProfile profile = getSolidFuelProfile(stack);
        return profile != null && profile.infiniteBurn();
    }

    // Prime the solid fuel from slot
    private void primeSolidFuelFromSlot() {
        ItemStack queued = thrusterInventory.getStackInSlot(SLOT_SOLID_FUEL);
        if (queued.isEmpty()) {
            return;
        }
        SolidFuelProfile profile = getSolidFuelProfile(queued);
        if (profile == null) {
            return;
        }

        solidFuelTypeId = profile.fuelId();
        solidFuelPowerMultiplier = profile.powerMultiplier();
        solidFuelParticleStyle = profile.particleStyle();
        if (profile.infiniteBurn()) {
            infiniteSolidFuel = true;
            solidFuelTicks = 0;
            setChanged();
            return;
        }

        infiniteSolidFuel = false;
        solidFuelTicks = Math.min(Integer.MAX_VALUE - 1, solidFuelTicks + profile.burnTicks());
        ItemStack reduced = queued.copy();
        reduced.shrink(1);
        thrusterInventory.setStackInSlot(SLOT_SOLID_FUEL, reduced);
    }

    // Get the block direction
    @Override
    public Direction getBlockDirection() {
        return getBlockState().getValue(CTDirectionalBlock.FACING);
    }

    // Set the ovr dir
    public void setOvrDir(Vec3 dir) {
        setOverrideDirection(dir, null);
    }

    // Set the override direction
    public void setOverrideDirection(Vec3 worldDir, @Nullable Vec3 localDir) {
        Vec3 normalizedLocal = localDir == null || localDir.lengthSqr() < 1.0E-6D ? null : localDir.normalize();
        Vec3 normalized = worldDir == null || worldDir.lengthSqr() < 1.0E-6D ? null : worldDir.normalize();
        boolean changed = (ovrDir == null) != (normalized == null)
                || (ovrDir != null && normalized != null && ovrDir.distanceToSqr(normalized) > 1.0E-4D);
        boolean localChanged = (ovrDirLocal == null) != (normalizedLocal == null)
                || (ovrDirLocal != null && normalizedLocal != null && ovrDirLocal.distanceToSqr(normalizedLocal) > 1.0E-4D);
        ovrDir = normalized;
        ovrDirLocal = normalizedLocal;
        cachedOverrideForceDirection = normalizedLocal == null
                ? null
                : new Vector3d(normalizedLocal.x, normalizedLocal.y, normalizedLocal.z);
        if (changed || localChanged) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    // Get the facing direction vector
    private Vec3 getFacingDirectionVector() {
        return Vec3.atLowerCornerOf(getBlockDirection().getNormal());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        THRUST / PHYSICS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the world thrust direction
    public Vec3 getWorldThrustDirection() {
        if (ovrDir != null) {
            return ovrDir;
        }

        Vec3 worldFromFacing = SimulatedHelper.toContainingWorldDirection(this, getFacingDirectionVector());
        if (worldFromFacing != null && worldFromFacing.lengthSqr() >= 1.0E-6D) {
            return worldFromFacing.normalize();
        }
        return getFacingDirectionVector();
    }

    // Get the local thrust direction
    public Vec3 getLocalThrustDirection() {
        if (ovrDirLocal != null) {
            return ovrDirLocal;
        }
        if (ovrDir != null) {
            Vec3 localDirection = SimulatedHelper.toContainingLocalDirection(this, ovrDir);
            if (localDirection != null && localDirection.lengthSqr() >= 1.0E-6D) {
                return localDirection.normalize();
            }
            return ovrDir;
        }

        return getFacingDirectionVector().normalize();
    }

    // Get the effective direction
    public Vec3 getEffectiveDirection() {
        return getWorldThrustDirection();
    }

    // Get the visual effect direction
    public Vec3 getVisualEffectDirection() {

        return getLocalThrustDirection();
    }

    // Get the visual source center
    private Vec3 getVisualSourceCenter() {

        Vec3 dir = getWorldThrustDirection();
        if (dir.lengthSqr() < 1.0E-6D) {
            dir = getFacingDirectionVector();
        }
        return Vec3.atCenterOf(worldPosition).add(dir.normalize().scale(0.55D));
    }

    // Get the world exhaust origin
    public Vec3 getWorldExhaustOrigin() {
        Vec3 localCenter = Vec3.atCenterOf(worldPosition);
        Vec3 worldCenter = SimulatedHelper.toGlobalWorldPosition(this, localCenter);
        if (worldCenter == null) {
            worldCenter = localCenter;
        }

        Vec3 dir = getWorldThrustDirection();
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            dir = SimulatedHelper.toContainingWorldDirection(this, getFacingDirectionVector());
        }
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            dir = getFacingDirectionVector();
        }
        return worldCenter.add(dir.normalize().scale(0.55D));
    }

    // Get the world exhaust geometry
    private WorldExhaustGeometry getWorldExhaustGeometry(double extraDistance) {
        Vec3 dir = getWorldThrustDirection();
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            dir = SimulatedHelper.toContainingWorldDirection(this, getFacingDirectionVector());
        }
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            dir = getFacingDirectionVector();
        }
        dir = dir.normalize();

        double requestedDistance = Math.max(airCurrent.maxDistance, 0.5D);
        Vec3 origin = getWorldExhaustOrigin();
        double maxDistance = getExhaustDistance(origin, dir, requestedDistance);
        boolean blocked = maxDistance < requestedDistance - 0.03125D;
        double extension = blocked ? 0.0D : Math.max(0.0D, extraDistance);
        Vec3 end = origin.add(dir.scale(maxDistance + extension));
        return new WorldExhaustGeometry(origin, dir, maxDistance, blocked, new AABB(origin, end));
    }

    // Get the world exhaust hit
    private @Nullable WorldExhaustHit getWorldExhaustHit(Entity entity, WorldExhaustGeometry geometry) {
        if (entity == null || geometry == null) {
            return null;
        }
        Vec3 worldPosition = getEntityWorldPosition(entity, null);
        return getWorldExhaustHit(worldPosition, THRUSTER_ENTITY_QUERY_RADIUS + entity.getBbWidth() * 0.5D,
                0.75D, geometry);
    }

    // Get the world exhaust hit
    private @Nullable WorldExhaustHit getWorldExhaustHit(Vec3 worldPosition, double radius, double extraDistance,
            WorldExhaustGeometry geometry) {
        if (worldPosition == null || geometry == null) {
            return null;
        }
        Vec3 offset = worldPosition.subtract(geometry.origin());
        double axialDistance = offset.dot(geometry.direction());
        double extension = geometry.blocked() ? 0.0D : Math.max(0.0D, extraDistance);
        if (axialDistance < 0.0D || axialDistance > geometry.maxDistance() + extension) {
            return null;
        }

        Vec3 closestPoint = geometry.origin().add(geometry.direction().scale(axialDistance));
        double allowedRadius = Math.max(0.0D, radius);
        if (worldPosition.distanceToSqr(closestPoint) > allowedRadius * allowedRadius) {
            return null;
        }

        return new WorldExhaustHit(worldPosition, axialDistance);
    }

    // Get the visual exhaust origin
    public Vec3 getVisualExhaustOrigin() {
        return getVisualSourceCenter();
    }

    // Check if the client effect source is valid
    public boolean isClientEffectSourceValid() {
        return level != null && level.isClientSide && !isRemoved()
                && getBlockState().getBlock() instanceof ThrusterBlock;
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {

        Vec3 origin = getVisualExhaustOrigin();
        Vec3 dir = getWorldThrustDirection();
        if (dir.lengthSqr() < 1.0E-6D) {

            dir = getWorldThrustDirection();
        }
        dir = dir.normalize();

        double beamLength = Mth.clamp(Math.max(getForcedProcessingDistance() + 8.0D, 64.0D), 64.0D, 256.0D);
        Vec3 end = origin.add(dir.scale(beamLength));
        return new AABB(origin, end).inflate(3.0D);
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ThrusterMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.thruster.config_screen.title");
    }

    // Check if the player can use this
    @Override
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    // Find the attached mount
    @SuppressWarnings("unused")
    private MountedThrusterMount findAttachedMount() {
        if (level == null) {
            return null;
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor instanceof ThrusterBearingBlockEntity bearing && bearing.getAttachedThruster() == this) {
                return new MountedThrusterMount(neighborPos, bearing.getAttachedThrusterDirection());
            }
        }
        return null;
    }

    // Get the airflow
    @Override
    public double getAirflow() {
        double baseAirflow = CTConfigs.COMMON.thrusterBaseAirflow.get();
        double airflow = finitePositiveOrDefault(baseAirflow, DEFAULT_MAX_AIRFLOW)
                * finiteNonNegativeOrDefault(getAppliedThrottle(), 0.0D)
                * finiteNonNegativeOrDefault(getPropulsionPowerMultiplier(), 0.0D);
        return Double.isFinite(airflow) ? airflow : 0.0D;
    }

    // Get the thrust
    @Override
    public double getThrust() {
        if (!isActive()) {
            return 0.0D;
        }
        double res = maximumUnscaledThrust()
                * finiteNonNegativeOrDefault(getAppliedThrottle(), 0.0D)
                * finiteNonNegativeOrDefault(getPropulsionPowerMultiplier(), 0.0D);
        return Double.isFinite(res) ? res : 0.0D;
    }

    // Get the available maximum scaled thrust
    public double getAvailableMaximumScaledThrust() {
        SolidFuelProfile queuedSolidFuel = focusedMode ? null : getSolidFuelProfile(
                thrusterInventory.getStackInSlot(SLOT_SOLID_FUEL));
        boolean hasPower = focusedMode
                ? beamFueledThisTick || hasRequiredBeamEnergy()
                : infiniteSolidFuel || solidFuelTicks > 0
                || queuedSolidFuel != null || !fuelTank.getFluid().isEmpty();
        if (!enabled || !hasPower) {
            return 0.0D;
        }
        boolean usingQueuedSolidFuel = !focusedMode && !infiniteSolidFuel
                && solidFuelTicks <= 0 && fuelTank.getFluid().isEmpty()
                && queuedSolidFuel != null;
        double powerMultiplier = getPropulsionPowerMultiplier();
        if (usingQueuedSolidFuel) {
            powerMultiplier = finiteNonNegativeOrDefault(
                    queuedSolidFuel.powerMultiplier(), 0.0D)
                    * getActivePropulsionUpgradeMultiplier();
        }
        boolean queuedFuelRejectsPressure = usingQueuedSolidFuel
                && "createthrusters:oxidized_creative_blaze_cake".equals(
                queuedSolidFuel.fuelId());
        double maximum = maximumUnscaledThrust()
                * finiteNonNegativeOrDefault(powerMultiplier, 0.0D)
                * finiteNonNegativeOrDefault(getAirflowScaling(), 0.0D)
                * finiteNonNegativeOrDefault(
                queuedFuelRejectsPressure ? 1.0D : getCurrentAirPressure(), 0.0D);
        return Double.isFinite(maximum) ? Math.max(0.0D, maximum) : 0.0D;
    }

    // Get the maximum imum unscaled thrust
    private double maximumUnscaledThrust() {
        double maximum = finitePositiveOrDefault(
                CTConfigs.COMMON.thrusterBaseThrust.get(), DEFAULT_MAX_THRUST);
        if (focusedMode) {
            maximum *= finiteNonNegativeOrDefault(
                    CTConfigs.COMMON.beamThrustMultiplier.get(),
                    DEFAULT_BEAM_THRUST_MULTIPLIER);
        }
        return maximum;
    }

    // Get the current air pressure
    @Override
    public double getCurrentAirPressure() {
        return ignoresAtmosphericScaling() ? 1.0D : BlockEntityPropeller.super.getCurrentAirPressure();
    }

    // Get the airflow scaling
    @Override
    public double getAirflowScaling() {
        return 1.0D;
    }

    // Check if this uses oxidized propellant
    private boolean usesOxidizedPropellant() {
        if (focusedMode) {
            return false;
        }
        if (infiniteSolidFuel || solidFuelTicks > 0) {
            return "createthrusters:oxidized_creative_blaze_cake".equals(solidFuelTypeId);
        }
        return OxidizedFuel.isOxidized(fuelTank.getFluid());
    }

    // Check if this is a superheated mode
    public boolean isSuperheatedMode() {
        return usesOxidizedPropellant();
    }

    // Check if the thruster ignores atmospheric scaling
    private boolean ignoresAtmosphericScaling() {
        return focusedMode && focusedRejectAirPressure || usesOxidizedPropellant();
    }

    // Check if the focused air pressure rejection is enabled
    public boolean isFocusedAirPressureRejectionEnabled() {
        return focusedRejectAirPressure;
    }

    // Check if this is active
    @Override
    public boolean isActive() {
        if (focusedMode) {

            return enabled && getAppliedThrottle() > 0.0f && (beamFueledThisTick || hasRequiredBeamEnergy());
        }
        return enabled && getAppliedThrottle() > 0.0f && (fuelTank.getFluidAmount() > 0 || solidFuelTicks > 0 || infiniteSolidFuel);
    }

    // Update the physics
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!isActive() || subLevel == null) {
            return;
        }
        Vector3d forceDirection;
        if (cachedOverrideForceDirection != null) {
            forceDirection = cachedOverrideForceDirection;
        } else if (ovrDir != null) {
            Vec3 localDirection = getLocalThrustDirection();
            forceDirection = new Vector3d(localDirection.x, localDirection.y, localDirection.z);
        } else {
            forceDirection = FACING_FORCE_DIRECTIONS[getBlockDirection().ordinal()];
        }
        SablePointImpulseApi.applyDirectional(
                subLevel,
                handle,
                ForceGroups.PROPULSION.get(),
                forcePoint,
                forceDirection,
                getScaledThrust(),
                timeStep);
    }

    // Create the facing force directions
    private static Vector3d[] createFacingForceDirections() {
        Direction[] directions = Direction.values();
        Vector3d[] vectors = new Vector3d[directions.length];
        for (Direction dir : directions) {
            Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());
            vectors[dir.ordinal()] = new Vector3d(normal.x, normal.y, normal.z);
        }
        return vectors;
    }

    // Use the non-negative fallback when the value is not finite
    private static double finiteNonNegativeOrDefault(double val, double fallback) {
        return Double.isFinite(val) ? Math.max(0.0D, val) : fallback;
    }

    // Get the finite positive or default
    private static double finitePositiveOrDefault(double val, double fallback) {
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }

    // Convert the thruster to containing local direction
    @SuppressWarnings("unused")
    private Vec3 toContainingLocalDirection(Vec3 worldDirection) {
        return SimulatedHelper.toContainingLocalDirection(this, worldDirection);
    }

    // Get the level
    @Override
    public Level getLevel() {
        return level;
    }

    // Get the air current
    @Override
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    // Get the air current world
    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    // Get the air current pos
    @Override
    public BlockPos getAirCurrentPos() {

        return worldPosition;
    }

    // Get the speed
    @Override
    public float getSpeed() {
        if (!isActive()) {
            return 0.0f;
        }
        return getFanEquivalentSpeed();
    }

    // Get the airflow origin side
    @Override
    public Direction getAirflowOriginSide() {

        Vec3 dir = getLocalThrustDirection();
        if (dir == null || dir.lengthSqr() < 1.0E-6D) {
            dir = getFacingDirectionVector();
        }
        return Direction.getNearest((float) dir.x, (float) dir.y, (float) dir.z);
    }

    // Get the air flow direction
    @Override
    public Direction getAirFlowDirection() {
        float speed = getSpeed();
        if (speed == 0.0f) {
            return null;
        }

        return getAirflowOriginSide();
    }

    // Collect the affected entities
    private List<Entity> collectAffectedEntities() {
        if (level == null || !isActive()) {
            return List.of();
        }

        WorldExhaustGeometry geometry = getWorldExhaustGeometry(0.75D);
        Level queryLevel = getWorldSpaceQueryLevel();
        List<Entity> entities = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        AABB worldBounds = geometry.bounds().inflate(THRUSTER_ENTITY_QUERY_RADIUS + 1.0D);

        collectEntitiesFromLevel(queryLevel, null, worldBounds, geometry, seen, entities);
        if (queryLevel != level) {
            collectEntitiesFromLevel(level, null, worldBounds, geometry, seen, entities);
        }

        for (Object subLevel : getExhaustSubLevels(worldBounds)) {
            Level subLevelLevel = getSubLevelLevel(subLevel);
            if (subLevelLevel == null) {
                continue;
            }
            Vec3 localStart = SimulatedHelper.toContainingLocalPosition(subLevel, geometry.origin());
            Vec3 localEnd = SimulatedHelper.toContainingLocalPosition(subLevel,
                    geometry.origin().add(geometry.direction().scale(geometry.maxDistance() + 0.75D)));
            if (localStart == null || localEnd == null) {
                continue;
            }
            AABB localBounds = new AABB(localStart, localEnd).inflate(THRUSTER_ENTITY_QUERY_RADIUS + 1.0D);
            collectEntitiesFromLevel(subLevelLevel, subLevel, localBounds, geometry, seen, entities);
        }

        return entities;
    }

    // Get the exhaust sub levels
    private List<Object> getExhaustSubLevels(AABB worldBounds) {
        Level queryLevel = getWorldSpaceQueryLevel();
        List<Object> subLevels = new ArrayList<>(SimulatedHelper.getIntersectingSubLevels(queryLevel, worldBounds));
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(this);
        if (containingSubLevel != null && !containsIdentity(subLevels, containingSubLevel)) {
            subLevels.add(0, containingSubLevel);
        }
        return subLevels;
    }

    // Check if this contains identity
    private static boolean containsIdentity(List<Object> values, Object candidate) {
        for (Object val : values) {
            if (val == candidate) {
                return true;
            }
        }
        return false;
    }

    // Collect the entities from level
    private void collectEntitiesFromLevel(Level queryLevel, @Nullable Object subLevel, AABB queryBounds,
            WorldExhaustGeometry geometry, Set<Entity> seen, List<Entity> entities) {
        if (queryLevel == null || queryBounds == null) {
            return;
        }

        for (Entity entity : queryLevel.getEntitiesOfClass(Entity.class, queryBounds, this::isValidExhaustEntity)) {
            if (entity == null || !seen.add(entity)) {
                continue;
            }
            Vec3 worldPosition = getEntityWorldPosition(entity, subLevel);
            if (getWorldExhaustHit(worldPosition, THRUSTER_ENTITY_QUERY_RADIUS + entity.getBbWidth() * 0.5D,
                    0.75D, geometry) != null) {
                entities.add(entity);
            }
        }
    }

    // Check if the exhaust entity is valid
    private boolean isValidExhaustEntity(Entity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved() && !isPlayerCreativeFlying(entity);
    }

    // Get the world space query level
    private Level getWorldSpaceQueryLevel() {
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(this);
        Level owningLevel = getSubLevelLevel(containingSubLevel);
        return owningLevel == null ? level : owningLevel;
    }

    // Resolve the containing level
    private static @Nullable Level getSubLevelLevel(@Nullable Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object res = subLevel.getClass().getMethod("getLevel").invoke(subLevel);
            return res instanceof Level subLevelOwner ? subLevelOwner : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the entity world position
    private Vec3 getEntityWorldPosition(Entity entity, @Nullable Object knownSubLevel) {
        if (entity == null) {
            return Vec3.ZERO;
        }

        Object subLevel = knownSubLevel;
        if (subLevel == null) {
            subLevel = SimulatedHelper.getEntityTrackingSubLevel(entity);
        }
        if (subLevel == null) {
            subLevel = SimulatedHelper.getContainingSubLevel(entity.level(), entity.position());
        }

        Vec3 worldPosition = subLevel == null
                ? entity.position()
                : SimulatedHelper.toContainingWorldPosition(subLevel, entity.position());
        if (worldPosition == null) {
            worldPosition = entity.position();
        }

        Level queryLevel = getWorldSpaceQueryLevel();
        return SimulatedHelper.projectOutOfSubLevels(queryLevel == null ? entity.level() : queryLevel, worldPosition);
    }

    // Convert the thruster to entity world movement
    private static Vec3 toEntityWorldMovement(Entity entity, Vec3 localMovement) {
        if (entity == null || localMovement == null || localMovement.lengthSqr() < 1.0E-6D) {
            return localMovement;
        }

        Object subLevel = SimulatedHelper.getEntityTrackingSubLevel(entity);
        if (subLevel == null) {
            subLevel = SimulatedHelper.getContainingSubLevel(entity.level(), entity.position());
        }
        if (subLevel == null) {
            return localMovement;
        }

        double length = localMovement.length();
        Vec3 worldDirection = SimulatedHelper.toContainingWorldDirection(subLevel, localMovement);
        return worldDirection == null || worldDirection.lengthSqr() < 1.0E-6D
                ? localMovement
                : worldDirection.normalize().scale(length);
    }

    // Apply the exhaust motion
    private void applyExhaustMotion(Entity entity, WorldExhaustHit hit, WorldExhaustGeometry geometry) {
        if (entity == null || hit == null || geometry == null) {
            return;
        }

        float speed = Math.abs(getSpeed());
        float sneakModifier = entity.isShiftKeyDown() ? 4096.0f : 512.0f;
        double distanceFromOrigin = Math.max(0.25D, hit.worldPosition().distanceTo(geometry.origin()));
        double maxDistance = Math.max(0.25D, geometry.maxDistance());
        float acceleration = (float) ((speed / sneakModifier) / (distanceFromOrigin / maxDistance));

        Vec3 previousLocalMotion = entity.getDeltaMovement();
        Vec3 previousWorldMotion = toEntityWorldMovement(entity, previousLocalMotion);
        float maxAcceleration = 5.0f;
        Vec3 correctionWorld = geometry.direction().scale(acceleration).subtract(previousWorldMotion);
        correctionWorld = new Vec3(
                Mth.clamp(correctionWorld.x, -maxAcceleration, maxAcceleration),
                Mth.clamp(correctionWorld.y, -maxAcceleration, maxAcceleration),
                Mth.clamp(correctionWorld.z, -maxAcceleration, maxAcceleration)
        ).scale(0.125D);

        Vec3 correctionLocal = SimulatedHelper.toEntityLocalMovement(entity, correctionWorld);
        entity.setDeltaMovement(previousLocalMotion.add(correctionLocal));
        entity.fallDistance = 0.0f;
    }

    // Check if this is source removed
    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    // Get the block pos
    @Override
    public BlockPos getBlockPos() {
        return worldPosition;
    }

    // Get the fuel type id
    public String getFuelTypeId() {
        if (infiniteSolidFuel || solidFuelTicks > 0) {
            return solidFuelTypeId;
        }
        FluidStack fuel = fuelTank.getFluid();
        if (!fuel.isEmpty()) {
            return getFuelDisplayName(fuel);
        }
        ItemStack queuedSolidFuel = thrusterInventory.getStackInSlot(SLOT_SOLID_FUEL);
        if (!queuedSolidFuel.isEmpty()) {
            ResourceLocation id = queuedSolidFuel.getItem().builtInRegistryHolder().key().location();
            return id.getNamespace() + ":" + id.getPath();
        }
        return "";
    }

    // Get the solid fuel ticks
    public int getSolidFuelTicks() {
        return solidFuelTicks;
    }

    // Check if this has infinite solid fuel
    public boolean hasInfiniteSolidFuel() {
        return infiniteSolidFuel;
    }

    // Get the real thrust
    public double getRealThrust() {
        return CTPropulsionTelemetry.getRealThrust(this);
    }

    // Get the lift capacity
    public double getLiftCapacity() {
        return CTPropulsionTelemetry.getLiftCapacity(this);
    }

    // Get the signal strength
    public int getSignalStrength() {
        return signalStrength;
    }

    // Get the CC id
    public String getCcId() {
        return ccId;
    }

    // Set the CC id
    public void setCcId(String assemblyComputerCraftId) {
        String normalized = assemblyComputerCraftId == null ? "" : assemblyComputerCraftId;
        if (this.ccId.equals(normalized)) {
            return;
        }
        this.ccId = normalized;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Get the CC alias
    public String getCcAlias() {
        return ccAlias;
    }

    // Set the CC alias
    public void setCcAlias(String alias) {
        String normalized = alias == null ? "" : alias;
        if (this.ccAlias.equals(normalized)) {
            return;
        }
        this.ccAlias = normalized;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Get the assembly ComputerCraft alias
    public String getAssemblyComputerCraftAlias() {
        return getCcAlias();
    }

    // Set the assembly ComputerCraft alias
    public void setAssemblyComputerCraftAlias(String alias) {
        setCcAlias(alias);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                      PROCESSING / EFFECTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the forced processing distance
    public double getForcedProcessingDistance() {
        return airCurrent.maxDistance;
    }

    // Get the forced processing type
    private FanProcessingType getForcedProcessingType() {
        if (!CTConfigs.SERVER.enableThrusterBulkProcessing.get()) {
            return null;
        }
        if (!hasProcessingUpgrade()) {
            return null;
        }
        return switch (processingUpgradeType) {
            case SMOKING -> AllFanProcessingTypes.SMOKING;
            case SMELTING -> AllFanProcessingTypes.BLASTING;
            case HAUNTING -> AllFanProcessingTypes.HAUNTING;
            default -> null;
        };
    }

    // Get the bulk processing speed multiplier
    private double getBulkProcessingSpeedMultiplier() {
        if (!isActive() || !CTConfigs.SERVER.enableThrusterBulkProcessing.get()) {
            return 0.0D;
        }
        if (!hasProcessingUpgrade()) {
            return 0.0D;
        }
        return Mth.clamp(getAppliedThrottle(), 0.0f, 1.0f) * getUpgradeProcessingFullThrottleMultiplier(processingUpgradeTier);
    }

    // Check if this has processing upgrade
    private boolean hasProcessingUpgrade() {
        if (processingUpgradeTier <= 0 || processingUpgradeType == ProcessingUpgradeType.NONE) {
            return false;
        }
        return !focusedMode || processingUpgradeTier >= 3;
    }

    // Get the upgrade processing multiplier
    private static double getUpgradeProcessingFullThrottleMultiplier(int tier) {
        return switch (Mth.clamp(tier, 1, 4)) {
            case 1 -> 0.5D;
            case 2 -> 1.0D;
            case 3 -> 1.5D;
            default -> 2.0D;
        };
    }

    // Get the upgrade fuel drain multiplier
    private static double getUpgradeFuelDrainMultiplier(int tier) {
        return switch (Mth.clamp(tier, 1, 4)) {
            case 1 -> 1.25D;
            case 2 -> 1.5D;
            case 3 -> 2.0D;
            default -> 2.5D;
        };
    }

    // Get the processing fuel drain multiplier
    private double getProcessingFuelDrainMultiplier() {
        double base = hasProcessingUpgrade() ? getUpgradeFuelDrainMultiplier(processingUpgradeTier) : 1.0D;
        return base * getActivePropulsionUpgradeFuelCostMultiplier();
    }

    // Get the propulsion upgrade fuel cost multiplier for tier
    public static double getPropulsionUpgradeFuelCostMultiplierForTier(int tier) {
        return switch (Mth.clamp(tier, 1, 4)) {
            case 1 -> 2.0D;
            case 2 -> 6.0D;
            case 3 -> 10.0D;
            default -> 18.0D;
        };
    }

    // Get the active propulsion upgrade fuel cost multiplier
    private double getActivePropulsionUpgradeFuelCostMultiplier() {
        if (propulsionUpgradeTier <= 0) {
            return 1.0D;
        }
        return getPropulsionUpgradeFuelCostMultiplierForTier(propulsionUpgradeTier);
    }

    // Get the processing upgrade tier
    public int getProcessingUpgradeTier() {
        return processingUpgradeTier;
    }

    // Get the processing upgrade type
    public ProcessingUpgradeType getProcessingUpgradeType() {
        return processingUpgradeType;
    }

    // Check if this can apply processing upgrade
    public boolean canApplyProcessingUpgrade(ProcessingUpgradeType type, int tier) {
        if (type == null || type == ProcessingUpgradeType.NONE) {
            return false;
        }
        if (tier < 1 || tier > 4) {
            return false;
        }
        if (focusedMode && tier < 3) {
            return false;
        }
        return true;
    }

    // Apply the processing upgrade
    public boolean applyProcessingUpgrade(ProcessingUpgradeType type, int tier) {
        if (type == null || type == ProcessingUpgradeType.NONE || !canApplyProcessingUpgrade(type, tier)) {
            return false;
        }
        ItemStack slotStack = thrusterInventory.getStackInSlot(SLOT_UPGRADE);
        if (slotStack.getItem() instanceof ProcessingUpgradeItem item
                && item.getType() == type && item.getTier() == tier) {
            return false;
        }
        thrusterInventory.setStackInSlot(SLOT_UPGRADE, processingUpgradeStack(type, tier));
        return true;
    }

    // Get the processing upgrade stack
    private static ItemStack processingUpgradeStack(ProcessingUpgradeType type, int tier) {
        DeferredItem<? extends Item> item = switch (type) {
            case SMOKING -> switch (tier) {
                case 1 -> CTItems.PROCESSING_UPGRADE_SMOKING_T1;
                case 2 -> CTItems.PROCESSING_UPGRADE_SMOKING_T2;
                case 3 -> CTItems.PROCESSING_UPGRADE_SMOKING_T3;
                default -> CTItems.PROCESSING_UPGRADE_SMOKING_T4;
            };
            case SMELTING -> switch (tier) {
                case 1 -> CTItems.PROCESSING_UPGRADE_SMELTING_T1;
                case 2 -> CTItems.PROCESSING_UPGRADE_SMELTING_T2;
                case 3 -> CTItems.PROCESSING_UPGRADE_SMELTING_T3;
                default -> CTItems.PROCESSING_UPGRADE_SMELTING_T4;
            };
            case HAUNTING -> switch (tier) {
                case 1 -> CTItems.PROCESSING_UPGRADE_HAUNTING_T1;
                case 2 -> CTItems.PROCESSING_UPGRADE_HAUNTING_T2;
                case 3 -> CTItems.PROCESSING_UPGRADE_HAUNTING_T3;
                default -> CTItems.PROCESSING_UPGRADE_HAUNTING_T4;
            };
            default -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    // Clear the processing progress
    private void clearProcessingProgress(ItemEntity itemEntity) {
        itemEntity.getPersistentData().remove(ITEM_PROCESSING_PROGRESS_KEY);
    }

    // Apply the bulk processing
    private boolean applyBulkProcessing(ItemEntity itemEntity, FanProcessingType processingType) {
        double speedMultiplier = getBulkProcessingSpeedMultiplier();
        if (speedMultiplier <= 0.0D) {
            clearProcessingProgress(itemEntity);
            return false;
        }
        if (!FanProcessing.canProcess(itemEntity, processingType)) {
            clearProcessingProgress(itemEntity);
            return false;
        }

        CompoundTag persistentData = itemEntity.getPersistentData();
        double accumulatedProgress = persistentData.getDouble(ITEM_PROCESSING_PROGRESS_KEY) + speedMultiplier;
        int processingSteps = Mth.floor(accumulatedProgress);
        persistentData.putDouble(ITEM_PROCESSING_PROGRESS_KEY, accumulatedProgress - processingSteps);
        if (processingSteps <= 0) {
            return false;
        }

        for (int step = 0; step < processingSteps; step++) {
            if (!itemEntity.isAlive() || !FanProcessing.canProcess(itemEntity, processingType)) {
                clearProcessingProgress(itemEntity);
                return false;
            }
            if (FanProcessing.applyProcessing(itemEntity, processingType)) {
                clearProcessingProgress(itemEntity);
                return true;
            }
        }

        return false;
    }

    // Apply the entity effect
    private void applyEntityEffect(Entity entity, Level world) {
        if (!(entity instanceof LivingEntity livingEntity) || !CTConfigs.COMMON.enableThrusterEntityDamage.get()) {
            return;
        }
        if (ShippingSchedulePilot.isPilot(livingEntity)) {
            return;
        }
        if (!hasClearDamagePath(entity)) {
            return;
        }
        if (isProtectedFromMappedShipThruster(entity)) {
            return;
        }
        if (isDamageFiltered()) {
            return;
        }

        ProcessingUpgradeType activeType = getEntityUpgradeType();
        if (activeType == ProcessingUpgradeType.HAUNTING) {
            applyHauntingDamage(livingEntity, world);
            return;
        }

        applyFireDamage(livingEntity, world, activeType);
    }

    // Check if this has clear damage path
    private boolean hasClearDamagePath(Entity entity) {
        if (entity == null || level == null) {
            return false;
        }
        Vec3 origin = getWorldExhaustOrigin();
        Vec3 target = getEntityWorldPosition(entity, null);
        Vec3 offset = target.subtract(origin);
        double distance = offset.length();
        if (distance <= 1.0E-5D) {
            return true;
        }
        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(this);
        double blockingDistance = SubLevelParticleOcclusion.findBlockingDistance(
                getWorldSpaceQueryLevel(), containingSubLevel,
                origin, offset.scale(1.0D / distance), distance,
                true, Set.of(), true);
        return blockingDistance + 0.125D >= distance;
    }

    // Check if this is protected from mapped ship thruster
    private boolean isProtectedFromMappedShipThruster(Entity entity) {
        if (entity == null || shipControlDamageProtectedSubLevels.isEmpty()) {
            return false;
        }
        Object entitySubLevel = SimulatedHelper.getEntityTrackingSubLevel(entity);
        if (entitySubLevel == null) {
            entitySubLevel = SimulatedHelper.getContainingSubLevel(
                    entity.level(), entity.position());
        }
        UUID entitySubLevelId = SimulatedHelper.getSubLevelId(entitySubLevel);
        for (Set<UUID> protectedIds : shipControlDamageProtectedSubLevels.values()) {
            if (isProtectedShipSubLevel(entitySubLevelId, protectedIds)) {
                return true;
            }
        }
        return false;
    }

    // Check if this is a protected ship sublevel
    static boolean isProtectedShipSubLevel(
            @Nullable UUID entitySubLevelId,
            Set<UUID> protectedSubLevelIds
    ) {
        return entitySubLevelId != null
                && protectedSubLevelIds != null
                && protectedSubLevelIds.contains(entitySubLevelId);
    }

    // Get the entity upgrade type
    private ProcessingUpgradeType getEntityUpgradeType() {
        return hasProcessingUpgrade() ? processingUpgradeType : ProcessingUpgradeType.NONE;
    }

    // Apply the fire damage
    private void applyFireDamage(LivingEntity livingEntity, Level world, ProcessingUpgradeType activeType) {
        if (activeType == ProcessingUpgradeType.SMOKING || activeType == ProcessingUpgradeType.SMELTING) {
            markDropProcessing(livingEntity, world, activeType);
        }

        float fireSeconds = switch (activeType) {
            case SMOKING -> THRUSTER_SMOKING_FIRE_SECONDS;
            case SMELTING -> THRUSTER_SMELTING_FIRE_SECONDS;
            default -> THRUSTER_FIRE_SECONDS;
        };
        float damage = scaleThrusterDamage(activeType == ProcessingUpgradeType.SMELTING
                ? THRUSTER_SMELTING_DAMAGE
                : THRUSTER_FIRE_DAMAGE);
        livingEntity.igniteForSeconds(fireSeconds);
        livingEntity.hurt(activeType == ProcessingUpgradeType.SMELTING
                ? CreateDamageSources.fanLava(world)
                : CreateDamageSources.fanFire(world), damage);
    }

    // Apply the haunting damage
    private void applyHauntingDamage(LivingEntity livingEntity, Level world) {
        if (!CTConfigs.COMMON.enableThrusterMobHaunting.get()) {
            clearHauntingConversion(livingEntity);
            return;
        }

        EntityType<?> conversionType = getHauntingConversionType(livingEntity);
        if (conversionType == null) {
            clearHauntingConversion(livingEntity);
            return;
        }
        Entity conversionPreview = conversionType.create(world);
        if (!(conversionPreview instanceof LivingEntity)) {
            clearHauntingConversion(livingEntity);
            return;
        }

        float damage = scaleThrusterDamage(THRUSTER_HAUNTING_DAMAGE);
        CompoundTag conversionData = prepareHauntingConversion(livingEntity, world, conversionType);
        if (damage >= livingEntity.getHealth()) {
            if (spawnHauntingConversion(livingEntity, world, conversionType, conversionData)) {
                livingEntity.discard();
            } else {
                clearHauntingConversion(livingEntity);
            }
            return;
        }

        boolean damaged = livingEntity.hurt(world.damageSources().magic(), damage);
        if (!damaged || livingEntity.isAlive()) {
            clearHauntingConversion(livingEntity);
            return;
        }
        if (!spawnHauntingConversion(livingEntity, world, conversionType, conversionData)) {
            clearHauntingConversion(livingEntity);
        }
    }

    // Mark the drop processing
    private static void markDropProcessing(LivingEntity livingEntity, Level world, ProcessingUpgradeType activeType) {
        CompoundTag data = livingEntity.getPersistentData();
        data.putString(ENTITY_DROP_PROCESSING_TYPE_KEY, activeType.name());
        data.putLong(ENTITY_DROP_PROCESSING_TIME_KEY, world.getGameTime());
    }

    // Prepare the haunting conversion
    private static CompoundTag prepareHauntingConversion(LivingEntity livingEntity, Level world,
            EntityType<?> conversionType) {
        CompoundTag data = livingEntity.getPersistentData();
        data.putString(ENTITY_HAUNTING_CONVERSION_KEY, EntityType.getKey(conversionType).toString());
        data.putLong(ENTITY_HAUNTING_CONVERSION_TIME_KEY, world.getGameTime());

        CompoundTag conversionData = livingEntity.saveWithoutId(new CompoundTag());
        conversionData.remove("UUID");
        conversionData.remove("Health");
        conversionData.remove("HurtTime");
        conversionData.remove("DeathTime");
        return conversionData;
    }

    // Clear the haunting conversion
    private static void clearHauntingConversion(LivingEntity livingEntity) {
        CompoundTag data = livingEntity.getPersistentData();
        data.remove(ENTITY_HAUNTING_CONVERSION_KEY);
        data.remove(ENTITY_HAUNTING_CONVERSION_TIME_KEY);
    }

    // Clear the thruster entity markers
    private static void clearThrusterEntityMarkers(LivingEntity livingEntity) {
        CompoundTag data = livingEntity.getPersistentData();
        data.remove(ENTITY_DROP_PROCESSING_TYPE_KEY);
        data.remove(ENTITY_DROP_PROCESSING_TIME_KEY);
        data.remove(ENTITY_HAUNTING_CONVERSION_KEY);
        data.remove(ENTITY_HAUNTING_CONVERSION_TIME_KEY);
    }

    // Get the haunting conversion type
    private static @Nullable EntityType<?> getHauntingConversionType(LivingEntity livingEntity) {
        return MobHauntingConversions.getConversionType(livingEntity.getType());
    }

    // Spawn the haunting conversion
    private boolean spawnHauntingConversion(LivingEntity original, Level world,
            EntityType<?> conversionType, CompoundTag conversionData) {
        Level spawnLevel = original.level() == null ? world : original.level();
        Entity convertedEntity = conversionType.create(spawnLevel);
        if (!(convertedEntity instanceof LivingEntity converted)) {
            return false;
        }

        converted.deserializeNBT(original.registryAccess(), conversionData);
        clearThrusterEntityMarkers(converted);
        Vec3 spawnPosition = getHauntingPos(original, converted, spawnLevel);
        converted.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        converted.setDeltaMovement(Vec3.ZERO);
        converted.setYRot(original.getYRot());
        converted.setXRot(original.getXRot());
        converted.setYHeadRot(original.getYHeadRot());
        converted.setYBodyRot(original.getYRot());
        converted.setHealth(converted.getMaxHealth());
        boolean spawned = spawnLevel.addFreshEntity(converted);
        if (spawned) {
            spawnHauntingConversionParticles(converted, spawnLevel);
        }
        return spawned;
    }

    // Get the haunting pos
    private Vec3 getHauntingPos(LivingEntity original, LivingEntity converted, Level spawnLevel) {
        Vec3 basePosition = original.position();
        if (canPlaceConvertedEntity(converted, spawnLevel, basePosition)) {
            return basePosition;
        }

        Vec3 escapeDirection = getEscapeDir(original);
        Vec3 raisedEscapeDirection = escapeDirection.add(0.0D, 0.35D, 0.0D);
        if (raisedEscapeDirection.lengthSqr() < 1.0E-6D) {
            raisedEscapeDirection = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            raisedEscapeDirection = raisedEscapeDirection.normalize();
        }

        Vec3[] directions = {
                escapeDirection,
                raisedEscapeDirection,
                new Vec3(0.0D, 1.0D, 0.0D)
        };
        int steps = Mth.ceil(CONVERSION_POSITION_MAX_NUDGE / CONVERSION_POSITION_STEP);
        for (Vec3 dir : directions) {
            if (dir == null || dir.lengthSqr() < 1.0E-6D) {
                continue;
            }
            Vec3 normalized = dir.normalize();
            for (int step = 1; step <= steps; step++) {
                Vec3 candidate = basePosition.add(normalized.scale(step * CONVERSION_POSITION_STEP));
                if (canPlaceConvertedEntity(converted, spawnLevel, candidate)) {
                    return candidate;
                }
            }
        }

        return basePosition;
    }

    // Get the escape dir
    private Vec3 getEscapeDir(LivingEntity original) {
        Vec3 exhaustDirection = getWorldThrustDirection();
        if (exhaustDirection == null || exhaustDirection.lengthSqr() < 1.0E-6D) {
            exhaustDirection = original.getLookAngle().scale(-1.0D);
        }
        if (exhaustDirection == null || exhaustDirection.lengthSqr() < 1.0E-6D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }

        Vec3 localEscapeDirection = SimulatedHelper.toEntityLocalMovement(original, exhaustDirection.normalize().scale(-1.0D));
        if (localEscapeDirection == null || localEscapeDirection.lengthSqr() < 1.0E-6D) {
            return exhaustDirection.normalize().scale(-1.0D);
        }
        return localEscapeDirection.normalize();
    }

    // Check if this can place converted entity
    private static boolean canPlaceConvertedEntity(LivingEntity converted, Level spawnLevel, Vec3 pos) {
        if (converted == null || spawnLevel == null || pos == null) {
            return false;
        }

        AABB bounds = converted.getDimensions(converted.getPose())
                .makeBoundingBox(pos.x, pos.y, pos.z)
                .deflate(1.0E-4D);
        return spawnLevel.noCollision(converted, bounds);
    }

    // Spawn the haunting conversion particles
    private static void spawnHauntingConversionParticles(LivingEntity converted, Level world) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }

        double y = converted.getY() + converted.getBbHeight() * 0.55D;
        double horizontalSpread = Math.max(0.25D, converted.getBbWidth() * 0.75D);
        double verticalSpread = Math.max(0.25D, converted.getBbHeight() * 0.35D);
        serverLevel.sendParticles(ParticleTypes.END_ROD, converted.getX(), y, converted.getZ(),
                18, horizontalSpread, verticalSpread, horizontalSpread, 0.03D);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, converted.getX(), y, converted.getZ(),
                28, horizontalSpread * 0.8D, verticalSpread, horizontalSpread * 0.8D, 0.12D);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         SERIALIZATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the thruster safely
    @Override
    public void writeSafe(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putInt(SHIP_CONTROL_PERSISTENCE_VERSION_TAG, SHIP_CONTROL_PERSISTENCE_VERSION);
        tag.putBoolean("Enabled", enabled);
        tag.putFloat("MinThrottle", minThrottle);
        tag.putFloat("MaxThrottle", maxThrottle);
        tag.putString("ControlMode", controlMode.name());
        tag.putBoolean("PeacefulMode", peacefulMode);
        tag.putBoolean("FilterSound", filterSound);
        tag.putBoolean("FilterParticles", filterParticles);
        tag.putBoolean("FilterDamage", filterDamage);
        tag.putInt("BeamColor", beamColor);
        tag.putFloat("BeamMaxOpacity", beamMaxOpacity);
        tag.putFloat("PlumeColorRatio", plumeColorRatio);
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
    }

    // Write the thruster
    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        if (!legacyShipControlPersistence) {
            tag.putInt(SHIP_CONTROL_PERSISTENCE_VERSION_TAG, SHIP_CONTROL_PERSISTENCE_VERSION);
        }
        if (shipControlEnvelope != null) {
            tag.putBoolean(SHIP_CONTROL_OVERRIDE_ACTIVE_TAG, true);
            tag.putString(SHIP_CONTROL_RESTORE_MODE_TAG, controlMode.name());
            tag.putFloat(SHIP_CONTROL_RESTORE_THROTTLE_TAG, computerThrottle);
            TransientThrusterControlSync.writeShipEnvelope(
                    tag,
                    clientPacket,
                    new TransientThrusterControlSync.ShipEnvelope(
                            shipControlEnvelope.channelId(),
                            shipControlEnvelope.minimum(),
                            shipControlEnvelope.maximum(),
                            shipControlEnvelope.throttle()));
        }
        tag.putFloat("Throttle", throttle);
        tag.putFloat("RedstoneThrottle", redstoneThrottle);
        tag.putFloat("ComputerThrottle", computerThrottle);
        tag.putBoolean("Enabled", enabled);
        tag.putFloat("MinThrottle", minThrottle);
        tag.putFloat("MaxThrottle", maxThrottle);
        tag.putInt("Signal", signalStrength);
        tag.putInt("SolidFuelTicks", solidFuelTicks);
        tag.putBoolean("InfiniteSolidFuel", infiniteSolidFuel);
        tag.putString("SolidFuelTypeId", solidFuelTypeId);
        tag.putDouble("SolidFuelPowerMultiplier", solidFuelPowerMultiplier);
        tag.putString("SolidFuelParticleStyle", solidFuelParticleStyle.name());
        tag.putString("AssemblyComputerCraftId", ccId);
        tag.putString("AssemblyComputerCraftAlias", ccAlias);
        tag.putString("ControlMode", controlMode.name());
        tag.putDouble("FuelDrainAccumulator", fractionalFuelDrain);
        tag.putBoolean("SoulThruster", soulThruster);
        tag.putBoolean("PeacefulMode", peacefulMode);
        tag.putBoolean("FilterSound", filterSound);
        tag.putBoolean("FilterParticles", filterParticles);
        tag.putBoolean("FilterDamage", filterDamage);
        tag.putBoolean("FocusedMode", focusedMode);
        tag.putBoolean("FocusedRejectAirPressure", focusedRejectAirPressure);
        tag.putString("ProcessingUpgradeType", processingUpgradeType.name());
        tag.putInt("ProcessingUpgradeTier", processingUpgradeTier);
        tag.putInt("PropulsionUpgradeTier", propulsionUpgradeTier);
        tag.putInt("BeamColor", beamColor);
        tag.putFloat("BeamMaxOpacity", beamMaxOpacity);
        tag.putFloat("PlumeColorRatio", plumeColorRatio);
        if (ovrDir != null) {
            tag.putDouble("OverrideDirX", ovrDir.x);
            tag.putDouble("OverrideDirY", ovrDir.y);
            tag.putDouble("OverrideDirZ", ovrDir.z);
        }
        if (ovrDirLocal != null) {
            tag.putDouble("OverrideLocalDirX", ovrDirLocal.x);
            tag.putDouble("OverrideLocalDirY", ovrDirLocal.y);
            tag.putDouble("OverrideLocalDirZ", ovrDirLocal.z);
        }
        tag.put("Fuel", fuelTank.writeToNBT(provider, new CompoundTag()));
        tag.put("ThrusterInventory", thrusterInventory.serializeNBT(provider));
        tag.putInt("BeamFE", energyStorage.getEnergyStored());
        tag.putDouble("SolidFuelDrainAccumulator", fractionalSolidFuelDrain);
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
        if (!clientPacket) {
            persistentTickStateDirty = false;
        }
    }

    // Read the thruster
    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        throttle = tag.getFloat("Throttle");
        redstoneThrottle = tag.getFloat("RedstoneThrottle");
        computerThrottle = tag.contains("ComputerThrottle") ? tag.getFloat("ComputerThrottle") : 0.0f;
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        minThrottle = tag.contains("MinThrottle") ? tag.getFloat("MinThrottle") : (tag.contains("MinPropulsionPercent") ? tag.getFloat("MinPropulsionPercent") : 0.0f);
        maxThrottle = tag.contains("MaxThrottle") ? tag.getFloat("MaxThrottle") : (tag.contains("MaxPropulsionPercent") ? tag.getFloat("MaxPropulsionPercent") : 1.0f);
        signalStrength = tag.getInt("Signal");
        solidFuelTicks = tag.getInt("SolidFuelTicks");
        infiniteSolidFuel = tag.getBoolean("InfiniteSolidFuel");
        solidFuelTypeId = tag.getString("SolidFuelTypeId");
        solidFuelPowerMultiplier = tag.contains("SolidFuelPowerMultiplier") ? tag.getDouble("SolidFuelPowerMultiplier") : 1.0D;
        ccId = tag.getString("AssemblyComputerCraftId");
        ccAlias = tag.getString("AssemblyComputerCraftAlias");
        if (tag.contains("SolidFuelParticleStyle")) {
            try {
                solidFuelParticleStyle = ExhaustParticleStyle.valueOf(tag.getString("SolidFuelParticleStyle"));
            } catch (IllegalArgumentException ignored) {
                solidFuelParticleStyle = ExhaustParticleStyle.DEFAULT;
            }
        } else {
            solidFuelParticleStyle = ExhaustParticleStyle.DEFAULT;
        }
        if (tag.contains("ControlMode")) {
            try {
                controlMode = ControlMode.valueOf(tag.getString("ControlMode"));
            } catch (IllegalArgumentException ignored) {
                controlMode = ControlMode.REDSTONE;
            }
        } else {
            controlMode = ControlMode.REDSTONE;
        }
        // -----------------------------------------------------SERVER STATE-----------------------------------------------------
        if (!clientPacket) {
            directSignals.clear();
            shipControlEnvelope = null;
            if (tag.getBoolean(SHIP_CONTROL_OVERRIDE_ACTIVE_TAG)) {
                computerThrottle = tag.contains(SHIP_CONTROL_RESTORE_THROTTLE_TAG)
                        ? tag.getFloat(SHIP_CONTROL_RESTORE_THROTTLE_TAG)
                        : 0.0F;
                if (tag.contains(SHIP_CONTROL_RESTORE_MODE_TAG)) {
                    try {
                        controlMode = ControlMode.valueOf(tag.getString(SHIP_CONTROL_RESTORE_MODE_TAG));
                    } catch (IllegalArgumentException ignored) {
                        controlMode = ControlMode.REDSTONE;
                    }
                } else {
                    controlMode = ControlMode.REDSTONE;
                }
            }
            legacyShipControlPersistence =
                    !tag.contains(SHIP_CONTROL_PERSISTENCE_VERSION_TAG)
                            && controlMode == ControlMode.COMPUTER
                            && computerThrottle > 1.0E-4F;
        } else {
            TransientThrusterControlSync.ShipEnvelope clientEnvelope =
                    TransientThrusterControlSync.readShipEnvelope(tag, true);
            if (tag.getBoolean(SHIP_CONTROL_OVERRIDE_ACTIVE_TAG)
                    && clientEnvelope != null) {
                shipControlEnvelope = new ShipControlEnvelope(
                        clientEnvelope.channelId(),
                        clientEnvelope.minimum(),
                        clientEnvelope.maximum(),
                        clientEnvelope.throttle());
            } else {
                shipControlEnvelope = null;
            }
        }
        // ------------------------------------FUEL AND EFFECT SETTINGS------------------------------------
        fractionalFuelDrain = tag.getDouble("FuelDrainAccumulator");
        soulThruster = tag.getBoolean("SoulThruster");
        peacefulMode = tag.getBoolean("PeacefulMode");
        filterSound = !tag.contains("FilterSound") || tag.getBoolean("FilterSound");
        filterParticles = !tag.contains("FilterParticles") || tag.getBoolean("FilterParticles");
        filterDamage = !tag.contains("FilterDamage") || tag.getBoolean("FilterDamage");
        focusedMode = tag.getBoolean("FocusedMode");
        focusedRejectAirPressure = !tag.contains("FocusedRejectAirPressure")
                || tag.getBoolean("FocusedRejectAirPressure");
        if (tag.contains("ProcessingUpgradeType")) {
            try {
                processingUpgradeType = ProcessingUpgradeType.valueOf(tag.getString("ProcessingUpgradeType"));
            } catch (IllegalArgumentException ignored) {
                processingUpgradeType = ProcessingUpgradeType.NONE;
            }
        } else {
            processingUpgradeType = ProcessingUpgradeType.NONE;
        }
        processingUpgradeTier = Mth.clamp(tag.contains("ProcessingUpgradeTier") ? tag.getInt("ProcessingUpgradeTier") : 0, 0, 4);
        propulsionUpgradeTier = Mth.clamp(tag.contains("PropulsionUpgradeTier") ? tag.getInt("PropulsionUpgradeTier") : 0, 0, 4);
        beamColor = tag.contains("BeamColor") ? tag.getInt("BeamColor") & 0xFFFFFF : DEFAULT_BEAM_COLOR;
        beamMaxOpacity = tag.contains("BeamMaxOpacity") ? Mth.clamp(tag.getFloat("BeamMaxOpacity"), 0.0f, 1.0f) : 0.34f;
        plumeColorRatio = tag.contains("PlumeColorRatio") ? Mth.clamp(tag.getFloat("PlumeColorRatio"), 0.0f, 1.0f) : DEFAULT_PLUME_COLOR_RATIO;
        ovrDir = tag.contains("OverrideDirX")
            ? new Vec3(tag.getDouble("OverrideDirX"), tag.getDouble("OverrideDirY"), tag.getDouble("OverrideDirZ")).normalize()
            : null;
        ovrDirLocal = tag.contains("OverrideLocalDirX")
            ? new Vec3(tag.getDouble("OverrideLocalDirX"), tag.getDouble("OverrideLocalDirY"), tag.getDouble("OverrideLocalDirZ")).normalize()
            : null;
        cachedOverrideForceDirection = ovrDirLocal == null
                ? null
                : new Vector3d(ovrDirLocal.x, ovrDirLocal.y, ovrDirLocal.z);
        fuelTank.readFromNBT(provider, tag.getCompound("Fuel"));
        if (tag.contains("ThrusterInventory")) {
            ct$readThrusterInventory(provider, tag.getCompound("ThrusterInventory"));
        } else {

            if (focusedMode && CTItems.THRUSTER_LENSE != null && thrusterInventory.getStackInSlot(SLOT_LENS).isEmpty()) {
                thrusterInventory.setStackInSlot(SLOT_LENS, new net.minecraft.world.item.ItemStack(CTItems.THRUSTER_LENSE.get()));
            }
            if (processingUpgradeType != ProcessingUpgradeType.NONE && processingUpgradeTier > 0
                    && thrusterInventory.getStackInSlot(SLOT_UPGRADE).isEmpty()) {
                thrusterInventory.setStackInSlot(SLOT_UPGRADE, processingUpgradeStack(processingUpgradeType, processingUpgradeTier));
            }
        }
        if (tag.contains("BeamFE")) {
            ((BeamEnergyStorage) energyStorage).setStored(tag.getInt("BeamFE"));
        }
        if (focusedMode) {
            clearFuelReserves();
        }
        fractionalSolidFuelDrain = tag.getDouble("SolidFuelDrainAccumulator");
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;
        beamFueledThisTick = false;
        syncModesFromInventory();
        enforceBeamSafeBuffer();
        refreshThrottle();
    }

    // Read the thruster inventory
    private void ct$readThrusterInventory(net.minecraft.core.HolderLookup.Provider provider, CompoundTag inventoryTag) {
        ct$clearThrusterInventory();
        int serializedSize = inventoryTag.contains("Size", Tag.TAG_INT) ? inventoryTag.getInt("Size") : thrusterInventory.getSlots();
        if (serializedSize >= thrusterInventory.getSlots()) {
            thrusterInventory.deserializeNBT(provider, inventoryTag);
            return;
        }

        ListTag itemsTag = inventoryTag.getList("Items", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < itemsTag.size(); idx++) {
            CompoundTag itemTag = itemsTag.getCompound(idx);
            ItemStack stack = ItemStack.parseOptional(provider, itemTag);
            if (stack.isEmpty()) {
                continue;
            }
            int sourceSlot = itemTag.getInt("Slot");
            int targetSlot = ct$resolveLegacyThrusterSlot(sourceSlot, stack);
            if (targetSlot < 0 || !thrusterInventory.getStackInSlot(targetSlot).isEmpty()) {
                continue;
            }
            thrusterInventory.setStackInSlot(targetSlot, stack);
        }
    }

    // Clear the thruster inventory
    private void ct$clearThrusterInventory() {
        for (int slot = 0; slot < thrusterInventory.getSlots(); slot++) {
            thrusterInventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // Resolve the legacy thruster slot
    private int ct$resolveLegacyThrusterSlot(int sourceSlot, ItemStack stack) {
        if (sourceSlot == SLOT_UPGRADE || thrusterInventory.isItemValid(SLOT_UPGRADE, stack)) {
            return SLOT_UPGRADE;
        }
        if (canUseAsSolidFuel(stack)) {
            return SLOT_SOLID_FUEL;
        }
        if (isThrusterLens(stack) || thrusterInventory.isItemValid(SLOT_LENS, stack)) {
            return SLOT_LENS;
        }
        if (ThrusterBlock.isCreateFilter(stack) || thrusterInventory.isItemValid(SLOT_LIST, stack)) {
            return SLOT_LIST;
        }
        return switch (sourceSlot) {
            case SLOT_LIST -> SLOT_LIST;
            case SLOT_LENS -> SLOT_LENS;
            case SLOT_SOLID_FUEL -> SLOT_SOLID_FUEL;
            default -> -1;
        };
    }

    // Create the thruster update tag
    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        return writeClient(new CompoundTag(), provider);
    }

    // Create the thruster update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            TOOLTIPS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.thruster")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.enabled"),
                CTTooltipHelper.onOff(enabled)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.throttle_input"),
                CTTooltipHelper.value(CTTooltipHelper.percent(getThrottle()), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.thrust_output"),
                CTTooltipHelper.value(CTTooltipHelper.percent(getAppliedThrottle()), ChatFormatting.YELLOW)));
        if (!focusedMode) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.fuel_stored"),
                    CTTooltipHelper.value(getFuelAmount() + " / " + getFuelCapacity(), ChatFormatting.BLUE)));
            tooltip.add(CTTooltipHelper.line(Component.literal("Fuel Consumption"),
                    CTTooltipHelper.value(String.format(java.util.Locale.ROOT, "%.3f mB/t",
                            getFuelConsumptionMbPerTick()), ChatFormatting.GOLD)));
        } else {
            int requiredFePerTick = getRequiredBeamFePerTick();
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.fe_stored"),
                    CTTooltipHelper.value(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored(),
                            ChatFormatting.BLUE)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.beam_requirement"),
                    CTTooltipHelper.value(requiredFePerTick + " FE/t", ChatFormatting.GOLD)));
        }
        if (isActive()) {
            MutableComponent thrustComponent = CTPropulsionTelemetry.thrustComponent(getRealThrust()).withStyle(ChatFormatting.AQUA);
            tooltip.add(dev.eriksonn.aeronautics.data.AeroLang.translate("propeller.thrust", thrustComponent)
                    .style(ChatFormatting.GRAY)
                    .component());
            MutableComponent liftComponent = CTPropulsionTelemetry.liftComponent(getLiftCapacity()).withStyle(ChatFormatting.AQUA);
            tooltip.add(dev.eriksonn.aeronautics.data.AeroLang.translate("propeller.can_lift", liftComponent)
                    .style(ChatFormatting.GRAY)
                    .component());
        }
        if (showDetails) {
            if (!focusedMode) {
                FluidStack fuel = fuelTank.getFluid();
                if (!fuel.isEmpty()) {
                    tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.fuel_type"),
                            CTTooltipHelper.value(getFuelDisplayName(fuel), ChatFormatting.GRAY)));
                }
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.burn_time"),
                        CTTooltipHelper.value(formatDuration(getEstimatedBurnSeconds()), ChatFormatting.LIGHT_PURPLE)));
            } else {
                int baseFePerTick = getBaseBeamFePerTick();
                double tierMultiplier = getProcessingFuelDrainMultiplier();
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.beam_calc"),
                        CTTooltipHelper.value(baseFePerTick + " x " + String.format("%.2f", tierMultiplier),
                                ChatFormatting.GRAY)));
            }
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.control_mode"),
                    CTTooltipHelper.value(switch (controlMode) {
                        case AUTO -> "Auto";
                        case REDSTONE -> "Redstone";
                        case COMPUTER -> "Computer";
                    }, ChatFormatting.GREEN)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.throttle_range"),
                    CTTooltipHelper.value(CTTooltipHelper.percent(minThrottle) + " to "
                            + CTTooltipHelper.percent(maxThrottle), ChatFormatting.YELLOW)));
            if (hasProcessingUpgrade()) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.processing_upgrade"),
                        CTTooltipHelper.value(processingUpgradeType.name() + " T" + processingUpgradeTier,
                                ChatFormatting.GOLD)));
            }
                if (propulsionUpgradeTier > 0) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.propulsion_upgrade"),
                    CTTooltipHelper.value("T" + propulsionUpgradeTier + " (x" + formatUpgradeMultiplier(getActivePropulsionUpgradeMultiplier()) + ")",
                        ChatFormatting.LIGHT_PURPLE)));
                }
            if (ModList.get().isLoaded("computercraft") && !ccAlias.isEmpty()) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.cc_alias"),
                        CTTooltipHelper.value(ccAlias, ChatFormatting.LIGHT_PURPLE)));
            }
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.thruster.fuel_mode"),
                    CTTooltipHelper.value(soulThruster ? "Soul" : "Standard",
                            soulThruster ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD)));
        }
        return true;
    }

    // Get the base beam FE per tick
    private int getBaseBeamFePerTick() {
        float appliedThrottle = getAppliedThrottle();
        if (!enabled || appliedThrottle <= 0.0f) {
            return 0;
        }
        double baseFePerTick = Math.max(1.0D, CTConfigs.COMMON.focusedModeFePerTick.get());
        return (int) Math.max(1.0D, baseFePerTick * appliedThrottle);
    }

    // Get the required beam FE per tick
    private int getRequiredBeamFePerTick() {
        return (int) Math.max(1.0D, getBaseBeamFePerTick() * getProcessingFuelDrainMultiplier());
    }

    // Check if this has required beam energy
    private boolean hasRequiredBeamEnergy() {
        int required = getRequiredBeamFePerTick();
        return required <= 0 || energyStorage.extractEnergy(required, true) >= required;
    }

    // Clear the fuel reserves
    private void clearFuelReserves() {
        fuelTank.drain(fuelTank.getFluidAmount(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        solidFuelTicks = 0;
        fractionalFuelDrain = 0.0D;
        fractionalSolidFuelDrain = 0.0D;
        clearSolidFuelProfile();
    }

    // Enforce the beam safe buffer
    private void enforceBeamSafeBuffer() {
        if (!focusedMode) {
            ((BeamEnergyStorage) energyStorage).setStored(0);
            return;
        }

        int safeCapacity = Math.max(0, energyStorage.getMaxEnergyStored());
        if (energyStorage.getEnergyStored() > safeCapacity) {
            ((BeamEnergyStorage) energyStorage).setStored(safeCapacity);
        }
    }

    // Pull the focused energy
    private void pullFocusedEnergy() {
        if (!focusedMode || level == null) {
            return;
        }
        int remaining = Math.min(FOCUSED_AUTO_PULL_PER_TICK,
                Math.max(0, energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored()));
        for (Direction side : Direction.values()) {
            if (remaining <= 0) {
                break;
            }
            IEnergyStorage src = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(side), side.getOpposite());
            if (src == null || !src.canExtract()) {
                continue;
            }
            int available = src.extractEnergy(remaining, true);
            int accepted = energyStorage.receiveEnergy(available, true);
            if (accepted <= 0) {
                continue;
            }
            int extracted = src.extractEnergy(accepted, false);
            int received = energyStorage.receiveEnergy(extracted, false);
            if (received < extracted && src.canReceive()) {
                src.receiveEnergy(extracted - received, false);
            }
            remaining -= received;
        }
    }

    // Sync the modes from inventory
    private void syncModesFromInventory() {
        boolean wasFocusedMode = focusedMode;
        ItemStack upgradeStack = thrusterInventory.getStackInSlot(SLOT_UPGRADE);
        ProcessingUpgradeType nextType = ProcessingUpgradeType.NONE;
        int nextTier = 0;
        int nextPropulsionTier = 0;
        if (upgradeStack.getItem() instanceof ProcessingUpgradeItem upgradeItem) {
            ProcessingUpgradeType candidateType = upgradeItem.getType();
            int candidateTier = upgradeItem.getTier();
            if (canApplyProcessingUpgrade(candidateType, candidateTier)) {
                nextType = candidateType;
                nextTier = candidateTier;
            }
        } else if (upgradeStack.getItem() instanceof PropulsionUpgradeItem propulsionUpgradeItem) {
            nextPropulsionTier = Mth.clamp(propulsionUpgradeItem.getTier(), 1, 4);
        }
        processingUpgradeType = nextType;
        processingUpgradeTier = nextTier;
        propulsionUpgradeTier = nextPropulsionTier;

        ItemStack lensStack   = thrusterInventory.getStackInSlot(SLOT_LENS);
        ItemStack filterStack = thrusterInventory.getStackInSlot(SLOT_LIST);
        focusedMode = isThrusterLens(lensStack);
        peacefulMode = ThrusterBlock.isCreateFilter(filterStack);
        soulThruster = processingUpgradeType == ProcessingUpgradeType.HAUNTING && processingUpgradeTier > 0;
        if (focusedMode && !wasFocusedMode) {
            clearFuelReserves();
        }
        if (focusedMode != wasFocusedMode) {
            syncFocusedBlockState();
        }
    }

    // Sync the focused block state
    private void syncFocusedBlockState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(ThrusterBlock.FOCUSED)) {
            return;
        }
        if (state.getValue(ThrusterBlock.FOCUSED) == focusedMode) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(ThrusterBlock.FOCUSED, focusedMode), 3);
    }

    // Refresh the capabilities
    private void refreshCapabilities() {
        if (level == null || level.isClientSide) {
            return;
        }

        level.invalidateCapabilities(worldPosition);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // Refresh the throttle
    private void refreshThrottle() {

        float requestedThrottle = switch (controlMode) {
            case AUTO -> computerThrottle > 0.0f ? computerThrottle : redstoneThrottle;
            case REDSTONE -> redstoneThrottle;
            case COMPUTER -> computerThrottle;
        };
        this.throttle = Mth.clamp(requestedThrottle, 0.0f, 1.0f);
    }

    // Get the applied throttle
    public float getAppliedThrottle() {
        if (shipControlEnvelope != null) {
            float requested = Mth.clamp(
                    shipControlEnvelope.throttle() + stabilizerThrottleOffset, 0.0f, 1.0f);
            return applyThrottleRange(requested,
                    shipControlEnvelope.minimum(), shipControlEnvelope.maximum());
        }
        return applyThrottleRange(throttle + stabilizerThrottleOffset);
    }

    // Format the upgrade multiplier
    private static String formatUpgradeMultiplier(double multiplier) {
        if (Math.abs(multiplier - Math.rint(multiplier)) < 1.0E-6D) {
            return Integer.toString((int) Math.rint(multiplier));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", multiplier);
    }

    // Set the stabilizer throttle offset
    public void setStabilizerThrottleOffset(float offset) {

        stabilizerThrottleOffset = Mth.clamp(offset, -1.0f, 1.0f);
    }

    // Apply the throttle range
    private float applyThrottleRange(float requestedThrottle) {
        return applyThrottleRange(requestedThrottle, minThrottle, maxThrottle);
    }

    // Apply the throttle range
    private static float applyThrottleRange(float requestedThrottle, float requestedMinimum, float requestedMaximum) {
        float normalizedThrottle = Mth.clamp(requestedThrottle, 0.0f, 1.0f);
        if (normalizedThrottle <= 0.0f) {
            return 0.0f;
        }
        float min = Mth.clamp(requestedMinimum, 0.0f, 1.0f);
        float max = Mth.clamp(Math.max(min, requestedMaximum), 0.0f, 1.0f);
        return Mth.lerp(normalizedThrottle, min, max);
    }

    // Store the ship control envelope
    private record ShipControlEnvelope(String channelId, float minimum, float maximum, float throttle) {
    }

    // Handle the thruster air current
    private static class ThrusterAirCurrent extends AirCurrent {
        // Thruster
        private final ThrusterBlockEntity thruster;

        // Initialize the thruster air current
        private ThrusterAirCurrent(ThrusterBlockEntity thruster) {
            super(thruster);
            this.thruster = thruster;
        }

        // Update the affected entities
        @Override
        protected void tickAffectedEntities(Level world) {
            if (world == null || world.isClientSide) {
                super.tickAffectedEntities(world);
                return;
            }

            WorldExhaustGeometry geometry = thruster.getWorldExhaustGeometry(0.75D);
            java.util.Iterator<Entity> iterator = this.caughtEntities.iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                WorldExhaustHit hit = thruster.getWorldExhaustHit(entity, geometry);
                if (!entity.isAlive() || hit == null || ThrusterBlockEntity.isPlayerCreativeFlying(entity)) {
                    if (entity instanceof ItemEntity itemEntity) {
                        thruster.clearProcessingProgress(itemEntity);
                    }
                    iterator.remove();
                    continue;
                }

                thruster.applyExhaustMotion(entity, hit, geometry);

                FanProcessingType processingType = this.getTypeAt((float) hit.axialDistance());
                if (entity instanceof ItemEntity itemEntity) {
                    if (processingType == null) {
                        thruster.clearProcessingProgress(itemEntity);
                    } else {
                        thruster.applyBulkProcessing(itemEntity, processingType);
                    }
                    continue;
                }

                if (thruster.isDamageFiltered()) {
                    continue;
                }

                thruster.applyEntityEffect(entity, world);
            }
        }

        // Find the entities
        @Override
        public void findEntities() {
            Level world = this.source.getAirCurrentWorld();
            if (world == null || world.isClientSide) {
                super.findEntities();
                return;
            }
            this.caughtEntities.clear();
            this.caughtEntities.addAll(thruster.collectAffectedEntities());
        }

        // Update the affected handlers
        @Override
        public void tickAffectedHandlers() {
            Level world = this.source.getAirCurrentWorld();
            if (world == null) {
                return;
            }
            if (world.isClientSide) {
                super.tickAffectedHandlers();
                return;
            }

            double speedMultiplier = thruster.getBulkProcessingSpeedMultiplier();
            if (speedMultiplier <= 0.0D) {
                return;
            }

            int passes = Mth.floor((float) speedMultiplier);
            double fractionalPass = speedMultiplier - passes;
            if (fractionalPass > 0.0D && world.random.nextDouble() < fractionalPass) {
                passes++;
            }

            for (int i = 0; i < passes; i++) {
                super.tickAffectedHandlers();
            }
        }

        // Find the affected handlers
        @Override
        public void findAffectedHandlers() {
            Level world = this.source.getAirCurrentWorld();
            this.affectedItemHandlers.clear();
            if (world == null) {
                return;
            }

            if (thruster.getBulkProcessingSpeedMultiplier() <= 0.0D) {
                return;
            }

            WorldExhaustGeometry geometry = thruster.getWorldExhaustGeometry(0.0D);
            Level queryLevel = thruster.getWorldSpaceQueryLevel();
            Set<String> seen = new HashSet<>();

            collectWorldSpaceHandlers(queryLevel, null, geometry, seen);
            if (queryLevel != world) {
                collectWorldSpaceHandlers(world, null, geometry, seen);
            }

            AABB searchBounds = geometry.bounds().inflate(THRUSTER_HANDLER_QUERY_RADIUS + 2.0D);
            for (Object subLevel : thruster.getExhaustSubLevels(searchBounds)) {
                Level subLevelLevel = ThrusterBlockEntity.getSubLevelLevel(subLevel);
                if (subLevelLevel != null) {
                    collectWorldSpaceHandlers(subLevelLevel, subLevel, geometry, seen);
                }
            }
        }

        // Collect the world space handlers
        private void collectWorldSpaceHandlers(Level queryLevel, @Nullable Object subLevel,
                WorldExhaustGeometry geometry, Set<String> seen) {
            if (queryLevel == null || geometry == null) {
                return;
            }

            double sampleStep = 0.5D;
            int sampleCount = Math.max(1, Mth.ceil(geometry.maxDistance() / sampleStep));
            int blockSearchRadius = Mth.ceil(THRUSTER_HANDLER_QUERY_RADIUS + 1.0D);

            for (int sample = 1; sample <= sampleCount; sample++) {
                double distance = Math.min(geometry.maxDistance(), sample * sampleStep);
                Vec3 worldSample = geometry.origin().add(geometry.direction().scale(distance));
                Vec3 localSample = subLevel == null
                        ? worldSample
                        : SimulatedHelper.toContainingLocalPosition(subLevel, worldSample);
                if (localSample == null) {
                    continue;
                }

                BlockPos center = BlockPos.containing(localSample);
                for (int xOffset = -blockSearchRadius; xOffset <= blockSearchRadius; xOffset++) {
                    for (int yOffset = -blockSearchRadius; yOffset <= blockSearchRadius; yOffset++) {
                        for (int zOffset = -blockSearchRadius; zOffset <= blockSearchRadius; zOffset++) {
                            collectWorldSpaceHandlerAt(queryLevel, subLevel,
                                    center.offset(xOffset, yOffset, zOffset), geometry, seen);
                        }
                    }
                }
            }
        }

        // Collect the world space handler
        private void collectWorldSpaceHandlerAt(Level queryLevel, @Nullable Object subLevel, BlockPos pos,
                WorldExhaustGeometry geometry, Set<String> seen) {
            String key = handlerKey(queryLevel, subLevel, pos);
            if (!seen.add(key)) {
                return;
            }

            Vec3 worldCenter = blockCenterWorld(queryLevel, subLevel, pos);
            WorldExhaustHit hit = thruster.getWorldExhaustHit(worldCenter, THRUSTER_HANDLER_QUERY_RADIUS + 0.125D,
                    0.0D, geometry);
            if (hit == null) {
                return;
            }

            TransportedItemStackHandlerBehaviour behaviour = getTransportedStackHandler(queryLevel, subLevel, pos);
            if (behaviour == null) {
                return;
            }

            FanProcessingType type = this.getTypeAt((float) hit.axialDistance());
            if (type == null) {
                type = FanProcessingType.getAt(queryLevel, pos);
            }
            if (type != null) {
                this.affectedItemHandlers.add(Pair.of(behaviour, type));
            }
        }

        // Get the transported stack handler
        private static TransportedItemStackHandlerBehaviour getTransportedStackHandler(Level queryLevel,
                @Nullable Object subLevel, BlockPos pos) {
            if (subLevel != null) {
                return BlockEntityBehaviour.get(SubLevelBlockEntityCollector.getBlockEntity(subLevel, pos),
                        TransportedItemStackHandlerBehaviour.TYPE);
            }
            return BlockEntityBehaviour.get(queryLevel, pos, TransportedItemStackHandlerBehaviour.TYPE);
        }

        // Get the block center world
        private Vec3 blockCenterWorld(Level queryLevel, @Nullable Object subLevel, BlockPos pos) {
            Vec3 localCenter = Vec3.atCenterOf(pos);
            Vec3 worldCenter = subLevel == null
                    ? localCenter
                    : SimulatedHelper.toContainingWorldPosition(subLevel, localCenter);
            if (worldCenter == null) {
                worldCenter = localCenter;
            }
            Level projectionLevel = thruster.getWorldSpaceQueryLevel();
            return SimulatedHelper.projectOutOfSubLevels(projectionLevel == null ? queryLevel : projectionLevel,
                    worldCenter);
        }

        // Get the handler key
        private static String handlerKey(Level queryLevel, @Nullable Object subLevel, BlockPos pos) {
            String levelKey = queryLevel == null ? "unknown" : queryLevel.dimension().location().toString();
            String subLevelKey = String.valueOf(SimulatedHelper.getSubLevelId(subLevel));
            return levelKey + ":" + subLevelKey + ":" + pos.asLong();
        }

        // Get the type
        @Override
        public @Nullable FanProcessingType getTypeAt(float offset) {
            FanProcessingType forcedType = thruster.getForcedProcessingType();
            if (forcedType != null && offset >= 0.0f && offset <= maxDistance) {
                return forcedType;
            }
            return super.getTypeAt(offset);
        }
    }

    // Handle the thruster config screen behaviour
    @SuppressWarnings("unused")
    private static class ThrusterConfigScreenBehaviour extends ScrollOptionBehaviour<ThrusterConfigControlOption> {
        // Initialize the thruster config screen behaviour
        public ThrusterConfigScreenBehaviour(ThrusterBlockEntity be, ValueBoxTransform slot) {
            super(ThrusterConfigControlOption.class, CONFIG_LABEL, be, slot);
            setValue(0);
        }

        // Check if this accepts value settings
        @Override
        public boolean acceptsValueSettings() {
            return false;
        }

        // Handle the short interact event
        @Override
        public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
            if (getWorld() == null || getWorld().isClientSide) {
                return;
            }

            ThrusterBlockEntity thruster = (ThrusterBlockEntity) blockEntity;
            player.openMenu(thruster, thruster::sendToMenu);
        }
    }

    // Define the thruster config control option values
    private enum ThrusterConfigControlOption implements INamedIconOptions {
        CONFIGURE;

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return AllIcons.I_CONFIG_OPEN;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.thruster.config_screen.title";
        }
    }

    // Handle the thruster config value box
    @SuppressWarnings("unused")
    private static class ThrusterConfigValueBox extends CenteredSideValueBoxTransform {
        // Initialize the thruster config value box
        public ThrusterConfigValueBox() {

            super((state, side) -> side.getAxis() != state.getValue(CTDirectionalBlock.FACING).getAxis());
        }

        // Get the south location
        @Override
        protected Vec3 getSouthLocation() {

            return VecHelper.voxelSpace(8.0, 8.0, 16.5);
        }

        // Get the local offset
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(CTDirectionalBlock.FACING);
            Direction localUp = switch (facing) {
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                default -> Direction.UP;
            };

            return super.getLocalOffset(level, pos, state)
                    .add(Vec3.atLowerCornerOf(localUp.getNormal()).scale(-0.25D));
        }

        // Rotate the thruster config value box
        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {

            if (!getSide().getAxis().isHorizontal()) {
                Direction facing = state.getValue(CTDirectionalBlock.FACING);
                ms.mulPose(Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing) + 180.0f));
            }
            super.rotate(level, pos, state, ms);
        }

        // Get the scale
        @Override
        public float getScale() {
            return 0.35f;
        }
    }

    // Handle the beam energy storage
    private static final class BeamEnergyStorage extends EnergyStorage {
        private static final int DEFAULT_CAPACITY = 100000;

        // Initialize the beam energy storage
        private BeamEnergyStorage() {
            super(resolveCapacity());
        }

        // Resolve the capacity
        private static int resolveCapacity() {
            try {
                return Math.max(1000, CTConfigs.COMMON.focusedModeFeCapacity.get());
            } catch (Exception ignored) {
                return DEFAULT_CAPACITY;
            }
        }

        // Set the stored
        public void setStored(int amount) {
            this.energy = Math.max(0, Math.min(capacity, amount));
        }

        // Receive the energy
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = super.receiveEnergy(maxReceive, simulate);
            return Math.max(0, accepted);
        }
    }

    // Handle the focused mode energy input view
    private final class FocusedModeEnergyInputView implements IEnergyStorage {
        // Receive the energy
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return energyStorage.receiveEnergy(maxReceive, simulate);
        }

        // Extract the energy
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        // Get the energy stored
        @Override
        public int getEnergyStored() {
            return energyStorage.getEnergyStored();
        }

        // Get the max energy stored
        @Override
        public int getMaxEnergyStored() {
            return energyStorage.getMaxEnergyStored();
        }

        // Check if this can extract
        @Override
        public boolean canExtract() {
            return false;
        }

        // Check if this can receive
        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
