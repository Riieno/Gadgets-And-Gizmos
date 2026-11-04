package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelPreviewRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelSnapshotBlocks;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldBlockHologramRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldPlacementControls;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientAppData;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletScreen;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;

import java.util.List;
import java.util.UUID;

// Keep one handheld archive placement preview local until the server confirms extraction
public final class DigisablePlacementClient{
    private static final ResourceLocation APP = DiagnosticTabletData.appId("digisable");
    private static Session session;

    private DigisablePlacementClient(){}

    public static void begin(InteractionHand hand, UUID tabletId, UUID archiveId){
        begin(hand, tabletId, archiveId, false);
    }

    // Share the placement controls between archive extraction and schematic construction
    public static void begin(InteractionHand hand, UUID tabletId, UUID archiveId, boolean schematic){
        if(tabletId == null || archiveId == null) throw new IllegalArgumentException("A tablet and archive are required");
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null) throw new IllegalArgumentException("A world is required");
        DiagnosticTabletClientAppData.apply(APP, false, tabletId, null, null, new CompoundTag());
        session = new Session(hand, tabletId, archiveId, minecraft.level.dimension().location(), schematic);
    }

    public static void onClientTick(ClientTickEvent.Pre evt){
        Session current = session;
        if(current == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(current.dimension)
                || !matches(current, minecraft.player.getItemInHand(current.hand))){
            session = null;
            return;
        }
        CompoundTag data = DiagnosticTabletClientAppData.get(APP, false, current.tabletId, null, null);
        if(data.hasUUID("PlacementComplete") && data.getUUID("PlacementComplete").equals(current.archiveId)){
            session = null;
            return;
        }
        if(!current.ready){
            if(data.contains("Error") || ++current.waitTicks > 200){
                session = null;
                minecraft.player.displayClientMessage(Component.literal(data.contains("Error")
                        ? data.getString("Error") : "Sublevel preview timed out"), true);
                DiagnosticTabletScreen.openItem(current.hand, minecraft.player.getItemInHand(current.hand));
                return;
            }
            if(data.hasUUID("PlacementId") && data.getUUID("PlacementId").equals(current.archiveId)){
                List<SubLevelPreviewRenderer.SnapshotBlock> blocks = SubLevelSnapshotBlocks.decode(
                        data.getList("Preview", Tag.TAG_COMPOUND), minecraft.level.registryAccess());
                if(blocks.isEmpty()) return;
                current.blocks = blocks;
                current.controls = new WorldPlacementControls(8, data.getInt("ExtractionRange"));
                current.ready = true;
            }
        }else if(current.awaiting && data.contains("Error")){
            current.awaiting = false;
            minecraft.player.displayClientMessage(Component.literal(data.getString("Error")), true);
        }
    }

    public static void onRenderWorld(RenderLevelStageEvent evt){
        Session current = session;
        if(current == null || !current.ready || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player == null || minecraft.screen != null) return;
        Vec3 target = current.controls.target(minecraft.player.getEyePosition(evt.getPartialTick().getGameTimeDeltaPartialTick(false)),
                minecraft.player.getViewVector(evt.getPartialTick().getGameTimeDeltaPartialTick(false)));
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        WorldBlockHologramRenderer.render(evt.getPoseStack(), buffers, evt.getCamera().getPosition(),
                target, current.controls.orientation(), current.blocks);
    }

    public static void onRenderGui(RenderGuiEvent.Post evt){
        Session current = session;
        if(current == null || Minecraft.getInstance().screen != null) return;
        GuiGraphics graphics = evt.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        String prompt = !current.ready ? "Loading preview...  |  Esc: cancel"
                : current.awaiting ? current.schematic ? "Starting worker construction..." : "Placing sublevel..."
                : "Wheel: push/pull  |  Hold Tab + move mouse: rotate  |  Use: place  |  Esc: cancel";
        graphics.drawCenteredString(font, prompt, graphics.guiWidth() / 2,
                graphics.guiHeight() - 52, 0xFFB9EAFB);
    }

    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt){
        Session current = session;
        if(current == null || !current.ready || evt.isCanceled()
                || Minecraft.getInstance().screen != null || evt.getScrollDeltaY() == 0) return;
        current.controls.scroll(evt.getScrollDeltaY(),
                SimConfigService.INSTANCE.client().itemConfig.physicsStaffScrollSensitivity.get(),
                Minecraft.getInstance().options.keySprint.isDown());
        evt.setCanceled(true);
    }

    public static boolean onMouseMove(double yaw, double pitch){
        Session current = session;
        Minecraft minecraft = Minecraft.getInstance();
        if(current == null || !current.ready || minecraft.screen != null
                || minecraft.player == null || !SimKeys.ROTATE_MODE.isPressed()) return false;
        Vec3 right = minecraft.player.calculateViewVector(0, minecraft.player.getYRot() - 90);
        current.controls.rotate(yaw, pitch,
                SimConfigService.INSTANCE.client().itemConfig.physicsStaffRotateSensitivity.get(), right);
        return true;
    }

    public static boolean onUse(){
        Session current = session;
        Minecraft minecraft = Minecraft.getInstance();
        if(current == null || minecraft.screen != null || minecraft.player == null
                || !matches(current, minecraft.player.getItemInHand(current.hand))) return false;
        if(!current.ready || current.awaiting) return true;
        Vec3 target = current.controls.target(minecraft.player.getEyePosition(), minecraft.player.getLookAngle());
        Quaterniond rotation = current.controls.orientation();
        String value = current.archiveId + "|" + target.x + "|" + target.y + "|" + target.z
                + "|" + rotation.x + "|" + rotation.y + "|" + rotation.z + "|" + rotation.w;
        CompoundTag data = DiagnosticTabletClientAppData.get(APP, false, current.tabletId, null, null);
        data.remove("Error");
        DiagnosticTabletClientAppData.apply(APP, false, current.tabletId, null, null, data);
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(false, current.hand,
                BlockPos.ZERO, null, current.tabletId, APP, current.schematic ? "schematics" : "archives", current.schematic ? "build" : "extract", value));
        current.awaiting = true;
        return true;
    }

    public static boolean cancel(){
        Session current = session;
        if(current == null || Minecraft.getInstance().screen != null) return false;
        if(current.awaiting) return true;
        session = null;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.player != null && matches(current, minecraft.player.getItemInHand(current.hand))){
            DiagnosticTabletScreen.openItem(current.hand, minecraft.player.getItemInHand(current.hand));
        }
        return true;
    }

    public static void clear(){ session = null; }

    private static boolean matches(Session current, ItemStack stack){
        return stack.getItem() instanceof DiagnosticTabletItem
                && current.tabletId.equals(DiagnosticTabletData.read(stack).tabletId());
    }

    private static final class Session{
        private final InteractionHand hand;
        private final UUID tabletId;
        private final UUID archiveId;
        private final ResourceLocation dimension;
        private final boolean schematic;
        private List<SubLevelPreviewRenderer.SnapshotBlock> blocks = List.of();
        private WorldPlacementControls controls;
        private int waitTicks;
        private boolean ready;
        private boolean awaiting;

        private Session(InteractionHand hand, UUID tabletId, UUID archiveId, ResourceLocation dimension, boolean schematic){
            this.hand = hand; this.tabletId = tabletId; this.archiveId = archiveId; this.dimension = dimension;
            this.schematic = schematic;
        }
    }
}
