package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSource;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.rieno.gadgetsandgizmos.compat.controller.OrientationAdapters;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

// Convert a gyroscope reading into orientation data for adjacent or linked controls
public class GyroscopeLinkBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, MenuProvider, BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current gyro pos
    private BlockPos gyroPos;
    // Current gyro dimension
    private String gyroDimension;
    // Current gyro sub-level id
    private UUID gyroSubLevelId;
    // Current gyro local pos
    private BlockPos gyroLocalPos;
    // Current tracking mode
    private TrackingMode trackingMode = TrackingMode.LIVE;
    // Tracked cardinal frequency bindings
    private final EnumMap<Direction, FrequencyBinding> cardinalFrequencyBindings = createDefaultFreqBindings();
    // Tracked cardinal output configs
    private final EnumMap<Direction, CardinalOutputConfig> cardinalOutputConfigs = createDefaultCardinalConfigs();
    // Tracked cardinal transmitters
    private final EnumMap<Direction, CardinalTransmitter> cardinalTransmitters = new EnumMap<>(Direction.class);
    // Registered cardinal transmitters
    private final EnumSet<Direction> registeredCardinalTransmitters = EnumSet.noneOf(Direction.class);
    // Current configure behaviour
    private ScrollOptionBehaviour<ConfigureControlOption> configureBehaviour;
    // Live angles in radians
    private double[] liveAnglesRadians;
    // Current live direction
    private Vec3 liveDirection;
    // Current latest payload
    private OrientationPayload latestPayload;
    // Current live target sensor
    private WeakReference<BlockEntity> liveTargetSensor = new WeakReference<>(null);
    // Current display relay target
    private WeakReference<AccDisplayBlockEntity> displayRelayTarget = new WeakReference<>(null);
    // Current relayed display adapter
    private @Nullable UniversalDisplayAdapterBlockEntity relayedDisplayAdapter;
    // Current relayed display revision
    private long relayedDisplayRevision = Long.MIN_VALUE;
    // Current relayed display width
    private int relayedDisplayWidth;
    // Current relayed display height
    private int relayedDisplayHeight;
    // Current relayed requested display mode
    private String relayedRequestedDisplayMode = "";
    // Tracks whether source range prefill is pending
    private boolean sourceRangePrefillPending;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyroscope link
    public GyroscopeLinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.GYROSCOPE_LINK.get(), pos, blockState);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            cardinalTransmitters.put(dir, new CardinalTransmitter(dir));
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
        ValueBoxTransform configSlot = new CenteredSideValueBoxTransform((state, dir) -> dir == Direction.UP);
        configureBehaviour = new ScrollOptionBehaviour<>(ConfigureControlOption.class,
                Component.translatable("createthrusters.gyroscope_link.config.title"), this, configSlot) {
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
                GyroscopeLinkBlockEntity link = (GyroscopeLinkBlockEntity) blockEntity;
                player.openMenu(link, link::sendToMenu);
            }
        };
        configureBehaviour.setValue(0);
        behaviours.add(configureBehaviour);
    }

    // Initialize the gyroscope link
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            applyPendingSourceRangePrefill();
            updateCardinalTransmitters(true);
            refreshCardinalTransmitterRegistrations();
            refreshDisplayRelayTarget();
        }
    }

    // Remove the gyroscope link
    @Override
    public void remove() {
        clearDisplayRelay();
        unregisterCardinalTransmitters();
        super.remove();
    }

    // Invalidate the gyroscope link
    @Override
    public void invalidate() {
        unregisterCardinalTransmitters();
        super.invalidate();
    }

    // Set the gyro target
    public void setGyroTarget(BlockPos pos, ResourceLocation dimension) {
        clearDisplayRelay();
        this.gyroPos = pos;
        this.gyroDimension = (pos != null && dimension != null) ? dimension.toString() : null;
        this.sourceRangePrefillPending = isLinked();
        captureStableTargetReference();
        applyPendingSourceRangePrefill();
        setChanged();
        if (level != null) {
            sendData();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, GyroscopeLinkBlockEntity be) {
        if (level.isClientSide || AccDisplayControllerRegistry.isStopping(level)) {
            return;
        }
        be.tick();
        be.tick(level, pos);
        if (level.getGameTime() % 10 == 0) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the gyroscope link
    private void tick(Level level, BlockPos selfPos) {
        applyPendingSourceRangePrefill();
        BlockEntity linkedTarget = resolveLiveTargetSensor();
        if (linkedTarget instanceof UniversalDisplayAdapterBlockEntity adapter) {
            relayDisplayFrame(adapter);
            liveAnglesRadians = null;
            liveDirection = null;
            latestPayload = null;
            dispatchPayloadToAdjacentTargets(level, selfPos, null, null);
            updateCardinalTransmitters(false);
            return;
        }
        clearDisplayRelayIfNeeded();
        AeroworksControllerCompat.DirectionalAxes aeroworksAxes =
                AeroworksControllerCompat.directionalAxes(linkedTarget);
        liveAnglesRadians = getTrackingMode() == TrackingMode.LIVE
                ? resolveLiveLinkedAnglesRad(linkedTarget, aeroworksAxes)
                : resolveStaticLinkedAnglesRad(linkedTarget, aeroworksAxes);
        liveDirection = liveAnglesRadians == null
                ? null
                : directionFromAngles(liveAnglesRadians[0], liveAnglesRadians[1]);
        latestPayload = buildPayload(level.getGameTime());
        SourceSignalAxes sourceAxes = resolveCurrentSourceSignalAxes(
                latestPayload, linkedTarget, aeroworksAxes);
        dispatchPayloadToAdjacentTargets(level, selfPos, latestPayload, sourceAxes);
        updateCardinalTransmitters(false, sourceAxes);

    }

    // Build the payload
    private @Nullable OrientationPayload buildPayload(long gameTime) {
        if (liveAnglesRadians == null || liveDirection == null) {
            return null;
        }
        return new OrientationPayload(
                liveAnglesRadians[0],
                liveAnglesRadians[1],
                liveDirection,
                getTrackingMode() == TrackingMode.LIVE,
                gameTime);
    }

    // Dispatch the payload to adjacent targets
    private void dispatchPayloadToAdjacentTargets(Level level, BlockPos selfPos, @Nullable OrientationPayload payload,
                                                  @Nullable SourceSignalAxes sourceAxes) {
        for (Direction dir : Direction.values()) {
            BlockEntity candidate = level.getBlockEntity(selfPos.relative(dir));
            if (candidate instanceof ThrusterBearingBlockEntity bearing) {
                dispatchPayloadToThrusterBearing(bearing, sourceAxes);
                continue;
            }
            if (candidate instanceof AileronBearingBlockEntity aileronBearing) {
                dispatchPayloadToAileronBearing(aileronBearing, sourceAxes);
                continue;
            }
            if (payload != null && candidate instanceof OrientationTarget target && target.canAcceptOrientationPayload(payload)) {
                if (target instanceof BiDirectionalGearboxBlockEntity gearbox) {
                    gearbox.applyGyroscopeLinkPayload(this, payload);
                } else {
                    target.applyOrientationPayload(payload);
                }
            }
        }
    }

    // Dispatch the payload to thruster bearing
    private void dispatchPayloadToThrusterBearing(ThrusterBearingBlockEntity bearing,
                                                  @Nullable SourceSignalAxes sourceAxes) {
        String channelId = bearingControlChannelId();
        if (sourceAxes == null) {
            bearing.clearGyroscopeLinkControlSignal(channelId);
            return;
        }
        BearingControlSignal signal = getBearingControlSignal(sourceAxes);
        bearing.applyGyroscopeLinkControlSignal(channelId, signal.angleDegrees(), (float) signal.outputFraction());
    }

    // Dispatch the payload to aileron bearing
    private void dispatchPayloadToAileronBearing(AileronBearingBlockEntity bearing,
                                                 @Nullable SourceSignalAxes sourceAxes) {
        String channelId = bearingControlChannelId();
        if (sourceAxes == null) {
            bearing.clearGyroscopeLinkControlSignal(channelId);
            return;
        }
        AileronControlSignal signal = getAileronControlSignal(sourceAxes);
        bearing.applyGyroscopeLinkControlSignal(channelId, signal.primaryAngleDegrees(), signal.secondaryAngleDegrees());
    }

    // Create the gyroscope link update tag
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        writeTrackingData(tag, provider);
        return tag;
    }

    // Create the gyroscope link update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Write the gyroscope link
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writeTrackingData(tag, provider);
        remapSchematicTrackingTag(tag, false);
    }

    // Write the gyroscope link safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        writeTrackingData(tag, provider);
        remapSchematicTrackingTag(tag, false);
    }

    // Read the gyroscope link
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        remapSchematicTrackingTag(tag, true);
        super.read(tag, provider, clientPacket);
        if (tag.contains("GyroX")) {
            gyroPos = new BlockPos(tag.getInt("GyroX"), tag.getInt("GyroY"), tag.getInt("GyroZ"));
        } else {
            gyroPos = null;
        }
        if (tag.contains("GyroDim")) {
            gyroDimension = tag.getString("GyroDim");
        } else {
            gyroDimension = null;
        }
        if (tag.contains("TargetOffset")) {
            BlockPos targetOffset = NBTHelper.readBlockPos(tag, "TargetOffset");
            gyroPos = worldPosition.offset(targetOffset);
            gyroDimension = tag.contains("TargetDimension") ? tag.getString("TargetDimension") : null;
        }
        gyroSubLevelId = tag.hasUUID("GyroSubLevelId") ? tag.getUUID("GyroSubLevelId") : null;
        gyroLocalPos = tag.contains("GyroLocalX")
                ? new BlockPos(tag.getInt("GyroLocalX"), tag.getInt("GyroLocalY"), tag.getInt("GyroLocalZ"))
                : null;
        trackingMode = tag.contains("TrackingMode")
                ? readTrackingMode(tag.getString("TrackingMode"))
                : TrackingMode.LIVE;
        cardinalFrequencyBindings.clear();
        cardinalFrequencyBindings.putAll(createDefaultFreqBindings());
        readCardinalFrequency(tag, provider, Direction.NORTH);
        readCardinalFrequency(tag, provider, Direction.SOUTH);
        readCardinalFrequency(tag, provider, Direction.EAST);
        readCardinalFrequency(tag, provider, Direction.WEST);
        boolean hasSavedCardinalConfig = hasAnyCardinalConfig(tag);
        cardinalOutputConfigs.clear();
        cardinalOutputConfigs.putAll(createDefaultCardinalConfigs());
        readCardinalConfig(tag, Direction.NORTH);
        readCardinalConfig(tag, Direction.SOUTH);
        readCardinalConfig(tag, Direction.EAST);
        readCardinalConfig(tag, Direction.WEST);
        sourceRangePrefillPending = !clientPacket && isLinked() && !hasSavedCardinalConfig;
        liveTargetSensor = new WeakReference<>(null);
    }

    // Remap the schematic tracking tag
    private static void remapSchematicTrackingTag(CompoundTag tag, boolean reading) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null) return;
        BlockPos pos = tag.contains("GyroLocalX")
                ? new BlockPos(tag.getInt("GyroLocalX"), tag.getInt("GyroLocalY"), tag.getInt("GyroLocalZ"))
                : tag.contains("GyroX")
                ? new BlockPos(tag.getInt("GyroX"), tag.getInt("GyroY"), tag.getInt("GyroZ"))
                : null;
        if (tag.hasUUID("GyroSubLevelId")) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping =
                    ctx.getMapping(tag.getUUID("GyroSubLevelId"));
            if (mapping == null || pos == null) {
                clearSchematicTrackingTag(tag);
                return;
            }
            pos = mapping.transform().apply(pos);
            tag.putUUID("GyroSubLevelId", mapping.newUUID());
            putTrackingPosition(tag, pos);
            return;
        }
        if (pos == null) return;
        if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
            if (!ctx.getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ())) {
                clearSchematicTrackingTag(tag);
                return;
            }
            pos = ctx.getPlaceTransform().apply(pos);
        } else {
            pos = reading
                    ? ctx.getPlaceTransform().apply(pos)
                    : ctx.getSetupTransform().apply(pos);
        }
        putTrackingPosition(tag, pos);
    }

    // Put the tracking position
    private static void putTrackingPosition(CompoundTag tag, BlockPos pos) {
        tag.putInt("GyroX", pos.getX());
        tag.putInt("GyroY", pos.getY());
        tag.putInt("GyroZ", pos.getZ());
        tag.putInt("GyroLocalX", pos.getX());
        tag.putInt("GyroLocalY", pos.getY());
        tag.putInt("GyroLocalZ", pos.getZ());
    }

    // Clear the schematic tracking tag
    private static void clearSchematicTrackingTag(CompoundTag tag) {
        for (String key : List.of("GyroSubLevelId", "GyroX", "GyroY", "GyroZ",
                "GyroLocalX", "GyroLocalY", "GyroLocalZ")) {
            tag.remove(key);
        }
    }

    // Get the connection dependencies
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null || gyroSubLevelId == null) return List.of();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        SubLevel subLevel = container.getSubLevel(gyroSubLevelId);
        return subLevel == null || subLevel.isRemoved() ? List.of() : List.of(subLevel);
    }

    // Get the gyro pos
    public BlockPos getGyroPos() {
        return gyroPos;
    }

    // Get the gyro sublevel id
    public @Nullable UUID getGyroSubLevelId() {
        return gyroSubLevelId;
    }

    // Get the gyro local pos
    public @Nullable BlockPos getGyroLocalPos() {
        return gyroLocalPos;
    }

    // Get the resolved gyro target
    public @Nullable BlockEntity getResolvedGyroTarget() {
        return resolveLiveTargetSensor();
    }

    // Get the gyro dimension
    public String getGyroDimension() {
        return gyroDimension;
    }

    // Check if this is linked
    public boolean isLinked() {
        return gyroPos != null && gyroDimension != null;
    }

    // Get the tracking mode
    public TrackingMode getTrackingMode() {
        return trackingMode == null ? TrackingMode.LIVE : trackingMode;
    }

    // Set the tracking mode
    public void setTrackingMode(TrackingMode trackingMode) {
        TrackingMode nextMode = trackingMode == null ? TrackingMode.LIVE : trackingMode;
        if (this.trackingMode == nextMode) {
            return;
        }
        this.trackingMode = nextMode;
        setChanged();
        sendData();
    }

    // Get the cardinal frequency first
    public ItemStack getCardinalFrequencyFirst(Direction dir) {
        FrequencyBinding binding = cardinalFrequencyBindings.get(dir);
        return binding == null ? ItemStack.EMPTY : copySingle(binding.first);
    }

    // Get the cardinal frequency second
    public ItemStack getCardinalFrequencySecond(Direction dir) {
        FrequencyBinding binding = cardinalFrequencyBindings.get(dir);
        return binding == null ? ItemStack.EMPTY : copySingle(binding.second);
    }

    // Set the cardinal frequency
    public void setCardinalFrequency(Direction dir, ItemStack first, ItemStack second) {
        if (dir == null || dir.getAxis().isVertical()) {
            return;
        }
        ItemStack nextFirst = copySingle(first);
        ItemStack nextSecond = copySingle(second);
        FrequencyBinding binding = cardinalFrequencyBindings.get(dir);
        if (binding != null && ItemStack.isSameItemSameComponents(binding.first, nextFirst)
                && ItemStack.isSameItemSameComponents(binding.second, nextSecond)) {
            return;
        }
        unregisterCardinalTransmitter(dir);
        cardinalFrequencyBindings.put(dir, new FrequencyBinding(nextFirst, nextSecond));
        updateCardinalTransmitter(dir, true);
        registerCardinalTransmitter(dir);
        setChanged();
        sendData();
    }

    // Get the cardinal output config
    public CardinalOutputConfig getCardinalOutputConfig(Direction dir) {
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        return config == null ? new CardinalOutputConfig() : config.copy();
    }

    // Set the cardinal output config
    public void setCardinalOutputConfig(Direction dir, CardinalOutputConfig config) {
        if (dir == null || config == null || dir.getAxis().isVertical()) {
            return;
        }
        cardinalOutputConfigs.put(dir, config.copy());
        updateCardinalTransmitter(dir, true);
        setChanged();
        sendData();
    }

    // Get the cardinal servo output
    public double getCardinalServoOutput(Direction dir) {
        if (dir == null || dir.getAxis().isVertical()) {
            return 0.0D;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled) {
            return 0.0D;
        }
        return Math.abs(getConfiguredCardinalOutputFraction(dir));
    }

    // Get the configured cardinal angle degrees
    public double getConfiguredCardinalAngleDegrees(Direction dir) {
        if (dir == null || dir.getAxis().isVertical()) {
            return 0.0D;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled) {
            return 0.0D;
        }
        return applyCardinalServoConfigDeg(config, getRawCardinalAngleDeg(dir));
    }

    // Get the configured cardinal output fraction
    public double getConfiguredCardinalOutputFraction(Direction dir) {
        if (dir == null || dir.getAxis().isVertical()) {
            return 0.0D;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled) {
            return 0.0D;
        }
        return normalizeConfiguredServoOutput(config, getConfiguredCardinalAngleDegrees(dir));
    }

    // Get the configured servo angle degrees
    public double getConfiguredServoAngleDegrees(Direction dir, double rawDegrees) {
        if (dir == null || dir.getAxis().isVertical()) {
            return rawDegrees;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled) {
            return 0.0D;
        }
        return applyCardinalServoConfigDeg(config, rawDegrees);
    }

    // Get the bearing control signal
    private BearingControlSignal getBearingControlSignal(SourceSignalAxes sourceAxes) {
        BearingControlSignal xSignal = getAxisBearingControlSignal(
                sourceAxes, Direction.NORTH, Direction.SOUTH);
        BearingControlSignal zSignal = getAxisBearingControlSignal(
                sourceAxes, Direction.WEST, Direction.EAST);
        return Math.abs(zSignal.angleDegrees()) > Math.abs(xSignal.angleDegrees()) ? zSignal : xSignal;
    }

    // Get the aileron control signal
    private AileronControlSignal getAileronControlSignal(SourceSignalAxes sourceAxes) {
        BearingControlSignal xSignal = getAxisBearingControlSignal(
                sourceAxes, Direction.NORTH, Direction.SOUTH);
        BearingControlSignal zSignal = getAxisBearingControlSignal(
                sourceAxes, Direction.WEST, Direction.EAST);
        return new AileronControlSignal(xSignal.angleDegrees(), zSignal.angleDegrees());
    }

    // Get the axis bearing control signal
    private BearingControlSignal getAxisBearingControlSignal(@Nullable SourceSignalAxes sourceAxes,
                                                             Direction negativeDirection,
                                                             Direction positiveDirection) {
        double positiveRaw = getRawCardinalSourceValue(positiveDirection, sourceAxes);
        double negativeRaw = getRawCardinalSourceValue(negativeDirection, sourceAxes);
        double positiveAngle = getConfiguredServoAngleDegrees(positiveDirection, positiveRaw);
        double negativeAngle = getConfiguredServoAngleDegrees(negativeDirection, negativeRaw);
        double positiveFraction = getConfiguredServoOutputFraction(positiveDirection, positiveRaw);
        double negativeFraction = getConfiguredServoOutputFraction(negativeDirection, negativeRaw);
        return new BearingControlSignal(
                positiveAngle - negativeAngle,
                Mth.clamp(positiveFraction - negativeFraction, -1.0D, 1.0D));
    }

    // Get the configured servo output fraction
    private double getConfiguredServoOutputFraction(Direction dir, double rawDegrees) {
        if (dir == null || dir.getAxis().isVertical()) {
            return 0.0D;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled) {
            return 0.0D;
        }
        return normalizeConfiguredServoOutput(config, applyCardinalServoConfigDeg(config, rawDegrees));
    }

    // Get the bearing control channel id
    private String bearingControlChannelId() {
        return "gyroscope_link:" + worldPosition.toShortString();
    }

    // Get the cardinal redstone signal
    public int getCardinalRedstoneSignal(Direction dir) {
        return getCardinalRedstoneSignal(dir, resolveCurrentSourceSignalAxes(latestPayload));
    }

    // Get the cardinal redstone signal
    private int getCardinalRedstoneSignal(Direction dir,
                                          @Nullable SourceSignalAxes sourceAxes) {
        if (dir == null || dir.getAxis().isVertical()) {
            return 0;
        }
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null || !config.enabled || !config.redstoneEnabled) {
            return 0;
        }
        double raw = getRawCardinalSourceValue(dir, sourceAxes);
        double servoOut = Math.abs(normalizeConfiguredServoOutput(
                config, applyCardinalServoConfigDeg(config, raw)));
        return Mth.clamp((int) Math.round(Mth.clamp(servoOut, 0.0D, 1.0D) * 15.0D), 0, 15);
    }

    // Get the linked angles radians
    public double[] getLinkedAnglesRadians() {
        if (getTrackingMode() == TrackingMode.LIVE) {
            double[] rad = resolveLiveLinkedAnglesRad();
            return rad == null ? null : new double[]{rad[0], rad[1]};
        }
        if (liveAnglesRadians != null) {
            return new double[]{liveAnglesRadians[0], liveAnglesRadians[1]};
        }
        return getTrackingMode() == TrackingMode.LIVE
                ? resolveLiveLinkedAnglesRad()
                : resolveStaticLinkedAnglesRad();
    }

    // Resolve the static linked angles rad
    private double[] resolveStaticLinkedAnglesRad() {
        Level currentLevel = getLevel();
        if (!isLinked() || currentLevel == null) {
            return null;
        }

        BlockEntity targetSensor = SimulatedHelper.findBlockEntityIncludingSubLevels(currentLevel, gyroPos);
        return resolveStaticLinkedAnglesRad(targetSensor,
                AeroworksControllerCompat.directionalAxes(targetSensor));
    }

    // Resolve the static linked angles rad
    private double[] resolveStaticLinkedAnglesRad(
            @Nullable BlockEntity targetSensor,
            @Nullable AeroworksControllerCompat.DirectionalAxes aeroworksAxes) {
        Level currentLevel = getLevel();
        if (!isLinked() || currentLevel == null) {
            return null;
        }

        ResourceLocation linkedDimension;
        try {
            linkedDimension = ResourceLocation.parse(gyroDimension);
        } catch (Exception ignored) {
            return null;
        }

        if (!currentLevel.dimension().location().equals(linkedDimension)) {
            return null;
        }

        if (targetSensor instanceof LinkedOrientationSource src && src.isOrientationSourceActive()) {
            double[] angles = src.getLinkedAnglesRadians();
            return angles == null ? null : new double[]{angles[0], angles[1]};
        }

        double[] adapted = resolveAdapterAngles(targetSensor, aeroworksAxes);
        if (adapted != null) {
            return adapted;
        }

        return SimulatedHelper.getAnglesFromWorld(currentLevel, gyroPos);
    }

    // Resolve the live linked angles rad
    private double[] resolveLiveLinkedAnglesRad() {
        BlockEntity targetSensor = resolveLiveTargetSensor();
        return resolveLiveLinkedAnglesRad(targetSensor,
                AeroworksControllerCompat.directionalAxes(targetSensor));
    }

    // Resolve the live linked angles rad
    private double[] resolveLiveLinkedAnglesRad(
            @Nullable BlockEntity targetSensor,
            @Nullable AeroworksControllerCompat.DirectionalAxes aeroworksAxes) {
        if (targetSensor instanceof LinkedOrientationSource src && src.isOrientationSourceActive()) {
            double[] angles = src.getLinkedAnglesRadians();
            return angles == null ? null : new double[]{angles[0], angles[1]};
        }

        double[] adapted = resolveAdapterAngles(targetSensor, aeroworksAxes);
        if (adapted != null) {
            return adapted;
        }

        if (!SimulatedHelper.isGimbalSensor(targetSensor)) {
            return resolveStaticLinkedAnglesRad(targetSensor, aeroworksAxes);
        }
        return SimulatedHelper.getAngles(targetSensor);
    }

    // Resolve the live target sensor
    private @Nullable BlockEntity resolveLiveTargetSensor() {
        BlockEntity cached = liveTargetSensor.get();
        if (isValidLinkTarget(cached) && !cached.isRemoved()) {
            return cached;
        }

        Level currentLevel = getLevel();
        if (currentLevel == null || !isLinked()) {
            return null;
        }

        BlockEntity resolved = resolveStoredTargetSensor(currentLevel);
        liveTargetSensor = new WeakReference<>(resolved);
        return resolved;
    }

    // Resolve the stored target sensor
    private @Nullable BlockEntity resolveStoredTargetSensor(Level currentLevel) {
        if (gyroSubLevelId != null && gyroLocalPos != null) {
            BlockEntity bySubLevelIdentity = SimulatedHelper.findLoadedBlockEntityExact(
                    currentLevel, gyroSubLevelId, gyroLocalPos);
            if (isValidLinkTarget(bySubLevelIdentity)
                    && matchesStoredSubLevel(bySubLevelIdentity)) {
                return bySubLevelIdentity;
            }
        }

        BlockEntity byStaticPos = gyroPos == null ? null : SimulatedHelper.findBlockEntityIncludingSubLevels(currentLevel, gyroPos);
        if (isValidLinkTarget(byStaticPos)) {
            captureStableTargetReference(byStaticPos);
            return byStaticPos;
        }
        return null;
    }

    // Check if this matches stored sublevel
    private boolean matchesStoredSubLevel(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (gyroSubLevelId == null) {
            return true;
        }
        return gyroSubLevelId.equals(SimulatedHelper.getContainingSubLevelId(blockEntity));
    }

    // Get the linked angles degrees
    public double[] getLinkedAnglesDegrees() {
        double[] rad = getLinkedAnglesRadians();
        if (rad == null) {
            return null;
        }
        return new double[]{Math.toDegrees(rad[0]), Math.toDegrees(rad[1])};
    }

    // Get the linked direction
    public Vec3 getLinkedDirection() {
        BlockEntity liveTarget = resolveLiveTargetSensor();
        if (liveTarget instanceof LinkedOrientationSource src && src.isOrientationSourceActive()) {
            Vec3 dir = src.getLinkedDirection();
            if (dir != null && dir.lengthSqr() > 1.0E-6D) {
                return dir.normalize();
            }
        }

        Vec3 adaptedDirection = resolveAdapterDirection(liveTarget);
        if (adaptedDirection != null) {
            return adaptedDirection;
        }

        if (getTrackingMode() == TrackingMode.LIVE) {
            double[] rad = resolveLiveLinkedAnglesRad();
            return rad == null ? null : directionFromAngles(rad[0], rad[1]);
        }
        if (liveDirection != null) {
            return liveDirection;
        }
        double[] rad = getLinkedAnglesRadians();
        return rad == null ? null : directionFromAngles(rad[0], rad[1]);
    }

    // Get the latest payload
    public @Nullable OrientationPayload getLatestPayload() {
        return latestPayload;
    }

    // Resolve the adapter angles
    private @Nullable double[] resolveAdapterAngles(@Nullable BlockEntity targetSensor) {
        return resolveAdapterAngles(targetSensor,
                AeroworksControllerCompat.directionalAxes(targetSensor));
    }

    // Resolve the adapter angles
    private @Nullable double[] resolveAdapterAngles(
            @Nullable BlockEntity targetSensor,
            @Nullable AeroworksControllerCompat.DirectionalAxes aeroworks) {
        if (aeroworks != null) {
            double maxTiltRadians = Math.toRadians(aeroworks.maxTiltDegrees());
            return new double[]{
                    -aeroworks.localZ() * maxTiltRadians,
                    -aeroworks.localX() * maxTiltRadians
            };
        }
        if (targetSensor instanceof AnalogueContraptionControllerBlockEntity controller) {
            double maxTiltRadians = Math.toRadians(CTConfigs.SERVER.controllerOrientationSourceMaxTiltDegrees.get());
            return OrientationAdapters.resolveControllerAngles(controller, maxTiltRadians);
        }
        if (targetSensor instanceof NavigationTableExtensionAccess nav) {
            return OrientationAdapters.resolveNavigationAngles(nav);
        }
        if (SimulatedHelper.isSteeringWheel(targetSensor)) {
            float wheelDeg = SimulatedHelper.getSteeringWheelAngle(targetSensor);
            if (!Float.isNaN(wheelDeg) && Math.abs(wheelDeg) > 1.0E-4f) {
                return new double[]{0.0D, Math.toRadians(wheelDeg)};
            }
            return new double[]{0.0D, 0.0D};
        }
        return null;
    }

    // Resolve the adapter direction
    private @Nullable Vec3 resolveAdapterDirection(@Nullable BlockEntity targetSensor) {
        double[] angles = resolveAdapterAngles(targetSensor);
        if (angles == null) {
            return null;
        }
        return directionFromAngles(angles[0], angles[1]);
    }

    // Get the direction from angles
    private static Vec3 directionFromAngles(double xAngle, double zAngle) {

        return OrientationMath.directionFromAngles(xAngle, zAngle);
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.gyroscope_link")));
        if (!isLinked()) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gyro_link.status"),
                    CTTooltipHelper.linked(false)));
        } else {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gyro_link.status"),
                    CTTooltipHelper.linked(true)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gyro_link.tracking_mode"),
                    CTTooltipHelper.value(trackingMode == TrackingMode.LIVE ? "Live" : "Static",
                            trackingMode == TrackingMode.LIVE ? ChatFormatting.AQUA : ChatFormatting.GOLD)));
            if (showDetails && gyroPos != null) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gyro_link.source_pos"),
                        CTTooltipHelper.value(gyroPos.getX() + ", " + gyroPos.getY() + ", " + gyroPos.getZ(),
                                ChatFormatting.AQUA)));
            }
            if (showDetails && gyroDimension != null && !gyroDimension.isBlank()) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gyro_link.dimension"),
                        CTTooltipHelper.value(gyroDimension, ChatFormatting.YELLOW)));
            }
        }
        return true;
    }

    // Write the tracking data
    private void writeTrackingData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("TrackingMode", trackingMode.name());
        if (gyroPos != null) {
            tag.putInt("GyroX", gyroPos.getX());
            tag.putInt("GyroY", gyroPos.getY());
            tag.putInt("GyroZ", gyroPos.getZ());
        }
        if (gyroDimension != null) {
            tag.putString("GyroDim", gyroDimension);
        }
        if (gyroSubLevelId != null) {
            tag.putUUID("GyroSubLevelId", gyroSubLevelId);
        }
        if (gyroLocalPos != null) {
            tag.putInt("GyroLocalX", gyroLocalPos.getX());
            tag.putInt("GyroLocalY", gyroLocalPos.getY());
            tag.putInt("GyroLocalZ", gyroLocalPos.getZ());
        }
        writeCardinalFrequency(tag, provider, Direction.NORTH);
        writeCardinalFrequency(tag, provider, Direction.SOUTH);
        writeCardinalFrequency(tag, provider, Direction.EAST);
        writeCardinalFrequency(tag, provider, Direction.WEST);
        writeCardinalConfig(tag, Direction.NORTH);
        writeCardinalConfig(tag, Direction.SOUTH);
        writeCardinalConfig(tag, Direction.EAST);
        writeCardinalConfig(tag, Direction.WEST);
    }

    // Capture the stable target reference
    private void captureStableTargetReference() {
        Level currentLevel = getLevel();
        if (currentLevel == null || gyroPos == null) {
            gyroSubLevelId = null;
            gyroLocalPos = null;
            liveTargetSensor = new WeakReference<>(null);
            return;
        }
        captureStableTargetReference(SimulatedHelper.findBlockEntityIncludingSubLevels(currentLevel, gyroPos));
    }

    // Capture the stable target reference
    private void captureStableTargetReference(@Nullable BlockEntity targetSensor) {
        if (!isValidLinkTarget(targetSensor)) {
            gyroSubLevelId = null;
            gyroLocalPos = null;
            liveTargetSensor = new WeakReference<>(null);
            return;
        }
        gyroSubLevelId = SimulatedHelper.getContainingSubLevelId(targetSensor);
        gyroLocalPos = targetSensor.getBlockPos().immutable();
        liveTargetSensor = new WeakReference<>(targetSensor);
    }

    // Apply the pending source range prefill
    private void applyPendingSourceRangePrefill() {
        if (!sourceRangePrefillPending) {
            return;
        }
        refreshSourceRangesFromLinkedTarget();
    }

    // Refresh the source ranges from linked target
    public boolean refreshSourceRangesFromLinkedTarget() {
        if (level == null || level.isClientSide) {
            return false;
        }
        BlockEntity targetSensor = resolveLiveTargetSensor();
        if (targetSensor == null) {
            sourceRangePrefillPending = isLinked();
            return false;
        }
        sourceRangePrefillPending = false;
        boolean changed = preconfigureSourceRangesFromTarget(targetSensor);
        if (!changed) {
            return false;
        }
        setChanged();
        sendData();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    // Preconfigure the source ranges from the target
    private boolean preconfigureSourceRangesFromTarget(@Nullable BlockEntity targetSensor) {
        SourceRangeProfile profile = resolveSourceRangeProfile(targetSensor);
        if (profile == null) {
            return false;
        }
        boolean changed = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
            SourceRange range = profile.rangeFor(dir);
            if (config == null || range == null) {
                continue;
            }
            if (Math.abs(config.sourceMinDegrees - range.min()) <= 1.0E-6D
                    && Math.abs(config.sourceMaxDegrees - range.max()) <= 1.0E-6D) {
                continue;
            }
            config.sourceMinDegrees = range.min();
            config.sourceMaxDegrees = range.max();
            updateCardinalTransmitter(dir, true);
            changed = true;
        }
        return changed;
    }

    // Resolve the source range profile
    private @Nullable SourceRangeProfile resolveSourceRangeProfile(@Nullable BlockEntity targetSensor) {
        if (AeroworksControllerCompat.isAdvancedDataLinkSource(targetSensor)
                || targetSensor instanceof AnalogueJoystickBlockEntity
                || targetSensor instanceof AnalogueContraptionControllerBlockEntity
                || targetSensor instanceof DirectionalAnalogSource) {
            return SourceRangeProfile.uniform(-1.0D, 1.0D);
        }
        if (SimulatedHelper.isSteeringWheel(targetSensor)) {
            double limit = sanitizeSourceLimit(SimulatedHelper.getSteeringWheelAngleLimitDegrees(targetSensor), 180.0D);
            return SourceRangeProfile.uniform(-limit, limit);
        }
        if (SimulatedHelper.isGimbalSensor(targetSensor)) {
            EnumMap<Direction, SourceRange> ranges = new EnumMap<>(Direction.class);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                double limit = sanitizeSourceLimit(
                        SimulatedHelper.getGimbalSensorAngleLimitDegrees(targetSensor, dir), 45.0D);
                ranges.put(dir, new SourceRange(-limit, limit));
            }
            return new SourceRangeProfile(ranges);
        }
        if (targetSensor instanceof NavigationTableExtensionAccess) {
            return SourceRangeProfile.uniform(-90.0D, 90.0D);
        }
        if (targetSensor instanceof LinkedOrientationSource) {
            return SourceRangeProfile.uniform(-90.0D, 90.0D);
        }
        return null;
    }

    // Sanitize the source limit
    private static double sanitizeSourceLimit(double limit, double fallback) {
        return Double.isFinite(limit) && Math.abs(limit) > 1.0E-6D ? Math.abs(limit) : fallback;
    }

    // Check if the link target is valid
    private static boolean isValidLinkTarget(@Nullable BlockEntity blockEntity) {
        return SimulatedHelper.isGimbalSensor(blockEntity)
                || SimulatedHelper.isSteeringWheel(blockEntity)
                || AeroworksControllerCompat.isAdvancedDataLinkSource(blockEntity)
                || blockEntity instanceof LinkedOrientationSource src && src.isOrientationSourceActive()
                || blockEntity instanceof AnalogueContraptionControllerBlockEntity
                || blockEntity instanceof NavigationTableExtensionAccess
                || blockEntity instanceof UniversalDisplayAdapterBlockEntity;
    }

    // Refresh the display relay target
    public void refreshDisplayRelayTarget() {
        AccDisplayBlockEntity prev = displayRelayTarget.get();
        displayRelayTarget = new WeakReference<>(null);
        relayedDisplayRevision = Long.MIN_VALUE;
        relayedDisplayWidth = 0;
        relayedDisplayHeight = 0;
        relayedRequestedDisplayMode = "";
        if (prev != null && !prev.isRemoved()) {
            prev.clearAdvancedDataLinkFrame(worldPosition);
        }
        BlockEntity linkedTarget = resolveLiveTargetSensor();
        if (linkedTarget instanceof UniversalDisplayAdapterBlockEntity adapter) {
            relayDisplayFrame(adapter);
        }
    }

    // Handle the linked display
    public boolean interactLinkedDisplay(double horizontal, double vertical,
                                         int mouseButton) {
        BlockEntity target = resolveLiveTargetSensor();
        return target instanceof UniversalDisplayAdapterBlockEntity adapter
                && adapter.interact(horizontal, vertical, mouseButton);
    }

    // Relay the display frame
    private void relayDisplayFrame(UniversalDisplayAdapterBlockEntity adapter) {
        AccDisplayBlockEntity display = resolveDisplayRelayTarget();
        if (display == null) {
            return;
        }
        int width = Math.max(1, display.networkWidth() * 96);
        int height = Math.max(1, display.networkHeight() * 96);
        adapter.configureDisplaySize(width, height);
        String requestedMode = adapter.requestedAccDisplayMode();
        if (!requestedMode.isBlank()
                && !requestedMode.equals(relayedRequestedDisplayMode)) {
            display.requestDisplayPresentation(requestedMode);
        }
        relayedRequestedDisplayMode = requestedMode;
        long revision = adapter.frameRevision();
        if (relayedDisplayAdapter == adapter
                && relayedDisplayRevision == revision
                && relayedDisplayWidth == width
                && relayedDisplayHeight == height) {
            return;
        }
        relayedDisplayAdapter = adapter;
        relayedDisplayRevision = revision;
        relayedDisplayWidth = width;
        relayedDisplayHeight = height;
        CompoundTag frame = adapter.accDisplayFrame();
        if (frame.isEmpty()) {
            display.clearAdvancedDataLinkFrame(worldPosition);
        } else {
            display.acceptAdvancedDataLinkFrame(worldPosition, frame);
        }
    }

    // Resolve the display relay target
    private @Nullable AccDisplayBlockEntity resolveDisplayRelayTarget() {
        AccDisplayBlockEntity cached = displayRelayTarget.get();
        if (cached != null && !cached.isRemoved()) {
            return cached;
        }
        if (level == null) {
            return null;
        }
        for (Direction dir : Direction.values()) {
            BlockEntity candidate = level.getBlockEntity(worldPosition.relative(dir));
            if (!(candidate instanceof AccDisplayBlockEntity display)) {
                continue;
            }
            AccDisplayBlockEntity root = display.networkRoot();
            cached = root == null ? display : root;
            displayRelayTarget = new WeakReference<>(cached);
            return cached;
        }
        return null;
    }

    // Clear the display relay if needed
    private void clearDisplayRelayIfNeeded() {
        if (relayedDisplayAdapter != null) {
            clearDisplayRelay();
        }
    }

    // Clear the display relay
    private void clearDisplayRelay() {
        AccDisplayBlockEntity display = displayRelayTarget.get();
        if (display != null && !display.isRemoved()) {
            display.clearAdvancedDataLinkFrame(worldPosition);
        }
        displayRelayTarget = new WeakReference<>(null);
        relayedDisplayAdapter = null;
        relayedDisplayRevision = Long.MIN_VALUE;
        relayedDisplayWidth = 0;
        relayedDisplayHeight = 0;
        relayedRequestedDisplayMode = "";
    }

    // Read the tracking mode
    private static TrackingMode readTrackingMode(String name) {
        try {
            return TrackingMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return TrackingMode.LIVE;
        }
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GyroscopeLinkMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.gyroscope_link.config.title");
    }

    // Check if the player can use this
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {

        MenuOpenHeader.encode(buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
        buffer.writeUtf(getTrackingMode().name());
    }

    // Get the raw cardinal angle deg
    private double getRawCardinalAngleDeg(Direction dir) {
        return getRawCardinalSourceValue(dir, resolveCurrentSourceSignalAxes(null));
    }

    // Get the raw cardinal source value
    private double getRawCardinalSourceValue(Direction dir, @Nullable SourceSignalAxes sourceAxes) {
        if (dir == null || sourceAxes == null) {
            return 0.0D;
        }
        double signedAxis = getSignedSourceAxis(dir, sourceAxes);
        return switch (dir) {
            case NORTH, WEST -> Math.max(0.0D, -signedAxis);
            case SOUTH, EAST -> Math.max(0.0D, signedAxis);
            default -> 0.0D;
        };
    }

    // Get the signed source axis
    private double getSignedSourceAxis(Direction dir, SourceSignalAxes sourceAxes) {
        boolean useNativeUnits = sourceAxes.normalizedSource()
                && isNormalizedSourceRange(cardinalOutputConfigs.get(dir));
        return switch (dir) {
            case NORTH, SOUTH -> useNativeUnits ? sourceAxes.xSource() : sourceAxes.xDegrees();
            case EAST, WEST -> useNativeUnits ? sourceAxes.zSource() : sourceAxes.zDegrees();
            default -> 0.0D;
        };
    }

    // Check if this is normalized source range
    private static boolean isNormalizedSourceRange(@Nullable CardinalOutputConfig config) {
        return config != null
                && Math.max(Math.abs(config.sourceMinDegrees), Math.abs(config.sourceMaxDegrees)) <= 2.0D;
    }

    // Resolve the current source signal axes
    private @Nullable SourceSignalAxes resolveCurrentSourceSignalAxes(@Nullable OrientationPayload payload) {
        BlockEntity targetSensor = resolveLiveTargetSensor();
        return resolveCurrentSourceSignalAxes(payload, targetSensor,
                AeroworksControllerCompat.directionalAxes(targetSensor));
    }

    // Resolve the current source signal axes
    private @Nullable SourceSignalAxes resolveCurrentSourceSignalAxes(
            @Nullable OrientationPayload payload,
            @Nullable BlockEntity targetSensor,
            @Nullable AeroworksControllerCompat.DirectionalAxes aeroworksAxes) {
        SourceSignalAxes nativeAxes = resolveNativeSourceSignalAxes(targetSensor, aeroworksAxes);
        if (nativeAxes != null) {
            return nativeAxes;
        }
        return resolveDegSourceSignalAxes(payload);
    }

    // Resolve the native source signal axes
    private @Nullable SourceSignalAxes resolveNativeSourceSignalAxes(@Nullable BlockEntity targetSensor) {
        return resolveNativeSourceSignalAxes(targetSensor,
                AeroworksControllerCompat.directionalAxes(targetSensor));
    }

    // Resolve the native source signal axes
    private @Nullable SourceSignalAxes resolveNativeSourceSignalAxes(
            @Nullable BlockEntity targetSensor,
            @Nullable AeroworksControllerCompat.DirectionalAxes aeroworks) {
        if (aeroworks != null) {
            double xSource = -aeroworks.localZ();
            double zSource = -aeroworks.localX();
            return new SourceSignalAxes(xSource, zSource,
                    xSource * aeroworks.maxTiltDegrees(),
                    zSource * aeroworks.maxTiltDegrees(), true);
        }
        if (targetSensor instanceof AnalogueJoystickBlockEntity joystick) {
            DirectionalAnalogSnapshot snapshot = joystick.getDirectionalAnalogSnapshot();
            double xSource = -snapshot.localZ();
            double zSource = -snapshot.localX();
            double maxTiltDegrees = joystick.getMaxTiltDegrees();
            return new SourceSignalAxes(xSource, zSource,
                    xSource * maxTiltDegrees, zSource * maxTiltDegrees, true);
        }
        if (targetSensor instanceof AnalogueContraptionControllerBlockEntity controller) {
            double xSource = getControllerAxisValue(controller, "pitch");
            double zSource = getControllerAxisValue(controller, "roll");
            double maxTiltDegrees = CTConfigs.SERVER.controllerOrientationSourceMaxTiltDegrees.get();
            return new SourceSignalAxes(xSource, zSource,
                    xSource * maxTiltDegrees, zSource * maxTiltDegrees, true);
        }
        if (targetSensor instanceof DirectionalAnalogSource directionalSource) {
            DirectionalAnalogSnapshot snapshot = directionalSource.getDirectionalAnalogSnapshot();
            if (snapshot == null) {
                snapshot = DirectionalAnalogSnapshot.ZERO;
            }
            double xSource = snapshot.localZ();
            double zSource = snapshot.localX();
            return new SourceSignalAxes(xSource, zSource,
                    Math.toDegrees(Math.atan(xSource)), Math.toDegrees(Math.atan(zSource)), true);
        }
        if (SimulatedHelper.isSteeringWheel(targetSensor)) {
            float wheelDegrees = SimulatedHelper.getSteeringWheelAngle(targetSensor);
            if (Float.isNaN(wheelDegrees)) {
                return null;
            }
            return new SourceSignalAxes(0.0D, wheelDegrees, 0.0D, wheelDegrees, false);
        }
        if (SimulatedHelper.isGimbalSensor(targetSensor)) {
            double[] angles = SimulatedHelper.getAngles(targetSensor);
            if (angles == null) {
                return null;
            }
            double xDegrees = Math.toDegrees(angles[0]);
            double zDegrees = Math.toDegrees(angles[1]);
            return new SourceSignalAxes(xDegrees, zDegrees, xDegrees, zDegrees, false);
        }
        return null;
    }

    // Get the controller axis value
    private static double getControllerAxisValue(AnalogueContraptionControllerBlockEntity controller, String axisId) {
        com.rieno.gadgetsandgizmos.lib.control.AnalogueAxis axis = controller.getAxis(axisId);
        return axis == null ? 0.0D : axis.getSignedValue();
    }

    // Resolve the deg source signal axes
    private @Nullable SourceSignalAxes resolveDegSourceSignalAxes(@Nullable OrientationPayload payload) {
        if (payload != null) {
            double xDegrees = payload.getXAngleDegrees();
            double zDegrees = payload.getZAngleDegrees();
            return new SourceSignalAxes(xDegrees, zDegrees, xDegrees, zDegrees, false);
        }
        double[] rad = liveAnglesRadians != null ? liveAnglesRadians : getLinkedAnglesRadians();
        if (rad == null) {
            return null;
        }
        double xDegrees = Math.toDegrees(rad[0]);
        double zDegrees = Math.toDegrees(rad[1]);
        return new SourceSignalAxes(xDegrees, zDegrees, xDegrees, zDegrees, false);
    }

    // Refresh the cardinal transmitter registrations
    private void refreshCardinalTransmitterRegistrations() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (hasCardinalFrequency(dir)) {
                registerCardinalTransmitter(dir);
            } else {
                unregisterCardinalTransmitter(dir);
            }
        }
    }

    // Register the cardinal transmitter
    private void registerCardinalTransmitter(Direction dir) {
        if (level == null || level.isClientSide || registeredCardinalTransmitters.contains(dir)
                || !hasCardinalFrequency(dir)) {
            return;
        }
        CardinalTransmitter transmitter = cardinalTransmitters.get(dir);
        if (transmitter == null) {
            return;
        }
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(resolveLinkLevel(level), transmitter);
        registeredCardinalTransmitters.add(dir);
    }

    // Remove the cardinal transmitter
    private void unregisterCardinalTransmitter(Direction dir) {
        if (level == null || level.isClientSide || !registeredCardinalTransmitters.remove(dir)) {
            return;
        }
        CardinalTransmitter transmitter = cardinalTransmitters.get(dir);
        if (transmitter != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(resolveLinkLevel(level), transmitter);
        }
    }

    // Remove the cardinal transmitters
    private void unregisterCardinalTransmitters() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            unregisterCardinalTransmitter(dir);
        }
    }

    // Update the cardinal transmitters
    private void updateCardinalTransmitters(boolean force) {
        if (level == null || level.isClientSide) {
            return;
        }
        updateCardinalTransmitters(force, resolveCurrentSourceSignalAxes(latestPayload));
    }

    // Update the cardinal transmitters
    private void updateCardinalTransmitters(boolean force,
                                            @Nullable SourceSignalAxes sourceAxes) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            updateCardinalTransmitter(dir, force, sourceAxes);
        }
    }

    // Update the cardinal transmitter
    private void updateCardinalTransmitter(Direction dir, boolean force) {
        updateCardinalTransmitter(dir, force, resolveCurrentSourceSignalAxes(latestPayload));
    }

    // Update the cardinal transmitter
    private void updateCardinalTransmitter(Direction dir, boolean force,
                                           @Nullable SourceSignalAxes sourceAxes) {
        if (level == null || level.isClientSide) {
            return;
        }
        CardinalTransmitter transmitter = cardinalTransmitters.get(dir);
        if (transmitter == null) {
            return;
        }
        int nextStrength = getCardinalRedstoneSignal(dir, sourceAxes);
        if (!force && transmitter.transmittedStrength == nextStrength) {
            return;
        }
        transmitter.transmittedStrength = nextStrength;
        if (registeredCardinalTransmitters.contains(dir)) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(resolveLinkLevel(level), transmitter);
        }
    }

    // Check if this has cardinal frequency
    private boolean hasCardinalFrequency(Direction dir) {
        FrequencyBinding binding = cardinalFrequencyBindings.get(dir);
        return binding != null && (!binding.first.isEmpty() || !binding.second.isEmpty());
    }

    // Resolve the link level
    private Level resolveLinkLevel(Level currentLevel) {
        try {
            Object viaGetter = currentLevel.getClass().getMethod("getLevel").invoke(currentLevel);
            if (viaGetter instanceof Level worldLevel) {
                return worldLevel;
            }
        } catch (Exception ignored) {
        }
        try {
            if (currentLevel.getServer() != null) {
                net.minecraft.server.level.ServerLevel byDimension = currentLevel.getServer().getLevel(currentLevel.dimension());
                if (byDimension != null) {
                    return byDimension;
                }
            }
        } catch (Exception ignored) {
        }
        return currentLevel;
    }

    // Apply the cardinal servo config deg
    private static double applyCardinalServoConfigDeg(CardinalOutputConfig config, double rawDegrees) {
        rawDegrees = normalizeRawAngleForSourceRange(config, rawDegrees);
        double sourceMin = Math.min(config.sourceMinDegrees, config.sourceMaxDegrees);
        double sourceMax = Math.max(config.sourceMinDegrees, config.sourceMaxDegrees);
        double sourceSpan = Math.max(1.0E-6D, sourceMax - sourceMin);
        double normalized = Mth.clamp((rawDegrees - sourceMin) / sourceSpan, 0.0D, 1.0D);
        double mapped = Mth.lerp(normalized, config.outputMin, config.outputMax);
        double clampMin = Math.min(config.clampMin, config.clampMax);
        double clampMax = Math.max(config.clampMin, config.clampMax);
        return Mth.clamp(mapped, clampMin, clampMax);
    }

    // Normalize the raw angle for source range
    private static double normalizeRawAngleForSourceRange(CardinalOutputConfig config, double rawDegrees) {
        double sourceMin = Math.min(config.sourceMinDegrees, config.sourceMaxDegrees);
        double sourceMax = Math.max(config.sourceMinDegrees, config.sourceMaxDegrees);
        if (sourceMin >= 0.0D && sourceMax > 180.0D) {
            double wrapped = rawDegrees % 360.0D;
            return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
        }
        return rawDegrees;
    }

    // Normalize the configured servo output
    private static double normalizeConfiguredServoOutput(CardinalOutputConfig config, double outputDegrees) {
        double clampMagnitude = Math.max(Math.abs(config.clampMin), Math.abs(config.clampMax));
        double outputMagnitude = Math.max(Math.abs(config.outputMin), Math.abs(config.outputMax));
        double maxMagnitude = Math.max(1.0E-6D, Math.max(clampMagnitude, outputMagnitude));
        return Mth.clamp(outputDegrees / maxMagnitude, -1.0D, 1.0D);
    }

    // Create the default freq bindings
    private static EnumMap<Direction, FrequencyBinding> createDefaultFreqBindings() {
        EnumMap<Direction, FrequencyBinding> defaults = new EnumMap<>(Direction.class);
        defaults.put(Direction.NORTH, new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY));
        defaults.put(Direction.SOUTH, new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY));
        defaults.put(Direction.EAST, new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY));
        defaults.put(Direction.WEST, new FrequencyBinding(ItemStack.EMPTY, ItemStack.EMPTY));
        return defaults;
    }

    // Create the default cardinal configs
    private static EnumMap<Direction, CardinalOutputConfig> createDefaultCardinalConfigs() {
        EnumMap<Direction, CardinalOutputConfig> defaults = new EnumMap<>(Direction.class);
        defaults.put(Direction.NORTH, new CardinalOutputConfig());
        defaults.put(Direction.SOUTH, new CardinalOutputConfig());
        defaults.put(Direction.EAST, new CardinalOutputConfig());
        defaults.put(Direction.WEST, new CardinalOutputConfig());
        return defaults;
    }

    // Get the cardinal config key
    private static String cardinalConfigKey(Direction dir) {
        return "CardinalConfig_" + dir.getSerializedName();
    }

    // Write the cardinal config
    private void writeCardinalConfig(CompoundTag tag, Direction dir) {
        CardinalOutputConfig config = cardinalOutputConfigs.get(dir);
        if (config == null) {
            return;
        }
        tag.put(cardinalConfigKey(dir), config.toTag());
    }

    // Read the cardinal config
    private void readCardinalConfig(CompoundTag tag, Direction dir) {
        String key = cardinalConfigKey(dir);
        if (!tag.contains(key)) {
            return;
        }
        cardinalOutputConfigs.put(dir, CardinalOutputConfig.fromTag(tag.getCompound(key)));
    }

    // Check if this has any cardinal config
    private static boolean hasAnyCardinalConfig(CompoundTag tag) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (tag.contains(cardinalConfigKey(dir))) {
                return true;
            }
        }
        return false;
    }

    // Get the cardinal frequency key
    private static String cardinalFrequencyKey(Direction dir) {
        return "CardinalFreq_" + dir.getSerializedName();
    }

    // Write the cardinal frequency
    private void writeCardinalFrequency(CompoundTag tag, HolderLookup.Provider provider, Direction dir) {
        FrequencyBinding binding = cardinalFrequencyBindings.get(dir);
        if (binding == null) {
            return;
        }
        CompoundTag bindingTag = new CompoundTag();
        if (!binding.first.isEmpty()) {
            bindingTag.put("First", binding.first.saveOptional(provider));
        }
        if (!binding.second.isEmpty()) {
            bindingTag.put("Second", binding.second.saveOptional(provider));
        }
        if (!bindingTag.isEmpty()) {
            tag.put(cardinalFrequencyKey(dir), bindingTag);
        }
    }

    // Read the cardinal frequency
    private void readCardinalFrequency(CompoundTag tag, HolderLookup.Provider provider, Direction dir) {
        String key = cardinalFrequencyKey(dir);
        if (!tag.contains(key)) {
            return;
        }
        CompoundTag bindingTag = tag.getCompound(key);
        ItemStack first = bindingTag.contains("First") ? ItemStack.parseOptional(provider, bindingTag.getCompound("First")) : ItemStack.EMPTY;
        ItemStack second = bindingTag.contains("Second") ? ItemStack.parseOptional(provider, bindingTag.getCompound("Second")) : ItemStack.EMPTY;
        cardinalFrequencyBindings.put(dir, new FrequencyBinding(first, second));
    }

    // Store the bearing control signal
    private record BearingControlSignal(double angleDegrees, double outputFraction) {
    }

    // Store the aileron control signal
    private record AileronControlSignal(double primaryAngleDegrees, double secondaryAngleDegrees) {
    }

    // Store the source signal axes
    private record SourceSignalAxes(double xSource, double zSource, double xDegrees, double zDegrees,
                                    boolean normalizedSource) {
    }

    // Store the source range
    private record SourceRange(double min, double max) {
    }

    // Store the source range profile
    private record SourceRangeProfile(EnumMap<Direction, SourceRange> ranges) {
        // Get the uniform
        private static SourceRangeProfile uniform(double min, double max) {
            EnumMap<Direction, SourceRange> ranges = new EnumMap<>(Direction.class);
            SourceRange range = new SourceRange(min, max);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                ranges.put(dir, range);
            }
            return new SourceRangeProfile(ranges);
        }

        // Get the range
        private @Nullable SourceRange rangeFor(Direction dir) {
            return ranges.get(dir);
        }
    }

    // Handle the frequency binding
    public static class FrequencyBinding {
        // Current first entry
        public ItemStack first;
        // Current second entry
        public ItemStack second;

        // Initialize the frequency binding
        public FrequencyBinding(ItemStack first, ItemStack second) {
            this.first = first == null ? ItemStack.EMPTY : first;
            this.second = second == null ? ItemStack.EMPTY : second;
        }
    }

    // Handle the cardinal transmitter
    private class CardinalTransmitter implements IRedstoneLinkable {
        // Cardinal transmitter direction
        private final Direction direction;
        // Transmitted strength
        private int transmittedStrength;

        // Initialize the cardinal transmitter
        private CardinalTransmitter(Direction dir) {
            this.direction = dir;
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return transmittedStrength;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return false;
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {
            return level != null && !isRemoved();
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            FrequencyBinding binding = cardinalFrequencyBindings.get(direction);
            if (binding == null) {
                return Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY,
                        RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(binding.first),
                    RedstoneLinkNetworkHandler.Frequency.of(binding.second));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            Vec3 projected = SimulatedHelper.toContainingWorldPosition(GyroscopeLinkBlockEntity.this,
                    Vec3.atCenterOf(worldPosition));
            return projected == null ? worldPosition : BlockPos.containing(projected);
        }
    }

    // Copy one item
    private static ItemStack copySingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Define the tracking mode values
    public enum TrackingMode implements INamedIconOptions {
        LIVE(AllIcons.I_REFRESH, "live"),
        STATIC(AllIcons.I_CONFIG_LOCKED, "static");

        // Icon
        private final AllIcons icon;
        // Key
        private final String key;

        // Initialize the tracking mode
        TrackingMode(AllIcons icon, String key) {
            this.icon = icon;
            this.key = key;
        }

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return icon;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.gyroscope_link.mode." + key;
        }
    }

    // Define the configure control option values
    private enum ConfigureControlOption implements INamedIconOptions {
        CONFIGURE;

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return AllIcons.I_CONFIG_OPEN;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.gyroscope_link.config.title";
        }
    }

    // Store cardinal output settings
    public static class CardinalOutputConfig {
        // Tracks whether cardinal output is enabled
        public boolean enabled = true;
        // Tracks whether redstone is enabled
        public boolean redstoneEnabled = true;
        // Source min in degrees
        public double sourceMinDegrees = -90.0D;
        // Source max in degrees
        public double sourceMaxDegrees = 90.0D;
        // Output min
        public double outputMin = -90.0D;
        // Output max
        public double outputMax = 90.0D;
        // Current clamp min
        public double clampMin = -90.0D;
        // Current clamp max
        public double clampMax = 90.0D;

        // Write the cardinal output config data
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Enabled", enabled);
            tag.putBoolean("RedstoneEnabled", redstoneEnabled);
            tag.putDouble("SourceMinDegrees", sourceMinDegrees);
            tag.putDouble("SourceMaxDegrees", sourceMaxDegrees);
            tag.putDouble("OutputMin", outputMin);
            tag.putDouble("OutputMax", outputMax);
            tag.putDouble("ClampMin", clampMin);
            tag.putDouble("ClampMax", clampMax);
            return tag;
        }

        // Read the cardinal output config data
        public static CardinalOutputConfig fromTag(CompoundTag tag) {
            CardinalOutputConfig config = new CardinalOutputConfig();
            config.enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
            config.redstoneEnabled = !tag.contains("RedstoneEnabled") || tag.getBoolean("RedstoneEnabled");
            config.sourceMinDegrees = tag.contains("SourceMinDegrees") ? tag.getDouble("SourceMinDegrees") : config.sourceMinDegrees;
            config.sourceMaxDegrees = tag.contains("SourceMaxDegrees") ? tag.getDouble("SourceMaxDegrees") : config.sourceMaxDegrees;
            config.outputMin = tag.contains("OutputMin") ? tag.getDouble("OutputMin") : config.outputMin;
            config.outputMax = tag.contains("OutputMax") ? tag.getDouble("OutputMax") : config.outputMax;
            config.clampMin = tag.contains("ClampMin") ? tag.getDouble("ClampMin") : config.clampMin;
            config.clampMax = tag.contains("ClampMax") ? tag.getDouble("ClampMax") : config.clampMax;
            migrateLegacyNormalizedOutput(config);
            return config;
        }

        // Migrate the legacy normalized output
        private static void migrateLegacyNormalizedOutput(CardinalOutputConfig config) {
            boolean normalizedOutput = Math.abs(config.outputMin) <= 1.0D && Math.abs(config.outputMax) <= 1.0D;
            boolean normalizedClamp = Math.abs(config.clampMin) <= 1.0D && Math.abs(config.clampMax) <= 1.0D;
            if (!normalizedOutput && !normalizedClamp) {
                return;
            }
            if (normalizedOutput) {
                config.outputMin *= 90.0D;
                config.outputMax *= 90.0D;
            }
            if (normalizedClamp) {
                config.clampMin *= 90.0D;
                config.clampMax *= 90.0D;
            }
        }

        // Copy the cardinal output config
        public CardinalOutputConfig copy() {
            CardinalOutputConfig copy = new CardinalOutputConfig();
            copy.enabled = enabled;
            copy.redstoneEnabled = redstoneEnabled;
            copy.sourceMinDegrees = sourceMinDegrees;
            copy.sourceMaxDegrees = sourceMaxDegrees;
            copy.outputMin = outputMin;
            copy.outputMax = outputMax;
            copy.clampMin = clampMin;
            copy.clampMax = clampMax;
            return copy;
        }
    }
}
