package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.lib.display.DisplayFrameEnvelope;
import com.rieno.gadgetsandgizmos.lib.display.DisplaySurfaceProjection;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplaySource;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplaySourceRegistry;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputOpenPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// Own one connected ACC display surface and the presentation data shared across its panels
public class AccDisplayBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int PIXELS_PER_BLOCK = 96;
    public static final int BLOCK_TEXTURE_PIXELS = 16;
    public static final int BORDER_BLOCK_PIXELS = 2;
    public static final int BORDER_PIXELS =
            PIXELS_PER_BLOCK * BORDER_BLOCK_PIXELS / BLOCK_TEXTURE_PIXELS;
    public static final int TASKBAR_PIXELS = 14;
    public static final String DISPLAY_MODE_AUTO = "auto";
    public static final List<String> DISPLAY_MODES = List.of(
            DISPLAY_MODE_AUTO, "acc_widgets", "acc_graph", "acc_plotter",
            "ship_information", "external", "display_link", "advanced_data_link");
    private static final int SOURCE_DISCOVERY_TICKS = 20;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current display frame
    private CompoundTag displayFrame = new CompoundTag();
    // Current controller sources
    private ListTag controllerSources = new ListTag();
    // Current ACC display controller
    private @Nullable AdvancedContraptionControllerBlockEntity controller;
    // Last frame fingerprint
    private int lastFrameFingerprint;
    // Last network graph revision
    private int lastNetworkGraphRevision = Integer.MIN_VALUE;
    // Last network frame
    private CompoundTag lastNetworkFrame = new CompoundTag();
    // Controls whether to force full graph sync
    private boolean forceFullGraphSync;
    // Cached graph controller
    private @Nullable AdvancedContraptionControllerBlockEntity cachedGraphController;
    // Cached graph revision
    private int cachedGraphRevision = Integer.MIN_VALUE;
    // Cached graph tag
    private CompoundTag cachedGraphTag = new CompoundTag();
    // Tracked display link lines
    private final List<String> displayLinkLines = new ArrayList<>();
    // Tracked advanced data link frames
    private final Map<BlockPos, CompoundTag> advancedDataLinkFrames = new LinkedHashMap<>();
    // Active source id
    private String activeSourceId = "";
    // Current configured display mode
    private String configuredDisplayMode = DISPLAY_MODE_AUTO;
    // Current ship information display mode
    private String shipInformationDisplayMode = ShipInformationDisplayModes.DEFAULT;
    // Tracks whether ship information mode is configured
    private boolean shipInformationModeConfigured;
    // Source before SCM initialization
    private String sourceBeforeScmInitialization = "";
    // Current SCM initialization frame
    private CompoundTag scmInitializationFrame = new CompoundTag();
    // Current mapped ship information frame
    private CompoundTag mappedShipInformationFrame = new CompoundTag();
    // Current parsed graph tag
    private @Nullable CompoundTag parsedGraphTag;
    // Current parsed display graph
    private AdvancedGraphDocument parsedDisplayGraph = new AdvancedGraphDocument();
    // Tracks whether network geometry is dirty
    private boolean networkGeometryDirty = true;
    // Cached network width
    private int cachedNetworkWidth = 1;
    // Cached network height
    private int cachedNetworkHeight = 1;
    // Display refresh queued
    private final AtomicBoolean displayRefreshQueued = new AtomicBoolean();
    // Current source discovery tick
    private int sourceDiscoveryTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display
    public AccDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.ACC_DISPLAY.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Rebuild and publish the frame
    private void rebuildAndPublishFrame() {
        if (level == null || level.isClientSide
                || AccDisplayControllerRegistry.isStopping(level) || !isNetworkRoot()) {
            return;
        }
        CompoundTag nextFrame = createDisplayFrame();
        boolean graphFrame = "graph".equals(nextFrame.getString("State"));
        boolean graphChanged = graphFrame && (!"graph".equals(displayFrame.getString("State"))
                || nextFrame.getInt("GraphRevision") != displayFrame.getInt("GraphRevision"));
        if (graphChanged) {
            forceFullGraphSync = true;
        }
        int fingerprint = nextFrame.hashCode();
        if (fingerprint == lastFrameFingerprint && nextFrame.equals(displayFrame)) {
            return;
        }
        displayFrame = nextFrame;
        lastFrameFingerprint = fingerprint;
        sendData();
    }

    // Get the display frame
    public CompoundTag displayFrame() {
        return displayFrame;
    }

    // Get the display mode
    public String displayMode() {
        AccDisplayBlockEntity root = networkRoot();
        return root == null ? shipInformationDisplayMode : root.shipInformationDisplayMode;
    }

    // Set the display mode
    public void setDisplayMode(String mode) {
        if (level == null || level.isClientSide) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        String normalized = normalizeDisplayMode(mode);
        root.shipInformationDisplayMode = normalized;
        root.shipInformationModeConfigured = true;
        root.configuredDisplayMode = "ship_information";
        root.activeSourceId = "";
        root.forceFullGraphSync = true;
        root.lastFrameFingerprint = Integer.MIN_VALUE;
        root.setChanged();
        root.copyDisplayModeToNetwork(normalized, true, "ship_information");
        root.rebuildAndPublishFrame();
    }

    // Request the display presentation
    public void requestDisplayPresentation(String mode) {
        if (level == null || level.isClientSide) return;
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        String sourceMode = normalizeDisplaySourceMode(mode);
        if (!"ship_information".equals(sourceMode)) return;
        root.configuredDisplayMode = sourceMode;
        root.activeSourceId = "";
        root.forceFullGraphSync = true;
        root.lastFrameFingerprint = Integer.MIN_VALUE;
        root.setChanged();
        root.copyDisplayModeToNetwork(root.shipInformationDisplayMode,
                root.shipInformationModeConfigured, sourceMode);
        root.rebuildAndPublishFrame();
    }

    // Set the display mode
    public void setDisplayMode(ServerPlayer player, String mode) {
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        if (player != null && root.playerIsNear(player)) {
            root.setDisplayMode(mode);
        }
    }

    // Copy the display mode to network
    private void copyDisplayModeToNetwork(String mode, boolean configured,
                                          String sourceMode) {
        refreshNetworkGeometry();
        for (int y = 0; y < cachedNetworkHeight; y++) {
            for (int x = 0; x < cachedNetworkWidth; x++) {
                BlockPos pos = worldPosition.below(y).relative(screenRight(), x);
                LevelChunk chunk = loadedChunk(pos);
                BlockEntity blockEntity = chunk == null ? null : chunk.getBlockEntity(pos);
                if (blockEntity instanceof AccDisplayBlockEntity display) {
                    display.shipInformationDisplayMode = mode;
                    display.shipInformationModeConfigured = configured;
                    display.configuredDisplayMode = normalizeDisplaySourceMode(sourceMode);
                    display.setChanged();
                    display.sendData();
                }
            }
        }
    }

    // Normalize the display mode
    public static String normalizeDisplayMode(String mode) {
        return ShipInformationDisplayModes.normalize(mode);
    }

    // Normalize the display source mode
    public static String normalizeDisplaySourceMode(String mode) {
        String normalized = mode == null ? "" : mode.strip().toLowerCase(java.util.Locale.ROOT);
        return DISPLAY_MODES.contains(normalized) ? normalized : DISPLAY_MODE_AUTO;
    }

    // Request the display refresh
    public void requestDisplayRefresh() {
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        root.rebuildAndPublishFrame();
    }

    // Queue the display refresh
    public void queueDisplayRefresh() {
        if (level == null || level.isClientSide || level.getServer() == null
                || AccDisplayControllerRegistry.isStopping(level)) {
            return;
        }
        displayRefreshQueued.set(true);
    }

    // Update the server
     public static void tickServer(Level level, BlockPos pos, BlockState state,
                                  AccDisplayBlockEntity display) {
        if(AccDisplayControllerRegistry.isStopping(level)) return;
        if(++display.sourceDiscoveryTicks >= SOURCE_DISCOVERY_TICKS){
            display.sourceDiscoveryTicks = 0;
            if(display.isNetworkRoot()) display.queueDisplayRefresh();

        }
        if(display.displayRefreshQueued.getAndSet(false)) display.requestDisplayRefresh();
    }
    // public static void tickServer(Level level, BlockPos pos, BlockState state,
    //                               AccDisplayBlockEntity display) {
    //     if (display.displayRefreshQueued.getAndSet(false)
    //             && !AccDisplayControllerRegistry.isStopping(level)) {
    //         display.requestDisplayRefresh();
    //     }
    // }

    // Receive the controller update
    public void receiveControllerUpdate(
            AdvancedContraptionControllerBlockEntity sender,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        if (level == null || level.isClientSide || sender == null || sender.isRemoved()) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        ListTag next = root.createControllerSources(sender,
                inputs == null ? Map.of() : inputs,
                outputs == null ? Map.of() : outputs);
        boolean controllerChanged = root.controller != sender;
        boolean graphChanged = controllerChanged
                || sourceGraphRevision(next) != sourceGraphRevision(root.controllerSources);
        if (!controllerChanged && next.equals(root.controllerSources)) {
            return;
        }
        root.controller = sender;
        root.controllerSources = next;
        root.forceFullGraphSync |= controllerChanged || graphChanged;
        root.rebuildAndPublishFrame();
    }

    // Release the controller
    public void releaseController(AdvancedContraptionControllerBlockEntity sender) {
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        if (root.controller != sender) {
            return;
        }
        root.controller = null;
        root.controllerSources = new ListTag();
        root.forceFullGraphSync = true;
        root.rebuildAndPublishFrame();
    }

    // Accept the ship initialization progress
    public void acceptShipInitializationProgress(
            boolean visible, int percent, String status
    ) {
        if (level == null || level.isClientSide) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        if (visible) {
            if (root.scmInitializationFrame.isEmpty()) {
                root.sourceBeforeScmInitialization = root.activeSourceId;
            }
            CompoundTag frame = new CompoundTag();
            frame.putString("State", "initializing");
            frame.putInt("Percent", Mth.clamp(percent, 0, 100));
            frame.putString("Status", status == null ? "" : status);
            root.scmInitializationFrame = frame;
            root.activeSourceId = "scm_initialization";
        } else {
            root.scmInitializationFrame = new CompoundTag();
            if ("scm_initialization".equals(root.activeSourceId)) {
                root.activeSourceId = root.sourceBeforeScmInitialization;
            }
            root.sourceBeforeScmInitialization = "";
        }
        root.requestDisplayRefresh();
    }

    // Accept the mapped ship information
    public void acceptMappedShipInformation(
            RailwayNavigatorGraphCompat.ShipDisplayData telemetry
    ) {
        if (level == null || level.isClientSide || telemetry == null) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        CompoundTag frame = new CompoundTag();
        frame.putString("State", "crn");
        frame.putString("Mode", "passenger_information/detailed_with_schedule");
        frame.put("CrnData", RailwayNavigatorGraphCompat.shipDisplayFrame(telemetry));
        if (frame.equals(root.mappedShipInformationFrame)) {
            return;
        }
        root.mappedShipInformationFrame = frame;
        root.requestDisplayRefresh();
    }

    // Clear the mapped ship information
    public void clearMappedShipInformation() {
        if (level == null || level.isClientSide) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        if (root.mappedShipInformationFrame.isEmpty()) {
            return;
        }
        root.mappedShipInformationFrame = new CompoundTag();
        root.requestDisplayRefresh();
    }

    // Get the graph
    public AdvancedGraphDocument graph() {
        CompoundTag graphTag = displayFrame.getCompound("Graph");
        if (graphTag != parsedGraphTag) {
            parsedGraphTag = graphTag;
            parsedDisplayGraph = AdvancedGraphDocument.fromTag(graphTag);
        }
        return parsedDisplayGraph;
    }

    // Get the value
    public AdvancedGraphDocument.Value value(String nodeId, String port) {
        CompoundTag values = displayFrame.getCompound("Values");
        CompoundTag val = values.getCompound(nodeId + ":" + port);
        AdvancedGraphDocument.Node node = null;
        if (val.isEmpty()) {
            node = graph().nodes().stream()
                    .filter(candidate -> candidate.id().equals(nodeId)).findFirst().orElse(null);
            if (node != null) {
                val = node.data().getCompound("Defaults").getCompound(port);
            }
        }
        if (val.isEmpty()) {
            if (node != null && "visible".equals(port)
                    && (node.type().startsWith("acc_display_")
                    || "acc_hologram_widget".equals(node.type())
                    || "advanced_hud_element".equals(node.type()))) {
                return AdvancedGraphDocument.Value.bool(true);
            }
            return AdvancedGraphDocument.Value.number(0.0D);
        }
        return new AdvancedGraphDocument.Value(
                val.getString("Type"), val.getCompound("Payload"));
    }

    // Accept the display link text
    public void acceptDisplayLinkText(int line, List<MutableComponent> text) {
        if (level == null || level.isClientSide) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) {
            root = this;
        }
        int firstLine = Math.max(0, line);
        int required = firstLine + (text == null ? 0 : text.size());
        while (root.displayLinkLines.size() < required) {
            root.displayLinkLines.add("");
        }
        if (text != null) {
            for (int idx = 0; idx < text.size(); idx++) {
                root.displayLinkLines.set(firstLine + idx, text.get(idx).getString());
            }
        }
        root.rebuildAndPublishFrame();
    }

    // Accept the advanced data link frame
    public void acceptAdvancedDataLinkFrame(BlockPos linkPos, CompoundTag frame) {
        if (level == null || level.isClientSide || linkPos == null || frame == null) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        CompoundTag next = frame.copy();
        CompoundTag prev = root.advancedDataLinkFrames.get(linkPos);
        if (next.equals(prev)) {
            return;
        }
        root.advancedDataLinkFrames.put(linkPos.immutable(), next);
        root.rebuildAndPublishFrame();
    }

    // Clear the advanced data link frame
    public void clearAdvancedDataLinkFrame(BlockPos linkPos) {
        if (linkPos == null) {
            return;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        if (root.advancedDataLinkFrames.remove(linkPos) != null) {
            root.rebuildAndPublishFrame();
        }
    }

    // Check if this is a network root
    public boolean isNetworkRoot() {
        if (!(getBlockState().getBlock() instanceof AccDisplayBlock)) {
            return false;
        }
        return !matchesDisplay(worldPosition.above())
                && !matchesDisplay(worldPosition.relative(screenLeft()));
    }

    // Get the network width
    public int networkWidth() {
        AccDisplayBlockEntity root = networkRoot();
        if (root != null && root != this) {
            return root.networkWidth();
        }
        refreshNetworkGeometry();
        return cachedNetworkWidth;
    }

    // Get the network height
    public int networkHeight() {
        AccDisplayBlockEntity root = networkRoot();
        if (root != null && root != this) {
            return root.networkHeight();
        }
        refreshNetworkGeometry();
        return cachedNetworkHeight;
    }

    // Refresh the network geometry
    private void refreshNetworkGeometry() {
        if (!networkGeometryDirty) {
            return;
        }
        int width = 1;
        while (width < 32 && matchesDisplay(worldPosition.relative(screenRight(), width))) {
            width++;
        }
        int height = 1;
        while (height < 32) {
            BlockPos row = worldPosition.below(height);
            boolean complete = true;
            for (int x = 0; x < width; x++) {
                if (!matchesDisplay(row.relative(screenRight(), x))) {
                    complete = false;
                    break;
                }
            }
            if (!complete) {
                break;
            }
            height++;
        }
        cachedNetworkWidth = width;
        cachedNetworkHeight = height;
        networkGeometryDirty = false;
    }

    // Handle a display topology change
    public static void displayTopologyChanged(Level level, BlockPos changedPos) {
        if (level == null || level.isClientSide || changedPos == null) {
            return;
        }
        java.util.Set<AccDisplayBlockEntity> roots =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Direction dir : Direction.values()) {
            collectAffectedRoot(level.getBlockEntity(changedPos.relative(dir)), roots);
        }
        collectAffectedRoot(level.getBlockEntity(changedPos), roots);
        for (AccDisplayBlockEntity root : roots) {
            root.networkGeometryDirty = true;
            root.rebuildAndPublishFrame();
            root.notifyAdjacentAdvDataLinks();
        }
        AccDisplayControllerRegistry.markDisplayTargetsDirty(level);
    }

    // Collect the affected root
    private static void collectAffectedRoot(
            @Nullable BlockEntity candidate, java.util.Set<AccDisplayBlockEntity> roots) {
        if (!(candidate instanceof AccDisplayBlockEntity display)) {
            return;
        }
        display.networkGeometryDirty = true;
        AccDisplayBlockEntity root = display.networkRoot();
        roots.add(root == null ? display : root);
    }

    // Notify the adjacent adv data links
    private void notifyAdjacentAdvDataLinks() {
        if (level == null) {
            return;
        }
        refreshNetworkGeometry();
        for (int y = 0; y < cachedNetworkHeight; y++) {
            for (int x = 0; x < cachedNetworkWidth; x++) {
                BlockPos displayPos = worldPosition.below(y).relative(screenRight(), x);
                for (Direction dir : Direction.values()) {
                    BlockEntity candidate = level.getBlockEntity(displayPos.relative(dir));
                    if (candidate instanceof GyroscopeLinkBlockEntity link) {
                        link.refreshDisplayRelayTarget();
                    }
                }
            }
        }
    }

    // Get the screen right
    public net.minecraft.core.Direction screenRight() {
        return getBlockState().getValue(AccDisplayBlock.FACING).getCounterClockWise();
    }

    // Get the network root
    public @Nullable AccDisplayBlockEntity networkRoot() {
        if (level == null || !(getBlockState().getBlock() instanceof AccDisplayBlock)) {
            return null;
        }
        BlockPos root = worldPosition;
        for (int distance = 0; distance < 31 && matchesDisplay(root.above()); distance++) {
            root = root.above();
        }
        for (int distance = 0; distance < 31
                && matchesDisplay(root.relative(screenLeft())); distance++) {
            root = root.relative(screenLeft());
        }
        LevelChunk chunk = loadedChunk(root);
        BlockEntity blockEntity = chunk == null ? null : chunk.getBlockEntity(root);
        return blockEntity instanceof AccDisplayBlockEntity display ? display : null;
    }

    // Handle the ACC display
    public boolean interact(Player player, BlockHitResult hit, int mouseButton) {
        // ------------------------------------INTERACTION CHECKS------------------------------------
        if (!(player instanceof ServerPlayer serverPlayer) || level == null) {
            return false;
        }
        AccDisplayBlockEntity root = networkRoot();
        if (root == null || !root.playerIsNear(serverPlayer)) {
            return false;
        }
        ScreenPoint point = root.screenPoint(this, hit);
        if (root.selectSource(point)) {
            return true;
        }
        point = root.contentPoint(point);
        if (point.x() < 0.0D || point.y() < 0.0D) return false;
        // ------------------------------------DISPLAY SOURCES------------------------------------
        String src = root.displayFrame.getString("ActiveSource");
        if ("adapter".equals(src) || "adapter:ship_information".equals(src)) {
            BlockEntity adapter = root.adjacentAdapter();
            return adapter instanceof UniversalDisplayAdapterBlockEntity displayAdapter
                    && displayAdapter.interact(point.x(), point.y(), mouseButton);
        }
        if ("external_source".equals(src)) {
            BlockEntity external = root.adjacentExternalSource();
            AccDisplaySource displaySource = AccDisplaySourceRegistry.source(external);
            return displaySource != null && displaySource.interact(external, point.x(), point.y(), mouseButton);
        }
        if (src.startsWith("advanced_data_link:")) {
            BlockPos linkPos;
            try {
                String encodedPosition = src.substring("advanced_data_link:".length());
                int presentationSuffix = encodedPosition.indexOf(":ship_information");
                if (presentationSuffix >= 0) {
                    encodedPosition = encodedPosition.substring(0, presentationSuffix);
                }
                linkPos = BlockPos.of(Long.parseLong(encodedPosition));
            } catch (NumberFormatException ignored) {
                return false;
            }
            BlockEntity link = level.getBlockEntity(linkPos);
            return link instanceof GyroscopeLinkBlockEntity dataLink
                    && dataLink.interactLinkedDisplay(point.x(), point.y(), mouseButton);
        }
        if ("display_link".equals(src)) {
            return false;
        }
        if ("computer_craft".equals(src)) {
            return root.interactComputerCraft(point.x(), point.y());
        }
        // -----------------------------------------------------GRAPH TARGETS-----------------------------------------------------
        AdvancedContraptionControllerBlockEntity activeController = root.boundController();
        if (activeController == null || activeController.displaysShipInitializationProgress()) {
            return false;
        }
        AdvancedGraphDocument graph = activeController.activeGraphView();
        String contentNodeId = root.displayFrame.getString("ContentNode");
        AdvancedGraphDocument.Node contentNode = contentNodeId.isBlank() ? null
                : graph.nodes().stream()
                .filter(node -> node.id().equals(contentNodeId))
                .findFirst().orElse(null);
        if (contentNode != null && "acc_display_external".equals(contentNode.type())) {
            return root.interactExternal(contentNode, point, mouseButton);
        }
        if (contentNode != null && ("acc_display_graph".equals(contentNode.type())
                || "acc_display_plotter".equals(contentNode.type())
                || "acc_display_crn".equals(contentNode.type()))) {
            return false;
        }
        List<AdvancedGraphDocument.Node> nodes = graph.nodes();
        for (int nodeIndex = nodes.size() - 1; nodeIndex >= 0; nodeIndex--) {
            AdvancedGraphDocument.Node node = nodes.get(nodeIndex);
            if (!(isDisplayWidgetNode(node)
                    || "advanced_hud_element".equals(node.type()))
                    || !root.value(node.id(), "visible").asBoolean()
                    || !root.targetsThisDisplay(node)) {
                continue;
            }
            int canvasWidth = Math.max(1, node.data().getInt("WidgetWidth"));
            int canvasHeight = Math.max(1, node.data().getInt("WidgetHeight"));
            double canvasX = point.x() * canvasWidth;
            double canvasY = point.y() * canvasHeight;
            ListTag elements = node.data().getList(AdvancedHudInteractions.ELEMENTS, Tag.TAG_COMPOUND);
            for (int idx = elements.size() - 1; idx >= 0; idx--) {
                CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                        elements.getCompound(idx), port -> root.value(node.id(), port));
                if (!AdvancedHudInteractions.isInteractiveType(elm.getString("Type"))
                        || elm.contains("Visible", Tag.TAG_BYTE) && !elm.getBoolean("Visible")
                        || !contains(elm, canvasX, canvasY)) {
                    continue;
                }
                return root.runInteraction(serverPlayer, node, elm, canvasX);
            }
        }
        return false;
    }

    // Handle ComputerCraft display interaction
    private boolean interactComputerCraft(double x, double y) {
        return invokeDirectComputerCraftInput("click", x, y, 0);
    }

    // Run the direct ComputerCraft input
    private boolean invokeDirectComputerCraftInput(String action, double x, double y, int val) {
        try {
            Class<?> peripheral = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.AccDisplayPeripheral");
            Object handled = peripheral.getMethod("input", AccDisplayBlockEntity.class,
                            String.class, double.class, double.class, int.class)
                    .invoke(null, this, action, x, y, val);
            return handled instanceof Boolean res && res;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Check if this is a computer craft display source
    public boolean isComputerCraftDisplaySource() {
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        CompoundTag frame = root.displayFrame;
        if (!"external".equals(frame.getString("State"))) return false;
        CompoundTag external = frame.getCompound("External");
        return "terminal".equals(external.getString("Format"))
                && "CC:Tweaked".equalsIgnoreCase(external.getString("Source"));
    }

    // Submit ComputerCraft input
    public boolean submitComputerCraftInput(ServerPlayer player, String action,
                                            double x, double y, int val) {
        AccDisplayBlockEntity root = networkRoot();
        if (root == null) root = this;
        if (!root.playerIsNear(player) || !root.isComputerCraftDisplaySource()) {
            return false;
        }
        String src = root.displayFrame.getString("ActiveSource");
        if ("computer_craft".equals(src)) {
            return root.invokeDirectComputerCraftInput(action, x, y, val);
        }
        if ("adapter".equals(src)) {
            BlockEntity adapter = root.adjacentAdapter();
            return adapter instanceof UniversalDisplayAdapterBlockEntity displayAdapter
                    && displayAdapter.input(action, x, y, val);
        }
        if ("external_source".equals(src)) {
            BlockEntity external = root.adjacentExternalSource();
            AccDisplaySource displaySource = AccDisplaySourceRegistry.source(external);
            return displaySource != null && displaySource.input(external, action, x, y, val);
        }
        AdvancedContraptionControllerBlockEntity activeController = root.boundController();
        if (activeController == null) return false;
        String contentNodeId = root.displayFrame.getString("ContentNode");
        AdvancedGraphDocument.Node contentNode = activeController.activeGraphView().nodes().stream()
                .filter(node -> node.id().equals(contentNodeId)
                        && "acc_display_external".equals(node.type()))
                .findFirst().orElse(null);
        if (contentNode != null) {
            UniversalDisplayAdapterBlockEntity adapter = root.externalAdapter(contentNode);
            if (adapter != null) return adapter.input(action, x, y, val);
            BlockEntity external = root.externalSource(contentNode);
            AccDisplaySource displaySource = AccDisplaySourceRegistry.source(external);
            return displaySource != null && displaySource.input(external, action, x, y, val);
        }
        return false;
    }

    // Submit the text input
    public void submitTextInput(ServerPlayer player, String nodeId,
                                String interactionId, String text) {
        if (!playerIsNear(player)) {
            return;
        }
        AdvancedContraptionControllerBlockEntity activeController = boundController();
        if (activeController == null) {
            return;
        }
        AdvancedGraphDocument.Node node = activeController.activeGraphView().nodes().stream()
                .filter(candidate -> candidate.id().equals(nodeId)
                        && (isDisplayWidgetNode(candidate)
                        || "advanced_hud_element".equals(candidate.type())))
                .findFirst().orElse(null);
        if (node == null) {
            return;
        }
        ListTag elements = node.data().getList(AdvancedHudInteractions.ELEMENTS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            if ("text_input".equals(elm.getString("Type"))
                    && interactionId.equals(elm.getString("InteractionId"))) {
                String val = text == null ? "" : text.strip();
                if (val.length() > 64) {
                    val = val.substring(0, 64);
                }
                activeController.handleHudInteraction(nodeId, interactionId,
                        AdvancedGraphDocument.Value.string(val));
                return;
            }
        }
    }

    // Run the interaction
    private boolean runInteraction(ServerPlayer player, AdvancedGraphDocument.Node node,
                                   CompoundTag elm, double canvasX) {
        String interactionId = elm.getString("InteractionId");
        if (interactionId.isBlank()) {
            return false;
        }
        String type = elm.getString("Type");
        String valuePort = elm.getString("ValuePort");
        AdvancedGraphDocument.Value current = valuePort.isBlank()
                ? AdvancedGraphDocument.Value.number(0.0D) : value(node.id(), valuePort);
        switch (type) {
            case "button" -> {
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.bool(true));
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.bool(false));
            }
            case "toggle" -> controller.handleHudInteraction(node.id(), interactionId,
                    AdvancedGraphDocument.Value.bool(!current.asBoolean()));
            case "slider" -> {
                double minimum = elm.getDouble("Min");
                double maximum = elm.getDouble("Max");
                if (!(maximum > minimum)) maximum = minimum + 1.0D;
                double fraction = Mth.clamp((canvasX - elm.getInt("X"))
                        / Math.max(1.0D, elm.getInt("W")), 0.0D, 1.0D);
                double next = minimum + (maximum - minimum) * fraction;
                double step = elm.getDouble("Step");
                if (step > 0.0D && Double.isFinite(step)) {
                    next = minimum + Math.round((next - minimum) / step) * step;
                }
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.number(Mth.clamp(next, minimum, maximum)));
            }
            case "text_input" -> PacketDistributor.sendToPlayer(player,
                    new AccDisplayTextInputOpenPayload(
                            MenuConfigTarget.of(worldPosition,
                                    SimulatedHelper.getContainingSubLevelId(this)),
                            node.id(), interactionId,
                            elm.getString("Text"), current.asString()));
            default -> {
                return false;
            }
        }
        return true;
    }

    // Get the bound controller
    private @Nullable AdvancedContraptionControllerBlockEntity boundController() {
        return controller == null || controller.isRemoved() ? null : controller;
    }

    // Check if the player is near the display
    private boolean playerIsNear(ServerPlayer player) {
        Vec3 pos = SimulatedHelper.toGlobalWorldPosition(
                this, Vec3.atCenterOf(worldPosition));
        if (player.position().distanceToSqr(pos) <= 32.0D * 32.0D) {
            return true;
        }
        UUID displaySubLevel = SimulatedHelper.getContainingSubLevelId(this);
        UUID playerSubLevel = SimulatedHelper.getSubLevelId(
                SimulatedHelper.getEntityTrackingSubLevel(player));
        return displaySubLevel != null && displaySubLevel.equals(playerSubLevel)
                && player.position().distanceToSqr(Vec3.atCenterOf(worldPosition))
                <= 32.0D * 32.0D;
    }

    // Get the screen point
    private ScreenPoint screenPoint(AccDisplayBlockEntity clicked, BlockHitResult hit) {
        DisplaySurfaceProjection.Point point = DisplaySurfaceProjection.normalizedPoint(
                worldPosition, clicked.worldPosition, screenRight(),
                SimulatedHelper.toBlockLocalHitPosition(clicked, hit),
                networkWidth(), networkHeight(), PIXELS_PER_BLOCK, BORDER_PIXELS);
        return new ScreenPoint(point.x(), point.y());
    }

    // Select the source
    private boolean selectSource(ScreenPoint point) {
        ListTag sources = displayFrame.getList("Sources", Tag.TAG_COMPOUND);
        if (sources.size() < 2 || point.y() < contentHeightFraction()) {
            return false;
        }
        int idx = Mth.clamp((int) Math.floor(point.x() * sources.size()),
                0, sources.size() - 1);
        String selected = sources.getCompound(idx).getString("Id");
        if (!selected.isBlank() && !selected.equals(activeSourceId)) {
            activeSourceId = selected;
            forceFullGraphSync = true;
            requestDisplayRefresh();
        }
        return true;
    }

    // Get the content point
    private ScreenPoint contentPoint(ScreenPoint point) {
        ListTag sources = displayFrame.getList("Sources", Tag.TAG_COMPOUND);
        if (sources.size() > 1) {
            double contentHeight = contentHeightFraction();
            point = new ScreenPoint(point.x(), Mth.clamp(
                    point.y() / contentHeight, 0.0D, 1.0D));
        }
        if (!displayFrame.contains("ContentNode", Tag.TAG_STRING)) return point;
        double width = Math.max(1.0D, displayFrame.getDouble("ContentWidth"));
        double height = Math.max(1.0D, displayFrame.getDouble("ContentHeight"));
        double scale = Math.max(0.01D, displayFrame.getDouble("ContentScale"));
        double centerX = displayFrame.getDouble("ContentX") + width * 0.5D;
        double centerY = displayFrame.getDouble("ContentY") + height * 0.5D;
        double dx = point.x() * 320.0D - centerX;
        double dy = point.y() * 180.0D - centerY;
        double rad = Math.toRadians(-displayFrame.getDouble("ContentRotation"));
        double localX = (dx * Math.cos(rad) - dy * Math.sin(rad)) / scale
                + width * 0.5D;
        double localY = (dx * Math.sin(rad) + dy * Math.cos(rad)) / scale
                + height * 0.5D;
        if (localX < 0.0D || localY < 0.0D || localX > width || localY > height) {
            return new ScreenPoint(-1.0D, -1.0D);
        }
        return new ScreenPoint(localX / width, localY / height);
    }

    // Get the content height fraction
    public double contentHeightFraction() {
        return Math.max(0.1D, 1.0D - TASKBAR_PIXELS
                / (double) Math.max(TASKBAR_PIXELS + 1, surfacePixelHeight()));
    }

    // Get the surface pixel width
    public int surfacePixelWidth() {
        return Math.max(1, networkWidth() * PIXELS_PER_BLOCK - BORDER_PIXELS * 2);
    }

    // Get the surface pixel height
    public int surfacePixelHeight() {
        return Math.max(1, networkHeight() * PIXELS_PER_BLOCK - BORDER_PIXELS * 2);
    }

    // Check if this contains the value
    private static boolean contains(CompoundTag elm, double x, double y) {
        int left = elm.getInt("X");
        int top = elm.getInt("Y");
        return x >= left && y >= top
                && x <= left + Math.max(1, elm.getInt("W"))
                && y <= top + Math.max(1, elm.getInt("H"));
    }

    // Get the screen left
    private net.minecraft.core.Direction screenLeft() {
        return screenRight().getOpposite();
    }

    // Check if this matches display
    private boolean matchesDisplay(BlockPos pos) {
        if (level == null) {
            return false;
        }
        BlockState own = getBlockState();
        LevelChunk chunk = loadedChunk(pos);
        if (chunk == null) {
            return false;
        }
        BlockState other = chunk.getBlockState(pos);
        return own.getBlock() == other.getBlock()
                && own.getValue(AccDisplayBlock.FACING) == other.getValue(AccDisplayBlock.FACING)
                && own.getValue(AccDisplayBlock.SIDE) == other.getValue(AccDisplayBlock.SIDE)
                && own.getValue(AccDisplayBlock.Y_ALIGNMENT) == other.getValue(AccDisplayBlock.Y_ALIGNMENT)
                && own.getValue(AccDisplayBlock.Z_ALIGNMENT) == other.getValue(AccDisplayBlock.Z_ALIGNMENT);
    }

    // Get the loaded chunk
    private @Nullable LevelChunk loadedChunk(BlockPos pos) {
        if (level == null) {
            return null;
        }
        return level.getChunkSource().getChunkNow(
                pos.getX() >> 4, pos.getZ() >> 4);
    }

    // Create the display frame
    private CompoundTag createDisplayFrame() {
        // ------------------------------------INITIALIZATION FRAME------------------------------------
        if (!scmInitializationFrame.isEmpty()) {
            CompoundTag frame = scmInitializationFrame.copy();
            ListTag sourceSummary = new ListTag();
            CompoundTag src = new CompoundTag();
            src.putString("Id", "scm_initialization");
            src.putString("Label", "SCM");
            sourceSummary.add(src);
            frame.put("Sources", sourceSummary);
            frame.putString("ActiveSource", "scm_initialization");
            return frame;
        }
        // --------------------------------------------------SOURCE COLLECTION---------------------------------------------------
        ListTag sources = new ListTag();
        ListTag presentations = new ListTag();
        if (controller != null && !controller.isRemoved()) {
            for (int idx = 0; idx < controllerSources.size(); idx++) {
                CompoundTag src = controllerSources.getCompound(idx);
                refreshCtrlExternalSource(src);
                if ("crn".equals(src.getCompound("Frame").getString("State"))) {
                    presentations.add(src);
                } else {
                    sources.add(src);
                }
            }
        }
        DiagnosticTabletBlockEntity diagnosticTablet = diagnosticTabletSource();
        if (diagnosticTablet != null) {
            CompoundTag external = new CompoundTag();
            external.putString("State", "external");
            CompoundTag tabletFrame = new CompoundTag();
            tabletFrame.putString("Format", "diagnostic_tablet");
            tabletFrame.putString("Source", "Smart Tablet");
            external.put("External", tabletFrame);
            addSource(sources, "diagnostic_tablet", "Smart Tablet", external);
        }
        CompoundTag adapter = adjacentAdapterFrame();
        if (!adapter.isEmpty()) {
            addTransportSources(sources, presentations, "adapter", "Adapter", adapter);
        }
        BlockEntity externalSource = adjacentExternalSource();
        AccDisplaySource sourceProvider = AccDisplaySourceRegistry.source(externalSource);
        if (sourceProvider != null) {
            CompoundTag payload = sourceProvider.frame(externalSource,
                    surfacePixelWidth(), surfacePixelHeight());
            if (payload != null && !payload.isEmpty()) {
                CompoundTag external = new CompoundTag();
                external.putString("State", "external");
                external.put("External", payload.copy());
                addSource(sources, "external_source",
                        externalSource.getBlockState().getBlock().getName().getString(), external);
            }
        }
        CompoundTag computerCraft = computerCraftFrame();
        if (!computerCraft.isEmpty()) {
            CompoundTag external = new CompoundTag();
            external.putString("State", "external");
            external.put("External", computerCraft);
            addSource(sources, "computer_craft", "CC:Tweaked", external);
        }
        if (!displayLinkLines.isEmpty()) {
            addSource(sources, "display_link", "Display Link", displayLinkFrame());
        }
        for (Map.Entry<BlockPos, CompoundTag> entry : advancedDataLinkFrames.entrySet()) {
            CompoundTag external = entry.getValue();
            if (external.isEmpty()) {
                continue;
            }
            String label = external.getString("Source");
            addTransportSources(sources, presentations,
                    "advanced_data_link:" + entry.getKey().asLong(),
                    label.isBlank() ? "Advanced Data Link" : label, external);
        }
        if (!mappedShipInformationFrame.isEmpty()) {
            addSource(presentations, "mapped_ship_information", "Ship Information",
                    mappedShipInformationFrame.copy());
        }
        if (sources.isEmpty() && presentations.isEmpty()) {
            CompoundTag frame = new CompoundTag();
            frame.putString("State", "unbound");
            return frame;
        }
        // ---------------------------------------------------SOURCE SELECTION---------------------------------------------------
        CompoundTag active = null;
        if ("ship_information".equals(configuredDisplayMode)) {
            for (int presentationIndex = 0;
                 presentationIndex < presentations.size() && active == null;
                 presentationIndex++) {
                String presentationId = presentations.getCompound(presentationIndex).getString("Id");
                for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                    CompoundTag src = sources.getCompound(sourceIndex);
                    if (presentationId.equals(src.getString("Id"))) {
                        active = src;
                        break;
                    }
                }
            }
            if (active == null) {
                for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                    CompoundTag src = sources.getCompound(sourceIndex);
                    if (activeSourceId.equals(src.getString("Id"))) {
                        active = src;
                        break;
                    }
                }
            }
            if (active == null) {
                for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                    CompoundTag src = sources.getCompound(sourceIndex);
                    String state = src.getCompound("Frame").getString("State");
                    if ("external".equals(state) || "display_link".equals(state)) {
                        active = src;
                        break;
                    }
                }
            }
        }
        if (!DISPLAY_MODE_AUTO.equals(configuredDisplayMode)
                && !"ship_information".equals(configuredDisplayMode)) {
            for (int idx = 0; idx < sources.size(); idx++) {
                CompoundTag src = sources.getCompound(idx);
                if (sourceMatchesMode(src, configuredDisplayMode)) {
                    active = src;
                    break;
                }
            }
        }
        if (DISPLAY_MODE_AUTO.equals(configuredDisplayMode)) {
            for (int idx = 0; idx < sources.size(); idx++) {
                CompoundTag src = sources.getCompound(idx);
                if (active == null && activeSourceId.equals(src.getString("Id"))) {
                    active = src;
                    break;
                }
            }
            if (active == null) {
                active = sources.isEmpty() ? null : sources.getCompound(0);
            }
        }
        if (active != null) {
            activeSourceId = active.getString("Id");
        }
        // ---------------------------------------------------FRAME COMPOSITION--------------------------------------------------
        CompoundTag frame;
        if ("ship_information".equals(configuredDisplayMode)
                && (active != null || !presentations.isEmpty())) {
            CompoundTag presentation = presentations.isEmpty() ? new CompoundTag()
                    : matchingPresentation(presentations,
                    active == null ? "" : active.getString("Id")).getCompound("Frame");
            frame = composeShipInformationFrame(presentation,
                    active == null ? new CompoundTag() : active.getCompound("Frame"),
                    shipInformationDisplayMode);
        } else if (active != null) {
            frame = active.getCompound("Frame").copy();
        } else if (DISPLAY_MODE_AUTO.equals(configuredDisplayMode)) {
            CompoundTag presentation = presentations.getCompound(0);
            activeSourceId = presentation.getString("Id");
            frame = presentation.getCompound("Frame").copy();
        } else {
            frame = unavailableModeFrame(configuredDisplayMode);
        }
        // ----------------------------------------------------SOURCE SUMMARY----------------------------------------------------
        ListTag sourceSummary = new ListTag();
        ListTag summarizedSources = sources.isEmpty() ? presentations : sources;
        for (int idx = 0; idx < summarizedSources.size(); idx++) {
            CompoundTag src = summarizedSources.getCompound(idx);
            CompoundTag summary = new CompoundTag();
            summary.putString("Id", src.getString("Id"));
            summary.putString("Label", src.getString("Label"));
            sourceSummary.add(summary);
        }
        frame.put("Sources", sourceSummary);
        frame.putString("ActiveSource", activeSourceId);
        frame.putString("ConfiguredMode", shipInformationDisplayMode);
        frame.putString("ConfiguredSourceMode", configuredDisplayMode);
        return frame;
    }

    // Get the ComputerCraft frame
    private CompoundTag computerCraftFrame() {
        try {
            Class<?> peripheral = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.AccDisplayPeripheral");
            Object frame = peripheral.getMethod("frame", AccDisplayBlockEntity.class).invoke(null, this);
            return frame instanceof CompoundTag res ? res : new CompoundTag();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new CompoundTag();
        }
    }

    // Check if the source matches the display mode
    private static boolean sourceMatchesMode(CompoundTag src, String mode) {
        String id = src.getString("Id");
        CompoundTag frame = src.getCompound("Frame");
        String state = frame.getString("State");
        String frameMode = frame.getString("Mode");
        return switch (mode) {
            case "acc_widgets" -> "acc:widgets".equals(id);
            case "acc_graph" -> "graph".equals(state) && "graph".equals(frameMode);
            case "acc_plotter" -> "graph".equals(state) && "plotter".equals(frameMode);
            case "ship_information" -> "crn".equals(state);
            case "external" -> "external".equals(state)
                    && !"adapter".equals(id) && !id.startsWith("advanced_data_link:");
            case "display_link" -> "display_link".equals(id)
                    || "display_link".equals(state)
                    || "text".equals(frame.getCompound("External").getString("Format"))
                    && frame.getCompound("External").getString("Source")
                    .toLowerCase(java.util.Locale.ROOT).contains("display link");
            case "advanced_data_link" -> "adapter".equals(id)
                    || id.startsWith("advanced_data_link:");
            default -> false;
        };
    }

    // Get the unavailable mode frame
    private static CompoundTag unavailableModeFrame(String mode) {
        CompoundTag frame = new CompoundTag();
        frame.putString("State", "unavailable");
        frame.putString("Mode", mode);
        frame.putString("Title", "ACC Display");
        frame.putString("Message", "The selected display source is not available");
        return frame;
    }

    // Add the source
    private static void addSource(
            ListTag sources, String id, String label, CompoundTag frame) {
        CompoundTag src = new CompoundTag();
        src.putString("Id", id);
        src.putString("Label", label);
        src.put("Frame", frame);
        sources.add(src);
    }

    // Add the transport sources
    private static void addTransportSources(
            ListTag sources, ListTag presentations,
            String id, String fallbackLabel, CompoundTag transported
    ) {
        CompoundTag payload = DisplayFrameEnvelope.payload(transported);
        CompoundTag presentation = DisplayFrameEnvelope.presentation(transported);
        String sourceLabel = payload.getString("Source");
        String label = sourceLabel.isBlank() ? fallbackLabel : sourceLabel;
        if (DisplayFrameEnvelope.hasRenderablePayload(payload)) {
            CompoundTag externalFrame = new CompoundTag();
            externalFrame.putString("State", "external");
            externalFrame.put("External", payload.copy());
            addSource(sources, id, label, externalFrame);
        }
        if (!presentation.isEmpty()) {
            presentation.putString("State", "crn");
            addSource(presentations, id,
                    "ACC Display Ship Information", presentation);
        }
    }

    // Get the matching presentation
    private static CompoundTag matchingPresentation(ListTag presentations, String sourceId) {
        if (!sourceId.isBlank()) {
            for (int idx = 0; idx < presentations.size(); idx++) {
                CompoundTag presentation = presentations.getCompound(idx);
                if (sourceId.equals(presentation.getString("Id"))) {
                    return presentation;
                }
            }
        }
        return presentations.getCompound(0);
    }

    // Get the compose ship information frame
    private static CompoundTag composeShipInformationFrame(
            CompoundTag presentationFrame, CompoundTag payloadFrame,
            String displayMode
    ) {
        CompoundTag frame = presentationFrame.copy();
        frame.putString("State", "crn");
        frame.putString("Mode", ShipInformationDisplayModes.normalize(displayMode));
        CompoundTag payload = new CompoundTag();
        String payloadState = payloadFrame.getString("State");
        if ("external".equals(payloadState)) {
            payload = payloadFrame.getCompound("External").copy();
        } else if ("display_link".equals(payloadState)) {
            payload.putString("Format", "text");
            payload.putString("Source", "Display Link");
            payload.put("Lines", payloadFrame.getList("Lines", Tag.TAG_STRING).copy());
        }
        forwardShipInformationSource(frame, payload);
        if (DisplayFrameEnvelope.hasRenderablePayload(payload)
                && frame.getList("DataLines", Tag.TAG_STRING).isEmpty()) {
            frame.put("ExternalPayload", payload);
        }
        if (!payload.isEmpty()) {
            frame.remove("ContentNode");
            frame.remove("ContentX");
            frame.remove("ContentY");
            frame.remove("ContentWidth");
            frame.remove("ContentHeight");
            frame.remove("ContentScale");
            frame.remove("ContentRotation");
        }
        return frame;
    }

    // Get the display link frame
    private CompoundTag displayLinkFrame() {
        CompoundTag frame = new CompoundTag();
        frame.putString("State", "display_link");
        ListTag lines = new ListTag();
        for (String line : displayLinkLines) {
            lines.add(net.minecraft.nbt.StringTag.valueOf(line));
        }
        frame.put("Lines", lines);
        return frame;
    }

    // Create the controller sources
    private ListTag createControllerSources(
            AdvancedContraptionControllerBlockEntity controller,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        ListTag sources = new ListTag();
        if (controller.displaysShipInitializationProgress()) {
            addSource(sources, "acc", "ACC",
                    createControllerFrame(controller, inputs, outputs, null, new CompoundTag()));
            return sources;
        }
        List<AdvancedGraphDocument.Node> visibleNodes = visibleContentNodes(
                controller, inputs, outputs);
        boolean widgetsVisible = visibleNodes.stream()
                .anyMatch(AccDisplayBlockEntity::isDisplayWidgetNode);
        List<AdvancedGraphDocument.Node> standaloneNodes = visibleNodes.stream()
                .filter(node -> "acc_display_graph".equals(node.type())
                        || "acc_display_plotter".equals(node.type())
                        || "acc_display_external".equals(node.type())
                        || "acc_display_crn".equals(node.type()))
                .toList();
        boolean sharedValuesRequired = widgetsVisible || standaloneNodes.stream()
                .anyMatch(node -> "acc_display_graph".equals(node.type())
                        || "acc_display_plotter".equals(node.type()));
        CompoundTag sharedValues = new CompoundTag();
        if (sharedValuesRequired) {
            writeValues(sharedValues, inputs);
            writeValues(sharedValues, outputs);
        }
        if (widgetsVisible || standaloneNodes.isEmpty()) {
            addSource(sources, "acc:widgets", "ACC",
                    createControllerFrame(controller, inputs, outputs, null, sharedValues));
        }
        Map<String, Integer> typeTotals = new java.util.HashMap<>();
        for (AdvancedGraphDocument.Node node : standaloneNodes) {
            typeTotals.merge(node.type(), 1, Integer::sum);
        }
        Map<String, Integer> typeIndexes = new java.util.HashMap<>();
        for (AdvancedGraphDocument.Node node : standaloneNodes) {
            int typeIndex = typeIndexes.merge(node.type(), 1, Integer::sum);
            addSource(sources, "acc:" + node.id(),
                    controllerSourceLabel(node, typeIndex,
                            typeTotals.getOrDefault(node.type(), 1)),
                    createControllerFrame(controller, inputs, outputs, node, sharedValues));
        }
        return sources;
    }

    // Create the controller frame
    private CompoundTag createControllerFrame(
            AdvancedContraptionControllerBlockEntity controller,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs,
            @Nullable AdvancedGraphDocument.Node contentNode,
            CompoundTag sharedValues
    ) {
        CompoundTag frame = new CompoundTag();
        frame.putLong("ControllerPos", controller.getBlockPos().asLong());
        java.util.UUID controllerSubLevel = SimulatedHelper.getContainingSubLevelId(controller);
        if (controllerSubLevel != null) {
            frame.putUUID("ControllerSubLevel", controllerSubLevel);
        }
        if (controller.displaysShipInitializationProgress()) {
            frame.putString("State", "initializing");
            frame.putInt("Percent", controller.getShipInitializationProgressPercent());
            frame.putString("Status", controller.getShipInitializationProgressStatus());
            return frame;
        }
        if (contentNode != null && "acc_display_external".equals(contentNode.type())) {
            frame.putString("State", "external");
            putContentLayout(frame, contentNode, inputs, outputs);
            frame.put("External", externalFrame(contentNode,
                    Math.max(1, (int) Math.round(frame.getDouble("ContentWidth"))),
                    Math.max(1, (int) Math.round(frame.getDouble("ContentHeight")))));
            return frame;
        }
        if (contentNode != null && "acc_display_crn".equals(contentNode.type())) {
            frame.putString("State", "crn");
            populateCrnFrame(frame, controller, contentNode, inputs, outputs);
            putContentLayout(frame, contentNode, inputs, outputs);
            return frame;
        }
        frame.putString("State", "graph");
        frame.putString("Mode", contentNode == null ? "widgets" : switch (contentNode.type()) {
            case "acc_display_graph" -> "graph";
            case "acc_display_plotter" -> "plotter";
            default -> "widgets";
        });
        if (contentNode != null) {
            putContentLayout(frame, contentNode, inputs, outputs);
        }
        int graphRevision = controller.getActiveGraphRevision();
        if (controller != cachedGraphController || graphRevision != cachedGraphRevision) {
            cachedGraphController = controller;
            cachedGraphRevision = graphRevision;
            cachedGraphTag = controller.activeGraphView().toTag();
        }
        frame.putInt("GraphRevision", graphRevision);
        frame.put("Graph", cachedGraphTag);
        frame.put("Values", sharedValues);
        return frame;
    }

    // Get the visible content nodes
    private List<AdvancedGraphDocument.Node> visibleContentNodes(
            AdvancedContraptionControllerBlockEntity activeController,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        List<AdvancedGraphDocument.Node> res = new ArrayList<>();
        for (AdvancedGraphDocument.Node node : activeController.activeGraphView().nodes()) {
            if (node != null && (node.type().startsWith("acc_display_")
                    || "acc_hologram_widget".equals(node.type()))
                    && targetsThisDisplay(node) && contentNodeVisible(node, inputs, outputs)) {
                res.add(node);
            }
        }
        return res;
    }

    // Check if the content node is visible
    static boolean contentNodeVisible(
            AdvancedGraphDocument.Node node,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        AdvancedGraphDocument.Value visible = outputs.get(node.id() + ":visible");
        if (visible == null) {
            visible = inputs.get(node.id() + ":visible");
        }
        if (visible == null) {
            CompoundTag stored = node.data().getCompound("Defaults").getCompound("visible");
            visible = stored.isEmpty()
                    ? AdvancedGraphDocument.Value.bool(true)
                    : new AdvancedGraphDocument.Value(
                    stored.getString("Type"), stored.getCompound("Payload"));
        }
        return visible.asBoolean();
    }

    // Get the controller source label
    private static String controllerSourceLabel(
            AdvancedGraphDocument.Node node, int typeIndex, int typeTotal
    ) {
        String custom = node.label();
        if (!custom.isBlank() && !custom.equals(node.type())) {
            return custom;
        }
        String base = switch (node.type()) {
            case "acc_display_graph" -> "Graph";
            case "acc_display_plotter" -> "Plotter";
            case "acc_display_external" -> "External";
            case "acc_display_crn" -> "ACC Display Ship Information";
            default -> "ACC";
        };
        return typeTotal > 1 ? base + " " + typeIndex : base;
    }

    // Check if this is a display widget node
    private static boolean isDisplayWidgetNode(AdvancedGraphDocument.Node node) {
        return node != null && ("acc_display_widget".equals(node.type())
                || "acc_hologram_widget".equals(node.type()));
    }

    // Get the source graph revision
    private static int sourceGraphRevision(ListTag sources) {
        for (int idx = 0; idx < sources.size(); idx++) {
            CompoundTag frame = sources.getCompound(idx).getCompound("Frame");
            if (frame.contains("GraphRevision", Tag.TAG_INT)) {
                return frame.getInt("GraphRevision");
            }
        }
        return Integer.MIN_VALUE;
    }

    // Put the content layout
    private static void putContentLayout(
            CompoundTag frame,
            AdvancedGraphDocument.Node node,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        frame.putString("ContentNode", node.id());
        frame.putDouble("ContentX", runtimeValue(node, "x", inputs, outputs, 0.0D));
        frame.putDouble("ContentY", runtimeValue(node, "y", inputs, outputs, 0.0D));
        frame.putDouble("ContentWidth", Math.max(1.0D,
                runtimeValue(node, "width", inputs, outputs, 320.0D)));
        frame.putDouble("ContentHeight", Math.max(1.0D,
                runtimeValue(node, "height", inputs, outputs, 180.0D)));
        frame.putDouble("ContentScale", Math.max(0.01D,
                runtimeValue(node, "scale", inputs, outputs, 1.0D)));
        frame.putDouble("ContentRotation",
                runtimeValue(node, "rotation", inputs, outputs, 0.0D));
    }

    // Get the runtime value
    static double runtimeValue(
            AdvancedGraphDocument.Node node,
            String port,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs,
            double fallback
    ) {
        AdvancedGraphDocument.Value val = outputs.get(node.id() + ":" + port);
        if (val == null) {
            val = inputs.get(node.id() + ":" + port);
        }
        if (val == null) {
            CompoundTag stored = node.data().getCompound("Defaults").getCompound(port);
            if (!stored.isEmpty()) {
                val = new AdvancedGraphDocument.Value(
                        stored.getString("Type"), stored.getCompound("Payload"));
            }
        }
        if (val == null || !Double.isFinite(val.asNumber())) {
            return fallback;
        }
        return val.asNumber();
    }

    // Get the runtime string
    static String runtimeString(
            AdvancedGraphDocument.Node node,
            String port,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs,
            String fallback
    ) {
        AdvancedGraphDocument.Value val = outputs.get(node.id() + ":" + port);
        if (val == null) {
            val = inputs.get(node.id() + ":" + port);
        }
        if (val == null) {
            CompoundTag stored = node.data().getCompound("Defaults").getCompound(port);
            if (!stored.isEmpty()) {
                val = new AdvancedGraphDocument.Value(
                        stored.getString("Type"), stored.getCompound("Payload"));
            }
        }
        return val == null ? fallback : val.asString();
    }

    // Populate the CRN frame
    static void populateCrnFrame(
            CompoundTag frame,
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node,
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        String displayMode = node.data().getString("DisplayMode");
        String mode = displayMode.isBlank()
                ? ShipInformationDisplayModes.DEFAULT
                : ShipInformationDisplayModes.normalize(displayMode);
        frame.putString("Mode", mode);
        frame.put("CrnData", RailwayNavigatorGraphCompat.shipDisplayFrame(
                controller.getCrnShipDisplayData()));
        if (AdvancedGraphCatalog.isCrnStaticTextMode(mode)) {
            frame.putString("Text", runtimeString(node, "text", inputs, outputs, ""));
        }
    }

    // Forward the ship information source
    static void forwardShipInformationSource(CompoundTag frame, CompoundTag sourceFrame) {
        if (frame == null || sourceFrame == null || sourceFrame.isEmpty()) {
            return;
        }
        ListTag sourceLines = sourceFrame.getList("Lines", Tag.TAG_STRING);
        if (sourceLines.isEmpty()) {
            return;
        }
        frame.put("DataLines", sourceLines.copy());
        String src = sourceFrame.getString("Source");
        if (!src.isBlank()) {
            frame.putString("DataSource", src);
        }
        if (AdvancedGraphCatalog.isCrnStaticTextMode(frame.getString("Mode"))
                && frame.getString("Text").isBlank()) {
            List<String> lines = new ArrayList<>();
            for (int idx = 0; idx < sourceLines.size(); idx++) {
                lines.add(sourceLines.getString(idx));
            }
            frame.putString("Text", String.join("\n", lines));
        }
    }

    // Refresh the ctrl external source
    private void refreshCtrlExternalSource(CompoundTag src) {
        if (controller == null || controller.isRemoved()) {
            return;
        }
        CompoundTag frame = src.getCompound("Frame");
        if (!"external".equals(frame.getString("State"))) {
            return;
        }
        String contentNodeId = frame.getString("ContentNode");
        if (contentNodeId.isBlank()) {
            return;
        }
        AdvancedGraphDocument.Node node = controller.activeGraphView().nodes().stream()
                .filter(candidate -> contentNodeId.equals(candidate.id())
                        && "acc_display_external".equals(candidate.type()))
                .findFirst().orElse(null);
        if (node == null) {
            return;
        }
        frame.put("External", externalFrame(node,
                Math.max(1, (int) Math.round(frame.getDouble("ContentWidth"))),
                Math.max(1, (int) Math.round(frame.getDouble("ContentHeight")))));
        src.put("Frame", frame);
    }

    // Get the external frame
    private CompoundTag externalFrame(AdvancedGraphDocument.Node node, int width, int height) {
        CompoundTag src = node.data().getCompound("SourceData");
        if (level == null) {
            return unavailableExternalFrame("Display source is unavailable");
        }
        if (!src.contains("BlockPos")) {
            CompoundTag adjacent = adjacentExternalFrame(width, height);
            return adjacent.isEmpty()
                    ? unavailableExternalFrame("Select an adapter or place a display beside a source")
                    : adjacent;
        }
        java.util.UUID subLevelId = src.hasUUID("SubLevelId")
                ? src.getUUID("SubLevelId") : null;
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                level, subLevelId, BlockPos.of(src.getLong("BlockPos")));
        if (blockEntity instanceof UniversalDisplayAdapterBlockEntity adapter) {
            adapter.configureDisplaySize(width, height);
            CompoundTag external = adapter.externalFrame();
            return external.isEmpty()
                    ? unavailableExternalFrame("Waiting for display source") : external;
        }
        AccDisplaySource displaySource = AccDisplaySourceRegistry.source(blockEntity);
        if (displaySource != null) {
            CompoundTag external = displaySource.frame(blockEntity, width, height);
            return external == null || external.isEmpty()
                    ? unavailableExternalFrame("Waiting for display source") : external.copy();
        }
        return unavailableExternalFrame("Display source is unavailable");
    }

    // Handle external display interaction
    private boolean interactExternal(AdvancedGraphDocument.Node node, ScreenPoint point,
                                     int mouseButton) {
        UniversalDisplayAdapterBlockEntity adapter = externalAdapter(node);
        if (adapter != null) return adapter.interact(point.x(), point.y(), mouseButton);
        BlockEntity external = externalSource(node);
        AccDisplaySource displaySource = AccDisplaySourceRegistry.source(external);
        return displaySource != null && displaySource.interact(external, point.x(), point.y(), mouseButton);
    }

    // Get the external adapter
    private @Nullable UniversalDisplayAdapterBlockEntity externalAdapter(
            AdvancedGraphDocument.Node node) {
        CompoundTag src = node.data().getCompound("SourceData");
        if (level != null && src.contains("BlockPos")) {
            java.util.UUID subLevelId = src.hasUUID("SubLevelId")
                    ? src.getUUID("SubLevelId") : null;
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                    level, subLevelId, BlockPos.of(src.getLong("BlockPos")));
            if (blockEntity instanceof UniversalDisplayAdapterBlockEntity adapter) {
                return adapter;
            }
        }
        BlockEntity adjacent = adjacentExternalSource();
        return adjacent instanceof UniversalDisplayAdapterBlockEntity adapter ? adapter : null;
    }

    // Get the external source
    private @Nullable BlockEntity externalSource(AdvancedGraphDocument.Node node) {
        CompoundTag src = node.data().getCompound("SourceData");
        if (level != null && src.contains("BlockPos")) {
            java.util.UUID subLevelId = src.hasUUID("SubLevelId")
                    ? src.getUUID("SubLevelId") : null;
            return SimulatedHelper.findBlockEntity(level, subLevelId,
                    BlockPos.of(src.getLong("BlockPos")));
        }
        return adjacentExternalSource();
    }

    // Get the adjacent external frame
    private CompoundTag adjacentExternalFrame(int width, int height) {
        BlockEntity src = adjacentExternalSource();
        if (src instanceof UniversalDisplayAdapterBlockEntity adapter) {
            adapter.configureDisplaySize(width, height);
            return adapter.externalFrame();
        }
        AccDisplaySource displaySource = AccDisplaySourceRegistry.source(src);
        if (displaySource != null) {
            CompoundTag frame = displaySource.frame(src, width, height);
            return frame == null ? new CompoundTag() : frame.copy();
        }
        return new CompoundTag();
    }

    // Get the adjacent adapter frame
    private CompoundTag adjacentAdapterFrame() {
        BlockEntity src = adjacentAdapter();
        if (!(src instanceof UniversalDisplayAdapterBlockEntity adapter)) {
            return new CompoundTag();
        }
        adapter.configureDisplaySize(
                surfacePixelWidth(), surfacePixelHeight());
        return adapter.accDisplayFrame();
    }

    // Get the adjacent adapter
    private @Nullable BlockEntity adjacentAdapter() {
        if (level == null) return null;
        for (int y = 0; y < networkHeight(); y++) {
            for (int x = 0; x < networkWidth(); x++) {
                BlockPos displayPos = worldPosition.below(y).relative(screenRight(), x);
                for (Direction dir : Direction.values()) {
                    BlockEntity candidate = level.getBlockEntity(displayPos.relative(dir));
                    if (candidate instanceof UniversalDisplayAdapterBlockEntity) return candidate;
                }
            }
        }
        return null;
    }

    // Get the diagnostic tablet source
    public @Nullable DiagnosticTabletBlockEntity diagnosticTabletSource() {
        if (level == null) return null;
        for (int y = 0; y < networkHeight(); y++) {
            for (int x = 0; x < networkWidth(); x++) {
                BlockPos displayPos = worldPosition.below(y).relative(screenRight(), x);
                for (Direction dir : Direction.values()) {
                    BlockEntity candidate = level.getBlockEntity(displayPos.relative(dir));
                    if (candidate instanceof DiagnosticTabletBlockEntity tablet) {
                        return tablet;
                    }
                }
            }
        }
        return null;
    }

    // Get the adjacent external source
    private @Nullable BlockEntity adjacentExternalSource() {
        if (level == null) return null;
        for (int y = 0; y < networkHeight(); y++) {
            for (int x = 0; x < networkWidth(); x++) {
                BlockPos displayPos = worldPosition.below(y).relative(screenRight(), x);
                for (Direction dir : Direction.values()) {
                    BlockEntity candidate = level.getBlockEntity(displayPos.relative(dir));
                    if (candidate instanceof UniversalDisplayAdapterBlockEntity || AccDisplaySourceRegistry.isSource(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    // Get the unavailable external frame
    private static CompoundTag unavailableExternalFrame(String msg) {
        CompoundTag frame = new CompoundTag();
        frame.putString("Format", "text");
        ListTag lines = new ListTag();
        lines.add(net.minecraft.nbt.StringTag.valueOf(msg));
        frame.put("Lines", lines);
        frame.putInt("Width", Math.max(1, msg.length()));
        frame.putInt("Height", 1);
        return frame;
    }

    // Check if the graph node targets this display
    public boolean targetsThisDisplay(AdvancedGraphDocument.Node node) {
        if (node == null) {
            return false;
        }
        CompoundTag target = node.data().getCompound("TargetData");
        if (!target.contains("BlockPos")) {
            return false;
        }
        java.util.UUID ownSubLevel = SimulatedHelper.getContainingSubLevelId(this);
        java.util.UUID targetSubLevel = target.hasUUID("SubLevelId")
                ? target.getUUID("SubLevelId") : null;
        if (!java.util.Objects.equals(ownSubLevel, targetSubLevel)) {
            return false;
        }
        BlockPos targetPos = BlockPos.of(target.getLong("BlockPos"));
        for (int y = 0; y < networkHeight(); y++) {
            for (int x = 0; x < networkWidth(); x++) {
                if (worldPosition.below(y).relative(screenRight(), x).equals(targetPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Write the values
    private static void writeValues(
            CompoundTag target,
            Map<String, AdvancedGraphDocument.Value> values
    ) {
        values.forEach((key, val) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", val.type());
            tag.put("Payload", val.payload().copy());
            target.put(key, tag);
        });
    }

    // Store the screen point
    private record ScreenPoint(double x, double y) {
    }

    // Write the ACC display
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putString("AccDisplayMode", configuredDisplayMode);
        tag.putString("AccDisplayShipInformationMode", shipInformationDisplayMode);
        tag.putBoolean("AccDisplayShipInformationModeConfigured",
                shipInformationModeConfigured);
        if (clientPacket) {
            tag.put("AccDisplayFrame", displayFrame.copy());
        }
    }

    // Create the display update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (ignored, registryAccess) -> writeIncrementalUpdate(registryAccess));
    }

    // Write the incremental update
    private CompoundTag writeIncrementalUpdate(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        write(tag, provider, true);
        CompoundTag packetFrame = tag.getCompound("AccDisplayFrame");
        CompoundTag currentFrame = displayFrame.copy();
        if ("graph".equals(packetFrame.getString("State"))) {
            int revision = packetFrame.getInt("GraphRevision");
            boolean canSendDelta = !forceFullGraphSync
                    && revision == lastNetworkGraphRevision
                    && "graph".equals(lastNetworkFrame.getString("State"));
            if (!canSendDelta) {
                forceFullGraphSync = false;
                lastNetworkGraphRevision = revision;
            } else {
                packetFrame.remove("Graph");
                CompoundTag previousValues = lastNetworkFrame.getCompound("Values");
                CompoundTag currentValues = currentFrame.getCompound("Values");
                CompoundTag changedValues = new CompoundTag();
                for (String key : currentValues.getAllKeys()) {
                    if (!Objects.equals(currentValues.get(key), previousValues.get(key))) {
                        changedValues.put(key, currentValues.get(key).copy());
                    }
                }
                ListTag removedValues = new ListTag();
                for (String key : previousValues.getAllKeys()) {
                    if (!currentValues.contains(key)) {
                        removedValues.add(net.minecraft.nbt.StringTag.valueOf(key));
                    }
                }
                packetFrame.putBoolean("ValueDelta", true);
                packetFrame.put("Values", changedValues);
                if (!removedValues.isEmpty()) {
                    packetFrame.put("RemovedValues", removedValues);
                }
            }
        }
        lastNetworkFrame = currentFrame;
        return tag;
    }

    // Read the ACC display
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        String legacyMode = tag.getString("AccDisplayMode");
        configuredDisplayMode = normalizeDisplaySourceMode(legacyMode);
        if (tag.contains("AccDisplayShipInformationMode", Tag.TAG_STRING)) {
            shipInformationDisplayMode = normalizeDisplayMode(
                    tag.getString("AccDisplayShipInformationMode"));
            shipInformationModeConfigured = tag.getBoolean(
                    "AccDisplayShipInformationModeConfigured");
        } else if (ShipInformationDisplayModes.contains(legacyMode)) {
            shipInformationDisplayMode = normalizeDisplayMode(legacyMode);
            shipInformationModeConfigured = true;
            configuredDisplayMode = "ship_information";
        } else {
            shipInformationDisplayMode = ShipInformationDisplayModes.DEFAULT;
            shipInformationModeConfigured = "ship_information".equals(legacyMode);
        }
        if (clientPacket && tag.contains("AccDisplayFrame", Tag.TAG_COMPOUND)) {
            CompoundTag incoming = tag.getCompound("AccDisplayFrame").copy();
            if (incoming.getBoolean("ValueDelta")) {
                CompoundTag mergedValues = displayFrame.getCompound("Values").copy();
                CompoundTag changedValues = incoming.getCompound("Values");
                for (String key : changedValues.getAllKeys()) {
                    mergedValues.put(key, changedValues.get(key).copy());
                }
                ListTag removedValues = incoming.getList("RemovedValues", Tag.TAG_STRING);
                for (int idx = 0; idx < removedValues.size(); idx++) {
                    mergedValues.remove(removedValues.getString(idx));
                }
                incoming.put("Values", mergedValues);
                incoming.remove("ValueDelta");
                incoming.remove("RemovedValues");
            }
            if ("graph".equals(incoming.getString("State"))
                    && !incoming.contains("Graph", Tag.TAG_COMPOUND)
                    && displayFrame.contains("Graph", Tag.TAG_COMPOUND)) {
                incoming.put("Graph", displayFrame.getCompound("Graph"));
            }
            displayFrame = incoming;
            lastFrameFingerprint = displayFrame.hashCode();
        }
    }
}
