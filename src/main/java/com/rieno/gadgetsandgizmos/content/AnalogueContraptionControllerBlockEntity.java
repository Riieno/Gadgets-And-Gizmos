package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SchematicSubLevelReferenceRemapper;
import com.rieno.gadgetsandgizmos.compat.controller.ExternalBlockEntityDirectControlCompat;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueAxis;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannel;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueSignalPacket;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueTransmissionTarget;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.ControllerBindingOwner;
import com.rieno.gadgetsandgizmos.lib.control.ControllerMechanic;
import com.rieno.gadgetsandgizmos.lib.control.ControllerMechanicBinding;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ControllerRuntimeSyncPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
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
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Handle controller inputs, layout and linked outputs
public class AnalogueContraptionControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation,
        MenuProvider, com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity, BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DIRECT_TARGET_OPTION_PREFIX = "::opt:";
    private static final String DIRECT_TARGET_PROPERTY_PREFIX = "::prop:";
    private static final String SIMULATED_DIRECTIONAL_GEARSHIFT_BLOCK_ID = "simulated:directional_gearshift";
    private static final String CREATE_ANALOG_LEVER_BLOCK_ID = "create:analog_lever";
    private static final String CREATE_ANALOGUE_LEVER_BLOCK_ID = "create:analogue_lever";
    private static final boolean DEBUG_LINKER_FACE_IO = Boolean.parseBoolean(
            System.getProperty("createthrusters.debug.linker_face_io", "false"));
    private static final double DEFAULT_RAMP_RATE = 0.08D;
    private static final double DEFAULT_STEP_AMOUNT = 0.1D;
    private static final double DEFAULT_SMOOTHING = 0.2D;
    private static final Direction[] OUTPUT_DIRECTIONS = Direction.values();
    private static final String ASSEMBLY_TRANSFER_TAG = "ControllerAssemblyTransfer";
    private static final String EMBEDDED_SLAB_TAG = "EmbeddedSlab";
    private static final String EMBEDDED_SLAB_MATERIAL_TAG = "EmbeddedSlabMaterial";
    private static final String EMBEDDED_BLOCK_ENTITY_TAG = "EmbeddedBlockEntity";
    private static final double RUNTIME_SYNC_RANGE_SQR = 256.0D * 256.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked channels
    private final Map<String, AnalogueChannel> channels = new LinkedHashMap<>();
    // Tracked frequency bindings
    private final Map<String, FrequencyBinding> frequencyBindings = new LinkedHashMap<>();
    // Tracked axes
    private final Map<String, AnalogueAxis> axes = new LinkedHashMap<>();
    // Tracked wireless targets
    private final Map<String, ControllerLinkTarget> wirelessTargets = new LinkedHashMap<>();
    // Tracked direct targets
    private final Map<String, ControllerDirectTargetReference> directTargets = new LinkedHashMap<>();
    // Input targets
    private final Map<String, ControllerDirectTargetReference> inputTargets = new LinkedHashMap<>();
    // Tracked sampled input active
    private final Map<String, Boolean> sampledInputActive = new LinkedHashMap<>();
    // Local output sides
    private final Map<String, Direction> localOutputSides = new LinkedHashMap<>();

    // Input frequency bindings
    private final Map<String, FrequencyBinding> inputFrequencyBindings = new LinkedHashMap<>();

    // Input link targets
    private final Map<String, ControllerLinkInputTarget> inputLinkTargets = new LinkedHashMap<>();
    // Tracked key bindings
    private final Map<String, Integer> keyBindings = new LinkedHashMap<>();
    // Last channel output strengths
    private final Map<String, Integer> lastChannelOutputStrengths = new LinkedHashMap<>();
    // Last custom entry output strengths
    private final Map<String, Integer> lastCustomEntryOutputStrengths = new LinkedHashMap<>();
    // Tracked hardware controller inputs
    private final Map<String, Double> hardwareControllerInputs = new LinkedHashMap<>();

    // Tracked binding modes
    private final Map<String, String> bindingModes = new LinkedHashMap<>();

    // Tracked binding presets
    private final Map<String, String> bindingPresets = new LinkedHashMap<>();

    // Tracked custom key entries
    private final List<CustomKeyEntry> customKeyEntries = new ArrayList<>();
    // Tracked custom key entries view
    private final List<CustomKeyEntry> customKeyEntriesView = Collections.unmodifiableList(customKeyEntries);
    // Tracked custom entry channels
    private final Map<String, AnalogueChannel> customEntryChannels = new LinkedHashMap<>();
    // Tracked custom frequency bindings
    private final Map<String, FrequencyBinding> customFrequencyBindings = new LinkedHashMap<>();
    // Tracked custom wireless targets
    private final Map<String, ControllerLinkTarget> customWirelessTargets = new LinkedHashMap<>();
    // Tracked custom input frequency bindings
    private final Map<String, FrequencyBinding> customInputFrequencyBindings = new LinkedHashMap<>();
    // Tracked custom input link targets
    private final Map<String, ControllerLinkInputTarget> customInputLinkTargets = new LinkedHashMap<>();
    // Tracked pressed custom entry ids
    private final Set<String> pressedCustomEntryIds = new HashSet<>();
    // Tracked pressed custom entry step down ids
    private final Set<String> pressedCustomEntryStepDownIds = new HashSet<>();
    // Tracked sampled custom entry ids
    private final Set<String> sampledCustomEntryIds = new HashSet<>();
    // Tracked sampled custom entry values
    private final Map<String, Float> sampledCustomEntryValues = new LinkedHashMap<>();
    // Tracked stored targets
    private final List<ControllerDiscoveryNode> storedTargets = new ArrayList<>();

    // Linker slot
    private final ItemStackHandler linkerSlot = new ItemStackHandler(1) {
        // Handle the contents changed event
        @Override
        protected void onContentsChanged(int slot) {
            if (slot != 0) {
                return;
            }
            if (loadingControllerData) {
                return;
            }
            if (level != null && !level.isClientSide) {
                ContraptionNetworkLinkerData.ensureStoredIdentity(getStackInSlot(0));
                ContraptionNetworkLinkerData.migrateLegacyStorage(getStackInSlot(0));
                syncFromInsertedLinker();
                if (!pendingAssemblyBindingRemap) {
                    replayCurrentOutputs();
                }
                syncTrackedLinkerRegistry();
                setChanged();
                sendData();
            }
        }

        // Check if the item is valid
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ContraptionNetworkLinkerItem)) {
                return false;
            }
            ServerLevel serverLevel = SableLevelApi.serverLevel(level);
            return serverLevel == null
                    || ContraptionNetworkLinkerTracker.get(serverLevel.getServer())
                    .canMutateLinker(serverLevel, stack);
        }

        // Get the slot limit
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    // Local outputs
    private final EnumMap<Direction, Integer> localOutputs = new EnumMap<>(Direction.class);
    // Current controller runtime program
    private @Nullable ControllerRuntimeProgram controllerRuntimeProgram;
    // Tracks whether controller runtime program is dirty
    private boolean controllerRuntimeProgramDirty = true;
    // Last runtime linker registry sync
    private long lastRuntimeLinkerRegistrySync = Long.MIN_VALUE;
    // Last runtime wireless registration refresh
    private long lastRuntimeWirelessRegistrationRefresh = Long.MIN_VALUE;
    // Current assembly source sub-level id
    private @Nullable UUID assemblySourceSubLevelId;
    // Current assembly old pos
    private @Nullable BlockPos assemblyOldPos;
    // Current assembly new pos
    private @Nullable BlockPos assemblyNewPos;
    // Tracks whether assembly source was the world
    private boolean assemblySourceWasWorld;
    // Tracks whether assembly binding remap is pending
    private boolean pendingAssemblyBindingRemap;
    // Tracks whether destructive removal is pending
    private boolean destructiveRemovalPending;
    // Tracks whether controller data is loading
    private boolean loadingControllerData;
    // Current embedded slab state
    private @Nullable BlockState embeddedSlabState;
    // Current embedded slab material
    private @Nullable BlockState embeddedSlabMaterial;
    // Current embedded block entity data
    private @Nullable CompoundTag embeddedBlockEntityData;
    // Current embedded block entity preview
    private @Nullable BlockEntity embeddedBlockEntityPreview;
    // Tracks whether embedded block is being restored
    private boolean restoringEmbeddedBlock;
    // Current custom name
    @org.jetbrains.annotations.Nullable
    private String customName;
    // Current ometer output signal
    private int ometerOutputSignal;
    // Current controller manifest id
    private String controllerManifestId = "";
    // Current controller manifest revision
    private int controllerManifestRevision;
    // Current controller manifest hash
    private String controllerManifestHash = "";
    // Pending controller schematic payload
    private @Nullable CompoundTag pendingControllerSchematicPayload;
    // Tracks whether controller schematic import is pending
    private boolean pendingControllerSchematicImport;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller
    public AnalogueContraptionControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get(), pos, blockState);
    }

    // Initialize the analogue contraption controller
    protected AnalogueContraptionControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        for (Direction dir : OUTPUT_DIRECTIONS) {
            localOutputs.put(dir, 0);
        }
        createChannels();
        createAxes();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the embedded slab state
    public @Nullable BlockState getEmbeddedSlabState() {
        return embeddedSlabState;
    }

    // Get the embedded block state
    public @Nullable BlockState getEmbeddedBlockState() {
        return embeddedSlabState;
    }

    // Set the embedded slab state
    public void setEmbeddedSlabState(@Nullable BlockState slabState) {
        setEmbeddedBlockState(slabState, null, null);
    }

    // Get the embedded slab material
    public @Nullable BlockState getEmbeddedSlabMaterial() {
        return embeddedSlabMaterial;
    }

    // Set the embedded slab state
    public void setEmbeddedSlabState(@Nullable BlockState slabState, @Nullable BlockState slabMaterial) {
        setEmbeddedBlockState(slabState, slabMaterial, null);
    }

    // Set the embedded block state
    public void setEmbeddedBlockState(@Nullable BlockState blockState, @Nullable BlockState blockMaterial,
                                      @Nullable CompoundTag blockEntityData) {
        BlockState normalized = blockState;
        BlockState normalizedMaterial = normalized == null ? null : blockMaterial;
        CompoundTag normalizedBlockEntityData = normalized == null || blockEntityData == null
                ? null
                : blockEntityData.copy();
        if (Objects.equals(embeddedSlabState, normalized)
                && Objects.equals(embeddedSlabMaterial, normalizedMaterial)
                && Objects.equals(embeddedBlockEntityData, normalizedBlockEntityData)) {
            return;
        }
        embeddedSlabState = normalized;
        embeddedSlabMaterial = normalizedMaterial;
        embeddedBlockEntityData = normalizedBlockEntityData;
        embeddedBlockEntityPreview = null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (!level.isClientSide) {
                sendData();
            }
        }
    }

    // Get the embedded block entity preview
    public @Nullable BlockEntity getEmbeddedBlockEntityPreview() {
        if (embeddedBlockEntityPreview != null) {
            return embeddedBlockEntityPreview;
        }
        if (level == null || embeddedSlabState == null || embeddedBlockEntityData == null) {
            return null;
        }
        embeddedBlockEntityPreview = EmbeddedCopycatBlockSnapshot.createPreview(
                level,
                worldPosition,
                embeddedSlabState,
                embeddedBlockEntityData);
        return embeddedBlockEntityPreview;
    }

    // Get the embedded consumed items
    public List<ItemStack> getEmbeddedConsumedItems() {
        return EmbeddedCopycatBlockSnapshot.consumedItems(getEmbeddedBlockEntityPreview());
    }

    // Check if an embedded block is being restored
    public boolean isRestoringEmbeddedBlock() {
        return restoringEmbeddedBlock;
    }

    // Restore the embedded block
    public boolean restoreEmbeddedBlock() {
        if (level == null || embeddedSlabState == null) {
            return false;
        }

        BlockState restoredState = embeddedSlabState;
        BlockState restoredMaterial = embeddedSlabMaterial;
        CompoundTag restoredBlockEntityData = embeddedBlockEntityData == null ? null : embeddedBlockEntityData.copy();
        restoringEmbeddedBlock = true;
        if (!level.setBlock(worldPosition, restoredState, 3)) {
            restoringEmbeddedBlock = false;
            return false;
        }

        BlockEntity restoredBlockEntity = level.getBlockEntity(worldPosition);
        if (restoredBlockEntityData != null && restoredBlockEntity != null) {
            EmbeddedCopycatBlockSnapshot.reload(restoredBlockEntity, restoredBlockEntityData);
        } else if (restoredMaterial != null && restoredBlockEntity != null) {
            EmbeddedCopycatBlockSnapshot.applyMaterial(restoredBlockEntity, restoredMaterial);
        }
        level.sendBlockUpdated(worldPosition, restoredState, restoredState, 3);
        return true;
    }

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, AnalogueContraptionControllerBlockEntity be) {
        be.tick();
    }

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    // Initialize the analogue contraption controller
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            if (!finalizePendingControllerSchematicImport()) {
                return;
            }
            syncFromInsertedLinker();
            syncTrackedLinkerRegistry();
            refreshLocalSideOutputs(false);
            refreshWirelessNetwork(true);

            for (ControllerLinkTarget target : customWirelessTargets.values()) {
                target.refreshRegistration(level);
            }
            for (ControllerLinkInputTarget target : customInputLinkTargets.values()) {
                target.refreshRegistration(level);
            }

            for (ControllerLinkInputTarget target : inputLinkTargets.values()) {
                target.refreshRegistration(level);
            }
            if (!pendingAssemblyBindingRemap) {
                replayCurrentOutputs();
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the analogue contraption controller
    @Override
    public void tick() {
        if (!isControllerRuntimeLoaded()) {
            return;
        }
        if (!finalizePendingControllerSchematicImport()) {
            return;
        }
        super.tick();

        if (!remapAssemblyBindingsIfReady()) {
            return;
        }
        ControllerRuntimeProgram runtimeProgram = controllerRuntimeProgram();
        long gameTime = level.getGameTime();
        if (runtimeProgram.isRuntimeIdle()
                && !runtimeProgram.needsMaintenanceTick(gameTime,
                lastRuntimeWirelessRegistrationRefresh,
                lastRuntimeLinkerRegistrySync)) {
            return;
        }
        if (runtimeProgram.needsWirelessRefresh(gameTime, lastRuntimeWirelessRegistrationRefresh)) {
            runtimeProgram.refreshWirelessRegistrations();
            lastRuntimeWirelessRegistrationRefresh = gameTime;
        }
        if (runtimeProgram.needsLinkerRegistrySync(gameTime, lastRuntimeLinkerRegistrySync)) {
            syncTrackedLinkerRegistry();
            lastRuntimeLinkerRegistrySync = gameTime;
        }
        boolean changed = false;
        Set<String> changedChannels = new LinkedHashSet<>();
        changed |= runtimeProgram.tickChannels(gameTime, changedChannels);

        changed |= runtimeProgram.sampleDirectInputs(gameTime, changedChannels);
        changed |= runtimeProgram.sampleCustomInputs(gameTime);
        changed |= runtimeProgram.sampleStandardWirelessInputs(gameTime, changedChannels);

        if (runtimeProgram.reconcileWirelessOutputStrengths(changedChannels)) {
            changed = true;
        }
        if (runtimeProgram.reconcileChannelOutputStrengths(changedChannels)) {
            changed = true;
        }
        changed |= runtimeProgram.reconcileCustomEntryOutputStrengths();

        boolean ometerChanged = refreshOmeterOutput();
        changed |= ometerChanged;

        for (AnalogueAxis axis : axes.values()) {
            changed |= axis.tick();
        }

        runtimeProgram.pushChannelSignals(changedChannels);

        changed |= runtimeProgram.refreshLocalSideOutputs(true);

        if (changed) {
            setChanged();
            if (ometerChanged) {
                sendRuntimeData();
            }
        }
    }

    // Check if the controller runtime is loaded
    protected boolean isControllerRuntimeLoaded() {
        return level != null
                && !level.isClientSide
                && !isRemoved();
    }

    // Send the runtime data
    protected void sendRuntimeData() {
        if (level == null || level.isClientSide
                || this instanceof PortableAnalogueContraptionControllerBlockEntity
                || this instanceof PortableAdvancedContraptionControllerBlockEntity) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        Vec3 worldPosition = SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(getBlockPos()));
        ControllerRuntimeSyncPayload payload = new ControllerRuntimeSyncPayload(
                getBlockPos(), subLevelId, ometerOutputSignal);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(level.dimension())) {
                continue;
            }
            if (SimulatedHelper.distanceSquaredWithSubLevels(
                    player.level(), player.position(), worldPosition) <= RUNTIME_SYNC_RANGE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // Apply the client runtime signal
    public void applyClientRuntimeSignal(int signal) {
        if (level != null && level.isClientSide) {
            ometerOutputSignal = clampOmeterSignal(signal);
        }
    }

    // Check if the controller runtime is idle
    protected boolean isControllerRuntimeIdle() {
        return !pendingAssemblyBindingRemap
                && getStoredLinker().isEmpty()
                && !hasBoundFrequencies(frequencyBindings)
                && !hasBoundFrequencies(inputFrequencyBindings)
                && !hasBoundFrequencies(customFrequencyBindings)
                && !hasBoundFrequencies(customInputFrequencyBindings)
                && !hasBoundTargets(directTargets)
                && !hasBoundTargets(inputTargets)
                && !hasLocalOutputSides()
                && customKeyEntries.isEmpty()
                && !hasSampledInputState()
                && !hasChannelRuntimeState()
                && !hasLocalOutputState();
    }

    // Check if this can move with Create contraption
    public boolean canMoveWithCreateContraption() {
        return isControllerRuntimeIdle() && storedTargets.isEmpty();
    }

    // Check if the controller runtime is idle without bindings
    protected boolean isControllerRuntimeIdleIgnoringBindings(Set<String> ignoredBindings) {
        return isControllerRuntimeIdleIgnoringBindings(ignoredBindings, false);
    }

    // Check if the controller runtime is idle without bindings
    protected boolean isControllerRuntimeIdleIgnoringBindings(Set<String> ignoredBindings, boolean ignoreStoredLinker) {
        if (ignoredBindings == null || ignoredBindings.isEmpty()) {
            if (!ignoreStoredLinker) {
                return isControllerRuntimeIdle();
            }
            return !pendingAssemblyBindingRemap
                    && !hasBoundFrequencies(frequencyBindings)
                    && !hasBoundFrequencies(inputFrequencyBindings)
                    && !hasBoundFrequencies(customFrequencyBindings)
                    && !hasBoundFrequencies(customInputFrequencyBindings)
                    && !hasBoundTargets(directTargets)
                    && !hasBoundTargets(inputTargets)
                    && !hasLocalOutputSides()
                    && customKeyEntries.isEmpty()
                    && !hasSampledInputState()
                    && !hasChannelRuntimeState()
                    && !hasLocalOutputState();
        }
        return !pendingAssemblyBindingRemap
                && (ignoreStoredLinker || getStoredLinker().isEmpty())
                && !hasBoundFrequencies(frequencyBindings, ignoredBindings)
                && !hasBoundFrequencies(inputFrequencyBindings, ignoredBindings)
                && !hasBoundFrequencies(customFrequencyBindings, ignoredBindings)
                && !hasBoundFrequencies(customInputFrequencyBindings, ignoredBindings)
                && !hasBoundTargets(directTargets, ignoredBindings)
                && !hasBoundTargets(inputTargets, ignoredBindings)
                && !hasLocalOutputSides(ignoredBindings)
                && !hasCustomKeysOutside(ignoredBindings)
                && !hasSampledInputState(ignoredBindings)
                && !hasChannelRuntimeState(ignoredBindings)
                && !hasLocalOutputState(ignoredBindings);
    }

    // Check if this has bound frequencies
    private static boolean hasBoundFrequencies(Map<String, FrequencyBinding> bindings) {
        for (FrequencyBinding binding : bindings.values()) {
            if (binding != null && binding.isBound()) {
                return true;
            }
        }
        return false;
    }

    // Check if this has bound frequencies
    private static boolean hasBoundFrequencies(Map<String, FrequencyBinding> bindings, Set<String> ignoredBindings) {
        for (Map.Entry<String, FrequencyBinding> entry : bindings.entrySet()) {
            if (ignoredBindings.contains(entry.getKey())) {
                continue;
            }
            FrequencyBinding binding = entry.getValue();
            if (binding != null && binding.isBound()) {
                return true;
            }
        }
        return false;
    }

    // Check if this has bound targets
    private static boolean hasBoundTargets(Map<String, ControllerDirectTargetReference> targets) {
        for (ControllerDirectTargetReference target : targets.values()) {
            if (target != null && target.isBound() && target.blockPos() != null) {
                return true;
            }
        }
        return false;
    }

    // Check if this has bound targets
    private static boolean hasBoundTargets(Map<String, ControllerDirectTargetReference> targets, Set<String> ignoredBindings) {
        for (Map.Entry<String, ControllerDirectTargetReference> entry : targets.entrySet()) {
            if (ignoredBindings.contains(entry.getKey())) {
                continue;
            }
            ControllerDirectTargetReference target = entry.getValue();
            if (target != null && target.isBound() && target.blockPos() != null) {
                return true;
            }
        }
        return false;
    }

    // Check if this has local output sides
    private boolean hasLocalOutputSides() {
        for (Direction dir : localOutputSides.values()) {
            if (dir != null) {
                return true;
            }
        }
        return false;
    }

    // Check if this has local output sides
    private boolean hasLocalOutputSides(Set<String> ignoredBindings) {
        for (Map.Entry<String, Direction> entry : localOutputSides.entrySet()) {
            if (!ignoredBindings.contains(entry.getKey()) && entry.getValue() != null) {
                return true;
            }
        }
        return false;
    }

    // Check for custom keys outside the ignored bindings
    private boolean hasCustomKeysOutside(Set<String> ignoredBindings) {
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry != null && !ignoredBindings.contains(entry.id())) {
                return true;
            }
        }
        return false;
    }

    // Check if this has sampled input state
    private boolean hasSampledInputState() {
        for (Boolean active : sampledInputActive.values()) {
            if (Boolean.TRUE.equals(active)) {
                return true;
            }
        }
        return !sampledCustomEntryIds.isEmpty()
                || !sampledCustomEntryValues.isEmpty()
                || !pressedCustomEntryIds.isEmpty()
                || !pressedCustomEntryStepDownIds.isEmpty();
    }

    // Check if this has sampled input state
    private boolean hasSampledInputState(Set<String> ignoredBindings) {
        for (Map.Entry<String, Boolean> entry : sampledInputActive.entrySet()) {
            if (!ignoredBindings.contains(entry.getKey()) && Boolean.TRUE.equals(entry.getValue())) {
                return true;
            }
        }
        return containsAnyOutside(sampledCustomEntryIds, ignoredBindings)
                || containsAnyOutside(sampledCustomEntryValues.keySet(), ignoredBindings)
                || containsAnyOutside(pressedCustomEntryIds, ignoredBindings)
                || containsAnyOutside(pressedCustomEntryStepDownIds, ignoredBindings);
    }

    // Check if this has channel runtime state
    private boolean hasChannelRuntimeState() {
        for (AnalogueChannel channel : channels.values()) {
            if (channel != null && (channel.isPressed() || channel.getRedstoneStrength() > 0
                    || Math.abs(channel.getUnsignedValue()) > 1.0E-4D)) {
                return true;
            }
        }
        for (AnalogueAxis axis : axes.values()) {
            if (axis != null && Math.abs(axis.getSignedValue()) > 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    // Check if this has channel runtime state
    private boolean hasChannelRuntimeState(Set<String> ignoredBindings) {
        for (Map.Entry<String, AnalogueChannel> entry : channels.entrySet()) {
            if (ignoredBindings.contains(entry.getKey())) {
                continue;
            }
            AnalogueChannel channel = entry.getValue();
            if (channel != null && (channel.isPressed() || channel.getRedstoneStrength() > 0
                    || Math.abs(channel.getUnsignedValue()) > 1.0E-4D)) {
                return true;
            }
        }
        for (AnalogueAxis axis : axes.values()) {
            if (axis != null && Math.abs(axis.getSignedValue()) > 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    // Check if this has local output state
    private boolean hasLocalOutputState() {
        for (Integer strength : localOutputs.values()) {
            if (strength != null && strength > 0) {
                return true;
            }
        }
        for (Integer strength : lastChannelOutputStrengths.values()) {
            if (strength != null && strength > 0) {
                return true;
            }
        }
        for (Integer strength : lastCustomEntryOutputStrengths.values()) {
            if (strength != null && strength > 0) {
                return true;
            }
        }
        return ometerOutputSignal > 0;
    }

    // Check if this has local output state
    private boolean hasLocalOutputState(Set<String> ignoredBindings) {
        for (Map.Entry<Direction, Integer> entry : localOutputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                return true;
            }
        }
        for (Map.Entry<String, Integer> entry : lastChannelOutputStrengths.entrySet()) {
            if (!ignoredBindings.contains(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0) {
                return true;
            }
        }
        for (Map.Entry<String, Integer> entry : lastCustomEntryOutputStrengths.entrySet()) {
            if (!ignoredBindings.contains(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0) {
                return true;
            }
        }
        return ometerOutputSignal > 0;
    }

    // Check if any value falls outside the ignored bindings
    private static boolean containsAnyOutside(Iterable<String> values, Set<String> ignoredBindings) {
        for (String val : values) {
            if (!ignoredBindings.contains(val)) {
                return true;
            }
        }
        return false;
    }

    // Reconcile the wireless output strengths
    private boolean reconcileWirelessOutputStrengths(List<String> changedChannels) {
        boolean changed = false;
        for (Map.Entry<String, ControllerLinkTarget> entry : wirelessTargets.entrySet()) {
            String channelId = entry.getKey();
            ControllerLinkTarget target = entry.getValue();
            AnalogueChannel channel = channels.get(channelId);
            if (target == null || channel == null) {
                continue;
            }
            int expected = channel.getRedstoneStrength();
            if (target.getTransmittedStrength() != expected) {
                changed = true;
                if (!changedChannels.contains(channelId)) {
                    changedChannels.add(channelId);
                }
            }
        }
        return changed;
    }

    // Get the current wireless signal
    private int currentWirelessSignal(Level linkLevel, ControllerLinkInputTarget receiver) {
        if (linkLevel == null || receiver == null || !receiver.binding().isBound()) {
            return 0;
        }
        Set<IRedstoneLinkable> network = Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(linkLevel, receiver);
        if (network == null || network.isEmpty()) {
            return 0;
        }
        int linkRange = AllConfigs.server().logistics.linkRange.get();
        double linkRangeSq = linkRange * (double) linkRange;
        int maxSignal = 0;
        for (IRedstoneLinkable candidate : network) {
            if (candidate == null || candidate == receiver || !candidate.isAlive()) {
                continue;
            }
            int transmittedStrength = net.minecraft.util.Mth.clamp(candidate.getTransmittedStrength(), 0, 15);
            if (transmittedStrength <= 0) {
                continue;
            }
            BlockPos candidateLocation = candidate.getLocation();
            BlockPos receiverLocation = receiver.getLocation();
            if (candidateLocation == null || receiverLocation == null) {
                continue;
            }
            double distanceSq = SimulatedHelper.distanceSquaredWithSubLevels(linkLevel,
                    Vec3.atCenterOf(candidateLocation), Vec3.atCenterOf(receiverLocation));
            if (distanceSq >= linkRangeSq) {
                continue;
            }
            maxSignal = Math.max(maxSignal, transmittedStrength);
            if (maxSignal >= 15) {
                break;
            }
        }
        return maxSignal;
    }

    // Get the list channel ids
    public List<String> listChannelIds() {
        return List.copyOf(channels.keySet());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         CONTROL INPUTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the list axis ids
    public List<String> listAxisIds() {
        return List.copyOf(axes.keySet());
    }

    // Press the channel
    public boolean pressChannel(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null || level == null) {
            return false;
        }
        boolean changed = channel.press(level.getGameTime());
        if (changed) {
            markChannelRuntimeDirty(channelId);
        }
        return changed;
    }

    // Reconcile the channel output strengths
    private boolean reconcileChannelOutputStrengths(List<String> changedChannels) {
        boolean changed = false;
        for (Map.Entry<String, AnalogueChannel> entry : channels.entrySet()) {
            String channelId = entry.getKey();
            int currentStrength = directOutputStrength(entry.getValue());
            Integer previousStrength = lastChannelOutputStrengths.put(channelId, currentStrength);
            if (previousStrength == null || previousStrength == currentStrength) {
                continue;
            }
            changed = true;
            if (!changedChannels.contains(channelId)) {
                changedChannels.add(channelId);
            }
        }
        return changed;
    }

    // Cache the channel output
    private void cacheChannelOutput(String channelId) {
        AnalogueChannel channel = channels.get(normalizeName(channelId));
        if (channel != null) {
            lastChannelOutputStrengths.put(normalizeName(channelId), directOutputStrength(channel));
        }
    }

    // Get the direct output strength
    private static int directOutputStrength(AnalogueChannel channel) {
        if (channel == null) {
            return 0;
        }
        return net.minecraft.util.Mth.clamp(
                net.minecraft.util.Mth.ceil(channel.getUnsignedValue() * 15.0D),
                0,
                15);
    }

    // Reconcile the custom entry output strengths
    private boolean reconcileCustomEntryOutputStrengths() {
        boolean changed = false;
        Set<String> activeEntryIds = new HashSet<>();
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            String entryId = entry.id();
            activeEntryIds.add(entryId);
            int currentStrength = customOutput(entryId);
            Integer previousStrength = lastCustomEntryOutputStrengths.put(entryId, currentStrength);
            if (previousStrength == null || previousStrength == currentStrength) {
                continue;
            }
            pushCustomEntrySignal(entryId, resolveCustomEntryOutputValue(entryId));
            changed = true;
        }
        lastCustomEntryOutputStrengths.keySet().removeIf(id -> !activeEntryIds.contains(id));
        return changed;
    }

    // Cache the custom output
    private void cacheCustomOutput(String id) {
        if (id != null && !id.isBlank()) {
            lastCustomEntryOutputStrengths.put(id, customOutput(id));
        }
    }

    // Get the custom output
    private int customOutput(String id) {
        return net.minecraft.util.Mth.clamp(
                net.minecraft.util.Mth.ceil(resolveCustomEntryOutputValue(id) * 15.0f),
                0,
                15);
    }

    // Release the channel
    public boolean releaseChannel(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null || level == null) {
            return false;
        }
        boolean changed = channel.release(level.getGameTime());
        if (changed) {
            markChannelRuntimeDirty(channelId);
        }
        return changed;
    }

    // Tap the analogue channel
    public boolean tapChannel(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null || level == null) {
            return false;
        }
        boolean changed = channel.tap(level.getGameTime());
        if (changed) {
            markChannelRuntimeDirty(channelId);
        }
        return changed;
    }

    // Press the bound key
    public int pressBoundKey(int keyCode) {
        return dispatchBoundKey(keyCode, BoundKeyAction.PRESS, 0.0D);
    }

    // Release the bound key
    public int releaseBoundKey(int keyCode) {
        return dispatchBoundKey(keyCode, BoundKeyAction.RELEASE, 0.0D);
    }

    // Get the tap bound key
    public int tapBoundKey(int keyCode) {
        return dispatchBoundKey(keyCode, BoundKeyAction.TAP, 0.0D);
    }

    // Set the bound key exact value
    public int setBoundKeyExactValue(int keyCode, double value) {
        return dispatchBoundKey(keyCode, BoundKeyAction.SET_VALUE, value);
    }

    // Count the bound key targets
    public int countBoundKeyTargets(int keyCode) {
        if (keyCode < 0) {
            return 0;
        }
        int count = 0;
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            if (getKeyBinding(channel.id()) == keyCode) {
                count++;
            }
        }
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            if (entry.keyCode == keyCode) {
                count++;
            }
            if (entry.stepDownKeyCode == keyCode) {
                count++;
            }
        }
        return count;
    }

    // Handle the controller key input
    public boolean handleControllerKeyInput(String channelId, boolean pressed) {
        if (channelId == null || channelId.isBlank()) {
            return false;
        }
        if (pressed) {
            if (channelId.endsWith("#step_down")) {
                return pressCustomEntryStepDown(channelId.substring(0, channelId.length() - "#step_down".length()));
            }
            return pressChannel(channelId) || pressCustomEntry(channelId);
        }
        if (channelId.endsWith("#step_down")) {
            return releaseCustomEntryStepDown(channelId.substring(0, channelId.length() - "#step_down".length()));
        }
        return releaseChannel(channelId) || releaseCustomEntry(channelId);
    }

    // Apply the client key animation
    public void applyClientKeyAnimation(String channelId, boolean pressed) {
        if (level == null || !level.isClientSide || channelId == null || channelId.isBlank()) {
            return;
        }
        boolean stepDown = channelId.endsWith("#step_down");
        String resolvedId = stepDown
                ? channelId.substring(0, channelId.length() - "#step_down".length())
                : channelId;
        AnalogueChannel channel = channels.get(normalizeName(resolvedId));
        if (channel != null) {
            if (pressed) {
                channel.press(level.getGameTime());
            } else {
                channel.release(level.getGameTime());
            }
            channel.tick(level.getGameTime());
            return;
        }

        CustomKeyEntry entry = findCustomEntry(resolvedId);
        if (entry == null) {
            return;
        }
        channel = ensureCustomEntryChannel(entry);
        if (channel == null) {
            return;
        }
        if (stepDown) {
            if (pressed) {
                pressedCustomEntryStepDownIds.add(resolvedId);
                if (channel.getMode() == AnalogueChannelMode.STEP) {
                    channel.stepBy(-Math.abs(entry.stepDownAmount), level.getGameTime());
                } else {
                    channel.press(level.getGameTime());
                }
            } else {
                pressedCustomEntryStepDownIds.remove(resolvedId);
                if (channel.getMode() != AnalogueChannelMode.STEP
                        && !pressedCustomEntryIds.contains(resolvedId)) {
                    channel.release(level.getGameTime());
                }
            }
        } else if (pressed) {
            pressedCustomEntryIds.add(resolvedId);
            channel.press(level.getGameTime());
        } else {
            pressedCustomEntryIds.remove(resolvedId);
            if (!pressedCustomEntryStepDownIds.contains(resolvedId)) {
                channel.release(level.getGameTime());
            }
        }
        channel.tick(level.getGameTime());
    }

    // Update the client animation
    public void tickClientAnimation() {
        if (level == null || !level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        for (AnalogueChannel channel : channels.values()) {
            channel.tick(gameTime);
        }
        for (AnalogueChannel channel : customEntryChannels.values()) {
            channel.tick(gameTime);
        }
        for (AnalogueAxis axis : axes.values()) {
            axis.tick();
        }
    }

    // Dispatch the bound key
    private int dispatchBoundKey(int keyCode, BoundKeyAction action, double val) {
        if (keyCode < 0 || level == null || level.isClientSide) {
            return 0;
        }
        int targets = 0;
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            String channelId = channel.id();
            if (getKeyBinding(channelId) != keyCode) {
                continue;
            }
            targets++;
            switch (action) {
                case PRESS -> handleControllerKeyInput(channelId, true);
                case RELEASE -> handleControllerKeyInput(channelId, false);
                case TAP -> tapChannel(channelId);
                case SET_VALUE -> setChannelExactValue(channelId, val);
            }
        }
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            if (entry.keyCode == keyCode) {
                targets++;
                switch (action) {
                    case PRESS -> handleControllerKeyInput(entry.id(), true);
                    case RELEASE -> handleControllerKeyInput(entry.id(), false);
                    case TAP -> tapCustomEntry(entry.id());
                    case SET_VALUE -> setCustomEntryExactValue(entry.id(), val);
                }
            }
            if (entry.stepDownKeyCode == keyCode) {
                targets++;
                String stepDownChannelId = entry.id() + "#step_down";
                switch (action) {
                    case PRESS -> handleControllerKeyInput(stepDownChannelId, true);
                    case RELEASE -> handleControllerKeyInput(stepDownChannelId, false);
                    case TAP -> tapCustomEntryStepDown(entry.id());
                    case SET_VALUE -> setCustomEntryExactValue(entry.id(), val);
                }
            }
        }
        return targets;
    }

    // Define the bound key action values
    private enum BoundKeyAction {
        PRESS,
        RELEASE,
        TAP,
        SET_VALUE
    }

    // Reset the channel
    public void resetChannel(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null) {
            return;
        }
        channel.reset();
        markChannelRuntimeDirty(channelId);
    }

    // Reset every controller channel
    public void resetAllChannels() {
        for (String channelId : channels.keySet()) {
            channels.get(channelId).reset();
            pushChannelSignal(channelId);
            cacheChannelOutput(channelId);
        }
        for (AnalogueAxis axis : axes.values()) {
            axis.tick();
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
    }

    // Get the channel
    public @Nullable AnalogueChannel getChannel(String channelId) {
        return channels.get(normalizeName(channelId));
    }

    // Check if the channel is active
    public boolean isChannelActive(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null) {
            return false;
        }
        return channel.isPressed()
                || sampledInputActive.getOrDefault(normalizeName(channelId), false)
                || channel.getUnsignedValue() > 1.0E-4D;
    }

    // Check if the channel is pressed
    public boolean isChannelPressed(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        return channel != null && channel.isPressed();
    }

    // Get the channel value
    public double getChannelValue(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        return channel == null ? 0.0D : channel.getUnsignedValue();
    }

    // Get the channel redstone strength
    public int getChannelRedstoneStrength(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        return channel == null ? 0 : channel.getRedstoneStrength();
    }

    // Get the graph binding value
    public double getGraphBindingValue(String bindingId) {
        if (HardwareControllerBindings.isHardwareBinding(bindingId)) {
            return hardwareControllerInputs.getOrDefault(bindingId, 0.0D);
        }
        AnalogueChannel channel = getChannel(bindingId);
        if (channel != null) {
            return channel.getUnsignedValue();
        }
        return getCustomEntryValue(bindingId);
    }

    // Get the ometer output signal
    public int getOmeterOutputSignal() {
        return level == null || level.isClientSide ? ometerOutputSignal : getOmeterOutput();
    }

    // Get the ometer output signal text
    public String getOmeterOutputSignalText() {
        return String.format(Locale.ROOT, "%02d", getOmeterOutputSignal());
    }

    // Refresh the ometer output
    private boolean refreshOmeterOutput() {
        int nextSignal = getOmeterOutput();
        if (ometerOutputSignal == nextSignal) {
            return false;
        }
        ometerOutputSignal = nextSignal;
        return true;
    }

    // Get the ometer output
    private int getOmeterOutput() {
        double strongest = 0.0D;
        for (String channelId : channels.keySet()) {
            strongest = Math.max(strongest, clampUnit(getGraphBindingValue(channelId)));
        }
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            strongest = Math.max(strongest, clampUnit(getGraphBindingValue(entry.id())));
        }
        return ometerSignalForStrength(strongest);
    }

    // Check if the graph binding is active
    public boolean isGraphBindingActive(String bindingId) {
        if (HardwareControllerBindings.isHardwareBinding(bindingId)) {
            return Math.abs(hardwareControllerInputs.getOrDefault(bindingId, 0.0D)) > 1.0E-4D;
        }
        return isChannelActive(bindingId) || isCustomEntryActive(bindingId);
    }

    // Apply the hardware controller input
    public void applyHardwareControllerInput(Map<String, Double> values) {
        Map<String, Double> next = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (!HardwareControllerBindings.isHardwareBinding(entry.getKey())) {
                    continue;
                }
                double val = entry.getValue() == null || !Double.isFinite(entry.getValue())
                        ? 0.0D : Mth.clamp(entry.getValue(), -1.0D, 1.0D);
                next.put(entry.getKey(), val);
            }
        }
        if (hardwareControllerInputs.equals(next)) {
            return;
        }
        hardwareControllerInputs.clear();
        hardwareControllerInputs.putAll(next);
        for (Map.Entry<String, Double> channel :
                HardwareControllerBindings.conventionalChannels(hardwareControllerInputs).entrySet()) {
            if (Math.abs(getChannelValue(channel.getKey()) - channel.getValue()) > 1.0E-4D) {
                setChannelExactValue(channel.getKey(), channel.getValue());
            }
        }
    }

    // Get the hardware controller inputs
    public Map<String, Double> getHardwareControllerInputs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(hardwareControllerInputs));
    }

    // Clear the hardware controller input
    public void clearHardwareControllerInput() {
        applyHardwareControllerInput(Map.of());
    }

    // Set the graph binding value
    public void setGraphBindingValue(String bindingId, double value) {
        if (getChannel(bindingId) != null) {
            setChannelExactValue(bindingId, value);
        } else {
            setCustomEntryExactValue(bindingId, value);
        }
    }

    // Reset the graph bindings
    public void resetGraphBindings() {
        resetAllChannels();
        for (CustomKeyEntry entry : customKeyEntries) {
            resetCustomEntry(entry.id());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         GRAPH BINDINGS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the graph binding values
    public void setGraphBindingValues(Map<String, Double> values, boolean resetMissing) {
        if (level == null || level.isClientSide) {
            return;
        }
        // ------------------------------------VALUE NORMALIZATION------------------------------------
        Map<String, Double> normalizedValues = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                String bindingId = normalizeName(entry.getKey());
                if (!bindingId.isBlank()) {
                    normalizedValues.put(bindingId, entry.getValue() == null ? 0.0D : entry.getValue());
                }
            }
        }

        // ------------------------------------BINDING UPDATES------------------------------------
        Set<String> changedChannels = new LinkedHashSet<>();
        Set<String> changedCustomEntries = new LinkedHashSet<>();
        for (Map.Entry<String, Double> entry : normalizedValues.entrySet()) {
            String bindingId = entry.getKey();
            AnalogueChannel channel = channels.get(bindingId);
            if (channel != null) {
                if (Math.abs(channel.getUnsignedValue() - entry.getValue()) <= 1.0E-4D) {
                    continue;
                }
                channel.setValueInstant(entry.getValue());
                changedChannels.add(bindingId);
                continue;
            }

            CustomKeyEntry customEntry = findCustomEntry(bindingId);
            if (customEntry == null) {
                continue;
            }
            AnalogueChannel customChannel = ensureCustomEntryChannel(customEntry);
            if (customChannel == null) {
                continue;
            }
            float clamped = net.minecraft.util.Mth.clamp(entry.getValue().floatValue(), 0.0f, 1.0f);
            if (Math.abs(resolveCustomEntryOutputValue(bindingId) - clamped) <= 1.0E-4f) {
                continue;
            }
            pressedCustomEntryIds.remove(bindingId);
            pressedCustomEntryStepDownIds.remove(bindingId);
            sampledCustomEntryIds.add(bindingId);
            sampledCustomEntryValues.put(bindingId, clamped);
            customChannel.setValueInstant(clamped);
            changedCustomEntries.add(bindingId);
        }

        // ------------------------------------MISSING BINDINGS------------------------------------
        if (resetMissing) {
            for (Map.Entry<String, AnalogueChannel> entry : channels.entrySet()) {
                if (normalizedValues.containsKey(entry.getKey())) {
                    continue;
                }
                if (Math.abs(entry.getValue().getUnsignedValue()) <= 1.0E-4D
                        && !entry.getValue().isPressed()) {
                    continue;
                }
                entry.getValue().reset();
                changedChannels.add(entry.getKey());
            }
            for (CustomKeyEntry entry : customKeyEntries) {
                if (entry == null || normalizedValues.containsKey(entry.id())) {
                    continue;
                }
                AnalogueChannel channel = ensureCustomEntryChannel(entry);
                if ((channel == null || (Math.abs(channel.getUnsignedValue()) <= 1.0E-4D
                        && !channel.isPressed()))
                        && !sampledCustomEntryIds.contains(entry.id())
                        && Math.abs(sampledCustomEntryValues.getOrDefault(entry.id(), 0.0f)) <= 1.0E-4f) {
                    continue;
                }
                if (channel != null) {
                    channel.reset();
                }
                pressedCustomEntryIds.remove(entry.id());
                pressedCustomEntryStepDownIds.remove(entry.id());
                sampledCustomEntryIds.remove(entry.id());
                sampledCustomEntryValues.remove(entry.id());
                changedCustomEntries.add(entry.id());
            }
        }

        if (changedChannels.isEmpty() && changedCustomEntries.isEmpty()) {
            return;
        }
        // -----------------------------------------------------OUTPUT SYNC-----------------------------------------------------
        for (String channelId : changedChannels) {
            pushChannelSignal(channelId);
            cacheChannelOutput(channelId);
        }
        for (String entryId : changedCustomEntries) {
            pushCustomEntrySignal(entryId, resolveCustomEntryOutputValue(entryId));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
    }

    // Reset the graph bindings batch
    public void resetGraphBindingsBatch() {
        setGraphBindingValues(Map.of(), true);
    }

    // Deactivate the portable outputs
    public void deactivatePortableOutputs() {
        if (level == null || level.isClientSide) {
            return;
        }
        clearHardwareControllerInput();
        resetGraphBindings();
        for (ControllerLinkTarget target : wirelessTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkTarget target : customWirelessTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkInputTarget target : inputLinkTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkInputTarget target : customInputLinkTargets.values()) {
            target.unregister(level);
        }
    }

    // Get the axis
    public @Nullable AnalogueAxis getAxis(String axisId) {
        return axes.get(normalizeName(axisId));
    }

    // Get the frequency binding
    public @Nullable FrequencyBinding getFrequencyBinding(String channelId) {
        return frequencyBindings.get(normalizeName(channelId));
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(String channelId) {
        FrequencyBinding binding = getFrequencyBinding(channelId);
        return binding == null ? ItemStack.EMPTY : binding.first();
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(String channelId) {
        FrequencyBinding binding = getFrequencyBinding(channelId);
        return binding == null ? ItemStack.EMPTY : binding.second();
    }

    // Get the input frequency binding
    public @Nullable FrequencyBinding getInputFrequencyBinding(String channelId) {
        return inputFrequencyBindings.get(normalizeName(channelId));
    }

    // Get the input frequency first
    public ItemStack getInputFrequencyFirst(String channelId) {
        FrequencyBinding binding = getInputFrequencyBinding(channelId);
        return binding == null ? ItemStack.EMPTY : binding.first();
    }

    // Get the input frequency second
    public ItemStack getInputFrequencySecond(String channelId) {
        FrequencyBinding binding = getInputFrequencyBinding(channelId);
        return binding == null ? ItemStack.EMPTY : binding.second();
    }

    // Get the local output side
    public @Nullable Direction getLocalOutputSide(String channelId) {
        return localOutputSides.get(normalizeName(channelId));
    }

    // Get the direct target
    public @Nullable ControllerDirectTargetReference getDirectTarget(String channelId) {
        return directTargets.get(normalizeName(channelId));
    }

    // Get the input target
    public @Nullable ControllerDirectTargetReference getInputTarget(String channelId) {
        return inputTargets.get(normalizeName(channelId));
    }

    // Get the key binding
    public int getKeyBinding(String channelId) {
        return keyBindings.getOrDefault(normalizeName(channelId), -1);
    }

    // Get the binding mode
    public String getBindingMode(String channelId) {
        return bindingModes.getOrDefault(normalizeName(channelId), "standard");
    }

    // Get the binding preset
    public String getBindingPreset(String channelId) {
        return bindingPresets.getOrDefault(normalizeName(channelId), "none");
    }

    // Get the custom key entries
    public List<CustomKeyEntry> getCustomKeyEntries() {
        return customKeyEntriesView;
    }

    // Add the custom key entry
    public String addCustomKeyEntry() {
        CustomKeyEntry entry = CustomKeyEntry.createNew();
        customKeyEntries.add(entry);
        FrequencyBinding binding = new FrequencyBinding(entry.id());
        customFrequencyBindings.put(entry.id(), binding);
        customWirelessTargets.put(entry.id(), new ControllerLinkTarget(entry.id(), binding));
        FrequencyBinding inputBinding = new FrequencyBinding(entry.id() + "#input");
        customInputFrequencyBindings.put(entry.id(), inputBinding);
        customInputLinkTargets.put(entry.id(), new ControllerLinkInputTarget(entry.id(), inputBinding));
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
        return entry.id();
    }

    // Add the custom key entry with id
    public void addCustomKeyEntryWithId(String id) {
        if (findCustomEntry(id) != null) return;
        CustomKeyEntry entry = new CustomKeyEntry(id);
        customKeyEntries.add(entry);
        ensureCustomEntryChannel(entry);
        FrequencyBinding binding = new FrequencyBinding(entry.id());
        customFrequencyBindings.put(entry.id(), binding);
        customWirelessTargets.put(entry.id(), new ControllerLinkTarget(entry.id(), binding));
        FrequencyBinding inputBinding = new FrequencyBinding(entry.id() + "#input");
        customInputFrequencyBindings.put(entry.id(), inputBinding);
        customInputLinkTargets.put(entry.id(), new ControllerLinkInputTarget(entry.id(), inputBinding));
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Remove the custom key entry
    public void removeCustomKeyEntry(String id) {
        removeCustomKey(id, true, true, true);
    }

    // Remove the custom key
    private void removeCustomKey(String id, boolean syncInsertedLinker, boolean notify) {
        removeCustomKey(id, syncInsertedLinker, notify, true);
    }

    // Remove the custom key
    private void removeCustomKey(String id, boolean syncInsertedLinker, boolean notify, boolean clearOutputSignal) {
        CustomKeyEntry removedEntry = findCustomEntry(id);
        if (clearOutputSignal) {

            clearDirectOutputSignalAndSiblings(removedEntry == null ? null : removedEntry.directTarget, id, true);
        }
        customKeyEntries.removeIf(e -> e.id().equals(id));
        customEntryChannels.remove(id);
        pressedCustomEntryIds.remove(id);
        pressedCustomEntryStepDownIds.remove(id);
        sampledCustomEntryIds.remove(id);
        sampledCustomEntryValues.remove(id);
        ControllerLinkTarget target = customWirelessTargets.remove(id);
        if (target != null && level != null && !level.isClientSide) {
            target.unregister(level);
        }
        ControllerLinkInputTarget inputTarget = customInputLinkTargets.remove(id);
        if (inputTarget != null && level != null && !level.isClientSide) {
            inputTarget.unregister(level);
        }
        customFrequencyBindings.remove(id);
        customInputFrequencyBindings.remove(id);
        if (syncInsertedLinker) {
            removeLinkerBinding(id);
        }
        invalidateControllerRuntimeProgram();
        if (notify) {
            setChanged();
            sendData();
        }
    }

    // Apply the custom key entry
    public void applyCustomKeyEntry(String id, int keyCode, int stepDownKeyCode, String label,
                                    AnalogueChannelMode mode, double riseRate, double fallRate,
                                    double stepAmount, double stepDownAmount, double deadzone, double smoothing,
                                    Direction localOutputSide, ItemStack first, ItemStack second,
                                    ItemStack inputFirst, ItemStack inputSecond,
                                    ControllerDirectTargetReference directTarget,
                                    ControllerDirectTargetReference inputTarget,
                                    String bindingPreset) {
        applyCustomKeyEntry(id, keyCode, stepDownKeyCode, label, mode,
                riseRate, fallRate, stepAmount, stepDownAmount, deadzone, smoothing,
                localOutputSide, first, second, inputFirst, inputSecond,
                directTarget, inputTarget, bindingPreset, ControllerBindingOwner.USER);
    }

    // Apply the owned custom key entry
    public void applyCustomKeyEntry(String id, int keyCode, int stepDownKeyCode, String label,
                                    AnalogueChannelMode mode, double riseRate, double fallRate,
                                    double stepAmount, double stepDownAmount, double deadzone, double smoothing,
                                    Direction localOutputSide, ItemStack first, ItemStack second,
                                    ItemStack inputFirst, ItemStack inputSecond,
                                    ControllerDirectTargetReference directTarget,
                                    ControllerDirectTargetReference inputTarget,
                                    String bindingPreset,
                                    ControllerBindingOwner owner) {
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) {
            entry = new CustomKeyEntry(id);
            customKeyEntries.add(entry);
            ensureCustomEntryChannel(entry);
            FrequencyBinding binding = new FrequencyBinding(id);
            customFrequencyBindings.put(id, binding);
            customWirelessTargets.put(id, new ControllerLinkTarget(id, binding));
            FrequencyBinding inputBinding = new FrequencyBinding(id + "#input");
            customInputFrequencyBindings.put(id, inputBinding);
            customInputLinkTargets.put(id, new ControllerLinkInputTarget(id, inputBinding));
        }
        entry.owner = owner == null ? ControllerBindingOwner.USER : owner;
        entry.keyCode = keyCode < 0 ? -1 : keyCode;
        entry.stepDownKeyCode = stepDownKeyCode < 0 ? -1 : stepDownKeyCode;
        entry.label = (label == null || label.isBlank()) ? "Custom" : label;
        entry.mode = mode == null ? AnalogueChannelMode.RAMP : mode;
        entry.riseRate = riseRate;
        entry.fallRate = fallRate;
        entry.stepAmount = stepAmount;
        entry.stepDownAmount = stepDownAmount;
        entry.deadzone = deadzone;
        entry.smoothing = smoothing;
        entry.localOutputSide = localOutputSide;
        entry.directTarget = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(directTarget));
        entry.inputTarget = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(inputTarget));
        entry.bindingPreset = bindingPreset == null || bindingPreset.isBlank() ? "none" : bindingPreset;
        ItemStack sanitizedFirst = copyMenuStack(first == null ? ItemStack.EMPTY : first);
        ItemStack sanitizedSecond = copyMenuStack(second == null ? ItemStack.EMPTY : second);
        FrequencyBinding binding = customFrequencyBindings.get(id);
        if (binding != null) {
            boolean changed = !ItemStack.isSameItemSameComponents(binding.first(), sanitizedFirst)
                    || !ItemStack.isSameItemSameComponents(binding.second(), sanitizedSecond);
            binding.set(sanitizedFirst, sanitizedSecond);
            entry.first = binding.first();
            entry.second = binding.second();
            ControllerLinkTarget target = customWirelessTargets.get(id);
            if (changed && target != null && level != null && !level.isClientSide) {
                target.updateBinding(level, binding);
            }
        } else {
            entry.first = sanitizedFirst;
            entry.second = sanitizedSecond;
        }

        ItemStack sanitizedInputFirst = copyMenuStack(inputFirst == null ? ItemStack.EMPTY : inputFirst);
        ItemStack sanitizedInputSecond = copyMenuStack(inputSecond == null ? ItemStack.EMPTY : inputSecond);
        FrequencyBinding inputBinding = customInputFrequencyBindings.get(id);
        if (inputBinding != null) {
            boolean inputChanged = !ItemStack.isSameItemSameComponents(inputBinding.first(), sanitizedInputFirst)
                    || !ItemStack.isSameItemSameComponents(inputBinding.second(), sanitizedInputSecond);
            inputBinding.set(sanitizedInputFirst, sanitizedInputSecond);
            entry.inputFirst = inputBinding.first();
            entry.inputSecond = inputBinding.second();
            ControllerLinkInputTarget inputLinkTarget = customInputLinkTargets.get(id);
            if (inputChanged && inputLinkTarget != null && level != null && !level.isClientSide) {
                inputLinkTarget.updateBinding(level, inputBinding);
            }
        } else {
            entry.inputFirst = sanitizedInputFirst;
            entry.inputSecond = sanitizedInputSecond;
        }
        ensureCustomEntryChannel(entry);
        saveLinkerBinding(entry);

        syncCustomFanout(entry);
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Sync the custom fanout
    private void syncCustomFanout(CustomKeyEntry src) {
        if (src == null) {
            return;
        }
        for (CustomKeyEntry sibling : customKeyEntries) {
            if (sibling == null || Objects.equals(sibling.id(), src.id())) {
                continue;
            }
            if (!shareCustomInputBinding(src, sibling)) {
                continue;
            }
            if (!copyCustomResponse(src, sibling)) {
                continue;
            }
            ensureCustomEntryChannel(sibling);
            saveLinkerBinding(sibling);
        }
    }

    // Copy the custom response
    private boolean copyCustomResponse(CustomKeyEntry src, CustomKeyEntry target) {
        boolean changed = target.mode != src.mode
                || Double.compare(target.riseRate, src.riseRate) != 0
                || Double.compare(target.fallRate, src.fallRate) != 0
                || Double.compare(target.stepAmount, src.stepAmount) != 0
                || Double.compare(target.stepDownAmount, src.stepDownAmount) != 0
                || Double.compare(target.deadzone, src.deadzone) != 0
                || Double.compare(target.smoothing, src.smoothing) != 0
                || !Objects.equals(target.bindingPreset, src.bindingPreset);
        if (!changed) {
            return false;
        }
        target.mode = src.mode;
        target.riseRate = src.riseRate;
        target.fallRate = src.fallRate;
        target.stepAmount = src.stepAmount;
        target.stepDownAmount = src.stepDownAmount;
        target.deadzone = src.deadzone;
        target.smoothing = src.smoothing;
        target.bindingPreset = src.bindingPreset;
        return true;
    }

    // Check if the entries share a custom input binding
    private static boolean shareCustomInputBinding(CustomKeyEntry a, CustomKeyEntry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.inputTarget != null && b.inputTarget != null
                && Objects.equals(a.inputTarget.targetId(), b.inputTarget.targetId())) {
            return true;
        }
        if (a.keyCode >= 0 && a.keyCode == b.keyCode) {
            return true;
        }
        return frequencyPairMatches(a.inputFirst, a.inputSecond, b.inputFirst, b.inputSecond);
    }

    // Check if the frequency pairs match
    private static boolean frequencyPairMatches(ItemStack aFirst, ItemStack aSecond, ItemStack bFirst, ItemStack bSecond) {
        return aFirst != null && !aFirst.isEmpty()
                && aSecond != null && !aSecond.isEmpty()
                && bFirst != null && !bFirst.isEmpty()
                && bSecond != null && !bSecond.isEmpty()
                && ItemStack.isSameItemSameComponents(aFirst, bFirst)
                && ItemStack.isSameItemSameComponents(aSecond, bSecond);
    }

                                // Apply the block state signal
                                private boolean applyBlockStateSignal(@Nullable Level targetLevel,
                                                                            @Nullable BlockPos targetPos,
                                                                            @Nullable BlockEntity targetBe,
                                                                            int strength,
                                                                            @Nullable String optionChannelId,
                                                                            @Nullable String signalPropertyKey) {
                                    // ------------------------------------TARGET CHECKS------------------------------------
                                    if (targetLevel == null || targetPos == null || targetLevel.isClientSide) {
                                        return false;
                                    }

                                    // ------------------------------------DIRECT ADAPTER------------------------------------
                                    if (ExternalBlockEntityDirectControlCompat.applyDirectSignal(targetBe, strength / 15.0f)) {
                                        return true;
                                    }

                                    // ------------------------------------BLOCK STATE------------------------------------
                                    BlockState state = targetLevel.getBlockState(targetPos);
                                    BlockState updated = state;
                                    boolean changed = false;
                                    boolean powered = strength > 0;

                                    Double exactBefore = signalPropertyKey == null || signalPropertyKey.isBlank()
                                            ? null
                                            : readNamedSignalProperty(state, signalPropertyKey);
                                    if (signalPropertyKey != null && !signalPropertyKey.isBlank()) {
                                        updated = trySetNamedBoolean(updated, signalPropertyKey, powered);
                                        updated = trySetNamedInteger(updated, signalPropertyKey, strength);
                                    }

                                    if (updated.hasProperty(BlockStateProperties.POWERED)) {
                                        updated = updated.setValue(BlockStateProperties.POWERED, powered);
                                        changed = true;
                                    }
                                    if (updated.hasProperty(BlockStateProperties.LIT)) {
                                        updated = updated.setValue(BlockStateProperties.LIT, powered);
                                        changed = true;
                                    }
                                    if (updated.hasProperty(BlockStateProperties.OPEN)) {
                                        updated = updated.setValue(BlockStateProperties.OPEN, powered);
                                        changed = true;
                                    }
                                    if (updated.hasProperty(BlockStateProperties.POWER)) {
                                        updated = updated.setValue(BlockStateProperties.POWER, strength);
                                        changed = true;
                                    }

                                    updated = trySetNamedBoolean(updated, "powered", powered);
                                    updated = trySetNamedBoolean(updated, "lit", powered);
                                    updated = trySetNamedBoolean(updated, "open", powered);
                                    updated = trySetNamedInteger(updated, "power", strength);

                                    updated = setNamedDirSignal(updated, optionChannelId, powered, strength);

                                    // ------------------------------------STATE CHECK------------------------------------
                                    if (updated == state && !changed) {
                                        if (DEBUG_LINKER_FACE_IO) {
                                            Create.LOGGER.info(
                                                    "[CT-LinkerFaceIO] write-skip pos={} strength={} option={} prop={} exactBefore={} reason=no-state-change",
                                                    targetPos,
                                                    strength,
                                                    optionChannelId,
                                                    signalPropertyKey,
                                                    exactBefore);
                                        }
                                        return false;
                                    }

                                    Double exactAfter = signalPropertyKey == null || signalPropertyKey.isBlank()
                                            ? null
                                            : readNamedSignalProperty(updated, signalPropertyKey);
                                    if (DEBUG_LINKER_FACE_IO) {
                                        Create.LOGGER.info(
                                                "[CT-LinkerFaceIO] write-apply pos={} strength={} option={} prop={} exactBefore={} exactAfter={} stateChanged={}",
                                                targetPos,
                                                strength,
                                                optionChannelId,
                                                signalPropertyKey,
                                                exactBefore,
                                                exactAfter,
                                                (updated != state || changed));
                                    }

                                    // ------------------------------------STATE COMMIT------------------------------------
                                    KineticBlockEntity.switchToBlockState(targetLevel, targetPos, updated);
                                    targetLevel.updateNeighborsAt(targetPos, updated.getBlock());
                                    for (Direction dir : Direction.values()) {
                                        targetLevel.updateNeighborsAt(targetPos.relative(dir), updated.getBlock());
                                    }
                                    return true;
                                }

                                // Try to set named boolean
                                private static BlockState trySetNamedBoolean(BlockState state, String propertyName, boolean val) {
                                    for (var property : state.getProperties()) {
                                        if (property instanceof BooleanProperty booleanProperty && propertyName.equals(property.getName())) {
                                            if (state.getValue(booleanProperty) != val) {
                                                return state.setValue(booleanProperty, val);
                                            }
                                            return state;
                                        }
                                    }
                                    return state;
                                }

                                // Try to set named integer
                                private static BlockState trySetNamedInteger(BlockState state, String propertyName, int val) {
                                    for (var property : state.getProperties()) {
                                        if (property instanceof IntegerProperty integerProperty && propertyName.equals(property.getName())) {
                                            int clamped = Math.max(integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0),
                                                    Math.min(val, integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(val)));
                                            if (state.getValue(integerProperty) != clamped) {
                                                return state.setValue(integerProperty, clamped);
                                            }
                                            return state;
                                        }
                                    }
                                    return state;
                                }

                                // Set the named dir signal
                                private static BlockState setNamedDirSignal(BlockState state,
                                                                                       @Nullable String optionChannelId,
                                                                                       boolean powered,
                                                                                       int strength) {
                                    if (optionChannelId == null || optionChannelId.isBlank()) {
                                        return state;
                                    }
                                    List<String> tokens = dirTokensForOption(optionChannelId);
                                    if (tokens.isEmpty()) {
                                        return state;
                                    }

                                    BlockState updated = state;
                                    for (var property : state.getProperties()) {
                                        String propertyName = property.getName();
                                        if (!matchesDirectionalToken(propertyName, tokens)) {
                                            continue;
                                        }
                                        String normalized = propertyName.toLowerCase(Locale.ROOT);
                                        if (property instanceof BooleanProperty booleanProperty
                                                && (normalized.contains("power")
                                                || normalized.contains("lit")
                                                || normalized.contains("open")
                                                || normalized.contains("active")
                                                || normalized.contains("enable")
                                                || normalized.endsWith("on"))) {
                                            if (updated.getValue(booleanProperty) != powered) {
                                                updated = updated.setValue(booleanProperty, powered);
                                            }
                                            continue;
                                        }
                                        if (property instanceof IntegerProperty integerProperty
                                                && (normalized.contains("power")
                                                || normalized.contains("signal")
                                                || normalized.contains("strength"))) {
                                            int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                                            int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                                            int clamped = Math.max(min, Math.min(strength, max));
                                            if (updated.getValue(integerProperty) != clamped) {
                                                updated = updated.setValue(integerProperty, clamped);
                                            }
                                        }
                                    }
                                    return updated;
                                }

                                // Read the dir property
                                private static @Nullable Double readDirProperty(BlockState state,
                                                                                                @Nullable String optionChannelId,
                                                                                                @Nullable String signalPropertyKey) {
                                    if (state == null) {
                                        return null;
                                    }

                                    if (signalPropertyKey != null && !signalPropertyKey.isBlank()) {
                                        Double exactValue = readNamedSignalProperty(state, signalPropertyKey);
                                        if (exactValue != null) {
                                            return exactValue;
                                        }
                                    }

                                    if (optionChannelId == null || optionChannelId.isBlank()) {
                                        return null;
                                    }

                                    List<String> tokens = dirTokensForOption(optionChannelId);
                                    if (tokens.isEmpty()) {
                                        return null;
                                    }

                                    Double best = null;
                                    for (var property : state.getProperties()) {
                                        String propertyName = property.getName();
                                        if (!matchesDirectionalToken(propertyName, tokens)) {
                                            continue;
                                        }
                                        String normalized = propertyName.toLowerCase(Locale.ROOT);
                                        if (property instanceof BooleanProperty booleanProperty
                                                && (normalized.contains("power")
                                                || normalized.contains("lit")
                                                || normalized.contains("open")
                                                || normalized.contains("active")
                                                || normalized.contains("enable")
                                                || normalized.endsWith("on"))) {
                                            double val = state.getValue(booleanProperty) ? 1.0D : 0.0D;
                                            best = best == null ? val : Math.max(best, val);
                                            continue;
                                        }
                                        if (property instanceof IntegerProperty integerProperty
                                                && (normalized.contains("power")
                                                || normalized.contains("signal")
                                                || normalized.contains("strength"))) {
                                            int raw = state.getValue(integerProperty);
                                            int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                                            int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                                            double scaled = max <= min ? (raw > min ? 1.0D : 0.0D)
                                                    : net.minecraft.util.Mth.clamp((raw - min) / (double) (max - min), 0.0D, 1.0D);
                                            best = best == null ? scaled : Math.max(best, scaled);
                                        }
                                    }
                                    return best;
                                }

                                // Read the named signal property
                                private static @Nullable Double readNamedSignalProperty(BlockState state,
                                                                                          String propertyName) {
                                    String normalizedName = propertyName == null
                                            ? ""
                                            : propertyName.trim().toLowerCase(Locale.ROOT);
                                    if (normalizedName.isBlank()) {
                                        return null;
                                    }
                                    for (var property : state.getProperties()) {
                                        if (!normalizedName.equals(property.getName().toLowerCase(Locale.ROOT))) {
                                            continue;
                                        }
                                        if (property instanceof BooleanProperty booleanProperty) {
                                            return state.getValue(booleanProperty) ? 1.0D : 0.0D;
                                        }
                                        if (property instanceof IntegerProperty integerProperty) {
                                            int raw = state.getValue(integerProperty);
                                            int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                                            int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                                            if (max <= min) {
                                                return raw > min ? 1.0D : 0.0D;
                                            }
                                            return net.minecraft.util.Mth.clamp((raw - min) / (double) (max - min), 0.0D, 1.0D);
                                        }
                                        return null;
                                    }
                                    return null;
                                }

                                // Check if this matches directional token
                                private static boolean matchesDirectionalToken(String propertyName, List<String> tokens) {
                                    if (propertyName == null || propertyName.isBlank()) {
                                        return false;
                                    }
                                    String normalized = propertyName.toLowerCase(Locale.ROOT);
                                    for (String token : tokens) {
                                        if (token == null || token.isBlank()) {
                                            continue;
                                        }
                                        String normalizedToken = token.toLowerCase(Locale.ROOT);
                                        if (normalized.equals(normalizedToken)
                                                || normalized.startsWith(normalizedToken + "_")
                                                || normalized.endsWith("_" + normalizedToken)
                                                || normalized.contains("_" + normalizedToken + "_")) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }

                                // Get the dir tokens for option
                                private static List<String> dirTokensForOption(@Nullable String optionChannelId) {
                                    if (optionChannelId == null || optionChannelId.isBlank()) {
                                        return List.of();
                                    }
                                    return switch (optionChannelId.trim().toLowerCase(Locale.ROOT)) {
                                        case "yaw_left", "roll_left", "strafe_left" -> List.of("left");
                                        case "yaw_right", "roll_right", "strafe_right" -> List.of("right");
                                        case "pitch_up", "lift_up" -> List.of("up", "top", "upper");
                                        case "pitch_down", "lift_down" -> List.of("down", "bottom", "lower");
                                        case "throttle_up" -> List.of("forward", "front", "fwd");
                                        case "throttle_down" -> List.of("backward", "back", "rear", "reverse");
                                        default -> List.of();
                                    };
                                }

    // Press the custom entry
    public boolean pressCustomEntry(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        pressedCustomEntryIds.add(id);
        boolean changed = channel.press(level.getGameTime());
        changed |= channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Press the custom entry step down
    public boolean pressCustomEntryStepDown(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        pressedCustomEntryStepDownIds.add(id);
        boolean changed;
        if (channel.getMode() == AnalogueChannelMode.STEP) {

            changed = channel.stepBy(-Math.abs(entry.stepDownAmount), level.getGameTime());
        } else {
            changed = channel.press(level.getGameTime());
        }
        changed |= channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Release the custom entry
    public boolean releaseCustomEntry(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        if (!pressedCustomEntryIds.remove(id)) return false;
        boolean changed = channel.release(level.getGameTime());
        changed |= channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Release the custom entry step down
    public boolean releaseCustomEntryStepDown(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        if (!pressedCustomEntryStepDownIds.remove(id)) return false;
        boolean changed = channel.getMode() == AnalogueChannelMode.STEP
                ? channel.tick(level.getGameTime())
                : channel.release(level.getGameTime()) || channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Tap the custom input entry
    public boolean tapCustomEntry(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        boolean changed = channel.tap(level.getGameTime());
        changed |= channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Step down the custom input entry
    public boolean tapCustomEntryStepDown(String id) {
        if (level == null || level.isClientSide) return false;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return false;
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) return false;
        boolean changed = channel.getMode() == AnalogueChannelMode.STEP
                ? channel.stepBy(-Math.abs(entry.stepDownAmount), level.getGameTime())
                : channel.tap(level.getGameTime());
        changed |= channel.tick(level.getGameTime());
        if (changed) {
            pushCustomEntrySignal(id, resolveCustomEntryOutputValue(id));
        }
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Check if the custom entry is active
    public boolean isCustomEntryActive(String id) {
        return pressedCustomEntryIds.contains(id)
                || pressedCustomEntryStepDownIds.contains(id)
                || sampledCustomEntryIds.contains(id)
                || sampledCustomEntryValues.getOrDefault(id, 0.0f) > 1.0E-4f
                || resolveCustomEntryOutputValue(id) > 1.0E-4f;
    }

    // Check if the custom entry is pressed
    public boolean isCustomEntryPressed(String id) {
        return pressedCustomEntryIds.contains(id) || pressedCustomEntryStepDownIds.contains(id);
    }

    // Check if the custom entry main is pressed
    public boolean isCustomEntryMainPressed(String id) {
        return pressedCustomEntryIds.contains(id);
    }

    // Check if the custom entry step down is pressed
    public boolean isCustomEntryStepDownPressed(String id) {
        return pressedCustomEntryStepDownIds.contains(id);
    }

    // Get the custom entry value
    public float getCustomEntryValue(String id) {
        return resolveCustomEntryOutputValue(id);
    }

    // Set the custom entry exact value
    public boolean setCustomEntryExactValue(String id, double value) {
        if (level == null || level.isClientSide) {
            return false;
        }
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) {
            return false;
        }
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel == null) {
            return false;
        }
        float clamped = net.minecraft.util.Mth.clamp((float) value, 0.0f, 1.0f);
        pressedCustomEntryIds.remove(id);
        pressedCustomEntryStepDownIds.remove(id);
        sampledCustomEntryIds.add(id);
        sampledCustomEntryValues.put(id, clamped);
        channel.setValueInstant(clamped);
        pushCustomEntrySignal(id, clamped);
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Reset the custom entry
    public boolean resetCustomEntry(String id) {
        if (level == null || level.isClientSide) {
            return false;
        }
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) {
            return false;
        }
        AnalogueChannel channel = ensureCustomEntryChannel(entry);
        if (channel != null) {
            channel.reset();
        }
        pressedCustomEntryIds.remove(id);
        pressedCustomEntryStepDownIds.remove(id);
        sampledCustomEntryIds.remove(id);
        sampledCustomEntryValues.remove(id);
        pushCustomEntrySignal(id, 0.0f);
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
        return true;
    }

    // Push the custom entry signal
    private void pushCustomEntrySignal(String id, float val) {
        if (level == null || level.isClientSide) return;
        CustomKeyEntry entry = findCustomEntry(id);
        if (entry == null) return;
        cacheCustomOutput(id);

        if (entry.directTarget != null && entry.directTarget.blockPos() != null) {
            BlockEntity targetBe = SimulatedHelper.findLoadedBlockEntityExact(
                    level, entry.directTarget.subLevelId(), entry.directTarget.blockPos());

            pushDirectTargetSignal(entry.directTarget, targetBe, id, getCustomDirectOutput(entry), true);
        }

        ControllerLinkTarget target = customWirelessTargets.get(id);
        FrequencyBinding binding = customFrequencyBindings.get(id);
        if (target != null && binding != null && binding.isBound()) {
            target.accept(new AnalogueSignalPacket(id, val, level.getGameTime(),
                    worldPosition.toShortString(), "analogue_contraption_controller"));
            if (target.isRegistered()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(resolveLinkLevel(level), target);
            }
        }
    }

    // Get the custom direct output
    private float getCustomDirectOutput(CustomKeyEntry sourceEntry) {
        if (sourceEntry == null || sourceEntry.directTarget == null || sourceEntry.directTarget.blockPos() == null) {
            return 0.0f;
        }
        String routeChannelId = resolveDirectSignalChannelId(sourceEntry.id(), sourceEntry.directTarget);
        float strongest = 0.0f;
        for (CustomKeyEntry candidate : customKeyEntries) {
            if (!matchesDirectRoute(sourceEntry, routeChannelId, candidate)) {
                continue;
            }
            strongest = Math.max(strongest, resolveCustomEntryOutputValue(candidate.id()));
        }
        return net.minecraft.util.Mth.clamp(strongest, 0.0f, 1.0f);
    }

    // Check if this matches direct route
    private static boolean matchesDirectRoute(CustomKeyEntry sourceEntry,
                                              String routeChannelId,
                                              CustomKeyEntry candidate) {
        if (sourceEntry == null || candidate == null || candidate.directTarget == null) {
            return false;
        }
        ControllerDirectTargetReference sourceTarget = sourceEntry.directTarget;
        ControllerDirectTargetReference candidateTarget = candidate.directTarget;
        if (sourceTarget.blockPos() == null || candidateTarget.blockPos() == null) {
            return false;
        }
        if (!Objects.equals(sourceTarget.blockPos(), candidateTarget.blockPos())) {
            return false;
        }
        if (!Objects.equals(sourceTarget.subLevelId(), candidateTarget.subLevelId())) {
            return false;
        }
        String candidateChannelId = resolveDirectSignalChannelId(candidate.id(), candidateTarget);
        return Objects.equals(routeChannelId, candidateChannelId);
    }

    // Find the custom entry
    private CustomKeyEntry findCustomEntry(String id) {
        for (CustomKeyEntry e : customKeyEntries) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    // Resolve the direct signal channel id
    private static String resolveDirectSignalChannelId(String fallbackChannelId,
                                                       ControllerDirectTargetReference directTarget) {
        if (directTarget == null || directTarget.targetId() == null) {
            return fallbackChannelId;
        }
        String targetId = directTarget.targetId();
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        if (optionIndex < 0) {
            return fallbackChannelId;
        }
        String optionChannelId = targetId.substring(optionIndex + DIRECT_TARGET_OPTION_PREFIX.length()).trim();
        int propertyIndex = optionChannelId.indexOf(DIRECT_TARGET_PROPERTY_PREFIX);
        if (propertyIndex >= 0) {
            optionChannelId = optionChannelId.substring(0, propertyIndex).trim();
        }

        return optionChannelId.isEmpty() ? fallbackChannelId : optionChannelId;
    }

    // Get the direct signal property
    private static @Nullable String directSignalProperty(@Nullable ControllerDirectTargetReference directTarget) {
        if (directTarget == null || directTarget.targetId() == null) {
            return null;
        }
        String targetId = directTarget.targetId();
        int propertyIndex = targetId.indexOf(DIRECT_TARGET_PROPERTY_PREFIX);
        if (propertyIndex < 0) {
            return null;
        }
        String key = targetId.substring(propertyIndex + DIRECT_TARGET_PROPERTY_PREFIX.length())
                .trim()
                .toLowerCase(Locale.ROOT);

        return key.isBlank() ? null : key;
    }

    // Get the output face
    private @Nullable Direction outputFace(@Nullable ControllerDirectTargetReference directTarget,
                                                           @Nullable Level targetLevel,
                                                           @Nullable BlockPos targetPos) {
        Direction mappedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(directTarget);
        if (directTarget == null || targetLevel == null || targetPos == null) {
            return mappedFace;
        }

        String signalPropertyKey = directSignalProperty(directTarget);
        if (signalPropertyKey == null || signalPropertyKey.isBlank()) {
            return mappedFace;
        }

        BlockState targetState = targetLevel.getBlockState(targetPos);
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(targetState.getBlock()));
        if (!SIMULATED_DIRECTIONAL_GEARSHIFT_BLOCK_ID.equals(blockId)
                || !targetState.hasProperty(BlockStateProperties.FACING)) {
            return mappedFace;
        }

        Direction facing = targetState.getValue(BlockStateProperties.FACING);

        if ("left_powered".equals(signalPropertyKey)) {
            return facing;
        }
        if ("right_powered".equals(signalPropertyKey)) {
            return facing.getOpposite();
        }

        return mappedFace;
    }

    // Get the local output signal
    public int getLocalOutputSignal(Direction direction) {
        return localOutputs.getOrDefault(direction, 0);
    }

    // Set the channel mode
    public void setChannelMode(String channelId, AnalogueChannelMode mode) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null) {
            return;
        }
        channel.setMode(mode);
        markChannelDirty(channelId);
    }

    // Set the channel exact value
    public void setChannelExactValue(String channelId, double value) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null) {
            return;
        }
        channel.setValueInstant(value);
        markChannelRuntimeDirty(channelId);
    }

    // Set the channel frequency
    public void setChannelFrequency(String channelId, ItemStack first, ItemStack second) {
        FrequencyBinding binding = getFrequencyBinding(channelId);
        ControllerLinkTarget target = wirelessTargets.get(normalizeName(channelId));
        if (binding == null || target == null) {
            return;
        }

        ItemStack previousFirst = binding.first();
        ItemStack previousSecond = binding.second();
        binding.set(first, second);
        boolean changed = !ItemStack.isSameItemSameComponents(previousFirst, binding.first())
                || !ItemStack.isSameItemSameComponents(previousSecond, binding.second());
        if (!changed) {
            return;
        }

        if (level != null && !level.isClientSide) {
            target.updateBinding(level, binding);
            pushChannelSignal(channelId);
        }
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Set the channel input frequency
    public void setChannelInputFrequency(String channelId, ItemStack first, ItemStack second) {
        FrequencyBinding binding = getInputFrequencyBinding(channelId);
        ControllerLinkInputTarget target = inputLinkTargets.get(normalizeName(channelId));
        if (binding == null || target == null) {
            return;
        }
        ItemStack previousFirst = binding.first();
        ItemStack previousSecond = binding.second();
        binding.set(first, second);
        boolean changed = !ItemStack.isSameItemSameComponents(previousFirst, binding.first())
                || !ItemStack.isSameItemSameComponents(previousSecond, binding.second());
        if (!changed) {
            return;
        }
        if (level != null && !level.isClientSide) {
            target.updateBinding(level, binding);
        }
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Set the local output side
    public void setLocalOutputSide(String channelId, @Nullable Direction side) {
        if (!setLocalOutputSideInternal(channelId, side)) {
            return;
        }
        invalidateControllerRuntimeProgram();
        controllerRuntimeProgram().refreshLocalSideOutputs(true);
        setChanged();
        sendData();
    }

    // Set the channel key binding
    public void setChannelKeyBinding(String channelId, int keyCode) {
        String normalized = normalizeName(channelId);
        if (!channels.containsKey(normalized)) {
            return;
        }
        int sanitized = keyCode < 0 ? -1 : keyCode;
        if (keyBindings.getOrDefault(normalized, -1) == sanitized) {
            return;
        }
        keyBindings.put(normalized, sanitized);
        setChanged();
        sendData();
    }

    // Set the channel direct target
    public void setChannelDirectTarget(String channelId, @Nullable ControllerDirectTargetReference directTarget) {
        String normalized = normalizeName(channelId);
        if (!channels.containsKey(normalized)) {
            return;
        }
        ControllerDirectTargetReference sanitized = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(directTarget));
        ControllerDirectTargetReference prev = directTargets.get(normalized);
        if (java.util.Objects.equals(prev, sanitized)) {
            return;
        }
        directTargets.put(normalized, sanitized);
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Set the channel input target
    public void setChannelInputTarget(String channelId, @Nullable ControllerDirectTargetReference inputTarget) {
        String normalized = normalizeName(channelId);
        if (!channels.containsKey(normalized)) {
            return;
        }
        ControllerDirectTargetReference sanitized = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(inputTarget));
        ControllerDirectTargetReference prev = inputTargets.get(normalized);
        if (java.util.Objects.equals(prev, sanitized)) {
            return;
        }
        inputTargets.put(normalized, sanitized);
        invalidateControllerRuntimeProgram();
        setChanged();
        sendData();
    }

    // Apply the channel config
    public void applyChannelConfig(String channelId, AnalogueChannelMode mode, double riseRate, double fallRate,
                                   double stepAmount, double deadzone, double smoothing,
                                   @Nullable Direction localSide, ItemStack first, ItemStack second,

                                   ItemStack inputFirst, ItemStack inputSecond,
                                   int keyCode, @Nullable ControllerDirectTargetReference directTarget,
                                   @Nullable ControllerDirectTargetReference inputTarget, String bindingMode,
                                   String bindingPreset) {
        AnalogueChannel channel = getChannel(channelId);
        FrequencyBinding binding = getFrequencyBinding(channelId);
        ControllerLinkTarget target = wirelessTargets.get(normalizeName(channelId));
        if (channel == null || binding == null || target == null) {
            return;
        }

        // ------------------------------------CHANNEL SETTINGS------------------------------------
        channel.setMode(mode);
        channel.setRiseRate(riseRate);
        channel.setFallRate(fallRate);
        channel.setStepAmount(stepAmount);
        channel.setDeadzone(deadzone);
        channel.setSmoothing(smoothing);

        channel.setResetToZero(mode != AnalogueChannelMode.STEP && mode != AnalogueChannelMode.LATCH);

        // ------------------------------------LOCAL BINDINGS------------------------------------
        boolean sideChanged = setLocalOutputSideInternal(channelId, localSide);
        int sanitizedKeyCode = keyCode < 0 ? -1 : keyCode;
        String normalizedChannelId = normalizeName(channelId);
        boolean keyBindingChanged = keyBindings.getOrDefault(normalizedChannelId, -1) != sanitizedKeyCode;
        if (keyBindingChanged) {
            keyBindings.put(normalizedChannelId, sanitizedKeyCode);
        }
        // ------------------------------------DIRECT TARGETS------------------------------------
        ControllerDirectTargetReference sanitizedDirectTarget = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(directTarget));
        ControllerDirectTargetReference previousDirectTarget = directTargets.get(normalizedChannelId);
        boolean directTargetChanged = !java.util.Objects.equals(previousDirectTarget, sanitizedDirectTarget);
        ControllerDirectTargetReference sanitizedInputTarget = ControllerRedstoneCompat.ensureCompatTarget(level, sanitizeDirectTarget(inputTarget));
        ControllerDirectTargetReference previousInputTarget = inputTargets.get(normalizedChannelId);
        boolean inputTargetChanged = !java.util.Objects.equals(previousInputTarget, sanitizedInputTarget);

        ItemStack insertedLinker = getActiveStoredLinker();
        if (!insertedLinker.isEmpty()) {

            ContraptionNetworkLinkerData.writeChannelBinding(insertedLinker, normalizedChannelId,
                    sanitizedDirectTarget, sanitizedInputTarget);
            ContraptionNetworkLinkerData.ChannelBind persistedBind =
                    ContraptionNetworkLinkerData.readChannelBindings(insertedLinker).get(normalizedChannelId);
            sanitizedDirectTarget = ControllerRedstoneCompat.ensureCompatTarget(level,
                    sanitizeDirectTarget(persistedBind == null ? null : persistedBind.directTarget()));
            sanitizedInputTarget = ControllerRedstoneCompat.ensureCompatTarget(level,
                    sanitizeDirectTarget(persistedBind == null ? null : persistedBind.inputTarget()));
            directTargetChanged = !java.util.Objects.equals(previousDirectTarget, sanitizedDirectTarget);
            inputTargetChanged = !java.util.Objects.equals(previousInputTarget, sanitizedInputTarget);
        }

        if (directTargetChanged) {
            directTargets.put(normalizedChannelId, sanitizedDirectTarget);
        }
        if (inputTargetChanged) {
            inputTargets.put(normalizedChannelId, sanitizedInputTarget);
        }

        // ------------------------------------WIRELESS BINDINGS------------------------------------
        String sanitizedBindingMode = bindingMode == null || bindingMode.isBlank() ? "standard" : bindingMode;
        String previousBindingMode = bindingModes.getOrDefault(normalizedChannelId, "standard");
        boolean bindingModeChanged = !previousBindingMode.equals(sanitizedBindingMode);
        if (bindingModeChanged) {
            bindingModes.put(normalizedChannelId, sanitizedBindingMode);
        }
        String sanitizedBindingPreset = bindingPreset == null || bindingPreset.isBlank() ? "none" : bindingPreset;
        String previousBindingPreset = bindingPresets.getOrDefault(normalizedChannelId, "none");
        boolean bindingPresetChanged = !previousBindingPreset.equals(sanitizedBindingPreset);
        if (bindingPresetChanged) {
            bindingPresets.put(normalizedChannelId, sanitizedBindingPreset);
        }
        ItemStack previousFirst = binding.first();
        ItemStack previousSecond = binding.second();
        binding.set(first, second);
        boolean bindingChanged = !ItemStack.isSameItemSameComponents(previousFirst, binding.first())
                || !ItemStack.isSameItemSameComponents(previousSecond, binding.second());

        if (bindingChanged && level != null && !level.isClientSide) {
            target.updateBinding(level, binding);
        }

        // ------------------------------------INPUT BINDINGS------------------------------------
        FrequencyBinding inputBinding = getInputFrequencyBinding(channelId);
        ControllerLinkInputTarget inputLinkTarget = inputLinkTargets.get(normalizeName(channelId));
        boolean inputBindingChanged = false;
        if (inputBinding != null && inputLinkTarget != null) {
            ItemStack prevInputFirst = inputBinding.first();
            ItemStack prevInputSecond = inputBinding.second();
            inputBinding.set(inputFirst, inputSecond);
            inputBindingChanged = !ItemStack.isSameItemSameComponents(prevInputFirst, inputBinding.first())
                    || !ItemStack.isSameItemSameComponents(prevInputSecond, inputBinding.second());
            if (inputBindingChanged && level != null && !level.isClientSide) {
                inputLinkTarget.updateBinding(level, inputBinding);
            }
        }

        if (bindingChanged || inputBindingChanged || sideChanged || keyBindingChanged || directTargetChanged || inputTargetChanged || bindingModeChanged || bindingPresetChanged) {
            invalidateControllerRuntimeProgram();
            markChannelDirty(channelId);
            return;
        }
        setChanged();
        sendData();
    }

    // Mark the channel dirty
    public void markChannelDirty(String channelId) {
        pushChannelSignal(channelId);
        cacheChannelOutput(channelId);
        refreshLocalSideOutputs(true);
        refreshOmeterOutput();
        setChanged();
        sendData();
    }

    // Mark the channel runtime dirty
    private void markChannelRuntimeDirty(String channelId) {
        pushChannelSignal(channelId);
        cacheChannelOutput(channelId);
        refreshLocalSideOutputs(true);
        markRuntimeStateDirty();
    }

    // Mark the runtime state dirty
    private void markRuntimeStateDirty() {
        boolean ometerChanged = refreshOmeterOutput();
        setChanged();
        if (ometerChanged) {
            sendRuntimeData();
        }
    }

    // Describe the channel
    public Map<String, Object> describeChannel(String channelId) {
        AnalogueChannel channel = getChannel(channelId);
        if (channel == null) {
            return Map.of();
        }
        Map<String, Object> description = new LinkedHashMap<>(channel.describe());
        FrequencyBinding binding = getFrequencyBinding(channelId);
        description.put("binding", describeBinding(binding));
        Direction localOutputSide = getLocalOutputSide(channelId);
        description.put("localSide", localOutputSide == null ? "" : localOutputSide.getSerializedName());
        ControllerDirectTargetReference directTarget = getDirectTarget(channelId);
        description.put("directTarget", directTarget == null ? Map.of() : describeDirectTarget(directTarget));
        ControllerDirectTargetReference inputTarget = getInputTarget(channelId);
        description.put("inputTarget", inputTarget == null ? Map.of() : describeDirectTarget(inputTarget));
        description.put("label", Component.translatable(resolveDefinition(channelId).translationKey()).getString());
        return description;
    }

    // Describe the axis
    public Map<String, Object> describeAxis(String axisId) {
        AnalogueAxis axis = getAxis(axisId);
        if (axis == null) {
            return Map.of();
        }
        Map<String, Object> description = new LinkedHashMap<>(axis.describe());
        description.put("negativeChannel", axis.negativeChannel().id());
        description.put("positiveChannel", axis.positiveChannel().id());
        return description;
    }

    // Get all signals
    public Map<String, Object> getAllSignals() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> channelPayload = new LinkedHashMap<>();
        for (String channelId : channels.keySet()) {
            channelPayload.put(channelId, describeChannel(channelId));
        }
        Map<String, Object> axisPayload = new LinkedHashMap<>();
        for (String axisId : axes.keySet()) {
            axisPayload.put(axisId, describeAxis(axisId));
        }
        payload.put("channels", channelPayload);
        payload.put("axes", axisPayload);
        return payload;
    }

    // Discover the control nodes
    public List<ControllerDiscoveryNode> discoverControlNodes() {

        return List.of();
    }

    // Get the stored targets
    public List<ControllerDiscoveryNode> getStoredTargets() {
        return List.copyOf(storedTargets);
    }

    // Update the stored target
    public void upsertStoredTarget(ControllerDiscoveryNode target) {
        if (target == null || !target.isValid()) {
            return;
        }
        String nodeId = target.nodeId();
        for (int i = 0; i < storedTargets.size(); i++) {
            ControllerDiscoveryNode existing = storedTargets.get(i);
            if (existing != null && nodeId.equals(existing.nodeId())) {
                storedTargets.set(i, target);
                setChanged();
                sendData();
                return;
            }
        }
        storedTargets.add(target);
        setChanged();
        sendData();
    }

    // Set the stored targets
    public void setStoredTargets(List<ControllerDiscoveryNode> targets) {
        storedTargets.clear();
        if (targets != null) {
            Map<String, ControllerDiscoveryNode> deduped = new LinkedHashMap<>();
            for (ControllerDiscoveryNode node : targets) {
                if (node != null && node.isValid()) {
                    deduped.putIfAbsent(node.nodeId(), node);
                }
            }
            storedTargets.addAll(deduped.values());
        }
        setChanged();
        sendData();
    }

    // Get the assignable targets
    public List<ControllerDiscoveryNode> getAssignableTargets() {
        return mergeAssignableTargets(storedTargets, linkerDiscoveryNodes());
    }

    // Merge the assignable targets
    static List<ControllerDiscoveryNode> mergeAssignableTargets(List<ControllerDiscoveryNode> storedTargets,
                                                                List<ControllerDiscoveryNode> linkerTargets) {
        Map<String, ControllerDiscoveryNode> nodes = new LinkedHashMap<>();
        if (storedTargets != null) {
            for (ControllerDiscoveryNode node : storedTargets) {
                if (node != null && node.isValid()) {
                    nodes.putIfAbsent(node.nodeId(), node);
                }
            }
        }
        if (linkerTargets != null) {
            for (ControllerDiscoveryNode node : linkerTargets) {
                if (node != null && node.isValid()) {
                    nodes.putIfAbsent(node.nodeId(), node);
                }
            }
        }
        return new ArrayList<>(nodes.values());
    }

    // Get the mechanic binding
    public @Nullable ControllerMechanicBinding getMechanicBinding(String channelId) {
        AnalogueControlChannel definition = resolveDefinition(channelId);
        AnalogueChannel channel = getChannel(channelId);
        ControllerMechanic mechanic = resolveMechanic(definition);
        if (definition == null || channel == null || mechanic == null) {
            return null;
        }

        FrequencyBinding frequencyBinding = getFrequencyBinding(channelId);
        return new ControllerMechanicBinding(
                definition.id(),
                mechanic,
                definition.isPositiveAxis(),
                definition.translationKey(),
                channel.getMode(),
                frequencyBinding == null ? new FrequencyBinding(definition.id()) : frequencyBinding,
                getDirectTarget(channelId),
                getKeyBinding(channelId),
                getLocalOutputSide(channelId));
    }

    // Get all mechanic bindings
    public Map<String, ControllerMechanicBinding> getAllMechanicBindings() {
        Map<String, ControllerMechanicBinding> bindings = new LinkedHashMap<>();
        for (String channelId : channels.keySet()) {
            ControllerMechanicBinding binding = getMechanicBinding(channelId);
            if (binding != null) {
                bindings.put(channelId, binding);
            }
        }
        return bindings;
    }

    // Handle the destroyed event
    public void onDestroyed() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (pendingAssemblyBindingRemap) {
            return;
        }

        for (String channelId : channels.keySet()) {
            clearDirectOutputSignalAndSiblings(directTargets.get(channelId), channelId, false);
        }
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            clearDirectOutputSignalAndSiblings(entry.directTarget, entry.id(), true);
        }
        resetAllChannels();
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry != null) {
                resetCustomEntry(entry.id());
            }
        }
        ItemStack linker = getStoredLinker();
        if (!linker.isEmpty()) {
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    linker.copy());
            linkerSlot.setStackInSlot(0, ItemStack.EMPTY);
        }
        unregisterWirelessNetwork();
        if (!retainControllerManifestAfterDestroy()) {
            deleteControllerManifest();
        }
    }

    // Mark the destructive removal
    public void markDestructiveRemoval() {
        destructiveRemovalPending = true;
    }

    // Check if the destructive removal is pending
    public boolean isDestructiveRemovalPending() {
        return destructiveRemovalPending;
    }

    // Let a specialised controller retain its durable SQLite state after a block replacement.
    protected boolean retainControllerManifestAfterDestroy() {
        return false;
    }

    // Handle the external relocation event
    public void onExternalRelocation() {
        if (level != null && !level.isClientSide) {
            unregisterWirelessNetwork();
            saveControllerManifestNow();
        }
    }

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) {
            unregisterWirelessNetwork();
        }
        super.onChunkUnloaded();
    }

    // Remove the wireless network
    private void unregisterWirelessNetwork() {
        for (ControllerLinkTarget target : wirelessTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkTarget target : customWirelessTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkInputTarget target : customInputLinkTargets.values()) {
            target.unregister(level);
        }
        for (ControllerLinkInputTarget target : inputLinkTargets.values()) {
            target.unregister(level);
        }
    }

    // Begin the assembly transfer
    public void beginAssemblyTransfer(BlockPos oldPos, BlockPos newPos) {
        if (oldPos == null || newPos == null) {
            return;
        }
        assemblySourceSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        assemblySourceWasWorld = assemblySourceSubLevelId == null;
        assemblyOldPos = oldPos.immutable();
        assemblyNewPos = newPos.immutable();
        if (level != null && !level.isClientSide) {
            unregisterWirelessNetwork();
        }
        pendingAssemblyBindingRemap = true;
    }

    // Finish the assembly transfer
    public void finishAssemblyTransfer() {
        BlockPos oldPos = assemblyOldPos;
        BlockPos newPos = assemblyNewPos;
        UUID sourceSubLevelId = assemblySourceSubLevelId;
        boolean sourceWasWorld = assemblySourceWasWorld;
        boolean hadTransfer = oldPos != null && newPos != null;
        if (hadTransfer && level != null && !level.isClientSide) {
            reloadControllerManifest(level.registryAccess());
            if (!pendingAssemblyBindingRemap) {
                assemblyOldPos = oldPos;
                assemblyNewPos = newPos;
                assemblySourceSubLevelId = sourceSubLevelId;
                assemblySourceWasWorld = sourceWasWorld;
            }
        }
        pendingAssemblyBindingRemap = assemblyOldPos != null && assemblyNewPos != null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         SERIALIZATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the analogue contraption controller
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (!clientPacket && schematicContext != null) {
            CompoundTag payload = pendingControllerSchematicPayload == null
                    ? createSchematicData(provider)
                    : pendingControllerSchematicPayload.copy();
            remapSchematicData(payload, schematicContext);
            if (schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
                payload.putBoolean(ControllerSchematicPayload.PLACEMENT_PREPARED_TAG, true);
            }
            ControllerSchematicPayload.write(tag, payload);
            return;
        }
        if (!clientPacket && pendingControllerSchematicPayload != null) {
            ControllerSchematicPayload.write(tag, pendingControllerSchematicPayload);
            return;
        }
        if (shouldPersistControllerManifest(clientPacket)) {
            ControllerManifestStore.ManifestSnapshot snapshot = saveControllerManifest(provider);
            if (snapshot != null) {
                ControllerManifestStore.writeControllerMetadata(tag, snapshot);
            } else if (controllerManifestId != null && !controllerManifestId.isBlank()) {
                tag.putString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID, controllerManifestId);
            }
            return;
        }
        writeControllerBody(tag, provider);
        writeAdditionalControllerManifestData(tag, provider);
        SchematicSubLevelReferenceRemapper.remapInPlace(tag);
    }

    // Create the schematic data
    private CompoundTag createSchematicData(HolderLookup.Provider provider) {
        CompoundTag payload = new CompoundTag();
        payload.putString(ControllerSchematicPayload.CONTROLLER_KIND_TAG, controllerManifestKind());
        CompoundTag controllerData = new CompoundTag();
        writeControllerBody(controllerData, provider);
        writeAdditionalControllerManifestData(controllerData, provider);
        payload.put(ControllerSchematicPayload.CONTROLLER_DATA_TAG, controllerData);

        ItemStack linker = getStoredLinker();
        if (!linker.isEmpty()) {
            CompoundTag linkerData =
                    ContraptionNetworkLinkerData.copyAuthoritativeRootForSchematic(linker);
            if (!linkerData.isEmpty()) {
                payload.put(ControllerSchematicPayload.LINKER_DATA_TAG, linkerData);
            }
        }
        writeAdditionalControllerSchematicPayload(payload, provider);
        return payload;
    }

    // Create the photomancy schematic payload
    final CompoundTag createPhotomancySchematicPayload(HolderLookup.Provider provider) {
        return createSchematicData(provider);
    }

    // Remap the schematic data
    private void remapSchematicData(
            CompoundTag payload,
            SubLevelSchematicSerializationContext ctx) {
        if (payload.contains(ControllerSchematicPayload.CONTROLLER_DATA_TAG, Tag.TAG_COMPOUND)) {
            SchematicSubLevelReferenceRemapper.remapInPlace(
                    payload.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG));
        }
        if (payload.contains(ControllerSchematicPayload.LINKER_DATA_TAG, Tag.TAG_COMPOUND)) {
            SchematicSubLevelReferenceRemapper.remapInPlace(
                    payload.getCompound(ControllerSchematicPayload.LINKER_DATA_TAG));
        }
        remapAdditionalControllerSchematicPayload(payload, ctx);
    }

    // Write the additional controller schematic payload
    protected void writeAdditionalControllerSchematicPayload(
            CompoundTag payload,
            HolderLookup.Provider provider) {
    }

    // Remap the additional controller schematic payload
    protected void remapAdditionalControllerSchematicPayload(
            CompoundTag payload,
            SubLevelSchematicSerializationContext context) {
    }

    // Read the additional controller schematic payload
    protected void readAdditionalControllerSchematicPayload(
            CompoundTag payload,
            HolderLookup.Provider provider) {
    }

    // Finalize the additional controller schematic import
    protected boolean finalizeAdditionalControllerSchematicImport(CompoundTag payload) {
        return true;
    }

    // Get the controller manifest kind
    protected String controllerManifestKind() {
        return "base_controller";
    }

    // Write the additional controller manifest data
    protected void writeAdditionalControllerManifestData(CompoundTag tag, HolderLookup.Provider provider) {
    }

    // Read the additional controller manifest data
    protected void readAdditionalControllerManifestData(CompoundTag tag, HolderLookup.Provider provider,
                                                        String manifestKind) {
    }

    // Check if this should persist controller manifest
    private boolean shouldPersistControllerManifest(boolean clientPacket) {
        return !clientPacket && level != null && !level.isClientSide;
    }

    // Get the loading dependencies
    @Override
    public Iterable<SubLevel> sable$getLoadingDependencies() {
        if (level == null) return List.of();
        Set<UUID> subLevelIds = new LinkedHashSet<>();
        collectTargetSubLevelIds(directTargets.values(), subLevelIds);
        collectTargetSubLevelIds(inputTargets.values(), subLevelIds);
        for (CustomKeyEntry entry : customKeyEntries) {
            addTargetSubLevelId(entry.directTarget, subLevelIds);
            addTargetSubLevelId(entry.inputTarget, subLevelIds);
        }
        ItemStack linker = getStoredLinker();
        if (!linker.isEmpty()) {
            for (ContraptionNetworkLinkerData.LinkedTarget target
                    : ContraptionNetworkLinkerData.readTargets(linker)) {
                if (target != null && target.subLevelId() != null) {
                    subLevelIds.add(target.subLevelId());
                }
            }
        }
        collectAdditionalSchematicSubLevelIds(subLevelIds);
        UUID containingSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (containingSubLevelId != null) {
            subLevelIds.remove(containingSubLevelId);
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        List<SubLevel> dependencies = new ArrayList<>();
        for (UUID subLevelId : subLevelIds) {
            SubLevel subLevel = container.getSubLevel(subLevelId);
            if (subLevel != null && !subLevel.isRemoved()) dependencies.add(subLevel);
        }
        return dependencies;
    }

    // Collect the additional schematic sublevel ids
    protected void collectAdditionalSchematicSubLevelIds(Set<UUID> subLevelIds) {
    }

    // Collect the target sublevel ids
    private static void collectTargetSubLevelIds(Iterable<ControllerDirectTargetReference> targets,
                                                 Set<UUID> subLevelIds) {
        for (ControllerDirectTargetReference target : targets) addTargetSubLevelId(target, subLevelIds);
    }

    // Add the target sublevel id
    private static void addTargetSubLevelId(@Nullable ControllerDirectTargetReference target,
                                            Set<UUID> subLevelIds) {
        if (target != null && target.subLevelId() != null) subLevelIds.add(target.subLevelId());
    }

    // Save the controller manifest
    private @Nullable ControllerManifestStore.ManifestSnapshot saveControllerManifest(HolderLookup.Provider provider) {
        CompoundTag manifestData = new CompoundTag();
        writeControllerBody(manifestData, provider);
        writeAdditionalControllerManifestData(manifestData, provider);
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        boolean schematicCopy = schematicContext != null
                && schematicContext.getType() == SubLevelSchematicSerializationContext.Type.SAVE;
        if (schematicContext != null) {
            SchematicSubLevelReferenceRemapper.remapInPlace(manifestData);
        }
        ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.saveController(
                schematicCopy ? "" : controllerManifestId,
                controllerManifestKind(),
                level,
                worldPosition,
                SimulatedHelper.getContainingSubLevelId(this),
                manifestData,
                manifestData.getCompound("AdvancedDraftGraph"),
                manifestData.getCompound("AdvancedActiveGraph"),
                schematicCopy ? 0 : controllerManifestRevision);
        if (snapshot != null && !schematicCopy) {
            applyControllerManifestSnapshot(snapshot);
            onControllerManifestSaved(snapshot);
        }
        return snapshot;
    }

    // Save the controller manifest now
    protected void saveControllerManifestNow() {
        if (level == null || level.isClientSide) {
            return;
        }
        saveControllerManifest(level.registryAccess());
    }

    // Get the controller manifest id
    protected String controllerManifestId() {
        return controllerManifestId == null ? "" : controllerManifestId;
    }

    // Reload the controller manifest
    private boolean reloadControllerManifest(HolderLookup.Provider provider) {
        return restoreControllerManifest(controllerManifestId, provider);
    }

    // Restore this controller from a known SQLite manifest identity.
    protected final boolean restoreControllerManifest(
            String manifestId,
            HolderLookup.Provider provider
    ) {
        if (level == null || level.isClientSide || manifestId == null || manifestId.isBlank()) {
            return false;
        }
        ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.loadController(manifestId, level);
        if (snapshot == null) {
            return false;
        }
        applyControllerManifestSnapshot(snapshot);
        String manifestKind = snapshot.kind();
        CompoundTag controllerData = snapshot.controllerData();
        if (!snapshot.draftGraph().isEmpty()) {
            controllerData.put("AdvancedDraftGraph", snapshot.draftGraph());
        }
        if (!snapshot.activeGraph().isEmpty()) {
            controllerData.put("AdvancedActiveGraph", snapshot.activeGraph());
        }
        CompoundTag remappedControllerData = SubLevelSchematicSerializationContext.getCurrentContext() == null
                ? controllerData
                : SchematicSubLevelReferenceRemapper.remappedCopy(controllerData);
        readControllerBody(remappedControllerData, provider);
        readAdditionalControllerManifestData(remappedControllerData, provider, manifestKind);
        invalidateControllerRuntimeProgram();
        onControllerManifestReloaded();
        return true;
    }

    // Handle the controller manifest reloaded event
    protected void onControllerManifestReloaded() {
    }

    // Observe a successful SQLite save after its stable manifest identity is known.
    protected void onControllerManifestSaved(ControllerManifestStore.ManifestSnapshot snapshot) {
    }

    // Check if the assembly transfer is pending
    protected boolean isAssemblyTransferPending() {
        return pendingAssemblyBindingRemap;
    }

    // Delete the controller manifest
    private void deleteControllerManifest() {
        if (controllerManifestId == null || controllerManifestId.isBlank()) {
            return;
        }
        if (ControllerManifestStore.deleteController(controllerManifestId, level)) {
            controllerManifestId = "";
            controllerManifestRevision = 0;
            controllerManifestHash = "";
        }
    }

    // Apply the controller manifest snapshot
    private void applyControllerManifestSnapshot(ControllerManifestStore.ManifestSnapshot snapshot) {
        controllerManifestId = snapshot.id();
        controllerManifestRevision = snapshot.revision();
        controllerManifestHash = snapshot.hash();
    }

    // Read the manifest meta
    private void readManifestMeta(CompoundTag tag) {
        controllerManifestId = ControllerManifestStore.controllerManifestId(tag);
        controllerManifestRevision = ControllerManifestStore.controllerManifestRevision(tag);
        controllerManifestHash = ControllerManifestStore.controllerManifestHash(tag);
    }

    // Reset the manifest id
    private void resetManifestId() {
        controllerManifestId = "";
        controllerManifestRevision = 0;
        controllerManifestHash = "";
    }

    // Finalize the pending controller schematic import
    private boolean finalizePendingControllerSchematicImport() {
        if (!pendingControllerSchematicImport) {
            return true;
        }
        if (level == null || level.isClientSide || pendingControllerSchematicPayload == null) {
            return false;
        }

        ItemStack linker = getStoredLinker();
        if (ContraptionNetworkLinkerData.hasEmbeddedSchematicRoot(linker)
                && !ContraptionNetworkLinkerData.migrateLegacyStorage(linker)) {
            return false;
        }
        if (!finalizeAdditionalControllerSchematicImport(
                pendingControllerSchematicPayload)) {
            return false;
        }

        resetManifestId();
        ControllerManifestStore.ManifestSnapshot snapshot =
                saveControllerManifest(level.registryAccess());
        if (snapshot == null) {
            return false;
        }
        pendingControllerSchematicImport = false;
        pendingControllerSchematicPayload = null;
        setChanged();
        return true;
    }

    // Write the controller body
    protected void writeControllerBody(CompoundTag tag, HolderLookup.Provider provider) {
        // ------------------------------------CHANNELS AND AXES------------------------------------
        CompoundTag channelTag = new CompoundTag();
        for (Map.Entry<String, AnalogueChannel> entry : channels.entrySet()) {
            channelTag.put(entry.getKey(), entry.getValue().writeToTag());
        }
        tag.put("Channels", channelTag);

        CompoundTag axisTag = new CompoundTag();
        for (Map.Entry<String, AnalogueAxis> entry : axes.entrySet()) {
            axisTag.put(entry.getKey(), entry.getValue().writeToTag());
        }
        tag.put("Axes", axisTag);

        // ------------------------------------WIRELESS BINDINGS------------------------------------
        CompoundTag bindingTag = new CompoundTag();
        for (Map.Entry<String, FrequencyBinding> entry : frequencyBindings.entrySet()) {
            bindingTag.put(entry.getKey(), entry.getValue().toTag(provider));
        }
        tag.put("Bindings", bindingTag);

        CompoundTag inputBindingTag = new CompoundTag();
        for (Map.Entry<String, FrequencyBinding> entry : inputFrequencyBindings.entrySet()) {
            inputBindingTag.put(entry.getKey(), entry.getValue().toTag(provider));
        }
        tag.put("InputBindings", inputBindingTag);

        // ------------------------------------DIRECT TARGETS------------------------------------
        CompoundTag directTargetsTag = new CompoundTag();
        for (Map.Entry<String, ControllerDirectTargetReference> entry : directTargets.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isBound()) {
                directTargetsTag.put(entry.getKey(), entry.getValue().toTag());
            }
        }
        tag.put("DirectTargets", directTargetsTag);

        CompoundTag inputTargetsTag = new CompoundTag();
        for (Map.Entry<String, ControllerDirectTargetReference> entry : inputTargets.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isBound()) {
                inputTargetsTag.put(entry.getKey(), entry.getValue().toTag());
            }
        }
        tag.put("InputTargets", inputTargetsTag);

        // -----------------------------------------------------LOCAL OUTPUTS-----------------------------------------------------
        CompoundTag localOutputsTag = new CompoundTag();
        for (Map.Entry<String, Direction> entry : localOutputSides.entrySet()) {
            if (entry.getValue() != null) {
                localOutputsTag.putString(entry.getKey(), entry.getValue().getSerializedName());
            }
        }
        tag.put("LocalOutputSides", localOutputsTag);

        CompoundTag keyBindingsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : keyBindings.entrySet()) {
            keyBindingsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("KeyBindings", keyBindingsTag);

        CompoundTag bindingModesTag = new CompoundTag();
        for (Map.Entry<String, String> entry : bindingModes.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                bindingModesTag.putString(entry.getKey(), entry.getValue());
            }
        }
        tag.put("BindingModes", bindingModesTag);

        CompoundTag bindingPresetsTag = new CompoundTag();
        for (Map.Entry<String, String> entry : bindingPresets.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                bindingPresetsTag.putString(entry.getKey(), entry.getValue());
            }
        }
        tag.put("BindingPresets", bindingPresetsTag);

        // ------------------------------------TARGETS AND CONTROLLER STATE------------------------------------
        ListTag storedTargetsTag = new ListTag();
        for (ControllerDiscoveryNode target : storedTargets) {
            if (target != null && target.isValid()) {
                storedTargetsTag.add(target.toTag());
            }
        }
        tag.put("StoredTargets", storedTargetsTag);
        tag.put("LinkerSlot", linkerSlot.serializeNBT(provider));
        if (embeddedSlabState != null) {
            tag.put(EMBEDDED_SLAB_TAG, NbtUtils.writeBlockState(embeddedSlabState));
            if (embeddedSlabMaterial != null) {
                tag.put(EMBEDDED_SLAB_MATERIAL_TAG, NbtUtils.writeBlockState(embeddedSlabMaterial));
            }
            if (embeddedBlockEntityData != null) {
                tag.put(EMBEDDED_BLOCK_ENTITY_TAG, embeddedBlockEntityData.copy());
            }
        }
        if (pendingAssemblyBindingRemap && assemblyOldPos != null && assemblyNewPos != null) {
            CompoundTag transferTag = new CompoundTag();
            transferTag.putBoolean("SourceWasWorld", assemblySourceWasWorld);
            if (assemblySourceSubLevelId != null) {
                transferTag.putUUID("SourceSubLevelId", assemblySourceSubLevelId);
            }
            transferTag.putLong("OldPos", assemblyOldPos.asLong());
            transferTag.putLong("NewPos", assemblyNewPos.asLong());
            tag.put(ASSEMBLY_TRANSFER_TAG, transferTag);
        }
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
        tag.putInt("OmeterOutputSignal", ometerOutputSignal);
        ListTag customKeyEntriesTag = new ListTag();
        for (CustomKeyEntry entry : customKeyEntries) {
            customKeyEntriesTag.add(entry.toTag(provider));
        }
        tag.put("CustomKeyEntries", customKeyEntriesTag);
    }

    // Read the analogue contraption controller
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        // ------------------------------------SCHEMATIC PAYLOAD------------------------------------
        boolean hadSchematicPayload = !clientPacket
                && tag.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND);
        CompoundTag schematicPayload = hadSchematicPayload
                ? ControllerSchematicPayload.take(tag)
                : null;
        if (hadSchematicPayload && schematicPayload == null) {
            schematicPayload = new CompoundTag();
            schematicPayload.putString(
                    ControllerSchematicPayload.CONTROLLER_KIND_TAG,
                    controllerManifestKind());
            schematicPayload.put(
                    ControllerSchematicPayload.CONTROLLER_DATA_TAG,
                    new CompoundTag());
        }
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (schematicPayload != null && schematicContext != null) {
            remapSchematicData(schematicPayload, schematicContext);
            if (schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
                schematicPayload.putBoolean(
                        ControllerSchematicPayload.PLACEMENT_PREPARED_TAG, true);
            }
        }

        // -----------------------------------------------------MANIFEST DATA-----------------------------------------------------
        CompoundTag controllerData = tag;
        String manifestKind = "";
        if (schematicPayload != null) {
            resetManifestId();
            controllerData = schematicPayload.getCompound(
                    ControllerSchematicPayload.CONTROLLER_DATA_TAG);
            manifestKind = schematicPayload.getString(
                    ControllerSchematicPayload.CONTROLLER_KIND_TAG);
            pendingControllerSchematicPayload = schematicPayload.copy();
            pendingControllerSchematicImport = schematicPayload.getBoolean(
                    ControllerSchematicPayload.PLACEMENT_PREPARED_TAG);
        } else if (!clientPacket && ControllerManifestStore.hasControllerMetadata(tag)) {
            readManifestMeta(tag);
            ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.loadControllerFromMetadata(tag, level);
            if (snapshot != null) {
                applyControllerManifestSnapshot(snapshot);
                manifestKind = snapshot.kind();
                controllerData = snapshot.controllerData();
                if (!snapshot.draftGraph().isEmpty()) {
                    controllerData.put("AdvancedDraftGraph", snapshot.draftGraph());
                }
                if (!snapshot.activeGraph().isEmpty()) {
                    controllerData.put("AdvancedActiveGraph", snapshot.activeGraph());
                }
            }
        } else if (!clientPacket) {
            pendingControllerSchematicPayload = null;
            pendingControllerSchematicImport = false;
        }
        // ------------------------------------REFERENCE REMAP------------------------------------
        CompoundTag remappedControllerData = schematicPayload != null
                || schematicContext == null
                ? controllerData
                : SchematicSubLevelReferenceRemapper.remappedCopy(controllerData);
        if (schematicPayload != null
                && !schematicPayload.contains(
                ControllerSchematicPayload.LINKER_DATA_TAG, Tag.TAG_COMPOUND)) {
            remappedControllerData.remove("LinkerSlot");
        }
        readControllerBody(remappedControllerData, provider);
        if (schematicPayload != null
                && schematicPayload.contains(
                ControllerSchematicPayload.LINKER_DATA_TAG, Tag.TAG_COMPOUND)) {
            ItemStack linker = getStoredLinker();
            if (ContraptionNetworkLinkerData.installSchematicCopy(
                    linker,
                    schematicPayload.getCompound(
                            ControllerSchematicPayload.LINKER_DATA_TAG))) {
                syncFromInsertedLinker();
            }
        }
        // ------------------------------------ADDITIONAL DATA------------------------------------
        readAdditionalControllerManifestData(remappedControllerData, provider, manifestKind);
        if (schematicPayload != null) {
            readAdditionalControllerSchematicPayload(schematicPayload, provider);
        }
        invalidateControllerRuntimeProgram();
    }

    // Read the controller body
    protected void readControllerBody(CompoundTag tag, HolderLookup.Provider provider) {
        // ------------------------------------EMBEDDED BLOCK------------------------------------
        embeddedSlabState = null;
        embeddedSlabMaterial = null;
        embeddedBlockEntityData = null;
        embeddedBlockEntityPreview = null;
        if (tag.contains(EMBEDDED_SLAB_TAG, Tag.TAG_COMPOUND)) {
            BlockState embeddedState = NbtUtils.readBlockState(
                    provider.lookupOrThrow(Registries.BLOCK),
                    tag.getCompound(EMBEDDED_SLAB_TAG));
            embeddedSlabState = embeddedState;
            if (tag.contains(EMBEDDED_SLAB_MATERIAL_TAG, Tag.TAG_COMPOUND)) {
                embeddedSlabMaterial = NbtUtils.readBlockState(
                        provider.lookupOrThrow(Registries.BLOCK),
                        tag.getCompound(EMBEDDED_SLAB_MATERIAL_TAG));
            }
            if (tag.contains(EMBEDDED_BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
                embeddedBlockEntityData = tag.getCompound(EMBEDDED_BLOCK_ENTITY_TAG).copy();
            }
        }

        // ------------------------------------CHANNELS AND AXES------------------------------------
        CompoundTag channelsTag = tag.getCompound("Channels");
        for (Map.Entry<String, AnalogueChannel> entry : channels.entrySet()) {
            if (channelsTag.contains(entry.getKey())) {
                entry.getValue().readFromTag(channelsTag.getCompound(entry.getKey()));
            }
        }

        CompoundTag axesTag = tag.getCompound("Axes");
        for (Map.Entry<String, AnalogueAxis> entry : axes.entrySet()) {
            if (axesTag.contains(entry.getKey())) {
                entry.getValue().readFromTag(axesTag.getCompound(entry.getKey()));
            }
        }

        // ------------------------------------WIRELESS BINDINGS------------------------------------
        CompoundTag bindingsTag = tag.getCompound("Bindings");
        for (Map.Entry<String, FrequencyBinding> entry : frequencyBindings.entrySet()) {
            if (bindingsTag.contains(entry.getKey())) {
                entry.getValue().read(bindingsTag.getCompound(entry.getKey()), provider);
            } else {
                entry.getValue().clear();
            }
        }

        CompoundTag inputBindingsTag = tag.getCompound("InputBindings");
        for (Map.Entry<String, FrequencyBinding> entry : inputFrequencyBindings.entrySet()) {
            if (inputBindingsTag.contains(entry.getKey())) {
                entry.getValue().read(inputBindingsTag.getCompound(entry.getKey()), provider);
            } else {
                entry.getValue().clear();
            }
        }

        // ------------------------------------TARGETS AND LOCAL OUTPUTS------------------------------------
        CompoundTag directTargetsTag = tag.getCompound("DirectTargets");
        for (String channelId : channels.keySet()) {
            directTargets.put(channelId, directTargetsTag.contains(channelId)
                    ? sanitizeDirectTarget(ControllerDirectTargetReference.fromTag(directTargetsTag.getCompound(channelId)))
                    : null);
        }

        CompoundTag inputTargetsTag = tag.getCompound("InputTargets");
        for (String channelId : channels.keySet()) {
            inputTargets.put(channelId, inputTargetsTag.contains(channelId)
                ? sanitizeDirectTarget(ControllerDirectTargetReference.fromTag(inputTargetsTag.getCompound(channelId)))
                : null);
            sampledInputActive.put(channelId, false);
        }

        CompoundTag localOutputsTag = tag.getCompound("LocalOutputSides");
        for (String channelId : channels.keySet()) {
            localOutputSides.put(channelId, localOutputsTag.contains(channelId)
                    ? Direction.byName(localOutputsTag.getString(channelId))
                    : null);
        }

        CompoundTag keyBindingsTag = tag.getCompound("KeyBindings");
        for (String channelId : channels.keySet()) {
            keyBindings.put(channelId, keyBindingsTag.contains(channelId) ? keyBindingsTag.getInt(channelId) : -1);
        }

        CompoundTag bindingModesTag = tag.getCompound("BindingModes");
        for (String channelId : channels.keySet()) {
            String modeId = bindingModesTag.contains(channelId) ? bindingModesTag.getString(channelId) : "standard";
            bindingModes.put(channelId, modeId == null || modeId.isBlank() ? "standard" : modeId);
        }

        CompoundTag bindingPresetsTag = tag.getCompound("BindingPresets");
        for (String channelId : channels.keySet()) {
            String presetId = bindingPresetsTag.contains(channelId) ? bindingPresetsTag.getString(channelId) : "none";
            bindingPresets.put(channelId, presetId == null || presetId.isBlank() ? "none" : presetId);
        }

        storedTargets.clear();
        ListTag storedTargetsTag = tag.getList("StoredTargets", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < storedTargetsTag.size(); idx++) {
            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(storedTargetsTag.getCompound(idx));
            if (node != null && node.isValid()) {
                storedTargets.add(node);
            }
        }
        loadingControllerData = true;
        try {
            if (tag.contains("LinkerSlot", Tag.TAG_COMPOUND)) {
                linkerSlot.deserializeNBT(provider, tag.getCompound("LinkerSlot"));
            } else {
                linkerSlot.setStackInSlot(0, ItemStack.EMPTY);
            }
        } finally {
            loadingControllerData = false;
        }
        pendingAssemblyBindingRemap = false;
        assemblySourceSubLevelId = null;
        assemblyOldPos = null;
        assemblyNewPos = null;
        assemblySourceWasWorld = false;
        if (tag.contains(ASSEMBLY_TRANSFER_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag transferTag = tag.getCompound(ASSEMBLY_TRANSFER_TAG);
            assemblySourceWasWorld = transferTag.getBoolean("SourceWasWorld");
            assemblySourceSubLevelId = transferTag.hasUUID("SourceSubLevelId")
                    ? transferTag.getUUID("SourceSubLevelId")
                    : null;
            assemblyOldPos = transferTag.contains("OldPos") ? BlockPos.of(transferTag.getLong("OldPos")) : null;
            assemblyNewPos = transferTag.contains("NewPos") ? BlockPos.of(transferTag.getLong("NewPos")) : null;
            pendingAssemblyBindingRemap = assemblyOldPos != null && assemblyNewPos != null;
        }
        syncFromInsertedLinker();
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;

        // -----------------------------------------------------CUSTOM INPUTS-----------------------------------------------------
        customKeyEntries.clear();
        customFrequencyBindings.clear();
        customWirelessTargets.clear();
        customInputFrequencyBindings.clear();
        customInputLinkTargets.clear();
        pressedCustomEntryIds.clear();
        pressedCustomEntryStepDownIds.clear();
        sampledCustomEntryIds.clear();
        sampledCustomEntryValues.clear();
        customEntryChannels.clear();
        ListTag customKeyEntriesTag = tag.getList("CustomKeyEntries", Tag.TAG_COMPOUND);
        for (int i = 0; i < customKeyEntriesTag.size(); i++) {
            CustomKeyEntry entry = CustomKeyEntry.fromTag(customKeyEntriesTag.getCompound(i), provider);
            if (entry != null) {
                customKeyEntries.add(entry);
            ensureCustomEntryChannel(entry);
                FrequencyBinding binding = new FrequencyBinding(entry.id());
                binding.set(entry.first, entry.second);
                customFrequencyBindings.put(entry.id(), binding);
                customWirelessTargets.put(entry.id(), new ControllerLinkTarget(entry.id(), binding));
                FrequencyBinding inputBinding = new FrequencyBinding(entry.id() + "#input");
                inputBinding.set(entry.inputFirst, entry.inputSecond);
                customInputFrequencyBindings.put(entry.id(), inputBinding);
                customInputLinkTargets.put(entry.id(), new ControllerLinkInputTarget(entry.id(), inputBinding));
            }
        }
        ometerOutputSignal = tag.contains("OmeterOutputSignal")
                ? clampOmeterSignal(tag.getInt("OmeterOutputSignal"))
                : getOmeterOutput();
    }

    // Save the controller data
    public CompoundTag saveControllerData(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        writeControllerBody(tag, provider);
        writeAdditionalControllerManifestData(tag, provider);
        return tag;
    }

    // Load the controller data
    public void loadControllerData(CompoundTag tag, HolderLookup.Provider provider) {
        read(tag == null ? new CompoundTag() : tag, provider, false);
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack linker = getStoredLinker();
            ContraptionNetworkLinkerData.migrateLegacyStorage(linker);
            ContraptionNetworkLinkerSnapshotPayload.sendIfChanged(serverPlayer, linker);
        }
        return new AnalogueContraptionControllerMenu(containerId, playerInventory, this);
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

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.analogue_controller.config.title");
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
        buffer.writeVarInt(storedTargets.size());
        for (ControllerDiscoveryNode node : storedTargets) {
            buffer.writeNbt(node == null ? new CompoundTag() : node.toTag());
        }
        buffer.writeVarInt(AnalogueControlChannel.values().length);
        for (AnalogueControlChannel definition : AnalogueControlChannel.values()) {
            AnalogueChannel channel = channels.get(definition.id());
            buffer.writeUtf(definition.id());
            buffer.writeUtf(channel == null ? AnalogueChannelMode.RAMP.name().toLowerCase(Locale.ROOT) : channel.getMode().name().toLowerCase(Locale.ROOT));
            buffer.writeFloat(channel == null ? (float) DEFAULT_RAMP_RATE : (float) channel.getRiseRate());
            buffer.writeFloat(channel == null ? (float) DEFAULT_RAMP_RATE : (float) channel.getFallRate());
            buffer.writeFloat(channel == null ? (float) DEFAULT_STEP_AMOUNT : (float) channel.getStepAmount());
            buffer.writeFloat(channel == null ? 0.0f : (float) channel.getDeadzone());
            buffer.writeFloat(channel == null ? (float) DEFAULT_SMOOTHING : (float) channel.getSmoothing());
            Direction localSide = getLocalOutputSide(definition.id());
            buffer.writeUtf(localSide == null ? "" : localSide.getSerializedName());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(getFrequencyFirst(definition.id())));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(getFrequencySecond(definition.id())));

            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(getInputFrequencyFirst(definition.id())));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(getInputFrequencySecond(definition.id())));
            buffer.writeInt(getKeyBinding(definition.id()));
            ControllerDirectTargetReference directTarget = getDirectTarget(definition.id());
            buffer.writeNbt(directTarget == null ? new CompoundTag() : directTarget.toTag());
            ControllerDirectTargetReference inputTarget = getInputTarget(definition.id());
            buffer.writeNbt(inputTarget == null ? new CompoundTag() : inputTarget.toTag());

            buffer.writeUtf(getBindingMode(definition.id()));
            buffer.writeUtf(getBindingPreset(definition.id()));
        }

        buffer.writeVarInt(customKeyEntries.size());
        for (CustomKeyEntry entry : customKeyEntries) {
            buffer.writeUtf(entry.id());
            buffer.writeInt(entry.keyCode);
            buffer.writeInt(entry.stepDownKeyCode);
            buffer.writeUtf(entry.label == null ? "Custom" : entry.label);
            buffer.writeUtf(entry.mode == null ? "ramp" : entry.mode.name().toLowerCase(Locale.ROOT));
            buffer.writeFloat((float) entry.riseRate);
            buffer.writeFloat((float) entry.fallRate);
            buffer.writeFloat((float) entry.stepAmount);
            buffer.writeFloat((float) entry.stepDownAmount);
            buffer.writeFloat((float) entry.deadzone);
            buffer.writeFloat((float) entry.smoothing);
            buffer.writeUtf(entry.localOutputSide == null ? "" : entry.localOutputSide.getSerializedName());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(entry.first));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(entry.second));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(entry.inputFirst));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, copyMenuStack(entry.inputSecond));
            buffer.writeNbt(entry.directTarget == null ? new CompoundTag() : entry.directTarget.toTag());
            buffer.writeNbt(entry.inputTarget == null ? new CompoundTag() : entry.inputTarget.toTag());
            buffer.writeUtf(entry.bindingPreset == null || entry.bindingPreset.isBlank() ? "none" : entry.bindingPreset);
        }
    }

    // Copy the menu stack
    private static ItemStack copyMenuStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Get the linker slot handler
    public ItemStackHandler getLinkerSlotHandler() {
        return linkerSlot;
    }

    // Get the stored linker
    public ItemStack getStoredLinker() {
        ItemStack stack = linkerSlot.getStackInSlot(0);
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    // Get the active stored linker
    private ItemStack getActiveStoredLinker() {
        ItemStack stack = getStoredLinker();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (stack.isEmpty() || serverLevel == null) {
            return stack;
        }
        return ContraptionNetworkLinkerTracker.get(serverLevel.getServer()).canMutateLinker(serverLevel, stack)
                ? stack
                : ItemStack.EMPTY;
    }

    // Handle the tracked linker data updated event
    public void onTrackedLinkerDataUpdated() {
        if (level == null || level.isClientSide) {
            return;
        }
        syncFromInsertedLinker();

        if (!pendingAssemblyBindingRemap) {
            replayCurrentOutputs();
        }
        setChanged();
        sendData();
    }

    // Sync the tracked linker registry
    public void syncTrackedLinkerRegistry() {
        syncTrackedLinkerRegistry(false);
    }

    // Sync the tracked linker registry
    private boolean syncTrackedLinkerRegistry(boolean reconcileNow) {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return false;
        }
        ContraptionNetworkLinkerTracker tracker = ContraptionNetworkLinkerTracker.get(level.getServer());
        if (reconcileNow) {
            return tracker.syncAndReconcileController(this);
        }
        tracker.syncController(this);
        return false;
    }

    // Get the linker discovery nodes
    private List<ControllerDiscoveryNode> linkerDiscoveryNodes() {
        ItemStack linker = getActiveStoredLinker();
        if (linker.isEmpty()) {
            return List.of();
        }
        return ContraptionNetworkLinkerData.toDiscoveryNodes(linker);
    }

    // Sync the inserted linker
    private void syncFromInsertedLinker() {
        ItemStack storedLinker = getStoredLinker();
        if (level != null && !level.isClientSide && !storedLinker.isEmpty()) {
            ContraptionNetworkLinkerData.ensureStoredIdentity(storedLinker);
        }
        ItemStack linker = getActiveStoredLinker();

        if (!pendingAssemblyBindingRemap) {
            clearRemovedLinkerOutputs(linker);
        }
        if (linker.isEmpty()) {
            clearLinkerFaceBindings();
            clearLinkerBoundCustomEntries();
            invalidateControllerRuntimeProgram();
            onInsertedLinkerSynchronized(linker);
            return;
        }

        Map<String, ContraptionNetworkLinkerData.ChannelBind> binds = ContraptionNetworkLinkerData.readChannelBindings(linker);
        if (binds.isEmpty()) {

            for (String channelId : channels.keySet()) {
                ControllerDirectTargetReference existingDirect = directTargets.get(channelId);
                ControllerDirectTargetReference existingInput = inputTargets.get(channelId);
                if (!ContraptionNetworkLinkerData.isLinkerFaceTarget(existingDirect)
                        && !ContraptionNetworkLinkerData.isLinkerFaceTarget(existingInput)) {
                    continue;
                }
                ContraptionNetworkLinkerData.writeChannelBinding(linker, channelId, existingDirect, existingInput);
            }
            binds = ContraptionNetworkLinkerData.readChannelBindings(linker);
        }

        clearLinkerFaceBindings();
        for (String channelId : channels.keySet()) {
            ContraptionNetworkLinkerData.ChannelBind bind = binds.get(channelId);
            if (bind == null) {
                continue;
            }
            directTargets.put(channelId, sanitizeDirectTarget(bind.directTarget()));
            inputTargets.put(channelId, sanitizeDirectTarget(bind.inputTarget()));
        }

        List<CompoundTag> customBindTags = ContraptionNetworkLinkerData.readCustomEntryBindings(linker);
        if (customBindTags.isEmpty() && level != null) {
            List<CompoundTag> existingCustomTags = new ArrayList<>();
            HolderLookup.Provider provider = level.registryAccess();
            for (CustomKeyEntry entry : customKeyEntries) {
                if (!isLinkerBoundCustomEntry(entry)) {
                    continue;
                }
                existingCustomTags.add(entry.toTag(provider));
            }
            if (!existingCustomTags.isEmpty()) {

                ContraptionNetworkLinkerData.writeCustomEntryBindings(linker, existingCustomTags);
                customBindTags = ContraptionNetworkLinkerData.readCustomEntryBindings(linker);
            }
        }

        clearLinkerBoundCustomEntries();
        if (!customBindTags.isEmpty()) {
            loadLinkerBindings(linker);
        }
        invalidateControllerRuntimeProgram();
        onInsertedLinkerSynchronized(linker);
    }

    // Handle the inserted linker synchronized event
    protected void onInsertedLinkerSynchronized(ItemStack linker) {
    }

    // Clear the linker face bindings
    private void clearLinkerFaceBindings() {
        for (String channelId : channels.keySet()) {
            ControllerDirectTargetReference direct = directTargets.get(channelId);
            if (ContraptionNetworkLinkerData.isLinkerFaceTarget(direct)) {
                directTargets.put(channelId, null);
            }
            ControllerDirectTargetReference input = inputTargets.get(channelId);
            if (ContraptionNetworkLinkerData.isLinkerFaceTarget(input)) {
                inputTargets.put(channelId, null);
            }
        }
    }

    // Clear the linker bound custom entries
    private void clearLinkerBoundCustomEntries() {
        List<String> toRemove = new ArrayList<>();
        for (CustomKeyEntry entry : customKeyEntries) {
            if (isLinkerBoundCustomEntry(entry)) {
                toRemove.add(entry.id());
            }
        }
        for (String id : toRemove) {

            removeCustomKey(id, false, false, false);
        }
    }

    // Load the linker bindings
    private void loadLinkerBindings(ItemStack linker) {
        if (level == null) {
            return;
        }
        HolderLookup.Provider provider = level.registryAccess();
        for (CompoundTag tag : ContraptionNetworkLinkerData.readCustomEntryBindings(linker)) {
            CustomKeyEntry entry = CustomKeyEntry.fromTag(tag, provider);
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            removeCustomKey(entry.id(), false, false, false);
            customKeyEntries.add(entry);
            ensureCustomEntryChannel(entry);

            FrequencyBinding binding = new FrequencyBinding(entry.id());
            binding.set(entry.first, entry.second);
            customFrequencyBindings.put(entry.id(), binding);
            ControllerLinkTarget outputTarget = new ControllerLinkTarget(entry.id(), binding);
            customWirelessTargets.put(entry.id(), outputTarget);

            FrequencyBinding inputBinding = new FrequencyBinding(entry.id() + "#input");
            inputBinding.set(entry.inputFirst, entry.inputSecond);
            customInputFrequencyBindings.put(entry.id(), inputBinding);
            ControllerLinkInputTarget inputTarget = new ControllerLinkInputTarget(entry.id(), inputBinding);
            customInputLinkTargets.put(entry.id(), inputTarget);

            if (!level.isClientSide) {
                outputTarget.refreshRegistration(level);
                inputTarget.refreshRegistration(level);
            }
        }
    }

    // Save the linker binding
    private void saveLinkerBinding(CustomKeyEntry entry) {
        if (entry == null) {
            return;
        }
        ItemStack linker = getActiveStoredLinker();
        if (linker.isEmpty()) {
            return;
        }

        List<CompoundTag> storedTags = new ArrayList<>(ContraptionNetworkLinkerData.readCustomEntryBindings(linker));
        String id = entry.id();
        storedTags.removeIf(tag -> Objects.equals(readCustomEntryId(tag), id));
        if (isLinkerBoundCustomEntry(entry) && level != null) {
            storedTags.add(entry.toTag(level.registryAccess()));
        }
        ContraptionNetworkLinkerData.writeCustomEntryBindings(linker, storedTags);
    }

    // Remove the linker binding
    private void removeLinkerBinding(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        ItemStack linker = getActiveStoredLinker();
        if (linker.isEmpty()) {
            return;
        }

        List<CompoundTag> storedTags = new ArrayList<>(ContraptionNetworkLinkerData.readCustomEntryBindings(linker));
        boolean changed = storedTags.removeIf(tag -> Objects.equals(readCustomEntryId(tag), id));
        if (changed) {
            ContraptionNetworkLinkerData.writeCustomEntryBindings(linker, storedTags);
        }
    }

    // Read the custom entry id
    private static @Nullable String readCustomEntryId(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (tag.contains("Id", Tag.TAG_STRING)) {
            return tag.getString("Id");
        }
        if (tag.contains("id", Tag.TAG_STRING)) {
            return tag.getString("id");
        }
        return null;
    }

    // Check if this is a linker bound custom entry
    private static boolean isLinkerBoundCustomEntry(@Nullable CustomKeyEntry entry) {
        if (entry == null) {
            return false;
        }
        return ContraptionNetworkLinkerData.isLinkerFaceTarget(entry.directTarget)
                || ContraptionNetworkLinkerData.isLinkerFaceTarget(entry.inputTarget);
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.analogue_contraption_controller")));
        int activeAxes = 0;
        for (String axisId : axes.keySet()) {
            AnalogueAxis axis = axes.get(axisId);
            if (axis == null) {
                continue;
            }
            double signedValue = axis.getSignedValue();
            if (!showDetails && Math.abs(signedValue) < 0.01D) {
                continue;
            }
            String label = switch (axisId) {
                case "pitch" -> "Pitch";
                case "roll" -> "Roll";
                case "yaw" -> "Yaw";
                case "throttle" -> "Throttle";
                case "strafe" -> "Strafe";
                case "lift" -> "Lift";
                default -> axisId.substring(0, 1).toUpperCase(Locale.ROOT) + axisId.substring(1);
            };
            tooltip.add(CTTooltipHelper.line(label,
                    CTTooltipHelper.value(CTTooltipHelper.signedDecimal(signedValue), ChatFormatting.AQUA)));
            activeAxes++;
        }
        if (!showDetails && activeAxes == 0) {
            tooltip.add(CTTooltipHelper.line("State", CTTooltipHelper.readyIdle(false)));
        }
        if (showDetails) {
            for (Direction dir : OUTPUT_DIRECTIONS) {
                tooltip.add(CTTooltipHelper.line(dir.getSerializedName().substring(0, 1).toUpperCase(Locale.ROOT)
                                + dir.getSerializedName().substring(1) + " Face",
                        CTTooltipHelper.value(Integer.toString(getLocalOutputSignal(dir)), ChatFormatting.YELLOW)));
            }
        }
        return true;
    }

    // Create the channels
    private void createChannels() {
        for (AnalogueControlChannel definition : AnalogueControlChannel.values()) {
            AnalogueChannel channel = AnalogueChannel.unsigned(definition.id());
            channel.setMode(definition.defaultMode());
            channel.setRiseRate(DEFAULT_RAMP_RATE);
            channel.setFallRate(DEFAULT_RAMP_RATE);
            channel.setDeadzone(0.0D);
            channel.setSmoothing(DEFAULT_SMOOTHING);
            channel.setStepAmount(DEFAULT_STEP_AMOUNT);
            channel.setResetToZero(definition.defaultMode() != AnalogueChannelMode.STEP && definition.defaultMode() != AnalogueChannelMode.LATCH);
            channel.setRepeatWhileHeld(false);
            if (definition.defaultMode() == AnalogueChannelMode.STEP) {
                channel.setFallRate(0.0D);
            }
            channels.put(definition.id(), channel);
            FrequencyBinding binding = new FrequencyBinding(definition.id());
            frequencyBindings.put(definition.id(), binding);
            wirelessTargets.put(definition.id(), new ControllerLinkTarget(definition.id(), binding));
            directTargets.put(definition.id(), null);
            inputTargets.put(definition.id(), null);

            FrequencyBinding inputBinding = new FrequencyBinding(definition.id() + "#input");
            inputFrequencyBindings.put(definition.id(), inputBinding);
            inputLinkTargets.put(definition.id(), new ControllerLinkInputTarget(definition.id(), inputBinding));
            sampledInputActive.put(definition.id(), false);
            localOutputSides.put(definition.id(), null);
            keyBindings.put(definition.id(), -1);
            bindingModes.put(definition.id(), "standard");
            bindingPresets.put(definition.id(), "none");
        }
    }

    // Create the axes
    private void createAxes() {
        axes.put("pitch", new AnalogueAxis("pitch", channels.get(AnalogueControlChannel.PITCH_DOWN.id()), channels.get(AnalogueControlChannel.PITCH_UP.id())));
        axes.put("roll", new AnalogueAxis("roll", channels.get(AnalogueControlChannel.ROLL_LEFT.id()), channels.get(AnalogueControlChannel.ROLL_RIGHT.id())));
        axes.put("yaw", new AnalogueAxis("yaw", channels.get(AnalogueControlChannel.YAW_LEFT.id()), channels.get(AnalogueControlChannel.YAW_RIGHT.id())));
        axes.put("throttle", new AnalogueAxis("throttle", channels.get(AnalogueControlChannel.THROTTLE_DOWN.id()), channels.get(AnalogueControlChannel.THROTTLE_UP.id())));
        axes.put("strafe", new AnalogueAxis("strafe", channels.get(AnalogueControlChannel.STRAFE_LEFT.id()), channels.get(AnalogueControlChannel.STRAFE_RIGHT.id())));
        axes.put("lift", new AnalogueAxis("lift", channels.get(AnalogueControlChannel.LIFT_DOWN.id()), channels.get(AnalogueControlChannel.LIFT_UP.id())));
        for (AnalogueAxis axis : axes.values()) {
            axis.setSmoothing(DEFAULT_SMOOTHING);
        }
    }

    // Refresh the wireless network
    private void refreshWirelessNetwork(boolean initialize) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ControllerLinkTarget target : wirelessTargets.values()) {
            refreshWirelessTarget(target, initialize);
        }
        for (ControllerLinkTarget target : customWirelessTargets.values()) {
            refreshWirelessTarget(target, initialize);
        }
        for (ControllerLinkInputTarget target : inputLinkTargets.values()) {
            refreshWirelessInputTarget(target, initialize);
        }
        for (ControllerLinkInputTarget target : customInputLinkTargets.values()) {
            refreshWirelessInputTarget(target, initialize);
        }
    }

    // Refresh the wireless target
    private void refreshWirelessTarget(ControllerLinkTarget target, boolean initialize) {
        if (target == null || level == null || level.isClientSide) {
            return;
        }
        if (initialize) {
            target.updateBinding(level, target.binding());
        } else {
            target.refreshRegistration(level);
        }
    }

    // Refresh the wireless input target
    private void refreshWirelessInputTarget(ControllerLinkInputTarget target, boolean initialize) {
        if (target == null || level == null || level.isClientSide) {
            return;
        }
        if (initialize) {
            target.updateBinding(level, target.binding());
        } else {
            target.refreshRegistration(level);
        }
    }

    // Refresh the wireless registrations
    private void refreshWirelessRegistrations() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ControllerLinkTarget target : wirelessTargets.values()) {
            target.refreshRegistration(level);
        }
        for (ControllerLinkTarget target : customWirelessTargets.values()) {
            target.refreshRegistration(level);
        }
        for (ControllerLinkInputTarget target : inputLinkTargets.values()) {
            target.refreshRegistration(level);
        }
        for (ControllerLinkInputTarget target : customInputLinkTargets.values()) {
            target.refreshRegistration(level);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       LINKER / DISCOVERY
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remap the assembly bindings when ready
    private boolean remapAssemblyBindingsIfReady() {
        if (!pendingAssemblyBindingRemap) {
            return true;
        }
        if (level == null || level.isClientSide) {
            return false;
        }
        if (assemblyOldPos == null || assemblyNewPos == null) {
            clearAssemblyTransfer();
            return true;
        }

        UUID sourceSubLevelId = assemblySourceWasWorld ? null : assemblySourceSubLevelId;
        UUID destinationSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (!isAssemblyDestinationReady(destinationSubLevelId)) {
            return false;
        }

        boolean changed = syncTrackedLinkerRegistry(true);
        if (changed) {
            syncFromInsertedLinker();
        }

        // ------------------------------------POSITION REMAP------------------------------------
        BlockPos offset = assemblyNewPos.subtract(assemblyOldPos);
        if (Objects.equals(sourceSubLevelId, destinationSubLevelId) && BlockPos.ZERO.equals(offset)) {
            clearAssemblyTransfer();
            replayCurrentOutputs();
            setChanged();
            sendData();
            return true;
        }

        // ------------------------------------CHANNEL ROUTES------------------------------------
        clearAssemblyRouteSources(assemblyOldPos);
        for (String channelId : channels.keySet()) {
            ControllerDirectTargetReference direct = remapMovedTarget(directTargets.get(channelId),
                    sourceSubLevelId, destinationSubLevelId, offset);
            ControllerDirectTargetReference input = remapMovedTarget(inputTargets.get(channelId),
                    sourceSubLevelId, destinationSubLevelId, offset);
            if (!Objects.equals(directTargets.get(channelId), direct)) {
                directTargets.put(channelId, direct);
                changed = true;
            }
            if (!Objects.equals(inputTargets.get(channelId), input)) {
                inputTargets.put(channelId, input);
                changed = true;
            }
        }

        // -----------------------------------------------------CUSTOM ROUTES-----------------------------------------------------
        for (CustomKeyEntry entry : customKeyEntries) {
            ControllerDirectTargetReference direct = remapMovedTarget(entry.directTarget,
                    sourceSubLevelId, destinationSubLevelId, offset);
            ControllerDirectTargetReference input = remapMovedTarget(entry.inputTarget,
                    sourceSubLevelId, destinationSubLevelId, offset);
            if (!Objects.equals(entry.directTarget, direct)) {
                entry.directTarget = direct;
                changed = true;
            }
            if (!Objects.equals(entry.inputTarget, input)) {
                entry.inputTarget = input;
                changed = true;
            }
        }

        // ------------------------------------STORED TARGETS------------------------------------
        for (int idx = 0; idx < storedTargets.size(); idx++) {
            ControllerDiscoveryNode node = storedTargets.get(idx);
            ControllerDiscoveryNode remapped = remapMovedDiscoveryNode(node, sourceSubLevelId, destinationSubLevelId, offset);
            if (!Objects.equals(node, remapped)) {
                storedTargets.set(idx, remapped);
                changed = true;
            }
        }

        // -----------------------------------------------------LINKER ROUTES-----------------------------------------------------
        ItemStack linker = getActiveStoredLinker();
        if (!linker.isEmpty()) {
            List<ContraptionNetworkLinkerData.LinkedTarget> existingTargets = ContraptionNetworkLinkerData.readTargets(linker);
            List<ContraptionNetworkLinkerData.LinkedTarget> remappedTargets = new ArrayList<>();
            boolean linkerTargetsChanged = false;
            for (ContraptionNetworkLinkerData.LinkedTarget target : existingTargets) {
                List<ContraptionNetworkLinkerData.LinkedTarget> remapped =
                        remapMovedLinkerTarget(target, sourceSubLevelId, destinationSubLevelId, offset);
                remappedTargets.addAll(remapped);
                linkerTargetsChanged |= remapped.size() != 1 || !Objects.equals(target, remapped.getFirst());
            }
            if (linkerTargetsChanged) {
                ContraptionNetworkLinkerData.writeTargets(linker, remappedTargets, ContraptionNetworkLinkerData.getEditMode(linker));
                changed = true;
            }
            for (String channelId : channels.keySet()) {
                ContraptionNetworkLinkerData.writeChannelBinding(
                        linker, channelId, directTargets.get(channelId), inputTargets.get(channelId));
            }
            List<CompoundTag> customBindings = new ArrayList<>();
            for (CustomKeyEntry entry : customKeyEntries) {
                if (isLinkerBoundCustomEntry(entry)) {
                    customBindings.add(entry.toTag(level.registryAccess()));
                }
            }
            ContraptionNetworkLinkerData.writeCustomEntryBindings(linker, customBindings);
        }

        if (syncTrackedLinkerRegistry(true)) {
            syncFromInsertedLinker();
            changed = true;
        }
        changed |= onAssemblyTransferCompleted(sourceSubLevelId, destinationSubLevelId, offset);
        if (changed) {
            invalidateControllerRuntimeProgram();
        }
        clearAssemblyTransfer();
        saveControllerManifestNow();
        replayCurrentOutputs();
        setChanged();
        sendData();
        return true;
    }

    // Complete the assembly transfer
    protected boolean onAssemblyTransferCompleted(@Nullable UUID sourceSubLevelId,
                                                  @Nullable UUID destinationSubLevelId,
                                                  BlockPos offset) {
        return false;
    }

    // Remap the moved target
    protected @Nullable ControllerDirectTargetReference remapMovedTarget(
            @Nullable ControllerDirectTargetReference target,
            @Nullable UUID sourceSubLevelId,
            @Nullable UUID destinationSubLevelId,
            BlockPos offset) {
        if (target == null || target.blockPos() == null
                || !Objects.equals(target.subLevelId(), sourceSubLevelId)) {
            return target;
        }
        BlockPos movedPos = target.blockPos().offset(offset);
        if (!shouldRemapMovedReference(target, sourceSubLevelId, destinationSubLevelId, movedPos)) {
            return target;
        }
        String sourceToken = sourceSubLevelId == null ? "world" : sourceSubLevelId.toString();
        String destinationToken = destinationSubLevelId == null ? "world" : destinationSubLevelId.toString();
        String sourceLocator = "@" + target.blockPos().asLong() + "#" + sourceToken;
        String destinationLocator = "@" + movedPos.asLong() + "#" + destinationToken;
        String targetId = target.targetId().replace(sourceLocator, destinationLocator);
        String groupId = target.groupId();
        if (groupId.equals(sourceSubLevelId == null ? "world" : "sublevel:" + sourceSubLevelId)) {
            groupId = destinationSubLevelId == null ? "world" : "sublevel:" + destinationSubLevelId;
        }
        return new ControllerDirectTargetReference(
                targetId, target.targetTypeId(), groupId, target.label(), target.compatModeId(), destinationSubLevelId, movedPos);
    }

    // Remap the moved discovery node
    protected @Nullable ControllerDiscoveryNode remapMovedDiscoveryNode(
            @Nullable ControllerDiscoveryNode node,
            @Nullable UUID sourceSubLevelId,
            @Nullable UUID destinationSubLevelId,
            BlockPos offset) {
        if (node == null || node.blockPos() == null || !Objects.equals(node.subLevelId(), sourceSubLevelId)) {
            return node;
        }
        BlockPos movedPos = node.blockPos().offset(offset);
        if (!shouldRemapMovedDiscoveryNode(node, sourceSubLevelId, destinationSubLevelId, movedPos)) {
            return node;
        }
        ControllerDirectTargetReference remapped = remapMovedTarget(
                node.asDirectTargetReference(), sourceSubLevelId, destinationSubLevelId, offset);
        if (remapped == null) {
            return node;
        }
        return new ControllerDiscoveryNode(
                remapped.targetId(), node.kind(), remapped.groupId(), node.blockId(), node.label(),
                destinationSubLevelId, movedPos);
    }

    // Remap the moved linker target
    private List<ContraptionNetworkLinkerData.LinkedTarget> remapMovedLinkerTarget(
            ContraptionNetworkLinkerData.LinkedTarget target,
            @Nullable UUID sourceSubLevelId,
            @Nullable UUID destinationSubLevelId,
            BlockPos offset) {
        if (target == null) {
            return List.of();
        }
        if (!Objects.equals(target.subLevelId(), sourceSubLevelId)) {
            return List.of(target);
        }
        BlockPos movedPos = target.blockPos().offset(offset);
        if (!isLinkerPlaneTarget(target)) {
            if (!shouldRemapMovedLinkerTarget(target, sourceSubLevelId, destinationSubLevelId, movedPos)) {
                return List.of(target);
            }
            return List.of(new ContraptionNetworkLinkerData.LinkedTarget(
                    movedPos, destinationSubLevelId, target.blockId(), target.label(),
                    target.mode(), target.scope(), target.faces()));
        }
        if (!hasTargetAt(sourceSubLevelId, target.blockPos()) && hasTargetAt(destinationSubLevelId, movedPos)) {
            return List.of(new ContraptionNetworkLinkerData.LinkedTarget(
                    movedPos, destinationSubLevelId, target.blockId(), target.label(),
                    target.mode(), target.scope(), target.faces()));
        }
        List<ContraptionNetworkLinkerData.LinkedFace> stayingFaces = new ArrayList<>();
        List<ContraptionNetworkLinkerData.LinkedFace> movingFaces = new ArrayList<>();
        for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
            if (face != null && didAttachedPlaneTargetMove(
                    target.blockPos(), face.face(), sourceSubLevelId, destinationSubLevelId, movedPos)) {
                movingFaces.add(face);
            } else {
                stayingFaces.add(face);
            }
        }
        if (movingFaces.isEmpty()) {
            return List.of(target);
        }
        List<ContraptionNetworkLinkerData.LinkedTarget> remappedTargets = new ArrayList<>();
        if (!stayingFaces.isEmpty()) {
            remappedTargets.add(new ContraptionNetworkLinkerData.LinkedTarget(
                    target.blockPos(), sourceSubLevelId, target.blockId(), target.label(),
                    target.mode(), target.scope(), stayingFaces));
        }
        remappedTargets.add(new ContraptionNetworkLinkerData.LinkedTarget(
                movedPos, destinationSubLevelId, target.blockId(), target.label(),
                target.mode(), target.scope(), movingFaces));
        return remappedTargets;
    }

    // Check if this should remap moved reference
    private boolean shouldRemapMovedReference(@Nullable ControllerDirectTargetReference target,
                                              @Nullable UUID sourceSubLevelId,
                                              @Nullable UUID destinationSubLevelId,
                                              BlockPos movedPos) {
        if (target == null || target.blockPos() == null) {
            return false;
        }
        if (!hasTargetAt(sourceSubLevelId, target.blockPos()) && hasTargetAt(destinationSubLevelId, movedPos)) {
            return true;
        }
        if (!ContraptionNetworkLinkerData.isLinkerFaceTarget(target)) {
            return false;
        }
        List<Direction> faces = facesFromLinkerTargetId(target.targetId());
        Direction selectedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target);
        if (selectedFace != null && !faces.contains(selectedFace)) {
            faces = new ArrayList<>(faces);
            faces.add(selectedFace);
        }
        return didAttachedPlaneTargetsMove(target.blockPos(), faces, sourceSubLevelId, destinationSubLevelId, movedPos);
    }

    // Check if this should remap moved discovery node
    private boolean shouldRemapMovedDiscoveryNode(@Nullable ControllerDiscoveryNode node,
                                                 @Nullable UUID sourceSubLevelId,
                                                 @Nullable UUID destinationSubLevelId,
                                                 BlockPos movedPos) {
        if (node == null || node.blockPos() == null) {
            return false;
        }
        if (!hasTargetAt(sourceSubLevelId, node.blockPos()) && hasTargetAt(destinationSubLevelId, movedPos)) {
            return true;
        }
        if (!isLinkerPlaneBlockId(node.blockId())) {
            return false;
        }
        return didAttachedPlaneTargetsMove(node.blockPos(), facesFromLinkerTargetId(node.nodeId()),
                sourceSubLevelId, destinationSubLevelId, movedPos);
    }

    // Check if this should remap moved linker target
    private boolean shouldRemapMovedLinkerTarget(ContraptionNetworkLinkerData.LinkedTarget target,
                                                @Nullable UUID sourceSubLevelId,
                                                @Nullable UUID destinationSubLevelId,
                                                BlockPos movedPos) {
        if (!hasTargetAt(sourceSubLevelId, target.blockPos()) && hasTargetAt(destinationSubLevelId, movedPos)) {
            return true;
        }
        if (!isLinkerPlaneTarget(target)) {
            return false;
        }
        List<Direction> faces = new ArrayList<>();
        for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
            faces.add(face.face());
        }
        return didAttachedPlaneTargetsMove(target.blockPos(), faces, sourceSubLevelId, destinationSubLevelId, movedPos);
    }

    // Check if any attached plane target moved
    private boolean didAttachedPlaneTargetsMove(BlockPos planePos,
                                                List<Direction> faces,
                                                @Nullable UUID sourceSubLevelId,
                                                @Nullable UUID destinationSubLevelId,
                                                BlockPos movedPlanePos) {
        if (planePos == null || movedPlanePos == null || faces == null || faces.isEmpty()) {
            return false;
        }
        boolean foundMovedAttachedBlock = false;
        for (Direction face : faces) {
            if (didAttachedPlaneTargetMove(planePos, face, sourceSubLevelId, destinationSubLevelId, movedPlanePos)) {
                foundMovedAttachedBlock = true;
            }
        }
        return foundMovedAttachedBlock;
    }

    // Check if the attached plane target moved
    private boolean didAttachedPlaneTargetMove(BlockPos planePos,
                                               @Nullable Direction face,
                                               @Nullable UUID sourceSubLevelId,
                                               @Nullable UUID destinationSubLevelId,
                                               BlockPos movedPlanePos) {
        if (planePos == null || face == null || movedPlanePos == null) {
            return false;
        }
        BlockPos oldAttachedPos = planePos.relative(face.getOpposite());
        BlockPos newAttachedPos = movedPlanePos.relative(face.getOpposite());
        return !hasTargetAt(sourceSubLevelId, oldAttachedPos)
                && hasTargetAt(destinationSubLevelId, newAttachedPos);
    }

    // Get the faces from linker target id
    private static List<Direction> facesFromLinkerTargetId(@Nullable String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return List.of();
        }
        int facesIndex = targetId.indexOf("::faces:");
        if (facesIndex < 0) {
            return List.of();
        }
        int optionsIndex = targetId.indexOf("::opt:", facesIndex);
        String facesPart = optionsIndex >= 0
                ? targetId.substring(facesIndex + "::faces:".length(), optionsIndex)
                : targetId.substring(facesIndex + "::faces:".length());
        if (facesPart.isBlank()) {
            return List.of();
        }
        List<Direction> faces = new ArrayList<>();
        for (String token : facesPart.split(",")) {
            String directionName = token;
            int propertyIndex = directionName.indexOf('=');
            if (propertyIndex >= 0) {
                directionName = directionName.substring(0, propertyIndex);
            }
            Direction dir = Direction.byName(directionName.trim());
            if (dir != null && !faces.contains(dir)) {
                faces.add(dir);
            }
        }
        return faces;
    }

    // Check if this is a linker plane target
    private static boolean isLinkerPlaneTarget(@Nullable ContraptionNetworkLinkerData.LinkedTarget target) {
        return target != null
                && target.scope().usesFaces()
                && isLinkerPlaneBlockId(target.blockId());
    }

    // Check if this is a linker plane block ID
    private static boolean isLinkerPlaneBlockId(@Nullable String blockId) {
        return blockId != null
                && blockId.equalsIgnoreCase(CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.getId().toString());
    }

    // Check if the assembly destination is ready
    private boolean isAssemblyDestinationReady(@Nullable UUID destinationSubLevelId) {
        if (assemblyNewPos == null) {
            return false;
        }
        if (destinationSubLevelId == null && SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, assemblyNewPos)) {
            return false;
        }
        return SubLevelBlockEntityCollector.ensureTargetLoaded(level, destinationSubLevelId, assemblyNewPos);
    }

    // Check if this has target
    private boolean hasTargetAt(@Nullable UUID subLevelId, BlockPos pos) {
        return pos != null
                && (subLevelId != null || !SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, pos))
                && SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, pos)
                && !level.getBlockState(pos).isAir();
    }

    // Clear the assembly transfer
    private void clearAssemblyTransfer() {
        pendingAssemblyBindingRemap = false;
        assemblySourceSubLevelId = null;
        assemblyOldPos = null;
        assemblyNewPos = null;
        assemblySourceWasWorld = false;
    }

    // Clear the assembly route sources
    private void clearAssemblyRouteSources(BlockPos previousControllerPos) {
        if (level == null || previousControllerPos == null) {
            return;
        }
        for (String channelId : channels.keySet()) {
            ControllerRedstoneCompat.clearSource(level, previousControllerPos.asLong() + ":" + channelId);
        }
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry != null && entry.id() != null && !entry.id().isBlank()) {
                ControllerRedstoneCompat.clearSource(
                        level, previousControllerPos.asLong() + ":custom:" + entry.id());
            }
        }
    }

    // Sample the direct targets
    private boolean sampleDirectTargets(List<String> changedChannels) {
        boolean changed = false;
        long gameTime = level == null ? 0L : level.getGameTime();
        for (Map.Entry<String, ControllerDirectTargetReference> entry : inputTargets.entrySet()) {
            String channelId = entry.getKey();
            ControllerDirectTargetReference directTarget = entry.getValue();
            AnalogueChannel channel = channels.get(channelId);
            if (channel == null) {
                continue;
            }

            if (directTarget == null || !directTarget.isBound() || directTarget.blockPos() == null) {
                if (sampledInputActive.getOrDefault(channelId, false)) {
                    sampledInputActive.put(channelId, false);
                    if (channel.release(gameTime)) {
                        changed = true;
                        if (!changedChannels.contains(channelId)) {
                            changedChannels.add(channelId);
                        }
                    }
                }
                continue;
            }

            double next = sampleDirectTarget(channelId, directTarget);
            if (applySampledInput(channelId, channel, next, gameTime,
                    isProportionalThrottleTarget(directTarget))) {
                changed = true;
                if (!changedChannels.contains(channelId)) {
                    changedChannels.add(channelId);
                }
            }
        }
        return changed;
    }

    // Sample the direct target
    private double sampleDirectTarget(String channelId, ControllerDirectTargetReference directTarget) {
        return ControllerRedstoneCompat.sampleTarget(level, channelId, directTarget);
    }

    // Sample the custom input targets
    private boolean sampleCustomInputTargets() {
        boolean changed = false;
        long gameTime = level == null ? 0L : level.getGameTime();
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            AnalogueChannel channel = ensureCustomEntryChannel(entry);
            if (channel == null) {
                continue;
            }

            float sampledValue = 0.0f;
            boolean hasSampledSource = false;
            if (entry.inputTarget != null) {
                hasSampledSource = true;
                sampledValue = (float) net.minecraft.util.Mth.clamp(
                        sampleDirectTarget(entry.id(), entry.inputTarget), 0.0D, 1.0D);
            } else if (isFrequencyBound(entry.inputFirst, entry.inputSecond)) {
                hasSampledSource = true;
                ControllerLinkInputTarget inputTarget = customInputLinkTargets.get(entry.id());
                if (inputTarget != null) {
                    sampledValue = net.minecraft.util.Mth.clamp(inputTarget.getReceivedStrength() / 15.0f, 0.0f, 1.0f);
                }
            }

            boolean isActive = sampledValue > Math.max(1.0E-4D, entry.deadzone);
            boolean wasSampledActive = sampledCustomEntryIds.contains(entry.id());
            if (isActive) {
                sampledCustomEntryIds.add(entry.id());
            } else {
                sampledCustomEntryIds.remove(entry.id());
            }

            boolean entryChanged = false;
            boolean proportionalInput = isProportionalThrottleTarget(entry.inputTarget);
            if (hasSampledSource) {
                entryChanged |= applySampledInput(entry.id(), channel, sampledValue, gameTime,
                        proportionalInput);
            } else {
                sampledInputActive.remove(entry.id());
            }

            entryChanged |= tickSampledChannel(channel, gameTime, proportionalInput);

            float prev = sampledCustomEntryValues.getOrDefault(entry.id(), 0.0f);
            if (Math.abs(prev - sampledValue) > 1.0E-4f) {
                sampledCustomEntryValues.put(entry.id(), sampledValue);
            }
            if (entryChanged) {
                pushCustomEntrySignal(entry.id(), resolveCustomEntryOutputValue(entry.id()));
                changed = true;
            }
        }
        return changed;
    }

    // Check if the frequency is bound
    static boolean isFrequencyBound(ItemStack first, ItemStack second) {
        return isFrequencyBound(first != null && !first.isEmpty(), second != null && !second.isEmpty());
    }

    // Check if the frequency is bound
    static boolean isFrequencyBound(boolean firstPresent, boolean secondPresent) {
        return firstPresent || secondPresent;
    }

    // Sample the standard input targets
    private boolean sampleStandardInputTargets(List<String> changedChannels) {
        if (level == null || level.isClientSide) {
            return false;
        }
        boolean changed = false;
        long gameTime = level.getGameTime();

        for (Map.Entry<String, ControllerLinkInputTarget> entry : inputLinkTargets.entrySet()) {
            String channelId = entry.getKey();
            ControllerLinkInputTarget inputTarget = entry.getValue();
            AnalogueChannel channel = channels.get(channelId);
            if (channel == null || inputTarget == null) {
                continue;
            }

            if (!inputTarget.binding().isBound()) {
                continue;
            }

            float sampledValue = net.minecraft.util.Mth.clamp(
                    inputTarget.getReceivedStrength() / 15.0f, 0.0f, 1.0f);

            if (applySampledInput(channelId, channel, sampledValue, gameTime)) {
                changed = true;
                if (!changedChannels.contains(channelId)) {
                    changedChannels.add(channelId);
                }
            }
        }
        return changed;
    }

    // Resolve the custom entry output value
    private float resolveCustomEntryOutputValue(String id) {
        AnalogueChannel channel = customEntryChannels.get(id);
        if (channel != null) {
            return net.minecraft.util.Mth.clamp((float) channel.getUnsignedValue(), 0.0f, 1.0f);
        }
        return net.minecraft.util.Mth.clamp(sampledCustomEntryValues.getOrDefault(id, 0.0f), 0.0f, 1.0f);
    }

    // Ensure the custom entry channel
    private AnalogueChannel ensureCustomEntryChannel(CustomKeyEntry entry) {
        if (entry == null) {
            return null;
        }
        AnalogueChannel channel = customEntryChannels.computeIfAbsent(entry.id(), AnalogueChannel::unsigned);
        channel.setMode(entry.mode == null ? AnalogueChannelMode.RAMP : entry.mode);
        channel.setRiseRate(entry.riseRate);
        channel.setFallRate(entry.fallRate);
        channel.setStepAmount(entry.stepAmount);
        channel.setDeadzone(entry.deadzone);
        channel.setSmoothing(entry.smoothing);
        channel.setResetToZero(channel.getMode() != AnalogueChannelMode.STEP && channel.getMode() != AnalogueChannelMode.LATCH);
        return channel;
    }

    // Get the directional value for channel
    private static double directionalValueForChannel(String channelId, DirectionalAnalogSnapshot snapshot) {
        AnalogueControlChannel channel = AnalogueControlChannel.byId(channelId);
        if (channel == null) {
            return 0.0D;
        }
        return switch (channel) {
            case PITCH_UP, THROTTLE_UP, LIFT_UP -> snapshot.forward();
            case PITCH_DOWN, THROTTLE_DOWN, LIFT_DOWN -> snapshot.backward();
            case ROLL_LEFT, YAW_LEFT, STRAFE_LEFT -> snapshot.left();
            case ROLL_RIGHT, YAW_RIGHT, STRAFE_RIGHT -> snapshot.right();

            case STABILIZE -> net.minecraft.util.Mth.clamp(
                    Math.max(
                            Math.max(snapshot.forward(), snapshot.backward()),
                            Math.max(snapshot.left(), snapshot.right())),
                    0.0D,
                    1.0D);
        };
    }

    // Get the scalar target value
    private static double scalarTargetValue(BlockEntity target) {
        Object val = invokeNoArgNumber(target, "getState");
        if (!(val instanceof Number)) {
            val = invokeNoArgNumber(target, "getSignal");
        }
        if (!(val instanceof Number)) {
            val = readNumberField(target, "state");
        }
        return val instanceof Number num ? net.minecraft.util.Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D) : 0.0D;
    }

    // Get the face target value
    private static double faceTargetValue(@Nullable Level targetLevel,
                                          @Nullable BlockPos targetPos,
                                          Direction face,
                                          @Nullable String optionChannelId,
                                          @Nullable String signalPropertyKey) {
        if (targetLevel == null || targetPos == null || face == null) {
            return 0.0D;
        }

        Double directionalValue = readDirProperty(targetLevel.getBlockState(targetPos),
            optionChannelId,
            signalPropertyKey);
        if (directionalValue != null) {
            if (DEBUG_LINKER_FACE_IO) {
                Create.LOGGER.info(
                        "[CT-LinkerFaceIO] read-property pos={} face={} option={} prop={} value={}",
                        targetPos,
                        face,
                        optionChannelId,
                        signalPropertyKey,
                        directionalValue);
            }
            return directionalValue;
        }
        BlockState targetState = targetLevel.getBlockState(targetPos);

        int signal = Math.max(targetState.getSignal(targetLevel, targetPos, face),
                targetState.getDirectSignal(targetLevel, targetPos, face));
        signal = Math.max(signal, targetLevel.getSignal(targetPos.relative(face), face));
        if (DEBUG_LINKER_FACE_IO) {
            Create.LOGGER.info(
                    "[CT-LinkerFaceIO] read-fallback-neighbor pos={} face={} option={} prop={} signal={}",
                    targetPos,
                    face,
                    optionChannelId,
                    signalPropertyKey,
                    signal);
        }
        return net.minecraft.util.Mth.clamp(signal / 15.0D, 0.0D, 1.0D);
    }

    // Get the block target value
    private static double blockTargetValue(@Nullable Level targetLevel, @Nullable BlockPos targetPos) {
        if (targetLevel == null || targetPos == null) {
            return 0.0D;
        }
        BlockState state = targetLevel.getBlockState(targetPos);
        int maxSignal = 0;
        for (Direction dir : Direction.values()) {
            maxSignal = Math.max(maxSignal, state.getSignal(targetLevel, targetPos, dir));
            maxSignal = Math.max(maxSignal, state.getDirectSignal(targetLevel, targetPos, dir));
        }
        return net.minecraft.util.Mth.clamp(maxSignal / 15.0D, 0.0D, 1.0D);
    }

    // Read the create analogue lever value
    private static @Nullable Double readCreateAnalogueLeverValue(@Nullable Level targetLevel,
                                                                  @Nullable BlockPos targetPos,
                                                                  @Nullable BlockEntity target) {
        if (targetLevel == null || targetPos == null) {
            return null;
        }

        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(targetLevel.getBlockState(targetPos).getBlock()));
        if (!CREATE_ANALOG_LEVER_BLOCK_ID.equals(blockId)
                && !CREATE_ANALOGUE_LEVER_BLOCK_ID.equals(blockId)) {
            return null;
        }

        if (target != null) {
            Object stateValue = invokeNoArgNumber(target, "getState");
            if (stateValue instanceof Number num) {
                return net.minecraft.util.Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D);
            }
            stateValue = invokeNoArgNumber(target, "getSignal");
            if (stateValue instanceof Number num) {
                return net.minecraft.util.Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D);
            }
        }

        BlockState state = targetLevel.getBlockState(targetPos);
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return net.minecraft.util.Mth.clamp(state.getValue(BlockStateProperties.POWER) / 15.0D, 0.0D, 1.0D);
        }

        return null;
    }

    // Resolve the direct target level
    private @Nullable Level resolveDirectTargetLevel(@Nullable ControllerDirectTargetReference target,
                                                     @Nullable BlockEntity targetBlockEntity) {
        if (targetBlockEntity != null && targetBlockEntity.getLevel() != null) {
            return targetBlockEntity.getLevel();
        }

        return level;
    }

    // Resolve the direct target pos
    private @Nullable BlockPos resolveDirectTargetPos(@Nullable ControllerDirectTargetReference target,
                                                      @Nullable BlockEntity targetBlockEntity) {
        if (targetBlockEntity != null) {
            return targetBlockEntity.getBlockPos();
        }
        return target == null ? null : target.blockPos();
    }

    // Apply the sampled input
    private boolean applySampledInput(String channelId, AnalogueChannel channel, double sampledValue, long gameTime) {
        return applySampledInput(channelId, channel, sampledValue, gameTime, false);
    }

    // Apply the sampled input
    private boolean applySampledInput(String channelId, AnalogueChannel channel, double sampledValue, long gameTime,
                                      boolean proportionalInput) {
        return applySampledInputState(sampledInputActive, channelId, channel, sampledValue, gameTime,
                proportionalInput);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        OUTPUT DISPATCH
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the sampled input state
    static boolean applySampledInputState(Map<String, Boolean> sampledInputActive, String channelId,
                                          AnalogueChannel channel, double sampledValue, long gameTime,
                                          boolean proportionalInput) {
        double clampedValue = net.minecraft.util.Mth.clamp(sampledValue, 0.0D, 1.0D);
        if (channel.getMode() == AnalogueChannelMode.RAMP
                || channel.getMode() == AnalogueChannelMode.MOMENTARY) {

            if (proportionalInput) {
                double prev = channel.getUnsignedValue();
                channel.setValueInstant(clampedValue);
                sampledInputActive.put(channelId, clampedValue > 1.0E-4D);
                return Math.abs(prev - channel.getUnsignedValue()) > 1.0E-4D;
            }

            boolean fractionalSample = clampedValue > 1.0E-4D && clampedValue < 1.0D - 1.0E-4D;
            if (fractionalSample) {
                boolean changed = channel.isPressed() && channel.release(gameTime);
                double prev = channel.getUnsignedValue();
                channel.setValueInstant(clampedValue);
                sampledInputActive.put(channelId, true);
                return changed || Math.abs(prev - channel.getUnsignedValue()) > 1.0E-4D;
            }

            boolean isActive = clampedValue > 1.0E-4D;
            sampledInputActive.put(channelId, isActive);
            if (isActive && !channel.isPressed()) {
                return channel.press(gameTime);
            }
            if (!isActive && channel.isPressed()) {
                return channel.release(gameTime);
            }
            return false;
        }

        if (channel.getMode() == AnalogueChannelMode.DIRECT) {
            double prev = channel.getUnsignedValue();
            channel.setValueInstant(clampedValue);

            sampledInputActive.put(channelId, clampedValue > sampledInputThreshold(channel));
            return Math.abs(prev - channel.getUnsignedValue()) > 1.0E-4D;
        }

        boolean wasActive = sampledInputActive.getOrDefault(channelId, false);

        boolean isActive = clampedValue > sampledInputThreshold(channel);
        sampledInputActive.put(channelId, isActive);
        if (isActive && !wasActive) {
            return channel.press(gameTime);
        }
        if (!isActive && wasActive) {
            return channel.release(gameTime);
        }
        return false;
    }

    // Check if this is a proportional throttle target
    static boolean isProportionalThrottleTarget(@Nullable ControllerDirectTargetReference target) {
        return target != null && ControllerDiscoveryKind.THROTTLE.id().equals(target.targetTypeId());
    }

    // Update the sampled channel
    static boolean tickSampledChannel(AnalogueChannel channel, long gameTime, boolean proportionalInput) {
        if (channel == null) {
            return false;
        }
        boolean authoritativeSample = proportionalInput
                && (channel.getMode() == AnalogueChannelMode.RAMP
                || channel.getMode() == AnalogueChannelMode.MOMENTARY
                || channel.getMode() == AnalogueChannelMode.DIRECT);
        return !authoritativeSample && channel.tick(gameTime);
    }

    // Get the sampled input threshold
    private static double sampledInputThreshold(AnalogueChannel channel) {
        if (channel == null) {
            return 0.5D;
        }
        double channelDeadzone = net.minecraft.util.Mth.clamp(channel.getDeadzone(), 0.0D, 1.0D);
        return channelDeadzone > 0.0D ? channelDeadzone : 0.5D;
    }

    // Invoke a numeric method without arguments
    private static @Nullable Object invokeNoArgNumber(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object res = method.invoke(target);
            return res instanceof Number ? res : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Read the number field
    private static @Nullable Object readNumberField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object res = field.get(target);
            return res instanceof Number ? res : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Refresh the local side outputs
    private boolean refreshLocalSideOutputs(boolean notifyNeighbors) {
        EnumMap<Direction, Integer> nextOutputs = new EnumMap<>(Direction.class);
        for (Direction dir : OUTPUT_DIRECTIONS) {
            nextOutputs.put(dir, 0);
        }

        for (Map.Entry<String, Direction> entry : localOutputSides.entrySet()) {
            Direction dir = entry.getValue();
            if (dir == null) {
                continue;
            }
            AnalogueChannel channel = channels.get(entry.getKey());
            if (channel == null) {
                continue;
            }
            int current = nextOutputs.get(dir);
            nextOutputs.put(dir, Math.max(current, channel.getRedstoneStrength()));
        }

        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry.localOutputSide == null) continue;
            int strength = net.minecraft.util.Mth.clamp(
                    net.minecraft.util.Mth.ceil(getCustomEntryValue(entry.id()) * 15.0f),
                    0,
                    15);
            int current = nextOutputs.get(entry.localOutputSide);
            nextOutputs.put(entry.localOutputSide, Math.max(current, strength));
        }

        boolean changed = false;
        for (Direction dir : OUTPUT_DIRECTIONS) {
            int current = localOutputs.getOrDefault(dir, 0);
            int next = nextOutputs.get(dir);
            if (current != next) {
                changed = true;
            }
            localOutputs.put(dir, next);
        }

        if (changed && notifyNeighbors && shouldNotifyOutputNeighbors()) {
            notifyOutputNeighbors();
        }
        return changed;
    }

    // Check if this should notify output neighbors
    protected boolean shouldNotifyOutputNeighbors() {
        return level != null && !level.isClientSide;
    }

    // Get the controller runtime program
    private ControllerRuntimeProgram controllerRuntimeProgram() {
        if (controllerRuntimeProgramDirty || controllerRuntimeProgram == null) {
            controllerRuntimeProgram = new ControllerRuntimeProgram().compile();
            controllerRuntimeProgramDirty = false;
        }
        return controllerRuntimeProgram;
    }

    // Invalidate the controller runtime program
    protected void invalidateControllerRuntimeProgram() {
        controllerRuntimeProgramDirty = true;
        lastRuntimeWirelessRegistrationRefresh = Long.MIN_VALUE;
        lastRuntimeLinkerRegistrySync = Long.MIN_VALUE;
    }

    // Get the linker sibling output targets
    private ControllerDirectTargetReference[] linkerSiblingOutputTargets(@Nullable ControllerDirectTargetReference directTarget) {
        if (directTarget == null || !ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(directTarget)) {
            return new ControllerDirectTargetReference[0];
        }
        ItemStack insertedLinker = getActiveStoredLinker();
        if (insertedLinker.isEmpty()) {
            return new ControllerDirectTargetReference[0];
        }

        Direction selectedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(directTarget);
        String primaryBaseNodeId = ContraptionNetworkLinkerData.baseNodeIdFromTargetId(directTarget.targetId());
        List<ControllerDirectTargetReference> siblings = new ArrayList<>();
        for (ControllerDiscoveryNode linkerNode : ContraptionNetworkLinkerData.toDiscoveryNodes(insertedLinker)) {
            if (linkerNode == null
                    || linkerNode.kind() != com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_OUTPUT) {
                continue;
            }
            if (primaryBaseNodeId.equals(ContraptionNetworkLinkerData.baseNodeIdFromTargetId(linkerNode.nodeId()))) {
                continue;
            }

            ControllerDirectTargetReference siblingTarget =
                    ContraptionNetworkLinkerData.directTargetForFace(linkerNode, selectedFace);
            if (siblingTarget != null && siblingTarget.isBound()) {
                siblings.add(siblingTarget);
            }
        }
        return siblings.toArray(ControllerDirectTargetReference[]::new);
    }

    // Push the compiled channel signal
    private void pushCompiledChannelSignal(ChannelRuntimeRoute route) {
        if (level == null || level.isClientSide || route == null || route.channel() == null) {
            return;
        }

        float outputValue = (float) route.channel().getUnsignedValue();
        ControllerDirectTargetReference directTarget = route.directTarget();
        if (directTarget != null && directTarget.blockPos() != null) {
            BlockEntity targetBe =
                    SimulatedHelper.findLoadedBlockEntityExact(level, directTarget.subLevelId(), directTarget.blockPos());
            pushDirectTargetSignal(directTarget, targetBe, route.channelId(), outputValue, false);
        }

        for (ControllerDirectTargetReference siblingTarget : route.linkerSiblingTargets()) {
            if (siblingTarget == null || !siblingTarget.isBound()) {
                continue;
            }
            BlockEntity targetBe = SimulatedHelper.findLoadedBlockEntityExact(level,
                    siblingTarget.subLevelId(),
                    siblingTarget.blockPos());
            pushDirectTargetSignal(siblingTarget, targetBe, route.channelId(), outputValue, false);
        }

        ControllerLinkTarget target = route.wirelessTarget();
        if (target == null) {
            return;
        }
        target.accept(new AnalogueSignalPacket(route.channelId(), outputValue, level.getGameTime(),
                worldPosition.toShortString(), "analogue_contraption_controller"));
        if (target.isRegistered()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(resolveLinkLevel(level), target);
        }
    }

    // Push the compiled custom signal
    private void pushCompiledCustomSignal(CustomRuntimeRoute route) {
        if (level == null || level.isClientSide || route == null || route.entry() == null) {
            return;
        }
        pushCustomEntrySignal(route.entry().id(), resolveCustomEntryOutputValue(route.entry().id()));
    }

    // Define the controller maintenance values
    private enum ControllerMaintenance {
        WIRELESS_REGISTRATION(40),
        LINKER_REGISTRY(40);

        // Interval tick count
        private final int intervalTicks;

        // Initialize the controller maintenance
        ControllerMaintenance(int intervalTicks) {
            this.intervalTicks = intervalTicks;
        }
    }

    // Store the channel runtime route
    private record ChannelRuntimeRoute(String channelId,
                                       AnalogueChannel channel,
                                       ControllerDirectTargetReference directTarget,
                                       ControllerDirectTargetReference inputTarget,
                                       ControllerLinkTarget wirelessTarget,
                                       ControllerLinkInputTarget inputLinkTarget,
                                       Direction localOutputSide,
                                       ControllerDirectTargetReference[] linkerSiblingTargets) {
        // Check if this has wireless input
        private boolean hasWirelessInput() {
            return inputLinkTarget != null && inputLinkTarget.binding().isBound();
        }

        // Check if this has direct input
        private boolean hasDirectInput() {
            return inputTarget != null && inputTarget.isBound() && inputTarget.blockPos() != null;
        }

        // Check if this has output route
        private boolean hasOutputRoute() {
            return directTarget != null
                    || wirelessTarget != null
                    || localOutputSide != null
                    || linkerSiblingTargets.length > 0;
        }
    }

    // Store the custom runtime route
    private record CustomRuntimeRoute(CustomKeyEntry entry,
                                      AnalogueChannel channel,
                                      ControllerLinkTarget wirelessTarget,
                                      ControllerLinkInputTarget inputLinkTarget,
                                      boolean hasInputFrequency) {
        // Check if this has direct input
        private boolean hasDirectInput() {
            return entry != null && entry.inputTarget != null;
        }

        // Check if this has output route
        private boolean hasOutputRoute() {
            return entry != null && (entry.directTarget != null
                    || entry.localOutputSide != null
                    || isFrequencyBound(entry.first, entry.second));
        }
    }

    // Handle the controller runtime program
    private final class ControllerRuntimeProgram {
        // Channels
        private final ChannelRuntimeRoute[] channels;
        // Custom entries
        private final CustomRuntimeRoute[] customEntries;
        // Wireless targets
        private final ControllerLinkTarget[] wirelessTargets;
        // Wireless inputs
        private final ControllerLinkInputTarget[] wirelessInputs;
        // Custom wireless targets
        private final ControllerLinkTarget[] customWirelessTargets;
        // Custom wireless inputs
        private final ControllerLinkInputTarget[] customWirelessInputs;
        // Local channel outputs
        private final ChannelRuntimeRoute[] localChannelOutputs;
        // Local custom outputs
        private final CustomRuntimeRoute[] localCustomOutputs;
        // Tracks whether registered wireless routes are available
        private final boolean hasRegisteredWirelessRoutes;
        // Tracks whether inserted linker is available
        private final boolean hasInsertedLinker;

        // Initialize the controller runtime program
        private ControllerRuntimeProgram() {
            this(new ChannelRuntimeRoute[0],
                    new CustomRuntimeRoute[0],
                    new ControllerLinkTarget[0],
                    new ControllerLinkInputTarget[0],
                    new ControllerLinkTarget[0],
                    new ControllerLinkInputTarget[0],
                    new ChannelRuntimeRoute[0],
                    new CustomRuntimeRoute[0],
                    false,
                    false);
        }

        // Initialize the controller runtime program
        private ControllerRuntimeProgram(ChannelRuntimeRoute[] channels,
                                         CustomRuntimeRoute[] customEntries,
                                         ControllerLinkTarget[] wirelessTargets,
                                         ControllerLinkInputTarget[] wirelessInputs,
                                         ControllerLinkTarget[] customWirelessTargets,
                                         ControllerLinkInputTarget[] customWirelessInputs,
                                         ChannelRuntimeRoute[] localChannelOutputs,
                                         CustomRuntimeRoute[] localCustomOutputs,
                                         boolean hasRegisteredWirelessRoutes,
                                         boolean hasInsertedLinker) {
            this.channels = channels;
            this.customEntries = customEntries;
            this.wirelessTargets = wirelessTargets;
            this.wirelessInputs = wirelessInputs;
            this.customWirelessTargets = customWirelessTargets;
            this.customWirelessInputs = customWirelessInputs;
            this.localChannelOutputs = localChannelOutputs;
            this.localCustomOutputs = localCustomOutputs;
            this.hasRegisteredWirelessRoutes = hasRegisteredWirelessRoutes;
            this.hasInsertedLinker = hasInsertedLinker;
        }

        // Compile the controller runtime program
        private ControllerRuntimeProgram compile() {
            List<ChannelRuntimeRoute> channelRoutes = new ArrayList<>();
            List<ChannelRuntimeRoute> localChannelRoutes = new ArrayList<>();
            for (Map.Entry<String, AnalogueChannel> entry : AnalogueContraptionControllerBlockEntity.this.channels.entrySet()) {
                String channelId = entry.getKey();
                ControllerDirectTargetReference directTarget = AnalogueContraptionControllerBlockEntity.this.directTargets.get(channelId);
                ChannelRuntimeRoute route = new ChannelRuntimeRoute(
                        channelId,
                        entry.getValue(),
                        directTarget,
                        AnalogueContraptionControllerBlockEntity.this.inputTargets.get(channelId),
                        AnalogueContraptionControllerBlockEntity.this.wirelessTargets.get(channelId),
                        AnalogueContraptionControllerBlockEntity.this.inputLinkTargets.get(channelId),
                        AnalogueContraptionControllerBlockEntity.this.localOutputSides.get(channelId),
                        linkerSiblingOutputTargets(directTarget));
                channelRoutes.add(route);
                if (route.localOutputSide() != null) {
                    localChannelRoutes.add(route);
                }
            }

            List<CustomRuntimeRoute> customRoutes = new ArrayList<>();
            List<CustomRuntimeRoute> localCustomRoutes = new ArrayList<>();
            for (CustomKeyEntry entry : AnalogueContraptionControllerBlockEntity.this.customKeyEntries) {
                if (entry == null) {
                    continue;
                }
                CustomRuntimeRoute route = new CustomRuntimeRoute(
                        entry,
                        ensureCustomEntryChannel(entry),
                        AnalogueContraptionControllerBlockEntity.this.customWirelessTargets.get(entry.id()),
                        AnalogueContraptionControllerBlockEntity.this.customInputLinkTargets.get(entry.id()),
                        isFrequencyBound(entry.inputFirst, entry.inputSecond));
                customRoutes.add(route);
                if (entry.localOutputSide != null) {
                    localCustomRoutes.add(route);
                }
            }

            boolean hasWirelessRoutes = hasBoundRuntimeTargets(AnalogueContraptionControllerBlockEntity.this.wirelessTargets)
                    || hasBoundRuntimeTargets(AnalogueContraptionControllerBlockEntity.this.customWirelessTargets)
                    || hasBoundRuntimeTargets(AnalogueContraptionControllerBlockEntity.this.inputLinkTargets)
                    || hasBoundRuntimeTargets(AnalogueContraptionControllerBlockEntity.this.customInputLinkTargets);

            return new ControllerRuntimeProgram(
                    channelRoutes.toArray(ChannelRuntimeRoute[]::new),
                    customRoutes.toArray(CustomRuntimeRoute[]::new),
                    AnalogueContraptionControllerBlockEntity.this.wirelessTargets.values().toArray(ControllerLinkTarget[]::new),
                    AnalogueContraptionControllerBlockEntity.this.inputLinkTargets.values().toArray(ControllerLinkInputTarget[]::new),
                    AnalogueContraptionControllerBlockEntity.this.customWirelessTargets.values().toArray(ControllerLinkTarget[]::new),
                    AnalogueContraptionControllerBlockEntity.this.customInputLinkTargets.values().toArray(ControllerLinkInputTarget[]::new),
                    localChannelRoutes.toArray(ChannelRuntimeRoute[]::new),
                    localCustomRoutes.toArray(CustomRuntimeRoute[]::new),
                    hasWirelessRoutes,
                    !getActiveStoredLinker().isEmpty());
        }

        // Check if the runtime is idle
        private boolean isRuntimeIdle() {
            if (pendingAssemblyBindingRemap) {
                return false;
            }
            for (ChannelRuntimeRoute route : channels) {
                AnalogueChannel channel = route.channel();
                if (route.hasDirectInput() || route.hasWirelessInput()) {
                    return false;
                }
                if (channel != null && (channel.isPressed() || channel.getRedstoneStrength() > 0
                        || Math.abs(channel.getUnsignedValue()) > 1.0E-4D)) {
                    return false;
                }
            }
            for (CustomRuntimeRoute route : customEntries) {
                CustomKeyEntry entry = route.entry();
                if (entry == null) {
                    continue;
                }
                if (route.hasDirectInput() || route.hasInputFrequency()) {
                    return false;
                }
                AnalogueChannel channel = route.channel();
                if (channel != null && (channel.isPressed() || channel.getRedstoneStrength() > 0
                        || Math.abs(channel.getUnsignedValue()) > 1.0E-4D)) {
                    return false;
                }
            }
            for (AnalogueAxis axis : axes.values()) {
                if (axis != null && Math.abs(axis.getSignedValue()) > 1.0E-4D) {
                    return false;
                }
            }
            return !hasSampledInputState()
                    && !hasLocalOutputState();
        }

        // Check if this needs maintenance tick
        private boolean needsMaintenanceTick(long gameTime, long lastWirelessRefresh, long lastLinkerSync) {
            return needsWirelessRefresh(gameTime, lastWirelessRefresh)
                    || needsLinkerRegistrySync(gameTime, lastLinkerSync);
        }

        // Check if this needs wireless refresh
        private boolean needsWirelessRefresh(long gameTime, long lastWirelessRefresh) {
            return hasRegisteredWirelessRoutes
                    && (lastWirelessRefresh == Long.MIN_VALUE
                    || gameTime - lastWirelessRefresh >= ControllerMaintenance.WIRELESS_REGISTRATION.intervalTicks);
        }

        // Check if this needs linker registry sync
        private boolean needsLinkerRegistrySync(long gameTime, long lastLinkerSync) {
            return hasInsertedLinker
                    && (lastLinkerSync == Long.MIN_VALUE
                    || gameTime - lastLinkerSync >= ControllerMaintenance.LINKER_REGISTRY.intervalTicks);
        }

        // Refresh the wireless registrations
        private void refreshWirelessRegistrations() {
            if (level == null || level.isClientSide) {
                return;
            }
            for (ControllerLinkTarget target : wirelessTargets) {
                target.refreshRegistration(level);
            }
            for (ControllerLinkTarget target : customWirelessTargets) {
                target.refreshRegistration(level);
            }
            for (ControllerLinkInputTarget target : wirelessInputs) {
                target.refreshRegistration(level);
            }
            for (ControllerLinkInputTarget target : customWirelessInputs) {
                target.refreshRegistration(level);
            }
        }

        // Update the channels
        private boolean tickChannels(long gameTime, Set<String> changedChannels) {
            boolean changed = false;
            for (ChannelRuntimeRoute route : channels) {
                if (route.channel() != null && route.channel().tick(gameTime)) {
                    changed = true;
                    changedChannels.add(route.channelId());
                }
            }
            return changed;
        }

        // Sample the direct inputs
        private boolean sampleDirectInputs(long gameTime, Set<String> changedChannels) {
            boolean changed = false;
            for (ChannelRuntimeRoute route : channels) {
                ControllerDirectTargetReference directTarget = route.inputTarget();
                AnalogueChannel channel = route.channel();
                if (channel == null) {
                    continue;
                }

                if (directTarget == null || !directTarget.isBound() || directTarget.blockPos() == null) {
                    if (sampledInputActive.getOrDefault(route.channelId(), false)) {
                        sampledInputActive.put(route.channelId(), false);
                        if (channel.release(gameTime)) {
                            changed = true;
                            changedChannels.add(route.channelId());
                        }
                    }
                    continue;
                }

                double next = sampleDirectTarget(route.channelId(), directTarget);
                if (applySampledInput(route.channelId(), channel, next, gameTime,
                        isProportionalThrottleTarget(directTarget))) {
                    changed = true;
                    changedChannels.add(route.channelId());
                }
            }
            return changed;
        }

        // Sample the custom inputs
        private boolean sampleCustomInputs(long gameTime) {
            boolean changed = false;
            for (CustomRuntimeRoute route : customEntries) {
                CustomKeyEntry entry = route.entry();
                if (entry == null) {
                    continue;
                }
                AnalogueChannel channel = route.channel();
                if (channel == null) {
                    continue;
                }

                float sampledValue = 0.0f;
                boolean hasSampledSource = false;
                if (entry.inputTarget != null) {
                    hasSampledSource = true;
                    sampledValue = (float) net.minecraft.util.Mth.clamp(
                            sampleDirectTarget(entry.id(), entry.inputTarget), 0.0D, 1.0D);
                } else if (route.hasInputFrequency()) {
                    hasSampledSource = true;
                    ControllerLinkInputTarget inputTarget = route.inputLinkTarget();
                    if (inputTarget != null) {
                        sampledValue = net.minecraft.util.Mth.clamp(inputTarget.getReceivedStrength() / 15.0f, 0.0f, 1.0f);
                    }
                }

                boolean isActive = sampledValue > Math.max(1.0E-4D, entry.deadzone);
                if (isActive) {
                    sampledCustomEntryIds.add(entry.id());
                } else {
                    sampledCustomEntryIds.remove(entry.id());
                }

                boolean entryChanged = false;
                boolean proportionalInput = isProportionalThrottleTarget(entry.inputTarget);
                if (hasSampledSource) {
                    entryChanged |= applySampledInput(entry.id(), channel, sampledValue, gameTime,
                            proportionalInput);
                } else {
                    sampledInputActive.remove(entry.id());
                }

                entryChanged |= tickSampledChannel(channel, gameTime, proportionalInput);

                float prev = sampledCustomEntryValues.getOrDefault(entry.id(), 0.0f);
                if (Math.abs(prev - sampledValue) > 1.0E-4f) {
                    sampledCustomEntryValues.put(entry.id(), sampledValue);
                }
                if (entryChanged) {
                    pushCompiledCustomSignal(route);
                    changed = true;
                }
            }
            return changed;
        }

        // Sample the standard wireless inputs
        private boolean sampleStandardWirelessInputs(long gameTime, Set<String> changedChannels) {
            if (level == null || level.isClientSide) {
                return false;
            }
            boolean changed = false;
            for (ChannelRuntimeRoute route : channels) {
                ControllerLinkInputTarget inputTarget = route.inputLinkTarget();
                AnalogueChannel channel = route.channel();
                if (channel == null || inputTarget == null || !inputTarget.binding().isBound()) {
                    continue;
                }

                float sampledValue = net.minecraft.util.Mth.clamp(
                        inputTarget.getReceivedStrength() / 15.0f, 0.0f, 1.0f);

                if (applySampledInput(route.channelId(), channel, sampledValue, gameTime)) {
                    changed = true;
                    changedChannels.add(route.channelId());
                }
            }
            return changed;
        }

        // Reconcile the wireless output strengths
        private boolean reconcileWirelessOutputStrengths(Set<String> changedChannels) {
            boolean changed = false;
            for (ChannelRuntimeRoute route : channels) {
                ControllerLinkTarget target = route.wirelessTarget();
                AnalogueChannel channel = route.channel();
                if (target == null || channel == null) {
                    continue;
                }
                int expected = channel.getRedstoneStrength();
                if (target.getTransmittedStrength() != expected) {
                    changed = true;
                    changedChannels.add(route.channelId());
                }
            }
            return changed;
        }

        // Reconcile the channel output strengths
        private boolean reconcileChannelOutputStrengths(Set<String> changedChannels) {
            boolean changed = false;
            for (ChannelRuntimeRoute route : channels) {
                int currentStrength = directOutputStrength(route.channel());
                Integer previousStrength = lastChannelOutputStrengths.put(route.channelId(), currentStrength);
                if (previousStrength == null || previousStrength == currentStrength) {
                    continue;
                }
                changed = true;
                changedChannels.add(route.channelId());
            }
            return changed;
        }

        // Reconcile the custom entry output strengths
        private boolean reconcileCustomEntryOutputStrengths() {
            boolean changed = false;
            Set<String> activeEntryIds = new HashSet<>();
            for (CustomRuntimeRoute route : customEntries) {
                CustomKeyEntry entry = route.entry();
                if (entry == null || entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                String entryId = entry.id();
                activeEntryIds.add(entryId);
                int currentStrength = customOutput(entryId);
                Integer previousStrength = lastCustomEntryOutputStrengths.put(entryId, currentStrength);
                if (previousStrength == null || previousStrength == currentStrength) {
                    continue;
                }
                pushCompiledCustomSignal(route);
                changed = true;
            }
            lastCustomEntryOutputStrengths.keySet().removeIf(id -> !activeEntryIds.contains(id));
            return changed;
        }

        // Push the channel signals
        private void pushChannelSignals(Set<String> changedChannels) {
            if (changedChannels.isEmpty()) {
                return;
            }
            for (ChannelRuntimeRoute route : channels) {
                if (!changedChannels.contains(route.channelId())) {
                    continue;
                }
                pushCompiledChannelSignal(route);
                cacheChannelOutput(route.channelId());
            }
        }

        // Refresh the local side outputs
        private boolean refreshLocalSideOutputs(boolean notifyNeighbors) {
            EnumMap<Direction, Integer> nextOutputs = new EnumMap<>(Direction.class);
            for (Direction dir : OUTPUT_DIRECTIONS) {
                nextOutputs.put(dir, 0);
            }

            for (ChannelRuntimeRoute route : localChannelOutputs) {
                Direction dir = route.localOutputSide();
                AnalogueChannel channel = route.channel();
                if (dir == null || channel == null) {
                    continue;
                }
                int current = nextOutputs.get(dir);
                nextOutputs.put(dir, Math.max(current, channel.getRedstoneStrength()));
            }

            for (CustomRuntimeRoute route : localCustomOutputs) {
                CustomKeyEntry entry = route.entry();
                if (entry == null || entry.localOutputSide == null) {
                    continue;
                }
                int strength = net.minecraft.util.Mth.clamp(
                        net.minecraft.util.Mth.ceil(getCustomEntryValue(entry.id()) * 15.0f),
                        0,
                        15);
                int current = nextOutputs.get(entry.localOutputSide);
                nextOutputs.put(entry.localOutputSide, Math.max(current, strength));
            }

            boolean changed = false;
            for (Direction dir : OUTPUT_DIRECTIONS) {
                int current = localOutputs.getOrDefault(dir, 0);
                int next = nextOutputs.get(dir);
                if (current != next) {
                    changed = true;
                }
                localOutputs.put(dir, next);
            }

            if (changed && notifyNeighbors && shouldNotifyOutputNeighbors()) {
                notifyOutputNeighbors();
            }
            return changed;
        }

        // Check if this has bound runtime targets
        private boolean hasBoundRuntimeTargets(Map<String, ? extends Object> targets) {
            for (Object target : targets.values()) {
                if (target instanceof ControllerLinkTarget linkTarget && linkTarget.binding().isBound()) {
                    return true;
                }
                if (target instanceof ControllerLinkInputTarget inputTarget && inputTarget.binding().isBound()) {
                    return true;
                }
            }
            return false;
        }
    }

    // Set the local output side internal
    private boolean setLocalOutputSideInternal(String channelId, @Nullable Direction side) {
        String normalized = normalizeName(channelId);
        if (!channels.containsKey(normalized)) {
            return false;
        }
        Direction prev = localOutputSides.get(normalized);
        if (prev == side) {
            return false;
        }
        localOutputSides.put(normalized, side);
        return true;
    }

    // Notify the output neighbors
    private void notifyOutputNeighbors() {
        BlockState state = getBlockState();
        level.updateNeighborsAt(worldPosition, state.getBlock());
        for (Direction dir : OUTPUT_DIRECTIONS) {
            level.updateNeighborsAt(worldPosition.relative(dir), state.getBlock());
        }
    }

    // Broadcast all controller signals
    private void broadcastAllSignals(boolean force) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (String channelId : channels.keySet()) {
            if (force || channels.get(channelId).getRedstoneStrength() >= 0) {
                pushChannelSignal(channelId);
            }
        }
    }

    // Replay the current outputs
    private void replayCurrentOutputs() {
        if (level == null || level.isClientSide || pendingAssemblyBindingRemap) {
            return;
        }

        broadcastAllSignals(true);
        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null) {
                continue;
            }
            pushCustomEntrySignal(entry.id(), resolveCustomEntryOutputValue(entry.id()));
        }
    }

    // Push the channel signal
    private void pushChannelSignal(String channelId) {
        if (level == null || level.isClientSide) {
            return;
        }
        AnalogueChannel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }

        float outputValue = (float) channel.getUnsignedValue();

        ControllerDirectTargetReference directTarget = directTargets.get(channelId);
        if (directTarget != null && directTarget.blockPos() != null) {
            BlockEntity targetBe =
                    SimulatedHelper.findLoadedBlockEntityExact(level, directTarget.subLevelId(), directTarget.blockPos());
            pushDirectTargetSignal(directTarget, targetBe, channelId, outputValue, false);
        }

        pushLinkerSiblingOutputSignals(channelId, directTarget, outputValue);

        ControllerLinkTarget target = wirelessTargets.get(channelId);
        if (target == null) {
            return;
        }
        target.accept(new AnalogueSignalPacket(channelId, outputValue, level.getGameTime(), worldPosition.toShortString(), "analogue_contraption_controller"));
        if (target.isRegistered()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(resolveLinkLevel(level), target);
        }
    }

    // Resolve the link level
    private Level resolveLinkLevel(Level currentLevel) {
        if (currentLevel == null) {
            return level;
        }
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
                net.minecraft.server.level.ServerLevel overworld = currentLevel.getServer().overworld();
                if (overworld != null) {
                    return overworld;
                }
            }
        } catch (Exception ignored) {
        }
        return currentLevel;
    }

    // Check if this is in the wireless network
    private boolean isInWirelessNetwork(Level linkLevel, IRedstoneLinkable target) {
        if (linkLevel == null || target == null) {
            return false;
        }
        Set<IRedstoneLinkable> network =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(linkLevel).get(target.getNetworkKey());
        return network != null && network.contains(target);
    }

    // Get the channel signal source id
    private String channelSignalSourceId(String channelId) {
        return worldPosition.asLong() + ":" + channelId;
    }

    // Get the custom signal source id
    private String customSignalSourceId(String entryId) {
        return worldPosition.asLong() + ":custom:" + entryId;
    }

    // Push the linker sibling output signals
    private void pushLinkerSiblingOutputSignals(String channelId,
                                                @Nullable ControllerDirectTargetReference directTarget,
                                                float outputValue) {
        pushLinkerSiblingOutputSignals(channelId, directTarget, outputValue, false);
    }

    // Push the linker sibling output signals
    private void pushLinkerSiblingOutputSignals(String channelId,
                                                @Nullable ControllerDirectTargetReference directTarget,
                                                float outputValue,
                                                boolean customRoute) {
        if (directTarget == null || !ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(directTarget)) {
            return;
        }
        ItemStack insertedLinker = getActiveStoredLinker();
        if (insertedLinker.isEmpty()) {
            return;
        }

        Direction selectedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(directTarget);
        String primaryBaseNodeId = ContraptionNetworkLinkerData.baseNodeIdFromTargetId(directTarget.targetId());
        for (ControllerDiscoveryNode linkerNode : ContraptionNetworkLinkerData.toDiscoveryNodes(insertedLinker)) {
            if (linkerNode == null
                    || linkerNode.kind() != com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.LINKER_FACE_OUTPUT) {
                continue;
            }
            if (primaryBaseNodeId.equals(ContraptionNetworkLinkerData.baseNodeIdFromTargetId(linkerNode.nodeId()))) {
                continue;
            }

            ControllerDirectTargetReference siblingTarget =
                    ContraptionNetworkLinkerData.directTargetForFace(linkerNode, selectedFace);
            if (siblingTarget == null || !siblingTarget.isBound()) {
                continue;
            }

            BlockEntity targetBe = SimulatedHelper.findLoadedBlockEntityExact(level,
                    siblingTarget.subLevelId(),
                    siblingTarget.blockPos());
            pushDirectTargetSignal(siblingTarget, targetBe, channelId, outputValue, customRoute);
        }
    }

    // Clear the removed linker outputs
    private void clearRemovedLinkerOutputs(ItemStack linker) {
        if (level == null || level.isClientSide) {
            return;
        }

        Map<String, ControllerDirectTargetReference> nextStandardRoutes = new LinkedHashMap<>();
        if (!linker.isEmpty()) {
            Map<String, ContraptionNetworkLinkerData.ChannelBind> binds = ContraptionNetworkLinkerData.readChannelBindings(linker);
            for (String channelId : channels.keySet()) {
                ContraptionNetworkLinkerData.ChannelBind bind = binds.get(channelId);
                if (bind == null || !ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(bind.directTarget())) {
                    continue;
                }
                nextStandardRoutes.put(channelId, sanitizeDirectTarget(bind.directTarget()));
            }
        }

        for (String channelId : channels.keySet()) {
            ControllerDirectTargetReference directTarget = directTargets.get(channelId);
            if (!ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(directTarget)) {
                continue;
            }
            if (!Objects.equals(directTarget, nextStandardRoutes.get(channelId))) {
                clearDirectOutputSignalAndSiblings(directTarget, channelId, false);
            }
        }

        Map<String, ControllerDirectTargetReference> nextCustomRoutes = new LinkedHashMap<>();
        if (!linker.isEmpty() && level != null) {
            HolderLookup.Provider provider = level.registryAccess();
            for (CompoundTag tag : ContraptionNetworkLinkerData.readCustomEntryBindings(linker)) {
                CustomKeyEntry entry = CustomKeyEntry.fromTag(tag, provider);
                if (entry == null || entry.id() == null || entry.id().isBlank()) {
                    continue;
                }
                if (!ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(entry.directTarget)) {
                    continue;
                }
                nextCustomRoutes.put(entry.id(), entry.directTarget);
            }
        }

        for (CustomKeyEntry entry : customKeyEntries) {
            if (entry == null || !ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(entry.directTarget)) {
                continue;
            }
            if (!Objects.equals(entry.directTarget, nextCustomRoutes.get(entry.id()))) {
                clearDirectOutputSignalAndSiblings(entry.directTarget, entry.id(), true);
            }
        }
    }

    // Clear the direct output signal and siblings
    private void clearDirectOutputSignalAndSiblings(@Nullable ControllerDirectTargetReference directTarget,
                                                    String routeId,
                                                    boolean customRoute) {
        clearDirectOutputSignal(directTarget, routeId, customRoute);
        pushLinkerSiblingOutputSignals(routeId, directTarget, 0.0f, customRoute);
    }

    // Clear the direct output signal
    private void clearDirectOutputSignal(@Nullable ControllerDirectTargetReference directTarget,
                                         String routeId,
                                         boolean customRoute) {
        if (level == null || level.isClientSide || directTarget == null || !directTarget.isBound()
                || directTarget.blockPos() == null || routeId == null || routeId.isBlank()) {
            return;
        }

        BlockEntity targetBe =
                SimulatedHelper.findLoadedBlockEntityExact(level, directTarget.subLevelId(), directTarget.blockPos());
        pushDirectTargetSignal(directTarget, targetBe, routeId, 0.0f, customRoute);
    }

    // Push the direct target signal
    private void pushDirectTargetSignal(ControllerDirectTargetReference directTarget,
                                        @Nullable BlockEntity targetBe,
                                        String routeId,
                                        float val,
                                        boolean customRoute) {
        if (directTarget == null || directTarget.blockPos() == null || routeId == null || routeId.isBlank()) {
            return;
        }
        ControllerDirectTargetReference resolvedTarget = ControllerRedstoneCompat.resolveMovingTarget(level, directTarget);
        if (resolvedTarget == null || resolvedTarget.blockPos() == null) {
            return;
        }
        BlockEntity resolvedTargetBe = resolvedTarget.equals(directTarget) && targetBe != null
                ? targetBe
                : SimulatedHelper.findLoadedBlockEntityExact(
                        level, resolvedTarget.subLevelId(), resolvedTarget.blockPos());
        String resolvedChannelId = resolveDirectSignalChannelId(routeId, resolvedTarget);
        if (resolvedTargetBe instanceof VectorBearingBlockEntity vectorBearing) {
            DirectInputSourceContext sourceContext = directInputCtx(routeId, customRoute);
            if (sourceContext != null
                    && vectorBearing.applySourceDirectionalControllerSignal(
                    sourceContext.channelId(), val, sourceContext.blockEntity())) {
                return;
            }
        }
        if (resolvedTargetBe instanceof com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver receiver) {
            receiver.applyDirectControllerSignal(resolvedChannelId, val);
            return;
        }
        String baseSourceId = customRoute ? customSignalSourceId(routeId) : channelSignalSourceId(routeId);
        ControllerRedstoneCompat.writeTarget(level, resolvedTarget, resolvedTargetBe, baseSourceId, val);
    }

    // Get the direct input ctx
    private @Nullable DirectInputSourceContext directInputCtx(String routeId, boolean customRoute) {
        if (level == null || routeId == null || routeId.isBlank()) {
            return null;
        }
        ControllerDirectTargetReference sourceTarget = null;
        if (customRoute) {
            CustomKeyEntry entry = findCustomEntry(routeId);
            sourceTarget = entry == null ? null : entry.inputTarget;
        } else {
            sourceTarget = inputTargets.get(normalizeName(routeId));
        }
        if (sourceTarget == null || !sourceTarget.isBound() || sourceTarget.blockPos() == null) {
            return null;
        }
        ControllerDirectTargetReference resolvedSource = ControllerRedstoneCompat.resolveMovingTarget(level, sourceTarget);
        if (resolvedSource == null || resolvedSource.blockPos() == null) {
            return null;
        }
        BlockEntity blockEntity =
                SimulatedHelper.findLoadedBlockEntityExact(
                        level, resolvedSource.subLevelId(), resolvedSource.blockPos());
        if (blockEntity == null) {
            return null;
        }
        return new DirectInputSourceContext(blockEntity, resolveDirectSignalChannelId(routeId, resolvedSource));
    }

    // Store direct input source context
    private record DirectInputSourceContext(BlockEntity blockEntity, String channelId) {
    }

    // Describe the binding
    private Map<String, Object> describeBinding(@Nullable FrequencyBinding binding) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (binding == null) {
            map.put("bound", false);
            return map;
        }
        map.put("bound", binding.isBound());
        map.put("first", itemId(binding.first()));
        map.put("second", itemId(binding.second()));
        return map;
    }

    // Get the item id
    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }

    // Describe the direct target
    private static Map<String, Object> describeDirectTarget(ControllerDirectTargetReference directTarget) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("targetId", directTarget.targetId());
        description.put("targetTypeId", directTarget.targetTypeId());
        description.put("groupId", directTarget.groupId());
        description.put("label", directTarget.label());
        description.put("compatModeId", directTarget.compatModeId());
        description.put("subLevelId", directTarget.subLevelId() == null ? "" : directTarget.subLevelId().toString());
        description.put("blockPos", directTarget.blockPos() == null ? "" : directTarget.blockPos().toShortString());
        return description;
    }

    // Sanitize the direct target
    private static @Nullable ControllerDirectTargetReference sanitizeDirectTarget(@Nullable ControllerDirectTargetReference directTarget) {
        if (directTarget == null || !directTarget.isBound()) {
            return null;
        }
        return directTarget;
    }

    // Clamp the unit
    private static double clampUnit(double val) {
        return Math.max(0.0D, Math.min(1.0D, val));
    }

    // Get the ometer signal for strength
    static int ometerSignalForStrength(double strength) {
        return clampOmeterSignal((int) Math.round(clampUnit(strength) * 15.0D));
    }

    // Clamp the ometer signal
    private static int clampOmeterSignal(int val) {
        return Math.max(0, Math.min(15, val));
    }

    // Normalize the name
    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    // Handle the controller config screen behaviour
    private static class ControllerConfigScreenBehaviour extends ScrollOptionBehaviour<ControllerConfigControlOption> {
        // Initialize the controller config screen behaviour
        public ControllerConfigScreenBehaviour(AnalogueContraptionControllerBlockEntity be, ValueBoxTransform slot) {
            super(ControllerConfigControlOption.class,
                    Component.translatable("createthrusters.analogue_controller.config.title"), be, slot);
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
            AnalogueContraptionControllerBlockEntity controller = (AnalogueContraptionControllerBlockEntity) blockEntity;
            player.openMenu(controller, controller::sendToMenu);
        }
    }

    // Define the controller config control option values
    private enum ControllerConfigControlOption implements INamedIconOptions {
        CONFIGURE;

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return AllIcons.I_CONFIG_OPEN;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.analogue_controller.config.title";
        }
    }

    // Handle the controller config value box
    private static class ControllerConfigValueBox extends CenteredSideValueBoxTransform {
        // Initialize the controller config value box
        public ControllerConfigValueBox() {
            super((state, side) -> side.getAxis() != state.getValue(CTDirectionalBlock.FACING).getAxis());
        }

        // Get the south location
        @Override
        protected Vec3 getSouthLocation() {

            return VecHelper.voxelSpace(8.0D, 3.0D, 15.75D);
        }

        // Get the local offset
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {

            return super.getLocalOffset(level, pos, state);
        }

        // Get the scale
        @Override
        public float getScale() {
            return 0.32f;
        }
    }

    // Resolve the definition
    private static AnalogueControlChannel resolveDefinition(String channelId) {
        return AnalogueControlChannel.byId(channelId);
    }

    // Resolve the mechanic
    private static @Nullable ControllerMechanic resolveMechanic(@Nullable AnalogueControlChannel definition) {
        if (definition == null) {
            return null;
        }

        return ControllerMechanic.byId(definition.axisId());
    }

    // Get the redstone link key
    private static Couple<RedstoneLinkNetworkHandler.Frequency> redstoneLinkKey(FrequencyBinding binding) {
        return Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
    }

    // Handle the controller link target
    private class ControllerLinkTarget implements AnalogueTransmissionTarget, IRedstoneLinkable {
        // Channel id
        private final String channelId;
        // Binding
        private final FrequencyBinding binding;
        // Transmitted strength
        private int transmittedStrength;
        // Tracks whether controller link target is registered
        private boolean registered;
        // Registered level
        private @Nullable Level registeredLevel;
        // Current network key
        private Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;

        // Initialize the controller link target
        private ControllerLinkTarget(String channelId, FrequencyBinding binding) {
            this.channelId = channelId;
            this.binding = binding;
            this.networkKey = redstoneLinkKey(binding);
        }

        // Get the channel id
        @Override
        public String channelId() {
            return channelId;
        }

        // Accept the controller link target
        @Override
        public void accept(AnalogueSignalPacket packet) {
            transmittedStrength = packet.redstoneStrength();
        }

        // Check if this is registered
        public boolean isRegistered() {
            return registered;
        }

        // Get the binding
        public FrequencyBinding binding() {
            return binding;
        }

        // Refresh the registration
        public void refreshRegistration(Level level) {
            Level linkLevel = resolveLinkLevel(level);
            Couple<RedstoneLinkNetworkHandler.Frequency> desiredKey = redstoneLinkKey(binding);
            boolean staleRegistration = registered && (registeredLevel != linkLevel
                    || !Objects.equals(networkKey, desiredKey)
                    || !isInWirelessNetwork(registeredLevel, this));
            if (staleRegistration || !binding.isBound()) {
                unregisterRegistered();
            }
            networkKey = desiredKey;
            if (binding.isBound() && !registered && linkLevel != null) {
                register(linkLevel);
            }
        }

        // Update the binding
        public void updateBinding(Level level, FrequencyBinding binding) {
            Level linkLevel = resolveLinkLevel(level);
            unregisterRegistered();
            networkKey = redstoneLinkKey(binding);
            if (binding.isBound() && linkLevel != null) {
                register(linkLevel);
            }
        }

        // Register the controller link target
        private void register(Level linkLevel) {
            registeredLevel = linkLevel;
            registered = true;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(linkLevel, this);
        }

        // Remove the controller link target
        public void unregister(Level level) {
            unregisterRegistered();
        }

        // Remove the registered
        private void unregisterRegistered() {
            boolean wasRegistered = registered;
            Level oldLevel = registeredLevel;
            if (wasRegistered && oldLevel != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(oldLevel, this);
            }
            registered = false;
            registeredLevel = null;
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
            return level != null && !isRemoved()
                    && (wirelessTargets.get(channelId) == this || customWirelessTargets.get(channelId) == this);
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return networkKey;
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }

    // Handle the controller link input target
    private class ControllerLinkInputTarget implements IRedstoneLinkable {
        // Entry id
        private final String entryId;
        // Binding
        private final FrequencyBinding binding;
        // Received strength
        private int receivedStrength;
        // Tracks whether controller link input target is registered
        private boolean registered;
        // Registered level
        private @Nullable Level registeredLevel;
        // Current network key
        private Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;

        // Initialize the controller link input target
        private ControllerLinkInputTarget(String entryId, FrequencyBinding binding) {
            this.entryId = entryId;
            this.binding = binding;
            this.networkKey = redstoneLinkKey(binding);
        }

        // Refresh the registration
        public void refreshRegistration(Level level) {
            Level linkLevel = resolveLinkLevel(level);
            Couple<RedstoneLinkNetworkHandler.Frequency> desiredKey = redstoneLinkKey(binding);
            boolean staleRegistration = registered && (registeredLevel != linkLevel
                    || !Objects.equals(networkKey, desiredKey)
                    || !isInWirelessNetwork(registeredLevel, this));
            if (staleRegistration || !binding.isBound()) {
                unregisterRegistered();
            }
            networkKey = desiredKey;
            if (binding.isBound() && !registered && linkLevel != null) {
                register(linkLevel);
            }
        }

        // Update the binding
        public void updateBinding(Level level, FrequencyBinding binding) {
            Level linkLevel = resolveLinkLevel(level);
            unregisterRegistered();
            networkKey = redstoneLinkKey(binding);
            if (binding.isBound() && linkLevel != null) {
                register(linkLevel);
            }
        }

        // Register the controller link input target
        private void register(Level linkLevel) {
            registeredLevel = linkLevel;
            registered = true;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(linkLevel, this);
            setReceivedStrength(currentWirelessSignal(linkLevel, this));
        }

        // Get the received strength
        public int getReceivedStrength() {
            return receivedStrength;
        }

        // Get the binding
        public FrequencyBinding binding() {
            return binding;
        }

        // Set the signal strength
        private void setSignalStrength(int networkPower) {
            int clamped = net.minecraft.util.Mth.clamp(networkPower, 0, 15);
            if (receivedStrength == clamped) {
                return;
            }
            receivedStrength = clamped;
        }

        // Remove the controller link input target
        public void unregister(Level level) {
            unregisterRegistered();
        }

        // Remove the registered
        private void unregisterRegistered() {
            boolean wasRegistered = registered;
            Level oldLevel = registeredLevel;
            if (wasRegistered && oldLevel != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(oldLevel, this);
            }
            registered = false;
            registeredLevel = null;
            receivedStrength = 0;
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
            setSignalStrength(networkPower);
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return binding.isBound();
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {
            return level != null && !isRemoved()
                    && (inputLinkTargets.get(entryId) == this || customInputLinkTargets.get(entryId) == this);
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return networkKey;
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }
}
