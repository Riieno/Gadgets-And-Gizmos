package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.script.LuaScripts;
import com.rieno.gadgetsandgizmos.lib.script.ScriptRequest;
import com.rieno.gadgetsandgizmos.lib.script.ScriptResult;
import com.rieno.gadgetsandgizmos.lib.script.ScriptSession;
import com.rieno.gadgetsandgizmos.lib.script.ScriptValues;
import com.rieno.gadgetsandgizmos.lib.script.ScriptUpdate;
import com.rieno.gadgetsandgizmos.lib.script.ScriptExecPorts;
import com.rieno.gadgetsandgizmos.lib.script.ScriptExecPulse;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPortChange;
import com.rieno.gadgetsandgizmos.lib.script.ScriptOutputChange;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPorts;
import net.minecraft.nbt.CompoundTag;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.ComputerContext;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerEnvironment;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.computer.GlobalEnvironment;
import dan200.computercraft.core.computer.mainthread.MainThread;
import dan200.computercraft.core.filesystem.MemoryMount;
import dan200.computercraft.core.metrics.MetricsObserver;
import dan200.computercraft.core.terminal.Terminal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Adapt an isolated CC computer to one ACC script session without placing blocks or creating world files
public final class ComputerCraftLuaComputer implements ScriptSession{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, Host> HOSTS = new HashMap<>();
    private static final long MAX_BOOT_TIME = TimeUnit.SECONDS.toNanos(10);
    private static boolean installed;
    private final AdvancedContraptionControllerBlockEntity controller;
    private final UUID id;
    private final Host host;
    private final AutoCloseable displaySource;
    private Computer computer;
    private com.rieno.gadgetsandgizmos.lib.compat.computercraft.VirtualWirelessModem modem;
    private Terminal terminal;
    private final java.util.concurrent.atomic.AtomicLong terminalVersion = new java.util.concurrent.atomic.AtomicLong();
    private long sampledTerminalVersion = -1;
    private ScriptTerminal terminalSnapshot;
    private ScriptRequest registered, synchronizedRequest, pending, active;
    private String pendingPort;
    private final CompoundTag portData = new CompoundTag();
    private final Map<String, GraphValue> outputValues = new LinkedHashMap<>(), changedOutputs = new LinkedHashMap<>();
    private final Set<String> removedOutputs = new HashSet<>();
    private final ArrayDeque<ScriptResult> completed = new ArrayDeque<>();
    private final ArrayDeque<ScriptExecPulse> execPulses = new ArrayDeque<>();
    private final ArrayDeque<ScriptPortChange> portChanges = new ArrayDeque<>();
    private final ArrayDeque<LocalRednetMessage> localMessages = new ArrayDeque<>();
    private long bootTime, sequence;
    private boolean closed, booted, initialized, mainRunning, handlerRunning, persistentOutputs;
    private volatile int day;
    private volatile double time;

    public ComputerCraftLuaComputer(AdvancedContraptionControllerBlockEntity controller, UUID id){
        this.controller = controller; this.id = id;
        host = HOSTS.computeIfAbsent(controller.getLevel().getServer(), Host::new);
        displaySource = com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.register(controller.getLevel().getServer(), id, (width, height) -> {
            var snapshot = terminal(); return snapshot == null ? new CompoundTag() : snapshot.displayFrame();
        }, this::input, this::canAccess);
    }

    private boolean canAccess(net.minecraft.server.level.ServerPlayer player){
        return player == null || com.rieno.gadgetsandgizmos.content.ShipPermissions.allows(player, controller,
                com.rieno.gadgetsandgizmos.lib.scm.ShipPermission.INTERACT)
                && com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(player, player.serverLevel(),
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(controller), controller.getBlockPos());
    }

    public synchronized boolean input(net.minecraft.server.level.ServerPlayer player, com.rieno.gadgetsandgizmos.lib.script.ScriptInput input){
        if(closed || computer == null || terminal == null) return false;
        if(!canAccess(player)) return false;
        computer.queueEvent(input.event(), input.arguments(terminal.getWidth(), terminal.getHeight())); return true;
    }

    // Tick queued world operations on their owning server thread
    public static void install(){
        if(installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post evt) -> {
            Host host = HOSTS.get(evt.getServer()); if(host != null) host.mainThread.tick();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent evt) -> {
            Host host = HOSTS.remove(evt.getServer()); if(host == null) return;
            com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.clear(evt.getServer());
            try{ host.ctx.ensureClosed(1, TimeUnit.SECONDS); }
            catch(InterruptedException ex){ Thread.currentThread().interrupt(); }
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read completion metadata through the same hub used by peripheral.wrap
    public static List<com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral> peripherals(AdvancedContraptionControllerBlockEntity controller){
        var bridge = ComputerCraftPeripheralBridge.of(controller); bridge.hub.refresh(); return bridge.hub.catalog();
    }

    public static net.minecraft.nbt.CompoundTag peripheralCatalog(AdvancedContraptionControllerBlockEntity controller){
        var bridge = ComputerCraftPeripheralBridge.of(controller);
        bridge.hub.refresh();
        var tag = new net.minecraft.nbt.CompoundTag(); var peripherals = new net.minecraft.nbt.ListTag(); var targets = new net.minecraft.nbt.ListTag();
        bridge.hub.catalog().forEach(peripheral -> peripherals.add(peripheral.toTag()));
        bridge.selections.values().forEach(target -> targets.add(target.toTag()));
        tag.put("Peripherals", peripherals); tag.put("Targets", targets); return tag;
    }

    @Override public synchronized void register(ScriptRequest req){
        if(closed) return;
        ScriptRequest prev = registered;
        boolean changed = prev == null || !prev.source().equals(req.source()) || prev.autoStart() != req.autoStart();
        var previousPorts = portData.copy();
        if(changed){
            stop();
            if(!persistentOutputs) outputValues.clear();
            portData.getAllKeys().stream().toList().forEach(portData::remove);
            schema(req, null);
            portData.putBoolean(ScriptPorts.TRACKED, true);
            if(prev != null){
                portData.put(ScriptPorts.OWNED, previousPorts.getCompound(ScriptPorts.OWNED).copy());
                ScriptPorts.reconcile(portData, prev.source(), req.source());
            }
        }else schema(req, prev);
        registered = req;
        if(changed) start();
        else if(computer != null) computer.queueEvent("__gng_script", null);
    }

    @Override public synchronized void submit(ScriptRequest req){ register(req); }

    @Override public synchronized void submit(ScriptRequest req, String port){
        if(!acceptsPulse(port)) return;
        register(req);
        pending = req; pendingPort = port;
        computer.queueEvent("__gng_script", null);
    }

    @Override public synchronized boolean acceptsPulse(String port){
        return !closed && computer != null && initialized && !handlerRunning && pending == null && active == null
                && portData.getCompound(ScriptPorts.EXEC_INPUTS).contains(port);
    }
    @Override public synchronized boolean running(){ return mainRunning || handlerRunning || pending != null || !booted && computer != null; }
    @Override public synchronized ScriptResult poll(){ return completed.poll(); }
    @Override public synchronized GraphValue output(String name){ return outputValues.get(name); }
    @Override public synchronized GraphValue reference(){
        return com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes.reference(com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.reference(id));
    }
    @Override public synchronized ScriptExecPulse pollExec(){ return execPulses.poll(); }
    @Override public synchronized ScriptPortChange pollPort(){ return portChanges.poll(); }
    @Override public synchronized ScriptOutputChange pollOutput(){
        if(registered == null || changedOutputs.isEmpty() && removedOutputs.isEmpty()) return null;
        var res = new ScriptOutputChange(id, registered.revision(), changedOutputs, removedOutputs);
        changedOutputs.clear(); removedOutputs.clear(); return res;
    }

    private void schema(ScriptRequest req, ScriptRequest prev){
        schema("DynamicInputs", "any", req.inputs().keySet().stream().filter(name -> !"name".equals(name)).collect(java.util.stream.Collectors.toSet()),
                prev == null ? Set.of() : prev.inputs().keySet());
        schema("DynamicOutputs", "any", req.outputs().keySet(), prev == null ? Set.of() : prev.outputs().keySet());
        schema(ScriptPorts.EXEC_INPUTS, "exec", req.execInputs(), prev == null ? Set.of() : prev.execInputs());
        schema(ScriptPorts.EXEC_OUTPUTS, "exec", req.execOutputs(), prev == null ? Set.of() : prev.execOutputs());
    }
    private void schema(String key, String type, Set<String> next, Set<String> prev){
        var data = portData.getCompound(key);
        for(String name : prev) if(!next.contains(name)) data.remove(name);
        for(String name : next) if(!prev.contains(name)) data.putString(name, type);
        portData.put(key, data);
    }

    private void start(){
        bootTime = System.nanoTime();
        booted = false;
        updateClock();
        ComputerEnvironment env = new ComputerEnvironment(){
            @Override public int getDay(){ return day; }
            @Override public double getTimeOfDay(){ return time; }
            @Override public MetricsObserver getMetrics(){ return MetricsObserver.discard(); }
            @Override public WritableMount createRootMount(){
                try(InputStream stream = ScriptSession.class.getResourceAsStream("runtime.lua")){
                    if(stream == null) throw new IllegalStateException("Lua runtime is unavailable");
                    return new MemoryMount(1024 * 1024).addFile("startup.lua", stream.readAllBytes())
                            .addFile(".settings", "{[\"bios.use_multishell\"]=false,[\"shell.allow_disk_startup\"]=false}");
                }catch(IOException ex){ throw new IllegalStateException("Cannot load Lua runtime", ex); }
            }
        };
        terminal = new Terminal(51, 19, true, terminalVersion::incrementAndGet);
        sampledTerminalVersion = -1;
        computer = new Computer(host.ctx, env, terminal, id.hashCode() & Integer.MAX_VALUE);
        computer.setLabel("Lua " + id);
        computer.addApi(new Bridge());
        computer.getEnvironment().setPeripheral(ComputerSide.BACK, new AdvancedContraptionControllerPeripheral(controller));
        modem = new com.rieno.gadgetsandgizmos.lib.compat.computercraft.VirtualWirelessModem(
                ComputerCraftAPI.getWirelessNetwork(controller.getLevel().getServer()), dan200.computercraft.shared.config.Config.modemRange);
        updateModemPosition();
        computer.getEnvironment().setPeripheral(ComputerSide.LEFT, modem);
        computer.turnOn();
    }

    @Override public synchronized void tick(){
        if(closed || computer == null) return;
        updateClock(); updateModemPosition(); computer.tick();
        if((booted && !computer.isOn()) || !booted && System.nanoTime() - bootTime > MAX_BOOT_TIME){
            String error = booted ? "Computer stopped before the script completed" : "CC:Tweaked computer could not start";
            ScriptRequest req = registered;
            stop();
            if(req != null) completed.add(new ScriptResult(id, req.revision(), outputValues, error, true, ++sequence, "startup"));
        }
    }
    private void updateClock(){
        long tick = controller.getLevel().getDayTime() + 6000L;
        day = (int)(tick / 24000L) + 1; time = tick % 24000L / 1000.0;
    }
    @Override public synchronized void namedEvent(String name, GraphValue data){
        if(computer != null && !closed) computer.queueEvent("gng_named_event", new Object[]{name, ScriptValues.encode(data)});
    }
    @Override public synchronized void rednetMessage(int sender, String protocol, GraphValue data){
        if(closed || registered == null || !registered.autoStart()) return;
        if(localMessages.size() >= 128) localMessages.removeFirst();
        localMessages.addLast(new LocalRednetMessage(sender, protocol, data));
    }
    private record LocalRednetMessage(int sender, String protocol, GraphValue data){}
    private void updateModemPosition(){
        if(modem == null) return;
        var level = controller.getLevel();
        var position = com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.projectOut(level,
                com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.toWorldPosition(controller, net.minecraft.world.phys.Vec3.atCenterOf(controller.getBlockPos())));
        modem.position(level, position);
    }
    private void stop(){
        if(modem != null){ modem.close(); modem = null; }
        if(computer != null) computer.unload();
        computer = null; terminal = null; terminalSnapshot = null;
        synchronizedRequest = null; active = null; pending = null;
        initialized = false; mainRunning = false; handlerRunning = false;
        execPulses.clear(); portChanges.clear(); changedOutputs.clear(); removedOutputs.clear(); completed.clear();
        localMessages.clear();
    }
    @Override public synchronized void close(){
        closed = true; stop(); outputValues.clear();
        try{ displaySource.close(); }catch(Exception ex){ throw new IllegalStateException("Cannot release Lua display source", ex); }
    }

    // Lua thread callbacks exchange immutable data under the session lock and never mutate the world
    public final class Bridge implements ILuaAPI{
        private final Computer owner = computer;
        @Override public String[] getNames(){ return new String[]{"__gng_script"}; }
        private boolean valid(){ return !closed && owner == computer; }

        @LuaFunction public Map<String, Object> next(){ synchronized(ComputerCraftLuaComputer.this){
            if(!valid()) return null;
            booted = true;
            if(pending == null && synchronizedRequest == registered) return null;
            ScriptRequest req = pending == null ? registered : pending;
            if(req == null) return null;
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("revision", req.revision());
            if(pending != null){ active = pending; pending = null; handlerRunning = true; update.put("run", true); update.put("exec", pendingPort); }
            var delta = ScriptUpdate.between(synchronizedRequest, req);
            if(delta.source() != null || synchronizedRequest == null || synchronizedRequest.autoStart() != req.autoStart()){
                update.put("source", LuaScripts.function(req.source())); update.put("autoStart", req.autoStart());
                Map<String, Object> retained = new LinkedHashMap<>();
                if(persistentOutputs) outputValues.forEach((name, val) -> retained.put(name, ScriptValues.encode(val)));
                update.put("retained", retained); persistentOutputs = false;
                mainRunning = req.autoStart();
            }
            if(delta.outputs() != null){
                Map<String, Object> defaults = new LinkedHashMap<>();
                delta.outputs().forEach((name, type) -> defaults.put(name, ScriptValues.defaultValue(type)));
                update.put("defaults", defaults);
            }
            Map<String, Object> inputs = new LinkedHashMap<>();
            delta.inputs().forEach((name, val) -> inputs.put(name, ScriptValues.encode(val)));
            if(!inputs.isEmpty()) update.put("inputs", inputs);
            if(!delta.removed().isEmpty()) update.put("removed", delta.removed().toArray(String[]::new));
            if(synchronizedRequest == null || !req.execInputs().equals(synchronizedRequest.execInputs())) update.put("execInputs", flags(req.execInputs()));
            if(synchronizedRequest == null || !req.execOutputs().equals(synchronizedRequest.execOutputs())) update.put("execOutputs", flags(req.execOutputs()));
            synchronizedRequest = req;
            return update;
        }}
        private Map<String, Boolean> flags(Set<String> names){ var res = new LinkedHashMap<String, Boolean>(); names.forEach(name -> res.put(name, true)); return res; }

        @LuaFunction public void validated(long revision, String error){ synchronized(ComputerCraftLuaComputer.this){
            if(valid()) completed.add(new ScriptResult(id, revision, Map.of(), error, false, ++sequence, "startup"));
        }}
        @LuaFunction public void complete(long revision, String error, String port){ synchronized(ComputerCraftLuaComputer.this){
            if(!valid()) return;
            if("startup".equals(port)) mainRunning = false;
            else{ active = null; handlerRunning = false; }
            completed.add(new ScriptResult(id, registered == null ? revision : registered.revision(), outputValues, error, true, ++sequence, port));
        }}
        @LuaFunction public void ready(boolean main, boolean handler, boolean ready){ synchronized(ComputerCraftLuaComputer.this){
            if(valid()){ mainRunning = main; handlerRunning = handler; initialized = ready; }
        }}
        @LuaFunction public void waiting(String main, String handler) throws dan200.computercraft.api.lua.LuaException{ synchronized(ComputerCraftLuaComputer.this){
            if(!valid()) return;
            if(!"task_complete".equals(main) && !"task_complete".equals(handler)) captureTerminal();
            if(modem == null || !modem.isOpen(65535)) return;
            if(!Set.of("*", "rednet_message").contains(main) && !Set.of("*", "rednet_message").contains(handler)) return;
            while(!localMessages.isEmpty()){
                var message = localMessages.removeFirst();
                computer.queueEvent("rednet_message", new Object[]{message.sender(), ScriptValues.encode(message.data()), message.protocol()});
            }
        }}
        @LuaFunction public void persistence(boolean enabled){ synchronized(ComputerCraftLuaComputer.this){ if(valid()) persistentOutputs = enabled; }}

        @LuaFunction public void port(String name, boolean output, boolean execution, boolean remove, int line) throws dan200.computercraft.api.lua.LuaException{ synchronized(ComputerCraftLuaComputer.this){
            if(!valid() || registered == null) return;
            try{
                String declaration = com.rieno.gadgetsandgizmos.lib.script.ScriptPortDeclarations.at(registered.source(), line, output, execution, name);
                var before = ScriptPorts.snapshot(portData);
                ScriptPorts.programmatic(portData, name, output, execution, remove, declaration);
                if(before.equals(ScriptPorts.snapshot(portData))) return;
                if(portChanges.size() >= 128) throw new IllegalArgumentException("Too many port changes");
                portChanges.add(new ScriptPortChange(id, registered.revision(), name, output, execution, remove, declaration));
                if(output && !execution && remove){ outputValues.remove(name); changedOutputs.remove(name); removedOutputs.add(name); }
            }catch(IllegalArgumentException ex){ throw new dan200.computercraft.api.lua.LuaException(ex.getMessage()); }
        }}
        @LuaFunction public void outputs(long revision, Map<?, ?> changes, Map<?, ?> removed) throws dan200.computercraft.api.lua.LuaException{ synchronized(ComputerCraftLuaComputer.this){
            if(!valid()) return;
            try{
                Map<String, String> schema = new LinkedHashMap<>();
                for(Object key : changes.keySet()) if(key instanceof String name && portData.getCompound("DynamicOutputs").contains(name)) schema.put(name, "any");
                ScriptValues.outputs(changes, schema).forEach((name, val) -> {
                    if(!val.equals(outputValues.put(name, val))){ changedOutputs.put(name, val); removedOutputs.remove(name); }
                });
                for(Object val : removed.values()) if(val instanceof String name && outputValues.remove(name) != null){ changedOutputs.remove(name); removedOutputs.add(name); }
            }catch(IllegalArgumentException ex){ throw new dan200.computercraft.api.lua.LuaException(ex.getMessage()); }
        }}
        @LuaFunction public void pulse(String port) throws dan200.computercraft.api.lua.LuaException{ synchronized(ComputerCraftLuaComputer.this){
            if(!valid() || registered == null) return;
            if(!portData.getCompound(ScriptPorts.EXEC_OUTPUTS).contains(port)) throw new dan200.computercraft.api.lua.LuaException("Unknown execution output");
            if(execPulses.size() >= 128) throw new dan200.computercraft.api.lua.LuaException("Too many execution pulses");
            execPulses.add(new ScriptExecPulse(id, registered.revision(), port, outputValues));
        }}
    }

    // Copy terminal state while holding the same lock used by CC's Lua thread
    @Override public synchronized ScriptTerminal terminal(){
        if(initialized) return terminalSnapshot;
        return captureTerminal();
    }

    // Publish a complete draw pass rather than sampling a cleared screen during a native world call
    private ScriptTerminal captureTerminal(){
        if(terminal == null) return null;
        synchronized(terminal){
            long version = terminalVersion.get();
            if(sampledTerminalVersion == version && terminalSnapshot != null) return terminalSnapshot;
            terminalSnapshot = com.rieno.gadgetsandgizmos.compat.computercraft.rdp.ComputerCraftTerminalPreview.snapshot(terminal);
            sampledTerminalVersion = version;
            return terminalSnapshot;
        }
    }

    // Share CC's scheduler within this server while keeping computer memory and files separate
    private static final class Host implements GlobalEnvironment{
        private final MinecraftServer server;
        private final MainThread mainThread = new MainThread();
        private final ComputerContext ctx;

        private Host(MinecraftServer server){
            this.server = server;
            ctx = ComputerContext.builder(this).mainThreadScheduler(mainThread).build();
        }

        @Override public String getHostString(){
            return "ComputerCraft " + ComputerCraftAPI.getInstalledVersion() + " (Minecraft " + SharedConstants.getCurrentVersion().getName() + ")";
        }
        @Override public String getUserAgent(){ return "computercraft/" + ComputerCraftAPI.getInstalledVersion(); }
        @Override public Mount createResourceMount(String domain, String path){ return ComputerCraftAPI.createResourceMount(server, domain, path); }
        @Override public InputStream createResourceFile(String domain, String path){
            try{
                var res = server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(domain, path));
                return res.isEmpty() ? null : res.get().open();
            }catch(IOException ex){ return null; }
        }
    }
}
