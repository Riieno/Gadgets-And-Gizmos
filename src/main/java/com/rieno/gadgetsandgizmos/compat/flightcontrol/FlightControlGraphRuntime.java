package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import ace.flight.api.IFCCThruster;
import ace.flight.api.WrenchContributor;
import ace.flight.block.*;
import ace.flight.item.NavigationProfileItem;
import ace.flight.navigation.NavigationProfileDraft;
import ace.flight.navigation.NavigationProfileDefinition;
import ace.flight.physics.AllocationResult;
import ace.flight.redstone.RedstoneControllable;
import ace.flight.redstone.ProxyFace;
import ace.flight.api.IFCCSwivelMount;
import ace.flight.api.IFCCFlapBearing;
import ace.flight.api.IFCCOpticalSensor;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.control.VectorThrustReceiver;
import com.rieno.gadgetsandgizmos.lib.control.ControlOwnerReceiver;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphBlockSettings;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

// Host native controller instances without placing Flight Control's control blocks
public final class FlightControlGraphRuntime implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final AdvancedContraptionControllerBlockEntity host;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, CompoundTag> restored = new HashMap<>();
    private final Map<FlightControlComputerBlockEntity, EngineLinks> engineLinks = new LinkedHashMap<>();
    private volatile List<Entry> physicsEntries = List.of();
    private FlightControlComputerBlockEntity computer;
    private int nextSlot = 1;
    private String status = "Ready";
    private boolean closed;
    private Set<String> availableTypes = Set.of();
    private boolean easyIntegration;
    private List<BlockEntity> physicalComponents = List.of();
    private long lastDiscoveryTick = Long.MIN_VALUE;
    private volatile boolean mousePilotActive;

    public FlightControlGraphRuntime(AdvancedContraptionControllerBlockEntity host){ this.host = host; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reconcile applied nodes, evaluate live settings and run native server ticks
    public void tick(){
        if(closed || host.getLevel() == null || host.isRemoved()) return;
        mousePilotActive = host.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.getServer().getPlayerList().getPlayers().stream().anyMatch(player -> FlightControlIntegration.isInteractionAuthorized(host, player));
        if(lastDiscoveryTick == Long.MIN_VALUE || host.getLevel().getGameTime() - lastDiscoveryTick >= 10L){
            lastDiscoveryTick = host.getLevel().getGameTime();
            var body = SableLevelApi.containing(host);
            physicalComponents = body == null ? List.of() : List.copyOf(SubLevelBlockEntityCollector.getBlockEntities(body));
            Set<String> found = new LinkedHashSet<>();
            for(BlockEntity component : physicalComponents){
                if(component.isRemoved()) continue;
                var block = BuiltInRegistries.BLOCK.getKey(component.getBlockState().getBlock());
                if(!"create_flight_control".equals(block.getNamespace())) continue;
                String type = FlightControlNodes.PREFIX + block.getPath();
                if(FlightControlNodes.isNode(type)) found.add(type);
            }
            boolean easy = found.contains(FlightControlNodes.PREFIX + "creative_flight_control_computer")
                    || CTConfigs.COMMON_SPEC.isLoaded() && CTConfigs.COMMON.easyFlightControlIntegration.get();
            if(!found.equals(availableTypes) || easy != easyIntegration){
                availableTypes = Set.copyOf(found);
                easyIntegration = easy;
                com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync.enqueue(host);
            }
        }
        for(var pair : List.copyOf(entries.entrySet())){
            Entry entry = pair.getValue();
            if(entry.physicalSource != null){
                if(!entry.physicalSource.isRemoved() && host.getLevel().getBlockEntity(entry.physicalSource.getBlockPos()) == entry.physicalSource) continue;
                removeEntry(entries.remove(pair.getKey()));
                continue;
            }
            if(host.getLevel().getBlockEntity(entry.blockEntity.getBlockPos()) == null) continue;
            CompoundTag saved = new CompoundTag();
            saved.putInt("Slot", entry.slot);
            saved.put("State", entry.blockEntity.saveWithoutMetadata(host.getLevel().registryAccess()));
            restored.put(pair.getKey(), saved);
            removeEntry(entries.remove(pair.getKey()));
            entries.put(pair.getKey(), create(pair.getKey(), entry.spec));
        }
        AdvancedGraphDocument graph = host.getActiveGraph();
        Set<String> retained = new LinkedHashSet<>();
        for(AdvancedGraphDocument.Node node : graph.nodes()){
            FlightControlNodes.Spec spec = FlightControlNodes.get(node.type());
            if(spec == null) continue;
            Entry entry = entries.get(node.id());
            BlockEntity physical = physicalComponent(spec, entry);
            if(physical == null && ("creative_flight_control_computer".equals(spec.path())
                    || !easyIntegration && !"hud_projector".equals(spec.path()))){
                removeEntry(entries.remove(node.id()));
                continue;
            }
            retained.add(node.id());
            if(entry != null && entry.physicalSource != physical){
                removeEntry(entry);
                entries.remove(node.id());
                entry = null;
            }
            if(entry != null && !entry.spec.path().equals(spec.path())){
                removeEntry(entry);
                entries.remove(node.id());
                entry = null;
            }
            if(entry == null){
                entry = physical != null && !"hud_projector".equals(spec.path()) ? new Entry(spec, physical, 0) : create(node.id(), spec);
                entry.physicalSource = physical;
                if(physical instanceof WrenchContributor contributor) entry.originalComputer = contributor.getLinkedComputerPos();
                entries.put(node.id(), entry);
            }
        }
        List<String> removed = entries.keySet().stream().filter(id -> !retained.contains(id) && !id.startsWith("$")).toList();
        for(String id : removed) removeEntry(entries.remove(id));
        boolean mouseRequested = false;
        Map<String, GraphValue> hudMouseSettings = null;
        for(AdvancedGraphDocument.Node node : graph.nodes()){
            Entry hud = entries.get(node.id());
            if(hud == null || !(hud.blockEntity instanceof HudProjectorBlockEntity)) continue;
            boolean requested = host.previewGraphInput(graph, node, "mouse_steering").asBoolean()
                    && host.previewGraphInput(graph, node, "enabled").asBoolean();
            mouseRequested |= requested;
            if(requested && hudMouseSettings == null){
                hudMouseSettings = new LinkedHashMap<>();
                for(String port : List.of("mouse_sensitivity", "mouse_turn_speed_degrees", "mouse_turn_mode")){
                    boolean configured = node.data().getCompound("Defaults").contains(port)
                            || graph.edges().stream().anyMatch(edge -> edge.toNode().equals(node.id()) && edge.toPort().equals(port));
                    hudMouseSettings.put(port, configured ? GraphRuntime.toLibraryValue(host.previewGraphInput(graph, node, port))
                            : hud.spec.fields().stream().filter(field -> field.port().equals(port)).findFirst().orElseThrow().defaultValue());
                }
            }
        }
        boolean explicitMouse = entries.entrySet().stream().filter(pair -> !pair.getKey().startsWith("$"))
                .map(pair -> mouseController(pair.getValue()))
                .anyMatch(mouse -> mouse != null && mouse.lastTotalPowered && mouse.mouseViewLockEnabled);
        if(mouseRequested && !explicitMouse && !entries.containsKey("$mouse")){
            Entry mouse = create("$mouse", FlightControlNodes.get(FlightControlNodes.PREFIX + "mouse_flight_controller"));
            MouseFlightControllerBlockEntity component = (MouseFlightControllerBlockEntity) mouse.blockEntity;
            component.setDefaultEnabled(true);
            ((com.rieno.gadgetsandgizmos.mixin.FlightControlMouseAccess) (Object) component).createThrusters$viewLock(true);
            component.setLinkedMouseSeat(host.getBlockPos());
            entries.put("$mouse", mouse);
        }
        if((!mouseRequested || explicitMouse) && entries.containsKey("$mouse")) removeEntry(entries.remove("$mouse"));
        Entry hudMouse = entries.get("$mouse");
        if(hudMouse != null && hudMouse.blockEntity instanceof MouseFlightControllerBlockEntity mouse){
            mouse.applyTorqueDirectly = host.hasShipControlModule();
            if(hudMouseSettings != null && !hudMouse.settings.equals(hudMouseSettings)){
                try{
                    configureHudMouse(mouse, hudMouseSettings);
                    hudMouse.settings = Map.copyOf(hudMouseSettings);
                }catch(ReflectiveOperationException err){ throw new IllegalStateException("Native HUD mouse configuration failed", err); }
            }
        }
        boolean contributors = entries.entrySet().stream().anyMatch(pair -> pair.getValue().blockEntity instanceof WrenchContributor
                && !(host.hasShipControlModule() && "$mouse".equals(pair.getKey())));
        FlightControlComputerBlockEntity selected = entries.entrySet().stream().filter(pair -> !pair.getKey().startsWith("$"))
                .map(pair -> pair.getValue().blockEntity)
                .filter(FlightControlComputerBlockEntity.class::isInstance)
                .map(FlightControlComputerBlockEntity.class::cast).findFirst().orElse(null);
        if(selected != null && entries.containsKey("$computer")) removeEntry(entries.remove("$computer"));
        if(selected == null && contributors){
            var spec = FlightControlNodes.get(FlightControlNodes.PREFIX + "flight_control_computer");
            Entry implicit = entries.get("$computer");
            BlockEntity physical = physicalComponent(spec, implicit);
            if(implicit != null && implicit.physicalSource != physical){
                removeEntry(entries.remove("$computer"));
                implicit = null;
            }
            if(implicit == null){
                implicit = physical == null ? create("$computer", spec) : new Entry(spec, physical, 0);
                implicit.physicalSource = physical;
                entries.put("$computer", implicit);
            }
            selected = (FlightControlComputerBlockEntity) implicit.blockEntity;
            if(physical == null){
                selected.workDryRun = false;
                selected.setWorking(true);
            }
        }
        computer = selected;
        if(!contributors && selected == null && entries.containsKey("$computer")) removeEntry(entries.remove("$computer"));
        publish();
        status = "Ready";
        for(AdvancedGraphDocument.Node node : graph.nodes()){
            Entry entry = entries.get(node.id());
            if(entry == null) continue;
            Map<String, GraphValue> vals = new LinkedHashMap<>();
            for(var field : entry.spec.fields()) vals.put(field.port(), GraphRuntime.toLibraryValue(host.previewGraphInput(graph, node, field.port())));
            for(var action : entry.spec.actions()) vals.put("signal_" + action.id(),
                    GraphRuntime.toLibraryValue(host.previewGraphInput(graph, node, "signal_" + action.id())));
            try{
                Set<String> claims = new LinkedHashSet<>();
                for(var action : entry.spec.actions()){
                    String port = "signal_" + action.id();
                    if(graph.edges().stream().anyMatch(edge -> edge.toNode().equals(node.id()) && edge.toPort().equals(port))
                            || vals.get(port).asBoolean() || vals.get(port).asNumber() > 0) claims.add(action.id());
                }
                if(entry.physicalSource != null){
                    Map<String, GraphValue> defaults = new LinkedHashMap<>();
                    entry.spec.fields().forEach(field -> defaults.put(field.port(), field.defaultValue()));
                    Set<String> wired = graph.edges().stream().filter(edge -> edge.toNode().equals(node.id()))
                            .map(AdvancedGraphDocument.Edge::toPort).collect(java.util.stream.Collectors.toSet());
                    var changes = entry.synchronizer.reconcile(vals, FlightControlBlockSettings.read(entry.physicalSource, entry.spec), defaults, wired);
                    vals.putAll(changes.graphUpdates());
                    if(!changes.graphUpdates().isEmpty()) host.updateNativeGraphSettings(node.id(), node.type(), changes.graphUpdates());
                    if(entry.physicalSource != entry.blockEntity && !changes.blockUpdates().isEmpty()){
                        Entry physicalEntry = new Entry(entry.spec, entry.physicalSource, 0);
                        configure(physicalEntry, vals, Set.of());
                    }
                }
                configure(entry, vals, claims);
            }catch(ReflectiveOperationException | IllegalArgumentException err){ status = FlightControlNodes.title(entry.spec.path()) + ": " + err.getMessage(); }
        }
        for(Entry entry : entries.values()){
            if(entry.blockEntity instanceof DistanceCouplerBlockEntity distance && host.getLevel().getGameTime() % 10L == 0L){
                configureSensors(distance);
            }
            if(entry.blockEntity instanceof ForwardIndicatorBlockEntity && !entry.settings.isEmpty()){
                try{ configureSpecial(entry, entry.settings); }
                catch(ReflectiveOperationException err){ throw new IllegalStateException("Native forward configuration failed", err); }
            }
        }
        linkControllers();
        publish();
        if(host.hasShipControlModule() || host.isShipControlInitializing()){
            for(var pair : engineLinks.entrySet()){
                EngineLinks links = pair.getValue();
                if(!links.engines.isEmpty() || !links.mounts.isEmpty() || !links.flaps.isEmpty()) releaseEngines(pair.getKey());
            }
            if(!host.isShipControlInitializing() && host.getLevel().getGameTime() % 10L == 0L){
                for(Entry entry : entries.values()){
                    if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer){
                        synchronizeScmGeometry(nativeComputer);
                    }
                }
            }
        }
        else if(host.getLevel().getGameTime() % 10L == 0L){
            for(Entry entry : List.copyOf(entries.values())){
                if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer){
                    if(nativeComputer.ctrlActiveCount > 0) discoverEngines(nativeComputer);
                    else releaseEngines(nativeComputer);
                }
            }
        }
        for(Entry entry : entries.values()){
            if(entry.physicalSource != entry.blockEntity) nativeTick(entry.blockEntity);
        }
        for(Entry entry : entries.values()){
            try{
                if(entry.blockEntity instanceof NavigationTerminalBlockEntity terminal){
                    synchronizeProfile(entry, terminal);
                    configureTerminal(entry, terminal);
                }
                if(entry.blockEntity instanceof NavigationProfileBuilderBlockEntity builder) configureProfile(entry, builder);
                if(entry.blockEntity instanceof FlightControlRedstoneProxyBlockEntity) configureProxy(entry);
            }catch(IllegalArgumentException err){ status = FlightControlNodes.title(entry.spec.path()) + ": " + err.getMessage(); }
        }
        publish();
        physicsEntries = List.copyOf(entries.values());
        if(!entries.isEmpty() && host.getLevel().getGameTime() % 10L == 0L){
            com.rieno.gadgetsandgizmos.lib.network.BlockEntityDataSync.enqueue(host);
        }
    }

    // Run detached physics actors once, under the real ACC's Sable callback
    public void physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double dt){
        if(closed || host.isRemoved()) return;
        List<Entry> snapshot = physicsEntries;
        HostedBlockEntities.physicsTick(host, subLevel, handle, dt,
                component -> snapshot.stream().anyMatch(entry -> entry.blockEntity == component));
    }

    // Refresh the native mouse controller's direction using authenticated ACC interaction
    public List<MouseFlightControllerBlockEntity> mouseControllers(){
        List<MouseFlightControllerBlockEntity> res = new ArrayList<>();
        for(Entry entry : physicsEntries){
            MouseFlightControllerBlockEntity mouse = mouseController(entry);
            if(mouse != null) res.add(mouse);
        }
        return List.copyOf(res);
    }

    // Use the same powered, view-locked controller for client aiming and server pilot intent
    public MouseFlightControllerBlockEntity pilotMouse(){
        return mouseControllers().stream().filter(mouse -> !mouse.isRemoved()
                && mouse.lastTotalPowered && mouse.mouseViewLockEnabled).findFirst().orElse(null);
    }

    public boolean hasMouseSteering(){
        return !mouseControllers().isEmpty();
    }

    public boolean nodeAvailable(String type){
        if((FlightControlNodes.PREFIX + "creative_flight_control_computer").equals(type)) return availableTypes.contains(type);
        return "createthrusters:flight_control_hud_projector".equals(type) || easyIntegration || availableTypes.contains(type);
    }

    // Mouse stabilization is requested only by an interacting pilot or an active graph signal
    public boolean mouseRequested(MouseFlightControllerBlockEntity mouse){
        if(mousePilotActive && pilotMouse() == mouse) return true;
        return physicsEntries.stream().anyMatch(entry -> (entry.blockEntity == mouse
                || entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative && creative.getFlightTerminal() == mouse)
                && entry.mouseSignalActive);
    }

    // Forward explicit native rotation intent without turning ordinary navigation yaw into banking
    public Set<String> rotationActions(BlockEntity nativeComputer){
        Set<String> res = new LinkedHashSet<>();
        for(Entry entry : physicsEntries){
            if(entry.enabled && entry.blockEntity instanceof WrenchContributor contributor
                    && contributor.isLinkedTo(nativeComputer.getBlockPos()) && contributor.isContributing()){
                res.addAll(FlightControlRequests.rotationActions(entry.blockEntity));
            }
        }
        return Set.copyOf(res);
    }

    // Export native state without applying controls while the graph is inspected
    public Map<String, GraphValue> outputs(String nodeId){
        Entry entry = entries.get(nodeId);
        if(entry == null) return Map.of("available", GraphValue.bool(false), "status", GraphValue.string("Unavailable"));
        CompoundTag nativeState = entry.blockEntity.getUpdateTag(host.getLevel().registryAccess());
        Map<String, GraphValue> telemetry = new LinkedHashMap<>();
        for(String key : nativeState.getAllKeys()){
            Tag val = nativeState.get(key);
            if(val instanceof net.minecraft.nbt.NumericTag number) telemetry.put(key, GraphValue.number(number.getAsDouble()));
            else if(val instanceof net.minecraft.nbt.StringTag string) telemetry.put(key, GraphValue.string(string.getAsString()));
        }
        if(entry.blockEntity instanceof ace.flight.observation.ObservableSource source){
            source.captureObservations().values().forEach((key, val) -> telemetry.put(key, GraphValue.of(val)));
        }
        boolean active = entry.enabled;
        if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer){
            active = nativeComputer.working;
            AllocationResult allocation = nativeComputer.getLastResult();
            if(allocation != null){
                telemetry.put("actual_force", vectorValue(allocation.actualForce()));
                telemetry.put("actual_torque", vectorValue(allocation.actualTorque()));
            }
        }else if(entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative) active = creative.isComputerEnabled();
        else if(entry.blockEntity instanceof MouseFlightControllerBlockEntity mouse) active = mouse.lastTotalPowered;
        if(entry.blockEntity instanceof NavigationTerminalBlockEntity terminal){
            active = terminal.isEffectivelyEnabled();
            var task = terminal.getTaskSnapshot();
            telemetry.put("task_phase", GraphValue.string(task.phase().name().toLowerCase(Locale.ROOT)));
            telemetry.put("block_reason", GraphValue.string(task.blockReason().name().toLowerCase(Locale.ROOT)));
            telemetry.put("detail", GraphValue.string(task.detail()));
            telemetry.put("afn_channels", GraphValue.list(terminal.getEditorSnapshot().afnChannels().stream().map(channel -> Map.of(
                    "id", channel.id().toString(), "name", channel.name(), "value_kind", channel.valueKind().name().toLowerCase(Locale.ROOT),
                    "referenced", channel.referenced(), "infinite_range", channel.infiniteRange(), "authenticated", channel.authenticationReady())).toList()));
        }
        return Map.of("available", GraphValue.bool(true), "active", GraphValue.bool(active),
                "component", GraphValue.string(nodeId), "telemetry", GraphValue.map(telemetry),
                "profile", GraphValue.map(Map.of("component", GraphValue.string(nodeId))), "status", GraphValue.string(status));
    }

    // Save native state inside the owning ACC's persistence and synchronization packet
    public CompoundTag save(){
        CompoundTag tag = new CompoundTag();
        ListTag available = new ListTag();
        availableTypes.forEach(type -> available.add(net.minecraft.nbt.StringTag.valueOf(type)));
        tag.put("AvailableTypes", available);
        tag.putBoolean("EasyIntegration", easyIntegration);
        ListTag components = new ListTag();
        for(var pair : entries.entrySet()){
            if(pair.getKey().startsWith("$scm:")) continue;
            Entry entry = pair.getValue();
            CompoundTag component = new CompoundTag();
            component.putString("Id", pair.getKey());
            component.putString("Type", FlightControlNodes.PREFIX + entry.spec.path());
            component.putInt("Slot", entry.slot);
            if(entry.physicalSource != null) component.putLong("PhysicalPos", entry.physicalSource.getBlockPos().asLong());
            component.putBoolean("Enabled", entry.enabled);
            component.put("State", entry.blockEntity.saveWithoutMetadata(host.getLevel().registryAccess()));
            component.put("Display", entry.blockEntity.getUpdateTag(host.getLevel().registryAccess()));
            component.put("BlockState", net.minecraft.nbt.NbtUtils.writeBlockState(entry.blockEntity.getBlockState()));
            components.add(component);
        }
        tag.put("Components", components);
        tag.putString("Status", status);
        return tag;
    }

    // Restore settings lazily on the server and detached display snapshots on the client
    public void load(CompoundTag tag){
        Set<String> available = new LinkedHashSet<>();
        tag.getList("AvailableTypes", Tag.TAG_STRING).forEach(val -> available.add(val.getAsString()));
        availableTypes = Set.copyOf(available);
        easyIntegration = tag.getBoolean("EasyIntegration");
        if(tag.contains("Status", Tag.TAG_STRING)) status = tag.getString("Status");
        for(Tag raw : tag.getList("Components", Tag.TAG_COMPOUND)){
            if(!(raw instanceof CompoundTag component)) continue;
            String id = component.getString("Id");
            FlightControlNodes.Spec spec = FlightControlNodes.get(component.getString("Type"));
            if(spec == null) continue;
            restored.put(id, component.copy());
            if(host.getLevel() != null && host.getLevel().isClientSide){
                Entry entry = entries.get(id);
                BlockEntity physical = component.contains("PhysicalPos") ? host.getLevel().getBlockEntity(BlockPos.of(component.getLong("PhysicalPos"))) : null;
                if(entry != null && (!entry.spec.path().equals(spec.path())
                        || entry.physicalSource != physical || entry.blockEntity.isRemoved())){
                    removeEntry(entry);
                    entries.remove(id);
                    entry = null;
                }
                if(entry == null){
                    entry = physical != null && !"hud_projector".equals(spec.path()) ? new Entry(spec, physical, 0) : create(id, spec);
                    entries.put(id, entry);
                }
                entry.physicalSource = physical;
                if(component.contains("BlockState", Tag.TAG_COMPOUND)) entry.blockEntity.setBlockState(net.minecraft.nbt.NbtUtils.readBlockState(
                        host.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), component.getCompound("BlockState")));
                entry.enabled = component.getBoolean("Enabled");
                entry.blockEntity.handleUpdateTag(component.getCompound("Display"), host.getLevel().registryAccess());
                if(entry.blockEntity instanceof HudProjectorBlockEntity hud && entry.physicalSource != hud){
                    hud.setBlockState(hud.getBlockState().setValue(HudProjectorBlock.FACING, net.minecraft.core.Direction.NORTH));
                }
            }
        }
        if(host.getLevel() != null && host.getLevel().isClientSide){
            Set<String> ids = new LinkedHashSet<>();
            tag.getList("Components", Tag.TAG_COMPOUND).forEach(raw -> ids.add(((CompoundTag) raw).getString("Id")));
            for(String id : entries.keySet().stream().filter(id -> !ids.contains(id)).toList()) removeEntry(entries.remove(id));
            publish();
            physicsEntries = List.copyOf(entries.values());
        }
    }

    // Expose enabled native projectors to the client renderer
    public List<BlockEntity> displays(){
        return entries.values().stream().filter(entry -> entry.enabled
                && (entry.blockEntity instanceof HudProjectorBlockEntity || entry.blockEntity instanceof AttitudeDisplayBlockEntity
                || entry.blockEntity instanceof AerodynamicTrailCreatorBlockEntity || entry.blockEntity instanceof NavigationTerminalBlockEntity))
                .map(entry -> entry.blockEntity).toList();
    }

    @Override
    public void close(){
        if(closed) return;
        closed = true;
        physicsEntries = List.of();
        releaseEngines();
        try{
            for(Entry entry : List.copyOf(entries.values())) removeEntry(entry);
            entries.clear();
        }finally{ HostedBlockEntities.remove(host); BlockEntityBindings.remove(host); FlightControlRequests.remove(host); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Allocate a stable component position near the ACC without replacing a world block
    private Entry create(String id, FlightControlNodes.Spec spec){
        CompoundTag saved = restored.get(id);
        int slot = saved == null ? allocateSlot() : Math.max(1, saved.getInt("Slot"));
        BlockPos savedPos = componentPosition(slot);
        if(saved != null && (host.getLevel().isOutsideBuildHeight(savedPos) || !HostedBlockEntities.positionAvailable(host, savedPos)
                || entries.values().stream().anyMatch(entry -> entry.blockEntity.getBlockPos().equals(savedPos)))) slot = allocateSlot();
        nextSlot = Math.max(nextSlot, slot + 1);
        BlockPos pos = componentPosition(slot);
        BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create_flight_control", spec.path())).defaultBlockState();
        if(state.isAir()) throw new IllegalStateException("Missing native block " + spec.path());
        try{
            BlockEntity component = (BlockEntity) Class.forName(spec.className())
                    .getConstructor(BlockPos.class, BlockState.class).newInstance(pos, state);
            component.setLevel(host.getLevel());
            if(saved != null) component.loadWithComponents(saved.getCompound("State"), host.getLevel().registryAccess());
            if(component instanceof FlightControlComputerBlockEntity nativeComputer) nativeComputer.clearControllers();
            if(component instanceof CreativeFlightControlComputerBlockEntity creative) creative.unlinkAllNavigationTerminals();
            Entry entry = new Entry(spec, component, slot);
            if(component instanceof RedstoneControllable controllable){
                for(int idx = 1; idx <= controllable.getMaxProxyLinks(); idx++){
                    if(controllable.getLinkedProxyPos(idx) != null) controllable.notifyProxyLink(false, 0,
                            controllable.getLinkedProxyId(idx), controllable.getLinkedProxyPos(idx));
                }
            }
            return entry;
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Cannot host " + spec.path(), err); }
    }

    private void publish(){
        HostedBlockEntities.publish(host, entries.values().stream().filter(entry -> entry.physicalSource != entry.blockEntity).map(entry -> entry.blockEntity).toList());
        Set<BlockEntity> bound = new LinkedHashSet<>();
        entries.values().stream().map(entry -> entry.physicalSource).filter(java.util.Objects::nonNull).forEach(bound::add);
        entries.values().stream().map(entry -> entry.proxyTarget).filter(val -> val instanceof BlockEntity)
                .map(val -> (BlockEntity) val).filter(physicalComponents::contains).forEach(bound::add);
        for(Entry entry : entries.values()){
            if(!(entry.physicalSource instanceof WrenchContributor contributor)) continue;
            BlockPos linked = contributor.getLinkedComputerPos();
            BlockEntity owner = linked == null ? null : host.getLevel().getBlockEntity(linked);
            if(owner == null || !physicalComponents.contains(owner)
                    || BlockEntityBindings.host(owner) != null && BlockEntityBindings.host(owner) != host) continue;
            if(owner instanceof FlightControlComputerBlockEntity || owner instanceof CreativeFlightControlComputerBlockEntity) bound.add(owner);
        }
        BlockEntityBindings.replace(host, bound);
    }

    // Select a matching block on this sublevel, retaining existing bindings between ticks
    private BlockEntity physicalComponent(FlightControlNodes.Spec spec, Entry current){
        var reserved = entries.values().stream().filter(entry -> entry != current)
                .map(entry -> entry.physicalSource).filter(java.util.Objects::nonNull).toList();
        var type = ResourceLocation.fromNamespaceAndPath("create_flight_control", spec.path());
        return BlockEntityBindings.select(host, physicalComponents, current == null ? null : current.physicalSource,
                reserved, component -> BuiltInRegistries.BLOCK.getKey(component.getBlockState().getBlock()).equals(type));
    }

    private BlockPos componentPosition(int slot){
        return HostedBlockEntities.positionForSlot(host, slot, 8);
    }

    private int allocateSlot(){
        int slot = HostedBlockEntities.findAvailableSlot(host, nextSlot, 8,
                entries.values().stream().map(entry -> entry.blockEntity.getBlockPos()).toList());
        nextSlot = slot + 1;
        return slot;
    }

    private void removeEntry(Entry entry){
        if(entry == null) return;
        FlightControlRequests.remove(entry.blockEntity);
        if(entry.physicalSource == entry.blockEntity){
            releaseProxy(entry);
            if(entry.blockEntity instanceof WrenchContributor contributor && !java.util.Objects.equals(entry.originalComputer, contributor.getLinkedComputerPos())){
                BlockPos linked = contributor.getLinkedComputerPos();
                BlockEntity owner = linked == null ? null : HostedBlockEntities.resolve(host.getLevel(), linked);
                if(owner == null || HostedBlockEntities.host(owner) != host) return;
                if(owner instanceof FlightControlComputerBlockEntity nativeComputer) nativeComputer.removeControllerAt(entry.blockEntity.getBlockPos());
                if(owner instanceof CreativeFlightControlComputerBlockEntity creative) creative.removeControllerAt(entry.blockEntity.getBlockPos());
                if(entry.originalComputer == null) contributor.clearLinkedComputer();
                else contributor.setLinkedComputer(entry.blockEntity.getBlockPos(), entry.originalComputer);
            }
            return;
        }
        if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer){
            releaseEngines(nativeComputer);
            engineLinks.remove(nativeComputer);
        }
        releaseProxy(entry);
        if(entry.blockEntity instanceof WrenchContributor contributor){
            BlockPos linked = contributor.getLinkedComputerPos();
            BlockEntity owner = linked == null ? null : HostedBlockEntities.resolve(host.getLevel(), linked);
            if(owner instanceof FlightControlComputerBlockEntity nativeComputer) nativeComputer.removeControllerAt(entry.blockEntity.getBlockPos());
            if(owner instanceof CreativeFlightControlComputerBlockEntity creative) creative.removeControllerAt(entry.blockEntity.getBlockPos());
            contributor.clearLinkedComputer();
        }
        if(entry.blockEntity instanceof SmartBlockEntity smart){ smart.onChunkUnloaded(); smart.remove(); }
        else if(entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative) creative.getAfnBroadcaster().detach();
        entry.blockEntity.setRemoved();
    }

    // Apply native setters only when settings change so PD targets are retained between ticks
    private void configure(Entry entry, Map<String, GraphValue> vals, Set<String> claims) throws ReflectiveOperationException{
        entry.enabled = vals.getOrDefault("enabled", GraphValue.bool(true)).asBoolean();
        Map<String, GraphValue> settings = new LinkedHashMap<>();
        entry.spec.fields().forEach(field -> settings.put(field.port(), vals.get(field.port())));
        if(!entry.settings.equals(settings)){
            applyConfig(entry.blockEntity, entry.spec, vals, "");
            configureSpecial(entry, vals);
            entry.settings = Map.copyOf(settings);
        }
        if(entry.blockEntity instanceof MouseFlightControllerBlockEntity mouse){
            mouse.setLinkedMouseSeat(host.getBlockPos());
        }
        if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer) nativeComputer.setWorking(entry.enabled);
        if(entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative) creative.setComputerDefaultEnabled(entry.enabled);
        if(entry.blockEntity instanceof RedstoneControllable controllable){
            bindSignals(entry, controllable, claims);
            for(var action : entry.spec.actions()){
                GraphValue val = vals.get("signal_" + action.id());
                int strength = "boolean".equals(action.type()) ? (val.asBoolean() ? 15 : 0)
                        : (int) Math.clamp(Math.round(val.asNumber()), 0, 15);
                int prev = entry.signals.getOrDefault(action.id(), 0);
                if(strength != prev && claims.contains(action.id())){
                    controllable.applyRedstoneSignal(action.id(), strength > 0, prev == 0 && strength > 0, strength,
                            entry.proxyId, entry.blockEntity.getBlockPos(), ProxyFace.FRONT);
                }
                entry.signals.put(action.id(), strength);
            }
        }
        entry.mouseSignalActive = entry.signals.values().stream().anyMatch(val -> val > 0);
    }

    private void configureSpecial(Entry entry, Map<String, GraphValue> vals) throws ReflectiveOperationException{
        if(entry.blockEntity instanceof FlightControlComputerBlockEntity nativeComputer){
            nativeComputer.setMode("debug".equals(vals.get("mode").asString()) ? 1 : 0);
            nativeComputer.debugTargetIndex = (int) Math.clamp(Math.round(vals.get("debug_target_index").asNumber()), 0,
                    FlightControlComputerBlockEntity.DEBUG_UNIT_TARGETS.length - 1);
            nativeComputer.debugApplyForces = vals.get("debug_apply_forces").asBoolean();
            if(vals.get("run_debug_tests").asBoolean() && !entry.settings.getOrDefault("run_debug_tests", GraphValue.bool(false)).asBoolean()){
                nativeComputer.runAllTestsRequested = true;
            }
            nativeComputer.workDryRun = vals.get("work_dry_run").asBoolean();
            EngineLinks links = engineLinks.computeIfAbsent(nativeComputer, key -> new EngineLinks());
            links.gravity = vals.get("consider_gravity").asBoolean();
            nativeComputer.considerGravity = links.gravity;
            nativeComputer.setSolverIterations((int) vals.get("solver_iterations").asNumber());
            nativeComputer.setAirframeMode(ace.flight.physics.FccAirframeMode.valueOf(vals.get("airframe_mode").asString().toUpperCase(Locale.ROOT)));
            nativeComputer.setAllocationPriority(ace.flight.physics.AllocationPriority.valueOf(vals.get("allocation_priority").asString().toUpperCase(Locale.ROOT)));
            nativeComputer.setFixedWingClimbDegrees((int) vals.get("fixed_wing_climb_degrees").asNumber());
            nativeComputer.setFixedWingStabilizationDistance((int) vals.get("fixed_wing_stabilization_distance").asNumber());
            nativeComputer.setFixedWingGlideDegrees((int) vals.get("fixed_wing_glide_degrees").asNumber());
            nativeComputer.setFixedWingFlightHeight(true, (int) vals.get("fixed_wing_takeoff_height").asNumber());
            nativeComputer.setFixedWingFlightHeight(false, (int) vals.get("fixed_wing_approach_height").asNumber());
            nativeComputer.setSonicBoomSettings(vals.get("sonic_boom_enabled").asBoolean(),
                    vals.get("sonic_boom_speed_threshold").asNumber(), vals.get("sonic_boom_volume_multiplier").asNumber());
        }
        if(entry.blockEntity instanceof MouseFlightControllerBlockEntity mouse){
            ((com.rieno.gadgetsandgizmos.mixin.FlightControlMouseAccess) (Object) mouse)
                    .createThrusters$viewLock(vals.get("mouse_view_lock").asBoolean());
        }
        if(entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative){
            MouseFlightControllerBlockEntity terminal = creative.getFlightTerminal();
            FlightControlNodes.Spec mouse = FlightControlNodes.get(FlightControlNodes.PREFIX + "mouse_flight_controller");
            applyConfig(terminal, mouse, vals, "mouse_");
            terminal.setLinkedMouseSeat(host.getBlockPos());
            ((com.rieno.gadgetsandgizmos.mixin.FlightControlMouseAccess) (Object) terminal)
                    .createThrusters$viewLock(vals.get("mouse_mouse_view_lock").asBoolean());
            creative.setFixedWingConfig(new ace.flight.navigation.CreativeFixedWingConfig(
                    vals.get("fixed_wing_enabled").asBoolean(), vals.get("fixed_wing_minimum_speed").asNumber(),
                    vals.get("fixed_wing_rotation_speed").asNumber(), (int) vals.get("fixed_wing_climb_degrees").asNumber(),
                    (int) vals.get("fixed_wing_takeoff_height").asNumber(), (int) vals.get("fixed_wing_approach_height").asNumber(),
                    (int) vals.get("fixed_wing_glide_degrees").asNumber(), (int) vals.get("fixed_wing_stabilization_distance").asNumber(),
                    vals.get("fixed_wing_simulate_lift").asBoolean(), vals.get("fixed_wing_minimum_turn_radius").asNumber()));
        }
        if(entry.blockEntity instanceof ForwardIndicatorBlockEntity indicator){
            var facing = ace.flight.control.BodyForwardFacing.fromSerializedName(vals.get("body_forward_facing").asString());
            BlockState state = indicator.getBlockState().setValue(ForwardIndicatorBlock.FACING, facing.direction());
            if(state != indicator.getBlockState()){
                if(entry.physicalSource == indicator) host.getLevel().setBlock(indicator.getBlockPos(), state, 3);
                else indicator.setBlockState(state);
            }
            indicator.setRecordedFacing(facing);
            Set<BlockPos> selected = new LinkedHashSet<>();
            for(BlockEntity component : coordinatedComponents()){
                if(component instanceof ForwardIndicatorLinkTarget link){
                    selected.add(component.getBlockPos());
                    if(entry.enabled) link.applyForwardIndicatorFacing(facing);
                }
            }
            for(BlockPos pos : List.copyOf(indicator.getLinkedTargetWorldPositions())){
                if(!selected.contains(pos)) indicator.removeLinkedTarget(pos);
            }
            for(BlockPos pos : selected) indicator.addLinkedTarget(pos);
        }
        if(entry.blockEntity instanceof NavigationTerminalBlockEntity terminal){
            terminal.setDefaultEnabled(terminal.getConfigRevision(), entry.enabled);

        }
        if(entry.blockEntity instanceof DistanceCouplerBlockEntity distance) configureSensors(distance);
        if(entry.blockEntity instanceof AerodynamicTrailCreatorBlockEntity trail){
            var access = (com.rieno.gadgetsandgizmos.mixin.FlightControlTrailAccess) (Object) trail;
            access.createThrusters$moduleColor((int) vals.get("module_color").asNumber());
            access.createThrusters$length((int) Math.round(vals.get("length").asNumber() * 10));
            access.createThrusters$thickness((int) Math.round(vals.get("thickness").asNumber() * 10));
            access.createThrusters$minSpeed((int) Math.round(vals.get("min_speed").asNumber() * 10));
            access.createThrusters$density((int) vals.get("density_percent").asNumber());
            access.createThrusters$color((int) vals.get("red").asNumber(), (int) vals.get("green").asNumber(),
                    (int) vals.get("blue").asNumber(), (int) vals.get("alpha").asNumber());
            access.createThrusters$enabled(entry.enabled);
        }
        if(entry.blockEntity instanceof ace.flight.compat.afn.FccAfnHost nativeHost){
            var afn = nativeHost.getAfnBroadcaster();
            var frequency = vals.get("afn_frequency");
            ItemStack first = FlightControlProfiles.frequencyItem(frequency.member("first"), host.getLevel().registryAccess());
            ItemStack second = FlightControlProfiles.frequencyItem(frequency.member("second"), host.getLevel().registryAccess());
            if(!ItemStack.matches(afn.getAfnFrequencyItems().getItem(0), first)) afn.getAfnFrequencyItems().setItem(0, first);
            if(!ItemStack.matches(afn.getAfnFrequencyItems().getItem(1), second)) afn.getAfnFrequencyItems().setItem(1, second);
            afn.applyAfnBroadcastEnabled(afn.getAfnBroadcastConfigRevision(), vals.get("afn_broadcast_enabled").asBoolean());
            afn.applyAfnBroadcastPose(afn.getAfnBroadcastConfigRevision(), vals.get("afn_broadcast_pose").asBoolean());
            if(afn.isAfnBroadcastInfiniteRange() != vals.get("afn_infinite_range").asBoolean()){
                if(!afn.applyAfnBroadcastInfiniteRange(afn.getAfnBroadcastConfigRevision(), vals.get("afn_infinite_range").asBoolean(),
                        FlightControlIntegration.author(host))) throw new IllegalArgumentException("Native AFN range configuration was rejected");
            }
        }
    }

    // Share the native mouse law while exposing turn limits and banking through the HUD node
    private static void configureHudMouse(MouseFlightControllerBlockEntity mouse, Map<String, GraphValue> vals)
            throws ReflectiveOperationException{
        var spec = FlightControlNodes.get(FlightControlNodes.PREFIX + "mouse_flight_controller");
        Map<String, GraphValue> settings = new LinkedHashMap<>();
        spec.fields().forEach(field -> settings.put(field.port(), field.defaultValue()));
        settings.put("enabled", GraphValue.bool(true));
        settings.put("mouse_sensitivity", GraphValue.number(Math.clamp(vals.get("mouse_sensitivity").asNumber(), 0.1D, 8.0D) * 0.12D));
        double rate = Math.toRadians(Math.clamp(vals.get("mouse_turn_speed_degrees").asNumber(), 0.0D, 720.0D));
        for(String port : List.of("max_pitch_velocity", "max_yaw_velocity", "max_roll_velocity")) settings.put(port, GraphValue.number(rate));
        settings.put("max_bank_degrees", GraphValue.number("pitch_yaw".equals(vals.get("mouse_turn_mode").asString()) ? 0 : 65));
        applyConfig(mouse, spec, settings, "");
    }

    // Use the longest native setter shared by the real block GUI and its hosted instance
    private static void applyConfig(BlockEntity component, FlightControlNodes.Spec spec,
            Map<String, GraphValue> vals, String prefix) throws ReflectiveOperationException{
        if(spec.config().isEmpty()) return;
        Method setter = null;
        for(Method method : component.getClass().getMethods()){
            if(method.getName().equals("setConfig") && method.getParameterCount() == spec.config().size()) setter = method;
        }
        if(setter == null) throw new IllegalArgumentException("Unavailable native config setter " + spec.path());
        Object[] args = new Object[setter.getParameterCount()];
        for(int idx = 0; idx < args.length; idx++){
            String name = spec.config().get(idx);
            var field = spec.fields().stream().filter(candidate -> name.equals(candidate.name())).findFirst().orElseThrow();
            args[idx] = argument(vals.get(prefix + field.port()), setter.getParameterTypes()[idx]);
        }
        setter.invoke(component, args);
    }

    // Preserve native task transitions, AFN authorization and navigation output rules
    private void configureTerminal(Entry entry, NavigationTerminalBlockEntity terminal){
        String action = entry.settings.get("task_action").asString();
        if(!action.equals(entry.taskAction)){
            entry.taskAction = action;
            if(!"none".equals(action)){
                var res = terminal.requestAction(ace.flight.navigation.NavigationTaskAction.valueOf(action.toUpperCase(Locale.ROOT)));
                if(!res.accepted()) status = "Native navigation action: " + res.status();
            }
        }
        Map<String, GraphValue> settings = Map.of("auto", entry.settings.get("afn_auto_infinite_range"),
                "ranges", entry.settings.get("afn_channel_ranges"), "redstone", entry.settings.get("redstone_outputs"));
        if(settings.equals(entry.terminalSettings)) return;
        boolean automatic = settings.get("auto").asBoolean();
        if(terminal.getEditorSnapshot().autoAfnInfiniteRange() != automatic){
            checkTerminalConfig(terminal.setAfnAutoInfiniteRange(terminal.getConfigRevision(), automatic, FlightControlIntegration.author(host)));
        }
        if(!automatic && settings.get("ranges").value() instanceof Map<?, ?> ranges){
            for(var channel : ranges.entrySet()){
                UUID id = UUID.fromString(channel.getKey().toString());
                checkTerminalConfig(terminal.setAfnInfiniteRange(terminal.getConfigRevision(), id,
                        GraphValue.of(channel.getValue()).asBoolean(), FlightControlIntegration.author(host)));
            }
        }
        List<ace.flight.navigation.redstone.NavigationRedstoneRule> rules = new ArrayList<>(
                ace.flight.navigation.redstone.NavigationRedstoneOutput.defaults());
        for(GraphValue val : settings.get("redstone").entries()){
            var kind = ace.flight.navigation.redstone.NavigationRedstoneRule.Kind.valueOf(val.member("kind").asString().toUpperCase(Locale.ROOT));
            var rule = new ace.flight.navigation.redstone.NavigationRedstoneRule(kind,
                    (int) val.member("waypoint").asNumber(), (int) val.member("threshold").asNumber(),
                    FlightControlProfiles.frequencyItem(val.member("first"), host.getLevel().registryAccess()),
                    FlightControlProfiles.frequencyItem(val.member("second"), host.getLevel().registryAccess()));
            if(kind == ace.flight.navigation.redstone.NavigationRedstoneRule.Kind.PROGRESS) rules.add(rule);
            else rules.set(kind.ordinal(), rule);
        }
        if(!terminal.redstoneOutput().replace(terminal.redstoneOutput().revision(), rules)){
            throw new IllegalArgumentException("Native navigation redstone configuration was rejected");
        }
        entry.terminalSettings = settings;
    }

    private static void checkTerminalConfig(NavigationTerminalBlockEntity.ConfigResult res){
        if(res.status() != NavigationTerminalBlockEntity.ConfigResult.Status.APPLIED){
            throw new IllegalArgumentException("Native AFN configuration: " + res.reason());
        }
    }

    // Bind graph signals to native proxy identities and retain native action ownership
    private void bindSignals(Entry source, RedstoneControllable target, Set<String> claims){
        if(source.claims.equals(claims) && source.proxyTarget == target) return;
        releaseProxy(source);
        if(claims.isEmpty()) return;
        Set<String> supported = target.getRedstoneActions().stream().map(ace.flight.redstone.RedstoneAction::id)
                .collect(java.util.stream.Collectors.toSet());
        if(!supported.containsAll(claims)) throw new IllegalArgumentException("Unsupported native action");
        if(!target.acceptsProxy(source.proxyId, source.blockEntity.getBlockPos())) throw new IllegalArgumentException("Native proxy slots are occupied");
        target.notifyProxyLink(true, 1, source.proxyId, source.blockEntity.getBlockPos());
        Set<String> occupied = target.getProxyClaimedActionsExcluding(source.proxyId, source.blockEntity.getBlockPos());
        if(claims.stream().anyMatch(occupied::contains)){
            target.notifyProxyLink(false, 0, source.proxyId, source.blockEntity.getBlockPos());
            throw new IllegalArgumentException("Native actions are already claimed");
        }
        target.updateProxyActionClaims(source.proxyId, source.blockEntity.getBlockPos(), claims);
        source.claims = Set.copyOf(claims);
        source.proxyTarget = target;
        source.signals.clear();
    }

    private void releaseProxy(Entry source){
        if(source.proxyTarget != null){
            source.proxyTarget.notifyProxyLink(false, 0, source.proxyId, source.blockEntity.getBlockPos());
            source.proxyTarget = null;
        }
        source.claims = Set.of();
        source.signals.clear();
    }

    // Use the native controller's link validation and optical sensor aggregation
    private void configureSensors(DistanceCouplerBlockEntity distance){
        Set<BlockPos> sensors = new LinkedHashSet<>();
        Set<BlockPos> mounts = new LinkedHashSet<>();
        for(BlockEntity component : physicalComponents){
            if(component.isRemoved() || BlockEntityBindings.host(component) != null && BlockEntityBindings.host(component) != host) continue;
            if(component instanceof IFCCOpticalSensor) sensors.add(component.getBlockPos());
            else if(component instanceof IFCCSwivelMount) mounts.add(component.getBlockPos());
        }
        for(BlockPos pos : distance.getLinkedSensorWorldPositions()) if(!sensors.contains(pos)) distance.toggleSensor(pos);
        for(BlockPos pos : distance.getLinkedSensorMountWorldPositions()) if(!mounts.contains(pos)) distance.toggleSensorMount(pos);
        for(BlockPos pos : sensors) if(!distance.isSensorLinked(pos)) distance.toggleSensor(pos);
        for(BlockPos pos : mounts) if(!distance.isSensorMountLinked(pos)) distance.toggleSensorMount(pos);
    }

    // Reconcile a native profile card whenever its source definition changes
    private void synchronizeProfile(Entry entry, NavigationTerminalBlockEntity terminal){
        GraphValue selected = entry.settings.getOrDefault("profile", GraphValue.map(Map.of()));
        String id = selected.member("component").asString();
        if(!selected.member("item").asString().isBlank()){
            if(selected.equals(entry.profileValue)) return;
            ItemStack card = FlightControlProfiles.frequencyItem(selected, host.getLevel().registryAccess());
            terminal.getDepotBehaviour().setHeldItem(card.isEmpty() ? null : new TransportedItemStack(card));
            terminal.notifyExternalProfileSlotMutation();
            entry.profileValue = selected;
            return;
        }
        if(entry.physicalSource == terminal && id.isBlank()) return;
        Entry source = entries.get(id);
        NavigationProfileDefinition definition = source != null && source.blockEntity instanceof NavigationProfileBuilderBlockEntity builder
                ? builder.getTemplateDefinition() : null;
        if(entry.profileInitialized && java.util.Objects.equals(definition, entry.profile)) return;
        entry.profileInitialized = true;
        ItemStack card = ItemStack.EMPTY;
        if(definition != null){
            card = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create_flight_control", "navigation_profile")));
            NavigationProfileItem.writeDefinition(card, definition);
        }
        terminal.getDepotBehaviour().setHeldItem(card.isEmpty() ? null : new TransportedItemStack(card));
        terminal.notifyExternalProfileSlotMutation();
        entry.profile = definition;
        entry.terminalSettings = Map.of();
    }

    // Capture AFN channels through the native probe before applying a native draft
    private void configureProfile(Entry entry, NavigationProfileBuilderBlockEntity builder){
        if(entry.settings.isEmpty()) return;
        List<ace.flight.navigation.NavigationProfileDefinition.AfnChannel> channels = new ArrayList<>();
        entry.channels.keySet().retainAll(entry.settings.get("afn_channels").entries());
        for(GraphValue channel : entry.settings.get("afn_channels").entries()){
            var cached = entry.channels.get(channel);
            if(cached != null){ channels.add(cached); continue; }
            GraphValue frequency = channel.member("frequency");
            ItemStack first = FlightControlProfiles.frequencyItem(frequency.member("first"), host.getLevel().registryAccess());
            ItemStack second = FlightControlProfiles.frequencyItem(frequency.member("second"), host.getLevel().registryAccess());
            if(builder.getTemplateDefinition() != null){
                var address = ace.flight.compat.afn.AfnFrequencyAddress.fromStacks(first, second);
                var saved = builder.getTemplateDefinition().waypointRoute().afnChannels().stream()
                        .filter(val -> val.name().equals(channel.member("name").asString()) && val.frequency().equals(address)).findFirst();
                if(saved.isPresent()){
                    channels.add(saved.get());
                    entry.channels.put(channel, saved.get());
                    continue;
                }
            }
            if(!ItemStack.matches(builder.getAfnProbeItems().getItem(0), first)
                    || !ItemStack.matches(builder.getAfnProbeItems().getItem(1), second)){
                builder.getAfnProbeItems().setItem(0, first);
                builder.getAfnProbeItems().setItem(1, second);
                builder.tick();
            }
            var capture = builder.captureAfnChannel(builder.getAfnProbeRevision(), channel.member("name").asString());
            if(!capture.captured()) throw new IllegalArgumentException(capture.reason());
            channels.add(capture.channel());
            entry.channels.put(channel, capture.channel());
        }
        var draft = FlightControlProfiles.draft(entry.settings, channels);
        if(builder.getTemplateDefinition() != null && builder.getTemplateDefinition().waypointRoute().equals(
                draft.toWaypointRoute(host.getLevel().dimension().location()))) return;
        var res = builder.applyEditorDraft(builder.getConfigRevision(), draft);
        if(res.status() != NavigationProfileBuilderBlockEntity.ApplyStatus.APPLIED) throw new IllegalArgumentException("Profile " + res.status());
    }

    // Expose the native proxy's named actions without redstone faces in the world
    private void configureProxy(Entry entry){
        if(entry.settings.isEmpty()) return;
        if(!entry.enabled){
            releaseProxy(entry);
            return;
        }
        Map<String, GraphValue> signals = new LinkedHashMap<>();
        String action = entry.settings.get("action").asString();
        signals.put(action, entry.settings.get("strength"));
        GraphValue extra = entry.settings.get("signals");
        if(extra.value() instanceof Map<?, ?> map) map.forEach((key, val) -> signals.put(key.toString(), GraphValue.of(val)));
        signals.entrySet().removeIf(pair -> pair.getKey().isBlank());
        if(signals.values().stream().noneMatch(val -> val.asBoolean() || val.asNumber() > 0)){
            releaseProxy(entry);
            return;
        }
        BlockEntity current = entry.proxyTarget instanceof BlockEntity component ? component : null;
        BlockEntity selected = BlockEntityBindings.select(host, coordinatedComponents(), current, List.of(), component ->
                component != entry.blockEntity && component instanceof RedstoneControllable controllable
                && controllable.getRedstoneActions().stream().map(ace.flight.redstone.RedstoneAction::id)
                        .collect(java.util.stream.Collectors.toSet()).containsAll(signals.keySet()));
        if(!(selected instanceof RedstoneControllable controllable)){
            releaseProxy(entry);
            return;
        }
        bindSignals(entry, controllable, signals.keySet());
        signals.forEach((id, val) -> {
            int strength = "boolean".equals(val.type()) ? val.asBoolean() ? 15 : 0 : (int) Math.clamp(Math.round(val.asNumber()), 0, 15);
            int prev = entry.signals.getOrDefault(id, 0);
            if(prev != strength) controllable.applyRedstoneSignal(id, strength > 0, prev == 0 && strength > 0, strength,
                    entry.proxyId, entry.blockEntity.getBlockPos(), ProxyFace.FRONT);
            entry.signals.put(id, strength);
        });
    }

    private void linkControllers(){
        for(Entry entry : entries.values()){
            if(!(entry.blockEntity instanceof WrenchContributor contributor)) continue;
            BlockEntity target = coordinatedComputer(entry, contributor);
            if(host.hasShipControlModule() && entries.get("$mouse") == entry) target = null;
            BlockPos prev = contributor.getLinkedComputerPos();
            if(prev != null && (target == null || !prev.equals(target.getBlockPos()))){
                BlockEntity owner = HostedBlockEntities.resolve(host.getLevel(), prev);
                if(owner == null) owner = host.getLevel().getBlockEntity(prev);
                if(owner instanceof FlightControlComputerBlockEntity nativeComputer) nativeComputer.removeControllerAt(entry.blockEntity.getBlockPos());
                else if(owner instanceof CreativeFlightControlComputerBlockEntity creative) creative.removeControllerAt(entry.blockEntity.getBlockPos());
                contributor.clearLinkedComputer();
            }
            if(target == null) continue;
            if(target instanceof CreativeFlightControlComputerBlockEntity creative){
                if(!(entry.blockEntity instanceof NavigationTerminalBlockEntity terminal)){
                    status = "Creative computers accept navigation terminals; other controllers need a Flight Control Computer";
                    continue;
                }
                creative.linkNavigationTerminal(terminal);
                continue;
            }
            if(!(target instanceof FlightControlComputerBlockEntity nativeComputer)){
                status = "Unavailable Flight Control computer";
                continue;
            }
            if(!contributor.isLinkedTo(target.getBlockPos())) contributor.setLinkedComputer(entry.blockEntity.getBlockPos(), target.getBlockPos());
            if(!nativeComputer.hasLinkedControllerAt(entry.blockEntity.getBlockPos())) nativeComputer.addController(entry.blockEntity.getBlockPos());
        }
    }

    // Include available native blocks without crossing another ACC's ownership
    private List<BlockEntity> coordinatedComponents(){
        Set<BlockEntity> res = new LinkedHashSet<>();
        entries.values().forEach(entry -> res.add(entry.blockEntity));
        for(BlockEntity component : physicalComponents){
            if(!component.isRemoved() && (BlockEntityBindings.host(component) == null || BlockEntityBindings.host(component) == host)) res.add(component);
        }
        return List.copyOf(res);
    }

    // Retain valid native GUI links and otherwise let the ACC connect compatible components
    private BlockEntity coordinatedComputer(Entry entry, WrenchContributor contributor){
        BlockPos prev = contributor.getLinkedComputerPos();
        var components = coordinatedComponents();
        java.util.function.Predicate<BlockEntity> matches = component -> component instanceof FlightControlComputerBlockEntity
                || entry.blockEntity instanceof NavigationTerminalBlockEntity && component instanceof CreativeFlightControlComputerBlockEntity;
        BlockEntity current = components.stream().filter(component -> component.getBlockPos().equals(prev) && matches.test(component))
                .findFirst().orElse(null);
        if(current != null) return current;
        if(entry.blockEntity instanceof NavigationTerminalBlockEntity){
            BlockEntity creative = BlockEntityBindings.select(host, components, null, List.of(), CreativeFlightControlComputerBlockEntity.class::isInstance);
            if(creative != null) return creative;
        }
        BlockEntity physical = BlockEntityBindings.select(host, physicalComponents, null, List.of(), FlightControlComputerBlockEntity.class::isInstance);
        return physical == null ? computer : physical;
    }

    // Supply computers with native solver geometry while SCM retains engine ownership
    private void synchronizeScmGeometry(FlightControlComputerBlockEntity nativeComputer){
        EngineLinks links = engineLinks.computeIfAbsent(nativeComputer, key -> new EngineLinks());
        Set<BlockPos> found = new LinkedHashSet<>();
        for(BlockEntity component : physicalComponents){
            if(!(component instanceof SmartVectorThrusterBlockEntity engine) || engine.isClusterMember()) continue;
            if((engine.getLinkedComputerPos() != null || engine.getLinkedSwivelMountPos() != null)
                    && FlightControlIntegration.engineHost(engine) != host) continue;
            found.add(engine.getBlockPos());
            if(!nativeComputer.hasLinkedThrusterAt(engine.getBlockPos())){
                nativeComputer.addThruster(engine.getBlockPos(), engine.getNozzleDirection(), engine.getEffectiveMaxThrustForComputer(),
                        engine.halfAngleDeg, engine.getBodyThrustCenterOffset());
            }
        }
        for(BlockPos pos : links.solverEngines) if(!found.contains(pos)) nativeComputer.removeThrusterAt(pos);
        links.solverEngines.clear();
        links.solverEngines.addAll(found);
    }

    // Link engines only when the standalone graph has explicitly active native controls
    private void discoverEngines(FlightControlComputerBlockEntity nativeComputer){
        EngineLinks links = engineLinks.computeIfAbsent(nativeComputer, key -> new EngineLinks());
        var body = SableLevelApi.containing(host);
        if(body == null) return;
        Set<SmartVectorThrusterBlockEntity> found = new LinkedHashSet<>();
        Set<ControlOwnerReceiver> displays = new LinkedHashSet<>();
        Set<IFCCSwivelMount> foundMounts = new LinkedHashSet<>();
        Set<IFCCFlapBearing> foundFlaps = new LinkedHashSet<>();
        for(BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(body)){
            if(blockEntity instanceof IFCCSwivelMount mount){
                BlockPos owner = mount.getFCCMountLinkedComputerPos();
                if(owner != null && !owner.equals(nativeComputer.getBlockPos())) continue;
                if(!nativeComputer.hasLinkedMountAt(blockEntity.getBlockPos())){
                    nativeComputer.addMount(blockEntity.getBlockPos());
                    mount.setFCCMountLinked(blockEntity.getBlockPos(), nativeComputer.getBlockPos());
                }
                foundMounts.add(mount);
                Set<BlockPos> added = links.mounts.computeIfAbsent(mount, key -> new LinkedHashSet<>());
                Set<BlockPos> mountedEngines = new LinkedHashSet<>();
                if(mount.getFCCMountedSubLevel() instanceof dev.ryanhcode.sable.sublevel.SubLevel child){
                    for(BlockEntity mounted : SubLevelBlockEntityCollector.getBlockEntities(child)){
                        if(!(mounted instanceof SmartVectorThrusterBlockEntity engine) || engine.isClusterMember()) continue;
                        if(engine.getLinkedComputerPos() != null || engine.getLinkedSwivelMountPos() != null
                                && !engine.isLinkedToSwivelMount(blockEntity.getBlockPos())) continue;
                        mountedEngines.add(engine.getBlockPos());
                        if(engine instanceof ControlOwnerReceiver receiver) displays.add(receiver);
                        if(engine instanceof VectorThrustReceiver receiver) receiver.releaseVectorControllerForce("ship_control_module");
                        if(!mount.hasFCCMountedThruster(engine.getBlockPos())){
                            mount.addFCCMountedThruster(engine.getBlockPos());
                            added.add(engine.getBlockPos());
                        }
                    }
                }
                for(BlockPos pos : List.copyOf(added)){
                    if(!mountedEngines.contains(pos)){ mount.removeFCCMountedThruster(pos); added.remove(pos); }
                }
            }
            if(blockEntity instanceof IFCCFlapBearing bearing && bearing.isFCCFlapBearingSupported()){
                BlockPos owner = bearing.getFCCFlapBearingLinkedComputerPos();
                if(owner == null || owner.equals(nativeComputer.getBlockPos())){
                    if(!nativeComputer.hasLinkedFlapBearingAt(blockEntity.getBlockPos())){
                        nativeComputer.addFlapBearing(blockEntity.getBlockPos());
                        bearing.setFCCFlapBearingLinked(blockEntity.getBlockPos(), nativeComputer.getBlockPos());
                    }
                    links.flaps.add(bearing);
                    foundFlaps.add(bearing);
                }
            }
            if(!(blockEntity instanceof SmartVectorThrusterBlockEntity engine) || engine.isClusterMember()) continue;
            BlockPos owner = engine.getLinkedComputerPos();
            if(owner != null && !owner.equals(nativeComputer.getBlockPos()) || engine.getLinkedSwivelMountPos() != null) continue;
            if(engine instanceof VectorThrustReceiver receiver) receiver.releaseVectorControllerForce("ship_control_module");
            if(!nativeComputer.hasLinkedThrusterAt(engine.getBlockPos())){
                nativeComputer.addThruster(engine.getBlockPos(), engine.getNozzleDirection(), engine.getEffectiveMaxThrustForComputer(),
                        engine.halfAngleDeg, engine.getBodyThrustCenterOffset());
                engine.setLinkedComputer(engine.getBlockPos(), nativeComputer.getBlockPos());
            }
            found.add(engine);
            if(engine instanceof ControlOwnerReceiver receiver) displays.add(receiver);
        }
        for(IFCCSwivelMount mount : List.copyOf(links.mounts.keySet())){
            if(foundMounts.contains(mount)) continue;
            if(nativeComputer.getBlockPos().equals(mount.getFCCMountLinkedComputerPos())){
                for(BlockPos pos : links.mounts.get(mount)) mount.removeFCCMountedThruster(pos);
                mount.clearFCCMountLinked();
            }
            if(mount instanceof BlockEntity component) nativeComputer.removeMountAt(component.getBlockPos());
            links.mounts.remove(mount);
        }
        for(IFCCFlapBearing bearing : List.copyOf(links.flaps)){
            if(foundFlaps.contains(bearing)) continue;
            if(nativeComputer.getBlockPos().equals(bearing.getFCCFlapBearingLinkedComputerPos())){
                bearing.setFCCFlapControlActive(false);
                bearing.clearFCCFlapBearingLinked();
            }
            if(bearing instanceof BlockEntity component) nativeComputer.removeFlapBearingAt(component.getBlockPos());
            links.flaps.remove(bearing);
        }
        for(SmartVectorThrusterBlockEntity engine : links.engines){
            if(!found.contains(engine) && engine.isLinkedTo(nativeComputer.getBlockPos())){
                if(engine instanceof ControlOwnerReceiver owner) owner.releaseControllerOwner("flight_control_acc");
                engine.clearLinkedComputer();
                nativeComputer.removeThrusterAt(engine.getBlockPos());
            }
        }
        for(ControlOwnerReceiver receiver : links.displays){
            if(!displays.contains(receiver)) receiver.releaseControllerOwner("flight_control_acc");
        }
        for(ControlOwnerReceiver receiver : displays){
            receiver.setControllerOwner("flight_control_acc", "createthrusters.flight_control.owner.acc");
        }
        links.displays.clear();
        links.displays.addAll(displays);
        links.engines.clear();
        links.solverEngines.clear();
        links.engines.addAll(found);
    }

    private void releaseEngines(){
        for(FlightControlComputerBlockEntity nativeComputer : List.copyOf(engineLinks.keySet())) releaseEngines(nativeComputer);
    }

    private void releaseEngines(FlightControlComputerBlockEntity nativeComputer){
        EngineLinks links = engineLinks.get(nativeComputer);
        if(links == null) return;
        links.displays.forEach(receiver -> receiver.releaseControllerOwner("flight_control_acc"));
        links.displays.clear();
        if(entries.values().stream().anyMatch(entry -> entry.blockEntity == nativeComputer && entry.physicalSource == nativeComputer)) return;
        for(SmartVectorThrusterBlockEntity engine : links.engines){
            if(engine.isLinkedTo(nativeComputer.getBlockPos())){
                if(engine instanceof ControlOwnerReceiver owner) owner.releaseControllerOwner("flight_control_acc");
                engine.setAllocatedForce(new double[3]);
                engine.clearLinkedComputer();
            }
        }
        nativeComputer.clearThrusters();
        links.mounts.forEach((mount, added) -> {
            if(nativeComputer.getBlockPos().equals(mount.getFCCMountLinkedComputerPos())){
                for(BlockPos pos : added) mount.removeFCCMountedThruster(pos);
                mount.clearFCCMountLinked();
            }
        });
        for(var mount : List.copyOf(nativeComputer.linkedMountEntries)) nativeComputer.removeMountAt(nativeComputer.getBlockPos().offset(mount.relativePos()));
        for(IFCCFlapBearing bearing : links.flaps){
            if(nativeComputer.getBlockPos().equals(bearing.getFCCFlapBearingLinkedComputerPos())){
                bearing.setFCCFlapControlActive(false);
                bearing.clearFCCFlapBearingLinked();
            }
        }
        for(var flap : List.copyOf(nativeComputer.linkedFlapBearingEntries)) nativeComputer.removeFlapBearingAt(nativeComputer.getBlockPos().offset(flap.relativePos()));
        links.engines.clear();
        links.solverEngines.clear();
        links.mounts.clear();
        links.flaps.clear();
        nativeComputer.considerGravity = links.gravity;
    }

    private static void nativeTick(BlockEntity component){
        if(component instanceof NavigationProfileHolderBlockEntity holder){ holder.tick(); return; }
        for(Method method : component.getClass().getMethods()){
            if(!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("tick") || method.getParameterCount() != 4
                    || !method.getParameterTypes()[3].isInstance(component)) continue;
            try{ method.invoke(null, component.getLevel(), component.getBlockPos(), component.getBlockState(), component); }
            catch(ReflectiveOperationException err){ throw new IllegalStateException("Native controller tick failed", err); }
            return;
        }
    }

    private static MouseFlightControllerBlockEntity mouseController(Entry entry){
        if(entry.blockEntity instanceof MouseFlightControllerBlockEntity mouse) return mouse;
        return entry.blockEntity instanceof CreativeFlightControlComputerBlockEntity creative ? creative.getFlightTerminal() : null;
    }

    private static Object argument(GraphValue val, Class<?> type){
        if(type == boolean.class) return val.asBoolean();
        if(type == int.class) return (int) Math.clamp(Math.round(val.asNumber()), Integer.MIN_VALUE, Integer.MAX_VALUE);
        if(type == double.class) return val.asNumber();
        if(type.isEnum()){
            for(Object option : type.getEnumConstants()){
                if(((Enum<?>) option).name().equalsIgnoreCase(val.asString())) return option;
            }
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + val.asString());
        }
        throw new IllegalArgumentException("Unsupported native setting type " + type.getName());
    }

    private static GraphValue vectorValue(double[] val){
        return GraphValue.map(Map.of("x", GraphValue.number(val[0]), "y", GraphValue.number(val[1]), "z", GraphValue.number(val[2])));
    }

    private String componentId(BlockEntity component){
        return entries.entrySet().stream().filter(pair -> pair.getValue().blockEntity == component)
                .map(Map.Entry::getKey).findFirst().orElse("");
    }

    private static final class EngineLinks{
        private final Set<BlockPos> solverEngines = new LinkedHashSet<>();
        private final Set<SmartVectorThrusterBlockEntity> engines = new LinkedHashSet<>();
        private final Set<ControlOwnerReceiver> displays = new LinkedHashSet<>();
        private final Map<IFCCSwivelMount, Set<BlockPos>> mounts = new LinkedHashMap<>();
        private final Set<IFCCFlapBearing> flaps = new LinkedHashSet<>();
        private boolean gravity = true;
    }

    private static final class Entry{
        private final FlightControlNodes.Spec spec;
        private final BlockEntity blockEntity;
        private final int slot;
        private BlockEntity physicalSource;
        private BlockPos originalComputer;
        private final GraphBlockSettings synchronizer = new GraphBlockSettings();
        private final Map<String, Integer> signals = new HashMap<>();
        private Map<String, GraphValue> settings = Map.of();
        private final UUID proxyId;
        private Set<String> claims = Set.of();
        private RedstoneControllable proxyTarget;
        private NavigationProfileDefinition profile;
        private GraphValue profileValue;
        private boolean profileInitialized;
        private Map<String, GraphValue> terminalSettings = Map.of();
        private String taskAction = "none";
        private final Map<GraphValue, ace.flight.navigation.NavigationProfileDefinition.AfnChannel> channels = new HashMap<>();
        private volatile boolean enabled = true;
        private volatile boolean mouseSignalActive;

        private Entry(FlightControlNodes.Spec spec, BlockEntity blockEntity, int slot){
            this.spec = spec;
            this.blockEntity = blockEntity;
            this.slot = slot;
            proxyId = UUID.nameUUIDFromBytes((spec.path() + ":" + blockEntity.getBlockPos()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
