package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// Exercise real chunk delivery, deferred snapshots and both worn HUD render paths
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class HudPayloadClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.hudPayloadValidation");
    private static final BlockPos DISPLAY = new BlockPos(4, -59, 4);
    private static final int PROOF_BYTES = 3 * 1024 * 1024;
    // Retain validation progress only inside the isolated development client
    private static boolean hidden, installed, complete;
    private static long started;
    private static volatile Throwable serverFailure;
    private static CompoundTag graphTag;

    // Wait for a real bulk snapshot before testing render-thread graph access
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        Minecraft mc = Minecraft.getInstance();
        if(!hidden){ GLFW.glfwHideWindow(mc.getWindow().getWindow()); hidden = true; started = System.nanoTime(); }
        try{
            if(System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Client payload validation timed out");
            if(serverFailure != null) throw new AssertionError("Cannot install the server fixture", serverFailure);
            if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            mc.options.pauseOnLostFocus = false;
            mc.setScreen(null);
            if(!installed){
                installed = true;
                var root = JsonParser.parseString(Files.readString(Path.of(System.getProperty("acc.presentation.graph")))).getAsJsonObject();
                graphTag = (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE,
                        root.getAsJsonArray("storedGraphs").get(0).getAsJsonObject().get("Graph"));
                installServerFixture(mc);
                return;
            }
            var entity = mc.level.getBlockEntity(DISPLAY);
            if(!(entity instanceof AccDisplayBlockEntity display)
                    || display.displayFrame().getByteArray("TransferProof").length != PROOF_BYTES) return;
            if(display.graph().nodes().size() != graphTag.getList("Nodes", 10).size())
                throw new AssertionError("The deferred snapshot changed the supplied graph");
            double millis = validateHud(mc);
            finish("PASSED real 3 MiB clientbound transfer, supplied graph and 120 Glasses/Pilot Hat HUD frames without graph documents or previews; mean "
                    + String.format(java.util.Locale.ROOT, "%.3f", millis) + " ms per HUD frame");
        }catch(Throwable err){ err.printStackTrace(); finish("FAILED " + err); }
    }

    // Send an incompressible frame through the normal initial chunk and snapshot handlers
    private static void installServerFixture(Minecraft mc){
        mc.getSingleplayerServer().execute(() -> {
            try{
                var level = mc.getSingleplayerServer().overworld();
                level.setBlock(DISPLAY, CTBlocks.ACC_DISPLAY.get().defaultBlockState(), 3);
                var display = new FrozenDisplay();
                level.setBlockEntity(display);
                byte[] proof = new byte[PROOF_BYTES]; new Random(42).nextBytes(proof);
                CompoundTag frame = new CompoundTag();
                frame.putString("State", "graph"); frame.putInt("GraphRevision", 42);
                frame.put("Graph", graphTag.copy()); frame.putByteArray("TransferProof", proof);
                CompoundTag data = new CompoundTag(); data.put("AccDisplayFrame", frame);
                display.readClient(data, level.registryAccess());
                display.sendData();
                for(var player : level.getServer().getPlayerList().getPlayers()){
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.connection.teleport(2, -56, 2, 0, 0);
                }
            }catch(Throwable err){ serverFailure = err; }
        });
    }

    // Reject any client graph evaluation while rendering the real supplied HUD canvases
    private static double validateHud(Minecraft mc){
        BlockPos pos = new BlockPos(8, -59, 8);
        var state = CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState();
        mc.level.setBlock(pos, state, 3);
        var controller = new AdvancedContraptionControllerBlockEntity(pos, state){
            // Fail immediately if a render fallback tries to execute the graph
            @Override public AdvancedGraphDocument.Value previewActiveGraphInput(AdvancedGraphDocument.Node node, String port){
                throw new AssertionError("HUD evaluated " + node.id() + ":" + port);
            }
        };
        mc.level.setBlockEntity(controller);
        var graph = AdvancedGraphDocument.fromTag(graphTag);
        var runtime = new com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime(null, true);
        runtime.compile(graph);
        controller.applyClientHudLayout(runtime.hudPresentationLayout());
        UUID pair = UUID.randomUUID();
        var hud = graph.nodes().stream().filter(node -> node.type().contains("hud_element")).findFirst().orElseThrow();
        controller.applyClientGraphRuntime(Map.of(pair, "Validation"), Map.of(hud.id() + ":value",
                AdvancedGraphLiveValue.from(AdvancedGraphDocument.Value.number(42))), Map.of(), Map.of());
        long nanos = 0;
        try(var surface = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "hud_payload_validation"))){
            for(var item : java.util.List.of(CTItems.PHYSICS_GOGGLES.get(), CTItems.PILOT_CAP.get())){
                ItemStack stack = new ItemStack(item);
                CompoundTag binding = new CompoundTag();
                binding.putUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID, pair);
                binding.putInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_X, pos.getX());
                binding.putInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Y, pos.getY());
                binding.putInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Z, pos.getZ());
                CompoundTag root = new CompoundTag(); root.put(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT, binding);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                mc.player.setItemSlot(EquipmentSlot.HEAD, stack);
                surface.render(640, 360, 640, 360, graphics -> CTPhysicsGogglesClient.onRenderGui(new RenderGuiEvent.Post(graphics, mc.getTimer())));
                com.mojang.blaze3d.systems.RenderSystem.bindTexture(mc.getTextureManager().getTexture(surface.texture()).getId());
                try(var pixels = new com.mojang.blaze3d.platform.NativeImage(640, 360, true)){
                    pixels.downloadTexture(0, false);
                    int painted = 0;
                    for(int y = 0; y < 360; y++) for(int x = 0; x < 640; x++){
                        if((pixels.getPixelRGBA(x, y) >>> 24) != 0) painted++;
                    }
                    if(painted < 100) throw new AssertionError("The worn item did not render its HUD");
                }
                long before = System.nanoTime();
                for(int idx = 0; idx < 60; idx++) surface.render(640, 360, 640, 360,
                        graphics -> CTPhysicsGogglesClient.onRenderGui(new RenderGuiEvent.Post(graphics, mc.getTimer())));
                nanos += System.nanoTime() - before;
            }
        }finally{ mc.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); }
        if(!controller.activeGraphReadView().nodes().isEmpty()) throw new AssertionError("The worn HUD loaded a graph document");
        return nanos / 120_000_000D;
    }

    // Record a reviewable result and close the isolated client
    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ Minecraft.getInstance().stop(); }
    }

    // Hold the fixture steady while ordinary network requests resolve its real block entity
    private static final class FrozenDisplay extends AccDisplayBlockEntity{
        // Use the production registered display type and state
        private FrozenDisplay(){ super(DISPLAY, CTBlocks.ACC_DISPLAY.get().defaultBlockState()); }
        // Keep the injected presentation stable throughout the transfer
        @Override public void tick(){}
        // Suppress the block's independent server ticker while this fixture owns its frame
        @Override public void requestDisplayRefresh(){}
        // Keep unrelated source refresh callbacks from replacing the transport fixture
        @Override public boolean isNetworkRoot(){ return false; }

    }
}
