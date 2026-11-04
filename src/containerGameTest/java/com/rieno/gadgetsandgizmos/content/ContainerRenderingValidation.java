package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import com.rieno.gadgetsandgizmos.lib.client.render.RetainedHologramRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRendererProvider;
import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState.Presentation;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletGuiProjection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

import java.lang.reflect.Field;
import java.util.Map;

// Exercise real tablet framebuffers and retained fluid geometry on the render thread
final class ContainerRenderingValidation{
    static void run(){
        checkStaticPreview();
        checkTankModes();
    }

    private static void checkStaticPreview(){
        var mc = Minecraft.getInstance();
        var tank = (SmartTankBlockEntity)mc.level.getBlockEntity(HybridHologramValidation.TANK);
        var sink = new Sink();
        var surface = DiagnosticTabletSurface.screen(Direction.UP);
        DiagnosticTabletGuiProjection.clear();
        var sessions = (Map<?, ?>)get(DiagnosticTabletGuiProjection.class, "CONTAINER_SESSIONS");
        try{
            for(int idx = 0; idx < 100; idx++) DiagnosticTabletGuiProjection.renderContainer(tank, surface, new PoseStack(), ignored -> sink, false);
            require(sessions.isEmpty() && sink.vertices == 400, "Distant preview allocated a screen or omitted its shared image");
            DiagnosticTabletGuiProjection.renderContainer(tank, surface, new PoseStack(), ignored -> sink, true);
            Object session = sessions.get(tank);
            require(session != null && get(session, "screen") != null, "Nearby tablet did not prepare its screen");
            invoke(session, "renderFrame", new Class<?>[]{float.class}, .5F);
            Object projection = get(session, "projection");
            require(projection != null && (boolean)invoke(projection, "isReady", new Class<?>[0]), "Nearby tablet did not render its framebuffer");
            long frame = (long)get(DiagnosticTabletGuiProjection.class, "frame");
            set(DiagnosticTabletGuiProjection.class, "frame", frame + 1);
            set(session, "lastFrame", 1L);
            for(int idx = 0; idx < 100; idx++) DiagnosticTabletGuiProjection.renderContainer(tank, surface, new PoseStack(), ignored -> sink, false);
            invoke(DiagnosticTabletGuiProjection.class, "renderFrames", new Class<?>[]{Map.class, long.class, float.class}, sessions, Util.getMillis(), .5F);
            require((long)get(session, "lastFrame") == 1L && get(session, "projection") == projection,
                    "Distant tablet refreshed or replaced its frozen image");
            DiagnosticTabletGuiProjection.renderContainer(tank, surface, new PoseStack(), ignored -> sink, true);
            set(get(session, "visibility"), "drawn", get(com.rieno.gadgetsandgizmos.lib.client.render.RenderSurfaceVisibility.class, "frame"));
            invoke(DiagnosticTabletGuiProjection.class, "renderFrames", new Class<?>[]{Map.class, long.class, float.class}, sessions, Util.getMillis(), .5F);
            require((long)get(session, "lastFrame") != 1L, "Nearby tablet did not resume live frames");
        }finally{ DiagnosticTabletGuiProjection.clear(); }
    }

    private static void checkTankModes(){
        var mc = Minecraft.getInstance();
        var tank = (SmartTankBlockEntity)mc.level.getBlockEntity(HybridHologramValidation.TANK);
        var state = tank.mountedTabletState();
        var prev = state.presentation();
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(tank);
        try(var scope = HologramRenderScope.enter(); RetainedHologramRenderer retained = ((HologramRendererProvider)renderer).createHologramRenderer(tank)){
            int fluid = draw(tank, retained, Presentation.CONTENTS);
            int tablet = draw(tank, retained, Presentation.TABLET);
            require(fluid > 0 && tablet > 0, "Enabled tank contents or tablet disappeared");
            require(draw(tank, retained, Presentation.NONE) == 0, "Disabled tank presentation retained old geometry");
            require(draw(tank, retained, Presentation.BOTH) == fluid + tablet, "Combined tank presentation lost a layer");
            require(draw(tank, retained, Presentation.CONTENTS) == fluid, "Tank mode cycle retained a stale tablet");
        }finally{ state.setPresentation(prev); }
    }

    private static int draw(SmartTankBlockEntity tank, RetainedHologramRenderer renderer, Presentation mode){
        tank.mountedTabletState().setPresentation(mode);
        var sink = new Sink();
        renderer.render(.5F, new PoseStack(), ignored -> sink, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        return sink.vertices;
    }

    private static Field field(Object owner, String name) throws ReflectiveOperationException{
        var field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
        field.setAccessible(true); return field;
    }
    private static Object get(Object owner, String name){
        try{ return field(owner, name).get(owner); }
        catch(ReflectiveOperationException err){ throw new AssertionError("Cannot inspect container rendering", err); }
    }
    private static void set(Object owner, String name, Object val){
        try{ field(owner, name).set(owner, val); }
        catch(ReflectiveOperationException err){ throw new AssertionError("Cannot set container validation state", err); }
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args){
        try{
            var method = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredMethod(name, types);
            method.setAccessible(true); return method.invoke(owner, args);
        }catch(ReflectiveOperationException err){ throw new AssertionError("Cannot exercise container rendering", err); }
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
