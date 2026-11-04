package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

// Check real ACC geometry reuse without altering the server's controller or display state
final class DisplayGeometryValidation{
    static final BlockPos SOURCE = new BlockPos(2, -59, -4);

    static void run(){
        var mc = Minecraft.getInstance();
        var display = (AccDisplayBlockEntity)mc.level.getBlockEntity(SOURCE);
        require(display != null && display.isNetworkRoot(), "ACC display fixture was not loaded");
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(display);
        var frame = new CompoundTag(); frame.putString("State", "unavailable"); frame.putString("Message", "Geometry validation");
        var prev = display.displayFrame();
        var sink = new Sink();
        set(display, "displayFrame", frame);
        try{
            Runnable draw = () -> SubLevelClientRenderApi.withProjection(new Matrix4f().ortho(-128, 128, -128, 128, -128, 128),
                    () -> renderer.render(display, .5F, new PoseStack(), ignored -> sink, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
            draw.run();
            Object retained = ((Map<?, ?>)get(renderer, "retained")).get(display);
            require(retained != null && get(retained, "frame") == frame, "ACC frame was not retained");
            require(!((Map<?, ?>)get(get(retained, "geometry"), "batches")).isEmpty(), "ACC content produced no retained geometry");
            long future = Util.getMillis() + 10_000;
            set(retained, "lastFrame", future);
            int before = sink.vertices;
            for(int idx = 0; idx < 20; idx++) draw.run();
            require((long)get(retained, "lastFrame") == future && sink.vertices > before,
                    "Repeated ACC presentations rebuilt geometry or stopped drawing");
            var next = frame.copy(); next.putString("Message", "Immediate network update");
            set(display, "displayFrame", next);
            draw.run();
            require(get(retained, "frame") == next && (long)get(retained, "lastFrame") != future,
                    "New ACC frame waited for the periodic refresh");
            set(retained, "lastFrame", 0L);
            draw.run();
            require((long)get(retained, "lastFrame") == 0L, "Static ACC text rebuilt without a frame change");
            var animated = frame.copy(); animated.putString("State", "initializing");
            set(display, "displayFrame", animated);
            draw.run();
            require((long)get(retained, "lastFrame") != 0L, "Live ACC refresh stopped");
            checkModes(display, retained, draw);
            checkBounds(display, renderer);
            checkCameraIsolation(display, retained, draw);
        }finally{ set(display, "displayFrame", prev); }
    }

    private static void checkModes(AccDisplayBlockEntity display, Object retained, Runnable draw){
        var lines = new ListTag(); lines.add(StringTag.valueOf("Retained display text"));
        var linked = new CompoundTag(); linked.putString("State", "display_link"); linked.put("Lines", lines);
        var text = new CompoundTag(); text.putString("State", "external");
        var payload = new CompoundTag(); payload.putString("Format", "text"); payload.put("Lines", lines.copy()); text.put("External", payload);
        var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", AdvancedGraphNodeFactory.Context.EMPTY);
        var target = new CompoundTag(); target.putLong("BlockPos", display.getBlockPos().asLong()); data.put("TargetData", target);
        var elements = new ListTag(); var element = new CompoundTag();
        element.putString("Type", "button"); element.putInt("W", 160); element.putInt("H", 80); element.putString("Text", "Widget");
        elements.add(element); data.put("WidgetElements", elements);
        graph.nodes().add(new AdvancedGraphDocument.Node("retained_widget", "acc_display_widget", "Widget", 0, 0, data));
        var widgets = new CompoundTag(); widgets.putString("State", "graph"); widgets.putString("Mode", "widgets"); widgets.put("Graph", graph.toTag());
        for(var frame : java.util.List.of(linked, text, widgets)){
            set(display, "displayFrame", frame); draw.run();
            require(get(retained, "frame") == frame && !((Map<?, ?>)get(get(retained, "geometry"), "batches")).isEmpty(),
                    "ACC mode lost its geometry after switching sources");
            long future = Util.getMillis() + 10_000; set(retained, "lastFrame", future);
            for(int idx = 0; idx < 20; idx++) draw.run();
            require((long)get(retained, "lastFrame") == future, "Unchanged ACC mode rebuilt on every presentation");
        }
    }

    private static void checkBounds(AccDisplayBlockEntity display, net.minecraft.client.renderer.blockentity.BlockEntityRenderer<AccDisplayBlockEntity> renderer){
        var level = Minecraft.getInstance().level;
        var pos = display.getBlockPos().relative(display.screenRight());
        var prev = level.getBlockState(pos);
        try{
            level.setBlock(pos, display.getBlockState(), 3);
            var member = (AccDisplayBlockEntity)level.getBlockEntity(pos);
            AccDisplayBlockEntity.displayTopologyChanged(level, pos);
            require(member != null && !renderer.shouldRender(member, display.getBlockPos().getCenter()), "ACC dispatched a non-root panel");
            var bounds = renderer.getRenderBoundingBox(display);
            require(bounds == renderer.getRenderBoundingBox(display) && bounds.contains(pos.getCenter()), "ACC bounds were rebuilt or omitted a panel");
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            AccDisplayBlockEntity.displayTopologyChanged(level, pos);
            require(renderer.getRenderBoundingBox(display) != bounds, "ACC resize retained stale bounds in the same tick");
        }finally{ level.setBlock(pos, prev, 3); }
    }

    // A camera omits nested feeds without changing the player's retained surface or its source demand
    private static void checkCameraIsolation(AccDisplayBlockEntity display, Object retained, Runnable draw){
        var mc = Minecraft.getInstance();
        var ref = ViewReference.of(mc.player);
        ViewSceneRenderer.request(ref, 16, 16);
        Object capture = ((Map<?, ?>)get(ViewSceneRenderer.class, "CAPTURES")).get(ref);
        require(capture != null, "Camera isolation fixture did not create a feed");
        Object prev = get(ViewSceneRenderer.class, "active");
        try{
            set(capture, "ready", true);
            var camera = new CompoundTag(); camera.putString("Format", "camera"); camera.put("ViewSource", ref.toTag());
            var frame = new CompoundTag(); frame.putString("State", "external"); frame.put("External", camera);
            set(display, "displayFrame", frame);
            draw.run();
            var sources = (Set<?>)get(get(retained, "feeds"), "sources");
            require(sources.contains(ref), "ACC camera surface did not retain its source");
            int batches = ((Map<?, ?>)get(get(retained, "geometry"), "batches")).size();
            set(retained, "lastFrame", 0L);
            set(ViewSceneRenderer.class, "active", capture);
            draw.run();
            require(get(retained, "secondaryFrame") == frame, "Secondary ACC surface was not retained separately");
            long future = Util.getMillis() + 10_000;
            set(retained, "lastSecondaryFrame", future);
            for(int idx = 0; idx < 20; idx++) draw.run();
            require((long)get(retained, "lastSecondaryFrame") == future, "Repeated camera passes rebuilt the same ACC surface");
            require((long)get(retained, "lastFrame") == 0L,
                    "Secondary camera replaced the player's retained ACC surface");
            require(sources.contains(ref) && ((Map<?, ?>)get(get(retained, "geometry"), "batches")).size() == batches,
                    "Secondary camera erased the player's live feed geometry or demand");
            set(ViewSceneRenderer.class, "active", prev);
            draw.run();
            require(get(retained, "frame") == frame && sources.contains(ref),
                    "Player camera feed did not resume after a secondary presentation");
        }finally{
            set(ViewSceneRenderer.class, "active", prev);
            ViewSceneRenderer.clear();
        }
    }

    private static Field field(Object owner, String name) throws ReflectiveOperationException{
        var field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
        field.setAccessible(true); return field;
    }
    private static Object get(Object owner, String name){
        try{ return field(owner, name).get(owner); }
        catch(ReflectiveOperationException err){ throw new AssertionError("Cannot inspect ACC rendering state", err); }
    }
    private static void set(Object owner, String name, Object val){
        try{ field(owner, name).set(owner, val); }
        catch(ReflectiveOperationException err){ throw new AssertionError("Cannot set ACC validation state", err); }
    }
    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
    private static final class Sink implements VertexConsumer{
        private int vertices;
        @Override public VertexConsumer addVertex(float x, float y, float z){ vertices++; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ return this; }
        @Override public VertexConsumer setUv(float u, float v){ return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
