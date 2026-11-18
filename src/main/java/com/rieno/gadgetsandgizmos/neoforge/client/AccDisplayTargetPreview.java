package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.ControllerTargetPreview;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderGeometryCache;
import com.rieno.gadgetsandgizmos.lib.client.ui.DisplayFramePreview;
import com.rieno.gadgetsandgizmos.lib.display.SurfaceFit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;

// Present the synced ACC surface without requiring its target block to be loaded on the client
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class AccDisplayTargetPreview{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>(16, .75F, true);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private AccDisplayTargetPreview(){}

    // Keep the addon renderer behind the library's generic preview contract
    public static void register(){ DisplayFramePreview.register(ControllerTargetPreview.DISPLAY_FORMAT, AccDisplayTargetPreview::draw); }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut evt){ SESSIONS.clear(); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void draw(GuiGraphics graphics, Font font, CompoundTag frame, int x, int y, int width, int height){
        var mc = Minecraft.getInstance(); if(mc.level == null) return;
        String key = frame.getCompound("Target").toString();
        var session = SESSIONS.computeIfAbsent(key, ignored -> new Session());
        if(SESSIONS.size() > 16) SESSIONS.remove(SESSIONS.keySet().iterator().next());
        var state = NbtUtils.readBlockState(mc.level.holderLookup(Registries.BLOCK), frame.getCompound("BlockState"));
        boolean changed = session.frame != frame;
        if(session.display == null || session.display.getLevel() != mc.level || session.display.getBlockState() != state){
            session.display = new PreviewDisplay(BlockPos.of(frame.getLong("Position")), state);
            session.display.setLevel(mc.level); changed = true;
        }
        var display = session.display;
        display.update(frame); session.frame = frame;
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer((AccDisplayBlockEntity)display);
        if(!(renderer instanceof AccDisplayRenderer surface)) return;
        var fit = SurfaceFit.fit(display.surfacePixelWidth(), display.surfacePixelHeight(), width, height, true);
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        graphics.flush();
        try(var clip = GuiPoseScissor.push(graphics, (int)Math.floor(x + fit.x()), (int)Math.floor(y + fit.y()),
                (int)Math.ceil(x + fit.x() + fit.width()), (int)Math.ceil(y + fit.y() + fit.height()))){
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + fit.x(), y + fit.y(), 0);
                graphics.pose().scale((float)fit.scaleX(), (float)fit.scaleY(), -1);
                long now = net.minecraft.Util.getMillis();
                boolean refresh = changed || now - session.lastFrame >= 50;
                session.geometry.render(graphics.pose(), graphics.bufferSource(), refresh,
                        buffers -> surface.renderSurface(display, mc.getTimer().getGameTimeDeltaPartialTick(true), new PoseStack(), buffers));
                if(refresh) session.lastFrame = now;
                graphics.flush();
            }finally{ graphics.pose().popPose(); }
        }
    }

    private static final class Session{
        private final RenderGeometryCache geometry = new RenderGeometryCache(true);
        private PreviewDisplay display;
        private CompoundTag frame;
        private long lastFrame;
    }

    // Retain physical dimensions independently of client chunk topology
    private static final class PreviewDisplay extends AccDisplayBlockEntity{
        private int width = 1, height = 1, blocksWide = 1, blocksHigh = 1;
        private PreviewDisplay(BlockPos pos, BlockState state){ super(pos, state); }
        private void update(CompoundTag frame){
            width = Math.clamp(frame.getInt("Width"), 1, 8192); height = Math.clamp(frame.getInt("Height"), 1, 8192);
            blocksWide = Math.clamp(frame.getInt("BlocksWide"), 1, 32); blocksHigh = Math.clamp(frame.getInt("BlocksHigh"), 1, 32);
            applyPreviewFrame(frame.getCompound("Content"));
        }
        @Override public AccDisplayBlockEntity networkRoot(){ return this; }
        @Override public boolean isNetworkRoot(){ return true; }
        @Override public int networkWidth(){ return blocksWide; }
        @Override public int networkHeight(){ return blocksHigh; }
        @Override public int surfacePixelWidth(){ return width; }
        @Override public int surfacePixelHeight(){ return height; }
    }
}
