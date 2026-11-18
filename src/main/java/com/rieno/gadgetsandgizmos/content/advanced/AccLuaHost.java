package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import com.rieno.gadgetsandgizmos.content.peripheral.ControlPeripheralBindings;
import com.rieno.gadgetsandgizmos.content.peripheral.AdvancedContraptionControllerPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralSides;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEvent;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventBus;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventSource;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.script.LuaJScriptSession;
import com.rieno.gadgetsandgizmos.lib.script.ScriptArguments;
import com.rieno.gadgetsandgizmos.lib.script.ScriptDevice;
import com.rieno.gadgetsandgizmos.lib.script.ScriptDisplayMethods;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptHost;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethods;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralNetwork;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralRegistry;
import com.rieno.gadgetsandgizmos.lib.script.ScriptSession;
import com.rieno.gadgetsandgizmos.lib.script.ScriptStoragePeripherals;
import com.rieno.gadgetsandgizmos.lib.script.ScriptValues;
import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Bind selected ACC controls to the library fallback without loading ComputerCraft classes
public final class AccLuaHost implements ScriptHost, ScriptPeripheralNetwork{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String REDNET = "gadgetsandgizmos:lua_rednet";
    private static final Map<AdvancedContraptionControllerBlockEntity, Names> NAMES = new WeakHashMap<>();
    private static final Map<BlockEntity, Monitor> MONITORS = new WeakHashMap<>();
    static{
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent evt) -> {
            MONITORS.entrySet().removeIf(entry -> entry.getKey().getLevel() != null && entry.getKey().getLevel().getServer() == evt.getServer());
        });
    }
    private final AdvancedContraptionControllerBlockEntity controller;
    private final Map<String, Bound> targets = new LinkedHashMap<>();
    private final Map<String, ScriptDisplayMethods> displays = new HashMap<>();
    private NamedEventBus.Subscription subscription;
    private LuaJScriptSession session;
    private AutoCloseable displaySource;
    private long discoveryTick = Long.MIN_VALUE;

    private AccLuaHost(AdvancedContraptionControllerBlockEntity controller){ this.controller = controller; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static ScriptSession create(AdvancedContraptionControllerBlockEntity controller, UUID id){
        var host = new AccLuaHost(controller);
        host.session = new LuaJScriptSession(id, host);
        host.displaySource = VirtualDisplaySources.register(controller.getLevel().getServer(), id, (width, height) -> {
            var frame = host.session.terminal();
            return frame == null ? new CompoundTag() : frame.displayFrame("ACC Lua");
        }, (player, input) -> host.canAccess(player) && host.session.input(input), host::canAccess);
        host.subscription = NamedEventBus.subscribe("gadgetsandgizmos:lua/" + id, event -> host.receive(id, event));
        return host.session;
    }

    public static CompoundTag catalog(AdvancedContraptionControllerBlockEntity controller){
        var host = new AccLuaHost(controller); var data = new CompoundTag(); var peripherals = new ListTag(); var selections = new ListTag();
        for(var device : host.devices()) if(!Set.of("back", "left").contains(device.name())) peripherals.add(device.catalog().toTag());
        host.targets.values().forEach(target -> { if(target.selected != null) selections.add(target.selected.toTag()); });
        data.put("Peripherals", peripherals); data.put("Targets", selections); return data;
    }

    @Override public List<ScriptDevice> devices(){
        if(controller.isRemoved() || controller.getLevel() == null){ targets.clear(); return List.of(); }
        long now = controller.getLevel().getGameTime();
        if(!targets.isEmpty() && now >= discoveryTick && now - discoveryTick < 10) return targets.values().stream().map(Bound::device).toList();
        targets.clear();
        discoveryTick = now;
        addController();
        Map<String, ContraptionNetworkLinkerData.LinkedTarget> linked = new LinkedHashMap<>();
        controller.getLinkedTargets().forEach(target -> linked.put(ContraptionNetworkLinkerData.nodeIdForTarget(target), target));
        for(var target : ContraptionNetworkLinkerData.readTargets(controller.getStoredLinker())){
            String key = ContraptionNetworkLinkerData.nodeIdForTarget(target); if(linked.containsKey(key)) linked.put(key, target);
        }
        for(var entry : linked.values()){
            var target = new ControllerDiscoveryNode(ContraptionNetworkLinkerData.nodeIdForTarget(entry), entry.mode().discoveryKind(), "", entry.blockId(), entry.label(), entry.subLevelId(), entry.blockPos());
            if(entry.scope().usesFaces()) for(var face : entry.faces()){
                var data = new CompoundTag(); var faces = new CompoundTag(); faces.putString(target.nodeId(), face.face().getSerializedName());
                data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces);
                add(new AdvancedGraphDocument.Node("", "", "", 0, 0, data), target);
            }else add(null, target);
        }
        for(var neighbour : ScriptPeripheralSides.adjacent(controller.getLevel(), controller.getBlockPos(),
                controller.getBlockState().getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING))){
            var be = neighbour.target();
            var selected = new ControllerDiscoveryNode("adjacent:" + neighbour.alias(), com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.MACHINE, "",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString(), "",
                    com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(be), be.getBlockPos());
            var data = new CompoundTag(); var faces = new CompoundTag(); var aliases = new CompoundTag();
            faces.putString(selected.nodeId(), neighbour.face().getSerializedName());
            aliases.putString(selected.nodeId(), neighbour.alias());
            data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces); data.put(GraphTargetSelection.ALIASES_TAG, aliases);
            add(new AdvancedGraphDocument.Node("", "", "", 0, 0, data), selected);
        }
        for(var graph : List.of(controller.getDraftGraph(), controller.getActiveGraph())){
            List<AdvancedGraphDocument.Node> nodes = new ArrayList<>(graph.nodes()); graph.functions().forEach(function -> nodes.addAll(function.nodes()));
            for(var node : nodes){
                add(node, ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData")));
                add(node, ControllerDiscoveryNode.fromTag(node.data().getCompound("SourceData")));
                for(Tag tag : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)) add(node, ControllerDiscoveryNode.fromTag((CompoundTag)tag));
            }
        }
        return targets.values().stream().map(Bound::device).toList();
    }

    @Override public Object[] call(String name, String method, List<Object> args) throws Exception{
        if(controller.isRemoved() || controller.getLevel() == null) throw new IllegalArgumentException("Controller is unavailable");
        Bound target = resolve(name);
        if(target.selected != null){
            var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), target.node, target.selected);
            if(resolved == null || resolved.level().getBlockEntity(resolved.pos()) != target.target || target.target.isRemoved())
                throw new IllegalArgumentException("Peripheral target is no longer available");
        }
        Object[] res = target.methods.call(method, args);
        for(int idx = 0; idx < res.length; idx++) res[idx] = value(res[idx]);
        return res;
    }

    @Override public long ticks(){ return controller.getLevel().getGameTime(); }
    @Override public long dayTicks(){ return controller.getLevel().getDayTime(); }
    @Override public double[] position(){
        Vec3 pos = SableTransformApi.projectOut(controller.getLevel(), SableTransformApi.toWorldPosition(controller, Vec3.atCenterOf(controller.getBlockPos())));
        return new double[]{pos.x, pos.y, pos.z};
    }
    @Override public void transmit(UUID id, int sender, Integer recipient, String protocol, Object data){
        double[] coordinates = position(); Vec3 pos = new Vec3(coordinates[0], coordinates[1], coordinates[2]);
        NamedEventSource source = new NamedEventSource(controller.getLevel().getServer(), controller.getLevel().dimension(), pos, id.toString());
        if(recipient == null && protocol != null && !protocol.isBlank()){
            NamedEventBus.publish(NamedEvent.of(source, protocol, GraphValue.of(data), 0)); return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>(); envelope.put("sender", sender); envelope.put("recipient", recipient == null ? -1 : recipient);
        if(protocol != null) envelope.put("protocol", protocol);
        envelope.put("message", data == null ? "" : data);
        NamedEventBus.publish(NamedEvent.of(source, REDNET, GraphValue.map(envelope), 0));
    }
    @Override public void close(){
        if(subscription != null){ subscription.close(); subscription = null; }
        if(displaySource != null){ try{ displaySource.close(); }catch(Exception ex){ throw new IllegalStateException(ex); } displaySource = null; }
        MONITORS.entrySet().removeIf(entry -> {
            if(entry.getValue().host != this) return false;
            if(entry.getKey() instanceof AccDisplayBlockEntity display) display.queueDisplayRefresh();
            if(entry.getKey() instanceof UniversalDisplayAdapterBlockEntity adapter) adapter.clearInputFrame("ACC Lua");
            return true;
        });
        targets.clear(); displays.clear();
    }

    @Override public void terminal(String name, ScriptTerminal frame){
        if(frame == null){
            MONITORS.entrySet().removeIf(entry -> {
                if(entry.getValue().host != this || !entry.getValue().name.equals(name)) return false;
                if(entry.getKey() instanceof AccDisplayBlockEntity display) display.queueDisplayRefresh();
                if(entry.getKey() instanceof UniversalDisplayAdapterBlockEntity adapter) adapter.clearInputFrame("ACC Lua");
                return true;
            });
            return;
        }
        Bound target;
        try{ target = resolve(name); }catch(ScriptException ex){ return; }
        if(target.target == null || target.target.isRemoved()) return;
        BlockEntity block = target.target;
        if(block instanceof AccDisplayBlockEntity display){
            var root = display.networkRoot(); if(root != null) block = root;
        }
        MONITORS.put(block, new Monitor(this, name, frame));
        if(block instanceof AccDisplayBlockEntity display) display.queueDisplayRefresh();
        if(block instanceof UniversalDisplayAdapterBlockEntity adapter) adapter.acceptInputFrame("ACC Lua", physicalFrame(frame));
    }

    @Override public int[] terminalSize(String name){
        Bound target = resolve(name);
        if(target.target instanceof AccDisplayBlockEntity display){
            var root = display.networkRoot(); if(root != null) display = root;
            return new int[]{Math.max(1, (int)Math.round((display.networkWidth() - .3125) * 64 / 6)),
                    Math.max(1, (int)Math.round((display.networkHeight() - .3125) * 64 / 9))};
        }
        if(target.target instanceof UniversalDisplayAdapterBlockEntity adapter){
            return new int[]{Math.max(1, adapter.displayWidth() / 6), Math.max(1, adapter.displayHeight() / 9)};
        }
        return ScriptHost.super.terminalSize(name);
    }

    public static CompoundTag terminalFrame(BlockEntity block){
        if(block instanceof AccDisplayBlockEntity display){ var root = display.networkRoot(); if(root != null) block = root; }
        Monitor monitor = MONITORS.get(block);
        return monitor == null || monitor.host.controller.isRemoved() ? new CompoundTag() : physicalFrame(monitor.frame);
    }

    private static CompoundTag physicalFrame(ScriptTerminal terminal){
        var frame = terminal.displayFrame("ACC Lua"); frame.putBoolean("PreserveAspect", false); return frame;
    }

    public static boolean terminalInput(BlockEntity block, String action, double x, double y, int val){
        if(block instanceof AccDisplayBlockEntity display){ var root = display.networkRoot(); if(root != null) block = root; }
        Monitor monitor = MONITORS.get(block);
        if(monitor == null || monitor.host.controller.isRemoved()) return false;
        try{ return monitor.host.session.monitorInput(monitor.name, new ScriptInput(action, x, y, val, "")); }
        catch(IllegalArgumentException ex){ return false; }
    }

    private boolean canAccess(ServerPlayer player){
        return player == null || com.rieno.gadgetsandgizmos.content.ShipPermissions.allows(player, controller,
                com.rieno.gadgetsandgizmos.lib.scm.ShipPermission.INTERACT)
                && com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(player, player.serverLevel(),
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(controller), controller.getBlockPos());
    }
    @Override public String resource(String path){
        if(!path.startsWith("rom/") || path.contains("..") || path.contains("\\")) return null;
        try(var stream = AccLuaHost.class.getResourceAsStream("/data/computercraft/lua/" + path)){
            if(stream == null) return null;
            return new String(stream.readNBytes(32769), java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException ex){ throw new ScriptException("Cannot load bundled Lua module"); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void receive(UUID id, NamedEvent event){
        if(controller.isRemoved() || controller.getLevel() == null || event.source().server() != controller.getLevel().getServer()) return;
        if(!AdvancedControllerNamedEventBus.withinDistance(event.source().dimension().equals(controller.getLevel().dimension()),
                event.source().position(), controller.namedControllerEventPosition(), event.maximumDistance())) return;
        if(REDNET.equals(event.name()) && event.data().value() instanceof Map<?, ?> data){
            int recipient = ((Number)data.get("recipient")).intValue();
            if(recipient != -1 && recipient != (id.hashCode() & Integer.MAX_VALUE)) return;
            if(event.source().endpointId().equals(id.toString())) return;
            session.rednetMessage(((Number)data.get("sender")).intValue(), (String)data.get("protocol"), GraphValue.of(data.get("message")));
        }else if(!REDNET.equals(event.name())){
            if(event.source().endpointId().equals(id.toString())) return;
            int sender;
            try{ sender = UUID.fromString(event.source().endpointId()).hashCode() & Integer.MAX_VALUE; }
            catch(IllegalArgumentException ex){ sender = -1 - Math.floorMod(event.source().endpointId().hashCode(), Integer.MAX_VALUE - 1); }
            session.rednetMessage(sender, event.name(), event.data());
        }
    }

    private void add(AdvancedGraphDocument.Node node, ControllerDiscoveryNode selected){
        var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), node, selected);
        if(resolved == null) return;
        BlockEntity target = resolved.level().getBlockEntity(resolved.pos());
        if(target == null || target == controller || target.isRemoved()) return;
        ScriptPeripheralBinding binding = ScriptPeripheralRegistry.resolve(target, resolved.side());
        if(binding == null) binding = ControlPeripheralBindings.resolve(target);
        String type = target instanceof AccDisplayBlockEntity || target instanceof UniversalDisplayAdapterBlockEntity ? "monitor"
                : binding == null ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()).toString() : binding.getType();
        String key = type + "/" + resolved.level().dimension().location() + "/" + resolved.pos().asLong() + "/" + resolved.side();
        var names = NAMES.computeIfAbsent(controller, ignored -> new Names());
        String shortName = names.ids.computeIfAbsent(key, ignored -> type + "_" + (names.next.merge(type, 1, Integer::sum) - 1));
        String name = "acc_" + controller.getScmPersistenceId() + "/" + shortName;
        Set<String> aliases = new LinkedHashSet<>(Set.of(shortName)); Set<String> refs = new LinkedHashSet<>(Set.of(selected.nodeId()));
        if(!selected.label().isBlank()) aliases.add(selected.label());
        if(node != null){ String alias = node.data().getCompound(GraphTargetSelection.ALIASES_TAG).getString(selected.nodeId()); if(!alias.isBlank()) aliases.add(alias); }
        Bound previous = targets.get(name);
        if(previous != null){ aliases.addAll(previous.device.aliases()); refs.addAll(previous.device.references()); }
        ScriptMethods methods = controls(target, resolved.side());
        if(type.equals("monitor")) displays.computeIfAbsent(name, ignored -> new ScriptDisplayMethods()).bind(methods);
        var types = new LinkedHashSet<String>();
        if(binding != null) types.addAll(binding.getAdditionalTypes());
        types.addAll(ScriptStoragePeripherals.bind(methods, new ScriptStoragePeripherals.Target(target, resolved.side()), this::storage));
        ScriptDevice device = new ScriptDevice(name, type, types, aliases, refs, methods.names());
        targets.put(name, new Bound(device, methods, target, node, selected));
    }

    private ScriptMethods controls(BlockEntity target, Direction side){
        ScriptMethods methods = new ScriptMethods().data(target);
        ScriptPeripheralBinding binding = ScriptPeripheralRegistry.resolve(target, side);
        if(binding == null) binding = ControlPeripheralBindings.resolve(target);
        if(binding instanceof AdvancedContraptionControllerPeripheralControl advanced){
            AccLuaHost network = target == controller ? this : new AccLuaHost((AdvancedContraptionControllerBlockEntity)target);
            advanced.network(network);
        }
        if(binding != null) methods.bind(binding);
        return methods;
    }

    private void addController(){
        ScriptMethods methods = controls(controller, null);
        targets.put("back", new Bound(new ScriptDevice("back", "advanced_contraption_controller", Set.of("peripheral_hub"), Set.of(), Set.of(), methods.names()), methods, controller, null, null));
        var modem = new ScriptMethods().bind("isWireless", args -> new Object[]{true});
        targets.put("left", new Bound(new ScriptDevice("left", "modem", Set.of(), Set.of(), Set.of(), modem.names()), modem, controller, null, null));
    }

    @Override public Map<String, String> getPeripherals(){
        if(targets.isEmpty()) devices();
        Map<String, String> names = new LinkedHashMap<>();
        targets.values().forEach(target -> { String name = target.device.name(); if(name.contains("/")) names.put(name.substring(name.lastIndexOf('/') + 1), name); });
        Map<String, String> aliases = new LinkedHashMap<>(); Set<String> ambiguous = new LinkedHashSet<>();
        targets.values().forEach(target -> target.device.aliases().forEach(alias -> {
            if(alias.isBlank() || Set.of("top", "bottom", "left", "right", "front", "back").contains(alias)) return;
            String previous = aliases.putIfAbsent(alias, target.device.name());
            if(previous != null && !previous.equals(target.device.name())) ambiguous.add(alias);
        }));
        aliases.forEach((alias, name) -> { if(!ambiguous.contains(alias) && !targets.containsKey(alias)) names.putIfAbsent(alias, name); });
        return names;
    }
    @Override public String peripheral(String id){ return resolve(id).device.name(); }
    @Override public List<String> getNamesRemote(){ if(targets.isEmpty()) devices(); return targets.keySet().stream().filter(name -> name.contains("/")).toList(); }
    @Override public boolean isPresentRemote(String name){ try{ resolve(name); return true; }catch(ScriptException ex){ return false; } }
    @Override public Object[] getTypeRemote(String name){
        if(!isPresentRemote(name)) return null;
        var device = resolve(name).device; List<String> types = new ArrayList<>(List.of(device.type())); types.addAll(device.types()); return types.toArray();
    }
    @Override public Boolean hasTypeRemote(String name, String type){ var types = getTypeRemote(name); return types == null ? null : Arrays.asList(types).contains(type); }
    @Override public List<String> getMethodsRemote(String name){ return isPresentRemote(name) ? resolve(name).device.methods().stream().sorted().toList() : null; }
    @Override public Object[] callRemote(ScriptArguments args){
        try{ return call((String)args.get(0), (String)args.get(1), args.values().subList(2, args.values().size())); }
        catch(Exception ex){ throw new ScriptException(ex.getMessage()); }
    }
    private Bound resolve(String name){
        if(targets.isEmpty()) devices();
        Bound exact = targets.get(name); if(exact != null) return exact;
        for(var target : targets.values()){
            String nativeName = target.device.name();
            if(nativeName.contains("/") && nativeName.substring(nativeName.lastIndexOf('/') + 1).equals(name)) return target;
        }
        var aliases = targets.values().stream().filter(target -> target.device.aliases().contains(name)).toList();
        if(aliases.size() != 1) throw new ScriptException("unknown or unavailable peripheral '" + name + "'");
        return aliases.getFirst();
    }
    private ScriptStoragePeripherals.Target storage(String name){
        Bound target = resolve(name);
        var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), target.node, target.selected);
        if(resolved == null || resolved.level().getBlockEntity(resolved.pos()) != target.target) throw new ScriptException("Storage is no longer available");
        return new ScriptStoragePeripherals.Target(target.target, resolved.side());
    }
    private static Object value(Object val){
        if(val == null || val instanceof String || val instanceof Boolean || val instanceof Number) return val;
        if(val instanceof Enum<?> choice) return choice.name().toLowerCase(Locale.ROOT);
        if(val instanceof net.minecraft.core.BlockPos pos) return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
        if(val instanceof Vec3 pos) return Map.of("x", pos.x, "y", pos.y, "z", pos.z);
        if(val instanceof GraphValue graph) return ScriptValues.encode(graph);
        if(val instanceof AdvancedGraphDocument.Value graph) return ScriptValues.encode(GraphRuntime.toLibraryValue(graph));
        if(val instanceof Map<?, ?> map){ var res = new LinkedHashMap<Object, Object>(); map.forEach((key, entry) -> res.put(key, value(entry))); return res; }
        if(val instanceof Collection<?> list) return list.stream().map(AccLuaHost::value).toList();
        if(val instanceof Optional<?> optional) return optional.map(AccLuaHost::value).orElse(null);
        throw new IllegalArgumentException("Unsupported device result: " + val.getClass().getSimpleName());
    }
    private record Bound(ScriptDevice device, ScriptMethods methods, BlockEntity target, AdvancedGraphDocument.Node node, ControllerDiscoveryNode selected){}
    private record Monitor(AccLuaHost host, String name, ScriptTerminal frame){}
    private static final class Names{
        private final Map<String, String> ids = new HashMap<>();
        private final Map<String, Integer> next = new HashMap<>();
    }
}
