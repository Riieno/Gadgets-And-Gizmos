package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftLuaComputer;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import com.rieno.gadgetsandgizmos.lib.script.ScriptRequest;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise real CC computers through the same port snapshots and graph scheduler used in game
@GameTestHolder("createthrusters_lua_scripts")
@PrefixGameTestTemplate(false)
public final class LuaScriptGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void computerSupportsCcApisBridgeTablesAndNamedEvents(GameTestHelper helper){
        var id = UUID.randomUUID();
        var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.submit(new ScriptRequest(id, 1, "local _, name, data = os.pullEvent('gng_named_event')\n"
                + "output.result = ports.value * 2 + data.amount\noutput.api = gng.getApiVersion()\n"
                + "output.items = {ports.value, math.sqrt(16)}\noutput.hidden = _G.__gng_script == nil\n"
                + "output.hub = peripheral.hasType('back', 'peripheral_hub')\noutput.host = _HOST\noutput.day = os.day()",
                Map.of("value", GraphValue.number(6)), Map.of("result", "number", "api", "number", "items", "list", "hidden", "boolean",
                        "hub", "boolean", "host", "string", "day", "number")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick();
            computer.namedEvent("example", GraphValue.map(Map.of("amount", 3)));
            var res = execution(computer);
            if(res == null) return;
            done[0] = true;
            computer.close();
            helper.assertTrue(res.valid(), "CC execution failed: " + res.error());
            helper.assertTrue(res.outputs().get("result").asNumber() == 15, "Input or named-event data was lost");
            helper.assertTrue(res.outputs().get("api").asNumber() == 2, "The existing G&G API was unavailable");
            helper.assertTrue(res.outputs().get("hub").asBoolean(), "The controller's native peripheral bridge was not attached");
            helper.assertTrue(res.outputs().get("host").asString().contains("Minecraft") && res.outputs().get("day").asNumber() >= 1,
                    "The headless computer did not expose the normal CC host and clock APIs");
            helper.assertTrue(res.outputs().get("items").value().equals(List.of(6.0, 4.0)), "Lua array output changed");
            helper.assertTrue(res.outputs().get("hidden").asBoolean(), "The private completion bridge leaked into player scope");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void yieldingScriptsCanWaitBeyondTheBootDeadline(GameTestHelper helper){
        var id = UUID.randomUUID();
        var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.submit(new ScriptRequest(id, 1, "sleep(11)\noutput.result = 1", Map.of(), Map.of("result", "number")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick();
            var res = execution(computer);
            if(res == null) return;
            done[0] = true;
            computer.close();
            helper.assertTrue(res.valid() && res.outputs().get("result").asNumber() == 1,
                    "A yielding script was stopped before returning its result: " + res);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void editingSleepingCodeCancelsItsOldCompletion(GameTestHelper helper){
        var id = UUID.randomUUID();
        var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.submit(new ScriptRequest(id, 1, "sleep(2)\noutput.result = -1", Map.of(), Map.of("result", "number")));
        boolean[] done = {false};
        boolean[] revised = {false};
        boolean[] returned = {false};
        int[] tick = {0};
        int[] settle = {-1};
        helper.runAtTickTime(20, () -> {
            revised[0] = true;
            computer.submit(new ScriptRequest(id, 2, "output.result = 9", Map.of(), Map.of("result", "number")));
        });
        helper.onEachTick(() -> {
            if(done[0]) return;
            tick[0]++;
            computer.tick();
            var res = execution(computer);
            if(res != null){
                helper.assertTrue(revised[0] && res.revision() == 2 && res.valid() && res.outputs().get("result").asNumber() == 9,
                        "Cancelled code published an old completion: " + res);
                returned[0] = true;
                settle[0] = tick[0] + 60;
            }
            if(returned[0] && tick[0] >= settle[0]){
                done[0] = true;
                computer.close();
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sqlSourcesAreSeparateScopedAndReloadWithoutPlacedNodes(GameTestHelper helper){
        try{
            var graph = new AdvancedGraphDocument();
            var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
            data.putString(LuaGraphNode.SOURCE, "output.result = 'SQL only'");
            var function = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "SQL script", data);
            graph.luaFunctions().add(function);
            String owner = UUID.randomUUID().toString();
            var controllerData = new CompoundTag();
            controllerData.putString("CustomName", "Lua SQL validation");
            var saved = ControllerSqliteStore.saveController(owner, 1, "advanced", helper.getLevel(), BlockPos.ZERO, null,
                    controllerData, graph.toTag(), new CompoundTag(), "");
            helper.assertTrue(saved != null, "The SQL graph save failed");
            var method = ControllerSqliteStore.class.getDeclaredMethod("connection", net.minecraft.world.level.Level.class);
            method.setAccessible(true);
            var connection = (java.sql.Connection)method.invoke(null, helper.getLevel());
            try(var query = connection.prepareStatement("SELECT source FROM Lua_Nodes WHERE owner_id=? AND graph_role='draft' AND function_id=?")){
                query.setString(1, owner); query.setString(2, function.id());
                try(var rows = query.executeQuery()){ helper.assertTrue(rows.next() && rows.getString(1).equals(data.getString(LuaGraphNode.SOURCE)), "Lua source was not in its dedicated table"); }
            }
            try(var query = connection.prepareStatement("SELECT graph_nbt FROM graphs WHERE owner_id=? AND graph_role='draft'")){
                query.setString(1, owner);
                try(var rows = query.executeQuery()){
                    helper.assertTrue(rows.next(), "The graph SQL row was missing");
                    var stored = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(rows.getBytes(1)), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    var store = new com.rieno.gadgetsandgizmos.lib.script.ScriptSourceStore("Lua_Nodes", "LuaSource", "LuaSourceRef");
                    helper.assertTrue(!store.embedded(stored), "The graph blob still contained embedded Lua code");
                    var hydrated = AdvancedGraphDocument.fromTag(store.hydrate(connection, "controller", owner, "draft", stored));
                    helper.assertTrue(hydrated.nodes().isEmpty() && hydrated.luaFunctions().size() == 1
                            && hydrated.luaFunctions().getFirst().data().getString(LuaGraphNode.SOURCE).equals(data.getString(LuaGraphNode.SOURCE)),
                            "An unplaced function failed its SQL round trip");
                    boolean rejected = false;
                    try{ store.hydrate(connection, "controller", UUID.randomUUID().toString(), "draft", stored); }
                    catch(java.sql.SQLException ex){ rejected = true; }
                    helper.assertTrue(rejected, "A script reference resolved across controller scopes");
                }
            }
            var raw = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(graph.toTag(), raw);
            try(var update = connection.prepareStatement("UPDATE graphs SET graph_nbt=? WHERE owner_id=? AND graph_role='draft'")){
                update.setBytes(1, raw.toByteArray()); update.setString(2, owner); update.executeUpdate();
            }
            var migrated = ControllerManifestStore.loadController(owner, helper.getLevel());
            helper.assertTrue(migrated != null && !migrated.draftGraph().isEmpty(), "A legacy embedded source failed to migrate on load");
            try(var query = connection.prepareStatement("SELECT graph_nbt FROM graphs WHERE owner_id=? AND graph_role='draft'")){
                query.setString(1, owner);
                try(var rows = query.executeQuery()){
                    helper.assertTrue(rows.next(), "The migrated graph row was missing");
                    var stored = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(rows.getBytes(1)), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    helper.assertTrue(!new com.rieno.gadgetsandgizmos.lib.script.ScriptSourceStore("Lua_Nodes", "LuaSource", "LuaSourceRef").embedded(stored),
                            "Legacy Lua code was left in the graph blob after loading");
                }
            }
            helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void storedTargetsExposeNativeMonitorWrapCallFindAndTerm(GameTestHelper helper){
        var controller = controller(helper);
        BlockPos displayPos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(displayPos, CTBlocks.ACC_DISPLAY.get().defaultBlockState(), 3);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(displayPos, null, "createthrusters:acc_display", "Lua Monitor",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("controllerStoredTargets");
            field.setAccessible(true);
            ((ControllerStoredTargets)field.get(controller)).read(ContraptionNetworkLinkerData.writeRoot(List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT));
        }catch(Exception ex){ throw new RuntimeException(ex); }
        var id = UUID.randomUUID();
        var computer = new ComputerCraftLuaComputer(controller, id);
        computer.submit(new ScriptRequest(id, 1,
                "local name = assert(gng.getPeripherals().monitor_0, 'Stored monitor was unavailable')\n"
                + "local monitor = assert(peripheral.wrap(name))\nterm.redirect(monitor)\nterm.setCursorPos(1, 1)\nwrite('Lua target')\n"
                + "local width, height = peripheral.call(name, 'getSize')\noutput.result = width\n"
                + "output.found = peripheral.getName(peripheral.find('monitor')) == name\noutput.type = peripheral.hasType(name, 'monitor')",
                Map.of(), Map.of("result", "any", "found", "any", "type", "any")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick();
            var result = execution(computer);
            if(result == null) return;
            helper.assertTrue(result.valid(), "Stored target peripheral failed: " + result.error());
            helper.assertTrue(result.outputs().get("result").asNumber() > 0 && result.outputs().get("found").asBoolean()
                    && result.outputs().get("type").asBoolean(), "Native monitor methods did not reach the stored target");
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void linkerAliasWrapCallsTheActualThruster(GameTestHelper helper){
        var controller = controller(helper); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(pos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "thruster",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller, id);
        computer.submit(new ScriptRequest(id, 1, "local thruster = assert(peripheral.wrap('thruster'), 'Linker alias did not resolve')\n"
                + "thruster.setThrottle(ports.value)\nsleep(0.1)\noutput.result = thruster.getStatus().computerThrottle\n"
                + "output.type = peripheral.hasType('thruster', 'thruster')\nprint(output.result)",
                Map.of("value", GraphValue.number(0.625)), Map.of("result", "any", "type", "any")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick(); var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid(), "Linker thruster script failed: " + result.error());
            helper.assertTrue(result.outputs().get("result").asNumber() == 0.625 && result.outputs().get("type").asBoolean(), "The alias did not call the real thruster methods");
            var catalog = ComputerCraftLuaComputer.peripherals(controller);
            helper.assertTrue(catalog.stream().anyMatch(val -> val.aliases().contains("thruster") && val.methods().contains("setThrottle")
                    && val.references().contains(ContraptionNetworkLinkerData.nodeIdForTarget(target))), "Completion metadata differed from the live linker peripheral");
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void duplicateLinkerAliasesRetainUniqueNativeNames(GameTestHelper helper){
        var controller = controller(helper); var targets = new java.util.ArrayList<ContraptionNetworkLinkerData.LinkedTarget>();
        for(int idx = 0; idx < 2; idx++){
            var pos = helper.absolutePos(new BlockPos(2 + idx, 1, 1));
            helper.getLevel().setBlock(pos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
            targets.add(new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "thruster",
                    ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of()));
        }
        var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, targets, ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        var catalog = ComputerCraftLuaComputer.peripherals(controller);
        helper.assertTrue(catalog.size() == 2 && catalog.stream().noneMatch(val -> val.aliases().contains("thruster"))
                && catalog.stream().map(com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral::name).distinct().count() == 2,
                "Duplicate labels resolved to an arbitrary target or lost their stable native names");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void renamedLinkerTargetsOverrideCopiedAndGraphLabels(GameTestHelper helper){
        var controller = controller(helper); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(pos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "thruster",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("controllerStoredTargets"); field.setAccessible(true);
            ((ControllerStoredTargets)field.get(controller)).read(ContraptionNetworkLinkerData.writeRoot(List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT));
            var graph = new AdvancedGraphDocument(); var data = node("read", "get_block_data");
            data.data().put("TargetData", new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode(
                    ContraptionNetworkLinkerData.nodeIdForTarget(target), target.mode().discoveryKind(), "", target.blockId(), target.label(), null, pos).toTag());
            graph.nodes().add(data);
            field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("draftGraph"); field.setAccessible(true); field.set(controller, graph);
        }catch(Exception ex){ throw new RuntimeException(ex); }
        var before = ComputerCraftLuaComputer.peripherals(controller).getFirst();
        var renamed = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "engine",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(renamed), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        var after = ComputerCraftLuaComputer.peripherals(controller).getFirst();
        helper.assertTrue(after.name().equals(before.name()) && after.aliases().equals(List.of("engine")), "An old copied or graph label overrode the current linker alias");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sourceAutosaveOnlyChangesOneSqlEntry(GameTestHelper helper){
        try{
            var controller = controller(helper);
            var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
            String previous = "output.result = 1", source = "output.result = 2";
            data.putString(LuaGraphNode.SOURCE, previous);
            var function = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Autosave", data);
            helper.assertTrue(controller.createLuaFunction(function), "Could not create the SQL function");
            var graph = controller.getDraftGraph();
            helper.assertTrue(controller.saveDraft(graph, graph.revision()), "Could not save the initial graph");
            var rawField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("draftGraph");
            rawField.setAccessible(true);
            var raw = ((AdvancedGraphDocument)rawField.get(controller)).toTag();
            var method = ControllerSqliteStore.class.getDeclaredMethod("connection", net.minecraft.world.level.Level.class);
            method.setAccessible(true);
            var connection = (java.sql.Connection)method.invoke(null, helper.getLevel());
            byte[] metadata;
            try(var query = connection.prepareStatement("SELECT definition_nbt FROM Lua_Nodes WHERE owner_id=? AND graph_role='library' AND function_id=?")){
                query.setString(1, controller.getScmPersistenceId().toString()); query.setString(2, function.id());
                try(var rows = query.executeQuery()){
                    helper.assertTrue(rows.next(), "The standalone function row is missing"); metadata = rows.getBytes(1);
                }
            }
            long changes = sqlChanges(connection);
            helper.assertTrue(controller.saveLuaSource(function.id(), previous, source), "Source autosave failed");
            helper.assertTrue(sqlChanges(connection) == changes + 1, "Autosave wrote more than its single Lua_Nodes row");
            helper.assertTrue(raw.equals(((AdvancedGraphDocument)rawField.get(controller)).toTag()), "Source autosave mutated the persisted graph");
            helper.assertTrue(controller.getDraftGraph().revision() == graph.revision() + 1, "Source autosave changed the graph revision");
            var loaded = ControllerSqliteStore.loadLuaFunctions(helper.getLevel(), controller.getScmPersistenceId());
            helper.assertTrue(loaded.size() == 1 && source.equals(loaded.getFirst().data().getString(LuaGraphNode.SOURCE)), "The source did not reload from SQL");
            helper.assertTrue(source.equals(controller.getDraftGraph().luaFunctions().getFirst().data().getString(LuaGraphNode.SOURCE)), "The editor graph did not read the standalone source");
            try(var query = connection.prepareStatement("SELECT definition_nbt FROM Lua_Nodes WHERE owner_id=? AND graph_role='library' AND function_id=?")){
                query.setString(1, controller.getScmPersistenceId().toString()); query.setString(2, function.id());
                try(var rows = query.executeQuery()){
                    helper.assertTrue(rows.next() && java.util.Arrays.equals(metadata, rows.getBytes(1)), "Source autosave rewrote ports or metadata");
                }
            }
            helper.assertTrue(controller.saveLuaSource(function.id(), previous, source), "An unchanged source acknowledgement failed");
            helper.assertTrue(!controller.saveLuaSource(function.id(), previous, "output.result = 3"), "A stale editor overwrote newer code");
            helper.assertTrue(sqlChanges(connection) == changes + 1, "Identical or rejected code caused a SQL write");
            helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void virtualModemsExchangeNativeRednetMessages(GameTestHelper helper){
        var controller = controller(helper);
        var senderId = UUID.randomUUID();
        var receiverId = UUID.randomUUID();
        var sender = new ComputerCraftLuaComputer(controller, senderId);
        var receiver = new ComputerCraftLuaComputer(controller, receiverId);
        receiver.submit(new ScriptRequest(receiverId, 1,
                "assert(rednet.isOpen('left'), 'Virtual modem was not opened')\n"
                + "assert(peripheral.find('modem').isWireless(), 'Not a wireless modem')\n"
                + "local sender, message = rednet.receive('gng_lua_modem', 60)\nassert(sender, 'No rednet packet arrived')\n"
                + "assert(message.value == 42, 'Rednet table payload changed')\n"
                + "rednet.send(sender, {answer = message.value + 1}, 'gng_lua_reply')\noutput.result = message.value",
                Map.of(), Map.of("result", "any")));
        sender.submit(new ScriptRequest(senderId, 1,
                "assert(rednet.isOpen('left'), 'Virtual modem was not opened')\n"
                + "for attempt = 1, 100 do\nrednet.broadcast({value = 42}, 'gng_lua_modem')\n"
                + "local sender, reply = rednet.receive('gng_lua_reply', 0.5)\n"
                + "if sender then output.result = reply.answer return end\nend\nerror('No rednet reply arrived')",
                Map.of(), Map.of("result", "any")));
        boolean[] received = {false}, sent = {false};
        helper.onEachTick(() -> {
            if(received[0] && sent[0]) return;
            receiver.tick(); sender.tick();
            var a = execution(sender); var b = execution(receiver);
            if(a != null){
                helper.assertTrue(a.valid() && a.outputs().get("result").asNumber() == 43, "Native rednet send/reply failed: " + a);
                sent[0] = true;
            }
            if(b != null){
                helper.assertTrue(b.valid() && b.outputs().get("result").asNumber() == 42, "Native rednet receive failed: " + b);
                received[0] = true;
            }
            if(received[0] && sent[0]){ sender.close(); receiver.close(); helper.succeed(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void liveInputsAndSchemaUpdatesNeverRestartTheProgram(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        String code = "local initial = ports.value\noutput.boot = initial\nwhile true do output.result = ports.value sleep(0) end";
        var schema = Map.of("result", "any", "boot", "any");
        computer.register(new ScriptRequest(id, 1, code, Map.of("value", GraphValue.number(7)), schema));
        int[] stage = {0}; Object[] nativeComputer = {null};
        helper.onEachTick(() -> {
            if(stage[0] == 3) return;
            computer.tick();
            var result = execution(computer);
            if(result != null) helper.assertTrue(result.valid(), "The running program failed: " + result);
            var delta = computer.pollOutput();
            if(delta == null) return;
            if(stage[0] == 0 && delta.changed().containsKey("boot")){
                helper.assertTrue(delta.changed().get("boot").asNumber() == 7, "Initial source did not run on load");
                try{ var field = ComputerCraftLuaComputer.class.getDeclaredField("computer"); field.setAccessible(true); nativeComputer[0] = field.get(computer); }
                catch(Exception ex){ throw new RuntimeException(ex); }
                computer.register(new ScriptRequest(id, 2, code, Map.of("value", GraphValue.number(11)), Map.of("result", "any", "boot", "any", "extra", "any")));
                stage[0] = 1;
            }else if(stage[0] == 1 && delta.changed().containsKey("result") && delta.changed().get("result").asNumber() == 11){
                helper.assertTrue(!delta.changed().containsKey("boot"), "Input or output schema updates ran the program again");
                try{ var field = ComputerCraftLuaComputer.class.getDeclaredField("computer"); field.setAccessible(true); helper.assertTrue(field.get(computer) == nativeComputer[0], "Port changes restarted the computer"); }
                catch(Exception ex){ throw new RuntimeException(ex); }
                computer.register(new ScriptRequest(id, 3, "output.result = 19", Map.of(), schema)); stage[0] = 2;
            }else if(stage[0] == 2 && delta.changed().containsKey("result") && delta.changed().get("result").asNumber() == 19){
                computer.close(); stage[0] = 3; helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void namedHandlersRunAlongsideUserLoopsAndIgnoreBusyPulses(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        String code = "node.persistentOutputs = true\nnode.addExecIn('advance')\nnode.addExecOut('ready')\nlocal count = 0\n"
                + "execEvent.on('advance').run = function() count = count + 1 output.result = ports.value + count "
                + "execEvent.pulse('ready') os.pullEvent('gng_named_event') end\nwhile true do sleep(0) end";
        var schema = Map.of("result", "any");
        computer.register(new ScriptRequest(id, 1, code, Map.of("value", GraphValue.number(10)), schema));
        int[] stage = {0}, settle = {0};
        helper.onEachTick(() -> {
            if(stage[0] == 5) return;
            computer.tick();
            var result = execution(computer); var pulse = computer.pollExec();
            if(result != null) helper.assertTrue(result.valid(), "Custom handler failed: " + result);
            if(stage[0] == 0 && computer.acceptsPulse("advance")){
                computer.submit(new ScriptRequest(id, 2, code, Map.of("value", GraphValue.number(10)), schema), "advance"); stage[0] = 1;
            }else if(stage[0] == 1 && pulse != null){
                helper.assertTrue(pulse.outputs().get("result").asNumber() == 11 && result == null, "The custom output did not pulse before its handler returned");
                computer.submit(new ScriptRequest(id, 3, code, Map.of("value", GraphValue.number(99)), schema), "advance");
                stage[0] = 2;
            }else if(stage[0] == 2){
                computer.namedEvent("resume", GraphValue.bool(true));
                if(result == null) return;
                helper.assertTrue(result.inputPort().equals("advance") && result.outputs().get("result").asNumber() == 11, "A busy pulse changed the active handler"); stage[0] = 3;
            }else if(stage[0] == 3){
                helper.assertTrue(pulse == null && result == null, "A busy pulse was queued");
                if(++settle[0] < 20) return;
                computer.submit(new ScriptRequest(id, 4, code, Map.of("value", GraphValue.number(20)), schema), "advance"); stage[0] = 4;
            }else if(stage[0] == 4){
                computer.namedEvent("resume", GraphValue.bool(true));
                if(result == null) return;
                helper.assertTrue(result.outputs().get("result").asNumber() == 22, "Live input deltas or retained handler locals were lost");
                computer.close(); stage[0] = 5; helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void persistentOutputsSurviveHandlersAndCodeRestartsAndFalseResetsThem(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        String body = "node.addExecIn('increment')\nnode.addExecOut('done')\nnode.addOutputPort('counter')\n"
                + "execEvent.on('increment').run = function() output.counter = (tonumber(output.counter) or 0) + 1 execEvent.pulse('done') end";
        String[] source = {"node.persistentOutputs = true\n" + body};
        var schema = Map.of("counter", "any");
        int[] count = {0}; long[] revision = {1}; boolean[] awaiting = {false}, readyToChange = {false};
        computer.register(new ScriptRequest(id, revision[0], source[0], Map.of(), schema));
        helper.onEachTick(() -> {
            if(count[0] == 5) return;
            computer.tick();
            var result = execution(computer);
            if(result != null) helper.assertTrue(result.valid(), "Persistent output script failed: " + result);
            if(result != null && result.inputPort().equals("increment")){ awaiting[0] = false; readyToChange[0] = true; }
            var pulse = computer.pollExec();
            if(pulse != null){
                int expected = count[0] < 3 ? count[0] + 1 : 1;
                helper.assertTrue(pulse.outputs().get("counter").asNumber() == expected, "Persistent output value was reset or false retained an old value: " + pulse);
                count[0]++;
                if(count[0] == 5){ computer.close(); helper.succeed(); return; }
            }
            if(!awaiting[0] && computer.acceptsPulse("increment")){
                if(count[0] == 2 && readyToChange[0]){
                    source[0] += "\n-- Restart with retention"; readyToChange[0] = false;
                    computer.register(new ScriptRequest(id, ++revision[0], source[0], Map.of(), schema)); return;
                }
                if(count[0] == 3 && readyToChange[0]){
                    source[0] = "node.persistentOutputs = false\n" + body; readyToChange[0] = false;
                    computer.register(new ScriptRequest(id, ++revision[0], source[0], Map.of(), schema)); return;
                }
                computer.submit(new ScriptRequest(id, ++revision[0], source[0], Map.of(), schema), "increment"); awaiting[0] = true;
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void customExecRoutesDataAndExposesTheComputerAsADisplayTarget(GameTestHelper helper){
        var graph = new AdvancedGraphDocument(); var script = node("script", "createthrusters:lua");
        com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.change(script.data(), "done", true, true, false);
        script.data().putString(LuaGraphNode.SOURCE, "node.addExecOut('done')\nterm.setCursorPos(1, 1) term.setTextColor(colors.red) "
                + "term.setBackgroundColor(colors.blue) term.write('Lua GUI') term.setCursorBlink(true)\noutput.result = 37 execEvent.pulse('done')");
        var capture = node("capture", "variable_set"); capture.data().putString("Variable", "answer"); capture.data().putString("VariableType", "number");
        capture.data().getCompound("DynamicInputs").putString("value", "number"); capture.data().getCompound("DynamicOutputs").putString("value", "number");
        capture.data().getCompound("Defaults").put("type", AdvancedGraphDocument.Value.string("number").toTag());
        graph.nodes().addAll(List.of(script, capture));
        graph.edges().add(new AdvancedGraphDocument.Edge("done", "script", "done", "capture", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("data", "script", "result", "capture", "value"));
        var runtime = new GraphRuntime(controller(helper), true); runtime.replaceSimulationGraph(null, graph); runtime.runtimeChanges(null, 0);
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            runtime.tickSimulation(graph, null); assertNoError(helper, runtime, "script");
            var result = runtime.liveOutput("capture", "value");
            var reference = runtime.liveOutput("script", LuaGraphNode.INSTANCE);
            if(result == null || result.asNumber() != 37 || reference == null) return;
            helper.assertTrue("target".equals(reference.type()) && com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.isReference(reference.payload()), "The CC instance output was not a virtual target");
            var screen = runtime.liveOutput("script", com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.PORT);
            var terminal = screen == null ? null : com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.fromValue(GraphRuntime.toLibraryValue(screen));
            helper.assertTrue(terminal != null && terminal.text().getFirst().startsWith("Lua GUI") && terminal.foreground().getFirst().startsWith("e") && terminal.background().getFirst().startsWith("b") && terminal.blink(), "Native terminal text or colors did not reach the inspector");
            try{
                var display = new AccDisplayBlockEntity(helper.absolutePos(new BlockPos(2, 1, 1)), CTBlocks.ACC_DISPLAY.get().defaultBlockState()); display.setLevel(helper.getLevel());
                var method = AccDisplayBlockEntity.class.getDeclaredMethod("externalSourceFrame", AdvancedGraphDocument.Node.class, CompoundTag.class, int.class, int.class); method.setAccessible(true);
                var frame = (CompoundTag)method.invoke(display, node("external", "acc_display_external"), reference.payload(), 500, 200);
                helper.assertTrue("terminal".equals(frame.getString("Format")) && frame.getBoolean("PreserveAspect") && frame.getCompound("Terminal").getString("term_text_0").startsWith("Lua GUI"), "The instance target did not resolve through ACC Display External Source");
            }catch(Exception ex){ throw new RuntimeException(ex); }
            var target = reference.payload().copy(); runtime.clear();
            helper.assertTrue(com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.frame(helper.getLevel().getServer(), target, 500, 200).isEmpty(), "The headless display reference leaked after computer removal");
            done[0] = true; helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void unplacedFunctionsOnlyValidateAndProgramsRunOnce(GameTestHelper helper){
        var graph = new AdvancedGraphDocument(); var script = node("once", "createthrusters:lua");
        script.data().putString(LuaGraphNode.SOURCE, "output.result = ports.value");
        script.data().getCompound("Defaults").put("value", AdvancedGraphDocument.Value.number(8).toTag());
        graph.nodes().add(script);
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
        data.putString(LuaGraphNode.SOURCE, "error('Unplaced function executed')");
        var unused = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Unused", data); graph.luaFunctions().add(unused);
        var runtime = new GraphRuntime(controller(helper), true); runtime.replaceSimulationGraph(null, graph);
        int[] stage = {0}, settle = {0};
        helper.onEachTick(() -> {
            if(stage[0] == 2) return;
            runtime.tickSimulation(graph, null); assertNoError(helper, runtime, "once"); assertNoError(helper, runtime, "lua-library:" + unused.id());
            var result = runtime.liveOutput("once", "result");
            if(result == null || result.asNumber() != 8) return;
            if(stage[0] == 0){ script.data().getCompound("Defaults").put("value", AdvancedGraphDocument.Value.number(20).toTag()); stage[0] = 1; }
            else if(++settle[0] > 40){
                helper.assertTrue(result.asNumber() == 8, "Input changes reran a completed program");
                var valid = runtime.liveOutput("lua-library:" + unused.id(), "valid"); if(valid == null || !valid.asBoolean()) return;
                runtime.clear(); stage[0] = 2; helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void programmaticPortsFollowSavedSourceWithoutGraphSaves(GameTestHelper helper){
        var controller = controller(helper); var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
        String initial = "node.addInputPort('sensor_' .. tostring(math.floor(ports.value)))\nnode.addOutputPort('temperature')\nnode.addExecIn('request')\nnode.addExecOut('done')\noutput.temperature = 42";
        data.putString(LuaGraphNode.SOURCE, initial); data.getCompound("Defaults").put("value", AdvancedGraphDocument.Value.number(6).toTag());
        var function = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Persistent interface", data);
        helper.assertTrue(controller.createLuaFunction(function), "Could not create the persistent function");
        var placed = data.copy(); function.apply(placed);
        graph.luaFunctions().add(function); graph.nodes().add(new AdvancedGraphDocument.Node("script", "createthrusters:lua", "Script", 0, 0, placed));
        helper.assertTrue(controller.saveDraft(graph, 0), "Could not save the initial placed node");
        int revision = controller.getDraftGraph().revision();
        var runtime = new GraphRuntime(controller, true); runtime.replaceSimulationGraph(null, controller.getDraftGraph());
        byte[] stored;
        try{ stored = graphBytes(helper, controller.controllerManifestId()); }catch(Exception ex){ throw new RuntimeException(ex); }
        int[] stage = {0};
        helper.onEachTick(() -> {
            if(stage[0] == 3) return;
            runtime.tickSimulation(controller.getDraftGraph(), null); assertNoError(helper, runtime, "script");
            var current = controller.getDraftGraph().nodes().getFirst();
            var outputs = AdvancedGraphCatalog.outputs(current); var inputs = AdvancedGraphCatalog.inputs(current);
            try{
                helper.assertTrue(controller.getDraftGraph().revision() == revision && java.util.Arrays.equals(stored, graphBytes(helper, controller.controllerManifestId())), "Programmatic port changes saved the graph");
                if(stage[0] == 0 && inputs.containsKey("sensor_6") && inputs.containsKey("request") && outputs.containsKey("temperature") && outputs.containsKey("done")){
                    var saved = ControllerSqliteStore.loadLuaFunctions(helper.getLevel(), controller.getScmPersistenceId()).getFirst();
                    helper.assertTrue(com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode.ports(saved.data(), false).containsKey("sensor_6"), "Programmatic ports were not persisted in Lua_Nodes");
                    helper.assertTrue(controller.saveLuaSource(function.id(), initial, "print('No declarations')"), "The source-only replacement failed"); stage[0] = 1;
                }else if(stage[0] == 1){
                    if(inputs.containsKey("sensor_6") || inputs.containsKey("request") || outputs.containsKey("temperature") || outputs.containsKey("done")) return;
                    helper.assertTrue(inputs.containsKey("value") && outputs.containsKey("result"), "Removing declarations erased authored ports");
                    helper.assertTrue(controller.saveLuaSource(function.id(), "print('No declarations')", initial), "Restoring declarations did not save"); stage[0] = 2;
                }else if(stage[0] == 2 && inputs.containsKey("sensor_6") && inputs.containsKey("request") && outputs.containsKey("temperature") && outputs.containsKey("done")){
                    runtime.clear(); stage[0] = 3; helper.succeed();
                }
            }catch(Exception ex){ throw new RuntimeException(ex); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void receivingScriptNeverInventsCoordinatesWithoutMessages(GameTestHelper helper){
        var controller = controller(helper); var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller, id);
        String source = "node.persistentOutputs = true\nnode.addExecOut('received')\nnode.addOutputPort('rec')\n"
                + "local openedWireless = false\nperipheral.find('modem', function(name, modem) if modem.isWireless() then rednet.open(name) openedWireless = true end return false end)\n"
                + "assert(openedWireless, 'Attach a wireless modem to this computer')\nlocal namedEvent = ports.string\nprint(namedEvent)\n"
                + "local sender, message = rednet.receive(namedEvent, 5)\nif sender == nil then else if message ~= nil then print(message) "
                + "output.sender = sender output.result = message execEvent.pulse('received') output.rec = true else output.rec = false end end";
        computer.register(new ScriptRequest(id, 1, source, Map.of("string", GraphValue.string("node:test2:no-sender"),
                "value", GraphValue.map(Map.of("x", 27.18559413330078, "y", -54.27858352661133, "z", 952.387451171875))), Map.of("result", "any", "sender", "any")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick();
            var update = computer.pollOutput();
            if(update != null) helper.assertTrue(!update.changed().containsKey("result") || update.changed().get("result").equals(GraphValue.string("")), "A coordinate or another input appeared without a received message: " + update);
            var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid(), "Exact receive script failed: " + result.error());
            helper.assertTrue(result.outputs().get("result").equals(GraphValue.string("")) && computer.pollExec() == null, "A timed-out receive fabricated a result or execution event: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void virtualComputerAcceptsNativeTerminalInput(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.register(new ScriptRequest(id, 1, "local seen = {}\nwhile true do local e,a,b,c = os.pullEvent() "
                + "if e == 'mouse_click' then assert(a == 1 and b == 26 and c == 10) seen.click = true "
                + "elseif e == 'key' then assert(a == keys.enter) seen.key = true elseif e == 'char' then assert(a == 'a') seen.char = true "
                + "elseif e == 'key_up' then seen.up = true elseif e == 'mouse_scroll' then seen.scroll = true "
                + "elseif e == 'mouse_up' then seen.release = true elseif e == 'mouse_drag' then seen.drag = true elseif e == 'paste' then seen.paste = a == 'pasted' end "
                + "if seen.click and seen.key and seen.char and seen.up and seen.scroll and seen.release and seen.drag and seen.paste then output.result = true return end end", Map.of(), Map.of("result", "any")));
        boolean[] sent = {false}, done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick();
            if(!sent[0] && computer.terminal() != null && computer.running()){
                var ref = com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.reference(id);
                for(var input : List.of(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", .5, .5, 0),
                        new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("key", 0, 0, 257), new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("char", 0, 0, 'a'),
                        new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("key_up", 0, 0, 257), new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("scroll", .5, .5, -1),
                        new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("release", .5, .5, 0), new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("drag", .5, .5, 0),
                        new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("paste", 0, 0, 0, "pasted")))
                    helper.assertTrue(com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.input(helper.getLevel().getServer(), ref, null, input), "The display source rejected terminal input");
            }
            var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid() && result.outputs().get("result").asBoolean(), "Terminal events were not delivered: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cachedGraphCoordinatesCannotBecomeAComputerOutput(GameTestHelper helper){
        var graph = new AdvancedGraphDocument(); var script = node("script", "createthrusters:lua");
        script.data().putString(LuaGraphNode.SOURCE, "node.persistentOutputs = true\nrednet.receive('node:test2', 5)");
        graph.nodes().add(script); var runtime = new GraphRuntime(controller(helper), true); runtime.compile(graph);
        try{
            var field = GraphRuntime.class.getDeclaredField("runtimeState"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var state = (Map<String, AdvancedGraphDocument.Value>)field.get(runtime);
            state.put("script:lua:result", GraphRuntime.fromLibraryValue(GraphValue.map(Map.of("x", 27.18559413330078, "y", -54.27858352661133, "z", 952.387451171875))));
            var result = runtime.previewOutput(graph, script, "result");
            helper.assertTrue(result.type().equals("string") && result.asString().isEmpty(), "A retained graph cache supplied a map that the computer never published: " + result);
            runtime.clear(); helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void accDisplayRoutesFittedInputToTheVirtualComputer(GameTestHelper helper){
        var controller = controller(helper); var displayPos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(displayPos, CTBlocks.ACC_DISPLAY.get().defaultBlockState(), 3);
        var display = (AccDisplayBlockEntity)helper.getLevel().getBlockEntity(displayPos);
        var graph = new AdvancedGraphDocument(); var external = node("external", "acc_display_external"); graph.nodes().add(external);
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LuaDisplayTest"), net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(displayPos));
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller, id);
        computer.register(new ScriptRequest(id, 1, "output.ready = true\nlocal events = {}\nwhile #events < 12 do local event,a,x,y = os.pullEvent() if event:find('mouse_',1,true) == 1 then events[#events+1] = {event,a,x,y} end end\noutput.result = events", Map.of(), Map.of("ready", "any", "result", "any")));
        boolean[] done = {false}, sent = {false};
        var expected = new java.util.ArrayList<Object>();
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick();
            var terminal = computer.terminal();
            if(!sent[0] && terminal != null && computer.output("ready") != null && computer.output("ready").asBoolean()){
                sent[0] = true;
                try{
                    var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph"); field.setAccessible(true); field.set(controller, graph);
                    field = AccDisplayBlockEntity.class.getDeclaredField("controller"); field.setAccessible(true); field.set(display, controller);
                    for(int[] authored : new int[][]{{320, 180}, {600, 180}, {160, 360}}){
                        var frame = new CompoundTag(); frame.putString("State", "external"); frame.putString("ContentNode", "external");
                        frame.putDouble("ContentWidth", authored[0]); frame.putDouble("ContentHeight", authored[1]); frame.putDouble("ContentScale", 1);
                        var sources = new net.minecraft.nbt.ListTag(); sources.add(new CompoundTag()); sources.add(new CompoundTag()); frame.put("Sources", sources);
                        frame.put("ExternalSourceData", com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.reference(id)); frame.put("External", terminal.displayFrame());
                        field = AccDisplayBlockEntity.class.getDeclaredField("displayFrame"); field.setAccessible(true); field.set(display, frame);
                        helper.assertTrue(display.isComputerCraftDisplaySource(), "The Lua frame was not recognized as an interactive computer");
                        double w = Math.max(1, Math.round(authored[0] / 320.0 * display.surfacePixelWidth()));
                        double h = Math.max(1, Math.round(authored[1] / 180.0 * (display.surfacePixelHeight() - AccDisplayBlockEntity.TASKBAR_PIXELS)));
                        double scale = Math.min(w / 306, h / 171);
                        helper.assertTrue(!display.submitComputerCraftInput(player, "click", 0, 0, 0), "Letterboxing became a terminal click");
                        int idx = 0;
                        for(String action : List.of("click", "drag", "release", "scroll")){
                            int col = new int[]{6, 48, 26, 1}[idx], row = new int[]{3, 17, 10, 19}[idx++];
                            double x = ((w - 306 * scale) / 2 + (col - .5) * 6 * scale) / w;
                            double y = ((h - 171 * scale) / 2 + (row - .5) * 9 * scale) / h;
                            helper.assertTrue(display.submitComputerCraftInput(player, action, x, y, action.equals("scroll") ? 1 : 0), "The display rejected fitted " + action);
                            expected.add(List.of(switch(action){ case "click" -> "mouse_click"; case "drag" -> "mouse_drag"; case "release" -> "mouse_up"; default -> "mouse_scroll"; }, 1.0, (double)col, (double)row));
                        }
                    }
                }catch(Exception ex){ throw new RuntimeException(ex); }
            }
            var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid() && result.outputs().get("result").value().equals(expected), "Display inputs did not match the rendered terminal cells: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    private static byte[] graphBytes(GameTestHelper helper, String owner) throws Exception{
        var method = ControllerSqliteStore.class.getDeclaredMethod("connection", net.minecraft.world.level.Level.class); method.setAccessible(true);
        var connection = (java.sql.Connection)method.invoke(null, helper.getLevel());
        try(var query = connection.prepareStatement("SELECT graph_nbt FROM graphs WHERE owner_id=? AND graph_role='draft'")){
            query.setString(1, owner); try(var rows = query.executeQuery()){ if(!rows.next()) throw new AssertionError("Missing graph row"); return rows.getBytes(1); }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void appliedNamedEventReachesRednetOutputsAndTerminal(GameTestHelper helper){ namedEventReachesRednet(helper, false, false); }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void draftNamedEventReachesRednetOutputsAndTerminal(GameTestHelper helper){ namedEventReachesRednet(helper, true, false); }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void initialDraftNamedEventWaitsForTheRednetListener(GameTestHelper helper){ namedEventReachesRednet(helper, true, true); }

    private static void namedEventReachesRednet(GameTestHelper helper, boolean simulation, boolean startup){
        String protocol = startup ? "node:test:startup" : simulation ? "node:test:draft" : "node:test:applied";
        var graph = new AdvancedGraphDocument(); var script = node("script", "createthrusters:lua");
        script.data().getCompound("DynamicInputs").putString("string", "any");
        script.data().getCompound("Defaults").put("string", AdvancedGraphDocument.Value.string(protocol).toTag());
        script.data().getCompound("DynamicOutputs").putString("sender", "any");
        script.data().putString(LuaGraphNode.SOURCE, "node.persistentOutputs = true\nlocal openedWireless = false\n"
                + "peripheral.find('modem', function(name, modem) if modem.isWireless() then rednet.open(name) openedWireless = true end return false end)\n"
                + "assert(openedWireless, 'Attach a wireless modem to this computer!')\nlocal namedEvent = ports.string\nprint(namedEvent)\n"
                + "while true do sleep(0) local sender, message = rednet.receive(namedEvent, 5) if sender ~= nil and message ~= nil then "
                + "print(message) output.sender = sender output.result = message end end");
        var sender = node("send", "send_named_controller_event");
        sender.data().putString("Event", protocol);
        sender.data().getCompound("Defaults").put("data", AdvancedGraphDocument.Value.string("This is a test").toTag());
        sender.data().getCompound("Defaults").put("distance", AdvancedGraphDocument.Value.number(0).toTag());
        graph.nodes().addAll(List.of(script, node("tick", startup ? "event_graph_ready" : "event_tick"), sender));
        graph.edges().add(new AdvancedGraphDocument.Edge("send", "tick", "exec", "send", "exec"));
        var runtime = new GraphRuntime(controller(helper), simulation);
        runtime.runtimeChanges(null, 0);
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            if(simulation) runtime.tickSimulation(graph, null); else runtime.tick(graph);
            assertNoError(helper, runtime, "script");
            var result = runtime.liveOutput("script", "result");
            if(result == null || !result.asString().equals("This is a test")) return;
            var screen = runtime.liveOutput("script", com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.PORT);
            var terminal = screen == null ? null : com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.fromValue(GraphRuntime.toLibraryValue(screen));
            if(terminal == null || terminal.text().stream().noneMatch(line -> line.contains("This is a test"))) return;
            helper.assertTrue(runtime.liveOutput("script", "sender").asNumber() != 0, "The sender id was not exposed");
            done[0] = true; runtime.clear(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void graphSaveCannotRollbackASourceOnlySave(GameTestHelper helper){
        var controller = controller(helper); var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
        data.putString(LuaGraphNode.SOURCE, "output.result = 'initial'");
        var function = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Save race", data);
        helper.assertTrue(controller.createLuaFunction(function), "Could not create the source entry");
        graph.luaFunctions().add(function);
        helper.assertTrue(controller.saveDraft(graph, 0), "Could not save the initial graph");
        var staleGraph = controller.getDraftGraph();
        helper.assertTrue(controller.saveLuaSource(function.id(), "output.result = 'initial'", "output.result = 'newer'"), "Could not save newer source");
        helper.assertTrue(controller.saveDraft(staleGraph, staleGraph.revision()), "Could not save the unrelated graph change");
        helper.assertTrue(controller.luaFunction(function.id()).data().getString(LuaGraphNode.SOURCE).equals("output.result = 'newer'"), "A stale graph save rolled back the SQL source");
        helper.assertTrue(controller.saveLuaSource(function.id(), "output.result = 'newer'", "output.result = 'next edit'"), "The next code save was permanently stuck on a stale baseline");
        var conflict = controller.writeLuaSource(function.id(), "output.result = 'newer'", "output.result = 'stale edit'");
        helper.assertTrue(conflict.status() == com.rieno.gadgetsandgizmos.lib.script.ScriptSourceStore.SourceStatus.CONFLICT
                && conflict.source().equals("output.result = 'next edit'"), "A rejected source save did not return the actual SQL baseline");
        helper.assertTrue(controller.saveLuaSource(function.id(), conflict.source(), "output.result = 'retried edit'"), "Rebasing a rejected edit could not recover its save");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyLuaReferencesRemainStableBeforeFirstGraphSave(GameTestHelper helper){
        var controller = controller(helper); var legacy = new AdvancedGraphDocument();
        legacy.nodes().add(node("legacy", "createthrusters:lua"));
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("draftGraph"); field.setAccessible(true); field.set(controller, legacy);
            String id = controller.getDraftGraph().luaFunctions().getFirst().id();
            helper.assertTrue(id.equals(controller.getDraftGraph().luaFunctions().getFirst().id()), "Reading a legacy node changed its SQL identity");
            helper.assertTrue(controller.saveLuaSource(id, LuaGraphNode.DEFAULT_SOURCE, "output.result = 52"), "A legacy node could not save code before its graph was saved");
            helper.assertTrue(controller.luaFunction(id).data().getString(LuaGraphNode.SOURCE).equals("output.result = 52"), "The saved legacy source was not retained");
            helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void requireLoadsCcPrettyInsideEachScriptSandbox(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        String source = "assert(require('_G') == _G)\nassert(_G.__gng_script == nil)\n"
                + "assert(require('debug') == debug and debug.getupvalue == nil)\n"
                + "assert(package.loaded.private_value == nil)\npackage.loaded.private_value = true\n"
                + "local pretty = require('cc.pretty')\nassert(require('cc.pretty') == pretty)\n"
                + "package.preload['player.module'] = function() return { value = ports.value, global = _G } end\n"
                + "local module = require('player.module')\nassert(module.global == _G and module.value == ports.value)\n"
                + "local ok, err = pcall(require, 'absent.module')\nassert(not ok and err:find('not found', 1, true))\n"
                + "pretty.print(pretty.pretty({value = module.value}))\noutput.result = pretty.render(pretty.pretty({value = module.value}))";
        computer.submit(new ScriptRequest(id, 1, source, Map.of("value", GraphValue.number(17)), Map.of("result", "string")));
        int[] stage = {0}; boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return;
            computer.tick(); var res = execution(computer); if(res == null) return;
            helper.assertTrue(res.valid() && res.outputs().get("result").asString().contains("17"), "Native require or sandbox failed: " + res);
            if(stage[0]++ == 0){
                computer.submit(new ScriptRequest(id, 2, source + "\n-- Restart module caches", Map.of("value", GraphValue.number(17)), Map.of("result", "string"))); return;
            }
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void luaRenameAndDeletionAreCanonicalAndRemoveAllReferences(GameTestHelper helper){
        try{
            var controller = controller(helper); var graph = new AdvancedGraphDocument();
            var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
            data.putString(LuaGraphNode.SOURCE, "output.result = 5");
            var definition = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Original", data);
            var other = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Keep", data);
            helper.assertTrue(controller.createLuaFunction(definition) && controller.createLuaFunction(other), "Could not create definitions");
            graph.luaFunctions().add(definition); graph.luaFunctions().add(other);
            var placed = node("placed", "createthrusters:lua"); definition.apply(placed.data()); graph.nodes().add(placed);
            var kept = node("keep", "createthrusters:lua"); other.apply(kept.data()); graph.nodes().add(kept);
            var group = new CompoundTag();
            com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphSelection.setCommentGroupNodeIds(group, java.util.Set.of("placed", "keep")); graph.groups().add(group);
            graph.edges().add(new AdvancedGraphDocument.Edge("wire", "placed", "result", "keep", "value"));
            var nested = new AdvancedGraphDocument.FunctionGraph(UUID.randomUUID().toString(), "Nested"); graph.functions().add(nested);
            var inside = node("inside", "createthrusters:lua"); definition.apply(inside.data()); nested.nodes().add(inside);
            helper.assertTrue(controller.saveDraft(graph, 0) && controller.applyDraft(), "Could not save graph fixtures");
            var stale = controller.getDraftGraph();
            helper.assertTrue(controller.renameLuaFunction(definition.id(), "Renamed"), "Rename failed");
            helper.assertTrue(controller.getDraftGraph().luaFunctions().stream().anyMatch(val -> val.id().equals(definition.id()) && val.name().equals("Renamed")), "Editor definition name was stale");
            com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.change(stale.nodes().getFirst().data(), "stale edit", false, false, false);
            helper.assertTrue(controller.saveDraft(stale, stale.revision()), "Could not save a stale unrelated edit");
            helper.assertTrue(controller.luaFunction(definition.id()).name().equals("Renamed"), "A graph save rolled back the name");
            helper.assertTrue(controller.getDraftGraph().nodes().getFirst().data().getCompound("Defaults").getCompound("name").getCompound("Payload").getString("Value").equals("Renamed"), "Placed names did not synchronize");
            stale = controller.getDraftGraph();
            helper.assertTrue(controller.deleteLuaFunction(definition.id()), "Deletion failed");
            helper.assertTrue(controller.luaFunction(definition.id()) == null && !controller.createLuaFunction(definition), "The deleted UUID was recreated");
            helper.assertTrue(!controller.saveLuaSource(definition.id(), "output.result = 5", "output.result = 6"), "Deleted source accepted an edit");
            helper.assertTrue(controller.saveDraft(stale, controller.getDraftGraph().revision()), "Could not save an old snapshot after deletion");
            for(var remaining : List.of(controller.getDraftGraph(), controller.getActiveGraph())){
                helper.assertTrue(remaining.nodes().stream().noneMatch(val -> val.id().equals("placed")) && remaining.edges().isEmpty()
                        && remaining.functions().getFirst().nodes().isEmpty(), "Deletion retained a placed instance or wire");
                helper.assertTrue(com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphSelection.commentGroupNodeIds(remaining.groups().getFirst()).equals(java.util.Set.of("keep")), "Deletion retained a comment-group member");
            }
            var method = ControllerSqliteStore.class.getDeclaredMethod("connection", net.minecraft.world.level.Level.class); method.setAccessible(true);
            var connection = (java.sql.Connection)method.invoke(null, helper.getLevel());
            try(var statement = connection.prepareStatement("SELECT count(*) FROM Lua_Nodes WHERE function_id=?")){
                statement.setString(1, definition.id()); try(var rows = statement.executeQuery()){ rows.next(); helper.assertTrue(rows.getInt(1) == 0, "SQL retained deleted code or metadata"); }
            }
            helper.assertTrue(controller.luaFunction(other.id()) != null, "Deletion removed another function");
            var reloaded = ControllerSqliteStore.loadController(controller.controllerManifestId(), helper.getLevel());
            helper.assertTrue(reloaded != null && !reloaded.draftGraph().toString().contains(definition.id())
                    && !reloaded.activeGraph().toString().contains(definition.id()) && !reloaded.controllerData().toString().contains(definition.id()), "Reload retained a deleted graph/history reference");
            helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void linkedCallsPreserveOnlyTheirNativeYields(GameTestHelper helper){
        var controller = controller(helper); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(pos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "thruster", ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT); controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller, id);
        String source = "local thruster\nlocal co = coroutine.create(function() thruster = assert(peripheral.wrap('thruster')) "
                + "for i=1,20 do assert(peripheral.isPresent('thruster')) thruster.setThrottle(0.625) end return true end)\n"
                + "local ok,value = coroutine.resume(co)\nassert(ok and value == true and coroutine.status(co) == 'dead', 'Fast native calls gained a bridge yield')\n"
                + "co = coroutine.create(function() return thruster.getThrottle() end)\nlocal ok,filter = coroutine.resume(co)\nassert(ok and filter == 'task_complete', 'The native world call did not yield normally')\n"
                + "local event = table.pack(os.pullEventRaw('task_complete'))\nlocal ok,value = coroutine.resume(co, table.unpack(event,1,event.n))\n"
                + "assert(ok and coroutine.status(co) == 'dead', 'The bridge added another world-thread wait')\noutput.result = value";
        computer.register(new ScriptRequest(id, 1, source, Map.of(), Map.of("result", "any")));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick(); var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid() && result.outputs().get("result").asNumber() == .625, "Native dispatch added overhead: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void inputSurvivesSleepZeroAndNativeWorldCalls(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.register(new ScriptRequest(id, 1, "local probe = peripheral.wrap('right')\nprobe.click()\nsleep(0)\n"
                + "local e,b,x,y = os.pullEvent('mouse_click')\nassert(x == 6 and y == 1, 'Sleep discarded or changed the click')\n"
                + "probe.nativeClick()\nlocal e,b,x,y = os.pullEvent('mouse_click')\noutput.result = x == 4 and y == 3", Map.of(), Map.of("result", "any")));
        attachInputProbe(computer);
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick(); var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid() && result.outputs().get("result").asBoolean(), "A native wait lost player input: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void terminalDoesNotPublishAnIncompleteWorldCallFrame(GameTestHelper helper){
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller(helper), id);
        computer.register(new ScriptRequest(id, 1, "local probe = peripheral.wrap('right')\nterm.clear() term.setCursorPos(1,1) print('Complete frame')\n"
                + "os.queueEvent('draw_next') os.pullEvent('draw_next')\nterm.clear() term.setCursorPos(1,1)\n"
                + "output.result = probe.completeFrameVisible()\nprint('Updated frame')", Map.of(), Map.of("result", "any")));
        attachInputProbe(computer);
        boolean[] done = {false}; com.rieno.gadgetsandgizmos.lib.script.ScriptResult[] finished = {null};
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick(); var result = execution(computer); if(result != null) finished[0] = result;
            if(finished[0] == null) return;
            helper.assertTrue(finished[0].valid() && finished[0].outputs().get("result").asBoolean(), "A cleared intermediate frame was published: " + finished[0]);
            var terminal = computer.terminal(); if(terminal == null || terminal.text().stream().noneMatch(line -> line.contains("Updated frame"))) return;
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void playerThrusterLoopProcessesEveryClickDuringPolling(GameTestHelper helper){
        var controller = controller(helper); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(pos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:thruster", "thruster", ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT); controller.getLinkerSlotHandler().setStackInSlot(0, linker);
        var id = UUID.randomUUID(); var computer = new ComputerCraftLuaComputer(controller, id);
        String source = "local thruster = assert(peripheral.wrap('thruster'))\nlocal throttle,tmp,count = -1,false,0\nlocal w = term.getSize()\noutput.ready = true\n"
                + "while true do\nif thruster.getThrottle() ~= throttle then throttle = thruster.getThrottle() end\n"
                + "term.setBackgroundColor(colors.black) term.clear() term.setCursorPos(1,1) term.setTextColor(8) print('Thruster: ')\n"
                + "term.setCursorPos(1,2) print(throttle) print(tmp)\nlocal e = {os.pullEvent()}\n"
                + "if e[1] == 'mouse_click' then\nif e[3] >= 0 and e[3] <= 8 and e[4] == 1 then "
                + "if thruster.getThrottle() < 1 then thruster.setThrottle(1) else thruster.setThrottle(0) end end\n"
                + "if e[3] >= 0 and e[3] <= w and e[4] == 3 then tmp = not tmp end\ncount = count + 1 end\n"
                + "if count == 12 then output.result = count break end\nsleep(0)\nend";
        computer.register(new ScriptRequest(id, 1, source, Map.of(), Map.of("ready", "any", "result", "any")));
        int[] sent = {0}; boolean[] done = {false};
        helper.onEachTick(() -> {
            if(done[0]) return; computer.tick();
            if(sent[0] < 12 && computer.output("ready") != null && computer.output("ready").asBoolean()){
                helper.assertTrue(computer.input(null, new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", 5.5 / 51, .5 / 19, 0)), "Computer rejected a click"); sent[0]++;
            }
            var result = execution(computer); if(result == null) return;
            helper.assertTrue(result.valid() && result.outputs().get("result").asNumber() == 12, "The player's polling loop lost clicks: " + result);
            done[0] = true; computer.close(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cachedHubReadsAndCallsDoNotRescanOrQueueTasks(GameTestHelper helper){
        try{
            int[] scans = {0}, tasks = {0};
            var peripheral = new dan200.computercraft.api.peripheral.IPeripheral(){
                @Override public String getType(){ return "fast_test"; }
                @Override public boolean equals(dan200.computercraft.api.peripheral.IPeripheral other){ return other == this; }
            };
            var target = new com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftPeripheralHub.Target("test", peripheral,
                    Map.of("read", (p, c, ctx, args) -> dan200.computercraft.api.lua.MethodResult.of(42)), java.util.Set.of("test"), java.util.Set.of());
            var hub = new com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftPeripheralHub("test", () -> { scans[0]++; return List.of(target); });
            hub.refresh();
            var access = (dan200.computercraft.api.peripheral.IComputerAccess)java.lang.reflect.Proxy.newProxyInstance(
                    LuaScriptGameTests.class.getClassLoader(), new Class<?>[]{dan200.computercraft.api.peripheral.IComputerAccess.class}, (proxy, method, args) -> switch(method.getName()){
                        case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0]; case "toString", "getAttachmentName" -> "test"; case "getID" -> 1; default -> null;
                    });
            hub.attach(access);
            dan200.computercraft.api.lua.ILuaContext ctx = task -> { tasks[0]++; return 1; };
            for(int idx = 0; idx < 1000; idx++){
                hub.getNamesRemote(); hub.getTypeRemote("test"); hub.getMethodsRemote("test"); hub.isPresentRemote("test");
                var result = hub.callRemote(access, ctx, new dan200.computercraft.api.lua.ObjectArguments("test", "read"));
                helper.assertTrue(result.getCallback() == null && result.getResult()[0].equals(42), "A fast call yielded");
            }
            helper.assertTrue(scans[0] == 1 && tasks[0] == 0, "Calls rescanned targets or scheduled non-native tasks: " + scans[0] + "/" + tasks[0]);
            hub.detach(access); helper.succeed();
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    private static void attachInputProbe(ComputerCraftLuaComputer computer){
        try{
            var field = ComputerCraftLuaComputer.class.getDeclaredField("computer"); field.setAccessible(true);
            ((dan200.computercraft.core.computer.Computer)field.get(computer)).getEnvironment().setPeripheral(dan200.computercraft.core.computer.ComputerSide.RIGHT, new InputProbe(computer));
        }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    public static final class InputProbe implements dan200.computercraft.api.peripheral.IPeripheral{
        private final ComputerCraftLuaComputer computer;
        private InputProbe(ComputerCraftLuaComputer computer){ this.computer = computer; }
        @Override public String getType(){ return "input_probe"; }
        @Override public boolean equals(dan200.computercraft.api.peripheral.IPeripheral other){ return other == this; }
        @dan200.computercraft.api.lua.LuaFunction public void click(){ computer.input(null, new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", 5.5 / 51, .5 / 19, 0)); }
        @dan200.computercraft.api.lua.LuaFunction(mainThread = true) public void nativeClick(){ computer.input(null, new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", 3.5 / 51, 2.5 / 19, 0)); }
        @dan200.computercraft.api.lua.LuaFunction(mainThread = true) public boolean completeFrameVisible(){
            var terminal = computer.terminal(); return terminal != null && terminal.text().stream().anyMatch(line -> line.contains("Complete frame"));
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void targetPreviewsResolveCamerasAdaptersAndComputersWithoutChangingGraphs(GameTestHelper helper){
        var ctrl = controller(helper); var level = helper.getLevel();
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PreviewTest"), net.minecraft.server.level.ClientInformation.createDefault());
        var sources = ControllerTargetPreview.sources(ctrl, player);
        var cameraPos = helper.absolutePos(new BlockPos(2, 1, 1)); var adapterPos = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlock(cameraPos, CTBlocks.CAMERA.get().defaultBlockState(), 3); level.setBlock(adapterPos, CTBlocks.UNIVERSAL_DISPLAY_ADAPTER.get().defaultBlockState(), 3);
        var camera = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("camera", com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.UNKNOWN, "", "createthrusters:camera", "Camera", null, cameraPos).toTag();
        var adapter = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("adapter", com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.UNKNOWN, "", "createthrusters:universal_display_adapter", "Adapter", null, adapterPos).toTag();
        var cameraFrame = sources.frame(player, camera, 340, 190);
        helper.assertTrue(cameraFrame.getString("Format").equals("camera") && cameraFrame.getCompound("ViewSource").getLong("BlockPos") == cameraPos.asLong(), "Camera target did not expose its live source");
        var terminal = new com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal(4, 2, List.of("Test", "Feed"), List.of("0000", "eeee"), List.of("ffff", "ffff"),
                java.util.Collections.nCopies(16, 0xFFAA3311), 2, 1, true, 14);
        ((UniversalDisplayAdapterBlockEntity)level.getBlockEntity(adapterPos)).acceptInputFrame("preview_test", terminal.displayFrame());
        var frame = sources.frame(player, adapter, 340, 190); var decoded = com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.fromDisplayFrame(frame);
        helper.assertTrue(decoded != null && decoded.text().equals(terminal.text()) && decoded.palette().equals(terminal.palette()) && decoded.blink(), "Adapter preview lost terminal content or colours");
        var denied = new com.rieno.gadgetsandgizmos.lib.display.DisplayPreviewSources(target -> level.getBlockEntity(cameraPos), src -> false, src -> null);
        helper.assertTrue(denied.frame(player, camera, 340, 190).isEmpty(), "Target preview ignored the access policy");
        helper.assertTrue(ctrl.getDraftGraph().revision() == 0 && ctrl.getDraftGraph().nodes().isEmpty(), "Previewing a target changed the graph");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rdpRecognizesOfflineComputersAndRequiresWirelessModems(GameTestHelper helper){
        var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("computercraft:computer_advanced"));
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var computer = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop.supports(computer), "An offline computer was not recognized");
        helper.assertTrue(!com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop.isAvailable(computer), "Remote access accepted a computer without a wireless modem");
        try{
            var method = DiagnosticTabletItem.class.getDeclaredMethod("bindingType", net.minecraft.world.level.block.entity.BlockEntity.class, net.minecraft.world.level.block.state.BlockState.class); method.setAccessible(true);
            helper.assertTrue(method.invoke(null, computer, computer.getBlockState()).equals("computer"), "Tablet Reader did not recognize the CC computer");
        }catch(Exception ex){ throw new RuntimeException(ex); }
        var modemPos = pos.above();
        for(String id : List.of("wireless_modem_normal", "wireless_modem_advanced")){
            var modem = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("computercraft:" + id));
            helper.getLevel().setBlock(modemPos, modem.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.DOWN), 3);
            helper.assertTrue(com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop.isAvailable(computer), "A closed " + id + " did not enable remote access");
            helper.getLevel().removeBlock(modemPos, false);
        }
        var wired = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("computercraft:wired_modem_full"));
        helper.getLevel().setBlock(modemPos, wired.defaultBlockState(), 3);
        helper.assertTrue(!com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop.isAvailable(computer), "A wired modem enabled wireless remote access");
        helper.getLevel().removeBlock(modemPos, false);
        helper.assertTrue(!com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop.isAvailable(computer), "Removing the modem left remote access available");
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ModemTest"), net.minecraft.server.level.ClientInformation.createDefault());
        DiagnosticTabletRemoteSessions.authorize(player, computer);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        helper.assertTrue(((dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity)computer).isUsable(player), "An old remote session blocked normal local use without a modem");
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos.offset(20, 0, 0)));
        helper.assertTrue(!((dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity)computer).isUsable(player), "An old remote session retained distance access without a modem");
        var wireless = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("computercraft:wireless_modem_normal"));
        helper.getLevel().setBlock(modemPos, wireless.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.DOWN), 3);
        helper.assertTrue(((dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity)computer).isUsable(player), "The installed CC version did not grant the authorized wireless remote menu");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rdpReaderBindsComputerRightClicksFromEitherHandWhileSneaking(GameTestHelper helper){
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReaderTest"), net.minecraft.server.level.ClientInformation.createDefault());
        var app = DiagnosticTabletData.appId("rdp");
        for(String block : List.of("computer_normal", "computer_advanced", "turtle_normal", "turtle_advanced")){
            level.setBlock(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("computercraft:" + block)).defaultBlockState(), 3);
            for(var hand : net.minecraft.world.InteractionHand.values()) for(boolean sneaking : List.of(false, true)){
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.setShiftKeyDown(sneaking);
                var tablet = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.DIAGNOSTIC_TABLET.get()); var id = UUID.randomUUID();
                DiagnosticTabletData.write(tablet, DiagnosticTabletData.State.DEFAULT.withTabletId(id).withApp(app, "devices").withMode(com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode.READER, ""));
                DiagnosticTabletAppStorage.addBinding(player.server, id, app, new DiagnosticTabletData.Binding("controller", null, helper.absolutePos(new BlockPos(1, 1, 1)), "Existing ACC"), true);
                player.setItemInHand(hand, tablet);
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
                var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
                com.rieno.gadgetsandgizmos.neoforge.CTPlayerEvents.onRightClickBlock(event);
                var bindings = DiagnosticTabletAppStorage.bindings(player.server, id, app);
                helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(), "Reader let the block consume the click: " + hand + ", sneaking=" + sneaking);
                helper.assertTrue(bindings.size() == 2 && bindings.getFirst().type().equals("controller") && bindings.getLast().type().equals("computer"), "Reader failed to bind " + block + ": " + hand + ", sneaking=" + sneaking);
                helper.assertTrue(DiagnosticTabletData.read(tablet).mode() == com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode.STANDARD, "Reader stayed active after pairing");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedEmptyGraphsDoNotBecomeNewControllers(GameTestHelper helper){
        var ctrl = controller(helper);
        helper.assertTrue(!ctrl.hasSavedGraph(), "A new controller was marked saved");
        helper.assertTrue(ctrl.saveDraft(new AdvancedGraphDocument(), 0), "Saving an empty graph failed");
        helper.assertTrue(ctrl.hasSavedGraph() && ctrl.getDraftGraph().nodes().isEmpty(), "A saved empty graph was treated as a new controller");
        helper.succeed();
    }

    private static com.rieno.gadgetsandgizmos.lib.script.ScriptResult execution(ComputerCraftLuaComputer computer){
        com.rieno.gadgetsandgizmos.lib.script.ScriptResult result;
        while((result = computer.poll()) != null) if(result.executed() || !result.valid()) return result;
        return null;
    }

    private static long sqlChanges(java.sql.Connection connection) throws java.sql.SQLException{
        try(var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT total_changes()")){
            rows.next(); return rows.getLong(1);
        }
    }

    private static void assertNoError(GameTestHelper helper, GraphRuntime runtime, String id){
        var error = runtime.liveOutput(id, "error");
        helper.assertTrue(error == null || error.asString().isBlank(), "Lua error: " + (error == null ? "" : error.asString()));
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, AdvancedGraphCatalog.displayName(type), 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(helper.getLevel());
        return controller;
    }
}
