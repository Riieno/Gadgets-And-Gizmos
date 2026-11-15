package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspection;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Verify the actual ACC Lua tab and capture its node, completion, insertion and error views
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class LuaEditorClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.luaEditorValidation");
    private static boolean complete, started;
    private static java.util.concurrent.CompletableFuture<net.minecraft.nbt.CompoundTag> networkSetup;
    private static java.util.concurrent.CompletableFuture<Boolean> networkStored;
    private static AdvancedContraptionControllerBlockEntity networkController;
    private static AdvancedContraptionControllerScreen networkScreen;
    private static String networkFunction;
    private static BlockPos networkPos;
    private static int networkTicks;
    private static final String NETWORK_SOURCE = "output.result = 'Saved through the source-only network'\n";
    private static java.util.concurrent.CompletableFuture<net.minecraft.nbt.CompoundTag> inputSetup;
    private static java.util.concurrent.CompletableFuture<Boolean> inputResult;
    private static com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftLuaComputer inputComputer;
    private static boolean inputSent;
    private static int libraryActionStage;
    private static int targetPreviewStage, remoteStage, remoteTicks;
    private static java.util.concurrent.CompletableFuture<Void> remoteWork;
    private static final UUID REMOTE_TABLET = UUID.randomUUID();
    private static BlockPos remoteComputerPos;
    private static String remoteDeviceKey;
    private static int remoteVariant;
    private static final List<String> REMOTE_BLOCKS = List.of("computer_normal", "computer_advanced", "turtle_normal", "turtle_advanced");
    private static java.util.concurrent.CompletableFuture<Boolean> libraryActionStored;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        if(started){
            try{ checkNetworkSave(mc); }catch(Throwable ex){ finish(mc, ex); }
            return;
        }
        started = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try{
            Path out = Path.of(OUTPUT); Files.createDirectories(out);
            validate(mc, out); validateExecutionAndInspector(mc, out); validateInspectorLayers(mc, out); validateHoverInspectors(mc, out); validateGraphPresentation(mc, out); validateExternalTerminalProjection(mc, out); validateSourceRecovery(mc);
            networkPos = mc.player.blockPosition().offset(2, 0, 2).immutable();
            networkSetup = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{
                    var level = mc.getSingleplayerServer().overworld();
                    level.removeBlock(networkPos, false);
                    level.setBlock(networkPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
                    networkController = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(networkPos);
                    var thrusterPos = networkPos.offset(1, 0, 0);
                    level.setBlock(thrusterPos, CTBlocks.THRUSTER.get().defaultBlockState(), 3);
                    var target = new com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkedTarget(thrusterPos, null, "createthrusters:thruster", "thruster",
                            com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                            com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
                    var linker = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.CONTRAPTION_NETWORK_LINKER.get());
                    com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData.LinkMode.OUTPUT);
                    networkController.getLinkerSlotHandler().setStackInSlot(0, linker);
                    var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
                    var function = new com.rieno.gadgetsandgizmos.lib.script.ScriptFunction(UUID.randomUUID().toString(), "Network save", data);
                    check(networkController.createLuaFunction(function), "Could not create the network test function");
                    networkFunction = function.id();
                    networkSetup.complete(networkController.getDraftGraph().toTag());
                }catch(Throwable ex){ networkSetup.completeExceptionally(ex); }
            });
        }catch(Throwable ex){ finish(mc, ex); }
    }

    private static void checkNetworkSave(Minecraft mc) throws Exception{
        if(++networkTicks > 1800) throw new AssertionError("Source-only network save timed out");
        if(inputSetup != null){ checkTerminalNetwork(mc); return; }
        if(networkSetup == null || !networkSetup.isDone()) return;
        if(networkScreen == null){
            var ctrl = new AdvancedContraptionControllerBlockEntity(networkPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); ctrl.setLevel(mc.level);
            var menu = new AdvancedContraptionControllerMenu(-1244, mc.player.getInventory(), ctrl);
            networkScreen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Source network validation"));
            mc.setScreen(networkScreen);
            networkScreen.updateProjectionGraph(AdvancedGraphDocument.fromTag(networkSetup.join()));
            var graph = (AdvancedGraphDocument)field(networkScreen, "draft");
            var prototype = com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.prototype(
                    com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.find(graph, networkFunction));
            invoke(networkScreen, "openLuaEditor", prototype);
            invoke(networkScreen, "editLuaSource", prototype, NETWORK_SOURCE);
            invoke(networkScreen, "saveLuaChanges");
            invoke(networkScreen, "promptLuaFunction", false, 0D, 0D);
            check(mc.screen == networkScreen, "The naming modal closed the live ACC screen");
            return;
        }
        var edits = (com.rieno.gadgetsandgizmos.lib.script.ScriptSourceEdits)field(networkScreen, "luaSourceEdits");
        if(edits.status(networkFunction).equals("Code save failed")) throw new AssertionError(edits.error(networkFunction));
        if(!edits.status(networkFunction).equals("Code saved")) return;
        @SuppressWarnings("unchecked") var peripherals = (List<com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral>)field(networkScreen, "luaPeripherals");
        if(peripherals.stream().noneMatch(val -> val.aliases().contains("thruster") && val.methods().contains("setThrottle"))) return;
        if(networkStored == null){
            networkStored = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{ networkStored.complete(networkController.getDraftGraph().revision() == 0 && networkController.luaFunction(networkFunction).data().getString(LuaGraphNode.SOURCE).equals(NETWORK_SOURCE)); }
                catch(Throwable ex){ networkStored.completeExceptionally(ex); }
            });
            return;
        }
        if(!networkStored.isDone()) return;
        check(networkStored.join(), "Network source save changed the graph revision or did not persist the SQL source");
        check(!(boolean)field(networkScreen, "draftDirty") && ((Map<?, ?>)field(networkScreen, "pendingGraphSaves")).isEmpty(), "A network source save scheduled a graph save");
        check(field(networkScreen, "luaNameModal") != null, "A source acknowledgement closed the naming modal");
        networkScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(mc.screen == networkScreen && field(networkScreen, "luaNameModal") == null, "Cancelling naming closed ACC or left its modal open");
        var graph = (AdvancedGraphDocument)field(networkScreen, "draft");
        check(com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.find(graph, networkFunction).data().getString(LuaGraphNode.SOURCE).equals(NETWORK_SOURCE), "Returning from the naming screen discarded saved code");
        ((AdvancedControllerLuaEditor)field(networkScreen, "luaEditor")).refresh();
        validateWrapCompletion(networkScreen, Path.of(OUTPUT));
        capture(Path.of(OUTPUT).resolve("source-network-saved.png"), networkScreen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        inputSetup = new java.util.concurrent.CompletableFuture<>(); inputResult = new java.util.concurrent.CompletableFuture<>();
        mc.getSingleplayerServer().execute(() -> {
            try{
                var id = UUID.randomUUID();
                inputComputer = new com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftLuaComputer(networkController, id);
                inputComputer.register(new com.rieno.gadgetsandgizmos.lib.script.ScriptRequest(id, 1,
                        "output.ready = true\nlocal _, text = os.pullEvent('char')\noutput.result = text", Map.of(), Map.of("ready", "any", "result", "any")));
            }catch(Throwable ex){ inputSetup.completeExceptionally(ex); }
        });
    }

    private static void checkTerminalNetwork(Minecraft mc) throws Exception{
        mc.getSingleplayerServer().execute(() -> {
            if(inputComputer == null) return;
            try{
                inputComputer.tick();
                var ready = inputComputer.output("ready");
                if(ready != null && ready.asBoolean() && !inputSetup.isDone()) inputSetup.complete(
                        com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime.fromLibraryValue(inputComputer.reference()).payload());
                var result = inputComputer.output("result");
                if(inputSent && result != null && result.asString().equals("a")){ inputComputer.close(); inputComputer = null; inputResult.complete(true); }
            }catch(Throwable ex){ inputResult.completeExceptionally(ex); }
        });
        if(!inputSetup.isDone()) return;
        if(!inputSent){
            inputSent = true;
            var data = new net.minecraft.nbt.CompoundTag(); data.put("Source", inputSetup.join()); data.putString("Action", "char"); data.putInt("Value", 'a');
            invoke(networkScreen, "sendLuaAction", "lua_terminal_input", data, "", 0L);
            return;
        }
        if(inputResult.isDone()){
            check(inputResult.join(), "The terminal input packet did not reach the headless computer");
            checkLibraryActionsNetwork(mc);
        }
    }

    private static void checkLibraryActionsNetwork(Minecraft mc) throws Exception{
        if(libraryActionStage == 0){
            libraryActionStage = 1;
            invoke(networkScreen, "promptLuaRename", networkFunction);
            var modal = (com.rieno.gadgetsandgizmos.lib.client.ui.TextInputModal)field(networkScreen, "luaNameModal");
            check(modal != null, "Network rename did not open an ACC modal");
            modal.value("Renamed through SQL RPC"); networkScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); return;
        }
        if(!((Map<?, ?>)field(networkScreen, "pendingLuaActions")).isEmpty()) return;
        if(libraryActionStored == null){
            libraryActionStored = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{
                    var function = networkController.luaFunction(networkFunction);
                    libraryActionStored.complete(libraryActionStage == 1 ? function != null && function.name().equals("Renamed through SQL RPC") : function == null);
                }catch(Throwable ex){ libraryActionStored.completeExceptionally(ex); }
            }); return;
        }
        if(!libraryActionStored.isDone()) return;
        check(libraryActionStored.join(), "A library action was acknowledged without persisting SQL");
        var graph = (AdvancedGraphDocument)field(networkScreen, "draft");
        if(libraryActionStage == 1){
            check(com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.find(graph, networkFunction).name().equals("Renamed through SQL RPC"), "The rename reply did not update the editor");
            libraryActionStage = 2; libraryActionStored = null; invoke(networkScreen, "deleteLuaFunction", networkFunction); return;
        }
        check(com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.find(graph, networkFunction) == null, "The delete reply retained the editor entry");
        checkTargetInspectorsNetwork(mc);
    }

    private static void checkTargetInspectorsNetwork(Minecraft mc) throws Exception{
        if(targetPreviewStage == 0){
            targetPreviewStage = 1;
            remoteWork = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{
                    var level = networkController.getLevel(); var pos = networkPos.offset(0, 0, 2);
                    level.setBlock(pos, CTBlocks.UNIVERSAL_DISPLAY_ADAPTER.get().defaultBlockState(), 3);
                    var palette = new java.util.ArrayList<>(java.util.Collections.nCopies(16, 0xFF88CCFF)); palette.set(0, 0xFFF0F0F0); palette.set(14, 0xFFCC4C4C); palette.set(15, 0xFF111111);
                    var terminal = new com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal(12, 3, List.of("Adapter feed", "Target input", "GUI preview "), List.of("0".repeat(12), "e".repeat(12), "0".repeat(12)), List.of("f".repeat(12), "f".repeat(12), "f".repeat(12)), palette, 0, 0, false, 0);
                    ((com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity)level.getBlockEntity(pos)).acceptInputFrame("test", terminal.displayFrame());
                    level.setBlock(networkPos.offset(1, 0, 2), CTBlocks.CAMERA.get().defaultBlockState(), 3);
                    remoteWork.complete(null);
                }catch(Throwable ex){ remoteWork.completeExceptionally(ex); }
            }); return;
        }
        if(!remoteWork.isDone()) return; remoteWork.join();
        if(targetPreviewStage == 1){
            targetPreviewStage = 2;
            var graph = new AdvancedGraphDocument(); var target = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("adapter", com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.UNKNOWN, "", "createthrusters:universal_display_adapter", "Adapter", null, networkPos.offset(0,0,2));
            var node = node("Target preview", 0, 0); graph.nodes().add(node);
            var savedTarget = new AdvancedGraphDocument.Node("target-feed", "createthrusters:target", "Adapter source", 450, 0, new net.minecraft.nbt.CompoundTag());
            com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes.select(savedTarget.data(), target); graph.nodes().add(savedTarget);
            graph.edges().add(new AdvancedGraphDocument.Edge("feed-wire", savedTarget.id(), "target", node.id(), "value"));
            networkScreen.updateProjectionGraph(graph); invoke(networkScreen, "selectGraphTab", (Object)null);
            var draft = (AdvancedGraphDocument)field(networkScreen, "draft"); node = draft.nodes().getFirst();
            var hitType = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.PortHit");
            var ctor = hitType.getDeclaredConstructor(AdvancedGraphDocument.Node.class, String.class, boolean.class); ctor.setAccessible(true);
            invoke(networkScreen, "setPortInspectorPinned", ctor.newInstance(node, "value", false), true);
            capture(Path.of(OUTPUT).resolve("target-inspector-pending.png"), networkScreen, networkScreen.width, networkScreen.height);
            invoke(networkScreen, "requestTargetPreviews"); return;
        }
        var frames = (Map<String, net.minecraft.nbt.CompoundTag>)field(networkScreen, "inspectorFrames");
        if(targetPreviewStage == 2){
            if(frames.values().stream().noneMatch(frame -> frame.getString("Format").equals("terminal"))) return;
            capture(Path.of(OUTPUT).resolve("target-inspector-controls.png"), networkScreen, networkScreen.width, networkScreen.height);
            var bubble = (com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble)((List<?>)field(networkScreen, "renderedInspectors")).getFirst();
            var mode = field(bubble, "modeButton"); var bounds = bubble.bounds();
            bubble.mouseClicked(bounds.x() + (int)invoke(mode, "x") + 3, bounds.y() + (int)invoke(mode, "y") + 3, 0); bubble.mouseReleased();
            check(bubble.state().mode() == com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.GUI, "A target inspector's mode button did not offer GUI");
            set(networkScreen, "zoom", .7D); set(networkScreen, "panX", 0D); set(networkScreen, "panY", 0D);
            set(bubble, "state", new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(8, 60, com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.GUI, 400, 240));
            capture(Path.of(OUTPUT).resolve("target-adapter-gui.png"), networkScreen, networkScreen.width, networkScreen.height);
            check(field(bubble, "terminal") != null, "A target input did not render its adapter terminal");
            var graph = (AdvancedGraphDocument)field(networkScreen, "draft"); var camera = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode("camera", com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.UNKNOWN, "", "createthrusters:camera", "Camera", null, networkPos.offset(1,0,2));
            com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes.select(graph.nodes().getLast().data(), camera);
            set(networkScreen, "draftRuntimeRevision", Long.MIN_VALUE);
            capture(Path.of(OUTPUT).resolve("target-camera-pending.png"), networkScreen, networkScreen.width, networkScreen.height);
            invoke(networkScreen, "requestTargetPreviews"); targetPreviewStage = 3; return;
        }
        if(targetPreviewStage == 3){
            if(frames.values().stream().noneMatch(frame -> frame.getString("Format").equals("camera"))) return;
            var cameraFrame = frames.values().stream().filter(frame -> frame.getString("Format").equals("camera")).findFirst().orElseThrow();
            var ref = com.rieno.gadgetsandgizmos.lib.view.ViewReference.fromTag(cameraFrame.getCompound("ViewSource"));
            if(ref != null && com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer.request(ref, 380, 198) == null) return;
            capture(Path.of(OUTPUT).resolve("target-camera-gui.png"), networkScreen, networkScreen.width, networkScreen.height);
            var bubble = (com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble)((List<?>)field(networkScreen, "renderedInspectors")).getFirst();
            check(field(bubble, "guiPreview") != null && field(bubble, "terminal") == null, "Switching target inputs did not replace the GUI feed");
            targetPreviewStage = 4;
        }
        checkRemoteComputer(mc);
    }

    private static void checkRemoteComputer(Minecraft mc) throws Exception{
        if(remoteStage == 0){
            remoteStage = 1; remoteTicks = 0; remoteWork = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(); var level = player.serverLevel();
                    remoteComputerPos = player.blockPosition().offset(3,0,3);
                    level.setBlock(remoteComputerPos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:" + REMOTE_BLOCKS.get(remoteVariant))).defaultBlockState(), 3);
                    level.setBlock(remoteComputerPos.above(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:wireless_modem_advanced")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.DOWN), 3);
                    var tablet = new net.minecraft.world.item.ItemStack(com.rieno.gadgetsandgizmos.registry.CTItems.DIAGNOSTIC_TABLET.get());
                    var rdp = com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.appId("rdp");
                    com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.write(tablet, com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.State.DEFAULT.withTabletId(REMOTE_TABLET).withApp(rdp, "devices"));
                    for(var binding : com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.bindings(player.server, REMOTE_TABLET, rdp))
                        com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.removeBinding(player.server, REMOTE_TABLET, rdp, com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.key(binding));
                    com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.addBinding(player.server, REMOTE_TABLET, rdp,
                            new com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.Binding("controller", null, networkPos, "Existing ACC"), true);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tablet);
                    com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase.forServer(player.server).setInstalled(REMOTE_TABLET, rdp, true);
                    player.teleportTo(remoteComputerPos.getX() + .5, remoteComputerPos.getY(), remoteComputerPos.getZ() - 2);
                    player.containerMenu.broadcastChanges(); remoteWork.complete(null);
                }catch(Throwable ex){ remoteWork.completeExceptionally(ex); }
            }); return;
        }
        if(!remoteWork.isDone()) return; remoteWork.join();
        if(++remoteTicks > 200) throw new AssertionError("Native remote computer handoff timed out at stage " + remoteStage);
        if(remoteStage == 1){
            if(!REMOTE_TABLET.equals(com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.read(mc.player.getMainHandItem()).tabletId())) return;
            if(mc.level.getBlockEntity(remoteComputerPos) == null || mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(remoteComputerPos)) > 16) return;
            DiagnosticTabletScreen.openItem(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem());
            remoteStage = 8; remoteTicks = 0; return;
        }
        if(remoteStage == 8){
            check(mc.screen instanceof DiagnosticTabletScreen<?>, "Tablet closed before entering Reader mode");
            var appData = (net.minecraft.nbt.CompoundTag)invoke(mc.screen, "appData", com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.appId("rdp"));
            if(appData.getList("Devices", net.minecraft.nbt.Tag.TAG_COMPOUND).size() != 1 || remoteTicks < 5) return;
            capture(Path.of(OUTPUT).resolve("rdp-before-reader.png"), mc.screen, mc.screen.width, mc.screen.height);
            check(clickTabletCard(mc.screen, tooltip -> tooltip.toLowerCase(java.util.Locale.ROOT).contains("reader")), "RDP Add new card ignored the click");
            check(mc.screen == null, "The RDP Reader button did not close the tablet");
            remoteStage = 9; remoteTicks = 0; return;
        }
        if(remoteStage == 9){
            if(remoteTicks < 5) return;
            check(com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem.isRdpReader(mc.player.getMainHandItem()), "RDP Reader button did not persist Reader mode");
            mc.player.setShiftKeyDown(false);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(remoteComputerPos), net.minecraft.core.Direction.NORTH, remoteComputerPos, false);
            check(mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction(), "Reader did not consume the computer right-click");
            check(mc.screen == null, "Reader opened the computer instead of pairing it");
            remoteStage = 6; remoteTicks = 0; return;
        }
        if(remoteStage == 6){
            if(com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.read(mc.player.getMainHandItem()).mode() != com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode.STANDARD) return;
            mc.player.setShiftKeyDown(false);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            remoteWork = new java.util.concurrent.CompletableFuture<>();
            mc.getSingleplayerServer().execute(() -> {
                try{
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var bindings = com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.bindings(player.server, REMOTE_TABLET, com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.appId("rdp"));
                    check(bindings.size() == 2 && bindings.getLast().type().equals("computer"), "Reader right-click did not store the " + REMOTE_BLOCKS.get(remoteVariant) + " alongside the existing ACC");
                    remoteDeviceKey = com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.key(bindings.getLast());
                    player.teleportTo(player.getX() + 20, player.getY(), player.getZ()); remoteWork.complete(null);
                }catch(Throwable ex){ remoteWork.completeExceptionally(ex); }
            });
            remoteStage = 7; remoteTicks = 0; return;
        }
        if(remoteStage == 7){
            DiagnosticTabletScreen.openItem(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem()); remoteStage = 2; remoteTicks = 0; return;
        }
        if(remoteStage == 2){
            if(!(mc.screen instanceof DiagnosticTabletScreen<?>)) return;
            var appData = (net.minecraft.nbt.CompoundTag)invoke(mc.screen, "appData", com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.appId("rdp"));
            var devices = appData.getList("Devices", net.minecraft.nbt.Tag.TAG_COMPOUND);
            if(remoteTicks < 10 || devices.size() != 2 || !devices.getCompound(1).getString("Key").equals(remoteDeviceKey)) return;
            check(devices.getCompound(1).getBoolean("Online"), "The paired computer did not appear online in the RDP app");
            capture(Path.of(OUTPUT).resolve("rdp-paired-" + REMOTE_BLOCKS.get(remoteVariant) + ".png"), mc.screen, mc.screen.width, mc.screen.height);
            check(clickTabletCard(mc.screen, tooltip -> tooltip.startsWith("Control ") && !tooltip.contains("Existing ACC")), "The RDP computer Control button did not accept input");
            remoteStage = 3; remoteTicks = 0; return;
        }
        if(remoteStage == 3){
            if(!(mc.screen instanceof dan200.computercraft.client.gui.AbstractComputerScreen<?>)) return;
            if(remoteTicks < 20) return;
            check(!(mc.screen instanceof DiagnosticTabletScreen<?>), "Tablet stayed open over the native computer GUI");
            for(char ch : "lua".toCharArray()) mc.screen.charTyped(ch, 0);
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            remoteStage = 4; remoteTicks = 0; return;
        }
        if(remoteStage == 4){
            if(remoteTicks < 10) return;
            check(mc.screen != null, "Remote computer closed while launching Lua");
            for(char ch : "print('Tablet remote desktop')".toCharArray()) mc.screen.charTyped(ch, 0);
            mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); remoteStage = 5; remoteTicks = 0; return;
        }
        if(remoteStage != 5 || remoteTicks < 10) return;
        if(mc.player.containerMenu instanceof dan200.computercraft.shared.computer.inventory.AbstractComputerMenu menu){
            var terminal = menu.getTerminal();
            boolean found = false;
            if(terminal != null) for(int row = 0; row < terminal.getHeight(); row++) if(terminal.getLine(row).toString().strip().equals("Tablet remote desktop")) found = true;
            if(!found) return;
        }
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "tablet_computer_validation"))){
            int width = mc.screen.width, height = mc.screen.height;
            renderer.render(width, height, width, height, graphics -> mc.screen.render(graphics, -1, -1, 0));
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(width, height, false)){ image.downloadTexture(0, false); image.flipY(); image.writeToFile(Path.of(OUTPUT).resolve("tablet-native-" + REMOTE_BLOCKS.get(remoteVariant) + ".png")); }
        }
        check(mc.screen instanceof dan200.computercraft.client.gui.AbstractComputerScreen<?>, "Remote computer menu failed distance validation");
        mc.screen.onClose(); check(mc.screen == null, "Closing a remote computer reopened or retained the tablet");
        if(++remoteVariant < REMOTE_BLOCKS.size()){ remoteStage = 0; return; }
        finish(mc, null);
    }

    // Click the rendered painted card through the tablet host's actual pointer route
    private static boolean clickTabletCard(net.minecraft.client.gui.screens.Screen screen, java.util.function.Predicate<String> match) throws Exception{
        var id = com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.appId("rdp");
        var renderer = com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry.renderer(id);
        var state = invoke(field(screen, "appClientSession"), "stateFor", id, renderer);
        for(var hit : (List<?>)field(state, "hits")){
            if(!match.test((String)invoke(hit, "tooltip"))) continue;
            var area = (com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout.Rect)invoke(hit, "area");
            double x = (int)field(screen, "left") + (area.left() + area.width() / 2D) * 492D / 320;
            double y = (int)field(screen, "top") + (area.top() + area.height() / 2D) * 286D / 180;
            return screen.mouseClicked(x, y, 0);
        }
        return false;
    }

    // Exercise real catalog replies through the same modal and editor input handlers as a player
    private static void validateWrapCompletion(AdvancedContraptionControllerScreen screen, Path out) throws Exception{
        var graph = (AdvancedGraphDocument)field(screen, "draft");
        var function = com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.find(graph, networkFunction);
        var prototype = com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.prototype(function);
        var editor = (AdvancedControllerLuaEditor)field(screen, "luaEditor");
        invoke(screen, "editLuaSource", prototype, ""); editor.refresh();
        capture(out.resolve("wrap-button.png"), screen, screen.width, screen.height);
        click(screen, field(editor.workspace(), "wrapButton"));
        check((boolean)field(screen, "graphTargetPickerOpen"), "Wrap peripheral did not open the ACC target picker");
        @SuppressWarnings("unchecked") var choices = (List<com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode>)invoke(screen, "graphTargetPickerChoices");
        var target = choices.stream().filter(val -> val.label().equals("thruster")).findFirst().orElseThrow(() -> new AssertionError("The linker thruster was missing from the picker"));
        capture(out.resolve("wrap-target-picker.png"), screen, screen.width, screen.height);
        invoke(screen, "toggleGraphTargetPickerSelection", target);
        click(screen, invoke(screen, "graphTargetPickerAcceptBounds", invoke(screen, "graphTargetPickerBounds")));
        check(!((boolean)field(screen, "graphTargetPickerOpen")) && function.data().getString(LuaGraphNode.SOURCE).equals("local thruster = peripheral.wrap(\"thruster\")\n"), "Picking the thruster did not insert its correct wrap block");
        for(char ch : "thruster.setTh".toCharArray()) screen.charTyped(ch, 0);
        capture(out.resolve("wrapped-method-completion.png"), screen, screen.width, screen.height);
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        check(function.data().getString(LuaGraphNode.SOURCE).endsWith("thruster.setThrottle("), "Tab did not complete the live peripheral's own method");
        invoke(screen, "editLuaSource", com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.prototype(function), ""); editor.refresh();
        for(char ch : "local engine = peripheral.wrap(\"thr".toCharArray()) screen.charTyped(ch, 0);
        capture(out.resolve("target-string-completion.png"), screen, screen.width, screen.height);
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        check(function.data().getString(LuaGraphNode.SOURCE).equals("local engine = peripheral.wrap(\"thruster\""), "Tab did not complete the quoted linker name");
        invoke(screen, "editLuaSource", com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.prototype(function), NETWORK_SOURCE); editor.refresh();
        check(!(boolean)field(screen, "draftDirty"), "Wrapping or completing a peripheral saved the graph");
        invoke(screen, "selectGraphTab", (Object)null);
        int count = graph.nodes().size();
        invoke(screen, "addNode", "createthrusters:lua", 0D, 0D);
        check(field(screen, "luaNameModal") != null && graph.nodes().size() == count, "Placing a new Lua node skipped the embedded naming modal");
        capture(out.resolve("graph-lua-name-modal.png"), screen, screen.width, screen.height);
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(graph.nodes().size() == count, "Cancelling a Lua name placed an unwanted node");
        invoke(screen, "openLuaEditor", prototype);
    }

    private static void finish(Minecraft mc, Throwable error){
        complete = true;
        try{
            if(error == null) Files.writeString(Path.of(OUTPUT).resolve("result.txt"), "PASSED normal/advanced computer and turtle pairing through painted RDP cards with an existing ACC, interactive native remote terminals, saved-graph template gating, scalar inspector padding, minimap occlusion/input, zoomed port markers, live detail cutoff, Lua editor and SQL actions\n");
            else{
                var trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
                Files.writeString(Path.of(OUTPUT).resolve("result.txt"), "FAILED\n" + trace);
            }
        }catch(Exception ignored){}
        finally{ mc.stop(); }
    }

    // Incoming graph and port snapshots must preserve source edits independently of graph saves
    private static void validateSourceRecovery(Minecraft mc) throws Exception{
        var ctrl = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); ctrl.setLevel(mc.level);
        var screen = new AdvancedContraptionControllerScreen(new AdvancedContraptionControllerMenu(-1245, mc.player.getInventory(), ctrl), mc.player.getInventory(), Component.literal("Source recovery"));
        screen.configureForProjection(); screen.init(mc, 800, 500);
        var graph = new AdvancedGraphDocument(); graph.nodes().add(node("Source recovery", 0, 0));
        screen.updateProjectionGraph(graph);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        var script = draft.nodes().getFirst(); String id = script.data().getString(com.rieno.gadgetsandgizmos.lib.script.ScriptFunction.REFERENCE);
        var saved = draft.copy(); saved.setRevision(1);
        invoke(screen, "editLuaSource", script, "output.result = 'unsaved text'");
        var edits = (com.rieno.gadgetsandgizmos.lib.script.ScriptSourceEdits)field(screen, "luaSourceEdits");
        edits.submit(992L);
        var snapshot = new com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload.GraphSnapshot(1, 0,
                saved.toTag(), new net.minecraft.nbt.CompoundTag(), null, null);
        invoke(screen, "adoptControllerGraphSnapshot", snapshot);
        draft = (AdvancedGraphDocument)field(screen, "draft");
        check(draft.nodes().getFirst().data().getString(LuaGraphNode.SOURCE).equals("output.result = 'unsaved text'"), "A graph snapshot replaced pending source text");
        @SuppressWarnings("unchecked") var saves = (Map<Long, AdvancedGraphDocument>)field(screen, "pendingGraphSaves");
        saves.put(993L, saved.copy());
        invoke(screen, "queueGraphActionResult", 993L, true, "Graph saved", 1, true, true, List.of());
        check(!(boolean)field(screen, "draftDirty"), "Pending Lua text made an acknowledged graph dirty again");
        screen.acknowledgeLuaSource(992L, false, "output.result = 'shared update'", "Code changed elsewhere");
        check(edits.status(id).equals("Code save failed") && edits.error(id).equals("Code changed elsewhere"), "The selected script lost its actual save error");
        check(edits.status(UUID.randomUUID().toString()).isEmpty(), "A failed save appeared on another Lua function");
        edits.retry(); var retry = edits.submit(994L);
        check(retry.previous().equals("output.result = 'shared update'") && retry.source().equals("output.result = 'unsaved text'"), "Retry discarded the player's source or used a stale SQL baseline");
        screen.acknowledgeLuaSource(994L, true, retry.source(), "Code saved");
        check(edits.pendingSources().isEmpty() && !(boolean)field(screen, "draftDirty"), "Recovering the source save changed the graph");
    }

    private static void validate(Minecraft mc, Path out) throws Exception{
        var ctrl = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        ctrl.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1235, mc.player.getInventory(), ctrl);
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Lua Validation"));
        screen.configureForProjection(); screen.init(mc, 800, 500);
        var graph = new AdvancedGraphDocument();
        var first = node("Altitude", 35, 40);
        var second = node("Altitude", 350, 60);
        first.data().putString(LuaGraphNode.SOURCE, "");
        second.data().putString(LuaGraphNode.SOURCE, "output.result = ports.value + 20\n");
        graph.nodes().addAll(List.of(first, second));
        screen.updateProjectionGraph(graph);
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        var draft = (AdvancedGraphDocument)field(screen, "draft");
        first = draft.nodes().getFirst(); second = draft.nodes().getLast();
        capture(out.resolve("lua-nodes.png"), screen, 800, 500);
        check((boolean)invoke(screen, "isNodeAvailableInEditor", "createthrusters:lua"), "Lua was unavailable with CC installed");
        var hit = invoke(screen, "nodeInspectorHit", first);
        var inspection = (GraphPortInspection)invoke(screen, "portInspection", hit);
        check(inspection.preview() == GraphPortInspection.Preview.CODE, "The Lua node inspector did not show authored source");
        first.data().putString(LuaGraphNode.SOURCE, "-- Flight controller\nlocal height = ports.value\noutput.result = math.max(0, height * 2)\n");
        invoke(screen, "setPortInspectorPinned", hit, true);
        capture(out.resolve("node-inspector.png"), screen, 800, 500);
        first.data().putString(LuaGraphNode.SOURCE, "");
        invoke(screen, "setPortInspectorPinned", hit, false);
        int nodeX = (int)invoke(screen, "screenX", first.x());
        int nodeWidth = (int)invoke(screen, "nodeWidth", first);
        for(boolean output : List.of(false, true)){
            String port = output ? "result" : "value";
            var pos = invoke(screen, "portPosition", first, port, output);
            int px = output ? nodeX + nodeWidth - 15 : nodeX + 10;
            check((boolean)invoke(screen, "rightClickLuaLabel", first, (double)px, (double)(int)invoke(pos, "y")), "Right-clicking a Lua port label did not start a rename");
            check(field(screen, "bodyValueEditor") != null, "The port label editor was unavailable");
            invoke(field(screen, "bodyValueEditor"), "cancel");
            invoke(screen, "renameLuaPort", first, port, port + " renamed", output);
            check((output ? AdvancedGraphCatalog.outputs(first) : AdvancedGraphCatalog.inputs(first)).containsKey(port + " renamed"), "The Lua port name was not updated");
            invoke(screen, "renameLuaPort", first, port + " renamed", port, output);
        }
        invoke(screen, "addLuaPort", first, false);
        invoke(screen, "addLuaPort", first, true);
        check(field(screen, "optionDropdown") == null, "Adding a Lua port opened a type selector");
        check(first.data().getCompound("DynamicInputs").getString("input").equals("any") && first.data().getCompound("DynamicOutputs").getString("output").equals("any"), "New Lua ports were not generic");
        invoke(screen, "removeDynamicPort", first, "input", false);
        invoke(screen, "removeDynamicPort", first, "output", true);
        var sourceEdits = (com.rieno.gadgetsandgizmos.lib.script.ScriptSourceEdits)field(screen, "luaSourceEdits");
        check(!sourceEdits.pending() && (int)field(screen, "luaSaveTicks") == 0, "Port edits scheduled a code autosave");
        set(screen, "draftDirty", false);
        invoke(screen, "openLuaEditor", first);
        var editor = (AdvancedControllerLuaEditor)field(screen, "luaEditor");
        check((boolean)field(screen, "luaTabOpen"), "The Lua tab did not open");
        capture(out.resolve("empty-editor.png"), screen, 800, 500);
        for(char ch : "gng.getApiV".toCharArray()) screen.charTyped(ch, 0);
        capture(out.resolve("completion.png"), screen, 800, 500);
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        check(first.data().getString(LuaGraphNode.SOURCE).equals("gng.getApiVersion("), "Bridge Tab completion did not edit the selected source");
        check(!(boolean)field(screen, "draftDirty"), "Editing Lua code marked the graph dirty");
        check(sourceEdits.pending(), "A player code edit did not queue its SQL update");
        int graphRevision = draft.revision();
        var codeSave = sourceEdits.submit(991L);
        check(codeSave != null && codeSave.source().equals("gng.getApiVersion("), "The queued SQL update contained the wrong source");
        set(screen, "luaSaveTicks", 0);
        invoke(screen, "queueGraphActionResult", 991L, true, "Code saved", graphRevision + 10, false, false, List.of());
        @SuppressWarnings("unchecked") var saves = (Map<Long, AdvancedGraphDocument>)field(screen, "pendingGraphSaves");
        check(saves.isEmpty() && draft.revision() == graphRevision && !(boolean)field(screen, "draftDirty"),
                "A code acknowledgement saved or changed the graph revision");
        check(!sourceEdits.pending() && !sourceEdits.saving() && (int)field(screen, "luaSaveTicks") == 0, "Acknowledged code was scheduled again");
        invoke(screen, "saveLuaChanges");
        check(saves.isEmpty(), "Code autosave entered the graph save queue");
        invoke(screen, "openLuaEditor", second);
        check(editor == field(screen, "luaEditor"), "Opening another Lua node created a second editor tab");
        var code = field(editor.workspace(), "editor");
        check(invoke(code, "source").equals(second.data().getString(LuaGraphNode.SOURCE)), "Switching scripts did not replace the displayed source");
        invoke(screen, "openLuaEditor", first);
        first.data().putString(LuaGraphNode.SOURCE, "-- Inputs are scoped to this node.\nlocal height = ports.value\noutput.result = math.max(0, height * 2)\n");
        set(screen, "draftSessionRevision", 4L); set(screen, "draftRuntimeRevision", 4L);
        @SuppressWarnings("unchecked") var outputs = (Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeOutputs");
        outputs.put(first.id() + ":result", AdvancedGraphDocument.Value.number(42));
        outputs.put(first.id() + ":valid", AdvancedGraphDocument.Value.bool(true));
        outputs.put(first.id() + ":error", AdvancedGraphDocument.Value.string(""));
        editor.refresh();
        capture(out.resolve("results.png"), screen, 800, 500);
        check(!sourceEdits.pending() && (int)field(screen, "luaSaveTicks") == 0, "Runtime port values triggered a code autosave");
        outputs.put(first.id() + ":result", com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime.fromLibraryValue(
                com.rieno.gadgetsandgizmos.lib.graph.GraphValue.list(List.of(1, Map.of("enabled", true)))));
        editor.refresh();
        capture(out.resolve("table-results.png"), screen, 800, 500);
        check(invoke(screen, "resolvedDisplayPortType", first, "result", true, new java.util.HashSet<String>()).equals("list"),
                "A generic Lua output did not resolve to its runtime type");
        outputs.put(first.id() + ":result", AdvancedGraphDocument.Value.number(42));
        editor.refresh();
        click(screen, field(editor.workspace(), "input"));
        capture(out.resolve("insert-input.png"), screen, 800, 500);
        Object dropdown = invoke(editor.workspace(), "dropdownBounds");
        click(screen, dropdown);
        check(first.data().getString(LuaGraphNode.SOURCE).contains("local value = ports.name"), "Insert Input did not insert the selected port");
        click(screen, field(editor.workspace(), "output"));
        click(screen, invoke(editor.workspace(), "dropdownBounds"));
        check(first.data().getString(LuaGraphNode.SOURCE).contains("output.result = value"), "Insert Output did not insert an output assignment");
        outputs.put(first.id() + ":error", AdvancedGraphDocument.Value.string("Lua:3: attempt to perform arithmetic on a nil value"));
        outputs.put(first.id() + ":valid", AdvancedGraphDocument.Value.bool(false));
        editor.refresh();
        capture(out.resolve("script-error.png"), screen, 800, 500);
        screen.init(mc, 640, 360);
        capture(out.resolve("compact-editor.png"), screen, 640, 360);
        var firstFunction = new AdvancedGraphDocument.FunctionGraph(UUID.randomUUID().toString(), "First");
        var secondFunction = new AdvancedGraphDocument.FunctionGraph(UUID.randomUUID().toString(), "Second");
        var original = node("Function script", 0, 0);
        var copied = new AdvancedGraphDocument.Node(original.id(), original.type(), original.label(), original.x(), original.y(), original.data());
        firstFunction.nodes().add(original); secondFunction.nodes().add(copied);
        draft = (AdvancedGraphDocument)field(screen, "draft");
        draft.functions().addAll(List.of(firstFunction, secondFunction));
        invoke(screen, "openLuaEditor", original);
        screen.charTyped(' ', 0);
        String authored = original.data().getString(LuaGraphNode.SOURCE);
        copied.data().putString(LuaGraphNode.SOURCE, authored);
        invoke(screen, "openLuaEditor", copied);
        code = field(editor.workspace(), "editor");
        invoke(code, "history", field(code, "undo"), field(code, "redo"));
        check(authored.equals(copied.data().getString(LuaGraphNode.SOURCE)), "Undo history leaked between scripts with identical source");
        screen.charTyped('-', 0);
        check(authored.equals(original.data().getString(LuaGraphNode.SOURCE)), "Editing a copied function changed another function's Lua node");
        check(copied.data().getString(LuaGraphNode.SOURCE).startsWith("-"), "The copied function's Lua source was not edited");
        capture(out.resolve("function-scopes.png"), screen, 640, 360);
        screen.updateProjectionGraph(new AdvancedGraphDocument());
        invoke(screen, "selectGraphTab", (Object)null);
        invoke(screen, "openLuaEditor", (Object)null);
        screen.init(mc, 800, 500);
        editor = (AdvancedControllerLuaEditor)field(screen, "luaEditor");
        draft = (AdvancedGraphDocument)field(screen, "draft");
        check(draft.nodes().isEmpty() && draft.luaFunctions().isEmpty() && (boolean)field(screen, "luaTabOpen"), "The empty Lua tab was unavailable");
        capture(out.resolve("empty-library.png"), screen, 800, 500);
        click(screen, field(editor.workspace(), "createButton"));
        var nameModal = (com.rieno.gadgetsandgizmos.lib.client.ui.TextInputModal)field(screen, "luaNameModal");
        check(nameModal != null && !(mc.screen instanceof ControllerTextInputScreen), "Creating a function opened a separate GUI screen");
        nameModal.value("Stored Lua Function");
        capture(out.resolve("lua-name-modal.png"), screen, 800, 500);
        var painted = field(nameModal, "texture");
        check(painted != null && invoke(invoke(painted, "panel"), "texture").toString().endsWith("textures/gui/text_input.png"), "Naming did not use the painted text_input artwork");
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        screen.init(mc, 800, 500);
        draft = (AdvancedGraphDocument)field(screen, "draft");
        check(draft.nodes().isEmpty() && draft.luaFunctions().size() == 1, "Creating a library function placed a graph node");
        var storedFunction = draft.luaFunctions().getFirst();
        check(storedFunction.name().equals("Stored Lua Function"), "The pre-entered function name was lost");
        editor = (AdvancedControllerLuaEditor)field(screen, "luaEditor");
        capture(out.resolve("stored-function.png"), screen, 800, 500);
        code = field(editor.workspace(), "editor");
        invoke(code, "insert", "-- SQL library function\n");
        check(storedFunction.data().getString(LuaGraphNode.SOURCE).startsWith("-- SQL library function"), "Editing an unplaced function did not update its definition");
        @SuppressWarnings("unchecked") var entries = (List<Object>)invoke(screen, "nodeBrowserEntries", "lua");
        check(entries.stream().anyMatch(entry -> {
            try{ return invoke(entry, "id").equals("lua_nodes"); }catch(Exception ex){ throw new RuntimeException(ex); }
        }), "The Lua Nodes category was absent");
        click(screen, field(editor.workspace(), "placeButton"));
        check(draft.nodes().size() == 1 && draft.nodes().getFirst().data().getString(com.rieno.gadgetsandgizmos.lib.script.ScriptFunction.REFERENCE).equals(storedFunction.id()),
                "Placing a library node lost its UUID reference");
        check(!AdvancedGraphCatalog.inputs(draft.nodes().getFirst()).containsKey("exec") && !AdvancedGraphCatalog.outputs(draft.nodes().getFirst()).containsKey("exec"), "A new Lua node retained default execution sockets");
        check("target".equals(AdvancedGraphCatalog.outputs(draft.nodes().getFirst()).get(LuaGraphNode.INSTANCE)), "The computer instance target output was missing");
        invoke(screen, "selectGraphTab", (Object)null);
        set(screen, "leftSidebarCollapsed", false);
        ((net.minecraft.client.gui.components.EditBox)field(screen, "nodeSearch")).setValue("lua");
        capture(out.resolve("lua-library.png"), screen, 800, 500);
        int left = (int)invoke(screen, "graphLeft");
        check((boolean)invoke(screen, "handleGraphTabClick", (double)(left + 10), 35D, 0) && (boolean)field(screen, "luaTabOpen"), "Lua was not the first graph tab");
        invoke(screen, "selectGraphTab", (Object)null);
        int count = draft.nodes().size();
        invoke(screen, "addNode", "createthrusters:lua", 40D, 40D);
        nameModal = (com.rieno.gadgetsandgizmos.lib.client.ui.TextInputModal)field(screen, "luaNameModal");
        check(nameModal != null && draft.nodes().size() == count, "New Lua Function placed a node before naming");
        nameModal.value("Named Before Placement");
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(draft.nodes().size() == count + 1 && draft.luaFunctions().size() == 2, "Confirming the node name did not create its reusable function");
        var renamed = draft.luaFunctions().getLast();
        invoke(screen, "openLuaEditor", com.rieno.gadgetsandgizmos.content.advanced.AdvancedLuaFunctions.prototype(renamed));
        capture(out.resolve("lua-rename-delete-controls.png"), screen, 800, 500);
        editor = (AdvancedControllerLuaEditor)field(screen, "luaEditor");
        int sidebarX = (int)field(editor.workspace(), "leftWidth");
        int rowTop = (int)field(editor.workspace(), "browserTop") + 31;
        editor.workspace().mouseClicked(sidebarX - 35, rowTop + 10, 0);
        nameModal = (com.rieno.gadgetsandgizmos.lib.client.ui.TextInputModal)field(screen, "luaNameModal");
        check(nameModal != null && nameModal.value().equals(renamed.name()), "The rename pen did not open the function's name");
        nameModal.value("Renamed in Lua editor"); screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(renamed.name().equals("Renamed in Lua editor"), "The sidebar rename did not update the definition");
        var renamedNode = draft.nodes().getLast();
        check(invoke(screen, "luaNodeName", renamedNode).equals("Renamed in Lua editor"), "The sidebar rename did not update the node body");
        invoke(screen, "selectOnly", renamedNode.id()); set(screen, "selectedInputPort", "name");
        invoke(screen, "updateNodeProperty", "Renamed on body");
        editor.refresh();
        var docs = (List<?>)field(editor.workspace(), "documents");
        check(docs.stream().anyMatch(doc -> { try{ return invoke(doc, "name").equals("Renamed on body"); }catch(Exception ex){ throw new RuntimeException(ex); } }), "The editor retained a stale function name");
        editor.workspace().mouseClicked(sidebarX - 17, rowTop + 10, 0);
        var actions = (Map<Long, ?>)field(screen, "pendingLuaActions");
        long deleteRequest = actions.keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        invoke(screen, "queueGraphActionResult", deleteRequest, true, "Deleted", draft.revision() + 1, false, false, List.of());
        check(draft.luaFunctions().stream().noneMatch(val -> val.id().equals(renamed.id())) && draft.nodes().stream().noneMatch(val -> val.id().equals(renamedNode.id())), "Deleting a library function retained GUI entries or placed instances");

    }

    private static void click(AdvancedContraptionControllerScreen screen, Object rect) throws Exception{
        screen.mouseClicked((int)invoke(rect, "x") + 8, (int)invoke(rect, "y") + 7, 0);
    }

    private static void validateExecutionAndInspector(Minecraft mc, Path out) throws Exception{
        var ctrl = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        ctrl.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1236, mc.player.getInventory(), ctrl);
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Lua Inspector Validation"));
        screen.configureForProjection(); screen.init(mc, 800, 500);
        var graph = new AdvancedGraphDocument();
        var node = node("Events and screen", 20, 20);
        node.data().putString(LuaGraphNode.SOURCE, "node.addExecIn('step')\nnode.addExecOut('finished')\n"
                + "execEvent.on('step').run = function() output.result = ports.value execEvent.pulse('finished') end\n-- " + "long source ".repeat(30));
        com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.change(node.data(), "step", false, true, false);
        com.rieno.gadgetsandgizmos.lib.script.ScriptPorts.change(node.data(), "finished", true, true, false);
        graph.nodes().add(node);
        screen.updateProjectionGraph(graph);
        set(screen, "zoom", 1D); set(screen, "panX", 0D); set(screen, "panY", 0D);
        var draft = (AdvancedGraphDocument)field(screen, "draft"); node = draft.nodes().getFirst();
        set(screen, "luaTabOpen", false);
        capture(out.resolve("exec-body-ports.png"), screen, 800, 500);
        int header = (int)invoke(screen, "screenY", node.y()) + 12;
        int input = (int)invoke(invoke(screen, "portPosition", node, "step", false), "y");
        int output = (int)invoke(invoke(screen, "portPosition", node, "finished", true), "y");
        check(input > header + 20 && output > input, "Custom execution ports were placed in the title bar or overlapped");
        var hit = invoke(screen, "nodeInspectorHit", node);
        invoke(screen, "setPortInspectorPinned", hit, true);
        var identity = com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Port.node(node.id());
        invoke(screen, "savePortInspectorState", invoke(screen, "portInspectorScope"), identity,
                new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(15, 20, com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.ADVANCED));
        capture(out.resolve("editable-inspector.png"), screen, 800, 500);
        var bubble = (com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble)((List<?>)field(screen, "renderedInspectors")).getFirst();
        var bounds = bubble.bounds();
        set(screen, "draftDirty", false);
        check(screen.mouseClicked(bounds.x() + 70, bounds.y() + 42, 0), "The pinned code editor did not capture its click");
        screen.mouseReleased(bounds.x() + 70, bounds.y() + 42, 0);
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        screen.charTyped('-', 0); screen.charTyped('-', 0);
        check(node.data().getString(LuaGraphNode.SOURCE).startsWith("--"), "Inspector typing did not edit the shared source");
        check(!(boolean)field(screen, "draftDirty") && ((Map<?, ?>)field(screen, "pendingGraphSaves")).isEmpty(), "Inspector edits saved the whole graph");
        check(((com.rieno.gadgetsandgizmos.lib.script.ScriptSourceEdits)field(screen, "luaSourceEdits")).pending(), "Inspector edits did not schedule source-only autosave");
        bubble.mouseScrolled(bounds.x() + 70, bounds.y() + 60, 0, -8, true);
        var editor = (com.rieno.gadgetsandgizmos.lib.client.ui.CodeEditor)field(bubble, "codeEditor");
        check(editor.horizontalOffset() > 0, "The inspector code editor did not scroll horizontally");
        capture(out.resolve("inspector-horizontal.png"), screen, 800, 500);
        var palette = java.util.List.of(0xFFF0F0F0, 0xFFF2B233, 0xFFE57FD8, 0xFF99B2F2, 0xFFDEDE6C, 0xFF7FCC19, 0xFFF2B2CC, 0xFF4C4C4C,
                0xFF999999, 0xFF4C99B2, 0xFFB266E5, 0xFF3366CC, 0xFF7F664C, 0xFF57A64E, 0xFFCC4C4C, 0xFF111111);
        var text = new java.util.ArrayList<String>(); var foreground = new java.util.ArrayList<String>(); var background = new java.util.ArrayList<String>();
        for(int row = 0; row < 19; row++){
            String line = row == 0 ? "G&G Lua Computer" : row == 2 ? "Native CC:Tweaked terminal" : row == 4 ? "Per-cell colours, palette and cursor" : row == 6 ? "0123456789 ABCDEFGHIJKLMNOPQRSTUVWXYZ" : "";
            text.add(String.format("%-51s", line)); foreground.add((row % 2 == 0 ? "0" : "a").repeat(51));
            background.add((row == 4 ? "b" : "f").repeat(51));
        }
        var terminal = new com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal(51, 19, text, foreground, background, palette, 0, 8, true, 0);
        ((Map<String, AdvancedGraphDocument.Value>)field(screen, "draftRuntimeOutputs")).put(node.id() + ":" + com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.PORT,
                com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime.fromLibraryValue(terminal.toValue()));
        set(bubble, "state", new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(15, 20, com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.GUI));
        capture(out.resolve("terminal-inspector.png"), screen, 800, 500);
        set(screen, "zoom", .7D);
        capture(out.resolve("terminal-inspector-scaled.png"), screen, 800, 500);
        var events = new java.util.ArrayList<com.rieno.gadgetsandgizmos.lib.script.ScriptInput>();
        bubble.terminalInput(events::add);
        bounds = bubble.bounds();
        double mx = bounds.x() + 170 * .7, my = bounds.y() + 122 * .7;
        check(bubble.mouseClicked(mx, my, 0) && bubble.terminalFocused(), "Clicking a scaled GUI did not focus its terminal");
        bubble.mouseDragged(mx + 4, my, 0); bubble.mouseReleased();
        bubble.keyPressed(GLFW.GLFW_KEY_ENTER); bubble.charTyped('a'); bubble.keyReleased(GLFW.GLFW_KEY_ENTER);
        bubble.mouseScrolled(mx, my, 0, -1, false);
        check(events.stream().map(com.rieno.gadgetsandgizmos.lib.script.ScriptInput::action).toList().equals(List.of("click", "drag", "release", "key", "char", "key_up", "scroll")), "Inspector GUI input did not produce native terminal events: " + events);
        check(java.util.Arrays.equals(events.getFirst().arguments(51, 19), new Object[]{1, 26, 10}), "Scaled GUI mouse coordinates did not match the rendered cell");
        check(bubble.keyPressed(GLFW.GLFW_KEY_ESCAPE) && !bubble.terminalFocused(), "Escape did not release terminal focus");
        var original = bubble.state();
        ((java.util.Set<String>)field(screen, "selectedNodes")).add(node.id());
        invoke(screen, "moveSelected", 48D, 32D);
        capture(out.resolve("inspector-follows-node.png"), screen, 800, 500);
        check(bubble.state().x() == original.x() + 48 && bubble.state().y() == original.y() + 32, "Inspector offset changed when its node moved");
        bounds = bubble.bounds();
        check(bubble.mouseClicked(bounds.right() - 3, bounds.bottom() - 3, 0), "The resize handle missed pointer capture");
        bubble.mouseDragged(bounds.right() + 67, bounds.bottom() + 53, 0); bubble.mouseReleased();
        check(bubble.state().width() >= 439 && bubble.state().height() >= 301, "The handle did not resize in graph coordinates at 70% zoom");
        capture(out.resolve("terminal-inspector-resized.png"), screen, 800, 500);
        var terminalArea = field(bubble, "terminalArea");
        check((int)invoke(terminalArea, "width") == bubble.state().width() - 20 && (int)invoke(terminalArea, "height") == bubble.state().height() - 42, "The terminal viewport did not use the resized bounds");
        events.clear(); bubble.terminalInput(events::add);
        bounds = bubble.bounds();
        double centerX = bounds.x() + bubble.state().width() * .7 / 2;
        double centerY = bounds.y() + (32 + (bubble.state().height() - 42) / 2.0) * .7;
        bubble.mouseClicked(centerX, centerY, 0); bubble.mouseReleased();
        check(java.util.Arrays.equals(events.getFirst().arguments(51, 19), new Object[]{1, 26, 10}), "Resizing changed terminal click cell mapping");
    }

    // Compare actual framebuffer pixels where rear titles and node widgets must be occluded
    private static void validateInspectorLayers(Minecraft mc, Path out) throws Exception{
        var style = new com.rieno.gadgetsandgizmos.lib.client.ui.HelpModalStyle(0xFF336699, 0xFF336699, 0xFF336699, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
        var appearance = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Appearance(style, 0xFFFFFFFF,
                (graphics, x, y, w, h, title) -> graphics.fill(x, y, x + w, y + h, 0xFF336699));
        var port = new GraphPortInspection("Output", "string", true, com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("value"), null);
        for(boolean modal : List.of(false, true)){
            try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "inspector_layer_validation"))){
                renderer.render(420, 280, 420, 280, graphics -> {
                    try(var layer = com.rieno.gadgetsandgizmos.lib.client.ui.GuiRenderLayer.pushFlattened(graphics, 400)){
                        graphics.fill(70, 70, 310, 180, 0xFFAA0000); graphics.drawString(mc.font, "Node foreground under the bubble", 90, 100, 0xFFFFFFFF);
                    }
                    var viewport = new com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport(0, 0, 0, 0, 1);
                    var canvas = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Rect(0, 0, 420, 280);
                    new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble().renderGraph(graphics, mc.font, canvas, viewport,
                            new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(80, 85, com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.SIMPLE, 300, 140),
                            port, appearance.withTitle(Component.literal("Rear title must be hidden")), null, -1, -1, val -> {}, () -> {}, 430, .00001F);
                    new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble().renderGraph(graphics, mc.font, canvas, viewport,
                            new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(50, 30, com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.SIMPLE, 300, 200),
                            port, appearance.withTitle(Component.literal("Front bubble")), null, -1, -1, val -> {}, () -> {}, 430.05F, .00001F);
                    if(modal) try(var layer = com.rieno.gadgetsandgizmos.lib.client.ui.GuiRenderLayer.pushFlattened(graphics, 500)){
                        graphics.fill(60, 70, 320, 180, 0xFF00BB22);
                    }
                });
                RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
                try(var image = new NativeImage(420, 280, false)){
                    image.downloadTexture(0, false); image.flipY(); image.writeToFile(out.resolve(modal ? "inspector-under-modal.png" : "inspector-overlap.png"));
                    for(int y = 85; y < 112; y++) for(int x = 80; x < 300; x++)
                        check(image.getPixelRGBA(x, y) == (modal ? 0xFF22BB00 : 0xFF996633), "Rear inspector or node text leaked above " + (modal ? "a modal" : "the front bubble") + " at " + x + "," + y);
                }
            }
        }
    }

    // Check saved-state onboarding, scalar clipping, marker zoom and minimap occlusion in real frames
    private static void validateGraphPresentation(Minecraft mc, Path out) throws Exception{
        var ctrl = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); ctrl.setLevel(mc.level);
        var menu = new AdvancedContraptionControllerMenu(-1250, mc.player.getInventory(), ctrl);
        check(!menu.hasSavedGraph(), "A new controller was considered saved");
        check((boolean)field(new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("New")), "templatePicker"), "A new controller lost its initial template choice");
        var saved = new AdvancedGraphDocument(); saved.setRevision(1); set(ctrl, "draftGraph", saved);
        menu = new AdvancedContraptionControllerMenu(-1251, mc.player.getInventory(), ctrl);
        check(menu.hasSavedGraph(), "A saved empty graph was considered new");
        set(menu, "initialDraft", new AdvancedGraphDocument());
        var screen = new AdvancedContraptionControllerScreen(menu, mc.player.getInventory(), Component.literal("Saved"));
        check(!(boolean)field(screen, "templatePicker"), "A missing graph packet prompted an existing controller for a template");
        screen.configureForProjection(); screen.init(mc, 800, 500);
        var graph = new AdvancedGraphDocument(); graph.nodes().add(node("Presentation", 0, 0)); screen.updateProjectionGraph(graph); invoke(screen, "selectGraphTab", (Object)null);
        var script = ((AdvancedGraphDocument)field(screen, "draft")).nodes().getFirst();
        var config = com.rieno.gadgetsandgizmos.config.CTConfigs.CLIENT.accNodeDetailZoomThreshold;
        double previous = config.get();
        try{
            set(screen, "zoom", .35D); config.set(.55D);
            var pos = invoke(screen, "portPosition", script, "result", true); int x = (int)invoke(pos, "x"), y = (int)invoke(pos, "y");
            check(invoke(screen, "portAt", (double)x, (double)y) == null, "Hidden ports retained their hit targets");
            config.set(.2D);
            check((boolean)invoke(screen, "nodeDetailsVisible") && invoke(screen, "portAt", (double)x, (double)y) != null, "Changing the detail cutoff did not restore port interaction immediately");
        }finally{ config.set(previous); }
        set(screen, "zoom", 1D);
        capture(out.resolve("minimap-without-inspector.png"), screen, 800, 500);
        var map = invoke(screen, "v2MinimapBounds"); int mapX = (int)invoke(map, "x"), mapY = (int)invoke(map, "y"), mapW = (int)invoke(map, "width"), mapH = (int)invoke(map, "height");
        var hitType = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.PortHit"); var ctor = hitType.getDeclaredConstructor(AdvancedGraphDocument.Node.class, String.class, boolean.class); ctor.setAccessible(true);
        invoke(screen, "setPortInspectorPinned", ctor.newInstance(script, "result", true), true);
        capture(out.resolve("minimap-inspector-pending.png"), screen, 800, 500);
        var bubble = (com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble)((List<?>)field(screen, "renderedInspectors")).getFirst();
        var viewport = (com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport)invoke(screen, "graphViewport");
        set(bubble, "state", new com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State(viewport.graphX(mapX - 12), viewport.graphY(mapY - 28), com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.ADVANCED, 480, 280));
        capture(out.resolve("minimap-over-inspector.png"), screen, 800, 500);
        try(var baselineStream = Files.newInputStream(out.resolve("minimap-without-inspector.png")); var actualStream = Files.newInputStream(out.resolve("minimap-over-inspector.png"));
            var baseline = NativeImage.read(baselineStream); var actual = NativeImage.read(actualStream)){
            for(int y = mapY + 6; y < mapY + mapH - 6; y++) for(int x = mapX + 6; x < mapX + mapW - 6; x++)
                check(baseline.getPixelRGBA(x, y) == actual.getPixelRGBA(x, y), "A pinned inspector rendered above the minimap at " + x + "," + y);
        }
        set(screen, "zoom", .8D); check(screen.mouseClicked(mapX + mapW - 20, mapY + 10, 0) && (double)field(screen, "zoom") == 1D, "An inspector intercepted the minimap reset control");
        var style = new com.rieno.gadgetsandgizmos.lib.client.ui.HelpModalStyle(0xFF202A30, 0xFF303D44, 0xFF667788, 0xFFFFFFFF, 0xFFAAAAAA, 0xFFCCAA66);
        var appearance = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Appearance(style, 0xFFFFFFFF, null);
        var values = List.of(com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("A scalar value"), com.rieno.gadgetsandgizmos.lib.graph.GraphValue.number(123.456), com.rieno.gadgetsandgizmos.lib.graph.GraphValue.bool(true), com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("Long values still need enough room to display the final wrapped line without clipping inside the bubble."));
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "scalar_inspection_validation"))){
            renderer.render(660, 300, 660, 300, graphics -> {
                for(int idx = 0; idx < values.size(); idx++){
                    var val = values.get(idx); var inspector = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble();
                    inspector.render(graphics, mc.font, 660, 300, 10 + (idx % 2) * 330, 10 + (idx / 2) * 140, new GraphPortInspection("Value", val.type(), true, val, null), appearance, null, false, -1, -1, List.of(), () -> {});
                    var area = (com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Rect)fieldUnchecked(inspector, "textArea");
                    check(area.height() >= (int)fieldUnchecked(inspector, "textHeight"), "Scalar hover content clipped for " + val.type());
                }
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(660, 300, false)){ image.downloadTexture(0, false); image.flipY(); image.writeToFile(out.resolve("scalar-inspector-padding.png")); }
        }
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "port_zoom_validation"))){
            renderer.render(240, 60, 240, 60, graphics -> {
                for(int idx = 0; idx < 3; idx++) com.rieno.gadgetsandgizmos.lib.client.render.GraphPortRenderer.data(graphics, 40 + idx * 80, 30, .5 + idx * .5, 0xFFEE2211, 0xFFEE2211, 0xFFEE2211);
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(240, 60, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(out.resolve("port-zoom.png")); int[] widths = new int[3];
                for(int idx = 0; idx < 3; idx++) for(int x = idx * 80; x < (idx + 1) * 80; x++) if(image.getPixelRGBA(x, 30) == 0xFF1122EE) widths[idx]++;
                check(widths[0] >= 4 && widths[1] > widths[0] && widths[2] > widths[1], "Port markers retained a fixed screen diameter across zoom levels");
            }
        }
    }

    // Render transient inspectors with and without native terminals or host-owned live feeds
    private static void validateHoverInspectors(Minecraft mc, Path out) throws Exception{
        var style = new com.rieno.gadgetsandgizmos.lib.client.ui.HelpModalStyle(0xFF202A30, 0xFF303D44, 0xFF667788, 0xFFFFFFFF, 0xFFAAAAAA, 0xFFCCAA66);
        var appearance = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Appearance(style, 0xFFFFFFFF, null);
        var code = new GraphPortInspection("Code", "lua", true, "", com.rieno.gadgetsandgizmos.lib.graph.GraphValue.string("print('Preview')"), GraphPortInspection.Preview.CODE, null);
        var terminal = new com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal(4, 2, List.of("Test", "Feed"), List.of("0000", "eeee"), List.of("ffff", "ffff"), java.util.Collections.nCopies(16, 0xFFAA3311), 0, 0, false, 0);
        var gui = new GraphPortInspection("Code", "lua", true, "", code.value(), GraphPortInspection.Preview.CODE, terminal.toValue());
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "hover_gui_validation"))){
            renderer.render(720, 500, 720, 500, graphics -> {
                var fallback = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble();
                fallback.render(graphics, mc.font, 720, 500, 10, 10, code, appearance, null, false, -1, -1, List.of(), () -> {});
                check(fallback.state().mode() == com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.ADVANCED && fieldUnchecked(fallback, "codeArea") != null, "Unavailable Lua terminals did not fall back to Advanced code");
                var live = new com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.Bubble();
                live.render(graphics, mc.font, 720, 500, 360, 10, gui, appearance, null, false, -1, -1, List.of(), () -> {});
                check(live.state().mode() == com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode.GUI && fieldUnchecked(live, "terminal") != null, "Hover inspection did not prefer its native terminal");
                var drawn = new java.util.concurrent.atomic.AtomicBoolean();
                com.rieno.gadgetsandgizmos.lib.client.ui.GraphPortInspector.render(graphics, mc.font, 720, 500, 10, 270, code, appearance, null,
                        (ctx, font, area) -> { drawn.set(true); ctx.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF88CCAA); });
                check(drawn.get(), "Transient inspection did not draw its live feed by default");
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(720, 500, false)){ image.downloadTexture(0, false); image.flipY(); image.writeToFile(out.resolve("hover-gui-defaults.png")); }
        }
    }

    private static Object fieldUnchecked(Object obj, String name){
        try{ return field(obj, name); }catch(Exception ex){ throw new RuntimeException(ex); }
    }

    // Use the real CC renderer's coloured cell geometry to check the client's inverse transform
    private static void validateExternalTerminalProjection(Minecraft mc, Path out) throws Exception{
        var display = new com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity(BlockPos.ZERO, CTBlocks.ACC_DISPLAY.get().defaultBlockState()); display.setLevel(mc.level);
        var lines = new java.util.ArrayList<String>(); var foreground = new java.util.ArrayList<String>(); var background = new java.util.ArrayList<String>();
        for(int row = 0; row < 19; row++){
            lines.add(" ".repeat(51)); foreground.add("0".repeat(51)); background.add(row == 2 ? "fffffe" + "f".repeat(45) : "f".repeat(51));
        }
        var palette = new java.util.ArrayList<>(java.util.Collections.nCopies(16, 0xFF000000)); palette.set(0, 0xFFFFFFFF); palette.set(14, 0xFFCC4C4C);
        var terminal = new com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal(51, 19, lines, foreground, background, palette, 0, 0, false, 0);
        Class<?> pointType = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayGuiProjection$ScreenPoint");
        var ctor = pointType.getDeclaredConstructor(double.class, double.class); ctor.setAccessible(true);
        var inverse = AccDisplayGuiProjection.class.getDeclaredMethod("contentPoint", com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity.class, pointType); inverse.setAccessible(true);
        var computerPoint = AccDisplayGuiProjection.class.getDeclaredMethod("computerPoint", com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity.class, pointType); computerPoint.setAccessible(true);
        for(int[] size : new int[][]{{320, 180}, {600, 180}, {160, 360}}) for(int rotation : new int[]{0, 27}){
            var frame = new net.minecraft.nbt.CompoundTag(); frame.putString("State", "external"); frame.putString("ContentNode", "terminal");
            frame.putDouble("ContentX", (320 - size[0]) * .5); frame.putDouble("ContentY", (180 - size[1]) * .5); frame.putDouble("ContentWidth", size[0]); frame.putDouble("ContentHeight", size[1]);
            frame.putDouble("ContentScale", .65); frame.putDouble("ContentRotation", rotation); frame.put("External", terminal.displayFrame());
            var sources = new net.minecraft.nbt.ListTag(); sources.add(new net.minecraft.nbt.CompoundTag()); sources.add(new net.minecraft.nbt.CompoundTag()); frame.put("Sources", sources);
            set(display, "displayFrame", frame);
            var layout = display.contentLayout(); var poses = new com.mojang.blaze3d.vertex.PoseStack();
            poses.translate(layout.x() + layout.width() * .5, layout.y() + layout.height() * .5, 0);
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(layout.rotation())); poses.scale(layout.scale(), layout.scale(), 1);
            poses.translate(-layout.width() * .5, -layout.height() * .5, 0);
            var sink = new TerminalVertices();
            check(com.rieno.gadgetsandgizmos.compat.computercraft.client.AccDisplayComputerCraftRenderer.render(terminal.displayFrame(), layout.width(), layout.height(), poses, ignored -> sink), "Native terminal renderer rejected a valid frame");
            var red = sink.vertices.stream().filter(vertex -> vertex.color == 0xFFCC4C4C).toList();
            check(red.size() == 4, "The native terminal did not render the red target cell: " + red.size());
            double px = red.stream().mapToDouble(vertex -> vertex.x).average().orElseThrow();
            double py = red.stream().mapToDouble(vertex -> vertex.y).average().orElseThrow();
            var point = inverse.invoke(null, display, ctor.newInstance(px / display.surfacePixelWidth(), py / display.surfacePixelHeight()));
            check(point != null, "The rendered cell was rejected at " + size[0] + "x" + size[1] + " / " + rotation + " pixel " + px + "," + py + " on " + display.surfacePixelWidth() + "x" + display.contentPixelHeight());
            double x = (double)invoke(point, "x"), y = (double)invoke(point, "y");
            var mapped = com.rieno.gadgetsandgizmos.lib.script.ScriptInput.point(x * layout.width(), y * layout.height(), layout.width(), layout.height(), 51, 19);
            check(mapped != null && java.util.Arrays.equals(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", mapped[0], mapped[1], 0).arguments(51, 19), new Object[]{1, 6, 3}), "A native rendered cell did not map back to column 6, row 3 at " + size[0] + "x" + size[1] + " / " + rotation);
            check(computerPoint.invoke(null, display, ctor.newInstance(px / display.surfacePixelWidth(), py / display.surfacePixelHeight())) != null, "Client terminal hit testing rejected the rendered cell");
            var fit = com.rieno.gadgetsandgizmos.lib.display.SurfaceFit.fit(306, 171, layout.width(), layout.height(), true);
            var bar = new org.joml.Vector3f((float)(fit.x() > 0 ? fit.x() / 2 : layout.width() / 2.0), (float)(fit.y() > 0 ? fit.y() / 2 : layout.height() / 2.0), 0);
            poses.last().pose().transformPosition(bar);
            check(computerPoint.invoke(null, display, ctor.newInstance(bar.x / display.surfacePixelWidth(), bar.y / display.surfacePixelHeight())) == null, "A letterbox point captured native terminal input");
        }
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "external_terminal_validation"))){
            renderer.render(360, 280, 360, 280, graphics -> {
                graphics.fill(0, 0, 360, 280, 0xFF223344);
                graphics.pose().pushPose(); graphics.pose().translate(30, 20, 100); graphics.pose().scale(4, 4, .001F);
                com.rieno.gadgetsandgizmos.compat.computercraft.client.AccDisplayComputerCraftRenderer.render(terminal.displayFrame(), 60, 44, graphics.pose(), graphics.bufferSource());
                graphics.flush(); graphics.pose().popPose();
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(360, 280, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(out.resolve("external-terminal-fit.png"));
            }
        }
    }

    private static final class TerminalVertices implements com.mojang.blaze3d.vertex.VertexConsumer{
        private final java.util.List<TerminalVertex> vertices = new java.util.ArrayList<>();
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z){ vertices.add(new TerminalVertex(x, y)); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a){ vertices.getLast().color = a << 24 | r << 16 | g << 8 | b; return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v){ return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v){ return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v){ return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
    private static final class TerminalVertex{
        private final float x, y;
        private int color;
        private TerminalVertex(float x, float y){ this.x = x; this.y = y; }
    }

    private static void capture(Path path, net.minecraft.client.gui.screens.Screen screen, int width, int height) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "lua_editor_validation"))){
            renderer.render(width, height, width, height, graphics -> screen.render(graphics, -1, -1, 0));
            RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(width, height, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(path);
            }
        }
    }

    private static AdvancedGraphDocument.Node node(String name, double x, double y){
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:lua", AdvancedGraphNodeFactory.Context.EMPTY);
        data.getCompound("Defaults").put("name", AdvancedGraphDocument.Value.string(name).toTag());
        return new AdvancedGraphDocument.Node(UUID.randomUUID().toString(), "createthrusters:lua", "Lua", x, y, data);
    }

    private static Object field(Object obj, String name) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(obj);
    }
    private static void set(Object obj, String name, Object val) throws Exception{
        var field = obj.getClass().getDeclaredField(name); field.setAccessible(true); field.set(obj, val);
    }
    private static Object invoke(Object obj, String name, Object... args) throws Exception{
        for(var method : obj.getClass().getDeclaredMethods()) if(method.getName().equals(name) && method.getParameterCount() == args.length){
            method.setAccessible(true); return method.invoke(obj, args);
        }
        throw new NoSuchMethodException(name);
    }
    private static void check(boolean condition, String message){ if(!condition) throw new AssertionError(message); }
}
