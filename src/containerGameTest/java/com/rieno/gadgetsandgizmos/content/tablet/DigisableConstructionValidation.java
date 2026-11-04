package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.time.ClockTime;
import com.rieno.gadgetsandgizmos.lib.graph.TimeGraphNode;
import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Exercise real Digisable workers and native tracking in an opt-in isolated client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class DigisableConstructionValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final boolean ENABLED = Boolean.getBoolean("gadgetsngizmos.digisableValidation");
    private static final Vec3 TARGET = new Vec3(128, 100, 128);
    private static final AABB WORKERS = new AABB(120, 92, 120, 150, 110, 145);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Exchange completion and failure state between the owning game threads
    private static volatile int phase;
    private static volatile Throwable failure;
    private static volatile UUID builtBody;
    private static AdvancedContraptionControllerBlockEntity copiedController;
    private static CompletableFuture<Void> setup;
    // Retain server-owned app fixtures until construction and cancellation finish
    private static TabletActionContext ctx;
    private static UUID schematicId;
    private static Set<UUID> previousBodies;
    private static int serverTicks;
    private static boolean sawWorkers;
    // Retain the original controller reference and server zone for copy and editor checks
    private static String sourceManifest;
    private static String serverTimezone;
    // Count responsive client ticks while the workers are active
    private static int clientTicks;
    private static long started;
    private static boolean finished;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Start the real app flow and check its tracked result from the client thread
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post evt){
        if(!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if(mc.screen instanceof PauseScreen) mc.setScreen(null);
        try{
            if(setup == null){
                started = System.nanoTime();
                var server = mc.getSingleplayerServer();
                setup = server.submit(() -> {
                    try{ begin(server.getPlayerList().getPlayers().getFirst()); }
                    catch(Exception err){ throw new IllegalStateException(err); }
                });
            }
            if(setup.isDone()) setup.join();
            if(failure != null) throw new AssertionError("Server construction validation failed", failure);
            if(System.nanoTime() - started > 60_000_000_000L) throw new AssertionError("Construction validation timed out at phase " + phase);
            clientTicks++;
            if(phase == 2){
                var body = SubLevelContainer.getContainer(mc.level).getSubLevel(builtBody);
                if(!(body instanceof ClientSubLevel clientBody) || !clientBody.isFinalized()) return;
                if(!(mc.player.containerMenu instanceof AdvancedContraptionControllerMenu menu) || mc.screen == null) return;
                require(serverTimezone.equals(menu.getServerTimezone()), "The menu did not synchronize its server timezone");
                var time = menu.getInitialDraft().nodes().stream().filter(node -> "createthrusters:time".equals(node.type())).findFirst().orElseThrow();
                require(time.data().getCompound("Defaults").getCompound("timezone").getCompound("Payload").getString("Value").equals(serverTimezone),
                        "The copied Time node lost its server timezone default");
                verifyTimezoneDropdown((AdvancedContraptionControllerScreen)mc.screen);
                verifyBeacons(clientBody);
                require(clientTicks >= 40, "Client did not keep ticking during construction");
                phase = 3;
            }else if(phase == 4){
                finished = true;
                writeResult("PASSED: native empty bounds; four Digisable workers; completed schematic; independent ACC graph identity and edits; Time node execution and server timezone menu synchronization; searchable timezone typing, selection, clearing and empty results; offset-free mode controls; loaded beacon discovery over full integer bounds; cancellation removes workers and partial plots; " + clientTicks + " responsive client ticks");
                mc.stop();
            }
        }catch(Throwable err){
            finished = true;
            LogUtils.getLogger().error("Digisable construction validation failed", err);
            writeResult("FAILED: " + err);
            mc.stop();
        }
    }

    // Verify the same server session used by the app completes and cancels cleanly
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post evt){
        if(!ENABLED || ctx == null || failure != null || phase == 4) return;
        try{
            if(phase == 1){
                serverTicks++;
                var workers = ctx.player().serverLevel().getEntitiesOfClass(PlayerMannequinEntity.class, WORKERS);
                if(workers.size() == 4) sawWorkers = true;
                var data = DigisableApp.snapshot(ctx);
                if(data.getBoolean("Building")) return;
                require(sawWorkers, "The app did not spawn four construction workers");
                require("Schematic built".equals(data.getString("BuildStatus")), data.getString("BuildStatus"));
                require(data.getInt("BuildPlaced") == 13 && data.getInt("BuildTotal") == 13, "Construction lost blocks");
                require(workers.isEmpty(), "Completed construction retained its workers");
                var bodies = SubLevelContainer.getContainer(ctx.player().serverLevel()).getAllSubLevels().stream()
                        .filter(body -> !previousBodies.contains(body.getUniqueId())).toList();
                require(bodies.size() == 1, "Completed construction did not retain one native body");
                builtBody = bodies.getFirst().getUniqueId();
                var controllers = SubLevelBlockEntityCollector.getBlockEntities(bodies.getFirst(), AdvancedContraptionControllerBlockEntity.class);
                require(controllers.size() == 1, "The schematic did not create its ACC");
                var controller = controllers.getFirst();
                String copyId = ControllerManifestStore.controllerManifestId(controller.saveWithId(ctx.player().registryAccess()));
                if(copyId.isBlank()){
                    require(serverTicks < 240, "The constructed ACC did not finalize its graph import");
                    return;
                }
                require(!sourceManifest.equals(copyId), "Digisable reused the source graph UUID " + copyId);
                var time = controller.getDraftGraph().nodes().getFirst();
                String expected = ClockTime.world(ctx.player().serverLevel().getDayTime()).text();
                require(expected.equals(new GraphRuntime(controller, false).previewOutput(controller.getDraftGraph(), time, "time").asString()),
                        "The constructed ACC did not execute its Time node");
                var copiedGraph = controller.getDraftGraph();
                copiedGraph.nodes().add(new AdvancedGraphDocument.Node("copy_only", "constant_number", "Copy", 220, 0, new CompoundTag()));
                require(controller.saveDraft(copiedGraph, copiedGraph.revision()), "Could not edit the constructed ACC graph");
                var original = ControllerManifestStore.loadController(sourceManifest, ctx.player().serverLevel());
                require(AdvancedGraphDocument.fromTag(original.draftGraph()).nodes().stream().noneMatch(node -> "copy_only".equals(node.id())),
                        "The constructed ACC changed its source graph");
                copiedController = controller;
                followController();
                ctx.player().openMenu(controller, controller::sendToMenu);
                phase = 2;
            }else if(phase == 2){
                followController();
            }else if(phase == 3){
                if(serverTicks != 0){
                    ctx.player().closeContainer();
                    ctx.player().connection.teleport(128.5, 100, 120, 0, 15);
                    previousBodies = bodyIds(ctx.player());
                    ctx = context(ctx.player());
                    build(TARGET.add(8, 0, 0));
                    serverTicks = 0;
                    return;
                }
                require(DigisableApp.snapshot(ctx).getBoolean("Building"), "Cancellation fixture did not start");
                DigisableSchematics.execute(ctx, action("cancel_build", ""));
                require(!DigisableApp.snapshot(ctx).getBoolean("Building"), "Cancelled construction remained active");
                require(bodyIds(ctx.player()).equals(previousBodies), "Cancellation retained a partial plot");
                require(ctx.player().serverLevel().getEntitiesOfClass(PlayerMannequinEntity.class, WORKERS).isEmpty(),
                        "Cancellation retained construction workers");
                phase = 4;
            }
        }catch(Throwable err){ failure = err; }
    }

    // Save a small beacon platform and start construction through the app's existing handler
    private static void begin(ServerPlayer player) throws Exception{
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.serverLevel().getChunk(8, 8);
        player.serverLevel().getChunk(8, 7);
        player.connection.teleport(128.5, 100, 120, 0, 15);
        var blocks = new ArrayList<SubLevelSchematic.Block>();
        for(int x = 0; x < 3; x++) for(int z = 0; z < 3; z++)
            blocks.add(new SubLevelSchematic.Block(new BlockPos(x, 0, z), Blocks.IRON_BLOCK.defaultBlockState(), null));
        blocks.add(new SubLevelSchematic.Block(new BlockPos(1, 1, 1), Blocks.BEACON.defaultBlockState(), null));
        blocks.add(new SubLevelSchematic.Block(new BlockPos(3, 0, 0), Blocks.CHEST.defaultBlockState(), new CompoundTag()));
        blocks.add(new SubLevelSchematic.Block(new BlockPos(3, 0, 1), Blocks.STONE.defaultBlockState(), null));
        var sourcePos = new BlockPos(124, 100, 124);
        player.serverLevel().setBlock(sourcePos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var source = (AdvancedContraptionControllerBlockEntity)player.serverLevel().getBlockEntity(sourcePos);
        serverTimezone = ClockTime.serverTimezone();
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("time", "createthrusters:time", "Time", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("createthrusters:time",
                        new AdvancedGraphNodeFactory.Context("", "variable", "variable", "", serverTimezone))));
        require(source.saveDraft(graph, source.getDraftGraph().revision()) && source.applyDraft(), "Could not configure the source ACC");
        var sourceData = source.saveWithFullMetadata(player.registryAccess());
        sourceManifest = ControllerManifestStore.controllerManifestId(sourceData);
        blocks.add(new SubLevelSchematic.Block(new BlockPos(3, 0, 2), source.getBlockState(), sourceData));
        var schematic = new SubLevelSchematic(java.util.List.of(new SubLevelSchematic.Body(UUID.randomUUID(),
                Vec3.ZERO, new Quaterniond(), new BlockPos(4, 2, 3), blocks)));
        String name = SubLevelSchematicFiles.save(player.server, "digisable-validation", schematic, PaidTabletApps.DIGISABLE.id());
        schematicId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        previousBodies = bodyIds(player);
        ctx = context(player);
        build(TARGET);
        phase = 1;
    }

    // Give each construction its own tablet session and cooldown
    private static TabletActionContext context(ServerPlayer player){
        var tablet = new ItemStack(CTItems.DIAGNOSTIC_TABLET.get());
        UUID id = DiagnosticTabletData.ensureTabletId(tablet);
        return new TabletActionContext(player, tablet, null, null, false, id, null, null);
    }

    // Use the normal placement arguments consumed by Digisable
    private static void build(Vec3 target) throws Exception{
        DigisableSchematics.execute(ctx, action("build", schematicId + "|" + target.x + "|" + target.y + "|" + target.z + "|0|0|0|1"));
    }

    // Create one app request without bypassing its construction policy
    private static TabletAction action(String name, String val){
        return new TabletAction(PaidTabletApps.DIGISABLE.id(), "schematics", name, Map.of("value", val));
    }

    // Capture existing plot identities before starting or cancelling a build
    private static Set<UUID> bodyIds(ServerPlayer player){
        var ids = new HashSet<UUID>();
        SubLevelContainer.getContainer(player.serverLevel()).getAllSubLevels().forEach(body -> ids.add(body.getUniqueId()));
        return ids;
    }

    // Keep menu reach valid while the completed unpowered fixture settles under gravity
    private static void followController(){
        var body = SubLevelContainer.getContainer(ctx.player().serverLevel()).getSubLevel(builtBody);
        var point = SableTransformApi.toWorldPosition(body, copiedController.getBlockPos().getCenter());
        ctx.player().connection.teleport(point.x, point.y + 2, point.z - 4, 0, 15);
    }

    // Exercise the existing search field and selection events against a live ACC screen
    private static void verifyTimezoneDropdown(AdvancedContraptionControllerScreen screen) throws Exception{
        var draft = (AdvancedGraphDocument)screenField(screen, "draft");
        var node = draft.nodes().stream().filter(val -> "createthrusters:time".equals(val.type())).findFirst().orElseThrow();
        var open = AdvancedContraptionControllerScreen.class.getDeclaredMethod("openOptionDropdown", AdvancedGraphDocument.Node.class,
                String.class, String.class, double.class, double.class, double.class, List.class);
        open.setAccessible(true);
        var rows = AdvancedContraptionControllerScreen.class.getDeclaredMethod("optionDropdownRowsTop",
                AdvancedContraptionControllerScreen.class.getDeclaredField("optionDropdown").getType());
        rows.setAccessible(true);
        open.invoke(screen, node, "mode", "string", 80D, 100D, 100D, List.of("World", "Real"));
        var dropdown = screenField(screen, "optionDropdown");
        var search = (EditBox)screenField(screen, "optionDropdownSearch");
        require(!search.visible, "The mode selector unexpectedly enabled search");
        screen.mouseClicked((int)dropdownValue(dropdown, "x") + 10,
                (int)rows.invoke(null, dropdown) + 18 + 9, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(AdvancedGraphCatalog.inputs(node).keySet().equals(Set.of("mode", "timezone")), "Real mode retained an offset control");
        var zones = TimeGraphNode.presentation().options().get("timezone");
        open.invoke(screen, node, "timezone", "string", 80D, 100D, 160D, zones);
        require(search.visible && search.isFocused(), "The timezone search did not open focused");
        for(char ch : "lOnDoN".toCharArray()) require(screen.charTyped(ch, 0), "The search field ignored typed input");
        dropdown = screenField(screen, "optionDropdown");
        require(List.of("Europe/London").equals(dropdownValue(dropdown, "options")), "Timezone filtering did not match case-insensitively");
        screen.mouseClicked((int)dropdownValue(dropdown, "x") + 10,
                (int)rows.invoke(null, dropdown) + 9, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require("Europe/London".equals(node.data().getCompound("Defaults").getCompound("timezone").getCompound("Payload").getString("Value"))
                && screenField(screen, "optionDropdown") == null, "Selecting a filtered timezone did not save and close");
        open.invoke(screen, node, "timezone", "string", 80D, 100D, 160D, zones);
        require(search.getValue().isEmpty() && zones.equals(dropdownValue(screenField(screen, "optionDropdown"), "options")),
                "Reopening the timezone dropdown retained its filter");
        for(char ch : "Missing/Zone".toCharArray()) screen.charTyped(ch, 0);
        require(((List<?>)dropdownValue(screenField(screen, "optionDropdown"), "options")).isEmpty(), "Unmatched zones did not show empty results");
        for(int idx = 0; idx < "Missing/Zone".length(); idx++) screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        require(zones.equals(dropdownValue(screenField(screen, "optionDropdown"), "options")), "Clearing search did not restore timezone choices");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(!search.visible && screenField(screen, "optionDropdown") == null, "Closing the dropdown left its search field visible");
        open.invoke(screen, node, "mode", "string", 80D, 100D, 100D, List.of("World", "Real"));
        dropdown = screenField(screen, "optionDropdown");
        screen.mouseClicked((int)dropdownValue(dropdown, "x") + 10,
                (int)rows.invoke(null, dropdown) + 9, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        require(AdvancedGraphCatalog.inputs(node).keySet().equals(Set.of("mode")), "World mode retained real-time controls");
    }

    // Read editor state without adding production-only validation hooks
    private static Object screenField(AdvancedContraptionControllerScreen screen, String name) throws Exception{
        var field = AdvancedContraptionControllerScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    // Inspect dropdown geometry and choices after real input events
    private static Object dropdownValue(Object dropdown, String name) throws Exception{
        var method = dropdown.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(dropdown);
    }

    // Invoke the optional mod's private discovery routine while retaining its normal beam lifecycle
    private static void verifyBeacons(ClientSubLevel body) throws Exception{
        require(ModList.get().isLoaded("contraptionlights"), "Contraption Lights was not installed for compatibility validation");
        var discover = Class.forName("xyz.atmerek.contraptionlights.sable.SubLevelBeaconBeams")
                .getDeclaredMethod("discover", ClientSubLevel.class, LevelPlot.class);
        discover.setAccessible(true);
        var plot = body.getPlot();
        var occupied = new BoundingBox3i(plot.getBoundingBox());
        try{
            require(((long[])discover.invoke(null, body, plot)).length == 1, "Normal beacon discovery lost the constructed beacon");
            plot.setBoundingBox(new BoundingBox3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
            for(int idx = 0; idx < 100; idx++)
                require(((long[])discover.invoke(null, body, plot)).length == 1, "Sparse beacon discovery lost an active beacon");
            plot.setBoundingBox(BoundingBox3i.EMPTY);
            require(((long[])discover.invoke(null, body, plot)).length == 0, "Empty plots returned occupied beacon positions");
        }finally{ plot.setBoundingBox(occupied); }
    }

    // Fail the isolated run with a concrete invariant
    private static void require(boolean val, String msg){ if(!val) throw new AssertionError(msg); }

    // Write the automated result outside the source tree
    private static void writeResult(String text){
        try{ Files.writeString(Path.of(System.getProperty("gadgetsngizmos.digisableValidationResult")), text); }
        catch(java.io.IOException err){ throw new IllegalStateException("Cannot write Digisable validation result", err); }
    }
}
