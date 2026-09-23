package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.simibubi.create.Create;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

// Encode stable linker targets and keep old item formats readable while the network moves
public final class ContraptionNetworkLinkerData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final boolean DEBUG_LINKER_FACE_IO = Boolean.parseBoolean(
            System.getProperty("createthrusters.debug.linker_face_io", "true"));
    public static final String TAG_ROOT = "ContraptionNetworkLinker";
    private static final String TAG_LINKER_ID = "LinkerId";
    private static final String TAG_MODE = "EditMode";
    private static final String TAG_TARGET_MODE = "TargetMode";
    private static final String TAG_TARGETS = "Targets";

    private static final String TAG_BLOCK_POS = "BlockPos";
    private static final String TAG_SUBLEVEL_ID = "SubLevelId";
    private static final String TAG_BLOCK_ID = "BlockId";
    private static final String TAG_LABEL = "Label";
    private static final String TAG_ENTRY_MODE = "Mode";
    private static final String TAG_SCOPE = "Scope";
    private static final String TAG_FACES = "Faces";
    private static final String TAG_FACE = "Face";
    private static final String TAG_FACE_LABEL = "FaceLabel";
    private static final String TAG_FACE_SIGNAL_KEY = "FaceSignalKey";
    private static final String TAG_CHANNEL_BINDS = "ChannelBinds";
    private static final String TAG_CUSTOM_ENTRY_BINDS = "CustomEntryBinds";
    private static final String TAG_BIND_DIRECT = "DirectTarget";
    private static final String TAG_BIND_INPUT = "InputTarget";
    private static final String TAG_STORED_GRAPHS = "StoredGraphs";
    private static final String TAG_SELECTED_GRAPH_ID = "SelectedGraphId";
    private static final String TAG_GRAPH_ID = "GraphId";
    private static final String TAG_GRAPH_NAME = "Name";
    private static final String TAG_GRAPH = "Graph";

    private static final String TAG_CLIENT_SNAPSHOT = "ClientSnapshot";
    private static final String TAG_CLIENT_MANIFEST_ID = "ManifestId";
    private static final String TAG_CLIENT_MANIFEST_REVISION = "ManifestRevision";
    private static final String TAG_CLIENT_MANIFEST_HASH = "ManifestHash";
    private static final String TAG_CLIENT_HAS_BINDINGS = "HasBindings";
    private static final String TAG_CLIENT_EDIT_SOURCE_ID = "_ClientSourceManifestId";
    private static final String TAG_CLIENT_EDIT_SOURCE_REVISION = "_ClientSourceManifestRevision";
    private static final String TAG_CLIENT_EDIT_SOURCE_HASH = "_ClientSourceManifestHash";
    private static final int CLIENT_TARGET_CACHE_LIMIT = 32;
    private static final int CLIENT_SNAPSHOT_CACHE_LIMIT = 64;
    private static final Map<String, CompoundTag> CLIENT_SNAPSHOT_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75F, true) {
                // Remove the eldest entry
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompoundTag> eldest) {
                    return size() > CLIENT_SNAPSHOT_CACHE_LIMIT;
                }
            });
    private static final Map<ClientSnapshotKey, List<LinkedTarget>> CLIENT_TARGET_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
                // Remove the eldest entry
                @Override
                protected boolean removeEldestEntry(Map.Entry<ClientSnapshotKey, List<LinkedTarget>> eldest) {
                    return size() > CLIENT_TARGET_CACHE_LIMIT;
                }
            });

    private static final String NODE_PREFIX = "ct_linker";
    private static final String NODE_FACES_PREFIX = "::faces:";
    private static final String NODE_BLOCK_SUFFIX = "::block";
    private static final String DIRECT_TARGET_OPTION_PREFIX = "::opt:";
    private static final String DIRECT_TARGET_PROPERTY_PREFIX = "::prop:";
    private static final String FORCED_BLOCK_SCOPE_GEARSHIFT = "simulated:directional_gearshift";
    private static final String FORCED_BLOCK_SCOPE_ANALOGUE_LEVER = "create:analogue_lever";
    private static final String FORCED_BLOCK_SCOPE_THROTTLE_LEVER = "simulated:throttle_lever";
    private static final String FORCED_BLOCK_SCOPE_STEPPED_LEVER = "dndecor:stepped_lever";
    private static final String SIMULATED_GIMBAL_SENSOR = "simulated:gimbal_sensor";
    public static final String CONTRAPTION_DIAGRAM_TARGET_ID = "simulated:contraption_diagram";
    private static final String CREATE_THRUSTERS_ANALOGUE_JOYSTICK = "createthrusters:analogue_joystick";
    private static final String CREATE_THRUSTERS_THRUSTER_BEARING = "createthrusters:thruster_bearing";
    private static final String CREATE_THRUSTERS_AILERON_BEARING = "createthrusters:aileron_bearing";
    private static final String CREATE_THRUSTERS_VECTOR_BEARING = "createthrusters:vector_bearing";
    private static final String CREATE_THRUSTERS_RCS_THRUSTER = "createthrusters:rcs_thruster";
    private static final String CREATE_THRUSTERS_DOUBLE_BUTTON = "createthrusters:double_button";
    private static final String CREATE_THRUSTERS_COPYCAT_DOUBLE_BUTTON = "createthrusters:copycat_double_button";
    private static final String AEROWORKS_JOYSTICK = "aeroworks:joystick";
    private static final String AEROWORKS_CONTROL_DESK = "aeroworks:control_desk";
    private static final String AEROWORKS_STEPPER_SERVO = "aeroworks:stepper_servo";

    private static final EnumMap<Direction, AnalogueControlChannel> FACE_TO_OPTION = new EnumMap<>(Direction.class);
    private static final Map<String, Direction> OPTION_TO_FACE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        FACE_TO_OPTION.put(Direction.NORTH, AnalogueControlChannel.YAW_LEFT);
        FACE_TO_OPTION.put(Direction.SOUTH, AnalogueControlChannel.YAW_RIGHT);
        FACE_TO_OPTION.put(Direction.WEST, AnalogueControlChannel.ROLL_LEFT);
        FACE_TO_OPTION.put(Direction.EAST, AnalogueControlChannel.ROLL_RIGHT);
        FACE_TO_OPTION.put(Direction.UP, AnalogueControlChannel.PITCH_UP);
        FACE_TO_OPTION.put(Direction.DOWN, AnalogueControlChannel.PITCH_DOWN);

        Map<String, Direction> reverse = new LinkedHashMap<>();
        for (Map.Entry<Direction, AnalogueControlChannel> entry : FACE_TO_OPTION.entrySet()) {
            reverse.put(entry.getValue().id(), entry.getKey());
        }
        reverse.put("nozzle_north", Direction.NORTH);
        reverse.put("nozzle_east", Direction.EAST);
        reverse.put("nozzle_south", Direction.SOUTH);
        reverse.put("nozzle_west", Direction.WEST);
        OPTION_TO_FACE = Collections.unmodifiableMap(reverse);
    }

    // Initialize the contraption network linker data
    private ContraptionNetworkLinkerData() {
    }

    // Define the link mode values
    public enum LinkMode {
        INPUT("input"),
        OUTPUT("output"),
        SCM("scm");

        // Link mode id
        private final String id;

        // Initialize the link mode
        LinkMode(String id) {
            this.id = id;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Get the discovery kind
        public ControllerDiscoveryKind discoveryKind() {
            return switch (this) {
                case INPUT -> ControllerDiscoveryKind.LINKER_FACE_INPUT;
                case OUTPUT -> ControllerDiscoveryKind.LINKER_FACE_OUTPUT;
                case SCM -> throw new IllegalStateException(
                        "SCM targets are not controller discovery nodes");
            };
        }

        // Find the link mode by id
        public static LinkMode byId(String id) {
            if (id == null) {
                return OUTPUT;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "input" -> INPUT;
                case "scm" -> SCM;
                default -> OUTPUT;
            };
        }
    }

    // Store the linked face
    public record LinkedFace(Direction face, String label, String signalKey) {
        // Initialize the linked face
        public LinkedFace {
            face = face == null ? Direction.NORTH : face;
            label = normalize(label);
            signalKey = normalize(signalKey).toLowerCase(Locale.ROOT);
        }
    }

    // Store the linked target
    public record LinkedTarget(BlockPos blockPos, @Nullable UUID subLevelId, String blockId, String label,
                               LinkMode mode, TargetScope scope, List<LinkedFace> faces) {
        // Initialize the linked target
        public LinkedTarget {
            blockPos = blockPos == null ? BlockPos.ZERO : blockPos.immutable();
            blockId = normalize(blockId);
            label = normalize(label);
            mode = mode == null ? LinkMode.OUTPUT : mode;
            scope = scope == null ? TargetScope.FACE : scope;
            faces = faces == null ? List.of() : List.copyOf(faces);
            if (isContraptionDiagramTarget(blockId)) {
                mode = LinkMode.INPUT;
                scope = TargetScope.BLOCK;
                faces = List.of();
            }
        }
    }

    // Store target read results
    public record TargetReadResult(boolean authoritative, @Nullable UUID linkerId,
                                   LinkMode editMode, TargetMode targetMode,
                                   List<LinkedTarget> targets) {
        // Initialize the target read result
        public TargetReadResult {
            editMode = editMode == null ? LinkMode.OUTPUT : editMode;
            targetMode = targetMode == null ? TargetMode.AUTO : targetMode;
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    // Define the target scope values
    public enum TargetScope {
        FACE("face"),
        BLOCK("block");

        // Target scope id
        private final String id;

        // Initialize the target scope
        TargetScope(String id) {
            this.id = id;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Check if this uses faces
        public boolean usesFaces() {
            return this == FACE;
        }

        // Find the target scope by id
        public static TargetScope byId(String id) {
            if (id == null) {
                return FACE;
            }
            return "block".equals(id.trim().toLowerCase(Locale.ROOT)) ? BLOCK : FACE;
        }
    }

    // Define the target mode values
    public enum TargetMode {
        AUTO("auto"),
        BLOCK("block"),
        FACE("face");

        // Target mode id
        private final String id;

        // Initialize the target mode
        TargetMode(String id) {
            this.id = id;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Find the target mode by id
        public static TargetMode byId(String id) {
            if (id == null) {
                return AUTO;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "block" -> BLOCK;
                case "face" -> FACE;
                default -> AUTO;
            };
        }

        // Get the next
        public TargetMode next() {
            return switch (this) {
                case AUTO -> BLOCK;
                case BLOCK -> FACE;
                case FACE -> AUTO;
            };
        }
    }

    // Store the face option
    public record FaceOption(@Nullable Direction face, @Nullable AnalogueControlChannel optionChannel, String label,
                             @Nullable String signalPropertyKey, @Nullable String targetChannelId) {
        // Initialize the face option
        public FaceOption(@Nullable Direction face, @Nullable AnalogueControlChannel optionChannel, String label,
                          @Nullable String signalPropertyKey) {
            this(face, optionChannel, label, signalPropertyKey,
                    optionChannel == null ? null : optionChannel.id());
        }
    }

    // Store the channel bind
    public record ChannelBind(@Nullable ControllerDirectTargetReference directTarget,
                              @Nullable ControllerDirectTargetReference inputTarget) {
    }

    // Store the stored graph
    public record StoredGraph(String graphId, String name, CompoundTag graphTag) {
        // Initialize the stored graph
        public StoredGraph {
            graphId = normalize(graphId);
            name = normalize(name);
            graphTag = graphTag == null ? new CompoundTag() : graphTag.copy();
        }
    }

    // Store face cycle state
    public enum FaceCycleState {
        ADDED_OUTPUT,
        ADDED_INPUT,
        ADDED_SCM,
        MOVED_TO_INPUT,
        MOVED_TO_OUTPUT,
        REMOVED
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the target scope
    public static TargetScope resolveTargetScope(@Nullable Level level,
                                                 @Nullable BlockPos blockPos,
                                                 @Nullable BlockState blockState,
                                                 @Nullable Direction clickedFace,
                                                 @Nullable TargetMode targetMode) {
        TargetMode mode = targetMode == null ? TargetMode.AUTO : targetMode;
        if (mode == TargetMode.BLOCK) {
            return TargetScope.BLOCK;
        }
        if (mode == TargetMode.FACE) {
            return TargetScope.FACE;
        }
        if (blockState == null) {
            return TargetScope.FACE;
        }

        if (isForcedBlockScopeBlock(blockState)) {
            return TargetScope.BLOCK;
        }
        return hasDirectionalRedstoneCapability(level, blockPos, blockState, clickedFace)
                ? TargetScope.FACE
                : TargetScope.BLOCK;
    }

    // Resolve the target scope
    public static TargetScope resolveTargetScope(@Nullable Level level,
                                                 @Nullable BlockPos blockPos,
                                                 @Nullable BlockState blockState,
                                                 @Nullable Direction clickedFace) {
        return resolveTargetScope(level, blockPos, blockState, clickedFace, TargetMode.AUTO);
    }

    // Resolve the face signal key for binding
    public static @Nullable String resolveFaceSignalKeyForBinding(@Nullable BlockState blockState,
                                                                   @Nullable Direction clickedFace) {
        if (blockState == null || clickedFace == null) {
            return null;
        }
        String resolved = resolveDirectionalSignalProp(blockState, clickedFace);
        if (DEBUG_LINKER_FACE_IO) {
            Create.LOGGER.info("[CT-LinkerFaceIO] bind-face face={} resolvedProp={} props={}",
                    clickedFace,
                    resolved,
                    describeSignalProps(blockState));
        }
        return resolved;
    }

    // Get the edit mode
    public static LinkMode getEditMode(ItemStack stack) {
        ControllerSqliteStore.LinkerHeader header = linkerHeader(stack);
        if (header != null) {
            return header.editMode();
        }
        CompoundTag root = rootTag(stack, false);
        if (root == null) {
            return LinkMode.OUTPUT;
        }
        return LinkMode.byId(root.getString(TAG_MODE));
    }

    // Get the target mode
    public static TargetMode getTargetMode(ItemStack stack) {
        ControllerSqliteStore.LinkerHeader header = linkerHeader(stack);
        if (header != null) {
            return header.targetMode();
        }
        CompoundTag root = rootTag(stack, false);
        if (root == null) {
            return TargetMode.AUTO;
        }
        return TargetMode.byId(root.getString(TAG_TARGET_MODE));
    }

    // Get the client edit mode
    public static LinkMode getClientEditMode(ItemStack stack) {
        CompoundTag root = clientRootTag(stack);
        return root == null ? LinkMode.OUTPUT : LinkMode.byId(root.getString(TAG_MODE));
    }

    // Get the client target mode
    public static TargetMode getClientTargetMode(ItemStack stack) {
        CompoundTag root = clientRootTag(stack);
        return root == null ? TargetMode.AUTO : TargetMode.byId(root.getString(TAG_TARGET_MODE));
    }

    // Read the client targets
    public static List<LinkedTarget> readClientTargets(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        CompoundTag stackTag = stackCustomDataTagView(stack);
        CompoundTag snapshot = validClientSnapshot(stackTag);
        if (snapshot == null) {
            CompoundTag root = stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)
                    ? stackTag.getCompound(TAG_ROOT)
                    : null;
            return root == null ? List.of() : readTargets(root);
        }

        ClientSnapshotKey cacheKey = new ClientSnapshotKey(
                snapshot.getString(TAG_CLIENT_MANIFEST_ID),
                snapshot.getInt(TAG_CLIENT_MANIFEST_REVISION),
                snapshot.getString(TAG_CLIENT_MANIFEST_HASH));
        synchronized (CLIENT_TARGET_CACHE) {
            List<LinkedTarget> cached = CLIENT_TARGET_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        List<LinkedTarget> parsed = List.copyOf(readTargets(snapshot));
        synchronized (CLIENT_TARGET_CACHE) {
            CLIENT_TARGET_CACHE.keySet().removeIf(key -> key.manifestId().equals(cacheKey.manifestId())
                    && !key.equals(cacheKey));
            CLIENT_TARGET_CACHE.put(cacheKey, parsed);
        }
        return parsed;
    }

    // Read the client channel bindings
    public static Map<String, ChannelBind> readClientChannelBindings(ItemStack stack) {
        CompoundTag root = clientRootTag(stack);
        return root == null ? Map.of() : readChannelBindings(root);
    }

    // Read the client custom entry bindings
    public static List<CompoundTag> readClientCustomEntryBindings(ItemStack stack) {
        CompoundTag root = clientRootTag(stack);
        return root == null ? List.of() : readCustomEntryBindings(root);
    }

    // Check if the client has bindings
    public static boolean clientHasBindings(ItemStack stack) {
        CompoundTag root = clientRootTag(stack);
        if (root == null) {
            return false;
        }
        if (root.contains(TAG_CLIENT_HAS_BINDINGS, Tag.TAG_BYTE)) {
            return root.getBoolean(TAG_CLIENT_HAS_BINDINGS);
        }
        return hasBindings(root);
    }

    // Check if this has client data
    public static boolean hasClientData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (!ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            return true;
        }
        return validClientSnapshot(stackTag) != null || stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND);
    }

    // Write the client edit root
    public static CompoundTag writeClientEditRoot(ItemStack stack, List<LinkedTarget> targets,
                                                  LinkMode editMode, TargetMode targetMode) {
        CompoundTag root = writeRoot(targets, editMode, targetMode);
        if (stack == null || stack.isEmpty()) {
            return root;
        }
        CompoundTag stackTag = stackCustomDataTagView(stack);
        if (!ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            return root;
        }
        CompoundTag snapshot = validClientSnapshot(stackTag);
        root.putString(TAG_CLIENT_EDIT_SOURCE_ID, snapshot == null
                ? ControllerManifestStore.linkerManifestId(stackTag)
                : snapshot.getString(TAG_CLIENT_MANIFEST_ID));
        root.putInt(TAG_CLIENT_EDIT_SOURCE_REVISION, snapshot == null
                ? ControllerManifestStore.linkerManifestRevision(stackTag)
                : snapshot.getInt(TAG_CLIENT_MANIFEST_REVISION));
        root.putString(TAG_CLIENT_EDIT_SOURCE_HASH, snapshot == null
                ? ControllerManifestStore.linkerManifestHash(stackTag)
                : snapshot.getString(TAG_CLIENT_MANIFEST_HASH));
        return root;
    }

    // Merge the client edit if current
    public static @Nullable CompoundTag mergeClientEditIfCurrent(ItemStack stack, CompoundTag incomingRoot) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag incoming = incomingRoot == null ? new CompoundTag() : incomingRoot.copy();
        boolean hasSource = incoming.contains(TAG_CLIENT_EDIT_SOURCE_ID, Tag.TAG_STRING)
                && incoming.contains(TAG_CLIENT_EDIT_SOURCE_REVISION, Tag.TAG_INT)
                && incoming.contains(TAG_CLIENT_EDIT_SOURCE_HASH, Tag.TAG_STRING);
        String sourceId = incoming.getString(TAG_CLIENT_EDIT_SOURCE_ID);
        int sourceRevision = incoming.getInt(TAG_CLIENT_EDIT_SOURCE_REVISION);
        String sourceHash = incoming.getString(TAG_CLIENT_EDIT_SOURCE_HASH);
        removeClientEditSource(incoming);

        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return sanitizeAndMergeClientEdit(incoming, stackTag.getCompound(TAG_ROOT));
        }
        if (!ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            return sanitizeAndMergeClientEdit(incoming, clientRootTag(stack));
        }
        if (!hasSource) {
            return null;
        }

        ControllerManifestStore.ManifestSnapshot latest =
                ControllerManifestStore.loadLinkerFromMetadata(stackTag);
        if (latest == null) {
            return null;
        }
        if (!Objects.equals(sourceId, latest.id())
                || sourceRevision != latest.revision()
                || !Objects.equals(sourceHash, latest.hash())) {
            installClientSnapshot(stack, stackTag, latest);
            return null;
        }

        installClientSnapshot(stack, stackTag, latest);
        return sanitizeAndMergeClientEdit(incoming, latest.linkerData());
    }

    // Ensure the client snapshot
    public static boolean ensureClientSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (!requiresClientSnapshotLoad(stackTag)) {
            return false;
        }

        ControllerManifestStore.ManifestSnapshot snapshot =
                ControllerManifestStore.loadLinkerFromMetadata(stackTag);
        if (snapshot == null) {
            return false;
        }

        boolean changed = hasPersistedLinkerPayload(stackTag);
        cleanLinkerStackTag(stackTag);
        ControllerManifestStore.writeLinkerMetadata(stackTag, snapshot);
        writeClientSnapshot(stackTag, snapshot.linkerData(), snapshot);
        if (changed) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
        }
        return changed;
    }

    // Check if this requires client snapshot load
    static boolean requiresClientSnapshotLoad(@Nullable CompoundTag stackTag) {
        return stackTag != null
                && !stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)
                && ControllerManifestStore.hasLinkerMetadata(stackTag)
                && currentClientSnapshot(stackTag) == null;
    }

    // Migrate the legacy storage
    public static boolean migrateLegacyStorage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (!stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag root = stackTag.getCompound(TAG_ROOT).copy();
        String manifestId = ControllerManifestStore.linkerManifestId(stackTag);
        int currentRevision = ControllerManifestStore.linkerManifestRevision(stackTag);
        if (currentRevision <= 0 && !manifestId.isBlank()) {
            currentRevision = ControllerSqliteStore.loadLinkerRevision(manifestId);
        }
        ControllerManifestStore.ManifestSnapshot snapshot =
                ControllerManifestStore.saveLinker(manifestId, root, currentRevision);
        if (snapshot == null) {
            return false;
        }

        cleanLinkerStackTag(stackTag);
        ControllerManifestStore.writeLinkerMetadata(stackTag, snapshot);
        writeClientSnapshot(stackTag, root, snapshot);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
        return true;
    }

    // Ensure the stored identity
    public static boolean ensureStoredIdentity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag stackTag = stackCustomDataTagView(stack);
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            String manifestId = ControllerManifestStore.linkerManifestId(stackTag);
            if (ControllerSqliteStore.loadLinkerHeader(manifestId) != null) {
                return true;
            }
        }
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return migrateLegacyStorage(stack);
        }
        getOrCreateLinkerId(stack);
        return ControllerManifestStore.hasLinkerMetadata(stackCustomDataTagView(stack));
    }

    // Copy the authoritative root for schematic
    static CompoundTag copyAuthoritativeRootForSchematic(ItemStack stack) {
        CompoundTag root = rootTag(stack, false);
        CompoundTag copy = root == null ? new CompoundTag() : root.copy();
        CompoundTag stackTag = stackCustomDataTagView(stack);
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            ListTag graphs = ControllerSqliteStore.loadLinkerStoredGraphs(
                    ControllerManifestStore.linkerManifestId(stackTag));
            if (!graphs.isEmpty()) {
                copy.put(TAG_STORED_GRAPHS, graphs);
            }
        }
        return copy;
    }

    // Install the schematic copy
    static boolean installSchematicCopy(ItemStack stack, CompoundTag linkerData) {
        if (stack == null || stack.isEmpty() || linkerData == null || linkerData.isEmpty()) {
            return false;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        installSchematicCopyData(stackTag, linkerData);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
        return true;
    }

    // Create the schematic copy root
    static CompoundTag createSchematicCopyRoot(CompoundTag linkerData) {
        CompoundTag root = linkerData == null ? new CompoundTag() : linkerData.copy();
        root.putUUID(TAG_LINKER_ID, UUID.randomUUID());
        removeClientEditSource(root);
        return root;
    }

    // Install the schematic copy data
    static void installSchematicCopyData(CompoundTag stackTag, CompoundTag linkerData) {
        if (stackTag == null) {
            return;
        }
        cleanLinkerStackTag(stackTag);
        stackTag.remove(ControllerManifestStore.TAG_LINKER_MANIFEST_ID);
        stackTag.put(TAG_ROOT, createSchematicCopyRoot(linkerData));
    }

    // Check if this has embedded schematic root
    static boolean hasEmbeddedSchematicRoot(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stackCustomDataTagView(stack).contains(TAG_ROOT, Tag.TAG_COMPOUND);
    }

    // Migrate the legacy NBT container
    static boolean migrateLegacyNbtContainer(CompoundTag container) {
        if (container == null) {
            return false;
        }
        if (!container.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            if (!ControllerManifestStore.hasLinkerMetadata(container)
                    || !hasPersistedLinkerPayload(container)) {
                return false;
            }
            cleanLinkerStackTag(container);
            return true;
        }

        CompoundTag root = container.getCompound(TAG_ROOT).copy();
        String manifestId = ControllerManifestStore.linkerManifestId(container);
        int currentRevision = ControllerManifestStore.linkerManifestRevision(container);
        if (currentRevision <= 0 && !manifestId.isBlank()) {
            currentRevision = ControllerSqliteStore.loadLinkerRevision(manifestId);
        }
        ControllerManifestStore.ManifestSnapshot snapshot =
                ControllerManifestStore.saveLinker(manifestId, root, currentRevision);
        if (snapshot == null) {
            return false;
        }
        cleanLinkerStackTag(container);
        ControllerManifestStore.writeLinkerMetadata(container, snapshot);
        writeClientSnapshot(container, root, snapshot);
        return true;
    }

    // Get the client snapshot for sync
    public static @Nullable ControllerManifestStore.ManifestSnapshot clientSnapshotForSync(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            if (!migrateLegacyStorage(stack)) {
                return null;
            }
            stackTag = stackCustomDataTag(stack);
        }
        return ControllerManifestStore.loadLinkerFromMetadata(stackTag);
    }

    // Install the client snapshot
    public static void installClientSnapshot(String manifestId, int revision, String hash,
                                             @Nullable CompoundTag linkerRoot) {
        if (manifestId == null || manifestId.isBlank()) {
            return;
        }
        ControllerManifestStore.ManifestSnapshot snapshot = new ControllerManifestStore.ManifestSnapshot(
                manifestId,
                Math.max(0, revision),
                hash == null ? "" : hash,
                ControllerManifestStore.STORAGE_VERSION,
                "contraption_network_linker",
                new CompoundTag(),
                new CompoundTag(),
                new CompoundTag(),
                linkerRoot == null ? new CompoundTag() : linkerRoot);
        CompoundTag metadata = new CompoundTag();
        ControllerManifestStore.writeLinkerMetadata(metadata, snapshot);
        writeClientSnapshot(metadata, snapshot.linkerData(), snapshot);
    }

    // Get the client snapshot data
    public static CompoundTag clientSnapshotData(ControllerManifestStore.ManifestSnapshot snapshot) {
        if (snapshot == null) {
            return new CompoundTag();
        }
        CompoundTag metadata = new CompoundTag();
        ControllerManifestStore.writeLinkerMetadata(metadata, snapshot);
        writeClientSnapshot(metadata, snapshot.linkerData(), snapshot);
        CompoundTag cached = validClientSnapshot(metadata);
        return cached == null ? new CompoundTag() : cached;
    }

    // Set the edit mode
    public static void setEditMode(ItemStack stack, LinkMode mode) {
        CompoundTag root = rootTag(stack, true);
        root.putString(TAG_MODE, mode == null ? LinkMode.OUTPUT.id() : mode.id());
        writeRootToStack(stack, root);
    }

    // Set the target mode
    public static void setTargetMode(ItemStack stack, TargetMode mode) {
        CompoundTag root = rootTag(stack, true);
        root.putString(TAG_TARGET_MODE, mode == null ? TargetMode.AUTO.id() : mode.id());
        writeRootToStack(stack, root);
    }

    // Cycle the target mode
    public static TargetMode cycleTargetMode(ItemStack stack) {
        TargetMode next = getTargetMode(stack).next();
        setTargetMode(stack, next);
        return next;
    }

    // Get the linker id
    public static @Nullable UUID getLinkerId(ItemStack stack) {
        ControllerSqliteStore.LinkerHeader header = linkerHeader(stack);
        if (header != null) {
            return header.linkerId();
        }
        CompoundTag root = rootTag(stack, false);
        if (root == null || !root.hasUUID(TAG_LINKER_ID)) {
            return null;
        }
        return root.getUUID(TAG_LINKER_ID);
    }

    // Get or create the linker id
    public static UUID getOrCreateLinkerId(ItemStack stack) {
        UUID existing = getLinkerId(stack);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        CompoundTag root = rootTag(stack, true);
        root.putUUID(TAG_LINKER_ID, created);
        writeRootToStack(stack, root);
        return created;
    }

    // Read the targets
    public static List<LinkedTarget> readTargets(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return readTargets(stackTag.getCompound(TAG_ROOT));
        }
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            ControllerSqliteStore.LinkerTargets targets =
                    ControllerSqliteStore.loadLinkerTargets(ControllerManifestStore.linkerManifestId(stackTag));
            if (targets.found()) {
                return targets.targets();
            }
        }
        CompoundTag root = rootTag(stack, false);
        if (root == null) {
            return List.of();
        }
        return readTargets(root);
    }

    // Read the authoritative targets
    public static TargetReadResult readAuthoritativeTargets(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new TargetReadResult(true, null, LinkMode.OUTPUT, TargetMode.AUTO, List.of());
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return targetReadResult(stackTag.getCompound(TAG_ROOT));
        }
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            ControllerSqliteStore.LinkerTargets targets =
                    ControllerSqliteStore.loadLinkerTargets(ControllerManifestStore.linkerManifestId(stackTag));
            if (!targets.found()) {
                ControllerManifestStore.ManifestSnapshot migrated =
                        ControllerManifestStore.loadLinkerFromMetadata(stackTag);
                if (migrated == null) {
                    return new TargetReadResult(false, null, null, null, List.of());
                }
                installClientSnapshot(stack, stackTag, migrated);
                return targetReadResult(migrated.linkerData());
            }
            return new TargetReadResult(true, targets.linkerId(), targets.editMode(), targets.targetMode(),
                    targets.targets());
        }
        CompoundTag root = rootTag(stack, false);
        return targetReadResult(root);
    }

    // Get the target read result
    private static TargetReadResult targetReadResult(@Nullable CompoundTag root) {
        if (root == null) {
            return new TargetReadResult(true, null, LinkMode.OUTPUT, TargetMode.AUTO, List.of());
        }
        UUID linkerId = root.hasUUID(TAG_LINKER_ID) ? root.getUUID(TAG_LINKER_ID) : null;
        return new TargetReadResult(true, linkerId,
                LinkMode.byId(root.getString(TAG_MODE)),
                TargetMode.byId(root.getString(TAG_TARGET_MODE)),
                readTargets(root));
    }

    // Read the targets
    public static List<LinkedTarget> readTargets(CompoundTag root) {
        if (root == null) {
            return List.of();
        }
        ListTag targetsTag = root.getList(TAG_TARGETS, Tag.TAG_COMPOUND);
        List<LinkedTarget> targets = new ArrayList<>();
        for (int i = 0; i < targetsTag.size(); i++) {
            CompoundTag entryTag = targetsTag.getCompound(i);
            if (!entryTag.contains(TAG_BLOCK_POS)) {
                continue;
            }

            BlockPos pos = BlockPos.of(entryTag.getLong(TAG_BLOCK_POS));
            UUID subLevelId = entryTag.hasUUID(TAG_SUBLEVEL_ID) ? entryTag.getUUID(TAG_SUBLEVEL_ID) : null;
            LinkMode mode = LinkMode.byId(entryTag.getString(TAG_ENTRY_MODE));
            TargetScope scope = TargetScope.byId(entryTag.getString(TAG_SCOPE));

            if (isDefaultBlockScopeBlockId(entryTag.getString(TAG_BLOCK_ID))) {
                scope = TargetScope.BLOCK;
            }

            ListTag facesTag = entryTag.getList(TAG_FACES, Tag.TAG_COMPOUND);
            List<LinkedFace> faces = new ArrayList<>();
            for (int faceIndex = 0; faceIndex < facesTag.size(); faceIndex++) {
                CompoundTag faceTag = facesTag.getCompound(faceIndex);
                Direction dir = Direction.byName(faceTag.getString(TAG_FACE));
                if (dir == null) {
                    continue;
                }
                faces.add(new LinkedFace(dir, faceTag.getString(TAG_FACE_LABEL),
                        faceTag.getString(TAG_FACE_SIGNAL_KEY)));
            }
                if (scope.usesFaces() && faces.isEmpty()) {
                continue;
            }

            targets.add(new LinkedTarget(
                    pos,
                    subLevelId,
                    entryTag.getString(TAG_BLOCK_ID),
                    entryTag.getString(TAG_LABEL),
                    mode,
                    scope,
                    sortFaces(faces)));
        }
        return sortTargets(targets);
    }

    // Write the root
    public static CompoundTag writeRoot(List<LinkedTarget> targets, LinkMode editMode, TargetMode targetMode) {
        CompoundTag root = new CompoundTag();
        root.putString(TAG_MODE, (editMode == null ? LinkMode.OUTPUT : editMode).id());
        root.putString(TAG_TARGET_MODE, (targetMode == null ? TargetMode.AUTO : targetMode).id());
        ListTag listTag = new ListTag();
        for (LinkedTarget target : sortTargets(targets)) {
            CompoundTag targetTag = new CompoundTag();
            targetTag.putLong(TAG_BLOCK_POS, target.blockPos().asLong());
            if (target.subLevelId() != null) {
                targetTag.putUUID(TAG_SUBLEVEL_ID, target.subLevelId());
            }
            if (!target.blockId().isBlank()) {
                targetTag.putString(TAG_BLOCK_ID, target.blockId());
            }
            if (!target.label().isBlank()) {
                targetTag.putString(TAG_LABEL, target.label());
            }
            targetTag.putString(TAG_ENTRY_MODE, target.mode().id());
            targetTag.putString(TAG_SCOPE, target.scope().id());

            ListTag facesTag = new ListTag();
            if (target.scope().usesFaces()) {
                for (LinkedFace face : sortFaces(target.faces())) {
                    CompoundTag faceTag = new CompoundTag();
                    faceTag.putString(TAG_FACE, face.face().getSerializedName());
                    if (!face.label().isBlank()) {
                        faceTag.putString(TAG_FACE_LABEL, face.label());
                    }
                    if (!face.signalKey().isBlank()) {
                        faceTag.putString(TAG_FACE_SIGNAL_KEY, face.signalKey());
                    }
                    facesTag.add(faceTag);
                }
            }
            targetTag.put(TAG_FACES, facesTag);
            listTag.add(targetTag);
        }
        root.put(TAG_TARGETS, listTag);
        return root;
    }

    // Write the root
    public static CompoundTag writeRoot(List<LinkedTarget> targets, LinkMode editMode) {
        return writeRoot(targets, editMode, TargetMode.AUTO);
    }

    // Write the root
    public static void writeRoot(ItemStack stack, CompoundTag root) {
        writeRootToStack(stack, root == null ? new CompoundTag() : root);
    }

    // Write the targets
    public static void writeTargets(ItemStack stack, List<LinkedTarget> targets, LinkMode editMode) {
        writeTargets(stack, targets, editMode, getTargetMode(stack));
    }

    // Write the targets
    public static void writeTargets(ItemStack stack, List<LinkedTarget> targets, LinkMode editMode,
                                    TargetMode targetMode) {
        CompoundTag nextRoot = writeRoot(targets, editMode, targetMode);
        CompoundTag existingRoot = rootTag(stack, false);
        nextRoot = mergeRootWithExistingBindings(nextRoot, existingRoot);
        writeRootToStack(stack, nextRoot);
    }

    // Rewrite the tracked targets
    static boolean rewriteTrackedTargets(ItemStack stack,
                                         List<LinkedTarget> targets,
                                         LinkMode editMode,
                                         TargetMode targetMode,
                                         Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                         Set<String> removedNodeIds,
                                         Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag root = rootTag(stack, true).copy();
        if (!rewriteTrackedRoot(root, targets, editMode, targetMode, replacementsByNodeId,
                removedNodeIds, faceQuarterTurnsByNodeId)) {
            return false;
        }
        writeRootToStack(stack, root);
        return true;
    }

    // Rewrite the tracked root
    static boolean rewriteTrackedRoot(CompoundTag root,
                                      List<LinkedTarget> targets,
                                      LinkMode editMode,
                                      TargetMode targetMode,
                                      Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                      Set<String> removedNodeIds,
                                      Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (root == null) {
            return false;
        }
        CompoundTag prev = root.copy();
        CompoundTag targetRoot = writeRoot(targets, editMode, targetMode);
        root.putString(TAG_MODE, targetRoot.getString(TAG_MODE));
        root.putString(TAG_TARGET_MODE, targetRoot.getString(TAG_TARGET_MODE));
        root.put(TAG_TARGETS, targetRoot.getList(TAG_TARGETS, Tag.TAG_COMPOUND).copy());
        removeClientEditSource(root);

        Map<String, ControllerDiscoveryNode> replacements = replacementsByNodeId == null
                ? Map.of()
                : replacementsByNodeId;
        Set<String> removals = removedNodeIds == null ? Set.of() : removedNodeIds;
        Map<String, Integer> faceQuarterTurns = faceQuarterTurnsByNodeId == null
                ? Map.of()
                : faceQuarterTurnsByNodeId;
        rewriteBindingTargetReferences(root, replacements, removals, faceQuarterTurns);
        return !Objects.equals(prev, root);
    }

    // Remove the targets
    public static boolean removeTargets(CompoundTag root, Predicate<LinkedTarget> shouldRemove) {
        if (root == null || shouldRemove == null) {
            return false;
        }
        List<LinkedTarget> currentTargets = readTargets(root);
        if (currentTargets.isEmpty()) {
            return false;
        }
        List<LinkedTarget> nextTargets = new ArrayList<>();
        Set<String> removedNodeIds = new java.util.LinkedHashSet<>();
        for (LinkedTarget target : currentTargets) {
            if (shouldRemove.test(target)) {
                removedNodeIds.add(nodeIdForTarget(target));
            } else {
                nextTargets.add(target);
            }
        }
        if (removedNodeIds.isEmpty()) {
            return false;
        }

        CompoundTag targetRoot = writeRoot(nextTargets,
                LinkMode.byId(root.getString(TAG_MODE)),
                TargetMode.byId(root.getString(TAG_TARGET_MODE)));
        root.put(TAG_TARGETS, targetRoot.getList(TAG_TARGETS, Tag.TAG_COMPOUND));
        removeBindingReferences(root, removedNodeIds);
        return true;
    }

    // Remove the controller target references
    static boolean removeControllerTargetReferences(CompoundTag controllerData,
                                                    BlockPos blockPos,
                                                    @Nullable UUID subLevelId,
                                                    Set<String> removedNodeIds) {
        if (controllerData == null || blockPos == null) {
            return false;
        }
        Set<String> removals = removedNodeIds == null ? Set.of() : removedNodeIds;
        boolean changed = removeCtrlReferenceSection(
                controllerData, "DirectTargets", blockPos, subLevelId, removals);
        changed |= removeCtrlReferenceSection(
                controllerData, "InputTargets", blockPos, subLevelId, removals);

        if (controllerData.contains("CustomKeyEntries", Tag.TAG_LIST)) {
            ListTag entries = controllerData.getList("CustomKeyEntries", Tag.TAG_COMPOUND);
            ListTag rewritten = new ListTag();
            boolean entriesChanged = false;
            for (int idx = 0; idx < entries.size(); idx++) {
                CompoundTag entry = entries.getCompound(idx).copy();
                if (removeMatchingReference(entry, "DirectTarget", blockPos, subLevelId, removals)) {
                    entriesChanged = true;
                }
                if (removeMatchingReference(entry, "InputTarget", blockPos, subLevelId, removals)) {
                    entriesChanged = true;
                }
                rewritten.add(entry);
            }
            if (entriesChanged) {
                controllerData.put("CustomKeyEntries", rewritten);
                changed = true;
            }
        }

        if (controllerData.contains("StoredTargets", Tag.TAG_LIST)) {
            ListTag targets = controllerData.getList("StoredTargets", Tag.TAG_COMPOUND);
            ListTag retained = new ListTag();
            boolean targetsChanged = false;
            for (int idx = 0; idx < targets.size(); idx++) {
                CompoundTag targetTag = targets.getCompound(idx);
                ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(targetTag);
                boolean remove = node != null
                        && (matchesBlock(node.blockPos(), node.subLevelId(), blockPos, subLevelId)
                        || removals.contains(baseNodeIdFromTargetId(node.nodeId())));
                if (remove) {
                    targetsChanged = true;
                } else {
                    retained.add(targetTag.copy());
                }
            }
            if (targetsChanged) {
                controllerData.put("StoredTargets", retained);
                changed = true;
            }
        }

        changed |= removeGraphTargetReferences(controllerData.getCompound("AdvancedDraftGraph"), removals);
        changed |= removeGraphTargetReferences(controllerData.getCompound("AdvancedActiveGraph"), removals);
        return changed;
    }

    // Remove the graph target references
    static boolean removeGraphTargetReferences(CompoundTag graph, Set<String> removedNodeIds) {
        Set<String> removals = removedNodeIds == null ? Set.of() : removedNodeIds;
        return !removals.isEmpty()
                && rewriteGraphTargetData(graph, Map.of(), removals, Map.of());
    }

    // Remove the ctrl reference section
    private static boolean removeCtrlReferenceSection(CompoundTag controllerData,
                                                            String sectionKey,
                                                            BlockPos blockPos,
                                                            @Nullable UUID subLevelId,
                                                            Set<String> removedNodeIds) {
        if (!controllerData.contains(sectionKey, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag section = controllerData.getCompound(sectionKey);
        CompoundTag retained = new CompoundTag();
        boolean changed = false;
        for (String portId : section.getAllKeys()) {
            CompoundTag referenceTag = section.getCompound(portId);
            ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(referenceTag);
            if (matchesReference(reference, blockPos, subLevelId, removedNodeIds)) {
                changed = true;
            } else {
                retained.put(portId, referenceTag.copy());
            }
        }
        if (changed) {
            controllerData.put(sectionKey, retained);
        }
        return changed;
    }

    // Remove the matching reference
    private static boolean removeMatchingReference(CompoundTag owner,
                                                   String key,
                                                   BlockPos blockPos,
                                                   @Nullable UUID subLevelId,
                                                   Set<String> removedNodeIds) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(owner.getCompound(key));
        if (!matchesReference(reference, blockPos, subLevelId, removedNodeIds)) {
            return false;
        }
        owner.remove(key);
        return true;
    }

    // Check if this matches reference
    private static boolean matchesReference(@Nullable ControllerDirectTargetReference reference,
                                            BlockPos blockPos,
                                            @Nullable UUID subLevelId,
                                            Set<String> removedNodeIds) {
        return reference != null && (matchesBlock(reference.blockPos(), reference.subLevelId(), blockPos, subLevelId)
                || removedNodeIds.contains(baseNodeIdFromTargetId(reference.targetId())));
    }

    // Check if this matches block
    private static boolean matchesBlock(@Nullable BlockPos candidatePos,
                                        @Nullable UUID candidateSubLevelId,
                                        BlockPos blockPos,
                                        @Nullable UUID subLevelId) {
        return Objects.equals(candidatePos, blockPos)
                && Objects.equals(candidateSubLevelId, subLevelId);
    }

    // Clear the contraption network linker data
    public static void clear(ItemStack stack) {
        writeTargets(stack, List.of(), getEditMode(stack));
    }

    // Toggle the selected linker face
    public static boolean toggleFace(ItemStack stack,
                                     BlockPos blockPos,
                                     @Nullable UUID subLevelId,
                                     String blockId,
                                     String blockLabel,
                                     Direction clickedFace,
                                     LinkMode mode) {
        List<LinkedTarget> targets = new ArrayList<>(readTargets(stack));
        int targetIndex = findTargetIndex(targets, blockPos, subLevelId, mode, TargetScope.FACE);
        if (targetIndex < 0) {
            LinkedTarget target = new LinkedTarget(blockPos, subLevelId, blockId, blockLabel, mode,
                TargetScope.FACE, List.of(new LinkedFace(clickedFace, defaultFaceLabel(clickedFace), "")));
            targets.add(target);
            writeTargets(stack, targets, getEditMode(stack));
            return true;
        }

        LinkedTarget existing = targets.get(targetIndex);
        List<LinkedFace> faces = new ArrayList<>(existing.faces());
        int faceIndex = findFaceIndex(faces, clickedFace);
        if (faceIndex >= 0) {
            faces.remove(faceIndex);
            if (faces.isEmpty()) {
                targets.remove(targetIndex);
            } else {
                targets.set(targetIndex, new LinkedTarget(existing.blockPos(), existing.subLevelId(), existing.blockId(),
                        existing.label(), existing.mode(), existing.scope(), faces));
            }
            writeTargets(stack, targets, getEditMode(stack));
            return false;
        }

        faces.add(new LinkedFace(clickedFace, defaultFaceLabel(clickedFace), ""));
        targets.set(targetIndex, new LinkedTarget(existing.blockPos(), existing.subLevelId(), existing.blockId(),
                existing.label(), existing.mode(), existing.scope(), faces));
        writeTargets(stack, targets, getEditMode(stack));
        return true;
    }

    // Cycle the face
    public static FaceCycleState cycleFace(ItemStack stack,
                                           BlockPos blockPos,
                                           @Nullable UUID subLevelId,
                                           String blockId,
                                           String blockLabel,
                                           Direction clickedFace,
                                           LinkMode addMode) {
        return cycleTarget(stack, blockPos, subLevelId, blockId, blockLabel, clickedFace, addMode,
            TargetScope.FACE, null);
    }

    // Cycle the target
    public static FaceCycleState cycleTarget(ItemStack stack,
                                             BlockPos blockPos,
                                             @Nullable UUID subLevelId,
                                             String blockId,
                                             String blockLabel,
                                             Direction clickedFace,
                                             LinkMode addMode,
                                             TargetScope scope,
                                             @Nullable String faceSignalKey) {
        // -----------------------------------------------------TARGET LOOKUP-----------------------------------------------------
        List<LinkedTarget> targets = new ArrayList<>(readTargets(stack));
        LinkMode modeToAdd = LinkMode.OUTPUT;

        int outputTargetIndex = findTargetIndex(targets, blockPos, subLevelId, LinkMode.OUTPUT, scope);
        int inputTargetIndex = findTargetIndex(targets, blockPos, subLevelId, LinkMode.INPUT, scope);
        int scmTargetIndex = findTargetIndex(targets, blockPos, subLevelId, LinkMode.SCM, scope);

        // -----------------------------------------------------FACE TARGETS-----------------------------------------------------
        if (scope.usesFaces()) {
            int outputFaceIndex = outputTargetIndex < 0 ? -1 : findFaceIndex(targets.get(outputTargetIndex).faces(), clickedFace);
            int inputFaceIndex = inputTargetIndex < 0 ? -1 : findFaceIndex(targets.get(inputTargetIndex).faces(), clickedFace);
            int scmFaceIndex = scmTargetIndex < 0 ? -1 : findFaceIndex(targets.get(scmTargetIndex).faces(), clickedFace);

            if (modeToAdd == LinkMode.OUTPUT) {
                if (outputFaceIndex >= 0) {
                    removeFaceFromTarget(targets, outputTargetIndex, clickedFace);
                    addFaceToTarget(targets, blockPos, subLevelId, blockId, blockLabel, clickedFace,
                            LinkMode.INPUT, scope, faceSignalKey);
                    writeTargets(stack, targets, getEditMode(stack));
                    return FaceCycleState.MOVED_TO_INPUT;
                }
                if (inputFaceIndex >= 0) {
                    removeFaceFromTarget(targets, inputTargetIndex, clickedFace);
                    addFaceToTarget(targets, blockPos, subLevelId, blockId, blockLabel, clickedFace,
                            LinkMode.SCM, scope, faceSignalKey);
                    writeTargets(stack, targets, getEditMode(stack));
                    return FaceCycleState.ADDED_SCM;
                }
                if (scmFaceIndex >= 0) {
                    removeFaceFromTarget(targets, scmTargetIndex, clickedFace);
                    writeTargets(stack, targets, getEditMode(stack));
                    return FaceCycleState.REMOVED;
                }
                addFaceToTarget(targets, blockPos, subLevelId, blockId, blockLabel, clickedFace,
                    LinkMode.OUTPUT, scope, faceSignalKey);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.ADDED_OUTPUT;
            }

            if (inputFaceIndex >= 0) {
                removeFaceFromTarget(targets, inputTargetIndex, clickedFace);
                addFaceToTarget(targets, blockPos, subLevelId, blockId, blockLabel, clickedFace,
                    LinkMode.OUTPUT, scope, faceSignalKey);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.MOVED_TO_OUTPUT;
            }
            if (outputFaceIndex >= 0) {
                removeFaceFromTarget(targets, outputTargetIndex, clickedFace);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.REMOVED;
            }
                addFaceToTarget(targets, blockPos, subLevelId, blockId, blockLabel, clickedFace,
                    LinkMode.INPUT, scope, faceSignalKey);
            writeTargets(stack, targets, getEditMode(stack));
            return FaceCycleState.ADDED_INPUT;
        }

        // -----------------------------------------------------BLOCK TARGETS-----------------------------------------------------
        if (modeToAdd == LinkMode.OUTPUT) {
            if (outputTargetIndex >= 0) {
                removeBlockTarget(targets, outputTargetIndex);
                addBlockTarget(targets, blockPos, subLevelId, blockId, blockLabel, LinkMode.INPUT, scope);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.MOVED_TO_INPUT;
            }
            if (inputTargetIndex >= 0) {
                removeBlockTarget(targets, inputTargetIndex);
                addBlockTarget(targets, blockPos, subLevelId, blockId, blockLabel, LinkMode.SCM, scope);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.ADDED_SCM;
            }
            if (scmTargetIndex >= 0) {
                removeBlockTarget(targets, scmTargetIndex);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.REMOVED;
            }
            addBlockTarget(targets, blockPos, subLevelId, blockId, blockLabel, LinkMode.OUTPUT, scope);
            writeTargets(stack, targets, getEditMode(stack));
            return FaceCycleState.ADDED_OUTPUT;
        }

        // -----------------------------------------------------TARGET CYCLE-----------------------------------------------------
        if (inputTargetIndex >= 0) {
            removeBlockTarget(targets, inputTargetIndex);
            addBlockTarget(targets, blockPos, subLevelId, blockId, blockLabel, LinkMode.OUTPUT, scope);
            writeTargets(stack, targets, getEditMode(stack));
            return FaceCycleState.MOVED_TO_OUTPUT;
        }
        if (outputTargetIndex >= 0) {
            removeBlockTarget(targets, outputTargetIndex);
            writeTargets(stack, targets, getEditMode(stack));
            return FaceCycleState.REMOVED;
        }
        addBlockTarget(targets, blockPos, subLevelId, blockId, blockLabel, LinkMode.INPUT, scope);
        writeTargets(stack, targets, getEditMode(stack));
        return FaceCycleState.ADDED_INPUT;
    }

    // Toggle the contraption diagram target
    public static FaceCycleState toggleContraptionDiagramTarget(ItemStack stack,
                                                                BlockPos blockPos,
                                                                UUID subLevelId,
                                                                String label) {
        List<LinkedTarget> targets = new ArrayList<>(readTargets(stack));
        for (int idx = 0; idx < targets.size(); idx++) {
            LinkedTarget target = targets.get(idx);
            if (isContraptionDiagramTarget(target.blockId())
                    && Objects.equals(target.subLevelId(), subLevelId)
                    && Objects.equals(target.blockPos(), blockPos)) {
                targets.remove(idx);
                writeTargets(stack, targets, getEditMode(stack));
                return FaceCycleState.REMOVED;
            }
        }

        targets.add(new LinkedTarget(
                blockPos,
                subLevelId,
                CONTRAPTION_DIAGRAM_TARGET_ID,
                label,
                LinkMode.INPUT,
                TargetScope.BLOCK,
                List.of()));
        writeTargets(stack, targets, getEditMode(stack));
        return FaceCycleState.ADDED_INPUT;
    }

    // Check if this is a contraption diagram target
    public static boolean isContraptionDiagramTarget(@Nullable String blockId) {
        return CONTRAPTION_DIAGRAM_TARGET_ID.equalsIgnoreCase(normalize(blockId));
    }

    // Convert the contraption network linker data to discovery nodes
    public static List<ControllerDiscoveryNode> toDiscoveryNodes(ItemStack stack) {
        return toDiscoveryNodes(readTargets(stack));
    }

    // Get the SCM targets
    public static List<ScmTarget> scmTargets(ItemStack stack) {
        List<ScmTarget> res = new ArrayList<>();
        for (LinkedTarget target : readTargets(stack)) {
            if (target.mode() != LinkMode.SCM) {
                continue;
            }
            if (!target.scope().usesFaces()) {
                res.add(new ScmTarget(target.subLevelId(), target.blockPos(),
                        target.blockId(), target.label()));
                continue;
            }
            for (LinkedFace face : target.faces()) {
                BlockPos controlledPos = target.blockPos().relative(face.face().getOpposite());
                AnalogueControlChannel faceChannel = AnalogueControlChannel.byId(face.signalKey());
                if (faceChannel == null) {
                    faceChannel = FACE_TO_OPTION.get(face.face());
                }
                res.add(new ScmTarget(target.subLevelId(), controlledPos,
                        "", target.label(), target.blockPos(), face.face(),
                        faceChannel == null ? "" : faceChannel.id()));
            }
        }
        return res.stream().distinct().toList();
    }

    // Get the exact signed SCM action bound to one linker face target.
    // An explicit compatible channel takes precedence; ordinary linker faces
    // retain their documented directional fallback.
    public static String scmTargetActionId(@Nullable ScmTarget target) {
        if (target == null || !target.usesFaceControl()) {
            return "";
        }
        AnalogueControlChannel explicit = AnalogueControlChannel.byId(
                target.controlChannelId());
        if (explicit != null) {
            return explicit.id();
        }
        AnalogueControlChannel fallback = FACE_TO_OPTION.get(target.signalFace());
        return fallback == null ? "" : fallback.id();
    }

    // Get the SCM discovery nodes
    public static List<ControllerDiscoveryNode> scmDiscoveryNodes(ItemStack stack) {
        List<ControllerDiscoveryNode> nodes = new ArrayList<>();
        for (ScmTarget target : scmTargets(stack)) {
            String blockId = target.blockId().isBlank() ? "minecraft:air" : target.blockId();
            String label = target.label().isBlank() ? fallbackLabel(blockId) : target.label();
            String groupId = target.subLevelId() == null
                    ? "scm-blocks:world" : "scm-blocks:" + target.subLevelId();
            nodes.add(new ControllerDiscoveryNode(
                    "scm-block@" + target.stableId() + NODE_BLOCK_SUFFIX,
                    ControllerDiscoveryKind.MACHINE, groupId, blockId, label,
                    target.subLevelId(), target.blockPosition()));
        }
        return nodes;
    }

    // Convert the contraption network linker data to discovery nodes
    public static List<ControllerDiscoveryNode> toDiscoveryNodes(List<LinkedTarget> targets) {
        List<ControllerDiscoveryNode> nodes = new ArrayList<>();
        for (LinkedTarget target : sortTargets(targets)) {
            if (target.mode() == LinkMode.SCM) {
                continue;
            }
            List<LinkedFace> faces = sortFaces(target.faces());
            if (target.scope().usesFaces() && faces.isEmpty()) {
                continue;
            }
            String nodeId = nodeIdForTarget(target);
            String groupId = target.subLevelId() == null ? "world" : "sublevel:" + target.subLevelId();
            String blockId = target.blockId().isBlank() ? "minecraft:air" : target.blockId();
            String label = target.label().isBlank() ? fallbackLabel(blockId) : target.label();
            nodes.add(new ControllerDiscoveryNode(nodeId, target.mode().discoveryKind(), groupId, blockId, label,
                    target.subLevelId(), target.blockPos()));
        }
        return nodes;
    }

    // Get the node id for target
    public static String nodeIdForTarget(LinkedTarget target) {
        List<LinkedFace> faces = target == null ? List.of() : sortFaces(target.faces());
        if (target == null) {
            return buildNodeId(BlockPos.ZERO, null, faces, LinkMode.OUTPUT, TargetScope.FACE);
        }
        return buildNodeId(target.blockPos(), target.subLevelId(), faces, target.mode(), target.scope());
    }

    // Get the face options for node
    public static List<FaceOption> faceOptionsForNode(ControllerDiscoveryNode node) {
        if (node == null) {
            return List.of();
        }
        Map<Direction, String> faceSignalMap = parseFaceSignalKeysFromNodeId(node.nodeId());
        List<Direction> faces = new ArrayList<>(faceSignalMap.keySet());
        TargetScope scope = parseScopeFromNodeId(node.nodeId());
        // ------------------------------------RCS THRUSTER FACES------------------------------------
        if (CREATE_THRUSTERS_RCS_THRUSTER.equalsIgnoreCase(node.blockId())) {
            String base = node.label() == null || node.label().isBlank()
                    ? "RCS Thruster" : node.label();
            return List.of(
                    new FaceOption(Direction.NORTH, AnalogueControlChannel.YAW_LEFT,
                            base + " - North Nozzle", null, "nozzle_north"),
                    new FaceOption(Direction.EAST, AnalogueControlChannel.ROLL_RIGHT,
                            base + " - East Nozzle", null, "nozzle_east"),
                    new FaceOption(Direction.SOUTH, AnalogueControlChannel.YAW_RIGHT,
                            base + " - South Nozzle", null, "nozzle_south"),
                    new FaceOption(Direction.WEST, AnalogueControlChannel.ROLL_LEFT,
                            base + " - West Nozzle", null, "nozzle_west"));
        }
        if (scope.usesFaces() && faces.isEmpty()) {
            return List.of();
        }

        // ------------------------------------LINKER FACE INPUTS------------------------------------
        if (node.kind() == ControllerDiscoveryKind.LINKER_FACE_INPUT
                && isDoubleButtonBlockId(node.blockId())) {
            String base = node.label() == null || node.label().isBlank() ? "Double Button" : node.label();
            Direction targetFace = faces.isEmpty() ? Direction.NORTH : faces.getFirst();
            return List.of(
                    new FaceOption(targetFace, AnalogueControlChannel.THROTTLE_UP,
                            base + " - Top", "top_powered"),
                    new FaceOption(targetFace, AnalogueControlChannel.THROTTLE_DOWN,
                            base + " - Bottom", "bottom_powered"));
        }

        // -----------------------------------------------------BLOCK TARGETS-----------------------------------------------------
        if (!scope.usesFaces()) {
            String base = node.label() == null || node.label().isBlank() ? "Target" : node.label();
            if (SIMULATED_GIMBAL_SENSOR.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.NORTH, FACE_TO_OPTION.get(Direction.NORTH),
                                base + " - North", null),
                        new FaceOption(Direction.EAST, FACE_TO_OPTION.get(Direction.EAST),
                                base + " - East", null),
                        new FaceOption(Direction.SOUTH, FACE_TO_OPTION.get(Direction.SOUTH),
                                base + " - South", null),
                        new FaceOption(Direction.WEST, FACE_TO_OPTION.get(Direction.WEST),
                                base + " - West", null));
            }
            if (FORCED_BLOCK_SCOPE_GEARSHIFT.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.WEST, AnalogueControlChannel.YAW_LEFT,
                                base + " - Left Output", "left_powered"),
                        new FaceOption(Direction.EAST, AnalogueControlChannel.YAW_RIGHT,
                                base + " - Right Output", "right_powered"));
            }
            if (AEROWORKS_STEPPER_SERVO.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.WEST, AnalogueControlChannel.YAW_LEFT,
                                base + " - Left Signal", "left_signal"),
                        new FaceOption(Direction.EAST, AnalogueControlChannel.YAW_RIGHT,
                                base + " - Right Signal", "right_signal"));
            }
            if (AEROWORKS_JOYSTICK.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.UP, AnalogueControlChannel.PITCH_UP, base + " - Forward", null),
                        new FaceOption(Direction.DOWN, AnalogueControlChannel.PITCH_DOWN, base + " - Back", null),
                        new FaceOption(Direction.WEST, AnalogueControlChannel.YAW_LEFT, base + " - Left", null),
                        new FaceOption(Direction.EAST, AnalogueControlChannel.YAW_RIGHT, base + " - Right", null),
                        new FaceOption(Direction.NORTH, AnalogueControlChannel.STABILIZE, base + " - Button", null));
            }
            if (CREATE_THRUSTERS_AILERON_BEARING.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.UP, AnalogueControlChannel.PITCH_UP, base + " - Cyan CW", null),
                        new FaceOption(Direction.DOWN, AnalogueControlChannel.PITCH_DOWN, base + " - Cyan CCW", null),
                        new FaceOption(Direction.EAST, AnalogueControlChannel.ROLL_RIGHT, base + " - Orange CW", null),
                        new FaceOption(Direction.WEST, AnalogueControlChannel.ROLL_LEFT, base + " - Orange CCW", null));
            }
            if (CREATE_THRUSTERS_VECTOR_BEARING.equalsIgnoreCase(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.UP, AnalogueControlChannel.PITCH_UP, base + " - Forward", null),
                        new FaceOption(Direction.DOWN, AnalogueControlChannel.PITCH_DOWN, base + " - Backward", null),
                        new FaceOption(Direction.WEST, AnalogueControlChannel.ROLL_LEFT, base + " - Left", null),
                        new FaceOption(Direction.EAST, AnalogueControlChannel.ROLL_RIGHT, base + " - Right", null));
            }
            if (isBearingBlockId(node.blockId())) {
                return List.of(
                        new FaceOption(Direction.EAST, AnalogueControlChannel.ROLL_RIGHT, base + " - CW", null),
                        new FaceOption(Direction.WEST, AnalogueControlChannel.ROLL_LEFT, base + " - CCW", null));
            }
            return List.of(new FaceOption(Direction.NORTH, null, base, null));
        }

        // -----------------------------------------------------FACE OPTIONS-----------------------------------------------------
        List<FaceOption> opts = new ArrayList<>();
        String base = node.label() == null || node.label().isBlank() ? "Target" : node.label();
        for (Direction face : faces) {
            AnalogueControlChannel optionChannel = FACE_TO_OPTION.get(face);
            if (optionChannel == null) {
                continue;
            }
            String signalKey = faceSignalMap.get(face);
            opts.add(new FaceOption(face, optionChannel, base + " - " + defaultFaceLabel(face),
                    signalKey == null || signalKey.isBlank() ? null : signalKey));
        }
        return opts;
    }

    // Get the face options for node
    public static List<FaceOption> faceOptionsForNode(@Nullable Level level, ControllerDiscoveryNode node) {
        if (level != null && node != null && node.blockPos() != null
                && AEROWORKS_CONTROL_DESK.equalsIgnoreCase(node.blockId())) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, node.subLevelId(), node.blockPos());
            List<AeroworksControllerCompat.ConsoleOption> consoleOptions =
                    AeroworksControllerCompat.consoleOptions(blockEntity);
            if (!consoleOptions.isEmpty()) {
                return consoleOptions.stream()
                        .map(option -> new FaceOption(null, null, option.label(), null, option.channelId()))
                        .toList();
            }
        }
        return faceOptionsForNode(node);
    }

    // Check if the node uses face options
    public static boolean nodeUsesFaceOptions(@Nullable ControllerDiscoveryNode node) {
        if (node == null) {
            return true;
        }
        return parseScopeFromNodeId(node.nodeId()).usesFaces();
    }

    // Resolve the face from direct target
    public static @Nullable Direction resolveFaceFromDirectTarget(ControllerDirectTargetReference directTarget) {
        if (directTarget == null) {
            return null;
        }
        String targetId = directTarget.targetId();
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        if (optionIndex < 0) {
            return null;
        }
        String option = targetId.substring(optionIndex + DIRECT_TARGET_OPTION_PREFIX.length()).trim();
        int propertyIndex = option.indexOf(DIRECT_TARGET_PROPERTY_PREFIX);
        if (propertyIndex >= 0) {
            option = option.substring(0, propertyIndex).trim();
        }
        option = option.toLowerCase(Locale.ROOT);
        return OPTION_TO_FACE.get(option);
    }

    // Get the direct target for face
    public static @Nullable ControllerDirectTargetReference directTargetForFace(ControllerDiscoveryNode node,
                                                                                 @Nullable Direction face) {
        if (node == null) {
            return null;
        }
        ControllerDirectTargetReference base = node.asDirectTargetReference();
        boolean rcsThruster = CREATE_THRUSTERS_RCS_THRUSTER.equalsIgnoreCase(node.blockId());
        if (face == null || !isLinkerFaceTarget(base) && !rcsThruster) {
            return base;
        }
        for (FaceOption option : faceOptionsForNode(node)) {
            if (option.face() != face || option.targetChannelId() == null
                    || option.targetChannelId().isBlank()) {
                continue;
            }
            String targetId = node.nodeId() + DIRECT_TARGET_OPTION_PREFIX + option.targetChannelId();
            if (option.signalPropertyKey() != null && !option.signalPropertyKey().isBlank()) {
                targetId += DIRECT_TARGET_PROPERTY_PREFIX + option.signalPropertyKey().trim().toLowerCase(Locale.ROOT);
            }
            String label = option.label() == null || option.label().isBlank() ? base.label() : option.label();
            return new ControllerDirectTargetReference(targetId, base.targetTypeId(), base.groupId(), label,
                    base.subLevelId(), base.blockPos());
        }
        return nodeUsesFaceOptions(node) ? null : base;
    }

    // Check if this is a linker face target type
    public static boolean isLinkerFaceTargetType(String targetTypeId) {
        if (targetTypeId == null) {
            return false;
        }
        String normalized = targetTypeId.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(ControllerDiscoveryKind.LINKER_FACE_INPUT.id())
                || normalized.equals(ControllerDiscoveryKind.LINKER_FACE_OUTPUT.id());
    }

    // Check if this is a linker face output type
    public static boolean isLinkerFaceOutputType(String targetTypeId) {
        if (targetTypeId == null) {
            return false;
        }
        return targetTypeId.trim().toLowerCase(Locale.ROOT).equals(ControllerDiscoveryKind.LINKER_FACE_OUTPUT.id());
    }

    // Check if this is a linker face target
    public static boolean isLinkerFaceTarget(@Nullable ControllerDirectTargetReference target) {
        if (target == null || !target.isBound()) {
            return false;
        }
        return isLinkerFaceTargetType(target.targetTypeId()) || isLinkerTargetId(target.targetId());
    }

    // Check if this is a linker face output target
    public static boolean isLinkerFaceOutputTarget(@Nullable ControllerDirectTargetReference target) {
        if (target == null || !target.isBound()) {
            return false;
        }
        if (isLinkerFaceOutputType(target.targetTypeId())) {
            return true;
        }
        String targetId = target.targetId();
        if (targetId == null) {
            return false;
        }
        String normalized = targetId.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith((NODE_PREFIX + ":" + LinkMode.OUTPUT.id() + "@").toLowerCase(Locale.ROOT));
    }

    // Check if this is a linker target ID
    private static boolean isLinkerTargetId(@Nullable String targetId) {
        if (targetId == null) {
            return false;
        }
        String normalized = targetId.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith((NODE_PREFIX + ":" + LinkMode.INPUT.id() + "@").toLowerCase(Locale.ROOT))
                || normalized.startsWith((NODE_PREFIX + ":" + LinkMode.OUTPUT.id() + "@").toLowerCase(Locale.ROOT));
    }

    // Get the base node id from target id
    public static String baseNodeIdFromTargetId(@Nullable String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return "";
        }
        int optionIndex = targetId.indexOf(DIRECT_TARGET_OPTION_PREFIX);
        if (optionIndex >= 0) {
            return targetId.substring(0, optionIndex);
        }
        return targetId;
    }

    // Replace the base node id
    public static String replaceBaseNodeId(@Nullable String targetId, String newBaseNodeId) {
        String safeBaseNodeId = newBaseNodeId == null ? "" : newBaseNodeId;
        if (targetId == null || targetId.isBlank()) {
            return safeBaseNodeId;
        }
        String currentBaseNodeId = baseNodeIdFromTargetId(targetId);
        if (currentBaseNodeId.isEmpty()) {
            return safeBaseNodeId;
        }
        return safeBaseNodeId + targetId.substring(currentBaseNodeId.length());
    }

    // Read the channel bindings
    public static Map<String, ChannelBind> readChannelBindings(ItemStack stack) {
        CompoundTag root = rootTag(stack, false);
        if (root == null) {
            return Map.of();
        }
        return readChannelBindings(root);
    }

    // Read the channel bindings
    public static Map<String, ChannelBind> readChannelBindings(CompoundTag root) {
        if (root == null || !root.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            return Map.of();
        }

        CompoundTag bindsTag = root.getCompound(TAG_CHANNEL_BINDS);
        Map<String, ChannelBind> binds = new LinkedHashMap<>();
        for (String channelId : bindsTag.getAllKeys()) {
            CompoundTag channelBindTag = bindsTag.getCompound(channelId);
            ControllerDirectTargetReference directTarget = null;
            ControllerDirectTargetReference inputTarget = null;
            if (channelBindTag.contains(TAG_BIND_DIRECT, Tag.TAG_COMPOUND)) {
                directTarget = ControllerDirectTargetReference.fromTag(channelBindTag.getCompound(TAG_BIND_DIRECT));
            }
            if (channelBindTag.contains(TAG_BIND_INPUT, Tag.TAG_COMPOUND)) {
                inputTarget = ControllerDirectTargetReference.fromTag(channelBindTag.getCompound(TAG_BIND_INPUT));
            }
            if ((directTarget != null && directTarget.isBound()) || (inputTarget != null && inputTarget.isBound())) {
                binds.put(channelId, new ChannelBind(directTarget, inputTarget));
            }
        }
        return binds;
    }

    // Write the channel binding
    public static void writeChannelBinding(ItemStack stack, String channelId,
                                           @Nullable ControllerDirectTargetReference directTarget,
                                           @Nullable ControllerDirectTargetReference inputTarget) {
        if (stack == null || stack.isEmpty() || channelId == null || channelId.isBlank()) {
            return;
        }

        CompoundTag root = rootTag(stack, true);
        CompoundTag bindsTag = root.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)
                ? root.getCompound(TAG_CHANNEL_BINDS)
                : new CompoundTag();

        ControllerDirectTargetReference sanitizedDirect = sanitizeBoundTarget(directTarget);
        ControllerDirectTargetReference sanitizedInput = sanitizeBoundTarget(inputTarget);
        if (sanitizedDirect == null && sanitizedInput == null) {
            bindsTag.remove(channelId);
        } else {
            CompoundTag channelBindTag = new CompoundTag();
            if (sanitizedDirect != null) {
                channelBindTag.put(TAG_BIND_DIRECT, sanitizedDirect.toTag());
            }
            if (sanitizedInput != null) {
                channelBindTag.put(TAG_BIND_INPUT, sanitizedInput.toTag());
            }
            bindsTag.put(channelId, channelBindTag);
        }

        if (bindsTag.getAllKeys().isEmpty()) {
            root.remove(TAG_CHANNEL_BINDS);
        } else {
            root.put(TAG_CHANNEL_BINDS, bindsTag);
        }

        writeRootToStack(stack, root);
    }

    // Merge the root with existing bindings
    public static CompoundTag mergeRootWithExistingBindings(ItemStack stack, CompoundTag incomingRoot) {
        return mergeRootWithExistingBindings(incomingRoot, rootTag(stack, false));
    }

    // Merge the root with existing bindings
    static CompoundTag mergeRootWithExistingBindings(CompoundTag incomingRoot,
                                                     @Nullable CompoundTag existingRoot) {
        CompoundTag mergedRoot = incomingRoot == null ? new CompoundTag() : incomingRoot.copy();
        if (existingRoot == null || existingRoot.isEmpty()) {
            return mergedRoot;
        }

        if (existingRoot.hasUUID(TAG_LINKER_ID) && !mergedRoot.hasUUID(TAG_LINKER_ID)) {
            mergedRoot.putUUID(TAG_LINKER_ID, existingRoot.getUUID(TAG_LINKER_ID));
        }
        if (existingRoot.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)
                && !mergedRoot.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            mergedRoot.put(TAG_STORED_GRAPHS, existingRoot.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND).copy());
        }
        if (existingRoot.contains(TAG_SELECTED_GRAPH_ID, Tag.TAG_STRING)
                && !mergedRoot.contains(TAG_SELECTED_GRAPH_ID, Tag.TAG_STRING)) {
            mergedRoot.putString(TAG_SELECTED_GRAPH_ID, existingRoot.getString(TAG_SELECTED_GRAPH_ID));
        }

        Set<String> currentNodeIds = currentNodeIds(mergedRoot);
        mergeChannelBindings(mergedRoot, existingRoot, currentNodeIds);
        mergeCustomEntryBindings(mergedRoot, existingRoot, currentNodeIds);
        return mergedRoot;
    }

    // Sanitize and merge a client edit
    static @Nullable CompoundTag sanitizeAndMergeClientEdit(CompoundTag incomingRoot,
                                                            @Nullable CompoundTag existingRoot) {
        CompoundTag incoming = incomingRoot == null ? new CompoundTag() : incomingRoot;
        List<LinkedTarget> existingTargets = existingRoot == null ? List.of() : readTargets(existingRoot);
        SanitizedClientTargets edit = sanitizeClientTargets(readTargets(incoming), existingTargets);
        if (edit == null) {
            return null;
        }
        CompoundTag editableRoot = writeRoot(
                edit.targets(),
                LinkMode.byId(incoming.getString(TAG_MODE)),
                TargetMode.byId(incoming.getString(TAG_TARGET_MODE)));
        CompoundTag serverRoot = existingRoot == null ? new CompoundTag() : existingRoot;
        CompoundTag mergedRoot = serverRoot.copy();
        mergedRoot.putString(TAG_MODE, editableRoot.getString(TAG_MODE));
        mergedRoot.putString(TAG_TARGET_MODE, editableRoot.getString(TAG_TARGET_MODE));
        mergedRoot.put(TAG_TARGETS, editableRoot.getList(TAG_TARGETS, Tag.TAG_COMPOUND).copy());
        removeClientEditSource(mergedRoot);

        Set<String> currentNodeIds = currentNodeIds(mergedRoot);
        mergeChannelBindings(mergedRoot, serverRoot, currentNodeIds);
        mergeCustomEntryBindings(mergedRoot, serverRoot, currentNodeIds);
        return mergedRoot;
    }

    // Sanitize the client targets
    private static @Nullable SanitizedClientTargets sanitizeClientTargets(List<LinkedTarget> incomingTargets,
                                                                           List<LinkedTarget> existingTargets) {
        List<LinkedTarget> available = new ArrayList<>(existingTargets == null ? List.of() : existingTargets);
        List<LinkedTarget> sanitized = new ArrayList<>();
        Map<String, ControllerDiscoveryNode> replacements = new LinkedHashMap<>();
        Set<String> removals = new java.util.LinkedHashSet<>();
        for (LinkedTarget existing : available) {
            removals.add(nodeIdForTarget(existing));
        }
        for (LinkedTarget incoming : incomingTargets == null ? List.<LinkedTarget>of() : incomingTargets) {
            int existingIndex = findEditableTarget(available, incoming);
            if (existingIndex < 0) {
                return null;
            }
            LinkedTarget existing = available.remove(existingIndex);
            List<LinkedFace> faces = sanitizeClientFaces(incoming, existing);
            if (faces == null) {
                return null;
            }
            LinkedTarget edited = new LinkedTarget(
                    existing.blockPos(),
                    existing.subLevelId(),
                    existing.blockId(),
                    sanitizeClientLabel(incoming.label()),
                    incoming.mode(),
                    existing.scope(),
                    faces);
            sanitized.add(edited);
            String previousNodeId = nodeIdForTarget(existing);
            removals.remove(previousNodeId);
            List<ControllerDiscoveryNode> replacementNodes = toDiscoveryNodes(List.of(edited));
            if (!replacementNodes.isEmpty()) {
                replacements.put(previousNodeId, replacementNodes.getFirst());
            }
        }
        return new SanitizedClientTargets(
                List.copyOf(sanitized),
                Map.copyOf(replacements),
                Set.copyOf(removals));
    }

    // Find the editable target
    private static int findEditableTarget(List<LinkedTarget> available, LinkedTarget incoming) {
        for (int idx = 0; idx < available.size(); idx++) {
            LinkedTarget existing = available.get(idx);
            if (Objects.equals(existing.blockPos(), incoming.blockPos())
                    && Objects.equals(existing.subLevelId(), incoming.subLevelId())
                    && Objects.equals(existing.blockId(), incoming.blockId())
                    && existing.scope() == incoming.scope()
                    && hasOnlyExistingFaces(incoming.faces(), existing.faces())) {
                return idx;
            }
        }
        return -1;
    }

    // Check if this has only existing faces
    private static boolean hasOnlyExistingFaces(List<LinkedFace> incomingFaces, List<LinkedFace> existingFaces) {
        if (incomingFaces == null || incomingFaces.isEmpty()) {
            return true;
        }
        Set<String> seen = new java.util.HashSet<>();
        for (LinkedFace incoming : incomingFaces) {
            String identity = incoming.face().getSerializedName() + "\u0000" + incoming.signalKey();
            if (!seen.add(identity) || findEditableFace(existingFaces, incoming) == null) {
                return false;
            }
        }
        return true;
    }

    // Sanitize the client faces
    private static @Nullable List<LinkedFace> sanitizeClientFaces(LinkedTarget incoming, LinkedTarget existing) {
        if (!existing.scope().usesFaces()) {
            return incoming.faces().isEmpty() ? List.of() : null;
        }
        List<LinkedFace> faces = new ArrayList<>();
        for (LinkedFace incomingFace : incoming.faces()) {
            LinkedFace existingFace = findEditableFace(existing.faces(), incomingFace);
            if (existingFace == null) {
                return null;
            }
            faces.add(new LinkedFace(existingFace.face(), sanitizeClientLabel(incomingFace.label()),
                    existingFace.signalKey()));
        }
        return List.copyOf(faces);
    }

    // Find the editable face
    private static @Nullable LinkedFace findEditableFace(List<LinkedFace> existingFaces, LinkedFace incoming) {
        if (existingFaces == null || incoming == null) {
            return null;
        }
        for (LinkedFace existing : existingFaces) {
            if (existing.face() == incoming.face()
                    && Objects.equals(existing.signalKey(), incoming.signalKey())) {
                return existing;
            }
        }
        return null;
    }

    // Sanitize the client label
    private static String sanitizeClientLabel(String label) {
        String normalized = normalize(label);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    // Store the sanitized client targets
    private record SanitizedClientTargets(List<LinkedTarget> targets,
                                          Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                          Set<String> removedNodeIds) {
    }

    // Get the current node ids
    private static Set<String> currentNodeIds(CompoundTag root) {
        Set<String> nodeIds = new java.util.LinkedHashSet<>();
        for (LinkedTarget target : readTargets(root)) {
            nodeIds.add(nodeIdForTarget(target));
        }
        return nodeIds;
    }

    // Merge the channel bindings
    private static void mergeChannelBindings(CompoundTag mergedRoot,
                                             CompoundTag existingRoot,
                                             Set<String> currentNodeIds) {
        CompoundTag combined = new CompoundTag();
        if (existingRoot.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            CompoundTag existingBinds = existingRoot.getCompound(TAG_CHANNEL_BINDS);
            for (String channelId : existingBinds.getAllKeys()) {
                combined.put(channelId, existingBinds.getCompound(channelId).copy());
            }
        }
        if (mergedRoot.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            CompoundTag incomingBinds = mergedRoot.getCompound(TAG_CHANNEL_BINDS);
            for (String channelId : incomingBinds.getAllKeys()) {
                combined.put(channelId, incomingBinds.getCompound(channelId).copy());
            }
        }

        CompoundTag pruned = new CompoundTag();
        for (String channelId : combined.getAllKeys()) {
            CompoundTag channelTag = combined.getCompound(channelId).copy();
            pruneTargetField(channelTag, TAG_BIND_DIRECT, currentNodeIds);
            pruneTargetField(channelTag, TAG_BIND_INPUT, currentNodeIds);
            if (channelTag.contains(TAG_BIND_DIRECT, Tag.TAG_COMPOUND)
                    || channelTag.contains(TAG_BIND_INPUT, Tag.TAG_COMPOUND)) {
                pruned.put(channelId, channelTag);
            }
        }

        if (pruned.getAllKeys().isEmpty()) {
            mergedRoot.remove(TAG_CHANNEL_BINDS);
        } else {
            mergedRoot.put(TAG_CHANNEL_BINDS, pruned);
        }
    }

    // Merge the custom entry bindings
    private static void mergeCustomEntryBindings(CompoundTag mergedRoot,
                                                 CompoundTag existingRoot,
                                                 Set<String> currentNodeIds) {
        Map<String, CompoundTag> combined = new LinkedHashMap<>();
        putCustomBindingsById(combined, existingRoot);
        putCustomBindingsById(combined, mergedRoot);

        ListTag pruned = new ListTag();
        for (CompoundTag tag : combined.values()) {
            CompoundTag next = pruneCustomBinding(tag, currentNodeIds);
            if (next != null) {
                pruned.add(next);
            }
        }

        if (pruned.isEmpty()) {
            mergedRoot.remove(TAG_CUSTOM_ENTRY_BINDS);
        } else {
            mergedRoot.put(TAG_CUSTOM_ENTRY_BINDS, pruned);
        }
    }

    // Put the custom bindings by id
    private static void putCustomBindingsById(Map<String, CompoundTag> bindings, CompoundTag root) {
        if (root == null || !root.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = root.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < list.size(); idx++) {
            CompoundTag tag = list.getCompound(idx).copy();
            String id = readCustomEntryId(tag);
            bindings.put(id == null || id.isBlank() ? "index:" + bindings.size() : id, tag);
        }
    }

    // Get the prune custom binding
    private static @Nullable CompoundTag pruneCustomBinding(CompoundTag tag, Set<String> currentNodeIds) {
        CompoundTag copy = tag.copy();
        boolean hadLinkerTarget = hasLinkerTarget(copy, TAG_BIND_DIRECT) || hasLinkerTarget(copy, TAG_BIND_INPUT);
        boolean directKept = pruneTargetField(copy, TAG_BIND_DIRECT, currentNodeIds);
        boolean inputKept = pruneTargetField(copy, TAG_BIND_INPUT, currentNodeIds);
        if (hadLinkerTarget && !directKept && !inputKept) {
            return null;
        }
        return copy;
    }

    // Prune the stale target field
    private static boolean pruneTargetField(CompoundTag owner, String key, Set<String> currentNodeIds) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag targetTag = owner.getCompound(key);
        ControllerDirectTargetReference target = ControllerDirectTargetReference.fromTag(targetTag);
        if (target == null) {
            owner.remove(key);
            return false;
        }
        if (!isLinkerFaceTarget(target)) {
            return true;
        }
        String baseNodeId = baseNodeIdFromTargetId(target.targetId());
        if (currentNodeIds.contains(baseNodeId)) {
            return true;
        }
        owner.remove(key);
        return false;
    }

    // Check if this has linker target
    private static boolean hasLinkerTarget(CompoundTag owner, String key) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        ControllerDirectTargetReference target = ControllerDirectTargetReference.fromTag(owner.getCompound(key));
        return isLinkerFaceTarget(target);
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

    // Read the custom entry bindings
    public static List<CompoundTag> readCustomEntryBindings(ItemStack stack) {
        CompoundTag root = rootTag(stack, false);
        return readCustomEntryBindings(root);
    }

    // Read the custom entry bindings
    public static List<CompoundTag> readCustomEntryBindings(CompoundTag root) {
        if (root == null || !root.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag listTag = root.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND);
        List<CompoundTag> copied = new ArrayList<>();
        for (int idx = 0; idx < listTag.size(); idx++) {
            copied.add(listTag.getCompound(idx).copy());
        }
        return copied;
    }

    // Write the custom entry bindings
    public static void writeCustomEntryBindings(ItemStack stack, List<CompoundTag> entryTags) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        CompoundTag root = rootTag(stack, true);
        ListTag listTag = new ListTag();
        if (entryTags != null) {
            for (CompoundTag entryTag : entryTags) {
                if (entryTag == null || entryTag.isEmpty()) {
                    continue;
                }
                listTag.add(entryTag.copy());
            }
        }

        if (listTag.isEmpty()) {
            root.remove(TAG_CUSTOM_ENTRY_BINDS);
        } else {
            root.put(TAG_CUSTOM_ENTRY_BINDS, listTag);
        }

        writeRootToStack(stack, root);
    }

    // Read the stored graphs
    public static List<StoredGraph> readStoredGraphs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        CompoundTag stackTag = stackCustomDataTagView(stack);
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            CompoundTag graphRoot = new CompoundTag();
            graphRoot.put(TAG_STORED_GRAPHS, ControllerSqliteStore.loadLinkerStoredGraphs(
                    ControllerManifestStore.linkerManifestId(stackTag)));
            return readStoredGraphs(graphRoot);
        }
        return stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)
                ? readStoredGraphs(stackTag.getCompound(TAG_ROOT))
                : List.of();
    }

    // Read the stored graphs
    public static List<StoredGraph> readStoredGraphs(CompoundTag root) {
        if (root == null || !root.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag listTag = root.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND);
        List<StoredGraph> graphs = new ArrayList<>();
        for (int idx = 0; idx < listTag.size(); idx++) {
            CompoundTag entry = listTag.getCompound(idx);
            CompoundTag graphTag = entry.getCompound(TAG_GRAPH);
            if (graphTag.isEmpty()) {
                continue;
            }
            String graphId = entry.getString(TAG_GRAPH_ID);
            if (graphId.isBlank()) {
                graphId = graphIdFor(entry.getString(TAG_GRAPH_NAME), graphTag);
            }
            graphs.add(new StoredGraph(graphId, entry.getString(TAG_GRAPH_NAME), graphTag));
        }
        return List.copyOf(graphs);
    }

    // Read the selected stored graph
    public static @Nullable StoredGraph readSelectedStoredGraph(ItemStack stack) {
        CompoundTag root = rootTag(stack, false);
        if (root == null) {
            return null;
        }
        String selectedGraphId = root.getString(TAG_SELECTED_GRAPH_ID);
        List<StoredGraph> graphs = readStoredGraphs(stack);
        if (graphs.isEmpty()) {
            return null;
        }
        if (!selectedGraphId.isBlank()) {
            for (StoredGraph graph : graphs) {
                if (selectedGraphId.equals(graph.graphId())) {
                    return graph;
                }
            }
        }
        return graphs.getFirst();
    }

    // Write the stored graph
    public static boolean writeStoredGraph(ItemStack stack, String name, AdvancedGraphDocument graph) {
        if (stack == null || stack.isEmpty() || graph == null) {
            return false;
        }
        CompoundTag root = rootTag(stack, true);
        putStoredGraph(root, name, graph.toTag(), false);
        writeRootToStack(stack, root);
        return true;
    }

    // Clone the controller draft graph
    public static boolean cloneControllerDraftGraph(
            ItemStack stack, String controllerManifestId, String graphName) {
        if (stack == null || stack.isEmpty()
                || controllerManifestId == null || controllerManifestId.isBlank()
                || !ensureStoredIdentity(stack)) {
            return false;
        }
        String linkerManifestId = ControllerManifestStore.linkerManifestId(
                stackCustomDataTagView(stack));
        return ControllerSqliteStore.cloneControllerDraftGraphToLinker(
                controllerManifestId, linkerManifestId, graphName);
    }

    // Rewrite the stored graph targets
    public static boolean rewriteStoredGraphTargets(ItemStack stack,
                                                    Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                                    Set<String> removedNodeIds) {
        return rewriteStoredGraphTargets(stack, replacementsByNodeId, removedNodeIds, Map.of());
    }

    // Rewrite the stored graph targets
    public static boolean rewriteStoredGraphTargets(ItemStack stack,
                                                    Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                                    Set<String> removedNodeIds,
                                                    Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag root = rootTag(stack, true);
        if (!rewriteStoredGraphTargets(root, replacementsByNodeId, removedNodeIds, faceQuarterTurnsByNodeId)) {
            return false;
        }
        writeRootToStack(stack, root);
        return true;
    }

    // Rewrite the stored graph targets
    public static boolean rewriteStoredGraphTargets(CompoundTag root,
                                                    Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                                    Set<String> removedNodeIds) {
        return rewriteStoredGraphTargets(root, replacementsByNodeId, removedNodeIds, Map.of());
    }

    // Rewrite the stored graph targets
    public static boolean rewriteStoredGraphTargets(CompoundTag root,
                                                    Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                                    Set<String> removedNodeIds,
                                                    Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (root == null || !root.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            return false;
        }
        Map<String, ControllerDiscoveryNode> replacements =
                replacementsByNodeId == null ? Map.of() : replacementsByNodeId;
        Set<String> removals = removedNodeIds == null ? Set.of() : removedNodeIds;
        Map<String, Integer> faceQuarterTurns = faceQuarterTurnsByNodeId == null
                ? Map.of()
                : faceQuarterTurnsByNodeId;
        if (replacements.isEmpty() && removals.isEmpty() && faceQuarterTurns.isEmpty()) {
            return false;
        }

        boolean changed = false;
        ListTag graphList = root.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND);
        ListTag rewrittenGraphs = new ListTag();
        for (int idx = 0; idx < graphList.size(); idx++) {
            CompoundTag entry = graphList.getCompound(idx).copy();
            if (entry.contains(TAG_GRAPH, Tag.TAG_COMPOUND)) {
                CompoundTag graph = entry.getCompound(TAG_GRAPH).copy();
                if (rewriteGraphTargetData(graph, replacements, removals, faceQuarterTurns)) {
                    entry.put(TAG_GRAPH, graph);
                    changed = true;
                }
            }
            rewrittenGraphs.add(entry);
        }
        if (changed) {
            root.put(TAG_STORED_GRAPHS, rewrittenGraphs);
        }
        return changed;
    }

    // Get the shared root with graph
    public static CompoundTag sharedRootWithGraph(ItemStack stack, String name, AdvancedGraphDocument graph) {
        CompoundTag root = rootTag(stack, false);
        CompoundTag sharedRoot = new CompoundTag();
        if (root != null) {
            if (root.contains(TAG_MODE, Tag.TAG_STRING)) {
                sharedRoot.putString(TAG_MODE, root.getString(TAG_MODE));
            }
            if (root.contains(TAG_TARGET_MODE, Tag.TAG_STRING)) {
                sharedRoot.putString(TAG_TARGET_MODE, root.getString(TAG_TARGET_MODE));
            }
        }
        putStoredGraph(sharedRoot, name, graph == null ? new CompoundTag() : graph.toTag(), true);
        return sanitizeForSharing(sharedRoot);
    }

    // Get the shared root with graph
    public static CompoundTag sharedRootWithGraph(String name, AdvancedGraphDocument graph) {
        CompoundTag sharedRoot = new CompoundTag();
        putStoredGraph(sharedRoot, name, graph == null ? new CompoundTag() : graph.toTag(), true);
        return sanitizeForSharing(sharedRoot);
    }

    // Prepare the shared graph import root
    public static CompoundTag prepareSharedGraphImportRoot(ItemStack stack, CompoundTag incomingRoot) {
        CompoundTag root = incomingRoot == null ? new CompoundTag() : incomingRoot.copy();
        CompoundTag existing = rootTag(stack, false);
        if (existing != null && existing.hasUUID(TAG_LINKER_ID)) {
            root.putUUID(TAG_LINKER_ID, existing.getUUID(TAG_LINKER_ID));
        } else if (!root.hasUUID(TAG_LINKER_ID)) {
            root.putUUID(TAG_LINKER_ID, UUID.randomUUID());
        }
        if (existing != null) {
            if (existing.contains(TAG_TARGETS, Tag.TAG_LIST)) {
                root.put(TAG_TARGETS, existing.getList(TAG_TARGETS, Tag.TAG_COMPOUND).copy());
            }
            if (existing.contains(TAG_MODE, Tag.TAG_STRING)) {
                root.putString(TAG_MODE, existing.getString(TAG_MODE));
            }
            if (existing.contains(TAG_TARGET_MODE, Tag.TAG_STRING)) {
                root.putString(TAG_TARGET_MODE, existing.getString(TAG_TARGET_MODE));
            }
        }
        root.remove(TAG_CHANNEL_BINDS);
        root.remove(TAG_CUSTOM_ENTRY_BINDS);
        return root;
    }

    // Sanitize linker data for sharing
    public static CompoundTag sanitizeForSharing(CompoundTag root) {
        CompoundTag copy = root == null ? new CompoundTag() : root.copy();
        copy.remove(TAG_LINKER_ID);
        copy.remove(TAG_TARGETS);
        copy.remove(TAG_CHANNEL_BINDS);
        copy.remove(TAG_CUSTOM_ENTRY_BINDS);
        copy.remove("StoredControllerManifests");
        if (copy.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            ListTag sanitizedGraphs = new ListTag();
            ListTag graphList = copy.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND);
            for (int idx = 0; idx < graphList.size(); idx++) {
                CompoundTag entry = graphList.getCompound(idx).copy();
                if (entry.contains(TAG_GRAPH, Tag.TAG_COMPOUND)) {
                    entry.put(TAG_GRAPH, sanitizeGraphTag(entry.getCompound(TAG_GRAPH)));
                }
                sanitizedGraphs.add(entry);
            }
            copy.put(TAG_STORED_GRAPHS, sanitizedGraphs);
        }
        return copy;
    }

    // Put the stored graph
    private static void putStoredGraph(CompoundTag root, String name, CompoundTag graphTag, boolean sanitizeGraph) {
        if (root == null || graphTag == null || graphTag.isEmpty()) {
            return;
        }
        String safeName = name == null || name.isBlank() ? "Graph" : name.trim();
        CompoundTag storedGraph = new CompoundTag();
        String graphId = graphIdFor(safeName, graphTag);
        storedGraph.putString(TAG_GRAPH_ID, graphId);
        storedGraph.putString(TAG_GRAPH_NAME, safeName);
        storedGraph.put(TAG_GRAPH, sanitizeGraph ? sanitizeGraphTag(graphTag) : graphTag.copy());

        ListTag graphs = new ListTag();
        if (root.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            ListTag existing = root.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND);
            for (int idx = 0; idx < existing.size(); idx++) {
                CompoundTag entry = existing.getCompound(idx);
                if (!graphId.equals(entry.getString(TAG_GRAPH_ID))) {
                    graphs.add(entry.copy());
                }
            }
        }
        graphs.add(storedGraph);
        root.put(TAG_STORED_GRAPHS, graphs);
        root.putString(TAG_SELECTED_GRAPH_ID, graphId);
    }

    // Sanitize the graph tag
    private static CompoundTag sanitizeGraphTag(CompoundTag graphTag) {
        CompoundTag sanitized = graphTag == null ? new CompoundTag() : graphTag.copy();
        ListTag nodes = sanitized.getList("Nodes", Tag.TAG_COMPOUND);
        ListTag sanitizedNodes = new ListTag();
        for (int idx = 0; idx < nodes.size(); idx++) {
            CompoundTag node = nodes.getCompound(idx).copy();
            CompoundTag data = node.getCompound("Data");
            clearLinkedBlockFields(data);
            node.put("Data", data);
            sanitizedNodes.add(node);
        }
        sanitized.put("Nodes", sanitizedNodes);
        return sanitized;
    }

    // Rewrite the graph target data
    private static boolean rewriteGraphTargetData(CompoundTag graphTag,
                                                  Map<String, ControllerDiscoveryNode> replacementsByNodeId,
                                                  Set<String> removedNodeIds,
                                                  Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (graphTag == null || !graphTag.contains("Nodes", Tag.TAG_LIST)) {
            return false;
        }
        boolean changed = false;
        ListTag nodes = graphTag.getList("Nodes", Tag.TAG_COMPOUND);
        ListTag rewrittenNodes = new ListTag();
        for (int idx = 0; idx < nodes.size(); idx++) {
            CompoundTag node = nodes.getCompound(idx).copy();
            CompoundTag data = node.getCompound("Data").copy();
            String baseNodeId = graphBaseNodeId(data);
            if (!baseNodeId.isBlank() && removedNodeIds.contains(baseNodeId)) {
                clearLinkedBlockFields(data);
                node.put("Data", data);
                changed = true;
            } else if (!baseNodeId.isBlank()) {
                ControllerDiscoveryNode replacement = replacementsByNodeId.get(baseNodeId);
                if (replacement != null && rewriteGraphNodeTargetData(data, baseNodeId, replacement,
                        faceQuarterTurnsByNodeId.getOrDefault(baseNodeId, 0))) {
                    node.put("Data", data);
                    changed = true;
                }
            }
            rewrittenNodes.add(node);
        }
        if (changed) {
            graphTag.put("Nodes", rewrittenNodes);
        }
        return changed;
    }

    // Get the graph base node id
    private static String graphBaseNodeId(CompoundTag data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(data.getCompound("TargetData"));
        if (discovery != null && !discovery.nodeId().isBlank()) {
            return discovery.nodeId();
        }
        String target = data.getString("Target");
        if (!target.isBlank()) {
            return baseNodeIdFromTargetId(target);
        }
        ControllerDirectTargetReference directTarget =
                ControllerDirectTargetReference.fromTag(data.getCompound("DirectTarget"));
        if (directTarget != null && directTarget.isBound()) {
            return baseNodeIdFromTargetId(directTarget.targetId());
        }
        ControllerDirectTargetReference inputTarget =
                ControllerDirectTargetReference.fromTag(data.getCompound("InputTarget"));
        if (inputTarget != null && inputTarget.isBound()) {
            return baseNodeIdFromTargetId(inputTarget.targetId());
        }
        return "";
    }

    // Rewrite the graph node target data
    private static boolean rewriteGraphNodeTargetData(CompoundTag data,
                                                     String previousBaseNodeId,
                                                     ControllerDiscoveryNode replacement,
                                                     int faceQuarterTurns) {
        boolean changed = false;
        String replacementLabel = replacement.label() == null || replacement.label().isBlank()
                ? replacement.nodeId()
                : replacement.label();
        if (!Objects.equals(data.getString("Target"), replacement.nodeId())) {
            data.putString("Target", replacement.nodeId());
            changed = true;
        }
        if (!Objects.equals(data.getString("TargetLabel"), replacementLabel)) {
            data.putString("TargetLabel", replacementLabel);
            changed = true;
        }
        CompoundTag replacementTag = replacement.toTag();
        if (!Objects.equals(data.getCompound("TargetData"), replacementTag)) {
            data.put("TargetData", replacementTag);
            changed = true;
        }
        changed |= rewriteEmbeddedTargetReference(
                data, "DirectTarget", previousBaseNodeId, replacement, faceQuarterTurns);
        changed |= rewriteEmbeddedTargetReference(
                data, "InputTarget", previousBaseNodeId, replacement, faceQuarterTurns);
        return changed;
    }

    // Rewrite the embedded target reference
    private static boolean rewriteEmbeddedTargetReference(CompoundTag data,
                                                         String key,
                                                         String previousBaseNodeId,
                                                         ControllerDiscoveryNode replacement,
                                                         int faceQuarterTurns) {
        if (data == null || !data.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(data.getCompound(key));
        if (reference == null || !reference.isBound()) {
            return false;
        }
        String currentBaseNodeId = baseNodeIdFromTargetId(reference.targetId());
        if (!Objects.equals(currentBaseNodeId, previousBaseNodeId)) {
            return false;
        }
        String nextTargetId = replaceBaseNodeId(reference.targetId(), replacement.nodeId());
        String nextLabel = reference.label();
        Direction selectedFace = resolveFaceFromDirectTarget(reference);
        if (selectedFace != null && Math.floorMod(faceQuarterTurns, 4) != 0) {
            Direction rotatedFace = ContraptionNetworkLinkerPlaneBlock.rotateDirection(
                    selectedFace, faceQuarterTurns);
            ControllerDirectTargetReference rotated = directTargetForFace(replacement, rotatedFace);
            if (rotated != null) {
                nextTargetId = rotated.targetId();
                nextLabel = rotated.label();
            }
        }
        String nextGroupId = replacement.subLevelId() == null ? "world" : "sublevel:" + replacement.subLevelId();
        ControllerDirectTargetReference next = new ControllerDirectTargetReference(
                nextTargetId,
                reference.targetTypeId(),
                nextGroupId,
                nextLabel,
                reference.compatModeId(),
                replacement.subLevelId(),
                replacement.blockPos());
        CompoundTag nextTag = next.toTag();
        if (Objects.equals(data.getCompound(key), nextTag)) {
            return false;
        }
        data.put(key, nextTag);
        return true;
    }

    // Rewrite the binding target references
    private static void rewriteBindingTargetReferences(
            CompoundTag root,
            Map<String, ControllerDiscoveryNode> replacementsByNodeId,
            Set<String> removedNodeIds,
            Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (root.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            CompoundTag bindings = root.getCompound(TAG_CHANNEL_BINDS).copy();
            for (String channelId : new ArrayList<>(bindings.getAllKeys())) {
                CompoundTag binding = bindings.getCompound(channelId).copy();
                rewriteBindingTargetReference(binding, TAG_BIND_DIRECT, replacementsByNodeId,
                        removedNodeIds, faceQuarterTurnsByNodeId);
                rewriteBindingTargetReference(binding, TAG_BIND_INPUT, replacementsByNodeId,
                        removedNodeIds, faceQuarterTurnsByNodeId);
                if (binding.contains(TAG_BIND_DIRECT, Tag.TAG_COMPOUND)
                        || binding.contains(TAG_BIND_INPUT, Tag.TAG_COMPOUND)) {
                    bindings.put(channelId, binding);
                } else {
                    bindings.remove(channelId);
                }
            }
            if (bindings.isEmpty()) {
                root.remove(TAG_CHANNEL_BINDS);
            } else {
                root.put(TAG_CHANNEL_BINDS, bindings);
            }
        }

        if (root.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)) {
            ListTag entries = root.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND);
            ListTag rewrittenEntries = new ListTag();
            for (int idx = 0; idx < entries.size(); idx++) {
                CompoundTag entry = entries.getCompound(idx).copy();
                rewriteBindingTargetReference(entry, TAG_BIND_DIRECT, replacementsByNodeId,
                        removedNodeIds, faceQuarterTurnsByNodeId);
                rewriteBindingTargetReference(entry, TAG_BIND_INPUT, replacementsByNodeId,
                        removedNodeIds, faceQuarterTurnsByNodeId);
                rewrittenEntries.add(entry);
            }
            root.put(TAG_CUSTOM_ENTRY_BINDS, rewrittenEntries);
        }
    }

    // Rewrite the binding target reference
    private static void rewriteBindingTargetReference(
            CompoundTag owner,
            String key,
            Map<String, ControllerDiscoveryNode> replacementsByNodeId,
            Set<String> removedNodeIds,
            Map<String, Integer> faceQuarterTurnsByNodeId) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(owner.getCompound(key));
        if (reference == null || !reference.isBound()) {
            return;
        }
        String baseNodeId = baseNodeIdFromTargetId(reference.targetId());
        if (removedNodeIds.contains(baseNodeId)) {
            owner.remove(key);
            return;
        }
        ControllerDiscoveryNode replacement = replacementsByNodeId.get(baseNodeId);
        if (replacement != null) {
            rewriteEmbeddedTargetReference(owner, key, baseNodeId, replacement,
                    faceQuarterTurnsByNodeId.getOrDefault(baseNodeId, 0));
        }
    }

    // Remove the binding references
    private static void removeBindingReferences(CompoundTag root, Set<String> removedNodeIds) {
        if (root == null || removedNodeIds == null || removedNodeIds.isEmpty()) {
            return;
        }
        if (root.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            CompoundTag binds = root.getCompound(TAG_CHANNEL_BINDS).copy();
            for (String key : new ArrayList<>(binds.getAllKeys())) {
                CompoundTag bind = binds.getCompound(key).copy();
                if (targetReferenceMatchesRemovedNode(bind.getCompound(TAG_BIND_DIRECT), removedNodeIds)) {
                    bind.remove(TAG_BIND_DIRECT);
                }
                if (targetReferenceMatchesRemovedNode(bind.getCompound(TAG_BIND_INPUT), removedNodeIds)) {
                    bind.remove(TAG_BIND_INPUT);
                }
                if (!bind.contains(TAG_BIND_DIRECT, Tag.TAG_COMPOUND)
                        && !bind.contains(TAG_BIND_INPUT, Tag.TAG_COMPOUND)) {
                    binds.remove(key);
                } else {
                    binds.put(key, bind);
                }
            }
            if (binds.isEmpty()) {
                root.remove(TAG_CHANNEL_BINDS);
            } else {
                root.put(TAG_CHANNEL_BINDS, binds);
            }
        }

        if (root.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)) {
            ListTag entries = root.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND);
            ListTag rewrittenEntries = new ListTag();
            for (int idx = 0; idx < entries.size(); idx++) {
                CompoundTag entry = entries.getCompound(idx).copy();
                if (targetReferenceMatchesRemovedNode(entry.getCompound(TAG_BIND_DIRECT), removedNodeIds)) {
                    entry.remove(TAG_BIND_DIRECT);
                }
                if (targetReferenceMatchesRemovedNode(entry.getCompound(TAG_BIND_INPUT), removedNodeIds)) {
                    entry.remove(TAG_BIND_INPUT);
                }
                rewrittenEntries.add(entry);
            }
            if (rewrittenEntries.isEmpty()) {
                root.remove(TAG_CUSTOM_ENTRY_BINDS);
            } else {
                root.put(TAG_CUSTOM_ENTRY_BINDS, rewrittenEntries);
            }
        }
    }

    // Check if the target references a removed node
    private static boolean targetReferenceMatchesRemovedNode(CompoundTag referenceTag, Set<String> removedNodeIds) {
        ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(referenceTag);
        return reference != null
                && reference.isBound()
                && removedNodeIds.contains(baseNodeIdFromTargetId(reference.targetId()));
    }

    // Clear the linked block fields
    private static void clearLinkedBlockFields(CompoundTag data) {
        data.remove("Target");
        data.remove("TargetLabel");
        data.remove("TargetData");
        data.remove("DirectTarget");
        data.remove("InputTarget");
        data.remove("BlockPos");
        data.remove("SubLevelId");
        if (data.contains("Defaults", Tag.TAG_COMPOUND)) {
            CompoundTag defaults = data.getCompound("Defaults");
            List<String> keys = new ArrayList<>(defaults.getAllKeys());
            for (String key : keys) {
                CompoundTag val = defaults.getCompound(key);
                if ("target".equals(key) || "target".equals(val.getString("Type"))) {
                    defaults.remove(key);
                }
            }
            if (defaults.isEmpty()) {
                data.remove("Defaults");
            } else {
                data.put("Defaults", defaults);
            }
        }
    }

    // Get the graph id
    private static String graphIdFor(String name, CompoundTag graphTag) {
        String seed = normalize(name) + "\u0000" + (graphTag == null ? "{}" : graphTag.toString());
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    // Sanitize the bound target
    private static @Nullable ControllerDirectTargetReference sanitizeBoundTarget(
            @Nullable ControllerDirectTargetReference target) {
        if (target == null || !target.isBound()) {
            return null;
        }
        return target;
    }

    // Find the target index
    private static int findTargetIndex(List<LinkedTarget> targets,
                                       BlockPos pos,
                                       @Nullable UUID subLevelId,
                                       LinkMode mode,
                                       TargetScope scope) {
        for (int idx = 0; idx < targets.size(); idx++) {
            LinkedTarget target = targets.get(idx);
            if (Objects.equals(target.subLevelId(), subLevelId)
                    && Objects.equals(target.blockPos(), pos)
                    && target.mode() == mode
                    && target.scope() == scope) {
                return idx;
            }
        }
        return -1;
    }

    // Find the face index
    private static int findFaceIndex(List<LinkedFace> faces, Direction dir) {
        for (int idx = 0; idx < faces.size(); idx++) {
            if (faces.get(idx).face() == dir) {
                return idx;
            }
        }
        return -1;
    }

    // Add the face to target
    private static void addFaceToTarget(List<LinkedTarget> targets,
                                        BlockPos pos,
                                        @Nullable UUID subLevelId,
                                        String blockId,
                                        String blockLabel,
                                        Direction face,
                                        LinkMode mode,
                                        TargetScope scope,
                                        @Nullable String signalKey) {
        int targetIndex = findTargetIndex(targets, pos, subLevelId, mode, scope);
        String resolvedSignalKey = normalize(signalKey).toLowerCase(Locale.ROOT);
        if (targetIndex < 0) {
            targets.add(new LinkedTarget(pos, subLevelId, blockId, blockLabel, mode,
                    scope, List.of(new LinkedFace(face, defaultFaceLabel(face), resolvedSignalKey))));
            return;
        }

        LinkedTarget existing = targets.get(targetIndex);
        List<LinkedFace> faces = new ArrayList<>(existing.faces());
        int existingFaceIndex = findFaceIndex(faces, face);
        if (existingFaceIndex >= 0) {
            LinkedFace existingFace = faces.get(existingFaceIndex);
            if (resolvedSignalKey.isBlank() || resolvedSignalKey.equals(existingFace.signalKey())) {
                return;
            }
            faces.set(existingFaceIndex,
                    new LinkedFace(existingFace.face(), existingFace.label(), resolvedSignalKey));
            targets.set(targetIndex, new LinkedTarget(existing.blockPos(), existing.subLevelId(), existing.blockId(),
                    existing.label(), existing.mode(), existing.scope(), faces));
            return;
        }
        faces.add(new LinkedFace(face, defaultFaceLabel(face), resolvedSignalKey));
        targets.set(targetIndex, new LinkedTarget(existing.blockPos(), existing.subLevelId(), existing.blockId(),
                existing.label(), existing.mode(), existing.scope(), faces));
    }

    // Add the block target
    private static void addBlockTarget(List<LinkedTarget> targets,
                                       BlockPos pos,
                                       @Nullable UUID subLevelId,
                                       String blockId,
                                       String blockLabel,
                                       LinkMode mode,
                                       TargetScope scope) {
        int targetIndex = findTargetIndex(targets, pos, subLevelId, mode, scope);
        if (targetIndex >= 0) {
            return;
        }
        targets.add(new LinkedTarget(pos, subLevelId, blockId, blockLabel, mode, scope, List.of()));
    }

    // Remove the face from target
    private static void removeFaceFromTarget(List<LinkedTarget> targets, int targetIndex, Direction face) {
        if (targetIndex < 0 || targetIndex >= targets.size()) {
            return;
        }
        LinkedTarget existing = targets.get(targetIndex);
        List<LinkedFace> faces = new ArrayList<>(existing.faces());
        int faceIndex = findFaceIndex(faces, face);
        if (faceIndex < 0) {
            return;
        }
        faces.remove(faceIndex);
        if (faces.isEmpty()) {
            targets.remove(targetIndex);
            return;
        }
        targets.set(targetIndex, new LinkedTarget(existing.blockPos(), existing.subLevelId(), existing.blockId(),
                existing.label(), existing.mode(), existing.scope(), faces));
    }

    // Remove the block target
    private static void removeBlockTarget(List<LinkedTarget> targets, int targetIndex) {
        if (targetIndex < 0 || targetIndex >= targets.size()) {
            return;
        }
        targets.remove(targetIndex);
    }

    // Sort the targets
    private static List<LinkedTarget> sortTargets(List<LinkedTarget> targets) {
        List<LinkedTarget> copy = new ArrayList<>(targets);
        copy.sort(Comparator
                .comparing((LinkedTarget target) -> target.subLevelId() == null ? "" : target.subLevelId().toString())
                .thenComparing(target -> target.blockPos().asLong())
                .thenComparing(target -> target.mode().id())
                .thenComparing(target -> target.label().toLowerCase(Locale.ROOT)));
        return copy;
    }

    // Sort the faces
    private static List<LinkedFace> sortFaces(List<LinkedFace> faces) {
        List<LinkedFace> copy = new ArrayList<>(faces);
        copy.sort(Comparator.comparingInt(face -> face.face().ordinal()));
        return copy;
    }

    // Build the node id
    private static String buildNodeId(BlockPos blockPos,
                                      @Nullable UUID subLevelId,
                                      List<LinkedFace> faces,
                                      LinkMode mode,
                                      TargetScope scope) {
        List<String> faceNames = new ArrayList<>();
        for (LinkedFace face : faces) {
            String token = face.face().getSerializedName();
            if (face.signalKey() != null && !face.signalKey().isBlank()) {
                token = token + "=" + face.signalKey();
            }
            faceNames.add(token);
        }
        faceNames.sort(String::compareTo);
        String subPart = subLevelId == null ? "world" : subLevelId.toString();
        if (!scope.usesFaces()) {
            return NODE_PREFIX + ":" + mode.id() + "@" + blockPos.asLong() + "#" + subPart + NODE_BLOCK_SUFFIX;
        }
        return NODE_PREFIX + ":" + mode.id() + "@" + blockPos.asLong() + "#" + subPart
                + NODE_FACES_PREFIX + String.join(",", faceNames);
    }

    // Parse the scope from node id
    private static TargetScope parseScopeFromNodeId(String nodeId) {
        if (nodeId != null && nodeId.contains(NODE_BLOCK_SUFFIX)) {
            return TargetScope.BLOCK;
        }
        return TargetScope.FACE;
    }

    // Parse the face signal keys from node id
    private static Map<Direction, String> parseFaceSignalKeysFromNodeId(String nodeId) {
        if (nodeId == null) {
            return Map.of();
        }
        int faceIndex = nodeId.indexOf(NODE_FACES_PREFIX);
        if (faceIndex < 0) {
            return Map.of();
        }
        String facesPart = nodeId.substring(faceIndex + NODE_FACES_PREFIX.length());
        if (facesPart.isBlank()) {
            return Map.of();
        }
        Map<Direction, String> directions = new EnumMap<>(Direction.class);
        for (String token : facesPart.split(",")) {
            String faceToken = token == null ? "" : token.trim();
            if (faceToken.isBlank()) {
                continue;
            }
            String[] split = faceToken.split("=", 2);
            Direction dir = Direction.byName(split[0].trim());
            if (dir != null) {
                String signalKey = split.length > 1 ? normalize(split[1]).toLowerCase(Locale.ROOT) : "";
                directions.put(dir, signalKey);
            }
        }
        return directions;
    }

    // Get the fallback label
    private static String fallbackLabel(String blockId) {
        try {
            ResourceLocation id = ResourceLocation.parse(blockId);
            String[] pieces = id.getPath().split("_");
            StringBuilder builder = new StringBuilder();
            for (String piece : pieces) {
                if (piece.isBlank()) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(piece.charAt(0)));
                if (piece.length() > 1) {
                    builder.append(piece.substring(1));
                }
            }
            return builder.isEmpty() ? "Target" : builder.toString();
        } catch (Exception ignored) {
            return "Target";
        }
    }

    // Create the default face label
    private static String defaultFaceLabel(Direction dir) {
        String val = dir.getSerializedName();
        return Character.toUpperCase(val.charAt(0)) + val.substring(1);
    }

    // Check if this is a forced block scope block
    private static boolean isForcedBlockScopeBlock(@Nullable BlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return isDefaultBlockScopeBlockId(id == null ? "" : id.toString());
    }

    // Check if this is a default block scope block ID
    private static boolean isDefaultBlockScopeBlockId(@Nullable String blockId) {
        if (blockId == null) {
            return false;
        }
        String normalized = blockId.trim();
        return FORCED_BLOCK_SCOPE_GEARSHIFT.equalsIgnoreCase(normalized)
                || FORCED_BLOCK_SCOPE_ANALOGUE_LEVER.equalsIgnoreCase(normalized)
                || FORCED_BLOCK_SCOPE_THROTTLE_LEVER.equalsIgnoreCase(normalized)
                || FORCED_BLOCK_SCOPE_STEPPED_LEVER.equalsIgnoreCase(normalized)
                || CREATE_THRUSTERS_ANALOGUE_JOYSTICK.equalsIgnoreCase(normalized)
                || CREATE_THRUSTERS_THRUSTER_BEARING.equalsIgnoreCase(normalized)
                || AEROWORKS_JOYSTICK.equalsIgnoreCase(normalized)
                || AEROWORKS_CONTROL_DESK.equalsIgnoreCase(normalized)
                || AEROWORKS_STEPPER_SERVO.equalsIgnoreCase(normalized)
                || CREATE_THRUSTERS_AILERON_BEARING.equalsIgnoreCase(normalized)
                || CREATE_THRUSTERS_VECTOR_BEARING.equalsIgnoreCase(normalized)
                || CREATE_THRUSTERS_RCS_THRUSTER.equalsIgnoreCase(normalized);
    }

    // Check if this is a double button block ID
    private static boolean isDoubleButtonBlockId(@Nullable String blockId) {
        return CREATE_THRUSTERS_DOUBLE_BUTTON.equalsIgnoreCase(normalize(blockId))
                || CREATE_THRUSTERS_COPYCAT_DOUBLE_BUTTON.equalsIgnoreCase(normalize(blockId));
    }

    // Check if this is a bearing block ID
    private static boolean isBearingBlockId(@Nullable String blockId) {
        String normalized = normalize(blockId).toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return path.contains("bearing");
    }

    private static final Set<String> DIRECTIONAL_TOKENS = Set.of(
            "north", "south", "east", "west", "up", "down",
            "left", "right", "front", "back", "forward", "rear", "side"
    );

    private static final Set<String> SIGNAL_TOKENS = Set.of(
            "powered", "lit", "signal", "active", "triggered", "on",
            "energized", "enabled", "input", "output", "power"
    );

    // Check if this has directional redstone capability
    private static boolean hasDirectionalRedstoneCapability(@Nullable Level level,
                                                            @Nullable BlockPos pos,
                                                            BlockState state,
                                                            @Nullable Direction clickedFace) {
        Block block = state.getBlock();

        if (block instanceof ComparatorBlock || block instanceof RepeaterBlock || block instanceof ObserverBlock
                || block instanceof DiodeBlock) {
            return true;
        }

        if (hasDynamicDirectionalSignalProp(state)) {
            return true;
        }

        if (hasBooleanProperty(state, "powered") || hasBooleanProperty(state, "lit")) {
            return false;
        }

        if (hasOrientationProperty(state) && (state.isSignalSource() || state.hasAnalogOutputSignal())) {
            return true;
        }

        if (level != null && pos != null) {
            int first = -1;
            boolean varied = false;
            for (Direction dir : Direction.values()) {
                int signal = state.getSignal(level, pos, dir);
                if (first < 0) {
                    first = signal;
                } else if (first != signal) {
                    varied = true;
                    break;
                }
            }
            if (varied) {
                return true;
            }
        }

        return false;
    }

    // Check if this has dynamic directional signal prop
    private static boolean hasDynamicDirectionalSignalProp(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof BooleanProperty || property instanceof IntegerProperty)) continue;
            String name = property.getName().toLowerCase(Locale.ROOT);
            String[] parts = name.split("_");
            boolean hasDir = false;
            boolean hasSig = false;
            for (String part : parts) {
                if (DIRECTIONAL_TOKENS.contains(part)) hasDir = true;
                if (SIGNAL_TOKENS.contains(part)) hasSig = true;
            }
            if (hasDir && hasSig) return true;
        }
        return false;
    }

    // Resolve the directional signal prop
    private static @Nullable String resolveDirectionalSignalProp(BlockState state, Direction clickedFace) {
        // -----------------------------------------------------TARGET CHECK-----------------------------------------------------
        if (state == null || clickedFace == null) {
            return null;
        }

        // -----------------------------------------------------FACE TOKENS-----------------------------------------------------
        Set<String> absoluteTokens = switch (clickedFace) {
            case NORTH -> Set.of("north", "n");
            case SOUTH -> Set.of("south", "s");
            case EAST -> Set.of("east", "e");
            case WEST -> Set.of("west", "w");
            case UP -> Set.of("up", "top", "upper");
            case DOWN -> Set.of("down", "bottom", "lower");
        };

        Set<String> relativeTokens = relativeTokensForFace(state, clickedFace);
        String bestName = null;
        int bestScore = Integer.MIN_VALUE;
        List<String> candidateDebug = new ArrayList<>();

        // ------------------------------------PROPERTY RANKING------------------------------------
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof BooleanProperty || property instanceof IntegerProperty)) {
                continue;
            }

            String normalized = property.getName().toLowerCase(Locale.ROOT);
            String[] parts = normalized.split("_");
            Set<String> partSet = new java.util.LinkedHashSet<>();
            Collections.addAll(partSet, parts);

            boolean hasSignal = false;
            for (String part : partSet) {
                if (SIGNAL_TOKENS.contains(part)) {
                    hasSignal = true;
                    break;
                }
            }
            if (!hasSignal) {
                continue;
            }

            int score = 0;
            boolean matchedDirection = false;
            boolean hasDirectionalToken = false;
            for (String part : partSet) {
                if (absoluteTokens.contains(part)) {
                    score += 100;
                    matchedDirection = true;
                    hasDirectionalToken = true;
                }
                if (relativeTokens.contains(part)) {
                    score += 80;
                    matchedDirection = true;
                    hasDirectionalToken = true;
                }
                if (DIRECTIONAL_TOKENS.contains(part)) {
                    score += 5;
                    hasDirectionalToken = true;
                }
            }

            if (!hasDirectionalToken) {
                continue;
            }

            if (!matchedDirection) {

                score += 1;
            }

            if (property instanceof IntegerProperty) {
                score += 10;
            }
            if (normalized.contains("power") || normalized.contains("signal")) {
                score += 8;
            }

            if (score > bestScore) {
                bestScore = score;
                bestName = normalized;
            }
            if (DEBUG_LINKER_FACE_IO) {
                candidateDebug.add(normalized + " score=" + score + " matchDir=" + matchedDirection);
            }
        }

        // -----------------------------------------------------DEBUG RESULT-----------------------------------------------------
        if (DEBUG_LINKER_FACE_IO) {
            Create.LOGGER.info("[CT-LinkerFaceIO] bind-score face={} best={} score={} candidates={}",
                    clickedFace,
                    bestName,
                    bestScore == Integer.MIN_VALUE ? null : bestScore,
                    candidateDebug);
        }

        return bestName;
    }

    // Describe the signal props
    private static List<String> describeSignalProps(BlockState state) {
        List<String> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            String name = property.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("power") || lower.contains("signal") || lower.contains("lit")
                    || lower.contains("active") || lower.contains("open") || lower.contains("enable")) {
                properties.add(name + ":" + property.getClass().getSimpleName());
            }
        }
        return properties;
    }

    // Get the relative tokens for face
    private static Set<String> relativeTokensForFace(BlockState state, Direction clickedFace) {
        Direction facing = resolveFacingDirection(state);
        if (facing == null || facing.getAxis().isVertical()) {
            return Set.of();
        }

        Direction up = Direction.UP;
        Direction down = Direction.DOWN;
        Direction right = facing.getClockWise();
        Direction left = right.getOpposite();
        Direction back = facing.getOpposite();

        if (clickedFace == facing) {
            return Set.of("front", "forward", "fwd");
        }
        if (clickedFace == back) {
            return Set.of("back", "backward", "rear", "reverse");
        }
        if (clickedFace == right) {
            return Set.of("right");
        }
        if (clickedFace == left) {
            return Set.of("left");
        }
        if (clickedFace == up) {
            return Set.of("up", "top", "upper");
        }
        if (clickedFace == down) {
            return Set.of("down", "bottom", "lower");
        }
        return Set.of();
    }

    // Resolve the facing direction
    @SuppressWarnings("unchecked")
    private static @Nullable Direction resolveFacingDirection(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            String name = property.getName();
            if (!"facing".equals(name) && !"horizontal_facing".equals(name)) {
                continue;
            }
            if (property.getValueClass() != Direction.class) {
                continue;
            }
            try {
                Property<Direction> directionProperty = (Property<Direction>) property;
                return state.getValue(directionProperty);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    // Check if this has orientation property
    private static boolean hasOrientationProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            String name = property.getName();
            if ("facing".equals(name) || "horizontal_facing".equals(name) || "axis".equals(name)) {
                return true;
            }
        }
        return false;
    }

    // Check if this has boolean property
    private static boolean hasBooleanProperty(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    // Get the client root tag
    private static @Nullable CompoundTag clientRootTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag stackTag = stackCustomDataTagView(stack);
        CompoundTag snapshot = validClientSnapshot(stackTag);
        if (snapshot != null) {
            return snapshot;
        }
        return stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND) ? stackTag.getCompound(TAG_ROOT) : null;
    }

    // Write the client snapshot
    static void writeClientSnapshot(CompoundTag stackTag,
                                    @Nullable CompoundTag root,
                                    ControllerManifestStore.ManifestSnapshot metadata) {
        if (stackTag == null || metadata == null) {
            return;
        }
        CompoundTag src = root == null ? new CompoundTag() : root;
        CompoundTag snapshot = new CompoundTag();
        if (src.hasUUID(TAG_LINKER_ID)) {
            snapshot.putUUID(TAG_LINKER_ID, src.getUUID(TAG_LINKER_ID));
        }
        snapshot.putString(TAG_MODE, LinkMode.byId(src.getString(TAG_MODE)).id());
        snapshot.putString(TAG_TARGET_MODE, TargetMode.byId(src.getString(TAG_TARGET_MODE)).id());
        snapshot.put(TAG_TARGETS, src.getList(TAG_TARGETS, Tag.TAG_COMPOUND).copy());
        if (src.contains(TAG_SELECTED_GRAPH_ID, Tag.TAG_STRING)) {
            snapshot.putString(TAG_SELECTED_GRAPH_ID, src.getString(TAG_SELECTED_GRAPH_ID));
        }
        if (src.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)) {
            snapshot.put(TAG_CHANNEL_BINDS, src.getCompound(TAG_CHANNEL_BINDS).copy());
        }
        if (src.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)) {
            snapshot.put(TAG_CUSTOM_ENTRY_BINDS,
                    src.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND).copy());
        }
        snapshot.putBoolean(TAG_CLIENT_HAS_BINDINGS, hasBindings(src));
        snapshot.putString(TAG_CLIENT_MANIFEST_ID, metadata.id());
        snapshot.putInt(TAG_CLIENT_MANIFEST_REVISION, metadata.revision());
        snapshot.putString(TAG_CLIENT_MANIFEST_HASH, metadata.hash());
        synchronized (CLIENT_SNAPSHOT_CACHE) {
            CLIENT_SNAPSHOT_CACHE.put(metadata.id(), snapshot.copy());
        }
        stackTag.remove(TAG_CLIENT_SNAPSHOT);
    }

    // Get the valid client snapshot
    static @Nullable CompoundTag validClientSnapshot(CompoundTag stackTag) {
        CompoundTag snapshot = currentClientSnapshot(stackTag);
        return snapshot == null ? null : snapshot.copy();
    }

    // Get the current client snapshot
    private static @Nullable CompoundTag currentClientSnapshot(CompoundTag stackTag) {
        if (stackTag == null || !ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            return null;
        }
        String manifestId = ControllerManifestStore.linkerManifestId(stackTag);
        CompoundTag snapshot = null;
        if (stackTag.contains(TAG_CLIENT_SNAPSHOT, Tag.TAG_COMPOUND)) {
            snapshot = stackTag.getCompound(TAG_CLIENT_SNAPSHOT);
        } else {
            synchronized (CLIENT_SNAPSHOT_CACHE) {
                snapshot = CLIENT_SNAPSHOT_CACHE.get(manifestId);
            }
        }
        if (snapshot == null) {
            return null;
        }
        if (!snapshot.contains(TAG_CLIENT_MANIFEST_ID, Tag.TAG_STRING)
                || !snapshot.contains(TAG_CLIENT_MANIFEST_REVISION, Tag.TAG_INT)
                || !snapshot.contains(TAG_CLIENT_MANIFEST_HASH, Tag.TAG_STRING)) {
            return null;
        }
        if (!Objects.equals(snapshot.getString(TAG_CLIENT_MANIFEST_ID), manifestId)) {
            return null;
        }
        if (stackTag.contains(ControllerManifestStore.TAG_LINKER_MANIFEST_REVISION, Tag.TAG_INT)
                && snapshot.getInt(TAG_CLIENT_MANIFEST_REVISION)
                != ControllerManifestStore.linkerManifestRevision(stackTag)) {
            return null;
        }
        if (stackTag.contains(ControllerManifestStore.TAG_LINKER_MANIFEST_HASH, Tag.TAG_STRING)
                && !Objects.equals(snapshot.getString(TAG_CLIENT_MANIFEST_HASH),
                ControllerManifestStore.linkerManifestHash(stackTag))) {
            return null;
        }
        return snapshot;
    }

    // Check if this has bindings
    private static boolean hasBindings(CompoundTag root) {
        if (root == null) {
            return false;
        }
        if (root.contains(TAG_CHANNEL_BINDS, Tag.TAG_COMPOUND)
                && !root.getCompound(TAG_CHANNEL_BINDS).getAllKeys().isEmpty()) {
            return true;
        }
        return root.contains(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_LIST)
                && !root.getList(TAG_CUSTOM_ENTRY_BINDS, Tag.TAG_COMPOUND).isEmpty();
    }

    // Remove the client edit source
    private static void removeClientEditSource(CompoundTag root) {
        root.remove(TAG_CLIENT_EDIT_SOURCE_ID);
        root.remove(TAG_CLIENT_EDIT_SOURCE_REVISION);
        root.remove(TAG_CLIENT_EDIT_SOURCE_HASH);
    }

    // Install the client snapshot
    private static void installClientSnapshot(ItemStack stack, CompoundTag stackTag,
                                              ControllerManifestStore.ManifestSnapshot snapshot) {
        cleanLinkerStackTag(stackTag);
        ControllerManifestStore.writeLinkerMetadata(stackTag, snapshot);
        writeClientSnapshot(stackTag, snapshot.linkerData(), snapshot);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
    }

    // Get the root tag
    private static CompoundTag rootTag(ItemStack stack, boolean create) {
        if (stack == null || stack.isEmpty()) {
            return create ? new CompoundTag() : null;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return stackTag.getCompound(TAG_ROOT);
        }
        if (ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            CompoundTag cached = validClientSnapshot(stackTag);
            if (cached != null) {
                return cached;
            }
            ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.loadLinkerFromMetadata(stackTag);
            if (snapshot != null) {
                if (validClientSnapshot(stackTag) == null) {
                    writeClientSnapshot(stackTag, snapshot.linkerData(), snapshot);
                }
                return snapshot.linkerData();
            }
            return create ? new CompoundTag() : null;
        }
        if (!stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            return new CompoundTag();
        }
        return stackTag.getCompound(TAG_ROOT);
    }

    // Get the linker header
    private static @Nullable ControllerSqliteStore.LinkerHeader linkerHeader(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        if (stackTag.contains(TAG_ROOT, Tag.TAG_COMPOUND)
                || !ControllerManifestStore.hasLinkerMetadata(stackTag)) {
            return null;
        }
        return ControllerSqliteStore.loadLinkerHeader(ControllerManifestStore.linkerManifestId(stackTag));
    }

    // Write the root to stack
    private static void writeRootToStack(ItemStack stack, CompoundTag root) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag stackTag = stackCustomDataTag(stack);
        String manifestId = ControllerManifestStore.linkerManifestId(stackTag);
        int currentRevision = ControllerManifestStore.linkerManifestRevision(stackTag);
        if (currentRevision <= 0 && !manifestId.isBlank()) {
            currentRevision = ControllerSqliteStore.loadLinkerRevision(manifestId);
        }
        ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.saveLinker(
                manifestId,
                root == null ? new CompoundTag() : root,
                currentRevision);
        if (snapshot != null) {
            cleanLinkerStackTag(stackTag);
            ControllerManifestStore.writeLinkerMetadata(stackTag, snapshot);
            writeClientSnapshot(stackTag, root, snapshot);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stackTag));
        }
    }

    // Check if this has persisted linker payload
    private static boolean hasPersistedLinkerPayload(CompoundTag stackTag) {
        return stackTag.contains(TAG_ROOT)
                || stackTag.contains(TAG_CLIENT_SNAPSHOT)
                || stackTag.contains(ControllerManifestStore.TAG_LINKER_MANIFEST_REVISION)
                || stackTag.contains(ControllerManifestStore.TAG_LINKER_MANIFEST_HASH)
                || stackTag.contains(ControllerManifestStore.TAG_LINKER_MANIFEST_STORAGE_VERSION);
    }

    // Clean the linker stack tag
    private static void cleanLinkerStackTag(CompoundTag stackTag) {
        stackTag.remove(TAG_ROOT);
        stackTag.remove(TAG_CLIENT_SNAPSHOT);
        stackTag.remove(ControllerManifestStore.TAG_LINKER_MANIFEST_REVISION);
        stackTag.remove(ControllerManifestStore.TAG_LINKER_MANIFEST_HASH);
        stackTag.remove(ControllerManifestStore.TAG_LINKER_MANIFEST_STORAGE_VERSION);
    }

    // Get the stack custom data tag
    private static CompoundTag stackCustomDataTag(ItemStack stack) {
        return ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
    }

    // Get the stack custom data tag view
    @SuppressWarnings("deprecation")
    private static CompoundTag stackCustomDataTagView(ItemStack stack) {
        return ((CustomData) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe();
    }

    // Store the client snapshot key
    private record ClientSnapshotKey(String manifestId, int revision, String hash) {
    }

    // Normalize the contraption network linker data
    private static String normalize(String val) {
        return val == null ? "" : val.trim();
    }
}
